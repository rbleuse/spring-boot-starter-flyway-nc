package io.github.rbleuse.flywaync.cassandra

import com.datastax.oss.driver.api.core.CqlSession
import io.github.rbleuse.flywaync.FlywayConfigurationCustomizer
import io.github.rbleuse.flywaync.FlywayNcAutoConfiguration
import io.github.rbleuse.flywaync.FlywayNcMigrationStrategy
import io.github.rbleuse.flywaync.FlywayNcProperties
import org.flywaydb.core.Flyway
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

@AutoConfiguration(before = [FlywayNcAutoConfiguration::class])
@ConditionalOnClass(Flyway::class, CqlSession::class)
@Import(FlywayNcAutoConfiguration::class)
@ConditionalOnBooleanProperty(name = ["spring.flyway-nc.enabled"], matchIfMissing = true)
@EnableConfigurationProperties(FlywayNcProperties::class, CassandraFlywayNcLockProperties::class)
class CassandraFlywayNcAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(FlywayNcMigrationStrategy::class)
    fun cassandraFlywayNcMigrationStrategy(lockProperties: CassandraFlywayNcLockProperties): FlywayNcMigrationStrategy =
        CassandraFlywayNcMigrationStrategy(lockProperties)

    /**
     * Applies Cassandra's `.cql` suffix and non-transactional execution defaults.
     *
     * Runs at the highest precedence among [FlywayConfigurationCustomizer]s so any user-supplied
     * customizer can override either default. The guard on
     * [FlywayNcProperties.migrationSuffixes] keeps an explicit `spring.flyway-nc.migration-suffixes`
     * authoritative.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun cassandraCqlSuffixCustomizer(props: FlywayNcProperties): FlywayConfigurationCustomizer =
        FlywayConfigurationCustomizer { config ->
            config.executeInTransaction(false)
            if (props.migrationSuffixes.isEmpty()) {
                config.sqlMigrationSuffixes(".cql")
            }
        }
}
