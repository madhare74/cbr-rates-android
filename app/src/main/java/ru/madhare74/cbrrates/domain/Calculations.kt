package ru.madhare74.cbrrates.domain

import ru.madhare74.cbrrates.data.Rate
import ru.madhare74.cbrrates.data.RatesData
import java.math.BigDecimal
import java.math.RoundingMode

enum class Direction { UP, DOWN, SAME }

/** Изменение курса к предыдущей дате, в рублях за номинал текущего курса. */
data class RateChange(val delta: BigDecimal, val direction: Direction)

object Changes {

    fun compute(current: Rate, previous: Rate?): RateChange? {
        if (previous == null) return null
        // Приводим прошлый курс к текущему номиналу (ЦБ иногда меняет номинал).
        val previousValue = previous.value
            .multiply(BigDecimal(current.nominal))
            .divide(BigDecimal(previous.nominal), 4, RoundingMode.HALF_UP)
        val delta = current.value.setScale(4, RoundingMode.HALF_UP).subtract(previousValue)
        val direction = when (delta.signum()) {
            1 -> Direction.UP
            -1 -> Direction.DOWN
            else -> Direction.SAME
        }
        return RateChange(delta, direction)
    }

    fun computeAll(data: RatesData): Map<String, RateChange> {
        val previous = data.previous ?: return emptyMap()
        return data.current.rates.mapNotNull { rate ->
            compute(rate, previous.byCode(rate.code))?.let { rate.code to it }
        }.toMap()
    }

    /** «▲ 0,12», «▼ 0,0345», «—». */
    fun label(change: RateChange): String = when (change.direction) {
        Direction.UP -> "▲ " + RuFormat.rate(change.delta.abs())
        Direction.DOWN -> "▼ " + RuFormat.rate(change.delta.abs())
        Direction.SAME -> "—"
    }
}

object Converter {
    /** Сумма в валюте → рубли с учётом номинала. */
    fun toRub(amount: BigDecimal, rate: Rate): BigDecimal =
        amount.multiply(rate.value).divide(BigDecimal(rate.nominal), 6, RoundingMode.HALF_UP)

    /** Рубли → сумма в валюте с учётом номинала. */
    fun fromRub(rub: BigDecimal, rate: Rate): BigDecimal =
        rub.multiply(BigDecimal(rate.nominal)).divide(rate.value, 6, RoundingMode.HALF_UP)
}

object Flags {
    fun emoji(code: String): String = when {
        code == "EUR" -> regional("EU")
        code == "XDR" -> "🌐" // 🌐 — СДР не привязаны к стране
        code.length == 3 && code.all { it in 'A'..'Z' } -> regional(code.substring(0, 2))
        else -> "🏳" // 🏳
    }

    private fun regional(countryCode: String): String =
        countryCode.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}
