# ADR-132: Unbuilt bodies say "To Be Implemented." and name no milestone

- **Status:** Accepted
- **Date:** 2026-11-24

## Context

[ADR-119] decision 2 had every throwing body name its roadmap milestone, in the exception message
and in a Javadoc line (`Built in M<n>`). The skeleton now holds hundreds of such bodies. Four
facts bear on that choice:

- **The numbers repeat the roadmap.** [`roadmap.md`][RM] owns the milestones and their dates. A
  number in a body says the same thing a second time, and it goes stale whenever the roadmap moves
  work from one milestone to another.
- **The team tracks the work through GitHub issues.** An issue cites the requirement rows and the
  ADRs it builds, and no milestone number. Nobody looks a body up by its milestone.
- **The feature issue template still asked for a milestone.** So an issue was asked for the number
  that the code then repeated.
- **The per-milestone list was a grep and nothing more.** [ADR-119] rejected a marker exception and
  a `Milestone` enum for want of a report anyone had asked for, so `grep "Built in M5"` was the
  only way to list one milestone's work.

## Decision

This amends [ADR-119] decision 2, the marker that [ADR-121] decision 13 quotes, and the remedy in
[ADR-130]'s revisit trigger. Everything else in them stands, including ADR-119 decision 5's rule
that the Javadoc tag goes when its body is built.

1. **An unbuilt Java body throws** `UnsupportedOperationException("To Be Implemented.")`.
2. **An unbuilt Python body raises** `NotImplementedError("To Be Implemented.")`.
3. **The Javadoc or docstring tag reads "To Be Implemented."** It replaces `Built in M<n>`, and goes
   when the body is built ([ADR-119] decision 5). A fact the tag used to carry, such as what the
   body is built with, follows in a sentence of its own. On a declared type with no body of its own,
   such as a record, an enum, a port or an error type, the tag says that the feature using the type
   is not built yet, and it goes when the first body that uses the type is built.
4. **The text is exact everywhere:** capital T, B and I, and the full stop, as a literal at each
   site. Prose that says a part is missing says "not built yet" or "not declared yet", so the phrase
   marks only unbuilt bodies.
5. **A code comment, a `CLAUDE.md` rule or an agent prompt names the feature a gap waits for,** such
   as sign-in or geocoding, and never a milestone's number or title. Where [ADR-130]'s revisit
   trigger says to name the milestone that fills a folder, the folder's description names the
   feature instead.
6. **The feature issue template has no milestone section.** The issue cites the requirement it
   delivers.
7. **The planning documents keep their milestones.** The roadmap, the tech stack and the other
   design documents still say when each part is built ([ADR-093]).

## Consequences

- **Easy:**
  - `grep -rF "To Be Implemented."` lists all unbuilt work: every unbuilt body, and the declared
    types that wait for one.
  - A roadmap change touches no source file.
  - The tag and the message are one phrase, so a reader sees at once which bodies are unbuilt.
- **Hard:** the grep no longer lists one milestone's share of the work. The issues say who builds
  what and when.
- **Cost:** one edit across every skeleton file, the `CLAUDE.md` rules and the agent prompt that
  named milestones, the issue template, and the documents that quote the message.
- **Risk:** an unbuilt body that no issue covers goes unnoticed. The grep still finds it.

## Alternatives considered

- **Keep the milestone numbers.** Rejected: they duplicate the roadmap and drift from it.
- **A marker exception and a `Milestone` enum.** Rejected: [ADR-119] rejected it, and its reason
  holds. It adds two types for a report nobody has asked for.
- **A shared constant for the message.** Rejected: a grep would find its one definition, not the
  unbuilt sites.
- **Name the issue number in each body.** Rejected: hundreds of bodies would need new tags each
  time issues split or merge.

## Revisit when

- The team needs unbuilt work listed per milestone again.
- The issues stop covering every unbuilt body.

[ADR-093]: ADR-093-the-implementation-plan-is-retired-and-a-roadmap-holds-the-dates.md
[ADR-119]: ADR-119-the-application-layer-is-declared-ahead-as-signatures-whose-bodies-throw.md
[ADR-121]: ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md
[ADR-130]: ADR-130-every-folder-is-declared-ahead-empty-and-documented.md
[RM]: ../roadmap.md
