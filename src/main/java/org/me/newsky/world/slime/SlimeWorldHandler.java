package org.me.newsky.world.slime;

import com.infernalsuite.aswm.api.SlimePlugin;
import com.infernalsuite.aswm.api.world.*;
import com.infernalsuite.aswm.api.loaders.SlimeLoader;
import com.infernalsuite.aswm.api.world.properties.SlimeProperties;
import com.infernalsuite.aswm.api.world.properties.SlimePropertyMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.world.WorldHandler;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class SlimeWorldHandler extends WorldHandler {

    private final SlimePlugin slimePlugin;
    private final SlimeLoader slimeLoader;

    public SlimeWorldHandler(NewSky plugin, ConfigHandler config) {
        super(plugin, config);
        this.slimePlugin = (SlimePlugin) Bukkit.getPluginManager().getPlugin("SlimeWorldManager");
        this.slimeLoader = slimePlugin.getLoader("mysql");
    }

    @Override
    public CompletableFuture<Void> createWorld(String worldName) {
        return loadWorld(worldName); // For slime worlds, creating is essentially loading.
    }

    @Override
    public CompletableFuture<Void> deleteWorld(String worldName) {
        return CompletableFuture.runAsync(() -> {
            try {
                slimeLoader.deleteWorld(worldName);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> loadWorld(String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SlimePropertyMap properties = new SlimePropertyMap();
                properties.setString(SlimeProperties.DIFFICULTY, "normal");
                properties.setInt(SlimeProperties.SPAWN_X, config.getIslandSpawnX());
                properties.setInt(SlimeProperties.SPAWN_Y, config.getIslandSpawnY());
                properties.setInt(SlimeProperties.SPAWN_Z, config.getIslandSpawnZ());

                SlimeWorld slimeWorld = slimePlugin.createEmptyWorld(slimeLoader, worldName, false, properties);
                return slimeWorld;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }).thenAcceptAsync(slimeWorld -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                slimePlugin.generateWorld(slimeWorld);
            });
        });
    }

    @Override
    public CompletableFuture<Void> unloadWorld(String worldName) {
        return CompletableFuture.runAsync(() -> {
            World world = Bukkit.getWorld(worldName);0
            if (world != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    removePlayersFromWorld(world);
                    slimePlugin.unloadWorld(worldName, true);
                });
            } else {
                throw new IllegalStateException(config.getIslandNotLoadedMessage());
            }
        });
    }
}
