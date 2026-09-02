# NewSky — Architecture Rules

## The three-layer rule: where every check lives

Every check belongs to exactly one category, decided by **where the state it guards lives**:

### 1. Input validation — earliest layer, zero I/O
Name→UUID resolution, home/warp name legality, coordinate/world parsing.
Commands resolve names and present errors; handlers validate formats
(`IslandUtils.isLegalPointName`, `IslandUtils.parseIslandUuid`).

### 2. Shared-state invariants & authorization — ONLY inside a DatabaseHandler transaction
Membership, ownership, already-banned/cooped, has-island. This state lives in MySQL and
changes concurrently from other servers, so any check outside the transaction is stale the
moment it is read. Handlers and commands must NOT pre-check these; the DB throws the domain
exception and it propagates cross-server by class name (`CrossServerMessenger.restoreRemoteException`,
so every domain exception needs a no-arg constructor).

- Every island write takes an `Actor`. `requireRole` runs **after** the island row lock
  (`lockIsland` / `SELECT ... FOR UPDATE`) — never before, or the role is read unlocked.
- The required role is hard-coded per DB method, never a parameter — a caller cannot lower
  its own bar. OWNER: setOwner, deleteIsland. MEMBER: everything else island-scoped.
- `Actor.Bypass` (operators, console, internal tasks) skips role rules by construction; its
  source string is for logging only. A missing actor in a cross-server payload is an error
  (`Actor.fromJson` throws), never a default.
- InnoDB row locks on the `islands` row are the cluster's mutual exclusion for island data.
  Do not add Redis locks on top: leases expire mid-operation, and two lock systems have no
  shared deadlock detection.

### 3. Non-DB state & non-DB side effects — fail-fast filter + fail-closed destination
Online status (Redis), pending invites (Redis), teleports/kicks (worlds). These cannot be
transactional with the database, so handler checks are **courtesy filters** for good error
messages. Correctness comes from the destination: `IslandAccessListener` re-checks ban/lock
on every join and world change. It is fine for these checks to be stale; it is a bug for
correctness to depend on them.

For world-side effects, the guard runs **at the point of effect**: on the island's host
server, at the moment the action executes (see `IslandOperator.expelPlayer`, and the membership
read inside the lock-toggle kick). A caller-side check alone leaves a cross-server-hop-sized
window; the destination guard shrinks it to microseconds, and whatever slips through must be
self-healing (a wrongly bounced player walks back; no state changed).

**Allowed exception:** a multi-step UX flow (currently only /is delete's confirm stages) may
add an advisory pre-check in the command layer so players are not rejected after the final
step. It is a courtesy, not a defense — the transaction still enforces.

## Corollaries

- `islandUuid` is an **address, not a capability**. Commands may resolve it early; staleness
  is harmless because the actor's relationship to that island is re-verified under the lock.
- **Point-write pattern** (home/warp set): derive the island from the world name
  (`parseIslandUuid`), let the `(player_uuid, island_uuid)` foreign key enforce membership.
  Never look up and compare.
- **Multi-store ordering**: database rows first (auth check in the same transaction), then
  world/filesystem. An orphaned world file is unreachable garbage; an island row pointing at
  a deleted world is a live bug. (`deleteIsland` follows this.)
- **Island load placement**: claimed atomically via `HSETNX` before loading
  (`IslandRegistry.claimIslandLoadedServer`); only the claimer releases on failure;
  `IslandOperator.loadIsland` de-duplicates concurrent loads locally.
- **`IslandSnapshot.get()` never returns null because a refresh is in flight** — listeners
  fail closed on null, so stale must beat absent on hot paths. Loads for one island are
  serialized so an older read can never overwrite a newer one.
- The read path for listeners (protection/PvP/access) is the in-memory snapshot only.
  Never put a SQL query on a per-block or per-hit path.

## Future read cache (planned, not yet built)

DB-first demoted caching from a correctness component to a display optimization: every
invariant and authorization lives in the transaction, so a stale cached read can no longer
corrupt anything. When a machine-rate reader appears (PlaceholderAPI/scoreboard, GUIs,
island-top displays), add the cache under three rules:

1. Cache only the display/suggestion layer. A cached value must never feed a write decision —
   writes take their truth inside the transaction, always.
2. Use short TTLs (a few seconds), never cross-server invalidation. Invalidation machinery is
   the road back to the old DataCache/pub-sub complexity this refactor removed.
3. Per-event paths (block/PvP/world listeners) stay on the IslandSnapshot pattern; do not
   route them through a command-layer cache.

Command-rate reads (single indexed queries, async) do not need caching — measure before adding any.

## Build verification

`mvn compile` fails on this machine: the AdvancedSlimePaper 4.2.0-SNAPSHOT jars are class-file
major 69 (Java 25) while the local JDK is 21. Verify with the stubs instead:

```
mvn -q -o dependency:build-classpath -Dmdep.outputFile=target/cp.txt -Dmdep.includeScope=compile
# strip the two infernalsuite jars from the classpath file, then:
find src/main/java target/codex-stubs -name "*.java" > target/srcs.txt
javac -nowarn -d target/verify -cp "@target/cp_nostub.txt" @target/srcs.txt
```

## Parked features

`temporaryremoveupgrade.txt` documents the deliberately removed Upgrade & Limit systems
(planned to be rebuilt). The five `Upgrade*`/`InsufficientFunds` exceptions are kept on
purpose — do not delete them as dead code.
