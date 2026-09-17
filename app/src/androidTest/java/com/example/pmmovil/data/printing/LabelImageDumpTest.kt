package com.example.pmmovil.data.printing

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Vuelca la etiqueta de entrada renderizada (PNG) y su ZPL ^GF a
 * <app>/files/zpl-preview/ del dispositivo, para:
 *  - abrir el PNG y revisar el diseno visualmente, y
 *  - pegar el .zpl en https://labelary.com/viewer.html (12 dpmm, 4x6) y ver el render exacto
 *    que imprimira la ZT411, sin gastar etiqueta.
 *
 * Ejecutar:  gradlew :app:connectedDebugAndroidTest --tests *LabelImageDumpTest
 * Recuperar: adb pull /sdcard/Android/data/com.example.pmmovil/files/zpl-preview
 *
 * No usa Hilt: LabelBitmapRenderer y LabelZplFactory se construyen directo (solo dependen
 * de un Context y del renderer, respectivamente).
 */
@RunWith(AndroidJUnit4::class)
class LabelImageDumpTest {

    @Test
    fun dumpEntradaLabel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val renderer = LabelBitmapRenderer(context)
        val preferences = context.getSharedPreferences(
            "LabelImageDumpTestPreferences",
            android.content.Context.MODE_PRIVATE
        )
        val layoutPreferences = ZebraLabelLayoutPreferences(preferences)
        val factory = LabelZplFactory(renderer, layoutPreferences)

        val carpeta = ZebraLabelData(
            numeroEntrada = "355910-00",
            fechaArribo = "06/02/2026",
            cliente = "ALLNEX MEXICO, S. DE R.L. DE C.V.",
            caja = "PMM-TEST",
            guia = "472430009",
            ubicaciones = "O3",
            qrValue = "50C20355910",
            tipoEtiqueta = "CARPETA"
        )
        val parcial = carpeta.copy(tipoEtiqueta = "", parcial = "6/6", qrValue = "50C20355910 6-6")

        val dir = File(context.getExternalFilesDir(null), "zpl-preview").apply { mkdirs() }

        val layout = ZebraLabelLayout.reference()
        writePng(renderer.render(carpeta, layout), File(dir, "entrada-carpeta.png"))
        writePng(renderer.render(parcial, layout), File(dir, "entrada-parcial.png"))

        val zplZ64 = runBlocking { factory.buildLabels(listOf(carpeta, parcial)) }
        File(dir, "entrada-label.zpl").writeText(zplZ64)

        // Version sin comprimir (hex) por si se quiere descartar la compresion en Labelary.
        val zplHex = runBlocking { factory.buildLabels(listOf(carpeta), compress = false) }
        File(dir, "entrada-label-hex.zpl").writeText(zplHex)

        println("Dump ^GF en: ${dir.absolutePath} (Z64=${zplZ64.length} bytes, hex=${zplHex.length} bytes)")
    }

    private fun writePng(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
