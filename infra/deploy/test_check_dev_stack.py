"""The rules of check_dev_stack.py, each fed a configuration that breaks it (ADR-152).

Compose's own output is the input: a resolved configuration is ``config --format json``'s shape,
and a written one ``config --no-interpolate``'s. No test needs Docker; `just infra-check` then runs
the rules against the real file and the running stack.
"""

from __future__ import annotations

from typing import Any

import pytest

import check_dev_stack as check

SAFE_PORT = {"host_ip": "127.0.0.1", "published": "5173", "target": 5173}
HARDENED = {"cap_drop": ["ALL"], "security_opt": ["no-new-privileges:true"]}


def services(**by_name: dict[str, Any]) -> check.Configuration:
    """A configuration holding the given services."""
    return {"services": by_name}


@pytest.mark.parametrize(
    "port",
    [
        {"host_ip": "0.0.0.0", "published": "5173", "target": 5173},
        {"published": "5173", "target": 5173},
        "${WEB_ADDRESS}:5173:5173",
        "5173:5173",
    ],
)
def test_given_a_port_off_the_loopback_when_ports_are_checked_then_it_is_named(
    port: dict[str, Any] | str,
) -> None:
    problems = check.loopback_problems(services(web={"ports": [port]}))

    assert problems
    assert all(problem.startswith("web publishes") for problem in problems)


def test_given_ports_written_on_the_loopback_when_ports_are_checked_then_none_is_named() -> None:
    configuration = services(
        web={"ports": [SAFE_PORT]}, postgres={"ports": ["127.0.0.1:${PORT:-5432}:5432"]}
    )

    assert check.loopback_problems(configuration) == []


@pytest.mark.parametrize(
    ("setting", "named"),
    [
        ({"network_mode": "host"}, "shares the host's network_mode"),
        ({"pid": "host"}, "shares the host's pid"),
        ({"cgroup": "host"}, "shares the host's cgroup"),
        ({"uts": "host"}, "shares the host's uts"),
        ({"privileged": True}, "runs privileged"),
        ({"cap_add": ["NET_ADMIN"]}, "adds capabilities"),
        ({"security_opt": ["seccomp=unconfined"]}, "runs seccomp:unconfined"),
        ({"security_opt": ["apparmor:unconfined"]}, "runs apparmor:unconfined"),
        ({"user": "root"}, "runs as root"),
        ({"user": "0:0"}, "runs as root"),
        ({"volumes": [{"source": "/var/run/docker.sock"}]}, "mounts Docker's socket"),
    ],
)
def test_given_a_service_with_access_to_the_host_when_checked_then_it_is_named(
    setting: dict[str, Any], named: str
) -> None:
    problems = check.host_access_problems(services(optimizer=setting))

    assert any(named in problem for problem in problems), problems


def test_given_a_non_root_confined_service_when_host_access_is_checked_then_none_is_named() -> None:
    service = {"user": "10001", "security_opt": ["no-new-privileges:true"], "volumes": []}

    assert check.host_access_problems(services(web=service)) == []


def test_given_a_built_service_keeping_capabilities_when_hardening_checked_then_named() -> None:
    service = {"build": {"context": "."}, "security_opt": ["no-new-privileges:true"]}

    assert check.hardening_problems(services(web=service)) == [
        "web is built here but keeps its capabilities: take *hardening"
    ]


def test_given_a_service_that_may_gain_privileges_when_hardening_is_checked_then_it_is_named() -> (
    None
):
    assert check.hardening_problems(services(postgres={"image": "postgres:18.6"})) == [
        "postgres lacks no-new-privileges:true"
    ]


def test_given_hardened_services_when_hardening_is_checked_then_none_is_named() -> None:
    configuration = services(
        web={"build": {"context": "."}, **HARDENED},
        postgres={"image": "postgres:18.6", "security_opt": ["no-new-privileges=true"]},
    )

    assert check.hardening_problems(configuration) == []


def test_given_a_service_written_with_an_env_file_when_credentials_are_checked_then_named() -> None:
    """Interpolated, Compose folds an env file into the environment, so only as written it shows."""
    written = services(core={"env_file": [{"path": ".env", "required": False}]})
    resolved = services(core={"environment": {"SPRING_PROFILES_ACTIVE": "dev"}})

    assert check.credential_problems(resolved, written, {}) == ["core reads an env file"]


def test_given_a_secret_in_a_service_not_its_holder_when_checked_then_the_key_alone_is_named() -> (
    None
):
    throwaway = "4f1c0ffee"
    resolved = services(
        migrate={"environment": {"SPRING_DATASOURCE_PASSWORD": throwaway}},
        core={"environment": {"SPRING_DATASOURCE_PASSWORD": throwaway}},
    )
    written = services(migrate={}, core={})

    problems = check.credential_problems(
        resolved, written, {"DISPATCHFLOW_OWNER_PASSWORD": throwaway}
    )

    assert problems == ["core holds DISPATCHFLOW_OWNER_PASSWORD"]
    assert throwaway not in "".join(problems)


def test_given_a_core_that_waits_for_migrate_to_start_only_when_order_checked_then_named() -> None:
    configuration = services(
        core={"depends_on": {"migrate": {"condition": "service_started"}}},
        migrate={"depends_on": {"postgres": {"condition": "service_healthy"}}},
    )

    assert check.start_order_problems(configuration) == [
        "core does not wait for migrate to be service_completed_successfully"
    ]


def test_given_every_way_compose_reads_a_key_when_variables_are_read_then_each_is_found() -> None:
    compose_text = "\n".join(
        [
            "# ${IN_A_COMMENT}",
            "  A: ${WITH_DEFAULT:-x}",
            "  B: ${REQUIRED:?set it}",
            "  C: $BARE_KEY",
            "  D: $$NOT_A_KEY and $$(command)",
            "  E: ${PLAIN}",
            "  F: ${MIXED:-x} ${MIXED}",
        ]
    )

    assert check.variables_read(compose_text) == {
        "WITH_DEFAULT": True,
        "REQUIRED": False,
        "BARE_KEY": False,
        "PLAIN": False,
        "MIXED": False,
    }


def test_given_a_key_env_example_does_not_list_when_keys_are_checked_then_it_is_named() -> None:
    compose_text = "  A: ${LISTED:-x}\n  B: $UNLISTED"

    assert check.unlisted_keys(compose_text, "LISTED=\n") == [".env.example does not list UNLISTED"]


def test_given_a_new_key_with_no_default_when_secrets_are_checked_then_it_must_be_mapped() -> None:
    compose_text = "  TOKEN: ${DISPATCHFLOW_NEW_TOKEN}\n  MODE: ${DISPATCHFLOW_MODE:-fake}"

    problems = check.unmapped_secrets(compose_text)

    assert len(problems) == 1
    assert problems[0].startswith("DISPATCHFLOW_NEW_TOKEN is read with no default")


def test_given_only_mapped_secrets_when_secrets_are_checked_then_none_is_named() -> None:
    compose_text = "  P: ${POSTGRES_PASSWORD:?set it}\n  A: ${DISPATCHFLOW_APP_PASSWORD:?set it}"

    assert check.unmapped_secrets(compose_text) == []


TESTS_AT_18_6 = 'DockerImageName.parse("postgres:18.6");'
WEB_ON_NODE_24 = "# syntax\nFROM node:24-slim\n"


def test_given_matching_tags_when_tags_are_checked_then_none_is_named() -> None:
    stack = services(postgres={"image": "postgres:18.6"})

    assert check.tag_problems(stack, TESTS_AT_18_6, WEB_ON_NODE_24, "24\n") == []


def test_given_a_postgres_bumped_in_the_stack_alone_when_tags_are_checked_then_it_is_named() -> (
    None
):
    stack = services(postgres={"image": "postgres:18.7"})

    assert check.tag_problems(stack, TESTS_AT_18_6, WEB_ON_NODE_24, "24\n") == [
        "the stack runs postgres:18.7 but the tests postgres:18.6: bump both together"
    ]


@pytest.mark.parametrize("nvmrc", ["26\n", "v26.1.0\n"])
def test_given_a_node_bumped_in_nvmrc_alone_when_tags_are_checked_then_it_is_named(
    nvmrc: str,
) -> None:
    stack = services(postgres={"image": "postgres:18.6"})

    assert check.tag_problems(stack, TESTS_AT_18_6, WEB_ON_NODE_24, nvmrc) == [
        "Dockerfile.dev's Node major differs from .nvmrc's: bump both together"
    ]


def test_given_a_developers_own_settings_when_the_environment_is_made_then_it_matches_ci() -> None:
    inherited = {
        "PATH": "/usr/bin",
        "COMPOSE_PROFILES": "osrm",
        "DISPATCHFLOW_STANDIN_ACCOUNT": "admin",
        "POSTGRES_PASSWORD": "the developer's own",
    }

    environment = check.throwaway_environment(
        inherited, "POSTGRES_PASSWORD=\nDISPATCHFLOW_STANDIN_ACCOUNT=\n"
    )

    assert environment["PATH"] == "/usr/bin"
    assert "COMPOSE_PROFILES" not in environment
    assert "DISPATCHFLOW_STANDIN_ACCOUNT" not in environment
    secret_values = [environment[key] for key in check.SECRET_HOLDERS]
    assert "the developer's own" not in secret_values
    assert len(set(secret_values)) == len(secret_values)


def test_given_the_configuration_when_the_override_is_written_then_ports_go_core_is_tagged() -> (
    None
):
    configuration = services(web={"ports": [SAFE_PORT]}, core={"ports": [SAFE_PORT]}, migrate={})

    override = check.check_override(configuration, "dispatchflow-check-1a2b")

    assert override == (
        "services:\n"
        "  web:\n    ports: !reset []\n"
        "  core:\n    ports: !reset []\n    image: dispatchflow-core:dispatchflow-check-1a2b\n"
        "  migrate:\n    image: dispatchflow-core:dispatchflow-check-1a2b\n"
    )


def test_given_projects_in_every_state_when_the_sweep_picks_then_only_stopped_checks_go() -> None:
    listing = [
        {"Name": "dispatchflow-check-0001", "Status": "exited(5)"},
        {"Name": "dispatchflow-check-0002", "Status": "exited(1), dead(1)"},
        {"Name": "dispatchflow-check-0003", "Status": "running(4), exited(1)"},
        {"Name": "dispatchflow-check-0004", "Status": "created(5)"},
        {"Name": "dispatchflow", "Status": "exited(5)"},
    ]

    assert check.finished_projects(listing) == [
        "dispatchflow-check-0001",
        "dispatchflow-check-0002",
    ]


@pytest.mark.parametrize(
    "output",
    ['[{"Service": "migrate"}]', '{"Service": "migrate"}\n'],
)
def test_given_either_json_shape_compose_prints_when_parsed_then_the_object_is_read(
    output: str,
) -> None:
    assert check.parse_json_lines(output) == [{"Service": "migrate"}]
