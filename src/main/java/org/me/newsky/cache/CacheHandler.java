package org.me.newsky.cache;

import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class CacheHandler {

    private final RedisHandler redisHandler;
    private final DatabaseHandler databaseHandler;

    public CacheHandler(RedisHandler redisHandler, DatabaseHandler databaseHandler) {
        this.redisHandler = redisHandler;
        this.databaseHandler = databaseHandler;

        cacheDataToRedis();
    }

    private void cacheDataToRedis() {
        cacheIslandDataToRedis();
        cacheIslandMembersToRedis();
    }

    private void cacheIslandDataToRedis() {
        databaseHandler.selectAllIslandData(resultSet -> {
            try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
                while (resultSet.next()) {
                    String islandUuidStr = resultSet.getString("island_uuid");
                    UUID islandUuid = UUID.fromString(islandUuidStr);
                    jedis.hset("island_data:" + islandUuid, "owner_uuid", resultSet.getString("owner_uuid"));
                    jedis.hset("island_data:" + islandUuid, "level", String.valueOf(resultSet.getInt("level")));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void cacheIslandMembersToRedis() {
        databaseHandler.selectAllIslandMembers(resultSet -> {
            try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
                while (resultSet.next()) {
                    String islandUuidStr = resultSet.getString("island_uuid");
                    UUID islandUuid = UUID.fromString(islandUuidStr);
                    String memberUuidStr = resultSet.getString("member_uuid");
                    UUID memberUuid = UUID.fromString(memberUuidStr);
                    jedis.sadd("island_members:" + islandUuid, memberUuid.toString());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void saveCacheToDatabase() {
        // Directly save all cached data to the database
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            jedis.keys("island_data:*").forEach(key -> {
                UUID islandUuid = UUID.fromString(key.replace("island_data:", ""));
                String ownerUuid = jedis.hget(key, "owner_uuid");
                int level = Integer.parseInt(jedis.hget(key, "level"));
                databaseHandler.updateIslandData(islandUuid, UUID.fromString(ownerUuid), level);
            });

            jedis.keys("island_members:*").forEach(key -> {
                UUID islandUuid = UUID.fromString(key.replace("island_members:", ""));
                Set<String> members = jedis.smembers(key);
                for (String memberUuid : members) {
                    databaseHandler.addIslandMember(islandUuid, UUID.fromString(memberUuid));
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void createIsland(UUID islandUuid, UUID ownerUuid) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            jedis.hset("island_data:" + islandUuid, "owner_uuid", ownerUuid.toString());
            jedis.hset("island_data:" + islandUuid, "level", String.valueOf(0));
            databaseHandler.updateIslandData(islandUuid, ownerUuid, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteIsland(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            // Delete all members
            Set<String> members = jedis.smembers("island_members:" + islandUuid);
            for (String member : members) {
                jedis.srem("island_members:" + islandUuid, member);
                databaseHandler.deleteIslandMember(islandUuid, UUID.fromString(member));
            }

            // Delete the island data
            jedis.del("island_data:" + islandUuid);
            databaseHandler.deleteIslandData(islandUuid);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addIslandMember(UUID islandUuid, UUID memberUuid) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            jedis.sadd("island_members:" + islandUuid, memberUuid.toString());
            databaseHandler.addIslandMember(islandUuid, memberUuid);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void removeIslandMember(UUID islandUuid, UUID memberUuid) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            jedis.srem("island_members:" + islandUuid, memberUuid.toString());
            databaseHandler.deleteIslandMember(islandUuid, memberUuid);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Get the owner UUID of a specific island.
     *
     * @param islandUuid The UUID of the island.
     * @return UUID of the owner or null if not found.
     */
    public UUID getIslandOwner(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            String owner = jedis.hget("island_data:" + islandUuid, "owner_uuid");
            return owner == null ? null : UUID.fromString(owner);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    /**
     * Get all members of a specific island.
     *
     * @param islandUuid The UUID of the island.
     * @return Set of member UUIDs or empty set if not found.
     */
    public Set<UUID> getIslandMembers(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            Set<String> members = jedis.smembers("island_members:" + islandUuid);
            Set<UUID> uuids = new HashSet<>();
            for (String member : members) {
                uuids.add(UUID.fromString(member));
            }
            return uuids;
        } catch (Exception e) {
            e.printStackTrace();
            return new HashSet<>();
        }
    }

    /**
     * Get the island UUID for a given player UUID. This method checks if the player is an owner or a member of any island.
     *
     * @param playerUuid The UUID of the player.
     * @return Optional containing the UUID of the island or empty if not found.
     */
    public Optional<UUID> getIslandUuidByPlayerUuid(UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
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
            return Optional.empty();
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }
}
