# ADR 0001: Modular monolith, not microservices

- Status: Accepted
- Date: 2026-09-25

## Context

RaktSetu must be a gradeable Java console app, a research simulator and later a web system. National load is small:
about 43,000 new units a day, a few events per second.

## Decision

One Maven multi-module build with one-way dependencies: `domain` ← `engine`, `persistence` ← `sim` ← `cli` (and later
`server`). The domain module has no dependencies at all.

## Consequences

- One build, one deployable, simple debugging and demos.
- Module boundaries are enforced by Maven, so a module can be split into a service later if a real need appears.
- The simulator and the live app share the same engine code, so research results come from the shipped code.
