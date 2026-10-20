package ru.madhare74.cbrrates.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Сайт ЦБ ответил кодом, отличным от 200. */
class CbrHttpException(val code: Int) : Exception("Сайт ЦБ ответил кодом $code")

/** Источник сырых ответов ЦБ. [date] = null — курсы на последнюю дату. */
fun interface RatesSource {
    suspend fun fetch(date: LocalDate?): ByteArray
}

/** Загрузка напрямую с официального сайта ЦБ РФ. */
class HttpCbrSource(
    private val baseUrl: String = "https://www.cbr.ru/scripts/XML_daily.asp",
) : RatesSource {

    override suspend fun fetch(date: LocalDate?): ByteArray = withContext(Dispatchers.IO) {
        val url = if (date == null) baseUrl else "$baseUrl?date_req=${date.format(REQUEST_DATE)}"
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) KursyCB/1.0")
            connection.setRequestProperty("Accept", "application/xml, text/xml, */*")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw CbrHttpException(code)
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        val REQUEST_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
