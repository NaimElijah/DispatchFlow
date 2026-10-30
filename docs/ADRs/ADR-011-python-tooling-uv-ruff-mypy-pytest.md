# ADR-011: Python projects use uv, ruff, mypy in strict mode and pytest

- **Status:** Accepted
- **Date:** 2026-10-30

## Context

Two projects are written in Python 3.14 ([ADR-010]): the solver service in `src/backend/solver`,
which is deployed, and the research project in `src/research`, which never is ([ADR-008]). Each
has its own `pyproject.toml` and lockfile.

The forces:

- **Every environment must resolve to the same packages.** Three people work on Windows and
  Linux, and CI and the solver's `Dockerfile` run on Linux ([NFR-16]). A lockfile that has gone
  stale must fail the build, not install different versions quietly.
- **The project rules ask for a doc comment on every public item**, and for the formatter and
  linter to be authoritative (`.claude/CLAUDE.md`). A rule that only a reviewer enforces is
  forgotten.
- **The solver's contract is typed data**: problem, solution, objective and validator
  ([ADR-004]). A type checker is the cheapest check that the pieces fit.
- **The solver's HTTP tests use Starlette's `TestClient`** through `fastapi.testclient`. The
  installed Starlette (1.10.0) imports `httpx2` first. It falls back to `httpx` with a
  `StarletteDeprecationWarning` that says to install `httpx2` instead.

## Decision

We use uv, ruff, mypy in strict mode and pytest in both Python projects. The `justfile` runs
them ([ADR-009]).

- **uv manages the environment and the lockfile.** `uv sync --locked` and `uv run --locked`
  fail when `uv.lock` no longer matches `pyproject.toml`. Every recipe uses `--locked`. The
  build backend is `uv_build`.
- **ruff formats and lints.** Line length is 100.
  - The selected rules are `E`, `W`, `F`, `I`, `B`, `UP`, `N`, `SIM` and `D`: pycodestyle errors
    and warnings, Pyflakes, import order, bugbear, pyupgrade, naming, simplifications and
    docstrings.
  - Docstrings follow the Google convention.
  - Tests are exempt from the `D` rules, because a test's name states its purpose and a
    docstring would repeat it.
- **mypy runs in strict mode** on `src` and `tests`.
- **pytest runs the tests** from `tests/`, with `--import-mode=importlib`, `--strict-markers`
  and `--strict-config`. A mistyped marker or setting is an error.
- **The solver's dev dependencies include `httpx2`** (locked at 2.16.0), not `httpx`. The test
  client needs one of them, and `httpx2` is the one Starlette prefers.
- **The research project's editable dependency on the solver**, and the `uv lock` it needs
  after a solver change, are described in [ADR-008].

The recipes are `solver-check` and `research-check`. Each runs, in order:

```text
uv run --locked ruff format --check .
uv run --locked ruff check .
uv run --locked mypy src tests
uv run --locked pytest
```

`solver-format` and `research-format` run `ruff format .` and `ruff check --fix .`.

## Consequences

- **Easy:** one tool installs Python, creates the environment and locks it. A stale lockfile
  fails fast, locally and in CI.
- **Easy:** one tool formats and lints, so there is one configuration and one command. The
  docstring rule makes "every public item gets a doc comment" a failing check.
- **Easy:** strict typing catches a mismatch between the solver's domain types and its adapters
  before a test runs.
- **Hard:** a change to the solver's `pyproject.toml` needs `uv lock` in `src/research` as
  well, or `just research-check` fails ([ADR-008]).
- **Hard:** the same ruff and mypy settings are written in two `pyproject.toml` files and must
  change together.
- **Hard:** strict mypy rejects untyped third-party code and every missing annotation, so each
  new dependency has to bring types or an explicit exception.
- **Risk:** the `httpx` fallback in Starlette is a deprecation, not a removal. If Starlette
  changes which package its test client requires, the dev dependency changes with it.
- **Cost:** contributors need uv. It is already needed to install `just` ([ADR-009]).

## Alternatives considered

- **Poetry or pip.** Rejected: uv does the environment, the lockfile and the Python version in
  one tool, and its `--locked` flag fails on a stale lockfile.
- **`httpx` for the test client.** Rejected: Starlette deprecates it for `TestClient`, and the
  tests would run with a deprecation warning.

## Revisit when

- Starlette stops accepting `httpx2` or changes its test client. Follow whichever package it
  names.
- A second Python project needs the same tool settings. Share them from one place instead of
  copying them between `pyproject.toml` files.
- ruff or mypy stops supporting the Python version in `.python-version`. Move the pin, or the
  tool, together.

[ADR-004]: ADR-004-pluggable-solver-port.md
[ADR-008]: ADR-008-monorepo-layout-under-src.md
[ADR-009]: ADR-009-just-as-the-single-entry-point.md
[ADR-010]: ADR-010-toolchain-pins-in-version-files.md
[NFR-16]: ../requirements/requirements.md#nfr-16
