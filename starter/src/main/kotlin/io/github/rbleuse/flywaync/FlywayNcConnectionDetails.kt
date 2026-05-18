package io.github.rbleuse.flywaync

import org.springframework.boot.autoconfigure.service.connection.ConnectionDetails

data class FlywayNcConnectionDetails(
    val url: String,
    val user: String? = null,
    val password: String? = null,
) : ConnectionDetails
