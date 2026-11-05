package com.example.pmmovil.ui.entrada

import com.example.pmmovil.data.model.EntradaFormSnapshot

/** Cómo se ve un campo del formulario de Entrada mientras se captura. */
enum class CaptureStatus {
    /** El campo no participa de la guía (no aplica a esta entrada). */
    NONE,

    /** Ya tiene dato: fondo verde claro y fijo, no parpadea. */
    CAPTURED,

    /** Falta y es el que sigue: contorno con destello suave. */
    NEXT,

    /** Falta, pero no es el que sigue: contorno marcado para que se vea que está por capturar. */
    PENDING
}

/** Los campos que la guía de captura sigue, en el orden en que aparecen en pantalla. */
enum class CaptureField {
    CONSECUTIVO,
    BODEGA,
    FECHA,
    CLIENTE,
    TRANSPORTE,
    CAJA,
    MANEJO,
    GUIA,
    HAZMAT_CLASE,
    HAZMAT_UN,
    DANO_DESCRIPCION,
    INBOND_VALOR,
    MERCANCIA,
    PESO
}

/**
 * Qué campos de la entrada ya están capturados, cuáles faltan y cuál es el que sigue. Es lo que alimenta el verde
 * (capturado) y el destello (por capturar) del formulario de Entrada: el personal de bodega veía una pantalla llena de
 * campos iguales y no sabía por dónde ir ni qué ya había llenado.
 *
 * El «siguiente» es solo el primer campo OBLIGATORIO que falta, en el orden de la pantalla. Los opcionales vacíos
 * (transporte, caja, guía, peso) se marcan como por capturar pero nunca destellan: así el destello no se queda pegado en
 * un dato que a veces no existe.
 */
class EntradaCaptureGuide private constructor(
    private val captured: Set<CaptureField>,
    private val relevant: Set<CaptureField>,
    /** El primer campo obligatorio que falta; `null` cuando ya está todo lo obligatorio. */
    val next: CaptureField?
) {

    fun status(field: CaptureField): CaptureStatus = when {
        field !in relevant -> CaptureStatus.NONE
        field in captured -> CaptureStatus.CAPTURED
        field == next -> CaptureStatus.NEXT
        else -> CaptureStatus.PENDING
    }

    companion object {
        /** Campos que bloquean el guardado, en el orden de la pantalla (ver [EntradaRules.validate]). */
        private val REQUIRED_ORDER = listOf(
            CaptureField.CONSECUTIVO,
            CaptureField.CLIENTE,
            CaptureField.MANEJO,
            CaptureField.HAZMAT_CLASE,
            CaptureField.HAZMAT_UN,
            CaptureField.DANO_DESCRIPCION,
            CaptureField.INBOND_VALOR,
            CaptureField.MERCANCIA
        )

        fun of(form: EntradaFormSnapshot): EntradaCaptureGuide {
            val relevant = buildSet {
                add(CaptureField.CONSECUTIVO)
                add(CaptureField.BODEGA)
                add(CaptureField.FECHA)
                add(CaptureField.CLIENTE)
                add(CaptureField.TRANSPORTE)
                add(CaptureField.CAJA)
                add(CaptureField.MANEJO)
                add(CaptureField.GUIA)
                if (form.hazmat) {
                    add(CaptureField.HAZMAT_CLASE)
                    add(CaptureField.HAZMAT_UN)
                }
                if (form.danada) add(CaptureField.DANO_DESCRIPCION)
                if (form.inBond) add(CaptureField.INBOND_VALOR)
                add(CaptureField.MERCANCIA)
                add(CaptureField.PESO)
            }

            val captured = buildSet {
                if (form.ref4.isNotBlank()) add(CaptureField.CONSECUTIVO)
                if (form.bodega.isNotBlank()) add(CaptureField.BODEGA)
                if (form.fecha.isNotBlank()) add(CaptureField.FECHA)
                if (form.codCliente.isNotBlank() || form.nomCliente.isNotBlank()) add(CaptureField.CLIENTE)
                if (form.codTransporte.isNotBlank() || form.nomTransporte.isNotBlank()) add(CaptureField.TRANSPORTE)
                if (form.cajaArribo.isNotBlank()) add(CaptureField.CAJA)
                if (form.manejo.isNotBlank()) add(CaptureField.MANEJO)
                if (form.guia.isNotBlank()) add(CaptureField.GUIA)
                if (form.claseHazmat.isNotBlank()) add(CaptureField.HAZMAT_CLASE)
                if (form.un.isNotBlank()) add(CaptureField.HAZMAT_UN)
                if (form.danadaDescripcion.isNotBlank()) add(CaptureField.DANO_DESCRIPCION)
                if (form.inBondVal.isNotBlank()) add(CaptureField.INBOND_VALOR)
                if (form.detalles.isNotEmpty()) add(CaptureField.MERCANCIA)
                if (form.pesoLbs > 0.0 || form.pesoKgs > 0.0) add(CaptureField.PESO)
            }

            val next = REQUIRED_ORDER.firstOrNull { it in relevant && it !in captured }
            return EntradaCaptureGuide(captured, relevant, next)
        }
    }
}
