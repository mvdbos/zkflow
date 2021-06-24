
import com.ing.zknotary.gradle.extension.ZKNotaryExtension
import com.ing.zknotary.gradle.task.joinConstFiles
import com.ing.zknotary.gradle.zinc.template.TemplateConfigurations
import com.ing.zknotary.gradle.zinc.template.TemplateConfigurations.Companion.doubleTemplateParameters
import com.ing.zknotary.gradle.zinc.template.TemplateConfigurations.Companion.floatTemplateParameters
import com.ing.zknotary.gradle.zinc.template.TemplateRenderer
import com.ing.zknotary.gradle.zinc.template.parameters.AbstractPartyTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.AbstractPartyTemplateParameters.Companion.ANONYMOUS_PARTY_TYPE_NAME
import com.ing.zknotary.gradle.zinc.template.parameters.AmountTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.BigDecimalTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.ByteArrayTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.CollectionTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.IssuedTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.NullableTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.PublicKeyTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.StringTemplateParameters
import com.ing.zknotary.gradle.zinc.template.parameters.TxStateTemplateParameters
import com.ing.zknotary.gradle.zinc.util.CircuitConfigurator
import com.ing.zknotary.gradle.zinc.util.CodeGenerator
import com.ing.zknotary.gradle.zinc.util.MerkleReplacer
import com.ing.zknotary.gradle.zinc.util.ZincSourcesCopier
import java.io.File

val myBigDecimalConfigurations = listOf(
    BigDecimalTemplateParameters(24, 6),
    BigDecimalTemplateParameters(100, 20),
    floatTemplateParameters,
    doubleTemplateParameters
)

val myByteArrayConfigurations = emptyList<ByteArrayTemplateParameters>()

val myAmountConfigurations = myBigDecimalConfigurations.map {
    AmountTemplateParameters(it, 8)
}

val myStringConfigurations: List<StringTemplateParameters> = listOf(StringTemplateParameters(32))

val myIssuedConfigurations: List<IssuedTemplateParameters<*>> = listOf(
    IssuedTemplateParameters(
        AbstractPartyTemplateParameters(ANONYMOUS_PARTY_TYPE_NAME, PublicKeyTemplateParameters.eddsaTemplateParameters),
        StringTemplateParameters(1)
    )
)

val myNullableConfigurations = emptyList<NullableTemplateParameters<*>>()

val myCollectionConfigurations = listOf(
    CollectionTemplateParameters(collectionSize = 3, innerTemplateParameters = StringTemplateParameters(1)),
    CollectionTemplateParameters("collection_integer.zn", collectionSize = 3, platformModuleName = "u32"),
    // Collection of participants to TestState.
    CollectionTemplateParameters(
        collectionSize = 2,
        innerTemplateParameters = AbstractPartyTemplateParameters(
            ANONYMOUS_PARTY_TYPE_NAME,
            PublicKeyTemplateParameters.eddsaTemplateParameters
        )
    ),
)

val templateConfigurations = TemplateConfigurations().apply {
    stringConfigurations = myStringConfigurations
    byteArrayConfigurations = myByteArrayConfigurations
    amountConfigurations = myAmountConfigurations
    bigDecimalConfigurations = myBigDecimalConfigurations
    issuedConfigurations = myIssuedConfigurations
    nullableConfigurations = myNullableConfigurations
    collectionConfigurations = myCollectionConfigurations
}

fun main(args: Array<String>) {
    val root = args[0]
    val projectVersion = args[1]

    val circuitSourcesBase = File("$root/circuits")
    val statesPath = "states"
    val circuitStates = circuitSourcesBase.resolve(statesPath)
    val mergedCircuitOutput = File("$root/build/circuits")

    circuitSourcesBase
        .listFiles { dir, file ->
            dir.resolve(file).isDirectory && file != statesPath
        }
        ?.map { it.name }
        ?.forEach { circuitName ->
            val outputPath = mergedCircuitOutput.resolve(circuitName).resolve("src")
            val circuitSourcesPath = circuitSourcesBase.resolve(circuitName)

            // Read the configuration
            val configurator = CircuitConfigurator(circuitSourcesPath, ZKNotaryExtension.CONFIG_CIRCUIT_FILE)
            configurator.generateConstsFile(outputPath)

            // Copy Zinc sources
            val copier = ZincSourcesCopier(outputPath)
            copier.copyZincCircuitSources(circuitSourcesPath, circuitName, projectVersion, ZKNotaryExtension.CONFIG_CIRCUIT_FILE)
            copier.copyZincCircuitStates(getCircuitStates(circuitStates, configurator.circuitConfiguration.circuit.states))
            copier.copyZincPlatformSources(getPlatformSources(root))
            copier.copyZincPlatformSources(getPlatformLibs(root))

            val consts = joinConstFiles(outputPath, getPlatformSourcesPath(root))

            // Render templates
            val templateRenderer = TemplateRenderer(outputPath.toPath()) {
                getTemplateContents(root, it.templateFile)
            }

            val txStatesTemplateConfigurations = configurator.circuitConfiguration.circuit.states.map { state ->
                // Existence of the required states is ensured during the copying.
                TxStateTemplateParameters(state)
            }.flatMap { it.resolveAllConfigurations() }.distinct()

            (templateConfigurations.resolveAllTemplateParameters() + txStatesTemplateConfigurations)
                .forEach(templateRenderer::renderTemplate)

            // Generate code
            val codeGenerator = CodeGenerator(outputPath)
            getTemplateContents(root, "merkle_template.zn").also { codeGenerator.generateMerkleUtilsCode(it, consts) }
            getTemplateContents(root, "main_template.zn").also { codeGenerator.generateMainCode(it, consts) }

            // Replace placeholders in Merkle tree functions
            val replacer = MerkleReplacer(outputPath)
            replacer.setCorrespondingMerkleTreeFunctionForComponentGroups(consts)
            replacer.setCorrespondingMerkleTreeFunctionForMainTree()
        }

    // Render templates for test circuits
    val testTemplateRenderer =
        TemplateRenderer(getPlatformSourcesTestSourcesPath(root).toPath()) { getTemplateContents(root, it.templateFile) }

    templateConfigurations.resolveAllTemplateParameters().forEach(testTemplateRenderer::renderTemplate)
}

private fun getPlatformSourcesPath(root: String): File {
    return File("$root/src/main/resources/zinc-platform-sources")
}

private fun getCircuitStates(circuitStates: File, states: List<CircuitConfigurator.State>): List<File> {
    return states.map { state ->
        val module = circuitStates.resolve(state.location)
        require(module.exists()) { "Expected ${module.absolutePath}" }
        module
    }
}

private fun getPlatformSources(root: String): Array<File>? {
    return File("$root/src/main/resources/zinc-platform-sources").listFiles()
}

private fun getPlatformLibs(root: String): Array<File>? {
    return File("$root/src/main/resources/zinc-platform-libraries").listFiles()
}

private fun getPlatformSourcesTestSourcesPath(root: String): File {
    return File("$root/build/zinc-platform-test-sources")
}

private fun getTemplateContents(root: String, templateName: String) =
    runCatching { File("$root/src/main/resources/zinc-platform-templates").listFiles() ?: error("Templates must be accessible") }
        .mapCatching { templates -> templates.single { it.name == templateName } ?: error("Multiple templates for $templateName found") }
        .map { it.readText() }
        .getOrThrow()
