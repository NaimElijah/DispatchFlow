# ADR-079: Client errors and crashes go to GlitchTip on the VM, through the Sentry SDKs

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

[NFR-74] requires errors in both clients and crashes of the phone app to reach the team with the
app version and the trace id. [NFR-163] requires those reports to carry no personal data,
credentials or tokens. The row carries the marker "ADR pending: client error and crash reporting".

The tech stack proposed Sentry's hosted service for both apps, "the best client error tracking with
no infrastructure". That needed an accepted exception to [NFR-209], because the reports would leave
Israel and the data region was unconfirmed. It named self-hosted GlitchTip as the fallback if the
exception was refused ([TS §10], [TS §11], [TS §13], [TS §22]).

On 2026-11-13 the owner, Naim Elijah, decided for the team not to ask for the exception. Reports go
to GlitchTip on the VM ([ADR-078]).

## Decision

- **Server:** GlitchTip runs on the VM, as its own container with its own database ([TS §15]).
  Reports stay in Israel, so no [NFR-209] exception is needed. The data inventory's list of services
  that receive company data names crash reporting ([NFR-217]), and here it is held in Israel.
- **Web app:** the Sentry browser SDK, pointed at GlitchTip, with personal data off ([TS §10]).
  Source maps upload from CI to GlitchTip.
- **Phone app:** the Sentry React Native SDK, pointed at GlitchTip, with personal data off ([TS §11]).
  Source maps upload from EAS.
- **Every report carries the app version and the trace id,** the same id as in the logs and audit
  records ([NFR-20], [NFR-74]).
- **The Content Security Policy's `connect-src` names GlitchTip's address,** instead of a Sentry
  host ([ADR-069]).
- **Checked at Pilot:** the source-map upload from CI and from EAS is tested against GlitchTip's
  version. This is the one point the sources leave unverified.
- **Scrubbing:** in both apps, `beforeSend` and `beforeBreadcrumb` remove URL fragments and query
  strings from every event and breadcrumb, because a link can carry a token ([NFR-163]).
- **Exposure:** Caddy exposes only GlitchTip's ingest paths, rate-limited. The GlitchTip UI is
  reached through SSM port forwarding, and not from the internet ([TS §15]).
- **Retention:** GlitchTip keeps events 7 days, like logs, traces and metrics ([NFR-76], [ADR-075]).

## Consequences

- **Easy:** no outside service receives client errors, so the choice adds no row to the list of
  exceptions in [ADR-077]. The apps keep the standard Sentry SDKs, which GlitchTip accepts, so hosted
  Sentry stays a change of address away ([TS §10]).
- **Hard:** the team runs, patches and backs up one more service on a single VM ([NFR-14]).
  GlitchTip is not in the memory table of [PL §23], so its share of the 16 GB is not budgeted. M3's
  measurement must include it.
- **Hard:** source-map upload to a server that is not Sentry's own is the least proven part. A broken
  upload gives stack traces nobody can read.
- **Cost:** no licence fee is stated in the sources. The cost is the VM's memory, disk and the team's
  time.
- **Risk:** [NFR-163] depends on SDK settings in two apps. A missing "personal data off" setting
  sends names or addresses into a database. The sources do not route client reports through the
  collector's redaction processor ([NFR-19]), so the SDK settings and the scrubbing hooks are the only controls.

## Alternatives considered

- **Hosted Sentry, as an accepted exception to [NFR-209].** Rejected: reports would leave Israel, and
  its data region is unconfirmed ([TS §13]). Its advantage is no infrastructure to run.
- **No client error reporting.** Rejected: [NFR-74] requires it.

## Revisit when

- The source-map upload fails against GlitchTip at the Pilot check.
- M3's memory measurement shows the 16 GB VM cannot carry GlitchTip with the rest of the stack.
- A GlitchTip security release goes unapplied for more than 14 days, or a backup of it is missed.
- GlitchTip stops accepting the Sentry SDKs' events after an SDK upgrade.
- A hosted Sentry region in Israel is confirmed, and the team no longer wants to run the service.

[ADR-069]: ADR-069-sign-in-and-transport-security.md
[ADR-075]: ADR-075-retention-deletes-records-and-anonymizes-people-in-place.md
[ADR-077]: ADR-077-push-builds-and-app-stores-are-accepted-exceptions-to-hosting-in-israel.md
[ADR-078]: ADR-078-hosting-on-one-aws-vm-in-tel-aviv-built-with-terraform.md
[NFR-14]: ../requirements/requirements.md#nfr-14
[NFR-19]: ../requirements/requirements.md#nfr-19
[NFR-20]: ../requirements/requirements.md#nfr-20
[NFR-74]: ../requirements/requirements.md#nfr-74
[NFR-76]: ../requirements/requirements.md#nfr-76
[NFR-163]: ../requirements/requirements.md#nfr-163
[NFR-209]: ../requirements/requirements.md#nfr-209
[NFR-217]: ../requirements/requirements.md#nfr-217
[TS §10]: ../architecture/tech_stack.md#10-web-app
[TS §11]: ../architecture/tech_stack.md#11-phone-app
[TS §13]: ../architecture/tech_stack.md#13-outside-services-and-where-data-goes
[TS §15]: ../architecture/tech_stack.md#15-infrastructure-and-operations
[TS §22]: ../architecture/tech_stack.md#22-open-decisions
[PL §23]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#23-deployment-and-operations
