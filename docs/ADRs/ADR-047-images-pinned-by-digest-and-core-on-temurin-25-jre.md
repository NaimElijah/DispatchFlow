# ADR-047: Images are pinned by digest, and the core runs on the Temurin 25 JRE

- **Status:** Accepted; amended by ADR-123
- **Date:** 2026-11-13

## Context

[ADR-019] gave each service its own `Dockerfile` and a non-root user. Today:

- **The core** builds on `eclipse-temurin:21-jdk-noble` and runs on `eclipse-temurin:21-jre-noble`.
  Its entry point is a plain `java -jar`, with no heap setting.
- **The optimizer** runs on `python:3.14-slim`, with uv 0.12.7 copied from
  `ghcr.io/astral-sh/uv:0.12.7`.
- **Both run as `dispatchflow`, uid 10001**, and expose ports 8080 and 8000.
- **Every base image is pinned by tag.** ADR-019 deferred digests: "nothing is deployed yet, and
  Dependabot keeps tags current with less to review". It also found that "a tag such as
  `21-jre-noble` moves when the publisher rebuilds it, so two builds of the same commit can
  differ".
- **No Compose file exists, and no CI job builds the images.** `ci.yml` has no image build.
- **The PostgreSQL image** is the string `postgres:18.6` in `TestcontainersConfiguration`
  ([ADR-014]).

ADR-019 named its trigger: when the first deployment is prepared, write the Compose file, add an
image build to CI, and decide between tags and digests. The proposed design prepares that
deployment ([PL §23]). The forces:

- **Builds are reproducible** ([TS §1], rule 4): "images pinned by digest, actions pinned by commit
  SHA". Deploys pull by digest, and a rollback is the previous digest ([PL §23]).
- **The server side is `linux/amd64`** ([NFR-208]).
- **Every container has a health check, a memory limit and a non-root user** ([TS §15], the
  *Containers* row). The core's limit on the 16 GB VM is 3 GB ([PL §23]).
- **A shell is useful during the pilot** for debugging ([TS §6], the *Runtime image* row).
- **Renovate can turn tags into digests and keep them current** ([ADR-046], [TS §5]).

## Decision

Non-root stands ([ADR-019]). We pin every image by digest, run the core on the Eclipse Temurin 25
JRE, and ship `linux/amd64` images.

**What changes in [ADR-019]:** its "pinned by tag", which becomes a pin by digest. Its `Dockerfile`
per service and its non-root user stand.

- **Digests.** Every image in `deploy/compose.yaml` and every `Dockerfile` base image is pinned by
  digest ([TS §15], the *Images* row; [PL §23]). The tag stays beside the digest for readers, as
  the `justfile` does for the OSV-Scanner image today. The core's and the optimizer's own images
  are deployed, and rolled back, by digest ([PL §23]).
- **The core's runtime image** is the Eclipse Temurin 25 JRE, non-root, with `-XX:MaxRAMPercentage`
  set for the container's memory limit ([TS §6], the *Runtime image* row). The plan sets it to 75
  for the 3 GB limit ([PL §23]). The move to Java 25 is [ADR-038]'s.
- **The optimizer's runtime image** is `python:3.14-slim` with uv, non-root ([TS §7], the *Runtime
  image* row).
- **PostgreSQL, OSRM and Caddy** run the official `postgres:18`, OSRM and `caddy` images, pinned by
  digest ([TS §15]). The sources name no other image.
- **Platform:** `linux/amd64`. GitHub Actions builds the images ([PL §23]). They start with one
  command and pass their health checks on Linux in CI, and on Windows and macOS through Docker
  Desktop, checked by hand before each pilot build ([NFR-208]).
- **The registry and the pull:** CI pushes the images to GHCR, and an SSM command pulls them by
  digest onto the VM ([TS §15], the *Deployment* row; [PL §23]). That row was confirmed on
  2026-11-14, with hosting on AWS ([ADR-078], [NFR-209]).
- **CI builds the core jar, and the image only copies it in** (decided on
  2026-11-14). jOOQ code generation ([ADR-041]) needs Docker, which CI has for Testcontainers and the
  image build does not. The generated code stays out of the repository, and the core `Dockerfile`
  becomes a runtime-only image.
- **Digest pins start in January,** when Renovate arrives ([ADR-046], [TS §5]), not at the pilot.
  This was chosen on 2026-11-14, because `compose.dev.yaml`'s images run on every laptop from
  M0.
- **Not adopted for now:** a GraalVM native image, and distroless images. Both are reconsidered
  after the pilot ([TS §6]).
- **Adoption:** digest pins from January; the runtime image settings from the pilot ([TS §15],
  [TS §20]). Until then ADR-019's tags stand.
- **Image scanning** is [ADR-072]'s: a pinned Trivy ([NFR-54]). The sources cite NFR-54 for the
  digest rule, and its text covers scanning only.

## Consequences

- **Easy:** two builds of one commit start from the same base. ADR-019's moving-tag problem goes.
- **Easy:** a rollback is the previous digest. It works because migrations only ever expand
  ([PL §23]).
- **Easy:** the heap follows the container's memory limit, and a shell is there when the pilot
  needs one.
- **Hard:** a digest tells a reader nothing, so a bump is a Renovate pull request to review
  ([ADR-046]). Renovate arrives in January, before any image is deployed.
- **Hard:** a pinned base image does not pick up the publisher's rebuilt security fixes until its
  digest moves. ADR-019 noted that the audit does not scan base images ([ADR-016]). [ADR-072]'s
  Trivy scans them from January ([NFR-54], [TS §18]); until it runs, nothing reports a vulnerable
  pinned base.
- **Hard:** with `MaxRAMPercentage` at 75, a quarter of the 3 GB is left for memory outside the
  heap. The memory limits are to be measured on the VM, not guessed ([TS §15], the *VM size* row).
- **Cost:** a JRE image with a shell is larger than a distroless one ([TS §6]). The JVM starts
  slower than a native image, which a long-running VM does not need.
- **Risk:** [TS §15] asks for a non-root user in every container. The sources do not say how the
  official `postgres`, OSRM and `caddy` images do it. Not verified.
- **Risk:** [TS §18] says every image gets a digest, while [PL §5] lists the `compose.dev.yaml`
  images by tag ([ADR-042]). It was decided on 2026-11-14 that digest pins start in January and
  cover every image, the development file included. Until then those images run by tag.

## Alternatives considered

- **Tags only, as ADR-019 has it.** Rejected: a tag moves when its publisher rebuilds it, and the
  deploy and rollback by digest depend on a fixed one.
- **A GraalVM native image for the core.** Rejected for now: slow builds, and reflection
  configuration for Spring, jOOQ and POI, to save a startup time that a long-running VM does not
  need ([TS §6]).
- **Distroless images.** Not adopted for now: smaller, but with no shell during the pilot.
  Reconsider after it ([TS §6]).
- **Alpine for the optimizer.** Rejected: musl breaks or slows the scientific wheels ([TS §7]).
- **Running as root.** Still rejected, as in ADR-019.

## Revisit when

- The pilot is over. Weigh a native image if startup time or memory matters, and distroless if the
  shell is no longer needed or base-image vulnerabilities become the problem.
- [ADR-072]'s Trivy scan, which starts in January with the digest pins, fails on a pinned digest or
  skips it ([NFR-54]).
- The hosting ([ADR-078], [NFR-209]) moves to an instance that is not `linux/amd64`, or to a
  registry or pull mechanism other than the one in the *Deployment* row. The platform and the pull
  change with it.
- The core is killed for memory at its limit. Re-measure the heap share on the VM.

[ADR-014]: ADR-014-real-dependencies-in-containers-for-tests-and-local-runs.md
[ADR-016]: ADR-016-dependency-audit-with-osv-scanner.md
[ADR-019]: ADR-019-container-images-run-as-non-root.md
[ADR-038]: ADR-038-mise-pins-every-toolchain-and-core-moves-to-java-25.md
[ADR-041]: ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md
[ADR-042]: ADR-042-compose-dev-for-local-runs-testcontainers-for-tests.md
[ADR-046]: ADR-046-renovate-replaces-dependabot-and-targets-dev.md
[ADR-072]: ADR-072-images-scanned-by-pinned-trivy-and-secrets-by-gitleaks.md
[ADR-078]: ADR-078-hosting-on-one-aws-vm-in-tel-aviv-built-with-terraform.md
[NFR-54]: ../requirements/requirements.md#nfr-54
[NFR-208]: ../requirements/requirements.md#nfr-208
[NFR-209]: ../requirements/requirements.md#nfr-209
[PL §5]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#5-repository-and-local-environment
[PL §23]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#23-deployment-and-operations
[TS §1]: ../architecture/tech_stack.md#1-what-decides-close-calls
[TS §5]: ../architecture/tech_stack.md#5-development-environment-and-workflow
[TS §6]: ../architecture/tech_stack.md#6-core-service-java
[TS §7]: ../architecture/tech_stack.md#7-optimization-service-python
[TS §15]: ../architecture/tech_stack.md#15-infrastructure-and-operations
[TS §18]: ../architecture/tech_stack.md#18-cicd-and-supply-chain
[TS §20]: ../architecture/tech_stack.md#20-adoption-order
