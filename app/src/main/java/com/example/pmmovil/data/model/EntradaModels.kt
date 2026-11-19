package com.example.pmmovil.data.model

import com.google.gson.annotations.SerializedName

data class EntradaCatalogItem(val value: String = "", val text: String = "")

data class EntradaCatalogs(
    val bodegas: List<EntradaCatalogItem> = emptyList(),
    val clasesHazmat: List<EntradaCatalogItem> = emptyList(),
    val manejos: List<EntradaCatalogItem> = emptyList(),
    val cajasArribo: List<EntradaCatalogItem> = emptyList(),
    val recibidores: List<EntradaCatalogItem> = emptyList(),
    val ubicaciones: List<EntradaCatalogItem> = emptyList(),
    val clases: List<EntradaCatalogItem> = emptyList()
)

data class EntradaCatalogsResponse(
    val success: Boolean = false,
    val catalogos: EntradaCatalogs = EntradaCatalogs(),
    val message: String? = null
)

data class EntradaConsecutiveResponse(
    val success: Boolean = false,
    val numero: String = "",
    val message: String = ""
)

data class EntradaDetail(
    @SerializedName(value = "cantidad", alternate = ["Cantidad"])
    val cantidad: Double = 0.0,
    @SerializedName(value = "clase", alternate = ["Clase"])
    val clase: String = "",
    @SerializedName(value = "descripcion", alternate = ["Descripcion"])
    val descripcion: String = "",
    @SerializedName(value = "atados", alternate = ["Atados"])
    val atados: String = "",
    @SerializedName(value = "ubicacion", alternate = ["Ubicacion"])
    val ubicacion: String = ""
)

data class EntradaLookupItem(
    @SerializedName(value = "id", alternate = ["Id", "value", "Value"])
    val id: String = "",
    @SerializedName(value = "nombre", alternate = ["Nombre", "text", "Text"])
    val nombre: String = "",
    @SerializedName(value = "extra", alternate = ["Extra"])
    val extra: String = ""
)

data class EntradaLookupResponse(
    val success: Boolean = false,
    val items: List<EntradaLookupItem> = emptyList(),
    val message: String? = null
)

data class EntradaEntityResponse(
    val success: Boolean = false,
    val codigo: String = "",
    val nombre: String = ""
)

data class EntradaHeader(
    val creferencia: String? = null,
    val cclienteidtraf: String? = null,
    val nom_cliente: String? = null,
    val dfechaarribo: String? = null,
    val cidmanejo: String? = null,
    val cnocaja: String? = null,
    val cnoguia: String? = null,
    val cidtransportraf: String? = null,
    val nom_transporte: String? = null,
    val linbond: Boolean? = null,
    val cinbondno: String? = null,
    val mobserv: String? = null,
    val lhazmat: Boolean? = null,
    val bodega: String? = null,
    val planta: String? = null,
    val nbultosin: Double? = null,
    val nsubpeso: Double? = null,
    val ntotpeso: Double? = null,
    val crecibio: String? = null,
    val cmarcas1: String? = null,
    val cmarcas2: String? = null,
    val danada: Boolean? = null,
    val danadaDescripcion: String? = null,
    val maderaSello: Boolean? = null,
    val maderaSelloCant: Int? = null,
    val maderaSinSello: Boolean? = null,
    val maderaSinSelloCant: Int? = null,
    val aserrin: Boolean? = null,
    val aserrinCant: Int? = null,
    val noSello: Boolean? = null,
    val noSelloCant: Int? = null
)

data class EntradaReferenceResponse(
    val success: Boolean = false,
    val message: String? = null,
    val encabezado: EntradaHeader? = null,
    val detalles: List<EntradaDetail> = emptyList(),
    val fotos: List<String> = emptyList()
)

data class CasetaPhotoInfo(val indice: Int = 0, val nombreArchivo: String = "")

data class CasetaScanResponse(
    val success: Boolean = false,
    val message: String? = null,
    val casetaId: String? = null,
    val numCaja: String? = null,
    val clienteId: String? = null,
    val nombreCliente: String? = null,
    val transporteId: String? = null,
    val nombreTransporte: String? = null,
    val placas: String? = null,
    val operador: String? = null,
    val planta: String? = null,
    val fechaArribo: String? = null,
    val fotosCount: Int = 0,
    val fotos: List<CasetaPhotoInfo> = emptyList(),
    val entradaExistente: Boolean = false,
    val referenciaExistente: String? = null
)

data class EntradaSaveRequest(
    val ref1: String,
    val ref2: String,
    val ref3: String,
    val ref4: String,
    val ref5: String,
    val ref6: String,
    val fecha: String,
    val bodega: String,
    val codCliente: String,
    val nomCliente: String,
    val hazmat: Boolean,
    val clase: String,
    val un: String,
    val danada: Boolean,
    val danadaDescripcion: String,
    val codTransporte: String,
    val nomTransporte: String,
    val manejo: String,
    val guia: String,
    val cajaArribo: String,
    val casetaId: String?,
    val inBond: Boolean,
    val inBondVal: String,
    val totalRecibido: Double,
    val pesoLbs: Double,
    val pesoKgs: Double,
    val observaciones: String,
    val recibio: String,
    val maderaSello: Boolean,
    val maderaSelloCant: Int,
    val maderaSinSello: Boolean,
    val maderaSinSelloCant: Int,
    val aserrin: Boolean,
    val aserrinCant: Int,
    val noSello: Boolean,
    val noSelloCant: Int,
    val detalles: List<EntradaDetail>,
    val fotos: List<String>,
    val fotosDanio: List<String>,
    /**
     * Cuántas fotos del daño va a subir la app DESPUÉS de este guardado, por su cola (el registro viaja liviano, sin
     * fotos). El servidor rechaza una recepción que declara daño y avisa cero, y con este número puede decir en el
     * enlace público «faltan N fotos del daño por llegar». Null = no avisar (no se usa; queda para pruebas).
     */
    val fotosDanioPendientes: Int? = null
)

data class EntradaOperationResponse(
    val success: Boolean = false,
    val message: String = "",
    val recepcionId: Int? = null
)

data class EntradaShareLinkResponse(
    val success: Boolean = false,
    val message: String? = null,
    val url: String? = null,
    val expira: String? = null
)

data class EntradaPhotosRequest(val referencia: String, val fotos: List<String>)

data class EntradaPhotoInfo(
    val idFotos: Int = 0,
    val nLineNo: Int = 0,
    val cExtension: String = "",
    val usuCrea: String = "",
    val fCrea: String = "",
    /**
     * Versión de la foto (idfotos + fecha): va en la dirección para que la tableta la guarde para siempre. Un servidor
     * anterior no la manda y entonces se pide solo el original (ver [com.example.pmmovil.data.api.EvidenciaFotoUrls.tres]).
     */
    val version: String? = null
)

data class EntradaPhotosResponse(
    val success: Boolean = false,
    val items: List<EntradaPhotoInfo> = emptyList(),
    val message: String? = null
)

/** Cliente/transporte más usados recientemente — sección "Frecuentes" del buscador. */
data class EntradaFrequentItem(
    @SerializedName(value = "id", alternate = ["Id"])
    val id: String = "",
    @SerializedName(value = "nombre", alternate = ["Nombre"])
    val nombre: String = "",
    @SerializedName(value = "uso", alternate = ["Uso"])
    val uso: Int = 0,
    @SerializedName(value = "ultimoUso", alternate = ["UltimoUso"])
    val ultimoUso: String? = null
) {
    val lookupItem: EntradaLookupItem
        get() = EntradaLookupItem(id = id, nombre = nombre)
}

data class EntradaFrequentResponse(
    val success: Boolean = false,
    val items: List<EntradaFrequentItem> = emptyList(),
    val message: String? = null
)

data class EntradaDraft(
    val id: String,
    val createdAt: Long,
    val lastEditAt: Long,
    val form: EntradaFormSnapshot
)

data class EntradaFormSnapshot(
    val ref1: String = "01",
    val ref2: String = "C",
    val ref3: String = "",
    val ref4: String = "",
    val ref5: String = "00",
    val ref6: String = "00",
    val fecha: String = "",
    val bodega: String = "1",
    val codCliente: String = "",
    val nomCliente: String = "",
    val codTransporte: String = "",
    val nomTransporte: String = "",
    val manejo: String = "",
    val guia: String = "",
    val cajaArribo: String = "",
    val casetaId: String? = null,
    val hazmat: Boolean = false,
    val claseHazmat: String = "",
    val un: String = "",
    val danada: Boolean = false,
    val danadaDescripcion: String = "",
    val inBond: Boolean = false,
    val inBondVal: String = "",
    val detalles: List<EntradaDetail> = emptyList(),
    val pesoLbs: Double = 0.0,
    val pesoKgs: Double = 0.0,
    val observaciones: String = "",
    val recibio: String = "",
    val maderaSello: Boolean = false,
    val maderaSelloCant: Int = 0,
    val maderaSinSello: Boolean = false,
    val maderaSinSelloCant: Int = 0,
    val aserrin: Boolean = false,
    val aserrinCant: Int = 0,
    val noSello: Boolean = false,
    val noSelloCant: Int = 0,
    val localPhotoPaths: List<String> = emptyList(),
    val localDamagePhotoPaths: List<String> = emptyList(),
    val isExisting: Boolean = false
)
