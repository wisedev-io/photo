package app.dvphoto.core

/** Calendar date without any java.time dependency (the app supports Android 7 without desugaring). */
data class CivilDate(val year: Int, val month: Int, val day: Int) {
    val epochDay: Long get() = Dates.daysFromCivil(year, month, day)
}

object Dates {
    /** Days since 1970-01-01 (proleptic Gregorian). Howard Hinnant's days_from_civil. */
    fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = if (month > 2) month - 3 else month + 9
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era.toLong() * 146097L + doe - 719468L
    }

    /** Parses the date part of an EXIF timestamp such as "2026:04:12 10:22:31"; null when unset or invalid. */
    fun parseExifDate(text: String?): CivilDate? {
        if (text == null || text.length < 10) return null
        val y = text.substring(0, 4).toIntOrNull() ?: return null
        val sep1 = text[4]
        val m = text.substring(5, 7).toIntOrNull() ?: return null
        val sep2 = text[7]
        val d = text.substring(8, 10).toIntOrNull() ?: return null
        if ((sep1 != ':' && sep1 != '-') || (sep2 != ':' && sep2 != '-')) return null
        if (y !in 1990..2200 || m !in 1..12 || d !in 1..31) return null
        return CivilDate(y, m, d)
    }

    fun epochDayOf(epochMillis: Long): Long = Math.floorDiv(epochMillis, 86_400_000L)
}
