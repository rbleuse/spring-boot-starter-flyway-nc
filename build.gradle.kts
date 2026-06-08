import org.gradle.api.tasks.testing.AggregateTestReport
import org.gradle.api.tasks.testing.TestReport

plugins {
    base
    `test-report-aggregation`
}

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

dependencies {
    testReportAggregation(project(":spring-boot-starter-flyway-nc"))
    testReportAggregation(project(":spring-boot-starter-flyway-nc-cassandra"))
    testReportAggregation(project(":spring-boot-starter-flyway-nc-mongodb"))
}

reporting {
    reports {
        val testAggregateTestReport by creating(AggregateTestReport::class) {
            testSuiteName = "test"
        }
    }
}

tasks.check {
    dependsOn(tasks.named<TestReport>("testAggregateTestReport"))
}
