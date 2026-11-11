package data

import java.nio.file.Path

data class RepositoryHistory(
    val authors: Authors,
    val commits: Commits,
    val renames: List<RenameEvent>,
    val lineChanges: List<LineChange>,
    val lineOrigins: Set<LineOrigin>,
) {
    constructor(
        commitMining: Pair<Commits, Authors>,
        renames: List<RenameEvent>,
        lineChanges: List<LineChange>,
        lineOrigins: Set<LineOrigin>,
    ) : this(commitMining.second, commitMining.first, renames, lineChanges, lineOrigins)

    fun getAuthorFromCommit(hash: String?): Author? {
        return authors.get(commits.get(hash)?.authorEmail)
    }

    fun getAllFiles(): Set<Path> =
        lineChanges.map { it.file }.distinct().toSet()
}

enum class ChangeType {
    ADD, REMOVE
}

data class LineOrigin(
    val originAdd: LineChange?,
    val remove: LineChange?
)

data class LineChange(
    val commitIndex: Long,
    val commitHash: String,
    val file: Path,
    val type: ChangeType,
    val content: LineContent,
    val oldLineNumber: Int?,
    val newLineNumber: Int?
) {
    constructor(
        commitIndex: Long,
        commitHash: String,
        file: Path,
        type: ChangeType,
        content: String,
        oldLineNumber: Int?,
        newLineNumber: Int?
    ) : this(commitIndex, commitHash, file, type, LineContent(content, file), oldLineNumber, newLineNumber)
}

data class LineContent(
    val content: String,
    val file: Path,
) {
    val isWhitespace = content.trim().isEmpty()
    val isImport = content.trim().matches(Regex("^(package|import)\\s.*"))
    val isComment = content.trim().matches(Regex("^//.*"))
    val isJavadoc = content.trim().matches(Regex("^(/\\*\\*|\\*/?).*"))
    val isBrace = content.trim().matches(Regex("^[{}()];?.*"))

    val isCode
        get() = !isWhitespace && !isImport && !isComment && !isJavadoc && !isBrace
}

data class RenameEvent(
    val commitIndex: Int,
    val commitId: String,
    val oldPath: Path,
    val newPath: Path,
    val score: Int
)
