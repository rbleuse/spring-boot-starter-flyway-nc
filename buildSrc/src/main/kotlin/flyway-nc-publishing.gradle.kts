plugins {
    `maven-publish`
    signing
}

val githubUrl = "https://github.com/rbleuse/spring-boot-starter-flyway-nc"

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            url = githubUrl
            name = project.name
            // Read description lazily — the "maven" publication for java-library
            // modules is created during the `plugins.withId("java-library")` callback
            // below, before the consuming subproject's `description = "…"` line has run.
            description = project.providers.provider { project.description ?: project.name }
            licenses {
                license {
                    name = "MIT License"
                    url = "https://opensource.org/license/mit"
                    distribution = "repo"
                }
            }
            developers {
                developer {
                    id = "rbleuse"
                    name = "Rémi Bleuse"
                    email = "remi.bleuse@gmail.com"
                }
            }
            scm {
                connection = "scm:git:https://github.com/rbleuse/spring-boot-starter-flyway-nc.git"
                developerConnection = "scm:git:ssh://git@github.com/rbleuse/spring-boot-starter-flyway-nc.git"
                url = githubUrl
            }
        }
    }

    repositories {
        maven {
            name = "centralPortalSnapshots"
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            credentials {
                username = providers.gradleProperty("centralPortalUsername")
                    .orElse(providers.environmentVariable("CENTRAL_PORTAL_USERNAME"))
                    .orNull
                password = providers.gradleProperty("centralPortalPassword")
                    .orElse(providers.environmentVariable("CENTRAL_PORTAL_PASSWORD"))
                    .orNull
            }
        }
    }
}

val signingKeyProvider = providers.gradleProperty("signingInMemoryKey")
    .orElse(providers.environmentVariable("GPG_SECRET_KEY"))
val signingPassphraseProvider = providers.gradleProperty("signingInMemoryKeyPassword")
    .orElse(providers.environmentVariable("GPG_PASSPHRASE"))

signing {
    // Set up keys if available; if not, the Sign tasks will fail-fast at
    // execution time (see below) only when actually invoked.
    if (signingKeyProvider.isPresent && signingPassphraseProvider.isPresent) {
        useInMemoryPgpKeys(signingKeyProvider.get(), signingPassphraseProvider.get())
    }
}

publishing.publications.withType<MavenPublication>().configureEach {
    signing.sign(this)
}

tasks.withType<Sign>().configureEach {
    // Snapshots never need signing — skip the task entirely. Reading
    // `project.version` here happens at execution time, so this is safe
    // regardless of where the consuming build file applies this plugin.
    onlyIf { !project.version.toString().endsWith("-SNAPSHOT") }

    doFirst {
        if (!signingKeyProvider.isPresent || !signingPassphraseProvider.isPresent) {
            throw GradleException(
                "Release builds require a GPG key and passphrase. " +
                    "Set GPG_SECRET_KEY + GPG_PASSPHRASE env vars, or " +
                    "signingInMemoryKey + signingInMemoryKeyPassword gradle properties."
            )
        }
    }
}

// Standard java-library publishing block. The BOM uses java-platform and
// declares its own publishing block (with the pom.withXml rewrite), so
// we filter on java-library — that's applied to the starters only.
plugins.withId("java-library") {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
            }
        }
    }
}
