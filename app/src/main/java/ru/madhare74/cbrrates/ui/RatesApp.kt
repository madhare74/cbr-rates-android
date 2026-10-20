package ru.madhare74.cbrrates.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun RatesApp(viewModel: RatesViewModel) {
    val state by viewModel.state.collectAsState()
    var selectedCode by rememberSaveable { mutableStateOf<String?>(null) }

    val data = state.data
    val selectedRate = selectedCode?.let { code -> data?.current?.byCode(code) }

    if (selectedRate != null && data != null) {
        BackHandler { selectedCode = null }
        ConverterScreen(
            rate = selectedRate,
            date = data.current.date,
            onBack = { selectedCode = null },
        )
    } else {
        RatesScreen(
            state = state,
            onRefresh = viewModel::refresh,
            onQueryChange = viewModel::onQueryChange,
            onToggleFavorite = viewModel::toggleFavorite,
            onOpenConverter = { selectedCode = it },
        )
    }
}

@Composable
fun CbrTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val dark = darkTheme
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF9CC2FF))
        else -> lightColorScheme(primary = Color(0xFF1F5FA8))
    }
    MaterialTheme(colorScheme = colors, content = content)
}
