package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.command.IslandSubCommand;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.island.IslandHandler;

import java.util.Optional;
import java.util.UUID;

public class AdminAddMemberCommand implements IslandSubCommand {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final IslandHandler islandHandler;

    public AdminAddMemberCommand(NewSky plugin, CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
        this.islandHandler = islandHandler;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /islandadmin addmember <player> <islandowner>");
            return true;
        }

        OfflinePlayer targetAdd = Bukkit.getOfflinePlayer(args[1]);
        OfflinePlayer targetIslandOwner = Bukkit.getOfflinePlayer(args[2]);

        Optional<UUID> islandUuid = cacheHandler.getIslandUuidByPlayerUuid(targetIslandOwner.getUniqueId());

        // Check if the player has an island
        if (islandUuid.isEmpty()) {
            sender.sendMessage(targetIslandOwner.getName() + " don't have an island.");
            return true;
        }

        // Check if the player is already a member of the island
        if (cacheHandler.getIslandMembers(islandUuid.get()).contains(targetAdd.getUniqueId())) {
            sender.sendMessage(targetAdd.getName() + " is already a member of your island.");
            return true;
        }

        // Check if the island is full
        if (cacheHandler.getIslandMembers(islandUuid.get()).size() >= 3) {
            sender.sendMessage(targetIslandOwner.getName() + " island is full.");
            return true;
        }

        // Check if the player already have an island
        if (cacheHandler.getIslandUuidByPlayerUuid(targetAdd.getUniqueId()).isPresent()) {
            sender.sendMessage(targetAdd.getName() + " already have an island.");
            return true;
        }

        // Teleport the player to the island
        if (targetAdd.isOnline()) {
            Player targetAddPlayer = (Player) targetAdd;
            targetAddPlayer.sendMessage(targetAdd.getName() + " have been added to " + targetIslandOwner.getName() + "'s island.");
            islandHandler.teleportToSpawn(targetAddPlayer, islandUuid.get().toString());
        }

        // Add the player to the island
        cacheHandler.addIslandMember(islandUuid.get(), targetAdd.getUniqueId());
        sender.sendMessage("Added " + targetAdd.getName() + " to " + targetIslandOwner.getName() + " island.");

        return true;
    }
}