"""Props of the food in a shop's fridge: what ContainerOverlays lays over the glass-fronted fridges and coolers, made.

    blender --background --factory-startup --python tools/models/props/fridgefood.py

The game paints these pictures over a fridge while it holds something: food_01 and food_02, each a set of goods and two
pictures of it, one of the shelves full (many) and one of them nearly bare (few). They are painted behind the glass of
the door, and nothing of them stands on the door's front: the tiles are appliances_refrigeration_01_22, 23, 40 and 41
(a shop's single-door fridge, black and white) and 16 to 19 (a cooler of two doors under a lit sign, one door to a tile),
which the mod draws as glass_fridge and glass_cooler (furniture.py) with the glass of their doors laid by
ShopFittings.java. So each picture is made as the goods on the shelves behind that glass: a mark for each place a shelf
is painted, coloured from the picture there, so a picture of a sparse fridge makes a sparse fridge.

What the pictures paint was measured by projecting each shelf of the model through the sprite projection and asking
where the picture is painted. Each picture lies over one tile of one piece, and a tile of the cooler is one door: the
left half of the model's x for tiles 16 and 18, the right half for 17 and 19. The marks stand where that picture paints
them on the shelf, in two ranks, the front one and the one behind it, and are coloured from the picture at their base.
The pictures are of rows of one thing: cans, bottles, a carton of juice, a jug, a tub, a pack. OVERLAYS says which, row
by row from the top shelf down, for each picture.

The single-door fridge's pictures paint three rows, on its three shelves; the bottom of it, under the lowest shelf, is
bare in every picture. The cooler's paint four, on its floor and its three shelves.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bmesh  # noqa: E402
import props  # noqa: E402
from props import Marks  # noqa: E402

F = props.furniture
BATCH = "fridgefood"

# The two hosts, as furniture.py builds them (glass_fridges).
HOSTS = {
    "glass_fridge": F.GLASS_FRIDGE,
    "glass_cooler": F.GLASS_COOLER,
}


def host(name):
    return F.glass_front_fridge(name, **HOSTS[name])


# Where the goods stand: from the front of the shelf in two ranks, 0.15 apart, and one mark each PITCH along the shelf,
# the width of the thing that stands in the row and a centimetre over.
RANK_FROM, RANK_SPAN, RANKS = -0.26, 0.30, 2
PITCH = {"can": 0.08, "bottle": 0.075, "jug": 0.115, "carton": 0.09, "tub": 0.145, "tray": 0.17, "jar": 0.095, "roll": 0.15}
# The span of x the marks of a picture are spread across: inside the fridge's glass, or inside one of the cooler's two.
FRIDGE_SPAN = (-0.27, 0.27)
COOLER_LEFT, COOLER_RIGHT = (-0.80, -0.04), (0.04, 0.80)

# What each picture paints, as the things in each row of the shelves from the top down. Every picture of a set paints
# the same rows of the same things over the whole of its tile's door, and the picture of few has the rows of the picture
# of many it goes with, most of the places bare.
#   fridge, set 1: tall cartons, tubs of cheese or cake, flat packs
#   fridge, set 2: cartons and jars, tubs and packs, tubs and sausages
#   cooler, left of a door as the picture shows it, set 1: bottles of cola, bottles of a green drink, cans of soup, cans
#   of a blue drink; set 2: cartons of juice, cans, bottles of water, bottles of a lime drink
#   cooler, right, set 1: bottles of a green drink, of a pink and orange one, of water, then jugs of juice; set 2: cans,
#   cans, bottles of cola, bottles of a pink and orange drink
FRIDGE_ROWS = {
    1: ("carton", "tub", "tray"),
    2: (("carton", "jar"), ("tub", "tray"), ("tub", "roll")),
}
COOLER_ROWS = {
    (1, "left"): ("bottle", "bottle", "can", "can"),
    (1, "right"): ("bottle", "bottle", "bottle", "jug"),
    (2, "left"): ("carton", "can", "bottle", "bottle"),
    (2, "right"): ("can", "can", "bottle", "bottle"),
}
# By tile (appliances_refrigeration_01_N): the number of the picture of many and of few over it, and for a cooler which of
# its two doors it is, the left or the right as the model is looked at from its front. The pictures are food_01_N and food_02_N.
COOLER_TILES = {18: (88, 92, "left"), 19: (89, 93, "right"), 16: (90, 94, "left"), 17: (91, 95, "right")}
FRIDGE_TILES = {22: (54, 62), 23: (55, 63), 40: (54, 62), 41: (55, 63)}

OVERLAYS = {}
for _set in (1, 2):
    for _tile, (_many, _few, _side) in COOLER_TILES.items():
        for _number in (_many, _few):
            OVERLAYS["food_0%d_%d" % (_set, _number)] = ("glass_cooler", _tile, COOLER_LEFT if _side == "left" else COOLER_RIGHT, COOLER_ROWS[(_set, _side)])
    for _tile, (_many, _few) in FRIDGE_TILES.items():
        for _number in (_many, _few):
            OVERLAYS["food_0%d_%d" % (_set, _number)] = ("glass_fridge", _tile, FRIDGE_SPAN, FRIDGE_ROWS[_set])


# Where the goods are built inside their host, on the second shelf, where the room they are shaded by is the host's.
def stand_at(name):
    shelf = HOSTS[name]["shelves"][1]
    return -0.2 if name == "glass_fridge" else -0.55, -0.15, shelf


def made(model, around):
    props.stands_on(model, around)
    return model


def can(name, around):
    model = F.Model(name)
    x, y, z = stand_at(HOSTS_BY_MODEL[name])
    body, top = bmesh.new(), bmesh.new()
    F.lathe(body, [(0.0, z), (0.031, z), (0.033, z + 0.004), (0.033, z + 0.108), (0.0, z + 0.108)], cx=x, cy=y, segments=10)
    F.lathe(top, [(0.0, z + 0.108), (0.03, z + 0.108), (0.03, z + 0.12), (0.0, z + 0.12)], cx=x, cy=y, segments=10)
    model.turned(body, "paint")
    model.turned(top, "steel")
    return made(model, around)


def bottle(name, around):
    model = F.Model(name)
    x, y, z = stand_at(HOSTS_BY_MODEL[name])
    body, cap = bmesh.new(), bmesh.new()
    F.lathe(body, [(0.0, z), (0.028, z), (0.03, z + 0.004), (0.03, z + 0.14), (0.017, z + 0.18), (0.013, z + 0.2), (0.0, z + 0.2)], cx=x, cy=y, segments=10)
    F.lathe(cap, [(0.0, z + 0.2), (0.015, z + 0.2), (0.015, z + 0.214), (0.0, z + 0.214)], cx=x, cy=y, segments=8)
    model.turned(body, "paint")
    model.turned(cap, "plastic")
    return made(model, around)


def jug(name, around):
    model = F.Model(name)
    x, y, z = stand_at(HOSTS_BY_MODEL[name])
    body, neck = bmesh.new(), bmesh.new()
    F.cuboid(body, x - 0.05, x + 0.05, y - 0.035, y + 0.035, z, z + 0.16)
    model.hard(body, "paint", bevel=0.008, segments=1)
    F.lathe(neck, [(0.0, z + 0.16), (0.022, z + 0.16), (0.022, z + 0.19), (0.0, z + 0.19)], cx=x, cy=y, segments=8)
    model.turned(neck, "plastic")
    return made(model, around)


def carton(name, around):
    model = F.Model(name)
    x, y, z = stand_at(HOSTS_BY_MODEL[name])
    body = bmesh.new()
    F.cuboid(body, x - 0.038, x + 0.038, y - 0.025, y + 0.025, z, z + 0.19)
    model.hard(body, "paint", bevel=0.004, segments=1)
    return made(model, around)


def tub(name, around):
    model = F.Model(name)
    x, y, z = stand_at(HOSTS_BY_MODEL[name])
    body = bmesh.new()
    F.lathe(body, [(0.0, z), (0.058, z), (0.066, z + 0.012), (0.066, z + 0.05), (0.05, z + 0.068), (0.0, z + 0.072)], cx=x, cy=y, segments=12)
    model.turned(body, "paint")
    return made(model, around)


def tray(name, around):
    model = F.Model(name)
    x, y, z = stand_at(HOSTS_BY_MODEL[name])
    body = bmesh.new()
    F.cuboid(body, x - 0.08, x + 0.08, y - 0.05, y + 0.05, z, z + 0.035)
    model.hard(body, "paint", bevel=0.005, segments=1)
    return made(model, around)


def jar(name, around):
    model = F.Model(name)
    x, y, z = stand_at(HOSTS_BY_MODEL[name])
    body, lid = bmesh.new(), bmesh.new()
    F.lathe(body, [(0.0, z), (0.038, z), (0.04, z + 0.004), (0.04, z + 0.1), (0.0, z + 0.1)], cx=x, cy=y, segments=10)
    F.lathe(lid, [(0.0, z + 0.1), (0.041, z + 0.1), (0.041, z + 0.116), (0.0, z + 0.116)], cx=x, cy=y, segments=10)
    model.turned(body, "paint")
    model.turned(lid, "steel")
    return made(model, around)


def roll(name, around):
    model = F.Model(name)
    x, y, z = stand_at(HOSTS_BY_MODEL[name])
    body = bmesh.new()
    F.lathe(body, [(0.0, 0.0), (0.03, 0.0), (0.03, 0.14), (0.0, 0.14)], segments=10, to=F.along_x(x - 0.07, y, z + 0.03))
    model.turned(body, "paint")
    return made(model, around)


MAKERS = {"can": can, "bottle": bottle, "jug": jug, "carton": carton, "tub": tub, "tray": tray, "jar": jar, "roll": roll}
# Which goods stand in which host.
GOODS = {"glass_cooler": ("can", "bottle", "jug", "carton"), "glass_fridge": ("carton", "tub", "tray", "jar", "roll")}
HOSTS_BY_MODEL = {"%s_%s_%s" % (BATCH, host_name.split("_")[1], kind): host_name for host_name, kinds in GOODS.items() for kind in kinds}


def good(host_name, kind):
    return "%s_%s_%s" % (BATCH, host_name.split("_")[1], kind)


def build():
    models = []
    around = {name: host(name) for name in HOSTS}
    for host_name, kinds in GOODS.items():
        for kind in kinds:
            models.append(MAKERS[kind](good(host_name, kind), around[host_name]))
    entries = []
    for overlay, (host_name, _tile, (low, high), rows) in sorted(OVERLAYS.items()):
        shelves = HOSTS[host_name]["shelves"]
        floors = ((HOSTS[host_name]["floor"],) + tuple(shelves))[::-1]
        for floor, kinds in zip(floors, rows):
            names = (kinds,) if isinstance(kinds, str) else kinds
            count = max(1, int(round((high - low) / max(PITCH[kind] for kind in names))))
            entries.append(Marks(overlay, host_name, [good(host_name, kind) for kind in names], (low, RANK_FROM, floor), (high - low, 0.0, 0.0),
                                 (0.0, RANK_SPAN, 0.0), (count, RANKS), ("paint",)))
    return models, entries


props.write(BATCH, os.path.abspath(__file__), build, {name: (lambda name=name: host(name)) for name in HOSTS})
