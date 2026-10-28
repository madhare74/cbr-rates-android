package ru.madhare74.cbrrates.widget

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Расписание обновления виджетов: каждый день в 10:00 и 16:00 по времени телефона.
 *
 * Курс считается несвежим, если с последнего наступившего времени по расписанию
 * приложению не удалось его проверить на сайте ЦБ (например, в 16:30 последняя
 * успешная проверка была вчера).
 */
object UpdateSchedule {
    val SLOTS: List<LocalTime> = listOf(LocalTime.of(10, 0), LocalTime.of(16, 0))

    /** Ближайшее время обновления строго после [now]. */
    fun next(now: ZonedDateTime): ZonedDateTime {
        val today = now.toLocalDate()
        return SLOTS.map { ZonedDateTime.of(today, it, now.zone) }.firstOrNull { it.isAfter(now) }
            ?: ZonedDateTime.of(today.plusDays(1), SLOTS.first(), now.zone)
    }

    /** Последнее наступившее время обновления (не позже [now]). */
    fun last(now: ZonedDateTime): ZonedDateTime {
        val today = now.toLocalDate()
        return SLOTS.map { ZonedDateTime.of(today, it, now.zone) }.lastOrNull { !it.isAfter(now) }
            ?: ZonedDateTime.of(today.minusDays(1), SLOTS.last(), now.zone)
    }

    /**
     * Несвежий ли курс. [fetchedAt] — время последней успешной проверки на сайте ЦБ.
     * Если время неизвестно (данные от старой версии приложения) — не помечаем.
     */
    fun isStale(fetchedAt: Long?, now: ZonedDateTime): Boolean {
        if (fetchedAt == null) return false
        return Instant.ofEpochMilli(fetchedAt).isBefore(last(now).toInstant())
    }

    fun isStale(fetchedAt: Long?, nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Boolean =
        isStale(fetchedAt, ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone))
}
