plugins {
    `flyway-nc-kotlin-module`
    kotlin("kapt")
}

description = "Spring Boot starter for Flyway native (non-JDBC) connectors"

dependencies {
    compileOnly(libs.flyway.core)
    runtimeOnly(libs.flyway.verb.migrate)
    runtimeOnly(libs.flyway.nc.scanners)

    compileOnly(libs.springBoot.autoconfigure)

    kapt(platform(libs.springBoot.dependencies))
    kapt(libs.springBoot.configuration.processor)

    testImplementation(platform(libs.springBoot.dependencies))
    testImplementation(libs.springBoot.starter.test)
}

tasks.matching { it.name == "kaptKotlin" }.configureEach {
    dependsOn(tasks.processResources)
}
