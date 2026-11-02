package me.elonchunk.dynoClaims.commands

import me.elonchunk.dynoClaims.DynoClaims
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

/** /dynoclaimreload          - reload config, restart the API, redeploy web files, show status
 *  /dynoclaimreload status   - just show the status / setup check */
class ReloadCommand(private val plugin: DynoClaims) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (!sender.hasPermission("dynoclaims.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED))
            return true
        }

        val statusOnly = args.firstOrNull().equals("status", ignoreCase = true)
        val lines = if (statusOnly) plugin.statusLines() else plugin.reloadAll()

        sender.sendMessage(Component.text(
            if (statusOnly) "DynoClaims status:" else "DynoClaims reloaded:", NamedTextColor.GREEN
        ))
        lines.forEach { sender.sendMessage(Component.text(" - $it", NamedTextColor.GRAY)) }
        return true
    }
}
