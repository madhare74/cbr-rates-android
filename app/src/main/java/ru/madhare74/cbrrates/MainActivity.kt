package ru.madhare74.cbrrates

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ru.madhare74.cbrrates.data.HttpCbrSource
import ru.madhare74.cbrrates.data.PrefsFavoritesStore
import ru.madhare74.cbrrates.data.PrefsRatesCache
import ru.madhare74.cbrrates.data.RatesRepository
import ru.madhare74.cbrrates.ui.CbrTheme
import ru.madhare74.cbrrates.ui.RatesApp
import ru.madhare74.cbrrates.ui.RatesViewModel
import ru.madhare74.cbrrates.widget.RatesUpdateWorker
import ru.madhare74.cbrrates.widget.RatesWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CbrApp : Application() {
    val repository by lazy { RatesRepository(HttpCbrSource(), PrefsRatesCache(this)) }
    val favoritesStore by lazy { PrefsFavoritesStore(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Расписание фонового обновления — только если на экране есть виджеты.
        if (RatesUpdateWorker.hasWidgets(this)) RatesUpdateWorker.schedule(this)
    }

    /** Перерисовать виджеты после свежей загрузки в приложении. */
    fun updateWidgets() {
        appScope.launch { runCatching { RatesWidget().updateAll(this@CbrApp) } }
    }

    val viewModelFactory: ViewModelProvider.Factory
        get() = viewModelFactory {
            initializer { RatesViewModel(repository, favoritesStore, onDataChanged = ::updateWidgets) }
        }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as CbrApp
        setContent {
            CbrTheme {
                val viewModel: RatesViewModel = viewModel(factory = app.viewModelFactory)
                RatesApp(viewModel)
            }
        }
    }
}
