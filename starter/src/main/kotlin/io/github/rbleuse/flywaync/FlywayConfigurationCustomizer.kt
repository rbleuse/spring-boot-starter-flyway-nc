package io.github.rbleuse.flywaync

import org.flywaydb.core.api.configuration.FluentConfiguration

/**
 * Callback interface that can be implemented by beans wishing to customize the Flyway
 * configuration.
 */
fun interface FlywayConfigurationCustomizer {
    /**
     * Customize the Flyway configuration.
     *
     * @param configuration the [FluentConfiguration] to customize
     */
    fun customize(configuration: FluentConfiguration)
}
