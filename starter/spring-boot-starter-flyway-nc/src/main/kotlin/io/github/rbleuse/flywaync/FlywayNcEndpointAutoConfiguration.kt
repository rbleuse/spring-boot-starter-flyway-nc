package io.github.rbleuse.flywaync

import org.flywaydb.core.Flyway
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@AutoConfiguration(after = [FlywayNcAutoConfiguration::class])
@ConditionalOnClass(Flyway::class, ConditionalOnAvailableEndpoint::class)
class FlywayNcEndpointAutoConfiguration {
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnAvailableEndpoint(FlywayNcEndpoint::class)
    class EndpointConfiguration {
        @Bean
        @ConditionalOnBean(Flyway::class)
        @ConditionalOnMissingBean
        fun flywayNcEndpoint(context: ApplicationContext): FlywayNcEndpoint = FlywayNcEndpoint(context)
    }
}
