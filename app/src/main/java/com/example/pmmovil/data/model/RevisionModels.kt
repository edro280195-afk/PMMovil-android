package com.example.pmmovil.data.model

import com.google.gson.annotations.SerializedName

data class FacturaRevisionDto(
    @SerializedName(value = "cnofact", alternate = ["Cnofact", "CnoFact"]) val Cnofact: String,
    @SerializedName(value = "creferencia", alternate = ["Creferencia", "CReferencia"]) val Creferencia: String?,
    @SerializedName(value = "crefAgrupada", alternate = ["CrefAgrupada", "Cref_agrupada"]) val CrefAgrupada: String?,
    @SerializedName("dFecha") val dFecha: String?,
    @SerializedName("cpedido") val Cpedido: String?,
    @SerializedName("bodegafact") val Bodegafact: String?,
    @SerializedName("cclienteid") val Cclienteid: String?,
    @SerializedName("nombreCliente") val NombreCliente: String?,
    @SerializedName("nombreProveedor") val NombreProveedor: String?,
    @SerializedName("nPesoLbs") val nPesoLbs: Double,
    @SerializedName("nPesoKgs") val nPesoKgs: Double,
    @SerializedName("totBultos") val TotBultos: Int,
    @SerializedName("tipoop") val Tipoop: String?,
    @SerializedName("marcas") val Marcas: String?,
    @SerializedName("numeros") val Numeros: String?,
    @SerializedName("obsrev") val Obsrev: String?,
    @SerializedName("discrepancias") val Discrepancias: String?,
    @SerializedName("usurev") val Usurev: String?,
    @SerializedName("statusRev") val StatusRev: Int,
    @SerializedName("totBultosDoc") val TotBultosDoc: Int,
    @SerializedName("usumodi") val Usumodi: String?,
    @SerializedName("fModi") val fModi: String?,
    @SerializedName("relacionCompleta") val RelacionCompleta: Boolean,
    @SerializedName("obsrev2") val Obsrev2: String?,
    @SerializedName(value = "usuedit", alternate = ["usuEdit", "UsuEdit"]) val UsuEdit: String?
)

data class FacturaResponse(
    val success: Boolean,
    val items: List<FacturaRevisionDto>?,
    val message: String?
)

data class PartidaRevisionDto(
    @SerializedName(value = "idRevdet", alternate = ["IdRevdet", "idrevdet"]) val IdRevdet: Int,
    @SerializedName(value = "cnofact", alternate = ["Cnofact", "CnoFact"]) val Cnofact: String,
    @SerializedName(value = "cnoParte", alternate = ["CnoParte", "cnoparte"]) val CnoParte: String?,
    @SerializedName(value = "cDescripcion", alternate = ["CDescripcion", "cdescripcion"]) val cDescripcion: String?,
    @SerializedName("marca") val Marca: String?,
    @SerializedName("modelo") val Modelo: String?,
    @SerializedName("numSer") val NumSer: String?,
    @SerializedName("numLote") val NumLote: String?,
    @SerializedName("nCantFactura") val nCantFactura: Double,
    @SerializedName("nCantOut") val nCantOut: Double?,
    @SerializedName("cPaisOrigen") val cPaisOrigen: String?,
    @SerializedName("cptCveTmateria") val CptCveTmateria: String?,
    @SerializedName("cptDescTmateria") val CptDescTmateria: String?,
    @SerializedName("tipoMaterial") val TipoMaterial: String? = null,
    @SerializedName("revisada") val Revisada: Boolean,
    @SerializedName("obsrevpartida") val Obsrevpartida: String?,
    @SerializedName("mnotapartida") val Mnotapartida: String?,
    @SerializedName("nPeso") val nPeso: Double?,
    @SerializedName("cantidadComercial") val CantidadComercial: Double? = null,
    @SerializedName("unidadComercial") val UnidadComercial: String? = null,
    @SerializedName("unidadFactura") val UnidadFactura: String? = null,
    @SerializedName("cantidadTarifa") val CantidadTarifa: Double? = null,
    @SerializedName("unidadTarifa") val UnidadTarifa: String? = null,
    @SerializedName("pesoUnitario") val PesoUnitario: Double? = null,
    @SerializedName("valorUnitario") val ValorUnitario: Double? = null,
    @SerializedName("valorAgregado") val ValorAgregado: Double? = null,
    @SerializedName("valorPartidaMe") val ValorPartidaMe: Double? = null,
    @SerializedName("factorEquivalencia") val FactorEquivalencia: Double? = null,
    // Solo para decidir si se muestra el campo especial de peso en libras
    // (clientes con la regla legacy GGD 14-08-17). No es editable desde Revisión.
    @SerializedName("cclienteid") val Cclienteid: String?,
    @SerializedName(value = "parteCte", alternate = ["ParteCte", "parte_cte", "Parte_cte"]) val ParteCte: String? = null
)

data class PartidaResponse(
    val success: Boolean,
    val items: List<PartidaRevisionDto>?,
    val message: String?
)

/** Unidad visible del catálogo legacy y su código interno UMC. */
data class UnidadComercialDto(
    @SerializedName("codigo") val codigo: String,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("codigoInterno") val codigoInterno: String
)

data class UnidadesComercialesResponse(
    val success: Boolean,
    val items: List<UnidadComercialDto>?,
    val message: String?
)

/** Respuesta atómica de la pantalla de revisión normal. */
data class EntradaCompletaRevisionResponse(
    val success: Boolean,
    val facturas: List<FacturaRevisionDto>?,
    val partidas: List<PartidaRevisionDto>?,
    // Observación general de TODA la revisión (traf3.observacion_revision) --
    // no por factura, para eso ya existe FacturaRevisionDto.Obsrev.
    val observacionRevision: String?,
    val serverTime: String?,
    val message: String?
)

data class GuardarObservacionEntradaRequest(
    val crefAgrupada: String,
    val valor: String
)

/**
 * Los tres datos del rombo de mercancia peligrosa, SEPARADOS como vienen
 * impresos: UN arriba, clase en el vertice y grupo de embalaje al pie.
 * Van a traf3, que es por referencia y no por partida.
 */
data class GuardarHazmatRevisionRequest(
    val crefAgrupada: String,
    /** Solo el numero, sin el prefijo "UN". */
    val un: String?,
    val clase: String?,
    val grupo: String?
)

data class PaisDto(
    @SerializedName("codigo") val codigo: String?,
    @SerializedName("descrip") val descrip: String?,
    @SerializedName("cpais") val cpais: String?,
    @SerializedName("cnombre") val cnombre: String?
)

data class PaisesResponse(
    val success: Boolean,
    val items: List<PaisDto>?,
    val frecuentes: List<PaisFrecuenteDto>? = null,
    val message: String?
)

data class PaisesFrecuentesResponse(
    val success: Boolean,
    val items: List<PaisFrecuenteDto>?,
    val message: String?
)

data class PaisFrecuenteDto(
    @SerializedName("codigo") val codigo: String?,
    @SerializedName("descrip") val descrip: String?,
    @SerializedName("usos") val usos: Int?
)

data class TipoMaterialDto(
    @SerializedName("codigo") val codigo: String?,
    @SerializedName("descrip") val descrip: String?
)

/** Opciones fijas usadas por Revisión Normal; no dependen de un catálogo remoto. */
val REVISION_TIPOS_MATERIAL = listOf(
    TipoMaterialDto(codigo = "METAL", descrip = "METAL"),
    TipoMaterialDto(codigo = "PLASTICO", descrip = "PLASTICO"),
    // Representa la opción vacía de VFP sin obligar al usuario a interpretar
    // una fila sin texto.
    TipoMaterialDto(codigo = "", descrip = "SIN TIPO")
)

data class ValidarHazmatResponse(
    val success: Boolean,
    val problemas: List<HazmatProblema>?
)

data class HazmatProblema(
    val cnoparte: String?,
    val problema: String?
)

data class FotoRevisionDto(
    @SerializedName("idFotos") val idFotos: Int,
    @SerializedName("nLineNo") val nLineNo: Int,
    @SerializedName("cExtension") val cExtension: String?,
    @SerializedName("usuCrea") val usuCrea: String?,
    @SerializedName("fCrea") val fCrea: String?,
    // 107 = evidencia normal de revisión, 103 = evidencia de discrepancia
    // (mismo ntipodocto que daño de Entrada Bodega, por decisión de equipo).
    @SerializedName("nTipoDocto") val nTipoDocto: Int = 107
) {
    val esDiscrepancia: Boolean get() = nTipoDocto == 103
}

data class FotosResponse(
    val success: Boolean,
    val items: List<FotoRevisionDto>?,
    val message: String?
)

data class SubirFotoRequest(
    val creferencia: String,
    val cclienteid: String,
    val image: String,
    val extension: String,
    val esDiscrepancia: Boolean = false
)

data class SubirFotoResponse(
    val success: Boolean,
    val id: Int?,
    val fileName: String?,
    val message: String?
)

data class DocumentoPdfDto(
    val creferencia: String?,
    val crefAgrupada: String?,
    val cclienteid: String?,
    val carpeta: String?,
    val nombreCliente: String?,
    val tipo: Int,
    val label: String?,
    val disponible: Boolean
)

data class DocumentosPdfResponse(
    val success: Boolean,
    val items: List<DocumentoPdfDto>?,
    val message: String?
)

data class EntradaPendienteDto(
    @SerializedName("crefAgrupada") val crefAgrupada: String,
    @SerializedName("nombreReferencia") val nombreReferencia: String?,
    @SerializedName("clienteId") val clienteId: String?,
    @SerializedName("nombreCliente") val nombreCliente: String?,
    @SerializedName("dFecha") val dFecha: String?,
    @SerializedName("totalFacturas") val totalFacturas: Int,
    @SerializedName("facturasCompletas") val facturasCompletas: Int,
    @SerializedName("totalPartidas") val totalPartidas: Int,
    @SerializedName("partidasRevisadas") val partidasRevisadas: Int,
    @SerializedName("inicioRevision") val inicioRevision: String?,
    @SerializedName("finRevision") val finRevision: String?,
    @SerializedName("usuarioRevisando") val usuarioRevisando: String?,
    @SerializedName("usuarioTomoDocumento") val usuarioTomoDocumento: String?,
    @SerializedName("todasRelacionCompleta") val todasRelacionCompleta: Boolean,
    @SerializedName("esAsignada") val esAsignada: Boolean?,
    @SerializedName("asignPrioridad") val asignPrioridad: Int?,
    @SerializedName("idAsignacion") val idAsignacion: Int?,
    @SerializedName("asignSupervisor") val asignSupervisor: String? = null,
    @SerializedName("asignNota") val asignNota: String? = null,
    @SerializedName("asignEstatus") val asignEstatus: Int? = null,
    @SerializedName("asignFecha") val asignFecha: String? = null
)

data class EntradasPendientesResponse(
    val success: Boolean,
    val items: List<EntradaPendienteDto>?,
    val truncado: Boolean?,
    val message: String?
)

/** Respuesta de GET /Revision/buscar/{crefAgrupada} */
data class BuscarEntradaResponse(
    val success: Boolean,
    val item: EntradaPendienteDto?,
    val message: String?
)

data class SolicitarDesbloqueoRequest(
    val cnofact: String,
    val motivo: String
)

data class SolicitarDesbloqueoResponse(
    val success: Boolean,
    val id: Int?,
    val message: String?
)

data class VerificarDesbloqueoResponse(
    val success: Boolean,
    val autorizado: Boolean,
    val autorizadoPor: String?,
    val fechaExpiracion: String?
)
