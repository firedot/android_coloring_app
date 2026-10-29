package com.fireworm.colourfun.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtworkDao {
    /** Most recently coloured first. */
    @Query("SELECT * FROM artworks ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ArtworkEntity>>

    @Query("SELECT * FROM artworks WHERE id = :id")
    suspend fun get(id: String): ArtworkEntity?

    @Upsert
    suspend fun upsert(artwork: ArtworkEntity)

    @Query("UPDATE artworks SET exportedUri = :uri WHERE id = :id")
    suspend fun setExportedUri(id: String, uri: String)

    @Query("DELETE FROM artworks WHERE id = :id")
    suspend fun delete(id: String)
}
