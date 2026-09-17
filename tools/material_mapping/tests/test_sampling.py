import pytest

from tools.material_mapping.compression import MappedPixel
from tools.material_mapping.sampling import render_definition, render_pixels
from tools.tests.support import coordinate_image


@pytest.mark.parametrize("scale,expected", [
    (1, [(0, 0, 0, 64), (1, 3, 48, 82)]),
    (2, [(1, 3, 48, 82), (3, 9, 144, 118)]),
    (3, [(1, 3, 48, 82), (4, 12, 192, 136)]),
])
def test_integer_scaled_sources_sample_java_cell_centers(scale: int, expected: list) -> None:
    # Literal coordinates: scale 2 samples (1,1)/(3,3); scale 3 samples (1,1)/(4,4).
    # The texture varies inside each logical cell, unlike a nearest-neighbour upscaled solid tile.
    sources = {"all": coordinate_image(2 * scale, 2 * scale)}
    mapped = [MappedPixel(0, 0, "all", 0, 0), MappedPixel(1, 0, "all", 1, 1)]
    definition = {
        "size": [2, 1], "source_grids": {"all": [2, 2]},
        "regions": [{"source_slot": "all", "source": [0, 0, 1, 1],
                     "destination": [0, 0, 1, 1], "transform": "identity"}],
        "pixels": [{"source_slot": "all", "source": [1, 1], "destination": [1, 0]}],
    }
    for image in (render_pixels(mapped, (2, 1), sources, {"all": (2, 2)}),
                  render_definition(definition, sources)):
        assert [image.getpixel((x, 0)) for x in range(2)] == expected
