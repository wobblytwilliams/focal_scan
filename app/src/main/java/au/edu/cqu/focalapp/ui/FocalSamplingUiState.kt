package au.edu.cqu.focalapp.ui

import au.edu.cqu.focalapp.domain.model.AnimalColor
import au.edu.cqu.focalapp.domain.model.Behavior

data class AnimalPanelUiState(
    val slotIndex: Int,
    val animalId: String,
    val animalColor: AnimalColor,
    val activeBehaviour: Behavior? = null,
    val activeEventId: Long? = null,
    val activeStartedAtEpochMs: Long? = null
) {
    companion object {
        fun defaults(count: Int = 3): List<AnimalPanelUiState> {
            return List(count) { index ->
                AnimalPanelUiState(
                    slotIndex = index,
                    animalId = "Animal ${index + 1}",
                    animalColor = AnimalColor.defaultForSlot(index)
                )
            }
        }
    }
}

data class FocalSamplingUiState(
    val isSessionActive: Boolean = false,
    val activeSessionId: Long? = null,
    val activeSessionStartedAtEpochMs: Long? = null,
    val exportSessionId: Long? = null,
    val configuredAnimalCount: Int? = null,
    val showTimeWarning: Boolean = false,
    val showAnimalCountDialog: Boolean = false,
    val showStartSessionDialog: Boolean = false,
    val animals: List<AnimalPanelUiState> = AnimalPanelUiState.defaults()
) {
    val canExport: Boolean
        get() = exportSessionId != null

    val visibleAnimals: List<AnimalPanelUiState>
        get() = animals.take(configuredAnimalCount ?: 0)
}
