# ADR-140: The app-wide `web` package sits above the modules, and what they need from it sits beside the kernel

- **Status:** Accepted; extended by ADR-147, ADR-148 and ADR-155; amended by ADR-142, ADR-147, ADR-148 and ADR-150
- **Date:** 2026-11-25

## Context

[ADR-040] leaves one placement open: the app-wide `web` package, which holds what belongs to no
module, such as the security filter chain, the Problem Details handler and the code generated from
the contract. Its own ADR decides it before the first web adapter merges, "once the generator's
package layout is visible". ADR-040 leans to a `web` module that depends on no module, with stable
error codes so its handler imports none. Later ADRs leave this ADR more questions
([issue #143][ISSUE-143]):

- **[ADR-043]** doesn't say whether generated code is committed or built each time.
- **[ADR-068]** has one interceptor read a generated `@RequiresCapability` and ask Access's
  `RoleSet.can`, and leaves where both live to this ADR.
- **[ADR-073]** leaves three points open. Two go to the first handler: whether a property the
  contract doesn't declare is refused, and whether the role check or validation runs first. The
  third, what generated validation leaves out, stays with Schemathesis's runs from January.
- **[ADR-120]** exempts `web` from rule 2 until this ADR, and its security filter will use Access's
  `SessionValidity`. Whoever answers a 401 writes `AuditLog.refused` for a changing call.
- **[ADR-121]** asks for the record of how the stand-in session works (decision 5), the retry and
  then 503 (decision 4), the boot refusal of the dev profile's longer token (decision 6), the HTTP
  status of each domain error, the 401 challenge, and the status of a stale version sent in a body
  (P31).
- **[ADR-130] decision 6 and [ADR-131] decision 8** add the placements: the stream's endpoint, its
  registry and the `SessionsRevoked` listener; the end-to-end control endpoints; the generated code;
  the component that builds the `Actor`; the capability check; and where the use cases become beans.

The forces:

- **Rule 2 refuses every module a dependency on `web`** (`ApplicationRules`), and Access will both
  serve operations and be used by `web`'s security. Opening rule 2 would let Access and `web` depend
  on each other, a cycle that Spring Modulith's `verify()` fails ([#145][ISSUE-145]).
- **The generated interfaces fix each handler's parameters,** so a controller can't simply declare an
  `Actor` argument, yet every web-called use case takes the `Actor` first, and every call before
  sign-in takes a `CallContext` ([ADR-120], [ADR-130]).
- **`Capability` and `RoleSet` are in `access.domain`,** which nothing outside Access may use, and
  Access's `api` has no capability check.
- **Nothing reaches a body that still throws** ([ADR-119] decision 4), and the application layer
  carries no Spring annotation but `@Transactional` ([TS §2.1][TS-2-1]).
- **The phone's outbox acts on each status** ([ADR-087], extended by [ADR-121] decision 16): 2xx,
  409, 422, a 404 with `NOT_FOUND`, 429, 5xx and network errors. A route's reports go in order
  ([FR-247]), so a row with no rule blocks every later report of its route.
- **The pinned frameworks,** read from their jars: Spring Security 7.1.2's bearer-token filter
  answers a refused token itself, through its entry point or failure handler, before a handler is
  chosen; Spring Framework 7.0.10 parses a multipart body before choosing the handler unless
  multipart resolution is lazy; and spring-tx 7.0.10 refuses a transaction synchronization when no
  transaction is active, while its `TransactionExecutionListener` reports each commit.

**The generator, as tried on 24 and 25 November 2026.** openapi-generator 7.27.0 (released
2 November 2026, the latest; the jar's checksum matched Maven Central's) ran its `spring` generator
on a copy of `client-api` with one made-up operation for each case: a body with a version,
`If-Match`, a `text/event-stream` response, tags, and `x-capability` with
`x-operation-extra-annotation`. The options were `interfaceOnly`, `useSpringBoot4`, `useJackson3`,
`useTags`, `skipDefaultInterface`, `useBeanValidation`, `openApiNullable=false`, `useJspecify`,
`useRecords`, `annotationLibrary=none` and `documentationProvider=none`. It showed:

- each tag becomes one `<Tag>Api` interface, `planning` giving `PlanningApi` and
  `planning-proposals` giving `PlanningProposalsApi`, and each method is named by its operationId;
- `skipDefaultInterface` leaves every method abstract, so one class implements all of a tag's
  operations, while an interface that no class implements still compiles;
- `x-operation-extra-annotation` puts the fully qualified annotation on the interface's method;
- `useJspecify` writes `@NullMarked` `package-info` files for the `api` and `model` packages, and
  `useRecords` writes the models as records carrying Bean Validation;
- a `text/event-stream` response becomes `ResponseEntity<String>`, which can't stream. The `x-sse`
  extension returns Reactor's `Flux` and `Mono`, which the servlet-based core doesn't use, and
  `x-internal: true` leaves the operation out;
- `x-spring-provide-args` adds a parameter of any type to a method, and imports that type into the
  generated interface;
- `x-spring-security-pre-authorize` writes `@PreAuthorize`;
- it also writes an unused `ApiUtil` class, and an operation whose only declared body is the error
  body declares only `application/problem+json`;
- Spring Boot 4.1 with Jackson 3 accepts a property the contract doesn't declare, and the generated
  records carry nothing that refuses one.

## Decision

Decided on 25 November 2026. The numbers follow the issue's points a to j.

1. **`web` sits above the modules, and nothing depends on it** (point a).
   - `com.dispatchflow.web` stays a top-level package that is no module (`NOT_MODULES`). Rule 2 keeps
     refusing every module a dependency on it, and rule 11 every shared adapter.
   - A new **rule 13** limits `web`: of the modules, it uses only their `application.api` and
     `events`. It may use the kernel, its adapters included, and any framework.
   - What the modules need sits beside the kernel, below `web`, or in a module's own `api`. Rule 11
     already keeps `shared.adapter` from using any module or `web`:
     - **`shared.adapter.in.web`:** the code generated from `client-api`, in `.api` and `.model`;
       `@RequiresCapability`, which the generated interfaces carry; and the factory that builds a
       `Problem` with its `code`, `params` and trace ID, which every module's handler and `web`'s use.
       It takes the trace ID from the trace filter's MDC entry, never from the call's holders.
     - **`shared.adapter.out.sse`:** the registry of open event streams. It holds kernel types only,
       such as `AccountId` and `SessionId`. `web.stream` registers each stream with it, and
       Planning's and Live Operations' `sse` adapters pick each event's recipients through Access's
       `api` before they send through it.
     - **`access.application.api` and `audit.application.api`:** the call's caller and context,
       through `CurrentActor` and `CurrentCall` (decision 4), and the capability check (decision 3).
   - `web`'s packages:

     | Package | Holds | Built by |
     |---|---|---|
     | `web` | The trace-ID filter, the call wrapper that binds the call's holders and retries, and the handlers of the kernel's errors and of requests that break the contract; `UnexpectedErrorHandler` | [#162][ISSUE-162] |
     | `web.security` | The stand-in session and the interceptor's 401 now; Spring Security's chain and the token filter with sign-in; the interceptor's 403 | [#170][ISSUE-170], [#199][ISSUE-199], [#171][ISSUE-171] |
     | `web.stream` | The event-stream endpoint, written by hand; its heartbeat; the `SessionsRevoked` listener, which closes the account's streams opened under an older generation | [#193][ISSUE-193], [#199][ISSUE-199] |
     | `web.e2e` | The end-to-end tests' control endpoints, under the `e2e` profile | The issue that builds the first one, which reopens decision 4 first |

   - **To Spring Modulith,** `web` is a module that depends on the others' exposed interfaces and on
     which none depends, so `verify()` finds no cycle. The kernel exposes to it and to the modules
     every package they touch: its root, `application`, `adapter.in.web` with `.api` and `.model`,
     `adapter.out.sse`, `adapter.out.persistence`, `adapter.out.clock`, and `adapter.out.optimizer`
     with its generated subpackages. [#145][ISSUE-145] checks how Spring Modulith 2.1 treats a named
     interface's subpackages. If one doesn't cover them, the generated packages get hand-written
     `package-info` files carrying `@NullMarked` and `@NamedInterface`, with the generator's own
     skipped through `.openapi-generator-ignore`, or the kernel becomes an open module, which amends
     [ADR-120] decision 2. Modulith can't limit an interface to the modules' adapters; ArchUnit does
     (decision 2).
2. **The generated code is built at every build and never committed** (point b).
   - The Maven plugin is pinned at 7.27.0, with the options above, generating into
     `shared.adapter.in.web.api` and `.model`. With `annotationLibrary` and `documentationProvider`
     both `none`, it imports nothing the core doesn't already depend on.
   - **Every operation carries exactly one tag, its module's name and its resource,** such as
     `planning-proposals`, giving `PlanningProposalsApi`. Every tag starts with its module's package
     name (`liveops-routes`, not `live-operations-routes`), so each controller in
     `<module>.adapter.in.web` implements one resource's interface of its own module.
   - The event stream's operation is marked `x-internal: true`, and `web.stream` serves it. It is
     still public and versioned, carries `x-capability`, and is exempt from the tag rule.
   - **[#161][ISSUE-161] adds the twin of rule 12:** the generated types stay in the `in/web`
     adapters and `web`; each controller implements only its own module's interfaces; and every
     generated interface has exactly one implementation, in its tag's module's `adapter.in.web`. So
     an operation with no controller fails the build. Because the contract change and its provider go
     in one pull request (`contracts/CLAUDE.md`), a contract change can't land before its handler.
   - The core's image is built from the repository's root, so the build sees `contracts/`.
   - #161 also settles the unused `ApiUtil` and the error-only media type, and confirms with
     [#148][ISSUE-148] that `oasdiff` and openapi-typescript keep the `x-internal` operation.
     [#161][ISSUE-161] and [#193][ISSUE-193] settle whether the stream's event payloads are generated
     models; if they are, the twin rule admits Planning's and Live Operations' `sse` adapters.
   - The other generated code that [ADR-043] leaves open stays open: the core's optimizer client,
     the optimizer's models and the apps' TypeScript client.
3. **`@RequiresCapability` sits in `shared.adapter.in.web`, and its interceptor in `web.security`**
   (point c).
   - The annotation holds the capability's name as a string, because the kernel can't name
     `access.domain.Capability`. The interceptor asks a new check in Access's `api`, implemented by
     its `…Service`, which parses the name and answers from `RoleSet` and the matrix ([ADR-104]).
     This extends [ADR-068]: the check is still `RoleSet`'s, asked through Access's `api`.
   - **The interceptor is built in two steps.** [#170][ISSUE-170] builds it with the stand-in, and it
     answers 401 to a call with no principal on an operation that isn't open. [#171][ISSUE-171]
     (stretch) adds the role check, which answers 403. Until then, the stand-in Dispatcher is never
     refused a role.
   - **No access token is accepted before the role check exists,** so [#199][ISSUE-199] depends on
     [#171][ISSUE-171].
   - **It refuses by default.** A handler under `/api/v1` that isn't an annotated handler method is
     refused, except an operation the contract marks open ([#199][ISSUE-199] names the marker) and a
     CORS preflight. A handler outside `/api/v1` is refused unless a named list holds it: health,
     and the provider callbacks, which check their own signature ([NFR-192]). The `e2e` profile's
     control endpoints are refused unless that profile is active (decision 4).
   - **A test compares the handlers with the contract:** each handler's effective annotation and
     open marker with its operation's `x-capability` and marker in `contracts/client-api`, the
     hand-written stream endpoint included, and each capability name with Access's capabilities.
4. **Every call has one caller, read from two holders** (point d).
   - **One principal per request.** `web.security` sets it: the account, the session and the roles,
     from the stand-in session now and from the access token after sign-in, or none. The interceptor
     checks that principal, and the call wrapper builds the call's holders from the same principal and
     the chosen handler.
   - **`audit.application.api.CurrentCall`** is a final class over a `ScopedValue<CallContext>`,
     bound for every call to a handler under `/api/v1`, signed in or not. Its `operation` is the
     handler's operationId.
   - **`access.application.api.CurrentActor`** is a final class over a `ScopedValue<Actor>`, bound
     on top when a caller is signed in. `ScopedValue` is final in JDK 25, and a bound value can't be
     changed during the call and ends with it. Each holder's `get()` fails when nothing is bound.
   - **Who may use them.** A controller passes them to its use case, and a use case keeps its `Actor`
     or `CallContext` parameter. Rule 14 lets only `web` and the modules' `adapter.in.web` use the
     two holders. An exception handler never reads them, because Spring MVC runs it after the call's
     scope has ended. [#170][ISSUE-170] adds the rules that only `web.security` builds an `Actor` and
     only `web`'s call wrapper calls the holders' bind methods, a check that every holder rule 14
     names exists, and `CurrentActor` on Reporting's narrowed `api` list ([ADR-120] P5).
   - **A provider callback,** such as the SMS reply ([ADR-121] decision 4), lives outside `/api/v1`
     and isn't in `client-api`. It goes through the call wrapper, so it is retried, and builds its
     context with `CallContext.serverStep`, taking the trace ID from the trace filter's MDC entry.
   - **The stand-in session** lives in `web.security` ([ADR-121] decision 5). It acts as an entry of
     the shared list of seeded accounts, the Dispatcher unless a property names another, with a fixed
     `SessionId` ([#170][ISSUE-170]). It fails closed:
     - it exists only when `getActiveProfiles()` is exactly `dev` or exactly `test`, so a profile that
       is active only as the default doesn't count;
     - the core refuses to boot with its configuration under any other set of profiles, or when a
       deployed environment is detected: the `DISPATCHFLOW_DEPLOYED` marker that every deployed
       environment sets, or any of the SMS provider's, the email sender's or the push service's
       credentials;
     - it serves only a request that presents no credential, neither an `Authorization` header nor
       the refresh cookie, and a presented token that fails answers 401 under every profile;
     - every port the development stack publishes, the web app's included, binds `127.0.0.1`, and the
       `dev` profile sets `server.address=127.0.0.1`, which the Compose core overrides inside its
       container ([#146][ISSUE-146]).

     Building sign-in deletes it.
   - **The longer dev token** ([ADR-121] decision 6) arrives with sign-in, and [#199][ISSUE-199]
     builds its refusal: the core refuses to boot with any lifetime but 15 minutes unless `dev` is
     the only active profile.
   - **The `e2e` profile is left open.** The stand-in refuses to boot beside it, and the profile
     refuses to boot under the same deployed-environment check. The Alpha's end-to-end run is the
     demo script on the `just dev` stack ([#259][ISSUE-259]), so no Alpha test needs both. The issue
     that builds the first control endpoint reopens this decision first.
5. **Each error's status** (point e). The rows that [ADR-120] decision 7 and [ADR-121] P29 already
   settled are copied with their source. `CompanyNotSetUp` is left out, because [#182][ISSUE-182]
   deletes it.

   | Error | Status | Source |
   |---|---|---|
   | `NotFound` | 404, `NOT_FOUND`, with the kind and ID in `params` | [ADR-120] |
   | Any `StaleVersion` on a call that sent `If-Match`, or `If-None-Match: *` on a target that exists | 412, `STALE_VERSION`, never retried | [ADR-120], [ADR-121] decision 4 and P31, [ADR-136] decision 3 |
   | A `StaleVersion` that compared a version sent in the body | 409, `STALE_VERSION`, never retried | This ADR (decision 7) |
   | Any other `StaleVersion` on a call that sent no precondition header; a lock wait that failed, on any call | Retried, then 503 with `Retry-After` | [ADR-121] decision 4, [ADR-136] decision 4; this ADR's decision 6 |
   | `SignInRefused` | 401 | [ADR-120] |
   | `IdempotencyKeyReused`, `ProofIdTaken`, `RouteNotStarted` | 409 | [ADR-120], [ADR-121] P29 |
   | `ProofImageTooLarge`, `SolverNotOffered`, `ProofFormatRefused`, `ProofImageUnreadable` | 422 | [ADR-120], [ADR-121] P29 |
   | `ProofUploadsCapped` | 429, with `Retry-After` | [ADR-120] |
   | `GrantLimitExceeded` | 403, `GRANT_LIMIT_EXCEEDED` | [ADR-120] |
   | A `RuleViolation` with `STEP_UP_REQUIRED` | 403 | [ADR-068], [ADR-120] P12 |
   | A `RuleViolation` with `NOT_YOUR_ROUTE` or `STOP_NOT_ON_YOUR_ROUTE` | 403 | This ADR |
   | A `RuleViolation` with `SESSION_REVOKED`, and a missing or refused access token | 401, with decision 7's one code and empty `params`; the audit record keeps the specific code | This ADR; [#170][ISSUE-170] names the code |
   | A `RuleViolation` with `REFRESH_REFUSED`, and any refused refresh, a malformed cookie or a failed CSRF check included | 401, `REFRESH_REFUSED`, every refusal alike ([NFR-92]) | This ADR |
   | Any other `RuleViolation` | 422, with its `code` and `params` | This ADR |
   | `RuleViolations` | 422, listing every error | This ADR |
   | A request that breaks the contract: a failed validation, a missing required header such as `If-Match`, an unreadable body, an unknown or duplicate property | 400, each failing item with a pointer into the body or the parameter's name and place, and a code, never the value | This ADR (decision 9), [ADR-073]'s open points |
   | A body over its size limit | 413, with a code of its own | This ADR |
   | Spring MVC's other own errors: no route, 405, 406, 415 | Their own status and a code of their own, never `NOT_FOUND` | This ADR |
   | `StoredStateInvalid` | 500, saying nothing of the cause | This ADR |
   | Anything else | 500, from `UnexpectedErrorHandler` | Built; [#162][ISSUE-162] gives it a `code` and the `traceId` |

   - **422 is the default for a refused rule,** and a code overrides it only where a client must act
     differently: sign in again (401), or give the password or stop asking (403). 400 then means only
     that the client broke the contract, a bug, and 422 that the rules said no. A missing required
     precondition header answers 400, as any missing required parameter does, not RFC 6585's 428.
   - **Every answer is the `Problem` of [ADR-043],** with `type` `about:blank`, its `code`, `params`
     and `traceId`, built by the factory in `shared.adapter.in.web`. `code` is a string, never an
     enum, and the contract lists the codes in its description, so a new code isn't a breaking
     change; both apps show their generic text with the `traceId` for a code they don't know.
     [#162][ISSUE-162] names, in the contract's `Problem` schema, the list member that a 400 and a
     multi-error 422 share and its entries, and the codes of the 400, the 413, Spring MVC's own
     errors, the 503 and the 500. [#171][ISSUE-171] names the code of a role refusal. A 400 entry and
     its log line never carry the refused value, a pointer is cut to a named length, and the handler
     never logs the exception's message.
   - **Where each is mapped.** A handler in `web` maps the kernel's errors (`RuleViolation`,
     `RuleViolations`, `NotFound`, `StaleVersion`, `StoredStateInvalid`) and the requests that break
     the contract, choosing a status by `code`, so `web` imports no module's error types. A test from
     [#162][ISSUE-162] checks that every code in that table is a code some module declares. Each module
     maps its own application exceptions in its `adapter.in.web`, those in its `api` included, such
     as `GrantLimitExceeded`. `UnexpectedErrorHandler` runs last, as it does today.
   - **The phone's outbox gains these rules,** extending [ADR-087], and the web app follows the same
     401 rule:
     - a 403 with a contract code marks the row refused and shows it to the driver, as 409 and 422 do;
     - a 401 makes the sender refresh once and resend; when the refresh answers `REFRESH_REFUSED`, it
       stops, keeps the rows pending until the same user signs in again ([FR-331]), and wipes the
       route data ([Q-92] #4);
     - a 400 or a 413 is retried with the 5xx back-off and reported to GlitchTip with the trace ID,
       the operation and the problem's codes and pointers, never the row. After 24 hours the row is
       marked refused and shown, so the route's later reports go on ([FR-247], [FR-248]);
     - any other status, Spring MVC's own errors included, is treated as a 5xx.

     The size limits of the proof and report operations sit above the domain's own limits, so an
     over-size proof answers `ProofImageTooLarge` 422.
6. **One call wrapper in `web` re-runs a call that lost a race** (point f, [#162][ISSUE-162]).
   - It re-invokes the resolved handler with the arguments it already read, because a request's body
     can be read only once, so a retried handler takes no streaming parameter, such as an
     `InputStream`. It sits outside the service's transactions, so each try runs in new ones
     ([ADR-126], [ADR-129]).
   - **What it retries.** `StaleVersion` records whether it compared the version the request sent:
     `requireCurrent` sets that. A `StaleVersion` on a call that sent `If-Match` or `If-None-Match` is
     never retried, and neither is one that compared a body's version. Any other `StaleVersion`, from
     a save, is retried on a call that sent no precondition header; on a call that sent its version
     in the body, the retry then fails `requireCurrent` and answers 409, because `web` can't see a
     body's version. [ADR-136]'s three lock-wait types are retried on every call, matched on the
     thrown type alone.
   - **Never after a commit.** A try is retried only while none of its transactions has committed: a
     `TransactionExecutionListener` on the transaction manager marks the try committed in
     `afterCommit`, and the wrapper reads that mark and rethrows instead. A use case's steps after its
     commit, such as a push, catch and log their own failures.
   - **One audit record.** A use case never audits a `StaleVersion` or one of [ADR-136]'s lock-wait
     types. The wrapper writes the one refused record of each it answers, 412, 409 or 503, with the
     call's operation, after it has chosen the answer.
   - It runs the call up to three more times, then answers 503 with `Retry-After: 2`, a named
     constant equal to the lock timeout of [ADR-136].
   - The same wrapper binds `CurrentCall` and `CurrentActor` (decision 4). The hook is a custom
     invocable handler method registered through `WebMvcRegistrations`, which #162 confirms in the
     pinned Spring Framework 7. If it doesn't hold, an `@Around` advice in `web` does the same, at
     the cost of AspectJ, a new dependency.
7. **Every 401 carries a bare challenge, and a stale body version answers 409** (point g).
   - Every 401 carries exactly `WWW-Authenticate: Bearer`, which [RFC 9110][RFC-9110] requires of a
     401 and [RFC 6750][RFC-6750] defines for bearer tokens. It carries no `error`,
     `error_description` or `error_uri`, so a missing, expired, malformed and revoked token look the
     same; this departs on purpose from RFC 6750's advice to name `invalid_token`. Sign-in and
     refresh answer the same header, also on purpose.
   - A missing, expired, malformed or revoked access token, and a `SESSION_REVOKED` from a use case,
     get one code with empty `params`, as [NFR-92] already does for refresh tokens. The PR that first
     answers it ([#170][ISSUE-170]) names the code in the contract.
   - An open operation, such as sign-in or refresh, ignores any `Authorization` header. The refresh
     token is a plain string in the contract, with no pattern, length or format, so validation can't
     answer a malformed one differently from an unknown one.
   - A stale version sent in a request body, as accepting a proposal sends the plan's ([ADR-121]
     P31), answers 409 `STALE_VERSION`. [RFC 9110][RFC-9110] ties 412 to precondition headers such as
     `If-Match`, and gives a versioning conflict as its example of 409. It is never retried, because
     the client did send a version. Both apps act on the `code`, so the web app reloads the plan
     either way.
8. **A request's trace ID comes from its `traceparent`, or is made fresh** (point h,
   [#162][ISSUE-162]).
   - A filter in `web`, ordered ahead of Spring Security's chain, takes the trace ID from a valid
     incoming `traceparent`, as [W3C Trace Context][W3C-TC] defines one: lower-case hex, a trace-id
     and a parent-id that aren't all zero, and a version other than `ff`, with a version above `00`
     read in that version's layout. Only the trace-id is kept. Otherwise it makes a random 128-bit ID
     that is never all zero, which `audit.domain.TraceId` requires. The raw header is never logged.
   - The same ID goes into the logs (the MDC key `traceId`), `Problem.traceId`, the call's
     `CallContext` and the optimizer call's `traceparent`, which carries the core's own new parent-id.
     [TS §16][TS-16] requires this from the web app to the core to the optimizer ([NFR-20],
     [NFR-74], [NFR-93]).
   - The MDC also carries the account and session IDs once the caller is known. The asynchronous
     listeners keep both: the executor copies the MDC to the listener's thread and restores it in a
     `finally`, and the filter clears it (decision 10).
   - A trace ID correlates requests and proves nothing about who acted: a client can label its own
     requests with any ID, and the account and session in the record and the MDC say who. The filter
     is deleted when the OpenTelemetry agent arrives, which does the same.
9. **A property the contract doesn't declare is refused, and the caller is checked before the body**
   (point i, [ADR-073]'s open points).
   - A request body with a property its schema doesn't declare answers 400. The server's JSON
     converter refuses it at any depth, and only the converter of the API the core serves does. An
     unreadable body, a duplicate key or an unknown property is reported alone, as the first one
     found; a failed validation lists every field. The proof and report operations' size limits are
     set as decision 5 says, and every other body has a named size limit, which answers 413.
   - Every object schema reachable from a request body sets `additionalProperties: false`, or
     `unevaluatedProperties: false` under `allOf`, and isn't reused in a response; response schemas
     stay open. The keyword documents the converter's rule, and Schemathesis checks the two agree.
   - The rest of the core keeps tolerating unknown properties, because an optional field added to a
     response isn't a breaking change (`contracts/CLAUDE.md`), and the optimizer client must keep
     reading responses that gain one.
   - **Request properties follow the apps' versions** ([ADR-081], [ADR-082]). An app starts sending a
     new request property only once the core release that declares it won't be rolled back past. On
     the phone's writes, which unsupported versions still send while their queues drain, a request
     property stays declared for the life of v1. Elsewhere a retired request property is still
     accepted and ignored, never removed in v1.
   - **The order of the checks:**
     1. the trace filter;
     2. ADR-071's rate limiter, so a flood of refused calls is bounded before any of them writes an
        audit record;
     3. `web.security`'s token filter, which replaces Spring Security's bearer-token filter
        ([#199][ISSUE-199]): it decodes a presented token through the `JwtDecoder`, sets the
        principal, or marks the request "token refused", and always continues. Spring Security's own
        authorization permits every request, its anonymous authentication counts as no principal,
        and none of its entry points or handlers answers;
     4. the handler is chosen, and multipart bodies are resolved lazily, after it;
     5. the interceptor answers 401, writing `AuditLog.refused` for a changing call, which can name
        the operation now ([ADR-120]), then 403;
     6. the arguments are read and validated, answering 400;
     7. the wrapper binds the holders and runs the handler, retrying it.

     So a refused caller learns nothing about the body's rules, and an operation is open only where
     the contract marks it. The CSRF check of refresh and sign-out runs in their own path and answers
     401 `REFRESH_REFUSED` ([ADR-069]). How 401s are counted against the limiter stays open for M6,
     as [ADR-120] left it.
10. **The use cases become beans in their module's configuration** (point j).
    - Each module gets one package-private `<Module>Configuration` at its root package, such as
      `com.dispatchflow.planning.PlanningConfiguration`, with `@Import({...})`. It is
      [TS §2.1][TS-2-1]'s *Configuration* layer, which may depend on everything. It imports only its
      own module's `application.service` classes, and the module's root holds nothing else but its
      `package-info`; [#162][ISSUE-162] adds the rule with the first one.
    - A use case and its `…Change` class join that list in the pull request that builds its body,
      so a throwing body is never wired ([ADR-119] decision 4). `@Import` lets Spring call each
      constructor, so none is written twice.
    - A port with no adapter yet gets a `<Port>StandIn` `@Component` in its declared adapter folder,
      where the real adapter replaces it, as the issues that need one placed it on 24 November 2026
      ([#184][ISSUE-184], [#196][ISSUE-196], [#197][ISSUE-197]). A stand-in exists only under the
      stand-in session's own condition, exactly `dev` or exactly `test`, so a deployment that still
      lacks a real adapter fails at startup instead of dropping its messages.
    - `com.dispatchflow.SchedulingConfiguration` sits beside `DispatchFlowApplication` and lands with
      [#162][ISSUE-162]. A nested configuration turns on `@EnableScheduling` behind a property that
      the `test` profile sets off, and the class turns on `@EnableAsync` always, with a named
      concurrency limit on the executor and the task decorator of decision 8. Spring Boot turns on
      neither by itself, so without `@EnableAsync` the `@Async` listeners of [ADR-129] would run
      inside the caller's request with no error.

## Consequences

- **Easy:**
  - Every module's controller reaches what it needs, the generated interfaces, the annotation, the
    `Problem` factory, the registry and the call's holders, without depending on `web`, so no cycle
    can form however many operations Access serves.
  - The issues that wait on this ADR build against one answer: [#161][ISSUE-161] the generator,
    [#162][ISSUE-162] the statuses, the trace ID, the retry and the wiring, [#170][ISSUE-170] the
    holders, the stand-in and the 401, [#171][ISSUE-171] the role check, [#193][ISSUE-193] the
    stream and [#199][ISSUE-199] the token filter.
  - A contract change changes the generated code in the same build, an operation with no controller
    fails the build, and a handler with no capability is refused.
  - The security chain sits in one package, in the order decision 9 fixes, and each 401 is audited
    with its operation.
- **Hard:**
  - The kernel gains two adapters, 54 adapters become 56, and rules 13 and 14 are two more rules to
    keep.
  - The core's image build needs the repository's root as its context ([#161][ISSUE-161]).
  - The holders are values read from the call's scope, so a test of a controller binds them, as the
    wrapper does.
  - Spring Security's bearer-token filter is replaced by one of our own, which #199 builds and tests.
  - Every request schema needs `additionalProperties: false`, the apps' releases follow the core's
    for a new request property, and the phone's request properties are never removed in v1.
  - The default status table grows by a row whenever a new code must make a client act differently,
    and a status once released is part of the contract.
- **Cost:** no new dependency. The generator plugin is [ADR-043]'s.
- **Risk:**
  - The invocable-handler hook is a Spring MVC extension point. #162 confirms it in the pinned
    version, and the `@Around` advice is the fallback.
  - The retries run at once, and each may wait 2 seconds for a lock, so a burst behind one long
    transaction holds the pool's connections longer.
  - The `e2e` profile can't run beside the stand-in, so the first end-to-end test that needs a
    control endpoint reopens decision 4.

## Alternatives considered

- **Modules depend on `web`, and `web` on nothing** ([ADR-040]'s leaning). Rejected: the `Actor`
  builder, the filter chain and the `SessionsRevoked` listener use Access's `api`, so they would all
  have to move into Access, and a module's controller would depend on an app-wide package.
- **Open rule 2 so modules may reach `web`.** Rejected: Access would depend on `web`, which depends
  on Access, a cycle.
- **A top-level package of its own for the generated code.** Rejected: [ADR-130] and [ADR-131] keep
  the tree to the modules, the kernel and `web`, and `shared.adapter.out.optimizer` already holds
  generated code beside the kernel.
- **Commit the generated code, with a check that it matches the contract.** Rejected: two lanes
  adding operations would conflict in generated files every time.
- **One tag per module.** Rejected: `skipDefaultInterface` makes one class implement all of a tag's
  operations, so each module would have one controller, of about 50 handlers for Planning, and two
  lanes would meet in it.
- **Let the generator add an `Actor` parameter** (`x-spring-provide-args`). Rejected: the generated
  interfaces in the kernel would import Access's `Actor`, so the kernel would depend on a module
  that depends on it.
- **`Actor.current()` on the record.** Rejected: the record is what every use case receives, so a
  static getter on it invites a service to read the call's actor instead of its parameter.
- **A request attribute or Spring Security's principal in each controller.** Rejected: every
  controller would need a string key or a cast, and the operationId isn't known when a filter sets it.
- **The interceptor in Access's `adapter.in.web`.** Rejected: an Access adapter would act on every
  module's calls, and the security chain would split between two packages.
- **Generated `@PreAuthorize`.** Rejected: it needs Spring Security's method security, puts
  expressions in the public contract, and runs after the body is validated.
- **Spring Security's own bearer-token filter, answering the 401.** Rejected: it answers before the
  handler is chosen, so the refusal's audit record couldn't name the operation, and the open
  operations would be a list of paths kept by hand beside the contract.
- **409 as the default for a refused rule, or a status per code.** Rejected: 409 fits a state
  conflict but not a blank name or a short password, and a table of about 200 codes would grow with
  every error, while no client acts on the difference.
- **412 for a stale body version.** Rejected: 412 names a precondition header the client never sent,
  as [ADR-121] found for calls that send no version.
- **`@Retryable` on each controller method.** Rejected: one forgotten annotation answers a lost race
  with 500, and it can't see whether the request sent a version.
- **A transaction synchronization to detect a commit.** Rejected: the wrapper runs outside every
  transaction, where spring-tx 7.0.10 refuses one, and starting synchronization there would stop the
  inner transactions' own after-commit callbacks.
- **Always a fresh trace ID.** Rejected: it breaks the hop from the web app to the core that
  [TS §16][TS-16] and [NFR-20] require.
- **Accept unknown properties.** Rejected: a misspelt optional field would be dropped silently,
  against [NFR-56]. **Validate before the role check.** Rejected: a caller who may not call the
  operation would learn what a valid body looks like.
- **`@Bean` methods, or scanning the service packages.** Rejected: `@Bean` methods repeat every
  constructor; scanning wires every throwing skeleton, and a skeleton whose port has no adapter
  stops the application from starting.
- **`@EnableScheduling` and `@EnableAsync` on `DispatchFlowApplication`.** Rejected: the jobs would
  fire on the wall clock in every integration test.

## Revisit when

- Spring Modulith's `verify()` ([#145][ISSUE-145]) finds a cycle through `web` or the kernel's
  adapters: the boundary is wrong, and a new ADR moves it.
- Audit needs a web operation: its controller would need `CurrentActor`, an arrow from Audit to
  Access that closes a cycle with Access's arrow to Audit.
- A generator upgrade, or the Spring Boot 4.2 move in January, changes the packages, the tags, the
  extra annotation or the event-stream output.
- Two lanes need the generated sources in their editor before a build, or the build becomes too slow:
  committing them is reconsidered.
- An operation needs two capabilities ([ADR-068]'s trigger), or a second entry, such as a job, reaches
  a role-gated use case.
- The first end-to-end test needs a control endpoint: the stand-in also allows exactly `e2e`, or
  sign-in replaces it, in a new ADR.
- A client needs to tell two refusals apart that share a status: the code's status is overridden in
  decision 5's table, before that code is released.
- An app must reach users before the core can be deployed with a new request property: unknown
  properties are accepted on that operation, in a new ADR.
- The load test ([NFR-239]) shows retries colliding or the pool emptying behind a long lock: a
  jittered pause between tries, or a cap on concurrent retries, in a new ADR.
- A use case can't be built by `@Import`, for example because it needs a factory: decision 10 is
  revisited for that module.
- The invocable-handler hook breaks in a Spring upgrade: the `@Around` advice replaces it.
- The OpenTelemetry agent arrives: decision 8's filter is deleted.

[ADR-040]: ADR-040-core-is-a-spring-modulith-monolith-with-one-module-per-context.md
[ADR-043]: ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-068]: ADR-068-every-web-handler-is-authorized-by-role-from-the-contract.md
[ADR-069]: ADR-069-sign-in-and-transport-security.md
[ADR-073]: ADR-073-runtime-contract-validation.md
[ADR-081]: ADR-081-eas-update-for-javascript-only-fixes-during-the-pilot.md
[ADR-082]: ADR-082-supported-phone-app-versions.md
[ADR-087]: ADR-087-driver-reports-sync-through-an-idempotent-outbox.md
[ADR-104]: ADR-104-the-company-profile-holds-customer-defaults-and-capabilities-follow-the-role-matrix.md
[ADR-119]: ADR-119-the-application-layer-is-declared-ahead-as-signatures-whose-bodies-throw.md
[ADR-120]: ADR-120-the-application-layer-takes-an-actor-and-a-call-context-and-reads-through-query-ports.md
[ADR-121]: ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md
[ADR-126]: ADR-126-jpa-replaces-jooq-and-the-domain-carries-its-mapping.md
[ADR-129]: ADR-129-transactions-start-only-in-the-service-layer.md
[ADR-130]: ADR-130-every-folder-is-declared-ahead-empty-and-documented.md
[ADR-131]: ADR-131-the-declared-structures-review-tightens-its-rules-and-fills-its-gaps.md
[ADR-136]: ADR-136-the-core-waits-two-seconds-for-a-lock-and-a-lock-it-cannot-get-is-retried.md
[TS-2-1]: ../architecture/tech_stack.md#21-the-dependency-rule
[TS-16]: ../architecture/tech_stack.md#16-observability-and-alerting
[ISSUE-143]: https://github.com/NaimElijah/DispatchFlow/issues/143
[ISSUE-145]: https://github.com/NaimElijah/DispatchFlow/issues/145
[ISSUE-146]: https://github.com/NaimElijah/DispatchFlow/issues/146
[ISSUE-148]: https://github.com/NaimElijah/DispatchFlow/issues/148
[ISSUE-161]: https://github.com/NaimElijah/DispatchFlow/issues/161
[ISSUE-162]: https://github.com/NaimElijah/DispatchFlow/issues/162
[ISSUE-170]: https://github.com/NaimElijah/DispatchFlow/issues/170
[ISSUE-171]: https://github.com/NaimElijah/DispatchFlow/issues/171
[ISSUE-182]: https://github.com/NaimElijah/DispatchFlow/issues/182
[ISSUE-184]: https://github.com/NaimElijah/DispatchFlow/issues/184
[ISSUE-193]: https://github.com/NaimElijah/DispatchFlow/issues/193
[ISSUE-196]: https://github.com/NaimElijah/DispatchFlow/issues/196
[ISSUE-197]: https://github.com/NaimElijah/DispatchFlow/issues/197
[ISSUE-199]: https://github.com/NaimElijah/DispatchFlow/issues/199
[ISSUE-259]: https://github.com/NaimElijah/DispatchFlow/issues/259
[FR-247]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-248]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-331]: ../requirements/requirements.md#publishing-and-the-phone-app
[NFR-20]: ../requirements/requirements.md#nfr-20
[NFR-56]: ../requirements/requirements.md#nfr-56
[NFR-74]: ../requirements/requirements.md#nfr-74
[NFR-92]: ../requirements/requirements.md#nfr-92
[NFR-93]: ../requirements/requirements.md#nfr-93
[NFR-192]: ../requirements/requirements.md#nfr-192
[NFR-239]: ../requirements/requirements.md#nfr-239
[Q-92]: ../requirements/requirements.md#open-questions
[RFC-6750]: https://www.rfc-editor.org/rfc/rfc6750
[RFC-9110]: https://www.rfc-editor.org/rfc/rfc9110
[W3C-TC]: https://www.w3.org/TR/trace-context/
