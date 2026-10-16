"""The interior of the Chevalier Cossette and the race cars built on its kind: a low two-seat sports car of the
eighties. A deep cockpit: a hooded pod of instruments before the driver with the engine's speed beside the road's,
a three-spoke wheel rimmed in leather, a high console between the seats with the gear lever and the hand brake,
low buckets with deep bolsters, and a carpeted load space under the glass hatch. The race car strips the trim, cages
the cockpit in steel tube and seats the driver in a moulded racing shell.
"""

import math

import bpy
from mathutils import Matrix

from . import compact, kit, luxury, materials, sedan, shell, upholstery
from .kit import NEEDLE_REVS, NEEDLE_SPEED, SHELL, Builder, circle, lerp, rounded_rect, smooth, spline, v
from .sedan import FLOOR_Z, SEAT_Z

# The road car's binnacle: the speedometer and the revs side by side, fuel and heat to the left, oil and volts to the right.
ROAD = {"speedo": (v(0.560, 0.060, -0.242), 0.048), "revs": (v(0.560, -0.062, -0.242), 0.048), "combi": (v(0.562, 0.172, -0.248), 0.032),
        "oil": (v(0.562, -0.174, -0.248), 0.032)}
# The race car's: a large revs dead ahead on the column, oil and volts beside it, a small speedometer out to the right.
RACE = {"speedo": (v(0.562, -0.196, -0.250), 0.026), "revs": (v(0.550, 0.0, -0.252), 0.050), "combi": (v(0.562, 0.125, -0.248), 0.032),
        "oil": (v(0.562, -0.118, -0.248), 0.030)}


def dials(race=False):
    return RACE if race else ROAD


def needles(race=False):
    d = dials(race)
    (speedo, rs), (revs, rr), (combi, rc) = d["speedo"], d["revs"], d["combi"]
    return [(NEEDLE_SPEED, speedo, rs, 0.92, sedan.SPEED_SWEEP), (NEEDLE_REVS, revs, rr, 0.92, (math.radians(-130), math.radians(130), 1.0)),
            *sedan.combi_needles(combi, rc)]


def dash(house, end_gap=0.030):
    """A low dash under the steeply raked screen, a deep hood over the instruments before the driver, and the
    console rising out of it between the seats."""
    s = house.side
    inner = s["sill_half"] - end_gap
    rings = []
    for k in range(57):
        out = lerp(inner, -inner, k / 56)
        y = house.y(out)
        xf, zf = house.edge("foot", abs(out))
        xf -= 0.01
        top = max(zf + 0.004, -0.235)
        normal = [(xf + 0.05, zf - 0.07), (xf + 0.004, zf + 0.001), (lerp(xf, 0.53, 0.5), top + 0.01), (0.56, top + 0.006), (0.535, top - 0.008),
                  (0.522, top - 0.03), (0.52, -0.31), (0.524, -0.37), (0.54, -0.41), (0.57, -0.43), (0.68, -0.45), (0.82, -0.36)]
        hood = [(xf + 0.05, zf - 0.07), (xf + 0.004, zf + 0.001), (0.62, -0.170), (0.53, -0.148), (0.505, -0.155), (0.495, -0.170), (0.505, -0.185),
                (0.575, -0.19), (0.58, -0.27), (0.524, -0.37), (0.54, -0.41), (0.57, -0.43), (0.68, -0.45), (0.82, -0.36)]
        w = smooth((0.28 - abs(y)) / 0.05) if abs(y) < 0.28 else 0.0
        rings.append([v(x, y, z) for (x, z) in sedan.blend_sections(normal, hood, w)])
    b = Builder()
    b.loft(rings, closed=True, caps=True)
    return b.finish("dashboard", "padded", SHELL, subdivide=2, coarsest=1, texels=1.5, sharp=50)


def instruments(house, vents=True):
    parts = []
    b = Builder()
    b.slab(rounded_rect(0.45 if vents else 0.47, 0.10, 0.025), v(0.578, -0.001 if vents else -0.008, -0.245), (0, -1, 0), (0, 0, 1), 0.004)
    parts.append(b.finish("instrument panel", "black", SHELL, texels=1.5, smooth_shade=False))
    d = dials(not vents)
    for name, (c, r), picture in (("speedometer", d["speedo"], "speedometer"), ("revs", d["revs"], "revs"), ("fuel and heat", d["combi"], "fuel-heat"),
                                  ("oil and volts", d["oil"], "oil-volts")):
        parts += sedan.instrument(name, c, r, picture)
    for group, pivot, radius, length, _ in needles(not vents):
        parts += sedan.needle(group, pivot, radius, length)
    # The oil pressure's and the volts' needles are not the mod's to turn: they stand at a running engine's reading.
    oil, r_oil = d["oil"]
    for side, angle in ((1, -90.0), (-1, 77.0)):
        pivot = oil + v(0, side * r_oil * sedan.COMBI_PIVOT, 0)
        turn = Matrix.Translation(pivot) @ Matrix.Rotation(math.radians(angle), 4, "X") @ Matrix.Translation(-pivot)
        for p in sedan.needle(SHELL, pivot, r_oil, sedan.COMBI_LENGTH):
            p.data.transform(turn)
            parts.append(p)
    s = house.side
    for out in (s["sill_half"] - 0.08, -(s["sill_half"] - 0.08)) if vents else ():
        parts += compact.eyeball(v(0.524, house.y(out - math.copysign(0.058, out)), -0.285), (-1, 0, 0), 0.028)
    if not vents:
        # Stripped for the track: no glove box, and a shift light over the instruments on a riveted plate.
        b = Builder()
        b.slab(rounded_rect(0.07, 0.035, 0.01), v(0.585, 0.0, -0.146), (0, -1, 0), (0, 0, 1), 0.03)
        parts.append(b.finish("shift light pod", "black", SHELL, bevel=0.004, texels=1.4))
        b = Builder()
        b.lathe([(0.0, 0.0), (0.012, 0.0), (0.012, 0.004), (0.010, 0.006), (0.0, 0.006)], v(0.555, 0.0, -0.146), v(-1, 0, 0), count=20)
        parts.append(b.finish("shift light", "lamp", SHELL, texels=2.0))
        b = Builder()
        for su in (-1, 1):
            for sw in (-1, 1):
                b.lathe([(0.0, 0.0), (0.003, 0.0), (0.0025, 0.0012), (0.0, 0.0016)], v(0.574, -0.008 + su * 0.225, -0.245 + sw * 0.042), v(-1, 0, 0), count=8)
        parts.append(b.finish("instrument plate rivets", "chrome", SHELL, texels=2.0, smooth_shade=False))
        parts += sedan.demisters(house, 0.53)
        return parts
    # A pair of slatted registers on the face between the instruments' hood and the glove box, over the stack.
    for out in (-0.05, -0.155):
        parts += sedan.vent(v(0.514, house.y(out), -0.29), (0, 1, 0), 0.09, 0.045)
    # The glove box's lid before the passenger, and the panel closing the space under the dash's end.
    gy = house.y(-0.325)
    b = Builder()
    b.slab(rounded_rect(0.17, 0.10, 0.012), v(0.519, gy, -0.315), (0, -1, 0), (0, 0, 1), 0.002)
    parts.append(b.finish("glove box", "plastic", SHELL, bevel=0.0008, texels=1.0))
    b = Builder()
    b.slab(rounded_rect(0.176, 0.106, 0.015), v(0.5205, gy, -0.315), (0, -1, 0), (0, 0, 1), 0.002)
    parts.append(b.finish("glove box shut line", "black", SHELL, texels=1.0, smooth_shade=False))
    b = Builder()
    b.slab(rounded_rect(0.05, 0.016, 0.006), v(0.512, gy, -0.275), (0, -1, 0), (0, 0, 1), 0.006)
    parts.append(b.finish("glove box handle", "black", SHELL, bevel=0.002, texels=1.6))
    # The demister's grille along the windscreen's foot.
    parts += sedan.demisters(house, 0.53)
    return parts


def console(house, race=False):
    """A high console from under the dash back between the seats: the radio and the heater's knobs in its sloped head,
    the gear lever in its boot, the hand brake, and an armrest lid at its end."""
    if race:
        return stripped_controls(house)
    parts = []
    mid = house.mid
    b = Builder()
    rings = []
    for x, h, w in ((0.60, 0.455, 0.24), (0.49, 0.455, 0.24), (0.472, 0.43, 0.24), (0.469, 0.33, 0.245), (0.44, 0.30, 0.25), (0.38, 0.29, 0.24), (0.10, 0.28, 0.23), (-0.16, 0.30, 0.22),
                    (-0.26, 0.24, 0.20)):
        rings.append([v(x, mid + u * (1.0 + 0.14 * (0.5 - wv / h)), FLOOR_Z + 0.04 + h * 0.5 + wv) for (u, wv) in rounded_rect(w, h, 0.025, 6)])
    b.loft(rings, closed=True, caps=True)
    parts.append(b.finish("console", "plastic", SHELL, subdivide=1, texels=0.9))
    b = Builder()
    up, out = v(-0.2, 0, 1.0).normalized(), v(-1.0, 0, -0.2).normalized()
    face_at = v(0.452, mid, -0.40)
    b = Builder()
    b.slab(rounded_rect(0.18, 0.13, 0.012), face_at, (0, -1, 0), up, 0.006)
    parts.append(b.finish("console face", "black", SHELL, bevel=0.002, texels=1.6))
    parts.append(sedan.surround("console face surround", "plastic", face_at + out * 0.006, (0, -1, 0), up, 0.18, 0.13, 0.006, lip=0.012))
    # On the face: the radio over the heater's three knobs, each in a recess of its own; stripped for the track, the
    # radio's opening blanked and the switches and the starter's button in its place.
    centre = face_at + out * 0.001
    if race:
        parts += switch_panel(centre, up, out)
    else:
        radio = centre + up * 0.033
        b = Builder()
        b.slab(rounded_rect(0.15, 0.042, 0.004), radio, (0, -1, 0), up, 0.005)
        parts.append(b.finish("radio face", "black", SHELL, bevel=0.0015, texels=2.5))
        parts.append(sedan.surround("radio surround", "plastic", radio, (0, -1, 0), up, 0.15, 0.042, 0.004, lip=0.004))
        b = Builder()
        b.slab(rounded_rect(0.07, 0.014, 0.002), radio + out * 0.0055 + up * 0.006, (0, -1, 0), up, 0.0015)
        parts.append(b.finish("radio window", "lens", SHELL, texels=3.0, smooth_shade=False))
        for dy in (-0.058, 0.058):
            b = Builder()
            b.lathe([(0.0, 0.0), (0.009, 0.0), (0.009, 0.009), (0.0075, 0.014), (0.0, 0.015)], radio + out * 0.005 + v(0, dy, 0), out, count=20)
            parts.append(b.finish("radio knob", "chrome", SHELL, texels=2.0))
        heater = centre - up * 0.034
        b = Builder()
        b.slab(rounded_rect(0.15, 0.05, 0.004), heater, (0, -1, 0), up, 0.004)
        parts.append(b.finish("heater panel", "black", SHELL, bevel=0.0012, texels=2.0))
        parts.append(sedan.surround("heater surround", "plastic", heater, (0, -1, 0), up, 0.15, 0.05, 0.004, lip=0.004))
        for k in range(3):
            b = Builder()
            knob_at = heater + out * 0.004 + up * 0.004 + v(0, lerp(0.05, -0.05, k / 2), 0)
            b.lathe([(0.0, 0.0), (0.0125, 0.0), (0.0125, 0.011), (0.0105, 0.015), (0.0, 0.016)], knob_at, out, count=24)
            parts.append(b.finish("heater knob", "plastic", SHELL, subdivide=1, texels=2.0))
            b = Builder()
            b.slab(rounded_rect(0.003, 0.014, 0.001), knob_at + out * 0.0162 + up * 0.004, (0, -1, 0), up, 0.001)
            parts.append(b.finish("heater knob mark", "lens", SHELL, texels=2.0, smooth_shade=False))
    top_z = FLOOR_Z + 0.04 + 0.29
    base = v(0.18, mid, top_z)
    # The gear lever's boot in a bezel let into the console's top, and an ash tray's lid ahead of it.
    parts.append(sedan.surround("shift surround", "black", base + v(0, 0, 0.004), (1, 0, 0), (0, 1, 0), 0.125, 0.11, 0.004, lip=0.006, radius=0.02))
    parts.append(sedan.surround("ash tray", "black", v(0.33, mid, FLOOR_Z + 0.04 + 0.293), (1, 0, 0), (0, 1, 0), 0.07, 0.10, 0.003, lip=0.005, radius=0.01))
    # The lid's finger pull along its rear edge.
    b = Builder()
    b.slab(rounded_rect(0.008, 0.04, 0.003), v(0.302, mid, FLOOR_Z + 0.04 + 0.296), (1, 0, 0), (0, 1, 0), 0.006)
    parts.append(b.finish("ash tray pull", "chrome", SHELL, bevel=0.002, texels=1.6))
    b = Builder()
    b.lathe([(0.05, 0.0), (0.047, 0.008), (0.048, 0.016), (0.041, 0.026), (0.042, 0.034), (0.034, 0.044), (0.035, 0.052), (0.025, 0.062), (0.024, 0.069), (0.013, 0.076), (0.010, 0.08)], base, v(-0.1, 0, 1), count=24)
    parts.append(b.finish("gear boot", "leather", SHELL, subdivide=1, texels=1.2))
    top = base + v(-0.04, 0, 0.17)
    b = Builder()
    b.sweep(spline([base + v(-0.008, 0, 0.07), top], 6), lambda i: circle(0.006, 12), ups=[v(1, 0, 0)] * 6)
    parts.append(b.finish("gear lever", "chrome", SHELL, texels=1.5))
    b = Builder()
    b.lathe([(0.0, -0.004), (0.017, 0.0), (0.022, 0.014), (0.021, 0.028), (0.014, 0.036), (0.0, 0.039)], top, v(-0.1, 0, 1), count=24)
    parts.append(b.finish("gear knob", "leather", SHELL, subdivide=1, texels=2.0))
    pivot = v(-0.04, mid - 0.06, top_z + 0.005)
    b = Builder()
    b.sweep(spline([pivot, pivot + v(0.11, 0, 0.03), pivot + v(0.20, 0, 0.05)], 8), lambda i: rounded_rect(0.022, 0.024, 0.009), ups=[v(0, 0, 1)] * 8)
    parts.append(b.finish("hand brake", "black", SHELL, subdivide=1, texels=1.4))
    parts.append(upholstery.block("console lid", "leather", v(-0.18, mid, top_z + 0.03), (0, 1, 0), (1, 0, 0), (0, 0, 1), 0.20, 0.22, 0.045, texels=0.9))
    return parts


def stripped_controls(house):
    """A stripped car keeps no console: the gear lever in a rubber boot on the bare tunnel, the hand brake beside it,
    and the switches on a plate fixed to the dash's face."""
    parts = []
    mid = house.mid
    parts += switch_panel(v(0.515, mid - 0.07, -0.355), v(0, 0, 1), v(-1, 0, 0))
    base = v(0.20, mid, FLOOR_Z + 0.17)
    b = Builder()
    b.lathe([(0.055, 0.0), (0.052, 0.009), (0.053, 0.018), (0.045, 0.029), (0.046, 0.038), (0.037, 0.049), (0.038, 0.058), (0.027, 0.068), (0.026, 0.076),
             (0.015, 0.083), (0.013, 0.087)], base, v(-0.1, 0, 1), count=24)
    parts.append(b.finish("gear boot", "rubber", SHELL, subdivide=1, texels=1.2))
    top = base + v(-0.05, 0, 0.30)
    b = Builder()
    b.sweep(spline([base + v(-0.008, 0, 0.08), base + v(-0.03, 0, 0.2), top], 10), lambda i: circle(0.0075, 12), ups=[v(1, 0, 0)] * 10)
    parts.append(b.finish("gear lever", "chrome", SHELL, texels=1.5))
    b = Builder()
    b.lathe([(0.0, -0.005), (0.016, 0.0), (0.021, 0.012), (0.021, 0.025), (0.016, 0.036), (0.0, 0.04)], top, v(-0.15, 0, 1), count=24)
    parts.append(b.finish("gear knob", "black", SHELL, subdivide=1, texels=2.0))
    pivot = v(-0.02, mid + 0.07, FLOOR_Z + 0.17)
    b = Builder()
    b.sweep(spline([pivot, pivot + v(0.11, 0, 0.05), pivot + v(0.20, 0, 0.08)], 8), lambda i: rounded_rect(0.022, 0.024, 0.009), ups=[v(0, 0, 1)] * 8)
    parts.append(b.finish("hand brake", "black", SHELL, subdivide=1, texels=1.4))
    return parts


def switch_panel(centre, up, out):
    """A plate over the face with a row of toggles, the starter's button and the master switch's key."""
    parts = []
    b = Builder()
    b.slab(rounded_rect(0.17, 0.10, 0.008), centre + out * 0.004, (0, -1, 0), up, 0.003)
    parts.append(b.finish("switch panel", "black", SHELL, bevel=0.001, texels=2.0))
    b = Builder()
    across = up.cross(out)
    for su in (-1, 1):
        for sw in (-1, 1):
            b.lathe([(0.0, 0.0), (0.003, 0.0), (0.0025, 0.0012), (0.0, 0.0016)], centre + out * 0.007 + across * (su * 0.075) + up * (sw * 0.04), out, count=8)
    parts.append(b.finish("switch panel rivets", "chrome", SHELL, texels=2.0, smooth_shade=False))
    for k in range(5):
        at = centre + out * 0.007 + up * 0.022 + v(0, lerp(0.065, -0.065, k / 4), 0)
        b = Builder()
        b.lathe([(0.0, 0.0), (0.006, 0.0), (0.006, 0.004), (0.0, 0.005)], at, out, count=16)
        parts.append(b.finish("toggle collar", "chrome", SHELL, texels=2.0))
        b = Builder()
        b.sweep([at + out * 0.004, at + out * 0.022 + up * 0.006], circle(0.0022, 8), ups=[up] * 2)
        parts.append(b.finish("toggle", "chrome", SHELL, texels=2.0))
    for dy, name in ((0.04, "starter"), (-0.04, "master switch")):
        at = centre + out * 0.007 - up * 0.022 + v(0, dy, 0)
        b = Builder()
        b.lathe([(0.0, 0.0), (0.016, 0.0), (0.016, 0.006), (0.013, 0.008), (0.0, 0.008)], at, out, count=24)
        parts.append(b.finish(name + " bezel", "chrome", SHELL, texels=2.0))
        b = Builder()
        b.lathe([(0.0, 0.0), (0.011, 0.0), (0.011, 0.012), (0.009, 0.015), (0.0, 0.016)], at + out * 0.004, out, count=24)
        parts.append(b.finish(name + " button", "black", SHELL, subdivide=1, texels=2.0))
    return parts


def extinguisher(house):
    """A fire extinguisher strapped in a bracket on the tunnel ahead of the passenger's seat."""
    parts = []
    at = v(0.30, house.mid - 0.14, FLOOR_Z + 0.075)
    b = Builder()
    b.lathe([(0.0, -0.17), (0.045, -0.165), (0.05, -0.15), (0.05, 0.12), (0.042, 0.15), (0.02, 0.165), (0.0, 0.17)], at, (1, 0, 0), count=24)
    parts.append(b.finish("extinguisher", "chrome", SHELL, subdivide=1, texels=1.0))
    b = Builder()
    b.lathe([(0.0, 0.0), (0.018, 0.0), (0.018, 0.04), (0.0, 0.045)], at + v(0.17, 0, 0), (1, 0, 0), count=16)
    parts.append(b.finish("extinguisher head", "black", SHELL, texels=1.2))
    for dx in (-0.09, 0.07):
        b = Builder()
        b.sweep(spline([at + v(dx, 0.06, -0.06), at + v(dx, 0.055, 0.03), at + v(dx, 0.0, 0.056), at + v(dx, -0.055, 0.03), at + v(dx, -0.06, -0.06)], 16),
                rounded_rect(0.025, 0.003, 0.001), ups=[v(1, 0, 0)] * 16)
        parts.append(b.finish("extinguisher strap", "black", SHELL, texels=1.0))
    return parts


def racing_seat(house, y, name):
    """A moulded racing shell narrow enough to stand clear of its neighbour, its sides rising into shoulder and head
    wings, padded in black cloth where the driver touches it, on a bracket each side to a rail on the floor."""
    parts = []
    lean = math.radians(14)
    up = v(-math.sin(lean), 0, math.cos(lean))
    facing = v(math.cos(lean), 0, math.sin(lean))
    b = Builder()
    rings = []
    # Dense enough to be smooth without subdivision, which would draw its open edge in away from the lip.
    for k in range(25):
        t = k / 24
        o = v(-0.18, y, SEAT_Z - 0.04) + up * (t * 0.66)
        wide = 0.205 - 0.03 * t + 0.015 * math.sin(math.pi * t)
        # The wings: the sides wrap further forward at the shoulders and again round the head.
        wrap = 0.16 + 0.06 * math.exp(-((t - 0.55) / 0.12) ** 2) + 0.12 * math.exp(-((t - 0.92) / 0.08) ** 2)
        ring = []
        for j in range(33):
            a = math.pi * j / 32
            ring.append(o + v(0, 1, 0) * (wide * math.cos(a)) + facing * (wrap * math.cos(a) ** 2 - 0.10))
        rings.append(ring)
    b.loft(rings, closed=False, caps=False)
    parts.append(b.finish(name + " shell", "cage", SHELL, solidify=0.012, texels=0.6))
    # The lip rolled round its front edges and over its top.
    edge = [r[0] for r in rings] + rings[-1][1:-1] + [r[-1] for r in reversed(rings)]
    b = Builder()
    b.sweep(spline([p + facing * 0.004 for p in edge], 160), circle(0.011, 10))
    parts.append(b.finish(name + " shell lip", "cage", SHELL, subdivide=1, texels=0.6))
    # The harness's slots through the back at the shoulders.
    t = 0.62
    wide = 0.205 - 0.03 * t + 0.015 * math.sin(math.pi * t)
    wrap = 0.16 + 0.06 * math.exp(-((t - 0.55) / 0.12) ** 2) + 0.12 * math.exp(-((t - 0.92) / 0.08) ** 2)
    skin = wrap * (0.07 / wide) ** 2 - 0.10
    for side in (1, -1):
        # Cut through the shell where the shoulder strap passes, square to the shell's curve there, a rubber edge round it.
        at = v(-0.18, y + side * 0.07, SEAT_Z - 0.04) + up * (t * 0.66) + facing * skin
        across = (v(0, 1, 0) * wide + facing * (2 * wrap * side * 0.07 / wide)).normalized()
        # The slot's dark well right through the shell and its padding, a closed rubber grommet on each face.
        through = across.cross(up)
        b = Builder()
        b.slab(rounded_rect(0.085, 0.022, 0.011), at, across, up, 0.05, both=True)
        parts.append(b.finish(name + " harness slot", "black", SHELL, texels=1.0, smooth_shade=False))
        for face in (-0.013, 0.013):
            loop = [at + through * face + across * u + up * w for (u, w) in rounded_rect(0.093, 0.030, 0.015, 4)]
            b = Builder()
            b.sweep(loop + loop[:1], circle(0.004, 8), ups=[through] * (len(loop) + 1))
            parts.append(b.finish(name + " harness slot edge", "rubber", SHELL, texels=1.0))
    b = Builder()
    b.slab(rounded_rect(0.36, 0.40, 0.08), v(0.05, y, SEAT_Z - 0.03), (0, 1, 0), (1, 0, 0), 0.06, both=True)
    parts.append(b.finish(name + " pad", "race cloth", SHELL, subdivide=2, texels=0.6))
    b = Builder()
    b.slab(rounded_rect(0.30, 0.50, 0.06), v(-0.17, y, SEAT_Z + 0.25) + facing * 0.012, (0, 1, 0), up, -0.04)
    parts.append(b.finish(name + " back pad", "race cloth", SHELL, subdivide=2, texels=0.6))
    for side in (1, -1):
        b = Builder()
        b.slab([(0.15, 0.0), (-0.25, 0.0), (-0.22, 0.10), (0.10, 0.10)], v(0, y + side * 0.255, FLOOR_Z + 0.02), (1, 0, 0), (0, 0, 1), 0.006, both=True)
        parts.append(b.finish(name + " bracket", "cage", SHELL, bevel=0.002, texels=0.8))
    return parts


def harness(house, y, bar_x, bar_z, name):
    """A five point harness: two shoulder straps from the bar behind the seat over the shell's top, two lap straps
    from the floor, all to a round buckle in the lap."""
    parts = []
    buckle = v(0.06, y, SEAT_Z + 0.06)
    for side in (1, -1):
        b = Builder()
        path = spline([v(bar_x, y + side * 0.07, bar_z), v(-0.40, y + side * 0.07, SEAT_Z + 0.335), v(-0.35, y + side * 0.07, SEAT_Z + 0.333),
                       v(-0.26, y + side * 0.08, SEAT_Z + 0.37), v(-0.15, y + side * 0.09, SEAT_Z + 0.32), v(-0.05, y + side * 0.09, SEAT_Z + 0.22),
                       buckle + v(-0.01, side * 0.03, 0.04)], 40)
        b.sweep(path, rounded_rect(0.05, 0.003, 0.001), ups=lambda i, p: v(0, 1, 0).cross(path[min(i + 1, len(path) - 1)] - path[max(i - 1, 0)]))
        parts.append(b.finish(name + " shoulder strap", "webbing", SHELL, texels=1.0))
        b = Builder()
        path = spline([v(-0.12, y + side * 0.24, FLOOR_Z + 0.08), v(-0.05, y + side * 0.20, SEAT_Z + 0.04), buckle + v(0.0, side * 0.04, -0.005)], 12)
        b.sweep(path, rounded_rect(0.075, 0.003, 0.001), ups=[v(0, 0, 1)] * 12)
        parts.append(b.finish(name + " lap strap", "webbing", SHELL, texels=1.0))
    b = Builder()
    b.lathe([(0.0, 0.0), (0.035, 0.0), (0.036, 0.012), (0.03, 0.018), (0.0, 0.02)], buckle, v(-0.3, 0, 1), count=28)
    parts.append(b.finish(name + " harness buckle", "chrome", SHELL, subdivide=1, texels=1.6))
    return parts


def stripped(house):
    """What a car stripped for the track keeps of its trim: the roof bare paint with its bows, the doors bare with a
    pull strap each."""
    s = house.side
    parts = []
    for p in kit.PARTS:
        if p.name.startswith(("roof lining", "header", "roof rail", "back header", "door header", "a pillar", "rear quarter", "hatch panel", "wheelhouse")) \
                or p["material"] == "carpet":
            p["material"] = "painted"
    for p in [p for p in kit.PARTS if p.name.startswith("quarter speaker")]:
        kit.PARTS.remove(p)
        bpy.data.objects.remove(p)
    for x in (0.15, -0.10, -0.35):
        b = Builder()
        path = [v(x, house.y(house.glass_out(s["top_z"]) - 0.03), house.roof_z(0.5) - 0.005), v(x, house.mid, house.roof_z(0.0) - 0.005),
                v(x, house.y(-(house.glass_out(s["top_z"]) - 0.03)), house.roof_z(0.5) - 0.005)]
        b.sweep(spline(path, 16), rounded_rect(0.05, 0.025, 0.006), ups=[v(0, 0, -1)] * 16)
        parts.append(b.finish("roof bow", "painted", SHELL, texels=0.5))
    for driver in (True, False):
        x_front, x_back = 0.50, s["b"][0] + 0.02
        b = Builder()
        grid = []
        for r in range(9):
            z = lerp(s["sill_z"] + 0.004, FLOOR_Z, r / 8)
            out = s["sill_half"] - 0.03
            grid.append([v(lerp(x_front + 0.10, x_back, c / 10), house.y(out, driver), z) for c in range(11)])
        b.loft(grid, closed=False, caps=False)
        parts.append(b.finish("door panel", "painted", SHELL, solidify=0.004, texels=0.5))
        side = 1 if driver else -1
        face_y = house.y(s["sill_half"] - 0.034, driver)
        b = Builder()
        for z in (s["sill_z"] - 0.12, s["sill_z"] - 0.30):
            b.sweep([v(x_front + 0.07, face_y, z), v(x_back + 0.03, face_y, z)], [(0.006 * math.cos(math.pi * k / 6), -side * 0.004 * math.sin(math.pi * k / 6)) for k in range(7)],
                    ups=[v(0, 0, 1)] * 2)
        parts.append(b.finish("door swage", "painted", SHELL, texels=0.5))
        b = Builder()
        b.sweep([v(x_front + 0.10, face_y, s["sill_z"] - 0.006), v(x_back, face_y, s["sill_z"] - 0.006)], rounded_rect(0.012, 0.02, 0.004), ups=[v(0, 0, 1)] * 2)
        parts.append(b.finish("door channel lip", "black", SHELL, texels=0.5))
        b = Builder()
        rim = [(x_front + 0.085, s["sill_z"] - 0.03), (x_back + 0.015, s["sill_z"] - 0.03), (x_back + 0.015, FLOOR_Z + 0.08), (x_front + 0.085, FLOOR_Z + 0.08)]
        for (xa, za), (xb, zb) in zip(rim, rim[1:] + rim[:1]):
            count = max(1, int(math.hypot(xb - xa, zb - za) / 0.06))
            for k in range(count):
                x, z = lerp(xa, xb, k / count), lerp(za, zb, k / count)
                b.lathe([(0.0, 0.0), (0.0035, 0.0), (0.003, 0.0015), (0.0, 0.002)], v(x, face_y - side * 0.002, z), v(0, -side, 0), count=8)
        parts.append(b.finish("door rivets", "cage", SHELL, texels=1.0, smooth_shade=False))
        # Under the windscreen's pillar, where the dash is cut back for the cage, the bare inner skin closes the cowl's side.
        foot_z = max(s["sill_z"], house.screen["foot"][2]) - 0.01
        px = house.a_pillar_x(foot_z)
        b = Builder()
        b.slab([(px + 0.06, s["sill_z"] - 0.16), (x_front - 0.02, s["sill_z"] - 0.16), (x_front - 0.02, foot_z - 0.01), (px - 0.07, foot_z - 0.005)],
               v(0, house.y(house.glass_out(s["sill_z"]) - 0.02, driver), 0), (1, 0, 0), (0, 0, 1), side * 0.006)
        parts.append(b.finish("cowl side", "painted", SHELL, bevel=0.003, texels=0.5))
        b = Builder()
        path = spline([v(x_front - 0.06, house.y(s["sill_half"] - 0.045, driver), s["sill_z"] - 0.14),
                       v((x_front + x_back) / 2, house.y(s["sill_half"] - 0.06, driver), s["sill_z"] - 0.17),
                       v(x_back + 0.08, house.y(s["sill_half"] - 0.045, driver), s["sill_z"] - 0.14)], 14)
        b.sweep(path, rounded_rect(0.03, 0.003, 0.001), ups=[v(0, 0, 1)] * 14)
        parts.append(b.finish("door pull strap", "webbing", SHELL, texels=1.0))
        # The release: a bare lever on a plate near the door's front, low enough to show under the cage's door bar.
        rx, rz = x_front - 0.04, s["sill_z"] - 0.21
        b = Builder()
        b.slab(rounded_rect(0.07, 0.035, 0.006), v(rx, house.y(s["sill_half"] - 0.034, driver), rz), (1, 0, 0), (0, 0, 1), (1 if driver else -1) * 0.004)
        parts.append(b.finish("door release plate", "cage", SHELL, bevel=0.0015, texels=1.4))
        b = Builder()
        b.sweep(spline([v(rx + 0.02, house.y(s["sill_half"] - 0.040, driver), rz), v(rx - 0.01, house.y(s["sill_half"] - 0.048, driver), rz + 0.002),
                        v(rx - 0.04, house.y(s["sill_half"] - 0.046, driver), rz)], 8), rounded_rect(0.006, 0.011, 0.003), ups=[v(0, 0, 1)] * 8)
        parts.append(b.finish("door release", "chrome", SHELL, texels=1.6))
    return parts


HARNESS_Z = SEAT_Z + 0.33
HOOP_X = -0.46


def cage(house):
    """Steel tube: a hoop behind the seats, a bar up each windscreen pillar to it along the roof, a bar across the
    top of the screen, and a bar along each door."""
    s = house.side
    parts = []
    r = 0.019

    def tube(points, name):
        b = Builder()
        path = spline(points, max(8, len(points) * 6))
        b.sweep(path, circle(r, 12), ups=[v(0, 0, 1) if abs((points[-1] - points[0]).normalized().z) < 0.9 else v(1, 0, 0)] * len(path))
        parts.append(b.finish(name, "cage", SHELL, texels=0.5))
    top_z = s["top_z"] - 0.025
    hoop_x = HOOP_X
    for driver in (True, False):
        out_top = house.glass_out(s["top_z"]) - 0.06
        out_low = s["sill_half"] - 0.07
        foot = v(0.52, house.y(out_low, driver), FLOOR_Z + 0.02)
        a_top = v(house.screen["top"][0] - 0.04, house.y(out_top, driver), top_z)
        hoop_top = v(hoop_x, house.y(out_top, driver), top_z)
        # Straight up from its plate, bent round on a radius of a few tubes' widths, then straight up the pillar.
        bend = v(0.50, house.y(out_low - 0.01, driver), s["sill_z"] - 0.02)
        tube([foot, v(0.51, house.y(out_low - 0.005, driver), s["sill_z"] - 0.14), bend, bend + (a_top - bend).normalized() * 0.10, a_top], "cage pillar")
        tube([a_top, hoop_top], "cage roof bar")
        tube([v(hoop_x, house.y(out_low, driver), FLOOR_Z + 0.02), hoop_top], "cage hoop leg")
        tube([v(0.505, house.y(out_low - 0.005, driver), s["sill_z"] - 0.12), v(hoop_x, house.y(out_low, driver), s["sill_z"] - 0.10)], "cage door bar")
    tube([v(hoop_x, house.y(house.glass_out(s["top_z"]) - 0.06), top_z), v(hoop_x, house.y(-(house.glass_out(s["top_z"]) - 0.06)), top_z)], "cage hoop top")
    out_low = s["sill_half"] - 0.07
    tube([v(hoop_x - 0.01, house.y(out_low), FLOOR_Z + 0.05), v(hoop_x - 0.01, house.y(-(house.glass_out(s["top_z"]) - 0.06)), top_z - 0.02)], "cage diagonal")
    tube([v(hoop_x - 0.015, house.y(out_low), HARNESS_Z), v(hoop_x - 0.015, house.y(-out_low), HARNESS_Z)], "cage harness bar")
    for driver in (True, False):
        for x in (0.52, hoop_x):
            b = Builder()
            b.slab(rounded_rect(0.08, 0.08, 0.008), v(x, house.y(out_low, driver), FLOOR_Z + 0.005), (1, 0, 0), (0, 1, 0), 0.006)
            parts.append(b.finish("cage foot plate", "cage", SHELL, bevel=0.002, texels=0.8))
    # The stays from the main hoop's top corners back and down to the rear towers.
    for driver in (True, False):
        out_top = house.glass_out(s["top_z"]) - 0.06
        stay_foot = v(house.spec["rear_axle"], house.y(s["sill_half"] - 0.12, driver), SEAT_Z - 0.05 + 0.19 + 0.012)
        tube([v(hoop_x - 0.01, house.y(out_top, driver), top_z - 0.01), stay_foot], "cage rear stay")
        b = Builder()
        b.slab(rounded_rect(0.10, 0.10, 0.01), stay_foot - v(0, 0, 0.012), (1, 0, 0), (0, 1, 0), 0.008)
        parts.append(b.finish("cage stay plate", "cage", SHELL, bevel=0.002, texels=0.8))
    tube([v(house.screen["top"][0] - 0.04, house.y(house.glass_out(s["top_z"]) - 0.06), top_z),
          v(house.screen["top"][0] - 0.04, house.y(-(house.glass_out(s["top_z"]) - 0.06)), top_z)], "cage screen bar")
    return parts


def load_space(house):
    """The carpeted space behind the seats under the hatch, and the trim of the quarters either side."""
    s, b_ = house.side, house.back
    parts = []
    inner = s["sill_half"] - 0.06
    b = Builder()
    b.slab([(s["b"][0] - 0.02, house.y(inner)), (b_["foot"][0] + 0.02, house.y(inner)), (b_["foot"][0] + 0.02, house.y(-inner)),
            (s["b"][0] - 0.02, house.y(-inner))], v(0, 0, SEAT_Z - 0.06), (1, 0, 0), (0, 1, 0), 0.02)
    parts.append(b.finish("load floor", "carpet", SHELL, bevel=0.004, texels=0.5))
    for driver in (True, False):
        b = Builder()
        grid = []
        for k in range(7):
            z = lerp(FLOOR_Z, s["sill_z"] + 0.01, k / 6)
            grid.append([v(lerp(b_["foot"][0] + 0.03, s["b"][0] - 0.01, c / 10), house.y(s["sill_half"] - 0.035, driver), z) for c in range(11)])
        # Rolled over at its top towards the glass.
        for dz, dout in ((0.014, 0.008), (0.016, 0.022)):
            grid.append([v(lerp(b_["foot"][0] + 0.03, s["b"][0] - 0.01, c / 10), house.y(s["sill_half"] - 0.035 + dout, driver), s["sill_z"] + dz) for c in range(11)])
        b.loft(grid, closed=False, caps=False)
        parts.append(b.finish("rear quarter", "trim", SHELL, solidify=0.012, subdivide=1, texels=0.5))
        parts.append(sedan.wheelhouse(house, s["sill_half"] - 0.035, SEAT_Z - 0.05, 0.62, 0.22, 0.19, driver))
        # A speaker in each quarter above the tub.
        side = 1 if driver else -1
        at = v(house.spec["rear_axle"] - 0.10, house.y(s["sill_half"] - 0.042, driver), s["sill_z"] - 0.10)
        b = Builder()
        b.slab(circle(0.05, 32), at, (1, 0, 0), (0, 0, 1), side * 0.004)
        parts.append(b.finish("quarter speaker", "grille", SHELL, bevel=0.002, texels=1.4))
        b = Builder()
        b.lathe([(0.048, 0.0), (0.058, 0.0), (0.062, 0.005), (0.058, 0.009), (0.050, 0.008)], at - v(0, side * 0.004, 0), v(0, -side, 0), count=32, caps=False)
        parts.append(b.finish("quarter speaker ring", "plastic", SHELL, subdivide=1, texels=1.4))
        b = Builder()
        for k in range(-3, 4):
            dz = k * 0.012
            half = (0.049 ** 2 - dz ** 2) ** 0.5
            b.slab(rounded_rect(2 * half, 0.004, 0.0015), at + v(0, -side * 0.004, dz), (1, 0, 0), (0, 0, 1), side * 0.003)
        parts.append(b.finish("quarter speaker bars", "plastic", SHELL, texels=1.4, smooth_shade=False))
    b = Builder()
    xb = b_["foot"][0] + 0.03
    wide = s["sill_half"] - 0.03
    b.slab([(house.y(wide), SEAT_Z - 0.06), (house.y(-wide), SEAT_Z - 0.06), (house.y(-wide), b_["foot"][2] - 0.006), (house.y(wide), b_["foot"][2] - 0.006)],
           v(xb, 0, 0), (0, 1, 0), (0, 0, 1), 0.02)
    parts.append(b.finish("hatch panel", "trim", SHELL, bevel=0.008, texels=0.5))
    if house.spec.get("race"):
        b = Builder()
        for k in range(3):
            z = lerp(SEAT_Z - 0.02, b_["foot"][2] - 0.05, (k + 0.5) / 3)
            b.sweep([v(xb, house.y(wide - 0.06), z), v(xb, house.y(-(wide - 0.06)), z)], [(0.0, 0.015), (-0.006, 0.010), (-0.006, -0.010), (0.0, -0.015)], ups=[v(0, 0, 1)] * 2)
        parts.append(b.finish("hatch ribs", "trim", SHELL, bevel=0.002, texels=0.5))
    b = Builder()
    b.sweep([v(xb + 0.012, house.y(wide - 0.01), b_["foot"][2] - 0.012), v(xb + 0.012, house.y(-(wide - 0.01)), b_["foot"][2] - 0.012)], rounded_rect(0.045, 0.03, 0.013),
            ups=[v(0, 0, 1)] * 2)
    parts.append(b.finish("hatch sill", "black", SHELL, subdivide=1, texels=0.5))
    # The hatch's striker in a plate at the middle of the panel's top.
    b = Builder()
    b.slab(rounded_rect(0.09, 0.04, 0.008), v(xb + 0.012, house.mid, b_["foot"][2] - 0.035), (0, -1, 0), (0, 0, 1), 0.004)
    parts.append(b.finish("hatch striker plate", "black", SHELL, bevel=0.0015, texels=1.6))
    b = Builder()
    b.sweep(spline([v(xb + 0.016, house.mid + 0.022, b_["foot"][2] - 0.035), v(xb + 0.04, house.mid + 0.016, b_["foot"][2] - 0.035), v(xb + 0.04, house.mid - 0.016, b_["foot"][2] - 0.035),
                    v(xb + 0.016, house.mid - 0.022, b_["foot"][2] - 0.035)], 10), circle(0.004, 8), ups=[v(0, 0, 1)] * 10)
    parts.append(b.finish("hatch striker", "chrome", SHELL, texels=1.6))
    return parts


def build(spec):
    race = spec.get("race", False)
    house = shell.House(spec)
    shell.panes(house)
    shell.lining(house)
    shell.pillars(house)
    dash(house)
    instruments(house, vents=not race)
    if race:
        # A dished wheel on three bare spokes to a small boss, its rim in suede.
        luxury.steering_wheel(rim="race cloth", spokes="black", boss_skin="black", pad=(0.07, 0.07), drop=0.0, angles=(90, -90, 180), round_boss=True)
    else:
        luxury.steering_wheel(rim="leather", spokes="black", boss_skin="leather", pad=(0.12, 0.09), drop=0.0, angles=(90, -90, 180))
    compact.column()
    console(house, race)
    if race:
        racing_seat(house, 0.0, "driver seat")
        racing_seat(house, 2 * house.mid, "passenger seat")
        cage(house)
        harness(house, 0.0, HOOP_X - 0.015, HARNESS_Z, "driver")
        harness(house, 2 * house.mid, HOOP_X - 0.015, HARNESS_Z, "passenger")
        extinguisher(house)
    else:
        lean = math.radians(20)
        compact.bucket(0.28, -0.15, 0.0, 0.50, SEAT_Z - 0.02, -0.20, lean, "driver seat")
        compact.bucket(0.28, -0.15, 2 * house.mid, 0.50, SEAT_Z - 0.02, -0.20, lean, "passenger seat")
    if not race:
        for driver in (True, False):
            sedan.door_card(house, 0.50, spec["side"]["b"][0] + 0.02, driver)
    sedan.floor(house, x_back=spec["side"]["b"][0] - 0.02, material="painted" if race else "carpet", tunnel_high=0.18, speakers=not race)
    compact.pedals()
    sedan.visors_and_mirror(house, visors=not race)
    # A stripped car's door is bare inside: no sail over the mirror's mount.
    sedan.door_mirrors(house, aero=True, sail=not race)
    load_space(house)
    if race:
        stripped(house)
    else:
        sedan.belts(house)
    return house


def info(house, mirrors):
    return sedan.info(house, mirrors, needles(house.spec.get("race", False)), glow_at=(0.56, 0.07, -0.222))
