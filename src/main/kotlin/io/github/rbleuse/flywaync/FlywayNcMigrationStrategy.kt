package io.github.rbleuse.flywaync

import org.flywaydb.core.Flyway

/**
 * Strategy used to initialize {@link Flyway} migration. Custom implementations may be
 * registered as a {@code @Bean} to override the default migration behavior.
 */
fun interface FlywayNcMigrationStrategy {
    /**
     * Trigger flyway migration.
     * @param flyway the flyway instance
     */
    fun migrate(flyway: Flyway)
}
