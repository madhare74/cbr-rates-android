package ru.madhare74.cbrrates.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Курс одной валюты из ответа ЦБ. [value] — рублей за [nominal] единиц валюты. */
data class Rate(
    val code: String,
    val name: String,
    val nominal: Int,
    val value: BigDecimal,
) {
    /** Рублей за одну единицу валюты. */
    val unitRate: BigDecimal
        get() = value.divide(BigDecimal(nominal), 10, RoundingMode.HALF_UP)
}

/** Набор курсов, установленных ЦБ на дату [date] (атрибут Date в ответе). */
data class RatesSnapshot(
    val date: LocalDate,
    val rates: List<Rate>,
) {
    fun byCode(code: String): Rate? = rates.firstOrNull { it.code == code }
}

/** Текущие курсы и курсы на предыдущую дату установления (для расчёта изменения). */
data class RatesData(
    val current: RatesSnapshot,
    val previous: RatesSnapshot?,
    /** Когда курсы последний раз успешно получены с сайта ЦБ (мс с 1970), null — неизвестно. */
    val fetchedAt: Long? = null,
)
