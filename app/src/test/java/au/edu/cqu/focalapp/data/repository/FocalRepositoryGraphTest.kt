package au.edu.cqu.focalapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.edu.cqu.focalapp.data.local.FocalDao
import au.edu.cqu.focalapp.data.local.FocalDatabase
import au.edu.cqu.focalapp.data.local.SamplingSessionEntity
import au.edu.cqu.focalapp.data.local.SessionFormatVersion
import au.edu.cqu.focalapp.domain.model.Behavior
import au.edu.cqu.focalapp.domain.model.TrackedAnimal
import au.edu.cqu.focalapp.util.SessionAnimalColorsCodec
import au.edu.cqu.focalapp.util.SessionAnimalIdsCodec
import au.edu.cqu.focalapp.util.SessionTrackedAnimalsCodec
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FocalRepositoryGraphTest {
    private lateinit var database: FocalDatabase
    private lateinit var dao: FocalDao
    private lateinit var repository: FocalRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FocalDatabase::class.java
        ).build()
        dao = database.focalDao()
        repository = FocalRepository(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun getCumulativeBehaviourTotals_combinesTrackedSessionsAndExcludesLegacySessions() = runTest {
        val blueGreenSession = repository.startSession(
            startedAtEpochMs = 0L,
            trackedAnimals = listOf(TrackedAnimal.BLUE, TrackedAnimal.GREEN),
            observerName = "Observer",
            timeOffsetSeconds = 0.0
        )
        val blueWalking = repository.startEvent(
            sessionId = blueGreenSession,
            animalId = TrackedAnimal.BLUE.displayName,
            behaviour = Behavior.WALKING,
            startTimeEpochMs = 0L
        )
        repository.endEvent(blueWalking, 1_800_000L)
        val blueIdle = repository.startEvent(
            sessionId = blueGreenSession,
            animalId = TrackedAnimal.BLUE.displayName,
            behaviour = Behavior.IDLE_RUMINATING,
            startTimeEpochMs = 1_800_000L
        )
        repository.endEvent(blueIdle, 2_400_000L)
        val greenGrazing = repository.startEvent(
            sessionId = blueGreenSession,
            animalId = TrackedAnimal.GREEN.displayName,
            behaviour = Behavior.GRAZING,
            startTimeEpochMs = 0L
        )
        repository.endEvent(greenGrazing, 1_200_000L)

        val yellowSession = repository.startSession(
            startedAtEpochMs = 3_000_000L,
            trackedAnimals = listOf(TrackedAnimal.YELLOW),
            observerName = "Observer",
            timeOffsetSeconds = 0.0
        )
        repository.startEvent(
            sessionId = yellowSession,
            animalId = TrackedAnimal.YELLOW.displayName,
            behaviour = Behavior.WALKING,
            startTimeEpochMs = 3_000_000L
        )

        dao.insertSession(
            SamplingSessionEntity(
                animalCount = 1,
                animalIdsJson = SessionAnimalIdsCodec.encode(listOf("Legacy animal")),
                animalColorsJson = SessionAnimalColorsCodec.encode(listOf(TrackedAnimal.YELLOW.animalColor)),
                trackedAnimalsJson = SessionTrackedAnimalsCodec.encode(listOf(TrackedAnimal.YELLOW)),
                sessionFormatVersion = SessionFormatVersion.LEGACY,
                startedAtEpochMs = 0L
            )
        ).also { legacySessionId ->
            val legacyEvent = repository.startEvent(
                sessionId = legacySessionId,
                animalId = TrackedAnimal.YELLOW.displayName,
                behaviour = Behavior.WALKING,
                startTimeEpochMs = 0L
            )
            repository.endEvent(legacyEvent, 3_000_000L)
        }

        val totals = repository.getCumulativeBehaviourTotals(nowEpochMs = 3_900_000L)
            .associateBy { it.trackedAnimal }

        assertEquals(1_800_000L, totals.getValue(TrackedAnimal.BLUE).walkingDurationMs)
        assertEquals(600_000L, totals.getValue(TrackedAnimal.BLUE).idleDurationMs)
        assertEquals(1_200_000L, totals.getValue(TrackedAnimal.GREEN).grazingDurationMs)
        assertEquals(900_000L, totals.getValue(TrackedAnimal.YELLOW).walkingDurationMs)
    }
}
