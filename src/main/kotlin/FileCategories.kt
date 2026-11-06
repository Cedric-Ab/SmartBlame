import java.nio.file.Path
import kotlin.io.path.extension

class FileCategories {
    val categories: Map<FileCategory, (Path) -> Boolean>

    constructor(categories: Map<FileCategory, (Path) -> Boolean> = mapOf()) {
        this.categories = categories + mapOf(FileCategory.other to { _ -> true })
    }

    fun categoryOf(path: Path): FileCategory {
        return categories
            .filter { it.value(path) }
            .map { it.key }
            .first()
    }
}

data class FileCategory(
    val name: String,
    val description: String,
) {
    companion object {
        fun byExtension(extensions: List<String>) =
            { p: Path -> extensions.contains(p.extension) }

        val other = FileCategory("other", "all other files")
    }
}
