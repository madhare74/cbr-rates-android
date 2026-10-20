package ru.madhare74.cbrrates.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.madhare74.cbrrates.domain.Changes
import ru.madhare74.cbrrates.domain.Direction
import ru.madhare74.cbrrates.domain.Flags
import ru.madhare74.cbrrates.domain.RateChange
import ru.madhare74.cbrrates.domain.RateRow
import ru.madhare74.cbrrates.domain.RatesList
import ru.madhare74.cbrrates.domain.RuFormat

object Tags {
    const val HEADER = "header"
    const val LIST = "rate_list"
    const val SEARCH = "search_field"
    const val NOTICE = "notice"
    const val ERROR = "error_message"
    const val RETRY = "retry_button"
    const val CONVERTER_CURRENCY = "converter_currency"
    const val CONVERTER_RUB = "converter_rub"
    fun row(code: String) = "rate_row_$code"
    fun star(code: String) = "star_$code"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatesScreen(
    state: RatesUiState,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenConverter: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Две короткие строки вместо одной длинной: при крупном системном
                    // шрифте заголовок не переносится посреди фразы.
                    val date = state.data?.current?.date
                    Column(Modifier.semantics(mergeDescendants = true) {}.testTag(Tags.HEADER)) {
                        Text(
                            "Курсы ЦБ РФ",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (date != null) {
                            Text(
                                "на ${RuFormat.date(date)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (state.data != null) {
                SearchField(state.query, onQueryChange)
            }
            state.notice?.let { Notice(it) }
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                val data = state.data
                when {
                    data != null -> RatesListContent(state, onToggleFavorite, onOpenConverter)
                    state.error != null -> ErrorContent(state.error, onRefresh)
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag(Tags.SEARCH),
        placeholder = { Text("Код или название", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        textStyle = MaterialTheme.typography.bodyLarge,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Очистить поиск")
                }
            }
        },
        singleLine = true,
    )
}

@Composable
private fun Notice(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag(Tags.NOTICE),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.padding(top = 64.dp))
        Text(
            message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(Tags.ERROR),
        )
        Spacer(Modifier.padding(top = 16.dp))
        Button(onClick = onRetry, modifier = Modifier.testTag(Tags.RETRY)) {
            Text("Повторить")
        }
    }
}

@Composable
private fun RatesListContent(
    state: RatesUiState,
    onToggleFavorite: (String) -> Unit,
    onOpenConverter: (String) -> Unit,
) {
    val data = state.data ?: return
    val sections = RatesList.build(data, state.favorites, state.query)
    LazyColumn(
        Modifier
            .fillMaxSize()
            .testTag(Tags.LIST),
    ) {
        if (sections.isEmpty) {
            item(key = "empty") {
                Text(
                    "Ничего не найдено",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (sections.favorites.isNotEmpty()) {
            item(key = "h_fav") { SectionHeader("Избранное") }
            items(sections.favorites, key = { "fav_" + it.rate.code }) { row ->
                RateRowItem(row, onToggleFavorite, onOpenConverter)
            }
        }
        if (sections.others.isNotEmpty()) {
            item(key = "h_all") { SectionHeader("Все валюты") }
            items(sections.others, key = { "all_" + it.rate.code }) { row ->
                RateRowItem(row, onToggleFavorite, onOpenConverter)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RateRowItem(
    row: RateRow,
    onToggleFavorite: (String) -> Unit,
    onOpenConverter: (String) -> Unit,
) {
    val rate = row.rate
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onOpenConverter(rate.code) },
                    onLongClick = { onToggleFavorite(rate.code) },
                    onLongClickLabel = if (row.isFavorite) "Убрать из избранного" else "Добавить в избранное",
                )
                .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)
                .testTag(Tags.row(rate.code)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Flags.emoji(rate.code), fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            // Каждая строка занимает всю ширину колонки: код и название не делят место
            // с курсом, поэтому при крупном шрифте ничего не сжимается в столбик.
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        rate.code,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        rate.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    // Неразрывные пробелы: если не влезет, перенос будет только у «=».
                    Text(
                        RatesList.rateLabelNoBreak(rate),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    row.change?.let {
                        Spacer(Modifier.width(8.dp))
                        ChangeLabel(it)
                    }
                }
            }
            IconButton(
                onClick = { onToggleFavorite(rate.code) },
                modifier = Modifier.testTag(Tags.star(rate.code)),
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = if (row.isFavorite) "Убрать из избранного" else "Добавить в избранное",
                    tint = if (row.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun ChangeLabel(change: RateChange) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val color = when (change.direction) {
        Direction.UP -> if (dark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
        Direction.DOWN -> if (dark) Color(0xFFEF5350) else Color(0xFFC62828)
        Direction.SAME -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        Changes.label(change).replace(' ', ' '),
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        softWrap = false,
    )
}
