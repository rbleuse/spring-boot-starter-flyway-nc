# spring-boot-starter-flyway-nc

A Spring Boot auto-configuration starter for Flyway's **Native Connectors** (NC) path — Flyway's non-JDBC engine introduced in Flyway 11/12 for databases like Cassandra.

Spring Boot's built-in `spring-boot-starter-flyway` is JDBC-only (it requires a `DataSource` bean), so this starter intentionally **does not depend on it** and reimplements the analogous initializer/strategy/customizer beans against Flyway's NC API.

## Requirements

- Spring Boot **4.1+** (tested against 4.1.0-RC1)
- Flyway **12.5+** with Native Connectors enabled (`FLYWAY_NATIVE_CONNECTORS=true`)
- JVM 17+

## Installation

This project publishes three artifacts:

- `io.github.rbleuse:spring-boot-starter-flyway-nc` — the generic starter
- `io.github.rbleuse:spring-boot-starter-flyway-nc-cassandra` — Cassandra support for the generic starter, including the Flyway Cassandra NC module and Docker Compose service connection details
- `io.github.rbleuse:spring-boot-starter-flyway-nc-dependencies` — a BOM pinning every Flyway NC module to a single, override-able version

### With the Spring Boot Gradle plugin (recommended)

```kotlin
plugins {
    id("org.springframework.boot") version "4.1.0-RC1" // applies io.spring.dependency-management
}

extra["flyway.version"] = "12.6.0" // optional override; defaults to the BOM's pinned version

dependencyManagement {
    imports {
        mavenBom("io.github.rbleuse:spring-boot-starter-flyway-nc-dependencies:<version>")
    }
}

dependencies {
    implementation("io.github.rbleuse:spring-boot-starter-flyway-nc")
    runtimeOnly("org.flywaydb:flyway-database-nc-<database>") // pick the DB module you need
}
```

For Cassandra, use the Cassandra-specific starter instead. It pulls in the generic starter and the Cassandra Flyway NC database module:

```kotlin
dependencies {
    implementation("io.github.rbleuse:spring-boot-starter-flyway-nc-cassandra")
}
```

How resolution works: the starter declares `flyway-verb-migrate` and `flyway-nc-scanners` as `runtimeOnly` dependencies; the DB module transitively brings `flyway-core` and `flyway-nc-core`. The BOM pins every Flyway NC module to `${flyway.version}`, which `io.spring.dependency-management` lets you override via `extra["flyway.version"]` — the same mechanism Spring Boot uses for its own managed dependencies.

### Enabling Flyway's Native Connectors path

Flyway requires the `FLYWAY_NATIVE_CONNECTORS` environment variable to use the NC engine. Set it before starting your application:

```
FLYWAY_NATIVE_CONNECTORS=true
```

Without it, Flyway silently falls back to JDBC and the starter will fail confusingly.

## Configuration

All properties are under the `spring.flyway-nc` prefix:

| Property | Default | Description |
|---|---|---|
| `enabled` | `true` | Set to `false` to disable autoconfig entirely. |
| `url` | — | The Flyway NC datasource URL (e.g. a Cassandra contact point URL). Required unless a `FlywayNcConnectionDetails` bean is provided, such as by Docker Compose service connections. |
| `user` | `null` | Optional username. |
| `password` | `null` | Optional password. |
| `locations` | `classpath:db/migration` | Migration locations, Flyway-style. |
| `migration-suffixes` | `null` | Overrides Flyway's SQL migration suffixes (e.g. `.cql`). |
| `default-schema` | `null` | Default schema/keyspace for migrations. |

Example `application.yml`:

```yaml
spring:
  flyway-nc:
    url: cassandra://localhost:9042/?localdatacenter=datacenter1
    default-schema: my_keyspace
    migration-suffixes: [".cql"]
    locations:
      - classpath:db/migration
```

### Docker Compose service connections

When both `io.github.rbleuse:spring-boot-starter-flyway-nc-cassandra` and `org.springframework.boot:spring-boot-docker-compose` are on the classpath, Cassandra compose services are detected the same way Spring Boot detects them for Spring Data Cassandra. The Cassandra starter contributes a `FlywayNcConnectionDetails` bean for `cassandra` images and uses it to configure Flyway's NC URL:

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

With that compose service, you can omit `spring.flyway-nc.url`, `spring.flyway-nc.user`, and `spring.flyway-nc.password`. The Docker Compose service connection supplies the mapped host and port, `CASSANDRA_DC` or `CASSANDRA_DATACENTER`, `CASSANDRA_KEYSPACE`, and credentials from `CASSANDRA_USER` or `CASSANDRA_USERNAME` plus `CASSANDRA_PASSWORD`.

### Testcontainers service connections

When `io.github.rbleuse:spring-boot-starter-flyway-nc-cassandra` and `org.springframework.boot:spring-boot-testcontainers` are on the test classpath, a `CassandraContainer` annotated with `@ServiceConnection` supplies `spring.flyway-nc` connection details automatically:

```kotlin
@Container
@ServiceConnection
val cassandra = CassandraContainer(DockerImageName.parse("cassandra:5.0"))
```

Configure `spring.flyway-nc.default-schema` when Flyway should migrate a specific keyspace; the Cassandra container supplies host, port, local datacenter, username, and password.

## How it works

`FlywayNcAutoConfiguration` is activated when `Flyway` is on the classpath and `spring.flyway-nc.enabled` is not `false`. It wires three beans, all gated with `@ConditionalOnMissingBean`:

1. **`FlywayNcConnectionDetails`** — built from `spring.flyway-nc.url`, `user`, and `password` when no service connection has provided one.
2. **`Flyway`** — built from `FlywayNcConnectionDetails` plus the remaining `FlywayNcProperties`. All discovered `FlywayConfigurationCustomizer` beans run against the `FluentConfiguration` builder before `.load()`, in `@Order` order.
3. **`FlywayNcMigrationInitializer`** — an `InitializingBean` that calls `flyway.migrate()` (or a user-supplied `FlywayNcMigrationStrategy`) in `afterPropertiesSet()`.

## Extension points

The two extension points mirror Spring Boot's JDBC Flyway API on purpose, so the experience is familiar.

### `FlywayNcMigrationStrategy`

Replaces what `flyway.migrate()` does at startup. Use it to skip migration in tests, run `repair()` first, etc.

```kotlin
@Bean
fun migrationStrategy(): FlywayNcMigrationStrategy = FlywayNcMigrationStrategy { flyway ->
    flyway.repair()
    flyway.migrate()
}
```

### `FlywayConfigurationCustomizer`

Runs against the Flyway *builder* before `.load()`. Prefer this over post-processing the `Flyway` bean when you need:

- settings not exposed via `spring.flyway-nc.*`, or
- an order dependency on a bootstrap bean — e.g. a `CqlSession` that must create the keyspace before migrations run — by taking that bean as a constructor parameter of the customizer.

```kotlin
@Bean
fun keyspaceBootstrap(session: CqlSession) = FlywayConfigurationCustomizer { config ->
    session.execute("CREATE KEYSPACE IF NOT EXISTS my_keyspace WITH replication = {...}")
    config.defaultSchema("my_keyspace")
}
```

## Building from source

Gradle (Kotlin DSL), use the wrapper:

```
./gradlew build               # compile + test + assemble
./gradlew test                # unit + integration tests (JUnit 5)
./gradlew test --tests "*IT"  # integration tests only (Testcontainers — requires Docker)
```

The `Test` task always sets `FLYWAY_NATIVE_CONNECTORS=true`. Don't run JUnit directly from an IDE without that env var, or Flyway will silently fall back to JDBC and tests will fail confusingly.

To build against a different Flyway version, edit the `flyway` entry in `gradle/libs.versions.toml`.
