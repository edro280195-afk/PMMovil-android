package com.example.pmmovil.data.repository

import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.model.HomeSummaryDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Las cifras del Tablero. No se guardan en el aparato: son del momento y se piden cada vez que se abre el inicio. */
@Singleton
class HomeSummaryRepository @Inject constructor(
    private val api: PMMovilApi
) {
    suspend fun get(): Result<HomeSummaryDto> = withContext(Dispatchers.IO) {
        try {
            val response = api.getHomeSummary()
            val body = response.body()
            if (response.isSuccessful && body?.success == true) {
                Result.success(body)
            } else {
                Result.failure(IllegalStateException("No se pudo consultar el resumen del Tablero (${response.code()})."))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
