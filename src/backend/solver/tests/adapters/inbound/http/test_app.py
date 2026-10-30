import pytest
from fastapi.testclient import TestClient

from dispatchflow_solver.adapters.inbound.http.app import create_app


def test_given_a_running_service_when_liveness_probed_then_up() -> None:
    client = TestClient(create_app())

    response = client.get("/health/liveness")

    assert response.status_code == 200
    assert response.headers["content-type"] == "application/json"
    assert response.json() == {"status": "UP"}


@pytest.mark.parametrize("path", ["/openapi.json", "/docs", "/redoc"])
def test_given_a_running_service_when_generated_docs_requested_then_not_found(path: str) -> None:
    assert TestClient(create_app()).get(path).status_code == 404
