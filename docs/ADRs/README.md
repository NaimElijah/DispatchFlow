# DispatchFlow - Architecture Decision Records

One file per decision, named `ADR-NNN-short-kebab-title.md`. Records are append-only. To change
course, add a new ADR and mark the old one `Superseded by ADR-NNN`. How to write one is in
[`../CLAUDE.md`](../CLAUDE.md#adrs).

| ADR | Decision | Status |
|---|---|---|
| **Architecture** | | |
| [ADR-001](ADR-001-domain-driven-design-with-hexagonal-architecture.md) | Domain-driven design, with every service built as ports and adapters | Accepted |
| [ADR-002](ADR-002-core-backend-and-separate-optimization-service.md) | A Java core backend and a separate Python optimization service | Accepted |
| [ADR-003](ADR-003-dispatcher-console-and-driver-app.md) | Two clients: a React web console and a React Native driver app | Superseded by ADR-035 |
| [ADR-004](ADR-004-pluggable-solver-port.md) | Every solver behind one solver port | Accepted; extended by ADR-029 |
| [ADR-005](ADR-005-or-tools-first-research-solver-for-re-optimization.md) | OR-Tools first, with our own solver as a measured research track aimed at re-optimization | Accepted; extended by ADR-029, which fixes the study's values this ADR left open |
| [ADR-006](ADR-006-versioned-openapi-contracts.md) | Every API between components is a versioned OpenAPI contract, written first | Accepted; extended by ADR-026 |
| [ADR-007](ADR-007-postgresql-with-flyway-migrations.md) | PostgreSQL is the system of record, and Flyway migrations own its schema | Accepted |
| [ADR-024](ADR-024-one-delivery-company-no-multi-tenancy.md) | DispatchFlow serves one delivery company, with no multi-tenancy | Accepted |
| [ADR-026](ADR-026-server-sent-events-and-push-for-live-updates.md) | Live updates use server-sent events and push, and clients act through HTTP POST | Accepted; extended by ADR-035 |
| [ADR-029](ADR-029-solver-objective-and-re-optimization-rules.md) | How both solvers trade drops, moved stops, lateness and travel time | Accepted; extended by ADR-032, which makes leave-at-door windows soft in solves too |
| [ADR-030](ADR-030-admin-issued-credentials-and-token-revocation.md) | Admins issue drivers' credentials, and a revoked access token is refused at once | Superseded by ADR-036 |
| [ADR-031](ADR-031-sms-through-an-israeli-provider-for-recipient-messages.md) | Messages to recipients go by SMS through an Israeli provider, from a two-way virtual number | Accepted |
| [ADR-032](ADR-032-leave-at-door-windows-are-preferences.md) | A leave-at-door order's time window is a preference, priced into the objective | Accepted |
| [ADR-035](ADR-035-both-apps-serve-every-role.md) | Both apps serve Admins, Dispatchers and Drivers | Accepted |
| [ADR-036](ADR-036-one-account-per-person-signed-in-by-email.md) | Each person has one account, signed in with their email, holding one or more roles | Accepted |
| [ADR-040](ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md) | The core is a Spring Modulith monolith with one module per bounded context, checked in CI | Proposed; would amend ADR-001 |
| [ADR-041](ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md) | Each module owns a PostgreSQL schema, read and written through jOOQ by a role that owns no table | Proposed; would supersede ADR-007 |
| [ADR-043](ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md) | Code is generated from both contracts on both sides, and errors are RFC 9457 Problem Details | Proposed; would extend ADR-006 |
| [ADR-044](ADR-044-solver-port-takes-start-budget-and-seed-and-problem-judges.md) | The solver port takes a start, a budget and a seed, and `Problem` alone judges a solution | Proposed; would amend ADR-004 |
| [ADR-045](ADR-045-generation-counters-revoke-tokens.md) | Generation counters on the user and the session revoke tokens | Proposed; would amend ADR-036 |
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
| [ADR-037](ADR-037-flat-layout-under-src-with-deploy-and-infra.md) | The code moves to a flat layout under `src/`, with deployment in `deploy/` and Terraform in `infra/` | Proposed; would supersede ADR-008 and amend ADR-006, ADR-009, ADR-018 and ADR-035 |
| [ADR-038](ADR-038-mise-pins-every-toolchain-and-core-moves-to-java-25.md) | mise pins every toolchain, and the core moves to Java 25 | Proposed; would supersede ADR-010 and amend ADR-002, ADR-009 and ADR-013 |
| [ADR-039](ADR-039-one-pnpm-workspace-for-both-apps-and-shared-packages.md) | Both apps and the shared TypeScript packages live in one pnpm workspace | Proposed; would supersede ADR-012 and amend ADR-035 |
| [ADR-042](ADR-042-compose-dev-for-local-runs-testcontainers-for-tests.md) | Local runs start their services from `compose.dev.yaml`; tests keep Testcontainers | Proposed; would supersede ADR-014 |
| [ADR-046](ADR-046-renovate-replaces-dependabot-and-targets-dev.md) | Renovate replaces Dependabot and keeps opening updates against `dev` | Proposed; would supersede ADR-021 and amend ADR-015 |
| **Project and team** | | |
| [ADR-017](ADR-017-proprietary-licence.md) | Release the code under a proprietary licence, all rights reserved | Accepted |
| [ADR-018](ADR-018-ai-assistant-context-in-the-repository.md) | The AI assistant's context lives in the repository | Accepted; extended by ADR-020 and ADR-025 |
| [ADR-020](ADR-020-team-subagents-in-the-repository.md) | Specialised team subagents live in `.claude/agents/`, beside each person's own | Accepted; extended by ADR-022 and ADR-033 |
| [ADR-021](ADR-021-dependabot-targets-dev-and-skips-pinned-majors.md) | Dependabot opens its updates against `dev`, and skips the versions other ADRs pin | Accepted |
| [ADR-022](ADR-022-personal-subagents-come-before-team-subagents.md) | Each person's own subagents come before the team's | Accepted; extended by ADR-025 and ADR-033 |
| [ADR-025](ADR-025-team-skills-in-the-repository.md) | Team skills live in `.claude/skills/`, and only a person can start them | Accepted; extended by ADR-033 and ADR-034 |
| [ADR-033](ADR-033-sync-with-dev-reviews-the-merge-on-sonnet.md) | `/sync-with-dev` reviews a merge with df-code-reviewer on Sonnet, and fixes what it finds | Accepted |
| [ADR-034](ADR-034-ask-before-merging-changes-to-the-assistant-setup.md) | Both team skills ask before merging a `dev` change to the assistant's setup | Accepted |
| **Deployment** | | |
| [ADR-019](ADR-019-container-images-run-as-non-root.md) | Each service ships as a container image that runs as a non-root user | Accepted |
| [ADR-027](ADR-027-proof-files-on-the-vm-disk-with-hourly-backups.md) | Proof files stay on the VM's disk, and backups run hourly during operating hours | Accepted |
| [ADR-028](ADR-028-driver-app-through-test-tracks-with-a-device-checklist.md) | The driver app ships through test tracks, and real phones are checked by hand | Accepted; extended by ADR-035 |
| [ADR-047](ADR-047-images-pinned-by-digest-and-core-on-temurin-25-jre.md) | Images are pinned by digest, and the core runs on the Temurin 25 JRE | Proposed; would amend ADR-019 |

> [!NOTE]
> Numbering restarted at ADR-001 on 2026-10-30. ADR-004 and ADR-005 are older (2026-10-27) and
> kept their numbers. Several decisions that `requirements.md` relies on have no ADR yet; each is
> marked `ADR pending: <title>` there until its ADR is accepted.

> [!IMPORTANT]
> ADR-037 to ADR-047 are Proposed. Each would supersede, amend or extend the accepted ADRs its
> status names, to match the design in [`tech_stack.md`](../architecture/tech_stack.md),
> [`domain_layer_design.md`](../architecture/domain_layer/domain_layer_design.md) and the
> [implementation plan](../init_impl_ref/technical_implementation_plan.md). The accepted ADRs keep
> their status until the team accepts the new ones ([`../CLAUDE.md`](../CLAUDE.md#adrs)). The
> plan's [§25](../init_impl_ref/technical_implementation_plan.md#25-decisions-to-record-as-adrs) and
> the tech stack's [§22](../architecture/tech_stack.md#22-open-decisions) list the decisions that
> still need an ADR before the code they govern merges.
>
> Some of them only make sense together: ADR-037, ADR-038 and ADR-039 (the layout, its toolchain
> and its workspace), and ADR-046 and ADR-047 (who moves the digest pins, and from when). Accept
> or reject each group as one.
