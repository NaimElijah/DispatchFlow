# ADR-135: The seven CI jobs are required checks on `dev` and `main`, and a pull request needs one approval

- **Status:** Accepted; amended by ADR-138 and ADR-151
- **Date:** 2026-11-24

## Context

[ADR-015] made CI one workflow with seven jobs, `core`, `optimizer`, `research`, `web`, `mobile`,
`contracts` and `audit`, each reporting a check of the same name on every pull request into `dev`
or `main`. It left branch protection to [#2][ISSUE-2]: "until issue #2 is done, a red job does not
block a merge".

`dev` and `main` are covered by one repository ruleset, "PR-only + protected : to main & dev",
created on 27 October 2026. On 24 November 2026 its rules were:

- no deletion and no force push;
- linear history;
- a pull request, merged by squash only, with **0 approving reviews**;
- a Copilot review on every push;
- **no required status check.**

Its one bypass is the repository's Admin role, always. That bypass is what lets `dev` reach `main`
through a merge commit, which the squash-only and linear-history rules would otherwise refuse.

Three ADRs wait on #2. [ADR-025] checks that the team skills still pass the rules. [ADR-034] would
let its question about `.claude/` changes go once review is required. [ADR-122] notes that nothing
blocks a red pull request and that nothing requires the human review it relies on.

## Decision

Decided on 24 November 2026. This amends ADR-015's "branch protection is not configured yet";
everything else in it stands.

1. **The ruleset requires the seven checks,** `core`, `optimizer`, `research`, `web`, `mobile`,
   `contracts` and `audit`. Only GitHub Actions can report them: the GitHub App with integration ID
   15368. A check is named by its job, so renaming, adding or removing a job changes the ruleset in
   the same pull request.
2. **A branch need not be up to date with its base before it merges.** CI on the push to `dev`
   checks the result after the merge.
3. **A pull request needs one approving review, and a later push dismisses it**
   (`dismiss_stale_reviews_on_push`). The approval then covers the code that merges. GitHub also
   dismisses it when the pull request's merge base changes, so merging `dev` into an approved branch
   needs a new approval too; a branch need not be up to date (decision 2), so that merge is needed
   only for a conflict. No separate approval of the last push is required: once a push has
   dismissed the approval, the author can't approve their own pull request.
4. **The Admin bypass applies to pull requests only** (`bypass_mode: pull_request`). An Admin can
   still merge a pull request past the rules, which the merge commit from `dev` to `main` needs. A
   push or a force push straight to `dev` or `main` is refused for everyone.
5. **The other rules stay as they were:** a pull request, squash only, linear history, no deletion
   or force push, and the Copilot review. The pull-request rule's
   `require_extra_approval_for_unattributed_changes` stays on, as it was; nothing here relies on it.
6. **The ruleset is held by GitHub, not by the repository.** This ADR records its rules. A change to
   them comes with a new ADR.
7. **What waited on #2:**
   - The team skills still pass. None of them merges a pull request, and the finish skill already
     requests a reviewer ([ADR-025]).
   - The skills keep [ADR-034]'s question for now. Review is now required and covers the diff that
     merges, so a change to the skills may drop it.
   - No automated review is made a required check ([ADR-122]). The Copilot review keeps commenting
     without blocking.

## Consequences

- **Easy:**
  - A red job blocks the merge of a pull request into `dev` or `main`.
  - Every teammate's pull request is read by one other person as it will merge, which ADR-034 and
    ADR-122 rely on.
  - A mistyped push or force push to `dev` or `main` is refused, the Admin's too.
- **Hard:**
  - A teammate's pull request waits for another person's approval, and a fix pushed after the
    approval, or a merge of `dev` to settle a conflict, waits for a new one.
  - When the audit turns red on a new advisory that no change caused ([ADR-016]), every pull request
    waits until the advisory is fixed or accepted. That is the point of a required check.
  - The audit also depends on outside hosts: Google's mirror of Maven Central ([ADR-134]), deps.dev,
    OSV.dev and the image registry `ghcr.io`. When one of them fails, every pull request waits. Rerun
    the job first; the Admin bypass is the last resort.
  - A job that is renamed or removed without the ruleset following blocks every merge, because its
    required check never reports ([ADR-015]).
  - Each pull request's head gets two runs of every check: one for the push, which tests the branch
    as it is, and one for the pull request, which tests its merge with the base. Only the second
    checks the pull request against a newer `dev`.
  - The workflow's concurrency group lets one push run wait at a time on `dev` or `main`, and a newer
    one cancels the waiting run. When pushes come fast, CI checks `dev`'s newest commit, not every
    merge.
- **Cost:** none in money; CI minutes are free on the standard runners of a public repository.
- **Risk:** the Admin bypass also skips the checks and the review of a pull request. An Admin merge
  of a red pull request is still possible, and the push to `dev` that follows shows the failure.

## Alternatives considered

- **Required checks with no approval.** Rejected: ADR-034 and ADR-122 rely on review being
  enforced.
- **Keep an approval through later pushes,** as the ruleset had it. Rejected: an author could push
  a change to `.claude/` or the workflow after the approval and merge it unread.
- **Also require an approval of the last push.** Not needed: a push that changes the diff already
  dismisses the approval, and the author can't approve their own pull request.
- **A bypass that also covers pushes** (`always`). Rejected: the merge from `dev` to `main` is a
  pull request, and nothing needs a direct push.
- **Require a branch to be up to date before it merges.** Rejected: every merge into `dev` would make
  each open pull request merge `dev` again and rerun CI. For a team of four that wait costs more than
  the rare clash it catches, and the push to `dev` catches that clash anyway.
- **One gate job as the only required check.** Not now: ADR-015 names it for when CI takes more than
  about 10 minutes and gains path filters.
- **A classic branch-protection rule for each branch.** Rejected: the ruleset already covers both
  branches with one set of rules.

## Revisit when

- A CI job is renamed, added or removed: update the required checks in the same pull request.
- CI takes more than about 10 minutes of wall time: add ADR-015's gate job and require it alone.
- Pull requests often wait more than two working days for an approval ([ADR-025]'s trigger): share
  the reviewer role, or reconsider the count.
- The bypass is used to merge a red pull request into `dev`: narrow who holds it.
- The pull-request bypass refuses the merge commit from `dev` to `main`: return the bypass to
  `always`, and record it here.

[ADR-015]: ADR-015-ci-one-workflow-one-job-per-component.md
[ADR-016]: ADR-016-dependency-audit-with-osv-scanner.md
[ADR-025]: ADR-025-team-skills-in-the-repository.md
[ADR-034]: ADR-034-ask-before-merging-changes-to-the-assistant-setup.md
[ADR-122]: ADR-122-the-finish-skill-runs-no-agent-review.md
[ADR-134]: ADR-134-the-audit-resolves-the-cores-pom-from-a-mirror-of-maven-central.md
[ISSUE-2]: https://github.com/NaimElijah/DispatchFlow/issues/2
