package org.me.newsky.cluster;

import java.util.UUID;

/**
 * Central definition of every Redis key used by the cluster layer.
 * Keeps key schemas in one place instead of scattering string literals across stores.
 */
public final class ClusterKeys {

    private static final String ONLINE_PLAYERS = "online:players";
    private static final String ONLINE_PLAYER_SERVERS = "online:player_servers";
    private static final String ISLAND_SERVER = "island:server";
    private static final String ISLAND_CLAIM_QUEUE_PREFIX = "island:claimqueue:";
    private static final String SERVER_MSPT = "server:mspt";
    private static final String KNOWN_SERVERS = "servers:known";
    private static final String ROUND_ROBIN_COUNTER = "server:round_robin_counter";
    private static final String SERVER_HEARTBEAT_PREFIX = "heartbeat:server:";
    private static final String GAME_SERVER_HEARTBEAT_PREFIX = "heartbeat:game_server:";
    private static final String INVITATION_PREFIX = "invitation:island:";

    private final String prefix;

    public ClusterKeys(String clusterId) {
        this.prefix = "newsky-" + clusterId + ":";
    }

    public String messagingInbox(String serverName) {
        return prefix + "messaging:inbox:" + serverName;
    }

    public String onlinePlayers() {
        return prefix + ONLINE_PLAYERS;
    }

    public String onlinePlayerServers() {
        return prefix + ONLINE_PLAYER_SERVERS;
    }

    public String islandServer() {
        return prefix + ISLAND_SERVER;
    }

    public String islandClaimQueue(UUID islandUuid) {
        return prefix + ISLAND_CLAIM_QUEUE_PREFIX + islandUuid;
    }

    public String serverMspt() {
        return prefix + SERVER_MSPT;
    }

    public String knownServers() {
        return prefix + KNOWN_SERVERS;
    }

    public String roundRobinCounter() {
        return prefix + ROUND_ROBIN_COUNTER;
    }

    public String serverHeartbeat(String serverName) {
        return prefix + SERVER_HEARTBEAT_PREFIX + serverName;
    }

    public String gameServerHeartbeat(String serverName) {
        return prefix + GAME_SERVER_HEARTBEAT_PREFIX + serverName;
    }

    public String serverHeartbeatPrefix() {
        return prefix + SERVER_HEARTBEAT_PREFIX;
    }

    public String gameServerHeartbeatPrefix() {
        return prefix + GAME_SERVER_HEARTBEAT_PREFIX;
    }

    public String invitation(UUID inviteeUuid) {
        return prefix + INVITATION_PREFIX + inviteeUuid;
    }
}
