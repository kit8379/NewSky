package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandBusyException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.NoActiveServerException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDeleteIslandCommand implements SubCommand {

    private static final long CONFIRM_TIMEOUT_MS = 15_000L;

    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    private final Map<UUID, Integer> confirmStage = new HashMap<>();
    private final Map<UUID, Long> lastConfirmTime = new HashMap<>();

    public PlayerDeleteIslandCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "delete";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerDeleteAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerDeletePermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerDeleteSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerDeleteDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        UUID playerUuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Timeout reset
        Long last = lastConfirmTime.get(playerUuid);
        if (last != null && now - last > CONFIRM_TIMEOUT_MS) {
            reset(playerUuid);
        }

        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(playerUuid);
        } catch (IslandDoesNotExistException e) {
            player.sendMessage(config.getPlayerNoIslandMessage());
            reset(playerUuid);
            return true;
        }

        UUID ownerUuid = api.getIslandOwner(islandUuid);
        if (ownerUuid == null || !ownerUuid.equals(playerUuid)) {
            player.sendMessage(config.getPlayerDeleteNotOwnerMessage());
            reset(playerUuid);
            return true;
        }

        int stage = confirmStage.getOrDefault(playerUuid, 0);

        // -------------------------
        // Stage 0 → First warning
        // -------------------------
        if (stage == 0) {
            confirmStage.put(playerUuid, 1);
            lastConfirmTime.put(playerUuid, now);
            player.sendMessage(config.getPlayerDeleteFirstWarningMessage());
            return true;
        }

        // -------------------------
        // Stage 1 → Final warning (tell them type /is delete again)
        // -------------------------
        if (stage == 1) {
            confirmStage.put(playerUuid, 2);
            lastConfirmTime.put(playerUuid, now);
            player.sendMessage(config.getPlayerDeleteFinalWarningMessage());
            return true;
        }

        // -------------------------
        // Stage 2 → Delete now (3rd time)
        // -------------------------
        if (stage == 2) {
            reset(playerUuid);

            api.deleteIsland(islandUuid).thenRun(() -> {
                api.sendPlayerMessage(playerUuid, config.getPlayerDeleteSuccessMessage());
            }).exceptionally(ex -> {
                Throwable cause = ex.getCause();
                if (cause instanceof IslandBusyException) {
                    player.sendMessage(config.getIslandBusyMessage());
                } else if (cause instanceof NoActiveServerException) {
                    player.sendMessage(config.getNoActiveServerMessage());
                } else {
                    player.sendMessage(config.getUnknownExceptionMessage());
                    plugin.severe("Error deleting island for player " + player.getName(), ex);
                }
                return null;
            });

            return true;
        }

        return true;
    }

    private void reset(UUID uuid) {
        confirmStage.remove(uuid);
        lastConfirmTime.remove(uuid);
    }
}
