package com.github.rbleuse.flywaync

import org.flywaydb.core.Flyway
import org.springframework.beans.factory.InitializingBean
import org.springframework.core.Ordered

/**
 * {@link InitializingBean} used to trigger {@link Flyway} migration through the
 * {@link FlywayMigrationStrategy}.
 */
class FlywayNcMigrationInitializer(
    private val flyway: Flyway,
    private val strategy: FlywayNcMigrationStrategy?,
) : InitializingBean, Ordered {

    override fun afterPropertiesSet() {
        if (strategy != null) strategy.migrate(flyway) else flyway.migrate()
    }

    override fun getOrder(): Int = 0
}
