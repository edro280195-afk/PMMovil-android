package com.example.pmmovil.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class FontScaleTest {

    @Test
    fun `normal and small letter is left alone`() {
        assertEquals(1f, cappedFontScale(1f, FIXED_SURFACE_MAX_FONT_SCALE), 0f)
        assertEquals(0.85f, cappedFontScale(0.85f, FIXED_SURFACE_MAX_FONT_SCALE), 0f)
        assertEquals(1.15f, cappedFontScale(1.15f, FIXED_SURFACE_MAX_FONT_SCALE), 0f)
    }

    @Test
    fun `big letter stops growing at the cap`() {
        assertEquals(1.15f, cappedFontScale(1.3f, FIXED_SURFACE_MAX_FONT_SCALE), 0f)
        assertEquals(1.15f, cappedFontScale(2f, FIXED_SURFACE_MAX_FONT_SCALE), 0f)
    }

    @Test
    fun `an invalid scale counts as normal`() {
        assertEquals(1f, cappedFontScale(Float.NaN, FIXED_SURFACE_MAX_FONT_SCALE), 0f)
        assertEquals(1f, cappedFontScale(0f, FIXED_SURFACE_MAX_FONT_SCALE), 0f)
        assertEquals(1f, cappedFontScale(-1f, FIXED_SURFACE_MAX_FONT_SCALE), 0f)
    }
}
