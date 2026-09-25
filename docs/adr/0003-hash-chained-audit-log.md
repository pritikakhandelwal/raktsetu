# ADR 0003: Hash-chained, append-only audit log

- Status: Accepted (implementation in Phase 1, M4)
- Date: 2026-09-25

## Context

Every issue, transfer and discard must be recorded permanently, and editing a past record should be detectable.

## Decision

Each audit entry stores the SHA-256 hash of its own content plus the previous entry's hash. A "verify" operation
recomputes the chain and reports the first broken entry. In Phase 2 the same log becomes the event store from which
stock is rebuilt (event sourcing).

## Alternatives considered

- Plain append-only file: edits are undetectable.
- Blockchain: solves distributed consensus, which we do not need; far more complex.

## Consequences

- Tamper-evidence with no extra infrastructure, and a strong demo moment.
- It detects tampering but does not prevent deleting the whole file; backups are an operational concern.
