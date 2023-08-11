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
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            jedis.publish("update_request_channel", "update_request");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void subscribeForWorldUpdates() {
        JedisPubSub jedisPubSub = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                if ("update".equals(message)) { // Note: Changed from "update_request" to "update"
                    redisHandler.updateWorldList();
                }
            }
        };

        new Thread(() -> {
            try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
                jedis.subscribe(jedisPubSub, "update_request_channel"); // Changed to match the publish channel
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}

