package io.github.rbleuse.flywaync.cassandra

import io.github.rbleuse.flywaync.FlywayConfigurationCustomizer
import io.github.rbleuse.flywaync.FlywayNcAutoConfiguration
import io.github.rbleuse.flywaync.FlywayNcProperties
import org.flywaydb.core.Flyway
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

@AutoConfiguration(before = [FlywayNcAutoConfiguration::class])
@ConditionalOnClass(Flyway::class)
@Import(FlywayNcAutoConfiguration::class)
@ConditionalOnBooleanProperty(name = ["spring.flyway-nc.enabled"], matchIfMissing = true)
@EnableConfigurationProperties(FlywayNcProperties::class)
class CassandraFlywayNcAutoConfiguration {

    /**
     * Cassandra's Flyway NC engine only executes `.cql` migrations, but Flyway's built-in default
     * suffix is `.sql`. Default it to `.cql` so consumers don't have to repeat the configuration.
     *
     * Runs at the highest precedence among [FlywayConfigurationCustomizer]s so any user-supplied
     * customizer runs after it and can override the suffix, and the guard on
     * [FlywayNcProperties.migrationSuffixes] keeps an explicit `spring.flyway-nc.migration-suffixes`
     * authoritative.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun cassandraCqlSuffixCustomizer(props: FlywayNcProperties): FlywayConfigurationCustomizer =
        FlywayConfigurationCustomizer { config ->
            if (props.migrationSuffixes.isEmpty()) {
                config.sqlMigrationSuffixes(".cql")
            }
        }
}
