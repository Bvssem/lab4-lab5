# DevSecOps Lab — TekBook SAST Pipeline

> A DevSecOps CI/CD lab featuring a custom Semgrep static-analysis rule for
> SQL-injection taint detection, a Dockerized Spring Boot application, and a
> GitLab CI security gate with network-segmented Docker Compose.

## Overview

This lab builds a security-gated CI/CD pipeline around **TekBook**, a Spring
Boot + PostgreSQL application. The pipeline runs a **custom Semgrep SAST rule**
(`tekbook-rules.yml`) that performs taint-mode analysis to detect HTTP request
parameters flowing unsanitized into SQL queries — a classic SQL-injection
pattern. The build fails (`--error`) on any violation.

## Tech Stack

| Layer | Technology |
|---|---|
| Application | Spring Boot 3.2.5 · Java 21 · Maven |
| Database | PostgreSQL 16 |
| DB admin | pgAdmin 4 (dev profile only) |
| Container | Docker multi-stage build (Maven → JRE runtime) |
| Orchestration | Docker Compose (frontend + backend networks) |
| SAST | Semgrep (custom taint rule) |
| CI | GitLab CI |

## CI Pipeline

```yaml
# .gitlab-ci.yml
semgrep_scan:
  stage: security
  image: semgrep/semgrep:latest
  script:
    - semgrep scan --config tekbook-rules.yml --error --metrics=off
        --json-output semgrep-report.json
  artifacts:
    when: always
    paths: [semgrep-report.json]
```

The job runs in the `security` stage, fails the pipeline on any finding
(`--error`), and publishes `semgrep-report.json` as a CI artifact.

## The Custom SAST Rule (`tekbook-rules.yml`)

```yaml
rules:
  - id: tekbook-http-to-sql
    languages: [java]
    severity: ERROR
    message: Donnee HTTP dans SQL. Utiliser des parametres.
    mode: taint
    pattern-sources:
      - pattern: $REQ.getParameter(...)      # HTTP input
    pattern-sinks:
      - patterns:
        - pattern: $STMT.executeQuery($SQL)  # JDBC sink
        - focus-metavariable: $SQL
```

This taint rule traces data from `HttpServletRequest.getParameter()` (source)
into `Statement.executeQuery()` (sink) and flags any unsanitized flow as an
`ERROR`. The companion `SqlExample.java` demonstrates the **safe** pattern
using `PreparedStatement` with bound parameters (`?` + `setString`).

## Setup & Usage

```bash
# 1. Configure environment
cp .env.example .env     # set DB_NAME, DB_USER, DB_PASSWORD, PGADMIN_*

# 2. Build and run the full stack
docker compose up --build
#   TekBook  → http://localhost:8080
#   pgAdmin  → http://localhost:5050  (only with: --profile dev)

# 3. Run the SAST scan locally
semgrep scan --config tekbook-rules.yml --error

# 4. In GitLab CI, the semgrep_scan job runs automatically on push.
```

## Docker Compose — Network Segmentation

```
┌─────────── frontend network ───────────┐
│  TekBook (:8080)     pgAdmin (:5050)   │
└──────────────┬───────────────┬─────────┘
               │               │ (dev profile only)
┌──────────────┴───────────────┴─────────┐
│           backend network               │
│            PostgreSQL (:5432)           │
└─────────────────────────────────────────┘
```

- **backend** — PostgreSQL is isolated; not exposed to the host.
- **frontend** — TekBook (8080) and pgAdmin (5050) are exposed.
- pgAdmin is connected to **both** networks (annotated `COMPROMISE` in
  `compose.yaml`) but is gated behind the `dev` Compose profile — it only
  starts when explicitly requested, keeping it out of production.

## Security Takeaways

- **Shift-left SAST** — SQL-injection detection runs in CI before merge, not
  after deployment.
- **Custom taint tracking** — the Semgrep rule is purpose-built for the app's
  stack (Servlet `getParameter` → JDBC `executeQuery`), not a generic rule.
- **Build-fail on violation** — `--error` enforces a hard security gate.
- **Network segmentation** — the database lives on an isolated backend network;
  pgAdmin's cross-network access is dev-profile-gated.
- **Multi-stage Docker build** — build tooling (Maven, JDK) stays in the build
  image; the runtime image carries only the JRE + JAR.
