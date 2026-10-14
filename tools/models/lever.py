"""Builds the mod's own model of a lever action rifle, or of a lever action carbine, and packs it for the jar.

Run headless with Blender 4.4, from the repository root:

    blender --background --factory-startup --threads 1 --python tools/models/lever.py -- PICTURES [--carbine] [--raw DIR [VIEW ...]] [--renders DIR] [--no-pack] [--cpu] [--mesh] [--stats]

PICTURES is the folder tools/models/shotgun_textures.py wrote. The model is made here from
nothing, of no maker's pattern: a saddle gun with a flat sided receiver that throws its cases
out of the top, a round barrel over a full length magazine tube, a straight wrist and a loop
lever. The rifle (leverrifle) is a deer gun with a blued receiver, a red walnut stock with a rubber
pad and a barrel band round its fore-end; the carbine (levercarbine, --carbine) is a lighter
gun for revolver rounds, with a mottled case hardened receiver, a saddle ring, a crescent butt
plate of steel and a band holding the magazine under the muzzle. They are drawn for the
game's L94 Rifle and L92 Carbine, which share one mesh and so one size.

It writes NAME.mesh.gz under src/main/resources/models, and NAME.colour.gz and
NAME.surface.gz under src/textures/models for tools/textures/compress.py to pack, laid out as
gunsmith.py says, in version 5 of the file. Its parts, in order: what is fixed; the lever,
which swings down about its pin; the bolt, which slides back along the bore; the hammer, which
falls forward about its pin and is pushed back by the bolt; and a fired case, made well clear
of the gun and packed about its own middle, for the mod to throw out of the top. The numbers
after the parts are the lever's pin and the hammer's, each a place along and over the bore
from the grip, how far the hammer falls and how far the bolt pushes it back, in radians, and
the middle of the loading gate from the grip, as the file has it.
"""

import math
import os
import sys

import bmesh
import bpy
from mathutils import Matrix, Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gunsmith as G  # noqa: E402
from gunsmith import along, along_y, along_x, cuboid, cutter, lathe, loft, rod, screw, sweep, take  # noqa: E402
import longgun as L  # noqa: E402

CARBINE = "--carbine" in sys.argv
NAME = "levercarbine" if CARBINE else "leverrifle"
FIXED, LEVER, BOLT, HAMMER, CASE = range(5)

# Laid out with the bore along y through x = 0, z = 0 and the trigger at y = 0. GRIP is where
# the hand that holds the wrist is, which the finished model has as its origin: so placed that
# the muzzle is where the game's muzzle attachment is on its L92 mesh, (0, 0.3866, 0.0263),
# at the 0.4957 of the game's units to a metre its scopes and shotgun are drawn at.
GRIP = Vector((0.0, -0.064, -0.0531))
MUZZLE_Y = GRIP.y + 0.3866 / 0.4957
RECEIVER_REAR, RECEIVER_FRONT, RECEIVER_HALF = -0.048, 0.128, 0.0114
RECEIVER_TOP, RECEIVER_BOTTOM = 0.0138, -0.0318
BARREL_R = 0.0108 if not CARBINE else 0.0100
MUZZLE_R = 0.0086 if not CARBINE else 0.0084
TUBE_R = 0.0090 if not CARBINE else 0.0080
TUBE_Z = -(BARREL_R + TUBE_R + 0.0006)
FORE_FROM, FORE_TO = RECEIVER_FRONT + 0.0005, 0.385 if not CARBINE else 0.345
BUTT = -0.330
# The lever turns about a pin at the front of the receiver's foot, the hammer about one low in its rear.
LEVER_PIN = Vector((0.0, 0.112, -0.0250))
HAMMER_PIN = Vector((0.0, -0.036, -0.0120))
# The middle of the loading gate in the right side of the receiver, where the rounds are pushed in.
GATE = Vector((RECEIVER_HALF + 0.0006, 0.090, -0.0138))
# How far the hammer falls forward when the shot is taken, and how much further back than cocked the bolt pushes it.
HAMMER_FALLS, HAMMER_OVER = math.radians(34), math.radians(38)
# How far back the bolt slides, which opens the top of the receiver in front of it.
TRAVEL = 0.056
BOLT_REAR, BOLT_FRONT = -0.028, 0.060
CASE_AT = Vector((0.0, 0.30, 0.40))
PORT = Vector((0.0, 0.032, RECEIVER_TOP - 0.004))
# Over the bore: the bottom of the rear sight's notch and the top of the front blade.
SIGHT_LINE = 0.0225
# With the cheek on the comb of the straight stock.
EYE_BEHIND_GRIP = 0.14
# Where the hand under the fore-end holds it.
HELD = Vector((0.0, (FORE_FROM + FORE_TO) / 2 + 0.02, -0.020))


def barrel_radius(y):
    t = min(max((y - RECEIVER_FRONT) / (MUZZLE_Y - RECEIVER_FRONT), 0.0), 1.0)
    return BARREL_R + (MUZZLE_R - BARREL_R) * t ** 0.8


def barrel():
    bm = bmesh.new()
    profile = [(0.0, RECEIVER_FRONT - 0.012), (BARREL_R + 0.0012, RECEIVER_FRONT - 0.012), (BARREL_R + 0.0012, RECEIVER_FRONT + 0.004),
               (BARREL_R, RECEIVER_FRONT + 0.006)]
    profile += [(barrel_radius(y), y) for y in (0.20, 0.30, 0.40, 0.50, 0.60, 0.68) if y < MUZZLE_Y - 0.01]
    profile += [(MUZZLE_R, MUZZLE_Y - 0.0010), (MUZZLE_R - 0.0007, MUZZLE_Y), (0.0062, MUZZLE_Y), (0.0052, MUZZLE_Y - 0.0018),
                (0.0040 if not CARBINE else 0.0047, MUZZLE_Y - 0.0018), (0.0040 if not CARBINE else 0.0047, MUZZLE_Y - 0.09), (0.0, MUZZLE_Y - 0.09)]
    lathe(bm, profile, along_y(), segments=56)
    take(bm, "barrel", "blued", sharp=25.0)

    tube_end = MUZZLE_Y - 0.010
    tube = bmesh.new()
    lathe(tube, [(0.0, RECEIVER_FRONT - 0.004), (TUBE_R, RECEIVER_FRONT - 0.004), (TUBE_R, 0.40), (TUBE_R, tube_end - 0.006), (TUBE_R - 0.0006, tube_end),
                 (0.0, tube_end)], along_y(z=TUBE_Z), segments=40)
    take(tube, "magazine tube", "blued", sharp=25.0)
    # The plug in the tube's mouth and the screw through the band that holds it.
    plug = bmesh.new()
    lathe(plug, [(0.0, tube_end - 0.001), (TUBE_R - 0.0012, tube_end - 0.001), (TUBE_R - 0.0012, tube_end + 0.0022), (TUBE_R - 0.0028, tube_end + 0.0034),
                 (0.0, tube_end + 0.0036)], along_y(z=TUBE_Z), segments=28)
    take(plug, "magazine plug", "blued", sharp=35.0)

    # A band round barrel and tube a little short of the muzzle: on the rifle the fore-end's, on the carbine the magazine's own.
    for name, y, wide in (("muzzle band", tube_end - 0.030, 0.012),) + ((("fore-end band", FORE_TO - 0.006, 0.014),) if not CARBINE else ()):
        top, low = barrel_radius(y) + 0.0016, TUBE_R + 0.0016
        rings = []
        for at, grow in ((y - wide / 2, 0.0), (y - wide / 2 + 0.0008, 0.0008), (y + wide / 2 - 0.0008, 0.0008), (y + wide / 2, 0.0)):
            # Round the barrel above and round the tube below, straight down the sides between.
            ring = [((top + grow) * math.cos(a), at, (top + grow) * math.sin(a)) for a in [math.pi * n / 24 for n in range(25)]]
            ring += [((low + grow) * math.cos(a), at, TUBE_Z + (low + grow) * math.sin(a)) for a in [math.pi + math.pi * n / 24 for n in range(25)]]
            rings.append(ring)
        band = bmesh.new()
        loft(band, rings)
        take(band, name, "blued", bevel=0.0003, segments=1, sharp=40.0)
    band_screw = bmesh.new()
    lathe(band_screw, [(0.0, -0.0006), (0.0018, -0.0006), (0.0018, 0.0004), (0.0014, 0.0008), (0.0, 0.0008)],
          Matrix.Translation((TUBE_R + 0.0021, tube_end - 0.030, TUBE_Z * 0.6)) @ Matrix.Rotation(math.radians(90), 4, "Y"), segments=16)
    take(band_screw, "band screw", "blued", sharp=40.0)


def receiver_outline():
    """The receiver from the side: the flat top over the bolt, rounding at the back down to the upper tang, the
    lower tang under the wrist, the cut for the lever at the foot."""
    top = [(RECEIVER_FRONT, RECEIVER_TOP - 0.0022), (RECEIVER_FRONT - 0.0030, RECEIVER_TOP), (-0.012, RECEIVER_TOP)]
    # Behind the bolt's channel the top falls away in a long curve to the upper tang.
    rear = [(-0.012 - 0.050 * t, RECEIVER_TOP - (RECEIVER_TOP - 0.0012) * (0.5 - 0.5 * math.cos(math.pi * t))) for t in (0.12, 0.25, 0.38, 0.5, 0.62, 0.75, 0.88)]
    tang = [(-0.066, 0.0010), (-0.100, -0.0010), (-0.106, -0.0028), (-0.100, -0.0046), (-0.060, -0.0052)]
    back = [(-0.048, -0.0060), (-0.048, -0.0220), (-0.050, -0.0300)]
    lower = [(-0.072, -0.0352), (-0.098, -0.0410), (-0.102, -0.0428), (-0.096, -0.0446), (-0.066, -0.0398), (-0.036, -0.0362)]
    foot = [(-0.020, -0.0340), (0.040, RECEIVER_BOTTOM), (RECEIVER_FRONT - 0.010, RECEIVER_BOTTOM), (RECEIVER_FRONT, RECEIVER_BOTTOM + 0.006)]
    return top + rear + tang + back + lower + foot


def receiver():
    channel = bmesh.new()
    cuboid(channel, -0.0064, 0.0064, RECEIVER_REAR - 0.03, BOLT_FRONT + 0.0004, 0.0022, 0.03)
    channel_cut = cutter(channel)
    # Under the receiver, the slot the lever's arm rises into, and the window over it the trigger comes down through.
    slot = bmesh.new()
    cuboid(slot, -0.0050, 0.0050, -0.030, LEVER_PIN.y + 0.010, RECEIVER_BOTTOM - 0.01, RECEIVER_BOTTOM + 0.0080)
    slot_cut = cutter(slot)
    bore = bmesh.new()
    lathe(bore, [(0.0, RECEIVER_FRONT - 0.02), (BARREL_R + 0.0012, RECEIVER_FRONT - 0.02), (BARREL_R + 0.0012, RECEIVER_FRONT + 0.01), (0.0, RECEIVER_FRONT + 0.01)],
          along_y(), segments=40)
    bore_cut = cutter(bore)
    body = bmesh.new()
    G.prism(body, receiver_outline()[::-1], "x", -RECEIVER_HALF, RECEIVER_HALF)
    take(body, "receiver", "receiver", bevel=0.0012, segments=3, sharp=35.0, cut=(channel_cut, slot_cut, bore_cut), texels=1.3)
    for used in (channel_cut, slot_cut, bore_cut):
        bpy.data.objects.remove(used)

    # On the right side, the gate the rounds are pushed into the magazine through, sprung flat.
    gate = bmesh.new()
    G.prism(gate, [(0.068, -0.0230), (0.112, -0.0230), (0.116, -0.0190), (0.116, -0.0080), (0.112, -0.0045), (0.072, -0.0045), (0.064, -0.0110)][::-1], "x", -0.0004, 0.0004)
    bmesh.ops.translate(gate, verts=gate.verts[:], vec=(RECEIVER_HALF + 0.0002, 0.0, 0.0))
    take(gate, "loading gate", "bright" if CARBINE else "blued", bevel=0.0003, segments=1)

    pins = bmesh.new()
    for y, z in ((LEVER_PIN.y, LEVER_PIN.z), (HAMMER_PIN.y, HAMMER_PIN.z), (0.026, -0.024), (0.084, -0.008)):
        lathe(pins, [(0.0, -RECEIVER_HALF - 0.0004), (0.0022, -RECEIVER_HALF - 0.0004), (0.0026, -RECEIVER_HALF), (0.0026, RECEIVER_HALF),
                     (0.0022, RECEIVER_HALF + 0.0004), (0.0, RECEIVER_HALF + 0.0004)], along_x(0.0, y, z), segments=18)
    screw(pins, RECEIVER_HALF + 0.0002, 0.088, -0.016, 0.0030, Matrix.Rotation(math.radians(90), 4, "Y"))
    screw(pins, -RECEIVER_HALF - 0.0002, 0.088, -0.016, 0.0030, Matrix.Rotation(math.radians(-90), 4, "Y"))
    screw(pins, 0.0, -0.090, 0.0004, 0.0024, Matrix.Identity(4))
    take(pins, "pins", "blued", sharp=40.0)

    if CARBINE:
        # On the left of the receiver, a stud and the ring a saddle strap was tied to.
        ring = bmesh.new()
        lathe(ring, [(0.0, 0.0), (0.0036, 0.0), (0.0036, 0.0030), (0.0026, 0.0042), (0.0, 0.0042)], Matrix.Translation((-RECEIVER_HALF, -0.012, -0.010)) @
              Matrix.Rotation(math.radians(-90), 4, "Y"), segments=20)
        path = [(-RECEIVER_HALF - 0.0050, -0.012 + 0.012 * math.sin(a), -0.010 - 0.012 + 0.012 * math.cos(a)) for a in [2 * math.pi * n / 40 for n in range(41)]]
        rod(ring, path, [0.0014] * len(path), segments=10)
        take(ring, "saddle ring", "blued", sharp=40.0)


def bolt():
    body = bmesh.new()
    cuboid(body, -0.0060, 0.0060, BOLT_REAR, BOLT_FRONT, 0.0026, RECEIVER_TOP - 0.0003)
    take(body, "bolt", "bright", group=BOLT, bevel=0.0010, segments=2, texels=1.3)
    # The extractor along its top, and the pin through its tail the hammer strikes.
    claw = bmesh.new()
    cuboid(claw, -0.0014, 0.0014, BOLT_FRONT - 0.022, BOLT_FRONT + 0.0006, RECEIVER_TOP - 0.0005, RECEIVER_TOP + 0.0004)
    lathe(claw, [(0.0, BOLT_REAR - 0.0030), (0.0020, BOLT_REAR - 0.0030), (0.0020, BOLT_REAR + 0.001), (0.0, BOLT_REAR + 0.001)],
          along_y(z=0.0080), segments=16)
    take(claw, "extractor", "blued", group=BOLT, bevel=0.0002, segments=1)


def hammer():
    """The hammer, cocked: its body standing up and back from its pin behind the bolt, and a checkered spur for the thumb."""
    bm = bmesh.new()
    p = HAMMER_PIN
    # Cocked it leans well back, its spur no higher than the bolt's top by more than the thumb needs, under the line of sight.
    outline = [(p.y + 0.004, p.z - 0.004), (p.y + 0.0055, p.z + 0.006), (p.y + 0.002, p.z + 0.014), (p.y - 0.006, p.z + 0.020), (p.y - 0.015, p.z + 0.024),
               (p.y - 0.025, p.z + 0.027), (p.y - 0.032, p.z + 0.028), (p.y - 0.033, p.z + 0.0245), (p.y - 0.026, p.z + 0.021), (p.y - 0.017, p.z + 0.016),
               (p.y - 0.010, p.z + 0.008), (p.y - 0.005, p.z - 0.004)]
    G.prism(bm, outline[::-1], "x", -0.0040, 0.0040)
    take(bm, "hammer", "hammer", group=HAMMER, bevel=0.0009, segments=2, sharp=35.0, texels=1.3)


def lever_path():
    """The lever's bar from its pin, back under the receiver, down round the trigger and the fingers' loop, and up to its tip under the wrist."""
    pin = (LEVER_PIN.y, LEVER_PIN.z)
    keys = [pin, (0.090, RECEIVER_BOTTOM - 0.0020), (0.040, RECEIVER_BOTTOM - 0.0030), (0.026, RECEIVER_BOTTOM - 0.0060), (0.024, -0.052), (0.017, -0.068),
            (0.004, -0.080), (-0.018, -0.088), (-0.050, -0.090), (-0.078, -0.084), (-0.093, -0.070), (-0.096, -0.056), (-0.090, -0.046), (-0.078, -0.0430)]
    return along(keys, 64)


def lever():
    bm = bmesh.new()
    path = lever_path()
    sweep(bm, path, 0.0042, [0.0072 if n < 10 else 0.0050 for n in range(len(path))])
    # The flat that closes the loop at the front, between the trigger and the fingers.
    lathe(bm, [(0.0, -0.0050), (0.0040, -0.0050), (0.0045, -0.0045), (0.0045, 0.0045), (0.0040, 0.0050), (0.0, 0.0050)], along_x(0.0, LEVER_PIN.y, LEVER_PIN.z),
          segments=20)
    take(bm, "lever", "lever", group=LEVER, bevel=0.0009, segments=2, sharp=40.0, texels=1.2)


def stock_bottom(y):
    """The underside of the straight stock, from the wrist to the toe."""
    t = (y - (-0.075)) / (BUTT - (-0.075))
    return -0.0450 + (-0.168 + 0.0450) * t + 0.022 * math.sin(math.pi * t)


STOCK = [
    # y, top, bottom, half width, how square, how narrow the comb is against the belly
    (-0.0480, 0.0090, -0.0300, 0.0116, 3.2, 1.0),
    (-0.0600, 0.0035, -0.0380, 0.0136, 2.8, 0.97),
    (-0.0750, 0.0000, -0.0440, 0.0148, 2.5, 0.94),
    (-0.1000, -0.0040, stock_bottom(-0.100), 0.0154, 2.4, 0.90),
    (-0.1300, -0.0110, stock_bottom(-0.130), 0.0164, 2.4, 0.84),
    (-0.1700, -0.0220, stock_bottom(-0.170), 0.0178, 2.4, 0.78),
    (-0.2200, -0.0360, stock_bottom(-0.220), 0.0190, 2.5, 0.75),
    (-0.2800, -0.0500, stock_bottom(-0.280), 0.0198, 2.6, 0.75),
    (BUTT, -0.0590, stock_bottom(BUTT), 0.0202, 2.7, 0.76),
]


def stock():
    wood = bmesh.new()
    loft(wood, [G.stock_ring(*station) for station in along(STOCK, 72)])
    take(wood, "stock", "wood", bevel=0.0012, segments=2, sharp=50.0, texels=1.1)
    last = STOCK[-1]
    if CARBINE:
        # A crescent of steel over the butt, its horns curled round the heel and the toe.
        plate = bmesh.new()
        rings = []
        for back, grow in ((0.0, 0.0002), (-0.0030, 0.0002), (-0.0040, -0.0006)):
            ring = G.stock_ring(BUTT + back, *last[1:], grow=grow)
            middle = (last[1] + last[2]) / 2
            ring = [(x, y + 0.012 * (1 - ((z - middle) / ((last[1] - last[2]) / 2)) ** 2) - 0.006, z) for x, y, z in ring]
            rings.append(ring)
        loft(plate, rings)
        take(plate, "butt plate", "receiver", sharp=40.0)
    else:
        for name, material, layers in (("spacer", "black", ((0.0, 0.0002), (-0.0020, 0.0002))),
                                       ("pad", "rubber", ((-0.0020, 0.0003), (-0.0180, 0.0003), (-0.0215, -0.0008), (-0.0238, -0.0030), (-0.0248, -0.0065)))):
            part = bmesh.new()
            loft(part, [G.stock_ring(BUTT + back, *last[1:], grow=grow) for back, grow in layers])
            take(part, name, material, sharp=40.0)


def fore_end():
    """The wood round the barrel and the tube ahead of the receiver, from its butt against the receiver to its tip."""
    def ring(y, scale):
        points = []
        middle = TUBE_Z * 0.55
        for n in range(48):
            a = 2 * math.pi * (n + 0.5) / 48
            c, s = math.cos(a), math.sin(a)
            # Up to the middle of the barrel's side, rounded under the tube.
            half = (RECEIVER_HALF + 0.0004) * scale
            high = (0.0 - middle) * scale
            low = (middle - (TUBE_Z - TUBE_R - 0.0040)) * scale
            x = half * math.copysign(abs(c) ** (2 / 2.6), c)
            z = middle + (high if s > 0 else low) * math.copysign(abs(s) ** (2 / 2.6), s)
            points.append((x, y, z))
        return points
    keys = [(FORE_FROM, 1.0), (FORE_FROM + 0.01, 1.0), (FORE_TO - 0.06, 0.97), (FORE_TO - 0.012, 0.95), (FORE_TO - 0.004, 0.90), (FORE_TO, 0.82)]
    cut = bmesh.new()
    lathe(cut, [(0.0, FORE_FROM - 0.01), (BARREL_R + 0.0003, FORE_FROM - 0.01), (BARREL_R + 0.0003, FORE_TO + 0.01), (0.0, FORE_TO + 0.01)], along_y(), segments=40)
    lathe(cut, [(0.0, FORE_FROM - 0.01), (TUBE_R + 0.0003, FORE_FROM - 0.01), (TUBE_R + 0.0003, FORE_TO + 0.01), (0.0, FORE_TO + 0.01)], along_y(z=TUBE_Z), segments=40)
    cut_obj = cutter(cut)
    wood = bmesh.new()
    loft(wood, [ring(y, scale) for y, scale in along(keys, 24)])
    take(wood, "fore-end", "wood", bevel=0.0010, segments=2, sharp=50.0, cut=(cut_obj,), texels=1.1)
    bpy.data.objects.remove(cut_obj)
    if CARBINE:
        # The carbine's fore-end ends in a band of its own.
        cap = bmesh.new()
        loft(cap, [ring(y, scale) for y, scale in ((FORE_TO - 0.010, 1.03), (FORE_TO - 0.009, 1.05), (FORE_TO - 0.001, 1.05), (FORE_TO, 1.03))])
        take(cap, "fore-end cap", "blued", sharp=40.0)


def sights():
    # The rear sight: a spring leaf in a dovetail across the barrel, buckhorn ears either side of its notch, and a stepped elevator under it.
    at = 0.255
    r = barrel_radius(at)
    base = bmesh.new()
    loft(base, [[(-half, y, r - 0.0020), (half, y, r - 0.0020), (half * 0.85, y, top), (-half * 0.85, y, top)]
                for y, half, top in ((at - 0.030, 0.0026, r - 0.0008), (at - 0.022, 0.0050, r + 0.0012), (at + 0.010, 0.0050, r + 0.0016), (at + 0.013, 0.0042, r + 0.0006))])
    take(base, "rear sight spring", "blued", bevel=0.0004, segments=1)
    L.notch_leaf(at + 0.0038, r + 0.0006, SIGHT_LINE)
    elevator = bmesh.new()
    G.prism(elevator, [(at - 0.014, r + 0.0012), (at - 0.002, r + 0.0012), (at - 0.002, r + 0.0030), (at - 0.006, r + 0.0030), (at - 0.006, r + 0.0042),
                    (at - 0.014, r + 0.0022)][::-1], "x", -0.0040, 0.0040)
    take(elevator, "rear sight elevator", "blued", bevel=0.0002, segments=1)

    # The front sight: a ramp brazed to the barrel's top and a blade with a brass bead let into its face.
    front = MUZZLE_Y - 0.016
    L.ramp_and_blade(front, barrel_radius(front) + 0.0025, SIGHT_LINE)


def case():
    """A fired case: the rifle's a rimmed bottle, the carbine's a short straight walled one, open at the mouth."""
    L.case(CASE_AT, L.RIMMED_STRAIGHT if CARBINE else L.RIMMED_BOTTLE, CASE)


def checkered_hammer(g):
    """Blued, with fine lines cut across the spur's top for the thumb."""
    colour, roughness, lines = G.steel(g, (0.030, 0.033, 0.044), 0.30)
    spur = g.step(g.math("SUBTRACT", HAMMER_PIN.z + 0.0235, g.z), -0.002, 0.0)
    cut = g.times(g.math("SUBTRACT", 1.0, spur), g.teeth(g.times(g.y, 1.0 / 0.0010)))
    return g.finish(colour, roughness, 1.0, g.add(g.times(lines, 0.00002), g.times(cut, 0.00012)))


# The rifle's walnut is red, the carbine's paler and browner.
G.MAKERS.update({"wood": L.wood((1.30, 0.86, 0.66) if not CARBINE else (1.42, 1.18, 0.96)), "hammer": checkered_hammer,
                 "lever": L.hardened if CARBINE else G.blued, "receiver": L.hardened if CARBINE else (lambda g: G.blued(g, 0.27))})
# Long and thin: its pictures are twice as wide as high.
G.WIDE = True
G.TUBES = {"barrel": (0.0, 0.43, BARREL_R - 0.0008), "magazine tube": (TUBE_Z, 0.43, TUBE_R)}


def build():
    G.clear()
    case()
    barrel()
    receiver()
    bolt()
    hammer()
    lever()
    L.trigger(RECEIVER_BOTTOM + 0.004, -0.0605)
    stock()
    fore_end()
    sights()
    G.count_triangles()


def pack(gun, colour, surface):
    mirror = Vector((-1.0, 1.0, 1.0))
    gate = (GATE - GRIP) * mirror
    moves = [LEVER_PIN.y - GRIP.y, LEVER_PIN.z - GRIP.z, HAMMER_PIN.y - GRIP.y, HAMMER_PIN.z - GRIP.z, HAMMER_FALLS, HAMMER_OVER, *gate]
    head = G.head_v5(Vector((0.0, MUZZLE_Y, 0.0)) - GRIP, HELD - GRIP, (PORT - GRIP) * mirror, TRAVEL, SIGHT_LINE, EYE_BEHIND_GRIP, moves)
    G.pack(gun, NAME, GRIP, colour, surface, range(5), head, moved=((CASE, CASE_AT),))


VIEWS = {
    # name: where the camera is, what it looks at, focal length, all from the grip
    "right": ((2.0, 0.24, 0.10), (0.0, 0.24, -0.02), 60),
    "left": ((-2.0, 0.24, 0.10), (0.0, 0.24, -0.02), 60),
    "action": ((0.36, 0.0, 0.08), (0.0, 0.07, 0.03), 50),
    "action-left": ((-0.36, 0.10, 0.10), (0.0, 0.07, 0.03), 50),
    "lever": ((0.30, 0.02, -0.12), (0.0, 0.05, 0.0), 45),
    "fore": ((0.40, 0.62, 0.14), (0.0, 0.40, 0.03), 50),
    "muzzle": ((0.18, 1.10, 0.14), (0.0, 0.70, 0.05), 60),
    "sights": ((0.0, -EYE_BEHIND_GRIP, SIGHT_LINE - GRIP.z + 0.002), (0.0, 0.90, SIGHT_LINE - GRIP.z), 40),
    "hip": ((-0.13, -0.36, 0.20), (0.02, 0.50, 0.02), 22),
    "stock": ((0.46, -0.40, 0.12), (0.0, -0.14, -0.02), 50),
}


def to_grip():
    """Puts the gun, joined or in its parts, with its grip at the origin, as the views are placed."""
    for obj in [bpy.data.objects[NAME]] if NAME in bpy.data.objects else G.PARTS:
        obj.location = -GRIP


def main():
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if not args:
        raise SystemExit("say where the pictures from shotgun_textures.py are")
    G.load_pictures(args[0])
    build()
    if "--raw" in args:
        at = args.index("--raw")
        G.render(args[at + 1], VIEWS, [name for name in args[at + 2:] if name in VIEWS], samples=24, moved=to_grip)
        return
    gun = G.unwrap(NAME)
    if "--mesh" in args:
        pack(gun, None, None)
        return
    if "--stats" in args:
        G.stats(gun, os.path.join(".lab", NAME + "-layout.png"))
        return
    colour, surface = G.pictures_of(G.bake(gun))
    if "--no-pack" not in args:
        pack(gun, colour, surface)
    if "--renders" in args:
        folder = os.path.abspath(args[args.index("--renders") + 1])
        os.makedirs(folder, exist_ok=True)
        G.dress(gun, colour, surface, folder)
        G.render(folder, VIEWS, [name for name in args if name in VIEWS], moved=to_grip)


main()
