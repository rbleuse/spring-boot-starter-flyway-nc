plugins {
    `java-platform`
    `flyway-nc-publishing`
}

group = rootProject.group
version = rootProject.version
description = """
    Dependencies BOM for spring-boot-starter-flyway-nc.
    Pins compatible versions of all Flyway Native Connectors modules and the starter itself.
    When imported via io.spring.dependency-management, the Flyway version is overridable
    via `extra["flyway.version"]` (Spring Boot style).
""".trimIndent()

val flywayVersion: String by project

// Flyway NC modules currently managed by this BOM. The runtime engine pieces
// (verb-migrate, nc-scanners and the verb/nc-core/nc-callbacks they pull in)
// are needed by every consumer; the database-nc-* modules are picked per
// consumer based on their target database.
val flywayModules = listOf(
    "flyway-core",
    "flyway-nc-core",
    "flyway-nc-callbacks",
    "flyway-nc-scanners",
    "flyway-verb-baseline",
    "flyway-verb-migrate",
    "flyway-verb-schemas",
    "flyway-verb-validate",
    "flyway-database-nc-cassandra",
    "flyway-database-nc-mongodb",
)

dependencies {
    constraints {
        api("${rootProject.group}:spring-boot-starter-flyway-nc:${rootProject.version}")
        api("${rootProject.group}:spring-boot-starter-flyway-nc-cassandra:${rootProject.version}")
        api("${rootProject.group}:spring-boot-starter-flyway-nc-cassandra-test:${rootProject.version}")
        api("${rootProject.group}:spring-boot-starter-flyway-nc-mongodb:${rootProject.version}")
        api("${rootProject.group}:spring-boot-starter-flyway-nc-mongodb-test:${rootProject.version}")

        flywayModules.forEach { api("org.flywaydb:$it:$flywayVersion") }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])

            // The generated POM lists each constraint with a literal version
            // (e.g. <version>12.5.0</version>). For io.spring.dependency-management's
            // `extra["flyway.version"]` override mechanism to work, the Flyway
            // entries must reference `${flyway.version}` and the POM must declare
            // a default value under <properties>. Rewrite the XML here.
            pom.withXml {
                val pomNode = asNode()

                pomNode.appendNode("properties")
                    .appendNode("flyway.version", flywayVersion)

                fun children(node: groovy.util.Node, localName: String): List<groovy.util.Node> =
                    node.children()
                        .filterIsInstance<groovy.util.Node>()
                        .filter { it.name().toString().substringAfterLast('}').substringAfterLast(':') == localName }

                val managedDeps = children(pomNode, "dependencyManagement")
                    .flatMap { children(it, "dependencies") }
                    .flatMap { children(it, "dependency") }

                managedDeps.forEach { dep ->
                    val groupNode = children(dep, "groupId").firstOrNull() ?: return@forEach
                    if (groupNode.text() != "org.flywaydb") return@forEach
                    val versionNode = children(dep, "version").firstOrNull() ?: return@forEach
                    versionNode.setValue("\${flyway.version}")
                }
            }
        }
    }
}