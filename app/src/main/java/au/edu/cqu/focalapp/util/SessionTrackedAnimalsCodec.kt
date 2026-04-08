package au.edu.cqu.focalapp.util

import au.edu.cqu.focalapp.domain.model.TrackedAnimal
import org.json.JSONArray

object SessionTrackedAnimalsCodec {
    fun encode(trackedAnimals: List<TrackedAnimal>): String {
        return JSONArray(trackedAnimals.map(TrackedAnimal::stableKey)).toString()
    }

    fun decode(raw: String): List<TrackedAnimal> {
        if (raw.isBlank()) {
            return emptyList()
        }

        val jsonArray = JSONArray(raw)
        return buildList {
            repeat(jsonArray.length()) { index ->
                TrackedAnimal.fromStableKey(jsonArray.optString(index))?.let(::add)
            }
        }
    }
}
