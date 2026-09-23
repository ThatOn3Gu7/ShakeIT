#!/usr/bin/env python3
"""Turn a failed ShakeIT CI run into one pull-request comment.

Every build job in ``.github/workflows/ci.yml`` tees its Gradle output into
``build.log`` and uploads it as an artifact. This script reads those logs, keeps
the parts a developer actually needs -- Kotlin compiler errors, resource errors,
lint errors, failing test names with their assertion messages, and Gradle's own
"what went wrong" block -- and posts (or updates) a single comment on the pull
request.

The point is the loop: ``gh pr view --comments`` then says exactly what broke, so
the next fix can be made without opening the web UI or scrolling a 40 MB log.

Standard library only; the Python the runner already has is enough.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ElementTree
from dataclasses import dataclass, field
from pathlib import Path

# Lets the script find its own comment again on the next run so it updates in
# place instead of stacking a new one on every push.
MARKER = "<!-- shakeit-ci-report -->"

API_ROOT = "https://api.github.com"

MAX_FINDINGS_PER_JOB = 40
MAX_GRADLE_BLOCK_LINES = 45
MAX_MESSAGE_LINES = 4
MAX_STACK_FRAMES = 3
MAX_COMMENT_CHARS = 60_000


@dataclass(frozen=True)
class Check:
    """One CI job. The names must match ``name:`` in the workflow."""

    name: str
    slug: str
    task: str


CHECKS = (
    Check(name="Unit tests (JVM)", slug="unit-tests", task=":app:testDebugUnitTest"),
    Check(name="Android Lint", slug="lint", task=":app:lintDebug"),
    Check(name="Compile debug APK", slug="assemble-debug", task=":app:assembleDebug"),
)


@dataclass(frozen=True)
class Finding:
    path: str
    line: int | None
    message: str
    severity: str = "error"

    def key(self) -> tuple:
        return (self.path, self.line, self.message, self.severity)

    def render(self) -> str:
        if self.line is None:
            return self.message
        return f"{self.path}:{self.line}: {self.message}"


@dataclass(frozen=True)
class TestFailure:
    test_class: str
    name: str
    message: str
    frames: tuple[str, ...] = ()

    def render(self) -> str:
        short_class = self.test_class.rsplit(".", 1)[-1]
        lines = [f"{short_class} > {self.name}"]
        if self.message:
            lines.append(f"    {self.message}")
        lines.extend(f"    {frame}" for frame in self.frames)
        return "\n".join(lines)


@dataclass
class ParsedLog:
    compiler: list[Finding] = field(default_factory=list)
    resources: list[Finding] = field(default_factory=list)
    lint: list[Finding] = field(default_factory=list)
    tests: list[TestFailure] = field(default_factory=list)
    gradle_block: list[str] = field(default_factory=list)
    failed_tasks: list[str] = field(default_factory=list)
    build_result: str = ""
    warning_count: int = 0
    lint_error_count: int | None = None
    lint_warning_count: int | None = None
    lines: int = 0

    @property
    def problems(self) -> int:
        return len(self.compiler) + len(self.resources) + len(self.tests) + len(
            [f for f in self.lint if f.severity == "error"]
        )


KOTLIN_DIAGNOSTIC = re.compile(
    r"^\s*(?P<severity>[ew]): (?:file://)?(?P<path>[^\s:]+):"
    r"(?P<line>\d+):(?P<column>\d+):?\s*(?P<message>.*)$"
)
# `path:line:col: error: message` -- aapt2 and the resource merger.
RESOURCE_DIAGNOSTIC = re.compile(
    r"^(?P<path>[^\s:]+):(?P<line>\d+):(?P<column>\d+): "
    r"(?P<severity>error|warning): (?P<message>.*)$"
)
# `path:line: Error: message [IssueId]` -- lint's text report on stdout.
LINT_DIAGNOSTIC = re.compile(
    r"^(?P<path>[^\s:]+):(?P<line>\d+): "
    r"(?P<severity>Error|Warning|Informational): (?P<message>.*)$"
)
LINT_SUMMARY = re.compile(r"^(?P<errors>\d+) errors?, (?P<warnings>\d+) warnings?$")
TEST_FAILURE = re.compile(r"^(?P<test_class>[\w.$]+) > (?P<name>.+?) FAILED\s*$")
FAILED_TASK = re.compile(r"^> Task (?P<task>:\S+) FAILED\s*$")
BUILD_RESULT = re.compile(r"^BUILD (?P<outcome>FAILED|SUCCESSFUL) in (?P<duration>.+)$")

# Lint's XML report, spliced into the build log by the lint job.
LINT_XML_BLOB = re.compile(r"<\?xml.*?</issues>", re.DOTALL)

GRADLE_FAILURE_HEADER = "FAILURE: Build failed with an exception."
GRADLE_BLOCK_STOPS = ("* Exception is:", "* Get more help", "BUILD FAILED", "BUILD SUCCESSFUL")


def relative(path: str, workspace: str) -> str:
    """Turns an absolute runner path into something readable in a comment."""
    cleaned = path.strip()
    if cleaned.startswith("file://"):
        cleaned = cleaned[len("file://") :]
    prefix = workspace.rstrip("/") + "/"
    if cleaned.startswith(prefix):
        cleaned = cleaned[len(prefix) :]
    # Lint's XML report writes paths relative to the module directory, and this
    # project has exactly one module.
    if cleaned.startswith("src/"):
        cleaned = "app/" + cleaned
    if not cleaned.startswith("/"):
        return cleaned
    # Last resort: keep the part of the path that identifies the file in the repo.
    for marker in ("/app/src/", "/app/", "/gradle/", "/build.gradle.kts"):
        index = cleaned.rfind(marker)
        if index >= 0:
            return cleaned[index + 1 :]
    return cleaned


def dedupe(findings: list[Finding]) -> list[Finding]:
    seen = set()
    unique = []
    for finding in findings:
        if finding.key() in seen:
            continue
        seen.add(finding.key())
        unique.append(finding)
    return unique


def collect_gradle_block(lines: list[str], start: int) -> tuple[list[str], int]:
    """Keeps Gradle's 'what went wrong' section and stops before the stack trace."""
    block: list[str] = []
    index = start
    while index < len(lines):
        stripped = lines[index].strip()
        if any(stripped.startswith(stop) for stop in GRADLE_BLOCK_STOPS):
            break
        block.append(lines[index].rstrip())
        index += 1
        if len(block) >= MAX_GRADLE_BLOCK_LINES:
            block.append("... (truncated)")
            break
    while block and not block[-1].strip():
        block.pop()
    return block, index


def collect_test_failure(lines: list[str], start: int) -> tuple[str, list[str], int]:
    """Reads the indented exception message and our own stack frames below a FAILED line."""
    messages: list[str] = []
    frames: list[str] = []
    index = start
    while index < len(lines):
        line = lines[index]
        if line.strip() and not line.startswith((" ", "\t")):
            break
        stripped = line.strip()
        if stripped.startswith("at "):
            if "com.shakeit" in stripped and len(frames) < MAX_STACK_FRAMES:
                frames.append(stripped)
        elif stripped and len(messages) < MAX_MESSAGE_LINES:
            messages.append(stripped)
        index += 1
    return " ".join(messages), frames, index


def parse_lint_xml(text: str, workspace: str) -> list[Finding]:
    """Reads lint's XML report when it has been spliced into the build log.

    The text report is preferred -- it is what a developer reads -- but it is
    written by AGP, not by us, so the XML is kept as a second channel.
    """
    findings: list[Finding] = []
    for match in LINT_XML_BLOB.finditer(text):
        try:
            root = ElementTree.fromstring(match.group(0))
        except ElementTree.ParseError:
            continue
        for issue in root.findall("issue"):
            message = (issue.get("message") or "").strip()
            issue_id = issue.get("id") or ""
            location = issue.find("location")
            line = None
            path = ""
            if location is not None:
                path = relative(location.get("file") or "", workspace)
                raw_line = location.get("line") or ""
                line = int(raw_line) if raw_line.isdigit() else None
            findings.append(
                Finding(
                    path=path,
                    line=line,
                    message=f"{message} [{issue_id}]" if issue_id else message,
                    severity=(issue.get("severity") or "error").lower(),
                )
            )
    return dedupe(findings)


def parse_log(path: Path, workspace: str) -> ParsedLog:
    text = path.read_text(encoding="utf-8", errors="replace")
    lines = text.splitlines()
    parsed = ParsedLog(lines=len(lines))

    index = 0
    while index < len(lines):
        line = lines[index]
        stripped = line.strip()

        if match := KOTLIN_DIAGNOSTIC.match(line):
            if match["severity"] == "e":
                parsed.compiler.append(
                    Finding(
                        path=relative(match["path"], workspace),
                        line=int(match["line"]),
                        message=match["message"].strip(),
                    )
                )
            else:
                parsed.warning_count += 1
            index += 1
            continue

        if match := FAILED_TASK.match(stripped):
            if match["task"] not in parsed.failed_tasks:
                parsed.failed_tasks.append(match["task"])
            index += 1
            continue

        if match := BUILD_RESULT.match(stripped):
            parsed.build_result = f"{match['outcome'].lower()} in {match['duration']}"
            index += 1
            continue

        if match := TEST_FAILURE.match(stripped):
            message, frames, index = collect_test_failure(lines, index + 1)
            parsed.tests.append(
                TestFailure(
                    test_class=match["test_class"],
                    name=match["name"],
                    message=message,
                    frames=tuple(frames),
                )
            )
            continue

        if stripped.startswith(GRADLE_FAILURE_HEADER):
            parsed.gradle_block, index = collect_gradle_block(lines, index)
            continue

        if match := RESOURCE_DIAGNOSTIC.match(stripped):
            parsed.resources.append(
                Finding(
                    path=relative(match["path"], workspace),
                    line=int(match["line"]),
                    message=f"{match['severity']}: {match['message'].strip()}",
                    severity=match["severity"],
                )
            )
            index += 1
            continue

        if match := LINT_DIAGNOSTIC.match(stripped):
            parsed.lint.append(
                Finding(
                    path=relative(match["path"], workspace),
                    line=int(match["line"]),
                    message=match["message"].strip(),
                    severity=match["severity"].lower(),
                )
            )
            index += 1
            continue

        if match := LINT_SUMMARY.match(stripped):
            parsed.lint_error_count = int(match["errors"])
            parsed.lint_warning_count = int(match["warnings"])
            index += 1
            continue

        index += 1

    parsed.compiler = dedupe(parsed.compiler)
    parsed.resources = dedupe([f for f in parsed.resources if f.severity == "error"])
    parsed.lint = dedupe(parsed.lint)
    if not parsed.lint:
        parsed.lint = parse_lint_xml(text, workspace)
        if parsed.lint and parsed.lint_error_count is None:
            parsed.lint_error_count = len([f for f in parsed.lint if f.severity in ("error", "fatal")])
            parsed.lint_warning_count = len(parsed.lint) - parsed.lint_error_count
    return parsed


@dataclass
class JobOutcome:
    check: Check
    conclusion: str = "unknown"
    url: str = ""
    parsed: ParsedLog | None = None
    log_path: Path | None = None

    @property
    def failed(self) -> bool:
        # Explicitly failed only: if the API is unreachable and no log was
        # uploaded there is nothing to report, and guessing "failure" would put
        # a red comment on a green run.
        return self.conclusion in ("failure", "timed_out", "action_required")

    @property
    def icon(self) -> str:
        return {
            "success": ":white_check_mark:",
            "skipped": ":fast_forward:",
            "cancelled": ":heavy_multiplication_x:",
            "failure": ":x:",
        }.get(self.conclusion, ":question:")


def infer_conclusion(parsed: ParsedLog) -> str:
    """Reads the outcome out of the log when the jobs API cannot be reached."""
    result = parsed.build_result
    if result.startswith("failed"):
        return "failure"
    if result.startswith("successful"):
        # A green build cannot have compiler errors, but be sure about tests.
        return "failure" if parsed.tests or parsed.compiler else "success"
    if parsed.tests or parsed.compiler or parsed.resources:
        return "failure"
    return "unknown"


# ---------------------------------------------------------------------------
# Markdown
# ---------------------------------------------------------------------------


def fence(lines: list[str], language: str = "text") -> str:
    if not lines:
        return ""
    body = "\n".join(line.rstrip() for line in lines)
    return f"```{language}\n{body}\n```"


def truncate_findings(findings: list[Finding]) -> list[str]:
    rendered = [finding.render() for finding in findings[:MAX_FINDINGS_PER_JOB]]
    if len(findings) > MAX_FINDINGS_PER_JOB:
        rendered.append(f"... and {len(findings) - MAX_FINDINGS_PER_JOB} more")
    return rendered


def job_section(outcome: JobOutcome) -> str:
    parsed = outcome.parsed
    parts: list[str] = []

    if parsed is None:
        parts.append(
            f"### {outcome.icon} {outcome.check.name} — {outcome.conclusion}\n\n"
            "No build log was uploaded for this job, so nothing could be extracted. "
            "Open the job log for the raw output."
        )
        return "\n\n".join(parts)

    heading = f"### {outcome.icon} {outcome.check.name} — `{outcome.check.task}`"
    parts.append(heading)

    if parsed.tests:
        parts.append(f"**{len(parsed.tests)} failing test(s)**\n\n" + fence(
            [line for test in parsed.tests for line in test.render().splitlines()]
        ))

    if parsed.compiler:
        label = "compiler error" if len(parsed.compiler) == 1 else "compiler errors"
        parts.append(
            f"**{len(parsed.compiler)} Kotlin {label}**\n\n"
            + fence(truncate_findings(parsed.compiler))
        )

    if parsed.resources:
        parts.append(
            f"**{len(parsed.resources)} resource error(s)**\n\n"
            + fence(truncate_findings(parsed.resources))
        )

    lint_errors = [finding for finding in parsed.lint if finding.severity == "error"]
    lint_warnings = [finding for finding in parsed.lint if finding.severity != "error"]
    if lint_errors:
        parts.append(
            f"**{len(lint_errors)} lint error(s)**\n\n" + fence(truncate_findings(lint_errors))
        )
    if lint_warnings:
        summary = parsed.lint_warning_count
        count = summary if summary is not None else len(lint_warnings)
        parts.append(
            f"<details>\n<summary>{count} lint warning(s) — not blocking</summary>\n\n"
            + fence(truncate_findings(lint_warnings))
            + "\n\n</details>"
        )

    if parsed.gradle_block:
        parts.append(
            "<details>\n<summary>Gradle failure summary</summary>\n\n"
            + fence(parsed.gradle_block)
            + "\n\n</details>"
        )

    if not parsed.tests and not parsed.compiler and not parsed.resources and not lint_errors:
        # The job failed without producing anything recognisable: a crash, an
        # out-of-memory kill, a missing SDK package. Say so rather than nothing.
        reason = parsed.build_result or "the build did not report a recognisable error"
        parts.append(
            f"No compiler, lint or test errors were found in the log ({reason}). "
            "This usually means the build broke before it reached the compiler — "
            "check the job log for SDK, network or Gradle configuration problems."
        )

    notes = []
    if parsed.failed_tasks:
        notes.append("failed tasks: " + ", ".join(f"`{task}`" for task in parsed.failed_tasks))
    if parsed.build_result:
        notes.append(parsed.build_result)
    if parsed.warning_count:
        notes.append(f"{parsed.warning_count} compiler warning(s)")
    if notes:
        parts.append("_" + " · ".join(notes) + "_")

    return "\n\n".join(parts)


def build_comment(
    outcomes: list[JobOutcome],
    repository: str,
    run_id: str,
    attempt: str,
    sha: str,
    server_url: str,
    head_branch: str,
) -> str:
    failed = [outcome for outcome in outcomes if outcome.failed]
    run_url = f"{server_url}/{repository}/actions/runs/{run_id}"
    if attempt and attempt != "1":
        run_url += f"/attempts/{attempt}"

    rows = []
    for outcome in outcomes:
        name = outcome.check.name
        label = f"[{name}]({outcome.url})" if outcome.url else name
        rows.append(f"| {label} | {outcome.icon} {outcome.conclusion} |")

    unresolved = [outcome for outcome in outcomes if outcome.conclusion == "unknown"]
    if failed:
        title = f"## :x: CI failed — {len(failed)} of {len(outcomes)} checks"
    elif unresolved:
        # Neither the jobs API nor a build log said what happened, so claiming a
        # pass would be a guess.
        title = (
            f"## :warning: CI finished but {len(unresolved)} of {len(outcomes)} "
            "check(s) could not be resolved"
        )
    else:
        title = "## :white_check_mark: CI passed"

    parts = [
        MARKER,
        title,
        f"[Run #{run_id}]({run_url})"
        f" · commit [`{sha[:7]}`]({server_url}/{repository}/commit/{sha})"
        f" · branch `{head_branch}`",
        "| Check | Result |\n| --- | --- |\n" + "\n".join(rows),
    ]

    if failed:
        parts.extend(job_section(outcome) for outcome in failed)
        parts.append(
            "---\n"
            "Raw logs: "
            + ", ".join(
                f"[{outcome.check.name}]({outcome.url})" if outcome.url else outcome.check.name
                for outcome in failed
            )
            + f"\n\nFrom a terminal: `gh run view {run_id} --log-failed`"
            "\n\n_This comment is written by CI and replaced on every push._"
        )
    else:
        parts.append("_This comment is written by CI and replaced on every push._")

    body = "\n\n".join(part for part in parts if part)
    if len(body) > MAX_COMMENT_CHARS:
        body = body[:MAX_COMMENT_CHARS] + "\n\n_... report truncated._"
    return body


# ---------------------------------------------------------------------------
# GitHub API
# ---------------------------------------------------------------------------


def api(token: str, path: str, method: str = "GET", body: dict | None = None):
    request = urllib.request.Request(API_ROOT + path, method=method)
    request.add_header("Authorization", f"Bearer {token}")
    request.add_header("Accept", "application/vnd.github+json")
    request.add_header("X-GitHub-Api-Version", "2022-11-28")
    request.add_header("User-Agent", "shakeit-ci-report")
    payload = None
    if body is not None:
        payload = json.dumps(body).encode("utf-8")
        request.add_header("Content-Type", "application/json")
        request.data = payload
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            raw = response.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")[:2000]
        raise SystemExit(f"GitHub API {method} {path} failed: {error.code} {detail}") from error


def find_pull_request(token: str, repository: str, event: dict, event_name: str, sha: str) -> int | None:
    if event_name == "pull_request":
        number = (event.get("pull_request") or {}).get("number")
        return int(number) if number else None

    ref = event.get("ref") or ""
    branch = ref.replace("refs/heads/", "") if ref else (event.get("workflow_run") or {}).get(
        "head_branch", ""
    )
    if not branch:
        return None
    owner = repository.split("/", 1)[0]
    try:
        pulls = api(token, f"/repos/{repository}/pulls?state=open&head={owner}:{branch}&per_page=100")
    except SystemExit as error:
        print(f"::warning::could not look up a pull request for {branch}: {error}", file=sys.stderr)
        return None
    if not pulls:
        return None
    for pull in pulls:
        if pull.get("head", {}).get("sha") == sha:
            return int(pull["number"])
    return int(pulls[0]["number"])


def fetch_job_conclusions(token: str, repository: str, run_id: str, attempt: str) -> dict[str, dict]:
    path = f"/repos/{repository}/actions/runs/{run_id}/jobs?per_page=100"
    if attempt and attempt != "1":
        path = f"/repos/{repository}/actions/runs/{run_id}/attempts/{attempt}/jobs?per_page=100"
    try:
        payload = api(token, path)
    except SystemExit as error:
        print(f"::warning::could not read job conclusions: {error}", file=sys.stderr)
        return {}
    return {job["name"]: job for job in (payload or {}).get("jobs", [])}


def upsert_comment(token: str, repository: str, number: int, body: str) -> str:
    comments = api(token, f"/repos/{repository}/issues/{number}/comments?per_page=100") or []
    for comment in comments:
        if MARKER in (comment.get("body") or ""):
            api(
                token,
                f"/repos/{repository}/issues/comments/{comment['id']}",
                method="PATCH",
                body={"body": body},
            )
            return "updated"
    api(token, f"/repos/{repository}/issues/{number}/comments", method="POST", body={"body": body})
    return "created"


def delete_stale_comment(token: str, repository: str, number: int) -> bool:
    comments = api(token, f"/repos/{repository}/issues/{number}/comments?per_page=100") or []
    removed = False
    for comment in comments:
        if MARKER in (comment.get("body") or ""):
            api(token, f"/repos/{repository}/issues/comments/{comment['id']}", method="DELETE")
            removed = True
    return removed


# ---------------------------------------------------------------------------
# Wiring
# ---------------------------------------------------------------------------


def find_log(logs_root: Path, slug: str) -> Path | None:
    """Locates the `build.log` an artifact left behind for this job."""
    candidates = sorted(logs_root.glob(f"**/*{slug}*/**/build.log"))
    if candidates:
        return candidates[0]
    candidates = sorted(logs_root.glob(f"**/logs-{slug}/*.log"))
    if candidates:
        return candidates[0]
    single = sorted(logs_root.glob("**/build.log"))
    return single[0] if len(single) == 1 and len(CHECKS) == 1 else None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--logs",
        type=Path,
        default=Path("ci-logs"),
        help="directory the build-log artifacts were downloaded into",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="print the comment instead of posting it (useful when running locally)",
    )
    args = parser.parse_args()

    token = os.environ.get("GITHUB_TOKEN", "")
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    server_url = os.environ.get("GITHUB_SERVER_URL", "https://github.com")
    run_id = os.environ.get("GITHUB_RUN_ID", "0")
    attempt = os.environ.get("GITHUB_RUN_ATTEMPT", "1")
    sha = os.environ.get("GITHUB_SHA", "")
    event_name = os.environ.get("GITHUB_EVENT_NAME", "")
    workspace = os.environ.get("GITHUB_WORKSPACE", os.getcwd())
    head_branch = os.environ.get("GITHUB_HEAD_REF") or os.environ.get("GITHUB_REF_NAME") or ""

    event: dict = {}
    event_path = os.environ.get("GITHUB_EVENT_PATH", "")
    if event_path and Path(event_path).is_file():
        event = json.loads(Path(event_path).read_text(encoding="utf-8", errors="replace"))
    if not head_branch:
        head_branch = (event.get("pull_request") or {}).get("head", {}).get("ref", "")

    logs_root: Path = args.logs
    jobs = fetch_job_conclusions(token, repository, run_id, attempt) if token else {}

    outcomes: list[JobOutcome] = []
    for check in CHECKS:
        job = jobs.get(check.name) or {}
        outcome = JobOutcome(
            check=check,
            conclusion=job.get("conclusion") or "unknown",
            url=job.get("html_url", ""),
        )
        log_path = find_log(logs_root, check.slug) if logs_root.is_dir() else None
        if log_path is not None:
            outcome.log_path = log_path
            outcome.parsed = parse_log(log_path, workspace)
            print(f"parsed {log_path} ({outcome.parsed.lines} lines)")
            if outcome.conclusion == "unknown":
                outcome.conclusion = infer_conclusion(outcome.parsed)
                print(f"{check.name}: conclusion taken from the log -> {outcome.conclusion}")
        else:
            print(f"::warning::no build log artifact found for {check.name}", file=sys.stderr)
        outcomes.append(outcome)

    failures = [outcome for outcome in outcomes if outcome.failed]
    body = build_comment(
        outcomes=outcomes,
        repository=repository,
        run_id=run_id,
        attempt=attempt,
        sha=sha,
        server_url=server_url,
        head_branch=head_branch or "unknown",
    )

    step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if step_summary:
        try:
            with open(step_summary, "a", encoding="utf-8") as summary:
                summary.write(body.replace(MARKER, "", 1).strip() + "\n")
        except OSError as error:
            print(f"::warning::could not write the step summary: {error}", file=sys.stderr)

    unresolved = [outcome for outcome in outcomes if outcome.conclusion == "unknown"]
    if not failures:
        print("No failures found." if not unresolved else f"{len(unresolved)} check(s) unresolved.")
        print(body)
        if args.dry_run or not token or not repository:
            return 0
        number = find_pull_request(token, repository, event, event_name, sha)
        if number is None:
            print("No open pull request for this commit; nothing to comment on.")
            return 0
        if delete_stale_comment(token, repository, number):
            print(f"Removed the stale failure report from #{number}.")
        return 0

    print(f"{len(failures)} check(s) failed: " + ", ".join(o.check.name for o in failures))
    print(body)

    if args.dry_run:
        return 0
    if not token or not repository:
        print("::warning::GITHUB_TOKEN/GITHUB_REPOSITORY missing; report printed above only.", file=sys.stderr)
        return 0

    number = find_pull_request(token, repository, event, event_name, sha)
    if number is None:
        print("No open pull request for this commit; report printed to the job log instead.")
        return 0
    action = upsert_comment(token, repository, number, body)
    print(f"{action.capitalize()} the CI report on {server_url}/{repository}/pull/{number}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
