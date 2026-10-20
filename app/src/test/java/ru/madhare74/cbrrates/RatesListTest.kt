package ru.madhare74.cbrrates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.madhare74.cbrrates.data.CbrXmlParser
import ru.madhare74.cbrrates.data.DEFAULT_FAVORITES
import ru.madhare74.cbrrates.data.RatesData
import ru.madhare74.cbrrates.domain.Flags
import ru.madhare74.cbrrates.domain.RatesList
import java.time.LocalDate

class RatesListTest {

    private val data = RatesData(CbrXmlParser.parse(Fixtures.current), CbrXmlParser.parse(Fixtures.previous))

    @Test
    fun `favorites are pinned on top in their order`() {
        val sections = RatesList.build(data, DEFAULT_FAVORITES, "")
        assertEquals(listOf("USD", "EUR", "CNY", "KZT"), sections.favorites.map { it.rate.code })
        assertTrue(sections.others.none { it.isFavorite })
        assertEquals(8, sections.others.size)
        assertEquals(sections.others.map { it.rate.code }.sorted(), sections.others.map { it.rate.code })
    }

    @Test
    fun `search by code and by russian name, case insensitive`() {
        val byCode = RatesList.build(data, DEFAULT_FAVORITES, "usd")
        assertEquals(listOf("USD"), (byCode.favorites + byCode.others).map { it.rate.code })

        val byName = RatesList.build(data, DEFAULT_FAVORITES, "ТЕНГЕ")
        assertEquals(listOf("KZT"), (byName.favorites + byName.others).map { it.rate.code })

        val nothing = RatesList.build(data, DEFAULT_FAVORITES, "zzz")
        assertTrue(nothing.isEmpty)
    }

    @Test
    fun `rate label shows nominal when not 1`() {
        assertEquals("100 KZT = 16,4521 ₽", RatesList.rateLabel(data.current.byCode("KZT")!!))
        assertEquals("81,1234 ₽", RatesList.rateLabel(data.current.byCode("USD")!!))
    }

    @Test
    fun `header contains cbr date`() {
        assertEquals("Курсы ЦБ РФ на 21.10.2026", RatesList.header(LocalDate.of(2026, 10, 21)))
    }

    @Test
    fun `flags`() {
        assertEquals("🇺🇸", Flags.emoji("USD"))
        assertEquals("🇪🇺", Flags.emoji("EUR"))
        assertEquals("🇰🇿", Flags.emoji("KZT"))
    }
}
