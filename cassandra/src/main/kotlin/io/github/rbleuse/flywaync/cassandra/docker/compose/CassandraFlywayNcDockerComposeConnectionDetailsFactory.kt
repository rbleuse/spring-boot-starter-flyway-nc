package io.github.rbleuse.flywaync.cassandra.docker.compose

import io.github.rbleuse.flywaync.FlywayNcConnectionDetails
import org.springframework.boot.docker.compose.core.RunningService
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionDetailsFactory
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionSource
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal class CassandraFlywayNcDockerComposeConnectionDetailsFactory :
    DockerComposeConnectionDetailsFactory<FlywayNcConnectionDetails>("cassandra") {
    private companion object {
        const val CASSANDRA_PORT = 9042
        const val CASSANDRA_DEFAULT_DATACENTER = "datacenter1"
    }

    override fun getDockerComposeConnectionDetails(source: DockerComposeConnectionSource): FlywayNcConnectionDetails {
        val service = source.runningService
        val environment = CassandraEnvironment(service.env())
        return object : FlywayNcConnectionDetails {
            override val url: String = service.toCassandraUrl(environment)
            override val user: String? = environment.user
            override val password: String? = environment.password
        }
    }

    private fun RunningService.toCassandraUrl(environment: CassandraEnvironment): String =
        buildString {
            append("cassandra://${host()}:${ports().get(CASSANDRA_PORT)}")
            environment.keyspace?.let { append("/").append(it.urlEncode()) }
            append("?localdatacenter=").append(environment.localDatacenter.urlEncode())
        }

    private fun String.urlEncode(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8)

    private class CassandraEnvironment(env: Map<String, String?>) {
        val localDatacenter: String = env["CASSANDRA_DC"] ?: env["CASSANDRA_DATACENTER"] ?: CASSANDRA_DEFAULT_DATACENTER
        val keyspace: String? = env["CASSANDRA_KEYSPACE"]
        val user: String? = env["CASSANDRA_USER"] ?: env["CASSANDRA_USERNAME"]
        val password: String? = env["CASSANDRA_PASSWORD"]
    }
}
