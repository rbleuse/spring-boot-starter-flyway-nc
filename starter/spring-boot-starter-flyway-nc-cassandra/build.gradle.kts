plugins {
    `flyway-nc-kotlin-module`
    `flyway-nc-publishing`
}

description = "Cassandra support for the Spring Boot Flyway native connectors starter"

val flywayVersion: String by project

dependencies {
    api(project(":spring-boot-starter-flyway-nc"))

    compileOnly("org.flywaydb:flyway-core:$flywayVersion")
    compileOnly(platform(libs.springBoot.dependencies))
    compileOnly(libs.springBoot.autoconfigure)
    compileOnly(libs.springBoot.docker.compose)
    compileOnly(libs.springBoot.testcontainers)
    compileOnly(libs.testcontainers.cassandra)

    runtimeOnly(platform(libs.springBoot.dependencies))
    runtimeOnly("org.flywaydb:flyway-database-nc-cassandra:$flywayVersion")

    testImplementation(platform(libs.springBoot.dependencies))
    testImplementation(libs.springBoot.docker.compose)
    testImplementation(libs.springBoot.starter.test)
    testImplementation(libs.springBoot.testcontainers)
    testImplementation(libs.testcontainers.cassandra)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation("org.flywaydb:flyway-database-nc-cassandra:$flywayVersion")
}
