package au.edu.cqu.focalapp.util

import au.edu.cqu.focalapp.data.local.BehaviorEventEntity
import au.edu.cqu.focalapp.data.local.SamplingSessionEntity
import au.edu.cqu.focalapp.domain.model.Behavior
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {
    @Test
    fun buildCsv_includesObserverNameAndSignedOffsetColumns() {
        val session = SamplingSessionEntity(
            id = 7L,
            animalCount = 1,
            animalIdsJson = "[\"Blue\"]",
            animalColorsJson = "[\"BLUE\"]",
            observerName = "Observer One",
            timeOffsetSeconds = -0.3,
            startedAtEpochMs = 1_000L
        )
        val csv = CsvExporter.buildCsv(
            session = session,
            events = listOf(
                BehaviorEventEntity(
                    sessionId = 7L,
                    animalId = "Blue",
                    behaviour = Behavior.GRAZING,
                    startTimeEpochMs = 1_000L,
                    endTimeEpochMs = 2_000L
                )
            )
        )

        assertTrue(csv.startsWith("session_id,observer_name,time_offset_seconds"))
        assertTrue(csv.contains("\"Observer One\""))
        assertTrue(csv.contains("\"-0.3\""))
    }

    @Test
    fun buildCsv_withNoEventsStillReturnsHeaderOnly() {
        val session = SamplingSessionEntity(
            id = 8L,
            animalCount = 0,
            animalIdsJson = "[]",
            animalColorsJson = "[]",
            observerName = "Observer Zero",
            timeOffsetSeconds = 0.0,
            startedAtEpochMs = 1_000L
        )

        val csv = CsvExporter.buildCsv(session = session, events = emptyList())

        assertTrue(csv.trim() == "session_id,observer_name,time_offset_seconds,animal_id,behaviour,start_time,end_time")
    }
}
