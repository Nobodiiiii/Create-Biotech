#!/usr/bin/env python3
"""
Local NeoForge launcher for quick-playing the 1.21.1 test world.
"""

from __future__ import annotations

import argparse
import glob
import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import sys
import time
import uuid
from dataclasses import dataclass
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

PROJECT_ROOT = Path(__file__).resolve().parent
DOT_MINECRAFT = PROJECT_ROOT / ".minecraft"
VERSIONS_DIR = DOT_MINECRAFT / "versions"
LIBRARIES_DIR = DOT_MINECRAFT / "libraries"
ASSETS_DIR = DOT_MINECRAFT / "assets"

DEFAULT_INSTANCE = "1.21.1-NeoForge"
DEFAULT_USERNAME = "Dev"
DEFAULT_WIDTH = 1600
DEFAULT_HEIGHT = 900
DEFAULT_SMOKE_TIMEOUT = 40
DEFAULT_SMOKE_LOG_LINES = 160
POST_ENTRY_SETTLE_SECONDS = 2.0
QUICKPLAY_MODS_DIR = PROJECT_ROOT / "build" / "quickplay" / "mods"

# Extra -D/-X flags for the game JVM, filled from --jvm-arg. Kept module level because
# build_jvm_args sits behind three launch paths that would all need the parameter threaded through.
EXTRA_JVM_ARGS: list[str] = []

SUCCESS_PATTERNS = (
    re.compile(r"\[Server thread/INFO\](?: \[[^\]]+\])?: .+ logged in with entity id "),
    re.compile(r"\[Server thread/INFO\](?: \[[^\]]+\])?: .+ joined the game"),
    re.compile(r"\[Server thread/INFO\](?: \[[^\]]+\])?: .+加入了游戏"),
)

ERROR_PATTERN = re.compile(
    r"\b(ERROR|FATAL)\b|Exception|Caused by:|Mixin apply failed|Crash|Failed to|Unable to",
    re.IGNORECASE,
)


@dataclass(frozen=True)
class LogCursor:
    exists: bool
    size: int = 0
    prefix: bytes = b""


def offline_player_uuid(username: str) -> str:
    """Match Minecraft's UUID.nameUUIDFromBytes("OfflinePlayer:<name>") identity."""
    digest = hashlib.md5(
        f"OfflinePlayer:{username}".encode("utf-8"), usedforsecurity=False
    ).digest()
    return uuid.UUID(bytes=digest, version=3).hex


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


def resolve_game_directory(instance: str) -> Path:
    instance_dir = resolve_version_json(instance).parent
    if (instance_dir / "saves").exists() or (instance_dir / "mods").exists():
        return instance_dir
    return DOT_MINECRAFT


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
    args.extend(EXTRA_JVM_ARGS)
    return args


def build_game_args(
    version_data: dict, game_directory: Path, world: str, width: int, height: int
) -> list[str]:
    asset_index = version_data.get("assetIndex", {}).get("id", version_data.get("assets", "5"))
    replacements = {
        "${auth_player_name}": DEFAULT_USERNAME,
        "${version_name}": version_data["id"],
        "${game_directory}": str(game_directory),
        "${assets_root}": str(ASSETS_DIR),
        "${assets_index_name}": str(asset_index),
        # A new random UUID on every launch makes the same single-player owner look like a
        # different player to mod data keyed by UUID, even though level.dat restores their position.
        "${auth_uuid}": offline_player_uuid(DEFAULT_USERNAME),
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


def newest_world(saves_dir: Path) -> str:
    worlds = [path for path in saves_dir.glob("*") if path.is_dir() and (path / "level.dat").exists()]
    if not worlds:
        raise FileNotFoundError(f"No worlds found under {saves_dir}")
    return max(worlds, key=lambda path: (path / "level.dat").stat().st_mtime).name


def run_build(offline: bool = True) -> None:
    env = os.environ.copy()
    command = [str(PROJECT_ROOT / "gradlew.bat")]
    if offline:
        command.append("--offline")
    command.extend(("build", "syncQuickPlayMods"))
    subprocess.run(command, cwd=PROJECT_ROOT, env=env, check=True)


def copy_runtime_mods(mods_dir: Path, props: dict[str, str]) -> None:
    minecraft_version = props.get("minecraft_version", "1.21.1")
    managed_prefixes = (
        f"create-{minecraft_version}-",
        f"vanillin-neoforge-{minecraft_version}-",
        f"jei-{minecraft_version}-neoforge-",
    )
    staged = list(QUICKPLAY_MODS_DIR.glob("*.jar"))
    if not staged:
        raise FileNotFoundError(f"Quick-play runtime mods not found under {QUICKPLAY_MODS_DIR}")

    for existing in mods_dir.glob("*.jar"):
        if existing.name.startswith(managed_prefixes):
            existing.unlink()
    for source in staged:
        shutil.copy2(source, mods_dir / source.name)


def copy_mod_jar(mods_dir: Path) -> Path:
    props = read_gradle_properties()
    mod_id = props.get("mod_id", "create_biotech")
    pattern = str(PROJECT_ROOT / "build" / "libs" / f"{mod_id}-*.jar")
    candidates = [Path(path) for path in glob.glob(pattern) if not path.endswith("-sources.jar")]
    if not candidates:
        raise FileNotFoundError("Built mod jar not found in build/libs")

    mods_dir.mkdir(exist_ok=True)
    copy_runtime_mods(mods_dir, props)
    jar_path = max(candidates, key=lambda path: path.stat().st_mtime)
    destination = mods_dir / jar_path.name
    for existing in mods_dir.glob(f"{mod_id}-*.jar"):
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


def latest_log_candidates(game_directory: Path) -> list[Path]:
    candidates = [game_directory / "logs" / "latest.log", DOT_MINECRAFT / "logs" / "latest.log"]
    return list(dict.fromkeys(candidates))


def capture_log_cursor(path: Path) -> LogCursor:
    if not path.exists():
        return LogCursor(False)
    try:
        size = path.stat().st_size
        with path.open("rb") as handle:
            prefix = handle.read(min(size, 512))
        return LogCursor(True, size, prefix)
    except OSError:
        return LogCursor(False)


def read_log_since(path: Path, cursor: LogCursor) -> str:
    try:
        stat = path.stat()
        start = 0
        with path.open("rb") as handle:
            if cursor.exists and stat.st_size >= cursor.size:
                current_prefix = handle.read(len(cursor.prefix))
                if current_prefix == cursor.prefix:
                    start = cursor.size
            handle.seek(start)
            return handle.read().decode("utf-8", errors="replace")
    except OSError:
        return ""


def read_smoke_log(
    game_directory: Path, cursors: dict[Path, LogCursor]
) -> tuple[Path, str]:
    candidates = latest_log_candidates(game_directory)
    for path in candidates:
        text = read_log_since(path, cursors[path])
        if text.strip():
            return path, text
    return candidates[0], ""


def entered_world(log_text: str) -> bool:
    return any(pattern.search(log_text) for pattern in SUCCESS_PATTERNS)


def extract_error_excerpt(log_text: str, max_lines: int) -> str:
    lines = log_text.splitlines()
    if not lines:
        return "No new log output was written during the smoke window."

    excerpt: list[str] = []
    index = 0
    blocks = 0
    while index < len(lines) and len(excerpt) < max_lines and blocks < 3:
        if not ERROR_PATTERN.search(lines[index]):
            index += 1
            continue

        start = max(0, index - 2)
        end = index + 1
        while end < len(lines) and end - index < 80:
            line = lines[end]
            if (
                line.startswith((" ", "\t"))
                or line.startswith(("Caused by:", "Suppressed:", "..."))
                or ERROR_PATTERN.search(line)
            ):
                end += 1
                continue
            break

        excerpt.extend(lines[start:end])
        excerpt.append("")
        blocks += 1
        index = end

    if excerpt:
        return "\n".join(excerpt[:max_lines])
    return "\n".join(lines[-max_lines:])


def stop_process_tree(process: subprocess.Popen) -> None:
    if process.poll() is not None:
        return

    if os.name == "nt":
        subprocess.run(
            ["taskkill", "/PID", str(process.pid), "/T", "/F"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        )
        return

    process.terminate()
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=5)


def build_launch_command(instance: str, world: str, width: int, height: int) -> tuple[list[str], Path]:
    version_json = resolve_version_json(instance)
    instance_dir = version_json.parent
    game_directory = resolve_game_directory(instance)
    version_data = json.loads(version_json.read_text(encoding="utf-8"))
    classpath = os.pathsep.join(build_classpath(version_data, instance_dir))
    return [
        find_java(),
        *build_jvm_args(version_data, instance_dir, classpath),
        version_data["mainClass"],
        *build_game_args(version_data, game_directory, world, width, height),
    ], game_directory


def start_client(instance: str, world: str, width: int, height: int) -> tuple[subprocess.Popen, Path]:
    command, game_directory = build_launch_command(instance, world, width, height)
    process = subprocess.Popen(
        command,
        cwd=game_directory,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        creationflags=getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0) if os.name == "nt" else 0,
    )
    return process, game_directory


def launch(instance: str, world: str, width: int, height: int, dry_run: bool) -> None:
    command, game_directory = build_launch_command(instance, world, width, height)
    if dry_run:
        print(f"[DRY] {instance}")
        print(f"  game directory: {game_directory}")
        print(f"  world: {world}")
        print(f"  args: {len(command)}")
        return

    process, game_directory = start_client(instance, world, width, height)
    print(f"[LAUNCH] {instance}")
    print(f"  game directory: {game_directory}")
    print(f"  world: {world}")
    print(f"  pid: {process.pid}")


def smoke_client(
    instance: str,
    world: str,
    width: int,
    height: int,
    timeout: int,
    max_log_lines: int,
    dry_run: bool,
) -> bool:
    command, game_directory = build_launch_command(instance, world, width, height)
    candidates = latest_log_candidates(game_directory)
    cursors = {path: capture_log_cursor(path) for path in candidates}

    if dry_run:
        print(f"[SMOKE][DRY] {instance}")
        print(f"  game directory: {game_directory}")
        print(f"  world: {world}")
        print(f"  timeout: {timeout}s")
        print(f"  args: {len(command)}")
        return True

    print(f"[SMOKE] {instance}: waiting {timeout}s for quickplay world entry")
    process, game_directory = start_client(instance, world, width, height)
    print(f"[SMOKE][LAUNCH] {instance}")
    print(f"  game directory: {game_directory}")
    print(f"  world: {world}")
    print(f"  pid: {process.pid}")

    deadline = time.monotonic() + timeout
    log_path = candidates[0]
    log_text = ""
    ok = False
    try:
        while time.monotonic() < deadline:
            log_path, log_text = read_smoke_log(game_directory, cursors)
            if log_text and entered_world(log_text):
                ok = True
                break
            if process.poll() is not None:
                break
            time.sleep(0.1)

        final_log_path, final_log_text = read_smoke_log(game_directory, cursors)
        if final_log_text.strip():
            log_path = final_log_path
            log_text = final_log_text
        ok = ok or bool(log_text and entered_world(log_text))

        if ok:
            print(
                f"[SMOKE][PASS] {instance}: entered world within {timeout}s; "
                f"waiting {POST_ENTRY_SETTLE_SECONDS:g}s before cleanup"
            )
            time.sleep(POST_ENTRY_SETTLE_SECONDS)
        else:
            print(f"[SMOKE][FAIL] {instance}: did not enter world within {timeout}s")
            exit_code = process.poll()
            if exit_code is not None:
                print(f"[SMOKE][PROCESS EXIT] code={exit_code}")
            print(f"[SMOKE][LOG] {log_path}")
            print(extract_error_excerpt(log_text, max_log_lines))
        return ok
    finally:
        stop_process_tree(process)


def main() -> int:
    parser = argparse.ArgumentParser(description="Build, copy, and quickplay the local NeoForge test instance.")
    parser.add_argument("--instance", default=DEFAULT_INSTANCE)
    parser.add_argument("--world", help="Save folder name under .minecraft/saves. Defaults to the newest world.")
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--online-build", action="store_true")
    parser.add_argument("--no-copy", action="store_true")
    parser.add_argument("--dry-run", action="store_true", help="Build/copy as requested, but do not start the client.")
    parser.add_argument("--smoke", action="store_true", help="Build, quickplay, wait for a world-entry log marker, then clean up.")
    parser.add_argument("--smoke-timeout", type=int, default=DEFAULT_SMOKE_TIMEOUT)
    parser.add_argument("--smoke-log-lines", type=int, default=DEFAULT_SMOKE_LOG_LINES)
    parser.add_argument("--width", type=int, default=DEFAULT_WIDTH)
    parser.add_argument("--height", type=int, default=DEFAULT_HEIGHT)
    parser.add_argument(
        "--jvm-arg",
        action="append",
        default=[],
        metavar="ARG",
        help="Extra flag for the game JVM, repeatable. "
             "Example: --jvm-arg=-Dcreate_biotech.surgery.profile=true",
    )
    args = parser.parse_args()
    if args.smoke_timeout <= 0:
        parser.error("--smoke-timeout must be greater than 0")
    if args.smoke_log_lines <= 0:
        parser.error("--smoke-log-lines must be greater than 0")
    EXTRA_JVM_ARGS.extend(args.jvm_arg)

    game_directory = resolve_game_directory(args.instance)
    saves_dir = game_directory / "saves"
    mods_dir = game_directory / "mods"
    world = args.world or newest_world(saves_dir)
    if not (saves_dir / world / "level.dat").exists():
        raise FileNotFoundError(f"World not found: {saves_dir / world}")

    if not args.skip_build:
        print("[BUILD] gradlew build" if args.online_build else "[BUILD] gradlew --offline build")
        run_build(offline=not args.online_build)

    if not args.no_copy:
        print(f"[COPY] {copy_mod_jar(mods_dir)}")

    if args.smoke:
        return 0 if smoke_client(
            args.instance,
            world,
            args.width,
            args.height,
            args.smoke_timeout,
            args.smoke_log_lines,
            args.dry_run,
        ) else 1

    launch(args.instance, world, args.width, args.height, args.dry_run)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
