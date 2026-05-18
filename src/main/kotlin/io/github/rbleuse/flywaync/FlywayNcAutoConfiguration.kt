package io.github.rbleuse.flywaync

import org.flywaydb.core.Flyway
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@AutoConfiguration
@ConditionalOnClass(Flyway::class)
@ConditionalOnBooleanProperty(name = ["spring.flyway-nc.enabled"], matchIfMissing = true)
@EnableConfigurationProperties(FlywayNcProperties::class)
class FlywayNcAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun flywayNcConnectionDetails(props: FlywayNcProperties): FlywayNcConnectionDetails =
        FlywayNcConnectionDetails(
            url = requireNotNull(props.url) { "spring.flyway-nc.url must be configured" },
            user = props.user,
            password = props.password,
        )

    @Bean
    @ConditionalOnMissingBean
    fun flyway(
        props: FlywayNcProperties,
        connectionDetails: FlywayNcConnectionDetails,
        customizers: ObjectProvider<FlywayConfigurationCustomizer>,
    ): Flyway {
        val config = Flyway.configure()
            .dataSource(connectionDetails.url, connectionDetails.user, connectionDetails.password)
            .locations(*props.locations.toTypedArray())
        props.migrationSuffixes?.let { config.sqlMigrationSuffixes(*it.toTypedArray()) }
        props.defaultSchema?.let { config.defaultSchema(it) }
        customizers.orderedStream().forEach { it.customize(config) }
        return config.load()
    }

    @Bean
    @ConditionalOnMissingBean
    fun flywayInitializer(
        flyway: Flyway,
        strategy: ObjectProvider<FlywayNcMigrationStrategy>,
    ): FlywayNcMigrationInitializer =
        FlywayNcMigrationInitializer(flyway, strategy.ifAvailable)
}
