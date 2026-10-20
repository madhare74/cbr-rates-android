package ru.madhare74.cbrrates.widget

import ru.madhare74.cbrrates.data.RatesData
import ru.madhare74.cbrrates.domain.Changes
import ru.madhare74.cbrrates.domain.Direction
import ru.madhare74.cbrrates.domain.Flags
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Содержимое плитки виджета: одна валюта к рублю. */
data class TileModel(
    val code: String,
    val flag: String,
    /** Флаг России — вторая сторона пары. */
    val rubFlag: String,
    /** «83,00» — рублей за номинал. */
    val rate: String,
    /** «за 100 KZT», если номинал не 1. */
    val nominalNote: String?,
    /** Номинал: курс указан за столько единиц валюты. */
    val nominal: Int,
    /** «−0,54%», «+0,12%», «0,00%» или null, если прошлого курса нет. */
    val percent: String?,
    val direction: Direction?,
    /** «20.10» — дата, на которую ЦБ установил курс. */
    val date: String,
)

object WidgetModels {
    /** Плитка меньше этого (в dp) — компактная, «размером с иконку»: без подписи с датой. */
    const val COMPACT_BELOW = 105f

    /**
     * Размер шрифта, при котором [text] (жирный) влезает в [widthDp] по ширине.
     * Средняя ширина цифры жирного Roboto ≈ 0,58 от размера шрифта.
     */
    fun fitWidth(text: String, widthDp: Float): Float = widthDp / (text.length.coerceAtLeast(1) * 0.58f)

    const val DEFAULT_CODE = "USD"
    private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")
    private const val MINUS = "−"

    private fun formatter(pattern: String) = DecimalFormat(
        pattern,
        DecimalFormatSymbols(Locale.ROOT).apply {
            groupingSeparator = ' '
            decimalSeparator = ','
        },
    ).apply { roundingMode = RoundingMode.HALF_UP }

    /** Курс на плитке: 2 знака после запятой, для курсов меньше 10 — 4, чтобы не терять точность. */
    fun tileRate(value: BigDecimal): String =
        if (value < BigDecimal.TEN) formatter("#,##0.00##").format(value) else formatter("#,##0.00").format(value)

    /** Изменение в процентах к предыдущему курсу: «−0,54%». */
    fun percent(current: BigDecimal, delta: BigDecimal): String {
        val previous = current.subtract(delta)
        if (previous.signum() == 0) return "0,00%"
        val value = delta.multiply(BigDecimal(100)).divide(previous, 2, RoundingMode.HALF_UP)
        val text = formatter("#,##0.00").format(value.abs()) + "%"
        return when (value.signum()) {
            1 -> "+$text"
            -1 -> MINUS + text
            else -> text
        }
    }

    fun build(data: RatesData?, code: String): TileModel? {
        if (data == null) return null
        val rate = data.current.byCode(code) ?: return null
        val change = Changes.computeAll(data)[code]
        return TileModel(
            code = code,
            flag = Flags.emoji(code),
            rubFlag = Flags.emoji("RUB"),
            rate = tileRate(rate.value),
            nominalNote = if (rate.nominal == 1) null else "за ${rate.nominal} $code",
            nominal = rate.nominal,
            percent = change?.let { percent(rate.value, it.delta) },
            direction = change?.let { if (percent(rate.value, it.delta) == "0,00%") Direction.SAME else it.direction },
            date = data.current.date.format(SHORT_DATE),
        )
    }
}
