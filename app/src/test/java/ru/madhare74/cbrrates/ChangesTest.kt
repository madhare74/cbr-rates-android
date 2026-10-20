package ru.madhare74.cbrrates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.madhare74.cbrrates.data.CbrXmlParser
import ru.madhare74.cbrrates.data.Rate
import ru.madhare74.cbrrates.data.RatesData
import ru.madhare74.cbrrates.domain.Changes
import ru.madhare74.cbrrates.domain.Direction
import java.math.BigDecimal

class ChangesTest {

    private fun rate(value: String, nominal: Int = 1) = Rate("USD", "Доллар США", nominal, BigDecimal(value))

    @Test
    fun `growth is UP with positive delta`() {
        val change = Changes.compute(rate("81.1234"), rate("80.9876"))!!
        assertEquals(Direction.UP, change.direction)
        assertEquals(0, BigDecimal("0.1358").compareTo(change.delta))
        assertEquals("▲ 0,1358", Changes.label(change))
    }

    @Test
    fun `fall is DOWN`() {
        val change = Changes.compute(rate("94.5678"), rate("94.8000"))!!
        assertEquals(Direction.DOWN, change.direction)
        assertEquals("▼ 0,2322", Changes.label(change))
    }

    @Test
    fun `equal rates are SAME with dash`() {
        val change = Changes.compute(rate("11.3456"), rate("11.3456"))!!
        assertEquals(Direction.SAME, change.direction)
        assertEquals("—", Changes.label(change))
    }

    @Test
    fun `no previous rate gives no change`() {
        assertNull(Changes.compute(rate("1"), null))
    }

    @Test
    fun `nominal change is normalized`() {
        // Было 1.6500 ₽ за 10 тенге, стало 16.4521 ₽ за 100 → прошлый курс за 100 = 16.5000.
        val change = Changes.compute(rate("16.4521", 100), rate("1.6500", 10))!!
        assertEquals(Direction.DOWN, change.direction)
        assertEquals(0, BigDecimal("-0.0479").compareTo(change.delta))
    }

    @Test
    fun `changes from saved samples`() {
        val data = RatesData(CbrXmlParser.parse(Fixtures.current), CbrXmlParser.parse(Fixtures.previous))
        val changes = Changes.computeAll(data)
        assertEquals(Direction.UP, changes["USD"]!!.direction)
        assertEquals(Direction.DOWN, changes["EUR"]!!.direction)
        assertEquals(Direction.SAME, changes["CNY"]!!.direction)
        assertEquals(Direction.DOWN, changes["KZT"]!!.direction)
        assertEquals("▼ 0,0479", Changes.label(changes["KZT"]!!))
    }

    @Test
    fun `no previous snapshot gives empty changes`() {
        val data = RatesData(CbrXmlParser.parse(Fixtures.current), null)
        assertEquals(emptyMap<String, Any>(), Changes.computeAll(data))
    }
}
