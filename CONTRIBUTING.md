# How we work

## Branches and pull requests

- `main` is always green. Nobody pushes to it directly.
- One branch per change, named after the roadmap module: `m1-inventory`, `m5-request-service`, `docs-uml-v1`.
- Open a pull request early. It needs **one review from a teammate who did not write the code**, and CI must pass.
- Keep pull requests small (under ~400 changed lines where possible).

## Before you push

```bash
./mvnw verify
```

## Code rules

- Java 21 language level. Records for value objects, sealed interfaces for closed hierarchies.
- No `LocalDate.now()` inside domain or engine code: "today" is always passed in, so tests and the simulator control
  time.
- Expected problems are checked `RaktSetuException` subclasses; programming errors are `IllegalArgumentException` /
  `IllegalStateException`.
- Every public class and method has a Javadoc comment explaining *why*, not just *what*.
- New behaviour comes with tests. Rules that apply to every combination (blood groups, components) get jqwik property
  tests.
- If you change classes or their relationships, update `docs/uml`.

## Weekly rhythm

- **Monday:** pick issues from the project board.
- **Friday:** demo what works on `main`; take screenshots for the report into `docs/screenshots/`.

## Ownership (everyone must be able to explain everything)

| Member | Owns |
|---|---|
| A | Domain model and compatibility, allocation, forecasting |
| B | Engine and strategies, optimiser, simulator, experiments, paper |
| C | Persistence and event store, console, server and dashboard, DevOps, executable |
