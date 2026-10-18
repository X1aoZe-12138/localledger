package cn.localledger.ui

import cn.localledger.ui.design.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CompactDateTimeLogicTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test fun clockAcceptsBoundariesAndRejectsInvalidInput() {
        assertEquals(LocalTime.MIDNIGHT, validClock("00", "00"))
        assertEquals(LocalTime.of(8, 5), validClock("08", "05"))
        assertEquals(LocalTime.of(14, 35), validClock("14", "35"))
        assertEquals(LocalTime.of(23, 59), validClock("23", "59"))
        listOf("24" to "00", "25" to "70", "12" to "60", "-1" to "00", "a" to "05", "" to "12")
            .forEach { (h, m) -> assertNull("$h:$m", validClock(h, m)) }
    }

    @Test fun calendarStartsMondayAndHandlesLeapDayMonthAndYear() {
        val leap = calendarDays(YearMonth.of(2024, 2))
        assertEquals(42, leap.size)
        assertEquals(LocalDate.of(2024, 2, 1), leap[3])
        assertTrue(LocalDate.of(2024, 2, 29) in leap)
        assertEquals(28, calendarDays(YearMonth.of(2023, 2)).count { it != null })
        assertEquals(LocalDate.of(2026, 10, 1), calendarDays(YearMonth.of(2026, 10))[3])
        assertEquals(LocalDate.of(2026, 1, 1), calendarDays(YearMonth.of(2026, 1))[3])
        assertEquals(LocalDate.of(2026, 9, 30), calendarDays(YearMonth.of(2026, 9)).filterNotNull().last())
    }

    @Test fun dateAndTimeEditsPreserveUnchangedPartInLocalZone() {
        val original = LocalDate.of(2026, 9, 20).atTime(11, 29, 37).atZone(zone).toInstant().toEpochMilli()
        val changedDate = replaceLocalDate(original, LocalDate.of(2026, 9, 21), zone)
        assertEquals(LocalDateTime.of(2026, 9, 21, 11, 29, 37), Instant.ofEpochMilli(changedDate).atZone(zone).toLocalDateTime())
        val changedTime = replaceLocalTime(original, LocalTime.of(14, 35), zone)
        assertEquals(LocalDateTime.of(2026, 9, 20, 14, 35), Instant.ofEpochMilli(changedTime).atZone(zone).toLocalDateTime())
        assertEquals(LocalDateTime.of(2027, 1, 1, 11, 29, 37), Instant.ofEpochMilli(
            replaceLocalDate(original, LocalDate.of(2027, 1, 1), zone)).atZone(zone).toLocalDateTime())
    }

    @Test fun localDayBoundsIncludeBothDatesAcrossYear() {
        val start = LocalDate.of(2026, 12, 31)
        val end = LocalDate.of(2027, 1, 1)
        val lower = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val upper = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        assertTrue(LocalDate.of(2026, 12, 31).atTime(0, 0).atZone(zone).toInstant().toEpochMilli() in lower until upper)
        assertTrue(LocalDate.of(2027, 1, 1).atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli() in lower until upper)
        assertFalse(upper in lower until upper)
    }
}
