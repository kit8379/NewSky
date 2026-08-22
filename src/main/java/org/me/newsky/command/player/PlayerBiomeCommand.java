package org.me.newsky.command.player;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.AsyncTabComplete;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.InvalidBiomeException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.LocationNotInIslandException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /is biome <biome>
 */
public class PlayerBiomeCommand implements SubCommand, AsyncTabComplete {

    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerBiomeCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "biome";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerBiomeAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerBiomePermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerBiomeSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerBiomeDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        if (args.length < 2) {
            return false;
        }

        String biomeName = args[1].toLowerCase(Locale.ROOT);

        UUID playerUuid = player.getUniqueId();
        String worldName = player.getWorld().getName();
        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();

        api.applyPlayerChunkBiome(playerUuid, worldName, chunkX, chunkZ, biomeName).thenRun(() -> player.sendMessage(config.getPlayerBiomeChangeSuccessMessage(biomeName))).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                player.sendMessage(config.getPlayerNoIslandMessage());
            } else if (cause instanceof LocationNotInIslandException) {
                player.sendMessage(config.getPlayerBiomeMustInOwnIslandMessage());
            } else if (cause instanceof InvalidBiomeException) {
                player.sendMessage(config.getPlayerBiomeInvalidMessage(biomeName));
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error handling biome command for player " + player.getName() + " biome=" + biomeName, ex);
            }

            return null;
        });

        return true;
    }

    @Override
    public CompletableFuture<List<String>> tabCompleteAsync(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);

            return CompletableFuture.completedFuture(getAllBiomeNames().stream().filter(name -> name.startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList()));
        }

        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    private List<String> getAllBiomeNames() {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);

        return registry.stream().map(registry::getKey).filter(Objects::nonNull).map(key -> key.getKey()).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}