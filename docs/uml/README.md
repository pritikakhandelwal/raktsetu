# UML diagrams

PlantUML sources, reviewed in pull requests like code. Render locally with the PlantUML plugin for IntelliJ or VS Code,
or paste into https://www.plantuml.com/plantuml.

| File | Diagram | Version |
|---|---|---|
| `use-case.puml` | Use cases for the three actors and the simulator | v1 (Phase 0) |
| `packages.puml` | Module dependencies | v1 (Phase 1) |
| `domain-classes.puml` | Domain model: components, facilities, inventory, requests, transfers, interfaces, exceptions | v2 (Phase 1) |
| `engine-classes.puml` | Engine: allocation, risk, freshness, strategies (Template Method), transfers | v1 (Phase 1) |
| `server-security-monitoring.puml` | Server: sign-in lockout (Strategy behind `LoginAttemptPolicy`), health check and metrics | v1 (hardening) |
| `alerts.puml` | Urgent SMS/WhatsApp alerts: channels (Strategy + Template Method), transactional outbox, dispatcher | v1 (alerts) |
| `sequence-emergency-request.puml` | The emergency request flow | v1 (Phase 1) |

Planned (roadmap Section 8): persistence class diagram, plan-and-approve sequence diagram, unit state machine, daily
planning activity, component and deployment diagrams, event schema.

The OOP concepts each class demonstrates are listed in [`../oop-concepts.md`](../oop-concepts.md).
