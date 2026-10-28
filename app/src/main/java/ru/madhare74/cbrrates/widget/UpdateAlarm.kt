package ru.madhare74.cbrrates.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.madhare74.cbrrates.CbrApp
import ru.madhare74.cbrrates.data.LoadResult
import java.time.ZonedDateTime

/**
 * Будильник системы для обновления виджетов в 10:00 и 16:00.
 * Точные будильники Samsung не откладывает так, как обычную фоновую работу,
 * поэтому обновление приходит вовремя, даже если приложение давно не открывали.
 */
object UpdateAlarm {
    private const val REQUEST_CODE = 1016
    const val ACTION = "ru.madhare74.cbrrates.UPDATE_WIDGETS"

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, UpdateAlarmReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Поставить будильник на ближайшее время по расписанию (заменяет предыдущий). */
    fun scheduleNext(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val at = UpdateSchedule.next(ZonedDateTime.now()).toInstant().toEpochMilli()
        val pi = pendingIntent(context)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (canExact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }
}

/**
 * Срабатывание будильника, а также перезагрузка телефона, обновление приложения
 * и смена времени/часового пояса — во всех случаях заново ставим будильник.
 */
class UpdateAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as CbrApp
        if (!RatesUpdateWorker.hasWidgets(app)) {
            UpdateAlarm.cancel(app)
            return
        }
        UpdateAlarm.scheduleNext(app)

        val isAlarm = intent.action == UpdateAlarm.ACTION
        val stale = UpdateSchedule.isStale(app.repository.loadCached()?.fetchedAt)
        if (!isAlarm && !stale) return

        // После будильника у приложения есть ~10 секунд на работу с сетью даже в спящем режиме.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val result = withTimeoutOrNull(8_000) { app.repository.load() }
                if (result !is LoadResult.Fresh) {
                    // Не успели или ошибка — повторы в фоне с паузами 15, 30, 60… минут.
                    RatesUpdateWorker.refreshNow(app)
                }
                runCatching { RatesWidget().updateAll(app) }
            } finally {
                pending.finish()
            }
        }
    }
}
