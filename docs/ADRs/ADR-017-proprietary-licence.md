# ADR-017: Release the code under a proprietary licence, all rights reserved

- **Status:** Accepted
- **Date:** 2026-10-30

## Context

DispatchFlow is a student project that plans a pilot with a real customer ([FR-47]), not yet
named ([Q-01]). The repository needs a licence from the start. Code with no licence is under the
author's copyright by default, and a visitor cannot tell what they may do with it.

Four forces apply:

- **The team has not decided how the code will be used** after the project: a pilot, commercial
  use, or release as open source. The licence should not decide that for them.
- **An open-source release cannot be withdrawn.** Anyone who received a version under an open
  licence keeps the right to use it under that licence. A proprietary licence can be replaced
  by an open one later.
- **The dependencies do not force a choice.** Every planned dependency is permissively licensed:
  - Google OR-Tools and Spring: Apache-2.0.
  - React, React Native, Expo, FastAPI and PyVRP: MIT.
  - OSRM: BSD-2-Clause.
- **The copyright is shared** between four people: Naim Elijah, Shai Manor, Bentzion Hadad and
  Moshe Klein.

One point is open. The team has not yet checked Ben-Gurion University's rules on intellectual
property in student projects. They may give the university a claim on the code, or limit what
the team can license.

## Decision

We release the code as proprietary, all rights reserved.

- **`LICENSE` at the root** names the copyright holders (Naim Elijah, Shai Manor, Bentzion Hadad
  and Moshe Klein, 2026), forbids use, copying, modification, distribution and sale without their
  prior written permission, and states that third-party components keep their own licences.
- **The OpenAPI contracts carry the same licence** in their `info.license` block ([ADR-006]): the
  name "Proprietary, all rights reserved" and the SPDX identifier
  `LicenseRef-DispatchFlow-Proprietary`, in `contracts/client-api/v1/openapi.yaml` and
  `contracts/optimization-api/v1/openapi.yaml`.
- **We do not choose an open-source licence yet.** The choice is open for the reasons above,
  and this ADR is the place to reverse it.

## Consequences

- **Easy:** every option stays open. A pilot, a commercial agreement or an open-source release
  can each be decided later.
- **Easy:** the licence can be loosened at any time, and an open release can be made from a
  version of the code we choose.
- **Easy:** no dependency conflicts with the licence, because none of the planned ones is
  copyleft.
- **Hard:** nobody outside the four holders may use or contribute to the code without written
  permission. This includes the pilot customer, who needs a written permission to run it.
- **Hard:** a later change of licence needs the agreement of all four holders, and possibly of
  the university (see the open point above).
- **Hard:** a new dependency with a copyleft licence, such as GPL or AGPL, would have to be
  checked against this licence before it is added.
- **Hard:** the licence text appears in more than one place (`LICENSE` and the `info.license`
  block of each contract), so a change of licence has to update all of them together.
- **Cost:** the project cannot benefit from outside contributions or from the reuse that comes
  with an open-source release.

## Alternatives considered

- **MIT.** Rejected: a release under it cannot be taken back for the versions already published,
  and it closes the commercial options before the team has chosen among them.
- **Apache-2.0.** Rejected for the same reasons.
- **AGPL-3.0.** Rejected for the same reasons. Its terms also apply to anyone who runs a
  modified version as a network service, which would shape a commercial deployment before the
  team has decided on one.

## Revisit when

- **Ben-Gurion University's policy requires another licence,** or gives the university a claim
  that changes who may license the code. Check the policy before the pilot.
- **The team decides to open-source the project.** A new ADR supersedes this one and names the
  licence.
- **The pilot or a commercial agreement needs terms** that the all-rights-reserved text cannot
  give, such as a licence to use the software. Replace the `LICENSE` text with those terms.

[ADR-006]: ADR-006-versioned-openapi-contracts.md
[FR-47]: ../requirements/requirements.md#research-and-evaluation
[Q-01]: ../requirements/requirements.md#open-questions
