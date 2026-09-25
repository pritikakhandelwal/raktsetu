# RaktSetu

**Expiry-aware blood redistribution network.**

India's e-Raktkosh answers *"Where is blood available?"* RaktSetu answers *"Which units will expire unused, where
should they go now, and how sure are we, given that stock reports are often out of date?"*

Blood collection in India rose from 126.95 lakh units (2023) to 157.14 lakh (2025), while discards rose from 8 lakh to
17.43 lakh; expiry is the most common reason, at about 46% of discards (e-Raktkosh data, Rajya Sabha Unstarred
Question 1882, answered 4 August 2026).

RaktSetu is an OOP-in-Java course project that grows into a research prototype and a production-quality system. The
full plan is in [`docs/roadmap/RaktSetu_Final_Roadmap.pdf`](docs/roadmap/RaktSetu_Final_Roadmap.pdf).

## Status

| Phase | Scope | State |
|---|---|---|
| 0 Foundations | Repository, CI, domain model, compatibility rules, design doc, UML v1 | **In progress** |
| 1 Course core (V1) | 16-option console app, persistence, allocation, strategies, simulator | Planned |
| 2–4 Research | Digital twin, forecasting, stale-data-aware optimiser, experiments, paper | Planned |
| 5 System (V3) | Spring Boot server, event store, REST/FHIR API, dashboard | Planned |

## Quick start

Requirements: **Java 21 or newer**. Maven is not needed; the wrapper downloads it.

```bash
./mvnw verify        # build and run all tests (Windows: mvnw.cmd verify)
./run.sh             # start the console app (Windows: run.bat)
```

Or run the JAR directly after building:

```bash
java -jar raktsetu-cli/target/raktsetu.jar
```

## Modules

| Module | Contents |
|---|---|
| `raktsetu-domain` | Blood groups, components, facilities, inventory, compatibility rules, exceptions. No dependencies. |
| `raktsetu-engine` | Allocation, expiry risk, forecasting, data-freshness beliefs, redistribution strategies. |
| `raktsetu-persistence` | CSV repositories, event store, hash-chained audit log. |
| `raktsetu-sim` | Digital-twin simulator and experiment runner. |
| `raktsetu-cli` | Console application (the course deliverable). |

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
