package org.me.newsky.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;

import java.util.concurrent.CompletableFuture;

public abstract class WorldHandler {

    protected final NewSky plugin;
    protected final ConfigHandler config;

    public WorldHandler(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;
        this.config = config;
    }

    public abstract CompletableFuture<Void> createWorld(String worldName);

    public abstract CompletableFuture<Void> deleteWorld(String worldName);

    public abstract CompletableFuture<Void> loadWorld(String worldName);

    public abstract CompletableFuture<Void> unloadWorld(String worldName);

    public void saveWorld(World world) {
    }

    protected boolean isWorldLoaded(String worldName) {
        return Bukkit.getWorld(worldName) != null;
    }

    protected void removePlayersFromWorld(World world) {
        World safeWorld = Bukkit.getServer().getWorlds().get(0);
        for (Player player : world.getPlayers()) {
            player.teleport(safeWorld.getSpawnLocation());
        }
    }

    protected CompletableFuture<Void> unloadWorldFromBukkit(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (!isWorldLoaded(worldName)) {
            future.complete(null);
            return future;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                removePlayersFromWorld(world);
                Bukkit.unloadWorld(world, true);
                future.complete(null);
            } else {
                future.completeExceptionally(new IllegalStateException(config.getIslandNotLoadedMessage()));
            }
        });

        return future;
    }
}
