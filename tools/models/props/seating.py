"""Props of the seating: what TileOverlays lays over furniture_seating_indoor, made.

    blender --background --factory-startup --python tools/models/props/seating.py

Each overlay the game may lay over a sofa, an armchair, a dining chair or an ottoman paints one soft thing on it, and
this stands that thing on the piece: a cushion in the corner of a seat, a round bolster, a plaid throw over the arm of a
sofa, a lace doily over the back of a chair, a blanket slung over an ottoman. The pictures are of the game's own
sofas, armchairs and wing chairs, which the mod's models are not exactly, so each thing stands where the picture
paints it on the mod's piece, resting on the seat and the back, and no cushion stands inside an arm or the back.

What each overlay paints, and from which sides its picture shows it, was measured off the overlays: the tables below
keep only the numbers. A picture shows the south and east sides of a tile only, so for a piece facing
north or west the picture is the cushion's top over the back, and its place is the one the same cushion has on the
facings that can be read.
"""

import math
import os
import sys

# ---- the shapes, which need no Blender: tools off the game's side measure the colours the props take from their
# ---- numbers


class Pillow:
    """A soft cushion w wide, h high and t thick, as a superquadric: full in the middle of its two faces, thin at its seam.
    Its own frame has x across, z up and y through it, its front the -y face."""

    def __init__(self, w, h, t, e1=0.8, e2=0.45, dimple=0.0):
        self.a, self.b, self.c, self.e1, self.e2, self.dimple = w / 2, t / 2, h / 2, e1, e2, dimple

    def rim(self, x):
        """At x along the cushion: how far its section reaches up, how far through, and the share of its width used."""
        used = min(abs(x) / self.a, 1.0) ** (2 / self.e2)
        up = self.c * (1 - used) ** (self.e2 / 2)
        through = self.b * max(1 - used ** (self.e2 / self.e1), 0.0) ** (self.e1 / 2)
        return up, through, used

    def point(self, x, angle):
        """The point of the surface at x along the cushion, angle round its section from the top towards the back."""
        up, through, used = self.rim(x)
        cz, cy = up * math.cos(angle), through * math.sin(angle)
        low, high = 0.0, 1.5
        for _ in range(40):
            s = (low + high) / 2
            g = (used + abs(s * cz / self.c) ** (2 / self.e2)) ** (self.e2 / self.e1) + abs(s * cy / self.b) ** (2 / self.e1)
            low, high = (s, high) if g < 1 else (low, s)
        z, y = low * cz, low * cy
        if self.dimple:
            # The button a tufted cushion is drawn in by, on both faces.
            y -= math.copysign(self.dimple * math.exp(-(x * x + z * z) / 0.05 ** 2) * min(abs(y) / self.b, 1.0) ** 2, y)
        return (x, y, z)

    def sections(self, count):
        """x along the cushion for each of its count + 1 sections, close together at its two ends where the section shrinks to nothing."""
        return [self.a * math.sin(-math.pi / 2 + math.pi * k / count) for k in range(count + 1)]

    def outward(self, p):
        """The unit normal of the surface at p, from the way the implicit surface grows."""
        x, y, z = p

        def g(x, y, z):
            used = (abs(x) / self.a) ** (2 / self.e2) + (abs(z) / self.c) ** (2 / self.e2)
            return used ** (self.e2 / self.e1) + (abs(y) / self.b) ** (2 / self.e1)
        step = 1e-4
        n = [(g(x + step, y, z) - g(x - step, y, z)), (g(x, y + step, z) - g(x, y - step, z)), (g(x, y, z + step) - g(x, y, z - step))]
        length = math.sqrt(sum(v * v for v in n)) or 1.0
        return tuple(v / length for v in n)


# What the seat and the back of the mod's sofas and armchairs are, from furniture.settee: the top of the seat cushion with its
# crown, the front of the back cushion where it stands, the arms' inner faces.
SEAT = 0.468
ARM_FACE = {"sofa": 0.785, "armchair": 0.345}


def back_y(z):
    """How far forward the back cushion's face stands at height z, as settee leans it, less what its crown could add."""
    return 0.085 + 0.2297 * (z - 0.47)


def turned(p, yaw, lean):
    """A point of a cushion's frame with its top leant back by lean and the whole turned about the upright by yaw."""
    x, y, z = p
    c, s = math.cos(lean), math.sin(lean)
    y, z = y * c + z * s, -y * s + z * c
    c, s = math.cos(yaw), math.sin(yaw)
    return (x * c - y * s, x * s + y * c, z)


def settle(shape, host, x, yaw, lean):
    """Where a cushion turned by yaw and leant by lean stands on the seat of a host so that it rests on the seat, touches
    the back and keeps to the arms, as far across as x asks: its offset (x, y, z) from the middle of the piece."""
    points = [turned(shape.point(at, angle), yaw, lean) for at in shape.sections(12) for angle in [i * math.pi / 6 for i in range(12)]]
    z = SEAT - min(p[2] for p in points)
    y = -max(p[1] - back_y(p[2] + z) for p in points) - 0.002
    reach = ARM_FACE[host] - 0.004
    x = max(min(x, reach - max(p[0] for p in points)), -reach - min(p[0] for p in points))
    return (x, y, z)


class Throw:
    """A plaid throw over the seat and the right-hand arm of a sofa: along a path in the (x, z) plane from the seat, up the
    arm's inner face, over its roll and down the outside, y wide, its top face where the throw is drawn."""
    ROLL = (0.8675, 0.509, 0.105)

    def __init__(self, start, y0, y1):
        self.y0, self.y1 = y0, y1
        cx, cz, r = self.ROLL
        near = r + 0.008
        path = [(start, SEAT + 0.012), ((start + 0.70) / 2, SEAT + 0.012), (0.70, SEAT + 0.012)]
        for degrees in (195, 175, 135, 90, 45, 5, -35):
            path.append((cx + near * math.cos(math.radians(degrees)), cz + near * math.sin(math.radians(degrees))))
        path += [(0.962, 0.38), (0.966, 0.31), (0.97, 0.24)]
        self.path = path
        self.at = [0.0]
        for a, b in zip(path, path[1:]):
            self.at.append(self.at[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
        self.length = self.at[-1]

    def point(self, s, y, lift=0.0):
        """The point on the throw's top face s along its path and y across it, lifted by lift off the face."""
        s = min(max(s, 0.0), self.length)
        i = 1
        while i < len(self.at) - 1 and self.at[i] < s:
            i += 1
        (xa, za), (xb, zb) = self.path[i - 1], self.path[i]
        t = (s - self.at[i - 1]) / (self.at[i] - self.at[i - 1])
        run = math.hypot(xb - xa, zb - za)
        # The face looks to the left of the way the path runs.
        nx, nz = -(zb - za) / run, (xb - xa) / run
        ripple = 0.004 * math.sin(y * 38 + s * 21) * math.sin(s * 9 + 1.0)
        out = lift + ripple
        return (xa + (xb - xa) * t + nx * out, y, za + (zb - za) * t + nz * out)


class Drape:
    """A blanket slung over an ottoman: a flat cloth u0 to u1 by v0 to v1 along the ottoman's x and y, lying on its top where
    it is over it and hanging down its sides where it is past them, flaring out as it falls and in folds."""
    TOP, HALF, FLOOR = 0.434, 0.30, 0.02

    def __init__(self, u0, u1, v0, v1, flare):
        self.u0, self.u1, self.v0, self.v1, self.flare = u0, u1, v0, v1, flare

    def point(self, u, v, lift=0.0):
        a = self.HALF + 0.012
        past_x, past_y = max(abs(u) - self.HALF, 0.0), max(abs(v) - self.HALF, 0.0)
        fall = past_x + past_y
        hang = min(fall / 0.15, 1.0)
        x = math.copysign(self.HALF + 0.012 + self.flare * past_x, u) if past_x else u
        y = math.copysign(self.HALF + 0.012 + self.flare * past_y, v) if past_y else v
        z = max(self.TOP + 0.012 - 0.85 * fall, self.FLOOR)
        folds = 0.014 * hang
        if past_x:
            x += math.copysign(folds * math.sin(v * 23 + 1.3) + lift, u)
        if past_y:
            y += math.copysign(folds * math.sin(u * 23 + 0.4) + lift, v)
        if not (past_x or past_y):
            z += lift + 0.004 * math.sin(u * 17 + v * 13)
        else:
            z += 0.004 * math.sin(u * 17 + v * 13) * (1 - hang)
        return (x, y, z)


class Doily:
    """A lace runner over the top rail of a dining chair's back, hanging down its front and its back, scalloped along both
    edges and x wide, as dining_chair builds the back: the rail's face stands rake(z) from the front."""
    HALF = 0.15

    @staticmethod
    def rake(z):
        return 0.19 + max(z - 0.44, 0.0) * 0.14

    def __init__(self):
        front = lambda z: self.rake(z) - 0.0155
        back = lambda z: self.rake(z) + 0.0155
        self.path = [(front(0.74), 0.74), (front(0.90), 0.90), (front(0.952), 0.952), ((front(0.96) + back(0.96)) / 2, 0.964),
                     (back(0.952), 0.952), (back(0.90), 0.90), (back(0.80), 0.80)]
        self.at = [0.0]
        for a, b in zip(self.path, self.path[1:]):
            self.at.append(self.at[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
        self.length = self.at[-1]

    def scallop(self, x):
        return 0.016 * abs(math.sin(math.pi * x / 0.05))

    def point(self, s, x, lift=0.0):
        s = min(max(s, 0.0), self.length)
        i = 1
        while i < len(self.at) - 1 and self.at[i] < s:
            i += 1
        (ya, za), (yb, zb) = self.path[i - 1], self.path[i]
        t = (s - self.at[i - 1]) / (self.at[i] - self.at[i - 1])
        run = math.hypot(yb - ya, zb - za)
        # The face looks to the left of the way the path runs: out of the chair at its front, which is -y.
        ny, nz = -(zb - za) / run, (yb - ya) / run
        return (x, ya + (yb - ya) * t + ny * lift, za + (zb - za) * t + nz * lift)


# ---- Blender below

import bmesh  # noqa: E402

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import props  # noqa: E402
from props import Prop  # noqa: E402

F = props.furniture
BATCH = "seating"

HOSTS = {
    "sofa": lambda: F.settee("sofa", 1.90, 2),
    "armchair": lambda: F.settee("armchair", 1.02, 1),
    "dining_chair": F.dining_chair,
    "ottoman": F.ottoman,
}

# ---- measured
CUSHIONS = {
    0: ("sofa", "red", -0.54, 1.00),
    1: ("sofa", "red", 0.54, -1.00),
    2: ("sofa", "red", -0.54, 1.00),
    3: ("sofa", "red", 0.54, -1.00),
    4: ("sofa", "red", 0.54, -1.00),
    5: ("sofa", "red", -0.54, 1.00),
    6: ("sofa", "red", 0.54, -1.00),
    7: ("sofa", "red", -0.54, 1.00),
    8: ("armchair", "stripe", 0.00, -0.18),
    9: ("armchair", "stripe", 0.00, 0.15),
    10: ("armchair", "stripe", 0.00, 0.00),
    11: ("armchair", "stripe", 0.00, 0.00),
    12: ("armchair", "red", 0.00, 0.15),
    13: ("armchair", "red", 0.00, -0.18),
    14: ("armchair", "red", 0.00, 0.00),
    15: ("armchair", "red", 0.00, 0.00),
    16: ("sofa", "stripe", 0.54, -1.00),
    17: ("sofa", "stripe", -0.54, 1.00),
    18: ("sofa", "stripe", -0.54, 1.00),
    19: ("sofa", "stripe", 0.54, -1.00),
    20: ("sofa", "stripe", 0.54, -1.00),
    21: ("sofa", "stripe", -0.54, 1.00),
    22: ("sofa", "stripe", 0.54, -1.00),
    23: ("sofa", "stripe", -0.54, 1.00),
    24: ("sofa", "check", -0.54, 1.00),
    26: ("sofa", "check", -0.54, 1.00),
    27: ("sofa", "check", 0.54, -1.00),
    29: ("sofa", "check", -0.54, 1.00),
    30: ("sofa", "check", 0.54, -1.00),
    31: ("sofa", "check", -0.54, 1.00),
    32: ("armchair", "check", 0.00, -0.18),
    33: ("armchair", "check", 0.00, 0.15),
    34: ("armchair", "check", 0.00, 0.00),
    35: ("armchair", "check", 0.00, 0.00),
    56: ("sofa", "round", -0.54, 1.00),
    57: ("sofa", "round", 0.54, -1.00),
    58: ("sofa", "round", -0.54, 1.00),
    59: ("sofa", "round", 0.54, -1.00),
    60: ("sofa", "round", 0.54, -1.00),
    61: ("sofa", "round", -0.54, 1.00),
    62: ("sofa", "round", 0.54, -1.00),
    63: ("sofa", "round", -0.54, 1.00),
}
CUSHION_DYES = {
    0: ((-0.542, -0.218, 0.634), None),
    1: ((0.512, 0.095, 0.619), None),
    2: ((-0.660, -0.072, 0.684), None),
    3: ((0.479, 0.126, 0.716), None),
    4: ((0.673, -0.030, 0.620), None),
    5: ((-0.653, -0.249, 0.625), None),
    6: ((0.444, 0.051, 0.753), None),
    7: ((-0.574, 0.051, 0.744), None),
    8: ((-0.201, 0.076, 0.693), (-0.048, 0.116, 0.631)),
    9: ((-0.133, 0.074, 0.767), (-0.119, 0.101, 0.685)),
    10: ((-0.204, -0.187, 0.620), (-0.230, -0.264, 0.620)),
    11: ((0.230, -0.230, 0.620), (0.324, -0.238, 0.620)),
    12: ((-0.054, -0.055, 0.684), None),
    13: ((-0.201, 0.076, 0.693), None),
    14: ((-0.511, -0.221, 0.620), None),
    15: ((0.460, -0.170, 0.620), None),
    16: ((0.529, 0.106, 0.685), (0.446, 0.111, 0.718)),
    17: ((-0.586, -0.271, 0.544), (-0.651, -0.217, 0.758)),
    18: ((-0.609, -0.002, 0.743), (-0.525, 0.019, 0.785)),
    19: ((0.447, 0.116, 0.693), (0.518, 0.116, 0.701)),
    20: ((0.467, 0.083, 0.768), (0.471, 0.112, 0.747)),
    21: ((-0.568, -0.002, 0.778), (-0.459, -0.072, 0.685)),
    22: ((0.533, -0.224, 0.600), (0.638, -0.035, 0.715)),
    23: ((-0.591, 0.038, 0.701), (-0.657, -0.182, 0.540)),
    24: ((-0.637, -0.212, 0.763), (-0.678, -0.232, 0.683)),
    26: ((-0.672, -0.190, 0.765), (-0.603, -0.270, 0.576)),
    27: ((0.426, 0.104, 0.625), (0.452, 0.119, 0.686)),
    29: ((-0.651, -0.217, 0.521), (-0.677, -0.109, 0.668)),
    30: ((0.423, 0.038, 0.710), (0.444, 0.051, 0.753)),
    31: ((-0.501, 0.017, 0.620), (-0.595, -0.108, 0.620)),
    32: ((0.199, 0.037, 0.650), (-0.010, 0.084, 0.603)),
    33: ((0.096, 0.084, 0.533), (0.185, 0.081, 0.585)),
    34: ((0.086, -0.204, 0.620), (0.205, -0.119, 0.620)),
    35: ((0.307, -0.187, 0.620), (0.494, -0.170, 0.620)),
    56: ((-0.643, -0.206, 0.648), None),
    57: ((0.462, 0.119, 0.627), None),
    58: ((-0.670, -0.113, 0.750), None),
    59: ((0.597, 0.014, 0.684), None),
    60: ((0.671, -0.168, 0.682), None),
    61: ((-0.637, -0.208, 0.629), None),
    62: ((0.527, -0.167, 0.619), None),
    63: ((-0.520, -0.150, 0.634), None),
}
THROW_DYES = {
    25: {"red": (0.900, 0.089, 0.613), "gold": (0.789, -0.108, 0.587), "blue": (0.341, -0.192, 0.483), "white": (0.767, 0.089, 0.542)},
    28: {"red": (0.492, -0.267, 0.479), "gold": (0.421, -0.192, 0.480), "blue": (0.961, 0.150, 0.381), "white": (0.301, -0.267, 0.476)},
}
DRAPES = {
    46: ((-0.46, 0.67, -0.40, 0.07, 0.07), (-0.295, -0.315, 0.411)),
    47: ((-0.66, 0.30, -0.37, 0.08, 0.01), (-0.140, -0.051, 0.446)),
}
DOILY_DYES = {36: (-0.180, -0.149, 0.900), 37: (0.118, 0.288, 0.900), 38: (-0.148, 0.321, 0.900), 39: (0.351, 0.337, 0.900)}
# ---- end measured

# Each cushion kind: its shape, and the cushions' size is the same for every one: a throw cushion a hand under half a metre across.
CUSHION_SIZE = (0.42, 0.33, 0.17)
BOLSTER_SIZE = (0.38, 0.38, 0.14)
LEAN = 0.33
POSE_TAGS = {(-0.54, 1.00): "l", (0.54, -1.00): "r", (0.00, -0.18): "e", (0.00, 0.15): "s", (0.00, 0.00): "z"}
STRIPES = (-0.135, -0.045, 0.045, 0.135)
STRIPE_WIDTH = 0.04
LIFT = 0.0016


def shape_of(kind):
    if kind == "red":
        return Pillow(*CUSHION_SIZE, dimple=0.02)
    if kind == "stripe":
        return Pillow(*CUSHION_SIZE)
    if kind == "check":
        return Pillow(*CUSHION_SIZE, dimple=0.02)
    return Pillow(*BOLSTER_SIZE, e1=0.8, e2=1.0, dimple=0.02)


def quads(bm, grid, wrap=False):
    """Faces over a grid of vertices, a list of rows, the last of each row joined to its first when wrap."""
    for a, b in zip(grid, grid[1:]):
        count = len(a) if wrap else len(a) - 1
        for j in range(count):
            k = (j + 1) % len(a)
            bm.faces.new((a[j], a[k], b[k], b[j]))


def surface(bm, shape, xs, angles, place, lift=0.0, wrap=True):
    """The cushion's surface over the sections xs and the angles round them, each point put by place, and lifted by lift."""
    grid = []
    for x in xs:
        row = []
        for angle in angles:
            p = shape.point(x, angle)
            if lift:
                n = shape.outward(p)
                p = (p[0] + n[0] * lift, p[1] + n[1] * lift, p[2] + n[2] * lift)
            row.append(bm.verts.new(place(p)))
        grid.append(row)
    quads(bm, grid, wrap)
    return grid


def cushion_models(name, kind, x, yaw, host_built, host):
    """The cushion of a kind standing as the pose says, and what is sewn on it, as models: [(name, model, picks)], each model
    standing on the host as built in its space, picks which dye of the overlay's picture it takes (0 its body, 1 its light)."""
    shape = shape_of(kind)
    off = settle(shape, host, x, yaw, LEAN)
    place = lambda p: tuple(a + b for a, b in zip(turned(p, yaw, LEAN), off))
    sections_n, around = (10, 16) if F.plain else (18, 28)
    angles = [i * 2 * math.pi / around for i in range(around)]
    body = bmesh.new()
    surface(body, shape, shape.sections(sections_n), angles, place)
    bmesh.ops.remove_doubles(body, verts=body.verts[:], dist=1e-5)
    model = F.Model(name, centred=False)
    model.turned(body, "pillow", sharp=180.0)
    props.stands_on(model, host_built)
    out = [(name, model, 0)]
    if kind == "stripe":
        bands = bmesh.new()
        for centre in STRIPES:
            xs = [centre + STRIPE_WIDTH * (k / 2 - 0.5) for k in range(3)]
            surface(bands, shape, xs, angles, place, lift=LIFT)
        part = F.Model(name + "_bands", centred=False)
        part.turned(bands, "pillow", sharp=180.0)
        props.stands_on(part, host_built)
        out.append((part.name, part, 1))
    elif kind == "check":
        quarters = bmesh.new()
        up = [-math.pi / 2 + i * math.pi / 12 for i in range(13)]
        down = [math.pi / 2 + i * math.pi / 12 for i in range(13)]
        for xs, turn in (([shape.a * k / 8 for k in range(9)], up), ([-shape.a * k / 8 for k in range(9)][::-1], down)):
            surface(quarters, shape, xs, turn, place, lift=LIFT, wrap=False)
        bmesh.ops.remove_doubles(quarters, verts=quarters.verts[:], dist=1e-5)
        part = F.Model(name + "_quarters", centred=False)
        part.turned(quarters, "pillow", sharp=180.0)
        props.stands_on(part, host_built)
        out.append((part.name, part, 1))
    return out


def slab(bm, top, bottom):
    """A thin closed body between two grids of vertices that match one for one, the top one's faces looking out."""
    quads(bm, top)
    quads(bm, bottom[::-1])
    rows, cols = len(top), len(top[0])
    for j in range(cols - 1):
        bm.faces.new((top[0][j], top[0][j + 1], bottom[0][j + 1], bottom[0][j]))
        bm.faces.new((top[-1][j + 1], top[-1][j], bottom[-1][j], bottom[-1][j + 1]))
    for i in range(rows - 1):
        bm.faces.new((top[i + 1][0], top[i][0], bottom[i][0], bottom[i + 1][0]))
        bm.faces.new((top[i][-1], top[i + 1][-1], bottom[i + 1][-1], bottom[i][-1]))


def grid(bm, point, a_values, b_values):
    return [[bm.verts.new(point(a, b)) for b in b_values] for a in a_values]


def span(low, high, count):
    return [low + (high - low) * i / count for i in range(count + 1)]


THROW = (0.20, -0.275, 0.18)
THROW_THICK = 0.008
THROW_LINES = {"red": ((0.065, 0.014), (0.105, 0.014)), "gold": ((-0.12, 0.016),)}
THROW_BARS = (0.18, 0.45, 0.72, 0.99)


def throw_models(host_built):
    throw = Throw(*THROW)
    steps_s, steps_y = (18, 6) if F.plain else (36, 10)
    ss, ys = span(0.0, throw.length, steps_s), span(throw.y0, throw.y1, steps_y)
    base = bmesh.new()
    slab(base, grid(base, lambda s, y: throw.point(s, y), ss, ys), grid(base, lambda s, y: throw.point(s, y, -THROW_THICK), ss, ys))
    out = []
    model = F.Model("seating_throw", centred=False)
    model.turned(base, "linen", sharp=60.0)
    out.append(model)
    for colour, lines in THROW_LINES.items():
        ribbon = bmesh.new()
        for centre, width in lines:
            grid_ = grid(ribbon, lambda s, y: throw.point(s, y, 0.0024), ss, span(centre - width / 2, centre + width / 2, 1))
            quads(ribbon, grid_)
        model = F.Model("seating_throw_%s" % colour, centred=False)
        model.turned(ribbon, "linen", sharp=60.0)
        out.append(model)
    bars = bmesh.new()
    for centre in THROW_BARS:
        quads(bars, grid(bars, lambda s, y: throw.point(s, y, 0.0016), span(centre - 0.015, centre + 0.015, 2), ys))
    model = F.Model("seating_throw_blue", centred=False)
    model.turned(bars, "linen", sharp=60.0)
    out.append(model)
    for model in out:
        props.stands_on(model, host_built)
    return out


def drape_model(name, shape, host_built):
    drape = Drape(*shape)
    steps = 12 if F.plain else 22
    us, vs = span(drape.u0, drape.u1, steps), span(drape.v0, drape.v1, steps)
    # One sheet: a slab's two faces of a cloth this thin come out of Blender's normals the wrong way about, and the ottoman
    # under it hides its underside.
    cloth = bmesh.new()
    quads(cloth, grid(cloth, lambda u, v: drape.point(u, v), us, vs))
    model = F.Model(name, centred=False)
    model.turned(cloth, "cloth", sharp=60.0)
    props.stands_on(model, host_built)
    return model


DOILY_THICK = 0.003


def doily_model(host_built):
    doily = Doily()
    columns = 24 if F.plain else 40
    rows = 8 if F.plain else 12
    xs = span(-doily.HALF, doily.HALF, columns)
    layer = bmesh.new()
    top = [[layer.verts.new(doily.point(doily.scallop(x) + (doily.length - 2 * doily.scallop(x)) * r / rows, x, 0.0)) for x in xs] for r in range(rows + 1)]
    bottom = [[layer.verts.new(doily.point(doily.scallop(x) + (doily.length - 2 * doily.scallop(x)) * r / rows, x, -DOILY_THICK)) for x in xs] for r in range(rows + 1)]
    slab(layer, top, bottom)
    model = F.Model("seating_doily", centred=False)
    model.turned(layer, "linen", sharp=60.0)
    props.stands_on(model, host_built)
    return model


def build():
    models, entries, made = [], [], {}
    built = {name: make() for name, make in HOSTS.items()}
    for number, (host, kind, x, yaw) in sorted(CUSHIONS.items()):
        overlay = "seating_01_%d" % number
        name = "seating_%s_%s_%s" % (kind, host, POSE_TAGS[(x, yaw)])
        if name not in made:
            made[name] = cushion_models(name, kind, x, yaw, built[host], host)
            models += [model for _, model, _ in made[name]]
        for part_name, _, pick in made[name]:
            entries.append(Prop(overlay, host, part_name, ("over", ["pillow"], CUSHION_DYES[number][pick])))
    parts = throw_models(built["sofa"])
    models += parts
    for number, colours in THROW_DYES.items():
        for part, colour in zip(parts, ("white", "red", "gold", "blue")):
            entries.append(Prop("seating_01_%d" % number, "sofa", part.name, ("over", ["linen"], colours[colour])))
    for number, (shape, point) in DRAPES.items():
        model = drape_model("seating_drape_%d" % number, shape, built["ottoman"])
        models.append(model)
        entries.append(Prop("seating_01_%d" % number, "ottoman", model.name, ("over", ["cloth"], point)))
    lace = doily_model(built["dining_chair"])
    models.append(lace)
    for number, point in DOILY_DYES.items():
        entries.append(Prop("seating_01_%d" % number, "dining_chair", lace.name, ("over", ["linen"], point)))
    return models, entries


props.write(BATCH, os.path.abspath(__file__), build, {name: make for name, make in HOSTS.items()})
