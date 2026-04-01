package au.edu.cqu.focalapp.util

import au.edu.cqu.focalapp.domain.model.AnimalColor
import org.json.JSONArray

object SessionAnimalColorsCodec {
    fun encode(animalColors: List<AnimalColor>): String {
        return JSONArray(animalColors.map(AnimalColor::name)).toString()
    }

    fun decode(raw: String): List<AnimalColor> {
        if (raw.isBlank()) {
            return emptyList()
        }

        val jsonArray = JSONArray(raw)
        return buildList {
            repeat(jsonArray.length()) { index ->
                runCatching {
                    AnimalColor.valueOf(jsonArray.optString(index))
                }.getOrNull()?.let(::add)
            }
        }
    }
}
