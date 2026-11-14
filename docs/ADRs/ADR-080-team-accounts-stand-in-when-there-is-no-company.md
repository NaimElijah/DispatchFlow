# ADR-080: The team's own accounts stand in when there is no company

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

[ADR-028] decided that the company owns the Apple and Google developer accounts, so distribution
waits for a named company ([Q-01]). [ADR-078] puts production on the company's AWS account. No
company is confirmed, and the team looks for one with no fixed date ([Q-01]). Synthetic data
([FR-45]) is the fallback until then.

[ADR-028] weighed "the team's own developer accounts" and did not choose them: builds could ship
before a company is named, but the app would then have to move to the company later. [NFR-186] says
the pilot's phones are reached on "the company's developer accounts".

On 2026-11-14 the owner, Naim Elijah, decided for the team what happens if there is still no
company when the pilot milestone, M13, starts. The pilot protocol ([Q-40]) then becomes a staged
pilot of 5 working days: synthetic data, classmates as drivers on their own phones, and a stand-in
dispatcher, on a production-like deployment ([FR-344]). [NFR-32] compares against the
nearest-neighbour baseline, and the report says the pilot was staged.

A staged pilot still needs somewhere to run and a way to get the app onto phones. The research
study needs machines too.

## Decision

This extends [ADR-028]. It applies only when there is no company. With a company, [NFR-186] stands
and the company's accounts are used.

- **Apple and Google:** the team's own Apple Developer Program account and Google Play account
  distribute the app through TestFlight and Google Play internal testing. The fees are about $99 a
  year (about ₪320) and $25 once (about ₪80): about ₪400 in the first year. These are the owner's
  conversions.
- **AWS, the team's account:** a team-owned account in `il-central-1` runs three things:
  - the research study's machines;
  - the usability VM, started for each study round, at a few shekels a round ([TS §14]);
  - in a staged pilot, a production-like VM: about two weeks of the 16 GB VM, about ₪250 with VAT.
- **Research machines are always in the team's account,** never the company's, even with a company.
  The estimate is 60–100 machine-hours, about ₪20–40 for the whole study. It comes from US list
  prices; the `il-central-1` price is checked when the study starts ([TS §8]).
- **With a real company, production stays on its AWS account.** Production's account holds only the
  product.

[Q-48] stays open for the rest of store distribution: review time, Apple's rules on background
location and tester accounts.

## Consequences

- **Easy:** the team can run a staged pilot and test builds on real phones without waiting for a
  company. Research costs stay off the company's AWS account, which [ADR-078] sizes for the product alone.
- **Hard:** the team's accounts hold the app. [ADR-028] named the cost: if a company appears
  afterwards, the app moves to its accounts. How an app moves between developer accounts is not
  verified.
- **Hard:** two AWS accounts to run: the company's, or the team's standing in for it, and the
  research account.
- **Cost:** about ₪400 in the first year for the two store accounts, about ₪250 for the staged
  pilot's VM, and about ₪20–40 for the research machines. All are estimates. The usability VM costs
  a few shekels per round. The sources do not say who pays them.
- **Risk:** classmates on TestFlight may count as external testers, whose builds go to App Review.
  [Q-48] raised this and it is not verified.
- **Risk:** a staged pilot proves less than a real one. The report must say it was staged
  ([NFR-32]).

## Alternatives considered

- **Wait for a company before any build ships or any pilot starts.** Rejected: the search has no fixed
  date, so M13 could pass with no pilot and no build on real phones.
- **The company's accounts only,** as [ADR-028] decided. Kept for when a company exists.
- **Android only, by direct install.** Rejected by [ADR-028]: it works only if every driver has Android.
- **Research machines on the company's AWS account.** Rejected: production's account holds only the
  product, and the study ends with the project.

## Revisit when

- A company is confirmed before M13. Production moves to its account and [NFR-186] applies.
- A company is confirmed after the staged pilot. Plan the move of the app to its accounts and check
  how an app transfers.
- Apple or Google refuses or delays the team's account, or review of classmates' builds delays the
  staged pilot.
- The team's AWS bill for a staged pilot or the study passes the estimates above.

[ADR-028]: ADR-028-driver-app-through-test-tracks-with-a-device-checklist.md
[ADR-078]: ADR-078-hosting-on-one-aws-vm-in-tel-aviv-built-with-terraform.md
[FR-45]: ../requirements/requirements.md#research-and-evaluation
[FR-344]: ../requirements/requirements.md#research-and-evaluation
[NFR-32]: ../requirements/requirements.md#nfr-32
[NFR-186]: ../requirements/requirements.md#nfr-186
[Q-01]: ../requirements/requirements.md#open-questions
[Q-40]: ../requirements/requirements.md#open-questions
[Q-48]: ../requirements/requirements.md#open-questions
[TS §8]: ../architecture/tech_stack.md#8-research-harness
[TS §14]: ../architecture/tech_stack.md#14-environments
