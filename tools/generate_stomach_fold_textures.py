"""Rebuild both stomach-fold textures from the shared, hand-authored pixel maps."""

from generate_stomach_textures import FOLD_TEXTURES, write_textures


if __name__ == "__main__":
    write_textures(FOLD_TEXTURES)
