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
    private final ClusterKeys keys = new ClusterKeys("test");
    private final Jedis jedis = mock(Jedis.class);
    private final ServerRegistry registry;

    ServerRegistryTest() {
        RedisHandler redisHandler = mock(RedisHandler.class);
        when(redisHandler.getJedis()).thenReturn(jedis);
        when(redisHandler.getKeys()).thenReturn(keys);
        registry = new ServerRegistry(mock(NewSky.class), redisHandler, mock(IslandRegistry.class), mock(OnlinePlayerRegistry.class));
    }

    @Test
    void activeServersAreTheKnownServersWhoseHeartbeatKeyExists() {
        when(jedis.smembers(keys.knownServers())).thenReturn(new LinkedHashSet<>(Arrays.asList("lobby", "server1", "server2")));
        when(jedis.mget(keys.serverHeartbeat("lobby"), keys.serverHeartbeat("server1"), keys.serverHeartbeat("server2"))).thenReturn(Arrays.asList("100", null, "300"));

        assertEquals(Map.of("lobby", "100", "server2", "300"), registry.getActiveServers());
        verify(jedis, never()).scan(anyString(), any());
    }

    @Test
    void gameServersUseTheGameServerHeartbeatKeys() {
        when(jedis.smembers(keys.knownServers())).thenReturn(new LinkedHashSet<>(Arrays.asList("lobby", "server1")));
        when(jedis.mget(keys.gameServerHeartbeat("lobby"), keys.gameServerHeartbeat("server1"))).thenReturn(Arrays.asList(null, "300"));

        assertEquals(Map.of("server1", "300"), registry.getActiveGameServers());
    }

    @Test
    void noKnownServersMeansNoLookup() {
        when(jedis.smembers(keys.knownServers())).thenReturn(new LinkedHashSet<>());

        assertEquals(Map.of(), registry.getActiveServers());
        verify(jedis, never()).mget(any(String[].class));
    }
}
