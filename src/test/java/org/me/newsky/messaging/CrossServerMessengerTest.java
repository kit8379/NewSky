package org.me.newsky.messaging;

import org.bukkit.Bukkit;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.me.newsky.NewSky;
import org.me.newsky.cluster.ClusterKeys;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.thread.BukkitAsyncExecutor;
import org.mockito.MockedStatic;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.XAddParams;

import static org.mockito.Mockito.*;

class CrossServerMessengerTest {
    @Test
    void startupCleanupAndOutgoingRequestsUseTheConfiguredCluster() {
        NewSky plugin = mock(NewSky.class);
        RedisHandler redis = mock(RedisHandler.class);
        Jedis jedis = mock(Jedis.class);
        when(redis.getJedis()).thenReturn(jedis);
        when(redis.getKeys()).thenReturn(new ClusterKeys("test"));
        BukkitAsyncExecutor executor = mock(BukkitAsyncExecutor.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        when(plugin.getBukkitAsyncExecutor()).thenReturn(executor);
        CrossServerMessenger messenger = new CrossServerMessenger(plugin, redis, "server1");

        messenger.start();
        verify(jedis).del("newsky-test:messaging:inbox:server1");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            messenger.request("server2", "test", new JSONObject());
            verify(jedis).xadd(eq("newsky-test:messaging:inbox:server2"), any(XAddParams.class), anyMap());
        } finally {
            messenger.stop();
        }
    }
}
