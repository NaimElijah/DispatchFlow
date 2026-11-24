# ADR-045: Generation counters on the user and the session revoke tokens

- **Status:** Accepted; extended by ADR-131; amended by ADR-104
- **Date:** 2026-11-13

## Context

[ADR-036], carrying forward [ADR-030], decided that "the server refuses an access token issued
before its user's last revocation" ([NFR-177]) and that an open event stream ends with it
([NFR-188]). Its step-up rule says giving the password "issues the session new tokens" ([NFR-227]),
without saying how a session's older tokens are refused. Its cost is "one lookup on every API
call". ADR-030 set a revisit trigger on that lookup showing up in [NFR-59]'s measurements.

None of this is code. The client contract says "No operation requires authentication yet"
(`contracts/client-api/v1/openapi.yaml`), so there is nothing to migrate.

The requirements the mechanism serves:

- [NFR-177]: refuse a revoked user's older access token, while "the user's other sessions refresh
  and carry on";
- [NFR-89]: a sign-out revokes the session it ends;
- [NFR-90]: a role, password or email change revokes every session of the user;
- [NFR-214]: Admin-only operations need the password given in the last 5 minutes, and [NFR-227]
  issues the session new tokens then and refuses the old ones.

The proposed design finds two problems with a revocation time ([PL §16], [DL §16]):

- **A JWT's `iat` has one-second resolution.** Against a millisecond revocation time, a token
  issued later in the same second as the revocation reads as older and is refused. Rounding the
  other way accepts a token issued just before it. An end-to-end test that changes a role and signs
  in again "would fail at random".
- **A revocation should be one write and an in-memory check.** The plan wants "refused at once"
  without a database read per request, and without writing every session when a user is revoked.

## Decision

We replace ADR-036's refusal of "an access token issued before its user's last revocation" with
two counters that only grow: one on `UserAccount`, one on `Session` ([DL §3.3], the `Generation`
value; [DL §4.1]).

**What changes in [ADR-036]:** its rule that "the server refuses an access token issued before its
user's last revocation" ([NFR-177]), and with it the unstated way a step-up refuses a session's
older tokens. Everything else in ADR-036 stands.

- **The access token** is a JWT signed with ES256, valid for 15 minutes ([NFR-86], [TS §6], the
  *Authentication* row). Its claims include `sub`, `sid`, `roles`, `ugen` (the user's
  `token_generation`) and `sgen` (the session's `generation`) ([PL §16.1]).
- **Every request checks** the signature and expiry, then `ugen == user.token_generation`,
  `sgen == session.generation`, and that the session is not revoked ([PL §16.1]).
  - The values come from a Caffeine cache.
  - The cache loads through its atomic loader, `get(key, loader)`, never `getIfPresent` followed
    by `put`. A load in flight can then not put an evicted value back into the cache.
  - The use case that changes them evicts the user's entry right after its commit, in the same
    thread, so the next request sees the change. Entries also expire after 60 seconds, as a
    backstop.
  - The `SessionsRevoked` event closes open streams ([NFR-188]).
- **The user counter** goes up on a role, password or email change, a deactivation, or the loss of
  the last role ([PL §16.1], [DL §4.1]). That one write refuses every older token and session
  ([NFR-90], [NFR-177]).
  - **Admin ends every session of a user** ([FR-187]: an Admin can end every session of a user
    without deactivating them; [FR-188]: an access token issued before a session is ended is
    refused at once). [PL §16.1] cites FR-187 among the revocation triggers without naming the
    counter. This ADR reads it as one increment of the user counter, which ends that user's open
    streams through the event ([NFR-188]). The owner confirmed this reading on 2026-11-14.
- **The session counter** goes up when the session confirms its password ([NFR-214], [NFR-227]).
  That refuses the session's older tokens and leaves the user's other sessions alone.
- **A sign-out** revokes its own session ([NFR-89]), which the check above reads from the session's
  state. Per [DL §4.1] it raises no event.
  - **An open event stream re-checks its session at each heartbeat, every 15 seconds, and closes
    once the session is revoked** ([NFR-267]). The owner decided this on 2026-11-14. So the streams
    of a signed-out session end at the next heartbeat, though a sign-out raises no event and only
    `SessionsRevoked` closes streams at once ([NFR-188]).
- **Refresh tokens** are opaque, rotating and stored as hashes ([TS §6]): 256 random bits, hashed
  with SHA-256, valid 14 days ([PL §16.1]). A session is live if it is not revoked and its
  `user_generation` equals the user's `token_generation`. A user's increment therefore ends every
  session without writing each one.
- **Unchanged from ADR-036:** the 30-second refresh grace and reuse revocation ([NFR-178],
  [NFR-88]), the 5-minute step-up window, and the rate limits.
- **A restore rotates the access-token signing key** ([NFR-272]; the owner, 2026-11-14). A restored
  database holds the counters of its snapshot, so a token issued after the snapshot, or one revoked
  after it, could read as valid. The new key refuses every older access token, and the restore
  revokes every session ([NFR-252]). No counter is moved: the key and the revoked sessions already
  cover it. The old signing key leaves the accepted set at once, with no two-key overlap
  ([ADR-069] allows the overlap only for an ordinary rotation).
  - **Changed credentials are treated as well** ([NFR-253]). For each account whose password or
    email changed since the snapshot, found in the logs by account ID, the restore blanks the
    password and voids the account's links. The records that re-apply these changes hold only IDs and
    are kept off the VM as each change happens. The mechanism is M13's.
  - **The restore step itself issues set-password links to the Admin accounts,** at addresses the
    company confirms, as an audited [FR-393] step. The Admins then confirm each account's state and
    roles with the company before any other set-password link goes out.
  - **Every open credential link is voided.** If the snapshot is older than the logs reach, every
    account gets the treatment above.
- **A test pins the same-second case:** change a user's role and sign in again within the same
  second. The new token works and the old one does not ([PL §24]).
- **Adoption is M6, in Semester B**, with sessions in full ([PL §3.3], [DL §4.1]).

## Consequences

- **Easy:** a revoked token is refused at once, with an integer comparison that has no
  same-second edge, so the end-to-end test is deterministic.
- **Easy:** revoking all of a user's sessions is one write.
- **Easy:** a step-up refuses one session's older tokens and no other session's.
- **Hard:** every use case that changes a counter must also evict the user's cache entry after its
  commit, in the same thread. A missed eviction leaves the old values for up to 60 seconds.
- **Hard:** [NFR-177] is worded in time ("issued before its user's last revocation"). Under
  counters it means "issued under an older generation". This ADR leaves the row's wording to the
  team.
- **Hard:** a restore has more steps in the runbook: rotate the key, and treat the accounts changed
  since the snapshot. Skipping either leaves older tokens, or a password an attacker set, valid.
- **Cost:** two more claims in every access token, and a cache of generations in the core's
  process. ADR-036's per-call lookup becomes a cache read.
- **Cost:** Caffeine is new to the build if the pom does not have it. `src/backend/core/pom.xml`
  declares no Caffeine today (checked with a search). Record its licence and maintenance status
  before adding it, as `.claude/CLAUDE.md` requires of every new dependency. The sources give
  neither.
- **Risk:** the cache is per process, and its eviction happens in the thread that commits. That is
  right for one VM ([NFR-14]). With a second core instance, only the 60-second expiry would bound a
  revoked token, and "at once" would not hold.
- **Risk:** the counters decide revocation alone. A bug that fails to increment one refuses
  nothing, and the token stays valid until it expires.

## Alternatives considered

- **ADR-036's revocation time compared with `iat`.** Rejected: one-second resolution makes the
  comparison wrong within the second of a revocation ([PL §16], [DL §16]).
- **Rounding the comparison the other way.** Rejected: it would accept a token issued just before
  the revocation ([PL §16]).
- **Writing every session when a user is revoked.** Rejected: one counter on the user does the
  same in one write ([PL §16]).
- **A database read on every request.** Rejected: the cache and its eviction give the same answer
  without one ([PL §16]).
- **Accepting a 15-minute access token after revocation.** Still rejected, as in ADR-036: the
  self-check would fail ASVS 7.4.1 and 7.4.2 ([NFR-40]).

## Revisit when

- A second core instance is planned ([NFR-14] changes). Replace the in-process cache with a shared
  check, or accept a bound longer than "at once".
- A revoked token is ever accepted for longer than one request in a test or an incident. The
  eviction path is wrong, and the 60-second expiry is doing its work.
- The self-check moves to ASVS Level 2, as ADR-036 says. Review the check on every request again.

[ADR-030]: ADR-030-admin-issued-credentials-and-token-revocation.md
[ADR-036]: ADR-036-one-account-per-person-signed-in-by-email.md
[DL §3.3]: ../architecture/domain_layer/domain_layer_design.md#33-value-objects
[DL §4.1]: ../architecture/domain_layer/domain_layer_design.md#41-access
[DL §16]: ../architecture/domain_layer/domain_layer_design.md#16-why-it-looks-like-this
[NFR-14]: ../requirements/requirements.md#nfr-14
[NFR-40]: ../requirements/requirements.md#nfr-40
[NFR-59]: ../requirements/requirements.md#nfr-59
[NFR-86]: ../requirements/requirements.md#nfr-86
[NFR-88]: ../requirements/requirements.md#nfr-88
[NFR-89]: ../requirements/requirements.md#nfr-89
[NFR-90]: ../requirements/requirements.md#nfr-90
[FR-187]: ../requirements/requirements.md#company-users-and-roles
[FR-188]: ../requirements/requirements.md#company-users-and-roles
[NFR-177]: ../requirements/requirements.md#nfr-177
[NFR-252]: ../requirements/requirements.md#nfr-252
[NFR-253]: ../requirements/requirements.md#nfr-253
[NFR-272]: ../requirements/requirements.md#nfr-272
[NFR-267]: ../requirements/requirements.md#nfr-267
[NFR-178]: ../requirements/requirements.md#nfr-178
[NFR-188]: ../requirements/requirements.md#nfr-188
[NFR-214]: ../requirements/requirements.md#nfr-214
[NFR-227]: ../requirements/requirements.md#nfr-227
[PL §3.3]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#33-semester-b-track
[PL §16]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#16-access-and-security
[PL §16.1]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#161-tokens
[PL §24]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#24-testing
[TS §6]: ../architecture/tech_stack.md#6-core-service-java
[FR-393]: ../requirements/requirements.md#company-users-and-roles
[ADR-069]: ADR-069-sign-in-and-transport-security.md
