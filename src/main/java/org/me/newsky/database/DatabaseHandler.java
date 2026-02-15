package org.me.newsky.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

public class DatabaseHandler {

    private final NewSky plugin;
    private final HikariDataSource dataSource;
    private final String prefix;

    public DatabaseHandler(NewSky plugin, ConfigHandler config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.prefix = Objects.requireNonNull(config, "config").getMySQLTablePrefix();

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

    private void inTransaction(String name, ConnectionConsumer work) {
        try (Connection connection = getConnection()) {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                work.use(connection);
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    plugin.severe("Database rollback failed for tx: " + name, rollbackEx);
                }
                plugin.severe("Database transaction failed: " + name, e);
                throw new RuntimeException(e);
            } finally {
                try {
                    connection.setAutoCommit(oldAutoCommit);
                } catch (SQLException ignored) {
                    // ignore
                }
            }
        } catch (SQLException e) {
            plugin.severe("Database transaction connection failed: " + name, e);
            throw new RuntimeException(e);
        }
    }

    private void executeUpdate(Connection connection, String query, PreparedStatementConsumer consumer) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            consumer.use(statement);
            statement.executeUpdate();
        }
    }

    public void executeUpdate(String query, PreparedStatementConsumer consumer) {
        try (Connection connection = getConnection()) {
            executeUpdate(connection, query, consumer);
        } catch (SQLException e) {
            plugin.severe("Database Update failed: " + query, e);
            throw new RuntimeException(e);
        }
    }

    private void executeQuery(Connection connection, String query, PreparedStatementConsumer consumer, ResultProcessor processor) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            consumer.use(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                processor.process(resultSet);
            }
        }
    }

    public void executeQuery(String query, PreparedStatementConsumer consumer, ResultProcessor processor) {
        try (Connection connection = getConnection()) {
            executeQuery(connection, query, consumer, processor);
        } catch (SQLException e) {
            plugin.severe("Database Query failed: " + query, e);
            throw new RuntimeException(e);
        }
    }

    // ================================================================================================================
    // Table creation
    // ================================================================================================================

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
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "islands (" + "island_uuid CHAR(36) NOT NULL," + "`lock` BOOLEAN NOT NULL DEFAULT FALSE," + "pvp BOOLEAN NOT NULL DEFAULT FALSE," + "PRIMARY KEY (island_uuid)" + ") ENGINE=InnoDB;", stmt -> {
        });
    }

    private void createIslandPlayersTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_players (" + "player_uuid CHAR(36) NOT NULL," + "island_uuid CHAR(36) NOT NULL," + "role VARCHAR(56) NOT NULL," + "PRIMARY KEY (player_uuid, island_uuid)," + "CONSTRAINT fk_island_players_island " + "FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid) " + "ON DELETE CASCADE" + ") ENGINE=InnoDB;", stmt -> {
        });
    }

    private void createIslandHomesTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_homes (" + "player_uuid CHAR(36) NOT NULL," + "home_name VARCHAR(32) NOT NULL," + "home_location VARCHAR(256)," + "island_uuid CHAR(36) NOT NULL," + "PRIMARY KEY (player_uuid, home_name)," + "KEY idx_island_homes_island (island_uuid)," + "CONSTRAINT fk_island_homes_island " + "FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid) " + "ON DELETE CASCADE," + "CONSTRAINT fk_island_homes_player_membership " + "FOREIGN KEY (player_uuid, island_uuid) REFERENCES " + prefix + "island_players(player_uuid, island_uuid) " + "ON DELETE CASCADE" + ") ENGINE=InnoDB;", stmt -> {
        });
    }

    private void createIslandWarpsTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_warps (" + "player_uuid CHAR(36) NOT NULL," + "warp_name VARCHAR(32) NOT NULL," + "warp_location VARCHAR(256)," + "island_uuid CHAR(36) NOT NULL," + "PRIMARY KEY (player_uuid, warp_name)," + "KEY idx_island_warps_island (island_uuid)," + "CONSTRAINT fk_island_warps_island " + "FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid) " + "ON DELETE CASCADE," + "CONSTRAINT fk_island_warps_player_membership " + "FOREIGN KEY (player_uuid, island_uuid) REFERENCES " + prefix + "island_players(player_uuid, island_uuid) " + "ON DELETE CASCADE" + ") ENGINE=InnoDB;", stmt -> {
        });
    }

    private void createIslandBanTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_bans (" + "island_uuid CHAR(36) NOT NULL," + "banned_player CHAR(36) NOT NULL," + "PRIMARY KEY (island_uuid, banned_player)," + "CONSTRAINT fk_island_bans_island " + "FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid) " + "ON DELETE CASCADE" + ") ENGINE=InnoDB;", stmt -> {
        });
    }

    private void createIslandCoopTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_coops (" + "island_uuid CHAR(36) NOT NULL," + "cooped_player CHAR(36) NOT NULL," + "PRIMARY KEY (island_uuid, cooped_player)," + "CONSTRAINT fk_island_coops_island " + "FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid) " + "ON DELETE CASCADE" + ") ENGINE=InnoDB;", stmt -> {
        });
    }

    public void createIslandLevelsTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_levels (" + "island_uuid CHAR(36) NOT NULL," + "level INT NOT NULL," + "PRIMARY KEY (island_uuid)," + "CONSTRAINT fk_island_levels_island " + "FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid) " + "ON DELETE CASCADE" + ") ENGINE=InnoDB;", stmt -> {
        });
    }

    private void createIslandUpgradesTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_upgrades (" + "island_uuid CHAR(36) NOT NULL," + "upgrade_id VARCHAR(64) NOT NULL," + "level INT NOT NULL," + "PRIMARY KEY (island_uuid, upgrade_id)," + "CONSTRAINT fk_island_upgrades_island " + "FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid) " + "ON DELETE CASCADE" + ") ENGINE=InnoDB;", stmt -> {
        });
    }

    public void createPlayerUuidTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "player_uuid (" + "uuid CHAR(36) NOT NULL," + "name VARCHAR(16) NOT NULL," + "PRIMARY KEY (uuid)," + "KEY idx_player_uuid_name (name)" + ") ENGINE=InnoDB;", stmt -> {
        });
    }

    // ================================================================================================================
    // Selects
    // ================================================================================================================

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

    // ================================================================================================================
    // Writes (transaction-protected where multi-statement)
    // ================================================================================================================

    public void addIslandData(UUID islandUuid, UUID ownerUuid, String homePoint) {
        inTransaction("addIslandData island=" + islandUuid, connection -> {
            executeUpdate(connection, "INSERT INTO " + prefix + "islands (island_uuid) VALUES (?);", stmt -> stmt.setString(1, islandUuid.toString()));

            executeUpdate(connection, "INSERT INTO " + prefix + "island_players (player_uuid, island_uuid, role) VALUES (?, ?, ?) " + "ON DUPLICATE KEY UPDATE role = ?;", stmt -> {
                stmt.setString(1, ownerUuid.toString());
                stmt.setString(2, islandUuid.toString());
                stmt.setString(3, "owner");
                stmt.setString(4, "owner");
            });

            executeUpdate(connection, "INSERT INTO " + prefix + "island_homes (player_uuid, island_uuid, home_name, home_location) VALUES (?, ?, ?, ?) " + "ON DUPLICATE KEY UPDATE home_location = ?;", stmt -> {
                stmt.setString(1, ownerUuid.toString());
                stmt.setString(2, islandUuid.toString());
                stmt.setString(3, "default");
                stmt.setString(4, homePoint);
                stmt.setString(5, homePoint);
            });
        });
    }

    public void addIslandPlayer(UUID islandUuid, UUID playerUuid, String role, String homePoint) {
        inTransaction("addIslandPlayer island=" + islandUuid + " player=" + playerUuid, connection -> {
            executeUpdate(connection, "INSERT INTO " + prefix + "island_players (player_uuid, island_uuid, role) VALUES (?, ?, ?) " + "ON DUPLICATE KEY UPDATE role = ?;", stmt -> {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, islandUuid.toString());
                stmt.setString(3, role);
                stmt.setString(4, role);
            });

            executeUpdate(connection, "INSERT INTO " + prefix + "island_homes (player_uuid, island_uuid, home_name, home_location) VALUES (?, ?, ?, ?) " + "ON DUPLICATE KEY UPDATE home_location = ?;", stmt -> {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, islandUuid.toString());
                stmt.setString(3, "default");
                stmt.setString(4, homePoint);
                stmt.setString(5, homePoint);
            });
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
        inTransaction("updateIslandOwner island=" + islandUuid, connection -> {
            executeUpdate(connection, "UPDATE " + prefix + "island_players SET role = ? WHERE player_uuid = ? AND island_uuid = ?;", stmt -> {
                stmt.setString(1, "member");
                stmt.setString(2, oldOwnerUuid.toString());
                stmt.setString(3, islandUuid.toString());
            });

            executeUpdate(connection, "UPDATE " + prefix + "island_players SET role = ? WHERE player_uuid = ? AND island_uuid = ?;", stmt -> {
                stmt.setString(1, "owner");
                stmt.setString(2, newOwnerUuid.toString());
                stmt.setString(3, islandUuid.toString());
            });
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
        executeUpdate("DELETE FROM " + prefix + "islands WHERE island_uuid = ?;", stmt -> stmt.setString(1, islandUuid.toString()));
    }

    public void deleteIslandPlayer(UUID islandUuid, UUID playerUuid) {
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

    // ================================================================================================================
    // Functional interfaces
    // ================================================================================================================

    @FunctionalInterface
    public interface ResultProcessor {
        void process(ResultSet resultSet) throws SQLException;
    }

    @FunctionalInterface
    public interface PreparedStatementConsumer {
        void use(PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    private interface ConnectionConsumer {
        void use(Connection connection) throws SQLException;
    }
}
