# ADR-159: A contract change goes with its provider and the consumers that call what it changes

- **Status:** Accepted
- **Date:** 2026-11-28

## Context

[ADR-006] made the contracts contract-first: "A contract changes in the same pull request as every
side it touches: the provider, and each consumer." `contracts/CLAUDE.md` read that as every
consumer the contract's table names, and applied it to the generators too. The next operation
either contract gained was to add the generator, and the validation generated from it, "to the
provider and every consumer in the same pull request".

The client API's first operations are #149's `openPlan` and `requestSolve`. The table names two
consumers of the client API, and neither calls them yet:

- **The web app.** Its screens for a plan arrive with later tickets (#225, #203, #224, #207). It
  has a hand-written `Problem` type, pinned to the contract by a test until its client is generated
  ([ADR-153]), and no call to the core.
- **The phone app.** It is outside the pnpm workspace and calls nothing.

Read literally, the rule put five things into the one pull request that serves the first
operation:

- the core's generator, with a new image context and an architecture rule (#161);
- the use cases behind the operations (#184);
- the contract itself (#149);
- the web app's generated client, with typed linting, a new image input and an ADR of its own
  (#148);
- in principle, a generator for the phone app.

That is two languages, two toolchains and about a dozen decisions in one review. One red check
on either side would hold every part back.

What the rule protects is that no side drifts from the contract: a provider never serves what the
contract doesn't say, and a consumer never calls what the contract doesn't have. A consumer that
calls none of a change's operations can't drift from them.

## Decision

Decided on 28 November 2026.

1. **A contract change goes in the same pull request as its provider and each consumer that calls
   an operation it adds or changes.** The provider serves every operation in the pull request that
   adds it. For the client API, the twin of rule 12 makes that so ([ADR-140] decision 2).
2. **A consumer that calls none of the changed operations changes nothing,** but its generated
   code is rebuilt and still passes `just check`. A breaking change still gets a new major-version
   folder ([ADR-006]), whoever calls it.
3. **A consumer's generator arrives with the first operation it calls, or earlier in a pull request
   of its own:**
   - the web app's with #148's second pull request, right after #184's;
   - the phone app's with its first call.

This amends [ADR-006]'s *Contract-first* point. The rest of it stands.

## Consequences

- **Easy:**
  - The first operations ship with their provider and its use cases, and no client is built that
    nothing calls.
  - Each pull request is reviewed in one language, by the reviewers that language needs.
- **Hard:**
  - For a while the contract has operations that no consumer calls. A reviewer reads the contract
    on its own, as [ADR-006] already lets a frontend build against an operation later.
  - Between #184's pull request and #148's second, the web app still types `Problem` by hand. Its
    drift test ([ADR-153]) keeps the type in step until the generated one replaces it.
- **Cost:** none in tools. The rule's text changes in `contracts/CLAUDE.md` and
  `src/backend/CLAUDE.md`.
- **Risk:** a consumer could start calling an operation with a hand-written request before its
  generator exists. The web app's `src/frontend/CLAUDE.md` already forbids it, and #148 makes
  `@dispatchflow/api-client` the only way to the server.

## Alternatives considered

- **One pull request for #149's writes, #161, #184 and #148, as the rule read.** Rejected for its
  size and its mix of languages: every decision in it would wait for the slowest one.
- **The web app's client first, before any operation exists.** [ADR-153] already turned it down
  for #173, since the generated client would have no operation to type and #173 would wait on its
  decisions. It still holds: the client's tests need at least one operation.
- **The literal rule, with the web app's client added to #184's pull request.** Rejected: it
  repeats the first alternative for the one consumer that exists.

## Revisit when

- A consumer outside this repository calls a contract. It can't be changed in the same pull
  request, so the contract would need a published, versioned artefact ([ADR-006]).
- `oasdiff` runs on every pull request ([ADR-094]): check whether its breaking-change report
  should name the consumers that call the changed operations.

[ADR-006]: ADR-006-versioned-openapi-contracts.md
[ADR-094]: ADR-094-each-pull-requests-contract-check-compares-against-its-base-branch.md
[ADR-140]: ADR-140-the-app-wide-web-package-sits-above-the-modules.md
[ADR-153]: ADR-153-the-web-apps-shared-form-refusal-and-table-pieces.md
