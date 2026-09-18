package org.me.newsky.cluster;

import org.junit.jupiter.api.Test;
import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ServerRegistryTest {
    private final Jedis jedis = mock(Jedis.class);
    private final ServerRegistry registry;

    ServerRegistryTest() {
        RedisHandler redisHandler = mock(RedisHandler.class);
        when(redisHandler.getJedis()).thenReturn(jedis);
        registry = new ServerRegistry(mock(NewSky.class), redisHandler, mock(IslandRegistry.class), mock(OnlinePlayerRegistry.class));
    }

    @Test
    void activeServersAreTheKnownServersWhoseHeartbeatKeyExists() {
        when(jedis.smembers(ClusterKeys.knownServers())).thenReturn(new LinkedHashSet<>(Arrays.asList("lobby", "server1", "server2")));
        when(jedis.mget(ClusterKeys.serverHeartbeat("lobby"), ClusterKeys.serverHeartbeat("server1"), ClusterKeys.serverHeartbeat("server2"))).thenReturn(Arrays.asList("100", null, "300"));

        assertEquals(Map.of("lobby", "100", "server2", "300"), registry.getActiveServers());
        verify(jedis, never()).scan(anyString(), any());
    }

    @Test
    void gameServersUseTheGameServerHeartbeatKeys() {
        when(jedis.smembers(ClusterKeys.knownServers())).thenReturn(new LinkedHashSet<>(Arrays.asList("lobby", "server1")));
        when(jedis.mget(ClusterKeys.gameServerHeartbeat("lobby"), ClusterKeys.gameServerHeartbeat("server1"))).thenReturn(Arrays.asList(null, "300"));

        assertEquals(Map.of("server1", "300"), registry.getActiveGameServers());
    }

    @Test
    void noKnownServersMeansNoLookup() {
        when(jedis.smembers(ClusterKeys.knownServers())).thenReturn(new LinkedHashSet<>());

        assertEquals(Map.of(), registry.getActiveServers());
        verify(jedis, never()).mget(any(String[].class));
    }
}
