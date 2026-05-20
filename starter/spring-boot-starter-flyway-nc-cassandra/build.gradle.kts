plugins {
    `flyway-nc-kotlin-module`
}

description = "Cassandra support for the Spring Boot Flyway native connectors starter"

dependencies {
    api(project(":spring-boot-starter-flyway-nc"))

    compileOnly(platform(libs.springBoot.dependencies))
    compileOnly(libs.springBoot.autoconfigure)
    compileOnly(libs.springBoot.docker.compose)
    compileOnly(libs.springBoot.testcontainers)
    compileOnly(libs.testcontainers.cassandra)
    runtimeOnly(libs.flyway.database.nc.cassandra)

    testImplementation(platform(libs.springBoot.dependencies))
    testImplementation(libs.springBoot.docker.compose)
    testImplementation(libs.springBoot.starter.test)
    testImplementation(libs.springBoot.testcontainers)
    testImplementation(libs.testcontainers.cassandra)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.flyway.database.nc.cassandra)
}
