package com.example.pmmovil.ui.salidacaseta

import com.example.pmmovil.ui.components.EvidenceGalleryItem
import com.example.pmmovil.ui.components.evidenceGalleryStartIndex
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SalidaFotosGaleriaTest {

    @Test
    fun `las fotos de salida se numeran en orden y su id es la ruta`() {
        val items = SalidaFotosGaleria.deSalida(listOf("/fotos/a.jpg", "/fotos/b.jpg", "/fotos/c.jpg"))

        assertEquals(listOf("/fotos/a.jpg", "/fotos/b.jpg", "/fotos/c.jpg"), items.map { it.id })
        assertEquals(listOf("Foto 1", "Foto 2", "Foto 3"), items.map { it.label })
        assertEquals(File("/fotos/b.jpg"), items[1].model)
    }

    @Test
    fun `al quitar una foto del medio las demas conservan su id y se renumeran`() {
        val antes = SalidaFotosGaleria.deSalida(listOf("/a.jpg", "/b.jpg", "/c.jpg"))
        val despues = SalidaFotosGaleria.deSalida(listOf("/a.jpg", "/c.jpg"))

        // El visor reconoce la foto por el id: la «c» sigue siendo la misma aunque ahora sea la segunda.
        assertEquals("/c.jpg", antes[2].id)
        assertEquals("/c.jpg", despues[1].id)
        assertEquals(listOf("Foto 1", "Foto 2"), despues.map { it.label })
    }

    @Test
    fun `sin fotos no hay nada que mostrar`() {
        assertTrue(SalidaFotosGaleria.deSalida(emptyList()).isEmpty())
        assertTrue(SalidaFotosGaleria.deLlegada(emptyList()) { _, _ -> "x" }.isEmpty())
    }

    @Test
    fun `las fotos de llegada usan la url de cada indice del servidor`() {
        val items = SalidaFotosGaleria.deLlegada(listOf(2, 3, 5)) { indice, tamano ->
            "http://api/Foto/caseta/ver?indice=$indice&tam=${tamano.parametro ?: "original"}"
        }

        assertEquals(listOf("llegada-2", "llegada-3", "llegada-5"), items.map { it.id })
        assertEquals(listOf("Llegada 1", "Llegada 2", "Llegada 3"), items.map { it.label })
        // En grande la vista; en la tira la miniatura; el original solo para el zoom.
        assertEquals("http://api/Foto/caseta/ver?indice=5&tam=vista", items[2].model)
        assertEquals("http://api/Foto/caseta/ver?indice=5&tam=mini", items[2].thumbModel)
        assertEquals("http://api/Foto/caseta/ver?indice=5&tam=original", items[2].zoomModel)
    }

    @Test
    fun `las fotos de salida tomadas en la tableta no piden original aparte`() {
        val item = SalidaFotosGaleria.deSalida(listOf("/fotos/a.jpg")).single()

        assertEquals(item.model, item.thumbModel)
        assertEquals(null, item.zoomModel)
    }

    @Test
    fun `el visor abre en la foto tocada y si no la encuentra en la primera`() {
        val items = listOf(
            EvidenceGalleryItem("a", "ma", "Foto 1"),
            EvidenceGalleryItem("b", "mb", "Foto 2"),
            EvidenceGalleryItem("c", "mc", "Foto 3")
        )

        assertEquals(0, evidenceGalleryStartIndex(items, "a"))
        assertEquals(2, evidenceGalleryStartIndex(items, "c"))
        assertEquals(0, evidenceGalleryStartIndex(items, "ya-borrada"))
        assertEquals(0, evidenceGalleryStartIndex(emptyList(), "a"))
    }
}
