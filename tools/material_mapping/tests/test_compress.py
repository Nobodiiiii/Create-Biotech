from __future__ import annotations

from tools.material_mapping.compression import MappedPixel, compress_pixels


def pixels(x: int, y: int, width: int, height: int, *, slot: str = "all",
           source_x: int = 0, source_y: int = 0, transform: str = "identity") -> list[MappedPixel]:
    return [
        MappedPixel(x + dx, y + dy, slot, source_x + dx, source_y + dy, transform)
        for dy in range(height)
        for dx in range(width)
    ]


def test_full_16_square_compresses_to_one_rectangle() -> None:
    result = compress_pixels(pixels(0, 0, 16, 16))

    assert result.regions == [{
        "source_slot": "all", "source": [0, 0, 16, 16],
        "destination": [0, 0, 16, 16], "transform": "identity",
    }]
    assert result.pixels == []


def test_adjacent_same_mapping_strips_merge_vertically() -> None:
    mapped = pixels(4, 2, 8, 1, source_x=2, source_y=3)
    mapped += pixels(4, 3, 8, 1, source_x=2, source_y=4)

    result = compress_pixels(mapped)

    assert result.regions == [{
        "source_slot": "all", "source": [2, 3, 8, 2],
        "destination": [4, 2, 8, 2], "transform": "identity",
    }]


def test_different_slots_and_transforms_never_merge() -> None:
    mapped = pixels(0, 0, 2, 1, slot="side")
    mapped += pixels(2, 0, 2, 1, slot="end", source_x=2)
    mapped += pixels(4, 0, 2, 1, source_x=4, transform="mirror_y")

    result = compress_pixels(mapped)

    assert [region["source_slot"] for region in result.regions] == ["side", "end", "all"]
    assert [region["transform"] for region in result.regions] == ["identity", "identity", "mirror_y"]


def test_l_shape_has_deterministic_row_run_decomposition() -> None:
    mapped = pixels(0, 0, 3, 1) + pixels(0, 1, 1, 2, source_y=1)

    result = compress_pixels(mapped)

    assert result.regions == [
        {
            "source_slot": "all", "source": [0, 0, 3, 1],
            "destination": [0, 0, 3, 1], "transform": "identity",
        },
        {
            "source_slot": "all", "source": [0, 1, 1, 2],
            "destination": [0, 1, 1, 2], "transform": "identity",
        },
    ]


def test_isolated_correction_remains_sparse_pixel() -> None:
    result = compress_pixels([MappedPixel(9, 7, "end", 3, 5, "identity")])

    assert result.regions == []
    assert result.pixels == [{"source_slot": "end", "source": [3, 5], "destination": [9, 7]}]
