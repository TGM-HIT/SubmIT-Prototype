#!/usr/bin/env python3
"""Audit an explicitly bounded commit range using Git path metadata only."""

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]
IMPLEMENTATION_PATHS = frozenset(
    {
        "scripts/compose.sh",
        "scripts/pgadmin-entrypoint.sh",
        "scripts/check-startup-config.py",
        "scripts/check-change-scope.py",
        "compose.yaml",
        ".env.example",
        ".dockerignore",
        "README.md",
        "backend/README.md",
    }
)
COORDINATOR_PATHS = frozenset(
    {
        "harness/gates/commit-scope.java",
        "harness/tickets/Bug-40.md",
        "harness/tickets/done/Bug-40.md",
        "harness/plan.md",
    }
)
FULL_SHA = re.compile(r"^(?:[0-9a-f]{40}|[0-9a-f]{64})$")


class AuditError(Exception):
    """A closed, actionable failure in the caller-supplied audit."""


def git_metadata(repo: Path, cwd: Path, *arguments: str) -> bytes:
    if not arguments:
        raise AuditError("internal error: empty Git metadata command")
    command = arguments[0]
    allowed = {
        "rev-parse": {"--verify"},
        "merge-base": {"--is-ancestor"},
        "rev-list": {"--reverse"},
        "diff-tree": {"--root"},
        "ls-tree": {"-r"},
    }
    if command not in allowed or arguments[1] not in allowed[command]:
        raise AuditError("refusing a Git command outside the metadata-only allowlist")
    result = subprocess.run(
        ["git", "-C", str(repo), *arguments],
        cwd=cwd,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if result.returncode != 0:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        raise AuditError(f"Git metadata query failed ({command}): {detail or 'no diagnostic'}")
    return result.stdout


def resolve_commit(repo: Path, cwd: Path, value: str, label: str) -> str:
    if FULL_SHA.fullmatch(value) is None:
        raise AuditError(f"{label} must be a full 40- or 64-character commit SHA")
    output = git_metadata(
        repo,
        cwd,
        "rev-parse",
        "--verify",
        "--quiet",
        "--end-of-options",
        f"{value}^{{commit}}",
    )
    resolved = output.decode("ascii", errors="strict").strip()
    if resolved != value:
        raise AuditError(f"{label} did not resolve to the exact supplied full SHA")
    return resolved


def valid_repo_path(path: object) -> bool:
    return (
        isinstance(path, str)
        and bool(path)
        and "\x00" not in path
        and "\\" not in path
        and not path.startswith("/")
        and all(part not in ("", ".", "..") for part in path.split("/"))
    )


def load_policy(policy_path: Path) -> tuple[frozenset[str], dict[str, frozenset[str]]]:
    try:
        data = json.loads(policy_path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise AuditError(f"could not read valid UTF-8 JSON policy: {error}") from error
    if not isinstance(data, dict) or set(data) != {
        "implementation_paths",
        "coordinator_changes",
    }:
        raise AuditError("policy must contain only implementation_paths and coordinator_changes")

    implementation = data["implementation_paths"]
    if (
        not isinstance(implementation, list)
        or any(not valid_repo_path(path) for path in implementation)
        or len(implementation) != len(set(implementation))
        or frozenset(implementation) != IMPLEMENTATION_PATHS
    ):
        raise AuditError("implementation_paths must be the exact nine-path POSIX allowlist")

    coordinator = data["coordinator_changes"]
    if not isinstance(coordinator, dict):
        raise AuditError("coordinator_changes must map full commit SHAs to exact path arrays")
    normalized: dict[str, frozenset[str]] = {}
    for sha, paths in coordinator.items():
        if not isinstance(sha, str) or FULL_SHA.fullmatch(sha) is None:
            raise AuditError("each coordinator attribution key must be a full commit SHA")
        if (
            not isinstance(paths, list)
            or not paths
            or any(not valid_repo_path(path) or path not in COORDINATOR_PATHS for path in paths)
            or len(paths) != len(set(paths))
        ):
            raise AuditError("coordinator attribution must contain nonempty exact allowed paths")
        normalized[sha] = frozenset(paths)
    return frozenset(implementation), normalized


def is_generated_or_protected_artifact(path: str) -> bool:
    normalized = path.replace("\\", "/")
    lower = normalized.lower()
    parts = lower.split("/")
    name = parts[-1]
    if name == ".env.example":
        return False
    if lower in {
        ".env",
        "docker.env",
        "docs/projekte_2026_27.xlsx",
        "bugs-and-ideas.md",
    }:
        return True
    if name.startswith(".env") or name in {
        ".pgpass",
        "credential.json",
        "credentials.json",
        "server.json",
        "servers.json",
    }:
        return True
    if re.search(r"(?:^|[-_.])(?:credential|credentials|server|servers)(?:[-_.].*)?\.json$", name):
        return True
    if name.startswith(("pgadmin4.db", "pgadmin.db")):
        return True
    if any(part in {"postgres-data", "pgadmin-data", "postgres_data", "pgadmin_data"} for part in parts):
        return True
    return name.endswith(
        (
            ".db",
            ".db-journal",
            ".db-shm",
            ".db-wal",
            ".sqlite",
            ".sqlite3",
            ".pem",
            ".key",
            ".p12",
            ".pfx",
        )
    )


def parse_paths(output: bytes, label: str) -> list[str]:
    if not output:
        return []
    try:
        values = output.rstrip(b"\0").split(b"\0")
        paths = [value.decode("utf-8", errors="strict") for value in values if value]
    except UnicodeDecodeError as error:
        raise AuditError(f"{label} contains a path that is not valid UTF-8") from error
    if any(not valid_repo_path(path) for path in paths):
        raise AuditError(f"{label} contains a malformed repository path")
    return paths


def parse_change_events(output: bytes, commit: str) -> list[tuple[str, str]]:
    if not output:
        return []
    try:
        fields = output.rstrip(b"\0").split(b"\0")
        decoded = [field.decode("utf-8", errors="strict") for field in fields]
    except UnicodeDecodeError as error:
        raise AuditError(f"commit {commit} contains a non-UTF-8 path") from error
    if len(decoded) % 2:
        raise AuditError(f"commit {commit} returned ambiguous name-status metadata")
    events: list[tuple[str, str]] = []
    for status, path in zip(decoded[::2], decoded[1::2]):
        if not re.fullmatch(r"[ACDMRTUXB]", status) or not valid_repo_path(path):
            raise AuditError(f"commit {commit} returned malformed change metadata")
        if status.startswith("R") or status.startswith("C"):
            raise AuditError(f"commit {commit} unexpectedly collapsed a rename/copy event")
        events.append((status, path))
    return events


def audit(
    repo: Path,
    baseline: str,
    head: str,
    policy_path: Path,
    *,
    cwd: Path,
) -> tuple[list[tuple[str, str, str]], list[tuple[str, str, str]]]:
    implementation, coordinator = load_policy(policy_path)
    repo = repo.resolve(strict=True)
    if not repo.is_dir():
        raise AuditError("--repo must name a Git working repository")
    baseline_sha = resolve_commit(repo, cwd, baseline, "--baseline")
    head_sha = resolve_commit(repo, cwd, head, "--head")
    ancestry = subprocess.run(
        ["git", "-C", str(repo), "merge-base", "--is-ancestor", baseline_sha, head_sha],
        cwd=cwd,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if ancestry.returncode != 0:
        raise AuditError("baseline is not an ancestor of head or ancestry is unavailable")

    commit_rows = git_metadata(
        repo,
        cwd,
        "rev-list",
        "--reverse",
        "--parents",
        f"{baseline_sha}..{head_sha}",
    ).decode("ascii", errors="strict").splitlines()
    commits: list[str] = []
    for row in commit_rows:
        fields = row.split()
        if len(fields) != 2:
            raise AuditError("merge or ambiguous commit coverage encountered; refusing partial audit")
        commit = fields[0]
        if FULL_SHA.fullmatch(commit) is None:
            raise AuditError("Git returned malformed commit metadata")
        commits.append(commit)

    for sha in coordinator:
        resolve_commit(repo, cwd, sha, "coordinator attribution")
        if sha not in commits:
            raise AuditError(f"coordinator attribution is outside the audited range: {sha}")

    implementation_events: list[tuple[str, str, str]] = []
    coordinator_events: list[tuple[str, str, str]] = []
    matched_coordinator: set[tuple[str, str]] = set()
    for commit in commits:
        output = git_metadata(
            repo,
            cwd,
            "diff-tree",
            "--root",
            "--no-commit-id",
            "--no-renames",
            "--name-status",
            "-z",
            "-r",
            commit,
        )
        for status, path in parse_change_events(output, commit):
            if path in implementation:
                implementation_events.append((commit, status, path))
            elif path in coordinator.get(commit, frozenset()):
                coordinator_events.append((commit, status, path))
                matched_coordinator.add((commit, path))
            else:
                raise AuditError(f"unattributed change event: {commit} {status} {path}")

    expected_coordinator = {
        (sha, path)
        for sha, paths in coordinator.items()
        for path in paths
    }
    if matched_coordinator != expected_coordinator:
        missing = sorted(expected_coordinator - matched_coordinator)
        raise AuditError(f"coordinator policy contains paths not changed at their attributed commit: {missing}")

    tracked_head = parse_paths(
        git_metadata(repo, cwd, "ls-tree", "-r", "--name-only", "-z", head_sha),
        "head tree",
    )
    protected = [path for path in tracked_head if is_generated_or_protected_artifact(path)]
    if protected:
        raise AuditError(f"protected/generated tracked path(s) at head: {sorted(protected)}")
    return implementation_events, coordinator_events


def _fixture_git(repo: Path, *arguments: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(repo), *arguments],
        cwd=ROOT,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        env={
            **os.environ,
            "GIT_AUTHOR_NAME": "Bug-40 synthetic test",
            "GIT_AUTHOR_EMAIL": "synthetic@example.invalid",
            "GIT_COMMITTER_NAME": "Bug-40 synthetic test",
            "GIT_COMMITTER_EMAIL": "synthetic@example.invalid",
        },
    )
    if result.returncode != 0:
        raise AuditError(f"synthetic Git setup failed: {result.stderr.strip()}")
    return result.stdout.strip()


def _fixture_commit(repo: Path, files: dict[str, str], message: str) -> str:
    for path, content in files.items():
        target = repo / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
    _fixture_git(repo, "add", "-A")
    _fixture_git(repo, "commit", "-m", message)
    return _fixture_git(repo, "rev-parse", "HEAD")


def _fixture_delete_commit(repo: Path, paths: list[str], files: dict[str, str], message: str) -> str:
    _fixture_git(repo, "rm", "--", *paths)
    return _fixture_commit(repo, files, message)


def _new_fixture(parent: Path, name: str, protected: tuple[str, ...] = ()) -> tuple[Path, str]:
    repo = parent / name
    repo.mkdir()
    _fixture_git(repo, "init", "-q")
    _fixture_git(repo, "config", "user.name", "Bug-40 synthetic test")
    _fixture_git(repo, "config", "user.email", "synthetic@example.invalid")
    files = {".env.example": "SYNTHETIC_SAMPLE=allowed\n"}
    files.update({path: "synthetic canary only\n" for path in protected})
    baseline = _fixture_commit(repo, files, "synthetic baseline")
    return repo, baseline


def _policy_file(path: Path, implementation: list[str] | None = None, coordinator: dict | None = None) -> Path:
    path.write_text(
        json.dumps(
            {
                "implementation_paths": list(IMPLEMENTATION_PATHS) if implementation is None else implementation,
                "coordinator_changes": {} if coordinator is None else coordinator,
            }
        ),
        encoding="utf-8",
    )
    return path


def _must_fail(label: str, *arguments: object, **keywords: object) -> None:
    try:
        audit(*arguments, **keywords)  # type: ignore[arg-type]
    except AuditError:
        return
    raise AuditError(f"self-test failed to reject {label}")


def run_self_tests() -> None:
    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    required_docs = (
        "scripts/check-change-scope.py",
        "--repo",
        "--baseline",
        "--head",
        "--policy",
        '"implementation_paths"',
        '"coordinator_changes"',
        "AUDIT_REPO",
        "AUDIT_BASELINE",
        "AUDIT_HEAD",
        "AUDIT_POLICY",
        "--self-test",
        "metadata-only",
        "private-free",
        "commit/tree path metadata",
        "no blob/content commands",
        "harness/gates/commit-scope.java",
        "harness/tickets/Bug-40.md",
        "harness/tickets/done/Bug-40.md",
        "harness/plan.md",
        "scripts/compose.sh",
        "scripts/pgadmin-entrypoint.sh",
        "scripts/check-startup-config.py",
        "scripts/check-change-scope.py",
        "compose.yaml",
        ".env.example",
        ".dockerignore",
        "README.md",
        "backend/README.md",
        "no wildcards",
        "ambiguous merge coverage",
    )
    if any(term not in readme for term in required_docs):
        raise AuditError("README audit guidance does not match the CLI, policy, and private-free prerequisites")

    with tempfile.TemporaryDirectory(prefix="bug40-scope-self-test-", dir=ROOT) as temporary:
        parent = Path(temporary)
        repo, base = _new_fixture(
            parent,
            "positive",
            ("harness/tickets/Bug-40.md",),
        )
        coordinator_commit = _fixture_commit(
            repo,
            {
                "scripts/compose.sh": "# synthetic wrapper\n",
                "harness/gates/commit-scope.java": "// synthetic gate event\n",
            },
            "allowed implementation and coordinator event",
        )
        lifecycle_commit = _fixture_delete_commit(
            repo,
            ["harness/tickets/Bug-40.md"],
            {
                "harness/tickets/done/Bug-40.md": "synthetic moved ticket\n",
                "harness/plan.md": "synthetic lifecycle state\n",
            },
            "synthetic lifecycle move",
        )
        positive_policy = _policy_file(
            parent / "positive-policy.json",
            coordinator={
                coordinator_commit: ["harness/gates/commit-scope.java"],
                lifecycle_commit: [
                    "harness/tickets/Bug-40.md",
                    "harness/tickets/done/Bug-40.md",
                    "harness/plan.md",
                ],
            },
        )
        implementation_events, coordinator_events = audit(
            repo,
            base,
            lifecycle_commit,
            positive_policy,
            cwd=ROOT,
        )
        if not implementation_events or len(coordinator_events) != 4:
            raise AuditError("self-test failed to retain separate implementation/coordinator lifecycle events")
        try:
            git_metadata(repo, ROOT, "show", lifecycle_commit)
        except AuditError:
            pass
        else:
            raise AuditError("self-test failed to reject a Git blob/content command")

        _must_fail(
            "an unattributed lifecycle/gate event",
            repo,
            base,
            lifecycle_commit,
            _policy_file(parent / "missing-attribution-policy.json"),
            cwd=ROOT,
        )
        _must_fail(
            "coordinator attribution at the wrong commit",
            repo,
            base,
            lifecycle_commit,
            _policy_file(
                parent / "wrong-commit-policy.json",
                coordinator={lifecycle_commit: ["harness/gates/commit-scope.java"]},
            ),
            cwd=ROOT,
        )
        _must_fail(
            "a wildcard implementation policy",
            repo,
            base,
            lifecycle_commit,
            _policy_file(parent / "wildcard-policy.json", ["scripts/*"]),
            cwd=ROOT,
        )
        _must_fail(
            "an application-source change",
            *_out_of_scope_fixture(parent, "application", "backend/src/main/java/Unexpected.java"),
            _policy_file(parent / "application-policy.json"),
            cwd=ROOT,
        )
        _must_fail(
            "an unrelated guide change",
            *_out_of_scope_fixture(parent, "guide", "backend/DEV-GUIDELINES.md"),
            _policy_file(parent / "guide-policy.json"),
            cwd=ROOT,
        )

        for protected in (
            ".env",
            "docker.env",
            "docs/Projekte_2026_27.xlsx",
            "bugs-and-ideas.md",
            "frontend/.env.local",
            "private/credentials.json",
            "private/servers.json",
            "pgadmin-data/pgadmin4.db",
            "pgadmin-data/pgadmin4.db-wal",
            "postgres-data/PG_VERSION",
            "tls/server.key",
        ):
            protected_repo, protected_head = _new_fixture(
                parent,
                "protected-" + str(len(list(parent.glob("protected-*")))),
                (protected,),
            )
            _must_fail(
                f"pre-existing protected/generated path {protected}",
                protected_repo,
                protected_head,
                protected_head,
                _policy_file(parent / "protected-policy.json"),
                cwd=ROOT,
            )
        allowed_repo, allowed_head = _new_fixture(
            parent,
            "sample-control",
            ("frontend/.env.example",),
        )
        audit(
            allowed_repo,
            allowed_head,
            allowed_head,
            _policy_file(parent / "sample-policy.json"),
            cwd=ROOT,
        )

        bad_base = "f" * 40
        _must_fail(
            "missing history",
            repo,
            bad_base,
            lifecycle_commit,
            positive_policy,
            cwd=ROOT,
        )
        _must_fail(
            "malformed policy",
            repo,
            base,
            lifecycle_commit,
            _policy_file(parent / "malformed-policy.json", ["scripts/compose.sh"]),
            cwd=ROOT,
        )
        _must_fail(
            "invalid ancestry",
            repo,
            lifecycle_commit,
            base,
            positive_policy,
            cwd=ROOT,
        )

        # A linear history with a forbidden transient addition followed by deletion
        # must fail even though the endpoint tree is unchanged.
        transient_repo, transient_base = _new_fixture(parent, "transient")
        transient_added = _fixture_commit(
            transient_repo,
            {"backend/README.md": "allowed\n", "notes with spaces.txt": "synthetic\n"},
            "transient forbidden addition",
        )
        _fixture_git(transient_repo, "rm", "notes with spaces.txt")
        _fixture_git(transient_repo, "commit", "-m", "delete transient forbidden path")
        transient_head = _fixture_git(transient_repo, "rev-parse", "HEAD")
        _must_fail(
            "a forbidden transient add/delete event",
            transient_repo,
            transient_base,
            transient_head,
            _policy_file(parent / "transient-policy.json"),
            cwd=ROOT,
        )
        if transient_added == transient_head:
            raise AuditError("self-test setup error: transient add/delete commits were not distinct")

        rename_repo, rename_base = _new_fixture(parent, "rename")
        _fixture_commit(rename_repo, {"scripts/compose.sh": "allowed source\n"}, "add allowed source")
        rename_base = _fixture_git(rename_repo, "rev-parse", "HEAD")
        _fixture_git(rename_repo, "mv", "scripts/compose.sh", "scripts/pgadmin-entrypoint.sh")
        _fixture_git(rename_repo, "commit", "-m", "allowed-to-allowed rename")
        rename_head = _fixture_git(rename_repo, "rev-parse", "HEAD")
        audit(rename_repo, rename_base, rename_head, _policy_file(parent / "rename-policy.json"), cwd=ROOT)

        _fixture_git(rename_repo, "mv", "scripts/pgadmin-entrypoint.sh", "new path with spaces.txt")
        _fixture_git(rename_repo, "commit", "-m", "rename to forbidden path")
        _fixture_git(rename_repo, "rm", "--", "new path with spaces.txt")
        _fixture_git(rename_repo, "commit", "-m", "delete renamed forbidden path")
        forbidden_rename_head = _fixture_git(rename_repo, "rev-parse", "HEAD")
        _must_fail(
            "a forbidden rename-then-delete path containing spaces",
            rename_repo,
            rename_base,
            forbidden_rename_head,
            _policy_file(parent / "rename-policy2.json"),
            cwd=ROOT,
        )
        reverse_repo, reverse_base = _new_fixture(
            parent,
            "reverse-rename",
            ("outside path with spaces.txt",),
        )
        (reverse_repo / "scripts").mkdir()
        _fixture_git(
            reverse_repo,
            "mv",
            "outside path with spaces.txt",
            "scripts/compose.sh",
        )
        _fixture_git(reverse_repo, "commit", "-m", "forbidden-to-allowed rename")
        reverse_head = _fixture_git(reverse_repo, "rev-parse", "HEAD")
        _must_fail(
            "a forbidden-to-allowed rename with a forbidden old path",
            reverse_repo,
            reverse_base,
            reverse_head,
            _policy_file(parent / "reverse-rename-policy.json"),
            cwd=ROOT,
        )
        if parse_change_events(
            b"A\0name with spaces.txt\0M\0name\twith\tdelimiters.txt\0",
            "0" * 40,
        ) != [
            ("A", "name with spaces.txt"),
            ("M", "name\twith\tdelimiters.txt"),
        ]:
            raise AuditError("self-test failed to preserve NUL-delimited names with spaces/tabs")

        # Merge commits are rejected before per-commit path inspection, avoiding
        # first-parent-only or combined-diff gaps.
        merge_repo, merge_base = _new_fixture(parent, "merge")
        original_branch = _fixture_git(merge_repo, "rev-parse", "--abbrev-ref", "HEAD")
        main_head = _fixture_commit(merge_repo, {"scripts/compose.sh": "main\n"}, "main")
        _fixture_git(merge_repo, "checkout", "-b", "side")
        side_head = _fixture_commit(merge_repo, {"README.md": "side\n"}, "side")
        _fixture_git(merge_repo, "checkout", original_branch)
        _fixture_git(merge_repo, "merge", "--no-ff", "-m", "merge side", "side")
        merge_head = _fixture_git(merge_repo, "rev-parse", "HEAD")
        _must_fail(
            "ambiguous merge coverage",
            merge_repo,
            merge_base,
            merge_head,
            _policy_file(parent / "merge-policy.json"),
            cwd=ROOT,
        )
        if not main_head or not side_head:
            raise AuditError("self-test setup failed to create merge parents")

    print(
        "Scope audit self-test passed: exact policy, coordinator attribution, lifecycle paths, "
        "deletions/renames, transient events, protected metadata, malformed history, and merge rejection."
    )


def _out_of_scope_fixture(parent: Path, name: str, path: str) -> tuple[Path, str, str]:
    repo, base = _new_fixture(parent, name)
    head = _fixture_commit(repo, {path: "synthetic out-of-scope change\n"}, "out of scope")
    return repo, base, head


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Audit an explicit Git range with exact paths; reads Git path metadata, never blobs."
    )
    parser.add_argument("--repo", type=Path, help="repository providing Git commit/tree path metadata")
    parser.add_argument("--baseline", help="full ancestral baseline commit SHA")
    parser.add_argument("--head", help="full head commit SHA")
    parser.add_argument("--policy", type=Path, help="explicit JSON policy with exact path lists")
    parser.add_argument("--self-test", action="store_true", help="run synthetic metadata-only regressions")
    args = parser.parse_args()
    if args.self_test:
        if any(value is not None for value in (args.repo, args.baseline, args.head, args.policy)):
            parser.error("--self-test cannot be combined with audit arguments")
        run_self_tests()
        return 0
    if any(value is None for value in (args.repo, args.baseline, args.head, args.policy)):
        parser.error("--repo, --baseline, --head, and --policy are all required")
    implementation_events, coordinator_events = audit(
        args.repo,
        args.baseline,
        args.head,
        args.policy,
        cwd=ROOT,
    )
    print("Implementation path events:")
    for commit, status, path in implementation_events:
        print(f"  {commit} {status} {path}")
    print("Coordinator-attributed path events:")
    for commit, status, path in coordinator_events:
        print(f"  {commit} {status} {path}")
    print(
        f"Scope audit passed: {len(implementation_events)} implementation events; "
        f"{len(coordinator_events)} separately attributed coordinator events."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AuditError as error:
        print(f"Scope audit failed: {error}", file=sys.stderr)
        raise SystemExit(1)
