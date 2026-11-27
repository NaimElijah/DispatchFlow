"""Checks infra/deploy/compose.dev.yaml, as `just infra-check` runs it (ADR-151).

The static checks read the file as Compose resolves it, with every profile, .env.example's keys and
a throwaway password for each key, never a developer's own infra/deploy/.env:

- every published port binds 127.0.0.1, written so rather than through a variable, and no service
  shares the host's network, process or IPC namespace, runs privileged, adds a capability, runs as
  root or mounts Docker's socket, so the stand-in session answers nobody off the laptop (ADR-019,
  ADR-140 decision 4);
- each service holds only its own passwords and no service reads an env file: the core never sees
  the owner's (ADR-041, ADR-145);
- .env.example lists every key the file reads;
- the core waits for the migrate step to succeed, and the migrate step for PostgreSQL to be healthy.

With ``--live``, the whole stack then starts in a project of its own, with a random name and no port
on the host, so it never meets a running ``just dev`` or another check:

- the migrate step exits 0, and the PostgreSQL server runs with #250's settings;
- every Java connection is ``app``'s, past ``DatabaseSessionGuard``;
- the core's JVM runs in Israel's zone (ADR-088);
- through the web app's proxy, the stand-in signs in a same-origin call and refuses a same-site
  one, and the dev server answers no other origin's CORS request (ADR-148).

The project, its volume and its images are removed afterwards, whatever happened. Exits 0 when every
check passes and 1 when one fails, naming it, or when the removal fails. It prints no password.
"""

from __future__ import annotations

import json
import os
import re
import secrets
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any

DEPLOY = Path(__file__).resolve().parent
COMPOSE_FILE = DEPLOY / "compose.dev.yaml"
ENV_EXAMPLE = DEPLOY / ".env.example"
LOOPBACK = "127.0.0.1"
PROJECT_PREFIX = "dispatchflow-check"
CORE_IMAGE = "dispatchflow-core"

# Each password's key, and the services that may hold its value (ADR-041, ADR-145).
PASSWORD_HOLDERS = {
    "POSTGRES_PASSWORD": {"postgres"},
    "DISPATCHFLOW_OWNER_PASSWORD": {"postgres", "migrate"},
    "DISPATCHFLOW_APP_PASSWORD": {"postgres", "core"},
}

# The services that run the core's image, which the live check tags with its own project's name.
CORE_IMAGE_SERVICES = ("migrate", "core")

# Namespaces a service may not share with the host.
HOST_NAMESPACES = ("network_mode", "pid", "ipc", "userns_mode")
ROOT_USERS = ("root", "0")
DOCKER_SOCKET = "docker.sock"

# The start order ADR-041 and ADR-145 require: (service, the service it waits for, the condition).
START_ORDER = (
    ("core", "migrate", "service_completed_successfully"),
    ("migrate", "postgres", "service_healthy"),
)

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

# Asks the web container's own dev server as a browser on the laptop would, with Host
# localhost:5173: the method, the path and the request's other headers as JSON are its arguments,
# and it prints the answer's status and its Access-Control-Allow-Origin. node:http, because fetch
# may refuse to set a Sec- header.
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
    environment = throwaway_environment()
    try:
        check_static(environment)
        print(
            "ok: compose.dev.yaml publishes only on 127.0.0.1, and each service gets only its own"
        )
        if "--live" in arguments:
            return check_live_stack(environment)
    except CheckFailedError as failure:
        print(f"FAILED: {failure}", file=sys.stderr)
        return 1
    return 0


def throwaway_environment() -> dict[str, str]:
    """This process's environment with a fresh, distinct password for each key the file needs."""
    environment = dict(os.environ)
    for key in PASSWORD_HOLDERS:
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
    """The file as Compose resolves it under every profile, which validates it as it resolves it."""
    arguments = ["--profile", "*", "config", "--format", "json"]
    if not interpolate:
        arguments.append("--no-interpolate")
    result = run(compose(*arguments), environment)
    if result.returncode != 0:
        raise CheckFailedError(f"docker compose config refused the file:\n{result.stderr.strip()}")
    configuration: Configuration = json.loads(result.stdout)
    return configuration


def check_static(environment: dict[str, str]) -> None:
    """Runs every check that reads the file alone."""
    resolved = compose_configuration(environment, interpolate=True)
    written = compose_configuration(environment, interpolate=False)
    problems = [
        *loopback_problems(resolved),
        *loopback_problems(written),
        *host_access_problems(resolved),
        *credential_problems(resolved, environment),
        *start_order_problems(resolved),
        *unlisted_keys(),
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
        if str(service.get("user", "")).split(":")[0] in ROOT_USERS:
            problems.append(f"{name} runs as root")
        for volume in service.get("volumes", []):
            if DOCKER_SOCKET in str(volume.get("source", "")):
                problems.append(f"{name} mounts Docker's socket")
    return problems


def credential_problems(configuration: Configuration, environment: dict[str, str]) -> list[str]:
    """Each service that holds a password not its own, or reads an env file. Names keys alone."""
    problems = []
    for name, service in configuration["services"].items():
        if service.get("env_file"):
            problems.append(f"{name} reads an env file")
        text = json.dumps(service)
        for key, holders in PASSWORD_HOLDERS.items():
            if name not in holders and environment[key] in text:
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


def unlisted_keys() -> list[str]:
    """Each key the file reads that .env.example does not list. Comment lines read nothing."""
    written = [
        line
        for line in COMPOSE_FILE.read_text(encoding="utf-8").splitlines()
        if not line.lstrip().startswith("#")
    ]
    read = {key for line in written for key in re.findall(r"\$\{([A-Z0-9_]+)", line)}
    listed = set(
        re.findall(r"^([A-Z0-9_]+)=", ENV_EXAMPLE.read_text(encoding="utf-8"), re.MULTILINE)
    )
    return [f".env.example does not list {key}" for key in sorted(read - listed)]


def check_live_stack(environment: dict[str, str]) -> int:
    """Starts the stack in its own project, checks it, and removes it whatever happened.

    Returns:
        0 when the stack was removed, 1 when the removal failed; a failed check raises instead.
    """
    project = f"{PROJECT_PREFIX}-{secrets.token_hex(4)}"
    configuration = compose_configuration(environment, interpolate=True)
    with tempfile.TemporaryDirectory() as folder:
        override = Path(folder) / "compose.check.yaml"
        override.write_text(check_override(configuration, project), encoding="utf-8")
        stack = LiveStack(project, override, environment)
        try:
            stack.start()
            stack.check_migrate_succeeded()
            stack.check_server_settings()
            stack.check_core_connects_as_app()
            stack.check_core_runs_in_israels_zone()
            stack.check_proxy_keeps_the_browsers_headers()
            stack.check_dev_server_answers_no_other_origin()
            print(
                "ok: the stack starts healthy, and migrate, the roles, the settings and the proxy "
                "check out"
            )
        finally:
            removed = stack.remove()
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

    def start(self) -> None:
        """Builds the images and waits until every service is healthy and migrate is done."""
        print(f"starting the stack as {self.project}; the first build takes several minutes")
        result = subprocess.run(
            self.command("up", "--build", "--wait", "--wait-timeout", str(WAIT_SECONDS)),
            env=self.environment,
            check=False,
        )
        if result.returncode != 0:
            raise CheckFailedError(f"the stack did not start healthy:\n{self.logs()}")

    def check_migrate_succeeded(self) -> None:
        """The one-shot migrate step ran to its end and exited 0."""
        result = run(self.command("ps", "--all", "--format", "json", "migrate"), self.environment)
        containers = parse_json_lines(result.stdout)
        if len(containers) != 1 or containers[0].get("ExitCode") != 0:
            raise CheckFailedError(f"the migrate step did not exit 0: {result.stdout.strip()}")

    def query(self, sql: str) -> str:
        """Runs one query as the superuser, inside the postgres container."""
        result = run(
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
            self.environment,
        )
        if result.returncode != 0:
            raise CheckFailedError(f"psql failed: {result.stderr.strip()}")
        return result.stdout.strip()

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
        if JVM_ZONE_FLAG not in result.stdout.split(b"\0"):
            raise CheckFailedError(
                "the core's JVM does not start with -Duser.timezone=Asia/Jerusalem"
            )

    def probe(self, method: str, headers: dict[str, str]) -> dict[str, Any]:
        """Sends one request through the web container's dev server, and reads its answer."""
        result = run(
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
            self.environment,
        )
        if result.returncode != 0:
            raise CheckFailedError(f"the web container's probe failed: {result.stderr.strip()}")
        answer: dict[str, Any] = json.loads(result.stdout)
        return answer

    def check_proxy_keeps_the_browsers_headers(self) -> None:
        """A same-origin call through the proxy is signed in; a same-site one is refused."""
        for fetch_site, expected in (
            ("same-origin", SIGNED_IN_UNKNOWN_PATH),
            ("same-site", NOT_SIGNED_IN),
        ):
            status = self.probe("GET", {"sec-fetch-site": fetch_site})["status"]
            if status != expected:
                raise CheckFailedError(
                    f"through the web proxy, a {fetch_site} call to {PROBE_PATH} "
                    f"answered {status}, not {expected}"
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

    def remove(self) -> bool:
        """Removes the project's containers, network, volume and images; says so when it can't.

        ``--rmi local`` removes the images the project built, the core's own tag among them in the
        Compose this was checked with; the tag is removed by name only if one still exists.
        """
        removals = [
            run(
                self.command("down", "--volumes", "--remove-orphans", "--rmi", "local"),
                self.environment,
            )
        ]
        core_image = f"{CORE_IMAGE}:{self.project}"
        if run(["docker", "image", "inspect", core_image], self.environment).returncode == 0:
            removals.append(run(["docker", "image", "rm", core_image], self.environment))
        failed = [result for result in removals if result.returncode != 0]
        for result in failed:
            print(f"FAILED to remove {self.project}: {result.stderr.strip()}", file=sys.stderr)
        return not failed


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
