import org.gradle.api.tasks.testing.AggregateTestReport
import org.gradle.api.tasks.testing.TestReport

plugins {
    base
    `test-report-aggregation`
    id("org.jetbrains.kotlinx.kover")
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

val coverageProjectPaths = listOf(
    ":spring-boot-starter-flyway-nc",
    ":spring-boot-starter-flyway-nc-cassandra",
    ":spring-boot-starter-flyway-nc-mongodb",
)

dependencies {
    coverageProjectPaths.forEach { testReportAggregation(project(it)) }
}

kover {
    useJacoco(libs.versions.jacoco.get())

    merge {
        projects(*coverageProjectPaths.toTypedArray())
    }

    reports {
        total {
            html {
                onCheck = true
            }
        }

        verify {
            rule {
                minBound(80)
            }
        }
    }
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
    dependsOn(tasks.named("koverVerify"))
}
