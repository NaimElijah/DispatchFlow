# ADR-148: The stand-in serves only its own origin, every unsigned call under `/api/v1` answers 401, and the guard outlives the session

- **Status:** Accepted; extended by ADR-160
- **Date:** 2026-11-26

## Context

[ADR-147] settled what #170's stand-in session left open (#296). A review after the merge, with
each claim tried in a test or a probe against the pinned Tomcat 11.0.27 and Spring Framework 7,
found six gaps:

- **A page on another `localhost` port acted as the Dispatcher.** [ADR-147] decision 7 refuses
  only `Sec-Fetch-Site: cross-site`. A site ignores the port, so a page on `http://localhost:8888`
  is `same-site` with the core on `:8080`, and a request that needs no preflight, such as a bodyless
  `POST`, ran as the Dispatcher. Nothing legitimate needs `same-site`: the web app's production
  policy allows connections to its own origin alone (`connect-src 'self'`, which `vite preview`
  sends), and the core has no CORS configuration, so the web app can reach the core only through
  a proxy on its own origin, whose requests are `same-origin`. No such proxy exists yet: neither
  Vite's dev server nor the Compose web container (#146) proxies `/api` today.
- **The loopback check trusted a header any client may send.** It read `getServerName()`. Under
  forwarded-header handling, the usual way to trust Caddy's `X-Forwarded-For` as [ADR-071] plans,
  Tomcat's `RemoteIpValve` and Spring's `ForwardedHeaderFilter` rewrite that name from
  `X-Forwarded-Host` or `Forwarded`. A page that rebinds its own name to `127.0.0.1` is
  same-origin with itself, so it may add those headers, and its browser sends no `Sec-Fetch-Site`,
  because the request goes to the page's own `http://` name, which is no potentially trustworthy
  URL ([Fetch Metadata][FETCH-METADATA]). [ADR-147] decision 7's reasoning, that a request with no
  `Sec-Fetch-Site` is no page's, is wrong for exactly that page: only the `Host` check refuses it.
  The check also compared case-sensitively, so `Host: LOCALHOST` was refused, though a host name
  is case-insensitive ([RFC 9110][RFC-9110] §4.2.3).
- **An exported `SERVER_ADDRESS` widened `just core-run`.** `server.address=127.0.0.1` is a
  profile file's value, so `SERVER_ADDRESS=0.0.0.0`, a common step to reach the core from a phone,
  outranks it, and any machine on the network that sent `Host: localhost` was signed in.
- **A handler under `/api/v1` that isn't an annotated method served a caller with no principal.**
  [ADR-140] decision 3 refuses it by default, but the interceptor let it pass, and #171, which holds
  the rest of that refusal, is stretch work. A router function under `/api/v1` would have served
  anyone. And Spring MVC lets its advice see an exception from an annotated method, a router
  function and the static-resource handler alone, so a refusal thrown for any other handler type,
  such as an `HttpRequestHandler` bean, would have answered 500 and an ERROR line, at any client's
  will. Until sign-in exists, which waits for #171 ([ADR-140] decision 3), no deployed core has a
  principal at all.
- **Any class could build a `CallContext` naming any account.** Rule 17 limits the `Actor`, and
  rule 21 keeps both types out of a handler's parameters, but a record's constructor is always
  public. A controller or a job could put another account in an audit record ([NFR-93]). Rule 21
  also read raw parameter types, so `Optional<Actor>` and `Actor[]` passed it.
- **[ADR-147]'s "Revisit when" deletes the guard with the session.** The guard's second check
  refuses any stand-in in a deployed environment, and the stand-in adapters of #184, #196 and #197
  outlive the session: [ADR-147] itself says "the guard covers it". Deleting the guard with sign-in
  would let a deployment that runs `dev` by mistake, with the marker set, boot on stand-in SMS and
  push.

## Decision

Decided on 26 November 2026.

1. **The stand-in serves only its own origin's pages.** It signs in a request with no
   `Sec-Fetch-Site`, or with `same-origin` or `none`, and no other value: `same-site`,
   `cross-site` and any value Fetch Metadata doesn't define are refused. This amends [ADR-147]
   decision 7. A request with no `Sec-Fetch-Site` is a client's that is no browser, such as `curl`
   or the phone app, or one to a URL that is no potentially trustworthy URL, as a rebinding page's
   own `http://` name is, which decision 2 refuses.
2. **The loopback check reads the `Host` header itself.** It takes the header's host, without its
   port and in lower case, and signs in only `localhost`, `127.0.0.1` and `[::1]`. A request with no
   `Host` header, which no browser sends and Tomcat refuses on HTTP/1.1, falls back to the server
   name; MockMvc's requests carry none. This replaces [ADR-147] decision 7's reasoning on a request
   without `Sec-Fetch-Site`.
3. **The loopback address is pinned where it outranks the environment.**
   `TestDispatchFlowApplication` passes `--server.address=127.0.0.1` on the command line, beside
   Flyway's option, and `@TomcatIntegrationTest` sets it in `@SpringBootTest`'s properties. The
   Compose core's address stays #146's, which publishes the port on `127.0.0.1` alone.
4. **A call with no principal to any handler under `/api/v1` answers the one 401.** A handler that
   isn't an annotated method, such as a router function or the static-resource handler of a path
   no operation has, is no operation, so it is never open, and the interceptor answers 401
   `ACCESS_TOKEN_REFUSED` without a principal. The refusal carries `@ResponseStatus(401)`, so a
   handler type whose exceptions Spring's advice never sees is answered by the error endpoint with
   the same code and challenge. It writes no audit record, since it names no operation. With a
   principal it passes, with no caller kept, until #171 adds the role check and the rest of
   [ADR-140] decision 3's default refusal. A CORS preflight still passes. This extends [ADR-140]
   decision 3 ahead of #171, and amends its decision 9's fifth check: only a changing call to an
   operation writes the refused record. An unknown path under `/api/v1` answers 401 before 404 to
   a caller with no principal.
5. **Only `web.security` builds a `CallContext`, beside the context's own server step.** Rule 17
   now covers both types: no class outside `web.security` calls or refers to the constructor of
   `Actor` or of `CallContext`, but the type itself. So `CallContext.serverStep`, which names no
   one, is how a job or a listener gets a context, and no other class of Audit builds one either:
   a whole-module exemption would have let Audit's own job and server-step adapters name any
   account. This extends [ADR-140] decision 4. Rule 21 now reads every type a parameter involves,
   so a type argument or an array of either type is caught too. Left to review: a type of the
   handler's own that holds one as a field, a `@ModelAttribute` method of a `@ControllerAdvice`,
   whose parameters Spring also binds from the request, and reflection, for rules 17 and 18.
6. **The guard outlives the session.** Sign-in (#199) deletes `StandInSession` and the list's reader
   in `web.security`, `SeededAccounts`, never `StandInGuard`. Its deployed-environment check stays
   as long as any `@StandInOnly` bean does, and its first check keeps refusing the seeded list,
   which #181's seed still reads, under any other set of profiles. It stays in `web.security`
   until then, and #199 moves it beside `StandInProfiles` in the root package, since it then
   guards only the modules' stand-ins and uses nothing below the root. This amends [ADR-147]'s
   "Revisit when", which had the guard go with the session.

Also recorded:

- **[ADR-147] decision 6 amends [ADR-140] decision 4 too.** [ADR-140] decision 4 has the stand-in
  act as "the Dispatcher unless a property names another". Since [ADR-147] decision 6 the property
  is required, and a run without it refuses to boot.
- **The MDC keeps the caller on every dispatch.** The trace filter puts a kept caller's `accountId`
  and `sessionId` back on an error or async dispatch, as it does the trace ID, so the error
  endpoint's one ERROR line says whose call failed ([ADR-140] decision 8).

## Consequences

- **Easy:**
  - No page in the developer's browser but the app's own origin can act through the stand-in, and
    a forwarded header can't change that.
  - Every deployed core refuses every unsigned call under `/api/v1`, whatever handler serves it,
    without waiting for #171.
  - An audit record's caller comes only from `web.security`, or from no one.
- **Hard:**
  - The web app must reach the core through a proxy on its own origin, which neither Vite's dev
    server nor the Compose web container has yet. Whoever first wires the web app to the core adds
    one that keeps the browser's `Host` and `Sec-Fetch-Site`, as #146 must for the Compose core.
    A direct call from `:5173` to `:8080` is refused, as the missing CORS configuration already
    refused it.
  - The phone app reaches a local core as `localhost`: the iOS simulator does so itself, and an
    Android emulator or device through `adb reverse tcp:8080 tcp:8080`. An emulator's `10.0.2.2`
    is refused, and a phone on the network can't reach a core bound to `127.0.0.1`.
- **Cost:** a few lines in the stand-in, the interceptor and the trace filter, one rule widened and
  one generalised, and their tests.
- **Risk:**
  - A browser that sends no Fetch Metadata, such as Safari before 16.4, is served from any page on
    `localhost`. Such browsers are years old, and the data is a developer's throwaway database.
  - An unknown path under `/api/v1` answers 401 rather than 404 to a caller with no principal. A
    path an operation has still answers 405, 406 or 415 before the 401, since Spring refuses those
    while it chooses the handler ([ADR-140] decision 9), so the 401 hides no operation's path; the
    contract publishes every one anyway.

## Alternatives considered

- **Keeping [ADR-147] decision 7 and refusing only `cross-site`.** A page on any `localhost` port
  could keep acting as the Dispatcher, while nothing legitimate sends `same-site`.
- **Also checking `Origin` against `Host`.** It would cover old browsers without Fetch Metadata, at
  a second check that every same-origin request must pass. Fetch Metadata has shipped in every
  current browser since 2023.
- **Checking the peer's address.** The Compose core's peer would be the Docker network's gateway,
  never the loopback, so the check would refuse the Compose core's every call.
- **Leaving the default refusal to #171.** #171 is stretch work, and until it lands a router
  function under `/api/v1` would serve anyone in a deployed core.
- **Turning off Spring's static-resource mapping instead.** It would make an unknown path answer
  404 again, but leave a router function or another handler type open.
- **Making `CallContext`'s constructor private behind factories.** A record's canonical constructor
  can't be private, and a public factory would be as open; only a rule limits who builds one.

## Revisit when

- A client must call the core directly from another origin, which needs CORS and reopens decision
  1.
- #171 lands: it takes over decision 4 within the full default refusal.
- The last `@StandInOnly` bean goes: the guard goes with it.
- A browser the team supports sends no Fetch Metadata.

[ADR-071]: ADR-071-api-rate-limits-in-process-with-bucket4j.md
[ADR-140]: ADR-140-the-app-wide-web-package-sits-above-the-modules.md
[ADR-147]: ADR-147-the-stand-in-condition-sits-in-the-root-package-and-a-refused-access-token-answers-one-code.md
[NFR-93]: ../requirements/requirements.md#nfr-93
[FETCH-METADATA]: https://www.w3.org/TR/fetch-metadata/
[RFC-9110]: https://www.rfc-editor.org/rfc/rfc9110#section-4.2.3
