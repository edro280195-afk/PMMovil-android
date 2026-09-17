package com.example.pmmovil.ui.caseta

import com.example.pmmovil.data.model.CasetaDetalle

object CasetaRules {
    fun isReadyForExit(detalle: CasetaDetalle?): Boolean {
        detalle ?: return false
        return detalle.estatusCarga.equals("AUTORIZADA", ignoreCase = true) &&
            !detalle.estatus.contains("INCOMPLETA", ignoreCase = true) &&
            !detalle.estatus.contains("OCUPADA", ignoreCase = true)
    }

    fun normalizeScannedRelation(raw: String): String {
        return raw
            .trim()
            .split(Regex("\\s+"))
            .firstOrNull()
            .orEmpty()
            .trim(',', ';')
    }
}
