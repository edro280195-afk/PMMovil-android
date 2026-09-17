package com.example.pmmovil.data.model

import com.google.gson.JsonElement

data class BarcodeConfigResponse(
    val success: Boolean = false,
    val message: String? = null,
    val crefAgrupada: String? = null,
    val cidcte: String? = null,
    val cidprov: String? = null,
    val barcodeLong: Int = 0,
    val nombreCliente: String? = null,
    val nombreProveedor: String? = null,
    val totalCatalogo: Int = 0,
    val configurado: Boolean = false
)

data class IniciarSesionBarcodeRequest(val crefAgrupada: String)

data class IniciarSesionBarcodeResponse(
    val success: Boolean = false,
    val message: String? = null,
    val idSesion: Int = 0,
    val cidcte: String? = null,
    val cidprov: String? = null,
    val barcodeLong: Int = 0,
    val nombreCliente: String? = null,
    val nombreProveedor: String? = null
)

data class RegistrarLecturaRequest(
    val idSesion: Int,
    val codigoRaw: String,
    val cnofact: String = "",
    val clienteScanId: String = ""
)

data class LecturaResponse(
    val success: Boolean = false,
    val message: String? = null,
    val cidproduct: String? = null,
    val existe: Boolean = false,
    val descripcion: String? = null,
    val duplicada: Boolean = false
)

data class ProductoResumen(
    val cidproduct: String = "",
    val descripcion: String? = null,
    val cantidad: Int = 0,
    val existe: Boolean = false,
    val codigoRaw: String? = null,
    val ultimaLectura: Long = 0,
    val pendientes: Int = 0
)

data class SesionBarcodeResumen(
    val idSesion: Int = 0,
    val crefAgrupada: String = "",
    val cidcte: String = "",
    val cidprov: String = "",
    val nombreCliente: String = "",
    val nombreProveedor: String = "",
    val usuario: String = "",
    val nombreUsuarioCompleto: String = "",
    val fechaInicio: JsonElement? = null,
    val fechaFin: JsonElement? = null,
    val totalLecturas: Int = 0,
    val totalExistentes: Int = 0,
    val totalInexistentes: Int = 0,
    val emailEnviado: Boolean = false,
    val emailDestinos: String = "",
    val facturasEscaneadas: String = "",
    val observaciones: String = ""
)

data class ResumenBarcodeResponse(
    val success: Boolean = false,
    val message: String? = null,
    val sesion: SesionBarcodeResumen? = null,
    val productos: List<ProductoResumen>? = null
)

data class RestarLecturaRequest(
    val idSesion: Int,
    val cidproduct: String,
    val codigoRaw: String = ""
)

data class RestarLecturaResponse(
    val success: Boolean = false,
    val restante: Int = 0
)

data class EliminarProductoRequest(
    val idSesion: Int,
    val cidproduct: String,
    val codigoRaw: String = ""
)

data class FinalizarBarcodeRequest(
    val enviarCorreo: Boolean = true,
    val observaciones: String = ""
)

data class AnalisisSemanticoBarcode(
    val cidproduct: String = "",
    val veredicto: String = "",
    val explicacion: String = ""
)

data class FinalizarBarcodeResponse(
    val success: Boolean = false,
    val message: String? = null,
    val resumen: SesionBarcodeResumen? = null,
    val productos: List<ProductoResumen>? = null,
    val analisisSemantico: List<AnalisisSemanticoBarcode>? = null,
    val correo: CorreoInfo? = null
)

data class CorreoInfo(
    val intentado: Boolean = false,
    val enviado: Boolean = false,
    val enProceso: Boolean = false,
    val destinatarios: List<String> = emptyList()
)

data class EstadoCorreoBarcodeResponse(
    val success: Boolean = false,
    val message: String? = null,
    val idSesion: Int = 0,
    val emailEnviado: Boolean = false,
    val destinatarios: List<String> = emptyList()
)

data class EstadoBarcodeResponse(
    val success: Boolean = false,
    val message: String? = null,
    val activa: Boolean = false,
    val idSesion: Int? = null,
    val cidcte: String? = null,
    val cidprov: String? = null,
    val barcodeLong: Int = 0,
    val nombreCliente: String? = null,
    val nombreProveedor: String? = null,
    val fechaInicio: JsonElement? = null,
    val lecturas: List<ProductoResumen>? = null
)

data class PendingBarcodeScan(
    val id: String,
    val code: String,
    val createdAt: Long,
    val sessionId: Int = 0
)
