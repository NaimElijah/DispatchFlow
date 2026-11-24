# ADR-086: An order import is two calls, upload then confirm within an hour, and writes every row or none

- **Status:** Accepted; extended by ADR-102
- **Date:** 2026-11-14

## Context

A dispatcher imports a day's orders from a CSV file ([FR-10]), or from an Excel workbook
([FR-138]). The requirements already fix much of the behaviour:

- every row is validated and every rejected row is reported with its reason ([FR-203]);
- a file with any rejected row imports nothing ([FR-204]);
- a row names its customer by address ([FR-205]);
- a file in UTF-8 is read as UTF-8, any other file as Windows-1255, and the dispatcher confirms a
  preview first ([FR-313]);
- a UTF-16 file is refused ([FR-314]).

[FR-10] carried the marker "ADR pending: CSV order import". It settles the shape that makes those
rules work together: how a file reaches the database, what a preview holds and for how long, and how
a row finds its customer.

Decided on 2026-11-13 and 2026-11-14:

- the plan's design of an import as upload then confirm, with an explicit Windows-1255
  confirmation ([PL §15], [PL §25]);
- on 2026-11-13, what "normalized" means for [FR-310] ([Q-94] #4);
- on 2026-11-14, that a preview lives one hour, after which it expires and its rows are deleted.

The rows of a preview hold customers' names, phones and entrance codes (the columns of [FR-309]).

## Decision

- **An import is two calls.** *Upload* creates an `ImportBatch` in the *previewed* state. It holds
  every parsed row and every error, and returns the preview. *Confirm* writes everything or nothing
  ([DL §9.4]).
- **The file is read in a fixed order** ([PL §15.1]):
  1. A file over 5 MB is refused by the multipart limit, before anything is read ([NFR-37]).
  2. A file that starts with the zip magic bytes goes to Apache POI. Everything else is CSV.
  3. A UTF-16 byte order mark or any NUL byte refuses the file ([FR-314]).
  4. A UTF-8 byte order mark, or bytes that decode as strict UTF-8, are read as UTF-8.
  5. Anything else is read as strict Windows-1255. Bytes undefined in 1255 refuse the file
     ([FR-209]). The response is marked `needsEncodingConfirmation` with a preview of the first
     rows, and *confirm* refuses without the flag ([FR-313], [FR-61]).
  6. A workbook is read with a minimum inflate ratio of 0.01 and a 50 MB cap on unpacked size, from
     its first sheet, using cached cell values only, so external references are never followed
     ([NFR-96], [NFR-97], [FR-319]).
- **The columns are fixed and named** ([FR-309]): `customer_name`, `address`, `phone`, `date`,
  `window_start`, `window_end`, `weight_kg`, `volume_l`, `service_min`, `priority`, `leave_at_door`,
  `reference`, `delivery_note`, `floor`, `apartment`, `entrance_code`, `recipient_name` and
  `recipient_phone`. Header names are accepted in Hebrew or English through one alias table, which
  the template download uses too ([FR-84]).
- **Every row goes through the `Customer` and `Order` factories,** which collect every broken rule
  instead of stopping at the first. `ImportBatch` adds the rules across rows, such as the date's
  order limit, counting existing orders ([BR-101], [BR-113]) ([PL §15.2]).
- **A row matches a customer on normalized address text** ([FR-310]), as a `NormalizedAddress`
  value. "Normalized" means ([Q-94] #4):
  - Unicode NFC;
  - trim and collapse whitespace;
  - one geresh and one gershayim;
  - expand a fixed table of abbreviations, for example רח׳ to רחוב, שד׳ to שדרות and ת״א to
    תל אביב;
  - drop punctuation except letters and digits;
  - lower-case Latin letters.

  The abbreviation table is data, not code, and the rule lives in one value object with a
  table-driven test.
- **No match, one match, several matches.** No match among active customers creates a customer from
  the row's name and phone, flagged for a pin check, or rejects the row if they are missing
  ([FR-311]). One match uses it. Several reject the row and name them ([FR-312]).
- **Confirm re-reads the world.** It reads the current matches, references and each date's capacity
  under the date's lock, because they may have changed since the preview, and `ImportBatch.confirm`
  checks every row again. One transaction then marks the batch *confirmed*, creates the customers
  and orders, writes the audit record and adds a `geocode_request` row for each new customer
  ([PL §15.2], [DL §6.1]).
- **Geocoding happens after the commit,** from that outbox row, so a slow geocoder never holds up an
  import ([NFR-60], [ADR-085]).
- **A preview lives one hour.** A batch not confirmed within the hour moves to *expired*, and its
  rows are deleted. A batch the dispatcher *discards* ([DL §9.4]) has its rows deleted at once
  (decided 2026-11-14). `ImportBatch.confirm` checks the
  hour on confirmation, and an hourly sweep expires the rest ([DL §10.3]). A requirement row beside
  [FR-313] states the hour.
- **The Alpha builds the UTF-8 CSV path and the row checks.** Excel, Windows-1255 and geocoding come
  in M7 ([PL §15]).

## Consequences

- **Easy:** the dispatcher sees every problem in a file at once, and nothing is half imported
  ([FR-203], [FR-204]).
- **Easy:** a wrong guess about the encoding shows as garbled Hebrew in the preview, before anything
  is written.
- **Easy:** the normalization rule can change in one place, because it lives in one value object
  with a table-driven test.
- **Hard:** a dispatcher who waits more than an hour must upload the file again.
- **Hard:** a file that is not UTF-8 always takes a second step, because the confirmation is
  required.
- **Hard:** confirm repeats the validation, so the work runs twice for a clean file.
- **Cost:** a table of previewed batches and rows, an expiry sweep and a deletion job. A 200-row
  file must still be validated and reported within 10 seconds, not counting geocoding ([NFR-60]).
- **Risk:** a normalization rule that is too loose matches a row to the wrong customer, and one that
  is too strict creates a duplicate customer, which is at least flagged ([FR-311]). The rule has not
  met a real company's files.
- **Risk:** the preview keeps personal data for up to an hour, and a dispatcher who abandons the
  file leaves it until the sweep runs.

## Alternatives considered

- **Import the valid rows and report the rest.** Rejected on 2026-10-10 ([Q-10]): a file with any
  rejected row imports nothing ([FR-204]).
- **One call that uploads and writes.** Rejected: it cannot show a Windows-1255 file's decoded
  Hebrew for confirmation first ([FR-313]).
- **A preview with no expiry.** Rejected: it would keep customers' names, phones and entrance codes
  in the table with no end.
- **ICU4J for Hebrew text.** Rejected in [TS §6]: much larger, for transliteration this rule does
  not do. The JDK's `java.text.Normalizer` covers NFC, and the Hebrew-specific rules are ours.
- **Matching on the raw address text.** Rejected: [FR-310] matches on normalized text, so
  abbreviation and spelling variants of one address find one customer.

## Revisit when

- Two imports in a row during the pilot are rejected for ambiguous or duplicate customers that the
  normalization rule caused ([FR-310], [FR-312]). Change the rule and its table.
- A pilot dispatcher loses a preview to the one-hour expiry. Lengthen the hour, or keep the rows
  until the end of the day.
- A 200-row import takes more than 10 seconds to validate ([NFR-60]). Validate in the background.
- The company's files arrive in an encoding other than UTF-8 and Windows-1255, or with columns
  other than [FR-309] names. Change the reading rules, not the two-call shape.

[ADR-085]: ADR-085-govmap-geocodes-addresses-with-nominatim-as-the-fallback.md
[DL §10.3]: ../architecture/domain_layer/domain_layer_design.md#103-every-rule-that-depends-on-time
[DL §6.1]: ../architecture/domain_layer/domain_layer_design.md#61-the-rule
[DL §9.4]: ../architecture/domain_layer/domain_layer_design.md#94-importbatch
[PL §15]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#15-imports-and-geocoding
[PL §15.1]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#151-reading-the-file
[PL §15.2]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#152-validating-rows
[PL §25]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#25-decisions-to-record-as-adrs
[TS §6]: ../architecture/tech_stack.md#6-core-service-java
[BR-101]: ../requirements/requirements.md#time-and-limits
[BR-113]: ../requirements/requirements.md#time-and-limits
[FR-10]: ../requirements/requirements.md#customers-and-orders
[FR-61]: ../requirements/requirements.md#customers-and-orders
[FR-84]: ../requirements/requirements.md#customers-and-orders
[FR-138]: ../requirements/requirements.md#customers-and-orders
[FR-203]: ../requirements/requirements.md#customers-and-orders
[FR-204]: ../requirements/requirements.md#customers-and-orders
[FR-205]: ../requirements/requirements.md#customers-and-orders
[FR-209]: ../requirements/requirements.md#customers-and-orders
[FR-309]: ../requirements/requirements.md#customers-and-orders
[FR-310]: ../requirements/requirements.md#customers-and-orders
[FR-311]: ../requirements/requirements.md#customers-and-orders
[FR-312]: ../requirements/requirements.md#customers-and-orders
[FR-313]: ../requirements/requirements.md#customers-and-orders
[FR-314]: ../requirements/requirements.md#customers-and-orders
[FR-319]: ../requirements/requirements.md#customers-and-orders
[NFR-37]: ../requirements/requirements.md#nfr-37
[NFR-60]: ../requirements/requirements.md#nfr-60
[NFR-96]: ../requirements/requirements.md#nfr-96
[NFR-97]: ../requirements/requirements.md#nfr-97
[Q-10]: ../requirements/requirements.md#open-questions
[Q-94]: ../requirements/requirements.md#open-questions
