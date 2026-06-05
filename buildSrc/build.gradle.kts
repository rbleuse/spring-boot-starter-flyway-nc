plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.allopen)
    // Make the type-safe `libs` accessor visible inside precompiled script plugins.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
