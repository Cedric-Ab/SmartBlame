import java.nio.file.Path

fun matchLineChanges(
    lineChanges: List<LineChange>,
    renames: List<RenameEvent>
): Set<LineOrigin> {
    val lineOrigins = resolveLineOrigins(lineChanges, renames).toMutableSet()

    lineOrigins.addAll(
        lineChanges
            .filter { it.type == ChangeType.ADD }
            .filter { lineChange ->
                !lineOrigins.any { lineOrigin -> lineOrigin.originAdd == lineChange }
            }
            .map { LineOrigin(it, null) }
    )

    return lineOrigins
}


fun resolveLineOrigins(
    lineChanges: List<LineChange>,
    renames: List<RenameEvent>
): List<LineOrigin> {
    val alternateFileNames = renames
        .groupBy { it.oldPath }
        .mapValues { (k, v) ->
            v.map { it.newPath }
        }

    val uf = UnionFind<Path>()

    for (r in renames) {
        uf.union(r.oldPath, r.newPath)
    }

    val addsByContent = mutableMapOf<Pair<Path, String>, MutableList<LineChange>>()

    val removes = mutableListOf<LineChange>()

    // 1. Split + index ADDs
    for (change in lineChanges) {
        when (change.type) {
            ChangeType.ADD -> {
                addsByContent
                    .getOrPut(uf.find(change.file) to change.content.content) { mutableListOf() }
                    .add(change)
            }

            ChangeType.REMOVE -> {
                removes.add(change)
            }
        }
    }

    // 2. Ensure deterministic order (critical)
    for ((_, list) in addsByContent) {
        list.sortWith(compareBy({ it.commitIndex }, { it.newLineNumber ?: Int.MAX_VALUE }))
    }

    // 3. Resolve REMOVE → original ADD
    val result = mutableListOf<LineOrigin>()

    for (remove in removes) {
        result.add(resolveRemove(remove, addsByContent, uf))
    }

    return result
}

fun resolveRemove(
    remove: LineChange,
    addsByContent: MutableMap<Pair<Path, String>, MutableList<LineChange>>,
    uf: UnionFind<Path>,
): LineOrigin {
    val candidates = addsByContent[uf.find(remove.file) to remove.content.content].orEmpty()

    val origin = candidates
        .lastOrNull { add ->
            add.commitIndex < remove.commitIndex
        }

    return LineOrigin(
        remove = remove,
        originAdd = origin
    )
}

class UnionFind<T> {
    private val parent = mutableMapOf<T, T>()

    fun find(x: T): T {
        parent.putIfAbsent(x, x)
        if (parent[x] != x) {
            parent[x] = find(parent[x]!!)
        }
        return parent[x]!!
    }

    fun union(a: T, b: T) {
        val pa = find(a)
        val pb = find(b)
        if (pa != pb) parent[pa] = pb
    }
}
