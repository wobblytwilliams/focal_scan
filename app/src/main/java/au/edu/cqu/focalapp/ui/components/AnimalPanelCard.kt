package au.edu.cqu.focalapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import au.edu.cqu.focalapp.domain.model.Behavior
import au.edu.cqu.focalapp.ui.AnimalPanelUiState
import au.edu.cqu.focalapp.ui.palette
import au.edu.cqu.focalapp.util.DateTimeFormats

@Composable
fun AnimalPanelCard(
    animal: AnimalPanelUiState,
    totalAnimals: Int,
    sessionActive: Boolean,
    onBehaviourPressed: (Behavior) -> Unit,
    onDeleteLast30Seconds: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = animal.activeBehaviour != null
    val palette = animal.animalColor.palette()
    val borderColor = if (isActive) palette.borderColor else palette.borderColor.copy(alpha = 0.55f)
    val titleColor = palette.contentColor
    val supportingTextColor = palette.supportingColor
    val verticalSpacing = when (totalAnimals) {
        1 -> 16.dp
        2 -> 12.dp
        else -> 8.dp
    }
    val contentPadding = when (totalAnimals) {
        1 -> 20.dp
        2 -> 16.dp
        else -> 12.dp
    }
    val titleStyle = when (totalAnimals) {
        3 -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.titleMedium
    }
    val behaviourLabelStyle = when (totalAnimals) {
        1 -> MaterialTheme.typography.bodyMedium
        2 -> MaterialTheme.typography.labelLarge
        else -> MaterialTheme.typography.labelMedium
    }
    val idStyle = when (totalAnimals) {
        1 -> MaterialTheme.typography.bodyLarge
        else -> MaterialTheme.typography.bodyMedium
    }
    val statusStyle = when (totalAnimals) {
        3 -> MaterialTheme.typography.labelSmall
        else -> MaterialTheme.typography.labelMedium
    }

    val containerColor = if (isActive) {
        palette.activeContainerColor
    } else {
        palette.containerColor
    }
    val instructionText = if (totalAnimals == 3) {
        "Tap a behaviour for ${animal.trackedAnimal.displayName.lowercase()}."
    } else {
        "Tap a behaviour to start or switch logging for ${animal.trackedAnimal.displayName.lowercase()}."
    }
    val behaviourRows = behaviourRowsFor(totalAnimals)
    val maxButtonsPerRow = behaviourRows.maxOf { it.size }

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = containerColor,
            contentColor = titleColor
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = animal.trackedAnimal.displayName,
                    style = titleStyle,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor
                )
                Text(
                    text = when {
                        animal.activeBehaviour != null -> "Active"
                        sessionActive -> "Ready"
                        else -> "Session inactive"
                    },
                    style = statusStyle,
                    color = supportingTextColor
                )
            }

            Text(
                text = instructionText,
                style = idStyle,
                fontWeight = FontWeight.Medium,
                color = supportingTextColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(if (totalAnimals == 3) 6.dp else 8.dp)
            ) {
                behaviourRows.forEach { rowBehaviours ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (totalAnimals == 3) 6.dp else 8.dp)
                    ) {
                        rowBehaviours.forEach { behaviour ->
                            FilterChip(
                                modifier = Modifier.weight(1f),
                                selected = animal.activeBehaviour == behaviour,
                                onClick = { onBehaviourPressed(behaviour) },
                                enabled = sessionActive,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = palette.fieldColor,
                                    labelColor = titleColor,
                                    selectedContainerColor = palette.selectionColor,
                                    selectedLabelColor = titleColor
                                ),
                                label = {
                                    Text(
                                        text = behaviour.label,
                                        style = behaviourLabelStyle,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }

                        repeat(maxButtonsPerRow - rowBehaviours.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Text(
                text = when {
                    animal.activeBehaviour != null && animal.activeStartedAtEpochMs != null ->
                        "Active: ${animal.activeBehaviour.label} since ${DateTimeFormats.formatLocalTime(animal.activeStartedAtEpochMs)}"

                    sessionActive ->
                        "No active behaviour."

                    else ->
                        "Start a session to begin recording."
                },
                style = if (totalAnimals == 1) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodySmall
                },
                color = supportingTextColor
            )

            Button(
                onClick = onDeleteLast30Seconds,
                enabled = sessionActive,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(if (totalAnimals == 3) "Delete 30s" else "Delete Last 30s")
            }
        }
    }
}

private val orderedBehaviours = listOf(
    Behavior.GRAZING,
    Behavior.WALKING,
    Behavior.IDLE,
    Behavior.IDLE_NON_RUMINATING,
    Behavior.IDLE_RUMINATING
)

private fun behaviourRowsFor(totalAnimals: Int): List<List<Behavior>> {
    return if (totalAnimals == 1) {
        listOf(
            listOf(Behavior.GRAZING, Behavior.WALKING, Behavior.IDLE),
            listOf(Behavior.IDLE_NON_RUMINATING, Behavior.IDLE_RUMINATING)
        )
    } else {
        orderedBehaviours.chunked(2)
    }
}
