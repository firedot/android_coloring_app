package com.fireworm.colourfun.data.artwork

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One saved picture. Its outline and paint live in files (see [ArtworkFileStore]); [seed] and
 * [generatorVersion] are only for debugging and "make another like this", never for restoring.
 *
 * @property documentScale the document size the paint was made at, relative to 2048 × 1536.
 */
@Entity(tableName = "artworks")
data class ArtworkEntity(
    @PrimaryKey val id: String,
    val seed: Long,
    val generatorVersion: Int,
    val documentScale: Float,
    val createdAt: Long,
    val updatedAt: Long,
    val exportedUri: String? = null,
)
