package io.github.rbleuse.flywaync

import com.mongodb.client.MongoClients
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.mongodb.MongoDBContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest(
    properties = [
        "spring.flyway-nc.default-schema=flyway_nc_it",
        "spring.flyway-nc.migration-suffixes[0]=.json",
    ],
)
@SpringBootConfiguration
@EnableAutoConfiguration
class FlywayNcMongoDBIT {
    companion object {
        private const val DATABASE = "flyway_nc_it"

        @ServiceConnection
        val mongo: MongoDBContainer = MongoDBContainer(
            DockerImageName.parse("mongo:8.0.21")
        )
    }

    @Test
    fun `migration runs and schema history is populated`() {
        MongoClients.create(mongo.connectionString).use { client ->
            val database = client.getDatabase(DATABASE)
            val collections = database.listCollectionNames().toList()

            collections shouldContain "flyway_nc_it_collection"
            collections shouldContain "flyway_schema_history"

            val historyVersions = database.getCollection("flyway_schema_history")
                .find()
                .toList()
                .mapNotNull { it.getString("version") }
            historyVersions shouldBe listOf("1")
        }
    }
}
