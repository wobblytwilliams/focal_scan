package au.edu.cqu.focalapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SamplingSessionEntity::class, BehaviorEventEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class FocalDatabase : RoomDatabase() {
    abstract fun focalDao(): FocalDao

    companion object {
        @Volatile
        private var instance: FocalDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE sessions ADD COLUMN animal_colors_json TEXT NOT NULL DEFAULT '[]'"
                )
                database.execSQL(
                    "UPDATE sessions SET animal_colors_json = '[\"BLUE\"]' WHERE animal_count = 1"
                )
                database.execSQL(
                    "UPDATE sessions SET animal_colors_json = '[\"BLUE\",\"GREEN\"]' WHERE animal_count = 2"
                )
                database.execSQL(
                    "UPDATE sessions SET animal_colors_json = '[\"BLUE\",\"GREEN\",\"YELLOW\"]' WHERE animal_count >= 3"
                )
            }
        }

        fun getInstance(context: Context): FocalDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FocalDatabase::class.java,
                    "focal_sampling.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
