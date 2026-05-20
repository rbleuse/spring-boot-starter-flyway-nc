package io.github.rbleuse.flywaync.mongodb.testcontainers

import io.github.rbleuse.flywaync.FlywayNcConnectionDetails
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionDetailsFactory
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionSource
import org.testcontainers.mongodb.MongoDBContainer

internal class MongoDBContainerFlywayNcConnectionDetailsFactory :
    ContainerConnectionDetailsFactory<MongoDBContainer, FlywayNcConnectionDetails>() {

    override fun getContainerConnectionDetails(
        source: ContainerConnectionSource<MongoDBContainer>,
    ): FlywayNcConnectionDetails =
        MongoDBContainerFlywayNcConnectionDetails(source)

    private class MongoDBContainerFlywayNcConnectionDetails(
        source: ContainerConnectionSource<MongoDBContainer>,
    ) : ContainerConnectionDetails<MongoDBContainer>(source), FlywayNcConnectionDetails {
        override val url: String
            get() = container.connectionString

        override val user: String? = null

        override val password: String? = null
    }
}
