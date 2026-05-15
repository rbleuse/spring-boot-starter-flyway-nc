import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    `java-library`
    `maven-publish`
}

group = "com.github.rbleuse"
version = "1.0.0-SNAPSHOT"
description = "Spring Boot starter for Flyway native (non-JDBC) connectors"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }

    withSourcesJar()
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

repositories {
    mavenCentral()
    maven("https://repo.spring.io/milestone")
}

dependencies {
    compileOnly(libs.flyway.core)
    runtimeOnly(libs.flyway.verb.migrate)
    runtimeOnly(libs.flyway.nc.scanners)

    compileOnly(libs.springBoot.autoconfigure)
    implementation(libs.kotlin.reflect)

    testImplementation(platform(libs.springBoot.dependencies))
    testImplementation(libs.springBoot.starter.test)
    testImplementation(libs.springBoot.testcontainers)
    testImplementation(libs.testcontainers.cassandra)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.flyway.database.nc.cassandra)
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
