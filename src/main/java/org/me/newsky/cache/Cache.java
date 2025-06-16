package org.me.newsky.cache;

import org.me.newsky.broker.CacheBroker;
import org.me.newsky.database.DatabaseHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Cache {

    private final DatabaseHandler databaseHandler;
    private CacheBroker cacheBroker;

    private final Map<UUID, Map<String, String>> islandData = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, String>> islandPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Map<String, String>>> islandHomes = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Map<String, String>>> islandWarps = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> islandBans = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> islandCoops = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> islandLevels = new ConcurrentHashMap<>();

    public Cache(DatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
    }

    public void setCacheBroker(CacheBroker cacheBroker) {
        this.cacheBroker = cacheBroker;
    }

    public void cacheAllData() {
        databaseHandler.selectAllIslandData(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                Map<String, String> data = new HashMap<>();
                data.put("lock", String.valueOf(rs.getBoolean("lock")));
                data.put("pvp", String.valueOf(rs.getBoolean("pvp")));
                islandData.put(islandUuid, data);
            }
        });

        databaseHandler.selectAllIslandPlayers(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                String role = rs.getString("role");
                islandPlayers.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).put(playerUuid, role);
            }
        });

        databaseHandler.selectAllIslandHomes(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                String homeName = rs.getString("home_name");
                String homeLocation = rs.getString("home_location");
                islandHomes.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(homeName, homeLocation);
            }
        });

        databaseHandler.selectAllIslandWarps(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                String warpName = rs.getString("warp_name");
                String warpLocation = rs.getString("warp_location");
                islandWarps.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(warpName, warpLocation);
            }
        });

        databaseHandler.selectAllIslandBans(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                UUID bannedPlayer = UUID.fromString(rs.getString("banned_player"));
                islandBans.computeIfAbsent(islandUuid, k -> ConcurrentHashMap.newKeySet()).add(bannedPlayer);
            }
        });

        databaseHandler.selectAllIslandCoops(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                UUID playerUuid = UUID.fromString(rs.getString("cooped_player"));
                islandCoops.computeIfAbsent(islandUuid, k -> ConcurrentHashMap.newKeySet()).add(playerUuid);
            }
        });

        databaseHandler.selectAllIslandLevels(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                int level = rs.getInt("level");
                islandLevels.put(islandUuid, level);
            }
        });
    }

    public void reloadIslandData(UUID islandUuid) {
        databaseHandler.selectAllIslandData(rs -> {
            boolean found = false;
            while (rs.next()) {
                if (islandUuid.toString().equals(rs.getString("island_uuid"))) {
                    Map<String, String> data = new ConcurrentHashMap<>();
                    data.put("lock", String.valueOf(rs.getBoolean("lock")));
                    data.put("pvp", String.valueOf(rs.getBoolean("pvp")));
                    islandData.put(islandUuid, data);
                    found = true;
                }
            }
            if (!found) {
                islandData.remove(islandUuid);
            }
        });
    }


    public void reloadIslandPlayers(UUID islandUuid) {
        databaseHandler.selectAllIslandPlayers(rs -> {
            Map<UUID, String> map = new ConcurrentHashMap<>();
            while (rs.next()) {
                if (islandUuid.toString().equals(rs.getString("island_uuid"))) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    String role = rs.getString("role");
                    map.put(playerUuid, role);
                }
            }
            if (map.isEmpty()) {
                islandPlayers.remove(islandUuid);
            } else {
                islandPlayers.put(islandUuid, map);
            }
        });
    }

    public void reloadIslandHomes(UUID islandUuid) {
        databaseHandler.selectAllIslandHomes(rs -> {
            Map<UUID, Map<String, String>> map = new ConcurrentHashMap<>();
            while (rs.next()) {
                if (islandUuid.toString().equals(rs.getString("island_uuid"))) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    String homeName = rs.getString("home_name");
                    String homeLocation = rs.getString("home_location");
                    map.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(homeName, homeLocation);
                }
            }
            if (map.isEmpty()) {
                islandHomes.remove(islandUuid);
            } else {
                islandHomes.put(islandUuid, map);
            }
        });
    }

    public void reloadIslandWarps(UUID islandUuid) {
        databaseHandler.selectAllIslandWarps(rs -> {
            Map<UUID, Map<String, String>> map = new ConcurrentHashMap<>();
            while (rs.next()) {
                if (islandUuid.toString().equals(rs.getString("island_uuid"))) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    String warpName = rs.getString("warp_name");
                    String warpLocation = rs.getString("warp_location");
                    map.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(warpName, warpLocation);
                }
            }
            if (map.isEmpty()) {
                islandWarps.remove(islandUuid);
            } else {
                islandWarps.put(islandUuid, map);
            }
        });
    }

    public void reloadIslandBans(UUID islandUuid) {
        databaseHandler.selectAllIslandBans(rs -> {
            Set<UUID> set = ConcurrentHashMap.newKeySet();
            while (rs.next()) {
                if (islandUuid.toString().equals(rs.getString("island_uuid"))) {
                    UUID bannedPlayer = UUID.fromString(rs.getString("banned_player"));
                    set.add(bannedPlayer);
                }
            }
            if (set.isEmpty()) {
                islandBans.remove(islandUuid);
            } else {
                islandBans.put(islandUuid, set);
            }
        });
    }

    public void reloadIslandCoops(UUID islandUuid) {
        databaseHandler.selectAllIslandCoops(rs -> {
            Set<UUID> set = ConcurrentHashMap.newKeySet();
            while (rs.next()) {
                if (islandUuid.toString().equals(rs.getString("island_uuid"))) {
                    UUID playerUuid = UUID.fromString(rs.getString("cooped_player"));
                    set.add(playerUuid);
                }
            }
            if (set.isEmpty()) {
                islandCoops.remove(islandUuid);
            } else {
                islandCoops.put(islandUuid, set);
            }
        });
    }

    public void reloadIslandLevels(UUID islandUuid) {
        databaseHandler.selectAllIslandLevels(rs -> {
            boolean found = false;
            while (rs.next()) {
                if (islandUuid.toString().equals(rs.getString("island_uuid"))) {
                    int level = rs.getInt("level");
                    islandLevels.put(islandUuid, level);
                    found = true;
                }
            }
            if (!found) {
                islandLevels.remove(islandUuid);
            }
        });
    }


    // =================================================================================================================
    // Island
    // =================================================================================================================
    public void createIsland(UUID islandUuid) {
        databaseHandler.updateIslandData(islandUuid);

        // Initialize island data in cache after DB success
        Map<String, String> data = new ConcurrentHashMap<>();
        data.put("lock", "false");
        data.put("pvp", "false");
        islandData.put(islandUuid, data);

        islandPlayers.put(islandUuid, new ConcurrentHashMap<>());
        islandHomes.put(islandUuid, new ConcurrentHashMap<>());
        islandWarps.put(islandUuid, new ConcurrentHashMap<>());
        islandBans.put(islandUuid, ConcurrentHashMap.newKeySet());
        islandCoops.put(islandUuid, ConcurrentHashMap.newKeySet());
        islandLevels.put(islandUuid, 0);

        cacheBroker.publishUpdate("island_data", islandUuid);
        cacheBroker.publishUpdate("island_players", islandUuid);
        cacheBroker.publishUpdate("island_homes", islandUuid);
        cacheBroker.publishUpdate("island_warps", islandUuid);
        cacheBroker.publishUpdate("island_bans", islandUuid);
        cacheBroker.publishUpdate("island_coops", islandUuid);
        cacheBroker.publishUpdate("island_levels", islandUuid);
    }

    public void deleteIsland(UUID islandUuid) {
        databaseHandler.deleteIsland(islandUuid);

        islandLevels.remove(islandUuid);
        islandCoops.remove(islandUuid);
        islandBans.remove(islandUuid);
        islandWarps.remove(islandUuid);
        islandHomes.remove(islandUuid);
        islandPlayers.remove(islandUuid);
        islandData.remove(islandUuid);

        cacheBroker.publishUpdate("island_levels", islandUuid);
        cacheBroker.publishUpdate("island_coops", islandUuid);
        cacheBroker.publishUpdate("island_bans", islandUuid);
        cacheBroker.publishUpdate("island_warps", islandUuid);
        cacheBroker.publishUpdate("island_homes", islandUuid);
        cacheBroker.publishUpdate("island_players", islandUuid);
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


    // Player
    // =================================================================================================================
    public void updateIslandPlayer(UUID islandUuid, UUID playerUuid, String role) {
        databaseHandler.updateIslandPlayer(islandUuid, playerUuid, role);

        islandPlayers.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).put(playerUuid, role);

        cacheBroker.publishUpdate("island_players", islandUuid);
    }

    public void deleteIslandPlayer(UUID islandUuid, UUID playerUuid) {
        databaseHandler.deleteIslandPlayer(islandUuid, playerUuid);

        Map<UUID, Map<String, String>> warps = islandWarps.get(islandUuid);
        if (warps != null) warps.remove(playerUuid);

        Map<UUID, Map<String, String>> homes = islandHomes.get(islandUuid);
        if (homes != null) homes.remove(playerUuid);

        Map<UUID, String> players = islandPlayers.get(islandUuid);
        if (players != null) players.remove(playerUuid);

        cacheBroker.publishUpdate("island_warps", islandUuid);
        cacheBroker.publishUpdate("island_homes", islandUuid);
        cacheBroker.publishUpdate("island_players", islandUuid);
    }

    public void updateIslandOwner(UUID islandUuid, UUID playerUuid) {
        databaseHandler.updateIslandOwner(islandUuid, playerUuid);

        Map<UUID, String> players = islandPlayers.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>());
        for (Map.Entry<UUID, String> entry : players.entrySet()) {
            if ("owner".equalsIgnoreCase(entry.getValue())) {
                entry.setValue("member");
            }
        }
        players.put(playerUuid, "owner");

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
        return islandLevels.entrySet().stream().sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed()).limit(size).collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), LinkedHashMap::putAll);
    }
}