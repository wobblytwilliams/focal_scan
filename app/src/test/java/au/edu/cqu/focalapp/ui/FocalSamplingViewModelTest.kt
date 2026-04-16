package au.edu.cqu.focalapp.ui

import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.edu.cqu.focalapp.data.local.FocalDatabase
import au.edu.cqu.focalapp.data.local.SessionFormatVersion
import au.edu.cqu.focalapp.data.repository.FocalRepository
import au.edu.cqu.focalapp.domain.model.Behavior
import au.edu.cqu.focalapp.domain.model.TrackedAnimal
import au.edu.cqu.focalapp.util.MainDispatcherRule
import au.edu.cqu.focalapp.util.SessionTrackedAnimalsCodec
import au.edu.cqu.focalapp.util.TimeProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FocalSamplingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: FocalDatabase
    private lateinit var repository: FocalRepository
    private lateinit var timeProvider: FakeTimeProvider
    private val directExecutor = Executor { runnable -> runnable.run() }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FocalDatabase::class.java
        )
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        repository = FocalRepository(database.focalDao())
        timeProvider = FakeTimeProvider(now = 1_000L)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun initialState_startsWithNoAnimalsSelected() = runTest {
        val viewModel = createViewModel()
        flushViewModelWork()

        assertTrue(viewModel.uiState.value.visibleAnimals.isEmpty())
        assertEquals(
            listOf(TrackedAnimal.BLUE, TrackedAnimal.GREEN, TrackedAnimal.YELLOW),
            viewModel.uiState.value.graph.groups.map(AnimalGraphGroupUiState::trackedAnimal)
        )
    }

    @Test
    fun toggleAnimalSelection_filtersVisibleAnimalsButKeepsAllGraphGroups() = runTest {
        val viewModel = createViewModel()
        flushViewModelWork()

        viewModel.toggleAnimalSelection(TrackedAnimal.BLUE)
        viewModel.toggleAnimalSelection(TrackedAnimal.GREEN)
        flushViewModelWork()

        val state = viewModel.uiState.value
        assertEquals(
            listOf(TrackedAnimal.BLUE, TrackedAnimal.GREEN),
            state.visibleAnimals.map(AnimalPanelUiState::trackedAnimal)
        )
        assertEquals(
            listOf(TrackedAnimal.BLUE, TrackedAnimal.GREEN, TrackedAnimal.YELLOW),
            state.graph.groups.map(AnimalGraphGroupUiState::trackedAnimal)
        )
    }

    @Test
    fun requestStartSession_showsTimeWarningBeforeStarting() = runTest {
        val viewModel = createViewModel()
        flushViewModelWork()
        viewModel.toggleAnimalSelection(TrackedAnimal.BLUE)
        populateValidSessionMetadata(viewModel)
        flushViewModelWork()

        viewModel.requestStartSession()

        assertTrue(viewModel.uiState.value.showTimeWarning)
    }

    @Test
    fun confirmTimeWarning_startsSessionWithSelectedAnimals() = runTest {
        val viewModel = createViewModel()
        flushViewModelWork()
        viewModel.toggleAnimalSelection(TrackedAnimal.BLUE)
        viewModel.toggleAnimalSelection(TrackedAnimal.GREEN)
        populateValidSessionMetadata(viewModel)
        flushViewModelWork()

        viewModel.requestStartSession()
        viewModel.confirmTimeWarning()
        flushViewModelWork()

        val activeState = viewModel.uiState.value
        assertTrue(activeState.isSessionActive)
        assertEquals(
            listOf(TrackedAnimal.BLUE, TrackedAnimal.GREEN),
            activeState.visibleAnimals.map(AnimalPanelUiState::trackedAnimal)
        )

        val session = repository.getSessionById(activeState.activeSessionId!!)!!
        assertEquals(SessionFormatVersion.TRACKED_ANIMALS, session.sessionFormatVersion)
        assertEquals(
            listOf(TrackedAnimal.BLUE, TrackedAnimal.GREEN),
            SessionTrackedAnimalsCodec.decode(session.trackedAnimalsJson)
        )
        assertEquals("Observer One", session.observerName)
        assertEquals(-0.3, session.timeOffsetSeconds, 0.0)

        val restoredViewModel = createViewModel()
        flushViewModelWork()

        assertTrue(restoredViewModel.uiState.value.isSessionActive)
        assertEquals(
            listOf(TrackedAnimal.BLUE, TrackedAnimal.GREEN),
            restoredViewModel.uiState.value.visibleAnimals.map(AnimalPanelUiState::trackedAnimal)
        )
        assertEquals("Observer One", restoredViewModel.uiState.value.sessionMetadata.observerName)
        assertEquals("0.3", restoredViewModel.uiState.value.sessionMetadata.timeOffsetInput)
        assertEquals(TimeOffsetDirection.BEHIND, restoredViewModel.uiState.value.sessionMetadata.timeOffsetDirection)
    }

    @Test
    fun dismissTimeWarning_returnsToMainScreen() = runTest {
        val viewModel = createViewModel()
        flushViewModelWork()
        viewModel.toggleAnimalSelection(TrackedAnimal.BLUE)
        populateValidSessionMetadata(viewModel)
        flushViewModelWork()

        viewModel.requestStartSession()
        viewModel.dismissTimeWarning()

        assertFalse(viewModel.uiState.value.showTimeWarning)
    }

    @Test
    fun selectionCannotChangeDuringActiveSession() = runTest {
        val viewModel = createViewModel()
        flushViewModelWork()

        viewModel.toggleAnimalSelection(TrackedAnimal.BLUE)
        viewModel.toggleAnimalSelection(TrackedAnimal.GREEN)
        populateValidSessionMetadata(viewModel)
        flushViewModelWork()
        viewModel.requestStartSession()
        viewModel.confirmTimeWarning()
        flushViewModelWork()

        viewModel.toggleAnimalSelection(TrackedAnimal.YELLOW)
        flushViewModelWork()

        assertEquals(
            listOf(TrackedAnimal.BLUE, TrackedAnimal.GREEN),
            viewModel.uiState.value.visibleAnimals.map(AnimalPanelUiState::trackedAnimal)
        )
    }

    @Test
    fun deleteLast30Seconds_onlyClearsSelectedAnimalWithoutStoppingSession() = runTest {
        val viewModel = createViewModel()
        flushViewModelWork()

        viewModel.toggleAnimalSelection(TrackedAnimal.BLUE)
        viewModel.toggleAnimalSelection(TrackedAnimal.GREEN)
        populateValidSessionMetadata(viewModel)
        flushViewModelWork()
        viewModel.requestStartSession()
        viewModel.confirmTimeWarning()
        flushViewModelWork()

        timeProvider.now = 105_000L
        viewModel.onBehaviourPressed(
            slotIndex = TrackedAnimal.BLUE.ordinal,
            behaviour = Behavior.GRAZING
        )
        flushViewModelWork()

        timeProvider.now = 110_000L
        viewModel.onBehaviourPressed(
            slotIndex = TrackedAnimal.GREEN.ordinal,
            behaviour = Behavior.WALKING
        )
        flushViewModelWork()

        timeProvider.now = 135_000L
        viewModel.deleteLast30Seconds(slotIndex = TrackedAnimal.BLUE.ordinal)
        flushViewModelWork()

        val state = viewModel.uiState.value
        assertTrue(state.isSessionActive)
        assertNull(state.animals[TrackedAnimal.BLUE.ordinal].activeBehaviour)
        assertEquals(
            Behavior.WALKING,
            state.animals[TrackedAnimal.GREEN.ordinal].activeBehaviour
        )

        val events = repository.getEventsForSession(state.activeSessionId!!)
        assertEquals(1, events.size)
        assertEquals(TrackedAnimal.GREEN.displayName, events.single().animalId)
        assertNull(events.single().endTimeEpochMs)
        assertNull(repository.getSessionById(state.activeSessionId!!)?.endedAtEpochMs)
    }

    @Test
    fun requestStartSession_doesNotOpenWarningWhenNothingIsSelected() = runTest {
        val viewModel = createViewModel()
        flushViewModelWork()
        populateValidSessionMetadata(viewModel)
        viewModel.requestStartSession()
        flushViewModelWork()

        assertFalse(viewModel.uiState.value.showTimeWarning)
        assertTrue(viewModel.uiState.value.visibleAnimals.isEmpty())
        assertEquals(
            listOf(TrackedAnimal.BLUE, TrackedAnimal.GREEN, TrackedAnimal.YELLOW),
            viewModel.uiState.value.graph.groups.map(AnimalGraphGroupUiState::trackedAnimal)
        )
    }

    @Test
    fun requestStartSession_requiresObserverName() = runTest {
        val viewModel = createViewModel()
        flushViewModelWork()
        viewModel.toggleAnimalSelection(TrackedAnimal.BLUE)
        viewModel.onTimeOffsetValueChanged("0")
        flushViewModelWork()

        viewModel.requestStartSession()
        flushViewModelWork()

        assertFalse(viewModel.uiState.value.showTimeWarning)
        assertFalse(viewModel.uiState.value.canStartSession)
    }

    @Test
    fun requestStartSession_requiresValidTimeOffset() = runTest {
        val viewModel = createViewModel()
        flushViewModelWork()
        viewModel.toggleAnimalSelection(TrackedAnimal.BLUE)
        viewModel.onObserverNameChanged("Observer Two")
        viewModel.onTimeOffsetValueChanged("0.33")
        flushViewModelWork()

        viewModel.requestStartSession()
        flushViewModelWork()

        assertFalse(viewModel.uiState.value.showTimeWarning)
        assertFalse(viewModel.uiState.value.sessionMetadata.isTimeOffsetValid)
    }

    @Test
    fun confirmTimeWarning_acceptsZeroOffset() = runTest {
        val zeroViewModel = createViewModel()
        flushViewModelWork()
        zeroViewModel.toggleAnimalSelection(TrackedAnimal.BLUE)
        zeroViewModel.onObserverNameChanged("Observer Zero")
        zeroViewModel.onTimeOffsetValueChanged("0.0")
        flushViewModelWork()

        zeroViewModel.requestStartSession()
        zeroViewModel.confirmTimeWarning()
        flushViewModelWork()

        assertEquals(
            0.0,
            repository.getSessionById(zeroViewModel.uiState.value.activeSessionId!!)?.timeOffsetSeconds ?: -1.0,
            0.0
        )
    }

    @Test
    fun confirmTimeWarning_acceptsOneDecimalOffset() = runTest {
        val decimalViewModel = createViewModel()
        flushViewModelWork()
        decimalViewModel.toggleAnimalSelection(TrackedAnimal.GREEN)
        decimalViewModel.onObserverNameChanged("Observer Decimal")
        decimalViewModel.onTimeOffsetDirectionChanged(TimeOffsetDirection.AHEAD)
        decimalViewModel.onTimeOffsetValueChanged("1.2")
        flushViewModelWork()

        decimalViewModel.requestStartSession()
        decimalViewModel.confirmTimeWarning()
        flushViewModelWork()

        assertEquals(
            1.2,
            repository.getSessionById(decimalViewModel.uiState.value.activeSessionId!!)?.timeOffsetSeconds ?: -1.0,
            0.0
        )
    }

    @Test
    fun stopSession_emitsExportEventImmediately() = runTest {
        val viewModel = createViewModel()
        flushViewModelWork()
        viewModel.toggleAnimalSelection(TrackedAnimal.BLUE)
        populateValidSessionMetadata(viewModel)
        flushViewModelWork()

        viewModel.requestStartSession()
        viewModel.confirmTimeWarning()
        flushViewModelWork()

        val exportEvent = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.events.first { it is UiEvent.ExportCsv } as UiEvent.ExportCsv
        }

        viewModel.stopSession()
        flushViewModelWork()

        val payload = exportEvent.await().payload
        assertTrue(payload.content.startsWith("session_id,observer_name,time_offset_seconds"))
        assertEquals(
            "session_id,observer_name,time_offset_seconds,animal_id,behaviour,start_time,end_time",
            payload.content.trim()
        )
    }

    @Test
    fun exportCsv_allowsHeaderOnlyExportForStoppedSessionWithNoEvents() = runTest {
        val viewModel = createViewModel()
        flushViewModelWork()
        viewModel.toggleAnimalSelection(TrackedAnimal.BLUE)
        populateValidSessionMetadata(viewModel)
        flushViewModelWork()

        viewModel.requestStartSession()
        viewModel.confirmTimeWarning()
        flushViewModelWork()

        val automaticExport = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.events.first { it is UiEvent.ExportCsv } as UiEvent.ExportCsv
        }

        viewModel.stopSession()
        flushViewModelWork()
        automaticExport.await()

        val manualExport = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.events.first { it is UiEvent.ExportCsv } as UiEvent.ExportCsv
        }

        viewModel.exportCsv()
        flushViewModelWork()

        val payload = manualExport.await().payload
        assertTrue(payload.content.trim() == "session_id,observer_name,time_offset_seconds,animal_id,behaviour,start_time,end_time")
    }

    private fun createViewModel(): FocalSamplingViewModel {
        return FocalSamplingViewModel(
            repository = repository,
            timeProvider = timeProvider
        )
    }

    private fun populateValidSessionMetadata(viewModel: FocalSamplingViewModel) {
        viewModel.onObserverNameChanged("Observer One")
        viewModel.onTimeOffsetDirectionChanged(TimeOffsetDirection.BEHIND)
        viewModel.onTimeOffsetValueChanged("0.3")
    }

    private fun TestScope.flushViewModelWork() {
        advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()
        advanceUntilIdle()
    }

    private class FakeTimeProvider(
        var now: Long
    ) : TimeProvider {
        override fun nowEpochMillis(): Long = now
    }
}
