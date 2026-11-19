package com.example.pmmovil.ui.casetaentrada

import com.example.pmmovil.data.model.CasetaEntradaFotoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CasetaEntradaRemotePhotosTest {

    private val base = "https://api/"

    @Test
    fun `cada foto guardada es miniatura en la cuadricula, vista en el visor y original para el zoom`() {
        val fotos = CasetaEntradaRemotePhotos.desdeServidor(
            base,
            "E-021107/26",
            listOf(CasetaEntradaFotoInfo(indice = 2, nombreArchivo = "E-021107-26-0002.jpg", version = "ff-10")),
            maximo = 15
        )

        val foto = fotos.single()
        assertEquals("https://api/Foto/caseta/ver?casetaId=E-021107%2F26&indice=2&tam=mini&v=ff-10", foto.model)
        assertEquals("https://api/Foto/caseta/ver?casetaId=E-021107%2F26&indice=2&tam=vista&v=ff-10", foto.viewerModel)
        assertEquals("https://api/Foto/caseta/ver?casetaId=E-021107%2F26&indice=2&v=ff-10", foto.zoomModel)
        assertEquals(2, foto.sourceIndex)
        assertEquals("E-021107-26-0002.jpg", foto.label)
        // Ya no se baja ni se guarda el texto base64 de cada foto: solo direcciones.
        assertTrue(foto.base64.isEmpty())
    }

    @Test
    fun `respeta el maximo, el orden y nombra las fotos sin archivo`() {
        val infos = (1..20).map { CasetaEntradaFotoInfo(indice = it) }

        val fotos = CasetaEntradaRemotePhotos.desdeServidor(base, "E-1/26", infos, maximo = 15)

        assertEquals(15, fotos.size)
        assertEquals((1..15).toList(), fotos.map { it.sourceIndex })
        assertEquals("Guardada 4", fotos[3].label)
        assertEquals("remote-4-3", fotos[3].key)
    }

    @Test
    fun `sin version (servidor anterior) se usa una sola direccion, la del original, para bajarla una vez`() {
        val fotos = CasetaEntradaRemotePhotos.desdeServidor(
            base,
            "E-1/26",
            listOf(CasetaEntradaFotoInfo(indice = 1, version = null), CasetaEntradaFotoInfo(indice = 2, version = " ")),
            maximo = 15
        )

        assertEquals("https://api/Foto/caseta/ver?casetaId=E-1%2F26&indice=1", fotos[0].model)
        assertEquals(fotos[0].model, fotos[0].viewerModel)
        assertEquals(null, fotos[0].zoomModel)
        assertEquals("https://api/Foto/caseta/ver?casetaId=E-1%2F26&indice=2", fotos[1].model)
    }

    @Test
    fun `dos lecturas del mismo detalle dan fotos iguales (no cuentan como cambios sin guardar)`() {
        val infos = listOf(CasetaEntradaFotoInfo(indice = 1, version = "a-1"), CasetaEntradaFotoInfo(indice = 2, version = "b-2"))

        assertEquals(
            CasetaEntradaRemotePhotos.desdeServidor(base, "E-1/26", infos, 15),
            CasetaEntradaRemotePhotos.desdeServidor(base, "E-1/26", infos, 15)
        )
    }
}
