package ru.madhare74.cbrrates

import ru.madhare74.cbrrates.data.RatesSource
import java.io.IOException
import java.time.LocalDate

/** Сохранённые образцы ответов ЦБ (windows-1251) из src/sharedTest/fixtures. */
object Fixtures {
    fun bytes(name: String): ByteArray =
        requireNotNull(Fixtures::class.java.classLoader!!.getResourceAsStream(name)) { "нет образца $name" }
            .use { it.readBytes() }

    val current: ByteArray get() = bytes("cbr_current.xml")
    val previous: ByteArray get() = bytes("cbr_previous.xml")
    val broken: ByteArray get() = bytes("cbr_broken.xml")
}

/** Источник, отдающий образцы: без даты — текущие курсы, с датой — предыдущие. */
class FixtureSource : RatesSource {
    val requestedDates = mutableListOf<LocalDate?>()
    override suspend fun fetch(date: LocalDate?): ByteArray {
        requestedDates += date
        return if (date == null) Fixtures.current else Fixtures.previous
    }
}

class OfflineSource : RatesSource {
    override suspend fun fetch(date: LocalDate?): ByteArray = throw IOException("Нет сети")
}
