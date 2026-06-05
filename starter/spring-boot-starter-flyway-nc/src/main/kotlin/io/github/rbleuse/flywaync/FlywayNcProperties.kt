package io.github.rbleuse.flywaync

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("spring.flyway-nc")
data class FlywayNcProperties(
    val url: String? = null,
    val user: String? = null,
    val password: String? = null,
    val locations: List<String> = listOf("classpath:db/migration"),
    val migrationSuffixes: List<String> = emptyList(),
    val defaultSchema: String? = null,
)
