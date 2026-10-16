"""Builds the mod's own model of the over and under double barrelled shotgun, and of the same gun sawn off, and packs both for the jar.

Run headless with Blender 4.4, from the repository root:

    blender --background --factory-startup --threads 1 --python tools/models/dbarrel.py -- PICTURES [--renders DIR] [--raw DIR [VIEW ...]] [--no-pack] [--cpu] [--mesh] [--stats]

PICTURES is the folder tools/models/shotgun_textures.py wrote. The model is made here from
nothing: a twelve bore over and under field gun of no maker's pattern, its action in bright
steel with an engraved border, two triggers, a top lever, a ventilated rib, side ribs between
the barrels, and a walnut stock and fore-end. The game's own model of it has no hammers, and
neither has this. Sawn off, the barrels are cut short, the bead is put back at the new muzzle
and the stock is sawn off behind the wrist and shaped into a grip.

Both guns are made at once and baked into one texture, which the two share: what only one of
them has (the full gun's stock, muzzles and beads, the sawn-off's grip, muzzles and bead) is
baked beside the rest, and the barrels and ribs, laid out so that the sawn-off's are the rear
part of the full gun's, are cut where the sawn-off ends. It writes dbarrel.mesh.gz and
dbarrelsawn.mesh.gz under src/main/resources/models, and dbarrel.colour.gz and
dbarrel.surface.gz, 2048 by 1024 texels, under src/textures/models, for
tools/textures/compress.py to pack for the jar. The files are laid out as gunsmith.py says, at
version 5.

The model is in metres. Its bore runs along +y, half way between its two barrels, and its top
is +z, as the game's own weapons are made, and its origin is in the hand that holds its grip.
It is in six parts, in this order in the file: what is fixed; the barrels with the fore-end,
which turn down about the hinge to open the gun; the top lever, which turns to the right about
an upright to let them go; a fired shell and a shell not yet fired, each lying along y with its
rim in the lower chamber; and the stock. The numbers that say how the parts move are, from the grip
and as the file has them: the hinge's y and z, how far the barrels turn open, in radians, the
top lever's y, how far it turns, in radians, the breech face's y, the lower and the upper
chamber's z, and how long a shell is.
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

G.TEXTURE = 2048
FULL, SAWN = "dbarrel", "dbarrelsawn"
FIXED, BARRELS, LEVER, SPENT, LIVE, STOCK = 0, 1, 2, 3, 4, 5
GROUPS = (FIXED, BARRELS, LEVER, SPENT, LIVE, STOCK)

# The two bores, over and under the line half way between them.
UPPER_Z, LOWER_Z = 0.0115, -0.0115
BORE_R, CHAMBER_R, RIM_R = 0.0092, 0.0104, 0.0113
# The barrels' back face, which the shells' rims are flush with, and the front of the block they are joined in.
BREECH_Y, MONOBLOC_FRONT = 0.045, 0.116
FULL_MUZZLE_Y = 0.770
# GRIP is the hand on the grip, which the files have as their origin: so far under the bore that
# the muzzle is as far above the line ahead of the hand as the muzzle of the game's own gun,
# which is how the mod points a weapon. The game's has its muzzle 0.4531 ahead and 0.0205 over
# its origin; its sawn-off 0.3138 and 0.0198. Cut where the sawn-off's muzzle is on its own line
# from the same hand, the sawn-off's barrels end at SAWN_Y.
GRIP = Vector((0.0, -0.160, -0.0205 / 0.4531 * (FULL_MUZZLE_Y + 0.160)))
SAWN_Y = round(GRIP.y - GRIP.z * 0.3138 / 0.0198, 4)
# The hinge: the stub pins in the action's walls the barrels turn on, and how far they turn open.
HINGE = Vector((0.0, 0.083, -0.0165))
OPENS = math.radians(38.0)
# The top lever turns about an upright at its front, this far to the right.
LEVER_Y, LEVER_TURNS = -0.004, math.radians(32.0)
ACTION_TOP, ACTION_BOTTOM, ACTION_HALF, ACTION_REAR, CHEEK_FRONT = 0.0262, -0.0440, 0.0186, -0.030, 0.093
FORE_REAR, FORE_FRONT = 0.096, 0.300
SHELL_LONG = 0.070
# Each shell is made well clear of the gun, so that neither shades it, and packed in the lower chamber, its rim at CHAMBER: shut in
# the gun it is inside the barrels' outline, which is what the hands are put on by.
SPENT_AT, LIVE_AT = Vector((0.0, 0.30, 0.40)), Vector((0.0, 0.30, 0.48))
CHAMBER = Vector((0.0, BREECH_Y, LOWER_Z))
# Over the grip, which is under the bore by GRIP's height: set by where the bead sits in the sight picture in the game. At 0.042 the
# action hid the rib, and at 0.081 the bead sat under the reticle.
SIGHT_LINE = 0.0700
EYE_BEHIND_GRIP = 0.22
# A part only one of the two guns has, by its name, and the parts the sawn-off has the rear of.
ONLY = {}
CUT = set()


def made(obj, only=None, cut=False):
    if only:
        ONLY[obj.name] = only
    if cut:
        CUT.add(obj.name)
    return obj


def barrel_radius(y):
    if y < 0.20:
        return 0.0113
    return 0.0113 + (0.0098 - 0.0113) * (y - 0.20) / (FULL_MUZZLE_Y - 0.20)


def stations(rear, front, every=0.1):
    """Places along the barrels for rings of vertices: SAWN_Y among them, so that cutting there leaves no face across it."""
    ys = [rear] + [y for y in (0.20, 0.30, 0.40, 0.50, 0.60, 0.70) if rear < y < front] + [front]
    if rear < SAWN_Y < front:
        ys.append(SAWN_Y)
    return sorted(set(round(y, 5) for y in ys))


def barrels():
    for name, z in (("upper barrel", UPPER_Z), ("lower barrel", LOWER_Z)):
        bm = bmesh.new()
        end = round(barrel_radius(FULL_MUZZLE_Y), 5)
        profile = [(0.0, MONOBLOC_FRONT - 0.002)] + [(barrel_radius(y), y) for y in stations(MONOBLOC_FRONT - 0.002, FULL_MUZZLE_Y - 0.0012)]
        profile += [(round(end - 0.0006, 5), FULL_MUZZLE_Y), (BORE_R + 0.0003, FULL_MUZZLE_Y), (BORE_R, FULL_MUZZLE_Y - 0.0006), (BORE_R, FULL_MUZZLE_Y - 0.09),
                    (0.0, FULL_MUZZLE_Y - 0.09)]
        # The muzzle's crown and bore are the full gun's alone: they are faces past SAWN_Y, which the sawn-off leaves out.
        lathe(bm, profile, along_y(z=z), segments=56)
        made(take(bm, name, "blued", group=BARRELS, sharp=25.0), cut=True)
        crown = bmesh.new()
        sawn = round(barrel_radius(SAWN_Y), 5)
        lathe(crown, [(sawn, SAWN_Y), (round(sawn - 0.0006, 5), SAWN_Y + 0.0006), (BORE_R + 0.0003, SAWN_Y + 0.0006), (BORE_R, SAWN_Y),
                      (BORE_R, SAWN_Y - 0.08), (0.0, SAWN_Y - 0.08)], along_y(z=z), segments=56)
        made(take(crown, name + " sawn crown", "bright", group=BARRELS, sharp=25.0), only=SAWN)

    # The side ribs fill the groove between the barrels down either side.
    side = bmesh.new()
    ys = stations(MONOBLOC_FRONT - 0.002, FULL_MUZZLE_Y - 0.003)
    loft(side, [[(sx * 0.80 * barrel_radius(y), y, sz * 0.0040) for sx, sz in ((-1, -1), (1, -1), (1, 1), (-1, 1))] for y in ys])
    made(take(side, "side ribs", "blued", group=BARRELS, bevel=0.0003, segments=1), cut=True)

    rib = bmesh.new()
    rear, half = MONOBLOC_FRONT - 0.004, 0.0040
    top = lambda y: UPPER_Z + barrel_radius(y) + 0.0045 - 0.0012 * (y - rear) / (FULL_MUZZLE_Y - rear)
    ys = sorted(set([rear + (FULL_MUZZLE_Y - 0.002 - rear) * n / 12 for n in range(13)] + [SAWN_Y]))
    loft(rib, [[(sx * half, y, top(y) - (0.0021 if low else 0.0)) for sx, low in ((-1, True), (1, True), (1, False), (-1, False))] for y in ys])
    made(take(rib, "rib", "rib", group=BARRELS, bevel=0.0003, segments=1), cut=True)
    posts = bmesh.new()
    y = rear + 0.022
    while y < FULL_MUZZLE_Y - 0.01:
        cuboid(posts, -0.0019, 0.0019, y - 0.006, y + 0.006, UPPER_Z + barrel_radius(y) - 0.0015, top(y) - 0.0018)
        y += 0.052
    made(take(posts, "rib posts", "blued", group=BARRELS, bevel=0.0004, segments=1), cut=True)

    for name, at, only in (("front bead", FULL_MUZZLE_Y - 0.0085, FULL), ("sawn bead", SAWN_Y - 0.0085, SAWN)):
        bead = bmesh.new()
        lathe(bead, [(0.0, 0.0), (0.0011, 0.0), (0.0011, 0.0012), (0.0019, 0.0020), (0.0021, 0.0032), (0.0015, 0.0046), (0.0, 0.0051)],
              Matrix.Translation((0.0, at, top(at) - 0.0003)), segments=20)
        made(take(bead, name, "brass", group=BARRELS, sharp=50.0), only=only)
    middle = bmesh.new()
    lathe(middle, [(0.0, 0.0), (0.0008, 0.0), (0.0011, 0.0008), (0.0008, 0.0018), (0.0, 0.0021)], Matrix.Translation((0.0, 0.44, top(0.44) - 0.0002)), segments=14)
    made(take(middle, "middle bead", "bright", group=BARRELS, sharp=50.0), only=FULL)
    return top


def monobloc_ring(y, grow=0.0):
    """The block the barrels are joined in, cut across: two round ends over and under, flat sides."""
    half, low, high = 0.0122 + grow, LOWER_Z - 0.0116 - grow, UPPER_Z + 0.0116 + grow
    ring = []
    for n in range(48):
        a = 2 * math.pi * (n + 0.5) / 48
        c, s = math.cos(a), math.sin(a)
        # Squared off: a superellipse, round enough at its top and bottom to follow the barrels.
        x = half * math.copysign(abs(c) ** (2 / 3.0), c)
        z = (high + low) / 2 + (high - low) / 2 * math.copysign(abs(s) ** (2 / 2.6), s)
        ring.append((x, y, z))
    return ring


def monobloc():
    holes = []
    for z in (UPPER_Z, LOWER_Z):
        chamber = bmesh.new()
        lathe(chamber, [(0.0, BREECH_Y - 0.002), (CHAMBER_R, BREECH_Y - 0.002), (CHAMBER_R, MONOBLOC_FRONT + 0.004), (0.0, MONOBLOC_FRONT + 0.004)], along_y(z=z), segments=40)
        holes.append(cutter(chamber))
        rim = bmesh.new()
        lathe(rim, [(0.0, BREECH_Y - 0.002), (RIM_R, BREECH_Y - 0.002), (RIM_R, BREECH_Y + 0.0016), (0.0, BREECH_Y + 0.0016)], along_y(z=z), segments=40)
        holes.append(cutter(rim))
    block = bmesh.new()
    loft(block, [monobloc_ring(y) for y in (BREECH_Y, BREECH_Y + 0.004, 0.08, MONOBLOC_FRONT - 0.006, MONOBLOC_FRONT)])
    take(block, "monobloc", "blued", group=BARRELS, bevel=0.0008, segments=2, sharp=40.0, cut=holes, texels=1.25)
    for used in holes:
        bpy.data.objects.remove(used)

    # The lump under the block that the fore-end iron hooks to, and the bites the stub pins turn in.
    lump = bmesh.new()
    cuboid(lump, -0.0110, 0.0110, BREECH_Y + 0.012, HINGE.y + 0.004, -0.0300, -0.0240)
    take(lump, "lump", "blued", group=BARRELS, bevel=0.0010, segments=2)
    # The ejectors: the two halves of the breech face, split along a line through both chambers, stand a hair proud.
    ejector = bmesh.new()
    for sx in (-1, 1):
        cuboid(ejector, min(sx * 0.0008, sx * 0.0124), max(sx * 0.0008, sx * 0.0124), BREECH_Y - 0.0004, BREECH_Y + 0.0060, -0.0040, 0.0040)
    take(ejector, "ejectors", "bright", group=BARRELS, bevel=0.0004, segments=1)


def plate(bm, outline, x0, x1):
    """A flat piece cut to an outline of (y, z), from x0 to x1."""
    loft(bm, [[(x, y, z) for y, z in outline] for x in (x0, x1)], caps=True)


def action():
    body = bmesh.new()

    def ring(y, top, bottom):
        half = ACTION_HALF
        right = [(half - 0.0030, bottom), (half, bottom + 0.0030), (half, top - 0.0090)]
        right += [(half - 0.0060 + 0.0060 * math.cos(math.radians(a)), top - 0.0090 + 0.0090 * math.sin(math.radians(a))) for a in range(0, 91, 10)]
        points = right + [(0.0, top)] + [(-x, z) for x, z in reversed(right)]
        return [(x, y, z) for x, z in points]

    # From the head of the stock to the standing breech, the top falling away behind the fences.
    keys = [(ACTION_REAR, ACTION_TOP - 0.0050, ACTION_BOTTOM + 0.0030), (ACTION_REAR + 0.006, ACTION_TOP - 0.0030, ACTION_BOTTOM + 0.0010),
            (-0.012, ACTION_TOP - 0.0010, ACTION_BOTTOM), (0.010, ACTION_TOP, ACTION_BOTTOM), (BREECH_Y - 0.006, ACTION_TOP, ACTION_BOTTOM),
            (BREECH_Y, ACTION_TOP, ACTION_BOTTOM)]
    loft(body, [ring(*key) for key in keys])
    take(body, "action", "action", bevel=0.0007, segments=2, sharp=38.0, texels=1.3)

    # The walls either side of the barrels' block, round at the front about the hinge, and the floor between them.
    cheeks = bmesh.new()
    outline = [(BREECH_Y - 0.002, ACTION_BOTTOM), (CHEEK_FRONT - 0.010, ACTION_BOTTOM)]
    outline += [(HINGE.y + 0.0118 * math.cos(math.radians(a)), HINGE.z + 0.0118 * math.sin(math.radians(a))) for a in range(-60, 91, 15)]
    outline += [(HINGE.y - 0.010, -0.0040), (BREECH_Y - 0.002, -0.0040)]
    for sx in (-1, 1):
        plate(cheeks, outline, sx * 0.0128, sx * ACTION_HALF)
    floor = [(BREECH_Y - 0.002, ACTION_BOTTOM), (CHEEK_FRONT - 0.004, ACTION_BOTTOM), (CHEEK_FRONT, ACTION_BOTTOM + 0.004), (CHEEK_FRONT, -0.0310),
             (BREECH_Y - 0.002, -0.0310)]
    plate(cheeks, floor, -0.0130, 0.0130)
    take(cheeks, "action walls", "action", bevel=0.0007, segments=2, sharp=38.0, texels=1.3)

    pins = bmesh.new()
    for sx in (-1, 1):
        lathe(pins, [(0.0, 0.0), (0.0062, 0.0), (0.0062, 0.0004), (0.0056, 0.0009), (0.0, 0.0009)],
              Matrix.Translation((sx * ACTION_HALF, HINGE.y, HINGE.z)) @ Matrix.Rotation(math.radians(90 * sx), 4, "Y"), segments=28)
    take(pins, "hinge pins", "bright", sharp=30.0)

    # The firing pins' bushings in the standing breech, which the open gun shows.
    bushes = bmesh.new()
    for z in (UPPER_Z, LOWER_Z):
        lathe(bushes, [(0.0, 0.0), (0.0032, 0.0), (0.0032, 0.0005), (0.0012, 0.0006), (0.0012, 0.0002), (0.0, 0.0002)],
              Matrix.Translation((0.0, BREECH_Y, z)) @ Matrix.Rotation(math.radians(90), 4, "X"), segments=20)
    take(bushes, "bushings", "bright", sharp=30.0)

    # The top tang over the wrist, with the safety and barrel selector on it.
    tang = bmesh.new()
    loft(tang, [[(sx * w, y, z + dz) for sx, dz in ((-1, -0.0012), (1, -0.0012), (1, 0.0006), (-1, 0.0006))]
                for y, z, w in ((ACTION_REAR + 0.004, ACTION_TOP - 0.0047, 0.0080), (-0.055, ACTION_TOP - 0.0100, 0.0072), (-0.074, ACTION_TOP - 0.0150, 0.0050),
                                (-0.080, ACTION_TOP - 0.0168, 0.0020))])
    take(tang, "top tang", "action", bevel=0.0005, segments=1, sharp=35.0)
    safety = bmesh.new()
    cuboid(safety, -0.0042, 0.0042, -0.050, -0.040, ACTION_TOP - 0.0090, ACTION_TOP - 0.0055)
    take(safety, "safety", "ridged", bevel=0.0012, segments=2)

    # Under the action: the guard, deep enough for two triggers.
    guard = bmesh.new()
    path = along([(0.0330, -0.0440), (0.0360, -0.0540), (0.0335, -0.0660), (0.0245, -0.0775), (0.0090, -0.0835), (-0.0150, -0.0845), (-0.0380, -0.0810),
                  (-0.0520, -0.0725), (-0.0580, -0.0610), (-0.0590, -0.0480)], 48)
    sweep(guard, path, 0.0068, [0.0034 + 0.0016 * abs(n / 47 - 0.5) * 2 for n in range(48)])
    take(guard, "trigger guard", "action", bevel=0.0011, segments=3, sharp=50.0)
    triggers = bmesh.new()
    for shift in (0.0115, -0.0145):
        path = along([(0.0060 + shift, -0.0435), (0.0055 + shift, -0.0525), (0.0030 + shift, -0.0605), (-0.0005 + shift, -0.0675), (-0.0015 + shift, -0.0730),
                      (0.0005 + shift, -0.0760)], 20)
        sweep(triggers, path, 0.0034, [0.0062 - 0.0030 * n / 19 for n in range(20)])
    take(triggers, "triggers", "gold", bevel=0.0010, segments=3, sharp=50.0)

    lever = bmesh.new()
    outline = [(LEVER_Y + 0.0040, 0.0), (LEVER_Y - 0.0060, -0.0040), (LEVER_Y - 0.0280, -0.0055), (LEVER_Y - 0.0360, -0.0070), (LEVER_Y - 0.0395, -0.0045),
               (LEVER_Y - 0.0395, 0.0045), (LEVER_Y - 0.0360, 0.0070), (LEVER_Y - 0.0280, 0.0055), (LEVER_Y - 0.0060, 0.0040)]
    # Laid flat on the top: the outline is (y, x) here, and stood the 4 mm it is thick.
    loft(lever, [[(x, y, ACTION_TOP + dz - 0.0008 - 0.0058 * smooth((LEVER_Y - y) / 0.034)) for y, x in outline] for dz in (0.0, 0.0030)])
    take(lever, "top lever", "action", group=LEVER, bevel=0.0010, segments=2, sharp=40.0)
    spindle = bmesh.new()
    lathe(spindle, [(0.0, 0.0), (0.0045, 0.0), (0.0045, 0.0034), (0.0038, 0.0040), (0.0, 0.0040)], Matrix.Translation((0.0, LEVER_Y, ACTION_TOP - 0.0012)), segments=24)
    take(spindle, "lever spindle", "action", group=LEVER, sharp=35.0)


STOCK_KEYS = [
    # y, top, bottom, half width, how square, how narrow the comb is against the belly: from the action's back to the butt.
    (ACTION_REAR, ACTION_TOP - 0.0050, ACTION_BOTTOM + 0.0030, ACTION_HALF - 0.0004, 3.6, 0.98),
    (-0.0550, ACTION_TOP - 0.0110, -0.0470, 0.0192, 3.0, 0.95),
    (-0.0830, 0.0090, -0.0500, 0.0186, 2.6, 0.92),
    (-0.1150, -0.0030, -0.0560, 0.0180, 2.4, 0.90),
    (-0.1400, -0.0105, -0.0670, 0.0178, 2.2, 0.88),
    (-0.1600, -0.0140, -0.0830, 0.0182, 2.2, 0.86),
    (-0.1760, -0.0155, -0.0945, 0.0187, 2.3, 0.84),
    (-0.1880, -0.0162, -0.0995, 0.0190, 2.4, 0.82),
    (-0.2000, -0.0170, -0.1020, 0.0192, 2.4, 0.79),
    (-0.2150, -0.0185, -0.1045, 0.0195, 2.3, 0.75),
    (-0.2500, -0.0240, -0.1160, 0.0203, 2.3, 0.71),
    (-0.3000, -0.0320, -0.1400, 0.0211, 2.4, 0.71),
    (-0.3400, -0.0380, -0.1600, 0.0215, 2.6, 0.73),
    (-0.3600, -0.0400, -0.1660, 0.0215, 2.7, 0.74),
]
BUTT = STOCK_KEYS[-1][0]


def stock():
    wood = bmesh.new()
    loft(wood, [stock_ring(*station) for station in along(STOCK_KEYS, 72)])
    made(take(wood, "stock", "walnut", group=STOCK, bevel=0.0012, segments=2, sharp=50.0, texels=1.1), only=FULL)
    pad = (("pad plate", "black", ((0.0, 0.0), (-0.0030, 0.0))),
           ("pad", "rubber", ((-0.0030, 0.0003), (-0.0190, 0.0003), (-0.0225, -0.0008), (-0.0248, -0.0030), (-0.0258, -0.0065))))
    G.butt_pad(BUTT, STOCK_KEYS[-1][1:], group=STOCK, layers=pad)
    for name in ("pad plate", "pad"):
        made(bpy.data.objects[name], only=FULL)


# The sawn-off's grip, from the action's back down to its foot: each station a point on its
# front edge and one on its back edge in (y, z), the cross section's half width, how square it
# is and how narrow its back strap is against its front. It meets the action below its top,
# so that with the sights up the eye looks over the action's back and not into end grain.
GRIP_FRONT = [(ACTION_REAR, ACTION_BOTTOM + 0.0030), (-0.0480, -0.0470), (-0.0640, -0.0530), (-0.0680, -0.0640), (-0.0710, -0.0790), (-0.0790, -0.0950),
              (-0.0855, -0.1100), (-0.0920, -0.1235), (-0.0970, -0.1330)]
GRIP_BACK = [(ACTION_REAR, ACTION_TOP - 0.0080), (-0.0560, 0.0090), (-0.0820, -0.0030), (-0.1000, -0.0200), (-0.1100, -0.0420), (-0.1190, -0.0660),
             (-0.1260, -0.0880), (-0.1340, -0.1075), (-0.1470, -0.1240)]
GRIP_SECTION = [(ACTION_HALF - 0.0004, 3.6, 0.98), (0.0192, 3.2, 0.95), (0.0186, 2.8, 0.92), (0.0180, 2.5, 0.89), (0.0176, 2.3, 0.88), (0.0175, 2.3, 0.88),
                (0.0177, 2.3, 0.88), (0.0181, 2.4, 0.88), (0.0186, 2.6, 0.90)]


def grip_stub():
    keys = along([front + back + section for front, back, section in zip(GRIP_FRONT, GRIP_BACK, GRIP_SECTION)], 52)
    wood = bmesh.new()
    loft(wood, [grip_ring(s[0:2], s[2:4], *s[4:]) for s in keys])
    made(take(wood, "grip", "walnut", group=STOCK, bevel=0.0012, segments=2, sharp=50.0, texels=1.1), only=SAWN)
    last, before = keys[-1], keys[-2]
    down = Vector(((last[0] + last[2] - before[0] - before[2]) / 2, (last[1] + last[3] - before[1] - before[3]) / 2)).normalized()
    cap = bmesh.new()
    loft(cap, [grip_ring(last[0:2], last[2:4], *last[4:], grow=grow, out=(down.x * along_it, down.y * along_it))
               for along_it, grow in ((-0.0004, 0.0002), (0.0030, 0.0002), (0.0042, -0.0008), (0.0050, -0.0030))])
    made(take(cap, "sawn grip cap", "black", group=STOCK, sharp=40.0), only=SAWN)


def fore_ring(y, scale):
    """The fore-end cut across: a trough under the lower barrel, open at the top where the barrel lies in it."""
    outer = [(0.0118, LOWER_Z + 0.0060), (0.0150, LOWER_Z + 0.0020), (0.0170, LOWER_Z - 0.0040), (0.0178, LOWER_Z - 0.0110), (0.0175, LOWER_Z - 0.0170),
             (0.0162, LOWER_Z - 0.0230), (0.0138, LOWER_Z - 0.0275), (0.0100, LOWER_Z - 0.0305), (0.0052, LOWER_Z - 0.0320)]
    middle = Vector((0.0, LOWER_Z - 0.012))
    shaped = [((x - middle.x) * scale + middle.x, (z - middle.y) * scale + middle.y) for x, z in outer]
    low = (0.0, (LOWER_Z - 0.0325 - middle.y) * scale + middle.y)
    inner_r = barrel_radius(y) + 0.0004
    inner = [(inner_r * math.cos(math.radians(a)), LOWER_Z + inner_r * math.sin(math.radians(a))) for a in range(-160, -19, 7)]
    points = shaped + [low] + [(-x, z) for x, z in reversed(shaped)] + [(-inner_r * math.cos(math.radians(20)) - 0.0004, LOWER_Z + 0.0060)] + inner
    points += [(inner_r * math.cos(math.radians(20)) + 0.0004, LOWER_Z + 0.0060)]
    return [(x, y, z) for x, z in points]


def fore_end():
    wood = bmesh.new()
    keys = [(FORE_REAR, 0.82), (FORE_REAR + 0.0015, 0.91), (FORE_REAR + 0.005, 0.96), (FORE_REAR + 0.012, 0.99), (FORE_REAR + 0.03, 1.0)]
    keys += [(FORE_REAR + 0.03 + (FORE_FRONT - FORE_REAR - 0.075) * n / 10, 1.0 + 0.015 * math.sin(math.pi * n / 10)) for n in range(1, 10)]
    # A schnabel: the tip swells into a lip before it ends.
    keys += [(FORE_FRONT - 0.045, 1.0), (FORE_FRONT - 0.022, 1.0), (FORE_FRONT - 0.012, 1.05), (FORE_FRONT - 0.004, 1.03), (FORE_FRONT, 0.90)]
    loft(wood, [fore_ring(y, scale) for y, scale in keys])
    take(wood, "fore-end", "walnut", group=BARRELS, bevel=0.0009, segments=2, sharp=42.0, texels=1.1)
    iron = bmesh.new()
    loft(iron, [[(sx * w, y, LOWER_Z - 0.0330 + dz) for sx, dz in ((-1, -0.0018), (1, -0.0018), (1, 0.0010), (-1, 0.0010))]
                for y, w in ((FORE_REAR + 0.002, 0.0040), (FORE_REAR + 0.050, 0.0044), (FORE_REAR + 0.070, 0.0030), (FORE_REAR + 0.078, 0.0012))])
    take(iron, "fore-end iron", "action", group=BARRELS, bevel=0.0006, segments=2, sharp=35.0)
    latch = bmesh.new()
    cuboid(latch, -0.0028, 0.0028, FORE_REAR + 0.090, FORE_REAR + 0.112, LOWER_Z - 0.0352, LOWER_Z - 0.0328)
    take(latch, "fore-end latch", "action", group=BARRELS, bevel=0.0010, segments=2)


def coin(g):
    """Bright steel left in the white and polished, with two engraved lines round the panel on each side of the action."""
    lines = g.streaks()
    side = g.step(g.math("ABSOLUTE", g.nx), 0.55, 0.75)
    inset = g.panel(0.005, -0.009, 0.030, 0.0270, 0.010, 0.0)
    outer = g.math("SUBTRACT", 1.0, g.step(g.math("ABSOLUTE", g.add(inset, 0.0012)), 0.00012, 0.00032))
    inner = g.math("SUBTRACT", 1.0, g.step(g.math("ABSOLUTE", g.add(inset, 0.0030)), 0.00010, 0.00026))
    groove = g.times(g.math("MAXIMUM", outer, inner), side)
    colour = g.scaled(g.colour(0.60, 0.60, 0.615), g.add(1.0, g.times(lines, 0.35), g.times(groove, -0.55)))
    rough = g.add(0.20, g.times(lines, 0.30), g.times(groove, 0.30))
    return g.finish(colour, rough, 1.0, g.add(g.times(lines, 0.00002), g.times(groove, -0.00025)))


def gold(g):
    return g.finish(g.colour(0.80, 0.58, 0.22), 0.22, 1.0, 0.0)


def ridged(g):
    """The safety's top, steel cut across with ridges for the thumb."""
    colour, roughness, lines = G.steel(g, (0.030, 0.033, 0.044), 0.36)
    up = g.step(g.nz, 0.5, 0.8)
    return g.finish(colour, roughness, 1.0, g.times(up, g.teeth(g.times(g.y, 1.0 / 0.0012)), 0.0002))


def walnut(g):
    # A panel of fine diamonds on each side of the wrist and of the sawn-off's grip, and of the fore-end.
    wrist = (-0.135, -0.045, 0.034, 0.0160, 0.010, math.radians(30))
    stub = (-0.090, -0.080, 0.032, 0.0140, 0.008, math.radians(66))
    fore = ((FORE_REAR + FORE_FRONT) / 2 + 0.005, LOWER_Z - 0.0135, 0.072, 0.0095, 0.005, 0.0)
    return G.walnut(g, (wrist, stub, fore), G.CHECKER_PITCH)


G.MAKERS.update({"action": coin, "gold": gold, "ridged": ridged, "walnut": walnut, "spent hull": G.hull_at(SPENT_AT.z), "live hull": G.hull_at(LIVE_AT.z)})


def shells():
    """A fired shell, its mouth open where the crimp unfolded, and one not yet fired, its crimp shut: each a brass head with its rim, and a plastic body."""
    for name, at, group in (("spent", SPENT_AT, SPENT), ("live", LIVE_AT, LIVE)):
        to = along_y(at.x, at.y, at.z)
        head = bmesh.new()
        G.shell_head(head, to, 0.0)
        take(head, name + " shell head", "brass", group=group, sharp=30.0)
        body = bmesh.new()
        if group == SPENT:
            profile = [(0.0, 0.0155), (0.0100, 0.0155), (0.0100, SHELL_LONG - 0.0012), (0.0097, SHELL_LONG), (0.0090, SHELL_LONG), (0.0090, 0.0200), (0.0, 0.0200)]
        else:
            profile = [(0.0, 0.0155), (0.0100, 0.0155), (0.0100, SHELL_LONG - 0.0040), (0.0098, SHELL_LONG - 0.0018), (0.0090, SHELL_LONG - 0.0006),
                       (0.0060, SHELL_LONG - 0.0012), (0.0, SHELL_LONG - 0.0020)]
        lathe(body, profile, to, segments=28)
        take(body, name + " shell body", name + " hull", group=group, sharp=30.0)


def build():
    G.clear()
    shells()
    barrels()
    monobloc()
    action()
    stock()
    grip_stub()
    fore_end()
    G.count_triangles()


# Long and thin: its pictures are twice as wide as high.
G.WIDE = True
G.TUBES = {"upper barrel": (UPPER_Z, SAWN_Y, 0.0110), "lower barrel": (LOWER_Z, SAWN_Y, 0.0110)}


def variant(gun, keep):
    """A copy of the joined gun with only what one of the two has: keep is FULL or SAWN."""
    copy = gun.copy()
    copy.data = gun.data.copy()
    bpy.context.scene.collection.objects.link(copy)
    mesh = copy.data
    part = [0] * len(mesh.polygons)
    mesh.attributes["part"].data.foreach_get("value", part)
    bm = bmesh.new()
    bm.from_mesh(mesh)
    bm.faces.ensure_lookup_table()
    gone = []
    for face, which in zip(bm.faces, part):
        name = G.PART_NAMES[which]
        if ONLY.get(name, keep) != keep:
            gone.append(face)
        elif keep == SAWN and name in CUT and face.calc_center_median().y > SAWN_Y:
            gone.append(face)
    bmesh.ops.delete(bm, geom=gone, context="FACES")
    bm.to_mesh(mesh)
    bm.free()
    print("%s: %d of %d faces kept" % (keep, len(mesh.polygons), len(part)))
    return copy


def pack(gun, colour, surface):
    # The mod holds a weapon up as the game's own are made, the mirror image in x of how it is laid out here.
    mirror = Vector((-1.0, 1.0, 1.0))
    held = Vector((0.0, (FORE_REAR + FORE_FRONT) / 2, LOWER_Z - 0.020)) - GRIP
    port = (Vector((0.0, BREECH_Y, LOWER_Z)) - GRIP) * mirror
    moves = (HINGE.y - GRIP.y, HINGE.z - GRIP.z, OPENS, LEVER_Y - GRIP.y, LEVER_TURNS, BREECH_Y - GRIP.y, LOWER_Z - GRIP.z, UPPER_Z - GRIP.z, SHELL_LONG)
    for keep, muzzle_y, pictures in ((FULL, FULL_MUZZLE_Y, (colour, surface)), (SAWN, SAWN_Y, (None, None))):
        muzzle = Vector((0.0, muzzle_y, 0.0)) - GRIP
        packed = variant(gun, keep)
        G.pack(packed, keep, GRIP, *pictures, GROUPS, G.head_v5(muzzle, held, port, 0.0, SIGHT_LINE, EYE_BEHIND_GRIP, moves),
               moved=((SPENT, SPENT_AT - CHAMBER + GRIP), (LIVE, LIVE_AT - CHAMBER + GRIP)))
        bpy.data.objects.remove(packed)


VIEWS = {
    # name: where the camera is, what it looks at, focal length
    "right": ((2.15, 0.20, 0.10), (0.0, 0.20, -0.03), 60),
    "left": ((-2.15, 0.20, 0.10), (0.0, 0.20, -0.03), 60),
    "action": ((0.40, 0.02, 0.10), (0.0, 0.03, 0.0), 50),
    "action-left": ((-0.38, 0.24, 0.16), (0.0, 0.06, 0.0), 50),
    "under": ((0.30, 0.05, -0.18), (0.0, 0.12, -0.03), 45),
    "fore": ((0.42, 0.40, 0.14), (0.0, 0.20, -0.01), 50),
    "muzzle": ((0.22, FULL_MUZZLE_Y + 0.40, 0.16), (0.0, FULL_MUZZLE_Y - 0.16, 0.01), 55),
    "sawn-muzzle": ((0.22, SAWN_Y + 0.40, 0.16), (0.0, SAWN_Y - 0.16, 0.01), 55),
    "sights": ((0.0, -0.20, 0.0830), (0.0, 0.90, 0.0700), 40),
    "breech": ((0.25, -0.12, 0.22), (0.0, 0.06, 0.02), 50),
    "grip": ((0.45, -0.30, 0.02), (0.0, -0.09, -0.05), 50),
}


def opened():
    """Puts the barrels open and the top lever over, about the hinge and the lever's upright."""
    for obj in [o for o in bpy.data.objects if o.type == "MESH" and "group" in o]:
        if obj["group"] == BARRELS:
            obj.matrix_world = Matrix.Translation(HINGE) @ Matrix.Rotation(-OPENS, 4, "X") @ Matrix.Translation(-HINGE)
        elif obj["group"] == LEVER:
            pivot = Vector((0.0, LEVER_Y, 0.0))
            obj.matrix_world = Matrix.Translation(pivot) @ Matrix.Rotation(LEVER_TURNS, 4, "Z") @ Matrix.Translation(-pivot)


def main():
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if not args:
        raise SystemExit("say where the pictures from shotgun_textures.py are")
    G.load_pictures(args[0])
    build()
    if "--raw" in args:
        at = args.index("--raw")
        names = [name for name in args[at + 2:] if not name.startswith("--")]
        if "--open" in args:
            opened()
        keep = SAWN if "--sawn" in args else FULL
        for obj in list(G.PARTS):
            if ONLY.get(obj.name, keep) != keep:
                bpy.data.objects.remove(obj)
        G.render(args[at + 1], VIEWS, names, samples=24)
        return
    gun = G.unwrap(FULL)
    if "--stats" in args:
        G.stats(gun, os.path.join(args[args.index("--stats") + 1], "layout.png") if len(args) > args.index("--stats") + 1 else "layout.png")
        return
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
        gun.hide_render = True
        full = variant(gun, FULL)
        full.hide_render = False
        G.render(os.path.join(folder, FULL), VIEWS)
        # The same studio, camera and views again, for the sawn-off.
        full.hide_render = True
        variant(gun, SAWN).hide_render = False
        scene, camera = bpy.context.scene, bpy.context.scene.camera
        for name, (place, target, lens) in VIEWS.items():
            camera.location = place
            camera.data.lens = lens
            camera.rotation_euler = (Vector(target) - Vector(place)).to_track_quat("-Z", "Y").to_euler()
            scene.render.filepath = os.path.join(folder, SAWN, name + ".png")
            bpy.ops.render.render(write_still=True)


main()
