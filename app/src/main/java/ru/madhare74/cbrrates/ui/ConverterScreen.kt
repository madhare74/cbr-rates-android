package ru.madhare74.cbrrates.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.madhare74.cbrrates.data.Rate
import ru.madhare74.cbrrates.domain.Converter
import ru.madhare74.cbrrates.domain.Flags
import ru.madhare74.cbrrates.domain.RatesList
import ru.madhare74.cbrrates.domain.RuFormat
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(rate: Rate, date: LocalDate, onBack: () -> Unit) {
    var currencyText by rememberSaveable(rate.code) { mutableStateOf(rate.nominal.toString()) }
    var rubText by rememberSaveable(rate.code) { mutableStateOf(RuFormat.amount(rate.value)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Конвертер ${rate.code}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("${Flags.emoji(rate.code)} ${rate.name}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Курс ЦБ на ${RuFormat.date(date)}:\n${RatesList.rateLabelNoBreak(rate)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = currencyText,
                onValueChange = { input ->
                    val clean = RuFormat.sanitizeInput(input)
                    currencyText = clean
                    rubText = RuFormat.parseAmount(clean)
                        ?.let { RuFormat.amount(Converter.toRub(it, rate)) }
                        ?: ""
                },
                label = { Text("Сумма, ${rate.code}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(Tags.CONVERTER_CURRENCY),
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = rubText,
                onValueChange = { input ->
                    val clean = RuFormat.sanitizeInput(input)
                    rubText = clean
                    currencyText = RuFormat.parseAmount(clean)
                        ?.let { RuFormat.amount(Converter.fromRub(it, rate)) }
                        ?: ""
                },
                label = { Text("Сумма, ₽ (RUB)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(Tags.CONVERTER_RUB),
            )
        }
    }
}
