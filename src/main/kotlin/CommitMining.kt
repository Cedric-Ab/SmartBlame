@file:OptIn(ExperimentalTime::class)

import org.eclipse.jgit.api.Git
import java.nio.file.Path
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun mineCommits(repoDir: Path): Pair<Commits, Authors> {

    val authorsByEmail = mutableMapOf<String, Author>()
    val commits = mutableListOf<Commit>()

    Git.open(repoDir.toFile()).use { git ->

        val log = git.log().call().toList().reversed()

        var seq = 0L

        for (rev in log) {

            val authorIdent = rev.authorIdent
            val email = authorIdent.emailAddress

            val author = authorsByEmail.getOrPut(email) {
                Author(
                    name = authorIdent.name,
                    mail = email
                )
            }

            val commit = Commit(
                commitHash = rev.name,
                commitSequenceNumber = seq++,
                parentCommitHashes = rev.parents.map { it.name },
                authorEmail = email,
                timestamp = Instant.fromEpochMilliseconds(
                    rev.commitTime.toLong() * 1000
                ),
                message = rev.fullMessage
            )

            commits += commit
        }
    }

    return Commits(commits) to Authors(authorsByEmail)
}
