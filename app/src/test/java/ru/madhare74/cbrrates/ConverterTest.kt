package ru.madhare74.cbrrates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.madhare74.cbrrates.data.Rate
import ru.madhare74.cbrrates.domain.Converter
import ru.madhare74.cbrrates.domain.RuFormat
import java.math.BigDecimal

class ConverterTest {

    private val usd = Rate("USD", "Доллар США", 1, BigDecimal("81.1234"))
    private val kzt = Rate("KZT", "Тенге", 100, BigDecimal("16.4521"))
    private val uzs = Rate("UZS", "Узбекских сумов", 10000, BigDecimal("64.1234"))

    @Test
    fun `currency to rubles with nominal 1`() {
        assertEquals("162,25", RuFormat.amount(Converter.toRub(BigDecimal("2"), usd)))
    }

    @Test
    fun `currency to rubles with nominal 100`() {
        // 100 тенге = 16,4521 ₽ → 1000 тенге = 164,521 ₽
        assertEquals("164,52", RuFormat.amount(Converter.toRub(BigDecimal("1000"), kzt)))
        // 1 тенге = 0,164521 ₽ → для сумм меньше 1 показываем до 4 знаков
        assertEquals("0,1645", RuFormat.amount(Converter.toRub(BigDecimal.ONE, kzt)))
    }

    @Test
    fun `rubles to currency with nominal`() {
        assertEquals("100", RuFormat.amount(Converter.fromRub(BigDecimal("16.4521"), kzt)))
        assertEquals("10 000", RuFormat.amount(Converter.fromRub(BigDecimal("64.1234"), uzs)))
        assertEquals("1 232,69", RuFormat.amount(Converter.fromRub(BigDecimal("100000"), usd)))
    }

    @Test
    fun `round trip is consistent`() {
        val rub = Converter.toRub(BigDecimal("12345.67"), usd)
        assertEquals("12 345,67", RuFormat.amount(Converter.fromRub(rub, usd)))
    }

    @Test
    fun `russian number format`() {
        assertEquals("1 234 567,89", RuFormat.amount(BigDecimal("1234567.891")))
        assertEquals("81,1234", RuFormat.rate(BigDecimal("81.1234")))
        assertEquals("18,45", RuFormat.rate(BigDecimal("18.45")))
        assertEquals("1 000,00", RuFormat.rate(BigDecimal("1000")))
    }

    @Test
    fun `parse user input`() {
        assertEquals(0, BigDecimal("1234.5").compareTo(RuFormat.parseAmount("1 234,5")))
        assertEquals(0, BigDecimal("1234.5").compareTo(RuFormat.parseAmount("1234.5")))
        assertEquals(0, BigDecimal("1000").compareTo(RuFormat.parseAmount("1 000")))
        assertNull(RuFormat.parseAmount(""))
        assertNull(RuFormat.parseAmount(","))
    }

    @Test
    fun `sanitize user input`() {
        assertEquals("12,5", RuFormat.sanitizeInput("12.5"))
        assertEquals("12,53", RuFormat.sanitizeInput("12,5,3"))
        assertEquals("0,5", RuFormat.sanitizeInput(",5"))
        assertEquals("1000", RuFormat.sanitizeInput("1 000"))
        assertEquals("1,2345", RuFormat.sanitizeInput("1,23456"))
        assertEquals("", RuFormat.sanitizeInput("abc"))
    }

    @Test
    fun `date format`() {
        assertEquals("05.01.2026", RuFormat.date(java.time.LocalDate.of(2026, 1, 5)))
    }
}
