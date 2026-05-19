package io.github.rbleuse.flywaync

data class FlywayNcConnectionDetailsProperties(
    override val url: String,
    override val user: String? = null,
    override val password: String? = null,
) : FlywayNcConnectionDetails
