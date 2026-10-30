from importlib.util import find_spec


def test_given_the_research_environment_when_looked_up_then_solver_and_pyvrp_installed() -> None:
    assert find_spec("dispatchflow_solver") is not None
    assert find_spec("pyvrp") is not None
