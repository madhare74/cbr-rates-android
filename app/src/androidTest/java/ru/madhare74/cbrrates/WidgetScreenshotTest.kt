package ru.madhare74.cbrrates

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.compose
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.madhare74.cbrrates.data.CachedRates
import ru.madhare74.cbrrates.data.CbrXmlParser
import ru.madhare74.cbrrates.data.PrefsRatesCache
import ru.madhare74.cbrrates.widget.RatesWidget
import java.io.File

/**
 * Рисует настоящую плитку виджета (RemoteViews) в картинку — для проверки оформления.
 * Снимки — в files/screenshots приложения.
 */
@RunWith(AndroidJUnit4::class)
class WidgetScreenshotTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target: Context = instrumentation.targetContext

    private fun fixture(name: String): ByteArray =
        instrumentation.context.assets.open(name).use { it.readBytes() }

    @Before
    fun saveSampleRates() {
        PrefsRatesCache(target).write(
            CachedRates(
                CbrXmlParser.decode(fixture("cbr_current.xml")),
                CbrXmlParser.decode(fixture("cbr_previous.xml")),
            ),
        )
    }

    private fun themed(dark: Boolean, fontScale: Float): Context {
        val config = Configuration(target.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            this.fontScale = fontScale
        }
        return target.createConfigurationContext(config)
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private fun render(name: String, code: String, size: DpSize, dark: Boolean, fontScale: Float = 1f, transparency: Int = 10) {
        val context = themed(dark, fontScale)
        val views = runBlocking { RatesWidget(codeOverride = code, transparencyOverride = transparency).compose(context, size = size) }
        val metrics = context.resources.displayMetrics
        fun px(dp: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, metrics).toInt()
        val w = px(size.width.value)
        val h = px(size.height.value)
        val pad = px(12f)
        var bitmap: Bitmap? = null
        instrumentation.runOnMainSync {
            val parent = FrameLayout(context)
            val view = views.apply(context, parent)
            parent.addView(view, FrameLayout.LayoutParams(w, h))
            parent.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
            )
            parent.layout(0, 0, w, h)
            // Фон «обоев», чтобы видеть края и прозрачность плитки
            val out = Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            // «Обои» с градиентом — чтобы оценить прозрачность
            val paint = android.graphics.Paint().apply {
                shader = android.graphics.LinearGradient(
                    0f, 0f, out.width.toFloat(), out.height.toFloat(),
                    intArrayOf(Color.rgb(58, 110, 165), Color.rgb(217, 160, 102), Color.rgb(46, 42, 40)),
                    null, android.graphics.Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
            canvas.translate(pad.toFloat(), pad.toFloat())
            parent.draw(canvas)
            bitmap = out
        }
        val dir = File(target.filesDir, "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(bitmap!!.width > 0)
    }

    @Test fun widget_usd_2x2_dark() = render("widget_usd_2x2_dark", "USD", DpSize(150.dp, 150.dp), dark = true)

    @Test fun widget_eur_2x2_dark() = render("widget_eur_2x2_dark", "EUR", DpSize(150.dp, 150.dp), dark = true)

    @Test fun widget_kzt_2x2_light() = render("widget_kzt_2x2_light", "KZT", DpSize(150.dp, 150.dp), dark = false)

    @Test fun widget_usd_small_dark() = render("widget_usd_small_dark", "USD", DpSize(100.dp, 100.dp), dark = true)

    @Test fun widget_usd_wide_dark() = render("widget_usd_wide_dark", "USD", DpSize(260.dp, 130.dp), dark = true)

    @Test fun widget_usd_t40_dark() =
        render("widget_usd_t40_dark", "USD", DpSize(150.dp, 150.dp), dark = true, transparency = 40)

    @Test fun widget_eur_t70_dark() =
        render("widget_eur_t70_dark", "EUR", DpSize(150.dp, 150.dp), dark = true, transparency = 70)

    @Test fun widget_usd_t100_light() =
        render("widget_usd_t100_light", "USD", DpSize(150.dp, 150.dp), dark = false, transparency = 100)

    // 1×1 на Samsung S24 Ultra (сетка 4×N): ячейка ≈ 80×95 dp
    @Test fun widget_1x1_usd_dark() = render("widget_1x1_usd_dark", "USD", DpSize(80.dp, 92.dp), dark = true)

    @Test fun widget_1x1_eur_t70() = render("widget_1x1_eur_t70", "EUR", DpSize(80.dp, 92.dp), dark = true, transparency = 70)

    @Test fun widget_1x1_kzt_light() = render("widget_1x1_kzt_light", "KZT", DpSize(80.dp, 92.dp), dark = false)

    @Test fun widget_1x1_small_bigfont() =
        render("widget_1x1_small_bigfont", "XDR", DpSize(68.dp, 76.dp), dark = true, fontScale = 1.5f)

    @Test fun widget_usd_bigfont_dark() =
        render("widget_usd_bigfont_dark", "USD", DpSize(150.dp, 150.dp), dark = true, fontScale = 1.5f)
}
