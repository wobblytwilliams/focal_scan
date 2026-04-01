package au.edu.cqu.focalapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.edu.cqu.focalapp.data.local.FocalDatabase
import au.edu.cqu.focalapp.domain.model.AnimalColor
import au.edu.cqu.focalapp.domain.model.Behavior
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FocalRepositoryTrimTest {
    private lateinit var database: FocalDatabase
    private lateinit var repository: FocalRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FocalDatabase::class.java
        ).build()
        repository = FocalRepository(database.focalDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun trimSessionToCutoff_deletesEventsFullyInsideTheWindow() = runTest {
        val sessionId = repository.startSession(
            startedAtEpochMs = 0L,
            animalCount = 1,
            animalIds = listOf("Animal 1"),
            animalColors = listOf(AnimalColor.BLUE)
        )
        val olderEventId = repository.startEvent(
            sessionId = sessionId,
            animalId = "Animal 1",
            behaviour = Behavior.GRAZING,
            startTimeEpochMs = 1_000L
        )
        repository.endEvent(olderEventId, 4_000L)
        val newerEventId = repository.startEvent(
            sessionId = sessionId,
            animalId = "Animal 1",
            behaviour = Behavior.WALKING,
            startTimeEpochMs = 8_000L
        )
        repository.endEvent(newerEventId, 9_500L)

        repository.trimSessionToCutoff(sessionId, 6_000L)

        val events = repository.getEventsForSession(sessionId)
        assertEquals(1, events.size)
        assertEquals(1_000L, events.single().startTimeEpochMs)
        assertEquals(4_000L, events.single().endTimeEpochMs)
    }

    @Test
    fun trimSessionToCutoff_truncatesOverlappingEventsAndKeepsSessionActive() = runTest {
        val sessionId = repository.startSession(
            startedAtEpochMs = 0L,
            animalCount = 1,
            animalIds = listOf("Animal 1"),
            animalColors = listOf(AnimalColor.BLUE)
        )
        val closedEventId = repository.startEvent(
            sessionId = sessionId,
            animalId = "Animal 1",
            behaviour = Behavior.GRAZING,
            startTimeEpochMs = 1_000L
        )
        repository.endEvent(closedEventId, 9_000L)
        repository.startEvent(
            sessionId = sessionId,
            animalId = "Animal 1",
            behaviour = Behavior.IDLE,
            startTimeEpochMs = 2_000L
        )

        repository.trimSessionToCutoff(sessionId, 6_000L)

        val events = repository.getEventsForSession(sessionId)
        assertEquals(2, events.size)
        assertEquals(6_000L, events[0].endTimeEpochMs)
        assertEquals(6_000L, events[1].endTimeEpochMs)
        assertNull(repository.getSessionById(sessionId)?.endedAtEpochMs)
        assertTrue(events.all { it.startTimeEpochMs < 6_000L })
    }
}
