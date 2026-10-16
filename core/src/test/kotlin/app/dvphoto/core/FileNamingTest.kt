package app.dvphoto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamingTest {
    @Test fun keepsGoodNames() {
        assertEquals("DV_Applicant", FileNaming.sanitize("DV_Applicant"))
        assertEquals("John Smith 2026", FileNaming.sanitize("  John   Smith 2026 "))
        assertEquals("photo (1)", FileNaming.sanitize("photo (1)"))
    }

    @Test fun removesTypedExtension() {
        assertEquals("name", FileNaming.sanitize("name.jpg"))
        assertEquals("name", FileNaming.sanitize("NAME.JPEG".lowercase()))
        assertEquals("name", FileNaming.sanitize("name.JPG"))
        assertEquals("name.v2", FileNaming.sanitize("name.v2.jpg"))
    }

    @Test fun neutralisesPathAndSpecialCharacters() {
        assertEquals("a_b_c", FileNaming.sanitize("a/b\\c"))
        assertEquals("a_b", FileNaming.sanitize("a:*?\"<>|b"))
        assertEquals("passwd", FileNaming.sanitize("../../passwd"))
        assertEquals("hidden", FileNaming.sanitize(".hidden"))
        assertEquals("x", FileNaming.sanitize("___x___"))
        assertEquals("", FileNaming.sanitize("////"))
        assertEquals("", FileNaming.sanitize("   "))
        assertEquals("", FileNaming.sanitize(".jpg"))
    }

    @Test fun keepsOtherAlphabets() {
        assertEquals("Камолиддин Расулов", FileNaming.sanitize("Камолиддин Расулов"))
        assertEquals("张伟", FileNaming.sanitize("张伟"))
    }

    @Test fun limitsLengthWithoutBreakingCharacters() {
        val long = "a".repeat(200)
        assertEquals(FileNaming.MAX_BASE_LENGTH, FileNaming.sanitize(long).length)
        val emojiNames = "é".repeat(200)
        assertEquals(FileNaming.MAX_BASE_LENGTH, FileNaming.sanitize(emojiNames).length)
        // emoji are replaced, never cut in half
        val s = FileNaming.sanitize("hi 😀 there")
        assertFalse(s.any { Character.isSurrogate(it) })
        assertEquals("hi _ there", s)
    }

    @Test fun defaultsWhenEmpty() {
        assertEquals("DV_Photo.jpg", FileNaming.fileName(""))
        assertEquals("DV_Photo.jpg", FileNaming.fileName("???"))
        assertEquals("Anna.jpg", FileNaming.fileName("Anna"))
        assertEquals("Anna.jpg", FileNaming.fileName("Anna.jpg"))
    }

    @Test fun uniqueNames() {
        assertEquals("a", FileNaming.unique("a", emptyList()))
        assertEquals("a (1)", FileNaming.unique("a", listOf("a.jpg")))
        assertEquals("a (2)", FileNaming.unique("a", listOf("A.JPG", "a (1).jpg")))
        assertEquals("a", FileNaming.unique("a", listOf("b.jpg", "a.png")))
        assertEquals("DV_Photo", FileNaming.unique("", emptyList()))
    }

    @Test fun transliteratesRussianAndUzbek() {
        assertEquals("Kamoliddin Rasulov", FileNaming.toAscii("Камолиддин Расулов"))
        assertEquals("Shchukin Yuliya", FileNaming.toAscii("Щукин Юлия"))
        assertEquals("Ozbekiston", FileNaming.toAscii("O‘zbekiston"))
        assertEquals("Ozbekiston", FileNaming.toAscii("O'zbekiston".replace("'", "‘")))
        assertEquals("Hozhiev Gofur", FileNaming.toAscii("Ҳожиев Ғофур"))
    }

    @Test fun stripsAccents() {
        assertEquals("Jose Munoz", FileNaming.toAscii("José Muñoz"))
        assertEquals("Francois Dupre", FileNaming.toAscii("François Dupré"))
        assertEquals("Strasse", FileNaming.toAscii("Straße"))
        assertEquals("Istanbul Sisli", FileNaming.toAscii("İstanbul Şişli").replace("Ş", "S").replace("ş", "s"))
    }

    @Test fun asciiSafety() {
        assertTrue(FileNaming.isAsciiSafe("DV_Applicant (1)"))
        assertFalse(FileNaming.isAsciiSafe("Расулов"))
        assertTrue(FileNaming.isAsciiSafe(FileNaming.toAscii("Расулов")))
    }

    @Test fun rolesProduceDistinctSafeNames() {
        val names = PhotoRole.values().map { FileNaming.fileName(it.base) }
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.all { FileNaming.isAsciiSafe(it) && it.endsWith(".jpg") })
    }
}
