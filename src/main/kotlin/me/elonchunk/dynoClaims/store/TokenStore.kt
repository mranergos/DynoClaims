package me.elonchunk.dynoClaims.store

import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Short-lived web session tokens.
 *  - one active token per player (running /webclaim again replaces the old link)
 *  - sliding expiry: every successful API call extends the session, so a link
 *    doesn't die while someone is busy drawing a big selection
 */
class TokenStore {

    class Entry(val playerId: UUID, private val ttlMillis: Long) {
        @Volatile
        private var expiresAtMillis: Long = System.currentTimeMillis() + ttlMillis

        fun isExpired(): Boolean = System.currentTimeMillis() > expiresAtMillis
        fun touch() { expiresAtMillis = System.currentTimeMillis() + ttlMillis }
    }

    private val random = SecureRandom()
    private val tokens = ConcurrentHashMap<String, Entry>()
    private val tokenByPlayer = ConcurrentHashMap<UUID, String>()

    fun createToken(playerId: UUID, ttlSeconds: Long): String {
        tokenByPlayer.remove(playerId)?.let { tokens.remove(it) }

        val bytes = ByteArray(24).also { random.nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        tokens[token] = Entry(playerId, ttlSeconds * 1000L)
        tokenByPlayer[playerId] = token
        return token
    }

    fun resolve(token: String?): Entry? {
        if (token.isNullOrEmpty()) return null
        val entry = tokens[token] ?: return null
        if (entry.isExpired()) {
            invalidate(token)
            return null
        }
        entry.touch()
        return entry
    }

    fun invalidate(token: String) {
        val entry = tokens.remove(token) ?: return
        tokenByPlayer.remove(entry.playerId, token)
    }

    fun sweepExpired() {
        tokens.entries.removeIf { (token, entry) ->
            val expired = entry.isExpired()
            if (expired) tokenByPlayer.remove(entry.playerId, token)
            expired
        }
    }
}
