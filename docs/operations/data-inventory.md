# DispatchFlow — Data inventory

What company data DispatchFlow sends to outside services, and what it keeps of the answers. The
requirements that call for it are [NFR-72][NFR-72] (the inventory of the personal data DispatchFlow
keeps) and [NFR-217][NFR-217] (each outside service that receives company data, what it receives,
and whether it is kept in Israel or an accepted exception). The retention periods are in
[ADR-075][ADR-075], and the plan for each outside service is in [TS §13][TS-13].

This file is not complete. It names one outside service so far. [#251][ISSUE-251] completes it with
the lists of personal data that [NFR-72] asks for. Until then, [TS §13][TS-13] covers the services
this file doesn't yet name.

## Outside services that receive company data

*Kept* is what DispatchFlow keeps of what it sent and of the service's answers, and for how long.

| Service | Used for | Receives | Held in | Kept |
|---|---|---|---|---|
| GovMap | Geocoding customer addresses ([FR-12]) | The address text after normalization ([FR-398]): free text of at most 200 characters, so it carries whatever was typed into it, such as an apartment or an entrance. The request also carries the team's API token. The customer's name, phone and other fields never go | Israel, as [TS §13][TS-13] records; not yet verified ([#153][ISSUE-153]) ([NFR-209]) | The point and match quality: on the customer (`orders.customer`) until the customer is anonymized ([ADR-075]); in the geocode cache, `orders.geocode_cache`, keyed by the address, which [#165][ISSUE-165] creates; and in Planning's solve inputs until 90 days after the plan's date ([ADR-121] decision 8). The address waits in `orders.geocode_request` ([#208][ISSUE-208]) until its lookup is done. The cache's deletion of an entry unused for a year ([ADR-075], *Geocode cache*) is built later, so in the Alpha cache entries are kept |

Notes on the GovMap row:

- GovMap is called only in `real` mode (`DISPATCHFLOW_GOVMAP_MODE=real`): by the Alpha demo and
  its rehearsals, the accuracy measurement and the adapter's check by hand, on synthetic and public
  addresses only ([ADR-139][ADR-139]). In the default `fake` mode, an address never leaves the
  machine.
- No company data goes to GovMap before [#165][ISSUE-165] merges, and the cache's yearly deletion is built
  later ([ADR-139]).
- GovMap's terms, price, request limits and hosting location, and what it keeps of our requests,
  are not yet checked ([#153][ISSUE-153]).

[ADR-075]: ../ADRs/ADR-075-retention-deletes-records-and-anonymizes-people-in-place.md
[ADR-121]: ../ADRs/ADR-121-the-application-skeletons-review-settles-its-seams-conflicts-retention-and-scoring-inputs.md
[ADR-139]: ../ADRs/ADR-139-govmap-geocoding-comes-into-the-alpha.md
[TS-13]: ../architecture/tech_stack.md#13-outside-services-and-where-data-goes
[ISSUE-153]: https://github.com/NaimElijah/DispatchFlow/issues/153
[ISSUE-165]: https://github.com/NaimElijah/DispatchFlow/issues/165
[ISSUE-208]: https://github.com/NaimElijah/DispatchFlow/issues/208
[ISSUE-251]: https://github.com/NaimElijah/DispatchFlow/issues/251
[FR-12]: ../requirements/requirements.md#geocoding
[FR-398]: ../requirements/requirements.md#customers-and-orders
[NFR-72]: ../requirements/requirements.md#nfr-72
[NFR-209]: ../requirements/requirements.md#nfr-209
[NFR-217]: ../requirements/requirements.md#nfr-217
