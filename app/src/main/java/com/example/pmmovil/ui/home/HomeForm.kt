package com.example.pmmovil.ui.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Desde este ancho menor (el que Android llama «smallest width») un aparato se trata como tableta. */
private const val TABLET_SMALLEST_WIDTH_DP = 600

/** Desde este ancho un teléfono acostado ya pone el visor y el Tablero lado a lado. */
private const val SIDE_BY_SIDE_WIDTH_DP = 720

/** Una tableta de pie con menos alto que éste es la de 8" (≈600x960 dp): usa las medidas densas. */
private const val DENSE_PORTRAIT_HEIGHT_DP = 1100

/** Una tableta acostada con menos alto que éste es la de 8" (≈960x600 dp): usa las medidas densas. */
private const val DENSE_LANDSCAPE_HEIGHT_DP = 700

/**
 * Cómo se acomoda el inicio nuevo según el aparato.
 *
 * El teléfono conserva las medidas de siempre. La tableta no estira el diseño del teléfono: llena la pantalla con
 * medidas propias, y por eso necesita saber cuánto alto mínimo le hace falta antes de ponerse a hacer scroll.
 *
 * Las tabletas oficiales de la bodega son Ulefone Armor Pad Pro de 8" (800x1280 px, ≈600x960 dp), así que la forma
 * «densa» no es un caso raro: es la que más se va a ver. Tiene las mismas piezas que la normal, más ajustadas, para
 * que el inicio quepa completo sin desplazarse.
 *
 * @param isTablet la tableta reparte el alto disponible entre sus bloques en lugar de apilarlos a su tamaño.
 * @param dense tableta chica: márgenes, letras y alturas más ajustados.
 * @param minHeight alto mínimo de toda la página (encabezado, contenido y pie) en tableta; por debajo de él la página
 * se desplaza en vez de apretar los bloques.
 */
enum class HomeForm(val isTablet: Boolean, val dense: Boolean = false, val minHeight: Dp = 0.dp) {
    /** Teléfono de pie: visor y Tablero uno bajo otro. */
    Compact(isTablet = false),

    /** Teléfono acostado: visor y Tablero lado a lado, con las medidas del teléfono. */
    Side(isTablet = false),

    /** Tableta de pie: el visor arriba como banda ancha, La Línea al centro y «Más» abajo. */
    TabletPortrait(isTablet = true, minHeight = 1180.dp),

    /** Tableta de pie de 8": lo mismo que la anterior, con medidas densas. */
    TabletPortraitDense(isTablet = true, dense = true, minHeight = 900.dp),

    /** Tableta acostada: el visor alto a la izquierda; La Línea y «Más» a la derecha. */
    TabletLandscape(isTablet = true, minHeight = 760.dp),

    /** Tableta acostada de 8": lo mismo que la anterior, con medidas densas. */
    TabletLandscapeDense(isTablet = true, dense = true, minHeight = 590.dp);

    /** El visor va como columna alta a la izquierda y el Tablero a la derecha. */
    val isTabletLandscape: Boolean get() = this == TabletLandscape || this == TabletLandscapeDense

    /** El visor va como banda ancha arriba y el Tablero debajo. */
    val isTabletPortrait: Boolean get() = this == TabletPortrait || this == TabletPortraitDense
}

/**
 * Decide la forma del inicio con las medidas de la ventana (en dp). La tableta se reconoce por su ancho menor, no por
 * el actual: así sigue siendo tableta al girarla y un teléfono acostado nunca lo es por muy ancho que quede. Dentro de
 * las tabletas, la de 8" se reconoce por su alto.
 */
fun homeFormFor(smallestWidthDp: Int, widthDp: Int, heightDp: Int): HomeForm = when {
    smallestWidthDp >= TABLET_SMALLEST_WIDTH_DP ->
        if (widthDp > heightDp) {
            if (heightDp < DENSE_LANDSCAPE_HEIGHT_DP) HomeForm.TabletLandscapeDense else HomeForm.TabletLandscape
        } else {
            if (heightDp < DENSE_PORTRAIT_HEIGHT_DP) HomeForm.TabletPortraitDense else HomeForm.TabletPortrait
        }

    widthDp >= SIDE_BY_SIDE_WIDTH_DP -> HomeForm.Side
    else -> HomeForm.Compact
}
