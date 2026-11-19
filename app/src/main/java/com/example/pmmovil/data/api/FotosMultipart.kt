package com.example.pmmovil.data.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Piezas para mandar fotos como archivos (multipart) en vez de texto base64, que las infla un 33 %. En caseta todo cruza
 * el internet de la oficina (~13 Mbps de subida compartidos, medido el 18-nov-2026): una salida de 8 fotos pasa de
 * ~8.5 MB a ~6.4 MB.
 *
 * Los campos de texto van como [RequestBody] y no como `String`: con el convertidor de Gson, un `String` en `@Part` viaja
 * con comillas (`"true"`) y el servidor no lo entiende.
 */
object FotosMultipart {

    private val JPEG = "image/jpeg".toMediaType()
    private val TEXTO = "text/plain; charset=utf-8".toMediaType()

    fun foto(campo: String, nombreArchivo: String, bytes: ByteArray): MultipartBody.Part =
        MultipartBody.Part.createFormData(campo, nombreArchivo, bytes.toRequestBody(JPEG))

    fun campo(campo: String, valor: String): MultipartBody.Part =
        MultipartBody.Part.createFormData(campo, valor)

    fun texto(valor: String): RequestBody = valor.toRequestBody(TEXTO)

    /**
     * El servidor todavía no tiene la ruta de archivos (un API anterior): 404 si no existe, 405/415 si la ruta existe pero
     * no recibe multipart. Entonces se manda como base64, como antes. Cualquier otro error es de la foto o del servidor.
     */
    fun sinRutaDeArchivos(codigoHttp: Int): Boolean = codigoHttp == 404 || codigoHttp == 405 || codigoHttp == 415
}
