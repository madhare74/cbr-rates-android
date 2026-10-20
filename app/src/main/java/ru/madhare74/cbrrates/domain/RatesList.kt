package ru.madhare74.cbrrates.domain

import ru.madhare74.cbrrates.data.LoadProblem
import ru.madhare74.cbrrates.data.Rate
import ru.madhare74.cbrrates.data.RatesData
import java.time.LocalDate
import java.util.Locale

data class RateRow(
    val rate: Rate,
    val change: RateChange?,
    val isFavorite: Boolean,
)

data class RateSections(
    val favorites: List<RateRow>,
    val others: List<RateRow>,
) {
    val isEmpty: Boolean get() = favorites.isEmpty() && others.isEmpty()
}

object RatesList {
    private val RU = Locale("ru")

    fun matches(rate: Rate, query: String): Boolean {
        val q = query.trim().lowercase(RU)
        if (q.isEmpty()) return true
        return rate.code.lowercase(RU).contains(q) || rate.name.lowercase(RU).contains(q)
    }

    /** Избранное сверху в порядке добавления, остальные — по коду. */
    fun build(data: RatesData, favorites: List<String>, query: String): RateSections {
        val changes = Changes.computeAll(data)
        val visible = data.current.rates.filter { matches(it, query) }
        val byCode = visible.associateBy { it.code }
        val favoriteRows = favorites.mapNotNull { code ->
            byCode[code]?.let { RateRow(it, changes[code], isFavorite = true) }
        }
        val favoriteSet = favorites.toSet()
        val otherRows = visible
            .filter { it.code !in favoriteSet }
            .sortedBy { it.code }
            .map { RateRow(it, changes[it.code], isFavorite = false) }
        return RateSections(favoriteRows, otherRows)
    }

    /** «100 KZT = 18,45 ₽» или «81,1234 ₽» при номинале 1. */
    fun rateLabel(rate: Rate): String {
        val rub = RuFormat.rate(rate.value) + " ₽"
        return if (rate.nominal == 1) rub else "${rate.nominal} ${rate.code} = $rub"
    }

    /**
     * То же, что [rateLabel], но с неразрывными пробелами внутри «100 KZT» и «16,4521 ₽»:
     * при нехватке места строка переносится только около знака «=».
     */
    fun rateLabelNoBreak(rate: Rate): String {
        val rub = (RuFormat.rate(rate.value) + " ₽").replace(' ', ' ')
        return if (rate.nominal == 1) rub else "${rate.nominal} ${rate.code} = $rub"
    }

    fun header(date: LocalDate?): String =
        if (date == null) "Курсы ЦБ РФ" else "Курсы ЦБ РФ на ${RuFormat.date(date)}"
}

/** Тексты сообщений об ошибках. */
object Messages {

    /** Пометка над списком, когда показаны сохранённые данные. */
    fun withSavedData(problem: LoadProblem, dataDate: LocalDate): String {
        val date = RuFormat.date(dataDate)
        return when (problem) {
            LoadProblem.NoConnection -> "Нет соединения. Данные на $date"
            LoadProblem.SecureConnection ->
                "Не удалось установить защищённое соединение с сайтом ЦБ. Данные на $date"
            is LoadProblem.ServerError ->
                "Сайт ЦБ недоступен (код ${problem.httpCode}). Данные на $date"
            LoadProblem.BadResponse -> "Не удалось разобрать ответ сайта ЦБ. Данные на $date"
        }
    }

    /** Сообщение на весь экран, когда сохранённых данных нет. */
    fun withoutData(problem: LoadProblem): String = when (problem) {
        LoadProblem.NoConnection ->
            "Нет соединения с интернетом. Проверьте подключение и попробуйте снова."
        LoadProblem.SecureConnection ->
            "Не удалось установить защищённое соединение с сайтом ЦБ. Попробуйте позже."
        is LoadProblem.ServerError ->
            "Сайт ЦБ недоступен (код ${problem.httpCode}). Попробуйте позже."
        LoadProblem.BadResponse ->
            "Не удалось разобрать ответ сайта ЦБ. Попробуйте позже."
    }
}
