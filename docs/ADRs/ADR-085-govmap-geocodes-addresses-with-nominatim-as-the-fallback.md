# ADR-085: GovMap geocodes addresses, and self-hosted Nominatim replaces it only if GovMap's terms fail

- **Status:** Accepted; amended by ADR-121
- **Date:** 2026-11-13

## Context

The system converts every customer and depot address, Hebrew ones included, to coordinates ([FR-12]).
It caches the results within the provider's terms ([FR-13], [NFR-17]). Customers and depots keep a
copy of the coordinates, and the map the dispatcher sees is our own MapLibre map
([ADR-084]).

[Q-02] asked which provider to use, and carried the marker "ADR pending: Google geocoding
provider". It planned a Hebrew-accuracy test against at least one alternative, and a review of
Google's terms. [TS §9] records what the terms of the big providers say:

- **Google:** its terms forbid showing its results on a non-Google map, and limit cached coordinates
  to 30 days. [NFR-148] was worded around that limit.
- **Mapbox:** its standard results must appear on a Mapbox map and cannot be stored.
- **HERE:** storage is limited to 30 days without a paid licence.

The system must also keep the server and its data in Israel ([NFR-209], [ADR-078]). [TS §13] lists
GovMap as holding the data it receives, customer and depot addresses, in Israel.

On 2026-11-13 the owner, Naim Elijah, decided for the team: GovMap, through the `Geocoder` port.
The owner gave no reason for preferring GovMap to Nominatim. Three facts about GovMap have not been
checked: its price, its request limits and its coordinate system. It may answer in the Israeli grid
(ITM), which the adapter would convert to WGS84. Unverified.

## Decision

- **The `Geocoder` port gets a GovMap adapter and a fake** ([TS §6.4]). Its shape is
  `Geocoder.lookup(address) -> Match | Ambiguous | NotFound | Unavailable` ([PL §15.3]).
- **The terms are checked before the geocoding code is built.** A team member reads GovMap's terms
  and gets its API token. The same check records GovMap's price, request limits and coordinate
  system.
- **If the terms forbid keeping GovMap's coordinates on customers and depots, or showing them on our
  map, self-hosted Nominatim replaces GovMap behind the same port.** It takes about 2 GB of the
  VM's 16 GB (estimate, not measured; [ADR-078]). Without that failure, no Nominatim container runs.
- **[NFR-148] is reworded:** "The geocoding provider's terms allow keeping its coordinates on
  customers and depots and showing them on our own map; this is checked before the pilot handles
  real data."
- **The accuracy test runs when geocoding is built (M7),** over one address list through GovMap
  alone: Hebrew, Arabic and transliterated addresses, each with a known location. Its results:
  - set what [FR-63] counts as a partial or ambiguous answer;
  - show whether [FR-140]'s normalization is needed;
  - leave [Q-83]'s answer as it is: a miss is flagged for a hand-placed location, and normalization stays
    Hebrew only.
- **Suggestions while typing ([FR-139])** use GovMap's if its terms allow them. If they do not, and [FR-139] still needs suggestions, Photon
  runs self-hosted on the VM, like Nominatim.
- **Address flags** ([FR-63]) are: no match, a partial match (the provider's quality is below
  street level), an ambiguous match, an address outside the service area, and a snap distance over
  300 m. OSRM reports the snap distance as `waypoints[].distance` ([PL §14]).
  - The 300 m limit was decided on 2026-11-14 as a starting value. The accuracy test tunes it.
  - A flag shows before a solve and never blocks it ([FR-216]).
- **The cache** ([FR-13]) is `geocode_cache(address_norm, provider, lat, lon, quality,
  fetched_at)`. Customers copy the coordinates. A location placed by hand wins over any lookup ([BR-13],
  [FR-215]). An entry unused for 1 year is deleted, as [Q-14]'s retention defaults set.
- **When GovMap cannot be reached,** the customer is saved without coordinates and the lookup is
  retried every 15 minutes for 24 hours, then stays flagged ([FR-64]). The dispatcher can place the
  location by hand if the provider cannot be reached or finds no match: [FR-218] is a Must and reads
  that way. A counter row holds lookups to 1,000 a day ([NFR-135]).
- **New customers are geocoded after an import commits,** from an outbox row, so a slow geocoder
  never holds up the import ([ADR-086]).

## Consequences

- **Easy:** the data stays in Israel ([TS §13]), so geocoding needs no [NFR-209] exception.
- **Easy:** a provider's terms no longer cap how long coordinates may be kept, as Google's 30 days
  did. The cache and the copies on customers follow GovMap's terms once they are read.
- **Easy:** a failed terms check changes one adapter and one container, not the domain, because the
  port stays the same.
- **Hard:** the choice rests on a check not yet made. Until the terms are read, nobody knows
  whether GovMap lets us keep and show its coordinates.
- **Hard:** the accuracy test runs on GovMap alone, so the team will not have a measured comparison
  with Nominatim or Photon unless the fallback fires.
- **Cost:** GovMap's price is unknown. If Nominatim runs, it is a GPL service run unmodified
  ([TS §18]) and about 2 GB of the VM (an estimate).
- **Risk:** poor Hebrew accuracy would show as many flags and hand-placed locations ([FR-14], [FR-218]).
  The M7 test is the first measurement.
- **Risk:** a terms failure found after the adapter is built wastes that adapter. The terms check
  comes first to avoid it.

## Alternatives considered

- **Google's Geocoding API.** Rejected: its terms forbid use with a non-Google map and cap cached
  coordinates at 30 days. Results would also leave Israel ([TS §23]).
- **Mapbox or HERE.** Rejected: Mapbox's standard results must appear on a Mapbox map and cannot be
  stored. HERE limits storage to 30 days without a paid licence.
- **Self-hosted Nominatim first.** Not chosen first. It needs a container and about 2 GB of the VM.
  It stays as the fallback behind the same port.
- **Choosing the provider by a test against an alternative,** as [Q-02] first planned. Dropped:
  the owner chose GovMap first, and the test now measures it alone.

## Revisit when

- GovMap's terms forbid keeping its coordinates or showing them on our map. Run Nominatim behind
  the port, as decided above.
- GovMap's request limit is below 1,000 lookups a day ([NFR-135]), or its price does not fit the
  hosting cap ([ADR-078]).
- The M7 accuracy test shows GovMap missing or flagging addresses that Nominatim would place. Run
  the same list through Nominatim and compare.
- GovMap changes its terms or withdraws the service.

[ADR-078]: ADR-078-hosting-on-one-aws-vm-in-tel-aviv-built-with-terraform.md
[ADR-084]: ADR-084-maplibre-in-both-apps-with-self-hosted-protomaps-tiles.md
[ADR-086]: ADR-086-csv-imports-upload-then-confirm-within-an-hour.md
[PL §14]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#14-travel-times-and-geodata
[PL §15.3]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#153-geocoding-m7
[TS §13]: ../architecture/tech_stack.md#13-outside-services-and-where-data-goes
[TS §18]: ../architecture/tech_stack.md#18-cicd-and-supply-chain
[TS §23]: ../architecture/tech_stack.md#23-alternatives-rejected-across-the-stack
[TS §6.4]: ../architecture/tech_stack.md#64-ports-and-adapters
[TS §9]: ../architecture/tech_stack.md#9-routing-and-geodata
[BR-13]: ../requirements/requirements.md#orders-records-and-history
[FR-12]: ../requirements/requirements.md#geocoding
[FR-13]: ../requirements/requirements.md#geocoding
[FR-14]: ../requirements/requirements.md#geocoding
[FR-63]: ../requirements/requirements.md#geocoding
[FR-64]: ../requirements/requirements.md#geocoding
[FR-139]: ../requirements/requirements.md#geocoding
[FR-140]: ../requirements/requirements.md#geocoding
[FR-215]: ../requirements/requirements.md#geocoding
[FR-216]: ../requirements/requirements.md#geocoding
[FR-218]: ../requirements/requirements.md#geocoding
[NFR-17]: ../requirements/requirements.md#nfr-17
[NFR-135]: ../requirements/requirements.md#nfr-135
[NFR-148]: ../requirements/requirements.md#nfr-148
[NFR-209]: ../requirements/requirements.md#nfr-209
[Q-02]: ../requirements/requirements.md#open-questions
[Q-14]: ../requirements/requirements.md#open-questions
[Q-83]: ../requirements/requirements.md#open-questions
