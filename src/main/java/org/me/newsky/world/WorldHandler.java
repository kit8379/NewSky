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


}
