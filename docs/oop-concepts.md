# Object-oriented design in RaktSetu

Where each OOP concept appears in the code, for the project report and the viva. Paths are relative to each
module's `src/main/java/com/raktsetu/`.

## The four pillars

| Concept | Where | What to say |
|---|---|---|
| **Abstraction** | `domain/model/BloodComponent` (abstract), `domain/model/Facility` (abstract) | A unit of blood is abstract: code works with "a component" and never needs to know if it is platelets or plasma. |
| **Encapsulation** | `domain/model/Inventory`; `server/security/SlidingWindowAttemptPolicy` | Stock can only change through `Inventory`'s methods, which keep each component and group in a `TreeSet` ordered by expiry. The lockout's failure queues are private and guarded by `synchronized`; callers only ask "is this key locked?". |
| **Inheritance** | `WholeBlood`, `RedCellConcentrate`, `Platelets`, `FreshFrozenPlasma` extend `BloodComponent`; `HubBloodCentre`, `StorageCentre`, `Hospital` extend `Facility`; all exceptions extend `RaktSetuException` | Shared state (ID, group, dates) lives once in the parent class. |
| **Polymorphism** | `BloodComponent.getShelfLifeDays()`, `getStorageCondition()`, `Transfusable`; `Facility.canStore()`, `canCollect()` | The expiry calculation calls `getShelfLifeDays()` without knowing the type: 5 days for platelets, 42 for red cells, 365 for plasma. Hospitals answer `canStore() == false`. |

## Interfaces (programming to a contract)

| Interface | Implementations | Why |
|---|---|---|
| `domain/interfaces/Identifiable`, `ExpiryTracked`, `Transfusable`, `Exportable` | Components, facilities, transfer orders | Small, single-purpose contracts (Interface Segregation). |
| `domain/interfaces/AuditTrail` | `AuditLogger` (file, hash-chained), `InMemoryAuditTrail` (tests), `server/CapturingAuditTrail` (database) | The engine writes to "an audit trail" and does not care where it is stored. |
| `domain/interfaces/DistanceProvider` | `TableDistanceProvider` | Allocation asks for distances through this interface, so the source of routes can change without touching it. |
| `domain/interfaces/StockDataSource` | `persistence/DataLoader` | Loading from CSV is one implementation; the database is another. |
| `persistence/Repository<T extends Identifiable>` | `CsvRepository<T>` | Generic repository: one class stores facilities, units or orders. |
| `engine/strategy/RedistributionStrategy` | `NearestFirstStrategy`, `ExpiryFirstStrategy` | Strategy pattern: the admin picks a planning policy at run time. |
| `engine/forecast/DemandForecaster` | `MovingAverageForecaster`, `ExponentialSmoothingForecaster`, `CrostonForecaster`, `DayOfWeekForecaster`, `SeasonalSmoothingForecaster` | Strategy again: the risk check and the planner ask "a forecaster" for demand. Each forecaster is also a factory: it returns a `Forecast` object (nested interface, implemented by the records `Flat` and `Weekly`) that answers any horizon. Swapping in the default reproduces the old results exactly, which is how we proved the refactoring safe. |
| `server/alerts/AlertChannel` | `SmsChannel`, `WhatsAppChannel`, `LogChannel` | The dispatcher sends through "a channel"; adding a provider is one new class. `AlertChannels` receives every implementation Spring finds (a `List<AlertChannel>`) and picks the configured ones. |
| `server/security/LoginAttemptPolicy` | `SlidingWindowAttemptPolicy` | `LoginGuard` holds two policies (per account, per address) behind the same interface; another policy could replace them without changing `LoginGuard`. |
| Spring's `HealthIndicator`, Micrometer's `MeterBinder` | `server/monitoring/AuditChainHealthIndicator`, `NetworkMetrics` | Implementing a framework's interface is how you plug into it: Spring finds every `HealthIndicator` and adds it to `/actuator/health`. |

## Design patterns

| Pattern | Where |
|---|---|
| **Template Method** | `AbstractRedistributionStrategy.plan()` is `final` and fixes the algorithm; subclasses only supply `order()` and `choose()`. The same in `alerts/HttpAlertChannel.send()`: timeouts and error handling are shared, and SMS and WhatsApp only build their provider's request. |
| **Transactional outbox** | `alerts/AlertOutbox` + `AlertDispatcher`: an alert is saved in the same database transaction as the change that caused it and sent afterwards, with retries and exponential back-off, so a rolled-back change never sends and a restart never loses one. |
| **Strategy** | `RedistributionStrategy`, `LoginAttemptPolicy`. |
| **Repository** | `Repository<T>`, `CsvRepository<T>`, and the database repositories in `server/db`. |
| **Dependency injection** | Every server class receives its collaborators through its constructor (for example `LoginGuard(Clock, MeterRegistry)`); tests pass a fixed `Clock`. |
| **Immutable snapshot** | `NetworkMetrics.Snapshot`: a record replaced as a whole, read without locks (`volatile`). |
| **Chain of responsibility** | The Spring Security filter chain, with our `CsrfCookieFilter` and `PasswordChangeFilter`. |

## Exceptions

A checked hierarchy for business rules: `RaktSetuException` → `InsufficientStockException`,
`IncompatibleBloodException`, `UnitExpiredException`, `FacilityNotFoundException`, `InvalidDataException`.
The web layer (`server/ApiErrors`) turns each into a clear JSON error and HTTP status. `TooManyAttemptsException`
carries extra state (`retryAfter`), which becomes the `Retry-After` header, and `AlertDeliveryException` carries
`retryable`, which decides whether the dispatcher tries again.

## Data structures and concurrency (from the performance work)

| Idea | Where | What to say |
|---|---|---|
| **Choosing a data structure** | `domain/model/Inventory`: `TreeSet` (red-black tree) instead of `PriorityQueue` (binary heap) | Both add and take the soonest-expiring unit in O(log n); only the tree can be read in order without copying and sorting, and remove any unit in O(log n) instead of O(n). |
| **Memoisation** | `BloodComponent.getExpiryDate()` | Worked out on first use, then remembered; safe without locks because `LocalDate` is immutable. |
| **Precomputed index** | `engine/BloodNetwork.storingNeighbours` | Built once from the route table, so a search looks at nearby centres, not the whole network. |
| **Records as map keys** | `AbstractRedistributionStrategy.Slot` | `equals`/`hashCode` come free with a record, replacing string keys built by concatenation. |
| **Readers-writer lock** | `server/NetworkLock` (wraps `ReentrantReadWriteLock`) | Encapsulation: callers say "read" or "write" and pass a lambda; how the lock works is hidden, and it cannot be left locked by mistake (`try/finally` inside). |
| **Incremental verification** | `server/AuditIntegrity` | Remembers where the verified hash chain ends and checks only new entries. |
| **Batch loading (avoiding N+1 queries)** | `server/db/RequestRepository.units(Collection)` | One `IN (...)` query instead of one query per request. |

Measured results are in [`benchmarks/`](benchmarks/README.md).

## Other Java features

- **Generics with bounds:** `Repository<T extends Identifiable>`, `CsvMapper<T>`.
- **Collections:** `EnumMap` and `TreeSet` (stock by expiry in `Inventory`), `ArrayDeque` (the sliding
  window), `ConcurrentHashMap` (thread-safe lockout).
- **Records** for immutable values: `Route`, `BloodRequest`, `AuditEvent`, `RiskItem`.
- **Enums with behaviour:** `BloodGroup`, `ComponentType`, `Compatibility`.
- **Streams and lambdas:** risk analysis and planning.
- **Concurrency:** a readers-writer lock around the shared network, `volatile` fields, virtual threads in the
  load test, scheduled jobs (`@Scheduled`).
- **File I/O:** CSV persistence and the hash-chained audit file (`persistence`).
- **Hashing:** SHA-256 audit chain (`persistence/AuditHashing`).

## Diagrams

See [`uml/`](uml/): use cases, packages, domain classes, engine classes, the server's security and monitoring
classes, and the emergency-request sequence.
