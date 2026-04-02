package au.edu.cqu.focalapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.edu.cqu.focalapp.domain.model.AnimalColor
import au.edu.cqu.focalapp.domain.model.Behavior
import au.edu.cqu.focalapp.ui.components.AnimalPanelCard
import au.edu.cqu.focalapp.util.CsvExportPayload
import au.edu.cqu.focalapp.util.CsvExporter
import au.edu.cqu.focalapp.util.DateTimeFormats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class StartSessionAnimalDraft(
    val animalId: String,
    val animalColor: AnimalColor
)

@Composable
fun FocalSamplingScreen(
    viewModel: FocalSamplingViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val liveClockText by rememberLiveClockText()
    val animalSectionHeight = remember(configuration.screenHeightDp) {
        (configuration.screenHeightDp.dp - 40.dp).coerceAtLeast(320.dp)
    }
    var draftAnimals by remember(
        uiState.showStartSessionDialog,
        uiState.configuredAnimalCount,
        uiState.visibleAnimals
    ) {
        mutableStateOf(buildStartSessionDrafts(uiState))
    }

    var pendingExport by remember { mutableStateOf<CsvExportPayload?>(null) }

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

    when {
        uiState.showTimeWarning -> {
            TimeWarningDialog(
                onConfirm = viewModel::confirmTimeWarning,
                onGoBack = viewModel::dismissTimeWarning
            )
        }

        uiState.showAnimalCountDialog -> {
            AnimalCountDialog(
                onSelectCount = viewModel::setAnimalCount,
                onDismiss = viewModel::dismissAnimalCountDialog
            )
        }

        uiState.showStartSessionDialog -> {
            StartSessionDialog(
                animals = draftAnimals,
                onAnimalIdChanged = { index, value ->
                    val updated = draftAnimals.toMutableList()
                    if (index in updated.indices) {
                        updated[index] = updated[index].copy(animalId = value)
                        draftAnimals = updated
                    }
                },
                onAnimalColorChanged = { index, color ->
                    val updated = draftAnimals.toMutableList()
                    if (index in updated.indices) {
                        updated[index] = updated[index].copy(animalColor = color)
                        draftAnimals = updated
                    }
                },
                onDismiss = viewModel::dismissStartSessionDialog,
                onStart = {
                    viewModel.startSession(
                        animalIds = draftAnimals.map(StartSessionAnimalDraft::animalId),
                        animalColors = draftAnimals.map(StartSessionAnimalDraft::animalColor)
                    )
                }
            )
        }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ClockCard(
                liveClockText = liveClockText,
                sessionActive = uiState.isSessionActive,
                activeSessionId = uiState.activeSessionId,
                activeSessionStartedAtEpochMs = uiState.activeSessionStartedAtEpochMs
            )

            SessionControlsCard(
                uiState = uiState,
                onStartSession = viewModel::requestStartSession,
                onStopSession = viewModel::stopSession,
                onExportCsv = viewModel::exportCsv,
                onConfigureAnimals = viewModel::openAnimalCountDialog
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
                text = "Timestamps are exported as UTC ISO 8601 with millisecond precision.",
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
    activeSessionStartedAtEpochMs: Long?
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                style = MaterialTheme.typography.headlineSmall,
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
            Text("Check device time before sampling")
        },
        text = {
            Text(
                "This app works offline, so it relies on the phone's own clock. " +
                    "Before you start a session, compare the device time against time.is. " +
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
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onExportCsv: () -> Unit,
    onConfigureAnimals: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Session controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = when {
                    uiState.isSessionActive && uiState.activeSessionId != null ->
                        "Recording locally in Room. Only one behaviour can be active per animal."

                    uiState.configuredAnimalCount != null ->
                        "${uiState.configuredAnimalCount} animal(s) configured for the next session."

                    uiState.exportSessionId != null ->
                        "The most recent session is ready to export as CSV."

                    else ->
                        "Set the number of animals first, then start a session to begin recording behaviour events."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartSession,
                    enabled = !uiState.isSessionActive && uiState.configuredAnimalCount != null
                ) {
                    Text("Start Session")
                }

                OutlinedButton(
                    onClick = onConfigureAnimals,
                    enabled = !uiState.isSessionActive
                ) {
                    Text("Set Animals")
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimalCountDialog(
    onSelectCount: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("How many animals will you monitor?")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose how many focal animals you want on screen for this session.")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    (1..3).forEach { count ->
                        Button(onClick = { onSelectCount(count) }) {
                            Text(count.toString())
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {}
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartSessionDialog(
    animals: List<StartSessionAnimalDraft>,
    onAnimalIdChanged: (Int, String) -> Unit,
    onAnimalColorChanged: (Int, AnimalColor) -> Unit,
    onDismiss: () -> Unit,
    onStart: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Set animal IDs and colours")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Enter the IDs and choose a background colour for the animals you are about to monitor.")
                animals.forEachIndexed { index, animal ->
                    val palette = animal.animalColor.palette()
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = palette.previewColor.copy(alpha = 0.92f),
                            contentColor = palette.contentColor
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Animal ${index + 1}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.contentColor
                            )
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = animal.animalId,
                                onValueChange = { value ->
                                    onAnimalIdChanged(index, value)
                                },
                                singleLine = true,
                                label = {
                                    Text("Animal ${index + 1} ID")
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = palette.contentColor,
                                    unfocusedTextColor = palette.contentColor,
                                    focusedLabelColor = palette.supportingColor,
                                    unfocusedLabelColor = palette.supportingColor,
                                    cursorColor = palette.borderColor,
                                    focusedBorderColor = palette.borderColor,
                                    unfocusedBorderColor = palette.borderColor.copy(alpha = 0.6f),
                                    focusedContainerColor = palette.fieldColor,
                                    unfocusedContainerColor = palette.fieldColor
                                )
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AnimalColor.entries.forEach { color ->
                                    val colorPalette = color.palette()
                                    FilterChip(
                                        selected = animal.animalColor == color,
                                        onClick = { onAnimalColorChanged(index, color) },
                                        label = {
                                            Text(color.label)
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = colorPalette.fieldColor,
                                            labelColor = colorPalette.contentColor,
                                            selectedContainerColor = colorPalette.selectionColor,
                                            selectedLabelColor = colorPalette.contentColor
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(onClick = onStart) {
                Text("Start Session")
            }
        }
    )
}

private fun buildStartSessionDrafts(uiState: FocalSamplingUiState): List<StartSessionAnimalDraft> {
    val animalCount = uiState.configuredAnimalCount ?: uiState.visibleAnimals.size
    return List(animalCount) { index ->
        val animal = uiState.animals.getOrNull(index)
        StartSessionAnimalDraft(
            animalId = animal?.animalId ?: "Animal ${index + 1}",
            animalColor = animal?.animalColor ?: AnimalColor.defaultForSlot(index)
        )
    }
}
