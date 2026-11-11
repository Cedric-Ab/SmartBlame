package data

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
