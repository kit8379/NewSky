package org.me.newsky.command.player;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<UUID, Integer> topIslands;
            try {
                topIslands = api.getTopIslandLevels(size);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error retrieving top islands", e);
                player.sendMessage(Component.text("There was an error retrieving the top islands."));
                return;
            }

            if (topIslands.isEmpty()) {
                player.sendMessage(config.getNoIslandsFoundMessage());
                return;
            }

            player.sendMessage(config.getTopIslandsHeaderMessage());

            int rank = 1;

            for (Map.Entry<UUID, Integer> entry : topIslands.entrySet()) {
                UUID islandUuid = entry.getKey();
                int level = entry.getValue();

                try {
                    UUID ownerUuid = api.getIslandOwner(islandUuid);
                    Set<UUID> members = api.getIslandMembers(islandUuid);
                    OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUuid);
                    String ownerName = owner.getName() != null ? owner.getName() : ownerUuid.toString();

                    String membersStr = members.stream().filter(uuid -> !uuid.equals(ownerUuid)).map(uuid -> {
                        String name = Bukkit.getOfflinePlayer(uuid).getName();
                        return name != null ? name : uuid.toString();
                    }).reduce((a, b) -> a + ", " + b).orElse("-");

                    player.sendMessage(config.getTopIslandMessage(rank++, ownerName, membersStr, level));
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error processing island info for top list", e);
                    player.sendMessage("There was an error processing the island info for the top list.");
                }
            }
        });

        return true;
    }
}
