package au.edu.cqu.focalapp.util

import org.json.JSONArray

object SessionAnimalIdsCodec {
    fun encode(animalIds: List<String>): String {
        return JSONArray(animalIds).toString()
    }

    fun decode(raw: String): List<String> {
        val jsonArray = JSONArray(raw)
        return List(jsonArray.length()) { index ->
            jsonArray.optString(index)
        }
    }
}
