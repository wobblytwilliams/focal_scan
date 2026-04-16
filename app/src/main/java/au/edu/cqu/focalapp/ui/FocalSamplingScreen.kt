package au.edu.cqu.focalapp.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.edu.cqu.focalapp.domain.model.Behavior
import au.edu.cqu.focalapp.domain.model.TrackedAnimal
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
    val screenPadding = if (isPortraitTablet) 24.dp else 16.dp
    val verticalSpacing = if (isPortraitTablet) 20.dp else 16.dp
    val animalSectionHeight = remember(
        configuration.screenHeightDp,
        isPortraitTablet,
        uiState.visibleAnimals.size
    ) {
        val visibleAnimalCount = uiState.visibleAnimals.size.coerceAtLeast(1)
        val perAnimalMinHeight = when {
            isPortraitTablet && visibleAnimalCount == 1 -> 360.dp
            isPortraitTablet && visibleAnimalCount == 2 -> 290.dp
            isPortraitTablet -> 250.dp
            visibleAnimalCount == 1 -> 300.dp
            visibleAnimalCount == 2 -> 240.dp
            else -> 220.dp
        }
        val interCardSpacing = when (visibleAnimalCount) {
            1 -> 0.dp
            2 -> 12.dp
            else -> 8.dp
        }
        val baseMinHeight = if (isPortraitTablet) 540.dp else 320.dp
        val desiredHeight = (perAnimalMinHeight * visibleAnimalCount) +
            (interCardSpacing * (visibleAnimalCount - 1))
        val heightFraction = if (isPortraitTablet) 0.52f else 0.46f
        val viewportTarget = configuration.screenHeightDp.dp * heightFraction
        viewportTarget.coerceAtLeast(baseMinHeight).coerceAtLeast(desiredHeight)
    }

    var pendingExport by remember { mutableStateOf<CsvExportPayload?>(null) }

    BackHandler(enabled = uiState.isSessionActive) {
        showExitConfirmation = true
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
                is UiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
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

            SessionControlsCard(
                uiState = uiState,
                isPortraitTablet = isPortraitTablet,
                onToggleAnimal = viewModel::toggleAnimalSelection,
                onObserverNameChanged = viewModel::onObserverNameChanged,
                onTimeOffsetValueChanged = viewModel::onTimeOffsetValueChanged,
                onTimeOffsetDirectionChanged = viewModel::onTimeOffsetDirectionChanged,
                onStartSession = viewModel::requestStartSession,
                onStopSession = viewModel::stopSession,
                onExportCsv = viewModel::exportCsv
            )

            CumulativeBehaviourGraphCard(
                graph = uiState.graph,
                isPortraitTablet = isPortraitTablet
            )

            if (uiState.visibleAnimals.isNotEmpty()) {
                AnimalPanelsSection(
                    animals = uiState.visibleAnimals,
                    sessionActive = uiState.isSessionActive,
                    animalSectionHeight = animalSectionHeight,
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
    animalSectionHeight: Dp,
    onBehaviourPressed: (Int, Behavior) -> Unit,
    onDeleteLast30Seconds: (Int) -> Unit
) {
    val sectionSpacing = when (animals.size) {
        1 -> 16.dp
        2 -> 12.dp
        else -> 8.dp
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(animalSectionHeight),
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
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionControlsCard(
    uiState: FocalSamplingUiState,
    isPortraitTablet: Boolean,
    onToggleAnimal: (TrackedAnimal) -> Unit,
    onObserverNameChanged: (String) -> Unit,
    onTimeOffsetValueChanged: (String) -> Unit,
    onTimeOffsetDirectionChanged: (TimeOffsetDirection) -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onExportCsv: () -> Unit
) {
    val contentPadding = if (isPortraitTablet) 24.dp else 16.dp
    val selectedCount = uiState.visibleAnimals.size
    val selectedSummary = selectedCount.toString()
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
                text = "Session controls",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = when {
                    uiState.isSessionActive && uiState.activeSessionId != null ->
                        "Recording locally on the tablet. Only one behaviour can be active per animal."

                    selectedCount > 0 ->
                        "$selectedCount animal${if (selectedCount == 1) "" else "s"} selected for the next session."

                    uiState.exportSessionId != null ->
                        "The most recent session is ready to export as CSV. Select the animals you want for the next session."

                    else ->
                        "All animals start off deselected. Turn on Blue, Green, and/or Yellow, then enter the observer name and time.is offset before starting."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Select Animals",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TrackedAnimal.entries.forEach { trackedAnimal ->
                    AnimalSelectionChip(
                        trackedAnimal = trackedAnimal,
                        selected = uiState.visibleAnimals.any { it.trackedAnimal == trackedAnimal },
                        enabled = !uiState.isSessionActive,
                        onClick = { onToggleAnimal(trackedAnimal) }
                    )
                }
            }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = selectedSummary,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = {
                    Text("Animals selected")
                },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                )
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
                    text = "Animal selection, observer name, and time offset are locked while a session is active.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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

                OutlinedButton(
                    onClick = onStopSession,
                    enabled = uiState.isSessionActive
                ) {
                    Text("Stop Session")
                }

                OutlinedButton(
                    onClick = onExportCsv,
                    enabled = uiState.canExport
                ) {
                    Text("Export CSV")
                }
            }
        }
    }
}

@Composable
private fun AnimalSelectionChip(
    trackedAnimal: TrackedAnimal,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val palette = trackedAnimal.animalColor.palette()

    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = trackedAnimal.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) palette.contentColor else MaterialTheme.colorScheme.onSurface
            )
        },
        border = BorderStroke(
            1.5.dp,
            if (selected) palette.borderColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            labelColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            selectedContainerColor = palette.selectionColor,
            selectedLabelColor = palette.contentColor
        )
    )
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
