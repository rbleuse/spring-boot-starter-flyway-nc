package io.github.rbleuse.flywaync.cassandra

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel
import com.datastax.oss.driver.api.core.cql.AsyncResultSet
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import com.datastax.oss.driver.api.core.cql.ResultSet
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CassandraMigrationLockTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `rejected or stalled heartbeat halts before the lease expires`(stalled: Boolean) {
        val session = mockk<CqlSession>()
        val acquireStatement = mockk<PreparedStatement>()
        val renewStatement = mockk<PreparedStatement>()
        val releaseStatement = mockk<PreparedStatement>()
        val acquireBound = mockk<BoundStatement>()
        val renewBound = mockk<BoundStatement>()
        val releaseBound = mockk<BoundStatement>()
        val acquired = mockk<AsyncResultSet>()
        val renewalRejected = mockk<AsyncResultSet>()
        val released = mockk<ResultSet>()
        val halted = CountDownLatch(1)
        val renewalResponse =
            if (stalled) CompletableFuture() else CompletableFuture.completedFuture(renewalRejected)
        var acquiredAt = 0L
        var haltedAt = 0L

        every { session.execute(any<String>()) } returns mockk(relaxed = true)
        every { session.prepare(any<String>()) } returnsMany listOf(acquireStatement, renewStatement, releaseStatement)
        every { acquireStatement.bind(any(), any(), any()) } returns acquireBound
        every { renewStatement.bind(any(), any(), any(), any()) } returns renewBound
        every { releaseStatement.bind(any(), any()) } returns releaseBound
        every { acquireBound.setSerialConsistencyLevel(DefaultConsistencyLevel.SERIAL) } returns acquireBound
        every { renewBound.setSerialConsistencyLevel(DefaultConsistencyLevel.SERIAL) } returns renewBound
        every { releaseBound.setSerialConsistencyLevel(DefaultConsistencyLevel.SERIAL) } returns releaseBound
        every { session.executeAsync(acquireBound) } answers {
            acquiredAt = System.nanoTime()
            CompletableFuture.completedFuture(acquired)
        }
        every { session.executeAsync(renewBound) } returns renewalResponse
        every { session.execute(releaseBound) } returns released
        every { acquired.wasApplied() } returns true
        every { renewalRejected.wasApplied() } returns false
        every { released.wasApplied() } returns true

        CassandraMigrationLock(
            session,
            "keyspace.flyway_schema_history",
            CassandraFlywayNcLockProperties(leaseDuration = Duration.ofSeconds(1)),
        ) {
            haltedAt = System.nanoTime()
            halted.countDown()
            throw Halted()
        }.withLock {
            halted.await(2, TimeUnit.SECONDS) shouldBe true
            (haltedAt - acquiredAt) shouldBeLessThan TimeUnit.SECONDS.toNanos(1)
        }
        if (stalled) renewalResponse.isCancelled shouldBe true
    }

    private class Halted : Error()
}
