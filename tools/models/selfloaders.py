"""Builds the mod's own models of the game's two self-loading rifles and packs them for the jar.

Run headless with Blender 4.4, from the repository root:

    blender --background --factory-startup --threads 1 --python tools/models/selfloaders.py -- PICTURES [--battle] [--renders DIR] [--raw DIR [VIEW ...]] [--no-pack] [--cpu] [--mesh] [--stats]

PICTURES is the folder tools/models/shotgun_textures.py wrote: the battle rifle's stock is
the same walnut. Both are made here from nothing and are of no maker's pattern:

- the assault rifle (the game's Base.AssaultRifle): a select-fire rifle of light alloy and
  black plastic, its receiver in two halves, a carrying handle over it with the rear sight
  in its back end, a twenty inch barrel under a round ribbed handguard, a front sight on a
  triangular post, a slotted flash hider and a curved thirty round magazine;
- with --battle, the battle rifle (the game's Base.AssaultRifle2): a self-loader with a
  steel receiver open on top over its bolt, an operating rod along its right side worked by
  a handle on it, a one piece walnut stock with a wooden handguard over the barrel, a gas
  cylinder under the barrel, an aperture rear sight, a slotted flash suppressor with the
  front sight on it, a straight twenty round magazine, and a low base over the receiver
  that a scope stands on.

It writes NAME.mesh.gz under src/main/resources/models and NAME.colour.gz and
NAME.surface.gz under src/textures/models, where NAME is assault or battle, laid out as
gunsmith.py says, at version 6, with six parts in this order: what is fixed; the part the
hand works to charge it (the assault rifle's charging handle, the battle rifle's operating
rod); the bolt carrier or bolt, which every shot sends back; a fired case, about its own
middle, for the mod to throw out of the port; the magazine, in its well; and the scope's
base, which the mod draws only with a scope fitted (none on the assault rifle, whose
carrying handle is what a scope stands on). The numbers after the parts say where the
hand takes hold of the charging part and whether it rides with the bolt; after them the
head says where the game's red dot sight and laser are fixed: see pack.

--raw renders the parts as they are made, unbaked, for looking at the shape.

The model is in metres. Its barrel runs along +y and its top is +z, as the game's own
weapons are made, and its origin is in the hand that holds its grip.
"""

import math
import os
import sys

import bmesh
import bpy
from mathutils import Matrix, Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gunsmith as G  # noqa: E402
from gunsmith import along, along_x, along_y, cuboid, cutter, grip_ring, lathe, loft, prism, rod, stock_ring, sweep, take  # noqa: E402

BATTLE = "--battle" in sys.argv
NAME = "battle" if BATTLE else "assault"
FIXED, HANDLE, BOLT, CASE, MAGAZINE, MOUNT = range(6)
# The fired case is made well clear of the gun, so that neither shades the other, and is packed about its own middle.
CASE_AT = Vector((0.0, 0.30, 0.40))

# Laid out with the bore along y through x = 0, z = 0 and the trigger at y = 0. GRIP is the
# finished model's origin, where the game has its own rifle's: every point the game fixes
# something by (its muzzle, its scope points, its red dot and laser) is where the game's own mesh
# has it, at the 0.4957 of the game's units to a metre its shotgun, scopes and hands are drawn
# at. GRIP's place along the gun puts the game's scope point over the middle of the base a scope
# stands on, and the barrel runs out to the game's muzzle from there.
UNITS = 0.4957
if BATTLE:
    GRIP = Vector((0.0, -0.075, -0.0297 / UNITS))
    MUZZLE_Y = GRIP.y + 0.5004 / UNITS
    # The game's two scope points, "scope" and "scope2", over the bore.
    SCOPE_POINTS = (0.0538 - 0.0297, 0.0551 - 0.0297)
    TRAVEL = 0.090
    SIGHT_LINE = 0.0330
    PORT = Vector((0.0120, 0.040, 0.0090))
else:
    GRIP = Vector((0.0, -0.045, -0.0237 / UNITS))
    MUZZLE_Y = GRIP.y + 0.3993 / UNITS
    SCOPE_POINTS = (0.0683 - 0.0237, 0.0678 - 0.0237)
    TRAVEL = 0.085
    SIGHT_LINE = 0.0600
    PORT = Vector((0.0128, 0.040, 0.0015))
# A scope stands on the gun ScopeModel.SEAT under the game's scope point: its base or handle's top is there, under the higher of the two.
SEAT = max(SCOPE_POINTS) / UNITS - 0.022
# The eye with the sights up is 10 and 11.5 cm behind the back of the rear sight, as a cheek on the stock puts it: far
# enough that the most a burst at the hip throws the gun back (Kick.backMost) leaves it the near plane clear.
EYE_BEHIND_GRIP = GRIP.y - ((-0.064 - 0.115) if BATTLE else (-0.074 - 0.100))


def ring(y, half, bottom, top, corner, count=8, lower=None):
    """A section across the gun at y: a rectangle half wide, from bottom to top, its corners rounded by corner, the lower
    two by `lower` where that is given."""
    points = []
    lower = corner if lower is None else lower
    for cx, cz, start, radius in ((half, top, 0, corner), (-half, top, 90, corner), (-half, bottom, 180, lower), (half, bottom, 270, lower)):
        mx = cx - math.copysign(radius, cx)
        mz = cz - math.copysign(radius, cz)
        for n in range(count + 1):
            angle = math.radians(start + 90 * n / count)
            points.append((mx + radius * math.cos(angle), y, mz + radius * math.sin(angle)))
    return points


def side_plate(bm, outline, half):
    """A slab of the gun's own outline seen from the side, (y, z) points in order round it, half thick either side of x = 0."""
    prism(bm, outline, "x", -half, half)


def tube(y0, y1, radius, segments=40, inner=0.0):
    """A round bar along the bore, solid or bored out."""
    bm = bmesh.new()
    if inner:
        lathe(bm, [(inner, y0), (radius, y0), (radius, y1), (inner, y1), (inner, y0 + 0.0001)], along_y(), segments=segments)
    else:
        lathe(bm, [(0.0, y0), (radius, y0), (radius, y1), (0.0, y1)], along_y(), segments=segments)
    return bm


def ribbed(y, radius, ribs, deep, count, top_flat=0.0):
    """A round section with ribs along it, as a handguard is moulded."""
    points = []
    for i in range(count):
        angle = 2 * math.pi * i / count
        r = radius + deep * (0.5 + 0.5 * math.cos(angle * ribs)) ** 3
        points.append((r * math.sin(angle), y, r * math.cos(angle)))
    return points


def case(length, rim, body, shoulder, neck, mouth):
    """A fired case, a rimless bottle of brass, open at the mouth, about its own middle."""
    half = length / 2
    profile = [(0.0, -half), (rim * 0.86, -half), (rim, -half + 0.0005), (rim, -half + 0.0012), (rim * 0.84, -half + 0.0016), (rim * 0.84, -half + 0.0026),
               (body, -half + 0.0032), (body * 0.97, shoulder), (neck, shoulder + (body - neck) * 1.1), (neck, half), (mouth, half), (mouth, half - 0.006),
               (0.0, half - 0.006)]
    brass = bmesh.new()
    lathe(brass, profile, along_y(CASE_AT.x, CASE_AT.y, CASE_AT.z), segments=24)
    take(brass, "case", "brass", group=CASE, sharp=30.0)


def bird_cage(y0, y1, radius, slots, closed_below):
    """A slotted flash hider: a tube with long slots round it, none underneath if closed_below, so that it does not
    kick up dust; its front a crown round the bore."""
    bm = tube(y0, y1, radius, segments=40, inner=0.0042)
    cuts = []
    for n in range(slots):
        angle = 2 * math.pi * n / slots if not closed_below else math.radians(-90 + 180 * n / (slots - 1))
        slot = bmesh.new()
        cuboid(slot, -0.0014, 0.0014, y0 + 0.012, y1 - 0.007, 0.0, radius + 0.004)
        bmesh.ops.transform(slot, matrix=Matrix.Rotation(angle, 4, "Y"), verts=slot.verts)
        cuts.append(cutter(slot, bevel=0.0006))
    take(bm, "flash hider", "phosphate", bevel=0.0005, segments=2, sharp=40.0, cut=cuts, texels=1.4)
    for used in cuts:
        bpy.data.objects.remove(used)


# The assault rifle.

A_UPPER_REAR, A_UPPER_FRONT = -0.074, 0.104
A_UPPER_HALF, A_UPPER_BOTTOM, A_UPPER_TOP = 0.0127, -0.0125, 0.0205
A_HANDLE_TOP = SEAT
# The line of sight runs along a channel in the handle's top, from the aperture in its back end to the post.
A_CHANNEL_HALF, A_CHANNEL_FOOT, A_BRIDGE_FOOT = 0.0036, SIGHT_LINE - 0.0042, SIGHT_LINE - 0.0120
A_REAR_SIGHT_Y = -0.058
A_BARREL_R = 0.0095
A_HANDGUARD_REAR, A_HANDGUARD_FRONT, A_HANDGUARD_R = 0.126, 0.424, 0.0232
A_FRONT_SIGHT_Y = 0.460
A_HIDER_FROM = MUZZLE_Y - 0.057
A_BUTT = -0.375
A_WELL_REAR, A_WELL_FRONT, A_WELL_FOOT = 0.025, 0.103, -0.071
# The magazine's top and the line it curves along, and its foot.
A_MAG_SPINE = [(0.064, -0.009), (0.064, -0.045), (0.065, -0.080), (0.069, -0.115), (0.076, -0.150), (0.087, -0.185), (0.097, -0.208)]


def a_upper():
    tunnel = cutter(tube(A_UPPER_REAR - 0.01, 0.078, 0.0131, segments=40))
    port = bmesh.new()
    cuboid(port, 0.004, 0.03, 0.012, 0.064, -0.0080, 0.0110)
    port_cut = cutter(port, bevel=0.0018)
    slot = bmesh.new()
    cuboid(slot, -0.0055, 0.0055, A_UPPER_REAR - 0.01, A_UPPER_REAR + 0.018, 0.0110, 0.030)
    slot_cut = cutter(slot)

    body = bmesh.new()
    rings = [ring(y, A_UPPER_HALF, A_UPPER_BOTTOM, A_UPPER_TOP, 0.0045, lower=0.0008) for y in (A_UPPER_REAR, 0.080)]
    # Ahead of the port the receiver rounds into the collar the barrel is screwed into.
    rings += [ring(0.084, A_UPPER_HALF, A_UPPER_BOTTOM, A_UPPER_TOP, 0.0060, lower=0.0030), ring(0.088, 0.0150, -0.0150, 0.0150, 0.0110),
              ring(A_UPPER_FRONT, 0.0150, -0.0150, 0.0150, 0.0110)]
    loft(body, rings)
    take(body, "upper receiver", "anodised", bevel=0.0006, segments=2, sharp=40.0, cut=(tunnel, port_cut, slot_cut), texels=1.2)
    for used in (tunnel, port_cut, slot_cut):
        bpy.data.objects.remove(used)

    # The handle over it, which the rear sight is in the back of and a scope's mount stands on.
    handle = bmesh.new()
    keys = [(A_UPPER_REAR + 0.0005, A_UPPER_TOP - 0.002, A_HANDLE_TOP - 0.010), (A_UPPER_REAR + 0.003, A_UPPER_TOP - 0.002, A_HANDLE_TOP - 0.003),
            (-0.043, A_UPPER_TOP - 0.002, A_HANDLE_TOP), (-0.041, A_BRIDGE_FOOT, A_HANDLE_TOP), (0.040, A_BRIDGE_FOOT, A_HANDLE_TOP),
            (0.044, A_UPPER_TOP - 0.002, A_HANDLE_TOP), (0.058, A_UPPER_TOP - 0.002, A_HANDLE_TOP - 0.004), (0.070, A_UPPER_TOP - 0.002, A_UPPER_TOP + 0.020),
            (0.072, A_UPPER_TOP - 0.002, A_UPPER_TOP + 0.016)]
    loft(handle, [ring(y, 0.0098, bottom, top, 0.0030, lower=0.0016) for y, bottom, top in keys])
    window = bmesh.new()
    cuboid(window, -0.0064, 0.0064, A_UPPER_REAR - 0.01, -0.044, SIGHT_LINE - 0.0080, SIGHT_LINE + 0.0072)
    window_cut = cutter(window, bevel=0.0012)
    groove = bmesh.new()
    cuboid(groove, -A_CHANNEL_HALF, A_CHANNEL_HALF, -0.046, 0.08, A_CHANNEL_FOOT, A_HANDLE_TOP + 0.01)
    groove_cut = cutter(groove, bevel=0.0008)
    hole = bmesh.new()
    lathe(hole, [(0.0, 0.05), (0.0034, 0.05), (0.0034, 0.09), (0.0, 0.09)], Matrix.Translation((0.0, 0.004, 0.0)), segments=20)
    hole_cut = cutter(hole)
    take(handle, "carrying handle", "anodised", bevel=0.0007, segments=2, sharp=40.0, cut=(window_cut, groove_cut, hole_cut), texels=1.3)
    for used in (window_cut, groove_cut, hole_cut):
        bpy.data.objects.remove(used)

    # The aperture: a thin leaf across the window with a hole the eye looks through.
    leaf = bmesh.new()
    cuboid(leaf, -0.0066, 0.0066, A_REAR_SIGHT_Y - 0.0009, A_REAR_SIGHT_Y + 0.0009, SIGHT_LINE - 0.0080, SIGHT_LINE + 0.0060)
    peep = cutter(tube(A_REAR_SIGHT_Y - 0.01, A_REAR_SIGHT_Y + 0.01, 0.0024, segments=24))
    peep.location.z = SIGHT_LINE
    take(leaf, "rear aperture", "sight", bevel=0.0003, segments=1, cut=(peep,), texels=1.5)
    bpy.data.objects.remove(peep)
    # The drum that raises it, across the housing under the window, and the knob on the right that moves it over.
    drum = bmesh.new()
    lathe(drum, [(0.0, -0.0118), (0.0118, -0.0118), (0.0124, -0.0108), (0.0124, 0.0108), (0.0118, 0.0118), (0.0, 0.0118)],
          along_x(0.0, A_REAR_SIGHT_Y + 0.002, SIGHT_LINE - 0.0215), segments=36)
    take(drum, "elevation drum", "knurled", bevel=0.0003, segments=1, sharp=40.0)
    knob = bmesh.new()
    lathe(knob, [(0.0, 0.0095), (0.0068, 0.0095), (0.0068, 0.0150), (0.0060, 0.0158), (0.0, 0.0158)], along_x(0.0, A_REAR_SIGHT_Y + 0.004, SIGHT_LINE - 0.0005),
          segments=28)
    take(knob, "windage knob", "knurled", bevel=0.0003, segments=1, sharp=40.0)

    # The forward assist, a plunger on the right behind the port, and the bump that turns cases away from a left-handed face.
    assist = bmesh.new()
    turn = Matrix.Translation((0.0100, -0.010, 0.0075)) @ Matrix.Rotation(math.radians(180), 4, "Z") @ along_y()
    lathe(assist, [(0.0, 0.0), (0.0078, 0.0), (0.0078, 0.020), (0.0062, 0.021), (0.0062, 0.026), (0.0072, 0.027), (0.0072, 0.033), (0.0060, 0.0350), (0.0, 0.0350)],
          turn, segments=32)
    take(assist, "forward assist", "anodised", bevel=0.0004, segments=1, sharp=40.0)
    deflector = bmesh.new()
    side_plate(deflector, [(-0.006, 0.000), (0.010, 0.000), (0.010, 0.016), (0.004, 0.019), (-0.006, 0.012)], 0.0035)
    bmesh.ops.translate(deflector, verts=deflector.verts, vec=(A_UPPER_HALF + 0.0025, 0.0, 0.0))
    take(deflector, "case deflector", "anodised", bevel=0.0010, segments=2, sharp=40.0)

    # The dust cover over the port, swung open on its hinge under it.
    cover = bmesh.new()
    hinge = Vector((A_UPPER_HALF + 0.0012, 0.0, -0.0110))
    down = Vector((0.36, 0.0, -0.93)).normalized()
    out = Vector((0.93, 0.0, 0.36))
    corners = [(hinge + down * d + out * o) for d, o in ((0.0, 0.0), (0.0205, 0.0), (0.0205, 0.0014), (0.0, 0.0014))]
    loft(cover, [[(c.x, y, c.z) for c in corners] for y in (0.010, 0.066)])
    take(cover, "dust cover", "phosphate", bevel=0.0004, segments=1, sharp=40.0)
    pin = bmesh.new()
    rod(pin, [(hinge.x, 0.006, hinge.z), (hinge.x, 0.070, hinge.z)], [0.0011, 0.0011], segments=10)
    take(pin, "cover hinge", "phosphate", sharp=50.0)


def a_lower():
    outline = [(A_UPPER_REAR + 0.002, A_UPPER_BOTTOM), (A_UPPER_FRONT - 0.004, A_UPPER_BOTTOM), (A_UPPER_FRONT - 0.004, -0.024), (A_WELL_FRONT, -0.034),
               (A_WELL_FRONT, A_WELL_FOOT + 0.006), (A_WELL_FRONT + 0.003, A_WELL_FOOT), (A_WELL_REAR - 0.004, A_WELL_FOOT), (A_WELL_REAR - 0.001, A_WELL_FOOT + 0.006),
               (A_WELL_REAR - 0.001, -0.040), (-0.022, -0.040), (-0.034, -0.033), (A_UPPER_REAR + 0.010, -0.030), (A_UPPER_REAR + 0.002, -0.026)]
    body = bmesh.new()
    side_plate(body, outline, A_UPPER_HALF)
    well = bmesh.new()
    cuboid(well, -0.0114, 0.0114, A_WELL_REAR + 0.0015, A_WELL_FRONT - 0.0025, A_WELL_FOOT - 0.01, A_UPPER_BOTTOM + 0.0005)
    well_cut = cutter(well, bevel=0.0010)
    # The slot the trigger hangs out of.
    opening = bmesh.new()
    cuboid(opening, -0.0036, 0.0036, -0.012, 0.012, -0.0450, -0.0300)
    opening_cut = cutter(opening, bevel=0.0010)
    take(body, "lower receiver", "anodised", bevel=0.0010, segments=2, sharp=40.0, cut=(well_cut, opening_cut), texels=1.2)
    for used in (well_cut, opening_cut):
        bpy.data.objects.remove(used)

    guard = bmesh.new()
    sweep(guard, along([(A_WELL_REAR - 0.002, -0.040), (A_WELL_REAR - 0.004, -0.050), (0.010, -0.0575), (-0.012, -0.0575), (-0.026, -0.050), (-0.031, -0.040)], 30),
          0.0050, 0.0030)
    take(guard, "trigger guard", "anodised", bevel=0.0009, segments=2, sharp=50.0)
    trigger = bmesh.new()
    sweep(trigger, along([(0.0050, -0.0300), (0.0042, -0.0380), (0.0018, -0.0450), (-0.0018, -0.0500), (-0.0032, -0.0525)], 18), 0.0028,
          [0.0055 - 0.0025 * n / 17 for n in range(18)])
    take(trigger, "trigger", "phosphate", bevel=0.0008, segments=2, sharp=50.0)

    # The selector on the left over the grip, the magazine's catch on the right, the bolt's catch on the left.
    selector = bmesh.new()
    lathe(selector, [(0.0, 0.0), (0.0068, 0.0), (0.0068, 0.0016), (0.0060, 0.0024), (0.0, 0.0024)],
          Matrix.Translation((-A_UPPER_HALF, -0.030, -0.0215)) @ Matrix.Rotation(math.radians(-90), 4, "Y"), segments=24)
    lever = [(-0.030 - 0.0195 * math.cos(math.radians(a)), -0.0215 + 0.0195 * math.sin(math.radians(a))) for a in (8,)]
    sweep_bm = bmesh.new()
    sweep(sweep_bm, [(-0.030, -0.0215), lever[0]], 0.0010, 0.0050)
    bmesh.ops.translate(sweep_bm, verts=sweep_bm.verts, vec=(-A_UPPER_HALF - 0.0022, 0.0, 0.0))
    take(selector, "selector", "phosphate", bevel=0.0003, segments=1, sharp=40.0)
    take(sweep_bm, "selector lever", "phosphate", bevel=0.0005, segments=2, sharp=40.0)
    button = bmesh.new()
    lathe(button, [(0.0, 0.0), (0.0048, 0.0), (0.0048, 0.0030), (0.0042, 0.0036), (0.0, 0.0036)],
          Matrix.Translation((A_UPPER_HALF, A_WELL_REAR - 0.006, -0.0265)) @ Matrix.Rotation(math.radians(90), 4, "Y"), segments=24)
    take(button, "magazine catch", "phosphate", bevel=0.0003, segments=1, sharp=40.0)
    fence = bmesh.new()
    lathe(fence, [(0.0058, 0.0), (0.0074, 0.0), (0.0074, 0.0022), (0.0058, 0.0022)],
          Matrix.Translation((A_UPPER_HALF - 0.0002, A_WELL_REAR - 0.006, -0.0265)) @ Matrix.Rotation(math.radians(90), 4, "Y"), segments=24)
    take(fence, "catch fence", "anodised", bevel=0.0004, segments=1, sharp=40.0)
    catch = bmesh.new()
    side_plate(catch, [(A_WELL_REAR + 0.004, -0.017), (A_WELL_REAR + 0.022, -0.017), (A_WELL_REAR + 0.022, -0.026), (A_WELL_REAR + 0.012, -0.033),
                       (A_WELL_REAR + 0.004, -0.030)], 0.0014)
    bmesh.ops.translate(catch, verts=catch.verts, vec=(-A_UPPER_HALF - 0.0014, 0.0, 0.0))
    take(catch, "bolt catch", "phosphate", bevel=0.0005, segments=1, sharp=40.0)
    # The pins the two halves are held together by, their heads on both sides.
    pins = bmesh.new()
    for y, z in ((A_UPPER_FRONT - 0.010, -0.0185), (A_UPPER_REAR + 0.014, -0.0190), (-0.008, -0.026), (0.008, -0.026)):
        rod(pins, [(-A_UPPER_HALF - 0.0007, y, z), (A_UPPER_HALF + 0.0007, y, z)], [0.0025, 0.0025], segments=16)
    take(pins, "pins", "phosphate", sharp=50.0)


A_GRIP_FRONT = [(-0.020, -0.026), (-0.030, -0.050), (-0.038, -0.066), (-0.040, -0.074), (-0.044, -0.083), (-0.052, -0.098), (-0.058, -0.110), (-0.060, -0.116)]
A_GRIP_BACK = [(-0.056, -0.024), (-0.058, -0.046), (-0.066, -0.062), (-0.074, -0.077), (-0.081, -0.092), (-0.087, -0.104), (-0.090, -0.112), (-0.090, -0.116)]
A_GRIP_HALF = [0.0125, 0.0136, 0.0140, 0.0141, 0.0142, 0.0144, 0.0143, 0.0138]


def a_furniture():
    grip = bmesh.new()
    stations = along([(fy, fz, by, bz, half) for (fy, fz), (by, bz), half in zip(A_GRIP_FRONT, A_GRIP_BACK, A_GRIP_HALF)], 40)
    # A swell for the middle finger, between the first and the third, on the front edge.
    rings = [grip_ring((fy, fz), (by, bz), half, 2.3, 0.94) for fy, fz, by, bz, half in stations]
    loft(grip, rings)
    take(grip, "grip", "plastic", bevel=0.0010, segments=2, sharp=50.0, texels=1.1)

    stock = bmesh.new()
    keys = [(A_UPPER_REAR + 0.003, 0.0150, -0.030, 0.0160, 3.0, 0.92), (-0.100, 0.0150, -0.034, 0.0172, 3.0, 0.92), (-0.170, 0.0152, -0.050, 0.0180, 3.2, 0.92),
            (-0.260, 0.0155, -0.073, 0.0188, 3.4, 0.92), (-0.340, 0.0158, -0.094, 0.0193, 3.5, 0.92), (A_BUTT + 0.012, 0.0160, -0.103, 0.0195, 3.5, 0.92)]
    loft(stock, [stock_ring(*station) for station in along(keys, 24)])
    take(stock, "stock", "plastic", bevel=0.0012, segments=2, sharp=50.0)
    plate = bmesh.new()
    last = keys[-1]
    loft(plate, [stock_ring(A_BUTT + back, *last[1:], grow=grow) for back, grow in ((0.012, 0.0004), (0.004, 0.0004), (0.0, -0.0004))])
    take(plate, "butt plate", "plastic", bevel=0.0008, segments=2, sharp=40.0)
    swivel = bmesh.new()
    rod(swivel, [(0.0, -0.300 + 0.014 * math.cos(math.radians(a)), -0.0800 - 0.010 * math.sin(math.radians(a))) for a in range(0, 181, 15)],
        [0.0016] * 13, segments=10)
    take(swivel, "rear swivel", "phosphate", sharp=50.0)

    # The handguard: two halves of moulded plastic, round and ribbed, between the ring behind it and the cap before it.
    guard = bmesh.new()
    stations = [(A_HANDGUARD_REAR, 0.0218), (A_HANDGUARD_REAR + 0.004, A_HANDGUARD_R), (A_HANDGUARD_FRONT - 0.006, A_HANDGUARD_R - 0.0010),
                (A_HANDGUARD_FRONT, A_HANDGUARD_R - 0.0030)]
    rings = [ribbed(y, r, 14, 0.0012, 168) for y, r in stations]
    loft(guard, rings)
    bore = cutter(tube(A_HANDGUARD_REAR - 0.01, A_HANDGUARD_FRONT + 0.01, A_BARREL_R + 0.004, segments=24))
    take(guard, "handguard", "handguard", bevel=0.0006, segments=1, sharp=55.0, cut=(bore,))
    bpy.data.objects.remove(bore)
    delta = bmesh.new()
    lathe(delta, [(0.0150, A_UPPER_FRONT), (0.0262, A_UPPER_FRONT), (0.0262, A_UPPER_FRONT + 0.006), (0.0240, A_HANDGUARD_REAR + 0.002),
                  (0.0160, A_HANDGUARD_REAR + 0.002), (0.0150, A_UPPER_FRONT + 0.001)], along_y(), segments=48)
    take(delta, "delta ring", "phosphate", bevel=0.0005, segments=1, sharp=40.0)
    cap = bmesh.new()
    lathe(cap, [(A_BARREL_R + 0.001, A_HANDGUARD_FRONT - 0.002), (A_HANDGUARD_R - 0.0015, A_HANDGUARD_FRONT - 0.002), (A_HANDGUARD_R - 0.0015, A_HANDGUARD_FRONT + 0.006),
                (0.0140, A_HANDGUARD_FRONT + 0.012), (A_BARREL_R + 0.001, A_HANDGUARD_FRONT + 0.012)], along_y(), segments=48)
    take(cap, "handguard cap", "phosphate", bevel=0.0005, segments=1, sharp=40.0)


def a_barrel():
    barrel = bmesh.new()
    lathe(barrel, [(0.0, A_UPPER_FRONT - 0.004), (A_BARREL_R + 0.0012, A_UPPER_FRONT - 0.004), (A_BARREL_R + 0.0012, A_HANDGUARD_FRONT + 0.012),
                   (A_BARREL_R, A_HANDGUARD_FRONT + 0.016), (A_BARREL_R, A_HIDER_FROM + 0.002), (0.0, A_HIDER_FROM + 0.002)], along_y(), segments=40)
    take(barrel, "barrel", "phosphate", sharp=30.0)
    bird_cage(A_HIDER_FROM, MUZZLE_Y, 0.0110, 5, True)

    # The front sight: a block clamped round the barrel with a bayonet's lug under it, and a post on two legs over it
    # between two ears that keep knocks off the post.
    y = A_FRONT_SIGHT_Y
    base = bmesh.new()
    lathe(base, [(A_BARREL_R, y - 0.016), (0.0132, y - 0.016), (0.0140, y - 0.014), (0.0140, y + 0.012), (0.0132, y + 0.014), (A_BARREL_R, y + 0.014)],
          along_y(), segments=40)
    take(base, "front sight base", "phosphate", bevel=0.0004, segments=1, sharp=40.0)
    lug = bmesh.new()
    side_plate(lug, [(y - 0.010, -0.010), (y + 0.012, -0.010), (y + 0.012, -0.0255), (y + 0.006, -0.0270), (y - 0.006, -0.0270), (y - 0.010, -0.0230)], 0.0045)
    take(lug, "bayonet lug", "phosphate", bevel=0.0007, segments=2, sharp=40.0)
    tower = bmesh.new()
    side_plate(tower, [(y - 0.016, 0.008), (y + 0.014, 0.008), (y + 0.006, 0.050), (y + 0.003, 0.052), (y - 0.006, 0.052), (y - 0.008, 0.050)], 0.0072)
    gap = bmesh.new()
    cuboid(gap, -0.0050, 0.0050, y - 0.03, y + 0.03, SIGHT_LINE - 0.018, 0.08)
    gap_cut = cutter(gap, bevel=0.0012)
    tower_hole = bmesh.new()
    side_plate(tower_hole, [(y - 0.007, 0.016), (y + 0.006, 0.016), (y + 0.001, 0.034), (y - 0.004, 0.034)], 0.02)
    hole_cut = cutter(tower_hole)
    take(tower, "front sight tower", "phosphate", bevel=0.0010, segments=2, sharp=40.0, cut=(hole_cut,))
    bpy.data.objects.remove(hole_cut)
    ears = bmesh.new()
    side_plate(ears, [(y - 0.009, 0.046), (y + 0.006, 0.046), (y + 0.004, SIGHT_LINE + 0.0055), (y - 0.005, SIGHT_LINE + 0.0055)], 0.0072)
    take(ears, "sight ears", "phosphate", bevel=0.0008, segments=2, sharp=40.0, cut=(gap_cut,))
    bpy.data.objects.remove(gap_cut)
    post = bmesh.new()
    cuboid(post, -0.0010, 0.0010, y - 0.0012, y + 0.0012, 0.044, SIGHT_LINE)
    take(post, "front post", "sight", bevel=0.0002, segments=1)
    swivel = bmesh.new()
    rod(swivel, [(0.0, y + 0.002 + 0.012 * math.cos(math.radians(a)), -0.018 - 0.012 * math.sin(math.radians(a))) for a in range(0, 181, 15)],
        [0.0015] * 13, segments=10)
    take(swivel, "front swivel", "phosphate", sharp=50.0)


def a_moving():
    # The charging handle: a T behind the receiver under the back of the carrying handle, a latch on its left.
    handle = bmesh.new()
    side_plate(handle, [(A_UPPER_REAR - 0.018, 0.0118), (A_UPPER_REAR + 0.030, 0.0118), (A_UPPER_REAR + 0.030, 0.0195), (A_UPPER_REAR - 0.016, 0.0195),
                        (A_UPPER_REAR - 0.018, 0.0185)], 0.0050)
    take(handle, "charging handle", "anodised", group=HANDLE, bevel=0.0006, segments=1, sharp=40.0)
    tee = bmesh.new()
    keys = [(-0.0235, 0.0), (-0.0170, 0.0030), (-0.0060, 0.0030), (0.0060, 0.0030), (0.0170, 0.0030), (0.0235, 0.0)]
    rings = []
    for x, back in keys:
        rings.append([(x, A_UPPER_REAR - 0.0175 - back + dy, 0.0155 + dz) for dy, dz in ((0.0, -0.0040), (0.0105, -0.0040), (0.0105, 0.0040), (0.0, 0.0040))])
    loft(tee, rings)
    take(tee, "handle tee", "anodised", group=HANDLE, bevel=0.0012, segments=2, sharp=40.0)
    latch = bmesh.new()
    side_plate(latch, [(A_UPPER_REAR - 0.012, 0.0125), (A_UPPER_REAR - 0.002, 0.0125), (A_UPPER_REAR - 0.002, 0.0190), (A_UPPER_REAR - 0.012, 0.0190)], 0.0025)
    bmesh.ops.translate(latch, verts=latch.verts, vec=(-0.0265, 0.0, 0.0))
    take(latch, "handle latch", "anodised", group=HANDLE, bevel=0.0006, segments=1, sharp=40.0)

    # The bolt carrier, which shows in the port, and the bolt's head at its front with its lugs.
    carrier = bmesh.new()
    lathe(carrier, [(0.0, -0.062), (0.0118, -0.062), (0.0124, -0.060), (0.0124, 0.052), (0.0116, 0.054), (0.0090, 0.054), (0.0090, 0.055), (0.0, 0.055)],
          along_y(), segments=36)
    relief = bmesh.new()
    cuboid(relief, 0.0098, 0.02, 0.016, 0.050, -0.007, 0.006)
    relief_cut = cutter(relief, bevel=0.0010)
    take(carrier, "bolt carrier", "phosphate", group=BOLT, bevel=0.0004, segments=1, sharp=40.0, cut=(relief_cut,), texels=1.3)
    bpy.data.objects.remove(relief_cut)
    head = bmesh.new()
    lathe(head, [(0.0, 0.054), (0.0084, 0.054), (0.0084, 0.066), (0.0090, 0.0665), (0.0090, 0.0725), (0.0, 0.0725)], along_y(), segments=36)
    lugs = []
    for n in range(7):
        lug = bmesh.new()
        cuboid(lug, -0.0018, 0.0018, 0.0670, 0.075, 0.0060, 0.012)
        bmesh.ops.transform(lug, matrix=Matrix.Rotation(2 * math.pi * (n + 0.5) / 7, 4, "Y"), verts=lug.verts)
        lugs.append(cutter(lug))
    take(head, "bolt head", "bright", group=BOLT, bevel=0.0002, segments=1, sharp=40.0, cut=lugs, texels=1.3)
    for used in lugs:
        bpy.data.objects.remove(used)

    case(0.0447, 0.0048, 0.00475, 0.0167, 0.0032, 0.0029)

    box_magazine(A_MAG_SPINE, 0.0310, 0.0112, "magazine")


def box_magazine(spine, half_deep, half, material):
    """A box magazine along a spine of (y, z) points from its top to its foot: half_deep from front to back about the
    spine and half wide, ribs down its sides, a floor plate under it and the follower in its top."""
    stations = along(spine, 44 if len(spine) > 2 else 2)
    rings = []
    for n, (y, z) in enumerate(stations):
        before, after = stations[max(n - 1, 0)], stations[min(n + 1, len(stations) - 1)]
        way = Vector((after[0] - before[0], after[1] - before[1])).normalized()
        rings.append(magazine_ring(y, z, Vector((way.y, -way.x)), half_deep, half))
    mag = bmesh.new()
    loft(mag, rings)
    take(mag, "magazine", material, group=MAGAZINE, bevel=0.0007, segments=2, sharp=40.0, texels=1.1)
    end, way, back = magazine_end(spine)
    plate = bmesh.new()
    loft(plate, [magazine_ring(end.x + way.x * d, end.y + way.y * d, back, half_deep + 0.0025 + grow, half + 0.0011 + grow)
                 for d, grow in ((-0.0060, -0.0008), (-0.0040, 0.0), (0.0004, 0.0), (0.0012, -0.0012))])
    take(plate, "floor plate", material, group=MAGAZINE, bevel=0.0008, segments=2, sharp=40.0)
    top_y, top_z = spine[0]
    follower = bmesh.new()
    cuboid(follower, -half + 0.0014, half - 0.0014, top_y - half_deep + 0.004, top_y + half_deep - 0.004, top_z - 0.006, top_z - 0.0012)
    take(follower, "follower", "follower", group=MAGAZINE, bevel=0.0010, segments=2, sharp=40.0)


def magazine_end(spine):
    """Where a magazine's spine ends, the way it runs there and the way to its back, in the y-z plane."""
    (y0, z0), (y1, z1) = spine[-2], spine[-1]
    way = Vector((y1 - y0, z1 - z0)).normalized()
    return Vector((y1, z1)), way, Vector((way.y, -way.x))


def magazine_ring(y, z, back, half_deep, half):
    """A section across a magazine at (y, z) on its spine, square to it: back is the way to its back in the y-z plane.
    Its sides are pressed in along two ribs."""
    points = []
    for i in range(40):
        angle = 2 * math.pi * i / 40
        c, s = math.cos(angle), math.sin(angle)
        across = half * math.copysign(abs(c) ** 0.25, c)
        along_it = half_deep * math.copysign(abs(s) ** 0.25, s)
        # Two ribs pressed out of each side, a third of the way in from front and back.
        rib = 0.0007 * (math.exp(-((along_it / half_deep - 0.33) / 0.10) ** 2) + math.exp(-((along_it / half_deep + 0.33) / 0.10) ** 2)) * abs(c) ** 4
        across += math.copysign(rib, c)
        points.append((across, y + back.x * along_it, z + back.y * along_it))
    return points


# The battle rifle.

B_REC_REAR, B_REC_FRONT, B_RING_FROM = -0.072, 0.140, 0.094
B_REC_HALF, B_WOOD_LINE = 0.0128, -0.0040
B_BRIDGE_TOP = 0.0165
B_REAR_SIGHT_Y = -0.056
B_BARREL_R = 0.0108
# The fore-end and the gas system are as far ahead as the game's longer barrel takes them.
B_FORE_TIP, B_GUARD_FROM, B_GUARD_TO = 0.505, 0.152, 0.472
B_GAS_FROM, B_GAS_TO, B_GAS_Z = 0.525, 0.612, -0.0222
B_SUPPRESSOR_FROM = MUZZLE_Y - 0.083
B_FRONT_SIGHT_Y = MUZZLE_Y - 0.041
B_BUTT = -0.343
B_WELL_REAR, B_WELL_FRONT = 0.040, 0.116
B_MAG_SPINE = [(0.078, -0.008), (0.0785, -0.060), (0.079, -0.118)]
# Where a scope fitted stands on the base over the receiver, from y to y, and how high.
B_SEAT, B_BASE_FROM, B_BASE_TO = SEAT, -0.005, 0.200
B_HANDLE_Y = 0.083


def joined(*bms):
    """One bmesh of several."""
    whole = bmesh.new()
    for part in bms:
        mesh = bpy.data.meshes.new("piece")
        part.to_mesh(mesh)
        part.free()
        whole.from_mesh(mesh)
        bpy.data.meshes.remove(mesh)
    return whole


def b_receiver():
    # The receiver: its sides above the wood, a bridge over the back of it that the rear sight stands on, open over
    # the bolt, and round where the barrel is screwed into its front.
    opening = bmesh.new()
    cuboid(opening, -0.0104, 0.0104, B_REC_REAR + 0.030, B_RING_FROM, -0.002, 0.03)
    opening_cut = cutter(opening, bevel=0.0012)
    body = bmesh.new()
    rings = [ring(B_REC_REAR, B_REC_HALF - 0.002, B_WOOD_LINE - 0.004, B_BRIDGE_TOP - 0.004, 0.004),
             ring(B_REC_REAR + 0.010, B_REC_HALF, B_WOOD_LINE - 0.004, B_BRIDGE_TOP, 0.005),
             ring(B_RING_FROM - 0.006, B_REC_HALF, B_WOOD_LINE - 0.004, B_BRIDGE_TOP, 0.005), ring(B_RING_FROM, 0.0168, -0.0168, 0.0168, 0.0150),
             ring(B_REC_FRONT, 0.0168, -0.0168, 0.0168, 0.0150)]
    loft(body, rings)
    take(body, "receiver", "parkerised", bevel=0.0007, segments=2, sharp=40.0, cut=(opening_cut,), texels=1.25)
    bpy.data.objects.remove(opening_cut)
    # The guide for a clip of rounds, on the front of the bridge.
    guide = bmesh.new()
    cuboid(guide, -0.0090, 0.0090, B_REC_REAR + 0.024, B_REC_REAR + 0.034, B_BRIDGE_TOP - 0.004, B_BRIDGE_TOP + 0.0045)
    take(guide, "clip guide", "parkerised", bevel=0.0008, segments=2, sharp=40.0)

    # The rear sight: an aperture in a block between two ears, raised by a knob on the left and moved over by one on the right.
    y = B_REAR_SIGHT_Y
    base = bmesh.new()
    cuboid(base, -0.0092, 0.0092, y - 0.010, y + 0.008, B_BRIDGE_TOP - 0.002, B_BRIDGE_TOP + 0.004)
    take(base, "rear sight base", "parkerised", bevel=0.0010, segments=2, sharp=40.0)
    sides = []
    for x0, x1 in ((-0.0092, -0.0058), (0.0058, 0.0092)):
        side = bmesh.new()
        side_plate(side, [(y - 0.008, B_BRIDGE_TOP), (y + 0.006, B_BRIDGE_TOP), (y + 0.004, SIGHT_LINE + 0.0070), (y - 0.006, SIGHT_LINE + 0.0070)], (x1 - x0) / 2)
        bmesh.ops.translate(side, verts=side.verts, vec=((x0 + x1) / 2, 0.0, 0.0))
        sides.append(side)
    take(joined(*sides), "sight ears", "parkerised", bevel=0.0008, segments=2, sharp=40.0)
    block = bmesh.new()
    cuboid(block, -0.0046, 0.0046, y - 0.0025, y + 0.0025, B_BRIDGE_TOP + 0.002, SIGHT_LINE + 0.0055)
    peep = cutter(tube(y - 0.01, y + 0.01, 0.0011, segments=20))
    peep.location.z = SIGHT_LINE
    take(block, "rear aperture", "sight", bevel=0.0005, segments=1, cut=(peep,), texels=1.5)
    bpy.data.objects.remove(peep)
    for x, turn, radius, wide in ((-0.0092, -1.0, 0.0100, 0.0058), (0.0092, 1.0, 0.0072, 0.0050)):
        knob = bmesh.new()
        lathe(knob, [(0.0, 0.0), (radius, 0.0), (radius, wide), (radius - 0.0008, wide + 0.0008), (0.0, wide + 0.0008)],
              Matrix.Translation((x, y - 0.001, B_BRIDGE_TOP + 0.009)) @ Matrix.Rotation(math.radians(90 * turn), 4, "Y"), segments=32)
        take(knob, "sight knob", "knurled", bevel=0.0003, segments=1, sharp=40.0)

    # The trigger guard, a steel loop under the stock behind the magazine, with the catch that lets the magazine go.
    guard = bmesh.new()
    sweep(guard, along([(B_WELL_REAR - 0.001, -0.050), (B_WELL_REAR - 0.004, -0.062), (0.020, -0.0705), (-0.008, -0.0705), (-0.026, -0.064), (-0.034, -0.052),
                        (-0.036, -0.048)], 30), 0.0058, 0.0034)
    take(guard, "trigger guard", "parkerised", bevel=0.0010, segments=2, sharp=50.0)
    plate = bmesh.new()
    sweep(plate, [(y0, stock_bottom(y0) + 0.0012) for y0 in (-0.062, -0.040, -0.020, 0.0, 0.020, B_WELL_REAR)], 0.0090, 0.0032)
    take(plate, "guard plate", "parkerised", bevel=0.0010, segments=2, sharp=40.0)
    trigger = bmesh.new()
    sweep(trigger, along([(0.0050, -0.0440), (0.0042, -0.0520), (0.0018, -0.0590), (-0.0018, -0.0630), (-0.0030, -0.0650)], 18), 0.0030,
          [0.0055 - 0.0025 * n / 17 for n in range(18)])
    take(trigger, "trigger", "parkerised", bevel=0.0008, segments=2, sharp=50.0)
    catch = bmesh.new()
    side_plate(catch, [(B_WELL_REAR - 0.002, -0.048), (B_WELL_REAR + 0.002, -0.048), (B_WELL_REAR + 0.001, -0.060), (B_WELL_REAR - 0.006, -0.064),
                       (B_WELL_REAR - 0.007, -0.060)], 0.0050)
    take(catch, "magazine catch", "parkerised", bevel=0.0008, segments=2, sharp=40.0)


# The stock's sections from the butt forward: y, top, bottom, half width, how square, how narrow the comb is against the belly.
B_STOCK = [
    (B_BUTT + 0.006, -0.0230, -0.1440, 0.0205, 2.8, 0.80),
    (-0.300, -0.0200, -0.1330, 0.0205, 2.8, 0.78),
    (-0.240, -0.0160, -0.1170, 0.0200, 2.6, 0.76),
    (-0.180, -0.0135, -0.1020, 0.0192, 2.4, 0.76),
    # The wrist, and the grip it turns down into behind the trigger guard.
    (-0.140, -0.0140, -0.0920, 0.0180, 2.3, 0.80),
    (-0.115, -0.0150, -0.0880, 0.0172, 2.3, 0.86),
    (-0.090, -0.0120, -0.0760, 0.0172, 2.4, 0.90),
    (-0.068, -0.0060, -0.0560, 0.0190, 2.8, 0.96),
    # Up the sides of the receiver and under it, round the well and the trigger guard.
    (-0.050, B_WOOD_LINE, -0.0500, 0.0215, 3.4, 1.0),
    (0.040, B_WOOD_LINE, -0.0505, 0.0222, 3.6, 1.0),
    (0.120, B_WOOD_LINE, -0.0480, 0.0218, 3.6, 1.0),
    # The fore-end, under the barrel.
    (0.230, -0.0050, -0.0430, 0.0200, 3.4, 1.0),
    (0.370, -0.0055, -0.0385, 0.0190, 3.2, 1.0),
    (B_FORE_TIP, -0.0060, -0.0340, 0.0176, 3.0, 1.0),
]


def stock_bottom(y):
    """How low the underside of the stock is at a place along it."""
    stations = along(B_STOCK, 96)
    for a, b in zip(stations, stations[1:]):
        if a[0] <= y <= b[0]:
            return a[2] + (b[2] - a[2]) * (y - a[0]) / (b[0] - a[0])
    raise ValueError("the stock does not reach %s" % y)


def b_stock():
    well = bmesh.new()
    cuboid(well, -0.0150, 0.0150, B_WELL_REAR, B_WELL_FRONT, -0.08, -0.004)
    well_cut = cutter(well, bevel=0.0015)
    channel = bmesh.new()
    cuboid(channel, -0.0112, 0.0112, B_REC_REAR, B_FORE_TIP + 0.01, B_WOOD_LINE - 0.002, 0.02)
    channel_cut = cutter(channel)
    # The operating rod's channel along the right side of the fore-end.
    groove = bmesh.new()
    cuboid(groove, 0.0110, 0.0180, B_RING_FROM - 0.01, B_FORE_TIP - 0.050, -0.0120, 0.02)
    groove_cut = cutter(groove, bevel=0.0010)
    wood = bmesh.new()
    loft(wood, [stock_ring(*station) for station in along(B_STOCK, 64)])
    take(wood, "stock", "walnut", bevel=0.0012, segments=2, sharp=50.0, cut=(well_cut, channel_cut, groove_cut), texels=1.05)
    for used in (well_cut, channel_cut, groove_cut):
        bpy.data.objects.remove(used)
    # The wooden handguard over the barrel, and the steel bands that hold its ends.
    cover = bmesh.new()
    rings = []
    for y, grow in along([(B_GUARD_FROM, -0.0030), (B_GUARD_FROM + 0.012, 0.0), (B_GUARD_TO - 0.020, 0.0), (B_GUARD_TO, -0.0025)], 8):
        points = []
        for i in range(25):
            angle = math.radians(-112 + 224 * i / 24)
            points.append(((0.0172 + grow) * math.sin(angle), y, -0.0010 + (0.0172 + grow) * math.cos(angle)))
        for i in range(24, -1, -1):
            angle = math.radians(-112 + 224 * i / 24)
            points.append(((0.0120 + grow) * math.sin(angle), y, -0.0010 + (0.0120 + grow) * math.cos(angle)))
        rings.append(points)
    loft(cover, rings)
    take(cover, "handguard", "walnut", bevel=0.0008, segments=2, sharp=50.0)
    for y0, y1, r in ((B_GUARD_FROM - 0.004, B_GUARD_FROM + 0.002, 0.0150), (B_GUARD_TO - 0.002, B_GUARD_TO + 0.006, 0.0155)):
        band = bmesh.new()
        lathe(band, [(B_BARREL_R, y0), (r, y0), (r + 0.0012, y0 + 0.001), (r + 0.0012, y1 - 0.001), (r, y1), (B_BARREL_R, y1)], along_y(0.0, 0.0, -0.001), segments=40)
        take(band, "handguard band", "parkerised", bevel=0.0003, segments=1, sharp=40.0)
    # The butt plate, steel, chequered.
    last = B_STOCK[0]
    plate = bmesh.new()
    loft(plate, [stock_ring(B_BUTT + back, *last[1:], grow=grow) for back, grow in ((0.0062, 0.0003), (0.0010, 0.0006), (0.0, 0.0001))])
    take(plate, "butt plate", "buttplate", bevel=0.0008, segments=2, sharp=40.0)
    swivel = bmesh.new()
    rod(swivel, [(0.0, -0.270 + 0.014 * math.cos(math.radians(a)), -0.1215 - 0.010 * math.sin(math.radians(a))) for a in range(0, 181, 15)], [0.0016] * 13, segments=10)
    take(swivel, "rear swivel", "parkerised", sharp=50.0)


def b_barrel():
    barrel = bmesh.new()
    lathe(barrel, [(0.0, B_REC_FRONT - 0.004), (B_BARREL_R + 0.0014, B_REC_FRONT - 0.004), (B_BARREL_R + 0.0014, 0.200), (B_BARREL_R, 0.260),
                   (0.0094, B_SUPPRESSOR_FROM - 0.010), (0.0090, B_SUPPRESSOR_FROM + 0.004), (0.0, B_SUPPRESSOR_FROM + 0.004)], along_y(), segments=40)
    take(barrel, "barrel", "parkerised", sharp=30.0)
    bird_cage(B_SUPPRESSOR_FROM, MUZZLE_Y, 0.0118, 5, True)

    # The gas cylinder under the barrel, held by a band round both, with the stock's tip against its back.
    gas = bmesh.new()
    lathe(gas, [(0.0, B_GAS_FROM), (0.0098, B_GAS_FROM), (0.0102, B_GAS_FROM + 0.004), (0.0102, B_GAS_TO - 0.012), (0.0110, B_GAS_TO - 0.011),
                (0.0110, B_GAS_TO - 0.002), (0.0080, B_GAS_TO), (0.0, B_GAS_TO)], along_y(0.0, 0.0, B_GAS_Z), segments=36)
    take(gas, "gas cylinder", "parkerised", bevel=0.0003, segments=1, sharp=40.0)
    band = bmesh.new()
    side_plate(band, [(B_FORE_TIP, 0.010), (B_FORE_TIP + 0.020, 0.010), (B_FORE_TIP + 0.020, -0.034), (B_FORE_TIP + 0.010, -0.038), (B_FORE_TIP, -0.036)], 0.0135)
    hole = cutter(tube(B_FORE_TIP - 0.01, B_FORE_TIP + 0.03, B_BARREL_R + 0.0005, segments=32))
    take(band, "front band", "parkerised", bevel=0.0018, segments=3, sharp=40.0, cut=(hole,))
    bpy.data.objects.remove(hole)
    lock = bmesh.new()
    side_plate(lock, [(B_GAS_TO - 0.010, 0.000), (B_GAS_TO - 0.001, 0.000), (B_GAS_TO - 0.001, -0.030), (B_GAS_TO - 0.010, -0.030)], 0.0060)
    take(lock, "cylinder lock", "parkerised", bevel=0.0012, segments=2, sharp=40.0)
    swivel = bmesh.new()
    rod(swivel, [(0.0, B_GAS_FROM + 0.040 + 0.012 * math.cos(math.radians(a)), B_GAS_Z - 0.0104 - 0.010 * math.sin(math.radians(a))) for a in range(0, 181, 15)],
        [0.0015] * 13, segments=10)
    take(swivel, "front swivel", "parkerised", sharp=50.0)

    # The front sight, on the flash suppressor: a blade between two ears, and the bayonet's lug under it.
    y = B_FRONT_SIGHT_Y
    base = bmesh.new()
    side_plate(base, [(y - 0.012, 0.0100), (y + 0.012, 0.0100), (y + 0.008, 0.0150), (y - 0.008, 0.0150)], 0.0060)
    take(base, "front sight base", "parkerised", bevel=0.0008, segments=2, sharp=40.0)
    ears = bmesh.new()
    side_plate(ears, [(y - 0.006, 0.0140), (y + 0.006, 0.0140), (y + 0.003, SIGHT_LINE + 0.0040), (y - 0.003, SIGHT_LINE + 0.0040)], 0.0060)
    gap = bmesh.new()
    cuboid(gap, -0.0040, 0.0040, y - 0.03, y + 0.03, 0.0175, 0.08)
    gap_cut = cutter(gap, bevel=0.0008)
    take(ears, "front sight ears", "parkerised", bevel=0.0006, segments=2, sharp=40.0, cut=(gap_cut,))
    bpy.data.objects.remove(gap_cut)
    blade = bmesh.new()
    cuboid(blade, -0.0009, 0.0009, y - 0.0018, y + 0.0018, 0.0150, SIGHT_LINE)
    take(blade, "front blade", "sight", bevel=0.0002, segments=1)
    lug = bmesh.new()
    side_plate(lug, [(B_SUPPRESSOR_FROM + 0.004, -0.009), (B_SUPPRESSOR_FROM + 0.026, -0.009), (B_SUPPRESSOR_FROM + 0.026, -0.0185),
                     (B_SUPPRESSOR_FROM + 0.004, -0.0185)], 0.0040)
    take(lug, "bayonet lug", "parkerised", bevel=0.0008, segments=2, sharp=40.0)


def b_moving():
    # The operating rod along the right side, its handle out over the receiver's front, which every shot sends back.
    bar = bmesh.new()
    rod(bar, [(0.0150, B_HANDLE_Y - 0.020, -0.0020), (0.0150, B_HANDLE_Y + 0.040, -0.0030), (0.0140, B_RING_FROM + 0.080, -0.0080),
              (0.0110, B_FORE_TIP - 0.050, -0.0120), (0.0060, B_GAS_FROM + 0.010, B_GAS_Z + 0.004)], [0.0032, 0.0032, 0.0032, 0.0030, 0.0030], segments=12)
    take(bar, "op rod", "parkerised", group=HANDLE, sharp=40.0)
    handle = bmesh.new()
    rings = []
    for x, rise in ((0.0150, 0.0), (0.0230, 0.0), (0.0300, 0.003), (0.0335, 0.006)):
        rings.append([(x, B_HANDLE_Y + dy, -0.0030 + rise + dz) for dy, dz in ((-0.010, -0.0060), (0.012, -0.0060), (0.012, 0.0070), (-0.010, 0.0070))])
    loft(handle, rings)
    take(handle, "op rod handle", "parkerised", group=HANDLE, bevel=0.0018, segments=2, sharp=40.0)

    # The bolt, seen through the top of the receiver: round, with the roller its lug turns on at the front right.
    bolt = bmesh.new()
    # Drawn right back it comes to the back of the receiver and no further, under the bridge.
    rear = B_REC_REAR + TRAVEL + 0.002
    lathe(bolt, [(0.0, rear), (0.0080, rear), (0.0094, rear + 0.004), (0.0094, B_RING_FROM - 0.002), (0.0, B_RING_FROM - 0.002)],
          along_y(0.0, 0.0, 0.0010), segments=36)
    take(bolt, "bolt", "bright", group=BOLT, bevel=0.0004, segments=1, sharp=40.0, texels=1.3)
    roller = bmesh.new()
    lathe(roller, [(0.0, 0.0), (0.0034, 0.0), (0.0034, 0.0050), (0.0, 0.0050)],
          Matrix.Translation((0.0080, B_RING_FROM - 0.012, 0.0060)) @ Matrix.Rotation(math.radians(90), 4, "Y"), segments=20)
    take(roller, "bolt roller", "bright", group=BOLT, sharp=40.0)

    case(0.0512, 0.0060, 0.0059, 0.0137, 0.0043, 0.0039)
    box_magazine(B_MAG_SPINE, 0.0365, 0.0138, "parkerised")

    # The base a scope is fixed to, over the receiver, held from its left side.
    base = bmesh.new()
    loft(base, [ring(y, 0.0080, B_SEAT - 0.0080, B_SEAT, 0.0012) for y in (B_BASE_FROM, B_BASE_TO)])
    take(base, "scope base", "parkerised", group=MOUNT, bevel=0.0006, segments=1, sharp=40.0)
    leg = bmesh.new()
    side_plate(leg, [(B_BASE_FROM + 0.010, B_SEAT - 0.006), (B_BASE_TO - 0.020, B_SEAT - 0.006), (B_BASE_TO - 0.030, -0.002), (B_BASE_FROM + 0.020, -0.002)], 0.0020)
    bmesh.ops.translate(leg, verts=leg.verts, vec=(-B_REC_HALF - 0.0022, 0.0, 0.0))
    take(leg, "base leg", "parkerised", group=MOUNT, bevel=0.0008, segments=2, sharp=40.0)
    arm = bmesh.new()
    side_plate(arm, [(B_BASE_FROM + 0.010, B_SEAT - 0.0085), (B_BASE_TO - 0.020, B_SEAT - 0.0085), (B_BASE_TO - 0.020, B_SEAT - 0.0050),
                     (B_BASE_FROM + 0.010, B_SEAT - 0.0050)], 0.5 * (B_REC_HALF + 0.0042))
    bmesh.ops.translate(arm, verts=arm.verts, vec=(-0.5 * (B_REC_HALF + 0.0042), 0.0, 0.0))
    take(arm, "base arm", "parkerised", group=MOUNT, bevel=0.0006, segments=1, sharp=40.0)


def build():
    G.clear()
    if BATTLE:
        b_receiver()
        b_stock()
        b_barrel()
        b_moving()
    else:
        a_upper()
        a_lower()
        a_furniture()
        a_barrel()
        a_moving()
    G.count_triangles()


def anodised(g):
    """Light alloy under a hard black coat, a little grey and dull, worn to the pale metal at its edges."""
    return g.finish(*G.black_coat(g, (0.021, 0.021, 0.023), 0.50, 0.45, (0.46, 0.46, 0.48)))


def phosphate(g):
    """Steel in a grey-black phosphate finish: dull and a little grainy, rubbed to the steel only at its sharpest edges."""
    rubbed = g.worn(0.25)
    grain = g.noise(5200.0, 2.0)
    colour = g.mix(rubbed, g.scaled(g.colour(0.032, 0.032, 0.034), g.add(0.85, g.times(grain, 0.3))), g.colour(0.40, 0.40, 0.41))
    return g.finish(colour, g.add(0.58, g.times(grain, 0.12), g.times(rubbed, -0.25)), g.add(0.55, g.times(rubbed, 0.45)), g.times(grain, 0.00002))


def plastic(g):
    """Black moulded plastic with a fine stipple, a little shinier where hands have rubbed its edges."""
    stipple = g.noise(1800.0, 2.0)
    rubbed = g.worn(0.35)
    colour = g.scaled(g.colour(0.016, 0.016, 0.017), g.add(0.9, g.times(stipple, 0.3)))
    return g.finish(colour, g.add(0.62, g.times(stipple, 0.12), g.times(rubbed, -0.22)), 0.0, g.times(stipple, 0.00004))


def handguard(g):
    """The handguard's plastic, with the rows of holes along its top half that let the barrel's heat out."""
    stipple = g.noise(1800.0, 2.0)
    # Six oval holes a side, in two rows, over the upper half, as dark pits.
    angle = g.math("ARCTAN2", g.x, g.z)
    row = g.math("MINIMUM", g.math("ABSOLUTE", g.math("SUBTRACT", g.math("ABSOLUTE", angle), 0.62)), g.math("ABSOLUTE", g.math("SUBTRACT", g.math("ABSOLUTE", angle), 1.05)))
    along_it = g.math("ABSOLUTE", g.math("SUBTRACT", g.math("FRACT", g.times(g.math("SUBTRACT", g.y, A_HANDGUARD_REAR + 0.012), 1.0 / 0.046)), 0.5))
    within = g.times(g.math("LESS_THAN", g.y, A_HANDGUARD_FRONT - 0.03), g.math("GREATER_THAN", g.y, A_HANDGUARD_REAR + 0.02))
    oval = g.add(g.math("POWER", g.times(row, 1.0 / 0.16), 2.0), g.math("POWER", g.times(along_it, 1.0 / 0.30), 2.0))
    hole = g.times(g.math("SUBTRACT", 1.0, g.step(oval, 0.85, 1.15)), within)
    colour = g.scaled(g.colour(0.016, 0.016, 0.017), g.times(g.add(0.9, g.times(stipple, 0.3)), g.math("SUBTRACT", 1.0, g.times(hole, 0.85))))
    return g.finish(colour, g.add(0.62, g.times(stipple, 0.12)), 0.0, g.add(g.times(stipple, 0.00004), g.times(hole, -0.0010)))


def magazine(g):
    """A magazine's pressed alloy, in a dry grey-black coat that wears off its edges sooner than the receiver's."""
    return g.finish(*G.black_coat(g, (0.030, 0.031, 0.033), 0.55, 0.7))


def parkerised(g):
    """Steel in the grey-green phosphate of a service rifle: dull, grainy, a little lighter where it has been handled."""
    rubbed = g.worn(0.35)
    grain = g.noise(5200.0, 2.0)
    colour = g.mix(rubbed, g.scaled(g.colour(0.050, 0.053, 0.048), g.add(0.85, g.times(grain, 0.3))), g.colour(0.36, 0.37, 0.37))
    return g.finish(colour, g.add(0.62, g.times(grain, 0.12), g.times(rubbed, -0.25)), g.add(0.5, g.times(rubbed, 0.5)), g.times(grain, 0.00002))


def buttplate(g):
    """The butt plate's steel, chequered so that it does not slide off a shoulder."""
    colour, roughness, lines = G.steel(g, (0.050, 0.053, 0.048), 0.6, wear=0.8)
    pitch = 0.0022
    one = g.teeth(g.add(g.times(g.x, 0.707 / pitch), g.times(g.z, 0.707 / pitch)))
    other = g.teeth(g.math("SUBTRACT", g.times(g.x, 0.707 / pitch), g.times(g.z, 0.707 / pitch)))
    back = g.step(g.math("SUBTRACT", 0.0, g.ny), 0.6, 0.9)
    return g.finish(colour, roughness, 1.0, g.times(g.math("MINIMUM", one, other), back, 0.0004))


def follower(g):
    return g.finish(g.colour(0.045, 0.060, 0.038), 0.55, 0.0, 0.0)


def sight(g):
    """A sight's own steel: dull black, so that its edges are black against what is aimed at."""
    colour, roughness, lines = G.steel(g, (0.018, 0.019, 0.021), 0.70, wear=0.15)
    return g.finish(colour, roughness, 1.0, g.times(lines, 0.00002))


def knurled(g):
    """A knob's milled edge: fine teeth across it, dull steel."""
    colour, roughness, lines = G.steel(g, (0.030, 0.031, 0.034), 0.55, wear=0.6)
    teeth = g.teeth(g.times(g.math("ARCTAN2", g.y, g.z), 60.0 / math.pi))
    return g.finish(colour, roughness, 1.0, g.times(teeth, 0.00025))


G.MAKERS.update({"anodised": anodised, "phosphate": phosphate, "plastic": plastic, "handguard": handguard, "magazine": magazine, "follower": follower,
                 "sight": sight, "knurled": knurled, "parkerised": parkerised, "walnut": G.walnut, "buttplate": buttplate})
G.TUBES = {"barrel": (0.0, 0.55, B_BARREL_R)} if BATTLE else {"barrel": (0.0, 0.45, A_BARREL_R)}
G.WIDE = True


def grab():
    """Where the hand takes hold of what it works to charge the rifle."""
    return Vector((0.030, B_HANDLE_Y, 0.0)) if BATTLE else Vector((0.0, A_UPPER_REAR - 0.0123, 0.0155))


def mounts():
    """Where the game's red dot sight and laser are fixed to the gun, by the point each is fixed by: the red dot on the
    handle's top or the scope's base, so far back that its clamp, 0.0908 m ahead of that point, is on it; the laser
    under the handguard or the fore-end, the 0.0282 m its top stands over that point below it, its 0.125 m body
    clear ahead of the hand that holds the gun there."""
    if BATTLE:
        return Vector((0.0, B_BASE_FROM + 0.0908 - 0.060, B_SEAT)), Vector((0.0, 0.400, stock_bottom(0.400) - 0.0282))
    return Vector((0.0, -0.002 - 0.0908, A_HANDLE_TOP)), Vector((0.0, 0.329, -A_HANDGUARD_R - 0.0282))


def pack(gun, colour, surface):
    """Writes the gun's files, with its own measures in the head. The mod holds a long gun up as the mirror image of how
    it is laid out here: its right side, with the port, has x less than nought in the file. After the parts come four
    numbers: where the hand takes hold of the charging part, and 1 where the charging part goes back with the bolt on every
    shot (the battle rifle's operating rod), 0 where it does not. Version 6: then the red dot's and laser's points."""
    flip = Vector((-1.0, 1.0, 1.0))
    muzzle = Vector((0.0, MUZZLE_Y, 0.0)) - GRIP
    held = (Vector((0.0, 0.270, -0.040)) if BATTLE else Vector((0.0, (A_HANDGUARD_REAR + A_HANDGUARD_FRONT) / 2, -A_HANDGUARD_R))) - GRIP
    moves = [*((grab() - GRIP) * flip), 1.0 if BATTLE else 0.0]
    red_dot, laser = mounts()
    G.pack(gun, NAME, GRIP, colour, surface, (FIXED, HANDLE, BOLT, CASE, MAGAZINE, MOUNT),
           G.head_v6(muzzle, held, (PORT - GRIP) * flip, TRAVEL, SIGHT_LINE, EYE_BEHIND_GRIP, moves, (red_dot - GRIP) * flip, (laser - GRIP) * flip),
           moved=((CASE, CASE_AT),))


MIDDLE, OFF = (0.20, 2.0)
VIEWS = {
    # name: where the camera is, what it looks at, focal length
    "right": ((OFF, MIDDLE, 0.0), (0.0, MIDDLE, -0.03), 60),
    "left": ((-OFF, MIDDLE, 0.0), (0.0, MIDDLE, -0.03), 60),
    "receiver": ((0.42, 0.02, 0.10), (0.0, 0.04, 0.0), 50),
    "receiver-left": ((-0.40, 0.20, 0.14), (0.0, 0.03, 0.0), 50),
    "under": ((0.30, 0.05, -0.20), (0.0, 0.10, -0.05), 45),
    "fore": ((0.45, 0.62, 0.16), (0.0, 0.40, 0.02), 50),
    "muzzle": ((0.22, MUZZLE_Y + 0.35, 0.16), (0.0, MUZZLE_Y - 0.16, 0.03), 55),
    "sights": ((0.0, -EYE_BEHIND_GRIP, SIGHT_LINE - GRIP.z), (0.0, 0.90, SIGHT_LINE - GRIP.z), 30),
    "hip": ((-0.13, -0.36, 0.20), (0.02, 0.50, 0.02), 22),
    "stock": ((0.50, -0.42, 0.12), (0.0, -0.18, -0.03), 50),
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
        at = args.index("--raw")
        G.render(args[at + 1], VIEWS, [name for name in args[at + 2:] if not name.startswith("--")], samples=24, moved=to_grip)
        return
    gun = G.unwrap(NAME)
    if "--mesh" in args:
        pack(gun, None, None)
        return
    if "--stats" in args:
        G.stats(gun, os.path.join(args[args.index("--stats") + 1], NAME + "-layout.png") if len(args) > args.index("--stats") + 1 else NAME + "-layout.png")
        return
    colour, surface = G.pictures_of(G.bake(gun))
    if "--no-pack" not in args:
        pack(gun, colour, surface)
    if "--renders" in args:
        folder = os.path.abspath(args[args.index("--renders") + 1])
        os.makedirs(folder, exist_ok=True)
        G.dress(gun, colour, surface, folder)
        G.render(folder, VIEWS, moved=to_grip)


main()
