package io.github.rbleuse.flywaync

import org.springframework.boot.context.properties.ConfigurationProperties
import kotlin.jvm.JvmRecord

@ConfigurationProperties("spring.flyway-nc")
@JvmRecord
data class FlywayNcProperties(
    val url: String? = null,
    val user: String? = null,
    val password: String? = null,
    val locations: List<String> = listOf("classpath:db/migration"),
    val migrationSuffixes: List<String> = emptyList(),
    val defaultSchema: String? = null,
)
