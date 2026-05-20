plugins {
    `flyway-nc-kotlin-module`
}

description = "Spring Boot starter for Flyway native (non-JDBC) connectors"

dependencies {
    compileOnly(libs.flyway.core)
    runtimeOnly(libs.flyway.verb.migrate)
    runtimeOnly(libs.flyway.nc.scanners)

    compileOnly(libs.springBoot.autoconfigure)

    testImplementation(platform(libs.springBoot.dependencies))
    testImplementation(libs.springBoot.starter.test)
}
