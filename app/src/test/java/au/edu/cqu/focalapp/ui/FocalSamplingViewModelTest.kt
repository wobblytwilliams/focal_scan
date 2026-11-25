package au.edu.cqu.focalapp.ui

import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.edu.cqu.focalapp.data.local.FocalDatabase
import au.edu.cqu.focalapp.data.local.SessionFormatVersion
import au.edu.cqu.focalapp.data.repository.FocalRepository
import au.edu.cqu.focalapp.domain.model.Behavior
import au.edu.cqu.focalapp.domain.model.AnimalColor
import au.edu.cqu.focalapp.util.MainDispatcherRule
import au.edu.cqu.focalapp.util.SessionAnimalIdsCodec
import au.edu.cqu.focalapp.util.SessionAnimalColorsCodec
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
    fun changingAnimalCountPreservesExistingIdsAndColours() = runTest {
        val viewModel = createViewModel()
        flushViewModelWork()
        assertFalse(viewModel.uiState.value.canStartSession)
        assertTrue(viewModel.uiState.value.graph.groups.isEmpty())

        viewModel.setAnimalId(0, "Cow-01")
        viewModel.setAnimalColor(0, AnimalColor.PINK)
        viewModel.setAnimalCount(5)
        viewModel.setAnimalId(1, " cow-01 ")
        assertFalse(viewModel.uiState.value.hasValidAnimalIds)
        assertEquals(5, viewModel.uiState.value.animals.size)
        viewModel.setAnimalCount(1)
        assertEquals("Cow-01", viewModel.uiState.value.animals.single().animalId)
        assertEquals(AnimalColor.PINK, viewModel.uiState.value.animals.single().animalColor)
    }

    @Test
    fun requestStartSession_showsTimeWarningBeforeStarting() = runTest {
        val viewModel = createViewModel()
        flushViewModelWork()
        viewModel.setAnimalId(0, "Cow-01")
        populateValidSessionMetadata(viewModel)
        flushViewModelWork()

        viewModel.requestStartSession()

        assertTrue(viewModel.uiState.value.showTimeWarning)
    }

    @Test
    fun confirmTimeWarning_startsSessionWithSelectedAnimals() = runTest {
        val viewModel = createViewModel()
        flushViewModelWork()
        viewModel.setAnimalCount(2)
        viewModel.setAnimalId(0, "Cow-01")
        viewModel.setAnimalId(1, "Cow-02")
        populateValidSessionMetadata(viewModel)
        flushViewModelWork()

        viewModel.requestStartSession()
        viewModel.confirmTimeWarning()
        flushViewModelWork()

        val activeState = viewModel.uiState.value
        assertTrue(activeState.isSessionActive)
        assertEquals(
            listOf("Cow-01", "Cow-02"),
            activeState.animals.map(AnimalPanelUiState::animalId)
        )

        val session = repository.getSessionById(activeState.activeSessionId!!)!!
        assertEquals(SessionFormatVersion.CUSTOM_ANIMALS, session.sessionFormatVersion)
        assertEquals(
            listOf("Cow-01", "Cow-02"),
            SessionAnimalIdsCodec.decode(session.animalIdsJson)
        )
        assertEquals(listOf(AnimalColor.BLUE, AnimalColor.GREEN), SessionAnimalColorsCodec.decode(session.animalColorsJson))
        assertEquals("Observer One", session.observerName)
        assertEquals(-0.3, session.timeOffsetSeconds, 0.0)

        val restoredViewModel = createViewModel()
        flushViewModelWork()

        assertTrue(restoredViewModel.uiState.value.isSessionActive)
        assertEquals(
            listOf("Cow-01", "Cow-02"),
            restoredViewModel.uiState.value.animals.map(AnimalPanelUiState::animalId)
        )
        assertEquals(listOf("Cow-01", "Cow-02"), restoredViewModel.uiState.value.knownAnimalIds)
        assertEquals("Observer One", restoredViewModel.uiState.value.sessionMetadata.observerName)
        assertEquals("0.3", restoredViewModel.uiState.value.sessionMetadata.timeOffsetInput)
        assertEquals(TimeOffsetDirection.BEHIND, restoredViewModel.uiState.value.sessionMetadata.timeOffsetDirection)
    }

    @Test
    fun dismissTimeWarning_returnsToMainScreen() = runTest {
        val viewModel = createViewModel()
        flushViewModelWork()
        viewModel.setAnimalId(0, "Cow-01")
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

        viewModel.setAnimalCount(2)
        viewModel.setAnimalId(0, "Cow-01")
        viewModel.setAnimalId(1, "Cow-02")
        populateValidSessionMetadata(viewModel)
        flushViewModelWork()
        viewModel.requestStartSession()
        viewModel.confirmTimeWarning()
        flushViewModelWork()

        viewModel.setAnimalCount(3)
        viewModel.setAnimalId(0, "Changed")
        viewModel.setAnimalColor(0, AnimalColor.ORANGE)
        flushViewModelWork()

        assertEquals(
            listOf("Cow-01", "Cow-02"),
            viewModel.uiState.value.animals.map(AnimalPanelUiState::animalId)
        )
    }

    @Test
    fun deleteLast30Seconds_onlyClearsSelectedAnimalWithoutStoppingSession() = runTest {
        val viewModel = createViewModel()
        flushViewModelWork()

        viewModel.setAnimalCount(2)
        viewModel.setAnimalId(0, "Cow-01")
        viewModel.setAnimalId(1, "Cow-02")
        populateValidSessionMetadata(viewModel)
        flushViewModelWork()
        viewModel.requestStartSession()
        viewModel.confirmTimeWarning()
        flushViewModelWork()

        timeProvider.now = 105_000L
        viewModel.onBehaviourPressed(
            slotIndex = 0,
            behaviour = Behavior.GRAZING
        )
        flushViewModelWork()

        timeProvider.now = 110_000L
        viewModel.onBehaviourPressed(
            slotIndex = 1,
            behaviour = Behavior.WALKING
        )
        flushViewModelWork()

        timeProvider.now = 135_000L
        viewModel.deleteLast30Seconds(slotIndex = 0)
        flushViewModelWork()

        val state = viewModel.uiState.value
        assertTrue(state.isSessionActive)
        assertNull(state.animals[0].activeBehaviour)
        assertEquals(
            Behavior.WALKING,
            state.animals[1].activeBehaviour
        )

        val events = repository.getEventsForSession(state.activeSessionId!!)
        assertEquals(1, events.size)
        assertEquals("Cow-02", events.single().animalId)
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
        assertTrue(viewModel.uiState.value.animals.single().animalId.isEmpty())
        assertTrue(viewModel.uiState.value.graph.groups.isEmpty())
    }

    @Test
    fun requestStartSession_requiresObserverName() = runTest {
        val viewModel = createViewModel()
        flushViewModelWork()
        viewModel.setAnimalId(0, "Cow-01")
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
        viewModel.setAnimalId(0, "Cow-01")
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
        zeroViewModel.setAnimalId(0, "Cow-01")
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
        decimalViewModel.setAnimalId(0, "Cow-02")
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
        viewModel.setAnimalId(0, "Cow-01")
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
        viewModel.setAnimalId(0, "Cow-01")
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

    @Test
    fun previousSessionsExportTheChosenSessionAndMainExportUsesLatestCompletedSession() = runTest {
        suspend fun saveSession(id: String): Long = repository.startSession(
            startedAtEpochMs = timeProvider.now,
            animalIds = listOf(id),
            animalColors = listOf(AnimalColor.BLUE),
            observerName = "Observer One",
            timeOffsetSeconds = 0.0
        )

        val older = saveSession("Cow-01")
        repository.stopSession(older, timeProvider.now)
        val latest = saveSession("Cow-02")
        repository.stopSession(latest, timeProvider.now)
        saveSession("Cow-03")

        val viewModel = createViewModel()
        flushViewModelWork()
        viewModel.loadPreviousSessions()
        flushViewModelWork()
        assertEquals(listOf(latest, older), viewModel.previousSessions.value?.map { it.id })
        assertTrue(viewModel.uiState.value.isSessionActive)

        suspend fun exportFileName(sessionId: Long? = null): String {
            val export = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.events.first { it is UiEvent.ExportCsv } as UiEvent.ExportCsv
            }
            viewModel.exportCsv(sessionId)
            flushViewModelWork()
            return export.await().payload.fileName
        }

        assertTrue(exportFileName(older).startsWith("focal_session_${older}_"))
        assertTrue(exportFileName().startsWith("focal_session_${latest}_"))
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
