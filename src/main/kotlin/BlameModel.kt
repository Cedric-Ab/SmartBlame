import java.nio.file.Path

data class BlameCommit(
    val hash: String,
    val authorName: String,
    val authorEmail: String,
    val timestamp: Long,
    val message: String
)

data class BlameLine(
    val lineNumber: Int,
    val content: String,
    val commit: BlameCommit
) {
    val isWhitespace = content.trim().isEmpty()
    val isImport = content.trim().matches(Regex("^(package|import)\\s.*"))
    val isComment = content.trim().matches(Regex("^//.*"))
    val isJavadoc = content.trim().matches(Regex("^(/\\*\\*|\\*/?).*"))
    val isBrace = content.trim().matches(Regex("^[{}()];?.*"))

    val isCode
        get() = !isWhitespace && !isImport && !isComment && !isJavadoc && !isBrace
}

data class BlameFile(
    val path: Path,
    val lines: List<BlameLine>
) {
    constructor(path: String, lines: List<BlameLine>) : this(Path.of(path), lines)
}

data class AuthorStats(
    val author: String,
    val email: String,
    val linesChanged: Int,
    val filesTouched: Set<String>,
    val commits: Set<String>
)

data class BlameReport(
    val files: List<BlameFile>,
    val authors: List<AuthorStats>
)
