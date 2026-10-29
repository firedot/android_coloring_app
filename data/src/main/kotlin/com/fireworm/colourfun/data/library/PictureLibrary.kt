package com.fireworm.colourfun.data.library

/** A group of pictures in the library, shown as an icon tab: a folder under `assets/pictures/`. */
data class Section(val id: String, val icon: String, val order: Int) {
    companion object {
        private val known = listOf(Section("animals", "paw", 0), Section("plants", "leaf", 1), Section("various", "star", 2))

        /** A new folder still shows up: after the known sections, with the star icon. */
        fun of(id: String): Section = known.firstOrNull { it.id == id } ?: Section(id, "star", known.size)
    }
}

/**
 * A bundled line-art picture: `<section>/<name>.png` with `<name>_thumb.webp` next to it. [id]
 * (`section/name`) never changes once shipped; a changed picture gets a new name. Paths are relative
 * to `assets/pictures/`.
 */
data class Picture(val id: String, val section: String, val file: String, val thumb: String)

/** The library as found in the asset folders; there is no index file to keep in sync. */
class PictureLibrary(private val sections: List<Pair<Section, List<Picture>>>) {

    /** Sections in display order, each with its pictures sorted by name; empty folders dropped. */
    fun bySection(): List<Pair<Section, List<Picture>>> = sections

    fun picture(id: String): Picture? = sections.firstNotNullOfOrNull { (_, pictures) -> pictures.firstOrNull { it.id == id } }

    companion object {
        /**
         * Builds the library from folder listings; [list] returns the names in a folder ("" = the
         * root). A device lists direct children only, Robolectric lists nested paths too
         * (`animals/cat.png`); both work.
         */
        fun scan(list: (path: String) -> List<String>): PictureLibrary = PictureLibrary(
            list("").map { it.substringBefore('/') }.distinct().mapNotNull { folder ->
                val pictures = list(folder)
                    .filter { it.endsWith(".png") && '/' !in it }
                    .map { it.removeSuffix(".png") }
                    .sorted()
                    .map { name -> Picture("$folder/$name", folder, "$folder/$name.png", "$folder/${name}_thumb.webp") }
                if (pictures.isEmpty()) null else Section.of(folder) to pictures
            }.sortedWith(compareBy({ it.first.order }, { it.first.id })),
        )
    }
}
