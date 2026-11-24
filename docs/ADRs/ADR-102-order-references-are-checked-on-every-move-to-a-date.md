# ADR-102: Order references are checked on every move to a date, and the abbreviation table starts with three

- **Status:** Accepted; extended by ADR-137; amended by ADR-115
- **Date:** 2026-11-14

## Context

The Customers & Orders domain is built ahead of its milestone, from [DL §4.3] and the requirements.
Several of its rules were not settled by any document:

- **References on a move.** [FR-211] refuses an imported order whose reference already exists on its
  date. [DL §4.3] checked it in `Order.place` and the import only, so an order rescheduled or put back
  onto a date could bring a reference that date already has. Nothing said whether a cancelled order's
  reference still counts.
- **The abbreviation table and punctuation.** [FR-398] expands abbreviations "from a fixed table", and
  [ADR-086] names three examples. No list beyond them has been agreed. Dropping punctuation outright
  would join "הרצל 5/3" into "הרצל 53", another address.
- **Decimals.** [BR-50] says only that a weight or a volume is positive. [ADR-061]'s `numeric(8,2)`
  and `numeric(9,2)` columns would round a CSV weight of 0.125 kg on insert. Today the kernel's
  `Weight` and `Volume` refuse more than two decimals with `QuantityOutOfRange` ([ADR-097]).
- **Retention.** [NFR-257] anonymizes a customer never retired a year after its last order, which
  leaves a customer with no order kept forever. An outside reference may be built from a customer's
  data, and [NFR-257] doesn't list it among an order's personal fields. A confirmed import's rows
  hold names, phones and entrance codes, and [ADR-086] rejected a preview kept without end.

Where the customer defaults live is decided in [ADR-104]: Access's `CompanyProfile` holds them.

This ADR amends [ADR-075], whose retention period for a customer never retired counted from the
last order only (decision 6), and extends [ADR-086], with the abbreviation table, the matching
steps and the rule that a confirmed import keeps no rows (decisions 3, 4 and 8).

## Decision

Decided on 2026-11-14:

1. **Orders takes the defaults as an input** ([ADR-104], [BR-124]). `Customer.register` takes a
   customer's defaults, and an import takes the company's in its `ImportContext`. The default service
   time may be absent, and an order given no service time whose customer has none is refused with
   `NoServiceTime`.
2. **A reference is checked on every move to a date** ([FR-211]). A reschedule, or a put-back onto a
   date, has its reference checked exactly as a new order on that date, through the same
   `DateCapacity` snapshot as the date's order limit ([BR-101], [BR-113]). A cancelled order is not in
   the snapshot, so its reference no longer counts. An order's own reference never counts against it.
3. **The abbreviation table starts with [ADR-086]'s three** ([FR-398]): רח׳ for רחוב, שד׳ for שדרות
   and ת״א for תל אביב. The rest is tracked in issue #69.
4. **Punctuation becomes a space** ([FR-398], whose row was allowed to change to say so). Every
   character other than a letter, a digit, whitespace, the geresh or the gershayim becomes a space,
   and whitespace then collapses. `'`, `‘`, `’` and the backtick count as the geresh; two gereshes,
   so `''` and `’’` too, and `"`, `“` and `”` count as the gershayim; and a geresh ends its word, so
   רח'הרצל reads as רח׳ הרצל. An address whose normalized form holds no letter or digit is refused
   with `BlankAddress`.
5. **More than two decimals is refused** ([BR-50]) with `QuantityOutOfRange`, so a weight is never
   changed silently. In an import, the row's field error carries it, listed as a `RowRefused` with the
   row's number under `ImportHasErrors`.
6. **A customer never retired is anonymized a year after the later of its creation and its last
   order** ([NFR-257]), counted in Israel's calendar. `Customer.register` records the creation time.
7. **An order's outside reference is personal data**: it prints redacted, and `Order.anonymize`
   removes it with the other personal fields, 90 days after the order's date ([NFR-257]).
8. **A confirmed import keeps no rows** ([NFR-31], [ADR-086]). Its orders and customers hold what was
   imported, so the batch drops its rows as a discarded or expired one does.

### Readings this ADR settles

No document said these, and the implementation reads them so:

- [FR-398]'s purpose is that two ways of typing one address match. So the matching key, and only
  it, also deletes format characters (such as ZWJ, ZWNJ, RLM and LRM) and combining marks (such as
  niqqud) before the punctuation step: הֶרְצֵל 5, and הרצל 5 with a zero-width joiner inside the
  word, both match הרצל 5. The stored address keeps them.
- Rows of one file whose addresses normalize alike, and match no customer, share one new customer,
  made from the first of them. Each such row still needs its own name and phone ([FR-311]).
- Rescheduling an order onto the date it is already on is refused with `AlreadyOnThatDate`.
- A retired customer's address and location don't change, and it isn't retired again
  (`CustomerRetired`); a geocode for it is still recorded. An anonymized customer refuses every
  command (`CustomerAnonymized`) and ignores a late geocode. An anonymized order refuses every command
  (`OrderAnonymized`).
- A put-back raises no event, because [DL §7.2] gives it none.
- A geocode answer for an address the customer no longer has is ignored, and a lookup the outbox gave
  up on after 24 hours flags the missing match ([FR-63], [FR-64]).
- Expiring a preview before its hour is refused with `ImportNotYetExpired`.

## Consequences

- **Easy:** one locked snapshot decides both the limit and the reference on every path that adds an
  order to a date, so a reschedule can't slip a duplicate past the import's check.
- **Easy:** a dispatcher who imports a weight of 0.125 kg sees the row and the reason, instead of an
  order of 0.13 kg.
- **Easy:** no customer, reference or import row keeps personal data past its retention.
- **Hard:** the use case reads the company's defaults from Access before it registers a customer or
  confirms an import.
- **Hard:** until the issue's table lands, addresses written with other abbreviations, such as שכ׳
  for שכונה, don't match their spelled-out form, and an import creates a second customer, flagged for
  a location check ([FR-311]).
- **Hard:** a dispatcher can't reopen a confirmed import's rows; the orders themselves show what came
  in.
- **Cost:** the `DateCapacity` query reads each order's reference with its ID.
- **Risk:** a company whose files use many abbreviations meets duplicate customers early in the
  pilot.

## Alternatives considered

- **Check references only on placing and importing,** as [DL §4.3] had it. Rejected: a reschedule or a
  put-back would put two orders with one reference on a date, which the partial unique index then
  refuses as a database error instead of a named refusal.
- **Keep a cancelled order's reference on its date.** Rejected: an outside system that cancels and
  re-sends an order with the same reference would be refused.
- **A longer abbreviation table now.** Rejected: nobody has agreed its entries, and a wrong one merges
  two customers' addresses ([ADR-086], Risk).
- **Drop punctuation without a space.** Rejected: it joins numbers and words into another address.
- **Round to two decimals, half up.** Rejected: it changes a dispatcher's figure without telling
  them.
- **Keep a customer with no order until someone deletes it.** Rejected: it keeps personal data with no
  end, against [NFR-31].

## Revisit when

- The issue's table is agreed: extend the table and its test, and re-normalize every stored customer's
  address, so the stored matching text follows the new table.
- A pilot import is rejected because of a decimal the company's system always sends, such as three
  decimals of kilograms: decide whether to round at the reader.
- Legal advice on retention arrives ([NFR-257]): change the periods, not the rule's owners.

[ADR-061]: ADR-061-capacity-is-weight-and-volume.md
[ADR-075]: ADR-075-retention-deletes-records-and-anonymizes-people-in-place.md
[ADR-086]: ADR-086-csv-imports-upload-then-confirm-within-an-hour.md
[ADR-097]: ADR-097-the-shared-kernel-holds-the-domains-building-blocks.md
[ADR-104]: ADR-104-the-company-profile-holds-customer-defaults-and-capabilities-follow-the-role-matrix.md
[DL §4.3]: ../architecture/domain_layer/domain_layer_design.md#43-customers--orders
[DL §7.2]: ../architecture/domain_layer/domain_layer_design.md#72-the-events
[BR-50]: ../requirements/requirements.md#routing-constraints
[BR-101]: ../requirements/requirements.md#time-and-limits
[BR-113]: ../requirements/requirements.md#time-and-limits
[BR-124]: ../requirements/requirements.md#orders-records-and-history
[FR-63]: ../requirements/requirements.md#geocoding
[FR-64]: ../requirements/requirements.md#geocoding
[FR-211]: ../requirements/requirements.md#customers-and-orders
[FR-311]: ../requirements/requirements.md#customers-and-orders
[FR-398]: ../requirements/requirements.md#customers-and-orders
[NFR-31]: ../requirements/requirements.md#nfr-31
[NFR-257]: ../requirements/requirements.md#nfr-257
