package au.edu.cqu.focalapp.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FocalDatabaseMigrationTest {
    @Test
    fun migration3To4_addsObserverNameAndTimeOffsetDefaults() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "focal-migration-${System.nanoTime()}"

        createVersion3Database(context, databaseName).use { database ->
            database.execSQL(
                """
                INSERT INTO sessions (
                    id,
                    animal_count,
                    animal_ids_json,
                    animal_colors_json,
                    tracked_animals_json,
                    session_format_version,
                    started_at,
                    ended_at
                ) VALUES (
                    1,
                    1,
                    '["Blue"]',
                    '["BLUE"]',
                    '["BLUE"]',
                    2,
                    1000,
                    NULL
                )
                """.trimIndent()
            )
        }

        val migratedDatabase = Room.databaseBuilder(
            context,
            FocalDatabase::class.java,
            databaseName
        )
            .addMigrations(
                FocalDatabase.MIGRATION_1_2,
                FocalDatabase.MIGRATION_2_3,
                FocalDatabase.MIGRATION_3_4
            )
            .build()

        val migratedSession = migratedDatabase.focalDao().getSessionById(1L)

        assertEquals("", migratedSession?.observerName)
        assertEquals(0.0, migratedSession?.timeOffsetSeconds ?: -1.0, 0.0)

        migratedDatabase.close()
        context.deleteDatabase(databaseName)
    }

    private fun createVersion3Database(
        context: Context,
        databaseName: String
    ): SupportSQLiteDatabase {
        context.deleteDatabase(databaseName)
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS sessions (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                animal_count INTEGER NOT NULL,
                                animal_ids_json TEXT NOT NULL,
                                animal_colors_json TEXT NOT NULL,
                                tracked_animals_json TEXT NOT NULL,
                                session_format_version INTEGER NOT NULL,
                                started_at INTEGER NOT NULL,
                                ended_at INTEGER
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS events (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                session_id INTEGER NOT NULL,
                                animal_id TEXT NOT NULL,
                                behaviour TEXT NOT NULL,
                                start_time INTEGER NOT NULL,
                                end_time INTEGER,
                                FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS index_events_session_id ON events(session_id)"
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS index_events_session_id_animal_id ON events(session_id, animal_id)"
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                }
            )
            .build()

        return FrameworkSQLiteOpenHelperFactory()
            .create(configuration)
            .writableDatabase
    }
}
