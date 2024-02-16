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

    public int getRedisDatabase() {
        return config.getInt("redis.database");
    }

    public boolean isDebug() {
        return config.getBoolean("debug");
    }

    public String getServerName() {
        return config.getString("server.name");
    }

    public int getHeartbeatInterval() {
        return config.getInt("server.heartbeat-interval");
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
        return config.getString("island.template");
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

    public String getDebugPrefix() {
        return Utils.colorize(config.getString("messages.debug-prefix"));
    }

    public String getCannotLeaveIslandBoundaryMessage() {
        return Utils.colorize(config.getString("messages.cannot-leave-island-boundary"));
    }

    public String getIslandNotFoundInServerMessage() {
        return Utils.colorize(config.getString("messages.island-not-found-in-server"));
    }

    public String getNoActiveServerMessage() {
        return Utils.colorize(config.getString("messages.no-active-server"));
    }

    public String getIslandNotLoadedMessage() {
        return Utils.colorize(config.getString("messages.island-not-loaded"));
    }

    public String getIslandAlreadyLoadedMessage() {
        return Utils.colorize(config.getString("messages.island-already-loaded"));
    }

    public String getCannotEditIslandMessage() {
        return Utils.colorize(config.getString("messages.cannot-edit-island"));
    }

    public String getIslandPvpDisabledMessage() {
        return Utils.colorize(config.getString("messages.island-pvp-disabled"));
    }

    public String getIslandMemberExistsMessage(String name) {
        return Utils.colorize(config.getString("messages.island-member-exists").replace("{name}", name));
    }

    public String getNotIslandMemberMessage(String name) {
        return Utils.colorize(config.getString("messages.not-island-member").replace("{name}", name));
    }

    public String getOnlyPlayerCanRunCommandMessage() {
        return Utils.colorize(config.getString("messages.only-player-can-run-command"));
    }

    public String getNoIslandMessage(String name) {
        return Utils.colorize(config.getString("messages.no-island").replace("{name}", name));
    }

    public String getNoOwnerMessage() {
        return Utils.colorize(config.getString("messages.no-owner"));
    }

    public String getIslandIDMessage(String islandId) {
        return Utils.colorize(config.getString("messages.island-id").replace("{island}", islandId));
    }

    public String getIslandOwnerMessage(String ownerName) {
        return Utils.colorize(config.getString("messages.island-owner").replace("{owner}", ownerName));
    }

    public String getIslandMembersMessage(String membersString) {
        return Utils.colorize(config.getString("messages.island-members").replace("{members}", membersString));
    }

    public String getIslandLoadSuccessMessage(String name) {
        return Utils.colorize(config.getString("messages.island-load-success").replace("{name}", name));
    }

    public String getIslandUnloadSuccessMessage(String name) {
        return Utils.colorize(config.getString("messages.island-unload-success").replace("{name}", name));
    }

    public String getPluginReloadedMessage() {
        return Utils.colorize(config.getString("messages.plugin-reloaded"));
    }

    public String getAdminCommandHelpMessage() {
        return Utils.colorize(config.getString("messages.admin-command-help"));
    }

    public String getAdminUnknownSubCommandMessage(String subCommand) {
        return Utils.colorize(config.getString("messages.admin-unknown-sub-command").replace("{subCommand}", subCommand));
    }

    public String getAdminAddMemberUsageMessage() {
        return Utils.colorize(config.getString("messages.admin-add-member-usage"));
    }

    public String getAdminRemoveMemberUsageMessage() {
        return Utils.colorize(config.getString("messages.admin-remove-member-usage"));
    }

    public String getAdminCreateUsageMessage() {
        return Utils.colorize(config.getString("messages.admin-create-island-usage"));
    }

    public String getAdminDeleteUsageMessage() {
        return Utils.colorize(config.getString("messages.admin-delete-island-usage"));
    }

    public String getAdminSetHomeUsageMessage() {
        return Utils.colorize(config.getString("messages.admin-set-home-usage"));
    }

    public String getAdminDelHomeUsageMessage() {
        return Utils.colorize(config.getString("messages.admin-delete-home-usage"));
    }

    public String getAdminHomeUsageMessage() {
        return Utils.colorize(config.getString("messages.admin-home-usage"));
    }

    public String getAdminSetWarpUsageMessage() {
        return Utils.colorize(config.getString("messages.admin-set-warps-usage"));
    }

    public String getAdminDelWarpUsageMessage() {
        return Utils.colorize(config.getString("messages.admin-delete-warp-usage"));
    }

    public String getAdminWarpUsageMessage() {
        return Utils.colorize(config.getString("messages.admin-warp-usage"));
    }

    public String getAdminInfoUsageMessage() {
        return Utils.colorize(config.getString("messages.admin-info-usage"));
    }

    public String getAdminLoadUsageMessage() {
        return Utils.colorize(config.getString("messages.admin-load-usage"));
    }

    public String getAdminUnloadUsageMessage() {
        return Utils.colorize(config.getString("messages.admin-unload-usage"));
    }

    public String getAdminReloadUsageMessage() {
        return Utils.colorize(config.getString("messages.admin-reload-usage"));
    }

    public String getAdminLockUsageMessage() {
        return Utils.colorize(config.getString("messages.admin-lock-usage"));
    }

    public String getAdminPvpUsageMessage() {
        return Utils.colorize(config.getString("messages.admin-pvp-usage"));
    }

    public String getAdminSetOwnerUsageMessage() {
        return Utils.colorize(config.getString("messages.admin-set-owner-usage"));
    }

    public String getAdminNoIslandMessage(String name) {
        return Utils.colorize(config.getString("messages.admin-no-island").replace("{name}", name));
    }

    public String getAdminAlreadyHasIslandMessage(String name) {
        return Utils.colorize(config.getString("messages.admin-already-has-island").replace("{name}", name));
    }

    public String getAdminAddMemberSuccessMessage(String name, String member) {
        return Utils.colorize(config.getString("messages.admin-add-member-success").replace("{name}", name).replace("{member}", member));
    }

    public String getAdminRemoveMemberSuccessMessage(String name, String member) {
        return Utils.colorize(config.getString("messages.admin-remove-member-success").replace("{name}", name).replace("{member}", member));
    }

    public String getAdminCreateSuccessMessage(String name) {
        return Utils.colorize(config.getString("messages.admin-create-island-success").replace("{name}", name));
    }

    public String getAdminDeleteWarningMessage(String name) {
        return Utils.colorize(config.getString("messages.admin-delete-warning").replace("{name}", name));
    }

    public String getAdminDeleteSuccessMessage(String name) {
        return Utils.colorize(config.getString("messages.admin-delete-island-success").replace("{name}", name));
    }

    public String getAdminCannotDeleteDefaultHomeMessage(String name) {
        return Utils.colorize(config.getString("messages.admin-cannot-delete-default-home").replace("{name}", name));
    }

    public String getAdminNoHomeMessage(String name, String home) {
        return Utils.colorize(config.getString("messages.admin-no-home").replace("{name}", name).replace("{home}", home));
    }

    public String getAdminHomeSuccessMessage(String home) {
        return Utils.colorize(config.getString("messages.home-success").replace("{home}", home));
    }

    public String getAdminDelHomeSuccessMessage(String name, String home) {
        return Utils.colorize(config.getString("messages.admin-delete-home-success").replace("{name}", name).replace("{home}", home));
    }

    public String getAdminNoWarpMessage(String name, String warp) {
        return Utils.colorize(config.getString("messages.admin-no-warp").replace("{name}", name).replace("{warp}", warp));
    }

    public String getAdminDelWarpSuccessMessage(String name, String warp) {
        return Utils.colorize(config.getString("messages.admin-delete-warp-success").replace("{name}", name).replace("{warp}", warp));
    }

    public String getAdminLockSuccessMessage(String name) {
        return Utils.colorize(config.getString("messages.admin-lock-success").replace("{name}", name));
    }

    public String getAdminUnLockSuccessMessage(String name) {
        return Utils.colorize(config.getString("messages.admin-unlock-success").replace("{name}", name));
    }


    public String getAdminMustInIslandSetHomeMessage(String name) {
        return Utils.colorize(config.getString("messages.admin-must-in-island-set-home").replace("{name}", name));
    }

    public String getAdminMustInIslandSetWarpMessage(String name) {
        return Utils.colorize(config.getString("messages.admin-must-in-island-set-warp").replace("{name}", name));
    }

    public String getAdminSetHomeSuccessMessage(String name, String home) {
        return Utils.colorize(config.getString("messages.admin-set-home-success").replace("{name}", name).replace("{home}", home));
    }

    public String getAdminSetWarpSuccessMessage(String name, String warp) {
        return Utils.colorize(config.getString("messages.admin-set-warp-success").replace("{name}", name).replace("{warp}", warp));
    }

    public String getAdminPvpEnableSuccessMessage(String name) {
        return Utils.colorize(config.getString("messages.admin-pvp-enable-success").replace("{name}", name));
    }

    public String getAdminPvpDisableSuccessMessage(String name) {
        return Utils.colorize(config.getString("messages.admin-pvp-disable-success").replace("{name}", name));
    }

    public String getAdminSetOwnerSuccessMessage(String name, String owner) {
        return Utils.colorize(config.getString("messages.admin-set-owner-success").replace("{name}", name).replace("{owner}", owner));
    }

    public String getAdminAlreadyOwnerMessage(String name, String owner) {
        return Utils.colorize(config.getString("messages.admin-already-owner").replace("{name}", name).replace("{owner}", owner));
    }

    public String getPlayerCommandHelpMessage() {
        return Utils.colorize(config.getString("messages.player-command-help"));
    }

    public String getPlayerUnknownSubCommandMessage(String subCommand) {
        return Utils.colorize(config.getString("messages.player-unknown-sub-command").replace("{subCommand}", subCommand));
    }

    public String getPlayerAddMemberUsageMessage() {
        return Utils.colorize(config.getString("messages.player-add-member-usage"));
    }

    public String getPlayerRemoveMemberUsageMessage() {
        return Utils.colorize(config.getString("messages.player-remove-member-usage"));
    }

    public String getPlayerCreateUsageMessage() {
        return Utils.colorize(config.getString("messages.player-create-island-usage"));
    }

    public String getPlayerDeleteUsageMessage() {
        return Utils.colorize(config.getString("messages.player-delete-island-usage"));
    }

    public String getPlayerHomeUsageMessage() {
        return Utils.colorize(config.getString("messages.player-home-usage"));
    }

    public String getPlayerSetHomeUsageMessage() {
        return Utils.colorize(config.getString("messages.player-set-home-usage"));
    }

    public String getPlayerDelHomeUsageMessage() {
        return Utils.colorize(config.getString("messages.player-delete-home-usage"));
    }

    public String getPlayerWarpUsageMessage() {
        return Utils.colorize(config.getString("messages.player-warp-usage"));
    }

    public String getPlayerSetWarpUsageMessage() {
        return Utils.colorize(config.getString("messages.player-set-warp-usage"));
    }

    public String getPlayerDelWarpUsageMessage() {
        return Utils.colorize(config.getString("messages.player-delete-warp-usage"));
    }

    public String getPlayerInfoUsageMessage() {
        return Utils.colorize(config.getString("messages.player-info-usage"));
    }

    public String getPlayerLockUsageMessage() {
        return Utils.colorize(config.getString("messages.player-lock-usage"));
    }

    public String getPlayerPvpUsageMessage() {
        return Utils.colorize(config.getString("messages.player-pvp-usage"));
    }

    public String getPlayerSetOwnerUsageMessage() {
        return Utils.colorize(config.getString("messages.player-set-owner-usage"));
    }

    public String getPlayerLeaveUsageMessage() {
        return Utils.colorize(config.getString("messages.player-leave-usage"));
    }

    public String getPlayerNoIslandMessage() {
        return Utils.colorize(config.getString("messages.player-no-island"));
    }

    public String getPlayerAlreadyHasIslandMessage() {
        return Utils.colorize(config.getString("messages.player-already-has-island"));
    }

    public String getPlayerMustInIslandSetHomeMessage() {
        return Utils.colorize(config.getString("messages.player-must-in-island-set-home"));
    }

    public String getPlayerMustInIslandSetWarpMessage() {
        return Utils.colorize(config.getString("messages.player-must-in-island-set-warp"));
    }

    public String getPlayerAddMemberSuccessMessage(String member) {
        return Utils.colorize(config.getString("messages.player-add-member-success").replace("{member}", member));
    }

    public String getPlayerRemoveMemberSuccessMessage(String member) {
        return Utils.colorize(config.getString("messages.player-remove-member-success").replace("{member}", member));
    }

    public String getPlayerRemoveMemberCannotRemoveSelfMessage() {
        return Utils.colorize(config.getString("messages.player-remove-member-cannot-remove-self"));
    }

    public String getPlayerCreateSuccessMessage() {
        return Utils.colorize(config.getString("messages.player-create-island-success"));
    }

    public String getPlayerDeleteWarningMessage() {
        return Utils.colorize(config.getString("messages.player-delete-warning"));
    }

    public String getPlayerDeleteSuccessMessage() {
        return Utils.colorize(config.getString("messages.player-delete-island-success"));
    }


    public String getPlayerTeleportToIslandSuccessMessage() {
        return Utils.colorize(config.getString("messages.player-teleport-to-island-success"));
    }

    public String getPlayerSetHomeSuccessMessage(String home) {
        return Utils.colorize(config.getString("messages.player-set-home-success").replace("{home}", home));
    }

    public String getPlayerSetWarpSuccessMessage(String warp) {
        return Utils.colorize(config.getString("messages.player-set-warp-success").replace("{warp}", warp));
    }

    public String getPlayerDelHomeSuccessMessage(String home) {
        return Utils.colorize(config.getString("messages.player-delete-home-success").replace("{home}", home));
    }

    public String getPlayerCannotDeleteDefaultHomeMessage() {
        return Utils.colorize(config.getString("messages.player-cannot-delete-default-home"));
    }

    public String getPlayerDelWarpSuccessMessage(String warp) {
        return Utils.colorize(config.getString("messages.player-delete-warp-success").replace("{warp}", warp));
    }

    public String getPlayerPvpEnableSuccessMessage() {
        return Utils.colorize(config.getString("messages.player-pvp-enable-success"));
    }

    public String getPlayerPvpDisableSuccessMessage() {
        return Utils.colorize(config.getString("messages.player-pvp-disable-success"));
    }

    public String getPlayerUnLockSuccessMessage() {
        return Utils.colorize(config.getString("messages.player-unlock-success"));
    }

    public String getPlayerLockSuccessMessage() {
        return Utils.colorize(config.getString("messages.player-lock-success"));
    }

    public String getPlayerNoHomeMessage(String home) {
        return Utils.colorize(config.getString("messages.player-no-home").replace("{home}", home));
    }

    public String getPlayerNoWarpMessage(String warp) {
        return Utils.colorize(config.getString("messages.player-no-warp").replace("{warp}", warp));
    }

    public String getPlayerHomeSuccessMessage(String home) {
        return Utils.colorize(config.getString("messages.player-home-success").replace("{home}", home));
    }

    public String getPlayerNotOwnerMessage() {
        return Utils.colorize(config.getString("messages.player-not-owner"));
    }

    public String getWarpSuccessMessage(String warp) {
        return Utils.colorize(config.getString("messages.warp-success").replace("{warp}", warp));
    }

    public String getNoWarpMessage(String name, String warp) {
        return Utils.colorize(config.getString("messages.no-warp").replace("{name}", name).replace("{warp}", warp));
    }

    public String getIslandLockedMessage() {
        return Utils.colorize(config.getString("messages.island-locked"));
    }


    public String getPlayerSetOwnerSuccessMessage(String name) {
        return Utils.colorize(config.getString("messages.player-set-owner-success").replace("{name}", name));
    }

    public String getPlayerAlreadyOwnerMessage(String name) {
        return Utils.colorize(config.getString("messages.player-already-owner").replace("{name}", name));
    }

    public String getPlayerCannotLeaveAsOwnerMessage() {
        return Utils.colorize(config.getString("messages.player-cannot-leave-as-owner"));
    }

    public String getPlayerLeaveSuccessMessage() {
        return Utils.colorize(config.getString("messages.player-leave-success"));
    }
}
