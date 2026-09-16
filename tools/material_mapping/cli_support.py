"""Shared authoring CLI parsing, schema costs, serialization and safe publication."""
from __future__ import annotations
import io
import json
import tempfile
from pathlib import Path
from typing import Any
from .sampling import validate_target_size

def _parse_sources(values: list[str]) -> dict[str, Path]:
    result: dict[str, Path] = {}
    for value in values:
        if "=" not in value:
            raise ValueError("--source must be SLOT=PNG")
        slot, path = value.split("=", 1)
        if not slot or not path or slot in result:
            raise ValueError("--source must use each non-empty slot once")
        result[slot] = Path(path)
    return result


def _parse_grids(values: list[str], slots: list[str]) -> dict[str, tuple[int, int]]:
    result: dict[str, tuple[int, int]] = {}
    for value in values:
        if "=" not in value:
            raise ValueError("--source-grid must be SLOT=WIDTHxHEIGHT")
        slot, size = value.split("=", 1)
        if slot not in slots or slot in result:
            raise ValueError("--source-grid must name each declared slot at most once")
        result[slot] = _parse_size(size)
    return result


def _parse_size(value: str, *, target: bool = False) -> tuple[int, int]:
    parts = value.lower().split("x", 1)
    if len(parts) != 2:
        raise ValueError("--size must be WIDTHxHEIGHT")
    width, height = int(parts[0]), int(parts[1])
    if target:
        validate_target_size((width, height))
    elif width <= 0 or height <= 0:
        raise ValueError("--size must be positive")
    return width, height


def _same_path(left: Path, right: Path) -> bool:
    return left.resolve() == right.resolve() or (left.exists() and right.exists() and left.samefile(right))


def _computed_cost(regions: list[dict], pixels: list[dict]) -> dict[str, int]:
    return {
        "max_pieces_per_source_quad": 1 if regions or pixels else 0,
        "additional_quads": 0,
    }


def _png_bytes(image) -> bytes:
    output = io.BytesIO()
    image.save(output, format="PNG", optimize=False)
    return output.getvalue()


def _publish_outputs(outputs: list[tuple[Path, bytes]]) -> None:
    for path, _ in outputs:
        path.parent.mkdir(parents=True, exist_ok=True)
    staged: list[tuple[Path, Path]] = []
    try:
        for path, content in outputs:
            with tempfile.NamedTemporaryFile(
                    mode="wb", prefix=path.name + ".", suffix=".tmp",
                    dir=path.parent, delete=False) as stream:
                stream.write(content)
                staged.append((Path(stream.name), path))
        for temporary, path in staged:
            temporary.replace(path)
    finally:
        for temporary, _ in staged:
            temporary.unlink(missing_ok=True)


def _json(data: dict[str, Any]) -> str:
    return json.dumps(data, ensure_ascii=False, indent=2, separators=(",", ": ")) + "\n"

