"""Builds the mod's own models of the game's three pistols and packs them for the jar.

Run headless with Blender 4.4, from the repository root:

    blender --background --factory-startup --threads 1 --python tools/models/pistol.py -- PICTURES KIND [--renders DIR] [--raw DIR [VIEW ...]] [--no-pack] [--cpu] [--mesh] [--stats]

PICTURES is the folder tools/models/shotgun_textures.py wrote: the forty-five's grips are its
walnut and the magnum's its rubber. KIND is the file's name and which pistol it is, each made
here from nothing and of no maker's pattern, as the game's own item says it is:

    pistol     Base.Pistol, a full size nine millimetre service pistol: double action, an
               exposed hammer, a slide open along its top over the barrel, levers on the slide
               that make it safe, a black finish and black chequered grips
    pistol2    Base.Pistol2, a forty-five of the old single action military pattern: a grip
               safety, a thumb safety, a spur hammer, a short barrel bushing, a parkerised
               finish and chequered walnut grips
    pistol3    Base.Pistol3, a big bore magnum autoloader: a heavy fixed barrel with a rib along
               its top and a short slide behind it, in stainless steel, with black rubber grips

Each writes, laid out as gunsmith.py says, KIND.mesh.gz under src/main/resources/models and
KIND.colour.gz and KIND.surface.gz under src/textures/models, a pistol's pictures a quarter of a
long gun's. Of the file's five parts a pistol has the frame and all that is fixed to it, the
slide, which runs back to work the action and is drawn back over the hammer, a fired case for
the mod to throw out of the port, and the magazine, which the mod draws in its well or not.

A pistol is made as the long guns are, its barrel along +y and its top +z, and packed as the
game's handguns are made, its top -z: the mirror of a long gun's, in z where theirs is in x.
"""

import math
import os
import sys

import bmesh
import bpy
from mathutils import Matrix, Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gunsmith as G  # noqa: E402

lathe, loft, cuboid, sweep, take, cutter, along, along_y, along_x = G.lathe, G.loft, G.cuboid, G.sweep, G.take, G.cutter, G.along, G.along_y, G.along_x

# The parts of the file's layout that a pistol has. The second is a pump's fore-end, which no pistol has.
FIXED, SLIDE, CASE, MAGAZINE = 0, 2, 3, 4
# The case is made well clear of the pistol, so that neither shades the other, and is packed about its own middle.
CASE_AT = Vector((0.0, 0.30, 0.40))
# The eye with the sights up, behind the hand that holds the grip: arms out and the head a little forward over them.
EYE_BEHIND_GRIP = 0.50


class Kind:
    """What one pistol is, laid out with the back of its slide at y = 0, the bore along y through x = 0, z = 0, and its
    right side, where the port is, at +x. Measures are in metres."""

    def __init__(self, **said):
        self.__dict__.update(said)

    def grip_line(self, z, inset=0.0):
        """Where the front and back of the grip are at a height, taken in by inset."""
        centre = self.grip_top_y - (self.grip_top_z - z) * math.tan(math.radians(self.rake))
        half = self.grip_deep / 2
        bulge = self.arch * math.sin(math.pi * min(max((self.grip_top_z - z) / (self.grip_top_z - self.grip_foot), 0.0), 1.0))
        return centre + half - inset, centre - half - bulge + inset


KINDS = {
    "pistol": Kind(
        item="Base.Pistol",
        # The game's model of it has its muzzle 0.1443 ahead of the hand and 0.0279 over it.
        game_muzzle=(0.1443, 0.0279),
        slide_long=0.2075, muzzle_y=0.2105,
        slide_half=0.0134, slide_top=0.0152, slide_bottom=-0.0148, slide_round=0.0068,
        frame_half=0.0106, dust_front=0.1745, dust_bottom=-0.0305,
        guard=[(0.0540, -0.0300), (0.0565, -0.0430), (0.0650, -0.0565), (0.0820, -0.0615), (0.1090, -0.0615), (0.1205, -0.0590),
               (0.1228, -0.0515), (0.1212, -0.0400), (0.1175, -0.0305)],
        trigger=[(0.0770, -0.0295), (0.0785, -0.0390), (0.0765, -0.0475), (0.0715, -0.0545), (0.0670, -0.0570)],
        grip_top_y=0.0245, grip_top_z=-0.0290, grip_foot=-0.1205, grip_deep=0.0565, rake=15.5, arch=0.0, grip_half=0.0106, panel_out=0.0172,
        port=(0.074, 0.114, -0.0045), open_top=(0.110, 0.191), serrations=(0.008, 0.044),
        barrel_r=0.0068, chamber=(0.070, 0.112, 0.0080), bore=0.0045,
        rear_sight=(0.006, 0.016, 0.0080, 0.0018, 0.0026), front_sight=(0.1955, 0.2030, 0.0016), sight_top=0.0205, dots=True,
        travel=0.046, case=[(0.0, -0.0095), (0.0040, -0.0095), (0.0049, -0.0092), (0.0049, -0.0083), (0.0042, -0.0079), (0.0042, -0.0073),
                            (0.0049, -0.0069), (0.0047, 0.0095), (0.0044, 0.0095), (0.0044, 0.0040), (0.0, 0.0040)],
        wrapped=False, finish="coat", frame_finish="black", barrel_finish="blued", grips="polygrip",
        hammer=[(0.0030, -0.0195), (0.0035, -0.0070), (-0.0010, 0.0010), (-0.0085, 0.0060), (-0.0145, 0.0065), (-0.0165, 0.0040),
                (-0.0150, 0.0010), (-0.0105, -0.0010), (-0.0055, -0.0075), (-0.0045, -0.0195)],
        hammer_half=0.0040,
        tang=[(-0.0050, -0.0148), (-0.0085, -0.0175), (-0.0100, -0.0235), (-0.0075, -0.0290)],
        magazine_half=0.0093, foot_plate=0.0045, foot_finish="polymer"),
    "pistol2": Kind(
        item="Base.Pistol2",
        game_muzzle=(0.1432, 0.0225),
        slide_long=0.2090, muzzle_y=0.2105,
        slide_half=0.0117, slide_top=0.0142, slide_bottom=-0.0150, slide_round=0.0052,
        frame_half=0.0102, dust_front=0.1540, dust_bottom=-0.0288,
        guard=[(0.0485, -0.0290), (0.0495, -0.0420), (0.0555, -0.0545), (0.0700, -0.0605), (0.0900, -0.0605), (0.1025, -0.0560),
               (0.1080, -0.0460), (0.1080, -0.0360), (0.1050, -0.0288)],
        trigger=[(0.0705, -0.0290), (0.0705, -0.0370), (0.0695, -0.0440), (0.0685, -0.0490)],
        grip_top_y=0.0215, grip_top_z=-0.0290, grip_foot=-0.1235, grip_deep=0.0545, rake=18.0, arch=0.0035, grip_half=0.0100, panel_out=0.0157,
        port=(0.052, 0.090, -0.0040), open_top=None, serrations=(0.008, 0.040),
        barrel_r=0.0072, chamber=(0.045, 0.095, 0.0083), bore=0.0058,
        rear_sight=(0.006, 0.0135, 0.0062, 0.0012, 0.0020), front_sight=(0.1950, 0.2010, 0.0012), sight_top=0.0188, dots=False,
        travel=0.048, case=[(0.0, -0.0114), (0.0045, -0.0114), (0.0060, -0.0111), (0.0060, -0.0102), (0.0051, -0.0097), (0.0051, -0.0091),
                            (0.0060, -0.0087), (0.0060, 0.0114), (0.0056, 0.0114), (0.0056, 0.0060), (0.0, 0.0060)],
        wrapped=False, finish="parkerised", frame_finish="parkerised", barrel_finish="bright", grips="walnut",
        hammer=[(0.0040, -0.0185), (0.0040, -0.0080), (0.0000, -0.0010), (-0.0070, 0.0040), (-0.0140, 0.0058), (-0.0172, 0.0046),
                (-0.0165, 0.0022), (-0.0110, 0.0005), (-0.0050, -0.0060), (-0.0040, -0.0185)],
        hammer_half=0.0034,
        tang=[(-0.0050, -0.0150), (-0.0060, -0.0160), (-0.0060, -0.0290)],
        magazine_half=0.0074, foot_plate=0.0012, foot_finish="blued"),
    "pistol3": Kind(
        item="Base.Pistol3",
        game_muzzle=(0.1466, 0.0236),
        slide_long=0.1100, muzzle_y=0.2620,
        slide_half=0.0150, slide_top=0.0185, slide_bottom=-0.0138, slide_round=0.0032,
        frame_half=0.0122, dust_front=0.2200, dust_bottom=-0.0330,
        guard=[(0.0660, -0.0330), (0.0680, -0.0460), (0.0760, -0.0600), (0.0940, -0.0660), (0.1300, -0.0660), (0.1420, -0.0630),
               (0.1455, -0.0550), (0.1440, -0.0430), (0.1405, -0.0330)],
        trigger=[(0.0960, -0.0330), (0.0975, -0.0430), (0.0955, -0.0515), (0.0905, -0.0585), (0.0860, -0.0610)],
        grip_top_y=0.0340, grip_top_z=-0.0330, grip_foot=-0.1340, grip_deep=0.0620, rake=17.0, arch=0.0, grip_half=0.0118, panel_out=0.0188,
        port=(0.056, 0.100, -0.0040), open_top=None, serrations=(0.008, 0.050),
        barrel_r=0.0, chamber=(0.045, 0.112, 0.0098), bore=0.0055,
        rear_sight=(0.004, 0.016, 0.0095, 0.0020, 0.0030), front_sight=(0.2470, 0.2550, 0.0018), sight_top=0.0258, dots=True,
        rib_top=0.0198,
        travel=0.052, case=[(0.0, -0.0165), (0.0050, -0.0165), (0.0066, -0.0162), (0.0066, -0.0150), (0.0058, -0.0147), (0.0057, 0.0165),
                            (0.0053, 0.0165), (0.0053, 0.0100), (0.0, 0.0100)],
        wrapped=True, finish="stainless", frame_finish="stainless", barrel_finish="stainless", grips="rubber",
        hammer=[(0.0030, -0.0180), (0.0035, -0.0060), (-0.0005, 0.0040), (-0.0080, 0.0095), (-0.0150, 0.0110), (-0.0185, 0.0085),
                (-0.0175, 0.0040), (-0.0120, 0.0015), (-0.0060, -0.0060), (-0.0050, -0.0180)],
        hammer_half=0.0045,
        tang=[(-0.0060, -0.0138), (-0.0110, -0.0170), (-0.0130, -0.0240), (-0.0090, -0.0330)],
        magazine_half=0.0090, foot_plate=0.0030, foot_finish="stainless"),
}

NAME = next((name for name in KINDS if name in sys.argv[sys.argv.index("--") + 1:]), None) if "--" in sys.argv else None
if NAME is None:
    raise SystemExit("say which pistol: " + ", ".join(KINDS))
K = KINDS[NAME]
# Where the hand that holds the grip is, which the file has as its origin: so far under the bore that the muzzle is as
# far above the line ahead of the hand as the muzzle of the game's own model of it is, which is how the mod points it.
GRIP_Y = 0.012
GRIP = Vector((0.0, GRIP_Y, -(K.muzzle_y - GRIP_Y) * K.game_muzzle[1] / K.game_muzzle[0]))
PORT = Vector((K.slide_half, (K.port[0] + K.port[1]) / 2, 0.004))


def both_sides(outline, near, far):
    """The x ranges of a piece on the right side and its mirror on the left."""
    return ((near, far), (-far, -near))


def section(half, top, bottom, round_top, round_bottom=0.0012, steps=7):
    """A slide or a barrel cut across: a box with its top corners rounded as far as round_top, in (x, z)."""
    points = []
    for corner, (cx, cz, r, start) in enumerate(((half - round_bottom, bottom + round_bottom, round_bottom, -90), (half - round_top, top - round_top, round_top, 0),
                                                 (-half + round_top, top - round_top, round_top, 90), (-half + round_bottom, bottom + round_bottom, round_bottom, 180))):
        for n in range(steps):
            angle = math.radians(start + 90 * n / (steps - 1))
            points.append((cx + r * math.cos(angle), cz + r * math.sin(angle)))
    return points


def disc(bm, centre, radius, thick, facing, segments=20, dome=0.0):
    """A round thing standing proud of a face: a screw's head, a button, a sight's dot. facing is "x", "-x", "y" or "-y"."""
    profile = [(0.0, 0.0), (radius, 0.0), (radius, thick * 0.6)]
    if dome:
        profile += [(radius * math.cos(math.radians(a)), thick * 0.6 + dome * math.sin(math.radians(a))) for a in (25, 50, 70)] + [(0.0, thick * 0.6 + dome)]
    else:
        profile += [(radius - thick * 0.4, thick), (0.0, thick)]
    turn = {"x": Matrix.Rotation(math.radians(90), 4, "Y"), "-x": Matrix.Rotation(math.radians(-90), 4, "Y"),
            "y": Matrix.Rotation(math.radians(-90), 4, "X"), "-y": Matrix.Rotation(math.radians(90), 4, "X")}[facing]
    lathe(bm, profile, Matrix.Translation(centre) @ turn, segments=segments)


def slide():
    """The slide: cut for the port, round the barrel where it shows, open along its top on the service pistol."""
    cuts = []
    port = bmesh.new()
    cuboid(port, 0.0015, 0.03, K.port[0], K.port[1], K.port[2], 0.03)
    cuts.append(cutter(port, bevel=0.0028))
    tunnel = bmesh.new()
    front = K.chamber[0] - 0.004
    lathe(tunnel, [(0.0, front), ((K.barrel_r or K.chamber[2]) + 0.0004, front), ((K.barrel_r or K.chamber[2]) + 0.0004, K.slide_long + 0.01), (0.0, K.slide_long + 0.01)],
          along_y(), segments=40)
    cuts.append(cutter(tunnel))
    if K.open_top:
        rear, end = K.open_top
        half = 0.0082
        outline = [(half * math.cos(math.radians(a)), end - half + half * math.sin(math.radians(a))) for a in range(0, 181, 15)]
        outline += [(-half * math.cos(math.radians(a)), rear + half - half * math.sin(math.radians(a))) for a in range(0, 181, 15)]
        top = bmesh.new()
        G.prism(top, outline, "z", 0.0030, 0.03)
        cuts.append(cutter(top))
    if K.item == "Base.Pistol2":
        # The bushing and the plug that holds the spring under it are let into the front of the slide.
        for z, r in ((0.0, 0.0108), (-0.0094, 0.0046)):
            hole = bmesh.new()
            lathe(hole, [(0.0, K.slide_long - 0.004), (r, K.slide_long - 0.004), (r, K.slide_long + 0.01), (0.0, K.slide_long + 0.01)], along_y(z=z), segments=40)
            cuts.append(cutter(hole))
    body = bmesh.new()
    ring = section(K.slide_half, K.slide_top, K.slide_bottom, K.slide_round)
    loft(body, [[(x, y, z) for x, z in ring] for y in (0.0, K.slide_long)])
    take(body, "slide", NAME + " slide", group=SLIDE, bevel=0.0008, segments=2, sharp=35.0, cut=cuts, texels=1.2)
    for used in cuts:
        bpy.data.objects.remove(used)

    # The extractor's long claw along the right of the port, and the firing pin's end in the back of the slide.
    claw = bmesh.new()
    cuboid(claw, K.slide_half - 0.0008, K.slide_half + 0.0003, K.port[0] - 0.014, K.port[0] + 0.003, 0.0012, 0.0048)
    take(claw, "extractor", K.finish, group=SLIDE, bevel=0.0003, segments=1)
    pin = bmesh.new()
    disc(pin, (0.0, -0.0004, 0.0), 0.0022, 0.0006, "-y", segments=16)
    take(pin, "firing pin stop", "bright", group=SLIDE, sharp=40.0)


def levers_on_slide():
    """The levers on each side of the back of the slide that make it safe, as the service pistol and the magnum have."""
    at_y, at_z, r = 0.022, 0.0040, 0.0050
    outline = [(at_y + r * math.cos(math.radians(a)), at_z + r * math.sin(math.radians(a))) for a in range(-90, 181, 18)]
    outline += [(at_y - 0.0095, at_z + 0.0020), (at_y - 0.0115, at_z - 0.0005), (at_y - 0.0070, at_z - 0.0040)]
    bm = bmesh.new()
    for low, high in both_sides(outline, K.slide_half - 0.0004, K.slide_half + 0.0030):
        G.prism(bm, outline, "x", low, high)
    take(bm, "safety levers", K.finish, group=SLIDE, bevel=0.0007, segments=2, sharp=40.0)
    # A red mark on each, showing when it is off safe.
    mark = bmesh.new()
    for side in (1, -1):
        disc(mark, (side * (K.slide_half + 0.0030), at_y + 0.0012, at_z + 0.0012), 0.0012, 0.0002, "x" if side > 0 else "-x", segments=12)
    take(mark, "safety marks", "red", group=SLIDE, sharp=50.0)


def sights():
    rear_y0, rear_y1, half, notch, deep = K.rear_sight
    base = K.slide_top - 0.0012
    outline = [(-half, base), (half, base), (half * 0.92, K.sight_top), (notch, K.sight_top), (notch, K.sight_top - deep), (-notch, K.sight_top - deep),
               (-notch, K.sight_top), (-half * 0.92, K.sight_top)]
    rear = bmesh.new()
    G.prism(rear, outline, "y", rear_y0, rear_y1)
    take(rear, "rear sight", "sight", group=SLIDE, bevel=0.0003, segments=1, sharp=40.0)
    front_y0, front_y1, blade = K.front_sight
    front_base = (K.slide_top if K.barrel_r else K.rib_top) - 0.0012
    outline = [(front_y0, front_base), (front_y1 + 0.003, front_base), (front_y1 + 0.003, front_base + 0.0018), (front_y1, K.sight_top), (front_y0, K.sight_top)]
    front = bmesh.new()
    G.prism(front, outline, "x", -blade, blade)
    # The service pistol's front sight is on its slide, the magnum's on its barrel's rib, which does not move.
    take(front, "front sight", "sight", group=SLIDE if K.barrel_r else FIXED, bevel=0.0003, segments=1, sharp=40.0)
    if K.dots:
        dots = bmesh.new()
        for x in (-(notch + (half - notch) / 2), notch + (half - notch) / 2):
            disc(dots, (x, rear_y0, K.sight_top - deep - 0.0002), 0.0010, 0.0002, "-y", segments=12)
        take(dots, "rear dots", "dot", group=SLIDE, sharp=50.0)
        dot = bmesh.new()
        disc(dot, (0.0, front_y0, K.sight_top - 0.0018), 0.0010, 0.0002, "-y", segments=12)
        take(dot, "front dot", "dot", group=SLIDE if K.barrel_r else FIXED, sharp=50.0)


def barrel():
    """What shows of the barrel: its muzzle, and its chamber in the port and under the open top."""
    rear, front, chamber = K.chamber
    profile = [(0.0, rear), (chamber, rear), (chamber, front), (K.barrel_r, front + 0.002)]
    profile += [(K.barrel_r, K.muzzle_y - 0.0008), (K.barrel_r - 0.0006, K.muzzle_y), (K.bore + 0.0006, K.muzzle_y), (K.bore, K.muzzle_y - 0.0010),
                (K.bore, K.muzzle_y - 0.03), (0.0, K.muzzle_y - 0.03)]
    bm = bmesh.new()
    lathe(bm, profile, along_y(), segments=40)
    take(bm, "barrel", K.barrel_finish, sharp=30.0)
    G.TUBES["barrel"] = (0.0, (front + K.muzzle_y) / 2, K.barrel_r)


def bushing():
    """The forty-five's barrel bushing and the plug under it, in the front of the slide."""
    ring = bmesh.new()
    lathe(ring, [(K.barrel_r + 0.0002, K.slide_long - 0.004), (0.0106, K.slide_long - 0.004), (0.0106, K.muzzle_y - 0.0004), (0.0101, K.muzzle_y),
                 (K.barrel_r + 0.0006, K.muzzle_y), (K.barrel_r + 0.0002, K.muzzle_y - 0.0005)], along_y(), segments=40)
    take(ring, "bushing", "bright", group=SLIDE, sharp=30.0)
    plug = bmesh.new()
    lathe(plug, [(0.0, K.slide_long - 0.004), (0.0044, K.slide_long - 0.004), (0.0044, K.slide_long - 0.0008), (0.0040, K.slide_long - 0.0002),
                 (0.0016, K.slide_long - 0.0002), (0.0012, K.slide_long - 0.0010), (0.0, K.slide_long - 0.0010)], along_y(z=-0.0094), segments=28)
    take(plug, "spring plug", K.finish, group=SLIDE, sharp=30.0)


def magnum_barrel():
    """The magnum's heavy barrel: flat sided, bevelled along its top, with a rib for the front sight, fixed to the frame ahead of the slide."""
    rear = K.slide_long + 0.0005
    hole = bmesh.new()
    lathe(hole, [(0.0, K.muzzle_y - 0.03), (K.bore, K.muzzle_y - 0.03), (K.bore, K.muzzle_y + 0.01), (0.0, K.muzzle_y + 0.01)], along_y(), segments=32)
    bore_cut = cutter(hole)
    block = bmesh.new()
    half, top, bottom = 0.0140, 0.0172, -0.0138
    ring = [(half, bottom + 0.0015), (half, top - 0.0060), (half - 0.0050, top), (-half + 0.0050, top), (-half, top - 0.0060), (-half, bottom + 0.0015),
            (-half + 0.0015, bottom), (half - 0.0015, bottom)]
    loft(block, [[(x, y, z) for x, z in ring] for y in (rear, K.muzzle_y)])
    take(block, "barrel", K.barrel_finish, bevel=0.0009, segments=2, sharp=30.0, cut=(bore_cut,), texels=1.2)
    bpy.data.objects.remove(bore_cut)
    rib = bmesh.new()
    cuboid(rib, -0.0042, 0.0042, rear + 0.002, K.muzzle_y - 0.002, top - 0.001, K.rib_top)
    take(rib, "rib", "rib", bevel=0.0005, segments=2, sharp=40.0)


def frame():
    """The frame under the slide, the dust cover ahead of the trigger and the tang behind under the hammer."""
    top = K.slide_bottom + 0.0002
    outline = [(K.tang[0][0], top), (K.dust_front - 0.004, top), (K.dust_front, top - 0.0030), (K.dust_front, K.dust_bottom + 0.0025),
               (K.dust_front - 0.0040, K.dust_bottom), (K.guard[-1][0], K.dust_bottom), (K.guard[0][0] + 0.002, K.dust_bottom),
               (K.guard[0][0] - 0.001, K.grip_top_z - 0.0040)]
    back = K.grip_line(K.grip_top_z - 0.004)[1]
    outline += [(back + 0.004, K.grip_top_z - 0.0040)] + list(reversed(K.tang[1:]))
    bm = bmesh.new()
    G.prism(bm, outline, "x", -K.frame_half, K.frame_half)
    take(bm, "frame", K.frame_finish, bevel=0.0007, segments=2, sharp=35.0, texels=1.1)

    guard = bmesh.new()
    path = along(K.guard, 44)
    sweep(guard, path, K.frame_half * 0.50, [0.0052 + 0.0014 * abs(n / 43 - 0.5) * 2 for n in range(44)])
    take(guard, "trigger guard", K.frame_finish, bevel=0.0010, segments=3, sharp=50.0)

    trigger = bmesh.new()
    path = along(K.trigger, 20)
    sweep(trigger, path, 0.0030, [0.0058 - 0.0024 * n / 19 for n in range(20)])
    take(trigger, "trigger", "black" if K.finish != "stainless" else "stainless", bevel=0.0009, segments=3, sharp=50.0)

    hammer = bmesh.new()
    G.prism(hammer, K.hammer, "x", -K.hammer_half, K.hammer_half)
    take(hammer, "hammer", K.finish if K.finish != "coat" else "black", bevel=0.0008, segments=2, sharp=40.0)


def grip():
    """The frame's grip and the panels on each side of it, the forty-five's grip safety, the magazine's well at its foot."""
    stations = []
    for n in range(10):
        z = K.grip_top_z + (K.grip_foot - K.grip_top_z) * n / 9
        front, back = K.grip_line(z)
        stations.append(((front, z), (back, z)))
    # Cut square across at its foot, the frame's ring at each station is in the plane through its front and back.
    body = bmesh.new()
    loft(body, [G.grip_ring(front, back, K.grip_half, 3.2, 1.0) for front, back in stations])
    # Most of it is under the panels.
    take(body, "grip frame", K.frame_finish, bevel=0.0008, segments=2, sharp=40.0, texels=0.8)

    # The panels: shells laid on the grip's sides, from under the frame to above its foot, or the magnum's one piece of
    # rubber round its sides and back.
    top, foot = K.grip_top_z - 0.0050, K.grip_foot + 0.0040
    thick = K.panel_out - K.grip_half
    arcs = [(-62.0, 262.0)] if K.wrapped else [(-62.0, 72.0), (108.0, 242.0)]
    panels = bmesh.new()
    for first, last in arcs:
        rings = []
        for n in range(12):
            z = top + (foot - top) * n / 11
            front, back = K.grip_line(z)
            angles = [first + (last - first) * i / 23 for i in range(24)]
            outer = [G.grip_point((front, z), (back, z), K.grip_half, 3.2, 1.0, math.radians(angle), thick) for angle in angles]
            inner = [G.grip_point((front, z), (back, z), K.grip_half, 3.2, 1.0, math.radians(angle), -0.0005) for angle in reversed(angles)]
            rings.append(outer + inner)
        loft(panels, rings)
    take(panels, "grip panels", K.grips, bevel=0.0012, segments=3, sharp=35.0, texels=1.15)
    screws = bmesh.new()
    for z in (top - 0.010, foot + 0.010):
        front, back = K.grip_line(z)
        for side in (1, -1):
            disc(screws, (side * (K.panel_out - 0.0004), (front + back) / 2, z), 0.0030, 0.0008, "x" if side > 0 else "-x", segments=20, dome=0.0004)
    take(screws, "grip screws", "screw", sharp=40.0)

    if K.item == "Base.Pistol2":
        # The grip safety: a short spur under the hammer, its back the top of the grip's back strap.
        y = K.grip_line(K.grip_top_z)[1]
        spur = [(-0.0045, K.slide_bottom - 0.0003), (-0.0140, K.slide_bottom - 0.0006), (-0.0180, K.slide_bottom - 0.0030), (-0.0165, K.slide_bottom - 0.0058),
                (y - 0.0010, K.grip_top_z + 0.0020), (y - 0.0030, K.grip_top_z - 0.0160), (y + 0.0010, K.grip_top_z - 0.0165), (y + 0.0020, K.grip_top_z),
                (-0.0020, K.slide_bottom - 0.0025)]
        safety = bmesh.new()
        G.prism(safety, spur, "x", -0.0085, 0.0085)
        take(safety, "grip safety", K.finish, bevel=0.0010, segments=2, sharp=40.0)


def controls():
    """The slide stop and the magazine's catch on the left, and the forty-five's thumb safety."""
    over = K.guard[0][0] + 0.005
    stop = [(over, K.slide_bottom - 0.0016), (over + 0.034, K.slide_bottom - 0.0012), (over + 0.039, K.slide_bottom - 0.0030), (over + 0.036, K.slide_bottom - 0.0065),
            (over + 0.010, K.slide_bottom - 0.0080), (over, K.slide_bottom - 0.0060)]
    bm = bmesh.new()
    G.prism(bm, stop, "x", -K.frame_half - 0.0020, -K.frame_half + 0.0004)
    take(bm, "slide stop", K.finish if K.finish != "coat" else "black", bevel=0.0006, segments=2, sharp=40.0)
    pins = bmesh.new()
    disc(pins, (K.frame_half - 0.0001, over + 0.030, K.slide_bottom - 0.0050), 0.0022, 0.0004, "x", segments=16)
    take(pins, "slide stop pin", "bright", sharp=40.0)
    catch = bmesh.new()
    front, back = K.grip_line(K.grip_top_z - 0.010)
    disc(catch, (-K.grip_half - 0.0004, front - 0.004, K.grip_top_z - 0.008), 0.0036, 0.0020, "-x", segments=20, dome=0.0004)
    take(catch, "magazine catch", "black" if K.finish != "stainless" else "stainless", sharp=40.0)
    if K.item == "Base.Pistol2":
        thumb = [(-0.0040, K.slide_bottom - 0.0075), (0.0040, K.slide_bottom - 0.0075), (0.0150, K.slide_bottom - 0.0040), (0.0215, K.slide_bottom - 0.0025),
                 (0.0222, K.slide_bottom - 0.0006), (0.0150, K.slide_bottom - 0.0002), (0.0040, K.slide_bottom - 0.0004), (-0.0045, K.slide_bottom - 0.0020)]
        safety = bmesh.new()
        G.prism(safety, thumb, "x", -K.frame_half - 0.0024, -K.frame_half + 0.0004)
        take(safety, "thumb safety", K.finish, bevel=0.0006, segments=2, sharp=40.0)
        tube = bmesh.new()
        lathe(tube, [(0.0, 0.0240), (0.0021, 0.0240), (0.0024, 0.0250), (0.0024, 0.0400), (0.0021, 0.0410), (0.0, 0.0410)],
              along_y(-K.frame_half - 0.0012, 0.0, K.slide_bottom - 0.0032), segments=16)
        take(tube, "plunger tube", K.finish, sharp=30.0)


def magazine():
    """The magazine: its body up the grip, out of sight while it is in, and the plate on its foot that shows."""
    stations = []
    for n in range(6):
        z = K.grip_top_z + 0.004 + (K.grip_foot - K.grip_top_z - 0.004) * n / 5
        front, back = K.grip_line(z, 0.0085)
        stations.append((front, back, z))
    body = bmesh.new()
    loft(body, [[(x, y, z) for x, y in ((K.magazine_half, back), (K.magazine_half, front - 0.0040), (K.magazine_half * 0.6, front), (-K.magazine_half * 0.6, front),
                                       (-K.magazine_half, front - 0.0040), (-K.magazine_half, back))] for front, back, z in stations])
    take(body, "magazine", "blued", group=MAGAZINE, bevel=0.0006, segments=2, sharp=40.0, texels=0.35)
    front, back = K.grip_line(K.grip_foot, -0.0006)
    plate = bmesh.new()
    outline = [(back, K.grip_foot - K.foot_plate), (front - 0.003, K.grip_foot - K.foot_plate), (front, K.grip_foot - K.foot_plate + 0.0020), (front, K.grip_foot),
               (back, K.grip_foot)]
    G.prism(plate, outline, "x", -K.grip_half - 0.0012, K.grip_half + 0.0012)
    take(plate, "magazine foot", K.foot_finish, group=MAGAZINE, bevel=0.0010, segments=2, sharp=40.0)


def case():
    brass = bmesh.new()
    lathe(brass, K.case, along_y(CASE_AT.x, CASE_AT.y, CASE_AT.z), segments=24)
    take(brass, "case", "brass", group=CASE, sharp=30.0)


BARE_COAT = (0.45, 0.45, 0.47)


def coat(g):
    """The service pistol's slide: a hard black coat over steel, its rear cut across with grooves to draw it back by."""
    colour, rough, metal, height = G.black_coat(g, (0.020, 0.020, 0.022), 0.42, 0.15, BARE_COAT)
    return g.finish(colour, rough, metal, g.add(height, serrated(g)))


def serrated(g):
    """Upright grooves across the sides of the back of the slide."""
    sides = g.step(g.math("ABSOLUTE", g.nx), 0.6, 0.8)
    within = g.times(g.step(g.y, K.serrations[0], K.serrations[0] + 0.0006), g.math("SUBTRACT", 1.0, g.step(g.y, K.serrations[1] - 0.0006, K.serrations[1])))
    low = g.step(g.z, K.slide_bottom + 0.0030, K.slide_bottom + 0.0040)
    return g.times(sides, within, low, g.math("SUBTRACT", g.teeth(g.times(g.y, 1.0 / 0.0018)), 1.0), 0.00045)


def parkerised(g):
    """The forty-five's finish: steel etched dull and grey-black, rubbed bright where hands and holster wear it."""
    grain = g.noise(3200.0, 2.0)
    rubbed = g.worn(0.28)
    colour = g.mix(rubbed, g.scaled(g.colour(0.040, 0.041, 0.042), g.add(0.75, g.times(grain, 0.5))), g.colour(0.36, 0.36, 0.37))
    return g.finish(colour, g.add(0.58, g.times(grain, 0.12), g.times(rubbed, -0.25)), g.add(0.55, g.times(rubbed, 0.45)), g.times(grain, 0.00003))


def stainless(g, rough=0.30):
    """Brushed stainless steel: pale, the lines of its polishing along the gun."""
    lines = g.streaks()
    rubbed = g.worn(0.4)
    colour = g.scaled(g.colour(0.50, 0.49, 0.475), g.add(1.0, g.times(lines, 0.25), g.times(rubbed, 0.12)))
    return g.finish(colour, g.add(rough, g.times(lines, 0.22), g.times(rubbed, -0.10)), 1.0, g.times(lines, 0.00002))


def serrated_slide(finish):
    """A slide in `finish`, with grooves across the sides of its back."""
    def make(g):
        graph = finish(g)
        bump = graph.tree.nodes["Bump"]
        height = bump.inputs["Height"].links[0].from_socket
        graph.tree.links.new(g.add(height, serrated(g)), bump.inputs["Height"])
        return graph
    return make


def grip_area(inset):
    """The panel of the grip's side that is chequered, `inset` metres in from the grip's front and back, as Graph.panel takes it."""
    panel_z = (K.grip_top_z + K.grip_foot) / 2
    front, back = K.grip_line(panel_z)
    return ((front + back) / 2, panel_z, (K.grip_top_z - K.grip_foot) / 2 - 0.009, (front - back) / 2 - inset, 0.004 if inset > 0.0078 else 0.003,
            math.radians(90 - K.rake))


def polygrip(g):
    """Black plastic grips moulded in fine diamonds, a smooth border round them."""
    side = g.step(g.math("ABSOLUTE", g.nx), 0.30, 0.55)
    cut, line, diamond = g.checkered(g.panel(*grip_area(0.0080)), 0.0012, side)
    grain = g.noise(1800.0, 2.0)
    colour = g.scaled(g.colour(0.016, 0.016, 0.017), g.add(1.0, g.times(cut, g.math("SUBTRACT", g.times(diamond, 0.6), 0.3))))
    return g.finish(colour, g.add(0.55, g.times(cut, 0.2), g.times(grain, 0.06)), 0.0,
                    g.add(g.times(cut, g.math("SUBTRACT", g.times(diamond, 0.00045), 0.00025)), g.times(line, -0.00020), g.times(grain, 0.00001)))


def walnut(g):
    """Walnut grips, chequered all over but for a diamond left smooth round each screw."""
    screws = []
    for z in (K.grip_top_z - 0.0150, K.grip_foot + 0.0140):
        f, b = K.grip_line(z)
        screws.append(((f + b) / 2, z, 0.0110, 0.0150))
    return G.walnut(g, (grip_area(0.0075),), 0.0011, span=0.12, tint=(1.35, 1.02, 0.80), smooth=screws)


def red(g):
    return g.finish(g.colour(0.55, 0.03, 0.02), 0.5, 0.0, 0.0)


def dot(g):
    return g.finish(g.colour(0.80, 0.80, 0.76), 0.45, 0.0, 0.0)


def sight(g):
    return g.finish(*G.black_coat(g, (0.014, 0.014, 0.016), 0.60, 0.15, BARE_COAT))


def polymer(g):
    grain = g.noise(1500.0, 2.0)
    return g.finish(g.scaled(g.colour(0.018, 0.018, 0.019), g.add(0.9, g.times(grain, 0.2))), 0.6, 0.0, g.times(grain, 0.00002))


def rib(g):
    colour, rough, lines = G.steel(g, (0.40, 0.40, 0.39), 0.45, wear=0.3)
    top = g.step(g.nz, 0.6, 0.9)
    return g.finish(colour, rough, 1.0, g.times(top, g.teeth(g.times(g.y, 1.0 / 0.0011)), 0.00012))


def screw(g):
    return G.blued(g, 0.35) if K.finish != "stainless" else stainless(g, 0.35)


SLIDES = {"coat": coat, "parkerised": serrated_slide(parkerised), "stainless": serrated_slide(stainless)}
G.MAKERS.update({"coat": coat, "parkerised": parkerised, "stainless": stainless, "polygrip": polygrip, "walnut": walnut, "red": red, "dot": dot, "sight": sight,
                 "polymer": polymer, "rib": rib, "screw": screw, NAME + " slide": SLIDES[K.finish]})


def build():
    G.clear()
    case()
    slide()
    if K.finish != "parkerised":
        levers_on_slide()
    sights()
    if K.barrel_r:
        barrel()
    else:
        chamber = bmesh.new()
        rear, front, r = K.chamber
        lathe(chamber, [(0.0, rear), (r, rear), (r, front), (0.0, front)], along_y(), segments=36)
        take(chamber, "chamber", "stainless", sharp=30.0)
        magnum_barrel()
    if K.item == "Base.Pistol2":
        bushing()
    frame()
    grip()
    controls()
    magazine()
    G.count_triangles()


def views():
    middle = K.muzzle_y / 2 - 0.02
    return {
        "right": ((0.75, middle, -0.03), (0.0, middle, -0.045), 50),
        "left": ((-0.75, middle, -0.03), (0.0, middle, -0.045), 50),
        "quarter": ((0.32, -0.20, 0.16), (0.0, middle, -0.035), 50),
        "quarter-left": ((-0.30, -0.18, 0.14), (0.0, middle, -0.035), 50),
        "top": ((0.0, middle, 0.55), (0.0, middle + 0.0001, 0.0), 50),
        "under": ((0.25, 0.30, -0.28), (0.0, middle, -0.05), 50),
        "muzzle": ((0.14, K.muzzle_y + 0.26, 0.06), (0.0, K.muzzle_y - 0.05, -0.01), 55),
        "sights": ((0.0, GRIP_Y - EYE_BEHIND_GRIP, K.sight_top), (0.0, K.muzzle_y + 0.6, K.sight_top), 45),
        "hip": ((-0.10, -0.32, 0.16), (0.0, K.muzzle_y, -0.02), 30),
        "back": ((0.12, -0.25, 0.05), (0.0, 0.02, -0.03), 60),
    }


def main():
    args = sys.argv[sys.argv.index("--") + 1:]
    G.load_pictures(args[0])
    G.TEXTURE = 1024
    build()
    if "--raw" in args:
        at = args.index("--raw")
        G.render(args[at + 1], views(), [name for name in args[at + 2:] if name in views()], size=(1200, 700), samples=24)
        return
    gun = G.unwrap(NAME)
    mirror = (1.0, 1.0, -1.0)

    def placed(where):
        return tuple(float(v) * m + 0.0 for v, m in zip(where - GRIP, mirror))

    # The game's laser and light, each clamped under the dust cover from just ahead of the trigger guard, at the game's
    # units to a metre of this pistol: its model's muzzle is as far from the hand as this one's.
    units = math.hypot(*K.game_muzzle) / (Vector((0.0, K.muzzle_y, 0.0)) - GRIP).length
    back = K.guard[-1][0] + 0.003
    laser = Vector((0.0, back - G.GAME_LASER_BACK / units, K.dust_bottom - G.GAME_LASER_TOP / units))
    light = Vector((0.0, back, K.dust_bottom - G.GAME_LIGHT_TOP / units))
    head = G.head_v6(placed(Vector((0.0, K.muzzle_y, 0.0))), placed(GRIP), placed(PORT), K.travel, K.sight_top, EYE_BEHIND_GRIP, [], None, placed(laser),
                     placed(light))
    if "--stats" in args:
        G.stats(gun, os.path.join(args[args.index("--stats") + 1], NAME + "-layout.png"))
        return
    if "--mesh" in args:
        G.pack(gun, NAME, GRIP, None, None, (FIXED, 1, SLIDE, CASE, MAGAZINE), head, moved=((CASE, CASE_AT),), mirror=mirror)
        return
    colour, surface = G.pictures_of(G.bake(gun))
    if "--no-pack" not in args:
        G.pack(gun, NAME, GRIP, colour, surface, (FIXED, 1, SLIDE, CASE, MAGAZINE), head, moved=((CASE, CASE_AT),), mirror=mirror)
    if "--renders" in args:
        folder = os.path.abspath(args[args.index("--renders") + 1])
        G.dress(gun, colour, surface, folder)
        G.render(folder, views(), [name for name in args if name in views()])


main()
