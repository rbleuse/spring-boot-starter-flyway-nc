import org.gradle.api.tasks.testing.AggregateTestReport
import org.gradle.api.tasks.testing.TestReport

plugins {
    base
    `test-report-aggregation`
    id("com.gradleup.nmcp.aggregation") version "1.6.1"
    id("org.jetbrains.kotlinx.kover")
}

group = "io.github.rbleuse"
version = "2.0.0-SNAPSHOT"
description = "Spring Boot starters for Flyway native connectors"

val springBootBom = libs.springBoot.dependencies.get()
val springBootBomPom = configurations.detachedConfiguration(
    dependencies.create(
        "${springBootBom.module.group}:${springBootBom.module.name}:" +
            "${springBootBom.versionConstraint.requiredVersion}@pom",
    ),
)
// Gradle imports BOM constraints but does not expose their Maven properties.
// Read the cached BOM POM so Flyway NC modules absent from Spring's managed list stay aligned.
val flywayVersion = providers.gradleProperty("flywayVersion").orElse(
    providers.provider {
        Regex("<flyway\\.version>([^<]+)</flyway\\.version>")
            .find(springBootBomPom.singleFile.readText())
            ?.groupValues
            ?.get(1)
            ?: error("Spring Boot BOM does not declare flyway.version")
    },
)
extra["flywayVersionProvider"] = flywayVersion

allprojects {
    group = rootProject.group
    version = rootProject.version

    pluginManager.withPlugin("maven-publish") {
        pluginManager.apply("com.gradleup.nmcp")
    }

    repositories {
        mavenCentral()
        maven("https://repo.spring.io/milestone")
    }
}

val coverageProjectPaths = listOf(
    ":spring-boot-starter-flyway-nc",
    ":spring-boot-starter-flyway-nc-cassandra",
    ":spring-boot-starter-flyway-nc-mongodb",
)

dependencies {
    coverageProjectPaths.forEach { testReportAggregation(project(it)) }
    subprojects.forEach { nmcpAggregation(project.dependencies.project(it.path)) }
}

nmcpAggregation {
    centralPortal {
        username = providers.environmentVariable("CENTRAL_PORTAL_USERNAME").orNull
        password = providers.environmentVariable("CENTRAL_PORTAL_PASSWORD").orNull
        publishingType = "AUTOMATIC"
    }
}

kover {
    useJacoco(libs.versions.jacoco.get())

    merge {
        projects(*coverageProjectPaths.toTypedArray())
    }

    reports {
        total {
            html {
                onCheck = true
            }
        }

        verify {
            rule {
                minBound(80)
            }
        }
    }
}

reporting {
    reports {
        create<AggregateTestReport>("testAggregateTestReport") {
            testSuiteName = "test"
        }
    }
}

tasks.check {
    dependsOn(tasks.named<TestReport>("testAggregateTestReport"))
    dependsOn(tasks.named("koverVerify"))
}

tasks.register("printFlywayVersion") {
    group = "help"
    description = "Prints the Flyway version managed by Spring Boot or overridden with -PflywayVersion"
    doLast { println(flywayVersion.get()) }
}
