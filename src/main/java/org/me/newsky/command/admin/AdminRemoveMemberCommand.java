package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import org.me.newsky.NewSky;
import org.me.newsky.command.IslandSubCommand;
import org.me.newsky.cache.CacheHandler;

import java.util.Optional;
import java.util.UUID;

public class AdminRemoveMemberCommand implements IslandSubCommand {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;

    public AdminRemoveMemberCommand(NewSky plugin) {
        this.plugin = plugin;
        this.cacheHandler = plugin.getCacheHandler();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /islandadmin removemember <player> <islandowner>");
            return true;
        }

        OfflinePlayer targetRemove = Bukkit.getOfflinePlayer(args[1]);
        OfflinePlayer targetIslandOwner = Bukkit.getOfflinePlayer(args[2]);

        Optional<UUID> islandUuid = cacheHandler.getIslandUuidByPlayerUuid(targetIslandOwner.getUniqueId());

        // Check if the target player have an island
        if (islandUuid.isEmpty()) {
            sender.sendMessage(targetIslandOwner.getName() + " don't have an island.");
            return true;
        }

        // Check if the target player is a member of the island
        if (!cacheHandler.getIslandMembers(islandUuid.get()).contains(targetRemove.getUniqueId())) {
            sender.sendMessage(targetRemove.getName() + " is not a member of your island.");
            return true;
        }

        // Check if the target player is the owner of the island
        if (cacheHandler.getIslandOwner(islandUuid.get()).equals(targetRemove.getUniqueId())) {
            sender.sendMessage("You can't remove the owner of your island.");
            return true;
        }

        // Remove the target player from the island
        cacheHandler.removeIslandMember(targetIslandOwner.getUniqueId(), targetRemove.getUniqueId());
        sender.sendMessage("Removed " + targetRemove.getName() + " from " + targetIslandOwner.getName() + " island.");

        return true;
    }
}