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
addresses would look alike.

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
   default-ignorable code points and U+2800. A variation selector belongs to the character before
   it, so at the end it is trimmed only together with an invisible character it follows, and an
   emoji keeps its presentation. Inside a `NAME` or an `ADDRESS`, `PlainText` refuses U+034F,
   U+115F, U+1160, U+2800, U+3164 and U+FFA0 with `TextHasForbiddenCharacters`. A `NOTE` keeps them
   inside, and no kind refuses a variation selector, because emoji need them ([BR-48], [FR-398]).
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
  selectors are default-ignorable, and an emoji in a name needs them.
- **Trim a trailing variation selector too.** Rejected: "Dana ❤️" would lose its emoji
  presentation.
- **Fold the fillers into the format-character rule.** Not chosen: they are not format characters,
  and a note allows format characters for reasons of its own. `TextKind` names the filler rule on
  its own, `allowsInvisibleFillers()`.

## Revisit when

- A real name or address is refused for a filler or a grapheme joiner inside it.
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
