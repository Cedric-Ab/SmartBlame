package data

data class Author(
    val name: String,
    val mail: String
) {
    companion object {
        fun List<Author>.get(mail: String?): Author? {
            if (mail == null) return null
            return this.firstOrNull { it.mail == mail }
        }

        fun List<Author>.get(commit: Commit?): Author? {
            return get(commit?.authorEmail)
        }
    }
}
