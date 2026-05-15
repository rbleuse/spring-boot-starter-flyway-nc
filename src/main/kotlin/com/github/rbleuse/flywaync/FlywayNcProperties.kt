package com.github.rbleuse.flywaync

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("spring.flyway-nc")
data class FlywayNcProperties(
    val enabled: Boolean = true,
    val url: String,
    val user: String? = null,
    val password: String? = null,
    val locations: List<String> = listOf("classpath:db/migration"),
    val migrationSuffixes: List<String>? = null,
    val defaultSchema: String? = null,
)
