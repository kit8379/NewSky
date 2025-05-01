package org.me.newsky.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.database.model.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MariaDBDatabaseHandler extends DatabaseHandler {

    private final HikariDataSource dataSource;

    public MariaDBDatabaseHandler(ConfigHandler config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:mysql://" + config.getMySQLHost() + ":" + config.getMySQLPort() + "/" + config.getMySQLName() + config.getMySQLProperties());
        hikari.setUsername(config.getMySQLUsername());
        hikari.setPassword(config.getMySQLPassword());
        hikari.setMaximumPoolSize(config.getMySQLMaxPoolSize());
        hikari.setConnectionTimeout(config.getMySQLConnectionTimeout());
        hikari.addDataSourceProperty("cachePrepStmts", config.getMySQLCachePrepStmts());
        hikari.addDataSourceProperty("prepStmtCacheSize", config.getMySQLPrepStmtCacheSize());
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", config.getMySQLPrepStmtCacheSqlLimit());
        this.dataSource = new HikariDataSource(hikari);
    }

    @Override
    public void close() {
        dataSource.close();
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void runUpdateSync(String query, StatementPreparer preparer) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
            preparer.accept(stmt);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Update failed: " + query, e);
        }
    }

    private void runUpdateAsync(String query, StatementPreparer preparer) {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
                preparer.accept(stmt);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Update failed: " + query, e);
            }
        });
    }

    @FunctionalInterface
    private interface StatementPreparer {
        void accept(PreparedStatement stmt) throws SQLException;
    }

    @Override
    public void createTables() {
        runUpdateSync("CREATE TABLE IF NOT EXISTS islands (" + "island_uuid VARCHAR(56) PRIMARY KEY, " + "`lock` BOOLEAN NOT NULL DEFAULT FALSE, " + "pvp BOOLEAN NOT NULL DEFAULT FALSE)", stmt -> {
        });
        runUpdateSync("CREATE TABLE IF NOT EXISTS island_players (" + "player_uuid VARCHAR(56), " + "island_uuid VARCHAR(56) NOT NULL, " + "role VARCHAR(56) NOT NULL, " + "PRIMARY KEY (player_uuid, island_uuid))", stmt -> {
        });
        runUpdateSync("CREATE TABLE IF NOT EXISTS island_homes (" + "player_uuid VARCHAR(56), " + "home_name VARCHAR(56), " + "home_location VARCHAR(256), " + "island_uuid VARCHAR(56), " + "PRIMARY KEY (player_uuid, home_name))", stmt -> {
        });
        runUpdateSync("CREATE TABLE IF NOT EXISTS island_warps (" + "player_uuid VARCHAR(56), " + "warp_name VARCHAR(56), " + "warp_location VARCHAR(256), " + "island_uuid VARCHAR(56), " + "PRIMARY KEY (player_uuid, warp_name))", stmt -> {
        });
        runUpdateSync("CREATE TABLE IF NOT EXISTS island_levels (" + "island_uuid VARCHAR(36) PRIMARY KEY, " + "level INT NOT NULL)", stmt -> {
        });
        runUpdateSync("CREATE TABLE IF NOT EXISTS island_bans (" + "island_uuid VARCHAR(56), " + "banned_player VARCHAR(56), " + "PRIMARY KEY (island_uuid, banned_player))", stmt -> {
        });
    }

    @Override
    public List<IslandData> selectAllIslandData() {
        List<IslandData> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("SELECT * FROM islands"); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(new IslandData(UUID.fromString(rs.getString("island_uuid")), rs.getBoolean("lock"), rs.getBoolean("pvp")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error while selecting all island data", e);
        }
        return list;
    }

    @Override
    public List<IslandPlayer> selectAllIslandPlayers() {
        List<IslandPlayer> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("SELECT * FROM island_players"); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(new IslandPlayer(UUID.fromString(rs.getString("player_uuid")), UUID.fromString(rs.getString("island_uuid")), rs.getString("role")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error while selecting all island players", e);
        }
        return list;
    }

    @Override
    public List<IslandHome> selectAllIslandHomes() {
        List<IslandHome> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("SELECT * FROM island_homes"); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(new IslandHome(UUID.fromString(rs.getString("player_uuid")), UUID.fromString(rs.getString("island_uuid")), rs.getString("home_name"), rs.getString("home_location")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error while selecting all island homes", e);
        }
        return list;
    }

    @Override
    public List<IslandWarp> selectAllIslandWarps() {
        List<IslandWarp> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("SELECT * FROM island_warps"); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(new IslandWarp(UUID.fromString(rs.getString("player_uuid")), UUID.fromString(rs.getString("island_uuid")), rs.getString("warp_name"), rs.getString("warp_location")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error while selecting all island warps", e);
        }
        return list;
    }

    @Override
    public List<IslandLevel> selectAllIslandLevels() {
        List<IslandLevel> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("SELECT * FROM island_levels"); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(new IslandLevel(UUID.fromString(rs.getString("island_uuid")), rs.getInt("level")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error while selecting all island levels", e);
        }
        return list;
    }

    @Override
    public List<IslandBan> selectAllIslandBans() {
        List<IslandBan> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("SELECT * FROM island_bans"); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(new IslandBan(UUID.fromString(rs.getString("island_uuid")), UUID.fromString(rs.getString("banned_player"))));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error while selecting all island bans", e);
        }
        return list;
    }

    @Override
    public void addIslandData(UUID islandUuid) {
        runUpdateAsync("INSERT INTO islands (island_uuid) VALUES (?)", stmt -> stmt.setString(1, islandUuid.toString()));
    }

    @Override
    public void updateIslandPlayer(UUID islandUuid, UUID playerUuid, String role) {
        runUpdateAsync("INSERT INTO island_players (player_uuid, island_uuid, role) VALUES (?, ?, ?) " + "ON DUPLICATE KEY UPDATE role = ?", stmt -> {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, islandUuid.toString());
            stmt.setString(3, role);
            stmt.setString(4, role);
        });
    }

    @Override
    public void updateHomePoint(UUID islandUuid, UUID playerUuid, String homeName, String homeLocation) {
        runUpdateAsync("INSERT INTO island_homes (player_uuid, island_uuid, home_name, home_location) VALUES (?, ?, ?, ?) " + "ON DUPLICATE KEY UPDATE home_location = ?", stmt -> {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, islandUuid.toString());
            stmt.setString(3, homeName);
            stmt.setString(4, homeLocation);
            stmt.setString(5, homeLocation);
        });
    }

    @Override
    public void updateWarpPoint(UUID islandUuid, UUID playerUuid, String warpName, String warpLocation) {
        runUpdateAsync("INSERT INTO island_warps (player_uuid, island_uuid, warp_name, warp_location) VALUES (?, ?, ?, ?) " + "ON DUPLICATE KEY UPDATE warp_location = ?", stmt -> {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, islandUuid.toString());
            stmt.setString(3, warpName);
            stmt.setString(4, warpLocation);
            stmt.setString(5, warpLocation);
        });
    }

    @Override
    public void updateIslandLock(UUID islandUuid, boolean lock) {
        runUpdateAsync("UPDATE islands SET lock = ? WHERE island_uuid = ?", stmt -> {
            stmt.setBoolean(1, lock);
            stmt.setString(2, islandUuid.toString());
        });
    }

    @Override
    public void updateIslandPvp(UUID islandUuid, boolean pvp) {
        runUpdateAsync("UPDATE islands SET pvp = ? WHERE island_uuid = ?", stmt -> {
            stmt.setBoolean(1, pvp);
            stmt.setString(2, islandUuid.toString());
        });
    }

    @Override
    public void updateIslandOwner(UUID islandUuid, UUID playerUuid) {
        runUpdateAsync("UPDATE island_players SET role = 'owner' WHERE player_uuid = ? AND island_uuid = ?", stmt -> {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, islandUuid.toString());
        });
    }

    @Override
    public void updateIslandLevel(UUID islandUuid, int level) {
        runUpdateAsync("INSERT INTO island_levels (island_uuid, level) VALUES (?, ?) " + "ON DUPLICATE KEY UPDATE level = ?", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setInt(2, level);
            stmt.setInt(3, level);
        });
    }

    @Override
    public void updateBanPlayer(UUID islandUuid, UUID playerUuid) {
        runUpdateAsync("INSERT INTO island_bans (island_uuid, banned_player) VALUES (?, ?)", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, playerUuid.toString());
        });
    }

    @Override
    public void deleteIsland(UUID islandUuid) {
        String id = islandUuid.toString();
        runUpdateAsync("DELETE FROM island_bans WHERE island_uuid = ?", stmt -> stmt.setString(1, id));
        runUpdateAsync("DELETE FROM island_levels WHERE island_uuid = ?", stmt -> stmt.setString(1, id));
        runUpdateAsync("DELETE FROM island_warps WHERE island_uuid = ?", stmt -> stmt.setString(1, id));
        runUpdateAsync("DELETE FROM island_homes WHERE island_uuid = ?", stmt -> stmt.setString(1, id));
        runUpdateAsync("DELETE FROM island_players WHERE island_uuid = ?", stmt -> stmt.setString(1, id));
        runUpdateAsync("DELETE FROM islands WHERE island_uuid = ?", stmt -> stmt.setString(1, id));
    }

    @Override
    public void deleteIslandPlayer(UUID islandUuid, UUID playerUuid) {
        runUpdateAsync("DELETE FROM island_warps WHERE player_uuid = ?", stmt -> stmt.setString(1, playerUuid.toString()));
        runUpdateAsync("DELETE FROM island_homes WHERE player_uuid = ?", stmt -> stmt.setString(1, playerUuid.toString()));
        runUpdateAsync("DELETE FROM island_players WHERE player_uuid = ? AND island_uuid = ?", stmt -> {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, islandUuid.toString());
        });
    }

    @Override
    public void deleteHomePoint(UUID islandUuid, UUID playerUuid, String homeName) {
        runUpdateAsync("DELETE FROM island_homes WHERE island_uuid = ? AND player_uuid = ? AND home_name = ?", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, playerUuid.toString());
            stmt.setString(3, homeName);
        });
    }

    @Override
    public void deleteWarpPoint(UUID islandUuid, UUID playerUuid, String warpName) {
        runUpdateAsync("DELETE FROM island_warps WHERE island_uuid = ? AND player_uuid = ? AND warp_name = ?", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, playerUuid.toString());
            stmt.setString(3, warpName);
        });
    }

    @Override
    public void deleteBanPlayer(UUID islandUuid, UUID playerUuid) {
        runUpdateAsync("DELETE FROM island_bans WHERE island_uuid = ? AND banned_player = ?", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, playerUuid.toString());
        });
    }
}
