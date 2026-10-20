package ru.madhare74.cbrrates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.madhare74.cbrrates.widget.WidgetStyle
import ru.madhare74.cbrrates.widget.WidgetStyle.withOpacity

class WidgetStyleTest {

    @Test
    fun `transparency maps to opacity`() {
        assertEquals(1f, WidgetStyle.opacity(0), 0.001f)
        assertEquals(0.6f, WidgetStyle.opacity(40), 0.001f)
        assertEquals(0f, WidgetStyle.opacity(100), 0.001f)
        assertEquals(1f, WidgetStyle.opacity(-5), 0.001f)
        assertEquals(0f, WidgetStyle.opacity(150), 0.001f)
    }

    @Test
    fun `background alpha follows transparency`() {
        assertEquals(0.3f, WidgetStyle.Night.tile.withOpacity(70).alpha, 0.01f)
        assertEquals(0f, WidgetStyle.Night.tile.withOpacity(100).alpha, 0.01f)
    }

    @Test
    fun `white text with shadow from 50 percent`() {
        assertFalse(WidgetStyle.isOverlay(10))
        assertFalse(WidgetStyle.isOverlay(49))
        assertTrue(WidgetStyle.isOverlay(50))
        assertTrue(WidgetStyle.isOverlay(100))
    }
}
