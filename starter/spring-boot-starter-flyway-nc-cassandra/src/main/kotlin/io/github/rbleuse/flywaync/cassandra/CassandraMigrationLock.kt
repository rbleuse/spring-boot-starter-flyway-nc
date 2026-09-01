package io.github.rbleuse.flywaync.cassandra

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel
import com.datastax.oss.driver.api.core.cql.AsyncResultSet
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import org.flywaydb.core.api.FlywayException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

internal class CassandraMigrationLock(
    private val session: CqlSession,
    private val lockName: String,
    private val properties: CassandraFlywayNcLockProperties,
    private val haltJvm: () -> Nothing = {
        Runtime.getRuntime().halt(1)
        error("Runtime.halt returned")
    },
) {
    private val owner =
        "${System.getenv("HOSTNAME") ?: System.getenv("COMPUTERNAME") ?: "unknown"}-" +
            "${ProcessHandle.current().pid()}-${UUID.randomUUID()}"
    private val leaseSeconds = properties.leaseSeconds

    // Stop waiting before the TTL expires; measure from request dispatch, not its response.
    private val leaseBudgetNanos = properties.leaseDuration.toNanos() / 3 * 2
    private val acquireStatement: PreparedStatement
    private val renewStatement: PreparedStatement
    private val releaseStatement: PreparedStatement

    init {
        session.execute(
            """
            CREATE TABLE IF NOT EXISTS $TABLE (
                lock_name text PRIMARY KEY,
                owner text,
                acquired_at timestamp
            )
            """.trimIndent(),
        )
        acquireStatement =
            session.prepare(
                "INSERT INTO $TABLE (lock_name, owner, acquired_at) " +
                    "VALUES (?, ?, toTimestamp(now())) IF NOT EXISTS USING TTL ?",
            )
        renewStatement =
            session.prepare(
                "UPDATE $TABLE USING TTL ? SET owner = ?, acquired_at = toTimestamp(now()) " +
                    "WHERE lock_name = ? IF owner = ?",
            )
        releaseStatement =
            session.prepare("DELETE FROM $TABLE WHERE lock_name = ? IF owner = ?")
    }

    fun withLock(migration: () -> Unit) {
        var leaseStartedAt = acquire()

        val migrationActive = AtomicBoolean(true)
        val executor =
            Executors.newSingleThreadScheduledExecutor { task ->
                Thread(task, "flyway-nc-cassandra-lock").apply { isDaemon = true }
            }
        val heartbeatPeriod = max(100, properties.leaseDuration.toMillis() / 3)
        executor.scheduleAtFixedRate(
            {
                try {
                    val renewalStartedAt = System.nanoTime()
                    if (!renew(leaseStartedAt + leaseBudgetNanos)) {
                        throw FlywayException("Lost Cassandra migration lock '$lockName'")
                    }
                    leaseStartedAt = renewalStartedAt
                } catch (failure: Throwable) {
                    if (migrationActive.compareAndSet(true, false)) {
                        logger.error("Lost Cassandra migration lock '$lockName'; halting to prevent concurrent migrations", failure)
                        haltJvm()
                    }
                }
            },
            0,
            heartbeatPeriod,
            TimeUnit.MILLISECONDS,
        )

        val migrationFailure = runCatching(migration).exceptionOrNull()
        migrationActive.set(false)
        executor.shutdownNow()

        val failures =
            listOfNotNull(
                migrationFailure,
                runCatching(::release).exceptionOrNull(),
            )
        val failure = failures.firstOrNull() ?: return
        failures.filterNot { it === failure }.forEach(failure::addSuppressed)
        throw failure
    }

    private fun acquire(): Long {
        val startedAt = System.nanoTime()
        var holder: String? = null
        while (true) {
            val requestStartedAt = System.nanoTime()
            val result =
                executeBefore(
                    acquireStatement
                        .bind(lockName, owner, leaseSeconds)
                        .setSerialConsistencyLevel(DefaultConsistencyLevel.SERIAL),
                    requestStartedAt + leaseBudgetNanos,
                )
            if (result.wasApplied()) {
                return requestStartedAt
            }
            holder = result.one()?.getString("owner") ?: holder

            val elapsed = Duration.ofNanos(System.nanoTime() - startedAt)
            if (elapsed >= properties.acquireTimeout) {
                throw FlywayException(
                    "Could not acquire Cassandra migration lock '$lockName' within " +
                        "${properties.acquireTimeout}; current holder: ${holder ?: "unknown"}",
                )
            }
            val remainingMillis = properties.acquireTimeout.minus(elapsed).toMillis()
            try {
                Thread.sleep(minOf(1_000, max(1, remainingMillis)))
            } catch (failure: InterruptedException) {
                Thread.currentThread().interrupt()
                throw FlywayException("Interrupted while waiting for Cassandra migration lock '$lockName'", failure)
            }
        }
    }

    private fun renew(deadline: Long): Boolean =
        executeBefore(
            renewStatement
                .bind(leaseSeconds, owner, lockName, owner)
                .setSerialConsistencyLevel(DefaultConsistencyLevel.SERIAL),
            deadline,
        ).wasApplied()

    private fun executeBefore(
        statement: BoundStatement,
        deadline: Long,
    ): AsyncResultSet {
        if (deadline - System.nanoTime() <= 0) {
            throw TimeoutException("Cassandra migration lock '$lockName' lease deadline elapsed")
        }
        val response = session.executeAsync(statement).toCompletableFuture()
        try {
            val result = response.get(max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
            if (deadline - System.nanoTime() <= 0) {
                throw TimeoutException("Cassandra migration lock '$lockName' lease deadline elapsed")
            }
            return result
        } finally {
            if (!response.isDone) response.cancel(true)
        }
    }

    private fun release() {
        val released =
            session
                .execute(
                    releaseStatement
                        .bind(lockName, owner)
                        .setSerialConsistencyLevel(DefaultConsistencyLevel.SERIAL),
                ).wasApplied()
        if (!released) {
            throw FlywayException("Cassandra migration lock '$lockName' was lost before release")
        }
    }

    companion object {
        const val TABLE = "flyway_schema_lock"

        private val logger: Logger = LoggerFactory.getLogger(CassandraMigrationLock::class.java)
    }
}
