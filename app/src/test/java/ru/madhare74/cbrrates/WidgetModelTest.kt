package ru.madhare74.cbrrates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.madhare74.cbrrates.data.CbrXmlParser
import ru.madhare74.cbrrates.data.RatesData
import ru.madhare74.cbrrates.domain.Direction
import ru.madhare74.cbrrates.widget.WidgetModels
import java.math.BigDecimal

class WidgetModelTest {

    private val data = RatesData(CbrXmlParser.parse(Fixtures.current), CbrXmlParser.parse(Fixtures.previous))

    @Test
    fun `usd tile`() {
        val tile = WidgetModels.build(data, "USD")!!
        assertEquals("81,12", tile.rate)
        assertEquals("🇺🇸", tile.flag)
        assertEquals("🇷🇺", tile.rubFlag)
        assertNull(tile.nominalNote)
        // (81,1234 − 80,9876) / 80,9876 = +0,1677 %
        assertEquals("+0,17%", tile.percent)
        assertEquals(Direction.UP, tile.direction)
        assertEquals("18.10", tile.date)
    }

    @Test
    fun `falling rate has minus sign`() {
        val tile = WidgetModels.build(data, "EUR")!!
        // (94,5678 − 94,8000) / 94,8000 = −0,2449 %
        assertEquals("−0,24%", tile.percent)
        assertEquals(Direction.DOWN, tile.direction)
    }

    @Test
    fun `nominal is shown in caption`() {
        val tile = WidgetModels.build(data, "KZT")!!
        assertEquals("16,45", tile.rate)
        assertEquals("за 100 KZT", tile.nominalNote)
    }

    @Test
    fun `unchanged rate`() {
        val tile = WidgetModels.build(data, "CNY")!!
        assertEquals("0,00%", tile.percent)
        assertEquals(Direction.SAME, tile.direction)
    }

    @Test
    fun `no previous day means no percent`() {
        val tile = WidgetModels.build(RatesData(data.current, null), "USD")!!
        assertNull(tile.percent)
        assertNull(tile.direction)
    }

    @Test
    fun `missing data or currency gives null`() {
        assertNull(WidgetModels.build(null, "USD"))
        assertNull(WidgetModels.build(data, "ZZZ"))
    }

    @Test
    fun `small rates keep precision`() {
        assertEquals("1,6452", WidgetModels.tileRate(BigDecimal("1.64521")))
        assertEquals("1 111,22", WidgetModels.tileRate(BigDecimal("1111.2233")))
    }

    @Test
    fun `long numbers get smaller font to fit width`() {
        val short = WidgetModels.fitWidth("81,12", 70f)
        val long = WidgetModels.fitWidth("1 111,22", 70f)
        assert(long < short)
        assert(long * 8 * 0.58f <= 70.01f)
    }

    @Test
    fun `percent rounding`() {
        assertEquals("−0,54%", WidgetModels.percent(BigDecimal("83.00"), BigDecimal("-0.4507")))
    }
}
