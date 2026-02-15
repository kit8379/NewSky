package org.me.newsky.cache;

import org.me.newsky.broker.CacheBroker;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.database.DatabaseHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Cache {

    private final ConfigHandler config;
    private final DatabaseHandler databaseHandler;

    private final Map<UUID, Map<String, String>> islandData = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, String>> islandPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Map<String, String>>> islandHomes = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Map<String, String>>> islandWarps = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> islandBans = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> islandCoops = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> islandLevels = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> islandUpgrades = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerUuidToName = new ConcurrentHashMap<>();
    private final Map<String, UUID> playerNameToUuid = new ConcurrentHashMap<>();

    private CacheBroker cacheBroker;

    public Cache(ConfigHandler config, DatabaseHandler databaseHandler) {
        this.config = config;
        this.databaseHandler = databaseHandler;
    }

    public void setCacheBroker(CacheBroker cacheBroker) {
        this.cacheBroker = cacheBroker;
    }

    public void cacheAllData() {
        cacheIslandData();
        cacheIslandPlayers();
        cacheIslandHomes();
        cacheIslandWarps();
        cacheIslandBans();
        cacheIslandCoops();
        cacheIslandLevels();
        cacheIslandUpgrades();
        cachePlayerUuidMap();
    }

    public void cacheIslandData() {
        databaseHandler.selectAllIslandData(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                Map<String, String> data = new HashMap<>();
                data.put("lock", String.valueOf(rs.getBoolean("lock")));
                data.put("pvp", String.valueOf(rs.getBoolean("pvp")));
                islandData.put(islandUuid, data);
            }
        });
    }

    public void cacheIslandPlayers() {
        databaseHandler.selectAllIslandPlayers(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                String role = rs.getString("role");
                islandPlayers.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).put(playerUuid, role);
            }
        });
    }

    public void cacheIslandHomes() {
        databaseHandler.selectAllIslandHomes(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                String homeName = rs.getString("home_name");
                String homeLocation = rs.getString("home_location");
                islandHomes.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(homeName, homeLocation);
            }
        });
    }

    public void cacheIslandWarps() {
        databaseHandler.selectAllIslandWarps(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                String warpName = rs.getString("warp_name");
                String warpLocation = rs.getString("warp_location");
                islandWarps.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(warpName, warpLocation);
            }
        });
    }

    public void cacheIslandBans() {
        databaseHandler.selectAllIslandBans(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                UUID bannedPlayer = UUID.fromString(rs.getString("banned_player"));
                islandBans.computeIfAbsent(islandUuid, k -> ConcurrentHashMap.newKeySet()).add(bannedPlayer);
            }
        });
    }

    public void cacheIslandCoops() {
        databaseHandler.selectAllIslandCoops(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                UUID playerUuid = UUID.fromString(rs.getString("cooped_player"));
                islandCoops.computeIfAbsent(islandUuid, k -> ConcurrentHashMap.newKeySet()).add(playerUuid);
            }
        });
    }

    public void cacheIslandLevels() {
        databaseHandler.selectAllIslandLevels(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                int level = rs.getInt("level");
                islandLevels.put(islandUuid, level);
            }
        });
    }

    public void cacheIslandUpgrades() {
        databaseHandler.selectAllIslandUpgrades(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                String upgradeId = rs.getString("upgrade_id");
                int level = rs.getInt("level");

                islandUpgrades.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).put(upgradeId, level);
            }
        });
    }

    public void cachePlayerUuidMap() {
        databaseHandler.selectAllPlayerUuid(rs -> {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String name = rs.getString("name");
                playerUuidToName.put(uuid, name);
                playerNameToUuid.put(name.toLowerCase(Locale.ROOT), uuid);
            }
        });
    }

    public void reloadIslandData(UUID islandUuid) {
        databaseHandler.selectIslandData(islandUuid, rs -> {
            if (rs.next()) {
                Map<String, String> data = new ConcurrentHashMap<>();
                data.put("lock", String.valueOf(rs.getBoolean("lock")));
                data.put("pvp", String.valueOf(rs.getBoolean("pvp")));
                islandData.put(islandUuid, data);
            } else {
                islandData.remove(islandUuid);
            }
        });
    }

    public void reloadIslandPlayers(UUID islandUuid) {
        databaseHandler.selectIslandPlayers(islandUuid, rs -> {
            Map<UUID, String> map = new ConcurrentHashMap<>();
            while (rs.next()) {
                UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                String role = rs.getString("role");
                map.put(playerUuid, role);
            }
            if (map.isEmpty()) {
                islandPlayers.remove(islandUuid);
            } else {
                islandPlayers.put(islandUuid, map);
            }
        });
    }

    public void reloadIslandHomes(UUID islandUuid) {
        databaseHandler.selectIslandHomes(islandUuid, rs -> {
            Map<UUID, Map<String, String>> map = new ConcurrentHashMap<>();
            while (rs.next()) {
                UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                String homeName = rs.getString("home_name");
                String homeLocation = rs.getString("home_location");
                map.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(homeName, homeLocation);
            }
            if (map.isEmpty()) {
                islandHomes.remove(islandUuid);
            } else {
                islandHomes.put(islandUuid, map);
            }
        });
    }

    public void reloadIslandWarps(UUID islandUuid) {
        databaseHandler.selectIslandWarps(islandUuid, rs -> {
            Map<UUID, Map<String, String>> map = new ConcurrentHashMap<>();
            while (rs.next()) {
                UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                String warpName = rs.getString("warp_name");
                String warpLocation = rs.getString("warp_location");
                map.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(warpName, warpLocation);
            }
            if (map.isEmpty()) {
                islandWarps.remove(islandUuid);
            } else {
                islandWarps.put(islandUuid, map);
            }
        });
    }

    public void reloadIslandBans(UUID islandUuid) {
        databaseHandler.selectIslandBans(islandUuid, rs -> {
            Set<UUID> set = ConcurrentHashMap.newKeySet();
            while (rs.next()) {
                UUID bannedPlayer = UUID.fromString(rs.getString("banned_player"));
                set.add(bannedPlayer);
            }
            if (set.isEmpty()) {
                islandBans.remove(islandUuid);
            } else {
                islandBans.put(islandUuid, set);
            }
        });
    }

    public void reloadIslandCoops(UUID islandUuid) {
        databaseHandler.selectIslandCoops(islandUuid, rs -> {
            Set<UUID> set = ConcurrentHashMap.newKeySet();
            while (rs.next()) {
                UUID playerUuid = UUID.fromString(rs.getString("cooped_player"));
                set.add(playerUuid);
            }
            if (set.isEmpty()) {
                islandCoops.remove(islandUuid);
            } else {
                islandCoops.put(islandUuid, set);
            }
        });
    }

    public void reloadIslandLevels(UUID islandUuid) {
        databaseHandler.selectIslandLevel(islandUuid, rs -> {
            if (rs.next()) {
                int level = rs.getInt("level");
                islandLevels.put(islandUuid, level);
            } else {
                islandLevels.remove(islandUuid);
            }
        });
    }

    public void reloadIslandUpgrades(UUID islandUuid) {
        databaseHandler.selectIslandUpgrades(islandUuid, rs -> {
            Map<String, Integer> map = new ConcurrentHashMap<>();
            while (rs.next()) {
                String upgradeId = rs.getString("upgrade_id");
                int level = rs.getInt("level");
                map.put(upgradeId, level);
            }

            if (map.isEmpty()) {
                islandUpgrades.remove(islandUuid);
            } else {
                islandUpgrades.put(islandUuid, map);
            }
        });
    }

    public void reloadPlayerUuid(UUID playerUuid) {
        databaseHandler.selectPlayerUuid(playerUuid, rs -> {
            if (rs.next()) {
                String name = rs.getString("name");
                playerUuidToName.put(playerUuid, name);
                playerNameToUuid.put(name.toLowerCase(Locale.ROOT), playerUuid);
            } else {
                playerUuidToName.remove(playerUuid);
                playerNameToUuid.values().removeIf(existing -> existing.equals(playerUuid));
            }
        });
    }

    // =================================================================================================================
    // Island
    // =================================================================================================================
    public void createIsland(UUID islandUuid, UUID ownerUuid) {
        String homePoint = config.getIslandSpawnX() + "," + config.getIslandSpawnY() + "," + config.getIslandSpawnZ() + "," + config.getIslandSpawnYaw() + "," + config.getIslandSpawnPitch();

        databaseHandler.addIslandData(islandUuid, ownerUuid, homePoint);

        Map<String, String> data = new ConcurrentHashMap<>();
        data.put("lock", "false");
        data.put("pvp", "false");
        islandData.put(islandUuid, data);
        cacheBroker.publishUpdate("island_data", islandUuid);

        Map<UUID, String> players = new ConcurrentHashMap<>();
        players.put(ownerUuid, "owner");
        islandPlayers.put(islandUuid, players);
        cacheBroker.publishUpdate("island_players", islandUuid);

        Map<String, String> homeMap = new ConcurrentHashMap<>();
        homeMap.put("default", homePoint);
        Map<UUID, Map<String, String>> playerHomes = new ConcurrentHashMap<>();
        playerHomes.put(ownerUuid, homeMap);
        islandHomes.put(islandUuid, playerHomes);
        cacheBroker.publishUpdate("island_homes", islandUuid);

        islandWarps.put(islandUuid, new ConcurrentHashMap<>());
        cacheBroker.publishUpdate("island_warps", islandUuid);

        islandBans.put(islandUuid, ConcurrentHashMap.newKeySet());
        cacheBroker.publishUpdate("island_bans", islandUuid);

        islandCoops.put(islandUuid, ConcurrentHashMap.newKeySet());
        cacheBroker.publishUpdate("island_coops", islandUuid);

        islandLevels.put(islandUuid, 0);
        cacheBroker.publishUpdate("island_levels", islandUuid);
    }


    public void deleteIsland(UUID islandUuid) {
        databaseHandler.deleteIsland(islandUuid);

        islandUpgrades.remove(islandUuid);
        cacheBroker.publishUpdate("island_upgrades", islandUuid);

        islandLevels.remove(islandUuid);
        cacheBroker.publishUpdate("island_levels", islandUuid);

        islandCoops.remove(islandUuid);
        cacheBroker.publishUpdate("island_coops", islandUuid);

        islandBans.remove(islandUuid);
        cacheBroker.publishUpdate("island_bans", islandUuid);

        islandWarps.remove(islandUuid);
        cacheBroker.publishUpdate("island_warps", islandUuid);

        islandHomes.remove(islandUuid);
        cacheBroker.publishUpdate("island_homes", islandUuid);

        islandPlayers.remove(islandUuid);
        cacheBroker.publishUpdate("island_players", islandUuid);

        islandData.remove(islandUuid);
        cacheBroker.publishUpdate("island_data", islandUuid);
    }

    public Optional<UUID> getIslandUuid(UUID playerUuid) {
        for (Map.Entry<UUID, Map<UUID, String>> entry : islandPlayers.entrySet()) {
            if (entry.getValue().containsKey(playerUuid)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }


    // =================================================================================================================
    // Player
    // =================================================================================================================
    public void updateIslandPlayer(UUID islandUuid, UUID playerUuid, String role) {
        String homePoint = config.getIslandSpawnX() + "," + config.getIslandSpawnY() + "," + config.getIslandSpawnZ() + "," + config.getIslandSpawnYaw() + "," + config.getIslandSpawnPitch();

        databaseHandler.addIslandPlayer(islandUuid, playerUuid, role, homePoint);

        islandPlayers.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).put(playerUuid, role);
        cacheBroker.publishUpdate("island_players", islandUuid);

        islandHomes.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put("default", homePoint);
        cacheBroker.publishUpdate("island_homes", islandUuid);
    }


    public void deleteIslandPlayer(UUID islandUuid, UUID playerUuid) {
        databaseHandler.deleteIslandPlayer(islandUuid, playerUuid);

        Map<UUID, Map<String, String>> warps = islandWarps.get(islandUuid);
        if (warps != null) warps.remove(playerUuid);
        cacheBroker.publishUpdate("island_warps", islandUuid);

        Map<UUID, Map<String, String>> homes = islandHomes.get(islandUuid);
        if (homes != null) homes.remove(playerUuid);
        cacheBroker.publishUpdate("island_homes", islandUuid);

        Map<UUID, String> players = islandPlayers.get(islandUuid);
        if (players != null) players.remove(playerUuid);
        cacheBroker.publishUpdate("island_players", islandUuid);
    }

    public void updateIslandOwner(UUID islandUuid, UUID oldOwnerUuid, UUID newOwnerUuid) {
        databaseHandler.updateIslandOwner(islandUuid, oldOwnerUuid, newOwnerUuid);

        Map<UUID, String> players = islandPlayers.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>());
        players.put(oldOwnerUuid, "member");
        players.put(newOwnerUuid, "owner");
        cacheBroker.publishUpdate("island_players", islandUuid);
    }


    public UUID getIslandOwner(UUID islandUuid) {
        Map<UUID, String> players = islandPlayers.get(islandUuid);
        if (players == null || players.isEmpty()) {
            throw new IllegalStateException("Island " + islandUuid + " has no players data loaded.");
        }

        for (Map.Entry<UUID, String> entry : players.entrySet()) {
            if ("owner".equalsIgnoreCase(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public Set<UUID> getIslandMembers(UUID islandUuid) {
        Map<UUID, String> players = islandPlayers.get(islandUuid);
        if (players == null || players.isEmpty()) {
            throw new IllegalStateException("Island " + islandUuid + " has no players data loaded.");
        }

        Set<UUID> members = new HashSet<>();
        for (Map.Entry<UUID, String> entry : players.entrySet()) {
            if ("member".equalsIgnoreCase(entry.getValue())) {
                members.add(entry.getKey());
            }
        }
        return members;
    }

    public Set<UUID> getIslandPlayers(UUID islandUuid) {
        return islandPlayers.getOrDefault(islandUuid, Collections.emptyMap()).keySet();
    }


    // =================================================================================================================
    // Home
    // =================================================================================================================
    public void updateHomePoint(UUID islandUuid, UUID playerUuid, String homeName, String homeLocation) {
        databaseHandler.updateHomePoint(islandUuid, playerUuid, homeName, homeLocation);

        islandHomes.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(homeName, homeLocation);
        cacheBroker.publishUpdate("island_homes", islandUuid);
    }

    public void deleteHomePoint(UUID islandUuid, UUID playerUuid, String homeName) {
        databaseHandler.deleteHomePoint(islandUuid, playerUuid, homeName);

        Map<UUID, Map<String, String>> playerMap = islandHomes.get(islandUuid);
        if (playerMap != null) {
            Map<String, String> homes = playerMap.get(playerUuid);
            if (homes != null) {
                homes.remove(homeName);
            }
        }
        cacheBroker.publishUpdate("island_homes", islandUuid);
    }

    public Optional<String> getHomeLocation(UUID islandUuid, UUID playerUuid, String homeName) {
        Map<UUID, Map<String, String>> playerMap = islandHomes.get(islandUuid);
        if (playerMap == null) return Optional.empty();
        Map<String, String> homes = playerMap.get(playerUuid);
        if (homes == null) return Optional.empty();
        return Optional.ofNullable(homes.get(homeName));
    }

    public Set<String> getHomeNames(UUID islandUuid, UUID playerUuid) {
        Map<UUID, Map<String, String>> playerMap = islandHomes.get(islandUuid);
        if (playerMap == null) return Collections.emptySet();
        Map<String, String> homes = playerMap.get(playerUuid);
        if (homes == null) return Collections.emptySet();
        return homes.keySet();
    }


    // =================================================================================================================
    // Warp
    // =================================================================================================================
    public void updateWarpPoint(UUID islandUuid, UUID playerUuid, String warpName, String warpLocation) {
        databaseHandler.updateWarpPoint(islandUuid, playerUuid, warpName, warpLocation);

        islandWarps.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(warpName, warpLocation);
        cacheBroker.publishUpdate("island_warps", islandUuid);
    }

    public void deleteWarpPoint(UUID islandUuid, UUID playerUuid, String warpName) {
        databaseHandler.deleteWarpPoint(islandUuid, playerUuid, warpName);

        Map<UUID, Map<String, String>> playerMap = islandWarps.get(islandUuid);
        if (playerMap != null) {
            Map<String, String> warps = playerMap.get(playerUuid);
            if (warps != null) {
                warps.remove(warpName);
            }
        }
        cacheBroker.publishUpdate("island_warps", islandUuid);
    }

    public Optional<String> getWarpLocation(UUID islandUuid, UUID playerUuid, String warpName) {
        Map<UUID, Map<String, String>> playerMap = islandWarps.get(islandUuid);
        if (playerMap == null) return Optional.empty();
        Map<String, String> warps = playerMap.get(playerUuid);
        if (warps == null) return Optional.empty();
        return Optional.ofNullable(warps.get(warpName));
    }

    public Set<String> getWarpNames(UUID islandUuid, UUID playerUuid) {
        Map<UUID, Map<String, String>> playerMap = islandWarps.get(islandUuid);
        if (playerMap == null) return Collections.emptySet();
        Map<String, String> warps = playerMap.get(playerUuid);
        if (warps == null) return Collections.emptySet();
        return warps.keySet();
    }


    // =================================================================================================================
    // Ban
    // =================================================================================================================
    public void updateBanPlayer(UUID islandUuid, UUID playerUuid) {
        databaseHandler.updateBanPlayer(islandUuid, playerUuid);

        islandBans.computeIfAbsent(islandUuid, k -> ConcurrentHashMap.newKeySet()).add(playerUuid);
        cacheBroker.publishUpdate("island_bans", islandUuid);
    }

    public void deleteBanPlayer(UUID islandUuid, UUID playerUuid) {
        databaseHandler.deleteBanPlayer(islandUuid, playerUuid);

        Set<UUID> bans = islandBans.get(islandUuid);
        if (bans != null) {
            bans.remove(playerUuid);
        }
        cacheBroker.publishUpdate("island_bans", islandUuid);
    }

    public boolean isPlayerBanned(UUID islandUuid, UUID playerUuid) {
        Set<UUID> bans = islandBans.get(islandUuid);
        return bans != null && bans.contains(playerUuid);
    }

    public Set<UUID> getBannedPlayers(UUID islandUuid) {
        return islandBans.getOrDefault(islandUuid, Collections.emptySet());
    }


    // =================================================================================================================
    // Coop
    // =================================================================================================================
    public void updateCoopPlayer(UUID islandUuid, UUID playerUuid) {
        databaseHandler.updateCoopPlayer(islandUuid, playerUuid);

        islandCoops.computeIfAbsent(islandUuid, k -> ConcurrentHashMap.newKeySet()).add(playerUuid);
        cacheBroker.publishUpdate("island_coops", islandUuid);
    }

    public void deleteCoopPlayer(UUID islandUuid, UUID playerUuid) {
        databaseHandler.deleteCoopPlayer(islandUuid, playerUuid);

        Set<UUID> coops = islandCoops.get(islandUuid);
        if (coops != null) {
            coops.remove(playerUuid);
        }
        cacheBroker.publishUpdate("island_coops", islandUuid);
    }

    public void deleteAllCoopOfPlayer(UUID playerUuid) {
        databaseHandler.deleteAllCoopOfPlayer(playerUuid);

        Set<UUID> affectedIslands = new HashSet<>();
        for (Map.Entry<UUID, Set<UUID>> entry : islandCoops.entrySet()) {
            if (entry.getValue().remove(playerUuid)) {
                affectedIslands.add(entry.getKey());
            }
        }
        for (UUID islandUuid : affectedIslands) {
            cacheBroker.publishUpdate("island_coops", islandUuid);
        }
    }

    public boolean isPlayerCooped(UUID islandUuid, UUID playerUuid) {
        Set<UUID> coopSet = islandCoops.get(islandUuid);
        return coopSet != null && coopSet.contains(playerUuid);
    }

    public Set<UUID> getCoopedPlayers(UUID islandUuid) {
        return islandCoops.getOrDefault(islandUuid, Collections.emptySet());
    }

    // =================================================================================================================
    // Lock
    // =================================================================================================================
    public void updateIslandLock(UUID islandUuid, boolean lock) {
        databaseHandler.updateIslandLock(islandUuid, lock);

        islandData.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).put("lock", String.valueOf(lock));
        cacheBroker.publishUpdate("island_data", islandUuid);
    }

    public boolean isIslandLock(UUID islandUuid) {
        return Boolean.parseBoolean(islandData.getOrDefault(islandUuid, Collections.emptyMap()).getOrDefault("lock", "false"));
    }

    // =================================================================================================================
    // PVP
    // =================================================================================================================
    public void updateIslandPvp(UUID islandUuid, boolean pvp) {
        databaseHandler.updateIslandPvp(islandUuid, pvp);

        islandData.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).put("pvp", String.valueOf(pvp));
        cacheBroker.publishUpdate("island_data", islandUuid);
    }

    public boolean isIslandPvp(UUID islandUuid) {
        return Boolean.parseBoolean(islandData.getOrDefault(islandUuid, Collections.emptyMap()).getOrDefault("pvp", "false"));
    }

    // =================================================================================================================
    // Level
    // =================================================================================================================
    public void updateIslandLevel(UUID islandUuid, int level) {
        databaseHandler.updateIslandLevel(islandUuid, level);

        islandLevels.put(islandUuid, level);
        cacheBroker.publishUpdate("island_levels", islandUuid);
    }

    public int getIslandLevel(UUID islandUuid) {
        return islandLevels.getOrDefault(islandUuid, 0);
    }

    public Map<UUID, Integer> getTopIslandLevels(int size) {
        PriorityQueue<Map.Entry<UUID, Integer>> heap = new PriorityQueue<>(Map.Entry.comparingByValue());

        for (var entry : islandLevels.entrySet()) {
            heap.offer(entry);
            if (heap.size() > size) {
                heap.poll();
            }
        }

        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(heap);
        sorted.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());

        Map<UUID, Integer> result = new LinkedHashMap<>();
        for (var e : sorted) {
            result.put(e.getKey(), e.getValue());
        }

        return result;
    }

    // ================================================================================================================
    // Upgrade
    // =================================================================================================================
    public void updateIslandUpgradeLevel(UUID islandUuid, String upgradeId, int level) {
        if (level <= 1) {
            databaseHandler.deleteIslandUpgrade(islandUuid, upgradeId);

            Map<String, Integer> map = islandUpgrades.get(islandUuid);
            if (map != null) {
                map.remove(upgradeId);
                if (map.isEmpty()) {
                    islandUpgrades.remove(islandUuid);
                }
            }
        } else {
            databaseHandler.upsertIslandUpgrade(islandUuid, upgradeId, level);
            islandUpgrades.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).put(upgradeId, level);
        }

        cacheBroker.publishUpdate("island_upgrades", islandUuid);
    }

    public int getIslandUpgradeLevel(UUID islandUuid, String upgradeId) {
        Map<String, Integer> map = islandUpgrades.get(islandUuid);
        if (map == null) {
            return 1;
        }
        return map.getOrDefault(upgradeId, 1);
    }


    // =================================================================================================================
    // Player UUID
    // =================================================================================================================
    public void updatePlayerUuid(UUID uuid, String name) {
        playerUuidToName.put(uuid, name);
        playerNameToUuid.put(name.toLowerCase(Locale.ROOT), uuid);
        databaseHandler.updatePlayerName(uuid, name);
        if (cacheBroker != null) {
            cacheBroker.publishUpdate("player_uuid", uuid);
        }
    }

    public Optional<String> getPlayerName(UUID uuid) {
        return Optional.ofNullable(playerUuidToName.get(uuid));
    }

    public Optional<UUID> getPlayerUuid(String name) {
        return Optional.ofNullable(playerNameToUuid.get(name.toLowerCase(Locale.ROOT)));
    }
}