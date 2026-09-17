package com.example.pmmovil.data.repository

import android.util.Log
import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.model.FaceEnrolamientoRequest
import com.example.pmmovil.data.model.FaceEstadoResponse
import com.example.pmmovil.data.model.FaceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alta y baja del rostro del usuario que ya inicio sesion.
 *
 * El LOGIN facial no vive aqui: vive en [AuthRepository], junto al resto de
 * las formas de entrar, para que la sesion quede escrita exactamente igual
 * venga del gafete, del teclado o de la cara.
 */
@Singleton
class FaceRepository @Inject constructor(
    private val api: PMMovilApi
) {

    suspend fun getEstado(): Result<FaceEstadoResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getFaceEstado()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                body
            } else {
                throw Exception(mensajeDeError(response.code()))
            }
        }.onFailure { Log.w(TAG, "No se pudo leer el estado del rostro: ${it.message}") }
    }

    suspend fun enrolar(request: FaceEnrolamientoRequest): Result<FaceResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.enrolarRostro(request)
                val body = response.body()
                if (response.isSuccessful && body?.Success == true) {
                    body
                } else {
                    throw Exception(body?.Message ?: mensajeDeError(response.code()))
                }
            }
        }

    /** Borra una sola apariencia (la de los lentes, por ejemplo). */
    suspend fun revocarApariencia(id: Long): Result<FaceResult> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.revocarApariencia(id)
            val body = response.body()
            if (response.isSuccessful && body?.Success == true) {
                body
            } else {
                throw Exception(body?.Message ?: mensajeDeError(response.code()))
            }
        }
    }

    suspend fun revocar(): Result<FaceResult> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.revocarRostro()
            val body = response.body()
            if (response.isSuccessful && body?.Success == true) {
                body
            } else {
                throw Exception(body?.Message ?: mensajeDeError(response.code()))
            }
        }
    }

    private fun mensajeDeError(codigo: Int): String = when (codigo) {
        401 -> "Tu sesión expiró. Vuelve a entrar."
        404 -> "El servidor no tiene habilitado el acceso con rostro."
        503 -> "El acceso con rostro está apagado en el servidor."
        else -> "Error del servidor ($codigo)"
    }

    private companion object {
        const val TAG = "PMM-FACE-REPO"
    }
}
