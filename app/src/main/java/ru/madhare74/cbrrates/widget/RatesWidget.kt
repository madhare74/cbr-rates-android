package ru.madhare74.cbrrates.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.madhare74.cbrrates.CbrApp
import ru.madhare74.cbrrates.MainActivity
import ru.madhare74.cbrrates.domain.Direction

/**
 * Виджет-плитка «валюта → рубль»: флаги пары сверху, под разделителем крупный курс ЦБ
 * и изменение к предыдущему дню в процентах. Валюта выбирается при добавлении виджета.
 * Размер текста подстраивается под размер плитки.
 */
class RatesWidget(
    /** Для скриншот-тестов: валюта без привязки к настоящему виджету. */
    private val codeOverride: String? = null,
) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as CbrApp
        val code = codeOverride
            ?: runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrNull()
                ?.let { WidgetPrefs.code(context, it) }
            ?: WidgetModels.DEFAULT_CODE
        val model = withContext(Dispatchers.IO) { WidgetModels.build(app.repository.loadCached(), code) }
        provideContent { Tile(model, code) }
    }
}

class RatesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RatesWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RatesUpdateWorker.schedule(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetPrefs.remove(context, it) }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RatesUpdateWorker.cancel(context)
    }
}

// Тёмная плитка как на образце; в светлой теме — светлая.
private val TileBg = ColorProvider(day = Color(0xF2F4F4F6), night = Color(0xE61E1E20))
private val HeaderBg = ColorProvider(day = Color(0xFFE6E7EA), night = Color(0xFF2A2A2D))
private val Divider = ColorProvider(day = Color(0xFFCBCCD0), night = Color(0xFF3C3C40))
private val MainText = ColorProvider(day = Color(0xFF111114), night = Color(0xFFFFFFFF))
private val SubText = ColorProvider(day = Color(0xFF6B6D73), night = Color(0xFF9A9BA1))
private val Up = ColorProvider(day = Color(0xFF2E7D32), night = Color(0xFF6FD47A))
private val Down = ColorProvider(day = Color(0xFFC62828), night = Color(0xFFFF7A70))

@Composable
private fun Tile(model: TileModel?, code: String) {
    val size = LocalSize.current
    // Размеры текста считаем от размера плитки и делим на системный масштаб шрифта,
    // чтобы при крупном шрифте в настройках телефона плитка не «разъезжалась».
    val fontScale = LocalContext.current.resources.configuration.fontScale
    // Бюджет по высоте (в долях base): шапка ≈0,30, курс ≈0,26, проценты ≈0,21, подпись ≈0,15.
    val base = minOf(size.width.value * 0.9f, size.height.value)
    fun sp(dp: Float) = (dp / fontScale).sp

    val rateSize = base * 0.21f
    val percentSize = base * 0.17f
    val flagSize = base * 0.16f
    val smallSize = (base * 0.08f).coerceAtLeast(9f)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(22.dp)
            .background(TileBg)
            .clickable(actionStartActivity<MainActivity>()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Шапка: флаг валюты и флаг России
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(HeaderBg)
                .padding(vertical = (base * 0.05f).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(model?.flag ?: "", style = TextStyle(fontSize = sp(flagSize)))
            Spacer(GlanceModifier.width((base * 0.16f).dp))
            Text(model?.rubFlag ?: "🇷🇺", style = TextStyle(fontSize = sp(flagSize)))
        }
        Box(GlanceModifier.fillMaxWidth().height(1.dp).background(Divider)) {}

        if (model == null) {
            Box(
                modifier = GlanceModifier.fillMaxSize().padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$code\nНет данных.\nОткройте приложение",
                    style = TextStyle(color = SubText, fontSize = sp(smallSize * 1.2f), textAlign = TextAlign.Center),
                )
            }
            return@Column
        }

        Column(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                model.rate,
                style = TextStyle(
                    color = MainText,
                    fontSize = sp(rateSize),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
            model.percent?.let {
                val color = when (model.direction) {
                    Direction.UP -> Up
                    Direction.DOWN -> Down
                    else -> SubText
                }
                Text(
                    it,
                    style = TextStyle(
                        color = color,
                        fontSize = sp(percentSize),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                )
            }
        }
        // Подпись: номинал (если не 1) и дата курса ЦБ
        Text(
            listOfNotNull(model.nominalNote, model.date).joinToString(" · "),
            modifier = GlanceModifier.padding(bottom = (base * 0.045f).dp),
            style = TextStyle(color = SubText, fontSize = sp(smallSize), textAlign = TextAlign.Center),
            maxLines = 1,
        )
    }
}
