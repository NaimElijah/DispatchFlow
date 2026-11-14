# ADR-072: A pinned Trivy scans images and Terraform, and Gitleaks scans the full history for secrets

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

On 2026-11-13 the owner, Naim Elijah, decided for the team to scan container images with a pinned
Trivy and to scan for secrets with Gitleaks. On 2026-11-14 he added a Trivy check of the Terraform
in `infra/`. This ADR settles the marker "ADR pending: image and secret scanning",
which [NFR-54] and [NFR-103] carry.

It extends [ADR-016], which keeps the lockfiles. OSV-Scanner stays and scans dependencies. ADR-016
says it does not scan base images, and names its own trigger: when container images are deployed,
"add an image scan for the base images". The proposed design deploys images ([TS §15], *Images*
row), so the trigger has fired.

The forces:

- **CI scans dependencies and images for known vulnerabilities** ([NFR-54]), and **scans the
  repository, with its full history, for committed secrets** ([NFR-103]).
- **A known vulnerability fails the build** unless recorded as accepted with a reason and an expiry
  ([NFR-104]). **A detected secret always fails the build and is rotated,** not only removed
  ([NFR-105]).
- **The scanner is itself a supply-chain risk.** On 19 March 2026, attackers used stolen credentials
  to publish a malicious Trivy release and force-pushed 76 of the 77 version tags of
  `aquasecurity/trivy-action` to credential-stealing code ([TS §18]). The compromise is
  CVE-2026-33634 ([TS §24]). A pinned commit SHA cannot be moved.
- **Pins stay pinned.** Every third-party action is pinned to a commit SHA and every image to a
  digest ([TS §1], rule 4; [ADR-047]), and Renovate keeps them current ([ADR-046]).
- **The infrastructure is code.** The Terraform in `infra/` describes the VM, its security group,
  buckets and IAM role ([TS §15], *Infrastructure as code* row). No Terraform file exists yet.

## Decision

- **Images: Trivy, pinned.**
  - Trivy is pinned to a release verified clean after the March 2026 compromise. Its GitHub action is
    pinned to a commit SHA, never a tag.
  - Renovate waits a minimum release age before it proposes a new Trivy release or action SHA
    ([ADR-046]). The sources give no number, so the team sets it with the rest of Renovate's
    configuration.
  - The release's number is not named here. It is read from Trivy's release page when the tech
    stack's version baseline gets its row ([TS §21]).
- **Terraform: `trivy config` on `infra/`, from the pilot.** A CI step runs it once the Terraform
  exists, with the same pinned tool. It costs nothing extra.
- **Secrets: Gitleaks, over the full history,** from the Alpha ([NFR-103], [TS §18]). A finding fails
  the build, and the secret is rotated ([NFR-105]). The plan also has Gitleaks block a commit that
  contains a secret ([PL §23][PL-23]).
- **Dependencies stay with OSV-Scanner** ([ADR-016]). Its lockfile audit and its `osv-scanner.toml`
  exceptions are unchanged.
- **Accepted findings** follow [NFR-104]: listed in a file with a reason and an expiry. The sources
  do not say whether Trivy's exceptions use its own file or one shared format.
- **Timing:** Trivy and Semgrep arrive in the January break, Gitleaks at the Alpha, and images are
  built in CI from the pilot ([TS §18], *Scans* and *Build* rows).

## Consequences

- **Easy:** one pinned tool checks both images and Terraform.
- **Easy:** a moved tag cannot change what CI runs, which is the failure of March 2026.
- **Hard:** a pin is upkeep. Each Trivy upgrade is a reviewed pull request after the minimum age, and
  someone checks the release before merging a digest or SHA change that carries no version change
  ([ADR-046]).
- **Hard:** the vulnerability database is online and moves. A pull request that touched nothing can
  fail because a new advisory appeared, as [ADR-016] found for OSV-Scanner.
- **Cost:** two more tools in CI, and their minutes. How the repository's CI minutes are budgeted is
  left to M5, when real run times are known.
- **Risk:** a malicious release that is pinned before anyone notices is still run. The verified
  release and the minimum release age reduce this. They do not remove it.
- **Risk:** between the January break and the pilot, CI builds no image ([TS §18], *Build* row), so
  the sources do not say what Trivy scans in that window. [NFR-54] asks for images to be scanned.
- **Risk:** Gitleaks finds what its rules recognise. A secret in a form no rule matches passes. The
  sources name no custom rules.

## Alternatives considered

- **Grype for images.** Rejected by the owner in favour of a pinned Trivy. The sources record no
  reason beyond that Trivy also checks the Terraform with the same tool. This ADR does not supply
  one.
- **OSV-Scanner alone.** Lost on [ADR-016]'s own finding: it scans lockfiles and the pom, not base
  images.
- **An unpinned Trivy action.** Rejected across the stack: the March 2026 `trivy-action` compromise
  moved its tags ([TS §23]).

## Revisit when

- Trivy, its action or its release channel is compromised again, or a release the team pinned is
  reported malicious. Replace the pin, rotate the credentials the runner held, and weigh Grype
  again.
- A Trivy finding blocks a release and [NFR-104]'s exception file cannot hold it. Decide the
  exception format.
- The repository becomes public, or GitHub Advanced Security is paid for. Compare GitHub's own
  scanning, as [ADR-016] says.
- The gap before the pilot matters: if the team builds images in CI before the pilot, run Trivy on
  them from the first build.

[ADR-016]: ADR-016-dependency-audit-with-osv-scanner.md
[ADR-046]: ADR-046-renovate-replaces-dependabot-and-targets-dev.md
[ADR-047]: ADR-047-images-pinned-by-digest-and-core-on-temurin-25-jre.md
[NFR-54]: ../requirements/requirements.md#nfr-54
[NFR-103]: ../requirements/requirements.md#nfr-103
[NFR-104]: ../requirements/requirements.md#nfr-104
[NFR-105]: ../requirements/requirements.md#nfr-105
[PL-23]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#23-deployment-and-operations
[TS §1]: ../architecture/tech_stack.md#1-what-decides-close-calls
[TS §15]: ../architecture/tech_stack.md#15-infrastructure-and-operations
[TS §18]: ../architecture/tech_stack.md#18-cicd-and-supply-chain
[TS §21]: ../architecture/tech_stack.md#21-version-baseline
[TS §23]: ../architecture/tech_stack.md#23-alternatives-rejected-across-the-stack
[TS §24]: ../architecture/tech_stack.md#24-references
