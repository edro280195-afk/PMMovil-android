package com.example.pmmovil.data.scanner

import com.example.pmmovil.data.model.MobileModuleIds
import java.net.URLEncoder

/** Lo que se puede hacer a partir de un código leído. */
enum class ScanActionId {
    /** Crear una entrada en bodega con los datos de la caja ya cargados. */
    CreateEntry,

    /** Dar salida a una caja o a una relación en caseta. */
    ExitGate,

    /** Buscar la caja en el patio. */
    FindInYard,

    /** Abrir la relación para supervisarla. */
    OpenRelation,

    /** Abrir la entrada en bodega. */
    OpenEntry,

    /** Abrir la revisión de la entrada. */
    ReviewEntry,

    /** Ver dónde está la entrada en el mapa de la bodega. */
    ShowOnMap,

    /** Registrar en caseta a un camión sin caja que llegó con pre-registro. */
    RegisterPreRegistro
}

/**
 * Una acción disponible para un código y la ruta del NavHost a la que lleva. La ruta ya trae el código escapado: las
 * pantallas de destino lo reciben como si se hubiera leído con el lector, y por eso leer nunca registra nada.
 *
 * @param modules basta tener [level] en cualquiera de estos módulos para verla.
 */
data class ScanAction(
    val id: ScanActionId,
    val route: String,
    val modules: List<Int>,
    val level: String = LEVEL_READ,
    val primary: Boolean = false
) {
    companion object {
        const val LEVEL_READ = "Lectura"
        const val LEVEL_ADD = "Agregar"
    }
}

object ScanActionResolver {

    /** Todas las acciones que existen para el tipo de código, sin mirar permisos. */
    fun actionsFor(code: ScannedCode): List<ScanAction> = when (code) {
        is ScannedCode.Caja -> listOf(
            ScanAction(
                ScanActionId.CreateEntry,
                "entrada_nueva?barcode=${encode(code.normalized)}",
                listOf(MobileModuleIds.WAREHOUSE_OPERATIONS),
                ScanAction.LEVEL_ADD
            ),
            exitGate(code.normalized),
            ScanAction(
                ScanActionId.FindInYard,
                "caseta_hub?barcode=${encode(code.normalized)}",
                listOf(MobileModuleIds.GATE)
            )
        )

        is ScannedCode.Relacion -> listOf(
            ScanAction(
                ScanActionId.OpenRelation,
                "relacion?barcode=${encode(code.normalized)}",
                listOf(MobileModuleIds.WAREHOUSE_RELATION)
            ),
            exitGate(code.normalized)
        )

        is ScannedCode.Carpeta -> entryActions(code.reference)
        is ScannedCode.Bulto -> entryActions(code.reference)

        is ScannedCode.PreRegistro -> listOf(
            ScanAction(
                ScanActionId.RegisterPreRegistro,
                "caseta_entrada?barcode=${encode(code.normalized)}",
                listOf(MobileModuleIds.GATE),
                ScanAction.LEVEL_ADD
            )
        )

        // Un gafete, una etiqueta de producto, una referencia dudosa o un código desconocido no abren nada por sí
        // solos: el gafete lleva una credencial y los demás necesitan un contexto que aquí no existe.
        ScannedCode.Gafete,
        is ScannedCode.ProductoCliente,
        is ScannedCode.Candidato,
        is ScannedCode.Desconocido -> emptyList()
    }

    /**
     * Las acciones que el puesto del usuario permite. Si queda una sola, se marca como principal; si quedan varias no
     * se adivina cuál es: sin consultar el estado de la pieza no se sabe si la caja entra o sale.
     */
    fun available(
        code: ScannedCode,
        hasPermission: (moduleId: Int, level: String) -> Boolean
    ): List<ScanAction> {
        val allowed = actionsFor(code).filter { action ->
            action.modules.any { module -> hasPermission(module, action.level) }
        }
        return if (allowed.size == 1) allowed.map { it.copy(primary = true) } else allowed
    }

    private fun entryActions(reference: String): List<ScanAction> = listOf(
        ScanAction(
            ScanActionId.ReviewEntry,
            "detalle/${encode(reference)}",
            listOf(MobileModuleIds.REVIEW)
        ),
        ScanAction(
            ScanActionId.OpenEntry,
            "entrada_editar/${encode(reference)}",
            listOf(MobileModuleIds.WAREHOUSE_OPERATIONS)
        ),
        ScanAction(
            ScanActionId.ShowOnMap,
            "mapa_bodega",
            listOf(MobileModuleIds.WAREHOUSE_OPERATIONS)
        )
    )

    // La pantalla de salida es una sola para salida con relación y manual; los dos permisos siguen separados.
    private fun exitGate(value: String) = ScanAction(
        ScanActionId.ExitGate,
        "salida_caseta?barcode=${encode(value)}",
        listOf(MobileModuleIds.GATE_RELATION_EXIT, MobileModuleIds.GATE_PHYSICAL_EXIT)
    )

    /** Escapa un valor para ir dentro de una ruta: `/` y `|` no pueden quedar sueltos y el espacio es `%20`. */
    internal fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

/**
 * La ruta de la pantalla de acciones. Quien reciba una lectura la manda siempre por [forScan] y no arma la ruta a
 * mano: así el gafete, que lleva una credencial, nunca viaja en una ruta (el NavHost la guarda en su estado y en el
 * estado de la actividad).
 */
object ScanActionsRoute {
    const val ARG_CODE = "code"

    /** Patrón que se registra en el NavHost. El código es opcional: sin él, la pantalla explica que era un gafete. */
    const val PATTERN = "acciones?$ARG_CODE={$ARG_CODE}"

    private const val WITHOUT_CODE = "acciones"

    // Una lectura no tiene por qué pasar de aquí: más largo ya es [ScannedCode.Desconocido] y no cabe en una ruta.
    private const val MAX_CODE_LENGTH = 200

    fun forScan(raw: String?): String {
        if (ScannedCodeClassifier.classify(raw) == ScannedCode.Gafete) return WITHOUT_CODE
        return "$WITHOUT_CODE?$ARG_CODE=${ScanActionResolver.encode(raw.orEmpty().trim().take(MAX_CODE_LENGTH))}"
    }

    /** Qué se leyó, según el argumento de la ruta. Sin argumento es un gafete: así lo deja [forScan]. */
    fun codeFromArgument(argument: String?): ScannedCode =
        argument?.let(ScannedCodeClassifier::classify) ?: ScannedCode.Gafete
}
