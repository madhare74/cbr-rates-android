package ru.madhare74.cbrrates.widget

import android.content.Context

/** Настройки каждого виджета (по appWidgetId): валюта и прозрачность фона. */
object WidgetPrefs {
    private const val FILE = "widgets"

    private fun codeKey(appWidgetId: Int) = "code_$appWidgetId"
    private fun transparencyKey(appWidgetId: Int) = "transparency_$appWidgetId"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun code(context: Context, appWidgetId: Int): String? = prefs(context).getString(codeKey(appWidgetId), null)

    fun transparency(context: Context, appWidgetId: Int): Int =
        prefs(context).getInt(transparencyKey(appWidgetId), WidgetStyle.DEFAULT_TRANSPARENCY)

    fun save(context: Context, appWidgetId: Int, code: String, transparency: Int) {
        prefs(context).edit()
            .putString(codeKey(appWidgetId), code)
            .putInt(transparencyKey(appWidgetId), transparency.coerceIn(0, 100))
            .commit()
    }

    fun remove(context: Context, appWidgetId: Int) {
        prefs(context).edit().remove(codeKey(appWidgetId)).remove(transparencyKey(appWidgetId)).apply()
    }
}
