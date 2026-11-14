# ADR-110: Audit records hold only IDs, codes, enums, numbers and instants, record refusals, and are kept a year

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

The Audit domain is built ahead of its milestone, from [DL §4.8][DL-48] and the requirements. Every
API call that changes something, and every sign-in, refresh and sign-out, leaves one audit record,
and reads are not audited ([NFR-23]). [NFR-93] lists what a record holds: when, the acting user and
session when known, the app and its version as the client declares them, the operation, the records
it names, its outcome and its trace ID. A record never carries personal data or a secret ([NFR-94]).
Audit records are deleted after a year ([NFR-258]), by a database function that computes its own
cutoff ([ADR-075], [ADR-041]). An Admin filters them by user, operation, record and date ([FR-126]).

[DL §4.8][DL-48] said where records are written and how they are purged, but not what one holds.
Two questions were open:

- **Failures.** [NFR-23] audits every sign-in, and a failed sign-in is a sign-in. [NFR-93] names an
  outcome but not its values. A refused call rolls its transaction back, and the audit record written
  in that transaction would go with it.
- **Retention's owner.** The purge function deletes by its own cutoff, so nothing in the code said
  where the year lives.

Every call carries `X-App` (web or phone) and `X-App-Version` ([ADR-082]), releases use semantic
versions ([TS §5][TS-5]), and the trace ID is a W3C trace-context ID, the same in every log line,
audit record and client error report ([TS §16][TS-16], [NFR-20]).

## Decision

On 2026-11-14 the owner, Naim Elijah, decided these for the team:

1. **Failures are audited, with an outcome field** ([NFR-23], [NFR-93]). `AuditOutcome` is
   *succeeded*, or *refused* with the refusal's error code (`ErrorCode`): a domain error's stable
   code, or a code the adapters answer with, such as `STALE_VERSION`. The record keeps the code alone,
   never the error's parameters. Writing a refused attempt in its own transaction, so that the
   refusal's rollback doesn't lose it, is the application layer's job in a later milestone. This
   ADR builds only the domain value.
2. **Audit owns its retention period: a year** ([NFR-258]). `AuditRecord.RETENTION` is one year on
   Israel's calendar ([ADR-088]), and `AuditRecord.isPastRetention(now)` answers whether a record is
   older than that. The database function of [ADR-075] still deletes the records and computes its
   own cutoff, which must match this period.

### Readings this ADR settles

No document said these, and the implementation reads them so:

- **Exactly NFR-93's fields, each an ID, a code, an enum, a number or an instant.** `AuditRecord`
  holds `at`, `user` (an `AccountId`, when known), `session` (a `SessionId`, when known), `app`, the
  `operation`, the named `records`, the `outcome` and the `traceId`, and no free text, so
  [NFR-94] holds by type. It has no ID of its own: [NFR-93] names none, and the persistence adapter
  may add a key.
- **User and session are each recorded when known, independently.** [NFR-93] ties neither to the
  other, so the domain doesn't either; a refused sign-in typically knows neither.
- **The app and its version are one optional pair,** `DeclaredApp`, because [ADR-082] sends both on
  every call. A server step that no client made, such as [FR-292]'s written-request step, has
  neither. The app is `ClientApp.WEB` or `PHONE`. The version is `AppVersion`: a semantic version's
  three numbers, each a non-negative `int`, without leading zeros. A pre-release or build suffix is
  refused with `InvalidAppVersion`, so no text a client sends reaches a record. The over-the-air
  update ID that travels with the version ([ADR-081]) is not kept, because [NFR-93] doesn't name it.
- **A version that doesn't parse leaves the pair out.** A call whose `X-App-Version` doesn't parse
  is recorded with `app` empty, so its `X-App` isn't kept either. The call is still audited: a
  sign-in attempt from such a client leaves its record like any other.
- **The operation is an ASCII identifier.** `OperationName` is the `client-api` operation ID of the
  call, such as `publishPlan` or `signIn`, or the name of a server step: an ASCII letter followed by
  letters, digits, dots, underscores or hyphens, at most 100 characters. That admits every operation
  ID style a later contract may adopt, such as `publish-plan`, so a contract change can't make every
  audit write refuse. Anything else is refused with `MalformedOperationName`. No enum lists the
  operations, because `client-api` declares none yet.
- **A named record is a kernel ID with its kind.** `NamedRecord` pairs a `RecordKind`, one constant
  per typed ID of the shared kernel, with its UUID. Its `of` factories, one per kernel ID, keep the
  kind matched to the ID. Its canonical constructor is for the persistence adapter, which rebuilds a
  stored record, and it doesn't check that the kind matches the ID. A driver shares its account's
  ID, so it is named as an account. The records are kept in the order given, and a record may name
  none.
- **An error code is upper snake case ASCII,** words of capitals and digits joined by single
  underscores, as `DomainError.code()` derives them; anything else is refused with
  `MalformedErrorCode`.
- **The trace ID is a W3C trace-id:** 32 lower-case hex digits, not all zero. Anything else is
  refused with `InvalidTraceId`. It is required, as [NFR-93] states it without "when known".
- **A record exactly a year old is kept.** [ADR-075] deletes records "older than 1 year", so
  `isPastRetention` is true only after the same local time a year later. A record of 29 February is
  kept until 28 February of the next year.

## Consequences

- **Easy:** a refused sign-in or a stale edit leaves a trace an Admin can filter by code ([FR-126]).
- **Easy:** no audit record can hold a name, a phone, an address or a token: the types allow only
  IDs, codes, enums, numbers and instants ([NFR-94]).
- **Easy:** a test of the purge function can compare its cutoff with `AuditRecord.RETENTION`.
- **Hard:** a refused attempt needs a second transaction, so the audit record outlives the refusal's
  rollback. The application layer builds it.
- **Hard:** a call whose client declares a malformed version, or a pre-release such as
  `1.4.2-beta.1`, is recorded with no app and no version.
- **Cost:** none beyond the module's value types.
- **Risk:** a refused call whose second write fails leaves no record of the refusal. The application
  layer must make that failure loud.

## Alternatives considered

- **Audit only successful changes.** Rejected: [NFR-23] audits every sign-in, failed ones included,
  and a failed sign-in is the attempt an Admin most needs to see.
- **Keep the refusal's whole error, with its parameters.** Rejected: an error's parameters are the
  response's, and nothing needs them in the record. The code names the refusal.
- **Keep the app version as the text the client sent.** Rejected: any text a client sends could carry
  personal data into a record ([NFR-94]).
- **An enum of every audited operation.** Rejected for now: `client-api` declares no operation, and
  each module's milestone adds its own.
- **Named records as bare UUIDs.** Rejected: an Admin would see an ID with no kind, and a caller could
  pass any UUID.
- **Leave the year only in the purge function.** Rejected: each module owns its retention periods,
  and the code would have no single place that says how long a record is kept.

## Revisit when

- The team ships pre-release or build-tagged app versions to drivers, and the audit must show them.
- `client-api` gains its operations, and a closed list would catch a mistyped operation name.
- A module must audit a record that no kernel ID names, such as a credential link or a held report.
- Legal advice ([Q-42]) changes the retention period.

[ADR-041]: ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md
[ADR-075]: ADR-075-retention-deletes-records-and-anonymizes-people-in-place.md
[ADR-081]: ADR-081-eas-update-for-javascript-only-fixes-during-the-pilot.md
[ADR-082]: ADR-082-supported-phone-app-versions.md
[ADR-088]: ADR-088-israel-local-time-for-every-date-and-schedule.md
[DL-48]: ../architecture/domain_layer/domain_layer_design.md#48-audit
[FR-126]: ../requirements/requirements.md#company-users-and-roles
[FR-292]: ../requirements/requirements.md#company-users-and-roles
[NFR-20]: ../requirements/requirements.md#nfr-20
[NFR-23]: ../requirements/requirements.md#nfr-23
[NFR-93]: ../requirements/requirements.md#nfr-93
[NFR-94]: ../requirements/requirements.md#nfr-94
[NFR-258]: ../requirements/requirements.md#nfr-258
[Q-42]: ../requirements/requirements.md#open-questions
[TS-5]: ../architecture/tech_stack.md#5-development-environment-and-workflow
[TS-16]: ../architecture/tech_stack.md#16-observability-and-alerting
