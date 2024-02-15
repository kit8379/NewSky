package org.me.newsky.config;

import org.bukkit.configuration.file.FileConfiguration;

public class ConfigHandler {
    private final FileConfiguration config;

    public ConfigHandler(FileConfiguration config) {
        this.config = config;
    }

    public String getDBHost() {
        return config.getString("DB.host");
    }

    public int getDBPort() {
        return config.getInt("DB.port");
    }

    public String getDBName() {
        return config.getString("DB.database");
    }

    public String getDBUsername() {
        return config.getString("DB.username");
    }

    public String getDBPassword() {
        return config.getString("DB.password");
    }

    public String getDBProperties() {
        return config.getString("DB.properties");
    }

    public int getDBMaxPoolSize() {
        return config.getInt("DB.max-pool-size");
    }

    public int getDBConnectionTimeout() {
        return config.getInt("DB.connection-timeout");
    }

    public String getDBCachePrepStmts() {
        return config.getString("DB.cache-prep-statements");
    }

    public String getDBPrepStmtCacheSize() {
        return config.getString("DB.prep-stmt-cache-size");
    }

    public String getDBPrepStmtCacheSqlLimit() {
        return config.getString("DB.prep-stmt-cache-sql-limit");
    }

    public String getRedisHost() {
        return config.getString("redis.host");
    }

    public int getRedisPort() {
        return config.getInt("redis.port");
    }

    public String getRedisPassword() {
        return config.getString("redis.password");
    }

    public int getRedisDatabase() {
        return config.getInt("redis.database");
    }

    public boolean isDebug() {
        return config.getBoolean("debug");
    }

    public String getServerName() {
        return config.getString("server.name");
    }

    public boolean isLobby() {
        return config.getBoolean("server.lobby");
    }

    public String getServerMode() {
        return config.getString("server.mode");
    }

    public String getStoragePath() {
        return config.getString("server.storage-path");
    }

    public String getTemplateWorldName() {
        return config.getString("world.template");
    }

    public int getIslandSize() {
        return config.getInt("island.size");
    }

    public int getBufferSize() {
        return config.getInt("island.buffer");
    }

    public int getIslandSpawnX() {
        return config.getInt("island.spawn.x");
    }

    public String getCannotLeaveIslandBoundaryMessage() {
        return config.getString("messages.cannot-leave-island-boundary");
    }

    public int getIslandSpawnY() {
        return config.getInt("island.spawn.y");
    }

    public int getIslandSpawnZ() {
        return config.getInt("island.spawn.z");
    }

    public float getIslandSpawnYaw() {
        return (float) config.getDouble("island.spawn.yaw");
    }

    public float getIslandSpawnPitch() {
        return (float) config.getDouble("island.spawn.pitch");
    }

    public String getIslandNotFoundInServerMessage() {
        return config.getString("messages.island-not-found-in-server");
    }

    public String getNoActiveServerMessage() {
        return config.getString("messages.no-active-server");
    }

    public String getIslandNotLoadedMessage() {
        return config.getString("messages.island-not-loaded");
    }

    public String getIslandAlreadyLoadedMessage() {
        return config.getString("messages.island-already-loaded");
    }

    public String getCannotEditIslandMessage() {
        return config.getString("messages.cannot-edit-island");
    }

    public String getIslandPvpDisabledMessage() {
        return config.getString("messages.island-pvp-disabled");
    }
}
