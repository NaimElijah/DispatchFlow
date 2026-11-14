# ADR-104: The company profile holds a new customer's defaults, and capabilities follow the role matrix

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

The Access domain is built ahead of its milestone, from [DL §4.1][DL-41] and the requirements. Some
of its rules were left open by the design:

- **A new customer's defaults.** [FR-125] puts the time window a new customer starts with on the
  company's settings page, and [FR-184] adds the service duration a new customer starts with, which
  must be positive. DL §4.1 gave `CompanyProfile` only the name and the language. Customers & Orders
  uses both defaults, but its domain may not be imported by Access, nor Access's by it
  ([DL §2.3][DL-23], rule 1).
- **Capability names.** `RoleSet.can(capability)` is the permission matrix under [FR-03]
  ([NFR-85]). [ADR-068] has every operation name one capability, and gives only examples of the
  names (`PLAN_PUBLISH`, `USERS_MANAGE`); no document named them all. Five of the matrix's cells are
  qualified ("Only their own", "Only its name and dispatch phone"), and [ADR-068] puts such data
  limits in the use cases.
- **Retiring a driver** removes the Driver role from the account, which keeps its other roles
  ([FR-307], [FR-56]). Fleet owns the driver and Access the roles, and Fleet may not import Access's
  domain. [FR-02] says "a user with no role is refused with the reason", while [BR-123] expects an
  account to lose its last role.
- **Growth.** A session keeps every refresh token it was ever issued, and an account every link it
  was ever sent, unless something drops them. Both are loaded whole ([DL §3.1][DL-31]).
- **Links that outlive their purpose.** [NFR-224] voided unspent links on a change of email or
  password only. A deactivated account, or one whose sessions an Admin ended ([FR-187]), kept them.
  A set-password link could also be sent to an account that already had a password, as a second
  reset path beside the 1-hour recovery link ([FR-294], [FR-345]).
- **The driver record.** Fleet keys a driver by the account's ID. [NFR-258] anonymizes every
  account a year after deactivation, and Fleet has no way to hear it.

## Decision

On 2026-11-14 the owner, Naim Elijah, decided these:

1. **The company profile holds a new customer's defaults** ([FR-125], [FR-184]).
   - `CompanyProfile` holds a `DefaultWindow` and, once an Admin sets one, a `DefaultServiceTime`
     of whole seconds, positive. Both are Access's own values. Customers & Orders reads them through
     Access's API in a later milestone, and makes its own values from them.
   - The company's name is at most 19 characters, counted in UTF-16 units as an SMS counts them
     ([FR-382]).
2. **`Capability` has one constant per row of the matrix** ([FR-03]). **This amends [ADR-068]:**
   capabilities are named for the matrix's rows, not for operations. The rest of ADR-068 stands,
   and each operation's `x-capability` names one of these constants.
   - Each constant is named for its row, such as `RETIRE_DRIVER` and `CHANGE_COMPANY_SETTINGS`, and
     its Javadoc quotes the row and the FR numbers it cites.
   - Each constant holds one `Grant` per role. A tick is `FULL`, a cell that starts "Only" is
     `LIMITED`, and an empty cell is `NONE`. `RoleSet.can` is true when any role's grant is not
     `NONE`, and `RoleSet.grant` gives the widest, so a combination of roles gets the union of what
     each allows ([FR-293]). The use case checks a limited grant's limit ([ADR-068]).
   - `Capability.isAdminOnly()` is true when the Admin cell is not empty and the other two are.
     These are the operations [NFR-214] gives only to Admins, so the step-up check runs on exactly
     them.
   - `CapabilityMatrixTest` reads the matrix from `requirements.md`. It fails when a row has no
     constant, a constant has no row, or a cell disagrees with a constant, for every role and every
     combination of roles ([NFR-210]).
3. **Access's side of retiring a driver** ([FR-307]). Fleet raises a new `DriverRetired` event.
   - `UserAccount.removeDriverRole(now)` removes the Driver role and keeps the others. It revokes
     every session, as every role change does ([NFR-90]).
   - Fleet's listener on `DriverRetired` calls it through Access's API ([DL §2.3][DL-23], rule 2).
     Delivery is at least once, so the method works in every state of the account, and a repeated
     call changes nothing.
   - The minimum of two active Admins is not checked, because only the Driver role goes ([BR-19]).
   - **[FR-02] covers every role an Admin gives.** `UserAccount.open` and `changeRoles` refuse an
     empty set of roles with `NoRoleGiven`. Retiring is the one way an account loses its last role,
     which [BR-123] expects, and the account then can't sign in.
4. **Tokens and links are pruned.**
   - On `exchange` and `confirmPassword`, a session drops each token that is not current and was
     issued more than 14 days and 30 seconds ago, the token lifetime plus the grace ([NFR-87],
     [NFR-178]). A token is issued no earlier than the token it succeeds, so every kept token's
     successor is kept too.
   - **This narrows [NFR-88]:** replaying a token the chain no longer keeps is refused as unknown,
     and no longer revokes the session.
   - On `issueLink`, an account drops each link that no longer works (spent, voided or expired) and
     was sent more than 72 hours ago, the longest a link works.
5. **A deactivation, and an Admin ending every session ([FR-187]), void every open link.**
   [NFR-224] says so now; the owner allowed that row's edit.
6. **A set-password link is only for an account with no password yet.** `issueLink` refuses it
   otherwise with `PasswordAlreadySet`, and a reset goes through the 1-hour recovery link
   ([FR-294], [FR-345]).
7. **`UserAccount.anonymize` raises `AccountAnonymized`,** naming the account. Fleet's listener
   anonymizes the driver record keyed by it ([NFR-258], [NFR-260]).

One choice is this ADR's own, not the owner's. **`DefaultWindow` is not the kernel's
`TimeWindow`,** because the kernel's value lies on one delivery date and a default belongs to no
date. So Access models the window itself ([DL §2.3][DL-23], rule 1). `DefaultWindow` holds two
wall-clock times and checks that the first is before the second ([BR-35]). Customers & Orders builds
the kernel `TimeWindow` on each order's date, where the date's skipped hour is checked ([BR-49],
[BR-111]).

## Consequences

- **Easy:** Customers & Orders gets both defaults from one owner, and an Admin changes them in one
  place.
- **Easy:** a capability can't drift from the matrix without a red build, and a role combination is
  checked against the union of its roles. The step-up set comes from the same matrix.
- **Easy:** a retired driver's account keeps working in its other roles, and a repeated event does
  no harm.
- **Easy:** a session and an account stay small however long they live.
- **Hard:** a change to the matrix's wording, or a new row, needs a change to `Capability` and to
  the test's row list in the same pull request.
- **Hard:** a limited grant's limit is checked in each use case, never in one place.
- **Hard:** the role-removal API is callable only from Fleet's `DriverRetired` listener, and has no
  role check of its own. Authorization happened on Fleet's `RETIRE_DRIVER` handler. So this is not
  the second entry to a role-gated use case that [ADR-068]'s trigger names.
- **Cost:** two small values in Access for what Customers & Orders models too.
- **Risk:** a thief who replays a refresh token more than 14 days old is refused without the
  session being revoked, so its owner isn't signed out. The token itself had expired.
- **Risk:** a default window set to a time that a clock change skips, such as 02:30, is refused on
  that one date when Customers & Orders builds the order's window.

## Alternatives considered

- **The kernel's `TimeWindow` for the default window.** Not chosen: it needs a date, and any date
  chosen for a default would be arbitrary and could fall on a clock change.
- **A new kernel value for a window of any day.** Not chosen: the kernel changes only with every
  module owner's agreement ([DL §2.3][DL-23], rule 5), and Access alone needs it now.
- **Capabilities named for operations** (`PLAN_PUBLISH`, `USERS_MANAGE`), as [ADR-068]'s examples
  are. Not chosen by the owner: one constant per row keeps the matrix and the code one-to-one.
- **A qualified cell read as no grant.** Rejected: a Driver could then not read their own account
  or change their own email, which the matrix allows.
- **Refusing to remove the last role.** Rejected: retiring a driver whose only role is Driver must
  succeed ([FR-307]), and [BR-123] says what such an account can do.
- **Keeping every token, so any replay revokes.** Not chosen: the chain would grow with every
  refresh for as long as the session lives.

## Revisit when

- A matrix cell gains a qualification that is not "Only …", or a row needs two capabilities.
- Customers & Orders needs the default window on a date for anything but a new order, or a second
  context needs a window of any day. Then propose a kernel value.
- A replayed token older than 14 days is seen in the audit records. Keep a hash of pruned tokens.

[ADR-068]: ADR-068-every-web-handler-is-authorized-by-role-from-the-contract.md
[BR-19]: ../requirements/requirements.md#access
[BR-35]: ../requirements/requirements.md#orders-records-and-history
[BR-49]: ../requirements/requirements.md#routing-constraints
[BR-111]: ../requirements/requirements.md#time-and-limits
[BR-123]: ../requirements/requirements.md#access
[DL-23]: ../architecture/domain_layer/domain_layer_design.md#23-rules-between-contexts
[DL-31]: ../architecture/domain_layer/domain_layer_design.md#31-aggregates
[DL-41]: ../architecture/domain_layer/domain_layer_design.md#41-access
[FR-02]: ../requirements/requirements.md#company-users-and-roles
[FR-03]: ../requirements/requirements.md#company-users-and-roles
[FR-56]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-125]: ../requirements/requirements.md#company-users-and-roles
[FR-184]: ../requirements/requirements.md#company-users-and-roles
[FR-187]: ../requirements/requirements.md#company-users-and-roles
[FR-293]: ../requirements/requirements.md#company-users-and-roles
[FR-294]: ../requirements/requirements.md#company-users-and-roles
[FR-307]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-345]: ../requirements/requirements.md#company-users-and-roles
[FR-382]: ../requirements/requirements.md#messages-to-recipients
[NFR-85]: ../requirements/requirements.md#nfr-85
[NFR-87]: ../requirements/requirements.md#nfr-87
[NFR-88]: ../requirements/requirements.md#nfr-88
[NFR-90]: ../requirements/requirements.md#nfr-90
[NFR-178]: ../requirements/requirements.md#nfr-178
[NFR-210]: ../requirements/requirements.md#nfr-210
[NFR-214]: ../requirements/requirements.md#nfr-214
[NFR-224]: ../requirements/requirements.md#nfr-224
[NFR-258]: ../requirements/requirements.md#nfr-258
[NFR-260]: ../requirements/requirements.md#nfr-260
