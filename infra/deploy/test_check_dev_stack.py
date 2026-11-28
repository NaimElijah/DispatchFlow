"""The rules of check_dev_stack.py, each fed a configuration that breaks it (ADR-152).

Compose's own output is the input: a resolved configuration is ``config --format json``'s shape,
and a written one ``config --no-interpolate``'s. No test needs Docker; `just infra-check` then runs
the rules against the real file and the running stack.
"""

from __future__ import annotations

import signal
from pathlib import Path
from typing import Any

import pytest

import check_dev_stack as check

SAFE_PORT = {"host_ip": "127.0.0.1", "published": "5173", "target": 5173}
HARDENED = {"cap_drop": ["ALL"], "security_opt": ["no-new-privileges:true"]}
STACK_COMMAND = ["postgres", "-c", "lc_messages=C"]
TESTS_AT_18_7 = (
    'DockerImageName WIREMOCK = DockerImageName.parse("wiremock/wiremock:3.13");\n'
    'POSTGRES_IMAGE = DockerImageName.parse("postgres:18.7");\n'
    'POSTGRES_COMMAND = {\n    "postgres",\n    "-c",\n    "fsync=off",\n    "-c",\n'
    '    "lc_messages=C"\n};\n'
)
WEB_ON_NODE_24 = "# syntax\nFROM node:24-slim\n"
JUSTFILE_ON_CADDY = 'pmtiles_image := "p"\ncaddy_image := "caddy:2.11.9-alpine"\n'


def services(**by_name: dict[str, Any]) -> check.Configuration:
    """A configuration holding the given services."""
    return {"services": by_name}


def postgres_stack(**settings: Any) -> check.Configuration:
    """A stack whose postgres runs the tests' tag and command, with the given settings."""
    return services(postgres={"image": "postgres:18.7", "command": STACK_COMMAND, **settings})


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
        ({"ipc": "host"}, "shares the host's ipc"),
        ({"userns_mode": "host"}, "shares the host's userns_mode"),
        ({"cgroup": "host"}, "shares the host's cgroup"),
        ({"uts": "host"}, "shares the host's uts"),
        ({"privileged": True}, "runs privileged"),
        ({"cap_add": ["NET_ADMIN"]}, "adds capabilities"),
        ({"devices": [{"source": "/dev/kvm", "target": "/dev/kvm"}]}, "adds devices"),
        ({"security_opt": ["seccomp=unconfined"]}, "runs seccomp:unconfined"),
        ({"security_opt": ["apparmor:unconfined"]}, "runs apparmor:unconfined"),
        ({"security_opt": ["systempaths=unconfined"]}, "runs systempaths:unconfined"),
        ({"security_opt": ["label:disable"]}, "runs label:disable"),
        ({"user": "root"}, "runs as root"),
        ({"user": "0:0"}, "runs as root"),
        ({"volumes": [{"type": "bind", "source": "/var/run/docker.sock"}]}, "Docker's socket"),
        ({"volumes": [{"type": "bind", "source": "/var/run"}]}, "Docker's socket"),
        ({"volumes": [{"type": "bind", "source": "/run/"}]}, "Docker's socket"),
        ({"volumes": [{"type": "bind", "source": str(check.ENV_FILE)}]}, "infra/deploy/.env"),
        ({"volumes": [{"type": "bind", "source": str(check.DEPLOY)}]}, "infra/deploy/.env"),
    ],
)
def test_given_a_service_with_access_to_the_host_when_checked_then_it_is_named(
    setting: dict[str, Any], named: str
) -> None:
    problems = check.host_access_problems(services(optimizer=setting))

    assert any(named in problem for problem in problems), problems


def test_given_a_non_root_confined_service_when_host_access_is_checked_then_none_is_named() -> None:
    service = {
        "user": "10001",
        "security_opt": ["no-new-privileges:true"],
        "volumes": [
            {"type": "bind", "source": str(check.DEPLOY / "postgres" / "initdb")},
            {"type": "volume", "source": "postgres-data"},
        ],
    }

    assert check.host_access_problems(services(web=service)) == []


def test_given_a_built_service_keeping_capabilities_when_hardening_checked_then_named() -> None:
    service = {"build": {"context": "."}, "security_opt": ["no-new-privileges:true"]}

    assert check.hardening_problems(services(web=service)) == [
        "web is built here but keeps its capabilities: take *hardening"
    ]


def test_given_a_service_that_may_gain_privileges_when_hardening_is_checked_then_it_is_named() -> (
    None
):
    assert check.hardening_problems(services(postgres={"image": "postgres:18.7"})) == [
        "postgres lacks no-new-privileges:true"
    ]


def test_given_hardened_services_when_hardening_is_checked_then_none_is_named() -> None:
    configuration = services(
        web={"build": {"context": "."}, **HARDENED},
        postgres={"image": "postgres:18.7", "security_opt": ["no-new-privileges=true"]},
    )

    assert check.hardening_problems(configuration) == []


def test_given_a_service_written_with_an_env_file_when_credentials_are_checked_then_named() -> None:
    """Interpolated, Compose folds an env file into the environment, so only as written it shows."""
    written = services(core={"env_file": [{"path": ".env", "required": False}]})
    resolved = services(core={"environment": {"SPRING_PROFILES_ACTIVE": "dev"}})

    assert check.credential_problems(resolved, written, {}) == ["core reads an env file"]


@pytest.mark.parametrize("kind", ["secrets", "configs"])
def test_given_a_compose_secret_or_config_when_credentials_are_checked_then_it_is_named(
    kind: str,
) -> None:
    """Compose keeps their values at the top of the file, apart from the services that read them."""
    resolved = {**services(core={kind: [{"source": "owner"}]}), kind: {"owner": {"content": "x"}}}

    assert check.credential_problems(resolved, services(core={}), {}) == [
        f"the file declares Compose {kind}, which the holder rule can't follow"
    ]


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


@pytest.mark.parametrize(
    ("core_waits", "migrate_waits", "named"),
    [
        (
            "service_started",
            "service_healthy",
            "core does not wait for migrate to be service_completed_successfully",
        ),
        (
            "service_completed_successfully",
            "service_started",
            "migrate does not wait for postgres to be service_healthy",
        ),
    ],
)
def test_given_a_wait_too_short_when_the_order_is_checked_then_it_is_named(
    core_waits: str, migrate_waits: str, named: str
) -> None:
    configuration = services(
        core={"depends_on": {"migrate": {"condition": core_waits}}},
        migrate={"depends_on": {"postgres": {"condition": migrate_waits}}},
    )

    assert check.start_order_problems(configuration) == [named]


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
            "  G: ${UNSET_DEFAULT-x}",
            "  H: ${REPLACED:+x}",
        ]
    )

    assert check.variables_read(compose_text) == {
        "WITH_DEFAULT": True,
        "REQUIRED": False,
        "BARE_KEY": False,
        "PLAIN": False,
        "MIXED": False,
        "UNSET_DEFAULT": True,
        "REPLACED": False,
    }


def test_given_keys_passed_through_bare_when_keys_are_read_then_they_count_as_secrets() -> None:
    """``- KEY``, or ``KEY:`` with no value, takes the key from the environment as it is."""
    written = services(
        core={"environment": ["SPRING_PROFILES_ACTIVE=dev", "DISPATCHFLOW_LISTED_TOKEN"]},
        optimizer={"environment": {"DISPATCHFLOW_MAPPED_TOKEN": None, "MODE": "fake"}},
    )

    keys = check.keys_read("  A: ${WITH_DEFAULT:-x}", written)

    assert keys == {
        "WITH_DEFAULT": True,
        "DISPATCHFLOW_LISTED_TOKEN": False,
        "DISPATCHFLOW_MAPPED_TOKEN": False,
    }


def test_given_a_key_env_example_does_not_list_when_keys_are_checked_then_it_is_named() -> None:
    keys = {"LISTED": True, "UNLISTED": False}

    assert check.unlisted_keys(keys, "LISTED=\n") == [".env.example does not list UNLISTED"]


def test_given_a_new_key_with_no_default_when_secrets_are_checked_then_it_must_be_mapped() -> None:
    problems = check.unmapped_secrets({"DISPATCHFLOW_NEW_TOKEN": False, "DISPATCHFLOW_MODE": True})

    assert len(problems) == 1
    assert problems[0].startswith("DISPATCHFLOW_NEW_TOKEN is read with no default")


def test_given_only_mapped_secrets_when_secrets_are_checked_then_none_is_named() -> None:
    keys = {"POSTGRES_PASSWORD": False, "DISPATCHFLOW_APP_PASSWORD": False}

    assert check.unmapped_secrets(keys) == []


def test_given_matching_tags_when_tags_are_checked_then_none_is_named() -> None:
    """A container declared above POSTGRES_IMAGE, as #165's WireMock may be, isn't taken for it."""
    assert check.tag_problems(postgres_stack(), TESTS_AT_18_7, WEB_ON_NODE_24, "24\n") == []


@pytest.mark.parametrize(
    ("tests", "found"),
    [
        (TESTS_AT_18_7.replace("18.7", "18.6"), "postgres:18.6"),
        ('DockerImageName.parse("postgres:18.7");', "no POSTGRES_IMAGE"),
    ],
)
def test_given_a_postgres_tag_the_tests_lack_when_tags_are_checked_then_it_is_named(
    tests: str, found: str
) -> None:
    assert check.tag_problems(postgres_stack(), tests, WEB_ON_NODE_24, "24\n") == [
        f"the stack runs postgres:18.7 but the tests {found}: bump both together"
    ]


@pytest.mark.parametrize(
    ("dockerfile", "nvmrc"),
    [(WEB_ON_NODE_24, "26\n"), (WEB_ON_NODE_24, "v26.1.0\n"), ("FROM node-bookworm\n", "24\n")],
)
def test_given_a_node_major_apart_from_nvmrc_when_tags_are_checked_then_it_is_named(
    dockerfile: str, nvmrc: str
) -> None:
    assert check.tag_problems(postgres_stack(), TESTS_AT_18_7, dockerfile, nvmrc) == [
        "Dockerfile.dev's Node major differs from .nvmrc's: bump both together"
    ]


def test_given_the_tiles_service_on_the_justfiles_caddy_when_checked_then_none_is_named() -> None:
    configuration = services(tiles={"image": "caddy:2.11.9-alpine"})

    assert check.caddy_pin_problems(configuration, JUSTFILE_ON_CADDY) == []


@pytest.mark.parametrize(
    ("tiles_image", "justfile_text", "found"),
    [
        ("caddy:2.11.10-alpine", JUSTFILE_ON_CADDY, "caddy:2.11.9-alpine"),
        ("caddy:2.11.9-alpine", "# caddy_image is gone\n", "no caddy_image"),
        (None, JUSTFILE_ON_CADDY, "caddy:2.11.9-alpine"),
    ],
)
def test_given_a_tiles_image_apart_from_the_justfiles_caddy_when_checked_then_it_is_named(
    tiles_image: str | None, justfile_text: str, found: str
) -> None:
    """`just data` runs the justfile's copy, so a bump of one copy alone would go unnoticed."""
    configuration = services(tiles={"image": tiles_image})

    assert check.caddy_pin_problems(configuration, justfile_text) == [
        f"the tiles service runs {tiles_image} but the justfile's caddy_image is {found}: "
        "bump both together"
    ]


def test_given_the_tests_command_when_it_is_read_then_their_own_fsync_off_is_left_out() -> None:
    assert check.tests_server_command(TESTS_AT_18_7) == STACK_COMMAND


@pytest.mark.parametrize(
    "stack_command",
    [
        [*STACK_COMMAND, "-c", "log_min_duration_statement=0"],
        ["postgres", "-c", "lc_messages=en_US.UTF-8"],
        None,
    ],
)
def test_given_a_stack_command_apart_from_the_tests_when_checked_then_it_is_named(
    stack_command: list[str] | None,
) -> None:
    stack = services(postgres={"image": "postgres:18.7", "command": stack_command})

    assert check.server_command_problems(stack, TESTS_AT_18_7) == [
        "the stack's postgres command differs from TestcontainersConfiguration's "
        "POSTGRES_COMMAND: change both together"
    ]


def test_given_the_tests_and_the_stack_on_one_command_when_checked_then_none_is_named() -> None:
    assert check.server_command_problems(postgres_stack(), TESTS_AT_18_7) == []


def test_given_tests_with_no_written_command_when_the_command_is_checked_then_it_is_named() -> None:
    assert check.server_command_problems(postgres_stack(), "no command here") != []


def test_given_a_service_under_another_profile_when_profiles_are_checked_then_it_is_named() -> None:
    configuration = services(osrm={"profiles": ["osrm"]}, tiles={"profiles": ["data"]})

    assert check.profile_problems(configuration) == [
        "osrm is under the osrm profile, which just dev doesn't start"
    ]


def test_given_services_under_data_or_none_when_profiles_are_checked_then_none_is_named() -> None:
    configuration = services(osrm={"profiles": ["data"]}, core={})

    assert check.profile_problems(configuration) == []


@pytest.mark.parametrize(
    ("setting", "named"),
    [
        ({**HARDENED}, "tiles is under the data profile but sets no numeric non-root user"),
        ({"user": "caddy", **HARDENED}, "sets no numeric non-root user"),
        ({"user": "0:0", **HARDENED}, "sets no numeric non-root user"),
        (
            {"user": "65534:65534", "security_opt": ["no-new-privileges:true"]},
            "tiles is under the data profile but keeps its capabilities: take *hardening",
        ),
    ],
)
def test_given_a_data_profile_service_unhardened_when_checked_then_it_is_named(
    setting: dict[str, Any], named: str
) -> None:
    """The live root check never starts the data profile, so only the file can show its user."""
    configuration = services(tiles={"profiles": ["data"], **setting})

    problems = check.data_profile_problems(configuration)

    assert len(problems) == 1
    assert named in problems[0]


def test_given_data_profile_services_hardened_when_checked_then_none_is_named() -> None:
    configuration = services(
        tiles={"profiles": ["data"], "user": "65534:65534", **HARDENED},
        osrm={"profiles": ["data"], "user": "10001", **HARDENED},
        postgres={"image": "postgres:18.7"},
    )

    assert check.data_profile_problems(configuration) == []


def test_given_a_named_volume_beside_the_databases_when_volumes_are_checked_then_it_is_named() -> (
    None
):
    configuration: check.Configuration = {
        "services": {},
        "volumes": {"postgres-data": {}, "osrm-data": {}},
    }

    assert check.volume_problems(configuration) == [
        "the file declares the named volume osrm-data, which just db-reset would delete"
    ]


def test_given_only_the_databases_volume_when_volumes_are_checked_then_none_is_named() -> None:
    assert check.volume_problems({"services": {}, "volumes": {"postgres-data": {}}}) == []
    assert check.volume_problems({"services": {}}) == []


def test_given_a_stack_breaking_every_rule_when_checked_statically_then_each_rule_is_named() -> (
    None
):
    """Each rule is wired in and reads the configuration it must: env files only as written."""
    throwaway = "0wnerpa55"
    resolved = {
        "services": {
            "postgres": {"image": "postgres:18.6", "command": ["postgres"], **HARDENED},
            "migrate": {"depends_on": {"postgres": {"condition": "service_healthy"}}, **HARDENED},
            "core": {
                "ports": [{"published": "8080", "target": 8080}],
                "privileged": True,
                "build": {"context": "."},
                "environment": {"SPRING_DATASOURCE_PASSWORD": throwaway},
                "depends_on": {"migrate": {"condition": "service_started"}},
            },
            "osrm": {"profiles": ["osrm"], **HARDENED},
            "tiles": {"image": "caddy:2.11.10-alpine", "profiles": ["data"], **HARDENED},
        },
        "secrets": {"owner": {"environment": "DISPATCHFLOW_OWNER_PASSWORD"}},
        "volumes": {"postgres-data": {}, "osrm-data": {}},
    }
    written = services(
        postgres={},
        migrate={},
        core={"env_file": [{"path": ".env"}], "environment": ["DISPATCHFLOW_NEW_TOKEN"]},
    )
    sources = check.Sources(
        compose_text="  P: ${POSTGRES_PASSWORD:?x}\n",
        env_example_text="POSTGRES_PASSWORD=\n",
        testcontainers_text=TESTS_AT_18_7,
        dockerfile_text=WEB_ON_NODE_24,
        nvmrc_text="26\n",
        justfile_text=JUSTFILE_ON_CADDY,
    )

    problems = " | ".join(
        check.static_problems(
            resolved, written, sources, {"DISPATCHFLOW_OWNER_PASSWORD": throwaway}
        )
    )

    for named in [
        "core publishes 8080:8080 on every interface",
        "core runs privileged",
        "core lacks no-new-privileges:true",
        "core is built here but keeps its capabilities",
        "core reads an env file",
        "the file declares Compose secrets",
        "core holds DISPATCHFLOW_OWNER_PASSWORD",
        "core does not wait for migrate",
        ".env.example does not list DISPATCHFLOW_NEW_TOKEN",
        "DISPATCHFLOW_NEW_TOKEN is read with no default",
        "the stack runs postgres:18.6 but the tests postgres:18.7",
        "Dockerfile.dev's Node major differs",
        "the tiles service runs caddy:2.11.10-alpine",
        "tiles is under the data profile but sets no numeric non-root user",
        "the stack's postgres command differs",
        "osrm is under the osrm profile",
        "the named volume osrm-data",
    ]:
        assert named in problems, named


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


def test_given_the_configuration_when_the_override_is_written_then_ports_reset_core_tagged() -> (
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


@pytest.mark.parametrize(("effective", "expected"), [("0", "0"), ("10001", "10001")])
def test_given_a_main_process_status_when_its_user_is_read_then_the_effective_uid_is(
    effective: str, expected: str
) -> None:
    status = f"Name:\tnode\nPid:\t1\nUid:\t10001\t{effective}\t10001\t10001\nGid:\t10001\n"

    assert check.main_process_uid(status) == expected


def test_given_a_status_that_names_no_user_when_read_then_the_root_check_fails() -> None:
    with pytest.raises(check.CheckFailedError, match="names no Uid"):
        check.main_process_uid("Name:\tnode\nPid:\t1\n")


def test_given_a_proxy_that_sets_its_own_host_when_its_answers_are_judged_then_it_is_named() -> (
    None
):
    rewritten = {"sec-fetch-site": "same-origin", "host": check.OTHER_LOOPBACK_NAME}
    answers = [
        ({"sec-fetch-site": "same-origin"}, check.SIGNED_IN_UNKNOWN_PATH, 404),
        (rewritten, check.NOT_SIGNED_IN, 404),
    ]

    problems = check.proxy_problems(answers)

    assert len(problems) == 1
    assert check.OTHER_LOOPBACK_NAME in problems[0]
    assert problems[0].endswith("answered 404, not 401")


def test_given_a_proxy_that_passes_the_headers_on_when_its_answers_are_judged_then_none() -> None:
    answers = [(headers, expected, expected) for headers, expected in check.PROXY_CALLS]

    assert check.proxy_problems(answers) == []


def test_given_a_run_ended_by_sigterm_when_the_signal_arrives_then_it_exits_through_finally() -> (
    None
):
    with pytest.raises(SystemExit) as ended:
        check.exit_on_signal(signal.SIGTERM, None)

    assert ended.value.code == check.SIGNAL_EXIT_BASE + signal.SIGTERM


def test_given_a_source_file_that_moved_when_read_then_the_failure_names_it(
    tmp_path: Path,
) -> None:
    with pytest.raises(check.CheckFailedError, match="is gone"):
        check.read(tmp_path / "TestcontainersConfiguration.java")


@pytest.mark.parametrize(
    "output",
    ['[{"Service": "migrate"}]', '{"Service": "migrate"}\n'],
)
def test_given_either_json_shape_compose_prints_when_parsed_then_the_object_is_read(
    output: str,
) -> None:
    assert check.parse_json_lines(output) == [{"Service": "migrate"}]
