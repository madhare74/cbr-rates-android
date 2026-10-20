package ru.madhare74.cbrrates.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import ru.madhare74.cbrrates.domain.Direction
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.madhare74.cbrrates.CbrApp
import ru.madhare74.cbrrates.data.Rate
import ru.madhare74.cbrrates.data.RatesData
import ru.madhare74.cbrrates.domain.Flags
import ru.madhare74.cbrrates.domain.RatesList
import ru.madhare74.cbrrates.ui.CbrTheme

/** Выбор валюты для новой плитки (и при повторной настройке долгим нажатием). */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Если пользователь выйдет без выбора — виджет не добавляется.
        setResult(Activity.RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        val app = application as CbrApp
        setContent {
            CbrTheme {
                var data by remember { mutableStateOf<RatesData?>(null) }
                var failed by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    data = withContext(Dispatchers.IO) { app.repository.loadCached() }
                    if (data == null) {
                        val loaded = app.repository.loadCached() ?: run {
                            app.repository.load()
                            app.repository.loadCached()
                        }
                        data = loaded
                        failed = loaded == null
                    }
                }
                val scope = rememberCoroutineScope()
                ChooserScreen(
                    data = data,
                    failed = failed,
                    favorites = app.favoritesStore.load(),
                    initialCode = WidgetPrefs.code(this, appWidgetId) ?: WidgetModels.DEFAULT_CODE,
                    initialTransparency = WidgetPrefs.transparency(this, appWidgetId),
                    isNew = WidgetPrefs.code(this, appWidgetId) == null,
                    onSave = { code, transparency -> scope.launch { save(code, transparency) } },
                )
            }
        }
    }

    private suspend fun save(code: String, transparency: Int) {
        WidgetPrefs.save(this, appWidgetId, code, transparency)
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        RatesWidget().update(this, glanceId)
        RatesUpdateWorker.schedule(this)
        setResult(Activity.RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@Composable
internal fun ChooserScreen(
    data: RatesData?,
    failed: Boolean,
    favorites: List<String>,
    initialCode: String,
    initialTransparency: Int,
    isNew: Boolean,
    onSave: (code: String, transparency: Int) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf(initialCode) }
    var transparency by rememberSaveable { mutableIntStateOf(initialTransparency) }
    Scaffold(
        topBar = {
            Surface {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                ) {
                    Text("Настройка виджета", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Курс к рублю по данным ЦБ РФ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        bottomBar = {
            if (data != null) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { onSave(code, transparency) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag("widget_save"),
                    ) { Text(if (isNew) "Добавить виджет" else "Сохранить") }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                data != null -> {
                    TilePreviewSection(WidgetModels.build(data, code), code, transparency) { transparency = it }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text("Код или название", maxLines = 1) },
                        singleLine = true,
                    )
                    val sections = RatesList.build(data, favorites, query)
                    LazyColumn(Modifier.fillMaxSize().testTag("widget_chooser")) {
                        items(sections.favorites + sections.others, key = { it.rate.code }) { row ->
                            ChooserRow(row.rate, selected = row.rate.code == code) { code = it }
                        }
                    }
                }
                failed -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Не удалось загрузить курсы. Откройте приложение «Курсы ЦБ», когда появится интернет, и добавьте виджет снова.",
                        textAlign = TextAlign.Center,
                    )
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

/** Превью плитки на «обоях» и ползунок прозрачности фона. */
@Composable
private fun TilePreviewSection(model: TileModel?, code: String, transparency: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF3A6EA5), Color(0xFFD9A066), Color(0xFF2E2A28)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            TilePreview(model, code, transparency, Modifier.size(112.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Прозрачность фона", style = MaterialTheme.typography.titleSmall)
            Text(
                "$transparency %",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("transparency_value"),
            )
            Slider(
                value = transparency.toFloat(),
                onValueChange = { onChange((it / 5f).roundToInt() * 5) },
                valueRange = 0f..100f,
                modifier = Modifier.testTag("transparency_slider"),
            )
            if (WidgetStyle.isOverlay(transparency)) {
                Text(
                    "Текст белый с тенью — читается на любых обоях",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Копия плитки виджета на Compose — для превью. */
@Composable
internal fun TilePreview(model: TileModel?, code: String, transparency: Int, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val theme = if (dark) WidgetStyle.Night else WidgetStyle.Day
    val overlay = WidgetStyle.isOverlay(transparency)
    val text = if (overlay) WidgetStyle.Overlay else theme
    val shadow = if (overlay) Shadow(Color(0xB3000000), Offset(0f, 2f), 8f) else null
    fun style(size: Int, color: Color, bold: Boolean) = TextStyle(
        color = color,
        fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        shadow = shadow,
    )
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(with(WidgetStyle) { theme.tile.withOpacity(transparency) }),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(with(WidgetStyle) { theme.header.withOpacity(transparency) })
                .padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(Flags.emoji(code), fontSize = 18.sp)
            Spacer(Modifier.width(16.dp))
            Text(Flags.emoji("RUB"), fontSize = 18.sp)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (overlay) text.divider else with(WidgetStyle) { theme.divider.withOpacity(transparency) }),
        )
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(model?.rate ?: "—", style = style(22, text.main, true), maxLines = 1)
            model?.percent?.let {
                val color = when (model.direction) {
                    Direction.UP -> text.up
                    Direction.DOWN -> text.down
                    else -> text.sub
                }
                Text(it, style = style(17, color, true), maxLines = 1)
            }
        }
        Text(
            listOfNotNull(model?.nominalNote, model?.date).joinToString(" · "),
            style = style(9, text.sub, false),
            maxLines = 1,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun ChooserRow(rate: Rate, selected: Boolean, onPick: (String) -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .clickable { onPick(rate.code) }
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("pick_${rate.code}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Flags.emoji(rate.code), fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(rate.code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    rate.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        HorizontalDivider(Modifier.padding(start = 56.dp))
    }
}
