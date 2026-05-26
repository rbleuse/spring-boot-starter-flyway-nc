plugins {
    `java-library`
    `flyway-nc-publishing`
}

description = "Starter for testing Spring Boot apps that use Flyway NC with Cassandra"

dependencies {
    api(project(":spring-boot-starter-flyway-nc-cassandra"))
    api(platform(libs.springBoot.dependencies))
    api(libs.springBoot.starter.test)
}
