# Security review

A review of the web server and front end on 1 October 2026, against the OWASP Top 10 (2021). It was done by reading
the code and testing a running server; it is not a substitute for an independent penetration test before a real
deployment.

## Findings

| # | Severity | Finding | Status |
|---|---|---|---|
| S1 | High | **Lockout of every user behind a proxy.** Sign-in failures are counted per IP address (20 in 15 minutes). On a hosting platform every request arrives from the platform's proxy, so a few people mistyping passwords, or one attacker, would lock everyone out. | Fixed: the `prod` profile sets `server.forward-headers-strategy=native`, so the server uses the visitor's address from `X-Forwarded-For` (trusted only from private-network proxies). |
| S2 | Medium | **No Content Security Policy.** A cross-site scripting bug, if one were introduced, could load scripts from anywhere. | Fixed: only the site's own scripts run; the map tiles are the only outside resource. Also added `Referrer-Policy` and `Permissions-Policy`. Checked in a browser: the app and map work with no policy violations. |
| S3 | Medium | **Sign-in cookie flags.** The session cookie was not marked `SameSite` or `Secure`. | Fixed: `SameSite=Lax` always; `Secure` in the `prod` profile. |
| S4 | Low | **Unbounded audit page.** `GET /api/audit?limit=` accepted any number, so a signed-in user could make the server load the whole log at once. | Fixed: capped at 500. |
| S5 | Low | **API explorer public in production.** `/api-docs` shows every endpoint to anyone. | Fixed: off in the `prod` profile. |
| S6 | Low | **Dependency updates were manual.** | Fixed: Dependabot opens weekly update pull requests for Maven, npm and GitHub Actions. |

## Checked and sound

| OWASP area | What was checked |
|---|---|
| A01 Broken access control | Every `/api` call needs a session except sign-in, setup and the privacy notice. Centre and hospital staff can act only for their own facility (`SecurityTest`, `WorkflowTest`). Administrator actions check the role on the server, not only in the page. |
| A02 Cryptographic failures | Passwords hashed with BCrypt through Spring's delegating encoder. No secrets in the code: database and provider keys come from environment variables. Phone numbers are masked in logs and on screen. |
| A03 Injection | All database queries use bound parameters. The few queries built by joining strings join only constants and integers. React escapes everything it displays. |
| A04 Insecure design | Every transfer needs human approval; incompatible or expired units are refused even when picked by hand; every stock movement is on a hash-chained log whose tampering the health check reports. |
| A05 Security misconfiguration | Stack traces are never returned. Health details, metrics and Prometheus need an administrator. The H2 console is off. |
| A07 Identification and authentication | New accounts get one-time passwords and must change them. Passwords need 10+ characters with a letter and a digit. Lockout after 5 failures per account or 20 per address in 15 minutes. The session ID changes at sign-in (no session fixation). CSRF tokens on every change. |
| A08 Integrity failures | Audit log hash chain; the CI build runs on every change. |
| A09 Logging and monitoring | Structured logs available; health, metrics and audit-chain checks at `/actuator`. |
| A10 Server-side request forgery | The only outgoing calls are to the configured routing and alert providers, never to an address a user supplies. |

## Still open

- **Two-factor sign-in for administrators.** Recommended before real use; administrators can approve transfers and
  manage every account.
- **Breached-password check.** New passwords are not checked against lists of leaked passwords.
- **Code scanning.** Turn on GitHub's CodeQL "default setup" (Settings → Code security), which needs no code change.
- **Independent penetration test** before handling real hospital operations.
- **Lockout memory on several servers.** Sign-in failures are counted in each server's memory, so with several servers
  the limits apply per server. Addressed with the multi-server work.

## Running in production

Start with `SPRING_PROFILES_ACTIVE=postgres,prod` behind HTTPS. Set `RAKTSETU_DB_URL`, `RAKTSETU_DB_USER` and
`RAKTSETU_DB_PASSWORD` (never the defaults), and the data protection settings in
[../privacy/README.md](../privacy/README.md).
