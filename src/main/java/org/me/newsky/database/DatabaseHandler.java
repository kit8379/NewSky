package org.me.newsky.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.*;
import org.me.newsky.model.Actor;
import org.me.newsky.model.Island;
import org.me.newsky.model.IslandTop;

import java.sql.*;
import java.util.*;

public class DatabaseHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final HikariDataSource dataSource;
    private final String prefix;

    public DatabaseHandler(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;
        this.config = config;
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

    private <T> T withConnection(ConnectionFunction<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            return work.apply(connection);
        } catch (SQLException e) {
            plugin.severe("Database connection operation failed.", e);
            throw new RuntimeException(e);
        }
    }

    private <T> T inTransaction(ConnectionFunction<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }

                if (e instanceof SQLIntegrityConstraintViolationException) {
                    throw new ConstraintViolationException(e);
                }

                if (e instanceof SQLException) {
                    plugin.severe("Database transaction failed.", e);
                    throw new RuntimeException(e);
                }

                throw (RuntimeException) e;
            }
        } catch (SQLException e) {
            plugin.severe("Database transaction connection failed.", e);
            throw new RuntimeException(e);
        }
    }

    private int executeUpdate(Connection connection, String query, PreparedStatementConsumer consumer) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            consumer.use(statement);
            return statement.executeUpdate();
        }
    }

    private int executeUpdate(String query, PreparedStatementConsumer consumer) {
        try (Connection connection = dataSource.getConnection()) {
            return executeUpdate(connection, query, consumer);
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new ConstraintViolationException(e);
        } catch (SQLException e) {
            plugin.severe("Database Update failed: " + query, e);
            throw new RuntimeException(e);
        }
    }

    private <T> T executeQuery(Connection connection, String query, PreparedStatementConsumer consumer, ResultFunction<T> function) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            consumer.use(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                return function.apply(resultSet);
            }
        }
    }

    private <T> T executeQuery(String query, PreparedStatementConsumer consumer, ResultFunction<T> function) {
        try (Connection connection = dataSource.getConnection()) {
            return executeQuery(connection, query, consumer, function);
        } catch (SQLException e) {
            plugin.severe("Database Query failed: " + query, e);
            throw new RuntimeException(e);
        }
    }

    // ================================================================================================================
    // Table creation
    // ================================================================================================================

    private void createTables() {
        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "islands (" + "island_uuid CHAR(36) NOT NULL," + "`lock` BOOLEAN NOT NULL DEFAULT FALSE," + "pvp BOOLEAN NOT NULL DEFAULT FALSE," + "PRIMARY KEY (island_uuid)" + ") ENGINE=InnoDB;", stmt -> {
        });

        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_players (" + "player_uuid CHAR(36) NOT NULL," + "island_uuid CHAR(36) NOT NULL," + "role VARCHAR(56) NOT NULL," + "PRIMARY KEY (player_uuid, island_uuid)," + "UNIQUE KEY uq_island_players_player_uuid (player_uuid)," + "KEY idx_island_players_island_uuid (island_uuid)," + "KEY idx_island_players_island_role (island_uuid, role)," + "CONSTRAINT fk_island_players_island " + "FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid) " + "ON DELETE CASCADE" + ") ENGINE=InnoDB;", stmt -> {
        });

        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_homes (" + "player_uuid CHAR(36) NOT NULL," + "home_name VARCHAR(32) NOT NULL," + "home_location VARCHAR(256)," + "island_uuid CHAR(36) NOT NULL," + "PRIMARY KEY (player_uuid, island_uuid, home_name)," + "KEY idx_island_homes_island (island_uuid)," + "KEY idx_island_homes_island_player (island_uuid, player_uuid)," + "CONSTRAINT fk_island_homes_island " + "FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid) " + "ON DELETE CASCADE," + "CONSTRAINT fk_island_homes_player_membership " + "FOREIGN KEY (player_uuid, island_uuid) REFERENCES " + prefix + "island_players(player_uuid, island_uuid) " + "ON DELETE CASCADE" + ") ENGINE=InnoDB;", stmt -> {
        });

        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_warps (" + "player_uuid CHAR(36) NOT NULL," + "warp_name VARCHAR(32) NOT NULL," + "warp_location VARCHAR(256)," + "island_uuid CHAR(36) NOT NULL," + "PRIMARY KEY (player_uuid, island_uuid, warp_name)," + "KEY idx_island_warps_island (island_uuid)," + "KEY idx_island_warps_island_player (island_uuid, player_uuid)," + "CONSTRAINT fk_island_warps_island " + "FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid) " + "ON DELETE CASCADE," + "CONSTRAINT fk_island_warps_player_membership " + "FOREIGN KEY (player_uuid, island_uuid) REFERENCES " + prefix + "island_players(player_uuid, island_uuid) " + "ON DELETE CASCADE" + ") ENGINE=InnoDB;", stmt -> {
        });

        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_bans (" + "island_uuid CHAR(36) NOT NULL," + "banned_player CHAR(36) NOT NULL," + "PRIMARY KEY (island_uuid, banned_player)," + "CONSTRAINT fk_island_bans_island " + "FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid) " + "ON DELETE CASCADE" + ") ENGINE=InnoDB;", stmt -> {
        });

        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_coops (" + "island_uuid CHAR(36) NOT NULL," + "cooped_player CHAR(36) NOT NULL," + "PRIMARY KEY (island_uuid, cooped_player)," + "KEY idx_island_coops_cooped_player (cooped_player, island_uuid)," + "CONSTRAINT fk_island_coops_island " + "FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid) " + "ON DELETE CASCADE" + ") ENGINE=InnoDB;", stmt -> {
        });

        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "island_levels (" + "island_uuid CHAR(36) NOT NULL," + "level INT NOT NULL," + "PRIMARY KEY (island_uuid)," + "CONSTRAINT fk_island_levels_island " + "FOREIGN KEY (island_uuid) REFERENCES " + prefix + "islands(island_uuid) " + "ON DELETE CASCADE" + ") ENGINE=InnoDB;", stmt -> {
        });

        executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "player_uuid (" + "uuid CHAR(36) NOT NULL," + "name VARCHAR(16) NOT NULL," + "name_lower VARCHAR(16) NOT NULL," + "PRIMARY KEY (uuid)," + "KEY idx_player_uuid_name (name)," + "KEY idx_player_uuid_name_lower (name_lower)" + ") ENGINE=InnoDB;", stmt -> {
        });
    }

    // ================================================================================================================
    // Targeted reads
    // ================================================================================================================

    public boolean isIslandLock(UUID islandUuid) {
        return executeQuery("SELECT `lock` FROM " + prefix + "islands WHERE island_uuid = ? LIMIT 1",
                stmt -> stmt.setString(1, islandUuid.toString()),
                rs -> rs.next() && rs.getBoolean("lock"));
    }

    public boolean isIslandPvp(UUID islandUuid) {
        return executeQuery("SELECT pvp FROM " + prefix + "islands WHERE island_uuid = ? LIMIT 1",
                stmt -> stmt.setString(1, islandUuid.toString()),
                rs -> rs.next() && rs.getBoolean("pvp"));
    }

    public int getIslandLevel(UUID islandUuid) {
        return executeQuery("SELECT level FROM " + prefix + "island_levels WHERE island_uuid = ? LIMIT 1",
                stmt -> stmt.setString(1, islandUuid.toString()),
                rs -> rs.next() ? rs.getInt("level") : 0);
    }

    public Optional<UUID> getIslandOwner(UUID islandUuid) {
        return withConnection(connection -> getIslandOwner(connection, islandUuid));
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

    /**
     * Removes every coop entry of one player and returns the affected islands.
     */
    public Set<UUID> deleteAllCoopsOfPlayer(UUID playerUuid) {
        return inTransaction(connection -> {
            Set<UUID> touched = executeQuery(connection, "SELECT island_uuid FROM " + prefix + "island_coops WHERE cooped_player = ? FOR UPDATE", stmt -> {
                stmt.setString(1, playerUuid.toString());
            }, rs -> {
                Set<UUID> result = new HashSet<>();
                while (rs.next()) {
                    result.add(parseRequiredUuid(rs.getString("island_uuid"), "island_coops.island_uuid"));
                }
                return result;
            });

            if (!touched.isEmpty()) {
                executeUpdate(connection, "DELETE FROM " + prefix + "island_coops WHERE cooped_player = ?;", stmt -> {
                    stmt.setString(1, playerUuid.toString());
                });
            }

            return Set.copyOf(touched);
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

            executeQuery(connection, topSql, stmt -> {
                stmt.setString(1, "owner");
                stmt.setInt(2, safeLimit);
            }, rs -> {
                while (rs.next()) {
                    UUID islandUuid = parseRequiredUuid(rs.getString("island_uuid"), "top.island_uuid");
                    UUID ownerUuid = parseRequiredUuid(rs.getString("owner_uuid"), "top.owner_uuid");
                    int level = rs.getInt("level");

                    islandOrder.add(islandUuid);
                    ownerByIsland.put(islandUuid, ownerUuid);
                    levelByIsland.put(islandUuid, level);
                }
                return null;
            });

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

            executeQuery(connection, memberSql.toString(), stmt -> {
                stmt.setString(1, "member");

                for (int i = 0; i < islandOrder.size(); i++) {
                    stmt.setString(i + 2, islandOrder.get(i).toString());
                }
            }, rs -> {
                while (rs.next()) {
                    UUID islandUuid = parseRequiredUuid(rs.getString("island_uuid"), "topMembers.island_uuid");
                    UUID memberUuid = parseRequiredUuid(rs.getString("player_uuid"), "topMembers.player_uuid");

                    membersByIsland.computeIfAbsent(islandUuid, ignored -> new LinkedHashSet<>()).add(memberUuid);
                }
                return null;
            });

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

    public void addIslandData(UUID islandUuid, UUID ownerUuid) {
        try {
            inTransaction(connection -> {
                if (getPlayerIsland(connection, ownerUuid).isPresent()) {
                    throw new IslandAlreadyExistException();
                }

                String homePoint = config.getIslandSpawnX() + "," + config.getIslandSpawnY() + "," + config.getIslandSpawnZ() + "," + config.getIslandSpawnYaw() + "," + config.getIslandSpawnPitch();

                executeUpdate(connection, "INSERT INTO " + prefix + "islands (island_uuid) VALUES (?);", stmt -> {
                    stmt.setString(1, islandUuid.toString());
                });

                executeUpdate(connection, "INSERT INTO " + prefix + "island_players (player_uuid, island_uuid, role) VALUES (?, ?, ?);", stmt -> {
                    stmt.setString(1, ownerUuid.toString());
                    stmt.setString(2, islandUuid.toString());
                    stmt.setString(3, "owner");
                });

                executeUpdate(connection, "INSERT INTO " + prefix + "island_homes (player_uuid, island_uuid, home_name, home_location) VALUES (?, ?, ?, ?) " + "ON DUPLICATE KEY UPDATE island_uuid = VALUES(island_uuid), home_location = VALUES(home_location);", stmt -> {
                    stmt.setString(1, ownerUuid.toString());
                    stmt.setString(2, islandUuid.toString());
                    stmt.setString(3, "default");
                    stmt.setString(4, homePoint);
                });

                return null;
            });
        } catch (ConstraintViolationException e) {
            throw new IslandAlreadyExistException();
        }
    }

    public void addIslandPlayer(UUID islandUuid, UUID playerUuid, String role) {
        try {
            inTransaction(connection -> {
                lockIsland(connection, islandUuid);

                Optional<UUID> playerIsland = getPlayerIsland(connection, playerUuid);
                if (playerIsland.isPresent()) {
                    if (playerIsland.get().equals(islandUuid)) {
                        throw new IslandPlayerAlreadyExistsException();
                    }
                    throw new IslandAlreadyExistException();
                }

                // Seeded under the island lock so it cannot race the owner moving their home.
                String homePoint = getIslandDefaultHome(connection, islandUuid).orElse(config.getIslandSpawnX() + "," + config.getIslandSpawnY() + "," + config.getIslandSpawnZ() + "," + config.getIslandSpawnYaw() + "," + config.getIslandSpawnPitch());

                executeUpdate(connection, "INSERT INTO " + prefix + "island_players (player_uuid, island_uuid, role) VALUES (?, ?, ?);", stmt -> {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, islandUuid.toString());
                    stmt.setString(3, role);
                });

                executeUpdate(connection, "INSERT INTO " + prefix + "island_homes (player_uuid, island_uuid, home_name, home_location) VALUES (?, ?, ?, ?) " + "ON DUPLICATE KEY UPDATE island_uuid = VALUES(island_uuid), home_location = VALUES(home_location);", stmt -> {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, islandUuid.toString());
                    stmt.setString(3, "default");
                    stmt.setString(4, homePoint);
                });

                executeUpdate(connection, "DELETE FROM " + prefix + "island_bans WHERE island_uuid = ? AND banned_player = ?;", stmt -> {
                    stmt.setString(1, islandUuid.toString());
                    stmt.setString(2, playerUuid.toString());
                });

                executeUpdate(connection, "DELETE FROM " + prefix + "island_coops WHERE island_uuid = ? AND cooped_player = ?;", stmt -> {
                    stmt.setString(1, islandUuid.toString());
                    stmt.setString(2, playerUuid.toString());
                });

                return null;
            });
        } catch (ConstraintViolationException e) {
            throw new IslandAlreadyExistException();
        }
    }

    public void updateHomePoint(UUID islandUuid, UUID playerUuid, String homeName, String homeLocation) {
        try {
            executeUpdate("INSERT INTO " + prefix + "island_homes (player_uuid, island_uuid, home_name, home_location) VALUES (?, ?, ?, ?) " + "ON DUPLICATE KEY UPDATE island_uuid = VALUES(island_uuid), home_location = VALUES(home_location);", stmt -> {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, islandUuid.toString());
                stmt.setString(3, homeName);
                stmt.setString(4, homeLocation);
            });
        } catch (ConstraintViolationException e) {
            throw new LocationNotInIslandException();
        }
    }

    public void updateWarpPoint(UUID islandUuid, UUID playerUuid, String warpName, String warpLocation) {
        try {
            executeUpdate("INSERT INTO " + prefix + "island_warps (player_uuid, island_uuid, warp_name, warp_location) VALUES (?, ?, ?, ?) " + "ON DUPLICATE KEY UPDATE island_uuid = VALUES(island_uuid), warp_location = VALUES(warp_location);", stmt -> {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, islandUuid.toString());
                stmt.setString(3, warpName);
                stmt.setString(4, warpLocation);
            });
        } catch (ConstraintViolationException e) {
            throw new LocationNotInIslandException();
        }
    }

    public boolean toggleIslandLock(Actor actor, UUID islandUuid) {
        return toggleBooleanColumn(actor, islandUuid, "`lock`");
    }

    public boolean toggleIslandPvp(Actor actor, UUID islandUuid) {
        return toggleBooleanColumn(actor, islandUuid, "pvp");
    }

    private boolean toggleBooleanColumn(Actor actor, UUID islandUuid, String column) {
        return inTransaction(connection -> {
            // This select is the island lock as well as the value read, so the role check has to
            // come after it: checking first would read the role without holding the lock.
            boolean current = executeQuery(connection, "SELECT " + column + " FROM " + prefix + "islands WHERE island_uuid = ? FOR UPDATE", stmt -> {
                stmt.setString(1, islandUuid.toString());
            }, rs -> {
                if (!rs.next()) {
                    throw new IslandDoesNotExistException();
                }
                return rs.getBoolean(1);
            });

            requireRole(actor, connection, islandUuid, RequiredRole.MEMBER);

            boolean next = !current;

            executeUpdate(connection, "UPDATE " + prefix + "islands SET " + column + " = ? WHERE island_uuid = ?", stmt -> {
                stmt.setBoolean(1, next);
                stmt.setString(2, islandUuid.toString());
            });

            return next;
        });
    }

    public void updateIslandOwner(Actor actor, UUID islandUuid, UUID newOwnerUuid) {
        inTransaction(connection -> {
            lockIsland(connection, islandUuid);
            requireRole(actor, connection, islandUuid, RequiredRole.OWNER);

            UUID oldOwnerUuid = getIslandOwner(connection, islandUuid).orElseThrow(IslandDoesNotExistException::new);

            if (oldOwnerUuid.equals(newOwnerUuid)) {
                throw new PlayerAlreadyOwnerException();
            }

            int demoted = executeUpdate(connection, "UPDATE " + prefix + "island_players SET role = ? WHERE player_uuid = ? AND island_uuid = ? AND role = ?", stmt -> {
                stmt.setString(1, "member");
                stmt.setString(2, oldOwnerUuid.toString());
                stmt.setString(3, islandUuid.toString());
                stmt.setString(4, "owner");
            });

            if (demoted != 1) {
                throw new IslandPlayerDoesNotExistException();
            }

            int promoted = executeUpdate(connection, "UPDATE " + prefix + "island_players SET role = ? WHERE player_uuid = ? AND island_uuid = ? AND role = ?", stmt -> {
                stmt.setString(1, "owner");
                stmt.setString(2, newOwnerUuid.toString());
                stmt.setString(3, islandUuid.toString());
                stmt.setString(4, "member");
            });

            if (promoted != 1) {
                throw new IslandPlayerDoesNotExistException();
            }

            return null;
        });
    }

    public void updateBanPlayer(Actor actor, UUID islandUuid, UUID playerUuid) {
        try {
            inTransaction(connection -> {
                lockIsland(connection, islandUuid);
                requireRole(actor, connection, islandUuid, RequiredRole.MEMBER);

                int inserted = executeUpdate(connection, "INSERT INTO " + prefix + "island_bans (island_uuid, banned_player) " + "SELECT ?, ? FROM DUAL " + "WHERE NOT EXISTS (" + "SELECT 1 FROM " + prefix + "island_players " + "WHERE island_uuid = ? AND player_uuid = ?" + ");", stmt -> {
                    stmt.setString(1, islandUuid.toString());
                    stmt.setString(2, playerUuid.toString());
                    stmt.setString(3, islandUuid.toString());
                    stmt.setString(4, playerUuid.toString());
                });

                if (inserted == 0) {
                    throw new CannotBanIslandPlayerException();
                }

                return null;
            });
        } catch (ConstraintViolationException e) {
            throw new PlayerAlreadyBannedException();
        }
    }

    public void updateCoopPlayer(Actor actor, UUID islandUuid, UUID playerUuid) {
        try {
            inTransaction(connection -> {
                lockIsland(connection, islandUuid);
                requireRole(actor, connection, islandUuid, RequiredRole.MEMBER);

                int inserted = executeUpdate(connection, "INSERT INTO " + prefix + "island_coops (island_uuid, cooped_player) " + "SELECT ?, ? FROM DUAL " + "WHERE NOT EXISTS (" + "SELECT 1 FROM " + prefix + "island_players " + "WHERE island_uuid = ? AND player_uuid = ?" + ");", stmt -> {
                    stmt.setString(1, islandUuid.toString());
                    stmt.setString(2, playerUuid.toString());
                    stmt.setString(3, islandUuid.toString());
                    stmt.setString(4, playerUuid.toString());
                });

                if (inserted == 0) {
                    throw new CannotCoopIslandPlayerException();
                }

                return null;
            });
        } catch (ConstraintViolationException e) {
            throw new PlayerAlreadyCoopedException();
        }
    }

    public void updateIslandLevel(UUID islandUuid, int level) {
        executeUpdate("INSERT INTO " + prefix + "island_levels (island_uuid, level) VALUES (?, ?) " + "ON DUPLICATE KEY UPDATE level = ?;", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setInt(2, level);
            stmt.setInt(3, level);
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

    public void deleteIsland(Actor actor, UUID islandUuid) {
        inTransaction(connection -> {
            lockIsland(connection, islandUuid);
            requireRole(actor, connection, islandUuid, RequiredRole.OWNER);

            executeUpdate(connection, "DELETE FROM " + prefix + "islands WHERE island_uuid = ?;", stmt -> {
                stmt.setString(1, islandUuid.toString());
            });

            return null;
        });
    }

    public void deleteIslandPlayer(Actor actor, UUID islandUuid, UUID playerUuid) {
        inTransaction(connection -> {
            lockIsland(connection, islandUuid);
            requireRole(actor, connection, islandUuid, RequiredRole.MEMBER);

            int deleted = executeUpdate(connection, "DELETE FROM " + prefix + "island_players WHERE player_uuid = ? AND island_uuid = ? AND role <> ?;", stmt -> {
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

            return null;
        });
    }

    public void deleteHomePoint(UUID islandUuid, UUID playerUuid, String homeName) {
        int deleted = executeUpdate("DELETE FROM " + prefix + "island_homes WHERE island_uuid = ? AND player_uuid = ? AND home_name = ?;", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, playerUuid.toString());
            stmt.setString(3, homeName);
        });

        if (deleted == 0) {
            throw new HomeDoesNotExistException();
        }
    }

    public void deleteWarpPoint(UUID islandUuid, UUID playerUuid, String warpName) {
        int deleted = executeUpdate("DELETE FROM " + prefix + "island_warps WHERE island_uuid = ? AND player_uuid = ? AND warp_name = ?;", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, playerUuid.toString());
            stmt.setString(3, warpName);
        });

        if (deleted == 0) {
            throw new WarpDoesNotExistException();
        }
    }

    public void deleteBanPlayer(Actor actor, UUID islandUuid, UUID playerUuid) {
        inTransaction(connection -> {
            lockIsland(connection, islandUuid);
            requireRole(actor, connection, islandUuid, RequiredRole.MEMBER);

            int deleted = executeUpdate(connection, "DELETE FROM " + prefix + "island_bans WHERE island_uuid = ? AND banned_player = ?;", stmt -> {
                stmt.setString(1, islandUuid.toString());
                stmt.setString(2, playerUuid.toString());
            });

            if (deleted == 0) {
                throw new PlayerNotBannedException();
            }

            return null;
        });
    }

    public void deleteCoopPlayer(Actor actor, UUID islandUuid, UUID playerUuid) {
        inTransaction(connection -> {
            lockIsland(connection, islandUuid);
            requireRole(actor, connection, islandUuid, RequiredRole.MEMBER);

            int deleted = executeUpdate(connection, "DELETE FROM " + prefix + "island_coops WHERE island_uuid = ? AND cooped_player = ?;", stmt -> {
                stmt.setString(1, islandUuid.toString());
                stmt.setString(2, playerUuid.toString());
            });

            if (deleted == 0) {
                throw new PlayerNotCoopedException();
            }

            return null;
        });
    }

    public Island getIslandSnapshot(UUID islandUuid) {
        return withConnection(connection -> {
            String islandSql = "SELECT `lock`, pvp FROM " + prefix + "islands WHERE island_uuid = ? LIMIT 1";
            boolean[] islandData = executeQuery(connection, islandSql, stmt -> {
                stmt.setString(1, islandUuid.toString());
            }, rs -> {
                if (!rs.next()) {
                    return null;
                }
                return new boolean[]{rs.getBoolean("lock"), rs.getBoolean("pvp")};
            });

            if (islandData == null) {
                return null;
            }
            boolean lock = islandData[0];
            boolean pvp = islandData[1];

            UUID ownerUuid = null;
            Set<UUID> members = new LinkedHashSet<>();

            String playersSql = "SELECT player_uuid, role FROM " + prefix + "island_players WHERE island_uuid = ?";
            Map<UUID, String> players = executeQuery(connection, playersSql, stmt -> {
                stmt.setString(1, islandUuid.toString());
            }, rs -> {
                Map<UUID, String> result = new LinkedHashMap<>();
                while (rs.next()) {
                    UUID playerUuid = parseRequiredUuid(rs.getString("player_uuid"), "island_players.player_uuid");
                    result.put(playerUuid, rs.getString("role"));
                }
                return result;
            });

            for (Map.Entry<UUID, String> entry : players.entrySet()) {
                if ("owner".equalsIgnoreCase(entry.getValue())) {
                    ownerUuid = entry.getKey();
                } else if ("member".equalsIgnoreCase(entry.getValue())) {
                    members.add(entry.getKey());
                }
            }

            if (ownerUuid == null) {
                throw new IllegalStateException("Island owner does not exist in database for island: " + islandUuid);
            }

            String coopsSql = "SELECT cooped_player FROM " + prefix + "island_coops WHERE island_uuid = ?";
            Set<UUID> coops = executeQuery(connection, coopsSql, stmt -> {
                stmt.setString(1, islandUuid.toString());
            }, rs -> {
                Set<UUID> result = new LinkedHashSet<>();
                while (rs.next()) {
                    result.add(parseRequiredUuid(rs.getString("cooped_player"), "island_coops.cooped_player"));
                }
                return result;
            });

            String bansSql = "SELECT banned_player FROM " + prefix + "island_bans WHERE island_uuid = ?";
            Set<UUID> bans = executeQuery(connection, bansSql, stmt -> {
                stmt.setString(1, islandUuid.toString());
            }, rs -> {
                Set<UUID> result = new LinkedHashSet<>();
                while (rs.next()) {
                    result.add(parseRequiredUuid(rs.getString("banned_player"), "island_bans.banned_player"));
                }
                return result;
            });

            return new Island(islandUuid, lock, pvp, ownerUuid, members.isEmpty() ? Set.of() : Set.copyOf(members), coops.isEmpty() ? Set.of() : Set.copyOf(coops), bans.isEmpty() ? Set.of() : Set.copyOf(bans));
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

    private void lockIsland(Connection connection, UUID islandUuid) throws SQLException {
        boolean exists = executeQuery(connection, "SELECT 1 FROM " + prefix + "islands WHERE island_uuid = ? FOR UPDATE", stmt -> {
            stmt.setString(1, islandUuid.toString());
        }, ResultSet::next);

        if (!exists) {
            throw new IslandDoesNotExistException();
        }
    }

    /**
     * Enforces the island role an operation demands. Only players are subject to role rules;
     * Bypass actors skip them by construction. The required role is fixed by the calling
     * operation and never comes from outside, so a caller cannot lower its own bar.
     */
    private void requireRole(Actor actor, Connection connection, UUID islandUuid, RequiredRole required) throws SQLException {
        if (!(actor instanceof Actor.Player player)) {
            return;
        }

        String role = getIslandRole(connection, islandUuid, player.uuid()).orElseThrow(IslandDoesNotExistException::new);

        if (required == RequiredRole.OWNER && !"owner".equalsIgnoreCase(role)) {
            throw new NotIslandOwnerException();
        }
    }

    private Optional<String> getIslandRole(Connection connection, UUID islandUuid, UUID playerUuid) throws SQLException {
        return executeQuery(connection, "SELECT role FROM " + prefix + "island_players WHERE island_uuid = ? AND player_uuid = ? LIMIT 1", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, playerUuid.toString());
        }, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.ofNullable(rs.getString("role"));
        });
    }

    private Optional<String> getIslandDefaultHome(Connection connection, UUID islandUuid) throws SQLException {
        String sql = "SELECT h.home_location FROM " + prefix + "island_homes h " + "JOIN " + prefix + "island_players p ON p.player_uuid = h.player_uuid AND p.island_uuid = h.island_uuid " + "WHERE h.island_uuid = ? AND h.home_name = ? AND p.role = ? LIMIT 1";

        return executeQuery(connection, sql, stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, "default");
            stmt.setString(3, "owner");
        }, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.ofNullable(rs.getString("home_location"));
        });
    }

    private Optional<UUID> getIslandOwner(Connection connection, UUID islandUuid) throws SQLException {
        return executeQuery(connection, "SELECT player_uuid FROM " + prefix + "island_players WHERE island_uuid = ? AND role = ? LIMIT 1", stmt -> {
            stmt.setString(1, islandUuid.toString());
            stmt.setString(2, "owner");
        }, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(parseRequiredUuid(rs.getString("player_uuid"), "island_players.player_uuid"));
        });
    }

    private Optional<UUID> getPlayerIsland(Connection connection, UUID playerUuid) throws SQLException {
        return executeQuery(connection, "SELECT island_uuid FROM " + prefix + "island_players WHERE player_uuid = ? LIMIT 1", stmt -> {
            stmt.setString(1, playerUuid.toString());
        }, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(parseRequiredUuid(rs.getString("island_uuid"), "island_players.island_uuid"));
        });
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
