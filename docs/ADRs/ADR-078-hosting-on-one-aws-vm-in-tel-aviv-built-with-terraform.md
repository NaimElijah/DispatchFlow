# ADR-078: DispatchFlow runs on one AWS VM in Tel Aviv, built with Terraform and capped at ₪550 a month

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

[NFR-209] requires the server and its data, backups included, to be hosted in Israel, and carries the
marker "ADR pending: hosting". [NFR-14] requires a single VM with no redundancy. [NFR-52] set one
monthly cap of ₪1,200, SMS messages included. The tech stack proposed AWS `il-central-1` and left
two choices open: the VM size, and the monitoring backend that goes with it ([TS §15], [TS §22]).

The forces:

- Account emails go through Amazon SES, which runs in `il-central-1`. Hosting there keeps them in
  Israel without a second email provider ([TS §15]).
- A restore drill needs infrastructure that is rebuilt the same way every time ([NFR-125],
  [NFR-126]).
- The memory limits in the implementation plan add up to about 14 GB on the 16 GB VM: `caddy` 256 MB,
  `core` 3 GB, `optimizer` 3 GB, `postgres` 2 GB, `osrm` 2.5 GB, the monitoring stack 3 GB and
  `backup` 256 MB ([PL §23]). GlitchTip ([ADR-079]) and the operating system come on top. OSRM's
  figure is an estimate that M3 measures.
- Telemetry must stay in Israel, and the team, not a vendor, must set its retention ([NFR-76],
  [TS §23]).

Decided on 2026-11-14: the hosting is AWS `il-central-1`, one VM built with Terraform, the 16 GB
size with the Grafana stack, and a hosting cap of ₪550 a month with VAT. The cap was first set at
₪450 and raised once VAT was counted.

## Decision

- **Where:** AWS Israel (Tel Aviv), `il-central-1`, one VM. The company owns the account that holds
  production. [ADR-080] says what stands in when there is no company.
- **Size:** 4 vCPU and 16 GB (`t3a.xlarge`), a burstable instance in unlimited-credit mode, so
  solves are never throttled. M3 measures OSRM's memory and checks the choice.
- **Built with Terraform,** state in S3, in `infra/`. It holds the VM, disk, Elastic IP, security
  group, S3 buckets, IAM roles, KMS key, SES identity, Route 53 zone, budget alerts and EBS snapshot policy.
  cloud-init installs Docker and starts the stack on a fresh VM ([TS §15], [PL §23]). A lost VM is
  rebuilt by `terraform apply`, the restore script and a DNS check, and the restore does not count as
  downtime ([NFR-183]).
- **Monitoring:** the Grafana stack on the VM: Prometheus, Alertmanager, Loki, Tempo and Grafana,
  fed by the OpenTelemetry Collector, with 7 days of retention ([NFR-76], [TS §16]).
- **Cap:** hosting (server, disk, backups, email) stays within ₪550 a month, VAT included. SMS is a
  separate, per-message cost that exists only while the company has messages on ([FR-350]).
  [NFR-52] becomes two caps. The company still confirms both ([Q-62]).
- **Budget alert:** AWS Budgets alerts at 90% of the cap, about ₪495.
- **Uptime check:** a Route 53 health check over HTTPS, with a CloudWatch alarm that emails a named
  team member through SNS, all in Terraform. It probes every 30 seconds, more often than [NFR-128]'s
  once a minute, so two failures in a row alert well inside its 10 minutes. Healthchecks.io's backup
  pings, sent from outside AWS, are a second and slower warning ([NFR-164]).
- **Alerts:** Alertmanager emails named team members through SNS in `il-central-1`. Alert text carries
  no personal data ([NFR-268]). The team names its chat service before the pilot, and the same
  rule holds for it.

### Deploy, update and backup bundle

This bundle was adopted on 2026-11-14. Each part has a requirement behind it.

- **CI reaches AWS through OIDC,** with no stored access key. The deploy role trusts only a protected
  `production` environment of the repository: its trust policy pins both `aud` and `sub`, and `sub`
  is the `production` environment (`…:environment:production`).
  - The deploy role runs one custom SSM document on one instance. The document takes the image
    digest as a parameter with an `allowedPattern`, so only a digest passes. The team never uses
    `AWS-RunShellScript`, and the deploy role has no `ssm:UpdateDocument`.
  - CI pushes to GHCR, and the VM pulls from GHCR.
- **Session Manager is limited to named team members.** SSM records who opened each session and when,
  and records no session stream ([NFR-229]).
- **Every human account that can deploy or release the app requires a second factor:** AWS, GitHub,
  Expo, Apple, Google, Mend and the domain's registrar ([NFR-265]).
- **The backup secrets sit under a customer-managed KMS key** whose policy names the identities that may
  read them: the two named members, and the roles that write and prune backups ([NFR-273]). The key policy has no account-root statement beyond key
  administration.
- **Goals for the VM's AWS access and the backups.** These were decided on 2026-11-14. The M13
  Terraform pull request designs the mechanisms against these goals, and gets an infrastructure
  review and a security review before it merges.
  1. **No container can use the VM's full AWS role.** Each container that calls AWS gets only what it
     needs: send email, publish alerts, write backups.
  2. **The VM cannot delete or corrupt backups,** including old versions and restic's key and config
     objects ([NFR-266]). The bucket is versioned, with Object Lock in governance mode for 35 days.
  3. **Pruning runs inside `il-central-1` under its own role,** never on runners outside Israel. The
     KMS key policy names the identities that may read the backup secrets.
  4. **Old versions are kept at least as long as the lock.**
  5. **Only the break-glass role can bypass the lock or change the bucket's lock, lifecycle or
     policy.** A team member assumes it with MFA, and assuming it raises an alert.
  6. **Erased personal data leaves the backups within a total the data inventory states**
     ([NFR-72]).
  7. **GlitchTip's containers get CPU and memory limits,** and its ingest gets its own host name,
     never a path of the app's host ([ADR-079]).
  8. **A scheduled check of the backup repository alerts when it fails.**
- **EBS snapshots are kept 35 days.** The disk and its snapshots are encrypted.
- **EAS Update's code signing is used if Expo's plan offers it** ([ADR-081]). Not verified.

### Cost estimate

Every figure is an estimate. The team has not yet seen an AWS bill.

| Line | Estimate | Basis |
|---|---|---|
| VM, `t3a.xlarge` | about ₪400 a month | [TS §15] gives about $0.17 an hour, around $125 a month on demand |
| Route 53 health check | about $1.60 a month, about ₪6 with VAT | Estimate, not measured |
| Disk, snapshots, S3, SES, the Route 53 zone | not itemised | Part of the total below |
| Hosting total before VAT | about ₪440–450 a month | Estimate, not measured |
| Hosting total with 18% VAT | about ₪520–530 a month | Estimate, not measured; AWS charges Israeli accounts VAT |
| Cap | ₪550 a month, VAT included | Decided |
| Budgets alert | at 90%, about ₪495 | Decided |

SMS is outside this table. Its cost is an estimate, not measured: about ₪550–850 a month with VAT at
150 deliveries a day ([Q-62]).

## Consequences

- **Easy:** account emails stay in Israel with one provider. Production is rebuilt the same way each
  time, which the restore drill needs. One Compose file describes production and the laptops
  ([TS §15]).
- **Hard:** the VM cannot clean the backup bucket, so pruning is a separate job under its own role in
  `il-central-1`, and the break-glass role needs a member with MFA. A failed prune job grows the
  bucket and the bill until a check alerts.
- **Hard:** one VM has no redundancy ([NFR-14]). The memory limits leave about 2 GB of the 16 GB
  for GlitchTip and the operating system. Nominatim as GovMap's fallback needs about 2 GB more
  ([ADR-085]), so M3's measurement decides whether everything fits.
- **Cost:** about ₪520–530 a month with VAT against a ₪550 cap, a margin of about ₪20–30. The
  estimate, not measured, gives no itemised line for the disk, snapshots, backups, email and KMS key.
- **Risk:** whether the pinned restic works with an Object Lock bucket is not checked. M13 checks
  it. If it does not, backups need another tool or another lock arrangement.
- **Risk:** whether `t3a` is offered in `il-central-1` is not checked. `t3` costs about 10% more
  and still fits the cap.
- **Risk:** the estimate does not say whether it allows for surplus CPU-credit charges in
  unlimited-credit mode. The first month's bill tests it.
- **Risk:** the Healthchecks.io warning watches the backup job, not the API. The Route 53 check is
  the only outage alert for the API.

## Alternatives considered

- **Azure Israel Central or Google Cloud `me-west1`.** Also in Israel, but email would need a
  separate provider ([TS §15]).
- **Hetzner or DigitalOcean.** Neither has an Israeli region ([NFR-209]).
- **8 GB (`t3.large` class) with OpenObserve.** About $70 a month against about $125, with
  memory caps per container. Rejected: the 16 GB size fits the cap with VAT, and measured memory,
  not a guess, should decide. It moves to "Over" and reopens only if the measured bill passes the
  cap.
- **SigNoz.** ClickHouse underneath, heavy on one VM ([TS §16]).
- **Hosted logging and monitoring** (Grafana Cloud, Datadog). Telemetry leaves Israel and the vendor
  sets retention ([TS §16], [TS §23]).
- **Non-burstable instances.** About twice the price for a load that is mostly idle ([TS §15]).
- **Pulumi or CDK, or clicking in the console.** A general-purpose language for about 15 resources,
  or no reproducibility ([NFR-125], [TS §15]).
- **Healthchecks.io alone for uptime.** It watches the backup job, so it warns later than a probe
  of the health endpoint.

## Revisit when

- A pilot month's bill passes ₪550 with VAT. Reopen the 8 GB size with OpenObserve.
- M3's measurement shows OSRM, the core, the optimizer and the monitoring stack, with GlitchTip, do
  not fit in 16 GB. Reopen the size or the monitoring backend.
- `t3a` is not offered in `il-central-1`. Move to `t3` and recheck the cap.
- The company declines the cap ([Q-62]).
- The first Budgets alert fires before the month ends.
- A requirement drops one VM or Israeli hosting ([NFR-14], [NFR-209]).

[ADR-079]: ADR-079-client-errors-and-crashes-go-to-self-hosted-glitchtip.md
[ADR-080]: ADR-080-team-accounts-stand-in-when-there-is-no-company.md
[ADR-081]: ADR-081-eas-update-for-javascript-only-fixes-during-the-pilot.md
[ADR-085]: ADR-085-govmap-geocodes-addresses-with-nominatim-as-the-fallback.md
[FR-350]: ../requirements/requirements.md#messages-to-recipients
[NFR-14]: ../requirements/requirements.md#nfr-14
[NFR-52]: ../requirements/requirements.md#nfr-52
[NFR-72]: ../requirements/requirements.md#nfr-72
[NFR-76]: ../requirements/requirements.md#nfr-76
[NFR-125]: ../requirements/requirements.md#nfr-125
[NFR-126]: ../requirements/requirements.md#nfr-126
[NFR-128]: ../requirements/requirements.md#nfr-128
[NFR-164]: ../requirements/requirements.md#nfr-164
[NFR-183]: ../requirements/requirements.md#nfr-183
[NFR-229]: ../requirements/requirements.md#nfr-229
[NFR-265]: ../requirements/requirements.md#nfr-265
[NFR-266]: ../requirements/requirements.md#nfr-266
[NFR-268]: ../requirements/requirements.md#nfr-268
[NFR-273]: ../requirements/requirements.md#nfr-273
[NFR-209]: ../requirements/requirements.md#nfr-209
[Q-62]: ../requirements/requirements.md#open-questions
[TS §15]: ../architecture/tech_stack.md#15-infrastructure-and-operations
[TS §16]: ../architecture/tech_stack.md#16-observability-and-alerting
[TS §22]: ../architecture/tech_stack.md#22-open-decisions
[TS §23]: ../architecture/tech_stack.md#23-alternatives-rejected-across-the-stack
[PL §23]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#23-deployment-and-operations
