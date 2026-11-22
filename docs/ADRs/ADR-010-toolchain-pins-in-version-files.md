# ADR-010: Toolchain versions are pinned in per-language version files

- **Status:** Superseded by ADR-038
- **Date:** 2026-10-30

## Context

The repository holds Java, Python and Node components ([ADR-008]), written by three people on
different machines and built by CI on Linux ([ADR-015]). A check that passes on one machine
must pass on the others ([NFR-16]). That cannot hold if each machine uses a different Java,
Python or Node.

The forces:

- **Each version must be written down once** and read by the tools that need it, not copied
  into the README, the workflow and each developer's head.
- **The official setup actions already read version files.** `actions/setup-java` reads
  `.java-version`, `actions/setup-node` reads `.nvmrc`, and uv reads each project's
  `requires-python`.
- **Some components need a particular version for a reason.** The research code depends on
  PyVRP, and the optimizer will depend on OR-Tools ([ADR-005]): libraries that publish wheels for a
  limited range of Python versions.
- **Some versions are repeated** in files that cannot read a version file, such as a Dockerfile.

## Decision

We pin each language's version in a file at the repository root, and the tools read it. Build
tools are pinned beside the code that uses them.

| Pin | Value | Where | Read by |
|---|---|---|---|
| Java | 21 | `.java-version` | `actions/setup-java` in CI |
| Python | 3.14 | `.python-version`, and `requires-python = ">=3.14,<3.15"` in both `pyproject.toml` files | uv reads `requires-python`; it does not look above a project for `.python-version` |
| Node | 24 | `.nvmrc` | `actions/setup-node` in CI |
| uv | 0.12.7 | `UV_VERSION` in `.github/workflows/ci.yml`, and the optimizer `Dockerfile` | `astral-sh/setup-uv`, Docker |
| Maven | 3.10.0 | `src/backend/core/.mvn/wrapper/maven-wrapper.properties` | the Maven wrapper |

The reason for each language version:

- **Java 21** is the version the implementation plan's technology stack names.
- **Python 3.14** is the newest version for which OR-Tools 9.15.6755 ships wheels. It publishes
  wheels for CPython 3.9 to 3.14. PyVRP 0.14.1 ships wheels for 3.11 to 3.15.
- **Node 24** (codename Krypton) is a long-term-support line, and it is the version installed on
  the owner's machine (v24.16.0). Node 26 (Lithium) entered long-term support on 2026-10-28,
  two days before this decision. We did not check whether Expo SDK 58 supports it.

The versions are repeated where a file cannot read the version files. They change together:

| Version | Also set in |
|---|---|
| Java 21 | `<java.version>` in `src/backend/core/pom.xml`, and both `FROM` lines of `src/backend/core/Dockerfile` (`eclipse-temurin:21`) |
| Python 3.14 | `requires-python = ">=3.14"` in `src/backend/optimizer/pyproject.toml` and `src/research/pyproject.toml`, and `FROM python:3.14-slim` in `src/backend/optimizer/Dockerfile` |
| uv 0.12.7 | `uv_build>=0.12.7,<0.13.0` under `[build-system]` in both `pyproject.toml` files, as well as the two places in the table |

## Consequences

- **Easy:** a new contributor reads three small files to learn which Java, Python and Node to
  install, and CI uses the same ones.
- **Easy:** one edit moves a language version in CI, and the setup actions need no
  version input of their own.
- **Hard:** the versions are pinned in several places that must change together. Nothing
  checks that they agree, so a missed one shows up as a failed build or an image that differs
  from CI.
- **Hard:** Python 3.14 is the newest version the optimizer can use, so it cannot move ahead of
  OR-Tools. The research code, which also needs PyVRP, is held to the same version.
- **Hard:** Node 24 is not the newest long-term-support line. The driver app can move to
  Node 26 only after Expo supports it.
- **Cost:** the version files, the uv pin and the Maven wrapper are each a pin to maintain.

## Alternatives considered

- **mise, to manage every toolchain version.** Rejected: it is a second tool to install, when
  `actions/setup-java`, `actions/setup-node` and uv already read these files.

## Revisit when

- Expo supports Node 26. Move `.nvmrc` to 26.
- OR-Tools ships wheels for CPython 3.15. Consider Python 3.15, which PyVRP already supports.
- The versions drift between the places listed above. Add a check that compares them.

[ADR-005]: ADR-005-or-tools-first-research-solver-for-re-optimization.md
[ADR-008]: ADR-008-monorepo-layout-under-src.md
[ADR-015]: ADR-015-ci-one-workflow-one-job-per-component.md
[NFR-16]: ../requirements/requirements.md#nfr-16
