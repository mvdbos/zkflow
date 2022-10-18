package com.ing.zkflow.zinc.poet.generate.structure

import com.ing.zkflow.annotations.ZKP
import com.ing.zkflow.annotations.ZKPSurrogate
import com.ing.zkflow.common.serialization.KClassSerializerProvider
import com.ing.zkflow.serialization.getSerialDescriptor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.corda.core.internal.readText
import net.corda.core.internal.writeText
import java.nio.file.Path
import java.nio.file.Paths
import java.util.ServiceLoader

@Serializable
data class ZkpStructure(
    @SerialName("__comment__")
    val comment: String = "!!!DO NOT MODIFY!!! This file is generated by `$GENERATE_ZKP_STRUCTURE`. " +
        "This file contains a generated baseline of the serialization structure for all @${ZKP::class.simpleName} " +
        "and @${ZKPSurrogate::class.simpleName} classes to detect and prevent backwards incompatible changes.",
    val structure: List<ZkpStructureType>,
)

fun generateZkpStructure(): ZkpStructure {
    val kClassSerializerProviders = ServiceLoader.load(KClassSerializerProvider::class.java)
    val structureTypes = kClassSerializerProviders
        .asSequence()
        .map { it.get().klass }
        .flatMap { ZkpStructureGenerator.generate(it.getSerialDescriptor()).toFlattenedClassStructure() }
        .distinct()
        .toList()
    return ZkpStructure(
        structure = structureTypes
    )
}

fun readSavedStructure(structureFile: Path): ZkpStructure =
    json.decodeFromString(ZkpStructure.serializer(), structureFile.readText())

fun writeGeneratedStructure(structureFile: Path, newStructure: ZkpStructure) {
    val jsonString = json.encodeToString(ZkpStructure.serializer(), newStructure)
    structureFile.writeText(jsonString)
}

fun verifyZkpStructure(savedStructure: ZkpStructure, newStructure: ZkpStructure) {
    // Detect incompatible changes
    val incompatibleChanges = compare(savedStructure.structure, newStructure.structure)
    if (incompatibleChanges.isNotEmpty()) {
        error(
            incompatibleChanges.joinToString(
                separator = "\n",
                prefix = "Backwards incompatible changes detected.\n"
            ) { it.toString() }
        )
    }
}

const val ZKP_STRUCTURE_JSON = "src/main/zkp/structure.json"
const val VERIFY_ZKP_STRUCTURE = "verifyZkpStructure"
const val GENERATE_ZKP_STRUCTURE = "generateZkpStructure"

private val json = Json {
    encodeDefaults = true // otherwise `comment` is not serialized
    prettyPrint = true
    prettyPrintIndent = "  "
}
internal val structureFile: Path = Paths.get(ZKP_STRUCTURE_JSON)
