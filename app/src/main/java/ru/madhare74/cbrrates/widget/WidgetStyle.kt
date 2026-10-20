package ru.madhare74.cbrrates.widget

import androidx.compose.ui.graphics.Color

/**
 * Цвета плитки — общие для настоящего виджета и превью в настройках.
 * Прозрачность фона задаётся пользователем: 0 % — непрозрачный, 100 % — без фона.
 */
object WidgetStyle {
    const val DEFAULT_TRANSPARENCY = 10

    /** С этой прозрачности текст становится белым с тенью — чтобы читался на любых обоях. */
    const val OVERLAY_FROM = 50

    data class Palette(
        val tile: Color,
        val header: Color,
        val divider: Color,
        val main: Color,
        val sub: Color,
        val up: Color,
        val down: Color,
    )

    val Day = Palette(
        tile = Color(0xFFF4F4F6),
        header = Color(0xFFE6E7EA),
        divider = Color(0xFFCBCCD0),
        main = Color(0xFF111114),
        sub = Color(0xFF6B6D73),
        up = Color(0xFF2E7D32),
        down = Color(0xFFC62828),
    )

    val Night = Palette(
        tile = Color(0xFF1E1E20),
        header = Color(0xFF2A2A2D),
        divider = Color(0xFF3C3C40),
        main = Color(0xFFFFFFFF),
        sub = Color(0xFF9A9BA1),
        up = Color(0xFF6FD47A),
        down = Color(0xFFFF7A70),
    )

    /** Цвета текста поверх обоев (белый с тенью). */
    val Overlay = Palette(
        tile = Color.Transparent,
        header = Color.Transparent,
        divider = Color(0x66FFFFFF),
        main = Color(0xFFFFFFFF),
        sub = Color(0xE6FFFFFF),
        up = Color(0xFF7CF08A),
        down = Color(0xFFFF8A80),
    )

    const val SHADOW = 0xB3000000.toInt()

    fun isOverlay(transparency: Int) = transparency >= OVERLAY_FROM

    /** Непрозрачность 0..1 из прозрачности в процентах. */
    fun opacity(transparency: Int): Float = 1f - transparency.coerceIn(0, 100) / 100f

    fun Color.withOpacity(transparency: Int): Color = copy(alpha = alpha * opacity(transparency))
}
