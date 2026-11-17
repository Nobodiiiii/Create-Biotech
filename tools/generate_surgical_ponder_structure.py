"""Build the shared surgical Ponder workshop using the vanilla structure NBT format."""

import gzip
import struct
from pathlib import Path


def string(value):
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def named(tag_type, name, payload):
    return bytes([tag_type]) + string(name) + payload


def compound(entries):
    return b"".join(entries) + b"\x00"


def integer(value):
    return struct.pack(">i", value)


def list_tag(tag_type, entries):
    return bytes([tag_type]) + integer(len(entries)) + b"".join(entries)


def position(x, y, z):
    return list_tag(3, [integer(x), integer(y), integer(z)])


def build():
    palette = []
    for name, properties in [
        ("minecraft:smooth_stone", {}),
        ("minecraft:polished_andesite", {}),
        ("create_biotech:surgical_table", {"powered": "false"}),
    ]:
        entries = [named(8, "Name", string(name))]
        if properties:
            entries.append(named(10, "Properties", compound([
                named(8, key, string(value)) for key, value in properties.items()
            ])))
        palette.append(compound(entries))
    blocks = []
    for x in range(11):
        for z in range(11):
            blocks.append(compound([named(9, "pos", position(x, 0, z)), named(3, "state", integer(0))]))
    for x in range(2, 9):
        for z in range(2, 8):
            for y, state in [(1, 1), (2, 2)]:
                blocks.append(compound([named(9, "pos", position(x, y, z)), named(3, "state", integer(state))]))
    payload = named(10, "", compound([
        named(3, "DataVersion", integer(3955)),
        named(9, "size", position(11, 5, 11)),
        named(9, "palette", list_tag(10, palette)),
        named(9, "blocks", list_tag(10, blocks)),
        named(9, "entities", list_tag(10, [])),
    ]))
    target = Path(__file__).resolve().parents[1] / "src/main/resources/assets/create_biotech/ponder/generated/create_biotech/surgical_table/workshop.nbt"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(gzip.compress(payload, mtime=0))
    print(f"Wrote {target} ({len(blocks)} blocks)")


if __name__ == "__main__":
    build()
