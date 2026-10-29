# UML diagrams

PlantUML sources, reviewed in pull requests like code. Render locally with the PlantUML plugin for IntelliJ or VS Code,
or paste into https://www.plantuml.com/plantuml.

| File | Diagram | Version |
|---|---|---|
| `use-case.puml` | Use cases of the three roles, the simulator and the SMS provider, grouped by role | v2 (web app) |
| `packages.puml` | Module dependencies, including the server, benchmarks and web front end | v2 (web app) |
| `domain-classes.puml` | Domain model: components, facilities, inventory, requests, transfers, interfaces, exceptions | v2 (Phase 1) |
| `engine-classes.puml` | Engine: allocation, risk, freshness, strategies (Template Method), transfers | v1 (Phase 1) |
| `server-security-monitoring.puml` | Server: sign-in lockout (Strategy behind `LoginAttemptPolicy`), health check and metrics | v1 (hardening) |
| `alerts.puml` | Urgent SMS/WhatsApp alerts: channels (Strategy + Template Method), transactional outbox, dispatcher | v1 (alerts) |
| `forecasting.puml` | Demand forecasters (Strategy), the `Forecast` objects they create, and where the engine uses them | v1 (Phase 2) |
| `sequence-emergency-request.puml` | The emergency request flow | v1 (Phase 1) |

Planned (roadmap Section 8): persistence class diagram, plan-and-approve sequence diagram, unit state machine, daily
planning activity, component and deployment diagrams, event schema.

The OOP concepts each class demonstrates are listed in [`../oop-concepts.md`](../oop-concepts.md).
