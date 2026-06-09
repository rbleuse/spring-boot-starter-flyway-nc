# spring-boot-starter-flyway-nc-mongodb

MongoDB support for [`spring-boot-starter-flyway-nc`](../../README.md) — Flyway's Native Connectors path applied to MongoDB via the [`flyway-database-nc-mongodb`](https://documentation.red-gate.com/flyway/flyway-concepts/database-objects/database-native-connectors) module.

This starter:

- `api`-depends on the generic starter, so adding it is enough to get autoconfig + MongoDB wiring.
- Brings `org.flywaydb:flyway-database-nc-mongodb` as `runtimeOnly`.
- Contributes Spring Boot service-connection factories for Docker Compose and Testcontainers so `spring.flyway-nc.*` properties can be omitted in most setups.

## Installation

```kotlin
dependencies {
    implementation("io.github.rbleuse:spring-boot-starter-flyway-nc-mongodb")
}
```

See the [root README](../../README.md#installation) for the recommended setup with the Spring Boot Gradle plugin and the `spring-boot-flyway-nc-dependencies` BOM, plus the mandatory `FLYWAY_NATIVE_CONNECTORS=true` environment variable.

**Minimum Flyway version:** 12.4.0 — the Flyway version managed by Spring Boot 4.1.

## Configuration

All `spring.flyway-nc.*` properties from the generic starter apply — see the [root README](../../README.md#configuration) for the full list. A minimal MongoDB configuration looks like:

```yaml
spring:
  flyway-nc:
    url: mongodb://localhost:27017
    default-schema: my_database
    migration-suffixes: [".json"]
```

### MongoDB URL format

The MongoDB Flyway NC URL is a standard MongoDB connection string:

```
mongodb://<host>:<port>[/<database>][?<options>]
```

- The path segment, if present, is the database name. Per the [URL schema handling rules](../../README.md#url-schema-handling), a path in the URL wins over `spring.flyway-nc.default-schema`.
- Username and password go through `spring.flyway-nc.user` / `spring.flyway-nc.password` rather than being embedded in the URL.

### Migration file formats — important

Flyway MongoDB NC accepts exactly **two** migration suffixes, and only one can be active at a time (`migration-suffixes` must contain exactly one entry):

- **`.json`** — driver-only. Each file is a single MongoDB command document, executed via the standard driver. No external tooling required.

  ```json
  {
    "create": "users"
  }
  ```

- **`.js`** — full mongosh scripts. **Requires the [`mongosh`](https://www.mongodb.com/docs/mongodb-shell/) binary on the PATH at runtime**, because Flyway shells out to it. If `mongosh` is missing you'll get a startup error like *"Mongosh is required for .js migrations and is not currently installed"*. Use this when `.json`'s single-command shape is too restrictive.

  ```javascript
  db.createCollection("users");
  db.users.createIndex({ email: 1 }, { unique: true });
  ```

Pick `.json` for simple collection/index work that can be done with raw MongoDB commands; pick `.js` (and install `mongosh`) when you need procedural logic.

### Docker Compose service connections

When `org.springframework.boot:spring-boot-docker-compose` is on the classpath alongside this starter, MongoDB compose services are detected automatically:

```yaml
services:
  mongo:
    image: mongo:8.0.21
    ports:
      - "27017"
    environment:
      MONGO_INITDB_DATABASE: my_database
      MONGO_INITDB_ROOT_USERNAME: root
      MONGO_INITDB_ROOT_PASSWORD: secret
```

With the compose service running, you can omit `spring.flyway-nc.url`, `user`, and `password`. The factory reads:

- The mapped host and port for container port `27017`.
- `MONGO_INITDB_DATABASE` (optional; embedded in the URL path when set).
- Credentials from `MONGO_INITDB_ROOT_USERNAME` and `MONGO_INITDB_ROOT_PASSWORD`.

### Testcontainers service connections

When `org.springframework.boot:spring-boot-testcontainers` is on the test classpath, a `MongoDBContainer` annotated with `@ServiceConnection` supplies the connection details automatically:

```kotlin
@ServiceConnection
val mongo = MongoDBContainer(DockerImageName.parse("mongo:8.0.21"))
```

The factory derives the URL from the container's `connectionString`. The default `mongo` image runs without authentication, so no credentials are exposed. The factory does not embed a database name, so set `spring.flyway-nc.default-schema` when migrations need a specific database.

> **Note:** this starter targets the modern `org.testcontainers.mongodb.MongoDBContainer` (from `org.testcontainers:testcontainers-mongodb`), not the legacy `org.testcontainers.containers.MongoDBContainer` shipped under `org.testcontainers:mongodb`.

## Customizing migration startup

If you need to bootstrap a database, an admin user, or indexes before Flyway runs, use a [`FlywayConfigurationCustomizer`](../../README.md#flywayconfigurationcustomizer) that takes the bootstrap bean as a constructor parameter — Spring will order the customizer behind it:

```kotlin
@Bean
fun databaseBootstrap(client: MongoClient) = FlywayConfigurationCustomizer { config ->
    client.getDatabase("my_database").runCommand(Document("ping", 1))
    config.defaultSchema("my_database")
}
```

To skip migration entirely in tests, or to run `repair()` first, supply a [`FlywayNcMigrationStrategy`](../../README.md#flywayncmigrationstrategy) bean.

## Writing migrations

Place migration files under `src/main/resources/db/migration/` (or wherever `spring.flyway-nc.locations` points). Flyway's standard `V<version>__<description>.<suffix>` naming applies. Don't forget `spring.flyway-nc.migration-suffixes: [".json"]` (or `[".js"]`) — `.sql` is Flyway's default and will not match MongoDB migrations.

```json
// V1__create_users_collection.json
{
  "create": "users"
}
```

```javascript
// V2__add_users_email_index.js  (requires mongosh on PATH)
db.users.createIndex({ email: 1 }, { unique: true });
```
