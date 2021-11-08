plugins {
    kotlin("jvm")
    `java-library`
    id("maven-publish")
}

dependencies {
    val kotlinxSerializationVersion: String by project
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
}

publishing {
    publications {
        create<MavenPublication>("zkZincPoetPoet") {
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
