import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java-library`
    `maven-publish`
}

description = "Spring Boot starter for Flyway native (non-JDBC) connectors"

apply(from = "../gradle/publishing.gradle")

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
    compileOnly(libs.flyway.core)
    runtimeOnly(libs.flyway.verb.migrate)
    runtimeOnly(libs.flyway.nc.scanners)

    compileOnly(libs.springBoot.autoconfigure)

    testImplementation(platform(libs.springBoot.dependencies))
    testImplementation(libs.springBoot.starter.test)
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
