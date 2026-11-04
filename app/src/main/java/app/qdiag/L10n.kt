package app.qdiag

import app.qdiag.core.Text
import app.qdiag.data.Language

/**
 * The app speaks French (the default) or English, picked in Settings. Each piece of UI text is
 * written in both languages where it's used: `tr("Read codes", "Lire les codes")`. Changing the
 * language recreates the activity, so everything on screen is read again.
 */
fun tr(en: String, fr: String): String = if (isFrench) fr else en

val isFrench: Boolean get() = Graph.settings.current.language == Language.FRENCH

/** Core's bilingual text in the app's language. */
val Text.local: String get() = get(isFrench)
