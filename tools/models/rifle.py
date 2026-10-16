"""Builds the mod's own model of a bolt action hunting rifle and packs it for the jar.

Run headless with Blender 4.4, from the repository root:

    blender --background --factory-startup --threads 1 --python tools/models/rifle.py -- PICTURES [--renders DIR] [--no-pack] [--cpu] [--mesh]

PICTURES is the folder tools/models/shotgun_textures.py wrote: the rifle's stock is the same
walnut. The model is made here from nothing: a sporting rifle with a round receiver, a
tapered barrel, open sights, a one piece walnut stock with a dark fore-end tip, and a bolt
whose handle lies down the right side, of no maker's pattern. It writes, under
src/main/resources/models, rifle.mesh.gz, and under src/textures/models rifle.colour.gz
and rifle.surface.gz, laid out exactly as shotgun.py lays out the shotgun's, which see. Of the
five parts that layout names, the rifle has three: what is fixed, the bolt, and a fired case for the mod to throw
out of the port. The bolt is turned up about the bore before it is drawn back, which the
mod does: here it is closed.

The tools that turn, loft, bevel, unwrap and bake a part, the steel, the rubber, the walnut
and the bake are gunsmith.py's.
"""

import math
import os
import sys

import bmesh
import bpy
import numpy
from mathutils import Matrix, Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gunsmith as G  # noqa: E402
from gunsmith import along, along_y, cuboid, cutter, lathe, loft, rod, screw, sweep, take  # noqa: E402

# The parts of the file's layout that the rifle has: of the five, the second is a pump's and the last a stock that is left out.
FIXED, BOLT, CASE = 0, 2, 3

# Laid out with the bore along y through x = 0, z = 0 and the trigger at y = 0. GRIP is
# where the hand that holds the grip is, which the finished model has as its origin: where
# the game's own hunting rifle has its hand against its muzzle, so that the muzzle of this
# one is where the game takes the shot to leave from.
MUZZLE_Y = 0.892
GRIP = Vector((0.0, -0.110, -0.0621))
RECEIVER_REAR, RECEIVER_FRONT, RECEIVER_R = -0.045, 0.170, 0.0172
BOLT_R = 0.0088
BUTT = -0.345
WOOD_TOP = -0.0040
TIP_FROM, TIP_TO = 0.455, 0.500
# How far the bolt is drawn back, once its handle is up.
TRAVEL = 0.085
# The case is made well clear of the rifle, so that neither shades the other, and is packed about its own middle.
CASE_AT = Vector((0.0, 0.30, 0.40))
PORT = Vector((0.0172, 0.085, 0.006))
# Over the bore: the top of the rear sight's notch and of the bead at the muzzle.
SIGHT_LINE = 0.0262
# With the cheek on the comb.
EYE_BEHIND_GRIP = 0.13
# Where the hand under the fore-end holds it.
HELD = Vector((0.0, 0.30, -0.030))


def barrel_radius(y):
    if y < 0.215:
        return 0.0150
    if y < 0.235:
        return 0.0150 + (0.0138 - 0.0150) * (y - 0.215) / 0.02
    t = (y - 0.235) / (MUZZLE_Y - 0.235)
    return 0.0086 + (0.0138 - 0.0086) * (1.0 - t) ** 1.6


def barrel():
    bm = bmesh.new()
    profile = [(0.0, RECEIVER_FRONT - 0.004), (0.0150, RECEIVER_FRONT - 0.004), (0.0150, 0.215), (0.0138, 0.235)]
    profile += [(barrel_radius(y), y) for y in (0.30, 0.37, 0.44, 0.51, 0.58, 0.65, 0.72, 0.79, 0.85)]
    # The crown: the bore's mouth is set back inside a rim, so that a knock to the muzzle does not reach it.
    profile += [(0.0086, MUZZLE_Y - 0.0010), (0.0080, MUZZLE_Y), (0.0066, MUZZLE_Y), (0.0058, MUZZLE_Y - 0.0022), (0.0039, MUZZLE_Y - 0.0022),
                (0.0039, MUZZLE_Y - 0.09), (0.0, MUZZLE_Y - 0.09)]
    lathe(bm, profile, along_y(), segments=56)
    take(bm, "barrel", "blued", sharp=25.0)


def receiver():
    port = bmesh.new()
    cuboid(port, 0.0035, 0.03, 0.040, 0.128, -0.0030, 0.03)
    port_cut = cutter(port, bevel=0.0040)
    tunnel = bmesh.new()
    lathe(tunnel, [(0.0, RECEIVER_REAR - 0.01), (BOLT_R + 0.0004, RECEIVER_REAR - 0.01), (BOLT_R + 0.0004, 0.132), (0.0, 0.132)], along_y(), segments=40)
    tunnel_cut = cutter(tunnel)
    # Where the root of the bolt's handle turns down into the right side of the bridge.
    notch = bmesh.new()
    cuboid(notch, 0.004, 0.03, RECEIVER_REAR - 0.002, RECEIVER_REAR + 0.0125, -0.0125, 0.0045)
    notch_cut = cutter(notch, bevel=0.0015)

    body = bmesh.new()
    lathe(body, [(0.0, RECEIVER_REAR), (RECEIVER_R - 0.0012, RECEIVER_REAR), (RECEIVER_R, RECEIVER_REAR + 0.0012), (RECEIVER_R, RECEIVER_FRONT - 0.0030),
                 (RECEIVER_R - 0.0008, RECEIVER_FRONT - 0.0018), (RECEIVER_R - 0.0008, RECEIVER_FRONT - 0.0010), (0.0160, RECEIVER_FRONT), (0.0, RECEIVER_FRONT)],
          along_y(), segments=64)
    take(body, "receiver", "receiver", bevel=0.0005, segments=2, sharp=40.0, cut=(tunnel_cut, port_cut, notch_cut), texels=1.25)
    for used in (port_cut, tunnel_cut, notch_cut):
        bpy.data.objects.remove(used)

    # The holes a scope's base is screwed into, filled with their screws.
    plugs = bmesh.new()
    for y in (-0.034, -0.016, 0.140, 0.158):
        screw(plugs, 0.0, y, RECEIVER_R - 0.0002, 0.0026, Matrix.Identity(4))
    take(plugs, "base screws", "blued", sharp=40.0)

    # The safety: a short lever on the right of the tang, behind the handle, with a pad for the thumb.
    safety = bmesh.new()
    rod(safety, [(0.0128, -0.0500, 0.0030), (0.0150, -0.0545, 0.0052), (0.0158, -0.0600, 0.0078)], [0.0030, 0.0027, 0.0027], segments=12)
    lathe(safety, [(0.0040 * math.sin(math.radians(a)), -0.0030 * math.cos(math.radians(a))) for a in range(0, 181, 20)],
          Matrix.Translation((0.0160, -0.0612, 0.0086)), segments=14)
    take(safety, "safety", "blued", sharp=50.0)

    # On the left of the bridge, the catch that lets the bolt be drawn right out.
    stop = bmesh.new()
    cuboid(stop, -RECEIVER_R - 0.0016, -RECEIVER_R + 0.0010, -0.0400, -0.0250, -0.0010, 0.0052)
    take(stop, "bolt stop", "blued", bevel=0.0009, segments=2)


def bolt():
    body = bmesh.new()
    lathe(body, [(0.0, RECEIVER_REAR - 0.004), (BOLT_R, RECEIVER_REAR - 0.004), (BOLT_R, 0.1290), (BOLT_R - 0.0012, 0.1302), (0.0, 0.1302)], along_y(), segments=40)
    take(body, "bolt", "bright", group=BOLT, sharp=30.0)
    # Along the bolt's right side, the long claw that pulls the fired case out.
    claw = bmesh.new()
    cuboid(claw, BOLT_R - 0.0006, BOLT_R + 0.0008, 0.050, 0.1296, -0.0030, 0.0030)
    take(claw, "extractor", "blued", group=BOLT, bevel=0.0003, segments=1)

    shroud = bmesh.new()
    lathe(shroud, [(0.0, RECEIVER_REAR + 0.0008), (0.0117, RECEIVER_REAR + 0.0008), (0.0120, RECEIVER_REAR - 0.0004), (0.0120, -0.058), (0.0112, -0.066),
                   (0.0096, -0.076), (0.0080, -0.0815), (0.0054, -0.0845), (0.0, -0.0855)], along_y(), segments=40)
    take(shroud, "bolt shroud", "blued", group=BOLT, sharp=28.0)

    # The handle: out of the bolt's root on the right, down and a little back, to a ball for the palm.
    root = Vector((0.0085, RECEIVER_REAR + 0.0052, -0.0020))
    ball = Vector((0.0600, RECEIVER_REAR - 0.0190, -0.0260))
    handle = bmesh.new()
    path = [root + (ball - root) * t + Vector((0.0, 0.0, 0.0030 * math.sin(math.pi * t))) for t in (0.0, 0.12, 0.3, 0.5, 0.7, 0.88, 0.97)]
    rod(handle, path, [0.0058, 0.0052, 0.0046, 0.0043, 0.0042, 0.0044, 0.0050], segments=18)
    take(handle, "bolt handle", "blued", group=BOLT, sharp=40.0)
    knob = bmesh.new()
    lathe(knob, [(0.0108 * math.sin(math.radians(a)), -0.0108 * math.cos(math.radians(a))) for a in range(0, 181, 12)],
          Matrix.Translation(ball) @ (ball - root).normalized().to_track_quat("Z", "Y").to_matrix().to_4x4(), segments=28)
    take(knob, "bolt knob", "blued", group=BOLT, sharp=50.0)


def furniture():
    """What is let into the underside of the stock: the plate, the magazine, the guard and the trigger."""
    # Two plates let into the wood, a hair proud of it: the guard's own, and before it the hinged floor of the magazine.
    for name, rear, front in (("guard plate", -0.0460, 0.0372), ("floor plate", 0.0384, 0.1320)):
        plate = bmesh.new()
        sweep(plate, [(y, stock_bottom(y) + 0.0011) for y in numpy.linspace(rear, front, 14)], 0.0096, 0.0030)
        take(plate, name, "blued", bevel=0.0010, segments=2)
    # The catch that lets the floor swing down, inside the front of the guard.
    catch = bmesh.new()
    cuboid(catch, -0.0030, 0.0030, 0.0325, 0.0366, -0.0585, -0.0500)
    take(catch, "floor catch", "blued", bevel=0.0010, segments=2)

    guard = bmesh.new()
    path = along([(0.0390, -0.0500), (0.0412, -0.0600), (0.0372, -0.0720), (0.0265, -0.0820), (0.0100, -0.0872), (-0.0100, -0.0872), (-0.0262, -0.0822),
                  (-0.0362, -0.0715), (-0.0402, -0.0600), (-0.0408, -0.0500)], 44)
    sweep(guard, path, 0.0064, [0.0032 + 0.0014 * abs(n / 43 - 0.5) * 2 for n in range(44)])
    take(guard, "trigger guard", "blued", bevel=0.0011, segments=3, sharp=50.0)

    trigger = bmesh.new()
    path = along([(0.0045, -0.0480), (0.0040, -0.0570), (0.0015, -0.0655), (-0.0022, -0.0728), (-0.0034, -0.0782), (-0.0012, -0.0812)], 20)
    sweep(trigger, path, 0.0032, [0.0060 - 0.0030 * n / 19 for n in range(20)])
    take(trigger, "trigger", "bright", bevel=0.0010, segments=3, sharp=50.0)

    under = Matrix.Rotation(math.radians(180), 4, "X")
    screws = bmesh.new()
    for y in (-0.0405, 0.1262):
        screw(screws, 0.0, y, stock_bottom(y) - 0.0006, 0.0034, under)
    take(screws, "action screws", "blued", sharp=40.0)


STOCK = [
    # y, top, bottom, half width, how square, how narrow the comb is against the belly
    (TIP_FROM - 0.0016, WOOD_TOP, -0.0310, 0.0170, 3.2, 1.0),
    (0.4000, WOOD_TOP, -0.0340, 0.0182, 3.2, 1.0),
    (0.3000, WOOD_TOP, -0.0385, 0.0196, 3.2, 1.0),
    (0.1900, WOOD_TOP, -0.0440, 0.0212, 3.4, 1.0),
    (0.1000, WOOD_TOP, -0.0478, 0.0222, 3.6, 1.0),
    (0.0200, WOOD_TOP, -0.0480, 0.0218, 3.6, 1.0),
    (-0.0350, -0.0045, -0.0478, 0.0205, 3.2, 0.98),
    # Behind the receiver the wood comes up to meet its tang, then narrows to the wrist.
    (-0.0600, -0.0010, -0.0500, 0.0186, 2.8, 0.95),
    (-0.0850, -0.0072, -0.0560, 0.0172, 2.5, 0.92),
    # The grip turns down from the wrist and ends in a flat for the heel of the hand.
    (-0.1100, -0.0150, -0.0770, 0.0176, 2.4, 0.90),
    (-0.1340, -0.0196, -0.1020, 0.0182, 2.4, 0.88),
    (-0.1500, -0.0188, -0.1090, 0.0186, 2.6, 0.86),
    # Behind it the belly comes up and the comb begins, high enough for an eye behind a scope.
    (-0.1660, -0.0150, -0.0890, 0.0190, 2.4, 0.82),
    (-0.1860, -0.0118, -0.0800, 0.0194, 2.4, 0.76),
    (-0.2300, -0.0132, -0.0900, 0.0202, 2.4, 0.72),
    (-0.2900, -0.0188, -0.1150, 0.0212, 2.5, 0.72),
    (-0.3300, -0.0238, -0.1320, 0.0218, 2.6, 0.74),
    (BUTT, -0.0260, -0.1380, 0.0218, 2.7, 0.75),
]


def stock_bottom(y):
    """How low the underside of the stock is at a place along it."""
    stations = along(STOCK, 96)
    for a, b in zip(stations, stations[1:]):
        if b[0] <= y <= a[0]:
            return b[2] + (a[2] - b[2]) * (y - b[0]) / (a[0] - b[0])
    raise ValueError("the stock does not reach %s" % y)


def stock():
    wood = bmesh.new()
    loft(wood, [G.stock_ring(*station) for station in along(STOCK, 96)])
    take(wood, "stock", "walnut", bevel=0.0012, segments=2, sharp=50.0, texels=1.1)

    # Before the wood: a white line, then a tip of black wood, rounded off.
    first = STOCK[0]
    line = bmesh.new()
    loft(line, [G.stock_ring(y, *first[1:], grow=0.0002) for y in (TIP_FROM - 0.0016, TIP_FROM)])
    take(line, "tip line", "ivory", sharp=40.0)
    tip = bmesh.new()
    keys = [(TIP_FROM, WOOD_TOP, -0.0310, 0.0170, 3.2, 1.0), (0.4720, WOOD_TOP, -0.0302, 0.0166, 3.0, 1.0), (0.4860, -0.0046, -0.0280, 0.0154, 2.7, 1.0),
            (0.4940, -0.0058, -0.0248, 0.0134, 2.4, 1.0), (0.4985, -0.0080, -0.0212, 0.0104, 2.2, 1.0), (TIP_TO, -0.0105, -0.0180, 0.0062, 2.0, 1.0)]
    loft(tip, [G.stock_ring(*station) for station in along(keys, 20)])
    take(tip, "fore-end tip", "ebony", bevel=0.0010, segments=2, sharp=50.0)

    G.butt_pad(BUTT, STOCK[-1][1:])

    studs = bmesh.new()
    profile = [(0.0, 0.0), (0.0042, 0.0), (0.0042, 0.002), (0.0028, 0.003), (0.0028, 0.0075), (0.0046, 0.0085), (0.0046, 0.0125), (0.003, 0.014), (0.0, 0.014)]
    lathe(studs, profile, Matrix.Translation((0.0, -0.285, -0.1118)) @ Matrix.Rotation(math.radians(180 - 22), 4, "X"), segments=20)
    lathe(studs, profile, Matrix.Translation((0.0, 0.405, -0.0328)) @ Matrix.Rotation(math.radians(180), 4, "X"), segments=20)
    take(studs, "sling studs", "blued", sharp=35.0)


def sights():
    # The rear sight: a base on the barrel, and a leaf standing on it with a notch in its top.
    at = 0.335
    r = barrel_radius(at)
    base = bmesh.new()
    loft(base, [[(-half, y, r - 0.0025), (half, y, r - 0.0025), (half * 0.8, y, top), (-half * 0.8, y, top)]
                for y, half, top in ((at - 0.024, 0.0040, r + 0.0008), (at - 0.016, 0.0056, r + 0.0048), (at + 0.010, 0.0056, r + 0.0062), (at + 0.014, 0.0050, r + 0.0030))])
    take(base, "rear sight base", "blued", bevel=0.0005, segments=2)
    notch = bmesh.new()
    cuboid(notch, -0.0011, 0.0011, at, at + 0.01, SIGHT_LINE - 0.0026, SIGHT_LINE + 0.01)
    notch_cut = cutter(notch)
    leaf = bmesh.new()
    cuboid(leaf, -0.0078, 0.0078, at + 0.0052, at + 0.0068, r + 0.0050, SIGHT_LINE)
    take(leaf, "rear sight leaf", "sight", bevel=0.0003, segments=1, cut=(notch_cut,))
    bpy.data.objects.remove(notch_cut)

    # The front sight: a ramp, cut across so that it does not shine, and a blade with a brass bead on it.
    front = 0.872
    r = barrel_radius(front)
    ramp = bmesh.new()
    loft(ramp, [[(-half, y, r - 0.0022), (half, y, r - 0.0022), (half * 0.7, y, top), (-half * 0.7, y, top)]
                for y, half, top in ((front - 0.050, 0.0030, r + 0.0006), (front - 0.012, 0.0042, r + 0.0108), (front + 0.010, 0.0042, r + 0.0112),
                                     (front + 0.014, 0.0038, r + 0.0060))])
    take(ramp, "front sight ramp", "ramp", bevel=0.0004, segments=1)
    blade = bmesh.new()
    cuboid(blade, -0.0009, 0.0009, front - 0.004, front + 0.007, r + 0.0105, SIGHT_LINE - 0.0012)
    take(blade, "front sight blade", "sight", bevel=0.0002, segments=1)
    bead = bmesh.new()
    lathe(bead, [(0.0015 * math.sin(math.radians(a)), -0.0015 * math.cos(math.radians(a))) for a in range(0, 181, 15)],
          Matrix.Translation((0.0, front - 0.004, SIGHT_LINE - 0.0015)), segments=16)
    take(bead, "front bead", "brass", sharp=50.0)


def case():
    """A fired case: a rimless bottle of brass, open at the mouth."""
    profile = [(0.0, -0.0255), (0.0048, -0.0255), (0.0058, -0.0250), (0.0058, -0.0243), (0.0050, -0.0240), (0.0050, -0.0232), (0.0059, -0.0226),
               (0.0057, 0.0130), (0.0043, 0.0178), (0.0043, 0.0255), (0.0039, 0.0255), (0.0039, 0.0120), (0.0, 0.0120)]
    brass = bmesh.new()
    lathe(brass, profile, along_y(CASE_AT.x, CASE_AT.y, CASE_AT.z), segments=28)
    take(brass, "case", "brass", group=CASE, sharp=30.0)


CHECKER_PITCH = 0.0016


def walnut(g):
    """An oiled walnut stock, with a panel of fine diamonds cut into each side of the grip and of the fore-end."""
    return G.walnut(g, ((-0.1240, -0.0600, 0.0270, 0.0140, 0.007, math.radians(56)), (0.300, -0.0235, 0.078, 0.0100, 0.005, math.radians(-2.5))), CHECKER_PITCH)


def ebony(g):
    """The black wood of the fore-end's tip: the stock's own figure, nearly lost in the dark."""
    light = g.grey(g.laid("walnut", 0.22))
    return g.finish(g.scaled(g.colour(0.013, 0.010, 0.009), g.add(0.6, g.times(light, 1.1))), g.add(0.30, g.times(g.math("SUBTRACT", 1.0, light), 0.14)), 0.0,
                    g.times(light, 0.00004))


def sight(g):
    """A sight's own steel: blued and left dull, so that its edges are black against what is aimed at."""
    colour, roughness, lines = G.steel(g, (0.020, 0.022, 0.028), 0.62, wear=0.25)
    return g.finish(colour, roughness, 1.0, g.times(lines, 0.00002))


def ramp(g):
    colour, roughness, lines = G.steel(g, (0.026, 0.028, 0.036), 0.50, wear=0.5)
    top = g.step(g.nz, 0.35, 0.8)
    return g.finish(colour, roughness, 1.0, g.times(top, g.teeth(g.times(g.y, 1.0 / 0.0012)), 0.00014))


G.MAKERS.update({"walnut": walnut, "ebony": ebony, "sight": sight, "ramp": ramp})
# Long and thin: its pictures are twice as wide as high.
G.WIDE = True
G.TUBES = {"barrel": (0.0, 0.53, 0.0110)}


def build():
    G.clear()
    case()
    barrel()
    receiver()
    bolt()
    furniture()
    stock()
    sights()
    G.count_triangles()


def pack(gun, colour, surface):
    """Writes the rifle's files, with the rifle's own measures in the head."""
    # The mod holds a weapon up as the game's own are made, which is the mirror image of how this one is laid out
    # here: its right side, with the bolt's handle and the port, has x less than nought in the file.
    muzzle = Vector((0.0, MUZZLE_Y, 0.0)) - GRIP
    port = (PORT - GRIP) * Vector((-1.0, 1.0, 1.0))
    G.pack(gun, "rifle", GRIP, colour, surface, range(5), G.head_v4(muzzle, HELD - GRIP, port, TRAVEL, SIGHT_LINE, EYE_BEHIND_GRIP), moved=((CASE, CASE_AT),))


VIEWS = {
    # name: where the camera is, what it looks at, focal length
    "right": ((2.30, 0.27, 0.10), (0.0, 0.27, -0.03), 60),
    "left": ((-2.30, 0.27, 0.10), (0.0, 0.27, -0.03), 60),
    "action": ((0.40, -0.06, 0.13), (0.0, 0.05, 0.0), 50),
    "action-left": ((-0.38, 0.26, 0.15), (0.0, 0.06, 0.0), 50),
    "under": ((0.30, -0.04, -0.20), (0.0, 0.05, -0.02), 45),
    "fore": ((0.42, 0.62, 0.16), (0.0, 0.36, -0.01), 50),
    "muzzle": ((0.20, 1.20, 0.13), (0.0, 0.72, 0.01), 60),
    "sights": ((0.0, -0.24, SIGHT_LINE + 0.004), (0.0, 0.90, SIGHT_LINE), 40),
    "hip": ((-0.13, -0.36, 0.20), (0.02, 0.50, 0.02), 22),
    "stock": ((0.50, -0.46, 0.12), (0.0, -0.14, -0.04), 50),
}


def main():
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if not args:
        raise SystemExit("say where the pictures from shotgun_textures.py are")
    G.load_pictures(args[0])
    build()
    if "--raw" in args:
        # The parts as they are made, under their own materials and unbaked: for looking at the shape.
        folder = os.path.abspath(args[args.index("--raw") + 1])
        os.makedirs(folder, exist_ok=True)
        G.render(folder, VIEWS, [name for name in args if name in VIEWS], samples=48)
        return
    gun = G.unwrap("rifle")
    if "--mesh" in args:
        pack(gun, None, None)
        return
    colour, surface = G.pictures_of(G.bake(gun))
    if "--no-pack" not in args:
        pack(gun, colour, surface)
    if "--renders" in args:
        folder = os.path.abspath(args[args.index("--renders") + 1])
        os.makedirs(folder, exist_ok=True)
        G.dress(gun, colour, surface, folder)
        G.render(folder, VIEWS, [name for name in args if name in VIEWS], samples=48)


main()
