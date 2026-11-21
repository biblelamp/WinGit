#!/usr/bin/env python3
"""Generates maven-sources.json: the Maven artifacts the Flatpak build needs offline.

Flathub builds without network access, so every jar and pom that Maven downloads must be
listed in the manifest as a file source with its SHA-256. The list is taken from a fresh
local repository after running the same Maven command the manifest runs:

    mvn -B -Dmaven.repo.local=/tmp/wingit-m2 -DskipTests package \
        dependency:copy-dependencies -DincludeScope=runtime
    python3 packaging/flatpak/generate-maven-sources.py /tmp/wingit-m2

Re-run it whenever a dependency or plugin version in the POMs changes.
"""
import hashlib
import json
import sys
from pathlib import Path

CENTRAL = "https://repo.maven.apache.org/maven2/"
OUTPUT = Path(__file__).with_name("maven-sources.json")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 16), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit("usage: generate-maven-sources.py <maven local repository>")
    repo = Path(sys.argv[1])
    sources = []
    for path in sorted(repo.rglob("*")):
        if not path.is_file() or path.suffix not in (".jar", ".pom"):
            continue
        rel = path.relative_to(repo).as_posix()
        if rel.startswith("cz/wingit/"):
            continue  # our own modules, built from this source tree
        sources.append({
            "type": "file",
            "url": CENTRAL + rel,
            "sha256": sha256(path),
            "dest": "maven-repo/" + path.parent.relative_to(repo).as_posix(),
            "dest-filename": path.name,
        })
    OUTPUT.write_text(json.dumps(sources, indent=4) + "\n", encoding="utf-8")
    print(f"{len(sources)} artifacts -> {OUTPUT}")


if __name__ == "__main__":
    main()
