package io.github.rbleuse.flywaync

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.service.connection.ConnectionDetailsFactories
import org.springframework.boot.docker.compose.core.ConnectionPorts
import org.springframework.boot.docker.compose.core.ImageReference
import org.springframework.boot.docker.compose.core.RunningService
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionSource
import org.springframework.mock.env.MockEnvironment

class CassandraFlywayNcDockerComposeConnectionDetailsFactoryTest {

    @Test
    fun `creates Flyway NC connection details for cassandra compose services`() {
        val source = dockerComposeConnectionSource(
            object : RunningService {
                override fun name(): String = "cassandra"
                override fun image(): ImageReference = ImageReference.of("cassandra:5.0")
                override fun host(): String = "127.0.0.1"
                override fun ports(): ConnectionPorts = singlePort(9042, 19042)
                override fun env(): Map<String, String> =
                    mapOf(
                        "CASSANDRA_DC" to "dc-test",
                        "CASSANDRA_KEYSPACE" to "flyway_keyspace",
                        "CASSANDRA_USER" to "flyway_user",
                        "CASSANDRA_PASSWORD" to "flyway_password",
                    )
                override fun labels(): Map<String, String> = emptyMap()
            },
        )

        val details = ConnectionDetailsFactories(javaClass.classLoader)
            .getConnectionDetails(source, false)[FlywayNcConnectionDetails::class.java]
                as FlywayNcConnectionDetails

        details.url shouldBe "cassandra://127.0.0.1:19042/flyway_keyspace?localdatacenter=dc-test"
        details.user shouldBe "flyway_user"
        details.password shouldBe "flyway_password"
    }

    private fun dockerComposeConnectionSource(service: RunningService): DockerComposeConnectionSource {
        val constructor = DockerComposeConnectionSource::class.java
            .getDeclaredConstructor(RunningService::class.java, org.springframework.core.env.Environment::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(service, MockEnvironment())
    }

    private fun singlePort(expectedContainerPort: Int, hostPort: Int): ConnectionPorts =
        object : ConnectionPorts {
            override fun get(containerPort: Int): Int =
                if (containerPort == expectedContainerPort) hostPort else error("No port mapped")

            override fun getAll(): List<Int> = listOf(hostPort)

            override fun getAll(protocol: String?): List<Int> = listOf(hostPort)
        }
}
