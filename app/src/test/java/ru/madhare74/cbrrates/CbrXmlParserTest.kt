package ru.madhare74.cbrrates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.madhare74.cbrrates.data.CbrParseException
import ru.madhare74.cbrrates.data.CbrXmlParser
import java.math.BigDecimal
import java.time.LocalDate

class CbrXmlParserTest {

    private val snapshot = CbrXmlParser.parse(Fixtures.current)

    @Test
    fun `date is taken from ValCurs Date attribute`() {
        assertEquals(LocalDate.of(2026, 10, 18), snapshot.date)
    }

    @Test
    fun `windows-1251 names are decoded`() {
        assertEquals("Доллар США", snapshot.byCode("USD")!!.name)
        assertEquals("Тенге", snapshot.byCode("KZT")!!.name)
        assertEquals("СДР (специальные права заимствования)", snapshot.byCode("XDR")!!.name)
    }

    @Test
    fun `comma decimal separator is parsed`() {
        assertEquals(0, BigDecimal("81.1234").compareTo(snapshot.byCode("USD")!!.value))
        assertEquals(0, BigDecimal("94.5678").compareTo(snapshot.byCode("EUR")!!.value))
    }

    @Test
    fun `nominal is parsed and unit rate accounts for it`() {
        val kzt = snapshot.byCode("KZT")!!
        assertEquals(100, kzt.nominal)
        assertEquals(0, BigDecimal("0.164521").compareTo(kzt.unitRate))
        assertEquals(10000, snapshot.byCode("UZS")!!.nominal)
        assertEquals(1, snapshot.byCode("USD")!!.nominal)
    }

    @Test
    fun `all currencies are read`() {
        assertEquals(12, snapshot.rates.size)
    }

    @Test
    fun `decode keeps cyrillic and parse of decoded text gives same result`() {
        val text = CbrXmlParser.decode(Fixtures.current)
        assertTrue(text.contains("Доллар США"))
        assertEquals(snapshot, CbrXmlParser.parse(text))
    }

    @Test
    fun `utf-8 declared response is also supported`() {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
            <ValCurs Date="01.02.2026" name="Foreign Currency Market">
              <Valute ID="R01235"><NumCode>840</NumCode><CharCode>USD</CharCode><Nominal>1</Nominal>
              <Name>Доллар США</Name><Value>90,5</Value></Valute>
            </ValCurs>""".trimIndent()
        val parsed = CbrXmlParser.parse(xml.toByteArray(Charsets.UTF_8))
        assertEquals(LocalDate.of(2026, 2, 1), parsed.date)
        assertEquals("Доллар США", parsed.rates.single().name)
        assertEquals(0, BigDecimal("90.5").compareTo(parsed.rates.single().value))
    }

    @Test(expected = CbrParseException::class)
    fun `html instead of xml is a parse error`() {
        CbrXmlParser.parse(Fixtures.broken)
    }

    @Test(expected = CbrParseException::class)
    fun `garbage is a parse error`() {
        CbrXmlParser.parse("not xml at all".toByteArray())
    }

    @Test(expected = CbrParseException::class)
    fun `missing date is a parse error`() {
        CbrXmlParser.parse(
            "<ValCurs><Valute><CharCode>USD</CharCode><Nominal>1</Nominal><Name>x</Name><Value>1,0</Value></Valute></ValCurs>",
        )
    }

    @Test(expected = CbrParseException::class)
    fun `empty rates list is a parse error`() {
        CbrXmlParser.parse("""<ValCurs Date="18.10.2026" name="Foreign Currency Market"></ValCurs>""")
    }

    @Test
    fun `parseDecimal handles comma and spaces`() {
        assertNotNull(CbrXmlParser.parseDecimal(" 1 234,5678 "))
        assertEquals(0, BigDecimal("1234.5678").compareTo(CbrXmlParser.parseDecimal(" 1 234,5678 ")))
    }
}
