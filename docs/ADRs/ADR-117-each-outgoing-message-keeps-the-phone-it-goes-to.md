# ADR-117: Each outgoing message keeps the phone it goes to, and the opt-out word is read from a reply's first 500 code points

- **Status:** Accepted
- **Date:** 2026-11-15

## Context

[ADR-108] built `StopMessages` with one phone per stop: each trigger brings the phone that
[FR-348] chooses at that moment, and a message added by a later trigger replaced the stop's phone.
A review of the whole domain layer found two defects in that shape, and one in how a reply is read:

- **A pending message went to the wrong phone.** A stop's "out for delivery" is pending for
  052-…, then the order's recipient phone changes and "your delivery changed" is triggered for
  050-…. The stop's phone became 050-…, so the pending message would be sent there, to a number it
  was never triggered for ([FR-348]).
- **The history of sent messages changed with it.** The messages already accepted read as if they
  went to 050-…. [FR-363] sends the opt-out confirmation only to "a phone messaged in the last 48
  hours", which needs to know which phone each message went to. A rating question accepted after
  the phone changed was opened for the stop's latest phone, not the one the question went to
  ([FR-356], [FR-357]).
- **The opt-out word had no length cap.** [ADR-108] point 4 reads the reply's first token, split at
  whitespace, put in NFKC, with emoji, symbols, format characters and punctuation removed. The
  token is everything up to the first space, so a first "word" of many thousands of combining marks
  was normalized whole, at a cost in CPU that the sender pays per reply. `Comment.cutFrom` already
  cuts a comment to 500 code points before it normalizes it ([NFR-203]).

## Decision

On 2026-11-15 the owner, Naim Elijah, decided, and accepted the review's fix for the opt-out word:

1. **Each outgoing message keeps the phone it goes to, set when it is created.** The phone is the
   one [FR-348] chose for the trigger that added the message, and it was an Israeli mobile then
   ([FR-378]). The sender sends each message to its own phone, and a "delivered" message that asks
   for a rating opens its rating request for that phone ([FR-356]). A later trigger that names
   another phone adds its own message there and changes no earlier message. `StopMessages` keeps no
   phone of its own: its snapshot holds each message's phone, which is personal data like the
   message's text ([NFR-193]). A message with no phone can't be built, so a stored one is damaged
   state. This amends [ADR-108], whose `StopMessages` kept the phone of the latest message.
2. **The opt-out word is read from the first 500 code points of the reply's first token.** The
   token is cut at 500 code points, so no surrogate pair is split, before it is put in NFKC; the
   rest of [ADR-108] point 4 is unchanged ([FR-363]). So "STOP" after 496 punctuation marks still
   opts out, and after 497 it does not: its last letter is past the cut. 500 is the comment's cap
   ([NFR-203], `Comment.MAXIMUM_LENGTH`), so no part of a reply is read past it. This amends
   [ADR-108] point 4.

## Consequences

- **Easy:** a message is sent to the phone it was triggered for, whatever later triggers bring.
- **Easy:** which phone was messaged, and when, is one read of the stop's messages, which
  [FR-363]'s 48 hours need.
- **Easy:** reading a reply costs at most 500 code points of normalization, however long it is.
- **Hard:** the same number is stored once per message rather than once per stop, a handful of
  copies per stop until the stop's messages go at the end of their retention ([NFR-184]).
- **Hard:** a recipient who writes 497 or more ignored characters before "STOP" in one token is not
  opted out by that reply.
- **Cost:** the persistence adapter, built with its milestone, maps one phone column per message.
- **Risk:** a stored message with no phone can't be rebuilt, so a damaged row stops its stop's
  messages from loading until it is repaired.

## Alternatives considered

- **Keep the stop's phone, and send every message to it.** Rejected: a pending message would go to
  a number it was never triggered for, and the record of where each message went would be lost.
- **Keep the stop's phone beside each message's.** Rejected: the two can disagree, so the
  aggregate would need an invariant to keep them in step, and the stop's phone would answer no
  question the messages' phones don't.
- **Check at restore that each stored phone is an Israeli mobile.** Rejected: a later
  libphonenumber release could reclassify a stored number and stop the stop's messages from
  loading, which `PhoneNumber.ofStored` exists to prevent. The rule is checked when the message is
  added.
- **Cut the opt-out token after normalizing it.** Rejected: normalizing is the cost the cut avoids.
- **Refuse a first token over 500 code points.** Rejected: a reply is never the recipient's mistake
  to fix ([NFR-201]).

## Revisit when

- A message must be re-sent to a phone the recipient corrected after it was triggered.
- Recipients are found writing long runs of emoji or punctuation before an opt-out word.

[ADR-108]: ADR-108-messaging-appends-opt-ins-holds-its-fixed-wording-and-cuts-comments-to-500-characters.md
[FR-348]: ../requirements/requirements.md#messages-to-recipients
[FR-356]: ../requirements/requirements.md#messages-to-recipients
[FR-357]: ../requirements/requirements.md#messages-to-recipients
[FR-363]: ../requirements/requirements.md#messages-to-recipients
[FR-378]: ../requirements/requirements.md#messages-to-recipients
[NFR-184]: ../requirements/requirements.md#nfr-184
[NFR-193]: ../requirements/requirements.md#nfr-193
[NFR-201]: ../requirements/requirements.md#nfr-201
[NFR-203]: ../requirements/requirements.md#nfr-203
