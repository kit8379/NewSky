package org.me.newsky.command.player;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.command.IslandSubCommand;
import org.me.newsky.cache.CacheHandler;

import java.util.Optional;
import java.util.UUID;

public class IslandRemoveMemberCommand implements IslandSubCommand {
    private final NewSky plugin;
    private final CacheHandler cacheHandler;

    public IslandRemoveMemberCommand(NewSky plugin) {
        this.plugin = plugin;
        this.cacheHandler = plugin.getCacheHandler();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if(args.length != 2) {
            sender.sendMessage("Usage: /island removemember <player>");
            return true;
        }

        Player player = (Player) sender;
        OfflinePlayer targetRemove = Bukkit.getOfflinePlayer(args[1]);
        Optional<UUID> islandUuid = cacheHandler.getIslandUuidByPlayerUuid(player.getUniqueId());

        if (islandUuid.isPresent()) {
            cacheHandler.removeIslandMember(islandUuid.get(), targetRemove.getUniqueId());
            sender.sendMessage("Removed " + targetRemove.getName() + " from your island.");
        } else {
            sender.sendMessage("You don't have an island.");
        }

        return true;
    }
}
