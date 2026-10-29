package com.fireworm.colourfun.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PictureCatalogTest {

    private val catalog = PictureCatalog.parse(
        """
        {
          "version": 1,
          "sections": [
            { "id": "various", "icon": "star", "order": 2 },
            { "id": "animals", "icon": "paw", "order": 0 },
            { "id": "plants", "icon": "leaf", "order": 1 }
          ],
          "pictures": [
            { "id": "various/car_01", "section": "various", "file": "various/car_01.png",
              "thumb": "various/car_01_thumb.webp", "difficulty": "medium", "regions": 30, "source": "s" },
            { "id": "animals/cat_01", "section": "animals", "file": "animals/cat_01.png",
              "thumb": "animals/cat_01_thumb.webp", "difficulty": "easy", "regions": 12, "source": "s",
              "added": "a field from a later version" }
          ]
        }
        """,
    )

    @Test
    fun `sections come in display order, without empty ones`() {
        val sections = catalog.bySection()

        assertEquals(listOf("animals", "various"), sections.map { it.first.id })
        assertEquals(listOf("animals/cat_01"), sections[0].second.map { it.id })
    }

    @Test
    fun `fields are read, unknown ones ignored`() {
        val cat = catalog.picture("animals/cat_01")!!

        assertEquals(Difficulty.Easy, cat.difficulty)
        assertEquals(12, cat.regions)
        assertEquals("animals/cat_01_thumb.webp", cat.thumb)
        assertNull(catalog.picture("animals/dog_01"))
    }
}
