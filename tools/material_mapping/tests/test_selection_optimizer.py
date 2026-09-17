from functools import lru_cache

import pytest

from tools.material_mapping.compression import MappedPixel, compress_pixels
from tools.material_mapping.sampling import render_definition, render_pixels
from tools.material_mapping.selection_optimizer import optimize_selections
from tools.tests.support import coordinate_image


def optimize(pixels, seconds=2):
    return optimize_selections(pixels, time_limit_seconds=seconds)


def mapped(rows):
    return [MappedPixel(x, y, "all", x + 4, y + 5)
            for y, row in enumerate(rows) for x, occupied in enumerate(row) if occupied == "1"]


def count(compressed):
    return len(compressed.regions) + len(compressed.pixels)


def test_minimum_partition_improves_over_row_first_compression():
    pixels = mapped(["110", "111", "110"])
    assert count(compress_pixels(pixels)) == 3
    result = optimize(pixels)
    assert count(result.compressed) == 2
    assert result.optimal and result.lower_bound == 2
    assert result.input_selections == 3 and not result.timed_out


def test_zero_search_budget_returns_valid_incumbent_without_claiming_optimality():
    pixels = mapped(["110", "111", "110"])
    result = optimize(pixels, seconds=0)
    assert count(result.compressed) == 3
    assert result.lower_bound == 2
    assert not result.optimal and result.timed_out


def test_degenerate_transform_labels_do_not_prevent_a_valid_merge():
    pixels = [MappedPixel(x, y, "all", 6 - x, 5 + y, "identity") for y in range(2) for x in range(3)]
    result = optimize(pixels)
    assert count(result.compressed) == 1 and result.optimal
    assert result.compressed.regions[0]["transform"] == "mirror_x"


def test_optimization_never_discards_pixels_changes_uvs_or_merges_slots():
    pixels = mapped(["1101", "1111", "1100"])
    pixels.append(MappedPixel(5, 1, "side", 7, 8))
    result = optimize(pixels)
    sources = {"all": coordinate_image(16, 16, 79), "side": coordinate_image(16, 16, 13)}
    definition = {"size": [6, 3], "source_grids": {"all": [16, 16], "side": [16, 16]},
                  "regions": result.compressed.regions, "pixels": result.compressed.pixels}
    assert render_definition(definition, sources).tobytes() == render_pixels(
        pixels, (6, 3), sources, {"all": (16, 16), "side": (16, 16)}).tobytes()
    assert count(result.compressed) <= count(compress_pixels(pixels))


def test_small_masks_match_an_independent_exhaustive_rectangle_cover_oracle():
    width, height = 3, 3
    rectangles = []
    for y in range(height):
        for x in range(width):
            for bottom in range(y + 1, height + 1):
                for right in range(x + 1, width + 1):
                    rectangles.append(sum(1 << (dy * width + dx)
                                          for dy in range(y, bottom) for dx in range(x, right)))

    @lru_cache(None)
    def minimum(mask):
        if not mask:
            return 0
        pivot = mask & -mask
        return 1 + min(minimum(mask ^ rect) for rect in rectangles if rect & pivot and rect & mask == rect)

    for mask in range(1, 1 << (width * height)):
        rows = ["".join("1" if mask & (1 << (y * width + x)) else "0" for x in range(width))
                for y in range(height)]
        result = optimize(mapped(rows))
        assert result.optimal, rows
        assert count(result.compressed) == minimum(mask), rows
        assert result.lower_bound == minimum(mask)


def test_empty_and_full_rectangles_have_trivial_proofs():
    for pixels, expected in [([], 0), (mapped(["111", "111"]), 1)]:
        result = optimize(pixels, seconds=0)
        assert result.optimal and result.lower_bound == expected
        assert count(result.compressed) == expected


@pytest.mark.parametrize("seconds", [-1, float("inf"), float("nan")])
def test_invalid_search_limits_fail(seconds):
    with pytest.raises(ValueError, match="finite.*non-negative"):
        optimize(mapped(["1"]), seconds)


@pytest.mark.parametrize("horizontal,vertical", [
    ((1, 0), (0, 1)), ((0, -1), (1, 0)), ((-1, 0), (0, -1)),
    ((0, 1), (-1, 0)), ((-1, 0), (0, 1)), ((1, 0), (0, -1)),
])
def test_optimized_holey_partitions_preserve_each_of_the_six_transform_coordinate_maps(horizontal, vertical):
    pixels = [MappedPixel(p.destination_x, p.destination_y, "all",
                          8 + p.destination_x * horizontal[0] + p.destination_y * vertical[0],
                          8 + p.destination_x * horizontal[1] + p.destination_y * vertical[1])
              for p in mapped(["110", "111", "110"])]
    result = optimize(pixels)
    assert result.optimal and count(result.compressed) == 2
    source = coordinate_image(16, 16, 79)
    definition = {"size": [3, 3], "source_grids": {"all": [16, 16]},
                  "regions": result.compressed.regions, "pixels": result.compressed.pixels}
    assert render_definition(definition, {"all": source}).tobytes() == render_pixels(
        pixels, (3, 3), {"all": source}, {"all": (16, 16)}).tobytes()
