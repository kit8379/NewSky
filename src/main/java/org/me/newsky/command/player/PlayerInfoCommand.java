package org.me.newsky.command.player;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * /is info [player]
 */
public class PlayerInfoCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerInfoCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "info";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerInfoAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerInfoPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerInfoSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerInfoDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        UUID playerUuid;
        if (args.length < 2) {
            playerUuid = player.getUniqueId();
        } else {
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(args[1]);
            playerUuid = targetPlayer.getUniqueId();
        }

        api.getIslandUuid(playerUuid).thenCompose(islandUuid -> {
            CompletableFuture<UUID> ownerFuture = api.getIslandOwner(islandUuid);
            CompletableFuture<Set<UUID>> membersFuture = api.getIslandMembers(islandUuid);
            CompletableFuture<Integer> levelFuture = api.getIslandLevel(islandUuid);

            return CompletableFuture.allOf(ownerFuture, membersFuture, levelFuture).thenApply(v -> {
                try {
                    UUID ownerUuid = ownerFuture.get();
                    Set<UUID> members = membersFuture.get();
                    int level = levelFuture.get();

                    String ownerName = Bukkit.getOfflinePlayer(ownerUuid).getName();
                    String memberNames = members.stream().map(uuid -> Bukkit.getOfflinePlayer(uuid).getName()).reduce((a, b) -> a + ", " + b).orElse(LegacyComponentSerializer.legacyAmpersand().serialize(config.getIslandInfoNoMembersMessage()));

                    sender.sendMessage(config.getIslandInfoHeaderMessage());
                    sender.sendMessage(config.getIslandInfoUUIDMessage(islandUuid));
                    sender.sendMessage(config.getIslandInfoOwnerMessage(ownerName));
                    sender.sendMessage(config.getIslandInfoMembersMessage(memberNames));
                    sender.sendMessage(config.getIslandInfoLevelMessage(level));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getPlayerNoIslandMessage());
            } else {
                sender.sendMessage("There was an error getting the island information.");
                plugin.getLogger().log(Level.SEVERE, "Error getting island information for player " + player.getName(), ex);
            }
            return null;
        });

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return api.getOnlinePlayers().stream().filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
