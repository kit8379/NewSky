package org.me.newsky.event;

import org.me.newsky.handler.RedisHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

public class RedisEventService {
    private final RedisHandler redisHandler;

    public RedisEventService(RedisHandler redisHandler) {
        this.redisHandler = redisHandler;
        subscribeForWorldUpdates();
    }

    public void publishUpdateRequest() {
        redisHandler.getJedisPool().getResource().publish("update_request_channel", "update");
    }

    private void subscribeForWorldUpdates() {
        JedisPubSub jedisPubSub = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                if ("update_request".equals(message)) {
                    redisHandler.updateWorldList();
                }
            }
        };

        new Thread(() -> {
            try (Jedis jedis = redisHandler.getJedisPool().getResource()) { // Assuming getJedisPool() returns the pool
                jedis.subscribe(jedisPubSub, "world_update_channel");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

}
