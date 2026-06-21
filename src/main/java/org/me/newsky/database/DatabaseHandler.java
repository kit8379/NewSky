package org.me.newsky.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.model.Island;
import org.me.newsky.model.IslandTop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

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

        createTables();
    }

    public void close() {
        dataSource.close();
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public <T> T withConnection(ConnectionFunction<T> work) {
        try (Connection connection = getConnection()) {
            return work.apply(connection);
        } catch (SQLException e) {
            plugin.severe("Database connection operation failed.", e);
            throw new RuntimeException(e);
        }
    }

    private void inTransaction(ConnectionConsumer work) {
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
                    plugin.severe("Database rollback failed for tx.", rollbackEx);
                }
                plugin.severe("Database transaction failed.", e);
                throw new RuntimeException(e);
            } finally {
                try {
                    connection.setAutoCommit(oldAutoCommit);
                } catch (SQLException ignored) {
                    // ignore
                }
            }
        } catch (SQLException e) {
            plugin.severe("Database transaction connection failed.", e);
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

    public <T> T executeQuery(String query, PreparedStatementConsumer consumer, ResultFunction<T> function) {
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            consumer.use(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                return function.apply(resultSet);
            }
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
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_players (" + "player_uuid CHAR(36) NOT NULL," + "island_uuid CHAR(36) NOT NULL," + "role VARCHAR(56) NOT NULL," + "PRIMARY KEY (player_uuid, island_uuid)," + "UNIQUE KEY uq_island_players_player_uuid (player_uuid)," + "KEY idx_island_players_island_uuid (island_uuid)," + "KEY idx_island_players_island_role (island_uuid, role)," + "CONSTRAINT fk_island_players_island " + "FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid) " + "ON DELETE CASCADE" + ") ENGINE=InnoDB;", stmt -> {
        });
    }

    private void createIslandHomesTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_homes (" + "player_uuid CHAR(36) NOT NULL," + "home_name VARCHAR(32) NOT NULL," + "home_location VARCHAR(256)," + "island_uuid CHAR(36) NOT NULL," + "PRIMARY KEY (player_uuid, home_name)," + "KEY idx_island_homes_island (island_uuid)," + "KEY idx_island_homes_island_player (island_uuid, player_uuid)," + "CONSTRAINT fk_island_homes_island " + "FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid) " + "ON DELETE CASCADE," + "CONSTRAINT fk_island_homes_player_membership " + "FOREIGN KEY (player_uuid, island_uuid) REFERENCES " + prefix + "island_players(player_uuid, island_uuid) " + "ON DELETE CASCADE" + ") ENGINE=InnoDB;", stmt -> {
        });
    }

    private void createIslandWarpsTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_warps (" + "player_uuid CHAR(36) NOT NULL," + "warp_name VARCHAR(32) NOT NULL," + "warp_location VARCHAR(256)," + "island_uuid CHAR(36) NOT NULL," + "PRIMARY KEY (player_uuid, warp_name)," + "KEY idx_island_warps_island (island_uuid)," + "KEY idx_island_warps_island_player (island_uuid, player_uuid)," + "CONSTRAINT fk_island_warps_island " + "FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid) " + "ON DELETE CASCADE," + "CONSTRAINT fk_island_warps_player_membership " + "FOREIGN KEY (player_uuid, island_uuid) REFERENCES " + prefix + "island_players(player_uuid, island_uuid) " + "ON DELETE CASCADE" + ") ENGINE=InnoDB;", stmt -> {
        });
    }

    private void createIslandBanTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_bans (" + "island_uuid CHAR(36) NOT NULL," + "banned_player CHAR(36) NOT NULL," + "PRIMARY KEY (island_uuid, banned_player)," + "CONSTRAINT fk_island_bans_island " + "FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid) " + "ON DELETE CASCADE" + ") ENGINE=InnoDB;", stmt -> {
        });
    }

    private void createIslandCoopTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_coops (" + "island_uuid CHAR(36) NOT NULL," + "cooped_player CHAR(36) NOT NULL," + "PRIMARY KEY (island_uuid, cooped_player)," + "KEY idx_island_coops_cooped_player (cooped_player, island_uuid)," + "CONSTRAINT fk_island_coops_island " + "FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid) " + "ON DELETE CASCADE" + ") ENGINE=InnoDB;", stmt -> {
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
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "player_uuid (" + "uuid CHAR(36) NOT NULL," + "name VARCHAR(16) NOT NULL," + "name_lower VARCHAR(16) NOT NULL," + "PRIMARY KEY (uuid)," + "KEY idx_player_uuid_name (name)," + "KEY idx_player_uuid_name_lower (name_lower)" + ") ENGINE=InnoDB;", stmt -> {
        });
    }

    // ================================================================================================================
    // Targeted reads
    // ================================================================================================================

    public boolean getIslandLock(UUID islandUuid) {
        String sql = "SELECT `lock` FROM " + prefix + "islands WHERE island_uuid = ? LIMIT 1";

        return executeQuery(sql, stmt -> stmt.setString(1, islandUuid.toString()), rs -> {
            if (!rs.next()) {
                return false;
            }
            return rs.getBoolean("lock");
        });
    }

    public boolean getIslandPvp(UUID islandUuid) {
        String sql = "SELECT pvp FROM " + prefix + "islands WHERE island_uuid = ? LIMIT 1";

        return executeQuery(sql, stmt -> stmt.setString(1, islandUuid.toString()), rs -> {
            if (!rs.next()) {
                return false;
            }
            return rs.getBoolean("pvp");
        });
    }

    public int getIslandLevel(UUID islandUuid) {
        String sql = "SELECT level FROM " + prefix + "island_levels WHERE island_uuid = ? LIMIT 1";

        return executeQuery(sql, stmt -> stmt.setString(1, islandUuid.toString()), rs -> {
            if (!rs.next()) {
                return 0;
            }
            return rs.getInt("level");
        });
    }

    public Optional<UUID> getIslandOwner(UUID islandUuid) {
        String sql = "SELECT player_uuid FROM " + prefix + "island_players WHERE island_uuid = ? AND role = ? LIMIT 1";

        return executeQuery(sql, stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, "owner");
        }, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(parseRequiredUuid(rs.getString("player_uuid"), "island_players.player_uuid"));
        });
    }

    public Map<UUID, String> getIslandPlayers(UUID islandUuid) {
        String sql = "SELECT player_uuid, role FROM " + prefix + "island_players WHERE island_uuid = ?";

        return executeQuery(sql, stmt -> stmt.setString(1, islandUuid.toString()), rs -> {
            Map<UUID, String> result = new LinkedHashMap<>();

            while (rs.next()) {
                UUID playerUuid = parseRequiredUuid(rs.getString("player_uuid"), "island_players.player_uuid");
                String role = rs.getString("role");

                if (role == null || role.isEmpty()) {
                    continue;
                }

                result.put(playerUuid, role);
            }

            return result.isEmpty() ? Collections.emptyMap() : Map.copyOf(result);
        });
    }

    public Map<String, String> getIslandHomes(UUID islandUuid, UUID playerUuid) {
        String sql = "SELECT home_name, home_location FROM " + prefix + "island_homes WHERE island_uuid = ? AND player_uuid = ?";

        return executeQuery(sql, stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, playerUuid.toString());
        }, rs -> {
            Map<String, String> result = new LinkedHashMap<>();

            while (rs.next()) {
                String homeName = rs.getString("home_name");
                String homeLocation = rs.getString("home_location");

                if (homeName == null || homeName.isEmpty()) {
                    continue;
                }

                result.put(homeName, homeLocation == null ? "" : homeLocation);
            }

            return result.isEmpty() ? Collections.emptyMap() : Map.copyOf(result);
        });
    }

    public Map<String, String> getIslandWarps(UUID islandUuid, UUID playerUuid) {
        String sql = "SELECT warp_name, warp_location FROM " + prefix + "island_warps WHERE island_uuid = ? AND player_uuid = ?";

        return executeQuery(sql, stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, playerUuid.toString());
        }, rs -> {
            Map<String, String> result = new LinkedHashMap<>();

            while (rs.next()) {
                String warpName = rs.getString("warp_name");
                String warpLocation = rs.getString("warp_location");

                if (warpName == null || warpName.isEmpty()) {
                    continue;
                }

                result.put(warpName, warpLocation == null ? "" : warpLocation);
            }

            return result.isEmpty() ? Collections.emptyMap() : Map.copyOf(result);
        });
    }

    public Set<UUID> getIslandBans(UUID islandUuid) {
        String sql = "SELECT banned_player FROM " + prefix + "island_bans WHERE island_uuid = ?";

        return executeQuery(sql, stmt -> stmt.setString(1, islandUuid.toString()), rs -> {
            Set<UUID> result = new LinkedHashSet<>();

            while (rs.next()) {
                result.add(parseRequiredUuid(rs.getString("banned_player"), "island_bans.banned_player"));
            }

            return result.isEmpty() ? Set.of() : Set.copyOf(result);
        });
    }

    public Set<UUID> getIslandCoops(UUID islandUuid) {
        String sql = "SELECT cooped_player FROM " + prefix + "island_coops WHERE island_uuid = ?";

        return executeQuery(sql, stmt -> stmt.setString(1, islandUuid.toString()), rs -> {
            Set<UUID> result = new LinkedHashSet<>();

            while (rs.next()) {
                result.add(parseRequiredUuid(rs.getString("cooped_player"), "island_coops.cooped_player"));
            }

            return result.isEmpty() ? Set.of() : Set.copyOf(result);
        });
    }

    public Set<UUID> getPlayerCoopedIslands(UUID playerUuid) {
        String sql = "SELECT island_uuid FROM " + prefix + "island_coops WHERE cooped_player = ?";

        return executeQuery(sql, stmt -> stmt.setString(1, playerUuid.toString()), rs -> {
            Set<UUID> result = new LinkedHashSet<>();

            while (rs.next()) {
                result.add(parseRequiredUuid(rs.getString("island_uuid"), "island_coops.island_uuid"));
            }

            return result.isEmpty() ? Set.of() : Set.copyOf(result);
        });
    }

    public Map<String, Integer> getIslandUpgrades(UUID islandUuid) {
        String sql = "SELECT upgrade_id, level FROM " + prefix + "island_upgrades WHERE island_uuid = ?";

        return executeQuery(sql, stmt -> stmt.setString(1, islandUuid.toString()), rs -> {
            Map<String, Integer> result = new LinkedHashMap<>();

            while (rs.next()) {
                String upgradeId = rs.getString("upgrade_id");
                int level = rs.getInt("level");

                if (upgradeId == null || upgradeId.isEmpty() || level <= 1) {
                    continue;
                }

                result.put(upgradeId, level);
            }

            return result.isEmpty() ? Collections.emptyMap() : Map.copyOf(result);
        });
    }

    public Optional<UUID> getIslandUuid(UUID playerUuid) {
        String sql = "SELECT island_uuid FROM " + prefix + "island_players WHERE player_uuid = ? LIMIT 1";

        return executeQuery(sql, stmt -> stmt.setString(1, playerUuid.toString()), rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }

            return Optional.of(parseRequiredUuid(rs.getString("island_uuid"), "island_players.island_uuid"));
        });
    }

    public Optional<UUID> getPlayerUuid(String name) {
        if (name == null || name.isEmpty()) {
            return Optional.empty();
        }

        String sql = "SELECT uuid FROM " + prefix + "player_uuid WHERE name_lower = ? LIMIT 1";

        return executeQuery(sql, stmt -> stmt.setString(1, name.toLowerCase(Locale.ROOT)), rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }

            return Optional.of(parseRequiredUuid(rs.getString("uuid"), "player_uuid.uuid"));
        });
    }

    public Optional<String> getPlayerName(UUID uuid) {
        String sql = "SELECT name FROM " + prefix + "player_uuid WHERE uuid = ? LIMIT 1";

        return executeQuery(sql, stmt -> stmt.setString(1, uuid.toString()), rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }

            return Optional.ofNullable(rs.getString("name"));
        });
    }

    public Map<UUID, String> getPlayerNames(Collection<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return Collections.emptyMap();
        }

        List<UUID> ordered = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();

        for (UUID uuid : uuids) {
            if (uuid != null && seen.add(uuid)) {
                ordered.add(uuid);
            }
        }

        if (ordered.isEmpty()) {
            return Collections.emptyMap();
        }

        StringBuilder sql = new StringBuilder("SELECT uuid, name FROM ").append(prefix).append("player_uuid WHERE uuid IN (");

        for (int i = 0; i < ordered.size(); i++) {
            if (i > 0) {
                sql.append(",");
            }
            sql.append("?");
        }

        sql.append(")");

        return executeQuery(sql.toString(), stmt -> {
            for (int i = 0; i < ordered.size(); i++) {
                stmt.setString(i + 1, ordered.get(i).toString());
            }
        }, rs -> {
            Map<UUID, String> result = new HashMap<>();

            while (rs.next()) {
                UUID uuid = parseRequiredUuid(rs.getString("uuid"), "player_uuid.uuid");
                String name = rs.getString("name");

                if (name != null) {
                    result.put(uuid, name);
                }
            }

            return result.isEmpty() ? Collections.emptyMap() : Map.copyOf(result);
        });
    }

    public List<IslandTop> getTopIslandLevels(int limit) {
        int safeLimit = Math.max(1, limit);

        return withConnection(connection -> {
            String topSql = "SELECT p.island_uuid, p.player_uuid AS owner_uuid, COALESCE(l.level, 0) AS level " + "FROM " + prefix + "island_players p " + "LEFT JOIN " + prefix + "island_levels l ON l.island_uuid = p.island_uuid " + "WHERE p.role = ? " + "ORDER BY level DESC, p.island_uuid ASC " + "LIMIT ?";

            List<UUID> islandOrder = new ArrayList<>();
            Map<UUID, UUID> ownerByIsland = new LinkedHashMap<>();
            Map<UUID, Integer> levelByIsland = new HashMap<>();

            try (PreparedStatement stmt = connection.prepareStatement(topSql)) {
                stmt.setString(1, "owner");
                stmt.setInt(2, safeLimit);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID islandUuid = parseRequiredUuid(rs.getString("island_uuid"), "top.island_uuid");
                        UUID ownerUuid = parseRequiredUuid(rs.getString("owner_uuid"), "top.owner_uuid");
                        int level = rs.getInt("level");

                        islandOrder.add(islandUuid);
                        ownerByIsland.put(islandUuid, ownerUuid);
                        levelByIsland.put(islandUuid, level);
                    }
                }
            }

            if (islandOrder.isEmpty()) {
                return List.of();
            }

            Map<UUID, Set<UUID>> membersByIsland = new HashMap<>();

            StringBuilder memberSql = new StringBuilder("SELECT island_uuid, player_uuid FROM " + prefix + "island_players WHERE role = ? AND island_uuid IN (");

            for (int i = 0; i < islandOrder.size(); i++) {
                if (i > 0) {
                    memberSql.append(",");
                }
                memberSql.append("?");
            }

            memberSql.append(")");

            try (PreparedStatement stmt = connection.prepareStatement(memberSql.toString())) {
                stmt.setString(1, "member");

                for (int i = 0; i < islandOrder.size(); i++) {
                    stmt.setString(i + 2, islandOrder.get(i).toString());
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID islandUuid = parseRequiredUuid(rs.getString("island_uuid"), "topMembers.island_uuid");
                        UUID memberUuid = parseRequiredUuid(rs.getString("player_uuid"), "topMembers.player_uuid");

                        membersByIsland.computeIfAbsent(islandUuid, ignored -> new LinkedHashSet<>()).add(memberUuid);
                    }
                }
            }

            List<IslandTop> result = new ArrayList<>(islandOrder.size());

            for (UUID islandUuid : islandOrder) {
                UUID ownerUuid = ownerByIsland.get(islandUuid);
                int level = levelByIsland.getOrDefault(islandUuid, 0);
                Set<UUID> members = membersByIsland.getOrDefault(islandUuid, Set.of());

                result.add(new IslandTop(islandUuid, ownerUuid, level, members.isEmpty() ? Set.of() : Set.copyOf(members)));
            }

            return result.isEmpty() ? List.of() : List.copyOf(result);
        });
    }

    // ================================================================================================================
    // Writes (transaction-protected where multi-statement)
    // ================================================================================================================

    public void addIslandData(UUID islandUuid, UUID ownerUuid, String homePoint) {
        inTransaction(connection -> {
            executeUpdate(connection, "INSERT INTO " + prefix + "islands (island_uuid) VALUES (?);", stmt -> {
                stmt.setString(1, islandUuid.toString());
            });

            executeUpdate(connection, "INSERT INTO " + prefix + "island_players (player_uuid, island_uuid, role) VALUES (?, ?, ?);", stmt -> {
                stmt.setString(1, ownerUuid.toString());
                stmt.setString(2, islandUuid.toString());
                stmt.setString(3, "owner");
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
        inTransaction(connection -> {
            executeUpdate(connection, "INSERT INTO " + prefix + "island_players (player_uuid, island_uuid, role) VALUES (?, ?, ?);", stmt -> {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, islandUuid.toString());
                stmt.setString(3, role);
            });

            executeUpdate(connection, "INSERT INTO " + prefix + "island_homes (player_uuid, island_uuid, home_name, home_location) VALUES (?, ?, ?, ?) " + "ON DUPLICATE KEY UPDATE home_location = ?;", stmt -> {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, islandUuid.toString());
                stmt.setString(3, "default");
                stmt.setString(4, homePoint);
                stmt.setString(5, homePoint);
            });

            executeUpdate(connection, "DELETE FROM " + prefix + "island_bans WHERE island_uuid = ? AND banned_player = ?;", stmt -> {
                stmt.setString(1, islandUuid.toString());
                stmt.setString(2, playerUuid.toString());
            });

            executeUpdate(connection, "DELETE FROM " + prefix + "island_coops WHERE island_uuid = ? AND cooped_player = ?;", stmt -> {
                stmt.setString(1, islandUuid.toString());
                stmt.setString(2, playerUuid.toString());
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
        inTransaction(connection -> {
            int demoted;

            try (PreparedStatement stmt = connection.prepareStatement("UPDATE " + prefix + "island_players SET role = ? WHERE player_uuid = ? AND island_uuid = ? AND role = ?")) {
                stmt.setString(1, "member");
                stmt.setString(2, oldOwnerUuid.toString());
                stmt.setString(3, islandUuid.toString());
                stmt.setString(4, "owner");
                demoted = stmt.executeUpdate();
            }

            if (demoted != 1) {
                throw new IllegalStateException("Failed to demote old owner exactly once: island=" + islandUuid + ", oldOwner=" + oldOwnerUuid + ", updated=" + demoted);
            }

            int promoted;

            try (PreparedStatement stmt = connection.prepareStatement("UPDATE " + prefix + "island_players SET role = ? WHERE player_uuid = ? AND island_uuid = ? AND role = ?")) {
                stmt.setString(1, "owner");
                stmt.setString(2, newOwnerUuid.toString());
                stmt.setString(3, islandUuid.toString());
                stmt.setString(4, "member");
                promoted = stmt.executeUpdate();
            }

            if (promoted != 1) {
                throw new IllegalStateException("Failed to promote new owner exactly once: island=" + islandUuid + ", newOwner=" + newOwnerUuid + ", updated=" + promoted);
            }
        });
    }

    public void updateBanPlayer(UUID islandUuid, UUID playerUuid) {
        executeUpdate("INSERT INTO " + prefix + "island_bans (island_uuid, banned_player) " + "SELECT ?, ? FROM DUAL " + "WHERE NOT EXISTS (" + "SELECT 1 FROM " + prefix + "island_players " + "WHERE island_uuid = ? AND player_uuid = ?" + ") " + "AND NOT EXISTS (" + "SELECT 1 FROM " + prefix + "island_bans " + "WHERE island_uuid = ? AND banned_player = ?" + ");", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, playerUuid.toString());
            stmt.setString(3, islandUuid.toString());
            stmt.setString(4, playerUuid.toString());
            stmt.setString(5, islandUuid.toString());
            stmt.setString(6, playerUuid.toString());
        });
    }

    public void updateCoopPlayer(UUID islandUuid, UUID playerUuid) {
        executeUpdate("INSERT INTO " + prefix + "island_coops (island_uuid, cooped_player) " + "SELECT ?, ? FROM DUAL " + "WHERE NOT EXISTS (" + "SELECT 1 FROM " + prefix + "island_players " + "WHERE island_uuid = ? AND player_uuid = ?" + ") " + "AND NOT EXISTS (" + "SELECT 1 FROM " + prefix + "island_coops " + "WHERE island_uuid = ? AND cooped_player = ?" + ");", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, playerUuid.toString());
            stmt.setString(3, islandUuid.toString());
            stmt.setString(4, playerUuid.toString());
            stmt.setString(5, islandUuid.toString());
            stmt.setString(6, playerUuid.toString());
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
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Player name cannot be null or empty");
        }

        executeUpdate("INSERT INTO " + prefix + "player_uuid (uuid, name, name_lower) VALUES (?, ?, ?) " + "ON DUPLICATE KEY UPDATE name = ?, name_lower = ?;", stmt -> {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
            stmt.setString(3, name.toLowerCase(Locale.ROOT));
            stmt.setString(4, name);
            stmt.setString(5, name.toLowerCase(Locale.ROOT));
        });
    }

    public void deleteIsland(UUID islandUuid) {
        executeUpdate("DELETE FROM " + prefix + "islands WHERE island_uuid = ?;", stmt -> {
            stmt.setString(1, islandUuid.toString());
        });
    }

    public void deleteIslandPlayer(UUID islandUuid, UUID playerUuid) {
        executeUpdate("DELETE FROM " + prefix + "island_players WHERE player_uuid = ? AND island_uuid = ? AND role <> ?;", stmt -> {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, islandUuid.toString());
            stmt.setString(3, "owner");
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
        executeUpdate("DELETE FROM " + prefix + "island_coops WHERE cooped_player = ?;", stmt -> {
            stmt.setString(1, playerUuid.toString());
        });
    }

    public void deleteIslandUpgrade(UUID islandUuid, String upgradeId) {
        executeUpdate("DELETE FROM " + prefix + "island_upgrades WHERE island_uuid = ? AND upgrade_id = ?;", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, upgradeId);
        });
    }

    public Island getIslandSnapshot(UUID islandUuid) {
        return withConnection(connection -> {
            boolean lock;
            boolean pvp;

            String islandSql = "SELECT `lock`, pvp FROM " + prefix + "islands WHERE island_uuid = ? LIMIT 1";
            try (PreparedStatement stmt = connection.prepareStatement(islandSql)) {
                stmt.setString(1, islandUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }

                    lock = rs.getBoolean("lock");
                    pvp = rs.getBoolean("pvp");
                }
            }

            UUID ownerUuid = null;
            Set<UUID> members = new LinkedHashSet<>();

            String playersSql = "SELECT player_uuid, role FROM " + prefix + "island_players WHERE island_uuid = ?";
            try (PreparedStatement stmt = connection.prepareStatement(playersSql)) {
                stmt.setString(1, islandUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID playerUuid = parseRequiredUuid(rs.getString("player_uuid"), "island_players.player_uuid");
                        String role = rs.getString("role");

                        if ("owner".equalsIgnoreCase(role)) {
                            ownerUuid = playerUuid;
                        } else if ("member".equalsIgnoreCase(role)) {
                            members.add(playerUuid);
                        }
                    }
                }
            }

            if (ownerUuid == null) {
                throw new IllegalStateException("Island owner does not exist in database for island: " + islandUuid);
            }

            Set<UUID> coops = new LinkedHashSet<>();
            String coopsSql = "SELECT cooped_player FROM " + prefix + "island_coops WHERE island_uuid = ?";
            try (PreparedStatement stmt = connection.prepareStatement(coopsSql)) {
                stmt.setString(1, islandUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        coops.add(parseRequiredUuid(rs.getString("cooped_player"), "island_coops.cooped_player"));
                    }
                }
            }

            Set<UUID> bans = new LinkedHashSet<>();
            String bansSql = "SELECT banned_player FROM " + prefix + "island_bans WHERE island_uuid = ?";
            try (PreparedStatement stmt = connection.prepareStatement(bansSql)) {
                stmt.setString(1, islandUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        bans.add(parseRequiredUuid(rs.getString("banned_player"), "island_bans.banned_player"));
                    }
                }
            }

            Map<String, Integer> upgrades = new LinkedHashMap<>();
            String upgradesSql = "SELECT upgrade_id, level FROM " + prefix + "island_upgrades WHERE island_uuid = ?";
            try (PreparedStatement stmt = connection.prepareStatement(upgradesSql)) {
                stmt.setString(1, islandUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String upgradeId = rs.getString("upgrade_id");
                        int level = rs.getInt("level");

                        if (upgradeId == null || upgradeId.isEmpty() || level <= 1) {
                            continue;
                        }

                        upgrades.put(upgradeId, level);
                    }
                }
            }

            return new Island(islandUuid, lock, pvp, ownerUuid, members.isEmpty() ? Set.of() : Set.copyOf(members), coops.isEmpty() ? Set.of() : Set.copyOf(coops), bans.isEmpty() ? Set.of() : Set.copyOf(bans), upgrades.isEmpty() ? Map.of() : Map.copyOf(upgrades));
        });
    }

    // ================================================================================================================
    // Helpers
    // ================================================================================================================

    private UUID parseRequiredUuid(String value, String fieldName) {
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing UUID value in DatabaseHandler for field: " + fieldName);
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid UUID value in DatabaseHandler for field " + fieldName + ": " + value, e);
        }
    }

    // ================================================================================================================
    // Functional interfaces
    // ================================================================================================================

    @FunctionalInterface
    public interface ResultFunction<T> {
        T apply(ResultSet resultSet) throws SQLException;
    }

    @FunctionalInterface
    public interface PreparedStatementConsumer {
        void use(PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    private interface ConnectionConsumer {
        void use(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface ConnectionFunction<T> {
        T apply(Connection connection) throws SQLException;
    }
}