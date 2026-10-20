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

class CbrApp : Application() {
    val repository by lazy { RatesRepository(HttpCbrSource(), PrefsRatesCache(this)) }
    val favoritesStore by lazy { PrefsFavoritesStore(this) }

    val viewModelFactory: ViewModelProvider.Factory
        get() = viewModelFactory {
            initializer { RatesViewModel(repository, favoritesStore) }
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
