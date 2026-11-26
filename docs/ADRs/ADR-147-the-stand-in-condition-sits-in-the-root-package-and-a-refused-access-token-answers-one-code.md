# ADR-147: The stand-in condition sits in the root package, a deployed environment is told by a marker and three keys, and a refused access token answers one code

- **Status:** Accepted; amended by ADR-148 and ADR-149
- **Date:** 2026-11-26

## Context

[ADR-140] decision 4 settles how the core knows its caller before sign-in exists: a stand-in
session of a seeded account, two holders that the call wrapper binds, and an interceptor that
answers 401. #170 builds them. Building them left four questions that the ADRs leave open:

- **Where the stand-in's condition lives.** [ADR-140] decision 10 gives every `<Port>StandIn`
  adapter "the stand-in session's own condition", and [ADR-133] gives #181's seed the same one. Those
  adapters sit in the modules (#184, #196, #197), and the seed in Access's `adapter/in/job`. No module
  may depend on `web` (`ApplicationRules` rule 2), so a condition kept in `web.security` could serve
  none of them.
- **What marks a deployed environment.** [ADR-140] decision 4 refuses the stand-in when the
  `DISPATCHFLOW_DEPLOYED` marker or "any of the SMS provider's, the email sender's or the push
  service's credentials" is set. No credential had a name: the SMS vendor is open, the push
  service's token is unnamed, and the email sender, SES, authenticates with the VM's IAM role
  ([TS §6][TS-6]), so it has no credential to see.
- **What the one 401 code is called.** [ADR-140] decision 7 gives a missing, expired, malformed or
  revoked access token, and a `SESSION_REVOKED` from a use case, one code with empty `params`, and
  leaves its name to the pull request that first answers it.
- **Where the code's text lives.** [NFR-144] gives every code Hebrew and English text with a next
  step. The shared error texts live in `src/frontend/packages/i18n`, which #173 builds and which is
  empty today.

## Decision

Decided on 26 November 2026.

1. **The condition sits in the root package.** `com.dispatchflow.StandInProfiles` is the condition,
   true when the active profiles are exactly `dev` or exactly `test`, and `@StandInOnly` is the
   meta-annotation a stand-in bean carries. The root package is [TS §2.1][TS-2-1]'s configuration
   layer. `web` and every module's adapters may use it: rules 2, 10, 11 and 13 refuse none of them.
   Rule 1 and the domain's purity rules keep it out of the application layer and the domains.
   `web.security`'s guard, which refuses to boot where a stand-in must not run, uses it too.
   - **The root stays a leaf.** No class directly in the root package uses a class below it, of a
     module, the kernel or `web` (`ApplicationRules` rule 19). That is what makes an adapter's use
     of the root safe: the root can never use the adapter back, so no cycle forms. A module's wiring
     stays in its own `<Module>Configuration` ([ADR-140] decision 10).
   - **It names the keys every stand-in reads**: the shared list's prefix,
     `dispatchflow.seeded-accounts`, and the stand-in session's, `dispatchflow.stand-in`. Each reader
     binds only the parts of an entry it uses: the stand-in session its `id` and `roles`, in
     `web.security`, and #181's seed its `address` and `language` too, in Access's own adapter,
     because no module may use `web`.
2. **A deployed environment is told by a marker and three keys.**
   - The marker is `dispatchflow.deployed`, the environment variable `DISPATCHFLOW_DEPLOYED`, which
     every deployed environment sets. Any value counts.
   - The keys are `dispatchflow.sms.credential`, `dispatchflow.email.credential` and
     `dispatchflow.push.credential`, the environment variables `DISPATCHFLOW_SMS_CREDENTIAL`,
     `DISPATCHFLOW_EMAIL_CREDENTIAL` and `DISPATCHFLOW_PUSH_CREDENTIAL`, named once as
     `StandInProfiles.SMS_CREDENTIAL`, `EMAIL_CREDENTIAL` and `PUSH_CREDENTIAL`. Each provider's adapter
     reads its secret from its key through that constant, and the core's `CLAUDE.md` says so. A
     credential set by mistake on a developer's machine then refuses the stand-in too.
   - SES's role can't be seen from inside the process, so for email the marker is the guard.
   - The refusal names the keys that are set, never their values.
3. **The one 401 code is `ACCESS_TOKEN_REFUSED`.** It answers a call with no principal on an
   operation that isn't open, any refused access token, and a `SESSION_REVOKED` from a use case,
   always with empty `params` and exactly `WWW-Authenticate: Bearer`. A client refreshes when it gets
   it, and signs in again when the refresh answers `REFRESH_REFUSED`. It mirrors that code, which
   [NFR-92] already gives every refused refresh. The audit record and the log line keep the specific
   code, such as `SESSION_REVOKED`. The contract's `Problem` names it, `WebCodes.forStatus(401)` maps
   to it, and `ErrorEndpoint` and the contract's handler add the challenge to any 401 they answer.
   The constant sits in `shared.adapter.in.web.Problems`, beside the bare challenge and
   `Problems.unauthorized`, because both `web` and `web.security` answer it, and `web.security` may
   not use `web` (decision 5).
4. **The code's Hebrew and English text goes to #173.** The pull request and a comment on #173 hold
   both texts. #173 adds them with the package it designs, so this decision changes no frontend file.
5. **The interceptor builds the caller, and the call wrapper only binds it.** This amends [ADR-140]
   decision 4, which has the call wrapper build the holders from the principal and the chosen
   handler, while the same decision lets only `web.security` build an `Actor`. `web.security`'s
   access interceptor builds the call's `Caller`, its `CallContext` and its `Actor`, once the handler
   is chosen, and keeps it in a request attribute. The call wrapper, in `web`, reads it and binds
   `CurrentCall` and `CurrentActor` around the handler, its retries included. So `web` depends on
   `web.security`, never the reverse (`ApplicationRules` rule 20), and what both need sits in
   `shared.adapter.in.web`: the 401's code, and `FilterOrder`, which puts the principal's filter
   after the trace filter.
6. **The main classpath's `dev` file names no stand-in account.** `application-dev.properties` ships
   in the image, and imports the seeded list, but leaves `dispatchflow.stand-in.account` out, as it
   leaves Flyway out ([ADR-146] decision 4). A run that uses `dev` names the account itself: the test
   classpath's `core-run.properties` for `just core-run`, at a precedence below the environment, and
   `DISPATCHFLOW_STANDIN_ACCOUNT` for the Compose core (#146). So a deployment that runs `dev` by
   mistake, even without the marker, refuses to boot, because the stand-in session finds no
   account. The session also runs the guard's check itself as it is made, and the guard is never
   lazy, so no bean-factory setting lets the session exist unchecked.
7. **The stand-in serves only this machine's own pages.** It signs in no request whose host is not
   `localhost`, `127.0.0.1` or `::1`, and none a browser marks `Sec-Fetch-Site: cross-site`. While
   `just core-run` is up, a page on another site could otherwise act as the Dispatcher: by a
   cross-site request that needs no preflight, or by rebinding its own host name to the loopback
   address, which leaves that name in `Host`. A client that sends no `Sec-Fetch-Site`, such as
   `curl`, is no page and is served. The Compose core's web proxy must keep the browser's `Host`, as
   Vite's proxy does by default (#146).

Choices #170 made within [ADR-140], recorded here so a maintainer needn't reverse-engineer them:

- **The shared list** is `seeded-accounts.properties` on the main classpath. Each entry sits under
  `dispatchflow.seeded-accounts.<entry>`, with its `id`, `roles`, `address` and `language`. It starts
  with `admin`, `dispatcher` and `driver`, whose invented addresses use the reserved `.test` domain
  ([RFC 6761][RFC-6761]). `application-dev.properties` and the test classpath's
  `application-test.properties` both import it. A test reads its IDs from the file itself, through
  `TestActors`.
- **The stand-in's account** is the entry that `dispatchflow.stand-in.account` names, required, and
  set to `dispatcher` by every run that uses the stand-in (decision 6). Its session is a fixed
  `SessionId` in code, which no stored session row matches. The audit record keeps a session's ID
  with no foreign key.
- **The stand-in's configuration is any property** under `dispatchflow.stand-in` or
  `dispatchflow.seeded-accounts`. With one present, the guard refuses any set of active profiles but
  exactly `dev` or exactly `test`, so `dev` beside `e2e` is refused, and so is a stand-in property
  exported where no such profile is active.
- **The refresh cookie is `__Host-refresh`.** The stand-in serves no request that carries it or an
  `Authorization` header, and #199 sets and reads it under that name.
- **The principal and the caller are request attributes** that only `web.security` writes
  (decision 5). The interceptor answers 401 by throwing an exception, which `web.security`'s handler
  answers through the served converters, so the body is built as every other problem's is.
- **The client a call declares** is kept only when `X-App` is `web` or `phone` and `X-App-Version`
  is three numbers, so no other client text reaches a record ([ADR-110] leaves the client out of
  anything else).
- **The MDC carries a signed-in call's `accountId` and `sessionId`** beside its `traceId`, from the
  interceptor on ([ADR-140] decision 8).
- **The holders bind on the request's own dispatch alone**, so an error dispatch, which keeps the
  request's attributes, runs the error endpoint as no caller.
- **A race's audit record names the caller.** `CallRetry` writes the record with the call's own
  context on a call under `/api/v1`, and with a server step's context on any other handler
  ([NFR-93]).
- **The audit log is looked up lazily** in the interceptor, as in `CallRetry`, until #180 makes it a
  bean. Until then, a 401 on a changing call answers 500 instead, and the tests fake the log.
- **Five rules join `ApplicationRules`.** Rule 17 lets only `web.security` build an `Actor`. Rule 18
  lets only the call wrapper call a holder's method other than a no-argument `get`. Rule 19 keeps
  the root a leaf (decision 1), and rule 20 keeps `web.security` off `web` (decision 5). Rule 21 lets
  no handler take an `Actor` or a `CallContext` parameter, which Spring would build from the
  request's own parameters. A check reports a holder that rule 14 names and no class is.

## Consequences

- **Easy:**
  - A stand-in adapter takes one annotation, and the guard covers it.
  - A test of a Driver's or an Admin's refusal takes its actor from `TestActors`.
  - A client tells "refresh" from "sign in again" by the code alone.
- **Hard:**
  - Each provider issue must read its secret from its reserved key. A credential under another name
    would not refuse the stand-in, and no test can catch a name a future adapter picks; review does.
- **Cost:** two annotations' worth of code in the root package, five rules, one more 401 row in
  the contract, and one more variable for the Compose core.
- **Risk:**
  - A deployment that runs `dev` and also names a stand-in account, but sets neither the marker
    nor a reserved key, would serve the stand-in. Every deployed environment's configuration sets
    the marker, and the deployment issue that writes it checks so.
  - Until #180, a 401 that must be audited answers 500: under any profiles without the stand-in,
    every changing call to `/api/v1` with no principal, and under the stand-in profiles, a changing
    call that presents a credential. No operation exists yet, and the first changing one waits for
    #180.

## Alternatives considered

- **The condition in `web.security`.** It sits beside the session, but no module's stand-in could
  use it, and each issue that needs one would copy or move it.
- **The condition in `shared.adapter.in.web`.** Reachable from the modules, but that package is the
  web code the controllers share, and the stand-ins of `out/sse`, `out/optimizer`, `out/osrm` and
  `in/job` would import a web package for a profile check.
- **`@Profile({"dev", "test"})`.** [ADR-140] decision 10 turns it down: it also holds beside any
  other profile.
- **Detecting AWS keys too.** `AWS_ACCESS_KEY_ID` would catch static SES keys, but it would stop
  `just core-run` on a laptop that exports AWS keys for other work. The VM uses a role, so it buys
  nothing there.
- **The marker alone, each provider issue naming its credential later.** Fewer names reserved now,
  but three later issues would each have to remember the guard.
- **`SIGN_IN_REQUIRED` or `NOT_SIGNED_IN`.** Both read well for a missing token, but they tell a
  client with an expired token to skip the refresh it should try first.
- **Building `packages/i18n` here.** It would settle #173's structure ahead of it, in a core pull
  request.
- **The stand-in's account in `application-dev.properties`.** As the issue first placed it, but
  the image would then carry everything a mistaken `dev` deployment needs to serve the stand-in,
  with the marker its only guard.
- **No check on the host or the site.** The loopback binding stops other machines, but not a page
  in the developer's own browser.
- **Writing the 401 from the interceptor itself.** It would build the JSON outside the served
  converter, a second way to make a problem body.

## Revisit when

- A provider adapter needs a credential that isn't one secret, such as a key pair.
- A deployment runs without a configuration that sets the marker.
- Sign-in lands (#199): the stand-in, its guard and this condition's use by the session go. The
  `<Port>StandIn` adapters keep the condition until their real adapters land.
- The first end-to-end test needs a control endpoint, which reopens [ADR-140] decision 4.

[ADR-110]: ADR-110-audit-records-hold-only-ids-codes-enums-numbers-and-instants-record-refusals-and-are-kept-a-year.md
[ADR-133]: ADR-133-a-startup-step-is-a-job-and-access-declares-one.md
[ADR-140]: ADR-140-the-app-wide-web-package-sits-above-the-modules.md
[ADR-146]: ADR-146-the-jar-takes-only-options-but-for-its-command-and-the-roles-guards-cover-every-object-and-log.md
[NFR-92]: ../requirements/requirements.md#nfr-92
[NFR-93]: ../requirements/requirements.md#nfr-93
[NFR-144]: ../requirements/requirements.md#nfr-144
[RFC-6761]: https://www.rfc-editor.org/rfc/rfc6761
[TS-2-1]: ../architecture/tech_stack.md#21-the-dependency-rule
[TS-6]: ../architecture/tech_stack.md#6-core-service-java
