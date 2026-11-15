"""Props of kitchen counters: what CounterOverlays lays over fixtures_counters_01, made.

    blender --background --factory-startup --python tools/models/props/counters.py

Each counter tile the game may lay one of two overlays over, and none of them sets anything on the counter: each is
the counter worn. Every one paints stains and nicks on the worktop and scuffs along the kick board, and one of each
two also paints one door or both standing ajar. So a counter the game paints ajar is drawn with that door left off,
over a bay of the cupboard with a shelf in it (furniture.cabinet's open_doors), and the door stands open on its
hinges as a prop. The stains and the scuffs are marks the mod stands where the overlay paints them, coloured as it
paints them there.

What each overlay paints ajar was measured off the overlays: OVERLAYS below. Only the numbers are kept here.
"""

import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bmesh  # noqa: E402
import props  # noqa: E402
from mathutils import Matrix  # noqa: E402
from props import Marks, Prop, Swap  # noqa: E402

F = props.furniture
BATCH = "counters"

# The counters the overlays lie on, as furniture.py makes them, and what they are made of.
HOSTS = {
    "counter": dict(sink=False, made="wood", top_of="worktop"),
    "counter_sink": dict(sink=True, made="wood", top_of="worktop"),
    "steel_counter": dict(sink=False, made="steel", top_of="steel"),
    "steel_counter_sink": dict(sink=True, made="steel", top_of="steel"),
}
# Wood counters and steel ones are both CounterOverlays' fixtures_counters_01; the steel ones are the tiles of the
# sheet whose GroupName has Steel in it.
STEEL_TILES = ("fixtures_counters_01_33", "fixtures_counters_01_35", "fixtures_counters_01_37", "fixtures_counters_01_39")

# As furniture.py's every() makes a counter: its depth and height.
DEEP, HIGH = 0.62, 0.90
# As furniture.cabinet builds one, from those: the top of its body, the kick board's height and how far back its face
# is, the face of its doors, and the outer edge each door hangs on.
TOP = HIGH - 0.04
KICK = 0.10
FRONT = -DEEP / 2
KICK_FACE = FRONT + 0.02 + 0.06
DOOR_FACE = FRONT + 0.02 - 0.02
DOOR_THICK = 0.02
HINGE = 0.492
# The bowl furniture.cabinet cuts in a sink's worktop, across.
BOWL = 0.27 + 0.014

# How far a door the game paints ajar stands open, in degrees: the overlays draw it a hand's width open at its handle.
AJAR = 12.0
# How far a mark stands proud of the face it is on, as far as the price tickets on a shelf's edge stand off it
# (furniture.py's tickets), and how thick it is, too thin for its edge to show, in metres.
PROUD = 0.0005
MARK_THICK = 0.0004

# For each overlay: the counter tile it is over, the doors it paints ajar (-1 the left as the counter is looked at, 1
# the right), and whether the counter's front, its kick board, is the side the tile's picture shows.
OVERLAYS = {
    "fixtures_overlay_counters_01_0": ("fixtures_counters_01_1", (), False),
    "fixtures_overlay_counters_01_1": ("fixtures_counters_01_1", (), False),
    "fixtures_overlay_counters_01_2": ("fixtures_counters_01_3", (-1, 1), True),
    "fixtures_overlay_counters_01_3": ("fixtures_counters_01_3", (1,), True),
    "fixtures_overlay_counters_01_4": ("fixtures_counters_01_5", (1,), True),
    "fixtures_overlay_counters_01_5": ("fixtures_counters_01_5", (-1,), True),
    "fixtures_overlay_counters_01_6": ("fixtures_counters_01_7", (), False),
    "fixtures_overlay_counters_01_7": ("fixtures_counters_01_7", (), False),
    "fixtures_overlay_counters_01_8": ("fixtures_counters_01_9", (), False),
    "fixtures_overlay_counters_01_9": ("fixtures_counters_01_9", (), False),
    "fixtures_overlay_counters_01_10": ("fixtures_counters_01_11", (-1,), True),
    "fixtures_overlay_counters_01_11": ("fixtures_counters_01_11", (-1,), True),
    "fixtures_overlay_counters_01_12": ("fixtures_counters_01_13", (-1,), True),
    "fixtures_overlay_counters_01_13": ("fixtures_counters_01_13", (1,), True),
    "fixtures_overlay_counters_01_14": ("fixtures_counters_01_15", (), False),
    "fixtures_overlay_counters_01_15": ("fixtures_counters_01_15", (), False),
    "fixtures_overlay_counters_01_16": ("fixtures_counters_01_33", (), False),
    "fixtures_overlay_counters_01_17": ("fixtures_counters_01_33", (), False),
    "fixtures_overlay_counters_01_18": ("fixtures_counters_01_35", (1,), True),
    "fixtures_overlay_counters_01_19": ("fixtures_counters_01_35", (-1,), True),
    "fixtures_overlay_counters_01_20": ("fixtures_counters_01_37", (1,), True),
    "fixtures_overlay_counters_01_21": ("fixtures_counters_01_37", (-1,), True),
    "fixtures_overlay_counters_01_22": ("fixtures_counters_01_39", (), False),
    "fixtures_overlay_counters_01_23": ("fixtures_counters_01_39", (), False),
    "fixtures_overlay_counters_01_24": ("fixtures_counters_01_41", (), False),
    "fixtures_overlay_counters_01_25": ("fixtures_counters_01_41", (), False),
    "fixtures_overlay_counters_01_26": ("fixtures_counters_01_43", (-1,), True),
    "fixtures_overlay_counters_01_27": ("fixtures_counters_01_43", (-1, 1), True),
    "fixtures_overlay_counters_01_28": ("fixtures_counters_01_45", (1,), True),
    "fixtures_overlay_counters_01_29": ("fixtures_counters_01_45", (-1,), True),
    "fixtures_overlay_counters_01_30": ("fixtures_counters_01_47", (), False),
    "fixtures_overlay_counters_01_31": ("fixtures_counters_01_47", (), False),
    "fixtures_overlay_counters_01_32": ("fixtures_counters_01_49", (), False),
    "fixtures_overlay_counters_01_33": ("fixtures_counters_01_49", (), False),
    "fixtures_overlay_counters_01_34": ("fixtures_counters_01_51", (-1,), True),
    "fixtures_overlay_counters_01_35": ("fixtures_counters_01_51", (1,), True),
    "fixtures_overlay_counters_01_36": ("fixtures_counters_01_53", (-1,), True),
    "fixtures_overlay_counters_01_37": ("fixtures_counters_01_53", (1,), True),
    "fixtures_overlay_counters_01_38": ("fixtures_counters_01_55", (), False),
    "fixtures_overlay_counters_01_39": ("fixtures_counters_01_55", (), False),
    "fixtures_overlay_counters_01_40": ("fixtures_counters_01_57", (), False),
    "fixtures_overlay_counters_01_41": ("fixtures_counters_01_57", (), False),
    "fixtures_overlay_counters_01_42": ("fixtures_counters_01_59", (1,), True),
    "fixtures_overlay_counters_01_43": ("fixtures_counters_01_59", (-1,), True),
    "fixtures_overlay_counters_01_44": ("fixtures_counters_01_61", (1,), True),
    "fixtures_overlay_counters_01_45": ("fixtures_counters_01_61", (-1, 1), True),
    "fixtures_overlay_counters_01_46": ("fixtures_counters_01_63", (), False),
    "fixtures_overlay_counters_01_47": ("fixtures_counters_01_63", (), False),
    "fixtures_overlay_counters_01_48": ("fixtures_counters_01_65", (), False),
    "fixtures_overlay_counters_01_49": ("fixtures_counters_01_65", (), False),
    "fixtures_overlay_counters_01_50": ("fixtures_counters_01_67", (-1, 1), True),
    "fixtures_overlay_counters_01_51": ("fixtures_counters_01_67", (1,), True),
    "fixtures_overlay_counters_01_52": ("fixtures_counters_01_69", (-1,), True),
    "fixtures_overlay_counters_01_53": ("fixtures_counters_01_69", (1,), True),
    "fixtures_overlay_counters_01_54": ("fixtures_counters_01_71", (), False),
    "fixtures_overlay_counters_01_55": ("fixtures_counters_01_71", (), False),
    "fixtures_overlay_counters_01_56": ("fixtures_counters_01_73", (), False),
    "fixtures_overlay_counters_01_57": ("fixtures_counters_01_73", (), False),
    "fixtures_overlay_counters_01_58": ("fixtures_counters_01_75", (-1,), True),
    "fixtures_overlay_counters_01_59": ("fixtures_counters_01_75", (-1,), True),
    "fixtures_overlay_counters_01_60": ("fixtures_counters_01_77", (-1, 1), True),
    "fixtures_overlay_counters_01_61": ("fixtures_counters_01_77", (-1,), True),
    "fixtures_overlay_counters_01_62": ("fixtures_counters_01_79", (), False),
    "fixtures_overlay_counters_01_63": ("fixtures_counters_01_79", (), False),
}


def host(name):
    return F.cabinet(name, DEEP, HIGH, True, **HOSTS[name])


def tag(ways):
    return {(-1,): "l", (1,): "r", (-1, 1): "lr"}[tuple(sorted(ways))]


def open_host(name, ways):
    return F.cabinet("%s_%s_open_%s" % (BATCH, name, tag(ways)), DEEP, HIGH, True, open_doors=tuple(sorted(ways)), **HOSTS[name])


def door(name, way, made, around):
    """One door of a counter standing AJAR degrees open on its hinges, built where it stands in the counter's space."""
    model = F.Model(name, centred=False)
    frame, panel, pulls = bmesh.new(), bmesh.new(), bmesh.new()
    F.cabinet_door(frame, panel, pulls, way, KICK, TOP, DOOR_FACE, True)
    # The handle side swings out to the front, which is -Y.
    turn = Matrix.Rotation(math.radians(way * AJAR), 3, "Z")
    for bm in (frame, panel, pulls):
        bmesh.ops.rotate(bm, cent=(way * HINGE, DOOR_FACE + DOOR_THICK, 0.0), matrix=turn, verts=bm.verts[:])
    model.hard(frame, made, bevel=0.004)
    model.hard(panel, made, bevel=0.003)
    model.hard(pulls, "metal", bevel=0.003, segments=1)
    props.stands_on(model, around)
    return model


def flat_mark(model, outline, z0, z1, surface, upright):
    """A mark: a slab MARK_THICK thick of the outline, lying flat between z0 and z1, or standing upright facing -Y."""
    bm = bmesh.new()
    rings = []
    for z in (z0, z1):
        rings.append([bm.verts.new((x, z, y) if upright else (x, y, z)) for x, y in outline])
    bm.faces.new(rings[0][::-1])
    bm.faces.new(rings[1])
    count = len(outline)
    for i in range(count):
        j = (i + 1) % count
        bm.faces.new((rings[0][i], rings[0][j], rings[1][j], rings[1][i]))
    model.plain(bm, surface)


def outline(seed, long, wide, corners=11):
    """A ragged closed outline about long by wide, the same for the same seed."""
    rng = random.Random(seed)
    points = []
    for i in range(corners):
        angle = 2 * math.pi * i / corners
        reach = 0.65 + 0.35 * rng.random()
        points.append((math.cos(angle) * long / 2 * reach, math.sin(angle) * wide / 2 * reach))
    return points


def stain(name, seed, long, wide, around):
    """A stain or nick on a worktop, lying on it."""
    model = F.Model(name)
    flat_mark(model, outline(seed, long, wide), 0.0, MARK_THICK, "worktop", False)
    props.stands_on(model, around)
    return model


def scuff(name, seed, long, high, around):
    """A scuff on a kick board, standing on its face."""
    model = F.Model(name)
    flat_mark(model, [(x, z + high / 2) for x, z in outline(seed, long, high)], 0.0, -MARK_THICK, "wood", True)
    props.stands_on(model, around)
    return model


STAINS = ["%s_stain_%s" % (BATCH, letter) for letter in "abcd"]
SCUFFS = ["%s_scuff_%s" % (BATCH, letter) for letter in "abc"]


def build():
    plain_counter = host("counter")
    models = [stain(STAINS[0], 1, 0.06, 0.035, plain_counter), stain(STAINS[1], 2, 0.045, 0.045, plain_counter),
              stain(STAINS[2], 3, 0.08, 0.02, plain_counter), stain(STAINS[3], 4, 0.035, 0.025, plain_counter),
              scuff(SCUFFS[0], 5, 0.07, 0.018, plain_counter), scuff(SCUFFS[1], 6, 0.05, 0.03, plain_counter),
              scuff(SCUFFS[2], 7, 0.09, 0.012, plain_counter)]
    entries = []
    made = set()
    for overlay, (tile, ways, front_shown) in sorted(OVERLAYS.items()):
        steel = tile in STEEL_TILES
        for name in ("steel_counter", "steel_counter_sink") if steel else ("counter", "counter_sink"):
            if ways:
                swapped = open_host(name, ways)
                if swapped.name not in made:
                    made.add(swapped.name)
                    models.append(swapped)
                entries.append(Swap(overlay, name, swapped.name))
                for way in ways:
                    leaf = "%s_%sdoor_%s" % (BATCH, "steel_" if steel else "", tag((way,)))
                    if leaf not in made:
                        made.add(leaf)
                        models.append(door(leaf, way, HOSTS[name]["made"], swapped))
                    entries.append(Prop(overlay, name, leaf))
            if HOSTS[name]["sink"]:
                for x0 in (-0.5 + 0.02, BOWL + 0.02):
                    entries.append(Marks(overlay, name, STAINS, (x0, FRONT + 0.03, HIGH + PROUD), (0.5 - BOWL - 0.04, 0.0, 0.0), (0.0, DEEP - 0.06, 0.0),
                                         (2, 5), ["worktop"]))
            else:
                entries.append(Marks(overlay, name, STAINS, (-0.47, FRONT + 0.03, HIGH + PROUD), (0.94, 0.0, 0.0), (0.0, DEEP - 0.06, 0.0), (8, 5), ["worktop"]))
            if front_shown:
                entries.append(Marks(overlay, name, SCUFFS, (-0.46, KICK_FACE - PROUD - MARK_THICK / 2, 0.012), (0.92, 0.0, 0.0), (0.0, 0.0, 0.05), (8, 1),
                                     ["wood"]))
    return models, entries


props.write(BATCH, os.path.abspath(__file__), build, {name: (lambda name=name: host(name)) for name in HOSTS})
