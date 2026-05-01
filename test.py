#!/usr/bin/env python3
"""
Minimal local Forge launcher for quick-playing the test world.
"""

from __future__ import annotations

import argparse
import glob
import io
import json
import os
import shutil
import subprocess
import sys
import uuid
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

PROJECT_ROOT = Path(__file__).resolve().parent
DOT_MINECRAFT = PROJECT_ROOT / ".minecraft"
VERSIONS_DIR = DOT_MINECRAFT / "versions"
LIBRARIES_DIR = DOT_MINECRAFT / "libraries"
ASSETS_DIR = DOT_MINECRAFT / "assets"
MODS_DIR = DOT_MINECRAFT / "mods"
SAVES_DIR = DOT_MINECRAFT / "saves"

DEFAULT_INSTANCE = "1.20.1-Forge"
DEFAULT_WIDTH = 1600
DEFAULT_HEIGHT = 900


def read_gradle_properties() -> dict[str, str]:
    props: dict[str, str] = {}
    for line in (PROJECT_ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return props


def find_java() -> str:
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = Path(java_home) / "bin" / "java.exe"
        if candidate.exists():
            return str(candidate)
    return "java"


def maven_to_path(coords: str) -> Path | None:
    parts = coords.split(":")
    if len(parts) < 3:
        return None
    group, artifact, version = parts[:3]
    return Path(group.replace(".", "/")) / artifact / version / f"{artifact}-{version}.jar"


def os_rule_allows(rules: list[dict]) -> bool:
    if not rules:
        return True

    result = False
    for rule in rules:
        action = rule.get("action") == "allow"
        os_info = rule.get("os", {})
        if rule.get("features"):
            continue
        if not os_info:
            result = action
        elif os_info.get("name") == "windows":
            result = action
        elif os_info.get("name") in ("osx", "linux"):
            result = not action
    return result


def feature_rule_allows(rules: list[dict], features: dict[str, bool]) -> bool:
    if not rules:
        return True

    result = False
    for rule in rules:
        action = rule.get("action") == "allow"
        required = rule.get("features", {})
        if not required or all(bool(features.get(k, False)) == bool(v) for k, v in required.items()):
            result = action
    return result


def resolve_version_json(instance: str) -> Path:
    json_path = VERSIONS_DIR / instance / f"{instance}.json"
    if not json_path.exists():
        raise FileNotFoundError(f"Version json not found: {json_path}")
    return json_path


def build_classpath(version_data: dict, instance_dir: Path) -> list[str]:
    entries: list[str] = []
    for library in version_data.get("libraries", []):
        if not os_rule_allows(library.get("rules", [])):
            continue

        artifact = library.get("downloads", {}).get("artifact", {})
        if artifact.get("path"):
            full_path = LIBRARIES_DIR / Path(artifact["path"])
        else:
            maven_path = maven_to_path(library.get("name", ""))
            if maven_path is None:
                continue
            full_path = LIBRARIES_DIR / maven_path

        if full_path.exists():
            entries.append(str(full_path))

    client_jar = instance_dir / f"{version_data['id']}.jar"
    if client_jar.exists():
        entries.append(str(client_jar))
    return entries


def build_jvm_args(version_data: dict, instance_dir: Path, classpath: str) -> list[str]:
    version_id = version_data["id"]
    replacements = {
        "${natives_directory}": str(instance_dir / "natives-windows-x86_64"),
        "${launcher_name}": "create-biotech-test",
        "${launcher_version}": "1.0",
        "${classpath}": classpath,
        "${library_directory}": str(LIBRARIES_DIR),
        "${classpath_separator}": os.pathsep,
        "${version_name}": version_id,
        "${primary_jar_name}": f"{version_id}.jar",
    }

    args = ["-Xmx4G", "-Xms512M"]
    for entry in version_data.get("arguments", {}).get("jvm", []):
        if isinstance(entry, str):
            args.append(replace_tokens(entry, replacements))
        elif isinstance(entry, dict) and os_rule_allows(entry.get("rules", [])):
            value = entry.get("value", [])
            for item in value if isinstance(value, list) else [value]:
                args.append(replace_tokens(item, replacements))

    log4j_config = instance_dir / "log4j2.xml"
    if log4j_config.exists():
        args.append(f"-Dlog4j.configurationFile={log4j_config}")
    return args


def build_game_args(version_data: dict, world: str, width: int, height: int) -> list[str]:
    asset_index = version_data.get("assetIndex", {}).get("id", version_data.get("assets", "5"))
    replacements = {
        "${auth_player_name}": "Dev",
        "${version_name}": version_data["id"],
        "${game_directory}": str(DOT_MINECRAFT),
        "${assets_root}": str(ASSETS_DIR),
        "${assets_index_name}": str(asset_index),
        "${auth_uuid}": str(uuid.uuid4()).replace("-", ""),
        "${auth_access_token}": "0",
        "${clientid}": "0",
        "${auth_xuid}": "0",
        "${user_type}": "legacy",
        "${version_type}": "release",
        "${resolution_width}": str(width),
        "${resolution_height}": str(height),
        "${quickPlayPath}": "quickPlay/create_biotech.json",
        "${quickPlaySingleplayer}": world,
    }
    features = {
        "has_custom_resolution": True,
        "has_quick_plays_support": True,
        "is_quick_play_singleplayer": True,
        "is_quick_play_multiplayer": False,
        "is_quick_play_realms": False,
    }

    args: list[str] = []
    for entry in version_data.get("arguments", {}).get("game", []):
        if isinstance(entry, str):
            args.append(replace_tokens(entry, replacements))
        elif isinstance(entry, dict) and feature_rule_allows(entry.get("rules", []), features):
            value = entry.get("value", [])
            for item in value if isinstance(value, list) else [value]:
                args.append(replace_tokens(item, replacements))
    return args


def replace_tokens(value: str, replacements: dict[str, str]) -> str:
    for token, replacement in replacements.items():
        value = value.replace(token, replacement)
    return value


def newest_world() -> str:
    worlds = [path for path in SAVES_DIR.glob("*") if path.is_dir() and (path / "level.dat").exists()]
    if not worlds:
        raise FileNotFoundError(f"No worlds found under {SAVES_DIR}")
    return max(worlds, key=lambda path: (path / "level.dat").stat().st_mtime).name


def run_build() -> None:
    env = os.environ.copy()
    env.setdefault("GRADLE_USER_HOME", str(PROJECT_ROOT / ".gradle-user"))
    subprocess.run([str(PROJECT_ROOT / "gradlew.bat"), "build"], cwd=PROJECT_ROOT, env=env, check=True)


def copy_mod_jar() -> Path:
    props = read_gradle_properties()
    mod_id = props.get("mod_id", "create_biotech")
    pattern = str(PROJECT_ROOT / "build" / "libs" / f"{mod_id}-*.jar")
    candidates = [Path(path) for path in glob.glob(pattern) if not path.endswith("-sources.jar")]
    if not candidates:
        raise FileNotFoundError("Built mod jar not found in build/libs")

    MODS_DIR.mkdir(exist_ok=True)
    jar_path = max(candidates, key=lambda path: path.stat().st_mtime)
    destination = MODS_DIR / jar_path.name
    for existing in MODS_DIR.glob(f"{mod_id}-*.jar"):
        if existing != destination:
            try:
                existing.unlink()
            except PermissionError:
                print(f"[WARN] Could not remove locked mod jar: {existing}")
    try:
        shutil.copy2(jar_path, destination)
    except PermissionError:
        if destination.exists():
            print(f"[WARN] Reusing locked mod jar: {destination}")
        else:
            raise
    return destination


def build_launch_command(instance: str, world: str, width: int, height: int) -> list[str]:
    version_json = resolve_version_json(instance)
    instance_dir = version_json.parent
    version_data = json.loads(version_json.read_text(encoding="utf-8"))
    classpath = os.pathsep.join(build_classpath(version_data, instance_dir))
    return [
        find_java(),
        *build_jvm_args(version_data, instance_dir, classpath),
        version_data["mainClass"],
        *build_game_args(version_data, world, width, height),
    ]


def launch(instance: str, world: str, width: int, height: int) -> None:
    command = build_launch_command(instance, world, width, height)
    process = subprocess.Popen(
        command,
        cwd=DOT_MINECRAFT,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        creationflags=getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0) if os.name == "nt" else 0,
    )
    print(f"[LAUNCH] {instance}")
    print(f"  world: {world}")
    print(f"  pid: {process.pid}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Build, copy, and quickplay the local Forge test instance.")
    parser.add_argument("--instance", default=DEFAULT_INSTANCE)
    parser.add_argument("--world", help="Save folder name under .minecraft/saves. Defaults to the newest world.")
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--no-copy", action="store_true")
    parser.add_argument("--width", type=int, default=DEFAULT_WIDTH)
    parser.add_argument("--height", type=int, default=DEFAULT_HEIGHT)
    args = parser.parse_args()

    world = args.world or newest_world()
    if not (SAVES_DIR / world / "level.dat").exists():
        raise FileNotFoundError(f"World not found: {SAVES_DIR / world}")

    if not args.skip_build:
        print("[BUILD] gradlew build")
        run_build()

    if not args.no_copy:
        print(f"[COPY] {copy_mod_jar()}")

    launch(args.instance, world, args.width, args.height)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
