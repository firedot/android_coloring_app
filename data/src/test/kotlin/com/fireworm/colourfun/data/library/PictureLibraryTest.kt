package com.fireworm.colourfun.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PictureLibraryTest {

    private val folders = mapOf(
        "" to listOf("various", "animals", "plants", "vehicles", "notes.txt"),
        "various" to listOf("car_01.png", "car_01_thumb.webp"),
        "animals" to listOf("dog_01.png", "dog_01_thumb.webp", "cat_01.png", "cat_01_thumb.webp"),
        "plants" to emptyList(),
        "vehicles" to listOf("bus_01.png", "bus_01_thumb.webp"),
    )
    private val library = PictureLibrary.scan { folders[it].orEmpty() }

    @Test
    fun `known sections come first in display order, then new ones, without empty folders`() {
        val sections = library.bySection().map { it.first }

        assertEquals(listOf("animals", "various", "vehicles"), sections.map { it.id })
        assertEquals(listOf("paw", "star", "star"), sections.map { it.icon })
    }

    @Test
    fun `pictures are the PNGs of a folder, sorted by name`() {
        val animals = library.bySection().first().second

        assertEquals(listOf("animals/cat_01", "animals/dog_01"), animals.map { it.id })
        assertEquals(Picture("animals/cat_01", "animals", "animals/cat_01.png", "animals/cat_01_thumb.webp"), animals[0])
    }

    @Test
    fun `a listing with nested paths gives the same library`() {
        val nested = PictureLibrary.scan { path ->
            folders.flatMap { (folder, names) -> names.map { if (folder.isEmpty()) it else "$folder/$it" } }
                .filter { path.isEmpty() || it.startsWith("$path/") }
                .map { if (path.isEmpty()) it else it.removePrefix("$path/") }
        }

        assertEquals(library.bySection(), nested.bySection())
    }

    @Test
    fun `pictures are found by id`() {
        assertEquals("vehicles/bus_01.png", library.picture("vehicles/bus_01")?.file)
        assertNull(library.picture("animals/cow_01"))
    }
}
