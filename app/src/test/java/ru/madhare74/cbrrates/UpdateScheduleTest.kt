package ru.madhare74.cbrrates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.madhare74.cbrrates.data.CbrXmlParser
import ru.madhare74.cbrrates.data.RatesData
import ru.madhare74.cbrrates.widget.UpdateSchedule
import ru.madhare74.cbrrates.widget.WidgetModels
import java.time.ZoneId
import java.time.ZonedDateTime

class UpdateScheduleTest {

    private val zone = ZoneId.of("Asia/Yekaterinburg")
    private fun at(day: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, zone)
    private fun millis(day: Int, h: Int, m: Int = 0) = at(day, h, m).toInstant().toEpochMilli()

    @Test
    fun `next slot`() {
        assertEquals(at(28, 10), UpdateSchedule.next(at(28, 7, 43)))
        assertEquals(at(28, 16), UpdateSchedule.next(at(28, 10, 0)))
        assertEquals(at(28, 16), UpdateSchedule.next(at(28, 12)))
        assertEquals(at(29, 10), UpdateSchedule.next(at(28, 16)))
        assertEquals(at(29, 10), UpdateSchedule.next(at(28, 23, 59)))
    }

    @Test
    fun `last slot`() {
        assertEquals(at(27, 16), UpdateSchedule.last(at(28, 7, 43)))
        assertEquals(at(28, 10), UpdateSchedule.last(at(28, 10)))
        assertEquals(at(28, 10), UpdateSchedule.last(at(28, 15, 59)))
        assertEquals(at(28, 16), UpdateSchedule.last(at(28, 22)))
    }

    @Test
    fun `fresh when checked after last slot`() {
        assertFalse(UpdateSchedule.isStale(millis(27, 16, 2), at(28, 7, 43)))
        assertFalse(UpdateSchedule.isStale(millis(28, 10, 1), at(28, 12)))
    }

    @Test
    fun `stale when scheduled check was missed`() {
        // В 12:00 последняя успешная проверка была вчера в 16:02 — утренняя пропущена.
        assertTrue(UpdateSchedule.isStale(millis(27, 16, 2), at(28, 12)))
        // Несколько дней без обновления.
        assertTrue(UpdateSchedule.isStale(millis(24, 16, 2), at(28, 7, 43)))
    }

    @Test
    fun `unknown check time is not marked`() {
        assertFalse(UpdateSchedule.isStale(null, at(28, 12)))
    }

    @Test
    fun `tile model carries stale flag and check date`() {
        val current = CbrXmlParser.parse(Fixtures.current)
        val previous = CbrXmlParser.parse(Fixtures.previous)
        val old = RatesData(current, previous, fetchedAt = System.currentTimeMillis() - 3 * 24 * 3600_000L)
        val tile = WidgetModels.build(old, "USD")!!
        assertTrue(tile.stale)
        assertEquals("⚠ " + tile.checkedDate, WidgetModels.staleLabel(tile.checkedDate))

        val fresh = WidgetModels.build(old.copy(fetchedAt = System.currentTimeMillis()), "USD")!!
        assertFalse(fresh.stale)

        val unknown = WidgetModels.build(old.copy(fetchedAt = null), "USD")!!
        assertFalse(unknown.stale)
        assertNull(unknown.checkedDate)
    }
}
