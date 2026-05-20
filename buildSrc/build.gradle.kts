plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    implementation("org.jetbrains.kotlin:kotlin-allopen:2.3.21")
    // Make the type-safe `libs` accessor visible inside precompiled script plugins.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
