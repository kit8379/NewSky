package org.me.newsky.command.player;

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

public class IslandAddMemberCommand implements IslandSubCommand {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final IslandHandler islandHandler;

    public IslandAddMemberCommand(NewSky plugin, ) {
        this.plugin = plugin;
        this.cacheHandler = plugin.getCacheHandler();
        this.islandHandler = plugin.getIslandHandler();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /island addmember <player>");
            return true;
        }

        Player player = (Player) sender;
        OfflinePlayer targetAdd = Bukkit.getOfflinePlayer(args[1]);

        if (targetAdd.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage("You can't add yourself to your island.");
            return true;
        }

        Optional<UUID> islandUuid = cacheHandler.getIslandUuidByPlayerUuid(player.getUniqueId());

        // Check if the player has an island
        if (islandUuid.isEmpty()) {
            sender.sendMessage("You don't have an island.");
            return true;
        }

        // Check if the player is already a member of the island
        if (cacheHandler.getIslandMembers(islandUuid.get()).contains(targetAdd.getUniqueId())) {
            sender.sendMessage(targetAdd.getName() + " is already a member of your island.");
            return true;
        }

        // Check if the island is full
        if (cacheHandler.getIslandMembers(islandUuid.get()).size() >= 3) {
            sender.sendMessage("Your island is full.");
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
            targetAddPlayer.sendMessage("You have been added to " + player.getName() + "'s island.");
            islandHandler.teleportToSpawn(targetAddPlayer, islandUuid.get().toString());
        }

        // Add the player to the island
        cacheHandler.addIslandMember(islandUuid.get(), targetAdd.getUniqueId());
        sender.sendMessage("Added " + targetAdd.getName() + " to your island.");

        return true;
    }
}