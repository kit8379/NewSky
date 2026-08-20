# NewSky — Architecture Rules

## The API shape rule: Actor first on every write

`NewSkyAPI` has exactly two halves, and the signature says which one you are looking at:

- **Every write takes an `Actor` as its first parameter.** No exceptions to remember — if it
  changes island or player state, the actor is there, and its javadoc names the one rule it
  enforces.
- **Every read takes no `Actor`.** Island state is not secret and a read changes nothing.

Only two writes are Actor-less, and both say why at the declaration: `calIslandLevel` and
`updatePlayerUuid` recompute derived/system data that nobody's rights decide.

The four rules, and where each is enforced — the split is not stylistic, it follows the
three-layer rule below:

| Rule | Meaning | Enforced |
|---|---|---|
| OWNER | island owner only | in the write transaction, after the row lock |
| MEMBER | any island member | in the write transaction, after the row lock |
| SELF | actor must be the player acted on | `Actor.requireSelf`, at the API boundary |
| BYPASS | operator, console, internal task | `Actor.requireBypass`, at the API boundary |

SELF and BYPASS belong at the boundary precisely *because* they are identity comparisons with
zero I/O: unlike a role, they read no shared state and therefore cannot be stale. Roles must
stay in the transaction for exactly the opposite reason.

When adding an API method: pick the rule first, put `Actor` first, name the rule in the javadoc.
Never add a write whose actor is accepted and ignored — a parameter that looks enforced but is
not is worse than none, because callers stop checking.

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
  (`IslandRegistry.claimIslandLoadedServer`); the host re-verifies the claim at the point of
  load (`claimOrConfirmIslandLoadedServer`) so a stale or replayed load request cannot put the
  world on a second server. A claim is released **only by its holder, inside that island's
  local lifecycle chain**, and always by compare-and-delete — never an unconditional `HDEL`,
  and never by a caller reacting to a timeout (the host may still be loading). A dangling
  claim self-heals: the next teleport routed to the host re-loads the island at the point of
  effect. `IslandOperator` serializes create/load/unload/delete per island locally and
  de-duplicates concurrent loads.
- **`IslandSnapshot.get()` never returns null because a refresh is in flight** — listeners
  fail closed on null, so stale must beat absent on hot paths. Loads for one island are
  serialized so an older read can never overwrite a newer one.
- The read path for listeners (protection/PvP/access) is the in-memory snapshot only.
  Never put a SQL query on a per-block or per-hit path.
- **Cross-server requests are ephemeral, not durable**: a server clears its own inbox on
  startup and drops requests older than the requester's timeout (nobody is waiting, and
  replaying non-idempotent operations like toggles or loads against moved-on state is
  harmful). Never design a flow that relies on an inbox message surviving a restart.

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

## Tests

`src/test/java` holds plain-main test classes (no JUnit — `mvn test` cannot run here for the
same ASP-jar reason). They cover the concurrency primitives the cluster safety rests on:

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
- `IslandSnapshotTest` — no Redis needed. Snapshot cache ordering: reads never overlap, an older
  read never overwrites a newer one (including the ABA case across unload+reload), failed reads
  keep the previous snapshot, deleted islands drop out, `reload` is a no-op when not hosting, and
  a monotonic-version property test under concurrent churn. Injects a reader through the
  `IslandSnapshot(Executor, Reader, ErrorSink)` seam - the production constructor delegates to it.
- `SnapshotPropagationTest` — the local-write vs concurrent-load routing race against real Redis
  (args: host port [iterations]), modeling the writer's post-commit re-check and the host's
  serialized snapshot loads. Ends with a negative control: with the re-check disabled the same
  race must strand a stale host, so the test cannot pass for the wrong reason.

```
find src/test/java -name "*.java" > target/test-srcs.txt
CP=$(cat target/cp_nostub.txt)
javac -nowarn -d target/verify-tests -cp "target/verify;$CP" @target/test-srcs.txt
java -cp "target/verify-tests;target/verify;$CP" org.me.newsky.test.KeyedSequentialExecutorTest
java -cp "target/verify-tests;target/verify;$CP" org.me.newsky.test.CrossServerMessageTest
java -cp "target/verify-tests;target/verify;$CP" org.me.newsky.test.RedisClaimScriptsTest
```

## Parked features

`temporaryremoveupgrade.txt` documents the deliberately removed Upgrade & Limit systems
(planned to be rebuilt). The five `Upgrade*`/`InsufficientFunds` exceptions are kept on
purpose — do not delete them as dead code.
