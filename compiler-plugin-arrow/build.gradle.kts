plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("maven-publish")
    jacoco
}

dependencies {
    val arrowMetaVersion: String by project
    implementation("io.arrow-kt:arrow-meta:$arrowMetaVersion")

    implementation(project(":serialization-candidate"))
    implementation(project(":annotations"))
    implementation(project(":utils"))

    // TODO: Check if we can make this compileOnly
    val cordaVersion: String by project
    implementation("net.corda:corda-core:$cordaVersion")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xopt-in=kotlin.ExperimentalUnsignedTypes"
    }
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
