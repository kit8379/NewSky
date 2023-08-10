package org.me.newsky.handler;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.me.newsky.NewSky;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class DatabaseHandler {

    private final HikariDataSource dataSource;
    private final NewSky plugin;
    private final ConfigHandler config;

    public DatabaseHandler(String host, int port, String database, String username, String password, NewSky plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigHandler();

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
        createTable();
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        dataSource.close();
    }

    public void asyncExecuteQuery(String query, ResultProcessor processor) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(query);
                 ResultSet resultSet = statement.executeQuery()) {
                processor.process(resultSet);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void asyncExecuteUpdate(PreparedStatementConsumer consumer, String query) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(query)) {
                consumer.use(statement);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @FunctionalInterface
    public interface ResultProcessor {
        void process(ResultSet resultSet) throws SQLException;
    }

    @FunctionalInterface
    public interface PreparedStatementConsumer {
        void use(PreparedStatement statement) throws SQLException;
    }

    
    public void createTable() {
        createIslandDataTable();
        createIslandMembersTable();
    }

    public void createIslandDataTable() {
        String sqlCommand = "CREATE TABLE IF NOT EXISTS island_data (" +
                "island_uuid VARCHAR(56) PRIMARY KEY," +
                "owner_uuid VARCHAR(56) NOT NULL," +
                "level INT(11) NOT NULL" +
                ");";

        asyncExecuteUpdate(PreparedStatement::execute, sqlCommand);
    }

    public void createIslandMembersTable() {
        String sqlCommand = "CREATE TABLE IF NOT EXISTS island_members (" +
                "member_uuid VARCHAR(56) PRIMARY KEY," +
                "island_uuid VARCHAR(56) NOT NULL," +
                "FOREIGN KEY (island_uuid) REFERENCES island_data(island_uuid)" +
                ");";
        asyncExecuteUpdate(PreparedStatement::execute, sqlCommand);
    }

    // Fetch all island data for caching
    public void selectAllIslandData(ResultProcessor processor) {
        String query = "SELECT * FROM island_data";
        asyncExecuteQuery(query, processor);
    }

    // Fetch all island members for caching
    public void selectAllIslandMembers(ResultProcessor processor) {
        String query = "SELECT * FROM island_members";
        asyncExecuteQuery(query, processor);
    }

    // Update island data by UUID
    public void updateIslandData(UUID islandUuid, UUID ownerUuid, int level) {
        String query = "INSERT INTO island_data (island_uuid, owner_uuid, level) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE owner_uuid = ?, level = ?";
        asyncExecuteUpdate(statement -> {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, ownerUuid.toString());
            statement.setInt(3, level);
            statement.setString(4, ownerUuid.toString());
            statement.setInt(5, level);
            statement.execute();
        }, query);
    }

    // Add a member to an island
    public void addIslandMember(UUID islandUuid, UUID memberUuid) {
        String query = "INSERT INTO island_members (member_uuid ,island_uuid) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE island_uuid = ?";
        asyncExecuteUpdate(statement -> {
            statement.setString(1, memberUuid.toString());
            statement.setString(2, islandUuid.toString());
            statement.setString(3, islandUuid.toString());
            statement.execute();
        }, query);
    }

    // Delete a member from an island
    public void deleteIslandMember(UUID islandUuid, UUID memberUuid) {
        String query = "DELETE FROM island_members WHERE member_uuid = ? AND island_uuid = ?";
        asyncExecuteUpdate(statement -> {
            statement.setString(1, memberUuid.toString());
            statement.setString(2, islandUuid.toString());
            statement.execute();
        }, query);
    }

    // Delete island data by UUID
    public void deleteIslandData(UUID islandUuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String query1 = "DELETE FROM island_members WHERE island_uuid = ?";
            try (Connection connection1 = getConnection();
                 PreparedStatement statement1 = connection1.prepareStatement(query1)) {
                statement1.setString(1, islandUuid.toString());
                statement1.execute();
            } catch (SQLException e) {
                e.printStackTrace();
            }

            String query2 = "DELETE FROM island_data WHERE island_uuid = ?";
            try (Connection connection2 = getConnection();
                 PreparedStatement statement2 = connection2.prepareStatement(query2)) {
                statement2.setString(1, islandUuid.toString());
                statement2.execute();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
}
