package com.example.pmmovil.ui.entrada

import com.example.pmmovil.data.model.EntradaFormSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El registro de la entrada viaja liviano (sin fotos) y las del daño suben después por la cola. Para que el servidor pueda
 * exigir «declara daño, avisa N» y el enlace público diga «faltan N», el request lleva cuántas va a subir la app. Si esto
 * saliera en cero con daño declarado, el servidor rechazaría toda recepción dañada.
 */
class EntradaSaveRequestTest {

    private val damaged = EntradaFormSnapshot(
        danada = true,
        danadaDescripcion = "Caja aplastada",
        localDamagePhotoPaths = listOf("/fotos/daño1.jpg", "/fotos/daño2.jpg")
    )

    @Test
    fun `damaged entry announces how many damage photos it will upload later`() {
        val request = damaged.toRequest(emptyList(), emptyList())

        assertEquals(2, request.fotosDanioPendientes)
    }

    @Test
    fun `photos never travel inside the save request`() {
        val request = damaged.toRequest(emptyList(), emptyList())

        assertTrue(request.fotos.isEmpty())
        assertTrue(request.fotosDanio.isEmpty())
    }

    @Test
    fun `entry without damage announces zero even with leftover local photos`() {
        // El usuario activó «Mercancía dañada», tomó fotos y lo apagó: esas fotos no se suben (ver enqueueDamagePhotosOf).
        val request = damaged.copy(danada = false, danadaDescripcion = "").toRequest(emptyList(), emptyList())

        assertEquals(0, request.fotosDanioPendientes)
    }

    @Test
    fun `an update with no new damage photos announces zero`() {
        val request = damaged.copy(localDamagePhotoPaths = emptyList()).toRequest(emptyList())

        assertEquals(0, request.fotosDanioPendientes)
    }
}
