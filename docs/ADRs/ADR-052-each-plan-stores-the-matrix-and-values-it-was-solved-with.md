# ADR-052: Each plan stores the travel-time matrix and the values it was solved with

- **Status:** Accepted; amended by ADR-121
- **Date:** 2026-11-13

## Context

A plan that is completed, or whose date has passed, keeps the values it was solved and reported with,
after the fleet or a customer changes ([BR-37]). A retired record stays on past plans ([FR-193]), and
a changed record applies to later plans ([FR-306]). The rows carry the marker "ADR pending: past
plans keep their values".

The road data is refreshed monthly ([NFR-129]), and the optimizer is stateless: every call carries the
whole problem, the matrix included ([PL §6]). The matrix is built from a `table` call for the depots
and the distinct customer locations, at most 15 + 200 of them, about 47,000 entries, and it must be
built within 5 seconds ([NFR-57], [PL §14]).

On 2026-11-13 the owner, Naim Elijah, decided for the team how the matrix is sent and where it is
kept. This ADR settles the marker.

## Decision

- **The matrix travels as base64 little-endian `int32`,** one for durations and one for distances,
  over distinct locations ([PL §6.1]). Two orders at one address share a location, and OSRM is asked
  about each distinct point once. A JSON array of numbers costs about three times the bytes and the
  parse time.
- **A no-road-route pair travels as −1,** and an order without a location as `null`, so the
  optimizer keeps such orders out of the search and names the reason ([PL §6.1], [FR-66]).
- **Each plan stores its own matrix** with its location list: a compressed `bytea` column (zstd) and
  a `jsonb` list of locations on the plan ([TS §6.5], [PL §12]). The matrix is cached by the hash of
  the coordinate list, so solving a plan again does not call OSRM again ([PL §14]).
- **The plan also keeps the rest of what it was solved and reported with:**
  - each solve job keeps its input, its result and its failure code ([DL §9.7]);
  - each stop keeps its leg's travel seconds and metres, so the core never applies a bucket factor
    ([PL §6.1], [DL §10.2]);
  - each published revision keeps its stops ([ADR-059]).
- **A re-optimization adds rows only:** one `table` call from the vehicles' positions to all the
  stop locations ([PL §14]).

## Consequences

- **Easy:** a past plan can be shown, checked or compared exactly as it was, whatever changes on the
  road, in the fleet or among the customers, so past plans stay reproducible ([PL §14]).
- **Easy:** solving a plan again does not wait for OSRM.
- **Hard:** each plan's storage grows with its matrix. At about 47,000 entries of 4 bytes, one matrix
  is about 188 kB before compression. That is arithmetic from the plan's figures, not a measurement,
  and the sources give no compressed size.
- **Hard:** the sources do not list every value a past plan must keep, for example a customer's
  address text or a vehicle's capacity at the time. The plan's schema has to answer that.
- **Cost:** a base64 codec on both sides of the optimizer contract, and a decompression when a past
  plan is read.
- **Risk:** a stored matrix is as old as the road data it was built from, up to 60 days
  ([NFR-129]). A re-solve of a ready plan with the same coordinates reuses it, by the hash of the
  coordinate list ([PL §14]).

## Alternatives considered

- **Rebuild the matrix from OSRM when a past plan is read.** Rejected: the road data changes monthly,
  so the past plan would change with it, against [BR-37].
- **A JSON array of numbers.** Rejected: about three times the bytes and the parse time ([PL §6.1]).
- **A matrix over orders, not locations.** Rejected: orders at one address share a location, and
  OSRM would be asked about the same point more than once ([PL §6.1]).
- **One shared matrix for the company,** not one per plan. Not chosen: a plan must keep the values it
  was solved with, and a shared matrix would change under it.

## Revisit when

- The database grows faster than the pilot's disk allows, and stored matrices are the largest part.
  Reconsider what a plan keeps.
- A matrix is built in more than 5 seconds at the scale of [NFR-04] and [NFR-05], which measures
  [NFR-57] in M3.
- A second routing engine is added, which changes the matrix's shape.

[ADR-059]: ADR-059-plan-revisions-are-stored-as-rows.md
[BR-37]: ../requirements/requirements.md#orders-records-and-history
[DL §9.7]: ../architecture/domain_layer/domain_layer_design.md#97-how-things-end
[DL §10.2]: ../architecture/domain_layer/domain_layer_design.md#102-one-travel-time-model-in-python
[FR-66]: ../requirements/requirements.md#daily-planning
[FR-193]: ../requirements/requirements.md#fleet-depots-and-shifts
[FR-306]: ../requirements/requirements.md#fleet-depots-and-shifts
[NFR-04]: ../requirements/requirements.md#nfr-04
[NFR-05]: ../requirements/requirements.md#nfr-05
[NFR-57]: ../requirements/requirements.md#nfr-57
[NFR-129]: ../requirements/requirements.md#nfr-129
[PL §6]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#6-contracts
[PL §6.1]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#61-optimizer-api-v1
[PL §12]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#12-database-schema
[PL §14]: https://github.com/NaimElijah/DispatchFlow/blob/f54b13bc3e29e72d37ebc2a32bfd5aed2543bdee/docs/init_impl_ref/technical_implementation_plan.md#14-travel-times-and-geodata
[TS §6.5]: ../architecture/tech_stack.md#65-core--optimizer-calls
