# ADR-137: The address abbreviation table gains six entries, and ambiguous ones stay out

- **Status:** Accepted
- **Date:** 2026-11-24

## Context

[FR-398] matches an import row to a customer on normalized address text, and the text expands
abbreviations from a fixed table. [ADR-102] decision 3 started the table with [ADR-086]'s three
entries, רח׳ for רחוב, שד׳ for שדרות and ת״א for תל אביב, and left the rest to
[#69][ISSUE-69], because a wrong entry merges two customers. `NormalizedAddress` holds the table, and
`NormalizedAddressTest` pins שכ׳ as an abbreviation the table leaves alone.

The table errs in two directions:

- **An entry missing:** a stored address and an import row that spell one address two ways match no
  customer, so the import creates a second one, with an address flag ([FR-311]). Be'er Sheva's
  neighbourhoods are named by letter, so שכ׳ ד׳ and שכונה ד׳ are one place written two ways.
- **An entry wrong:** expanding an abbreviation that stands for more than one word makes two different
  addresses equal, and the import adds the row to the wrong customer, with no flag.

An entry matches only as a whole word, with no letter or digit touching it, after `'`, `"` and
their typographic forms have become the geresh and gershayim (ADR-102 decision 4).

Until a company is known, the service area is the whole road-data extract ([FR-320]), so an import
can hold addresses from any city in it.

No database holds customers. Nothing is deployed, and the `dev` and `test` databases are containers
that last one run.

## Decision

Decided on 24 November 2026. This extends ADR-102 decision 3; everything else in it stands.

1. **Six entries join the table:**

   | Abbreviation | Expands to | What it names |
   |---|---|---|
   | שכ׳ | שכונה | a neighbourhood, as in שכ׳ ד׳ |
   | סמ׳ | סמטה | an alley |
   | ת״ד | תא דואר | a post office box |
   | דיר׳ | דירה | a flat |
   | כנ׳ | כניסה | an entrance, as in כנ׳ ב׳ |
   | מס׳ | מספר | a house number |

2. **These stay out:**
   - **Two meanings in an address,** so expanding one would merge two customers:
     - ד׳: a flat, or a neighbourhood or entrance named ד׳;
     - ק׳: a floor, or a town's קריית;
     - כ׳: a square, or the letter as a name;
     - ב״ש: באר שבע, or בית שמש, which the service area also holds ([FR-320]).
   - **Two full forms:** א״ת and אזה״ת expand to אזור תעשייה or to אזור התעשייה, and the table can
     give only one.
   - **Not matchable as typed:** י-ם, because the hyphen becomes a space before the table is read.
3. **`NormalizedAddressTest` has a case for each entry,** and one for each abbreviation in the
   first two groups above, which must stay as typed.
4. **No stored address needs normalizing again:** no database holds a customer. From the first
   deployment, a change to the table comes with a step that normalizes every stored customer's
   address again ([ADR-102], Revisit when).

## Consequences

- **Easy:**
  - Addresses written with these six abbreviations find their customer instead of creating a second
    one.
  - The abbreviations left out are named here and pinned by the test, so nobody adds one without
    seeing why it stayed out.
- **Hard:**
  - An abbreviation that touches a digit is not expanded: a geresh ends its word only before a
    letter, so דיר׳3 stays as typed and does not match דירה 3.
  - An address written with ב״ש does not match its customer written with באר שבע, so the import
    creates a second customer with an address flag.
  - Once a database holds customers, every change to the table needs that normalization step.
- **Cost:** six lines in `NormalizedAddress`, and a case for each in its test.
- **Risk:** a file that spells a flat or an entrance one way and the customer's stored address
  another still makes a second customer. Its address flag shows it.

## Alternatives considered

- **Keep the table at three until an expert has read every entry,** as ADR-102 had it. Rejected:
  שכ׳ alone splits Be'er Sheva's lettered neighbourhoods into two customers each, and each entry above
  has one meaning in an address.
- **Add ב״ש for באר שבע.** Rejected for now: a row meaning בית שמש would match a Be'er Sheva customer
  at the same street and number, and the order would go to the wrong city with no flag. A second
  customer with a flag is the safer mistake.
- **Add the one-letter abbreviations as well.** Rejected: each has two meanings in an address, so
  each could merge two customers.
- **Drop the words the table expands** (רחוב, שכונה, דירה), so רחוב הרצל matches הרצל. Rejected
  here: it changes FR-398's steps, not the table, and needs its own decision.

## Revisit when

- An import shows a common abbreviation the table lacks, or a merge that one of these entries caused
  ([FR-310], [FR-312]): change the table and its test.
- The service area is clipped to a company's area that holds Be'er Sheva and not Beit Shemesh: add
  ב״ש.
- The first deployment holds customers: build the normalization step before the next table change.

[ADR-086]: ADR-086-csv-imports-upload-then-confirm-within-an-hour.md
[ADR-102]: ADR-102-order-references-are-checked-on-every-move-to-a-date.md
[FR-310]: ../requirements/requirements.md#customers-and-orders
[FR-311]: ../requirements/requirements.md#customers-and-orders
[FR-312]: ../requirements/requirements.md#customers-and-orders
[FR-320]: ../requirements/requirements.md#geocoding
[FR-398]: ../requirements/requirements.md#customers-and-orders
[ISSUE-69]: https://github.com/NaimElijah/DispatchFlow/issues/69
