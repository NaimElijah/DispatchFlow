# DispatchFlow - Architecture Decision Records

One file per decision, named `ADR-NNN-short-kebab-title.md`. Records are append-only. To change
course, add a new ADR and mark the old one `Superseded by ADR-NNN`. How to write one is in
[`../CLAUDE.md`](../CLAUDE.md#adrs).

| ADR | Decision | Status |
|---|---|---|
| **Architecture** | | |
| [ADR-001](ADR-001-domain-driven-design-with-hexagonal-architecture.md) | Domain-driven design, with every service built as ports and adapters | Accepted; amended by ADR-040 |
| [ADR-002](ADR-002-core-backend-and-separate-optimization-service.md) | A Java core backend and a separate Python optimization service | Accepted; amended by ADR-038 |
| [ADR-003](ADR-003-dispatcher-console-and-driver-app.md) | Two clients: a React web app and a React Native phone app | Superseded by ADR-035 |
| [ADR-004](ADR-004-pluggable-solver-port.md) | Every solver behind one solver port | Accepted; extended by ADR-029; amended by ADR-044 |
| [ADR-005](ADR-005-or-tools-first-research-solver-for-re-optimization.md) | OR-Tools first, with our own solver as a measured research track aimed at re-optimization | Accepted; extended by ADR-029; amended by ADR-091 |
| [ADR-006](ADR-006-versioned-openapi-contracts.md) | Every API between components is a versioned OpenAPI contract, written first | Accepted; extended by ADR-026 and ADR-043; amended by ADR-037 and ADR-123 |
| [ADR-007](ADR-007-postgresql-with-flyway-migrations.md) | PostgreSQL is the system of record, and Flyway migrations own its schema | Superseded by ADR-041 |
| [ADR-024](ADR-024-one-delivery-company-no-multi-tenancy.md) | DispatchFlow serves one delivery company, with no multi-tenancy | Accepted |
| [ADR-026](ADR-026-server-sent-events-and-push-for-live-updates.md) | Live updates use server-sent events and push, and clients act through HTTP POST | Accepted; extended by ADR-035 and ADR-083 |
| [ADR-029](ADR-029-solver-objective-and-re-optimization-rules.md) | How both solvers trade drops, moved stops, lateness and travel time | Accepted; extended by ADR-032, ADR-048, ADR-049, ADR-053, ADR-091 and ADR-121 |
| [ADR-030](ADR-030-admin-issued-credentials-and-token-revocation.md) | Admins issue drivers' credentials, and a revoked access token is refused at once | Superseded by ADR-036 |
| [ADR-031](ADR-031-sms-through-an-israeli-provider-for-recipient-messages.md) | Messages to recipients go by SMS through an Israeli provider, from a two-way virtual number | Accepted |
| [ADR-108](ADR-108-messaging-appends-opt-ins-holds-its-fixed-wording-and-cuts-comments-to-500-characters.md) | Messaging appends opt-ins to the opt-out history, holds its fixed wording, and cuts comments to 500 characters | Accepted; extended by ADR-121; amended by ADR-117 |
| [ADR-117](ADR-117-each-outgoing-message-keeps-the-phone-it-goes-to.md) | Each outgoing message keeps the phone it goes to, and the opt-out word is read from a reply's first 500 code points | Accepted |
| [ADR-032](ADR-032-leave-at-door-windows-are-preferences.md) | A leave-at-door order's time window is a preference, priced into the objective | Accepted; extended by ADR-048; amended by ADR-100 |
| [ADR-035](ADR-035-both-apps-serve-every-role.md) | Both apps serve Admins, Dispatchers and Drivers | Accepted; extended by ADR-076 and ADR-083; amended by ADR-037, ADR-039 and ADR-123 |
| [ADR-036](ADR-036-one-account-per-person-signed-in-by-email.md) | Each person has one account, signed in with their email, holding one or more roles | Accepted; extended by ADR-069; amended by ADR-045 |
| [ADR-040](ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md) | The core is a Spring Modulith monolith with one module per bounded context, checked in CI | Accepted; extended by ADR-097, ADR-118 and ADR-120 |
| [ADR-118](ADR-118-a-domain-package-above-30-files-splits-by-aggregate.md) | A domain package above 30 files splits into one package per aggregate, over a root that holds the module's error | Accepted; amended by ADR-120 and ADR-125 |
| [ADR-125](ADR-125-a-folder-above-20-files-splits-wherever-a-group-of-four-can-move.md) | A source folder above 20 files splits wherever a group of four files can move | Accepted |
| [ADR-097](ADR-097-the-shared-kernel-holds-the-domains-building-blocks.md) | The shared kernel also holds the building blocks every module's domain uses | Accepted; extended by ADR-106 and ADR-111 |
| [ADR-106](ADR-106-free-text-is-normalized-and-capped-by-one-kernel-rule.md) | Free text is normalized and capped by one kernel rule, and personal values carry a marker | Accepted; extended by ADR-111 |
| [ADR-111](ADR-111-attempt-is-a-shared-kernel-value-and-names-and-addresses-drop-invisible-fillers.md) | Attempt is a shared-kernel value, and names and addresses drop invisible fillers | Accepted |
| [ADR-119](ADR-119-the-application-layer-is-declared-ahead-as-signatures-whose-bodies-throw.md) | The application layer is declared ahead of its milestones, as signatures whose bodies throw | Accepted; amended by ADR-121 |
| [ADR-120](ADR-120-the-application-layer-takes-an-actor-and-a-call-context-and-reads-through-query-ports.md) | The application layer takes an Actor and a CallContext, keeps its shared ports in shared.application, and reads through query ports | Accepted; extended by ADR-125; amended by ADR-121 |
| [ADR-121](ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md) | The application skeleton's review settles its seams, its conflicts, its retention and its scoring inputs | Accepted |
| [ADR-041](ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md) | Each module owns a PostgreSQL schema, read and written through jOOQ by a role that owns no table | Accepted |
| [ADR-043](ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md) | Code is generated from both contracts on both sides, and errors are RFC 9457 Problem Details | Accepted; extended by ADR-068, ADR-073, ADR-087 and ADR-094; amended by ADR-123 |
| [ADR-094](ADR-094-each-pull-requests-contract-check-compares-against-its-base-branch.md) | Each pull request's contract check compares against its base branch | Accepted |
| [ADR-044](ADR-044-solver-port-takes-start-budget-and-seed-and-problem-judges.md) | The solver port takes a start, a budget and a seed, and `Problem` alone judges a solution | Accepted; extended by ADR-050 and ADR-099; amended by ADR-105, ADR-120 and ADR-121 |
| [ADR-045](ADR-045-generation-counters-revoke-tokens.md) | Generation counters on the user and the session revoke tokens | Accepted; amended by ADR-104 |
| [ADR-048](ADR-048-travel-times-from-buckets-and-a-buffer-with-no-live-traffic.md) | Travel times come from time-of-day buckets and a buffer, with no live traffic | Accepted; amended by ADR-098 |
| [ADR-098](ADR-098-the-validator-works-in-double-precision-with-a-tolerance.md) | The validator works in double precision, compares limits within 1e-6, and rounds only product travel times | Accepted |
| [ADR-099](ADR-099-a-route-may-carry-the-starts-its-solver-claims.md) | A route may carry the service starts its solver claims, and the validator checks them | Accepted |
| [ADR-049](ADR-049-drop-penalty-unit-k-scaled-by-priority.md) | An order's drop penalty is K times 6 minus its priority, with K = 5L + 2T + F + 1 | Accepted; amended by ADR-100 and ADR-105 |
| [ADR-100](ADR-100-k-covers-door-windows-outside-a-shift-and-no-vehicle-is-a-drop-reason.md) | K's L covers door windows outside a shift, and *no vehicle available* is a drop reason | Accepted; amended by ADR-105 |
| [ADR-050](ADR-050-re-optimization-conventions-live-only-in-the-python-problem-factory.md) | Re-optimization conventions live only in the Python problem factory | Accepted; extended by ADR-105 |
| [ADR-105](ADR-105-a-re-optimizations-problem-carries-its-conventions-and-only-a-vehicle-that-is-out-has-no-driver.md) | A re-optimization's problem carries its conventions, and only a vehicle that is out has no driver | Accepted; amended by ADR-112 and ADR-113 |
| [ADR-113](ADR-113-route-stability-is-its-own-cost-the-rules-read-a-problem-through-a-protocol-and-untimed-outputs-are-documented.md) | Route stability is a cost of its own, the rules read a problem through a protocol, and untimed and past-the-day outputs are documented | Accepted |
| [ADR-051](ADR-051-one-solver-worker-per-lane-killed-30-seconds-past-its-limit.md) | Each lane has one solver worker process, killed 30 seconds past its limit | Accepted |
| [ADR-052](ADR-052-each-plan-stores-the-matrix-and-values-it-was-solved-with.md) | Each plan stores the travel-time matrix and the values it was solved with | Accepted; amended by ADR-121 |
| [ADR-053](ADR-053-plan-lifecycle-publishing-stability-drop-reasons-and-abandoned-plans.md) | A plan follows one lifecycle that ends in completed, cancelled or abandoned, and every drop has a reason | Accepted; amended by ADR-100 and ADR-101 |
| [ADR-101](ADR-101-completed-plans-take-late-outcomes-as-revisions-and-routes-keep-an-index.md) | A completed plan takes late outcomes as revisions, a hand-over takes an optional check, and each route keeps an index from 1 to 30 | Accepted; amended by ADR-112 |
| [ADR-112](ADR-112-a-re-optimization-keeps-every-order-and-route-a-solve-that-breaks-a-lock-fails-and-publishing-clears-locks.md) | A re-optimization keeps every order and route, a solve that breaks a lock fails, and publishing clears locks | Accepted |
| [ADR-054](ADR-054-publishing-re-checks-every-route-against-the-current-fleet.md) | Publishing re-checks every route against the current fleet and orders | Accepted |
| [ADR-055](ADR-055-accepting-a-re-optimization-checks-the-vehicles-it-keeps-driving.md) | Accepting a re-optimization checks the vehicles it keeps driving | Accepted |
| [ADR-056](ADR-056-accepting-a-re-optimization-checks-changes-recorded-since-its-basis.md) | Accepting a re-optimization re-checks every change recorded since its preview | Accepted |
| [ADR-057](ADR-057-concurrent-changes-to-a-plan-use-optimistic-locking.md) | Concurrent changes to a plan use optimistic locking, and one re-optimization runs at a time | Accepted; amended by ADR-121 |
| [ADR-058](ADR-058-routes-changed-while-the-optimizer-is-down-are-checked-when-it-returns.md) | A route changed while the optimizer is down is marked not yet checked, and checked when it returns | Accepted |
| [ADR-059](ADR-059-plan-revisions-are-stored-as-rows.md) | Each published plan revision is stored as rows, one per stop | Accepted; extended by ADR-121 |
| [ADR-060](ADR-060-hard-limits-on-fleet-and-plan-size.md) | The system refuses a sixteenth vehicle and a date's 201st order | Accepted |
| [ADR-061](ADR-061-capacity-is-weight-and-volume.md) | A vehicle's capacity is a weight in kilograms and a volume in litres, both required and positive | Accepted |
| [ADR-062](ADR-062-fleet-checks-the-driver-role-when-it-reads.md) | Fleet asks Access for the Driver role each time it reads a driver, and keeps no copy | Accepted |
| [ADR-103](ADR-103-fleet-normalizes-plates-keeps-one-pair-of-depot-hours-holds-no-vehicle-in-weekly-shifts-and-retires-and-anonymizes-drivers-through-events.md) | Fleet normalizes plates, keeps one pair of depot hours, holds no vehicle in weekly shifts, and retires and anonymizes drivers through events | Accepted |
| [ADR-063](ADR-063-a-transaction-changes-one-aggregate-and-every-rule-has-one-owner.md) | A transaction changes one existing aggregate, and every business rule has exactly one owner | Accepted |
| [ADR-064](ADR-064-events-only-where-they-earn-their-place-through-the-modulith-registry.md) | Events exist only where they earn their place, and Spring Modulith's registry delivers them | Accepted; amended by ADR-101, ADR-103, ADR-104, ADR-107 and ADR-115 |
| [ADR-065](ADR-065-facts-outrank-intent.md) | Facts outrank intent: an accepted report stands, the plan is corrected, and a person is told | Accepted; amended by ADR-121 |
| [ADR-066](ADR-066-every-asynchronous-step-has-a-watcher.md) | Every asynchronous step that crosses a commit has a watcher | Accepted |
| [ADR-067](ADR-067-live-arrivals-propagate-delay-along-stored-legs-when-asked.md) | Live arrival times propagate delay along the stored legs, and are computed when asked | Accepted |
| [ADR-073](ADR-073-runtime-contract-validation.md) | A request that breaks its contract is refused by validation generated from the contract | Accepted |
| [ADR-082](ADR-082-supported-phone-app-versions.md) | The server supports the current and the previous phone app version, and an older one still drains its queue | Accepted; amended by ADR-094 |
| [ADR-083](ADR-083-admins-and-dispatchers-get-alerts-by-stream-and-push-in-the-phone-app.md) | Admins and Dispatchers get alerts in the phone app by event stream while it is open and by push in the background | Accepted |
| [ADR-084](ADR-084-maplibre-in-both-apps-with-self-hosted-protomaps-tiles.md) | Both apps draw maps with MapLibre, from self-hosted Protomaps tiles | Accepted |
| [ADR-085](ADR-085-govmap-geocodes-addresses-with-nominatim-as-the-fallback.md) | GovMap geocodes addresses, and self-hosted Nominatim replaces it only if GovMap's terms fail | Accepted; amended by ADR-121 |
| [ADR-086](ADR-086-csv-imports-upload-then-confirm-within-an-hour.md) | An order import is two calls, upload then confirm within an hour, and writes every row or none | Accepted; extended by ADR-102 |
| [ADR-102](ADR-102-order-references-are-checked-on-every-move-to-a-date.md) | Order references are checked on every move to a date, and the abbreviation table starts with three | Accepted; amended by ADR-115 |
| [ADR-115](ADR-115-import-rows-keep-their-own-recipient-put-backs-raise-an-event-and-orders-clamp-a-stepped-back-clock.md) | An import row keeps its own recipient, a put-back onto another date raises `OrderPutBack`, and Orders' commands clamp a clock that stepped back | Accepted |
| [ADR-087](ADR-087-driver-reports-sync-through-an-idempotent-outbox.md) | Driver reports wait in an outbox on the phone and reach the server with a user-scoped idempotency key | Accepted; extended by ADR-121 |
| [ADR-107](ADR-107-live-operations-alerts-once-per-key-corrects-a-put-back-by-event-and-treats-a-reschedule-as-a-cancellation.md) | Live Operations alerts once per key, corrects a put-back by event, and treats a reschedule as a cancellation | Accepted; extended by ADR-121; amended by ADR-114 |
| [ADR-114](ADR-114-live-operations-reaches-planning-from-its-own-listener-and-names-every-fact-it-judges.md) | Live Operations reaches Planning from its own listener, and names every fact it judges | Accepted; extended by ADR-121 |
| [ADR-109](ADR-109-reporting-computes-exact-on-time-shares-from-current-facts-and-keeps-no-personal-data.md) | Reporting computes exact on-time shares from current facts and keeps no personal data | Accepted; extended by ADR-112 |
| [ADR-088](ADR-088-israel-local-time-for-every-date-and-schedule.md) | Every date, time window, shift and schedule is Israel local time, held as elapsed seconds in a solve | Accepted; amended by ADR-098 |
| [ADR-089](ADR-089-interface-languages.md) | Both apps are available in Hebrew, right to left, and in English only | Accepted |
| [ADR-090](ADR-090-scope-cuts-for-the-first-release.md) | The first release cuts ten capabilities, and each has its own trigger to reopen | Accepted |
| [ADR-091](ADR-091-study-design-for-the-solver-comparison.md) | The solver comparison follows eleven fixed design choices, and a mutation run checks the validator first | Accepted |
| [ADR-092](ADR-092-the-alns-package-runs-our-solvers-outer-loop.md) | The `alns` package runs our solver's outer loop, and the rest of the solver stays ours | Accepted |
| **Repository, build and delivery** | | |
| [ADR-008](ADR-008-monorepo-layout-under-src.md) | One repository, with the code under `src/backend`, `src/frontend` and `src/research` | Superseded by ADR-037 |
| [ADR-009](ADR-009-just-as-the-single-entry-point.md) | The root `justfile` is the single entry point, and CI runs the same recipes | Accepted; amended by ADR-037, ADR-038 and ADR-123 |
| [ADR-010](ADR-010-toolchain-pins-in-version-files.md) | Toolchain versions are pinned in per-language version files | Superseded by ADR-038 |
| [ADR-011](ADR-011-python-tooling-uv-ruff-mypy-pytest.md) | Python projects use uv, ruff, mypy in strict mode and pytest | Accepted |
| [ADR-012](ADR-012-javascript-tooling-npm-eslint-prettier.md) | Both frontend apps use npm, ESLint and Prettier, with TypeScript held at 6.0 | Superseded by ADR-039 |
| [ADR-013](ADR-013-java-tooling-maven-wrapper-and-spotless.md) | The core backend builds with the Maven wrapper and formats with Spotless and palantir-java-format | Accepted; amended by ADR-038 |
| [ADR-014](ADR-014-real-dependencies-in-containers-for-tests-and-local-runs.md) | Integration tests and local runs use a real PostgreSQL in a container | Superseded by ADR-042 |
| [ADR-015](ADR-015-ci-one-workflow-one-job-per-component.md) | CI is one workflow with one job per component, and Dependabot keeps dependencies current | Accepted; extended by ADR-021; amended by ADR-046 |
| [ADR-016](ADR-016-dependency-audit-with-osv-scanner.md) | One OSV-Scanner pass audits every lockfile, and an accepted vulnerability expires | Accepted; extended by ADR-023 and ADR-072; amended by ADR-095 |
| [ADR-023](ADR-023-accept-minimatch-and-glob-advisories.md) | Accept the minimatch and glob advisories in the frontends' tooling until 2027-01-31 | Accepted; extended by ADR-095 |
| [ADR-095](ADR-095-accept-the-zod-and-deepmerge-advisories-until-2027-01-31.md) | Accept the zod and deepmerge advisories in the phone app's tooling until 2027-01-31 | Accepted |
| [ADR-037](ADR-037-flat-layout-under-src-with-deploy-and-infra.md) | The code moves to a flat layout under `src/`, with deployment in `deploy/` and Terraform in `infra/` | Superseded by ADR-123 |
| [ADR-123](ADR-123-the-code-keeps-its-grouped-layout-and-three-components-take-new-names.md) | The code keeps its grouped layout, and three components take new names | Accepted |
| [ADR-038](ADR-038-mise-pins-every-toolchain-and-core-moves-to-java-25.md) | mise pins every toolchain, and the core moves to Java 25 | Accepted |
| [ADR-039](ADR-039-one-pnpm-workspace-for-both-apps-and-shared-packages.md) | Both apps and the shared TypeScript packages live in one pnpm workspace | Accepted; amended by ADR-123 |
| [ADR-042](ADR-042-compose-dev-for-local-runs-testcontainers-for-tests.md) | Local runs start their services from `compose.dev.yaml`; tests keep Testcontainers | Accepted; amended by ADR-123 |
| [ADR-046](ADR-046-renovate-replaces-dependabot-and-targets-dev.md) | Renovate replaces Dependabot and keeps opening updates against `dev` | Accepted |
| [ADR-072](ADR-072-images-scanned-by-pinned-trivy-and-secrets-by-gitleaks.md) | A pinned Trivy scans images and Terraform, and Gitleaks scans the full history for secrets | Accepted |
| [ADR-074](ADR-074-dependency-licences-checked-against-an-allow-list.md) | Dependency licences are checked in CI against an allow-list, and GPL and AGPL services run unmodified | Accepted |
| [ADR-081](ADR-081-eas-update-for-javascript-only-fixes-during-the-pilot.md) | JavaScript-only fixes reach the phone app through EAS Update during the pilot | Accepted |
| **Project and team** | | |
| [ADR-017](ADR-017-proprietary-licence.md) | Release the code under a proprietary licence, all rights reserved | Accepted |
| [ADR-018](ADR-018-ai-assistant-context-in-the-repository.md) | The AI assistant's context lives in the repository | Accepted; extended by ADR-020, ADR-025, ADR-096 and ADR-124; amended by ADR-037 and ADR-123 |
| [ADR-096](ADR-096-the-root-claude-md-is-agreed-and-no-longer-a-draft.md) | The root CLAUDE.md is agreed, and is no longer a draft | Accepted |
| [ADR-124](ADR-124-a-claude-md-holds-only-what-a-session-would-otherwise-get-wrong.md) | A CLAUDE.md holds only what a session would otherwise get wrong, in the narrowest folder its rule governs | Accepted |
| [ADR-020](ADR-020-team-subagents-in-the-repository.md) | Specialised team subagents live in `.claude/agents/`, beside each person's own | Accepted; extended by ADR-022, ADR-033 and ADR-124 |
| [ADR-021](ADR-021-dependabot-targets-dev-and-skips-pinned-majors.md) | Dependabot opens its updates against `dev`, and skips the versions other ADRs pin | Superseded by ADR-046 |
| [ADR-022](ADR-022-personal-subagents-come-before-team-subagents.md) | Each person's own subagents come before the team's | Accepted; extended by ADR-025 and ADR-033 |
| [ADR-025](ADR-025-team-skills-in-the-repository.md) | Team skills live in `.claude/skills/`, and only a person can start them | Accepted; extended by ADR-033 and ADR-034; amended by ADR-122 |
| [ADR-033](ADR-033-sync-with-dev-reviews-the-merge-on-sonnet.md) | `/sync-with-dev` reviews a merge with df-code-reviewer on Sonnet, and fixes what it finds | Accepted; amended by ADR-122 |
| [ADR-034](ADR-034-ask-before-merging-changes-to-the-assistant-setup.md) | Both team skills ask before merging a `dev` change to the assistant's setup | Accepted |
| [ADR-122](ADR-122-the-finish-skill-runs-no-agent-review.md) | The finish skill runs no agent review, and keeps its check for secrets | Accepted |
| [ADR-093](ADR-093-the-implementation-plan-is-retired-and-a-roadmap-holds-the-dates.md) | The implementation plan is retired, and a roadmap holds the dates | Accepted |
| [ADR-080](ADR-080-team-accounts-stand-in-when-there-is-no-company.md) | The team's own accounts stand in when there is no company | Accepted |
| **Deployment** | | |
| [ADR-019](ADR-019-container-images-run-as-non-root.md) | Each service ships as a container image that runs as a non-root user | Accepted; amended by ADR-047 |
| [ADR-027](ADR-027-proof-files-on-the-vm-disk-with-hourly-backups.md) | Proof files stay on the VM's disk, and backups run hourly during operating hours | Accepted |
| [ADR-028](ADR-028-driver-app-through-test-tracks-with-a-device-checklist.md) | The phone app ships through test tracks, and real phones are checked by hand | Accepted; extended by ADR-035 and ADR-081; amended by ADR-080 |
| [ADR-047](ADR-047-images-pinned-by-digest-and-core-on-temurin-25-jre.md) | Images are pinned by digest, and the core runs on the Temurin 25 JRE | Accepted; amended by ADR-123 |
| [ADR-078](ADR-078-hosting-on-one-aws-vm-in-tel-aviv-built-with-terraform.md) | DispatchFlow runs on one AWS VM in Tel Aviv, built with Terraform and capped at ₪550 a month | Accepted |
| [ADR-079](ADR-079-client-errors-and-crashes-go-to-self-hosted-glitchtip.md) | Client errors and crashes go to GlitchTip on the VM, through the Sentry SDKs | Accepted |
| **Security and personal data** | | |
| [ADR-068](ADR-068-every-web-handler-is-authorized-by-role-from-the-contract.md) | Every web handler is authorized by role, from the `x-capability` of its contract operation | Accepted; amended by ADR-104 and ADR-120 |
| [ADR-104](ADR-104-the-company-profile-holds-customer-defaults-and-capabilities-follow-the-role-matrix.md) | The company profile holds a new customer's defaults, and capabilities follow the role matrix | Accepted; extended by ADR-121; amended by ADR-116 |
| [ADR-116](ADR-116-only-an-admin-changes-their-own-email.md) | Only an Admin changes their own email | Accepted |
| [ADR-069](ADR-069-sign-in-and-transport-security.md) | Sign-in uses signed access tokens, rotating refresh tokens and Argon2id, over HTTPS through Caddy | Accepted; extended by ADR-121 |
| [ADR-070](ADR-070-security-and-accessibility-standards.md) | We self-check against OWASP ASVS 5.0 Level 1 and build to WCAG 2.2 AA, with no separate target for IS 5568 | Accepted |
| [ADR-071](ADR-071-api-rate-limits-in-process-with-bucket4j.md) | API rate limits run in the core's process, with Bucket4j | Accepted; extended by ADR-121 |
| [ADR-075](ADR-075-retention-deletes-records-and-anonymizes-people-in-place.md) | Retention deletes positions, proofs and messages, and anonymizes customers and drivers in place | Accepted; extended by ADR-121; amended by ADR-102 |
| [ADR-110](ADR-110-audit-records-hold-only-ids-codes-enums-numbers-and-instants-record-refusals-and-are-kept-a-year.md) | Audit records hold only IDs, codes, enums, numbers and instants, record refusals, and are kept a year | Accepted; extended by ADR-114, ADR-120 and ADR-121 |
| [ADR-076](ADR-076-admin-and-dispatcher-data-stays-in-memory-on-devices.md) | Admin and Dispatcher data stays in memory on devices, and sign-out wipes it | Accepted |
| [ADR-077](ADR-077-push-builds-and-app-stores-are-accepted-exceptions-to-hosting-in-israel.md) | Push, app builds, the app stores and map-app links are accepted exceptions to hosting in Israel, on conditions | Accepted |

> [!NOTE]
> Numbering restarted at ADR-001 on 2026-10-30. ADR-004 and ADR-005 are older (2026-10-27) and
> kept their numbers.

> [!IMPORTANT]
> On 2026-11-13 and 2026-11-14 the owner, Naim Elijah, settled every proposed and open item in the
> requirements and the design documents for the team. ADR-037 to ADR-047 were accepted then, and
> ADR-048 to ADR-093 record the rest, including every decision the requirements marked
> `ADR pending`. ADR-094 to ADR-096, on 2026-11-14, settle three points that were found still open
> afterwards. ADR-097 to ADR-110, from 2026-11-14, record decisions taken while the domain layer was
> being built. ADR-111 to ADR-118, on 2026-11-15, record the owner's decisions on the review of that
> layer, and the split of its packages. ADR-119 to ADR-121, on 2026-11-15, record the
> application layer's skeleton and its review. ADR-122, on 2026-11-18, drops the finish skill's agent
> review. ADR-123, on 2026-11-22, drops ADR-037's flat tree and renames three components. ADR-124,
> on 2026-11-22, cuts each `CLAUDE.md` to what a session would otherwise get wrong. ADR-125, on
> 2026-11-22, splits a source folder above 20 files wherever four of its files can move. The tech
> stack's [§22](../architecture/tech_stack.md#22-open-decisions) lists what is still open.
