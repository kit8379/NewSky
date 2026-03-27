package org.me.newsky.cache;

import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.model.Island;
import org.me.newsky.model.IslandTop;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.state.ServerHeartbeatState;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.*;

public final class DataCache {

    private static final String DATA_PREFIX = "newsky:data:";

    private static final String PLAYER_ISLAND_KEY = DATA_PREFIX + "player:island";
    private static final String PLAYER_NAME_KEY = DATA_PREFIX + "player:name";
    private static final String PLAYER_UUID_KEY = DATA_PREFIX + "player:uuid";

    private static final String LUA_UPDATE_ISLAND_OWNER = "local playersKey = KEYS[1] " + "local coreKey = KEYS[2] " + "local playerIslandKey = KEYS[3] " + "local islandUuid = ARGV[1] " + "local oldOwnerUuid = ARGV[2] " + "local newOwnerUuid = ARGV[3] " + "local currentOwner = redis.call('HGET', coreKey, 'owner') " + "if currentOwner ~= oldOwnerUuid then " + "    return 0 " + "end " + "local newOwnerRole = redis.call('HGET', playersKey, newOwnerUuid) " + "if not newOwnerRole then " + "    return -1 " + "end " + "redis.call('HSET', playersKey, oldOwnerUuid, 'member') " + "redis.call('HSET', playersKey, newOwnerUuid, 'owner') " + "redis.call('HSET', coreKey, 'owner', newOwnerUuid) " + "redis.call('HSET', playerIslandKey, newOwnerUuid, islandUuid) " + "return 1";
    private static final String LUA_DELETE_ALL_COOP_OF_PLAYER = "local reverseKey = KEYS[1] " + "local dataPrefix = ARGV[1] " + "local playerUuid = ARGV[2] " + "local islands = redis.call('SMEMBERS', reverseKey) " + "for i = 1, #islands do " + "    local coopKey = dataPrefix .. 'island:' .. islands[i] .. ':coops' " + "    redis.call('SREM', coopKey, playerUuid) " + "end " + "redis.call('DEL', reverseKey) " + "return islands";

    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final DatabaseHandler database;

    public DataCache(NewSky plugin, RedisHandler redisHandler, DatabaseHandler database, ServerHeartbeatState serverHeartbeatState) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.database = database;

        if (serverHeartbeatState.getActiveServers().isEmpty()) {
            plugin.debug("DataCache", "Server heartbeat indicates this is the only active server, performing full Redis data cache bootstrap.");
            cacheAll();
        } else {
            plugin.debug("DataCache", "Server heartbeat indicates there are already active servers, skipping full Redis data cache bootstrap.");
        }
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

    public String playerCoopedIslandsKey(UUID playerUuid) {
        return DATA_PREFIX + "player:cooped_islands:" + playerUuid;
    }

    private String asBool(boolean value) {
        return value ? "1" : "0";
    }

    private boolean parseBool(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private OptionalInt parseOptionalInt(String value) {
        if (value == null || value.isEmpty()) {
            return OptionalInt.empty();
        }

        try {
            return OptionalInt.of(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid integer value in DataCache: " + value, e);
        }
    }

    private int parseRequiredInt(String value, @SuppressWarnings("SameParameterValue") String fieldName) {
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing integer value in DataCache for field: " + fieldName);
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid integer value in DataCache for field " + fieldName + ": " + value, e);
        }
    }

    private Optional<UUID> parseOptionalUuid(String value) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid UUID value in DataCache: " + value, e);
        }
    }

    private UUID parseRequiredUuid(String value, @SuppressWarnings("SameParameterValue") String fieldName) {
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing UUID value in DataCache for field: " + fieldName);
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid UUID value in DataCache for field " + fieldName + ": " + value, e);
        }
    }

    // =================================================================================================================
    // Full bootstrap
    // =================================================================================================================

    public void cacheAll() {
        plugin.debug("DataCache", "Starting Redis data cache from MySQL...");

        flushData();
        cacheIslandCore();
        cacheIslandPlayers();
        cacheIslandHomes();
        cacheIslandWarps();
        cacheIslandBans();
        cacheIslandCoops();
        cacheIslandLevels();
        cacheIslandUpgrades();
        cachePlayerUuid();

        plugin.debug("DataCache", "Redis data cache finished.");
    }

    private void flushData() {
        plugin.debug("DataCache", "Flushing Redis keys under prefix: " + DATA_PREFIX);

        try (Jedis jedis = redisHandler.getJedis()) {
            String cursor = ScanParams.SCAN_POINTER_START;
            ScanParams params = new ScanParams().match(DATA_PREFIX + "*").count(500);

            do {
                ScanResult<String> result = jedis.scan(cursor, params);

                if (!result.getResult().isEmpty()) {
                    Pipeline p = jedis.pipelined();
                    for (String key : result.getResult()) {
                        p.del(key);
                    }
                    p.sync();
                }

                cursor = result.getCursor();
            } while (!"0".equals(cursor));

        } catch (Exception e) {
            plugin.severe("Failed to flush Redis data cache.", e);
            throw new RuntimeException(e);
        }

        plugin.debug("DataCache", "Redis data cache flush completed.");
    }

    private void cacheIslandCore() {
        plugin.debug("DataCache", "Caching island core data...");

        try (Jedis jedis = redisHandler.getJedis()) {
            database.selectAllIslandData(rs -> {
                Pipeline p = jedis.pipelined();

                while (rs.next()) {
                    UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                    p.hset(islandCoreKey(islandUuid), "lock", rs.getBoolean("lock") ? "1" : "0");
                    p.hset(islandCoreKey(islandUuid), "pvp", rs.getBoolean("pvp") ? "1" : "0");
                }

                p.sync();
            });
        } catch (Exception e) {
            plugin.severe("Failed to load island core cache.", e);
            throw new RuntimeException(e);
        }

        plugin.debug("DataCache", "Island core data cache completed.");
    }

    private void cacheIslandPlayers() {
        plugin.debug("DataCache", "Caching island players data...");

        try (Jedis jedis = redisHandler.getJedis()) {
            database.selectAllIslandPlayers(rs -> {
                Pipeline p = jedis.pipelined();

                while (rs.next()) {
                    UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    String role = rs.getString("role");

                    p.hset(islandPlayersKey(islandUuid), playerUuid.toString(), role);
                    p.hset(PLAYER_ISLAND_KEY, playerUuid.toString(), islandUuid.toString());

                    if ("owner".equalsIgnoreCase(role)) {
                        p.hset(islandCoreKey(islandUuid), "owner", playerUuid.toString());
                    }
                }

                p.sync();
            });
        } catch (Exception e) {
            plugin.severe("Failed to load island players cache.", e);
            throw new RuntimeException(e);
        }

        plugin.debug("DataCache", "Island players data cache completed.");
    }

    private void cacheIslandHomes() {
        plugin.debug("DataCache", "Caching island homes data...");

        try (Jedis jedis = redisHandler.getJedis()) {
            database.selectAllIslandHomes(rs -> {
                Pipeline p = jedis.pipelined();

                while (rs.next()) {
                    UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));

                    p.hset(islandHomesKey(islandUuid, playerUuid), rs.getString("home_name"), rs.getString("home_location"));
                }

                p.sync();
            });
        } catch (Exception e) {
            plugin.severe("Failed to load island homes cache.", e);
            throw new RuntimeException(e);
        }

        plugin.debug("DataCache", "Island homes data cache completed.");
    }

    private void cacheIslandWarps() {
        plugin.debug("DataCache", "Caching island warps data...");

        try (Jedis jedis = redisHandler.getJedis()) {
            database.selectAllIslandWarps(rs -> {
                Pipeline p = jedis.pipelined();

                while (rs.next()) {
                    UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));

                    p.hset(islandWarpsKey(islandUuid, playerUuid), rs.getString("warp_name"), rs.getString("warp_location"));
                }

                p.sync();
            });
        } catch (Exception e) {
            plugin.severe("Failed to load island warps cache.", e);
            throw new RuntimeException(e);
        }

        plugin.debug("DataCache", "Island warps data cache completed.");
    }

    private void cacheIslandBans() {
        plugin.debug("DataCache", "Caching island bans data...");

        try (Jedis jedis = redisHandler.getJedis()) {
            database.selectAllIslandBans(rs -> {
                Pipeline p = jedis.pipelined();

                while (rs.next()) {
                    UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                    p.sadd(islandBansKey(islandUuid), rs.getString("banned_player"));
                }

                p.sync();
            });
        } catch (Exception e) {
            plugin.severe("Failed to load island bans cache.", e);
            throw new RuntimeException(e);
        }

        plugin.debug("DataCache", "Island bans data cache completed.");
    }

    private void cacheIslandCoops() {
        plugin.debug("DataCache", "Caching island coops data...");

        try (Jedis jedis = redisHandler.getJedis()) {
            database.selectAllIslandCoops(rs -> {
                Pipeline p = jedis.pipelined();

                while (rs.next()) {
                    UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                    UUID playerUuid = UUID.fromString(rs.getString("cooped_player"));

                    p.sadd(islandCoopsKey(islandUuid), playerUuid.toString());
                    p.sadd(playerCoopedIslandsKey(playerUuid), islandUuid.toString());
                }

                p.sync();
            });
        } catch (Exception e) {
            plugin.severe("Failed to load island coops cache.", e);
            throw new RuntimeException(e);
        }

        plugin.debug("DataCache", "Island coops data cache completed.");
    }

    private void cacheIslandLevels() {
        plugin.debug("DataCache", "Caching island levels data...");

        try (Jedis jedis = redisHandler.getJedis()) {
            database.selectAllIslandLevels(rs -> {
                Pipeline p = jedis.pipelined();

                while (rs.next()) {
                    UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                    p.hset(islandCoreKey(islandUuid), "level", String.valueOf(rs.getInt("level")));
                }

                p.sync();
            });
        } catch (Exception e) {
            plugin.severe("Failed to load island level cache.", e);
            throw new RuntimeException(e);
        }

        plugin.debug("DataCache", "Island levels data cache completed.");
    }

    private void cacheIslandUpgrades() {
        plugin.debug("DataCache", "Caching island upgrades data...");

        try (Jedis jedis = redisHandler.getJedis()) {
            database.selectAllIslandUpgrades(rs -> {
                Pipeline p = jedis.pipelined();

                while (rs.next()) {
                    int level = rs.getInt("level");
                    if (level <= 1) {
                        continue;
                    }

                    UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                    p.hset(islandUpgradesKey(islandUuid), rs.getString("upgrade_id"), String.valueOf(level));
                }

                p.sync();
            });
        } catch (Exception e) {
            plugin.severe("Failed to load island upgrades cache.", e);
            throw new RuntimeException(e);
        }

        plugin.debug("DataCache", "Island upgrades data cache completed.");
    }

    private void cachePlayerUuid() {
        plugin.debug("DataCache", "Caching player UUID <-> name mapping...");

        try (Jedis jedis = redisHandler.getJedis()) {
            database.selectAllPlayerUuid(rs -> {
                Pipeline p = jedis.pipelined();

                while (rs.next()) {
                    String uuid = rs.getString("uuid");
                    String name = rs.getString("name");

                    p.hset(PLAYER_NAME_KEY, uuid, name);
                    p.hset(PLAYER_UUID_KEY, name.toLowerCase(Locale.ROOT), uuid);
                }

                p.sync();
            });
        } catch (Exception e) {
            plugin.severe("Failed to load player uuid cache.", e);
            throw new RuntimeException(e);
        }

        plugin.debug("DataCache", "Player UUID <-> name mapping cache completed.");
    }

    // =================================================================================================================
    // Island lifecycle (DB first -> Redis)
    // =================================================================================================================

    public void createIsland(UUID islandUuid, UUID ownerUuid, String homeLocation) {
        database.addIslandData(islandUuid, ownerUuid, homeLocation);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline p = jedis.pipelined();

            Map<String, String> core = new HashMap<>();
            core.put("lock", "0");
            core.put("pvp", "0");
            core.put("level", "0");
            core.put("owner", ownerUuid.toString());

            p.hset(islandCoreKey(islandUuid), core);
            p.hset(islandPlayersKey(islandUuid), ownerUuid.toString(), "owner");
            p.hset(PLAYER_ISLAND_KEY, ownerUuid.toString(), islandUuid.toString());
            p.hset(islandHomesKey(islandUuid, ownerUuid), "default", homeLocation);

            p.sync();
        } catch (Exception e) {
            plugin.severe("Database insert succeeded but Redis sync failed while creating island: island=" + islandUuid + ", owner=" + ownerUuid, e);
            throw new RuntimeException(e);
        }
    }

    public void deleteIsland(UUID islandUuid) {
        database.deleteIsland(islandUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            Map<String, String> players = jedis.hgetAll(islandPlayersKey(islandUuid));
            Set<String> coops = jedis.smembers(islandCoopsKey(islandUuid));

            Pipeline p = jedis.pipelined();

            for (String playerUuid : players.keySet()) {
                UUID player = UUID.fromString(playerUuid);
                p.hdel(PLAYER_ISLAND_KEY, playerUuid);
                p.del(islandHomesKey(islandUuid, player));
                p.del(islandWarpsKey(islandUuid, player));
            }

            for (String coopUuid : coops) {
                UUID coopPlayer = UUID.fromString(coopUuid);
                p.srem(playerCoopedIslandsKey(coopPlayer), islandUuid.toString());
            }

            p.del(islandCoreKey(islandUuid));
            p.del(islandPlayersKey(islandUuid));
            p.del(islandCoopsKey(islandUuid));
            p.del(islandBansKey(islandUuid));
            p.del(islandUpgradesKey(islandUuid));

            p.sync();
        } catch (Exception e) {
            plugin.severe("Database delete succeeded but Redis sync failed while deleting island: island=" + islandUuid, e);
            throw new RuntimeException(e);
        }
    }

    // =================================================================================================================
    // Player -> island index
    // =================================================================================================================

    public Optional<UUID> getIslandUuid(UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.hget(PLAYER_ISLAND_KEY, playerUuid.toString());
            return parseOptionalUuid(value);
        } catch (Exception e) {
            plugin.severe("Failed to get island UUID for player: " + playerUuid, e);
            throw new RuntimeException(e);
        }
    }

    // =================================================================================================================
    // Player UUID / Name
    // =================================================================================================================

    public void updatePlayerUuid(UUID uuid, String name) {
        database.updatePlayerName(uuid, name);

        String lower = name.toLowerCase(Locale.ROOT);

        try (Jedis jedis = redisHandler.getJedis()) {
            String oldName = jedis.hget(PLAYER_NAME_KEY, uuid.toString());

            Pipeline p = jedis.pipelined();

            if (oldName != null && !oldName.equalsIgnoreCase(name)) {
                p.hdel(PLAYER_UUID_KEY, oldName.toLowerCase(Locale.ROOT));
            }

            p.hset(PLAYER_NAME_KEY, uuid.toString(), name);
            p.hset(PLAYER_UUID_KEY, lower, uuid.toString());

            p.sync();
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis sync failed while updating player name mapping: uuid=" + uuid + ", name=" + name, e);
            throw new RuntimeException(e);
        }
    }

    public Optional<UUID> getPlayerUuid(String name) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.hget(PLAYER_UUID_KEY, name.toLowerCase(Locale.ROOT));
            return parseOptionalUuid(value);
        } catch (Exception e) {
            plugin.severe("Failed to get player UUID for name: " + name, e);
            throw new RuntimeException(e);
        }
    }

    public Optional<String> getPlayerName(UUID uuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return Optional.ofNullable(jedis.hget(PLAYER_NAME_KEY, uuid.toString()));
        } catch (Exception e) {
            plugin.severe("Failed to get player name for: " + uuid, e);
            throw new RuntimeException(e);
        }
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

            try (Jedis jedis = redisHandler.getJedis()) {
                String name = jedis.hget(PLAYER_NAME_KEY, only.toString());
                if (name == null) {
                    return Collections.emptyMap();
                }

                Map<UUID, String> result = new HashMap<>(1);
                result.put(only, name);
                return result;
            } catch (Exception e) {
                plugin.severe("Failed to get player name for single UUID: " + only, e);
                throw new RuntimeException(e);
            }
        }

        final List<UUID> orderedUuids;

        if (uuids instanceof Set<?>) {
            orderedUuids = new ArrayList<>(uuids.size());
            for (UUID uuid : uuids) {
                if (uuid != null) {
                    orderedUuids.add(uuid);
                }
            }
        } else {
            orderedUuids = new ArrayList<>(uuids.size());
            Set<UUID> seen = new HashSet<>((int) (uuids.size() / 0.75f) + 1);

            for (UUID uuid : uuids) {
                if (uuid != null && seen.add(uuid)) {
                    orderedUuids.add(uuid);
                }
            }
        }

        if (orderedUuids.isEmpty()) {
            return Collections.emptyMap();
        }

        String[] fields = new String[orderedUuids.size()];
        for (int i = 0; i < orderedUuids.size(); i++) {
            fields[i] = orderedUuids.get(i).toString();
        }

        try (Jedis jedis = redisHandler.getJedis()) {
            List<String> names = jedis.hmget(PLAYER_NAME_KEY, fields);
            Map<UUID, String> result = new HashMap<>((int) (orderedUuids.size() / 0.75f) + 1);

            for (int i = 0; i < orderedUuids.size(); i++) {
                String name = names.get(i);
                if (name != null) {
                    result.put(orderedUuids.get(i), name);
                }
            }

            return result;
        } catch (Exception e) {
            plugin.severe("Failed to get player names for UUID collection, size=" + orderedUuids.size(), e);
            throw new RuntimeException(e);
        }
    }

    // =================================================================================================================
    // Island core
    // =================================================================================================================

    public void updateIslandLock(UUID islandUuid, boolean lock) {
        database.updateIslandLock(islandUuid, lock);

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset(islandCoreKey(islandUuid), "lock", asBool(lock));
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis sync failed while updating island lock: island=" + islandUuid + ", lock=" + lock, e);
            throw new RuntimeException(e);
        }
    }

    public boolean isIslandLock(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return parseBool(jedis.hget(islandCoreKey(islandUuid), "lock"));
        } catch (Exception e) {
            plugin.severe("Failed to get island lock for: " + islandUuid, e);
            throw new RuntimeException(e);
        }
    }

    public void updateIslandPvp(UUID islandUuid, boolean pvp) {
        database.updateIslandPvp(islandUuid, pvp);

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset(islandCoreKey(islandUuid), "pvp", asBool(pvp));
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis sync failed while updating island pvp: island=" + islandUuid + ", pvp=" + pvp, e);
            throw new RuntimeException(e);
        }
    }

    public boolean isIslandPvp(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return parseBool(jedis.hget(islandCoreKey(islandUuid), "pvp"));
        } catch (Exception e) {
            plugin.severe("Failed to get island pvp for: " + islandUuid, e);
            throw new RuntimeException(e);
        }
    }

    public void updateIslandLevel(UUID islandUuid, int level) {
        database.updateIslandLevel(islandUuid, level);

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset(islandCoreKey(islandUuid), "level", String.valueOf(level));
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis sync failed while updating island level: island=" + islandUuid + ", level=" + level, e);
            throw new RuntimeException(e);
        }
    }

    public int getIslandLevel(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return parseOptionalInt(jedis.hget(islandCoreKey(islandUuid), "level")).orElse(0);
        } catch (Exception e) {
            plugin.severe("Failed to get island level for: " + islandUuid, e);
            throw new RuntimeException(e);
        }
    }

    public List<IslandTop> getTopIslandLevels(int limit) {
        int safeLimit = Math.max(1, limit);

        Comparator<IslandTop> comparator = Comparator.comparingInt(IslandTop::getLevel).thenComparing(top -> top.getIslandUuid().toString());

        try (Jedis jedis = redisHandler.getJedis()) {
            String cursor = ScanParams.SCAN_POINTER_START;
            ScanParams params = new ScanParams().match(DATA_PREFIX + "island:*:core").count(500);

            int prefixLength = (DATA_PREFIX + "island:").length();
            int suffixLength = ":core".length();

            PriorityQueue<IslandTop> topQueue = new PriorityQueue<>(safeLimit, comparator);

            do {
                ScanResult<String> scan = jedis.scan(cursor, params);
                List<String> keys = scan.getResult();

                if (!keys.isEmpty()) {
                    Pipeline pipeline = jedis.pipelined();
                    List<redis.clients.jedis.Response<List<String>>> coreResponses = new ArrayList<>(keys.size());

                    for (String key : keys) {
                        coreResponses.add(pipeline.hmget(key, "owner", "level"));
                    }

                    pipeline.sync();

                    for (int i = 0; i < keys.size(); i++) {
                        String key = keys.get(i);
                        if (key == null || key.length() <= prefixLength + suffixLength) {
                            continue;
                        }

                        Optional<UUID> islandUuid = parseOptionalUuid(key.substring(prefixLength, key.length() - suffixLength));
                        if (islandUuid.isEmpty()) {
                            continue;
                        }

                        List<String> core = coreResponses.get(i).get();
                        if (core == null || core.size() < 2) {
                            continue;
                        }

                        Optional<UUID> ownerUuid = parseOptionalUuid(core.get(0));
                        if (ownerUuid.isEmpty()) {
                            continue;
                        }

                        int level = parseOptionalInt(core.get(1)).orElse(0);
                        IslandTop top = new IslandTop(islandUuid.get(), ownerUuid.get(), level, Set.of());

                        topQueue.offer(top);
                        if (topQueue.size() > safeLimit) {
                            topQueue.poll();
                        }
                    }
                }

                cursor = scan.getCursor();
            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));

            if (topQueue.isEmpty()) {
                return List.of();
            }

            List<IslandTop> topIslands = new ArrayList<>(topQueue);
            topIslands.sort(comparator.reversed());

            Pipeline pipeline = jedis.pipelined();
            List<redis.clients.jedis.Response<Map<String, String>>> memberResponses = new ArrayList<>(topIslands.size());

            for (IslandTop islandTop : topIslands) {
                memberResponses.add(pipeline.hgetAll(islandPlayersKey(islandTop.getIslandUuid())));
            }

            pipeline.sync();

            List<IslandTop> result = new ArrayList<>(topIslands.size());

            for (int i = 0; i < topIslands.size(); i++) {
                IslandTop islandTop = topIslands.get(i);
                Map<String, String> players = memberResponses.get(i).get();

                if (players == null || players.isEmpty()) {
                    result.add(new IslandTop(islandTop.getIslandUuid(), islandTop.getOwnerUuid(), islandTop.getLevel(), Set.of()));
                    continue;
                }

                Set<UUID> members = new LinkedHashSet<>();
                for (Map.Entry<String, String> entry : players.entrySet()) {
                    if ("member".equalsIgnoreCase(entry.getValue())) {
                        members.add(parseRequiredUuid(entry.getKey(), "islandPlayers.memberUuid"));
                    }
                }

                result.add(new IslandTop(islandTop.getIslandUuid(), islandTop.getOwnerUuid(), islandTop.getLevel(), members.isEmpty() ? Set.of() : Set.copyOf(members)));
            }

            return result;
        } catch (Exception e) {
            plugin.severe("Failed to get top island levels", e);
            throw new RuntimeException(e);
        }
    }

    // =================================================================================================================
    // Players / owner / members
    // =================================================================================================================

    public void updateIslandPlayer(UUID islandUuid, UUID playerUuid, String role, String homeLocation) {
        database.addIslandPlayer(islandUuid, playerUuid, role, homeLocation);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline p = jedis.pipelined();
            p.hset(islandPlayersKey(islandUuid), playerUuid.toString(), role);
            p.hset(PLAYER_ISLAND_KEY, playerUuid.toString(), islandUuid.toString());
            p.hset(islandHomesKey(islandUuid, playerUuid), "default", homeLocation);
            p.sync();
        } catch (Exception e) {
            plugin.severe("Database insert succeeded but Redis sync failed while adding island player: island=" + islandUuid + ", player=" + playerUuid + ", role=" + role, e);
            throw new RuntimeException(e);
        }
    }

    public void deleteIslandPlayer(UUID islandUuid, UUID playerUuid) {
        database.deleteIslandPlayer(islandUuid, playerUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline p = jedis.pipelined();
            p.hdel(islandPlayersKey(islandUuid), playerUuid.toString());
            p.hdel(PLAYER_ISLAND_KEY, playerUuid.toString());
            p.del(islandHomesKey(islandUuid, playerUuid));
            p.del(islandWarpsKey(islandUuid, playerUuid));
            p.sync();
        } catch (Exception e) {
            plugin.severe("Database delete succeeded but Redis sync failed while deleting island player: island=" + islandUuid + ", player=" + playerUuid, e);
            throw new RuntimeException(e);
        }
    }

    public void updateIslandOwner(UUID islandUuid, UUID oldOwnerUuid, UUID newOwnerUuid) {
        database.updateIslandOwner(islandUuid, oldOwnerUuid, newOwnerUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            Object result = jedis.eval(LUA_UPDATE_ISLAND_OWNER, 3, islandPlayersKey(islandUuid), islandCoreKey(islandUuid), PLAYER_ISLAND_KEY, islandUuid.toString(), oldOwnerUuid.toString(), newOwnerUuid.toString());

            long code = (result instanceof Long) ? (Long) result : -999L;

            if (code == 1L) {
                return;
            }
            if (code == 0L) {
                throw new IllegalStateException("Failed to update island owner because the current owner does not match: island=" + islandUuid + ", expectedOldOwner=" + oldOwnerUuid);
            }
            if (code == -1L) {
                throw new IllegalStateException("Failed to update island owner because the new owner is not in island players: island=" + islandUuid + ", newOwner=" + newOwnerUuid);
            }

            throw new IllegalStateException("Unexpected Lua result while updating island owner: " + code);
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis sync failed while updating island owner: island=" + islandUuid + ", oldOwner=" + oldOwnerUuid + ", newOwner=" + newOwnerUuid, e);
            throw new RuntimeException(e);
        }
    }

    public UUID getIslandOwner(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.hget(islandCoreKey(islandUuid), "owner");
            return parseRequiredUuid(value, "islandCore.owner");
        } catch (Exception e) {
            plugin.severe("Failed to get island owner for: " + islandUuid, e);
            throw new RuntimeException(e);
        }
    }

    public Set<UUID> getIslandMembers(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Map<String, String> players = jedis.hgetAll(islandPlayersKey(islandUuid));
            if (players == null || players.isEmpty()) {
                return Set.of();
            }

            Set<UUID> result = new HashSet<>();

            for (Map.Entry<String, String> entry : players.entrySet()) {
                if (!"member".equalsIgnoreCase(entry.getValue())) {
                    continue;
                }
                result.add(parseRequiredUuid(entry.getKey(), "islandPlayers.memberUuid"));
            }

            return result.isEmpty() ? Set.of() : Set.copyOf(result);
        } catch (Exception e) {
            plugin.severe("Failed to get island members for: " + islandUuid, e);
            throw new RuntimeException(e);
        }
    }

    public Set<UUID> getIslandPlayers(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Set<String> rawPlayers = jedis.hkeys(islandPlayersKey(islandUuid));
            if (rawPlayers == null || rawPlayers.isEmpty()) {
                return Set.of();
            }

            Set<UUID> result = new HashSet<>(rawPlayers.size());
            for (String rawPlayer : rawPlayers) {
                result.add(parseRequiredUuid(rawPlayer, "islandPlayers.playerUuid"));
            }

            return Set.copyOf(result);
        } catch (Exception e) {
            plugin.severe("Failed to get island players for: " + islandUuid, e);
            throw new RuntimeException(e);
        }
    }

    // =================================================================================================================
    // Homes
    // =================================================================================================================

    public void updateHomePoint(UUID islandUuid, UUID playerUuid, String homeName, String homeLocation) {
        database.updateHomePoint(islandUuid, playerUuid, homeName, homeLocation);

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset(islandHomesKey(islandUuid, playerUuid), homeName, homeLocation);
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis sync failed while updating home point: island=" + islandUuid + ", player=" + playerUuid + ", home=" + homeName, e);
            throw new RuntimeException(e);
        }
    }

    public void deleteHomePoint(UUID islandUuid, UUID playerUuid, String homeName) {
        database.deleteHomePoint(islandUuid, playerUuid, homeName);

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel(islandHomesKey(islandUuid, playerUuid), homeName);
        } catch (Exception e) {
            plugin.severe("Database delete succeeded but Redis sync failed while deleting home point: island=" + islandUuid + ", player=" + playerUuid + ", home=" + homeName, e);
            throw new RuntimeException(e);
        }
    }

    public Optional<String> getHomeLocation(UUID islandUuid, UUID playerUuid, String homeName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return Optional.ofNullable(jedis.hget(islandHomesKey(islandUuid, playerUuid), homeName));
        } catch (Exception e) {
            plugin.severe("Failed to get home location: island=" + islandUuid + ", player=" + playerUuid + ", home=" + homeName, e);
            throw new RuntimeException(e);
        }
    }

    public Set<String> getHomeNames(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Set<String> names = jedis.hkeys(islandHomesKey(islandUuid, playerUuid));
            return names == null || names.isEmpty() ? Set.of() : Set.copyOf(names);
        } catch (Exception e) {
            plugin.severe("Failed to get home names: island=" + islandUuid + ", player=" + playerUuid, e);
            throw new RuntimeException(e);
        }
    }

    // =================================================================================================================
    // Warps
    // =================================================================================================================

    public void updateWarpPoint(UUID islandUuid, UUID playerUuid, String warpName, String warpLocation) {
        database.updateWarpPoint(islandUuid, playerUuid, warpName, warpLocation);

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset(islandWarpsKey(islandUuid, playerUuid), warpName, warpLocation);
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis sync failed while updating warp point: island=" + islandUuid + ", player=" + playerUuid + ", warp=" + warpName, e);
            throw new RuntimeException(e);
        }
    }

    public void deleteWarpPoint(UUID islandUuid, UUID playerUuid, String warpName) {
        database.deleteWarpPoint(islandUuid, playerUuid, warpName);

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel(islandWarpsKey(islandUuid, playerUuid), warpName);
        } catch (Exception e) {
            plugin.severe("Database delete succeeded but Redis sync failed while deleting warp point: island=" + islandUuid + ", player=" + playerUuid + ", warp=" + warpName, e);
            throw new RuntimeException(e);
        }
    }

    public Optional<String> getWarpLocation(UUID islandUuid, UUID playerUuid, String warpName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return Optional.ofNullable(jedis.hget(islandWarpsKey(islandUuid, playerUuid), warpName));
        } catch (Exception e) {
            plugin.severe("Failed to get warp location: island=" + islandUuid + ", player=" + playerUuid + ", warp=" + warpName, e);
            throw new RuntimeException(e);
        }
    }

    public Set<String> getWarpNames(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Set<String> names = jedis.hkeys(islandWarpsKey(islandUuid, playerUuid));
            return names == null || names.isEmpty() ? Set.of() : Set.copyOf(names);
        } catch (Exception e) {
            plugin.severe("Failed to get warp names: island=" + islandUuid + ", player=" + playerUuid, e);
            throw new RuntimeException(e);
        }
    }

    // =================================================================================================================
    // Bans
    // =================================================================================================================

    public void updateBanPlayer(UUID islandUuid, UUID playerUuid) {
        database.updateBanPlayer(islandUuid, playerUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.sadd(islandBansKey(islandUuid), playerUuid.toString());
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis sync failed while adding banned player: island=" + islandUuid + ", player=" + playerUuid, e);
            throw new RuntimeException(e);
        }
    }

    public void deleteBanPlayer(UUID islandUuid, UUID playerUuid) {
        database.deleteBanPlayer(islandUuid, playerUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.srem(islandBansKey(islandUuid), playerUuid.toString());
        } catch (Exception e) {
            plugin.severe("Database delete succeeded but Redis sync failed while deleting banned player: island=" + islandUuid + ", player=" + playerUuid, e);
            throw new RuntimeException(e);
        }
    }

    public boolean isPlayerBanned(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.sismember(islandBansKey(islandUuid), playerUuid.toString());
        } catch (Exception e) {
            plugin.severe("Failed to check banned player: island=" + islandUuid + ", player=" + playerUuid, e);
            throw new RuntimeException(e);
        }
    }

    public Set<UUID> getBannedPlayers(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Set<String> rawPlayers = jedis.smembers(islandBansKey(islandUuid));
            if (rawPlayers == null || rawPlayers.isEmpty()) {
                return Set.of();
            }

            Set<UUID> result = new HashSet<>(rawPlayers.size());
            for (String rawPlayer : rawPlayers) {
                result.add(parseRequiredUuid(rawPlayer, "islandBans.playerUuid"));
            }

            return Set.copyOf(result);
        } catch (Exception e) {
            plugin.severe("Failed to get banned players for island: " + islandUuid, e);
            throw new RuntimeException(e);
        }
    }

    // =================================================================================================================
    // Coops
    // =================================================================================================================

    public void updateCoopPlayer(UUID islandUuid, UUID playerUuid) {
        database.updateCoopPlayer(islandUuid, playerUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline p = jedis.pipelined();
            p.sadd(islandCoopsKey(islandUuid), playerUuid.toString());
            p.sadd(playerCoopedIslandsKey(playerUuid), islandUuid.toString());
            p.sync();
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis sync failed while adding coop player: island=" + islandUuid + ", player=" + playerUuid, e);
            throw new RuntimeException(e);
        }
    }

    public void deleteCoopPlayer(UUID islandUuid, UUID playerUuid) {
        database.deleteCoopPlayer(islandUuid, playerUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline p = jedis.pipelined();
            p.srem(islandCoopsKey(islandUuid), playerUuid.toString());
            p.srem(playerCoopedIslandsKey(playerUuid), islandUuid.toString());
            p.sync();
        } catch (Exception e) {
            plugin.severe("Database delete succeeded but Redis sync failed while deleting coop player: island=" + islandUuid + ", player=" + playerUuid, e);
            throw new RuntimeException(e);
        }
    }

    public Set<UUID> deleteAllCoopOfPlayer(UUID playerUuid) {
        database.deleteAllCoopOfPlayer(playerUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            Object result = jedis.eval(LUA_DELETE_ALL_COOP_OF_PLAYER, 1, playerCoopedIslandsKey(playerUuid), DATA_PREFIX, playerUuid.toString());

            if (!(result instanceof List<?> rawList)) {
                throw new IllegalStateException("Unexpected Lua result while deleting all coop of player: " + playerUuid + ", result=" + result);
            }

            Set<UUID> touchedIslands = new HashSet<>();
            for (Object raw : rawList) {
                if (raw != null) {
                    touchedIslands.add(parseRequiredUuid(String.valueOf(raw), "deleteAllCoopOfPlayer.islandUuid"));
                }
            }

            return touchedIslands.isEmpty() ? Set.of() : Set.copyOf(touchedIslands);
        } catch (Exception e) {
            plugin.severe("Database delete succeeded but Redis sync failed while deleting all coop of player: player=" + playerUuid, e);
            throw new RuntimeException(e);
        }
    }

    public boolean isPlayerCooped(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.sismember(islandCoopsKey(islandUuid), playerUuid.toString());
        } catch (Exception e) {
            plugin.severe("Failed to check cooped player: island=" + islandUuid + ", player=" + playerUuid, e);
            throw new RuntimeException(e);
        }
    }

    public Set<UUID> getCoopedPlayers(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Set<String> rawPlayers = jedis.smembers(islandCoopsKey(islandUuid));
            if (rawPlayers == null || rawPlayers.isEmpty()) {
                return Set.of();
            }

            Set<UUID> result = new HashSet<>(rawPlayers.size());
            for (String rawPlayer : rawPlayers) {
                result.add(parseRequiredUuid(rawPlayer, "islandCoops.playerUuid"));
            }

            return Set.copyOf(result);
        } catch (Exception e) {
            plugin.severe("Failed to get cooped players for island: " + islandUuid, e);
            throw new RuntimeException(e);
        }
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
            if (level <= 1) {
                jedis.hdel(islandUpgradesKey(islandUuid), upgradeId);
            } else {
                jedis.hset(islandUpgradesKey(islandUuid), upgradeId, String.valueOf(level));
            }
        } catch (Exception e) {
            plugin.severe("Database update succeeded but Redis sync failed while updating island upgrade level: island=" + islandUuid + ", upgrade=" + upgradeId + ", level=" + level, e);
            throw new RuntimeException(e);
        }
    }

    public int getIslandUpgradeLevel(UUID islandUuid, String upgradeId) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return parseOptionalInt(jedis.hget(islandUpgradesKey(islandUuid), upgradeId)).orElse(1);
        } catch (Exception e) {
            plugin.severe("Failed to get island upgrade level: island=" + islandUuid + ", upgrade=" + upgradeId, e);
            throw new RuntimeException(e);
        }
    }

    // =================================================================================================================
    // Island Snapshot
    // =================================================================================================================

    public Island getIslandSnapshot(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {

            Map<String, String> core = jedis.hgetAll(islandCoreKey(islandUuid));
            if (core == null || core.isEmpty()) {
                return null;
            }

            boolean lock = parseBool(core.get("lock"));
            boolean pvp = parseBool(core.get("pvp"));
            UUID owner = parseRequiredUuid(core.get("owner"), "islandCore.owner");

            Map<String, String> playersRaw = jedis.hgetAll(islandPlayersKey(islandUuid));
            Set<UUID> members = new HashSet<>();

            for (Map.Entry<String, String> entry : playersRaw.entrySet()) {
                if (!"member".equalsIgnoreCase(entry.getValue())) {
                    continue;
                }
                members.add(parseRequiredUuid(entry.getKey(), "islandPlayers.memberUuid"));
            }

            Set<String> rawCoops = jedis.smembers(islandCoopsKey(islandUuid));
            Set<UUID> coops = new HashSet<>(rawCoops.size());
            for (String rawCoop : rawCoops) {
                coops.add(parseRequiredUuid(rawCoop, "islandCoops.playerUuid"));
            }

            Set<String> rawBans = jedis.smembers(islandBansKey(islandUuid));
            Set<UUID> bans = new HashSet<>(rawBans.size());
            for (String rawBan : rawBans) {
                bans.add(parseRequiredUuid(rawBan, "islandBans.playerUuid"));
            }

            Map<String, String> upgradesRaw = jedis.hgetAll(islandUpgradesKey(islandUuid));
            Map<String, Integer> upgrades = new HashMap<>();

            for (Map.Entry<String, String> entry : upgradesRaw.entrySet()) {
                int upgradeLevel = parseRequiredInt(entry.getValue(), "islandUpgrades.level");
                if (upgradeLevel <= 1) {
                    continue;
                }
                upgrades.put(entry.getKey(), upgradeLevel);
            }

            return new Island(islandUuid, lock, pvp, owner, members.isEmpty() ? Set.of() : Set.copyOf(members), coops.isEmpty() ? Set.of() : Set.copyOf(coops), bans.isEmpty() ? Set.of() : Set.copyOf(bans), upgrades.isEmpty() ? Map.of() : Map.copyOf(upgrades));

        } catch (Exception e) {
            plugin.severe("Failed to load island snapshot from Redis for: " + islandUuid, e);
            throw new RuntimeException(e);
        }
    }
}