package org.me.newsky.test;

import org.bukkit.Location;
import org.me.newsky.teleport.TeleportHandler;

import java.util.UUID;

/** Token/ack semantics for pending proxy-hop teleports. */
public final class TeleportHandlerTest {

    public static void main(String[] args) {
        TeleportHandler handler = new TeleportHandler();
        UUID player = UUID.randomUUID();

        handler.addPendingTeleport(player, new Location(null, 1, 2, 3));
        TeleportHandler.PendingTeleportTicket old = handler.claimPendingTeleport(player);

        handler.addPendingTeleport(player, new Location(null, 4, 5, 6));
        handler.completePendingTeleport(player, old, true);
        TeleportHandler.PendingTeleportTicket current = handler.claimPendingTeleport(player);
        Check.that(current != null && current.location().getX() == 4,
                "an old teleport completion cannot erase a newer request");

        handler.completePendingTeleport(player, current, false);
        Check.that(handler.claimPendingTeleport(player) != null,
                "a blocked/failed teleport remains available for retry");

        handler.completePendingTeleport(player, current, true);
        Check.that(handler.claimPendingTeleport(player) == null,
                "only a successful matching teleport consumes the request");
        System.out.println("TeleportHandlerTest: ALL PASS");
    }
}
