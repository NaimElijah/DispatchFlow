# ADR-074: Dependency licences are checked in CI against an allow-list, and GPL and AGPL services run unmodified

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

On 2026-11-13 it was confirmed that the licence of every dependency must be compatible with the
project's own ([NFR-51]), and that the notices its licences require ship with the apps
([NFR-154]). This ADR settles the marker "ADR pending: dependency licence check".

The project's own licence is proprietary, all rights reserved ([ADR-017]). That ADR found every
planned dependency permissive and named three licences:

- Apache-2.0 for Google OR-Tools and Spring;
- MIT for React, React Native, Expo, FastAPI and PyVRP;
- BSD-2-Clause for OSRM.

It also warned that "a new dependency with a copyleft licence, such as GPL or AGPL, would have to be
checked against this licence before it is added". It rejected AGPL for the project's own code
because its terms reach anyone who runs a modified version as a network service.

The forces:

- **Rule 6 of the tech stack:** every tool is free and open source, or free for students, and "every
  licence is checked against an allow-list" ([TS §1]).
- **Some wanted services are copyleft.** [TS §18] names Nominatim and the Grafana stack as GPL or AGPL
  services. Nominatim runs only if GovMap's terms fail ([ADR-085]).
- **A new dependency the list must hold.** The `alns` package is MIT ([ADR-092]).
- **No licence check exists today.** [ADR-016] audits vulnerabilities only.

## Decision

CI checks the licence of every dependency against an allow-list. Anything not on it fails the check
until a reviewed change decides it.

- **The check** sits in the SBOM and licence stage of CI: CycloneDX SBOMs from Maven, Python and npm,
  and a licence allow-list check against [ADR-017] ([TS §18]). It is adopted at the pilot.
- **The list** is a file in the repository. The sources do not enumerate its entries beyond
  [ADR-017]'s three, so its first version is written, and reviewed, when the check is built.
- **`alns` joins the list.** Its licence is MIT ([ADR-092]). The SBOM row of the tech stack lists it.
- **GPL and AGPL software runs only as an unmodified, separate container.** It is never linked into
  the core or the optimizer. [TS §18] names the Grafana stack, and Nominatim only as GovMap's
  fallback. Each is recorded in the licence check ([TS §18]).
- **Notices ship with the apps.** A notices file goes into both apps ([NFR-154], [TS §18]).
- **A new dependency's record** states what it replaces, its maintenance status and its licence
  (`.claude/CLAUDE.md`, Engineering judgment). The check enforces the licence part.

The Maven SBOM also answers [ADR-016]'s open Java gap, which that ADR says needs "its own ADR,
because it adds a build plugin". This ADR does not choose the plugin.

## Consequences

- **Easy:** a copyleft library cannot enter unnoticed, and the notices file comes from the same data.
- **Easy:** the exception for each GPL or AGPL service sits in one place.
- **Hard:** the list needs upkeep. Each new licence is a decision, and a transitive dependency with
  an unlisted licence stops a build.
- **Hard:** the exception depends on a fact the team must keep true: the service is unmodified. A
  patched build of Nominatim or Grafana would leave the rule.
- **Cost:** a CycloneDX plugin or tool for each of Maven, Python and npm, adopted at the pilot
  ([TS §18]). Their licences and maintenance status are recorded before they are added.
- **Risk:** the check is only as good as the licence data in package metadata. The sources give no
  control for a missing or wrong entry.
- **Risk:** the fallback for background location is a paid library, Transistor's, "paid licence for
  Android release builds" ([TS §11]). A paid licence is not an allow-list entry. Adopting it is a
  separate decision.
- **Risk:** the pilot adoption leaves earlier dependencies unchecked. [ADR-017]'s survey is the only
  check until then.

## Alternatives considered

- **A deny-list of copyleft licences.** Not chosen. [TS §1] says allow-list, and the sources give no
  further reason. An allow-list also forces a decision on every licence that is neither known good
  nor known bad.
- **Review by hand at each pull request.** Lost on [NFR-51] covering every dependency, transitive
  ones included, which a reviewer does not see.
- **Releasing under an open licence so copyleft fits.** Rejected in [ADR-017]: an open release cannot
  be withdrawn, and it would close options before the team has chosen.

## Revisit when

- The team decides to open-source the project, or the university's policy changes who may licence
  the code ([ADR-017]). The allow-list's premise changes, and so does the copyleft rule.
- A dependency the project needs has a licence that is not on the list, as the paid background-location
  library would be. Decide it in an ADR first.
- Nominatim or a Grafana-stack component has to be modified. The exception no longer holds.

[ADR-016]: ADR-016-dependency-audit-with-osv-scanner.md
[ADR-017]: ADR-017-proprietary-licence.md
[ADR-085]: ADR-085-govmap-geocodes-addresses-with-nominatim-as-the-fallback.md
[ADR-092]: ADR-092-the-alns-package-runs-our-solvers-outer-loop.md
[NFR-51]: ../requirements/requirements.md#nfr-51
[NFR-154]: ../requirements/requirements.md#nfr-154
[TS §1]: ../architecture/tech_stack.md#1-what-decides-close-calls
[TS §11]: ../architecture/tech_stack.md#11-phone-app
[TS §18]: ../architecture/tech_stack.md#18-cicd-and-supply-chain
