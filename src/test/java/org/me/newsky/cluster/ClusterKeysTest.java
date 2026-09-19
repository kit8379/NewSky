package org.me.newsky.cluster;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClusterKeysTest {
    private final UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void networksWithTheSameServerAndPlayerIdsHaveSeparateRedisKeys() {
        List<String> first = allKeys(new ClusterKeys("first"));
        List<String> second = allKeys(new ClusterKeys("second"));

        assertTrue(Collections.disjoint(first, second));
        assertTrue(first.stream().allMatch(key -> key.startsWith("newsky-first:")));
        assertTrue(second.stream().allMatch(key -> key.startsWith("newsky-second:")));
        assertEquals(first, allKeys(new ClusterKeys("first")));
    }

    @Test
    void heartbeatPrefixesUsedByLuaAndServerLookupsMatchHeartbeatKeys() {
        ClusterKeys keys = new ClusterKeys("test");

        assertEquals(keys.serverHeartbeat("server1"), keys.serverHeartbeatPrefix() + "server1");
        assertEquals(keys.gameServerHeartbeat("server1"), keys.gameServerHeartbeatPrefix() + "server1");
    }

    private List<String> allKeys(ClusterKeys keys) {
        return List.of(keys.onlinePlayers(), keys.onlinePlayerServers(), keys.islandServer(),
                keys.islandClaimQueue(uuid), keys.serverMspt(), keys.knownServers(),
                keys.roundRobinCounter(), keys.serverHeartbeat("server1"),
                keys.gameServerHeartbeat("server1"), keys.invitation(uuid), keys.messagingInbox("server1"));
    }
}
