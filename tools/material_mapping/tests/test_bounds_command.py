"""End-to-end compact candidate selection with real PNGs and schema 1 output."""
import json

from PIL import Image
import pytest

from tools.material_mapping import cli as commands
from tools.material_mapping.inference import infer_mapping
from tools.material_mapping.sampling import render_definition
from tools.material_mapping.source_bounds import resolve_min_bounds
from tools.tests.support import setup_inputs, read_report, coordinate_image, FIXTURES


def compact_inputs(tmp_path, fuzzy=False):
    sample = Image.new("RGBA", (3, 1))
    sample.putpixel((0, 0), (10, 20, 30, 255))
    sample.putpixel((2, 0), (90, 100, 110, 255))
    source = Image.new("RGBA", (22, 4))
    source.putpixel((20, 2), sample.getpixel((0, 0)))
    color = (93 if fuzzy else 90, 100, 110, 255)
    for x in range(18):
        source.putpixel((x, 0), color)
    source.putpixel((21, 2), color)
    args, outputs = setup_inputs(tmp_path, sample, source)
    if fuzzy:
        args += ["--rgb-rmse", "6", "--rgb-max-error", "12"]
    return args, outputs, sample, source


@pytest.mark.parametrize("fuzzy", [False, True])
def test_compact_cli_finds_late_candidate_beyond_report_limit_and_transfers_uvs(tmp_path, fuzzy):
    args, outputs, sample, _ = compact_inputs(tmp_path, fuzzy)
    assert commands.main(args + ["--ambiguity-policy", "min-bounds", "--max-candidates", "1"]) == 0
    report = read_report(outputs)
    assert report["resolved_pixels"] == 2 and report["ambiguous_pixels"] == 0
    assert report["background_pixels"] == 1 and report["matching"]["ambiguity_policy"] == "min-bounds"
    bounds = report["source_bounds_optimization"]
    assert bounds["source_bounds"] == {"all": [20, 2, 2, 1]}
    assert bounds["input_area"] == 63 and bounds["output_area"] == bounds["lower_bound"] == 2
    assert bounds["optimal"] and bounds["candidate_search_complete"]
    assert bounds["selected_regions"][0]["candidate_count"] == 19
    assert bounds["selected_regions"][0]["source"] == [21, 2, 1, 1]
    definition = json.loads(outputs["target.json"].read_text())
    assert definition == json.loads((FIXTURES / "compact_bounds.json").read_text())
    assert definition["schema"] == 1 and "source_bounds_optimization" not in definition
    other = coordinate_image(22, 4, 21)
    transferred = render_definition(definition, {"all": other})
    assert [transferred.getpixel((x, 0)) for x in range(3)] == [
        other.getpixel((20, 2)), (0, 0, 0, 0), other.getpixel((21, 2))]
    with Image.open(outputs["preview.png"]) as preview:
        assert preview.getpixel((0, 0)) == sample.getpixel((0, 0))
        assert preview.getpixel((2, 0)) == (93 if fuzzy else 90, 100, 110, 255)
    first = {name: path.read_bytes() for name, path in outputs.items()}
    assert commands.main(args + ["--ambiguity-policy", "min-bounds", "--max-candidates", "1"]) == 0
    assert first == {name: path.read_bytes() for name, path in outputs.items()}


@pytest.mark.parametrize("fuzzy", [False, True])
def test_complete_candidate_collection_is_independent_of_diagnostic_cap(tmp_path, fuzzy):
    _, _, sample, source = compact_inputs(tmp_path, fuzzy)
    kwargs = {"rgb_rmse": 6, "rgb_max_error": 12} if fuzzy else {}
    result = infer_mapping(sample, {"all": source}, {"all": source.size}, candidate_limit=1,
                           collect_all_candidates=True, **kwargs)
    ambiguous, = result.issues
    assert len(ambiguous.candidates) == 19 and not ambiguous.search_truncated
    assert (21, 2, 1, 1) in {c.source for c in ambiguous.candidates}


def test_legacy_report_policy_keeps_ambiguity_unresolved(tmp_path):
    args, outputs, _, _ = compact_inputs(tmp_path)
    assert commands.main(args + ["--ambiguity-policy", "report"]) == 3
    report = read_report(outputs)
    assert (report["resolved_pixels"], report["ambiguous_pixels"]) == (1, 1)
    assert "source_bounds_optimization" not in report


@pytest.mark.parametrize("value", ["-1", "nan", "inf"])
def test_invalid_bounds_budget_preserves_all_outputs(tmp_path, value):
    args, outputs, _, _ = compact_inputs(tmp_path)
    for path in outputs.values():
        path.write_bytes(b"previous output")
    assert commands.main(args + ["--ambiguity-policy", "min-bounds", "--bounds-seconds", value]) == 2
    assert all(path.read_bytes() == b"previous output" for path in outputs.values())


def test_full_rgba_roundtrip_still_protects_outputs_after_candidate_resolution(tmp_path, monkeypatch):
    args, outputs, _, _ = compact_inputs(tmp_path)
    for path in outputs.values():
        path.write_bytes(b"previous output")
    original = commands.render_definition

    def corrupt(*args, **kwargs):
        result = original(*args, **kwargs)
        r, g, b, a = result.getpixel((0, 0))
        result.putpixel((0, 0), (r ^ 1, g, b, a))
        return result

    monkeypatch.setattr(commands, "render_definition", corrupt)
    assert commands.main(args + ["--ambiguity-policy", "min-bounds"]) == 2
    assert all(path.read_bytes() == b"previous output" for path in outputs.values())


def test_no_match_remains_a_draft_not_an_arbitrary_nearby_source(tmp_path):
    args, outputs, _, _ = compact_inputs(tmp_path)
    sample_path = tmp_path / "sample.png"
    with Image.open(sample_path) as image:
        sample = image.convert("RGBA")
    sample.putpixel((1, 0), (1, 2, 3, 7))
    sample.save(sample_path)
    assert commands.main(args + ["--ambiguity-policy", "min-bounds"]) == 3
    report = read_report(outputs)
    assert (report["resolved_pixels"], report["ambiguous_pixels"], report["unmatched_pixels"]) == (2, 0, 1)
    with Image.open(outputs["mask.png"]) as mask:
        assert mask.getpixel((1, 0)) == (255, 0, 0, 255)


def test_overlapping_face_evidence_cannot_collapse_whole_matches_to_a_single_source_pixel():
    # Both AA faces overlap at the middle target pixel. A legal whole-face match
    # spans TWO source pixels. Independent projected choices must not collapse
    # all three destinations to source pixel 0 merely to report a 1x1 box.
    sample = Image.new("RGBA", (3, 1), (50, 60, 70, 255))
    source = Image.new("RGBA", (10, 1), (50, 60, 70, 255))
    raw = infer_mapping(sample, {"all": source}, {"all": source.size}, candidate_limit=1,
                        collect_all_candidates=True, face_rectangles=[(0, 0, 2, 1), (1, 0, 2, 1)])
    resolved, report = resolve_min_bounds(raw)
    assert not resolved.pixels and len(resolved.issues) == 3
    assert all(i.reason == "ambiguous" and i.resolution_blocker == "overlapping_face_evidence"
               for i in resolved.issues)
    assert report["excluded_ambiguous_regions"] == 3 and report["optimal"] is None
    assert all(len(i.candidates) == 10 and not i.search_truncated for i in resolved.issues)


def test_overlapping_fuzzy_evidence_cannot_concentrate_legal_rectangle_errors_into_illegal_pixels():
    sample = Image.new("RGBA", (4, 1), (50, 60, 70, 255))
    source = Image.new("RGBA", (3, 1), (50, 60, 70, 255))
    source.putpixel((0, 0), (60, 60, 70, 255))
    # Both whole matches have RMS sqrt(100/3) < 6. Picking source x=0
    # independently for all destinations would instead give RMS 10 > 6.
    raw = infer_mapping(sample, {"all": source}, {"all": source.size}, collect_all_candidates=True,
                        rgb_rmse=6, rgb_max_error=12, face_rectangles=[(0, 0, 3, 1), (1, 0, 3, 1)])
    resolved, report = resolve_min_bounds(raw)
    assert not resolved.pixels and len(resolved.issues) == 4
    assert all(i.resolution_blocker == "overlapping_face_evidence" for i in resolved.issues)
    assert report["excluded_ambiguous_regions"] == 4 and report["optimal"] is None

