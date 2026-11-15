"""Props of crafted plank shelves: what ContainerOverlays lays over carpentry_02_64 and carpentry_02_65, made.

    blender --background --factory-startup --python tools/models/props/plankshelves.py

The eight pictures books&misc_01_60..63 and books&misc_02_60..63 are the game's pictures of a shelf holding many things
(60 and 61) or few (62 and 63), over a shelf facing south (64) or east (65). Measured off the pictures (the rows and
groups below): every one of them paints books and nothing else, in three rows. The many pictures fill each row from end
to end, with books stood up. The few pictures leave each row to one or two groups of books, a run of books stood up, a
stack lying flat or both.

So the books are made as props on the plank shelf's boards, not as marks coloured from the pictures: the picture's three
rows stand at about 0.0, 0.65 and 1.3 m in the shelf's metres, and furniture.py's plank_shelves has its boards at 0.06,
0.53, 1.02 and 1.5, so a point of the picture over a board is not a point of a book on it. The three rows go on the three
lowest boards.

The many pictures are one model, filled as furniture.py's home_goods fills a bookcase, since the game paints the same
books over a bookcase and over these shelves. Each few picture lays its own groups, from where the picture paints them.
Each book is a piece of its own on the books surface, which gives every book a colour of its own.
"""

import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bmesh  # noqa: E402
import props  # noqa: E402
from mathutils import Matrix  # noqa: E402
from props import Prop  # noqa: E402

F = props.furniture
BATCH = "plankshelves"

# As furniture.plank_shelves builds the shelf: the boards' feet and their thickness, the face of the sides' insides and
# how far the boards reach to the front and to the back.
BOARD_FEET = (0.06, 0.53, 1.02)
BOARD_THICK = 0.025
INSIDE = 0.455
FRONT, BACK = -0.19, 0.17
# How far a book keeps from the board's front edge and its back, and the room over each of the three boards the books
# stand on: to the underside of the next board.
EDGE = 0.015
ROOM = (0.53 - 0.085, 1.02 - 0.555, 1.5 - 1.045)

# The shelf's side edge the books keep clear of.
CLEAR = INSIDE - 0.015

# For each overlay: how it is held, "many", or the groups it paints on each of the three rows, lowest first. A group is
# ("run", x, books): books stood up side by side, ("flat", x, books): books lying in a stack. x is where its middle is on
# the shelf's front, in the shelf's metres, from the picture's columns: x = (u - 64) / 64 - 0.26 + 0.09 for a shelf facing
# south and x = (u - 64) / 64 + 0.26 - 0.09 for one facing east, with u the group's middle column in the picture, 0.26 the
# difference of the shelf's centre on the square (carpentry_02_64 at 0.5, 0.24, carpentry_02_65 at 0.24, 0.5) and 0.09 how
# far in from the front the middle of a book is, since the picture's columns slant with depth.
OVERLAYS = {
    "books&misc_01_60": "many",
    "books&misc_01_61": "many",
    "books&misc_02_60": "many",
    "books&misc_02_61": "many",
    "books&misc_01_62": [
        [("run", -0.23, 3), ("flat", 0.06, 2)],
        [("run", -0.27, 2), ("run", 0.09, 3)],
        [("flat", -0.22, 1), ("run", 0.11, 2)],
    ],
    "books&misc_01_63": [
        [("run", 0.08, 7)],
        [("run", 0.00, 6)],
        [("run", -0.03, 5), ("flat", 0.31, 1)],
    ],
    "books&misc_02_62": [
        [("flat", -0.03, 2)],
        [("run", -0.22, 2), ("run", 0.14, 1)],
        [("flat", -0.08, 3)],
    ],
    "books&misc_02_63": [
        [("flat", -0.05, 2)],
        [("run", 0.32, 3)],
        [("flat", 0.06, 2)],
    ],
}
SEEDS = {"many": 701, "books&misc_01_62": 702, "books&misc_01_63": 703, "books&misc_02_62": 704, "books&misc_02_63": 705}
BEVEL = 0.003


def host():
    return F.plank_shelves()


def tops():
    return [foot + BOARD_THICK for foot in BOARD_FEET]


def many_books(name):
    """Books along each of the three boards from end to end, as a bookcase's many are."""
    model = F.Model(name, centred=False)
    part, finish = F.parts(model)
    levels = [(top, room, FRONT + EDGE, BACK - EDGE) for top, room in zip(tops(), ROOM)]
    F.home_goods(part, random.Random(SEEDS["many"]), levels, -CLEAR, CLEAR, True, True)
    finish(BEVEL)
    props.stands_on(model, host())
    return model


def run(bm, pick, x, count, top, room):
    """Books stood up side by side, spines to the front, centred on x."""
    widths = [pick.uniform(0.022, 0.05) for _ in range(count)]
    at = x - sum(widths) / 2
    if at < -CLEAR or at + sum(widths) > CLEAR:
        raise SystemExit("a run of %d books at %.2f does not fit the shelf" % (count, x))
    for each in widths:
        tall = min(pick.uniform(0.19, 0.29), room - 0.02)
        deep = pick.uniform(0.14, 0.19)
        back = FRONT + EDGE + pick.uniform(0.0, 0.05)
        F.cuboid(bm, at, at + each - 0.002, back, back + deep, top, top + tall)
        at += each


def flat(bm, pick, x, count, top):
    """Books lying in a stack, each turned a little from the one under it, centred on x."""
    z = top
    for _ in range(count):
        wide, deep, thick = pick.uniform(0.2, 0.26), pick.uniform(0.15, 0.19), pick.uniform(0.026, 0.04)
        shift_x, shift_y = pick.uniform(-0.012, 0.012), pick.uniform(0.0, 0.02)
        turn = pick.uniform(-0.12, 0.12)
        middle_y = FRONT + EDGE + deep / 2 + shift_y
        if abs(x + shift_x) + wide / 2 + deep / 2 * 0.12 > CLEAR:
            raise SystemExit("a stack of books at %.2f does not fit the shelf" % x)
        before = set(bm.verts)
        F.cuboid(bm, x + shift_x - wide / 2, x + shift_x + wide / 2, middle_y - deep / 2, middle_y + deep / 2, z, z + thick)
        bmesh.ops.rotate(bm, cent=(x + shift_x, middle_y, z), matrix=Matrix.Rotation(turn, 3, "Z"), verts=[v for v in bm.verts if v not in before])
        z += thick


def few_books(name, overlay):
    """The groups the overlay paints, each on its own row."""
    model = F.Model(name, centred=False)
    pick = random.Random(SEEDS[overlay])
    books = bmesh.new()
    for (top, room), groups in zip(zip(tops(), ROOM), OVERLAYS[overlay]):
        for kind, x, count in groups:
            if kind == "run":
                run(books, pick, x, count, top, room)
            else:
                flat(books, pick, x, count, top)
    model.hard(books, "books", bevel=BEVEL, segments=1)
    props.stands_on(model, host())
    return model


def build():
    models = [many_books("%s_books_many" % BATCH)]
    entries = []
    for overlay, held in sorted(OVERLAYS.items()):
        if held == "many":
            entries.append(Prop(overlay, "plank_shelves", models[0].name, "plain"))
        else:
            few = few_books("%s_books_%s" % (BATCH, overlay.split("_", 1)[1]), overlay)
            models.append(few)
            entries.append(Prop(overlay, "plank_shelves", few.name, "plain"))
    return models, entries


props.write(BATCH, os.path.abspath(__file__), build, {"plank_shelves": host})
