plugins {
    id("com.cosminpolifronie.gradle.plantuml") version "1.6.0"
}

plantUml {
    val diagrams = fileTree(projectDir) { include("**/*.puml") }.toList()

    diagrams.map { it.relativeTo(projectDir) }.forEach { file ->
        render(object {
            val input = file.path
            val output = if (file.parent != null) file.parent + "/" else "" + file.nameWithoutExtension + ".svg"
            val format = "svg"
            val withMetadata = true
        })
    }
}

