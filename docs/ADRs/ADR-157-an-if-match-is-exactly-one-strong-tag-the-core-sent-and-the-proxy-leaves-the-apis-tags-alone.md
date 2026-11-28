# ADR-157: An `If-Match` is exactly one strong tag the core sent, and the proxy leaves the API's tags alone

- **Status:** Accepted
- **Date:** 2026-11-28

## Context

[ADR-057] sends a record's version as a strong entity tag, `"<version>"`, and takes it back as
`If-Match`; a version that is no longer current answers 412 `STALE_VERSION`. [ADR-140] decision 5
answers 400 to a request that breaks the contract, a missing `If-Match` included, "not RFC 6585's
428", so 400 means only that the client has a bug. `requestSolve`, the first operation to take
`If-Match` (#149), states the header's form as a pattern, so the validation generated from the
contract refuses anything else ([ADR-073]). An audit on 28 November 2026 found what that pattern
decides and nothing had recorded:

- **[RFC 9110] §13.1.1 gives `If-Match` three more forms.** `*` is true whenever the record exists.
  A weak tag never matches, because `If-Match` compares strongly, so it answers 412. A list is true
  when any tag in it matches. The pattern answers 400 to all three.
- **[BR-76] needs the version named.** A solve replaces the plan's routes. Under `*`, a dispatcher
  who read the plan before another one's edits would solve over them.
- **The production proxy rewrites tags.** The tech stack's reverse proxy is Caddy (TS §15). Its
  `encode` handler compresses JSON by default, and it appends the encoding to a strong `ETag`, so
  `"5"` leaves as `"5-gzip"`. Caddy strips that suffix from an `If-None-Match` it passes on, and not
  from an `If-Match`. Other proxies differ: nginx's gzip turns a strong tag weak. No Caddyfile is
  written yet, and `just dev` compresses nothing: the core doesn't, and Vite's proxy passes its
  answers through.

## Decision

Decided on 28 November 2026.

1. **An `If-Match` is exactly one strong tag of the form the core sends, `"<version>"`.** The
   contract states it as a pattern, so its generated validation answers 400 `INVALID_REQUEST`:
   - with a `MISSING_PARAMETER` item when the header is missing, as `Problem`'s item codes have
     it;
   - with a `WRONG_FORMAT` item for `*`, a weak tag, a list, or anything else that isn't a tag the
     core sends.

   A tag of that form that isn't the record's current version answers 412 `STALE_VERSION`
   ([ADR-057]). The shared `If-Match` parameter is for a record that always exists. An operation
   whose first save sends `If-None-Match: *` ([ADR-121] P31) declares its own pair of headers, and
   decides what neither or both answer, when it is written.
2. **The proxy in front of the core leaves the API's tags as the core wrote them.**
   - Caddy's `encode` doesn't apply to `/api/` responses.
   - Every answer also carries `no-transform` beside `no-store` in its `Cache-Control`. Caddy's
     documentation, at 2.11, says `encode` leaves a response that has it, and [RFC 9111]
     §5.2.2.6 binds every other intermediary not to transform it, so the core keeps its tags even
     past a Caddyfile that forgets the path.
   - If the API's answers need compression, the core compresses them itself, once a test shows
     that its server keeps the `ETag` as it is, and an answer that carries a token stays
     uncompressed, since a compressed secret beside reflected input is what BREACH reads.

This extends [ADR-057] with the form an `If-Match` takes, and [ADR-140] with how a header that
breaks it is answered.

## Consequences

- **Easy:**
  - The check is the contract's own pattern, generated on the server; no one writes a parser for
    the header's other forms.
  - A tag changed on its way back fails as what it is, a defect, with a `traceId` to find it by.
    Under [RFC 9110], the same tag would answer 412, and the screen would ask the user to reload,
    which would fail the same way every time.
- **Hard:**
  - A generic HTTP tool that sends `*` or a list is refused, where [RFC 9110] would accept it.
  - The API's answers travel uncompressed from Caddy to the browser and the phone. The web
    app's own files are not API answers, so Caddy still compresses them for [NFR-58]'s first
    load.
- **Cost:** the bytes compression would save on the API's answers.
- **Risk:** a proxy, CDN or library added later that rewrites tags breaks every versioned write at
  once. The review of the first Caddyfile, and of any later hop, checks that an answer's `ETag`
  arrives as the core sent it, on the Caddy version it pins, since only its documentation, not a
  run, says it honours `no-transform`.

## Alternatives considered

- **[RFC 9110]'s own semantics: compare a weak tag or a list, and let `*` pass.** It follows the
  standard, but `*` would let a solve run against whatever version is current, which [BR-76]
  forbids, and a tag a proxy changed would answer 412 to every try.
- **428 Precondition Required for a missing or wildcard `If-Match`.** [ADR-140] already answers a
  missing precondition header 400, as any missing required parameter.
- **Stripping Caddy's suffix in the core.** It would tie the core to one proxy's way of changing
  tags, and the next proxy would need another rule.
- **Weak tags from the core, which Caddy leaves alone.** `If-Match` compares strongly, so a weak
  tag never matches.
- **`no-transform` alone, with no rule for the Caddyfile.** It holds the tags from the core's
  side, but a proxy that ignores it would still rewrite them, so it backs the path rule up rather
  than replacing it.

## Revisit when

- The API's answers need compression: a `getPlan` answer for a 30-route plan passes 200 KB, or a
  screen that waits on the API misses its load budget.
- A client must send `*` or a list of tags.
- The deployment's proxy changes, or a CDN comes in front of the API.

[ADR-057]: ADR-057-concurrent-changes-to-a-plan-use-optimistic-locking.md
[ADR-073]: ADR-073-runtime-contract-validation.md
[ADR-121]: ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md
[ADR-140]: ADR-140-the-app-wide-web-package-sits-above-the-modules.md
[BR-76]: ../requirements/requirements.md#plan-lifecycle-and-publishing
[NFR-58]: ../requirements/requirements.md#nfr-58
[RFC 9111]: https://www.rfc-editor.org/rfc/rfc9111#section-5.2.2.6
[RFC 9110]: https://www.rfc-editor.org/rfc/rfc9110#section-13.1.1
