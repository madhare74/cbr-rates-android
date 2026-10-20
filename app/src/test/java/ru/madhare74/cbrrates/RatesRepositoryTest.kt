package ru.madhare74.cbrrates

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.madhare74.cbrrates.data.CachedRates
import ru.madhare74.cbrrates.data.CbrHttpException
import ru.madhare74.cbrrates.data.CbrXmlParser
import ru.madhare74.cbrrates.data.InMemoryRatesCache
import ru.madhare74.cbrrates.data.LoadProblem
import ru.madhare74.cbrrates.data.LoadResult
import ru.madhare74.cbrrates.data.RatesRepository
import ru.madhare74.cbrrates.data.RatesSource
import ru.madhare74.cbrrates.domain.Messages
import java.net.UnknownHostException
import java.time.LocalDate
import javax.net.ssl.SSLHandshakeException

class RatesRepositoryTest {

    private fun savedCache() = InMemoryRatesCache(
        CachedRates(
            CbrXmlParser.decode(Fixtures.current),
            CbrXmlParser.decode(Fixtures.previous),
        ),
    )

    @Test
    fun `fresh load returns current and previous and saves them`() = runTest {
        val cache = InMemoryRatesCache()
        val source = FixtureSource()
        val result = RatesRepository(source, cache).load()

        result as LoadResult.Fresh
        assertEquals(LocalDate.of(2026, 10, 18), result.data.current.date)
        assertEquals(LocalDate.of(2026, 10, 17), result.data.previous!!.date)
        // предыдущий день запрошен по дате перед датой текущих курсов
        assertEquals(listOf(null, LocalDate.of(2026, 10, 17)), source.requestedDates)
        assertNotNull(cache.read())
    }

    @Test
    fun `no network with saved data shows saved data with notice`() = runTest {
        val result = RatesRepository(OfflineSource(), savedCache()).load()

        result as LoadResult.Cached
        assertEquals(LoadProblem.NoConnection, result.problem)
        assertEquals(LocalDate.of(2026, 10, 18), result.data.current.date)
        assertEquals(12, result.data.current.rates.size)
        assertEquals(
            "Нет соединения. Данные на 18.10.2026",
            Messages.withSavedData(result.problem, result.data.current.date),
        )
    }

    @Test
    fun `no network without saved data is failure`() = runTest {
        val result = RatesRepository(OfflineSource(), InMemoryRatesCache()).load()
        assertEquals(LoadResult.Failed(LoadProblem.NoConnection), result)
    }

    @Test
    fun `unknown host counts as no connection`() = runTest {
        val source = RatesSource { throw UnknownHostException("www.cbr.ru") }
        val result = RatesRepository(source, InMemoryRatesCache()).load()
        assertEquals(LoadResult.Failed(LoadProblem.NoConnection), result)
    }

    @Test
    fun `http error is server error`() = runTest {
        val source = RatesSource { throw CbrHttpException(403) }
        val result = RatesRepository(source, savedCache()).load()
        result as LoadResult.Cached
        assertEquals(LoadProblem.ServerError(403), result.problem)
        assertEquals(
            "Сайт ЦБ недоступен (код 403). Данные на 18.10.2026",
            Messages.withSavedData(result.problem, result.data.current.date),
        )
    }

    @Test
    fun `tls error is secure connection problem`() = runTest {
        val source = RatesSource { throw SSLHandshakeException("cert") }
        val result = RatesRepository(source, InMemoryRatesCache()).load()
        assertEquals(LoadResult.Failed(LoadProblem.SecureConnection), result)
    }

    @Test
    fun `unparsable response keeps saved data and does not overwrite cache`() = runTest {
        val cache = savedCache()
        val before = cache.read()
        val source = RatesSource { Fixtures.broken }
        val result = RatesRepository(source, cache).load()

        result as LoadResult.Cached
        assertEquals(LoadProblem.BadResponse, result.problem)
        assertEquals(before, cache.read())
    }

    @Test
    fun `unparsable response without saved data is failure`() = runTest {
        val result = RatesRepository(RatesSource { Fixtures.broken }, InMemoryRatesCache()).load()
        assertEquals(LoadResult.Failed(LoadProblem.BadResponse), result)
        assertTrue(Messages.withoutData(LoadProblem.BadResponse).contains("разобрать"))
    }

    @Test
    fun `previous day failure still shows current rates`() = runTest {
        val source = RatesSource { date -> if (date == null) Fixtures.current else throw UnknownHostException() }
        val result = RatesRepository(source, InMemoryRatesCache()).load()
        result as LoadResult.Fresh
        assertNull(result.data.previous)
    }

    @Test
    fun `previous lookup steps back when cbr returns the same date`() = runTest {
        val requested = mutableListOf<LocalDate?>()
        val source = RatesSource { date ->
            requested += date
            when (date) {
                null, LocalDate.of(2026, 10, 17) -> Fixtures.current // ЦБ вернул ту же дату 18.10
                else -> Fixtures.previous
            }
        }
        val result = RatesRepository(source, InMemoryRatesCache()).load()
        result as LoadResult.Fresh
        assertEquals(LocalDate.of(2026, 10, 17), result.data.previous!!.date)
        assertEquals(listOf(null, LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 16)), requested)
    }

    @Test
    fun `saved data is readable after successful load`() = runTest {
        val cache = InMemoryRatesCache()
        RatesRepository(FixtureSource(), cache).load()
        val offline = RatesRepository(OfflineSource(), cache).load()
        offline as LoadResult.Cached
        assertEquals("Доллар США", offline.data.current.byCode("USD")!!.name)
        assertNotNull(offline.data.previous)
    }
}
