# DispatchFlow — Tech stack

**Status:** Decided, 14 November 2026. [§22](#22-open-decisions) lists what is still open; rows marked
Optional are adopted if time allows.

What each part of DispatchFlow is built with, **why that choice wins here, and what it beat**.
[`domain_layer_design.md`](domain_layer/domain_layer_design.md) defines the bounded contexts, aggregates and events;
[`docs/roadmap.md`](../roadmap.md) says when each part is built; the [ADRs](../ADRs/README.md) record the decisions
and their reasons. This file doesn't repeat any of them. FR, BR, NFR, UC and Q identifiers refer to the
requirements document.

**How to read the tables.** Each row has a **Why** column (what makes the choice right for DispatchFlow)
and an **Over** column (the serious alternatives, and the reason each lost). The **Status** column says
how settled the row is and when it's adopted:

- **Decided:** settled; names its ADR where one exists.
- **Proposed:** still open; [Open decisions](#22-open-decisions) says what decides it.
- **Optional:** worth adopting if time allows; nothing depends on it.
- **When:** *Alpha*, *Jan* (the January break, after the Alpha demo), *B* (Semester B), *Pilot*
  (before the pilot). The dates are in the [roadmap](../roadmap.md#1-the-course-calendar).

**Versions** are the baseline of 12 November 2026 ([Version baseline](#21-version-baseline)). Build files
and `mise.toml` pin exact versions, because NFR-62 and NFR-115 require solver and library versions to be
recorded.

## Contents

1. [What decides close calls](#1-what-decides-close-calls)
2. [Architecture style](#2-architecture-style)
3. [Bounded contexts](#3-bounded-contexts)
4. [Repository layout](#4-repository-layout)
5. [Development environment and workflow](#5-development-environment-and-workflow)
6. [Core service (Java)](#6-core-service-java)
7. [Optimization service (Python)](#7-optimization-service-python)
8. [Research harness](#8-research-harness)
9. [Routing and geodata](#9-routing-and-geodata)
10. [Web app](#10-web-app)
11. [Phone app](#11-phone-app)
12. [Shared TypeScript packages](#12-shared-typescript-packages)
13. [Outside services and where data goes](#13-outside-services-and-where-data-goes)
14. [Environments](#14-environments)
15. [Infrastructure and operations](#15-infrastructure-and-operations)
16. [Observability and alerting](#16-observability-and-alerting)
17. [Testing](#17-testing)
18. [CI/CD and supply chain](#18-cicd-and-supply-chain)
19. [Documentation and diagrams](#19-documentation-and-diagrams)
20. [Adoption order](#20-adoption-order)
21. [Version baseline](#21-version-baseline)
22. [Open decisions](#22-open-decisions)
23. [Alternatives rejected across the stack](#23-alternatives-rejected-across-the-stack)
24. [References](#24-references)

---

## 1. What decides close calls

When two options are both good, these six rules pick, in this order:

1. **One VM, one Compose file** (NFR-14, NFR-16). An option that needs a cluster, a message broker or a
   second database loses.
2. **Personal data stays in Israel** (NFR-209). Anything that touches personal data is self-hosted when
   self-hosting is cheap, and an outside service needs an accepted exception.
3. **Four students, seven months.** The default of the framework already chosen wins, unless something
   else pays for itself before the Alpha. Every tool has a milestone when it's adopted, and nothing is
   adopted earlier.
4. **Reproducible** (NFR-62, NFR-115). Exact versions, images pinned by digest, actions pinned by commit
   SHA.
5. **Contracts first between languages** (ADR-006). Code is generated on both sides of every boundary;
   no hand-written type crosses one.
6. **Free and open source, or free for students.** The code itself is proprietary (ADR-017), so every
   licence is checked against an allow-list.

---

## 2. Architecture style

| Concern | Choice | Status | Why | Over |
|---|---|---|---|---|
| Shape of the system | A Java **modular monolith** for the product, plus a separate **Python optimization service** | Decided (ADR-002) | One deployable keeps transactions local and operations simple on one VM. The optimizer is separate because the research ecosystem (OR-Tools, PyVRP, NumPy, Numba, SciPy, vrplib) is Python, and a 60-second CPU-bound solve must not share a process with HTTP | **Microservices per context:** nine services on one VM, and distributed transactions for no gain. **Everything in Python:** no equivalent of Spring Modulith's module verification or event registry, and a weaker security stack. **Everything in Java** with OR-Tools' Java bindings: the research harness and PyVRP would still be Python, and the product and the study must share one validator (BR-42) |
| Inside each service | **Domain-Driven Design with hexagonal architecture** (ports and adapters) | Decided ([ADR-001](../ADRs/ADR-001-domain-driven-design-with-hexagonal-architecture.md)) | The domain is the hard part (re-optimization rules, late reports, lifecycles). Ports keep it free of frameworks, so it's unit-tested without Spring or a database, and every outside service can be faked (NFR-175) | **Layered** (controller, service, repository): the domain ends up shaped by persistence. **Clean Architecture** with interactors and presenters: more ceremony than ports and adapters for the same separation |

### 2.1 The dependency rule

Dependencies point inward only.

| Layer | Holds | May depend on | Must not depend on |
|---|---|---|---|
| Domain | Aggregates, entities, value objects, records, domain events, named errors | The JDK; JSpecify annotations; the shared kernel; its own module's `events`; pure libraries with no I/O (libphonenumber, JTS; NumPy in Python) | Frameworks, persistence, HTTP, JSON, the system clock, ID generation, unseeded randomness, other modules |
| Application | One class per use case; the module's `api` (interfaces other modules call) and `spi` (ports it owns for them to implement); secondary ports; application services such as `SolveRequestAssembler` | Domain; other modules' `api`, `spi` and `events`; `@Transactional`, and nothing else from Spring | Adapters, web types, persistence types |
| Adapters | Primary: web, event stream, job workers, schedulers, watchers, provider callbacks, CLI. Secondary: persistence, OSRM, optimizer, geocoder, SMS, email, push, file store | Application, frameworks | Other adapters (an adapter reaches another only through a port) |
| Configuration | Wiring, profiles | Everything | — |

Rules every module follows:

1. **The inside owns and names its ports**, in domain language: `RouteOptimizer`, not
   `OptimizerHttpClient`.
2. **Aggregates refer to each other by ID only.** The transaction rule is in the domain document (§6.1):
   one existing aggregate per transaction, plus newly created aggregates of the same module and technical
   records.
3. **Time, IDs and randomness come in through ports** (`Clock`, `IdGenerator`, seeded generators), so
   tests control them (NFR-13, NFR-175). The domain never reads the system clock.
4. **Outside models stop at the adapter** (anti-corruption layer). OpenAPI DTOs, OSRM responses and
   provider callbacks never reach the domain.
5. **Reads may bypass aggregates** (CQRS-lite). Screens and reports query read models directly; every
   write goes through an aggregate.
6. **Outside calls happen before the transaction opens**, so no database transaction waits on HTTP. A retry (`@Retryable`) sits in the adapter that makes
   the call, never in the application layer.
7. **Every rule has one owner in the domain** (domain document §3.7, §5). Reading repositories, ports and
   other modules is the application layer's job: it passes what it read to the owner as values, so no
   domain class depends on a port.

### 2.2 Enforcement

| Where | Tool | Status | What it checks | Why now, or why later |
|---|---|---|---|---|
| Core | **Spring Modulith** `ApplicationModules.verify()` | Decided ([ADR-040](../ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md)) · Alpha | No cycles between modules; modules use only each other's exposed API | It catches the most expensive mistake (a cycle), and it's one test |
| Core | **ArchUnit** | Decided ([ADR-040](../ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md)) · Alpha | The six purity rules of the domain document (§3.9): domain packages import only the JDK, the shared kernel, their own domain, their own module's `events` and pure libraries; no clock, no `UUID.randomUUID()`, no randomness or I/O; no generic exceptions for business rules; only persistence adapters call `restore` and the lock-read snapshot factories. Also: each persistence adapter uses only its own module's schema, and the application layer takes only `@Transactional` from Spring | A few rules keep the domain pure from day one. The package layout is the convention, so no annotation is needed. **Over jMolecules' hexagonal rules:** they check the same layering, at the price of annotating every type, and the package names already say it |
| Core | **Error Prone + NullAway**, with JSpecify annotations (as Spring Framework 7 uses) | Decided ([ADR-040](../ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md)) · Jan | Null safety at compile time | Error Prone needs JDK compiler flags that are a common source of build trouble. Added after the Alpha, in warning mode first, then as errors |
| Optimizer, research | **import-linter** | Decided ([ADR-040](../ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md)) · Alpha | Layers; the domain imports no `fastapi`, `pydantic`, `ortools`, `pyvrp` or `numba`; product adapters never import `pyvrp` (FR-286) | Cheap, and it keeps PyVRP out of the product from the first commit |
| Optimizer, research | **mypy** (strict) | Decided ([ADR-011](../ADRs/ADR-011-python-tooling-uv-ruff-mypy-pytest.md)) · Alpha | Types | Cheapest when the code is new |

All of these run in CI on every change once adopted (NFR-133).

---

## 3. Bounded contexts

Defined in [`domain_layer_design.md`](domain_layer/domain_layer_design.md): nine contexts (Access, Fleet, Customers &
Orders, Planning, Live Operations, Recipient Messaging, Reporting, Audit, and the Optimization service),
their aggregates, the module graph, the event catalogue and the rules that span aggregates. This file keeps only
the tools, so the model has one home and can't drift between two copies.

The glossary of the ubiquitous language, in English and Hebrew, is §17 of the domain document, so code,
screens and documents use the same words.

---

## 4. Repository layout

**Decided ([ADR-123](../ADRs/ADR-123-the-code-keeps-its-grouped-layout-and-three-components-take-new-names.md)) · Alpha.** The grouped tree with ADR-123's component names. Inside `core` and `optimizer` it draws the target packages ([ADR-040](../ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md)), not today's.
`src/research` already exists (NFR-50).

```text
.editorconfig  .gitattributes  mise.toml  justfile  lefthook.yml  renovate.json
.github/
  workflows/                      CI pipelines
  CODEOWNERS  pull_request_template.md  ISSUE_TEMPLATE/
contracts/
  client-api/v1/openapi.yaml      OpenAPI: web and phone apps ↔ core (ADR-006)
  optimization-api/v1/openapi.yaml  OpenAPI: core ↔ optimization service
src/
  backend/
    core/                         Java, Spring Boot, one deployable
      src/main/java/.../dispatchflow/
        access/ fleet/ orders/ planning/ liveops/ messaging/ reporting/ audit/
          domain/                   aggregates, entities, value objects, sealed states, named errors
          events/                   the module's events (the `events` named interface): IDs,
                                    shared-kernel values and the package's own enums only
          application/
            api/                    use cases and queries other modules call
            spi/                    ports this module owns for other modules to implement
            port/out/               repositories, logs and outside services
            service/                one class per use case, and application services
          adapter/
            in/web/  in/job/  in/schedule/  in/callback/
            out/persistence/  out/osrm/  out/optimizer/  out/...
        shared/                     shared kernel: typed IDs, DeliveryDate, IsraelDateTime, DaySeconds, TimeWindow,
                                    GeoPoint, Weight, Volume, Priority, PhoneNumber, and the domain's
                                    building blocks (errors, Version, AggregateRoot, events; ADR-097)
      src/main/resources/db/migration/   Flyway migrations, one PostgreSQL schema per module
    optimizer/                    Python, FastAPI
      src/dispatchflow_optimizer/
        domain/  application/
        adapters/inbound/http/
        adapters/outbound/solvers/{ortools,alns,nearest_neighbour}/
  frontend/
    web/                          React web app
    mobile/                       Expo phone app (from Semester B)
    packages/                     api-client, schemas, i18n, map-style, formatters, eslint-config, tsconfig
  research/                       harness, study configs, results (no pilot-derived data, NFR-50)
tests/
  e2e/                            Playwright: the Alpha demo script and the use-case flows
  load/                           k6 scripts (NFR-10, NFR-59)
infra/
  deploy/                         compose.yaml  compose.dev.yaml  Caddyfile  backup/  observability/
  osrm/                           OSRM configuration
  terraform/                      AWS account resources (from M13)
data/                             git-ignored: OSM extract, OSRM files, PMTiles, benchmark instances
docs/
  architecture/  ADRs/  operations/  guides/{he,en}/  research/
```

**Why one repository:** both contracts, both services and both apps change together, and one pull
request can update a contract and every generated client at once. **Over** a repository per service: no
atomic contract changes, and four copies of CI.

---

## 5. Development environment and workflow

| Concern | Choice | Status | Why | Over |
|---|---|---|---|---|
| Tool versions | **mise** (`mise.toml` pins JDK, Node.js, Python, uv, pnpm, just, Terraform) | Decided ([ADR-038](../ADRs/ADR-038-mise-pins-every-toolchain-and-core-moves-to-java-25.md)) · Alpha | One file gives four laptops and CI the same versions of three language toolchains, on Windows and macOS | **asdf:** no native Windows support. **SDKMAN + nvm + pyenv:** three tools, three configuration files, and pyenv isn't native on Windows. **Dev containers:** heavy on Windows laptops, and weaker in IntelliJ |
| Task runner | **just** (`just dev`, `just test`, `just e2e`, `just demo`, `just study`) | Decided ([ADR-009](../ADRs/ADR-009-just-as-the-single-entry-point.md)) · Alpha | One command per task, the same on every OS | **make:** not native on Windows, with tab-sensitive syntax. **npm scripts:** tie Java and Python tasks to Node. **Taskfile:** fine, but YAML for what is a list of shell lines |
| Git hooks | **lefthook**: format and lint staged files, check commit messages | Decided · Alpha | One small binary that runs hooks for Java, Python and TypeScript alike | **husky:** Node-centred. **pre-commit:** Python-centred, and slower, because it builds its own environment per hook |
| Editor settings | **EditorConfig**; `.gitattributes` fixes line endings (LF) | Decided · Alpha | Mixed Windows and macOS laptops (NFR-208): line endings break shell scripts and diffs first | — |
| Containers on laptops | **Docker Desktop** (Windows, macOS) or Docker Engine (Linux) with Compose v2 | Decided (NFR-16, NFR-208) | Testcontainers, Spring Boot's Compose support and the production Compose file all assume Docker | **Podman:** works, but each of those three needs extra setup on Windows |
| Local services | `compose.dev.yaml`: PostgreSQL, OSRM on a small extract, Mailpit, WireMock for SMS and the geocoder, the optimizer. **Spring Boot Docker Compose support** starts them when the core runs from the IDE | Decided ([ADR-042](../ADRs/ADR-042-compose-dev-for-local-runs-testcontainers-for-tests.md)) · Alpha | The whole stack on a laptop, with no outside network (NFR-175) | **Starting containers by hand:** someone always forgets one. **Testcontainers at development time:** good too, but the Compose file is already shared with production |
| IDEs | **IntelliJ IDEA** (free JetBrains educational licence) for Java; **PyCharm** or **VS Code** for Python; **VS Code** for TypeScript; **Android Studio** and **Xcode** for phone builds and emulators (Semester B) | Decided · Alpha | The best tool per language, all free for students | **One IDE for everything:** Java refactoring is weak outside IntelliJ |
| API client | **Bruno**, with collections in the repository | Decided · Alpha | Requests are versioned with the code and need no account | **Postman:** collections live in its cloud by default and need an account. **IntelliJ HTTP files:** good, but only for IntelliJ users |
| Database client | **DBeaver**, or IntelliJ's database tools | Decided · Alpha | Free, works with every OS | — |
| Contract-first mocks | **MSW** in the web app and tests; **Prism** serves a mock of `client-api` | Optional · Alpha | Front-end work can start before an endpoint exists | — |
| Source control | **Git** on **GitHub**: private repository, protected `main` and `dev`, required reviews and checks, `CODEOWNERS` per area | Decided · Alpha | The code is proprietary (ADR-017); GitHub Projects holds the Gantt chart the course asks for | **GitLab:** equally capable; the team is already on GitHub |
| Branching | Short-lived branches off `dev`, squash-merged into `dev`; `dev` reaches `main` by merge commit; **Conventional Commits** (checked by commitlint in lefthook and CI) | Decided · Alpha | Small merges keep four people out of each other's way; history stays readable, and release notes can be generated | **GitFlow:** long-lived branches and painful merges for a four-person team |
| Releases | Semantic versions with Git tags. Phone app versions follow NFR-44 (the previous version stays supported) | Decided · B | — | — |
| Planning and tracking | **GitHub Projects**: backlog, iteration board, and the roadmap view as the living Gantt chart (exported to draw.io for presentations) | Decided · Alpha | Issues, pull requests and the plan in one place | **Jira or Trello:** another tool, and no link to pull requests without setup |
| Dependency updates | **Renovate**, as Mend's hosted Renovate app, grouped weekly, with its pull requests against `dev` and a minimum release age of 3 days; it also updates pinned action SHAs, image digests (from January) and `mise.toml` | Decided ([ADR-046](../ADRs/ADR-046-renovate-replaces-dependabot-and-targets-dev.md)) · Jan | One configuration covers Maven, uv, pnpm, Compose images, GitHub Actions and mise, and it can turn tags into pinned digests and SHAs by itself | **Dependabot:** updates Compose images since 2025 and keeps existing pins current, but doesn't update `mise.toml` or convert tags into pinned digests and SHAs |

---

## 6. Core service (Java)

| Concern | Choice | Status | Why | Over |
|---|---|---|---|---|
| Language | **Java 25 (LTS)** | Decided ([ADR-038](../ADRs/ADR-038-mise-pins-every-toolchain-and-core-moves-to-java-25.md)) | Records for value objects; sealed interfaces and pattern matching for state machines (BR-12, BR-33, BR-87); virtual threads for event streams and workers. Since Java 24, a virtual thread no longer pins its carrier inside `synchronized`, which JDBC drivers and pools use | **Java 21:** also LTS, but it lacks that fix, and 25 is supported longer. **Kotlin:** nicer syntax, but a second language on top of Java and Python for the team, and Spring Modulith's documentation is Java-first |
| Framework | **Spring Boot 4.1.x** now; **4.2.x** in the January break | Decided · Alpha | Spring Boot 4.1 was released on 10 June 2026 and has open-source support until 31 July 2027, past the end of the project. 4.2 is scheduled for 19 November 2026 and is supported until 31 December 2027. Upgrading after the Alpha demo keeps a `.0` release out of the last weeks before it | **Quarkus or Micronaut:** faster startup and native images, which a long-running VM doesn't need, and nothing there matches Spring Modulith, its event registry and Spring Security's resource-server support |
| Modules | **Spring Modulith 2.1** (the line matching Boot 4.1; the next line with Boot 4.2) | Decided ([ADR-040](../ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md)) · Alpha | One module per bounded context, verified in a test; the JDBC event publication registry; module tests; generated module documentation (§19) | **Maven multi-module:** compile-time boundaries, but nine modules of build configuration and no event registry. **ArchUnit alone:** checks packages, but has no event registry or module tests |
| Build | **Maven** with the Maven Wrapper | Decided ([ADR-013](../ADRs/ADR-013-java-tooling-maven-wrapper-and-spotless.md)) · Alpha | Declarative and predictable; the wrapper pins the version on every laptop | **Gradle:** faster incremental builds, but its build logic is code, which is one more thing to learn and debug. Maven is decided for good |
| Code style | **Spotless** with palantir-java-format; **JaCoCo** coverage | Decided ([ADR-013](../ADRs/ADR-013-java-tooling-maven-wrapper-and-spotless.md)) · Alpha | Formatting is automatic, so reviews never discuss it | **google-java-format:** two-space indentation and awkward lambdas. **Checkstyle:** reports problems but doesn't fix them |
| Static checks | **Error Prone + NullAway** | Decided · Jan | Catches null dereferences and common bugs at compile time, using the same JSpecify annotations as Spring Framework 7 | **Checker Framework:** more powerful, and much slower and noisier. **SpotBugs:** runs after compilation, with more false positives |
| Boilerplate | Records and plain classes; **no Lombok** | Decided · Alpha | Records cover value objects | **Lombok:** generated code hides what an aggregate exposes, and it hooks into the compiler, which Error Prone also does |
| Database | **PostgreSQL 18**, **Flyway** | Decided ([ADR-041](../ADRs/ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md)) | Partial unique indexes, `FOR UPDATE SKIP LOCKED`, advisory locks and `jsonb` carry real rules here (BR-34, BR-38, BR-36, BR-101). PostgreSQL 19 is due on 19 November 2026; a first release isn't worth taking mid-project. PostGIS only when a feature needs a spatial query (Q-18) | **MySQL:** no partial indexes, which BR-34 and BR-38 rely on. **H2 in tests:** behaves differently; Testcontainers runs the real thing. **Liquibase:** XML or YAML changelogs and rollbacks, which expand-then-contract migrations don't need |
| Persistence adapter | **jOOQ** (Open Source Edition), with explicit hand-written mappers | Decided ([ADR-041](../ADRs/ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md)) · Alpha | Type-safe SQL generated from the real schema; explicit mapping keeps the domain free of annotations; the same SQL serves Reporting's read models; PostgreSQL features (`SKIP LOCKED`, `ON CONFLICT`, advisory locks) are first-class. Details in [6.3](#63-persistence) | **JPA / Hibernate:** a pure domain needs a second set of entity classes plus mappers, and lazy loading and dirty checking don't earn their cost. **Spring Data JDBC:** designed for aggregates, but it saves a changed collection by deleting and reinserting every child row, so every edit would rewrite a 200-stop plan. **MyBatis:** SQL in XML, with no compile-time checking. **JdbcTemplate:** no type safety across 37 tables |
| Connection pool | **HikariCP** (Spring Boot's default) | Decided · Alpha | The default, and the fastest well-known pool | — |
| Web adapter | **Spring MVC** on virtual threads; interfaces generated by **openapi-generator** (`spring`, `interfaceOnly`, `useSpringBoot4`, `useJackson3`); Bean Validation; **Jackson 3** | Decided ([ADR-043](../ADRs/ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md)) · Alpha | Plain blocking code that scales like reactive code, thanks to virtual threads; a contract-first API (ADR-006), whose generated validation covers most of NFR-56 | **WebFlux:** reactive code everywhere, for no gain over virtual threads, with blocking JDBC underneath. **springdoc (code first):** would make the Java code the source of truth, while the Python service and the phone app need a reviewed contract before the code |
| API conventions | Path versions (`/api/v1`) through Spring Framework 7's API versioning; errors as RFC 9457 Problem Details with a stable error code that both apps translate | Decided ([ADR-043](../ADRs/ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md)) · Alpha | NFR-21, NFR-69, NFR-143, NFR-144; a standard error shape needs no custom parser | **Custom error JSON:** every client has to learn it |
| Live updates | `SseEmitter` adapter behind the `LiveUpdates` port; a heartbeat every 15 seconds, at which the stream's session is re-checked (NFR-267); a stream ends when its token expires (NFR-188) or its session is revoked (NFR-267) | Decided ([ADR-026](../ADRs/ADR-026-server-sent-events-and-push-for-live-updates.md)) · Alpha | Updates only flow from the server, and server-sent events are plain HTTP, through Caddy and HTTP/2 | **WebSockets:** two-way, which isn't needed; harder to authorize and to proxy. **Polling:** load and latency (NFR-09) |
| Authentication | **Spring Security** resource-server decoding. Access tokens: **Nimbus JOSE + JWT**, ES256, 15 minutes (NFR-86), carrying the user's and the session's generation counters and refused once either has moved on (NFR-177; [ADR-045](../ADRs/ADR-045-generation-counters-revoke-tokens.md)). Refresh tokens: opaque, rotating, stored as hashes (NFR-22, NFR-91, NFR-178). Passwords: **Argon2id** (Spring Security's encoder with **Bouncy Castle**; NFR-36). Web refresh cookie: `__Host-` prefixed, Secure, HttpOnly, SameSite=Strict, `Path=/`, with a CSRF token (NFR-106, NFR-107); sign-out sends `Clear-Site-Data` and deletes the cookie | Decided ([ADR-036](../ADRs/ADR-036-one-account-per-person-signed-in-by-email.md), [ADR-045](../ADRs/ADR-045-generation-counters-revoke-tokens.md)) · Alpha (sign-in), B (in full) | First-party apps for one company; ADR-036's account and link rules and ADR-045's revocation rules are simpler to own than to configure. ES256 lets anything verify a token without being able to sign one, at no real cost; Argon2id is OWASP's first choice and memory-hard | **Keycloak:** another heavy service on one VM, with link and revocation behaviour that differs from ADR-036 and ADR-045. **Spring Authorization Server:** a full OAuth2 server, with flows first-party apps don't need. **Hosted identity** (Auth0, Cognito): another vendor and its own email flows. **HS256:** would work with one service, but any holder of the key can mint tokens. **bcrypt:** not memory-hard, and it ignores password bytes past 72 |
| Link tokens and hashes | `SecureRandom` 256-bit link tokens, stored as hashes (NFR-211); HMAC-SHA-256 with a secret key for opt-out hashes (NFR-202) and research pseudonyms (NFR-153); `MessageDigest.isEqual` for constant-time secret checks (NFR-192) | Decided ([ADR-069](../ADRs/ADR-069-sign-in-and-transport-security.md)) · B | The JDK has everything needed | **A library:** nothing to add |
| Password policy | At least 8 characters, plus a bundled list of the 3,000 most common passwords of 8 or more characters (NFR-95) | Decided ([ADR-069](../ADRs/ADR-069-sign-in-and-transport-security.md)) · B | NIST-style length plus blocklist | **Composition rules** (a digit, a symbol): longer to explain, no safer |
| Second factor | TOTP | Optional (FR-128 is a Could) | — | — |
| Authorization | Role checks at the web adapter for every call and stream event, from the contract's `x-capability` (NFR-85); data-scoped checks inside use cases (BR-02, BR-41) | Decided ([ADR-068](../ADRs/ADR-068-every-web-handler-is-authorized-by-role-from-the-contract.md)) · Alpha | Roles are transport policy; "only your own route" is a domain rule | **Method-security annotations scattered by hand:** one forgotten annotation is an open endpoint; generating them from the contract can't forget |
| Rate limits | **Bucket4j**, in process | Decided ([ADR-071](../ADRs/ADR-071-api-rate-limits-in-process-with-bucket4j.md)) · B | Limits keyed by typed email and source IP (NFR-35, NFR-53, NFR-102, NFR-200, NFR-216); one instance needs no shared store. The core trusts `X-Forwarded-For` only from Caddy, which overwrites it, and keeps the buckets in a Caffeine cache with a maximum size and an expiry after the longest window | **Caddy rate limiting:** needs a custom Caddy build and doesn't know the typed email. **Redis-backed limits:** another service |
| Caching | **Caffeine**: the user and session generation counters ([ADR-045](../ADRs/ADR-045-generation-counters-revoke-tokens.md)), company settings, the service-area polygon | Decided · Alpha | Bounded size and expiry, in process | **`ConcurrentHashMap`:** no expiry. **Redis:** another service |
| Outbound resilience | Timeouts on every client; Spring Framework 7's built-in `@Retryable` and `@ConcurrencyLimit`, in the adapter that makes the call, where a retry is safe. The application layer allows only `@Transactional` | Decided ([ADR-040](../ADRs/ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md)) · Alpha | NFR-42 and NFR-189 are met by queues and fall-backs, not retries | **Resilience4j:** circuit breakers solve fleet-scale problems that one VM with three dependencies doesn't have |
| Solve queue | `solve_job` table claimed with `FOR UPDATE SKIP LOCKED`, in **two lanes, one worker each**: daily solves and re-optimizations. A queue watchdog (domain §12) | Decided · Alpha | A 60-second solve for tomorrow must never hold up today's 20-second re-optimization (NFR-07). The lanes, phases and limits (BR-38, BR-112) are domain rules, so they need their own table anyway | **RabbitMQ, Redis or Kafka:** another service (NFR-14). **JobRunr:** Postgres-backed with a dashboard, but its model is "run this method later", and the domain rules would still need their own table. **Quartz:** a cluster scheduler, for a single instance |
| Event publication | Spring Modulith's JDBC event publication registry, with republication at restart, staleness limits and scheduled resubmission turned on. Events live in `<module>/events/` and carry only IDs, shared-kernel values and the package's own enums | Decided ([ADR-064](../ADRs/ADR-064-events-only-where-they-earn-their-place-through-the-modulith-registry.md)) · Alpha | Events are stored in the same transaction as the change. Modulith 2.x retries failed publications only when configured to, so the design configures it. If a module is ever split out, Modulith can externalize the same events to a broker, so no domain code changes (domain §2.3) | **A hand-written outbox:** the same idea, with the code to write and test ourselves |
| Schedules | Spring `@Scheduled` with cron in `Asia/Jerusalem` ([ADR-066](../ADRs/ADR-066-every-asynchronous-step-has-a-watcher.md)) | Decided · Alpha | One instance, so no distributed lock | **Quartz or ShedLock:** solve problems of several instances |
| Imports | **Apache Commons CSV**: strict UTF-8 first, then Windows-1255 with a preview (FR-313, FR-209). **Apache POI** with `ZipSecureFile` limits and no external links (NFR-96, NFR-97) | Decided ([ADR-086](../ADRs/ADR-086-csv-imports-upload-then-confirm-within-an-hour.md)) · Alpha (UTF-8 CSV), B (Excel, Windows-1255) | Commons CSV is small and lets our code control decoding, which FR-313's encoding rules need. POI has the zip-bomb limits and also writes the exports. Keep the `jdk.charsets` module if the runtime is jlinked | **OpenCSV:** bean binding we don't use. **FastExcel:** faster, but streaming-only, with fewer of the safety settings NFR-96 and NFR-97 need |
| Hebrew text | `java.text.Normalizer` (NFC) plus DispatchFlow's own rules for geresh and gershayim variants, abbreviations and spelling variants (FR-140, FR-310); `java.net.IDN` for email domains (BR-45) | Decided · Alpha | The JDK covers Unicode; the Hebrew-specific rules are ours and live in one table-driven function | **ICU4J:** much larger, for transliteration we don't do |
| Exports | **Apache POI** on the server, with formula characters neutralized (FR-107, NFR-203). Routes print from the browser (FR-72) | Decided · Alpha (summary only), B | One place enforces NFR-203 | **Client-side spreadsheet libraries:** every client would have to neutralize formulas |
| Proof images | **ImageIO** + **TwelveMonkeys**. Refuse oversized pixel dimensions before decoding, then re-encode, which strips metadata (NFR-205) | Decided · Alpha (signatures), B (photos) | A 5 MB file (NFR-37) can still declare enormous dimensions; checking the header first stops a decompression bomb | **Thumbnailator:** a convenience layer over the same decoders. **libvips:** a native dependency |
| Pure domain libraries; IDs | **libphonenumber** (FR-377, FR-378) and **JTS** (service area, FR-320) in the domain; **uuid-creator** (UUIDv7) in the `IdGenerator` adapter | Decided · Alpha | The two libraries have no I/O, so the domain may use them. IDs exist before persistence, but the domain doesn't make them: factories take them as parameters, so tests choose them | **The JDK's `UUID`:** Java 25 makes only random and name-based UUIDs, and v7 keys index better. JDK 26 adds `UUID.ofEpochMillis` for v7, but 25 is the LTS release. **PostGIS for the service area:** a spatial extension for one point-in-polygon check |
| HTTP clients | Spring `RestClient` for OSRM, the geocoder, SMS and push; the optimizer client generated from its contract (openapi-generator, `java` with the RestClient library) | Decided · Alpha | Synchronous code on virtual threads; integrates with observability and `@Retryable` | **WebClient:** reactive. **OkHttp or Apache HttpClient:** a second HTTP stack to configure |
| Email | **AWS SDK for Java v2** (SES v2) in `il-central-1`, with a configuration set with link and open tracking off (NFR-223) | Decided ([ADR-078](../ADRs/ADR-078-hosting-on-one-aws-vm-in-tel-aviv-built-with-terraform.md)) · Pilot (Mailpit before) | Email stays in Israel (NFR-209), and the SDK authenticates with the VM's IAM role | **SMTP to SES:** needs SMTP credentials to store and rotate. **SendGrid or Mailgun:** data leaves Israel |
| Observability | **OpenTelemetry Java agent** for traces; **Micrometer** for domain metrics (NFR-161), exported over OTLP; Spring Boot structured JSON logs carrying the trace ID (NFR-20, NFR-159), where a log converter scrubs the fields the data inventory names and request bodies are never logged (NFR-19); **Actuator** health and readiness (NFR-160) | Decided · Alpha (logs, health), Pilot (traces, metrics) | The agent instruments JDBC, HTTP clients and Spring with no code | **Micrometer Tracing alone:** works, but needs per-library setup. **Vendor agents:** tie the data to one vendor |
| Runtime image | **Eclipse Temurin 25 JRE**, non-root, with `-XX:MaxRAMPercentage` set for the container's memory limit | Decided ([ADR-047](../ADRs/ADR-047-images-pinned-by-digest-and-core-on-temurin-25-jre.md)) · Pilot | `linux/amd64` (NFR-208); a shell is available for debugging during the pilot | **GraalVM native image:** slow builds and reflection configuration for Spring, jOOQ and POI, to save startup time a long-running VM doesn't need. **Distroless:** smaller, but no shell; reconsider after the pilot |

### 6.1 Domain conventions

The domain document's §3 is the full set of rules. In Java they come down to these:

- **Value objects are records** that check themselves in their constructors and copy any list they hold
  (`List.copyOf`). The catalogue is in the domain document (§3.3).
- **States are sealed interfaces, and every transition is a guarded method** that refuses with a named
  error: the plan lifecycle (BR-12, BR-59, BR-60, BR-103), the solve job, deliveries (BR-87) and the
  rest (domain §9). A `switch` over a sealed state must cover every state, so the compiler finds every
  place a new state has to be handled; refused transitions are covered by tests, not by the compiler.
  Order status is derived, not stored (BR-33).
- **Domain errors are records of a sealed type per module** (`PlanningError`), thrown in a `RuleViolation`
  or, where every refusal must be listed, a `RuleViolations` (domain §3.8).
- **Domain events are records named in the past tense:** `PlanPublished`, `OutcomeRecorded`,
  `PlanRevised`. They live in the module's `events` package, which holds only IDs,
  shared-kernel values and its own enums, and which the module's domain may import ([ADR-064](../ADRs/ADR-064-events-only-where-they-earn-their-place-through-the-modulith-registry.md)). The catalogue, and why
  each one exists, is in the domain document (§7).
- **Israel's time-zone rules come from the JDK's tzdata.** Keep the JDK patched, because daylight-saving
  rules can change.

### 6.2 Application layer

- One class per use case: `RequestSolve`, `PublishPlan`, `AcceptProposal`, `HandOverRoute`, `ReportStop`,
  `ReconcileLateOutcome`, `ConfirmImport`. An interface in the module's `api` only for those another module
  calls (`ReconcileLateOutcome`, `RouteMembership`), because that is the seam a remote client would
  replace (domain §13). An interface with one implementation and one caller earns nothing.
- Outside calls first; then one `@Transactional` method that changes one existing aggregate. That
  annotation is the only Spring annotation allowed in this layer. `@Retryable` sits in the adapter
  that makes the outside call, not here.
- Each changing use case writes its audit record through the `AuditLog` port, in the same transaction
  (NFR-23, NFR-93). Audit records never carry personal data (NFR-94).

### 6.3 Persistence

**jOOQ in the persistence adapters**, with code generated from the Flyway-migrated schema, one
PostgreSQL schema per module. A module's adapter imports only its own schema's generated classes, which
ArchUnit checks; Reporting reads the read-only views other modules publish. The build runs
the migrations against a Testcontainers PostgreSQL (the `testcontainers-jooq-codegen-maven-plugin`), so
the generated code always matches the real schema. Docker must be running for `./mvnw verify`, which the
adapter tests need anyway.

- **Optimistic locking:** `UPDATE … WHERE id = ? AND version = ?` on the root table; zero rows updated
  means the change is out of date (BR-76). Child tables carry no version of their own.
- **Loading an aggregate whole:** jOOQ's `MULTISET` reads a root with its child rows in one query (a plan
  with its routes and stops), and the mapper rebuilds it through the aggregate's `restore` factory.
- **Constraints in the schema:** a partial unique index for one plan per date (BR-34); unique emails after
  normalizing (BR-45); unique keys on outgoing messages (NFR-191).
- **Types:** `timestamptz` for instants, `date` and `time` for Israel-local values; the database session
  runs in UTC.
- **Two database roles.** Flyway runs as `dispatchflow_owner`, which owns every table; the core connects
  as `app`, which has only the grants it needs, and `INSERT` and `SELECT` alone on `audit_record`. The
  application must never own a table: an owner can always grant itself back whatever was revoked.
  Flyway runs in a one-shot `migrate` step before the core starts, so the core process never holds the
  owner credentials, which stay deploy-only, in `migrate`'s own environment. A database function
  owned by `dispatchflow_owner` deletes only audit records older than 1 year and computes its cutoff
  itself. Its `EXECUTE` is revoked from `PUBLIC` and granted only to `app`, and the core's nightly retention job calls it at 03:00 `Asia/Jerusalem`, watched like
  the other jobs ([ADR-041](../ADRs/ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md), [ADR-075](../ADRs/ADR-075-retention-deletes-records-and-anonymizes-people-in-place.md)).
- **Migrations** are tested in CI against the schema of the last merge to `main`, with data in it
  (NFR-165).

### 6.4 Ports and adapters

| Secondary port | Adapter | Notes |
|---|---|---|
| `RouteOptimizer`, `FeasibilityChecker`, `BaselinePlanner` | Optimizer HTTP client, generated from `optimization-api` | Anti-corruption layer. Also used for edit checks (FR-233) and the publishing re-check (BR-21). While the optimizer is down, hand-overs and cancellations complete and their routes show *not yet checked* until it returns; publishing refuses (BR-70, [ADR-058](../ADRs/ADR-058-routes-changed-while-the-optimizer-is-down-are-checked-when-it-returns.md)) |
| `TravelTimes`, `RouteShapes` | OSRM `table` and `route` services | **Base durations only.** The Python `TravelTimeModel` applies the bucket factors and the buffer (FR-321, FR-322); the core never does |
| `Geocoder` | GovMap adapter, fake; Nominatim only if GovMap's terms fail | Decided in [ADR-085](../ADRs/ADR-085-govmap-geocodes-addresses-with-nominatim-as-the-fallback.md) |
| `SmsGateway` | Israeli provider adapter (Q-96); fake in every environment except production (NFR-206) | Callbacks verified by signature or header secret (NFR-192) |
| `EmailSender` | Amazon SES; Mailpit (SMTP) in development and tests | |
| `PushNotifier` | Expo Push API; fake in tests | The payload says only that something changed (NFR-218) |
| `LiveUpdates` | SSE emitter registry | |
| `ProofStore` | Local disk ([ADR-027](../ADRs/ADR-027-proof-files-on-the-vm-disk-with-hourly-backups.md)) | Served only through authorized calls (NFR-38) |
| `Clock` | System clock; a controllable clock in the end-to-end profile (NFR-175) | |
| `IdGenerator` | UUIDv7 (uuid-creator) | IDs exist before persistence; factories take them as parameters |
| `PasswordHasher`, `TokenSigner`, `Hmac` | Spring Security's Argon2id encoder; Nimbus JOSE + JWT; the JDK's `Mac` | Hashing and signing need secrets and randomness, so they stay out of the domain, which gets `PasswordHash` and `PhoneHash` values |
| Repositories | jOOQ | One repository per aggregate root |

### 6.5 Core ↔ optimizer calls

- **Plain HTTP and JSON**, synchronous, from the job worker's virtual thread. The client timeout is the
  solve limit plus 30 seconds (BR-32, BR-84).
- The core sends the travel-time matrix over distinct locations with each request ([ADR-052](../ADRs/ADR-052-each-plan-stores-the-matrix-and-values-it-was-solved-with.md)), and stores
  each plan's matrix with the plan (BR-37), compressed in a `bytea` column.

---

## 7. Optimization service (Python)

| Concern | Choice | Status | Why | Over |
|---|---|---|---|---|
| Language | **Python 3.14** | Decided ([ADR-038](../ADRs/ADR-038-mise-pins-every-toolchain-and-core-moves-to-java-25.md)) · Alpha | The newest Python that every scientific dependency ships wheels for: OR-Tools 9.15, Numba, PyVRP, NumPy and SciPy all have 3.14 wheels. It gets bug fixes through the whole project | **3.15:** OR-Tools has no 3.15 wheels yet. **3.13:** its bug-fix releases end about now, two years after its release, and it would get only security fixes during the pilot |
| Packaging and environments | **uv** (lock file committed) | Decided · Alpha | Fast, one tool for Python versions, environments and locking; the lock hash goes in every run manifest | **Poetry:** slower, and doesn't manage Python versions. **pip-tools + pyenv:** two tools. **conda:** heavy, for nothing that wheels don't already provide |
| Lint and format | **Ruff** | Decided · Alpha | One fast tool for linting and formatting | **flake8 + isort + black:** three tools to configure, and slower |
| Types | **mypy** (strict) | Decided · Alpha | One type authority in CI, with Pydantic's mypy plugin | **pyright:** faster, and fine in editors through Pylance, but CI needs one checker, and mypy has the Pydantic plugin |
| HTTP adapter | **FastAPI** + **Uvicorn**; **Pydantic v2** models generated from `optimization-api` by **datamodel-code-generator**, used only in the adapter; **orjson** for responses | Decided · Alpha | Async handlers for the pipe to the solver workers; validation and OpenAPI built in; generated models can't drift from the contract | **Flask:** no async, no built-in validation. **Litestar:** similar features, smaller community. **gRPC:** ADR-006 chose OpenAPI, and gRPC is harder to inspect with curl or Bruno |
| Settings | **pydantic-settings** (environment variables only, NFR-03) | Decided · Alpha | Typed settings, validated at startup | — |
| Domain numerics | **NumPy** | Decided · Alpha | Structs of arrays for the validator and for Numba | — |
| Product solver | **OR-Tools** Routing, pinned to an exact version: a time dimension and two capacity dimensions (weight, volume), disjunctions with priority-based drop penalties (BR-54), a fixed vehicle cost (FR-21), guided local search. Matrices registered with `RegisterTransitMatrix`, not Python callbacks ([plan §8](https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#8-or-tools-model)) | Decided ([ADR-005](../ADRs/ADR-005-or-tools-first-research-solver-for-re-optimization.md)) · Alpha | Every DispatchFlow rule maps to a standard construct, warm start is built in, and it's free. Registered matrices measured about 4.5 times faster than callbacks for the same search | **VROOM:** a fast C++ engine with OSRM built in, but its time windows are hard constraints and its objective has no terms for re-optimization stability or priority-weighted drops. **PyVRP in the product:** FR-286 keeps it as the study's reference solver. **Timefold:** Java, outside the Python research stack and its validator. **Hexaly or Gurobi:** commercial licences |
| Own solver | Clarke-Wright + ALNS with simulated-annealing acceptance (FR-42), hot loops in **Numba** | Decided (FR-42) · B | Numba compiles the insertion loop from Python syntax over NumPy arrays, which the team can read and test | **Cython:** a compile step per platform and C-like code. **Rust through PyO3:** fastest, but a fourth language; kept as the last resort ([plan §10.5](https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#105-speed-plan-q-17)). **Vectorized NumPy only:** the insertion search is sequential and branchy |
| ALNS outer loop | The **`alns`** package (MIT) for operator selection and simulated-annealing acceptance; our own construction, operators, route state and Numba kernels | Decided ([ADR-092](../ADRs/ADR-092-the-alns-package-runs-our-solvers-outer-loop.md)) · B | A tested, published implementation of the generic loop (about 300 lines saved), while everything that makes the solver DispatchFlow's stays ours. The advisor confirms that this counts as our solver. `alns` joins the licence allow-list (NFR-51) | **Writing the loop ourselves:** the fallback if the advisor wants every line owned |
| Tests | **pytest**, **pytest-cov**, **Hypothesis** | Decided · Alpha | Hypothesis generates instances for the property tests (NFR-82, NFR-170 to NFR-174) | **unittest:** more boilerplate, no fixtures |
| Architecture checks | **import-linter** | Decided · Alpha | [2.2](#22-enforcement) | — |
| Profiling | **py-spy** | Decided · B | Samples a running process with no code changes | **cProfile:** slows the code it measures |
| Observability | OpenTelemetry instrumentation for FastAPI; **structlog** JSON logs carrying the trace ID | Decided · Pilot | One trace from the web app to the solver | — |
| Runtime image | `python:3.14-slim` with uv, non-root | Decided · Pilot | Small, official, with glibc for the scientific wheels | **Alpine:** musl breaks or slows the scientific wheels |

### 7.1 The hexagon

- **Domain:**
  - Value objects: `Problem` and `Solution`, frozen dataclasses over NumPy arrays; `TravelTimeModel`;
    `PenaltyScale`.
  - `Problem.validate`: the single definition of feasibility (BR-15, BR-42), including the
    re-optimization conventions of BR-23 and BR-24.
  - `Problem.cost`: the shared objective (BR-09, BR-56, BR-115, BR-120).
  - `Problem.explain_drop`: every drop reason, from no location to displaced (FR-144).
  - `ProblemFactory`: builds a `Problem` from a request, with the re-optimization conventions
    ([ADR-050](../ADRs/ADR-050-re-optimization-conventions-live-only-in-the-python-problem-factory.md)).
  - `TravelTimeModel` is the only place bucket factors and the buffer are applied ([ADR-048](../ADRs/ADR-048-travel-times-from-buckets-and-a-buffer-with-no-live-traffic.md)).
    FR-322's upper bound stays. M1 solves the demo day both at the upper bound and at per-bucket
    times; if the upper bound needs an extra vehicle, or drops an order the per-bucket solve
    serves, an ADR decides whether FR-322 changes.
- **Application:** the `Solve`, `Reoptimize`, `Evaluate` and `Baseline` use cases.
- **Solver port:** `solve(problem, start, budget, seed) -> Solution` ([ADR-044](../ADRs/ADR-044-solver-port-takes-start-budget-and-seed-and-problem-judges.md)). `start` is an optional warm-start
  solution. `budget` is a time limit, an iteration budget or a solution limit, because property tests and
  end-to-end tests must not run on wall-clock limits (NFR-82, NFR-175).
- **Solver adapters,** under `adapters/outbound/solvers/` (§4):
  - `ortools`: warm start through `ReadAssignmentFromRoutes` and `SolveFromAssignmentWithParameters`
    (FR-280)
  - `alns`: the DispatchFlow solver, which repairs the current routes on re-optimization (FR-273), with
    optional sampled iteration logging that is off in the product (FR-163, FR-287)
  - `nearest_neighbour`: the baseline (FR-39, FR-342)
  - `pyvrp`: harness only, never in the product (FR-286); import-linter and the dependency-boundary
    test (environment and lockfile) enforce it

### 7.2 Process model

One HTTP process, and one solver worker process per lane, started with `spawn`; a solve overrunning its
limit by 30 seconds is killed as a backstop (BR-32, BR-84). Validation runs inside the limit (NFR-119).
Numba kernels compile when a worker starts, with `cache=True`. The matrix arrives in the request, so the
solver stays a pure function (NFR-13). Recorded in [ADR-051](../ADRs/ADR-051-one-solver-worker-per-lane-killed-30-seconds-past-its-limit.md).

---

## 8. Research harness

`src/research` is a primary adapter over the optimization service's application layer.

| Concern | Choice | Status | Why | Over |
|---|---|---|---|---|
| Command line | **Typer**; the whole study reruns from one command (`just study`) (NFR-121) | Decided · B | Click underneath, with commands declared by type hints | **argparse:** verbose. **Click directly:** fine; Typer is less code |
| Configuration | YAML study files, validated by Pydantic (NFR-122) | Decided · B | Readable by the advisor; validated before a 28-hour run starts | **Hydra:** powerful composition the studies don't need |
| Instances | **vrplib** reads Solomon and Gehring-Homberger instances and best-known solutions; demands read as weights (FR-43, FR-275, FR-390) | Decided · Alpha (validator tests), B | The standard reader for these formats | **A hand-written parser:** easy to get subtly wrong |
| Reference solver | **PyVRP** (FR-51) | Decided · B | A state-of-the-art open-source solver to compare against | — |
| Results | **pandas** + **pyarrow**, stored as Parquet beside their configuration (NFR-122) | Decided · B | Thousands of rows, and SciPy and matplotlib take pandas directly | **polars:** faster, which a few thousand rows don't need. **CSV:** loses types |
| Statistics | **SciPy** (Wilcoxon signed-rank, paired and one-sided); the rank-biserial effect size computed in code and unit-tested (FR-165, FR-288) | Decided ([ADR-091](../ADRs/ADR-091-study-design-for-the-solver-comparison.md)) · B | Standard, and the test choice is explicit in code | **statsmodels:** more than these two calculations need |
| "Within 5%" | Paired one-sided Wilcoxon tests (non-inferiority): NFR-24 on (gap − 0.05) and NFR-117 on (ours − OR-Tools) with a margin of 1% of OR-Tools' mean objective, and a primary limit of 5 s. Confirm them with the advisor before the first measured run | Decided ([ADR-091](../ADRs/ADR-091-study-design-for-the-solver-comparison.md)) · B | A plain difference test can't show that one solver is within 5% of another | **A two-sided test on the gap:** answers "are they different", not "is it close enough" |
| Figures | **matplotlib**, generated by scripts so they rerun with the study | Decided · B | Full control over publication figures | **seaborn or plotly:** extra layers for static figures |
| Exploration | **JupyterLab**, with **jupytext** so notebooks are stored as reviewable scripts. Final figures never come from a notebook | Decided · B | Exploration without unreviewable JSON in the repository | **Plain notebooks:** diffs are unreadable |
| Provenance | A manifest per run: commit, configuration hash, seeds, CPU model, instance type, package versions. The 30 solved plans of the study are stored with its inputs, and their hash goes in the manifest ([ADR-091](../ADRs/ADR-091-study-design-for-the-solver-comparison.md)). Pilot-derived data and results stay outside the repository, which keeps only their hash and configuration (NFR-62, NFR-115, NFR-122, NFR-50) | Decided · B | Enough to rerun any result | **MLflow or DVC:** a server or a storage layer for what a manifest and Parquet files already give |
| Synthetic days | **pyosmium** draws real addresses from the OSM extract; **Faker** `he_IL` names; phones from the stated range (FR-45, NFR-207) | Decided · Alpha | The Alpha, every test and every demo run on these days | **Random coordinates:** not on roads, so OSRM snaps them oddly |
| Disruption simulation | A plain seeded event loop, using the same recorded seed for delays, breakdowns, new orders, cancellations and late reports (FR-46, FR-167) | Decided · B | A sorted list of events is easy to make deterministic and to test | **SimPy:** a process framework for a simulation with a few dozen ordered events |
| Ablation study | Operator switches and fixed-weight mode in the `alns` adapter's configuration (FR-164) | Decided · B | — | — |
| Hardware | Non-burstable compute instances of one fixed type, one run at a time per machine. The static Solomon study alone is about 28 hours of runs (56 instances × 10 runs × 3 solvers × 60 s). The Gehring-Homberger runs (200 customers, and 400 if time remains) and the scaling study (3 runs per size) come on top. The work is split across identical machines that are started for the study and stopped after (NFR-111). The instance type is named when the study starts and goes in each run manifest. The machines live in a separate AWS account owned by the team, in `il-central-1`, not the company's account ([ADR-080](../ADRs/ADR-080-team-accounts-stand-in-when-there-is-no-company.md)). Estimate, not measured: 60–100 machine-hours, about ₪20–40 for the whole study, from US list prices; check the `il-central-1` price when the study starts | Decided · B | Burstable instances throttle unpredictably, which would put noise into timed results | **Laptops:** different CPUs, and other work running |

---

## 9. Routing and geodata

| Concern | Choice | Status | Why | Over |
|---|---|---|---|---|
| Road data | **Geofabrik**'s Israel and Palestine OSM extract (`.osm.pbf`) and its boundary polygon, refreshed monthly; the team is told past 60 days (NFR-129) | Decided · Alpha | Free, current, and one file feeds routing, tiles and the service area | **Commercial road data:** licences, and no better coverage of Israeli streets for this use |
| Data tools | **osmium-tool** (clip the extract, read its timestamp for NFR-43); **pmtiles** CLI (extract tiles) | Decided · Alpha | Standard, scriptable | — |
| Road routing | **OSRM 26.x** (official image), car profile, contraction hierarchies. `table` gives the matrix (NFR-57); `route` gives shapes for the map (FR-24). `--max-table-size` raised above its default of 100. Processed in CI, shipped as files | Decided · Alpha | The fastest open-source matrix queries: a 216-point table in well under NFR-57's 5 s | **Valhalla:** time-dependent and truck costing, but slower matrices; reconsider only if vehicle-type road limits (FR-118) are built. **GraphHopper:** its fast matrix is part of the paid service, not the open-source engine. **Hosted matrix APIs** (Google, Mapbox, HERE): priced per element, about 47,000 per solve, and the data leaves Israel |
| Service area | The extract's boundary polygon, checked with JTS in the domain. The whole extract is used until a company is known; then it is clipped to the company's area with osmium in the monthly refresh (FR-320) | Decided · B | A point-in-polygon check in memory. The extract also covers the Palestinian territories, which the clip removes once there is a company | **PostGIS:** a spatial extension for one check |
| Geocoding | **GovMap**, through the `Geocoder` port. Before the geocoding code is built in B, a team member reads GovMap's terms and gets its API token. If the terms forbid keeping its coordinates on customers and depots, or showing them on our map, self-hosted **Nominatim** replaces it behind the same port. FR-139 suggestions come from GovMap if its terms allow, otherwise from **Photon**, self-hosted on the VM | Decided ([ADR-085](../ADRs/ADR-085-govmap-geocodes-addresses-with-nominatim-as-the-fallback.md)) · B | Results can be stored and shown on our own map, if the terms allow it. Not yet checked: GovMap's price, request limits and coordinate system (probably the Israeli grid, ITM, which the adapter would convert to WGS84; unverified). When geocoding is built, one address list runs through GovMap alone: Hebrew, Arabic and transliterated addresses (Q-83), each with a known location. Its results set FR-63's partial and ambiguous rules and show whether FR-140's normalization is needed | **Self-hosted Nominatim:** the fallback, about 2 GB of the VM's 16 GB. **Google:** its terms forbid showing its results on a non-Google map and limit cached coordinates to 30 days, which settles NFR-148. **Mapbox:** its standard results must appear on a Mapbox map and can't be stored. **HERE:** storage limited to 30 days without a paid licence |
| Web map | **MapLibre GL JS 6** through **react-map-gl 8** | Decided ([ADR-084](../ADRs/ADR-084-maplibre-in-both-apps-with-self-hosted-protomaps-tiles.md)) · Alpha | WebGL holds NFR-109 at 200 stops and 15 routes; draggable markers for hand-placed locations (FR-14); the same vector style as the phone app | **Leaflet:** DOM markers struggle at 200 stops and 15 routes, and it can't render the vector style. **OpenLayers:** capable, but no React Native counterpart, so two map stacks. **Mapbox GL JS:** a proprietary licence and access tokens. **Google Maps:** terms, and data leaving Israel |
| Phone map | **@maplibre/maplibre-react-native 11** on native; a `Map.web.tsx` using maplibre-gl for the phone app's web build (NFR-220) | Decided ([ADR-084](../ADRs/ADR-084-maplibre-in-both-apps-with-self-hosted-protomaps-tiles.md)) · B | Same style and layers as the web app; answers Q-03; NFR-109 (30 fps) binds it too | **react-native-maps:** platform map SDKs with a different look, and no vector style |
| Tiles | **Protomaps** basemap PMTiles extract of Israel, served by Caddy with range requests. Styles from **@protomaps/basemaps** with Hebrew (`name:he`) labels; fonts self-hosted; OSM attribution shown (NFR-18). Rebuilt monthly with OSRM | Decided ([ADR-084](../ADRs/ADR-084-maplibre-in-both-apps-with-self-hosted-protomaps-tiles.md)) · Alpha | One static file, no tile server, no vendor, and tiles stay in Israel | **OpenMapTiles + tileserver-gl:** another running service. **Hosted tiles** (MapTiler, Stadia): a vendor, usage limits, and requests leaving Israel |
| Hebrew labels on the web | MapLibre's right-to-left text plugin (`@mapbox/mapbox-gl-rtl-text`), served from the VM | Decided · Alpha | Hebrew renders correctly; check native label rendering on real phones (NFR-140) | — |
| Data formats | OSM PBF in; GeoJSON for map layers; encoded polylines from OSRM, decoded in the core | Decided · Alpha | Each is the native format of the tool that uses it | — |

---

## 10. Web app

| Concern | Choice | Status | Why | Over |
|---|---|---|---|---|
| Base | **React 19**, **TypeScript 6.0** (strict), **Vite 8**; a single-page app served by Caddy from the same origin as the API (NFR-15, NFR-58, NFR-67) | Decided · Alpha | React lets the web and phone apps share packages and skills. TypeScript 7 (the native compiler) is out, but typescript-eslint supports only versions below 6.1, so stay on 6.0 until it does | **Next.js:** nothing needs server rendering, and it adds a server. **Angular:** heavier, and nothing shared with React Native. **Vue or Svelte:** nothing shared with the phone app. **TypeScript 7:** loses type-aware lint rules for now |
| Server state | **TanStack Query** (not persisted) over the generated API client | Decided · Alpha | Caching, refetch on stream events and mutations, all built in | **RTK Query:** brings Redux. **SWR:** fewer mutation features |
| Routing | **TanStack Router** | Decided · Alpha | Typed route parameters and search parameters (`/plans/$date`), and the same family as Query and Table. Deciding before the first screen saves a rewrite | **React Router:** fine too, with weaker typing of parameters |
| UI kit | **Mantine 9**, with `@mantine/dates`, `@mantine/notifications`, `@mantine/charts` (NFR-27, NFR-46, FR-41) | Decided · Alpha | Right-to-left through one `DirectionProvider`, plus dates, notifications and charts in one kit | **MUI:** right-to-left needs an Emotion plugin set up by hand, and its date pickers are a separate product. **shadcn/ui:** copy-in components on Radix and Tailwind, more assembly. **Ant Design:** heavy, with an enterprise look |
| Icons and fonts | **Tabler Icons**; a Hebrew-capable font (Heebo or Rubik), self-hosted through **Fontsource** (NFR-27) | Decided · Alpha | Fonts come from our origin, not a font CDN | **Google Fonts CDN:** a request to an outside service on every load |
| Tables | **TanStack Table** (FR-151) | Decided · Alpha | Headless, so right-to-left and accessibility stay in our hands | **AG Grid:** capable, but heavy, and it styles everything its own way |
| Forms | **React Hook Form** + **Zod 4** (schemas shared with the phone app) (NFR-69) | Decided · Alpha | One schema validates in both apps | **Formik:** slower development and maintenance. **TanStack Form:** newer, with fewer examples |
| UI state | **Zustand**: one selection shared by map, timeline and table (FR-150) | Decided · Alpha | A small store with no boilerplate | **Redux Toolkit:** boilerplate for a few fields. **React context:** re-renders the whole workspace on every selection |
| Drag and drop | **dnd-kit**, with a non-drag alternative for every drag (WCAG 2.2 SC 2.5.7): "Move to route…" for stops, tap-to-place for hand-placed locations (FR-14, FR-22, NFR-46) | Decided · Alpha (stretch) | Keyboard sensors and announcements built in | **react-beautiful-dnd:** deprecated. **Pragmatic drag and drop:** lighter, with fewer accessibility helpers |
| Timeline | Custom SVG with **d3-scale**; items focusable; the table is the accessible path (FR-25, FR-151) | Decided · Alpha | Windows as bands, violations outlined, right-to-left | **vis-timeline:** no right-to-left, weak accessibility. **Gantt libraries:** built around project tasks |
| Dates and times | `Intl` with `timeZone: 'Asia/Jerusalem'` for display; **date-fns 4** for arithmetic such as time left in a window (NFR-68, FR-154) | Decided · Alpha | `Intl` formats Hebrew dates correctly; date-fns is tree-shakable | **Day.js:** time zones through plugins. **Temporal:** not yet in every browser the drivers use |
| Phones | **libphonenumber-js** for input formatting; the server stays the authority (FR-377) | Decided · Alpha | Same rules as the server | — |
| Languages | **i18next** + react-i18next; `<bdi>` for mixed-direction text; **@hebcal/core** for holidays (NFR-27, NFR-136, FR-80) | Decided · Alpha | One i18n setup for both apps | **FormatJS:** ICU messages are richer, but i18next is simpler and works the same in React Native |
| Event stream | `fetch` + **eventsource-parser** (ADR-026, FR-347) | Decided · Alpha | The browser's `EventSource` can't send an `Authorization` header | **A token in the URL:** ends up in logs |
| Proof capture | **signature_pad** for signatures; a live camera stream through `getUserMedia` for photos, never a file picker (NFR-222, FR-30) | Decided · Alpha (signatures), B (photos) | A *delivered* report needs a proof (FR-367), and a signature works with a mouse. A file picker would accept a photo from the gallery | — |
| Location (driver screens) | Browser Geolocation API while the page is visible (FR-243) | Decided · B | The phone app takes over in the background | — |
| Help and guides | Markdown guides shown in the app with **react-markdown** (NFR-79) | Decided · B | Guides live with the code | — |
| Printing | Print stylesheet; the browser handles Hebrew right to left (FR-72) | Decided · B | No PDF engine needed | **Server-side PDF:** Hebrew shaping and fonts to get right |
| Errors | The **Sentry** browser SDK, pointed at **GlitchTip** on the VM, personal data off; `beforeSend` and `beforeBreadcrumb` scrub URL fragments and query strings; source maps upload from CI to GlitchTip, checked against GlitchTip's version at Pilot (NFR-74, NFR-163) | Decided ([ADR-079](../ADRs/ADR-079-client-errors-and-crashes-go-to-self-hosted-glitchtip.md)) · Pilot | Reports stay in Israel, so NFR-209 needs no exception, and GlitchTip speaks the Sentry SDK's protocol | **Hosted Sentry:** reports leave Israel and need an NFR-209 exception |
| Lint and format | **ESLint** (typescript-eslint, react-hooks, jsx-a11y), **Prettier** (NFR-46) | Decided · Alpha | Type-aware rules and the accessibility rule set are the reasons to lint | **Biome:** much faster, and since version 2 it has type-aware rules of its own, but they cover a subset of typescript-eslint's, and its type inference is its own reimplementation rather than the TypeScript compiler. Revisit when its type-aware rules mature |
| Unit and component tests | **Vitest**, **React Testing Library**, **MSW** | Decided · Alpha | Same config as Vite | **Jest:** a second transform pipeline next to Vite |
| Bundle size | A bundle visualizer (rollup-plugin-visualizer; check it with Vite 8 in M0) (NFR-58) | Decided · Jan | Finds what makes the first load slow | — |

**Structure:** `src/features/<context>` mirrors the bounded contexts. Only `@dispatchflow/api-client`
talks to the server. The client never decides feasibility: violations come from the validator. Sign-out sends
`Clear-Site-Data` and deletes the refresh cookie, so nothing an account received stays in the
browser, driver screens included (Q-92, Q-98, [ADR-076](../ADRs/ADR-076-admin-and-dispatcher-data-stays-in-memory-on-devices.md)).

---

## 11. Phone app

Built from Semester B; the Alpha's driver screens run in the browser.

| Concern | Choice | Status | Why | Over |
|---|---|---|---|---|
| Base | **Expo SDK 58** (React Native 0.88), TypeScript (strict). Check every native dependency against the SDK first (NFR-132) | Decided ([ADR-035](../ADRs/ADR-035-both-apps-serve-every-role.md)) · B | One TypeScript codebase for Android and iOS, sharing packages with the web app; background location, an encrypted store and push are all needed and all available | **Bare React Native:** native projects to maintain, and no EAS. **Flutter:** Dart, so nothing shared with the web app. **Native Kotlin and Swift:** two apps. **A PWA:** no background location on iOS |
| Development builds | **expo-dev-client** | Decided · B | Expo Go can't load MapLibre | — |
| Builds and distribution | **EAS Build** and **EAS Submit** to TestFlight and Google Play internal testing, on the company's accounts, or the team's own if there is no company by M13: about $99 a year for the Apple Developer Program (≈₪320) and $25 once for Google Play (≈₪80), about ₪400 in the first year (estimate, not measured; [ADR-080](../ADRs/ADR-080-team-accounts-stand-in-when-there-is-no-company.md)). Profiles: development, preview, production, each with its own bundle ID (NFR-186, [ADR-028](../ADRs/ADR-028-driver-app-through-test-tracks-with-a-device-checklist.md)) | Decided ([ADR-080](../ADRs/ADR-080-team-accounts-stand-in-when-there-is-no-company.md)) · B | iOS builds without a Mac on every desk | **Local Xcode and Gradle builds:** a Mac for every iOS build |
| Updates without a store build | **EAS Update** for JavaScript-only fixes; the app reports its version and update ID with every call (NFR-44, NFR-93, NFR-134); native changes still go through store builds | Decided ([ADR-081](../ADRs/ADR-081-eas-update-for-javascript-only-fixes-during-the-pilot.md)) · Pilot | A same-day fix during the pilot | **Store builds only:** a day or more per fix |
| Navigation and links | **Expo Router**; Universal Links and App Links (NFR-213) | Decided · B | File-based routes and deep links, built on React Navigation | **React Navigation directly:** the same engine, with deep links configured by hand |
| Background location | **expo-location** + **expo-task-manager**, one position per 30 s. `timeInterval` is Android-only, so on iOS the task throttles ([plan §19](https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#19-phone-app)). Location stops at sign-out, at a hand-over and when the driver is marked unavailable (Q-92, NFR-30). Android's foreground-service notification doubles as the "location is on" sign (NFR-149). If manufacturers' battery savers kill it in the device checklist, Transistor Software's background-geolocation library (paid licence for Android release builds) is the fallback (FR-31, NFR-47) | Decided · B | First-party and free | **The Transistor library first:** better on aggressive Android phones, but paid; kept as the fallback |
| Encrypted storage | **expo-sqlite** built with SQLCipher (`useSQLCipher`); the key in **expo-secure-store**. Holds driver data only: the route cache and the report outbox. Admin and Dispatcher data stays in memory ([ADR-076](../ADRs/ADR-076-admin-and-dispatcher-data-stays-in-memory-on-devices.md)). A refused refresh wipes the route data and keeps the outbox; 14 days offline wipes the route data and the queued reports and proofs (Q-92). Never AsyncStorage or a persisted query cache (NFR-39, FR-69) | Decided ([ADR-076](../ADRs/ADR-076-admin-and-dispatcher-data-stays-in-memory-on-devices.md)) · B | First-party, encrypted, and SQL for the outbox's ordering | **op-sqlite:** faster and also supports SQLCipher, but third-party. **WatermelonDB:** a sync framework we don't need. **MMKV:** key-value only. **AsyncStorage:** unencrypted |
| Proof photos | **expo-camera** (also barcodes, FR-98) and **expo-image-manipulator**. Bytes move into the encrypted database and temporary files are deleted at once (FR-30, NFR-39, NFR-204) | Decided · B | Photos never reach the gallery | **The system image picker:** accepts old photos |
| Signature | **react-native-signature-canvas** (FR-30) | Decided · B | — | — |
| Reconnect | **@react-native-community/netinfo** triggers the outbox flush (FR-246) | Decided · B | — | — |
| Alerts | **expo-notifications** through Expo's push service (an accepted exception, [ADR-077](../ADRs/ADR-077-push-builds-and-app-stores-are-accepted-exceptions-to-hosting-in-israel.md)), with its push access-token security on; payloads carry no personal data (FR-71, NFR-218). Admin and Dispatcher alerts arrive by push in the background and by the foreground stream while the app is open. Push tokens are removed at sign-out, at revocation and when Expo reports the device gone (Q-92) | Decided ([ADR-083](../ADRs/ADR-083-admins-and-dispatchers-get-alerts-by-stream-and-push-in-the-phone-app.md)) · B | One API for APNs and FCM | **FCM and APNs directly:** two providers to configure |
| Foreground stream | **react-native-sse** (ADR-026) | Decided · B | Same stream as the web app | — |
| Server state and forms | **TanStack Query** (not persisted); **React Hook Form** + **Zod** | Decided · B | Same as the web app | — |
| UI kit and icons | **React Native Paper**; **@expo/vector-icons** (NFR-71, NFR-137, NFR-138) | Decided · B | Accessible components, right-to-left support, stable | **Tamagui:** powerful, with more to set up (its configuration and optional compiler). **gluestack-ui:** younger, with breaking changes between major versions. **NativeBase:** deprecated |
| Languages | **i18next** with **expo-localization**. Switching to or from Hebrew calls `I18nManager.forceRTL` and reloads the app (FR-124, NFR-27) | Decided · B | Same strings package as the web app | — |
| Device details | **expo-application** (version for NFR-93), **expo-linking** | Decided · B | — | — |
| Navigate | Waze (`https://waze.com/ul?ll={lat},{lon}&navigate=yes`) and Google Maps (`https://www.google.com/maps/dir/?api=1&destination={lat},{lon}`) with stored coordinates (FR-28, FR-325) | Decided · B | Drivers already use them; links need no SDK | **Turn-by-turn inside the app:** a large build for what Waze already does |
| Errors and crashes | The **Sentry** React Native SDK, pointed at **GlitchTip**, personal data off; `beforeSend` and `beforeBreadcrumb` scrub URL fragments and query strings; source maps upload from EAS, checked at Pilot (NFR-74, NFR-163) | Decided ([ADR-079](../ADRs/ADR-079-client-errors-and-crashes-go-to-self-hosted-glitchtip.md)) · Pilot | As in the web app | **Hosted Sentry:** as in the web app |
| Lint and tests | **ESLint** (with react-native-a11y rules), **Prettier**; **jest-expo** + **React Native Testing Library**; **Maestro** flows for the repeatable device-checklist steps, adopted once the device checklist ([ADR-028](../ADRs/ADR-028-driver-app-through-test-tracks-with-a-device-checklist.md)) has been run by hand for two releases (free) | Decided · B; Maestro Optional | — | **Detox:** heavier to set up than Maestro |

**The report outbox is a small hexagon of its own.** A `ReportQueue` port sits over a SQLCipher
adapter. Each report carries an idempotency key scoped to the signed-in user (NFR-65), a per-route
sequence number (FR-247), and the device time and the send time, for clock correction (BR-44, BR-114).
Reports stay queued until the server acknowledges them (FR-248). The sync is recorded in [ADR-087](../ADRs/ADR-087-driver-reports-sync-through-an-idempotent-outbox.md).

**Admin and Dispatcher screens** are native, with parity to the web app ([ADR-035](../ADRs/ADR-035-both-apps-serve-every-role.md), FR-120).

---

## 12. Shared TypeScript packages

| Concern | Choice | Status | Why | Over |
|---|---|---|---|---|
| Workspaces | **pnpm** workspaces; the hoisted linker if Metro has trouble resolving dependencies | Decided ([ADR-039](../ADRs/ADR-039-one-pnpm-workspace-for-both-apps-and-shared-packages.md)) · Alpha | Strict dependencies catch undeclared imports; fast installs | **npm or Yarn workspaces:** looser, so an undeclared dependency works by accident until it doesn't. **Nx or Turborepo:** build caching that a repository this size doesn't need yet |
| API client | `@dispatchflow/api-client`: types and client generated from `client-api` with **openapi-typescript** and **openapi-fetch** | Decided · Alpha | Types only, with a tiny runtime | **orval:** generates hooks and more code. **openapi-generator's TypeScript output:** bulky classes |
| Other packages | `@dispatchflow/schemas` (Zod forms for both apps), `@dispatchflow/i18n` (Hebrew and English strings, with text and a next step for every error code, NFR-144), `@dispatchflow/map-style` (one MapLibre style and the route colours, FR-148, FR-240), `@dispatchflow/formatters` (dates, times, distances, weights, volumes and shekels, NFR-68), `@dispatchflow/eslint-config`, `@dispatchflow/tsconfig` | Decided · Alpha (all but the phone-only parts) | One definition of each, used by both apps | **Copies in each app:** they drift |

---

## 13. Outside services and where data goes

Input to the data inventory (NFR-72, NFR-217).

| Service | Used for | Receives | Held in | Status |
|---|---|---|---|---|
| AWS EC2, EBS, S3, SSM (`il-central-1`) | VM, disk, backups, secrets | Everything | Israel | Decided ([ADR-078](../ADRs/ADR-078-hosting-on-one-aws-vm-in-tel-aviv-built-with-terraform.md)) |
| Amazon SES (`il-central-1`) | Account emails | Users' email addresses; link tokens | Israel | Decided ([ADR-078](../ADRs/ADR-078-hosting-on-one-aws-vm-in-tel-aviv-built-with-terraform.md)) |
| Amazon Route 53 | DNS | Host names only | Global | Decided ([ADR-078](../ADRs/ADR-078-hosting-on-one-aws-vm-in-tel-aviv-built-with-terraform.md)) |
| Let's Encrypt | TLS certificates | Host names only | Outside Israel | No personal data |
| SMS provider | Messages to recipients | Recipient phones; message text; replies | Confirm under Q-96 | Decided: an Israeli provider ([ADR-031](../ADRs/ADR-031-sms-through-an-israeli-provider-for-recipient-messages.md)); vendor open until M12 (Q-96, [22](#22-open-decisions)) |
| GovMap | Geocoding | Customer and depot addresses | Israel | Decided ([ADR-085](../ADRs/ADR-085-govmap-geocodes-addresses-with-nominatim-as-the-fallback.md)); its terms are checked before the geocoding code is built |
| Expo (EAS, push) → APNs, FCM | Builds, updates, phone alerts | App code; push tokens; "something changed" only (NFR-218) | Outside Israel | Accepted exception ([ADR-077](../ADRs/ADR-077-push-builds-and-app-stores-are-accepted-exceptions-to-hosting-in-israel.md)) |
| Waze, Google Maps (on the driver's phone) | Navigate (FR-28, FR-325) | The stop's coordinates only, never a name, phone or address text; opened on the driver's tap. While the driver navigates, the map app also receives their live position | Outside Israel | Accepted exception, the fourth of NFR-209 (NFR-271; [ADR-077](../ADRs/ADR-077-push-builds-and-app-stores-are-accepted-exceptions-to-hosting-in-israel.md)) |
| TestFlight, Google Play | App distribution | Testers' accounts | Outside Israel | Accepted exception ([ADR-077](../ADRs/ADR-077-push-builds-and-app-stores-are-accepted-exceptions-to-hosting-in-israel.md)); the rest of Q-48 is open |
| GitHub (repository, Actions, GHCR) | Code, CI, images | Code and images only; never company data | Outside Israel | No personal data (NFR-50) |
| Mend's hosted Renovate app | Dependency update pull requests | Code only, with write access to the repository; never company data | Outside Israel | No personal data (NFR-50, [ADR-046](../ADRs/ADR-046-renovate-replaces-dependabot-and-targets-dev.md)) |
| Amazon Route 53 health check, CloudWatch, SNS | Outage alerts | The health endpoint's address; the alerted member's email | AWS | Decided ([ADR-078](../ADRs/ADR-078-hosting-on-one-aws-vm-in-tel-aviv-built-with-terraform.md)) |
| Amazon SNS (`il-central-1`) | Alert email from Alertmanager | Alert text, with no personal data (NFR-268); the alerted members' emails | Israel | Decided ([ADR-078](../ADRs/ADR-078-hosting-on-one-aws-vm-in-tel-aviv-built-with-terraform.md)) |
| The team's chat service | Alerts to the team's channel | Alert text, with no personal data (NFR-268) | Not yet known | Proposed: the team names it before the pilot ([22](#22-open-decisions)) |
| Healthchecks.io | Backup alerts from outside AWS, a second and slower warning | Job pings | Anywhere | Decided; no personal data |

Map tiles, routing, GlitchTip, the monitoring stack and, only if needed, Nominatim (if GovMap's terms fail) and Photon (if FR-139 needs it) run on the VM, so no outside service is involved.

---

## 14. Environments

| Environment | Where | Data | Outside services | Purpose |
|---|---|---|---|---|
| Local | Each laptop, `just dev` | Synthetic (FR-45) | All faked (Mailpit, WireMock); no outside network | Development, and the Alpha demo |
| CI | GitHub Actions runners | Synthetic, fixed seeds | All faked; controllable clock (NFR-175) | Tests on every change |
| Usability | A small separate VM on the team's AWS account, started only for study rounds (a few shekels a round) | Synthetic | Fake SMS (NFR-206); real email | Sessions with classmates and a stand-in dispatcher (FR-344) |
| Production (pilot) | The VM in `il-central-1`, on the company's AWS account | The company's | Real; the only holder of the SMS credential (NFR-206) | The pilot (FR-47) |

With no company by M13 (a staged pilot), the team's own AWS account, the one that holds the research machines (§8), also runs the production-like VM: about two weeks of the 16 GB VM, ≈₪250 with VAT (estimate, not measured; [ADR-080](../ADRs/ADR-080-team-accounts-stand-in-when-there-is-no-company.md)). With a company, production stays on its account, which holds only the product.

---

## 15. Infrastructure and operations

All of this is adopted before the pilot (M13), not before the Alpha.

| Concern | Choice | Status | Why | Over |
|---|---|---|---|---|
| Hosting | **AWS Israel (Tel Aviv), `il-central-1`**, one VM built with Terraform, on the company's AWS account (NFR-14, NFR-209, NFR-52) | Decided ([ADR-078](../ADRs/ADR-078-hosting-on-one-aws-vm-in-tel-aviv-built-with-terraform.md)) · Pilot | In Israel, and SES runs in the same region, so account emails stay in Israel without another provider | **Azure Israel Central or Google Cloud `me-west1`:** also in Israel, but email would need a separate provider. **Hetzner or DigitalOcean:** no Israeli region |
| AWS accounts | The company's account holds only the product. The team's own account holds the research machines (§8) and, when there is no company, the usability VM and a production-like VM for a staged pilot (§14) | Decided ([ADR-080](../ADRs/ADR-080-team-accounts-stand-in-when-there-is-no-company.md)) · Pilot | A staged pilot, with no company by M13, still needs a production-like deployment | — |
| Infrastructure as code | **Terraform** (OpenTofu is a drop-in alternative), state in S3: VM, disk, Elastic IP, security group, S3 buckets, IAM role, SES identity, Route 53 zone, budget alerts, EBS snapshot policy. **cloud-init** installs Docker and starts the stack on a fresh VM (NFR-125, NFR-183, NFR-166) | Decided · Pilot | About 15 resources, rebuilt the same way every time, which a restore drill needs | **Pulumi or CDK:** infrastructure in a general-purpose language for 15 resources. **Clicking in the console:** not reproducible (NFR-125) |
| VM size | 4 vCPU / 16 GB (`t3a.xlarge`, about $0.17 an hour, around $125 a month on demand), a burstable instance in unlimited-credit mode, so solves are never throttled. M3 checks the size against OSRM's measured memory. Not yet checked: that `t3a` is offered in `il-central-1`; `t3` costs about 10% more and still fits the cap | Decided ([ADR-078](../ADRs/ADR-078-hosting-on-one-aws-vm-in-tel-aviv-built-with-terraform.md)) · Pilot | Measured memory (OSRM, the core, the optimizer) decides, not a guess; 16 GB also holds the Grafana stack (§16) | **2 vCPU / 8 GB** (`t3.large` class, about $0.096 an hour, around $70 a month) with memory caps and OpenObserve: reopened only if the measured bill passes the ₪550 cap (NFR-52). **Non-burstable instances:** steadier, at about twice the price, for a load that is mostly idle |
| Operating system | **Ubuntu Server 26.04 LTS**; **unattended-upgrades** for security patches; **chrony** on AWS Time Sync, because the server's clock judges staleness and device clock offsets (FR-74, BR-114) (NFR-127) | Decided · Pilot | Long support; the same family as the laptops' WSL | **Amazon Linux 2023:** fine too, but different from what the team uses locally |
| Network | Security group open only on 80 and 443; no SSH port (**SSM Session Manager** instead, limited to named members, whose accounts require MFA, NFR-265) (NFR-26). No container can use the VM's full AWS role: each container that calls AWS gets only what it needs (send email, publish alerts, write backups). The M13 Terraform pull request designs the mechanisms against these goals, and gets an infrastructure review and a security review before it merges ([ADR-078](../ADRs/ADR-078-hosting-on-one-aws-vm-in-tel-aviv-built-with-terraform.md)) | Decided · Pilot | Nothing to brute-force | **SSH with keys:** an open port and keys to manage |
| Containers | **Docker Engine** + Compose v2: `caddy`, `migrate` (one-shot Flyway as the owner role, before `core` starts), `core`, `optimizer`, `postgres`, `osrm`, `glitchtip` (with its own database, and CPU and memory limits), `nominatim` (only as GovMap's fallback), `photon` (only if FR-139 needs it), `otel-collector`, the monitoring backend (Prometheus, Alertmanager, Loki, Tempo, Grafana) and `backup`. Each has a health check, a memory limit and a non-root user (NFR-16, NFR-160, NFR-208) | Decided · Pilot | One file describes production, the same one the laptops use | **Kubernetes or k3s:** a cluster for one VM. **Nomad:** the same |
| Images | Temurin 25 JRE (core), copying in the jar CI builds, so jOOQ's generated code stays out of the repository; `python:3.14-slim` (optimizer); official `postgres:18`, OSRM and `caddy` images; all pinned by digest from January, with Renovate (NFR-54) | Decided ([ADR-047](../ADRs/ADR-047-images-pinned-by-digest-and-core-on-temurin-25-jre.md)) · Pilot | — | — |
| Domain and TLS | A domain registered for DispatchFlow, DNS in **Route 53**; certificates from Let's Encrypt through Caddy; SPF, DKIM and DMARC for SES (NFR-26, NFR-213) | Decided ([ADR-069](../ADRs/ADR-069-sign-in-and-transport-security.md)) · Pilot | — | — |
| Reverse proxy | **Caddy**: automatic TLS, HTTP/2 and HTTP/3 (so several open event streams don't hit the browser's per-host connection limit), HSTS, frame-ancestors, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, and a Content Security Policy with `base-uri 'none'; form-action 'self'; object-src 'none'` and a `connect-src` that names GlitchTip's address (M0 checks that the web app renders under it: Mantine injects a `<style>` element that may need a nonce or hash); serves the web app, PMTiles and the app-link files; exposes only GlitchTip's ingest paths, on GlitchTip's own host name and never a path of the app's host, with a request-body size cap and a cap on the storage volume, and GlitchTip's own event throttling if the pinned version has it (checked at the pilot); its UI is reached through SSM port forwarding; no buffering on event streams (NFR-26, NFR-55, NFR-213) | Decided ([ADR-069](../ADRs/ADR-069-sign-in-and-transport-security.md)) · Pilot | Certificates with no cron job, and a short config file | **Nginx:** certificates by hand through certbot, and more configuration. **Traefik:** label-driven configuration is hard to read for a fixed setup |
| Backups | **restic** to S3 in `il-central-1`, encrypted: an hourly `pg_dump` first, then the proof files, so every file a row points to is in the backup. A **Healthchecks.io** ping on every run; a restore drill script, rehearsed before the pilot. Daily EBS snapshots as a second layer (NFR-28, NFR-125, NFR-126, NFR-164). The restic key and config sit in SSM, readable by two named members (Q-92). The VM cannot delete or corrupt backups, including old versions and restic's key and config objects (NFR-266): the bucket is versioned, with Object Lock in governance mode for 35 days, and old versions are kept at least as long as the lock. Pruning runs inside `il-central-1` under its own role, never on runners outside Israel. Only the break-glass role, assumed by a member with MFA, can bypass the lock or change the bucket's lock, lifecycle or policy, and assuming it raises an alert. A scheduled check of the backup repository alerts when it fails. The disk and snapshots are encrypted, and EBS snapshots are kept 35 days (Q-14). Erased personal data leaves the backups within a total the data inventory states. Whether the pinned restic works with an Object Lock bucket is unverified; M13 checks it | Decided · Pilot | One tool covers the database and the files, deduplicated and encrypted | **pgBackRest or WAL-G:** point-in-time recovery is more than a one-hour loss allowance needs, and they don't cover the proof files. **EBS snapshots alone:** crash-consistent images, not logical dumps |
| Secrets | **AWS SSM Parameter Store** (SecureString), read by the VM's IAM role at deploy into a file only root can read (mode 0600); local `.env` files are git-ignored (NFR-03). SSM records who opened each session on the VM and when (CloudTrail), and records no session stream (NFR-229). The backup secrets sit under a customer-managed KMS key whose policy names the identities that may read them (the two named members, and the roles that write and prune backups) and keeps no account-root statement beyond key administration (NFR-273). A leaving member's access is removed that day and the secrets they held are rotated (Q-92) | Decided · Pilot | Free at this scale, and read with the role the VM already has | **Secrets Manager:** $0.40 per secret per month, for rotation features we don't use. **Vault:** another service |
| Cost control | **AWS Budgets** alerts at 90% of the hosting cap of ₪550 a month with VAT, about ₪495 (NFR-52, Q-62; the company still confirms the cap); a spend limit at the SMS provider (Q-96); bills checked each pilot month | Decided · Pilot | Estimate, not measured: about ₪440–450 before VAT and ₪520–530 with Israel's 18% VAT; the lines are in [ADR-078](../ADRs/ADR-078-hosting-on-one-aws-vm-in-tel-aviv-built-with-terraform.md) | — |
| Deployment | GitHub Actions builds `linux/amd64` images, pushes them to **GHCR**, and an SSM command pulls and restarts them outside operating hours (NFR-127, NFR-208). CI signs in to AWS with OIDC, and the trust pins both `aud` and `sub` (the protected `production` environment). The deploy role may run one custom SSM document on one instance, whose digest parameter has an `allowedPattern` (never `AWS-RunShellScript`), and it has no `ssm:UpdateDocument`. Every human account that can deploy or release the app requires MFA (NFR-265) | Decided · Pilot | No open port, and no stored AWS credentials on runners | **SSH deploys:** need port 22 (NFR-26). **ECR:** fine, another registry to manage. **CodeDeploy:** more machinery for one VM |
| Runbook | `docs/operations/runbook.md`: deploy, restore, secret rotation, outage handling, recovering a lost Admin (NFR-166). A restore revokes every session, voids every open credential link, re-applies the deactivations, role removals, data requests and opt-outs made since the snapshot (from records that carry only IDs and keyed hashes, kept off the VM as each change happens; M13 designs the mechanism), rotates the access-token signing key and drops the old one from the accepted set at once, with no two-key overlap (NFR-272), and keeps messages off until an Admin turns them on (Q-92, NFR-252, NFR-253). The restore step itself issues set-password links to the Admin accounts, at addresses the company confirms, as an audited step (FR-393). The Admins then confirm each account's state and roles with the company before any other set-password link goes out. For each account whose password or email changed since the snapshot, found in the logs by account ID, the restore blanks the password and voids the account's links, and, once the Admins are confirmed, a set-password link goes to the confirmed address. If the snapshot is older than the logs reach, every account gets this treatment. Rotating the secrets also versions the keyed-hash keys: every opt-out whose phone is still stored is re-hashed at rotation. While any opt-out remains under an old key version, that key is kept for lookups only: the accepted risk is the key's continued existence, never a lost opt-out (NFR-202). Research pseudonyms start anew under the new key ([ADR-069](../ADRs/ADR-069-sign-in-and-transport-security.md)). Every admin step that takes a person's identifier (the first-Admin and lost-Admin steps, the deactivation of the Admins a request names (FR-346), the data requests) reads it at a non-echoing prompt, so it never enters a command line, shell history or SSM's history (FR-393, FR-291). The audit record holds the account ID, and an export is encrypted to the requesting member's public key, so no decryption key is on the VM, and is deleted on a set date, never sent to the terminal (FR-409, [ADR-069](../ADRs/ADR-069-sign-in-and-transport-security.md)) | Decided · Pilot | — | — |

---

## 16. Observability and alerting

| Concern | Choice | Status | Why | Over |
|---|---|---|---|---|
| Instrumentation | OpenTelemetry in both services (Java agent; FastAPI instrumentation); Micrometer domain metrics in the core (NFR-159, NFR-161) | Decided · Pilot (spans for NFR-07 from M9) | One standard across two languages | **Vendor SDKs:** lock the data to one backend |
| Correlation | W3C trace context across web app → core → optimizer; the trace ID in every log line, audit record and client error report (NFR-20, NFR-93, NFR-74) | Decided · Alpha (log IDs), Pilot (traces) | One ID finds everything about one request | — |
| Pipeline | **OpenTelemetry Collector**, with a redaction processor as a backstop against personal data (NFR-19) | Decided · Pilot | One place to scrub | — |
| Backend | **Prometheus** + **Alertmanager**, **Loki**, **Tempo**, **Grafana**, on the 16 GB VM, with 7-day retention (NFR-76) | Decided ([ADR-078](../ADRs/ADR-078-hosting-on-one-aws-vm-in-tel-aviv-built-with-terraform.md)) · Pilot | Self-hosted, so telemetry stays in Israel and the plan sets retention | **OpenObserve** (one program for logs, metrics, traces and alerts) on an 8 GB VM: rejected with that size (§15, §23). **SigNoz:** ClickHouse underneath, heavy on one VM. **Hosted** (Grafana Cloud, Datadog): telemetry leaves Israel, and the vendor sets retention |
| Dashboards and alert rules | Stored as code in `infra/deploy/observability/` (NFR-75, NFR-197) | Decided · Pilot | Reviewed like code | — |
| Alerts | Solve failures above 10% of a day, a preview over 30 s, 5 queued jobs, disk above 80% (NFR-75); failed backups (NFR-164); the message cap or more than 10% dropped (NFR-197); the recovery-email cap (NFR-215); an event publication still failing after 5 resubmissions; a job the watchdog had to fail. Delivered by email through Amazon SNS in `il-central-1` and to the team's chat channel; alert text carries no personal data (NFR-268) | Decided · Pilot | A watcher that repairs silently should still tell someone | — |
| Client errors | **GlitchTip**, self-hosted on the VM, through the Sentry SDKs in both apps (NFR-74) | Decided ([ADR-079](../ADRs/ADR-079-client-errors-and-crashes-go-to-self-hosted-glitchtip.md)) · Pilot | §10 | §10 |
| Uptime | A **Route 53 health check** over HTTPS on the health endpoint, with a CloudWatch alarm that emails a named team member through SNS, in Terraform; about $1.60 a month (estimate, not measured). Healthchecks.io's backup pings, from outside AWS, are a second and slower warning (NFR-128) | Decided ([ADR-078](../ADRs/ADR-078-hosting-on-one-aws-vm-in-tel-aviv-built-with-terraform.md)) · Pilot | The VM can't report its own outage | — |

---

## 17. Testing

### 17.1 By layer

| Layer | What | Tools | Why these | Over |
|---|---|---|---|---|
| Domain | Every invariant and transition with plain values and no mocks; seeded random command sequences for `Plan` and `Delivery` (domain §14) | JUnit (the version Spring Boot manages), AssertJ; pytest, Hypothesis | Fast and plain. A seeded loop in plain JUnit covers Java's property tests without another engine | **jqwik:** its documentation doesn't state support for the JUnit 6 platform that Spring Boot 4 uses, and since its 1.10 release it prints messages aimed at AI coding agents into test output, which a team using coding assistants shouldn't build on |
| Application | Use cases against in-memory fakes of every secondary port; the same fakes back the end-to-end profile (NFR-175) | JUnit | One set of fakes, two uses | **Mockito everywhere:** tests that check calls instead of outcomes |
| Validator | Standalone suite: published best-known Solomon solutions accepted, limit cases accepted, a catalogue of crafted violations rejected by rule (NFR-80, NFR-167, NFR-168, NFR-169, NFR-194) | pytest | The validator decides everything, so it's tested hardest | — |
| Solvers | Property tests under iteration budgets; a fake infeasible solver (NFR-82, NFR-170 to NFR-174) | Hypothesis | Finds instances nobody thought to write | — |
| Adapters | Persistence and migrations against real PostgreSQL; OSRM on a small fixed extract; provider adapters against stubs (NFR-165) | Testcontainers, WireMock | The real database, not an imitation | **H2:** different SQL behaviour |
| Modules and events | Each module booted alone; each reaction run twice to prove idempotency; the watchers ([ADR-066](../ADRs/ADR-066-every-asynchronous-step-has-a-watcher.md)) | Spring Modulith's `@ApplicationModuleTest` and `Scenario` API | Built for exactly this | — |
| Architecture | Module rules and the domain's purity rules | Spring Modulith, ArchUnit, import-linter ([2.2](#22-enforcement)) | — | — |
| Contracts | Both sides of both APIs against their contracts; breaking-change check; lint (NFR-21, NFR-134, NFR-162) | Schemathesis, oasdiff, Redocly CLI | Property-based API tests generated from the contract | **Pact:** consumer-driven contracts, built for teams that don't own both sides. We own both sides of both contracts, so the OpenAPI file already is the contract |
| Permissions | Every role and role combination against every capability; one driver against another's data (NFR-176, NFR-210) | API tests generated from the permission matrix | Can't forget a row | — |
| Front-end units | Components, hooks, formatters, bidirectional sample strings (NFR-136); the phone and web wipe rules (NFR-230 to NFR-236, NFR-263, NFR-269), with an injected clock and faked secure storage and push | Vitest, React Testing Library, MSW; jest-expo, React Native Testing Library | — | — |
| End to end | The Alpha demo script; then UC-06, UC-07, UC-08, UC-10, UC-11 and UC-14, on the web app and the phone app's web build, against the Compose stack, with a controllable clock, fixed road data, search budgets and faked push, SMS and email (NFR-83, NFR-175). These run under an `e2e` profile: its test-only endpoints (the clock, the fakes' inboxes) exist only in that profile, and the profile refuses to boot when the SMS credential is present (NFR-206), with a test for each ([ADR-093](../ADRs/ADR-093-the-implementation-plan-is-retired-and-a-roadmap-holds-the-dates.md)) | Playwright (`page.clock`), Mailpit | Two browser contexts in one test (a dispatcher and a driver), clock control and offline mode | **Cypress:** one tab at a time, so the dispatcher-and-driver script can't run in one test |
| Accessibility | Automated WCAG 2.2 AA in CI, plus a manual audit with NVDA, VoiceOver and TalkBack (NFR-141, NFR-142) | @axe-core/playwright | Runs inside the end-to-end tests | — |
| Devices | Scripted real-phone checklist, including background location, offline work, encrypted storage, verified links, Hebrew map labels, and the Admin and Dispatcher memory-only check and the sign-out wipe (NFR-140, NFR-221) | Manual; **Maestro** flows (optional: adopted once the checklist has run by hand for two releases, [ADR-028](../ADRs/ADR-028-driver-app-through-test-tracks-with-a-device-checklist.md)) | — | — |
| Mutation | **mutmut** runs once over `Problem.validate` before the first measured study run in B, and every surviving mutant becomes a test or is recorded as equivalent ([ADR-091](../ADRs/ADR-091-study-design-for-the-solver-comparison.md)). **PIT** on the Java domain rules stays optional | mutmut (Python); PIT (Java, optional) | Shows whether the validator's tests would notice a broken rule | — |

### 17.2 Fault injection

| Fault | How | Requirements |
|---|---|---|
| Optimizer, OSRM or geocoder down or slow | **Toxiproxy** (Testcontainers module) between the core and each service | NFR-42 |
| SMS provider down, slow or answering twice | WireMock faults and repeated callbacks | NFR-189, NFR-190, NFR-199, NFR-201 |
| A listener throws once | A test listener that fails on its first call; the watcher must recover | Domain §12 |
| A service crashes after acknowledging a report | Kill the container during an end-to-end run | NFR-64 |
| The core restarts during a solve | Restart the container while a job runs | BR-74, BR-85, NFR-12 |
| The phone loses its connection | Playwright's offline mode on the web build; airplane mode on the device checklist | FR-70, FR-246 |
| Losing the VM | Restore drill: Terraform a new VM, restic restore, timed | NFR-125, NFR-126 |

### 17.3 Security testing

| What | Tool | Why | Over |
|---|---|---|---|
| Static analysis (NFR-40) | **Semgrep** Community Edition | Free for private repositories | **CodeQL:** free only for public repositories, and the repository is private (ADR-017) |
| Running-app scan (NFR-40, NFR-55) | **ZAP** baseline scan against the usability environment | The standard free scanner | — |
| ASVS 5.0 Level 1 self-check (NFR-40) | A checklist in `docs/operations/`, each item linked to a test or a review | — | — |
| Dependencies, images, secrets, licences (NFR-51, NFR-54, NFR-103) | [18](#18-cicd-and-supply-chain) | — | — |

### 17.4 Performance measurement

Measured as NFR-181 says: on the deployment VM, a mid-range laptop (4 cores, 8 GB, integrated
graphics, under 4 years old) and a mid-range Android phone (Galaxy A5x class, under 3 years old), at
200 stops and 15 routes, at the 95th percentile over at least 100 samples.

| Requirement | Measured with |
|---|---|
| NFR-07 re-optimization preview, NFR-57 matrix build | OpenTelemetry spans from request to response, read in the tracing backend |
| NFR-08 edit checks | Playwright timing from the edit to the flags on screen |
| NFR-09 live updates, NFR-180 alerts | k6 with xk6-sse for the web stream, comparing send and receive timestamps. On phones, NFR-180 runs from the server's send to the alert on screen; an offline phone shows waiting alerts within 60 s of reconnecting while the app is open |
| NFR-10 load, NFR-59 API latency | k6, for 10 minutes: 15 phones (a position every 30 s, a report about every 2 minutes, and a route read on each push) and 5 web sessions (an open stream, and the plan reloaded each minute). An error is a 5xx or an answer over 2 s. Stream holders run as their own scenario, because the SSE extension blocks its virtual user while connected; verify that extension in M13 |
| NFR-58 first load | **Lighthouse CI** with Fast 4G throttling |
| NFR-60 import validation | A timed integration test with 200 rows |
| NFR-61 phone app opening | Device checklist (Maestro timing, if adopted) |
| NFR-109 frame rate | Chrome DevTools performance recordings of scripted pan, zoom and select |
| NFR-47 battery | **Android Studio** Power Profiler and `batterystats`; **Xcode** energy reports; an 8-hour run on real phones |

**Why k6:** scripts in JavaScript, and the SSE extension covers the stream. **Over** Gatling (Scala or
Java DSL) and JMeter (GUI-first, XML plans).

---

## 18. CI/CD and supply chain

| Stage | Tools | Status | Requirements |
|---|---|---|---|
| Lint and format | Spotless; Ruff, mypy; ESLint, Prettier, `tsc --noEmit`; Redocly lint; commitlint | Alpha (Error Prone from January) | |
| Unit and architecture tests | JUnit, pytest, Vitest; Modulith, ArchUnit, import-linter | Alpha (jest-expo later) | NFR-133 |
| Adapter and contract tests | Testcontainers, WireMock; Toxiproxy, Schemathesis, oasdiff against `main` | Alpha (Testcontainers, WireMock); Jan (the rest) | NFR-134, NFR-162, NFR-165 |
| Build | Maven, uv, pnpm, Vite; Docker Buildx for `linux/amd64` images; OSRM preprocessing | Alpha (images from the pilot) | NFR-208 |
| Scans | **OSV-Scanner** (dependencies, already running, [ADR-016](../ADRs/ADR-016-dependency-audit-with-osv-scanner.md)); a pinned **Trivy** for images, and `trivy config` on `infra/` once the Terraform exists, from the pilot ([ADR-072](../ADRs/ADR-072-images-scanned-by-pinned-trivy-and-secrets-by-gitleaks.md)); **Gitleaks** over the full history (NFR-103, NFR-105); **Semgrep** | Jan (Trivy, Semgrep); Gitleaks from the Alpha | NFR-54, NFR-103 |
| SBOM and licences | **CycloneDX** SBOMs from Maven, Python and npm; a licence allow-list check against [ADR-017](../ADRs/ADR-017-proprietary-licence.md) and [ADR-074](../ADRs/ADR-074-dependency-licences-checked-against-an-allow-list.md), which lists `alns` (MIT); the notices file shipped in both apps | Pilot | NFR-51, NFR-154 |
| End to end, accessibility, performance | Playwright on the Compose stack (the demo script from M5); axe; Lighthouse CI | Alpha (the demo script); B (the rest) | NFR-83, NFR-141, NFR-58 |
| Solver smoke run | A small Solomon run, never Gehring-Homberger (Q-22) | B | FR-275 |
| Deploy | Manual approval, then GHCR and an SSM command; phone builds through EAS | Pilot | NFR-127, NFR-186 |

- **Pin every third-party action to a commit SHA and every image to a digest.** On 19 March 2026,
  attackers used stolen credentials to publish a malicious Trivy v0.69.4 release and force-push 76 of
  the 77 version tags of `aquasecurity/trivy-action` to credential-stealing code (CVE-2026-33634). On
  22 March they published malicious v0.69.5 and v0.69.6 images on Docker Hub. A pinned SHA can't be
  moved. Trivy is pinned to a verified release and its action to a commit SHA, and Renovate's minimum
  release age holds each upgrade back ([ADR-046](../ADRs/ADR-046-renovate-replaces-dependabot-and-targets-dev.md), [ADR-072](../ADRs/ADR-072-images-scanned-by-pinned-trivy-and-secrets-by-gitleaks.md)).
- **Deploy and release accounts:** every human account that can deploy or release the app requires MFA (NFR-265). EAS Update code signing is used if Expo's plan offers it (unverified).
- **Accepted vulnerabilities** are listed in a file, each with its reason and an expiry date (NFR-104).
- **GPL and AGPL services** (the Grafana stack, and Nominatim only as GovMap's fallback) run
  unmodified as separate containers. Record them in the licence check ([ADR-074](../ADRs/ADR-074-dependency-licences-checked-against-an-allow-list.md)).
- **Open: CI minutes, to decide at M5.** CI runs twice for a branch with an open pull request, and a
  private repository's allowance is 2,000 minutes a month (Free) or 3,000 (Pro). Decide once the
  end-to-end jobs exist and their run times are known ([22](#22-open-decisions)).

**Why GitHub Actions:** the repository is on GitHub, and Actions runs Testcontainers on its Linux
runners with Docker already installed. **Over** a self-hosted runner: a machine to keep patched.

---

## 19. Documentation and diagrams

| What | Tool | Where |
|---|---|---|
| Architecture, requirements, ADRs, runbook | Markdown | `docs/` |
| Diagrams in documents | **Mermaid** (rendered by GitHub) and **draw.io** | `docs/architecture/` |
| Module diagrams and canvases | **Spring Modulith Documenter**, generated in CI (PlantUML / C4) | `docs/architecture/generated/` |
| API reference | **Redocly CLI** builds HTML from both contracts in CI | CI artifact |
| User guides, Hebrew and English | Markdown, shown in the web app's help pages (NFR-79) | `docs/guides/{he,en}/` |
| Usability study | Task scripts in Markdown; the SUS questionnaire in a form; screen recording with consent (**OBS Studio**) (FR-48) | `docs/research/usability/` |
| Course documents (ARD, ADD) | The faculty's templates. The ARD is a PDF built outside the repository. The ADD due on 5 January is assembled from this file, the domain document, the ADRs and the [roadmap](../roadmap.md), with diagrams exported from the repository | The ARD PDF in `docs/requirements/ARD/`; the ADD outside the repository |

**Why Mermaid first:** diagrams live in the Markdown they explain, render on GitHub, and change in the
same pull request as the text. draw.io is for presentation drawings. **Over** image-only diagrams: they
go stale silently, and an embedded image can be lost when a document is exported.

**One topic, one document.** The domain model lives only in the domain document, and tools and
versions only here. Each document links to the others instead of copying them.

---

## 20. Adoption order

What each stage brings in, so the team doesn't build infrastructure before the product. The
[roadmap](../roadmap.md) has the milestones and dates.

| Stage | Brings in |
|---|---|
| **Alpha** ([dates](../roadmap.md#22-alpha-track)): local, web only, synthetic data | Repository tooling ([5](#5-development-environment-and-workflow)); PostgreSQL with the owner and application roles, Flyway, jOOQ; signature proofs (`signature_pad`, ImageIO); the core with Fleet, Customers & Orders, Planning, the fact aggregates for online reports, and Reporting's summary and baseline; Spring Modulith's event registry with retries configured; the optimizer with OR-Tools, nearest neighbour and the daily validator; OSRM and tiles on a small extract; the web app with map, timeline, table, publishing and driver screens; the synthetic order generator; CI with unit tests, `verify()`, the ArchUnit purity rules, import-linter, mypy, Gitleaks, and the demo script under Playwright |
| **January break** (after the 12 January demo) | Spring Boot 4.2; Error Prone and NullAway (warnings first); Schemathesis and oasdiff; Renovate, with digest pins for every image; Trivy and Semgrep; the bundle visualizer |
| **Semester B** | Access in full; Excel and Windows-1255 imports; geocoding (GovMap); Live Operations in full and re-optimization; the phone app with offline outbox and background location; push; Recipient Messaging with the fake SMS gateway; the own solver and the research harness |
| **Before the pilot** | AWS through Terraform; Caddy, TLS, domain, SES; backups and the restore drill; observability and alerts; GlitchTip and the Route 53 health check; real SMS; ZAP and the ASVS self-check; SBOMs and the licence check; the device checklist; the usability environment; the team's own AWS account, if there is no company |

---

## 21. Version baseline

Checked on 12 November 2026. Python and JavaScript versions were read from PyPI and npm; Java-side dates
come from the projects' release announcements. Maven Central couldn't be queried for this check, so pin
the Java libraries to the versions Spring Boot manages, and check the rest when the build files are
written. The GlitchTip, Trivy and `alns` rows were read on 14 November 2026: `alns` from PyPI, the
other two from their projects' release pages.

| Component | Baseline | Note |
|---|---|---|
| Java | 25 LTS (Eclipse Temurin) | |
| Spring Boot | 4.1.x, then 4.2.x in January | 4.1 released 10 June 2026, OSS support to 31 July 2027; 4.2 scheduled for 19 November 2026, OSS support to 31 December 2027 |
| Spring Modulith | 2.1.x with Boot 4.1 (2.1 GA on 11 June 2026) | The 2.2 line follows Boot 4.2 |
| PostgreSQL | 18 | 19 is due on 19 November 2026; don't switch during the project |
| jOOQ, Flyway, JUnit, Testcontainers | The versions Spring Boot manages (jOOQ: latest Open Source Edition) | The open-source edition supports the latest PostgreSQL versions |
| Python | 3.14 | OR-Tools has no 3.15 wheels; 3.13 is ending bug-fix support |
| OR-Tools | 9.15.6755, pinned exactly | The latest on PyPI |
| NumPy, SciPy | 2.5.x, 1.18.x | Both need Python 3.12 or later |
| Numba | 0.68.x | Has 3.14 wheels |
| PyVRP | 0.14.x | Harness only |
| FastAPI, Pydantic | 0.162.x, 2.14.x | |
| pandas | 3.1.x | Copy-on-write and the string dtype are the defaults since 3.0 |
| OSRM | 26.x (26.7 latest) | |
| Node.js | Current LTS, pinned in `mise.toml` | |
| TypeScript | 6.0.x | typescript-eslint supports TypeScript below 6.1 |
| React | 19.3.x | |
| Vite, Vitest | 8.x, 5.x | |
| Mantine | 9.x | |
| TanStack Query, Router, Table | 5.x, 1.x, 9.x | |
| MapLibre GL JS, react-map-gl | 6.x, 8.x | |
| Playwright | 1.65.x | |
| Expo SDK | 58 (React Native 0.88) | |
| @maplibre/maplibre-react-native | 11.5.x | Requires Expo 54 or later |
| Ubuntu Server | 26.04 LTS | |
| Terraform | Current 1.x | |
| GlitchTip | 7.0.0, released 9 November 2026 | The latest release on the project's GitLab page. Read its release notes before pinning: 7.0 is days old, and it runs HTTP, the worker and the scheduler as one process |
| Trivy | 0.76.2, released 5 November 2026 | The latest release on GitHub. The compromise hit v0.69.4 to v0.69.6; verify the release the way the advisory describes before pinning it (not verified here) |
| `alns` | 7.0.0, released 21 October 2024; MIT | The latest on PyPI. It declares Python 3.9 and later, so check it on 3.14 when adopted |

---

## 22. Open decisions

Three decisions are open. Every other row of this file is decided, and its reasoning is in its ADR. Some details are deferred to M0: ADR-038's Node major; ADR-039's ESLint, Prettier and Renovate pins; ADR-042's `TestDispatchFlowApplication` and `just core-run`.

| Decision | Proposed answer | Refs |
|---|---|---|
| SMS vendor | An Israeli provider chosen against the Q-96 checklist when M12 starts. A signed data-processing agreement comes before real recipients get messages, and the team builds against the fake gateway until then | Q-96, NFR-206 |
| CI minutes | Decide at M5, once the end-to-end jobs exist and real run times are known ([18](#18-cicd-and-supply-chain)) | — |
| The team's chat service | The team names one before the pilot, for alerts with no personal data ([13](#13-outside-services-and-where-data-goes)) | NFR-268 |

---

## 23. Alternatives rejected across the stack

Most rejections are in the **Over** column of their row. These cut across several rows.

| Option | Why not |
|---|---|
| Redis, RabbitMQ, Kafka, Celery | One VM (NFR-14). PostgreSQL already covers the solve queue, the message outbox and the event registry |
| Kubernetes | One VM and one Compose command (NFR-14, NFR-16) |
| Hosted logging, monitoring and identity | Personal data and telemetry would leave Israel, and the vendor, not the plan, would set retention (NFR-76, NFR-209) |
| Google, Mapbox or HERE for maps and geocoding | Terms that forbid storing results or showing them on our map, per-element pricing, and data leaving Israel |
| GraphQL, WebSockets | ADR-006 chose OpenAPI; ADR-026 chose server-sent events |
| Unpinned GitHub Actions | The March 2026 `trivy-action` compromise |
| Expo's web build as the web app | A dense desktop console is better built with plain React for the browser; the phone app's web build stays for tests (NFR-220) |
| A web view for the Admin and Dispatcher screens in the phone app | It was the largest scope saving available, but it needed those web screens to work at phone width, a safe sign-in hand-off into the web view, and storage cleared at sign-out. Both apps serve every role natively instead ([ADR-035](../ADRs/ADR-035-both-apps-serve-every-role.md), FR-120) |
| Hosted Sentry | Reports would leave Israel and need an NFR-209 exception. GlitchTip on the VM speaks the same SDK ([ADR-079](../ADRs/ADR-079-client-errors-and-crashes-go-to-self-hosted-glitchtip.md)) |
| Grype | One of the two image scanners considered. A pinned Trivy was chosen, and it also checks the Terraform in `infra/` ([ADR-072](../ADRs/ADR-072-images-scanned-by-pinned-trivy-and-secrets-by-gitleaks.md)) |
| 8 GB VM with OpenObserve | The 16 GB VM with the Grafana stack was chosen. Reopen it only if the measured bill passes the ₪550 cap ([ADR-078](../ADRs/ADR-078-hosting-on-one-aws-vm-in-tel-aviv-built-with-terraform.md)) |

---

## 24. References

- Spring Boot 4.1 release and the 4.2 schedule: <https://infoq.com/news/2026/06/spring-boot-4-1>
- Spring Boot 4.1 and 4.2 support dates: <https://ecorpit.com/spring-boot-4-1-end-of-support-2027/>
- Spring Modulith 2.x event publication lifecycle, staleness and resubmission:
  <https://docs.spring.io/spring-modulith/reference/2.0/events.html>
- Spring Modulith configuration properties: <https://docs.spring.io/spring-modulith/reference/2.2/appendix.html>
- Spring Framework 7 resilience features: <https://docs.spring.io/spring-framework/reference/core/resilience.html>
- Spring Modulith verification: <https://docs.spring.io/spring-modulith/reference/verification.html>
- PostgreSQL 19 RC 1 and the planned GA date: <https://www.postgresql.org/about/news/postgresql-19-rc-1-released-3409/>
- jOOQ editions and supported databases: <https://www.jooq.org/download/>
- OpenAPI Generator, Jackson 3 and Spring Boot 4 options: <https://github.com/OpenAPITools/openapi-generator/pull/22854>
- Expo SDK 58: <https://expo.dev/changelog/sdk-58>
- expo-location options (`timeInterval` is Android-only): <https://unpkg.com/expo-location@56.0.15/src/Location.types.ts>
- MapLibre React Native: <https://github.com/maplibre/maplibre-react-native>
- expo-sqlite (SQLCipher option): <https://docs.expo.dev/versions/latest/sdk/sqlite/>
- Protomaps localization: <https://docs.protomaps.com/basemaps/localization>
- OSRM releases: <https://github.com/Project-OSRM/osrm-backend/releases>
- Ubuntu 26.04 LTS: <https://documentation.ubuntu.com/release-notes/26.04/>
- Amazon SES in Tel Aviv: <https://aws.amazon.com/about-aws/whats-new/2023/08/amazon-simple-email-service-israel-tel-aviv-region>
- EC2 prices in `il-central-1`: <https://aws-pricing.com/t3a.xlarge.html>, <https://aws-pricing.com/t3.large.html>
- Google Maps Platform Service Specific Terms, Geocoding API sections 6.2 and 6.3: <https://cloud.google.com/maps-platform/terms/maps-service-terms>
- Geocoding storage terms compared: <https://www.geocod.io/geocoding-terms-of-use-comparison>
- HERE permanent geocoding: <https://docs.here.com/here-kb/docs/permanent-geocoding-overview-licensing-and-usage-rules>
- Trivy supply-chain compromise (CVE-2026-33634): <https://nvd.nist.gov/vuln/detail/CVE-2026-33634>
- Trivy compromise advisory, with affected versions and exposure windows: <https://github.com/advisories/GHSA-69fq-xp46-6x23>
- Trivy releases: <https://github.com/aquasecurity/trivy/releases>
- GlitchTip releases: <https://gitlab.com/glitchtip/glitchtip-backend/-/releases>
- `alns` on PyPI: <https://pypi.org/project/alns/>
- xk6-sse: <https://github.com/phymbert/xk6-sse>
