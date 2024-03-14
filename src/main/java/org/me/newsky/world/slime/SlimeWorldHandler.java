package org.me.newsky.world.slime;

import com.infernalsuite.aswm.api.SlimePlugin;
import com.infernalsuite.aswm.api.loaders.SlimeLoader;
import com.infernalsuite.aswm.api.world.SlimeWorld;
import com.infernalsuite.aswm.api.world.properties.SlimeProperties;
import com.infernalsuite.aswm.api.world.properties.SlimePropertyMap;
import org.bukkit.Bukkit;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.world.WorldHandler;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class SlimeWorldHandler extends WorldHandler {

    private final SlimePlugin slimePlugin;
    private final SlimeLoader slimeLoader;
    private final SlimePropertyMap properties;

    public SlimeWorldHandler(NewSky plugin, ConfigHandler config) {
        super(plugin, config);
        this.slimePlugin = (SlimePlugin) Bukkit.getPluginManager().getPlugin("SlimeWorldManager");
        this.slimeLoader = Objects.requireNonNull(slimePlugin).getLoader(config.getSlimeDataSource());

        properties = new SlimePropertyMap();
        properties.setString(SlimeProperties.DIFFICULTY, "normal");
        properties.setInt(SlimeProperties.SPAWN_X, config.getIslandSpawnX());
        properties.setInt(SlimeProperties.SPAWN_Y, config.getIslandSpawnY());
        properties.setInt(SlimeProperties.SPAWN_Z, config.getIslandSpawnZ());
    }

    @Override
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
                    }
                });
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    @Override
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
                    }
                });
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }


    @Override
    public CompletableFuture<Void> unloadWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        unloadWorldFromBukkit(worldName).thenRunAsync(() -> {
            future.complete(null);
        }).exceptionally(e -> {
            future.completeExceptionally(e);
            return null;
        });

        return future;
    }

    @Override
    public CompletableFuture<Void> deleteWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        unloadWorldFromBukkit(worldName).thenRunAsync(() -> {
            try {
                slimeLoader.deleteWorld(worldName);
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }).exceptionally(e -> {
            future.completeExceptionally(e);
            return null;
        });

        return future;
    }
}
