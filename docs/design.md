# RaktSetu design document

Status: **v0.1, Phase 0**. Solo project by Pritika Khandelwal; share with the project guide for feedback before
Phase 1.

## 1. Problem

Units expire in one blood centre while patients elsewhere wait for the same group. e-Raktkosh shows where blood is,
but it does not recommend where blood should go, and its stock figures are often out of date because centres do not
always report daily. RaktSetu targets both gaps.

## 2. Goals and non-goals

**Goals**

1. Track stock by component and blood group, issuing first-expiry-first-out.
2. Serve emergency requests from the nearest compatible facility, explaining any substitution.
3. Flag units likely to expire unused, using each facility's recorded usage.
4. Recommend transfers of at-risk units, comparing interchangeable strategies, with human approval.
5. Treat stale stock reports as uncertain rather than as fact.
6. Record every issue, transfer and discard in a tamper-evident audit log.
7. Measure the impact with a reproducible simulation.

**Non-goals (for now):** donor management and screening (e-Raktkosh already does this), arranging transport, live
e-Raktkosh integration, storing any donor personal data.

## 3. Users

| Actor | Main tasks |
|---|---|
| Blood centre staff | View stock, add units, remove expired units, run the risk check |
| Hospital staff | Raise emergency requests, check compatibility |
| Network administrator | Network-wide view, redistribution plans, approvals, audit log, simulation |

## 4. Architecture

Modular monolith: Maven modules with one-way dependencies. See ADR 0001.

```
raktsetu-cli ──► raktsetu-sim ──► raktsetu-engine ──► raktsetu-domain
      └────────► raktsetu-persistence ─────────────────────┘
```

- **domain**: pure Java, no dependencies. Model, interfaces, exceptions, clinical rules.
- **engine**: decisions (allocation, risk, forecasting, beliefs, strategies). Depends only on domain.
- **persistence**: files now, an event store later. Depends only on domain.
- **sim**: drives the engine through the same interfaces the live app uses.
- **cli**: input and output only; no business logic.

## 5. Key rules

- **Time:** "today" is always passed in (ADR 0002). Nothing in domain or engine calls `LocalDate.now()`.
- **Expiry:** `expiry = collectedOn + shelfLife` unless explicitly shortened; a unit is expired on its expiry date.
- **Issuing:** `Inventory` never issues an expired unit; callers purge expired units daily and record them as wastage.
- **Compatibility:** `BloodGroup` owns the two base relations (red cells, plasma). Each component applies the right one
  and adds its own nuance (whole blood identical only; platelets flagged when non-identical). Checking returns a
  result with a reason; only issuing or transferring an unsafe unit throws `IncompatibleBloodException`.
- **Hospitals** hold no stock in this model; units are issued to them from centres.
- **Audit:** append-only and hash-chained (ADR 0003).

## 6. Domain model (Phase 0)

| Type | Kind | Notes |
|---|---|---|
| `BloodGroup` | enum | 8 groups; `canGiveRedCellsTo`, `canGivePlasmaTo` |
| `ComponentType` | enum | 4 components; factory `newUnit(...)` |
| `BloodComponent` | abstract class | ID, group, collection date, expiry; implements `Identifiable`, `ExpiryTracked`, `Transfusable` |
| `WholeBlood`, `RedCellConcentrate`, `Platelets`, `FreshFrozenPlasma` | final classes | Shelf life, storage, compatibility |
| `Facility` | abstract class | Owns its `Inventory` (composition); tracks last report date |
| `HubBloodCentre`, `StorageCentre`, `Hospital` | final classes | Capabilities |
| `Inventory` | class | `PriorityQueue` per component and group in FEFO order |
| `CompatibilityResult` | record | Level and reason |
| `RaktSetuException` + 5 subclasses | checked exceptions | |

## 7. Coming next (Phase 1)

`BloodRequest`, `TransferOrder`, `Reservation`, `UsageHistory`, `BloodNetwork`, `RequestService`,
`ExpiryRiskAnalyzer`, `FreshnessTracker`, `RedistributionStrategy` with Nearest-First and Expiry-First,
`Repository<T>`, `CsvRepository<T>`, `DataLoader`, `AuditLogger`, `ReportWriter`, the 30-day simulator and the full
console menu. See the roadmap, Section 6.

## 8. Open questions

- Which region do we calibrate the simulator to? Proposed: Pune division, Maharashtra.
- Travel-time matrix: precompute with OSRM, or use straight-line distance with a road factor for V1?
- Should platelets for RhD-negative women of child-bearing age be refused rather than flagged? (Needs clinical input;
  flagged for now.)
