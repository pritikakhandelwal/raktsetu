# ADR 0002: "Today" is always passed in

- Status: Accepted
- Date: 2026-09-25

## Context

Expiry depends on the current date. If code reads the system clock, sample data goes stale before the demo, tests
depend on the day they run, and simulations cannot run 365 days in a second.

## Decision

Domain and engine methods take `LocalDate today` as a parameter (later a `java.time.Clock` in services). Only the
outermost layer (console, server) decides what "today" is. The console has a "Set simulation date" option.

## Consequences

- Deterministic tests and reproducible simulations.
- The demo date can be fixed in advance.
- Slightly longer method signatures; accepted.
