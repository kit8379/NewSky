package org.me.newsky.command.player;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * /is top
 */
public class PlayerTopCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerTopCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "top";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerTopAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerTopPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerTopSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerTopDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        int size = 10;

        api.getTopIslandLevels(size).thenAccept(topIslands -> {
            if (topIslands.isEmpty()) {
                player.sendMessage(config.getNoIslandsFoundMessage());
                return;
            }

            player.sendMessage(config.getTopIslandsHeaderMessage());

            int[] rank = {1};

            for (Map.Entry<UUID, Integer> entry : topIslands.entrySet()) {
                UUID islandUuid = entry.getKey();
                int level = entry.getValue();
                int currentRank = rank[0]++;

                CompletableFuture<UUID> ownerFuture = api.getIslandOwner(islandUuid);
                CompletableFuture<Set<UUID>> membersFuture = api.getIslandMembers(islandUuid);

                CompletableFuture.allOf(ownerFuture, membersFuture).thenAccept(v -> {
                    try {
                        UUID ownerUuid = ownerFuture.get();
                        Set<UUID> members = membersFuture.get();
                        String ownerName = Bukkit.getOfflinePlayer(ownerUuid).getName();

                        String membersStr = members.stream().filter(uuid -> !uuid.equals(ownerUuid)).map(uuid -> Bukkit.getOfflinePlayer(uuid).getName()).filter(Objects::nonNull).reduce((a, b) -> a + ", " + b).orElse("-");

                        player.sendMessage(config.getTopIslandMessage(currentRank, ownerName != null ? ownerName : ownerUuid.toString(), membersStr, level));
                    } catch (Exception e) {
                        player.sendMessage("There was an error processing the island info for the top list.");
                        plugin.getLogger().log(Level.SEVERE, "Error processing island info for top list", e);
                    }
                });
            }
        }).exceptionally(ex -> {
            player.sendMessage(Component.text("There was an error retrieving the top islands."));
            plugin.getLogger().log(Level.SEVERE, "Error retrieving top islands for player " + player.getName(), ex);
            return null;
        });

        return true;
    }
}