"""Real local texture fixtures: separate known-coordinate controls from authored art."""
import json
from pathlib import Path

import pytest
from PIL import Image

from tools.material_mapping.cli import main
from tools.material_mapping.inference import infer_mapping
from tools.material_mapping.sampling import render_definition


FIXTURES = Path(__file__).resolve().parents[2] / "test-resources/fixtures/connected_spider"
MATERIALS = ["andesite", "brass", "copper", "biotech", "explosion_proof"]


@pytest.mark.parametrize("material", MATERIALS)
def test_known_connected_patch_recovers_coordinates_and_transfers_to_other_casings(tmp_path, material):
    source_path = FIXTURES / f"{material}_casing_connected.png"
    with Image.open(source_path) as image:
        source = image.convert("RGBA")
    sample = source.crop((16, 16, 48, 48)).transpose(Image.Transpose.ROTATE_270)
    result = infer_mapping(sample, {"all": source}, {"all": (128, 128)})
    assert not result.issues and len(result.pixels) == 1024
    assert all((p.source_x, p.source_y) == (16 + p.destination_y, 47 - p.destination_x) for p in result.pixels)

    sample_path, target = tmp_path / "sample.png", tmp_path / "target.json"
    sample.save(sample_path)
    assert main([
        "infer", "--sample", str(sample_path), "--source", f"all={source_path}",
        "--source-grid", "all=128x128", "--target-json", str(target),
        "--preview", str(tmp_path / "preview.png"), "--report", str(tmp_path / "report.json"),
        "--unresolved-mask", str(tmp_path / "mask.png"),
    ]) == 0
    definition = json.loads(target.read_text(encoding="utf-8"))
    assert len(definition["regions"]) == 1 and definition["pixels"] == []
    for other in MATERIALS:
        with Image.open(FIXTURES / f"{other}_casing_connected.png") as image:
            other_source = image.convert("RGBA")
        expected = other_source.crop((16, 16, 48, 48)).transpose(Image.Transpose.ROTATE_270)
        assert render_definition(definition, {"all": other_source}).tobytes() == expected.tobytes()


def test_authored_spider_reports_unresolved_instead_of_inventing_uvs(tmp_path):
    target, report = tmp_path / "target.json", tmp_path / "report.json"
    assert main([
        "infer", "--sample", str(FIXTURES / "spider_andesite_authored.png"),
        "--source", f"all={FIXTURES / 'andesite_casing_connected.png'}", "--source-grid", "all=128x128",
        "--target-json", str(target), "--preview", str(tmp_path / "preview.png"),
        "--report", str(report), "--unresolved-mask", str(tmp_path / "mask.png"),
    ]) == 3
    data = json.loads(report.read_text(encoding="utf-8"))
    assert not target.exists() and not data["target_written"]
    assert data["resolved_pixels"] == 0
    assert data["ambiguous_pixels"] == 694 and data["unmatched_pixels"] == 634
    assert data["background_pixels"] == 720

