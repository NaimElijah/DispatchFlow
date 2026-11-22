# DispatchFlow — Project Instructions

DispatchFlow plans and re-plans daily delivery routes for one delivery company. What it must do is in
`docs/requirements/requirements.md`; how it is built is in `docs/architecture/`; why each
decision was taken is in `docs/ADRs/`. Read the relevant parts before proposing work.

Two design documents set the direction, and each owns one topic:

- [`domain_layer_design.md`][DL] (in `docs/architecture/domain_layer/`): the domain model.
- [`tech_stack.md`][TS] (in `docs/architecture/`): the tools and versions, and when each is adopted.

[`docs/roadmap.md`][RM] says when each part is built. Which document wins is the precedence rule
under Documentation.

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

[ADR-123] (Accepted) keeps this tree and renames three components, which now carry their new
names: `solver` became `optimizer`, `console` became `web` and `driver-app` became `mobile`.
[ADR-038] (Accepted) moved the core to Java 25; its `mise.toml` is still to come.

This file holds the rules for the whole repository. Narrower rules live next to the code and load
when you work there ([ADR-018](../docs/ADRs/ADR-018-ai-assistant-context-in-the-repository.md)):

- `src/backend/CLAUDE.md` and `src/frontend/CLAUDE.md`: the rules for each side.
- A `CLAUDE.md` in each component (`core`, `optimizer`, `research`, `web`, `mobile`): its
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
| `/finished-work-on-branch-and-making-pr-to-dev` | Commit finished work, merge the latest `dev`, check the commits for secrets, run `just check`, push, and open a pull request into `dev` |
| `/sync-with-dev` | Merge the latest `dev` into your branch in the middle of work, review the merge and fix what the review finds |
| `/fix-pr-feedback` | Act on the review comments on your pull request, push, and reply to them |

`/finished-work-on-branch-and-making-pr-to-dev` runs no agent review
([ADR-122](../docs/ADRs/ADR-122-the-finish-skill-runs-no-agent-review.md)): if you want one, run it
before you type the command. `/sync-with-dev` runs `df-code-reviewer`, even if you have agents of your
own, on Sonnet at high effort, when a merge has conflicts or files both sides changed
([ADR-033](../docs/ADRs/ADR-033-sync-with-dev-reviews-the-merge-on-sonnet.md)). Both skills ask before
merging a `dev` change to `.claude/`, `.github/`, `justfile` or a `CLAUDE.md`
([ADR-034](../docs/ADRs/ADR-034-ask-before-merging-changes-to-the-assistant-setup.md)).

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
- A folder of more than 30 source files, not counting `package-info.java`, splits into subfolders of
  what belongs together. A module's domain splits into one package per aggregate, over a root that
  holds its error and shared values ([ADR-118]). Files the language binds stay together
  (package-private access, a sealed hierarchy), and so do files in a dependency cycle. Never widen
  production visibility or add a package cycle to split. The same threshold applies to an
  application package, which splits by aggregate too. The kernel's domain types and each `events`
  package stay flat; `shared/application/` is a package of its own ([ADR-120]). Each new package
  carries `@NullMarked` in its `package-info.java`.

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

- `docs/requirements/requirements.md` states what the system must do, `docs/architecture/`
  how it is built, and `docs/ADRs/` why. Point across; never restate.
- `docs/architecture/tech_stack.md` and `docs/architecture/domain_layer/` hold the decided
  design, and `docs/roadmap.md` the milestones and dates. The rest of `docs/architecture/`
  describes what is built.
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
- Requirements are intent, not observation. Behaviour found in code is not a requirement until
  the team agrees it is.
- Diagrams live in the repo as code (Mermaid), never as pasted images.

[TS]: ../docs/architecture/tech_stack.md
[TS-1]: ../docs/architecture/tech_stack.md#1-what-decides-close-calls
[TS-20]: ../docs/architecture/tech_stack.md#20-adoption-order
[TS-22]: ../docs/architecture/tech_stack.md#22-open-decisions
[DL]: ../docs/architecture/domain_layer/domain_layer_design.md
[DL-17]: ../docs/architecture/domain_layer/domain_layer_design.md#17-ubiquitous-language
[RM]: ../docs/roadmap.md
[ADRS]: ../docs/ADRs/README.md
[ADR-093]: ../docs/ADRs/ADR-093-the-implementation-plan-is-retired-and-a-roadmap-holds-the-dates.md
[ADR-024]: ../docs/ADRs/ADR-024-one-delivery-company-no-multi-tenancy.md
[ADR-025]: ../docs/ADRs/ADR-025-team-skills-in-the-repository.md
[ADR-038]: ../docs/ADRs/ADR-038-mise-pins-every-toolchain-and-core-moves-to-java-25.md
[ADR-088]: ../docs/ADRs/ADR-088-israel-local-time-for-every-date-and-schedule.md
[ADR-090]: ../docs/ADRs/ADR-090-scope-cuts-for-the-first-release.md
[ADR-118]: ../docs/ADRs/ADR-118-a-domain-package-above-30-files-splits-by-aggregate.md
[ADR-120]: ../docs/ADRs/ADR-120-the-application-layer-takes-an-actor-and-a-call-context-and-reads-through-query-ports.md
[ADR-123]: ../docs/ADRs/ADR-123-the-code-keeps-its-grouped-layout-and-three-components-take-new-names.md
[NFR-209]: ../docs/requirements/requirements.md#nfr-209
[NFR-262]: ../docs/requirements/requirements.md#nfr-262
