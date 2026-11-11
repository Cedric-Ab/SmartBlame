package common

import java.nio.file.Path
import kotlin.io.path.extension

class FileCategories(
    categories: List<FileCategory>
) {
    private var _categories: List<FileCategory> = categories
    val categories: List<FileCategory>
        get() = _categories.plus(listOf(FileCategory.other))

    fun categoryOf(path: Path): FileCategory {
        return _categories.firstOrNull { it.applies(path) } ?: FileCategory.other
    }

    fun byName(name: String): FileCategory? {
        return categories.firstOrNull { it.name == name }
    }

    fun byNameOrThrow(name: String): FileCategory {
        return byName(name) ?: throw IllegalArgumentException("File category '$name' not found")
    }
}

data class FileCategory(
    val name: String,
    val description: String,
    internal val applies: ((Path) -> Boolean),
) {
    constructor(name: String, extensions: List<String>) :
            this(name, extensions.joinToString(" / ") { "*.$it" }, { extensions.contains(it.extension) })

    companion object {
        val other = FileCategory("other", "all other files")
        { throw IllegalArgumentException("File category 'other' should not be used for matching files") }
    }
}
