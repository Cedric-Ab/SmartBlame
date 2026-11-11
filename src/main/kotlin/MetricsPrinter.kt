import common.FileCategories
import common.FileCategory
import java.nio.file.Path
import kotlin.io.path.extension

class MetricsPrinter(
    val metrics: Metrics,
    val fileCategories: FileCategories,
) : MetricsPresenter {
    constructor(
        history: RepositoryHistory,
        fileCategories: FileCategories
    ) : this(Metrics(history, fileCategories), fileCategories)

    val divider = "----------------------------------------------------------------------------------------------------"
    val thickDivider = divider.replace('-', '#')

    fun introduceNewStatistic(title: String) {
        println()
        println(thickDivider)
        println()
        println(title)
        println()
    }

    fun author() {
        fileCategories.categories
            .filter { !it.key.name.contains("ignore") }
            .keys
            .forEach(::author)
    }

    fun author(category: FileCategory) {
        val authors = metrics.history.authors.authorsByMail.values
        val allFiles = metrics.history.getAllFiles()

        val exampleAcceptedFiles = allFiles
            .filter { fileCategories.categoryOf(it) == category }
            .take(5)
        println(thickDivider)
        println("${category.name}: ${category.description} ${exampleAcceptedFiles.map { it.fileName }}")
        println(thickDivider)
        metrics.fileCategory = category
        authors.forEach(::author)
        metrics.fileCategory = null
    }

    fun commits() {
        val authors = metrics.history.authors.authorsByMail.values
        val longestName = authors.maxOfOrNull { it.name.length }

        introduceNewStatistic("Displaying amount of commits per author")

        authors
            .associateWith(metrics::numCommitsFromAuthor)
            .entries
            .sortedByDescending { it.value }
            .forEach { (author, numCommits) ->
                println("%-${longestName}s has %d commits".format(author.name, numCommits))
            }
    }

    private fun toNumFilesAndLines(origins: List<LineOrigin>): Pair<Int, Int> {
        return origins.map { it.originAdd?.file ?: it.remove?.file }.distinct().size to origins.size
    }

    private fun toNumFiles(lines: List<LineChange>): Int {
        return lines.map { it.file }.distinct().size
    }

    private fun List<LineOrigin>.toContentFromOrigin() =
        this.mapNotNull { it.originAdd?.content ?: it.remove?.content }

    private fun List<LineChange>.toContent() =
        this.map { it.content }

    fun author(author: Author) {
        println(divider)
        println("Statistics for ${author.name}")
        println(divider)
        val addedCodeSurvival = metrics.authorAddedCodeSurvival(author)

        println(
            "Added a total of %s".format(linesToString(addedCodeSurvival.values.flatten().toContentFromOrigin())) +
                    if (addedCodeSurvival.values.sumOf { it.size } == 0) "" else ", of which")

        addedCodeSurvival[null]?.let { lineOrigins ->
            if (lineOrigins.isNotEmpty()) {
                println(
                    "%s still exist ".format(linesToStringShort(lineOrigins.toContentFromOrigin()))
                )
            }
        }
        addedCodeSurvival
            .entries
            .sortedByDescending { it.value.size }
            .forEach { (author, n) ->
                if (n.isNotEmpty() && author != null) {
                    println("%s were deleted by %s".format(linesToStringShort(n.toContentFromOrigin()), author.name))
                }
            }


        val deletedCode = metrics.authorDeletedCode(author)
        println(
            "Deleted a total of %s".format(linesToStringShort(deletedCode.values.flatten().toContentFromOrigin())) +
                    if (addedCodeSurvival.values.sumOf { it.size } == 0) "" else ", of which")
        deletedCode
            .entries
            .sortedByDescending { it.value.size }
            .forEach { (author, n) ->
                if (n.isNotEmpty()) {
                    println(
                        "%s were added by %s".format(
                            linesToStringShort(n.toContentFromOrigin()), author?.name ?: "unknown"
                        )
                    )
                }
            }

        println()
    }

    fun authorDetailed(author: Author, fileCategory: FileCategory) {
        metrics.fileCategory = fileCategory

        println(divider)
        println("Detailed Statistics for ${author.name}")
        println(divider)


        val addedCodeSurvival = metrics.linesCreatedByAuthor(author).mapNotNull { it.originAdd }
        if (addedCodeSurvival.isEmpty()) {
            println("Added a total of %d lines in %d files".format(0, 0))
        } else {
            println("Added a total of %s, of which".format(linesToString(addedCodeSurvival.toContent())))
        }
        printFileRankingByLines(addedCodeSurvival.toContent())


        val deletedCode = metrics.linesCreatedByAuthor(author).mapNotNull { it.remove }
        if (deletedCode.isEmpty()) {
            println("Deleted a total of %d lines in %d files".format(0, 0))
        } else {
            println("Deleted a total of %s, of which".format(linesToString(deletedCode.toContent())))
        }
        printFileRankingByLines(deletedCode.toContent())

        println()

        metrics.fileCategory = null
    }

    private fun printFileRankingByLines(lines: List<LineContent>) {
        lines.groupBy { it.file }
            .entries
            .sortedByDescending { it.value.size }
            .forEach {
                println(
                    "%s in %s"
                        .format(linesToString(it.value), it.key)
                        .replace("ch\\zhaw\\it\\pm2\\mkdirprobleme\\easygrade", "...")
                )
            }
    }

    private fun linesToString(lines: List<LineContent>, shortened: Boolean = false): String {
        val l = lines.filter { it.file.extension == "java" || it.file.extension == "kt" }

        if (l.isEmpty()) return "No lines"

        val numLines = l.size
        val numFiles = l.map { it.file }.distinct().size
        val numCodeLines = l.filter { it.isCode }.size
        val numLinesWhitespace = l.filter { it.isWhitespace }.size + l.filter { it.isComment }.size
        val numLinesImports = l.filter { it.isImport }.size
        val numLinesJavadoc = l.filter { it.isJavadoc }.size
        val numLinesBraces = l.filter { it.isBrace }.size

        val formatString =
            if (shortened) "%4d lines in %2d files (%3d:%3d,%3d,%3d,%3d)"
            else "%4d lines in %2d files of which %4d are actually code " +
                    "(whitespaces: %4d imports: %4d javadoc: %4d braces: %4d)"

        return formatString.format(
            numLines,
            numFiles,
            numCodeLines,
            numLinesWhitespace,
            numLinesImports,
            numLinesJavadoc,
            numLinesBraces
        )
    }

    private fun linesToStringShort(lines: List<LineContent>): String {
        return linesToString(lines, true)
    }

    companion object {
        fun aggregate(repoDir: Path, fileCategories: FileCategories): MetricsPrinter {
            val (commits, authors) = mineCommits(repoDir)
            val renames = getAllRenames(repoDir)
            val lineChanges = getAllLineChanges(repoDir, commits)
            val lineOrigins = matchLineChanges(lineChanges, renames)

            val history = RepositoryHistory(authors, commits, renames, lineChanges, lineOrigins)

            return MetricsPrinter(history, fileCategories)
        }

    }
}
