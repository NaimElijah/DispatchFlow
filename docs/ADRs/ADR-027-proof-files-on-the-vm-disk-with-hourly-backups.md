# ADR-027: Proof files stay on the VM's disk, and backups run hourly during operating hours

- **Status:** Accepted
- **Date:** 2026-11-04

## Context

Drivers capture photos and signatures as proof of delivery ([FR-30]). The system runs on one VM with
no redundancy ([NFR-14]), and the monthly budget is ₪300 at most ([NFR-52]). [Q-14] asked where the
files live, and [Q-77] how much acknowledged data a lost VM may lose. A daily backup could lose a
whole day of acknowledged reports, and [NFR-64] covers one service failing, not a lost VM.

The volume is small. At 300 KB a photo, 200 stops a day and 22 days a month, which is an estimate and
not a measurement, about 4 GB is held at once under the 90-day retention of [NFR-184]. [NFR-37] allows 5 MB a photo,
so the app has to shrink photos to about that size.

## Decision

- **Proof files live on the VM's disk**, behind a port ([ADR-001]). The core serves them after a role
  check ([NFR-38]).
- **The database and new proof files are copied off the VM** every hour during operating hours, and
  at least every 24 hours ([NFR-28]).
- **A restore covers both** and finishes within 4 hours ([NFR-125]).

## Consequences

- **Easy:** no new service or SDK, and the files have no outside address.
- **Hard:** the disk has a fixed size, so its use is watched ([NFR-75]) and it is resized by hand. Proofs share
  the disk with PostgreSQL, so a full disk stops both. The core's image needs a volume owned by its
  non-root user ([ADR-019]), which its Dockerfile does not have yet.
- **Cost:** a backup script and storage off the VM, which the budget has to cover. No provider has
  been priced.
- **Risk:** losing the VM loses up to an hour of reports and photos during operating hours.
- **Risk:** the backup store holds every signature and the whole database, so its credential, encryption
  and lifetime matter as much as a bucket's would. They are not set yet ([Q-14], [Q-92]).

## Alternatives considered

- **Object storage (S3-style).** Rejected for now: files would survive losing the VM and space would
  grow by itself, but it adds a bucket, a credential, an SDK dependency and a test container, and a
  misconfigured bucket could expose signatures. Moving later means one new adapter and a copy job.
- **Daily backups only.** Rejected: losing the VM could lose a whole day's reports and photos.
- **Continuous archiving of the database log.** Rejected: at most minutes of database changes lost,
  but more setup and more to restore, and proofs would still be hourly.

## Revisit when

- Losing an hour of proofs stops being acceptable, for example under a contract with the company.
- More than one server has to read the files.
- The disk outgrows what the budget's VM offers.

[ADR-001]: ADR-001-domain-driven-design-with-hexagonal-architecture.md
[ADR-019]: ADR-019-container-images-run-as-non-root.md
[FR-30]: ../requirements/requirements.md#publishing-and-the-driver-app
[NFR-14]: ../requirements/requirements.md#nfr-14
[NFR-28]: ../requirements/requirements.md#nfr-28
[NFR-37]: ../requirements/requirements.md#nfr-37
[NFR-38]: ../requirements/requirements.md#nfr-38
[NFR-52]: ../requirements/requirements.md#nfr-52
[NFR-64]: ../requirements/requirements.md#nfr-64
[NFR-75]: ../requirements/requirements.md#nfr-75
[NFR-125]: ../requirements/requirements.md#nfr-125
[NFR-184]: ../requirements/requirements.md#nfr-184
[Q-14]: ../requirements/requirements.md#open-questions
[Q-77]: ../requirements/requirements.md#open-questions
[Q-92]: ../requirements/requirements.md#open-questions
