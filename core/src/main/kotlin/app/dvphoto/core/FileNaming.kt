package app.dvphoto.core

import java.text.Normalizer

/** Who a photo is for: DV entries need one photo for the applicant, the spouse and each child. */
enum class PhotoRole(val base: String) {
    APPLICANT("DV_Applicant"),
    SPOUSE("DV_Spouse"),
    CHILD1("DV_Child1"),
    CHILD2("DV_Child2"),
    CHILD3("DV_Child3"),
    CHILD4("DV_Child4"),
    OTHER("DV_Photo"),
}

object FileNaming {
    const val EXTENSION = "jpg"
    const val DEFAULT_BASE = "DV_Photo"
    const val MAX_BASE_LENGTH = 80

    private fun isSeparator(c: Char) = c == ' ' || c == '_' || c == '-' || c == '.'

    /**
     * Makes [input] safe as a file name on every storage backend. Returns the base name without extension;
     * letters and digits of any alphabet are kept, path separators and other symbols become underscores.
     */
    fun sanitize(input: String): String {
        var s = input.trim()
        val lower = s.lowercase()
        for (ext in listOf(".jpeg", ".jpg")) {
            if (lower.endsWith(ext)) { s = s.substring(0, s.length - ext.length); break }
        }
        val sb = StringBuilder()
        var i = 0
        var prevSpace = false
        while (i < s.length) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            val keep = Character.isLetterOrDigit(cp) || cp == '-'.code || cp == '_'.code || cp == '.'.code ||
                cp == '('.code || cp == ')'.code
            when {
                cp == ' '.code || cp == '\t'.code -> {
                    if (!prevSpace) sb.append(' ')
                    prevSpace = true
                }
                keep -> { sb.appendCodePoint(cp); prevSpace = false }
                else -> { if (sb.isEmpty() || sb[sb.length - 1] != '_') sb.append('_'); prevSpace = false }
            }
        }
        var out = sb.toString()
        while (out.contains("..")) out = out.replace("..", ".")
        out = out.trim { isSeparator(it) }
        if (out.codePointCount(0, out.length) > MAX_BASE_LENGTH) {
            out = out.substring(0, out.offsetByCodePoints(0, MAX_BASE_LENGTH)).trim { isSeparator(it) }
        }
        return out
    }

    /** Sanitised base name, or [DEFAULT_BASE] when nothing usable is left. */
    fun baseOrDefault(input: String): String = sanitize(input).ifEmpty { DEFAULT_BASE }

    fun fileName(base: String): String = "${baseOrDefault(base)}.$EXTENSION"

    /** True when the name only has English letters, digits and . _ - ( ) space - the safest set for web uploads. */
    fun isAsciiSafe(name: String): Boolean = name.all { it.code < 128 }

    /**
     * The first role whose default file name is not taken yet: applicant, then spouse, then each child.
     * Gives the natural order when a family saves several photos in a row; [PhotoRole.OTHER] when all are used.
     */
    fun nextRole(existing: Collection<String>): PhotoRole {
        val taken = existing.map { it.lowercase() }.toHashSet()
        return PhotoRole.values().firstOrNull { fileName(it.base).lowercase() !in taken } ?: PhotoRole.OTHER
    }

    /** Picks "name", "name (1)", "name (2)"... so that it does not clash with [existing] file names. */
    fun unique(base: String, existing: Collection<String>): String {
        val taken = existing.map { it.lowercase() }.toHashSet()
        val b = baseOrDefault(base)
        if ("$b.$EXTENSION".lowercase() !in taken) return b
        for (n in 1..999) {
            val candidate = "$b ($n)"
            if ("$candidate.$EXTENSION".lowercase() !in taken) return candidate
        }
        return "$b (${System.currentTimeMillis()})"
    }

    /** Best-effort transliteration to English letters (Cyrillic, accents) so the name is safe for the DV website. */
    fun toAscii(input: String): String {
        val sb = StringBuilder()
        for (ch in input) {
            val t = cyrillic[ch]
            if (t != null) { sb.append(t); continue }
            when (ch) {
                '‘', '’', 'ʻ', 'ʼ', '`', '´' -> Unit // O‘zbek -> Ozbek
                'ß' -> sb.append("ss")
                'æ' -> sb.append("ae")
                'Æ' -> sb.append("AE")
                'œ' -> sb.append("oe")
                'ø' -> sb.append('o')
                'Ø' -> sb.append('O')
                'ı' -> sb.append('i')
                'İ' -> sb.append('I')
                'ə' -> sb.append('a')
                'Ə' -> sb.append('A')
                'ł' -> sb.append('l')
                'Ł' -> sb.append('L')
                'đ' -> sb.append('d')
                'Đ' -> sb.append('D')
                else -> sb.append(ch)
            }
        }
        val decomposed = Normalizer.normalize(sb.toString(), Normalizer.Form.NFD)
        val stripped = StringBuilder()
        for (ch in decomposed) {
            val type = Character.getType(ch)
            if (type == Character.NON_SPACING_MARK.toInt()) continue
            stripped.append(ch)
        }
        return sanitize(stripped.toString().filter { it.code < 128 || Character.isLetterOrDigit(it).not() })
    }

    private val cyrillic: Map<Char, String> = buildMap {
        val pairs = listOf(
            "а" to "a", "б" to "b", "в" to "v", "г" to "g", "д" to "d", "е" to "e", "ё" to "yo", "ж" to "zh",
            "з" to "z", "и" to "i", "й" to "y", "к" to "k", "л" to "l", "м" to "m", "н" to "n", "о" to "o",
            "п" to "p", "р" to "r", "с" to "s", "т" to "t", "у" to "u", "ф" to "f", "х" to "kh", "ц" to "ts",
            "ч" to "ch", "ш" to "sh", "щ" to "shch", "ъ" to "", "ы" to "y", "ь" to "", "э" to "e", "ю" to "yu",
            "я" to "ya", "ў" to "o", "қ" to "q", "ғ" to "g", "ҳ" to "h", "ә" to "a", "ө" to "o", "ү" to "u",
            "ң" to "n", "ұ" to "u", "і" to "i", "є" to "ye", "ї" to "yi", "ґ" to "g",
        )
        for ((c, l) in pairs) {
            put(c[0], l)
            put(c[0].uppercaseChar(), l.replaceFirstChar { it.uppercaseChar() })
        }
    }
}
