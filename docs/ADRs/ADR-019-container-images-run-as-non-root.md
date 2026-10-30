# ADR-019: Each service ships as a container image that runs as a non-root user

- **Status:** Accepted
- **Date:** 2026-10-30

## Context

The two services, `core` and the optimization service, will run on one VM ([NFR-14]), and
[NFR-16] asks for every server-side component to start with one Docker Compose command. Compose
is not set up yet. Each service still needs an image now, so that it can be built and run the
way it will be deployed.

- **Least privilege.** A process that is broken into should not hold root inside its container.
- **The images must not carry what only a build needs.** `core` needs a JDK and Maven to build,
  and only a JRE to run.
- **Nothing secret goes into an image.** `.env` files hold local configuration, and git already
  ignores them.
- **Rebuilds should be fast.** A source change should not reinstall every dependency.

## Decision

Each service has its own `Dockerfile` and `.dockerignore` in its folder.

- **`core` is built in two stages.** `eclipse-temurin:21-jdk-noble` runs `./mvnw package`, and
  `eclipse-temurin:21-jre-noble` runs the jar. The image build skips the tests
  (`-DskipTests`): they run in CI, where Docker is available for the integration tests
  ([ADR-014]).
- **The optimization service is one stage** on `python:3.14-slim`, with uv 0.12.7 copied in from
  its image. It installs the locked dependencies in their own layer before it copies the source,
  and it installs no development dependencies (`uv sync --locked --no-dev`).
- **Both run as a system user, `dispatchflow`, with uid 10001.** The `USER` line comes after
  every step that needs root.
- **`.dockerignore` keeps `.env` out of the build context** in both services, along with build
  output and caches. The solver's also leaves out `tests/`.
- **Base images are pinned by tag**, and Dependabot proposes tag updates weekly ([ADR-015]).
- **The ports are fixed:** `core` exposes 8080 and the optimization service 8000.

## Consequences

- **Easy:** a compromised process in either container is not root there.
- **Easy:** the `core` image holds a JRE and the jar, with no JDK, Maven or source.
- **Easy:** a source-only change to the solver reuses the dependency layer.
- **Hard:** a tag such as `21-jre-noble` moves when the publisher rebuilds it, so two builds of
  the same commit can differ.
- **Hard:** no CI job builds the images, so a broken `Dockerfile` is found only when someone
  builds it. Both images were built and run by hand on 2026-10-30. Each answered its health
  probe as the `dispatchflow` user, `core` against a throwaway PostgreSQL.
- **Hard:** the audit does not scan the base images ([ADR-016]).
- **Cost:** a step that needs root, such as installing a system package, has to come before the
  `USER` line.

## Alternatives considered

- **Running as root, the base images' default.** Rejected: nothing the services do needs it.
- **Distroless or Alpine base images.** Not adopted: the official Temurin and Python images
  build and run both services as they are. Revisit when image size or the number of base-image
  vulnerabilities matters (see below).
- **Pinning base images by digest.** Deferred: it makes builds repeatable, but nothing is
  deployed yet, and Dependabot keeps tags current with less to review.
- **Running the tests inside `docker build`.** Rejected: the integration tests start a
  PostgreSQL container, and a build stage has no Docker to start it with. CI runs them instead.

## Revisit when

- **The first deployment is prepared** ([NFR-16]). Write the Compose file, add an image build
  to CI, and decide between tags and digests.
- **Images are deployed.** Add a scan of the base images ([ADR-016]).
- **An image's size or its base-image vulnerabilities become a problem.** Weigh a distroless or
  slimmer base.

[ADR-014]: ADR-014-real-dependencies-in-containers-for-tests-and-local-runs.md
[ADR-015]: ADR-015-ci-one-workflow-one-job-per-component.md
[ADR-016]: ADR-016-dependency-audit-with-osv-scanner.md
[NFR-14]: ../requirements/requirements.md#nfr-14
[NFR-16]: ../requirements/requirements.md#nfr-16
