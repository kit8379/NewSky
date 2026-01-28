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
    private final String prefix;

    public DatabaseHandler(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;
        this.prefix = config.getMySQLTablePrefix();

        String host = config.getMySQLHost();
        int port = config.getMySQLPort();
        String database = config.getMySQLDB();
        String username = config.getMySQLUsername();
        String password = config.getMySQLPassword();
        boolean useSsl = config.getMySQLUseSSL();
        String properties = config.getMySQLProperties();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSsl + "&" + properties);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
        hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
        hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
        hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
        hikariConfig.addDataSourceProperty("maintainTimeStats", "false");

        this.dataSource = new HikariDataSource(hikariConfig);
    }


    public void close() {
        dataSource.close();
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void executeUpdate(String query, PreparedStatementConsumer consumer) {
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            consumer.use(statement);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.severe("Database Update failed: " + query, e);
            throw new RuntimeException(e);
        }
    }

    public void executeQuery(String query, PreparedStatementConsumer consumer, ResultProcessor processor) {
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            consumer.use(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                processor.process(resultSet);
            }
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
        createIslandUpgradesTable();
        createPlayerUuidTable();
    }

    private void createIslandDataTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "islands (island_uuid VARCHAR(56) PRIMARY KEY, `lock` BOOLEAN NOT NULL DEFAULT FALSE, pvp BOOLEAN NOT NULL DEFAULT FALSE);", PreparedStatement::execute);
    }

    private void createIslandPlayersTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_players (player_uuid VARCHAR(56), island_uuid VARCHAR(56) NOT NULL, role VARCHAR(56) NOT NULL, FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid), PRIMARY KEY (player_uuid, island_uuid));", PreparedStatement::execute);
    }

    private void createIslandHomesTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_homes (player_uuid VARCHAR(56), home_name VARCHAR(56), home_location VARCHAR(256), island_uuid VARCHAR(56), PRIMARY KEY (player_uuid, home_name), FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid));", PreparedStatement::execute);
    }

    private void createIslandWarpsTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_warps (player_uuid VARCHAR(56), warp_name VARCHAR(56), warp_location VARCHAR(256), island_uuid VARCHAR(56), PRIMARY KEY (player_uuid, warp_name), FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid));", PreparedStatement::execute);
    }

    private void createIslandBanTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_bans (island_uuid VARCHAR(56), banned_player VARCHAR(56), PRIMARY KEY (island_uuid, banned_player), FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid));", PreparedStatement::execute);
    }

    private void createIslandCoopTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_coops (" + "island_uuid VARCHAR(56) NOT NULL, " + "cooped_player VARCHAR(56) NOT NULL, " + "PRIMARY KEY (island_uuid, cooped_player), " + "FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid)" + ");", PreparedStatement::execute);
    }

    public void createIslandLevelsTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_levels (" + "island_uuid VARCHAR(56) PRIMARY KEY, " + "level INT NOT NULL" + ");", PreparedStatement::execute);
    }

    private void createIslandUpgradesTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_upgrades (" + "island_uuid VARCHAR(56) NOT NULL, " + "upgrade_id VARCHAR(64) NOT NULL, " + "level INT NOT NULL, " + "PRIMARY KEY (island_uuid, upgrade_id), " + "FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid)" + ");", PreparedStatement::execute);
    }

    public void createPlayerUuidTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "player_uuid (" + "uuid VARCHAR(56) PRIMARY KEY, " + "name VARCHAR(64) NOT NULL" + ");", PreparedStatement::execute);
    }

    public void selectAllIslandData(ResultProcessor processor) {
        executeQuery("SELECT * FROM " + prefix + "islands", stmt -> {
        }, processor);
    }

    public void selectAllIslandPlayers(ResultProcessor processor) {
        executeQuery("SELECT * FROM " + prefix + "island_players", stmt -> {
        }, processor);
    }

    public void selectAllIslandHomes(ResultProcessor processor) {
        executeQuery("SELECT * FROM " + prefix + "island_homes", stmt -> {
        }, processor);
    }

    public void selectAllIslandWarps(ResultProcessor processor) {
        executeQuery("SELECT * FROM " + prefix + "island_warps", stmt -> {
        }, processor);
    }

    public void selectAllIslandBans(ResultProcessor processor) {
        executeQuery("SELECT * FROM " + prefix + "island_bans", stmt -> {
        }, processor);
    }

    public void selectAllIslandCoops(ResultProcessor processor) {
        executeQuery("SELECT * FROM " + prefix + "island_coops", stmt -> {
        }, processor);
    }

    public void selectAllIslandLevels(ResultProcessor processor) {
        executeQuery("SELECT * FROM " + prefix + "island_levels", stmt -> {
        }, processor);
    }

    public void selectAllIslandUpgrades(ResultProcessor processor) {
        executeQuery("SELECT * FROM " + prefix + "island_upgrades", stmt -> {
        }, processor);
    }

    public void selectAllPlayerUuid(ResultProcessor processor) {
        executeQuery("SELECT uuid, name FROM " + prefix + "player_uuid", stmt -> {
        }, processor);
    }

    public void selectIslandData(UUID islandUuid, ResultProcessor processor) {
        executeQuery("SELECT * FROM " + prefix + "islands WHERE island_uuid = ?", stmt -> stmt.setString(1, islandUuid.toString()), processor);
    }

    public void selectIslandPlayers(UUID islandUuid, ResultProcessor processor) {
        executeQuery("SELECT * FROM " + prefix + "island_players WHERE island_uuid = ?", stmt -> stmt.setString(1, islandUuid.toString()), processor);
    }

    public void selectIslandHomes(UUID islandUuid, ResultProcessor processor) {
        executeQuery("SELECT * FROM " + prefix + "island_homes WHERE island_uuid = ?", stmt -> stmt.setString(1, islandUuid.toString()), processor);
    }

    public void selectIslandWarps(UUID islandUuid, ResultProcessor processor) {
        executeQuery("SELECT * FROM " + prefix + "island_warps WHERE island_uuid = ?", stmt -> stmt.setString(1, islandUuid.toString()), processor);
    }

    public void selectIslandBans(UUID islandUuid, ResultProcessor processor) {
        executeQuery("SELECT * FROM " + prefix + "island_bans WHERE island_uuid = ?", stmt -> stmt.setString(1, islandUuid.toString()), processor);
    }

    public void selectIslandCoops(UUID islandUuid, ResultProcessor processor) {
        executeQuery("SELECT * FROM " + prefix + "island_coops WHERE island_uuid = ?", stmt -> stmt.setString(1, islandUuid.toString()), processor);
    }

    public void selectIslandLevel(UUID islandUuid, ResultProcessor processor) {
        executeQuery("SELECT * FROM " + prefix + "island_levels WHERE island_uuid = ?", stmt -> stmt.setString(1, islandUuid.toString()), processor);
    }

    public void selectIslandUpgrades(UUID islandUuid, ResultProcessor processor) {
        executeQuery("SELECT * FROM " + prefix + "island_upgrades WHERE island_uuid = ?", stmt -> stmt.setString(1, islandUuid.toString()), processor);
    }

    public void selectPlayerUuid(UUID playerUuid, ResultProcessor processor) {
        executeQuery("SELECT * FROM " + prefix + "player_uuid WHERE uuid = ?", stmt -> stmt.setString(1, playerUuid.toString()), processor);
    }

    public void addIslandData(UUID islandUuid, UUID ownerUuid, String homePoint) {
        executeUpdate("INSERT INTO " + prefix + "islands (island_uuid) VALUES (?);", stmt -> stmt.setString(1, islandUuid.toString()));

        executeUpdate("INSERT INTO " + prefix + "island_players (player_uuid, island_uuid, role) VALUES (?, ?, ?) " + "ON DUPLICATE KEY UPDATE role = ?;", stmt -> {
            stmt.setString(1, ownerUuid.toString());
            stmt.setString(2, islandUuid.toString());
            stmt.setString(3, "owner");
            stmt.setString(4, "owner");
        });

        executeUpdate("INSERT INTO " + prefix + "island_homes (player_uuid, island_uuid, home_name, home_location) VALUES (?, ?, ?, ?) " + "ON DUPLICATE KEY UPDATE home_location = ?;", stmt -> {
            stmt.setString(1, ownerUuid.toString());
            stmt.setString(2, islandUuid.toString());
            stmt.setString(3, "default");
            stmt.setString(4, homePoint);
            stmt.setString(5, homePoint);
        });
    }

    public void addIslandPlayer(UUID islandUuid, UUID playerUuid, String role, String homePoint) {
        executeUpdate("INSERT INTO " + prefix + "island_players (player_uuid, island_uuid, role) VALUES (?, ?, ?) " + "ON DUPLICATE KEY UPDATE role = ?;", stmt -> {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, islandUuid.toString());
            stmt.setString(3, role);
            stmt.setString(4, role);
        });

        executeUpdate("INSERT INTO " + prefix + "island_homes (player_uuid, island_uuid, home_name, home_location) VALUES (?, ?, ?, ?) " + "ON DUPLICATE KEY UPDATE home_location = ?;", stmt -> {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, islandUuid.toString());
            stmt.setString(3, "default");
            stmt.setString(4, homePoint);
            stmt.setString(5, homePoint);
        });
    }

    public void updateHomePoint(UUID islandUuid, UUID playerUuid, String homeName, String homeLocation) {
        executeUpdate("INSERT INTO " + prefix + "island_homes (player_uuid, island_uuid, home_name, home_location) VALUES (?, ?, ?, ?) " + "ON DUPLICATE KEY UPDATE home_location = ?;", stmt -> {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, islandUuid.toString());
            stmt.setString(3, homeName);
            stmt.setString(4, homeLocation);
            stmt.setString(5, homeLocation);
        });
    }

    public void updateWarpPoint(UUID islandUuid, UUID playerUuid, String warpName, String warpLocation) {
        executeUpdate("INSERT INTO " + prefix + "island_warps (player_uuid, island_uuid, warp_name, warp_location) VALUES (?, ?, ?, ?) " + "ON DUPLICATE KEY UPDATE warp_location = ?;", stmt -> {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, islandUuid.toString());
            stmt.setString(3, warpName);
            stmt.setString(4, warpLocation);
            stmt.setString(5, warpLocation);
        });
    }

    public void updateIslandLock(UUID islandUuid, boolean lock) {
        executeUpdate("UPDATE " + prefix + "islands SET `lock` = ? WHERE island_uuid = ?;", stmt -> {
            stmt.setBoolean(1, lock);
            stmt.setString(2, islandUuid.toString());
        });
    }

    public void updateIslandPvp(UUID islandUuid, boolean pvp) {
        executeUpdate("UPDATE " + prefix + "islands SET pvp = ? WHERE island_uuid = ?;", stmt -> {
            stmt.setBoolean(1, pvp);
            stmt.setString(2, islandUuid.toString());
        });
    }

    public void updateIslandOwner(UUID islandUuid, UUID oldOwnerUuid, UUID newOwnerUuid) {
        executeUpdate("UPDATE " + prefix + "island_players SET role = ? WHERE player_uuid = ? AND island_uuid = ?;", stmt -> {
            stmt.setString(1, "member");
            stmt.setString(2, oldOwnerUuid.toString());
            stmt.setString(3, islandUuid.toString());
        });

        executeUpdate("UPDATE " + prefix + "island_players SET role = ? WHERE player_uuid = ? AND island_uuid = ?;", stmt -> {
            stmt.setString(1, "owner");
            stmt.setString(2, newOwnerUuid.toString());
            stmt.setString(3, islandUuid.toString());
        });
    }

    public void updateBanPlayer(UUID islandUuid, UUID playerUuid) {
        executeUpdate("INSERT INTO " + prefix + "island_bans (island_uuid, banned_player) VALUES (?, ?);", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, playerUuid.toString());
        });
    }

    public void updateCoopPlayer(UUID islandUuid, UUID playerUuid) {
        executeUpdate("INSERT INTO " + prefix + "island_coops (island_uuid, cooped_player) VALUES (?, ?);", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, playerUuid.toString());
        });
    }

    public void updateIslandLevel(UUID islandUuid, int level) {
        executeUpdate("INSERT INTO " + prefix + "island_levels (island_uuid, level) VALUES (?, ?) " + "ON DUPLICATE KEY UPDATE level = ?;", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setInt(2, level);
            stmt.setInt(3, level);
        });
    }

    public void upsertIslandUpgrade(UUID islandUuid, String upgradeId, int level) {
        executeUpdate("INSERT INTO " + prefix + "island_upgrades (island_uuid, upgrade_id, level) VALUES (?, ?, ?) " + "ON DUPLICATE KEY UPDATE level = ?;", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, upgradeId);
            stmt.setInt(3, level);
            stmt.setInt(4, level);
        });
    }

    public void updatePlayerName(UUID uuid, String name) {
        executeUpdate("INSERT INTO " + prefix + "player_uuid (uuid, name) VALUES (?, ?) " + "ON DUPLICATE KEY UPDATE name = ?;", stmt -> {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
            stmt.setString(3, name);
        });
    }

    public void deleteIsland(UUID islandUuid) {
        executeUpdate("DELETE FROM " + prefix + "island_upgrades WHERE island_uuid = ?;", stmt -> stmt.setString(1, islandUuid.toString()));
        executeUpdate("DELETE FROM " + prefix + "island_levels WHERE island_uuid = ?;", stmt -> stmt.setString(1, islandUuid.toString()));
        executeUpdate("DELETE FROM " + prefix + "island_coops WHERE island_uuid = ?;", stmt -> stmt.setString(1, islandUuid.toString()));
        executeUpdate("DELETE FROM " + prefix + "island_bans WHERE island_uuid = ?;", stmt -> stmt.setString(1, islandUuid.toString()));
        executeUpdate("DELETE FROM " + prefix + "island_warps WHERE island_uuid = ?;", stmt -> stmt.setString(1, islandUuid.toString()));
        executeUpdate("DELETE FROM " + prefix + "island_homes WHERE island_uuid = ?;", stmt -> stmt.setString(1, islandUuid.toString()));
        executeUpdate("DELETE FROM " + prefix + "island_players WHERE island_uuid = ?;", stmt -> stmt.setString(1, islandUuid.toString()));
        executeUpdate("DELETE FROM " + prefix + "islands WHERE island_uuid = ?;", stmt -> stmt.setString(1, islandUuid.toString()));
    }

    public void deleteIslandPlayer(UUID islandUuid, UUID playerUuid) {
        executeUpdate("DELETE FROM " + prefix + "island_warps WHERE player_uuid = ?;", stmt -> stmt.setString(1, playerUuid.toString()));
        executeUpdate("DELETE FROM " + prefix + "island_homes WHERE player_uuid = ?;", stmt -> stmt.setString(1, playerUuid.toString()));
        executeUpdate("DELETE FROM " + prefix + "island_players WHERE player_uuid = ? AND island_uuid = ?;", stmt -> {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, islandUuid.toString());
        });
    }

    public void deleteHomePoint(UUID islandUuid, UUID playerUuid, String homeName) {
        executeUpdate("DELETE FROM " + prefix + "island_homes WHERE island_uuid = ? AND player_uuid = ? AND home_name = ?;", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, playerUuid.toString());
            stmt.setString(3, homeName);
        });
    }

    public void deleteWarpPoint(UUID islandUuid, UUID playerUuid, String warpName) {
        executeUpdate("DELETE FROM " + prefix + "island_warps WHERE island_uuid = ? AND player_uuid = ? AND warp_name = ?;", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, playerUuid.toString());
            stmt.setString(3, warpName);
        });
    }

    public void deleteBanPlayer(UUID islandUuid, UUID playerUuid) {
        executeUpdate("DELETE FROM " + prefix + "island_bans WHERE island_uuid = ? AND banned_player = ?;", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, playerUuid.toString());
        });
    }

    public void deleteCoopPlayer(UUID islandUuid, UUID playerUuid) {
        executeUpdate("DELETE FROM " + prefix + "island_coops WHERE island_uuid = ? AND cooped_player = ?;", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, playerUuid.toString());
        });
    }

    public void deleteAllCoopOfPlayer(UUID playerUuid) {
        executeUpdate("DELETE FROM " + prefix + "island_coops WHERE cooped_player = ?;", stmt -> stmt.setString(1, playerUuid.toString()));
    }

    public void deleteIslandUpgrade(UUID islandUuid, String upgradeId) {
        executeUpdate("DELETE FROM " + prefix + "island_upgrades WHERE island_uuid = ? AND upgrade_id = ?;", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, upgradeId);
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
}