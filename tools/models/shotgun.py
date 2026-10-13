"""Builds the mod's own model of a pump action shotgun, or of the same gun sawn off, and packs it for the jar.

Run headless with Blender 4.4, from the repository root:

    blender --background --factory-startup --python tools/models/shotgun.py -- PICTURES [--sawn-off] [--renders DIR] [--raw DIR [VIEW ...]] [--no-pack] [--cpu] [--mesh] [--stats]

PICTURES is the folder tools/models/shotgun_textures.py wrote. The model is made here from
nothing: a twelve bore field gun with a ventilated rib, a walnut stock and fore-end and a
blued receiver, of no maker's pattern. With --sawn-off it is that gun cut down: its barrel,
rib and magazine short, with the bead put back at the new muzzle, and its stock sawn off
behind the wrist and shaped into a grip for the hand. It writes NAME.mesh.gz, the
triangles, under src/main/resources/models, and NAME.colour.gz and NAME.surface.gz, 2048
texels square, under src/textures/models, for tools/textures/compress.py to pack for the
jar, where NAME is shotgun or sawnoff. The files are laid out as gunsmith.py says, at
version 4.

--raw renders the parts as they are made, unbaked, for looking at the shape.

The model is in metres. Its barrel runs along +y and its top is +z, as the game's own
weapons are made, and its origin is in the hand that holds its grip. Three parts of it
move: the fore-end with its action bars slides back along y to work the action, and the
bolt goes back with it and uncovers the port. With them is a fired shell, about its own
middle and lying along y, for the mod to throw out of the port. The stock is a part of its
own too: with the sights up it lies under the eye, and the mod leaves it out. The sawn-off's
grip is in that part. The five parts are in the file in that order: what is fixed, the
fore-end, the bolt, the shell and the stock.
"""

import math
import os
import sys

import bmesh
import bpy
from mathutils import Matrix, Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gunsmith as G  # noqa: E402
from gunsmith import along, along_x, along_y, cuboid, cutter, grip_ring, lathe, loft, smooth, stock_ring, sweep, take  # noqa: E402

SAWN_OFF = "--sawn-off" in sys.argv
NAME = "sawnoff" if SAWN_OFF else "shotgun"
# Laid out with the bore along y through x = 0, z = 0 and the trigger at y = 0. GRIP is
# where the hand that holds the grip is, which the finished model has as its origin: so far
# under the bore that the muzzle is as far above the line ahead of the hand as the muzzle
# of the game's own shotgun is, which is how the mod points a weapon. The game's sawn-off
# has its muzzle 0.3249 ahead of the origin and 0.0267 over it: cut where its muzzle is on
# the same line from the hand, the gun is drawn at 0.5057 of the game's units to a metre,
# where the whole one is drawn at 0.4957.
FULL_MUZZLE_Y = 0.780
MUZZLE_Y = 0.5225 if SAWN_OFF else FULL_MUZZLE_Y
GRIP = Vector((0.0, -0.120, -0.0528))
RECEIVER_FRONT, RECEIVER_REAR, RECEIVER_HALF = 0.105, -0.085, 0.0205
RECEIVER_TOP, RECEIVER_BOTTOM = 0.023, -0.047
TUBE_Z, TUBE_R = -0.0305, 0.0125
# Where the magazine tube ends and its cap begins. The sawn-off's cap comes to just short of its muzzle.
TUBE_END = MUZZLE_Y - 0.0350 if SAWN_OFF else 0.6005
FORE_REAR, FORE_FRONT = 0.225, 0.420
TRAVEL = 0.094
BUTT = -0.352
FIXED, PUMP, BOLT, SHELL, BUTT_END = 0, 1, 2, 3, 4
# The shell is made well clear of the gun, so that neither shades the other, and is packed about its own middle.
SHELL_AT = Vector((0.0, 0.30, 0.40))
PORT = Vector((0.0190, 0.047, -0.002))
# Over the back of the rib, by enough to see the bead at its far end.
SIGHT_LINE = 0.0275
# A little behind where a cheek on the comb would have it: from there the back of the receiver is a hand's width in the
# picture and the rib runs away from it, where from over the comb it is a wall across the foot of the picture.
EYE_BEHIND_GRIP = 0.26


def barrel_radius(y):
    if y < 0.19:
        return 0.0135
    if y < 0.26:
        return 0.0135 + (0.0118 - 0.0135) * (y - 0.19) / 0.07
    return 0.0118 + (0.0108 - 0.0118) * (y - 0.26) / (FULL_MUZZLE_Y - 0.26)


def rib_top(y):
    return 0.0215 + (0.0183 - 0.0215) * (y - 0.110) / (0.778 - 0.110)


def barrel():
    bm = bmesh.new()
    profile = [(0.0, 0.098), (0.0135, 0.098), (0.0135, 0.19), (0.0118, 0.26)]
    profile += [(barrel_radius(y), y) for y in (0.36, 0.46, 0.56, 0.66, 0.76) if y < MUZZLE_Y - 0.01]
    end = round(barrel_radius(MUZZLE_Y), 5)
    profile += [(end, MUZZLE_Y - 0.0012), (round(end - 0.0007, 5), MUZZLE_Y), (0.00955, MUZZLE_Y), (0.0092, MUZZLE_Y - 0.0006), (0.0092, MUZZLE_Y - 0.09), (0.0, MUZZLE_Y - 0.09)]
    lathe(bm, profile, along_y(), segments=56)
    take(bm, "barrel", "blued", sharp=25.0)
    collar = bmesh.new()
    lathe(collar, [(0.0, RECEIVER_FRONT - 0.001), (0.0158, RECEIVER_FRONT - 0.001), (0.0158, RECEIVER_FRONT + 0.0045), (0.0150, RECEIVER_FRONT + 0.0060),
                   (0.0, RECEIVER_FRONT + 0.0060)], along_y(), segments=56)
    take(collar, "barrel collar", "blued", sharp=25.0)

    rib = bmesh.new()
    rear, front, half = 0.110, MUZZLE_Y - 0.002, 0.0038
    stations = [rear + (front - rear) * n / 12 for n in range(13)]
    loft(rib, [[(sx * half, y, rib_top(y) - (0.0021 if low else 0.0)) for sx, low in ((-1, True), (1, True), (1, False), (-1, False))] for y in stations])
    take(rib, "rib", "rib", bevel=0.0003, segments=1)

    posts = bmesh.new()
    y = 0.132
    while y < MUZZLE_Y - 0.01:
        cuboid(posts, -0.0019, 0.0019, y - 0.008, y + 0.008, barrel_radius(y) - 0.0015, rib_top(y) - 0.0018)
        y += 0.058
    take(posts, "rib posts", "blued", bevel=0.0004, segments=1)

    beads = bmesh.new()
    bead = MUZZLE_Y - 0.0085
    lathe(beads, [(0.0, 0.0), (0.0011, 0.0), (0.0011, 0.0012), (0.0019, 0.0020), (0.0021, 0.0032), (0.0015, 0.0046), (0.0, 0.0051)],
          Matrix.Translation((0.0, bead, rib_top(bead) - 0.0003)), segments=20)
    take(beads, "front bead", "brass", sharp=50.0)
    if SAWN_OFF:
        return
    middle = bmesh.new()
    lathe(middle, [(0.0, 0.0), (0.0008, 0.0), (0.0011, 0.0008), (0.0008, 0.0018), (0.0, 0.0021)], Matrix.Translation((0.0, 0.44, rib_top(0.44) - 0.0002)), segments=14)
    take(middle, "middle bead", "bright", sharp=50.0)


def magazine():
    tube = bmesh.new()
    stations = [y for y in (0.098, 0.2, 0.3, 0.35, 0.45, 0.55) if y < TUBE_END - 0.01] + [TUBE_END]
    lathe(tube, [(0.0, 0.098)] + [(TUBE_R, y) for y in stations] + [(0.0, TUBE_END)], along_y(z=TUBE_Z), segments=48)
    take(tube, "magazine tube", "blued")

    cap = bmesh.new()
    lathe(cap, [(radius, TUBE_END + y) for radius, y in ((0.0, -0.0010), (0.0139, -0.0010), (0.0143, 0.0), (0.0143, 0.0180), (0.0138, 0.0195), (0.0132, 0.0210),
                                                         (0.0132, 0.0235), (0.0118, 0.0285), (0.0085, 0.0320), (0.004, 0.0335), (0.0, 0.0337))],
          along_y(z=TUBE_Z), segments=48)
    take(cap, "magazine cap", "knurled", sharp=28.0)

    stud = bmesh.new()
    lathe(stud, [(0.0, 0.0), (0.0042, 0.0), (0.0042, 0.002), (0.0028, 0.003), (0.0028, 0.0075), (0.0046, 0.0085), (0.0046, 0.0125), (0.003, 0.014), (0.0, 0.014)],
          Matrix.Translation((0.0, TUBE_END + 0.0095, TUBE_Z - 0.0135)) @ Matrix.Rotation(math.radians(180), 4, "X"), segments=20)
    take(stud, "front stud", "blued", sharp=35.0)

    # The ring on the barrel that the magazine cap is screwed up against.
    ring = bmesh.new()
    front, back = TUBE_END - 0.0015, TUBE_END - 0.0195
    lathe(ring, [(0.0, back), (0.0156, back), (0.0160, back + 0.0006), (0.0160, front - 0.0006), (0.0156, front), (0.0, front)], along_y(z=TUBE_Z), segments=48)
    take(ring, "barrel ring", "blued", sharp=28.0)
    web = bmesh.new()
    rings = []
    for y in (back, front):
        rings.append([(-0.0078, y, TUBE_Z + 0.012), (0.0078, y, TUBE_Z + 0.012), (0.0056, y, -0.004), (-0.0056, y, -0.004)])
    loft(web, rings)
    take(web, "barrel ring web", "blued", bevel=0.0006)


def receiver_ring(y):
    slope = 1.0 - (1.0 - 0.743) * smooth((-0.045 - y) / 0.04)
    half = RECEIVER_HALF
    right = [(half - 0.0035, RECEIVER_BOTTOM), (half, RECEIVER_BOTTOM + 0.0035), (half, -0.012)]
    right += [(half * math.cos(math.radians(a)), 0.0025 + half * math.sin(math.radians(a))) for a in range(0, 90, 6)]
    points = right + [(0.0, RECEIVER_TOP)] + [(-x, z) for x, z in reversed(right)]
    return [(x, y, z if z <= -0.012 else -0.012 + (z + 0.012) * slope) for x, z in points]


def receiver():
    port = bmesh.new()
    cuboid(port, 0.006, 0.03, 0.010, 0.084, -0.0115, 0.0072)
    port_cut = cutter(port, bevel=0.0035)
    tunnel = bmesh.new()
    cuboid(tunnel, -0.0128, 0.0172, -0.040, 0.0845, -0.0118, 0.0075)
    tunnel_cut = cutter(tunnel)
    loading = bmesh.new()
    cuboid(loading, -0.0112, 0.0112, 0.034, 0.098, -0.06, -0.022)
    loading_cut = cutter(loading, bevel=0.0)

    body = bmesh.new()
    stations = [RECEIVER_REAR, -0.080, -0.074, -0.067, -0.060, -0.053, -0.045, -0.02, 0.01, 0.04, 0.07, RECEIVER_FRONT]
    loft(body, [receiver_ring(y) for y in stations])
    take(body, "receiver", "receiver", bevel=0.0005, segments=2, sharp=40.0, cut=(tunnel_cut, port_cut, loading_cut), texels=1.25)
    for used in (port_cut, tunnel_cut, loading_cut):
        bpy.data.objects.remove(used)

    bolt = bmesh.new()
    cuboid(bolt, -0.0122, 0.0165, 0.0105, 0.0838, -0.0110, 0.0067)
    take(bolt, "bolt", "bright", group=BOLT, bevel=0.0012, segments=3)
    extractor = bmesh.new()
    cuboid(extractor, 0.0160, 0.0171, 0.056, 0.0838, -0.0042, 0.0012)
    cuboid(extractor, 0.0160, 0.0169, 0.014, 0.050, -0.0085, -0.0070)
    take(extractor, "extractor", "blued", group=BOLT, bevel=0.0003, segments=1)

    # The carrier that lifts a shell from the magazine closes the port underneath.
    carrier = bmesh.new()
    cuboid(carrier, -0.0106, 0.0106, 0.036, 0.096, -0.0455, -0.0440)
    cuboid(carrier, -0.0030, 0.0030, 0.050, 0.090, -0.0462, -0.0450)
    take(carrier, "carrier", "bright", bevel=0.0005, segments=1)

    pins = bmesh.new()
    for y, z in ((-0.058, -0.027), (0.014, -0.034)):
        lathe(pins, [(0.0, -0.0208), (0.0024, -0.0208), (0.0028, -0.0204), (0.0028, 0.0204), (0.0024, 0.0208), (0.0, 0.0208)], along_x(0.0, y, z), segments=20)
    take(pins, "pins", "blued", sharp=30.0)


def trigger_group():
    plate = bmesh.new()
    cuboid(plate, -0.0100, 0.0100, -0.080, 0.0325, -0.0493, -0.045)
    take(plate, "trigger plate", "black", bevel=0.0012, segments=3)
    housing = bmesh.new()
    cuboid(housing, -0.0082, 0.0082, -0.0535, -0.0345, -0.0635, -0.046)
    take(housing, "safety housing", "black", bevel=0.0028, segments=4)

    guard = bmesh.new()
    path = along([(0.0315, -0.0470), (0.0345, -0.0560), (0.0320, -0.0680), (0.0230, -0.0790), (0.0080, -0.0845), (-0.0120, -0.0850), (-0.0300, -0.0815),
                  (-0.0420, -0.0730), (-0.0470, -0.0620), (-0.0480, -0.0500)], 44)
    sweep(guard, path, 0.0068, [0.0034 + 0.0016 * abs(n / 43 - 0.5) * 2 for n in range(44)])
    take(guard, "trigger guard", "black", bevel=0.0011, segments=3, sharp=50.0)

    trigger = bmesh.new()
    path = along([(0.0060, -0.0455), (0.0055, -0.0540), (0.0030, -0.0620), (-0.0005, -0.0690), (-0.0015, -0.0745), (0.0005, -0.0775)], 20)
    sweep(trigger, path, 0.0034, [0.0062 - 0.0030 * n / 19 for n in range(20)])
    take(trigger, "trigger", "blued", bevel=0.0010, segments=3, sharp=50.0)

    safety = bmesh.new()
    lathe(safety, [(0.0, -0.0108), (0.0030, -0.0108), (0.0035, -0.0102), (0.0035, 0.0118), (0.0030, 0.0124), (0.0, 0.0124)], along_x(0.0, -0.043, -0.0575), segments=24)
    take(safety, "safety", "blued", sharp=30.0)

    release = bmesh.new()
    cuboid(release, -0.0118, -0.0098, 0.0335, 0.0455, -0.0540, -0.0470)
    take(release, "action release", "blued", bevel=0.0008, segments=2)


STOCK = [
    # y, top, bottom, half width, how square, how narrow the comb is against the belly
    (-0.0830, 0.0122, -0.0465, 0.0200, 3.6, 0.98),
    (-0.0950, 0.0068, -0.0490, 0.0192, 2.9, 0.94),
    (-0.1150, -0.0030, -0.0550, 0.0181, 2.4, 0.90),
    (-0.1400, -0.0105, -0.0660, 0.0178, 2.2, 0.88),
    (-0.1600, -0.0140, -0.0820, 0.0182, 2.2, 0.86),
    (-0.1760, -0.0155, -0.0935, 0.0187, 2.3, 0.84),
    (-0.1880, -0.0162, -0.0985, 0.0190, 2.4, 0.82),
    (-0.2000, -0.0170, -0.1010, 0.0192, 2.4, 0.79),
    (-0.2150, -0.0185, -0.1035, 0.0195, 2.3, 0.75),
    (-0.2500, -0.0240, -0.1150, 0.0203, 2.3, 0.71),
    (-0.3000, -0.0320, -0.1390, 0.0211, 2.4, 0.71),
    (-0.3400, -0.0380, -0.1590, 0.0215, 2.6, 0.73),
    (BUTT, -0.0400, -0.1650, 0.0215, 2.7, 0.74),
]


def stock():
    wood = bmesh.new()
    stations = along(STOCK, 64)
    loft(wood, [stock_ring(*station) for station in stations])
    take(wood, "stock", "walnut", group=BUTT_END, bevel=0.0012, segments=2, sharp=50.0, texels=1.1)

    G.butt_pad(BUTT, STOCK[-1][1:], group=BUTT_END)

    stud = bmesh.new()
    lathe(stud, [(0.0, 0.0), (0.0042, 0.0), (0.0042, 0.002), (0.0028, 0.003), (0.0028, 0.0075), (0.0046, 0.0085), (0.0046, 0.0125), (0.003, 0.014), (0.0, 0.014)],
          Matrix.Translation((0.0, -0.290, -0.1335)) @ Matrix.Rotation(math.radians(180 - 27), 4, "X"), segments=20)
    take(stud, "rear stud", "blued", group=BUTT_END, sharp=35.0)


# The sawn-off's grip, from where the stock meets the receiver down to its foot: each station
# is a point on its front edge and one on its back edge in (y, z), the cross section's half
# width, how square it is, and how narrow the back strap is against the front. It meets the
# receiver below its top, so that with the sights up the eye looks over the receiver's back
# and not into the end grain of the wood.
GRIP_FRONT = [(-0.0830, -0.0465), (-0.0720, -0.0500), (-0.0640, -0.0560), (-0.0640, -0.0660), (-0.0680, -0.0800), (-0.0770, -0.0960), (-0.0835, -0.1110),
              (-0.0900, -0.1245), (-0.0950, -0.1340)]
GRIP_BACK = [(-0.0830, -0.0080), (-0.0905, -0.0135), (-0.0985, -0.0215), (-0.1055, -0.0330), (-0.1110, -0.0460), (-0.1190, -0.0680), (-0.1255, -0.0895),
             (-0.1330, -0.1090), (-0.1460, -0.1250)]
GRIP_SECTION = [(0.0200, 3.6, 0.98), (0.0192, 3.0, 0.94), (0.0184, 2.6, 0.90), (0.0178, 2.4, 0.88), (0.0175, 2.3, 0.88), (0.0175, 2.3, 0.88), (0.0177, 2.3, 0.88),
                (0.0181, 2.4, 0.88), (0.0186, 2.6, 0.90)]


def grip_stub():
    """The stock sawn off behind the wrist, and what is left of it shaped down into a grip, with a cap on its foot."""
    stations = along([front + back + section for front, back, section in zip(GRIP_FRONT, GRIP_BACK, GRIP_SECTION)], 48)
    wood = bmesh.new()
    loft(wood, [grip_ring(s[0:2], s[2:4], *s[4:]) for s in stations])
    take(wood, "grip", "walnut", group=BUTT_END, bevel=0.0012, segments=2, sharp=50.0, texels=1.1)

    # The cap stands on the foot along the grip's length, which runs from the middle of the last station but one to the last.
    last, before = stations[-1], stations[-2]
    down = Vector(((last[0] + last[2] - before[0] - before[2]) / 2, (last[1] + last[3] - before[1] - before[3]) / 2)).normalized()
    cap = bmesh.new()
    loft(cap, [grip_ring(last[0:2], last[2:4], *last[4:], grow=grow, out=(down.x * along_it, down.y * along_it))
               for along_it, grow in ((-0.0004, 0.0002), (0.0030, 0.0002), (0.0042, -0.0008), (0.0050, -0.0030))])
    take(cap, "grip cap", "black", group=BUTT_END, sharp=40.0)


def fore_ring(y, scale):
    """The fore-end cut across: a trough round the magazine tube, open at the top where the barrel lies in it."""
    outer = [(0.0200, -0.0040), (0.0216, -0.0062), (0.0234, -0.0100), (0.0236, -0.0140), (0.0218, -0.0180), (0.0216, -0.0215), (0.0240, -0.0262),
             (0.0263, -0.0335), (0.0266, -0.0400), (0.0256, -0.0462), (0.0232, -0.0522), (0.0194, -0.0572), (0.0142, -0.0610), (0.0076, -0.0634)]
    middle = Vector((0.0, -0.034))
    shaped = [((x - middle.x) * scale + middle.x, (z - middle.y) * scale + middle.y) for x, z in outer]
    low = (0.0, (-0.0642 - middle.y) * scale + middle.y)
    inner = [(0.0139 * math.cos(math.radians(a)), 0.0139 * math.sin(math.radians(a))) for a in range(-163, -16, 7)]
    points = shaped + [low] + [(-x, z) for x, z in reversed(shaped)] + [(-0.0133, -0.0040)] + inner + [(0.0133, -0.0040)]
    return [(x, y, z) for x, z in points]


def fore_end():
    wood = bmesh.new()
    keys = [(FORE_REAR, 0.80), (FORE_REAR + 0.0015, 0.90), (FORE_REAR + 0.005, 0.955), (FORE_REAR + 0.012, 0.985), (FORE_REAR + 0.03, 1.0)]
    keys += [(FORE_REAR + 0.03 + (FORE_FRONT - FORE_REAR - 0.075) * n / 10, 1.0 + 0.012 * math.sin(math.pi * n / 10)) for n in range(1, 10)]
    keys += [(FORE_FRONT - 0.045, 1.0), (FORE_FRONT - 0.02, 0.96), (FORE_FRONT - 0.008, 0.91), (FORE_FRONT - 0.002, 0.85), (FORE_FRONT, 0.76)]
    loft(wood, [fore_ring(y, scale) for y, scale in keys])
    take(wood, "fore-end", "walnut", group=PUMP, bevel=0.0009, segments=2, sharp=42.0, texels=1.1)

    nut = bmesh.new()
    lathe(nut, [(0.0, FORE_FRONT - 0.004), (0.0162, FORE_FRONT - 0.004), (0.0162, FORE_FRONT + 0.0030), (0.0155, FORE_FRONT + 0.0040), (0.0, FORE_FRONT + 0.0040)],
          along_y(z=TUBE_Z), segments=40)
    take(nut, "fore-end nut", "blued", group=PUMP, sharp=28.0)

    bars = bmesh.new()
    for side in (-1, 1):
        low, high = sorted((side * 0.0136, side * 0.0156))
        cuboid(bars, low, high, 0.020, FORE_REAR + 0.03, -0.0300, -0.0215)
    take(bars, "action bars", "blued", group=PUMP, bevel=0.0004, segments=1)


def knurled(g):
    colour, roughness, lines = G.steel(g, (0.030, 0.033, 0.044), 0.36)
    band = g.times(g.step(g.y, TUBE_END, TUBE_END + 0.0007), g.math("SUBTRACT", 1.0, g.step(g.y, TUBE_END + 0.0173, TUBE_END + 0.0180)))
    round_it = g.math("ARCTAN2", g.x, g.math("SUBTRACT", g.z, TUBE_Z))
    return g.finish(colour, roughness, 1.0, g.times(band, g.teeth(g.times(round_it, 40 / (2 * math.pi))), 0.0003))


def walnut(g):
    # Cut into the sides of the grip and of the fore-end, a panel of fine diamonds for the hand to hold by.
    if SAWN_OFF:
        grip = (-0.0980, -0.0840, 0.034, 0.0140, 0.008, math.radians(69.5))
    else:
        grip = (-0.150, -0.045, 0.032, 0.0165, 0.010, math.radians(24))
    return G.walnut(g, (grip, ((FORE_REAR + FORE_FRONT) / 2, -0.0395, 0.070, 0.0108, 0.005, 0.0)), G.CHECKER_PITCH)


G.MAKERS.update({"hull": G.hull_at(SHELL_AT.z), "knurled": knurled, "walnut": walnut})


def shell():
    """A fired shell: a brass head with its rim, and the plastic body open at the mouth where the crimp unfolded."""
    to = along_y(SHELL_AT.x, SHELL_AT.y, SHELL_AT.z)
    head = bmesh.new()
    G.shell_head(head, to, -0.0350)
    take(head, "shell head", "brass", group=SHELL, sharp=30.0)
    body = bmesh.new()
    lathe(body, [(0.0, -0.0195), (0.0100, -0.0195), (0.0100, 0.0338), (0.0097, 0.0350), (0.0090, 0.0350), (0.0090, -0.0150), (0.0, -0.0150)], to, segments=28)
    take(body, "shell body", "hull", group=SHELL, sharp=30.0)




def build():
    G.clear()
    shell()
    barrel()
    magazine()
    receiver()
    trigger_group()
    if SAWN_OFF:
        grip_stub()
    else:
        stock()
    fore_end()
    G.count_triangles()


G.TUBES = {"barrel": (0.0, 0.31 if SAWN_OFF else 0.46, 0.0122), "magazine tube": (TUBE_Z, 0.29 if SAWN_OFF else 0.35, TUBE_R)}


def pack(gun, colour, surface):
    # The mod holds a weapon up as the game's own are made, which is the mirror image of how this one is laid out
    # here: its right side, with the port the spent shells leave by, has x less than nought in the file.
    muzzle = Vector((0.0, MUZZLE_Y, 0.0)) - GRIP
    held = Vector((0.0, (FORE_REAR + FORE_FRONT) / 2, -0.034)) - GRIP
    port = (PORT - GRIP) * Vector((-1.0, 1.0, 1.0))
    G.pack(gun, NAME, GRIP, colour, surface, (FIXED, PUMP, BOLT, SHELL, BUTT_END), G.head_v4(muzzle, held, port, TRAVEL, SIGHT_LINE, EYE_BEHIND_GRIP),
           moved=((SHELL, SHELL_AT),))


# Half way from the butt to the muzzle, from the grip, and how far off the gun is seen whole.
MIDDLE, OFF = (0.30, 1.5) if SAWN_OFF else (0.33, 2.15)
VIEWS = {
    # name: where the camera is, what it looks at, focal length
    "right": ((OFF, MIDDLE, 0.10), (0.0, MIDDLE, -0.03), 60),
    "left": ((-OFF, MIDDLE, 0.10), (0.0, MIDDLE, -0.03), 60),
    "receiver": ((0.42, 0.02, 0.10), (0.0, 0.10, 0.03), 50),
    "receiver-left": ((-0.40, 0.30, 0.16), (0.0, 0.12, 0.03), 50),
    "under": ((0.30, 0.05, -0.16), (0.0, 0.18, 0.0), 45),
    "fore": ((0.45, 0.62, 0.16), (0.0, 0.42, 0.02), 50),
    "muzzle": ((0.22, MUZZLE_Y + 0.40, 0.16), (0.0, MUZZLE_Y - 0.16, 0.035), 55),
    "sights": ((0.0, -0.21, 0.0835), (0.0, 0.90, 0.0705), 40),
    "hip": ((-0.13, -0.36, 0.20), (0.02, 0.50, 0.02), 22),
    "stock": ((0.50, -0.42, 0.12), (0.0, -0.10, -0.03), 50),
}


def to_grip():
    """Puts the gun, joined in one or in its parts as they were made, about the hand on its grip."""
    for obj in [bpy.data.objects[NAME]] if NAME in bpy.data.objects else G.PARTS:
        obj.location = -GRIP


def main():
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if not args:
        raise SystemExit("say where the pictures from shotgun_textures.py are")
    G.load_pictures(args[0])
    build()
    if "--raw" in args:
        # The parts as they are made, under their own materials and unbaked: for looking at the shape.
        at = args.index("--raw")
        G.render(args[at + 1], VIEWS, [name for name in args[at + 2:] if not name.startswith("--")], samples=24, moved=to_grip)
        return
    gun = G.unwrap(NAME)
    if "--mesh" in args:
        # The triangles alone, which do not change with what is baked onto them.
        pack(gun, None, None)
        return
    if "--stats" in args:
        G.stats(gun, ".lab/shotgun/layout.png")
        return
    colour, surface = G.pictures_of(G.bake(gun))
    if "--no-pack" not in args:
        pack(gun, colour, surface)
    if "--renders" in args:
        folder = os.path.abspath(args[args.index("--renders") + 1])
        os.makedirs(folder, exist_ok=True)
        G.dress(gun, colour, surface, folder)
        G.render(folder, VIEWS, moved=to_grip)
    if "--blend" in args:
        bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(args[args.index("--blend") + 1]))


main()
