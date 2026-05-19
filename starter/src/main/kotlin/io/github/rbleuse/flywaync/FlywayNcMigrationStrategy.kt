package io.github.rbleuse.flywaync

import org.flywaydb.core.Flyway

/**
 * Strategy used to initialize [Flyway] migration. Custom implementations may be
 * registered as a `@Bean` to override the default migration behavior.
 */
fun interface FlywayNcMigrationStrategy {
    /**
     * Trigger Flyway migration.
     *
     * @param flyway the [Flyway] instance to migrate
     */
    fun migrate(flyway: Flyway)
}
