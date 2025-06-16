package org.me.newsky.world;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.loaders.SlimeLoader;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.world.SlimeWorldInstance;
import com.infernalsuite.asp.api.world.properties.SlimeProperties;
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap;
import com.infernalsuite.asp.loaders.mysql.MysqlLoader;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.teleport.TeleportHandler;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class WorldHandler {

    public final NewSky plugin;
    public final ConfigHandler config;
    public final TeleportHandler teleportHandler;
    private final SlimeLoader slimeLoader;
    private final SlimePropertyMap properties;
    private final AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();

    public WorldHandler(NewSky plugin, ConfigHandler config, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.config = config;
        this.teleportHandler = teleportHandler;

        try {
            this.slimeLoader = new MysqlLoader("jdbc:mysql://{host}:{port}/{database}?useSSL={usessl}&autoReconnect=true&useUnicode=true&characterEncoding=utf8", config.getMySQLHost(), config.getMySQLPort(), config.getMySQLDB(), false, config.getMySQLUsername(), config.getMySQLPassword());
            plugin.debug("WorldHandler", "Initialized MySQL slimeLoader successfully.");
        } catch (SQLException e) {
            plugin.severe("Failed to initialize MySQL slimeLoader", e);
            throw new RuntimeException(e);
        }

        properties = new SlimePropertyMap();
        properties.setValue(SlimeProperties.DIFFICULTY, "normal");
        properties.setValue(SlimeProperties.ENVIRONMENT, "normal");
        properties.setValue(SlimeProperties.SPAWN_X, config.getIslandSpawnX());
        properties.setValue(SlimeProperties.SPAWN_Y, config.getIslandSpawnY());
        properties.setValue(SlimeProperties.SPAWN_Z, config.getIslandSpawnZ());
        plugin.debug("WorldHandler", "Default slime world properties configured.");
    }

    public CompletableFuture<Void> createWorld(String worldName) {
        plugin.debug("WorldHandler", "Creating world: " + worldName);
        CompletableFuture<Void> future = new CompletableFuture<>();

        File templateWorld = plugin.getDataFolder().toPath().resolve("template/" + config.getTemplateWorldName()).toFile();
        plugin.debug("WorldHandler", "Template world path resolved: " + templateWorld.getAbsolutePath());

        if (!templateWorld.exists()) {
            plugin.severe("Template world folder not found: " + templateWorld.getAbsolutePath());
            future.completeExceptionally(new IllegalStateException("Template folder not found"));
            return future;
        }

        try {
            SlimeWorld newWorld = asp.readVanillaWorld(templateWorld, worldName, slimeLoader);
            plugin.debug("WorldHandler", "Vanilla world read successfully for: " + worldName);
            asp.saveWorld(newWorld);
            plugin.debug("WorldHandler", "World saved to slime loader: " + worldName);
            SlimeWorld loadedWorld = asp.readWorld(slimeLoader, worldName, false, properties);
            return loadWorldToBukkit(loadedWorld);
        } catch (Exception e) {
            plugin.severe("Failed to create slime world: " + worldName, e);
            future.completeExceptionally(e);
            return future;
        }
    }

    public CompletableFuture<Void> loadWorld(String worldName) {
        plugin.debug("WorldHandler", "Loading world: " + worldName);
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (isWorldLoaded(worldName)) {
            plugin.debug("WorldHandler", "World already loaded: " + worldName);
            future.complete(null);
            return future;
        }

        try {
            SlimeWorld world = asp.readWorld(slimeLoader, worldName, false, properties);
            plugin.debug("WorldHandler", "World read from slime loader: " + worldName);
            return loadWorldToBukkit(world);
        } catch (Exception e) {
            plugin.severe("Failed to load world: " + worldName, e);
            future.completeExceptionally(e);
            return future;
        }
    }

    public CompletableFuture<Void> unloadWorld(String worldName) {
        plugin.debug("WorldHandler", "Unloading world: " + worldName);
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (!isWorldLoaded(worldName)) {
            plugin.debug("WorldHandler", "World is not loaded: " + worldName);
            future.complete(null);
            return future;
        }

        try {
            SlimeWorld world = asp.getLoadedWorld(worldName);
            asp.saveWorld(world);
            plugin.debug("WorldHandler", "World saved before unload: " + worldName);
            return unloadWorldFromBukkit(worldName);
        } catch (Exception e) {
            plugin.severe("Failed to unload slime world: " + worldName, e);
            future.completeExceptionally(e);
            return future;
        }
    }

    public CompletableFuture<Void> deleteWorld(String worldName) {
        plugin.debug("WorldHandler", "Deleting world: " + worldName);
        CompletableFuture<Void> future = new CompletableFuture<>();

        unloadWorldFromBukkit(worldName).thenRun(() -> {
            try {
                slimeLoader.deleteWorld(worldName);
                plugin.debug("WorldHandler", "Deleted slime world: " + worldName);
                future.complete(null);
            } catch (Exception e) {
                plugin.severe("Failed to delete slime world: " + worldName, e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    public boolean isWorldLoaded(String worldName) {
        return Bukkit.getWorld(worldName) != null;
    }

    public void removePlayersFromWorld(World world) {
        plugin.debug("WorldHandler", "Removing players from world: " + world.getName());
        for (Player player : world.getPlayers()) {
            plugin.debug("WorldHandler", "Teleporting player: " + player.getName());
            player.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), config.getLobbyCommand(player.getName()));
        }
    }

    private CompletableFuture<Void> loadWorldToBukkit(SlimeWorld world) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                asp.loadWorld(world, true);
                plugin.debug("WorldHandler", "World loaded into Bukkit: " + world.getName());
                future.complete(null);
            } catch (Exception e) {
                plugin.severe("Failed to load world to Bukkit: " + world.getName(), e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    public CompletableFuture<Void> unloadWorldFromBukkit(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    removePlayersFromWorld(world);
                    if (Bukkit.unloadWorld(world, false)) {
                        plugin.debug("WorldHandler", "World unloaded from Bukkit: " + worldName);
                    } else {
                        future.completeExceptionally(new IllegalStateException("Failed to unload world from Bukkit: " + worldName));
                        plugin.severe("Failed to unload world from Bukkit: " + worldName);
                    }
                }
                future.complete(null);
            } catch (Exception e) {
                plugin.severe("Failed to unload world from Bukkit: " + worldName, e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    public void unloadAllWorldsOnShutdown() {
        plugin.debug("WorldHandler", "Unloading all worlds on shutdown...");
        List<SlimeWorldInstance> loadedWorlds = asp.getLoadedWorlds();
        for (SlimeWorldInstance worldInstance : loadedWorlds) {
            try {
                asp.saveWorld(worldInstance);
                Bukkit.unloadWorld(worldInstance.getName(), false);
                plugin.debug("WorldHandler", "World unloaded on shutdown: " + worldInstance.getName());
            } catch (Exception e) {
                plugin.severe("Failed to unload world on shutdown: " + worldInstance.getName(), e);
            }
        }
    }
}