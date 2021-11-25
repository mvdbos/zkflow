plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("maven-publish")
}

repositories {
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    val arrowMetaVersion: String by project
    implementation("io.arrow-kt:arrow-meta:$arrowMetaVersion")

    implementation(project(":serialization-candidate"))
    implementation(project(":annotations"))
    implementation(project(":utils"))
}

publishing {
    publications {
        create<MavenPublication>("arrowCompilerPlugin") {
            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/ingzkp/zkflow")
            credentials {
                username = System.getenv("GITHUB_USERNAME")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
