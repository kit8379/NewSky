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
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(query);
                 ResultSet resultSet = statement.executeQuery()) {
                processor.process(resultSet);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private CompletableFuture<Void> executeUpdate(PreparedStatementConsumer consumer, String query) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(query)) {
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
        createIslandWarpsTable();
    }

    private void createIslandDataTable() {
        executeUpdate(PreparedStatement::execute,
                "CREATE TABLE IF NOT EXISTS islands (island_uuid VARCHAR(56) PRIMARY KEY, level INT(11) NOT NULL);").join();
    }

    private void createIslandPlayersTable() {
        executeUpdate(PreparedStatement::execute,
                "CREATE TABLE IF NOT EXISTS island_players (player_uuid VARCHAR(56), island_uuid VARCHAR(56) NOT NULL, spawn VARCHAR(256) NOT NULL, role VARCHAR(56) NOT NULL, FOREIGN KEY (island_uuid) REFERENCES islands(island_uuid), PRIMARY KEY (player_uuid, island_uuid));").join();
    }

    private void createIslandWarpsTable() {
        executeUpdate(PreparedStatement::execute,
                "CREATE TABLE IF NOT EXISTS island_warps (player_uuid VARCHAR(56), island_uuid VARCHAR(56), warp_name VARCHAR(56), warp_location VARCHAR(256), PRIMARY KEY (player_uuid, warp_name), FOREIGN KEY (island_uuid) REFERENCES islands(island_uuid));").join();
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

    public void updateIslandData(UUID islandUuid, int level) {
        executeUpdate(statement -> {
            statement.setString(1, islandUuid.toString());
            statement.setInt(2, level);
            statement.setInt(3, level);
        }, "INSERT INTO islands (island_uuid, level) VALUES (?, ?) ON DUPLICATE KEY UPDATE level = ?");
    }

    public void addIslandPlayer(UUID playerUuid, UUID islandUuid, String spawnLocation, String role) {
        executeUpdate(statement -> {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, islandUuid.toString());
            statement.setString(3, spawnLocation);
            statement.setString(4, role);
            statement.setString(5, spawnLocation);
            statement.setString(6, role);
        }, "INSERT INTO island_players (player_uuid, island_uuid, spawn, role) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE spawn = ?, role = ?");
    }

    public void addWarpPoint(UUID playerUuid, UUID islandUuid, String warpName, String warpLocation) {
        executeUpdate(statement -> {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, islandUuid.toString());
            statement.setString(3, warpName);
            statement.setString(4, warpLocation);
            statement.setString(5, warpLocation);
        }, "INSERT INTO island_warps (player_uuid, island_uuid, warp_name, warp_location) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE warp_location = ?");
    }

    public void deleteIslandData(UUID islandUuid) {
        // Delete all associated island players asynchronously
        CompletableFuture<Void> deletePlayersFuture = executeUpdate(statement -> statement.setString(1, islandUuid.toString()), "DELETE FROM island_players WHERE island_uuid = ?");
        // After deleting players, delete the island data
        deletePlayersFuture.thenRun(() -> executeUpdate(statement -> statement.setString(1, islandUuid.toString()), "DELETE FROM islands WHERE island_uuid = ?"));
    }

    public void deleteIslandPlayer(UUID playerUuid, UUID islandUuid) {
        executeUpdate(statement -> {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, islandUuid.toString());
        }, "DELETE FROM island_players WHERE player_uuid = ? AND island_uuid = ?");
    }

    public void deleteWarpPoint(UUID playerUuid, String warpName) {
        executeUpdate(statement -> {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, warpName);
        }, "DELETE FROM island_warps WHERE player_uuid = ? AND warp_name = ?");
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
