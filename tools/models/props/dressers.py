"""Props of dressers: what the game's furniture_storage_01 overlays lay over its chests of drawers, bedside cabinets and side tables, made.

    blender --background --factory-startup --python tools/models/props/dressers.py

Each overlay is a picture of what stands on the piece, photo frames, a mug, a box of tissues, a vase, a jewellery box, a hand mirror, perfume,
and on some of them a drawer pulled out. Each thing the picture paints on the top is made as a model standing where the picture shows it, and each
drawer the picture paints pulled out is a drawer pulled out of the piece (furniture.py's open_drawers, and its pulled_drawer for the prop).

What each overlay paints was measured off the pictures: ITEMS gives the pixels of the picture each thing fills and the way it faces, DRAWERS the
drawer and how far it is out. A thing is stood so that the bottom of the pixels it fills and their middle are where its footprint's front corner
and middle are, through the game's projection of the piece (project and lift below). A picture shows only what is not behind the piece's mirror
or its other faces: the things of the overlays on a piece whose front faces north or west are made from the sides the picture shows.

Colours come from the picture where the picture paints a part as a whole patch of one colour that the shader's nine-pixel sample keeps under a
two pixel slip (a box, a mug, a terracotta pot, a perfume bulb): the table gives the pixel each takes it from, chosen as the one whose colour moves
least under that slip. Leaves, flowers and white pots are patches too small or too mixed to sample cleanly, and take the shader's own surfaces.
"""

import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bmesh  # noqa: E402
import props  # noqa: E402
from mathutils import Matrix, Vector  # noqa: E402
from props import Prop, Swap  # noqa: E402

F = props.furniture
BATCH = "dressers"

# The height of each piece's top in the piece's own model, as furniture.py builds it.
TOP = {"dresser": 0.88, "dresser_mirror": 0.88, "bedside_cabinet": 0.66, "side_table": 0.5}
# The height the picture paints each piece's top at, in the piece's model metres: the side table's sprite is a taller table than its model, so what stands
# on it is placed over the sprite's top and set down on the model's.
PICTURE_TOP = {"dresser": 0.88, "dresser_mirror": 0.88, "bedside_cabinet": 0.66, "side_table": 0.82}
HOSTS = {
    "dresser": lambda: F.dresser(),
    "dresser_mirror": lambda: F.dresser_mirror(),
    "bedside_cabinet": lambda: F.bedside_cabinet(),
    "side_table": lambda: F.side_table(),
}
OPENED = {
    "dresser": lambda name, open_drawers: F.dresser(name, open_drawers=open_drawers),
    "dresser_mirror": lambda name, open_drawers: F.dresser_mirror(name, open_drawers=open_drawers),
    "bedside_cabinet": lambda name, open_drawers: F.bedside_cabinet(name, open_drawers=open_drawers),
    "side_table": lambda name, open_drawers: F.side_table(name, open_drawers=open_drawers),
}

LEVEL_HEIGHT = 2.44949

# What a thing stands in, in the metres the game's sprite paints it at (a tile is a tile): the pieces of its footprint and height, each a box
# ("box", wide, deep, high, bottom) or an upright cylinder ("cyl", radius, high, bottom), about its middle. The picture's pixels fix where the
# bottom of this stands, and the things below are built to fill it.
KINDS = {
    "TIS": [("box", 0.32, 0.13, 0.135, 0.0)],
    "FRL": [("box", 0.22, 0.12, 0.30, 0.0)],
    "FRS": [("box", 0.145, 0.09, 0.205, 0.0)],
    "MUG": [("cyl", 0.085, 0.15, 0.0)],
    "PLA": [("cyl", 0.085, 0.12, 0.0), ("cyl", 0.07, 0.2, 0.12)],
    "PLB": [("cyl", 0.095, 0.15, 0.0), ("cyl", 0.08, 0.15, 0.15)],
    "VAS": [("cyl", 0.055, 0.28, 0.0), ("cyl", 0.08, 0.3, 0.28)],
    "JBR": [("box", 0.34, 0.22, 0.13, 0.0)],
    "JOL": [("box", 0.27, 0.17, 0.10, 0.0)],
    "JRD": [("box", 0.33, 0.22, 0.12, 0.0)],
    "JBL": [("box", 0.20, 0.15, 0.17, 0.0)],
    "MIR": [("cyl", 0.07, 0.04, 0.0), ("cyl", 0.07, 0.25, 0.04)],
    "PRF": [("cyl", 0.05, 0.1, 0.0)],
    "COM": [("box", 0.04, 0.12, 0.012, 0.0)],
    "NEC": [("cyl", 0.05, 0.015, 0.0)],
    "POL": [("cyl", 0.025, 0.09, 0.0)],
    "NOT": [("box", 0.22, 0.17, 0.02, 0.0)],
    "PEN": [("box", 0.18, 0.012, 0.012, 0.0)],
    "PAP": [("box", 0.3, 0.2, 0.012, 0.0)],
    "CMP": [("cyl", 0.04, 0.03, 0.0)],
}


# ---- the game's projection of a piece, for where a thing stands ----

def _turn(turns, x, y):
    return [(-x, y), (-y, -x), (x, -y), (y, x)][turns & 3]


def _unturn(turns, dx, dy):
    return [(-dx, dy), (-dy, -dx), (dx, -dy), (dy, dx)][turns & 3]


def project(facts, x, y, z):
    """The pixel of a 2x sprite, in its 128 by 256 frame, that paints the point (x, y, z) of the piece's model."""
    turns, cx, cy, scale = facts
    dx, dy = _turn(turns, x * scale, y * scale)
    a, b = cx + dx, cy + dy
    return (a - b) * 64 + 64, (a + b) * 32 - z * scale / LEVEL_HEIGHT * 192 + 192


def lift(facts, u, v, z):
    """The x, y of the point z up the piece's model that the sprite paints at pixel (u, v)."""
    turns, cx, cy, scale = facts
    s = (u - 64) / 64.0
    t = (v - 192 + z * scale / LEVEL_HEIGHT * 192) / 32.0
    a, b = (s + t) / 2, (t - s) / 2
    x, y = _unturn(turns, a - cx, b - cy)
    return x / scale, y / scale


def host_yaw(turns, facing):
    """The turn about the piece's upright, in degrees from its x towards its y, of a thing whose front (its -y) faces facing on the map."""
    x, y = _unturn(turns, math.cos(math.radians(facing)), math.sin(math.radians(facing)))
    return math.degrees(math.atan2(y, x)) + 90.0


def envelope(kind, yaw, scale):
    points = []
    c, s = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    for piece in KINDS[kind]:
        if piece[0] == "box":
            _, wide, deep, high, low = piece
            corners = [(sx * wide / 2, sy * deep / 2) for sx in (-1, 1) for sy in (-1, 1)]
        else:
            _, radius, high, low = piece
            corners = [(radius * math.cos(math.pi / 6 * i), radius * math.sin(math.pi / 6 * i)) for i in range(12)]
        for x, y in corners:
            for z in (low, low + high):
                points.append(((x * c - y * s) / scale, (x * s + y * c) / scale, z / scale))
    return points


def stand(facts, top, kind, box, facing):
    """Where the footprint's middle of a thing of the kind is, in the piece's model about its middle, and its turn, so that what it fills of the
    picture is box (the first and last pixel of each way): its bottom on the box's bottom and its middle on the box's middle."""
    turns, _, _, scale = facts
    yaw = host_yaw(turns, facing)
    around = project(facts, 0.0, 0.0, 0.0)
    offsets = [project(facts, x, y, z) for x, y, z in envelope(kind, yaw, scale)]
    across = [u - around[0] for u, _ in offsets]
    down = [v - around[1] for _, v in offsets]
    x0, y0, x1, y1 = box
    x, y = lift(facts, (x0 + x1 + 1) / 2 - (min(across) + max(across)) / 2, y1 + 1 - max(down), top)
    return x, y, yaw


# ---- what the pictures paint, measured ----

# For each overlay and each host the piece is drawn as: the turns Furniture gives the tile's piece, where its middle is in the square, and the
# scale its model is stood at (hostfacts.tsv).
FACTS = {
    0: {'dresser': (3, 0.68075, 0.5, 0.98583)},
    1: {'dresser': (0, 0.5, 0.68075, 0.98583)},
    2: {'dresser': (3, 0.68075, 0.5, 0.98583)},
    3: {'dresser': (0, 0.5, 0.68075, 0.98583)},
    4: {'dresser': (3, 0.68075, 0.5, 0.98583)},
    5: {'dresser': (0, 0.5, 0.68075, 0.98583)},
    6: {'dresser': (3, 0.68075, 0.5, 0.98583)},
    7: {'dresser': (0, 0.5, 0.68075, 0.98583)},
    8: {'dresser': (1, 0.31925, 0.5, 0.98583)},
    9: {'dresser': (2, 0.5, 0.31925, 0.98583)},
    10: {'dresser': (1, 0.31925, 0.5, 0.98583)},
    11: {'dresser': (2, 0.5, 0.31925, 0.98583)},
    12: {'dresser': (1, 0.31925, 0.5, 0.98583)},
    13: {'dresser': (2, 0.5, 0.31925, 0.98583)},
    14: {'dresser': (1, 0.31925, 0.5, 0.98583)},
    15: {'dresser': (2, 0.5, 0.31925, 0.98583)},
    16: {'dresser': (1, 0.31925, 0.5, 0.98583)},
    17: {'dresser': (2, 0.5, 0.31925, 0.98583)},
    18: {'dresser': (1, 0.31925, 0.5, 0.98583)},
    19: {'dresser': (2, 0.5, 0.31925, 0.98583)},
    20: {'dresser': (1, 0.31925, 0.5, 0.98583)},
    21: {'dresser': (2, 0.5, 0.31925, 0.98583)},
    22: {'dresser': (1, 0.31925, 0.5, 0.98583)},
    23: {'dresser': (2, 0.5, 0.31925, 0.98583)},
    32: {'dresser': (1, 0.31925, 0.5, 0.98583)},
    33: {'dresser': (2, 0.5, 0.31925, 0.98583)},
    34: {'dresser': (1, 0.31925, 0.5, 0.98583)},
    35: {'dresser': (2, 0.5, 0.31925, 0.98583)},
    40: {'dresser': (1, 0.31925, 0.5, 0.98583)},
    41: {'dresser': (2, 0.5, 0.31925, 0.98583)},
    42: {'dresser': (1, 0.31925, 0.5, 0.98583)},
    43: {'dresser': (2, 0.5, 0.31925, 0.98583)},
    44: {'side_table': (2, 0.5, 0.5, 0.76547)},
    45: {'side_table': (1, 0.5, 0.5, 0.76547)},
    46: {'side_table': (3, 0.5, 0.5, 0.76547)},
    47: {'side_table': (0, 0.5, 0.5, 0.76547)},
    48: {'dresser_mirror': (3, 0.68075, 0.5, 0.98583)},
    49: {'dresser_mirror': (0, 0.5, 0.68075, 0.98583)},
    50: {'dresser_mirror': (3, 0.68075, 0.5, 0.98583)},
    51: {'dresser_mirror': (0, 0.5, 0.68075, 0.98583)},
    52: {'dresser_mirror': (1, 0.31925, 0.5, 0.98583), 'dresser': (1, 0.31925, 0.5, 0.98583)},
    53: {'dresser_mirror': (2, 0.5, 0.31925, 0.98583), 'dresser': (2, 0.5, 0.31925, 0.98583)},
    54: {'dresser_mirror': (1, 0.31925, 0.5, 0.98583), 'dresser': (1, 0.31925, 0.5, 0.98583)},
    55: {'dresser_mirror': (2, 0.5, 0.31925, 0.98583), 'dresser': (2, 0.5, 0.31925, 0.98583)},
    56: {'dresser_mirror': (3, 0.68075, 0.5, 0.98583)},
    57: {'dresser_mirror': (0, 0.5, 0.68075, 0.98583)},
    58: {'dresser_mirror': (3, 0.68075, 0.5, 0.98583)},
    59: {'dresser_mirror': (0, 0.5, 0.68075, 0.98583)},
    60: {'dresser_mirror': (1, 0.31925, 0.5, 0.98583), 'dresser': (1, 0.31925, 0.5, 0.98583)},
    61: {'dresser_mirror': (2, 0.5, 0.31925, 0.98583), 'dresser': (2, 0.5, 0.31925, 0.98583)},
    62: {'dresser_mirror': (1, 0.31925, 0.5, 0.98583), 'dresser': (1, 0.31925, 0.5, 0.98583)},
    63: {'dresser_mirror': (2, 0.5, 0.31925, 0.98583), 'dresser': (2, 0.5, 0.31925, 0.98583)},
    64: {'bedside_cabinet': (1, 0.36515, 0.5, 1.31444)},
    65: {'bedside_cabinet': (2, 0.5, 0.36515, 1.31444)},
    66: {'bedside_cabinet': (3, 0.63485, 0.5, 1.31444)},
    67: {'bedside_cabinet': (0, 0.5, 0.63485, 1.31444)},
    68: {'bedside_cabinet': (1, 0.36515, 0.5, 1.31444)},
    69: {'bedside_cabinet': (2, 0.5, 0.36515, 1.31444)},
    70: {'bedside_cabinet': (3, 0.63485, 0.5, 1.31444)},
    71: {'bedside_cabinet': (0, 0.5, 0.63485, 1.31444)},
}

# The drawers each overlay paints pulled out and how far, in metres, by the drawer counted from the lowest.
DRAWERS = {
    8: {0: 0.04},
    9: {1: 0.12},
    10: {0: 0.04, 1: 0.1, 2: 0.12},
    11: {2: 0.12},
    12: {2: 0.12},
    13: {0: 0.06},
    14: {1: 0.08},
    15: {0: 0.06},
    16: {1: 0.08},
    17: {0: 0.06},
    20: {0: 0.04, 1: 0.08},
    21: {0: 0.06, 1: 0.1, 2: 0.12},
    32: {2: 0.12},
    33: {0: 0.08},
    35: {2: 0.12},
    40: {0: 0.1},
    41: {0: 0.08},
    42: {2: 0.12},
    44: {0: 0.08},
    48: {2: 0.12},
    51: {2: 0.12},
    52: {1: 0.12},
    53: {1: 0.12},
    54: {0: 0.08},
    55: {1: 0.12},
    56: {1: 0.12},
    57: {1: 0.12},
    58: {0: 0.12, 2: 0.12},
    59: {2: 0.12},
    60: {0: 0.06, 2: 0.12},
    61: {0: 0.1},
    64: {0: 0.08},
    65: {1: 0.05},
    67: {1: 0.04},
    68: {1: 0.04},
    69: {0: 0.05},
}

# What each overlay paints on top of the piece, each item as (kind, the box of the picture's pixels it fills, the way its front faces on the map in degrees from east
# towards south, its look, the picture's pixel each of its dyed parts takes its colour from).
ITEMS = {
    0: [
        ('FRL', (89, 127, 104, 161), 90, 'gold', None),
        ('TIS', (56, 156, 83, 178), 90, None, {'body': (62, 177)}),
    ],
    1: [
        ('TIS', (48, 158, 74, 174), 45, None, {'body': (48, 173)}),
    ],
    2: [
        ('FRL', (60, 153, 77, 179), 225, 'back', None),
        ('FRS', (79, 155, 90, 171), 225, 'back', None),
    ],
    3: [
        ('PLA', (24, 135, 44, 166), 0, None, {'pot': (31, 165)}),
        ('FRL', (53, 151, 70, 177), 225, 'back', None),
        ('FRS', (72, 153, 83, 169), 225, 'back', None),
    ],
    4: [
        ('MUG', (72, 137, 90, 157), 225, None, {'body': (72, 151)}),
        ('PLA', (85, 129, 105, 160), 0, None, {'pot': (101, 151)}),
        ('JOL', (56, 158, 82, 177), 90, None, {'body': (60, 163)}),
    ],
    5: [
        ('FRL', (20, 129, 35, 163), 0, 'magenta', None),
        ('FRS', (41, 156, 52, 172), 225, 'back', None),
        ('FRL', (54, 154, 71, 180), 225, 'back', None),
    ],
    6: [
        ('FRL', (57, 148, 74, 177), 225, 'back', None),
        ('PLB', (73, 138, 96, 168), 0, None, None),
        ('JBL', (92, 138, 109, 162), 90, None, {'body': (99, 157)}),
    ],
    7: [
        ('VAS', (19, 115, 42, 163), 0, None, None),
        ('JBR', (31, 150, 71, 178), 90, None, {'body': (53, 173)}),
    ],
    8: [
        ('FRL', (50, 114, 71, 140), 45, 'orange', None),
        ('FRL', (20, 130, 35, 164), 0, 'magenta', None),
        ('FRS', (34, 132, 48, 148), 45, 'magenta', None),
    ],
    9: [
        ('FRL', (52, 115, 72, 141), 45, 'dark', None),
        ('FRS', (69, 131, 86, 151), 45, 'gold', None),
        ('JRD', (75, 140, 108, 164), 90, None, {'body': (93, 159)}),
    ],
    10: [
        ('MUG', (16, 142, 35, 162), 45, None, {'body': (25, 157)}),
        ('TIS', (59, 131, 77, 148), 45, None, {'body': (59, 135)}),
        ('PAP', (40, 141, 70, 158), 45, None, None),
    ],
    11: [
        ('MUG', (76, 142, 95, 162), 225, None, {'body': (76, 157)}),
    ],
    12: [
        ('TIS', (22, 140, 49, 162), 90, None, {'body': (28, 161)}),
    ],
    13: [
        ('FRL', (89, 131, 104, 165), 90, 'gold', None),
        ('FRS', (76, 133, 90, 149), 45, 'gold', None),
    ],
    14: [
        ('FRL', (19, 130, 34, 163), 0, 'gold', None),
        ('FRL', (51, 117, 71, 142), 45, 'dark', None),
        ('JOL', (38, 138, 66, 155), 90, None, {'body': (44, 140)}),
    ],
    15: [
        ('PLA', (66, 122, 86, 153), 0, None, {'pot': (79, 153)}),
    ],
    16: [
        ('MUG', (30, 131, 49, 151), 225, None, {'body': (35, 139)}),
        ('TIS', (46, 129, 73, 150), 0, None, {'body': (57, 145)}),
        ('NOT', (16, 149, 41, 165), 45, None, None),
        ('PEN', (23, 163, 35, 168), 45, None, None),
        ('PAP', (40, 148, 55, 157), 45, None, None),
    ],
    17: [
        ('MIR', (51, 106, 68, 140), 90, None, None),
        ('POL', (55, 139, 66, 148), 0, None, None),
        ('CMP', (42, 139, 53, 148), 0, None, None),
        ('JBR', (65, 133, 105, 158), 90, None, {'body': (87, 157)}),
    ],
    18: [
        ('PLA', (48, 113, 68, 144), 0, None, {'pot': (55, 143)}),
        ('MIR', (21, 126, 38, 160), 90, None, None),
        ('PRF', (36, 143, 49, 157), 0, None, {'ball': (45, 155)}),
        ('COM', (38, 158, 59, 165), 0, None, None),
    ],
    19: [
        ('FRL', (87, 130, 102, 164), 90, 'gold', None),
        ('FRS', (74, 132, 88, 148), 45, 'gold', None),
    ],
    20: [
        ('PLA', (52, 115, 72, 146), 0, None, {'pot': (59, 145)}),
        ('FRL', (23, 133, 38, 167), 0, 'gold', None),
        ('FRS', (37, 135, 51, 151), 45, 'gold', None),
    ],
    21: [
        ('VAS', (48, 93, 70, 141), 0, None, None),
        ('JBR', (64, 134, 104, 162), 90, None, {'body': (85, 157)}),
    ],
    22: [
        ('VAS', (53, 96, 76, 144), 0, None, None),
        ('FRL', (46, 125, 66, 151), 45, 'dark', None),
    ],
    23: [
        ('JRD', (50, 129, 81, 158), 90, None, {'body': (60, 130)}),
        ('NEC', (45, 139, 54, 150), 0, None, None),
        ('PRF', (66, 138, 87, 158), 0, None, {'ball': (69, 149)}),
        ('COM', (81, 158, 87, 171), 0, None, None),
        ('MIR', (88, 125, 105, 159), 90, None, None),
    ],
    32: [
        ('PLA', (39, 122, 59, 153), 0, None, {'pot': (48, 153)}),
    ],
    33: [
        ('JRD', (48, 130, 79, 159), 90, None, {'body': (56, 132)}),
        ('NEC', (43, 140, 52, 150), 0, None, None),
        ('PRF', (64, 139, 85, 159), 0, None, {'ball': (67, 151)}),
        ('COM', (79, 159, 85, 172), 0, None, None),
        ('MIR', (86, 126, 103, 160), 90, None, None),
    ],
    34: [
        ('JBL', (30, 136, 55, 160), 90, None, {'body': (37, 155)}),
        ('MIR', (54, 114, 71, 148), 90, None, None),
        ('POL', (57, 147, 63, 155), 0, None, None),
    ],
    35: [
        ('JRD', (48, 134, 81, 158), 90, None, {'body': (53, 137)}),
        ('PEN', (64, 159, 76, 161), 45, None, None),
        ('TIS', (77, 145, 104, 166), 90, None, {'body': (83, 165)}),
    ],
    40: [
        ('FRL', (26, 133, 41, 166), 0, 'dark', None),
        ('FRS', (40, 135, 54, 151), 45, 'dark', None),
    ],
    41: [
        ('FRL', (53, 121, 73, 147), 45, 'dark', None),
        ('FRS', (77, 134, 91, 150), 45, 'dark', None),
        ('FRL', (90, 132, 105, 165), 90, 'dark', None),
    ],
    42: [
        ('MUG', (25, 141, 44, 161), 45, None, {'body': (35, 155)}),
    ],
    43: [
        ('FRL', (50, 128, 70, 151), 45, 'dark', None),
        ('VAS', (66, 105, 92, 150), 0, None, None),
        ('FRS', (80, 144, 94, 160), 45, 'dark', None),
    ],
    44: [
        ('PLB', (56, 151, 79, 181), 0, None, None),
    ],
    45: [
        ('FRL', (38, 153, 53, 187), 0, 'gold', None),
        ('FRS', (52, 155, 66, 171), 45, 'gold', None),
    ],
    46: [
        ('PLB', (51, 156, 74, 186), 0, None, None),
    ],
    47: [
        ('FRL', (57, 164, 74, 190), 225, 'back', None),
        ('FRS', (44, 166, 55, 182), 225, 'back', None),
    ],
    48: [
        ('JOL', (52, 159, 63, 176), 90, 'cut', {'body': (63, 159)}),
    ],
    49: [
        ('JBR', (62, 150, 80, 176), 90, 'cut', {'body': (62, 150)}),
    ],
    50: [
        ('PLB', (47, 143, 63, 173), 0, 'cut', None),
    ],
    51: [
        ('FRL', (62, 149, 77, 175), 225, 'back', None),
    ],
    52: [
        ('FRL', (51, 117, 73, 144), 45, 'orange', None),
        ('NOT', (30, 144, 56, 164), 45, None, None),
        ('PEN', (38, 158, 50, 164), 45, None, None),
    ],
    53: [
        ('FRS', (51, 128, 67, 145), 45, 'gold', None),
        ('JBR', (60, 135, 99, 158), 90, None, {'body': (81, 157)}),
    ],
    54: [
        ('TIS', (48, 132, 74, 148), 45, None, {'body': (48, 147)}),
    ],
    55: [
        ('PLA', (66, 121, 86, 152), 0, None, {'pot': (73, 151)}),
    ],
    56: [
        ('FRL', (49, 151, 63, 176), 225, 'back', None),
    ],
    57: [
        ('PLA', (62, 143, 74, 174), 0, 'cut', {'pot': (62, 173)}),
    ],
    58: [
        ('JBL', (53, 155, 63, 177), 90, 'cut', {'body': (63, 177)}),
    ],
    59: [
        ('TIS', (62, 155, 76, 175), 90, 'cut', {'body': (66, 175)}),
    ],
    60: [
        ('VAS', (48, 93, 71, 141), 0, None, None),
        ('FRL', (29, 129, 44, 162), 0, 'gold', None),
        ('FRS', (44, 131, 57, 147), 45, 'gold', None),
    ],
    61: [
        ('FRL', (85, 130, 100, 164), 90, 'gold', None),
        ('FRS', (72, 132, 86, 148), 45, 'gold', None),
    ],
    62: [
        ('MUG', (20, 141, 39, 161), 45, None, {'body': (29, 155)}),
        ('TIS', (55, 131, 81, 147), 45, None, {'body': (79, 143)}),
        ('PAP', (44, 140, 75, 157), 45, None, None),
    ],
    63: [
        ('FRL', (48, 116, 69, 143), 45, 'dark', None),
        ('FRS', (66, 132, 80, 150), 45, 'gold', None),
        ('JRD', (71, 141, 104, 165), 90, None, {'body': (89, 159)}),
    ],
    64: [
        ('TIS', (29, 144, 56, 166), 90, None, {'body': (35, 165)}),
    ],
    65: [
        ('MUG', (57, 132, 76, 152), 225, None, {'body': (57, 147)}),
        ('TIS', (68, 147, 94, 162), 45, None, {'body': (68, 161)}),
    ],
    66: [
        ('TIS', (69, 143, 95, 159), 45, None, {'body': (69, 159)}),
    ],
    67: [
        ('TIS', (29, 141, 56, 163), 90, None, {'body': (38, 163)}),
    ],
    68: [
        ('TIS', (25, 146, 51, 162), 45, None, {'body': (25, 161)}),
    ],
    69: [
        ('TIS', (49, 137, 76, 159), 90, None, {'body': (66, 159)}),
    ],
    70: [
        ('TIS', (48, 150, 75, 172), 90, None, {'body': (54, 171)}),
    ],
    71: [
        ('TIS', (32, 142, 59, 164), 90, None, {'body': (48, 163)}),
    ],
}


# ---- the things, built in their own space: the middle of the footprint on the floor, the front towards -y, in the metres of the picture ----

class Stood:
    """A model of a thing that stands where it does on a piece: what is built is turned, scaled to the piece's model and moved there as it is taken."""

    def __init__(self, name, at, yaw, scale):
        self.model = F.Model(name, centred=False)
        self.scale = scale
        self.matrix = Matrix.Translation(at) @ Matrix.Rotation(math.radians(yaw), 4, "Z") @ Matrix.Scale(scale, 4)

    def _put(self, bm):
        bmesh.ops.transform(bm, matrix=self.matrix, verts=bm.verts[:])
        return bm

    def hard(self, bm, surface, bevel=0.003, segments=1):
        self.model.hard(self._put(bm), surface, bevel=bevel * self.scale, segments=segments)

    def turned(self, bm, surface, sharp=40.0):
        self.model.turned(self._put(bm), surface, sharp=sharp)

    def plain(self, bm, surface):
        self.model.plain(self._put(bm), surface)


def box(bm, x0, x1, y0, y1, z0, z1):
    F.cuboid(bm, x0, x1, y0, y1, z0, z1)


def dims(kind, piece=0):
    return KINDS[kind][piece][1:]


def tissue(s, rng, look):
    wide, deep, high, _ = dims("TIS")
    body_high = high - 0.03
    body = bmesh.new()
    box(body, -wide / 2, wide / 2, -deep / 2, deep / 2, 0.0, body_high)
    s.hard(body, "paint", bevel=0.004)
    slot = bmesh.new()
    box(slot, -wide * 0.36, wide * 0.36, -0.017, 0.017, body_high, body_high + 0.0006)
    s.plain(slot, "rubber")
    puff = bmesh.new()
    for k, (x, y, lean) in enumerate(((-0.06, -0.008, -0.03), (-0.005, 0.004, 0.012), (0.05, -0.002, 0.03))):
        top = 0.03 + 0.008 * (k % 2)
        F.leaf_strip(puff, [(x, y, body_high - 0.004), (x + lean * 0.3, y, body_high + top * 0.5), (x + lean, y + 0.004 * (k - 1), body_high + top),
                            (x + lean * 1.5, y + 0.006 * (k - 1), body_high + top * 0.8)], [0.034, 0.05, 0.05, 0.018], thick=0.003)
    s.turned(puff, "linen", sharp=60.0)


FRAME_LOOK = {"gold": "wood", "dark": "dark_wood", "magenta": "vinyl", "orange": "wood", "back": "wood", None: "wood"}


def frame(s, rng, look, kind):
    wide, _, high, _ = dims(kind)
    rail, thick = 0.022 * wide / 0.22 + 0.006, 0.018
    tilt = 11.0
    mould, mat, photo, board, strut, people = bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new()
    half = wide / 2
    box(mould, -half, half, -thick / 2, thick / 2, 0.0, rail)
    box(mould, -half, half, -thick / 2, thick / 2, high - rail, high)
    box(mould, -half, -half + rail, -thick / 2, thick / 2, rail, high - rail)
    box(mould, half - rail, half, -thick / 2, thick / 2, rail, high - rail)
    box(mat, -half + rail - 0.002, half - rail + 0.002, -thick / 2 + 0.006, thick / 2 - 0.005, rail - 0.002, high - rail + 0.002)
    inner = 0.016 * wide / 0.22 + 0.004
    box(photo, -half + rail + inner, half - rail - inner, -thick / 2 + 0.0055, -thick / 2 + 0.0075, rail + inner, high - rail - inner)
    box(board, -half + rail - 0.002, half - rail + 0.002, thick / 2 - 0.005, thick / 2 + 0.004, rail - 0.002, high - rail + 0.002)
    pw, ph, front = wide - 2 * (rail + inner), high - 2 * (rail + inner), -thick / 2 + 0.0075
    for x, tall, girth in ((-0.2, 0.34, 0.16), (0.2, 0.28, 0.14)):
        cx, base = x * pw, rail + inner + ph * 0.1
        F.sphere(people, cx, front - 0.0004, base + ph * tall * 0.78, pw * 0.09, segments=8)
        box(people, cx - pw * girth / 2, cx + pw * girth / 2, front - 0.0008, front, base, base + ph * tall * 0.62)
    for bm in (mould, mat, photo, board, people):
        bmesh.ops.rotate(bm, cent=(0.0, 0.0, 0.0), matrix=Matrix.Rotation(math.radians(-tilt), 3, "X"), verts=bm.verts[:])
    top_back = Vector((0.0, thick / 2 + 0.004, high * 0.72)) @ Matrix.Rotation(math.radians(-tilt), 3, "X")
    F.beam(strut, (0.0, top_back.y, top_back.z), (0.0, 0.10, 0.0), 0.016, 0.01)
    s.hard(mould, FRAME_LOOK[look], bevel=0.0025)
    s.plain(mat, "linen")
    s.plain(photo, "paint")
    s.turned(people, "cloth", sharp=50.0)
    s.hard(board, "wood", bevel=0.002)
    s.hard(strut, "dark_wood", bevel=0.002)


def mug(s, rng, look):
    r, high = dims("MUG")[0] * 0.82, dims("MUG")[1]
    body = bmesh.new()
    F.lathe(body, [(0.0, 0.0), (r, 0.0), (r * 1.03, high), (r * 1.03 - 0.008, high), (r - 0.008, 0.014), (0.0, 0.014)], segments=16)
    s.turned(body, "ceramic", sharp=50.0)
    handle = bmesh.new()
    arc = [(r * 1.0 + 0.004, 0.0, high * 0.82), (r + 0.05, 0.0, high * 0.8), (r + 0.062, 0.0, high * 0.5), (r + 0.05, 0.0, high * 0.22), (r * 1.0, 0.0, high * 0.2)]
    for a, b in zip(arc, arc[1:]):
        F.beam(handle, a, b, 0.018, 0.016)
    s.hard(handle, "ceramic", bevel=0.003)
    drink = bmesh.new()
    box(drink, -r + 0.01, r - 0.01, -r + 0.01, r - 0.01, high - 0.02, high - 0.012)
    s.plain(drink, "dark_wood")


def pot(s, rng, look, top, bottom, high):
    clay = bmesh.new()
    F.lathe(clay, [(0.0, 0.0), (bottom, 0.0), (top - 0.004, high - 0.014), (top + 0.008, high - 0.014), (top + 0.008, high), (top - 0.006, high), (top - 0.006, high - 0.012),
                   (0.0, high - 0.012)], segments=16)
    s.turned(clay, "ceramic", sharp=50.0)
    earth = bmesh.new()
    F.lathe(earth, [(0.0, high - 0.011), (top - 0.007, high - 0.011), (top - 0.007, high - 0.01), (0.0, high - 0.01)], segments=16)
    s.turned(earth, "rubber")


def bloom_head(heads, x, y, z):
    F.lathe(heads, [(0.0, z - 0.008), (0.015, z - 0.003), (0.02, z + 0.003), (0.01, z + 0.009), (0.0, z + 0.006)], cx=x, cy=y, segments=8)


def leaves(s, rng, look, base, spread, count, length, blooms=0, bloom_surface=None, height=0.3):
    plant = bmesh.new()
    for k in range(count):
        yaw = k * 360 / count + rng.uniform(-15, 15)
        F.leaf_strip(plant, F.arching((0.0, 0.0, base), yaw, rng.uniform(45, 70), length * rng.uniform(0.8, 1.1), 0.02, 4), [0.004, 0.024, 0.032, 0.02, 0.002], thick=0.002)
    heads = bmesh.new()
    for k in range(blooms):
        angle, out = rng.uniform(0, 2 * math.pi), spread * math.sqrt(rng.uniform(0.0, 0.8))
        x, y, z = out * math.cos(angle), out * math.sin(angle), base + (height - base) * rng.uniform(0.55, 1.0)
        F.beam(plant, (x * 0.3, y * 0.3, base - 0.005), (x, y, z - 0.01), 0.005, 0.005)
        bloom_head(heads, x, y, z)
    s.turned(plant, "plastic", sharp=50.0)
    if blooms:
        s.turned(heads, bloom_surface, sharp=60.0)


def blooms_only(s, rng, look, base, spread, count, height, surface):
    heads = bmesh.new()
    for k in range(count):
        angle, out = rng.uniform(0, 2 * math.pi), spread * math.sqrt(rng.uniform(0.0, 0.8))
        bloom_head(heads, out * math.cos(angle), out * math.sin(angle), base + (height - base) * rng.uniform(0.55, 1.0))
    s.turned(heads, surface, sharp=60.0)


def vase_glass(s, rng, look):
    glass = bmesh.new()
    F.lathe(glass, [(0.0, 0.0), (0.05, 0.0), (0.056, 0.02), (0.04, 0.12), (0.024, 0.2), (0.03, 0.28), (0.024, 0.28), (0.019, 0.2), (0.033, 0.12), (0.047, 0.026), (0.0, 0.01)],
            segments=14)
    s.turned(glass, "pane", sharp=50.0)


def vase_stems(rng):
    return [(k * 72 + rng.uniform(-20, 20), rng.uniform(68, 80)) for k in range(5)]


def vase_leaves(s, rng, look):
    plant = bmesh.new()
    for k in range(4):
        yaw = k * 90 + rng.uniform(-20, 20)
        F.leaf_strip(plant, F.arching((0.0, 0.0, 0.27), yaw, rng.uniform(40, 62), 0.2, 0.04, 4), [0.004, 0.026, 0.034, 0.026, 0.002], thick=0.002)
    for yaw, rise in vase_stems(rng):
        F.beam(plant, (0.0, 0.0, 0.27), F.arching((0.0, 0.0, 0.27), yaw, rise, 0.24, 0.02, 4)[-1], 0.005, 0.005)
    s.turned(plant, "plastic", sharp=50.0)


def vase_blooms(s, rng, look):
    # The leaves drew four leaf angles and two numbers a stem first: the stems stand where the leaves' stems do.
    for k in range(4):
        rng.uniform(-20, 20)
        rng.uniform(40, 62)
    heads = bmesh.new()
    for yaw, rise in vase_stems(rng):
        tip = F.arching((0.0, 0.0, 0.27), yaw, rise, 0.24, 0.02, 4)[-1]
        for petal in range(3):
            F.leaf_strip(heads, F.arching(tip, yaw + petal * 40 - 40, rng.uniform(40, 70), 0.07, 0.01, 3), [0.002, 0.016, 0.012, 0.002], thick=0.002)
    s.turned(heads, "wood", sharp=60.0)


def jewel(s, rng, look, kind):
    wide, deep, high, _ = dims(kind)
    feet = 0.018 if kind == "JBR" else 0.0
    low = high * 0.55
    base, lid, trim = bmesh.new(), bmesh.new(), bmesh.new()
    box(base, -wide / 2, wide / 2, -deep / 2, deep / 2, feet, feet + low)
    box(lid, -wide / 2 - 0.004, wide / 2 + 0.004, -deep / 2 - 0.004, deep / 2 + 0.004, feet + low, high)
    s.hard(base, "paint", bevel=0.005)
    s.hard(lid, "paint", bevel=0.006, segments=2)
    for x in ((-wide * 0.3, wide * 0.3) if kind == "JBL" else (0.0,)):
        box(trim, x - 0.014, x + 0.014, -deep / 2 - 0.007, -deep / 2 - 0.003, feet + low - 0.018, feet + low + 0.012)
    if feet:
        for x in (-1, 1):
            for y in (-1, 1):
                box(trim, x * (wide / 2 - 0.03) - 0.01, x * (wide / 2 - 0.03) + 0.01, y * (deep / 2 - 0.03) - 0.01, y * (deep / 2 - 0.03) + 0.01, 0.0, feet)
    s.hard(trim, "metal", bevel=0.002)


def mirror(s, rng, look):
    base, pole, ring, glass = bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new()
    F.lathe(base, [(0.0, 0.0), (0.06, 0.0), (0.062, 0.008), (0.04, 0.016), (0.012, 0.022), (0.0, 0.022)], segments=14)
    F.lathe(pole, [(0.007, 0.02), (0.007, 0.17), (0.0, 0.17)], segments=8)
    rx, rz = 0.068, 0.088
    tilt = Matrix.Translation((0.0, 0.0, 0.26)) @ Matrix.Rotation(math.radians(90 + 14), 4, "X")
    F.lathe(ring, [(rx + 0.007 * math.cos(math.pi / 3 * i), 0.007 * math.sin(math.pi / 3 * i)) for i in range(6)], segments=20, closed=True, caps=False, sy=rz / rx, to=tilt)
    F.lathe(glass, [(0.0, 0.0), (rx - 0.002, 0.0)], segments=20, sy=rz / rx, to=tilt)
    s.turned(base, "steel", sharp=50.0)
    s.turned(pole, "steel", sharp=50.0)
    s.turned(ring, "steel", sharp=50.0)
    s.plain(glass, "pane")


def flask(s, rng, look):
    bottle, cap = bmesh.new(), bmesh.new()
    box(bottle, -0.045, 0.005, -0.012, 0.022, 0.0, 0.07)
    box(cap, -0.03, -0.01, -0.004, 0.014, 0.07, 0.085)
    s.hard(bottle, "straw", bevel=0.004)
    s.hard(cap, "metal", bevel=0.002)


def ball(s, rng, look):
    bulb = bmesh.new()
    F.sphere(bulb, 0.03, -0.022, 0.03, 0.03, segments=10)
    s.turned(bulb, "vinyl", sharp=60.0)


def comb(s, rng, look):
    plate = bmesh.new()
    box(plate, -0.06, 0.06, -0.018, 0.018, 0.0, 0.004)
    for k in range(9):
        x = -0.054 + k * 0.0135
        box(plate, x, x + 0.006, -0.03, -0.018, 0.0, 0.003)
    s.plain(plate, "wood")


def necklace(s, rng, look):
    pearls = bmesh.new()
    for k in range(12):
        a = 2 * math.pi * k / 12
        F.sphere(pearls, 0.04 * math.cos(a), 0.034 * math.sin(a), 0.008, 0.008, segments=6)
    s.turned(pearls, "linen", sharp=60.0)


def polish(s, rng, look):
    bottles, caps, odd = bmesh.new(), bmesh.new(), bmesh.new()
    for x, y, into in ((-0.012, 0.01, bottles), (0.008, -0.004, bottles), (0.02, 0.012, odd)):
        F.lathe(into, [(0.0, 0.0), (0.014, 0.0), (0.015, 0.012), (0.013, 0.04), (0.0, 0.04)], cx=x, cy=y, segments=8)
        F.lathe(caps, [(0.006, 0.04), (0.007, 0.07), (0.0, 0.072)], cx=x, cy=y, segments=6)
    s.turned(bottles, "vinyl", sharp=50.0)
    s.turned(odd, "cloth", sharp=50.0)
    s.turned(caps, "plastic", sharp=50.0)


def compact(s, rng, look):
    case, inner = bmesh.new(), bmesh.new()
    F.lathe(case, [(0.0, 0.0), (0.04, 0.0), (0.04, 0.026), (0.0, 0.026)], segments=14)
    F.lathe(inner, [(0.0, 0.0262), (0.028, 0.0262)], segments=14)
    s.turned(case, "plastic", sharp=50.0)
    s.plain(inner, "vinyl")


def notepad(s, rng, look):
    pad, lines = bmesh.new(), bmesh.new()
    box(pad, -0.11, 0.11, -0.085, 0.085, 0.0, 0.016)
    for k in range(6):
        y = -0.07 + k * 0.026
        box(lines, -0.1, 0.1, y, y + 0.006, 0.0162, 0.0166)
    s.hard(pad, "paint", bevel=0.002)
    s.plain(lines, "pane")


def pencil(s, rng, look):
    wood, tip, rubber = bmesh.new(), bmesh.new(), bmesh.new()
    F.beam(wood, (-0.09, 0.0, 0.006), (0.07, 0.0, 0.006), 0.011, 0.011)
    F.beam(tip, (0.07, 0.0, 0.006), (0.09, 0.0, 0.006), 0.006, 0.006)
    F.beam(rubber, (-0.1, 0.0, 0.006), (-0.09, 0.0, 0.006), 0.011, 0.011)
    s.hard(wood, "wood", bevel=0.001)
    s.hard(tip, "dark_wood", bevel=0.001)
    s.hard(rubber, "vinyl", bevel=0.001)


def papers(s, rng, look):
    for k, (x, y, turn) in enumerate(((0.0, 0.0, 0.0), (0.01, 0.015, 7.0))):
        sheet = bmesh.new()
        box(sheet, -0.1, 0.1, -0.14, 0.14, 0.0015 * k, 0.0015 * (k + 1))
        bmesh.ops.rotate(sheet, cent=(0.0, 0.0, 0.0), matrix=Matrix.Rotation(math.radians(turn), 3, "Z"), verts=sheet.verts[:])
        bmesh.ops.translate(sheet, vec=(x, y, 0.0), verts=sheet.verts[:])
        s.plain(sheet, "paint")


# What each kind is made of: its models as (suffix of the name, what builds it, the surfaces the picture colours or None, the part of the thing's
# dyes that colours them). A thing in more than one model has a colour for each: a pot, its leaves and its flowers.
MADE = {
    "TIS": [("", tissue, ("paint",), "body")],
    "FRL": [("", lambda s, r, look: frame(s, r, look, "FRL"), None, None)],
    "FRS": [("", lambda s, r, look: frame(s, r, look, "FRS"), None, None)],
    "MUG": [("", mug, ("ceramic",), "body")],
    "PLA": [("_pot", lambda s, r, look: pot(s, r, look, 0.085, 0.05, 0.12), ("ceramic",), "pot"),
            ("_leaves", lambda s, r, look: leaves(s, r, look, 0.115, 0.1, 9, 0.15, blooms=14, bloom_surface="linen", height=0.3), None, None)],
    "PLB": [("_pot", lambda s, r, look: pot(s, r, look, 0.095, 0.06, 0.15), None, None),
            ("_leaves", lambda s, r, look: leaves(s, r, look, 0.145, 0.1, 10, 0.14), None, None),
            ("_blooms", lambda s, r, look: blooms_only(s, r, look, 0.145, 0.09, 12, 0.29, "straw"), None, None)],
    "VAS": [("_glass", vase_glass, None, None), ("_leaves", vase_leaves, None, None), ("_blooms", vase_blooms, None, None)],
    "JBR": [("", lambda s, r, look: jewel(s, r, look, "JBR"), ("paint",), "body")],
    "JOL": [("", lambda s, r, look: jewel(s, r, look, "JOL"), ("paint",), "body")],
    "JRD": [("", lambda s, r, look: jewel(s, r, look, "JRD"), ("paint",), "body")],
    "JBL": [("", lambda s, r, look: jewel(s, r, look, "JBL"), ("paint",), "body")],
    "MIR": [("", mirror, None, None)],
    "PRF": [("_flask", flask, None, None), ("_ball", ball, ("vinyl",), "ball")],
    "COM": [("", comb, None, None)],
    "NEC": [("", necklace, None, None)],
    "POL": [("", polish, None, None)],
    "NOT": [("", notepad, None, None)],
    "PEN": [("", pencil, None, None)],
    "PAP": [("", papers, None, None)],
    "CMP": [("", compact, None, None)],
}


def tag(drawers):
    return "".join(str(i) for i in sorted(drawers))


def build():
    models = []
    entries = []
    made = set()
    hosts_built = {name: make() for name, make in HOSTS.items()}
    origins = {name: props.origin(model) for name, model in hosts_built.items()}
    opened = {}
    for overlay in sorted(FACTS):
        name = "storage_01_%d" % overlay
        pulled = DRAWERS.get(overlay, {})
        for host, facts in sorted(FACTS[overlay].items()):
            around = hosts_built[host]
            if pulled:
                key = (host, tag(pulled))
                if key not in opened:
                    opened[key] = OPENED[host]("%s_%s_open_%s" % (BATCH, host, tag(pulled)), tuple(sorted(pulled)))
                    models.append(opened[key])
                around = opened[key]
                entries.append(Swap(name, host, around.name))
                for drawer, out in sorted(pulled.items()):
                    leaf = "%s_%s_drawer_%d_%d" % (BATCH, "dresser" if host == "dresser_mirror" else host, drawer, round(out * 100))
                    # A dresser and a dresser with a mirror have one chest, so one drawer between them.
                    if leaf not in made:
                        made.add(leaf)
                        pull = F.pulled_drawer(leaf, around.drawers[drawer], out, around.drawer_deep, "wood")
                        props.stands_on(pull, around)
                        models.append(pull)
                    entries.append(Prop(name, host, leaf, "host"))
            at = origins[host]
            for k, (kind, box_px, facing, look, dyes) in enumerate(ITEMS.get(overlay, [])):
                x, y, yaw = stand(facts, PICTURE_TOP[host], kind, box_px, facing)
                # The side table's picture paints things as wide as its top, and the model's top is smaller: they stand no nearer its edge than this.
                if host == "side_table":
                    x, y = max(-0.2, min(0.2, x)), max(-0.2, min(0.2, y))
                where = Vector((x + at.x, y + at.y, TOP[host]))
                for suffix, builder, dyed, part in MADE[kind]:
                    model_name = "%s_%d_%d%s" % (BATCH, overlay, k, suffix)
                    if model_name not in made:
                        made.add(model_name)
                        stood = Stood(model_name, where, yaw, 1.0 / facts[3])
                        builder(stood, random.Random(overlay * 100 + k), look)
                        props.stands_on(stood.model, around)
                        models.append(stood.model)
                    if dyed is None:
                        entries.append(Prop(name, host, model_name, "plain"))
                    else:
                        u, v = dyes[part]
                        px, py = lift(facts, u + 0.5, v + 0.5, PICTURE_TOP[host])
                        entries.append(Prop(name, host, model_name, ("over", dyed, (px + at.x, py + at.y, PICTURE_TOP[host]))))
    return models, entries


props.write(BATCH, os.path.abspath(__file__), build, HOSTS)
