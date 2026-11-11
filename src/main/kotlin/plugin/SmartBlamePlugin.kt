package plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class SmartBlamePlugin : Plugin<Project> {

    override fun apply(project: Project) {

        val ext = project.extensions.create(
            "smartBlame",
            SmartBlameExtension::class.java
        )

        project.tasks.register(
            "smartBlame",
            SmartBlameTask::class.java
        ) {
            it.ignoreWhitespace.convention(ext.ignoreWhitespace)
            it.ignoreImports.convention(ext.ignoreImports)
            it.ignoreComments.convention(ext.ignoreComments)
            it.separateJavadoc.convention(ext.separateJavadoc)

            it.sourceDir.set(
                project.layout.projectDirectory.dir("src")
            )
        }
    }
}
