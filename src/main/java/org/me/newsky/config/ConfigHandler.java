package org.me.newsky.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.me.newsky.util.Utils;

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

    public String getServerName() {
        return config.getString("server.name");
    }

    public String getServerMode() {
        return config.getString("server.mode");
    }

    // Messages
    public String getReloadMessage() {
        return Utils.colorize(config.getString("messages.reload"));
    }

    public String getUnknownCommandMessage() {
        return Utils.colorize(config.getString("messages.unknown-command"));
    }

    public String getNoConsoleMessage() {
        return Utils.colorize(config.getString("messages.no-console"));
    }

    public String getNoIslandMessage() {
        return Utils.colorize(config.getString("messages.no-island"));
    }

    public String getNoIslandOwnerMessage() {
        return Utils.colorize(config.getString("messages.no-island-owner"));
    }

    public String getPlayerAlreadyIslandOwnerMessage() {
        return Utils.colorize(config.getString("messages.player-already-island-owner"));
    }

    public String getPlayerAlreadyIslandOwnerMessage(String playerName) {
        String messageTemplate = getPlayerAlreadyIslandOwnerMessage();
        return messageTemplate.replace("%player%", playerName);
    }

    public String getHasIslandMessage() {
        return Utils.colorize(config.getString("messages.has-island"));
    }

    public String getPlayerNotMemberMessage() {
        return Utils.colorize(config.getString("messages.player-not-member"));
    }

    public String getPlayerNotMemberMessage(String playerName) {
        String messageTemplate = getPlayerNotMemberMessage();
        return messageTemplate.replace("%player%", playerName);
    }

    public String getPlayerAlreadyMemberMessage() {
        return Utils.colorize(config.getString("messages.player-already-member"));
    }

    public String getPlayerAlreadyMemberMessage(String playerName) {
        String messageTemplate = getPlayerAlreadyMemberMessage();
        return messageTemplate.replace("%player%", playerName);
    }

    public String getCannotDeleteOwnerMessage() {
        return Utils.colorize(config.getString("messages.cannot-delete-owner"));
    }

    public String getIslandCreatedMessage() {
        return Utils.colorize(config.getString("messages.island-created"));
    }

    public String getIslandDeletedMessage() {
        return Utils.colorize(config.getString("messages.island-deleted"));
    }

    public String getPlayerNoIslandMessage() {
        return Utils.colorize(config.getString("messages.player-no-island"));
    }

    public String getPlayerNoIslandMessage(String playerName) {
        String messageTemplate = getPlayerNoIslandMessage();
        return messageTemplate.replace("%player%", playerName);
    }

    public String getPlayerHasIslandMessage() {
        return Utils.colorize(config.getString("messages.player-has-island"));
    }

    public String getPlayerHasIslandMessage(String playerName) {
        String messageTemplate = getPlayerHasIslandMessage();
        return messageTemplate.replace("%player%", playerName);
    }

    public String getPlayerIslandCreatedMessage() {
        return Utils.colorize(config.getString("messages.player-island-created"));
    }

    public String getPlayerIslandCreatedMessage(String playerName) {
        String messageTemplate = getPlayerIslandCreatedMessage();
        return messageTemplate.replace("%player%", playerName);
    }

    public String getPlayerIslandDeletedMessage() {
        return Utils.colorize(config.getString("messages.player-island-deleted"));
    }

    public String getPlayerIslandDeletedMessage(String playerName) {
        String messageTemplate = getPlayerIslandDeletedMessage();
        return messageTemplate.replace("%player%", playerName);
    }

    public String getPlayerIslandAddedMessage() {
        return Utils.colorize(config.getString("messages.player-island-added"));
    }

    public String getPlayerIslandAddedMessage(String playerName, String islandOwner) {
        String messageTemplate = getPlayerIslandAddedMessage();
        return messageTemplate.replace("%player%", playerName).replace("%islandowner%", islandOwner);
    }

    public String getPlayerIslandRemovedMessage() {
        return Utils.colorize(config.getString("messages.player-island-removed"));
    }

    public String getPlayerIslandRemovedMessage(String playerName, String islandOwner) {
        String messageTemplate = getPlayerIslandRemovedMessage();
        return messageTemplate.replace("%player%", playerName).replace("%islandowner%", islandOwner);
    }

    public String getPlayerAddMemberCommandUsage() {
        return Utils.colorize(config.getString("messages.player-addmember-command-usage"));
    }

    public String getPlayerRemoveMemberCommandUsage() {
        return Utils.colorize(config.getString("messages.player-removemember-command-usage"));
    }

    public String getPlayerHomeCommandUsage() {
        return Utils.colorize(config.getString("messages.player-home-command-usage"));
    }

    public String getAdminCreateIslandCommandUsage() {
        return Utils.colorize(config.getString("messages.admin-createisland-command-usage"));
    }

    public String getAdminDeleteIslandCommandUsage() {
        return Utils.colorize(config.getString("messages.admin-deleteisland-command-usage"));
    }

    public String getAdminAddMemberCommandUsage() {
        return Utils.colorize(config.getString("messages.admin-addmember-command-usage"));
    }

    public String getAdminRemoveMemberCommandUsage() {
        return Utils.colorize(config.getString("messages.admin-removemember-command-usage"));
    }

    public String getAdminHomeCommandUsage() {
        return Utils.colorize(config.getString("messages.admin-home-command-usage"));
    }

    public String getIslandInfo(String island, String owner, String members) {
        String messageTemplate = Utils.colorize(config.getString("messages.island-info"));
        return messageTemplate.replace("%island%", island).replace("%owner%", owner).replace("%members%", members);
    }
}
