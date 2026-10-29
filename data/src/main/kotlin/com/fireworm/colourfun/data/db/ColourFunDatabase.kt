package com.fireworm.colourfun.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 2 replaced the procedurally generated outlines with library pictures. Version 1 artworks
 * cannot be converted, so the upgrade starts fresh and [onDestructiveMigration] clears their files.
 */
@Database(entities = [ArtworkEntity::class], version = 2)
abstract class ColourFunDatabase : RoomDatabase() {
    abstract fun artworkDao(): ArtworkDao

    companion object {
        fun create(
            context: Context,
            name: String = "artworks.db",
            onDestructiveMigration: () -> Unit = {},
        ): ColourFunDatabase =
            Room.databaseBuilder(context, ColourFunDatabase::class.java, name)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .addCallback(object : Callback() {
                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) = onDestructiveMigration()
                })
                .build()
    }
}
