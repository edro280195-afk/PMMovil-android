package com.example.pmmovil.data.repository

import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.model.TraceRequest
import com.example.pmmovil.data.model.TraceResultDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Qué contestó el servidor a la consulta de un código. */
sealed interface TraceOutcome {
    /** Hay ficha. */
    data class Found(val result: TraceResultDto) : TraceOutcome

    /** El código tiene forma válida pero no existe en el sistema. */
    data object NotFound : TraceOutcome

    /** El servidor no cubre este código (400). */
    data object Rejected : TraceOutcome

    /** El puesto del usuario no puede consultar fichas (403). */
    data object Forbidden : TraceOutcome

    /** Sin red, sin sesión, tiempo agotado o error del servidor: la pantalla se queda con lo que dice el código. */
    data object Unavailable : TraceOutcome
}

/**
 * La ficha de un código. Solo consulta: nunca registra nada. No guarda nada en el aparato ni escribe el código en
 * ningún registro, porque puede ser un QR de pre-registro con su token.
 */
@Singleton
class TraceRepository @Inject constructor(
    private val api: PMMovilApi
) {
    suspend fun resolve(rawCode: String): TraceOutcome = withContext(Dispatchers.IO) {
        try {
            val response = api.resolveTrace(TraceRequest(rawCode.trim()))
            when {
                response.code() == HTTP_BAD_REQUEST -> TraceOutcome.Rejected
                response.code() == HTTP_FORBIDDEN -> TraceOutcome.Forbidden
                !response.isSuccessful -> TraceOutcome.Unavailable
                else -> {
                    val body = response.body()
                    when {
                        body == null -> TraceOutcome.Unavailable
                        body.found -> TraceOutcome.Found(body)
                        else -> TraceOutcome.NotFound
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Red, JSON mal formado o lo que sea: la ficha es un extra, nunca debe tumbar la pantalla de acciones.
            TraceOutcome.Unavailable
        }
    }

    private companion object {
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_FORBIDDEN = 403
    }
}
