package io.github.rbleuse.flywaync

import org.flywaydb.core.Flyway
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@AutoConfiguration
@ConditionalOnClass(Flyway::class)
@ConditionalOnBooleanProperty(name = ["spring.flyway-nc.enabled"], matchIfMissing = true)
@EnableConfigurationProperties(FlywayNcProperties::class)
class FlywayNcAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun flywayNcConnectionDetails(props: FlywayNcProperties): FlywayNcConnectionDetails =
        object : FlywayNcConnectionDetails {
            override val url: String = requireNotNull(props.url) { "spring.flyway-nc.url must be configured" }
            override val user: String? = props.user
            override val password: String? = props.password
        }

    @Bean
    @ConditionalOnMissingBean
    fun flyway(
        props: FlywayNcProperties,
        connectionDetails: FlywayNcConnectionDetails,
        customizers: ObjectProvider<FlywayConfigurationCustomizer>,
    ): Flyway {
        val rawUrl = connectionDetails.url
        val urlCarriesSchema = rawUrl.hasSchemaInPath()
        val resolvedUrl = if (urlCarriesSchema) rawUrl else rawUrl.withSchemaIfMissing(props.defaultSchema)
        val config = Flyway.configure()
            .dataSource(resolvedUrl, connectionDetails.user, connectionDetails.password)
            .locations(*props.locations.toTypedArray())
        if (props.migrationSuffixes.isNotEmpty()) {
            config.sqlMigrationSuffixes(*props.migrationSuffixes.toTypedArray())
        }
        // Only configure defaultSchema when the URL didn't already carry one; otherwise the
        // user-supplied URL's schema would silently lose to spring.flyway-nc.default-schema.
        if (!urlCarriesSchema) {
            props.defaultSchema?.takeUnless { it.isBlank() }?.let { config.defaultSchema(it) }
        }
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

    private fun String.hasSchemaInPath(): Boolean {
        val uri = runCatching { URI(this) }.getOrNull() ?: return false
        return !uri.rawPath.isNullOrEmpty() && uri.rawPath != "/"
    }

    private fun String.withSchemaIfMissing(schema: String?): String {
        if (schema.isNullOrBlank()) return this
        if (this.hasSchemaInPath()) return this
        val encoded = URLEncoder.encode(schema, StandardCharsets.UTF_8)
        val queryIdx = this.indexOf('?')
        val base = (if (queryIdx >= 0) this.substring(0, queryIdx) else this).trimEnd('/')
        val query = if (queryIdx >= 0) this.substring(queryIdx) else ""
        return "$base/$encoded$query"
    }
}
