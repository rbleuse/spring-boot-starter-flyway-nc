package io.github.rbleuse.flywaync

import org.springframework.boot.autoconfigure.service.connection.ConnectionDetails

interface FlywayNcConnectionDetails : ConnectionDetails {
    val url: String
    val user: String?
        get() = null
    val password: String?
        get() = null
}
