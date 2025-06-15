package org.me.newsky.cache;

import org.me.newsky.NewSky;
import org.me.newsky.broker.CacheBroker;
import org.me.newsky.database.DatabaseHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Cache {

    private final NewSky plugin;
    private final DatabaseHandler databaseHandler;
    private CacheBroker cacheBroker;

    private final Map<UUID, Map<String, String>> islandData = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, String>> islandPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Map<String, String>>> islandHomes = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Map<String, String>>> islandWarps = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> islandBans = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> islandCoops = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> islandLevels = new ConcurrentHashMap<>();

    public Cache(NewSky plugin, DatabaseHandler databaseHandler) {
        this.plugin = plugin;
        this.databaseHandler = databaseHandler;
    }

    public void setCacheBroker(CacheBroker cacheBroker) {
        this.cacheBroker = cacheBroker;
    }

    public void cacheAllData() {
        plugin.debug(getClass().getSimpleName(), "Loading all island data into cache...");
        databaseHandler.selectAllIslandData(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                Map<String, String> data = new HashMap<>();
                data.put("lock", String.valueOf(rs.getBoolean("lock")));
                data.put("pvp", String.valueOf(rs.getBoolean("pvp")));
                islandData.put(islandUuid, data);
            }
        });
        plugin.debug(getClass().getSimpleName(), "Finished loading all island data into cache.");

        plugin.debug(getClass().getSimpleName(), "Loading all island players into cache...");
        databaseHandler.selectAllIslandPlayers(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                String role = rs.getString("role");
                islandPlayers.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).put(playerUuid, role);
            }
        });
        plugin.debug(getClass().getSimpleName(), "Finished loading all island players into cache.");

        plugin.debug(getClass().getSimpleName(), "Loading all island homes into cache...");
        databaseHandler.selectAllIslandHomes(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                String homeName = rs.getString("home_name");
                String homeLocation = rs.getString("home_location");
                islandHomes.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(homeName, homeLocation);
            }
        });
        plugin.debug(getClass().getSimpleName(), "Finished loading all island homes into cache.");

        plugin.debug(getClass().getSimpleName(), "Loading all island warps into cache...");
        databaseHandler.selectAllIslandWarps(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                String warpName = rs.getString("warp_name");
                String warpLocation = rs.getString("warp_location");
                islandWarps.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(warpName, warpLocation);
            }
        });
        plugin.debug(getClass().getSimpleName(), "Finished loading all island warps into cache.");

        plugin.debug(getClass().getSimpleName(), "Loading all island bans into cache...");
        databaseHandler.selectAllIslandBans(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                UUID bannedPlayer = UUID.fromString(rs.getString("banned_player"));
                islandBans.computeIfAbsent(islandUuid, k -> ConcurrentHashMap.newKeySet()).add(bannedPlayer);
            }
        });
        plugin.debug(getClass().getSimpleName(), "Finished loading all island bans into cache.");

        plugin.debug(getClass().getSimpleName(), "Loading all island coops into cache...");
        databaseHandler.selectAllIslandCoops(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                UUID playerUuid = UUID.fromString(rs.getString("cooped_player"));
                islandCoops.computeIfAbsent(islandUuid, k -> ConcurrentHashMap.newKeySet()).add(playerUuid);
            }
        });
        plugin.debug(getClass().getSimpleName(), "Finished loading all island coops into cache.");

        plugin.debug(getClass().getSimpleName(), "Loading all island levels into cache...");
        databaseHandler.selectAllIslandLevels(rs -> {
            while (rs.next()) {
                UUID islandUuid = UUID.fromString(rs.getString("island_uuid"));
                int level = rs.getInt("level");
                islandLevels.put(islandUuid, level);
            }
        });
        plugin.debug(getClass().getSimpleName(), "Finished loading all island levels into cache.");
    }

    public void reloadIslandData(UUID islandUuid) {
        plugin.debug(getClass().getSimpleName(), "Reloading island data for " + islandUuid);
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
            if (!found) islandData.remove(islandUuid);
        });
        plugin.debug(getClass().getSimpleName(), "Finished reloading island data for " + islandUuid);
    }

    public void reloadIslandPlayers(UUID islandUuid) {
        plugin.debug(getClass().getSimpleName(), "Reloading island players for " + islandUuid);
        databaseHandler.selectAllIslandPlayers(rs -> {
            Map<UUID, String> map = new ConcurrentHashMap<>();
            while (rs.next()) {
                if (islandUuid.toString().equals(rs.getString("island_uuid"))) {
                    map.put(UUID.fromString(rs.getString("player_uuid")), rs.getString("role"));
                }
            }
            if (map.isEmpty()) {
                islandPlayers.remove(islandUuid);
            } else {
                islandPlayers.put(islandUuid, map);
            }
        });
        plugin.debug(getClass().getSimpleName(), "Finished reloading island players for " + islandUuid);
    }

    public void reloadIslandHomes(UUID islandUuid) {
        plugin.debug(getClass().getSimpleName(), "Reloading island homes for " + islandUuid);
        databaseHandler.selectAllIslandHomes(rs -> {
            Map<UUID, Map<String, String>> map = new ConcurrentHashMap<>();
            while (rs.next()) {
                if (islandUuid.toString().equals(rs.getString("island_uuid"))) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    map.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(rs.getString("home_name"), rs.getString("home_location"));
                }
            }
            if (map.isEmpty()) {
                islandHomes.remove(islandUuid);
            } else {
                islandHomes.put(islandUuid, map);
            }
        });
        plugin.debug(getClass().getSimpleName(), "Finished reloading island homes for " + islandUuid);
    }

    public void reloadIslandWarps(UUID islandUuid) {
        plugin.debug(getClass().getSimpleName(), "Reloading island warps for " + islandUuid);
        databaseHandler.selectAllIslandWarps(rs -> {
            Map<UUID, Map<String, String>> map = new ConcurrentHashMap<>();
            while (rs.next()) {
                if (islandUuid.toString().equals(rs.getString("island_uuid"))) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    map.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(rs.getString("warp_name"), rs.getString("warp_location"));
                }
            }
            if (map.isEmpty()) {
                islandWarps.remove(islandUuid);
            } else {
                islandWarps.put(islandUuid, map);
            }
        });
        plugin.debug(getClass().getSimpleName(), "Finished reloading island warps for " + islandUuid);
    }

    public void reloadIslandBans(UUID islandUuid) {
        plugin.debug(getClass().getSimpleName(), "Reloading island bans for " + islandUuid);
        databaseHandler.selectAllIslandBans(rs -> {
            Set<UUID> set = ConcurrentHashMap.newKeySet();
            while (rs.next()) {
                if (islandUuid.toString().equals(rs.getString("island_uuid"))) {
                    set.add(UUID.fromString(rs.getString("banned_player")));
                }
            }
            if (set.isEmpty()) {
                islandBans.remove(islandUuid);
            } else {
                islandBans.put(islandUuid, set);
            }
        });
        plugin.debug(getClass().getSimpleName(), "Finished reloading island bans for " + islandUuid);
    }

    public void reloadIslandCoops(UUID islandUuid) {
        plugin.debug(getClass().getSimpleName(), "Reloading island coops for " + islandUuid);
        databaseHandler.selectAllIslandCoops(rs -> {
            Set<UUID> set = ConcurrentHashMap.newKeySet();
            while (rs.next()) {
                if (islandUuid.toString().equals(rs.getString("island_uuid"))) {
                    set.add(UUID.fromString(rs.getString("cooped_player")));
                }
            }
            if (set.isEmpty()) {
                islandCoops.remove(islandUuid);
            } else {
                islandCoops.put(islandUuid, set);
            }
        });
        plugin.debug(getClass().getSimpleName(), "Finished reloading island coops for " + islandUuid);
    }

    public void reloadIslandLevels(UUID islandUuid) {
        plugin.debug(getClass().getSimpleName(), "Reloading island levels for " + islandUuid);
        databaseHandler.selectAllIslandLevels(rs -> {
            boolean found = false;
            while (rs.next()) {
                if (islandUuid.toString().equals(rs.getString("island_uuid"))) {
                    islandLevels.put(islandUuid, rs.getInt("level"));
                    found = true;
                }
            }
            if (!found) {
                islandLevels.remove(islandUuid);
            }
        });
        plugin.debug(getClass().getSimpleName(), "Finished reloading island levels for " + islandUuid);
    }


    // =================================================================================================================
    // Island
    // =================================================================================================================
    public void createIsland(UUID islandUuid) {
        plugin.debug(getClass().getSimpleName(), "Creating new island with UUID: " + islandUuid);
        databaseHandler.addIslandData(islandUuid).thenRun(() -> {
            // Initialize island data in cache after DB success
            Map<String, String> data = new ConcurrentHashMap<>();
            data.put("lock", "false");
            data.put("pvp", "false");
            islandData.put(islandUuid, data);

            // Ensure empty structures for other data
            islandPlayers.put(islandUuid, new ConcurrentHashMap<>());
            islandHomes.put(islandUuid, new ConcurrentHashMap<>());
            islandWarps.put(islandUuid, new ConcurrentHashMap<>());
            islandBans.put(islandUuid, ConcurrentHashMap.newKeySet());
            islandCoops.put(islandUuid, ConcurrentHashMap.newKeySet());
            islandLevels.put(islandUuid, 0);

            // Publish to broker
            cacheBroker.publishUpdate("island_data", islandUuid);
            cacheBroker.publishUpdate("island_players", islandUuid);
            cacheBroker.publishUpdate("island_homes", islandUuid);
            cacheBroker.publishUpdate("island_warps", islandUuid);
            cacheBroker.publishUpdate("island_bans", islandUuid);
            cacheBroker.publishUpdate("island_coops", islandUuid);
            cacheBroker.publishUpdate("island_levels", islandUuid);
        });
        plugin.debug(getClass().getSimpleName(), "Finished creating new island with UUID: " + islandUuid);
    }

    public void deleteIsland(UUID islandUuid) {
        plugin.debug(getClass().getSimpleName(), "Deleting island with UUID: " + islandUuid);
        databaseHandler.deleteIsland(islandUuid).thenRun(() -> {
            // Remove all data from cache
            islandData.remove(islandUuid);
            islandPlayers.remove(islandUuid);
            islandHomes.remove(islandUuid);
            islandWarps.remove(islandUuid);
            islandBans.remove(islandUuid);
            islandCoops.remove(islandUuid);
            islandLevels.remove(islandUuid);

            // Publish to broker
            cacheBroker.publishUpdate("island_data", islandUuid);
            cacheBroker.publishUpdate("island_players", islandUuid);
            cacheBroker.publishUpdate("island_homes", islandUuid);
            cacheBroker.publishUpdate("island_warps", islandUuid);
            cacheBroker.publishUpdate("island_bans", islandUuid);
            cacheBroker.publishUpdate("island_coops", islandUuid);
            cacheBroker.publishUpdate("island_levels", islandUuid);
        });
        plugin.debug(getClass().getSimpleName(), "Finished deleting island with UUID: " + islandUuid);
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
        plugin.debug(getClass().getSimpleName(), "Updating player " + playerUuid + " role to " + role + " on island " + islandUuid);
        databaseHandler.updateIslandPlayer(islandUuid, playerUuid, role).thenRun(() -> {
            islandPlayers.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).put(playerUuid, role);
            cacheBroker.publishUpdate("island_players", islandUuid);
        });
        plugin.debug(getClass().getSimpleName(), "Finished updating player " + playerUuid + " role to " + role + " on island " + islandUuid);
    }

    public void deleteIslandPlayer(UUID islandUuid, UUID playerUuid) {
        plugin.debug(getClass().getSimpleName(), "Deleting player " + playerUuid + " from island " + islandUuid);
        databaseHandler.deleteIslandPlayer(islandUuid, playerUuid).thenRun(() -> {
            Map<UUID, String> players = islandPlayers.get(islandUuid);
            if (players != null) players.remove(playerUuid);

            Map<UUID, Map<String, String>> homes = islandHomes.get(islandUuid);
            if (homes != null) homes.remove(playerUuid);

            Map<UUID, Map<String, String>> warps = islandWarps.get(islandUuid);
            if (warps != null) warps.remove(playerUuid);

            cacheBroker.publishUpdate("island_players", islandUuid);
            cacheBroker.publishUpdate("island_homes", islandUuid);
            cacheBroker.publishUpdate("island_warps", islandUuid);
        });
        plugin.debug(getClass().getSimpleName(), "Finished deleting player " + playerUuid + " from island " + islandUuid);
    }

    public void updateIslandOwner(UUID islandUuid, UUID playerUuid) {
        plugin.debug(getClass().getSimpleName(), "Updating owner of island " + islandUuid + " to player " + playerUuid);
        databaseHandler.updateIslandOwner(islandUuid, playerUuid).thenRun(() -> {
            Map<UUID, String> players = islandPlayers.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>());
            for (Map.Entry<UUID, String> entry : players.entrySet()) {
                if ("owner".equalsIgnoreCase(entry.getValue())) {
                    entry.setValue("member");
                }
            }

            players.put(playerUuid, "owner");

            cacheBroker.publishUpdate("island_players", islandUuid);
        });
        plugin.debug(getClass().getSimpleName(), "Finished updating owner of island " + islandUuid + " to player " + playerUuid);
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
        plugin.debug(getClass().getSimpleName(), "Updating home point '" + homeName + "' for player " + playerUuid + " on island " + islandUuid);
        databaseHandler.updateHomePoint(islandUuid, playerUuid, homeName, homeLocation).thenRun(() -> {
            islandHomes.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(homeName, homeLocation);
            cacheBroker.publishUpdate("island_homes", islandUuid);
        });
        plugin.debug(getClass().getSimpleName(), "Finished updating home point '" + homeName + "' for player " + playerUuid + " on island " + islandUuid);
    }

    public void deleteHomePoint(UUID islandUuid, UUID playerUuid, String homeName) {
        plugin.debug(getClass().getSimpleName(), "Deleting home point '" + homeName + "' for player " + playerUuid + " on island " + islandUuid);
        databaseHandler.deleteHomePoint(islandUuid, playerUuid, homeName).thenRun(() -> {
            Map<UUID, Map<String, String>> playerMap = islandHomes.get(islandUuid);
            if (playerMap != null) {
                Map<String, String> homes = playerMap.get(playerUuid);
                if (homes != null) homes.remove(homeName);
            }
            cacheBroker.publishUpdate("island_homes", islandUuid);
        });
        plugin.debug(getClass().getSimpleName(), "Finished deleting home point '" + homeName + "' for player " + playerUuid + " on island " + islandUuid);
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
        plugin.debug(getClass().getSimpleName(), "Updating warp point '" + warpName + "' for player " + playerUuid + " on island " + islandUuid);
        databaseHandler.updateWarpPoint(islandUuid, playerUuid, warpName, warpLocation).thenRun(() -> {
            islandWarps.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(warpName, warpLocation);
            cacheBroker.publishUpdate("island_warps", islandUuid);
        });
        plugin.debug(getClass().getSimpleName(), "Finished updating warp point '" + warpName + "' for player " + playerUuid + " on island " + islandUuid);
    }

    public void deleteWarpPoint(UUID islandUuid, UUID playerUuid, String warpName) {
        plugin.debug(getClass().getSimpleName(), "Deleting warp point '" + warpName + "' for player " + playerUuid + " on island " + islandUuid);
        databaseHandler.deleteWarpPoint(islandUuid, playerUuid, warpName).thenRun(() -> {
            Map<UUID, Map<String, String>> playerMap = islandWarps.get(islandUuid);
            if (playerMap != null) {
                Map<String, String> warps = playerMap.get(playerUuid);
                if (warps != null) warps.remove(warpName);
            }
            cacheBroker.publishUpdate("island_warps", islandUuid);
        });
        plugin.debug(getClass().getSimpleName(), "Finished deleting warp point '" + warpName + "' for player " + playerUuid + " on island " + islandUuid);
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
        plugin.debug(getClass().getSimpleName(), "Banning player " + playerUuid + " on island " + islandUuid);
        databaseHandler.updateBanPlayer(islandUuid, playerUuid).thenRun(() -> {
            islandBans.computeIfAbsent(islandUuid, k -> ConcurrentHashMap.newKeySet()).add(playerUuid);
            cacheBroker.publishUpdate("island_bans", islandUuid);
        });
        plugin.debug(getClass().getSimpleName(), "Finished banning player " + playerUuid + " on island " + islandUuid);
    }

    public void deleteBanPlayer(UUID islandUuid, UUID playerUuid) {
        plugin.debug(getClass().getSimpleName(), "Unbanning player " + playerUuid + " on island " + islandUuid);
        databaseHandler.deleteBanPlayer(islandUuid, playerUuid).thenRun(() -> {
            Set<UUID> bans = islandBans.get(islandUuid);
            if (bans != null) bans.remove(playerUuid);
            cacheBroker.publishUpdate("island_bans", islandUuid);
        });
        plugin.debug(getClass().getSimpleName(), "Finished unbanning player " + playerUuid + " on island " + islandUuid);
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
        plugin.debug(getClass().getSimpleName(), "Adding coop player " + playerUuid + " to island " + islandUuid);
        databaseHandler.updateCoopPlayer(islandUuid, playerUuid).thenRun(() -> {
            islandCoops.computeIfAbsent(islandUuid, k -> ConcurrentHashMap.newKeySet()).add(playerUuid);
            cacheBroker.publishUpdate("island_coops", islandUuid);
        });
        plugin.debug(getClass().getSimpleName(), "Finished adding coop player " + playerUuid + " to island " + islandUuid);
    }

    public void deleteCoopPlayer(UUID islandUuid, UUID playerUuid) {
        plugin.debug(getClass().getSimpleName(), "Removing coop player " + playerUuid + " from island " + islandUuid);
        databaseHandler.deleteCoopPlayer(islandUuid, playerUuid).thenRun(() -> {
            Set<UUID> coops = islandCoops.get(islandUuid);
            if (coops != null) coops.remove(playerUuid);
            cacheBroker.publishUpdate("island_coops", islandUuid);
        });
        plugin.debug(getClass().getSimpleName(), "Finished removing coop player " + playerUuid + " from island " + islandUuid);
    }

    public void deleteAllCoopOfPlayer(UUID playerUuid) {
        plugin.debug(getClass().getSimpleName(), "Deleting all coops for player " + playerUuid);
        databaseHandler.deleteAllCoopOfPlayer(playerUuid).thenRun(() -> {
            Set<UUID> affectedIslands = new HashSet<>();
            for (Map.Entry<UUID, Set<UUID>> entry : islandCoops.entrySet()) {
                if (entry.getValue().remove(playerUuid)) {
                    affectedIslands.add(entry.getKey());
                }
            }
            for (UUID islandUuid : affectedIslands) {
                cacheBroker.publishUpdate("island_coops", islandUuid);
            }
        });
        plugin.debug(getClass().getSimpleName(), "Finished deleting all coops for player " + playerUuid);
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
        plugin.debug(getClass().getSimpleName(), "Updating lock status for island " + islandUuid + " to " + lock);
        databaseHandler.updateIslandLock(islandUuid, lock).thenRun(() -> {
            islandData.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).put("lock", String.valueOf(lock));
            cacheBroker.publishUpdate("island_data", islandUuid);
        });
        plugin.debug(getClass().getSimpleName(), "Finished updating lock status for island " + islandUuid + " to " + lock);
    }

    public boolean isIslandLock(UUID islandUuid) {
        return Boolean.parseBoolean(islandData.getOrDefault(islandUuid, Collections.emptyMap()).getOrDefault("lock", "false"));
    }

    // =================================================================================================================
    // PVP
    // =================================================================================================================
    public void updateIslandPvp(UUID islandUuid, boolean pvp) {
        plugin.debug(getClass().getSimpleName(), "Updating PVP status for island " + islandUuid + " to " + pvp);
        databaseHandler.updateIslandPvp(islandUuid, pvp).thenRun(() -> {
            islandData.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).put("pvp", String.valueOf(pvp));
            cacheBroker.publishUpdate("island_data", islandUuid);
        });
        plugin.debug(getClass().getSimpleName(), "Finished updating PVP status for island " + islandUuid + " to " + pvp);
    }

    public boolean isIslandPvp(UUID islandUuid) {
        return Boolean.parseBoolean(islandData.getOrDefault(islandUuid, Collections.emptyMap()).getOrDefault("pvp", "false"));
    }

    // =================================================================================================================
    // Level
    // =================================================================================================================
    public void updateIslandLevel(UUID islandUuid, int level) {
        plugin.debug(getClass().getSimpleName(), "Updating level for island " + islandUuid + " to " + level);
        databaseHandler.updateIslandLevel(islandUuid, level).thenRun(() -> {
            islandLevels.put(islandUuid, level);
            cacheBroker.publishUpdate("island_levels", islandUuid);
        });
        plugin.debug(getClass().getSimpleName(), "Finished updating level for island " + islandUuid + " to " + level);
    }

    public int getIslandLevel(UUID islandUuid) {
        return islandLevels.getOrDefault(islandUuid, 0);
    }

    public Map<UUID, Integer> getTopIslandLevels(int size) {
        return islandLevels.entrySet().stream().sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed()).limit(size).collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), LinkedHashMap::putAll);
    }
}