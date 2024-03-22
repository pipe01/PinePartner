package net.pipe01.pinepartner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Watch::class,
        AllowedNotifApp::class,
        Plugin::class,
    ],
    version = 13,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchDao(): WatchDao

    abstract fun allowedNotifAppDao(): AllowedNotifAppDao

    abstract fun pluginDao(): PluginDao

    companion object {
        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java, "pinepartner"
            ).enableMultiInstanceInvalidation().build()
        }
    }
}