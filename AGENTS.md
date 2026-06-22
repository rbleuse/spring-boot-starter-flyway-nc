# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## What this project is

A Spring Boot auto-configuration starter for Flyway's **Native Connectors** (NC) path — Flyway's non-JDBC engine (Flyway 11/12+) used for databases like Cassandra and MongoDB. Spring Boot's built-in `spring-boot-starter-flyway` is JDBC-only and requires a `DataSource` bean, so this starter intentionally **does not depend on it** and reimplements the analogous initializer/strategy/customizer beans against Flyway's NC API.

Requirements: Spring Boot 4.1+, Flyway 12.5+ with NC enabled, JVM 17+ bytecode.

## Build & test commands

Use the Gradle wrapper:

```
./gradlew build                                       # compile + test + assemble all artifacts
./gradlew test                                        # unit + integration tests (JUnit 5)
./gradlew :spring-boot-starter-flyway-nc-cassandra:test --tests "*IT"   # Cassandra IT only (Testcontainers — requires Docker)
./gradlew :spring-boot-starter-flyway-nc-mongodb:test --tests "*IT"     # MongoDB IT only (Testcontainers — requires Docker)
./gradlew :spring-boot-starter-flyway-nc-cassandra:test --tests "io.github.rbleuse.flywaync.FlywayNcAutoConfigurationTest.method name"
./gradlew :spring-boot-flyway-nc-dependencies:generatePomFileForMavenPublication  # inspect the BOM POM
```

**Critical:** every `Test` task in the build sets `FLYWAY_NATIVE_CONNECTORS=true`. Running JUnit directly from an IDE without that env var makes Flyway silently fall back to JDBC and tests fail confusingly. If a test failure looks like Flyway is hitting JDBC, check this first.

To build against a different Flyway version, pass `-PflywayVersion=<version>` (e.g. `./gradlew build -PflywayVersion=12.8.0`); the default lives in `gradle.properties` as `flywayVersion` (single source of truth — both the starters' runtime deps and the BOM's pinned versions read from it). CI exercises several Flyway versions via the `compatibility` matrix in `.github/workflows/test.yaml`, whose version list is the data file `.github/flyway-versions.json` (kept out of the workflow file so the default `GITHUB_TOKEN` can edit it — it cannot push changes to `.github/workflows/*`). The daily `.github/workflows/flyway-version-check.yaml` cron appends newly released Flyway versions (latest patch per new minor, including new majors; patch-only bumps ignored) to that data file and opens a PR after validating each new version in parallel matrix jobs.

## Toolchain quirk

The `flyway-nc-kotlin-module` convention plugin provisions a JDK 25 toolchain but compiles Kotlin with `jvmTarget = JVM_17` and Java with `options.release = 17`. Don't "fix" this to align — the project must produce JVM 17 bytecode while using a modern toolchain.

## Module / artifact layout

This is a **six-module Gradle build** producing six published artifacts:

- `:spring-boot-starter-flyway-nc` (dir `starter/spring-boot-starter-flyway-nc/`) — the generic starter (Kotlin, `java-library`). Holds the autoconfig, properties, and extension-point interfaces.
- `:spring-boot-starter-flyway-nc-cassandra` (dir `starter/spring-boot-starter-flyway-nc-cassandra/`) — Cassandra-specific starter (Kotlin, `java-library`). `api`-depends on the generic starter; brings `flyway-database-nc-cassandra` as `runtimeOnly` and contributes Spring Boot service-connection factories for Docker Compose and Testcontainers.
- `:spring-boot-starter-flyway-nc-cassandra-test` (dir `starter/spring-boot-starter-flyway-nc-cassandra-test/`) — thin test starter (no Kotlin, no `src/`, just a `build.gradle.kts`). `api`-depends on the Cassandra starter and `spring-boot-starter-test`. Mirrors Spring Boot's `spring-boot-starter-flyway-test`.
- `:spring-boot-starter-flyway-nc-mongodb` (dir `starter/spring-boot-starter-flyway-nc-mongodb/`) — MongoDB-specific starter (Kotlin, `java-library`). Same shape as the Cassandra module; brings `flyway-database-nc-mongodb` as `runtimeOnly`.
- `:spring-boot-starter-flyway-nc-mongodb-test` (dir `starter/spring-boot-starter-flyway-nc-mongodb-test/`) — thin test starter. Same shape as the Cassandra test starter.
- `:spring-boot-flyway-nc-dependencies` (dir `platform/spring-boot-flyway-nc-dependencies/`) — a `java-platform` BOM pinning every Flyway NC module and all DB starters to one version.

The root project has no source — it only configures `group`/`version`/repositories for `allprojects`. Shared module setup lives in precompiled convention plugins under `buildSrc/src/main/kotlin/`: `flyway-nc-kotlin-module.gradle.kts` configures Kotlin/Java compilation and tests, while `flyway-nc-publishing.gradle.kts` configures Maven publishing, signing, POM metadata, and source/Javadoc jars. Each module applies the conventions it needs. The BOM module has no source either — only the `dependencies { constraints { ... } }` block and a custom `publishing { }` block in `platform/spring-boot-flyway-nc-dependencies/build.gradle.kts`.

## How resolution is intended to work for consumers

The generic starter declares `flyway-verb-migrate` and `flyway-nc-scanners` as `runtimeOnly`. A consumer who wants a specific database adds one of the DB-specific starters (Cassandra → `flyway-database-nc-cassandra`, MongoDB → `flyway-database-nc-mongodb`) or a `flyway-database-nc-*` module of their choice — that module transitively pulls `flyway-core` + `flyway-nc-core`. The BOM pins all Flyway NC modules to `${flyway.version}` so `io.spring.dependency-management` consumers can override via `extra["flyway.version"]`, exactly like Spring Boot's own managed deps.

This `${flyway.version}` placeholder is **not** what Gradle's `java-platform` produces by default — `platform/spring-boot-flyway-nc-dependencies/build.gradle.kts` rewrites the generated POM XML in `pom.withXml { ... }` to:
1. Add a `<properties><flyway.version>…</flyway.version></properties>` default.
2. Replace the literal version on every `org.flywaydb:*` constraint with `${flyway.version}`.

If you touch this code, regenerate the POM and verify both substitutions still happen — losing either one breaks the version-override contract that's the BOM's reason to exist.

## Runtime architecture

`FlywayNcAutoConfiguration` is the generic entry point, registered via `starter/spring-boot-starter-flyway-nc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. It activates when `Flyway` is on the classpath and `spring.flyway-nc.enabled != false`, and wires three beans, all `@ConditionalOnMissingBean`:

1. `FlywayNcConnectionDetails` — a default implementation built from `spring.flyway-nc.url` / `user` / `password`. Skipped when a service-connection factory (Docker Compose, Testcontainers, or a user bean) has already contributed one. The interface extends Spring Boot 4.1's `ConnectionDetails`, so it participates in the standard `@ServiceConnection` machinery.
2. `Flyway` — built from `FlywayNcConnectionDetails` plus the remaining `FlywayNcProperties`. The autoconfig has explicit URL/schema handling: if the URL already carries a path it is treated as the schema and `defaultSchema` is **not** applied (URL wins); otherwise, if `defaultSchema` is set, it is URL-encoded and appended as the URL path before `Flyway.configure().dataSource(...)`. All `FlywayConfigurationCustomizer` beans run against the `FluentConfiguration` builder before `.load()`, ordered via `ObjectProvider.orderedStream()`. If you touch this logic, preserve the "URL wins" invariant — silently overriding a user-supplied path with `default-schema` was the bug it was added to prevent.
3. `FlywayNcMigrationInitializer` — an `InitializingBean` that calls `flyway.migrate()` (or a user-supplied `FlywayNcMigrationStrategy`) in `afterPropertiesSet()`.

The two extension points (`FlywayNcMigrationStrategy`, `FlywayConfigurationCustomizer`) intentionally mirror Spring Boot's JDBC Flyway API so users coming from the standard starter find a familiar shape. Preserve that parallel when extending the API.

`FlywayConfigurationCustomizer` is the right escape hatch for settings not surfaced as properties, and for ordering dependencies on bootstrap beans (e.g. a `CqlSession` that must create a keyspace before migration runs — declare it as a constructor param of the customizer).

Database-specific modules may register additional auto-configuration around the generic entry point. The Cassandra starter registers `CassandraFlywayNcAutoConfiguration` before and imports the generic auto-configuration. It contributes a highest-precedence customizer that defaults `sqlMigrationSuffixes` to `.cql` only when `spring.flyway-nc.migration-suffixes` is empty; an explicit property or a later user customizer can override it.

## Cassandra module specifics

`starter/spring-boot-starter-flyway-nc-cassandra/src/main/resources/META-INF/spring.factories` registers two `ConnectionDetailsFactory` implementations (not autoconfig classes — these are picked up via the legacy `spring.factories` mechanism that `ConnectionDetailsFactories` uses):

- `CassandraFlywayNcDockerComposeConnectionDetailsFactory` — keys off compose services using the `cassandra` image. Reads `CASSANDRA_DC`/`CASSANDRA_DATACENTER` (default `datacenter1`), `CASSANDRA_KEYSPACE`, `CASSANDRA_USER`/`CASSANDRA_USERNAME`, `CASSANDRA_PASSWORD` from the service env. Builds a `cassandra://host:mappedPort/<keyspace>?localdatacenter=<dc>` URL, URL-encoding the keyspace and DC.
- `CassandraContainerFlywayNcConnectionDetailsFactory` — adapts a `org.testcontainers.cassandra.CassandraContainer` (the modern Testcontainers module, not the legacy `org.testcontainers.containers.CassandraContainer`) into `FlywayNcConnectionDetails`. URL is derived from `contactPoint` + `localDatacenter`; credentials come from `container.username` / `container.password`. No keyspace is embedded — set `spring.flyway-nc.default-schema` (or use an init script) when migrations need a specific keyspace.

`starter/spring-boot-starter-flyway-nc-cassandra/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` also registers `CassandraFlywayNcAutoConfiguration`, which supplies the `.cql` migration-suffix default described above.

## MongoDB module specifics

`starter/spring-boot-starter-flyway-nc-mongodb/src/main/resources/META-INF/spring.factories` registers two `ConnectionDetailsFactory` implementations following the same pattern as Cassandra:

- `MongoDBFlywayNcDockerComposeConnectionDetailsFactory` — keys off compose services using the `mongo` image. Reads `MONGO_INITDB_DATABASE`, `MONGO_INITDB_ROOT_USERNAME`, `MONGO_INITDB_ROOT_PASSWORD` from the service env. Builds a `mongodb://host:mappedPort[/<database>]` URL, URL-encoding the database name, and appends `?authSource=admin` when root credentials are present.
- `MongoDBContainerFlywayNcConnectionDetailsFactory` — adapts a `org.testcontainers.mongodb.MongoDBContainer` (the modern Testcontainers 2.x package, from `org.testcontainers:testcontainers-mongodb`) into `FlywayNcConnectionDetails`. URL comes from `container.connectionString`; no credentials are exposed (default `mongo` image has no auth). No database is embedded — set `spring.flyway-nc.default-schema` when migrations need a specific database.

**Mongo migration formats:** Flyway MongoDB NC accepts `.js` and `.json` only — and `.js` requires the external [`mongosh`](https://www.mongodb.com/docs/mongodb-shell/) binary on the PATH at runtime, because Flyway shells out to it. The IT in this repo uses `.json` (executed directly through `mongodb-driver-sync`, no extra tools) to keep the test self-contained. `.json` migrations are a single MongoDB command document (e.g. `{"create": "my_collection"}`) — they're less expressive than `.js` but enough for collection/index management. Flyway also rejects multiple `sqlMigrationSuffixes` values for Mongo, so configure exactly one of the two.

**Mongo `flyway_schema_history` shape difference vs. Cassandra:** the Mongo NC writes an extra "baseline-style" document with a `null` `version` field alongside the per-migration entries. The Mongo IT filters those out (`mapNotNull { it.getString("version") }`) before asserting; the Cassandra IT does not need to. Keep that in mind when writing assertions against Mongo's history collection.

If you add another database starter, follow the same shape: an `api` dependency on the generic starter, `runtimeOnly` the matching `flyway-database-nc-*` module, register service-connection factories via `META-INF/spring.factories`, add the artifact to the BOM's `flywayModules` list, and (optionally) add a matching thin `-test` starter under `starter/` mirroring the Cassandra/MongoDB shape.

## Tests

Most tests live in the DB-specific modules, because exercising the autoconfig in a meaningful way requires a Flyway NC database module on the classpath. The generic starter has a small Java interop test for `FlywayNcProperties`. MongoDB has three test classes; Cassandra has those same categories plus a test for its database-specific auto-configuration:

**Cassandra:**

- `starter/spring-boot-starter-flyway-nc-cassandra/src/test/.../FlywayNcAutoConfigurationTest` — `ApplicationContextRunner`-style unit tests for the autoconfig wiring (bean creation, `enabled=false`, user override, connection-details propagation, customizer invocation). Uses a no-op `FlywayNcMigrationStrategy` bean to avoid hitting a real DB.
- `starter/spring-boot-starter-flyway-nc-cassandra/src/test/.../FlywayNcCassandraIT` — Testcontainers-backed integration test (`cassandra:5.0`) that asserts migration actually runs against a real Cassandra and that `flyway_schema_history` gets populated. Uses `@ServiceConnection` on a static `CassandraContainer` with `withInitScript("cassandra-init.cql")` to create the keyspace before migration. Requires Docker.
- `starter/spring-boot-starter-flyway-nc-cassandra/src/test/.../CassandraFlywayNcDockerComposeConnectionDetailsFactoryTest` — exercises the compose factory against a hand-rolled `RunningService` fixture; verifies the URL/user/password it produces.
- `starter/spring-boot-starter-flyway-nc-cassandra/src/test/.../CassandraFlywayNcAutoConfigurationTest` — verifies the Cassandra starter defaults migration suffixes to `.cql` and preserves an explicit `spring.flyway-nc.migration-suffixes` value.

Test migrations live under `starter/spring-boot-starter-flyway-nc-cassandra/src/test/resources/db/migration/` (`.cql` files); keyspace bootstrap is in `starter/spring-boot-starter-flyway-nc-cassandra/src/test/resources/cassandra-init.cql`.

**MongoDB:**

- `starter/spring-boot-starter-flyway-nc-mongodb/src/test/.../FlywayNcAutoConfigurationTest` — same shape as the Cassandra one, but with `mongodb://localhost:27017/test` URLs and connection details. The unit-test class deliberately lives in package `io.github.rbleuse.flywaync` (same FQN as Cassandra's) so the two test classes are addressable identically per module.
- `starter/spring-boot-starter-flyway-nc-mongodb/src/test/.../FlywayNcMongoDBIT` — Testcontainers-backed IT against `mongo:8.0.21` (the latest 8.0 LTS at time of writing). Uses `org.testcontainers.mongodb.MongoDBContainer` with `@ServiceConnection`. No init script needed — MongoDB creates databases lazily. Asserts the migration ran (`flyway_nc_it_collection` exists) and `flyway_schema_history` contains version `"1"` (filtering out the baseline `null` entry, see the MongoDB section above). Requires Docker.
- `starter/spring-boot-starter-flyway-nc-mongodb/src/test/.../MongoDBFlywayNcDockerComposeConnectionDetailsFactoryTest` — same shape as the Cassandra compose test.

Test migrations live under `starter/spring-boot-starter-flyway-nc-mongodb/src/test/resources/db/migration/` (`.json` files). See the MongoDB section above for why `.json` and not `.js`.
