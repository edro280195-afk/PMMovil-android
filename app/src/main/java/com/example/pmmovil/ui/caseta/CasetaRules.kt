package com.example.pmmovil.ui.caseta

import com.example.pmmovil.data.model.CasetaDetalle
import com.example.pmmovil.data.scanner.OfficialQrParser

object CasetaRules {
    fun isReadyForExit(detalle: CasetaDetalle?): Boolean {
        detalle ?: return false
        return detalle.estatusCarga.equals("AUTORIZADA", ignoreCase = true) &&
            !detalle.estatus.contains("INCOMPLETA", ignoreCase = true) &&
            !detalle.estatus.contains("OCUPADA", ignoreCase = true)
    }

    /**
     * La relación de una lectura. La orden de carga (`01I26106418-1`) y la requisición de cruce
     * (`R-01I26106070  0000  101`) traen más texto que la relación: quedarse con el primer pedazo
     * dejaba `01I26106418-1` o `R-01I26106070`, que el servidor no encuentra.
     */
    fun normalizeScannedRelation(raw: String): String {
        OfficialQrParser.relacionOf(raw)?.let { return it }
        return raw
            .trim()
            .split(Regex("\\s+"))
            .firstOrNull()
            .orEmpty()
            .trim(',', ';')
    }
}
