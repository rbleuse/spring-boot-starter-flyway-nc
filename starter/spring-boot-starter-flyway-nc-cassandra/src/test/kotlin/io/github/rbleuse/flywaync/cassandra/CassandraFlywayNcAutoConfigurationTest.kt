package io.github.rbleuse.flywaync.cassandra

import io.github.rbleuse.flywaync.FlywayNcAutoConfiguration
import io.github.rbleuse.flywaync.FlywayNcActuatorEndpointAutoConfiguration
import io.github.rbleuse.flywaync.FlywayNcMigrationInitializer
import io.github.rbleuse.flywaync.FlywayNcMigrationStrategy
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBean
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration
import org.springframework.boot.actuate.endpoint.InvocationContext
import org.springframework.boot.actuate.endpoint.SecurityContext
import org.springframework.boot.actuate.endpoint.invoke.OperationInvokerAdvisor
import org.springframework.boot.actuate.endpoint.invoke.convert.ConversionServiceParameterValueMapper
import org.springframework.boot.actuate.endpoint.web.EndpointMediaTypes
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpointDiscoverer
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

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

    private val strategyContextRunner =
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    CassandraFlywayNcAutoConfiguration::class.java,
                    FlywayNcAutoConfiguration::class.java,
                ),
            ).withUserConfiguration(NoOpInitializerConfig::class.java)
            .withPropertyValues("spring.flyway-nc.url=cassandra://localhost:9042/test?localdatacenter=dc1")

    @Test
    fun `actuator caches Cassandra migration information for one minute by default`() {
        val flyway = mockk<Flyway>(relaxed = true)
        every { flyway.info().all() } returns emptyArray()
        contextRunner
            .withConfiguration(
                AutoConfigurations.of(FlywayNcActuatorEndpointAutoConfiguration::class.java, EndpointAutoConfiguration::class.java),
            ).withBean(Flyway::class.java, { flyway })
            .withPropertyValues("management.endpoints.web.exposure.include=flywaync")
            .run { context ->
                context.environment.getProperty("management.endpoint.flywaync.cache.time-to-live") shouldBe "1m"
                context.environment.getProperty("management.endpoint.flyway.cache.time-to-live") shouldBe null
                readEndpointTwice(context)
                verify(exactly = 1) { flyway.info() }
            }
    }

    @Test
    fun `application can disable the default Cassandra actuator cache`() {
        val flyway = mockk<Flyway>(relaxed = true)
        every { flyway.info().all() } returns emptyArray()
        contextRunner
            .withConfiguration(
                AutoConfigurations.of(FlywayNcActuatorEndpointAutoConfiguration::class.java, EndpointAutoConfiguration::class.java),
            ).withBean(Flyway::class.java, { flyway })
            .withPropertyValues(
                "management.endpoints.web.exposure.include=flywaync",
                "management.endpoint.flywaync.cache.time-to-live=0ms",
            ).run { context ->
                context.environment.getProperty("management.endpoint.flywaync.cache.time-to-live") shouldBe "0ms"
                readEndpointTwice(context)
                verify(exactly = 2) { flyway.info() }
            }
    }

    @Test
    fun `application can replace the default Cassandra actuator cache duration`() {
        contextRunner
            .withPropertyValues("management.endpoint.flywaync.cache.time-to-live=10s")
            .run { context ->
                context.environment.getProperty("management.endpoint.flywaync.cache.time-to-live") shouldBe "10s"
            }
    }

    private fun readEndpointTwice(context: ApplicationContext) {
        val operation =
            WebEndpointDiscoverer(
                context,
                ConversionServiceParameterValueMapper(),
                EndpointMediaTypes.DEFAULT,
                emptyList(),
                emptyList(),
                context.getBeansOfType(OperationInvokerAdvisor::class.java).values,
                emptyList(),
                emptyList(),
            ).endpoints.single { it.endpointId.toString() == "flywaync" }.operations.single()
        repeat(2) { operation.invoke(InvocationContext(SecurityContext.NONE, emptyMap())) }
    }

    @Test
    fun `defaults sqlMigrationSuffixes to cql when none configured`() {
        contextRunner.run { context ->
            val configuration = context.getBean<Flyway>().configuration
            configuration.sqlMigrationSuffixes.toList() shouldBe listOf(".cql")
            configuration.isExecuteInTransaction shouldBe false
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

    @Test
    fun `distributed lock can be disabled`() {
        strategyContextRunner
            .withPropertyValues("spring.flyway-nc.lock.enabled=false")
            .run { context ->
                context.containsBean("cassandraFlywayNcMigrationStrategy") shouldBe true
                context.getBean<CassandraFlywayNcLockProperties>().enabled shouldBe false
                context.getBean<Flyway>().configuration.isBaselineOnMigrate shouldBe false
            }
    }

    @Test
    fun `creates Cassandra migration strategy by default`() {
        strategyContextRunner
            .withPropertyValues(
                "spring.flyway-nc.lock.lease-duration=30s",
                "spring.flyway-nc.lock.acquire-timeout=45s",
            ).run { context ->
                context.containsBean("cassandraFlywayNcMigrationStrategy") shouldBe true
                context.getBean<CassandraFlywayNcLockProperties>().leaseDuration shouldBe Duration.ofSeconds(30)
                context.getBean<CassandraFlywayNcLockProperties>().acquireTimeout shouldBe Duration.ofSeconds(45)
                context.getBean<Flyway>().configuration.isBaselineOnMigrate shouldBe false
                context
                    .getBean<Flyway>()
                    .configuration.baselineVersion
                    .toString() shouldBe "1"
            }
    }

    @Test
    fun `locking preserves explicit baseline settings`() {
        strategyContextRunner
            .withPropertyValues(
                "spring.flyway-nc.baseline-on-migrate=true",
                "spring.flyway-nc.baseline-version=7",
            ).run { context ->
                val configuration = context.getBean<Flyway>().configuration
                configuration.isBaselineOnMigrate shouldBe true
                configuration.baselineVersion.toString() shouldBe "7"
            }
    }

    @Test
    fun `user-defined migration strategy overrides Cassandra lock strategy`() {
        contextRunner
            .run { context ->
                context.getBeansOfType(FlywayNcMigrationStrategy::class.java).keys shouldBe
                    setOf("noOpMigrationStrategy")
                context.getBean<Flyway>().configuration.isBaselineOnMigrate shouldBe false
            }
    }

    @Configuration
    class NoOpMigrationStrategyConfig {
        @Bean
        fun noOpMigrationStrategy(): FlywayNcMigrationStrategy =
            FlywayNcMigrationStrategy { /* skip migrate() to avoid hitting a real DB */ }
    }

    @Configuration
    class NoOpInitializerConfig {
        @Bean
        fun noOpFlywayInitializer(flyway: Flyway): FlywayNcMigrationInitializer =
            FlywayNcMigrationInitializer(flyway) { /* skip database access */ }
    }
}
