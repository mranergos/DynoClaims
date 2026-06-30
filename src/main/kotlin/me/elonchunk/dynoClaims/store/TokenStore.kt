package me.elonchunk.dynoClaims.store

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TokenStore {

    data class Entry(val playerId: UUID, val expiresAtMillis: Long) {
        fun isExpired(): Boolean = System.currentTimeMillis() > expiresAtMillis
    }

    private val tokens = ConcurrentHashMap<String, Entry>()

    fun createToken(playerId: UUID, ttlSeconds: Long): String {
        val token = UUID.randomUUID().toString().replace("-", "")
        tokens[token] = Entry(playerId, System.currentTimeMillis() + ttlSeconds * 1000L)
        return token
    }

    fun resolve(token: String?): Entry? {
        if (token == null) return null
        val entry = tokens[token] ?: return null
        if (entry.isExpired()) {
            tokens.remove(token)
            return null
        }
        return entry
    }

    fun invalidate(token: String) {
        tokens.remove(token)
    }

    fun sweepExpired() {
        tokens.entries.removeIf { it.value.isExpired() }
    }
}