package org.me.newsky.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.me.newsky.config.ConfigHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DatabaseHandler {

    private final HikariDataSource dataSource;

    public DatabaseHandler(ConfigHandler config) {
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

    private CompletableFuture<Void> executeUpdate(PreparedStatementConsumer consumer, String query) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
                consumer.use(statement);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void executeQuery(String query, ResultProcessor processor) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(query); ResultSet resultSet = statement.executeQuery()) {
                processor.process(resultSet);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
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
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS islands (island_uuid VARCHAR(56) PRIMARY KEY, `lock` BOOLEAN NOT NULL DEFAULT FALSE, pvp BOOLEAN NOT NULL DEFAULT FALSE);").join();
    }

    private void createIslandPlayersTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS island_players (player_uuid VARCHAR(56), island_uuid VARCHAR(56) NOT NULL, role VARCHAR(56) NOT NULL, FOREIGN KEY (island_uuid) REFERENCES islands(island_uuid), PRIMARY KEY (player_uuid, island_uuid));").join();
    }

    private void createIslandHomesTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS island_homes (player_uuid VARCHAR(56), home_name VARCHAR(56), home_location VARCHAR(256), island_uuid VARCHAR(56), PRIMARY KEY (player_uuid, home_name), FOREIGN KEY (island_uuid) REFERENCES islands(island_uuid));").join();
    }

    private void createIslandWarpsTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS island_warps (player_uuid VARCHAR(56), warp_name VARCHAR(56), warp_location VARCHAR(256), island_uuid VARCHAR(56), PRIMARY KEY (player_uuid, warp_name), FOREIGN KEY (island_uuid) REFERENCES islands(island_uuid));").join();
    }

    private void createIslandBanTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS island_bans (island_uuid VARCHAR(56), banned_player VARCHAR(56), PRIMARY KEY (island_uuid, banned_player), FOREIGN KEY (island_uuid) REFERENCES islands(island_uuid));").join();
    }

    private void createIslandCoopTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS island_coops (" + "island_uuid VARCHAR(56) NOT NULL, " + "cooped_player VARCHAR(56) NOT NULL, " + "PRIMARY KEY (island_uuid, cooped_player), " + "FOREIGN KEY (island_uuid) REFERENCES islands(island_uuid)" + ");").join();
    }

    public void createIslandLevelsTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS island_levels (" + "island_uuid VARCHAR(56) PRIMARY KEY, " + "level INT NOT NULL" + ");").join();
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

    public CompletableFuture<Void> addIslandData(UUID islandUuid) {
        return executeUpdate(s -> s.setString(1, islandUuid.toString()), "INSERT INTO islands (island_uuid) VALUES (?);");
    }

    public CompletableFuture<Void> updateIslandPlayer(UUID islandUuid, UUID playerUuid, String role) {
        return executeUpdate(s -> {
            s.setString(1, playerUuid.toString());
            s.setString(2, islandUuid.toString());
            s.setString(3, role);
            s.setString(4, role);
        }, "INSERT INTO island_players (player_uuid, island_uuid, role) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE role = ?;");
    }

    public CompletableFuture<Void> updateHomePoint(UUID islandUuid, UUID playerUuid, String homeName, String homeLocation) {
        return executeUpdate(s -> {
            s.setString(1, playerUuid.toString());
            s.setString(2, islandUuid.toString());
            s.setString(3, homeName);
            s.setString(4, homeLocation);
            s.setString(5, homeLocation);
        }, "INSERT INTO island_homes (player_uuid, island_uuid, home_name, home_location) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE home_location = ?;");
    }

    public CompletableFuture<Void> updateWarpPoint(UUID islandUuid, UUID playerUuid, String warpName, String warpLocation) {
        return executeUpdate(s -> {
            s.setString(1, playerUuid.toString());
            s.setString(2, islandUuid.toString());
            s.setString(3, warpName);
            s.setString(4, warpLocation);
            s.setString(5, warpLocation);
        }, "INSERT INTO island_warps (player_uuid, island_uuid, warp_name, warp_location) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE warp_location = ?;");
    }

    public CompletableFuture<Void> updateIslandLock(UUID islandUuid, boolean lock) {
        return executeUpdate(s -> {
            s.setBoolean(1, lock);
            s.setString(2, islandUuid.toString());
        }, "UPDATE islands SET lock = ? WHERE island_uuid = ?;");
    }

    public CompletableFuture<Void> updateIslandPvp(UUID islandUuid, boolean pvp) {
        return executeUpdate(s -> {
            s.setBoolean(1, pvp);
            s.setString(2, islandUuid.toString());
        }, "UPDATE islands SET pvp = ? WHERE island_uuid = ?;");
    }

    public CompletableFuture<Void> updateIslandOwner(UUID islandUuid, UUID playerUuid) {
        return executeUpdate(s -> {
            s.setString(1, playerUuid.toString());
            s.setString(2, islandUuid.toString());
        }, "UPDATE island_players SET role = 'owner' WHERE player_uuid = ? AND island_uuid = ?;");
    }

    public CompletableFuture<Void> updateBanPlayer(UUID islandUuid, UUID playerUuid) {
        return executeUpdate(s -> {
            s.setString(1, islandUuid.toString());
            s.setString(2, playerUuid.toString());
        }, "INSERT INTO island_bans (island_uuid, banned_player) VALUES (?, ?);");
    }

    public CompletableFuture<Void> updateCoopPlayer(UUID islandUuid, UUID playerUuid) {
        return executeUpdate(s -> {
            s.setString(1, islandUuid.toString());
            s.setString(2, playerUuid.toString());
        }, "INSERT INTO island_coops (island_uuid, cooped_player) VALUES (?, ?);");
    }

    public CompletableFuture<Void> updateIslandLevel(UUID islandUuid, int level) {
        return executeUpdate(s -> {
            s.setString(1, islandUuid.toString());
            s.setInt(2, level);
            s.setInt(3, level);
        }, "INSERT INTO island_levels (island_uuid, level) VALUES (?, ?) ON DUPLICATE KEY UPDATE level = ?;");
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        return executeUpdate(s -> s.setString(1, islandUuid.toString()), "DELETE FROM island_levels WHERE island_uuid = ?;").thenCompose(v -> executeUpdate(s -> s.setString(1, islandUuid.toString()), "DELETE FROM island_bans WHERE island_uuid = ?;")).thenCompose(v -> executeUpdate(s -> s.setString(1, islandUuid.toString()), "DELETE FROM island_coops WHERE island_uuid = ?;")).thenCompose(v -> executeUpdate(s -> s.setString(1, islandUuid.toString()), "DELETE FROM island_warps WHERE island_uuid = ?;")).thenCompose(v -> executeUpdate(s -> s.setString(1, islandUuid.toString()), "DELETE FROM island_homes WHERE island_uuid = ?;")).thenCompose(v -> executeUpdate(s -> s.setString(1, islandUuid.toString()), "DELETE FROM island_players WHERE island_uuid = ?;")).thenCompose(v -> executeUpdate(s -> s.setString(1, islandUuid.toString()), "DELETE FROM islands WHERE island_uuid = ?;"));
    }


    public CompletableFuture<Void> deleteIslandPlayer(UUID islandUuid, UUID playerUuid) {
        return executeUpdate(s -> s.setString(1, playerUuid.toString()), "DELETE FROM island_warps WHERE player_uuid = ?;").thenCompose(v -> executeUpdate(s -> s.setString(1, playerUuid.toString()), "DELETE FROM island_homes WHERE player_uuid = ?;")).thenCompose(v -> executeUpdate(s -> {
            s.setString(1, playerUuid.toString());
            s.setString(2, islandUuid.toString());
        }, "DELETE FROM island_players WHERE player_uuid = ? AND island_uuid = ?;"));
    }

    public CompletableFuture<Void> deleteHomePoint(UUID islandUuid, UUID playerUuid, String homeName) {
        return executeUpdate(s -> {
            s.setString(1, islandUuid.toString());
            s.setString(2, playerUuid.toString());
            s.setString(3, homeName);
        }, "DELETE FROM island_homes WHERE island_uuid = ? AND player_uuid = ? AND home_name = ?;");
    }

    public CompletableFuture<Void> deleteWarpPoint(UUID islandUuid, UUID playerUuid, String warpName) {
        return executeUpdate(s -> {
            s.setString(1, islandUuid.toString());
            s.setString(2, playerUuid.toString());
            s.setString(3, warpName);
        }, "DELETE FROM island_warps WHERE island_uuid = ? AND player_uuid = ? AND warp_name = ?;");
    }

    public CompletableFuture<Void> deleteBanPlayer(UUID islandUuid, UUID playerUuid) {
        return executeUpdate(s -> {
            s.setString(1, islandUuid.toString());
            s.setString(2, playerUuid.toString());
        }, "DELETE FROM island_bans WHERE island_uuid = ? AND banned_player = ?;");
    }

    public CompletableFuture<Void> deleteCoopPlayer(UUID islandUuid, UUID playerUuid) {
        return executeUpdate(s -> {
            s.setString(1, islandUuid.toString());
            s.setString(2, playerUuid.toString());
        }, "DELETE FROM island_coops WHERE island_uuid = ? AND cooped_player = ?;");
    }

    public CompletableFuture<Void> deleteAllCoopOfPlayer(UUID playerUuid) {
        return executeUpdate(s -> s.setString(1, playerUuid.toString()), "DELETE FROM island_coops WHERE cooped_player = ?;");
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