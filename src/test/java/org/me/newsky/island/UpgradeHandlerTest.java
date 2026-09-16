package org.me.newsky.island;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.economy.EconomyHandler;
import org.me.newsky.exceptions.*;
import org.me.newsky.model.Actor;
import org.me.newsky.model.Upgrade;
import org.me.newsky.network.IslandDistributor;
import org.me.newsky.thread.BukkitAsyncExecutor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UpgradeHandlerTest {
    private final UUID island = UUID.randomUUID();
    private final UUID buyer = UUID.randomUUID();
    private final Actor actor = new Actor.Player(buyer);
    private final NewSky plugin = mock(NewSky.class);
    private final ConfigHandler config = mock(ConfigHandler.class);
    private final DatabaseHandler database = mock(DatabaseHandler.class);
    private final IslandDistributor distributor = mock(IslandDistributor.class);
    private final Economy economy = mock(Economy.class);
    private final OfflinePlayer payer = mock(OfflinePlayer.class);
    private MockedStatic<Bukkit> bukkit;
    private UpgradeHandler handler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        BukkitAsyncExecutor executor = mock(BukkitAsyncExecutor.class);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; }).when(executor).execute(any());
        when(plugin.getBukkitAsyncExecutor()).thenReturn(executor);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.getMainThreadExecutor(plugin)).thenReturn(Runnable::run);
        RegisteredServiceProvider<Economy> registration = mock(RegisteredServiceProvider.class);
        when(registration.getProvider()).thenReturn(economy);
        ServicesManager services = mock(ServicesManager.class);
        when(services.getRegistration(Economy.class)).thenReturn(registration);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getServicesManager).thenReturn(services);
        bukkit.when(() -> Bukkit.getOfflinePlayer(buyer)).thenReturn(payer);

        when(config.isUpgrade("coop-limit")).thenReturn(true);
        when(config.getUpgradeLevels("coop-limit")).thenReturn(List.of(1, 2, 3));
        when(config.getUpgradeLimit("coop-limit", 1)).thenReturn(1);
        when(config.getUpgradeLimit("coop-limit", 2)).thenReturn(2);
        when(config.getUpgradeRequireLevel("coop-limit", 2)).thenReturn(100);
        when(config.getUpgradePrice("coop-limit", 2)).thenReturn(25000.0);
        when(database.getIslandUpgradeLevel(island, "coop-limit")).thenReturn(1);
        when(database.getIslandLevel(island)).thenReturn(150);
        when(economy.withdrawPlayer(payer, 25000.0)).thenReturn(response(EconomyResponse.ResponseType.SUCCESS));
        when(economy.depositPlayer(payer, 25000.0)).thenReturn(response(EconomyResponse.ResponseType.SUCCESS));
        when(economy.format(anyDouble())).thenAnswer(call -> "$" + call.<Double>getArgument(0));
        handler = new UpgradeHandler(plugin, config, database, distributor, new EconomyHandler(plugin));
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    void buyWithdrawsThenWritesTheNextLevel() {
        when(distributor.setUpgradeLevel(actor, island, "coop-limit", 1, 2)).thenReturn(CompletableFuture.completedFuture(null));

        assertEquals(2, handler.buyUpgrade(actor, island, buyer, "coop-limit").join());

        verify(economy).withdrawPlayer(payer, 25000.0);
        verify(distributor).setUpgradeLevel(actor, island, "coop-limit", 1, 2);
        verify(economy, never()).depositPlayer(any(OfflinePlayer.class), anyDouble());
    }

    @Test
    void failedWriteRefundsAndReportsTheCause() {
        when(distributor.setUpgradeLevel(actor, island, "coop-limit", 1, 2)).thenReturn(CompletableFuture.failedFuture(new UpgradeLevelChangedException()));

        assertFailure(UpgradeLevelChangedException.class, handler.buyUpgrade(actor, island, buyer, "coop-limit"));

        verify(economy).withdrawPlayer(payer, 25000.0);
        verify(economy).depositPlayer(payer, 25000.0);
    }

    @Test
    void gatesFailBeforeAnyMoneyMoves() {
        when(database.getIslandLevel(island)).thenReturn(99);
        assertFailure(UpgradeIslandLevelTooLowException.class, handler.buyUpgrade(actor, island, buyer, "coop-limit"));

        when(database.getIslandLevel(island)).thenReturn(150);
        when(database.getIslandUpgradeLevel(island, "coop-limit")).thenReturn(3);
        assertFailure(UpgradeMaxedException.class, handler.buyUpgrade(actor, island, buyer, "coop-limit"));

        assertFailure(UpgradeDoesNotExistException.class, handler.buyUpgrade(actor, island, buyer, "fly"));

        verifyNoInteractions(economy, distributor);
    }

    @Test
    void failedRefundLogsThePurchaseContextAndKeepsTheWriteFailure() {
        when(distributor.setUpgradeLevel(actor, island, "coop-limit", 1, 2)).thenReturn(CompletableFuture.failedFuture(new UpgradeLevelChangedException()));
        when(economy.depositPlayer(payer, 25000.0)).thenReturn(response(EconomyResponse.ResponseType.FAILURE));

        assertFailure(UpgradeLevelChangedException.class, handler.buyUpgrade(actor, island, buyer, "coop-limit"));
        verify(plugin).severe("Could not refund 25000.0 to " + buyer + " after a failed coop-limit upgrade purchase.");
    }

    @Test
    void insufficientFundsWritesNothing() {
        when(economy.withdrawPlayer(payer, 25000.0)).thenReturn(response(EconomyResponse.ResponseType.FAILURE));

        assertFailure(InsufficientFundsException.class, handler.buyUpgrade(actor, island, buyer, "coop-limit"));

        verifyNoInteractions(distributor);
        verify(economy, never()).depositPlayer(any(OfflinePlayer.class), anyDouble());
    }

    @Test
    void adminSetValidatesTheLevelAndCompareAndSetsFromTheCurrentOne() {
        when(distributor.setUpgradeLevel(any(), any(), anyString(), anyInt(), anyInt())).thenReturn(CompletableFuture.completedFuture(null));
        Actor admin = new Actor.Bypass("test");

        handler.setUpgradeLevel(admin, island, "coop-limit", 3).join();
        verify(distributor).setUpgradeLevel(admin, island, "coop-limit", 1, 3);

        assertFailure(UpgradeLevelDoesNotExistException.class, handler.setUpgradeLevel(admin, island, "coop-limit", 4));
        assertFailure(UpgradeDoesNotExistException.class, handler.setUpgradeLevel(admin, island, "fly", 1));
        verifyNoMoreInteractions(distributor);
    }

    @Test
    void detailsCombineIslandStateWithTheViewersBalance() {
        when(economy.getBalance(payer)).thenReturn(30000.0);
        Upgrade details = handler.getUpgradeDetails(island, buyer, "coop-limit").join();
        assertEquals(new Upgrade(1, 1, false, 2, 2, 100, "$25000.0", true, 150, "$30000.0"), details);

        when(economy.getBalance(payer)).thenReturn(10.0);
        assertFalse(handler.getUpgradeDetails(island, buyer, "coop-limit").join().available());

        when(database.getIslandUpgradeLevel(island, "coop-limit")).thenReturn(3);
        when(config.getUpgradeLimit("coop-limit", 3)).thenReturn(3);
        assertTrue(handler.getUpgradeDetails(island, buyer, "coop-limit").join().maxed());
        verifyNoInteractions(distributor);
    }

    private static EconomyResponse response(EconomyResponse.ResponseType type) {
        return new EconomyResponse(25000.0, 0, type, null);
    }

    private static void assertFailure(Class<? extends Throwable> expected, CompletableFuture<?> future) {
        CompletionException error = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(expected, error.getCause());
    }
}
