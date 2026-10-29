package com.fireworm.colourfun.data.artwork

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ArtworkEntity::class], version = 1)
abstract class ArtworkDatabase : RoomDatabase() {
    abstract fun artworkDao(): ArtworkDao

    companion object {
        fun create(context: Context, name: String = "artworks.db"): ArtworkDatabase =
            Room.databaseBuilder(context, ArtworkDatabase::class.java, name).build()
    }
}
