import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.TaskProvider

plugins {
    `flyway-nc-kotlin-module`
    `flyway-nc-publishing`
    kotlin("kapt")
}

description = "Spring Boot starter for Flyway native (non-JDBC) connectors"

val flywayVersion: String by project

dependencies {
    compileOnly("org.flywaydb:flyway-core:$flywayVersion")
    runtimeOnly("org.flywaydb:flyway-verb-migrate:$flywayVersion")
    runtimeOnly("org.flywaydb:flyway-nc-scanners:$flywayVersion")

    compileOnly(libs.springBoot.autoconfigure)

    kapt(platform(libs.springBoot.dependencies))
    kapt(libs.springBoot.configuration.processor)

    testImplementation(platform(libs.springBoot.dependencies))
    testImplementation(libs.springBoot.starter.test)
}

val runtimeClasspath: NamedDomainObjectProvider<Configuration> = configurations.named("runtimeClasspath")

val verifyFlywayVersion: TaskProvider<Task> = tasks.register("verifyFlywayVersion") {
    group = "verification"
    description = "Verifies that the resolved Flyway Core version matches flywayVersion"

    doLast {
        val resolvedVersions = runtimeClasspath
            .get()
            .incoming
            .resolutionResult
            .allComponents
            .mapNotNull { component ->
                component.moduleVersion
                    ?.takeIf { it.group == "org.flywaydb" && it.name == "flyway-core" }
                    ?.version
            }
            .distinct()

        check(resolvedVersions.size == 1) {
            "Expected exactly one resolved org.flywaydb:flyway-core version, found $resolvedVersions"
        }

        val resolvedVersion = resolvedVersions.single()
        check(resolvedVersion == flywayVersion) {
            "Configured Flyway version is $flywayVersion, but org.flywaydb:flyway-core resolved to $resolvedVersion"
        }

        logger.lifecycle("Verified resolved Flyway Core version: $resolvedVersion")
    }
}

tasks.named("test") {
    dependsOn(verifyFlywayVersion)
}

kapt {
    arguments {
        arg(
            "org.springframework.boot.configurationprocessor.additionalMetadataLocations",
            layout.projectDirectory.dir("src/main/resources").asFile.absolutePath,
        )
    }
}

// The Spring configuration processor is registered on the `kapt` (main) configuration only.
// kapt still spins up a processing round for the test source set, where no annotation processor
// claims the globally-applied arguments (additionalMetadataLocations) or kapt's own injected
// kapt.kotlin.generated option, producing a spurious "options were not recognized by any
// processor" warning. Nothing is generated for tests, so disable the test kapt round entirely.
// Matched lazily because kapt registers these tasks after this script is evaluated.
tasks.matching { it.name == "kaptTestKotlin" || it.name == "kaptGenerateStubsTestKotlin" }
    .configureEach { enabled = false }
