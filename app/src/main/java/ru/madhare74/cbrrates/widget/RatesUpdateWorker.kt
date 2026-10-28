package ru.madhare74.cbrrates.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.madhare74.cbrrates.CbrApp
import ru.madhare74.cbrrates.data.LoadResult
import java.util.concurrent.TimeUnit

/**
 * Фоновая загрузка курсов для виджета. По расписанию — раз в 12 часов (точное время
 * выбирает Android, обычно с небольшим опозданием), и разово по кнопке «↻».
 */
class RatesUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as CbrApp
        val result = app.repository.load()
        RatesWidget().updateAll(applicationContext)
        return when {
            result is LoadResult.Fresh -> Result.success()
            runAttemptCount < MAX_RETRIES -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val PERIODIC = "rates_widget_periodic"
        private const val NOW = "rates_widget_now"
        private const val MAX_RETRIES = 4

        private val network = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun hasWidgets(context: Context): Boolean {
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, RatesWidgetReceiver::class.java))
            return ids.isNotEmpty()
        }

        /** Будильник на 10:00 и 16:00 плюс запасное фоновое обновление раз в 12 часов. */
        fun schedule(context: Context) {
            UpdateAlarm.scheduleNext(context)
            val request = PeriodicWorkRequestBuilder<RatesUpdateWorker>(12, TimeUnit.HOURS, 2, TimeUnit.HOURS)
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<RatesUpdateWorker>()
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            UpdateAlarm.cancel(context)
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
