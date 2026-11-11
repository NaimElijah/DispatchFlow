# DispatchFlow — Project Instructions

> Draft for the team to agree on (#1). Until then, raise anything you disagree with at the team
> meeting rather than working around it.

DispatchFlow plans and re-plans daily delivery routes for one delivery company. What it must do is in
`docs/requirements/requirements.md`; how it is built is in `docs/architecture/`; why each
decision was taken is in `docs/ADRs/`. Read the relevant parts before proposing work.

## Layout

| Path | What lives there |
|---|---|
| `src/backend/core` | REST API and system of record (Java 21, Spring Boot) |
| `src/backend/solver` | Solver service (Python, FastAPI) |
| `src/frontend/console` | Dispatcher console (React, TypeScript) |
| `src/frontend/driver-app` | Driver app (React Native, Expo) |
| `src/research` | Benchmarks, simulation, order generator; never deployed |
| `contracts/` | Versioned OpenAPI contracts between the components |
| `tests/` | Tests that span components: `e2e/`, `load/` |
| `infra/` | Road-network data and deployment |
| `logs/` | Local log output; git ignores its contents |

This file holds the rules for the whole repository. Narrower rules live next to the code and load
when you work there ([ADR-018](../docs/ADRs/ADR-018-ai-assistant-context-in-the-repository.md)):

- `src/backend/CLAUDE.md` and `src/frontend/CLAUDE.md`: the rules for each side.
- A `CLAUDE.md` in each component (`core`, `solver`, `research`, `console`, `driver-app`): its
  layout, rules, gotchas and commands, including how to run one test.
- `contracts/CLAUDE.md`: how a contract changes. `docs/CLAUDE.md`: how to write requirements and
  ADRs.

Keep each file to what an assistant needs in that folder, and never repeat a rule that a parent
file already states.

## Team agents

`.claude/agents/` holds four subagents, available to everyone
([ADR-020](../docs/ADRs/ADR-020-team-subagents-in-the-repository.md)). None of them commits or
pushes.

**Each person's own agents come first**
([ADR-022](../docs/ADRs/ADR-022-personal-subagents-come-before-team-subagents.md)). When an agent
in the user's `~/.claude/agents/` does the same job as a team agent, use the user's agent: for
example, their `code-reviewer` instead of `df-code-reviewer`. Use a team agent only for a job that
none of the user's own agents covers, or when the user names it. Never give a personal agent a
`df-` name: Claude Code would load the team agent in its place.

| Agent | Use it to |
|---|---|
| `df-code-reviewer` | Review a change before it is committed or opened as a pull request |
| `df-security-reviewer` | Review a change to auth, API operations, personal data, input, secrets, dependencies, CI or images |
| `df-debugger` | Find the root cause of a bug, a failing test or a red CI job, with a failing test |
| `df-tester` | Write tests for new or untested behaviour |

## Team skills

`.claude/skills/` holds three skills for the branch workflow, available to everyone
([ADR-025](../docs/ADRs/ADR-025-team-skills-in-the-repository.md)). Only a person can start one, by
typing its command.

| Command | Use it to |
|---|---|
| `/finished-work-on-branch-and-making-pr-to-dev` | Commit finished work, merge the latest `dev`, review and fix, run `just check`, push, and open a pull request into `dev` |
| `/sync-with-dev` | Merge the latest `dev` into your branch in the middle of work, review the merge and fix what the review finds |
| `/fix-pr-feedback` | Act on the review comments on your pull request, push, and reply to them |

`/finished-work-on-branch-and-making-pr-to-dev` runs `df-code-reviewer`, and `df-security-reviewer` when
the change calls for it, even if you have agents of your own. `/sync-with-dev` runs `df-code-reviewer` the
same way, on Sonnet at high effort, when a merge has conflicts or files both sides changed
([ADR-033](../docs/ADRs/ADR-033-sync-with-dev-reviews-the-merge-on-sonnet.md)). Both skills ask before
merging a `dev` change to `.claude/`, `.github/`, `justfile` or a `CLAUDE.md`
([ADR-034](../docs/ADRs/ADR-034-ask-before-merging-changes-to-the-assistant-setup.md)).

## Commands

The root `justfile` is the only entry point, and CI runs the same recipes. `just` lists them.

- `just setup`: install every component's dependencies.
- `just check`: everything CI checks. Run it before every push.
- `just format`: reformat every component.
- `just <component>-check`, `just <component>-run`: one component at a time.

## How we work

- Be direct. Disagree when something is wrong and say why.
- If a request is ambiguous, ask before building. One good question beats a confidently wrong
  implementation.
- Before a non-trivial change, state the plan and the trade-off in a few lines and wait for
  confirmation. Mechanical changes: just do them.
- Never claim something works without running it. Say which checks ran and what they printed.
- Say "I don't know" instead of guessing. Never invent APIs, flags, config keys or library
  behaviour: check the lockfile, the installed version or the docs.

## Engineering judgment

- Build the simplest thing that satisfies the actual requirement. Complexity must be pulled by a
  real constraint, never added in anticipation of one.
- Record every architectural decision, including a decision not to adopt something, as an ADR in
  `docs/ADRs/`, with the trigger that would make us revisit it.
- Dependencies point one way: domain logic never imports frameworks, I/O or transport types.
  Adapters depend on the core, never the reverse. No cyclic dependencies.
- Every new third-party dependency needs a justification: what it replaces, its maintenance
  status, its licence, and why the standard library or an existing dependency is not enough.

## Code style

- Names carry the meaning. Comments explain why, never what.
- Every public type, function and module gets a doc comment: purpose, contract, errors, and any
  non-obvious precondition or side effect.
- No dead code, no commented-out code, no debug prints, no magic values.
- `TODO` only with an owner and an issue reference.
- Fail fast and loudly. Never swallow an error; every catch handles, enriches or rethrows.
- Validate at the boundary; inside it, trust the types.
- The formatter and linter are authoritative. Never disable a rule inline without a comment
  saying why.

## Testing

- Tests are the executable specification, named Given/When/Then.
- Test through the public boundary: the API, the port, the CLI. Never assert on internals.
- Every bug fix starts with a failing test that reproduces it.
- Cover failure paths: invalid input, timeouts, partial failure, empty and boundary cases.
- Deterministic always: inject the clock, seed the randomness, no `sleep`, no real network.
- Real dependencies in containers (Testcontainers) for things we do not own; mock only our own
  ports.
- Never weaken, skip or delete a test to make code pass.

## Security

- Secrets never enter the repo, logs, error messages or shell history. They live in `.env`
  files, which git ignores; commit a `.env.example` with the keys and no values.
- Never log credentials, tokens or personal data.
- Parameterised queries only. Never build a query, command or path by concatenating input.
- Pin dependencies and commit the lockfiles. `just audit` scans them, and CI fails on a known
  vulnerability; an accepted one goes into that folder's `osv-scanner.toml` with its reason and
  an expiry date.

## Git

- `main` is production. `dev` is development: branch off it for new work, and open every pull
  request back into it. `dev` reaches `main` through a merge commit, never a squash.
- Branch names: `type/short-kebab-description`, for example `feat/order-csv-import`.
- Conventional Commits: `type(scope): imperative subject`, under 72 characters. The body
  explains why and references the issue or ADR.
- A pull request is squash-merged, so its title is the commit: write it as a Conventional
  Commit. Fill in the template, and link the issue with `Closes #N`.
- One logical change per commit. Never mix a refactor with a behaviour change, or formatting
  with logic.
- Never commit a broken build: `just check` passes first. Never force-push a shared branch.

## Documentation

- `docs/requirements/requirements.md` states what the system must do, `docs/architecture/`
  describes it as it is, and `docs/ADRs/` records why. Point across; never restate.
- A change that alters behaviour updates the documents it affects in the same pull request.
- Requirements are intent, not observation. Behaviour found in code is not a requirement until
  the team agrees it is.
- Diagrams live in the repo as code (Mermaid), never as pasted images.
