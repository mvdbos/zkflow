import com.ing.zkflow.gradle.task.CreateZincDirectoriesForCircuitTask
import com.ing.zkflow.compilation.zinc.template.parameters.BigDecimalTemplateParameters
import com.ing.zkflow.compilation.zinc.template.parameters.AmountTemplateParameters
import com.ing.zkflow.compilation.zinc.template.parameters.StringTemplateParameters

plugins {
    kotlin("jvm") version "1.5.31"
    id("com.ing.zkflow.gradle-plugin")
}

zkp {
    addConfigurations(StringTemplateParameters(33))

    val bigDecimalParameters = listOf(
        BigDecimalTemplateParameters(25, 6),
        BigDecimalTemplateParameters(102, 20)
    )
    addConfigurations(bigDecimalParameters)


    val amountParameters = bigDecimalParameters.map { AmountTemplateParameters(it, 8) }
    addConfigurations(amountParameters)
}

repositories {
    google()
    maven("https://jitpack.io")
    maven("https://software.r3.com/artifactory/corda")
    maven("https://repo.gradle.org/gradle/libs-releases")
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
}

// Normally this build is run after the included build, but this ensures that all dependencies
// exist at build time. This is not necessary when using the real maven dependencies instead
// of a composite build.
tasks.matching {
    it.name == "processZincSources" ||
        it is CreateZincDirectoriesForCircuitTask
//            it is com.ing.zkflow.gradle.task.CopyZincCircuitSourcesTask ||
//            it is com.ing.zkflow.gradle.task.CopyZincPlatformSourcesTask ||
//            it is com.ing.zkflow.gradle.task.GenerateZincPlatformCodeFromTemplatesTask ||
//            it is com.ing.zkflow.gradle.task.PrepareCircuitForCompilationTask
}.forEach {
    val parentProject = gradle.includedBuild(project.rootDir.parentFile.name)
    it.mustRunAfter(parentProject.task(":zinc-platform-sources:assemble"))
    it.dependsOn(parentProject.task(":zinc-platform-sources:assemble"))
}
