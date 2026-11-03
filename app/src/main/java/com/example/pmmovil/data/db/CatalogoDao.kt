package com.example.pmmovil.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.pmmovil.data.model.CatalogoItemEntity
import com.example.pmmovil.data.model.CatalogoVersionEntity

@Dao
abstract class CatalogoDao {
    @Query("SELECT * FROM catalogo_items WHERE catalogo = :catalogo ORDER BY orden")
    abstract suspend fun items(catalogo: String): List<CatalogoItemEntity>

    @Query("SELECT * FROM catalogo_versiones")
    abstract suspend fun versiones(): List<CatalogoVersionEntity>

    @Query("DELETE FROM catalogo_items WHERE catalogo = :catalogo")
    protected abstract suspend fun borrar(catalogo: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertar(items: List<CatalogoItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun guardarVersion(version: CatalogoVersionEntity)

    /** Cambia un catálogo completo de una vez: o queda el nuevo entero o queda el anterior, nunca a medias. */
    @Transaction
    open suspend fun reemplazar(catalogo: String, items: List<CatalogoItemEntity>, version: CatalogoVersionEntity) {
        borrar(catalogo)
        insertar(items)
        guardarVersion(version)
    }
}
