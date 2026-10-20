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
import androidx.glance.color.ColorProvider as DayNightColor
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.AndroidRemoteViews
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import ru.madhare74.cbrrates.R
import ru.madhare74.cbrrates.domain.Flags
import ru.madhare74.cbrrates.widget.WidgetStyle.withOpacity
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
    private val transparencyOverride: Int? = null,
) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as CbrApp
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrNull()
        val code = codeOverride
            ?: appWidgetId?.let { WidgetPrefs.code(context, it) }
            ?: WidgetModels.DEFAULT_CODE
        val transparency = transparencyOverride
            ?: appWidgetId?.let { WidgetPrefs.transparency(context, it) }
            ?: WidgetStyle.DEFAULT_TRANSPARENCY
        val model = withContext(Dispatchers.IO) { WidgetModels.build(app.repository.loadCached(), code) }
        provideContent { Tile(model, code, transparency) }
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

private fun dayNight(transparency: Int, pick: (WidgetStyle.Palette) -> Color, fade: Boolean): ColorProvider {
    val day = pick(WidgetStyle.Day)
    val night = pick(WidgetStyle.Night)
    return if (fade) {
        DayNightColor(day = day.withOpacity(transparency), night = night.withOpacity(transparency))
    } else {
        DayNightColor(day = day, night = night)
    }
}

@Composable
private fun Tile(model: TileModel?, code: String, transparency: Int) {
    val size = LocalSize.current
    // Размеры считаются от размера плитки в dp и не зависят от системного размера шрифта,
    // чтобы при крупном шрифте в настройках телефона плитка не «разъезжалась».
    val fontScale = LocalContext.current.resources.configuration.fontScale
    // Компактная плитка (1×1, размером с иконку): шапка ≈0,33, курс ≈0,33, проценты ≈0,25, без подписи.
    // Обычная: шапка ≈0,30, курс ≈0,26, проценты ≈0,21, подпись ≈0,15.
    val base = minOf(size.width.value * 0.9f, size.height.value)
    val compact = base < WidgetModels.COMPACT_BELOW
    fun sp(dp: Float) = (dp / fontScale).sp

    val textWidth = size.width.value - 10f
    val rateSize = minOf(base * (if (compact) 0.27f else 0.21f), WidgetModels.fitWidth(model?.rate ?: "", textWidth))
    val percentSize = minOf(base * (if (compact) 0.2f else 0.17f), WidgetModels.fitWidth(model?.percent ?: "", textWidth))
    val flagSize = base * (if (compact) 0.2f else 0.16f)
    val smallSize = (base * 0.08f).coerceAtLeast(9f)
    val corner = if (compact) 16.dp else 22.dp

    val overlay = WidgetStyle.isOverlay(transparency)
    val o = WidgetStyle.Overlay
    val tileBg = dayNight(transparency, { it.tile }, fade = true)
    val headerBg = dayNight(transparency, { it.header }, fade = true)
    val divider = if (overlay) ColorProvider(o.divider) else dayNight(transparency, { it.divider }, fade = true)

    @Composable
    fun Label(text: String, sizeDp: Float, pick: (WidgetStyle.Palette) -> Color, bold: Boolean) {
        if (overlay) {
            ShadowText(text, sizeDp, pick(o), bold)
        } else {
            Text(
                text,
                style = TextStyle(
                    color = dayNight(transparency, pick, fade = false),
                    fontSize = sp(sizeDp),
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                ),
                maxLines = if (text.contains('\n')) 3 else 1,
            )
        }
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(corner)
            .background(tileBg)
            .clickable(actionStartActivity<MainActivity>()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Шапка: флаг валюты и флаг России
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(headerBg)
                .padding(vertical = (base * (if (compact) 0.04f else 0.05f)).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(model?.flag ?: Flags.emoji(code), style = TextStyle(fontSize = sp(flagSize)))
            if (compact && model != null && model.nominal != 1) {
                // В компактной плитке подписи нет — номинал показываем между флагами.
                Spacer(GlanceModifier.width((base * 0.04f).dp))
                Label("×${model.nominal}", flagSize * 0.55f, { it.sub }, bold = true)
                Spacer(GlanceModifier.width((base * 0.04f).dp))
            } else {
                Spacer(GlanceModifier.width((base * 0.16f).dp))
            }
            Text(model?.rubFlag ?: Flags.emoji("RUB"), style = TextStyle(fontSize = sp(flagSize)))
        }
        Box(GlanceModifier.fillMaxWidth().height(1.dp).background(divider)) {}

        if (model == null) {
            Box(
                modifier = GlanceModifier.fillMaxSize().padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Label(if (compact) "Нет\nданных" else "$code\nНет данных.\nОткройте приложение", smallSize * 1.2f, { it.sub }, bold = false)
            }
            return@Column
        }

        Column(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(model.rate, rateSize, { it.main }, bold = true)
            model.percent?.let {
                val pick: (WidgetStyle.Palette) -> Color = when (model.direction) {
                    Direction.UP -> { p -> p.up }
                    Direction.DOWN -> { p -> p.down }
                    else -> { p -> p.sub }
                }
                Label(it, percentSize, pick, bold = true)
            }
        }
        // Подпись: номинал (если не 1) и дата курса ЦБ
        if (!compact) {
            Box(modifier = GlanceModifier.padding(bottom = (base * 0.045f).dp)) {
                Label(listOfNotNull(model.nominalNote, model.date).joinToString(" · "), smallSize, { it.sub }, bold = false)
            }
        }
    }
}

/** Текст с тенью через обычный TextView: в Glance тени у текста нет. Размер — в dp. */
@Composable
private fun ShadowText(text: String, sizeDp: Float, color: Color, bold: Boolean) {
    val context = LocalContext.current
    val layout = if (bold) R.layout.widget_shadow_text_bold else R.layout.widget_shadow_text
    val views = RemoteViews(context.packageName, layout).apply {
        setTextViewText(R.id.text, text)
        setTextViewTextSize(R.id.text, TypedValue.COMPLEX_UNIT_DIP, sizeDp)
        setTextColor(R.id.text, color.toArgb())
        setInt(R.id.text, "setMaxLines", if (text.contains('\n')) 3 else 1)
    }
    AndroidRemoteViews(views)
}
