# spring-boot-starter-flyway-nc-cassandra

Cassandra support for [`spring-boot-starter-flyway-nc`](../../README.md) — Flyway's Native Connectors path applied to Apache Cassandra via the [`flyway-database-nc-cassandra`](https://documentation.red-gate.com/flyway/flyway-concepts/database-objects/database-native-connectors) module.

This starter:

- `api`-depends on the generic starter, so adding it is enough to get autoconfig + Cassandra wiring.
- Brings `org.flywaydb:flyway-database-nc-cassandra` as `runtimeOnly`.
- Contributes Spring Boot service-connection factories for Docker Compose and Testcontainers so `spring.flyway-nc.*` properties can be omitted in most setups.

## Installation

```kotlin
dependencies {
    implementation("io.github.rbleuse:spring-boot-starter-flyway-nc-cassandra")
}
```

See the [root README](../../README.md#installation) for the recommended setup with the Spring Boot Gradle plugin and the `spring-boot-flyway-nc-dependencies` BOM, plus the mandatory `FLYWAY_NATIVE_CONNECTORS=true` environment variable.

## Configuration

All `spring.flyway-nc.*` properties from the generic starter apply — see the [root README](../../README.md#configuration) for the full list. A minimal Cassandra configuration looks like:

```yaml
spring:
  flyway-nc:
    url: cassandra://localhost:9042?localdatacenter=datacenter1
    default-schema: my_keyspace
```

This starter defaults `spring.flyway-nc.migration-suffixes` to `[".cql"]` (Flyway's own default is `.sql`), so you don't need to set it. Provide the property explicitly to override.

### Cassandra URL format

The Cassandra Flyway NC URL looks like:

```
cassandra://<host>:<port>[/<keyspace>]?localdatacenter=<dc>[&...]
```

- The path segment, if present, is the keyspace. Per the [URL schema handling rules](../../README.md#url-schema-handling), a path in the URL wins over `spring.flyway-nc.default-schema`.
- `localdatacenter` is required by the Cassandra driver.

### Docker Compose service connections

When `org.springframework.boot:spring-boot-docker-compose` is on the classpath alongside this starter, Cassandra compose services are detected automatically:

```yaml
services:
  cassandra:
    image: cassandra:5.0
    ports:
      - "9042"
    environment:
      CASSANDRA_DC: datacenter1
      CASSANDRA_KEYSPACE: my_keyspace
      CASSANDRA_USER: cassandra
      CASSANDRA_PASSWORD: cassandra
```

With the compose service running, you can omit `spring.flyway-nc.url`, `user`, and `password`. The factory reads:

- The mapped host and port for container port `9042`.
- `CASSANDRA_DC` or `CASSANDRA_DATACENTER` (defaults to `datacenter1`).
- `CASSANDRA_KEYSPACE` (optional; embedded in the URL path when set).
- Credentials from `CASSANDRA_USER` or `CASSANDRA_USERNAME` plus `CASSANDRA_PASSWORD`.

### Testcontainers service connections

When `org.springframework.boot:spring-boot-testcontainers` is on the test classpath, a `CassandraContainer` annotated with `@ServiceConnection` supplies the connection details automatically:

```kotlin
@ServiceConnection
val cassandra = CassandraContainer(DockerImageName.parse("cassandra:5.0"))
    .withInitScript("cassandra-init.cql")
```

The factory derives the URL from the container's `contactPoint` and `localDatacenter`, and forwards the container's `username` / `password`. The factory does not embed a keyspace, so set `spring.flyway-nc.default-schema` (or use an init script that creates the keyspace) when migrations need one.

> **Note:** this starter targets the modern `org.testcontainers.cassandra.CassandraContainer` (from `org.testcontainers:testcontainers-cassandra`), not the legacy `org.testcontainers.containers.CassandraContainer` shipped under `org.testcontainers:cassandra`.

## Customizing migration startup

If you need to bootstrap a keyspace before Flyway runs (for example, because your application creates it dynamically rather than via Docker / Testcontainers init scripts), use a [`FlywayConfigurationCustomizer`](../../README.md#flywayconfigurationcustomizer) that takes the bootstrap bean as a constructor parameter — Spring will order the customizer behind it:

```kotlin
@Bean
fun keyspaceBootstrap(session: CqlSession) = FlywayConfigurationCustomizer { config ->
    session.execute(
        """
        CREATE KEYSPACE IF NOT EXISTS my_keyspace
        WITH replication = { 'class': 'SimpleStrategy', 'replication_factor': 1 }
        """.trimIndent()
    )
    config.defaultSchema("my_keyspace")
}
```

To skip migration entirely in tests, or to run `repair()` first, supply a [`FlywayNcMigrationStrategy`](../../README.md#flywayncmigrationstrategy) bean.

## Writing migrations

Place `.cql` files under `src/main/resources/db/migration/` (or wherever `spring.flyway-nc.locations` points). Flyway's standard `V<version>__<description>.cql` naming applies. This starter already defaults `spring.flyway-nc.migration-suffixes` to `[".cql"]` (Flyway's own default is `.sql`), so no extra configuration is needed unless you want a different suffix.

```cql
-- V1__create_users_table.cql
CREATE TABLE users (
    id uuid PRIMARY KEY,
    email text,
    created_at timestamp
);
```
