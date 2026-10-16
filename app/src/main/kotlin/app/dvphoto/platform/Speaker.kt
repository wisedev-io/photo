package app.dvphoto.platform

import android.content.Context
import android.speech.tts.TextToSpeech

/** Reads the guidance aloud so people can frame themselves without looking at the screen. */
class Speaker(context: Context, private val localeTag: String) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    @Volatile private var ready = false
    private var lastText = ""
    private var lastAt = 0L

    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) return
        val result = engine.setLanguage(LocaleHelper.effectiveLocale(localeTag))
        ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    /** Speaks [text] unless the same words were spoken moments ago. */
    fun speak(text: String, nowMillis: Long) {
        if (!ready || text.isBlank()) return
        if (text == lastText && nowMillis - lastAt < 6_000) return
        lastText = text
        lastAt = nowMillis
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dvphoto")
    }

    fun shutdown() {
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        ready = false
    }
}
