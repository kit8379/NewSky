package org.me.newsky.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.*;
import org.me.newsky.model.Actor;
import org.me.newsky.model.Island;
import org.me.newsky.model.IslandTop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.*;

public class DatabaseHandler {

    /**
     * Where failures are reported. Exists so {@code DatabaseTransactionTest} can run this exact
     * class against a scratch MySQL without a Bukkit server behind it.
     */
    @FunctionalInterface
    public interface ErrorSink {
        void error(String message, Throwable error);
    }

    private final ErrorSink errorSink;
    private final HikariDataSource dataSource;
    private final String prefix;
    private final String spawnLocation;

    public record IslandCoreData(boolean lock, boolean pvp, int level, Optional<UUID> owner) {
    }

    /** Value plus the durable version committed in the same island transaction. */
    public record VersionedBoolean(boolean value, long version) {
    }

    public DatabaseHandler(NewSky plugin, ConfigHandler config) {
        this(buildDataSource(config), config.getMySQLTablePrefix(), buildSpawnLocation(config),
                plugin::severe);
    }

    public DatabaseHandler(HikariDataSource dataSource, String tablePrefix, String spawnLocation, ErrorSink errorSink) {
        this.dataSource = dataSource;
        this.prefix = tablePrefix;
        this.spawnLocation = spawnLocation;
        this.errorSink = errorSink;

        new DatabaseSchema(dataSource, prefix, errorSink).createTables();
    }

    private static HikariDataSource buildDataSource(ConfigHandler config) {
        HikariConfig hikariConfig = new HikariConfig();
        String jdbcUrl = "jdbc:mysql://" + config.getMySQLHost() + ":" + config.getMySQLPort()
                + "/" + config.getMySQLDB() + "?useSSL=" + config.getMySQLUseSSL()
                + "&" + config.getMySQLProperties();
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(config.getMySQLUsername());
        hikariConfig.setPassword(config.getMySQLPassword());

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

        return new HikariDataSource(hikariConfig);
    }

    private static String buildSpawnLocation(ConfigHandler config) {
        return config.getIslandSpawnX() + "," + config.getIslandSpawnY() + ","
                + config.getIslandSpawnZ() + "," + config.getIslandSpawnYaw() + ","
                + config.getIslandSpawnPitch();
    }

    public void close() {
        dataSource.close();
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private <T> T withConnection(ConnectionFunction<T> work) {
        try (Connection connection = getConnection()) {
            return work.apply(connection);
        } catch (SQLException e) {
            errorSink.error("Database connection operation failed.", e);
            throw new RuntimeException(e);
        }
    }

    private <T> T inTransaction(ConnectionFunction<T> work) {
        try (Connection connection = getConnection()) {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException e) {
                rollbackQuietly(connection);
                if (e instanceof SQLIntegrityConstraintViolationException) {
                    throw new ConstraintViolationException(e);
                }
                errorSink.error("Database transaction failed.", e);
                throw new RuntimeException(e);
            } catch (RuntimeException e) {
                rollbackQuietly(connection);
                throw e;
            } finally {
                try {
                    connection.setAutoCommit(oldAutoCommit);
                } catch (SQLException ignored) {
                    // ignore
                }
            }
        } catch (SQLException e) {
            errorSink.error("Database transaction connection failed.", e);
            throw new RuntimeException(e);
        }
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // ignore
        }
    }

    private int executeUpdate(Connection connection, String query,
                              PreparedStatementConsumer consumer) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            consumer.use(statement);
            return statement.executeUpdate();
        }
    }

    private int executeUpdate(String query, PreparedStatementConsumer consumer) {
        try (Connection connection = getConnection()) {
            return executeUpdate(connection, query, consumer);
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e);
        } catch (SQLException e) {
            errorSink.error("Database Update failed: " + query, e);
            throw new RuntimeException(e);
        }
    }

    private <T> T executeQuery(String query, PreparedStatementConsumer consumer,
                               ResultFunction<T> function) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            consumer.use(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                return function.apply(resultSet);
            }
        } catch (SQLException e) {
            errorSink.error("Database Query failed: " + query, e);
            throw new RuntimeException(e);
        }
    }

    // ================================================================================================================
    // Targeted reads
    // ================================================================================================================

    public Optional<IslandCoreData> getIslandCore(UUID islandUuid) {
        String sql = """
                SELECT i.`lock`, i.pvp, COALESCE(l.level, 0) AS level, p.player_uuid AS owner_uuid
                FROM %sislands i
                LEFT JOIN %sisland_levels l ON l.island_uuid = i.island_uuid
                LEFT JOIN %sisland_players p ON p.island_uuid = i.island_uuid AND p.role = ?
                WHERE i.island_uuid = ?
                LIMIT 1
                """.formatted(prefix, prefix, prefix);

        return executeQuery(sql, stmt -> {
            stmt.setString(1, "owner");
            stmt.setString(2, islandUuid.toString());
        }, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }

            String ownerUuid = rs.getString("owner_uuid");
            Optional<UUID> owner = Optional.empty();
            if (ownerUuid != null && !ownerUuid.isEmpty()) {
                owner = Optional.of(parseRequiredUuid(ownerUuid, "island_players.player_uuid"));
            }

            return Optional.of(new IslandCoreData(
                    rs.getBoolean("lock"), rs.getBoolean("pvp"), rs.getInt("level"), owner));
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
        String sql = "SELECT home_name, home_location FROM " + prefix
                + "island_homes WHERE island_uuid = ? AND player_uuid = ?";

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
        String sql = "SELECT warp_name, warp_location FROM " + prefix
                + "island_warps WHERE island_uuid = ? AND player_uuid = ?";

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

    /** The islands where this player currently holds a coop, for the quit cleanup's work list. */
    public Set<UUID> getCoopIslands(UUID playerUuid) {
        String sql = "SELECT island_uuid FROM " + prefix + "island_coops WHERE cooped_player = ?";

        return executeQuery(sql, stmt -> stmt.setString(1, playerUuid.toString()), rs -> {
            Set<UUID> result = new LinkedHashSet<>();

            while (rs.next()) {
                result.add(parseRequiredUuid(rs.getString("island_uuid"), "island_coops.island_uuid"));
            }

            return result.isEmpty() ? Set.of() : Set.copyOf(result);
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

        StringBuilder sql = new StringBuilder("SELECT uuid, name FROM ")
                .append(prefix)
                .append("player_uuid WHERE uuid IN (");

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
            String topSql = """
                    SELECT p.island_uuid, p.player_uuid AS owner_uuid,
                           COALESCE(l.level, 0) AS level
                    FROM %sisland_players p
                    LEFT JOIN %sisland_levels l ON l.island_uuid = p.island_uuid
                    WHERE p.role = ?
                    ORDER BY level DESC, p.island_uuid ASC
                    LIMIT ?
                    """.formatted(prefix, prefix);

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

            StringBuilder memberSql = new StringBuilder("SELECT island_uuid, player_uuid FROM ")
                    .append(prefix)
                    .append("island_players WHERE role = ? AND island_uuid IN (");

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

                        membersByIsland.computeIfAbsent(islandUuid, ignored -> new LinkedHashSet<>())
                                .add(memberUuid);
                    }
                }
            }

            List<IslandTop> result = new ArrayList<>(islandOrder.size());

            for (UUID islandUuid : islandOrder) {
                UUID ownerUuid = ownerByIsland.get(islandUuid);
                int level = levelByIsland.getOrDefault(islandUuid, 0);
                Set<UUID> members = membersByIsland.getOrDefault(islandUuid, Set.of());

                Set<UUID> memberCopy = members.isEmpty() ? Set.of() : Set.copyOf(members);
                result.add(new IslandTop(islandUuid, ownerUuid, level, memberCopy));
            }

            return result.isEmpty() ? List.of() : List.copyOf(result);
        });
    }

    // ================================================================================================================
    // Writes (transaction-protected where multi-statement)
    // ================================================================================================================

    public void createIsland(UUID islandUuid, UUID ownerUuid) {
        createIsland(islandUuid, ownerUuid, null);
    }

    public void createIsland(UUID islandUuid, UUID ownerUuid, UUID writeEpoch) {
        try {
            inTransaction(connection -> {
                if (playerHasIsland(connection, ownerUuid)) {
                    throw new IslandAlreadyExistException();
                }

                String homePoint = islandSpawnLocation();

                String insertIsland = "INSERT INTO " + prefix
                        + "islands (island_uuid, write_epoch, provision_state) "
                        + "VALUES (?, ?, 'provisioning')";
                executeUpdate(connection, insertIsland, stmt -> {
                    stmt.setString(1, islandUuid.toString());
                    stmt.setString(2, writeEpoch == null ? null : writeEpoch.toString());
                });

                String insertOwner = "INSERT INTO " + prefix
                        + "island_players (player_uuid, island_uuid, role) VALUES (?, ?, ?)";
                executeUpdate(connection, insertOwner, stmt -> {
                    stmt.setString(1, ownerUuid.toString());
                    stmt.setString(2, islandUuid.toString());
                    stmt.setString(3, "owner");
                });

                upsertHome(connection, islandUuid, ownerUuid, "default", homePoint);

                return null;
            });
        } catch (ConstraintViolationException e) {
            throw new IslandAlreadyExistException();
        }
    }

    public long addMember(UUID islandUuid, UUID playerUuid, String role, UUID vouchedBy) {
        return addMember(islandUuid, playerUuid, role, vouchedBy, null);
    }

    public long addMember(UUID islandUuid, UUID playerUuid, String role, UUID vouchedBy,
                          UUID expectedWriteEpoch) {
        if (!"member".equals(role)) {
            throw new IllegalArgumentException("Only the member role can be granted; ownership moves via setOwner");
        }

        try {
            return inTransaction(connection -> {
                lockIsland(connection, islandUuid, expectedWriteEpoch);

                if (vouchedBy != null && getIslandRole(connection, islandUuid, vouchedBy).isEmpty()) {
                    throw new InviterNotMemberException();
                }

                Optional<UUID> playerIsland = getPlayerIsland(connection, playerUuid);
                if (playerIsland.isPresent()) {
                    if (playerIsland.get().equals(islandUuid)) {
                        throw new IslandPlayerAlreadyExistsException();
                    }
                    throw new IslandAlreadyExistException();
                }

                String homePoint = getIslandDefaultHome(connection, islandUuid)
                        .orElseGet(this::islandSpawnLocation);

                String insertMember = "INSERT INTO " + prefix
                        + "island_players (player_uuid, island_uuid, role) VALUES (?, ?, ?)";
                executeUpdate(connection, insertMember, stmt -> {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, islandUuid.toString());
                    stmt.setString(3, role);
                });

                upsertHome(connection, islandUuid, playerUuid, "default", homePoint);

                String deleteBan = "DELETE FROM " + prefix
                        + "island_bans WHERE island_uuid = ? AND banned_player = ?";
                executeUpdate(connection, deleteBan, stmt -> {
                    stmt.setString(1, islandUuid.toString());
                    stmt.setString(2, playerUuid.toString());
                });

                String deleteCoop = "DELETE FROM " + prefix
                        + "island_coops WHERE island_uuid = ? AND cooped_player = ?";
                executeUpdate(connection, deleteCoop, stmt -> {
                    stmt.setString(1, islandUuid.toString());
                    stmt.setString(2, playerUuid.toString());
                });

                return bumpStateVersion(connection, islandUuid);
            });
        } catch (ConstraintViolationException e) {
            throw new IslandAlreadyExistException();
        }
    }

    public void setHome(UUID islandUuid, UUID playerUuid, String homeName, String homeLocation) {
        try {
            upsertHome(islandUuid, playerUuid, homeName, homeLocation);
        } catch (ConstraintViolationException e) {
            throw new LocationNotInIslandException();
        }
    }

    public void setWarp(UUID islandUuid, UUID playerUuid, String warpName, String warpLocation) {
        try {
            String sql = "INSERT INTO " + prefix
                    + "island_warps (player_uuid, island_uuid, warp_name, warp_location) "
                    + "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                    + "island_uuid = VALUES(island_uuid), warp_location = VALUES(warp_location)";
            executeUpdate(sql, stmt -> {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, islandUuid.toString());
                stmt.setString(3, warpName);
                stmt.setString(4, warpLocation);
            });
        } catch (ConstraintViolationException e) {
            throw new LocationNotInIslandException();
        }
    }

    public boolean toggleLock(UUID islandUuid, Actor actor) {
        return toggleLockVersioned(islandUuid, actor).value();
    }

    public boolean togglePvp(UUID islandUuid, Actor actor) {
        return togglePvpVersioned(islandUuid, actor).value();
    }

    public VersionedBoolean toggleLockVersioned(UUID islandUuid, Actor actor) {
        return toggleLockVersioned(islandUuid, actor, UUID.randomUUID());
    }

    public VersionedBoolean togglePvpVersioned(UUID islandUuid, Actor actor) {
        return togglePvpVersioned(islandUuid, actor, UUID.randomUUID());
    }

    public VersionedBoolean toggleLockVersioned(UUID islandUuid, Actor actor, UUID operationId) {
        return toggleLockVersioned(islandUuid, actor, operationId, null);
    }

    public VersionedBoolean togglePvpVersioned(UUID islandUuid, Actor actor, UUID operationId) {
        return togglePvpVersioned(islandUuid, actor, operationId, null);
    }

    public VersionedBoolean toggleLockVersioned(UUID islandUuid, Actor actor, UUID operationId,
                                                UUID expectedWriteEpoch) {
        return toggleBooleanColumnVersioned(islandUuid, actor, "`lock`", "island.lock.toggle",
                operationId, expectedWriteEpoch);
    }

    public VersionedBoolean togglePvpVersioned(UUID islandUuid, Actor actor, UUID operationId,
                                               UUID expectedWriteEpoch) {
        return toggleBooleanColumnVersioned(islandUuid, actor, "pvp", "island.pvp.toggle",
                operationId, expectedWriteEpoch);
    }

    private VersionedBoolean toggleBooleanColumnVersioned(UUID islandUuid, Actor actor, String column,
                                                           String action, UUID operationId,
                                                           UUID expectedWriteEpoch) {
        return inTransaction(connection -> {
            boolean current;
            long currentStateVersion;

            String readSql = "SELECT " + column + ", state_version, write_epoch FROM "
                    + prefix + "islands WHERE island_uuid = ? FOR UPDATE";
            try (PreparedStatement stmt = connection.prepareStatement(readSql)) {
                stmt.setString(1, islandUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new IslandDoesNotExistException();
                    }
                    current = rs.getBoolean(1);
                    currentStateVersion = rs.getLong("state_version");
                    if (expectedWriteEpoch != null
                            && !expectedWriteEpoch.toString().equals(rs.getString("write_epoch"))) {
                        throw new WrongIslandHostException();
                    }
                }
            }

            VersionedBoolean replay = getToggleOperation(connection, operationId, islandUuid, action);
            if (replay != null) {
                return new VersionedBoolean(replay.value(), currentStateVersion);
            }

            requireRole(connection, islandUuid, actor, RequiredRole.MEMBER);

            boolean next = !current;

            String updateSql = "UPDATE " + prefix + "islands SET " + column
                    + " = ? WHERE island_uuid = ?";
            executeUpdate(connection, updateSql, stmt -> {
                stmt.setBoolean(1, next);
                stmt.setString(2, islandUuid.toString());
            });

            VersionedBoolean committed = new VersionedBoolean(next, bumpStateVersion(connection, islandUuid));
            String operationSql = "INSERT INTO " + prefix
                    + "island_operations (operation_id, island_uuid, action, boolean_result, "
                    + "state_version) VALUES (?, ?, ?, ?, ?)";
            executeUpdate(connection, operationSql, stmt -> {
                stmt.setString(1, operationId.toString());
                stmt.setString(2, islandUuid.toString());
                stmt.setString(3, action);
                stmt.setBoolean(4, committed.value());
                stmt.setLong(5, committed.version());
            });
            return committed;
        });
    }

    public long setOwner(UUID islandUuid, Actor actor, UUID newOwnerUuid) {
        return setOwner(islandUuid, actor, newOwnerUuid, null);
    }

    public long setOwner(UUID islandUuid, Actor actor, UUID newOwnerUuid, UUID expectedWriteEpoch) {
        return inTransaction(connection -> {
            lockIsland(connection, islandUuid, expectedWriteEpoch);
            requireRole(connection, islandUuid, actor, RequiredRole.OWNER);

            UUID oldOwnerUuid = getIslandOwner(connection, islandUuid)
                    .orElseThrow(IslandDoesNotExistException::new);

            if (oldOwnerUuid.equals(newOwnerUuid)) {
                throw new PlayerAlreadyOwnerException();
            }

            String updateRole = "UPDATE " + prefix
                    + "island_players SET role = ? "
                    + "WHERE player_uuid = ? AND island_uuid = ? AND role = ?";
            int demoted = executeUpdate(connection, updateRole, stmt -> {
                stmt.setString(1, "member");
                stmt.setString(2, oldOwnerUuid.toString());
                stmt.setString(3, islandUuid.toString());
                stmt.setString(4, "owner");
            });

            if (demoted != 1) {
                throw new IslandPlayerDoesNotExistException();
            }

            int promoted = executeUpdate(connection, updateRole, stmt -> {
                stmt.setString(1, "owner");
                stmt.setString(2, newOwnerUuid.toString());
                stmt.setString(3, islandUuid.toString());
                stmt.setString(4, "member");
            });

            if (promoted != 1) {
                throw new IslandPlayerDoesNotExistException();
            }

            return bumpStateVersion(connection, islandUuid);
        });
    }

    public long addBan(UUID islandUuid, Actor actor, UUID playerUuid) {
        return addBan(islandUuid, actor, playerUuid, null);
    }

    public long addBan(UUID islandUuid, Actor actor, UUID playerUuid, UUID expectedWriteEpoch) {
        try {
            return inTransaction(connection -> {
                lockIsland(connection, islandUuid, expectedWriteEpoch);
                requireRole(connection, islandUuid, actor, RequiredRole.MEMBER);

                String sql = insertIfNotMemberSql("island_bans", "banned_player");
                int inserted = executeUpdate(connection, sql, stmt -> {
                    stmt.setString(1, islandUuid.toString());
                    stmt.setString(2, playerUuid.toString());
                    stmt.setString(3, islandUuid.toString());
                    stmt.setString(4, playerUuid.toString());
                });

                if (inserted == 0) {
                    throw new CannotBanIslandPlayerException();
                }

                return bumpStateVersion(connection, islandUuid);
            });
        } catch (ConstraintViolationException e) {
            throw new PlayerAlreadyBannedException();
        }
    }

    public long addCoop(UUID islandUuid, Actor actor, UUID playerUuid) {
        return addCoop(islandUuid, actor, playerUuid, null);
    }

    public long addCoop(UUID islandUuid, Actor actor, UUID playerUuid, UUID expectedWriteEpoch) {
        try {
            return inTransaction(connection -> {
                lockIsland(connection, islandUuid, expectedWriteEpoch);
                requireRole(connection, islandUuid, actor, RequiredRole.MEMBER);

                String sql = insertIfNotMemberSql("island_coops", "cooped_player");
                int inserted = executeUpdate(connection, sql, stmt -> {
                    stmt.setString(1, islandUuid.toString());
                    stmt.setString(2, playerUuid.toString());
                    stmt.setString(3, islandUuid.toString());
                    stmt.setString(4, playerUuid.toString());
                });

                if (inserted == 0) {
                    throw new CannotCoopIslandPlayerException();
                }

                return bumpStateVersion(connection, islandUuid);
            });
        } catch (ConstraintViolationException e) {
            throw new PlayerAlreadyCoopedException();
        }
    }

    public void setIslandLevel(UUID islandUuid, int level) {
        String sql = "INSERT INTO " + prefix
                + "island_levels (island_uuid, level) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE level = ?";
        executeUpdate(sql, stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setInt(2, level);
            stmt.setInt(3, level);
        });
    }

    public void setPlayerName(UUID uuid, String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Player name cannot be null or empty");
        }

        String sql = "INSERT INTO " + prefix
                + "player_uuid (uuid, name, name_lower) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE name = ?, name_lower = ?";
        executeUpdate(sql, stmt -> {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
            stmt.setString(3, name.toLowerCase(Locale.ROOT));
            stmt.setString(4, name);
            stmt.setString(5, name.toLowerCase(Locale.ROOT));
        });
    }

    public void deleteIsland(UUID islandUuid, Actor actor) {
        deleteIsland(islandUuid, actor, null);
    }

    public void deleteIsland(UUID islandUuid, Actor actor, UUID expectedWriteEpoch) {
        inTransaction(connection -> {
            lockIsland(connection, islandUuid, expectedWriteEpoch);
            requireRole(connection, islandUuid, actor, RequiredRole.OWNER);

            executeUpdate(connection, "DELETE FROM " + prefix + "islands WHERE island_uuid = ?;", stmt -> {
                stmt.setString(1, islandUuid.toString());
            });

            return null;
        });
    }

    public long removeMember(UUID islandUuid, Actor actor, UUID playerUuid) {
        return removeMember(islandUuid, actor, playerUuid, null);
    }

    public long removeMember(UUID islandUuid, Actor actor, UUID playerUuid, UUID expectedWriteEpoch) {
        return inTransaction(connection -> {
            lockIsland(connection, islandUuid, expectedWriteEpoch);
            requireRole(connection, islandUuid, actor, RequiredRole.MEMBER);

            String sql = "DELETE FROM " + prefix
                    + "island_players WHERE player_uuid = ? AND island_uuid = ? AND role <> ?";
            int deleted = executeUpdate(connection, sql, stmt -> {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, islandUuid.toString());
                stmt.setString(3, "owner");
            });

            if (deleted == 0) {
                // Either the player is not on this island, or they are its owner.
                UUID ownerUuid = getIslandOwner(connection, islandUuid).orElse(null);
                if (playerUuid.equals(ownerUuid)) {
                    throw new CannotRemoveOwnerException();
                }
                throw new IslandPlayerDoesNotExistException();
            }

            return bumpStateVersion(connection, islandUuid);
        });
    }

    public void deleteHome(UUID islandUuid, UUID playerUuid, String homeName) {
        String sql = "DELETE FROM " + prefix
                + "island_homes WHERE island_uuid = ? AND player_uuid = ? AND home_name = ?";
        int deleted = executeUpdate(sql, stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, playerUuid.toString());
            stmt.setString(3, homeName);
        });

        if (deleted == 0) {
            throw new HomeDoesNotExistException();
        }
    }

    public void deleteWarp(UUID islandUuid, UUID playerUuid, String warpName) {
        String sql = "DELETE FROM " + prefix
                + "island_warps WHERE island_uuid = ? AND player_uuid = ? AND warp_name = ?";
        int deleted = executeUpdate(sql, stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, playerUuid.toString());
            stmt.setString(3, warpName);
        });

        if (deleted == 0) {
            throw new WarpDoesNotExistException();
        }
    }

    public long removeBan(UUID islandUuid, Actor actor, UUID playerUuid) {
        return removeBan(islandUuid, actor, playerUuid, null);
    }

    public long removeBan(UUID islandUuid, Actor actor, UUID playerUuid, UUID expectedWriteEpoch) {
        return inTransaction(connection -> {
            lockIsland(connection, islandUuid, expectedWriteEpoch);
            requireRole(connection, islandUuid, actor, RequiredRole.MEMBER);

            String sql = "DELETE FROM " + prefix
                    + "island_bans WHERE island_uuid = ? AND banned_player = ?";
            int deleted = executeUpdate(connection, sql, stmt -> {
                stmt.setString(1, islandUuid.toString());
                stmt.setString(2, playerUuid.toString());
            });

            if (deleted == 0) {
                throw new PlayerNotBannedException();
            }

            return bumpStateVersion(connection, islandUuid);
        });
    }

    public long removeCoop(UUID islandUuid, Actor actor, UUID playerUuid) {
        return removeCoop(islandUuid, actor, playerUuid, null);
    }

    public long removeCoop(UUID islandUuid, Actor actor, UUID playerUuid, UUID expectedWriteEpoch) {
        return inTransaction(connection -> {
            lockIsland(connection, islandUuid, expectedWriteEpoch);
            requireRole(connection, islandUuid, actor, RequiredRole.MEMBER);

            String sql = "DELETE FROM " + prefix
                    + "island_coops WHERE island_uuid = ? AND cooped_player = ?";
            int deleted = executeUpdate(connection, sql, stmt -> {
                stmt.setString(1, islandUuid.toString());
                stmt.setString(2, playerUuid.toString());
            });

            if (deleted == 0) {
                throw new PlayerNotCoopedException();
            }

            return bumpStateVersion(connection, islandUuid);
        });
    }

    public Island getIslandSnapshot(UUID islandUuid) {
        // One transaction so the four reads share a single consistent view: read piecemeal on
        // autocommit, a concurrent owner transfer or delete committing between the statements
        // produces a torn snapshot (e.g. an island row with no owner) that fails the whole refresh.
        return inTransaction(connection -> {
            boolean lock;
            boolean pvp;
            long stateVersion;

            String islandSql = "SELECT `lock`, pvp, state_version FROM " + prefix
                    + "islands WHERE island_uuid = ? LIMIT 1";
            try (PreparedStatement stmt = connection.prepareStatement(islandSql)) {
                stmt.setString(1, islandUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }

                    lock = rs.getBoolean("lock");
                    pvp = rs.getBoolean("pvp");
                    stateVersion = rs.getLong("state_version");
                }
            }

            UUID ownerUuid = null;
            Set<UUID> members = new LinkedHashSet<>();

            String playersSql = "SELECT player_uuid, role FROM " + prefix
                    + "island_players WHERE island_uuid = ?";
            try (PreparedStatement stmt = connection.prepareStatement(playersSql)) {
                stmt.setString(1, islandUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID playerUuid = parseRequiredUuid(
                                rs.getString("player_uuid"), "island_players.player_uuid");
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
                throw new IllegalStateException(
                        "Island owner does not exist in database for island: " + islandUuid);
            }

            Set<UUID> coops = new LinkedHashSet<>();
            String coopsSql = "SELECT cooped_player FROM " + prefix + "island_coops WHERE island_uuid = ?";
            try (PreparedStatement stmt = connection.prepareStatement(coopsSql)) {
                stmt.setString(1, islandUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        coops.add(parseRequiredUuid(
                                rs.getString("cooped_player"), "island_coops.cooped_player"));
                    }
                }
            }

            Set<UUID> bans = new LinkedHashSet<>();
            String bansSql = "SELECT banned_player FROM " + prefix + "island_bans WHERE island_uuid = ?";
            try (PreparedStatement stmt = connection.prepareStatement(bansSql)) {
                stmt.setString(1, islandUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        bans.add(parseRequiredUuid(
                                rs.getString("banned_player"), "island_bans.banned_player"));
                    }
                }
            }

            Set<UUID> memberCopy = members.isEmpty() ? Set.of() : Set.copyOf(members);
            Set<UUID> coopCopy = coops.isEmpty() ? Set.of() : Set.copyOf(coops);
            Set<UUID> banCopy = bans.isEmpty() ? Set.of() : Set.copyOf(bans);
            return new Island(islandUuid, lock, pvp, ownerUuid, memberCopy, coopCopy, banCopy,
                    stateVersion);
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
            throw new IllegalStateException("Invalid UUID value in DatabaseHandler for field "
                    + fieldName + ": " + value, e);
        }
    }

    private void lockIsland(Connection connection, UUID islandUuid) throws SQLException {
        lockIsland(connection, islandUuid, null);
    }

    private void lockIsland(Connection connection, UUID islandUuid, UUID expectedWriteEpoch)
            throws SQLException {
        String sql = "SELECT write_epoch FROM " + prefix
                + "islands WHERE island_uuid = ? FOR UPDATE";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, islandUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new IslandDoesNotExistException();
                }
                if (expectedWriteEpoch != null
                        && !expectedWriteEpoch.toString().equals(rs.getString("write_epoch"))) {
                    throw new WrongIslandHostException();
                }
            }
        }
    }

    public void bindWriteEpoch(UUID islandUuid, UUID writeEpoch) {
        inTransaction(connection -> {
            lockIsland(connection, islandUuid);
            String sql = "UPDATE " + prefix
                    + "islands SET write_epoch = ? WHERE island_uuid = ?";
            executeUpdate(connection, sql, stmt -> {
                stmt.setString(1, writeEpoch.toString());
                stmt.setString(2, islandUuid.toString());
            });
            return null;
        });
    }

    public boolean isIslandProvisioning(UUID islandUuid) {
        return withConnection(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement("SELECT provision_state FROM " + prefix
                    + "islands WHERE island_uuid = ? LIMIT 1")) {
                stmt.setString(1, islandUuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new IslandDoesNotExistException();
                    }
                    return "provisioning".equals(rs.getString(1));
                }
            }
        });
    }

    public void markIslandReady(UUID islandUuid, UUID expectedWriteEpoch) {
        inTransaction(connection -> {
            lockIsland(connection, islandUuid, expectedWriteEpoch);
            executeUpdate(connection, "UPDATE " + prefix
                    + "islands SET provision_state = 'ready' WHERE island_uuid = ?", stmt ->
                    stmt.setString(1, islandUuid.toString()));
            return null;
        });
    }

    private long bumpStateVersion(Connection connection, UUID islandUuid) throws SQLException {
        int updated = executeUpdate(connection,
                "UPDATE " + prefix + "islands SET state_version = state_version + 1 WHERE island_uuid = ?",
                stmt -> stmt.setString(1, islandUuid.toString()));
        if (updated != 1) {
            throw new IslandDoesNotExistException();
        }

        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT state_version FROM " + prefix + "islands WHERE island_uuid = ?")) {
            stmt.setString(1, islandUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new IslandDoesNotExistException();
                }
                return rs.getLong(1);
            }
        }
    }

    private VersionedBoolean getToggleOperation(Connection connection, UUID operationId,
                                                UUID islandUuid, String action) throws SQLException {
        String sql = "SELECT island_uuid, action, boolean_result, state_version FROM "
                + prefix + "island_operations WHERE operation_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, operationId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                boolean sameIsland = islandUuid.toString().equals(rs.getString("island_uuid"));
                boolean sameAction = action.equals(rs.getString("action"));
                if (!sameIsland || !sameAction) {
                    throw new IllegalStateException(
                            "Operation ID was reused for a different mutation: " + operationId);
                }
                return new VersionedBoolean(rs.getBoolean("boolean_result"), rs.getLong("state_version"));
            }
        }
    }

    private void requireRole(Connection connection, UUID islandUuid, Actor actor,
                             RequiredRole required) throws SQLException {
        if (!(actor instanceof Actor.Player player)) {
            return;
        }

        String role = getIslandRole(connection, islandUuid, player.uuid())
                .orElseThrow(IslandDoesNotExistException::new);

        if (required == RequiredRole.OWNER && !"owner".equalsIgnoreCase(role)) {
            throw new NotIslandOwnerException();
        }
    }

    private Optional<String> getIslandRole(Connection connection, UUID islandUuid, UUID playerUuid)
            throws SQLException {
        String sql = "SELECT role FROM " + prefix
                + "island_players WHERE island_uuid = ? AND player_uuid = ? LIMIT 1";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, playerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(rs.getString("role"));
            }
        }
    }

    private Optional<String> getIslandDefaultHome(Connection connection, UUID islandUuid) throws SQLException {
        String sql = """
                SELECT h.home_location
                FROM %sisland_homes h
                JOIN %sisland_players p
                  ON p.player_uuid = h.player_uuid AND p.island_uuid = h.island_uuid
                WHERE h.island_uuid = ? AND h.home_name = ? AND p.role = ?
                LIMIT 1
                """.formatted(prefix, prefix);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, "default");
            stmt.setString(3, "owner");

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(rs.getString("home_location"));
            }
        }
    }

    private String islandSpawnLocation() {
        return spawnLocation;
    }

    private void upsertHome(UUID islandUuid, UUID playerUuid, String homeName,
                            String homeLocation) {
        executeUpdate(homeUpsertSql(), stmt -> {
            setHomeParameters(stmt, islandUuid, playerUuid, homeName, homeLocation);
        });
    }

    private void upsertHome(Connection connection, UUID islandUuid, UUID playerUuid,
                            String homeName, String homeLocation) throws SQLException {
        executeUpdate(connection, homeUpsertSql(), stmt -> {
            setHomeParameters(stmt, islandUuid, playerUuid, homeName, homeLocation);
        });
    }

    private String homeUpsertSql() {
        return "INSERT INTO " + prefix
                + "island_homes (player_uuid, island_uuid, home_name, home_location) "
                + "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                + "island_uuid = VALUES(island_uuid), home_location = VALUES(home_location)";
    }

    private String insertIfNotMemberSql(String tableName, String playerColumn) {
        return "INSERT INTO " + prefix + tableName + " (island_uuid, " + playerColumn + ") "
                + "SELECT ?, ? FROM DUAL WHERE NOT EXISTS ("
                + "SELECT 1 FROM " + prefix + "island_players "
                + "WHERE island_uuid = ? AND player_uuid = ?)";
    }

    private void setHomeParameters(PreparedStatement statement, UUID islandUuid, UUID playerUuid,
                                   String homeName, String homeLocation) throws SQLException {
        statement.setString(1, playerUuid.toString());
        statement.setString(2, islandUuid.toString());
        statement.setString(3, homeName);
        statement.setString(4, homeLocation);
    }

    private Optional<UUID> getIslandOwner(Connection connection, UUID islandUuid)
            throws SQLException {
        String sql = "SELECT player_uuid FROM " + prefix
                + "island_players WHERE island_uuid = ? AND role = ? LIMIT 1";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, "owner");

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(parseRequiredUuid(
                        rs.getString("player_uuid"), "island_players.player_uuid"));
            }
        }
    }

    private boolean playerHasIsland(Connection connection, UUID playerUuid) throws SQLException {
        return getPlayerIsland(connection, playerUuid).isPresent();
    }

    private Optional<UUID> getPlayerIsland(Connection connection, UUID playerUuid)
            throws SQLException {
        String sql = "SELECT island_uuid FROM " + prefix
                + "island_players WHERE player_uuid = ? LIMIT 1";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(parseRequiredUuid(
                        rs.getString("island_uuid"), "island_players.island_uuid"));
            }
        }
    }

    private enum RequiredRole {
        MEMBER, OWNER
    }

    private static final class ConstraintViolationException extends RuntimeException {
        ConstraintViolationException(Throwable cause) {
            super(cause);
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
    public interface ConnectionFunction<T> {
        T apply(Connection connection) throws SQLException;
    }
}
