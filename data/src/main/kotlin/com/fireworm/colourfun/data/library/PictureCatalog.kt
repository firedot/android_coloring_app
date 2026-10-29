package com.fireworm.colourfun.data.library

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A group of pictures in the library, shown as an icon tab. Sections are data, not code. */
@Serializable
data class Section(val id: String, val icon: String, val order: Int)

@Serializable
enum class Difficulty {
    @SerialName("easy") Easy,
    @SerialName("medium") Medium,
    @SerialName("hard") Hard,
}

/**
 * A bundled line-art picture. [id] (`section/slug`) and [file] never change once shipped; a changed
 * picture gets a new id. Paths are relative to `assets/pictures/`.
 */
@Serializable
data class Picture(
    val id: String,
    val section: String,
    val file: String,
    val thumb: String,
    val difficulty: Difficulty,
    val regions: Int,
    val source: String,
)

/** `assets/pictures/catalog.json`, written by `tools/pictures/prepare.py`. */
@Serializable
data class PictureCatalog(val version: Int, val sections: List<Section>, val pictures: List<Picture>) {

    /** Sections in display order, each with its pictures in catalog order; empty sections dropped. */
    fun bySection(): List<Pair<Section, List<Picture>>> {
        val pictures = pictures.groupBy { it.section }
        return sections.sortedBy { it.order }.mapNotNull { section -> pictures[section.id]?.let { section to it } }
    }

    fun picture(id: String): Picture? = pictures.firstOrNull { it.id == id }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): PictureCatalog = json.decodeFromString(serializer(), text)
    }
}
