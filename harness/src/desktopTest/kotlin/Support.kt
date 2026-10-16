import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.LocalStringTable
import androidx.compose.ui.res.MissingStrings
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.dvphoto.ui.DvTheme
import app.dvphoto.ui.Pal
import app.dvphoto.ui.ScreenFrame
import org.jetbrains.skia.EncodedImageFormat
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

object Harness {
    val resDir: File = File(System.getProperty("res.dir") ?: error("res.dir not set"))
    val shotsDir: File = File(System.getProperty("shots.dir") ?: "build/screenshots").also { it.mkdirs() }

    /** Android string-resource unescaping: \' \" \n \\ \@ \? and \uXXXX; surrounding quotes are dropped. */
    fun unescape(raw: String): String {
        var s = raw.trim()
        if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length - 1)
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'u' -> { sb.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                    else -> sb.append(n)
                }
                i += 2
            } else { sb.append(c); i++ }
        }
        // Resources.getString() would run String.format only when args are given; "%%" is for those.
        return sb.toString()
    }

    fun parse(file: File): Map<String, String> {
        if (!file.exists()) return emptyMap()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate {
            val e = nodes.item(it) as Element
            e.getAttribute("name") to unescape(e.textContent)
        }
    }

    /** Default strings overlaid with the translation folder [qualifier] (e.g. "ru"), like Android does. */
    fun strings(qualifier: String?): Map<String, String> {
        val base = parse(File(resDir, "values/strings.xml"))
        if (qualifier == null) return base
        return base + parse(File(resDir, "values-$qualifier/strings.xml"))
    }

    /** A flat, recognisable stand-in for a portrait photo: white wall, shoulders, head with hair, eyes. */
    fun portrait(size: Int = 600, headTop: Float = 0.105f, chin: Float = 0.705f, eyeY: Float = 0.375f, cx: Float = 0.5f): ImageBitmap {
        val bmp = ImageBitmap(size, size)
        val c = Canvas(bmp)
        fun paint(color: Color) = Paint().apply { this.color = color; style = PaintingStyle.Fill; isAntiAlias = true }
        val s = size.toFloat()
        c.drawRect(0f, 0f, s, s, paint(Color(0xFFF1F1EE)))
        // shoulders
        c.drawRoundRect(s * 0.06f, s * 0.80f, s * 0.94f, s * 1.3f, s * 0.2f, s * 0.2f, paint(Color(0xFF3B4A63)))
        // neck
        c.drawRect(s * (cx - 0.07f), s * (chin - 0.04f), s * (cx + 0.07f), s * 0.82f, paint(Color(0xFFD9A07F)))
        // hair + head
        val headW = (chin - headTop) * 0.72f * 0.98f
        c.drawOval(s * (cx - headW / 2), s * headTop, s * (cx + headW / 2), s * chin, paint(Color(0xFF2A211C)))
        c.drawOval(s * (cx - headW * 0.45f), s * (headTop + (chin - headTop) * 0.2f), s * (cx + headW * 0.45f), s * chin, paint(Color(0xFFE2AE8D)))
        // eyes + mouth
        val ex = headW * 0.22f
        for (dx in listOf(-ex, ex)) {
            c.drawOval(s * (cx + dx - 0.022f), s * (eyeY - 0.008f), s * (cx + dx + 0.022f), s * (eyeY + 0.012f), paint(Color.White))
            c.drawOval(s * (cx + dx - 0.010f), s * (eyeY - 0.006f), s * (cx + dx + 0.010f), s * (eyeY + 0.010f), paint(Color(0xFF2A1B12)))
        }
        c.drawRoundRect(s * (cx - 0.05f), s * (chin - 0.085f), s * (cx + 0.05f), s * (chin - 0.07f), 12f, 12f, paint(Color(0xFF9A4B45)))
        return bmp
    }

    @OptIn(ExperimentalComposeUiApi::class)
    fun render(
        name: String,
        lang: String? = null,
        widthDp: Int = 412,
        heightDp: Int = 892,
        density: Float = 2f,
        rtl: Boolean = false,
        content: @Composable () -> Unit,
    ): File {
        val table = strings(lang)
        MissingStrings.clear()
        val scene = ImageComposeScene(width = (widthDp * density).toInt(), height = (heightDp * density).toInt(), density = Density(density)) {
            CompositionLocalProvider(
                LocalStringTable provides table,
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                DvTheme { ScreenFrame { content() } }
            }
        }
        // let animations settle (spring/tween) before the final frame
        var t = 0L
        repeat(40) { scene.render(t); t += 50_000_000L }
        val image = scene.render(t)
        val bytes = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
        scene.close()
        val suffix = lang?.let { "_$it" } ?: ""
        val file = File(shotsDir, "$name$suffix.png")
        file.writeBytes(bytes)
        check(MissingStrings.isEmpty()) { "$name$suffix: missing strings ${MissingStrings.toList()}" }
        return file
    }

    /** Fraction of pixels that differ from the dominant background colour: a crude "something was drawn" test. */
    fun inkRatio(png: File): Double {
        val img = javax.imageio.ImageIO.read(png)
        val bg = img.getRGB(2, 2)
        var diff = 0L
        val step = 3
        var n = 0L
        for (y in 0 until img.height step step) for (x in 0 until img.width step step) {
            n++
            if (img.getRGB(x, y) != bg) diff++
        }
        return diff.toDouble() / n
    }
}
