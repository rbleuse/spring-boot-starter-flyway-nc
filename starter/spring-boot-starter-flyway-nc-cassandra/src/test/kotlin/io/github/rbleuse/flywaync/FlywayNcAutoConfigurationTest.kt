package io.github.rbleuse.flywaync

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.atomic.AtomicInteger

class FlywayNcAutoConfigurationTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlywayNcAutoConfiguration::class.java))
            .withUserConfiguration(NoOpMigrationStrategyConfig::class.java)
            .withPropertyValues("spring.flyway-nc.url=cassandra://localhost:9042/test?localdatacenter=dc1")

    @Test
    fun `creates Flyway and initializer beans by default`() {
        contextRunner.run { context ->
            context.containsBean("flyway") shouldBe true
            context.containsBean("flywayInitializer") shouldBe true
            context.getBean<Flyway>()
            context.getBean<FlywayNcMigrationInitializer>()
        }
    }

    @Test
    fun `does not create beans when enabled is false`() {
        contextRunner
            .withPropertyValues("spring.flyway-nc.enabled=false")
            .run { context ->
                context.containsBean("flyway") shouldBe false
                context.containsBean("flywayInitializer") shouldBe false
            }
    }

    @Test
    fun `user-defined Flyway bean overrides autoconfig bean`() {
        val userFlyway =
            Flyway
                .configure()
                .dataSource("cassandra://localhost:9042/other?localdatacenter=dc1", null, null)
                .load()
        contextRunner
            .withBean(Flyway::class.java, { userFlyway })
            .run { context ->
                context.getBean<Flyway>() shouldBe userFlyway
            }
    }

    @Test
    fun `uses connection details when url property is not configured`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlywayNcAutoConfiguration::class.java))
            .withUserConfiguration(NoOpMigrationStrategyConfig::class.java)
            .withBean(
                FlywayNcConnectionDetails::class.java,
                {
                    object : FlywayNcConnectionDetails {
                        override val url: String = "cassandra://compose-host:19042/compose_keyspace?localdatacenter=compose-dc"
                        override val user: String = "compose-user"
                        override val password: String = "compose-password"
                    }
                },
            ).run { context ->
                val configuration = context.getBean<Flyway>().configuration
                configuration.url shouldBe
                    "cassandra://compose-host:19042/compose_keyspace?localdatacenter=compose-dc"
                configuration.user shouldBe "compose-user"
                configuration.password shouldBe "compose-password"
            }
    }

    @Test
    fun `default-schema is URL-encoded and appended when the URL has no path`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlywayNcAutoConfiguration::class.java))
            .withUserConfiguration(NoOpMigrationStrategyConfig::class.java)
            .withPropertyValues(
                "spring.flyway-nc.url=cassandra://localhost:9042?localdatacenter=dc1",
                "spring.flyway-nc.default-schema=my keyspace",
            ).run { context ->
                context.getBean<Flyway>().configuration.url shouldBe
                    "cassandra://localhost:9042/my%20keyspace?localdatacenter=dc1"
            }
    }

    @Test
    fun `URL-supplied schema wins over default-schema`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlywayNcAutoConfiguration::class.java))
            .withUserConfiguration(NoOpMigrationStrategyConfig::class.java)
            .withPropertyValues(
                "spring.flyway-nc.url=cassandra://localhost:9042/explicit_ks?localdatacenter=dc1",
                "spring.flyway-nc.default-schema=ignored_ks",
            ).run { context ->
                val configuration = context.getBean<Flyway>().configuration
                configuration.url shouldBe "cassandra://localhost:9042/explicit_ks?localdatacenter=dc1"
                configuration.defaultSchema shouldBe null
            }
    }

    @Test
    fun `fails with a helpful message when no connection source is available`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlywayNcAutoConfiguration::class.java))
            .withUserConfiguration(NoOpMigrationStrategyConfig::class.java)
            .run { context ->
                val failure = context.startupFailure
                failure shouldNotBe null
                val messages =
                    generateSequence<Throwable>(failure) { it.cause }
                        .mapNotNull { it.message }
                        .toList()
                messages.any {
                    it.contains("spring.flyway-nc.url") && it.contains("service connection", ignoreCase = true)
                } shouldBe true
            }
    }

    @Test
    fun `customizer is invoked on the Flyway builder before load`() {
        val callCount = AtomicInteger()
        contextRunner
            .withBean(
                FlywayConfigurationCustomizer::class.java,
                { FlywayConfigurationCustomizer { callCount.incrementAndGet() } },
            ).run { context ->
                context.getBean<Flyway>()
                callCount.get() shouldBe 1
            }
    }

    @Configuration
    class NoOpMigrationStrategyConfig {
        @Bean
        fun noOpMigrationStrategy(): FlywayNcMigrationStrategy =
            FlywayNcMigrationStrategy { /* skip migrate() to avoid hitting a real DB */ }
    }
}
