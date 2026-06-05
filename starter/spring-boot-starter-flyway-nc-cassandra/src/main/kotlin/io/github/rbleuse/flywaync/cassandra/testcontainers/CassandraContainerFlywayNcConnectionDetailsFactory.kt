package io.github.rbleuse.flywaync.cassandra.testcontainers

import io.github.rbleuse.flywaync.FlywayNcConnectionDetails
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionDetailsFactory
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionSource
import org.testcontainers.cassandra.CassandraContainer

internal class CassandraContainerFlywayNcConnectionDetailsFactory :
    ContainerConnectionDetailsFactory<CassandraContainer, FlywayNcConnectionDetails>() {

    override fun getContainerConnectionDetails(
        source: ContainerConnectionSource<CassandraContainer>,
    ): FlywayNcConnectionDetails =
        CassandraContainerFlywayNcConnectionDetails(source)

    private class CassandraContainerFlywayNcConnectionDetails(
        source: ContainerConnectionSource<CassandraContainer>,
    ) : ContainerConnectionDetails<CassandraContainer>(source), FlywayNcConnectionDetails {
        override val url: String
            get() {
                val contactPoint = container.contactPoint
                return "cassandra://${contactPoint.hostString}:${contactPoint.port}?localdatacenter=${container.localDatacenter}"
            }

        override val user: String
            get() = container.username

        override val password: String
            get() = container.password
    }
}
