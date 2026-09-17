"""RGB tolerance must recover spatial UVs, not nearest-color pixel mosaics."""
import json
import random
from pathlib import Path

from PIL import Image
import pytest

from tools.material_mapping import cli as commands
from tools.material_mapping.compression import compress_pixels
from tools.material_mapping.inference import infer_mapping, TRANSPOSE
from tools.material_mapping.sampling import render_definition, render_pixels
from tools.tests.support import coordinate_image
from tools.tests.support import setup_inputs, read_report


def texture(size=(8, 6)):
    rng = random.Random(9173)
    image = Image.new("RGBA", size)
    image.putdata([tuple(rng.randrange(30, 210) for _ in range(3)) + (255,)
                   for _ in range(size[0] * size[1])])
    return image


def shift_rgb(image, delta):
    result = image.copy()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = image.getpixel((x, y))
            result.putpixel((x, y), (r + delta, g, b, a))
    return result


def infer(sample, source, **kwargs):
    return infer_mapping(sample, {"all": source}, {"all": source.size},
                         rgb_rmse=6, rgb_max_error=12, **kwargs)


@pytest.mark.parametrize("transform", list(TRANSPOSE))
def test_small_rgb_edits_recover_one_large_rectangle_and_transfer_uvs(transform):
    patch = texture()
    source = Image.new("RGBA", (16, 12))
    source.paste(patch, (3, 4))
    expected = patch if TRANSPOSE[transform] is None else patch.transpose(TRANSPOSE[transform])
    sample = shift_rgb(expected, 3)
    assert not infer_mapping(sample, {"all": source}, {"all": source.size}).pixels
    result = infer(sample, source)
    assert not result.issues and len(result.pixels) == 48
    compressed = compress_pixels(result.pixels)
    assert compressed.pixels == [] and len(compressed.regions) == 1
    assert compressed.regions[0]["source"] == [3, 4, 8, 6]
    assert compressed.regions[0]["transform"] == transform
    # Independent coordinate-coded material catches wrong source positions.
    other = coordinate_image(16, 12, 37)
    other_patch = other.crop((3, 4, 11, 10))
    if TRANSPOSE[transform] is not None:
        other_patch = other_patch.transpose(TRANSPOSE[transform])
    assert render_pixels(result.pixels, sample.size, {"all": other}, {"all": other.size}).tobytes() == other_patch.tobytes()


def test_peak_limit_does_not_hide_one_bad_pixel_in_a_large_rectangle():
    source = texture()
    sample = source.copy()
    r, g, b, a = sample.getpixel((0, 0))
    sample.putpixel((0, 0), (r + 30, g, b, a))
    result = infer(sample, source)
    assert all((p.destination_x, p.destination_y) != (0, 0) for p in result.pixels)
    assert result.issues and len(result.pixels) >= 40


def test_whole_rectangle_rms_is_a_separate_limit():
    source = texture()
    result = infer(shift_rgb(source, 8), source)
    assert not result.pixels  # max 12 alone would admit the whole patch


def test_alpha_must_match_exactly_even_with_large_rgb_tolerance():
    source = Image.new("RGBA", (1, 1), (60, 70, 80, 255))
    result = infer(Image.new("RGBA", (1, 1), (60, 70, 80, 254)), source)
    assert not result.pixels and result.issues[0].reason == "unmatched"


def test_display_candidate_limit_does_not_hide_a_later_better_match():
    patch = texture((4, 3))
    source = Image.new("RGBA", (28, 4))
    for x, delta in [(0, 4), (6, 5), (12, 3), (20, 0)]:
        source.paste(shift_rgb(patch, delta), (x, 0))
    result = infer(patch, source, candidate_limit=1)
    assert not result.issues and len(result.pixels) == 12
    assert {(p.source_x - p.destination_x, p.source_y - p.destination_y) for p in result.pixels} == {(20, 0)}


@pytest.mark.parametrize("delta,margin", [(0, 0), (1, 1), (2, 2)])
def test_tied_or_close_whole_patch_candidates_remain_one_draft_region(delta, margin):
    patch = texture((4, 3))
    source = Image.new("RGBA", (10, 3))
    source.paste(patch, (0, 0))
    source.paste(shift_rgb(patch, delta), (6, 0))
    result = infer(patch, source, rgb_ambiguity_margin=margin)
    assert not result.pixels and len(result.issues) == 1
    assert result.issues[0].reason == "ambiguous"
    assert result.issues[0].destination == (0, 0, 4, 3)


def test_transparent_hole_is_never_filled_by_fuzzy_source_sampling():
    source = texture()
    sample = shift_rgb(source, 3)
    sample.putpixel((3, 2), (0, 0, 0, 0))
    result = infer(sample, source)
    assert result.background_pixels == 1 and len(result.pixels) == 47
    preview = render_pixels(result.pixels, sample.size, {"all": source}, {"all": source.size})
    assert preview.getpixel((3, 2)) == (0, 0, 0, 0)


def test_exactly_two_close_candidates_are_not_reported_as_search_truncation():
    patch = texture((4, 3))
    source = Image.new("RGBA", (10, 3))
    source.paste(patch, (0, 0))
    source.paste(patch, (6, 0))
    result = infer(patch, source, candidate_limit=1)
    assert len(result.issues[0].candidates) == 2
    assert not result.issues[0].search_truncated


def test_many_close_candidates_are_bounded_without_hiding_ambiguity():
    source = Image.new("RGBA", (16, 16), (70, 80, 90, 255))
    sample = Image.new("RGBA", (1, 1), (72, 80, 90, 255))
    result = infer(sample, source, candidate_limit=2)
    assert not result.pixels and len(result.issues[0].candidates) == 3
    assert result.issues[0].search_truncated


def test_fuzzy_uses_logical_cell_centers_for_scaled_sources():
    patch = texture((4, 3))
    source = Image.new("RGBA", (8, 6), (250, 250, 250, 255))
    for y in range(3):
        for x in range(4):
            source.putpixel((2 * x + 1, 2 * y + 1), patch.getpixel((x, y)))
    result = infer_mapping(shift_rgb(patch, 3), {"all": source}, {"all": (4, 3)},
                           rgb_rmse=6, rgb_max_error=12)
    assert len(result.pixels) == 12 and not result.issues
    assert render_pixels(result.pixels, (4, 3), {"all": source}, {"all": (4, 3)}).tobytes() == patch.tobytes()


def test_zero_rgb_thresholds_preserve_exact_result():
    patch = texture()
    sample = shift_rgb(patch, 3)
    assert infer_mapping(sample, {"all": patch}, {"all": patch.size}) == infer_mapping(
        sample, {"all": patch}, {"all": patch.size}, rgb_rmse=0, rgb_max_error=0)


def test_model_faces_retain_the_whole_rectangle_and_exclude_unused_space():
    source = texture()
    sample = Image.new("RGBA", (16, 12), (1, 2, 3, 255))
    sample.paste(shift_rgb(source, 3), (2, 3))
    result = infer(sample, source, face_rectangles=[(2, 3, 8, 6)] * 2)
    assert not result.issues and len(result.pixels) == 48
    assert result.unused_pixels == 144
    assert compress_pixels(result.pixels).regions[0]["destination"] == [2, 3, 8, 6]


def test_fuzzy_shared_face_conflicts_do_not_depend_on_face_order():
    # The whole face chooses source x=0; a single overlapping pixel chooses x=4.
    source = Image.new("RGBA", (5, 1))
    for x, color in enumerate([(50, 10, 20, 255), (100, 40, 80, 255),
                                (0, 0, 0, 0), (0, 0, 0, 0), (53, 10, 20, 255)]):
        source.putpixel((x, 0), color)
    sample = source.crop((0, 0, 2, 1))
    sample.putpixel((0, 0), (53, 10, 20, 255))
    faces = [(0, 0, 2, 1), (0, 0, 1, 1)]
    result = infer(sample, source, face_rectangles=faces)
    assert result == infer(sample, source, face_rectangles=list(reversed(faces)))
    assert [(p.destination_x, p.source_x) for p in result.pixels] == [(1, 1)]
    assert result.issues[0].reason == "ambiguous"


def call(args):
    try:
        return commands.main(args)
    except SystemExit as error:
        return error.code


def test_cli_fuzzy_preview_contains_source_pixels_and_reports_sample_error(tmp_path):
    source = texture()
    args, outputs = setup_inputs(tmp_path, shift_rgb(source, 3), source)
    args += ["--rgb-rmse", "6", "--rgb-max-error", "12"]
    assert call(args) == 0
    with Image.open(outputs["preview.png"]) as preview:
        assert preview.tobytes() == source.tobytes()
    report = read_report(outputs)
    assert report["matching"]["mode"] == "rgb_tolerant"
    assert report["matching"]["alpha"] == "exact"
    assert report["matching"]["rgb_rmse_limit"] == 6
    assert report["matching"]["rgb_max_error_limit"] == 12
    assert report["sample_error"] == {"evaluated_pixels": 48, "approximate_pixels": 48,
                                      "rgb_rmse": 3.0, "rgb_max_error": 3.0}
    definition = json.loads(outputs["target.json"].read_text())
    assert len(definition["regions"]) == 1 and definition["pixels"] == []
    assert "matching" not in definition
    first = {n: p.read_bytes() for n, p in outputs.items()}
    assert call(args) == 0
    assert first == {n: p.read_bytes() for n, p in outputs.items()}


@pytest.mark.parametrize("channel", [0, 3])
def test_fuzzy_tolerance_never_weakens_export_round_trip_or_publishes_on_failure(tmp_path, monkeypatch, channel):
    source = texture()
    args, outputs = setup_inputs(tmp_path, shift_rgb(source, 3), source)
    args += ["--rgb-rmse", "6", "--rgb-max-error", "12"]
    assert call(args) == 0
    for path in outputs.values():
        path.write_bytes(b"previous output")
    original = commands.render_definition

    def corrupt(*args, **kwargs):
        image = original(*args, **kwargs)
        color = list(image.getpixel((0, 0)))
        color[channel] ^= 1  # RGB is inside fuzzy limit, still corruption
        image.putpixel((0, 0), tuple(color))
        return image

    monkeypatch.setattr(commands, "render_definition", corrupt)
    assert call(args) == 2
    assert all(p.read_bytes() == b"previous output" for p in outputs.values())


@pytest.mark.parametrize("rmse,peak,margin", [(-1, 2, 1), (3, 2, 1), (1, 0, 1),
    (0, 1, 1), (float("nan"), 8, 1), (2, float("inf"), 1), (2, 8, -1), (2, 8, float("nan")), (500, 500, 1)])
def test_invalid_rgb_limits_fail_clearly(rmse, peak, margin):
    sample = texture()
    with pytest.raises(ValueError, match="RGB|rgb"):
        infer_mapping(sample, {"all": sample}, {"all": sample.size},
                      rgb_rmse=rmse, rgb_max_error=peak, rgb_ambiguity_margin=margin)


def test_fuzzy_cli_matches_shared_java_rotation_fixture(tmp_path):
    patch = texture()
    source = Image.new("RGBA", (16, 12))
    source.paste(patch, (3, 4))
    sample = shift_rgb(patch.transpose(Image.Transpose.ROTATE_270), 3)
    args, outputs = setup_inputs(tmp_path, sample, source)
    assert call(args + ["--rgb-rmse", "6", "--rgb-max-error", "12"]) == 0
    fixture = Path(__file__).resolve().parents[2] / "test-resources/fixtures/definitions/python_inference/fuzzy_rotated.json"
    assert json.loads(outputs["target.json"].read_text()) == json.loads(fixture.read_text())



