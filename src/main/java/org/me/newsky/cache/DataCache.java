package org.me.newsky.cache;

import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.model.IslandTop;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public final class DataCache {

    private static final String DATA_PREFIX = "newsky:data:";
    private static final int POSITIVE_TTL_SECONDS = 1800;
    private static final int NEGATIVE_TTL_SECONDS = 300;
    private static final int VERSION_TTL_SECONDS = POSITIVE_TTL_SECONDS * 4;
    private static final int TTL_JITTER_PERCENT = 10;

    private static final String PLAYER_ISLAND_KEY_PREFIX = DATA_PREFIX + "player:island:";
    private static final String PLAYER_NAME_KEY_PREFIX = DATA_PREFIX + "player:name:";
    private static final String PLAYER_UUID_KEY_PREFIX = DATA_PREFIX + "player:uuid:";
    private static final String LEGACY_PLAYER_ISLAND_KEY = DATA_PREFIX + "player:island";
    private static final String LEGACY_PLAYER_NAME_KEY = DATA_PREFIX + "player:name";
    private static final String LEGACY_PLAYER_UUID_KEY = DATA_PREFIX + "player:uuid";
    private static final String NULL_MARKER = "@@NULL@@";
    private static final String CACHE_MARKER_SUFFIX = ":cached";
    private static final String CACHE_VERSION_SUFFIX = ":version";

    private static final String WRITE_HASH_IF_VERSION_SCRIPT = """
            local current = redis.call('GET', KEYS[3])
            if not current then current = '0' end
            if current ~= ARGV[1] then return 0 end
            
            redis.call('DEL', KEYS[1])
            redis.call('DEL', KEYS[2])
            
            local count = tonumber(ARGV[4])
            if count == 0 then
                redis.call('SETEX', KEYS[2], tonumber(ARGV[3]), '1')
            else
                for i = 1, count do
                    local base = 4 + ((i - 1) * 2)
                    redis.call('HSET', KEYS[1], ARGV[base + 1], ARGV[base + 2])
                end
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
            end
            
            return 1
            """;

    private static final String WRITE_SET_IF_VERSION_SCRIPT = """
            local current = redis.call('GET', KEYS[3])
            if not current then current = '0' end
            if current ~= ARGV[1] then return 0 end
            
            redis.call('DEL', KEYS[1])
            redis.call('DEL', KEYS[2])
            
            local count = tonumber(ARGV[4])
            if count == 0 then
                redis.call('SETEX', KEYS[2], tonumber(ARGV[3]), '1')
            else
                for i = 1, count do
                    redis.call('SADD', KEYS[1], ARGV[4 + i])
                end
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
            end
            
            return 1
            """;

    private static final String WRITE_STRING_IF_VERSION_SCRIPT = """
            local current = redis.call('GET', KEYS[2])
            if not current then current = '0' end
            if current ~= ARGV[1] then return 0 end
            
            redis.call('SETEX', KEYS[1], tonumber(ARGV[2]), ARGV[3])
            return 1
            """;

    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final DatabaseHandler database;

    private final ConcurrentHashMap<String, Object> loadLocks = new ConcurrentHashMap<>();

    public DataCache(NewSky plugin, RedisHandler redisHandler, DatabaseHandler database) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.database = database;

        deleteLegacyPlayerHashCaches();
    }

    // =================================================================================================================
    // Key helpers
    // =================================================================================================================

    public String islandCoreKey(UUID islandUuid) {
        return DATA_PREFIX + "island:" + islandUuid + ":core:v2";
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

    private String cacheVersionKey(String key) {
        return key + CACHE_VERSION_SUFFIX;
    }

    private String playerIslandKey(UUID playerUuid) {
        return PLAYER_ISLAND_KEY_PREFIX + playerUuid;
    }

    private String playerNameKey(UUID playerUuid) {
        return PLAYER_NAME_KEY_PREFIX + playerUuid;
    }

    private String playerUuidKey(String name) {
        return PLAYER_UUID_KEY_PREFIX + name.toLowerCase(Locale.ROOT);
    }

    // =================================================================================================================
    // TTL helpers
    // =================================================================================================================

    private int positiveTtlSeconds() {
        return jitteredTtlSeconds(POSITIVE_TTL_SECONDS);
    }

    private int negativeTtlSeconds() {
        return jitteredTtlSeconds(NEGATIVE_TTL_SECONDS);
    }

    private int jitteredTtlSeconds(int baseTtlSeconds) {
        int jitter = Math.max(1, baseTtlSeconds * TTL_JITTER_PERCENT / 100);
        return baseTtlSeconds - jitter + ThreadLocalRandom.current().nextInt(jitter * 2 + 1);
    }

    // =================================================================================================================
    // Parse helpers
    // =================================================================================================================

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

    // =================================================================================================================
    // Local load coordination
    // =================================================================================================================

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

    // =================================================================================================================
    // Redis cache state and invalidation
    // =================================================================================================================

    private boolean isCompositeCacheKnown(Jedis jedis, String key) {
        return jedis.exists(key) || jedis.exists(cacheMarkerKey(key));
    }

    private void deleteLegacyPlayerHashCaches() {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.del(LEGACY_PLAYER_ISLAND_KEY, LEGACY_PLAYER_NAME_KEY, LEGACY_PLAYER_UUID_KEY);
        } catch (Exception e) {
            plugin.severe("Failed to delete legacy player cache hashes from Redis.", e);
        }
    }

    private String readCacheVersion(Jedis jedis, String key) {
        String version = jedis.get(cacheVersionKey(key));
        return version == null ? "0" : version;
    }

    private void bumpCacheVersion(Pipeline pipeline, String key) {
        pipeline.incr(cacheVersionKey(key));
        pipeline.expire(cacheVersionKey(key), VERSION_TTL_SECONDS);
    }

    private void deleteCompositeKey(Pipeline pipeline, String key) {
        pipeline.del(key);
        pipeline.del(cacheMarkerKey(key));
    }

    private void invalidateCompositeKey(Pipeline pipeline, String key) {
        bumpCacheVersion(pipeline, key);
        deleteCompositeKey(pipeline, key);
    }

    private void invalidateScalarKey(Pipeline pipeline, String key) {
        bumpCacheVersion(pipeline, key);
        pipeline.del(key);
    }

    // =================================================================================================================
    // Version-guarded Redis writes
    // =================================================================================================================

    private void writeHashObjectCache(String key, Map<String, String> payload, String expectedVersion) {
        List<String> keys = List.of(key, cacheMarkerKey(key), cacheVersionKey(key));
        List<String> args = new ArrayList<>();

        args.add(expectedVersion);
        args.add(String.valueOf(positiveTtlSeconds()));
        args.add(String.valueOf(negativeTtlSeconds()));

        if (payload == null || payload.isEmpty()) {
            args.add("0");
        } else {
            List<Map.Entry<String, String>> entries = new ArrayList<>(payload.entrySet());
            args.add(String.valueOf(entries.size()));

            for (Map.Entry<String, String> entry : entries) {
                args.add(entry.getKey());
                args.add(entry.getValue() == null ? "" : entry.getValue());
            }
        }

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.eval(WRITE_HASH_IF_VERSION_SCRIPT, keys, args);
        }
    }

    private void writeSetObjectCache(String key, Collection<String> values, String expectedVersion) {
        List<String> keys = List.of(key, cacheMarkerKey(key), cacheVersionKey(key));
        List<String> args = new ArrayList<>();
        List<String> members = new ArrayList<>();

        if (values != null) {
            for (String value : values) {
                if (value != null) {
                    members.add(value);
                }
            }
        }

        args.add(expectedVersion);
        args.add(String.valueOf(positiveTtlSeconds()));
        args.add(String.valueOf(negativeTtlSeconds()));
        args.add(String.valueOf(members.size()));
        args.addAll(members);

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.eval(WRITE_SET_IF_VERSION_SCRIPT, keys, args);
        }
    }

    private void writeStringObjectCache(Jedis jedis, String key, String value, boolean positive, String expectedVersion) {
        List<String> keys = List.of(key, cacheVersionKey(key));
        List<String> args = List.of(expectedVersion, String.valueOf(positive ? positiveTtlSeconds() : negativeTtlSeconds()), value == null ? NULL_MARKER : value);
        jedis.eval(WRITE_STRING_IF_VERSION_SCRIPT, keys, args);
    }

    // =================================================================================================================
    // Lazy cache loaders
    // =================================================================================================================

    private Map<String, String> getHashObject(String key, Supplier<Map<String, String>> dbLoader) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Map<String, String> cached = jedis.hgetAll(key);

            if (cached != null && !cached.isEmpty()) {
                return cached;
            }

            if (isCompositeCacheKnown(jedis, key)) {
                return Collections.emptyMap();
            }
        } catch (Exception e) {
            plugin.severe("Failed to get cache from Redis for key: " + key, e);
        }

        return withLoadLock("hash:" + key, () -> {
            String expectedVersion = null;

            try (Jedis jedis = redisHandler.getJedis()) {
                Map<String, String> cached = jedis.hgetAll(key);

                if (cached != null && !cached.isEmpty()) {
                    return cached;
                }

                if (isCompositeCacheKnown(jedis, key)) {
                    return Collections.emptyMap();
                }

                expectedVersion = readCacheVersion(jedis, key);
            } catch (Exception e) {
                plugin.severe("Failed to re-check cache from Redis for key: " + key, e);
            }

            Map<String, String> loaded = dbLoader.get();

            if (expectedVersion != null) {
                try {
                    writeHashObjectCache(key, loaded, expectedVersion);
                } catch (Exception e) {
                    plugin.severe("Failed to populate cache for key: " + key, e);
                }
            }

            return loaded == null || loaded.isEmpty() ? Collections.emptyMap() : loaded;
        });
    }

    private Set<String> getSetObject(String key, Supplier<Set<String>> dbLoader) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Set<String> cached = jedis.smembers(key);

            if (cached != null && !cached.isEmpty()) {
                return cached;
            }

            if (isCompositeCacheKnown(jedis, key)) {
                return Set.of();
            }
        } catch (Exception e) {
            plugin.severe("Failed to get cache from Redis for key: " + key, e);
        }

        return withLoadLock("set:" + key, () -> {
            String expectedVersion = null;

            try (Jedis jedis = redisHandler.getJedis()) {
                Set<String> cached = jedis.smembers(key);

                if (cached != null && !cached.isEmpty()) {
                    return cached;
                }

                if (isCompositeCacheKnown(jedis, key)) {
                    return Set.of();
                }

                expectedVersion = readCacheVersion(jedis, key);
            } catch (Exception e) {
                plugin.severe("Failed to re-check cache from Redis for key: " + key, e);
            }

            Set<String> loaded = dbLoader.get();

            if (expectedVersion != null) {
                try {
                    writeSetObjectCache(key, loaded, expectedVersion);
                } catch (Exception e) {
                    plugin.severe("Failed to populate cache for key: " + key, e);
                }
            }

            return loaded == null || loaded.isEmpty() ? Set.of() : loaded;
        });
    }

    private Optional<String> parseOptionalString(String value) {
        if (value == null || value.isEmpty() || NULL_MARKER.equals(value)) {
            return Optional.empty();
        }

        return Optional.of(value);
    }

    private Optional<String> getOptionalStringObject(String key, Supplier<Optional<String>> dbLoader) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String cached = jedis.get(key);
            if (cached != null) {
                return parseOptionalString(cached);
            }
        } catch (Exception e) {
            plugin.severe("Failed to get cache from Redis for key: " + key, e);
        }

        return withLoadLock("scalar:" + key, () -> {
            String expectedVersion = null;

            try (Jedis jedis = redisHandler.getJedis()) {
                String cached = jedis.get(key);
                if (cached != null) {
                    return parseOptionalString(cached);
                }

                expectedVersion = readCacheVersion(jedis, key);
            } catch (Exception e) {
                plugin.severe("Failed to re-check cache from Redis for key: " + key, e);
            }

            Optional<String> loaded = Optional.ofNullable(dbLoader.get()).orElseGet(Optional::empty).filter(value -> !value.isEmpty());

            if (expectedVersion != null) {
                try (Jedis jedis = redisHandler.getJedis()) {
                    writeStringObjectCache(jedis, key, loaded.orElse(NULL_MARKER), loaded.isPresent(), expectedVersion);
                } catch (Exception e) {
                    plugin.severe("Failed to populate cache for key: " + key, e);
                }
            }

            return loaded;
        });
    }

    private Map<String, String> readCacheVersions(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> cacheKeys = new ArrayList<>(keys);
        String[] versionKeys = new String[cacheKeys.size()];

        for (int i = 0; i < cacheKeys.size(); i++) {
            versionKeys[i] = cacheVersionKey(cacheKeys.get(i));
        }

        try (Jedis jedis = redisHandler.getJedis()) {
            List<String> versions = jedis.mget(versionKeys);
            Map<String, String> result = new HashMap<>((int) (cacheKeys.size() / 0.75f) + 1);

            for (int i = 0; i < cacheKeys.size(); i++) {
                String version = versions.get(i);
                result.put(cacheKeys.get(i), version == null ? "0" : version);
            }

            return result;
        } catch (Exception e) {
            plugin.severe("Failed to get player name cache versions from Redis, size=" + cacheKeys.size(), e);
            return Collections.emptyMap();
        }
    }

    // =================================================================================================================
    // Redis payload converters
    // =================================================================================================================

    private Map<String, String> toRedisIslandPlayersPayload(Map<UUID, String> players) {
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

    private Map<String, String> toRedisIslandCorePayload(DatabaseHandler.IslandCoreData core) {
        Map<String, String> payload = new LinkedHashMap<>();

        payload.put("lock", core.lock() ? "1" : "0");
        payload.put("pvp", core.pvp() ? "1" : "0");
        payload.put("level", String.valueOf(core.level()));
        payload.put("owner", core.owner().map(UUID::toString).orElse(NULL_MARKER));

        return Collections.unmodifiableMap(payload);
    }

    private Map<String, String> toRedisStringMapPayload(Map<String, String> values) {
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

    private Map<String, String> toRedisUpgradePayload(Map<String, Integer> upgrades) {
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

    private Set<String> toRedisUuidSetPayload(Set<UUID> values) {
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

    // =================================================================================================================
    // Typed lazy cache accessors
    // =================================================================================================================

    private Map<String, String> getIslandPlayersRoleMap(UUID islandUuid) {
        String key = islandPlayersKey(islandUuid);
        return getHashObject(key, () -> toRedisIslandPlayersPayload(database.getIslandPlayers(islandUuid)));
    }

    private Map<String, String> getIslandCoreMap(UUID islandUuid) {
        String key = islandCoreKey(islandUuid);
        return getHashObject(key, () -> database.getIslandCore(islandUuid).map(this::toRedisIslandCorePayload).orElseGet(Collections::emptyMap));
    }

    private Map<String, String> getHomeMap(UUID islandUuid, UUID playerUuid) {
        String key = islandHomesKey(islandUuid, playerUuid);
        return getHashObject(key, () -> toRedisStringMapPayload(database.getIslandHomes(islandUuid, playerUuid)));
    }

    private Map<String, String> getWarpMap(UUID islandUuid, UUID playerUuid) {
        String key = islandWarpsKey(islandUuid, playerUuid);
        return getHashObject(key, () -> toRedisStringMapPayload(database.getIslandWarps(islandUuid, playerUuid)));
    }

    private Set<String> getBanSet(UUID islandUuid) {
        String key = islandBansKey(islandUuid);
        return getSetObject(key, () -> toRedisUuidSetPayload(database.getIslandBans(islandUuid)));
    }

    private Set<String> getCoopSet(UUID islandUuid) {
        String key = islandCoopsKey(islandUuid);
        return getSetObject(key, () -> toRedisUuidSetPayload(database.getIslandCoops(islandUuid)));
    }

    private Map<String, String> getUpgradeMap(UUID islandUuid) {
        String key = islandUpgradesKey(islandUuid);
        return getHashObject(key, () -> toRedisUpgradePayload(database.getIslandUpgrades(islandUuid)));
    }

    // =================================================================================================================
    // Island lifecycle (DB truth + lazy Redis cache)
    // =================================================================================================================

    public void createIsland(UUID islandUuid, UUID ownerUuid, String homeLocation) {
        database.addIslandData(islandUuid, ownerUuid, homeLocation);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();

            invalidateCompositeKey(pipeline, islandCoreKey(islandUuid));
            invalidateCompositeKey(pipeline, islandPlayersKey(islandUuid));
            invalidateCompositeKey(pipeline, islandHomesKey(islandUuid, ownerUuid));
            invalidateCompositeKey(pipeline, islandWarpsKey(islandUuid, ownerUuid));
            invalidateCompositeKey(pipeline, islandBansKey(islandUuid));
            invalidateCompositeKey(pipeline, islandCoopsKey(islandUuid));
            invalidateCompositeKey(pipeline, islandUpgradesKey(islandUuid));
            invalidateScalarKey(pipeline, playerIslandKey(ownerUuid));

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

            invalidateCompositeKey(pipeline, islandCoreKey(islandUuid));

            invalidateCompositeKey(pipeline, islandPlayersKey(islandUuid));
            invalidateCompositeKey(pipeline, islandBansKey(islandUuid));
            invalidateCompositeKey(pipeline, islandCoopsKey(islandUuid));
            invalidateCompositeKey(pipeline, islandUpgradesKey(islandUuid));

            for (UUID playerUuid : playersBeforeDelete.keySet()) {
                invalidateScalarKey(pipeline, playerIslandKey(playerUuid));

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
        return getOptionalStringObject(playerIslandKey(playerUuid), () -> database.getIslandUuid(playerUuid).map(UUID::toString)).map(value -> parseRequiredUuid(value, "playerIsland.islandUuid"));
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

            invalidateScalarKey(pipeline, playerNameKey(uuid));

            if (oldName.isPresent() && !oldName.get().isEmpty()) {
                invalidateScalarKey(pipeline, playerUuidKey(oldName.get()));
            }

            invalidateScalarKey(pipeline, playerUuidKey(name));
            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis invalidation failed while updating player uuid mapping: uuid=" + uuid + ", name=" + name, e);
        }
    }

    public Optional<UUID> getPlayerUuid(String name) {
        if (name == null || name.isEmpty()) {
            return Optional.empty();
        }

        return getOptionalStringObject(playerUuidKey(name), () -> database.getPlayerUuid(name).map(UUID::toString)).map(value -> parseRequiredUuid(value, "playerUuid.uuid"));
    }

    public Optional<String> getPlayerName(UUID uuid) {
        return getOptionalStringObject(playerNameKey(uuid), () -> database.getPlayerName(uuid));
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

        String[] keys = new String[ordered.size()];
        for (int i = 0; i < ordered.size(); i++) {
            keys[i] = playerNameKey(ordered.get(i));
        }

        Map<UUID, String> result = new HashMap<>((int) (ordered.size() / 0.75f) + 1);
        List<UUID> misses = new ArrayList<>();

        try (Jedis jedis = redisHandler.getJedis()) {
            List<String> names = jedis.mget(keys);

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
            List<String> missKeys = new ArrayList<>(misses.size());
            for (UUID miss : misses) {
                missKeys.add(playerNameKey(miss));
            }

            Map<String, String> expectedVersions = readCacheVersions(missKeys);
            Map<UUID, String> loaded = database.getPlayerNames(misses);
            if (loaded == null) {
                loaded = Collections.emptyMap();
            }

            try (Jedis jedis = redisHandler.getJedis()) {
                for (UUID miss : misses) {
                    String key = playerNameKey(miss);
                    String expectedVersion = expectedVersions.get(key);
                    String loadedName = loaded.get(miss);

                    if (loadedName == null || loadedName.isEmpty()) {
                        if (expectedVersion != null) {
                            writeStringObjectCache(jedis, key, NULL_MARKER, false, expectedVersion);
                        }
                        continue;
                    }

                    result.put(miss, loadedName);

                    if (expectedVersion != null) {
                        writeStringObjectCache(jedis, key, loadedName, true, expectedVersion);
                    }
                }
            } catch (Exception e) {
                plugin.severe("Failed to populate player names cache from DB, size=" + loaded.size(), e);
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
            Pipeline pipeline = jedis.pipelined();
            invalidateCompositeKey(pipeline, islandCoreKey(islandUuid));
            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis invalidation failed while updating island lock: island=" + islandUuid + ", lock=" + lock, e);
        }
    }

    public boolean isIslandLock(UUID islandUuid) {
        return parseBool(getIslandCoreMap(islandUuid).get("lock"));
    }

    public void updateIslandPvp(UUID islandUuid, boolean pvp) {
        database.updateIslandPvp(islandUuid, pvp);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();
            invalidateCompositeKey(pipeline, islandCoreKey(islandUuid));
            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis invalidation failed while updating island pvp: island=" + islandUuid + ", pvp=" + pvp, e);
        }
    }

    public boolean isIslandPvp(UUID islandUuid) {
        return parseBool(getIslandCoreMap(islandUuid).get("pvp"));
    }

    public void updateIslandLevel(UUID islandUuid, int level) {
        database.updateIslandLevel(islandUuid, level);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();
            invalidateCompositeKey(pipeline, islandCoreKey(islandUuid));
            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis invalidation failed while updating island level: island=" + islandUuid + ", level=" + level, e);
        }
    }

    public int getIslandLevel(UUID islandUuid) {
        return parseOptionalInt(getIslandCoreMap(islandUuid).get("level")).orElse(0);
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

            invalidateScalarKey(pipeline, playerIslandKey(playerUuid));

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

            invalidateScalarKey(pipeline, playerIslandKey(playerUuid));

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
            invalidateCompositeKey(pipeline, islandCoreKey(islandUuid));
            invalidateCompositeKey(pipeline, islandPlayersKey(islandUuid));
            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis invalidation failed while updating island owner: island=" + islandUuid + ", oldOwner=" + oldOwnerUuid + ", newOwner=" + newOwnerUuid, e);
        }
    }

    public UUID getIslandOwner(UUID islandUuid) {
        return parseOptionalUuid(getIslandCoreMap(islandUuid).get("owner")).orElseThrow(() -> new IllegalStateException("Island owner does not exist for island: " + islandUuid));
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

    public Set<UUID> getPlayerCoopedIslands(UUID playerUuid) {
        Set<UUID> islands = database.getPlayerCoopedIslands(playerUuid);
        return islands.isEmpty() ? Set.of() : Set.copyOf(islands);
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
