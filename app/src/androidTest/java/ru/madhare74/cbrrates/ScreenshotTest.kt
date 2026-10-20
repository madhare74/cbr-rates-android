package ru.madhare74.cbrrates

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.madhare74.cbrrates.data.InMemoryFavoritesStore
import ru.madhare74.cbrrates.data.InMemoryRatesCache
import ru.madhare74.cbrrates.data.RatesRepository
import ru.madhare74.cbrrates.data.RatesSource
import ru.madhare74.cbrrates.ui.CbrTheme
import ru.madhare74.cbrrates.ui.RatesApp
import ru.madhare74.cbrrates.ui.RatesViewModel
import ru.madhare74.cbrrates.ui.Tags
import java.io.File

/**
 * Снимки экранов при разном системном размере шрифта и в обеих темах — для визуальной
 * проверки вёрстки. Снимки складываются в папку files/screenshots приложения на эмуляторе.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Реальные по длине названия и номиналы ЦБ. */
    private val currencies = listOf(
        Triple("AUD", 1, "Австралийский доллар") to "52,3412",
        Triple("BYN", 1, "Белорусский рубль") to "25,6789",
        Triple("CNY", 1, "Юань") to "11,3456",
        Triple("CZK", 10, "Чешских крон") to "34,9012",
        Triple("DZD", 100, "Алжирских динаров") to "61,6335",
        Triple("EGP", 10, "Египетских фунтов") to "15,3569",
        Triple("ETB", 100, "Эфиопских быров") to "51,5168",
        Triple("EUR", 1, "Евро") to "94,5678",
        Triple("GBP", 1, "Фунт стерлингов Соединенного королевства") to "108,9012",
        Triple("IDR", 10000, "Индонезийских рупий") to "49,8765",
        Triple("KZT", 100, "Казахстанских тенге") to "16,4521",
        Triple("USD", 1, "Доллар США") to "81,1234",
        Triple("UZS", 10000, "Узбекских сумов") to "64,1234",
        Triple("VND", 10000, "Вьетнамских донгов") to "31,2468",
        Triple("XDR", 1, "СДР (специальные права заимствования)") to "1 111,2233",
    )

    private fun xml(date: String, shift: Double): ByteArray {
        val body = currencies.joinToString("") { (c, v) ->
            val value = v.replace(" ", "").replace(',', '.').toDouble() + shift * (c.first.hashCode() % 7 - 3)
            val text = String.format(java.util.Locale.ROOT, "%.4f", value).replace('.', ',')
            "<Valute ID=\"R\"><NumCode>0</NumCode><CharCode>${c.first}</CharCode><Nominal>${c.second}</Nominal>" +
                "<Name>${c.third}</Name><Value>$text</Value></Valute>"
        }
        return ("<?xml version=\"1.0\" encoding=\"utf-8\"?><ValCurs Date=\"$date\" name=\"Foreign Currency Market\">" +
            body + "</ValCurs>").toByteArray(Charsets.UTF_8)
    }

    private val source = RatesSource { date ->
        if (date == null) xml("20.10.2026", 0.0) else xml("18.10.2026", 0.1)
    }

    private fun launch(fontScale: Float, dark: Boolean) {
        val viewModel = RatesViewModel(RatesRepository(source, InMemoryRatesCache()), InMemoryFavoritesStore())
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                CbrTheme(darkTheme = dark) { RatesApp(viewModel) }
            }
        }
        composeRule.waitForIdle()
    }

    private fun save(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.filesDir, "screenshots").apply { mkdirs() }
        val file = File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun list_scale100_light() { launch(1.0f, dark = false); save("list_scale100_light") }

    @Test fun list_scale100_dark() { launch(1.0f, dark = true); save("list_scale100_dark") }

    @Test fun list_scale150_dark() { launch(1.5f, dark = true); save("list_scale150_dark") }

    @Test fun list_scale130_dark() { launch(1.3f, dark = true); save("list_scale130_dark") }

    @Test fun list_scale200_dark() { launch(2.0f, dark = true); save("list_scale200_dark") }

    @Test
    fun converter_scale150_dark() {
        launch(1.5f, dark = true)
        composeRule.onNodeWithTag(Tags.row("KZT")).performClick()
        save("converter_scale150_dark")
    }
}
