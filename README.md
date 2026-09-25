# RaktSetu

**Expiry-aware blood redistribution network.**

India's e-Raktkosh answers *"Where is blood available?"* RaktSetu answers *"Which units will expire unused, where
should they go now, and how sure are we, given that stock reports are often out of date?"*

Blood collection in India rose from 126.95 lakh units (2023) to 157.14 lakh (2025), while discards rose from 8 lakh to
17.43 lakh; expiry is the most common reason, at about 46% of discards (e-Raktkosh data, Rajya Sabha Unstarred
Question 1882, answered 4 August 2026).

RaktSetu is a solo OOP-in-Java course project by Pritika Khandelwal that grows into a research prototype and a
production-quality system. The full plan is in
[`docs/roadmap/RaktSetu_Final_Roadmap.pdf`](docs/roadmap/RaktSetu_Final_Roadmap.pdf).

## Status

| Phase | Scope | State |
|---|---|---|
| 0 Foundations | Repository, CI, domain model, compatibility rules, design doc, UML v1 | Done |
| 1 Course core (V1) | 16-option console app, persistence, allocation, strategies, simulator | **Done** |
| 2–4 Research | Digital twin, forecasting, stale-data-aware optimiser, experiments, paper | Planned |
| 5 System (V3) | Spring Boot server, event store, REST/FHIR API, dashboard | **Started**: REST API and web front end |

## Quick start

Requirements: **Java 21 or newer**. Maven is not needed; the wrapper downloads it.

```bash
./mvnw verify        # build and run all tests (Windows: mvnw.cmd verify)
./run.sh             # start the console app (Windows: run.bat)
```

Or run the JAR directly after building:

```bash
java -jar raktsetu-cli/target/raktsetu.jar --today 2026-10-01
```

| Option | Meaning |
|---|---|
| `--data DIR` | Folder for the data files (default `raktsetu-data`) |
| `--today YYYY-MM-DD` | The date the app treats as today (default: system date). Fix it for demos. |
| `--seed N` | Seed for the sample network and simulation (default 42) |
| `--reset` | Replace the data with a fresh sample network |

On first run the app creates a **synthetic sample network** around Pune (1 hub, 4 storage centres, 6 hospitals, about
350 units, 14 days of usage history), dated relative to "today" so it never goes stale.

## Web app

The same engine also runs as a web app: a Spring Boot REST API (`raktsetu-server`) serving a single-page React front
end (`web/`) in a black, cream and coral-red style with heavy condensed headlines. Maven builds the front end too
(it downloads its own Node.js), so one command produces one JAR:

```bash
./mvnw package -DskipTests
./run-web.sh --raktsetu.today=2026-10-01      # Windows: run-web.bat
```

Then open http://localhost:8080. Sections: at-risk units, emergency request and compatibility check, the network and
each centre's shelves, transfer plans with approve and reject, the simulation, and the audit log with its hash-chain
seal. "Next day" in the header moves the date forward. Every change is saved to `raktsetu-data/`.

For front-end development with hot reload, run the server as above and then `npm run dev` in `web/`
(http://localhost:5173 forwards `/api` to the server). `./mvnw verify -DskipFrontend` builds the Java side without
Node.

| Endpoint | Purpose |
|---|---|
| `GET /api/summary` | Headline numbers |
| `GET /api/facilities`, `GET /api/facilities/{id}/stock` | Centres, hospitals and shelves |
| `POST /api/facilities/{id}/units`, `POST /api/facilities/{id}/report` | Add units; record a stock report |
| `GET /api/stock` | Network-wide stock and groups running low |
| `POST /api/requests`, `POST /api/compatibility` | Blood requests and compatibility checks |
| `GET /api/risks`, `GET /api/strategies`, `GET /api/strategies/compare` | Risk check and strategies |
| `POST /api/plans`, `GET /api/transfers`, `POST /api/transfers/{id}/approve` or `/reject` | Transfer plans |
| `GET /api/audit`, `POST /api/simulations`, `GET /api/wastage`, `POST /api/date` | Audit, simulation, wastage, date |

Errors come back as `{"error": ..., "message": ...}`: 400 for bad input, 404 for an unknown facility, 409 for a refused
operation such as not enough stock.

## Demo in five minutes

1. **Option 1, `SC04`:** the remote Baramati centre holds platelets marked URGENT.
2. **Option 4:** request O- red cells for `H01`; units come from the nearest centre, soonest-expiring first.
3. **Option 5, `PLASMA`, `AB+`, `O-`:** allowed, because plasma rules are the reverse of red cells.
4. **Option 4 with 50 units:** a clear "Not enough..." message listing where stock exists.
5. **Options 6, 8, 7, 9:** the risk check, both strategies side by side, a plan, and approvals.
6. **Option 10:** the audit log and its hash-chain check. Edit a line of `raktsetu-data/audit_log.csv`, run it again,
   and it reports the tampering.
7. **Option 11:** the 30-day simulation. With seed 42, redistribution cuts expired units from 105 to 68 (35%) and
   reduces unmet requests from 43 to 32.

## Results so far (simulation, seed 42, 30 days from 2026-10-01)

| Policy | Units expired | of which platelets | Requests unmet | Units transferred |
|---|---|---|---|---|
| No redistribution | 105 | 91 | 43 | 0 |
| Nearest-First | 68 | 59 | 32 | 174 |
| Expiry-First | 68 | 59 | 32 | 152 |

These come from a synthetic network with illustrative demand; they show the mechanism works, not real-world impact.
Both strategies save the same number of units here, but Expiry-First moves 22 fewer units to do it. Separating the
strategies properly needs the larger experiments planned for Phase 4.

## Modules

| Module | Contents |
|---|---|
| `raktsetu-domain` | Blood groups, components, facilities, inventory, compatibility rules, exceptions. No dependencies. |
| `raktsetu-engine` | Allocation, expiry risk, forecasting, data-freshness beliefs, redistribution strategies. |
| `raktsetu-persistence` | CSV repositories, event store, hash-chained audit log. |
| `raktsetu-sim` | Digital-twin simulator and experiment runner. |
| `raktsetu-cli` | Console application (the course deliverable). |
| `raktsetu-server` | Spring Boot REST API; serves the web front end. |
| `web/` | React + TypeScript front end (Vite). |

## Clinical rules implemented

| Component | Shelf life | Compatibility |
|---|---|---|
| Whole blood | 35 days | Group-identical only |
| Red cells (SAGM) | 42 days | ABO antigens and RhD must be acceptable to the recipient (O- universal donor) |
| Platelets | 5 days | ABO-identical preferred; others allowed with a warning; RhD+ to RhD- flagged |
| Plasma (FFP) | 365 days | Reverse of red cells: AB is the universal plasma donor; RhD ignored |

A unit is expired **on** its expiry date. Shelf-life values must be confirmed against current NACO/NBTC standards.

## Documentation

- [Design document](docs/design.md)
- [Architecture decision records](docs/adr)
- [UML diagrams (PlantUML sources)](docs/uml)
- [Data file formats](data/README.md)
- [How we work](CONTRIBUTING.md)

## Scope

RaktSetu is a decision-support prototype running on simulated data. It recommends transfers for authorised staff to
approve; it never moves blood automatically. Transfers between blood centres are permitted in India under the
3 April 2017 amendment to the Drugs and Cosmetics Rules. No donor personal data is stored.
