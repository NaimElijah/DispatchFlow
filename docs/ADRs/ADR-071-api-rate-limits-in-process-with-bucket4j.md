# ADR-071: API rate limits run in the core's process, with Bucket4j

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

On 2026-11-13 the owner, Naim Elijah, confirmed the rate-limit rows for the team: [NFR-53] as a
Should and [NFR-102] as a Must. This ADR settles the marker "ADR pending: API rate limiting", and
names how every limit in the requirements is counted.

The limits the requirements already set:

| Limit | Counted by | Row |
|---|---|---|
| 10 failed sign-ins in a rolling 15 minutes | typed email and source address | [NFR-35] |
| Past 10 failures on one typed address from any source, one attempt every 5 seconds | typed email | [NFR-216], [NFR-226] |
| 120 calls a minute | user | [NFR-53] |
| 20 calls a minute without a token | source address | [NFR-102] |
| 600 calls a minute with the SMS provider's signature or secret, in all | all such calls | [NFR-200] |

A request over a limit is refused with when to retry ([NFR-102]). No limit ever locks an account
([NFR-226]).

The forces:

- **The system runs on one VM with no redundancy** ([NFR-14]), so the core is one instance.
- **The sign-in limit counts the typed email,** whether or not an account holds it ([NFR-225]).
  A proxy never sees that value.
- **Every action is one HTTP call,** so a limit applies per call ([ADR-026]).
- **Every new service is a cost** on four people ([TS §1], rule 1).

## Decision

The core counts every limit above with **Bucket4j, in process** ([TS §6], *Rate limits* row).

- **One library, every limit.** Each row of the table is a bucket keyed as the table says. A success
  resets the sign-in counts ([PL §16.3][PL-163]).
- **The answer is `429` with `Retry-After`** ([PL §16.3][PL-163]). The phone app waits that long
  without counting an attempt ([PL §19][PL-19]).
- **Calls without a token** are sign-in, refresh, sign-out, health checks, and the calls of
  [FR-52] and [FR-263] ([NFR-01]). [NFR-102] limits them by source address.
- **Priorities as confirmed:** [NFR-102] and the sign-in limits are Must. [NFR-53] is a Should.
- **Adoption:** Semester B ([TS §6]).

Nothing is built.

## Consequences

- **Easy:** no new service, no shared store, and the typed email is visible where it is counted.
- **Easy:** one library and one `429` shape for every limit, which the contract can describe once
  ([ADR-043]).
- **Hard:** counters live in the core's memory, so they reset when the core restarts. This follows from
  "in process". The sources do not mention it.
- **Hard:** behind Caddy, the core sees the proxy's address unless it reads the client's address from
  what Caddy forwards. The sources do not say how it does. A wrong answer would put every client in
  one bucket.
- **Cost:** a library to add. The sources give no licence or maintenance status for it. Record both
  against the licence allow-list ([ADR-074]) before adding it, as `.claude/CLAUDE.md` requires.
- **Risk:** [NFR-102] counts refresh and sign-in by source address. Drivers who start together behind
  one depot address could come near 20 calls a minute. [NFR-10]'s load mix has 15 phones and 5 web
  sessions. The pilot's sign-in behaviour is not measured.
- **Risk:** buckets keyed by typed email can be created at will by a caller. The sources do not say
  how an entry ages out. Unbounded growth is possible until that is decided.
- **Risk:** the per-address slowdown does not stop credential stuffing, as [ADR-036] found.

## Alternatives considered

- **Caddy rate limiting.** Lost on needing a custom Caddy build, and on not knowing the typed email
  ([TS §6], *Over*).
- **Redis-backed limits.** Lost on being another service ([TS §6], *Over*), against one VM and one
  Compose file ([NFR-14], [NFR-16]).

## Revisit when

- The core runs as more than one instance. Counters per process no longer add up, and a shared store
  becomes the cheaper fix.
- A real driver is refused with `429` in the pilot under [NFR-102], more than once on a pilot day.
  Count refresh by user instead of by address, or raise the limit.
- A credential-stuffing attempt shows in the logs. The per-address limits do not stop it, so add
  limits by source across addresses.
- A restart during the pilot lets a counted caller try again, and the logs show it. Consider
  persisting the sign-in counters.

[ADR-026]: ADR-026-server-sent-events-and-push-for-live-updates.md
[ADR-036]: ADR-036-one-account-per-person-signed-in-by-email.md
[ADR-043]: ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-074]: ADR-074-dependency-licences-checked-against-an-allow-list.md
[FR-52]: ../requirements/requirements.md#company-users-and-roles
[FR-263]: ../requirements/requirements.md#live-operations-and-re-optimization
[NFR-01]: ../requirements/requirements.md#nfr-01
[NFR-10]: ../requirements/requirements.md#nfr-10
[NFR-14]: ../requirements/requirements.md#nfr-14
[NFR-16]: ../requirements/requirements.md#nfr-16
[NFR-35]: ../requirements/requirements.md#nfr-35
[NFR-53]: ../requirements/requirements.md#nfr-53
[NFR-102]: ../requirements/requirements.md#nfr-102
[NFR-200]: ../requirements/requirements.md#nfr-200
[NFR-216]: ../requirements/requirements.md#nfr-216
[NFR-225]: ../requirements/requirements.md#nfr-225
[NFR-226]: ../requirements/requirements.md#nfr-226
[PL-163]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#163-limits-and-checks
[PL-19]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#19-phone-app
[TS §1]: ../architecture/tech_stack.md#1-what-decides-close-calls
[TS §6]: ../architecture/tech_stack.md#6-core-service-java
