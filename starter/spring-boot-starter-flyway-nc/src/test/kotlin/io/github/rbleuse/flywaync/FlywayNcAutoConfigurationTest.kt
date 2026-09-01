package io.github.rbleuse.flywaync

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.atomic.AtomicInteger

class FlywayNcAutoConfigurationTest {
    private lateinit var configuration: FluentConfiguration
    private lateinit var flyway: Flyway

    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlywayNcAutoConfiguration::class.java))
            .withUserConfiguration(NoOpMigrationStrategyConfig::class.java)
            .withPropertyValues("spring.flyway-nc.url=test://localhost:1234/schema")

    @BeforeEach
    fun setUp() {
        configuration = mockk()
        flyway = mockk()
        mockkStatic(Flyway::class)
        every { Flyway.configure() } returns configuration
        every { configuration.dataSource(any(), any(), any()) } returns configuration
        every { configuration.locations(*anyVararg<String>()) } returns configuration
        every { configuration.baselineOnMigrate(any()) } returns configuration
        every { configuration.baselineVersion(any<String>()) } returns configuration
        every { configuration.validateOnMigrate(any()) } returns configuration
        every { configuration.connectRetries(any()) } returns configuration
        every { configuration.connectRetriesInterval(any()) } returns configuration
        every { configuration.validateMigrationNaming(any()) } returns configuration
        every { configuration.failOnMissingLocations(any()) } returns configuration
        every { configuration.target(any<String>()) } returns configuration
        every { configuration.table(any()) } returns configuration
        every { configuration.createSchemas(any()) } returns configuration
        every { configuration.outOfOrder(any()) } returns configuration
        every { configuration.defaultSchema(any()) } returns configuration
        every { configuration.load() } returns flyway
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Flyway::class)
    }

    @Test
    fun `creates Flyway and initializer beans by default`() {
        contextRunner.run { context ->
            context.getBean("flyway") shouldBe flyway
            context.getBean("flywayInitializer").shouldBeInstanceOf<FlywayNcMigrationInitializer>()
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
        val userFlyway = mockk<Flyway>()
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
                        override val url: String = "test://compose-host:1234/compose_schema"
                        override val user: String = "compose-user"
                        override val password: String = "compose-password"
                    }
                },
            ).run {
                verify {
                    configuration.dataSource(
                        "test://compose-host:1234/compose_schema",
                        "compose-user",
                        "compose-password",
                    )
                }
            }
    }

    @Test
    fun `default-schema is URL-encoded and appended when the URL has no path`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlywayNcAutoConfiguration::class.java))
            .withUserConfiguration(NoOpMigrationStrategyConfig::class.java)
            .withPropertyValues(
                "spring.flyway-nc.url=test://localhost:1234",
                "spring.flyway-nc.default-schema=my schema",
            ).run {
                verify {
                    configuration.dataSource("test://localhost:1234/my%20schema", null, null)
                    configuration.defaultSchema("my schema")
                }
            }
    }

    @Test
    fun `URL-supplied schema wins over default-schema`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlywayNcAutoConfiguration::class.java))
            .withUserConfiguration(NoOpMigrationStrategyConfig::class.java)
            .withPropertyValues(
                "spring.flyway-nc.url=test://host1:1234,host2:1234/explicit_schema?cluster=primary",
                "spring.flyway-nc.default-schema=ignored_schema",
            ).run {
                verify {
                    configuration.dataSource(
                        "test://host1:1234,host2:1234/explicit_schema?cluster=primary",
                        null,
                        null,
                    )
                }
                verify(exactly = 0) { configuration.defaultSchema(any()) }
            }
    }

    @Test
    fun `default-schema is appended to a multi-host URL without a path`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlywayNcAutoConfiguration::class.java))
            .withUserConfiguration(NoOpMigrationStrategyConfig::class.java)
            .withPropertyValues(
                "spring.flyway-nc.url=test://host1:1234,host2:1234?cluster=primary",
                "spring.flyway-nc.default-schema=my_schema",
            ).run {
                verify {
                    configuration.dataSource(
                        "test://host1:1234,host2:1234/my_schema?cluster=primary",
                        null,
                        null,
                    )
                    configuration.defaultSchema("my_schema")
                }
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
                    generateSequence(failure) { it.cause }
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
            ).run {
                callCount.get() shouldBe 1
            }
    }

    @Test
    fun `applies migration and connection properties`() {
        contextRunner
            .withPropertyValues(
                "spring.flyway-nc.baseline-on-migrate=true",
                "spring.flyway-nc.baseline-version=2",
                "spring.flyway-nc.validate-on-migrate=false",
                "spring.flyway-nc.connect-retries=3",
                "spring.flyway-nc.connect-retries-interval=15",
                "spring.flyway-nc.validate-migration-naming=true",
                "spring.flyway-nc.fail-on-missing-locations=true",
                "spring.flyway-nc.target=4",
                "spring.flyway-nc.table=custom_history",
                "spring.flyway-nc.create-schemas=false",
                "spring.flyway-nc.out-of-order=true",
            ).run {
                verify {
                    configuration.baselineOnMigrate(true)
                    configuration.baselineVersion("2")
                    configuration.validateOnMigrate(false)
                    configuration.connectRetries(3)
                    configuration.connectRetriesInterval(15)
                    configuration.validateMigrationNaming(true)
                    configuration.failOnMissingLocations(true)
                    configuration.target("4")
                    configuration.table("custom_history")
                    configuration.createSchemas(false)
                    configuration.outOfOrder(true)
                }
            }
    }

    @Configuration
    class NoOpMigrationStrategyConfig {
        @Bean
        fun noOpMigrationStrategy(): FlywayNcMigrationStrategy =
            FlywayNcMigrationStrategy { /* skip migrate() to avoid hitting a real DB */ }
    }
}
