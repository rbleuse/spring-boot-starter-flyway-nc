package io.github.rbleuse.flywaync

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationInfo
import org.flywaydb.core.internal.nc.NativeConnectorsDatabase
import org.springframework.boot.actuate.endpoint.OperationResponseBody
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.context.ApplicationContext
import java.time.Instant

/** Exposes the standard Flyway Actuator response without Spring Boot's JDBC Flyway module. */
@Endpoint(id = "flywaync")
class FlywayNcEndpoint(
    private val context: ApplicationContext,
) {
    @Synchronized
    @ReadOperation
    fun flywayBeans(): FlywayBeansDescriptor =
        FlywayBeansDescriptor(
            generateSequence(context) { it.parent }.associate { target ->
                target.id to
                    ContextFlywayBeansDescriptor(
                        target.getBeansOfType(Flyway::class.java).mapValues { (_, flyway) ->
                            // Flyway NC 12.11 leaves info() connections open, including on failure.
                            AutoCloseable {
                                flyway.configuration.pluginRegister
                                    .getInstancesOf(NativeConnectorsDatabase::class.java)
                                    .forEach { if (!it.isClosed) it.close() }
                            }.use {
                                FlywayDescriptor(flyway.info().all().map(::FlywayMigrationDescriptor))
                            }
                        },
                        target.parent?.id,
                    )
            },
        )

    class FlywayBeansDescriptor(
        val contexts: Map<String?, ContextFlywayBeansDescriptor>,
    ) : OperationResponseBody

    class ContextFlywayBeansDescriptor(
        val flywayBeans: Map<String, FlywayDescriptor>,
        val parentId: String?,
    )

    class FlywayDescriptor(
        val migrations: List<FlywayMigrationDescriptor>,
    )

    class FlywayMigrationDescriptor(
        info: MigrationInfo,
    ) {
        val type: String = info.type.name()
        val checksum: Int? = info.checksum
        val version: String? = info.version?.toString()
        val description: String? = info.description
        val script: String? = info.script
        val state = info.state
        val installedBy: String? = info.installedBy
        val installedOn: Instant? = info.installedOn?.let { Instant.ofEpochMilli(it.time) }
        val installedRank: Int? = info.installedRank
        val executionTime: Int? = info.executionTime
    }
}
