# ADR-106: Free text is normalized and capped by one kernel rule, and personal values carry a marker

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

Every module takes free text: people's, customers', vehicles' and depots' names, addresses, and
notes for drivers. Reviews of the first domain modules found that each module checked only that its
text was not blank:

- Names accepted invisible, control and bidi characters. "Van 1" and "Van 1" followed by a no-break
  space were accepted as two different vehicles, and look the same on every screen.
- Names accepted any length: a name of 5,000,000 characters was accepted.
- A NUL character was accepted, and PostgreSQL refuses it in a `text` column, so the failure would
  surface as a database error instead of a refusal.
- No requirement states a length for any field. [NFR-56] (contract validation) refuses requests
  that break the contract, but the contracts set no `maxLength` yet.

Trimming and checking in each module would repeat one rule in every module that takes text, and the
copies would drift.

A security review of the Orders module found a second gap. The shape rule that keeps personal data
out of error and event fields ([ADR-043], [NFR-19]) matched `PhoneNumber` and `GeoPoint` by type,
and `EmailAddress`, `Address` and `NormalizedAddress` by simple name, from a list in the
architecture tests. Each module would have to edit that list, and the modules in flight add many
more personal values, such as `ContactName`, `DriverNote`, `Recipient` and `DriverName`. A value
left off the list passed the rule.

A review of the first version of this rule found more: the JDK's `Normalizer` takes time quadratic
in a run of combining marks (160,000 marks took 9.8 seconds); line and paragraph separators and
carriage returns slipped past the line-feed rule; text of only invisible characters, such as a
Hangul filler, was not empty; and a personal value could hide inside an unmarked wrapper, or print
itself through `toString()`.

[ADR-097] put the building blocks every module's domain uses in the shared kernel. This ADR extends
it with two more.

## Decision

On 2026-11-14 the owner, Naim Elijah, decided that every module's free text goes through one kernel
value, `PlainText`, built by `PlainText.of(TextKind, String)`.

- **Kinds and caps.** `TextKind` names the kind, and each kind has a cap in Unicode code points,
  counted after normalization. The caps are starting values:

  | Kind | Cap | For |
  |---|---|---|
  | `NAME` | 100 | Person, customer, vehicle and depot names, and other short fields |
  | `ADDRESS` | 200 | An address as typed |
  | `NOTE` | 500 | Notes for drivers and free comments |

- **Steps**, in this order:
  1. Refuse raw input longer than 8 UTF-16 units per code point of the cap with `TextTooLong`,
     before normalizing, so the normalizer's quadratic case stays small. Eight leaves room for
     padding and for decomposed letters.
  2. In a `NOTE`, turn each CRLF and each lone CR into a line feed.
  3. Unicode NFC, with the JDK's `java.text.Normalizer`.
  4. Trim from both ends every character that is whitespace (`Character.isWhitespace`), a space
     separator (`Character.isSpaceChar`, which covers no-break spaces), or a format character
     (category Cf). The interior is kept. Matching forms, such as [FR-398]'s normalized address,
     stay in their modules.
  5. Refuse text longer than the cap with `TextTooLong(kind, maxLength)`.
  6. Treat text made only of characters that render as nothing as empty: format characters,
     Unicode's default-ignorable code points (such as U+00AD, U+034F, U+115F, U+3164 and the
     variation selectors), and U+2800, the blank Braille pattern.
  7. Refuse with `TextHasForbiddenCharacters(kind)` text that holds:
     - a control character (Cc), except a line feed in a `NOTE`; a CR in a `NAME` or an `ADDRESS`
       is refused;
     - a line or paragraph separator, U+2028 or U+2029, except in a `NOTE`;
     - in a `NAME` or an `ADDRESS`, an interior format character (Cf) other than ZWNJ (U+200C), ZWJ
       (U+200D), LRM (U+200E) and RLM (U+200F), which Hebrew and mixed text use, and ALM (U+061C),
       because Arabic addresses are expected input ([Q-83]);
     - a bidi embedding, override or isolate control, U+202A to U+202E and U+2066 to U+2069, in any
       kind;
     - an unpaired surrogate, or a private-use (Co) or unassigned (Cn) code point.

  Because the ends are trimmed first, a format character at either end, a bidi control included,
  is removed rather than refused. Tag characters are format characters, so a subdivision flag at
  the end of a note, such as England's, loses its tags and becomes a bare black flag. That is
  accepted. Homoglyphs are not handled.
- **Errors carry no text.** Both errors' only fields are the kind and a number, so no text reaches
  a response's `params` ([ADR-043]). The web adapter attaches the name of the refused field to
  them, and to `InvalidPhoneNumber` the same way, so the message names the field. The core maps
  none of them yet.
- **Blank text is not refused here.** `PlainText` may be empty, and `isEmpty()` says so. Each module
  keeps its own `Blank…` error, so the message names the field.
- **Personal data.** Names, addresses and notes are personal data ([NFR-19]): `toString()` prints
  `PlainText[redacted]`, and a refusal names no text.
- **Stored text** is rebuilt with `PlainText.ofStored(TextKind, String)`, which applies the same rule
  through `StoredStateInvalid.whileRestoring`, so stored text the rule refuses is an incident, never
  a user-facing refusal.
- **Scope.** [NFR-203] cuts a recipient's comment to 500 characters. Cutting is not refusing: the
  Messaging context (wave 3) adds its own truncating path, and `PlainText` stays a rule that refuses.

The owner also decided, the same day, that personal values are marked in their own type rather than
listed by name:

- **A marker.** The kernel's `PersonalData` interface marks a value that identifies or describes a
  person: a name, a phone number, an address, a location, a free-text note or an email address.
  `PhoneNumber` and `GeoPoint` implement it, and every module makes each of its personal values
  implement it.
- **Error and event fields.** An error field or an event field whose type implements
  `PersonalData`, or a list of such values, is refused. An error field is also refused when its
  type is a DispatchFlow record or class that holds a `PersonalData` value at any depth of fields
  and type arguments, such as `Optional<List<ContactName>>` or an unmarked `Contact(ContactName)`.
  The search unwraps arrays, so `Recipients(Name[] names)` counts, and it searches an interface or
  an abstract class through its subclasses, so a sealed `Recipient` whose `Named(Name)` variant
  holds a personal value counts. It stops at a type it has already seen. The bans on text (`CharSequence`, `PlainText`),
  `Object` and arrays in error fields stay, and the name list is removed.
- **Redacted printing.** Every concrete type that implements `PersonalData` declares its own
  `toString()`, so it prints redacted. A record's implicit `toString()` does not count. javac marks
  that implicit method `public final`, and the rule tells the two apart by that modifier, so a
  record that declares `public final String toString()` on purpose is flagged as missing one; it
  drops `final`.
- **`PlainText` is not marked.** Whether a piece of text is personal depends on the module value
  that holds it. An error field still may not hold a `PlainText`, because it is text, and an event
  never could, because the event rule lists what it may hold.

The ArchUnit shape rules in `DomainShapeRules` check all three, with a fixture each must flag.

## Consequences

- **Easy:** one rule, one set of tests, and one pair of error codes for both apps to translate.
  Look-alike names such as "Van 1" with a trailing no-break space become equal, and a NUL or a
  5,000,000-character name is refused, before it reaches PostgreSQL and before the normalizer
  sees it. A module's new personal value is covered by the shape rules once it implements
  `PersonalData`, with no edit to the architecture tests.
- **Hard:** a module that needs a different cap needs a new `TextKind`, which is a kernel change
  and needs every module owner's agreement ([ADR-097]). Lowering a cap later makes longer stored
  text fail to load, so it needs a migration first. Text padded with more than 8 units per allowed
  code point is refused even when it would fit once trimmed.
- **Cost:** none. The rule uses only the JDK.
- **Risk:**
  - The caps are guesses, and a real name, address or note may be longer.
  - Unicode versions. Whether a code point is unassigned depends on the JDK's Unicode version
    (15.0 in Java 21), so a character newer than the JDK, such as a new emoji, is refused until the
    JDK is upgraded. The default-ignorable table is copied from Unicode 15 and misses later
    additions.
  - A personal value whose author forgets the marker passes the shape rules unseen, as a name
    missing from the list did before; review checks it.

## Alternatives considered

- **Trimming in each module, with no caps.** Rejected: one copy of the same rule per module, which
  drift apart, and nothing bounds the length or refuses a NUL.
- **Leaving the caps to the contract,** as `maxLength` in OpenAPI. Rejected as the only check: the
  contract counts before normalization, it does not guard imports or stored data, and it sets no
  caps yet. The contract may still state the same caps once [NFR-56] is built.
- **Keeping the list of personal values by name.** Rejected: every module edits the architecture
  tests, and a value left off the list passes.
- **Marking `PlainText` as personal data.** Rejected: a module may hold text that is not personal,
  such as a depot's name, and the module value that holds the text knows which it is.
- **Matching confusable characters (homoglyph skeletons).** Not chosen: it needs Unicode's
  confusables data, which the JDK lacks, and no reviewed case needs it.

## Revisit when

- A cap refuses real data.
- The contracts' `maxLength` values are set ([NFR-56]), so the caps here and there must agree.
- A personal value reaches an error or an event because its type lacks the marker.
- The JDK moves to a newer Unicode version, which may add default-ignorable code points.

[ADR-043]: ADR-043-generated-code-from-both-contracts-and-problem-details-errors.md
[ADR-097]: ADR-097-the-shared-kernel-holds-the-domains-building-blocks.md
[FR-398]: ../requirements/requirements.md#customers-and-orders
[NFR-19]: ../requirements/requirements.md#nfr-19
[NFR-56]: ../requirements/requirements.md#nfr-56
[NFR-203]: ../requirements/requirements.md#nfr-203
[Q-83]: ../requirements/requirements.md#open-questions
