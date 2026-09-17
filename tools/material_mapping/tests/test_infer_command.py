import json
from pathlib import Path

import pytest
from PIL import Image

from tools.material_mapping import cli as commands
from tools.material_mapping.sampling import render_definition
from tools.tests.support import COLORS, ZERO, letter_image
from tools.tests.support import coordinate_image, setup_inputs, read_report


FIXTURES = Path(__file__).resolve().parents[2] / "test-resources/fixtures/definitions/python_inference"


def test_complete_inference_compresses_and_round_trips_without_input_paths(tmp_path):
    args, outputs = setup_inputs(tmp_path)
    assert commands.main(args) == 0
    definition = json.loads(outputs["target.json"].read_text(encoding="utf-8"))
    assert definition == {
        "schema": 1, "size": [2, 3], "source_grids": {"all": [3, 2]},
        "regions": [{"source_slot": "all", "source": [0, 0, 3, 2],
                     "destination": [0, 0, 2, 3], "transform": "rotate_90"}],
        "pixels": [], "computed_cost": {"max_pieces_per_source_quad": 1, "additional_quads": 0}}
    assert definition == json.loads((FIXTURES / "rotated.json").read_text(encoding="utf-8"))
    with Image.open(outputs["preview.png"]) as preview:
        assert preview.tobytes() == letter_image(["DA", "EB", "FC"]).tobytes()
    report = read_report(outputs)
    assert report["status"] == "complete" and report["target_written"]
    assert report["resolved_pixels"] == 6 and report["unresolved_regions"] == []
    # A different material must follow the inferred UV, not retain sample colors.
    different = letter_image(["FED", "CBA"])
    assert render_definition(definition, {"all": different}).tobytes() == letter_image(["CF", "BE", "AD"]).tobytes()


def test_draft_exports_only_resolved_part_with_colored_diagnostics(tmp_path):
    args, outputs = setup_inputs(tmp_path, letter_image(["ACB"]), letter_image(["ABB"]))
    assert commands.main(args) == 3
    report = read_report(outputs)
    assert report["status"] == "draft" and report["target_written"]
    assert (report["resolved_pixels"], report["ambiguous_pixels"], report["unmatched_pixels"]) == (1, 1, 1)
    assert json.loads(outputs["target.json"].read_text(encoding="utf-8")) == json.loads(
        (FIXTURES / "draft.json").read_text(encoding="utf-8"))
    assert [i["reason"] for i in report["unresolved_regions"]] == ["unmatched", "ambiguous"]
    assert {tuple(c["source"]) for c in report["unresolved_regions"][1]["candidates"]} == {(1, 0, 1, 1), (2, 0, 1, 1)}
    with Image.open(outputs["preview.png"]) as preview, Image.open(outputs["mask.png"]) as mask:
        assert [preview.getpixel((x, 0)) for x in range(3)] == [COLORS["A"], ZERO, ZERO]
        assert [mask.getpixel((x, 0)) for x in range(3)] == [ZERO, (255, 0, 0, 255), (255, 191, 0, 255)]


@pytest.mark.parametrize("existing", [False, True])
def test_fully_unresolved_does_not_create_or_overwrite_target(tmp_path, existing, capsys):
    args, outputs = setup_inputs(tmp_path, letter_image(["C"]), letter_image(["AB"]))
    if existing:
        outputs["target.json"].write_bytes(b"previous target")
    assert commands.main(args) == 3
    assert "no target written" in capsys.readouterr().out
    assert read_report(outputs)["target_written"] is False
    if existing:
        assert outputs["target.json"].read_bytes() == b"previous target"
    else:
        assert not outputs["target.json"].exists()
    assert all(path.exists() for name, path in outputs.items() if name != "target.json")


def test_report_candidates_are_bounded_honest_and_deterministic(tmp_path):
    args, outputs = setup_inputs(tmp_path, letter_image(["A"]), Image.new("RGBA", (16, 16), COLORS["A"]))
    args += ["--max-candidates", "2"]
    assert commands.main(args) == 3
    issue = read_report(outputs)["unresolved_regions"][0]
    assert len(issue["candidates"]) == 2 and issue["candidate_count"] == 3
    assert issue["candidate_count_is_lower_bound"] and issue["candidates_truncated"]
    first = {name: path.read_bytes() for name, path in outputs.items() if path.exists()}
    assert commands.main(args) == 3
    assert first == {name: path.read_bytes() for name, path in outputs.items() if path.exists()}


def test_complete_outputs_are_repeatable_and_inputs_unchanged(tmp_path):
    args, outputs = setup_inputs(tmp_path)
    inputs = {path: path.read_bytes() for path in tmp_path.glob("*.png")}
    assert commands.main(args) == 0
    first = {name: path.read_bytes() for name, path in outputs.items()}
    assert commands.main(args) == 0
    assert first == {name: path.read_bytes() for name, path in outputs.items()}
    assert all(path.read_bytes() == data for path, data in inputs.items())


@pytest.mark.parametrize("seconds,expected,optimal", [(0, 3, False), (2, 2, True)])
def test_cli_minimizes_selections_without_sacrificing_coverage(tmp_path, seconds, expected, optimal):
    source = coordinate_image(16, 16)
    sample = Image.new("RGBA", (3, 3), ZERO)
    for y, row in enumerate(["110", "111", "110"]):
        for x, occupied in enumerate(row):
            if occupied == "1":
                sample.putpixel((x, y), source.getpixel((4 + x, 5 + y)))
    args, outputs = setup_inputs(tmp_path, sample, source)
    assert commands.main(args + ["--optimize-seconds", str(seconds)]) == 0
    report = read_report(outputs)
    optimization = report["selection_optimization"]
    assert optimization["scope"] == "fixed_resolved_uv_mapping"
    assert optimization["input_selections"] == 3 and optimization["output_selections"] == expected
    assert optimization["optimal"] is optimal and optimization["lower_bound"] == 2
    assert report["resolved_pixels"] == 7 and report["background_pixels"] == 2
    if optimal:
        assert json.loads(outputs["target.json"].read_text(encoding="utf-8")) == json.loads(
            (FIXTURES / "optimized_partition.json").read_text(encoding="utf-8"))
    with Image.open(outputs["preview.png"]) as preview:
        assert preview.tobytes() == sample.tobytes()


@pytest.mark.parametrize("corruption", ["rgb", "alpha", "mode", "size"])
def test_preview_corruption_is_detected_before_any_publication(tmp_path, monkeypatch, corruption):
    args, outputs = setup_inputs(tmp_path)
    for path in outputs.values():
        path.write_bytes(b"previous output")
    original = commands.render_definition

    def corrupt(*args, **kwargs):
        image = original(*args, **kwargs)
        if corruption == "mode":
            return image.convert("RGB")
        if corruption == "size":
            return image.crop((0, 0, 1, 1))
        color = list(image.getpixel((0, 0)))
        color[0 if corruption == "rgb" else 3] ^= 1
        image.putpixel((0, 0), tuple(color))
        return image

    monkeypatch.setattr(commands, "render_definition", corrupt)
    assert commands.main(args) == 2
    assert all(path.read_bytes() == b"previous output" for path in outputs.values())


@pytest.mark.parametrize("change,expected", [
    ("missing-grid", "source grid"), ("bad-scale", "grid scale"),
    ("duplicate-slot", "once"), ("unknown-grid", "declared slot"),
    ("zero-limit", "positive integer"), ("output-alias", "distinct"),
    ("input-alias", "input"), ("empty-sample", "empty"), ("oversize-sample", "257x1"),
    ("invalid-optimization-budget", "finite and non-negative"),
])
def test_invalid_input_preserves_outputs(tmp_path, capsys, change, expected):
    args, outputs = setup_inputs(tmp_path)
    for path in outputs.values():
        path.write_bytes(b"previous output")
    if change == "missing-grid":
        start = args.index("--source-grid")
        del args[start:start + 2]
    elif change == "bad-scale":
        args[args.index("--source-grid") + 1] = "all=2x2"
    elif change == "duplicate-slot":
        args += ["--source", f"all={tmp_path / 'source.png'}"]
    elif change == "unknown-grid":
        args += ["--source-grid", "side=1x1"]
    elif change == "zero-limit":
        args += ["--max-candidates", "0"]
    elif change == "output-alias":
        args[args.index("--report") + 1] = str(outputs["target.json"])
    elif change == "input-alias":
        args[args.index("--report") + 1] = str(tmp_path / "sample.png")
    elif change == "empty-sample":
        Image.new("RGBA", (1, 1), ZERO).save(tmp_path / "sample.png")
    elif change == "oversize-sample":
        Image.new("RGBA", (257, 1), COLORS["A"]).save(tmp_path / "sample.png")
    elif change == "invalid-optimization-budget":
        args += ["--optimize-seconds", "nan"]
    assert commands.main(args) == 2
    assert expected in capsys.readouterr().err
    assert all(path.read_bytes() == b"previous output" for path in outputs.values())

