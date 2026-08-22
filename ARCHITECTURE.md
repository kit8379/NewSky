# NewSky code guide

This document is the shortest path into the plugin. Start with the normal request flow below and
only open the cluster classes when you are changing cross-server behaviour.

## Normal request flow

Most player features follow the same path:

```text
Command
  -> NewSkyAPI / PlayerActions / AdminActions
  -> island handler (CoreHandler, PlayerHandler, BanHandler, ...)
  -> IslandDistributor
  -> local IslandOperator OR CrossServerMessenger -> remote IslandOperator
  -> DatabaseHandler
  -> IslandSnapshot update (only while the island is hosted here)
```

For example, an island ban starts in `PlayerBanCommand`, passes through `BanHandler`, and is routed
by `IslandDistributor`. `IslandOperator.addBan` owns the actual write sequence. The database
transaction validates the actor and commits the ban; the returned state version is then applied to
the hosted snapshot.

## Main classes

| Class | Responsibility |
| --- | --- |
| `NewSky` | Creates components, registers remote actions/listeners/commands, starts and stops them. |
| `IslandDistributor` | Chooses the server for a request and retries a write if the island moved. |
| `IslandOperator` | Runs island lifecycle and data writes on the selected server. |
| `DatabaseHandler` | Public MySQL reads and transaction-protected writes. |
| `DatabaseSchema` | Table creation and online column migrations only. |
| `IslandSnapshot` | In-memory enforcement state for islands hosted on this server. |
| `WorldHandler` | ASP/Bukkit world creation, loading, saving, unloading and deletion. |
| `IslandRegistry` | Redis island host claims and write-authority decisions. |
| `ServerRegistry` | Server incarnation heartbeat and dead-server cleanup. |
| `OnlinePlayerRegistry` | Cluster online players and durable coop cleanup queue. |
| `CrossServerMessenger` | At-most-once-per-boot Redis stream request/response transport. |

The command classes and island handlers are intentionally thin. Validation that must remain true
at commit time belongs in `DatabaseHandler`, not only in a command or API pre-check.

## Island lifecycle

`IslandOperator` serializes operations by island UUID. Different islands still run in parallel.

### Create

```text
claim host -> create provisioning rows -> seed snapshot -> create world
           -> verify claim -> mark ready -> verify claim
```

If creation fails, cleanup uses the MySQL write epoch. It cannot delete rows already rebound to a
newer server incarnation.

### Load

```text
check local Bukkit world -> claim/confirm host -> bind MySQL write epoch
                         -> seed snapshot -> load/resume world -> verify claim
```

Concurrent loads on one server share one future. Redis chooses one host across servers.

### Unload

The live host saves and unloads normally. A process that has lost its claim only removes its local
Bukkit world and does not save it.

### Data write

```text
acquire write authority -> bind write epoch -> verify live claim
                        -> commit transaction -> apply exact snapshot version
                        -> release temporary claim when island is not loaded
```

## Consistency rules

These rules are correctness requirements, not optional optimisations:

1. A Redis host claim stores `server instance UUID + server name`. A restarted server cannot
   confirm or delete the previous process's claim.
2. Every hosted MySQL state write checks the current `write_epoch` while holding the island row
   lock. An old process cannot commit island metadata after takeover.
3. A write returns the `state_version` committed in the same transaction. `IslandSnapshot` applies
   only the next version; a gap causes one consistent database read.
4. Create/load/unload/delete and state writes share the same per-island operation chain. Their
   commit order and in-memory order therefore cannot cross.

Do not replace the Redis Lua scripts with separate Redis commands. Their read/check/write steps
must remain atomic.

## Thread rules

- Bukkit world/player access runs on the main thread.
- Redis and MySQL calls run on `BukkitAsyncExecutor`.
- A `CompletableFuture` represents completion of the real effect. Do not complete a teleport or
  remote write future before the underlying operation has completed.
- Player join/quit registry updates are serialized per player; island operations are serialized
  per island.

## Making a change

- New command or API surface: command -> actions/API -> an existing island handler.
- New island state field: schema/migration -> transaction -> `Island` immutable delta -> snapshot
  tests -> listener enforcement.
- New cross-server operation: action constant -> distributor payload -> handler registration in
  `NewSky` -> operator method -> message test.
- New lifecycle behaviour: keep it inside the per-island `IslandOperator.serialized` path.
- New Redis compound operation: use one Lua script and add the exact script to
  `RedisClaimScriptsTest`.

## ASP storage contract

NewSky is the only component allowed to initiate ASP world saves, unloads and deletes. Every
normal NewSky path checks the island claim before writing through ASP; a server that loses its
cluster lease self-fences and unloads local worlds with `save=false`.

The ASP world blob does not have, and is not expected to gain, a storage-level epoch. NewSky does
not fork or replace ASP's MySQL loader. This is a deliberate system boundary, not a pending
correctness feature. The supported failure model therefore assumes a JVM does not pause after its
final authority check, remain alive beyond lease takeover, and later resume inside the same ASP
save call. Protecting that out-of-model sequence would require changing the ASP storage API.
