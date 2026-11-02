package me.elonchunk.dynoClaims.web

import com.google.gson.JsonParser

data class ClaimRequest(val token: String?, val world: String?, val chunks: List<IntArray>) {
    companion object {
        /** Returns null if the body isn't valid JSON of the expected shape. */
        fun fromJson(body: String): ClaimRequest? {
            if (body.isBlank()) return null
            return try {
                val obj = JsonParser.parseString(body).asJsonObject
                val token = obj.get("token")?.takeIf { it.isJsonPrimitive }?.asString
                val world = obj.get("world")?.takeIf { it.isJsonPrimitive }?.asString

                val chunks = ArrayList<IntArray>()
                obj.getAsJsonArray("chunks")?.forEach { element ->
                    val pair = element.asJsonArray
                    if (pair.size() == 2) chunks.add(intArrayOf(pair.get(0).asInt, pair.get(1).asInt))
                }
                ClaimRequest(token, world, chunks)
            } catch (_: Exception) {
                null
            }
        }
    }
}
