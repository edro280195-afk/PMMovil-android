package com.example.pmmovil.data.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/**
 * Calibracion contra fotos REALES de bodega. Opcional: solo corre si la variable de entorno PMM_STILL_GRAY apunta a una
 * carpeta con las 28 fotos del laboratorio ya en gris (NOMBRE.gray: dos enteros little-endian ancho y alto, y luego los
 * bytes de luminancia a resolucion completa). Los valores esperados son los que midio el servidor (FotoMejoraService,
 * OpenCV) sobre esas mismas fotos el 2-nov-2026; aqui se comprueba que el escalado y el medidor en Kotlin dan lo mismo,
 * porque el umbral 200 tiene que significar igual en la tableta y en el API.
 *
 * Los .gray se generan con OpenCV (cvtColor BGR2GRAY sobre cada .jpg). No se usa un decodificador de JPEG aqui porque
 * las pruebas unitarias de Android no traen javax.imageio.
 */
class StillSharpnessCalibrationTest {

    private val expected = mapOf(
        "borrosa_00" to 10.0, "borrosa_01" to 12.0, "borrosa_02" to 25.0, "borrosa_03" to 26.0, "borrosa_04" to 11.0,
        "borrosa_05" to 35.0, "borrosa_06" to 41.0, "borrosa_07" to 12.0, "borrosa_08" to 29.0, "borrosa_09" to 63.0,
        "borrosa_10" to 63.0, "borrosa_11" to 63.0, "borrosa_12" to 30.0, "borrosa_13" to 67.0,
        "buena_24" to 10972.0, "buena_25" to 3071.0, "buena_26" to 3693.0, "buena_27" to 3085.0,
        "oscura_14" to 1556.0, "oscura_15" to 1549.0, "oscura_16" to 2553.0, "oscura_17" to 1123.0, "oscura_18" to 605.0,
        "oscura_19" to 287.0, "oscura_20" to 534.0, "oscura_21" to 3438.0, "oscura_22" to 479.0, "oscura_23" to 1354.0
    )

    private fun readGray(file: File): GrayPlane {
        val bytes = file.readBytes()
        val header = ByteBuffer.wrap(bytes, 0, 8).order(ByteOrder.LITTLE_ENDIAN)
        val width = header.int
        val height = header.int
        return GrayPlane(width, height, bytes.copyOfRange(8, bytes.size))
    }

    @Test
    fun `el medidor en Kotlin coincide con OpenCV en fotos reales`() {
        val dir = System.getenv("PMM_STILL_GRAY")
        assumeTrue("PMM_STILL_GRAY no esta definida", dir != null && File(dir).isDirectory)

        var checked = 0
        for ((name, reference) in expected) {
            val file = File(dir, "$name.gray")
            if (!file.exists()) continue
            val measured = StillQuality.sharpness(readGray(file).scaledToLongSide(StillQuality.MEASURE_LONG_SIDE))
            println("CALIBRACION $name esperado=${reference.toInt()} kotlin=${measured.toInt()}")

            assertEquals(
                "$name: la decision de reintento debe ser la misma que la del servidor (esperado=$reference, kotlin=$measured)",
                reference < StillBurstPolicy.RETRY_BELOW,
                StillBurstPolicy.needsRetry(measured)
            )
            assertTrue(
                "$name: esperado=$reference kotlin=$measured",
                abs(measured - reference) <= max(5.0, 0.10 * reference)
            )
            checked++
        }
        assertTrue("se esperaban fotos en $dir", checked > 0)
    }

    /**
     * Fotos de 12 MP (3072x4080) tomadas con el Pixel el 2-nov-2026, ya reducidas a la mitad por el decodificador (como hace
     * BitmapFactory con inSampleSize=2) y guardadas en gris. El esperado es OpenCV reduciendo ese mismo plano a 1280 con
     * INTER_AREA. Esta prueba existe porque la primera version del medidor, con cajas enteras, daba de 2 a 3 veces menos
     * nitidez que OpenCV justo en estas fotos (la app registro 170 donde OpenCV mide 291).
     */
    @Test
    fun `el medidor en Kotlin coincide con OpenCV en fotos de 12 megapixeles`() {
        val dir = System.getenv("PMM_STILL_GRAY_PIXEL")
        assumeTrue("PMM_STILL_GRAY_PIXEL no esta definida", dir != null && File(dir).isDirectory)

        val expectedPixel = mapOf(
            "165348_288" to 291.0, "165353_911" to 382.0, "165358_293" to 310.0, "165404_594" to 471.0,
            "165407_390" to 398.0, "165418_850" to 417.0, "165424_612" to 584.0, "165439_117" to 260.0,
            "165443_119" to 972.0, "165447_349" to 890.0
        )
        var checked = 0
        for ((name, reference) in expectedPixel) {
            val file = File(dir, "$name.gray")
            if (!file.exists()) continue
            val measured = StillQuality.sharpness(readGray(file).scaledToLongSide(StillQuality.MEASURE_LONG_SIDE))
            println("CALIBRACION-PIXEL $name esperado=${reference.toInt()} kotlin=${measured.toInt()}")
            assertTrue(
                "$name: esperado=$reference kotlin=$measured",
                abs(measured - reference) <= 0.08 * reference
            )
            checked++
        }
        assertTrue("se esperaban fotos en $dir", checked > 0)
    }
}
