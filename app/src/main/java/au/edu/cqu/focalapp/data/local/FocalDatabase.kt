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
    version = 4,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE sessions ADD COLUMN tracked_animals_json TEXT NOT NULL DEFAULT '[]'"
                )
                database.execSQL(
                    "ALTER TABLE sessions ADD COLUMN session_format_version INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE sessions ADD COLUMN observer_name TEXT NOT NULL DEFAULT ''"
                )
                database.execSQL(
                    "ALTER TABLE sessions ADD COLUMN time_offset_seconds REAL NOT NULL DEFAULT 0.0"
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
