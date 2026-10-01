# RaktSetu

**Expiry-aware blood redistribution network.**

**Live demo: https://raktsetu-ubvx.onrender.com** (free hosting: after a quiet spell the first visit takes about a
minute to wake up). Staff can request an account from the sign-in page; a network administrator approves each
request before it can be used.

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
| 2 Digital twin and forecasting | Twin with true vs reported stock, Pune pilot network with road routes, six demand forecasters, 20-replication study | **Done** ([findings](docs/research/forecasting/findings.md)) |
| 3–4 Research | Stale-data-aware optimiser, reporting-compliance experiments, calibration with real data, paper | Planned |
| 5 System (V3) | Spring Boot server, database, sign-in and roles, workflows, notifications, SMS/WhatsApp alerts, monitoring, API docs, benchmarked and load-tested | **Working product, [live](https://raktsetu-ubvx.onrender.com)** (e-Raktkosh/FHIR integration still to do) |

### Course deliverables

| Deliverable | State |
|---|---|
| Java source code | Done: 7 Maven modules + React front end, 189 Java tests, 80% coverage gate per module |
| UML design documents | Done: 8 diagrams in [`docs/uml`](docs/uml) |
| Executable project | Done: one-click Windows app with Java included, plus web and console JARs ([Releases](https://github.com/pritikakhandelwal/raktsetu/releases)) |
| Project report | Drafted; the PDF will be added to `docs/` at submission |
| PowerPoint presentation | Drafted (10 slides) |
| Final demonstration | Script ready (first-run setup to a delivered request and an approved transfer) |

## Download for Windows (no installation)

Download `RaktSetu-<version>-windows.zip` from [Releases](https://github.com/pritikakhandelwal/raktsetu/releases),
unzip it anywhere, and double-click:

- **`RaktSetu.exe`**: the web app. Your browser opens http://localhost:8080 after about 20 seconds; the first visit
  creates the administrator. Data is kept in `RaktSetu\data` in your user folder. Close the window to stop it.
- **`RaktSetu Console.exe`**: the console app (course V1).

Java is included. If SmartScreen warns about an unrecognised app, choose "More info", then "Run anyway" (the app is
not signed with a paid certificate). To rebuild the package: `powershell -ExecutionPolicy Bypass -File
tools\package-windows.ps1` (needs JDK 21+).

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

## The web app (the full product)

RaktSetu runs as a multi-user web app: a Spring Boot server with a database, and a React front end for three kinds
of user. **One command, nothing to install but Java 21+:**

```bash
./mvnw package -DskipTests        # once: builds the Java code and the front end (Maven fetches its own Node.js)
./run-web.sh                      # Windows: run-web.bat
```

Open http://localhost:8080. The server starts empty: no sample data and no built-in accounts.

1. **First run:** the setup page asks you to create the first **network administrator** (your own username and a
   password of at least 10 characters with a letter and a digit).
2. **Facilities** (admin): add your hub blood centre, storage centres and hospitals, clicking the map to place each
   one. Road routes to every other facility are worked out automatically (OSRM, with a straight-line estimate when
   offline); the admin can correct any route by hand.
3. **People** (admin): create an account for each member of staff and choose their facility. RaktSetu shows a
   **one-time password** once; the person must choose their own password the first time they sign in. Admins can
   reset a password or disable an account later.
4. Centre staff record stock (with the donation number on the bag, or numbered automatically) and report their stock
   daily; hospitals request blood.

The overview page shows a getting-started checklist until these steps are done.

| Role | Pages | What they do |
|---|---|---|
| Network administrator | Overview, Requests, Transfers, Facilities, People, Audit | Sees the whole network; plans and approves transfers; manages facilities and accounts |
| Centre staff | Today, Stock, Transfers | Log collections, report daily stock, dispatch requests, receive transfers, discard damaged units |
| Hospital staff | Request blood, My requests, Availability | Request blood for their hospital, cancel before dispatch, confirm receipt |

Every user has an **Account** page (click your name) to change their password. The date is the real date in
India (Asia/Kolkata); just after midnight expired units move to wastage and the daily reminders go out.

**How a request flows:** a hospital raises it → units are reserved immediately from the nearest compatible centres
(soonest-expiring first) → each centre is notified and dispatches → the hospital confirms receipt. Cancelling before
dispatch puts the units back on the shelf. A request the network cannot meet is recorded and administrators are told.

**How a transfer flows:** the admin generates a plan (Nearest-First or Expiry-First) → approves or rejects each
transfer → stock moves and both centres are notified → the destination confirms it arrived.

**Notifications** (the bell) cover requests to dispatch, dispatches, cancellations, unmet requests, transfers, and a
daily reminder of stock expiring within two days and overdue stock reports.

**With PostgreSQL** instead of the embedded database: `docker compose up --build` (or run the JAR with
`--spring.profiles.active=postgres` and `RAKTSETU_DB_URL`, `RAKTSETU_DB_USER`, `RAKTSETU_DB_PASSWORD`).

Options: `--raktsetu.data-dir=...` chooses where the embedded database lives; `--raktsetu.routing-url=` (empty)
turns off online route lookups; `--server.port=...` changes the port. Back up the data folder; deleting it starts
afresh with the setup page.

**Urgent alerts (SMS and WhatsApp):** anyone can add a mobile number on their Account page (admins can add one
when creating a person). Requests to dispatch, blood on its way, and requests that could not be met are then also
sent to the phone, so an emergency does not wait for someone to open the site. Alerts are written to an outbox in
the same transaction as the change, then sent in the background with retries (30 s, 1 min, 2 min, ... up to 5
tries); administrators see delivery status on the People page, and anyone can send themselves a test alert.
With no provider configured, alerts are only written to the server log. To send real messages, set environment
variables before starting the server (never put keys in files you commit):

```bash
# SMS through Twilio
export RAKTSETU_ALERTS_SMS_ACCOUNT_SID=AC...
export RAKTSETU_ALERTS_SMS_AUTH_TOKEN=...
export RAKTSETU_ALERTS_SMS_FROM=+1...            # your Twilio number
# and/or WhatsApp through Meta's Cloud API (needs an approved template "raktsetu_alert" with one body parameter)
export RAKTSETU_ALERTS_WHATSAPP_PHONE_NUMBER_ID=...
export RAKTSETU_ALERTS_WHATSAPP_TOKEN=...
```

**Monitoring and API docs:**

- `/actuator/health` is public and reports DOWN if the audit log has been tampered with. Administrators also see
  the details.
- `/actuator/metrics` and `/actuator/prometheus` (administrators only) publish the network's state, for example
  `raktsetu.units.at_risk`, `raktsetu.facilities.unverified` and `raktsetu.logins.failed`, for Grafana or similar.
- `/api-docs` is a browsable OpenAPI description of every endpoint (`/v3/api-docs` gives the JSON).
- Five wrong passwords in 15 minutes lock that account for up to 15 minutes; twenty from one address lock that
  address. An administrator's password reset clears the lock. For JSON logs, add
  `--logging.structured.format.console=ecs`.

The OOP concepts in the code, class by class, are in [docs/oop-concepts.md](docs/oop-concepts.md).

**Performance** ([details](docs/benchmarks/README.md)): at 1,000 blood centres (300,000 units), serving an
emergency request takes 0.13 ms and planning transfers for the whole network 0.67 s, after profiling cut these
by 66× and 47×. The server handles about 430 requests per second with 200 simultaneous users on one laptop
(p99 0.74 s, no errors), 4.4× more than before tuning. Benchmarks: `raktsetu-bench` (JMH and an HTTP load test).

For front-end development with hot reload, run the server and then `npm run dev` in `web/`
(http://localhost:5173 forwards `/api` to the server). `./mvnw verify -DskipFrontend` builds only the Java side.

### API

All endpoints need a signed-in session except sign-in and first-run setup; accounts with a one-time password can
only change their password until they do; changes need the `X-XSRF-TOKEN` header
(the front end sends it from the `XSRF-TOKEN` cookie).

| Endpoint | Who | Purpose |
|---|---|---|
| `GET/POST /api/setup` | Anyone, until the first admin exists | First-run setup |
| `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout` | Anyone | Sign in and out |
| `POST /api/auth/password` | Signed in | Change your own password |
| `GET /api/summary`, `/facilities`, `/facilities/{id}/stock`, `/stock`, `/risks`, `/audit`, `/wastage` | Signed in | Views |
| `POST /api/facilities/{id}/units`, `/report`, `/units/{unitId}/discard` | That centre, admin | Stock changes |
| `GET/POST /api/requests`; `POST /api/requests/{id}/dispatch`, `/receive`, `/cancel` | By role | Request workflow |
| `POST /api/plans`; `GET /api/transfers`; `POST /api/transfers/{id}/approve`, `/reject` | Admin | Transfer plans |
| `POST /api/transfers/{id}/receive` | Destination centre | Confirm arrival |
| `GET /api/notifications`; `POST /api/notifications/{id}/read`, `/read-all` | Signed in | Notifications |
| `POST /api/compatibility`, `GET /api/strategies`, `/strategies/compare` | Signed in | Tools |
| `POST /api/facilities`, `PUT /api/facilities/{id}`, `GET /api/facilities/{id}/routes`, `PUT /api/routes/{from}/{to}` | Admin | Facilities and routes |
| `GET/POST /api/users`; `POST /api/users/{u}/reset-password`, `/enabled` | Admin | Accounts |
| `PUT /api/auth/contact`; `POST /api/alerts/test` | Signed in | Your mobile number and alert setting; a test alert (once a minute) |
| `GET /api/alerts` | Admin | Alert channels and recent deliveries (numbers masked) |

Errors come back as `{"error": ..., "message": ...}`: 400 bad input, 401 not signed in, 403 not allowed for this
account, 404 unknown facility, 409 refused (for example not enough stock), 429 too many failed sign-ins.

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

## Research: forecasting study (Phase 2)

On a synthetic Pune district network (10 centres, 22 hospitals, real road times), the digital twin compared six
demand forecasters under flat, weekly and dengue-season demand, with 20 paired replications
([method](docs/research/forecasting/method.md), [findings](docs/research/forecasting/findings.md)):

- Redistribution itself cuts expired units by about **60%** and halves unmet demand when supply is adequate.
- A longer history makes forecasts up to 13% more accurate, but modelling weekdays adds about 1 point once window
  length is controlled for, and Croston/SBA does not help rare groups under this demand model.
- More accurate forecasts did **not** give better plans: forecasters differ by under 20 expired units in 120 days,
  and the differences follow how many units are moved, not forecast error.

Demand patterns and the network are illustrative; calibration against real usage data is the next step.

## Modules

| Module | Contents |
|---|---|
| `raktsetu-domain` | Blood groups, components, facilities, inventory, compatibility rules, exceptions. No dependencies. |
| `raktsetu-engine` | Allocation, expiry risk, forecasting, data-freshness beliefs, redistribution strategies. |
| `raktsetu-persistence` | CSV repositories, event store, hash-chained audit log. |
| `raktsetu-sim` | Digital-twin simulator and experiment runner. |
| `raktsetu-bench` | Performance: JMH engine benchmarks and an HTTP load test ([results](docs/benchmarks/README.md)). |
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
- [OOP concepts, class by class](docs/oop-concepts.md)
- [Performance: benchmarks and load test](docs/benchmarks/README.md)
- [Research: forecasting study](docs/research/forecasting/findings.md)
- [Roadmap](docs/roadmap/RaktSetu_Final_Roadmap.pdf)
- [Data file formats](data/README.md)
- [How we work](CONTRIBUTING.md)

## Scope

RaktSetu is a working decision-support system; its research results so far come from simulated data on a synthetic
network. It recommends transfers for authorised staff to approve; it never moves blood automatically. Transfers between blood centres are permitted in India under the
3 April 2017 amendment to the Drugs and Cosmetics Rules. No donor personal data is stored.
