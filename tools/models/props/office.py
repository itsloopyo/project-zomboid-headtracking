"""Props of the office furniture: what the game lays over its filing cabinets and its lockers, made.

    blender --background --factory-startup --python tools/models/props/office.py

The filing cabinets (location_business_office_generic_01_16, 17, 24, 25, 32..35) have sixteen overlays, office_01_0 to
15, and each paints one or two drawers pulled out of the three. Where the picture shows the front the drawer's front
moves out and the box behind it shows, and where it shows folders in the box they are made: those drawers are pulled a
hand or more. A cabinet whose front faces north or west (turns 0 and 3) is drawn with only the edge of a pulled drawer
showing past its side, and how far each is pulled was read off the width of that edge. So the cabinet is drawn as
furniture.filing_cabinet with the drawers left out over their pockets, and each drawer stands out as pulled_drawer.

The lockers (furniture_storage_02_0 to 5, 12, 13) have twelve overlays, storage_01_24 to 31 and 36 to 39. Those on the
two-door grey cabinets put cardboard boxes on the top (24 to 29), stand one door ajar (24, 25, 30, 31), or both (24, 25),
and those on the blue and yellow lockers stand a door ajar (37, 38) or are wear (36, 39: a few dozen pixels, none more than 57 of 255 from the locker's own
colour, so there is nothing to make). A door ajar is drawn as the locker with that door's bay open (locker_open, as
furniture.locker with one door left off over a pocket) and the door standing open on its hinge.

How each overlay was read off its picture is in FILING and LOCKERS. Only the numbers are kept here.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bmesh  # noqa: E402
import props  # noqa: E402
from mathutils import Matrix, Vector  # noqa: E402
from props import Prop, Swap  # noqa: E402

F = props.furniture
BATCH = "office"

# As furniture.locker builds it: half its width, the y of its front and its back, the height of its top, and the height
# its doors stand from and to.
HALF, FRONT, BACK, TOP = 0.38, -0.23, 0.23, 1.83
BODY_LOW = 0.08
DOOR_LOW, DOOR_HIGH = 0.095, TOP - 0.015
# The door's outer edge is where it hangs, and its face stands a door's thickness in front of the body's.
HINGE = HALF - 0.012
DOOR_THICK = 0.014

# Each filing cabinet overlay: the cabinet tile it lies over, then each drawer it paints pulled as (drawer counted
# from the lowest, centimetres pulled out, folders showing in the box). The tiles turn 2 and 1 show their front;
# turns 0 and 3 are read off the edge past the side, which is the pull itself (a pixel is 1.6 cm along the pull).
# Nothing pulls farther than 12 cm: with the pulls, that is the farthest a prop may stand beyond its piece.
FILING = {
    "office_01_0": ("location_business_office_generic_01_16", ((2, 5, False),)),
    "office_01_1": ("location_business_office_generic_01_16", ((0, 5, False),)),
    "office_01_2": ("location_business_office_generic_01_17", ((1, 12, True),)),
    "office_01_3": ("location_business_office_generic_01_17", ((2, 5, False),)),
    "office_01_4": ("location_business_office_generic_01_32", ((2, 5, False), (1, 10, True))),
    "office_01_5": ("location_business_office_generic_01_32", ((0, 5, False),)),
    "office_01_6": ("location_business_office_generic_01_33", ((1, 5, False),)),
    "office_01_7": ("location_business_office_generic_01_33", ((2, 5, False), (0, 12, True))),
    "office_01_8": ("location_business_office_generic_01_24", ((2, 12, False), (1, 3, False))),
    "office_01_9": ("location_business_office_generic_01_24", ((1, 12, False),)),
    "office_01_10": ("location_business_office_generic_01_25", ((2, 3, False),)),
    "office_01_11": ("location_business_office_generic_01_25", ((1, 10, False),)),
    "office_01_12": ("location_business_office_generic_01_34", ((2, 10, False), (0, 3, False))),
    "office_01_13": ("location_business_office_generic_01_34", ((1, 5, False),)),
    "office_01_14": ("location_business_office_generic_01_35", ((0, 3, False),)),
    "office_01_15": ("location_business_office_generic_01_35", ((2, 10, False), (1, 3, False))),
}

# Each locker overlay: the tile it lies over, the door it stands ajar (-1 the left as the locker is looked at, 1 the
# right, 0 none) and by how many degrees, and the cartons on its top, each as kind, x, y, width along x, depth along y,
# height, all in the locker's space, and the point of the host's space where the overlay paints the carton's top, for
# its colour. A carton's place is where its footprint lies on the picture's top face as a share of that face, taken
# across the locker's own top: the pictures' lockers are wider than the model, so the carton's size shrinks with them.
# The colour point is where the game's own projection puts the carton in the picture, so it can lie outside the
# carton's footprint on the scaled model.
LOCKERS = {
    "storage_01_24": ("furniture_storage_02_0", -1, 8, (("shut", 0.193, 0.054, 0.157, 0.202, 0.239, (0.171, -0.070, 2.069)),)),
    "storage_01_25": ("furniture_storage_02_1", 1, 8, (("shut", 0.150, 0.048, 0.191, 0.327, 0.292, (0.114, 0.068, 2.122)),
                                                        ("battered", -0.144, 0.048, 0.227, 0.164, 0.192, (-0.266, 0.049, 2.022)))),
    "storage_01_26": ("furniture_storage_02_2", 0, 0, (("shut", 0.168, 0.020, 0.156, 0.218, 0.245, (0.327, 0.013, 2.075)),
                                                        ("shut", -0.203, 0.096, 0.287, 0.218, 0.292, (-0.154, 0.125, 2.122)))),
    "storage_01_27": ("furniture_storage_02_3", 0, 0, (("shut", 0.084, 0.066, 0.239, 0.252, 0.259, (0.193, -0.068, 2.089)),)),
    "storage_01_28": ("furniture_storage_02_0", 0, 0, (("shut", -0.090, 0.027, 0.229, 0.269, 0.272, (-0.233, -0.125, 2.102)),
                                                        ("shut", 0.193, 0.054, 0.181, 0.175, 0.172, (0.171, -0.070, 2.002)))),
    "storage_01_29": ("furniture_storage_02_1", 0, 0, (("shut", -0.150, 0.123, 0.191, 0.177, 0.219, (-0.270, 0.135, 2.049)),
                                                        ("shut", 0.126, 0.020, 0.191, 0.327, 0.292, (0.081, 0.035, 2.122)))),
    "storage_01_30": ("furniture_storage_02_0", -1, 8, ()),
    "storage_01_31": ("furniture_storage_02_1", 1, 8, ()),
    "storage_01_37": ("furniture_storage_02_13", 1, 7, ()),
    "storage_01_38": ("furniture_storage_02_4", 1, 15, ()),
}

SIDE = {-1: "l", 1: "r"}


def filing_cabinet(name="filing_cabinet", open_drawers=()):
    return F.filing_cabinet(name, open_drawers=open_drawers)


def locker_door(doors, trim, side):
    """The door of a locker on one side, shut, as furniture.locker builds it: its panel, two groups of four louvres and
    the plate and bar of its handle."""
    low, high = sorted((side * 0.004, side * HINGE))
    F.cuboid(doors, low, high, FRONT - DOOR_THICK, FRONT, DOOR_LOW, DOOR_HIGH)
    for group in (0.30, 1.48):
        for n in range(4):
            z = group + n * 0.035
            F.prism_y(doors, ((low + high) / 2, z + 0.004, high - low - 0.12, 0.012, FRONT - 0.022),
                      ((low + high) / 2, z + 0.012, high - low - 0.12, 0.018, FRONT - 0.014))
    F.cuboid(trim, side * 0.06 - 0.02, side * 0.06 + 0.02, FRONT - 0.018, FRONT - 0.014, 0.93, 1.05)
    F.bar_handle(trim, side * 0.06 - 0.006, side * 0.06 + 0.006, FRONT - 0.018, 0.95, 1.03, proud=0.02, thick=0.008)


def locker():
    return F.locker()


def locker_open(name, side):
    """The locker with the door on side left off over a bay as deep as the locker, the other door shut."""
    model = F.Model(name)
    body = bmesh.new()
    for x in (-1, 1):
        for y in (FRONT + 0.04, BACK - 0.04):
            F.cuboid(body, x * (HALF - 0.04) - 0.02, x * (HALF - 0.04) + 0.02, y - 0.02, y + 0.02, 0.0, BODY_LOW)
    low, high = sorted((side * 0.004, side * HINGE))
    F.pocketed_body(body, -HALF, HALF, FRONT, BACK, BODY_LOW, TOP, [dict(x0=low, x1=high, z0=DOOR_LOW, z1=DOOR_HIGH)], (0,))
    model.hard(body, "paint", bevel=0.005)
    doors, trim = bmesh.new(), bmesh.new()
    locker_door(doors, trim, -side)
    model.hard(doors, "paint", bevel=0.003, segments=1)
    model.hard(trim, "metal", bevel=0.002, segments=1)
    return model


def ajar_door(name, side, degrees, around):
    """The door on side standing degrees open on its hinge, built where it stands in the locker's space."""
    model = F.Model(name, centred=False)
    doors, trim = bmesh.new(), bmesh.new()
    locker_door(doors, trim, side)
    # The free edge swings out to the front, which is -Y.
    turn = Matrix.Rotation(math.radians(side * degrees), 3, "Z")
    for bm in (doors, trim):
        bmesh.ops.rotate(bm, cent=(side * HINGE, FRONT - DOOR_THICK / 2, 0.0), matrix=turn, verts=bm.verts[:])
    model.hard(doors, "paint", bevel=0.003, segments=1)
    model.hard(trim, "metal", bevel=0.002, segments=1)
    props.stands_on(model, around)
    return model


def folders(name, slot, out, deep, around):
    """Manila folders standing in a drawer pulled out metres, over the length of the box that shows and a hand beyond,
    each with a tab on its top edge, the tabs stepped across the box."""
    model = F.Model(name, centred=False)
    bm = bmesh.new()
    inside = slot["face"] - out + slot["thick"]
    x0, x1 = slot["x0"] + 0.03, slot["x1"] - 0.03
    low, high = slot["z0"] + 0.01 + 0.008, slot["z1"] - 0.02 - 0.03
    count = int((out + 0.1) / 0.022)
    for n in range(count):
        y = inside + 0.02 + n * 0.022
        if y + 0.003 > inside + deep - 0.012:
            raise SystemExit("%s: the folders run out of the drawer's box" % name)
        F.cuboid(bm, x0, x1, y, y + 0.003, low, high)
        step = (n % 3 - 1) * 0.09
        F.cuboid(bm, step - 0.03, step + 0.03, y, y + 0.003, high, high + 0.014)
    model.plain(bm, "worktop")
    props.stands_on(model, around)
    return model


def carton(name, kind, at, size, around):
    """A cardboard box of size (width, depth, height) standing on its base at at, which is a place on the locker's top."""
    if kind == "battered":
        model = F.battered_carton(name, *size)
    else:
        model = F.carton(name, *size)
    model.vertices = [(position + Vector(at), normal, surface) for position, normal, surface in model.vertices]
    model.centred = False
    props.stands_on(model, around)
    return model


def build():
    models = []
    entries = []
    made = set()

    def add(model):
        if model.name in made:
            raise SystemExit("%s is made twice" % model.name)
        made.add(model.name)
        models.append(model)
        return model

    for overlay, (tile, pulled) in sorted(FILING.items(), key=lambda item: int(item[0].rsplit("_", 1)[1])):
        ways = tuple(sorted(n for n, _, _ in pulled))
        swap_name = "%s_filing_cabinet_open_%s" % (BATCH, "".join(str(n) for n in ways))
        if swap_name not in made:
            add(filing_cabinet(swap_name, ways))
        swapped = next(m for m in models if m.name == swap_name)
        entries.append(Swap(overlay, "filing_cabinet", swap_name))
        for n, centimetres, held in pulled:
            drawer = "%s_drawer_%d_%d" % (BATCH, n, centimetres)
            if drawer not in made:
                model = F.pulled_drawer(drawer, swapped.drawers[n], centimetres / 100.0, swapped.drawer_deep, "paint")
                props.stands_on(model, swapped)
                add(model)
            entries.append(Prop(overlay, "filing_cabinet", drawer))
            if held:
                leaf = "%s_folders_%d_%d" % (BATCH, n, centimetres)
                if leaf not in made:
                    add(folders(leaf, swapped.drawers[n], centimetres / 100.0, swapped.drawer_deep, swapped))
                entries.append(Prop(overlay, "filing_cabinet", leaf, "plain"))

    shut = None
    for overlay, (tile, side, degrees, cartons) in sorted(LOCKERS.items(), key=lambda item: int(item[0].rsplit("_", 1)[1])):
        if side:
            swap_name = "%s_locker_open_%s" % (BATCH, SIDE[side])
            if swap_name not in made:
                add(locker_open(swap_name, side))
            around = next(m for m in models if m.name == swap_name)
            entries.append(Swap(overlay, "locker", swap_name))
            leaf = "%s_locker_door_%s%d" % (BATCH, SIDE[side], degrees)
            if leaf not in made:
                add(ajar_door(leaf, side, degrees, around))
            entries.append(Prop(overlay, "locker", leaf))
        else:
            if shut is None:
                shut = locker()
            around = shut
        for n, (kind, x, y, width, depth, high, dye) in enumerate(cartons):
            leaf = "%s_carton_%s_%d" % (BATCH, overlay.rsplit("_", 1)[1], n)
            add(carton(leaf, kind, (x, y, TOP), (width, depth, high), around))
            entries.append(Prop(overlay, "locker", leaf, ("over", ("paint",), dye)))
    return models, entries


props.write(BATCH, os.path.abspath(__file__), build, {"filing_cabinet": filing_cabinet, "locker": locker})
