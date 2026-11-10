# ADR-031: Messages to recipients go by SMS through an Israeli provider, from a two-way virtual number

- **Status:** Accepted
- **Date:** 2026-11-10

## Context

Avi Segal, the seminar mentor, asked on 2026-11-10 for messages to the people who receive
deliveries: one about 15 minutes before arrival, others at useful moments, and a rating request
after delivery that is answered with a number from 1 to 10 ([FR-49], [FR-351] to [FR-363]).

At 150 deliveries a day, 22 days a month and about 3.5 messages a delivery, the company sends about
11,500 messages a month, or about 17,250 segments at 1.5 segments a message, a margin over the one
segment that [FR-382] aims for. That is an estimate, not a measurement. Prices seen on 2026-11-10, from provider pages and third-party copies of rate
cards, not quotes, converted at the ARD's ₪3.17 to the dollar and before VAT of 18%:

| Channel | Price | A month at the estimate |
|---|---|---|
| SMS through an Israeli provider | ₪0.02 to ₪0.05 a message ([TextMe], [Teleco]) | ₪350 to ₪870 |
| SMS through Twilio | $0.2575 a segment ([Twilio]) | about ₪14,000 |
| WhatsApp Business Platform, Israel | $0.0053 a utility template, $0.0353 a marketing one ([Srileo]) | about ₪190, or about ₪500 if the rating request counts as marketing |

Hebrew is sent as Unicode: one message holds 70 characters, and each segment of a longer one 67
([Exotel]). A sender shown as a name cannot receive replies, and the rating needs replies.
WhatsApp needs the company's own Meta business verification, and the company is not identified yet
([Q-01]). [NFR-52] capped the running cost at ₪300 a month, and the ARD estimates the server at
₪155 to ₪230 of it.

## Decision

- **SMS only,** through an Israeli provider, which [Q-96] chooses against the criteria it lists.
- **From a two-way virtual number,** so that replies reach DispatchFlow, and only through
  authenticated provider calls ([NFR-192], [NFR-199] to [NFR-201]).
- **Behind ports in `core`** ([ADR-001]): a driven adapter implements the outbound messaging port,
  and a driving adapter receives the provider's calls, checks them and hands replies to a use case,
  so no provider type reaches `domain`.
- **Fixed wording** per message type, in the company's default interface language, with no links
  ([FR-349]). With a company name of up to 19 characters and a dispatch phone of up to 11, every
  draft below fits one 70-character message ([FR-382]).
- **Off until an Admin turns it on** ([FR-350]), so the demo and the Alpha need no SMS account. The
  rating question has its own switch, off until legal advice answers [Q-95] ([FR-375]).
- **Only Israeli mobile numbers,** stored in one form ([FR-377], [FR-378]), and at most 1,000
  messages a day ([NFR-196]).
- **The SMS bill counts toward [NFR-52],** which rises to ₪1,200 a month.
- **Not decided here:** how the triggers in orders, planning and execution reach the messaging code
  (events or calls, an outbox table, the scheduler that evaluates [FR-352]). That design must keep
  [NFR-189] to [NFR-191] and the one-way dependencies of [ADR-001], and it comes with the messaging
  code.

| Message | Draft wording |
|---|---|
| Out for delivery ([FR-351]) | {company}: המשלוח בדרך ויגיע היום בין 10:30–11:30 |
| Arriving soon ([FR-352]) | {company}: הנהג יגיע אליך בעוד כ-15 דקות |
| Time changed ([FR-353]) | {company}: זמן ההגעה עודכן: היום בין 12:00–13:00 |
| Dropped, moved or cancelled ([FR-354]) | {company}: חל שינוי במשלוח. לפרטים: {dispatch phone} |
| Missed you ([FR-355]) | {company}: לא הצלחנו למסור את המשלוח. לפרטים: {dispatch phone} |
| Delivered, rating question off ([FR-356]) | {company}: המשלוח נמסר (ליד הדלת). |
| Delivered, rating question on ([FR-356]) | {company}: המשלוח נמסר (ליד הדלת). איך היה? השב/י במספר 1–10 |
| Thank you ([FR-358]) | {company}: תודה על המשוב! |
| Format reminder ([FR-359]) | {company}: נא להשיב במספר בין 1 ל-10 בלבד |
| Opt-out confirmation ([FR-363]) | {company}: הוסרת, ולא יישלחו אליך עוד הודעות |

## Consequences

- **Easy:** one integration that reaches every phone, set up in days, with no review by Meta. A
  WhatsApp adapter can be added later behind the same port.
- **Hard:** an endpoint that takes calls without an access token ([NFR-01], [NFR-192]); sending at
  most once when a provider call times out ([NFR-191], [NFR-198]); keeping opt-outs past every
  retention period ([NFR-202]); keeping Hebrew wording within 70 characters, because a longer
  message costs twice as much.
- **Cost:** ₪350 to ₪870 a month at the estimate above, or ₪410 to ₪1,030 with VAT. [NFR-52] rose
  to ₪1,200, and [Q-62] is reopened, because the team's guess of what the company will pay is ₪200
  to ₪300. The client contract gains operations for the switches, ratings, comments and opt-outs
  ([ADR-006]).
- **Risk:** with VAT and the server, the cap holds only below about ₪0.045 a segment before VAT
  ([Q-96]), and a month at the full 200 stops a day ([NFR-05]) breaks it at that price.
- **Risk:** the company may refuse the cost ([Q-62]), or legal advice may find that the rating
  request needs consent ([Q-95]), in which case its switch stays off.
- **Risk:** recipients see a phone number as the sender, not the company's name, and SMS phishing is
  common, so a message may be ignored. A reply's sending number can be faked, so a reply alone never
  justifies an action about a driver ([Q-42]).
- **Note:** this meets the revisit trigger "the budget allows SMS" of [ADR-030]. Drivers' sign-in
  stays as ADR-030 decided, because [FR-200] keeps a driver's phone out of recovery for security,
  not for cost.

## Alternatives considered

- **WhatsApp only.** Rejected: it waits for the company's Meta verification ([Q-01]), Meta approves
  every template, a recipient without WhatsApp gets nothing, Meta can pause a number with a poor
  quality rating, and phone numbers go to Meta ([Q-42]). It costs less, about ₪190 a month.
- **WhatsApp first, SMS as the fallback.** Rejected for now: two integrations, fallback and
  de-duplication rules, and every blocker of WhatsApp.
- **Both channels, with the company choosing one.** Rejected: twice the integration and testing for
  a choice made once.
- **SMS through Twilio.** Rejected: 16 to 40 times the price of an Israeli provider.
- **Email,** which [FR-49] offered before. Rejected: DispatchFlow holds a phone for each customer
  ([FR-59]) but no email address, and email is read too late for a 15-minute warning.
- **A sender shown as the company's name.** Rejected: it cannot receive replies.
- **A rating page opened from a link.** Rejected: a public page falls under IS 5568 ([Q-59]), and
  links in SMS look like phishing.

## Revisit when

- The company passes Meta's business verification and asks for WhatsApp.
- The SMS bill passes ₪870 a month before VAT, the top of the estimate.
- Legal advice says the rating request needs consent ([Q-95]).

[ADR-001]: ADR-001-domain-driven-design-with-hexagonal-architecture.md
[ADR-006]: ADR-006-versioned-openapi-contracts.md
[ADR-030]: ADR-030-admin-issued-credentials-and-token-revocation.md
[FR-49]: ../requirements/requirements.md#messages-to-recipients
[FR-59]: ../requirements/requirements.md#customers-and-orders
[FR-200]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-349]: ../requirements/requirements.md#messages-to-recipients
[FR-350]: ../requirements/requirements.md#messages-to-recipients
[FR-351]: ../requirements/requirements.md#messages-to-recipients
[FR-352]: ../requirements/requirements.md#messages-to-recipients
[FR-353]: ../requirements/requirements.md#messages-to-recipients
[FR-354]: ../requirements/requirements.md#messages-to-recipients
[FR-355]: ../requirements/requirements.md#messages-to-recipients
[FR-356]: ../requirements/requirements.md#messages-to-recipients
[FR-358]: ../requirements/requirements.md#messages-to-recipients
[FR-359]: ../requirements/requirements.md#messages-to-recipients
[FR-363]: ../requirements/requirements.md#messages-to-recipients
[FR-375]: ../requirements/requirements.md#messages-to-recipients
[FR-377]: ../requirements/requirements.md#messages-to-recipients
[FR-378]: ../requirements/requirements.md#messages-to-recipients
[FR-382]: ../requirements/requirements.md#messages-to-recipients
[NFR-01]: ../requirements/requirements.md#nfr-01
[NFR-05]: ../requirements/requirements.md#nfr-05
[NFR-52]: ../requirements/requirements.md#nfr-52
[NFR-189]: ../requirements/requirements.md#nfr-189
[NFR-191]: ../requirements/requirements.md#nfr-191
[NFR-192]: ../requirements/requirements.md#nfr-192
[NFR-196]: ../requirements/requirements.md#nfr-196
[NFR-198]: ../requirements/requirements.md#nfr-198
[NFR-199]: ../requirements/requirements.md#nfr-199
[NFR-201]: ../requirements/requirements.md#nfr-201
[NFR-202]: ../requirements/requirements.md#nfr-202
[Q-01]: ../requirements/requirements.md#open-questions
[Q-42]: ../requirements/requirements.md#open-questions
[Q-59]: ../requirements/requirements.md#open-questions
[Q-62]: ../requirements/requirements.md#open-questions
[Q-95]: ../requirements/requirements.md#open-questions
[Q-96]: ../requirements/requirements.md#open-questions
[Exotel]: https://developer.exotel.com/docs/sms-support/unicode-sms
[Srileo]: https://srileo.com/tools/whatsapp-api-pricing-calculator/israel/utility/
[Teleco]: https://www.teleco.co.il/%D7%9E%D7%A2%D7%A8%D7%9B%D7%AA-%D7%9C%D7%A9%D7%9C%D7%99%D7%97%D7%AA-%D7%94%D7%95%D7%93%D7%A2%D7%95%D7%AA-sms/
[TextMe]: https://textme.co.il/%D7%9E%D7%97%D7%99%D7%A8%D7%95%D7%9F-sms/
[Twilio]: https://www.twilio.com/en-us/sms/pricing/il
