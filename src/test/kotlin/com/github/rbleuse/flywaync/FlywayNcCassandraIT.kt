package com.github.rbleuse.flywaync

import com.datastax.oss.driver.api.core.CqlSession
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.testcontainers.cassandra.CassandraContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.InetSocketAddress

@Testcontainers
@SpringBootConfiguration
@EnableAutoConfiguration
@SpringBootTest
@ContextConfiguration(initializers = [FlywayNcCassandraIT.PropertyInitializer::class])
class FlywayNcCassandraIT {

    companion object {
        private const val KEYSPACE = "flyway_nc_it"

        @Container
        @JvmStatic
        val cassandra: CassandraContainer = CassandraContainer(
            DockerImageName.parse("cassandra:5.0")
        ).withInitScript("cassandra-init.cql")
    }

    class PropertyInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(applicationContext: ConfigurableApplicationContext) {
            val host = cassandra.contactPoint.hostName
            val port = cassandra.contactPoint.port
            val dc = cassandra.localDatacenter
            TestPropertyValues.of(
                "spring.flyway-nc.url=cassandra://$host:$port/$KEYSPACE?localdatacenter=$dc",
                "spring.flyway-nc.migration-suffixes[0]=.cql",
            ).applyTo(applicationContext.environment)
        }
    }

    @Test
    fun `migration runs against a real Cassandra and creates the table plus history row`() {
        val host = cassandra.contactPoint.hostName
        val port = cassandra.contactPoint.port
        val dc = cassandra.localDatacenter

        CqlSession.builder()
            .addContactPoint(InetSocketAddress(host, port))
            .withLocalDatacenter(dc)
            .withKeyspace(KEYSPACE)
            .build()
            .use { session ->
                val tables = session.execute(
                    "SELECT table_name FROM system_schema.tables WHERE keyspace_name = ?",
                    KEYSPACE,
                ).all().map { it.getString("table_name") }

                tables shouldContain "flyway_nc_it_table"
                tables shouldContain "flyway_schema_history"

                val historyVersions = session.execute(
                    "SELECT version FROM flyway_schema_history",
                ).all().map { it.getString("version") }
                historyVersions shouldBe listOf("1")
            }
    }
}
