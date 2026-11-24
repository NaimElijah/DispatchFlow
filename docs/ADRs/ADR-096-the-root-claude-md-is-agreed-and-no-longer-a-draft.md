# ADR-096: The root CLAUDE.md is agreed, and is no longer a draft

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

[ADR-018] keeps the assistant's context in the repository. It marks `.claude/CLAUDE.md` "a draft
until the team agrees it (issue #1)", and the file opened with a banner that said so: "raise anything
you disagree with at the team meeting rather than working around it".

Issue #1 ("chore: initialize the project structure") was closed on 2026-10-30, and it records no
agreement on the file. On 2026-11-13 and 2026-11-14 every open design decision was settled. Claude
Code loads the root file at the start of every session, so the banner told every session that the
rules were tentative.

## Decision

Decided on 2026-11-14: `.claude/CLAUDE.md` is agreed.

- The draft banner is removed.
- The file changes the way the rest of the repository does: by a pull request that is reviewed as
  code ([ADR-018]). A member who disagrees with a rule raises it for a decision, or opens a pull
  request that changes it.

## Consequences

- **Easy:** every session reads the rules as agreed, not as a proposal.
- **Hard:** a rule a member disagrees with is changed by a reviewed pull request, not ignored on the
  strength of a banner.
- **Cost:** none.
- **Risk:** a member may not know that the file is now agreed. The pull request that removes the
  banner says so.

## Alternatives considered

- **Keep the banner.** Rejected: it tells every session that the rules are tentative, which they no
  longer are.
- **Name a contact in the banner.** Rejected: the rules stand on their own, and how to
  change them is the same as for any file.

## Revisit when

- The team changes how it has taken decisions since 2026-11-13.
- A rule in the file is disputed and the pull request process does not settle it.

[ADR-018]: ADR-018-ai-assistant-context-in-the-repository.md
