# Data file formats

All files are UTF-8 CSV with a header row. Blank lines and lines starting with `#` are ignored. A malformed row is
reported with its file name and line number (`InvalidDataException`) and skipped; the other rows still load.

The app keeps its data in a folder (default `raktsetu-data/`, not committed to git). On first run it generates a sample
network with a fixed seed **relative to "today"**, so units never go stale before a demo. The loader is implemented by
`DataLoader` in `raktsetu-persistence`.

## facilities.csv

| Column | Type | Example | Notes |
|---|---|---|---|
| `id` | text | `HUB01` | Unique |
| `name` | text | `Pune Regional Blood Centre` | |
| `type` | `HUB` / `STORAGE` / `HOSPITAL` | `HUB` | Hospitals hold no stock |
| `last_reported_on` | date `YYYY-MM-DD` | `2026-10-01` | Optional; drives data freshness |

## units.csv

| Column | Type | Example | Notes |
|---|---|---|---|
| `unit_id` | text | `RS-000123` | Unique. Units created by the app use `RS-` plus a serial; imported units may use any ID, such as an ISBT 128 donation number |
| `facility_id` | text | `HUB01` | Must be a facility that stores blood |
| `component` | `WHOLE_BLOOD` / `RED_CELLS` / `PLATELETS` / `PLASMA` | `PLATELETS` | |
| `blood_group` | `O-` ... `AB+` | `B+` | |
| `collected_on` | date | `2026-09-29` | |
| `expires_on` | date | | Optional; only when processing shortened the shelf life |

## distances.csv

| Column | Type | Example | Notes |
|---|---|---|---|
| `from_id` | text | `HUB01` | |
| `to_id` | text | `H02` | Rows are read as symmetric unless both directions are given |
| `distance_km` | integer | `14` | |
| `travel_minutes` | integer | `32` | Road travel time. Synthetic in the sample network; to be precomputed with OSRM on OpenStreetMap for the calibrated network |

## usage.csv

Daily issues per facility, used by the expiry-risk check.

| Column | Type | Example |
|---|---|---|
| `date` | date | `2026-09-30` |
| `facility_id` | text | `SC03` |
| `component` | component | `RED_CELLS` |
| `blood_group` | group | `O+` |
| `units_issued` | integer | `4` |

## Written by the app

| File | Contents |
|---|---|
| `audit_log.csv` | Append-only, hash-chained record of every collection, issue, transfer and discard: `seq, date, type, facility_id, counterparty, component, blood_group, unit_ids` (`;`-separated), `note, prev_hash, hash` |
| `wastage.csv` | Every unit removed as expired: `date, facility_id, unit_id, component, blood_group` |
| `state.properties` | `next_unit_serial`, so IDs of issued units are never reused |
| `reports/*.csv` | Exported stock, risk, redistribution and simulation reports |
