package com.example.pmmovil.data.scanner

import com.example.pmmovil.data.model.TraceKind
import com.example.pmmovil.data.model.TraceResultDto

/**
 * De la ficha del servidor a las acciones de la pantalla. El servidor dice cuáles permite el estado de la pieza y en
 * qué orden conviene ofrecerlas; la app pone la ruta (la misma de [ScanActionResolver.build]) y decide con los permisos
 * del usuario cuáles se muestran. Una acción que el estado no permite (dar salida a una caja que ya salió) nunca llega.
 */
object TraceActionResolver {

    /** Los nombres de acción del servidor son los de [ScanActionId] en mayúsculas con guion bajo. */
    fun idOf(name: String): ScanActionId? = when (name) {
        "CREATE_ENTRY" -> ScanActionId.CreateEntry
        "EXIT_GATE" -> ScanActionId.ExitGate
        "FIND_IN_YARD" -> ScanActionId.FindInYard
        "OPEN_RELATION" -> ScanActionId.OpenRelation
        "OPEN_ENTRY" -> ScanActionId.OpenEntry
        "REVIEW_ENTRY" -> ScanActionId.ReviewEntry
        "SHOW_ON_MAP" -> ScanActionId.ShowOnMap
        "REGISTER_PREREGISTRO" -> ScanActionId.RegisterPreRegistro
        else -> null
    }

    /**
     * Las acciones que el servidor permite, en su orden, sin mirar permisos. Una acción cuyo destino no se puede
     * determinar con la ficha (por ejemplo, abrir «la» entrada cuando hay varias) no se incluye.
     *
     * @param rawCode lo que se leyó; solo lo usa el pre-registro, que lleva su QR completo a la pantalla de caseta.
     */
    fun actionsFor(result: TraceResultDto, rawCode: String): List<ScanAction> =
        result.actions.mapNotNull(::idOf).distinct().mapNotNull { id ->
            valueFor(id, result, rawCode)?.let { value -> ScanActionResolver.build(id, value) }
        }

    /**
     * Las acciones que el puesto del usuario permite. La primera que quede es la principal: el orden del servidor ya
     * lo decide el estado de la pieza (si la caja está lista para salir, la salida va primero).
     */
    fun available(
        result: TraceResultDto,
        rawCode: String,
        hasPermission: (moduleId: Int, level: String) -> Boolean
    ): List<ScanAction> =
        actionsFor(result, rawCode)
            .filter { action -> action.modules.any { module -> hasPermission(module, action.level) } }
            .mapIndexed { index, action -> if (index == 0) action.copy(primary = true) else action }

    private fun valueFor(id: ScanActionId, result: TraceResultDto, rawCode: String): String? {
        // Se usa el folio como está guardado en la base, no como se leyó: la etiqueta puede traer el cero de la
        // plantilla vieja (`E-017846/26`) y la base guardarlo sin él, o al revés.
        val casetaId = result.caja?.casetaId?.takeIf { it.isNotBlank() }
        val relno = (result.relacion?.relno ?: result.caja?.relacion)?.takeIf { it.isNotBlank() }
        return when (id) {
            ScanActionId.CreateEntry, ScanActionId.FindInYard -> casetaId
            ScanActionId.ExitGate -> if (result.kind == TraceKind.RELACION) relno ?: casetaId else casetaId ?: relno
            ScanActionId.OpenRelation -> relno
            ScanActionId.OpenEntry, ScanActionId.ReviewEntry -> result.entradas.singleOrNull()?.reference
            ScanActionId.ShowOnMap -> ""
            ScanActionId.RegisterPreRegistro -> rawCode.trim().takeIf { it.isNotEmpty() }
        }
    }
}
