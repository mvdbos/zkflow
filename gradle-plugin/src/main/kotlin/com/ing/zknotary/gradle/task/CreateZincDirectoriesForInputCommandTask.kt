package com.ing.zknotary.gradle.task

import com.ing.zknotary.gradle.util.platformSamples
import com.ing.zknotary.gradle.util.zkNotaryExtension
import org.gradle.api.DefaultTask
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

/**
This task receives the input commandName as command line argument and creates a zinc directory under src/main with the
provided command name. It also copies sample code for consts, contract rules, and contract state that should be manually
implemented on zkdapp side.
 **/
open class CreateZincDirectoriesForInputCommandTask : DefaultTask() {
    private var commandName = "command"

    @Option(option = "command", description = "Set the command type of the transaction.")
    fun setCommand(command: String) {
        commandName = command
    }

    @Input
    fun getCommand(): String {
        return commandName
    }

    @TaskAction
    fun createZincDirectoriesForInputCommand() {
        val extension = project.zkNotaryExtension
        project.copy { copy ->
            copy.into(extension.circuitSourcesBasePath.resolve(commandName))
            copy.from(project.platformSamples).eachFile {
                @Suppress("SpreadOperator")
                it.relativePath = RelativePath(true, *it.relativePath.segments.drop(1).toTypedArray())
            }
            copy.includeEmptyDirs = false
        }
    }
}
