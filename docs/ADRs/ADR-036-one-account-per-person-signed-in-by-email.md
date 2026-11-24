# ADR-036: Each person has one account, signed in with their email, holding one or more roles

- **Status:** Accepted; extended by ADR-069; amended by ADR-045
- **Date:** 2026-11-11

## Context

[ADR-030] gave drivers a username and Admin-issued passwords, because drivers may have no email
address, and gave Admins and Dispatchers recovery by email. The requirements gave a person who both
dispatches and drives two accounts, one per role.

On 2026-11-11 the requirements were aligned with the [ARD]. Its FR-52 says a new
user "will receive first credentials from an Admin, and any user will be able to recover a forgotten
password through an emailed link". It was also decided that each person has one account, which can hold
several roles.

[ADR-031] notes that drivers' sign-in "stays as ADR-030 decided", because [FR-200] keeps a driver's
phone out of recovery. That note no longer holds. [FR-200] still keeps the phone's number out of
recovery. A mailbox, even one read on the same phone, is a different channel: it has credentials of
its own.

A security review of that change, the same day, found two new exposures. Email becomes every user's
only sign-in identifier and recovery channel, and a phone may now carry the Admin role. The review's
findings were answered the same day. A self-check against Level 1 of OWASP ASVS 5.0 must pass before
the pilot handles real data ([NFR-40]). With one company, no platform operator can rescue a lost
Admin ([ADR-024]).

## Decision

- **One account per person,** signed in with their own email ([FR-296]). No two accounts share an
  email, compared without regard to case or surrounding spaces ([BR-45]).
- **Several roles per account:** Admin, Dispatcher and Driver, in any combination ([FR-02],
  [FR-293]).
  - The account may do everything any of its roles allows, and the server checks it on every call
    ([FR-03], [NFR-85]). That includes recording or correcting an outcome on the account's own
    route, an accepted risk below.
  - The Driver role's own limit stays as [BR-02] says. Through it the server sends published routes
    only, and the event stream is filtered the same way ([BR-41], [NFR-85]).
  - An account with no role cannot sign in ([BR-123]).
  - Retiring a driver removes only the Driver role ([FR-307]). It is the one role change a
    Dispatcher may make ([FR-56]).
  - A change to an account's roles, password or email revokes its sessions ([NFR-90]).
- **First credentials:** a new user gets a set-password link at their email ([FR-294]). An Admin
  creating the account sends it, which is how this ADR reads the ARD's "from an Admin".
  - The link works once, expires 72 hours after it is sent, and allows nothing except setting a
    password.
  - It proves the address, and no Admin knows anyone's password.
  - It replaces [ADR-030]'s Admin-issued first password.
- **Recovery for everyone:** every user, drivers included, recovers a forgotten password through an
  emailed link that works once and expires within an hour ([FR-386], [FR-345]). FR-295, which gave
  drivers usernames and Admin-issued resets, is withdrawn.
- **The links themselves,** for recovery, a first password or a new email:
  - each carries a random token of at least 128 bits, stored only as a hash and never logged
    ([NFR-211]);
  - the token sits in the URL fragment, and the page removes it as it loads ([NFR-212]); the email
    has link tracking off ([NFR-223]);
  - only using the link spends the token, a newer link of a kind voids older ones, and a change of
    email or password voids every unspent link ([NFR-224]);
  - the link opens the web app, or the phone app through verified app links only ([NFR-213]);
  - every link and notice goes to the address stored on the account, never the one typed, and
    addresses are compared folding ASCII letters only ([BR-125], [BR-45]).
- **Changing an email:** an Admin can change anyone's, and a user can change their own by giving
  their password ([FR-387]).
  - The new address takes effect only once a link sent to it is used within 1 hour, and the old
    address is told without being shown the new one ([FR-388]).
  - Sessions end when it takes effect, and an address another account holds is refused only then
    ([FR-392]).
- **Admin operations:**
  - the server refuses an operation that only Admins may perform unless the session gave the
    current password in the last 5 minutes, and both apps ask for it ([NFR-214]);
  - giving it issues the session new tokens ([NFR-227]), and a wrong one counts toward the sign-in
    limits ([FR-177]);
  - every active Admin is emailed when an account gains the Admin role, an Admin account is
    reactivated, or an Admin's password or email changes ([FR-389]);
  - an Admin can end every session of a user ([FR-187]).
- **Abuse of sign-in and recovery:**
  - sign-in and recovery answer the same whether or not an address exists, and a deactivated or
    role-less account is answered as a wrong password is ([NFR-145]);
  - an unknown address runs the same password-hash work, and the limits count typed addresses
    whether or not an account holds them ([NFR-225]);
  - past 10 failures on one address, from any source, one attempt is checked every 5 seconds, and
    no account is ever locked ([NFR-216], [NFR-226]);
  - recovery emails stop at a daily cap, and the team is alerted ([NFR-215]).
- **Audit:** an audit record names the session when it is known, and the app as the client declares
  it ([NFR-93]).
- **Kept from [ADR-030]:**
  - the server refuses an access token issued before its user's last revocation ([NFR-177]), and an
    open event stream ends with it ([NFR-188]);
  - a refresh token presented again within 30 seconds gets a new token, and anything later revokes
    the session ([NFR-178], [NFR-88]);
  - failed sign-ins are limited per account and source address ([NFR-35]);
  - the company keeps two active Admins, counting accounts that hold the Admin role ([BR-19]);
  - an audited server step creates or deactivates Admins on a written request ([FR-292], [FR-346]);
  - there is no second sign-in factor for the pilot ([Q-81]).
- **Tests** check each combination of two roles, and all three, against exactly the union of their
  capabilities ([NFR-210]).

## Consequences

- **Easy:** one sign-in per person, and no switching accounts to change role.
- **Easy:** no Admin is needed when a driver forgets a password, and no Admin ever knows a user's
  password.
- **Hard:** the company must collect an email address for every driver before they can sign in.
- **Hard:** the server checks a set of roles rather than one, everywhere it checks a role.
- **Hard:** an Admin types the password again for Admin operations, at most every 5 minutes, on a
  phone as on a desk.
- **Cost:** one lookup on every API call, as [ADR-030] found. The email sender now carries every
  set-password link, recovery link and Admin alert, and the data inventory names it ([NFR-217]).
- **Risk, accepted on 2026-11-11:** an account that holds Driver with Dispatcher or
  Admin can record *delivered* on a stop of its own route without a proof ([FR-73]). It can also
  correct its own outcomes ([FR-333]). A driver alone is refused both, by [FR-367] and [BR-18]. The
  audit records name who recorded each outcome ([NFR-23], [NFR-93]).
- **Risk:** an account with the Admin role, used on a phone in a van, carries Admin power on the
  road.
  - The controls are re-authentication ([NFR-214]), the alerts to every Admin ([FR-389]), ending
    sessions ([FR-187]) and revocation ([NFR-177]).
  - What a phone may keep for the Admin and Dispatcher roles is open ([Q-98]). [NFR-179] wipes
    only a driver's route data.
- **Risk:** a phone that holds the user's mailbox is also a recovery channel for that account.
- **Risk:** a thief who replays a refresh token within 30 seconds holds the session until the real
  device presents its replaced token, as [ADR-030] noted. That session may now hold the Admin role
  on a phone.
- **Risk:** the per-account slowdown ([NFR-216]) does not stop credential stuffing, which tries each
  leaked password once per account.
- **Risk:** anyone who knows about 20 users' addresses can use up the day's recovery emails within
  an hour ([NFR-215], [FR-172]), and the uniform answer ([NFR-145]) hides from users that nothing
  was sent. The team's alert is the control.
- **Risk:** an attacker can hold an address's one attempt every 5 seconds ([NFR-216]), which slows
  the real user's sign-in too.
- **Risk:** an Admin can take over a non-Admin account by changing its email and then recovering
  it ([FR-387], [FR-386]). Only the old address is told, because [FR-389] covers Admin accounts
  only.
- **Risk:** a Dispatcher who retires the driver record of an Admin who also drives ends that Admin's
  sessions ([FR-307], [NFR-90]).
- **Risk:** a small company may struggle to keep two Admins, and a compromised Admin has no second
  factor behind the password.
- **Risk:** two people cannot share one email address, because each address signs in one account.

## Alternatives considered

- **Two accounts for a person with two roles,** one role each, as the requirements had it. Rejected:
  switching accounts to change role.
- **Usernames for drivers, with resets issued by an Admin,** as [ADR-030] decided. Rejected: the ARD
  lets every user recover by email.
- **An optional email,** with Admin-issued resets for drivers who have none. Rejected: not every
  user could recover by email.
- **An Admin-issued first password,** as [ADR-030] decided. Rejected: the Admin knows
  it, and nothing proves the address.
- **Refusing outcomes on an account's own route,** so another Dispatcher or Admin records them.
  Rejected: the union of roles stays whole.
- **An app lock or a second factor for accounts holding the Admin role.** Not chosen for the pilot;
  [Q-81] keeps the second factor out.
- **Still rejected, as [ADR-030] found:**
  - SMS codes for drivers: a driver's phone is never a recovery channel ([FR-200]);
  - accepting a 15-minute access token after revocation: the self-check would fail ASVS 7.4.1 and
    7.4.2;
  - no refresh grace: drivers on weak signal would be signed out mid-route.

## Revisit when

- Two or more of the pilot's drivers have no email address. Bring back an Admin-issued reset as a
  fallback.
- A recipient or the company disputes a *delivered* that the route's own account recorded without
  a proof, as the audit record shows ([NFR-93]). Refuse such outcomes.
- The recovery cap is reached on a day of the pilot ([NFR-215]). Count requests per source more
  tightly, or raise the cap.
- A phone holding the Admin role is lost or stolen. Restrict Admin operations in the phone app, or
  add an app lock.
- The self-check moves to ASVS Level 2, or the per-call lookup shows up in [NFR-59]'s measurements,
  as [ADR-030] says.

[ADR-024]: ADR-024-one-delivery-company-no-multi-tenancy.md
[ADR-030]: ADR-030-admin-issued-credentials-and-token-revocation.md
[ADR-031]: ADR-031-sms-through-an-israeli-provider-for-recipient-messages.md
[ARD]: ../requirements/ARD/DispatchFlow_ARD.pdf
[BR-02]: ../requirements/requirements.md#access
[BR-18]: ../requirements/requirements.md#driver-reports-and-outcomes
[BR-19]: ../requirements/requirements.md#access
[BR-41]: ../requirements/requirements.md#access
[BR-45]: ../requirements/requirements.md#access
[BR-123]: ../requirements/requirements.md#access
[BR-125]: ../requirements/requirements.md#access
[FR-02]: ../requirements/requirements.md#company-users-and-roles
[FR-03]: ../requirements/requirements.md#company-users-and-roles
[FR-56]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-73]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-172]: ../requirements/requirements.md#company-users-and-roles
[FR-177]: ../requirements/requirements.md#company-users-and-roles
[FR-187]: ../requirements/requirements.md#company-users-and-roles
[FR-200]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-292]: ../requirements/requirements.md#company-users-and-roles
[FR-293]: ../requirements/requirements.md#company-users-and-roles
[FR-294]: ../requirements/requirements.md#company-users-and-roles
[FR-296]: ../requirements/requirements.md#company-users-and-roles
[FR-307]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-333]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-345]: ../requirements/requirements.md#company-users-and-roles
[FR-346]: ../requirements/requirements.md#company-users-and-roles
[FR-367]: ../requirements/requirements.md#publishing-and-the-phone-app
[FR-386]: ../requirements/requirements.md#company-users-and-roles
[FR-387]: ../requirements/requirements.md#company-users-and-roles
[FR-388]: ../requirements/requirements.md#company-users-and-roles
[FR-389]: ../requirements/requirements.md#company-users-and-roles
[FR-392]: ../requirements/requirements.md#company-users-and-roles
[NFR-23]: ../requirements/requirements.md#nfr-23
[NFR-35]: ../requirements/requirements.md#nfr-35
[NFR-40]: ../requirements/requirements.md#nfr-40
[NFR-59]: ../requirements/requirements.md#nfr-59
[NFR-85]: ../requirements/requirements.md#nfr-85
[NFR-88]: ../requirements/requirements.md#nfr-88
[NFR-90]: ../requirements/requirements.md#nfr-90
[NFR-93]: ../requirements/requirements.md#nfr-93
[NFR-145]: ../requirements/requirements.md#nfr-145
[NFR-177]: ../requirements/requirements.md#nfr-177
[NFR-178]: ../requirements/requirements.md#nfr-178
[NFR-179]: ../requirements/requirements.md#nfr-179
[NFR-188]: ../requirements/requirements.md#nfr-188
[NFR-210]: ../requirements/requirements.md#nfr-210
[NFR-211]: ../requirements/requirements.md#nfr-211
[NFR-212]: ../requirements/requirements.md#nfr-212
[NFR-213]: ../requirements/requirements.md#nfr-213
[NFR-214]: ../requirements/requirements.md#nfr-214
[NFR-215]: ../requirements/requirements.md#nfr-215
[NFR-216]: ../requirements/requirements.md#nfr-216
[NFR-217]: ../requirements/requirements.md#nfr-217
[NFR-223]: ../requirements/requirements.md#nfr-223
[NFR-224]: ../requirements/requirements.md#nfr-224
[NFR-225]: ../requirements/requirements.md#nfr-225
[NFR-226]: ../requirements/requirements.md#nfr-226
[NFR-227]: ../requirements/requirements.md#nfr-227
[Q-81]: ../requirements/requirements.md#open-questions
[Q-98]: ../requirements/requirements.md#open-questions
