package ru.madhare74.cbrrates

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RatesUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Сохранённые образцы ответов ЦБ из src/sharedTest/fixtures (assets тестового APK). */
    private fun fixture(name: String): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { it.readBytes() }

    private val sampleSource = RatesSource { date ->
        if (date == null) fixture("cbr_current.xml") else fixture("cbr_previous.xml")
    }

    private val offlineSource = RatesSource { throw IOException("Нет сети") }

    private fun launch(source: RatesSource) {
        val viewModel = RatesViewModel(
            RatesRepository(source, InMemoryRatesCache()),
            InMemoryFavoritesStore(),
        )
        composeRule.setContent {
            CbrTheme { RatesApp(viewModel) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun screenOpensAndShowsList() {
        launch(sampleSource)
        composeRule.onNodeWithTag(Tags.HEADER).assertTextEquals("Курсы ЦБ РФ", "на 18.10.2026")
        composeRule.onNodeWithTag(Tags.LIST).assertIsDisplayed()
        composeRule.onNodeWithText("Избранное").assertIsDisplayed()
        composeRule.onNodeWithTag(Tags.row("USD")).assertIsDisplayed()
        composeRule.onNodeWithText("Доллар США").assertIsDisplayed()
        composeRule.onNodeWithText("81,1234\u00A0₽").assertIsDisplayed()
        composeRule.onNodeWithText("100\u00A0KZT = 16,4521\u00A0₽").assertIsDisplayed()
        composeRule.onNodeWithText("▲\u00A00,1358").assertIsDisplayed()
    }

    @Test
    fun searchFiltersList() {
        launch(sampleSource)
        composeRule.onNodeWithTag(Tags.SEARCH).performTextInput("тенге")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(Tags.row("KZT")).assertIsDisplayed()
        composeRule.onNodeWithTag(Tags.row("USD")).assertDoesNotExist()
        composeRule.onNodeWithTag(Tags.row("EUR")).assertDoesNotExist()

        composeRule.onNodeWithTag(Tags.SEARCH).performTextReplacement("gbp")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(Tags.row("GBP")).assertIsDisplayed()
        composeRule.onNodeWithTag(Tags.row("KZT")).assertDoesNotExist()
    }

    @Test
    fun converterOpensAndRecalculates() {
        launch(sampleSource)
        composeRule.onNodeWithTag(Tags.row("USD")).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Конвертер USD").assertIsDisplayed()
        composeRule.onNodeWithTag(Tags.CONVERTER_RUB).assertTextContains("81,12")

        composeRule.onNodeWithTag(Tags.CONVERTER_CURRENCY).performTextReplacement("2")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(Tags.CONVERTER_RUB).assertTextContains("162,25")

        composeRule.onNodeWithTag(Tags.CONVERTER_RUB).performTextReplacement("1000")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(Tags.CONVERTER_CURRENCY).assertTextContains("12,33")
    }

    @Test
    fun converterRespectsNominal() {
        launch(sampleSource)
        composeRule.onNodeWithTag(Tags.row("KZT")).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(Tags.CONVERTER_CURRENCY).assertTextContains("100")
        composeRule.onNodeWithTag(Tags.CONVERTER_RUB).assertTextContains("16,45")

        composeRule.onNodeWithTag(Tags.CONVERTER_CURRENCY).performTextReplacement("1000")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(Tags.CONVERTER_RUB).assertTextContains("164,52")
    }

    @Test
    fun errorShownWhenNoData() {
        launch(offlineSource)
        composeRule.onNodeWithTag(Tags.ERROR)
            .assertIsDisplayed()
            .assertTextContains("Нет соединения", substring = true)
        composeRule.onNodeWithTag(Tags.RETRY).assertIsDisplayed()
        composeRule.onNodeWithText("Повторить").assertIsDisplayed()
    }
}
