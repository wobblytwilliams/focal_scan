package au.edu.cqu.focalapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.edu.cqu.focalapp.data.local.SamplingSessionEntity
import au.edu.cqu.focalapp.data.local.SessionFormatVersion
import au.edu.cqu.focalapp.data.repository.FocalRepository
import au.edu.cqu.focalapp.domain.model.AnimalBehaviourTotals
import au.edu.cqu.focalapp.domain.model.Behavior
import au.edu.cqu.focalapp.domain.model.GraphBehaviourCategory
import au.edu.cqu.focalapp.domain.model.TrackedAnimal
import au.edu.cqu.focalapp.util.CsvExportPayload
import au.edu.cqu.focalapp.util.CsvExporter
import au.edu.cqu.focalapp.util.DateTimeFormats
import au.edu.cqu.focalapp.util.SessionAnimalIdsCodec
import au.edu.cqu.focalapp.util.SessionTrackedAnimalsCodec
import au.edu.cqu.focalapp.util.TimeProvider
import kotlinx.coroutines.CoroutineStart
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
    private companion object {
        const val ROLLBACK_WINDOW_MS = 30_000L
    }

    private val actionMutex = Mutex()
    private var hasUserSelectionOverride = false

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

    fun onObserverNameChanged(observerName: String) {
        val state = _uiState.value
        if (state.isSessionActive) {
            return
        }

        _uiState.value = state.copy(
            sessionMetadata = state.sessionMetadata.copy(observerName = observerName)
        )
    }

    fun onTimeOffsetValueChanged(rawValue: String) {
        val state = _uiState.value
        if (state.isSessionActive) {
            return
        }

        _uiState.value = state.copy(
            sessionMetadata = state.sessionMetadata.copy(
                timeOffsetInput = sanitizeTimeOffsetInput(rawValue)
            )
        )
    }

    fun onTimeOffsetDirectionChanged(direction: TimeOffsetDirection) {
        val state = _uiState.value
        if (state.isSessionActive) {
            return
        }

        _uiState.value = state.copy(
            sessionMetadata = state.sessionMetadata.copy(
                timeOffsetDirection = direction
            )
        )
    }

    fun requestStartSession() {
        val state = _uiState.value
        if (state.isSessionActive) {
            return
        }

        val validationMessage = validateStartSession(state)
        if (validationMessage != null) {
            viewModelScope.launch {
                _events.emit(UiEvent.ShowMessage(validationMessage))
            }
            return
        }

        _uiState.update { it.copy(showTimeWarning = true) }
    }

    fun confirmTimeWarning() {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            actionMutex.withLock {
                val state = _uiState.value
                if (state.isSessionActive) {
                    return@withLock
                }

                val validationMessage = validateStartSession(state)
                if (validationMessage != null) {
                    _uiState.value = withGraph(
                        state.copy(showTimeWarning = false),
                        nowEpochMs = timeProvider.nowEpochMillis()
                    )
                    _events.emit(UiEvent.ShowMessage(validationMessage))
                    return@withLock
                }

                startSelectedSessionLocked(state)
            }
        }
    }

    fun toggleAnimalSelection(trackedAnimal: TrackedAnimal) {
        val state = _uiState.value
        if (state.isSessionActive) {
            return
        }

        hasUserSelectionOverride = true

        val updatedAnimals = state.animals.map { animal ->
            if (animal.trackedAnimal == trackedAnimal) {
                animal.copy(
                    isSelected = !animal.isSelected,
                    activeBehaviour = null,
                    activeEventId = null,
                    activeStartedAtEpochMs = null
                )
            } else {
                animal
            }
        }

        _uiState.value = state.copy(
            showTimeWarning = false,
            animals = updatedAnimals
        )
    }

    fun stopSession() {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
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
                val exportPayload = buildCsvExportPayload(sessionId)

                val closedAnimals = state.animals.map { animal ->
                    animal.copy(
                        activeBehaviour = null,
                        activeEventId = null,
                        activeStartedAtEpochMs = null
                    )
                }

                _uiState.value = withGraph(
                    state.copy(
                        isSessionActive = false,
                        activeSessionId = null,
                        activeSessionStartedAtEpochMs = null,
                        exportSessionId = sessionId,
                        showTimeWarning = false,
                        animals = closedAnimals
                    ),
                    nowEpochMs = now
                )

                _events.emit(
                    UiEvent.ShowMessage(
                        "Session #$sessionId stopped. Open behaviours were closed at ${DateTimeFormats.formatLocalTime(now)}."
                    )
                )

                if (exportPayload != null) {
                    _events.emit(UiEvent.ExportCsv(exportPayload))
                } else {
                    _events.emit(UiEvent.ShowMessage("Session #$sessionId could not be prepared for CSV export."))
                }
            }
        }
    }

    fun deleteLast30Seconds(slotIndex: Int) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            actionMutex.withLock {
                val state = _uiState.value
                val sessionId = state.activeSessionId
                val animal = state.animals.getOrNull(slotIndex)

                if (!state.isSessionActive || sessionId == null || animal == null || !animal.isSelected) {
                    return@withLock
                }

                val now = timeProvider.nowEpochMillis()
                val cutoffEpochMs = now - ROLLBACK_WINDOW_MS
                repository.trimAnimalToCutoff(
                    sessionId = sessionId,
                    animalId = animal.animalId,
                    cutoffEpochMs = cutoffEpochMs
                )

                val updatedAnimals = state.animals.toMutableList().apply {
                    this[slotIndex] = animal.copy(
                        activeBehaviour = null,
                        activeEventId = null,
                        activeStartedAtEpochMs = null
                    )
                }

                _uiState.value = withGraph(
                    state.copy(
                        animals = updatedAnimals,
                        exportSessionId = sessionId
                    ),
                    nowEpochMs = now
                )

                _events.emit(
                    UiEvent.ShowMessage(
                        "Deleted the last 30 seconds for ${animal.trackedAnimal.displayName}. Logging for this animal is paused."
                    )
                )
            }
        }
    }

    fun onBehaviourPressed(slotIndex: Int, behaviour: Behavior) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            actionMutex.withLock {
                val state = _uiState.value
                val sessionId = state.activeSessionId

                if (!state.isSessionActive || sessionId == null) {
                    _events.emit(UiEvent.ShowMessage("Start a session before recording behaviour events."))
                    return@withLock
                }

                val animal = state.animals.getOrNull(slotIndex)
                if (animal == null || !animal.isSelected) {
                    return@withLock
                }

                val now = timeProvider.nowEpochMillis()

                val updatedAnimal = when {
                    animal.activeBehaviour == null -> {
                        val newEventId = repository.startEvent(
                            sessionId = sessionId,
                            animalId = animal.animalId,
                            behaviour = behaviour,
                            startTimeEpochMs = now
                        )

                        animal.copy(
                            activeBehaviour = behaviour,
                            activeEventId = newEventId,
                            activeStartedAtEpochMs = now
                        )
                    }

                    animal.activeBehaviour == behaviour -> {
                        animal.activeEventId?.let { repository.endEvent(it, now) }

                        animal.copy(
                            activeBehaviour = null,
                            activeEventId = null,
                            activeStartedAtEpochMs = null
                        )
                    }

                    else -> {
                        animal.activeEventId?.let { repository.endEvent(it, now) }

                        val newEventId = repository.startEvent(
                            sessionId = sessionId,
                            animalId = animal.animalId,
                            behaviour = behaviour,
                            startTimeEpochMs = now
                        )

                        animal.copy(
                            activeBehaviour = behaviour,
                            activeEventId = newEventId,
                            activeStartedAtEpochMs = now
                        )
                    }
                }

                val updatedAnimals = state.animals.toMutableList().apply {
                    this[slotIndex] = updatedAnimal
                }

                _uiState.value = withGraph(
                    state.copy(
                        animals = updatedAnimals,
                        exportSessionId = sessionId
                    ),
                    nowEpochMs = now
                )
            }
        }
    }

    fun exportCsv() {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            actionMutex.withLock {
                val state = _uiState.value
                val sessionId = state.exportSessionId

                if (sessionId == null) {
                    _events.emit(UiEvent.ShowMessage("There is no session available to export yet."))
                    return@withLock
                }

                if (repository.getSessionById(sessionId) == null) {
                    _events.emit(UiEvent.ShowMessage("The selected session could not be found."))
                    return@withLock
                }

                val exportPayload = buildCsvExportPayload(sessionId)
                if (exportPayload == null) {
                    _events.emit(UiEvent.ShowMessage("Session #$sessionId could not be prepared for export."))
                    return@withLock
                }

                _events.emit(UiEvent.ExportCsv(exportPayload))
            }
        }
    }

    fun refreshGraph() {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            actionMutex.withLock {
                val state = _uiState.value
                _uiState.value = withGraph(
                    state,
                    nowEpochMs = timeProvider.nowEpochMillis()
                )
            }
        }
    }

    private suspend fun startSelectedSessionLocked(state: FocalSamplingUiState) {
        val selectedAnimals = state.selectedTrackedAnimals
        val observerName = state.sessionMetadata.trimmedObserverName
        val timeOffsetSeconds = state.sessionMetadata.signedTimeOffsetSeconds ?: 0.0
        val now = timeProvider.nowEpochMillis()
        val sessionId = repository.startSession(
            startedAtEpochMs = now,
            trackedAnimals = selectedAnimals,
            observerName = observerName,
            timeOffsetSeconds = timeOffsetSeconds
        )
        hasUserSelectionOverride = false

        _uiState.value = withGraph(
            state.copy(
                isSessionActive = true,
                activeSessionId = sessionId,
                activeSessionStartedAtEpochMs = now,
                exportSessionId = sessionId,
                showTimeWarning = false,
                sessionMetadata = SessionMetadataUiState.fromStoredValues(
                    observerName = observerName,
                    signedTimeOffsetSeconds = timeOffsetSeconds
                ),
                animals = buildAnimals(
                    currentAnimals = state.animals,
                    selectedAnimals = selectedAnimals,
                    resetActiveState = true
                )
            ),
            nowEpochMs = now
        )

        _events.emit(UiEvent.ShowMessage("Session #$sessionId started."))
    }

    private fun restoreExistingSession() {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            actionMutex.withLock {
                val activeSnapshot = repository.getActiveSessionSnapshot()
                val latestSession = repository.getLatestSession()
                val currentState = _uiState.value

                if (currentState.isSessionActive) {
                    _uiState.value = withGraph(
                        currentState,
                        nowEpochMs = timeProvider.nowEpochMillis()
                    )
                    return@withLock
                }

                val restoredState = when {
                    activeSnapshot != null -> {
                        hasUserSelectionOverride = false
                        val selectedAnimals = trackedAnimalsForSession(activeSnapshot.session)
                        val restoredAnimals = buildAnimals(
                            currentAnimals = AnimalPanelUiState.defaults(selectedAnimals),
                            selectedAnimals = selectedAnimals,
                            resetActiveState = false
                        ).toMutableList()

                        if (activeSnapshot.session.sessionFormatVersion >= SessionFormatVersion.TRACKED_ANIMALS) {
                            activeSnapshot.openEvents.forEach { event ->
                                val trackedAnimal = TrackedAnimal.fromStoredAnimalId(event.animalId) ?: return@forEach
                                val targetIndex = trackedAnimal.ordinal
                                if (targetIndex in restoredAnimals.indices) {
                                    restoredAnimals[targetIndex] = restoredAnimals[targetIndex].copy(
                                        activeBehaviour = event.behaviour,
                                        activeEventId = event.id,
                                        activeStartedAtEpochMs = event.startTimeEpochMs
                                    )
                                }
                            }
                        } else {
                            activeSnapshot.openEvents
                                .take(selectedAnimals.size)
                                .forEachIndexed { index, event ->
                                    val trackedAnimal = selectedAnimals[index]
                                    val targetIndex = trackedAnimal.ordinal
                                    restoredAnimals[targetIndex] = restoredAnimals[targetIndex].copy(
                                        activeBehaviour = event.behaviour,
                                        activeEventId = event.id,
                                        activeStartedAtEpochMs = event.startTimeEpochMs
                                    )
                                }
                        }

                        FocalSamplingUiState(
                            isSessionActive = true,
                            activeSessionId = activeSnapshot.session.id,
                            activeSessionStartedAtEpochMs = activeSnapshot.session.startedAtEpochMs,
                            exportSessionId = activeSnapshot.session.id,
                            showTimeWarning = false,
                            sessionMetadata = SessionMetadataUiState.fromStoredValues(
                                observerName = activeSnapshot.session.observerName,
                                signedTimeOffsetSeconds = activeSnapshot.session.timeOffsetSeconds
                            ),
                            animals = restoredAnimals
                        )
                    }

                    latestSession != null -> {
                        val selectedAnimals = if (hasUserSelectionOverride) {
                            currentState.selectedTrackedAnimals
                        } else {
                            emptyList()
                        }

                        FocalSamplingUiState(
                            exportSessionId = latestSession.id,
                            showTimeWarning = false,
                            sessionMetadata = if (hasUserSelectionOverride || currentState.sessionMetadata != SessionMetadataUiState()) {
                                currentState.sessionMetadata
                            } else {
                                SessionMetadataUiState()
                            },
                            animals = buildAnimals(
                                currentAnimals = currentState.animals,
                                selectedAnimals = selectedAnimals
                            )
                        )
                    }

                    else -> {
                        if (hasUserSelectionOverride || currentState.sessionMetadata != SessionMetadataUiState()) {
                            currentState.copy(showTimeWarning = false)
                        } else {
                            FocalSamplingUiState(
                                sessionMetadata = SessionMetadataUiState(),
                                animals = AnimalPanelUiState.defaults()
                            )
                        }
                    }
                }

                _uiState.value = withGraph(
                    restoredState,
                    nowEpochMs = timeProvider.nowEpochMillis()
                )
            }
        }
    }

    private fun trackedAnimalsForSession(session: SamplingSessionEntity): List<TrackedAnimal> {
        val decodedTrackedAnimals = normalizeTrackedAnimals(
            SessionTrackedAnimalsCodec.decode(session.trackedAnimalsJson)
        )
        if (decodedTrackedAnimals.isNotEmpty()) {
            return decodedTrackedAnimals
        }

        val decodedLegacyIds = normalizeTrackedAnimals(
            SessionAnimalIdsCodec.decode(session.animalIdsJson).mapNotNull(TrackedAnimal::fromStoredAnimalId)
        )
        if (decodedLegacyIds.isNotEmpty()) {
            return decodedLegacyIds
        }

        return TrackedAnimal.entries.take(session.animalCount.coerceIn(0, TrackedAnimal.entries.size))
    }

    private fun normalizeTrackedAnimals(trackedAnimals: List<TrackedAnimal>): List<TrackedAnimal> {
        return TrackedAnimal.entries.filter { it in trackedAnimals }
    }

    private fun buildAnimals(
        currentAnimals: List<AnimalPanelUiState>,
        selectedAnimals: List<TrackedAnimal>,
        resetActiveState: Boolean = true
    ): List<AnimalPanelUiState> {
        return TrackedAnimal.entries.mapIndexed { index, trackedAnimal ->
            val currentAnimal = currentAnimals.getOrNull(index)
                ?: AnimalPanelUiState(
                    slotIndex = index,
                    trackedAnimal = trackedAnimal
                )

            val updatedAnimal = currentAnimal.copy(
                trackedAnimal = trackedAnimal,
                isSelected = trackedAnimal in selectedAnimals
            )

            if (resetActiveState || !updatedAnimal.isSelected) {
                updatedAnimal.copy(
                    activeBehaviour = null,
                    activeEventId = null,
                    activeStartedAtEpochMs = null
                )
            } else {
                updatedAnimal
            }
        }
    }

    private suspend fun withGraph(
        state: FocalSamplingUiState,
        nowEpochMs: Long
    ): FocalSamplingUiState {
        return state.copy(
            graph = buildGraphUiState(
                trackedAnimals = TrackedAnimal.entries.toList(),
                nowEpochMs = nowEpochMs
            )
        )
    }

    private suspend fun buildGraphUiState(
        trackedAnimals: List<TrackedAnimal>,
        nowEpochMs: Long
    ): BehaviourGraphUiState {
        if (trackedAnimals.isEmpty()) {
            return BehaviourGraphUiState()
        }

        val totalsByAnimal = repository.getCumulativeBehaviourTotals(nowEpochMs)
            .associateBy(AnimalBehaviourTotals::trackedAnimal)

        val groups = trackedAnimals.map { trackedAnimal ->
            val totals = totalsByAnimal[trackedAnimal] ?: AnimalBehaviourTotals(trackedAnimal)
            AnimalGraphGroupUiState(
                trackedAnimal = trackedAnimal,
                bars = listOf(
                    AnimalGraphBarUiState(
                        category = GraphBehaviourCategory.WALKING,
                        minutes = totals.walkingDurationMs / 60_000f
                    ),
                    AnimalGraphBarUiState(
                        category = GraphBehaviourCategory.GRAZING,
                        minutes = totals.grazingDurationMs / 60_000f
                    ),
                    AnimalGraphBarUiState(
                        category = GraphBehaviourCategory.IDLE,
                        minutes = totals.idleDurationMs / 60_000f
                    )
                )
            )
        }

        return BehaviourGraphUiState(
            groups = groups,
            yAxisMaxMinutes = calculateGraphAxisMaxMinutes(groups)
        )
    }

    private suspend fun buildCsvExportPayload(sessionId: Long): CsvExportPayload? {
        val session = repository.getSessionById(sessionId) ?: return null
        val events = repository.getEventsForSession(sessionId)
        return CsvExportPayload(
            fileName = CsvExporter.buildFileName(
                sessionId = sessionId,
                sessionStartedAtEpochMs = session.startedAtEpochMs
            ),
            content = CsvExporter.buildCsv(
                session = session,
                events = events
            )
        )
    }

    private fun validateStartSession(state: FocalSamplingUiState): String? {
        return when {
            state.visibleAnimals.isEmpty() ->
                "Select at least one animal before starting a session."

            !state.sessionMetadata.isObserverNameValid ->
                "Enter the observer name before starting a session."

            !state.sessionMetadata.isTimeOffsetValid ->
                "Enter the time.is offset as 0 or a value with up to one decimal place."

            else -> null
        }
    }
}
