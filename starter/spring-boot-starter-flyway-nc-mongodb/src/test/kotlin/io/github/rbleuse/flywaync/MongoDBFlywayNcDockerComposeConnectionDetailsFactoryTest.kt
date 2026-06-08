package io.github.rbleuse.flywaync

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.service.connection.ConnectionDetailsFactories
import org.springframework.boot.docker.compose.core.ConnectionPorts
import org.springframework.boot.docker.compose.core.ImageReference
import org.springframework.boot.docker.compose.core.RunningService
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionSource
import org.springframework.mock.env.MockEnvironment

class MongoDBFlywayNcDockerComposeConnectionDetailsFactoryTest {
    @Test
    fun `creates Flyway NC connection details for mongo compose services`() {
        val source =
            dockerComposeConnectionSource(
                object : RunningService {
                    override fun name(): String = "mongo"

                    override fun image(): ImageReference = ImageReference.of("mongo:8.0.21")

                    override fun host(): String = "127.0.0.1"

                    override fun ports(): ConnectionPorts = singlePort(27017, 37017)

                    override fun env(): Map<String, String> =
                        mapOf(
                            "MONGO_INITDB_DATABASE" to "flyway_db",
                            "MONGO_INITDB_ROOT_USERNAME" to "flyway_user",
                            "MONGO_INITDB_ROOT_PASSWORD" to "flyway_password",
                        )

                    override fun labels(): Map<String, String> = emptyMap()
                },
            )

        val details =
            ConnectionDetailsFactories(javaClass.classLoader)
                .getConnectionDetails(source, false)[FlywayNcConnectionDetails::class.java]
                as FlywayNcConnectionDetails

        details.url shouldBe "mongodb://127.0.0.1:37017/flyway_db?authSource=admin"
        details.user shouldBe "flyway_user"
        details.password shouldBe "flyway_password"
    }

    @Test
    fun `omits authSource when no credentials are provided`() {
        val source =
            dockerComposeConnectionSource(
                object : RunningService {
                    override fun name(): String = "mongo"

                    override fun image(): ImageReference = ImageReference.of("mongo:8.0.21")

                    override fun host(): String = "127.0.0.1"

                    override fun ports(): ConnectionPorts = singlePort(27017, 37017)

                    override fun env(): Map<String, String> = mapOf("MONGO_INITDB_DATABASE" to "flyway_db")

                    override fun labels(): Map<String, String> = emptyMap()
                },
            )

        val details =
            ConnectionDetailsFactories(javaClass.classLoader)
                .getConnectionDetails(source, false)[FlywayNcConnectionDetails::class.java]
                as FlywayNcConnectionDetails

        details.url shouldBe "mongodb://127.0.0.1:37017/flyway_db"
        details.user shouldBe null
        details.password shouldBe null
    }

    private fun dockerComposeConnectionSource(service: RunningService): DockerComposeConnectionSource {
        val constructor =
            DockerComposeConnectionSource::class.java
                .getDeclaredConstructor(RunningService::class.java, org.springframework.core.env.Environment::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(service, MockEnvironment())
    }

    private fun singlePort(
        expectedContainerPort: Int,
        hostPort: Int,
    ): ConnectionPorts =
        object : ConnectionPorts {
            override fun get(containerPort: Int): Int = if (containerPort == expectedContainerPort) hostPort else error("No port mapped")

            override fun getAll(): List<Int> = listOf(hostPort)

            override fun getAll(protocol: String?): List<Int> = listOf(hostPort)
        }
}
