package au.edu.cqu.focalapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.edu.cqu.focalapp.data.local.SamplingSessionEntity
import au.edu.cqu.focalapp.data.repository.FocalRepository
import au.edu.cqu.focalapp.domain.model.AnimalBehaviourTotals
import au.edu.cqu.focalapp.domain.model.Behavior
import au.edu.cqu.focalapp.domain.model.GraphBehaviourCategory
import au.edu.cqu.focalapp.domain.model.AnimalColor
import au.edu.cqu.focalapp.util.CsvExportPayload
import au.edu.cqu.focalapp.util.CsvExporter
import au.edu.cqu.focalapp.util.DateTimeFormats
import au.edu.cqu.focalapp.util.SessionAnimalIdsCodec
import au.edu.cqu.focalapp.util.SessionAnimalColorsCodec
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

    private val _uiState = MutableStateFlow(FocalSamplingUiState())
    val uiState: StateFlow<FocalSamplingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val _previousSessions = MutableStateFlow<List<SamplingSessionEntity>?>(null)
    val previousSessions = _previousSessions.asStateFlow()

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
                if (state.isSessionActive || !state.showTimeWarning) {
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

    fun setAnimalCount(count: Int) {
        if (count !in SupportedAnimalCounts) return
        _uiState.update { state ->
            if (state.isSessionActive) state else state.copy(
                animals = List(count) { index -> state.animals.getOrNull(index) ?: AnimalPanelUiState(index) }
            )
        }
    }

    fun setAnimalId(slot: Int, id: String) = editAnimal(slot) { it.copy(animalId = id) }

    fun setAnimalColor(slot: Int, color: AnimalColor) = editAnimal(slot) { it.copy(animalColor = color) }

    private fun editAnimal(slot: Int, edit: (AnimalPanelUiState) -> AnimalPanelUiState) {
        _uiState.update { state ->
            if (state.isSessionActive) state else state.copy(
                animals = state.animals.map { if (it.slotIndex == slot) edit(it) else it }
            )
        }
    }

    fun loadPreviousSessions() {
        viewModelScope.launch {
            actionMutex.withLock { _previousSessions.value = repository.getFinishedSessions() }
        }
    }

    fun clearGraphHistory() {
        viewModelScope.launch {
            actionMutex.withLock {
                val now = timeProvider.nowEpochMillis()
                repository.clearGraphHistory(now)
                updateGraph(now)
            }
        }
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

                repository.stopSession(sessionId, now)
                if (_previousSessions.value != null) {
                    _previousSessions.value = repository.getFinishedSessions()
                }
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

                if (!state.isSessionActive || sessionId == null || animal == null) {
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
                        animals = updatedAnimals
                    ),
                    nowEpochMs = now
                )

                _events.emit(
                    UiEvent.ShowMessage(
                        "Deleted the last 30 seconds for ${animal.animalId}. Logging for this animal is paused."
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
                if (animal == null) {
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
                        animals = updatedAnimals
                    ),
                    nowEpochMs = now
                )
            }
        }
    }

    fun exportCsv(sessionId: Long? = null) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            actionMutex.withLock {
                val targetId = sessionId ?: _uiState.value.exportSessionId

                if (targetId == null) {
                    _events.emit(UiEvent.ShowMessage("There is no completed session to export yet."))
                    return@withLock
                }

                val exportPayload = buildCsvExportPayload(targetId)
                if (exportPayload == null) {
                    _events.emit(UiEvent.ShowMessage("Session #$targetId could not be prepared for export."))
                    return@withLock
                }

                _events.emit(UiEvent.ExportCsv(exportPayload))
            }
        }
    }

    fun refreshGraph() {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            actionMutex.withLock {
                updateGraph(timeProvider.nowEpochMillis())
            }
        }
    }

    private suspend fun startSelectedSessionLocked(state: FocalSamplingUiState) {
        val animals = state.animals.map { animal ->
            val id = animal.animalId.trim()
            val knownId = state.knownAnimalIds.firstOrNull { it.equals(id, ignoreCase = true) }
            animal.copy(animalId = knownId ?: id)
        }
        val observerName = state.sessionMetadata.trimmedObserverName
        val timeOffsetSeconds = state.sessionMetadata.signedTimeOffsetSeconds ?: 0.0
        val now = timeProvider.nowEpochMillis()
        val sessionId = repository.startSession(
            startedAtEpochMs = now,
            animalIds = animals.map { it.animalId },
            animalColors = animals.map { it.animalColor },
            observerName = observerName,
            timeOffsetSeconds = timeOffsetSeconds
        )

        _uiState.value = withGraph(
            state.copy(
                isSessionActive = true,
                activeSessionId = sessionId,
                activeSessionStartedAtEpochMs = now,
                showTimeWarning = false,
                sessionMetadata = SessionMetadataUiState.fromStoredValues(
                    observerName = observerName,
                    signedTimeOffsetSeconds = timeOffsetSeconds
                ),
                animals = animals,
                knownAnimalIds = (animals.map { it.animalId } + state.knownAnimalIds)
                    .distinctBy { it.lowercase() }
            ),
            nowEpochMs = now
        )

        _events.emit(UiEvent.ShowMessage("Session #$sessionId started."))
    }

    private fun restoreExistingSession() {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            actionMutex.withLock {
                val active = repository.getActiveSessionSnapshot()
                val latestFinishedId = repository.getLatestFinishedSession()?.id
                val knownIds = repository.getKnownAnimalIds()
                val now = timeProvider.nowEpochMillis()
                val graphTotals = repository.getCumulativeBehaviourTotals(now)

                _uiState.update { state ->
                    if (active == null) {
                        state.copy(
                            exportSessionId = latestFinishedId,
                            knownAnimalIds = knownIds,
                            graph = buildGraphUiState(knownIds, graphTotals)
                        )
                    } else {
                        val session = active.session
                        val colors = SessionAnimalColorsCodec.decode(session.animalColorsJson)
                        val openEvents = active.openEvents.associateBy { it.animalId }
                        val animals = SessionAnimalIdsCodec.decode(session.animalIdsJson).mapIndexed { index, id ->
                            val event = openEvents[id]
                            AnimalPanelUiState(
                                slotIndex = index,
                                animalId = id,
                                animalColor = colors.getOrElse(index) { AnimalColor.defaultForSlot(index) },
                                activeBehaviour = event?.behaviour,
                                activeEventId = event?.id,
                                activeStartedAtEpochMs = event?.startTimeEpochMs
                            )
                        }
                        state.copy(
                            isSessionActive = true,
                            activeSessionId = session.id,
                            activeSessionStartedAtEpochMs = session.startedAtEpochMs,
                            exportSessionId = latestFinishedId,
                            showTimeWarning = false,
                            sessionMetadata = SessionMetadataUiState.fromStoredValues(
                                session.observerName, session.timeOffsetSeconds
                            ),
                            animals = animals,
                            knownAnimalIds = knownIds,
                            graph = buildGraphUiState(knownIds, graphTotals)
                        )
                    }
                }
            }
        }
    }

    private suspend fun updateGraph(nowEpochMs: Long) {
        val totals = repository.getCumulativeBehaviourTotals(nowEpochMs)
        _uiState.update { it.copy(graph = buildGraphUiState(it.knownAnimalIds, totals)) }
    }

    private suspend fun withGraph(state: FocalSamplingUiState, nowEpochMs: Long): FocalSamplingUiState =
        state.copy(graph = buildGraphUiState(state.knownAnimalIds, repository.getCumulativeBehaviourTotals(nowEpochMs)))

    private fun buildGraphUiState(
        knownIds: List<String>,
        totals: List<AnimalBehaviourTotals>
    ): BehaviourGraphUiState {
        val totalsById = totals.associateBy { it.animalId.lowercase() }
        val ids = (knownIds + totals.map { it.animalId }).distinctBy { it.lowercase() }
        val groups = ids.map { id ->
            val totals = totalsById[id.lowercase()] ?: AnimalBehaviourTotals(id)
            AnimalGraphGroupUiState(
                animalId = id,
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
            !state.hasValidAnimalIds ->
                "Enter a different animal ID for each observation card."

            !state.sessionMetadata.isObserverNameValid ->
                "Enter the observer name before starting a session."

            !state.sessionMetadata.isTimeOffsetValid ->
                "Enter the time.is offset as 0 or a value with up to one decimal place."

            else -> null
        }
    }
}
