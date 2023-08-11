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

    private final HikariDataSource dataSource;
    private final NewSky plugin;

    public DatabaseHandler(String host, int port, String database, String username, String password, NewSky plugin) {
        this.plugin = plugin;

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
        createTables();
    }

    public void close() {
        dataSource.close();
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void executeQuery(String query, ResultProcessor processor, boolean async) {
        Runnable task = () -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(query);
                 ResultSet resultSet = statement.executeQuery()) {
                processor.process(resultSet);
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

    private void createTables() {
        createIslandDataTable();
        createIslandMembersTable();
    }

    private void createIslandDataTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS island_data (island_uuid VARCHAR(56) PRIMARY KEY, owner_uuid VARCHAR(56) NOT NULL, level INT(11) NOT NULL);", false);
    }

    private void createIslandMembersTable() {
        executeUpdate(PreparedStatement::execute, "CREATE TABLE IF NOT EXISTS island_members (member_uuid VARCHAR(56) PRIMARY KEY, island_uuid VARCHAR(56) NOT NULL, FOREIGN KEY (island_uuid) REFERENCES island_data(island_uuid));", false);
    }

    // Fetch all island data for caching
    public void selectAllIslandData(ResultProcessor processor) {
        executeQuery("SELECT * FROM island_data", processor, true);
    }

    // Fetch all island members for caching
    public void selectAllIslandMembers(ResultProcessor processor) {
        executeQuery("SELECT * FROM island_members", processor, true);
    }

    // Update island data by UUID
    public void updateIslandData(UUID islandUuid, UUID ownerUuid, int level) {
        executeUpdate(statement -> {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, ownerUuid.toString());
            statement.setInt(3, level);
            statement.setString(4, ownerUuid.toString());
            statement.setInt(5, level);
        }, "INSERT INTO island_data (island_uuid, owner_uuid, level) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE owner_uuid = ?, level = ?", true);
    }

    // Add a member to an island
    public void addIslandMember(UUID islandUuid, UUID memberUuid) {
        executeUpdate(statement -> {
            statement.setString(1, memberUuid.toString());
            statement.setString(2, islandUuid.toString());
            statement.setString(3, islandUuid.toString());
        }, "INSERT INTO island_members (member_uuid, island_uuid) VALUES (?, ?) ON DUPLICATE KEY UPDATE island_uuid = ?", true);
    }

    // Delete a member from an island
    public void deleteIslandMember(UUID islandUuid, UUID memberUuid) {
        executeUpdate(statement -> {
            statement.setString(1, memberUuid.toString());
            statement.setString(2, islandUuid.toString());
        }, "DELETE FROM island_members WHERE member_uuid = ? AND island_uuid = ?", true);
    }

    // Delete island data by UUID
    public void deleteIslandData(UUID islandUuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            executeUpdate(statement -> statement.setString(1, islandUuid.toString()), "DELETE FROM island_members WHERE island_uuid = ?", false);
            executeUpdate(statement -> statement.setString(1, islandUuid.toString()), "DELETE FROM island_data WHERE island_uuid = ?", false);
        });
    }
}
