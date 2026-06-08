import org.gradle.api.tasks.testing.AggregateTestReport
import org.gradle.api.tasks.testing.TestReport
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

plugins {
    base
    `test-report-aggregation`
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
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

val coverageProjectPaths = listOf(
    ":spring-boot-starter-flyway-nc",
    ":spring-boot-starter-flyway-nc-cassandra",
    ":spring-boot-starter-flyway-nc-mongodb",
)

subprojects {
    if (path in coverageProjectPaths) {
        pluginManager.apply("org.jetbrains.kotlinx.kover")

        extensions.configure<KoverProjectExtension>("kover") {
            useJacoco("0.8.15")
        }
    }
}

kover {
    useJacoco("0.8.15")

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
    dependsOn(tasks.named("koverHtmlReport"))
    dependsOn(tasks.named("koverVerify"))
}
