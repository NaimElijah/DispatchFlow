# ADR-108: Messaging appends opt-ins to the opt-out history, holds its fixed wording, and cuts comments to 500 characters

- **Status:** Accepted; extended by ADR-121; amended by ADR-117
- **Date:** 2026-11-14

## Context

The Recipient Messaging domain is built ahead of its milestone ([DL §4.6][DL-46]). Its three
aggregates are `StopMessages`, keyed by the stop, `RatingRequest` and the singleton
`MessagingSettings`; opt-outs and inbound replies are records ([DL §3.2][DL-32]). The requirements
fix most of its rules ([FR-348] to [FR-384], [NFR-190], [NFR-191], [NFR-196], [NFR-198]), and five
were left open:

- **Removing an opt-out.** [FR-380] lets a dispatcher remove an opt-out at the person's request, and
  [NFR-202] keeps every opt-out until the company leaves. Neither says how a removal is kept.
- **The English wording.** [FR-349] fixes the wording per message type in the company's default
  language. [ADR-031] drafted the Hebrew only, and [ADR-089] makes English the second language.
- **The quarter hour.** [FR-351] rounds the projected arrival "to the nearest quarter hour" without
  saying which way a time exactly between two goes.
- **The opt-out word and "8 thanks".** [FR-363] reads the reply's "first word", and [FR-360] keeps
  every reply "other than a bare rating" as a comment, so a rating followed by words is unclear.
- **Retention.** [NFR-184] keeps messages and replies 90 days, and the domain document lists the
  periods in one place for every module ([DL §10.3][DL-103]).

A sixth point is a gap in the kernel: [NFR-203] keeps a comment "cut to 500 characters", while the
kernel's `PlainText` refuses a note over its cap of 500, and [ADR-106] left the cut for recipients'
comments to Messaging.

## Decision

On 2026-11-14 the owner, Naim Elijah, decided the open rules:

1. **Removing an opt-out appends an opt-in.** A phone's history is a list of entries, never
   changed: an opt-out by an opt-out word ([FR-363]), an opt-out a dispatcher records, or an opt-in
   a dispatcher makes ([FR-380]). Each entry keeps the phone's keyed hash, when, and for a
   dispatcher's entry who did it ([NFR-202]). The latest entry for the phone decides whether it may
   be messaged, and point 5 says which entry is the latest. `OptOutStatus` reads the history; an
   opt-in with no dispatcher can't be built or restored.
2. **The wording is fixed per message type, in the company's default language.** The Hebrew is
   ADR-031's, as written. The English below was drafted for the owner, who approved it as written
   on 2026-11-15: it is short, plain, and keeps the Hebrew's meaning. Every text starts with the company's name and a colon. With a
   name of 19 characters and a dispatch phone of 11, the longest text, "delivered at the door,
   asking for a rating" in Hebrew, is exactly 70 characters ([FR-382]); `MessageTextTest` checks
   every wording at those limits.

   | Message | English |
   |---|---|
   | Out for delivery ([FR-351]) | {company}: Your delivery arrives today between 10:30–11:30 |
   | Arriving soon ([FR-352]) | {company}: The driver will reach you in about 15 minutes |
   | Time changed ([FR-353]) | {company}: Arrival time updated: today between 12:00–13:00 |
   | Dropped, moved or cancelled ([FR-354]) | {company}: Your delivery has changed. Details: {dispatch phone} |
   | Missed you ([FR-355]) | {company}: We could not deliver it. Details: {dispatch phone} |
   | Delivered, rating question off ([FR-356]) | {company}: Delivered (at the door). |
   | Delivered, rating question on ([FR-356]) | {company}: Delivered (at the door). How was it? Reply 1–10 |
   | Thank you ([FR-358]) | {company}: Thank you for your feedback! |
   | Format reminder ([FR-359]) | {company}: Please reply with a number from 1 to 10 only |
   | Opt-out confirmation ([FR-363]) | {company}: You are removed and will get no more messages |

   The texts live in the domain, in `MessageText`, because the domain document makes a message's
   text a rule of that value ([DL §3.7][DL-37]) and the message entity holds the text it was
   triggered with ([DL §4.6][DL-46]). `Wording` names each fixed text with the facts it gives.
3. **Small readings.**
   - A projected arrival exactly halfway between two quarter hours rounds up: 10:07:30 gives 10:15
     ([FR-351]). Israel's offsets are whole hours, so rounding the instant rounds the wall clock.
   - "8 thanks" is both the rating 8 and a comment, and the comment is the whole reply ([FR-357],
     [FR-360]).
   - Messaging owns its retention periods: messages and replies, comments included, are kept 90
     days from sending or receipt ([NFR-184], [NFR-193]), in `MessagingRetention`. Each comment's 90
     days run from its own receipt, and the rating request's phone goes 90 days after its question
     was sent.
4. **The opt-out word is read widely.** The reply's first token, split at whitespace, is put in
   NFKC; then emoji, other and modifier symbols (So, Sk), format characters (Cf) and punctuation
   are removed, and it is compared without letter case ([FR-363]). So "Stop!", "STOP😡", "הסר🙏",
   "STOP" followed by a right-to-left mark, and a full-width "ＳＴＯＰ" all opt out; "stop,please"
   and "STOP1" do not. Emoji are found by `Character.isExtendedPictographic`, which leaves digits
   alone and covers emoji newer than the JDK's Unicode.
5. **A STOP processed last wins.** The latest entry of a phone's history is the one appended last,
   whatever its time. A STOP received at 09:00 but processed after a dispatcher's 10:00 opt-in opts
   the phone out, and gets the confirmation when the phone was messaged in the 48 hours before it
   ([FR-363], [FR-380]).
6. **A rating request keeps at most 20 comments,** a starting value. Once it keeps 20, every later
   reply is ignored, a rating included, and gets no answer ([FR-360]).

The requirements already decide these, and the code follows them: a correction sends no message
([FR-374]); "time changed" goes when the projection is 30 or more minutes from the arrival last
sent, at most twice ([FR-353], [FR-384]); FR-373 stops every message of FR-351 to FR-354 and
FR-372; the messages switch applies to triggers after it ([FR-350]); comments are kept for 48 hours
even after the rating ([FR-360], [FR-379]); any message not accepted within 15 minutes is dropped
([NFR-190]); "time changed" and "your delivery changed" need the route started ([FR-353],
[FR-354]); the dispatch phone is written in national form of at most 11 characters ([FR-382]);
and the order's recipient phone comes before the customer's ([FR-348]).

### How a comment is cut

`Comment.cutFrom` keeps a reply as a comment of at most 500 code points, the kernel's count
([NFR-203], [ADR-106]):

1. carriage returns and CRLF pairs become line feeds, so a Windows line end counts once;
2. the ends are trimmed as the kernel trims them, so leading blanks don't use up the 500;
3. the text is cut after 500 code points, so no surrogate pair is split;
4. the cut text is normalized to NFC and cut again after 500 code points, because NFC turns some
   characters into two, such as a Hebrew letter with its dot written as one presentation form;
5. each code point the kernel refuses in a note becomes U+FFFD (see the readings);
6. the kernel's rule for a note, `PlainText.of(TextKind.NOTE, …)`, then checks it.

Nothing visible left is `BlankComment`. `Reply.parse` trims the reply before it reads it, and both
of its comment branches cut the trimmed text. A stored comment is checked, never cut: one over the
cap is damaged state.

### Readings this ADR settles

No document settles these, and the code takes the narrowest reading:

- **The dispatch phone** lives in `MessagingSettings`, as [DL §4.6][DL-46] places it. Its national
  form is libphonenumber's national format with everything but digits removed, such as
  `0523456789`, which a phone dials and links as it is. A number outside Israel is
  `DispatchPhoneNotIsraeli`, since in national form it can't be dialled from Israel, and one whose
  national form is longer than 11 characters, such as a 12-digit voicemail number, is
  `DispatchPhoneTooLong`. The settings are created with it, because two messages can't be written
  without it, and both switches start off ([FR-350], [FR-375]).
- **The company name** comes from Access, and Messaging checks again what a message relies on: not
  blank and at most 19 characters, or `UnusableCompanyName`.
- **The arrival last sent** is the window of the latest "out for delivery" or "time changed" that
  was not dropped. A stop whose only arrival was dropped gets no "time changed".
- **One projection may add two messages.** "Arriving soon" and "time changed" are judged each on its
  own rows, so one call can add both; it takes a `FreshIds`.
- **Fifteen minutes** run from the trigger, and a message exactly 15 minutes old may still be
  accepted. Past them, a pending, sending or unknown message is dropped, and a claim drops it
  instead of sending it.
- **An acceptance after a stale drop is recorded.** A message dropped as stale while it was sending
  may have left. When the provider then accepts it, it becomes accepted: it opens its rating request
  as usual and counts toward the daily cap, because the fact outranks the drop ([ADR-065]). Any
  other answer for a dropped message is `MessageNotSending`.
- **The daily cap counts claims** ([NFR-196]): a message claimed today counts while sending, unknown
  or accepted, and once dropped with its outcome unknown, since each may have left.
- **A refused character in a comment becomes U+FFFD.** The comment path replaces each code point the
  kernel refuses in a note: a control other than a line feed, such as a tab; a bidi embedding,
  override or isolate control; an unpaired surrogate; and a private-use or unassigned code point,
  such as a Unicode 16 emoji. So "10 🫩" and "9 then a tab, thanks" keep their ratings and their
  comments, and no reply is refused and sent again for ever ([NFR-201]).
- **A phone that opts out after a trigger** gets nothing: the claim drops the message ([FR-363]).
- **A bare rating** is a reply whose trimmed text is the number alone; "9." is a rating and a
  comment.
- **A blank reply** within the 48 hours is neither a rating nor an opt-out, so as the first such
  reply before a rating it gets the format reminder ([FR-359]), and nothing is kept.
- **A reply's answers** (the thank-you and the reminder) and the opt-out confirmation go only while
  messages may go to the phone; one due when they can't is skipped for good, since each is sent
  once ([FR-358], [FR-359], [FR-363]). A second opt-out word from a phone already opted out adds no
  entry and no confirmation. The 48 hours of [FR-357] and of [FR-363] include their ends.
- **A rating request is closed** from 48 hours after its question was accepted, a matter of time
  rather than a stored state. Its rating stays after its phone and comments go, for the day's
  figures ([FR-362], [NFR-193]); a removal run with personal data left and none of it due is
  `RetentionNotOver`.

## Consequences

- **Easy:** an opt-out and its removal are both on record, with who and when, and a phone's standing
  is one read of its history.
- **Easy:** every message's text is a tested value: its wording, its language and its length at the
  FR-382 limits are unit tests, with no template file to keep in step.
- **Easy:** a long reply is kept cut, not lost, and a surrogate pair is never split.
- **Hard:** changing a wording, or adding a language, is a code change and a release, not a setting.
- **Hard:** a comment's refused characters are lost: a dispatcher reads U+FFFD where the recipient
  typed a tab or an emoji newer than the JDK, and the comment path repeats the kernel's list of
  refused characters, which `ReplyTest` keeps in step.
- **Hard:** a phone that sends 20 comments to one request silences it, its rating included.
- **Cost:** the opt-out history grows by one entry per change; at a pilot's volume that is a few
  rows a week.
- **Risk:** the application layer still has to decide how the thank-you, the reminder and the
  opt-out confirmation are sent, and keep them within [NFR-190], [NFR-196] and [NFR-198]; the domain
  only says which is due.

## Alternatives considered

- **Delete the opt-out on removal.** Rejected: [NFR-202] keeps opt-outs until the company leaves,
  and a deletion loses who removed it and when ([FR-380]).
- **Texts in a message catalogue outside the domain.** Rejected: the domain document puts the text
  rule on `MessageText`, and a catalogue would move the 70-character check out of the unit tests.
- **Round the quarter hour half down, or to even.** Rejected: half up is how a person reads a clock.
- **Keep only the words after the rating as the comment.** Rejected: [FR-360] keeps "each reply",
  and the whole reply is what the recipient wrote.
- **Refuse a comment over 500 characters.** Rejected: [NFR-203] says cut, and the recipient can't
  fix a refused reply.
- **Cut before line ends become line feeds.** Rejected: a Windows line end would count twice.

## Revisit when

- The company asks for its own wording.
- The company asks for a third message language.
- A dispatch phone abroad, or a short code such as `*1234`, is needed: the national form then needs
  a rule of its own.
- Dispatchers find U+FFFD in comments often enough to matter, or a recipient's rating is lost to the
  20-comment cap.
- Legal advice changes the retention of messages or comments ([NFR-184], [Q-42]).

[ADR-031]: ADR-031-sms-through-an-israeli-provider-for-recipient-messages.md
[ADR-065]: ADR-065-facts-outrank-intent.md
[ADR-089]: ADR-089-interface-languages.md
[ADR-106]: ADR-106-free-text-is-normalized-and-capped-by-one-kernel-rule.md
[DL-32]: ../architecture/domain_layer/domain_layer_design.md#32-records
[DL-37]: ../architecture/domain_layer/domain_layer_design.md#37-domain-services-none
[DL-46]: ../architecture/domain_layer/domain_layer_design.md#46-recipient-messaging
[DL-103]: ../architecture/domain_layer/domain_layer_design.md#103-every-rule-that-depends-on-time
[FR-348]: ../requirements/requirements.md#messages-to-recipients
[FR-349]: ../requirements/requirements.md#messages-to-recipients
[FR-350]: ../requirements/requirements.md#messages-to-recipients
[FR-351]: ../requirements/requirements.md#messages-to-recipients
[FR-352]: ../requirements/requirements.md#messages-to-recipients
[FR-353]: ../requirements/requirements.md#messages-to-recipients
[FR-354]: ../requirements/requirements.md#messages-to-recipients
[FR-355]: ../requirements/requirements.md#messages-to-recipients
[FR-356]: ../requirements/requirements.md#messages-to-recipients
[FR-357]: ../requirements/requirements.md#messages-to-recipients
[FR-358]: ../requirements/requirements.md#messages-to-recipients
[FR-359]: ../requirements/requirements.md#messages-to-recipients
[FR-360]: ../requirements/requirements.md#messages-to-recipients
[FR-362]: ../requirements/requirements.md#messages-to-recipients
[FR-363]: ../requirements/requirements.md#messages-to-recipients
[FR-374]: ../requirements/requirements.md#messages-to-recipients
[FR-375]: ../requirements/requirements.md#messages-to-recipients
[FR-379]: ../requirements/requirements.md#messages-to-recipients
[FR-380]: ../requirements/requirements.md#messages-to-recipients
[FR-382]: ../requirements/requirements.md#messages-to-recipients
[FR-384]: ../requirements/requirements.md#messages-to-recipients
[NFR-184]: ../requirements/requirements.md#nfr-184
[NFR-190]: ../requirements/requirements.md#nfr-190
[NFR-191]: ../requirements/requirements.md#nfr-191
[NFR-193]: ../requirements/requirements.md#nfr-193
[NFR-196]: ../requirements/requirements.md#nfr-196
[NFR-198]: ../requirements/requirements.md#nfr-198
[NFR-201]: ../requirements/requirements.md#nfr-201
[NFR-202]: ../requirements/requirements.md#nfr-202
[NFR-203]: ../requirements/requirements.md#nfr-203
[Q-42]: ../requirements/requirements.md#open-questions
