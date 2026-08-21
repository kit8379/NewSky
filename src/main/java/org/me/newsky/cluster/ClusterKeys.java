package org.me.newsky.cluster;

import java.util.UUID;

/**
 * Central definition of every Redis key used by the cluster layer.
 * Keeps key schemas in one place instead of scattering string literals across stores.
 */
public final class ClusterKeys {

    private static final String ONLINE_PLAYERS = "newsky:online:players";
    private static final String ONLINE_PLAYER_SERVERS = "newsky:online:player_servers";
    private static final String ISLAND_SERVER = "newsky:island:server";
    private static final String SERVER_MSPT = "newsky:server:mspt";
    private static final String ROUND_ROBIN_COUNTER = "newsky:server:round_robin_counter";
    private static final String SERVER_HEARTBEAT_PREFIX = "newsky:heartbeat:server:";
    private static final String GAME_SERVER_HEARTBEAT_PREFIX = "newsky:heartbeat:game_server:";
    private static final String INVITATION_PREFIX = "newsky:invitation:island:";
    private static final String COOP_CLEANUP_QUEUE = "newsky:cleanup:coop";
    private static final String COOP_CLEANUP_LEASES = "newsky:cleanup:coop:leases";
    private static final String MESSAGING_INBOX_PREFIX = "newsky:messaging:inbox:";

    private ClusterKeys() {
    }

    public static String onlinePlayers() {
        return ONLINE_PLAYERS;
    }

    public static String onlinePlayerServers() {
        return ONLINE_PLAYER_SERVERS;
    }

    public static String islandServer() {
        return ISLAND_SERVER;
    }

    public static String serverMspt() {
        return SERVER_MSPT;
    }

    public static String roundRobinCounter() {
        return ROUND_ROBIN_COUNTER;
    }

    public static String serverHeartbeat(String serverName) {
        return SERVER_HEARTBEAT_PREFIX + serverName;
    }

    public static String gameServerHeartbeat(String serverName) {
        return GAME_SERVER_HEARTBEAT_PREFIX + serverName;
    }

    public static String serverHeartbeatPrefix() {
        return SERVER_HEARTBEAT_PREFIX;
    }

    public static String gameServerHeartbeatPrefix() {
        return GAME_SERVER_HEARTBEAT_PREFIX;
    }

    public static String invitation(UUID inviteeUuid) {
        return INVITATION_PREFIX + inviteeUuid;
    }

    public static String coopCleanupQueue() {
        return COOP_CLEANUP_QUEUE;
    }

    public static String coopCleanupLeases() {
        return COOP_CLEANUP_LEASES;
    }

    public static String messagingInbox(String serverName) {
        return MESSAGING_INBOX_PREFIX + serverName;
    }
}
