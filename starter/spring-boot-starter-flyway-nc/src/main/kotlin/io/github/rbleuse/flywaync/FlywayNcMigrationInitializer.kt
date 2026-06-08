package io.github.rbleuse.flywaync

import org.flywaydb.core.Flyway
import org.springframework.beans.factory.InitializingBean
import org.springframework.core.Ordered

/**
 * [InitializingBean] used to trigger [Flyway] migration through the
 * [FlywayNcMigrationStrategy].
 */
class FlywayNcMigrationInitializer(
    private val flyway: Flyway,
    private val strategy: FlywayNcMigrationStrategy?,
) : InitializingBean,
    Ordered {
    override fun afterPropertiesSet() {
        if (strategy != null) strategy.migrate(flyway) else flyway.migrate()
    }

    override fun getOrder(): Int = 0
}
