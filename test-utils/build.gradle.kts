import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode.Strict

plugins {
    kotlin("jvm")
    id("maven-publish")
    id("java-library")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":notary"))

    val cordaReleaseGroup: String by project
    val cordaVersion: String by project
    implementation("$cordaReleaseGroup:corda-test-utils:$cordaVersion")
}

kotlin {
    explicitApi = Strict
}

publishing {
    publications {
        create<MavenPublication>("zkNotary") {
            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/ingzkp/zk-notary")
            credentials {
                username = System.getenv("GITHUB_USERNAME")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
tasks.apply {
    matching { it is JavaCompile || it is org.jetbrains.kotlin.gradle.tasks.KotlinCompile }.forEach { it.dependsOn(":checkJavaVersion") }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            languageVersion = "1.3"
            apiVersion = "1.3"
            jvmTarget = "1.8"
            javaParameters = true   // Useful for reflection.
            freeCompilerArgs = listOf("-Xjvm-default=compatibility")
        }
    }
}