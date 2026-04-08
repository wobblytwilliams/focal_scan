package au.edu.cqu.focalapp.ui

import au.edu.cqu.focalapp.domain.model.GraphBehaviourCategory
import au.edu.cqu.focalapp.domain.model.TrackedAnimal
import org.junit.Assert.assertEquals
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
}
