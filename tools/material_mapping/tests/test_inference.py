import pytest
from PIL import Image

from tools.material_mapping.inference import infer_mapping as infer
from tools.material_mapping.compression import compress_pixels
from tools.material_mapping.sampling import render_definition
from tools.tests.support import coordinate_image


from tools.tests.support import COLORS, ZERO, letter_image


@pytest.mark.parametrize("transform,rows,coordinates", [
    ("identity", ["ABC", "DEF"], [(4, 3), (5, 3), (6, 3), (4, 4), (5, 4), (6, 4)]),
    ("rotate_90", ["DA", "EB", "FC"], [(4, 4), (4, 3), (5, 4), (5, 3), (6, 4), (6, 3)]),
    ("rotate_180", ["FED", "CBA"], [(6, 4), (5, 4), (4, 4), (6, 3), (5, 3), (4, 3)]),
    ("rotate_270", ["CF", "BE", "AD"], [(6, 3), (6, 4), (5, 3), (5, 4), (4, 3), (4, 4)]),
    ("mirror_x", ["CBA", "FED"], [(6, 3), (5, 3), (4, 3), (6, 4), (5, 4), (4, 4)]),
    ("mirror_y", ["DEF", "ABC"], [(4, 4), (5, 4), (6, 4), (4, 3), (5, 3), (6, 3)]),
])
def test_unique_nonsquare_patch_recovers_source_coordinates(transform, rows, coordinates):
    source = Image.new("RGBA", (9, 7), ZERO)
    source.paste(letter_image(["ABC", "DEF"]), (4, 3))
    result = infer(letter_image(rows), {"all": source}, {"all": source.size})

    assert result.issues == []
    pixels = sorted(result.pixels, key=lambda pixel: (pixel.destination_y, pixel.destination_x))
    assert [(p.source_x, p.source_y) for p in pixels] == coordinates
    assert {p.transform for p in pixels} == {transform}
    compressed = compress_pixels(result.pixels)
    other_material = coordinate_image(9, 7, 79)
    definition = {"size": list(letter_image(rows).size), "source_grids": {"all": [9, 7]},
                  "regions": compressed.regions, "pixels": compressed.pixels}
    rendered = render_definition(definition, {"all": other_material})
    assert [rendered.getpixel((x, y)) for y in range(rendered.height) for x in range(rendered.width)] == [
        other_material.getpixel(coordinate) for coordinate in coordinates]


def test_spatial_match_resolves_repeated_colors_without_guessing_each_pixel():
    patch = letter_image(["ABA", "CAD"])
    source = Image.new("RGBA", (8, 7), ZERO)
    source.paste(patch, (3, 2))
    result = infer(patch, {"all": source}, {"all": source.size})

    assert result.issues == []
    assert len(result.pixels) == 6
    assert {(p.destination_x, p.destination_y, p.source_x, p.source_y) for p in result.pixels} == {
        (0, 0, 3, 2), (1, 0, 4, 2), (2, 0, 5, 2), (0, 1, 3, 3), (1, 1, 4, 3), (2, 1, 5, 3)}


def test_repeated_patches_remain_one_ambiguous_region():
    patch = letter_image(["ABC", "DEF"])
    source = Image.new("RGBA", (11, 5), ZERO)
    source.paste(patch, (1, 1))
    source.paste(patch, (7, 1))
    result = infer(patch, {"all": source}, {"all": source.size})

    assert result.pixels == []
    assert len(result.issues) == 1
    issue = result.issues[0]
    assert issue.reason == "ambiguous" and issue.destination == (0, 0, 3, 2)
    assert {c.source for c in issue.candidates} == {(1, 1, 3, 2), (7, 1, 3, 2)}
    assert not issue.search_truncated


def test_degenerate_transforms_are_not_false_ambiguities():
    sample = letter_image(["A"])
    result = infer(sample, {"all": sample}, {"all": (1, 1)})
    assert len(result.pixels) == 1 and result.issues == []


def test_partial_inference_retains_unique_pixels_and_reports_hand_edited_color():
    sample = letter_image(["ACB"])
    source = letter_image(["AB"])
    result = infer(sample, {"all": source}, {"all": (2, 1)})

    assert [(p.destination_x, p.source_x) for p in result.pixels] == [(0, 0), (2, 1)]
    assert [(i.reason, i.destination) for i in result.issues] == [("unmatched", (1, 0, 1, 1))]


def test_identical_pixels_across_slots_are_reported_not_preferred():
    sample = letter_image(["A"])
    result = infer(sample, {"side": sample, "end": sample}, {"side": (1, 1), "end": (1, 1)})
    assert result.pixels == []
    assert {c.slot for c in result.issues[0].candidates} == {"side", "end"}


def test_transparent_margins_stay_unmapped_and_hidden_rgb_is_not_discarded():
    sample = Image.new("RGBA", (5, 4), ZERO)
    sample.putpixel((3, 2), (7, 8, 9, 0))
    source = Image.new("RGBA", (1, 1), (7, 8, 9, 0))
    result = infer(sample, {"all": source}, {"all": (1, 1)})
    assert [(p.destination_x, p.destination_y) for p in result.pixels] == [(3, 2)]
    assert result.background_pixels == 19 and result.issues == []


@pytest.mark.parametrize("pixel", [(17, 23, 29, 81), (18, 23, 29, 80)])
def test_no_alpha_or_color_tolerance(pixel):
    result = infer(Image.new("RGBA", (1, 1), pixel), {"all": letter_image(["A"])}, {"all": (1, 1)})
    assert result.pixels == [] and result.issues[0].reason == "unmatched"


def test_scaled_sources_use_existing_java_center_samples():
    source = Image.new("RGBA", (4, 2), COLORS["F"])
    source.putpixel((1, 1), COLORS["A"])
    source.putpixel((3, 1), COLORS["B"])
    result = infer(letter_image(["AB"]), {"all": source}, {"all": (2, 1)})
    assert result.issues == []
    assert [(p.source_x, p.source_y) for p in result.pixels] == [(0, 0), (1, 0)]


def test_many_candidates_are_bounded_and_marked_as_lower_bound():
    source = Image.new("RGBA", (16, 16), COLORS["A"])
    result = infer(letter_image(["A"]), {"all": source}, {"all": (16, 16)}, candidate_limit=2)
    assert result.pixels == []
    assert len(result.issues[0].candidates) == 3
    assert result.issues[0].search_truncated


def test_source_grid_can_exceed_target_dimension_limit():
    source = Image.new("RGBA", (512, 512), ZERO)
    source.putpixel((300, 400), COLORS["A"])
    result = infer(letter_image(["A"]), {"all": source}, {"all": (512, 512)})
    assert [(p.source_x, p.source_y) for p in result.pixels] == [(300, 400)]


def test_empty_sample_is_not_a_valid_java_target():
    with pytest.raises(ValueError, match="empty|zero RGBA"):
        infer(Image.new("RGBA", (2, 2), ZERO), {"all": letter_image(["A"])}, {"all": (1, 1)})


def test_symmetric_patch_is_ambiguous_when_source_coordinates_differ():
    sample = letter_image(["ABA"])
    result = infer(sample, {"all": sample}, {"all": sample.size})
    assert not result.pixels and len(result.issues[0].candidates) == 2
    assert {c.transform for c in result.issues[0].candidates} == {"identity", "rotate_180"}


def test_background_holes_never_become_mappings_for_other_materials():
    sample = letter_image(["ABC", "DEF"])
    sample.putpixel((1, 0), ZERO)
    result = infer(sample, {"all": sample}, {"all": sample.size})
    assert result.background_pixels == 1 and not result.issues
    assert {(p.destination_x, p.destination_y) for p in result.pixels} == {(0, 0), (2, 0), (0, 1), (1, 1), (2, 1)}


def test_every_pixel_has_exactly_one_status_in_deterministic_mixed_samples():
    import random
    rng = random.Random(20260915)
    source = letter_image(["ABB", "DEE"])
    for _ in range(30):
        sample = Image.new("RGBA", (7, 5), ZERO)
        sample.putdata([rng.choice([ZERO, *COLORS.values()]) for _ in range(35)])
        result = infer(sample, {"all": source}, {"all": source.size})
        covered = {(p.destination_x, p.destination_y) for p in result.pixels}
        assert len(covered) == len(result.pixels)
        for issue in result.issues:
            x, y, w, h = issue.destination
            area = {(x + dx, y + dy) for dy in range(h) for dx in range(w)}
            assert not covered & area
            covered |= area
        assert covered == {(x, y) for y in range(5) for x in range(7) if sample.getpixel((x, y)) != ZERO}
