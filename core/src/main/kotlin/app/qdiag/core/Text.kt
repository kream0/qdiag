package app.qdiag.core

/** A piece of user-facing text in both of the app's languages (the app picks one). */
data class Text(val en: String, val fr: String) {
    fun get(french: Boolean): String = if (french) fr else en
}
