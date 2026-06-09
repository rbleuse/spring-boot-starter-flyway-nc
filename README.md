# spring-boot-starter-flyway-nc

A Spring Boot auto-configuration starter for Flyway's **Native Connectors** (NC) path — Flyway's non-JDBC engine introduced in Flyway 11/12 for databases like Cassandra and MongoDB.

Spring Boot's built-in `spring-boot-starter-flyway` is JDBC-only (it requires a `DataSource` bean), so this starter intentionally **does not depend on it** and reimplements the analogous initializer/strategy/customizer beans against Flyway's NC API.

## Requirements

- Spring Boot **4.1+**
- Flyway with Native Connectors enabled (`FLYWAY_NATIVE_CONNECTORS=true`). The minimum
  Flyway version depends on the database module you use: **Cassandra needs 12.5.0+**
  (when its NC module was introduced); **MongoDB needs 12.4.0+** (the version Spring
  Boot 4.1 manages).
- JVM 17+ bytecode (toolchain provisioned at JDK 25, compiled to release 17)

## Modules

This build publishes six artifacts:

| Coordinate | Purpose |
|---|---|
| `io.github.rbleuse:spring-boot-starter-flyway-nc` | The generic starter — autoconfig, properties, extension points. |
| `io.github.rbleuse:spring-boot-starter-flyway-nc-cassandra` | Cassandra support — see [the Cassandra module README](starter/spring-boot-starter-flyway-nc-cassandra/README.md). |
| `io.github.rbleuse:spring-boot-starter-flyway-nc-cassandra-test` | Thin test starter — pulls in `spring-boot-starter-flyway-nc-cassandra` plus `spring-boot-starter-test`. |
| `io.github.rbleuse:spring-boot-starter-flyway-nc-mongodb` | MongoDB support — see [the MongoDB module README](starter/spring-boot-starter-flyway-nc-mongodb/README.md). |
| `io.github.rbleuse:spring-boot-starter-flyway-nc-mongodb-test` | Thin test starter — pulls in `spring-boot-starter-flyway-nc-mongodb` plus `spring-boot-starter-test`. |
| `io.github.rbleuse:spring-boot-flyway-nc-dependencies` | A `java-platform` BOM pinning every Flyway NC module to a single, override-able version. |

## Installation

### With the Spring Boot Gradle plugin (recommended)

```kotlin
plugins {
    id("org.springframework.boot") version "4.1.0" // applies io.spring.dependency-management
}

extra["flyway.version"] = "12.6.0" // optional override; defaults to the BOM's pinned version

dependencyManagement {
    imports {
        mavenBom("io.github.rbleuse:spring-boot-flyway-nc-dependencies:<version>")
    }
}

dependencies {
    implementation("io.github.rbleuse:spring-boot-starter-flyway-nc")
    runtimeOnly("org.flywaydb:flyway-database-nc-<database>") // pick the DB module you need
}
```

For database-specific starters (which bundle the matching `flyway-database-nc-*` module and service-connection factories), see the per-database READMEs:

- [Cassandra](starter/spring-boot-starter-flyway-nc-cassandra/README.md)
- [MongoDB](starter/spring-boot-starter-flyway-nc-mongodb/README.md)

**How resolution works:** the starter declares `flyway-verb-migrate` and `flyway-nc-scanners` as `runtimeOnly`; the DB module transitively brings `flyway-core` and `flyway-nc-core`. The BOM pins every Flyway NC module to `${flyway.version}`, which `io.spring.dependency-management` lets you override via `extra["flyway.version"]` — the same mechanism Spring Boot uses for its own managed dependencies.

### Enabling Flyway's Native Connectors path

Flyway requires the `FLYWAY_NATIVE_CONNECTORS` environment variable to use the NC engine. Set it before starting your application:

```
FLYWAY_NATIVE_CONNECTORS=true
```

Without it, Flyway silently falls back to JDBC and migration will fail confusingly.

## Configuration

All properties are under the `spring.flyway-nc` prefix:

| Property | Default | Description |
|---|---|---|
| `enabled` | `true` | Set to `false` to disable autoconfig entirely. |
| `url` | — | The Flyway NC datasource URL. Required unless a `FlywayNcConnectionDetails` bean is provided, such as by a Docker Compose or Testcontainers service connection contributed by a database-specific starter. |
| `user` | `null` | Optional username. |
| `password` | `null` | Optional password. |
| `locations` | `[classpath:db/migration]` | Migration locations, Flyway-style. |
| `migration-suffixes` | `[]` | Overrides Flyway's SQL migration suffixes (e.g. `.cql`). When empty, Flyway's defaults apply. |
| `default-schema` | `null` | Default schema/keyspace for migrations — see [URL schema handling](#url-schema-handling) below for how this interacts with `url`. |

Example `application.yml`:

```yaml
spring:
  flyway-nc:
    url: <database-specific URL>
    default-schema: my_schema
    locations:
      - classpath:db/migration
```

URL formats and database-specific options are documented in the per-database READMEs ([Cassandra](starter/spring-boot-starter-flyway-nc-cassandra/README.md#cassandra-url-format), [MongoDB](starter/spring-boot-starter-flyway-nc-mongodb/README.md#mongodb-url-format)).

### URL schema handling

The `url` and `default-schema` properties interact as follows:

- If the URL already carries a path segment (e.g. `…://host:port/my_schema?…`), that path is treated as the schema and `default-schema` is **not** applied — the URL wins.
- If the URL has no path (or just `/`) and `default-schema` is set, it is URL-encoded and appended as the path before passing the URL to Flyway. This is how the autoconfig steers Flyway toward a specific schema/keyspace when the connection details don't already encode one.

### Service connections

Database-specific starters can contribute `FlywayNcConnectionDetails` beans via Spring Boot's standard service-connection mechanism (Docker Compose, Testcontainers). When such a factory is on the classpath, you can omit `spring.flyway-nc.url`, `user`, and `password` entirely. See the per-database READMEs — [Cassandra](starter/spring-boot-starter-flyway-nc-cassandra/README.md#docker-compose-service-connections), [MongoDB](starter/spring-boot-starter-flyway-nc-mongodb/README.md#docker-compose-service-connections) — for examples.

## How it works

`FlywayNcAutoConfiguration` is activated when `org.flywaydb.core.Flyway` is on the classpath and `spring.flyway-nc.enabled` is not `false`. It wires three beans, all gated with `@ConditionalOnMissingBean`:

1. **`FlywayNcConnectionDetails`** — a default implementation built from `spring.flyway-nc.url`, `user`, and `password`. Skipped when a service-connection factory (Docker Compose, Testcontainers) has already contributed one.
2. **`Flyway`** — built from `FlywayNcConnectionDetails` plus the remaining `FlywayNcProperties`. All discovered `FlywayConfigurationCustomizer` beans run against the `FluentConfiguration` builder before `.load()`, ordered via `ObjectProvider.orderedStream()`.
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
- an order dependency on a bootstrap bean — e.g. a session/client that must create the schema before migrations run — by taking that bean as a constructor parameter of the customizer.

```kotlin
@Bean
fun schemaBootstrap(client: SomeClient) = FlywayConfigurationCustomizer { config ->
    client.createSchemaIfMissing("my_schema")
    config.defaultSchema("my_schema")
}
```

For a worked Cassandra example, see [the Cassandra module README](starter/spring-boot-starter-flyway-nc-cassandra/README.md#customizing-migration-startup).

## Limitations

- **Database coverage is currently Cassandra and MongoDB.** The BOM and the dedicated database starters (`spring-boot-starter-flyway-nc-cassandra`, `spring-boot-starter-flyway-nc-mongodb`) ship wiring for these two today. Other Flyway NC database modules can still be used with the generic starter by adding the appropriate `flyway-database-nc-*` dependency yourself, but there is no equivalent service-connection support for them.
- **Spring Boot 4.1+ is required.** The starter targets the Boot 4.1 autoconfiguration / `ConnectionDetails` APIs (released with Spring Boot 4.1, which manages Flyway 12.4.0).
- **`FLYWAY_NATIVE_CONNECTORS=true` is mandatory.** The starter does not — and cannot — set it for you. Without it Flyway silently uses its JDBC engine.
- **No `DataSource` integration.** This is intentional (the NC engine has no `DataSource`), but it means everything Spring Boot's JDBC Flyway starter relies on a `DataSource` for (e.g. autodetection of credentials from `spring.datasource.*`) does not apply here. Configure `spring.flyway-nc.*` or supply a `FlywayNcConnectionDetails` bean.
- **`migration-suffixes` replaces Flyway's defaults rather than adding to them.** When set, only the listed suffixes are recognized.

## Building from source

Gradle (Kotlin DSL), use the wrapper:

```
./gradlew build                                       # compile + test + assemble all artifacts
./gradlew test                                        # unit + integration tests (JUnit 5)
./gradlew :spring-boot-starter-flyway-nc-cassandra:test --tests "*IT"   # Cassandra integration test (Testcontainers — requires Docker)
./gradlew :spring-boot-starter-flyway-nc-mongodb:test --tests "*IT"     # MongoDB integration test (Testcontainers — requires Docker)
```

The `Test` task always sets `FLYWAY_NATIVE_CONNECTORS=true`. Running JUnit directly from an IDE without that env var makes Flyway silently fall back to JDBC and tests fail confusingly — check this first if a failure looks like Flyway is hitting JDBC.

To build against a different Flyway version, edit the `flyway` entry in `gradle/libs.versions.toml` — it is the single source of truth for both the starter's runtime deps and the BOM's pinned versions.
