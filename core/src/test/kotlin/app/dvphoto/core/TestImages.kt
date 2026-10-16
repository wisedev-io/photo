package app.dvphoto.core

import java.io.ByteArrayOutputStream

/** Builds tiny synthetic JPEG headers (no pixel data) to exercise the metadata scanner. */
object TestJpeg {
    class ExifSpec(
        val make: String? = null,
        val model: String? = null,
        val software: String? = null,
        val description: String? = null,
        val dateTime: String? = null,
        val dateTimeOriginal: String? = null,
        val sceneCaptureType: Int? = null,
        val userComment: String? = null,
        val bigEndian: Boolean = false,
    )

    private fun ByteArrayOutputStream.u16(v: Int, big: Boolean) {
        if (big) { write(v shr 8 and 0xFF); write(v and 0xFF) } else { write(v and 0xFF); write(v shr 8 and 0xFF) }
    }

    private fun ByteArrayOutputStream.u32(v: Long, big: Boolean) {
        if (big) { for (s in intArrayOf(24, 16, 8, 0)) write((v shr s and 0xFF).toInt()) }
        else { for (s in intArrayOf(0, 8, 16, 24)) write((v shr s and 0xFF).toInt()) }
    }

    private class Entry(val tag: Int, val type: Int, val count: Int, val data: ByteArray)

    private fun ascii(s: String) = (s + "\u0000").toByteArray(Charsets.ISO_8859_1)

    /** Writes an IFD (entries sorted by tag) followed by its out-of-line data; returns bytes given the IFD's own offset. */
    private fun ifd(entries: List<Entry>, startOffset: Int, big: Boolean, pointerValues: Map<Int, Long> = emptyMap()): ByteArray {
        val sorted = entries.sortedBy { it.tag }
        val headerLen = 2 + 12 * sorted.size + 4
        val out = ByteArrayOutputStream()
        val dataArea = ByteArrayOutputStream()
        out.u16(sorted.size, big)
        for (e in sorted) {
            out.u16(e.tag, big); out.u16(e.type, big); out.u32(e.count.toLong(), big)
            val ptr = pointerValues[e.tag]
            when {
                ptr != null -> out.u32(ptr, big)
                e.data.size <= 4 -> { out.write(e.data); repeat(4 - e.data.size) { out.write(0) } }
                else -> {
                    out.u32((startOffset + headerLen + dataArea.size()).toLong(), big)
                    dataArea.write(e.data)
                    if (dataArea.size() % 2 == 1) dataArea.write(0)
                }
            }
        }
        out.u32(0, big)
        out.write(dataArea.toByteArray())
        return out.toByteArray()
    }

    fun exifPayload(spec: ExifSpec): ByteArray {
        val big = spec.bigEndian
        val ifd0 = ArrayList<Entry>()
        spec.make?.let { ifd0 += Entry(0x010F, 2, it.length + 1, ascii(it)) }
        spec.model?.let { ifd0 += Entry(0x0110, 2, it.length + 1, ascii(it)) }
        spec.software?.let { ifd0 += Entry(0x0131, 2, it.length + 1, ascii(it)) }
        spec.description?.let { ifd0 += Entry(0x010E, 2, it.length + 1, ascii(it)) }
        spec.dateTime?.let { ifd0 += Entry(0x0132, 2, it.length + 1, ascii(it)) }

        val exifEntries = ArrayList<Entry>()
        spec.dateTimeOriginal?.let { exifEntries += Entry(0x9003, 2, it.length + 1, ascii(it)) }
        spec.sceneCaptureType?.let {
            val d = ByteArrayOutputStream().also { o -> o.u16(it, big) }.toByteArray()
            exifEntries += Entry(0xA406, 3, 1, d)
        }
        spec.userComment?.let {
            val d = "ASCII\u0000\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + it.toByteArray(Charsets.ISO_8859_1)
            exifEntries += Entry(0x9286, 7, d.size, d)
        }
        val hasExifIfd = exifEntries.isNotEmpty()
        if (hasExifIfd) ifd0 += Entry(0x8769, 4, 1, ByteArray(4))

        // two passes: sizes do not depend on pointer values
        val probe = ifd(ifd0, 8, big, mapOf(0x8769 to 0L))
        val exifOffset = 8L + probe.size
        val ifd0Bytes = ifd(ifd0, 8, big, mapOf(0x8769 to exifOffset))
        val exifBytes = if (hasExifIfd) ifd(exifEntries, exifOffset.toInt(), big) else ByteArray(0)

        val tiff = ByteArrayOutputStream()
        tiff.write((if (big) "MM" else "II").toByteArray())
        tiff.u16(42, big)
        tiff.u32(8, big)
        tiff.write(ifd0Bytes)
        tiff.write(exifBytes)
        return "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + tiff.toByteArray()
    }

    fun xmpPayload(xml: String): ByteArray =
        "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.ISO_8859_1) + xml.toByteArray(Charsets.UTF_8)

    fun segment(marker: Int, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0xFF); out.write(marker)
        val len = payload.size + 2
        out.write(len shr 8 and 0xFF); out.write(len and 0xFF)
        out.write(payload)
        return out.toByteArray()
    }

    /** SOI + [segments] + a fake "pixel data" run that contains marker-like text which must NOT be scanned. */
    fun jpeg(vararg segments: ByteArray, pixelDecoy: String = "trainedAlgorithmicMedia Photoshop"): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0xFF); out.write(0xD8)
        for (s in segments) out.write(s)
        out.write(segment(0xDA, byteArrayOf(0, 3, 1, 0, 0, 0)))
        out.write(pixelDecoy.toByteArray())
        out.write(0xFF); out.write(0xD9)
        return out.toByteArray()
    }

    fun app1Exif(spec: ExifSpec) = segment(0xE1, exifPayload(spec))
    fun app1Xmp(xml: String) = segment(0xE1, xmpPayload(xml))
    fun app11(text: String) = segment(0xEB, text.toByteArray(Charsets.ISO_8859_1))
}
