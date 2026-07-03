package takagi.ru.monica.steam.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SteamAccountEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SteamDatabase : RoomDatabase() {
    abstract fun steamAccountDao(): SteamAccountDao

    companion object {
        @Volatile
        private var INSTANCE: SteamDatabase? = null

        fun getDatabase(context: Context): SteamDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SteamDatabase::class.java,
                    "steam_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
