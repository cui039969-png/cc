#!/usr/bin/env python3
"""Local-only publication hygiene check. Never prints matched secret values or uploads data.

Scans working publishable files, the Git index, and reachable local Git history blobs.
Ignored local files are deliberately not opened. A shallow clone is reported explicitly.
This detects recognizable mistakes; it cannot classify arbitrary prose as personal data.
"""
from __future__ import annotations

import argparse
import json
import pathlib
import re
import subprocess
import sys


ROOT = pathlib.Path(__file__).resolve().parents[1]
SECRET_PATTERNS = (
    ("private-key", re.compile(rb"-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----")),
    ("github-token", re.compile(rb"\bgh[pousr]_[A-Za-z0-9]{20,}\b|\bgithub_pat_[A-Za-z0-9_]{20,}\b")),
    ("cloud-access-key", re.compile(rb"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b")),
    ("openai-token", re.compile(rb"\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{20,}\b")),
    ("slack-token", re.compile(rb"\bxox[baprs]-[A-Za-z0-9-]{20,}\b")),
    ("google-api-key", re.compile(rb"\bAIza[A-Za-z0-9_-]{30,}\b")),
    ("credential-assignment", re.compile(
        rb"(?i)\b(?:storePassword|keyPassword|api[_-]?key|access[_-]?token|client[_-]?secret)"
        rb"\s*[:=]\s*[\"'](?!YOUR_|your_|example|placeholder|REPLACE|<|\$)[A-Za-z0-9_+/.=-]{16,}[\"']"
    )),
)
PRIVATE_SUFFIXES = (
    ".db", ".db-wal", ".db-shm", ".db-journal", ".sqlite", ".sqlite-wal", ".sqlite-shm", ".sqlite-journal",
    ".sqlite3", ".sqlite3-wal", ".sqlite3-shm", ".sqlite3-journal", ".log", ".jks", ".keystore", ".p12", ".pfx",
)
PRIVATE_DIRS = {"private", "personal-data", "backups", "device-logs"}


def git(*arguments: str) -> bytes:
    result = subprocess.run(["git", "-C", str(ROOT), *arguments], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise RuntimeError("Git command failed: " + arguments[0])
    return result.stdout


def forbidden_path(name: str) -> bool:
    path = pathlib.PurePosixPath(name.replace("\\", "/"))
    lower = path.name.lower()
    return (
        lower == "local.properties" or lower == ".env" or lower.startswith(".env.")
        or lower.endswith(PRIVATE_SUFFIXES)
        or bool({part.lower() for part in path.parts} & PRIVATE_DIRS)
        or (lower.endswith(".json") and any(word in lower for word in ("private-backup", "personal-backup", "daily3minutes-backup-")))
        or bool(re.fullmatch(r"daily-3-minutes-[0-9]{4}-[0-9]{2}-[0-9]{2}\.json", lower))
    )


def inspect_content(data: bytes) -> list[str]:
    return [label for label, pattern in SECRET_PATTERNS if pattern.search(data)]


def is_unapproved_backup(name: str, data: bytes) -> bool:
    """Real app exports have no reason to be in publishable source outside explicit synthetic fixtures."""
    path = pathlib.PurePosixPath(name.partition(":")[2] if ":" in name else name)
    if path.suffix.lower() != ".json":
        return False
    if "synthetic" in path.name.lower() and {part.lower() for part in path.parts} & {"test", "androidtest", "fixtures", "examples", "resources"}:
        return False
    try:
        value = json.loads(data)
    except (ValueError, UnicodeDecodeError, RecursionError):
        return False
    return (
        isinstance(value, dict) and "schemaVersion" in value and isinstance(value.get("records"), list)
        and any(isinstance(record, dict) and {"date", "learned", "unclear", "nextPriority"} <= record.keys() for record in value["records"])
    )


def self_test() -> None:
    assert forbidden_path("nested/local.properties")
    assert forbidden_path("app/private/records.json")
    assert forbidden_path("sample.db-wal")
    assert forbidden_path("sample.sqlite-wal")
    assert forbidden_path("sample.sqlite3-shm")
    assert forbidden_path("device.log")
    assert forbidden_path("release.keystore")
    assert forbidden_path("daily-3-minutes-2026-10-24.json")
    assert not forbidden_path("app/schemas/io.github.daily3minutes.data.DailyDatabase/1.json")
    assert not forbidden_path("app/src/test/resources/synthetic-backup.json")
    # Construct a synthetic marker in memory; no secret-shaped fixture is written into Git.
    fake = ("gh" + "p_" + "A" * 30).encode("ascii")
    assert "github-token" in inspect_content(fake)
    assert not inspect_content(b"synthetic learning example without credentials")
    fake_backup = json.dumps({"schemaVersion": 1, "records": [{"date": "2026-10-24", "learned": "synthetic", "unclear": "", "nextPriority": ""}]}).encode()
    assert is_unapproved_backup("arbitrary.json", fake_backup)
    assert not is_unapproved_backup("app/src/test/resources/synthetic-backup.json", fake_backup)
    print("PASS: 14 synthetic scanner assertions")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true", help="Run synthetic in-memory scanner assertions first")
    args = parser.parse_args()
    if args.self_test:
        self_test()
    findings: set[tuple[str, str]] = set()
    files = {
        name.decode("utf-8", "surrogateescape")
        for name in git("ls-files", "--cached", "--others", "--exclude-standard", "-z").split(b"\0") if name
    }
    current_count = 0
    for name in sorted(files):
        if forbidden_path(name):
            findings.add(("publishable-private-artifact", name))
        path = ROOT / name
        if path.is_symlink():
            findings.add(("publishable-symlink-needs-review", name))
            continue
        if path.is_file():
            current_count += 1
            content = path.read_bytes()
            if is_unapproved_backup(name, content):
                findings.add(("unapproved-record-backup", name))
            for label in inspect_content(content):
                findings.add((label, name))
    ignore = ROOT / ".gitignore"
    if ignore.exists():
        for line in ignore.read_text(encoding="utf-8").splitlines():
            pattern = line.strip().lower().removeprefix("/")
            if pattern in {"*.json", "**/*.json", "**.json"}:
                findings.add(("blanket-json-ignore", ".gitignore"))
    # Include index blobs so an unstaged edit cannot hide sensitive staged content.
    objects: dict[str, str] = {}
    for entry in git("ls-files", "--stage", "-z").split(b"\0"):
        if not entry:
            continue
        metadata, raw_name = entry.split(b"\t", 1)
        name = raw_name.decode("utf-8", "surrogateescape")
        sha = metadata.split()[1].decode("ascii")
        objects.setdefault(sha, "index:" + name)
    # rev-list sees only the repository's local reachable history, never fetches a remote.
    for entry in git("rev-list", "--objects", "--all").splitlines():
        sha_bytes, _, raw_name = entry.partition(b" ")
        if not raw_name:
            continue
        name = raw_name.decode("utf-8", "surrogateescape")
        sha = sha_bytes.decode("ascii")
        if forbidden_path(name):
            findings.add(("history-private-artifact", "history:" + name))
        objects.setdefault(sha, "history:" + name)
    blob_count = 0
    for sha, name in objects.items():
        if git("cat-file", "-t", sha).strip() != b"blob":
            continue
        blob_count += 1
        content = git("cat-file", "blob", sha)
        if is_unapproved_backup(name, content):
            findings.add(("unapproved-record-backup", name))
        for label in inspect_content(content):
            findings.add((label, name))
    shallow = git("rev-parse", "--is-shallow-repository").strip() == b"true"
    commits = int(git("rev-list", "--all", "--count").strip())
    for label, name in sorted(findings):
        print(f"FAIL: {label}: {name}")
    print(f"Scanned {current_count} working files; {blob_count} distinct index/history blobs; {commits} local reachable commits.")
    if shallow:
        print("LIMITATION: shallow repository; earlier upstream history is unavailable and was not audited.")
    print("LIMITATION: recognizable secret patterns cannot determine whether arbitrary prose contains personal records.")
    if findings:
        print(f"FAIL: {len(findings)} issue(s); matched values are intentionally omitted.")
        return 1
    print("PASS: no recognized secrets, private artifact paths, or blanket JSON ignore found in the audited scope.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError) as error:
        print(f"ERROR: scanner could not complete ({type(error).__name__}); no content emitted.")
        raise SystemExit(2)
