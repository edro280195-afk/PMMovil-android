package com.example.pmmovil.data.camera

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FotoParaSubirTest {

    @Test
    fun `el nombre de la preparada lleva el tamano y la fecha de la original`() {
        assertEquals("salida_1.jpg.123-456.jpg", FotoParaSubir.nombrePreparado("salida_1.jpg", 123, 456))
        assertEquals("salida_1.jpg", FotoParaSubir.nombreOriginalDe("salida_1.jpg.123-456.jpg"))
        assertEquals("a.b.c.jpg", FotoParaSubir.nombreOriginalDe("a.b.c.jpg.9-1700000000000.jpg"))
    }

    @Test
    fun `lo que no es una preparada no se confunde`() {
        assertNull(FotoParaSubir.nombreOriginalDe("salida_1.jpg"))
        assertNull(FotoParaSubir.nombreOriginalDe("salida_1.jpg.tmp"))
        assertNull(FotoParaSubir.nombreOriginalDe("x.jpg.abc.jpg"))
    }

    @Test
    fun `si la original cambia ya no corresponde la misma preparada`() {
        val carpeta = Files.createTempDirectory("fotos").toFile()
        try {
            val original = File(carpeta, "caja.jpg").apply { writeBytes(ByteArray(10)) }
            val antes = FotoParaSubir.preparadaDe(original)
            original.writeBytes(ByteArray(20))

            assertTrue(antes.name != FotoParaSubir.preparadaDe(original).name)
            assertEquals(File(carpeta, ".para-subir"), antes.parentFile)
        } finally {
            carpeta.deleteRecursively()
        }
    }

    @Test
    fun `olvidar borra la preparada y limpiar quita las huerfanas sin tocar las vigentes`() {
        val carpeta = Files.createTempDirectory("fotos").toFile()
        try {
            val viva = File(carpeta, "viva.jpg").apply { writeBytes(ByteArray(5)) }
            val olvidada = File(carpeta, "olvidada.jpg").apply { writeBytes(ByteArray(6)) }
            val preparadaViva = FotoParaSubir.preparadaDe(viva).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(3)) }
            val preparadaOlvidada = FotoParaSubir.preparadaDe(olvidada).apply { writeBytes(ByteArray(3)) }
            val huerfana = File(preparadaViva.parentFile, "ya-no-existe.jpg.1-2.jpg").apply { writeBytes(ByteArray(3)) }

            FotoParaSubir.olvidar(olvidada.path)
            assertFalse(preparadaOlvidada.exists())
            assertTrue(preparadaViva.exists())

            FotoParaSubir.limpiarHuerfanas(carpeta)
            assertFalse(huerfana.exists())
            assertTrue(preparadaViva.exists())
        } finally {
            carpeta.deleteRecursively()
        }
    }
}
