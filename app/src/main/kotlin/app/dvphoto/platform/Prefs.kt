package app.dvphoto.platform

import android.content.Context
import android.content.SharedPreferences

/** Small settings store. Everything stays on the device. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.applicationContext.getSharedPreferences("dvphoto", Context.MODE_PRIVATE)

    /** BCP-47 tag of the chosen language, or "" to follow the system. */
    var language: String
        get() = sp.getString("language", "") ?: ""
        set(v) { sp.edit().putString("language", v).apply() }

    var autoCapture: Boolean
        get() = sp.getBoolean("autoCapture", true)
        set(v) { sp.edit().putBoolean("autoCapture", v).apply() }

    var voice: Boolean
        get() = sp.getBoolean("voice", false)
        set(v) { sp.edit().putBoolean("voice", v).apply() }

    var measurements: Boolean
        get() = sp.getBoolean("measurements", false)
        set(v) { sp.edit().putBoolean("measurements", v).apply() }

    /** "GALLERY" or "DOWNLOADS". */
    var folder: String
        get() = sp.getString("folder", "GALLERY") ?: "GALLERY"
        set(v) { sp.edit().putString("folder", v).apply() }

    /** [app.dvphoto.core.Strictness.id] of the chosen level; "" until the user picks one. */
    var strictness: String
        get() = sp.getString("strictness", "") ?: ""
        set(v) { sp.edit().putString("strictness", v).apply() }

    var preferFront: Boolean
        get() = sp.getBoolean("preferFront", true)
        set(v) { sp.edit().putBoolean("preferFront", v).apply() }
}
