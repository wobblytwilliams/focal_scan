package au.edu.cqu.focalapp.domain.model

enum class GraphBehaviourCategory(val label: String) {
    WALKING("Walking"),
    GRAZING("Grazing"),
    IDLE("Idle")
}

data class AnimalBehaviourTotals(
    val trackedAnimal: TrackedAnimal,
    val walkingDurationMs: Long = 0L,
    val grazingDurationMs: Long = 0L,
    val idleDurationMs: Long = 0L
) {
    fun add(behaviour: Behavior, durationMs: Long): AnimalBehaviourTotals {
        if (durationMs <= 0L) {
            return this
        }

        return when (behaviour) {
            Behavior.WALKING -> copy(walkingDurationMs = walkingDurationMs + durationMs)
            Behavior.GRAZING -> copy(grazingDurationMs = grazingDurationMs + durationMs)
            Behavior.IDLE,
            Behavior.IDLE_NON_RUMINATING,
            Behavior.IDLE_RUMINATING -> copy(idleDurationMs = idleDurationMs + durationMs)
        }
    }

    fun durationFor(category: GraphBehaviourCategory): Long {
        return when (category) {
            GraphBehaviourCategory.WALKING -> walkingDurationMs
            GraphBehaviourCategory.GRAZING -> grazingDurationMs
            GraphBehaviourCategory.IDLE -> idleDurationMs
        }
    }
}
