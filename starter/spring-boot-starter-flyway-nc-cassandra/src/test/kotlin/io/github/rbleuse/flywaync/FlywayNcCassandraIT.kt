package io.github.rbleuse.flywaync

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.internal.core.session.DefaultSession
import io.github.rbleuse.flywaync.cassandra.CassandraFlywayNcLockProperties
import io.github.rbleuse.flywaync.cassandra.CassandraFlywayNcMigrationStrategy
import io.github.rbleuse.flywaync.cassandra.CassandraMigrationLock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.flywaydb.core.api.output.MigrateResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.cassandra.CassandraContainer
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(
    properties = [
        "spring.flyway-nc.default-schema=flyway_nc_it",
    ],
)
@SpringBootConfiguration
@EnableAutoConfiguration
@Testcontainers(disabledWithoutDocker = true)
class FlywayNcCassandraIT {
    @AfterEach
    fun `all Cassandra sessions are closed`() {
        // Read the driver's leak counter: checking only the last session misses overwritten sessions.
        val count = DefaultSession::class.java.getDeclaredField("INSTANCE_COUNT").apply { isAccessible = true }
        (count.get(null) as AtomicInteger).get() shouldBe 0
    }

    companion object {
        private const val KEYSPACE = "flyway_nc_it"

        @ServiceConnection
        val cassandra: CassandraContainer =
            CassandraContainer(
                DockerImageName.parse("cassandra:5.0.9"),
            ).withInitScript("cassandra-init.cql")
    }

    @Test
    fun `lock uses Testcontainers connection details without a configured URL`() {
        cqlSession().use { session ->
            val tables =
                session
                    .execute(
                        "SELECT table_name FROM system_schema.tables WHERE keyspace_name = ?",
                        KEYSPACE,
                    ).all()
                    .map { it.getString("table_name") }

            tables shouldContain "flyway_nc_it_table"
            tables shouldContain "flyway_schema_history"
            tables shouldContain "flyway_schema_lock"

            val historyVersions =
                session
                    .execute(
                        "SELECT version FROM flyway_schema_history",
                    ).all()
                    .map { it.getString("version") }
            historyVersions shouldBe listOf("1")

            session
                .execute(
                    "SELECT owner FROM flyway_schema_lock WHERE lock_name = ?",
                    "$KEYSPACE.flyway_schema_history",
                ).one() shouldBe null

            session
                .execute(
                    "SELECT keyspace_name FROM system_schema.keyspaces WHERE keyspace_name = 'flyway_nc_lock'",
                ).one() shouldBe null
        }
    }

    @Test
    fun `lock lease is renewed and serializes concurrent migrations`() {
        val url =
            "cassandra://${cassandra.contactPoint.hostName}:${cassandra.contactPoint.port}/" +
                "$KEYSPACE?localdatacenter=${cassandra.localDatacenter}"
        val configuration =
            Flyway
                .configure()
                .dataSource(url, null, null)
                .load()
                .configuration
        val strategy =
            CassandraFlywayNcMigrationStrategy(
                CassandraFlywayNcLockProperties(
                    enabled = true,
                    leaseDuration = Duration.ofSeconds(2),
                    acquireTimeout = Duration.ofSeconds(8),
                ),
            )
        val firstEntered = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val entries = AtomicInteger()
        val active = AtomicInteger()
        val maxActive = AtomicInteger()

        fun flywayMock(): Flyway =
            mockk<Flyway>(relaxed = true).also { flyway ->
                every { flyway.configuration } returns configuration
                every { flyway.migrate() } answers {
                    val activeNow = active.incrementAndGet()
                    maxActive.updateAndGet { maxOf(it, activeNow) }
                    try {
                        if (entries.incrementAndGet() == 1) {
                            firstEntered.countDown()
                            check(releaseFirst.await(10, TimeUnit.SECONDS))
                        } else {
                            secondEntered.countDown()
                        }
                    } finally {
                        active.decrementAndGet()
                    }
                    MigrateResult().apply { migrationsExecuted = 1 }
                }
            }

        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit { strategy.migrate(flywayMock()) }
            check(firstEntered.await(10, TimeUnit.SECONDS))
            val second = executor.submit { strategy.migrate(flywayMock()) }

            secondEntered.await(2_500, TimeUnit.MILLISECONDS) shouldBe false
            releaseFirst.countDown()

            first.get(15, TimeUnit.SECONDS)
            second.get(15, TimeUnit.SECONDS)
            entries.get() shouldBe 2
            maxActive.get() shouldBe 1
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }

    @ParameterizedTest
    @CsvSource("false, 1", "true, 1", "true, 42")
    fun `lock table does not cause an empty keyspace to be baselined`(
        baselineOnMigrate: Boolean,
        baselineVersion: String,
    ) {
        val emptyKeyspace = "flyway_nc_empty_${baselineOnMigrate}_$baselineVersion"
        cqlSession().use { session ->
            session.execute(
                "CREATE KEYSPACE $emptyKeyspace " +
                    "WITH replication = { 'class': 'SimpleStrategy', 'replication_factor': 1 }",
            )
        }
        val flyway =
            Flyway
                .configure()
                .dataSource(
                    "cassandra://${cassandra.contactPoint.hostName}:${cassandra.contactPoint.port}/" +
                        "$emptyKeyspace?localdatacenter=${cassandra.localDatacenter}",
                    null,
                    null,
                ).locations("classpath:db/migration")
                .sqlMigrationSuffixes(".cql")
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion(baselineVersion)
                .executeInTransaction(false)
                .load()

        val strategy = CassandraFlywayNcMigrationStrategy(CassandraFlywayNcLockProperties())
        repeat(2) { strategy.migrate(flyway) }

        cqlSession().use { session ->
            session
                .execute("SELECT version FROM $emptyKeyspace.flyway_schema_history WHERE partition = 'flyway'")
                .all()
                .map { it.getString("version") } shouldBe listOf("1")
            session
                .execute(
                    "SELECT table_name FROM system_schema.tables WHERE keyspace_name = ?",
                    emptyKeyspace,
                ).map { it.getString("table_name") } shouldContain "flyway_nc_it_table"
        }
        flyway.configuration.isBaselineOnMigrate shouldBe baselineOnMigrate
        flyway.configuration.baselineVersion.toString() shouldBe baselineVersion
    }

    @Test
    fun `one second lease renews across Cassandra expiry boundaries`() {
        cqlSession().use { session ->
            val halted = CountDownLatch(1)
            CassandraMigrationLock(
                session,
                "$KEYSPACE.short_lease",
                CassandraFlywayNcLockProperties(leaseDuration = Duration.ofSeconds(1)),
            ) {
                halted.countDown()
                error("Unexpected JVM halt")
            }.withLock {
                halted.await(4, TimeUnit.SECONDS) shouldBe false
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `non-empty keyspace without history respects baseline opt-in`(baselineOnMigrate: Boolean) {
        val existingKeyspace = "flyway_nc_existing_$baselineOnMigrate"
        cqlSession().use { session ->
            session.execute(
                "CREATE KEYSPACE IF NOT EXISTS $existingKeyspace " +
                    "WITH replication = { 'class': 'SimpleStrategy', 'replication_factor': 1 }",
            )
            session.execute("CREATE TABLE $existingKeyspace.existing_table (id uuid PRIMARY KEY)")
        }
        val flyway =
            Flyway
                .configure()
                .dataSource(
                    "cassandra://${cassandra.contactPoint.hostName}:${cassandra.contactPoint.port}/" +
                        "$existingKeyspace?localdatacenter=${cassandra.localDatacenter}",
                    null,
                    null,
                ).locations("classpath:db/migration")
                .sqlMigrationSuffixes(".cql")
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion("0")
                .executeInTransaction(false)
                .load()

        val strategy = CassandraFlywayNcMigrationStrategy(CassandraFlywayNcLockProperties())
        if (baselineOnMigrate) {
            strategy.migrate(flyway)
        } else {
            shouldThrow<FlywayException> { strategy.migrate(flyway) }
        }

        cqlSession().use { session ->
            val tables =
                session
                    .execute(
                        "SELECT table_name FROM system_schema.tables WHERE keyspace_name = ?",
                        existingKeyspace,
                    ).mapNotNull { it.getString("table_name") }
            tables shouldContain "existing_table"
            tables.contains("flyway_nc_it_table") shouldBe baselineOnMigrate
            tables.contains("flyway_schema_history") shouldBe baselineOnMigrate
        }
    }

    @Test
    fun `failed migration is recorded and blocks the next runner`() {
        val failedKeyspace = "flyway_nc_failure_it"
        cqlSession().use { session ->
            session.execute(
                "CREATE KEYSPACE IF NOT EXISTS $failedKeyspace " +
                    "WITH replication = { 'class': 'SimpleStrategy', 'replication_factor': 1 }",
            )
        }
        val url =
            "cassandra://${cassandra.contactPoint.hostName}:${cassandra.contactPoint.port}/" +
                "$failedKeyspace?localdatacenter=${cassandra.localDatacenter}"

        fun flyway(): Flyway =
            Flyway
                .configure()
                .dataSource(url, null, null)
                .locations("classpath:db/failure")
                .sqlMigrationSuffixes(".cql")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .executeInTransaction(false)
                .table("flyway_failure_history")
                .load()

        val strategy =
            CassandraFlywayNcMigrationStrategy(
                CassandraFlywayNcLockProperties(enabled = true),
            )
        val failedFlyway = flyway()
        repeat(2) {
            shouldThrow<FlywayException> { strategy.migrate(failedFlyway) }
            `all Cassandra sessions are closed`()
        }

        cqlSession().use { session ->
            val history =
                session
                    .execute(
                        "SELECT version, success FROM $failedKeyspace.flyway_failure_history WHERE partition = 'flyway'",
                    ).all()
                    .associate { it.getString("version") to it.getBoolean("success") }
            history["1"] shouldBe false
        }
    }

    @Test
    fun `repeated up-to-date migrations close sessions with and without locking and validation`() {
        for (locking in listOf(true, false)) {
            for (validation in listOf(true, false)) {
                val flyway =
                    Flyway
                        .configure()
                        .dataSource(
                            "cassandra://${cassandra.contactPoint.hostName}:${cassandra.contactPoint.port}/" +
                                "$KEYSPACE?localdatacenter=${cassandra.localDatacenter}",
                            null,
                            null,
                        ).locations("classpath:db/migration")
                        .sqlMigrationSuffixes(".cql")
                        .validateOnMigrate(validation)
                        .executeInTransaction(false)
                        .load()
                val strategy =
                    CassandraFlywayNcMigrationStrategy(
                        CassandraFlywayNcLockProperties(enabled = locking),
                    )
                repeat(2) {
                    strategy.migrate(flyway)
                    `all Cassandra sessions are closed`()
                }
            }
        }
    }

    private fun cqlSession(): CqlSession =
        CqlSession
            .builder()
            .addContactPoint(InetSocketAddress(cassandra.contactPoint.hostName, cassandra.contactPoint.port))
            .withLocalDatacenter(cassandra.localDatacenter)
            .withKeyspace(KEYSPACE)
            .build()
}
