package me.elonchunk.dynoClaims.web

data class ClaimRequest(val token: String?, val world: String?, val chunks: List<IntArray>) {
    companion object {
        fun fromJson(body: String): ClaimRequest {
            val token = extractString(body, "token")
            val world = extractString(body, "world")
            val chunks = extractChunks(body)
            return ClaimRequest(token, world, chunks)
        }

        private fun extractString(body: String, key: String): String? {
            val regex = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
            return regex.find(body)?.groupValues?.get(1)
        }

        private fun extractChunks(body: String): List<IntArray> {
            val regex = Regex("\\[\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*]")
            return regex.findAll(body).map {
                intArrayOf(it.groupValues[1].toInt(), it.groupValues[2].toInt())
            }.toList()
        }
    }
}