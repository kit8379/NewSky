package org.me.newsky.world;

import com.infernalsuite.aswm.api.SlimePlugin;
import com.infernalsuite.aswm.api.loaders.SlimeLoader;
import com.infernalsuite.aswm.api.world.SlimeWorld;
import com.infernalsuite.aswm.api.world.properties.SlimeProperties;
import com.infernalsuite.aswm.api.world.properties.SlimePropertyMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class WorldHandler {

    public final NewSky plugin;

    public final ConfigHandler config;
    private final SlimePlugin slimePlugin;
    private final SlimeLoader slimeLoader;
    private final SlimePropertyMap properties;

    public WorldHandler(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;
        this.config = config;
        this.slimePlugin = (SlimePlugin) Bukkit.getPluginManager().getPlugin("SlimeWorldManager");
        this.slimeLoader = Objects.requireNonNull(slimePlugin).getLoader("mysql");

        // Set world properties
        properties = new SlimePropertyMap();
        properties.setString(SlimeProperties.DIFFICULTY, "normal");
        properties.setInt(SlimeProperties.SPAWN_X, config.getIslandSpawnX());
        properties.setInt(SlimeProperties.SPAWN_Y, config.getIslandSpawnY());
        properties.setInt(SlimeProperties.SPAWN_Z, config.getIslandSpawnZ());
        properties.setValue(SlimeProperties.DIFFICULTY, "normal");
        properties.setValue(SlimeProperties.DEFAULT_BIOME, "plains");
    }

    public CompletableFuture<Void> createWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            try {
                File templateWorld = plugin.getDataFolder().toPath().resolve("template/" + config.getTemplateWorldName()).toFile();
                slimePlugin.importWorld(templateWorld, worldName, slimeLoader);
                SlimeWorld world = slimePlugin.loadWorld(slimeLoader, worldName, false, properties);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        slimePlugin.loadWorld(world);
                        future.complete(null);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                        e.printStackTrace();
                    }
                });
            } catch (Exception e) {
                future.completeExceptionally(e);
                e.printStackTrace();
            }
        });

        return future;
    }

    public CompletableFuture<Void> loadWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (isWorldLoaded(worldName)) {
            future.complete(null);
            return future;
        }

        CompletableFuture.runAsync(() -> {
            try {
                SlimeWorld world = slimePlugin.loadWorld(slimeLoader, worldName, false, properties);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        slimePlugin.loadWorld(world);
                        future.complete(null);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                        e.printStackTrace();
                    }
                });
            } catch (Exception e) {
                future.completeExceptionally(e);
                e.printStackTrace();
            }
        });

        return future;
    }

    public CompletableFuture<Void> unloadWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        unloadWorldFromBukkit(worldName, true).thenRunAsync(() -> {
            future.complete(null);
        });

        return future;
    }

    public CompletableFuture<Void> deleteWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        unloadWorldFromBukkit(worldName, false).thenRunAsync(() -> {
            try {
                slimeLoader.deleteWorld(worldName);
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
                e.printStackTrace();
            }
        });

        return future;
    }

    public boolean isWorldLoaded(String worldName) {
        return Bukkit.getWorld(worldName) != null;
    }

    public void removePlayersFromWorld(World world) {
        World safeWorld = Bukkit.getServer().getWorlds().get(0);
        for (Player player : world.getPlayers()) {
            player.teleport(safeWorld.getSpawnLocation());
        }
    }

    public CompletableFuture<Void> unloadWorldFromBukkit(String worldName, boolean save) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (!isWorldLoaded(worldName)) {
            future.complete(null);
            return future;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                removePlayersFromWorld(world);
                Bukkit.unloadWorld(world, save);
            }
            future.complete(null);
        });

        return future;
    }
}