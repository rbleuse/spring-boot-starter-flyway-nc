package io.github.rbleuse.flywaync

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.CoreMigrationType
import org.flywaydb.core.api.MigrationInfo
import org.flywaydb.core.api.MigrationState
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.Date

class FlywayNcActuatorEndpointTest {
    @Test
    fun `JSON matches Boot Flyway for parent contexts and nullable migration fields`() {
        GenericApplicationContext().use { parent ->
            parent.id = "parent"
            parent.beanFactory.registerSingleton("parentFlyway", flyway())
            parent.refresh()
            GenericApplicationContext(parent).use { child ->
                child.id = "child"
                child.beanFactory.registerSingleton(
                    "childFlyway",
                    flyway(migration("1", MigrationState.SUCCESS), migration(null, MigrationState.PENDING)),
                )
                child.refresh()

                val mapper = JsonMapper.builder().build()
                val actual = mapper.readTree(mapper.writeValueAsString(FlywayNcActuatorEndpoint(child).flywayBeans()))
                val expected =
                    mapper.readTree(
                        """
                        {
                          "contexts": {
                            "parent": { "flywayBeans": { "parentFlyway": { "migrations": [] } }, "parentId": null },
                            "child": {
                              "parentId": "parent",
                              "flywayBeans": {
                                "childFlyway": {
                                  "migrations": [
                                    {
                                      "type": "SQL", "checksum": 123, "version": "1",
                                      "description": "example", "script": "V1__example.cql", "state": "SUCCESS",
                                      "installedBy": "cassandra", "installedOn": "2026-10-23T00:00:00Z",
                                      "installedRank": 1, "executionTime": 12
                                    },
                                    {
                                      "type": "SQL", "checksum": null, "version": null,
                                      "description": "example", "script": "R__example.cql", "state": "PENDING",
                                      "installedBy": null, "installedOn": null, "installedRank": null, "executionTime": null
                                    }
                                  ]
                                }
                              }
                            }
                          }
                        }
                        """.trimIndent(),
                    )
                actual shouldBe expected
            }
        }
    }

    private fun flyway(vararg migrations: MigrationInfo): Flyway =
        mockk<Flyway>(relaxed = true) {
            every { info().all() } returns arrayOf(*migrations)
        }

    private fun migration(
        version: String?,
        state: MigrationState,
    ): MigrationInfo =
        mockk {
            every { type } returns CoreMigrationType.SQL
            every { checksum } returns if (version != null) 123 else null
            every { getVersion() } returns version?.let(MigrationVersion::fromVersion)
            every { description } returns "example"
            every { script } returns if (version != null) "V1__example.cql" else "R__example.cql"
            every { getState() } returns state
            every { installedBy } returns if (state == MigrationState.SUCCESS) "cassandra" else null
            every { installedOn } returns
                if (state == MigrationState.SUCCESS) Date.from(Instant.parse("2026-10-23T00:00:00Z")) else null
            every { installedRank } returns if (state == MigrationState.SUCCESS) 1 else null
            every { executionTime } returns if (state == MigrationState.SUCCESS) 12 else null
        }
}
