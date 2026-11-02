package me.elonchunk.dynoClaims.commands

import me.elonchunk.dynoClaims.DynoClaims
import me.elonchunk.dynoClaims.towny.TownyBridge
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class DynoClaimCommand(private val plugin: DynoClaims) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage("This command can only be used in-game.")
            return true
        }

        // Fail here, in-game, instead of letting people open a web page that just says "no town".
        if (TownyBridge.getTown(player) == null) {
            player.sendMessage(Component.text("You need to be in a town first. Create one with /town new <name>.", NamedTextColor.RED))
            return true
        }
        if (!plugin.mayClaim(player)) {
            player.sendMessage(Component.text("You don't have permission to claim land for your town.", NamedTextColor.RED))
            return true
        }

        val base = plugin.webBaseUrl(player.virtualHost?.hostString)
        if (base == null) {
            player.sendMessage(Component.text(
                "The web claim map isn't set up yet. Ask an admin to run /dynoclaimreload.", NamedTextColor.RED
            ))
            return true
        }

        val ttl = plugin.config.getLong("web.token-ttl-seconds", 300)
        val token = plugin.tokenStore.createToken(player.uniqueId, ttl)
        val link = "$base/claimmanager.html?token=$token"

        val open = Component.text("[ OPEN CLAIM MAP ]", NamedTextColor.AQUA)
            .decorate(TextDecoration.BOLD)
            .clickEvent(ClickEvent.openUrl(link))
            .hoverEvent(HoverEvent.showText(Component.text("Opens the claim map in your browser", NamedTextColor.GRAY)))

        val copy = Component.text(" [copy link]", NamedTextColor.GRAY)
            .clickEvent(ClickEvent.copyToClipboard(link))
            .hoverEvent(HoverEvent.showText(Component.text("Copy the link instead", NamedTextColor.GRAY)))

        player.sendMessage(Component.text("Your claim link is ready:", NamedTextColor.GREEN))
        player.sendMessage(open.append(copy))
        player.sendMessage(Component.text("The link is just for you and stays active while you use it.", NamedTextColor.GRAY))
        return true
    }
}
