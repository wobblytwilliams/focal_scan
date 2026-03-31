package au.edu.cqu.focalapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import au.edu.cqu.focalapp.domain.model.Behavior
import au.edu.cqu.focalapp.ui.AnimalPanelUiState
import au.edu.cqu.focalapp.util.DateTimeFormats

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimalPanelCard(
    animal: AnimalPanelUiState,
    sessionActive: Boolean,
    onBehaviourPressed: (Behavior) -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = animal.activeBehaviour != null
    val borderColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Animal ${animal.slotIndex + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when {
                        animal.activeBehaviour != null -> "Active"
                        sessionActive -> "Ready"
                        else -> "Session inactive"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "Animal ID: ${animal.animalId}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Behavior.entries.forEach { behaviour ->
                    FilterChip(
                        selected = animal.activeBehaviour == behaviour,
                        onClick = { onBehaviourPressed(behaviour) },
                        enabled = sessionActive,
                        label = {
                            Text(behaviour.label)
                        }
                    )
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
