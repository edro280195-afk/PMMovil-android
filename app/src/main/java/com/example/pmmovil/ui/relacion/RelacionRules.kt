package com.example.pmmovil.ui.relacion

import com.example.pmmovil.data.model.SalidaEtiqueta
import com.example.pmmovil.data.model.SalidaRelacion

object RelacionRules {
    fun canAuthorize(relation: SalidaRelacion?, currentUser: String): Boolean {
        relation ?: return false
        val status = relation.estatus.uppercase()
        val loadStatus = relation.estatusCarga.uppercase()
        return !wasLoadedByCurrentUser(relation, currentUser) &&
            status != "EN PROCESO" &&
            !status.contains("ERROR") &&
            !status.contains("OCUPADA") &&
            loadStatus != "EN ESPERA" &&
            loadStatus != "CARGANDO" &&
            !(loadStatus.isBlank() && relation.usucargaMcia != "NO REQUERIDO") &&
            !loadStatus.contains("BLOQUEO") &&
            // La búsqueda del supervisor ya encuentra relaciones de cualquier estado: una que ya salió no se autoriza.
            loadStatus != "SALIDA"
    }

    fun statusLabel(relation: SalidaRelacion?, currentUser: String): String {
        relation ?: return ""
        val status = relation.estatus.uppercase()
        val loadStatus = relation.estatusCarga.uppercase()
        return when {
            wasLoadedByCurrentUser(relation, currentUser) -> "NECESITA OTRO ENCARGADO"
            status.contains("OCUPADA") -> "RELACIÓN OCUPADA"
            status.contains("ERROR") -> relation.estatus
            status == "EN PROCESO" -> "EN PROCESO"
            loadStatus == "EN ESPERA" || loadStatus == "CARGANDO" -> relation.estatusCarga
            loadStatus.isBlank() && relation.usucargaMcia != "NO REQUERIDO" -> "NO HA SIDO CARGADA"
            loadStatus.contains("BLOQUEO") -> "BLOQUEADA"
            loadStatus == "SALIDA" -> "YA SALIÓ"
            else -> relation.estatusCarga.ifBlank { relation.estatus }
        }
    }

    fun isBlocked(label: SalidaEtiqueta): Boolean {
        val history = label.historial.orEmpty()
        val last = history.split(",").map(String::trim).filter(String::isNotBlank).lastOrNull().orEmpty()
        return (history.contains("BLOQUEO") && !history.contains("DESBLOQUEO")) ||
            (last.contains("BLOQUEO") && !last.contains("DESBLOQUEO"))
    }

    private fun wasLoadedByCurrentUser(
        relation: SalidaRelacion,
        currentUser: String
    ): Boolean {
        val normalizedUser = currentUser.trim()
        return normalizedUser.isNotBlank() &&
            relation.usucargaMcia.trim().equals(normalizedUser, ignoreCase = true)
    }
}
