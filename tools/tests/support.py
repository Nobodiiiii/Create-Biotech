from __future__ import annotations

import json
from pathlib import Path

from PIL import Image


def coordinate_color(x: int, y: int, seed: int = 0) -> tuple[int, int, int, int]:
    return ((x + seed) % 256, (y * 3 + seed) % 256,
            (x * 17 + y * 31 + seed) % 256, 64 + (x * 7 + y * 11 + seed) % 192)


def coordinate_image(width: int, height: int, seed: int = 0) -> Image.Image:
    image = Image.new("RGBA", (width, height))
    image.putdata([coordinate_color(x, y, seed) for y in range(height) for x in range(width)])
    return image


def group_names(data: dict) -> list[str]:
    return [entry["name"] for entry in data["outliner"] if isinstance(entry, dict)]


def _elements_in_group(data: dict, name: str) -> list[dict]:
    by_uuid = {element["uuid"]: element for element in data["elements"]}
    group = next(entry for entry in data["outliner"] if isinstance(entry, dict) and entry["name"] == name)
    return [by_uuid[uuid] for uuid in group["children"]]


def stable_face_pairs(data: dict, slot: str) -> bool:
    destination = {
        (element["name"], face)
        for element in _elements_in_group(data, "CM_DEST")
        for face in element.get("faces", {})
    }
    source = {
        (element["name"], face)
        for element in _elements_in_group(data, f"CM_SOURCE[{slot}]")
        for face in element.get("faces", {})
    }
    return bool(source) and source <= destination


FIXTURES = Path(__file__).resolve().parents[1] / "test-resources/fixtures/definitions/python_inference"

COLORS = {letter: (17 + index * 31, 23 + index * 19, 29 + index * 13, 80 + index * 23)
          for index, letter in enumerate("ABCDEF")}
ZERO = (0, 0, 0, 0)


def letter_image(rows):
    image = Image.new("RGBA", (len(rows[0]), len(rows)), ZERO)
    for y, row in enumerate(rows):
        for x, letter in enumerate(row):
            image.putpixel((x, y), COLORS[letter])
    return image


def setup_inputs(tmp_path, sample=None, source=None):
    sample = sample if sample is not None else letter_image(["DA", "EB", "FC"])
    source = source if source is not None else letter_image(["ABC", "DEF"])
    sample_path, source_path = tmp_path / "sample.png", tmp_path / "source.png"
    sample.save(sample_path)
    source.save(source_path)
    outputs = {name: tmp_path / name for name in ["target.json", "preview.png", "report.json", "mask.png"]}
    args = ["infer", "--sample", str(sample_path), "--source", f"all={source_path}",
            "--source-grid", f"all={source.width}x{source.height}",
            "--target-json", str(outputs["target.json"]), "--preview", str(outputs["preview.png"]),
            "--report", str(outputs["report.json"]), "--unresolved-mask", str(outputs["mask.png"])]
    return args, outputs


def read_report(outputs):
    return json.loads(outputs["report.json"].read_text(encoding="utf-8"))

