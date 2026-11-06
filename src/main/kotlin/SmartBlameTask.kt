import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import kotlin.io.path.invariantSeparatorsPathString

abstract class SmartBlameTask : DefaultTask() {
    @get:Input
    abstract val ignoreWhitespace: Property<Boolean>

    @get:Input
    abstract val ignoreImports: Property<Boolean>

    @get:Input
    abstract val ignoreComments: Property<Boolean>

    @get:Input
    abstract val separateJavadoc: Property<Boolean>

    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty

    @TaskAction
    fun run() {
        val repoDir = project.rootDir.toPath()

        val fileCategories = FileCategories(
            mapOf(
                FileCategory("github", "contains github") to { p -> p.startsWith(".github") },
                FileCategory("gradle", "contains gradle") to { p ->
                    p.fileName.invariantSeparatorsPathString.contains("gradle", true)
                },
                FileCategory("java", "*.java") to FileCategory.byExtension(listOf("java")),
                FileCategory("kotlin", "*.kt / *.kts") to FileCategory.byExtension(listOf("kt", "kts")),
                FileCategory("documentation", "*.md / *.puml") to FileCategory.byExtension(listOf("md", "puml")),
                FileCategory("resources", "*.css / *.fxml") to FileCategory.byExtension(listOf("css", "fxml")),
                FileCategory("ignored", "*.svg") to FileCategory.byExtension(listOf("svg")),
            )
        )

        val metricsPrinter = MetricsPrinter.aggregate(repoDir, fileCategories)

//        metricsPrinter.author()
        metricsPrinter.author(FileCategory("java", "*.java"))
//        metricsPrinter.authorDetailed(history.authors.authorsByMail.values.first(), FileCategory("java", "*.java"))

//        metrics.fileCommitSpread()
//            .forEach { (key, value) -> simplePrint("$key: $value") }
//        metrics.authorChurn().forEach { (key, value) -> println("$key: $value") }

        println()

        metricsPrinter.commits()

        println()

        BasicBlame(sourceDir.get().asFile.toPath()).basicBlame(repoDir)
    }
}
