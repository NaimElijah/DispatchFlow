# ADR-111: Attempt is a shared-kernel value, and names and addresses drop invisible fillers

- **Status:** Accepted
- **Date:** 2026-11-15

## Context

A review of the whole domain layer, on `dev` at `4701fd5`, found two gaps in the shared kernel.

**Three copies of `Attempt`.** An order's attempt ([FR-258]) is counted by Customers & Orders, and
Planning and Live Operations hold it on every stop. Each context kept its own copy, as
[DL §2.3][DL-23], rule 1, asks for a concept that several contexts model. The three copies had drifted:

- Orders' refused a number below 1 with `StoredStateInvalid`, and checked `next()` for overflow;
- Planning's refused it with a user-facing `InvalidAttempt` in `PlanningError`, and its `next()`
  wrapped past `Integer.MAX_VALUE` to a negative number;
- Live Operations' refused it with `StoredStateInvalid`, and had no `next()`.

No user ever types an attempt's number. It comes from stored state, or from an event's `int`, which
Customers & Orders raised. So the value means the same in all three contexts, and its rule has no
context's behaviour in it. [ADR-097] lets the kernel hold what every module uses and no context owns.

**Invisible fillers in names and addresses.** [ADR-106] trimmed whitespace, space separators and
format characters from both ends of free text, and treated text made only of characters that render
as nothing as empty. Some characters that render as nothing are none of those three: the Hangul
fillers U+115F, U+1160, U+3164 and U+FFA0, the combining grapheme joiner U+034F, and the blank
Braille pattern U+2800. They were neither trimmed nor refused, so "Van 1" followed by U+3164 was
accepted beside "Van 1" as a second vehicle ([BR-48]), and an address ending in U+3164 made a
duplicate customer. Access's `EmailAddress` already refuses the Hangul fillers, because two
addresses would look alike. A security review of the first fix found two more holes of the same
kind: the Khmer inherent vowels U+17B4 and U+17B5, default-ignorable but not format characters,
passed inside a name; and a variation selector after a letter or a digit was kept, so "Van 1"
followed by U+FE0F was a second name beside "Van 1", though both look the same. A second review
found that U+FE0F after an emoji that already shows as an emoji, such as U+1F600 or the truck
U+1F69A, changes nothing either, so "Van" with that emoji and U+FE0F was a second name too; and
that U+FE0E after an emoji that already shows as text, such as U+2764, changes nothing.

## Decision

On 2026-11-15 the owner, Naim Elijah, decided:

1. **`Attempt` is a shared-kernel value.** `com.dispatchflow.shared.Attempt` replaces the three
   copies in Customers & Orders, Planning and Live Operations. It is numbered from 1, with `FIRST`,
   an overflow-checked `next()` and a natural order. It has one refusal rule, in the kernel's style:
   a number below 1 is refused with `SharedKernelError.InvalidAttempt`, inside a `RuleViolation`. A
   stored number is rebuilt through `StoredStateInvalid.whileRestoring`, so a damaged row is an
   incident, as for every other stored kernel value. Planning's `InvalidAttempt` is removed. Events
   keep carrying the attempt as an `int` ([FR-258], [DL §7.3][DL-73]). This extends [ADR-097]: the
   kernel's shared values are the nine that [DL §2.3][DL-23], rule 5, lists, plus `Attempt`, and the
   exception to rule 1 that each context models its own copy no longer applies to it.
2. **Names and addresses drop invisible fillers.** `PlainText` trims from both ends every code
   point it counts as invisible: whitespace, space separators, format characters, Unicode's
   default-ignorable code points and U+2800. Inside a `NAME` or an `ADDRESS`, `PlainText` refuses
   with `TextHasForbiddenCharacters` every default-ignorable code point that is not a format
   character (category Cf), such as U+034F, U+115F, U+1160, U+17B4, U+17B5, U+3164 and U+FFA0, and
   U+2800. Format characters keep [ADR-106]'s rule, which allows U+061C and U+200C to U+200F inside
   a name or an address. A `NOTE` keeps them all inside ([BR-48], [FR-398]).

   A variation selector belongs to the character before it. In a `NAME` or an `ADDRESS` only one
   selector stays: U+FE0F, right after a base that shows as text unless U+FE0F follows it. That is
   a base for which the JDK's `Character.isEmoji` is true and `Character.isEmojiPresentation` is
   false, and, to leave out the keycap bases, `Character.isExtendedPictographic` is true. So
   "Dana ❤️" keeps its emoji presentation, and so does "©️". Every other selector changes nothing a
   reader sees: one after a letter or a digit; U+FE0F after an emoji that already shows as one,
   such as U+1F600; U+FE0E, the text selector, after any base; a second after an emoji; or VS1 to
   VS14, a Mongolian or an ideographic selector. It is refused inside the text and trimmed at the
   ends, so "Van 1" followed by U+FE0F equals "Van 1", "Van" with U+1F69A and U+FE0F equals it
   without the selector, and an emoji with two selectors equals the emoji with its first
   ([BR-48]). The digits, `#` and `*` are emoji to `Character.isEmoji` only as keycap bases: with
   U+FE0F and no keycap mark they show as themselves, so they keep no selector. A `NOTE` keeps
   every variation selector inside it, and the whole run at its end after any visible character;
   at its end a selector run is trimmed only together with an invisible character it follows. In
   every kind a leading selector is trimmed.

   The kernel's rule decides only what text is kept. A module that keeps names unique compares
   them in a matching form of its own, as Fleet does for [BR-48]'s vehicle names: NFC, with
   every variation selector and format character removed and each run of whitespace one space.
   So "Van" with a left-to-right mark, or with a no-break space for its space, is the same vehicle
   name as "Van" without it, while each keeps its text as entered.

   This extends [ADR-106], step 4 (what is trimmed) and step 7 (what is refused). Its step 6 needs
   no check of its own any more: text made only of invisible characters is trimmed to empty.

## Consequences

- **Easy:** one `Attempt`, one error code for both apps to translate, and a `next()` that can't
  wrap. A context that gains attempts later uses the kernel's.
- **Easy:** "Van 1" with a trailing or leading filler equals "Van 1", so [BR-48]'s uniqueness check
  and the customer match see one vehicle and one address. One with a filler inside is refused, with
  the error that already names the field.
- **Hard:** the kernel grows by one value, and a change to `Attempt` now needs every module owner's
  agreement ([ADR-097]).
- **Hard:** a name or an address that really uses a filler or the grapheme joiner inside it, such
  as old Hangul written with conjoining jamo, or Hebrew with the joiner between two marks, is
  refused. A note still takes it.
- **Hard:** a name or an address with a variation selector after a base that is not an emoji is
  refused: a keycap emoji such as "1️⃣" (a digit, U+FE0F, U+20E3), a Mongolian free variation
  selector, or an ideographic variation selector after a CJK ideograph. A note still takes it.
- **Hard:** a name or an address can't ask for the text presentation of an emoji that shows as
  one by default, with U+FE0E inside it. Some platforms draw such a sequence as text, so a real
  name may be refused. A note still takes it.
- **Cost:** none. Both rules use only the JDK.
- **Risk:** stored names and addresses that hold a filler inside fail to load as damaged state once
  this rule ships. No module stores text yet, so there is nothing to migrate.

## Alternatives considered

- **Keep three copies of `Attempt` and align them.** Rejected: the copies had already drifted three
  ways, and nothing would stop them drifting again. The value holds no context's behaviour.
- **Refuse a stored attempt below 1 with `StoredStateInvalid` in the constructor,** as Orders and
  Live Operations did. Not chosen: the number also arrives from an event's `int`, which is not
  stored state, and every other kernel value refuses with a `SharedKernelError` and leaves stored
  paths to `whileRestoring`.
- **Refuse every default-ignorable code point inside a name or an address.** Rejected: the variation
  selectors are default-ignorable, and an emoji in a name needs them. Only the selectors that follow
  an emoji are kept.
- **Trim every trailing variation selector too.** Rejected: "Dana ❤️" would lose its emoji
  presentation. Only one that follows no emoji is trimmed.
- **Keep a variation selector after any visible character in a name or an address.** Rejected:
  after a letter or a digit it renders as nothing, so "Van 1" with U+FE0F was a second vehicle
  beside "Van 1" ([BR-48]).
- **Keep U+FE0E and U+FE0F after any base that `Character.isExtendedPictographic` accepts,** as
  this decision first had it. Rejected: U+FE0F after an emoji that already shows as one, and
  U+FE0E after one that already shows as text, change nothing, so each made a second name.
- **Keep U+FE0F after every base for which `Character.isEmoji` is true and
  `Character.isEmojiPresentation` false.** Rejected: that set holds the digits, so "Van 1"
  followed by U+FE0F would again be a second name beside "Van 1".
- **Make the kernel's text the uniqueness key.** Rejected: the left-to-right mark and the no-break
  space are needed in Hebrew and mixed-direction text, so the kernel keeps them, and only a
  module's matching form drops them.
- **Fold the fillers into the format-character rule.** Not chosen: they are not format characters,
  and a note allows format characters for reasons of its own. `TextKind` names the filler rule on
  its own, `allowsInvisibleFillers()`.

## Revisit when

- A real name or address is refused for a filler or a grapheme joiner inside it, or for a
  variation selector after a base that is not an emoji, such as a keycap, or for U+FE0E after an
  emoji.
- A context needs an attempt with a rule of its own, such as a cap on put-backs.
- The JDK moves to a newer Unicode version, which may add invisible fillers or variation
  selectors.

[ADR-097]: ADR-097-the-shared-kernel-holds-the-domains-building-blocks.md
[ADR-106]: ADR-106-free-text-is-normalized-and-capped-by-one-kernel-rule.md
[BR-48]: ../requirements/requirements.md#access
[DL-23]: ../architecture/domain_layer/domain_layer_design.md#23-rules-between-contexts
[DL-73]: ../architecture/domain_layer/domain_layer_design.md#73-shape-and-delivery
[FR-258]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-398]: ../requirements/requirements.md#customers-and-orders
