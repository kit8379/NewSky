package org.me.newsky.teleport;

import org.bukkit.Location;
import org.bukkit.World;
import org.json.JSONObject;
import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;

import java.util.UUID;

public class TeleportHandler {

    private static final String PENDING_TELEPORT_PREFIX = "newsky:pending_teleport:";
    private static final int PENDING_TELEPORT_TTL_SECONDS = 60;

    private final NewSky plugin;
    private final RedisHandler redisHandler;

    public TeleportHandler(NewSky plugin, RedisHandler redisHandler) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
    }

    public void addPendingTeleport(UUID playerUuid, Location location) {
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Pending teleport location must have a world");
        }

        JSONObject json = new JSONObject();
        json.put("world", location.getWorld().getName());
        json.put("x", location.getX());
        json.put("y", location.getY());
        json.put("z", location.getZ());
        json.put("yaw", location.getYaw());
        json.put("pitch", location.getPitch());

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.setex(key(playerUuid), PENDING_TELEPORT_TTL_SECONDS, json.toString());
        } catch (Exception e) {
            plugin.severe("Failed to add pending teleport for player " + playerUuid, e);
            throw new RuntimeException(e);
        }
    }

    public void removePendingTeleport(UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.del(key(playerUuid));
        } catch (Exception e) {
            plugin.severe("Failed to remove pending teleport for player " + playerUuid, e);
        }
    }

    public Location getPendingTeleport(UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String raw = jedis.get(key(playerUuid));
            if (raw == null || raw.isEmpty()) {
                return null;
            }

            JSONObject json = new JSONObject(raw);
            World world = plugin.getServer().getWorld(json.getString("world"));
            if (world == null) {
                return null;
            }

            return new Location(world, json.getDouble("x"), json.getDouble("y"), json.getDouble("z"), (float) json.getDouble("yaw"), (float) json.getDouble("pitch"));
        } catch (Exception e) {
            plugin.severe("Failed to get pending teleport for player " + playerUuid, e);
            return null;
        }
    }

    private String key(UUID playerUuid) {
        return PENDING_TELEPORT_PREFIX + playerUuid;
    }
}

