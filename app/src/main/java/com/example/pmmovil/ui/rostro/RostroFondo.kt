package com.example.pmmovil.ui.rostro

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

/**
 * El fondo de las superficies de rostro.
 *
 * Tres capas, y la segunda es la que de verdad importa:
 *
 *  1. Un degradado vertical claro, alineado al tema PMMovil.
 *  2. Un halo radial que crece durante la captura -- el iluminador.
 *  3. **Grano.** Un degradado sobre un panel de 8 bits produce
 *     BANDEO: anillos concéntricos visibles donde el color cambia de nivel.
 *     Se nota muchísimo en pantallas baratas, y es exactamente lo que hace
 *     que una app se vea mal hecha aunque el diseño esté bien. Un ruido
 *     tenue encima rompe las bandas porque desordena el redondeo de cada
 *     píxel. Es el mismo truco que usan los degradados de iOS.
 *
 * El bitmap de ruido se genera UNA vez y se repite en mosaico, así que
 * cuesta 9 KB y un draw call.
 */
@Composable
fun RostroFondo(
    resplandor: Float,
    modifier: Modifier = Modifier,
    contenido: @Composable BoxScope.() -> Unit
) {
    val grano = rememberGrano()

    Box(
        // El tamaño del llamador debe ganar. En el login facial el panel está
        // embebido dentro del formulario y tiene una altura fija; aplicar
        // fillMaxSize después del modifier del llamador hacía que creciera,
        // cubriera la cabecera y empalmara el glifo con el resto del login.
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
            .drawBehind {
                // 1. Base con profundidad: limpia y clara, como el resto de
                //    las pantallas operativas de PMMovil.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            RostroTema.FondoAlto,
                            RostroTema.Fondo,
                            RostroTema.FondoBajo
                        )
                    )
                )

                // 2. El halo. Centrado un poco arriba del medio, que es donde
                //    va el glifo y donde está la cara.
                if (resplandor > 0.01f) {
                    val centro = Offset(size.width / 2f, size.height * 0.42f)
                    val radio = size.minDimension * 1.05f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                RostroTema.Brillo.copy(alpha = 0.22f * resplandor),
                                RostroTema.Brillo.copy(alpha = 0.08f * resplandor),
                                Color.Transparent
                            ),
                            center = centro,
                            radius = radio
                        ),
                        radius = radio,
                        center = centro
                    )
                }

                // 3. Cierre muy suave: conserva el foco sin oscurecer la
                //    pantalla ni romper el contraste de bodega.
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            RostroTema.FondoBajo.copy(alpha = 0.20f)
                        ),
                        center = Offset(size.width / 2f, size.height * 0.45f),
                        radius = size.maxDimension * 0.72f
                    )
                )

                // 4. Grano contra el bandeo.
                drawRect(brush = grano)
            },
        content = contenido
    )
}

/**
 * Mosaico de ruido monocromo, generado una sola vez.
 *
 * La semilla es fija a propósito: si cambiara entre recomposiciones el grano
 * "herviría" y se notaría muchísimo más que el bandeo que viene a arreglar.
 */
@Composable
private fun rememberGrano(): ShaderBrush {
    val imagen: ImageBitmap = remember {
        val lado = 128
        val aleatorio = Random(20260902)
        val pixeles = IntArray(lado * lado) {
            // Alfa muy bajo: el grano tiene que sentirse, no verse.
            val alfa = aleatorio.nextInt(0, 14)
            (alfa shl 24) or 0x00FFFFFF
        }
        Bitmap.createBitmap(pixeles, lado, lado, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    return remember(imagen) {
        ShaderBrush(ImageShader(imagen, TileMode.Repeated, TileMode.Repeated))
    }
}
