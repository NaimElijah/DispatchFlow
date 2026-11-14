# ADR-070: We self-check against OWASP ASVS 5.0 Level 1 and build to WCAG 2.2 AA, with no separate target for IS 5568

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

On 2026-11-13 the owner, Naim Elijah, decided for the team which standards the security and the
accessibility work is measured against. This ADR settles the marker
"ADR pending: security and accessibility standards", which [NFR-40] and [NFR-46] carry. It also
records his answer to [Q-59] on IS 5568, the Israeli web-accessibility standard.

The forces:

- **A pilot with real data needs a bar to pass.** [NFR-40] asks for a self-check against Level 1 of
  the OWASP Application Security Verification Standard (ASVS) 5.0 before the pilot handles real
  data. [ADR-030], [ADR-036] and [ADR-045] already cite ASVS items when they reject a weaker
  design.
- **Both apps serve every role** ([ADR-035]), and the driver screens are used one-handed and in
  sunlight ([NFR-137], [NFR-138]). [NFR-46] asks for WCAG 2.2 level AA in both.
- **Automated checks do not cover all of WCAG.** [NFR-142] has a team member audit by hand what they
  miss.
- **The standard that applies by law is not known.** [Q-59] assumed on 2026-11-04, unchecked, that
  IS 5568 reaches only a public page such as the customer tracking page ([FR-117]). Legal advice on
  personal data is already planned ([Q-42]).
- **The repository is private**, so some free scanners are not available ([TS §17.3]).

## Decision

- **Security: ASVS 5.0 Level 1, as a self-check.** It passes before the pilot handles real data
  ([NFR-40]). The check is a list in `docs/operations/`, with each item linked to a test or to a
  review ([TS §17.3]).
  - Semgrep Community Edition runs static analysis in CI.
  - The OWASP ZAP baseline scan runs against the usability environment ([NFR-55]).
  - Items that need a decision are settled by ADR where the design changes, as [ADR-036] and [ADR-045]
    did. [Q-98] says ASVS 14.3.1 is a Level 1 item, which is why device storage was
    decided before the self-check ([ADR-076]).
- **Accessibility: WCAG 2.2 level AA in both clients** ([NFR-46]).
  - Automated checks run in CI: axe through the end-to-end tests ([NFR-141], [TS §17.1]).
  - A team member audits by hand, with NVDA, VoiceOver and TalkBack, the criteria the automation does
    not cover. It passes with no Level A or AA failure before the pilot ([NFR-142]).
  - Lint rules help while code is written: `jsx-a11y` in the web app, the React Native accessibility
    rules in the phone app ([TS §10], [TS §11]).
  - Dragging has a non-drag alternative, as WCAG 2.2 SC 2.5.7 asks ([TS §10]).
- **IS 5568 ([Q-59]), the owner's answer:** nothing changes in what is built. WCAG 2.2 AA covers the
  standard's technical bar. Whether its legal duties, such as an accessibility statement, reach the
  apps joins the legal advice of [Q-42]. They are assumed to apply to the public tracking page
  ([FR-117]). This ADR does not check that claim against the text of IS 5568.

## Consequences

- **Easy:** two named standards give a reviewer a fixed list to check against, and a pass or fail.
- **Easy:** both tools in the security check are free for a private repository ([TS §17.3]).
- **Easy:** one accessibility target serves both clients and the public page.
- **Hard:** the ASVS list needs an owner and a date. The sources name neither, and the check is due
  before the pilot (M13).
- **Hard:** the manual audit needs three screen readers, NVDA, VoiceOver and TalkBack, across a
  desktop and phones. Whoever runs it needs all three.
- **Cost:** the manual accessibility audit takes team time that no milestone sizes yet. Axe in the
  end-to-end tests and lint rules cost nothing beyond setup.
- **Risk:** Level 1 is the lowest ASVS level. It does not ask for a second factor, and [ADR-036]
  accepts that for Admins.
- **Risk:** IS 5568 may ask for more than WCAG 2.2 AA says, or for a statement the apps lack. Legal
  advice comes before the pilot handles real data ([Q-42]), and until then the claim rests on the
  owner's answer.
- **Risk:** a self-check is the team checking itself. The sources name no outside tester.

## Alternatives considered

- **ASVS Level 2.** Not chosen for the pilot. [ADR-030] found that a second sign-in factor is a
  Level 2 item, and [Q-81] keeps it out. Level 2 is the next step (see Revisit when).
- **CodeQL for static analysis.** Lost on being free only for public repositories, and this one is
  private ([ADR-017], [TS §17.3]). Semgrep Community Edition is free for private ones.
- **Automated accessibility checks only.** Lost on [NFR-142]: automation does not cover every
  criterion, so a person audits the rest.
- **IS 5568 as a second, separate target.** Lost on the owner's answer to [Q-59]: WCAG 2.2 AA already
  covers its technical bar, and the legal question belongs to the legal advice.

## Revisit when

- The self-check moves to ASVS Level 2, as [ADR-036] and [ADR-045] anticipate, or the company asks for
  a second factor ([FR-128]).
- The legal advice of [Q-42] says that IS 5568 reaches the web app or the phone app, or that it asks
  for more than WCAG 2.2 AA. Add what it asks for.
- The pre-pilot manual audit finds Level A or AA failures the automated checks missed. Widen the
  automated rules so the next release catches them.
- The pilot's company or a recipient reports a barrier on the public tracking page.

[ADR-017]: ADR-017-proprietary-licence.md
[ADR-030]: ADR-030-admin-issued-credentials-and-token-revocation.md
[ADR-035]: ADR-035-both-apps-serve-every-role.md
[ADR-036]: ADR-036-one-account-per-person-signed-in-by-email.md
[ADR-045]: ADR-045-generation-counters-revoke-tokens.md
[ADR-076]: ADR-076-admin-and-dispatcher-data-stays-in-memory-on-devices.md
[FR-117]: ../requirements/requirements.md#live-operations-and-re-optimization
[FR-128]: ../requirements/requirements.md#company-users-and-roles
[NFR-40]: ../requirements/requirements.md#nfr-40
[NFR-46]: ../requirements/requirements.md#nfr-46
[NFR-55]: ../requirements/requirements.md#nfr-55
[NFR-137]: ../requirements/requirements.md#nfr-137
[NFR-138]: ../requirements/requirements.md#nfr-138
[NFR-141]: ../requirements/requirements.md#nfr-141
[NFR-142]: ../requirements/requirements.md#nfr-142
[Q-42]: ../requirements/requirements.md#open-questions
[Q-59]: ../requirements/requirements.md#open-questions
[Q-81]: ../requirements/requirements.md#open-questions
[Q-98]: ../requirements/requirements.md#open-questions
[TS §10]: ../architecture/tech_stack.md#10-web-app
[TS §11]: ../architecture/tech_stack.md#11-phone-app
[TS §17.1]: ../architecture/tech_stack.md#171-by-layer
[TS §17.3]: ../architecture/tech_stack.md#173-security-testing
