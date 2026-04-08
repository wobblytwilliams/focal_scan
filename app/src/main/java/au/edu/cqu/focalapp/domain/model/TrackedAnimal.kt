package au.edu.cqu.focalapp.domain.model

enum class TrackedAnimal(
    val displayName: String,
    val animalColor: AnimalColor
) {
    BLUE(
        displayName = "Blue",
        animalColor = AnimalColor.BLUE
    ),
    GREEN(
        displayName = "Green",
        animalColor = AnimalColor.GREEN
    ),
    YELLOW(
        displayName = "Yellow",
        animalColor = AnimalColor.YELLOW
    );

    val stableKey: String
        get() = name

    companion object {
        fun defaults(): List<TrackedAnimal> = entries

        fun fromStableKey(raw: String): TrackedAnimal? {
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
        }

        fun fromDisplayName(raw: String): TrackedAnimal? {
            return entries.firstOrNull { it.displayName.equals(raw.trim(), ignoreCase = true) }
        }

        fun fromStoredAnimalId(raw: String): TrackedAnimal? {
            return fromStableKey(raw) ?: fromDisplayName(raw)
        }

        fun defaultSelection(): List<TrackedAnimal> = defaults()
    }
}
