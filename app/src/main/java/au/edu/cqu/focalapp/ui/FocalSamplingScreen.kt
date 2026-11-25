package au.edu.cqu.focalapp.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.edu.cqu.focalapp.domain.model.Behavior
import au.edu.cqu.focalapp.domain.model.AnimalColor
import au.edu.cqu.focalapp.data.local.SamplingSessionEntity
import au.edu.cqu.focalapp.util.SessionAnimalIdsCodec
import au.edu.cqu.focalapp.ui.components.AnimalPanelCard
import au.edu.cqu.focalapp.ui.components.CumulativeBehaviourGraphCard
import au.edu.cqu.focalapp.util.CsvExportPayload
import au.edu.cqu.focalapp.util.CsvExporter
import au.edu.cqu.focalapp.util.DateTimeFormats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FocalSamplingScreen(
    viewModel: FocalSamplingViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val previousSessions by viewModel.previousSessions.collectAsStateWithLifecycle()
    var showPreviousSessions by rememberSaveable { mutableStateOf(false) }
    var confirmGraphReset by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val configuration = LocalConfiguration.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val liveClockText by rememberLiveClockText()
    var showExitConfirmation by remember { mutableStateOf(false) }
    val isPortraitTablet = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        configuration.screenWidthDp >= 600 && configuration.screenHeightDp > configuration.screenWidthDp
    }
    val mainScrollState = rememberScrollState()
    val screenPadding = if (isPortraitTablet) 24.dp else 16.dp
    val verticalSpacing = if (isPortraitTablet) 20.dp else 16.dp
    val animalMinHeight = remember(
        configuration.screenHeightDp,
        isPortraitTablet,
        uiState.animals.size
    ) {
        val visibleAnimalCount = uiState.animals.size.coerceAtLeast(1)
        val perAnimalMinHeight = when {
            isPortraitTablet && visibleAnimalCount == 1 -> 360.dp
            isPortraitTablet && visibleAnimalCount == 2 -> 290.dp
            isPortraitTablet -> 250.dp
            visibleAnimalCount == 1 -> 300.dp
            visibleAnimalCount == 2 -> 240.dp
            else -> 220.dp
        }
        val viewportTarget = configuration.screenHeightDp.dp * if (isPortraitTablet) 0.52f else 0.46f
        (viewportTarget / visibleAnimalCount).coerceAtLeast(perAnimalMinHeight)
    }

    var pendingExport by remember { mutableStateOf<CsvExportPayload?>(null) }

    BackHandler(enabled = uiState.isSessionActive || showPreviousSessions) {
        if (showPreviousSessions) showPreviousSessions = false else showExitConfirmation = true
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val exportPayload = pendingExport
        pendingExport = null

        when {
            uri == null -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("CSV export cancelled.")
                }
            }

            exportPayload == null -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Nothing was queued for export.")
                }
            }

            else -> {
                coroutineScope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            CsvExporter.writeToUri(
                                context = context,
                                uri = uri,
                                content = exportPayload.content
                            )
                        }
                    }.onSuccess {
                        snackbarHostState.showSnackbar("CSV saved to the selected location.")
                    }.onFailure { throwable ->
                        snackbarHostState.showSnackbar(
                            "CSV export failed: ${throwable.message ?: "Unknown error"}"
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> launch { snackbarHostState.showSnackbar(event.message) }
                is UiEvent.ExportCsv -> {
                    pendingExport = event.payload
                    exportLauncher.launch(event.payload.fileName)
                }
            }
        }
    }

    LaunchedEffect(viewModel, uiState.isSessionActive) {
        if (uiState.isSessionActive) {
            while (true) {
                viewModel.refreshGraph()
                delay(1_000L)
            }
        }
    }

    if (uiState.showTimeWarning) {
        TimeWarningDialog(
            onConfirm = viewModel::confirmTimeWarning,
            onGoBack = viewModel::dismissTimeWarning
        )
    }

    if (showExitConfirmation) {
        ExitSessionDialog(
            onStay = { showExitConfirmation = false },
            onLeave = {
                showExitConfirmation = false
                activity?.finish()
            }
        )
    }

    if (confirmGraphReset) {
        AlertDialog(
            onDismissRequest = { confirmGraphReset = false },
            title = { Text("Clear graph history?") },
            text = { Text("All graph totals will return to zero. Saved sessions and CSV exports will stay available.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmGraphReset = false
                    viewModel.clearGraphHistory()
                }) { Text("Clear history") }
            },
            dismissButton = {
                TextButton(onClick = { confirmGraphReset = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        if (showPreviousSessions) {
            PreviousSessionsPage(
                sessions = previousSessions,
                onBack = { showPreviousSessions = false },
                onExport = { viewModel.exportCsv(it) },
                modifier = modifier.padding(paddingValues)
            )
            return@Scaffold
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(mainScrollState)
                .padding(screenPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            ClockCard(
                liveClockText = liveClockText,
                sessionActive = uiState.isSessionActive,
                activeSessionId = uiState.activeSessionId,
                activeSessionStartedAtEpochMs = uiState.activeSessionStartedAtEpochMs,
                isPortraitTablet = isPortraitTablet
            )

            if (!uiState.isSessionActive) {
                AnimalDetailsCard(
                    animals = uiState.animals,
                    knownIds = uiState.knownAnimalIds,
                    onCountChanged = viewModel::setAnimalCount,
                    onIdChanged = viewModel::setAnimalId,
                    onColorChanged = viewModel::setAnimalColor
                )
            }

            SessionDetailsCard(
                uiState = uiState,
                isPortraitTablet = isPortraitTablet,
                onObserverNameChanged = viewModel::onObserverNameChanged,
                onTimeOffsetValueChanged = viewModel::onTimeOffsetValueChanged,
                onTimeOffsetDirectionChanged = viewModel::onTimeOffsetDirectionChanged
            )

            SessionControlsCard(
                uiState = uiState,
                isPortraitTablet = isPortraitTablet,
                onStartSession = viewModel::requestStartSession,
                onStopSession = viewModel::stopSession,
                onExportCsv = { viewModel.exportCsv() },
                onPreviousSessions = {
                    viewModel.loadPreviousSessions()
                    showPreviousSessions = true
                }
            )

            CumulativeBehaviourGraphCard(
                graph = uiState.graph,
                isPortraitTablet = isPortraitTablet,
                onClearHistory = { confirmGraphReset = true }
            )

            if (uiState.isSessionActive && uiState.animals.isNotEmpty()) {
                AnimalPanelsSection(
                    animals = uiState.animals,
                    sessionActive = uiState.isSessionActive,
                    animalMinHeight = animalMinHeight,
                    onBehaviourPressed = { slotIndex, behaviour ->
                        viewModel.onBehaviourPressed(slotIndex, behaviour)
                    },
                    onDeleteLast30Seconds = { slotIndex ->
                        viewModel.deleteLast30Seconds(slotIndex)
                    }
                )
            }

            Text(
                text = "Timestamps are exported as UTC ISO 8601 with millisecond precision. Behaviour totals stay on-device and the CSV export remains raw event data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun rememberLiveClockText(): State<String> {
    return produceState(initialValue = DateTimeFormats.formatClock(System.currentTimeMillis())) {
        while (true) {
            val now = System.currentTimeMillis()
            value = DateTimeFormats.formatClock(now)
            val delayMs = (1000L - (now % 1000L)).coerceAtLeast(100L)
            delay(delayMs)
        }
    }
}

@Composable
private fun ClockCard(
    liveClockText: String,
    sessionActive: Boolean,
    activeSessionId: Long?,
    activeSessionStartedAtEpochMs: Long?,
    isPortraitTablet: Boolean
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Column(
            modifier = Modifier.padding(if (isPortraitTablet) 24.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Device time",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = liveClockText,
                style = if (isPortraitTablet) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.headlineSmall
                },
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when {
                    sessionActive && activeSessionId != null && activeSessionStartedAtEpochMs != null ->
                        "Session #$activeSessionId started at ${DateTimeFormats.formatLocalTime(activeSessionStartedAtEpochMs)}"

                    else -> "No active session running."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
            )
        }
    }
}

@Composable
private fun TimeWarningDialog(
    onConfirm: () -> Unit,
    onGoBack: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onGoBack,
        title = {
            Text("Have you checked time.is?")
        },
        text = {
            Text(
                "This app works offline, so it relies on the tablet's own clock. " +
                    "Before you start a session, check time.is and make sure you include the time offset in the session card. " +
                    "Enter 0 if time.is says exact. " +
                    "Event timestamps are stored with millisecond precision to help align them with accelerometer data."
            )
        },
        dismissButton = {
            TextButton(onClick = onGoBack) {
                Text("Go Back")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Confirm")
            }
        }
    )
}

@Composable
private fun ExitSessionDialog(
    onStay: () -> Unit,
    onLeave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onStay,
        title = {
            Text("Session still running")
        },
        text = {
            Text(
                "This session is still active. If you leave the app now, recording will keep running and you can restore it when you reopen the app."
            )
        },
        dismissButton = {
            TextButton(onClick = onStay) {
                Text("Stay in app")
            }
        },
        confirmButton = {
            TextButton(onClick = onLeave) {
                Text("Leave app")
            }
        }
    )
}

@Composable
private fun AnimalPanelsSection(
    animals: List<AnimalPanelUiState>,
    sessionActive: Boolean,
    animalMinHeight: Dp,
    onBehaviourPressed: (Int, Behavior) -> Unit,
    onDeleteLast30Seconds: (Int) -> Unit
) {
    val sectionSpacing = when (animals.size) {
        1 -> 16.dp
        2 -> 12.dp
        else -> 8.dp
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(sectionSpacing)
    ) {
        animals.forEach { animal ->
            AnimalPanelCard(
                animal = animal,
                totalAnimals = animals.size,
                sessionActive = sessionActive,
                onBehaviourPressed = { behaviour ->
                    onBehaviourPressed(animal.slotIndex, behaviour)
                },
                onDeleteLast30Seconds = {
                    onDeleteLast30Seconds(animal.slotIndex)
                },
                modifier = Modifier.heightIn(min = animalMinHeight)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionDetailsCard(
    uiState: FocalSamplingUiState,
    isPortraitTablet: Boolean,
    onObserverNameChanged: (String) -> Unit,
    onTimeOffsetValueChanged: (String) -> Unit,
    onTimeOffsetDirectionChanged: (TimeOffsetDirection) -> Unit
) {
    val contentPadding = if (isPortraitTablet) 24.dp else 16.dp
    val observerNameIsInvalid = !uiState.isSessionActive &&
        uiState.sessionMetadata.observerName.isNotBlank() &&
        !uiState.sessionMetadata.isObserverNameValid
    val timeOffsetIsInvalid = !uiState.isSessionActive &&
        uiState.sessionMetadata.timeOffsetInput.isNotBlank() &&
        !uiState.sessionMetadata.isTimeOffsetValid

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Session Details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.sessionMetadata.observerName,
                onValueChange = onObserverNameChanged,
                readOnly = uiState.isSessionActive,
                isError = observerNameIsInvalid,
                label = {
                    Text("Observer name")
                },
                supportingText = {
                    Text(
                        if (observerNameIsInvalid) {
                            "Enter the observer name before starting."
                        } else {
                            "Required before starting the session."
                        }
                    )
                }
            )

            Text(
                text = "Time.is offset",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TimeOffsetDirection.entries.forEach { direction ->
                    FilterChip(
                        selected = uiState.sessionMetadata.timeOffsetDirection == direction,
                        onClick = { onTimeOffsetDirectionChanged(direction) },
                        enabled = !uiState.isSessionActive,
                        label = {
                            Text(
                                text = when (direction) {
                                    TimeOffsetDirection.AHEAD -> "Ahead"
                                    TimeOffsetDirection.BEHIND -> "Behind"
                                }
                            )
                        }
                    )
                }
            }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.sessionMetadata.timeOffsetInput,
                onValueChange = onTimeOffsetValueChanged,
                readOnly = uiState.isSessionActive,
                isError = timeOffsetIsInvalid,
                label = {
                    Text("Offset seconds")
                },
                supportingText = {
                    Text(
                        if (timeOffsetIsInvalid) {
                            "Use 0 or a non-negative value with up to one decimal place."
                        } else {
                            "Enter 0 if time.is says exact. Otherwise enter seconds, for example 0.3."
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            if (uiState.isSessionActive) {
                Text(
                    text = "Session details are locked while recording.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionControlsCard(
    uiState: FocalSamplingUiState,
    isPortraitTablet: Boolean,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onExportCsv: () -> Unit,
    onPreviousSessions: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(if (isPortraitTablet) 24.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Session Controls",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartSession,
                    enabled = uiState.canStartSession
                ) {
                    Text("Start Session")
                }

                if (uiState.isSessionActive) {
                    Button(
                        onClick = onStopSession,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) { Text("Stop Session") }
                }

                OutlinedButton(onClick = onExportCsv, enabled = uiState.canExport) {
                    Text("Export CSV")
                }

                OutlinedButton(onClick = onPreviousSessions) {
                    Text("Previous sessions")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimalDetailsCard(
    animals: List<AnimalPanelUiState>,
    knownIds: List<String>,
    onCountChanged: (Int) -> Unit,
    onIdChanged: (Int, String) -> Unit,
    onColorChanged: (Int, AnimalColor) -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Animal Details", style = MaterialTheme.typography.titleLarge)
            ChoiceMenu(
                label = "Number of animals",
                selected = animals.size,
                choices = SupportedAnimalCounts.toList(),
                text = Int::toString,
                onSelect = onCountChanged
            )
            animals.forEach { animal ->
                var focused by remember { mutableStateOf(false) }
                val id = animal.animalId.trim()
                val others = animals.filter { it.slotIndex != animal.slotIndex }
                    .map { it.animalId.trim() }
                val duplicate = id.isNotEmpty() && others.any { it.equals(id, ignoreCase = true) }
                val suggestions = if (focused) knownIds.asSequence()
                    .filter { it.startsWith(id, ignoreCase = true) && it != id }
                    .filterNot { candidate -> others.any { it.equals(candidate, ignoreCase = true) } }
                    .take(5).toList() else emptyList()
                OutlinedTextField(
                    value = animal.animalId,
                    onValueChange = { onIdChanged(animal.slotIndex, it) },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                    label = { Text("Animal ${animal.slotIndex + 1} ID") },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = if (duplicate) ({ Text("Use a different ID for each animal.") }) else null
                )
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { suggestion ->
                            OutlinedButton(onClick = { onIdChanged(animal.slotIndex, suggestion) }) {
                                Text(suggestion)
                            }
                        }
                    }
                }
                ChoiceMenu(
                    label = "Card colour",
                    selected = animal.animalColor,
                    choices = AnimalColor.entries,
                    text = AnimalColor::label,
                    onSelect = { onColorChanged(animal.slotIndex, it) }
                )
            }
        }
    }
}

@Composable
private fun <T> ChoiceMenu(
    label: String,
    selected: T,
    choices: List<T>,
    text: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: ${text(selected)} \u25BE") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(text(choice)) },
                    onClick = {
                        expanded = false
                        onSelect(choice)
                    }
                )
            }
        }
    }
}

@Composable
private fun PreviousSessionsPage(
    sessions: List<SamplingSessionEntity>?,
    onBack: () -> Unit,
    onExport: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("\u2190 Back") }
        Text("Previous sessions", style = MaterialTheme.typography.headlineSmall)
        when {
            sessions == null -> Text("Loading sessions...")
            sessions.isEmpty() -> Text("No completed sessions yet.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sessions, key = { it.id }) { session ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Session #${session.id}", style = MaterialTheme.typography.titleMedium)
                            Text(DateTimeFormats.formatClock(session.startedAtEpochMs))
                            if (session.observerName.isNotBlank()) Text(session.observerName)
                            val ids = SessionAnimalIdsCodec.decode(session.animalIdsJson)
                            if (ids.isNotEmpty()) Text(ids.joinToString())
                            OutlinedButton(onClick = { onExport(session.id) }) { Text("Export CSV") }
                        }
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
