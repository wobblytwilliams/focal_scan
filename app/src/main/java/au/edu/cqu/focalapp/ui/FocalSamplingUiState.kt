package au.edu.cqu.focalapp.ui

import au.edu.cqu.focalapp.domain.model.AnimalColor
import au.edu.cqu.focalapp.domain.model.Behavior
import au.edu.cqu.focalapp.domain.model.GraphBehaviourCategory
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

internal val SupportedAnimalCounts = 1..5

data class AnimalPanelUiState(
    val slotIndex: Int,
    val animalId: String = "",
    val animalColor: AnimalColor = AnimalColor.defaultForSlot(slotIndex),
    val activeBehaviour: Behavior? = null,
    val activeEventId: Long? = null,
    val activeStartedAtEpochMs: Long? = null
)

enum class TimeOffsetDirection {
    AHEAD,
    BEHIND
}

data class SessionMetadataUiState(
    val observerName: String = "",
    val timeOffsetInput: String = "",
    val timeOffsetDirection: TimeOffsetDirection = TimeOffsetDirection.AHEAD
) {
    val trimmedObserverName: String
        get() = observerName.trim()

    val isObserverNameValid: Boolean
        get() = trimmedObserverName.isNotEmpty()

    val isTimeOffsetValid: Boolean
        get() = parseUnsignedTimeOffsetSeconds(timeOffsetInput) != null

    val signedTimeOffsetSeconds: Double?
        get() = parseUnsignedTimeOffsetSeconds(timeOffsetInput)?.let { unsignedValue ->
            if (unsignedValue == 0.0) {
                0.0
            } else if (timeOffsetDirection == TimeOffsetDirection.AHEAD) {
                unsignedValue
            } else {
                -unsignedValue
            }
        }

    companion object {
        fun fromStoredValues(
            observerName: String,
            signedTimeOffsetSeconds: Double
        ): SessionMetadataUiState {
            return SessionMetadataUiState(
                observerName = observerName,
                timeOffsetInput = formatTimeOffsetInput(signedTimeOffsetSeconds),
                timeOffsetDirection = if (signedTimeOffsetSeconds < 0.0) {
                    TimeOffsetDirection.BEHIND
                } else {
                    TimeOffsetDirection.AHEAD
                }
            )
        }
    }
}

data class AnimalGraphBarUiState(
    val category: GraphBehaviourCategory,
    val minutes: Float
)

data class AnimalGraphGroupUiState(
    val animalId: String,
    val bars: List<AnimalGraphBarUiState>
) {
    val maxMinutes: Float
        get() = bars.maxOfOrNull(AnimalGraphBarUiState::minutes) ?: 0f
}

data class BehaviourGraphUiState(
    val groups: List<AnimalGraphGroupUiState> = emptyList(),
    val yAxisMaxMinutes: Int = 60
) {
    val hasGroups: Boolean
        get() = groups.isNotEmpty()
}

data class FocalSamplingUiState(
    val isSessionActive: Boolean = false,
    val activeSessionId: Long? = null,
    val activeSessionStartedAtEpochMs: Long? = null,
    val exportSessionId: Long? = null,
    val showTimeWarning: Boolean = false,
    val sessionMetadata: SessionMetadataUiState = SessionMetadataUiState(),
    val animals: List<AnimalPanelUiState> = listOf(AnimalPanelUiState(0)),
    val knownAnimalIds: List<String> = emptyList(),
    val graph: BehaviourGraphUiState = BehaviourGraphUiState()
) {
    val canExport: Boolean
        get() = exportSessionId != null

    val hasValidAnimalIds: Boolean
        get() = animals.all { it.animalId.isNotBlank() } &&
            animals.map { it.animalId.trim().lowercase() }.distinct().size == animals.size

    val canStartSession: Boolean
        get() = !isSessionActive &&
            hasValidAnimalIds &&
            sessionMetadata.isObserverNameValid &&
            sessionMetadata.isTimeOffsetValid
}

private val TimeOffsetPattern = Regex("^\\d+(\\.\\d)?$")

internal fun sanitizeTimeOffsetInput(raw: String): String {
    if (raw.isBlank()) {
        return ""
    }

    val sanitized = StringBuilder()
    var hasDecimalPoint = false

    raw.trim().forEach { character ->
        when {
            character.isDigit() -> sanitized.append(character)

            character == '.' && !hasDecimalPoint -> {
                if (sanitized.isEmpty()) {
                    sanitized.append('0')
                }
                sanitized.append(character)
                hasDecimalPoint = true
            }
        }
    }

    return sanitized.toString()
}

internal fun parseUnsignedTimeOffsetSeconds(raw: String): Double? {
    val input = raw.trim()
    if (input.isEmpty() || !TimeOffsetPattern.matches(input)) {
        return null
    }
    return input.toDoubleOrNull()
}

internal fun formatTimeOffsetInput(signedSeconds: Double): String {
    val absoluteSeconds = abs(signedSeconds)
    if (absoluteSeconds == 0.0) {
        return "0"
    }

    return if (absoluteSeconds == absoluteSeconds.toLong().toDouble()) {
        absoluteSeconds.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", absoluteSeconds)
    }
}

internal fun calculateGraphAxisMaxMinutes(groups: List<AnimalGraphGroupUiState>): Int {
    val maxMinutes = groups.maxOfOrNull(AnimalGraphGroupUiState::maxMinutes) ?: 0f
    val roundedUp = if (maxMinutes <= 0f) {
        0
    } else {
        ceil(maxMinutes / 15f).toInt() * 15
    }
    return max(60, roundedUp)
}
