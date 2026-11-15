"""Props of kitchen counters: what CounterOverlays lays over fixtures_counters_01, made.

    blender --background --factory-startup --python tools/models/props/counters.py

Each counter tile the game may lay one of two overlays over, and none of them sets anything on the counter: each is
the counter worn. One of each two paints one door or both standing ajar. So a counter the game paints ajar is drawn
with that door left off, over a bay of the cupboard with a shelf in it (furniture.cabinet's open_doors), and the door
stands open on its hinges as a prop.

The rest of what the overlays paint, stains and nicks on the worktop and scuffs on the kick board, is not made: where
an overlay paints no door it is at most 44 of 255 from the counter's own colour under it, mostly under 30, over a few
dozen of the sprite's pixels, and a mark coloured as the overlay paints it there is the colour of the counter.

What each overlay paints ajar was measured off the overlays: OVERLAYS below. Only the numbers are kept here.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bmesh  # noqa: E402
import props  # noqa: E402
from mathutils import Matrix  # noqa: E402
from props import Prop, Swap  # noqa: E402

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
# As furniture.cabinet builds one, from those: the top of its body, the kick board's height, the face of its doors, and
# the outer edge each door hangs on.
TOP = HIGH - 0.04
KICK = 0.10
FRONT = -DEEP / 2
DOOR_FACE = FRONT + 0.02 - 0.02
DOOR_THICK = 0.02
HINGE = 0.492

# How far a door the game paints ajar stands open, in degrees: the overlays draw it a hand's width open at its handle.
AJAR = 12.0

# For each overlay: the counter tile it is over, and the doors it paints ajar (-1 the left as the counter is looked at,
# 1 the right).
OVERLAYS = {
    "fixtures_overlay_counters_01_0": ("fixtures_counters_01_1", ()),
    "fixtures_overlay_counters_01_1": ("fixtures_counters_01_1", ()),
    "fixtures_overlay_counters_01_2": ("fixtures_counters_01_3", (-1, 1)),
    "fixtures_overlay_counters_01_3": ("fixtures_counters_01_3", (1,)),
    "fixtures_overlay_counters_01_4": ("fixtures_counters_01_5", (1,)),
    "fixtures_overlay_counters_01_5": ("fixtures_counters_01_5", (-1,)),
    "fixtures_overlay_counters_01_6": ("fixtures_counters_01_7", ()),
    "fixtures_overlay_counters_01_7": ("fixtures_counters_01_7", ()),
    "fixtures_overlay_counters_01_8": ("fixtures_counters_01_9", ()),
    "fixtures_overlay_counters_01_9": ("fixtures_counters_01_9", ()),
    "fixtures_overlay_counters_01_10": ("fixtures_counters_01_11", (-1,)),
    "fixtures_overlay_counters_01_11": ("fixtures_counters_01_11", (-1,)),
    "fixtures_overlay_counters_01_12": ("fixtures_counters_01_13", (-1,)),
    "fixtures_overlay_counters_01_13": ("fixtures_counters_01_13", (1,)),
    "fixtures_overlay_counters_01_14": ("fixtures_counters_01_15", ()),
    "fixtures_overlay_counters_01_15": ("fixtures_counters_01_15", ()),
    "fixtures_overlay_counters_01_16": ("fixtures_counters_01_33", ()),
    "fixtures_overlay_counters_01_17": ("fixtures_counters_01_33", ()),
    "fixtures_overlay_counters_01_18": ("fixtures_counters_01_35", (1,)),
    "fixtures_overlay_counters_01_19": ("fixtures_counters_01_35", (-1,)),
    "fixtures_overlay_counters_01_20": ("fixtures_counters_01_37", (1,)),
    "fixtures_overlay_counters_01_21": ("fixtures_counters_01_37", (-1,)),
    "fixtures_overlay_counters_01_22": ("fixtures_counters_01_39", ()),
    "fixtures_overlay_counters_01_23": ("fixtures_counters_01_39", ()),
    "fixtures_overlay_counters_01_24": ("fixtures_counters_01_41", ()),
    "fixtures_overlay_counters_01_25": ("fixtures_counters_01_41", ()),
    "fixtures_overlay_counters_01_26": ("fixtures_counters_01_43", (-1,)),
    "fixtures_overlay_counters_01_27": ("fixtures_counters_01_43", (-1, 1)),
    "fixtures_overlay_counters_01_28": ("fixtures_counters_01_45", (1,)),
    "fixtures_overlay_counters_01_29": ("fixtures_counters_01_45", (-1,)),
    "fixtures_overlay_counters_01_30": ("fixtures_counters_01_47", ()),
    "fixtures_overlay_counters_01_31": ("fixtures_counters_01_47", ()),
    "fixtures_overlay_counters_01_32": ("fixtures_counters_01_49", ()),
    "fixtures_overlay_counters_01_33": ("fixtures_counters_01_49", ()),
    "fixtures_overlay_counters_01_34": ("fixtures_counters_01_51", (-1,)),
    "fixtures_overlay_counters_01_35": ("fixtures_counters_01_51", (1,)),
    "fixtures_overlay_counters_01_36": ("fixtures_counters_01_53", (-1,)),
    "fixtures_overlay_counters_01_37": ("fixtures_counters_01_53", (1,)),
    "fixtures_overlay_counters_01_38": ("fixtures_counters_01_55", ()),
    "fixtures_overlay_counters_01_39": ("fixtures_counters_01_55", ()),
    "fixtures_overlay_counters_01_40": ("fixtures_counters_01_57", ()),
    "fixtures_overlay_counters_01_41": ("fixtures_counters_01_57", ()),
    "fixtures_overlay_counters_01_42": ("fixtures_counters_01_59", (1,)),
    "fixtures_overlay_counters_01_43": ("fixtures_counters_01_59", (-1,)),
    "fixtures_overlay_counters_01_44": ("fixtures_counters_01_61", (1,)),
    "fixtures_overlay_counters_01_45": ("fixtures_counters_01_61", (-1, 1)),
    "fixtures_overlay_counters_01_46": ("fixtures_counters_01_63", ()),
    "fixtures_overlay_counters_01_47": ("fixtures_counters_01_63", ()),
    "fixtures_overlay_counters_01_48": ("fixtures_counters_01_65", ()),
    "fixtures_overlay_counters_01_49": ("fixtures_counters_01_65", ()),
    "fixtures_overlay_counters_01_50": ("fixtures_counters_01_67", (-1, 1)),
    "fixtures_overlay_counters_01_51": ("fixtures_counters_01_67", (1,)),
    "fixtures_overlay_counters_01_52": ("fixtures_counters_01_69", (-1,)),
    "fixtures_overlay_counters_01_53": ("fixtures_counters_01_69", (1,)),
    "fixtures_overlay_counters_01_54": ("fixtures_counters_01_71", ()),
    "fixtures_overlay_counters_01_55": ("fixtures_counters_01_71", ()),
    "fixtures_overlay_counters_01_56": ("fixtures_counters_01_73", ()),
    "fixtures_overlay_counters_01_57": ("fixtures_counters_01_73", ()),
    "fixtures_overlay_counters_01_58": ("fixtures_counters_01_75", (-1,)),
    "fixtures_overlay_counters_01_59": ("fixtures_counters_01_75", (-1,)),
    "fixtures_overlay_counters_01_60": ("fixtures_counters_01_77", (-1, 1)),
    "fixtures_overlay_counters_01_61": ("fixtures_counters_01_77", (-1,)),
    "fixtures_overlay_counters_01_62": ("fixtures_counters_01_79", ()),
    "fixtures_overlay_counters_01_63": ("fixtures_counters_01_79", ()),
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


def build():
    models = []
    entries = []
    made = set()
    for overlay, (tile, ways) in sorted(OVERLAYS.items()):
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
    return models, entries


props.write(BATCH, os.path.abspath(__file__), build, {name: (lambda name=name: host(name)) for name in HOSTS})
