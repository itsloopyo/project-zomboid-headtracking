"""Props of baths and basins: what TileOverlays lays over fixtures_bathroom_01 and fixtures_sinks_01, made.

    blender --background --factory-startup --python tools/models/props/bathsinks.py

fixtures_01_24..39 lie on the bath's two squares: a bottle or two, a bar of soap, a sponge on its rim, or a towel
over the near side. fixtures_01_0..3 and 8..11 lie on the steel basin: a mug, plates and cups in the bowl, or a
checked tea towel over its rim. fixtures_01_16..19 paint two cloth-wrapped pipes by a basin's tap, and 20 a bar of
soap resting inside a basin's bowl.

The pictures' sinks are bigger than the mod's basin (table_basin), so what the pictures paint in the bowl is stood in
the mod's bowl, each piece where it is in the picture scaled down to it and kept clear of the others. Pieces the
pictures paint past an end of the mod's bath stand on that end's ledge, so on the bath each piece stands where the
picture paints it, drawn in onto the model's end or side ledge. Where a picture paints a stain or a crumb on a plate,
a drip by the soap, or a wrapper's white corner, nothing is made: they are a few pixels wide.

What each picture paints was measured off the overlays: BATH, SINKS and PIPES below hold only the numbers.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bmesh  # noqa: E402
import props  # noqa: E402
from mathutils import Matrix, Vector  # noqa: E402
from props import Prop  # noqa: E402

F = props.furniture
BATCH = "bathsinks"

HOSTS = {
    "bath": lambda: F.bath(),
    "table_basin": lambda: F.table_basin("table_basin", "ceramic"),
    "table_basin_steel": lambda: F.table_basin("table_basin_steel", "steel"),
}

# The bath as furniture.bath builds it: the top of its rim, and the middle of the flat of the rim at each end and along
# each side. The end ledges are 6 cm wide at 0.79 to 0.85 from the middle, the side ledges 6 cm at 0.32 to 0.38.
BATH_TOP = 0.56
END_LEDGE = 0.82
SIDE_LEDGE = 0.35
# A point of an overlay's picture, at this height, is a point of the host's space whose projection lands on the
# picture's pixel: the height only has to be the one the measuring used.
BATH_DYE_Z = 0.60
SINK_DYE_Z = 0.08

# The basin as furniture.table_basin builds it, in the space it is built in: the middle of the bowl and its squash,
# and the inside of the bowl as (radius, height) from its floor to its lip.
BOWL_Y = -0.02
BOWL_SQUASH = 0.8
BOWL_INSIDE = [(0.0, 0.032), (0.08, 0.035), (0.145, 0.055), (0.182, 0.097), (0.197, 0.13)]
RIM_RADIUS = 0.205
TAP_Y = 0.19
# Where a picture paints a piece in the bowl, it stands this share of the way from the middle of the mod's bowl, and
# in the room the bowl has.
BOWL_FIT = 0.4
BOWL_ROOM = (0.115, 0.092)

# What the bath's overlays paint: (kind, x, y, dye x, dye y) in the bath's space. x, y is the piece's middle where the
# picture puts it, drawn in to the model's ledge: a bottle or a soap on an end of the rim (turned along it), a sponge
# on a side. The dye point is a painted pixel of the piece's picture, at BATH_DYE_Z: the world shader takes a dye's colour from nine
# texels 6 apart round it, so for a small piece it is the pixel those nine give the piece's own colour.
BATH = {
    "fixtures_01_24": [("bottle", -END_LEDGE, 0.26, -1.004, 0.207)],
    "fixtures_01_25": [("soap", END_LEDGE, -0.26, 0.855, -0.372)],
    "fixtures_01_26": [("soap", END_LEDGE, 0.26, 0.832, 0.379)],
    "fixtures_01_27": [("bottle", -END_LEDGE, 0.26, -1.020, 0.497)],
    "fixtures_01_28": [("bottle", -END_LEDGE, 0.26, -1.043, 0.277), ("flask", -END_LEDGE, 0.17, -0.934, 0.199)],
    "fixtures_01_29": [("bottle", END_LEDGE, -0.26, 0.777, -0.356), ("sponge", 0.70, SIDE_LEDGE, 0.824, 0.378)],
    "fixtures_01_30": [("bottle", END_LEDGE, 0.26, 0.808, 0.450), ("sponge", 0.70, -SIDE_LEDGE, 0.832, -0.371)],
    "fixtures_01_31": [("bottle", -END_LEDGE, 0.26, -0.856, 0.504), ("flask", -END_LEDGE, 0.17, -0.934, 0.395)],
    "fixtures_01_32": [("soap", END_LEDGE, -0.24, 0.981, -0.214)],
    "fixtures_01_33": [("bottle", -END_LEDGE, 0.26, -0.792, 0.465)],
    "fixtures_01_34": [("bottle", -END_LEDGE, -0.26, -0.738, -0.442)],
    "fixtures_01_35": [("soap", END_LEDGE, 0.22, 0.958, 0.191)],
    "fixtures_01_36": [("bottle", END_LEDGE, -0.26, 1.036, -0.300), ("sponge", 0.70, SIDE_LEDGE, 0.770, 0.372)],
    "fixtures_01_37": [("towel", 1, 0.004, 0.762)],
    "fixtures_01_38": [("towel", -1, -0.011, -0.778)],
    "fixtures_01_39": [("bottle", END_LEDGE, 0.26, 0.965, 0.199), ("sponge", 0.70, -SIDE_LEDGE, 0.762, -0.348)],
}
# A towel hangs over the side of the tub the picture shows, +1 the back and -1 the front, from x0 to x1.
TOWEL_SPAN = (-0.68, -0.04)

# What the basins' overlays paint, where the picture puts each, as (kind, x, y, dye x, dye y) in the basin's space
# (the middle of its footprint), the dye point at SINK_DYE_Z; a dye of None leaves the piece its own colour. A cloth
# is placed by the angle of its picture's middle from the bowl's, and dyed from a red square of its checks.
SINKS = {
    "fixtures_01_0": [("mug", -0.273, -0.182, -0.360, -0.120), ("plate", -0.223, -0.177, -0.219, -0.167), ("dish", 0.006, -0.242, -0.219, -0.167)],
    "fixtures_01_1": [("mug", 0.249, -0.205, 0.344, -0.136), ("plate", 0.207, -0.208, 0.203, -0.183), ("dish", -0.029, -0.265, 0.203, -0.183)],
    "fixtures_01_2": [("mug", 0.244, 0.009, 0.250, -0.057), ("cup", -0.057, 0.005, -0.031, 0.037), ("cup", -0.145, 0.086, -0.031, 0.037),
                      ("dish", 0.181, 0.049, 0.196, 0.091)],
    "fixtures_01_3": [("mug", -0.221, 0.033, -0.235, -0.042), ("cup", 0.169, 0.110, 0.047, 0.052), ("cup", 0.080, 0.029, 0.047, 0.052),
                      ("dish", -0.165, 0.081, -0.180, 0.107)],
    "fixtures_01_8": [("cloth", -0.180, 0.043, -0.258, 0.153)],
    "fixtures_01_9": [("cloth", -0.150, -0.001, -0.078, 0.130), ("plate", 0.216, -0.191, 0.274, -0.269)],
    "fixtures_01_10": [("cloth", 0.142, 0.191, 0.250, 0.130), ("cup", -0.139, 0.104, None, None)],
    "fixtures_01_11": [("cloth", 0.295, 0.019, 0.328, -0.042), ("mug", -0.124, 0.075, -0.094, 0.099), ("plate", -0.181, 0.010, -0.243, 0.044)],
    "fixtures_01_20": [("soap", -0.314, 0.085, -0.368, 0.044)],
}
# The two cloth-wrapped pipes by the tap, dyed from a painted pixel of each picture, at SINK_DYE_Z.
PIPES = {
    "fixtures_01_16": (0.015, -0.120),
    "fixtures_01_17": (-0.031, -0.136),
    "fixtures_01_18": (0.110, 0.271),
    "fixtures_01_19": (-0.094, 0.287),
}
# The size of each dish in the bowl: its radius.
DISH_RADIUS = {"plate": 0.05, "dish": 0.038, "cup": 0.024, "mug": 0.03}


def put(bm, matrix):
    bmesh.ops.transform(bm, matrix=matrix, verts=bm.verts[:])


def at(x, y, z, yaw=0.0):
    return Matrix.Translation((x, y, z)) @ Matrix.Rotation(math.radians(yaw), 4, "Z")


def bottle(model, matrix):
    """A bottle of shampoo 0.24 high, 6 cm across and 3.5 deep, standing on the middle of its foot."""
    wide, deep, body, shoulder = 0.06, 0.035, 0.165, 0.03
    shell = bmesh.new()
    F.cuboid(shell, -wide / 2, wide / 2, -deep / 2, deep / 2, 0.0, body)
    F.prism(shell, (0.0, 0.0, wide, deep, body), (0.0, 0.0, 0.028, 0.022, body + shoulder))
    put(shell, matrix)
    model.hard(shell, "plastic", bevel=0.006, segments=2)
    cap = bmesh.new()
    F.lathe(cap, [(0.0, body + shoulder), (0.011, body + shoulder), (0.011, body + shoulder + 0.012), (0.014, body + shoulder + 0.014),
                  (0.014, body + shoulder + 0.044), (0.0, body + shoulder + 0.046)], segments=12)
    put(cap, matrix)
    model.turned(cap, "rubber")


def flask(model, matrix):
    """A small round bottle 0.115 high, 3.4 cm across, with a cap."""
    shell = bmesh.new()
    F.lathe(shell, [(0.0, 0.0), (0.016, 0.0), (0.017, 0.004), (0.017, 0.07), (0.012, 0.084), (0.0, 0.086)], segments=14)
    put(shell, matrix)
    model.turned(shell, "plastic", sharp=50.0)
    cap = bmesh.new()
    F.lathe(cap, [(0.0, 0.084), (0.009, 0.084), (0.009, 0.1), (0.0, 0.102)], segments=10)
    put(cap, matrix)
    model.turned(cap, "rubber")


def soap(model, matrix):
    """A bar of soap, 9 cm by 5.5 by 2.6, its length along x."""
    bar = bmesh.new()
    F.cuboid(bar, -0.045, 0.045, -0.0275, 0.0275, 0.0, 0.026)
    put(bar, matrix)
    model.hard(bar, "plastic", bevel=0.009, segments=2)


def sponge(model, matrix):
    """A sponge, 11.5 cm by 5.5 by 4, its length along x."""
    block = bmesh.new()
    F.cuboid(block, -0.0575, 0.0575, -0.0275, 0.0275, 0.0, 0.04)
    put(block, matrix)
    model.hard(block, "cloth", bevel=0.013, segments=3)


def plate(model, matrix, radius):
    """A plate lying flat, its rim flared up from a flat floor."""
    k = radius / 0.062
    shell = bmesh.new()
    F.lathe(shell, [(0.0, 0.0), (0.045 * k, 0.0), (0.056 * k, 0.006), (0.062 * k, 0.013), (0.0585 * k, 0.014), (0.0545 * k, 0.0085),
                    (0.043 * k, 0.003), (0.0, 0.003)], segments=22)
    put(shell, matrix)
    model.turned(shell, "ceramic", sharp=50.0)


def cup(model, matrix, radius):
    """A cup, a little taller than it is wide."""
    k = radius / 0.024
    shell = bmesh.new()
    F.lathe(shell, [(0.0, 0.0), (0.016 * k, 0.0), (0.018 * k, 0.003), (0.024 * k, 0.034 * k), (0.0225 * k, 0.034 * k), (0.0165 * k, 0.006),
                    (0.0, 0.005)], segments=16)
    put(shell, matrix)
    model.turned(shell, "ceramic", sharp=50.0)


def mug(model, matrix):
    """A mug 7.5 cm high and 6 cm across with its handle on the +x side."""
    shell = bmesh.new()
    F.lathe(shell, [(0.0, 0.0), (0.027, 0.0), (0.03, 0.005), (0.03, 0.075), (0.0265, 0.075), (0.0265, 0.008), (0.0, 0.008)], segments=18)
    put(shell, matrix)
    model.turned(shell, "ceramic", sharp=50.0)
    grip = bmesh.new()
    F.cuboid(grip, 0.027, 0.05, -0.0045, 0.0045, 0.058, 0.068)
    F.cuboid(grip, 0.044, 0.053, -0.0045, 0.0045, 0.016, 0.068)
    F.cuboid(grip, 0.027, 0.05, -0.0045, 0.0045, 0.016, 0.026)
    put(grip, matrix)
    model.hard(grip, "ceramic", bevel=0.003, segments=1)


def sheet(bm, top, push, cells, thick):
    """A thin sheet of cloth: top(i, j) is the point of its outer face at cell corner i, j and push(i, j) the unit
    normal there, the sheet thick under it. Returns the corners of the outer face by (i, j)."""
    across, along = cells
    outer = {(i, j): top(i, j) for i in range(across + 1) for j in range(along + 1)}
    inner = {(i, j): outer[i, j] - push(i, j) * thick for i, j in outer}
    face = {key: bm.verts.new(point) for key, point in outer.items()}
    back = {key: bm.verts.new(point) for key, point in inner.items()}
    for i in range(across):
        for j in range(along):
            bm.faces.new((face[i, j], face[i + 1, j], face[i + 1, j + 1], face[i, j + 1]))
            bm.faces.new((back[i, j], back[i, j + 1], back[i + 1, j + 1], back[i + 1, j]))
    for i in range(across):
        bm.faces.new((face[i, 0], back[i, 0], back[i + 1, 0], face[i + 1, 0]))
        bm.faces.new((face[i, along], face[i + 1, along], back[i + 1, along], back[i, along]))
    for j in range(along):
        bm.faces.new((face[0, j], face[0, j + 1], back[0, j + 1], back[0, j]))
        bm.faces.new((face[across, j], back[across, j], back[across, j + 1], face[across, j + 1]))
    return outer


def path_normals(path):
    """The outer unit normal at each point of a path of (d, z), left of its direction of travel."""
    out = []
    for i in range(len(path)):
        a, b = path[max(i - 1, 0)], path[min(i + 1, len(path) - 1)]
        dx, dz = b[0] - a[0], b[1] - a[1]
        length = math.hypot(dx, dz)
        out.append((-dz / length, dx / length))
    return out


# The towel over the tub's side, as the path of its middle (distance from the middle line of the bath, height): over
# the flat of the rim, round the edge and down the wall to the floor. The picture paints it ending at the floor.
TOWEL_PATH = [(0.300, 0.568), (0.340, 0.568), (0.374, 0.567), (0.388, 0.560), (0.394, 0.545), (0.395, 0.500), (0.395, 0.400), (0.395, 0.300),
              (0.395, 0.200), (0.395, 0.100), (0.395, 0.030)]


def towel(model, side):
    """A towel hung over the side of the bath at y = side * 0.38, folded over the rim and hanging to the floor."""
    x0, x1 = TOWEL_SPAN
    across = 24
    path = TOWEL_PATH
    normals = path_normals(path)
    cloth = bmesh.new()

    def top(i, j):
        x = x0 + (x1 - x0) * i / across
        d, z = path[j]
        hang = max(0.0, min(1.0, (0.55 - z) / 0.3))
        d += 0.006 * hang * math.sin(x * 29.0 + z * 6.0)
        z -= 0.012 * (1.0 - math.cos(x * 23.0)) * (1.0 if j == len(path) - 1 else 0.0)
        return Vector((x, side * d, z))

    def push(i, j):
        n = normals[j]
        return Vector((0.0, side * n[0], n[1])).normalized()

    sheet(cloth, top, push, (across, len(path) - 1), 0.009)
    model.turned(cloth, "cloth", sharp=60.0)


def settle(items, centre):
    """Where each (kind, x, y) of a bowl's pieces stands: the picture's place scaled to the bowl, then pushed clear of
    the others and kept in the bowl's room, in the space the basin is built in. Returns (x, y, z) for each."""
    pos = []
    for kind, x, y in items:
        pos.append([centre[0] + BOWL_FIT * (x - centre[0]), centre[1] + BOWL_FIT * (y - centre[1])])
    radii = [DISH_RADIUS[kind] for kind, _, _ in items]
    for _ in range(400):
        for a in range(len(pos)):
            for b in range(a + 1, len(pos)):
                dx, dy = pos[a][0] - pos[b][0], pos[a][1] - pos[b][1]
                gap = math.hypot(dx, dy)
                need = radii[a] + radii[b] + 0.003
                if gap < need:
                    dx, dy = (dx / gap, dy / gap) if gap > 1e-9 else (1.0, 0.0)
                    push = (need - gap) / 2
                    pos[a][0] += dx * push
                    pos[a][1] += dy * push
                    pos[b][0] -= dx * push
                    pos[b][1] -= dy * push
        for p, r in zip(pos, radii):
            rx, ry = BOWL_ROOM[0] - r, BOWL_ROOM[1] - r
            e = math.hypot((p[0] - centre[0]) / rx, (p[1] - centre[1]) / ry)
            if e > 1.0:
                p[0] = centre[0] + (p[0] - centre[0]) / e
                p[1] = centre[1] + (p[1] - centre[1]) / e
    for a in range(len(pos)):
        for b in range(a + 1, len(pos)):
            if math.hypot(pos[a][0] - pos[b][0], pos[a][1] - pos[b][1]) < radii[a] + radii[b]:
                raise SystemExit("the pieces in a bowl cannot be stood clear of each other: %s" % (items,))
    out = []
    for p, r in zip(pos, radii):
        reach = max(math.hypot(p[0] - centre[0] + r * math.cos(t), (p[1] - centre[1] + r * math.sin(t)) / BOWL_SQUASH)
                    for t in [k * math.pi / 8 for k in range(16)])
        out.append((p[0], p[1], inside(reach)))
    return out


def inside(radius):
    """The height of the bowl's inside at a radius from its middle."""
    for (r0, z0), (r1, z1) in zip(BOWL_INSIDE, BOWL_INSIDE[1:]):
        if radius <= r1:
            return z0 + (z1 - z0) * max(radius - r0, 0.0) / (r1 - r0)
    raise SystemExit("%.3f is past the lip of the bowl" % radius)


# The cloth over the bowl's rim as the path of its middle: (how far out from the rim, height), from inside the bowl,
# over the lip and down the outside.
CLOTH_PATH = [(-0.065, 0.082), (-0.04, 0.108), (-0.015, 0.132), (0.0, 0.137), (0.012, 0.137), (0.02, 0.130), (0.026, 0.110), (0.028, 0.080),
              (0.027, 0.050)]
CLOTH_HALF = 0.08


def cloth(model, centre, angle):
    """A checked tea towel over the rim at the point of the bowl's rim at this angle round it: its white squares in the
    cloth's own linen, its red ones, in the cloth surface, taking the picture's colour."""
    rim = Vector((centre[0] + RIM_RADIUS * math.cos(angle), centre[1] + RIM_RADIUS * BOWL_SQUASH * math.sin(angle), 0.0))
    tangent = Vector((-RIM_RADIUS * math.sin(angle), RIM_RADIUS * BOWL_SQUASH * math.cos(angle), 0.0)).normalized()
    out = Vector((RIM_RADIUS * BOWL_SQUASH * math.cos(angle), RIM_RADIUS * math.sin(angle), 0.0)).normalized()
    across, along = 8, len(CLOTH_PATH) - 1
    normals = path_normals(CLOTH_PATH)

    def top(i, j):
        s = -CLOTH_HALF + 2 * CLOTH_HALF * i / across
        d, z = CLOTH_PATH[j]
        return rim + tangent * s + out * d + Vector((0.0, 0.0, z + 0.003 * math.sin(s * 40.0)))

    def push(i, j):
        n = normals[j]
        return (out * n[0] + Vector((0.0, 0.0, n[1]))).normalized()

    base = bmesh.new()
    corners = sheet(base, top, push, (across, along), 0.004)
    model.turned(base, "linen", sharp=60.0)
    checks = bmesh.new()
    for i in range(across):
        for j in range(along):
            if (i + j) % 2:
                continue
            quad = [(i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)]
            low = [corners[k] + push(*k) * 0.0003 for k in quad]
            high = [corners[k] + push(*k) * 0.0013 for k in quad]
            lows = [checks.verts.new(p) for p in low]
            highs = [checks.verts.new(p) for p in high]
            checks.faces.new(lows[::-1])
            checks.faces.new(highs)
            for a in range(4):
                b = (a + 1) % 4
                checks.faces.new((lows[a], lows[b], highs[b], highs[a]))
    model.plain(checks, "cloth")


def wraps(model, x_off, y):
    """Two upright pipes wrapped in cloth, either side of the tap's stem."""
    profile = [(0.0, 0.0), (0.014, 0.0)]
    height = 0.008
    for _ in range(6):
        profile += [(0.017, height), (0.014, height + 0.012)]
        height += 0.024
    profile += [(0.011, height), (0.0, height + 0.004)]
    pipes = bmesh.new()
    for side in (-1, 1):
        F.lathe(pipes, profile, cx=side * x_off, cy=y, segments=10)
    model.turned(pipes, "cloth", sharp=50.0)


BATH_KINDS = ("bottle", "flask", "soap", "sponge", "towel")


def build():
    models = []
    entries = []
    made = {}

    bath_host = F.bath()
    basin_host = F.table_basin("table_basin_steel", "steel")
    off = props.origin(basin_host)
    centre = (0.0, BOWL_Y)

    def model_of(key, draw):
        if key not in made:
            model = F.Model("%s_%s_%d" % (BATCH, key[0], len([k for k in made if k[0] == key[0]])), centred=False)
            draw(model)
            props.stands_on(model, bath_host if key[0] in BATH_KINDS else basin_host)
            made[key] = model
            models.append(model)
        return made[key].name

    for overlay, items in sorted(BATH.items()):
        for item in items:
            kind = item[0]
            if kind == "towel":
                _, side, dye_x, dye_y = item
                name = model_of(("towel", side), lambda m, side=side: towel(m, side))
                surfaces = ["cloth"]
            else:
                _, x, y, dye_x, dye_y = item
                yaw = 0.0 if kind == "sponge" else 90.0
                z = BATH_TOP + (0.001 if kind in ("soap", "sponge") else 0.0)
                draw = {"bottle": bottle, "flask": flask, "soap": soap, "sponge": sponge}[kind]
                name = model_of((kind, x, y), lambda m, draw=draw, x=x, y=y, z=z, yaw=yaw: draw(m, at(x, y, z, yaw)))
                surfaces = ["cloth"] if kind == "sponge" else ["plastic"]
            entries.append(Prop(overlay, "bath", name, ("over", surfaces, (dye_x, dye_y, BATH_DYE_Z))))

    def basin_point(x, y):
        return x + off.x, y + off.y

    for overlay, items in sorted(SINKS.items()):
        bowl = [(kind, *basin_point(x, y)) for kind, x, y, _, _ in items if kind in DISH_RADIUS]
        spots = settle(bowl, centre)
        spot = iter(spots)
        for kind, x, y, dye_x, dye_y in items:
            surfaces = ["ceramic"]
            if kind in DISH_RADIUS:
                px, py, pz = next(spot)
                if kind == "mug":
                    draw = lambda m, px=px, py=py, pz=pz: mug(m, at(px, py, pz))
                elif kind == "cup":
                    draw = lambda m, px=px, py=py, pz=pz: cup(m, at(px, py, pz), DISH_RADIUS["cup"])
                else:
                    draw = lambda m, px=px, py=py, pz=pz, kind=kind: plate(m, at(px, py, pz), DISH_RADIUS[kind])
                key = (kind, round(px, 4), round(py, 4))
            elif kind == "cloth":
                mx, my = basin_point(x, y)
                angle = math.atan2((my - centre[1]) / BOWL_SQUASH, mx - centre[0])
                draw = lambda m, angle=angle: cloth(m, centre, angle)
                key = ("cloth", round(angle, 3))
                surfaces = ["cloth"]
            else:
                mx, my = basin_point(x, y)
                angle = math.atan2((my - centre[1]) / BOWL_SQUASH, mx - centre[0])
                draw = lambda m, angle=angle: soap_on_rim(m, centre, angle)
                key = ("rimsoap", round(angle, 3))
                surfaces = ["plastic"]
            name = model_of(key, draw)
            dye = "plain" if dye_x is None else ("over", surfaces, (*basin_point(dye_x, dye_y), SINK_DYE_Z))
            entries.append(Prop(overlay, "table_basin_steel", name, dye))

    for overlay, (dye_x, dye_y) in sorted(PIPES.items()):
        name = model_of(("wraps",), lambda m: wraps(m, 0.05, TAP_Y - 0.015))
        for host in ("table_basin", "table_basin_steel"):
            dye = ("over", ["cloth"], (*basin_point(dye_x, dye_y), SINK_DYE_Z))
            entries.append(Prop(overlay, host, name, dye))
    return models, entries


def soap_on_rim(model, centre, angle):
    """A bar of soap resting on the inside of the bowl just under its lip, along the rim at this angle."""
    radius = 0.17
    (r0, z0), (r1, z1) = next(pair for pair in zip(BOWL_INSIDE, BOWL_INSIDE[1:]) if pair[0][0] <= radius <= pair[1][0])
    slope = math.atan2(z1 - z0, r1 - r0)
    x = centre[0] + radius * math.cos(angle)
    y = centre[1] + radius * BOWL_SQUASH * math.sin(angle)
    matrix = at(x, y, inside(radius) + 0.003, math.degrees(angle) - 90.0) @ Matrix.Rotation(slope, 4, "X")
    soap(model, matrix)


props.write(BATCH, os.path.abspath(__file__), build, HOSTS)
