"""What the scripts for the mod's own long guns share beyond gunsmith.py's tools: open sights, fired cases, a
trigger and its guard, a box magazine, sling studs, and the finishes of plain oiled wood, case hardened steel, a
sight's dull steel, a ramp cut across, and black polymer.

It is imported by lever.py, rifle.py, selfloader.py, shotgun.py and toy.py, which run headless in Blender 4.4. Every length is in
metres, in a gun laid out with its bore along +y through x = 0, z = 0 and its top +z, as gunsmith.py says.
"""

import math

import bmesh
import bpy
from mathutils import Matrix, Vector

import gunsmith as G
from gunsmith import along, along_y, cuboid, cutter, lathe, loft, sweep, take


# gunsmith's measures of the game's red dot sight and laser in metres, at the 0.4957 of the game's units to a metre the
# long guns are drawn at.
RED_DOT_CLAMP, RED_DOT_BACK = G.GAME_RED_DOT_CLAMP / 0.4957, G.GAME_RED_DOT_BACK / 0.4957
LASER_TOP, LASER_BACK = G.GAME_LASER_TOP / 0.4957, G.GAME_LASER_BACK / 0.4957


def under_barrel(back, barrel_radius):
    """Where the game's laser is fixed on a barrel by a clamp under it, its back at y = back, from the gun's origin."""
    at = back - LASER_BACK
    return Vector((0.0, at, -barrel_radius(at + 0.05) - LASER_TOP))


def notch_leaf(at, foot, sight_line, name="rear sight leaf"):
    """A rear sight's leaf standing across the barrel at y = at from z = foot: buckhorn ears either side of a U cut
    down to the line of sight."""
    notch = bmesh.new()
    lathe(notch, [(0.0, -0.01), (0.0013, -0.01), (0.0013, 0.01), (0.0, 0.01)], Matrix.Translation((0.0, at, sight_line + 0.0013)) @
          Matrix.Rotation(math.radians(-90), 4, "X"), segments=20)
    cuboid(notch, -0.0013, 0.0013, at - 0.01, at + 0.01, sight_line + 0.0013, sight_line + 0.01)
    notch_cut = cutter(notch)
    leaf = bmesh.new()
    G.prism(leaf, [(-0.0090, foot), (0.0090, foot), (0.0090, sight_line + 0.0010), (0.0074, sight_line + 0.0048), (0.0040, sight_line + 0.0034),
                  (-0.0040, sight_line + 0.0034), (-0.0074, sight_line + 0.0048), (-0.0090, sight_line + 0.0010)], "y", at - 0.0008, at + 0.0008)
    take(leaf, name, "sight", bevel=0.0002, segments=1, cut=(notch_cut,))
    bpy.data.objects.remove(notch_cut)


def ramp_and_blade(front, foot, sight_line, hood=False):
    """A front sight: a ramp standing on the barrel whose top is at z = foot at y = front, cut across so that it does
    not shine, a blade on it to the line of sight with a brass bead let into its face, and if asked a hood over it."""
    ramp = bmesh.new()
    loft(ramp, [[(-half, y, foot - 0.0020), (half, y, foot - 0.0020), (half * 0.7, y, top), (-half * 0.7, y, top)]
                for y, half, top in ((front - 0.034, 0.0028, foot - 0.0010), (front - 0.010, 0.0036, foot + 0.0050), (front + 0.008, 0.0036, foot + 0.0054),
                                     (front + 0.011, 0.0032, foot + 0.0020))])
    take(ramp, "front sight ramp", "ramp", bevel=0.0004, segments=1)
    blade = bmesh.new()
    G.prism(blade, [(front - 0.006, foot + 0.0045), (front + 0.006, foot + 0.0045), (front + 0.005, sight_line - 0.0004), (front + 0.001, sight_line),
                 (front - 0.004, sight_line - 0.0004)][::-1], "x", -0.0008, 0.0008)
    take(blade, "front sight blade", "sight", bevel=0.0002, segments=1)
    bead = bmesh.new()
    lathe(bead, [(0.0, 0.0), (0.0010, 0.0), (0.0010, 0.0007), (0.0, 0.0009)], Matrix.Translation((0.0, front - 0.0045, sight_line - 0.0011)) @
          Matrix.Rotation(math.radians(90), 4, "X"), segments=14)
    take(bead, "front bead", "brass", sharp=50.0)
    if hood:
        cover = bmesh.new()
        rings = []
        for y in (front - 0.010, front + 0.010):
            rings.append([(0.0062 * math.cos(math.radians(a)), y, foot + 0.0046 + 0.0062 * math.sin(math.radians(a))) for a in range(-20, 201, 11)]
                         + [(0.0054 * math.cos(math.radians(a)), y, foot + 0.0046 + 0.0054 * math.sin(math.radians(a))) for a in range(200, -21, -11)])
        loft(cover, rings)
        take(cover, "front sight hood", "blued", bevel=0.0002, segments=1, sharp=40.0)


def case(at, profile, group):
    """A fired case, turned from its profile of (radius, along) about its own middle, made well clear of the gun at `at`."""
    brass = bmesh.new()
    lathe(brass, profile, along_y(at.x, at.y, at.z), segments=28)
    take(brass, "case", "brass", group=group, sharp=30.0)


# Profiles of fired cases, (radius, along) from the head to the mouth and back down its inside.
RIMMED_BOTTLE = [(0.0, -0.0256), (0.0076, -0.0256), (0.0076, -0.0241), (0.0064, -0.0239), (0.0061, 0.0100), (0.0044, 0.0160), (0.0041, 0.0256),
                 (0.0037, 0.0256), (0.0037, 0.0120), (0.0, 0.0120)]
RIMMED_STRAIGHT = [(0.0, -0.0165), (0.0069, -0.0165), (0.0069, -0.0151), (0.0060, -0.0149), (0.0060, 0.0165), (0.0055, 0.0165), (0.0055, -0.0120),
                   (0.0, -0.0120)]
RIMLESS_SMALL = [(0.0, -0.0223), (0.0047, -0.0223), (0.0048, -0.0215), (0.0040, -0.0210), (0.0040, -0.0200), (0.0048, -0.0195), (0.0047, 0.0085),
                 (0.0031, 0.0125), (0.0029, 0.0223), (0.0026, 0.0223), (0.0026, 0.0100), (0.0, 0.0100)]
RIMLESS_PISTOL = [(0.0, -0.0114), (0.0060, -0.0114), (0.0061, -0.0106), (0.0052, -0.0101), (0.0052, -0.0094), (0.0060, -0.0089), (0.0060, 0.0114),
                  (0.0056, 0.0114), (0.0056, -0.0080), (0.0, -0.0080)]


def trigger(top, foot, ahead=0.0, name="trigger", material="bright"):
    """A curved trigger blade from z = top down to its toe at z = foot, its face at y = ahead."""
    bm = bmesh.new()
    drop = top - foot
    path = along([(ahead + 0.006, top), (ahead + 0.005, top - 0.36 * drop), (ahead + 0.002, top - 0.62 * drop), (ahead - 0.003, top - 0.84 * drop),
                  (ahead - 0.004, top - 0.95 * drop), (ahead - 0.002, foot)], 20)
    sweep(bm, path, 0.0030, [0.0058 - 0.0026 * n / 19 for n in range(20)])
    take(bm, name, material, bevel=0.0009, segments=2, sharp=50.0)


def guard(front, back, top, low, material="blued", name="trigger guard"):
    """A trigger guard: a bow from the front of its plate at y = front, down to z = low and back up to y = back."""
    bm = bmesh.new()
    deep = top - low
    long = front - back
    path = along([(front, top), (front + 0.003, top - 0.25 * deep), (front - 0.0015, top - 0.55 * deep), (front - 0.012, top - 0.85 * deep),
                  (front - 0.30 * long, low), (back + 0.30 * long, low + 0.04 * deep), (back + 0.08 * long, low + 0.22 * deep), (back, top - 0.45 * deep),
                  (back + 0.004, top)], 44)
    sweep(bm, path, 0.0064, [0.0032 + 0.0014 * abs(n / 43 - 0.5) * 2 for n in range(44)])
    take(bm, name, material, bevel=0.0011, segments=3, sharp=50.0)


def magazine(top, front, back, half, foot, lean=0.0, curve=0.0, name="magazine", group=0, material="black", floor="black"):
    """A box magazine from z = top down to z = foot, front to back as wide as front - back, half wide, its front
    leaning forward by `lean` metres at the foot and bowed forward in the middle by `curve`, with a floor plate."""
    rings = []
    count = 12
    for n in range(count + 1):
        t = n / count
        z = top + (foot - top) * t
        shift = lean * t + curve * math.sin(math.pi * t) * 0.5 + curve * t * t
        ring = []
        for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
            ring.append((sx * half, (front if sy > 0 else back) + shift, z))
        rings.append(ring)
    bm = bmesh.new()
    loft(bm, rings)
    take(bm, name, material, group=group, bevel=0.0012, segments=2, sharp=40.0)
    plate = bmesh.new()
    shift = lean + curve
    loft(plate, [[(sx * (half + grow), (front + grow if sy > 0 else back - grow) + shift, z) for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
                 for z, grow in ((foot + 0.002, 0.0006), (foot - 0.004, 0.0010), (foot - 0.0052, 0.0004))])
    take(plate, name + " floor", floor, group=group, bevel=0.0010, segments=2, sharp=40.0)


def stud(bm, y, z, tilt):
    """A sling swivel's stud screwed into the wood at (y, z), standing out of it at `tilt` degrees off straight down."""
    profile = [(0.0, 0.0), (0.0042, 0.0), (0.0042, 0.002), (0.0028, 0.003), (0.0028, 0.0075), (0.0046, 0.0085), (0.0046, 0.0125), (0.003, 0.014), (0.0, 0.014)]
    lathe(bm, profile, Matrix.Translation((0.0, y, z)) @ Matrix.Rotation(math.radians(180 - tilt), 4, "X"), segments=20)


def wood(tint, span=0.30):
    """Plain oiled wood: the walnut picture laid along the gun, tinted."""
    def make(g):
        figure = g.laid("walnut", span)
        light = g.grey(figure)
        colour = g.tinted(g.mix(0.6, figure, g.tinted(figure, figure)), g.colour(*tint))
        rough = g.add(0.30, g.times(g.math("SUBTRACT", 1.0, light), 0.16))
        return g.finish(colour, rough, 0.0, g.times(light, 0.00006))
    return make


def hardened(g):
    """Steel case hardened in bone charcoal: faint blues, straws and browns swirled through a dark grey, worn silver at the edges."""
    lines = g.streaks()
    rubbed = g.worn(0.8)
    broad, fine = g.noise(55.0, 4.0), g.noise(230.0, 3.0)
    swirl = g.mix(g.step(broad, 0.40, 0.66), g.colour(0.15, 0.11, 0.06), g.colour(0.045, 0.06, 0.13))
    swirl = g.mix(g.times(g.step(fine, 0.55, 0.72), 0.5), swirl, g.colour(0.10, 0.06, 0.10))
    colour = g.mix(0.45, g.colour(0.085, 0.085, 0.090), swirl)
    colour = g.mix(rubbed, g.scaled(colour, g.add(1.0, g.times(lines, 0.4))), g.colour(0.46, 0.46, 0.47))
    return g.finish(colour, g.add(0.22, g.times(lines, 0.2), g.times(rubbed, 0.05)), 1.0, g.times(lines, 0.00002))


def sight(g):
    """A sight's own steel: blued and left dull, so that its edges are black against what is aimed at."""
    colour, roughness, lines = G.steel(g, (0.020, 0.022, 0.028), 0.62, wear=0.25)
    return g.finish(colour, roughness, 1.0, g.times(lines, 0.00002))


def ramp(g):
    colour, roughness, lines = G.steel(g, (0.026, 0.028, 0.036), 0.50, wear=0.5)
    top = g.step(g.nz, 0.35, 0.8)
    return g.finish(colour, roughness, 1.0, g.times(top, g.teeth(g.times(g.y, 1.0 / 0.0012)), 0.00014))


def polymer(g):
    """Black glass filled polymer, moulded with a fine pebbled texture and rubbed smooth and a little shiny where hands go."""
    pebble = g.noise(1900.0, 2.0)
    rubbed = g.worn(0.5)
    colour = g.scaled(g.colour(0.022, 0.022, 0.024), g.add(0.9, g.times(pebble, 0.25), g.times(rubbed, 0.6)))
    return g.finish(colour, g.math("SUBTRACT", g.add(0.62, g.times(pebble, 0.10)), g.times(rubbed, 0.22)), 0.0, g.times(pebble, 0.00005))


G.MAKERS.update({"sight": sight, "ramp": ramp, "hardened": hardened, "polymer": polymer})
