package app.dvphoto.core

import app.dvphoto.core.TestJpeg.ExifSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataTest {
    private fun scan(vararg segs: ByteArray) = MetadataScanner.scan(TestJpeg.jpeg(*segs))

    @Test fun readsExifFieldsLittleAndBigEndian() {
        for (big in listOf(false, true)) {
            val f = scan(
                TestJpeg.app1Exif(
                    ExifSpec(
                        make = "Google", model = "Pixel 8", software = "HDR+ 1.0", description = "hello",
                        dateTime = "2026:09:01 08:00:00", dateTimeOriginal = "2026:08:30 12:34:56", bigEndian = big,
                    )
                )
            )
            assertEquals("big=$big", ImageFormat.JPEG, f.format)
            assertEquals("Google", f.make)
            assertEquals("Pixel 8", f.model)
            assertEquals("HDR+ 1.0", f.software)
            assertEquals("hello", f.description)
            // DateTimeOriginal wins over DateTime
            assertEquals(CivilDate(2026, 8, 30), f.takenAt)
            assertTrue(f.hasExif)
            assertTrue(f.flags.isEmpty())
        }
    }

    @Test fun dateTimeIsUsedWhenOriginalMissing() {
        val f = scan(TestJpeg.app1Exif(ExifSpec(dateTime = "2026:01:02 03:04:05")))
        assertEquals(CivilDate(2026, 1, 2), f.takenAt)
    }

    @Test fun unsetDatesAreIgnored() {
        val f = scan(TestJpeg.app1Exif(ExifSpec(dateTimeOriginal = "0000:00:00 00:00:00")))
        assertNull(f.takenAt)
    }

    @Test fun shortStringsStoredInlineAreRead() {
        val f = scan(TestJpeg.app1Exif(ExifSpec(software = "ab", make = "XY")))
        assertEquals("ab", f.software)
        assertEquals("XY", f.make)
    }

    @Test fun detectsBeautyAppInSoftware() {
        val f = scan(TestJpeg.app1Exif(ExifSpec(software = "BeautyPlus 7.1")))
        assertTrue(EditFlag.BEAUTY_APP in f.flags)
        assertEquals("beautyplus", f.matched)
        assertEquals(Status.FAIL, Provenance.editing(f).status)
        assertEquals(Hint.EDIT_APP, Provenance.editing(f).hint)
    }

    @Test fun editorsWarnButDoNotFail() {
        val f = scan(TestJpeg.app1Exif(ExifSpec(software = "Adobe Photoshop Lightroom Classic 13.0")))
        assertTrue(EditFlag.EDITOR_APP in f.flags)
        assertEquals(Status.WARN, Provenance.editing(f).status)
        assertEquals(Hint.EDIT_SOFTWARE, Provenance.editing(f).hint)
    }

    @Test fun aiToolNameFailsTheCheck() {
        val f = scan(TestJpeg.app1Exif(ExifSpec(software = "Midjourney V6")))
        assertTrue(EditFlag.AI_TOOL in f.flags)
        assertEquals(Hint.EDIT_AI, Provenance.editing(f).hint)
        assertEquals(Status.FAIL, Provenance.editing(f).status)
    }

    @Test fun xmpDigitalSourceTypeIsAnAiMarker() {
        val xml = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF><rdf:Description xmlns:Iptc4xmpExt="http://iptc.org/std/Iptc4xmpExt/2008-02-29/"
            Iptc4xmpExt:DigitalSourceType="http://cv.iptc.org/newscodes/digitalsourcetype/compositeWithTrainedAlgorithmicMedia"
            xmp:CreatorTool="Some Tool"/></rdf:RDF></x:xmpmeta>"""
        val f = scan(TestJpeg.app1Xmp(xml))
        assertTrue(EditFlag.AI_TRAINED in f.flags)
        assertTrue(f.hasXmp)
        assertEquals("Some Tool", f.creatorTool)
        assertEquals(Status.FAIL, Provenance.editing(f).status)
    }

    @Test fun plainAlgorithmicMediaIsAlsoFlagged() {
        val f = scan(TestJpeg.app1Xmp("<x:xmpmeta>digitalsourcetype/algorithmicMedia</x:xmpmeta>"))
        assertTrue(EditFlag.ALGORITHMIC_MEDIA in f.flags)
        assertEquals(Hint.EDIT_AI, Provenance.editing(f).hint)
    }

    @Test fun xmpNamespacesDoNotLookLikeEditors() {
        val xml = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:photoshop="http://ns.adobe.com/photoshop/1.0/"/></x:xmpmeta>"""
        val f = scan(TestJpeg.app1Xmp(xml))
        assertFalse(EditFlag.EDITOR_APP in f.flags)
        assertEquals(Status.PASS, Provenance.editing(f).status)
    }

    @Test fun creatorToolElementFormIsRead() {
        val f = scan(TestJpeg.app1Xmp("<x:xmpmeta><xmp:CreatorTool>Snapseed 2.0</xmp:CreatorTool></x:xmpmeta>"))
        assertEquals("Snapseed 2.0", f.creatorTool)
        assertTrue(EditFlag.EDITOR_APP in f.flags)
    }

    @Test fun c2paInApp11() {
        val plain = scan(TestJpeg.app11("JP\u0000\u0000\u0000\u0001jumb....c2pa.created c2pa.cropped"))
        assertTrue(EditFlag.CREDENTIALS_PRESENT in plain.flags)
        assertFalse(EditFlag.CREDENTIALS_EDITED in plain.flags)
        assertEquals(Status.PASS, Provenance.editing(plain).status)

        val edited = scan(TestJpeg.app11("JP\u0000\u0000jumb c2pa.actions c2pa.filtered"))
        assertTrue(EditFlag.CREDENTIALS_EDITED in edited.flags)
        assertEquals(Hint.EDIT_CREDENTIALS, Provenance.editing(edited).hint)
        assertEquals(Status.WARN, Provenance.editing(edited).status)

        val ai = scan(TestJpeg.app11("jumb c2pa.actions digitalSourceType http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia"))
        assertTrue(EditFlag.AI_TRAINED in ai.flags)
        assertEquals(Status.FAIL, Provenance.editing(ai).status)
    }

    @Test fun c2paManifestSplitAcrossSegmentsIsReassembled() {
        val f = scan(TestJpeg.app11("jumb c2pa.actions digitalSourceType http://cv.iptc.org/newscodes/digitalsource"), TestJpeg.app11("type/trainedAlgorithmicMedia"))
        assertTrue(EditFlag.AI_TRAINED in f.flags)
    }

    @Test fun portraitModeIsAWarning() {
        val f = scan(TestJpeg.app1Exif(ExifSpec(sceneCaptureType = 2)))
        assertTrue(EditFlag.PORTRAIT_MODE in f.flags)
        assertEquals(Hint.EDIT_PORTRAIT, Provenance.editing(f).hint)
        assertEquals(Status.WARN, Provenance.editing(f).status)
        val standard = scan(TestJpeg.app1Exif(ExifSpec(sceneCaptureType = 0)))
        assertFalse(EditFlag.PORTRAIT_MODE in standard.flags)
    }

    @Test fun depthXmpIsPortraitMode() {
        val f = scan(TestJpeg.app1Xmp("<x:xmpmeta xmlns:GDepth=\"http://ns.google.com/photos/1.0/depthmap/\"><GDepth:Format>RangeInverse</GDepth:Format></x:xmpmeta>"))
        assertTrue(EditFlag.PORTRAIT_MODE in f.flags)
    }

    @Test fun userCommentIsSearchedToo() {
        val f = scan(TestJpeg.app1Exif(ExifSpec(userComment = "Edited with Galaxy AI")))
        assertTrue(EditFlag.AI_TOOL in f.flags)
    }

    @Test fun pixelDataIsNeverScanned() {
        // The decoy text sits after the start-of-scan marker and must be ignored.
        val f = MetadataScanner.scan(TestJpeg.jpeg(TestJpeg.app1Exif(ExifSpec(make = "Canon")), pixelDecoy = "trainedAlgorithmicMedia Photoshop BeautyPlus"))
        assertTrue(f.flags.isEmpty())
        assertEquals("Canon", f.make)
    }

    @Test fun cleanPhoneJpegPasses() {
        val f = scan(TestJpeg.app1Exif(ExifSpec(make = "samsung", model = "SM-S921B", software = "S921BXXU2AXH1", dateTimeOriginal = "2026:10:01 09:00:00")))
        assertTrue(f.flags.isEmpty())
        assertEquals(Status.PASS, Provenance.editing(f).status)
    }

    @Test fun shortAppNamesMustBeWholeWords() {
        // firmware / build strings that merely contain an app-like fragment are NOT filter apps
        for (fw in listOf("G991BXXS2AB612", "S901BXXU4CWINK1", "SM-A546E_SNOWY", "HUAWEI-ULIKELY-1.0", "13.0.0.AB612CN")) {
            val f = scan(TestJpeg.app1Exif(ExifSpec(software = fw)))
            assertTrue("$fw -> ${f.flags}", f.flags.isEmpty())
        }
        // ...while the real apps still are
        for (app in listOf("B612 Camera 12.1", "Snow - AI Profile", "Wink 3.0", "GIMP 2.10", "Ulike")) {
            val f = scan(TestJpeg.app1Exif(ExifSpec(software = app)))
            assertTrue("$app -> ${f.flags}", f.flags.isNotEmpty())
        }
    }

    @Test fun realisticHonestPhotosAreNotFlagged() {
        // Pixel-style Ultra HDR XMP (gain map + container directory), standard scene type
        val pixelXmp = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
            <rdf:Description xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/" hdrgm:Version="1.0"
              xmlns:Container="http://ns.google.com/photos/1.0/container/" xmlns:Item="http://ns.google.com/photos/1.0/container/item/">
              <Container:Directory><rdf:Seq><rdf:li><Container:Item Item:Semantic="Primary" Item:Mime="image/jpeg"/></rdf:li>
              <rdf:li><Container:Item Item:Semantic="GainMap" Item:Mime="image/jpeg" Item:Length="48321"/></rdf:li></rdf:Seq></Container:Directory>
            </rdf:Description></rdf:RDF></x:xmpmeta>"""
        val pixel = scan(
            TestJpeg.app1Exif(ExifSpec(make = "Google", model = "Pixel 9", software = "HDR+ 1.0.71", dateTimeOriginal = "2026:09:20 14:00:00", sceneCaptureType = 0)),
            TestJpeg.app1Xmp(pixelXmp),
        )
        assertTrue(pixel.flags.toString(), pixel.flags.isEmpty())
        assertEquals(Status.PASS, Provenance.editing(pixel).status)

        val iphone = scan(TestJpeg.app1Exif(ExifSpec(make = "Apple", model = "iPhone 15", software = "17.6.1", dateTimeOriginal = "2026:08:02 09:41:00", sceneCaptureType = 0)))
        assertTrue(iphone.flags.isEmpty())

        val samsung = scan(TestJpeg.app1Exif(ExifSpec(make = "samsung", model = "SM-S928B", software = "S928BXXU3AXI5", dateTimeOriginal = "2026:09:01 18:30:00")))
        assertTrue(samsung.flags.isEmpty())

        // a camera that records Content Credentials (capture only) is fine
        val c2pa = scan(TestJpeg.app11("JP\u0000\u0000jumb c2pa.created c2pa.actions c2pa.opened"))
        assertEquals(Status.PASS, Provenance.editing(c2pa).status)
    }

    @Test fun garbageAndTruncatedInputNeverCrash() {
        val rnd = java.util.Random(7)
        repeat(300) {
            val b = ByteArray(rnd.nextInt(400))
            rnd.nextBytes(b)
            if (b.size > 2 && it % 2 == 0) { b[0] = 0xFF.toByte(); b[1] = 0xD8.toByte() }
            MetadataScanner.scan(b)
        }
        val good = TestJpeg.jpeg(TestJpeg.app1Exif(ExifSpec(make = "Canon", software = "x", dateTimeOriginal = "2026:01:01 00:00:00")))
        for (cut in 0..good.size) MetadataScanner.scan(good, cut)
        MetadataScanner.scan(ByteArray(0))
    }

    @Test fun nonJpegContainersAreScannedForMarkers() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A) +
            "....iTXtXML:com.adobe.xmp<x:xmpmeta>http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia</x:xmpmeta>".toByteArray()
        val f = MetadataScanner.scan(png)
        assertEquals(ImageFormat.PNG, f.format)
        assertTrue(EditFlag.AI_TRAINED in f.flags)

        val heic = ByteArray(4) + "ftypheic".toByteArray() + ByteArray(64)
        assertEquals(ImageFormat.HEIF, MetadataScanner.scan(heic).format)
        assertTrue(MetadataScanner.scan(heic).flags.isEmpty())
    }

    @Test fun extraExifFillsGapsForOtherFormats() {
        val base = MetadataFindings(ImageFormat.HEIF)
        val merged = base.withExtra("FaceApp 4.0", CivilDate(2026, 5, 5))
        assertEquals(CivilDate(2026, 5, 5), merged.takenAt)
        assertTrue(EditFlag.BEAUTY_APP in merged.flags)
        // does not override what the scanner already found
        val have = MetadataFindings(ImageFormat.JPEG, software = "x", takenAt = CivilDate(2026, 1, 1))
        assertEquals(CivilDate(2026, 1, 1), have.withExtra("Photoshop", CivilDate(2020, 1, 1)).takenAt)
    }

    // --- photo age -------------------------------------------------------------------------

    private val today = CivilDate(2026, 10, 16).epochDay

    private fun ageOf(d: CivilDate?) = Provenance.age(MetadataFindings(ImageFormat.JPEG, takenAt = d), today)

    @Test fun photoAgeRules() {
        assertEquals(Status.PASS, ageOf(CivilDate(2026, 10, 16)).status)
        assertEquals(Status.PASS, ageOf(CivilDate(2026, 4, 20)).status) // ~5.9 months
        val old = ageOf(CivilDate(2026, 3, 1))
        assertEquals(Status.FAIL, old.status)
        assertEquals(Hint.AGE_OLD, old.hint)
        assertEquals(7.5f, old.value!!, 0.6f)
        assertEquals(Status.FAIL, ageOf(CivilDate(2024, 1, 1)).status)
        assertEquals(Hint.AGE_UNKNOWN, ageOf(null).hint)
        assertEquals(Status.WARN, ageOf(null).status)
        assertEquals(Hint.AGE_FUTURE, ageOf(CivilDate(2026, 12, 1)).hint)
        // a few hours of clock/timezone skew is not "future"
        assertEquals(Status.PASS, ageOf(CivilDate(2026, 10, 17)).status)
    }

    @Test fun evaluateReturnsBothChecks() {
        val checks = Provenance.evaluate(MetadataFindings(ImageFormat.JPEG), today)
        assertEquals(listOf(CheckId.EDITING, CheckId.PHOTO_AGE), checks.map { it.id })
    }

    @Test fun datesMatchJavaTime() {
        for ((y, m, d) in listOf(Triple(1970, 1, 1), Triple(2000, 2, 29), Triple(2024, 12, 31), Triple(2026, 10, 16), Triple(2100, 3, 1))) {
            assertEquals("$y-$m-$d", java.time.LocalDate.of(y, m, d).toEpochDay(), Dates.daysFromCivil(y, m, d))
        }
        assertEquals(CivilDate(2026, 4, 12), Dates.parseExifDate("2026:04:12 10:22:31"))
        assertEquals(CivilDate(2026, 4, 12), Dates.parseExifDate("2026-04-12T10:22:31"))
        assertNull(Dates.parseExifDate("0000:00:00 00:00:00"))
        assertNull(Dates.parseExifDate("20260412"))
        assertNull(Dates.parseExifDate(null))
        assertNotNull(Dates.epochDayOf(System.currentTimeMillis()))
        assertEquals(-1L, Dates.epochDayOf(-1L))
    }
}
