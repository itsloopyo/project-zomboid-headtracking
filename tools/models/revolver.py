"""Builds the mod's own models of the revolvers, and of a toy cap pistol made like one, and packs them for the jar.

Run headless with Blender 4.4, from the repository root:

    blender --background --factory-startup --threads 1 --python tools/models/revolver.py -- PICTURES KIND [--renders DIR] [--raw DIR [VIEW ...]] [--open] [--cocked] [--no-pack] [--cpu] [--mesh] [--stats]

PICTURES is the folder tools/models/shotgun_textures.py wrote: the grips are its walnut and
rubber. KIND is one of the four below. Each is made here from nothing, of no maker's pattern
and with no maker's marks: a double action revolver whose cylinder swings out to the left on
a crane, with an ejector rod under the barrel in a shroud, a hammer with a spur, and a smooth
trigger. They are the same mechanism in different sizes:

    revolver   a service revolver: medium frame, six .357 chambers, a four inch barrel with
               a full length shroud and a rib, fixed sights, blued, walnut grips
    magnum     a large frame .44 revolver: six chambers, a five inch tapered barrel, an
               adjustable rear sight and a red insert in the front, stainless, walnut
               target grips with finger grooves
    snubnose   a small frame .38: five chambers, a short barrel with the ejector rod's head
               out in front of a short shroud, a sight groove in the top strap, blued,
               black rubber boot grips
    capgun     a toy: a chunky chromed cap pistol with gold hammer and trigger, cream
               plastic grips moulded with coarse diamonds and a ring at the butt, a solid
               barrel with an orange plug in its muzzle, and eight seats in its cylinder for
               caps, moulded seams and moulded screws

It writes KIND.mesh.gz under src/main/resources/models and KIND.colour.gz and
KIND.surface.gz, 1024 texels square, under src/textures/models, for
tools/textures/compress.py to pack, in the layout gunsmith.py says at version 5.

The model is made with its barrel along +y and its top +z, its right side +x, and the bore
through x = 0, z = 0, the cylinder's front face at y = 0. The game's handguns are made
upside down from its long guns, with their top towards -z, and the file has it so: it is
written mirrored in z, about the hand that holds its grip.

Its parts, in the file in this order:

    0  what is fixed: the frame, barrel, shroud, sights, grips, latch and screws
    1  the trigger, which turns back about its pin as it is pulled
    2  the hammer, which turns back about its pin as it is cocked
    3  the cylinder, which turns about its axis one chamber a shot, and swings out on the crane
    4  the crane: the yoke in front of the cylinder and the tube it swings on
    5  the ejector: the star in the back of the cylinder and the rod that pushes it, which
       turn with the cylinder and slide back out of it to throw out what is in the chambers
    6  a round, its rim's back face at the origin and its length along y, for each chamber
       that holds one: a cap in the cap gun's
    7  a fired case, laid out as the round is: a burnt cap in the cap gun's

The numbers after the parts' counts say how they move, each place from the grip and each
turn about an axis through it, as the file has them:

    hammer pin y, z; how far it turns about +x to be cocked, radians
    trigger pin y, z; how far it turns about +x when it is pulled
    the cylinder's axis x, z; how far it turns about +y with each shot, the next chamber coming
      under the hammer from where that turn back from the top would put it
    how many chambers it has
    the crane's pin x, z; how far it turns about +y to swing the cylinder out
    how far the ejector pushes back
    where the back of the chamber under the hammer is: x, y, z
"""

import math
import os
import sys

import bmesh
import bpy
from mathutils import Matrix, Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gunsmith as G  # noqa: E402
from gunsmith import along, cuboid, cutter, grip_ring, lathe, loft, rod, screw, sweep, take  # noqa: E402

FIXED, TRIGGER, HAMMER, CYLINDER, CRANE, EJECTOR, ROUND, CASE = range(8)

ARGS = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []


class Kind:
    """What sets one revolver apart from another: its sizes, in metres, and its finish."""

    def __init__(self, name, **sizes):
        self.name = name
        self.__dict__.update(sizes)


KINDS = {
    "revolver": Kind(
        "revolver", chambers=6, s=1.0, cyl_r=0.0186, cyl_len=0.0405, pitch=0.0119, chamber_r=0.00485, rim_r=0.0056, rim_t=0.0014,
        case_len=0.0330, bullet_len=0.0072, bullet="copper", barrel_r=(0.0098, 0.0096), bore_r=0.0045, rib=True, shroud="full", sights="fixed",
        metal="blued", parts_metal="blued", grip="walnut", toy=False, travel=0.0215,
        # How far the game's own model has its muzzle from its grip, at the size its handguns are drawn in true hands, and how far it is
        # over the line ahead of the grip: models_weapons.txt, muzzle (0, 0.1444, -0.0297) over ViewModel.HANDGUN_FITS of PER_METRE.
        muzzle_far=0.1788, muzzle_rise=math.atan2(0.0297, 0.1444),
        grip_front=[(0.0040, 0.0060), (0.0030, -0.0040), (-0.0005, -0.0160), (-0.0080, -0.0380), (-0.0160, -0.0600), (-0.0235, -0.0800), (-0.0265, -0.0880)],
        grip_back=[(-0.0330, 0.0120), (-0.0352, 0.0020), (-0.0400, -0.0200), (-0.0470, -0.0420), (-0.0545, -0.0640), (-0.0610, -0.0820), (-0.0630, -0.0870)],
        grip_section=[(0.0120, 3.0, 0.95), (0.0140, 2.8, 0.93), (0.0155, 2.6, 0.92), (0.0164, 2.5, 0.92), (0.0166, 2.6, 0.92), (0.0163, 2.8, 0.93),
                      (0.0158, 3.0, 0.95)]),
    "magnum": Kind(
        "magnum", chambers=6, s=1.12, cyl_r=0.0219, cyl_len=0.0440, pitch=0.0140, chamber_r=0.0058, rim_r=0.0066, rim_t=0.0015,
        case_len=0.0326, bullet_len=0.0082, bullet="soft", barrel_r=(0.0118, 0.0100), bore_r=0.0054, rib=True, shroud="half", sights="adjustable",
        metal="stainless", parts_metal="stainless", grip="walnut", toy=False, travel=0.0230,
        muzzle_far=0.2128, muzzle_rise=math.atan2(0.0271, 0.1734),
        grip_front=[(0.0040, 0.0060), (0.0035, -0.0040), (0.0005, -0.0140), (-0.0030, -0.0250), (-0.0085, -0.0330), (-0.0070, -0.0440),
                    (-0.0130, -0.0530), (-0.0125, -0.0640), (-0.0190, -0.0740), (-0.0240, -0.0870), (-0.0285, -0.0990)],
        grip_back=[(-0.0330, 0.0120), (-0.0355, 0.0020), (-0.0395, -0.0160), (-0.0430, -0.0300), (-0.0470, -0.0430), (-0.0510, -0.0560),
                   (-0.0550, -0.0690), (-0.0595, -0.0810), (-0.0640, -0.0920), (-0.0665, -0.0985), (-0.0680, -0.1015)],
        grip_section=[(0.0125, 3.0, 0.95), (0.0145, 2.8, 0.93), (0.0162, 2.5, 0.92), (0.0172, 2.4, 0.91), (0.0170, 2.4, 0.91), (0.0176, 2.4, 0.91),
                      (0.0174, 2.4, 0.91), (0.0178, 2.4, 0.91), (0.0176, 2.5, 0.92), (0.0170, 2.7, 0.93), (0.0164, 2.9, 0.95)]),
    "snubnose": Kind(
        "snubnose", chambers=5, s=0.88, cyl_r=0.0172, cyl_len=0.0400, pitch=0.0108, chamber_r=0.0048, rim_r=0.0055, rim_t=0.0013,
        case_len=0.0293, bullet_len=0.0095, bullet="lead", barrel_r=(0.0088, 0.0086), bore_r=0.0045, rib=False, shroud="short", sights="groove",
        metal="matte", parts_metal="blued", grip="rubber", toy=False, travel=0.0200,
        muzzle_far=0.1408, muzzle_rise=math.atan2(0.0271, 0.1129),
        grip_front=[(0.0040, 0.0060), (0.0035, -0.0040), (0.0000, -0.0120), (-0.0050, -0.0210), (-0.0040, -0.0290), (-0.0080, -0.0380),
                    (-0.0090, -0.0470), (-0.0140, -0.0560), (-0.0215, -0.0630), (-0.0300, -0.0670)],
        grip_back=[(-0.0310, 0.0120), (-0.0335, 0.0020), (-0.0365, -0.0170), (-0.0400, -0.0320), (-0.0430, -0.0450), (-0.0450, -0.0550),
                   (-0.0450, -0.0610), (-0.0425, -0.0660), (-0.0385, -0.0690), (-0.0340, -0.0705)],
        grip_section=[(0.0118, 3.0, 0.95), (0.0136, 2.6, 0.93), (0.0146, 2.4, 0.92), (0.0150, 2.3, 0.92), (0.0152, 2.3, 0.92), (0.0152, 2.3, 0.92),
                      (0.0150, 2.3, 0.92), (0.0146, 2.4, 0.93), (0.0138, 2.6, 0.95), (0.0128, 2.8, 0.97)]),
    "capgun": Kind(
        "capgun", chambers=8, s=1.04, cyl_r=0.0192, cyl_len=0.0400, pitch=0.0128, chamber_r=0.0032, rim_r=0.0034, rim_t=0.0010,
        case_len=0.0, bullet_len=0.0, bullet="", barrel_r=(0.0108, 0.0108), bore_r=0.0040, rib=True, shroud="full", sights="toy",
        metal="chrome", parts_metal="gold", grip="toyplastic", toy=True, travel=0.0035,
        muzzle_far=0.1803, muzzle_rise=math.atan2(0.028, 0.146),
        grip_front=[(0.0040, 0.0060), (0.0030, -0.0040), (-0.0010, -0.0170), (-0.0090, -0.0410), (-0.0170, -0.0640), (-0.0230, -0.0820), (-0.0250, -0.0880)],
        grip_back=[(-0.0330, 0.0120), (-0.0355, 0.0020), (-0.0410, -0.0200), (-0.0490, -0.0450), (-0.0570, -0.0670), (-0.0630, -0.0830), (-0.0645, -0.0870)],
        grip_section=[(0.0125, 2.6, 0.95), (0.0148, 2.4, 0.94), (0.0165, 2.3, 0.94), (0.0175, 2.3, 0.94), (0.0177, 2.3, 0.94), (0.0174, 2.4, 0.94),
                      (0.0168, 2.6, 0.95)]),
}

K = next((KINDS[name] for name in ARGS[1:] if name in KINDS), None)
if K is None:
    raise SystemExit("say which revolver: " + ", ".join(KINDS))
s = K.s
G.TEXTURE = 1024

# Laid out with the bore along y through x = 0, z = 0, the cylinder's front face at y = 0.
ZC = -K.pitch
CYL_REAR = -K.cyl_len
SHIELD = 0.0095 * s
R0 = CYL_REAR - SHIELD
TOP = ZC + K.cyl_r + 0.0006 + 0.0052 * s
WINDOW_BOTTOM = ZC - K.cyl_r - 0.0010
FB = WINDOW_BOTTOM - 0.0080 * s
W = 0.0105 * s
LUG = 0.0010
LUG_END = 0.0190 * s
YOKE_FRONT = 0.0060
# The barrel's shank is turned down where it meets the frame, which leaves the crane's yoke room under it.
SHANK_R = 0.0072
BARREL_LONG = {"revolver": 0.1045, "magnum": 0.1330, "snubnose": 0.0640, "capgun": 0.1050}[K.name]
MUZZLE_Y = 0.0003 + BARREL_LONG
# The hand that holds the grip: so far behind the muzzle and under the bore that the muzzle is as far ahead of it and as high over the
# line ahead of it as the game's own model has its muzzle, at the size handguns are drawn in true hands.
GRIP = Vector((0.0, MUZZLE_Y - K.muzzle_far * math.cos(K.muzzle_rise), -K.muzzle_far * math.sin(K.muzzle_rise)))
# The hammer's and the trigger's pins.
HAMMER_AT = (R0 - 0.0085 * s, ZC - 0.0030 * s)
TRIGGER_AT = (CYL_REAR + 0.0130 * s, FB + 0.0045 * s)
COCKED, PULLED = math.radians(42), math.radians(-22)
# The crane's pin, under the cylinder and to its left, and how far it turns to swing the cylinder out to the left.
CRANE_AT = (-0.0062 * s, ZC - K.cyl_r - 0.0016)
SWING = math.radians(-98)
ROD_R, HEAD_R = 0.0027 * s, 0.0037 * s
SHROUD_END = {"full": MUZZLE_Y - 0.0015, "half": LUG_END + 0.060, "short": LUG_END + 0.020}[K.shroud]
ROD_END = {"full": SHROUD_END - 0.0060, "half": SHROUD_END + 0.0045, "short": SHROUD_END + 0.0110}[K.shroud]
STAR_R = K.pitch * 0.93
# The rounds are made well clear of the gun, so that neither shades the other, and are packed about their rim's back face.
ROUND_AT = Vector((0.0, 0.30, 0.40))
CASE_AT = Vector((0.0, 0.40, 0.40))
# Over the bore: the bottom of the rear sight's notch, which the top of the front sight is lined up with.
SIGHT_LINE = {"fixed": TOP + 0.0002, "adjustable": TOP + 0.0034, "groove": TOP - 0.0006, "toy": TOP + 0.0010}[K.sights]
RIB_TOP = TOP - 0.0008
# With the arms out and the sights before the eye.
EYE_BEHIND_GRIP = 0.36


def scaled(points):
    """Places given about the back of the recoil shield and the bottom of the frame, at the frame's size."""
    return [(R0 + y * s, FB + z * s) for y, z in points]


def behind(points):
    """Places given about the back of the recoil shield and the top of the frame, at the frame's size."""
    return [(R0 + y * s, TOP + z * s) for y, z in points]


def box(x0, x1, y0, y1, z0, z1, bevel=0.0):
    bm = bmesh.new()
    cuboid(bm, x0, x1, y0, y1, z0, z1)
    return cutter(bm, bevel=bevel)


def tube_cut(radius, y0, y1, x=0.0, z=0.0, segments=32):
    bm = bmesh.new()
    lathe(bm, [(0.0, y0), (radius, y0), (radius, y1), (0.0, y1)], G.along_y(x=x, z=z), segments=segments)
    return cutter(bm)


def hull(circles, count=24):
    """The outline round some circles, (x, z, radius), counter-clockwise in x and z."""
    points = []
    for cx, cz, r in circles:
        points += [(cx + r * math.cos(2 * math.pi * i / count), cz + r * math.sin(2 * math.pi * i / count)) for i in range(count)]
    points = sorted(set(points))

    def turn(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    lower, upper = [], []
    for p in points:
        while len(lower) >= 2 and turn(lower[-2], lower[-1], p) <= 0:
            lower.pop()
        lower.append(p)
    for p in reversed(points):
        while len(upper) >= 2 and turn(upper[-2], upper[-1], p) <= 0:
            upper.pop()
        upper.append(p)
    return lower[:-1] + upper[:-1]


def chamber_places():
    """Where each chamber's middle is in x and z, from the top one round the way the cylinder turns."""
    return [(K.pitch * math.sin(2 * math.pi * j / K.chambers), ZC + K.pitch * math.cos(2 * math.pi * j / K.chambers)) for j in range(K.chambers)]


def done_with(*cutters):
    for used in cutters:
        bpy.data.objects.remove(used)


def top_edges():
    """Cutters that round the top strap and the backstrap off along their top edges."""
    cuts = []
    for side in (-1.0, 1.0):
        edge = bmesh.new()
        corners = [(side * (W + 0.001), TOP - 0.0048 * s), (side * (W + 0.001), TOP + 0.001), (side * (W - 0.0040 * s), TOP + 0.001)]
        loft(edge, [[(x, y, z) for x, z in corners] for y in (R0 - 0.04, LUG_END + 0.001)])
        cuts.append(cutter(edge))
    return cuts


def frame():
    # The frame from the muzzle end of the window back to the recoil shield, and under the window to the trigger.
    outline = [(LUG_END, TOP - 0.0010), (LUG_END - 0.0010, TOP), (R0 - 0.0005, TOP), (R0 - 0.0005, FB - 0.0040 * s)]
    outline += [(TRIGGER_AT[0] - 0.006 * s, FB - 0.0006), (0.0040 * s, FB - 0.0002), (0.0090 * s, FB + 0.0030), (0.0110 * s, FB + 0.0080),
                (0.0110 * s, -(K.barrel_r[0] + 0.0014)), (LUG_END, -(K.barrel_r[0] + 0.0014))]
    bm = bmesh.new()
    G.prism(bm, outline, "x", -W, W)
    cuts = [
        # The window the cylinder turns in, and the room for the crane's yoke ahead of it, under the barrel.
        box(-0.05, 0.05, CYL_REAR - 0.0006, LUG, WINDOW_BOTTOM, ZC + K.cyl_r + 0.0006),
        box(-0.05, 0.05, LUG - 0.0006, YOKE_FRONT + 0.0004, FB + 0.0018, -SHANK_R + 0.0004),
        # The slot the trigger stands in.
        box(-0.0050 * s, 0.0050 * s, TRIGGER_AT[0] - 0.0095 * s, TRIGGER_AT[0] + 0.0065 * s, FB - 0.02, TRIGGER_AT[1] + 0.0060 * s),
    ] + top_edges()
    if K.sights == "groove":
        # The top strap's groove, which the eye looks down to the front sight.
        cuts.append(box(-0.0012, 0.0012, R0 - 0.001, LUG_END + 0.001, SIGHT_LINE, TOP + 0.01, bevel=0.0004))
    take(bm, "frame", K.metal, bevel=0.0012 * s, segments=3, sharp=35.0, cut=cuts, texels=1.2)
    done_with(*cuts)

    # Behind the shield the frame falls away under the hammer's spur and comes round down the backstrap to the grip, rounded
    # across as a casting is: a loft of rounded sections from the hump's outside in, and a block inside it, its front buried in
    # the frame ahead of it.
    hump = [(R0 + 0.0040 * s, TOP - 0.0003)] + behind([(-0.0050, -0.0055), (-0.0120, -0.0105), (-0.0190, -0.0160), (-0.0250, -0.0230), (-0.0295, -0.0310),
                                                      (-0.0322, -0.0375), (-0.0335, -0.0425)])
    inside = Vector((R0 - 0.0040 * s, FB + 0.0080 * s))
    inner = [tuple(Vector(p) + (inside - Vector(p)).normalized() * 0.0200 * s) for p in hump]
    stations = along([q + p + (W, 3.0, 1.0) for p, q in zip(hump, inner)], 28)
    strap = bmesh.new()
    loft(strap, [grip_ring(st[0:2], st[2:4], *st[4:], count=40) for st in stations])
    slot = box(-0.0042 * s, 0.0042 * s, R0 - 0.0190 * s, R0 - 0.0002, HAMMER_AT[1] - 0.0090 * s, TOP + 0.02)
    edges = top_edges()
    take(strap, "backstrap", K.metal, bevel=0.0008, segments=2, sharp=40.0, cut=[slot] + edges, texels=1.2)
    done_with(slot, *edges)
    outline = [(R0 + 0.0060 * s, TOP - 0.0010)] + [((p[0] + q[0]) / 2, (p[1] + q[1]) / 2) for p, q in zip(hump, inner)]
    outline += scaled([(-0.0300, -0.0010), (0.0010, -0.0045), (0.0060, -0.0040)])
    outline += [(R0 + 0.0060 * s, FB - 0.0040 * s)]
    block = bmesh.new()
    G.prism(block, outline, "x", -W, W)
    cuts = [box(-0.0042 * s, 0.0042 * s, R0 - 0.0190 * s, R0 - 0.0002, HAMMER_AT[1] - 0.0090 * s, TOP + 0.02)]
    take(block, "frame back", K.metal, bevel=0.0012 * s, segments=3, sharp=35.0, cut=cuts, texels=1.2)
    done_with(*cuts)

    # The recoil shield stands out of the frame's sides behind the cylinder, round its axis.
    shield = bmesh.new()
    lathe(shield, [(0.0, R0 + 0.0008), (K.cyl_r * 0.86, R0 + 0.0008), (K.cyl_r * 0.93, R0 + 0.0040 * s), (K.cyl_r * 0.93, CYL_REAR - 0.0006),
                   (0.0, CYL_REAR - 0.0006)], G.along_y(z=ZC), segments=72)
    slot = box(-0.0042 * s, 0.0042 * s, R0 - 0.01, CYL_REAR, -0.0040, TOP + 0.02)
    take(shield, "recoil shield", K.metal, bevel=0.0008, segments=2, sharp=40.0, cut=(slot,))
    done_with(slot)

    bushing = bmesh.new()
    lathe(bushing, [(0.0, CYL_REAR - 0.0012), (0.0024, CYL_REAR - 0.0012), (0.0024, CYL_REAR - 0.0004), (0.0008, CYL_REAR - 0.0004), (0.0008, CYL_REAR - 0.0002),
                    (0.0, CYL_REAR - 0.0002)], G.along_y(), segments=20)
    take(bushing, "firing pin bushing", "bright" if not K.toy else K.parts_metal, sharp=40.0)


def barrel():
    bm = bmesh.new()
    rear, muzzle = K.barrel_r
    profile = [(0.0, 0.0003), (SHANK_R, 0.0003), (SHANK_R, YOKE_FRONT), (rear, YOKE_FRONT + 0.0008)]
    profile += [(rear + (muzzle - rear) * t, YOKE_FRONT + 0.0008 + (MUZZLE_Y - 0.0012 - YOKE_FRONT - 0.0008) * t) for t in (0.25, 0.5, 0.75, 1.0)]
    if K.toy:
        # Solid, with the orange plug standing in its muzzle.
        profile += [(muzzle - 0.0008, MUZZLE_Y), (K.bore_r + 0.0006, MUZZLE_Y), (K.bore_r, MUZZLE_Y - 0.0006), (0.0, MUZZLE_Y - 0.0006)]
    else:
        profile += [(muzzle - 0.0007, MUZZLE_Y), (K.bore_r + 0.0010, MUZZLE_Y), (K.bore_r, MUZZLE_Y - 0.0010), (K.bore_r, MUZZLE_Y - 0.05), (0.0, MUZZLE_Y - 0.05)]
    lathe(bm, profile, G.along_y(), segments=64)
    take(bm, "barrel", K.metal, sharp=25.0, texels=1.1)
    if K.toy:
        plug = bmesh.new()
        lathe(plug, [(0.0, MUZZLE_Y - 0.004), (K.bore_r - 0.0001, MUZZLE_Y - 0.004), (K.bore_r - 0.0001, MUZZLE_Y + 0.0022), (K.bore_r - 0.0008, MUZZLE_Y + 0.0032),
                     (0.0, MUZZLE_Y + 0.0034)], G.along_y(), segments=32)
        take(plug, "muzzle plug", "blaze", sharp=40.0)

    if K.rib:
        rib = bmesh.new()
        stations = [LUG_END - 0.0030 + (MUZZLE_Y - 0.0006 - LUG_END + 0.0030) * n / 8 for n in range(9)]
        loft(rib, [[(-0.0042 * s, y, 0.0), (0.0042 * s, y, 0.0), (0.0034 * s, y, RIB_TOP), (-0.0034 * s, y, RIB_TOP)] for y in stations])
        take(rib, "rib", K.metal, bevel=0.0006, segments=2, sharp=40.0)

    shroud = bmesh.new()
    rear_top = -(K.barrel_r[0] * 0.55)

    def section(y, shrink=1.0):
        half = 0.0058 * s * shrink
        points = [(half, y, rear_top), (half, y, ZC)]
        points += [(half * math.cos(math.radians(a)), y, ZC - half * math.sin(math.radians(a))) for a in range(15, 180, 15)]
        points += [(-half, y, ZC), (-half, y, rear_top)]
        return points

    keys = [YOKE_FRONT + 0.0004 + (SHROUD_END - 0.004 - YOKE_FRONT) * n / 6 for n in range(7)]
    loft(shroud, [section(y) for y in keys] + [section(SHROUD_END - 0.0015, 0.94), section(SHROUD_END, 0.80)])
    # The ejector rod lies in a channel along the shroud's middle, open towards where the crane swings it out.
    swung = rotated((0.0, ZC), CRANE_AT, SWING * 0.35)
    way = Vector((swung[0], swung[1] - ZC)).normalized()
    channel = tube_cut(HEAD_R + 0.0006, YOKE_FRONT - 0.001, ROD_END + 0.0010, z=ZC)
    slot = bmesh.new()
    across = Vector((-way.y, way.x)) * (ROD_R + 0.0006)
    far = way * 0.03
    corners = [(0.0 + across.x, ZC + across.y), (far.x + across.x, ZC + far.y + across.y), (far.x - across.x, ZC + far.y - across.y), (-across.x, ZC - across.y)]
    loft(slot, [[(x, y, z) for x, z in corners] for y in (YOKE_FRONT - 0.001, ROD_END + 0.001)])
    slot_cut = cutter(slot)
    take(shroud, "shroud", K.metal, bevel=0.0010 * s, segments=2, sharp=35.0, cut=(channel, slot_cut), texels=1.1)
    done_with(channel, slot_cut)


def rotated(point, about, angle):
    """A place in x and z turned about another by an angle about +y."""
    dx, dz = point[0] - about[0], point[1] - about[1]
    c, si = math.cos(angle), math.sin(angle)
    return (about[0] + dx * c + dz * si, about[1] - dx * si + dz * c)


def sights():
    front_end = MUZZLE_Y - (0.0045 if K.sights != "groove" else 0.0030)
    blade = bmesh.new()
    base = RIB_TOP if K.rib else K.barrel_r[1] - 0.0004
    keys = [(front_end - 0.028 * s, base - 0.0004), (front_end - 0.016 * s, base + (SIGHT_LINE - base) * 0.55), (front_end - 0.0035, SIGHT_LINE),
            (front_end, SIGHT_LINE), (front_end + 0.0004, base - 0.0004)]
    half = {"fixed": 0.0016, "adjustable": 0.0016, "groove": 0.0017, "toy": 0.0024}[K.sights]
    rings = []
    for y, z in keys:
        rings.append([(-half, y, base - 0.0025), (half, y, base - 0.0025), (half, y, z), (-half, y, z)])
    loft(blade, rings)
    take(blade, "front sight", "ramp" if not K.toy else K.metal, bevel=0.0003, segments=1, sharp=40.0)
    if K.sights == "adjustable":
        insert = bmesh.new()
        cuboid(insert, -half - 0.0001, half + 0.0001, front_end - 0.0070, front_end - 0.0038, SIGHT_LINE - 0.0030, SIGHT_LINE - 0.0008)
        take(insert, "sight insert", "insert", bevel=0.0002, segments=1)

    if K.sights == "groove":
        return
    rear = bmesh.new()
    if K.sights == "adjustable":
        back, front, half_wide, high = R0 - 0.0030, R0 + 0.0130, 0.0055, SIGHT_LINE + 0.0026
    elif K.sights == "toy":
        back, front, half_wide, high = R0 - 0.0010, R0 + 0.0075, 0.0042, SIGHT_LINE + 0.0022
    else:
        back, front, half_wide, high = R0 - 0.0005, R0 + 0.0060, 0.0034, SIGHT_LINE + 0.0024
    loft(rear, [[(-half_wide, y, TOP - 0.0020), (half_wide, y, TOP - 0.0020), (half_wide * 0.85, y, top), (-half_wide * 0.85, y, top)]
                for y, top in ((back, high - 0.0010), (back + 0.0015, high), (front - 0.0030, high), (front, TOP - 0.0004))])
    notch = box(-0.0013, 0.0013, back - 0.001, front + 0.001, SIGHT_LINE, high + 0.01)
    take(rear, "rear sight", "sight" if not K.toy else K.metal, bevel=0.0004, segments=2, sharp=40.0, cut=(notch,))
    done_with(notch)
    if K.sights == "adjustable":
        knobs = bmesh.new()
        screw(knobs, half_wide - 0.0002, (back + front) / 2, TOP + 0.0016, 0.0016, Matrix.Rotation(math.radians(90), 4, "Y"))
        screw(knobs, 0.0, front - 0.0045, high - 0.0002, 0.0014, Matrix.Identity(4))
        take(knobs, "sight screws", "bright", sharp=40.0)


def grip():
    front, back = scaled(K.grip_front), scaled(K.grip_back)
    sections = [(half * s, square, comb) for half, square, comb in K.grip_section]
    stations = along([f + b + c for f, b, c in zip(front, back, sections)], 32)
    wood = bmesh.new()
    loft(wood, [grip_ring(st[0:2], st[2:4], *st[4:], count=40) for st in stations])
    take(wood, "grip", K.grip, bevel=0.0012 * s, segments=2, sharp=45.0, texels=1.15)
    if K.toy:
        # A ring at the butt, for a cord, in a lug moulded on.
        foot = Vector(((front[-1][0] + back[-1][0]) / 2, (front[-1][1] + back[-1][1]) / 2))
        ring = bmesh.new()
        segments, round_it = 32, 12
        rings = []
        for i in range(segments + 1):
            a = 2 * math.pi * i / segments
            centre = Vector((0.0, foot.x + 0.0052 * math.sin(a), foot.y - 0.0060 - 0.0052 * math.cos(a)))
            out = Vector((0.0, math.sin(a), -math.cos(a)))
            rings.append([centre + out * (0.0011 * math.cos(2 * math.pi * k / round_it)) + Vector((0.0011 * math.sin(2 * math.pi * k / round_it), 0.0, 0.0))
                          for k in range(round_it)])
        loft(ring, rings[:-1] + [rings[0]], caps=False)
        take(ring, "lanyard ring", K.parts_metal, sharp=60.0)
        lug = bmesh.new()
        cuboid(lug, -0.0022, 0.0022, foot.x - 0.0040, foot.x + 0.0040, foot.y - 0.0016, foot.y + 0.0020)
        take(lug, "lanyard lug", K.metal, bevel=0.0010, segments=2)


def trigger_group():
    ty, tz = TRIGGER_AT
    guard = bmesh.new()
    keys = [(0.0265, 0.0010), (0.0290, -0.0080), (0.0245, -0.0175), (0.0125, -0.0240), (-0.0040, -0.0250), (-0.0140, -0.0210), (-0.0190, -0.0120),
            (R0 + 0.0040 * s - ty, -0.0045)]
    path = along([(ty + y * s, FB + z * s) if i < len(keys) - 1 else (R0 + 0.0040 * s, FB + z * s) for i, (y, z) in enumerate(keys)], 44)
    thick = 0.0042 * s * (1.2 if K.toy else 1.0)
    sweep(guard, path, 0.0040 * s * (1.25 if K.toy else 1.0), [thick + 0.0010 * abs(n / 43 - 0.5) * 2 for n in range(44)])
    take(guard, "trigger guard", K.metal, bevel=0.0012 * s, segments=3, sharp=50.0)

    trig = bmesh.new()
    path = along([(ty, tz), (ty + 0.0010 * s, FB - 0.0030 * s), (ty + 0.0008 * s, FB - 0.0100 * s), (ty - 0.0018 * s, FB - 0.0160 * s),
                  (ty - 0.0050 * s, FB - 0.0195 * s)], 24)
    sweep(trig, path, 0.0045 * s, [0.0068 * s - 0.0030 * s * n / 23 for n in range(24)])
    take(trig, "trigger", K.parts_metal, group=TRIGGER, bevel=0.0012 * s, segments=3, sharp=50.0)


def hammer():
    hy, hz = HAMMER_AT
    outline = [(R0 - 0.0003, -0.0060 * s), (R0 - 0.0003, 0.0035 * s), (R0 - 0.0015, TOP - 0.0012)]
    outline += behind([(-0.0060, 0.0018), (-0.0120, 0.0045), (-0.0175, 0.0060), (-0.0215, 0.0058), (-0.0230, 0.0042), (-0.0214, 0.0020), (-0.0178, -0.0002),
                       (-0.0168, -0.0050)])
    outline += [(R0 - 0.0165 * s, ZC + 0.0010 * s), (hy - 0.0060 * s, hz - 0.0010 * s), (hy - 0.0035 * s, hz - 0.0058 * s), (hy + 0.0020 * s, hz - 0.0065 * s),
                (R0 - 0.0025, -0.0085 * s)]
    bm = bmesh.new()
    G.prism(bm, [(y, z) for y, z in reversed(outline)], "x", -0.0037 * s, 0.0037 * s)
    take(bm, "hammer", K.parts_metal, group=HAMMER, bevel=0.0009 * s, segments=3, sharp=35.0, texels=1.2)
    nose = bmesh.new()
    lathe(nose, [(0.0, R0 + 0.0008), (0.0011, R0 + 0.0008), (0.0014, R0 - 0.0010), (0.0, R0 - 0.0010)], G.along_y(), segments=16)
    take(nose, "hammer nose", K.parts_metal, group=HAMMER, sharp=40.0)


def pins_and_screws():
    heads = bmesh.new()
    out = Matrix.Rotation(math.radians(90), 4, "Y")
    back = Matrix.Rotation(math.radians(-90), 4, "Y")
    for y, z in (HAMMER_AT, TRIGGER_AT):
        for x, to in ((W, out), (-W, back)):
            lathe(heads, [(0.0, -0.0004), (0.0019 * s, -0.0004), (0.0019 * s, 0.0002), (0.0015 * s, 0.0005), (0.0, 0.0005)], Matrix.Translation((x, y, z)) @ to,
                  segments=20)
    take(heads, "pins", "bright" if not K.toy else K.parts_metal, sharp=40.0)
    screws = bmesh.new()
    for y, z in scaled([(-0.0080, 0.0060), (-0.0210, 0.0300), (0.0020, 0.0360)]):
        if K.toy:
            # Moulded heads, as the halves of a toy are held.
            lathe(screws, [(0.0019 - 0.0019 * (1 - math.cos(math.radians(a))), 0.0010 * math.sin(math.radians(a))) for a in range(0, 91, 15)] + [(0.0, 0.0010)],
                  Matrix.Translation((W - 0.0002, y, z)) @ out, segments=20)
        else:
            screw(screws, W - 0.0002, y, z, 0.0021 * s, out)
    take(screws, "side plate screws", {"blued": "blued", "matte": "blued", "stainless": "stainless"}.get(K.metal, K.metal), sharp=40.0)

    latch = bmesh.new()
    cuboid(latch, -W - 0.0026 * s, -W + 0.0010, R0 - 0.0150 * s, R0 - 0.0035 * s, ZC - 0.0020 * s, ZC + 0.0045 * s)
    take(latch, "cylinder latch", "latch" if not K.toy else K.parts_metal, bevel=0.0012 * s, segments=3, sharp=40.0)


def cylinder():
    bm = bmesh.new()
    r = K.cyl_r
    lathe(bm, [(0.0, CYL_REAR), (r - 0.0012, CYL_REAR), (r, CYL_REAR + 0.0012), (r, -0.0026), (r - 0.0006, -0.0012), (r - 0.0020, 0.0), (0.0, 0.0)],
          G.along_y(z=ZC), segments=96)
    cuts = []
    for x, z in chamber_places():
        if K.toy:
            # Seats for the caps at the back, and the mouths of chambers moulded in the front.
            cuts.append(tube_cut(K.chamber_r, CYL_REAR - 0.001, CYL_REAR + 0.0030, x=x, z=z, segments=32))
            cuts.append(tube_cut(K.chamber_r * 1.25, -0.0030, 0.001, x=x, z=z, segments=32))
        else:
            cuts.append(tube_cut(K.chamber_r, CYL_REAR - 0.001, 0.001, x=x, z=z, segments=32))
            cuts.append(tube_cut(K.rim_r + 0.0002, CYL_REAR - 0.001, CYL_REAR + K.rim_t, x=x, z=z, segments=32))
    # The flutes between the chambers, and the notches the stop drops into, under each chamber.
    for j in range(K.chambers):
        a = 2 * math.pi * (j + 0.5) / K.chambers
        flute_r = (0.0050 if not K.toy else 0.0042) * s
        middle = (r + flute_r * 0.30)
        fx, fz = middle * math.sin(a), ZC + middle * math.cos(a)
        flute = bmesh.new()
        length = K.cyl_len * 0.58
        front = -K.cyl_len * 0.14
        profile = [(0.0, front - length)] + [(flute_r * math.sin(math.radians(t)), front - length + flute_r * (1 - math.cos(math.radians(t)))) for t in range(15, 91, 15)]
        profile += [(flute_r * math.sin(math.radians(t)), front - flute_r * (1 - math.cos(math.radians(t)))) for t in range(90, -1, -15)]
        lathe(flute, profile, G.along_y(x=fx, z=fz), segments=24)
        cuts.append(cutter(flute))
        b = 2 * math.pi * j / K.chambers
        notch = bmesh.new()
        cuboid(notch, -0.0013, 0.0013, CYL_REAR + 0.0065 * s, CYL_REAR + 0.0100 * s, -0.004, 0.0009)
        bmesh.ops.transform(notch, matrix=Matrix.Translation((0.0, 0.0, ZC)) @ Matrix.Rotation(b, 4, "Y") @ Matrix.Translation((0.0, 0.0, r)), verts=notch.verts)
        cuts.append(cutter(notch))
    # The recess the extractor star lies in.
    cuts.append(tube_cut(STAR_R + 0.0003, CYL_REAR - 0.001, CYL_REAR + K.rim_t + 0.0008, z=ZC, segments=48))
    take(bm, "cylinder", K.metal, group=CYLINDER, bevel=0.0005, segments=2, sharp=40.0, cut=cuts, texels=1.25)
    done_with(*cuts)


def crane():
    yoke = bmesh.new()
    outline = hull([(CRANE_AT[0], CRANE_AT[1], 0.0046 * s), (0.0, ZC, 0.0052 * s)])
    made = [[yoke.verts.new((x, y, z)) for x, z in outline] for y in (0.0004, YOKE_FRONT)]
    count = len(outline)
    yoke.faces.new(made[0][::-1])
    yoke.faces.new(made[1])
    for i in range(count):
        j = (i + 1) % count
        yoke.faces.new((made[0][i], made[0][j], made[1][j], made[1][i]))
    hole = tube_cut(ROD_R + 0.0003, -0.01, 0.02, z=ZC, segments=24)
    take(yoke, "crane yoke", K.metal, group=CRANE, bevel=0.0006, segments=2, sharp=40.0, cut=(hole,))
    done_with(hole)
    pin = bmesh.new()
    lathe(pin, [(0.0, CYL_REAR + 0.0030), (0.0040 * s, CYL_REAR + 0.0030), (0.0040 * s, YOKE_FRONT - 0.0004), (0.0, YOKE_FRONT - 0.0004)],
          G.along_y(x=CRANE_AT[0], z=CRANE_AT[1]), segments=32)
    take(pin, "crane tube", K.metal, group=CRANE, sharp=40.0)


def ejector():
    star = bmesh.new()
    lathe(star, [(0.0, CYL_REAR), (STAR_R - 0.0004, CYL_REAR), (STAR_R, CYL_REAR + 0.0004), (STAR_R, CYL_REAR + K.rim_t + 0.0006), (0.0, CYL_REAR + K.rim_t + 0.0006)],
          G.along_y(z=ZC), segments=64)
    holes = [tube_cut(K.chamber_r if not K.toy else K.chamber_r * 1.15, CYL_REAR - 0.001, CYL_REAR + 0.01, x=x, z=z, segments=32) for x, z in chamber_places()]
    take(star, "extractor star", "bright" if not K.toy else K.parts_metal, group=EJECTOR, bevel=0.0003, segments=1, sharp=40.0, cut=holes)
    done_with(*holes)
    bar = bmesh.new()
    head = 0.0085 * s
    lathe(bar, [(0.0, CYL_REAR + 0.0005), (ROD_R, CYL_REAR + 0.0005), (ROD_R, ROD_END - head), (HEAD_R, ROD_END - head + 0.0008), (HEAD_R, ROD_END - 0.0012), (HEAD_R - 0.0010, ROD_END),
                (0.0, ROD_END)], G.along_y(z=ZC), segments=24)
    take(bar, "ejector rod", "knurled" if not K.toy else K.parts_metal, group=EJECTOR, sharp=40.0)


def cartridge():
    """A round, made at ROUND_AT, and a fired case at CASE_AT: or the cap gun's cap, whole and burnt."""
    if K.toy:
        for at, name, material, group, burst in ((ROUND_AT, "cap", "cap", ROUND, False), (CASE_AT, "burnt cap", "burnt", CASE, True)):
            cap = bmesh.new()
            profile = [(0.0, 0.0), (K.chamber_r - 0.0002, 0.0), (K.chamber_r - 0.0002, 0.0006), (K.chamber_r - 0.0006, 0.0010)]
            profile += [(K.chamber_r * 0.62 * math.cos(math.radians(a)), 0.0010 + 0.0016 * math.sin(math.radians(a)) * (0.45 if burst else 1.0)) for a in range(0, 91, 15)]
            lathe(cap, profile[:-1] + [(0.0, profile[-1][1])], G.along_y(at.x, at.y, at.z), segments=20)
            take(cap, name, material, group=group, sharp=40.0)
        return
    case_r = K.chamber_r - 0.0001
    for at, group, fired in ((ROUND_AT, ROUND, False), (CASE_AT, CASE, True)):
        brass = bmesh.new()
        mouth = K.case_len
        profile = [(0.0, 0.0012), (0.0022, 0.0012), (0.0022, 0.0), (K.rim_r - 0.0004, 0.0), (K.rim_r, 0.0004), (K.rim_r, K.rim_t - 0.0003),
                   (K.rim_r - 0.0003, K.rim_t), (case_r - 0.0003, K.rim_t + 0.0003), (case_r, K.rim_t + 0.0012), (case_r - 0.00008, mouth)]
        if fired:
            profile += [(case_r - 0.0004, mouth), (case_r - 0.0004, K.rim_t + 0.0030), (0.0, K.rim_t + 0.0030)]
        else:
            profile += [(0.0, mouth)]
        lathe(brass, profile, G.along_y(at.x, at.y, at.z), segments=28)
        take(brass, "spent case" if fired else "case", "spent" if fired else "brass", group=group, sharp=30.0)
        primer = bmesh.new()
        dent = [(0.0, 0.0004), (0.0007, 0.0002)] if fired else [(0.0, -0.0001)]
        lathe(primer, dent + [(0.0019, -0.0001), (0.0019, 0.0010), (0.0, 0.0010)], G.along_y(at.x, at.y, at.z), segments=20)
        take(primer, "fired primer" if fired else "primer", "nickel", group=group, sharp=30.0)
        if fired:
            continue
        bullet = bmesh.new()
        r = case_r - 0.0002
        seated = mouth - 0.0040
        nose = K.bullet_len + 0.0040
        if K.bullet == "lead":
            shape = [(r * math.cos(math.radians(a)) ** 0.7, seated + 0.0050 + (nose - 0.0050) * math.sin(math.radians(a))) for a in range(0, 91, 10)]
        else:
            shape = [(r * (1 - 0.42 * t * t), seated + 0.0045 + (nose - 0.0045) * t) for t in (0.0, 0.2, 0.4, 0.6, 0.8, 1.0)]
        profile = [(0.0, seated), (r, seated), (r, seated + 0.0045)] + shape
        if profile[-1][0] > 1e-6:
            profile.append((0.0, profile[-1][1]))
        lathe(bullet, profile, G.along_y(at.x, at.y, at.z), segments=28)
        take(bullet, "bullet", "lead" if K.bullet == "lead" else "copper", group=ROUND, sharp=30.0)
        if K.bullet != "lead":
            tip = bmesh.new()
            front = profile[-2]
            lathe(tip, [(0.0, front[1] - 0.0006), (front[0] + 0.00005, front[1] - 0.0006), (front[0] * (0.55 if K.bullet == "copper" else 1.1), front[1] + (0.0002 if K.bullet == "copper" else 0.0016)),
                        (0.0, front[1] + (0.0 if K.bullet == "copper" else 0.0020))], G.along_y(at.x, at.y, at.z), segments=20)
            take(tip, "bullet tip", "lead", group=ROUND, sharp=40.0)


def blued(g):
    """A service revolver's bluing, worn through at its edges less than a long gun's carried in the field."""
    colour, roughness, lines = G.steel(g, (0.030, 0.033, 0.044), 0.28, wear=0.45)
    return g.finish(colour, roughness, 1.0, g.times(lines, 0.00002))


def stainless(g):
    lines = g.streaks()
    rubbed = g.worn(0.4)
    colour = g.mix(rubbed, g.scaled(g.colour(0.43, 0.43, 0.44), g.add(1.0, g.times(lines, 0.45))), g.colour(0.60, 0.60, 0.61))
    return g.finish(colour, g.add(0.30, g.times(lines, 0.35), g.times(rubbed, -0.08)), 1.0, g.times(lines, 0.00003))


def matte(g):
    colour, roughness, lines = G.steel(g, (0.034, 0.036, 0.042), 0.46, wear=0.7)
    return g.finish(colour, roughness, 1.0, g.times(lines, 0.00002))


def latch(g):
    """The cylinder latch's thumbpiece, cut across with grooves for the thumb."""
    colour, roughness, lines = G.steel(g, (0.030, 0.033, 0.044), 0.30)
    grooves = g.teeth(g.times(g.y, 1.0 / 0.0011))
    side = g.step(g.math("ABSOLUTE", g.nx), 0.6, 0.9)
    return g.finish(colour, roughness, 1.0, g.times(side, grooves, 0.00015))


def knurled(g):
    lines = g.streaks()
    round_it = g.math("ARCTAN2", g.x, g.math("SUBTRACT", g.z, ZC))
    head = g.step(g.y, ROD_END - 0.0085 * s + 0.0010, ROD_END - 0.0085 * s + 0.0016)
    one = g.teeth(g.add(g.times(round_it, 18 / math.pi), g.times(g.y, 1.0 / 0.0010)))
    other = g.teeth(g.math("SUBTRACT", g.times(round_it, 18 / math.pi), g.times(g.y, 1.0 / 0.0010)))
    diamond = g.math("MINIMUM", one, other)
    base = (0.43, 0.43, 0.44) if K.metal == "stainless" else (0.030, 0.033, 0.044)
    return g.finish(g.scaled(g.colour(*base), g.add(1.0, g.times(lines, 0.4))), g.add(0.30, g.times(head, 0.12)), 1.0, g.times(head, diamond, 0.00022))


def ramp(g):
    """The front sight: its rear face cut across so that it does not shine."""
    colour, roughness, lines = G.steel(g, (0.022, 0.024, 0.030) if K.metal != "stainless" else (0.40, 0.40, 0.41), 0.55, wear=0.4)
    back = g.step(g.math("SUBTRACT", 0.0, g.ny), 0.15, 0.5)
    return g.finish(colour, roughness, 1.0, g.times(back, g.teeth(g.times(g.z, 1.0 / 0.0008)), 0.00012))


def sight(g):
    """A sight's own steel: dull black, so that its notch is black against what is aimed at."""
    colour, roughness, lines = G.steel(g, (0.018, 0.019, 0.022), 0.66, wear=0.15)
    return g.finish(colour, roughness, 1.0, g.times(lines, 0.00002))


def insert(g):
    return g.finish(g.colour(0.62, 0.02, 0.015), 0.38, 0.0, 0.0)


def copper(g):
    return g.finish(g.colour(0.80, 0.42, 0.26), 0.26, 1.0, 0.0)


def lead(g):
    grain = g.noise(900.0, 2.0)
    return g.finish(g.scaled(g.colour(0.30, 0.30, 0.31), g.add(0.85, g.times(grain, 0.3))), 0.62, 0.55, g.times(grain, 0.00002))


def nickel(g):
    return g.finish(g.colour(0.66, 0.65, 0.62), 0.24, 1.0, 0.0)


def spent(g):
    """A fired case: brass, sooted towards its mouth."""
    soot = g.step(g.y, CASE_AT.y + K.case_len * 0.55, CASE_AT.y + K.case_len)
    colour = g.mix(g.times(soot, 0.85), g.colour(0.80, 0.58, 0.26), g.colour(0.10, 0.08, 0.06))
    return g.finish(colour, g.add(0.30, g.times(soot, 0.35)), g.math("SUBTRACT", 1.0, g.times(soot, 0.7)), 0.0)


def seam(g):
    """The line where a toy's mould's two halves met, down its middle: one on it, nought off it."""
    return g.math("SUBTRACT", 1.0, g.step(g.math("ABSOLUTE", g.x), 0.0002, 0.0005))


def grip_panel(share):
    """Where the hand holds the grip: its middle `share` of the way from its top to its foot, its length, how deep it is front to back
    half way down, and which way it runs in y and z, in radians."""
    front, back = scaled(K.grip_front), scaled(K.grip_back)
    top = Vector(((front[1][0] + back[1][0]) / 2, (front[1][1] + back[1][1]) / 2))
    foot = Vector(((front[-1][0] + back[-1][0]) / 2, (front[-1][1] + back[-1][1]) / 2))
    middle = top + (foot - top) * share
    deep = (Vector(front[len(front) // 2]) - Vector(back[len(back) // 2])).length
    return middle, (foot - top).length, deep, math.atan2(foot.y - top.y, foot.x - top.x)


def chrome(g):
    """A toy's plated zinc: a bright, smooth plate with the casting's ripples under it."""
    ripple = g.noise(180.0, 2.0)
    return g.finish(g.colour(0.78, 0.78, 0.80), g.add(0.08, g.times(ripple, 0.06)), 1.0, g.add(g.times(ripple, 0.00004), g.times(seam(g), 0.00012)))


def gold(g):
    ripple = g.noise(180.0, 2.0)
    return g.finish(g.colour(0.92, 0.70, 0.30), g.add(0.14, g.times(ripple, 0.06)), 1.0, g.times(ripple, 0.00004))


def toyplastic(g):
    """Cream plastic moulded with coarse diamonds, and the seam where the mould's halves met."""
    middle, long, deep, turn = grip_panel(0.52)
    nearest = g.panel(middle.x, middle.y, long * 0.36, 0.0110, 0.006, turn)
    side = g.step(g.math("ABSOLUTE", g.nx), 0.30, 0.55)
    cut = g.times(g.math("SUBTRACT", 1.0, g.step(nearest, -0.0004, 0.0004)), side)
    diamond = g.diamonds(0.0034)
    colour = g.scaled(g.colour(0.80, 0.74, 0.60), g.math("SUBTRACT", 1.0, g.times(cut, g.math("SUBTRACT", 0.14, g.times(diamond, 0.12)))))
    return g.finish(colour, g.add(0.34, g.times(cut, 0.08)), 0.0, g.add(g.times(cut, diamond, 0.00045), g.times(seam(g), 0.00015)))


def blaze(g):
    return g.finish(g.colour(0.95, 0.24, 0.02), 0.50, 0.0, 0.0)


def cap(g):
    return g.finish(g.colour(0.62, 0.04, 0.03), 0.42, 0.0, 0.0)


def burnt(g):
    grain = g.noise(3000.0, 2.0)
    return g.finish(g.scaled(g.colour(0.05, 0.035, 0.03), g.add(0.7, g.times(grain, 0.6))), 0.85, 0.0, g.times(grain, 0.00003))


def walnut(g):
    """The grips: walnut, with a panel of fine diamonds on each side where the hand holds it, the grain down the grip."""
    middle, long, deep, turn = grip_panel(0.50)
    return G.walnut(g, ((middle.x, middle.y, long * 0.36, deep * 0.32, 0.006, turn),), 0.0016, grain=turn)


G.MAKERS.update({"blued": blued, "stainless": stainless, "matte": matte, "latch": latch, "knurled": knurled, "ramp": ramp, "sight": sight, "insert": insert, "copper": copper,
                 "lead": lead, "nickel": nickel, "spent": spent, "chrome": chrome, "gold": gold, "toyplastic": toyplastic, "blaze": blaze, "cap": cap,
                 "burnt": burnt, "walnut": walnut})


def build():
    G.clear()
    cartridge()
    frame()
    barrel()
    sights()
    grip()
    trigger_group()
    hammer()
    pins_and_screws()
    cylinder()
    crane()
    ejector()
    G.count_triangles()
    print("the grip's hand at y %.4f z %.4f; muzzle y %.4f; frame back of the shield y %.4f, bottom z %.4f" % (GRIP.y, GRIP.z, MUZZLE_Y, R0, FB))


def in_file(place):
    """A place of the model's as the file has it: from the grip, mirrored in z."""
    return Vector((place[0] - GRIP.x, place[1] - GRIP.y, -(place[2] - GRIP.z)))


def pack(gun, colour, surface):
    muzzle = in_file((0.0, MUZZLE_Y, 0.0))
    nothing = Vector((0.0, 0.0, 0.0))
    hammer_at, trigger_at = in_file((0.0, *HAMMER_AT)), in_file((0.0, *TRIGGER_AT))
    axis, crane_at = in_file((0.0, 0.0, ZC)), in_file((CRANE_AT[0], 0.0, CRANE_AT[1]))
    chamber = in_file((0.0, CYL_REAR, 0.0))
    # Mirrored in z, a turn about +x or +y goes the other way.
    # It turns one chamber a shot anticlockwise seen from behind, which is about -y as it is made and +y in the file.
    moves = [hammer_at.y, hammer_at.z, -COCKED, trigger_at.y, trigger_at.z, -PULLED, axis.x, axis.z, 2 * math.pi / K.chambers, float(K.chambers),
             crane_at.x, crane_at.z, -SWING, K.travel, *chamber]
    G.pack(gun, K.name, GRIP, colour, surface, range(8), G.head_v5(muzzle, nothing, nothing, K.travel, SIGHT_LINE, EYE_BEHIND_GRIP, moves),
           moved=((ROUND, ROUND_AT), (CASE, CASE_AT)), mirror=(1.0, 1.0, -1.0))


def pose():
    """For a look at the parts as they are made: the cylinder swung out, or the hammer cocked and the trigger pulled."""
    for obj in G.PARTS:
        group = obj["group"]
        if "--open" in ARGS and group in (CYLINDER, CRANE, EJECTOR):
            obj.matrix_world = (Matrix.Translation((CRANE_AT[0], 0.0, CRANE_AT[1])) @ Matrix.Rotation(SWING, 4, "Y")
                                @ Matrix.Translation((-CRANE_AT[0], 0.0, -CRANE_AT[1])))
        if "--cocked" in ARGS and group in (HAMMER, TRIGGER):
            pin, angle = (HAMMER_AT, COCKED) if group == HAMMER else (TRIGGER_AT, PULLED)
            obj.matrix_world = Matrix.Translation((0.0, *pin)) @ Matrix.Rotation(angle, 4, "X") @ Matrix.Translation((0.0, -pin[0], -pin[1]))


MIDDLE = (MUZZLE_Y + R0 - 0.05) / 2
VIEWS = {
    # name: where the camera is, what it looks at, focal length
    "right": ((0.9, MIDDLE, 0.0), (0.0, MIDDLE, -0.03), 60),
    "left": ((-0.9, MIDDLE, 0.0), (0.0, MIDDLE, -0.03), 60),
    "shooter": ((0.10, R0 - 0.30, 0.10), (0.0, MUZZLE_Y - 0.05, -0.01), 50),
    "sights": ((0.0, R0 - 0.36, SIGHT_LINE + 0.0004), (0.0, MUZZLE_Y + 0.5, SIGHT_LINE), 40),
    "front": ((0.10, MUZZLE_Y + 0.22, 0.05), (0.0, MUZZLE_Y - 0.06, -0.01), 50),
    "rear": ((-0.16, R0 - 0.14, 0.07), (-0.02, CYL_REAR, ZC), 50),
    "under": ((0.16, MIDDLE, -0.24), (0.0, MIDDLE, -0.04), 50),
    "cylinder": ((0.16, -0.02, 0.06), (0.0, CYL_REAR / 2, ZC), 60),
}


def main():
    if not ARGS:
        raise SystemExit("say where the pictures from shotgun_textures.py are")
    G.load_pictures(ARGS[0])
    build()
    if "--raw" in ARGS:
        at = ARGS.index("--raw")
        pose()
        G.render(ARGS[at + 1], VIEWS, [name for name in ARGS[at + 2:] if not name.startswith("--")], size=(960, 540), samples=12)
        return
    gun = G.unwrap(K.name)
    if "--mesh" in ARGS:
        pack(gun, None, None)
        return
    if "--stats" in ARGS:
        G.stats(gun, ".lab/revolvers/layout-%s.png" % K.name)
        return
    colour, surface = G.pictures_of(G.bake(gun))
    if "--no-pack" not in ARGS:
        pack(gun, colour, surface)
    if "--renders" in ARGS:
        folder = os.path.abspath(ARGS[ARGS.index("--renders") + 1])
        os.makedirs(folder, exist_ok=True)
        G.dress(gun, colour, surface, folder)
        G.render(folder, VIEWS)


main()
