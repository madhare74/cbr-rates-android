package ru.madhare74.cbrrates.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Русский формат чисел: пробел между разрядами, запятая в дробной части. */
object RuFormat {

    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private fun format(pattern: String, value: BigDecimal): String {
        val symbols = DecimalFormatSymbols(Locale.ROOT).apply {
            groupingSeparator = ' '
            decimalSeparator = ','
            minusSign = '-'
        }
        return DecimalFormat(pattern, symbols).apply { roundingMode = RoundingMode.HALF_UP }.format(value)
    }

    /** Курс: минимум 2, максимум 4 знака после запятой. 18.45 → «18,45», 81.1234 → «81,1234». */
    fun rate(value: BigDecimal): String = format("#,##0.00##", value)

    /**
     * Сумма в конвертере: до 2 знаков после запятой, для сумм меньше 1 — до 4,
     * чтобы не терять малые значения. 1234567.891 → «1 234 567,89».
     */
    fun amount(value: BigDecimal): String =
        if (value.abs() < BigDecimal.ONE) format("#,##0.####", value) else format("#,##0.##", value)

    fun date(date: LocalDate): String = date.format(DATE)

    /** «1 234,5» / «1234.5» → 1234.5; пустая или некорректная строка → null. */
    fun parseAmount(text: String): BigDecimal? {
        val normalized = text
            .replace(" ", "")
            .replace(" ", "")
            .replace(" ", "")
            .replace(',', '.')
        if (normalized.isEmpty() || normalized == ".") return null
        return normalized.toBigDecimalOrNull()
    }

    /**
     * Очистка ввода в поле суммы: только цифры и один разделитель (запятая),
     * не больше 12 цифр в целой части и 4 в дробной.
     */
    fun sanitizeInput(text: String): String {
        val result = StringBuilder()
        var hasSeparator = false
        var intDigits = 0
        var fracDigits = 0
        for (ch in text) {
            when {
                ch.isDigit() && !hasSeparator && intDigits < 12 -> {
                    result.append(ch); intDigits++
                }
                ch.isDigit() && hasSeparator && fracDigits < 4 -> {
                    result.append(ch); fracDigits++
                }
                (ch == ',' || ch == '.') && !hasSeparator -> {
                    if (intDigits == 0) result.append('0')
                    result.append(','); hasSeparator = true
                }
            }
        }
        return result.toString()
    }
}
