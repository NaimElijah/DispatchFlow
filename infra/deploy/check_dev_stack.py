"""Checks infra/deploy/compose.dev.yaml, as `just infra-check` runs it (ADR-151, ADR-152).

The static checks read the file as Compose resolves it, with every profile, .env.example's keys and
a throwaway value for each secret, never a developer's own infra/deploy/.env or exported settings:

- every published port binds 127.0.0.1, written so rather than through a variable, and no service
  shares a host namespace, runs privileged or unconfined, adds a capability, sets a root user or
  mounts Docker's socket, so the stand-in session answers nobody off the laptop (ADR-019, ADR-140
  decision 4);
- every service gains no privilege, and each one built from our own images drops every capability;
- each service holds only its own secrets, and no service reads an env file: the core never sees
  the owner's password (ADR-041, ADR-145). A key read with no default is a secret, and must name
  the services that may hold it in SECRET_HOLDERS;
- .env.example lists every key the file reads;
- the core waits for the migrate step to succeed, and the migrate step for PostgreSQL to be healthy;
- the stack's PostgreSQL and Node tags are the tests' and .nvmrc's.

With ``--live``, the whole stack then starts in a project of its own, with a random name and no port
on the host, so it never meets a running ``just dev`` or another check. It first removes what an
interrupted earlier check left behind:

- every service turns healthy, none of their main processes runs as root, and the migrate step
  exits 0;
- the PostgreSQL server runs with #250's settings, and every Java connection is ``app``'s, past
  ``DatabaseSessionGuard``;
- the core's JVM runs in Israel's zone (ADR-088);
- through the web app's proxy, the stand-in signs in a same-origin call from a loopback host,
  refuses a same-site one and one whose Host is another name, and the dev server answers no other
  origin's CORS request (ADR-148).

The project, its volume and its images are removed afterwards, whatever happened. Exits 0 when every
check passes and 1 when one fails, naming it, or when the removal fails. It prints no secret.
"""

from __future__ import annotations

import json
import os
import re
import secrets
import subprocess
import sys
import tempfile
from collections.abc import Mapping
from pathlib import Path
from typing import Any

DEPLOY = Path(__file__).resolve().parent
REPOSITORY = DEPLOY.parent.parent
COMPOSE_FILE = DEPLOY / "compose.dev.yaml"
ENV_EXAMPLE = DEPLOY / ".env.example"
TESTCONTAINERS_CONFIGURATION = (
    REPOSITORY / "src/backend/core/src/test/java/com/dispatchflow/TestcontainersConfiguration.java"
)
WEB_DOCKERFILE = REPOSITORY / "src/frontend/web/Dockerfile.dev"
NVMRC = REPOSITORY / ".nvmrc"
LOOPBACK = "127.0.0.1"
PROJECT_PREFIX = "dispatchflow-check"
CORE_IMAGE = "dispatchflow-core"

# Each secret's key, and the services that may hold its value (ADR-041, ADR-145). A key the file
# reads with no default is a secret, and the static check fails until it is listed here.
SECRET_HOLDERS = {
    "POSTGRES_PASSWORD": {"postgres"},
    "DISPATCHFLOW_OWNER_PASSWORD": {"postgres", "migrate"},
    "DISPATCHFLOW_APP_PASSWORD": {"postgres", "core"},
}

# Settings of the developer's own that would make a local check differ from CI's.
COMPOSE_SETTINGS_PREFIX = "COMPOSE_"

# The services that run the core's image, which the live check tags with its own project's name.
CORE_IMAGE_SERVICES = ("migrate", "core")

# Namespaces a service may not share with the host, and the confinements it may not drop.
HOST_NAMESPACES = ("network_mode", "pid", "ipc", "userns_mode", "cgroup", "uts")
UNCONFINED = ("seccomp:unconfined", "apparmor:unconfined")
ROOT_USERS = ("root", "0")
ROOT_UID = "0"
DOCKER_SOCKET = "docker.sock"
NO_NEW_PRIVILEGES = "no-new-privileges:true"
ALL_CAPABILITIES = "ALL"

# The start order ADR-041 and ADR-145 require: (service, the service it waits for, the condition).
START_ORDER = (
    ("core", "migrate", "service_completed_successfully"),
    ("migrate", "postgres", "service_healthy"),
)

# A variable Compose reads: ${KEY}, ${KEY:-default}, ${KEY:?error} and the like, or a bare $KEY,
# but not $$, which is a literal dollar sign.
VARIABLE = re.compile(
    r"(?<!\$)\$(?:\{(?P<braced>[A-Za-z_][A-Za-z0-9_]*)(?P<operator>:?[-?+])?[^}]*\}"
    r"|(?P<bare>[A-Za-z_][A-Za-z0-9_]*))"
)
DEFAULT_OPERATORS = ("-", ":-")
LISTED_KEY = re.compile(r"^([A-Z0-9_]+)=", re.MULTILINE)

# Where the tests' PostgreSQL tag and the web image's Node major are written.
TESTS_POSTGRES_TAG = re.compile(r'DockerImageName\.parse\("(?P<image>[^"]+)"\)')
NODE_BASE_MAJOR = re.compile(r"^FROM node:(?P<major>\d+)", re.MULTILINE)
NVMRC_MAJOR = re.compile(r"\s*v?(?P<major>\d+)")

# --wait-timeout counts from the start of the containers, after the images are built.
WAIT_SECONDS = 600
LOG_LINES_ON_FAILURE = 80

# What the PostgreSQL JDBC driver sets as application_name when the core sets none.
JDBC_APPLICATION_NAME = "PostgreSQL JDBC Driver"

# #250's server settings, as SHOW reports them.
SERVER_SETTINGS = {
    "log_error_verbosity": "terse",
    "log_parameter_max_length": "0",
    "log_parameter_max_length_on_error": "0",
    "lc_messages": "C",
}

JVM_ZONE_FLAG = b"-Duser.timezone=Asia/Jerusalem"

# A path under /api/v1 that no operation serves: signed in, the core answers 404; not signed in,
# 401 before any lookup (ADR-148 decision 4).
PROBE_PATH = "/api/v1/infra-check"
SIGNED_IN_UNKNOWN_PATH = 404
NOT_SIGNED_IN = 401
OTHER_ORIGIN = "http://localhost:8888"

# A host Vite lets through, as it does every *.localhost name, but the stand-in refuses, as it does
# every name but localhost, 127.0.0.1 and [::1]: only a Host passed on as the browser sent it gets
# 401 for it.
OTHER_LOOPBACK_NAME = "dispatchflow.localhost:5173"

# The proxy calls the probe makes: the request's headers, and the status the core must answer.
PROXY_CALLS = (
    ({"sec-fetch-site": "same-origin"}, SIGNED_IN_UNKNOWN_PATH),
    ({"sec-fetch-site": "same-site"}, NOT_SIGNED_IN),
    ({"sec-fetch-site": "same-origin", "host": OTHER_LOOPBACK_NAME}, NOT_SIGNED_IN),
)

# Asks the web container's own dev server as a browser on the laptop would, with Host
# localhost:5173 unless the headers name another: the method, the path and the request's other
# headers as JSON are its arguments, and it prints the answer's status and its
# Access-Control-Allow-Origin. node:http, because fetch may refuse to set a Sec- header.
PROXY_PROBE = """
const http = require('node:http');
const [method, path, headers] = process.argv.slice(1);
const request = http.request(
  { host: '127.0.0.1', port: 5173, method, path,
    headers: { host: 'localhost:5173', ...JSON.parse(headers) } },
  (answer) => {
    const allowOrigin = answer.headers['access-control-allow-origin'] ?? null;
    console.log(JSON.stringify({ status: answer.statusCode, allowOrigin }));
    answer.resume();
  });
request.on('error', (error) => { console.error(error.message); process.exit(1); });
request.end();
"""

# The states of a project's containers that mean no check is still running it.
FINISHED_STATES = {"exited", "dead"}
PROJECT_STATE = re.compile(r"(?P<state>[a-z]+)\(\d+\)")


class CheckFailedError(Exception):
    """A check that did not pass, with what it found."""


Configuration = dict[str, Any]


def main(arguments: list[str]) -> int:
    """Runs the static checks, then the live one when asked.

    Args:
        arguments: the command line, without the script's name; ``--live`` adds the live check.

    Returns:
        The process's exit status: 0 when every check passed and the live stack was removed.
    """
    environment = throwaway_environment(os.environ, ENV_EXAMPLE.read_text(encoding="utf-8"))
    try:
        check_static(environment)
        say("ok: compose.dev.yaml publishes only on 127.0.0.1, and each service gets only its own")
        if "--live" in arguments:
            return check_live_stack(environment)
    except CheckFailedError as failure:
        print(f"FAILED: {failure}", file=sys.stderr, flush=True)
        return 1
    return 0


def say(message: str) -> None:
    """Prints a line at once, so it keeps its place among Compose's own output in CI's log."""
    print(message, flush=True)


def throwaway_environment(inherited: Mapping[str, str], env_example_text: str) -> dict[str, str]:
    """The environment the checks run Compose in.

    Args:
        inherited: this process's environment.
        env_example_text: .env.example, whose keys a developer may have exported.

    Returns:
        The inherited environment without Compose's own settings or any key .env.example lists, so
        a local run reads the file as CI does, and with a fresh, distinct value for each secret.
    """
    listed = set(LISTED_KEY.findall(env_example_text))
    environment = {
        key: value
        for key, value in inherited.items()
        if key not in listed and not key.startswith(COMPOSE_SETTINGS_PREFIX)
    }
    for key in SECRET_HOLDERS:
        environment[key] = secrets.token_hex(24)
    return environment


def compose(*arguments: str, project: str | None = None) -> list[str]:
    """The docker compose command line for compose.dev.yaml, read with .env.example alone."""
    command = ["docker", "compose", "--env-file", str(ENV_EXAMPLE), "-f", str(COMPOSE_FILE)]
    if project is not None:
        command += ["-p", project]
    return command + list(arguments)


def run(command: list[str], environment: dict[str, str]) -> subprocess.CompletedProcess[str]:
    """Runs a command and captures its output, without raising on a non-zero status."""
    return subprocess.run(command, env=environment, capture_output=True, text=True, check=False)


def compose_configuration(environment: dict[str, str], *, interpolate: bool) -> Configuration:
    """The file as Compose resolves it under every profile, which validates it as it resolves it.

    Interpolated, Compose also folds each env_file into the service's environment, so only the
    file as written still shows that a service reads one.
    """
    arguments = ["--profile", "*", "config", "--format", "json"]
    if not interpolate:
        arguments.append("--no-interpolate")
    result = run(compose(*arguments), environment)
    if result.returncode != 0:
        raise CheckFailedError(f"docker compose config refused the file:\n{result.stderr.strip()}")
    configuration: Configuration = json.loads(result.stdout)
    return configuration


def check_static(environment: dict[str, str]) -> None:
    """Runs every check that reads the files alone."""
    resolved = compose_configuration(environment, interpolate=True)
    written = compose_configuration(environment, interpolate=False)
    compose_text = COMPOSE_FILE.read_text(encoding="utf-8")
    secret_values = {key: environment[key] for key in SECRET_HOLDERS}
    problems = [
        *loopback_problems(resolved),
        *loopback_problems(written),
        *host_access_problems(resolved),
        *hardening_problems(resolved),
        *credential_problems(resolved, written, secret_values),
        *start_order_problems(resolved),
        *unlisted_keys(compose_text, ENV_EXAMPLE.read_text(encoding="utf-8")),
        *unmapped_secrets(compose_text),
        *tag_problems(
            resolved,
            TESTCONTAINERS_CONFIGURATION.read_text(encoding="utf-8"),
            WEB_DOCKERFILE.read_text(encoding="utf-8"),
            NVMRC.read_text(encoding="utf-8"),
        ),
    ]
    if problems:
        raise CheckFailedError("; ".join(dict.fromkeys(problems)))


def loopback_problems(configuration: Configuration) -> list[str]:
    """Each published port that binds anything but 127.0.0.1.

    Compose resolves a port to its long form, with a ``host_ip``, but leaves one that holds a
    variable as written, so a port is either a mapping or the string from the file.
    """
    problems = []
    for name, service in configuration["services"].items():
        for port in service.get("ports", []):
            if isinstance(port, str):
                if not port.startswith(f"{LOOPBACK}:"):
                    problems.append(f"{name} publishes {port}, not on a written {LOOPBACK}")
            elif port.get("host_ip") != LOOPBACK:
                problems.append(
                    f"{name} publishes {port.get('published')}:{port.get('target')} on "
                    f"{port.get('host_ip') or 'every interface'}, not {LOOPBACK}"
                )
    return problems


def security_options(service: Configuration) -> set[str]:
    """A service's security_opt, with Docker's ``key=value`` spelling read as ``key:value``."""
    return {str(option).replace("=", ":") for option in service.get("security_opt", [])}


def host_access_problems(configuration: Configuration) -> list[str]:
    """Each setting that would hand a container the host, or root's powers on it."""
    problems = []
    for name, service in configuration["services"].items():
        problems += [
            f"{name} shares the host's {key}"
            for key in HOST_NAMESPACES
            if service.get(key) == "host"
        ]
        if service.get("privileged"):
            problems.append(f"{name} runs privileged")
        if service.get("cap_add"):
            problems.append(f"{name} adds capabilities {service['cap_add']}")
        problems += [
            f"{name} runs {option}"
            for option in sorted(security_options(service) & set(UNCONFINED))
        ]
        if str(service.get("user", "")).split(":")[0] in ROOT_USERS:
            problems.append(f"{name} runs as root")
        for volume in service.get("volumes", []):
            if DOCKER_SOCKET in str(volume.get("source", "")):
                problems.append(f"{name} mounts Docker's socket")
    return problems


def hardening_problems(configuration: Configuration) -> list[str]:
    """Each service that may gain a privilege, or that we build and that keeps a capability."""
    problems = []
    for name, service in configuration["services"].items():
        if NO_NEW_PRIVILEGES not in security_options(service):
            problems.append(f"{name} lacks {NO_NEW_PRIVILEGES}")
        if "build" in service and ALL_CAPABILITIES not in service.get("cap_drop", []):
            problems.append(f"{name} is built here but keeps its capabilities: take *hardening")
    return problems


def credential_problems(
    resolved: Configuration, written: Configuration, secret_values: Mapping[str, str]
) -> list[str]:
    """Each service that holds a secret not its own, or reads an env file. Names keys alone.

    Args:
        resolved: the file as Compose resolves it, where each secret's throwaway value shows.
        written: the file as written, the only one that still names an env_file.
        secret_values: each secret's key and the throwaway value it was given.

    Returns:
        One problem per service and secret, or env file.
    """
    problems = [
        f"{name} reads an env file"
        for name, service in written["services"].items()
        if service.get("env_file")
    ]
    for name, service in resolved["services"].items():
        text = json.dumps(service)
        for key, value in secret_values.items():
            if name not in SECRET_HOLDERS[key] and value in text:
                problems.append(f"{name} holds {key}")
    return problems


def start_order_problems(configuration: Configuration) -> list[str]:
    """Each wait that ADR-041 and ADR-145's start order needs and the file lacks."""
    problems = []
    for service, dependency, condition in START_ORDER:
        waits = configuration["services"][service].get("depends_on", {}).get(dependency, {})
        if waits.get("condition") != condition:
            problems.append(f"{service} does not wait for {dependency} to be {condition}")
    return problems


def variables_read(compose_text: str) -> dict[str, bool]:
    """Each key the file reads, and whether every place that reads it gives a default.

    Comment lines read nothing.
    """
    defaults: dict[str, bool] = {}
    for line in compose_text.splitlines():
        if line.lstrip().startswith("#"):
            continue
        for match in VARIABLE.finditer(line):
            key = match.group("braced") or match.group("bare")
            has_default = match.group("operator") in DEFAULT_OPERATORS
            defaults[key] = defaults.get(key, True) and has_default
    return defaults


def unlisted_keys(compose_text: str, env_example_text: str) -> list[str]:
    """Each key the file reads that .env.example does not list."""
    listed = set(LISTED_KEY.findall(env_example_text))
    return [
        f".env.example does not list {key}"
        for key in sorted(set(variables_read(compose_text)) - listed)
    ]


def unmapped_secrets(compose_text: str) -> list[str]:
    """Each key the file reads with no default, a secret, that SECRET_HOLDERS doesn't map."""
    return [
        f"{key} is read with no default, so it is a secret: name the services that may hold it in "
        "SECRET_HOLDERS"
        for key, has_default in sorted(variables_read(compose_text).items())
        if not has_default and key not in SECRET_HOLDERS
    ]


def tag_problems(
    configuration: Configuration, testcontainers_text: str, dockerfile_text: str, nvmrc_text: str
) -> list[str]:
    """Each image tag the stack writes apart from the one it must equal.

    Args:
        configuration: the file as Compose resolves it.
        testcontainers_text: TestcontainersConfiguration.java, which names the tests' PostgreSQL.
        dockerfile_text: the web app's Dockerfile.dev, whose base image is Node.
        nvmrc_text: .nvmrc, which pins Node's major for every laptop and CI.

    Returns:
        One problem per pair that differs, so a bump of one copy fails until the other follows.
    """
    problems = []
    stack_postgres = configuration["services"]["postgres"].get("image")
    tests_postgres = TESTS_POSTGRES_TAG.search(testcontainers_text)
    if tests_postgres is None or tests_postgres.group("image") != stack_postgres:
        found = tests_postgres.group("image") if tests_postgres else "no tag"
        problems.append(
            f"the stack runs {stack_postgres} but the tests {found}: bump both together"
        )
    node = NODE_BASE_MAJOR.search(dockerfile_text)
    pinned = NVMRC_MAJOR.match(nvmrc_text)
    if node is None or pinned is None or node.group("major") != pinned.group("major"):
        problems.append("Dockerfile.dev's Node major differs from .nvmrc's: bump both together")
    return problems


def check_live_stack(environment: dict[str, str]) -> int:
    """Starts the stack in its own project, checks it, and removes it whatever happened.

    Returns:
        0 when the stack was removed, 1 when the removal failed; a failed check raises instead.
    """
    sweep_interrupted_checks(environment)
    project = f"{PROJECT_PREFIX}-{secrets.token_hex(4)}"
    configuration = compose_configuration(environment, interpolate=True)
    with tempfile.TemporaryDirectory() as folder:
        override = Path(folder) / "compose.check.yaml"
        override.write_text(check_override(configuration, project), encoding="utf-8")
        stack = LiveStack(project, override, environment)
        try:
            stack.start()
            stack.check_no_main_process_runs_as_root()
            stack.check_migrate_succeeded()
            stack.check_server_settings()
            stack.check_core_connects_as_app()
            stack.check_core_runs_in_israels_zone()
            stack.check_proxy_keeps_the_browsers_headers()
            stack.check_dev_server_answers_no_other_origin()
            say(
                "ok: the stack starts healthy, as no root, and migrate, the roles, the settings "
                "and the proxy check out"
            )
        finally:
            removed = remove_project(project, environment)
    return 0 if removed else 1


def check_override(configuration: Configuration, project: str) -> str:
    """An override that drops every published port and tags the core's image with the project."""
    lines = ["services:"]
    for name, service in configuration["services"].items():
        settings = []
        if service.get("ports"):
            settings.append("    ports: !reset []")
        if name in CORE_IMAGE_SERVICES:
            settings.append(f"    image: {CORE_IMAGE}:{project}")
        if settings:
            lines += [f"  {name}:", *settings]
    return "\n".join(lines) + "\n"


def finished_projects(listing: list[dict[str, Any]]) -> list[str]:
    """The check projects in ``docker compose ls --all`` whose containers have all stopped.

    A project still running, or being created, may be another worktree's check, so it stays.
    """
    finished = []
    for project in listing:
        name = str(project.get("Name", ""))
        states = {
            match.group("state") for match in PROJECT_STATE.finditer(project.get("Status", ""))
        }
        if name.startswith(f"{PROJECT_PREFIX}-") and states and states <= FINISHED_STATES:
            finished.append(name)
    return finished


def sweep_interrupted_checks(environment: dict[str, str]) -> None:
    """Removes what an earlier check left when it was killed before it could remove its project."""
    result = run(
        [
            "docker",
            "compose",
            "ls",
            "--all",
            "--format",
            "json",
            "--filter",
            f"name={PROJECT_PREFIX}-",
        ],
        environment,
    )
    if result.returncode != 0:
        raise CheckFailedError(f"docker compose ls failed: {result.stderr.strip()}")
    for project in finished_projects(parse_json_lines(result.stdout)):
        say(f"removing {project}, which an interrupted check left behind")
        if not remove_project(project, environment):
            raise CheckFailedError(f"could not remove {project}, which an earlier check left")


def remove_project(project: str, environment: dict[str, str]) -> bool:
    """Removes a check project's containers, network, volume and images; says so when it can't.

    ``--rmi local`` removes the images the project built, the core's own tag among them in the
    Compose this was checked with; the tag is removed by name only if one still exists.
    """
    removals = [
        run(
            compose("down", "--volumes", "--remove-orphans", "--rmi", "local", project=project),
            environment,
        )
    ]
    core_image = f"{CORE_IMAGE}:{project}"
    if run(["docker", "image", "inspect", core_image], environment).returncode == 0:
        removals.append(run(["docker", "image", "rm", core_image], environment))
    failed = [result for result in removals if result.returncode != 0]
    for result in failed:
        print(f"FAILED to remove {project}: {result.stderr.strip()}", file=sys.stderr, flush=True)
    return not failed


class LiveStack:
    """compose.dev.yaml started under the check's own project name, with no port on the host."""

    def __init__(self, project: str, override: Path, environment: dict[str, str]) -> None:
        """Names the stack; nothing starts until ``start``."""
        self.project = project
        self.override = override
        self.environment = environment

    def command(self, *arguments: str) -> list[str]:
        """A docker compose command line against this project."""
        return compose("-f", str(self.override), *arguments, project=self.project)

    def checked(self, command: list[str], what: str) -> str:
        """Runs a command that must succeed, and returns what it printed."""
        result = run(command, self.environment)
        if result.returncode != 0:
            raise CheckFailedError(f"{what} failed: {result.stderr.strip()}")
        return result.stdout

    def start(self) -> None:
        """Builds the images and waits until every service is healthy and migrate is done."""
        say(f"starting the stack as {self.project}; the first build takes several minutes")
        result = subprocess.run(
            self.command("up", "--build", "--wait", "--wait-timeout", str(WAIT_SECONDS)),
            env=self.environment,
            check=False,
        )
        if result.returncode != 0:
            raise CheckFailedError(f"the stack did not start healthy:\n{self.logs()}")

    def check_no_main_process_runs_as_root(self) -> None:
        """Each running container's main process runs as a user other than root (ADR-019).

        Only the main process counts: a health check, or a ``docker compose exec``, runs as the
        image's user, which for postgres is root until its entrypoint hands over.
        """
        containers = parse_json_lines(
            self.checked(self.command("ps", "--format", "json"), "docker compose ps")
        )
        for container in containers:
            identifier = str(container["ID"])
            main_pid = self.checked(
                ["docker", "inspect", "--format", "{{.State.Pid}}", identifier], "docker inspect"
            ).strip()
            processes = self.checked(
                ["docker", "top", identifier, "-eo", "pid,uid,comm"], "docker top"
            )
            for line in processes.splitlines()[1:]:
                pid, uid, command = line.split(maxsplit=2)
                if pid == main_pid and uid == ROOT_UID:
                    raise CheckFailedError(f"{container['Service']} runs {command} as root")

    def check_migrate_succeeded(self) -> None:
        """The one-shot migrate step ran to its end and exited 0."""
        output = self.checked(
            self.command("ps", "--all", "--format", "json", "migrate"), "docker compose ps"
        )
        containers = parse_json_lines(output)
        if len(containers) != 1 or containers[0].get("ExitCode") != 0:
            raise CheckFailedError(f"the migrate step did not exit 0: {output.strip()}")

    def query(self, sql: str) -> str:
        """Runs one query as the superuser, inside the postgres container."""
        return self.checked(
            self.command(
                "exec",
                "-T",
                "postgres",
                "psql",
                "-U",
                "postgres",
                "-d",
                "dispatchflow",
                "-tA",
                "-c",
                sql,
            ),
            "psql",
        ).strip()

    def check_server_settings(self) -> None:
        """#250's settings reach the server from the postgres service's command line."""
        for setting, expected in SERVER_SETTINGS.items():
            actual = self.query(f"SHOW {setting}")
            if actual != expected:
                raise CheckFailedError(f"the server's {setting} is {actual!r}, not {expected!r}")

    def check_core_connects_as_app(self) -> None:
        """Every Java connection is app's: migrate's are closed, and the core's pool is open."""
        roles = self.query(
            "SELECT string_agg(DISTINCT usename, ',') FROM pg_catalog.pg_stat_activity "
            f"WHERE application_name = '{JDBC_APPLICATION_NAME}'"
        )
        if not roles:
            raise CheckFailedError(
                f"no connection is named {JDBC_APPLICATION_NAME!r}: "
                "did the core set an application_name?"
            )
        if roles != "app":
            raise CheckFailedError(f"the core's connections are {roles!r}, not only 'app'")

    def check_core_runs_in_israels_zone(self) -> None:
        """The core's image starts its JVM in Asia/Jerusalem (ADR-088, #250)."""
        result = subprocess.run(
            self.command("exec", "-T", "core", "cat", "/proc/1/cmdline"),
            env=self.environment,
            capture_output=True,
            check=False,
        )
        if result.returncode != 0:
            raise CheckFailedError(
                f"reading the core's command line failed: {result.stderr.decode().strip()}"
            )
        if JVM_ZONE_FLAG not in result.stdout.split(b"\0"):
            raise CheckFailedError(
                "the core's JVM does not start with -Duser.timezone=Asia/Jerusalem"
            )

    def probe(self, method: str, headers: dict[str, str]) -> dict[str, Any]:
        """Sends one request through the web container's dev server, and reads its answer."""
        output = self.checked(
            self.command(
                "exec",
                "-T",
                "web",
                "node",
                "-e",
                PROXY_PROBE,
                method,
                PROBE_PATH,
                json.dumps(headers),
            ),
            "the web container's probe",
        )
        answer: dict[str, Any] = json.loads(output)
        return answer

    def check_proxy_keeps_the_browsers_headers(self) -> None:
        """The proxy passes Host and Sec-Fetch-Site on as the browser sent them (ADR-148).

        A same-origin call from a loopback host is signed in. A same-site one is refused, which a
        dropped Sec-Fetch-Site would let through, and so is one whose Host is another name, which
        a proxy that set its own Host would turn into a loopback one.
        """
        for headers, expected in PROXY_CALLS:
            status = self.probe("GET", headers)["status"]
            if status != expected:
                raise CheckFailedError(
                    f"through the web proxy, a call to {PROBE_PATH} with {headers} answered "
                    f"{status}, not {expected}"
                )

    def check_dev_server_answers_no_other_origin(self) -> None:
        """Neither a preflight nor a call from another localhost port is let through by CORS."""
        preflight = {"origin": OTHER_ORIGIN, "access-control-request-method": "POST"}
        for method, headers in (("OPTIONS", preflight), ("GET", {"origin": OTHER_ORIGIN})):
            allowed = self.probe(method, headers)["allowOrigin"]
            if allowed is not None:
                raise CheckFailedError(
                    f"the dev server lets {OTHER_ORIGIN} read a {method} answer ({allowed})"
                )

    def logs(self) -> str:
        """The last lines of every service's log, for a failure's message."""
        result = run(
            self.command("logs", "--no-color", "--tail", str(LOG_LINES_ON_FAILURE)),
            self.environment,
        )
        return result.stdout + result.stderr


def parse_json_lines(output: str) -> list[dict[str, Any]]:
    """Reads ``--format json`` output: one object per line, or one array, by Compose's version."""
    text = output.strip()
    if not text:
        return []
    if text.startswith("["):
        parsed: list[dict[str, Any]] = json.loads(text)
        return parsed
    return [json.loads(line) for line in text.splitlines() if line.strip()]


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
