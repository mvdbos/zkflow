package com.ing.zknotary.gradle.extension

import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import java.io.File

open class ZKNotaryExtension(project: Project) {

    companion object {
        const val NAME = "zkp"

        private const val MERGED_CIRCUIT_BUILD_PATH = "zinc"
        private const val CIRCUIT_SOURCES_BASE_PATH = "src/main/zinc"

        val float = BigDecimalTemplateParameters(39, 46, "Float")
        val double = BigDecimalTemplateParameters(309, 325, "Double")
    }

    @Input
    var stringConfigurations: List<StringTemplateParameters> = emptyList()

    @Input
    var bigDecimalConfigurations: List<BigDecimalTemplateParameters> = emptyList()

    @Input
    var amountConfigurations: List<AmountTemplateParameters> = emptyList()

    @Input
    var zincPlatformSourcesVersion: String? = "1.0-SNAPSHOT"

    @Input
    var notaryVersion: String? = "1.0-SNAPSHOT"

    @OutputDirectory
    val mergedCircuitOutputPath: File = project.buildDir.resolve(MERGED_CIRCUIT_BUILD_PATH)

    @InputDirectory
    val circuitSourcesBasePath: File = project.projectDir.resolve(CIRCUIT_SOURCES_BASE_PATH)

    @Input
    val platformSourcesPath = "zinc-platform-sources/**/*.zn"

    @Input
    val platformTemplatesPath = "zinc-platform-templates/**/*.zn"

    @Input
    val platformSamplesPath = "zinc-platform-samples/**/*.zn"

    @Input
    val merkleTemplate = "merkle_template.zn"

    @Input
    val mainTemplate = "main_template.zn"

    private val fixedTemplateParameters: List<TemplateParameters> = listOf(
        float,
        double,
        UniqueIdentifierTemplateParameters,
        LinearPointerTemplateParameters,
    )

    fun resolveAllTemplateParameters(): List<TemplateParameters> {
        return (fixedTemplateParameters + stringConfigurations + bigDecimalConfigurations + amountConfigurations)
            .flatMap { it.resolveAllConfigurations() }
            .distinct()
    }
}
