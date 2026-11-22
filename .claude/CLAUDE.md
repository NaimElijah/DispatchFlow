# DispatchFlow — Project Instructions

DispatchFlow plans and re-plans daily delivery routes for one delivery company.
`docs/requirements/requirements.md` states what it must do; `docs/architecture/` how it is built
([`tech_stack.md`][TS] and [`domain_layer/`][DL] hold the decided design, the rest describes what is
built); `docs/ADRs/` why; [`docs/roadmap.md`][RM] when. Point across; never restate. Read the
relevant parts before proposing work. Which document wins is the precedence rule under
Documentation.

## Layout

| Path | What lives there |
|---|---|
| `src/backend/core` | REST API and system of record (Java 25, Spring Boot) |
| `src/backend/optimizer` | Optimization service (Python, FastAPI) |
| `src/frontend/web` | Web app, for every role (React, TypeScript) |
| `src/frontend/mobile` | Phone app, for every role (React Native, Expo) |
| `src/research` | Benchmarks, simulation, order generator; never deployed |
| `contracts/` | Versioned OpenAPI contracts between the components |
| `docs/` | Requirements, the decided design, the ADRs and the roadmap |
| `tests/` | Tests that span components: `e2e/`, `load/` |
| `infra/` | Deployment, OSRM configuration and, from M13, Terraform |
| `logs/` | Local log output; git ignores its contents |

## Where rules live

A folder's `CLAUDE.md` loads once you read or edit a file in it or below ([ADR-018], [ADR-124]). The
two side files (`src/backend`, `src/frontend`) hold what each side's components share; each
component has one for its rules, gotchas and commands; `contracts/` and `docs/` have one each.

- A rule goes in the narrowest `CLAUDE.md` whose folder holds every file it governs, and nowhere
  else.
- A `CLAUDE.md` holds commands, gotchas, conventions you would get wrong by default and rules no
  test checks. A rule a test or linter enforces gets one line naming the enforcer, plus only what
  goes against the defaults.
- No status notes, and no layout `ls` shows. A coming change is a directive. Where the tree still
  differs from an accepted decision, so that a rule would read as already true, one line says so,
  and the change that closes the gap deletes it.
- Budgets: this file at most 200 lines and 12,000 characters, `core`'s under 200 lines,
  `optimizer`'s 150, every other 100. A rule that passes these tests is never cut to meet one.
- Whoever reviews a `CLAUDE.md` change checks it against this section.

## Team agents

`.claude/agents/` holds four subagents, available to everyone: `df-code-reviewer`,
`df-security-reviewer`, `df-debugger` and `df-tester` ([ADR-020]). None of them commits or pushes.
When an agent in the user's `~/.claude/agents/` does the same job as a team agent, use the user's
agent first: their `code-reviewer` instead of `df-code-reviewer` ([ADR-022]). Use a team agent only
for a job none of theirs covers, or when the user names it. Never give a personal agent a `df-`
name: Claude Code would load the team agent in its place.

## Team skills

`.claude/skills/` holds three skills for the branch workflow, available to everyone ([ADR-025]).
Only a person can start one, by typing its command.

| Command | Use it to |
|---|---|
| `/finished-work-on-branch-and-making-pr-to-dev` | Commit finished work, merge the latest `dev`, check the commits for secrets, run `just check`, push, and open a pull request into `dev` |
| `/sync-with-dev` | Merge the latest `dev` into your branch in the middle of work, review the merge and fix what the review finds |
| `/fix-pr-feedback` | Act on the review comments on your pull request, push, and reply to them |

The finish skill runs no agent review: if you want one, run it before you type the command
([ADR-122]). Each skill's own file says what else it does.

## Commands

The root `justfile` is the only entry point, and CI runs the same recipes. `just` lists them.

- `just setup`: install every component's dependencies.
- `just check`: everything CI checks. Run it before every push.
- `just format`: reformat every component.
- `just <component>-check`: one component's checks (`core`, `optimizer`, `research`, `web`,
  `mobile`, `contracts`).
- `just <component>-run`: start `core`, `optimizer`, `web` or `mobile`.

## How we work

- Be direct. Disagree when something is wrong and say why.
- If a request is ambiguous, ask before building.
- Before a non-trivial change, state the plan and the trade-off in a few lines and wait for
  confirmation. Mechanical changes: just do them.
- Never claim something works without running it. Say which checks ran and what they printed.
- Say "I don't know" instead of guessing. Never invent APIs, flags, config keys or library
  behaviour: check the lockfile, the installed version or the docs.
- Comments explain why, never what. Every public type, function and module gets a doc comment:
  purpose, contract, errors, and any non-obvious precondition or side effect.
- No dead code, no commented-out code, no debug prints, no magic values. `TODO` only with an owner
  and an issue reference.
- Fail fast and loudly. Never swallow an error; every catch handles, enriches or rethrows. Validate
  at the boundary; inside it, trust the types.
- The formatter and linter are authoritative. Never disable a rule inline without a comment
  saying why.
- A folder of more than 30 source files splits into subfolders of what belongs together ([ADR-118]);
  `src/backend/core/CLAUDE.md` has the Java rules.

## Testing

- Tests are the executable specification, named Given/When/Then. Test through the public boundary:
  the API, the port, the CLI. Never assert on internals.
- Every bug fix starts with a failing test that reproduces it. Cover failure paths: invalid input,
  timeouts, partial failure, empty and boundary cases.
- Deterministic always: inject the clock, seed the randomness, no `sleep`, no real network.
- Real dependencies in containers (Testcontainers) for things we do not own; mock only our own
  ports.
- Never weaken, skip or delete a test to make code pass.

## Engineering judgment

- Build the simplest thing that satisfies the actual requirement. Complexity must be pulled by a
  real constraint, never added in anticipation of one.
- DispatchFlow serves one delivery company, with no multi-tenancy ([ADR-024]). The capabilities that
  [ADR-090] cuts are not built: building one reopens that ADR first.
- Every date, time window, shift and schedule is Israel local time, `Asia/Jerusalem`. No person or
  device has a zone of its own ([ADR-088]).
- Record every architectural decision, including a decision not to adopt something, as an ADR in
  `docs/ADRs/`, with the trigger that would make us revisit it.
- Dependencies point one way: domain logic never imports frameworks, I/O or transport types.
  Adapters depend on the core, never the reverse. No cyclic dependencies.
- Every new third-party dependency needs a justification: what it replaces, its maintenance
  status, its licence, why the standard library or an existing dependency is not enough, and the
  milestone at which [TS] adopts it. Nothing is adopted earlier ([TS §1][TS-1], rule 3).
- Use the words of [DL §17][DL-17] in code, screens and new text, and avoid its "words not used".
  The existing text of `requirements.md` keeps its other wording until it is aligned with the
  domain document, and a new link copies the row's existing gloss.

## Security

- Secrets never enter the repo, logs, error messages or shell history. They live in `.env`
  files, which git ignores; commit a `.env.example` with the keys and no values.
- Never log credentials, tokens or personal data.
- Company and personal data stay in Israel ([NFR-209]). Code sends them to no outside service
  hosted elsewhere unless [NFR-262] lists it as an accepted exception, and every outside service
  that receives company data is named in the data inventory (NFR-217). Fonts and map tiles load
  from our own host, and error reports go to our own GlitchTip.
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
- Never commit a broken build: `just check` passes first. The one exception: the team skills that
  push may commit first, and gate the push on `just check` ([ADR-025]). Never force-push a shared
  branch.

## Documentation

- Precedence: `requirements.md`, then the domain document, then the tech stack. An accepted ADR
  stands until a new ADR supersedes it: where it and a design document disagree, build from the
  ADR and correct the document. On when a tool is adopted, [TS §20][TS-20] wins over the roadmap; on
  milestones and dates, the roadmap wins ([ADR-093]). A change to a decision comes with a new ADR;
  pointer and wording fixes need none. The retired implementation plan, reached by permalink, is
  history: it binds only where an accepted ADR or a CLAUDE.md states the rule itself. New code
  follows the requirements, both design documents and the accepted ADRs.
- An accepted ADR comes before the code it governs merges. Code that a Proposed ADR governs merges
  only once that ADR is accepted; until then the accepted ADR it would change still stands, and the
  pull request says which Proposed ADR it depends on. The [ADR index][ADRS] shows each status,
  [TS §22][TS-22] lists the decisions still open, and the [roadmap][RM] names the ADRs still to be
  written before their milestone's code merges.
- A change that alters behaviour updates the documents it affects in the same pull request.
- Diagrams live in the repo as code (Mermaid), never as pasted images.

[TS]: ../docs/architecture/tech_stack.md
[TS-1]: ../docs/architecture/tech_stack.md#1-what-decides-close-calls
[TS-20]: ../docs/architecture/tech_stack.md#20-adoption-order
[TS-22]: ../docs/architecture/tech_stack.md#22-open-decisions
[DL]: ../docs/architecture/domain_layer/domain_layer_design.md
[DL-17]: ../docs/architecture/domain_layer/domain_layer_design.md#17-ubiquitous-language
[RM]: ../docs/roadmap.md
[ADRS]: ../docs/ADRs/README.md
[ADR-018]: ../docs/ADRs/ADR-018-ai-assistant-context-in-the-repository.md
[ADR-020]: ../docs/ADRs/ADR-020-team-subagents-in-the-repository.md
[ADR-022]: ../docs/ADRs/ADR-022-personal-subagents-come-before-team-subagents.md
[ADR-024]: ../docs/ADRs/ADR-024-one-delivery-company-no-multi-tenancy.md
[ADR-025]: ../docs/ADRs/ADR-025-team-skills-in-the-repository.md
[ADR-088]: ../docs/ADRs/ADR-088-israel-local-time-for-every-date-and-schedule.md
[ADR-090]: ../docs/ADRs/ADR-090-scope-cuts-for-the-first-release.md
[ADR-093]: ../docs/ADRs/ADR-093-the-implementation-plan-is-retired-and-a-roadmap-holds-the-dates.md
[ADR-118]: ../docs/ADRs/ADR-118-a-domain-package-above-30-files-splits-by-aggregate.md
[ADR-122]: ../docs/ADRs/ADR-122-the-finish-skill-runs-no-agent-review.md
[ADR-124]: ../docs/ADRs/ADR-124-a-claude-md-holds-only-what-a-session-would-otherwise-get-wrong.md
[NFR-209]: ../docs/requirements/requirements.md#nfr-209
[NFR-262]: ../docs/requirements/requirements.md#nfr-262
