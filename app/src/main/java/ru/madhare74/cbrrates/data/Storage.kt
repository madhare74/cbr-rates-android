package ru.madhare74.cbrrates.data

import android.content.Context

/** Последние успешно загруженные ответы ЦБ (уже декодированный XML). */
data class CachedRates(
    val currentXml: String,
    val previousXml: String?,
)

interface RatesCache {
    fun read(): CachedRates?
    fun write(value: CachedRates)
}

class InMemoryRatesCache(private var value: CachedRates? = null) : RatesCache {
    override fun read(): CachedRates? = value
    override fun write(value: CachedRates) {
        this.value = value
    }
}

class PrefsRatesCache(context: Context) : RatesCache {
    private val prefs = context.getSharedPreferences("rates_cache", Context.MODE_PRIVATE)

    override fun read(): CachedRates? {
        val current = prefs.getString(KEY_CURRENT, null) ?: return null
        return CachedRates(current, prefs.getString(KEY_PREVIOUS, null))
    }

    override fun write(value: CachedRates) {
        prefs.edit()
            .putString(KEY_CURRENT, value.currentXml)
            .putString(KEY_PREVIOUS, value.previousXml)
            .apply()
    }

    private companion object {
        const val KEY_CURRENT = "current_xml"
        const val KEY_PREVIOUS = "previous_xml"
    }
}

val DEFAULT_FAVORITES = listOf("USD", "EUR", "CNY", "KZT")

/** Избранные валюты в порядке добавления. */
interface FavoritesStore {
    fun load(): List<String>
    fun save(codes: List<String>)
}

class InMemoryFavoritesStore(initial: List<String> = DEFAULT_FAVORITES) : FavoritesStore {
    private var codes = initial
    override fun load(): List<String> = codes
    override fun save(codes: List<String>) {
        this.codes = codes
    }
}

class PrefsFavoritesStore(context: Context) : FavoritesStore {
    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    override fun load(): List<String> {
        val raw = prefs.getString(KEY, null) ?: return DEFAULT_FAVORITES
        return raw.split(',').filter { it.isNotBlank() }
    }

    override fun save(codes: List<String>) {
        prefs.edit().putString(KEY, codes.joinToString(",")).apply()
    }

    private companion object {
        const val KEY = "codes"
    }
}
