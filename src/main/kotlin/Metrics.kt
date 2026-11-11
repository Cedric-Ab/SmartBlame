import common.FileCategories
import common.FileCategory
import data.*
import java.nio.file.Path

class Metrics(
    val history: RepositoryHistory,
    val fileCategories: FileCategories,
) {
    val lineChanges
        get() = history.lineChanges
            .filter { lineChange ->
                fileFilter(lineChange.file)
            }
    val lineOrigins
        get() = history.lineOrigins
            .filter { lineOrigin ->
                fileFilter(lineOrigin.originAdd?.file) || fileFilter(lineOrigin.remove?.file)
            }

    var fileCategory: FileCategory? = null

    private fun fileFilter(path: Path?): Boolean {
        return path != null && (fileCategory == null || fileCategories.categoryOf(path) == fileCategory)
    }

    fun String?.toCommit() = history.getAuthorFromCommit(this)

    fun totalChurn(): Int =
        lineChanges.size

    fun insertDeleteRatio(): Map<ChangeType, Int> =
        lineChanges.groupingBy { it.type }.eachCount()

    fun netGrowth(): Map<Path, Int> {
        return lineChanges.groupBy { it.file }
            .mapValues { (_, change) ->
                change.count { it.type == ChangeType.ADD } - change.count { it.type == ChangeType.ADD }
            }
    }

    fun fileChurn(): Map<Path, Int> =
        lineChanges.groupBy { it.file }
            .mapValues { it.value.size }

    fun fileCommitSpread(): Map<Path, Int> =
        lineChanges.groupBy { it.file }
            .mapValues { it.value.map { e -> e.commitHash }.toSet().size }

    fun commitSize(): Map<String, Int> =
        lineChanges.groupBy { it.commitHash }
            .mapValues { it.value.size }

    fun commitBalance(): Map<String, Double> =
        lineChanges.groupBy { it.commitHash }
            .mapValues { (_, evs) ->
                val ins = evs.count { it.type == ChangeType.ADD }
                val del = evs.count { it.type == ChangeType.REMOVE }

                if (del == 0) ins.toDouble() else ins.toDouble() / del
            }

    fun fileActivityOverTime(): Map<Path, List<String>> =
        lineChanges.groupBy { it.file }
            .mapValues { it.value.map { e -> e.commitHash } }

    fun fileCommitFrequency(): Map<Path, Int> =
        lineChanges.groupBy { it.file }
            .mapValues { it.value.map { e -> e.commitHash }.toSet().size }

    fun hotFiles(topN: Int = 10): List<Path> =
        lineChanges.groupBy { it.file }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(topN)
            .map { it.key }

    fun authorChurn(): Map<String?, Int> =
        lineChanges.groupBy { it.commitHash.toCommit()?.mail }
            .mapValues { it.value.size }

    fun commitsFromAuthor(author: Author): List<Commit> =
        history.commits.commitsByHash.values
            .filter { it.authorEmail == author.mail }

    fun numCommitsFromAuthor(author: Author): Int = commitsFromAuthor(author).size

    fun authorAverageCommitSize(): Map<String?, Double> {
        return commitSize().entries.groupBy { it.key.toCommit() }
            .mapValues { (_, v) ->
                v.map { it.value }.average()
            }
            .mapKeys { (k, _) -> k?.name }
    }

    fun linesCreatedByAuthor(author: Author): List<LineOrigin> {
        return lineOrigins
            .filter { (originAdd, _) -> originAdd != null }
            .filter { (originAdd, _) ->
                originAdd?.commitHash.toCommit() == author
            }
    }

    fun linesDeletedByAuthor(author: Author): List<LineOrigin> {
        return lineOrigins
            .filter { (_, delete) -> delete != null }
            .filter { (_, delete) ->
                delete?.commitHash.toCommit() == author
            }
    }

    fun authorAddedCodeSurvival(author: Author): Map<Author?, List<LineOrigin>> {
        return linesCreatedByAuthor(author)
            .groupBy { it.remove?.commitHash.toCommit() }
    }

    fun authorDeletedCode(author: Author): Map<Author?, List<LineOrigin>> {
        return linesDeletedByAuthor(author)
            .groupBy { it.originAdd?.commitHash.toCommit() }
    }
}
