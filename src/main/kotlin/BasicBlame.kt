import org.eclipse.jgit.api.Git
import org.eclipse.jgit.blame.BlameResult
import java.io.File
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

class BasicBlame(
    val sourceDir: Path
) {
    fun basicBlame(repoDir: Path) {
        Git.open(repoDir.toFile()).use { git ->

            val files = collectFiles()

            val fileResults = buildFileBlame(git, repoDir.toFile(), files)

            val report = buildReport(fileResults)

            printSummary(report)
        }
    }

    private fun makePathsPrintable(
        files: List<Path>
    ): Map<Path, String> {
        val dropElementForName = mutableListOf<Boolean>()

        var paths = files
        while (paths.all { file -> file.nameCount > 1 }) {
            val rootDirs = paths
                .mapNotNull { file -> file.subpath(0, 1) }
                .distinct()

            dropElementForName.add(rootDirs.size == 1)
            paths = paths.map { path -> path.subpath(1, path.nameCount) }
        }

        fun getRepresentation(path: Path): String {
            var representation = ""

            var remainingPath = path
            var droppedLast = true
            for (i in 0 until dropElementForName.size) {
                if (dropElementForName[i]) {
                    if (!droppedLast) {
                        representation += "/..."
                        droppedLast = true
                    }
                } else {
                    if (representation != "") {
                        representation += "/"
                    }
                    representation += remainingPath.subpath(0, 1)
                    droppedLast = false
                }
                remainingPath = remainingPath.subpath(1, remainingPath.nameCount)
            }
            representation += remainingPath.invariantSeparatorsPathString
            return representation
        }

        return files.associateWith(::getRepresentation)
    }

    // -----------------------------
    // File collection
    // -----------------------------
    private fun collectFiles() =
        sourceDir.toFile()
            .walkTopDown()
            .filter { it.extension == "java" }
//            .filter { it.toPath().contains(Path.of("ui")) }

    // -----------------------------
    // Blame extraction per file
    // -----------------------------
    private fun buildFileBlame(
        git: Git,
        repoDir: File,
        files: Sequence<File>
    ): List<BlameFile> {

        val results = mutableListOf<BlameFile>()

        for (file in files) {

            val path = file.relativeTo(repoDir).invariantSeparatorsPath

            val blame = runBlame(git, path) ?: continue

            val lines = extractLines(blame)

            results += BlameFile(
                path = path,
                lines = lines
            )
        }

        return results
    }

    // -----------------------------
    // JGit blame call
    // -----------------------------
    private fun runBlame(git: Git, path: String): BlameResult? {
        return git.blame()
            .setFilePath(path)
            .setFollowFileRenames(true)
            .call()
    }

    // -----------------------------
    // Convert BlameResult → domain model
    // -----------------------------
    private fun extractLines(blame: BlameResult): List<BlameLine> {

        val result = blame.resultContents
        val lines = mutableListOf<BlameLine>()

        for (i in 0 until result.size()) {

            val commit = blame.getSourceCommit(i)

            val blameCommit = BlameCommit(
                hash = commit.name,
                authorName = commit.authorIdent.name,
                authorEmail = commit.authorIdent.emailAddress,
                timestamp = commit.authorIdent.whenAsInstant.epochSecond,
                message = commit.fullMessage
            )

            lines += BlameLine(
                lineNumber = i,
                content = result.getString(i),
                commit = blameCommit
            )
        }

        return lines
    }

    // -----------------------------
    // Build final report
    // -----------------------------
    private fun buildReport(files: List<BlameFile>): BlameReport {

        val authors = files
            .flatMap { it.lines }
            .groupBy { it.commit.authorEmail }
            .map { (email, lines) ->

                val first = lines.first().commit

                AuthorStats(
                    author = first.authorName,
                    email = email,
                    linesChanged = lines.size,
                    filesTouched = lines.map { it.commit.hash }.toSet(),
                    commits = lines.map { it.commit.hash }.toSet()
                )
            }

        return BlameReport(
            files = files,
            authors = authors
        )
    }

    // -----------------------------
    // Output
    // -----------------------------
    private fun printSummary(report: BlameReport) {
        printFilesByLength(report)
        statsByAutor(report)
    }

    private fun statsByAutor(report: BlameReport) {
        val longestName = report.authors.map { it.author }.maxOfOrNull { it.length }

        val lines = report.files
            .flatMap { it.lines }

        report.authors
            .sortedByDescending { stats: AuthorStats -> stats.linesChanged }
            .forEach { authorStats ->
                val numFiles = report.files
                    .filter { file -> file.lines.any { it.commit.authorEmail == authorStats.email } }
                    .size
                val authorLines = lines.filter { it.commit.authorEmail == authorStats.email }
                println(
                    "%-${longestName}s: in %2d files: ".format(
                        authorStats.author,
                        numFiles
                    ) + linesToString(authorLines)
                )
            }
        println(
            "%-${longestName}s: in %2d files: ".format(
                "TOTAL",
                report.files.size
            ) + linesToString(report.files.flatMap { it.lines })
        )
    }

    private fun linesToString(lines: List<BlameLine>): String {
        val numLines = lines.size
        val numCodeLines = lines.filter { it.isCode }.size
        val numLinesWhitespace = lines.filter { it.isWhitespace }.size
        val numLinesImports = lines.filter { it.isImport }.size
        val numLinesComments = lines.filter { it.isComment }.size
        val numLinesJavadoc = lines.filter { it.isJavadoc }.size
        val numLinesBraces = lines.filter { it.isBrace }.size
        return "%4d lines of which %4d (%2d%%) are actually code (whitespaces: %4d (%2d%%) imports: %4d (%2d%%) comments: %4d (%2d%%) javadoc: %4d (%2d%%) braces: %4d (%2d%%))".format(
            numLines,
            numCodeLines,
            100 * numCodeLines / numLines,
            numLinesWhitespace,
            100 * numLinesWhitespace / numLines,
            numLinesImports,
            100 * numLinesImports / numLines,
            numLinesComments,
            100 * numLinesComments / numLines,
            numLinesJavadoc,
            100 * numLinesJavadoc / numLines,
            numLinesBraces,
            100 * numLinesBraces / numLines,
        )
    }

    private fun printFilesByLength(report: BlameReport) {
        val pathRepresentations = makePathsPrintable(report.files.map { file -> file.path })
        val longestRepresentation = pathRepresentations.values.maxOfOrNull { it.length } ?: 10
        report.files
            .sortedByDescending { it.lines.size }
            .forEach { file ->
                val numLines = file.lines.size
                val numCodeLines = file.lines.filter { it.isCode }.size
                val percentageOfCode = 100 * numCodeLines / numLines
                println(
                    "%-${longestRepresentation}s: %4d of which %4d (%2d%%) are actually code".format(
                        pathRepresentations[file.path],
                        numLines,
                        numCodeLines,
                        percentageOfCode,
                    )
                )
            }
    }
}
