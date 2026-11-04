package com.example.pmmovil.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFormTest {

    @Test
    fun `phone upright stays compact`() {
        // Pixel 9 Pro XL de pie.
        assertEquals(HomeForm.Compact, homeFormFor(smallestWidthDp = 448, widthDp = 448, heightDp = 998))
    }

    @Test
    fun `phone on its side goes side by side but is never a tablet`() {
        // El mismo Pixel acostado: ancho de sobra, pero su ancho menor sigue siendo el de un teléfono.
        val form = homeFormFor(smallestWidthDp = 448, widthDp = 998, heightDp = 448)
        assertEquals(HomeForm.Side, form)
        assertFalse(form.isTablet)
    }

    @Test
    fun `tablet is told apart by its smallest width in both orientations`() {
        // Lenovo TB-X606F: 1200x1920 px a 240 dpi, o sea 800x1280 dp.
        assertEquals(HomeForm.TabletPortrait, homeFormFor(smallestWidthDp = 800, widthDp = 800, heightDp = 1280))
        assertEquals(HomeForm.TabletLandscape, homeFormFor(smallestWidthDp = 800, widthDp = 1280, heightDp = 800))
    }

    @Test
    fun `the 8 inch warehouse tablet gets the dense forms`() {
        // Ulefone Armor Pad Pro: 800x1280 px, que Android suele reportar como 600x960 dp.
        assertEquals(HomeForm.TabletPortraitDense, homeFormFor(smallestWidthDp = 600, widthDp = 600, heightDp = 960))
        assertEquals(HomeForm.TabletLandscapeDense, homeFormFor(smallestWidthDp = 600, widthDp = 960, heightDp = 600))
        // Aunque la barra de estado y la de navegación le quiten algo de alto, sigue siendo la densa.
        assertEquals(HomeForm.TabletPortraitDense, homeFormFor(smallestWidthDp = 600, widthDp = 600, heightDp = 896))
        assertEquals(HomeForm.TabletLandscapeDense, homeFormFor(smallestWidthDp = 600, widthDp = 960, heightDp = 552))
    }

    @Test
    fun `the 10 inch tablet keeps the regular forms even with the bars taking height`() {
        assertEquals(HomeForm.TabletPortrait, homeFormFor(smallestWidthDp = 800, widthDp = 800, heightDp = 1208))
        assertEquals(HomeForm.TabletLandscape, homeFormFor(smallestWidthDp = 800, widthDp = 1280, heightDp = 752))
    }

    @Test
    fun `a 600 dp device already counts as a tablet`() {
        assertTrue(homeFormFor(smallestWidthDp = 600, widthDp = 600, heightDp = 960).isTablet)
        assertFalse(homeFormFor(smallestWidthDp = 599, widthDp = 599, heightDp = 960).isTablet)
    }

    @Test
    fun `only the tablet forms are dense, and they say which way they lay out`() {
        assertFalse(HomeForm.Compact.dense)
        assertFalse(HomeForm.Side.dense)
        assertFalse(HomeForm.TabletPortrait.dense)
        assertTrue(HomeForm.TabletPortraitDense.dense)
        assertTrue(HomeForm.TabletPortraitDense.isTabletPortrait)
        assertFalse(HomeForm.TabletPortraitDense.isTabletLandscape)
        assertTrue(HomeForm.TabletLandscapeDense.isTabletLandscape)
        assertTrue(HomeForm.TabletLandscape.isTabletLandscape)
        assertFalse(HomeForm.Compact.isTabletPortrait)
    }

    @Test
    fun `only the tablet forms ask for a minimum page height and dense asks for less`() {
        assertEquals(0f, HomeForm.Compact.minHeight.value, 0f)
        assertEquals(0f, HomeForm.Side.minHeight.value, 0f)
        assertTrue(HomeForm.TabletPortrait.minHeight.value > 0f)
        assertTrue(HomeForm.TabletLandscape.minHeight.value > 0f)
        assertTrue(HomeForm.TabletPortraitDense.minHeight < HomeForm.TabletPortrait.minHeight)
        assertTrue(HomeForm.TabletLandscapeDense.minHeight < HomeForm.TabletLandscape.minHeight)
    }
}
