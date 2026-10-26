package com.example.pmmovil.ui.revision

import org.junit.Assert.assertEquals
import org.junit.Test

class VeredictoDeFotosTest {

    @Test
    fun `fotos en el servidor bastan aunque la lista de pantalla estuviera vieja`() {
        // El caso 01C263608160000: seis fotos ya subidas y ninguna pendiente.
        assertEquals(VeredictoDeFotos.Suficiente, veredictoDeFotos(totalEnServidor = 6, consultaCompleta = true, pendientesPorSubir = 0))
    }

    @Test
    fun `una foto por subir tambien basta`() {
        assertEquals(VeredictoDeFotos.Suficiente, veredictoDeFotos(totalEnServidor = 0, consultaCompleta = true, pendientesPorSubir = 1))
    }

    @Test
    fun `sin fotos en ninguna parte y con el servidor completo se bloquea`() {
        assertEquals(VeredictoDeFotos.SinFotos, veredictoDeFotos(totalEnServidor = 0, consultaCompleta = true, pendientesPorSubir = 0))
    }

    @Test
    fun `un cero con una consulta caida no se toma por falta de fotos`() {
        assertEquals(VeredictoDeFotos.SinVerificar, veredictoDeFotos(totalEnServidor = 0, consultaCompleta = false, pendientesPorSubir = 0))
    }

    @Test
    fun `una consulta caida no estorba si ya hay una foto que lo compensa`() {
        assertEquals(VeredictoDeFotos.Suficiente, veredictoDeFotos(totalEnServidor = 2, consultaCompleta = false, pendientesPorSubir = 0))
        assertEquals(VeredictoDeFotos.Suficiente, veredictoDeFotos(totalEnServidor = 0, consultaCompleta = false, pendientesPorSubir = 3))
    }
}
