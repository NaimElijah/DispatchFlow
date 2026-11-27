FROM python:3.14-slim AS base
COPY --from=ghcr.io/astral-sh/uv:0.12.7 /uv /bin/uv
WORKDIR /app
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy UV_PYTHON_DOWNLOADS=never
# Dependencies first, in their own layer, so a source change does not reinstall them.
COPY pyproject.toml uv.lock ./
RUN uv sync --locked --no-dev --no-install-project
RUN useradd --system --uid 10001 dispatchflow
EXPOSE 8000

# The development image, for infra/deploy/compose.dev.yaml alone (ADR-151). Compose Watch writes,
# renames and deletes files in src/ as the container's user, so here dispatchflow owns it.
FROM base AS dev
COPY --chown=dispatchflow src/ src/
RUN uv sync --locked --no-dev
USER dispatchflow
CMD ["/app/.venv/bin/uvicorn", "dispatchflow_optimizer.adapters.inbound.http.app:create_app", "--factory", "--host", "0.0.0.0", "--port", "8000"]

# The production image, the default target: root owns the code, so the service can't rewrite it.
FROM base
COPY src/ src/
RUN uv sync --locked --no-dev
USER dispatchflow
CMD ["/app/.venv/bin/uvicorn", "dispatchflow_optimizer.adapters.inbound.http.app:create_app", "--factory", "--host", "0.0.0.0", "--port", "8000"]
