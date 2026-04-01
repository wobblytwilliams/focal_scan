package au.edu.cqu.focalapp.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.edu.cqu.focalapp.data.local.FocalDatabase
import au.edu.cqu.focalapp.data.repository.FocalRepository
import au.edu.cqu.focalapp.domain.model.AnimalColor
import au.edu.cqu.focalapp.domain.model.Behavior
import au.edu.cqu.focalapp.util.MainDispatcherRule
import au.edu.cqu.focalapp.util.SessionAnimalColorsCodec
import au.edu.cqu.focalapp.util.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FocalSamplingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: FocalDatabase
    private lateinit var repository: FocalRepository
    private lateinit var timeProvider: FakeTimeProvider

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FocalDatabase::class.java
        ).build()
        repository = FocalRepository(database.focalDao())
        timeProvider = FakeTimeProvider(now = 1_000L)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun requestStartSession_showsTimeWarningBeforeSetup() = runTest {
        val viewModel = createViewModel()

        viewModel.setAnimalCount(2)
        viewModel.requestStartSession()

        assertTrue(viewModel.uiState.value.showTimeWarning)
        assertFalse(viewModel.uiState.value.showStartSessionDialog)
    }

    @Test
    fun confirmTimeWarning_opensStartSessionDialog() = runTest {
        val viewModel = createViewModel()

        viewModel.setAnimalCount(2)
        viewModel.requestStartSession()
        viewModel.confirmTimeWarning()

        assertFalse(viewModel.uiState.value.showTimeWarning)
        assertTrue(viewModel.uiState.value.showStartSessionDialog)
    }

    @Test
    fun dismissTimeWarning_returnsToMainScreen() = runTest {
        val viewModel = createViewModel()

        viewModel.setAnimalCount(2)
        viewModel.requestStartSession()
        viewModel.dismissTimeWarning()

        assertFalse(viewModel.uiState.value.showTimeWarning)
        assertFalse(viewModel.uiState.value.showStartSessionDialog)
    }

    @Test
    fun startSession_persistsSelectedColorsAndRestoresThem() = runTest {
        val viewModel = createViewModel()

        viewModel.setAnimalCount(2)
        viewModel.requestStartSession()
        viewModel.confirmTimeWarning()
        viewModel.startSession(
            animalIds = listOf("Ewe 17", "Ewe 22"),
            animalColors = listOf(AnimalColor.YELLOW, AnimalColor.BLUE)
        )
        advanceUntilIdle()

        val activeState = viewModel.uiState.value
        assertTrue(activeState.isSessionActive)
        assertEquals(
            listOf(AnimalColor.YELLOW, AnimalColor.BLUE),
            activeState.visibleAnimals.map(AnimalPanelUiState::animalColor)
        )

        val session = repository.getSessionById(activeState.activeSessionId!!)!!
        assertEquals(
            listOf(AnimalColor.YELLOW, AnimalColor.BLUE),
            SessionAnimalColorsCodec.decode(session.animalColorsJson)
        )

        val restoredViewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(restoredViewModel.uiState.value.isSessionActive)
        assertFalse(restoredViewModel.uiState.value.showTimeWarning)
        assertEquals(
            listOf(AnimalColor.YELLOW, AnimalColor.BLUE),
            restoredViewModel.uiState.value.visibleAnimals.map(AnimalPanelUiState::animalColor)
        )
    }

    @Test
    fun deleteLast30Seconds_clearsActiveBehavioursWithoutStoppingSession() = runTest {
        val viewModel = createViewModel()

        viewModel.setAnimalCount(1)
        viewModel.requestStartSession()
        viewModel.confirmTimeWarning()
        viewModel.startSession(
            animalIds = listOf("Ram 3"),
            animalColors = listOf(AnimalColor.BLUE)
        )
        advanceUntilIdle()

        timeProvider.now = 105_000L
        viewModel.onBehaviourPressed(slotIndex = 0, behaviour = Behavior.GRAZING)
        advanceUntilIdle()

        timeProvider.now = 135_000L
        viewModel.deleteLast30Seconds()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSessionActive)
        assertNull(state.visibleAnimals.single().activeBehaviour)
        assertTrue(repository.getEventsForSession(state.activeSessionId!!).isEmpty())
        assertNull(repository.getSessionById(state.activeSessionId!!)?.endedAtEpochMs)
    }

    private fun createViewModel(): FocalSamplingViewModel {
        return FocalSamplingViewModel(
            repository = repository,
            timeProvider = timeProvider
        )
    }

    private class FakeTimeProvider(
        var now: Long
    ) : TimeProvider {
        override fun nowEpochMillis(): Long = now
    }
}
