"""The FastAPI application that serves the optimization API."""

from enum import StrEnum

from fastapi import FastAPI
from pydantic import BaseModel


class ProbeStatus(StrEnum):
    """What a health probe reports."""

    UP = "UP"


class Probe(BaseModel):
    """A health probe's answer."""

    status: ProbeStatus


def create_app() -> FastAPI:
    """Builds the solver's HTTP application.

    Returns:
        The FastAPI application serving the optimization API.
    """
    # contracts/optimization-api is the API's description (ADR-006), so the service publishes no
    # generated spec or docs pages of its own.
    app = FastAPI(title="DispatchFlow solver", openapi_url=None, docs_url=None, redoc_url=None)

    @app.get("/health/liveness")
    def liveness() -> Probe:
        return Probe(status=ProbeStatus.UP)

    return app
