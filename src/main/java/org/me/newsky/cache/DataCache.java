package org.me.newsky.cache;

import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.model.IslandTop;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class DataCache {

    private static final String DATA_PREFIX = "newsky:data:";
    private static final int TTL_SECONDS = 1800;

    private static final String PLAYER_ISLAND_KEY = DATA_PREFIX + "player:island";
    private static final String PLAYER_NAME_KEY = DATA_PREFIX + "player:name";
    private static final String PLAYER_UUID_KEY = DATA_PREFIX + "player:uuid";
    private static final String NULL_MARKER = "@@NULL@@";
    private static final String CACHE_MARKER_SUFFIX = ":cached";

    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final DatabaseHandler database;

    /*
     * Prevent local cache stampede for lazy loads.
     * This is JVM-local protection. It does not coordinate across servers.
     */
    private final ConcurrentHashMap<String, Object> loadLocks = new ConcurrentHashMap<>();

    public DataCache(NewSky plugin, RedisHandler redisHandler, DatabaseHandler database) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.database = database;

        plugin.debug("DataCache", "Using lazy Redis cache mode with negative caching. Startup bootstrap is disabled.");
    }

    // =================================================================================================================
    // Key helpers
    // =================================================================================================================

    public String islandCoreKey(UUID islandUuid) {
        return DATA_PREFIX + "island:" + islandUuid + ":core";
    }

    public String islandPlayersKey(UUID islandUuid) {
        return DATA_PREFIX + "island:" + islandUuid + ":players";
    }

    public String islandCoopsKey(UUID islandUuid) {
        return DATA_PREFIX + "island:" + islandUuid + ":coops";
    }

    public String islandBansKey(UUID islandUuid) {
        return DATA_PREFIX + "island:" + islandUuid + ":bans";
    }

    public String islandUpgradesKey(UUID islandUuid) {
        return DATA_PREFIX + "island:" + islandUuid + ":upgrades";
    }

    public String islandHomesKey(UUID islandUuid, UUID playerUuid) {
        return DATA_PREFIX + "island:" + islandUuid + ":homes:" + playerUuid;
    }

    public String islandWarpsKey(UUID islandUuid, UUID playerUuid) {
        return DATA_PREFIX + "island:" + islandUuid + ":warps:" + playerUuid;
    }

    public String cacheMarkerKey(String key) {
        return key + CACHE_MARKER_SUFFIX;
    }

    private boolean parseBool(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private OptionalInt parseOptionalInt(String value) {
        if (value == null || value.isEmpty() || NULL_MARKER.equals(value)) {
            return OptionalInt.empty();
        }

        try {
            return OptionalInt.of(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid integer value in DataCache: " + value, e);
        }
    }

    private Optional<UUID> parseOptionalUuid(String value) {
        if (value == null || value.isEmpty() || NULL_MARKER.equals(value)) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid UUID value in DataCache: " + value, e);
        }
    }

    private UUID parseRequiredUuid(String value, String fieldName) {
        if (value == null || value.isEmpty() || NULL_MARKER.equals(value)) {
            throw new IllegalStateException("Missing UUID value in DataCache for field: " + fieldName);
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid UUID value in DataCache for field " + fieldName + ": " + value, e);
        }
    }

    private <T> T withLoadLock(String lockKey, Supplier<T> supplier) {
        Object lock = loadLocks.computeIfAbsent(lockKey, ignored -> new Object());

        synchronized (lock) {
            try {
                return supplier.get();
            } finally {
                loadLocks.remove(lockKey, lock);
            }
        }
    }

    private boolean isCompositeCacheKnown(Jedis jedis, String key) {
        return jedis.exists(key) || jedis.exists(cacheMarkerKey(key));
    }

    private void invalidateCompositeKey(Pipeline pipeline, String key) {
        pipeline.del(key);
        pipeline.del(cacheMarkerKey(key));
    }

    private void writeHashObjectCache(String key, Map<String, String> payload) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();

            invalidateCompositeKey(pipeline, key);

            if (payload == null || payload.isEmpty()) {
                pipeline.setex(cacheMarkerKey(key), TTL_SECONDS, "1");
            } else {
                pipeline.hset(key, payload);
                pipeline.expire(key, TTL_SECONDS);
            }

            pipeline.sync();
        }
    }

    private void writeSetObjectCache(String key, Collection<String> values) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();

            invalidateCompositeKey(pipeline, key);

            if (values == null || values.isEmpty()) {
                pipeline.setex(cacheMarkerKey(key), TTL_SECONDS, "1");
            } else {
                pipeline.sadd(key, values.toArray(new String[0]));
                pipeline.expire(key, TTL_SECONDS);
            }

            pipeline.sync();
        }
    }

    private Map<String, String> getHashObject(String key, Supplier<Map<String, String>> dbLoader, String loadDescription) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Map<String, String> cached = jedis.hgetAll(key);

            if (cached != null && !cached.isEmpty()) {
                return cached;
            }

            if (isCompositeCacheKnown(jedis, key)) {
                return Collections.emptyMap();
            }
        } catch (Exception e) {
            plugin.severe("Failed to get " + loadDescription + " cache from Redis for key: " + key, e);
        }

        return withLoadLock("hash:" + key, () -> {
            try (Jedis jedis = redisHandler.getJedis()) {
                Map<String, String> cached = jedis.hgetAll(key);

                if (cached != null && !cached.isEmpty()) {
                    return cached;
                }

                if (isCompositeCacheKnown(jedis, key)) {
                    return Collections.emptyMap();
                }
            } catch (Exception e) {
                plugin.severe("Failed to re-check " + loadDescription + " cache from Redis for key: " + key, e);
            }

            Map<String, String> loaded = dbLoader.get();

            try {
                writeHashObjectCache(key, loaded);
            } catch (Exception e) {
                plugin.severe("Failed to populate " + loadDescription + " cache for key: " + key, e);
            }

            return loaded == null || loaded.isEmpty() ? Collections.emptyMap() : loaded;
        });
    }

    private Set<String> getSetObject(String key, Supplier<Set<String>> dbLoader, String loadDescription) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Set<String> cached = jedis.smembers(key);

            if (cached != null && !cached.isEmpty()) {
                return cached;
            }

            if (isCompositeCacheKnown(jedis, key)) {
                return Set.of();
            }
        } catch (Exception e) {
            plugin.severe("Failed to get " + loadDescription + " cache from Redis for key: " + key, e);
        }

        return withLoadLock("set:" + key, () -> {
            try (Jedis jedis = redisHandler.getJedis()) {
                Set<String> cached = jedis.smembers(key);

                if (cached != null && !cached.isEmpty()) {
                    return cached;
                }

                if (isCompositeCacheKnown(jedis, key)) {
                    return Set.of();
                }
            } catch (Exception e) {
                plugin.severe("Failed to re-check " + loadDescription + " cache from Redis for key: " + key, e);
            }

            Set<String> loaded = dbLoader.get();

            try {
                writeSetObjectCache(key, loaded);
            } catch (Exception e) {
                plugin.severe("Failed to populate " + loadDescription + " cache for key: " + key, e);
            }

            return loaded == null || loaded.isEmpty() ? Set.of() : loaded;
        });
    }

    private Map<String, String> toRawIslandPlayersPayload(Map<UUID, String> players) {
        if (players == null || players.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> payload = new LinkedHashMap<>();

        for (Map.Entry<UUID, String> entry : players.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }

            String role = entry.getValue();
            if (role == null || role.isEmpty()) {
                continue;
            }

            payload.put(entry.getKey().toString(), role);
        }

        return payload.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(payload);
    }

    private Map<String, String> toRawStringMapPayload(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> payload = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : values.entrySet()) {
            String field = entry.getKey();
            if (field == null || field.isEmpty()) {
                continue;
            }

            payload.put(field, entry.getValue() == null ? "" : entry.getValue());
        }

        return payload.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(payload);
    }

    private Map<String, String> toRawUpgradePayload(Map<String, Integer> upgrades) {
        if (upgrades == null || upgrades.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> payload = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> entry : upgrades.entrySet()) {
            String upgradeId = entry.getKey();
            Integer level = entry.getValue();

            if (upgradeId == null || upgradeId.isEmpty() || level == null) {
                continue;
            }

            payload.put(upgradeId, String.valueOf(level));
        }

        return payload.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(payload);
    }

    private Set<String> toRawUuidSetPayload(Set<UUID> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }

        Set<String> payload = new LinkedHashSet<>();

        for (UUID uuid : values) {
            if (uuid != null) {
                payload.add(uuid.toString());
            }
        }

        return payload.isEmpty() ? Set.of() : Set.copyOf(payload);
    }

    private Map<String, String> getIslandPlayersRoleMap(UUID islandUuid) {
        String key = islandPlayersKey(islandUuid);
        return getHashObject(key, () -> toRawIslandPlayersPayload(database.getIslandPlayers(islandUuid)), "island players");
    }

    private Map<String, String> getHomeMap(UUID islandUuid, UUID playerUuid) {
        String key = islandHomesKey(islandUuid, playerUuid);
        return getHashObject(key, () -> toRawStringMapPayload(database.getIslandHomes(islandUuid, playerUuid)), "island homes");
    }

    private Map<String, String> getWarpMap(UUID islandUuid, UUID playerUuid) {
        String key = islandWarpsKey(islandUuid, playerUuid);
        return getHashObject(key, () -> toRawStringMapPayload(database.getIslandWarps(islandUuid, playerUuid)), "island warps");
    }

    private Set<String> getBanSet(UUID islandUuid) {
        String key = islandBansKey(islandUuid);
        return getSetObject(key, () -> toRawUuidSetPayload(database.getIslandBans(islandUuid)), "island bans");
    }

    private Set<String> getCoopSet(UUID islandUuid) {
        String key = islandCoopsKey(islandUuid);
        return getSetObject(key, () -> toRawUuidSetPayload(database.getIslandCoops(islandUuid)), "island coops");
    }

    private Map<String, String> getUpgradeMap(UUID islandUuid) {
        String key = islandUpgradesKey(islandUuid);
        return getHashObject(key, () -> toRawUpgradePayload(database.getIslandUpgrades(islandUuid)), "island upgrades");
    }

    // =================================================================================================================
    // Island lifecycle (DB truth + lazy Redis cache)
    // =================================================================================================================

    public void createIsland(UUID islandUuid, UUID ownerUuid, String homeLocation) {
        database.addIslandData(islandUuid, ownerUuid, homeLocation);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();

            pipeline.del(islandCoreKey(islandUuid));

            invalidateCompositeKey(pipeline, islandPlayersKey(islandUuid));
            invalidateCompositeKey(pipeline, islandHomesKey(islandUuid, ownerUuid));
            invalidateCompositeKey(pipeline, islandWarpsKey(islandUuid, ownerUuid));
            invalidateCompositeKey(pipeline, islandBansKey(islandUuid));
            invalidateCompositeKey(pipeline, islandCoopsKey(islandUuid));
            invalidateCompositeKey(pipeline, islandUpgradesKey(islandUuid));

            pipeline.hdel(PLAYER_ISLAND_KEY, ownerUuid.toString());
            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Database insert succeeded but Redis invalidation failed while creating island: island=" + islandUuid + ", owner=" + ownerUuid, e);
        }
    }

    public void deleteIsland(UUID islandUuid) {
        Map<UUID, String> playersBeforeDelete = database.getIslandPlayers(islandUuid);
        database.deleteIsland(islandUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();

            pipeline.del(islandCoreKey(islandUuid));

            invalidateCompositeKey(pipeline, islandPlayersKey(islandUuid));
            invalidateCompositeKey(pipeline, islandBansKey(islandUuid));
            invalidateCompositeKey(pipeline, islandCoopsKey(islandUuid));
            invalidateCompositeKey(pipeline, islandUpgradesKey(islandUuid));

            for (UUID playerUuid : playersBeforeDelete.keySet()) {
                pipeline.hdel(PLAYER_ISLAND_KEY, playerUuid.toString());

                invalidateCompositeKey(pipeline, islandHomesKey(islandUuid, playerUuid));
                invalidateCompositeKey(pipeline, islandWarpsKey(islandUuid, playerUuid));
            }

            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Database delete succeeded but Redis invalidation failed while deleting island: island=" + islandUuid, e);
        }
    }

    // =================================================================================================================
    // Player -> island index
    // =================================================================================================================

    public Optional<UUID> getIslandUuid(UUID playerUuid) {
        String field = playerUuid.toString();

        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.hget(PLAYER_ISLAND_KEY, field);
            if (value != null) {
                return parseOptionalUuid(value);
            }
        } catch (Exception e) {
            plugin.severe("Failed to get island UUID from Redis for player: " + playerUuid, e);
        }

        return withLoadLock("scalar:" + PLAYER_ISLAND_KEY + ":" + field, () -> {
            try (Jedis jedis = redisHandler.getJedis()) {
                String value = jedis.hget(PLAYER_ISLAND_KEY, field);
                if (value != null) {
                    return parseOptionalUuid(value);
                }
            } catch (Exception e) {
                plugin.severe("Failed to re-check island UUID cache from Redis for player: " + playerUuid, e);
            }

            Optional<UUID> loaded = database.getIslandUuid(playerUuid);

            try (Jedis jedis = redisHandler.getJedis()) {
                jedis.hset(PLAYER_ISLAND_KEY, field, loaded.map(UUID::toString).orElse(NULL_MARKER));
            } catch (Exception e) {
                plugin.severe("Failed to populate player->island cache for player: " + playerUuid, e);
            }

            return loaded;
        });
    }

    // =================================================================================================================
    // Player UUID / Name
    // =================================================================================================================

    public void updatePlayerUuid(UUID uuid, String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Player name cannot be null or empty");
        }

        Optional<String> oldName = database.getPlayerName(uuid);
        database.updatePlayerName(uuid, name);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();

            pipeline.hdel(PLAYER_NAME_KEY, uuid.toString());

            if (oldName.isPresent() && !oldName.get().isEmpty()) {
                pipeline.hdel(PLAYER_UUID_KEY, oldName.get().toLowerCase(Locale.ROOT));
            }

            pipeline.hdel(PLAYER_UUID_KEY, name.toLowerCase(Locale.ROOT));
            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis invalidation failed while updating player uuid mapping: uuid=" + uuid + ", name=" + name, e);
        }
    }

    public Optional<UUID> getPlayerUuid(String name) {
        if (name == null || name.isEmpty()) {
            return Optional.empty();
        }

        String lower = name.toLowerCase(Locale.ROOT);

        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.hget(PLAYER_UUID_KEY, lower);
            if (value != null) {
                return parseOptionalUuid(value);
            }
        } catch (Exception e) {
            plugin.severe("Failed to get player UUID from Redis for name: " + name, e);
        }

        return withLoadLock("scalar:" + PLAYER_UUID_KEY + ":" + lower, () -> {
            try (Jedis jedis = redisHandler.getJedis()) {
                String value = jedis.hget(PLAYER_UUID_KEY, lower);
                if (value != null) {
                    return parseOptionalUuid(value);
                }
            } catch (Exception e) {
                plugin.severe("Failed to re-check player UUID cache from Redis for name: " + name, e);
            }

            Optional<UUID> loaded = database.getPlayerUuid(name);

            try (Jedis jedis = redisHandler.getJedis()) {
                jedis.hset(PLAYER_UUID_KEY, lower, loaded.map(UUID::toString).orElse(NULL_MARKER));
            } catch (Exception e) {
                plugin.severe("Failed to populate player uuid cache for name: " + name, e);
            }

            return loaded;
        });
    }

    public Optional<String> getPlayerName(UUID uuid) {
        String field = uuid.toString();

        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.hget(PLAYER_NAME_KEY, field);
            if (value != null) {
                if (NULL_MARKER.equals(value)) {
                    return Optional.empty();
                }
                return Optional.of(value);
            }
        } catch (Exception e) {
            plugin.severe("Failed to get player name from Redis for: " + uuid, e);
        }

        return withLoadLock("scalar:" + PLAYER_NAME_KEY + ":" + field, () -> {
            try (Jedis jedis = redisHandler.getJedis()) {
                String value = jedis.hget(PLAYER_NAME_KEY, field);
                if (value != null) {
                    if (NULL_MARKER.equals(value)) {
                        return Optional.empty();
                    }
                    return Optional.of(value);
                }
            } catch (Exception e) {
                plugin.severe("Failed to re-check player name cache from Redis for uuid: " + uuid, e);
            }

            Optional<String> loaded = database.getPlayerName(uuid);

            try (Jedis jedis = redisHandler.getJedis()) {
                Pipeline pipeline = jedis.pipelined();

                pipeline.hset(PLAYER_NAME_KEY, field, loaded.orElse(NULL_MARKER));

                if (loaded.isPresent() && !loaded.get().isEmpty()) {
                    pipeline.hset(PLAYER_UUID_KEY, loaded.get().toLowerCase(Locale.ROOT), uuid.toString());
                }

                pipeline.sync();
            } catch (Exception e) {
                plugin.severe("Failed to populate player name cache for uuid: " + uuid, e);
            }

            return loaded;
        });
    }

    public Map<UUID, String> getPlayerNames(Collection<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return Collections.emptyMap();
        }

        if (uuids.size() == 1) {
            UUID only = uuids.iterator().next();
            if (only == null) {
                return Collections.emptyMap();
            }

            return getPlayerName(only).map(name -> Map.of(only, name)).orElseGet(Collections::emptyMap);
        }

        List<UUID> ordered = new ArrayList<>(uuids.size());
        Set<UUID> seen = new HashSet<>((int) (uuids.size() / 0.75f) + 1);

        for (UUID uuid : uuids) {
            if (uuid != null && seen.add(uuid)) {
                ordered.add(uuid);
            }
        }

        if (ordered.isEmpty()) {
            return Collections.emptyMap();
        }

        String[] fields = new String[ordered.size()];
        for (int i = 0; i < ordered.size(); i++) {
            fields[i] = ordered.get(i).toString();
        }

        Map<UUID, String> result = new HashMap<>((int) (ordered.size() / 0.75f) + 1);
        List<UUID> misses = new ArrayList<>();

        try (Jedis jedis = redisHandler.getJedis()) {
            List<String> names = jedis.hmget(PLAYER_NAME_KEY, fields);

            for (int i = 0; i < ordered.size(); i++) {
                String name = names.get(i);

                if (name == null) {
                    misses.add(ordered.get(i));
                    continue;
                }

                if (!NULL_MARKER.equals(name)) {
                    result.put(ordered.get(i), name);
                }
            }
        } catch (Exception e) {
            plugin.severe("Failed to get player names from Redis for UUID collection, size=" + ordered.size(), e);
            misses.clear();
            misses.addAll(ordered);
            result.clear();
        }

        if (!misses.isEmpty()) {
            Map<UUID, String> loaded = database.getPlayerNames(misses);

            try (Jedis jedis = redisHandler.getJedis()) {
                Pipeline pipeline = jedis.pipelined();

                for (UUID miss : misses) {
                    String loadedName = loaded.get(miss);

                    if (loadedName == null || loadedName.isEmpty()) {
                        pipeline.hset(PLAYER_NAME_KEY, miss.toString(), NULL_MARKER);
                        continue;
                    }

                    result.put(miss, loadedName);
                    pipeline.hset(PLAYER_NAME_KEY, miss.toString(), loadedName);
                    pipeline.hset(PLAYER_UUID_KEY, loadedName.toLowerCase(Locale.ROOT), miss.toString());
                }

                pipeline.sync();
            } catch (Exception e) {
                plugin.severe("Failed to populate player names cache from DB, size=" + loaded.size(), e);
                result.putAll(loaded);
            }
        }

        return result.isEmpty() ? Collections.emptyMap() : result;
    }

    // =================================================================================================================
    // Island core
    // =================================================================================================================

    public void updateIslandLock(UUID islandUuid, boolean lock) {
        database.updateIslandLock(islandUuid, lock);

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.del(islandCoreKey(islandUuid));
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis invalidation failed while updating island lock: island=" + islandUuid + ", lock=" + lock, e);
        }
    }

    public boolean isIslandLock(UUID islandUuid) {
        String key = islandCoreKey(islandUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.hget(key, "lock");
            if (value != null) {
                return parseBool(value);
            }
        } catch (Exception e) {
            plugin.severe("Failed to get island lock from Redis for island: " + islandUuid, e);
        }

        return withLoadLock("scalar:" + key + ":lock", () -> {
            try (Jedis jedis = redisHandler.getJedis()) {
                String value = jedis.hget(key, "lock");
                if (value != null) {
                    return parseBool(value);
                }
            } catch (Exception e) {
                plugin.severe("Failed to re-check island lock cache from Redis for island: " + islandUuid, e);
            }

            boolean loaded = database.getIslandLock(islandUuid);

            try (Jedis jedis = redisHandler.getJedis()) {
                Pipeline pipeline = jedis.pipelined();
                pipeline.hset(key, "lock", loaded ? "1" : "0");
                pipeline.expire(key, TTL_SECONDS);
                pipeline.sync();
            } catch (Exception e) {
                plugin.severe("Failed to populate island lock cache for island: " + islandUuid, e);
            }

            return loaded;
        });
    }

    public void updateIslandPvp(UUID islandUuid, boolean pvp) {
        database.updateIslandPvp(islandUuid, pvp);

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.del(islandCoreKey(islandUuid));
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis invalidation failed while updating island pvp: island=" + islandUuid + ", pvp=" + pvp, e);
        }
    }

    public boolean isIslandPvp(UUID islandUuid) {
        String key = islandCoreKey(islandUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.hget(key, "pvp");
            if (value != null) {
                return parseBool(value);
            }
        } catch (Exception e) {
            plugin.severe("Failed to get island pvp from Redis for island: " + islandUuid, e);
        }

        return withLoadLock("scalar:" + key + ":pvp", () -> {
            try (Jedis jedis = redisHandler.getJedis()) {
                String value = jedis.hget(key, "pvp");
                if (value != null) {
                    return parseBool(value);
                }
            } catch (Exception e) {
                plugin.severe("Failed to re-check island pvp cache from Redis for island: " + islandUuid, e);
            }

            boolean loaded = database.getIslandPvp(islandUuid);

            try (Jedis jedis = redisHandler.getJedis()) {
                Pipeline pipeline = jedis.pipelined();
                pipeline.hset(key, "pvp", loaded ? "1" : "0");
                pipeline.expire(key, TTL_SECONDS);
                pipeline.sync();
            } catch (Exception e) {
                plugin.severe("Failed to populate island pvp cache for island: " + islandUuid, e);
            }

            return loaded;
        });
    }

    public void updateIslandLevel(UUID islandUuid, int level) {
        database.updateIslandLevel(islandUuid, level);

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.del(islandCoreKey(islandUuid));
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis invalidation failed while updating island level: island=" + islandUuid + ", level=" + level, e);
        }
    }

    public int getIslandLevel(UUID islandUuid) {
        String key = islandCoreKey(islandUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.hget(key, "level");
            if (value != null) {
                return parseOptionalInt(value).orElse(0);
            }
        } catch (Exception e) {
            plugin.severe("Failed to get island level from Redis for island: " + islandUuid, e);
        }

        return withLoadLock("scalar:" + key + ":level", () -> {
            try (Jedis jedis = redisHandler.getJedis()) {
                String value = jedis.hget(key, "level");
                if (value != null) {
                    return parseOptionalInt(value).orElse(0);
                }
            } catch (Exception e) {
                plugin.severe("Failed to re-check island level cache from Redis for island: " + islandUuid, e);
            }

            int loaded = database.getIslandLevel(islandUuid);

            try (Jedis jedis = redisHandler.getJedis()) {
                Pipeline pipeline = jedis.pipelined();
                pipeline.hset(key, "level", String.valueOf(loaded));
                pipeline.expire(key, TTL_SECONDS);
                pipeline.sync();
            } catch (Exception e) {
                plugin.severe("Failed to populate island level cache for island: " + islandUuid, e);
            }

            return loaded;
        });
    }

    public List<IslandTop> getTopIslandLevels(int limit) {
        return database.getTopIslandLevels(limit);
    }

    // =================================================================================================================
    // Players / owner / members
    // =================================================================================================================

    public void updateIslandPlayer(UUID islandUuid, UUID playerUuid, String role, String homeLocation) {
        database.addIslandPlayer(islandUuid, playerUuid, role, homeLocation);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();

            invalidateCompositeKey(pipeline, islandPlayersKey(islandUuid));

            pipeline.hdel(PLAYER_ISLAND_KEY, playerUuid.toString());

            invalidateCompositeKey(pipeline, islandHomesKey(islandUuid, playerUuid));
            invalidateCompositeKey(pipeline, islandWarpsKey(islandUuid, playerUuid));
            invalidateCompositeKey(pipeline, islandBansKey(islandUuid));
            invalidateCompositeKey(pipeline, islandCoopsKey(islandUuid));

            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Database insert succeeded but Redis invalidation failed while adding island player: island=" + islandUuid + ", player=" + playerUuid + ", role=" + role, e);
        }
    }

    public void deleteIslandPlayer(UUID islandUuid, UUID playerUuid) {
        database.deleteIslandPlayer(islandUuid, playerUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();

            invalidateCompositeKey(pipeline, islandPlayersKey(islandUuid));

            pipeline.hdel(PLAYER_ISLAND_KEY, playerUuid.toString());

            invalidateCompositeKey(pipeline, islandHomesKey(islandUuid, playerUuid));
            invalidateCompositeKey(pipeline, islandWarpsKey(islandUuid, playerUuid));

            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Database delete succeeded but Redis invalidation failed while deleting island player: island=" + islandUuid + ", player=" + playerUuid, e);
        }
    }

    public void updateIslandOwner(UUID islandUuid, UUID oldOwnerUuid, UUID newOwnerUuid) {
        database.updateIslandOwner(islandUuid, oldOwnerUuid, newOwnerUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();
            pipeline.del(islandCoreKey(islandUuid));
            invalidateCompositeKey(pipeline, islandPlayersKey(islandUuid));
            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis invalidation failed while updating island owner: island=" + islandUuid + ", oldOwner=" + oldOwnerUuid + ", newOwner=" + newOwnerUuid, e);
        }
    }

    public UUID getIslandOwner(UUID islandUuid) {
        String key = islandCoreKey(islandUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.hget(key, "owner");
            if (value != null) {
                Optional<UUID> cached = parseOptionalUuid(value);
                if (cached.isPresent()) {
                    return cached.get();
                }
                throw new IllegalStateException("Island owner does not exist in cache for island: " + islandUuid);
            }
        } catch (Exception e) {
            if (e instanceof IllegalStateException) {
                throw (IllegalStateException) e;
            }
            plugin.severe("Failed to get island owner from Redis for island: " + islandUuid, e);
        }

        return withLoadLock("scalar:" + key + ":owner", () -> {
            try (Jedis jedis = redisHandler.getJedis()) {
                String value = jedis.hget(key, "owner");
                if (value != null) {
                    Optional<UUID> cached = parseOptionalUuid(value);
                    if (cached.isPresent()) {
                        return cached.get();
                    }
                    throw new IllegalStateException("Island owner does not exist in cache for island: " + islandUuid);
                }
            } catch (Exception e) {
                if (e instanceof IllegalStateException) {
                    throw (IllegalStateException) e;
                }
                plugin.severe("Failed to re-check island owner cache from Redis for island: " + islandUuid, e);
            }

            Optional<UUID> loaded = database.getIslandOwner(islandUuid);

            try (Jedis jedis = redisHandler.getJedis()) {
                Pipeline pipeline = jedis.pipelined();
                pipeline.hset(key, "owner", loaded.map(UUID::toString).orElse(NULL_MARKER));
                pipeline.expire(key, TTL_SECONDS);
                pipeline.sync();
            } catch (Exception e) {
                plugin.severe("Failed to populate island owner cache for island: " + islandUuid, e);
            }

            if (loaded.isEmpty()) {
                throw new IllegalStateException("Island owner does not exist in database for island: " + islandUuid);
            }

            return loaded.get();
        });
    }

    public Set<UUID> getIslandMembers(UUID islandUuid) {
        Map<String, String> players = getIslandPlayersRoleMap(islandUuid);

        if (players.isEmpty()) {
            return Set.of();
        }

        Set<UUID> result = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : players.entrySet()) {
            if ("member".equalsIgnoreCase(entry.getValue())) {
                result.add(parseRequiredUuid(entry.getKey(), "islandPlayers.memberUuid"));
            }
        }

        return result.isEmpty() ? Set.of() : Set.copyOf(result);
    }

    public Set<UUID> getIslandPlayers(UUID islandUuid) {
        Map<String, String> players = getIslandPlayersRoleMap(islandUuid);

        if (players.isEmpty()) {
            return Set.of();
        }

        Set<UUID> result = new LinkedHashSet<>(players.size());
        for (String rawPlayer : players.keySet()) {
            result.add(parseRequiredUuid(rawPlayer, "islandPlayers.playerUuid"));
        }

        return Set.copyOf(result);
    }

    // =================================================================================================================
    // Homes
    // =================================================================================================================

    public void updateHomePoint(UUID islandUuid, UUID playerUuid, String homeName, String homeLocation) {
        database.updateHomePoint(islandUuid, playerUuid, homeName, homeLocation);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();
            invalidateCompositeKey(pipeline, islandHomesKey(islandUuid, playerUuid));
            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis invalidation failed while updating home point: island=" + islandUuid + ", player=" + playerUuid + ", home=" + homeName, e);
        }
    }

    public void deleteHomePoint(UUID islandUuid, UUID playerUuid, String homeName) {
        database.deleteHomePoint(islandUuid, playerUuid, homeName);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();
            invalidateCompositeKey(pipeline, islandHomesKey(islandUuid, playerUuid));
            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Database delete succeeded but Redis invalidation failed while deleting home point: island=" + islandUuid + ", player=" + playerUuid + ", home=" + homeName, e);
        }
    }

    public Optional<String> getHomeLocation(UUID islandUuid, UUID playerUuid, String homeName) {
        String key = islandHomesKey(islandUuid, playerUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.hget(key, homeName);
            if (value != null) {
                return Optional.of(value);
            }

            if (isCompositeCacheKnown(jedis, key)) {
                return Optional.empty();
            }
        } catch (Exception e) {
            plugin.severe("Failed to get island homes cache from Redis for island=" + islandUuid + ", player=" + playerUuid, e);
        }

        Map<String, String> homes = getHomeMap(islandUuid, playerUuid);
        return Optional.ofNullable(homes.get(homeName));
    }

    public Set<String> getHomeNames(UUID islandUuid, UUID playerUuid) {
        Map<String, String> homes = getHomeMap(islandUuid, playerUuid);
        return homes.isEmpty() ? Set.of() : Set.copyOf(homes.keySet());
    }

    // =================================================================================================================
    // Warps
    // =================================================================================================================

    public void updateWarpPoint(UUID islandUuid, UUID playerUuid, String warpName, String warpLocation) {
        database.updateWarpPoint(islandUuid, playerUuid, warpName, warpLocation);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();
            invalidateCompositeKey(pipeline, islandWarpsKey(islandUuid, playerUuid));
            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis invalidation failed while updating warp point: island=" + islandUuid + ", player=" + playerUuid + ", warp=" + warpName, e);
        }
    }

    public void deleteWarpPoint(UUID islandUuid, UUID playerUuid, String warpName) {
        database.deleteWarpPoint(islandUuid, playerUuid, warpName);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();
            invalidateCompositeKey(pipeline, islandWarpsKey(islandUuid, playerUuid));
            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Database delete succeeded but Redis invalidation failed while deleting warp point: island=" + islandUuid + ", player=" + playerUuid + ", warp=" + warpName, e);
        }
    }

    public Optional<String> getWarpLocation(UUID islandUuid, UUID playerUuid, String warpName) {
        String key = islandWarpsKey(islandUuid, playerUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.hget(key, warpName);
            if (value != null) {
                return Optional.of(value);
            }

            if (isCompositeCacheKnown(jedis, key)) {
                return Optional.empty();
            }
        } catch (Exception e) {
            plugin.severe("Failed to get island warps cache from Redis for island=" + islandUuid + ", player=" + playerUuid, e);
        }

        Map<String, String> warps = getWarpMap(islandUuid, playerUuid);
        return Optional.ofNullable(warps.get(warpName));
    }

    public Set<String> getWarpNames(UUID islandUuid, UUID playerUuid) {
        Map<String, String> warps = getWarpMap(islandUuid, playerUuid);
        return warps.isEmpty() ? Set.of() : Set.copyOf(warps.keySet());
    }

    // =================================================================================================================
    // Bans
    // =================================================================================================================

    public void updateBanPlayer(UUID islandUuid, UUID playerUuid) {
        database.updateBanPlayer(islandUuid, playerUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();
            invalidateCompositeKey(pipeline, islandBansKey(islandUuid));
            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis invalidation failed while adding banned player: island=" + islandUuid + ", player=" + playerUuid, e);
        }
    }

    public void deleteBanPlayer(UUID islandUuid, UUID playerUuid) {
        database.deleteBanPlayer(islandUuid, playerUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();
            invalidateCompositeKey(pipeline, islandBansKey(islandUuid));
            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Database delete succeeded but Redis invalidation failed while deleting banned player: island=" + islandUuid + ", player=" + playerUuid, e);
        }
    }

    public boolean isPlayerBanned(UUID islandUuid, UUID playerUuid) {
        String key = islandBansKey(islandUuid);
        String member = playerUuid.toString();

        try (Jedis jedis = redisHandler.getJedis()) {
            if (jedis.exists(key)) {
                return jedis.sismember(key, member);
            }

            if (jedis.exists(cacheMarkerKey(key))) {
                return false;
            }
        } catch (Exception e) {
            plugin.severe("Failed to get island bans cache from Redis for island: " + islandUuid, e);
        }

        Set<String> loaded = getBanSet(islandUuid);
        return loaded.contains(member);
    }

    public Set<UUID> getBannedPlayers(UUID islandUuid) {
        Set<String> rawPlayers = getBanSet(islandUuid);

        if (rawPlayers.isEmpty()) {
            return Set.of();
        }

        Set<UUID> result = new LinkedHashSet<>(rawPlayers.size());
        for (String rawPlayer : rawPlayers) {
            result.add(parseRequiredUuid(rawPlayer, "islandBans.playerUuid"));
        }

        return Set.copyOf(result);
    }

    // =================================================================================================================
    // Coops
    // =================================================================================================================

    public void updateCoopPlayer(UUID islandUuid, UUID playerUuid) {
        database.updateCoopPlayer(islandUuid, playerUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();
            invalidateCompositeKey(pipeline, islandCoopsKey(islandUuid));
            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis invalidation failed while adding coop player: island=" + islandUuid + ", player=" + playerUuid, e);
        }
    }

    public void deleteCoopPlayer(UUID islandUuid, UUID playerUuid) {
        database.deleteCoopPlayer(islandUuid, playerUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();
            invalidateCompositeKey(pipeline, islandCoopsKey(islandUuid));
            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Database delete succeeded but Redis invalidation failed while deleting coop player: island=" + islandUuid + ", player=" + playerUuid, e);
        }
    }

    public Set<UUID> deleteAllCoopOfPlayer(UUID playerUuid) {
        Set<UUID> touchedIslands = database.getPlayerCoopedIslands(playerUuid);
        database.deleteAllCoopOfPlayer(playerUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();

            for (UUID islandUuid : touchedIslands) {
                invalidateCompositeKey(pipeline, islandCoopsKey(islandUuid));
            }

            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Database delete succeeded but Redis invalidation failed while deleting all coop of player: player=" + playerUuid, e);
        }

        return touchedIslands.isEmpty() ? Set.of() : Set.copyOf(touchedIslands);
    }

    public boolean isPlayerCooped(UUID islandUuid, UUID playerUuid) {
        String key = islandCoopsKey(islandUuid);
        String member = playerUuid.toString();

        try (Jedis jedis = redisHandler.getJedis()) {
            if (jedis.exists(key)) {
                return jedis.sismember(key, member);
            }

            if (jedis.exists(cacheMarkerKey(key))) {
                return false;
            }
        } catch (Exception e) {
            plugin.severe("Failed to get island coops cache from Redis for island: " + islandUuid, e);
        }

        Set<String> loaded = getCoopSet(islandUuid);
        return loaded.contains(member);
    }

    public Set<UUID> getCoopedPlayers(UUID islandUuid) {
        Set<String> rawPlayers = getCoopSet(islandUuid);

        if (rawPlayers.isEmpty()) {
            return Set.of();
        }

        Set<UUID> result = new LinkedHashSet<>(rawPlayers.size());
        for (String rawPlayer : rawPlayers) {
            result.add(parseRequiredUuid(rawPlayer, "islandCoops.playerUuid"));
        }

        return Set.copyOf(result);
    }

    // =================================================================================================================
    // Upgrades
    // =================================================================================================================

    public void updateIslandUpgradeLevel(UUID islandUuid, String upgradeId, int level) {
        if (level <= 1) {
            database.deleteIslandUpgrade(islandUuid, upgradeId);
        } else {
            database.upsertIslandUpgrade(islandUuid, upgradeId, level);
        }

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();
            invalidateCompositeKey(pipeline, islandUpgradesKey(islandUuid));
            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis invalidation failed while updating island upgrade level: island=" + islandUuid + ", upgrade=" + upgradeId + ", level=" + level, e);
        }
    }

    public int getIslandUpgradeLevel(UUID islandUuid, String upgradeId) {
        String key = islandUpgradesKey(islandUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.hget(key, upgradeId);
            if (value != null) {
                return parseOptionalInt(value).orElse(1);
            }

            if (isCompositeCacheKnown(jedis, key)) {
                return 1;
            }
        } catch (Exception e) {
            plugin.severe("Failed to get island upgrades cache from Redis for island: " + islandUuid, e);
        }

        Map<String, String> upgrades = getUpgradeMap(islandUuid);
        return parseOptionalInt(upgrades.get(upgradeId)).orElse(1);
    }
}