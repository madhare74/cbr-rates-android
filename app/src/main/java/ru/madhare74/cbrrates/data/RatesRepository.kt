package ru.madhare74.cbrrates.data

import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.net.ssl.SSLException

/** Почему не удалось получить свежие курсы. */
sealed interface LoadProblem {
    /** Нет интернета или сайт не отвечает. */
    data object NoConnection : LoadProblem

    /** Не удалось установить защищённое соединение (сертификат и т.п.). */
    data object SecureConnection : LoadProblem

    /** Сайт ЦБ ответил ошибкой. */
    data class ServerError(val httpCode: Int) : LoadProblem

    /** Ответ получен, но разобрать его не удалось. */
    data object BadResponse : LoadProblem
}

sealed interface LoadResult {
    /** Свежие данные с сайта ЦБ. */
    data class Fresh(val data: RatesData) : LoadResult

    /** Свежие данные получить не удалось, показываем сохранённые. */
    data class Cached(val data: RatesData, val problem: LoadProblem) : LoadResult

    /** Нет ни свежих, ни сохранённых данных. */
    data class Failed(val problem: LoadProblem) : LoadResult
}

class RatesRepository(
    private val source: RatesSource,
    private val cache: RatesCache,
) {

    /** Сохранённые на устройстве курсы, если они есть и читаются. */
    fun loadCached(): RatesData? {
        val cached = cache.read() ?: return null
        return try {
            val current = CbrXmlParser.parse(cached.currentXml)
            val previous = cached.previousXml?.let { runCatching { CbrXmlParser.parse(it) }.getOrNull() }
            RatesData(current, previous)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun load(): LoadResult {
        return try {
            val currentXml = CbrXmlParser.decode(source.fetch(null))
            val current = CbrXmlParser.parse(currentXml)
            val (previous, previousXml) = loadPrevious(current)
            cache.write(CachedRates(currentXml, previousXml))
            LoadResult.Fresh(RatesData(current, previous))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val problem = classify(e)
            val cached = loadCached()
            if (cached != null) LoadResult.Cached(cached, problem) else LoadResult.Failed(problem)
        }
    }

    /**
     * Курсы на предыдущую дату установления. Запрашиваем день перед датой текущих
     * курсов; если ЦБ вернул ту же дату, отступаем дальше (до недели).
     * Ошибка здесь не мешает показать текущие курсы — просто не будет изменения.
     */
    private suspend fun loadPrevious(current: RatesSnapshot): Pair<RatesSnapshot?, String?> {
        var date = current.date.minusDays(1)
        try {
            repeat(MAX_PREVIOUS_STEPS) {
                val xml = CbrXmlParser.decode(source.fetch(date))
                val snapshot = CbrXmlParser.parse(xml)
                if (snapshot.date.isBefore(current.date)) return snapshot to xml
                date = date.minusDays(1)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // не критично
        }
        return null to null
    }

    companion object {
        private const val MAX_PREVIOUS_STEPS = 7

        fun classify(e: Throwable): LoadProblem = when (e) {
            is CbrHttpException -> LoadProblem.ServerError(e.code)
            is CbrParseException -> LoadProblem.BadResponse
            is SSLException -> LoadProblem.SecureConnection
            is IOException -> LoadProblem.NoConnection
            else -> LoadProblem.BadResponse
        }
    }
}
