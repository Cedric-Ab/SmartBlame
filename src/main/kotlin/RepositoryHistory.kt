@file:OptIn(ExperimentalTime::class)

import java.nio.file.Path
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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

data class Authors(val authorsByMail: Map<String, Author>) {
    constructor(authors: List<Author>) : this(authors.associateBy { it.mail })

    fun get(mail: String?): Author? {
        if (mail == null) return null
        return authorsByMail[mail]
    }

    fun get(commit: Commit?): Author? {
        return get(commit?.authorEmail)
    }
}

data class Author(
    val name: String,
    val mail: String
)

data class Commits(val commitsByHash: Map<String, Commit>) {
    constructor(commits: List<Commit>) : this(commits.associateBy { it.commitHash })

    fun get(hash: String?): Commit? {
        if (hash == null) return null
        return commitsByHash[hash]
    }
}

data class Commit(
    val commitHash: String,
    val commitSequenceNumber: Long,
    val parentCommitHashes: List<String>,
    val authorEmail: String,
    val timestamp: Instant,
    val message: String
)

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
