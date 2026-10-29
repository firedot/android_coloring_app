package com.fireworm.colourfun.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fireworm.colourfun.data.files.ArtworkFileStore
import com.fireworm.colourfun.drawing.document.DocumentSize

/**
 * One saved picture. Its outline and paint live in files (see [ArtworkFileStore]).
 *
 * The document geometry is stored as it was created, because a saved document is never resized:
 * on a screen of another aspect it is letterboxed instead.
 *
 * @property pictureId the library picture it was started from, e.g. `animals/cat_01`.
 * @property documentScale the picture scale: 1, or 0.75 on low-RAM devices.
 */
@Entity(tableName = "artworks")
data class ArtworkEntity(
    @PrimaryKey val id: String,
    val pictureId: String,
    val docWidth: Int,
    val docHeight: Int,
    val pictureLeft: Int,
    val pictureTop: Int,
    val documentScale: Float,
    val createdAt: Long,
    val updatedAt: Long,
    val exportedUri: String? = null,
)

/** The document geometry the artwork was made at. */
val ArtworkEntity.documentSize: DocumentSize get() = DocumentSize(docWidth, docHeight, documentScale)
