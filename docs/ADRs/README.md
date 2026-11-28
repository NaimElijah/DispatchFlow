# DispatchFlow - Architecture Decision Records

One file per decision, named `ADR-NNN-short-kebab-title.md`. Records are append-only. To change
course, add a new ADR and mark the old one `Superseded by ADR-NNN`. How to write one is in
[`../CLAUDE.md`](../CLAUDE.md#adrs).

| ADR | Decision | Status |
|---|---|---|
| **Architecture** | | |
| [ADR-001](ADR-001-domain-driven-design-with-hexagonal-architecture.md) | Domain-driven design, with every service built as ports and adapters | Accepted; amended by ADR-040 and ADR-126 |
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
| [ADR-040](ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md) | The core is a Spring Modulith monolith with one module per bounded context, checked in CI | Accepted; extended by ADR-097, ADR-118, ADR-120, ADR-130 and ADR-140; amended by ADR-126 |
| [ADR-118](ADR-118-a-domain-package-above-30-files-splits-by-aggregate.md) | A domain package above 30 files splits into one package per aggregate, over a root that holds the module's error | Accepted; amended by ADR-120 and ADR-125 |
| [ADR-125](ADR-125-a-folder-above-20-files-splits-wherever-a-group-of-four-can-move.md) | A source folder above 20 files splits wherever a group of four files can move | Accepted; extended by ADR-126, ADR-129 and ADR-130 |
| [ADR-097](ADR-097-the-shared-kernel-holds-the-domains-building-blocks.md) | The shared kernel also holds the building blocks every module's domain uses | Accepted; extended by ADR-106, ADR-111 and ADR-128; amended by ADR-126 |
| [ADR-106](ADR-106-free-text-is-normalized-and-capped-by-one-kernel-rule.md) | Free text is normalized and capped by one kernel rule, and personal values carry a marker | Accepted; extended by ADR-111 |
| [ADR-111](ADR-111-attempt-is-a-shared-kernel-value-and-names-and-addresses-drop-invisible-fillers.md) | Attempt is a shared-kernel value, and names and addresses drop invisible fillers | Accepted |
| [ADR-119](ADR-119-the-application-layer-is-declared-ahead-as-signatures-whose-bodies-throw.md) | The application layer is declared ahead of its milestones, as signatures whose bodies throw | Accepted; extended by ADR-130; amended by ADR-121, ADR-129 and ADR-132 |
| [ADR-120](ADR-120-the-application-layer-takes-an-actor-and-a-call-context-and-reads-through-query-ports.md) | The application layer takes an Actor and a CallContext, keeps its shared ports in shared.application, and reads through query ports | Accepted; extended by ADR-125, ADR-130, ADR-131 and ADR-140; amended by ADR-121 |
| [ADR-121](ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md) | The application skeleton's review settles its seams, its conflicts, its retention and its scoring inputs | Accepted; extended by ADR-131, ADR-136 and ADR-140; amended by ADR-126, ADR-132 and ADR-139 |
| [ADR-041](ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md) | Each module owns a PostgreSQL schema, read and written through jOOQ by a role that owns no table | Accepted; amended by ADR-126, ADR-127 and ADR-145 |
| [ADR-126](ADR-126-jpa-replaces-jooq-and-the-domain-carries-its-mapping.md) | JPA replaces jOOQ in the core, and the domain classes carry their own mapping | Accepted; extended by ADR-130; amended by ADR-127 and ADR-128 |
| [ADR-127](ADR-127-how-the-aggregates-are-mapped-and-where-their-stored-state-is-tested.md) | How the 20 aggregates are mapped, and where their stored state is tested | Accepted; extended by ADR-128 and ADR-145 |
| [ADR-128](ADR-128-the-persistence-rules-the-mapping-settled-after-adr-127.md) | The persistence rules the JPA mapping settled after ADR-127 | Accepted |
| [ADR-129](ADR-129-transactions-start-only-in-the-service-layer.md) | Transactions start only in the service layer | Accepted; extended and amended by ADR-149 |
| [ADR-136](ADR-136-the-core-waits-two-seconds-for-a-lock-and-a-lock-it-cannot-get-is-retried.md) | The core waits at most 2 seconds for a lock, and a call that can't get one is retried | Accepted; extended by ADR-145; amended by ADR-140 |
| [ADR-145](ADR-145-the-migrate-command-runs-flyway-as-the-owner-and-the-core-connects-as-app.md) | The migrate command runs Flyway as the owner, and the core connects as app with only what its adapters run | Accepted; extended by ADR-151; amended by ADR-146 |
| [ADR-146](ADR-146-the-jar-takes-only-options-but-for-its-command-and-the-roles-guards-cover-every-object-and-log.md) | The jar takes only options but for its command, and the roles' guards cover every object and log | Accepted; extended by ADR-151 |
| [ADR-130](ADR-130-every-folder-is-declared-ahead-empty-and-documented.md) | Every folder the design draws is declared ahead, empty and documented | Accepted; extended by ADR-140; amended by ADR-131, ADR-132 and ADR-133 |
| [ADR-131](ADR-131-the-declared-structures-review-tightens-its-rules-and-fills-its-gaps.md) | The declared structure's review tightens its rules and fills its gaps | Accepted; extended by ADR-140 |
| [ADR-132](ADR-132-unbuilt-bodies-say-to-be-implemented.md) | Unbuilt bodies say "To Be Implemented." and name no milestone | Accepted |
| [ADR-133](ADR-133-a-startup-step-is-a-job-and-access-declares-one.md) | A step that runs once at startup is a `job` adapter, and Access declares one | Accepted; extended by ADR-147 |
| [ADR-140](ADR-140-the-app-wide-web-package-sits-above-the-modules.md) | The app-wide `web` package sits above the modules, and what they need from it sits beside the kernel | Accepted; extended by ADR-147 and ADR-148; amended by ADR-142, ADR-147, ADR-148 and ADR-150 |
| [ADR-142](ADR-142-the-call-wrappers-retry-its-400s-and-its-error-endpoint-as-162-built-them.md) | The call wrapper's retry, its 400s and its error endpoint, as #162 built them | Accepted; amended by ADR-149 |
| [ADR-147](ADR-147-the-stand-in-condition-sits-in-the-root-package-and-a-refused-access-token-answers-one-code.md) | The stand-in condition sits in the root package, a deployed environment is told by a marker and three keys, and a refused access token answers one code | Accepted; amended by ADR-148 and ADR-149 |
| [ADR-148](ADR-148-the-stand-in-serves-only-its-own-origin-every-unsigned-api-call-answers-401-and-the-guard-outlives-the-session.md) | The stand-in serves only its own origin, every unsigned call under `/api/v1` answers 401, and the guard outlives the session | Accepted |
| [ADR-149](ADR-149-the-use-case-records-its-refusal-after-the-rollback-and-the-audit-purge-counts-its-year-back-from-now.md) | The use case records its refusal after the rollback, and the audit purge counts its year back from now | Accepted; extended and amended by ADR-150 |
| [ADR-150](ADR-150-one-audit-helper-records-the-refusals-a-use-case-sees-and-the-contracts-handler-records-a-changing-call-it-refuses.md) | One Audit helper records the refusals a use case sees, and the contract's handler records a changing call it refuses | Accepted |
| [ADR-043](ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md) | Code is generated from both contracts on both sides, and errors are RFC 9457 Problem Details | Accepted; extended by ADR-068, ADR-073, ADR-087, ADR-094 and ADR-140; amended by ADR-123 and ADR-153 |
| [ADR-094](ADR-094-each-pull-requests-contract-check-compares-against-its-base-branch.md) | Each pull request's contract check compares against its base branch | Accepted |
| [ADR-044](ADR-044-solver-port-takes-start-budget-and-seed-and-problem-judges.md) | The solver port takes a start, a budget and a seed, and `Problem` alone judges a solution | Accepted; extended by ADR-050 and ADR-099; amended by ADR-105, ADR-120 and ADR-121 |
| [ADR-045](ADR-045-generation-counters-revoke-tokens.md) | Generation counters on the user and the session revoke tokens | Accepted; extended by ADR-131; amended by ADR-104 |
| [ADR-048](ADR-048-travel-times-from-buckets-and-a-buffer-with-no-live-traffic.md) | Travel times come from time-of-day buckets and a buffer, with no live traffic | Accepted; amended by ADR-098 |
| [ADR-098](ADR-098-the-validator-works-in-double-precision-with-a-tolerance.md) | The validator works in double precision, compares limits within 1e-6, and rounds only product travel times | Accepted |
| [ADR-099](ADR-099-a-route-may-carry-the-starts-its-solver-claims.md) | A route may carry the service starts its solver claims, and the validator checks them | Accepted |
| [ADR-049](ADR-049-drop-penalty-unit-k-scaled-by-priority.md) | An order's drop penalty is K times 6 minus its priority, with K = 5L + 2T + F + 1 | Accepted; amended by ADR-100 and ADR-105 |
| [ADR-100](ADR-100-k-covers-door-windows-outside-a-shift-and-no-vehicle-is-a-drop-reason.md) | K's L covers door windows outside a shift, and *no vehicle available* is a drop reason | Accepted; amended by ADR-105 |
| [ADR-050](ADR-050-re-optimization-conventions-live-only-in-the-python-problem-factory.md) | Re-optimization conventions live only in the Python problem factory | Accepted; extended by ADR-105 |
| [ADR-105](ADR-105-a-re-optimizations-problem-carries-its-conventions-and-only-a-vehicle-that-is-out-has-no-driver.md) | A re-optimization's problem carries its conventions, and only a vehicle that is out has no driver | Accepted; amended by ADR-112 and ADR-113 |
| [ADR-113](ADR-113-route-stability-is-its-own-cost-the-rules-read-a-problem-through-a-protocol-and-untimed-outputs-are-documented.md) | Route stability is a cost of its own, the rules read a problem through a protocol, and untimed and past-the-day outputs are documented | Accepted |
| [ADR-051](ADR-051-one-solver-worker-per-lane-killed-30-seconds-past-its-limit.md) | Each lane has one solver worker process, killed 30 seconds past its limit | Accepted; extended by ADR-131 |
| [ADR-052](ADR-052-each-plan-stores-the-matrix-and-values-it-was-solved-with.md) | Each plan stores the travel-time matrix and the values it was solved with | Accepted; amended by ADR-121 |
| [ADR-053](ADR-053-plan-lifecycle-publishing-stability-drop-reasons-and-abandoned-plans.md) | A plan follows one lifecycle that ends in completed, cancelled or abandoned, and every drop has a reason | Accepted; amended by ADR-100 and ADR-101 |
| [ADR-101](ADR-101-completed-plans-take-late-outcomes-as-revisions-and-routes-keep-an-index.md) | A completed plan takes late outcomes as revisions, a hand-over takes an optional check, and each route keeps an index from 1 to 30 | Accepted; amended by ADR-112 and ADR-126 |
| [ADR-112](ADR-112-a-re-optimization-keeps-every-order-and-route-a-solve-that-breaks-a-lock-fails-and-publishing-clears-locks.md) | A re-optimization keeps every order and route, a solve that breaks a lock fails, and publishing clears locks | Accepted |
| [ADR-054](ADR-054-publishing-re-checks-every-route-against-the-current-fleet.md) | Publishing re-checks every route against the current fleet and orders | Accepted |
| [ADR-055](ADR-055-accepting-a-re-optimization-checks-the-vehicles-it-keeps-driving.md) | Accepting a re-optimization checks the vehicles it keeps driving | Accepted |
| [ADR-056](ADR-056-accepting-a-re-optimization-checks-changes-recorded-since-its-basis.md) | Accepting a re-optimization re-checks every change recorded since its preview | Accepted |
| [ADR-057](ADR-057-concurrent-changes-to-a-plan-use-optimistic-locking.md) | Concurrent changes to a plan use optimistic locking, and one re-optimization runs at a time | Accepted; extended by ADR-153; amended by ADR-121 and ADR-140 |
| [ADR-058](ADR-058-routes-changed-while-the-optimizer-is-down-are-checked-when-it-returns.md) | A route changed while the optimizer is down is marked not yet checked, and checked when it returns | Accepted |
| [ADR-059](ADR-059-plan-revisions-are-stored-as-rows.md) | Each published plan revision is stored as rows, one per stop | Accepted; extended by ADR-121 |
| [ADR-060](ADR-060-hard-limits-on-fleet-and-plan-size.md) | The system refuses a sixteenth vehicle and a date's 201st order | Accepted |
| [ADR-061](ADR-061-capacity-is-weight-and-volume.md) | A vehicle's capacity is a weight in kilograms and a volume in litres, both required and positive | Accepted |
| [ADR-062](ADR-062-fleet-checks-the-driver-role-when-it-reads.md) | Fleet asks Access for the Driver role each time it reads a driver, and keeps no copy | Accepted |
| [ADR-103](ADR-103-fleet-normalizes-plates-keeps-one-pair-of-depot-hours-holds-no-vehicle-in-weekly-shifts-and-retires-and-anonymizes-drivers-through-events.md) | Fleet normalizes plates, keeps one pair of depot hours, holds no vehicle in weekly shifts, and retires and anonymizes drivers through events | Accepted |
| [ADR-063](ADR-063-a-transaction-changes-one-aggregate-and-every-rule-has-one-owner.md) | A transaction changes one existing aggregate, and every business rule has exactly one owner | Accepted; extended by ADR-129 |
| [ADR-064](ADR-064-events-only-where-they-earn-their-place-through-the-modulith-registry.md) | Events exist only where they earn their place, and Spring Modulith's registry delivers them | Accepted; extended by ADR-129; amended by ADR-101, ADR-103, ADR-104, ADR-107 and ADR-115 |
| [ADR-065](ADR-065-facts-outrank-intent.md) | Facts outrank intent: an accepted report stands, the plan is corrected, and a person is told | Accepted; amended by ADR-121 |
| [ADR-066](ADR-066-every-asynchronous-step-has-a-watcher.md) | Every asynchronous step that crosses a commit has a watcher | Accepted |
| [ADR-067](ADR-067-live-arrivals-propagate-delay-along-stored-legs-when-asked.md) | Live arrival times propagate delay along the stored legs, and are computed when asked | Accepted |
| [ADR-073](ADR-073-runtime-contract-validation.md) | A request that breaks its contract is refused by validation generated from the contract | Accepted; extended by ADR-140 |
| [ADR-082](ADR-082-supported-phone-app-versions.md) | The server supports the current and the previous phone app version, and an older one still drains its queue | Accepted; extended by ADR-140; amended by ADR-094 |
| [ADR-083](ADR-083-admins-and-dispatchers-get-alerts-by-stream-and-push-in-the-phone-app.md) | Admins and Dispatchers get alerts in the phone app by event stream while it is open and by push in the background | Accepted |
| [ADR-084](ADR-084-maplibre-in-both-apps-with-self-hosted-protomaps-tiles.md) | Both apps draw maps with MapLibre, from self-hosted Protomaps tiles | Accepted; amended by ADR-158 |
| [ADR-158](ADR-158-the-alphas-tiles-come-from-a-pinned-protomaps-build-checked-by-hash.md) | The Alpha's tiles come from a pinned Protomaps build, checked by hash, until the monthly refresh keeps its own cut | Accepted |
| [ADR-085](ADR-085-govmap-geocodes-addresses-with-nominatim-as-the-fallback.md) | GovMap geocodes addresses, and self-hosted Nominatim replaces it only if GovMap's terms fail | Accepted; amended by ADR-121 and ADR-139 |
| [ADR-086](ADR-086-csv-imports-upload-then-confirm-within-an-hour.md) | An order import is two calls, upload then confirm within an hour, and writes every row or none | Accepted; extended by ADR-102; amended by ADR-139 |
| [ADR-139](ADR-139-govmap-geocoding-comes-into-the-alpha.md) | GovMap geocoding comes into the Alpha, behind the `Geocoder` port, and the demo reaches the real GovMap | Accepted |
| [ADR-102](ADR-102-order-references-are-checked-on-every-move-to-a-date.md) | Order references are checked on every move to a date, and the abbreviation table starts with three | Accepted; extended by ADR-137; amended by ADR-115 |
| [ADR-137](ADR-137-the-address-abbreviation-table-gains-six-entries.md) | The address abbreviation table gains six entries, and ambiguous ones stay out | Accepted |
| [ADR-115](ADR-115-import-rows-keep-their-own-recipient-put-backs-raise-an-event-and-orders-clamp-a-stepped-back-clock.md) | An import row keeps its own recipient, a put-back onto another date raises `OrderPutBack`, and Orders' commands clamp a clock that stepped back | Accepted |
| [ADR-087](ADR-087-driver-reports-sync-through-an-idempotent-outbox.md) | Driver reports wait in an outbox on the phone and reach the server with a user-scoped idempotency key | Accepted; extended by ADR-121 and ADR-140 |
| [ADR-107](ADR-107-live-operations-alerts-once-per-key-corrects-a-put-back-by-event-and-treats-a-reschedule-as-a-cancellation.md) | Live Operations alerts once per key, corrects a put-back by event, and treats a reschedule as a cancellation | Accepted; extended by ADR-121; amended by ADR-114 |
| [ADR-114](ADR-114-live-operations-reaches-planning-from-its-own-listener-and-names-every-fact-it-judges.md) | Live Operations reaches Planning from its own listener, and names every fact it judges | Accepted; extended by ADR-121 |
| [ADR-109](ADR-109-reporting-computes-exact-on-time-shares-from-current-facts-and-keeps-no-personal-data.md) | Reporting computes exact on-time shares from current facts and keeps no personal data | Accepted; extended by ADR-112 |
| [ADR-088](ADR-088-israel-local-time-for-every-date-and-schedule.md) | Every date, time window, shift and schedule is Israel local time, held as elapsed seconds in a solve | Accepted; amended by ADR-098 |
| [ADR-089](ADR-089-interface-languages.md) | Both apps are available in Hebrew, right to left, and in English only | Accepted; extended by ADR-143 |
| [ADR-143](ADR-143-the-web-app-shell-declares-routes-per-area-opens-in-english-and-renders-no-style-element.md) | The web app's shell declares routes per area, opens in English, and renders no `<style>` element | Accepted; extended by ADR-153; amended by ADR-144 |
| [ADR-144](ADR-144-the-web-apps-features-stay-apart-and-its-lower-folders-never-import-upward.md) | The web app's features stay apart, its lower folders never import upward, and sign-in stands beside the shell | Accepted; extended by ADR-153 |
| [ADR-153](ADR-153-the-web-apps-shared-form-refusal-and-table-pieces.md) | The web app builds every form, refusal and row list from one set of shared pieces, and its schemas name codes, never messages | Accepted; amended by ADR-154 |
| [ADR-154](ADR-154-the-audit-of-the-shared-web-pieces-amends-how-a-call-fails-a-notice-closes-and-a-range-reads.md) | The audit of the web app's shared pieces amends how a call fails, how a saved notice closes and how a time range reads | Accepted |
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
| [ADR-015](ADR-015-ci-one-workflow-one-job-per-component.md) | CI is one workflow with one job per component, and Dependabot keeps dependencies current | Accepted; extended by ADR-021; amended by ADR-046, ADR-135 and ADR-151 |
| [ADR-016](ADR-016-dependency-audit-with-osv-scanner.md) | One OSV-Scanner pass audits every lockfile, and an accepted vulnerability expires | Accepted; extended by ADR-023 and ADR-072; amended by ADR-095 and ADR-134 |
| [ADR-023](ADR-023-accept-minimatch-and-glob-advisories.md) | Accept the minimatch and glob advisories in the frontends' tooling until 2027-01-31 | Accepted; extended by ADR-095 |
| [ADR-095](ADR-095-accept-the-zod-and-deepmerge-advisories-until-2027-01-31.md) | Accept the zod and deepmerge advisories in the phone app's tooling until 2027-01-31 | Accepted |
| [ADR-134](ADR-134-the-audit-resolves-the-cores-pom-from-a-mirror-of-maven-central.md) | The audit resolves the core's pom from Google's mirror of Maven Central | Accepted; extended by ADR-151 |
| [ADR-135](ADR-135-the-seven-ci-jobs-are-required-checks-and-a-pull-request-needs-one-approval.md) | The seven CI jobs are required checks on `dev` and `main`, and a pull request needs one approval | Accepted; amended by ADR-138 and ADR-151 |
| [ADR-138](ADR-138-the-admin-bypass-returns-to-always-because-a-pull-request-bypass-refuses-the-promotion.md) | The Admin bypass returns to `always`, because a pull-request bypass refuses the merge from `dev` to `main` | Accepted |
| [ADR-037](ADR-037-flat-layout-under-src-with-deploy-and-infra.md) | The code moves to a flat layout under `src/`, with deployment in `deploy/` and Terraform in `infra/` | Superseded by ADR-123 |
| [ADR-123](ADR-123-the-code-keeps-its-grouped-layout-and-three-components-take-new-names.md) | The code keeps its grouped layout, and three components take new names | Accepted; extended by ADR-130 |
| [ADR-038](ADR-038-mise-pins-every-toolchain-and-core-moves-to-java-25.md) | mise pins every toolchain, and the core moves to Java 25 | Accepted |
| [ADR-039](ADR-039-one-pnpm-workspace-for-both-apps-and-shared-packages.md) | Both apps and the shared TypeScript packages live in one pnpm workspace | Accepted; extended by ADR-130, ADR-141 and ADR-143; amended by ADR-123 |
| [ADR-141](ADR-141-prettiers-settings-live-in-one-file-in-src-frontend.md) | Prettier's settings live in one file in `src/frontend`, for the web app and every workspace package | Accepted |
| [ADR-042](ADR-042-compose-dev-for-local-runs-testcontainers-for-tests.md) | Local runs start their services from `compose.dev.yaml`; tests keep Testcontainers | Accepted; amended by ADR-123, ADR-139 and ADR-151 |
| [ADR-151](ADR-151-the-dev-stack-runs-the-core-as-a-compose-service-and-the-core-checks-its-database-session.md) | The dev stack runs the core as a Compose service, and the core checks its database session at startup | Accepted; amended by ADR-152 and ADR-156 |
| [ADR-152](ADR-152-the-session-guard-reads-the-real-login-and-accepts-only-untranslated-messages-and-the-stack-check-checks-what-it-claims.md) | The session guard reads the real login and accepts only untranslated messages, and the stack's check checks what it claims | Accepted; extended by ADR-156 |
| [ADR-156](ADR-156-just-dev-waits-for-a-healthy-stack-then-watches-and-the-stack-outlives-the-watch.md) | `just dev` waits for a healthy stack, then watches, and the stack outlives the watch | Accepted |
| [ADR-046](ADR-046-renovate-replaces-dependabot-and-targets-dev.md) | Renovate replaces Dependabot and keeps opening updates against `dev` | Accepted; extended by ADR-152 |
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
| [ADR-068](ADR-068-every-web-handler-is-authorized-by-role-from-the-contract.md) | Every web handler is authorized by role, from the `x-capability` of its contract operation | Accepted; extended by ADR-140; amended by ADR-104 and ADR-120 |
| [ADR-104](ADR-104-the-company-profile-holds-customer-defaults-and-capabilities-follow-the-role-matrix.md) | The company profile holds a new customer's defaults, and capabilities follow the role matrix | Accepted; extended by ADR-121; amended by ADR-116 |
| [ADR-116](ADR-116-only-an-admin-changes-their-own-email.md) | Only an Admin changes their own email | Accepted |
| [ADR-069](ADR-069-sign-in-and-transport-security.md) | Sign-in uses signed access tokens, rotating refresh tokens and Argon2id, over HTTPS through Caddy | Accepted; extended by ADR-121, ADR-140 and ADR-143 |
| [ADR-070](ADR-070-security-and-accessibility-standards.md) | We self-check against OWASP ASVS 5.0 Level 1 and build to WCAG 2.2 AA, with no separate target for IS 5568 | Accepted |
| [ADR-071](ADR-071-api-rate-limits-in-process-with-bucket4j.md) | API rate limits run in the core's process, with Bucket4j | Accepted; extended by ADR-121 |
| [ADR-075](ADR-075-retention-deletes-records-and-anonymizes-people-in-place.md) | Retention deletes positions, proofs and messages, and anonymizes customers and drivers in place | Accepted; extended by ADR-121; amended by ADR-102 and ADR-131 |
| [ADR-110](ADR-110-audit-records-hold-only-ids-codes-enums-numbers-and-instants-record-refusals-and-are-kept-a-year.md) | Audit records hold only IDs, codes, enums, numbers and instants, record refusals, and are kept a year | Accepted; extended by ADR-114, ADR-120 and ADR-121; amended by ADR-149 |
| [ADR-076](ADR-076-admin-and-dispatcher-data-stays-in-memory-on-devices.md) | Admin and Dispatcher data stays in memory on devices, and sign-out wipes it | Accepted |
| [ADR-077](ADR-077-push-builds-and-app-stores-are-accepted-exceptions-to-hosting-in-israel.md) | Push, app builds, the app stores and map-app links are accepted exceptions to hosting in Israel, on conditions | Accepted |

> [!NOTE]
> Numbering restarted at ADR-001 on 2026-10-30. ADR-004 and ADR-005 are older (2026-10-27) and
> kept their numbers.

> [!IMPORTANT]
> On 2026-11-13 and 2026-11-14 every proposed and open item in the requirements and the design
> documents was settled. ADR-037 to ADR-047 were accepted then, and
> ADR-048 to ADR-093 record the rest, including every decision the requirements marked
> `ADR pending`. ADR-094 to ADR-096, on 2026-11-14, settle three points that were found still open
> afterwards. ADR-097 to ADR-110, from 2026-11-14, record decisions taken while the domain layer was
> being built. ADR-111 to ADR-118, on 2026-11-15, record the decisions on the review of that
> layer, and the split of its packages. ADR-119 to ADR-121, on 2026-11-15, record the
> application layer's skeleton and its review. ADR-122, on 2026-11-18, drops the finish skill's agent
> review. ADR-123, on 2026-11-22, drops ADR-037's flat tree and renames three components. ADR-124,
> on 2026-11-22, cuts each `CLAUDE.md` to what a session would otherwise get wrong. ADR-125, on
> 2026-11-22, splits a source folder above 20 files wherever four of its files can move. ADR-126, on
> 2026-11-23, replaces jOOQ with JPA in the core and lets the domain classes carry their mapping.
> ADR-127, on 2026-11-23, records how the 20 aggregates are mapped and where their stored state is
> tested.
> ADR-128, on 2026-11-23, records six persistence rules the mapping settled after ADR-127:
> a session's writes, state no entity maps, durations, gaps in stored lists, instants to the
> microsecond, and batch fetching.
> ADR-129, on 2026-11-24, starts every transaction in the service layer: every service is
> transactional, the adapters and listeners never are, and a use case with outside calls holds its
> transaction in a second class.
> ADR-130, on 2026-11-24, declares every folder the design draws, empty and documented: six
> kinds of inbound adapter, a naming rule for the outbound ones, and three rules that keep adapters
> apart and shared ones off the modules.
> ADR-131, on 2026-11-24, settles that structure's review: rule 11 refuses `web`, rule 12 keeps the
> optimizer client in its adapters, every module gets a `cli` for the steps that span them, and a
> revoking server step reaches the serving core.
> ADR-132, on 2026-11-24, drops the milestone number from every unbuilt body: it says "To Be
> Implemented." and names no milestone.
> ADR-133, on 2026-11-24, lets a `job` adapter be a step that runs once at startup, and declares
> Access's `in/job` for the step that stores the seeded accounts.
> ADR-134, on 2026-11-24, has the audit read the core's parent pom from Google's mirror of Maven
> Central, which Central's rate limit had failed, and records that the core's Java dependencies are
> audited.
> ADR-135, on 2026-11-24, makes the seven CI jobs required checks on `dev` and `main` and has a
> pull request wait for one approval.
> ADR-136, on 2026-11-24, has every connection of the core wait at most 2 seconds for a lock, and
> has the retry #162 builds re-run a call whose wait for a lock failed.
> ADR-137, on 2026-11-24, adds six abbreviations to FR-398's table, among them שכ׳ for שכונה, and
> names the ones left out, with the reason for each.
> ADR-138, on 2026-11-24, returns the ruleset's Admin bypass to `always`, because the pull-request
> bypass refused the merge commit from `dev` to `main`.
> ADR-139, on 2026-11-25, brings GovMap geocoding into the Alpha behind the `Geocoder` port, with the
> WireMock fake by default and the real GovMap for the demo, and moves ADR-085's accuracy test with
> it. An order outside the service area now drops in the Alpha, and the core refuses a GovMap mode
> that doesn't fit its profile. Depot geocoding, suggestions while typing and the cache's retention
> stay later.
> ADR-140, on 2026-11-25, places the app-wide `web` package above the modules: rule 13 limits it to
> their `api` and `events`, and rule 14 keeps the call's holders to `web` and the controllers. It
> settles the generated code, the caller, the error statuses, the retry, the trace ID and where the
> use cases become beans.
> ADR-141, on 2026-11-25, moves the web app's `.prettierrc.json` up to `src/frontend`, where the
> web app and every package of the new pnpm workspace read it; the phone app's identical copy stays
> until #147.
> ADR-142, on 2026-11-25, amends ADR-140 with what #162's build and review found: a read-only commit
> doesn't block a retry, a race after a read-write commit answers 500, a 400 lists at most 100
> items, the handlers run module, contract, kernel, unexpected, a last-resort endpoint answers every
> error as a `Problem`, no handler takes a streaming parameter, and the async queue stays unbounded
> until a load test shows it growing.
> ADR-143, on 2026-11-25, settles #163's web app shell: each area declares its own routes in code
> under one layout route, the web app's folders depend one way, `/` opens tomorrow's plan, the app
> opens in English and turns Hebrew only through its Settings page, remembered in one storage key
> until FR-124, shared packages ship their source, the font is Heebo, no error reaches the screen,
> `permissions` holds what each dependency may do, and the app renders no `<style>` element, so
> ADR-069's policy holds unchanged, as the check in Chrome showed.
> ADR-144, on 2026-11-25, amends ADR-143 after a review of the shell: no feature imports another,
> nothing below `src/` imports the modules that assemble the app or its tests, lint enforces both,
> sign-in stands beside the shell with its session in `lib/`, writing HTML into the page fails lint,
> a generated package generates before lint and test, and a missing subject shows the not-found page
> inside the shell.
> ADR-145, on 2026-11-26, builds ADR-041's two roles: the core's own image runs Flyway as the owner
> through a `migrate` command that starts only the data source and Flyway, with the core's 2-second
> lock limit. The owner gets `CREATE` on the database and on `public`, an init script creates both
> roles, `app` gets exactly what each adapter runs, and a test guards the grants. The tests and
> `just core-run` run Flyway in the core's JVM, as the owner.
> ADR-146, on 2026-11-26, amends ADR-145 after a review of the roles: the jar refuses any argument
> but options and a first `migrate`, the init script turns off every setting that would log a
> statement or its parse tree, `DatabaseRolesIT` reads the server's own log and guards every object
> the owner owns, `public`, columns, partitions, sequences and Flyway's history included, Flyway's
> test-only setting never goes into `application-dev.properties`, and an older database runs the
> script itself.
> ADR-147, on 2026-11-26, extends and amends ADR-140 with what #170's stand-in session settled:
> its condition, exactly `dev` or exactly `test`, sits in the root package, which depends on
> nothing below it, as `@StandInOnly` for every stand-in; a deployed environment is told by
> `DISPATCHFLOW_DEPLOYED` and three reserved provider-credential keys; every refused access token
> answers 401 `ACCESS_TOKEN_REFUSED`, `SESSION_REVOKED` included, and that code's Hebrew and English
> text goes to #173; and the access interceptor builds the call's caller, which the call wrapper only
> binds, so `web` depends on `web.security` and never the reverse.
> ADR-148, on 2026-11-26, amends ADR-147 after a review of the stand-in: it serves only a request
> from its own origin or from no browser, reads the `Host` header itself rather than the server
> name forwarded headers rewrite, and runs on an address pinned to the loopback interface; a call
> with no principal to any handler under `/api/v1` answers 401, with a refused record only for an
> operation; only `web.security` builds a `CallContext`, beside the context's own server step; and
> sign-in deletes the stand-in session but not its guard, which every other stand-in still needs.
> ADR-149, on 2026-11-26, settles who records a refused attempt and how the audit purge counts its
> year, as #180 builds the audit log: the use case writes the refused record once its `Change` has
> rolled back, naming what the attempt named, so a use case whose transaction can refuse is two
> classes, only a transactional class records a success, and none but two named `Change`s a
> refusal; a record is past its retention once it was made before the same Israel local time a
> year earlier, the earlier instant where the clocks skipped or repeated that time, one cutoff
> that the code and the database's owner-run purge function share, and `app` may run the purge
> alone. It replaces the lazy lookup of the audit log that ADR-142 and ADR-147 left until #180.
> ADR-150, on 2026-11-27, amends ADR-149 after a review of #180: one Audit helper, `Refusals`,
> runs a use case's own checks and `Change` and records the refusal that leaves them, a domain
> refusal or any application refusal the kernel's `ApplicationRefusal` marks, a not-found among
> them; a use case that answers a conflict itself records it; the contract's handler records a
> changing call it refuses before the handler starts; rule 10 also bans the helper in a
> transaction, and lists a `Change` only when no refusal leaves it once recorded, so its commit
> carries the record; and a refusal that is only another exception's cause answers 500.
>
> ADR-151, on 2026-11-27, settles #146 and the parts of #250 that have a home today: one Compose
> file runs PostgreSQL, the migrate step, the core, the optimizer and the web app's dev server on
> a laptop, each service with only its own passwords and every port on `127.0.0.1`; the core runs
> as a Compose service, and Spring Boot's Docker Compose support is not adopted, so `just
> core-run` stays for the IDE; Compose Watch carries edits into the web and optimizer
> containers; the core refuses to start as a superuser, a role with an attribute that passes the
> grants, a member of another role, an owner, or on a server whose messages aren't English; the
> core's JVM runs in Israel's zone; and `just infra-check` checks the file and the running stack,
> in `just check` and CI's `infra` job, the eighth required check.
>
> ADR-152, on 2026-11-27, amends ADR-151 and extends ADR-046 after a review of #146: the guard
> reads the login from `pg_stat_activity`, so a superuser's `SET SESSION AUTHORIZATION` no
> longer passes, and accepts only `lc_messages` `C` or `POSIX`, since a `LANGUAGE` variable
> translates an English locale, and older glibc `C.UTF-8` too; the tests' server runs the
> stack's settings; `DatabaseSessionGuard` checks identity and `DatabaseRolesIT` the grants;
> `just infra-check` reads env files from the file as written, maps every secret to its
> holders, checks hardening, root processes, a rewritten `Host` and the PostgreSQL and Node
> tags, and tests its own rules; the Maven wrapper pins its download's SHA-256; and the local
> run sets its zone in its own `main`.
>
> ADR-153, on 2026-11-27, settles #173's shared pieces, amends ADR-043 until #148 and extends
> ADR-057, ADR-143 and ADR-144: the formatters, the schemas and the refusal texts are packages
> both apps share; a schema names the server's code, never a message, and an empty optional value
> is left out; the web app types the `Problem` by hand, pinned to the contract, until #148; a
> versioned save sends the `ETag` as it came, or `If-None-Match: *` for none, and never resends;
> a refusal shows on its field and the rest in a summary that takes focus; the time of day is
> Mantine's 24-hour picker, and Day.js is only its peer; and @hookform/resolvers and user-event
> come in beside the libraries the tech stack named.
>
> ADR-154, on 2026-11-27, amends ADR-153 after an audit of the merged pieces: Zod never compiles
> code, which ADR-069's policy refuses, and the web app takes it from `@dispatchflow/schemas`; only
> fetch and the answer's body decide that a call failed on the way; reads and writes read their
> answers one way, a failed read never says a change may be stored, and only a read that failed on
> the way is tried again; the form records its resets, field by field, for its time fields; a saved
> notice that offers an action stays until closed, returns focus and names no person; a time range
> reads left to right in both languages; an optional number has its own block; focus on a refusal
> follows reading order, a window's included; a table's default cell writes text only; a screen
> sends the newest version it knows; and the web tests run in New York's zone.
>
> ADR-156, on 2026-11-28, amends ADR-151 and extends ADR-152 after an audit of #172's recipes:
> `just dev` runs `up --build --wait`, prints where each service listens, then watches with
> `watch --no-up`, so a start that fails fails the command; the stack outlives the watch, so
> Ctrl+C, which just reports as a failed recipe, stops only the watch, and `just dev-down` comes
> after it; every checkout runs the one project and database, which `just db-reset`'s prompt
> says; and a service under the `data` profile sets a numeric non-root user and drops every
> capability outside the root group, and the file creates or mounts no volume but the database's,
> named or anonymous; and no service shares another container's namespace, takes a security
> option but no-new-privileges, or reaches the database's files but postgres itself.
>
> ADR-158, on 2026-11-28, amends ADR-084 for the Alpha after #152: `just data` cuts the tiles
> from the last build of a basemap version that Protomaps keeps for good, and checks the cut and
> the fonts' archive against SHA-256 pins; the monthly rebuild returns with the monthly refresh of
> the road data, which keeps its own cut in Israel; and the Alpha accepts tiles older than
> NFR-129's 60 days, until the pin passes 120.
> The tech stack's [§22](../architecture/tech_stack.md#22-open-decisions) lists what is still open.
