group = "io.github.rbleuse"
version = "1.0.0-SNAPSHOT"
description = "Spring Boot starters for Flyway native (non-JDBC) connectors"

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
        maven("https://repo.spring.io/milestone")
    }
}

subprojects {
    apply(plugin = "maven-publish")
    apply(from = "${rootDir}/gradle/publishing.gradle")

    // Standard java-library publishing block. The BOM uses javaPlatform and
    // declares its own publishing block (with the pom.withXml rewrite), so
    // we filter on java-library — that's applied to the starters only.
    plugins.withId("java-library") {
        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                }
            }
        }
    }
}
