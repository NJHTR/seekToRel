from __future__ import annotations

import hashlib
import argparse
import json
from pathlib import Path

from packaging.markers import default_environment
from packaging.requirements import Requirement
from packaging.utils import canonicalize_name, parse_wheel_filename


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dependency-report", type=Path)
    parser.add_argument("--catalog-graph", type=Path, default=Path("catalog-dependencies.json"))
    args = parser.parse_args()
    wheelhouse = Path("wheelhouse")
    wheels = sorted(wheelhouse.glob("*.whl"))
    if not wheels:
        raise SystemExit("wheelhouse contains no wheels")

    entries: list[tuple[str, str, str]] = []
    seen: set[tuple[str, str]] = set()
    for wheel in wheels:
        name, version, _, _ = parse_wheel_filename(wheel.name)
        coordinate = (canonicalize_name(name), str(version))
        if coordinate in seen:
            raise SystemExit(f"wheelhouse contains duplicate candidates for {coordinate[0]}=={coordinate[1]}")
        seen.add(coordinate)
        digest = hashlib.sha256(wheel.read_bytes()).hexdigest()
        entries.append((coordinate[0], coordinate[1], digest))

    lines = [f"{name}=={version} --hash=sha256:{digest}\n" for name, version, digest in sorted(entries)]
    Path("requirements.lock").write_text("".join(lines), encoding="utf-8", newline="\n")
    if args.dependency_report:
        write_catalog_graph(args.dependency_report, args.catalog_graph, entries)


def write_catalog_graph(report_path: Path, output_path: Path,
                        wheel_entries: list[tuple[str, str, str]]) -> None:
    if not report_path.is_file():
        raise SystemExit(f"dependency report does not exist: {report_path}")
    report = json.loads(report_path.read_text(encoding="utf-8"))
    install = report.get("install")
    if not isinstance(install, list) or not install:
        raise SystemExit("dependency report contains no install entries")
    by_coordinate = {(name, version): digest for name, version, digest in wheel_entries}
    by_name = {}
    for entry in install:
        metadata = entry.get("metadata") or {}
        name = canonicalize_name(str(metadata.get("name", "")))
        version = str(metadata.get("version", ""))
        if not name or not version:
            raise SystemExit("dependency report entry is missing metadata name/version")
        coordinate = (name, version)
        if coordinate not in by_coordinate:
            raise SystemExit(f"dependency report package is not present in wheelhouse: {name}=={version}")
        if name in by_name and by_name[name]["version"] != version:
            raise SystemExit(f"dependency report selects multiple versions of {name}")
        by_name[name] = {"name": name, "version": version, "sha256": by_coordinate[coordinate],
                         "metadata": metadata}

    environment = default_environment()
    graph = []
    for name in sorted(by_name):
        package = by_name[name]
        required_coordinates = set()
        for raw_requirement in package["metadata"].get("requires_dist") or []:
            requirement = Requirement(raw_requirement)
            if requirement.marker is not None and not requirement.marker.evaluate(environment):
                continue
            dependency_name = canonicalize_name(requirement.name)
            dependency = by_name.get(dependency_name)
            if dependency is None:
                raise SystemExit(f"selected dependency is absent from report: {raw_requirement}")
            if not requirement.specifier.contains(dependency["version"], prereleases=True):
                raise SystemExit(f"selected version does not satisfy {package['name']} requirement {raw_requirement}")
            required_coordinates.add((dependency["name"], dependency["version"]))
        graph.append({
            "name": package["name"],
            "version": package["version"],
            "sha256": package["sha256"],
            "requires": [
                {"name": dependency_name, "version": dependency_version}
                for dependency_name, dependency_version in sorted(required_coordinates)
            ],
        })
    output_path.write_text(json.dumps(graph, ensure_ascii=False, separators=(",", ":")) + "\n",
                           encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
