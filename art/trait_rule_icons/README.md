# JEI trait rule icons

Three transparent, 18×18, black-and-white pixel sprites. The JEI rule widget
renders them at their native size; the existing rule and condition tooltips apply.

| File | Meaning | Construction |
| --- | --- | --- |
| `presence.png` | A retained carrier is sufficient | Checked box |
| `threshold.png` | Coverage must reach a threshold | Solid fill bar with a protruding threshold marker |
| `scaled.png` | Effect strength grows with coverage | Filled pie with a missing upper-right quarter |

Output: `src/main/resources/assets/create_biotech/textures/gui/sprites/jei/trait_rule/`.

Requires Python and Pillow. Regenerate the textures and `preview.png` with:

```powershell
python art/trait_rule_icons/draw_icons.py
```

All pixels are authored by the script without AI image generation, antialiasing,
or resampling. Preview enlargement uses nearest-neighbor scaling.

## Reference and attribution

The checked box is adapted from **1-bit Pixel Icons by
Nikoichu**, released under CC0 1.0 Universal:

- Author/source: https://nikoichu.itch.io/pixel-icons
- License: https://creativecommons.org/publicdomain/zero/1.0/
- Local reference root: `D:/Files/ponderer/ref/ponderer-nikoichu-icons/Sprites_Cropped/`
- Presence: `Software_Checkbox_Checkmarked_Yes_Done_Todo.png`
- Threshold construction reference: `Software_Battery_Power_Level_2.png`;
  redrawn horizontally with a solid fill, no battery cap and an extended threshold marker.
- Scaled: a pie drawn directly at the native pixel resolution.

License information was also recorded by the local Ponderer project in
`Common/src/main/resources/assets/ponderer/textures/gui/action_icons.LICENSE.txt`.
The checked box retains its original black and white pixels,
centered in an 18×18 transparent canvas. The script embeds its mask so
regeneration does not require the reference library.
