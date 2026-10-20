package ru.madhare74.cbrrates.data

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.math.BigDecimal
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

class CbrParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Разбор ответа https://www.cbr.ru/scripts/XML_daily.asp
 *
 * Особенности: кодировка windows-1251 (берётся из XML-заголовка), запятая как
 * десятичный разделитель, номинал (курс может быть за 10, 100 … единиц),
 * дата установления курсов в атрибуте Date корневого элемента.
 */
object CbrXmlParser {

    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val ENCODING_REGEX = Regex("""encoding\s*=\s*["']([A-Za-z0-9_.\-]+)["']""")
    private val XML_DECL_REGEX = Regex("""^\s*<\?xml[^>]*\?>""")

    /** Байты ответа → текст с учётом кодировки из заголовка (по умолчанию windows-1251). */
    fun decode(bytes: ByteArray): String {
        val head = String(bytes, 0, minOf(bytes.size, 256), Charsets.ISO_8859_1)
        val encodingName = ENCODING_REGEX.find(head)?.groupValues?.get(1) ?: "windows-1251"
        val charset = try {
            Charset.forName(encodingName)
        } catch (e: Exception) {
            throw CbrParseException("Неизвестная кодировка ответа: $encodingName", e)
        }
        return String(bytes, charset).removePrefix("﻿")
    }

    fun parse(bytes: ByteArray): RatesSnapshot = parse(decode(bytes))

    /** Разбор уже декодированного текста XML. */
    fun parse(xml: String): RatesSnapshot {
        // Текст уже декодирован — убираем заголовок с encoding, чтобы парсер не спорил с ним.
        val body = XML_DECL_REGEX.replaceFirst(xml.removePrefix("﻿"), "")
        val document = try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            factory.newDocumentBuilder().parse(InputSource(StringReader(body)))
        } catch (e: Exception) {
            throw CbrParseException("Ответ ЦБ не является корректным XML", e)
        }

        val root = document.documentElement
            ?: throw CbrParseException("Пустой ответ ЦБ")
        if (root.tagName != "ValCurs") {
            throw CbrParseException("Неожиданный формат ответа ЦБ: <${root.tagName}>")
        }

        val dateText = root.getAttribute("Date").trim()
        val date = try {
            LocalDate.parse(dateText, DATE_FORMAT)
        } catch (e: Exception) {
            throw CbrParseException("Некорректная дата в ответе ЦБ: '$dateText'", e)
        }

        val nodes = root.getElementsByTagName("Valute")
        val rates = (0 until nodes.length).map { i -> parseValute(nodes.item(i) as Element) }
        if (rates.isEmpty()) throw CbrParseException("В ответе ЦБ нет курсов валют")

        return RatesSnapshot(date, rates)
    }

    /** «81,1234» → 81.1234. */
    fun parseDecimal(text: String): BigDecimal {
        val normalized = text.trim()
            .replace(" ", "")
            .replace(" ", "")
            .replace(',', '.')
        return try {
            BigDecimal(normalized)
        } catch (e: NumberFormatException) {
            throw CbrParseException("Некорректное число в ответе ЦБ: '$text'", e)
        }
    }

    private fun parseValute(element: Element): Rate {
        val code = element.childText("CharCode").uppercase()
        val name = element.childText("Name")
        val nominal = element.childText("Nominal").toIntOrNull()
            ?: throw CbrParseException("Некорректный номинал у валюты $code")
        val value = parseDecimal(element.childText("Value"))
        if (nominal <= 0) throw CbrParseException("Некорректный номинал у валюты $code")
        if (value.signum() <= 0) throw CbrParseException("Некорректный курс у валюты $code")
        return Rate(code = code, name = name, nominal = nominal, value = value)
    }

    private fun Element.childText(tag: String): String {
        val list = getElementsByTagName(tag)
        if (list.length == 0) throw CbrParseException("В ответе ЦБ нет поля $tag")
        return list.item(0).textContent.trim()
    }
}
