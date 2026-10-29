package com.fireworm.colourfun.data

import androidx.room.Room
import com.fireworm.colourfun.data.artwork.ArtworkDatabase
import com.fireworm.colourfun.data.artwork.ArtworkFileStore
import com.fireworm.colourfun.data.artwork.ArtworkRepository
import com.fireworm.colourfun.generator.LineArtCodec
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkRepositoryTest {

    @get:Rule val folder = TemporaryFolder()

    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ArtworkDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private var clock = 1_000L
    private val repository by lazy {
        ArtworkRepository(db.artworkDao(), ArtworkFileStore(folder.root), Dispatchers.Unconfined) { clock }
    }

    @After
    fun close() = db.close()

    @Test
    fun `saved paint and outline restore exactly`() = runBlocking {
        val paint = testPaint(256, 192)
        repository.save(testSnapshot("a", paint))

        val restored = checkNotNull(repository.load("a"))
        assertArrayEquals(paint.pixels(), checkNotNull(restored.paint).pixels())
        assertTrue(restored.paint.isMutable)
        assertEquals(LineArtCodec.encode(testLineArt()), LineArtCodec.encode(restored.lineArt))
        assertEquals(9L, restored.entity.seed)
        assertTrue(repository.thumbnailFile("a").length() > 0)
        assertTrue(File(folder.root, "a").listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `saving again keeps createdAt, bumps updatedAt and orders the gallery`() = runBlocking {
        repository.save(testSnapshot("a", testPaint(64, 48)))
        clock = 2_000L
        repository.save(testSnapshot("b", testPaint(64, 48)))
        clock = 3_000L
        repository.save(testSnapshot("a", testPaint(64, 48, seed = 5)))

        val all = repository.observeAll().first()
        assertEquals(listOf("a", "b"), all.map { it.id })
        assertEquals(1_000L, all[0].createdAt)
        assertEquals(3_000L, all[0].updatedAt)
        assertArrayEquals(testPaint(64, 48, seed = 5).pixels(), repository.load("a")!!.paint!!.pixels())
    }

    @Test
    fun `missing or deleted artworks load as null`() = runBlocking {
        assertNull(repository.load("nope"))
        repository.save(testSnapshot("a", testPaint(64, 48)))
        repository.delete("a")
        assertNull(repository.load("a"))
        assertTrue(!File(folder.root, "a").exists())
    }
}
