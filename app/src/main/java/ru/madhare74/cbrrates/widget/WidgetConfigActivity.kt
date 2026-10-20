package ru.madhare74.cbrrates.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
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
                    current = WidgetPrefs.code(this, appWidgetId),
                    onPick = { code -> scope.launch { pick(code) } },
                )
            }
        }
    }

    private suspend fun pick(code: String) {
        WidgetPrefs.setCode(this, appWidgetId, code)
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        RatesWidget().update(this, glanceId)
        RatesUpdateWorker.schedule(this)
        setResult(Activity.RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@Composable
private fun ChooserScreen(
    data: RatesData?,
    failed: Boolean,
    favorites: List<String>,
    current: String?,
    onPick: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = {
            Surface {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                ) {
                    Text("Валюта для виджета", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Курс к рублю по данным ЦБ РФ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                data != null -> {
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
                            ChooserRow(row.rate, selected = row.rate.code == current, onPick)
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

@Composable
private fun ChooserRow(rate: Rate, selected: Boolean, onPick: (String) -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
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
