"""Read cube face UV bounds from .bbmodel JSON; no editor or preview dependency."""
from __future__ import annotations

import json
from math import isfinite
from pathlib import Path


def face_rectangles(path: str | Path, size: tuple[int, int]) -> list[tuple[int, int, int, int]]:
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError("model root must be an object")
    resolution = data.get("resolution")
    if not isinstance(resolution, dict) or any(
            not _integer(resolution.get(key)) for key in ("width", "height")):
        raise ValueError("model resolution must contain integer width and height")
    if (resolution["width"], resolution["height"]) != size:
        raise ValueError("model resolution must equal the sample dimensions")
    elements = data.get("elements")
    if not isinstance(elements, list):
        raise ValueError("model elements must be a list of cubes")
    outliner = data.get("outliner", [])
    if not isinstance(outliner, list):
        raise ValueError("model outliner must be a list")
    excluded = {uuid for uuid, exported in _outliner_entries(outliner) if not exported}
    destination = next((group for group in outliner
                        if isinstance(group, dict) and group.get("name") == "CM_DEST"), None)
    if destination is not None:
        by_uuid = {element["uuid"]: element for element in elements
                   if isinstance(element, dict) and isinstance(element.get("uuid"), str)}
        elements = [by_uuid[uuid] for uuid, _ in _outliner_entries(destination.get("children", []))
                    if uuid in by_uuid]
    rectangles = []
    for element in elements:
        if not isinstance(element, dict):
            raise ValueError("model elements must be cubes")
        if "uuid" in element and not isinstance(element["uuid"], str):
            raise ValueError("model element uuid must be a string")
        if element.get("export") is False or element.get("uuid") in excluded:
            continue
        if element.get("type", "cube") != "cube":
            raise ValueError("model inference supports only rectangular cube faces")
        faces = element.get("faces")
        if not isinstance(faces, dict):
            raise ValueError("model cube must have rectangular face UVs")
        for direction, face in faces.items():
            if direction not in {"north", "south", "east", "west", "up", "down"}:
                raise ValueError(f"unsupported model cube face {direction}")
            if not isinstance(face, dict):
                raise ValueError("model face must have a rectangular UV")
            if "texture" in face and face["texture"] is None:
                continue
            rotation = face.get("rotation", 0)
            if not _integer(rotation) or rotation not in (0, 90, 180, 270):
                raise ValueError(f"model face {direction} UV rotation must be 0, 90, 180 or 270")
            uv = face.get("uv")
            if not isinstance(uv, list) or len(uv) != 4:
                raise ValueError(f"model face {direction} must have a rectangular UV")
            if not all(_integer(value) for value in uv):
                raise ValueError(f"model face {direction} UV must use integer coordinates")
            x1, y1, x2, y2 = map(int, uv)
            x, y, width, height = min(x1, x2), min(y1, y2), abs(x2 - x1), abs(y2 - y1)
            if width == 0 or height == 0:
                raise ValueError(f"model face {direction} UV must be non-empty")
            if min(x, y) < 0 or x + width > size[0] or y + height > size[1]:
                raise ValueError(f"model face {direction} UV exceeds sample bounds")
            rectangles.append((x, y, width, height))
    if not rectangles:
        raise ValueError("model must contain at least one non-empty face UV")
    return rectangles


def _outliner_entries(children, parent_export=True):
    if not isinstance(children, list):
        raise ValueError("model outliner children must be a list")
    for child in children:
        if isinstance(child, str):
            yield child, parent_export
        elif isinstance(child, dict):
            yield from _outliner_entries(child.get("children", []),
                                         parent_export and child.get("export") is not False)


def _integer(value):
    return type(value) is int or (type(value) is float and isfinite(value) and value.is_integer())
