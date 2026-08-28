import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java-library`
    id("org.jmailen.kotlinter")
    id("org.jetbrains.kotlinx.kover")
}

// Generated catalog accessors collide with init-plugin classloaders (e.g. dependency-submission).
val libs = versionCatalogs.named("libs")

kover {
    useJacoco(libs.findVersion("jacoco").get().requiredVersion)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    jvmToolchain(25)

    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    environment("FLYWAY_NATIVE_CONNECTORS", "true")
    // Surface skipped tests on the console. The Testcontainers ITs carry
    // @Testcontainers(disabledWithoutDocker = true), so when Docker is absent they
    // are skipped rather than failing — this makes that visible instead of silent.
    testLogging {
        events("skipped")
    }
}

dependencies {
    "testImplementation"(libs.findLibrary("kotest-assertions-core").get())
    "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
}
