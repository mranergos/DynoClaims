package me.elonchunk.dynoClaims.web

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Towny and Bukkit are not thread-safe: every API touch from the HTTP threads goes through here. */
object MainThread {
    fun <T> call(plugin: Plugin, timeoutSeconds: Long = 15, block: () -> T): T {
        if (Bukkit.isPrimaryThread()) return block()

        val future = CompletableFuture<T>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                future.complete(block())
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        })
        return future.get(timeoutSeconds, TimeUnit.SECONDS)
    }
}
