package com.github.rbleuse.flywaync

import org.flywaydb.core.api.configuration.FluentConfiguration

/**
 * Callback interface that can be implemented by beans wishing to customize the flyway
 * configuration.
 */
fun interface FlywayConfigurationCustomizer {
    /**
     * Customize the flyway configuration.
     * @param configuration the {@link FluentConfiguration} to customize
     */
    fun customize(configuration: FluentConfiguration)
}
