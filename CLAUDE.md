# NewSky — Architecture Rules

For the current code map, lifecycle diagrams, thread rules and the ASP storage contract,
read `ARCHITECTURE.md` first. This file keeps the deeper authorization rationale and test notes.

## The API shape rule: writes enter through a handle, reads live on the root

`NewSkyAPI` exposes exactly one way to write, with no exceptions and no footnotes:

- **`api.player(uuid)`** — act *as that player*. SELF is structural, not checked: there is no
  parameter to name anyone else as actor or subject. Island-scoped operations resolve **the
  player's own island internally** (a player has exactly one — unique key on `player_uuid`), so
  targeting another island is unrepresentable and the resolution is fresher than any caller's.
  Operator-only operations do not exist on this handle.
- **`api.admin(sender)`** — act as an operator on arbitrary targets. The sender's name rides
  along as `Actor.Bypass` into logs and cross-server payloads; that name is the only
  accountability a bypass has, which is why it is required.
- **Reads take no identity** — island state is not secret and a read changes nothing.

Internal machinery (quit cleanup, schedulers, join listeners) does **not** pass through the API:
it calls its handler directly (`CoopHandler.removeAllCoops`, `UuidHandler`,
`LevelHandler`). If an operation has no external caller, it does not belong on the API.

Underneath, every handler write still takes an `Actor` first, and the rules live where the state
they read lives — this split is not stylistic, it follows the three-layer rule below:

| Rule | Meaning | Enforced |
|---|---|---|
| OWNER | island owner only | in the write transaction, after the row lock |
| MEMBER | any island member | in the write transaction, after the row lock |
| SELF | actor must be the player acted on | structurally by `PlayerActions`; `Actor.requireSelf` in handlers as backstop |
| BYPASS | operator, console | structurally by handle split; `Actor.requireBypass` in handlers as backstop |

SELF and BYPASS can live at the boundary precisely *because* they are identity comparisons with
zero I/O: they read no shared state and therefore cannot be stale. Roles must stay in the
transaction for exactly the opposite reason.

When adding an operation: player-facing → `PlayerActions` (own island resolved inside);
operator-facing → `AdminActions`; internal-only → handler only, never the API. The handler
method takes `Actor` first and its javadoc names the rule. Never add a write whose actor is
accepted and ignored — a parameter that looks enforced but is not is worse than none, because
callers stop checking.

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
  (`IslandRegistry.claimHost`); the host re-verifies the claim at the point of
  load (`claimOrConfirmHost`) so a stale or replayed load request cannot put the
  world on a second server. A claim is released **only by its holder, inside that island's
  local lifecycle chain**, and always by compare-and-delete — never an unconditional `HDEL`,
  and never by a caller reacting to a timeout (the host may still be loading). A dangling
  claim self-heals: the next teleport routed to the host re-loads the island at the point of
  effect. `IslandOperator` serializes create/load/unload/delete per island locally and
  de-duplicates concurrent loads.
- **The snapshot is authoritative memory, not a polling cache**: the database is read at world
  load and every normal write applies its own exact versioned delta (`Island.withX`) before the
  operation completes. Deltas queue behind an in-flight seed; the unload generation stops a stale
  seed from resurrecting state (the ABA case). Duplicate/late versions are ignored. A version gap
  is treated as an abnormal missed/out-of-band update and triggers one consistent database read.
- **Every state write runs under write authority** (`IslandRegistry.acquireWriteAuthority`,
  atomic in Redis): HOST executes and applies its delta; CLAIMED takes a temporary claim for an
  unloaded island inside the island's chain slot and releases it there, so a concurrent load
  queues behind the write and seeds committed state — the write-versus-load race is
  unrepresentable, not detected; OTHER refuses with `WrongIslandHostException` and the caller
  re-resolves and follows the island (bounded attempts). Never execute a state write beside
  someone else's claim: the host's memory would never hear about it.
- **`IslandSnapshot.get()` returning null means the island is not ready on this server**. Listeners
  fail closed on null; they never query MySQL from a hot event path.
- The read path for listeners (protection/PvP/access) is the in-memory snapshot only.
  Never put a SQL query on a per-block or per-hit path.
- **Cross-server requests are ephemeral, not durable**: a server clears its own inbox on
  startup and drops requests older than the requester's timeout (nobody is waiting, and
  replaying non-idempotent operations like toggles or loads against moved-on state is
  harmful). Never design a flow that relies on an inbox message surviving a restart.
- **Lifecycle ordering**: on startup the heartbeat's claim sweep runs BEFORE the messenger
  opens intake; on shutdown the messenger closes intake FIRST and the sweep runs after the
  worlds are gone. Either inversion lets a load slip through and lose its claim to the sweep.
- **Delta-order equals commit-order**: writes for one island run inside its
  `KeyedSequentialExecutor` chain slot, so two writes cannot commit in one order and apply in
  the other. The trade for dropping the self-correcting re-read is that a wrong delta persists
  until unload — which is why `Island.withX` must mirror its transaction exactly and
  `IslandSnapshotTest.deltaRunMatchesReferenceModel` pins the equivalence.
- **Teleporting into an island is a visit, not an edit**: the teleport guard mirrors
  IslandAccessListener (ban/lock/boundary), never the build rules — build rules there would
  break public warps for every visitor. Roles stay hard-coded: add-member only ever grants
  "member" (ownership moves via setOwner alone), and an invitation is a vouch that is
  re-verified against the inviter's membership inside the add-member transaction.

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

AdvancedSlimePaper is pinned to the last Java 21-compatible snapshot in `pom.xml`. The normal
verification command is:

```powershell
mvn -q -DskipTests package
```

## Tests

`src/test/java` holds plain-main test classes rather than JUnit tests. `mvn test` compiles them but
reports zero executed tests, so it must not be treated as test success. Run their `main` methods
with `target/test-classes`, `target/classes` and Maven's test dependency classpath. They cover the
concurrency primitives the cluster safety rests on:

- `KeyedSequentialExecutorTest` — per-key ordering, no-overlap, failure isolation, cross-key
  parallelism, chain cleanup (backs island lifecycle + online-player ordering).
- `CrossServerMessageTest` — wire-format round trip, timestamp for the stale-request filter,
  error identity for cross-server exception restore.
- `RedisClaimScriptsTest` — runs the exact deployed Lua scripts (referenced from the production
  constants, never copied) against a real Redis at 127.0.0.1:6379 (args: host port), plus the
  concurrent-HSETNX placement race. Prints SKIPPED when no Redis is reachable — run it with the
  dev Redis up.
- `RedisEdgeCaseTest` — reaper edge cases (dead/live/resurrected/re-claimed holders, heartbeat
  TTL expiry flipping the verdict), online reap sparing players who rejoined elsewhere, invite
  set-if-absent and double-accept races, inbox MAXLEN cap. Same Redis gating as above.
- `RedisPlacementChaosTest` — model-based chaos run of the placement protocol (args: host port
  [ops]): simulated servers load/unload/crash/restart/reap/replay against real Redis using the
  production Lua, with deterministic detection that no island is ever loaded on two servers at
  once, plus coverage assertions so a run that never crashed or refused anything cannot pass.
- `ActorRulesTest` — no Redis needed. The SELF and BYPASS identity rules, plus the cross-server
  round trip: a restored player actor must not decay into a bypass, and a payload with no actor
  must be rejected rather than defaulted.
- `IslandSnapshotTest` — no Redis needed. The delta model's rules: concurrent seeds coalesce
  into one read, deltas queue behind an in-flight seed and apply in submission order, a delta
  for an unhosted island touches nothing, the ABA/unload-generation guard, failed seeds keep
  the previous snapshot, a 2000-delta random run must equal a reference model field-for-field,
  and a monotonic-version property test under churn. Injects a reader through the
  `IslandSnapshot(Executor, Reader, ErrorSink)` seam - the production constructor delegates to it.
- `SnapshotPropagationTest` — the write-versus-load race under write authority, against real
  Redis with the production Lua (args: host port [iterations]). Ends with a negative control:
  with no write authority the same race must strand a stale host, so the test cannot pass for
  the wrong reason.
- `DatabaseTransactionTest` — the transaction layer against a real MySQL (args: host port user
  password), on a scratch database created and dropped by the run; prints SKIPPED without one.
  Races the rules that are enforced by racing: N creates for one owner yield exactly one island,
  two concurrent ownership transfers keep exactly one owner row, 200 racing lock toggles lose no
  update (the row-lock serialization the delta model assumes). Plus role rules in-transaction,
  invitation vouch re-verification, add-member side effects, ban rules, the point-write foreign
  key, and delete cascades. Uses the `DatabaseHandler(HikariDataSource, prefix, spawnLocation,
  ErrorSink)` seam - the production constructor delegates to it.
- `ClusterFencingExtremeTest` — real Redis + MySQL takeover tests: duplicate server-name startup,
  A→B→C claim handoff, old-epoch write/delete cleanup rejection, operation-ID replay and row-lock
  ordering during epoch takeover.
- `RedisStreamDeliveryExtremeTest` — boot-scoped stream cursor behaviour, inbox retirement and
  concurrent producers with exact loss/duplicate counts.
- `TeleportHandlerTest` — stale teleport completions cannot remove a newer request; failed or
  blocked teleports remain retryable.

```powershell
mvn -q test dependency:build-classpath "-Dmdep.outputFile=target/test-cp.txt" "-Dmdep.includeScope=test"
$dependencyCp = Get-Content -Raw target/test-cp.txt
$testCp = "target/test-classes;target/classes;$dependencyCp"
java -cp $testCp org.me.newsky.test.KeyedSequentialExecutorTest
java -cp $testCp org.me.newsky.test.CrossServerMessageTest
java -cp $testCp org.me.newsky.test.RedisClaimScriptsTest 192.168.1.49 6379
```

## Parked features

`temporaryremoveupgrade.txt` documents the deliberately removed Upgrade & Limit systems
(planned to be rebuilt). The five `Upgrade*`/`InsufficientFunds` exceptions are kept on
purpose — do not delete them as dead code.
