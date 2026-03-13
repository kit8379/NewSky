package org.me.newsky.cache;

import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.model.Island;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.*;
import java.util.stream.Collectors;

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

    public DataCache(NewSky plugin, RedisHandler redisHandler, DatabaseHandler database) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.database = database;
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

    private int parseInt(String value, int def) {
        if (value == null) {
            return def;
        }
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return def;
        }
    }

    private Optional<UUID> parseUuid(String value) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (Exception e) {
            plugin.severe("Invalid UUID value in DataCache: " + value, e);
            return Optional.empty();
        }
    }

    private void failRedisSync(String action, Exception e) {
        plugin.severe("Database write succeeded but Redis sync failed for action: " + action, e);
        throw new RuntimeException(e);
    }

    // =================================================================================================================
    // Full bootstrap
    // =================================================================================================================

    public void cacheAll() {
        plugin.debug("DataCache", "Starting Redis data cache rebuild from MySQL...");

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

        plugin.debug("DataCache", "Redis data cache rebuild finished.");
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
    }

    private void cacheIslandCore() {
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
    }

    private void cacheIslandPlayers() {
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
    }

    private void cacheIslandHomes() {
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
    }

    private void cacheIslandWarps() {
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
    }

    private void cacheIslandBans() {
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
    }

    private void cacheIslandCoops() {
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
    }

    private void cacheIslandLevels() {
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
    }

    private void cacheIslandUpgrades() {
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
    }

    private void cachePlayerUuid() {
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
            failRedisSync("createIsland:" + islandUuid, e);
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
            failRedisSync("deleteIsland:" + islandUuid, e);
        }
    }

    // =================================================================================================================
    // Player -> island index
    // =================================================================================================================

    public Optional<UUID> getIslandUuid(UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.hget(PLAYER_ISLAND_KEY, playerUuid.toString());
            return parseUuid(value);
        } catch (Exception e) {
            plugin.severe("Failed to get island UUID for player: " + playerUuid, e);
            return Optional.empty();
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
            failRedisSync("updatePlayerUuid:" + uuid, e);
        }
    }

    public Optional<String> getPlayerName(UUID uuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return Optional.ofNullable(jedis.hget(PLAYER_NAME_KEY, uuid.toString()));
        } catch (Exception e) {
            plugin.severe("Failed to get player name for: " + uuid, e);
            return Optional.empty();
        }
    }

    public Optional<UUID> getPlayerUuid(String name) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.hget(PLAYER_UUID_KEY, name.toLowerCase(Locale.ROOT));
            return parseUuid(value);
        } catch (Exception e) {
            plugin.severe("Failed to get player UUID for name: " + name, e);
            return Optional.empty();
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
            failRedisSync("updateIslandLock:" + islandUuid, e);
        }
    }

    public boolean isIslandLock(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return parseBool(jedis.hget(islandCoreKey(islandUuid), "lock"));
        } catch (Exception e) {
            plugin.severe("Failed to get island lock for: " + islandUuid, e);
            return false;
        }
    }

    public void updateIslandPvp(UUID islandUuid, boolean pvp) {
        database.updateIslandPvp(islandUuid, pvp);

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset(islandCoreKey(islandUuid), "pvp", asBool(pvp));
        } catch (Exception e) {
            failRedisSync("updateIslandPvp:" + islandUuid, e);
        }
    }

    public boolean isIslandPvp(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return parseBool(jedis.hget(islandCoreKey(islandUuid), "pvp"));
        } catch (Exception e) {
            plugin.severe("Failed to get island pvp for: " + islandUuid, e);
            return false;
        }
    }

    public void updateIslandLevel(UUID islandUuid, int level) {
        database.updateIslandLevel(islandUuid, level);

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset(islandCoreKey(islandUuid), "level", String.valueOf(level));
        } catch (Exception e) {
            failRedisSync("updateIslandLevel:" + islandUuid, e);
        }
    }

    public int getIslandLevel(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return parseInt(jedis.hget(islandCoreKey(islandUuid), "level"), 0);
        } catch (Exception e) {
            plugin.severe("Failed to get island level for: " + islandUuid, e);
            return 0;
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
            failRedisSync("updateIslandPlayer:" + islandUuid + ":" + playerUuid, e);
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
            failRedisSync("deleteIslandPlayer:" + islandUuid + ":" + playerUuid, e);
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
            failRedisSync("updateIslandOwner:" + islandUuid, e);
        }
    }

    public UUID getIslandOwner(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.hget(islandCoreKey(islandUuid), "owner");
            return parseUuid(value).orElseThrow(() -> new IllegalStateException("Island owner missing for island: " + islandUuid));
        } catch (Exception e) {
            plugin.severe("Failed to get island owner for: " + islandUuid, e);
            throw new RuntimeException(e);
        }
    }

    public Set<UUID> getIslandMembers(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Map<String, String> players = jedis.hgetAll(islandPlayersKey(islandUuid));
            Set<UUID> result = new HashSet<>();

            for (Map.Entry<String, String> entry : players.entrySet()) {
                if (!"member".equalsIgnoreCase(entry.getValue())) {
                    continue;
                }
                parseUuid(entry.getKey()).ifPresent(result::add);
            }

            return result;
        } catch (Exception e) {
            plugin.severe("Failed to get island members for: " + islandUuid, e);
            return Set.of();
        }
    }

    public Set<UUID> getIslandPlayers(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.hkeys(islandPlayersKey(islandUuid)).stream().map(this::parseUuid).flatMap(Optional::stream).collect(Collectors.toSet());
        } catch (Exception e) {
            plugin.severe("Failed to get island players for: " + islandUuid, e);
            return Set.of();
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
            failRedisSync("updateHomePoint:" + islandUuid + ":" + playerUuid + ":" + homeName, e);
        }
    }

    public void deleteHomePoint(UUID islandUuid, UUID playerUuid, String homeName) {
        database.deleteHomePoint(islandUuid, playerUuid, homeName);

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel(islandHomesKey(islandUuid, playerUuid), homeName);
        } catch (Exception e) {
            failRedisSync("deleteHomePoint:" + islandUuid + ":" + playerUuid + ":" + homeName, e);
        }
    }

    public Optional<String> getHomeLocation(UUID islandUuid, UUID playerUuid, String homeName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return Optional.ofNullable(jedis.hget(islandHomesKey(islandUuid, playerUuid), homeName));
        } catch (Exception e) {
            plugin.severe("Failed to get home location: island=" + islandUuid + ", player=" + playerUuid + ", home=" + homeName, e);
            return Optional.empty();
        }
    }

    public Set<String> getHomeNames(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.hkeys(islandHomesKey(islandUuid, playerUuid));
        } catch (Exception e) {
            plugin.severe("Failed to get home names: island=" + islandUuid + ", player=" + playerUuid, e);
            return Set.of();
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
            failRedisSync("updateWarpPoint:" + islandUuid + ":" + playerUuid + ":" + warpName, e);
        }
    }

    public void deleteWarpPoint(UUID islandUuid, UUID playerUuid, String warpName) {
        database.deleteWarpPoint(islandUuid, playerUuid, warpName);

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel(islandWarpsKey(islandUuid, playerUuid), warpName);
        } catch (Exception e) {
            failRedisSync("deleteWarpPoint:" + islandUuid + ":" + playerUuid + ":" + warpName, e);
        }
    }

    public Optional<String> getWarpLocation(UUID islandUuid, UUID playerUuid, String warpName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return Optional.ofNullable(jedis.hget(islandWarpsKey(islandUuid, playerUuid), warpName));
        } catch (Exception e) {
            plugin.severe("Failed to get warp location: island=" + islandUuid + ", player=" + playerUuid + ", warp=" + warpName, e);
            return Optional.empty();
        }
    }

    public Set<String> getWarpNames(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.hkeys(islandWarpsKey(islandUuid, playerUuid));
        } catch (Exception e) {
            plugin.severe("Failed to get warp names: island=" + islandUuid + ", player=" + playerUuid, e);
            return Set.of();
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
            failRedisSync("updateBanPlayer:" + islandUuid + ":" + playerUuid, e);
        }
    }

    public void deleteBanPlayer(UUID islandUuid, UUID playerUuid) {
        database.deleteBanPlayer(islandUuid, playerUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.srem(islandBansKey(islandUuid), playerUuid.toString());
        } catch (Exception e) {
            failRedisSync("deleteBanPlayer:" + islandUuid + ":" + playerUuid, e);
        }
    }

    public boolean isPlayerBanned(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.sismember(islandBansKey(islandUuid), playerUuid.toString());
        } catch (Exception e) {
            plugin.severe("Failed to check banned player: island=" + islandUuid + ", player=" + playerUuid, e);
            return false;
        }
    }

    public Set<UUID> getBannedPlayers(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.smembers(islandBansKey(islandUuid)).stream().map(this::parseUuid).flatMap(Optional::stream).collect(Collectors.toSet());
        } catch (Exception e) {
            plugin.severe("Failed to get banned players for island: " + islandUuid, e);
            return Set.of();
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
            failRedisSync("updateCoopPlayer:" + islandUuid + ":" + playerUuid, e);
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
            failRedisSync("deleteCoopPlayer:" + islandUuid + ":" + playerUuid, e);
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
                    parseUuid(String.valueOf(raw)).ifPresent(touchedIslands::add);
                }
            }

            return touchedIslands;
        } catch (Exception e) {
            failRedisSync("deleteAllCoopOfPlayer:" + playerUuid, e);
            return Set.of();
        }
    }

    public boolean isPlayerCooped(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.sismember(islandCoopsKey(islandUuid), playerUuid.toString());
        } catch (Exception e) {
            plugin.severe("Failed to check cooped player: island=" + islandUuid + ", player=" + playerUuid, e);
            return false;
        }
    }

    public Set<UUID> getCoopedPlayers(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.smembers(islandCoopsKey(islandUuid)).stream().map(this::parseUuid).flatMap(Optional::stream).collect(Collectors.toSet());
        } catch (Exception e) {
            plugin.severe("Failed to get cooped players for island: " + islandUuid, e);
            return Set.of();
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
            failRedisSync("updateIslandUpgradeLevel:" + islandUuid + ":" + upgradeId, e);
        }
    }

    public int getIslandUpgradeLevel(UUID islandUuid, String upgradeId) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return parseInt(jedis.hget(islandUpgradesKey(islandUuid), upgradeId), 1);
        } catch (Exception e) {
            plugin.severe("Failed to get island upgrade level: island=" + islandUuid + ", upgrade=" + upgradeId, e);
            return 1;
        }
    }

    // =================================================================================================================
    // Island Snapshot Getter
    // =================================================================================================================

    public Island getIslandSnapshot(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {

            Map<String, String> core = jedis.hgetAll(islandCoreKey(islandUuid));
            if (core == null || core.isEmpty()) {
                return null;
            }

            boolean lock = parseBool(core.get("lock"));
            boolean pvp = parseBool(core.get("pvp"));
            UUID owner = parseUuid(core.get("owner")).orElse(null);

            Map<String, String> playersRaw = jedis.hgetAll(islandPlayersKey(islandUuid));
            Set<UUID> members = new HashSet<>();

            for (Map.Entry<String, String> entry : playersRaw.entrySet()) {
                if (!"member".equalsIgnoreCase(entry.getValue())) {
                    continue;
                }
                parseUuid(entry.getKey()).ifPresent(members::add);
            }

            Set<UUID> coops = jedis.smembers(islandCoopsKey(islandUuid)).stream().map(this::parseUuid).flatMap(Optional::stream).collect(Collectors.toSet());

            Set<UUID> bans = jedis.smembers(islandBansKey(islandUuid)).stream().map(this::parseUuid).flatMap(Optional::stream).collect(Collectors.toSet());

            Map<String, String> upgradesRaw = jedis.hgetAll(islandUpgradesKey(islandUuid));
            Map<String, Integer> upgrades = new HashMap<>();

            for (Map.Entry<String, String> entry : upgradesRaw.entrySet()) {
                int upgradeLevel = parseInt(entry.getValue(), 1);
                if (upgradeLevel <= 1) {
                    continue;
                }
                upgrades.put(entry.getKey(), upgradeLevel);
            }

            return new Island(islandUuid, lock, pvp, owner, members, coops, bans, upgrades);

        } catch (Exception e) {
            plugin.severe("Failed to load island snapshot from Redis for: " + islandUuid, e);
            return null;
        }
    }
}