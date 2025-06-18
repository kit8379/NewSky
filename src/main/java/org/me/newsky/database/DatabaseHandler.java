package org.me.newsky.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class DatabaseHandler {

    private final NewSky plugin;
    private final HikariDataSource dataSource;

    public DatabaseHandler(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;

        String host = config.getMySQLHost();
        int port = config.getMySQLPort();
        String database = config.getMySQLDB();
        String username = config.getMySQLUsername();
        String password = config.getMySQLPassword();
        String properties = config.getMySQLProperties();
        int maxPoolSize = config.getMySQLMaxPoolSize();
        int connectionTimeout = config.getMySQLConnectionTimeout();
        String cachePrepStmts = config.getMySQLCachePrepStmts();
        String prepStmtCacheSize = config.getMySQLPrepStmtCacheSize();
        String prepStmtCacheSqlLimit = config.getMySQLPrepStmtCacheSqlLimit();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + properties);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(maxPoolSize);
        hikariConfig.setConnectionTimeout(connectionTimeout);
        hikariConfig.addDataSourceProperty("cachePrepStmts", cachePrepStmts);
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", prepStmtCacheSize);
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", prepStmtCacheSqlLimit);
        this.dataSource = new HikariDataSource(hikariConfig);
    }

    public void close() {
        dataSource.close();
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void executeUpdate(PreparedStatementConsumer consumer, String query) {
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            consumer.use(statement);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.severe("Database Update failed: " + query, e);
            throw new RuntimeException(e);
        }
    }


    public void executeQuery(String query, ResultProcessor processor) {
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(query); ResultSet resultSet = statement.executeQuery()) {
            processor.process(resultSet);
        } catch (SQLException e) {
            plugin.severe("Database Query failed: " + query, e);
            throw new RuntimeException(e);
        }
    }

    public void createTables() {
        createIslandDataTable();
        createIslandPlayersTable();
        createIslandHomesTable();
        createIslandWarpsTable();
        createIslandBanTable();
        createIslandCoopTable();
        createIslandLevelsTable();
    }

    private void createIslandDataTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS islands (island_uuid VARCHAR(56) PRIMARY KEY, `lock` BOOLEAN NOT NULL DEFAULT FALSE, pvp BOOLEAN NOT NULL DEFAULT FALSE);");
    }

    private void createIslandPlayersTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS island_players (player_uuid VARCHAR(56), island_uuid VARCHAR(56) NOT NULL, role VARCHAR(56) NOT NULL, FOREIGN KEY (island_uuid) REFERENCES islands(island_uuid), PRIMARY KEY (player_uuid, island_uuid));");
    }

    private void createIslandHomesTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS island_homes (player_uuid VARCHAR(56), home_name VARCHAR(56), home_location VARCHAR(256), island_uuid VARCHAR(56), PRIMARY KEY (player_uuid, home_name), FOREIGN KEY (island_uuid) REFERENCES islands(island_uuid));");
    }

    private void createIslandWarpsTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS island_warps (player_uuid VARCHAR(56), warp_name VARCHAR(56), warp_location VARCHAR(256), island_uuid VARCHAR(56), PRIMARY KEY (player_uuid, warp_name), FOREIGN KEY (island_uuid) REFERENCES islands(island_uuid));");
    }

    private void createIslandBanTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS island_bans (island_uuid VARCHAR(56), banned_player VARCHAR(56), PRIMARY KEY (island_uuid, banned_player), FOREIGN KEY (island_uuid) REFERENCES islands(island_uuid));");
    }

    private void createIslandCoopTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS island_coops (" + "island_uuid VARCHAR(56) NOT NULL, " + "cooped_player VARCHAR(56) NOT NULL, " + "PRIMARY KEY (island_uuid, cooped_player), " + "FOREIGN KEY (island_uuid) REFERENCES islands(island_uuid)" + ");");
    }

    public void createIslandLevelsTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS island_levels (" + "island_uuid VARCHAR(56) PRIMARY KEY, " + "level INT NOT NULL" + ");");
    }

    public void selectAllIslandData(ResultProcessor processor) {
        executeQuery("SELECT * FROM islands", processor);
    }

    public void selectAllIslandPlayers(ResultProcessor processor) {
        executeQuery("SELECT * FROM island_players", processor);
    }

    public void selectAllIslandHomes(ResultProcessor processor) {
        executeQuery("SELECT * FROM island_homes", processor);
    }

    public void selectAllIslandWarps(ResultProcessor processor) {
        executeQuery("SELECT * FROM island_warps", processor);
    }

    public void selectAllIslandBans(ResultProcessor processor) {
        executeQuery("SELECT * FROM island_bans", processor);
    }

    public void selectAllIslandCoops(ResultProcessor processor) {
        executeQuery("SELECT * FROM island_coops", processor);
    }

    public void selectAllIslandLevels(ResultProcessor processor) {
        executeQuery("SELECT * FROM island_levels", processor);
    }

    public void addIslandData(UUID islandUuid, UUID ownerUuid, String homePoint) {
        // Insert island base data
        executeUpdate(s -> s.setString(1, islandUuid.toString()), "INSERT INTO islands (island_uuid) VALUES (?);");

        executeUpdate(s -> {
            s.setString(1, ownerUuid.toString());
            s.setString(2, islandUuid.toString());
            s.setString(3, "owner");
            s.setString(4, "owner");
        }, "INSERT INTO island_players (player_uuid, island_uuid, role) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE role = ?;");

        executeUpdate(s -> {
            s.setString(1, ownerUuid.toString());
            s.setString(2, islandUuid.toString());
            s.setString(3, "default");
            s.setString(4, homePoint);
            s.setString(5, homePoint);
        }, "INSERT INTO island_homes (player_uuid, island_uuid, home_name, home_location) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE home_location = ?;");
    }


    public void addIslandPlayer(UUID islandUuid, UUID playerUuid, String role, String homePoint) {
        executeUpdate(s -> {
            s.setString(1, playerUuid.toString());
            s.setString(2, islandUuid.toString());
            s.setString(3, role);
            s.setString(4, role);
        }, "INSERT INTO island_players (player_uuid, island_uuid, role) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE role = ?;");

        executeUpdate(s -> {
            s.setString(1, playerUuid.toString());
            s.setString(2, islandUuid.toString());
            s.setString(3, "default");
            s.setString(4, homePoint);
            s.setString(5, homePoint);
        }, "INSERT INTO island_homes (player_uuid, island_uuid, home_name, home_location) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE home_location = ?;");
    }


    public void updateHomePoint(UUID islandUuid, UUID playerUuid, String homeName, String homeLocation) {
        executeUpdate(s -> {
            s.setString(1, playerUuid.toString());
            s.setString(2, islandUuid.toString());
            s.setString(3, homeName);
            s.setString(4, homeLocation);
            s.setString(5, homeLocation);
        }, "INSERT INTO island_homes (player_uuid, island_uuid, home_name, home_location) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE home_location = ?;");
    }

    public void updateWarpPoint(UUID islandUuid, UUID playerUuid, String warpName, String warpLocation) {
        executeUpdate(s -> {
            s.setString(1, playerUuid.toString());
            s.setString(2, islandUuid.toString());
            s.setString(3, warpName);
            s.setString(4, warpLocation);
            s.setString(5, warpLocation);
        }, "INSERT INTO island_warps (player_uuid, island_uuid, warp_name, warp_location) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE warp_location = ?;");
    }

    public void updateIslandLock(UUID islandUuid, boolean lock) {
        executeUpdate(s -> {
            s.setBoolean(1, lock);
            s.setString(2, islandUuid.toString());
        }, "UPDATE islands SET lock = ? WHERE island_uuid = ?;");
    }

    public void updateIslandPvp(UUID islandUuid, boolean pvp) {
        executeUpdate(s -> {
            s.setBoolean(1, pvp);
            s.setString(2, islandUuid.toString());
        }, "UPDATE islands SET pvp = ? WHERE island_uuid = ?;");
    }

    public void updateIslandOwner(UUID islandUuid, UUID oldOwnerUuid, UUID newOwnerUuid) {
        executeUpdate(s -> {
            s.setString(1, "member");
            s.setString(2, oldOwnerUuid.toString());
            s.setString(3, islandUuid.toString());
        }, "UPDATE island_players SET role = ? WHERE player_uuid = ? AND island_uuid = ?;");

        executeUpdate(s -> {
            s.setString(1, "owner");
            s.setString(2, newOwnerUuid.toString());
            s.setString(3, islandUuid.toString());
        }, "UPDATE island_players SET role = ? WHERE player_uuid = ? AND island_uuid = ?;");
    }

    public void updateBanPlayer(UUID islandUuid, UUID playerUuid) {
        executeUpdate(s -> {
            s.setString(1, islandUuid.toString());
            s.setString(2, playerUuid.toString());
        }, "INSERT INTO island_bans (island_uuid, banned_player) VALUES (?, ?);");
    }

    public void updateCoopPlayer(UUID islandUuid, UUID playerUuid) {
        executeUpdate(s -> {
            s.setString(1, islandUuid.toString());
            s.setString(2, playerUuid.toString());
        }, "INSERT INTO island_coops (island_uuid, cooped_player) VALUES (?, ?);");
    }

    public void updateIslandLevel(UUID islandUuid, int level) {
        executeUpdate(s -> {
            s.setString(1, islandUuid.toString());
            s.setInt(2, level);
            s.setInt(3, level);
        }, "INSERT INTO island_levels (island_uuid, level) VALUES (?, ?) ON DUPLICATE KEY UPDATE level = ?;");
    }

    public void deleteIsland(UUID islandUuid) {
        executeUpdate(s -> s.setString(1, islandUuid.toString()), "DELETE FROM island_levels WHERE island_uuid = ?;");
        executeUpdate(s -> s.setString(1, islandUuid.toString()), "DELETE FROM island_coops WHERE island_uuid = ?;");
        executeUpdate(s -> s.setString(1, islandUuid.toString()), "DELETE FROM island_bans WHERE island_uuid = ?;");
        executeUpdate(s -> s.setString(1, islandUuid.toString()), "DELETE FROM island_warps WHERE island_uuid = ?;");
        executeUpdate(s -> s.setString(1, islandUuid.toString()), "DELETE FROM island_homes WHERE island_uuid = ?;");
        executeUpdate(s -> s.setString(1, islandUuid.toString()), "DELETE FROM island_players WHERE island_uuid = ?;");
        executeUpdate(s -> s.setString(1, islandUuid.toString()), "DELETE FROM islands WHERE island_uuid = ?;");
    }


    public void deleteIslandPlayer(UUID islandUuid, UUID playerUuid) {
        executeUpdate(s -> s.setString(1, playerUuid.toString()), "DELETE FROM island_warps WHERE player_uuid = ?;");
        executeUpdate(s -> s.setString(1, playerUuid.toString()), "DELETE FROM island_homes WHERE player_uuid = ?;");
        executeUpdate(s -> {
            s.setString(1, playerUuid.toString());
            s.setString(2, islandUuid.toString());
        }, "DELETE FROM island_players WHERE player_uuid = ? AND island_uuid = ?;");
    }


    public void deleteHomePoint(UUID islandUuid, UUID playerUuid, String homeName) {
        executeUpdate(s -> {
            s.setString(1, islandUuid.toString());
            s.setString(2, playerUuid.toString());
            s.setString(3, homeName);
        }, "DELETE FROM island_homes WHERE island_uuid = ? AND player_uuid = ? AND home_name = ?;");
    }

    public void deleteWarpPoint(UUID islandUuid, UUID playerUuid, String warpName) {
        executeUpdate(s -> {
            s.setString(1, islandUuid.toString());
            s.setString(2, playerUuid.toString());
            s.setString(3, warpName);
        }, "DELETE FROM island_warps WHERE island_uuid = ? AND player_uuid = ? AND warp_name = ?;");
    }

    public void deleteBanPlayer(UUID islandUuid, UUID playerUuid) {
        executeUpdate(s -> {
            s.setString(1, islandUuid.toString());
            s.setString(2, playerUuid.toString());
        }, "DELETE FROM island_bans WHERE island_uuid = ? AND banned_player = ?;");
    }

    public void deleteCoopPlayer(UUID islandUuid, UUID playerUuid) {
        executeUpdate(s -> {
            s.setString(1, islandUuid.toString());
            s.setString(2, playerUuid.toString());
        }, "DELETE FROM island_coops WHERE island_uuid = ? AND cooped_player = ?;");
    }

    public void deleteAllCoopOfPlayer(UUID playerUuid) {
        executeUpdate(s -> s.setString(1, playerUuid.toString()), "DELETE FROM island_coops WHERE cooped_player = ?;");
    }


    @FunctionalInterface
    public interface ResultProcessor {
        void process(ResultSet resultSet) throws SQLException;
    }

    @FunctionalInterface
    public interface PreparedStatementConsumer {
        void use(PreparedStatement statement) throws SQLException;
    }
}