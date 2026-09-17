"""Model boundaries constrain exact matching; geometry costs count reused faces."""
import json
import os
from pathlib import Path

import pytest
from PIL import Image

from tools.material_mapping import cli as commands
from tools.material_mapping.inference import infer_mapping
from tools.material_mapping.compression import geometry_cost
from tools.material_mapping.sampling import render_definition
from tools.tests.support import coordinate_image
from tools.tests.support import FIXTURES, read_report, setup_inputs
from tools.tests.support import COLORS, ZERO, letter_image


def write_model(tmp_path, size, rectangles):
    data = {
        "resolution": {"width": size[0], "height": size[1]},
        "elements": [{"uuid": f"cube-{index}", "name": f"cube-{index}",
                      "from": [0, 0, 0], "to": [1, 1, 1],
                      "faces": {"north": {"uv": list(uv), "texture": 0}}}
                     for index, uv in enumerate(rectangles)],
    }
    path = tmp_path / "model.bbmodel"
    path.write_text(json.dumps(data), encoding="utf-8")
    return path


def run(args):
    # argparse reports an unsupported --model as 2 before this feature exists.
    try:
        return commands.main(args)
    except SystemExit as error:
        return error.code


def test_face_boundaries_recover_repeated_colors_that_blind_bisection_loses(tmp_path):
    source = Image.new("RGBA", (12, 6), ZERO)
    source.paste(letter_image(["AB", "CA"]), (1, 1))
    source.paste(letter_image(["DEFE", "ABAD"]), (7, 3))
    sample = letter_image(["ABDEFE", "CAABAD"])
    blind = infer_mapping(sample, {"all": source}, {"all": source.size})
    assert len(blind.pixels) == 6 and blind.issues
    model = write_model(tmp_path, sample.size, [(0, 0, 2, 2), (2, 0, 6, 2)])
    args, outputs = setup_inputs(tmp_path, sample, source)

    assert run(args + ["--model", str(model)]) == 0
    report = read_report(outputs)
    assert report["resolved_pixels"] == 12 and report["unused_pixels"] == 0
    definition = json.loads(outputs["target.json"].read_text(encoding="utf-8"))
    assert definition["schema"] == 1
    assert definition == json.loads((FIXTURES / "model_aware.json").read_text(encoding="utf-8"))
    # Independent, coordinate-coded second material detects same-color wrong UVs.
    other = coordinate_image(12, 6, 83)
    rendered = render_definition(definition, {"all": other})
    expected_coordinates = [(1, 1), (2, 1), (7, 3), (8, 3), (9, 3), (10, 3),
                            (1, 2), (2, 2), (7, 4), (8, 4), (9, 4), (10, 4)]
    assert [rendered.getpixel((x, y)) for y in range(2) for x in range(6)] == [
        other.getpixel(p) for p in expected_coordinates]
    assert report["model_geometry_cost"]["output_fragments"] == 2
    assert report["selection_optimization"]["scope"] == "fixed_resolved_uv_mapping"


@pytest.mark.parametrize("rows,coordinates", [
    (["ABC", "DEF"], [(1, 1), (2, 1), (3, 1), (1, 2), (2, 2), (3, 2)]),
    (["DA", "EB", "FC"], [(1, 2), (1, 1), (2, 2), (2, 1), (3, 2), (3, 1)]),
    (["FED", "CBA"], [(3, 2), (2, 2), (1, 2), (3, 1), (2, 1), (1, 1)]),
    (["CF", "BE", "AD"], [(3, 1), (3, 2), (2, 1), (2, 2), (1, 1), (1, 2)]),
    (["CBA", "FED"], [(3, 1), (2, 1), (1, 1), (3, 2), (2, 2), (1, 2)]),
    (["DEF", "ABC"], [(1, 2), (2, 2), (3, 2), (1, 1), (2, 1), (3, 1)]),
])
def test_model_flow_keeps_six_transforms_and_excludes_unused_opaque_space(tmp_path, rows, coordinates):
    patch = letter_image(rows)
    source = Image.new("RGBA", (5, 4), ZERO)
    source.paste(letter_image(["ABC", "DEF"]), (1, 1))
    sample = Image.new("RGBA", (patch.width + 2, patch.height + 2), (251, 252, 253, 255))
    sample.paste(patch, (1, 1))
    # Reversed model UV endpoints normalize coverage, not the inferred source transform.
    model = write_model(tmp_path, sample.size, [(patch.width + 1, patch.height + 1, 1, 1)])
    args, outputs = setup_inputs(tmp_path, sample, source)
    assert run(args + ["--model", str(model)]) == 0
    report = read_report(outputs)
    assert report["resolved_pixels"] == 6
    assert report["unused_pixels"] == sample.width * sample.height - 6
    assert report["background_pixels"] == report["unmatched_pixels"] == report["ambiguous_pixels"] == 0
    definition = json.loads(outputs["target.json"].read_text(encoding="utf-8"))
    other = coordinate_image(5, 4, 101)
    rendered = render_definition(definition, {"all": other})
    assert [rendered.getpixel((x + 1, y + 1)) for y in range(patch.height)
            for x in range(patch.width)] == [other.getpixel(p) for p in coordinates]
    with Image.open(outputs["preview.png"]) as preview, Image.open(outputs["mask.png"]) as mask:
        assert preview.getpixel((0, 0)) == mask.getpixel((0, 0)) == ZERO
        assert preview.crop((1, 1, patch.width + 1, patch.height + 1)).tobytes() == patch.tobytes()


def test_reused_faces_are_one_mapping_but_each_emits_geometry_and_repeat_export_is_stable(tmp_path):
    sample = letter_image(["ABC", "DEF"])
    model = write_model(tmp_path, sample.size, [(0, 0, 3, 2)] * 3)
    args, outputs = setup_inputs(tmp_path, sample, sample)
    inputs = {path: path.read_bytes() for path in [model, tmp_path / "sample.png", tmp_path / "source.png"]}
    assert run(args + ["--model", str(model)]) == 0
    report = read_report(outputs)
    cost = report["model_geometry_cost"]
    assert report["resolved_pixels"] == 6 and report["selection_optimization"]["output_selections"] == 1
    assert (cost["original_faces"], cost["output_fragments"], cost["max_pieces_per_face"],
            cost["additional_quads"], cost["raw_mapping_intersections"]) == (3, 3, 1, 0, 3)
    first = {name: path.read_bytes() for name, path in outputs.items()}
    assert run(args + ["--model", str(model)]) == 0
    assert first == {name: path.read_bytes() for name, path in outputs.items()}
    assert all(path.read_bytes() == data for path, data in inputs.items())


def test_overlapping_unique_face_matches_with_different_source_uv_become_conflict_draft(tmp_path):
    sample = letter_image(["ABE", "CDF"])
    source = Image.new("RGBA", (10, 6), ZERO)
    source.paste(letter_image(["AB", "CD"]), (1, 1))
    source.paste(letter_image(["BE", "DF"]), (6, 3))
    model = write_model(tmp_path, sample.size, [(0, 0, 2, 2), (1, 0, 3, 2)])
    args, outputs = setup_inputs(tmp_path, sample, source)
    assert run(args + ["--model", str(model)]) == 3
    report = read_report(outputs)
    assert (report["resolved_pixels"], report["ambiguous_pixels"], report["unmatched_pixels"]) == (4, 2, 0)
    assert {tuple(c["source"]) for issue in report["unresolved_regions"] for c in issue["candidates"]} == {
        (2, 1, 1, 1), (2, 2, 1, 1), (6, 3, 1, 1), (6, 4, 1, 1)}
    with Image.open(outputs["preview.png"]) as preview:
        assert preview.getpixel((1, 0)) == preview.getpixel((1, 1)) == ZERO
    before = {name: path.read_bytes() for name, path in outputs.items()}
    data = json.loads(model.read_text(encoding="utf-8"))
    data["elements"].reverse()
    model.write_text(json.dumps(data), encoding="utf-8")
    assert run(args + ["--model", str(model)]) == 3
    assert before == {name: path.read_bytes() for name, path in outputs.items()}


def test_holes_and_unused_pixels_have_disjoint_accounting_and_reused_split_costs(tmp_path):
    source = coordinate_image(3, 3)
    sample = source.copy()
    sample.putpixel((1, 0), ZERO)
    sample.putpixel((1, 1), ZERO)
    sample.putpixel((1, 2), ZERO)
    model = write_model(tmp_path, sample.size, [(0, 0, 3, 3)] * 3)
    args, outputs = setup_inputs(tmp_path, sample, source)
    assert run(args + ["--model", str(model)]) == 0
    report = read_report(outputs)
    assert (report["resolved_pixels"], report["background_pixels"], report["unused_pixels"]) == (6, 3, 0)
    cost = report["model_geometry_cost"]
    assert (cost["original_faces"], cost["output_fragments"], cost["max_pieces_per_face"],
            cost["additional_quads"]) == (3, 6, 2, 3)
    definition = json.loads(outputs["target.json"].read_text(encoding="utf-8"))
    assert definition["computed_cost"] == {"max_pieces_per_source_quad": 2, "additional_quads": 3}
    other = Image.new("RGBA", (3, 3), COLORS["F"])
    rendered = render_definition(definition, {"all": other})
    assert [rendered.getpixel((1, y)) for y in range(3)] == [ZERO] * 3


def test_model_draft_keeps_unmatched_and_multiple_candidates_without_guessing(tmp_path):
    sample = letter_image(["ACB"])
    source = letter_image(["ABB"])
    model = write_model(tmp_path, sample.size, [(0, 0, 3, 1)] * 2)
    args, outputs = setup_inputs(tmp_path, sample, source)
    assert run(args + ["--model", str(model)]) == 3
    report = read_report(outputs)
    assert (report["resolved_pixels"], report["ambiguous_pixels"], report["unmatched_pixels"]) == (1, 1, 1)
    assert report["model_geometry_cost"]["output_fragments"] == 2


@pytest.mark.parametrize("output_flag", ["--target-json", "--preview", "--report", "--unresolved-mask"])
def test_model_input_cannot_be_replaced_by_any_output(tmp_path, output_flag, capsys):
    args, outputs = setup_inputs(tmp_path)
    model = write_model(tmp_path, (2, 3), [(0, 0, 2, 3)])
    original = model.read_bytes()
    for path in outputs.values():
        path.write_bytes(b"previous output")
    args[args.index(output_flag) + 1] = str(model)
    assert run(args + ["--model", str(model)]) == 2
    assert "must not overwrite an input" in capsys.readouterr().err
    assert model.read_bytes() == original
    assert all(path.read_bytes() == b"previous output" for path in outputs.values())


def test_model_hardlink_alias_is_protected_as_input(tmp_path, capsys):
    args, outputs = setup_inputs(tmp_path)
    model = write_model(tmp_path, (2, 3), [(0, 0, 2, 3)])
    original = model.read_bytes()
    os.link(model, outputs["report.json"])
    assert run(args + ["--model", str(model)]) == 2
    assert "must not overwrite an input" in capsys.readouterr().err
    assert outputs["report.json"].read_bytes() == model.read_bytes() == original
    assert not outputs["target.json"].exists()


@pytest.mark.parametrize("change,expected", [
    ("size", "resolution"), ("fractional", "integer"), ("outside", "bounds"),
    ("degenerate", "non-empty"), ("mesh", "cube"), ("missing", "UV"),
    ("huge-resolution", "resolution"), ("uuid-object", "uuid"), ("face-direction", "cube face"),
])
def test_invalid_model_preserves_all_outputs(tmp_path, change, expected, capsys):
    args, outputs = setup_inputs(tmp_path)
    model = write_model(tmp_path, (2, 3), [(0, 0, 2, 3)])
    data = json.loads(model.read_text(encoding="utf-8"))
    if change == "size":
        data["resolution"]["width"] = 3
    elif change == "fractional":
        data["elements"][0]["faces"]["north"]["uv"][0] = 0.5
    elif change == "outside":
        data["elements"][0]["faces"]["north"]["uv"][2] = 3
    elif change == "degenerate":
        data["elements"][0]["faces"]["north"]["uv"][2] = 0
    elif change == "mesh":
        data["elements"][0]["type"] = "mesh"
    elif change == "missing":
        del data["elements"][0]["faces"]["north"]["uv"]
    elif change == "huge-resolution":
        data["resolution"]["width"] = 10 ** 500
    elif change == "uuid-object":
        data["elements"][0]["uuid"] = {}
    elif change == "face-direction":
        data["elements"][0]["faces"]["diagonal"] = data["elements"][0]["faces"].pop("north")
    model.write_text(json.dumps(data), encoding="utf-8")
    for path in outputs.values():
        path.write_bytes(b"previous output")
    assert run(args + ["--model", str(model)]) == 2
    assert expected in capsys.readouterr().err
    assert all(path.read_bytes() == b"previous output" for path in outputs.values())


def test_overlap_uses_unique_whole_face_evidence_without_guessing_symmetric_subface(tmp_path):
    sample = letter_image(["ABA", "CAD"])
    model = write_model(tmp_path, sample.size, [(0, 0, 3, 2), (0, 0, 3, 1)])
    args, outputs = setup_inputs(tmp_path, sample, sample)
    assert run(args + ["--model", str(model)]) == 0
    report = read_report(outputs)
    assert report["resolved_pixels"] == 6 and report["ambiguous_pixels"] == 0
    assert report["model_geometry_cost"]["output_fragments"] == 2


def test_fully_ambiguous_reused_face_preserves_candidate_bounds_and_previous_target(tmp_path):
    sample = letter_image(["AA", "AA"])
    source = Image.new("RGBA", (16, 16), COLORS["A"])
    model = write_model(tmp_path, sample.size, [(0, 0, 2, 2)] * 3)
    args, outputs = setup_inputs(tmp_path, sample, source)
    outputs["target.json"].write_bytes(b"previous target")
    assert run(args + ["--model", str(model), "--max-candidates", "2"]) == 3
    report = read_report(outputs)
    assert not report["target_written"] and outputs["target.json"].read_bytes() == b"previous target"
    assert report["ambiguous_pixels"] == 4
    issue, = report["unresolved_regions"]
    assert issue["candidate_count"] == 3 and len(issue["candidates"]) == 2
    assert issue["candidate_count_is_lower_bound"] and issue["candidates_truncated"]
    assert report["model_geometry_cost"]["output_fragments"] == 0


def test_paired_model_uses_only_destination_group_and_retains_hidden_destination_faces(tmp_path):
    sample = letter_image(["ABC", "DEF"])
    model = write_model(tmp_path, sample.size, [(0, 0, 3, 2), (20, 0, 23, 2)])
    data = json.loads(model.read_text(encoding="utf-8"))
    data["elements"][0]["visibility"] = False
    data["outliner"] = [{"name": "CM_DEST", "children": ["cube-0"]},
                        {"name": "CM_SOURCE[all]", "children": ["cube-1"]}]
    model.write_text(json.dumps(data), encoding="utf-8")
    args, outputs = setup_inputs(tmp_path, sample, sample)
    assert run(args + ["--model", str(model)]) == 0
    assert read_report(outputs)["model_geometry_cost"]["original_faces"] == 1


@pytest.mark.parametrize("paired", [False, True])
def test_nonexported_groups_and_disabled_faces_do_not_change_coverage_or_face_cost(tmp_path, paired):
    sample = letter_image(["ABC", "DEF"])
    model = write_model(tmp_path, sample.size, [(0, 0, 3, 2), (20, 0, 23, 2)])
    data = json.loads(model.read_text(encoding="utf-8"))
    data["elements"][0]["faces"]["south"] = {"texture": None}
    group = {"name": "not-exported", "export": False, "children": ["cube-1"]}
    data["outliner"] = ([{"name": "CM_DEST", "children": ["cube-0", group]}]
                        if paired else ["cube-0", group])
    model.write_text(json.dumps(data), encoding="utf-8")
    args, outputs = setup_inputs(tmp_path, sample, sample)
    assert run(args + ["--model", str(model)]) == 0
    assert read_report(outputs)["model_geometry_cost"]["original_faces"] == 1


@pytest.mark.parametrize("rotation", [45, True, "90"])
def test_unsupported_face_uv_rotation_is_rejected_before_publication(tmp_path, rotation, capsys):
    args, outputs = setup_inputs(tmp_path)
    model = write_model(tmp_path, (2, 3), [(0, 0, 2, 3)])
    data = json.loads(model.read_text(encoding="utf-8"))
    data["elements"][0]["faces"]["north"]["rotation"] = rotation
    model.write_text(json.dumps(data), encoding="utf-8")
    for path in outputs.values():
        path.write_bytes(b"previous output")
    assert run(args + ["--model", str(model)]) == 2
    assert "UV rotation" in capsys.readouterr().err
    assert all(path.read_bytes() == b"previous output" for path in outputs.values())


def test_model_mode_validates_full_rgba_roundtrip_before_any_publication(tmp_path, monkeypatch):
    args, outputs = setup_inputs(tmp_path)
    model = write_model(tmp_path, (2, 3), [(0, 0, 2, 3)])
    for path in outputs.values():
        path.write_bytes(b"previous output")
    original = commands.render_definition

    def corrupt(*args, **kwargs):
        result = original(*args, **kwargs)
        r, g, b, a = result.getpixel((0, 0))
        result.putpixel((0, 0), (r ^ 1, g, b, a))
        return result

    monkeypatch.setattr(commands, "render_definition", corrupt)
    assert run(args + ["--model", str(model)]) == 2
    assert all(path.read_bytes() == b"previous output" for path in outputs.values())


def test_source_center_sampling_and_large_source_grids_remain_valid_in_model_mode(tmp_path):
    source = Image.new("RGBA", (1024, 4), ZERO)
    source.putpixel((601, 3), (7, 8, 9, 0))
    sample = Image.new("RGBA", (1, 1), (7, 8, 9, 0))
    model = write_model(tmp_path, sample.size, [(0, 0, 1, 1)])
    args, outputs = setup_inputs(tmp_path, sample, source)
    args[args.index("--source-grid") + 1] = "all=512x2"
    assert run(args + ["--model", str(model)]) == 0
    definition = json.loads(outputs["target.json"].read_text(encoding="utf-8"))
    assert definition["pixels"] == [{"source_slot": "all", "source": [300, 1], "destination": [0, 0]}]


def test_face_cost_merges_after_clipping_and_empty_faces_do_not_offset_added_quads():
    # T shape: global row merge is 3 selections; left 2x3 face merges into one.
    regions = [
        {"source_slot": "all", "source": [4, 5, 2, 1], "destination": [0, 0, 2, 1]},
        {"source_slot": "all", "source": [4, 6, 3, 1], "destination": [0, 1, 3, 1]},
        {"source_slot": "all", "source": [4, 7, 2, 1], "destination": [0, 2, 2, 1]},
    ]
    cost = geometry_cost([(0, 0, 2, 3), (0, 0, 3, 3), (10, 10, 1, 1)], regions, [])
    assert (cost["original_faces"], cost["output_fragments"], cost["max_pieces_per_face"],
            cost["additional_quads"], cost["raw_mapping_intersections"]) == (3, 4, 3, 2, 6)


def test_model_with_only_background_used_space_does_not_report_unused_color_as_unmatched(tmp_path):
    sample = Image.new("RGBA", (2, 1), COLORS["F"])
    sample.putpixel((0, 0), ZERO)
    model = write_model(tmp_path, sample.size, [(0, 0, 1, 1)])
    args, outputs = setup_inputs(tmp_path, sample, letter_image(["A"]))
    assert run(args + ["--model", str(model)]) == 0
    report = read_report(outputs)
    assert (report["resolved_pixels"], report["background_pixels"], report["unused_pixels"],
            report["unmatched_pixels"]) == (0, 1, 1, 0)
    assert not report["target_written"] and not outputs["target.json"].exists()


def test_authored_spider_fuzzy_draft_recovers_large_regions_without_resolving_ties(tmp_path):
    fixtures = Path(__file__).resolve().parents[2] / "test-resources/fixtures/connected_spider"
    model = fixtures.parent / "models/spider_body.bbmodel"
    with Image.open(fixtures / "spider_andesite_authored.png") as image:
        sample = image.convert("RGBA")
    with Image.open(fixtures / "andesite_casing_connected.png") as image:
        source = image.convert("RGBA")
    args, outputs = setup_inputs(tmp_path, sample, source)
    assert run(args + ["--model", str(model), "--rgb-rmse", "48", "--rgb-max-error", "128"]) == 3
    report = read_report(outputs)
    assert report["resolved_pixels"] == 336
    assert report["ambiguous_pixels"] == 982 and report["unmatched_pixels"] == 10
    assert report["unused_pixels"] == 704 and report["background_pixels"] == 16
    definition = json.loads(outputs["target.json"].read_text())
    assert len(definition["regions"]) == 11 and definition["pixels"] == []
    assert max(r["destination"][2] * r["destination"][3] for r in definition["regions"]) == 60
    assert report["model_geometry_cost"]["output_fragments"] == 11  # partial coverage, not 66 faces rendered
    assert report["sample_error"]["rgb_rmse"] <= 48 and report["sample_error"]["rgb_max_error"] <= 128
    for material in ("biotech", "explosion_proof"):
        with Image.open(fixtures / f"{material}_casing_connected.png") as other:
            rendered = render_definition(definition, {"all": other.convert("RGBA")})
        assert sum(rendered.getpixel((x, y))[3] != 0 for y in range(32) for x in range(64)) == 336

