package io.github.rbleuse.flywaync

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.DurationUnit
import java.time.Duration
import java.time.temporal.ChronoUnit

@ConfigurationProperties("spring.flyway-nc")
data class FlywayNcProperties(
    val url: String? = null,
    val user: String? = null,
    val password: String? = null,
    val locations: List<String> = listOf("classpath:db/migration"),
    val migrationSuffixes: List<String> = emptyList(),
    val defaultSchema: String? = null,
    val baselineOnMigrate: Boolean = false,
    val baselineVersion: String = "1",
    val validateOnMigrate: Boolean = true,
    val connectRetries: Int = 0,
    @DurationUnit(ChronoUnit.SECONDS)
    val connectRetriesInterval: Duration = Duration.ofSeconds(120),
    val validateMigrationNaming: Boolean = false,
    val failOnMissingLocations: Boolean = false,
    val target: String = "latest",
    val table: String = "flyway_schema_history",
    val createSchemas: Boolean = true,
    val outOfOrder: Boolean = false,
)
