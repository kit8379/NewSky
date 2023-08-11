package org.me.newsky.command.player;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.command.IslandSubCommand;
import org.me.newsky.handler.CacheHandler;

public class IslandRemoveMemberCommand implements IslandSubCommand {
    private final NewSky plugin;
    private final CacheHandler cacheHandler;

    public IslandRemoveMemberCommand(NewSky plugin) {
        this.plugin = plugin;
        this.cacheHandler = plugin.getCacheHandler();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        OfflinePlayer targetRemove = Bukkit.getOfflinePlayer(args[1]);
        cacheHandler.addIslandMember(player.getUniqueId(), targetRemove.getUniqueId());
        sender.sendMessage("Removed " + targetRemove.getName() + " from your island.");
        return true;
    }
}
