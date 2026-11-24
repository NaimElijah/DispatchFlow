# ADR-024: DispatchFlow serves one delivery company, with no multi-tenancy

- **Status:** Accepted
- **Date:** 2026-11-04

## Context

Until now the requirements described a product that many small businesses share. Anyone could sign
up an organization, every record belonged to exactly one organization, the backend had to enforce
that isolation on every read and write, and a platform operator could suspend an organization.

Decided on 2026-11-03: DispatchFlow is made for a single customer, one
delivery company. That company is the pilot's company too, and it is not named yet ([Q-01]).

Nothing has to be undone in code. `src/` and `contracts/` have no organization or tenant concept
yet, so this is the cheapest moment to decide.

## Decision

One deployment serves one delivery company.

- There is no organization or tenant entity, and no table carries a company key.
- There is no public sign-up. The first Admin account is created when the system is deployed
  ([FR-01]), by a step on the server with no default credential ([FR-291]).
- The company's settings are one record ([FR-125]).
- Access control stays: a valid token on every call, with the exceptions [NFR-01] lists, roles
  checked on the server ([NFR-85]), and a driver limited to the stops of their own routes
  ([BR-02]). [NFR-176] tests this through the API.

These requirements are withdrawn, and their IDs are never reused:

| ID | What it said |
|---|---|
| BR-01 | Tenant isolation |
| NFR-02 | Isolation enforced by the backend and tested with two organizations |
| NFR-41 | Organizations solving at the same time without delaying each other |
| FR-78 | A platform operator suspends an organization |
| FR-122 | A new organization loads sample data |
| Q-13 | How tenant isolation is built |
| Q-41 | Whether open sign-up holds during the pilot |
| Q-61 | What a suspension does and who may see tenant data for support |

The non-goal *Let one user belong to several organizations* goes too, replaced by *Serve more than
one company*.

ADR-001 and ADR-002 mention organizations in their context, and ADR-020 and ADR-022 mention
tenancy. Accepted ADRs are never rewritten, so this ADR overrides those mentions. None of them
decided multi-tenancy. `ADR pending: multi-tenancy by organization` and `ADR pending: tenant
isolation by filtering and tenant keys`, which the requirements used to name, will not be written.
The implementation plan in `docs/init_impl/` also describes multi-tenancy, and stays as planning
history.

## Consequences

- **Easy:** no isolation filter on every query, and no second organization in every API test. There
  is no open sign-up that could spend the paid geocoding quota, no platform operator role, and no
  company's solve delaying another's.
- **Hard:** a second company needs its own deployment, or a migration that adds a company key to
  every table and query.
- **Cost:** a deployment step that creates the first Admin safely ([FR-291]), in place of a sign-up
  flow.
- **Risk:** tenant isolation no longer backs up the role checks. A missing role check or a driver
  reaching another driver's route has nothing behind it, so [FR-03], [NFR-85] and [BR-02] carry all
  of the access control, and [NFR-176] has to prove them.
- **Risk:** with no platform operator, recovering a hijacked or lost Admin needs a second Admin
  ([BR-19]) or a team member with server access ([FR-292]).
- **Risk:** the limits of 15 vehicles and 200 stops are final ([Q-90]) but were set for small
  businesses. A larger company reopens them.

## Alternatives considered

- **Keep multi-tenancy for a possible second customer.** Rejected. No second customer exists, and
  every query, test and sign-up flow would carry the cost until one does.
- **Keep a company key on every table, with one company in it.** Rejected for the same reason. It
  makes a later migration cheaper, but every query still needs the filter and every API test still
  needs to prove it.

## Revisit when

- **A second delivery company wants DispatchFlow.** Compare separate deployments with adding company
  keys. Separate deployments stay simpler until running and upgrading them costs more than the
  migration.
- **The company's own structure needs separation,** such as branches whose dispatchers must not see
  each other's data. That needs a scope inside the company, not a tenant.

[BR-02]: ../requirements/requirements.md#access
[BR-19]: ../requirements/requirements.md#access
[FR-01]: ../requirements/requirements.md#company-users-and-roles
[FR-03]: ../requirements/requirements.md#company-users-and-roles
[FR-125]: ../requirements/requirements.md#company-users-and-roles
[FR-291]: ../requirements/requirements.md#company-users-and-roles
[FR-292]: ../requirements/requirements.md#company-users-and-roles
[NFR-01]: ../requirements/requirements.md#nfr-01
[NFR-85]: ../requirements/requirements.md#nfr-85
[NFR-176]: ../requirements/requirements.md#nfr-176
[Q-01]: ../requirements/requirements.md#open-questions
[Q-90]: ../requirements/requirements.md#open-questions
