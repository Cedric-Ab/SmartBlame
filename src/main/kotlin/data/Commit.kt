package data

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class Commits(val commitsByHash: Map<String, Commit>) {
    constructor(commits: List<Commit>) : this(commits.associateBy { it.commitHash })

    fun get(hash: String?): Commit? {
        if (hash == null) return null
        return commitsByHash[hash]
    }
}

data class Commit @OptIn(ExperimentalTime::class) constructor(
    val commitHash: String,
    val commitSequenceNumber: Long,
    val parentCommitHashes: List<String>,
    val authorEmail: String,
    val timestamp: Instant,
    val message: String
)
