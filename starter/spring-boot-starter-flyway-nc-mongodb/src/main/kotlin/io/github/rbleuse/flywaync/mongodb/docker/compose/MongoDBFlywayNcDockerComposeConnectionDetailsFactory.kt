package io.github.rbleuse.flywaync.mongodb.docker.compose

import io.github.rbleuse.flywaync.FlywayNcConnectionDetails
import org.springframework.boot.docker.compose.core.RunningService
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionDetailsFactory
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionSource
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal class MongoDBFlywayNcDockerComposeConnectionDetailsFactory :
    DockerComposeConnectionDetailsFactory<FlywayNcConnectionDetails>("mongo") {
    private companion object {
        const val MONGODB_PORT = 27017
    }

    override fun getDockerComposeConnectionDetails(source: DockerComposeConnectionSource): FlywayNcConnectionDetails {
        val service = source.runningService
        val environment = MongoDBEnvironment(service.env())
        return object : FlywayNcConnectionDetails {
            override val url: String = service.toMongoDBUrl(environment)
            override val user: String? = environment.user
            override val password: String? = environment.password
        }
    }

    private fun RunningService.toMongoDBUrl(environment: MongoDBEnvironment): String =
        buildString {
            append("mongodb://${host()}:${ports().get(MONGODB_PORT)}")
            environment.database?.let { append("/").append(it.urlEncode()) }
            if (environment.user != null) append("?authSource=admin")
        }

    private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)

    private class MongoDBEnvironment(
        env: Map<String, String?>,
    ) {
        val database: String? = env["MONGO_INITDB_DATABASE"]
        val user: String? = env["MONGO_INITDB_ROOT_USERNAME"]
        val password: String? = env["MONGO_INITDB_ROOT_PASSWORD"]
    }
}
