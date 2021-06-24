package com.ing.zknotary.gradle.zinc.util

import java.io.File
import java.nio.ByteBuffer

class ZincSourcesCopier(private val outputPath: File) {
    fun copyZincCircuitSources(circuitSources: File, circuitName: String, version: String, configFileName: String) {
        circuitSources
            .listFiles()
            ?.forEach { file ->
                if (file.name == configFileName) {
                    val copy = outputPath.parentFile.resolve(file.name)
                    file.copyTo(copy, overwrite = true)
                } else {
                    val copy = outputPath.resolve(file.name)
                    file.copyTo(copy, overwrite = true)
                }
            }

        createOutputFile(outputPath.parentFile.resolve("Zargo.toml")).apply {
            if (!exists()) createNewFile()
            outputStream().channel
                .truncate(0)
                .write(
                    ByteBuffer.wrap(
                        """
    [circuit]
    name = "$circuitName"
    version = "$version"                                
                        """.trimIndent().toByteArray()
                    )
                )
        }
    }

    fun copyZincCircuitStates(circuitStates: List<File>) {
        circuitStates.forEach { file ->
            file.copyTo(
                createOutputFile(outputPath).resolve(file.name),
                overwrite = true
            )
        }
    }

    fun copyZincPlatformSources(platformSources: Array<File>?) {
        platformSources?.forEach { file ->
            file.copyTo(
                createOutputFile(outputPath).resolve(file.name),
                overwrite = true
            )
        }
    }

    fun copyZincCircuitSourcesForTests(circuitSources: File) {
        circuitSources.listFiles { _, name -> name != "main.zn" }?.forEach {
            it.copyTo(
                createOutputFile(outputPath).resolve(it.name),
                overwrite = true
            )
        }
    }

    private fun createOutputFile(targetFile: File): File {
        targetFile.parentFile?.mkdirs()
        targetFile.delete()
        targetFile.createNewFile()
        return targetFile
    }
}
