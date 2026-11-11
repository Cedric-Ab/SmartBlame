package data

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class Commit @OptIn(ExperimentalTime::class) constructor(
    val commitHash: String,
    val commitSequenceNumber: Long,
    val parentCommitHashes: List<String>,
    val authorEmail: String,
    val timestamp: Instant,
    val message: String
) {
    companion object {
        fun List<Commit>.get(hash: String?): Commit? {
            if (hash == null) return null
            return this.find { it.commitHash == hash }
        }
    }
}
