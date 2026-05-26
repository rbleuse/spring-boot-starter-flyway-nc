plugins {
    `java-library`
    `flyway-nc-publishing`
}

description = "Starter for testing Spring Boot apps that use Flyway NC with MongoDB"

dependencies {
    api(project(":spring-boot-starter-flyway-nc-mongodb"))
    api(platform(libs.springBoot.dependencies))
    api(libs.springBoot.starter.test)
}
