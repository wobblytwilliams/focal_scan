package au.edu.cqu.focalapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.edu.cqu.focalapp.data.repository.FocalRepository
import au.edu.cqu.focalapp.domain.model.Behavior
import au.edu.cqu.focalapp.util.CsvExportPayload
import au.edu.cqu.focalapp.util.CsvExporter
import au.edu.cqu.focalapp.util.DateTimeFormats
import au.edu.cqu.focalapp.util.SessionAnimalIdsCodec
import au.edu.cqu.focalapp.util.TimeProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface UiEvent {
    data class ShowMessage(val message: String) : UiEvent
    data class ExportCsv(val payload: CsvExportPayload) : UiEvent
}

class FocalSamplingViewModel(
    private val repository: FocalRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {
    private val actionMutex = Mutex()

    private val _uiState = MutableStateFlow(FocalSamplingUiState())
    val uiState: StateFlow<FocalSamplingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        restoreExistingSession()
    }

    fun dismissTimeWarning() {
        _uiState.update { it.copy(showTimeWarning = false) }
    }

    fun openAnimalCountDialog() {
        if (_uiState.value.isSessionActive) {
            return
        }

        _uiState.update {
            it.copy(
                showAnimalCountDialog = true,
                showStartSessionDialog = false
            )
        }
    }

    fun setAnimalCount(count: Int) {
        if (count !in 1..3) {
            return
        }

        _uiState.update { state ->
            state.copy(
                configuredAnimalCount = count,
                showAnimalCountDialog = false,
                showStartSessionDialog = false,
                animals = buildAnimalsForCount(
                    currentAnimals = state.animals,
                    animalCount = count
                )
            )
        }
    }

    fun requestStartSession() {
        val state = _uiState.value
        if (state.isSessionActive) {
            return
        }

        if (state.configuredAnimalCount == null) {
            openAnimalCountDialog()
            return
        }

        _uiState.update {
            it.copy(
                showAnimalCountDialog = false,
                showStartSessionDialog = true
            )
        }
    }

    fun dismissStartSessionDialog() {
        _uiState.update { it.copy(showStartSessionDialog = false) }
    }

    fun startSession(animalIds: List<String>) {
        viewModelScope.launch {
            actionMutex.withLock {
                val state = _uiState.value
                if (state.isSessionActive) {
                    return@withLock
                }

                val animalCount = state.configuredAnimalCount
                if (animalCount == null) {
                    _uiState.update {
                        it.copy(
                            showAnimalCountDialog = true,
                            showStartSessionDialog = false
                        )
                    }
                    return@withLock
                }

                val normalizedIds = List(animalCount) { index ->
                    normalizedAnimalId(animalIds.getOrNull(index).orEmpty(), index)
                }
                val now = timeProvider.nowEpochMillis()
                val sessionId = repository.startSession(
                    startedAtEpochMs = now,
                    animalCount = animalCount,
                    animalIds = normalizedIds
                )

                _uiState.value = state.copy(
                    isSessionActive = true,
                    activeSessionId = sessionId,
                    activeSessionStartedAtEpochMs = now,
                    exportSessionId = sessionId,
                    showAnimalCountDialog = false,
                    showStartSessionDialog = false,
                    animals = buildAnimalsForCount(
                        currentAnimals = state.animals,
                        animalCount = animalCount,
                        configuredIds = normalizedIds
                    )
                )

                _events.emit(UiEvent.ShowMessage("Session #$sessionId started."))
            }
        }
    }

    fun stopSession() {
        viewModelScope.launch {
            actionMutex.withLock {
                val state = _uiState.value
                val sessionId = state.activeSessionId
                if (!state.isSessionActive || sessionId == null) {
                    return@withLock
                }

                val now = timeProvider.nowEpochMillis()

                state.animals
                    .mapNotNull { it.activeEventId }
                    .forEach { repository.endEvent(it, now) }

                repository.stopSession(sessionId, now)

                val closedAnimals = state.animals.map { animal ->
                    animal.copy(
                        activeBehaviour = null,
                        activeEventId = null,
                        activeStartedAtEpochMs = null
                    )
                }

                _uiState.value = state.copy(
                    isSessionActive = false,
                    activeSessionId = null,
                    activeSessionStartedAtEpochMs = null,
                    exportSessionId = sessionId,
                    showStartSessionDialog = false,
                    animals = closedAnimals
                )

                _events.emit(
                    UiEvent.ShowMessage(
                        "Session #$sessionId stopped. Open behaviours were closed at ${DateTimeFormats.formatLocalTime(now)}."
                    )
                )
            }
        }
    }

    fun onBehaviourPressed(slotIndex: Int, behaviour: Behavior) {
        viewModelScope.launch {
            actionMutex.withLock {
                val state = _uiState.value
                val sessionId = state.activeSessionId

                if (!state.isSessionActive || sessionId == null) {
                    _events.emit(UiEvent.ShowMessage("Start a session before recording behaviour events."))
                    return@withLock
                }

                val animal = state.animals.getOrNull(slotIndex) ?: return@withLock
                val now = timeProvider.nowEpochMillis()
                val normalizedAnimalId = normalizedAnimalId(animal.animalId, slotIndex)

                val updatedAnimal = when {
                    animal.activeBehaviour == null -> {
                        val newEventId = repository.startEvent(
                            sessionId = sessionId,
                            animalId = normalizedAnimalId,
                            behaviour = behaviour,
                            startTimeEpochMs = now
                        )

                        animal.copy(
                            animalId = normalizedAnimalId,
                            activeBehaviour = behaviour,
                            activeEventId = newEventId,
                            activeStartedAtEpochMs = now
                        )
                    }

                    animal.activeBehaviour == behaviour -> {
                        animal.activeEventId?.let { repository.endEvent(it, now) }

                        animal.copy(
                            animalId = normalizedAnimalId,
                            activeBehaviour = null,
                            activeEventId = null,
                            activeStartedAtEpochMs = null
                        )
                    }

                    else -> {
                        animal.activeEventId?.let { repository.endEvent(it, now) }

                        val newEventId = repository.startEvent(
                            sessionId = sessionId,
                            animalId = normalizedAnimalId,
                            behaviour = behaviour,
                            startTimeEpochMs = now
                        )

                        animal.copy(
                            animalId = normalizedAnimalId,
                            activeBehaviour = behaviour,
                            activeEventId = newEventId,
                            activeStartedAtEpochMs = now
                        )
                    }
                }

                val updatedAnimals = state.animals.toMutableList().apply {
                    this[slotIndex] = updatedAnimal
                }

                _uiState.value = state.copy(
                    animals = updatedAnimals,
                    exportSessionId = sessionId
                )
            }
        }
    }

    fun exportCsv() {
        viewModelScope.launch {
            actionMutex.withLock {
                val state = _uiState.value
                val sessionId = state.exportSessionId

                if (sessionId == null) {
                    _events.emit(UiEvent.ShowMessage("There is no session available to export yet."))
                    return@withLock
                }

                val session = repository.getSessionById(sessionId)
                if (session == null) {
                    _events.emit(UiEvent.ShowMessage("The selected session could not be found."))
                    return@withLock
                }

                val events = repository.getEventsForSession(sessionId)
                if (events.isEmpty()) {
                    _events.emit(UiEvent.ShowMessage("Session #$sessionId has no events to export."))
                    return@withLock
                }

                _events.emit(
                    UiEvent.ExportCsv(
                        payload = CsvExportPayload(
                            fileName = CsvExporter.buildFileName(
                                sessionId = sessionId,
                                sessionStartedAtEpochMs = session.startedAtEpochMs
                            ),
                            content = CsvExporter.buildCsv(events)
                        )
                    )
                )
            }
        }
    }

    private fun restoreExistingSession() {
        viewModelScope.launch {
            actionMutex.withLock {
                val activeSnapshot = repository.getActiveSessionSnapshot()
                val latestSession = repository.getLatestSession()

                if (activeSnapshot != null) {
                    val configuredIds = SessionAnimalIdsCodec.decode(activeSnapshot.session.animalIdsJson)
                    val restoredAnimals = buildAnimalsForCount(
                        currentAnimals = AnimalPanelUiState.defaults(),
                        animalCount = activeSnapshot.session.animalCount,
                        configuredIds = configuredIds,
                        resetActiveState = false
                    ).toMutableList()

                    activeSnapshot.openEvents
                        .take(3)
                        .forEach { event ->
                            val configuredIndex = configuredIds.indexOfFirst { it == event.animalId }
                            val targetIndex = if (configuredIndex in restoredAnimals.indices) {
                                configuredIndex
                            } else {
                                restoredAnimals.indexOfFirst { it.activeEventId == null }
                            }

                            if (targetIndex in restoredAnimals.indices) {
                                restoredAnimals[targetIndex] = restoredAnimals[targetIndex].copy(
                                animalId = event.animalId,
                                activeBehaviour = event.behaviour,
                                activeEventId = event.id,
                                activeStartedAtEpochMs = event.startTimeEpochMs
                            )
                            }
                        }

                    _uiState.value = FocalSamplingUiState(
                        isSessionActive = true,
                        activeSessionId = activeSnapshot.session.id,
                        activeSessionStartedAtEpochMs = activeSnapshot.session.startedAtEpochMs,
                        exportSessionId = activeSnapshot.session.id,
                        configuredAnimalCount = activeSnapshot.session.animalCount,
                        showTimeWarning = true,
                        showAnimalCountDialog = false,
                        showStartSessionDialog = false,
                        animals = restoredAnimals
                    )
                } else if (latestSession != null) {
                    _uiState.update {
                        it.copy(
                            exportSessionId = latestSession.id,
                            configuredAnimalCount = latestSession.animalCount,
                            animals = buildAnimalsForCount(
                                currentAnimals = it.animals,
                                animalCount = latestSession.animalCount,
                                configuredIds = SessionAnimalIdsCodec.decode(latestSession.animalIdsJson)
                            )
                        )
                    }
                }
            }
        }
    }

    private fun normalizedAnimalId(input: String, slotIndex: Int): String {
        return input.trim().ifEmpty { "Animal ${slotIndex + 1}" }
    }

    private fun buildAnimalsForCount(
        currentAnimals: List<AnimalPanelUiState>,
        animalCount: Int,
        configuredIds: List<String> = emptyList(),
        resetActiveState: Boolean = true
    ): List<AnimalPanelUiState> {
        val defaults = AnimalPanelUiState.defaults()

        return defaults.mapIndexed { index, default ->
            if (index < animalCount) {
                val current = currentAnimals.getOrNull(index) ?: default
                val configuredAnimal = current.copy(
                    animalId = configuredIds.getOrNull(index)
                        ?: current.animalId.trim().ifEmpty { "Animal ${index + 1}" }
                )

                if (resetActiveState) {
                    configuredAnimal.copy(
                        activeBehaviour = null,
                        activeEventId = null,
                        activeStartedAtEpochMs = null
                    )
                } else {
                    configuredAnimal
                }
            } else {
                default
            }
        }
    }
}
