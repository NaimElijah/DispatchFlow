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

# The stack's project is the file's own, `dispatchflow`, so every checkout and worktree of the
# repository runs the one stack and its one database (ADR-156). The `data` profile is where the
# services whose files git ignores join, osrm (#168) first; `just infra-check` starts none of them,
# and check_dev_stack.py's DEV_PROFILES holds the same name.
dev_stack := "docker compose -f infra/deploy/compose.dev.yaml --profile data"
dev_env := justfile_directory() / "infra/deploy/.env"

# Build and start the whole stack, wait until every service is healthy and the migrate step has
# finished, print where each one listens, then watch: an edit to the web app, the shared web packages
# or the optimizer reloads in its container (ADR-151, ADR-156). Ctrl+C stops only the watch, which
# just then reports as a failed recipe, and leaves the stack running; a change to the core needs
# Ctrl+C and `just dev` again. Needs Docker.
[doc("Start the whole development stack, healthy, then reload edits until Ctrl+C")]
dev: _dev-env
    {{ dev_stack }} up --build --wait
    @echo "Web app:    http://$({{ dev_stack }} port web 5173)"
    @echo "Core:       http://$({{ dev_stack }} port core 8080)"
    @echo "Optimizer:  http://$({{ dev_stack }} port optimizer 8000)"
    @echo "PostgreSQL: $({{ dev_stack }} port postgres 5432)"
    @echo "Watching for edits. Ctrl+C stops only the watch, and just reports it as a failed recipe."
    @echo "Stop it before just dev-down, or a lockfile or manifest change starts a service again."
    {{ dev_stack }} watch --no-up

# Stop the stack and remove its containers. Its database stays for the next `just dev`. Stop the
# watch first: one left running rebuilds and starts a service again when its lockfile or manifest
# changes.
[doc("Stop the development stack, keeping its database")]
dev-down: _dev-env
    {{ dev_stack }} down

# Stop the stack and delete its database, every row of local data with it, so the next `just dev`
# migrates an empty one and creates the roles again, as a password changed in infra/deploy/.env
# needs. It is the only volume the stack creates, which `just infra-check` holds it to, and every
# checkout shares it. Asks first, before the .env check; `just --yes db-reset` doesn't ask.
[confirm("This deletes the dev stack's database, which every checkout of this repository shares, and all its data. Continue? [y/N]")]
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

# Scan every lockfile and the pom for known vulnerabilities. Needs Docker.
audit:
    docker run --rm -v "{{ justfile_directory() }}:/src:ro" {{ osv_scanner_image }} scan source --maven-registry "{{ maven_mirror }}" -r /src
