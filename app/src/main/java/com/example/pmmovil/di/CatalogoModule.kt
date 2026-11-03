package com.example.pmmovil.di

import android.util.Log
import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.catalog.CatalogoAlmacenRoom
import com.example.pmmovil.data.catalog.CatalogoFuenteApi
import com.example.pmmovil.data.catalog.CatalogoRepository
import com.example.pmmovil.data.db.CatalogoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Un solo repositorio de catálogos maestros para toda la app: todas las pantallas comparten lo que ya se bajó. */
@Module
@InstallIn(SingletonComponent::class)
object CatalogoModule {
    @Provides
    @Singleton
    fun provideCatalogoRepository(api: PMMovilApi, dao: CatalogoDao): CatalogoRepository =
        CatalogoRepository(
            fuente = CatalogoFuenteApi(api),
            almacen = CatalogoAlmacenRoom(dao),
            advertir = { Log.w("PMM-CATALOGOS", it) }
        )
}
