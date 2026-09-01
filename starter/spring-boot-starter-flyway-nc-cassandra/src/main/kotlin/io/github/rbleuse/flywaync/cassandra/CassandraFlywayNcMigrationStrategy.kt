package io.github.rbleuse.flywaync.cassandra

import com.datastax.oss.driver.api.core.CqlSession
import io.github.rbleuse.flywaync.FlywayNcMigrationStrategy
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.flywaydb.database.nc.cassandra.CassandraDatabase
import org.flywaydb.nc.utils.VerbUtils
import java.net.InetSocketAddress
import java.net.URI

internal class CassandraFlywayNcMigrationStrategy(
    private val lockProperties: CassandraFlywayNcLockProperties,
) : FlywayNcMigrationStrategy {
    override fun migrate(flyway: Flyway) {
        if (!lockProperties.enabled) {
            flyway.withClosedSession { flyway.migrate() }
            return
        }
        val configuration = flyway.configuration
        val uri = URI.create(configuration.url)
        val keyspace = uri.keyspace()

        openSession(uri, keyspace, configuration.user, configuration.password).use { session ->
            CassandraMigrationLock(
                session = session,
                lockName = "$keyspace.${configuration.table}",
                properties = lockProperties,
            ).withLock {
                flyway
                    .withClosedSession {
                        initializeEmptySchemaHistory(flyway, session, keyspace)
                        flyway.migrate()
                    }.also { result ->
                        if (result.migrationsExecuted > 0 && !session.checkSchemaAgreement()) {
                            throw FlywayException("Cassandra schema agreement was not reached after migrations")
                        }
                    }
            }
        }
    }

    private fun initializeEmptySchemaHistory(
        flyway: Flyway,
        session: CqlSession,
        keyspace: String,
    ) {
        val configuration = flyway.configuration
        if (configuration.defaultSchema != null && configuration.defaultSchema != keyspace) return
        val schemasEmpty =
            (configuration.schemas.toList() + keyspace).all { schema ->
                session
                    .execute("SELECT table_name FROM system_schema.tables WHERE keyspace_name = ?", schema)
                    .all { schema == keyspace && it.getString("table_name") == CassandraMigrationLock.TABLE }
            }
        if (!schemasEmpty) return

        // The lock table must not turn an otherwise empty schema into a baseline candidate.
        flyway.withClosedSession {
            val database = configuration.pluginRegister.getExact(CassandraDatabase::class.java)
            database.initialize(VerbUtils.getResolvedEnvironment(configuration, null), configuration)
            database.createSchemaHistoryTableIfNotExists(configuration)
        }
    }

    // Flyway NC 12.11 leaves its session open and overwrites it on the next operation.
    // Close each top-level operation, including failures, before another can initialize it.
    private inline fun <T> Flyway.withClosedSession(operation: () -> T): T =
        configuration.pluginRegister.getExact(CassandraDatabase::class.java).use {
            operation()
        }

    private fun openSession(
        uri: URI,
        keyspace: String,
        user: String?,
        password: String?,
    ): CqlSession =
        CqlSession
            .builder()
            .addContactPoint(InetSocketAddress(uri.host, if (uri.port == -1) 9042 else uri.port))
            .withKeyspace(keyspace)
            .apply {
                uri.queryParameter("localdatacenter")?.let(::withLocalDatacenter)
                if (user != null && password != null) {
                    withAuthCredentials(user, password)
                }
            }.build()

    private fun URI.keyspace(): String =
        path?.removePrefix("/")?.takeUnless(String::isBlank)
            ?: throw FlywayException("A Cassandra keyspace is required when migration locking is enabled")

    private fun URI.queryParameter(name: String): String? =
        query
            ?.split('&')
            ?.firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
}
