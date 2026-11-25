# ADR-069: Sign-in uses signed access tokens, rotating refresh tokens and Argon2id, over HTTPS through Caddy

- **Status:** Accepted; extended by ADR-121, ADR-140 and ADR-143
- **Date:** 2026-11-14

## Context

On 2026-11-13 the security and access rows of the requirements and the core service's
authentication rows in the tech stack were confirmed. On 2026-11-14 the tech stack's infrastructure
rows (network, domain and TLS, reverse proxy) were confirmed, and three further decisions were made
that this ADR records. This ADR settles the marker "ADR pending: sign-in and transport security", which
[NFR-26], [NFR-36] and [NFR-39] carry.

This extends [ADR-036], which decided who signs in, with what, and how links and limits behave. It
does not repeat them. [ADR-045] decided how revocation works, with generation counters. This ADR
names the mechanisms underneath: token formats, the password function, the cookie, the transport and
the headers. None of it is built; `contracts/client-api/v1/openapi.yaml` declares no operation yet.

The forces:

- **First-party apps for one company** ([ADR-024]). The sign-in rules of [ADR-036] are simpler to own
  than to configure in a general identity server ([TS §6], *Authentication* row).
- **Passwords are stored only as salted hashes from a password function** ([NFR-36]). Tokens, offline
  route data and queued reports are encrypted on the phone ([NFR-39]).
- **All traffic is encrypted in transit.** An unencrypted request is refused or redirected, never
  served ([NFR-26]).
- **A self-check against ASVS 5.0 Level 1 passes before the pilot handles real data** ([NFR-40],
  [ADR-070]).
- **One VM, no open SSH port** ([TS §15], *Network* row), so the first Admin cannot be created by
  logging in and running a command.

## Decision

### Tokens and passwords

- **Access token:** a JWT signed with ES256, valid 15 minutes ([NFR-86]), decoded by Spring Security's
  resource server with Nimbus JOSE + JWT. It carries the user's and the session's generation counters
  ([ADR-045]). The signing key comes from SSM, and two keys are accepted during a rotation, told apart
  by `kid` ([PL §16.1][PL-161]).
- **Refresh token:** opaque, 256 random bits, stored only as a SHA-256 hash, valid 14 days
  ([NFR-87], [NFR-91]). Each is exchanged once, with a 30-second grace for a repeat ([NFR-22],
  [NFR-178]).
- **Where tokens live.** In the web app the access token is held in memory, and the refresh token is a
  `__Host-` cookie with `Path=/` that is Secure, HttpOnly and SameSite=Strict, with a double-submit
  CSRF cookie that also takes the `__Host-` prefix, and a CSRF header on refresh ([NFR-106],
  [NFR-107]). It is `__Host-` and not `__Secure-` because a
  sibling host, such as GlitchTip's, could plant a `__Secure-` cookie; a `__Host-` cookie cannot
  be set by another host. A `__Host-` cookie cannot carry a narrower path, so the browser sends it
  on every request to the host. Only the refresh and sign-out endpoints read it, and a test checks
  that a request elsewhere that carries only the cookie gets 401. In the phone app both tokens are in `expo-secure-store`
  ([PL §16.1][PL-161]).
- **Passwords:** Argon2id through Spring Security's encoder with Bouncy Castle, at OWASP's minimum
  (19 MiB, 2 iterations, parallelism 1) or above, "measured on the VM" ([PL §16.2][PL-162]). The
  policy is at least 8 characters, a bundled list of the 3,000 most common passwords refused, and no
  composition rules ([NFR-95]).
- **Link tokens and secrets:** `SecureRandom` 256-bit link tokens, stored as hashes. HMAC-SHA-256 with
  a secret key for opt-out hashes ([NFR-202]) and research pseudonyms ([NFR-153]).
  `MessageDigest.isEqual` for constant-time secret checks ([NFR-192]). The JDK covers all of it, so no
  library is added ([TS §6], *Link tokens and hashes* row).
- **The keyed-hash keys rotate by version.** The keys for opt-out hashes and research pseudonyms each
  have a version. Every hash records the version of the key that made it, and new hashes use the new
  key. A rotation ends like this:
  - every opt-out whose phone is still stored is re-hashed under the new key at the rotation;
  - while any opt-out remains under an old key version, that key is kept for lookups only. The
    accepted risk is the key's continued existence, never a lost opt-out ([NFR-202]);
  - research pseudonyms simply start anew under the new key.
- **No second factor for the pilot.** TOTP stays Optional, following [FR-128] (a Could) and [Q-81].

### Transport and headers

- **HTTPS everywhere, through Caddy.** Caddy takes certificates from Let's Encrypt automatically and
  serves HTTP/2 and HTTP/3, so several open event streams do not meet the browser's per-host
  connection limit. DNS is in Route 53. The security group opens only ports 80 and 443, and there is
  no SSH port: SSM Session Manager replaces it ([TS §15], *Network*, *Domain and TLS* and *Reverse
  proxy* rows).
- **Headers:** HSTS (`max-age=31536000; includeSubDomains` in the plan's Caddyfile sketch), a Content
  Security Policy and `frame-ancestors 'none'` ([NFR-55], [PL §23][PL-23]). Caddy also sends
  `X-Content-Type-Options: nosniff` and a site-wide `Referrer-Policy: no-referrer` ([TS §15],
  *Reverse proxy* row).
- **The Content Security Policy gains three directives** on 2026-11-14: `base-uri 'none'`,
  `form-action 'self'` and `object-src 'none'`. Its `connect-src` names GlitchTip, replacing the
  hosted Sentry host in the plan's sketch ([ADR-079]). With the plan's other directives, the policy
  reads:

  ```text
  default-src 'self'; img-src 'self' blob: data:; connect-src 'self' <GlitchTip address>;
  worker-src blob:; frame-ancestors 'none'; base-uri 'none'; form-action 'self'; object-src 'none'
  ```

  The plan's sketch is the only source for the first five directives, and the GlitchTip address is not
  chosen yet.
- **M0 checks that the web app renders under this policy.** Mantine may inject a `<style>` element that
  needs a nonce or a hash ([TS §10]). If it does, the fix is recorded then. The check goes into the
  roadmap's M0 entry.
- **Email has link and open tracking off**, with SPF, DKIM and DMARC for SES ([NFR-223], [TS §15]).

### The first-Admin and lost-Admin step

The step that creates the first Admin, or replaces a lost one, is an audited server step behind a
profile, run through SSM ([FR-291], [FR-292], [FR-346], [PL §16.2][PL-162]). On 2026-11-14 it was
decided that **every admin step that takes a person's identifier reads it at a prompt that does not
echo** ([FR-393]): the first-Admin and lost-Admin steps, the deactivation of the Admins a request
names ([FR-346]) and the data requests ([NFR-49], [NFR-185]). The identifier never enters a command line, the shell history or
SSM's history. SSM records who opened each session on the VM and when (CloudTrail), and records no
session stream ([NFR-229]), so nothing typed or printed in a session is kept. An export goes to an
encrypted file that is deleted on a set date, never to the terminal ([FR-409]). The file is
encrypted to the requesting member's public key, so no decryption key is on the VM. The audit record holds the account ID. This replaces the plan's sketch, which passed `--email=` on the command line. The
decision goes into [FR-291]'s note and the runbook row ([TS §15]).

## Consequences

- **Easy:** the JDK, Spring Security, Nimbus and Bouncy Castle (for Argon2id) cover every mechanism. There is no identity service to
  run on a single VM ([ADR-024]).
- **Easy:** a verifier can check an ES256 token without being able to sign one.
- **Easy:** Caddy renews certificates with no cron job ([TS §15], *Reverse proxy*).
- **Hard:** the Argon2id parameters are not final. They are measured on the VM, which is built before
  the pilot ([TS §15]).
- **Hard:** a Content Security Policy this strict can break a framework that injects styles. M0 tests
  it so that the answer arrives before any screen depends on it.
- **Hard:** a non-echoing prompt inside an SSM session is a runbook step, and so is the encrypted
  export with its deletion date. How the prompt is done there is
  not verified here.
- **Hard:** a keyed-hash key rotation is a version bump and a re-hash of the stored opt-outs. While
  any opt-out remains under an old version, the old key stays, for lookups only.
- **Cost:** key rotation is a runbook step with two accepted keys, and every secret has a rotation
  step ([TS §15], *Runbook* and *Secrets* rows).
- **Risk:** what Caddy does with a plain request on port 80 is its default, which the sources do not
  state. [NFR-26] allows a refusal or a redirect, and the ASVS self-check and the ZAP scan ([TS §17.3])
  show which one happens. Not verified.
- **Risk:** a stolen access token works until it expires, 15 minutes, or is revoked. Revocation takes
  effect on the next request through [ADR-045]'s counters.
- **Risk:** there is no second factor behind a password, including for Admins ([ADR-036]).

## Alternatives considered

- **Keycloak.** Lost on another heavy service on one VM, with link and revocation behaviour that
  differs from [ADR-036] ([TS §6]).
- **Spring Authorization Server.** Lost on being a full OAuth2 server, with flows that first-party
  apps do not need ([TS §6]).
- **Hosted identity (Auth0, Cognito).** Lost on another vendor and its own email flows ([TS §6]), and
  on personal data and telemetry leaving Israel ([TS §23]).
- **HS256 tokens.** Lost on any holder of the key being able to mint tokens ([TS §6]).
- **bcrypt.** Lost on not being memory-hard, and on ignoring password bytes past 72 ([TS §6]).
- **Composition rules for passwords** (a digit, a symbol). Lost on being longer to explain and no
  safer ([TS §6]).
- **The email on the command line,** as the plan sketched. Rejected on 2026-11-14: the
  email would enter the history and the session log.
- **SMS codes and a second factor for the pilot.** Rejected in [ADR-036] and [Q-81].

## Revisit when

- The self-check moves to ASVS Level 2, which asks for a second factor ([ADR-030] found item 6.3.3 at
  that level). Promote TOTP from Optional.
- The M0 check shows the web app cannot render under the policy without weakening it. Record the
  nonce or hash fix, or the exception, in a new ADR.
- The Argon2id measurement on the VM shows sign-in breaking [NFR-59]'s 500 ms at the 95th percentile.
  Lower the cost, or move the hash off the request path.
- The company wants to sign in through its own identity provider. Compare Keycloak then.
- A plain request on port 80 is served instead of refused or redirected.

[ADR-024]: ADR-024-one-delivery-company-no-multi-tenancy.md
[ADR-030]: ADR-030-admin-issued-credentials-and-token-revocation.md
[ADR-036]: ADR-036-one-account-per-person-signed-in-by-email.md
[ADR-045]: ADR-045-generation-counters-revoke-tokens.md
[ADR-070]: ADR-070-security-and-accessibility-standards.md
[ADR-079]: ADR-079-client-errors-and-crashes-go-to-self-hosted-glitchtip.md
[FR-128]: ../requirements/requirements.md#company-users-and-roles
[FR-291]: ../requirements/requirements.md#company-users-and-roles
[FR-292]: ../requirements/requirements.md#company-users-and-roles
[FR-346]: ../requirements/requirements.md#company-users-and-roles
[FR-393]: ../requirements/requirements.md#company-users-and-roles
[FR-409]: ../requirements/requirements.md#company-users-and-roles
[NFR-22]: ../requirements/requirements.md#nfr-22
[NFR-26]: ../requirements/requirements.md#nfr-26
[NFR-36]: ../requirements/requirements.md#nfr-36
[NFR-39]: ../requirements/requirements.md#nfr-39
[NFR-40]: ../requirements/requirements.md#nfr-40
[NFR-49]: ../requirements/requirements.md#nfr-49
[NFR-55]: ../requirements/requirements.md#nfr-55
[NFR-59]: ../requirements/requirements.md#nfr-59
[NFR-86]: ../requirements/requirements.md#nfr-86
[NFR-87]: ../requirements/requirements.md#nfr-87
[NFR-91]: ../requirements/requirements.md#nfr-91
[NFR-95]: ../requirements/requirements.md#nfr-95
[NFR-106]: ../requirements/requirements.md#nfr-106
[NFR-107]: ../requirements/requirements.md#nfr-107
[NFR-153]: ../requirements/requirements.md#nfr-153
[NFR-178]: ../requirements/requirements.md#nfr-178
[NFR-185]: ../requirements/requirements.md#nfr-185
[NFR-192]: ../requirements/requirements.md#nfr-192
[NFR-202]: ../requirements/requirements.md#nfr-202
[NFR-223]: ../requirements/requirements.md#nfr-223
[NFR-229]: ../requirements/requirements.md#nfr-229
[PL-161]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#161-tokens
[PL-162]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#162-passwords-and-links
[PL-23]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#23-deployment-and-operations
[Q-81]: ../requirements/requirements.md#open-questions
[TS §6]: ../architecture/tech_stack.md#6-core-service-java
[TS §10]: ../architecture/tech_stack.md#10-web-app
[TS §15]: ../architecture/tech_stack.md#15-infrastructure-and-operations
[TS §17.3]: ../architecture/tech_stack.md#173-security-testing
[TS §23]: ../architecture/tech_stack.md#23-alternatives-rejected-across-the-stack
