# ADR-116: Only an Admin changes their own email

- **Status:** Accepted
- **Date:** 2026-11-15

## Context

[FR-387] lets an Admin change any user's email, and a user change their own by giving their
current password. [ADR-104] point 2 makes changing another user's email an Admin-only act that
needs a fresh password ([NFR-214]), because it is a path to taking over that account through
recovery ([FR-386]). It names a second Admin's account among those at risk, and relies on step-up
alone to protect it.

A review of the Access domain found that step-up is not enough. Admin A gives their own password,
points Admin B's email at a mailbox A controls, confirms the link sent there ([FR-388]), and asks
for B's recovery link. A then holds both of the Admins that [BR-19] requires, and B is locked out.
Nothing in the domain stopped it: the email-change link did not record who asked for it, so the
account could not tell B's own request from A's.

The same review found smaller defects in the same aggregates, which this record's own choices
below settle:

- **The retention of a revoked session.** A session that its account's revocation ended
  ([NFR-90], [NFR-177]) was dated by its current token's expiry, so it was kept up to 44 days
  instead of 30 after it ended ([NFR-258]).
- **An account with no password yet.** `Session.open` trusted the sign-in adapter to refuse an
  account whose set-password link was never used ([FR-294]).

## Decision

On 2026-11-15 the owner, Naim Elijah, decided:

1. **Only an Admin changes their own email.** No account changes the email of an account that holds
   the Admin role, unless it is that account. The user's own change, with their current password,
   stays as it is. [FR-387] is edited to add "except another Admin's, which only that Admin can
   change". **This amends [ADR-104] point 2:** the step-up stands for changing a non-Admin's
   email, and no longer stands alone for an Admin's. The rest of ADR-104 is unchanged.
   - The refusal is a new error, `EmailOfAnotherAdmin` ([NFR-144]: a code the apps translate).
   - It applies whatever other roles the account holds, because the Admin role is what recovery
     would hand over.

Some choices are this record's own, not the owner's:

- **The email-change link records who asked, and the rule is checked twice.**
  `LinkPurpose.EmailChange` holds the address and the `AccountId` that asked. `issueLink` refuses
  another account's request for an Admin, and `confirmEmailByLink` refuses it again when the link is
  used. The second check closes a path around the first: A asks to change a Dispatcher's email,
  makes that Dispatcher an Admin within the hour the link works ([FR-388]), then confirms the link.
  A role change revokes sessions ([NFR-90]) but voids no link, so without the second check the
  change would take effect.
- **A session its account's revocation ended is dated by that revocation.**
  `Session.revokeWithAccount(generation, at)` revokes a live session begun under an older account
  generation, at the time `SessionsRevoked` carries. Access's own listener on that event calls it,
  and a redelivered event changes nothing, because a revoked session keeps its first revocation and
  a session begun under the new generation is not ended. The token check still refuses the
  session's tokens at once, whether or not the listener has run ([NFR-177]).
- **An account with no password is refused at sign-in by the domain,** with a new error,
  `PasswordNotSet`. The sign-in endpoint answers it as it answers a wrong password, after the same
  hash work, as it already answers a deactivated or role-less account ([NFR-145]). No existing
  error fits: each names a different reason, and the use case's own decision needs the reason.

## Consequences

- **Easy:** one Admin can't take over another's account through email and recovery, so [BR-19]'s
  two Admins stay two people.
- **Easy:** the rule lives in the aggregate, at both points where an address can change, so no use
  case can skip it.
- **Easy:** a session is deleted 30 days after it ended, however it ended ([NFR-258]).
- **Hard:** an Admin who has lost access to their own mailbox can't be helped by another Admin.
  They recover through the written-request server step ([FR-346], [FR-292]), which takes longer.
- **Hard:** a stored email-change link holds one more field, the account that asked, which the
  adapter stores and rebuilds.
- **Cost:** Access needs a listener on its own `SessionsRevoked` event, which updates every live
  session of the account, when the application layer arrives.
- **Risk:** until that listener has run, the retention job still dates such a session by its
  token's expiry, so a failed listener keeps it up to 14 days longer.

## Alternatives considered

- **Keep step-up alone, as ADR-104 had it.** Not chosen: one Admin's own password is all the attack
  needs.
- **Require the target Admin's current password as well.** Not chosen: it is the same rule with a
  second credential flowing through another person's session, and the target can simply change
  the address themselves.
- **Require a second Admin to approve the change.** Not chosen: with two Admins the approver is the
  target, which is the owner's rule with more steps, and there is no approval workflow to build it
  on.
- **Void every open email-change link when an account gains the Admin role.** This closes the
  same path around the first check, and needs no new field. Not chosen: it adds a voiding rule to
  [NFR-224]'s list, which the owner did not decide, while recording who asked keeps the rule in one
  predicate checked where an address takes effect.
- **Date a session by its account's revocation through `endedAt(generation, at)`.** Not chosen: the
  account doesn't keep the time of each revocation, so the retention job would have nothing to pass.

## Revisit when

- The company has more than two Admins and wants a second Admin to be able to repair a colleague's
  address without the written-request step.
- An account gains a role other than Admin that recovery could hand over to another person.
- Session retention moves to the database, where a revocation could date sessions in one statement.

[ADR-104]: ADR-104-the-company-profile-holds-customer-defaults-and-capabilities-follow-the-role-matrix.md
[BR-19]: ../requirements/requirements.md#access
[FR-292]: ../requirements/requirements.md#company-users-and-roles
[FR-294]: ../requirements/requirements.md#company-users-and-roles
[FR-346]: ../requirements/requirements.md#company-users-and-roles
[FR-386]: ../requirements/requirements.md#company-users-and-roles
[FR-387]: ../requirements/requirements.md#company-users-and-roles
[FR-388]: ../requirements/requirements.md#company-users-and-roles
[NFR-90]: ../requirements/requirements.md#nfr-90
[NFR-144]: ../requirements/requirements.md#nfr-144
[NFR-145]: ../requirements/requirements.md#nfr-145
[NFR-177]: ../requirements/requirements.md#nfr-177
[NFR-214]: ../requirements/requirements.md#nfr-214
[NFR-224]: ../requirements/requirements.md#nfr-224
[NFR-258]: ../requirements/requirements.md#nfr-258
