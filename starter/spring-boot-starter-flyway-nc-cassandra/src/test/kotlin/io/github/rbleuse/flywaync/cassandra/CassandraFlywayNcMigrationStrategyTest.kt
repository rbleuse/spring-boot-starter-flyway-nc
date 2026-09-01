package io.github.rbleuse.flywaync.cassandra

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Duration

class CassandraFlywayNcMigrationStrategyTest {
    @Test
    fun `disabled lock delegates to migrate without info or a lock session`() {
        val flyway = mockk<Flyway>(relaxed = true)
        every { flyway.configuration } returns
            Flyway
                .configure()
                .dataSource("cassandra://unreachable:9042/test?localdatacenter=datacenter1", null, null)
                .load()
                .configuration

        CassandraFlywayNcMigrationStrategy(
            CassandraFlywayNcLockProperties(enabled = false),
        ).migrate(flyway)

        verify(exactly = 1) { flyway.migrate() }
        verify(exactly = 0) { flyway.info() }
        verify(exactly = 0) { flyway.validate() }
    }

    @ParameterizedTest
    @CsvSource("1000, 2", "1500, 3", "120000, 121")
    fun `lease TTL includes rounding and a second of expiry margin`(
        leaseMillis: Long,
        ttlSeconds: Int,
    ) {
        CassandraFlywayNcLockProperties(leaseDuration = Duration.ofMillis(leaseMillis)).leaseSeconds shouldBe ttlSeconds
    }

    @Test
    fun `maximum lease leaves room for the TTL margin without overflow`() {
        val maximumLease = Duration.ofSeconds(Int.MAX_VALUE.toLong() - 1)
        CassandraFlywayNcLockProperties(leaseDuration = maximumLease).leaseSeconds shouldBe Int.MAX_VALUE
        shouldThrow<IllegalArgumentException> {
            CassandraFlywayNcLockProperties(leaseDuration = maximumLease.plusNanos(1))
        }
    }
}
