import data.ChangeType
import data.Commit
import data.Commit.Companion.get
import data.LineChange
import data.RenameEvent
import org.eclipse.jgit.diff.DiffAlgorithm
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.patch.FileHeader
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.util.io.DisabledOutputStream
import java.nio.file.Path

fun getAllLineChanges(repoDir: Path, commits: List<Commit>): List<LineChange> {
    val repository: Repository = FileRepositoryBuilder()
        .findGitDir(repoDir.toFile())
        .build()

    val result = mutableListOf<LineChange>()

    repository.use { repo ->
        RevWalk(repo).use { walk ->
            val head = repo.resolve("HEAD")
                ?: throw IllegalStateException("Repository has no HEAD (no commits yet or invalid repo state)")
            val start = walk.parseCommit(head)

            walk.markStart(start)

            val reader = repo.newObjectReader()

            DiffFormatter(DisabledOutputStream.INSTANCE).use { formatter ->
                formatter.setRepository(repo)
                formatter.isDetectRenames = true
                formatter.setDiffAlgorithm(
                    DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.HISTOGRAM)
                )

                for (commit in walk) {

                    val parent = if (commit.parentCount > 0) {
                        walk.parseCommit(commit.getParent(0).id)
                    } else {
                        continue
                    }

                    val oldTree = CanonicalTreeParser().apply {
                        reset(reader, parent.tree)
                    }

                    val newTree = CanonicalTreeParser().apply {
                        reset(reader, commit.tree)
                    }

                    val diffs = formatter.scan(oldTree, newTree)

                    for (entry in diffs) {

                        val fileHeader = formatter.toFileHeader(entry)
                        if (fileHeader.patchType == FileHeader.PatchType.BINARY) continue

                        val oldId = entry.oldId.toObjectId()
                        val newId = entry.newId.toObjectId()

                        val oldText = if (oldId != ObjectId.zeroId())
                            RawText(repo.open(oldId).bytes)
                        else null

                        val newText = if (newId != ObjectId.zeroId())
                            RawText(repo.open(newId).bytes)
                        else null

                        for (edit in fileHeader.toEditList()) {

                            // REMOVE
                            if (oldText != null) {
                                for (i in edit.beginA until edit.endA) {
                                    result.add(
                                        LineChange(
                                            commitIndex = commits.get(commit.name)?.commitSequenceNumber ?: error(""),
                                            commitHash = commit.name,
                                            file = Path.of(entry.oldPath),
                                            type = ChangeType.REMOVE,
                                            content = oldText.getString(i),
                                            oldLineNumber = i + 1,
                                            newLineNumber = null
                                        )
                                    )
                                }
                            }

                            // ADD
                            if (newText != null) {
                                for (i in edit.beginB until edit.endB) {
                                    result.add(
                                        LineChange(
                                            commitIndex = commits.get(commit.name)?.commitSequenceNumber ?: error(""),
                                            commitHash = commit.name,
                                            file = Path.of(entry.newPath),
                                            type = ChangeType.ADD,
                                            content = newText.getString(i),
                                            oldLineNumber = null,
                                            newLineNumber = i + 1
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    return result
}

fun getAllRenames(repoDir: Path): List<RenameEvent> {

    val repository = FileRepositoryBuilder()
        .findGitDir(repoDir.toFile())
        .build()

    val result = mutableListOf<RenameEvent>()

    repository.use { repo ->
        RevWalk(repo).use { walk ->

            val head = repo.resolve("HEAD") ?: return emptyList()
            val start = walk.parseCommit(head)

            walk.markStart(start)

            val reader = repo.newObjectReader()

            DiffFormatter(DisabledOutputStream.INSTANCE).use { formatter ->
                formatter.setRepository(repo)
                formatter.isDetectRenames = true

                var index = 0

                for (commit in walk) {

                    if (commit.parentCount == 0) {
                        index++
                        continue
                    }

                    val parent = walk.parseCommit(commit.getParent(0).id)

                    val oldTree = CanonicalTreeParser().apply {
                        reset(reader, parent.tree)
                    }

                    val newTree = CanonicalTreeParser().apply {
                        reset(reader, commit.tree)
                    }

                    val diffs = formatter.scan(oldTree, newTree)

                    for (diff in diffs) {
                        if (diff.changeType == DiffEntry.ChangeType.RENAME) {
                            result.add(
                                RenameEvent(
                                    commitIndex = index,
                                    commitId = commit.name,
                                    oldPath = Path.of(diff.oldPath),
                                    newPath = Path.of(diff.newPath),
                                    score = diff.score
                                )
                            )
                        }
                    }

                    index++
                }
            }
        }
    }

    return result
}
