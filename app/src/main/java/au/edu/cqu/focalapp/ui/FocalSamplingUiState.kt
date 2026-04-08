package au.edu.cqu.focalapp.ui

import au.edu.cqu.focalapp.domain.model.AnimalColor
import au.edu.cqu.focalapp.domain.model.Behavior
import au.edu.cqu.focalapp.domain.model.GraphBehaviourCategory
import au.edu.cqu.focalapp.domain.model.TrackedAnimal
import kotlin.math.ceil
import kotlin.math.max

data class AnimalPanelUiState(
    val slotIndex: Int,
    val trackedAnimal: TrackedAnimal,
    val isSelected: Boolean = true,
    val activeBehaviour: Behavior? = null,
    val activeEventId: Long? = null,
    val activeStartedAtEpochMs: Long? = null
) {
    val animalId: String
        get() = trackedAnimal.displayName

    val animalColor: AnimalColor
        get() = trackedAnimal.animalColor

    companion object {
        fun defaults(selectedAnimals: List<TrackedAnimal> = TrackedAnimal.defaultSelection()): List<AnimalPanelUiState> {
            return TrackedAnimal.entries.mapIndexed { index, trackedAnimal ->
                AnimalPanelUiState(
                    slotIndex = index,
                    trackedAnimal = trackedAnimal,
                    isSelected = trackedAnimal in selectedAnimals
                )
            }
        }
    }
}

data class AnimalGraphBarUiState(
    val category: GraphBehaviourCategory,
    val minutes: Float
)

data class AnimalGraphGroupUiState(
    val trackedAnimal: TrackedAnimal,
    val bars: List<AnimalGraphBarUiState>
) {
    val maxMinutes: Float
        get() = bars.maxOfOrNull(AnimalGraphBarUiState::minutes) ?: 0f
}

data class BehaviourGraphUiState(
    val groups: List<AnimalGraphGroupUiState> = emptyList(),
    val yAxisMaxMinutes: Int = 60
) {
    val hasGroups: Boolean
        get() = groups.isNotEmpty()
}

data class FocalSamplingUiState(
    val isSessionActive: Boolean = false,
    val activeSessionId: Long? = null,
    val activeSessionStartedAtEpochMs: Long? = null,
    val exportSessionId: Long? = null,
    val showTimeWarning: Boolean = false,
    val animals: List<AnimalPanelUiState> = AnimalPanelUiState.defaults(),
    val graph: BehaviourGraphUiState = BehaviourGraphUiState()
) {
    val canExport: Boolean
        get() = exportSessionId != null

    val visibleAnimals: List<AnimalPanelUiState>
        get() = animals.filter(AnimalPanelUiState::isSelected)

    val selectedTrackedAnimals: List<TrackedAnimal>
        get() = visibleAnimals.map(AnimalPanelUiState::trackedAnimal)

    val canStartSession: Boolean
        get() = !isSessionActive && visibleAnimals.isNotEmpty()
}

internal fun calculateGraphAxisMaxMinutes(groups: List<AnimalGraphGroupUiState>): Int {
    val maxMinutes = groups.maxOfOrNull(AnimalGraphGroupUiState::maxMinutes) ?: 0f
    val roundedUp = if (maxMinutes <= 0f) {
        0
    } else {
        ceil(maxMinutes / 15f).toInt() * 15
    }
    return max(60, roundedUp)
}
