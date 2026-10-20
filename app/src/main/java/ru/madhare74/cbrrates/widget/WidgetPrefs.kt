package ru.madhare74.cbrrates.widget

import android.content.Context

/** Какая валюта выбрана для каждого виджета (по appWidgetId). */
object WidgetPrefs {
    private const val FILE = "widgets"

    private fun key(appWidgetId: Int) = "code_$appWidgetId"

    fun code(context: Context, appWidgetId: Int): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(key(appWidgetId), null)

    fun setCode(context: Context, appWidgetId: Int, code: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(key(appWidgetId), code).apply()
    }

    fun remove(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(key(appWidgetId)).apply()
    }
}
