# ADR-084: Both apps draw maps with MapLibre, from self-hosted Protomaps tiles

- **Status:** Accepted; amended by ADR-158
- **Date:** 2026-11-13

## Context

[Q-03] asked which map library each app uses, which tile source, and whether the web app's frame rate
([NFR-109]) also binds the phone app. [ADR-035] widened it to both apps, because both serve every
role. The phone app's library must also run in its web build, which the end-to-end tests use
([NFR-220]).

The forces:

- A dispatcher sees every route of a plan on a map, drawn along the road network ([FR-24]), and fixes
  a bad match by dragging a pin ([FR-14]). The target is 200 stops and 15 routes at 30 frames a
  second ([NFR-109]).
- The map data and the tiles must stay in Israel ([NFR-209]). Maps built on OpenStreetMap data show
  its attribution ([NFR-18]).
- Both apps should look alike: one colour and one label per route everywhere ([FR-148], [FR-240]).
- Google's and Mapbox's terms tie their results to their own maps, and Google's and HERE's limit
  how long results may be stored ([TS §9]).

Decided on 2026-11-13: the maps for both apps, with self-hosted tiles.

## Decision

- **Web app:** MapLibre GL JS through `react-map-gl` ([TS §9], [TS §10]).
- **Phone app:** `@maplibre/maplibre-react-native` on phones, and a `Map.web.tsx` that uses
  `maplibre-gl` for the phone app's web build ([TS §9], [NFR-220]).
- **One style and one set of route colours,** held in `@dispatchflow/map-style` and used by both
  apps ([TS §12]).
- **Tiles:** a Protomaps basemap, an extract of Israel as a PMTiles file, served by Caddy with
  range requests. Styles come from `@protomaps/basemaps` with Hebrew (`name:he`) labels. Fonts are
  self-hosted, and the OpenStreetMap attribution is shown ([NFR-18]). The extract is rebuilt monthly
  with OSRM's road data ([NFR-129]).
- **Hebrew on the web:** MapLibre's right-to-left text plugin, served from the VM. On phones, native
  label rendering is checked on real phones ([NFR-140]).
- **[NFR-109] binds both apps,** measured as [NFR-181] says: at 200 stops and 15 routes, at the
  95th percentile, on a mid-range laptop and a mid-range Android phone.
- **Development builds:** Expo Go cannot load MapLibre, so the phone app uses `expo-dev-client`
  ([TS §11]).

## Consequences

- **Easy:** one style, one set of layers and one label scheme in both apps. Tiles are one static
  file: no tile server, no vendor, and no request leaves Israel, so the data inventory has no map
  vendor to list ([TS §9], [NFR-217]).
- **Hard:** two MapLibre packages behind one component in the phone app, one for phones and one for
  its web build, which must show the same map.
- **Hard:** the phone app needs development builds instead of Expo Go.
- **Hard:** nobody has yet measured the 30 frames a second on the phone app's map. [NFR-181] defines
  the measurement.
- **Cost:** a monthly rebuild of the tiles with the road data, and the PMTiles file on the VM's disk.
  Its size is not stated in the sources.
- **Risk:** Hebrew labels on native maps are unchecked until a real phone runs the device checklist
  ([TS §9], [NFR-140]).
- **Risk:** `@maplibre/maplibre-react-native` is a native dependency of Expo's SDK. Every native
  dependency is checked against the SDK first ([TS §11]), and this one has not been checked here.

## Alternatives considered

- **Leaflet.** Rejected: its DOM markers struggle at 200 stops and 15 routes, and it cannot render
  the vector style ([TS §9]).
- **OpenLayers.** Rejected: capable, but it has no React Native counterpart, so two map stacks.
- **Mapbox GL JS.** Rejected: a proprietary licence and access tokens.
- **Google Maps.** Rejected: its terms, and data leaving Israel ([TS §9], [TS §23]).
- **`react-native-maps` on phones.** Rejected: platform map SDKs with a different look, and no
  vector style.
- **OpenMapTiles with `tileserver-gl`.** Rejected: another running service.
- **Hosted tiles** (MapTiler, Stadia). Rejected: a vendor, usage limits, and requests leaving
  Israel.

## Revisit when

- [NFR-109] is missed at 200 stops and 15 routes on the mid-range laptop or the mid-range Android
  phone ([NFR-181]).
- Hebrew labels render wrongly on real phones and the device checklist cannot fix them.
- `@maplibre/maplibre-react-native` cannot follow an Expo SDK upgrade.
- Serving the PMTiles file through Caddy shows up as slow map loads in the pilot.

[ADR-035]: ADR-035-both-apps-serve-every-role.md
[FR-14]: ../requirements/requirements.md#geocoding
[FR-24]: ../requirements/requirements.md#dispatcher-views
[FR-148]: ../requirements/requirements.md#dispatcher-views
[FR-240]: ../requirements/requirements.md#dispatcher-views
[NFR-18]: ../requirements/requirements.md#nfr-18
[NFR-109]: ../requirements/requirements.md#nfr-109
[NFR-129]: ../requirements/requirements.md#nfr-129
[NFR-140]: ../requirements/requirements.md#nfr-140
[NFR-181]: ../requirements/requirements.md#nfr-181
[NFR-209]: ../requirements/requirements.md#nfr-209
[NFR-217]: ../requirements/requirements.md#nfr-217
[NFR-220]: ../requirements/requirements.md#nfr-220
[Q-03]: ../requirements/requirements.md#open-questions
[TS §9]: ../architecture/tech_stack.md#9-routing-and-geodata
[TS §10]: ../architecture/tech_stack.md#10-web-app
[TS §11]: ../architecture/tech_stack.md#11-phone-app
[TS §12]: ../architecture/tech_stack.md#12-shared-typescript-packages
[TS §23]: ../architecture/tech_stack.md#23-alternatives-rejected-across-the-stack
