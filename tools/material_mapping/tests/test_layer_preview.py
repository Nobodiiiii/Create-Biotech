from PIL import Image

from tools.material_mapping.sampling import apply_texture_layers


def test_texture_layers_use_stable_index_order_and_cutout_alpha():
    base = Image.new("RGBA", (3, 1), (10, 20, 30, 255))
    first = Image.new("RGBA", (3, 1))
    first.putdata([(200, 0, 0, 255), (0, 0, 0, 0), (200, 0, 0, 255)])
    second = Image.new("RGBA", (1, 1), (0, 200, 0, 255))
    layers = [
        {"index": 10, "texture": "test:second", "grid": [1, 1], "destination": [1, 0, 1, 1]},
        {"index": 5, "texture": "test:first"},
        {"index": 10, "model": "test:hat", "part": "head"},
    ]

    result = apply_texture_layers(base, layers, {
        "test:first": first,
        "test:second": second,
    })

    assert [result.getpixel((x, 0)) for x in range(3)] == [
        (200, 0, 0, 255),
        (0, 200, 0, 255),
        (200, 0, 0, 255),
    ]


def test_texture_layer_samples_uniform_hd_logical_texels_and_scales_destination():
    base = Image.new("RGBA", (4, 2), (0, 0, 0, 255))
    texture = Image.new("RGBA", (4, 2))
    texture.paste((255, 0, 0, 255), (0, 0, 2, 2))
    texture.paste((0, 0, 255, 255), (2, 0, 4, 2))

    result = apply_texture_layers(base, [{
        "index": 0,
        "texture": "test:hd",
        "grid": [2, 1],
        "source": [0, 0, 2, 1],
        "destination": [0, 0, 4, 2],
    }], {"test:hd": texture})

    assert [result.getpixel((x, y)) for y in range(2) for x in range(4)] == [
        (255, 0, 0, 255), (255, 0, 0, 255), (0, 0, 255, 255), (0, 0, 255, 255),
        (255, 0, 0, 255), (255, 0, 0, 255), (0, 0, 255, 255), (0, 0, 255, 255),
    ]


def test_texture_layer_rejects_partial_alpha():
    base = Image.new("RGBA", (1, 1), (0, 0, 0, 255))
    texture = Image.new("RGBA", (1, 1), (255, 255, 255, 128))

    try:
        apply_texture_layers(base, [{"index": 0, "texture": "test:partial"}], {
            "test:partial": texture,
        })
    except ValueError as error:
        assert "cutout alpha" in str(error)
    else:
        raise AssertionError("partial alpha was accepted")


def test_preview_command_resolves_material_override_and_legacy_overlay(tmp_path):
    import json
    from tools.material_mapping import cli

    target = {
        "schema": 1, "size": [2, 1], "source_grids": {"all": [2, 1]},
        "regions": [{"source_slot": "all", "source": [0, 0, 2, 1],
                     "destination": [0, 0, 2, 1]}],
        "pixels": [], "overlay": "test:eyes",
        "layers": [{"index": 5, "texture": "test:default"}],
        "material_overrides": {"test:copper": {
            "slots": {"all": "test:copper_source"},
            "layers": [
                {"index": 10, "texture": "test:face", "grid": [1, 1],
                 "destination": [1, 0, 1, 1]},
                {"index": 20, "model": "test:hat", "part": "head"},
            ],
        }},
    }
    definition = tmp_path / "target.json"
    definition.write_text(json.dumps(target), encoding="utf-8")
    images = {
        "source": Image.new("RGBA", (2, 1), (20, 20, 20, 255)),
        "eyes": Image.new("RGBA", (2, 1), (200, 0, 0, 255)),
        "face": Image.new("RGBA", (1, 1), (0, 200, 0, 255)),
    }
    paths = {}
    for name, image in images.items():
        paths[name] = tmp_path / f"{name}.png"
        image.save(paths[name])
    output = tmp_path / "preview.png"

    assert cli.main([
        "preview", "--target-json", str(definition), "--material", "test:copper",
        "--source", f"test:copper_source={paths['source']}",
        "--texture", f"test:eyes={paths['eyes']}",
        "--texture", f"test:face={paths['face']}",
        "--output", str(output),
    ]) == 0
    with Image.open(output) as preview:
        assert [preview.getpixel((x, 0)) for x in range(2)] == [
            (200, 0, 0, 255), (0, 200, 0, 255),
        ]


def test_hd_layer_preserves_color_detail_with_uniform_alpha():
    texture = Image.new("RGBA", (2, 2), (1, 2, 3, 255))
    texture.putpixel((1, 1), (71, 22, 193, 255))
    result = apply_texture_layers(Image.new("RGBA", (1, 1)),
        [{"index": 0, "texture": "test:hd", "grid": [1, 1]}], {"test:hd": texture})
    assert result.getpixel((0, 0)) == (71, 22, 193, 255)


def test_mixed_alpha_downscale_is_rejected_like_uv_geometry():
    import pytest
    texture = Image.new("RGBA", (2, 1), (1, 2, 3, 255))
    texture.putpixel((1, 0), (0, 0, 0, 0))
    with pytest.raises(ValueError, match="alpha"):
        apply_texture_layers(Image.new("RGBA", (1, 1)),
            [{"index": 0, "texture": "test:mixed", "grid": [2, 1]}], {"test:mixed": texture})


def test_layer_requires_exact_int32_index_and_one_kind():
    import pytest
    for layer in ({"texture":"test:t"}, {"index":True,"texture":"test:t"},
                  {"index":0.5,"texture":"test:t"}, {"index":2147483648,"texture":"test:t"},
                  {"index":0,"texture":"test:t","model":"test:m"}, {"index":0}):
        with pytest.raises(ValueError):
            apply_texture_layers(Image.new("RGBA", (1, 1)), [layer], {"test:t":Image.new("RGBA", (1, 1))})


def test_preview_override_overlay_and_missing_override_fallback(tmp_path):
    import json
    from tools.material_mapping import cli
    target = {"schema":1,"size":[1,1],"source_grids":{"all":[1,1]},
              "pixels":[{"source_slot":"all","source":[0,0],"destination":[0,0]}],
              "overlay":"test:base", "material_overrides":{"test:m":{"overlay":"test:override"}}}
    definition = tmp_path / "target.json"
    definition.write_text(json.dumps(target),encoding="utf-8")
    src = tmp_path / "source.png"
    Image.new("RGBA",(1,1),(100,20,30,255)).save(src)
    ov = tmp_path / "override.png"
    Image.new("RGBA",(1,1),(17,230,43,255)).save(ov)
    out = tmp_path / "out.png"
    args = ["preview","--target-json",str(definition),"--material","test:m",
            "--source",f"all={src}","--texture",f"test:base={src}","--output",str(out)]
    assert cli.main(args+["--texture",f"test:override={ov}"]) == 0
    with Image.open(out) as image:
        assert image.getpixel((0,0)) == (17,230,43,255)
    assert cli.main(args) == 0
    with Image.open(out) as image:
        assert image.getpixel((0,0)) == (100,20,30,255)


def test_preview_never_overwrites_its_definition(tmp_path):
    import json
    from tools.material_mapping import cli
    target = {"schema":1,"size":[1,1],"source_grids":{"all":[1,1]},
              "pixels":[{"source_slot":"all","source":[0,0],"destination":[0,0]}]}
    definition = tmp_path / "target.json"
    text = json.dumps(target)
    definition.write_text(text,encoding="utf-8")
    src = tmp_path / "source.png"
    Image.new("RGBA",(1,1),(100,20,30,255)).save(src)
    assert cli.main(["preview","--target-json",str(definition),"--source",f"all={src}",
                     "--output",str(definition)]) != 0
    assert definition.read_text(encoding="utf-8") == text


def test_material_named_eyes_choose_one_texture_not_a_stack():
    base = Image.new("RGBA", (2, 1), (10, 20, 30, 255))
    layers = [{"index": 0, "texture": "host:eyes/eye_default", "material_variants": True}]
    replacement = Image.new("RGBA", (2, 1))
    replacement.putpixel((1, 0), (20, 200, 30, 255))
    textures = {"host:eyes/eye_default": Image.new("RGBA", (2, 1), (200, 10, 30, 255)),
                "host:eyes/eye_copper_casing": Image.new("RGBA", (2, 1), (0, 0, 200, 255)),
                "host:eyes/create/eye_copper_casing": replacement}
    result = apply_texture_layers(base, layers, textures, material="create:copper_casing")
    assert [result.getpixel((x, 0)) for x in range(2)] == [(10, 20, 30, 255), (20, 200, 30, 255)]
    result = apply_texture_layers(base, layers, textures, material="other:copper_casing")
    assert result.getpixel((0, 0)) == (0, 0, 200, 255)
    result = apply_texture_layers(base, layers, textures, material="create:brass_casing")
    assert result.getpixel((0, 0)) == (200, 10, 30, 255)
    result = apply_texture_layers(base, [{"index":0,"texture":"host:eyes/eye_default"}], textures, material="create:copper_casing")
    assert result.getpixel((0, 0)) == (200, 10, 30, 255)


def test_material_variants_requires_boolean():
    import pytest
    for invalid in (1, "true", None):
        with pytest.raises(ValueError, match="material_variants"):
            apply_texture_layers(Image.new("RGBA", (1, 1)),
                [{"index":0,"texture":"host:eyes/eye_default","material_variants":invalid}],
                {"host:eyes/eye_default":Image.new("RGBA", (1, 1))})


def test_material_named_texture_with_incompatible_grid_uses_default():
    result = apply_texture_layers(Image.new("RGBA", (2, 1)),
        [{"index":0,"texture":"host:eyes/eye_default","material_variants":True}],
        {"host:eyes/create/eye_copper_casing":Image.new("RGBA", (3, 1), (255, 0, 0, 255)),
         "host:eyes/eye_default":Image.new("RGBA", (2, 1), (1, 2, 3, 255))}, material="create:copper_casing")
    assert result.getpixel((0, 0)) == (1, 2, 3, 255)


def test_preview_cli_passes_material_to_named_eye_selection(tmp_path):
    import json
    from tools.material_mapping import cli
    target = {"schema":1,"size":[1,1],"source_grids":{"all":[1,1]},
              "pixels":[{"source_slot":"all","source":[0,0],"destination":[0,0]}],
              "layers":[{"index":0,"texture":"host:eyes/eye_default","material_variants":True}]}
    definition = tmp_path / "target.json"
    definition.write_text(json.dumps(target),encoding="utf-8")
    source, eye, out = (tmp_path / name for name in ("source.png", "eye.png", "preview.png"))
    Image.new("RGBA",(1,1),(10,20,30,255)).save(source)
    Image.new("RGBA",(1,1),(101,202,33,255)).save(eye)
    assert cli.main(["preview","--target-json",str(definition),"--material","create:copper_casing",
                     "--source",f"all={source}","--texture",f"host:eyes/eye_copper_casing={eye}",
                     "--output",str(out)]) == 0
    with Image.open(out) as result:
        assert result.getpixel((0,0)) == (101,202,33,255)


def test_material_named_eyes_default_texture_namespace_matches_java():
    result = apply_texture_layers(Image.new("RGBA", (1, 1)),
        [{"index":0,"texture":"eyes/eye_default","material_variants":True}],
        {"minecraft:eyes/create/eye_copper_casing":Image.new("RGBA", (1, 1), (7, 19, 61, 255))},
        material="create:copper_casing")
    assert result.getpixel((0, 0)) == (7, 19, 61, 255)


def test_material_variant_roles_are_isolated_and_never_use_bare_names():
    for role, other in (("eye", "body"), ("body", "eye")):
        default = f"host:spider/{role}_00"
        layer = {"index":0,"texture":default,"material_variants":True}
        original = Image.new("RGBA", (1, 1), (10, 20, 30, 255))
        wrong = Image.new("RGBA", (1, 1), (255, 0, 0, 255))
        textures = {default:original, f"host:spider/create/{other}_copper_casing":wrong,
                    f"host:spider/{other}_copper_casing":wrong,
                    "host:spider/create/copper_casing":wrong, "host:spider/copper_casing":wrong}
        result = apply_texture_layers(original,[layer],textures,material="create:copper_casing")
        assert result.getpixel((0, 0)) == (10, 20, 30, 255)
        textures[f"host:spider/{role}_copper_casing"] = Image.new("RGBA", (1,1), (31,42,53,255))
        result = apply_texture_layers(original,[layer],textures,material="create:copper_casing")
        assert result.getpixel((0, 0)) == (31,42,53,255)


def test_material_variants_unknown_default_role_does_not_guess():
    for name in ("default", "spider", "eyebrow_00", "bodyguard_00"):
        original = Image.new("RGBA", (1,1), (10,20,30,255))
        result = apply_texture_layers(original,[{"index":0,"texture":f"host:{name}","material_variants":True}],
            {f"host:{name}":original, "host:create/copper_casing":Image.new("RGBA", (1,1), (255,0,0,255))},
            material="create:copper_casing")
        assert result.getpixel((0, 0)) == (10,20,30,255)


def test_material_variant_nested_paths_prefix_only_the_filename():
    result = apply_texture_layers(Image.new("RGBA", (1,1)),
        [{"index":0,"texture":"host:spider/body_00","material_variants":True}],
        {"host:spider/addon/machines/body_casing":Image.new("RGBA",(1,1),(9,87,65,255))},
        material="addon:machines/casing")
    assert result.getpixel((0, 0)) == (9,87,65,255)


def test_numbered_eye_pool_uses_numeric_material_index_and_skips_invalid_candidates():
    import pytest
    base = Image.new("RGBA", (1, 1))
    layer = {"index": 0, "texture": "test:eyes/eye_00", "material_variants": True}
    colors = {"eye_10": 10, "eye_02": 2, "eye_00": 0, "body_01": 99,
              "nested/eye_01": 98, "eye_copper": 97}
    textures = {"test:eyes/"+name: Image.new("RGBA", (1, 1), (value, 0, 0, 255))
                for name, value in colors.items()}
    textures["test:eyes/eye_01"] = Image.new("RGBA", (1, 1), (1, 0, 0, 128))
    textures["test:eyes/eye_03"] = Image.new("RGBA", (2, 1))
    textures["other:eyes/eye_01"] = Image.new("RGBA", (1, 1), (96, 0, 0, 255))
    for index, expected in enumerate([0, 2, 10, 0, 2]):
        result = apply_texture_layers(base, [layer], textures, material="test:brass", material_index=index)
        assert result.getpixel((0, 0))[0] == expected
    with pytest.raises(ValueError, match="material-index"):
        apply_texture_layers(base, [layer], textures, material="test:brass")
    # Dedicated textures win even without an index; explicit textures opt out.
    assert apply_texture_layers(base, [layer], textures, material="test:copper").getpixel((0, 0))[0] == 97
    assert apply_texture_layers(base, [dict(layer, material_variants=False)], textures,
                                material="test:brass", material_index=2).getpixel((0, 0))[0] == 0
    for invalid in (-1, True, 1.5):
        with pytest.raises(ValueError, match="material_index"):
            apply_texture_layers(base, [layer], textures, material="test:brass", material_index=invalid)


def test_body_does_not_cycle_numbered_candidates():
    base = Image.new("RGBA", (1, 1))
    layer = {"index": 0, "texture": "test:body_00", "material_variants": True}
    textures = {"test:body_00": Image.new("RGBA", (1, 1), (10, 0, 0, 255)),
                "test:body_01": Image.new("RGBA", (1, 1), (20, 0, 0, 255)),
                "test:body_copper": Image.new("RGBA", (1, 1), (30, 0, 0, 255))}
    assert apply_texture_layers(base, [layer], textures, material="test:brass", material_index=1).getpixel((0, 0))[0] == 10
    assert apply_texture_layers(base, [layer], textures, material="test:copper", material_index=1).getpixel((0, 0))[0] == 30


def test_preview_command_requires_palette_index_only_for_unresolved_generic_pool(tmp_path):
    import json
    from tools.material_mapping import cli
    target = {"schema": 1, "size": [1, 1], "source_grids": {"all": [1, 1]},
              "regions": [{"source_slot": "all", "source": [0, 0, 1, 1], "destination": [0, 0, 1, 1]}],
              "layers": [{"index": 0, "texture": "test:eye_00", "material_variants": True}]}
    definition=tmp_path/"target.json"
    definition.write_text(json.dumps(target), encoding="utf-8")
    for name, color in [("source", (0, 0, 0, 255)), ("eye_00", (10, 0, 0, 255)), ("eye_01", (20, 0, 0, 255))]:
        Image.new("RGBA", (1, 1), color).save(tmp_path/(name+".png"))
    output=tmp_path/"preview.png"
    output.write_bytes(b"existing output")
    args=["preview", "--target-json", str(definition), "--material", "test:brass",
          "--source", f"all={tmp_path/'source.png'}", "--output", str(output),
          "--texture", f"test:eye_00={tmp_path/'eye_00.png'}", "--texture", f"test:eye_01={tmp_path/'eye_01.png'}"]
    assert cli.main(args) == 2
    assert output.read_bytes() == b"existing output"
    assert cli.main(args+["--material-index", "1"]) == 0
    with Image.open(output) as image:
        assert image.getpixel((0, 0)) == (20, 0, 0, 255)


def test_empty_pool_and_transparent_candidate_keep_replacement_semantics():
    base=Image.new("RGBA", (1, 1), (50, 0, 0, 255))
    layer={"index": 0, "texture": "test:eye_default", "material_variants": True}
    textures={"test:eye_default": Image.new("RGBA", (1, 1), (10, 0, 0, 255)),
              "test:eye_01": Image.new("RGBA", (1, 1), (20, 0, 0, 128))}
    assert apply_texture_layers(base,[layer],textures,material="test:a").getpixel((0,0)) == (10,0,0,255)
    textures["test:eye_01"]=Image.new("RGBA", (1,1))
    assert apply_texture_layers(base,[layer],textures,material="test:a").getpixel((0,0)) == (50,0,0,255)


def test_preview_dedicated_body_precedes_generated_uv_and_explicit_layers_win(tmp_path):
    import json
    from tools.material_mapping import cli
    target={"schema":1,"size":[2,1],"source_grids":{"all":[2,1]},
            "regions":[{"source_slot":"all","source":[0,0,2,1],"destination":[0,0,2,1]}],
            "body_textures":"test:skins","layers":[{"index":0,"texture":"test:eye"}]}
    definition=tmp_path/"target.json"; definition.write_text(json.dumps(target),encoding="utf-8")
    source=tmp_path/"source.png"; Image.new("RGBA",(2,1),(200,0,0,255)).save(source)
    body=Image.new("RGBA",(2,1)); body.putpixel((0,0),(0,200,0,255)); body.save(tmp_path/"body.png")
    eye=Image.new("RGBA",(2,1)); eye.putpixel((0,0),(0,0,200,255)); eye.save(tmp_path/"eye.png")
    output=tmp_path/"preview.png"
    args=["preview","--target-json",str(definition),"--material","other:casing","--source",f"all={source}",
          "--texture",f"test:skins/other/body_casing={tmp_path/'body.png'}",
          "--texture",f"test:eye={tmp_path/'eye.png'}","--output",str(output)]
    assert cli.main(args)==0
    with Image.open(output) as image:
        assert [image.getpixel((x,0)) for x in range(2)]==[(0,0,200,255),(0,0,0,0)]
    # Source is unnecessary when a dedicated body is selected; the CLI must not decode it.
    source.unlink()
    assert cli.main(args)==0
    previous=output.read_bytes()
    target["body_textures"]=None
    definition.write_text(json.dumps(target),encoding="utf-8")
    assert cli.main(args)==2
    assert output.read_bytes()==previous


def test_dedicated_body_lookup_is_independent_of_eyes_and_has_no_numbered_fallback():
    from tools.material_mapping.sampling import render_dedicated_body
    opaque=Image.new("RGBA",(2,1),(10,20,30,255))
    textures={"test:skins/addon/machines/body_casing":Image.new("RGBA",(3,1)),
              "test:skins/machines/body_casing":opaque,
              "test:skins/body_00":opaque,"test:skins/eye_casing":opaque}
    assert render_dedicated_body((2,1),"test:skins/",textures,"addon:machines/casing").tobytes()==opaque.tobytes()
    assert render_dedicated_body((2,1),"test:skins",textures,"addon:unknown") is None
    assert render_dedicated_body((2,1),None,textures,"addon:machines/casing") is None
    textures["test:skins/addon/machines/body_casing"]=Image.new("RGBA",(2,1))
    assert render_dedicated_body((2,1),"test:skins",textures,"addon:machines/casing").getbbox() is None
    textures["test:skins/addon/machines/body_casing"]=Image.new("RGBA",(2,1),(1,2,3,128))
    assert render_dedicated_body((2,1),"test:skins",textures,"addon:machines/casing").tobytes()==opaque.tobytes()


def test_body_directory_rejects_invalid_config_even_without_material():
    import pytest
    from tools.material_mapping.sampling import render_dedicated_body
    for folder in (True,1,{},[],"","test:","test:/","Bad Space"):
        with pytest.raises(ValueError,match="body_textures"):
            render_dedicated_body((1,1),folder,{},None)


def test_preview_skips_unreadable_named_body_and_uses_short_name_or_generated_uv(tmp_path):
    import json
    from tools.material_mapping import cli
    definition=tmp_path/"target.json"
    definition.write_text(json.dumps({"schema":1,"size":[1,1],"source_grids":{"all":[1,1]},
        "regions":[{"source_slot":"all","source":[0,0,1,1],"destination":[0,0,1,1]}],
        "body_textures":"test:skins"}),encoding="utf-8")
    broken=tmp_path/"broken.png";broken.write_bytes(b"invalid PNG")
    good=tmp_path/"good.png";Image.new("RGBA",(1,1),(10,20,30,255)).save(good)
    source=tmp_path/"source.png";Image.new("RGBA",(1,1),(90,80,70,255)).save(source)
    output=tmp_path/"preview.png"
    args=["preview","--target-json",str(definition),"--material","other:casing",
          "--texture",f"test:skins/other/body_casing={broken}","--source",f"all={source}","--output",str(output)]
    assert cli.main(args+["--texture",f"test:skins/body_casing={good}"])==0
    with Image.open(output) as image: assert image.getpixel((0,0))==(10,20,30,255)
    assert cli.main(args)==0
    with Image.open(output) as image: assert image.getpixel((0,0))==(90,80,70,255)
