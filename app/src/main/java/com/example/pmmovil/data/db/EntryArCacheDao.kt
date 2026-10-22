package com.example.pmmovil.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pmmovil.data.model.EntryArCacheEntity

@Dao
interface EntryArCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<EntryArCacheEntity>)

    @Query("SELECT * FROM entry_ar_cache WHERE reference IN (:references)")
    suspend fun getByReferences(references: List<String>): List<EntryArCacheEntity>
}
