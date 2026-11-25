# ADR-139: GovMap geocoding comes into the Alpha

- **Status:** Accepted
- **Date:** 2026-11-25

## Context

The Application Requirements Document (ARD) commits geocoding to the Alpha. The *Addresses* row of
[ARD §5.2][ARD], Table 5.2, promises "Addresses converted to coordinates; doubtful matches flagged;
wrong pins dragged into place", and cites [FR-12], [FR-63] and [FR-14]. Three more passages of the
ARD depend on it:

- **The demo scenario:** "One address is flagged as doubtful, and we drag its pin into place."
- **Success criterion 4:** "The geocoding test reports GovMap's match rate on at least 100 real
  Be'er Sheva addresses with known locations."
- **Risk R3 (Table 5.1),** Hebrew addresses geocoded badly, or terms that forbid storing or showing
  the results. Its mitigation reads the terms before geocoding is built, keeps Nominatim as the
  fallback behind the same interface, and says that in the Alpha a test of at least 100 real
  Be'er Sheva addresses, "Hebrew, Arabic and transliterated", sets what counts as a doubtful match.
  Its early warning is terms that forbid storing or showing the coordinates, or a low match rate.

Geocoding is not on §5.2's "Not in the Alpha" list.

The tech stack places it later. [TS §20][TS-20], *Adoption order*, lists "geocoding (GovMap)" in
the Semester B row, and [TS §9][TS-9] marks its *Geocoding* and *Service area* rows "· B". The same
file already puts the service area's polygon and JTS in the Alpha: the *Caching* and *Pure domain
libraries* rows are "Decided · Alpha".

The project rule is that [TS §20][TS-20] decides when a tool is adopted, and that a change to a
decision comes with a new ADR, accepted before the code it governs merges (`.claude/CLAUDE.md`,
*Documentation*). Until an accepted ADR moves geocoding, the GovMap adapter ([#165][ISSUE-165]),
the geocoding of new customers ([#208][ISSUE-208]) and the placing of a location by hand
([#209][ISSUE-209]) can't merge.

[ADR-085] decides that GovMap's terms are checked before its code is built, and that its accuracy
test runs "when geocoding is built (M7)". The check is [#153][ISSUE-153]. It has not reported by
25 November 2026, so GovMap's price, request limits, coordinate system and terms are not yet
checked.

## Decision

Decided on 23 November 2026, without waiting for the mentor's scope decision of 1 December: GovMap
geocoding comes into the Alpha, and this ADR is accepted at once. Decision 4's startup guards and
decision 6's service-area input were decided on 25 November 2026.

1. **GovMap geocoding, behind the existing `Geocoder` port,** is adopted in the Alpha with the
   scope of ARD §5.2. The port is
   `src/backend/core/src/main/java/com/dispatchflow/orders/application/port/out/Geocoder.java`.
   - A customer's address becomes coordinates ([FR-12]).
   - A doubtful match is flagged ([FR-63]).
   - A wrong location is dragged into place ([FR-14], [BR-13]), with a way to place it that needs
     no dragging ([NFR-46], [ADR-070]). [FR-218] lets the dispatcher place it by hand when GovMap
     can't be reached or finds no match.
   - A location placed by hand is checked to lie inside the service area, and an address outside it
     is flagged ([FR-320], [FR-63]; [#208][ISSUE-208], [#209][ISSUE-209]). In the Alpha the road
     data is the Be'er Sheva clip that [#168][ISSUE-168] prepares, so the service area is that
     clip ([#209][ISSUE-209]): [FR-320]'s "the whole extract until a company is known".
   - The accuracy test runs over Hebrew, Arabic and transliterated addresses ([#132][ISSUE-132],
     [#175][ISSUE-175]). ARD R3 puts all three in the Alpha, [Q-83] keeps them in the geocoding
     test, and [ADR-085] ties that test to the time geocoding is built. Arabic and transliterated addresses stay in the Alpha's test. It
     runs by hand in `real` mode ([#175][ISSUE-175]), never in `just check` or CI.
2. **The cache, the retries and the daily cap come with it:** the cache ([FR-13],
   [#165][ISSUE-165]), the retry every 15 minutes for 24 hours when GovMap can't be reached
   ([FR-64]), and the 1,000 lookups a day ([NFR-135], [#208][ISSUE-208]).
3. **What stays later:**
   - Suggestions while a dispatcher types ([FR-139]), and Photon, which serves them if GovMap's
     terms don't.
   - The cache's retention, the yearly deletion of an entry unused for a year ([ADR-075]).
   - Depot geocoding, which gets a port of Fleet's own at M7 ([FR-130], [ADR-120] P4, [ADR-121]
     decision 13 and the `fleet/adapter/out/govmap` row of [ADR-131]). Those three statements still hold,
     because an Alpha depot's location is set by coordinates ([#195][ISSUE-195]).
4. **Run modes.** `DISPATCHFLOW_GOVMAP_MODE` is `fake` by default. `just dev` without the token, and
   CI's run of the demo script ([#259][ISSUE-259]), use [ADR-042]'s WireMock fake GovMap, which
   [#165][ISSUE-165] builds. `just check` never runs in `real` mode. The Alpha demo runs `just dev` with
   `DISPATCHFLOW_GOVMAP_MODE=real` and the real `DISPATCHFLOW_GOVMAP_TOKEN` in `infra/deploy/.env`.
   - **Who uses `real` mode:** the demo, its rehearsals on the demo laptop ([#259][ISSUE-259]'s ten
     runs), the accuracy measurement ([#175][ISSUE-175]) and [#165][ISSUE-165]'s check by hand.
     They are the one exception to [ADR-042]'s "no outside network" (decision 5), and this ADR
     records it. [NFR-175]'s end-to-end tests stay offline.
   - **Only synthetic and public addresses** go to GovMap: the demo's synthetic customers and
     [#155][ISSUE-155]'s public places, until the legal advice of [Q-42] covers sending addresses
     to GovMap and [NFR-148] is checked.
   - **In `real` mode the core fails at startup** without the token, or with a base URL that isn't
     `https`. The token travels in a header where GovMap allows one.
   - **The mode is guarded both ways at startup.**
     - The `test` profile refuses `real` mode, and `@IntegrationTest` pins `fake` in its own
       `@SpringBootTest` properties. Those outrank an exported environment variable, which in turn
       outranks `application-test.properties`, so a shell that exported the demo's variables can't
       make `just check` spend [NFR-135]'s daily lookups or send test addresses out.
     - The core refuses `fake` mode unless `dev`, `test`, `e2e` or `schema-draft` is the only active
       profile, as [ADR-121] decision 5 refuses its stand-in session. `schema-draft` (`just
       core-schema-draft`) opens no connection and runs no job. So a deployment that forgot the mode
       fails at startup, instead of retrying for 24 hours against a fake that isn't there ([FR-64]).
     - Both need a profile to be active, and today none is: `@IntegrationTest` activates `test`,
       and `just core-run` activates `dev`, as [#170][ISSUE-170] plans. Whichever of
       [#165][ISSUE-165] and [#170][ISSUE-170] merges first adds them, and each refusal has a test.
   - **The token has one home,** the git-ignored `infra/deploy/.env`, with its key in
     `infra/deploy/.env.example` ([#146][ISSUE-146]). It is never written into a run
     configuration, an API client's collection or a test fixture. A leaked token is revoked and
     reissued at GovMap by whoever registered it ([#153][ISSUE-153]), not only deleted from git.
5. **This ADR amends four accepted ones:**
   - **[ADR-085]:** its accuracy test "runs when geocoding is built (M7)" now runs in the Alpha,
     and each of its other mentions of the M7 test means this Alpha test. The rest stands,
     including the terms check before the geocoding code is built and Nominatim as the fallback
     behind the same port.
   - **[ADR-086]:** its sentence "Excel, Windows-1255 and geocoding come in M7" now reads: geocoding
     comes into the Alpha, and the import's confirmation writes a geocoding request for each new
     customer; Excel and Windows-1255 stay in M7.
   - **[ADR-042]:** "the whole stack runs on a laptop with no outside network" gains one opt-in
     exception, the demo's `real` mode. The default stays offline, so the end-to-end tests of
     [NFR-175] still need no outside network.
   - **[ADR-121]:** its *Hard* line, "the service-area flag at M7", now reads "in the Alpha"
     (decision 6).
6. **An order outside the service area drops in the Alpha,** as *unreachable location* ([FR-320],
   [FR-66]), unless its location was placed by hand.
   - The solve contract's order gains a required `outsideServiceArea` boolean. Required, because a
     sender that forgot it would otherwise read as "inside", and the field can't be made required
     later in v1 without a breaking change (`contracts/CLAUDE.md`). The contract isn't merged yet,
     so this costs nothing now.
   - [#156][ISSUE-156] adds it to the contract and [#177][ISSUE-177], its provider, in the same pull
     request (`contracts/CLAUDE.md`): `OrderSpec` gains the flag, and `ProblemFactory` drops such an
     order before the search, with the *unreachable location* reason it already gives an order the
     road network can't reach.
   - [#198][ISSUE-198] sends the flag that Planning's `ProblemFacts.Order` already carries from
     [#211][ISSUE-211]. The core never sets it for a location placed by hand or a missing one, and
     `Customer.placeLocation` refuses a hand-placed point outside the area.
   - The gap notes in `ProblemFacts.java` and the optimizer's `requests.py` name this decision until
     those issues delete them, and the sentence in `SolveRequestAssembler`'s Javadoc that states the
     drop becomes true.

## Consequences

- **Easy:**
  - ARD §5.2's *Addresses* row, its demo scenario and its success criterion 4 can be met.
  - [#165][ISSUE-165], [#208][ISSUE-208] and [#209][ISSUE-209] can merge once this ADR is on `dev`.
  - The port, the domain and the tests stay as they are. A terms failure changes one adapter
    ([ADR-085]).
  - Customer addresses go to GovMap, which [TS §13][TS-13] records as holding them in Israel, so
    geocoding needs no [NFR-262] exception under [NFR-209]. [#153][ISSUE-153] checks where GovMap
    holds them (see *Revisit when*).
  - Tests and CI stay offline: the `test` profile runs only in `fake` mode (decision 4), so
    [NFR-175] holds.
- **Hard:**
  - The demo needs a network connection to GovMap. Only a machine with the token and
    `DISPATCHFLOW_GOVMAP_MODE=real` reaches it.
  - The GovMap token is a secret. It lives only in a git-ignored `.env`, and only `real` mode needs
    it.
  - The fake can drift from the real GovMap, and a demo run on the fake shows none of GovMap's real
    matches. [#165][ISSUE-165] builds its stubs from the answers [#153][ISSUE-153] records and
    reads the committed stubs through its own client in a test, and [#175][ISSUE-175]'s run in
    `real` mode is the check of that client against the real GovMap.
  - [TS §14][TS-14]'s *Local* row stops saying that everything is faked.
  - The Alpha takes on the adapter, the cache, the retries, the flags, placing a location by hand,
    the service-area drop and the accuracy test. The roadmap's M2 carries them.
  - [#156][ISSUE-156], [#177][ISSUE-177] and [#198][ISSUE-198] each grow by the service-area field
    (decision 6). Until they merge, an order outside the service area is planned at the road nearest
    its location: OSRM's table, called with no `radiuses` ([#178][ISSUE-178]), snaps each point to
    its nearest road with no distance limit by default. The flag still reaches the dispatcher's list
    of flagged addresses first ([FR-63]), and it never blocks a solve ([FR-216]).
  - The inventory of outside services grows by one row, and that row must stay true ([NFR-217]).
- **Cost:** GovMap's price and request limits are not yet checked ([#153][ISSUE-153],
  [TS §9][TS-9]).
- **Risk:**
  - Low accuracy on Hebrew addresses ([ARD §5.2][ARD], R3) would show as many flags and hand-placed
    locations in the demo. The accuracy test is the first measurement.
  - The daily cap and the rehearsals. A rehearsal on a clean laptop looks every new customer up
    again, up to 200 for the demo CSV, so [NFR-135]'s 1,000 lookups a day cover about five runs, and
    [#259][ISSUE-259]'s ten need two days or more.
  - A terms failure found late. The terms are still checked before the geocoding code is built
    ([ADR-085]): nothing calls GovMap before [#153][ISSUE-153] reports, apart from that issue's one
    call by hand with a public place's address.

## Alternatives considered

- **Keep geocoding for later, and seed customers with coordinates,** as the tech stack planned.
  Rejected: it drops ARD §5.2's *Addresses* row, the demo's flagged address and success criterion 4.
- **Self-hosted Nominatim now.** Rejected: it takes about 2 GB of the VM ([ADR-085], an estimate),
  and it is that ADR's fallback, not its choice.
- **Run the demo on the WireMock fake alone.** Rejected: it shows nothing of GovMap's real matches,
  the risk R3 names. It becomes the demo's fallback if GovMap's terms fail ([#153][ISSUE-153]).
- **Leave the service-area drop for M7,** as [ADR-121] planned, with the flag shown to the
  dispatcher only. Rejected: [FR-320] is a Must, and an address that GovMap places in the wrong
  town would become a stop with a made-up travel time instead of a drop that says why. The input is
  one field and one condition, in two issues that are still open.
- **A required mode with no default, and no profile list.** A run that sets no mode would fail at
  startup without tying GovMap's settings to profile names. Rejected: a deployment that copied the
  dev `.env` sets `fake` on purpose and would pass, and [ADR-121] decision 5 already guards its
  stand-in by profile, so both guards read the same way.
- **A plain `fake` default with no startup guards.** Rejected: it holds only while nobody runs the
  tests in a shell that exported `real` mode, and a deployment that forgot the mode would fail one
  address at a time, for 24 hours, instead of at startup.

## Revisit when

- [#153][ISSUE-153] finds that GovMap's terms forbid storing or showing its results. [ADR-085]'s
  fallback applies, and a new ADR records the change.
- The mentor removes geocoding from the Alpha on 1 December. A new ADR supersedes this one.
- [#175][ISSUE-175]'s run finds GovMap missing or flagging addresses that Nominatim would place,
  R3's early warning and [ADR-085]'s own trigger: the same list runs through Nominatim, and the
  demo's mode is chosen from both results.
- [#153][ISSUE-153] finds that GovMap holds or processes the addresses outside Israel, or can't say
  where. Geocoding then needs an [NFR-262] exception, in a new ADR amending [ADR-077], or
  [ADR-085]'s fallback.
- [#153][ISSUE-153] finds a limit on how long GovMap's answers may be kept. The cache's deletion,
  and a refresh of the copies on customers, then come into the Alpha in a new ADR.
- [#153][ISSUE-153] finds that GovMap geocodes only through its browser API. The token, the data
  flow and the web app's content security policy then change, in a new ADR.
- GovMap can't be reached on a demo day: the demo runs in `fake` mode, on [#179][ISSUE-179]'s
  mappings.
- A run needs `fake` mode under a profile other than `dev`, `test`, `e2e` or `schema-draft`, such
  as a study run or the usability VM ([TS §14][TS-14]). That profile joins decision 4's list, in a
  new ADR.

[ADR-042]: ADR-042-compose-dev-for-local-runs-testcontainers-for-tests.md
[ADR-070]: ADR-070-security-and-accessibility-standards.md
[ADR-075]: ADR-075-retention-deletes-records-and-anonymizes-people-in-place.md
[ADR-077]: ADR-077-push-builds-and-app-stores-are-accepted-exceptions-to-hosting-in-israel.md
[ADR-085]: ADR-085-govmap-geocodes-addresses-with-nominatim-as-the-fallback.md
[ADR-086]: ADR-086-csv-imports-upload-then-confirm-within-an-hour.md
[ADR-120]: ADR-120-the-application-layer-takes-an-actor-and-a-call-context-and-reads-through-query-ports.md
[ADR-121]: ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md
[ADR-131]: ADR-131-the-declared-structures-review-tightens-its-rules-and-fills-its-gaps.md
[ARD]: ../requirements/ARD/DispatchFlow_ARD.pdf
[TS-9]: ../architecture/tech_stack.md#9-routing-and-geodata
[TS-13]: ../architecture/tech_stack.md#13-outside-services-and-where-data-goes
[TS-14]: ../architecture/tech_stack.md#14-environments
[TS-20]: ../architecture/tech_stack.md#20-adoption-order
[ISSUE-132]: https://github.com/NaimElijah/DispatchFlow/issues/132
[ISSUE-146]: https://github.com/NaimElijah/DispatchFlow/issues/146
[ISSUE-153]: https://github.com/NaimElijah/DispatchFlow/issues/153
[ISSUE-155]: https://github.com/NaimElijah/DispatchFlow/issues/155
[ISSUE-156]: https://github.com/NaimElijah/DispatchFlow/issues/156
[ISSUE-165]: https://github.com/NaimElijah/DispatchFlow/issues/165
[ISSUE-168]: https://github.com/NaimElijah/DispatchFlow/issues/168
[ISSUE-170]: https://github.com/NaimElijah/DispatchFlow/issues/170
[ISSUE-175]: https://github.com/NaimElijah/DispatchFlow/issues/175
[ISSUE-177]: https://github.com/NaimElijah/DispatchFlow/issues/177
[ISSUE-178]: https://github.com/NaimElijah/DispatchFlow/issues/178
[ISSUE-179]: https://github.com/NaimElijah/DispatchFlow/issues/179
[ISSUE-195]: https://github.com/NaimElijah/DispatchFlow/issues/195
[ISSUE-198]: https://github.com/NaimElijah/DispatchFlow/issues/198
[ISSUE-208]: https://github.com/NaimElijah/DispatchFlow/issues/208
[ISSUE-209]: https://github.com/NaimElijah/DispatchFlow/issues/209
[ISSUE-211]: https://github.com/NaimElijah/DispatchFlow/issues/211
[ISSUE-259]: https://github.com/NaimElijah/DispatchFlow/issues/259
[BR-13]: ../requirements/requirements.md#orders-records-and-history
[FR-12]: ../requirements/requirements.md#geocoding
[FR-13]: ../requirements/requirements.md#geocoding
[FR-14]: ../requirements/requirements.md#geocoding
[FR-63]: ../requirements/requirements.md#geocoding
[FR-64]: ../requirements/requirements.md#geocoding
[FR-66]: ../requirements/requirements.md#daily-planning
[FR-130]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-139]: ../requirements/requirements.md#geocoding
[FR-216]: ../requirements/requirements.md#geocoding
[FR-218]: ../requirements/requirements.md#geocoding
[FR-320]: ../requirements/requirements.md#geocoding
[NFR-46]: ../requirements/requirements.md#nfr-46
[NFR-135]: ../requirements/requirements.md#nfr-135
[NFR-148]: ../requirements/requirements.md#nfr-148
[NFR-175]: ../requirements/requirements.md#nfr-175
[NFR-209]: ../requirements/requirements.md#nfr-209
[NFR-217]: ../requirements/requirements.md#nfr-217
[NFR-262]: ../requirements/requirements.md#nfr-262
[Q-42]: ../requirements/requirements.md#open-questions
[Q-83]: ../requirements/requirements.md#open-questions
