package me.elonchunk.dynoClaims.commands

import me.elonchunk.dynoClaims.DynoClaims
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

        val ttl = plugin.config.getLong("web.token-ttl-seconds", 300)
        val token = plugin.tokenStore.createToken(player.uniqueId, ttl)

        val webBase = plugin.config.getString("web.public-base-url", "") ?: ""
        val link = if (webBase.isEmpty())
            "claimmanager.html?token=$token"
        else
            "$webBase/claimmanager.html?token=$token"

        player.sendMessage(Component.text("Your claim link is ready:", NamedTextColor.GREEN))

        val clickableLink = Component.text("[ CLICK HERE TO COPY LINK ]", NamedTextColor.AQUA)
            .decorate(TextDecoration.BOLD)
            .clickEvent(ClickEvent.copyToClipboard(link))
            .hoverEvent(HoverEvent.showText(Component.text("Click to copy, then paste into your browser", NamedTextColor.GRAY)))

        player.sendMessage(clickableLink)
        player.sendMessage(Component.text("This link expires in $ttl seconds.", NamedTextColor.GRAY))

        return true
    }
}