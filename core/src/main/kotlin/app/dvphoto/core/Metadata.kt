package app.dvphoto.core

import kotlin.math.min

enum class ImageFormat { JPEG, PNG, WEBP, HEIF, UNKNOWN }

/** Signs found in a photo's metadata that it was filtered, beautified, edited or AI generated. */
enum class EditFlag {
    /** IPTC DigitalSourceType trainedAlgorithmicMedia / compositeWithTrainedAlgorithmicMedia. */
    AI_TRAINED,
    /** IPTC DigitalSourceType algorithmicMedia / compositeSynthetic. */
    ALGORITHMIC_MEDIA,
    /** The software/description names a known AI generator or AI editing feature. */
    AI_TOOL,
    /** The software/description names a beauty, face-retouch or filter app. */
    BEAUTY_APP,
    /** The software/description names a general photo editor. */
    EDITOR_APP,
    /** The file carries C2PA Content Credentials (informational). */
    CREDENTIALS_PRESENT,
    /** Content Credentials record filter / colour / edit actions. */
    CREDENTIALS_EDITED,
    /** Portrait / depth-effect capture (background blur and often skin smoothing). */
    PORTRAIT_MODE,
}

data class MetadataFindings(
    val format: ImageFormat,
    val make: String? = null,
    val model: String? = null,
    val software: String? = null,
    val creatorTool: String? = null,
    val description: String? = null,
    val takenAt: CivilDate? = null,
    val flags: Set<EditFlag> = emptySet(),
    /** Name of the app / tool that caused an AI_TOOL, BEAUTY_APP or EDITOR_APP flag. */
    val matched: String? = null,
    val hasExif: Boolean = false,
    val hasXmp: Boolean = false,
) {
    /** Fills in values the platform EXIF reader found when our own parser could not (HEIC, PNG, WebP). */
    fun withExtra(software: String?, takenAt: CivilDate?): MetadataFindings {
        val mergedSoftware = this.software ?: software
        var out = copy(software = mergedSoftware, takenAt = this.takenAt ?: takenAt)
        if (this.software == null && software != null) {
            val (flag, name) = MetadataScanner.classifyApp(software)
            if (flag != null) out = out.copy(flags = out.flags + flag, matched = out.matched ?: name)
        }
        return out
    }
}

/**
 * Reads EXIF, XMP and C2PA markers from the first bytes of an image file. Pure Kotlin so it can be
 * unit tested on the JVM; it only ever looks at metadata segments, never at pixel data.
 */
object MetadataScanner {
    /** Enough for every JPEG metadata segment in practice; callers pass at most this many bytes. */
    const val MAX_SCAN_BYTES = 1 shl 20

    // --- vocabulary -------------------------------------------------------------------------

    private val aiTools = listOf(
        "midjourney", "dall-e", "dall·e", "stable diffusion", "stablediffusion", "firefly", "generative fill",
        "generative ai", "generative edit", "ai generated", "ai-generated", "aigenerated", "made with google ai",
        "edited with google ai", "galaxy ai", "magic editor", "magic eraser", "photo unblur", "reimagine",
        "chatgpt", "openai", "gemini", "imagen", "nano banana", "synthid", "leonardo.ai", "ideogram",
    )

    private val beautyApps = listOf(
        "beautyplus", "beauty plus", "meitu", "youcam", "facetune", "faceapp", "b612", "camera360", "ulike",
        "wink", "remini", "lensa", "airbrush", "perfect365", "bestie", "snow", "retouch", "beauty",
        "instagram", "snapchat", "tiktok",
    )

    private val editors = listOf(
        "photoshop", "lightroom", "snapseed", "gimp", "pixlr", "picsart", "photoscape", "paint.net", "affinity",
        "capture one", "darktable", "canva", "fotor", "polarr", "vsco", "photodirector", "pixelmator", "luminar",
        "on1", "photopea", "adobe",
    )

    private val credentialEdits = listOf(
        "c2pa.filtered", "c2pa.color_adjustments", "c2pa.edited", "c2pa.drawing", "c2pa.placed", "c2pa.painted",
    )

    fun classifyApp(text: String): Pair<EditFlag?, String?> {
        val t = text.lowercase()
        aiTools.firstOrNull { mentions(t, it) }?.let { return EditFlag.AI_TOOL to it }
        beautyApps.firstOrNull { mentions(t, it) }?.let { return EditFlag.BEAUTY_APP to it }
        editors.firstOrNull { mentions(t, it) }?.let { return EditFlag.EDITOR_APP to it }
        return null to null
    }

    /**
     * True when [token] occurs in the already lower-cased [text]. Short tokens ("b612", "snow", "wink", "gimp"...)
     * must stand alone as a word, otherwise a phone's firmware string such as "G991BXXS2AB612" would look like an app.
     */
    internal fun mentions(text: String, token: String): Boolean {
        var from = 0
        while (true) {
            val i = text.indexOf(token, from)
            if (i < 0) return false
            if (token.length > 6) return true
            val before = if (i == 0) ' ' else text[i - 1]
            val after = if (i + token.length >= text.length) ' ' else text[i + token.length]
            if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
            from = i + 1
        }
    }

    // --- entry point ------------------------------------------------------------------------

    fun scan(bytes: ByteArray, length: Int = bytes.size): MetadataFindings {
        val n = min(length, bytes.size)
        return when {
            n >= 4 && bytes[0].u() == 0xFF && bytes[1].u() == 0xD8 -> scanJpeg(bytes, n)
            n >= 8 && bytes[0].u() == 0x89 && bytes[1].toInt() == 'P'.code && bytes[2].toInt() == 'N'.code ->
                scanGeneric(bytes, n, ImageFormat.PNG)
            n >= 12 && ascii(bytes, 0, 4) == "RIFF" && ascii(bytes, 8, 4) == "WEBP" ->
                scanGeneric(bytes, n, ImageFormat.WEBP)
            n >= 12 && ascii(bytes, 4, 4) == "ftyp" -> scanGeneric(bytes, n, ImageFormat.HEIF)
            else -> scanGeneric(bytes, n, ImageFormat.UNKNOWN)
        }
    }

    // --- JPEG -------------------------------------------------------------------------------

    private class Acc {
        var make: String? = null
        var model: String? = null
        var software: String? = null
        var description: String? = null
        var userComment: String? = null
        var creatorTool: String? = null
        var takenAt: CivilDate? = null
        var sceneCaptureType: Int? = null
        var hasExif = false
        var hasXmp = false
        val xmp = StringBuilder()
        val app11 = StringBuilder()
    }

    private fun scanJpeg(b: ByteArray, n: Int): MetadataFindings {
        val acc = Acc()
        var i = 2
        while (i + 4 <= n) {
            if (b[i].u() != 0xFF) { i++; continue }
            val marker = b[i + 1].u()
            if (marker == 0xFF) { i++; continue }
            if (marker == 0x01 || marker == 0xD8 || marker in 0xD0..0xD7) { i += 2; continue }
            if (marker == 0xD9 || marker == 0xDA) break // EOI / start of scan: pixel data follows
            val segLen = u16(b, i + 2, big = true)
            if (segLen < 2) break
            val start = i + 4
            val end = min(i + 2 + segLen, n)
            if (start <= end) {
                when (marker) {
                    0xE1 -> readApp1(b, start, end, acc)
                    0xEB -> acc.app11.append(latin1(b, start, end))
                }
            }
            i += 2 + segLen
        }
        return build(ImageFormat.JPEG, acc)
    }

    private fun readApp1(b: ByteArray, start: Int, end: Int, acc: Acc) {
        if (end - start >= 6 && ascii(b, start, 4) == "Exif") {
            acc.hasExif = true
            readTiff(b, start + 6, end, acc)
            return
        }
        val xapId = "http://ns.adobe.com/xap/1.0/\u0000"
        val extId = "http://ns.adobe.com/xmp/extension/\u0000"
        when {
            end - start > xapId.length && ascii(b, start, xapId.length) == xapId -> {
                acc.hasXmp = true
                acc.xmp.append(latin1(b, start + xapId.length, end))
            }
            end - start > extId.length && ascii(b, start, extId.length) == extId -> {
                acc.xmp.append(latin1(b, start + extId.length, end))
            }
        }
    }

    // --- EXIF / TIFF ------------------------------------------------------------------------

    private fun readTiff(b: ByteArray, base: Int, end: Int, acc: Acc) {
        if (end - base < 8) return
        val big = when (ascii(b, base, 2)) {
            "MM" -> true
            "II" -> false
            else -> return
        }
        if (u16(b, base + 2, big) != 42) return
        val ifd0 = u32(b, base + 4, big)
        readIfd(b, base, end, ifd0, big, acc, depth = 0)
    }

    private fun readIfd(b: ByteArray, base: Int, end: Int, offset: Long, big: Boolean, acc: Acc, depth: Int) {
        if (depth > 2 || offset < 0 || offset > Int.MAX_VALUE) return
        val ifd = base + offset.toInt()
        if (ifd < base || ifd + 2 > end) return
        val count = min(u16(b, ifd, big), 400)
        for (k in 0 until count) {
            val e = ifd + 2 + 12 * k
            if (e + 12 > end) return
            val tag = u16(b, e, big)
            val type = u16(b, e + 2, big)
            val cnt = u32(b, e + 4, big)
            val size = typeSize(type) * cnt
            if (size < 0 || size > Int.MAX_VALUE) continue
            val loc = if (size <= 4) e + 8 else base + u32(b, e + 8, big).toInt()
            val inBounds = loc >= base && loc + size <= end
            if (depth == 0) {
                when (tag) {
                    0x010F -> if (inBounds && type == 2) acc.make = text(b, loc, size.toInt())
                    0x0110 -> if (inBounds && type == 2) acc.model = text(b, loc, size.toInt())
                    0x0131 -> if (inBounds && type == 2) acc.software = text(b, loc, size.toInt())
                    0x010E -> if (inBounds && type == 2) acc.description = text(b, loc, size.toInt())
                    0x0132 -> if (inBounds && type == 2 && acc.takenAt == null) {
                        acc.takenAt = Dates.parseExifDate(text(b, loc, size.toInt()))
                    }
                    0x8769 -> if (type == 4 && cnt == 1L) {
                        readIfd(b, base, end, u32(b, e + 8, big), big, acc, depth + 1)
                    }
                }
            } else {
                when (tag) {
                    0x9003 -> if (inBounds && type == 2) {
                        Dates.parseExifDate(text(b, loc, size.toInt()))?.let { acc.takenAt = it }
                    }
                    0x9004 -> if (inBounds && type == 2 && acc.takenAt == null) {
                        acc.takenAt = Dates.parseExifDate(text(b, loc, size.toInt()))
                    }
                    0xA406 -> if (type == 3 && cnt == 1L) acc.sceneCaptureType = u16(b, e + 8, big)
                    0x9286 -> if (inBounds && type == 7 && size > 8) {
                        acc.userComment = text(b, loc + 8, size.toInt() - 8)
                    }
                }
            }
        }
    }

    private fun typeSize(type: Int): Long = when (type) {
        1, 2, 6, 7 -> 1
        3, 8 -> 2
        4, 9, 11 -> 4
        5, 10, 12 -> 8
        else -> 1
    }

    // --- non-JPEG containers ----------------------------------------------------------------

    private fun scanGeneric(b: ByteArray, n: Int, format: ImageFormat): MetadataFindings {
        val whole = latin1(b, 0, n).lowercase()
        val flags = LinkedHashSet<EditFlag>()
        var matched: String? = null
        val hasXmp = whole.contains("<x:xmpmeta") || whole.contains("xmpmeta")
        if (whole.contains("trainedalgorithmicmedia")) flags += EditFlag.AI_TRAINED
        else if (whole.contains("algorithmicmedia") || whole.contains("compositesynthetic")) flags += EditFlag.ALGORITHMIC_MEDIA
        if (whole.contains("jumb") && whole.contains("c2pa")) {
            flags += EditFlag.CREDENTIALS_PRESENT
            if (credentialEdits.any { whole.contains(it) }) flags += EditFlag.CREDENTIALS_EDITED
        }
        aiTools.firstOrNull { it.length >= 9 && whole.contains(it) }?.let { flags += EditFlag.AI_TOOL; matched = it }
        return MetadataFindings(format = format, flags = flags, matched = matched, hasXmp = hasXmp)
    }

    // --- classification ---------------------------------------------------------------------

    private fun build(format: ImageFormat, a: Acc): MetadataFindings {
        val xmp = a.xmp.toString()
        val xmpLower = xmp.lowercase()
        a.creatorTool = xmlValue(xmp, xmpLower, "creatortool")
        val app11Lower = a.app11.toString().lowercase()
        val blob = xmpLower + "\u0000" + app11Lower

        val flags = LinkedHashSet<EditFlag>()
        var matched: String? = null

        if (blob.contains("trainedalgorithmicmedia")) flags += EditFlag.AI_TRAINED
        else if (blob.contains("algorithmicmedia") || blob.contains("compositesynthetic")) flags += EditFlag.ALGORITHMIC_MEDIA

        if (app11Lower.contains("c2pa") || app11Lower.contains("jumb")) {
            flags += EditFlag.CREDENTIALS_PRESENT
            if (credentialEdits.any { app11Lower.contains(it) || xmpLower.contains(it) }) flags += EditFlag.CREDENTIALS_EDITED
        }

        // AI tool names may sit in a metadata blob or in the descriptive fields
        val fields = listOfNotNull(a.software, a.creatorTool, a.description, a.userComment)
        for (t in fields + listOf(blob)) {
            val lower = t.lowercase()
            val hit = aiTools.firstOrNull { mentions(lower, it) }
            if (hit != null) { flags += EditFlag.AI_TOOL; matched = matched ?: hit; break }
        }
        // Beauty / editor apps are looked up in descriptive fields only (XMP namespaces mention "adobe" everywhere)
        for (t in fields) {
            val (flag, name) = classifyApp(t)
            if (flag == EditFlag.BEAUTY_APP || flag == EditFlag.EDITOR_APP) {
                flags += flag
                if (matched == null) matched = name
            }
        }
        if (a.sceneCaptureType == 2 || xmpLower.contains("gdepth:") || xmpLower.contains("gcamera:portrait")) {
            flags += EditFlag.PORTRAIT_MODE
        }
        return MetadataFindings(
            format = format, make = a.make, model = a.model, software = a.software, creatorTool = a.creatorTool,
            description = a.description, takenAt = a.takenAt, flags = flags, matched = matched,
            hasExif = a.hasExif, hasXmp = a.hasXmp,
        )
    }

    /** Value of `<ns:Name>value</ns:Name>` or `ns:Name="value"` in an XMP packet (case-insensitive name). */
    private fun xmlValue(xmp: String, lower: String, name: String): String? {
        var from = 0
        while (true) {
            val i = lower.indexOf(name, from)
            if (i < 0) return null
            var j = i + name.length
            while (j < xmp.length && xmp[j] == ' ') j++
            if (j < xmp.length && xmp[j] == '=') {
                j++
                while (j < xmp.length && xmp[j] == ' ') j++
                if (j < xmp.length && (xmp[j] == '"' || xmp[j] == '\'')) {
                    val q = xmp[j]
                    val e = xmp.indexOf(q, j + 1)
                    if (e > j) return xmp.substring(j + 1, e).trim().ifEmpty { null }
                }
            } else if (j < xmp.length && xmp[j] == '>') {
                val e = xmp.indexOf('<', j + 1)
                if (e > j) return xmp.substring(j + 1, e).trim().ifEmpty { null }
            }
            from = i + name.length
        }
    }

    // --- byte helpers -----------------------------------------------------------------------

    private fun Byte.u(): Int = toInt() and 0xFF

    private fun u16(b: ByteArray, o: Int, big: Boolean): Int {
        if (o < 0 || o + 2 > b.size) return 0
        return if (big) (b[o].u() shl 8) or b[o + 1].u() else (b[o + 1].u() shl 8) or b[o].u()
    }

    private fun u32(b: ByteArray, o: Int, big: Boolean): Long {
        if (o < 0 || o + 4 > b.size) return 0
        val x = if (big) {
            (b[o].u().toLong() shl 24) or (b[o + 1].u().toLong() shl 16) or (b[o + 2].u().toLong() shl 8) or b[o + 3].u().toLong()
        } else {
            (b[o + 3].u().toLong() shl 24) or (b[o + 2].u().toLong() shl 16) or (b[o + 1].u().toLong() shl 8) or b[o].u().toLong()
        }
        return x and 0xFFFFFFFFL
    }

    private fun ascii(b: ByteArray, o: Int, len: Int): String {
        if (o < 0 || o + len > b.size) return ""
        val sb = StringBuilder(len)
        for (k in 0 until len) sb.append((b[o + k].u()).toChar())
        return sb.toString()
    }

    private fun latin1(b: ByteArray, from: Int, to: Int): String {
        val sb = StringBuilder(maxOf(0, to - from))
        for (k in from until min(to, b.size)) sb.append(b[k].u().toChar())
        return sb.toString()
    }

    /** NUL/space-trimmed text of an EXIF ASCII field; empty strings become null. */
    private fun text(b: ByteArray, o: Int, len: Int): String? {
        if (len <= 0 || o < 0 || o + len > b.size) return null
        var e = o + len
        while (e > o && (b[e - 1].toInt() == 0 || b[e - 1].toInt() == ' '.code)) e--
        val s = String(b, o, e - o, Charsets.UTF_8).replace("\u0000", "").trim()
        return s.ifEmpty { null }
    }
}
