# DispatchFlow's commands. CI runs these same recipes (.github/workflows/ci.yml), so a check that
# passes here passes there. `just` lists them all.

set windows-shell := ["powershell.exe", "-NoLogo", "-NoProfile", "-Command"]

mvnw := if os_family() == "windows" { ".\\mvnw.cmd" } else { "./mvnw" }
redocly := "npx --yes @redocly/cli@2.72.1"
# Pinned by digest as well as tag, so a re-pointed tag cannot change what scans the repository.
osv_scanner_image := "ghcr.io/google/osv-scanner:v2.7.0@sha256:4f3b7e2c64b9d38a5febc5a7e4a3dfef1d1362f726dea7a2ef4762b46469fcd0"

# List every recipe.
default:
    @{{ just_executable() }} --list

# Install every component's dependencies.
setup: core-setup optimizer-setup research-setup web-setup mobile-setup

# Run every check that CI runs. Needs Docker, for the core's integration tests and the audit.
check: core-check optimizer-check research-check web-check mobile-check contracts-check audit

# Reformat every component's code.
format: core-format optimizer-format research-format web-format mobile-format

# --- backend/core: Java 21, Spring Boot, Maven ---

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

# Start the core backend on port 8080 against a throwaway PostgreSQL container. Needs Docker.
[working-directory('src/backend/core')]
core-run:
    {{ mvnw }} -B -ntp spring-boot:test-run

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

# --- frontend/web: React, TypeScript, Vite, npm ---

[working-directory('src/frontend/web')]
web-setup:
    npm ci

# Lint, format check, tests and a production build.
[working-directory('src/frontend/web')]
web-check:
    npm run lint
    npm run format:check
    npm test
    npm run build

[working-directory('src/frontend/web')]
web-format:
    npm run format

# Start the web app's dev server.
[working-directory('src/frontend/web')]
web-run:
    npm run dev

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

# Scan every lockfile and the pom for known vulnerabilities. Needs Docker.
audit:
    docker run --rm -v "{{ justfile_directory() }}:/src:ro" {{ osv_scanner_image }} scan source -r /src
