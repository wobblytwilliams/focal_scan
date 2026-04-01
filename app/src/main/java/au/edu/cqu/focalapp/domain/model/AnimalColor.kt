package au.edu.cqu.focalapp.domain.model

enum class AnimalColor(val label: String) {
    BLUE("Blue"),
    GREEN("Green"),
    YELLOW("Yellow");

    companion object {
        fun defaultForSlot(slotIndex: Int): AnimalColor {
            return entries[slotIndex % entries.size]
        }

        fun defaultsForCount(count: Int): List<AnimalColor> {
            return List(count) { index -> defaultForSlot(index) }
        }
    }
}
