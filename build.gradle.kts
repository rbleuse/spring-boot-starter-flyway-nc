group = "io.github.rbleuse"
version = "1.0.0-SNAPSHOT"
description = "Spring Boot starters for Flyway native connectors"

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
        maven("https://repo.spring.io/milestone")
    }
}
