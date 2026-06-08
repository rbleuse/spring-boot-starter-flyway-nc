package io.github.rbleuse.flywaync

import io.kotest.matchers.shouldBe
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
            .withPropertyValues("spring.flyway-nc.url=mongodb://localhost:27017/test")

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
                .dataSource("mongodb://localhost:27017/other", null, null)
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
                        override val url: String = "mongodb://compose-host:37017/compose_db"
                        override val user: String = "compose-user"
                        override val password: String = "compose-password"
                    }
                },
            ).run { context ->
                val configuration = context.getBean<Flyway>().configuration
                configuration.url shouldBe "mongodb://compose-host:37017/compose_db"
                configuration.user shouldBe "compose-user"
                configuration.password shouldBe "compose-password"
            }
    }

    @Test
    fun `multi-host URL keeps its path and ignores default-schema`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlywayNcAutoConfiguration::class.java))
            .withUserConfiguration(NoOpMigrationStrategyConfig::class.java)
            .withPropertyValues(
                "spring.flyway-nc.url=mongodb://host1:27017,host2:27017/explicit_db?replicaSet=rs0",
                "spring.flyway-nc.default-schema=ignored_db",
            ).run { context ->
                val configuration = context.getBean<Flyway>().configuration
                configuration.url shouldBe "mongodb://host1:27017,host2:27017/explicit_db?replicaSet=rs0"
                configuration.defaultSchema shouldBe null
            }
    }

    @Test
    fun `default-schema is appended to a multi-host URL without a path`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlywayNcAutoConfiguration::class.java))
            .withUserConfiguration(NoOpMigrationStrategyConfig::class.java)
            .withPropertyValues(
                "spring.flyway-nc.url=mongodb://host1:27017,host2:27017?replicaSet=rs0",
                "spring.flyway-nc.default-schema=mydb",
            ).run { context ->
                context.getBean<Flyway>().configuration.url shouldBe
                    "mongodb://host1:27017,host2:27017/mydb?replicaSet=rs0"
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
