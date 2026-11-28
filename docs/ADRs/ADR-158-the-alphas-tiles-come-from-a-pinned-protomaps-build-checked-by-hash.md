# ADR-158: The Alpha's tiles come from a pinned Protomaps build, checked by hash, until the monthly refresh keeps its own cut

- **Status:** Accepted
- **Date:** 2026-11-28

## Context

[ADR-084] decided that the map's tiles are a Protomaps extract of Israel, and that "the extract is
rebuilt monthly with OSRM's road data ([NFR-129])". #152 builds the development stack's copy with
`just data`, which cuts Be'er Sheva's tiles from a Protomaps build with the `pmtiles` CLI. No
monthly refresh exists yet, for the tiles or the road data.

What #152 found about Protomaps' builds:

- **Protomaps keeps a daily build for about a week, and the last build of each basemap version
  for good** (`https://build-metadata.protomaps.dev/builds.json`). On 2026-11-28 it kept
  4.15.0's last build (`20260722`), 4.15.1's (`20260811`), and 4.15.2's dailies from
  `20261122`. 4.15.2 is still the current version, so none of its builds is kept for good yet.
- **A pin to a daily build stops downloading about a week later**, and then every clean clone's
  `just data` fails.
- **The cut is byte-for-byte reproducible** from the same build, the same region file and the same
  `pmtiles` image, so a hash of the cut pins what every laptop serves.
- **The newest kept build is old.** On 2026-11-28 `20260811` is 109 days old, past the 60 days
  after which [NFR-129] wants the team told about the road data.

The forces:

- Every laptop and the Alpha demo should show the same map, and a clean clone must be able to
  build it ([NFR-208]).
- The project's rule is that a changed decision comes with a new ADR (`.claude/CLAUDE.md`,
  Documentation). The pin rule replaces ADR-084's monthly rebuild for as long as it holds.

## Decision

Decided on 28 November 2026.

**What changes in [ADR-084]:** its monthly rebuild, for the Alpha only. Its tile source, its
server, its styles, its fonts and its attribution stand.

- **The pin.** `just data` cuts the tiles from the last build of a basemap version that Protomaps
  keeps for good, named in the root `justfile` as `protomaps_build`. The pin moves only when
  Protomaps keeps a newer version's last build, in a committed change. A daily build is never
  pinned.
- **The hash checks.** `just data` checks the fonts' archive and the cut tiles against their
  SHA-256 pins in the `justfile`, and serves nothing that fails. A moved build, a new `pmtiles`
  image or a changed region file takes both hashes again from a clean run, in the same change, and
  a failed check prints the hash it found.
- **The monthly cycle comes back with the monthly refresh,** the job that refreshes OSRM's road
  data each month ([NFR-129]), before the pilot. That job cuts the tiles from that month's
  Protomaps build and keeps the cut, and its hash, in storage of our own in Israel ([NFR-209]),
  so the hash pins a copy we hold and not a build Protomaps may delete. From then on, the
  deployed tiles follow ADR-084's monthly rebuild, and this ADR's pin rule binds only the
  development stack, until that also reads our own copy.
- **The Alpha accepts tiles older than 60 days.** [NFR-129] binds the road data OSRM routes on.
  The map's background can lag it during the Alpha, which runs on synthetic data only.

## Consequences

- **Easy:** every laptop, and the Alpha demo, serves the same tiles, checked by hash, and a clean
  clone can always build them.
- **Easy:** a moved source fails loudly and names the hash it found, so retaking a pin is one run.
- **Hard:** the map shows OpenStreetMap as of the pinned build, 11 August 2026 today, and drifts
  further from the road data OSRM routes on until the pin moves or the monthly refresh is built.
- **Hard:** when the pin moves depends on Protomaps' release cadence, which the team doesn't
  control. The gaps between kept builds have been irregular.
- **Cost:** a pin move is a hand-made change: a clean `just data` run, two new hashes and a
  commit.
- **Risk:** Protomaps stops keeping each version's last build, or deletes `20260811`. Every clean
  `just data` then fails until the pin moves to a build that is kept.
- **Risk:** the arm64 `pmtiles` image, which Docker Desktop pulls on Apple Silicon, might cut
  different bytes from the same build. Nobody has run `just data` on a Mac yet ([NFR-208]).

## Alternatives considered

- **Keep ADR-084's monthly rebuild now, from that month's daily build.** Rejected for the Alpha:
  the daily build is gone a week later, so a pin to it breaks every clean clone, and keeping our
  own copy needs storage and a job the Alpha doesn't have.
- **Commit the cut tiles.** Rejected: a binary of several megabytes in git, retaken on every move.
- **Download the newest build on every run, with no hash.** Rejected: laptops would differ, and a
  changed or broken source would pass silently.

## Revisit when

- The monthly refresh of the road data is built. It brings back ADR-084's monthly rebuild for the
  deployed tiles, and this ADR then binds only the development stack.
- Protomaps keeps a newer version's last build. Move the pin and retake both hashes.
- The pinned build passes 120 days, twice [NFR-129]'s 60, while no newer one is kept. Decide
  whether the Alpha keeps its own cut of a daily build.
- Protomaps changes how long it keeps builds, or `just data` fails on a Mac.

[ADR-084]: ADR-084-maplibre-in-both-apps-with-self-hosted-protomaps-tiles.md
[NFR-129]: ../requirements/requirements.md#nfr-129
[NFR-208]: ../requirements/requirements.md#nfr-208
[NFR-209]: ../requirements/requirements.md#nfr-209
