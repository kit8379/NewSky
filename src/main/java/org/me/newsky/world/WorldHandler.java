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
import java.util.logging.Level;

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
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize MySQL slimeLoader", e);
            throw new RuntimeException(e);
        }

        properties = new SlimePropertyMap();
        properties.setValue(SlimeProperties.DIFFICULTY, "normal");
        properties.setValue(SlimeProperties.ENVIRONMENT, "normal");
        properties.setValue(SlimeProperties.SPAWN_X, config.getIslandSpawnX());
        properties.setValue(SlimeProperties.SPAWN_Y, config.getIslandSpawnY());
        properties.setValue(SlimeProperties.SPAWN_Z, config.getIslandSpawnZ());
    }

    public CompletableFuture<Void> createWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        File templateWorld = plugin.getDataFolder().toPath().resolve("template/" + config.getTemplateWorldName()).toFile();

        if (!templateWorld.exists()) {
            plugin.getLogger().log(Level.SEVERE, "Template folder not found: " + templateWorld.getAbsolutePath());
            future.completeExceptionally(new IllegalStateException("Template folder not found"));
            return future;
        }

        try {
            SlimeWorld newWorld = asp.readVanillaWorld(templateWorld, worldName, slimeLoader);
            asp.saveWorld(newWorld);
            SlimeWorld loadedWorld = asp.readWorld(slimeLoader, worldName, false, properties);

            return loadWorldToBukkit(loadedWorld);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create world: " + worldName, e);
            future.completeExceptionally(e);
            return future;
        }
    }

    public CompletableFuture<Void> loadWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (isWorldLoaded(worldName)) {
            future.complete(null);
            return future;
        }

        try {
            SlimeWorld world = asp.readWorld(slimeLoader, worldName, false, properties);
            return loadWorldToBukkit(world);
        } catch (Exception e) {
            future.completeExceptionally(e);
            return future;
        }
    }


    public CompletableFuture<Void> unloadWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (!isWorldLoaded(worldName)) {
            future.complete(null);
            return future;
        }

        try {
            SlimeWorld world = asp.getLoadedWorld(worldName);
            asp.saveWorld(world);
            return unloadWorldFromBukkit(worldName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save slime world before unload: " + worldName, e);
            future.completeExceptionally(e);
            return future;
        }
    }

    public CompletableFuture<Void> deleteWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        unloadWorldFromBukkit(worldName).thenRun(() -> {
            try {
                slimeLoader.deleteWorld(worldName);
                future.complete(null);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete slime world: " + worldName, e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    public boolean isWorldLoaded(String worldName) {
        return Bukkit.getWorld(worldName) != null;
    }

    public void removePlayersFromWorld(World world) {
        for (Player player : world.getPlayers()) {
            player.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), config.getLobbyCommand(player.getName()));
        }
    }

    private CompletableFuture<Void> loadWorldToBukkit(SlimeWorld world) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                asp.loadWorld(world, true);
                future.complete(null);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load world to Bukkit: " + world.getName(), e);
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
                    Bukkit.unloadWorld(world, false);
                }
                future.complete(null);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to unload world from Bukkit: " + worldName, e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    public void unloadAllWorldsOnShutdown() {
        List<SlimeWorldInstance> loadedWorlds = asp.getLoadedWorlds();
        for (SlimeWorldInstance worldInstance : loadedWorlds) {
            try {
                asp.saveWorld(worldInstance);
                Bukkit.unloadWorld(worldInstance.getName(), false);
                plugin.getLogger().info("Saved and unloaded slime world: " + worldInstance.getName());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save/unload world: " + worldInstance.getName(), e);
            }
        }
    }
}