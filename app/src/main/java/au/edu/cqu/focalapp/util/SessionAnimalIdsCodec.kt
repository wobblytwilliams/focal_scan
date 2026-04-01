package au.edu.cqu.focalapp.util

import org.json.JSONArray

object SessionAnimalIdsCodec {
    fun encode(animalIds: List<String>): String {
        return JSONArray(animalIds).toString()
    }

    fun decode(raw: String): List<String> {
        if (raw.isBlank()) {
            return emptyList()
        }

        val jsonArray = JSONArray(raw)
        return List(jsonArray.length()) { index ->
            jsonArray.optString(index)
        }
    }
}
