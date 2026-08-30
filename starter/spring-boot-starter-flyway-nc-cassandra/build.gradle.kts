plugins {
    `flyway-nc-kotlin-module`
    `flyway-nc-publishing`
}

description = "Cassandra support for the Spring Boot Flyway native connectors starter"

val flywayVersion = rootProject.extra["flywayVersionProvider"] as Provider<String>

dependencies {
    api(project(":spring-boot-starter-flyway-nc"))

    compileOnly(flywayVersion.map { "org.flywaydb:flyway-core:$it" })
    compileOnly(platform(libs.springBoot.dependencies))
    compileOnly(libs.springBoot.autoconfigure)
    compileOnly(libs.springBoot.docker.compose)
    compileOnly(libs.springBoot.testcontainers)
    compileOnly(libs.testcontainers.cassandra)

    runtimeOnly(platform(libs.springBoot.dependencies))
    runtimeOnly(flywayVersion.map { "org.flywaydb:flyway-database-nc-cassandra:$it" })

    testImplementation(platform(libs.springBoot.dependencies))
    testImplementation(libs.springBoot.docker.compose)
    testImplementation(libs.springBoot.starter.test)
    testImplementation(libs.springBoot.testcontainers)
    testImplementation(libs.testcontainers.cassandra)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(flywayVersion.map { "org.flywaydb:flyway-database-nc-cassandra:$it" })
}
