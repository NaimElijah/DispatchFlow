# ADR-075: Retention deletes positions, proofs and messages, and anonymizes customers and drivers in place

- **Status:** Accepted; extended by ADR-121; amended by ADR-102 and ADR-131
- **Date:** 2026-11-14

## Context

On 2026-11-13 the owner, Naim Elijah, adopted retention periods for every kind of personal data that
[Q-14] left open, as defaults until legal advice ([Q-42]). On 2026-11-14 he decided how a period
ends: customers and drivers are anonymized in place, and positions, proofs, messages and replies are
deleted. He decided both for the team. This ADR records the pair, beside the retention row
[NFR-184].

The forces:

- **Each kind of personal data has a stated period and is deleted automatically when it ends**
  ([NFR-31]). Only a request ([NFR-49]) or a retention period removes personal fields, which include
  a customer's coordinates, and the non-personal figures stay ([BR-98]).
- **History stays.** A depot, vehicle, driver, customer or order that any plan refers to is retired
  or cancelled, never deleted ([BR-43]). A completed plan keeps the values it was solved and reported
  with ([BR-37]). After a person's data is deleted, reports keep their non-personal figures
  ([NFR-152]).
- **Audit records carry no personal data** ([NFR-94]). They name the acting user and the session by ID
  ([NFR-93]).
- **A data inventory lists every kind of personal data** ([NFR-72]), and a new personal column must not
  slip past retention ([PL §16.4][PL-164]).
- **Legal advice is pending** ([Q-42]), so the periods are defaults.

## Decision

A nightly job applies the periods below ([PL §16.4][PL-164]). A row that other records refer to is
**anonymized in place**. A record nothing refers to is **deleted**.

| Kind of data | Period | Result |
|---|---|---|
| Location positions | 7 days | Deleted |
| Proofs, messages and replies | 90 days after sending or receipt | Deleted |
| An order's personal fields | 90 days after the order's date | Personal fields removed, figures stay |
| A retired customer or driver | 1 year after retirement | Anonymized in place |
| A customer never retired | 1 year after their last order | Anonymized in place |
| Every account, whatever its roles | 1 year after deactivation | Anonymized in place: audit records and plans refer to them |
| Audit records | 1 year | Deleted |
| Sessions | 30 days after they end | Deleted |
| GlitchTip events | 7 days | Deleted ([ADR-079]) |
| Geocode cache | An entry unused for 1 year | Deleted |
| Research instances | The end of the project plus 1 year | Deleted |
| Backups | 35 days | Age out of the bucket |

The sources are [NFR-184] for the first, second and fourth rows, and [NFR-257] for the third. The
rows after the fourth, except GlitchTip's, are the owner's defaults for [Q-14]. Logs, traces and
metrics are kept 7 days ([NFR-76]), and GlitchTip's event lifetime is set to 7 days to match
[NFR-76].

- **Anonymized in place** means the personal columns are blanked and the row stays: names, phones,
  addresses, coordinates and notes are replaced, and the figures stay ([BR-98], [NFR-152]).
- **Audit records are deleted by a database function.** The `app` role has only INSERT and SELECT
  on audit, and `EXECUTE` on one function ([ADR-041]). That function is owned by
  `dispatchflow_owner`, deletes only audit records older than 1 year, and computes the cutoff
  itself. The core's nightly retention job calls it at 03:00 (`Asia/Jerusalem`), and its
  Healthchecks.io ping alerts the team when a run fails or doesn't happen ([ADR-066]). The owner credential stays deploy-only, used once by `migrate` (the owner,
  2026-11-14).
- **Deleted** applies to positions, proofs, messages and replies. Each day's average rating and share
  answered stay ([NFR-193]).
- **One check against the inventory.** A test compares the data inventory with an annotation,
  `@PersonalData`, on each personal column's jOOQ field, so a new personal column cannot pass
  unnoticed ([PL §16.4][PL-164], [NFR-72]). How the annotation attaches to generated jOOQ code is
  not checked here.
- **Requests use the same anonymization.** An `admin-step` command finds every row about a phone or an
  email from the inventory's list of personal columns, exports them, and corrects or anonymizes them
  in one audited transaction ([NFR-49], [NFR-185], [PL §16.4][PL-164]).
- **Opt-outs are kept.** A phone's opt-out stays as a keyed hash until the company leaves, whatever
  retention removes ([NFR-202]).
- **Company exit** deletes everything 30 days after the export is delivered ([NFR-150], [BR-99]). That
  is the one exception to retiring instead of deleting.

## Consequences

- **Easy:** plans, reports and the audit trail keep working after a person is anonymized, because
  the rows and their IDs stay.
- **Easy:** one test ties every personal column to the inventory, so a new column is caught in CI.
- **Hard:** "anonymized" must hold. Names, phones, addresses and coordinates are blanked, and so must
  anything else that identifies a person, such as free-text notes. The test finds only columns the
  team annotates.
- **Hard:** the rows after the fourth, except GlitchTip's, are the owner's Q-14 defaults. Two of
  them, a customer never retired and every account, apply this ADR's rule (anonymize what other
  records refer to, delete the rest) to those defaults. The data inventory confirms each row before
  the pilot handles real data.
- **Cost:** a nightly job, one database function owned by the owner role, the annotation and its
  test, and the inventory file (`docs/operations/data-inventory.md`).
- **Risk:** a restore brings back rows that retention had already removed. The runbook step in
  [Q-92] re-applies data requests and opt-outs made since the snapshot. It does not name retention.
  The nightly job should remove them again; no source says so.
- **Risk:** a backup holds deleted data for up to 35 days.
- **Risk:** the periods are defaults. Legal advice may shorten them or change what counts as
  anonymized ([Q-42]).

## Alternatives considered

- **Deleting every record at its deadline.** Rejected. It would break the history that a plan refers
  to ([BR-43], [BR-37]) and the figures that reports keep ([NFR-152]).
- **Anonymizing positions, proofs, messages and replies too.** Not chosen: the owner decided that
  these four are deleted. The sources give no further reason.
- **Keeping everything until the company leaves.** Lost on [NFR-31], which asks for a stated period
  for each kind.

## Revisit when

- The legal advice of [Q-42] changes a period, or finds that an anonymized row still identifies a
  person.
- The data inventory finds that a kind's result is wrong, or the restore drill shows that removed
  data comes back and stays. Update the table.
- A column holds personal data and is missing from the annotation test, found in review or in the
  pilot. Tighten how personal columns are marked.
- The pilot company asks for a different period.

[ADR-041]: ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md
[ADR-066]: ADR-066-every-asynchronous-step-has-a-watcher.md
[ADR-079]: ADR-079-client-errors-and-crashes-go-to-self-hosted-glitchtip.md
[BR-37]: ../requirements/requirements.md#orders-records-and-history
[BR-43]: ../requirements/requirements.md#orders-records-and-history
[BR-98]: ../requirements/requirements.md#orders-records-and-history
[BR-99]: ../requirements/requirements.md#orders-records-and-history
[NFR-31]: ../requirements/requirements.md#nfr-31
[NFR-49]: ../requirements/requirements.md#nfr-49
[NFR-72]: ../requirements/requirements.md#nfr-72
[NFR-76]: ../requirements/requirements.md#nfr-76
[NFR-93]: ../requirements/requirements.md#nfr-93
[NFR-94]: ../requirements/requirements.md#nfr-94
[NFR-150]: ../requirements/requirements.md#nfr-150
[NFR-152]: ../requirements/requirements.md#nfr-152
[NFR-184]: ../requirements/requirements.md#nfr-184
[NFR-185]: ../requirements/requirements.md#nfr-185
[NFR-193]: ../requirements/requirements.md#nfr-193
[NFR-202]: ../requirements/requirements.md#nfr-202
[NFR-257]: ../requirements/requirements.md#nfr-257
[PL-164]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#164-personal-data
[Q-14]: ../requirements/requirements.md#open-questions
[Q-42]: ../requirements/requirements.md#open-questions
[Q-92]: ../requirements/requirements.md#open-questions
