package org.me.newsky.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

public class RedisEventService {
    private final RedisHandler redisHandler;

    public RedisEventService(RedisHandler redisHandler) {
        this.redisHandler = redisHandler;
        subscribeForWorldUpdates();
    }

    public void publishUpdateRequest() {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            jedis.publish("update_request_channel", "update");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void subscribeForWorldUpdates() {
        JedisPubSub jedisPubSub = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                if ("update".equals(message)) {
                    redisHandler.updateWorldList();
                }
            }
        };

        new Thread(() -> {
            try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
                jedis.subscribe(jedisPubSub, "update_request_channel");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}

