package ru.madhare74.cbrrates.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.madhare74.cbrrates.data.FavoritesStore
import ru.madhare74.cbrrates.data.LoadResult
import ru.madhare74.cbrrates.data.RatesData
import ru.madhare74.cbrrates.data.RatesRepository
import ru.madhare74.cbrrates.domain.Messages

data class RatesUiState(
    /** Первая загрузка, данных ещё нет. */
    val isLoading: Boolean = false,
    /** Обновление поверх уже показанных данных. */
    val isRefreshing: Boolean = false,
    val data: RatesData? = null,
    /** Пометка над списком (показаны сохранённые данные). */
    val notice: String? = null,
    /** Ошибка на весь экран (данных нет). */
    val error: String? = null,
    val favorites: List<String> = emptyList(),
    val query: String = "",
)

class RatesViewModel(
    private val repository: RatesRepository,
    private val favoritesStore: FavoritesStore,
    /** Вызывается, когда сохранённые курсы или избранное изменились (обновить виджеты). */
    private val onDataChanged: () -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(
        RatesUiState(
            data = repository.loadCached(),
            favorites = favoritesStore.load(),
        ),
    )
    val state: StateFlow<RatesUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (loadJob?.isActive == true) return
        _state.update {
            it.copy(isLoading = it.data == null, isRefreshing = it.data != null, error = null)
        }
        loadJob = viewModelScope.launch {
            val result = repository.load()
            if (result is LoadResult.Fresh) onDataChanged()
            _state.update { current ->
                when (result) {
                    is LoadResult.Fresh -> current.copy(data = result.data, notice = null, error = null)
                    is LoadResult.Cached -> current.copy(
                        data = result.data,
                        notice = Messages.withSavedData(result.problem, result.data.current.date),
                        error = null,
                    )
                    is LoadResult.Failed -> {
                        val shown = current.data
                        if (shown != null) {
                            current.copy(notice = Messages.withSavedData(result.problem, shown.current.date))
                        } else {
                            current.copy(error = Messages.withoutData(result.problem))
                        }
                    }
                }.copy(isLoading = false, isRefreshing = false)
            }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun toggleFavorite(code: String) {
        val favorites = _state.value.favorites
        val updated = if (code in favorites) favorites - code else favorites + code
        favoritesStore.save(updated)
        onDataChanged()
        _state.update { it.copy(favorites = updated) }
    }
}
