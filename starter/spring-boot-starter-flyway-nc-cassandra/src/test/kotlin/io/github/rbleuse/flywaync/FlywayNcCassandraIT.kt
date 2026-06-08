package io.github.rbleuse.flywaync

import com.datastax.oss.driver.api.core.CqlSession
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.cassandra.CassandraContainer
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.InetSocketAddress

@SpringBootTest(
    properties = [
        // No spring.flyway-nc.migration-suffixes here on purpose: the Cassandra starter defaults
        // it to .cql via CassandraFlywayNcAutoConfiguration, so this IT also guards that default.
        "spring.flyway-nc.default-schema=flyway_nc_it",
    ],
)
@SpringBootConfiguration
@EnableAutoConfiguration
@Testcontainers(disabledWithoutDocker = true)
class FlywayNcCassandraIT {
    companion object {
        private const val KEYSPACE = "flyway_nc_it"

        @ServiceConnection
        val cassandra: CassandraContainer =
            CassandraContainer(
                DockerImageName.parse("cassandra:5.0"),
            ).withInitScript("cassandra-init.cql")
    }

    @Test
    fun `migration runs when keyspace is embedded in the URL and default-schema is unset`() {
        val host = cassandra.contactPoint.hostName
        val port = cassandra.contactPoint.port
        val dc = cassandra.localDatacenter

        CqlSession
            .builder()
            .addContactPoint(InetSocketAddress(host, port))
            .withLocalDatacenter(dc)
            .withKeyspace(KEYSPACE)
            .build()
            .use { session ->
                val tables =
                    session
                        .execute(
                            "SELECT table_name FROM system_schema.tables WHERE keyspace_name = ?",
                            KEYSPACE,
                        ).all()
                        .map { it.getString("table_name") }

                tables shouldContain "flyway_nc_it_table"
                tables shouldContain "flyway_schema_history"

                val historyVersions =
                    session
                        .execute(
                            "SELECT version FROM flyway_schema_history",
                        ).all()
                        .map { it.getString("version") }
                historyVersions shouldBe listOf("1")
            }
    }
}
