package org.me.newsky.handler;

import org.me.newsky.NewSky;
import redis.clients.jedis.Jedis;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class CacheHandler {

    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final DatabaseHandler databaseHandler;

    public CacheHandler(NewSky plugin) {
        this.plugin = plugin;
        this.redisHandler = plugin.getRedisHandler();
        this.databaseHandler = plugin.getDBHandler();

        cacheDataToRedis();
    }

    private void cacheDataToRedis() {
        cacheIslandDataToRedis();
        cacheIslandMembersToRedis();
    }

    private void cacheIslandDataToRedis() {
        databaseHandler.selectAllIslandData(resultSet -> {
            try (Jedis jedis = redisHandler.getJedis()) {
                while (resultSet.next()) {
                    UUID islandUuid = (UUID) resultSet.getObject("island_uuid");
                    jedis.hset("island_data:" + islandUuid, "owner_uuid", resultSet.getString("owner_uuid"));
                    jedis.hset("island_data:" + islandUuid, "level", String.valueOf(resultSet.getInt("level")));
                    // Set a marker for unchanged data
                    jedis.hset("island_data:" + islandUuid, "status", "unchanged");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private void cacheIslandMembersToRedis() {
        databaseHandler.selectAllIslandMembers(resultSet -> {
            try (Jedis jedis = redisHandler.getJedis()) {
                while (resultSet.next()) {
                    UUID islandUuid = (UUID) resultSet.getObject("island_uuid");
                    UUID memberUuid = (UUID) resultSet.getObject("member_uuid");
                    jedis.sadd("island_members:" + islandUuid, memberUuid.toString());
                    // Set a marker for unchanged member data
                    jedis.hset("island_members:" + islandUuid + ":status", memberUuid.toString(), "unchanged");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void saveCacheToDatabase() {
        try (Jedis jedis = redisHandler.getJedis()) {
            // Update or delete island_data based on status
            jedis.keys("island_data:*").forEach(key -> {
                String status = jedis.hget(key, "status");
                UUID islandUuid = UUID.fromString(key.replace("island_data:", ""));
                if ("modified".equals(status)) {
                    // Update island_data in database
                    String ownerUuid = jedis.hget(key, "owner_uuid");
                    int level = Integer.parseInt(jedis.hget(key, "level"));
                    databaseHandler.updateIslandData(islandUuid, UUID.fromString(ownerUuid), level);
                } else if ("deleted".equals(status)) {
                    // Delete island_data from database
                    jedis.del(key);
                    databaseHandler.deleteIslandData(islandUuid);
                }
            });

            // Update or delete island_members based on status
            jedis.keys("island_members:*:status").forEach(key -> {
                UUID islandUuid = UUID.fromString(key.replace("island_members:", "").replace(":status", ""));
                jedis.hgetAll(key).forEach((memberUuid, status) -> {
                    if ("modified".equals(status)) {
                        // Update member in database
                        databaseHandler.addIslandMember(islandUuid, UUID.fromString(memberUuid));
                    } else if ("deleted".equals(status)) {
                        // Delete member from database
                        jedis.srem("island_members:" + islandUuid, memberUuid);
                        jedis.hdel(key, memberUuid);
                        databaseHandler.deleteIslandMember(islandUuid, UUID.fromString(memberUuid));
                    }
                });
            });
        }
    }

    public void createIsland(UUID islandUuid, UUID ownerUuid, int level) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_data:" + islandUuid, "owner_uuid", ownerUuid.toString());
            jedis.hset("island_data:" + islandUuid, "level", String.valueOf(level));
            jedis.hset("island_data:" + islandUuid, "status", "modified");
        }
    }

    public void updateIslandOwner(UUID islandUuid, UUID newOwnerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_data:" + islandUuid, "owner_uuid", newOwnerUuid.toString());
            jedis.hset("island_data:" + islandUuid, "status", "modified");
        }
    }

    public void updateIslandLevel(UUID islandUuid, int newLevel) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_data:" + islandUuid, "level", String.valueOf(newLevel));
            jedis.hset("island_data:" + islandUuid, "status", "modified");
        }
    }

    public void deleteIsland(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            // Mark the island data for deletion
            jedis.hset("island_data:" + islandUuid, "status", "deleted");

            // Mark all members of the island for deletion
            Set<String> members = jedis.smembers("island_members:" + islandUuid);
            for (String member : members) {
                jedis.hset("island_members:" + islandUuid + ":status", member, "deleted");
            }
        }
    }

    public void addIslandMember(UUID islandUuid, UUID memberUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.sadd("island_members:" + islandUuid, memberUuid.toString());
            jedis.hset("island_members:" + islandUuid + ":status", memberUuid.toString(), "modified");
        }
    }

    public void deleteIslandMember(UUID islandUuid, UUID memberUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_members:" + islandUuid + ":status", memberUuid.toString(), "deleted");
        }
    }

    /**
     * Get the owner UUID of a specific island.
     *
     * @param islandUuid The UUID of the island.
     * @return UUID of the owner or null if not found.
     */
    public UUID getIslandOwner(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String owner = jedis.hget("island_data:" + islandUuid, "owner_uuid");
            return owner == null ? null : UUID.fromString(owner);
        }
    }

    /**
     * Get the level of a specific island.
     *
     * @param islandUuid The UUID of the island.
     * @return Level of the island or -1 if not found.
     */
    public int getIslandLevel(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String level = jedis.hget("island_data:" + islandUuid, "level");
            return level == null ? -1 : Integer.parseInt(level);
        }
    }

    /**
     * Get all members of a specific island.
     *
     * @param islandUuid The UUID of the island.
     * @return Set of member UUIDs or empty set if not found.
     */
    public Set<UUID> getIslandMembers(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Set<String> members = jedis.smembers("island_members:" + islandUuid);
            Set<UUID> uuids = new HashSet<>();
            for (String member : members) {
                uuids.add(UUID.fromString(member));
            }
            return uuids;
        }
    }

    /**
     * Get the island UUID for a given player UUID. This method checks if the player is an owner or a member of any island.
     *
     * @param playerUuid The UUID of the player.
     * @return Optional containing the UUID of the island or empty if not found.
     */
    public Optional<UUID> getIslandUuidByPlayerUuid(UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            // Check if player is an owner of any island
            Set<String> ownerKeys = jedis.keys("island_data:*");
            for (String key : ownerKeys) {
                String owner = jedis.hget(key, "owner_uuid");
                if (playerUuid.toString().equals(owner)) {
                    return Optional.of(UUID.fromString(key.replace("island_data:", "")));
                }
            }

            // Check if player is a member of any island
            Set<String> memberKeys = jedis.keys("island_members:*");
            for (String key : memberKeys) {
                boolean isMember = jedis.sismember(key, playerUuid.toString());
                if (isMember) {
                    return Optional.of(UUID.fromString(key.replace("island_members:", "")));
                }
            }
        }

        return Optional.empty();
    }
}
