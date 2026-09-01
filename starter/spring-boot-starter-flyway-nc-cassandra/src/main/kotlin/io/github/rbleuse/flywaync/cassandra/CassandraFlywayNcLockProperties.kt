package io.github.rbleuse.flywaync.cassandra

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.DurationUnit
import java.time.Duration
import java.time.temporal.ChronoUnit

@ConfigurationProperties("spring.flyway-nc.lock")
data class CassandraFlywayNcLockProperties(
    val enabled: Boolean = true,
    @DurationUnit(ChronoUnit.SECONDS)
    val leaseDuration: Duration = Duration.ofMinutes(2),
    @DurationUnit(ChronoUnit.SECONDS)
    val acquireTimeout: Duration = Duration.ofMinutes(10),
) {
    init {
        require(leaseDuration >= Duration.ofSeconds(1)) {
            "spring.flyway-nc.lock.lease-duration must be at least one second"
        }
        require(leaseDuration <= Duration.ofSeconds(Int.MAX_VALUE.toLong() - 1)) {
            "spring.flyway-nc.lock.lease-duration is too large"
        }
        require(!acquireTimeout.isNegative) {
            "spring.flyway-nc.lock.acquire-timeout must not be negative"
        }
    }

    // Cassandra rounds expiration timestamps to seconds; reserve one extra second.
    internal val leaseSeconds =
        leaseDuration.seconds.toInt() + if (leaseDuration.nano == 0) 1 else 2
}
