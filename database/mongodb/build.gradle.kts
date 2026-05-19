import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java-library`
    `maven-publish`
}

description = "MongoDB support for the Spring Boot Flyway native connectors starter"

apply(from = "../../gradle/publishing.gradle")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }

    withSourcesJar()
    withJavadocJar()
}

kotlin {
    jvmToolchain(25)

    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

dependencies {
    api(project(":spring-boot-starter-flyway-nc"))

    compileOnly(platform(libs.springBoot.dependencies))
    compileOnly(libs.springBoot.autoconfigure)
    compileOnly(libs.springBoot.docker.compose)
    compileOnly(libs.springBoot.testcontainers)
    compileOnly(libs.testcontainers.mongodb)
    runtimeOnly(libs.flyway.database.nc.mongodb)

    testImplementation(platform(libs.springBoot.dependencies))
    testImplementation(libs.springBoot.docker.compose)
    testImplementation(libs.springBoot.starter.test)
    testImplementation(libs.springBoot.testcontainers)
    testImplementation(libs.testcontainers.mongodb)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.flyway.database.nc.mongodb)
    testImplementation(libs.mongodb.driver.sync)
    testImplementation(libs.kotest.assertions.core)

    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
    environment("FLYWAY_NATIVE_CONNECTORS", "true")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
