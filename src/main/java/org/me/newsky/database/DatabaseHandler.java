package org.me.newsky.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
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

        String host = config.getDBHost();
        int port = config.getDBPort();
        String database = config.getDBName();
        String username = config.getDBUsername();
        String password = config.getDBPassword();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?autoReconnect=true");
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        this.dataSource = new HikariDataSource(hikariConfig);
    }

    public void close() {
        dataSource.close();
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void executeQuery(String query, ResultProcessor processor) {
        Runnable task = () -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(query);
                 ResultSet resultSet = statement.executeQuery()) {
                processor.process(resultSet);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    private void executeUpdate(PreparedStatementConsumer consumer, String query, boolean async) {
        Runnable task = () -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(query)) {
                consumer.use(statement);
                statement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };
        if (async) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
        else {
            task.run();
        }
    }

    @FunctionalInterface
    public interface ResultProcessor {
        void process(ResultSet resultSet) throws SQLException;
    }

    @FunctionalInterface
    public interface PreparedStatementConsumer {
        void use(PreparedStatement statement) throws SQLException;
    }

    public void createTables() {
        createIslandDataTable();
        createIslandPlayersTable();
    }

    private void createIslandDataTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS islands (island_uuid VARCHAR(56) PRIMARY KEY, level INT(11) NOT NULL);", false);
    }

    private void createIslandPlayersTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS island_players (player_uuid VARCHAR(56), island_uuid VARCHAR(56) NOT NULL, spawn VARCHAR(256) NOT NULL, role VARCHAR(56) NOT NULL, FOREIGN KEY (island_uuid) REFERENCES islands(island_uuid), PRIMARY KEY (player_uuid, island_uuid));", false);
    }

    // Fetch all island data for caching
    public void selectAllIslandData(ResultProcessor processor) {
        executeQuery("SELECT * FROM islands", processor);
    }

    // Fetch all island players for caching
    public void selectAllIslandPlayers(ResultProcessor processor) {
        executeQuery("SELECT * FROM island_players", processor);
    }

    // Update island data by UUID
    public void updateIslandData(UUID islandUuid) {
        executeUpdate(statement -> {
            statement.setString(1, islandUuid.toString());
            statement.setInt(2, 0);
            statement.setInt(3, 0);
        }, "INSERT INTO islands (island_uuid, level) VALUES (?, ?) ON DUPLICATE KEY UPDATE level = ?", true);
    }

    // Associate a player to an island (either as owner or member)
    public void addIslandPlayer(UUID playerUuid, UUID islandUuid, String spawnLocation, String role) {
        executeUpdate(statement -> {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, islandUuid.toString());
            statement.setString(3, spawnLocation);
            statement.setString(4, role);
        }, "INSERT INTO island_players (player_uuid, island_uuid, spawn, role) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE spawn = ?, role = ?", true);
    }

    // Delete a player association from an island
    public void deleteIslandPlayer(UUID playerUuid, UUID islandUuid) {
        executeUpdate(statement -> {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, islandUuid.toString());
        }, "DELETE FROM island_players WHERE player_uuid = ? AND island_uuid = ?", true);
    }

    // Delete island data by UUID
    public void deleteIslandData(UUID islandUuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            executeUpdate(statement -> statement.setString(1, islandUuid.toString()), "DELETE FROM island_players WHERE island_uuid = ?", false);
            executeUpdate(statement -> statement.setString(1, islandUuid.toString()), "DELETE FROM islands WHERE island_uuid = ?", false);
        });
    }
}
