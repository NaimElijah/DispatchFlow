# DispatchFlow - Architecture Decision Records

One file per decision, named `ADR-NNN-short-kebab-title.md`. Records are append-only. To change
course, add a new ADR and mark the old one `Superseded by ADR-NNN`. How to write one is in
[`../CLAUDE.md`](../CLAUDE.md#adrs).

| ADR | Decision | Status |
|---|---|---|
| **Architecture** | | |
| [ADR-001](ADR-001-domain-driven-design-with-hexagonal-architecture.md) | Domain-driven design, with every service built as ports and adapters | Accepted |
| [ADR-002](ADR-002-core-backend-and-separate-optimization-service.md) | A Java core backend and a separate Python optimization service | Accepted |
| [ADR-003](ADR-003-dispatcher-console-and-driver-app.md) | Two clients: a React web console and a React Native driver app | Accepted; extended by ADR-026 and ADR-028 |
| [ADR-004](ADR-004-pluggable-solver-port.md) | Every solver behind one solver port | Accepted; extended by ADR-029 |
| [ADR-005](ADR-005-or-tools-first-research-solver-for-re-optimization.md) | OR-Tools first, with our own solver as a measured research track aimed at re-optimization | Accepted; extended by ADR-029, which fixes the study's values this ADR left open |
| [ADR-006](ADR-006-versioned-openapi-contracts.md) | Every API between components is a versioned OpenAPI contract, written first | Accepted; extended by ADR-026 |
| [ADR-007](ADR-007-postgresql-with-flyway-migrations.md) | PostgreSQL is the system of record, and Flyway migrations own its schema | Accepted |
| [ADR-024](ADR-024-one-delivery-company-no-multi-tenancy.md) | DispatchFlow serves one delivery company, with no multi-tenancy | Accepted |
| [ADR-026](ADR-026-server-sent-events-and-push-for-live-updates.md) | Live updates use server-sent events and push, and clients act through HTTP POST | Accepted |
| [ADR-029](ADR-029-solver-objective-and-re-optimization-rules.md) | How both solvers trade drops, moved stops, lateness and travel time | Accepted; extended by ADR-032, which makes leave-at-door windows soft in solves too |
| [ADR-030](ADR-030-admin-issued-credentials-and-token-revocation.md) | Admins issue drivers' credentials, and a revoked access token is refused at once | Accepted |
| [ADR-031](ADR-031-sms-through-an-israeli-provider-for-recipient-messages.md) | Messages to recipients go by SMS through an Israeli provider, from a two-way virtual number | Accepted |
| [ADR-032](ADR-032-leave-at-door-windows-are-preferences.md) | A leave-at-door order's time window is a preference, priced into the objective | Accepted |
| **Repository, build and delivery** | | |
| [ADR-008](ADR-008-monorepo-layout-under-src.md) | One repository, with the code under `src/backend`, `src/frontend` and `src/research` | Accepted |
| [ADR-009](ADR-009-just-as-the-single-entry-point.md) | The root `justfile` is the single entry point, and CI runs the same recipes | Accepted |
| [ADR-010](ADR-010-toolchain-pins-in-version-files.md) | Toolchain versions are pinned in per-language version files | Accepted |
| [ADR-011](ADR-011-python-tooling-uv-ruff-mypy-pytest.md) | Python projects use uv, ruff, mypy in strict mode and pytest | Accepted |
| [ADR-012](ADR-012-javascript-tooling-npm-eslint-prettier.md) | Both frontend apps use npm, ESLint and Prettier, with TypeScript held at 6.0 | Accepted |
| [ADR-013](ADR-013-java-tooling-maven-wrapper-and-spotless.md) | The core backend builds with the Maven wrapper and formats with Spotless and palantir-java-format | Accepted |
| [ADR-014](ADR-014-real-dependencies-in-containers-for-tests-and-local-runs.md) | Integration tests and local runs use a real PostgreSQL in a container | Accepted |
| [ADR-015](ADR-015-ci-one-workflow-one-job-per-component.md) | CI is one workflow with one job per component, and Dependabot keeps dependencies current | Accepted; extended by ADR-021 |
| [ADR-016](ADR-016-dependency-audit-with-osv-scanner.md) | One OSV-Scanner pass audits every lockfile, and an accepted vulnerability expires | Accepted; extended by ADR-023 |
| [ADR-023](ADR-023-accept-minimatch-and-glob-advisories.md) | Accept the minimatch and glob advisories in the frontends' tooling until 2027-01-31 | Accepted |
| **Project and team** | | |
| [ADR-017](ADR-017-proprietary-licence.md) | Release the code under a proprietary licence, all rights reserved | Accepted |
| [ADR-018](ADR-018-ai-assistant-context-in-the-repository.md) | The AI assistant's context lives in the repository | Accepted; extended by ADR-020 and ADR-025 |
| [ADR-020](ADR-020-team-subagents-in-the-repository.md) | Specialised team subagents live in `.claude/agents/`, beside each person's own | Accepted; extended by ADR-022 |
| [ADR-021](ADR-021-dependabot-targets-dev-and-skips-pinned-majors.md) | Dependabot opens its updates against `dev`, and skips the versions other ADRs pin | Accepted |
| [ADR-022](ADR-022-personal-subagents-come-before-team-subagents.md) | Each person's own subagents come before the team's | Accepted; extended by ADR-025 |
| [ADR-025](ADR-025-team-skills-in-the-repository.md) | Team skills live in `.claude/skills/`, and only a person can start them | Accepted |
| **Deployment** | | |
| [ADR-019](ADR-019-container-images-run-as-non-root.md) | Each service ships as a container image that runs as a non-root user | Accepted |
| [ADR-027](ADR-027-proof-files-on-the-vm-disk-with-hourly-backups.md) | Proof files stay on the VM's disk, and backups run hourly during operating hours | Accepted |
| [ADR-028](ADR-028-driver-app-through-test-tracks-with-a-device-checklist.md) | The driver app ships through test tracks, and real phones are checked by hand | Accepted |

> [!NOTE]
> Numbering restarted at ADR-001 on 2026-10-30. ADR-004 and ADR-005 are older (2026-10-27) and
> kept their numbers. Several decisions that `requirements.md` relies on have no ADR yet; each is
> marked `ADR pending: <title>` there until its ADR is written.
