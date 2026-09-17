package com.example.pmmovil.data.model

import android.util.Base64
import com.google.gson.annotations.SerializedName
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Modelos del acceso facial de produccion. Ver Scripts/face_schema.sql en
 * PMMovil.Api.
 *
 * La diferencia de fondo contra los modelos del laboratorio esta en el login:
 * [FaceLoginRequest] NO lleva nombre de usuario. El dispositivo manda el vector
 * y el servidor decide quien es. Si el telefono pudiera afirmar una identidad,
 * cualquiera que descompile el APK entraria como quien quisiera.
 *
 * El vector viaja en base64 con el mismo codec del laboratorio
 * ([VectorCodec]): 128 floats little-endian = 512 bytes exactos.
 */

/** Una toma del enrolamiento, con la pose y la calidad en que se capturo. */
data class FacePlantillaDto(
    @SerializedName("vectorBase64") val VectorBase64: String,
    @SerializedName("dims") val Dims: Int = 128,
    /** -1 = frontal; 0..47 = sector visual observado en el anillo. */
    @SerializedName("poseSector") val PoseSector: Int = -1,
    @SerializedName("yaw") val Yaw: Float? = null,
    @SerializedName("pitch") val Pitch: Float? = null,
    @SerializedName("roll") val Roll: Float? = null,
    @SerializedName("luma") val Luma: Float? = null,
    @SerializedName("nitidez") val Nitidez: Float? = null,
    @SerializedName("scoreDeteccion") val ScoreDeteccion: Float? = null,
    @SerializedName("rostroPx") val RostroPx: Int? = null
)

/**
 * Alta completa en un solo viaje. Las dos vueltas del anillo se resuelven en el
 * dispositivo -- tiene los modelos y esta comparando sus PROPIAS capturas, asi
 * que no hay nada que ganar partiendolo en llamadas.
 */
data class FaceEnrolamientoRequest(
    @SerializedName("aceptacionTexto") val AceptacionTexto: String,
    /** PNG del trazo de la firma, base64. El servidor lo exige. */
    @SerializedName("firmaPngBase64") val FirmaPngBase64: String,
    /** Como se llama esta apariencia: "Con lentes", "Sin barba". */
    @SerializedName("etiqueta") val Etiqueta: String? = null,
    /**
     * true = borrar las apariencias anteriores. El default es AGREGAR: quien
     * llega hoy con lentes suma una apariencia, no pierde la que tenia.
     */
    @SerializedName("reemplazar") val Reemplazar: Boolean = false,
    @SerializedName("dispositivo") val Dispositivo: String? = null,
    @SerializedName("planta") val Planta: String? = null,
    @SerializedName("modelo") val Modelo: String = "sface-2021dec",
    @SerializedName("plantillas") val Plantillas: List<FacePlantillaDto>
)

/** Una apariencia dada de alta, tal como la lista el servidor. */
data class FaceAparienciaDto(
    @SerializedName("id") val Id: Long = 0,
    @SerializedName("etiqueta") val Etiqueta: String? = null,
    @SerializedName("plantillas") val Plantillas: Int = 0,
    @SerializedName("createdAt") val CreatedAt: String? = null,
    @SerializedName("dispositivo") val Dispositivo: String? = null,
    @SerializedName("firmado") val Firmado: Boolean = false
)

/** Intento de entrar con la cara. Sin usuario, a proposito. */
data class FaceLoginRequest(
    @SerializedName("vectorBase64") val VectorBase64: String,
    @SerializedName("dims") val Dims: Int = 128,
    @SerializedName("modelo") val Modelo: String = "sface-2021dec",
    @SerializedName("planta") val Planta: String? = null,
    @SerializedName("dispositivo") val Dispositivo: String? = null,
    /** 1 o 2 dentro de la misma presencia frente a la camara. */
    @SerializedName("intento") val Intento: Int = 1,
    @SerializedName("luzLux") val LuzLux: Float? = null,
    @SerializedName("luma") val Luma: Float? = null,
    @SerializedName("nitidez") val Nitidez: Float? = null,
    @SerializedName("scoreDeteccion") val ScoreDeteccion: Float? = null,
    @SerializedName("yaw") val Yaw: Float? = null,
    @SerializedName("pitch") val Pitch: Float? = null,
    @SerializedName("roll") val Roll: Float? = null,
    @SerializedName("rostroPx") val RostroPx: Int? = null,
    @SerializedName("msDispositivo") val MsDispositivo: Int? = null
)

data class FaceResult(
    @SerializedName("success") val Success: Boolean,
    @SerializedName("id") val Id: Long = 0,
    @SerializedName("plantillas") val Plantillas: Int = 0,
    @SerializedName("message") val Message: String? = null
)

data class FaceEstadoResponse(
    @SerializedName("success") val Success: Boolean = true,
    /** false cuando el servidor todavia no tiene encendido el acceso facial. */
    @SerializedName("disponible") val Disponible: Boolean = false,
    @SerializedName("enrolado") val Enrolado: Boolean = false,
    @SerializedName("plantillas") val Plantillas: Int = 0,
    @SerializedName("enroladoEl") val EnroladoEl: String? = null,
    @SerializedName("dispositivo") val Dispositivo: String? = null,
    @SerializedName("message") val Message: String? = null,
    /** Las apariencias activas, de la mas reciente a la mas vieja. */
    @SerializedName("apariencias") val Apariencias: List<FaceAparienciaDto> = emptyList(),
    /** Cuantas caben; al llegar al tope la app deja de ofrecer "Agregar". */
    @SerializedName("maxApariencias") val MaxApariencias: Int = 3
)

/**
 * Serializacion del vector facial.
 *
 * 128 floats little-endian = 512 bytes exactos. El orden de bytes se fija a
 * mano y no se deja al azar de la arquitectura: del otro lado el servidor los
 * lee con BinaryPrimitives.ReadSingleLittleEndian, y si las dos puntas no
 * coincidieran los vectores saldrian basura sin que nada truene -- todos los
 * intentos simplemente dejarian de reconocer a nadie.
 */
object VectorCodec {

    fun aBase64(vector: FloatArray): String {
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach(buffer::putFloat)
        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }

    fun desdeBase64(base64: String): FloatArray {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buffer.float }
    }
}
