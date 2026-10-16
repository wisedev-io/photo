package app.dvphoto.platform

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** In-app language choice that works on every Android version (no AppCompat needed). */
object LocaleHelper {
    /** Language tags the app is translated into; "" means "follow the system". */
    val tags: List<String> = listOf("", "en", "ru", "uz", "es", "fr", "pt", "tr", "ar", "bn")

    /** Names written in their own language, so people can always find theirs. */
    fun nativeName(tag: String): String? = when (tag) {
        "en" -> "English"
        "ru" -> "Русский"
        "uz" -> "Oʻzbekcha"
        "es" -> "Español"
        "fr" -> "Français"
        "pt" -> "Português"
        "tr" -> "Türkçe"
        "ar" -> "العربية"
        "bn" -> "বাংলা"
        else -> null
    }

    fun wrap(base: Context, tag: String): Context {
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** Locale used for speech and formatting. */
    fun effectiveLocale(tag: String): Locale = if (tag.isEmpty()) Locale.getDefault() else Locale.forLanguageTag(tag)
}
