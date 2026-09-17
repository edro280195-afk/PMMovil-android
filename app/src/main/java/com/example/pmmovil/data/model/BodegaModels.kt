package com.example.pmmovil.data.model

import com.google.gson.annotations.SerializedName

data class BodegaEntradaDto(
    @SerializedName(value = "creferencia", alternate = ["Creferencia", "cref", "crefAgrupada"])
    val creferencia: String = "",
    @SerializedName(value = "ref", alternate = ["Ref"])
    val ref: String? = null,
    @SerializedName(value = "fArribo", alternate = ["FArribo", "fArriboStr", "dfechaarribo"])
    val fArribo: String? = null,
    @SerializedName(value = "claveCte", alternate = ["ClaveCte", "cclienteidtraf"])
    val claveCte: String? = null,
    @SerializedName(value = "cliente", alternate = ["Cliente", "cnombre"])
    val cliente: String? = null,
    @SerializedName(value = "caja", alternate = ["Caja", "cnocaja"])
    val caja: String? = null,
    @SerializedName(value = "guia", alternate = ["Guia", "cnoguia"])
    val guia: String? = null,
    @SerializedName(value = "recibio", alternate = ["Recibio", "crecibio"])
    val recibio: String? = null,
    @SerializedName(value = "transportista", alternate = ["Transportista"])
    val transportista: String? = null,
    @SerializedName(value = "claveProv", alternate = ["ClaveProv", "cidprovtraf"])
    val claveProv: String? = null,
    @SerializedName(value = "proveedor", alternate = ["Proveedor"])
    val proveedor: String? = null,
    @SerializedName(value = "via", alternate = ["Via", "cvia"])
    val via: String? = null,
    @SerializedName(value = "manejo", alternate = ["Manejo", "cidmanejo"])
    val manejo: String? = null,
    @SerializedName(value = "tipoMcia", alternate = ["TipoMcia", "tipomcia"])
    val tipoMcia: String? = null,
    @SerializedName(value = "inbond", alternate = ["Inbond", "linbond"])
    val inbond: Boolean = false,
    @SerializedName(value = "hazmat", alternate = ["Hazmat", "lhazmat"])
    val hazmat: Boolean = false,
    @SerializedName(value = "fCreacion", alternate = ["FCreacion", "fcreatrafico"])
    val fCreacion: String? = null,
    @SerializedName(value = "fModi", alternate = ["FModi", "tdatetimetrafico"])
    val fModi: String? = null
)

data class BodegaListaResponse(
    val success: Boolean = false,
    val items: List<BodegaEntradaDto>? = null,
    val message: String? = null
)
