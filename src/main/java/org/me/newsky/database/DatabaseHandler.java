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
        String database = config.getMySQLName();
        String username = config.getMySQLUsername();
        String password = config.getMySQLPassword();
        String properties = config.getMySQLProperties();
        int maxPoolSize = config.getMySQLMaxPoolSize();
        int connectionTimeout = config.getMySQLConnectionTimeout();
        String cachePrepStmts = config.getMySQLCachePrepStmts();
        String prepStmtCacheSize = config.getMySQLPrepStmtCacheSize();
        String prepStmtCacheSqlLimit = config.getMySQLPrepStmtCacheSqlLimit();

        // HikariCP config
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

    private void executeQuery(String query, ResultProcessor processor) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(query); ResultSet resultSet = statement.executeQuery()) {
                processor.process(resultSet);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
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

    public void createTables() {
        createIslandDataTable();
        createIslandPlayersTable();
        createIslandHomesTable();
        createIslandWarpsTable();
        createIslandLevelsTable();
        createIslandBanTable();
    }

    private void createIslandDataTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS islands (" + "island_uuid VARCHAR(56) PRIMARY KEY, " + "`lock` BOOLEAN NOT NULL DEFAULT FALSE, " + "pvp BOOLEAN NOT NULL DEFAULT FALSE);").join();
    }

    private void createIslandPlayersTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS island_players (player_uuid VARCHAR(56), island_uuid VARCHAR(56) NOT NULL, role VARCHAR(56) NOT NULL, FOREIGN KEY (island_uuid) REFERENCES islands(island_uuid), PRIMARY KEY (player_uuid, island_uuid));").join();
    }

    private void createIslandHomesTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS island_homes (" + "player_uuid VARCHAR(56), " + "home_name VARCHAR(56), " + "home_location VARCHAR(256), " + "island_uuid VARCHAR(56), " + "PRIMARY KEY (player_uuid, home_name), " + "FOREIGN KEY (island_uuid) REFERENCES islands(island_uuid));").join();
    }

    private void createIslandWarpsTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS island_warps (" + "player_uuid VARCHAR(56), " + "warp_name VARCHAR(56), " + "warp_location VARCHAR(256), " + "island_uuid VARCHAR(56), " + "PRIMARY KEY (player_uuid, warp_name), " + "FOREIGN KEY (island_uuid) REFERENCES islands(island_uuid));").join();
    }

    public void createIslandLevelsTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS island_levels (" + "island_uuid VARCHAR(36) PRIMARY KEY, " + "level INT NOT NULL);").join();
    }

    private void createIslandBanTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS island_ban (" + "island_uuid VARCHAR(56), " + "banned_player VARCHAR(56), " + "PRIMARY KEY (island_uuid, banned_player), " + "FOREIGN KEY (island_uuid) REFERENCES islands(island_uuid));").join();
    }

    public void selectAllIslandData(ResultProcessor processor) {
        executeQuery("SELECT * FROM islands", processor);
    }

    public void selectAllIslandPlayers(ResultProcessor processor) {
        executeQuery("SELECT * FROM island_players", processor);
    }

    public void selectAllIslandWarps(ResultProcessor processor) {
        executeQuery("SELECT * FROM island_warps", processor);
    }

    public void selectAllIslandHomes(ResultProcessor processor) {
        executeQuery("SELECT * FROM island_homes", processor);
    }

    public void selectAllIslandLevels(ResultProcessor processor) {
        executeQuery("SELECT * FROM island_levels", processor);
    }

    public void selectAllIslandBans(ResultProcessor processor) {
        executeQuery("SELECT * FROM island_ban", processor);
    }

    public void addIslandData(UUID islandUuid) {
        executeUpdate(statement -> {
            statement.setString(1, islandUuid.toString());
        }, "INSERT INTO islands (island_uuid) VALUES (?)");
    }

    public void updateIslandPlayer(UUID islandUuid, UUID playerUuid, String role) {
        executeUpdate(statement -> {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, islandUuid.toString());
            statement.setString(3, role);
            statement.setString(4, role);
        }, "INSERT INTO island_players (player_uuid, island_uuid, role) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE role = ?;");
    }

    public void updateHomePoint(UUID islandUuid, UUID playerUuid, String homeName, String homeLocation) {
        executeUpdate(statement -> {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, islandUuid.toString());
            statement.setString(3, homeName);
            statement.setString(4, homeLocation);
            statement.setString(5, homeLocation);
        }, "INSERT INTO island_homes (player_uuid, island_uuid, home_name, home_location) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE home_location = ?;");
    }

    public void updateWarpPoint(UUID islandUuid, UUID playerUuid, String warpName, String warpLocation) {
        executeUpdate(statement -> {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, islandUuid.toString());
            statement.setString(3, warpName);
            statement.setString(4, warpLocation);
            statement.setString(5, warpLocation);
        }, "INSERT INTO island_warps (player_uuid, island_uuid, warp_name, warp_location) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE warp_location = ?;");
    }


    public void updateIslandLock(UUID islandUuid, boolean lock) {
        executeUpdate(statement -> {
            statement.setBoolean(1, lock);
            statement.setString(2, islandUuid.toString());
        }, "UPDATE islands SET lock = ? WHERE island_uuid = ?;");
    }

    public void updateIslandPvp(UUID islandUuid, boolean pvp) {
        executeUpdate(statement -> {
            statement.setBoolean(1, pvp);
            statement.setString(2, islandUuid.toString());
        }, "UPDATE islands SET pvp = ? WHERE island_uuid = ?;");
    }

    public void updateIslandOwner(UUID islandUuid, UUID playerUuid) {
        executeUpdate(statement -> {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, islandUuid.toString());
        }, "UPDATE island_players SET role = 'owner' WHERE player_uuid = ? AND island_uuid = ?;");
    }


    public void updateIslandLevel(UUID islandUuid, int level) {
        executeUpdate(statement -> {
            statement.setString(1, islandUuid.toString());
            statement.setInt(2, level);
            statement.setInt(3, level);
        }, "INSERT INTO island_levels (island_uuid, level) VALUES (?, ?) ON DUPLICATE KEY UPDATE level = ?;");
    }

    public void updateBanPlayer(UUID islandUuid, UUID playerUuid) {
        executeUpdate(statement -> {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, playerUuid.toString());
        }, "INSERT INTO island_ban (island_uuid, banned_player) VALUES (?, ?);");
    }

    public void deleteIsland(UUID islandUuid) {
        executeUpdate(statement -> {
            statement.setString(1, islandUuid.toString());
        }, "DELETE FROM island_levels WHERE island_uuid = ?;").thenCompose(v -> {
            return executeUpdate(statement -> {
                statement.setString(1, islandUuid.toString());
            }, "DELETE FROM island_warps WHERE island_uuid = ?;");
        }).thenCompose(v -> {
            return executeUpdate(statement -> {
                statement.setString(1, islandUuid.toString());
            }, "DELETE FROM island_homes WHERE island_uuid = ?;");
        }).thenCompose(v -> {
            return executeUpdate(statement -> {
                statement.setString(1, islandUuid.toString());
            }, "DELETE FROM island_players WHERE island_uuid = ?;");
        }).thenCompose(v -> {
            return executeUpdate(statement -> {
                statement.setString(1, islandUuid.toString());
            }, "DELETE FROM islands WHERE island_uuid = ?;");
        });
    }


    public void deleteIslandPlayer(UUID islandUuid, UUID playerUuid) {
        executeUpdate(statement -> {
            statement.setString(1, playerUuid.toString());
        }, "DELETE FROM island_warps WHERE player_uuid = ?;").thenCompose(v -> executeUpdate(statement -> {
            statement.setString(1, playerUuid.toString());
        }, "DELETE FROM island_homes WHERE player_uuid = ?;")).thenCompose(v -> executeUpdate(statement -> {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, islandUuid.toString());
        }, "DELETE FROM island_players WHERE player_uuid = ? AND island_uuid = ?;"));
    }

    public void deleteHomePoint(UUID islandUuid, UUID playerUuid, String homeName) {
        executeUpdate(statement -> {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, playerUuid.toString());
            statement.setString(3, homeName);
        }, "DELETE FROM island_homes WHERE island_uuid = ? AND player_uuid = ? AND home_name = ?;");
    }

    public void deleteWarpPoint(UUID islandUuid, UUID playerUuid, String warpName) {
        executeUpdate(statement -> {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, playerUuid.toString());
            statement.setString(3, warpName);
        }, "DELETE FROM island_warps WHERE island_uuid = ? AND player_uuid = ? AND warp_name = ?;");
    }

    public void deleteBanPlayer(UUID islandUuid, UUID playerUuid) { // Add this method
        executeUpdate(statement -> {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, playerUuid.toString());
        }, "DELETE FROM island_ban WHERE island_uuid = ? AND banned_player = ?;");
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