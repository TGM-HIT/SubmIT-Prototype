#!/usr/bin/env python3
"""Small, dependency-free verification helpers for evidence and structured data."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import stat
import sys
import xml.etree.ElementTree as ET
from typing import Any


class InputError(Exception):
    """An expected input or validation failure suitable for a concise diagnostic."""


def _read_regular_file(value: str) -> bytes:
    path = Path(value)
    try:
        mode = path.lstat().st_mode
    except OSError as error:
        if isinstance(error, FileNotFoundError):
            raise InputError(f"input file not found: {path}") from None
        raise InputError(f"unable to inspect input file: {path}") from None
    if not stat.S_ISREG(mode):
        raise InputError(f"input must be a regular, non-symlink file: {path}")
    try:
        return path.read_bytes()
    except OSError:
        raise InputError(f"unable to read input file: {path}") from None


def _json_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise InputError("JSON input contains a duplicate object key")
        result[key] = value
    return result


def _reject_json_constant(_: str) -> None:
    raise InputError("JSON input contains a non-standard numeric constant")


def _parse_json(path: str) -> Any:
    data = _read_regular_file(path)
    try:
        text = data.decode("utf-8")
        return json.loads(
            text,
            object_pairs_hook=_json_object,
            parse_constant=_reject_json_constant,
        )
    except InputError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError):
        raise InputError(f"input is not valid UTF-8 JSON: {path}") from None


def _xml_local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _case_counts(cases: list[ET.Element]) -> dict[str, int]:
    counts = {"tests": len(cases), "failures": 0, "errors": 0, "skipped": 0}
    for case in cases:
        case_tags = {_xml_local_name(node.tag) for node in case.iter()}
        for result in ("failures", "errors", "skipped"):
            marker = {"failures": "failure", "errors": "error", "skipped": "skipped"}[result]
            counts[result] += marker in case_tags
    return counts


def _validate_report_counters(root: ET.Element, path: str) -> None:
    for suite in root.iter():
        if _xml_local_name(suite.tag) not in {"testsuite", "testsuites"}:
            continue
        expected = _case_counts(
            [node for node in suite.iter() if _xml_local_name(node.tag) == "testcase"]
        )
        for key, actual in expected.items():
            if key not in suite.attrib:
                continue
            try:
                declared = int(suite.attrib[key])
            except ValueError:
                raise InputError(f"malformed {key} counter in XML report: {path}") from None
            if declared < 0 or declared != actual:
                raise InputError(f"inconsistent {key} counter in XML report: {path}")


def _parse_junit(path: str) -> dict[str, Any]:
    data = _read_regular_file(path)
    if b"<!DOCTYPE" in data.upper() or b"<!ENTITY" in data.upper():
        raise InputError(f"XML DTD and entity declarations are not supported: {path}")
    try:
        root = ET.fromstring(data)
    except ET.ParseError:
        raise InputError(f"input is not well-formed XML: {path}") from None
    if _xml_local_name(root.tag) not in {"testsuite", "testsuites"}:
        raise InputError(f"XML report root must be testsuite or testsuites: {path}")
    cases = [node for node in root.iter() if _xml_local_name(node.tag) == "testcase"]
    _validate_report_counters(root, path)
    return {"path": path, **_case_counts(cases)}


def _junit_summary(arguments: argparse.Namespace) -> int:
    reports = [_parse_junit(path) for path in arguments.reports]
    totals = {
        key: sum(report[key] for report in reports)
        for key in ("tests", "failures", "errors", "skipped")
    }
    expectations = {
        "failures": 0 if arguments.expect_failures is None else arguments.expect_failures,
        "errors": 0 if arguments.expect_errors is None else arguments.expect_errors,
        "skipped": 0 if arguments.expect_skipped is None else arguments.expect_skipped,
    }
    if arguments.expect_tests is not None:
        expectations["tests"] = arguments.expect_tests
    matches = {
        key: actual == expected
        for key, expected in expectations.items()
        for actual in (totals[key],)
    }
    result = {
        "reports": sorted(reports, key=lambda report: report["path"]),
        "totals": totals,
        "expectations": expectations,
        "expectations_match": all(matches.values()),
    }
    print(json.dumps(result, indent=2, sort_keys=True, ensure_ascii=False))
    if not all(matches.values()):
        print("verification failed: XML totals do not match caller expectations", file=sys.stderr)
        return 1
    return 0


def _escape_pointer_segment(segment: str) -> str:
    return segment.replace("~", "~0").replace("/", "~1")


def _same_json_value(left: Any, right: Any) -> bool:
    if isinstance(left, bool) or isinstance(right, bool):
        return type(left) is type(right) and left == right
    if isinstance(left, (int, float)) and isinstance(right, (int, float)):
        return left == right
    return type(left) is type(right) and left == right


def _json_differences(left: Any, right: Any, pointer: str = "") -> list[dict[str, str]]:
    if isinstance(left, dict) and isinstance(right, dict):
        differences = []
        for key in sorted(left.keys() | right.keys()):
            child = f"{pointer}/{_escape_pointer_segment(key)}"
            if key not in left:
                differences.append({"path": child, "kind": "added"})
            elif key not in right:
                differences.append({"path": child, "kind": "removed"})
            else:
                differences.extend(_json_differences(left[key], right[key], child))
        return differences
    if isinstance(left, list) and isinstance(right, list):
        differences = []
        for index in range(max(len(left), len(right))):
            child = f"{pointer}/{index}"
            if index >= len(left):
                differences.append({"path": child, "kind": "added"})
            elif index >= len(right):
                differences.append({"path": child, "kind": "removed"})
            else:
                differences.extend(_json_differences(left[index], right[index], child))
        return differences
    if _same_json_value(left, right):
        return []
    return [{"path": pointer, "kind": "changed"}]


def _json_compare(arguments: argparse.Namespace) -> int:
    left = _parse_json(arguments.left)
    right = _parse_json(arguments.right)
    differences = _json_differences(left, right)
    equal = not differences
    result = {
        "equal": equal,
        "expected": arguments.expect,
        "expectation_match": (arguments.expect == "equal") == equal,
        "differences": differences,
    }
    print(json.dumps(result, indent=2, sort_keys=True, ensure_ascii=False))
    if not result["expectation_match"]:
        print("verification failed: JSON comparison does not match caller expectation", file=sys.stderr)
        return 1
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Dependency-free, read-only verification of test reports and JSON documents."
    )
    commands = parser.add_subparsers(dest="command", required=True)

    junit = commands.add_parser(
        "junit-summary", help="summarize one or more JUnit XML reports"
    )
    junit.add_argument("reports", nargs="+", metavar="XML")
    junit.add_argument("--expect-tests", type=int)
    junit.add_argument("--expect-failures", type=int)
    junit.add_argument("--expect-errors", type=int)
    junit.add_argument("--expect-skipped", type=int)
    junit.set_defaults(handler=_junit_summary)

    compare = commands.add_parser(
        "json-compare", help="compare JSON structure without printing input values"
    )
    compare.add_argument("left", metavar="LEFT_JSON")
    compare.add_argument("right", metavar="RIGHT_JSON")
    compare.add_argument("--expect", choices=("equal", "different"), required=True)
    compare.set_defaults(handler=_json_compare)
    return parser


def main(argv: list[str] | None = None) -> int:
    arguments = build_parser().parse_args(argv)
    try:
        return arguments.handler(arguments)
    except InputError as error:
        print(f"verification error: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
