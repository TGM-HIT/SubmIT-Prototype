"""Standard-library tests for scripts/verify.py and synthetic archive discovery."""

from __future__ import annotations

import builtins
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest import mock


SCRIPT = Path(__file__).resolve().with_name("verify.py")
SPEC = importlib.util.spec_from_file_location("verify_tool", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
verify = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verify)


SCRIPT_SUFFIXES = {
    ".py", ".js", ".mjs", ".sh", ".bash", ".zsh", ".fish", ".rb",
    ".pl", ".php", ".ps1", ".bat", ".cmd", ".csh", ".java", ".groovy",
    ".gradle", ".ts", ".tsx",
}
COPIED_OR_GENERATED = {
    "baseline", "host-context", "docker-context", "checker-context",
    "vendor", "node_modules", "build", "target", "dist", "generated",
    ".gradle", "__pycache__", "venv", ".venv", "site-packages",
}
PROTECTED_NAMES = {".env", "docker.env", "bugs-and-ideas.md", "Projekte_2026_27.xlsx"}


def synthetic_archive_discovery(root: Path) -> tuple[list[tuple[str, str]], dict[str, int]]:
    """Test-only model of the one-time audit's path-first selection rules."""
    candidates: list[tuple[str, str]] = []
    excluded = {"copied_or_generated": 0, "protected": 0, "symlink": 0}
    for current, directories, filenames in os.walk(root, followlinks=False):
        current_path = Path(current)
        relative = current_path.relative_to(root)
        kept_directories = []
        for name in directories:
            if (current_path / name).is_symlink():
                excluded["symlink"] += 1
            else:
                kept_directories.append(name)
        directories[:] = sorted(kept_directories)
        in_evidence = "evidence" in relative.parts
        if not in_evidence:
            continue
        excluded_tree = bool(set(relative.parts) & COPIED_OR_GENERATED)
        for name in sorted(filenames):
            path = current_path / name
            if path.is_symlink():
                excluded["symlink"] += 1
                continue
            if name in PROTECTED_NAMES:
                excluded["protected"] += 1
                continue
            suffix = path.suffix.lower()
            candidate = suffix in SCRIPT_SUFFIXES
            if excluded_tree:
                if candidate:
                    excluded["copied_or_generated"] += 1
                continue
            if not candidate and not suffix:
                try:
                    with path.open("rb") as stream:
                        candidate = stream.read(256).startswith(b"#!")
                except OSError:
                    candidate = False
            if candidate:
                digest = hashlib.sha256(path.read_bytes()).hexdigest()
                candidates.append((path.relative_to(root).as_posix(), digest))
    return candidates, excluded


def write_xml(path: Path, content: str) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    return path


def write_json(path: Path, value: str) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(value, encoding="utf-8")
    return path


class VerifyCliTests(unittest.TestCase):
    def run_cli(self, *arguments: str, cwd: Path | None = None) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(SCRIPT), *arguments],
            cwd=cwd,
            capture_output=True,
            text=True,
            check=False,
        )

    def test_help_is_available_for_cli_and_each_subcommand(self) -> None:
        for arguments in (("--help",), ("junit-summary", "--help"), ("json-compare", "--help")):
            with self.subTest(arguments=arguments):
                result = self.run_cli(*arguments)
                self.assertEqual(result.returncode, 0)
                self.assertIn("usage:", result.stdout)
                self.assertEqual(result.stderr, "")

    def test_import_has_no_repository_inspection_or_filesystem_side_effects(self) -> None:
        code = """
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
from unittest import mock
import argparse
import json
import stat
import typing
import xml.etree.ElementTree
script = Path(sys.argv[1])
sys.dont_write_bytecode = True
before = set(Path.cwd().iterdir())
spec = importlib.util.spec_from_file_location("verify_import_probe", script)
module = importlib.util.module_from_spec(spec)
with mock.patch("pathlib.Path.cwd", side_effect=AssertionError("cwd inspected")), \\
     mock.patch("os.scandir", side_effect=AssertionError("repository scanned")), \\
     mock.patch("subprocess.Popen", side_effect=AssertionError("command executed")), \\
     mock.patch("builtins.open", side_effect=AssertionError("data file opened")):
    spec.loader.exec_module(module)
assert set(Path.cwd().iterdir()) == before
"""
        with tempfile.TemporaryDirectory(prefix="verify-import-isolation ") as temporary:
            result = subprocess.run(
                [sys.executable, "-c", code, str(SCRIPT)],
                cwd=temporary,
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(list(Path(temporary).iterdir()), [])

    def test_junit_accepts_two_run_like_directories_from_another_cwd(self) -> None:
        with tempfile.TemporaryDirectory(prefix="verification example with spaces ") as temporary:
            outside = Path(temporary)
            first = write_xml(
                outside / "run alpha" / "nested evidence" / "TEST-first.xml",
                '<testsuites tests="2" failures="0" errors="0" skipped="0">'
                '<testsuite tests="2" failures="0" errors="0" skipped="0">'
                '<testcase classname="First" name="one"/>'
                '<testcase classname="First" name="two"/>'
                '</testsuite></testsuites>',
            )
            second = write_xml(
                outside / "run beta" / "TEST-second.xml",
                '<testsuite tests="1" failures="0" errors="0" skipped="0">'
                '<testcase classname="Second" name="different"/>'
                '</testsuite>',
            )
            first_before = hashlib.sha256(first.read_bytes()).hexdigest()
            second_before = hashlib.sha256(second.read_bytes()).hexdigest()

            first_result = self.run_cli(
                "junit-summary", "--expect-tests", "2", str(first), cwd=outside
            )
            second_result = self.run_cli(
                "junit-summary", "--expect-tests", "1", str(second), cwd=outside
            )

            self.assertEqual(first_result.returncode, 0, first_result.stderr)
            self.assertEqual(second_result.returncode, 0, second_result.stderr)
            self.assertEqual(json.loads(first_result.stdout)["totals"]["tests"], 2)
            self.assertEqual(json.loads(second_result.stdout)["totals"]["tests"], 1)
            self.assertEqual(hashlib.sha256(first.read_bytes()).hexdigest(), first_before)
            self.assertEqual(hashlib.sha256(second.read_bytes()).hexdigest(), second_before)
            self.assertEqual(sorted(path.name for path in first.parent.iterdir()), ["TEST-first.xml"])
            self.assertEqual(sorted(path.name for path in second.parent.iterdir()), ["TEST-second.xml"])

    def test_junit_counts_nested_results_and_honors_each_expectation(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            report = write_xml(
                Path(temporary) / "nested.xml",
                '<testsuites tests="3" failures="1" errors="1" skipped="1">'
                '<testsuite tests="2" failures="1" errors="0" skipped="1">'
                '<testcase classname="A" name="failed"><failure/></testcase>'
                '<testcase classname="A" name="skipped"><skipped/></testcase>'
                '</testsuite>'
                '<testsuite tests="1" failures="0" errors="1" skipped="0">'
                '<testcase classname="B" name="errored"><error/></testcase>'
                '</testsuite></testsuites>',
            )
            result = self.run_cli(
                "junit-summary", "--expect-tests", "3", "--expect-failures", "1",
                "--expect-errors", "1", "--expect-skipped", "1", str(report),
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(
                json.loads(result.stdout)["totals"],
                {"tests": 3, "failures": 1, "errors": 1, "skipped": 1},
            )

    def test_junit_default_rejects_nonpassing_report_and_wrong_expectation(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            report = write_xml(
                Path(temporary) / "failed.xml",
                '<testsuite tests="1" failures="1" errors="0" skipped="0">'
                '<testcase name="known failure"><failure/></testcase></testsuite>',
            )
            default = self.run_cli("junit-summary", str(report))
            wrong_expectation = self.run_cli(
                "junit-summary", "--expect-tests", "4", str(report)
            )
            self.assertEqual(default.returncode, 1)
            self.assertEqual(wrong_expectation.returncode, 1)
            self.assertIn("caller expectations", default.stderr)
            self.assertEqual(json.loads(default.stdout)["totals"]["failures"], 1)

    def test_junit_accepts_an_explicit_empty_suite_and_rejects_bad_inputs(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            empty = write_xml(
                root / "empty.xml",
                '<testsuite tests="0" failures="0" errors="0" skipped="0"/>',
            )
            malformed = root / "malformed.xml"
            malformed.write_text("<testsuite><testcase>", encoding="utf-8")
            wrong_root = write_xml(root / "wrong-root.xml", "<project/>")
            inconsistent = write_xml(
                root / "inconsistent.xml",
                '<testsuite tests="2"><testcase name="only"/></testsuite>',
            )
            accepted = self.run_cli(
                "junit-summary", "--expect-tests", "0", str(empty)
            )
            missing = self.run_cli("junit-summary", str(root / "missing.xml"))
            bad_xml = self.run_cli("junit-summary", str(malformed))
            bad_root = self.run_cli("junit-summary", str(wrong_root))
            bad_counter = self.run_cli("junit-summary", str(inconsistent))
            no_input = self.run_cli("junit-summary")
            self.assertEqual(accepted.returncode, 0, accepted.stderr)
            self.assertEqual(json.loads(accepted.stdout)["totals"]["tests"], 0)
            self.assertEqual(missing.returncode, 2)
            self.assertEqual(bad_xml.returncode, 2)
            self.assertEqual(bad_root.returncode, 2)
            self.assertEqual(bad_counter.returncode, 2)
            self.assertEqual(no_input.returncode, 2)
            self.assertNotIn("Traceback", bad_xml.stderr)

    def test_junit_rejects_symlink_before_reading_its_target(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            target = write_xml(root / "external.xml", "<testsuite/>")
            link = root / "alias.xml"
            link.symlink_to(target)
            with mock.patch.object(Path, "read_bytes") as read_bytes:
                with self.assertRaisesRegex(verify.InputError, "regular, non-symlink"):
                    verify._parse_junit(str(link))
                read_bytes.assert_not_called()
            result = self.run_cli("junit-summary", str(link))
            self.assertEqual(result.returncode, 2)
            self.assertIn("regular, non-symlink", result.stderr)

    def test_json_compare_ignores_object_order_and_preserves_array_order(self) -> None:
        with tempfile.TemporaryDirectory(prefix="json compare from other cwd ") as temporary:
            outside = Path(temporary)
            root = outside / "run alpha with spaces"
            left = write_json(root / "before document.json", '{"z":1,"nested":{"a":true,"b":[1,2]}}')
            reordered = write_json(
                root / "same document reordered.json",
                '{"nested":{"b":[1,2],"a":true},"z":1}',
            )
            changed = write_json(
                root / "changed array order.json",
                '{"z":1,"nested":{"a":true,"b":[2,1]}}',
            )
            left_hash = hashlib.sha256(left.read_bytes()).hexdigest()
            reordered_hash = hashlib.sha256(reordered.read_bytes()).hexdigest()
            changed_hash = hashlib.sha256(changed.read_bytes()).hexdigest()
            equal = self.run_cli(
                "json-compare", str(left), str(reordered), "--expect", "equal", cwd=outside
            )
            unequal = self.run_cli(
                "json-compare", str(left), str(changed), "--expect", "different", cwd=outside
            )
            self.assertEqual(equal.returncode, 0, equal.stderr)
            self.assertTrue(json.loads(equal.stdout)["equal"])
            self.assertEqual(unequal.returncode, 0, unequal.stderr)
            result = json.loads(unequal.stdout)
            self.assertFalse(result["equal"])
            self.assertEqual(
                result["differences"],
                [{"path": "/nested/b/0", "kind": "changed"},
                 {"path": "/nested/b/1", "kind": "changed"}],
            )
            self.assertEqual(hashlib.sha256(left.read_bytes()).hexdigest(), left_hash)
            self.assertEqual(hashlib.sha256(reordered.read_bytes()).hexdigest(), reordered_hash)
            self.assertEqual(hashlib.sha256(changed.read_bytes()).hexdigest(), changed_hash)
            self.assertEqual(
                {path.name for path in root.iterdir()},
                {"before document.json", "same document reordered.json", "changed array order.json"},
            )

    def test_json_compare_reports_exact_structure_without_values(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            marker = "SYNTHETIC_SECRET_MARKER_MUST_NOT_BE_PRINTED"
            left = write_json(root / "left.json", json.dumps({"a/b": marker, "same": 1}))
            right = write_json(root / "right.json", '{"same":true,"added":null}')
            result = self.run_cli("json-compare", str(left), str(right), "--expect", "different")
            self.assertEqual(result.returncode, 0, result.stderr)
            document = json.loads(result.stdout)
            self.assertEqual(
                document["differences"],
                [{"path": "/a~1b", "kind": "removed"},
                 {"path": "/added", "kind": "added"},
                 {"path": "/same", "kind": "changed"}],
            )
            self.assertNotIn(marker, result.stdout + result.stderr)

    def test_json_compare_distinguishes_boolean_from_number_and_enforces_expectation(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            left = write_json(root / "left.json", '{"value":true}')
            right = write_json(root / "right.json", '{"value":1}')
            mismatch = self.run_cli("json-compare", str(left), str(right), "--expect", "equal")
            expected_difference = self.run_cli(
                "json-compare", str(left), str(right), "--expect", "different"
            )
            self.assertEqual(mismatch.returncode, 1)
            self.assertEqual(expected_difference.returncode, 0)
            self.assertEqual(
                json.loads(expected_difference.stdout)["differences"],
                [{"path": "/value", "kind": "changed"}],
            )

    def test_json_compare_rejects_missing_malformed_duplicate_and_nonstandard_values(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            valid = write_json(root / "valid.json", '{"ok":true}')
            marker = "SYNTHETIC_SECRET_MARKER"
            malformed = write_json(root / "malformed.json", f'{{"value":"{marker}"')
            empty_json = write_json(root / "empty.json", "")
            duplicate = write_json(root / "duplicate.json", '{"x":1,"x":2}')
            nonstandard = write_json(root / "nonstandard.json", '{"x":NaN}')
            results = (
                self.run_cli("json-compare", str(root / "missing"), str(valid), "--expect", "equal"),
                self.run_cli("json-compare", str(malformed), str(valid), "--expect", "equal"),
                self.run_cli("json-compare", str(empty_json), str(valid), "--expect", "equal"),
                self.run_cli("json-compare", str(duplicate), str(valid), "--expect", "equal"),
                self.run_cli("json-compare", str(nonstandard), str(valid), "--expect", "equal"),
            )
            self.assertTrue(all(result.returncode == 2 for result in results))
            self.assertTrue(all("Traceback" not in result.stderr for result in results))
            self.assertTrue(all(marker not in result.stdout + result.stderr for result in results))
            self.assertIn("duplicate object key", results[3].stderr)
            self.assertIn("non-standard numeric constant", results[4].stderr)
            self.assertEqual(
                {path.name for path in root.iterdir()},
                {"valid.json", "malformed.json", "empty.json", "duplicate.json", "nonstandard.json"},
            )

    def test_archive_discovery_rules_on_nested_synthetic_tree(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            first = root / "run-a" / "evidence" / "tools" / "reusable.py"
            second = root / "run-b" / "evidence" / "tools" / "reusable.py"
            nested = root / "run-a" / "evidence" / "nested" / "evidence" / "check.py"
            extensionless = root / "run-b" / "evidence" / "scripts" / "probe"
            write_json(first, 'print("synthetic only")\n')
            write_json(second, 'print("synthetic only")\n')
            write_json(nested, 'print("nested synthetic only")\n')
            extensionless.parent.mkdir(parents=True)
            extensionless.write_text("#!/usr/bin/env python3\npass\n", encoding="utf-8")
            extensionless.chmod(0o755)
            for name in ("baseline", "vendor", "generated"):
                copied = root / "run-b" / "evidence" / name / "source.py"
                write_json(copied, "copied input\n")

            protected = root / "run-a" / "evidence" / ".env"
            protected.parent.mkdir(parents=True, exist_ok=True)
            protected.write_text("SYNTHETIC_PRIVATE_SENTINEL\n", encoding="utf-8")
            protected_policy = protected.with_name("bugs-and-ideas.md")
            protected_policy.write_text("SYNTHETIC_POLICY_SENTINEL\n", encoding="utf-8")
            outside = root / "outside"
            outside.mkdir()
            outside_script = write_json(outside / "outside.py", "not in archive\n")
            link = root / "run-a" / "evidence" / "external"
            link.symlink_to(outside, target_is_directory=True)

            real_open = builtins.open
            real_path_open = Path.open
            protected_paths = {protected, protected_policy}

            def guarded_open(file: object, *args: object, **kwargs: object):
                if Path(file).resolve() in protected_paths:
                    raise AssertionError("protected synthetic file was opened")
                return real_open(file, *args, **kwargs)

            def guarded_path_open(path: Path, *args: object, **kwargs: object):
                if path.resolve() in protected_paths:
                    raise AssertionError("protected synthetic file was opened")
                return real_path_open(path, *args, **kwargs)

            with mock.patch.object(Path, "open", guarded_path_open), mock.patch(
                "builtins.open", guarded_open
            ):
                candidates, excluded = synthetic_archive_discovery(root)

            names = [name for name, _ in candidates]
            self.assertEqual(len(candidates), 4)
            self.assertIn("run-a/evidence/nested/evidence/check.py", names)
            self.assertIn("run-b/evidence/scripts/probe", names)
            self.assertNotIn("run-a/evidence/external/outside.py", names)
            self.assertNotIn(outside_script.relative_to(root).as_posix(), names)
            self.assertEqual(excluded["copied_or_generated"], 3)
            self.assertEqual(excluded["protected"], 2)
            self.assertEqual(excluded["symlink"], 1)
            hashes = {}
            for name, digest in candidates:
                hashes.setdefault(digest, []).append(name)
            duplicate_families = [members for members in hashes.values() if len(members) > 1]
            self.assertEqual(len(duplicate_families), 1)
            self.assertEqual(len(duplicate_families[0]), 2)
            dispositions = {name: "retain" for name in names}
            totals = {
                disposition: sum(value == disposition for value in dispositions.values())
                for disposition in {"promote", "retain"}
            }
            self.assertEqual(sum(totals.values()), len(candidates))


if __name__ == "__main__":
    unittest.main()
