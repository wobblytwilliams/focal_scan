package au.edu.cqu.focalapp.ui

import au.edu.cqu.focalapp.domain.model.GraphBehaviourCategory
import au.edu.cqu.focalapp.domain.model.TrackedAnimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocalSamplingUiStateTest {
    @Test
    fun calculateGraphAxisMaxMinutes_defaultsToSixtyAndRoundsUpByFifteen() {
        val defaultAxis = calculateGraphAxisMaxMinutes(
            groups = listOf(
                AnimalGraphGroupUiState(
                    trackedAnimal = TrackedAnimal.BLUE,
                    bars = listOf(
                        AnimalGraphBarUiState(GraphBehaviourCategory.WALKING, 12f),
                        AnimalGraphBarUiState(GraphBehaviourCategory.GRAZING, 0f),
                        AnimalGraphBarUiState(GraphBehaviourCategory.IDLE, 0f)
                    )
                )
            )
        )
        val expandedAxis = calculateGraphAxisMaxMinutes(
            groups = listOf(
                AnimalGraphGroupUiState(
                    trackedAnimal = TrackedAnimal.GREEN,
                    bars = listOf(
                        AnimalGraphBarUiState(GraphBehaviourCategory.WALKING, 61f),
                        AnimalGraphBarUiState(GraphBehaviourCategory.GRAZING, 0f),
                        AnimalGraphBarUiState(GraphBehaviourCategory.IDLE, 0f)
                    )
                )
            )
        )

        assertEquals(60, defaultAxis)
        assertEquals(75, expandedAxis)
    }

    @Test
    fun sessionMetadata_validatesAndSignsTimeOffset() {
        val metadata = SessionMetadataUiState(
            observerName = " Observer ",
            timeOffsetInput = "0.3",
            timeOffsetDirection = TimeOffsetDirection.BEHIND
        )

        assertTrue(metadata.isObserverNameValid)
        assertTrue(metadata.isTimeOffsetValid)
        assertEquals(-0.3, metadata.signedTimeOffsetSeconds ?: 0.0, 0.0)
    }

    @Test
    fun timeOffsetHelpers_sanitizeParseAndFormatValues() {
        assertEquals("0.37", sanitizeTimeOffsetInput(".37"))
        assertEquals(0.3, parseUnsignedTimeOffsetSeconds("0.3") ?: 0.0, 0.0)
        assertEquals("0", formatTimeOffsetInput(0.0))
        assertEquals("1.2", formatTimeOffsetInput(-1.2))
        assertFalse(SessionMetadataUiState(timeOffsetInput = "0.33").isTimeOffsetValid)
    }
}
