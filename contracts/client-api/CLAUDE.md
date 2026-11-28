# Client API

## Conventions

Every operation follows these; [ADR-140], [ADR-155] and [ADR-157] hold the reasons:

- `x-capability` ([ADR-068]) and a stable `operationId`, which names the generated method and the
  audit record's operation. The core reads `traceparent` itself: no operation declares it;
- exactly one tag, its module's package name and its resource (`planning-proposals`,
  `liveops-routes`), giving one `<Tag>Api` per tag. The event stream's operation carries
  `x-internal: true`, so the generator skips it and `web.stream` serves it by hand; it is still
  public and versioned, carries `x-capability` and is exempt from the tag rule;
- `additionalProperties: false` on every object schema reachable from a request body
  (`unevaluatedProperties: false` under `allOf`), never reused in a response, so an undeclared
  property answers 400. Response schemas stay open, because an optional field added to one isn't
  breaking;
- an app sends a new request property only once the core release that declares it won't be rolled
  back past. On the phone's writes a request property stays declared for the life of v1; elsewhere
  a retired one stays accepted and ignored, never removed in v1 ([ADR-082]);
- RFC 9457 errors: `Problem` has `type` `about:blank`, `code`, `params` and `traceId`, `errors` on
  a 400 and a 422 that lists several ([ADR-043]), and a deprecated `detail`, never set. `code` is
  a string, never an enum, so a new code isn't breaking; each needs its entry in the description,
  and Hebrew and English text with a next step (NFR-144);
- refusals that [NFR-92] and [NFR-145] require to look the same share one `code`, one status and
  empty `params`, and `params` never carries personal data, secrets or tokens. So the refresh
  token is a plain string, with no pattern, length or format. A path parameter or a body's map key
  carries an ID, never personal data: `Problem.instance` echoes the path, a 400's pointer the key;
- statuses:
  - 400 only for a request that breaks the contract. A failed validation lists its failing fields
    or rows at once, up to 100 (ADR-142); an unreadable body, a duplicate key or an unknown
    property is reported alone, as the first one found. Each item has a pointer into the body, the
    parameter's name and place, or neither for the request as a whole, and a code, never the value
    ([ADR-043]). A missing required header, such as `If-Match`, answers 400;
  - 413, with its own code, for a body over its size limit; the proof and report operations'
    limits sit above the domain's own;
  - 422 for a refused rule; 401, with exactly `WWW-Authenticate: Bearer` and one code, for any
    refused access token (an open operation ignores `Authorization`); 403 for the role, step-up,
    own-route and `GRANT_LIMIT_EXCEEDED` refusals;
- `ETag`, `If-Match` and `If-None-Match`: a failed `If-Match` or `If-None-Match` answers 412
  `STALE_VERSION`, any `StaleVersion` on such a call included; a stale version sent in a body, as
  accepting a proposal sends the plan's, answers 409 `STALE_VERSION`; neither is retried. A call
  with no precondition header, and any failed lock wait, is retried, then 503 with `Retry-After`
  ([ADR-057], [ADR-121]). An `If-Match` is exactly one strong tag the core sent, so a wildcard, a
  weak tag or a list answers 400 `WRONG_FORMAT`; the shared `IfMatch` is for a record that always
  exists, and an operation whose first save sends `If-None-Match: *` declares its own pair
  ([ADR-157]);
- every answer carries `Cache-Control: no-store, no-transform` ([ADR-157]), and a response header
  the core always sends is `required: true`;
- in a body, a moment, or a time a solve works out from the date's midnight in Israel, is an
  RFC 3339 `date-time` in UTC. A time a person enters for a date is that date and a local
  `HH:mm`, both ways, which the core resolves ([BR-111]), and a time of day that holds on every
  date, such as a depot's hours, a local `HH:mm`; both under one shared schema with an `HH:mm`
  pattern, never `format: time`. A drop's fact is a `PlanTime` even where it copies a typed time.
  A list whose values carry fields is a `oneOf` with a `kind`, one of plain values the optimizer
  decides, or that has grown, a string with its values listed, and any other an `enum`
  ([ADR-155]);
- `Idempotency-Key` on driver reports, route starts and finishes, problem reports ([ADR-087],
  [ADR-121]) and position batches ([NFR-285]), and the client headers ([ADR-082]).

[ADR-043]: ../../docs/ADRs/ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-057]: ../../docs/ADRs/ADR-057-concurrent-changes-to-a-plan-use-optimistic-locking.md
[ADR-068]: ../../docs/ADRs/ADR-068-every-web-handler-is-authorized-by-role-from-the-contract.md
[ADR-082]: ../../docs/ADRs/ADR-082-supported-phone-app-versions.md
[ADR-087]: ../../docs/ADRs/ADR-087-driver-reports-sync-through-an-idempotent-outbox.md
[ADR-121]: ../../docs/ADRs/ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md
[ADR-140]: ../../docs/ADRs/ADR-140-the-app-wide-web-package-sits-above-the-modules.md
[ADR-155]: ../../docs/ADRs/ADR-155-the-client-api-keeps-instants-and-local-times-apart-and-sends-a-list-that-may-grow-as-a-string.md
[ADR-157]: ../../docs/ADRs/ADR-157-an-if-match-is-exactly-one-strong-tag-the-core-sent-and-the-proxy-leaves-the-apis-tags-alone.md
[BR-111]: ../../docs/requirements/requirements.md#time-and-limits
[NFR-92]: ../../docs/requirements/requirements.md#nfr-92
[NFR-145]: ../../docs/requirements/requirements.md#nfr-145
[NFR-285]: ../../docs/requirements/requirements.md#nfr-285
