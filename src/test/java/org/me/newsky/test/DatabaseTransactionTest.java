package org.me.newsky.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.exceptions.CannotBanIslandPlayerException;
import org.me.newsky.exceptions.CannotRemoveOwnerException;
import org.me.newsky.exceptions.InviterNotMemberException;
import org.me.newsky.exceptions.IslandAlreadyExistException;
import org.me.newsky.exceptions.IslandPlayerAlreadyExistsException;
import org.me.newsky.exceptions.LocationNotInIslandException;
import org.me.newsky.exceptions.NotIslandOwnerException;
import org.me.newsky.exceptions.PlayerAlreadyBannedException;
import org.me.newsky.model.Actor;
import org.me.newsky.model.Island;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The transaction layer against a real MySQL, on a scratch database created and dropped by the
 * run. This is the pillar everything else leans on: role checks after the row lock, constraint
 * backstops racing writers, the single-owner invariant, and the row-lock serialization the whole
 * delta model assumes (commit order = the order deltas are derived in). Where a rule is enforced
 * by a race, the test races it - a version that merely calls methods in sequence would prove the
 * happy path and nothing else.
 * <p>
 * Needs a MySQL (args: host port user password); prints SKIPPED without one.
 */
public final class DatabaseTransactionTest {

    private static DatabaseHandler database;

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 3306;
        String user = args.length > 2 ? args[2] : "root";
        String password = args.length > 3 ? args[3] : "";

        String serverUrl = "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true";
        String scratch = "newsky_test_" + Long.toHexString(System.nanoTime());

        try (Connection probe = DriverManager.getConnection(serverUrl, user, password); Statement stmt = probe.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE " + scratch);
        } catch (Exception e) {
            System.out.println("DatabaseTransactionTest: SKIPPED (no MySQL reachable at " + host + ":" + port + " - " + e.getMessage() + ")");
            return;
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + scratch + "?useSSL=false&allowPublicKeyRetrieval=true");
        hikariConfig.setUsername(user);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(24);

        try (HikariDataSource dataSource = new HikariDataSource(hikariConfig)) {
            database = new DatabaseHandler(dataSource, "newsky_", "0,64,0,0,0", (message, error) -> {
            });

            createSeedsOwnerAndDefaultHome();
            racingCreatesForOneOwnerProduceExactlyOneIsland();
            addMemberSeedsHomeAndClearsBanAndCoop();
            addMemberRefusesNonMemberRoles();
            invitationVouchIsReVerifiedInTheJoinTransaction();
            membershipConflictsAreRefused();
            roleRulesRunInsideTheTransaction();
            racingOwnershipTransfersKeepExactlyOneOwner();
            racingTogglesNeverLoseAnUpdate();
            banRules();
            pointWritesEnforceMembershipByForeignKey();
            deleteCascadesEverything();
            System.out.println("DatabaseTransactionTest: ALL PASS");
        } finally {
            try (Connection cleanup = DriverManager.getConnection(serverUrl, user, password); Statement stmt = cleanup.createStatement()) {
                stmt.executeUpdate("DROP DATABASE IF EXISTS " + scratch);
            }
        }
    }

    private static void createSeedsOwnerAndDefaultHome() {
        UUID island = UUID.randomUUID();
        UUID owner = UUID.randomUUID();

        database.createIsland(island, owner);

        Island snapshot = database.getIslandSnapshot(island);
        Check.that(owner.equals(snapshot.getOwner()), "create seeds the owner row");
        Check.that(database.getIslandHomes(island, owner).containsKey("default"), "create seeds the owner's default home");
        Check.that(island.equals(database.getIslandUuid(owner).orElse(null)), "the owner resolves to their island");
    }

    // The unique key on player_uuid is the enforcement; the pre-check is a courtesy. Two creates
    // racing for the same owner must resolve to exactly one island, with the loser rolled back
    // whole - no orphaned islands row from the losing transaction.
    private static void racingCreatesForOneOwnerProduceExactlyOneIsland() throws Exception {
        UUID owner = UUID.randomUUID();
        int contenders = 8;

        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(contenders);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();

        for (int i = 0; i < contenders; i++) {
            pool.execute(() -> {
                try {
                    start.await();
                    database.createIsland(UUID.randomUUID(), owner);
                    wins.incrementAndGet();
                } catch (IslandAlreadyExistException e) {
                    refused.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        Check.that(done.await(30, TimeUnit.SECONDS), "racing creates finished");
        pool.shutdown();

        Check.that(wins.get() == 1, contenders + " racing creates for one owner produced exactly one island (wins=" + wins + ")");
        Check.that(refused.get() == contenders - 1, "every loser was refused with IslandAlreadyExistException (refused=" + refused + ")");
    }

    private static void addMemberSeedsHomeAndClearsBanAndCoop() {
        UUID island = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        Actor bypass = new Actor.Bypass("test");

        database.createIsland(island, owner);
        database.addBan(island, bypass, joiner);
        database.addCoop(island, bypass, joiner);

        database.addMember(island, joiner, "member", null);

        Island snapshot = database.getIslandSnapshot(island);
        Check.that(snapshot.getMembers().contains(joiner), "the joiner became a member");
        Check.that(!snapshot.getBans().contains(joiner), "joining cleared the joiner's ban row");
        Check.that(!snapshot.getCoops().contains(joiner), "joining cleared the joiner's coop row");
        Check.that(database.getIslandHomes(island, joiner).containsKey("default"), "joining seeded the joiner's default home");
    }

    private static void addMemberRefusesNonMemberRoles() {
        UUID island = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        database.createIsland(island, owner);

        boolean refused = false;
        try {
            database.addMember(island, UUID.randomUUID(), "owner", null);
        } catch (IllegalArgumentException e) {
            refused = true;
        }

        Check.that(refused, "granting the owner role through add-member is refused");
        Check.that(database.getIslandSnapshot(island).getMembers().isEmpty(), "the refused grant wrote nothing");
    }

    // The invitation is a vouch, and the voucher's membership is re-read inside the join
    // transaction: an invite from someone kicked before it was redeemed must die with their
    // membership.
    private static void invitationVouchIsReVerifiedInTheJoinTransaction() {
        UUID island = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID kickedInviter = UUID.randomUUID();
        UUID invitee = UUID.randomUUID();
        Actor ownerActor = new Actor.Player(owner);

        database.createIsland(island, owner);
        database.addMember(island, kickedInviter, "member", null);
        database.removeMember(island, ownerActor, kickedInviter);

        boolean refused = false;
        try {
            database.addMember(island, invitee, "member", kickedInviter);
        } catch (InviterNotMemberException e) {
            refused = true;
        }

        Check.that(refused, "an invitation vouched by a since-kicked member is refused at redemption");

        database.addMember(island, invitee, "member", owner);
        Check.that(database.getIslandSnapshot(island).getMembers().contains(invitee), "the same join vouched by a current member succeeds");
    }

    private static void membershipConflictsAreRefused() {
        UUID islandA = UUID.randomUUID();
        UUID islandB = UUID.randomUUID();
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        UUID member = UUID.randomUUID();

        database.createIsland(islandA, ownerA);
        database.createIsland(islandB, ownerB);
        database.addMember(islandA, member, "member", null);

        boolean already = false;
        try {
            database.addMember(islandA, member, "member", null);
        } catch (IslandPlayerAlreadyExistsException e) {
            already = true;
        }
        Check.that(already, "joining an island twice is refused");

        boolean elsewhere = false;
        try {
            database.addMember(islandB, member, "member", null);
        } catch (IslandAlreadyExistException e) {
            elsewhere = true;
        }
        Check.that(elsewhere, "joining a second island is refused");
    }

    private static void roleRulesRunInsideTheTransaction() {
        UUID island = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();

        database.createIsland(island, owner);
        database.addMember(island, member, "member", null);

        boolean ownerRequired = false;
        try {
            database.deleteIsland(island, new Actor.Player(member));
        } catch (NotIslandOwnerException e) {
            ownerRequired = true;
        }
        Check.that(ownerRequired, "a member cannot delete the island (OWNER rule)");

        boolean strangerRefused = false;
        try {
            database.toggleLock(island, new Actor.Player(UUID.randomUUID()));
        } catch (Exception e) {
            strangerRefused = true;
        }
        Check.that(strangerRefused, "a stranger cannot toggle the lock (MEMBER rule)");

        boolean memberMayToggle = database.toggleLock(island, new Actor.Player(member));
        Check.that(memberMayToggle, "a member may toggle the lock");
        database.toggleLock(island, new Actor.Player(member));

        boolean ownerCannotBeKicked = false;
        try {
            database.removeMember(island, new Actor.Player(member), owner);
        } catch (CannotRemoveOwnerException e) {
            ownerCannotBeKicked = true;
        }
        Check.that(ownerCannotBeKicked, "the owner cannot be removed as a member");
    }

    // The single-owner invariant under a race: the current owner hands off to B and C
    // concurrently. The row lock serializes the transfers, the role-conditional updates make the
    // second transfer fail its OWNER check, and exactly one owner row survives.
    private static void racingOwnershipTransfersKeepExactlyOneOwner() throws Exception {
        UUID island = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID candidateB = UUID.randomUUID();
        UUID candidateC = UUID.randomUUID();

        database.createIsland(island, owner);
        database.addMember(island, candidateB, "member", null);
        database.addMember(island, candidateC, "member", null);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger transferred = new AtomicInteger();

        for (UUID candidate : new UUID[]{candidateB, candidateC}) {
            pool.execute(() -> {
                try {
                    start.await();
                    database.setOwner(island, new Actor.Player(owner), candidate);
                    transferred.incrementAndGet();
                } catch (Exception ignored) {
                    // the loser: no longer the owner by the time its transaction ran
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        Check.that(done.await(30, TimeUnit.SECONDS), "racing transfers finished");
        pool.shutdown();

        Check.that(transferred.get() == 1, "exactly one of two racing ownership transfers succeeded (transferred=" + transferred + ")");

        Island snapshot = database.getIslandSnapshot(island);
        long ownerRows = database.getIslandPlayers(island).values().stream().filter("owner"::equalsIgnoreCase).count();
        Check.that(ownerRows == 1, "exactly one owner row survives the race (rows=" + ownerRows + ")");
        Check.that(snapshot.getMembers().contains(owner), "the old owner was demoted to member");
    }

    // The assumption the whole delta model rests on: the SELECT ... FOR UPDATE serializes
    // read-modify-write, so no toggle can base itself on a stale read. 200 racing flips must land
    // on the initial value - a single lost update breaks the parity.
    private static void racingTogglesNeverLoseAnUpdate() throws Exception {
        UUID island = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        database.createIsland(island, owner);

        int threads = 20;
        int togglesEach = 10;
        Actor actor = new Actor.Player(owner);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.execute(() -> {
                try {
                    start.await();
                    for (int i = 0; i < togglesEach; i++) {
                        database.toggleLock(island, actor);
                    }
                } catch (Exception e) {
                    throw new AssertionError("toggle failed mid-race", e);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        Check.that(done.await(60, TimeUnit.SECONDS), "racing toggles finished");
        pool.shutdown();

        boolean finalLock = database.getIslandCore(island).orElseThrow().lock();
        Check.that(!finalLock, threads * togglesEach + " racing toggles lost no update (even count returned to unlocked)");
    }

    private static void banRules() {
        UUID island = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Actor actor = new Actor.Player(owner);

        database.createIsland(island, owner);
        database.addMember(island, member, "member", null);

        boolean memberProtected = false;
        try {
            database.addBan(island, actor, member);
        } catch (CannotBanIslandPlayerException e) {
            memberProtected = true;
        }
        Check.that(memberProtected, "a member cannot be banned");

        database.addBan(island, actor, stranger);
        Check.that(database.getIslandBans(island).contains(stranger), "a stranger can be banned");

        boolean duplicate = false;
        try {
            database.addBan(island, actor, stranger);
        } catch (PlayerAlreadyBannedException e) {
            duplicate = true;
        }
        Check.that(duplicate, "banning twice is refused");
    }

    // The point-write pattern: membership is enforced by the (player_uuid, island_uuid) foreign
    // key, never by look-up-and-compare.
    private static void pointWritesEnforceMembershipByForeignKey() {
        UUID island = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();

        database.createIsland(island, owner);

        database.setHome(island, owner, "base", "1,2,3,0,0");
        Check.that(database.getIslandHomes(island, owner).containsKey("base"), "a member's home write lands");

        boolean refused = false;
        try {
            database.setHome(island, stranger, "sneak", "1,2,3,0,0");
        } catch (LocationNotInIslandException e) {
            refused = true;
        }
        Check.that(refused, "a non-member's home write is refused by the foreign key");
    }

    private static void deleteCascadesEverything() {
        UUID island = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Actor ownerActor = new Actor.Player(owner);

        database.createIsland(island, owner);
        database.addMember(island, member, "member", null);
        database.setWarp(island, owner, "shop", "5,64,5,0,0");
        database.addBan(island, ownerActor, UUID.randomUUID());
        database.addCoop(island, ownerActor, UUID.randomUUID());
        database.setIslandLevel(island, 42);

        database.deleteIsland(island, ownerActor);

        Check.that(database.getIslandSnapshot(island) == null, "the island row is gone");
        Check.that(database.getIslandPlayers(island).isEmpty(), "player rows cascaded");
        Check.that(database.getIslandHomes(island, owner).isEmpty(), "home rows cascaded");
        Check.that(database.getIslandWarps(island, owner).isEmpty(), "warp rows cascaded");
        Check.that(database.getIslandBans(island).isEmpty(), "ban rows cascaded");
        Check.that(database.getIslandCoops(island).isEmpty(), "coop rows cascaded");
        Check.that(database.getIslandUuid(owner).isEmpty(), "the owner no longer resolves to an island");
    }
}
