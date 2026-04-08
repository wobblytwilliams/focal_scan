package au.edu.cqu.focalapp.ui

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
    fun toggleAnimalSelection_filtersVisibleAnimalsButKeepsAllGraphGroups() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleAnimalSelection(TrackedAnimal.YELLOW)
        advanceUntilIdle()

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
        advanceUntilIdle()

        viewModel.requestStartSession()

        assertTrue(viewModel.uiState.value.showTimeWarning)
    }

    @Test
    fun confirmTimeWarning_startsSessionWithSelectedAnimals() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.toggleAnimalSelection(TrackedAnimal.YELLOW)
        advanceUntilIdle()

        viewModel.requestStartSession()
        viewModel.confirmTimeWarning()
        advanceUntilIdle()

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

        val restoredViewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(restoredViewModel.uiState.value.isSessionActive)
        assertEquals(
            listOf(TrackedAnimal.BLUE, TrackedAnimal.GREEN),
            restoredViewModel.uiState.value.visibleAnimals.map(AnimalPanelUiState::trackedAnimal)
        )
    }

    @Test
    fun dismissTimeWarning_returnsToMainScreen() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestStartSession()
        viewModel.dismissTimeWarning()

        assertFalse(viewModel.uiState.value.showTimeWarning)
    }

    @Test
    fun selectionCannotChangeDuringActiveSession() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleAnimalSelection(TrackedAnimal.YELLOW)
        advanceUntilIdle()
        viewModel.requestStartSession()
        viewModel.confirmTimeWarning()
        advanceUntilIdle()

        viewModel.toggleAnimalSelection(TrackedAnimal.YELLOW)
        advanceUntilIdle()

        assertEquals(
            listOf(TrackedAnimal.BLUE, TrackedAnimal.GREEN),
            viewModel.uiState.value.visibleAnimals.map(AnimalPanelUiState::trackedAnimal)
        )
    }

    @Test
    fun deleteLast30Seconds_onlyClearsSelectedAnimalWithoutStoppingSession() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleAnimalSelection(TrackedAnimal.YELLOW)
        advanceUntilIdle()
        viewModel.requestStartSession()
        viewModel.confirmTimeWarning()
        advanceUntilIdle()

        timeProvider.now = 105_000L
        viewModel.onBehaviourPressed(
            slotIndex = TrackedAnimal.BLUE.ordinal,
            behaviour = Behavior.GRAZING
        )
        advanceUntilIdle()

        timeProvider.now = 110_000L
        viewModel.onBehaviourPressed(
            slotIndex = TrackedAnimal.GREEN.ordinal,
            behaviour = Behavior.WALKING
        )
        advanceUntilIdle()

        timeProvider.now = 135_000L
        viewModel.deleteLast30Seconds(slotIndex = TrackedAnimal.BLUE.ordinal)
        advanceUntilIdle()

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
        advanceUntilIdle()

        TrackedAnimal.entries.forEach(viewModel::toggleAnimalSelection)
        advanceUntilIdle()
        viewModel.requestStartSession()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showTimeWarning)
        assertTrue(viewModel.uiState.value.visibleAnimals.isEmpty())
        assertEquals(
            listOf(TrackedAnimal.BLUE, TrackedAnimal.GREEN, TrackedAnimal.YELLOW),
            viewModel.uiState.value.graph.groups.map(AnimalGraphGroupUiState::trackedAnimal)
        )
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
