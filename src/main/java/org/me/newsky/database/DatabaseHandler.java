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
        String host = config.getDBHost();
        int port = config.getDBPort();
        String database = config.getDBName();
        String username = config.getDBUsername();
        String password = config.getDBPassword();
        String properties = config.getDBProperties();
        int maxPoolSize = config.getDBMaxPoolSize();
        int connectionTimeout = config.getDBConnectionTimeout();
        String cachePrepStmts = config.getDBCachePrepStmts();
        String prepStmtCacheSize = config.getDBPrepStmtCacheSize();
        String prepStmtCacheSqlLimit = config.getDBPrepStmtCacheSqlLimit();

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
    }

    private void createIslandDataTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS islands (" + "island_uuid VARCHAR(56) PRIMARY KEY, " + "`lock` BOOLEAN NOT NULL DEFAULT FALSE);").join();
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

    public void addIslandData(UUID islandUuid) {
        executeUpdate(statement -> {
            statement.setString(1, islandUuid.toString());
        }, "INSERT INTO islands (island_uuid) VALUES (?)");
    }

    public void addOrUpdateIslandPlayer(UUID playerUuid, UUID islandUuid, String role) {
        executeUpdate(statement -> {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, islandUuid.toString());
            statement.setString(3, role);
            statement.setString(4, role);
        }, "INSERT INTO island_players (player_uuid, island_uuid, role) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE role = ?;");
    }


    public void addOrUpdateWarpPoint(UUID playerUuid, UUID islandUuid, String warpName, String warpLocation) {
        executeUpdate(statement -> {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, islandUuid.toString());
            statement.setString(3, warpName);
            statement.setString(4, warpLocation);
            statement.setString(5, warpLocation);
        }, "INSERT INTO island_warps (player_uuid, island_uuid, warp_name, warp_location) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE warp_location = ?;");
    }

    public void addOrUpdateHomePoint(UUID playerUuid, UUID islandUuid, String homeName, String homeLocation) {
        executeUpdate(statement -> {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, islandUuid.toString());
            statement.setString(3, homeName);
            statement.setString(4, homeLocation);
            statement.setString(5, homeLocation);
        }, "INSERT INTO island_homes (player_uuid, island_uuid, home_name, home_location) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE home_location = ?;");
    }


    public void updateIslandLock(UUID islandUuid, boolean lock) {
        executeUpdate(statement -> {
            statement.setBoolean(1, lock);
            statement.setString(2, islandUuid.toString());
        }, "UPDATE islands SET lock = ? WHERE island_uuid = ?;");
    }

    public void deleteIsland(UUID islandUuid) {
        executeUpdate(statement -> {
            statement.setString(1, islandUuid.toString());
        }, "DELETE FROM island_warps WHERE island_uuid = ?;").thenCompose(v -> executeUpdate(statement -> {
            statement.setString(1, islandUuid.toString());
        }, "DELETE FROM island_homes WHERE island_uuid = ?;")).thenCompose(v -> executeUpdate(statement -> {
            statement.setString(1, islandUuid.toString());
        }, "DELETE FROM island_players WHERE island_uuid = ?;")).thenCompose(v -> executeUpdate(statement -> {
            statement.setString(1, islandUuid.toString());
        }, "DELETE FROM islands WHERE island_uuid = ?;"));
    }


    public void deleteIslandPlayer(UUID playerUuid, UUID islandUuid) {
        executeUpdate(statement -> {
            statement.setString(1, playerUuid.toString());
        }, "DELETE FROM island_warps WHERE player_uuid = ?;").thenCompose(v -> executeUpdate(statement -> {
            statement.setString(1, playerUuid.toString());
        }, "DELETE FROM island_homes WHERE player_uuid = ?;")).thenCompose(v -> executeUpdate(statement -> {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, islandUuid.toString());
        }, "DELETE FROM island_players WHERE player_uuid = ? AND island_uuid = ?;"));
    }

    public void deleteWarpPoint(UUID islandUuid, UUID playerUuid, String warpName) {
        executeUpdate(statement -> {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, playerUuid.toString());
            statement.setString(3, warpName);
        }, "DELETE FROM island_warps WHERE island_uuid = ? AND player_uuid = ? AND warp_name = ?;");
    }

    public void deleteHomePoint(UUID islandUuid, UUID playerUuid, String homeName) {
        executeUpdate(statement -> {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, playerUuid.toString());
            statement.setString(3, homeName);
        }, "DELETE FROM island_homes WHERE island_uuid = ? AND player_uuid = ? AND home_name = ?;");
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