# Performance

Two kinds of measurement, both reproducible from `raktsetu-bench`:

- **Engine benchmarks** (JMH): the three core algorithms on generated networks of 50 to 1,000 blood centres.
- **HTTP load test**: many signed-in hospital staff using the real server at once, through its public API.

Everything was measured on one laptop (8 cores, Windows 11, Java 25). The load test ran its load generator on the
same machine as the server, with the embedded H2 database, so the absolute numbers are a floor, not a ceiling;
the before/after comparisons are like for like.

## Engine: 5× to 66× faster at 1,000 centres

Each network has two hospitals and 300 units per centre, so 1,000 centres means 3,000 facilities and 300,000 units.
Routes exist only between facilities within 100 km, as a real road network would be looked up.

| Operation (1,000 centres) | Before | After | Speed-up |
|---|---:|---:|---:|
| Serve an emergency request | 8.50 ms | 0.13 ms | **66×** |
| Find all units at risk of expiring | 421 ms | 76 ms | **5.5×** |
| Plan transfers for the whole network | 31.6 s | 0.67 s | **47×** |

Average time per operation (JMH, 3 × 1 s warm-up, 5 × 1 s measurement, 1 fork):

| Centres | Allocate before | Allocate after | Risk before | Risk after | Plan before | Plan after |
|---:|---:|---:|---:|---:|---:|---:|
| 50 | 0.21 ms | 0.03 ms | 18 ms | 2.9 ms | 0.32 s | 0.016 s |
| 200 | 1.17 ms | 0.06 ms | 68 ms | 15 ms | 3.54 s | 0.091 s |
| 500 | 4.39 ms | 0.08 ms | 176 ms | 37 ms | 6.98 s | 0.26 s |
| 1,000 | 8.50 ms | 0.13 ms | 421 ms | 76 ms | 31.6 s | 0.67 s |

Planning used to grow with the square of the network (every at-risk unit was checked against every centre); it now
grows roughly linearly. Raw results: [`engine-before.json`](engine-before.json), [`engine-after.json`](engine-after.json).

### What changed, found by profiling (JMH's stack profiler)

1. **A unit's expiry date was recomputed on every call** (collection date + shelf life), and sorting stock calls it
   thousands of times. It is now worked out once and remembered (`BloodComponent.getExpiryDate`).
2. **Stock is kept in a red-black tree instead of a binary heap** (`Inventory`: `TreeSet` instead of
   `PriorityQueue`). A heap only keeps its smallest element in order, so every "units in expiry order" view copied
   and sorted the whole queue; a tree is always in order, and removing any unit is O(log n) instead of O(n).
3. **`removeExpired` returns at once when nothing can have expired.** Each inventory remembers a lower bound on its
   earliest expiry. Before, every request scanned every shelf in the network first.
4. **A neighbour index** (`BloodNetwork.storingNeighbours`): for each facility, the centres it has a route to, built
   once from the route table. Allocation and planning look only at nearby centres, not the whole network.
5. **Per-plan lookup tables** in the planner: usage, stock and the list of possible destinations are worked out once
   per centre, component and blood group, not once per at-risk unit.

Every change keeps the results identical: the full test suite passes, and the 30-day simulation gives exactly the
same output as before for 5 seeds and all 3 policies (for seed 42, wastage still falls from 105 to 68 units).

## Server: 4.4× the throughput, p99 4.3× lower

20 centres, 40 hospitals and one staff account per hospital, created through the API as a real administrator would
(including each person's one-time password and first password change). Each level runs for 30 s after a 15 s
warm-up, closed loop with no think time. The mix is what hospital staff do: 35% overview, 20% their requests, 15%
network stock, 15% notifications, and 15% raising an emergency request and then cancelling it.

| Users | Before: req/s | Before: p99 | After: req/s | After: p99 | Errors |
|---:|---:|---:|---:|---:|---:|
| 1 | 83 | 55 ms | 133 | 22 ms | 0 |
| 10 | 116 | 196 ms | 413 | 53 ms | 0 |
| 50 | 100 | 1,767 ms | 428 | 193 ms | 0 |
| 100 | 86 | 2,072 ms | 432 | 414 ms | 0 |
| 200 | 96 | 3,160 ms | 426 | 736 ms | 0 |

Full reports: [`load-before.md`](load-before.md) → [`load-step1-audit.md`](load-step1-audit.md) →
[`load-step2-rwlock.md`](load-step2-rwlock.md) → [`load-after.md`](load-after.md).

### What changed

1. **The overview re-verified the whole audit log on every page load** (a SHA-256 hash per entry, under the lock),
   so it slowed down as the log grew. `AuditIntegrity` now checks only entries written since the last check,
   continuing the hash chain; the whole chain is still re-checked every five minutes and on the audit page.
   (≈100 → 230 req/s.)
2. **One lock for everything became a readers-writer lock** (`NetworkLock`, a fair `ReentrantReadWriteLock`).
   Most traffic only reads, and reads now run in parallel; changes still run one at a time. `ConcurrencyTest` runs
   16 threads raising, cancelling and reading at once and checks stock and the audit log afterwards.
   (230 → 380 req/s.)
3. **An N+1 query**: listing 50 requests ran 51 queries (one per request for its units). It now runs 2.
   (380 → 430 req/s; single-user median 8.0 → 6.3 ms.)

### Where the limit is now, and what would raise it

Throughput now levels off at about 430 req/s because each change holds the write lock while its database
transaction commits. The next steps, in order of payoff:

1. Commit to the database outside the write lock (apply in memory, release, then persist with a version check).
2. Run on PostgreSQL on its own machine instead of the embedded H2 file database.
3. Partition by region: one network (and one lock) per state or district, since blood rarely moves between them.

## Reproducing

```bash
./mvnw -pl raktsetu-bench -am package -DskipTests
java -jar raktsetu-bench/target/raktsetu-bench.jar engine -rf json -rff engine.json
# Start a server with an empty data folder on port 8090, then:
java -jar raktsetu-bench/target/raktsetu-bench.jar load --url http://localhost:8090 --levels 1,10,50,100,200 --out load.md
```
