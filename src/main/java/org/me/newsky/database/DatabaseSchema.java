package org.me.newsky.database;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

final class DatabaseSchema {

    private final HikariDataSource dataSource;
    private final String prefix;
    private final DatabaseHandler.ErrorSink errorSink;

    DatabaseSchema(HikariDataSource dataSource, String prefix, DatabaseHandler.ErrorSink errorSink) {
        this.dataSource = dataSource;
        this.prefix = prefix;
        this.errorSink = errorSink;
    }

    void createTables() {
        createIslandTable();
        createOperationTable();
        createPlayerTable();
        createHomeTable();
        createWarpTable();
        createBanTable();
        createCoopTable();
        createLevelTable();
        createUuidTable();

        ensureIslandColumn("state_version", "BIGINT NOT NULL DEFAULT 0");
        ensureIslandColumn("write_epoch", "CHAR(36) NULL");
        ensureIslandColumn("provision_state", "VARCHAR(16) NOT NULL DEFAULT 'ready'");
        deleteExpiredOperations();
    }

    private void createIslandTable() {
        execute("""
                CREATE TABLE IF NOT EXISTS %sislands (
                    island_uuid CHAR(36) NOT NULL,
                    `lock` BOOLEAN NOT NULL DEFAULT FALSE,
                    pvp BOOLEAN NOT NULL DEFAULT FALSE,
                    state_version BIGINT NOT NULL DEFAULT 0,
                    write_epoch CHAR(36) NULL,
                    provision_state VARCHAR(16) NOT NULL DEFAULT 'ready',
                    PRIMARY KEY (island_uuid)
                ) ENGINE=InnoDB
                """.formatted(prefix));
    }

    private void createOperationTable() {
        execute("""
                CREATE TABLE IF NOT EXISTS %sisland_operations (
                    operation_id CHAR(36) NOT NULL,
                    island_uuid CHAR(36) NOT NULL,
                    action VARCHAR(64) NOT NULL,
                    boolean_result BOOLEAN NOT NULL,
                    state_version BIGINT NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (operation_id),
                    KEY idx_island_operations_created (created_at),
                    CONSTRAINT fk_island_operations_island
                        FOREIGN KEY (island_uuid) REFERENCES %sislands(island_uuid)
                        ON DELETE CASCADE
                ) ENGINE=InnoDB
                """.formatted(prefix, prefix));
    }

    private void createPlayerTable() {
        execute("""
                CREATE TABLE IF NOT EXISTS %sisland_players (
                    player_uuid CHAR(36) NOT NULL,
                    island_uuid CHAR(36) NOT NULL,
                    role VARCHAR(56) NOT NULL,
                    PRIMARY KEY (player_uuid, island_uuid),
                    UNIQUE KEY uq_island_players_player_uuid (player_uuid),
                    KEY idx_island_players_island_uuid (island_uuid),
                    KEY idx_island_players_island_role (island_uuid, role),
                    CONSTRAINT fk_island_players_island
                        FOREIGN KEY (island_uuid) REFERENCES %sislands(island_uuid)
                        ON DELETE CASCADE
                ) ENGINE=InnoDB
                """.formatted(prefix, prefix));
    }

    private void createHomeTable() {
        execute("""
                CREATE TABLE IF NOT EXISTS %sisland_homes (
                    player_uuid CHAR(36) NOT NULL,
                    home_name VARCHAR(32) NOT NULL,
                    home_location VARCHAR(256),
                    island_uuid CHAR(36) NOT NULL,
                    PRIMARY KEY (player_uuid, island_uuid, home_name),
                    KEY idx_island_homes_island (island_uuid),
                    KEY idx_island_homes_island_player (island_uuid, player_uuid),
                    CONSTRAINT fk_island_homes_island
                        FOREIGN KEY (island_uuid) REFERENCES %sislands(island_uuid)
                        ON DELETE CASCADE,
                    CONSTRAINT fk_island_homes_player_membership
                        FOREIGN KEY (player_uuid, island_uuid)
                        REFERENCES %sisland_players(player_uuid, island_uuid)
                        ON DELETE CASCADE
                ) ENGINE=InnoDB
                """.formatted(prefix, prefix, prefix));
    }

    private void createWarpTable() {
        execute("""
                CREATE TABLE IF NOT EXISTS %sisland_warps (
                    player_uuid CHAR(36) NOT NULL,
                    warp_name VARCHAR(32) NOT NULL,
                    warp_location VARCHAR(256),
                    island_uuid CHAR(36) NOT NULL,
                    PRIMARY KEY (player_uuid, island_uuid, warp_name),
                    KEY idx_island_warps_island (island_uuid),
                    KEY idx_island_warps_island_player (island_uuid, player_uuid),
                    CONSTRAINT fk_island_warps_island
                        FOREIGN KEY (island_uuid) REFERENCES %sislands(island_uuid)
                        ON DELETE CASCADE,
                    CONSTRAINT fk_island_warps_player_membership
                        FOREIGN KEY (player_uuid, island_uuid)
                        REFERENCES %sisland_players(player_uuid, island_uuid)
                        ON DELETE CASCADE
                ) ENGINE=InnoDB
                """.formatted(prefix, prefix, prefix));
    }

    private void createBanTable() {
        execute("""
                CREATE TABLE IF NOT EXISTS %sisland_bans (
                    island_uuid CHAR(36) NOT NULL,
                    banned_player CHAR(36) NOT NULL,
                    PRIMARY KEY (island_uuid, banned_player),
                    CONSTRAINT fk_island_bans_island
                        FOREIGN KEY (island_uuid) REFERENCES %sislands(island_uuid)
                        ON DELETE CASCADE
                ) ENGINE=InnoDB
                """.formatted(prefix, prefix));
    }

    private void createCoopTable() {
        execute("""
                CREATE TABLE IF NOT EXISTS %sisland_coops (
                    island_uuid CHAR(36) NOT NULL,
                    cooped_player CHAR(36) NOT NULL,
                    PRIMARY KEY (island_uuid, cooped_player),
                    KEY idx_island_coops_cooped_player (cooped_player, island_uuid),
                    CONSTRAINT fk_island_coops_island
                        FOREIGN KEY (island_uuid) REFERENCES %sislands(island_uuid)
                        ON DELETE CASCADE
                ) ENGINE=InnoDB
                """.formatted(prefix, prefix));
    }

    private void createLevelTable() {
        execute("""
                CREATE TABLE IF NOT EXISTS %sisland_levels (
                    island_uuid CHAR(36) NOT NULL,
                    level INT NOT NULL,
                    PRIMARY KEY (island_uuid),
                    CONSTRAINT fk_island_levels_island
                        FOREIGN KEY (island_uuid) REFERENCES %sislands(island_uuid)
                        ON DELETE CASCADE
                ) ENGINE=InnoDB
                """.formatted(prefix, prefix));
    }

    private void createUuidTable() {
        execute("""
                CREATE TABLE IF NOT EXISTS %splayer_uuid (
                    uuid CHAR(36) NOT NULL,
                    name VARCHAR(16) NOT NULL,
                    name_lower VARCHAR(16) NOT NULL,
                    PRIMARY KEY (uuid),
                    KEY idx_player_uuid_name (name),
                    KEY idx_player_uuid_name_lower (name_lower)
                ) ENGINE=InnoDB
                """.formatted(prefix));
    }

    private void deleteExpiredOperations() {
        execute("DELETE FROM " + prefix
                + "island_operations WHERE created_at < (CURRENT_TIMESTAMP - INTERVAL 7 DAY)");
    }

    private void ensureIslandColumn(String columnName, String definition) {
        String tableName = prefix + "islands";

        try (Connection connection = dataSource.getConnection()) {
            if (hasColumn(connection, tableName, columnName)) {
                return;
            }

            try {
                execute(connection, "ALTER TABLE " + tableName
                        + " ADD COLUMN " + columnName + " " + definition);
            } catch (SQLException concurrentMigration) {
                // Several servers may start at once. Another server completing the same ALTER is success.
                if (!hasColumn(connection, tableName, columnName)) {
                    throw concurrentMigration;
                }
            }
        } catch (SQLException error) {
            errorSink.error("Failed to migrate islands." + columnName + ".", error);
            throw new RuntimeException(error);
        }
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName)
            throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(
                connection.getCatalog(), null, tableName, columnName)) {
            return columns.next();
        }
    }

    private void execute(String sql) {
        try (Connection connection = dataSource.getConnection()) {
            execute(connection, sql);
        } catch (SQLException error) {
            errorSink.error("Database schema update failed: " + sql, error);
            throw new RuntimeException(error);
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}
