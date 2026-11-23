import tomllib
from importlib.util import find_spec
from pathlib import Path

LOCKFILE = Path(__file__).resolve().parents[1] / "uv.lock"


# ADR-004: PyVRP is a benchmark reference only, so the service must never install it, nor declare
# it anywhere it could be installed from, such as an extra or a dependency group.
def test_given_the_optimizer_environment_when_pyvrp_looked_up_then_it_is_not_installed() -> None:
    assert find_spec("pyvrp") is None


def test_given_the_optimizer_lockfile_when_read_then_pyvrp_is_not_in_it() -> None:
    packages = tomllib.loads(LOCKFILE.read_text(encoding="utf-8"))["package"]

    assert "pyvrp" not in {package["name"] for package in packages}
