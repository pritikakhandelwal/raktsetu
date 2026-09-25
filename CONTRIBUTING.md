# How I work

RaktSetu is a one-person project. These rules stand in for the reviews a team would give.

## Branches and pull requests

- `main` is always green. Changes go in through pull requests, not direct pushes.
- One branch per change, named after the roadmap module: `m1-inventory`, `m5-request-service`, `docs-uml-v1`.
- Before merging, re-read the whole diff against the checklist in the pull request template, and wait for CI to pass.
  An automated code review is a useful second pair of eyes.
- Keep pull requests small (under ~400 changed lines where possible), so each one is easy to explain in the viva.

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

- **Monday:** pick issues from the project board for the current phase.
- **Friday:** run through what works on `main`; save screenshots for the report in `docs/screenshots/`; write the
  report notes for anything finished.
- **Every two weeks:** show progress to the project guide for outside feedback.

If time runs short, follow the cut list in the roadmap (Section 3.1): Phase 1 is never cut.
