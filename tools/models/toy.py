"""Builds the mod's own model of a toy cap rifle and packs it for the jar.

Run headless with Blender 4.4, from the repository root:

    blender --background --factory-startup --threads 1 --python tools/models/toy.py -- PICTURES [--raw DIR [VIEW ...]] [--renders DIR] [--no-pack] [--cpu] [--mesh] [--stats]

PICTURES is the folder tools/models/shotgun_textures.py wrote. The model is made here from
nothing, of no maker's pattern: a child's cap firing rifle in moulded plastic, drawn for the
game's Toy Cap Rifle. A frame painted to look like steel holds a revolving cylinder of eight
chambers for the caps, under a hammer cocked by the thumb; a black plastic barrel with an
orange tip, as toys are marked, runs over a dummy magazine tube; and the stock and fore-end
are brown plastic moulded with a wood grain that only a child would take for wood. The seams
of the mould run along it.

It writes toy.mesh.gz under src/main/resources/models, and toy.colour.gz and toy.surface.gz
(1024 square) under src/textures/models for tools/textures/compress.py to pack, laid out as
gunsmith.py says, in version 5 of the file. Its parts, in order: what is fixed; the
cylinder, which turns about its own axis under the bore an eighth of a turn as the hammer
is cocked; and the hammer, which falls forward about its pin at the shot. The numbers after
the parts are how far under the bore the cylinder's axis is, the hammer's pin along and over
the bore from the grip, and how far the hammer falls, in radians.
"""

import math
import os
import sys

import bmesh
import bpy
from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gunsmith as G  # noqa: E402
from gunsmith import along, along_x, along_y, cuboid, cutter, lathe, loft, take  # noqa: E402
import longgun as L  # noqa: E402

NAME = "toy"
FIXED, CYLINDER, HAMMER = range(3)

# Laid out with the bore along y through x = 0, z = 0 and the trigger at y = 0. GRIP is where the
# hand that holds the wrist is, the finished model's origin: so placed that the muzzle is where the
# game's muzzle attachment is on its toy rifle, (0, 0.38, 0.033), at 0.4957 of the game's units to
# a metre, as the real guns are drawn.
GRIP = Vector((0.0, -0.064, -0.033 / 0.4957))
MUZZLE_Y = GRIP.y + 0.38 / 0.4957
FRAME_REAR, FRAME_FRONT, FRAME_HALF = -0.050, 0.084, 0.0120
FRAME_TOP, FRAME_BOTTOM = 0.0200, -0.0330
CYLINDER_FROM, CYLINDER_TO, CYLINDER_R = 0.014, 0.062, 0.0175
# The cylinder's axis is under the bore by as far as a chamber is from it: the chamber at its top is in line with the barrel.
CYLINDER_Z = -0.0105
HAMMER_PIN = Vector((0.0, -0.022, 0.0020))
HAMMER_FALLS = math.radians(40)
BARREL_R = 0.0080
BUTT = -0.315
FORE_FROM, FORE_TO = FRAME_FRONT + 0.0005, 0.310
SIGHT_LINE = 0.0200
EYE_BEHIND_GRIP = 0.14
HELD = Vector((0.0, (FORE_FROM + FORE_TO) / 2 + 0.02, -0.020))


def frame():
    window = bmesh.new()
    cuboid(window, -0.03, 0.03, CYLINDER_FROM - 0.0015, CYLINDER_TO + 0.0015, CYLINDER_Z - CYLINDER_R - 0.002, FRAME_TOP - 0.0045)
    window_cut = cutter(window)
    body = bmesh.new()
    outline = [(FRAME_FRONT, FRAME_TOP - 0.003), (FRAME_FRONT - 0.004, FRAME_TOP), (-0.010, FRAME_TOP), (-0.024, FRAME_TOP - 0.004), (-0.040, 0.008),
               (-0.075, 0.0030), (-0.080, 0.0010), (-0.075, -0.0010), (-0.048, -0.0030), (FRAME_REAR, -0.022), (-0.060, -0.040), (-0.064, -0.043),
               (-0.058, -0.045), (-0.030, FRAME_BOTTOM - 0.002), (FRAME_FRONT - 0.012, FRAME_BOTTOM), (FRAME_FRONT, FRAME_BOTTOM + 0.010)]
    G.prism(body, outline[::-1], "x", -FRAME_HALF, FRAME_HALF)
    take(body, "frame", "painted", bevel=0.0018, segments=3, sharp=35.0, cut=(window_cut,), texels=1.3)
    bpy.data.objects.remove(window_cut)
    # The pin the cylinder turns on, through the front of the frame, and the screws that hold the frame's halves.
    pins = bmesh.new()
    lathe(pins, [(0.0, CYLINDER_FROM - 0.003), (0.0030, CYLINDER_FROM - 0.003), (0.0030, CYLINDER_TO + 0.003), (0.0, CYLINDER_TO + 0.003)],
          along_y(z=CYLINDER_Z), segments=20)
    for y, z in ((HAMMER_PIN.y, HAMMER_PIN.z), (-0.006, -0.026), (0.072, -0.020)):
        lathe(pins, [(0.0, -FRAME_HALF - 0.0006), (0.0026, -FRAME_HALF - 0.0006), (0.0030, -FRAME_HALF), (0.0030, FRAME_HALF), (0.0026, FRAME_HALF + 0.0006),
                     (0.0, FRAME_HALF + 0.0006)], along_x(0.0, y, z), segments=18)
    take(pins, "pins", "bright", sharp=40.0)


def cylinder():
    bm = bmesh.new()
    lathe(bm, [(0.0, CYLINDER_FROM), (CYLINDER_R - 0.0020, CYLINDER_FROM), (CYLINDER_R, CYLINDER_FROM + 0.0025), (CYLINDER_R, CYLINDER_TO - 0.0025),
               (CYLINDER_R - 0.0020, CYLINDER_TO), (0.0, CYLINDER_TO)], along_y(z=CYLINDER_Z), segments=64)
    cuts = []
    # Eight chambers open at the back, where a cap goes, and the flutes between them along the outside.
    for n in range(8):
        a = math.pi / 2 + 2 * math.pi * n / 8
        c, s = math.cos(a), math.sin(a)
        mouth = bmesh.new()
        lathe(mouth, [(0.0, CYLINDER_FROM - 0.01), (0.0034, CYLINDER_FROM - 0.01), (0.0034, CYLINDER_FROM + 0.004), (0.0, CYLINDER_FROM + 0.004)],
              along_y(0.0105 * c, 0.0, CYLINDER_Z + 0.0105 * s), segments=16)
        cuts.append(cutter(mouth))
        b = a + math.pi / 8
        flute = bmesh.new()
        lathe(flute, [(0.0, CYLINDER_FROM + 0.009), (0.0028, CYLINDER_FROM + 0.011), (0.0028, CYLINDER_TO - 0.011), (0.0, CYLINDER_TO - 0.009)],
              along_y(0.0196 * math.cos(b), 0.0, CYLINDER_Z + 0.0196 * math.sin(b)), segments=16)
        cuts.append(cutter(flute))
    take(bm, "cylinder", "painted", group=CYLINDER, bevel=0.0006, segments=2, sharp=40.0, cut=cuts, texels=1.3)
    for used in cuts:
        bpy.data.objects.remove(used)


def hammer():
    p = HAMMER_PIN
    bm = bmesh.new()
    outline = [(p.y + 0.004, p.z - 0.004), (p.y + 0.0055, p.z + 0.006), (p.y + 0.002, p.z + 0.013), (p.y - 0.006, p.z + 0.019), (p.y - 0.014, p.z + 0.023),
               (p.y - 0.022, p.z + 0.025), (p.y - 0.024, p.z + 0.021), (p.y - 0.017, p.z + 0.016), (p.y - 0.010, p.z + 0.008), (p.y - 0.005, p.z - 0.004)]
    G.prism(bm, outline[::-1], "x", -0.0042, 0.0042)
    take(bm, "hammer", "painted", group=HAMMER, bevel=0.0010, segments=2, sharp=35.0, texels=1.3)


def barrel():
    bm = bmesh.new()
    lathe(bm, [(0.0, CYLINDER_TO + 0.0005), (BARREL_R + 0.0018, CYLINDER_TO + 0.0005), (BARREL_R + 0.0018, FRAME_FRONT + 0.004), (BARREL_R, FRAME_FRONT + 0.006),
               (BARREL_R, MUZZLE_Y - 0.020), (0.0, MUZZLE_Y - 0.020)], along_y(), segments=40)
    take(bm, "barrel", "toyblack", sharp=30.0)
    # The orange tip a toy is marked by, plugged so that nothing can be fired from it.
    tip = bmesh.new()
    lathe(tip, [(0.0, MUZZLE_Y - 0.0205), (BARREL_R + 0.0004, MUZZLE_Y - 0.0205), (BARREL_R + 0.0004, MUZZLE_Y - 0.0012), (BARREL_R - 0.0008, MUZZLE_Y),
                (0.0030, MUZZLE_Y), (0.0030, MUZZLE_Y - 0.004), (0.0, MUZZLE_Y - 0.004)], along_y(), segments=40)
    take(tip, "orange tip", "orange", sharp=40.0)
    tube_z = -(BARREL_R + 0.0065 + 0.0006)
    tube = bmesh.new()
    lathe(tube, [(0.0, FRAME_FRONT - 0.004), (0.0065, FRAME_FRONT - 0.004), (0.0065, MUZZLE_Y - 0.060), (0.0058, MUZZLE_Y - 0.056), (0.0, MUZZLE_Y - 0.056)],
          along_y(z=tube_z), segments=32)
    take(tube, "dummy tube", "toyblack", sharp=30.0)
    band = bmesh.new()
    y = MUZZLE_Y - 0.075
    rings = []
    for at, grow in ((y - 0.006, 0.0), (y - 0.005, 0.0007), (y + 0.005, 0.0007), (y + 0.006, 0.0)):
        ring = [((BARREL_R + 0.0014 + grow) * math.cos(a), at, (BARREL_R + 0.0014 + grow) * math.sin(a)) for a in [math.pi * n / 20 for n in range(21)]]
        ring += [((0.0079 + grow) * math.cos(a), at, tube_z + (0.0079 + grow) * math.sin(a)) for a in [math.pi + math.pi * n / 20 for n in range(21)]]
        rings.append(ring)
    loft(band, rings)
    take(band, "band", "painted", bevel=0.0003, segments=1, sharp=40.0)


STOCK = [
    # y, top, bottom, half width, how square, how narrow the comb is against the belly
    (FRAME_REAR, 0.0060, -0.0280, 0.0124, 3.0, 1.0),
    (-0.0620, 0.0020, -0.0400, 0.0140, 2.8, 0.97),
    (-0.0800, -0.0010, -0.0470, 0.0150, 2.5, 0.94),
    (-0.1100, -0.0060, -0.0620, 0.0160, 2.4, 0.90),
    (-0.1500, -0.0160, -0.0900, 0.0172, 2.4, 0.84),
    (-0.2000, -0.0290, -0.1180, 0.0184, 2.5, 0.78),
    (-0.2600, -0.0420, -0.1430, 0.0192, 2.6, 0.77),
    (BUTT, -0.0510, -0.1580, 0.0196, 2.7, 0.78),
]


def stock():
    wood = bmesh.new()
    loft(wood, [G.stock_ring(*station) for station in along(STOCK, 48)])
    take(wood, "stock", "toywood", bevel=0.0024, segments=3, sharp=50.0, texels=1.0)
    last = STOCK[-1]
    plate = bmesh.new()
    loft(plate, [G.stock_ring(BUTT + back, *last[1:], grow=grow) for back, grow in ((0.0005, 0.0004), (-0.0060, 0.0004), (-0.0075, -0.0010))])
    take(plate, "butt plate", "toyblack", sharp=40.0)

    def ring(y, scale):
        return [(0.0110 * scale * math.copysign(abs(math.cos(a)) ** 0.75, math.cos(a)), y,
                 -0.0040 + (0.0040 if math.sin(a) > 0 else 0.0200) * scale * math.copysign(abs(math.sin(a)) ** 0.75, math.sin(a)))
                for a in [2 * math.pi * (n + 0.5) / 40 for n in range(40)]]
    fore = bmesh.new()
    loft(fore, [ring(y, scale) for y, scale in ((FORE_FROM, 1.0), (FORE_FROM + 0.02, 1.04), (FORE_TO - 0.03, 1.0), (FORE_TO - 0.005, 0.92), (FORE_TO, 0.80))])
    take(fore, "fore-end", "toywood", bevel=0.0016, segments=2, sharp=50.0)


def sights():
    at = 0.200
    blade = bmesh.new()
    G.prism(blade, [(-0.0060, BARREL_R - 0.001), (0.0060, BARREL_R - 0.001), (0.0060, SIGHT_LINE + 0.0030), (0.0016, SIGHT_LINE + 0.0030), (0.0016, SIGHT_LINE),
                     (-0.0016, SIGHT_LINE), (-0.0016, SIGHT_LINE + 0.0030), (-0.0060, SIGHT_LINE + 0.0030)], "y", at - 0.0016, at + 0.0016)
    take(blade, "rear sight", "toyblack", bevel=0.0005, segments=1)
    front = MUZZLE_Y - 0.030
    post = bmesh.new()
    G.prism(post, [(front - 0.008, BARREL_R - 0.001), (front + 0.006, BARREL_R - 0.001), (front + 0.004, SIGHT_LINE), (front - 0.003, SIGHT_LINE)][::-1], "x",
            -0.0012, 0.0012)
    take(post, "front sight", "toyblack", bevel=0.0004, segments=1)


def seam(g):
    """How much of a mould's parting line there is at a place: a thin ridge down the gun's middle."""
    return g.math("SUBTRACT", 1.0, g.step(g.math("ABSOLUTE", g.x), 0.00025, 0.0006))


def painted(g):
    """Plastic sprayed with a silver grey paint to look like steel, its seam showing through, rubbed to the darker plastic at the edges."""
    rubbed = g.worn(0.7)
    fleck = g.noise(3200.0, 1.0)
    colour = g.mix(rubbed, g.scaled(g.colour(0.21, 0.21, 0.22), g.add(0.9, g.times(fleck, 0.25))), g.colour(0.06, 0.06, 0.065))
    return g.finish(colour, g.add(0.36, g.times(fleck, 0.12), g.times(rubbed, 0.25)), g.math("SUBTRACT", 0.55, g.times(rubbed, 0.55)),
                    g.add(g.times(seam(g), 0.00012), g.times(fleck, 0.00002)))


def toyblack(g):
    gloss = g.noise(900.0, 2.0)
    return g.finish(g.colour(0.018, 0.018, 0.019), g.add(0.22, g.times(gloss, 0.08)), 0.0, g.add(g.times(seam(g), 0.00012), g.times(gloss, 0.00001)))


def orange(g):
    return g.finish(g.colour(0.95, 0.24, 0.02), 0.30, 0.0, g.times(seam(g), 0.00012))


def toywood(g):
    """Brown plastic moulded with a grain: shallow straight ridges along the gun, a little waved, glossy, the seam down its middle."""
    wave = g.times(g.noise(30.0, 2.0), 0.004)
    grain = g.teeth(g.add(g.times(g.z, 1.0 / 0.0042), g.times(wave, 1.0 / 0.0042)))
    colour = g.scaled(g.colour(0.25, 0.085, 0.040), g.add(0.85, g.times(grain, 0.25)))
    return g.finish(colour, 0.32, 0.0, g.add(g.times(grain, 0.00010), g.times(seam(g), 0.00015)))


G.MAKERS.update({"painted": painted, "toyblack": toyblack, "orange": orange, "toywood": toywood})
# A toy is seen up close no more than a rifle is, and its plastic has little to show: its pictures are a quarter the size of a long gun's, square.
G.TEXTURE = 1024
G.TUBES = {"barrel": (0.0, 0.40, BARREL_R)}


def build():
    G.clear()
    frame()
    cylinder()
    hammer()
    barrel()
    L.guard(0.014, -0.050, FRAME_BOTTOM + 0.002, -0.060, material="painted")
    L.trigger(FRAME_BOTTOM + 0.004, -0.050, material="painted")
    stock()
    sights()
    G.count_triangles()


def pack(gun, colour, surface):
    mirror = Vector((-1.0, 1.0, 1.0))
    moves = [CYLINDER_Z - GRIP.z, HAMMER_PIN.y - GRIP.y, HAMMER_PIN.z - GRIP.z, HAMMER_FALLS]
    port = Vector((0.0, CYLINDER_FROM, 0.0))
    head = G.head_v5(Vector((0.0, MUZZLE_Y, 0.0)) - GRIP, HELD - GRIP, (port - GRIP) * mirror, 0.0, SIGHT_LINE, EYE_BEHIND_GRIP, moves)
    G.pack(gun, NAME, GRIP, colour, surface, range(3), head)


VIEWS = {
    "right": ((2.0, 0.24, 0.10), (0.0, 0.24, -0.02), 60),
    "left": ((-2.0, 0.24, 0.10), (0.0, 0.24, -0.02), 60),
    "action": ((0.30, 0.04, 0.10), (0.0, 0.08, 0.05), 50),
    "sights": ((0.0, -EYE_BEHIND_GRIP, SIGHT_LINE - GRIP.z + 0.002), (0.0, 0.90, SIGHT_LINE - GRIP.z), 40),
    "hip": ((-0.13, -0.36, 0.20), (0.02, 0.50, 0.02), 22),
    "muzzle": ((0.16, 1.00, 0.14), (0.0, 0.64, 0.06), 60),
}


def to_grip():
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
