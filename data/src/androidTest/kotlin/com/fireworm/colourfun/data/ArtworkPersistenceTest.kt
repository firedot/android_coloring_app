package com.fireworm.colourfun.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fireworm.colourfun.data.db.ColourFunDatabase
import com.fireworm.colourfun.data.db.documentSize
import com.fireworm.colourfun.data.files.ArtworkFileStore
import com.fireworm.colourfun.drawing.document.DocumentSize
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Save, drop every in-memory object as a process kill would, then restore from disk. */
@RunWith(AndroidJUnit4::class)
class ArtworkPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "persistence-test")
    private val dbName = "persistence-test.db"

    @After
    fun cleanUp() {
        root.deleteRecursively()
        context.deleteDatabase(dbName)
    }

    @Test
    fun saveKillRestoreYieldsIdenticalPaintPixelsAndDocumentSize() = runBlocking {
        // A full-size 20:9 document.
        val paint = testPaint(3413, 1536)
        val expected = paint.pixels()

        ColourFunDatabase.create(context, dbName).let { db ->
            ArtworkRepository(db.artworkDao(), ArtworkFileStore(root)).save(testSnapshot("artwork", paint))
            db.close()
        }
        paint.recycle()

        val db = ColourFunDatabase.create(context, dbName)
        val restored = checkNotNull(ArtworkRepository(db.artworkDao(), ArtworkFileStore(root)).load("artwork"))
        db.close()

        val restoredPaint = checkNotNull(restored.paint)
        assertEquals(DocumentSize(3413, 1536, 1f), restored.entity.documentSize)
        assertEquals(3413, restoredPaint.width)
        assertArrayEquals(expected, restoredPaint.pixels())
        assertArrayEquals(testOutlinePng, restored.outlinePng)
    }
}
