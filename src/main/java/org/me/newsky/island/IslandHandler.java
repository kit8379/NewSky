package org.me.newsky.island;

import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.redis.RedisHeartBeat;

import java.util.Random;
import java.util.Set;

public class IslandHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final MVWorldManager mvWorldManager;
    private final RedisHandler redisHandler;
    private final RedisHeartBeat redisHeartBeat;

    public IslandHandler(NewSky plugin, ConfigHandler config, MVWorldManager mvWorldManager, RedisHandler redisHandler, RedisHeartBeat redisHeartBeat) {
        this.plugin = plugin;
        this.config = config;
        this.mvWorldManager = mvWorldManager;
        this.redisHandler = redisHandler;
        this.redisHeartBeat = redisHeartBeat;
    }



    public void createWorld(String worldName) {


    }

    public void loadWorld(String worldName) {


    }

    public void unloadWorld(String worldName) {


    }

    public void deleteWorld(String worldName) {


    }

    public void teleportToSpawn(Player player, String worldName) {


    }
}