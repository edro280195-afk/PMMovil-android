package com.example.pmmovil.ui.salidacaseta

import com.example.pmmovil.data.model.ConfirmarSalidaCasetaResponse
import com.example.pmmovil.data.repository.EnvioComoArchivoNoDisponible
import com.example.pmmovil.data.repository.EnvioPorLotesNoDisponible
import com.example.pmmovil.data.repository.SalidaFotosServidor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SalidaFotosEnvioTest {

    /** Servidor de mentira: anota lo que recibe y puede quedarse "pensando" para probar la cancelación. */
    private class ServidorFalso(
        var aceptaArchivos: Boolean = true,
        var tieneLotes: Boolean = true
    ) : SalidaFotosServidor {
        val comoArchivo = mutableListOf<Pair<Int, Int>>() // (posición inicial, cuántas)
        val comoBase64 = mutableListOf<Pair<Int, Int>>()
        val confirmaciones = mutableListOf<Pair<Int?, Int>>() // (totalFotos, fotos en base64 dentro de la confirmación)
        var detenido: CompletableDeferred<Unit>? = null
        var intentosDeArchivo = 0

        override suspend fun subirFotosSalidaArchivos(
            relno: String,
            indiceInicial: Int,
            fotos: List<ByteArray>
        ): Result<ConfirmarSalidaCasetaResponse> {
            intentosDeArchivo++
            detenido?.await()
            if (!aceptaArchivos) return Result.failure(EnvioComoArchivoNoDisponible())
            comoArchivo += indiceInicial to fotos.size
            return Result.success(ConfirmarSalidaCasetaResponse(success = true))
        }

        override suspend fun subirFotosSalida(
            relno: String,
            indiceInicial: Int,
            fotosBase64: List<String>
        ): Result<ConfirmarSalidaCasetaResponse> {
            if (!tieneLotes) return Result.failure(EnvioPorLotesNoDisponible())
            assertTrue(fotosBase64.all { it.startsWith("data:image/jpeg;base64,") })
            comoBase64 += indiceInicial to fotosBase64.size
            return Result.success(ConfirmarSalidaCasetaResponse(success = true))
        }

        override suspend fun confirmarSalida(
            relno: String,
            usuario: String,
            observaciones: String,
            fotosBase64: List<String>?,
            totalFotos: Int?
        ): Result<ConfirmarSalidaCasetaResponse> {
            confirmaciones += totalFotos to (fotosBase64?.size ?: 0)
            return Result.success(ConfirmarSalidaCasetaResponse(success = true, message = "Salida registrada"))
        }
    }

    private fun envio(
        servidor: ServidorFalso,
        preparar: suspend (String) -> ByteArray? = { ruta -> ruta.toByteArray() }
    ) = SalidaFotosEnvio(servidor, "prueba", preparar = preparar, esperaAntesDeSubirMs = 0, registrar = {})

    private suspend fun esperarQue(condicion: () -> Boolean) {
        repeat(200) {
            if (condicion()) return
            delay(10)
        }
        assertTrue("No se cumplió a tiempo", condicion())
    }

    @Test
    fun `las fotos suben en segundo plano y al confirmar solo falta la confirmacion`() = runBlocking {
        val servidor = ServidorFalso()
        val envio = envio(servidor)
        val fotos = listOf("/a.jpg", "/b.jpg", "/c.jpg")

        envio.subirEnSegundoPlano(this, "01I1") { fotos }
        esperarQue { envio.enServidor(fotos) == 3 }
        val resultado = envio.confirmarConFotos("01I1", "guardia", "", fotos) {}

        assertEquals(listOf(1 to 3), servidor.comoArchivo)
        // Nada más que subir: la confirmación solo comprueba que estén las 3.
        assertEquals(listOf<Pair<Int?, Int>>(3 to 0), servidor.confirmaciones)
        assertTrue(resultado is SalidaFotosEnvio.Confirmacion.Terminada)
    }

    @Test
    fun `una foto tomada mientras sube la anterior entra sola en el siguiente lote`() = runBlocking {
        val servidor = ServidorFalso().apply { detenido = CompletableDeferred() }
        val envio = envio(servidor)
        val fotos = mutableListOf("/a.jpg", "/b.jpg", "/c.jpg")

        envio.subirEnSegundoPlano(this, "01I1") { fotos.toList() }
        esperarQue { servidor.intentosDeArchivo == 1 }
        fotos += "/d.jpg"
        envio.subirEnSegundoPlano(this, "01I1") { fotos.toList() }
        servidor.detenido!!.complete(Unit)
        esperarQue { envio.enServidor(fotos) == 4 }

        // El lote de 3 no se repite: la cuarta va sola en la posición 4.
        assertEquals(listOf(1 to 3, 4 to 1), servidor.comoArchivo)
    }

    @Test
    fun `al borrar una foto del medio se vuelven a mandar solo las que se recorrieron`() = runBlocking {
        val servidor = ServidorFalso()
        val envio = envio(servidor)
        val fotos = mutableListOf("/a.jpg", "/b.jpg", "/c.jpg", "/d.jpg")
        envio.subirEnSegundoPlano(this, "01I1") { fotos.toList() }
        esperarQue { envio.enServidor(fotos) == 4 }

        fotos.remove("/b.jpg")
        envio.olvidar("/b.jpg")
        val resultado = envio.confirmarConFotos("01I1", "guardia", "", fotos.toList()) {}

        assertEquals(listOf(1 to 4, 2 to 2), servidor.comoArchivo)
        // La confirmación dice 3: el servidor quita la cuarta que sobró.
        assertEquals(listOf<Pair<Int?, Int>>(3 to 0), servidor.confirmaciones)
        assertTrue(resultado is SalidaFotosEnvio.Confirmacion.Terminada)
    }

    @Test
    fun `un servidor que no recibe archivos recibe base64 y no se le vuelve a preguntar`() = runBlocking {
        val servidor = ServidorFalso(aceptaArchivos = false)
        val envio = envio(servidor)
        val fotos = (1..6).map { "/f$it.jpg" }

        envio.confirmarConFotos("01I1", "guardia", "", fotos) {}

        assertEquals(1, servidor.intentosDeArchivo)
        assertEquals(listOf(1 to 4, 5 to 2), servidor.comoBase64)
        assertEquals(listOf<Pair<Int?, Int>>(6 to 0), servidor.confirmaciones)
    }

    @Test
    fun `un servidor sin lotes recibe todas las fotos en la confirmacion, como antes`() = runBlocking {
        val servidor = ServidorFalso(aceptaArchivos = false, tieneLotes = false)
        val envio = envio(servidor)
        val fotos = listOf("/a.jpg", "/b.jpg")

        val resultado = envio.confirmarConFotos("01I1", "guardia", "", fotos) {}

        assertTrue(resultado is SalidaFotosEnvio.Confirmacion.Terminada)
        assertEquals(listOf<Pair<Int?, Int>>(null to 2), servidor.confirmaciones)
    }

    @Test
    fun `una foto que no se pudo preparar no deja confirmar y dice cual es`() = runBlocking {
        val servidor = ServidorFalso()
        val envio = envio(servidor, preparar = { ruta -> if (ruta == "/b.jpg") null else ruta.toByteArray() })

        val resultado = envio.confirmarConFotos("01I1", "guardia", "", listOf("/a.jpg", "/b.jpg")) {}

        assertTrue(resultado is SalidaFotosEnvio.Confirmacion.SinConfirmar)
        assertTrue((resultado as SalidaFotosEnvio.Confirmacion.SinConfirmar).mensaje.contains("foto 2"))
        assertTrue(servidor.confirmaciones.isEmpty())
    }

    @Test
    fun `confirmar detiene el envio de fondo y manda lo que falte sin repetir`() = runBlocking {
        val servidor = ServidorFalso().apply { detenido = CompletableDeferred() }
        val envio = envio(servidor)
        val fotos = listOf("/a.jpg", "/b.jpg")

        envio.subirEnSegundoPlano(this, "01I1") { fotos }
        esperarQue { servidor.intentosDeArchivo == 1 }
        // El envío de fondo se quedó "pensando"; el guardia confirma: se cancela y la confirmación lo manda ella misma.
        servidor.detenido = null
        val resultado = envio.confirmarConFotos("01I1", "guardia", "", fotos) {}

        assertTrue(resultado is SalidaFotosEnvio.Confirmacion.Terminada)
        assertEquals(listOf(1 to 2), servidor.comoArchivo)
        assertEquals(listOf<Pair<Int?, Int>>(2 to 0), servidor.confirmaciones)
    }

    @Test
    fun `sin fotos solo se confirma`() = runBlocking {
        val servidor = ServidorFalso()

        envio(servidor).confirmarConFotos("01I1", "guardia", "", emptyList()) {}

        assertTrue(servidor.comoArchivo.isEmpty())
        assertEquals(listOf<Pair<Int?, Int>>(null to 0), servidor.confirmaciones)
    }
}
