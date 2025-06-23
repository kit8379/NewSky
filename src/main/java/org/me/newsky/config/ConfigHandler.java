package org.me.newsky.config;

import net.kyori.adventure.text.Component;
import org.me.newsky.NewSky;
import org.me.newsky.util.ColorUtils;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.loader.ConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class ConfigHandler {

    private final NewSky plugin;

    private CommentedConfigurationNode config;
    private CommentedConfigurationNode messages;
    private CommentedConfigurationNode commands;
    private CommentedConfigurationNode levels;

    public ConfigHandler(NewSky plugin) {
        this.plugin = plugin;
        loadConfigs();
    }

    private void loadConfigs() {
        this.config = loadAndMerge("config.yml");
        this.messages = loadAndMerge("messages.yml");
        this.commands = loadAndMerge("commands.yml");
        this.levels = loadAndMerge("levels.yml");
    }

    private CommentedConfigurationNode loadAndMerge(String fileName) {
        try {
            File file = new File(plugin.getDataFolder(), fileName);
            if (!file.exists()) {
                plugin.saveResource(fileName, false);
            }

            // Loader for the user's config file
            ConfigurationLoader<@org.jetbrains.annotations.NotNull CommentedConfigurationNode> userLoader = YamlConfigurationLoader.builder().path(file.toPath()).build();

            // Load user node
            CommentedConfigurationNode userNode = userLoader.load();

            // Load default node from plugin jar resource
            InputStream in = plugin.getResource(fileName);
            if (in == null) {
                plugin.severe("Default config missing in jar: " + fileName);
                return userNode;
            }

            ConfigurationLoader<@org.jetbrains.annotations.NotNull CommentedConfigurationNode> defaultLoader = YamlConfigurationLoader.builder().source(() -> new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))).build();

            CommentedConfigurationNode defaultNode = defaultLoader.load();

            // Overlay user values onto default node (preserves default structure + comments + order)
            mergeNodes(defaultNode, userNode);

            // Save merged config
            userLoader.save(defaultNode);

            return defaultNode;

        } catch (IOException e) {
            plugin.severe("Failed to load or save config: " + fileName, e);
            return null;
        }
    }

    private void mergeNodes(CommentedConfigurationNode target, CommentedConfigurationNode source) {
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : source.childrenMap().entrySet()) {
            Object key = entry.getKey();
            ConfigurationNode sourceChild = entry.getValue();
            CommentedConfigurationNode targetChild = target.node(key);

            if (sourceChild.isMap()) {
                mergeNodes(targetChild, (CommentedConfigurationNode) sourceChild);
            } else {
                try {
                    targetChild.set(sourceChild.raw());
                } catch (SerializationException e) {
                    plugin.severe("Failed to set config value for key: " + key, e);
                }
            }
        }
    }

    // ================================================================================================================
    // Config Section
    // ================================================================================================================

    public String getMySQLHost() {
        return config.node("MySQL", "host").getString();
    }

    public int getMySQLPort() {
        return config.node("MySQL", "port").getInt();
    }

    public String getMySQLDB() {
        return config.node("MySQL", "database").getString();
    }

    public String getMySQLUsername() {
        return config.node("MySQL", "username").getString();
    }

    public String getMySQLPassword() {
        return config.node("MySQL", "password").getString();
    }

    public String getMySQLProperties() {
        return config.node("MySQL", "properties").getString();
    }

    public int getMySQLMaxPoolSize() {
        return config.node("MySQL", "max-pool-size").getInt();
    }

    public int getMySQLConnectionTimeout() {
        return config.node("MySQL", "connection-timeout").getInt();
    }

    public boolean getMySQLCachePrepStmts() {
        return config.node("MySQL", "cache-prep-statements").getBoolean();
    }

    public int getMySQLPrepStmtCacheSize() {
        return config.node("MySQL", "prep-stmt-cache-size").getInt();
    }

    public int getMySQLPrepStmtCacheSqlLimit() {
        return config.node("MySQL", "prep-stmt-cache-sql-limit").getInt();
    }

    public String getRedisHost() {
        return config.node("redis", "host").getString();
    }

    public int getRedisPort() {
        return config.node("redis", "port").getInt();
    }

    public String getRedisPassword() {
        return config.node("redis", "password").getString();
    }

    public int getRedisDatabase() {
        return config.node("redis", "database").getInt();
    }

    public String getRedisCacheChannel() {
        return config.node("redis", "channel", "cache").getString();
    }

    public String getRedisIslandChannel() {
        return config.node("redis", "channel", "island").getString();
    }

    public boolean isDebug() {
        return config.node("debug").getBoolean();
    }

    public String getServerName() {
        return config.node("server", "name").getString();
    }

    public boolean isLobby() {
        return config.node("server", "lobby").getBoolean();
    }

    public int getHeartbeatInterval() {
        return config.node("server", "heartbeat-interval").getInt();
    }

    public String getServerSelector() {
        return config.node("server", "selector").getString();
    }

    public int getMsptUpdateInterval() {
        return config.node("server", "mspt-update-interval").getInt();
    }

    public List<String> getLobbyServerNames() {
        try {
            return config.node("lobby", "server-names").getList(String.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load lobby server-names list", e);
            return java.util.Collections.emptyList();
        }
    }

    public String getLobbyWorldName() {
        return config.node("lobby", "world-name").getString();
    }

    public double getLobbyX() {
        return config.node("lobby", "location", "x").getDouble();
    }

    public double getLobbyY() {
        return config.node("lobby", "location", "y").getDouble();
    }

    public double getLobbyZ() {
        return config.node("lobby", "location", "z").getDouble();
    }

    public float getLobbyYaw() {
        return (float) config.node("lobby", "location", "yaw").getDouble();
    }

    public float getLobbyPitch() {
        return (float) config.node("lobby", "location", "pitch").getDouble();
    }

    public String getTemplateWorldName() {
        return config.node("island", "template").getString();
    }

    public int getIslandSize() {
        return config.node("island", "size").getInt();
    }

    public int getIslandSpawnX() {
        return config.node("island", "spawn", "x").getInt();
    }

    public int getIslandSpawnY() {
        return config.node("island", "spawn", "y").getInt();
    }

    public int getIslandSpawnZ() {
        return config.node("island", "spawn", "z").getInt();
    }

    public float getIslandSpawnYaw() {
        return (float) config.node("island", "spawn", "yaw").getDouble();
    }

    public float getIslandSpawnPitch() {
        return (float) config.node("island", "spawn", "pitch").getDouble();
    }

    public int getIslandUnloadInterval() {
        return config.node("island", "island-unload-interval").getInt();
    }

    public int getIslandLevelUpdateInterval() {
        return config.node("island", "level-update-interval").getInt();
    }

    public Map<String, Object> getIslandGameRules() {
        try {
            return config.node("island", "gamerules").childrenMap().entrySet().stream().map(e -> Map.entry(String.valueOf(e.getKey()), Objects.requireNonNull(e.getValue().raw()))).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } catch (Exception e) {
            plugin.severe("Failed to load island gamerules", e);
            return java.util.Collections.emptyMap();
        }
    }

    public String getBaseCommandMode() {
        return config.node("command", "base-command-mode").getString();
    }

    public int getBlockLevel(String material) {
        return levels.node("blocks", material).getInt(0);
    }

    // ================================================================================================================
    // Commands Section
    // ================================================================================================================

    public List<String> getPlayerCommandOrder() {
        try {
            return commands.node("commands", "player").childrenMap().keySet().stream().map(Object::toString).toList();
        } catch (Exception e) {
            plugin.severe("Failed to load player command order", e);
            return java.util.Collections.emptyList();
        }
    }

    public String[] getPlayerInviteAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "player", "invite", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player invite aliases", e);
            return new String[0];
        }
    }

    public String getPlayerInvitePermission() {
        return commands.node("commands", "player", "invite", "permission").getString();
    }

    public String getPlayerInviteSyntax() {
        return commands.node("commands", "player", "invite", "syntax").getString();
    }

    public String getPlayerInviteDescription() {
        return commands.node("commands", "player", "invite", "description").getString();
    }

    public String[] getPlayerAcceptAliases() {
        try {
            return Objects.requireNonNull(Objects.requireNonNull(commands.node("commands", "player", "accept", "aliases").getList(String.class))).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player accept aliases", e);
            return new String[0];
        }
    }

    public String getPlayerAcceptPermission() {
        return commands.node("commands", "player", "accept", "permission").getString();
    }

    public String getPlayerAcceptSyntax() {
        return commands.node("commands", "player", "accept", "syntax").getString();
    }

    public String getPlayerAcceptDescription() {
        return commands.node("commands", "player", "accept", "description").getString();
    }

    public String[] getPlayerRejectAliases() {
        try {
            return Objects.requireNonNull(Objects.requireNonNull(commands.node("commands", "player", "reject", "aliases").getList(String.class))).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player reject aliases", e);
            return new String[0];
        }
    }

    public String getPlayerRejectPermission() {
        return commands.node("commands", "player", "reject", "permission").getString();
    }

    public String getPlayerRejectSyntax() {
        return commands.node("commands", "player", "reject", "syntax").getString();
    }

    public String getPlayerRejectDescription() {
        return commands.node("commands", "player", "reject", "description").getString();
    }

    public String[] getPlayerBanAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "player", "ban", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player ban aliases", e);
            return new String[0];
        }
    }

    public String getPlayerBanPermission() {
        return commands.node("commands", "player", "ban", "permission").getString();
    }

    public String getPlayerBanSyntax() {
        return commands.node("commands", "player", "ban", "syntax").getString();
    }

    public String getPlayerBanDescription() {
        return commands.node("commands", "player", "ban", "description").getString();
    }

    public String[] getPlayerBanListAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "player", "banlist", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player banlist aliases", e);
            return new String[0];
        }
    }

    public String getPlayerBanListPermission() {
        return commands.node("commands", "player", "banlist", "permission").getString();
    }

    public String getPlayerBanListSyntax() {
        return commands.node("commands", "player", "banlist", "syntax").getString();
    }

    public String getPlayerBanListDescription() {
        return commands.node("commands", "player", "banlist", "description").getString();
    }


    public String[] getPlayerCoopAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "player", "coop", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player coop aliases", e);
            return new String[0];
        }
    }

    public String getPlayerCoopPermission() {
        return commands.node("commands", "player", "coop", "permission").getString();
    }

    public String getPlayerCoopSyntax() {
        return commands.node("commands", "player", "coop", "syntax").getString();
    }

    public String getPlayerCoopDescription() {
        return commands.node("commands", "player", "coop", "description").getString();
    }

    public String[] getPlayerCoopListAliases() {
        try {
            return Objects.requireNonNull(Objects.requireNonNull(Objects.requireNonNull(Objects.requireNonNull(Objects.requireNonNull(Objects.requireNonNull(Objects.requireNonNull(Objects.requireNonNull(Objects.requireNonNull(commands.node("commands", "player", "cooplist", "aliases").getList(String.class)))))))))).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player cooplist aliases", e);
            return new String[0];
        }
    }

    public String getPlayerCoopListPermission() {
        return commands.node("commands", "player", "cooplist", "permission").getString();
    }

    public String getPlayerCoopListSyntax() {
        return commands.node("commands", "player", "cooplist", "syntax").getString();
    }

    public String getPlayerCoopListDescription() {
        return commands.node("commands", "player", "cooplist", "description").getString();
    }

    public String[] getPlayerCreateAliases() {
        try {
            return Objects.requireNonNull(Objects.requireNonNull(commands.node("commands", "player", "create", "aliases").getList(String.class))).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player create aliases", e);
            return new String[0];
        }
    }

    public String getPlayerCreatePermission() {
        return commands.node("commands", "player", "create", "permission").getString();
    }

    public String getPlayerCreateSyntax() {
        return commands.node("commands", "player", "create", "syntax").getString();
    }

    public String getPlayerCreateDescription() {
        return commands.node("commands", "player", "create", "description").getString();
    }

    public String[] getPlayerDeleteAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "player", "delete", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player delete aliases", e);
            return new String[0];
        }
    }

    public String getPlayerDeletePermission() {
        return commands.node("commands", "player", "delete", "permission").getString();
    }

    public String getPlayerDeleteSyntax() {
        return commands.node("commands", "player", "delete", "syntax").getString();
    }

    public String getPlayerDeleteDescription() {
        return commands.node("commands", "player", "delete", "description").getString();
    }

    public String[] getPlayerDelHomeAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "player", "delhome", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player delhome aliases", e);
            return new String[0];
        }
    }

    public String getPlayerDelHomePermission() {
        return commands.node("commands", "player", "delhome", "permission").getString();
    }

    public String getPlayerDelHomeSyntax() {
        return commands.node("commands", "player", "delhome", "syntax").getString();
    }

    public String getPlayerDelHomeDescription() {
        return commands.node("commands", "player", "delhome", "description").getString();
    }

    public String[] getPlayerDelWarpAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "player", "delwarp", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player delwarp aliases", e);
            return new String[0];
        }
    }

    public String getPlayerDelWarpPermission() {
        return commands.node("commands", "player", "delwarp", "permission").getString();
    }

    public String getPlayerDelWarpSyntax() {
        return commands.node("commands", "player", "delwarp", "syntax").getString();
    }

    public String getPlayerDelWarpDescription() {
        return commands.node("commands", "player", "delwarp", "description").getString();
    }

    public String[] getPlayerExpelAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "player", "expel", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player expel aliases", e);
            return new String[0];
        }
    }

    public String getPlayerExpelPermission() {
        return commands.node("commands", "player", "expel", "permission").getString();
    }

    public String getPlayerExpelSyntax() {
        return commands.node("commands", "player", "expel", "syntax").getString();
    }

    public String getPlayerExpelDescription() {
        return commands.node("commands", "player", "expel", "description").getString();
    }

    public String[] getPlayerHelpAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "player", "help", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player help aliases", e);
            return new String[0];
        }
    }

    public String getPlayerHelpPermission() {
        return commands.node("commands", "player", "help", "permission").getString();
    }

    public String getPlayerHelpSyntax() {
        return commands.node("commands", "player", "help", "syntax").getString();
    }

    public String getPlayerHelpDescription() {
        return commands.node("commands", "player", "help", "description").getString();
    }

    public String[] getPlayerHomeAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "player", "home", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player home aliases", e);
            return new String[0];
        }
    }

    public String getPlayerHomePermission() {
        return commands.node("commands", "player", "home", "permission").getString();
    }

    public String getPlayerHomeSyntax() {
        return commands.node("commands", "player", "home", "syntax").getString();
    }

    public String getPlayerHomeDescription() {
        return commands.node("commands", "player", "home", "description").getString();
    }

    public String[] getPlayerInfoAliases() {
        try {
            return Objects.requireNonNull(Objects.requireNonNull(commands.node("commands", "player", "info", "aliases").getList(String.class))).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player info aliases", e);
            return new String[0];
        }
    }

    public String getPlayerInfoPermission() {
        return commands.node("commands", "player", "info", "permission").getString();
    }

    public String getPlayerInfoSyntax() {
        return commands.node("commands", "player", "info", "syntax").getString();
    }

    public String getPlayerInfoDescription() {
        return commands.node("commands", "player", "info", "description").getString();
    }

    public String[] getPlayerLeaveAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "player", "leave", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player leave aliases", e);
            return new String[0];
        }
    }

    public String getPlayerLeavePermission() {
        return commands.node("commands", "player", "leave", "permission").getString();
    }

    public String getPlayerLeaveSyntax() {
        return commands.node("commands", "player", "leave", "syntax").getString();
    }

    public String getPlayerLeaveDescription() {
        return commands.node("commands", "player", "leave", "description").getString();
    }

    public String[] getPlayerLevelAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "player", "level", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player level aliases", e);
            return new String[0];
        }
    }

    public String getPlayerLevelPermission() {
        return commands.node("commands", "player", "level", "permission").getString();
    }

    public String getPlayerLevelSyntax() {
        return commands.node("commands", "player", "level", "syntax").getString();
    }

    public String getPlayerLevelDescription() {
        return commands.node("commands", "player", "level", "description").getString();
    }

    public String[] getPlayerLockAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "player", "lock", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player lock aliases", e);
            return new String[0];
        }
    }

    public String getPlayerLockPermission() {
        return commands.node("commands", "player", "lock", "permission").getString();
    }

    public String getPlayerLockSyntax() {
        return commands.node("commands", "player", "lock", "syntax").getString();
    }

    public String getPlayerLockDescription() {
        return commands.node("commands", "player", "lock", "description").getString();
    }

    public String[] getPlayerPvpAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "player", "pvp", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player pvp aliases", e);
            return new String[0];
        }
    }

    public String getPlayerPvpPermission() {
        return commands.node("commands", "player", "pvp", "permission").getString();
    }

    public String getPlayerPvpSyntax() {
        return commands.node("commands", "player", "pvp", "syntax").getString();
    }

    public String getPlayerPvpDescription() {
        return commands.node("commands", "player", "pvp", "description").getString();
    }

    public String[] getPlayerRemoveMemberAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "player", "removemember", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player removemember aliases", e);
            return new String[0];
        }
    }

    public String getPlayerRemoveMemberPermission() {
        return commands.node("commands", "player", "removemember", "permission").getString();
    }

    public String getPlayerRemoveMemberSyntax() {
        return commands.node("commands", "player", "removemember", "syntax").getString();
    }

    public String getPlayerRemoveMemberDescription() {
        return commands.node("commands", "player", "removemember", "description").getString();
    }

    public String[] getPlayerSetHomeAliases() {
        try {
            return Objects.requireNonNull(Objects.requireNonNull(commands.node("commands", "player", "sethome", "aliases").getList(String.class))).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player sethome aliases", e);
            return new String[0];
        }
    }

    public String getPlayerSetHomePermission() {
        return commands.node("commands", "player", "sethome", "permission").getString();
    }

    public String getPlayerSetHomeSyntax() {
        return commands.node("commands", "player", "sethome", "syntax").getString();
    }

    public String getPlayerSetHomeDescription() {
        return commands.node("commands", "player", "sethome", "description").getString();
    }

    public String[] getPlayerSetOwnerAliases() {
        try {
            return Objects.requireNonNull(Objects.requireNonNull(commands.node("commands", "player", "setowner", "aliases").getList(String.class))).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player setowner aliases", e);
            return new String[0];
        }
    }

    public String getPlayerSetOwnerPermission() {
        return commands.node("commands", "player", "setowner", "permission").getString();
    }

    public String getPlayerSetOwnerSyntax() {
        return commands.node("commands", "player", "setowner", "syntax").getString();
    }

    public String getPlayerSetOwnerDescription() {
        return commands.node("commands", "player", "setowner", "description").getString();
    }

    public String[] getPlayerSetWarpAliases() {
        try {
            return Objects.requireNonNull(Objects.requireNonNull(Objects.requireNonNull(commands.node("commands", "player", "setwarp", "aliases").getList(String.class)))).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player setwarp aliases", e);
            return new String[0];
        }
    }

    public String getPlayerSetWarpPermission() {
        return commands.node("commands", "player", "setwarp", "permission").getString();
    }

    public String getPlayerSetWarpSyntax() {
        return commands.node("commands", "player", "setwarp", "syntax").getString();
    }

    public String getPlayerSetWarpDescription() {
        return commands.node("commands", "player", "setwarp", "description").getString();
    }

    public String[] getPlayerTopAliases() {
        try {
            return Objects.requireNonNull(Objects.requireNonNull(commands.node("commands", "player", "top", "aliases").getList(String.class))).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player top aliases", e);
            return new String[0];
        }
    }

    public String getPlayerTopPermission() {
        return commands.node("commands", "player", "top", "permission").getString();
    }

    public String getPlayerTopSyntax() {
        return commands.node("commands", "player", "top", "syntax").getString();
    }

    public String getPlayerTopDescription() {
        return commands.node("commands", "player", "top", "description").getString();
    }

    public String[] getPlayerUnbanAliases() {
        try {
            return Objects.requireNonNull(Objects.requireNonNull(commands.node("commands", "player", "unban", "aliases").getList(String.class))).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player unban aliases", e);
            return new String[0];
        }
    }

    public String getPlayerUnbanPermission() {
        return commands.node("commands", "player", "unban", "permission").getString();
    }

    public String getPlayerUnbanSyntax() {
        return commands.node("commands", "player", "unban", "syntax").getString();
    }

    public String getPlayerUnbanDescription() {
        return commands.node("commands", "player", "unban", "description").getString();
    }

    public String[] getPlayerUncoopAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "player", "uncoop", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player uncoop aliases", e);
            return new String[0];
        }
    }

    public String getPlayerUncoopPermission() {
        return commands.node("commands", "player", "uncoop", "permission").getString();
    }

    public String getPlayerUncoopSyntax() {
        return commands.node("commands", "player", "uncoop", "syntax").getString();
    }

    public String getPlayerUncoopDescription() {
        return commands.node("commands", "player", "uncoop", "description").getString();
    }

    public String[] getPlayerValueAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "player", "value", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player value aliases", e);
            return new String[0];
        }
    }

    public String getPlayerValuePermission() {
        return commands.node("commands", "player", "value", "permission").getString();
    }

    public String getPlayerValueSyntax() {
        return commands.node("commands", "player", "value", "syntax").getString();
    }

    public String getPlayerValueDescription() {
        return commands.node("commands", "player", "value", "description").getString();
    }

    public String[] getPlayerWarpAliases() {
        try {
            return Objects.requireNonNull(Objects.requireNonNull(commands.node("commands", "player", "warp", "aliases").getList(String.class))).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player warp aliases", e);
            return new String[0];
        }
    }

    public String getPlayerWarpPermission() {
        return commands.node("commands", "player", "warp", "permission").getString();
    }

    public String getPlayerWarpSyntax() {
        return commands.node("commands", "player", "warp", "syntax").getString();
    }

    public String getPlayerWarpDescription() {
        return commands.node("commands", "player", "warp", "description").getString();
    }

    public String[] getPlayerLobbyAliases() {
        try {
            return Objects.requireNonNull(Objects.requireNonNull(commands.node("commands", "player", "lobby", "aliases").getList(String.class))).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load player lobby aliases", e);
            return new String[0];
        }
    }

    public String getPlayerLobbyPermission() {
        return commands.node("commands", "player", "lobby", "permission").getString();
    }

    public String getPlayerLobbySyntax() {
        return commands.node("commands", "player", "lobby", "syntax").getString();
    }

    public String getPlayerLobbyDescription() {
        return commands.node("commands", "player", "lobby", "description").getString();
    }

    public List<String> getAdminCommandOrder() {
        try {
            return commands.node("commands", "admin").childrenMap().keySet().stream().map(Object::toString).toList();
        } catch (Exception e) {
            plugin.severe("Failed to load admin command order", e);
            return java.util.Collections.emptyList();
        }
    }

    public String[] getAdminAddMemberAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "admin", "addmember", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load admin addmember aliases", e);
            return new String[0];
        }
    }

    public String getAdminAddMemberPermission() {
        return commands.node("commands", "admin", "addmember", "permission").getString();
    }

    public String getAdminAddMemberSyntax() {
        return commands.node("commands", "admin", "addmember", "syntax").getString();
    }

    public String getAdminAddMemberDescription() {
        return commands.node("commands", "admin", "addmember", "description").getString();
    }

    public String[] getAdminBanAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "admin", "ban", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load admin ban aliases", e);
            return new String[0];
        }
    }

    public String getAdminBanPermission() {
        return commands.node("commands", "admin", "ban", "permission").getString();
    }

    public String getAdminBanSyntax() {
        return commands.node("commands", "admin", "ban", "syntax").getString();
    }

    public String getAdminBanDescription() {
        return commands.node("commands", "admin", "ban", "description").getString();
    }

    public String[] getAdminCoopAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "admin", "coop", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load admin coop aliases", e);
            return new String[0];
        }
    }

    public String getAdminCoopPermission() {
        return commands.node("commands", "admin", "coop", "permission").getString();
    }

    public String getAdminCoopSyntax() {
        return commands.node("commands", "admin", "coop", "syntax").getString();
    }

    public String getAdminCoopDescription() {
        return commands.node("commands", "admin", "coop", "description").getString();
    }

    public String[] getAdminCreateAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "admin", "create", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load admin create aliases", e);
            return new String[0];
        }
    }

    public String getAdminCreatePermission() {
        return commands.node("commands", "admin", "create", "permission").getString();
    }

    public String getAdminCreateSyntax() {
        return commands.node("commands", "admin", "create", "syntax").getString();
    }

    public String getAdminCreateDescription() {
        return commands.node("commands", "admin", "create", "description").getString();
    }

    public String[] getAdminDeleteAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "admin", "delete", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load admin delete aliases", e);
            return new String[0];
        }
    }

    public String getAdminDeletePermission() {
        return commands.node("commands", "admin", "delete", "permission").getString();
    }

    public String getAdminDeleteSyntax() {
        return commands.node("commands", "admin", "delete", "syntax").getString();
    }

    public String getAdminDeleteDescription() {
        return commands.node("commands", "admin", "delete", "description").getString();
    }

    public String[] getAdminDelHomeAliases() {
        try {
            return Objects.requireNonNull(Objects.requireNonNull(commands.node("commands", "admin", "delhome", "aliases").getList(String.class))).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load admin delhome aliases", e);
            return new String[0];
        }
    }

    public String getAdminDelHomePermission() {
        return commands.node("commands", "admin", "delhome", "permission").getString();
    }

    public String getAdminDelHomeSyntax() {
        return commands.node("commands", "admin", "delhome", "syntax").getString();
    }

    public String getAdminDelHomeDescription() {
        return commands.node("commands", "admin", "delhome", "description").getString();
    }

    public String[] getAdminDelWarpAliases() {
        try {
            return Objects.requireNonNull(Objects.requireNonNull(commands.node("commands", "admin", "delwarp", "aliases").getList(String.class))).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load admin delwarp aliases", e);
            return new String[0];
        }
    }

    public String getAdminDelWarpPermission() {
        return commands.node("commands", "admin", "delwarp", "permission").getString();
    }

    public String getAdminDelWarpSyntax() {
        return commands.node("commands", "admin", "delwarp", "syntax").getString();
    }

    public String getAdminDelWarpDescription() {
        return commands.node("commands", "admin", "delwarp", "description").getString();
    }

    public String[] getAdminHelpAliases() {
        try {
            return Objects.requireNonNull(Objects.requireNonNull(Objects.requireNonNull(commands.node("commands", "admin", "help", "aliases").getList(String.class)))).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load admin help aliases", e);
            return new String[0];
        }
    }

    public String getAdminHelpPermission() {
        return commands.node("commands", "admin", "help", "permission").getString();
    }

    public String getAdminHelpSyntax() {
        return commands.node("commands", "admin", "help", "syntax").getString();
    }

    public String getAdminHelpDescription() {
        return commands.node("commands", "admin", "help", "description").getString();
    }

    public String[] getAdminHomeAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "admin", "home", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load admin home aliases", e);
            return new String[0];
        }
    }

    public String getAdminHomePermission() {
        return commands.node("commands", "admin", "home", "permission").getString();
    }

    public String getAdminHomeSyntax() {
        return commands.node("commands", "admin", "home", "syntax").getString();
    }

    public String getAdminHomeDescription() {
        return commands.node("commands", "admin", "home", "description").getString();
    }

    public String[] getAdminLoadAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "admin", "load", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load admin load aliases", e);
            return new String[0];
        }
    }

    public String getAdminLoadPermission() {
        return commands.node("commands", "admin", "load", "permission").getString();
    }

    public String getAdminLoadSyntax() {
        return commands.node("commands", "admin", "load", "syntax").getString();
    }

    public String getAdminLoadDescription() {
        return commands.node("commands", "admin", "load", "description").getString();
    }

    public String[] getAdminLockAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "admin", "lock", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load admin lock aliases", e);
            return new String[0];
        }
    }

    public String getAdminLockPermission() {
        return commands.node("commands", "admin", "lock", "permission").getString();
    }

    public String getAdminLockSyntax() {
        return commands.node("commands", "admin", "lock", "syntax").getString();
    }

    public String getAdminLockDescription() {
        return commands.node("commands", "admin", "lock", "description").getString();
    }

    public String[] getAdminPvpAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "admin", "pvp", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load admin pvp aliases", e);
            return new String[0];
        }
    }

    public String getAdminPvpPermission() {
        return commands.node("commands", "admin", "pvp", "permission").getString();
    }

    public String getAdminPvpSyntax() {
        return commands.node("commands", "admin", "pvp", "syntax").getString();
    }

    public String getAdminPvpDescription() {
        return commands.node("commands", "admin", "pvp", "description").getString();
    }

    public String[] getAdminReloadAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "admin", "reload", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load admin reload aliases", e);
            return new String[0];
        }
    }

    public String getAdminReloadPermission() {
        return commands.node("commands", "admin", "reload", "permission").getString();
    }

    public String getAdminReloadSyntax() {
        return commands.node("commands", "admin", "reload", "syntax").getString();
    }

    public String getAdminReloadDescription() {
        return commands.node("commands", "admin", "reload", "description").getString();
    }

    public String[] getAdminRemoveMemberAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "admin", "removemember", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load admin removemember aliases", e);
            return new String[0];
        }
    }

    public String getAdminRemoveMemberPermission() {
        return commands.node("commands", "admin", "removemember", "permission").getString();
    }

    public String getAdminRemoveMemberSyntax() {
        return commands.node("commands", "admin", "removemember", "syntax").getString();
    }

    public String getAdminRemoveMemberDescription() {
        return commands.node("commands", "admin", "removemember", "description").getString();
    }

    public String[] getAdminSetHomeAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "admin", "sethome", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load admin sethome aliases", e);
            return new String[0];
        }
    }

    public String getAdminSetHomePermission() {
        return commands.node("commands", "admin", "sethome", "permission").getString();
    }

    public String getAdminSetHomeSyntax() {
        return commands.node("commands", "admin", "sethome", "syntax").getString();
    }

    public String getAdminSetHomeDescription() {
        return commands.node("commands", "admin", "sethome", "description").getString();
    }

    public String[] getAdminSetWarpAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "admin", "setwarp", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load admin setwarp aliases", e);
            return new String[0];
        }
    }

    public String getAdminSetWarpPermission() {
        return commands.node("commands", "admin", "setwarp", "permission").getString();
    }

    public String getAdminSetWarpSyntax() {
        return commands.node("commands", "admin", "setwarp", "syntax").getString();
    }

    public String getAdminSetWarpDescription() {
        return commands.node("commands", "admin", "setwarp", "description").getString();
    }

    public String[] getAdminUnbanAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "admin", "unban", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load admin unban aliases", e);
            return new String[0];
        }
    }

    public String getAdminUnbanPermission() {
        return commands.node("commands", "admin", "unban", "permission").getString();
    }

    public String getAdminUnbanSyntax() {
        return commands.node("commands", "admin", "unban", "syntax").getString();
    }

    public String getAdminUnbanDescription() {
        return commands.node("commands", "admin", "unban", "description").getString();
    }

    public String[] getAdminUncoopAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "admin", "uncoop", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load admin uncoop aliases", e);
            return new String[0];
        }
    }

    public String getAdminUncoopPermission() {
        return commands.node("commands", "admin", "uncoop", "permission").getString();
    }

    public String getAdminUncoopSyntax() {
        return commands.node("commands", "admin", "uncoop", "syntax").getString();
    }

    public String getAdminUncoopDescription() {
        return commands.node("commands", "admin", "uncoop", "description").getString();
    }

    public String[] getAdminUnloadAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "admin", "unload", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load admin unload aliases", e);
            return new String[0];
        }
    }

    public String getAdminUnloadPermission() {
        return commands.node("commands", "admin", "unload", "permission").getString();
    }

    public String getAdminUnloadSyntax() {
        return commands.node("commands", "admin", "unload", "syntax").getString();
    }

    public String getAdminUnloadDescription() {
        return commands.node("commands", "admin", "unload", "description").getString();
    }

    public String[] getAdminWarpAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "admin", "warp", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load admin warp aliases", e);
            return new String[0];
        }
    }

    public String getAdminWarpPermission() {
        return commands.node("commands", "admin", "warp", "permission").getString();
    }

    public String getAdminWarpSyntax() {
        return commands.node("commands", "admin", "warp", "syntax").getString();
    }

    public String getAdminWarpDescription() {
        return commands.node("commands", "admin", "warp", "description").getString();
    }

    public String[] getAdminLobbyAliases() {
        try {
            return Objects.requireNonNull(commands.node("commands", "admin", "lobby", "aliases").getList(String.class)).toArray(new String[0]);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            plugin.severe("Failed to load admin lobby aliases", e);
            return new String[0];
        }
    }

    public String getAdminLobbyPermission() {
        return commands.node("commands", "admin", "lobby", "permission").getString();
    }

    public String getAdminLobbySyntax() {
        return commands.node("commands", "admin", "lobby", "syntax").getString();
    }

    public String getAdminLobbyDescription() {
        return commands.node("commands", "admin", "lobby", "description").getString();
    }

    // =========================================================
    // Messages Section
    // =========================================================

    // General Messages
    public Component getPluginReloadedMessage() {
        return ColorUtils.colorize(messages.node("messages", "plugin-reloaded").getString());
    }

    public Component getOnlyPlayerCanRunCommandMessage() {
        return ColorUtils.colorize(messages.node("messages", "only-player-can-run-command").getString());
    }

    public Component getUnknownSubCommandMessage() {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "unknown-sub-command").getString()));
    }

    public Component getNoPermissionCommandMessage() {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "no-permission-command").getString()));
    }

    public Component getCommandUsageMessage(String command, String syntax) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "usage-command").getString()).replace("{command}", command).replace("{syntax}", syntax));
    }

    public Component getNoActiveServerMessage() {
        return ColorUtils.colorize(messages.node("messages", "no-active-server").getString());
    }

    public Component getIslandNotLoadedMessage() {
        return ColorUtils.colorize(messages.node("messages", "island-not-loaded").getString());
    }

    public Component getIslandAlreadyLoadedMessage() {
        return ColorUtils.colorize(messages.node("messages", "island-already-loaded").getString());
    }

    public Component getIslandLevelMessage(int level) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "island-level").getString()).replace("{level}", String.valueOf(level)));
    }

    public Component getIslandLoadSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "island-load-success").getString()).replace("{player}", player));
    }

    public Component getIslandUnloadSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "island-unload-success").getString()).replace("{player}", player));
    }

    public Component getIslandLockedMessage() {
        return ColorUtils.colorize(messages.node("messages", "island-locked").getString());
    }

    public Component getPlayerBannedMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-banned").getString());
    }

    public Component getIslandPvpDisabledMessage() {
        return ColorUtils.colorize(messages.node("messages", "island-pvp-disabled").getString());
    }

    public Component getIslandMemberExistsMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "island-member-exists").getString()).replace("{player}", player));
    }

    public Component getIslandMemberNotExistsMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "not-island-member").getString()).replace("{player}", player));
    }

    public Component getCannotEditIslandMessage() {
        return ColorUtils.colorize(messages.node("messages", "cannot-edit-island").getString());
    }

    public Component getWarpSuccessMessage(String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "warp-success").getString()).replace("{warp}", warp));
    }

    public Component getNoWarpMessage(String player, String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "no-warp").getString()).replace("{player}", player).replace("{warp}", warp));
    }

    public Component getNoIslandMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "no-island").getString()).replace("{player}", player));
    }

    public Component getAlreadyHasIslandMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "already-has-island").getString()).replace("{player}", player));
    }

    public Component getWasAddedToIslandMessage(String owner) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "was-added-to-island").getString()).replace("{owner}", owner));
    }

    public Component getWasRemovedFromIslandMessage(String owner) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "was-removed-from-island").getString()).replace("{owner}", owner));
    }

    public Component getWasBannedFromIslandMessage(String owner) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "was-banned-from-island").getString()).replace("{owner}", owner));
    }

    public Component getWasUnbannedFromIslandMessage(String owner) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "was-unbanned-from-island").getString()).replace("{owner}", owner));
    }

    public Component getWasCoopedToIslandMessage(String owner) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "was-cooped-from-island").getString()).replace("{owner}", owner));
    }

    public Component getWasUncoopedFromIslandMessage(String owner) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "was-uncooped-from-island").getString()).replace("{owner}", owner));
    }

    public Component getNewMemberNotificationMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "new-member-notification").getString()).replace("{player}", player));
    }

    public Component getCannotRemoveOwnerMessage() {
        return ColorUtils.colorize(messages.node("messages", "cannot-remove-owner").getString());
    }

    public Component getUnknownExceptionMessage() {
        return ColorUtils.colorize(messages.node("messages", "unknown-exception").getString());
    }

    // Admin Command Messages
    public Component getAdminNoIslandMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-no-island").getString()).replace("{player}", player));
    }

    public Component getAdminAddMemberSuccessMessage(String target, String owner) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-add-member-success").getString()).replace("{target}", target).replace("{owner}", owner));
    }

    public Component getAdminRemoveMemberSuccessMessage(String target, String owner) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-remove-member-success").getString()).replace("{target}", target).replace("{owner}", owner));
    }

    public Component getAdminCreateSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-create-island-success").getString()).replace("{player}", player));
    }

    public Component getAdminDeleteWarningMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-delete-warning").getString()).replace("{player}", player));
    }

    public Component getAdminDeleteSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-delete-island-success").getString()).replace("{player}", player));
    }

    public Component getAdminCannotDeleteDefaultHomeMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-cannot-delete-default-home").getString()).replace("{player}", player));
    }

    public Component getAdminNoHomeMessage(String player, String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-no-home").getString()).replace("{player}", player).replace("{home}", home));
    }

    public Component getAdminHomeSuccessMessage(String player, String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-home-success").getString()).replace("{player}", player).replace("{home}", home));
    }

    public Component getAdminDelHomeSuccessMessage(String player, String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-delete-home-success").getString()).replace("{player}", player).replace("{home}", home));
    }

    public Component getAdminNoWarpMessage(String player, String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-no-warp").getString()).replace("{player}", player).replace("{warp}", warp));
    }

    public Component getAdminDelWarpSuccessMessage(String player, String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-delete-warp-success").getString()).replace("{player}", player).replace("{warp}", warp));
    }

    public Component getAdminLockSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-lock-success").getString()).replace("{player}", player));
    }

    public Component getAdminUnLockSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-unlock-success").getString()).replace("{player}", player));
    }

    public Component getAdminMustInIslandSetHomeMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-must-in-island-set-home").getString()).replace("{player}", player));
    }

    public Component getAdminMustInIslandSetWarpMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-must-in-island-set-warp").getString()).replace("{player}", player));
    }

    public Component getAdminSetHomeSuccessMessage(String player, String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-set-home-success").getString()).replace("{player}", player).replace("{home}", home));
    }

    public Component getAdminSetWarpSuccessMessage(String player, String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-set-warp-success").getString()).replace("{player}", player).replace("{warp}", warp));
    }

    public Component getAdminPvpEnableSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-pvp-enable-success").getString()).replace("{player}", player));
    }

    public Component getAdminPvpDisableSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-pvp-disable-success").getString()).replace("{player}", player));
    }

    public Component getAdminBanSuccessMessage(String owner, String target) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-ban-success").getString()).replace("{owner}", owner).replace("{target}", target));
    }

    public Component getAdminUnbanSuccessMessage(String owner, String target) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-unban-success").getString()).replace("{owner}", owner).replace("{target}", target));
    }

    public Component getAdminCoopSuccessMessage(String owner, String target) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-coop-success").getString()).replace("{owner}", owner).replace("{target}", target));
    }

    public Component getAdminUncoopSuccessMessage(String owner, String target) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-uncoop-success").getString()).replace("{owner}", owner).replace("{target}", target));
    }

    public Component getAdminLobbySuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-lobby-success").getString()).replace("{player}", player));
    }

    // Player Command Messages
    public Component getPlayerNoIslandMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-no-island").getString());
    }

    public Component getPlayerAlreadyHasIslandMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-already-has-island").getString());
    }

    public Component getPlayerMustInIslandSetHomeMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-must-in-island-set-home").getString());
    }

    public Component getPlayerMustInIslandSetWarpMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-must-in-island-set-warp").getString());
    }

    public Component getPlayerInviteSentMessage(String target) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-invite-sent").getString()).replace("{player}", target));
    }

    public Component getPlayerInviteReceiveMessage(String inviter) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-invite-receive").getString()).replace("{player}", inviter));
    }

    public Component getPlayerInviteAcceptedNotifyMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-invite-accepted-notify").getString()).replace("{player}", player));
    }

    public Component getPlayerInviteRejectedNotifyMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-invite-rejected-notify").getString()).replace("{player}", player));
    }

    public Component getPlayerInviteAcceptedMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-invite-accepted").getString());
    }

    public Component getPlayerInviteRejectedMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-invite-rejected").getString());
    }

    public Component getPlayerAlreadyInvitedMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-already-invited").getString()).replace("{player}", player));
    }

    public Component getPlayerNoPendingInviteMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-no-pending-invite").getString());
    }

    public Component getPlayerRemoveMemberSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-remove-member-success").getString()).replace("{player}", player));
    }

    public Component getPlayerCannotRemoveSelfMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-cannot-remove-self").getString());
    }

    public Component getPlayerCreateSuccessMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-create-island-success").getString());
    }

    public Component getPlayerDeleteWarningMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-delete-warning").getString());
    }

    public Component getPlayerDeleteSuccessMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-delete-island-success").getString());
    }

    public Component getPlayerSetHomeSuccessMessage(String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-set-home-success").getString()).replace("{home}", home));
    }

    public Component getPlayerSetWarpSuccessMessage(String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-set-warp-success").getString()).replace("{warp}", warp));
    }

    public Component getPlayerDelHomeSuccessMessage(String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-delete-home-success").getString()).replace("{home}", home));
    }

    public Component getPlayerCannotDeleteDefaultHomeMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-cannot-delete-default-home").getString());
    }

    public Component getPlayerDelWarpSuccessMessage(String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-delete-warp-success").getString()).replace("{warp}", warp));
    }

    public Component getPlayerLockSuccessMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-lock-success").getString());
    }

    public Component getPlayerUnLockSuccessMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-unlock-success").getString());
    }

    public Component getPlayerPvpEnableSuccessMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-pvp-enable-success").getString());
    }

    public Component getPlayerPvpDisableSuccessMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-pvp-disable-success").getString());
    }

    public Component getPlayerNoHomeMessage(String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-no-home").getString()).replace("{home}", home));
    }

    public Component getPlayerNoWarpMessage(String warp) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-no-warp").getString()).replace("{warp}", warp));
    }

    public Component getPlayerHomeSuccessMessage(String home) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-home-success").getString()).replace("{home}", home));
    }

    public Component getPlayerSetOwnerSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-set-owner-success").getString()).replace("{player}", player));
    }

    public Component getPlayerAlreadyOwnerMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-already-owner").getString()).replace("{player}", player));
    }

    public Component getPlayerCannotLeaveAsOwnerMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-cannot-leave-as-owner").getString());
    }

    public Component getPlayerLeaveSuccessMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-leave-success").getString());
    }

    public Component getPlayerExpelSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-expel-success").getString()).replace("{player}", player));
    }

    public Component getPlayerCannotExpelIslandPlayerMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-cannot-expel-island-player").getString());
    }

    public Component getPlayerBanSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-ban-success").getString()).replace("{player}", player));
    }

    public Component getPlayerUnbanSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-unban-success").getString()).replace("{player}", player));
    }

    public Component getPlayerAlreadyBannedMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-already-banned").getString()).replace("{player}", player));
    }

    public Component getPlayerNotBannedMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-not-banned").getString()).replace("{player}", player));
    }

    public Component getPlayerCannotBanIslandPlayerMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-cannot-ban-island-player").getString());
    }

    public Component getPlayerCoopSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-coop-success").getString()).replace("{player}", player));
    }

    public Component getPlayerUncoopSuccessMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-uncoop-success").getString()).replace("{player}", player));
    }

    public Component getPlayerAlreadyCoopedMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-already-cooped").getString()).replace("{player}", player));
    }

    public Component getPlayerNotCoopedMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-not-cooped").getString()).replace("{player}", player));
    }

    public Component getPlayerCannotCoopIslandPlayerMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-cannot-coop-island-player").getString());
    }

    public Component getPlayerNoItemInHandMessage() {
        return ColorUtils.colorize(messages.node("messages", "no-item-in-hand").getString());
    }

    public Component getPlayerBlockValueCommandMessage(String block, int value) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "block-value").getString()).replace("{block}", block).replace("{value}", String.valueOf(value)));
    }

    public Component getPlayerNotOnlineMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-not-online").getString()).replace("{player}", player));
    }

    public Component getPlayerLobbySuccessMessage() {
        return ColorUtils.colorize(messages.node("messages", "player-lobby-success").getString());
    }

    // Info
    public Component getIslandInfoHeaderMessage() {
        return ColorUtils.colorize(messages.node("messages", "island-info-header").getString());
    }

    public Component getIslandInfoUUIDMessage(UUID islandUuid) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "island-info-uuid").getString()).replace("{island_uuid}", islandUuid.toString()));
    }

    public Component getIslandInfoLevelMessage(int level) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "island-info-level").getString()).replace("{level}", String.valueOf(level)));
    }

    public Component getIslandInfoOwnerMessage(String owner) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "island-info-owner").getString()).replace("{owner}", owner));
    }

    public Component getIslandInfoMembersMessage(String members) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "island-info-members").getString()).replace("{members}", members));
    }

    public Component getIslandInfoNoMembersMessage() {
        return ColorUtils.colorize(messages.node("messages", "island-info-no-members").getString());
    }

    // Top
    public Component getTopIslandsHeaderMessage() {
        return ColorUtils.colorize(messages.node("messages", "top-islands-header").getString());
    }

    public Component getTopIslandMessage(int rank, String owner, String members, int level) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "top-islands-message").getString()).replace("{rank}", String.valueOf(rank)).replace("{owner}", owner).replace("{members}", members).replace("{level}", String.valueOf(level)));
    }

    public Component getNoIslandsFoundMessage() {
        return ColorUtils.colorize(messages.node("messages", "top-islands-no-island").getString());
    }

    // Ban
    public Component getBannedPlayersHeaderMessage() {
        return ColorUtils.colorize(messages.node("messages", "banned-players-header").getString());
    }

    public Component getBannedPlayerMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "banned-player-message").getString()).replace("{player}", player));
    }

    public Component getNoBannedPlayersMessage() {
        return ColorUtils.colorize(messages.node("messages", "banned-player-no-banned").getString());
    }

    // Coop
    public Component getCoopedPlayersHeaderMessage() {
        return ColorUtils.colorize(messages.node("messages", "cooped-players-header").getString());
    }

    public Component getCoopedPlayerMessage(String player) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "cooped-player-message").getString()).replace("{player}", player));
    }

    public Component getNoCoopedPlayersMessage() {
        return ColorUtils.colorize(messages.node("messages", "cooped-player-no-cooped").getString());
    }

    // Help
    public Component getPlayerHelpHeader() {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-help-header").getString()));
    }

    public Component getPlayerHelpEntry(String command, String syntax, String description) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-help-entry").getString()).replace("{command}", command).replace("{syntax}", syntax).replace("{description}", description));
    }

    public Component getPlayerHelpFooter(int page, int total) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "player-help-footer").getString()).replace("{prev}", String.valueOf(page - 1)).replace("{next}", String.valueOf(page + 1)).replace("{page}", String.valueOf(page)).replace("{total}", String.valueOf(total)));
    }

    public Component getAdminHelpHeader() {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-help-header").getString()));
    }

    public Component getAdminHelpEntry(String command, String syntax, String description) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-help-entry").getString()).replace("{command}", command).replace("{syntax}", syntax).replace("{description}", description));
    }

    public Component getAdminHelpFooter(int page, int total) {
        return ColorUtils.colorize(Objects.requireNonNull(messages.node("messages", "admin-help-footer").getString()).replace("{prev}", String.valueOf(page - 1)).replace("{next}", String.valueOf(page + 1)).replace("{page}", String.valueOf(page)).replace("{total}", String.valueOf(total)));
    }

}
