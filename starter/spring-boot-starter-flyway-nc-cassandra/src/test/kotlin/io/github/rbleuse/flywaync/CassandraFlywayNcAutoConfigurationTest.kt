package io.github.rbleuse.flywaync

import io.github.rbleuse.flywaync.cassandra.CassandraFlywayNcAutoConfiguration
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class CassandraFlywayNcAutoConfigurationTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    CassandraFlywayNcAutoConfiguration::class.java,
                    FlywayNcAutoConfiguration::class.java,
                ),
            ).withUserConfiguration(NoOpMigrationStrategyConfig::class.java)
            .withPropertyValues("spring.flyway-nc.url=cassandra://localhost:9042/test?localdatacenter=dc1")

    @Test
    fun `defaults sqlMigrationSuffixes to cql when none configured`() {
        contextRunner.run { context ->
            context
                .getBean<Flyway>()
                .configuration.sqlMigrationSuffixes
                .toList() shouldBe listOf(".cql")
        }
    }

    @Test
    fun `explicit migration-suffixes property wins over the cql default`() {
        contextRunner
            .withPropertyValues("spring.flyway-nc.migration-suffixes[0]=.cassandra")
            .run { context ->
                context
                    .getBean<Flyway>()
                    .configuration.sqlMigrationSuffixes
                    .toList() shouldBe listOf(".cassandra")
            }
    }

    @Configuration
    class NoOpMigrationStrategyConfig {
        @Bean
        fun noOpMigrationStrategy(): FlywayNcMigrationStrategy =
            FlywayNcMigrationStrategy { /* skip migrate() to avoid hitting a real DB */ }
    }
}
