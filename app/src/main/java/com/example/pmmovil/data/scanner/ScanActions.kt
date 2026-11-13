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

    /** Ir a «Cargar mercancía» con la relación ya puesta, para escanear sus bultos. */
    LoadCargo,

    /** Abrir la entrada en bodega. */
    OpenEntry,

    /** Abrir la revisión de la entrada. */
    ReviewEntry,

    /**
     * Registrar que el revisador agarró la carpeta física: queda a su nombre y el Monitor la ve, sin abrir la revisión ni
     * arrancar el cronómetro. A diferencia de las demás, esta acción ESCRIBE: la ejecuta quien la toca ([ScanAction.command]),
     * no navega a ninguna pantalla.
     */
    TakeFolder,

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
    val primary: Boolean = false,
    /** La acción no navega: la pantalla la ejecuta (hoy solo «Tomar carpeta»). [route] va vacía. */
    val command: Boolean = false
) {
    companion object {
        const val LEVEL_READ = "Lectura"
        const val LEVEL_ADD = "Agregar"
        const val LEVEL_MODIFY = "Modificar"
    }
}

object ScanActionResolver {

    /** Todas las acciones que existen para el tipo de código, sin mirar permisos. */
    fun actionsFor(code: ScannedCode): List<ScanAction> = when (code) {
        is ScannedCode.Caja -> listOf(
            build(ScanActionId.CreateEntry, code.normalized),
            build(ScanActionId.ExitGate, code.normalized),
            build(ScanActionId.FindInYard, code.normalized)
        )

        // Sin red no se sabe si ya se cargó: se ofrecen las tres y ninguna es la principal. Con servidor, la ficha
        // manda solo la que corresponde (ver TraceActionResolver).
        is ScannedCode.Relacion -> listOf(
            build(ScanActionId.LoadCargo, code.normalized),
            build(ScanActionId.OpenRelation, code.normalized),
            build(ScanActionId.ExitGate, code.normalized)
        )

        is ScannedCode.Carpeta -> entryActions(code.reference)
        is ScannedCode.Bulto -> entryActions(code.reference)

        is ScannedCode.PreRegistro -> listOf(build(ScanActionId.RegisterPreRegistro, code.normalized))

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
        build(ScanActionId.ReviewEntry, reference),
        build(ScanActionId.TakeFolder, reference),
        build(ScanActionId.OpenEntry, reference),
        build(ScanActionId.ShowOnMap, reference)
    )

    /**
     * La acción [id] apuntando a [value], que es lo que la pantalla de destino recibe como si lo hubiera leído el lector:
     * el folio de la caja, la relación, la referencia de la entrada o el QR del pre-registro, según la acción. El mapa
     * no recibe nada. Es el único lugar que sabe a qué ruta va cada acción y con qué permiso, para que la hoja sin red y
     * la ficha con servidor no puedan diferir.
     */
    internal fun build(id: ScanActionId, value: String): ScanAction = when (id) {
        ScanActionId.CreateEntry -> ScanAction(
            id,
            "entrada_nueva?barcode=${encode(value)}",
            listOf(MobileModuleIds.WAREHOUSE_OPERATIONS),
            ScanAction.LEVEL_ADD
        )

        // La pantalla de salida es una sola para salida con relación y manual; los dos permisos siguen separados.
        ScanActionId.ExitGate -> ScanAction(
            id,
            "salida_caseta?barcode=${encode(value)}",
            listOf(MobileModuleIds.GATE_RELATION_EXIT, MobileModuleIds.GATE_PHYSICAL_EXIT)
        )

        ScanActionId.FindInYard -> ScanAction(
            id,
            "caseta_hub?barcode=${encode(value)}",
            listOf(MobileModuleIds.GATE)
        )

        ScanActionId.OpenRelation -> ScanAction(
            id,
            "relacion?barcode=${encode(value)}",
            listOf(MobileModuleIds.WAREHOUSE_RELATION)
        )

        // La pantalla de carga recibe la relación como si la hubiera leído el escáner; leer nunca registra nada.
        ScanActionId.LoadCargo -> ScanAction(
            id,
            "salida?barcode=${encode(value)}",
            listOf(MobileModuleIds.WAREHOUSE_EXIT)
        )

        ScanActionId.OpenEntry -> ScanAction(
            id,
            "entrada_editar/${encode(value)}",
            listOf(MobileModuleIds.WAREHOUSE_OPERATIONS)
        )

        ScanActionId.ReviewEntry -> ScanAction(
            id,
            "detalle/${encode(value)}",
            listOf(MobileModuleIds.REVIEW)
        )

        // No navega: el valor es la referencia de la entrada y quien toca la acción llama a «Tomar carpeta».
        ScanActionId.TakeFolder -> ScanAction(
            id,
            "",
            listOf(MobileModuleIds.REVIEW),
            ScanAction.LEVEL_MODIFY,
            command = true
        )

        ScanActionId.ShowOnMap -> ScanAction(
            id,
            "mapa_bodega",
            listOf(MobileModuleIds.WAREHOUSE_OPERATIONS)
        )

        ScanActionId.RegisterPreRegistro -> ScanAction(
            id,
            "caseta_entrada?barcode=${encode(value)}",
            listOf(MobileModuleIds.GATE),
            ScanAction.LEVEL_ADD
        )
    }

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
        // Un QR oficial viaja solo con su entrada o relación: la pantalla de destino y el servidor buscan eso, no el
        // texto completo de la etiqueta.
        val value = OfficialQrParser.identityOf(raw) ?: raw.orEmpty().trim().take(MAX_CODE_LENGTH)
        return "$WITHOUT_CODE?$ARG_CODE=${ScanActionResolver.encode(value)}"
    }

    /** Qué se leyó, según el argumento de la ruta. Sin argumento es un gafete: así lo deja [forScan]. */
    fun codeFromArgument(argument: String?): ScannedCode =
        argument?.let(ScannedCodeClassifier::classify) ?: ScannedCode.Gafete
}
