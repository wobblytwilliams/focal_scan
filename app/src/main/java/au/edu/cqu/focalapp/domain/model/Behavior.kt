package au.edu.cqu.focalapp.domain.model

enum class Behavior(val label: String) {
    GRAZING("Grazing"),
    WALKING("Walking"),
    IDLE("Idle"),
    IDLE_NON_RUMINATING("Idle non-ruminating"),
    IDLE_RUMINATING("Idle ruminating")
}
