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
        if (args.length < 2) {
            sender.sendMessage("Usage: /island removemember <player>");
            return true;
        }

        Player player = (Player) sender;
        OfflinePlayer targetRemove = Bukkit.getOfflinePlayer(args[1]);

        // Check if the player is trying to remove himself
        if (targetRemove.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage("You can't remove yourself from your island.");
            return true;
        }

        Optional<UUID> islandUuid = cacheHandler.getIslandUuidByPlayerUuid(player.getUniqueId());

        // Check if the player has an island
        if (islandUuid.isEmpty()) {
            sender.sendMessage("You don't have an island.");
            return true;
        }

        // Check if the player is a member of the island
        if (!cacheHandler.getIslandMembers(islandUuid.get()).contains(targetRemove.getUniqueId())) {
            sender.sendMessage(targetRemove.getName() + " is not a member of your island.");
            return true;
        }

        // Check if the player is the owner of the island
        if (cacheHandler.getIslandOwner(islandUuid.get()).equals(targetRemove.getUniqueId())) {
            sender.sendMessage("You can't remove the owner of your island.");
            return true;
        }

        // Remove the target player from the island
        cacheHandler.removeIslandMember(islandUuid.get(), targetRemove.getUniqueId());
        sender.sendMessage("Removed " + targetRemove.getName() + " from your island.");

        return true;
    }
}