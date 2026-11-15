"""Writes src/test/data/overlays.tsv: every overlay the game may lay over a tile, which PropsTests checks every batch's
props against, so the tests know the game's overlays without the game.

    python tools/models/props/overlays.py [the game's folder]

It reads the game's TileOverlays and ContainerOverlays maps from its Lua (media/lua/server/Items) and its tile
definitions (media/tile_definitions), and writes one line for each overlay and tile it may lie over, but for the
tiles of floors, walls and roofs:

    OVERLAY  TILE  KIND  CHANCE  CHOICES  ROW

KIND is tile (TileOverlays, set out once at random as the square loads) or container (ContainerOverlays, painted while
the container holds something). A tile overlay is one of CHOICES pictures, "none" counted, picked on one square in
CHANCE. ROW is the tile's definition as TileRows reads it: its name, then each property and value, split by bars.
Run it again after a game update.
"""

import os
import re
import struct
import sys

GAME = sys.argv[1] if len(sys.argv) > 1 else r"C:\Program Files (x86)\Steam\steamapps\common\ProjectZomboid"
ITEMS = os.path.join(GAME, "media", "lua", "server", "Items")
DEFINITIONS = os.path.join(GAME, "media", "tile_definitions")
OUTPUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "src", "test", "data", "overlays.tsv")
ENTRY = re.compile(r'\{\s*name\s*=\s*"([^"]*)"(?:,\s*chance\s*=\s*(\d+))?(?:,\s*usage\s*=\s*"([^"]*)")?,\s*tiles\s*=\s*\{([^}]*)\}\s*\}')
# The files are the game's own, read in the order it loads its tile definitions: a later one adds to an earlier.
BUILDING = ("floors_", "walls_", "blends_", "roofs_")
TILE_FILES = ["newtiledefinitions.tiles", "tiledefinitions_erosion.tiles", "tiledefinitions_overlays.tiles", "tiledefinitions_b42chunkcaching.tiles",
              "tiledefinitions_noiseworks.patch.tiles"]


def tiles():
    out = {}
    for f in TILE_FILES:
        d = open(os.path.join(DEFINITIONS, f), "rb").read()
        if d[:4] != b"tdef":
            raise SystemExit("%s is not a file of tile definitions" % f)
        p = 4

        def text():
            nonlocal p
            e = d.index(b"\n", p)
            v = d[p:e].decode("latin1").strip()
            p = e + 1
            return v

        def number():
            nonlocal p
            v = struct.unpack_from("<i", d, p)[0]
            p += 4
            return v
        number()
        for _ in range(number()):
            name = text()
            text()
            number(), number(), number()
            for t in range(number()):
                props = {}
                for _ in range(number()):
                    k = text()
                    props[k] = text()
                key = "%s_%d" % (name, t)
                if key in out:
                    out[key].update(props)
                elif props or f == TILE_FILES[0]:
                    out[key] = props
    return out


def overlays():
    found = []
    for f in sorted(os.listdir(ITEMS)):
        if not f.endswith(".lua"):
            continue
        source = open(os.path.join(ITEMS, f), encoding="latin1").read()
        kind = "tile" if "getTileOverlays():addOverlays" in source else "container" if "getContainerOverlays():addOverlays" in source else None
        if kind is None:
            continue
        for m in re.finditer(r'overlayMap\["([^"]+)"\] = (.*)', source):
            for e in ENTRY.finditer(m.group(2)):
                pictures = re.findall(r'"([^"]+)"', e.group(4))
                for picture in pictures:
                    if picture != "none":
                        found.append((picture, m.group(1), kind, int(e.group(2) or 1), len(pictures)))
    return found


def main():
    t = tiles()
    lines = set()
    for picture, tile, kind, chance, choices in overlays():
        # The grime, cracks and moss laid over floors and walls are no furniture's.
        if tile not in t or tile.startswith(BUILDING):
            continue
        row = "|".join([tile] + ["%s|%s" % kv for kv in sorted(t[tile].items())])
        lines.add("\t".join((picture, tile, kind, str(chance), str(choices), row)))
    with open(OUTPUT, "w", encoding="latin1", newline="\n") as out:
        out.write("# Made by tools/models/props/overlays.py from the game's Lua and tile definitions.\n")
        for line in sorted(lines):
            out.write(line + "\n")
    print("wrote %d lines to %s" % (len(lines), os.path.normpath(OUTPUT)))


main()
