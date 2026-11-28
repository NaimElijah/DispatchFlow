# DispatchFlow's commands. CI runs these same recipes (.github/workflows/ci.yml), so a check that
# passes here passes there. `just` lists them all.

set windows-shell := ["powershell.exe", "-NoLogo", "-NoProfile", "-Command"]

mvnw := if os_family() == "windows" { ".\\mvnw.cmd" } else { "./mvnw" }
redocly := "npx --yes @redocly/cli@2.72.1"
# Pinned by digest as well as tag, so a re-pointed tag cannot change what scans the repository.
osv_scanner_image := "ghcr.io/google/osv-scanner:v2.7.0@sha256:4f3b7e2c64b9d38a5febc5a7e4a3dfef1d1362f726dea7a2ef4762b46469fcd0"
# Google's mirror of Maven Central, where the audit resolves the core's parent pom and BOMs. Central
# answers shared CI runners with HTTP 429 now and then, and a parent the scan can't load fails the
# audit (ADR-134).
maven_mirror := "https://maven-central.storage-download.googleapis.com/maven2"
# The checker of the development stack lives outside the two Python projects, so it borrows the
# optimizer's ruff, mypy and pytest from its lockfile's dev group, hashes and every dependency
# pinned, and Dependabot's bumps of that lockfile reach it too (ADR-152). --only-dev installs
# neither the optimizer nor its own dependencies.
infra_tools := "uv run --project src/backend/optimizer --locked --only-dev"
# The map's files, which `just data` builds (#152). Protomaps keeps a daily build for a week but the
# last build of each basemap version for good (https://build-metadata.protomaps.dev/builds.json),
# so the pin names the newest such build, 4.15.1's, and moves only when a newer version's last build
# is kept: a daily pin would stop downloading a week later (ADR-156). The fonts and sprite come from a pinned
# commit of protomaps/basemaps-assets. Both downloads are checked against their SHA-256, because a
# name or a commit pins the address, not the bytes; the cut is byte-for-byte reproducible from the
# same build, region and pmtiles image, so a changed source fails the check. A moved pin, a new
# pmtiles image or a changed boundary needs both hashes taken again from a clean run, in a committed
# change: `just name=value data` overrides a pin for one run only. The Caddy image is the tiles
# service's, in compose.dev.yaml; its sh, wget and tar run the steps the pmtiles image can't, as it
# has no shell.
pmtiles_image := "protomaps/go-pmtiles:v1.31.2@sha256:06574f01f55a78f78f887bc7ebf729a5c093c0d6e17d9876300cfcb0758b59d3"
protomaps_build := "20260811"
protomaps_basemap_version := "4.15.1"
israel_tiles_sha256 := "f0c0d38f8473a029be9fd8212c9fb1db5d8c6600335bf9142e1366562fc8f359"
basemaps_assets_commit := "028c18f713baecad011301ff7a69acc39bcc2ae7"
basemaps_assets_sha256 := "57e40e8c512bd8042d0a3a251f19d0d1c8523ad963c666c3c6643bada4dc92d0"
caddy_image := "caddy:2.11.9-alpine@sha256:003a97808e781aa9f7e75cc2f43d34d461099031faf5e20faefb9f61126836f0"
# `just data`'s containers fetch and unpack files from outside, so they run as nobody, holding no
# capability and unable to gain one, as the stack's own services do (ADR-019).
data_container := "--rm --user 65534:65534 --cap-drop ALL --security-opt no-new-privileges:true"

# List every recipe.
default:
    @{{ just_executable() }} --list

# Install every component's dependencies.
setup: core-setup optimizer-setup research-setup web-setup mobile-setup

# Run every check that CI runs. Needs Docker, for the core's integration tests, the audit and the
# development stack.
check: core-check optimizer-check research-check web-check mobile-check contracts-check audit infra-check

# Reformat every component's code.
format: core-format optimizer-format research-format web-format mobile-format infra-format

# --- backend/core: Java 25, Spring Boot, Maven ---

[working-directory('src/backend/core')]
core-setup:
    {{ mvnw }} -B -ntp dependency:go-offline

# Format check, unit tests, and integration tests against a PostgreSQL container.
[working-directory('src/backend/core')]
core-check:
    {{ mvnw }} -B -ntp verify

[working-directory('src/backend/core')]
core-format:
    {{ mvnw }} -B -ntp spotless:apply

# Start the core backend on 127.0.0.1:8080, under the dev profile, against a throwaway PostgreSQL
# container. Needs Docker.
[working-directory('src/backend/core')]
core-run:
    {{ mvnw }} -B -ntp spring-boot:test-run

# Write the DDL Hibernate would generate to target/schema-draft.sql, a migration's draft. No database.
[working-directory('src/backend/core')]
core-schema-draft:
    {{ mvnw }} -B -ntp spring-boot:run "-Dspring-boot.run.profiles=schema-draft"

# --- backend/optimizer: Python, FastAPI, uv ---

[working-directory('src/backend/optimizer')]
optimizer-setup:
    uv sync --locked

# Format check, lint, strict type check, import-linter and tests.
[working-directory('src/backend/optimizer')]
optimizer-check:
    uv run --locked ruff format --check .
    uv run --locked ruff check .
    uv run --locked mypy src tests
    uv run --locked lint-imports
    uv run --locked pytest

[working-directory('src/backend/optimizer')]
optimizer-format:
    uv run --locked ruff format .
    uv run --locked ruff check --fix .

# Start the optimizer on port 8000, reloading on every change.
[working-directory('src/backend/optimizer')]
optimizer-run:
    uv run --locked uvicorn dispatchflow_optimizer.adapters.inbound.http.app:create_app --factory --reload

# --- research: Python, uv; never deployed ---

[working-directory('src/research')]
research-setup:
    uv sync --locked

# Format check, lint, strict type check and tests.
[working-directory('src/research')]
research-check:
    uv run --locked ruff format --check .
    uv run --locked ruff check .
    uv run --locked mypy src tests
    uv run --locked pytest

[working-directory('src/research')]
research-format:
    uv run --locked ruff format .
    uv run --locked ruff check --fix .

# --- frontend/web: React, TypeScript, Vite; the pnpm workspace in src/frontend ---

# Install the web app's and the shared packages' dependencies from the workspace lockfile.
[working-directory('src/frontend')]
web-setup:
    pnpm ci

# Lint, format check, tests and a production build, in the web app and every shared package.
[working-directory('src/frontend')]
web-check:
    pnpm -r run lint
    pnpm -r run format:check
    pnpm -r run test
    pnpm -r run build

[working-directory('src/frontend')]
web-format:
    pnpm -r run format

# Start the web app's dev server.
[working-directory('src/frontend')]
web-run:
    pnpm --filter dispatchflow-web run dev

# --- frontend/mobile: React Native, Expo, npm ---

[working-directory('src/frontend/mobile')]
mobile-setup:
    npm ci

# Lint, format check, type check, tests and a web export.
[working-directory('src/frontend/mobile')]
mobile-check:
    npm run lint
    npm run format:check
    npm run typecheck
    npm test
    npm run build

[working-directory('src/frontend/mobile')]
mobile-format:
    npm run format

# Start Expo's dev server for phones and the browser.
[working-directory('src/frontend/mobile')]
mobile-run:
    npm start

# --- contracts and dependencies ---

# Lint both OpenAPI contracts.
[working-directory('contracts')]
contracts-check:
    {{ redocly }} lint

# --- infra: the development stack, infra/deploy/compose.dev.yaml ---

# The stack's project is the file's own, `dispatchflow`. The `data` profile is where the services
# whose files git ignores join, tiles (#152) and then osrm (#168); `just infra-check` starts none
# of them.
dev_stack := "docker compose -f infra/deploy/compose.dev.yaml --profile data"
dev_env := justfile_directory() / "infra/deploy/.env"

# Build and start the whole stack, wait until every service is healthy and the migrate step has
# finished, print where each one listens, then watch: an edit to the web app, the shared web packages
# or the optimizer reloads in its container (ADR-151). Ctrl+C stops the watch and leaves the stack
# running; a change to the core needs Ctrl+C and `just dev` again. Needs Docker.
[doc("Start the whole development stack, healthy, then reload edits until Ctrl+C")]
dev: _dev-env
    {{ dev_stack }} up --build --wait
    @echo "Web app:    http://$({{ dev_stack }} port web 5173)"
    @echo "Core:       http://$({{ dev_stack }} port core 8080)"
    @echo "Optimizer:  http://$({{ dev_stack }} port optimizer 8000)"
    @echo "Tiles:      http://$({{ dev_stack }} port tiles 8090)"
    @echo "PostgreSQL: $({{ dev_stack }} port postgres 5432)"
    @echo "Watching for edits. Ctrl+C stops the watch only; just dev-down stops the stack."
    {{ dev_stack }} watch --no-up

# Stop the stack and remove its containers. Its database stays for the next `just dev`.
[doc("Stop the development stack, keeping its database")]
dev-down: _dev-env
    {{ dev_stack }} down

# Stop the stack and delete its database, every row of local data with it, so the next `just dev`
# migrates an empty one and creates the roles again, as a password changed in infra/deploy/.env
# needs. It is the stack's only named volume, which `just infra-check` holds it to. Asks first;
# `just --yes db-reset` doesn't.
[confirm("This deletes the dev stack's database and all its data. Continue? [y/N]")]
[doc("Stop the development stack and delete its database")]
db-reset: _dev-env
    {{ dev_stack }} down --volumes

# Every Compose command reads infra/deploy/.env's passwords, so stop with the fix before Compose
# stops with its own message.
_dev-env:
    @{{ if path_exists(dev_env) == "true" { "" } else { error("infra/deploy/.env is missing: copy infra/deploy/.env.example to infra/deploy/.env and fill in its three passwords") } }}

# Format check, lint, strict type check and tests of the checker; then check that the stack's file
# publishes every port on 127.0.0.1 and gives each service only its own secrets, and start the whole
# stack in a throwaway project with no port on the host, check it, and remove it. Needs Docker.
infra-check:
    {{ infra_tools }} ruff format --check infra/deploy
    {{ infra_tools }} ruff check infra/deploy
    {{ infra_tools }} mypy --strict infra/deploy
    {{ infra_tools }} pytest -q infra/deploy
    uv run --no-project --python 3.14 infra/deploy/check_dev_stack.py --live

infra-format:
    {{ infra_tools }} ruff format infra/deploy
    {{ infra_tools }} ruff check --fix infra/deploy

# --- data: the files the development stack serves, in data/, which git ignores ---

# Build the map's files into data/tiles/ for compose.dev.yaml's tiles service: Be'er Sheva's tiles,
# cut from Protomaps' build to infra/osrm/beer-sheva.geojson, the style's fonts and its light sprite,
# and BUILD, which names their sources and hashes. Each step runs in a container, so the recipe is
# the same on Windows and macOS (NFR-208). Needs Docker Desktop and the network. A native Linux
# Docker is untested: its bind mount keeps the host's owner, so nobody may not write to data/tiles/.
# Each step writes .part files beside the ones the stack serves, and only the last swaps them in,
# after every check passed, so a failed run leaves the served files and their BUILD as they were.
#   1. Remove a failed run's .part files, fetch the fonts' archive, check its hash, and copy the
#      fonts and sprite with links resolved: the tiles service follows a link anywhere in its
#      container, so data/tiles/ holds none.
#   2. Cut the tiles.
#   3. Check the tiles' hash, or remove every .part file and stop; then swap the three in and
#      write BUILD last, so it names what is there.
data: _data-pins
    docker run {{ data_container }} -v "{{ justfile_directory() }}/data/tiles:/tiles" --entrypoint sh {{ caddy_image }} -c 'set -eo pipefail; rm -rf /tiles/fonts.part /tiles/sprites.part /tiles/israel.pmtiles.part; wget -q -O /tmp/assets.tar.gz https://codeload.github.com/protomaps/basemaps-assets/tar.gz/{{ basemaps_assets_commit }}; [ $(sha256sum /tmp/assets.tar.gz | cut -c1-64) = {{ basemaps_assets_sha256 }} ] || { echo the fonts archive does not match basemaps_assets_sha256 >&2; exit 1; }; tar -xzf /tmp/assets.tar.gz -C /tmp; mkdir /tiles/sprites.part; cp -RL /tmp/basemaps-assets-{{ basemaps_assets_commit }}/fonts /tiles/fonts.part; cp -L /tmp/basemaps-assets-{{ basemaps_assets_commit }}/sprites/v4/light* /tiles/sprites.part/'
    docker run {{ data_container }} -v "{{ justfile_directory() }}/infra/osrm:/region:ro" -v "{{ justfile_directory() }}/data/tiles:/tiles" {{ pmtiles_image }} extract https://build.protomaps.com/{{ protomaps_build }}.pmtiles /tiles/israel.pmtiles.part --region=/region/beer-sheva.geojson --quiet
    docker run {{ data_container }} -v "{{ justfile_directory() }}/data/tiles:/tiles" --entrypoint sh {{ caddy_image }} -c 'set -eo pipefail; [ $(sha256sum /tiles/israel.pmtiles.part | cut -c1-64) = {{ israel_tiles_sha256 }} ] || { rm -rf /tiles/fonts.part /tiles/sprites.part /tiles/israel.pmtiles.part; echo the cut tiles do not match israel_tiles_sha256 >&2; exit 1; }; rm -rf /tiles/fonts /tiles/sprites; mv /tiles/fonts.part /tiles/fonts; mv /tiles/sprites.part /tiles/sprites; mv /tiles/israel.pmtiles.part /tiles/israel.pmtiles; printf protomaps-build={{ protomaps_build }}\\nprotomaps-basemap={{ protomaps_basemap_version }}\\nisrael-tiles-sha256={{ israel_tiles_sha256 }}\\nbasemaps-assets={{ basemaps_assets_commit }}\\nbasemaps-assets-sha256={{ basemaps_assets_sha256 }}\\n > /tiles/BUILD'

# The pins are pasted into the steps' shell commands, and `just name=value data` overrides them, so
# each must match its own shape before any step runs: a value with a shell character never reaches
# a shell.
_data-pins:
    @{{ if protomaps_build =~ '^[0-9]{8}$' { "" } else { error("protomaps_build must be a build date, eight digits") } }}
    @{{ if protomaps_basemap_version =~ '^[0-9]+\.[0-9]+\.[0-9]+$' { "" } else { error("protomaps_basemap_version must be a version, such as 4.15.1") } }}
    @{{ if israel_tiles_sha256 =~ '^[0-9a-f]{64}$' { "" } else { error("israel_tiles_sha256 must be 64 lowercase hex digits") } }}
    @{{ if basemaps_assets_commit =~ '^[0-9a-f]{40}$' { "" } else { error("basemaps_assets_commit must be a full commit, 40 lowercase hex digits") } }}
    @{{ if basemaps_assets_sha256 =~ '^[0-9a-f]{64}$' { "" } else { error("basemaps_assets_sha256 must be 64 lowercase hex digits") } }}
    @{{ if pmtiles_image =~ '^[a-z0-9./_-]+:[A-Za-z0-9._-]+@sha256:[0-9a-f]{64}$' { "" } else { error("pmtiles_image must be name:tag@sha256:digest") } }}
    @{{ if caddy_image =~ '^[a-z0-9./_-]+:[A-Za-z0-9._-]+@sha256:[0-9a-f]{64}$' { "" } else { error("caddy_image must be name:tag@sha256:digest") } }}

# Scan every lockfile and the pom for known vulnerabilities. Needs Docker.
audit:
    docker run --rm -v "{{ justfile_directory() }}:/src:ro" {{ osv_scanner_image }} scan source --maven-registry "{{ maven_mirror }}" -r /src
