"""The interior of the Chevalier Nyala and the cars of its kind: a full-size American saloon of the
early eighties. A long padded dashboard with a hooded cluster of two round instruments and a gear
indicator, a two-spoke wheel with a broad horn pad, a column gear lever, a split bench in pleated
cloth, door cards with a long armrest and chrome handles, a cloth lining with a dome lamp.
"""

import math

from mathutils import Vector

from . import materials, shell, upholstery
from .kit import (FLOOR_Z, MIRROR, NEEDLE_FUEL, NEEDLE_HEAT, NEEDLE_REVS, NEEDLE_SPEED, SHELL, WHEEL, Builder, circle, frame_from, lerp, mirror_y, rounded_rect, smooth,
                  spline, superellipse, v)

# The wheel's middle from the eye and the way its column runs, away from the driver (Cabins.PLACE, brought to tiles).
WHEEL_AT = v(0.354, 0.0, -0.298)
COLUMN = v(0.9380, 0.0, -0.3468).normalized()
RIM = 0.188

SEAT_Z = -0.60


def blend_sections(a, b, w):
    return [(lerp(p[0], q[0], w), lerp(p[1], q[1], w)) for p, q in zip(a, b)]


def dash(house):
    """The dashboard from door to door: a padded top that meets the windscreen, a rolled edge, and a face that
    is drawn forward into a hooded recess before the driver for the instruments."""
    s = house.side
    inner = s["sill_half"] - 0.030
    ys = []
    count = 44
    for k in range(count + 1):
        ys.append(house.y(lerp(inner, -inner, k / count)))

    def sections(y):
        out = abs(y - house.mid)
        xf, zf = house.edge("foot", out)
        xf -= 0.01
        normal = [
            (xf + 0.05, zf - 0.07), (xf + 0.004, zf + 0.001), (lerp(xf, 0.50, 0.35), zf + 0.014), (lerp(xf, 0.50, 0.72), -0.191),
            (0.506, -0.188), (0.486, -0.191), (0.473, -0.203), (0.468, -0.222), (0.467, -0.26), (0.468, -0.30), (0.470, -0.345),
            (0.474, -0.40), (0.482, -0.437), (0.53, -0.458), (0.66, -0.478), (0.80, -0.36),
        ]
        hood = [
            (xf + 0.05, zf - 0.07), (xf + 0.004, zf + 0.001), (0.60, -0.177), (0.535, -0.159),
            (0.472, -0.152), (0.454, -0.159), (0.449, -0.173), (0.458, -0.186), (0.53, -0.196), (0.566, -0.206), (0.566, -0.33),
            (0.505, -0.346), (0.476, -0.372), (0.478, -0.432), (0.60, -0.47), (0.80, -0.36),
        ]
        # The hood spans the instruments before the driver, eased in over a few centimetres each side.
        w = smooth((0.255 - abs(y)) / 0.05) if abs(y) < 0.255 else 0.0
        return blend_sections(normal, hood, w)

    b = Builder()
    rings = []
    for y in ys:
        rings.append([v(x, y, z) for (x, z) in sections(y)])
    b.loft(rings, closed=True, caps=True)
    return b.finish("dashboard", "padded", SHELL, subdivide=2, coarsest=1, texels=1.6, sharp=50)


def moulded_panel(name, material, front, across, up, width, height, proud, radius=0.014, flare=0.018, texels=1.4):
    """A panel moulded out of a face: its front at front, standing proud of the face behind it, its sides flaring
    out into the face in a fillet so it reads as one moulding with it and not a box set on it. across and up lie in
    its face; it faces across x up."""
    across, up = Vector(across).normalized(), Vector(up).normalized()
    facing = across.cross(up)
    b = Builder()
    rings = []
    for back, grow in ((proud + 0.03, flare * 1.6), (proud, flare), (proud * 0.55, flare * 0.25), (proud * 0.15, 0.002), (0.0, 0.0)):
        rings.append([front - facing * back + across * u + up * w for (u, w) in rounded_rect(width + 2 * grow, height + 2 * grow, radius + grow, 6)])
    b.loft(rings, closed=True, caps=True, cap_ends=(False, True))
    return b.finish(name, material, SHELL, subdivide=1, texels=texels)


def surround(name, material, front, across, up, width, height, proud, lip=0.010, radius=0.006, texels=1.6):
    """A moulded frame round a panel set into a face, so the panel reads as let in and not laid on: from a fillet in
    the face behind, out to a lip standing just proud of the panel's front at front, and back in round the panel's
    edge to behind it."""
    across, up = Vector(across).normalized(), Vector(up).normalized()
    facing = across.cross(up)
    b = Builder()
    rings = []
    for back, half_w, half_h, r in ((proud + 0.02, width / 2 + lip + 0.010, height / 2 + lip + 0.010, radius + lip + 0.010),
                                    (proud, width / 2 + lip + 0.004, height / 2 + lip + 0.004, radius + lip + 0.004),
                                    (-0.003, width / 2 + lip, height / 2 + lip, radius + lip), (-0.004, width / 2 + lip * 0.4, height / 2 + lip * 0.4, radius + lip * 0.4),
                                    (-0.001, width / 2 + 0.001, height / 2 + 0.001, radius), (0.012, width / 2 + 0.001, height / 2 + 0.001, radius)):
        rings.append([front - facing * back + across * u + up * w for (u, w) in rounded_rect(half_w * 2, half_h * 2, r, 5)])
    b.loft(rings, closed=True, caps=False)
    return b.finish(name, material, SHELL, subdivide=1, texels=texels)


def dash_details(house):
    parts = []
    mid = house.mid
    # The face of the instruments, set back in the hood's recess: a black plate.
    b = Builder()
    b.slab(rounded_rect(0.50, 0.128, 0.02), v(0.562, 0.0, -0.268), (0, -1, 0), (0, 0, 1), 0.004)
    parts.append(b.finish("cluster plate", "black", SHELL, texels=1.5, smooth_shade=False))
    parts += gauges()
    # The centre stack: a panel stood proud of the face, turned a little to the driver.
    turn = math.radians(5)
    cx, cy = 0.462, mid - 0.03
    ahead, left = v(math.cos(turn), math.sin(turn), 0), v(-math.sin(turn), math.cos(turn), 0)
    parts.append(moulded_panel("centre stack", "plastic", v(cx, cy, -0.333) - ahead * 0.008, -left, (0, 0, 1), 0.235, 0.215, 0.016, flare=0.024))
    # Under it a short lip of the heater's housing, closed underneath, so the stack does not hang below the dash and
    # the middle passenger of the bench keeps the room for his knees.
    b = Builder()
    b.slab([(cx - 0.004, -0.425), (cx + 0.16, -0.425), (cx + 0.16, -0.447), (cx - 0.004, -0.447)], v(0, cy, 0), (1, 0, 0), (0, 0, 1), 0.205, both=True)
    parts.append(b.finish("stack housing", "plastic", SHELL, bevel=0.008, bevel_segments=2, texels=0.8))
    # Two slatted vents at its head.
    for dy in (-0.055, 0.055):
        parts += vent(v(cx - 0.016, cy + dy, -0.247), left, 0.095, 0.042)
    # The radio: a face with a tuning window, two knobs and a row of buttons.
    b = Builder()
    b.slab(rounded_rect(0.18, 0.05, 0.004), v(cx - 0.017, cy, -0.300), left, (0, 0, 1), 0.006)
    parts.append(b.finish("radio face", "black", SHELL, bevel=0.0015, texels=2.5))
    parts.append(surround("radio surround", "plastic", v(cx - 0.017, cy, -0.300) - ahead * 0.006, -left, (0, 0, 1), 0.18, 0.05, 0.006))
    b = Builder()
    b.slab(rounded_rect(0.085, 0.018, 0.002), v(cx - 0.0235, cy + 0.0, -0.294), left, (0, 0, 1), 0.0015)
    parts.append(b.finish("radio window", "lens", SHELL, texels=3.0, smooth_shade=False))
    for dy in (-0.066, 0.066):
        b = Builder()
        b.lathe([(0.0, 0.0), (0.0105, 0.0), (0.0105, 0.010), (0.009, 0.016), (0.0, 0.017)], v(cx - 0.024, cy + dy, -0.300), -ahead, count=24)
        parts.append(b.finish("radio knob", "chrome", SHELL, texels=2.0, sharp=40))
    for k in range(5):
        b = Builder()
        b.slab(rounded_rect(0.016, 0.008, 0.002), v(cx - 0.024, cy - 0.032 + k * 0.016, -0.313), left, (0, 0, 1), 0.004)
        parts.append(b.finish("radio button", "chrome", SHELL, texels=2.0, smooth_shade=False))
    # The heater's controls: a panel with three levers in slots, the heat, the air's way and the fan.
    b = Builder()
    b.slab(rounded_rect(0.18, 0.042, 0.004), v(cx - 0.017, cy, -0.360), left, (0, 0, 1), 0.004)
    parts.append(b.finish("heater panel", "plastic", SHELL, bevel=0.0012, texels=2.0))
    parts.append(surround("heater surround", "plastic", v(cx - 0.017, cy, -0.360) - ahead * 0.004, -left, (0, 0, 1), 0.18, 0.042, 0.004))
    # Each lever's black paddle riding in a dark slot of its own across the panel.
    # Placed along the panel's own face, which the stack's turn sets back towards the driver's side.
    panel_at = v(cx - 0.017, cy, -0.360)
    for k, at in enumerate((0.3, 0.7, 0.45)):
        slot = panel_at + left * lerp(0.058, -0.058, k / 2)
        b = Builder()
        b.slab(rounded_rect(0.042, 0.005, 0.002), slot - ahead * 0.0042, left, (0, 0, 1), 0.0008)
        parts.append(b.finish("heater slot", "black", SHELL, texels=2.0, smooth_shade=False))
        b = Builder()
        b.slab(rounded_rect(0.012, 0.018, 0.003), slot + left * lerp(-0.016, 0.016, at) - ahead * 0.0045, left, (0, 0, 1), 0.008)
        parts.append(b.finish("heater lever", "black", SHELL, bevel=0.0015, texels=2.0))
    # The ash tray's lid.
    b = Builder()
    b.slab(rounded_rect(0.12, 0.028, 0.004), v(cx - 0.017, cy, -0.410), left, (0, 0, 1), 0.004)
    parts.append(b.finish("ash tray", "chrome", SHELL, bevel=0.0012, texels=1.5))
    # The glove box's lid on the passenger's side, with its button.
    gy = house.y(-0.42)
    b = Builder()
    b.slab(rounded_rect(0.29, 0.105, 0.012), v(0.469, gy, -0.345), (0, -1, 0), (0, 0, 1), 0.008, both=True)
    parts.append(b.finish("glove box", "padded", SHELL, bevel=0.003, texels=1.0))
    b = Builder()
    b.slab(rounded_rect(0.032, 0.013, 0.004), v(0.462, gy, -0.31), (0, -1, 0), (0, 0, 1), 0.005)
    parts.append(b.finish("glove box button", "chrome", SHELL, bevel=0.001, texels=2.0))
    # A band of wood-grained trim across the passenger's side of the face, nearly flush in a thin bezel, stopping
    # short of the end vent.
    inner, outer = 0.17, house.side["sill_half"] - 0.185
    centre = v(0.4665, house.y(-(inner + outer) / 2), -0.258)
    b = Builder()
    b.slab(rounded_rect(outer - inner, 0.046, 0.006), centre, (0, -1, 0), (0, 0, 1), 0.0015)
    parts.append(b.finish("dash trim", "wood", SHELL, bevel=0.0008, texels=1.2))
    parts.append(surround("dash trim bezel", "plastic", centre + v(0.0015, 0, 0), (0, -1, 0), (0, 0, 1), outer - inner, 0.046, 0.003, lip=0.004, radius=0.006))
    # Slatted vents at each end of the face.
    parts += vent(v(0.468, house.y(house.side["sill_half"] - 0.125), -0.24), v(0, 1, 0), 0.075, 0.042)
    parts += vent(v(0.468, house.y(-(house.side["sill_half"] - 0.125)), -0.24), v(0, 1, 0), 0.075, 0.042)
    # The demister's grille along the foot of the windscreen, and a speaker grille at each end of the top.
    parts += demisters(house, 0.47)
    for out in (house.side["sill_half"] - 0.11, -(house.side["sill_half"] - 0.11)):
        x, z = house.edge("foot", abs(out))
        b = Builder()
        b.slab(rounded_rect(0.10, 0.07, 0.01), v(x - 0.10, house.y(out), z + 0.011), (0, 1, 0), (1, 0, 0.18), 0.003)
        parts.append(b.finish("speaker grille", "grille", SHELL, texels=1.6, smooth_shade=False))
    return parts


def demisters(house, face_x, frac=0.4):
    """The demister's grilles on the dash's top close to the windscreen's foot: a broad one before each of the people
    in front and a short one at each end for the side windows. Each is laid onto the dash's surface after it is
    built (cabins.seat_on_dashboard)."""
    s = house.side
    parts = []
    for out, wide in ((0.30, 0.32), (-0.30, 0.32), (s["sill_half"] - 0.16, 0.08), (-(s["sill_half"] - 0.16), 0.08)):
        xf, zf = house.edge("foot", abs(out))
        b = Builder()
        b.slab(rounded_rect(0.03, wide, 0.006), v(lerp(xf - 0.01, face_x, frac), house.y(out), zf + 0.01), (1, 0, 0), (0, 1, 0), 0.0015)
        parts.append(b.finish("demister", "grille", SHELL, texels=1.4, smooth_shade=False))
        b = Builder()
        count = int(wide / 0.015)
        for k in range(count + 1):
            dy = lerp(-wide / 2 + 0.008, wide / 2 - 0.008, k / count)
            b.slab(rounded_rect(0.026, 0.004, 0.0015), v(lerp(xf - 0.01, face_x, frac), house.y(out) + dy, zf + 0.01), (1, 0, 0), (0, 1, 0), 0.0022)
        parts.append(b.finish("demister bars", "padded", SHELL, texels=1.4, smooth_shade=False))
    return parts


def vent(centre, across, width, height):
    """A slatted vent let into a face: a frame and five slats."""
    across = Vector(across).normalized()
    up = v(0, 0, 1)
    out = across.cross(up)
    b = Builder()
    outline = rounded_rect(width, height, 0.006)
    hole = rounded_rect(width - 0.01, height - 0.01, 0.003)
    rings_o = [centre + across * u + up * w for (u, w) in outline]
    rings_i = [centre + across * u + up * w for (u, w) in hole]
    # A frame: outer ring, then inner ring set back.
    n = len(outline)
    fo = [b.vert(p) for p in rings_o]
    fo_back = [b.vert(p - out * 0.003) for p in rings_o]
    fi = [b.vert(p - out * 0.006) for p in rings_i]
    fi_back = [b.vert(p + out * 0.02) for p in rings_i]
    for i in range(n):
        j = (i + 1) % n
        b.face([fo[i], fo[j], fi[j], fi[i]])
        b.face([fo_back[j], fo_back[i], fo[i], fo[j]])
        b.face([fi[i], fi[j], fi_back[j], fi_back[i]])
    b.face(list(reversed(fi_back)))
    parts = [b.finish("vent frame", "black", SHELL, texels=1.5, sharp=40)]
    # The slats stand just behind the frame's lip, in front of a dark well, so they show however deep the frame is set
    # into the face it is let into.
    b = Builder()
    b.slab(rounded_rect(width - 0.01, height - 0.01, 0.003), centre - out * 0.001, across, (0, 0, 1), 0.001)
    parts.append(b.finish("vent well", "black", SHELL, texels=1.5, smooth_shade=False))
    b = Builder()
    for k in range(5):
        z = lerp(-height / 2 + 0.008, height / 2 - 0.008, k / 4)
        c = centre + up * z - out * 0.0035
        b.slab(rounded_rect(width - 0.016, 0.0022, 0.001), c, across, (0, 0, 1), 0.005, both=True)
    parts.append(b.finish("vent slats", "plastic", SHELL, texels=1.5, smooth_shade=False))
    return parts


# The saloon's cluster: a wide speedometer before the driver, the fuel gauge to its left and the heat to its right,
# each a face with its needle turning about a pivot below it, hidden behind the lip under the faces. Each: its middle,
# width, height, how far below its lower edge the pivot is, the scale's radius, and its sweep.
SPEEDO = (v(0.553, 0.0, -0.252), 0.24, 0.085, 0.05, 0.11)
FUEL = (v(0.553, 0.155, -0.262), 0.07, 0.055, 0.02, 0.06)
HEAT = (v(0.553, -0.155, -0.262), 0.07, 0.055, 0.02, 0.06)


def instrument(name, centre, radius, picture, ring=True):
    """One round instrument facing the driver: its printed face in a shallow dish, and a chrome ring round it, or
    without the ring a face printed on a panel shared with others."""
    parts = []
    c, r = centre, radius
    materials.DIALS["dial " + picture] = (c, r, r)
    b = Builder()
    if ring:
        profile = [(0.0, -0.004), (r * 0.5, -0.0035), (r * 0.95, -0.002), (r, 0.0), (r + 0.002, 0.004), (r + 0.006, 0.006), (r + 0.007, 0.004), (r + 0.008, -0.006)]
    else:
        profile = [(0.0, -0.002), (r * 0.6, -0.0018), (r, -0.0012), (r + 0.003, -0.004)]
    b.lathe(profile, c, v(-1, 0, 0), count=48)
    parts.append(b.finish(name + " face", "dial " + picture, SHELL, texels=8.0, sharp=30))
    if not ring:
        return parts
    b = Builder()
    b.lathe([(r + 0.0055, 0.0035), (r + 0.0085, 0.006), (r + 0.010, 0.004), (r + 0.010, -0.002)], c, v(-1, 0, 0), count=48, caps=False)
    parts.append(b.finish(name + " ring", "chrome", SHELL, texels=3.0, sharp=50))
    return parts


def needle(group, pivot, radius, length):
    """A needle pointing straight up at rest, a part of its own the mod turns about its pivot, with its cap."""
    parts = []
    b = Builder()
    tip = radius * length
    outline = [(-0.0016, 0.0), (-0.0009, tip * 0.96), (0.0, tip), (0.0009, tip * 0.96), (0.0016, 0.0), (0.0024, -0.012), (-0.0024, -0.012)]
    b.slab(outline, pivot + v(-0.003, 0, 0), (0, -1, 0), (0, 0, 1), 0.0012)
    parts.append(b.finish("needle %d" % group, "needle", group, texels=4.0, smooth_shade=False))
    b = Builder()
    b.lathe([(0.0, 0.0), (0.0055, 0.0), (0.0055, 0.003), (0.0, 0.004)], pivot + v(-0.0045, 0, 0), v(-1, 0, 0), count=16)
    parts.append(b.finish("needle cap %d" % group, "black", group, texels=3.0))
    return parts


# The needles of the saloon's instruments: which, its pivot, the radius of its dial, its length as a share of that,
# and its sweep (see info).
def sedan_needles():
    table = []
    for group, (c, w, h, below, radius), sweep in ((NEEDLE_SPEED, SPEEDO, (math.radians(-50), math.radians(50), SPEED_SWEEP[2])),
                                                   (NEEDLE_FUEL, FUEL, (math.radians(-40), math.radians(40), 1.0)),
                                                   (NEEDLE_HEAT, HEAT, (math.radians(-40), math.radians(40), 1.0))):
        table.append((group, c - v(0, 0, h / 2 + below), radius, 0.94, sweep))
    return table


def band_instrument(name, centre, width, height, picture):
    """A wide instrument facing the driver: its printed face, set back behind a bright rim."""
    parts = []
    materials.DIALS["dial " + picture] = (centre, width / 2, height / 2)
    b = Builder()
    b.slab(rounded_rect(width, height, 0.006), centre + v(0.001, 0, 0), (0, -1, 0), (0, 0, 1), 0.002)
    parts.append(b.finish(name + " face", "dial " + picture, SHELL, texels=8.0, smooth_shade=False))
    parts.append(surround(name + " rim", "chrome", centre - v(0.002, 0, 0), (0, -1, 0), (0, 0, 1), width, height, 0.002, lip=0.003, radius=0.006, texels=3.0))
    return parts


def gauges():
    """The instruments: the speedometer and the fuel and heat gauges, their needles, which the mod turns, the lip that
    hides the needles' pivots, and the gear indicator on it."""
    parts = []
    for name, (c, w, h, _, _), picture in (("speedometer", SPEEDO, "speedometer-band"), ("fuel", FUEL, "fuel-band"), ("heat", HEAT, "heat-band")):
        parts += band_instrument(name, c, w, h, picture)
    for group, pivot, radius, length, _ in sedan_needles():
        parts += needle(group, pivot, radius, length)
    b = Builder()
    b.slab(rounded_rect(0.50, 0.06, 0.012), v(0.545, 0.0, -0.325), (0, -1, 0), (0, 0, 1), 0.012)
    parts.append(b.finish("cluster lip", "black", SHELL, bevel=0.003, texels=1.5))
    b = Builder()
    b.slab(rounded_rect(0.13, 0.016, 0.003), v(0.532, 0.0, -0.310), (0, -1, 0), (0, 0, 1), 0.002)
    materials.DIALS["dial gears"] = (v(0.532, 0.0, -0.310), 0.065, 0.008)
    parts.append(b.finish("gear indicator", "dial gears", SHELL, texels=8.0, smooth_shade=False))
    return parts


def wheel_frame():
    """The wheel's own frame: x along its column away from the driver, y to the left, z up its face."""
    up = v(0, 0, 1)
    face_up = (up - COLUMN * up.dot(COLUMN)).normalized()
    return frame_from(WHEEL_AT, COLUMN, face_up)


def horn_pad(width, height, material, drop=0.0, name="horn pad", squareness=3.2):
    """A padded horn button on the wheel's hub: a rounded oblong domed towards the driver, in the wheel's frame,
    width across and height up its face, its middle drop below the hub's; round where squareness is 2."""
    rings = []
    for x, scale in ((0.075, 0.70), (0.055, 0.93), (0.038, 1.0), (0.022, 0.98), (0.010, 0.88), (0.003, 0.62)):
        rings.append([v(x, u * scale, w * scale - drop) for (u, w) in superellipse(width, height, squareness, 28)])
    b = Builder()
    b.loft(rings, closed=True, caps=True)
    pad = b.finish(name, material, WHEEL, subdivide=1, texels=2.0)
    pad.data.transform(wheel_frame())
    return pad


def blade_spoke(start, end, wide, narrow, thick, material, bow=0.006):
    """A spoke from hub to rim in the wheel's frame, a flat blade broad in the wheel's face, narrowing to the rim."""
    b = Builder()
    path = spline([start, (start + end) / 2 + v(bow, 0, 0), end], 10)
    b.sweep(path, lambda i: rounded_rect(lerp(wide, narrow, i / 9), thick, thick * 0.45), ups=[v(-1, 0, 0)] * 10)
    sp = b.finish("spoke", material, WHEEL, subdivide=1, texels=2.0, sharp=50)
    sp.data.transform(wheel_frame())
    return sp


def steering_wheel():
    """A thin rim with grips for the fingers behind it, two spokes that dip a little below the middle, and a broad
    soft horn pad set into the dish of the wheel."""
    m = wheel_frame()
    parts = []
    b = Builder()
    n = 64
    path, ups = [], []
    for k in range(n):
        a = 2 * math.pi * k / n
        path.append(v(0.0, RIM * math.sin(a), RIM * math.cos(a)))
        ups.append(v(-1.0, 0.0, 0.0))

    def section(i):
        a = 2 * math.pi * i / n
        # Grips for the fingers along the back of the rim, a little thicker at ten and two.
        grip = 1.0 + 0.10 * max(0.0, math.cos(6 * a)) ** 4
        out = []
        for j in range(16):
            t = 2 * math.pi * j / 16
            ripple = 1.0 + (0.08 * max(0.0, math.sin(48 * a)) ** 2 if math.cos(t) > 0.3 else 0.0)
            out.append((0.0118 * grip * math.sin(t), 0.0145 * math.cos(t) * ripple))
        return out
    rings = []
    for i, p in enumerate(path):
        radial = v(0, p.y, p.z).normalized()
        sec = section(i)
        rings.append([p + radial * u + v(1, 0, 0) * w for (u, w) in sec])
    made = [[b.vert(q) for q in ring] for ring in rings]
    for i in range(n):
        a, c = made[i], made[(i + 1) % n]
        for j in range(16):
            k = (j + 1) % 16
            b.face([a[j], a[k], c[k], c[j]])
    rim = b.finish("rim", "wheel", WHEEL, subdivide=1, texels=3.0, sharp=60)
    rim.data.transform(m)
    parts.append(rim)
    # The spokes: flat bars from the hub to the rim at a little below the middle on each side.
    for side in (1, -1):
        b = Builder()
        path = [v(0.040, side * 0.06, -0.030), v(0.026, side * 0.115, -0.040), v(0.010, side * 0.160, -0.034), v(0.002, side * (RIM - 0.008), -0.026)]
        path = spline(path, 12)
        widths = [lerp(0.050, 0.030, k / 11) for k in range(12)]
        b.sweep(path, lambda i: rounded_rect(0.009, widths[i], 0.004), ups=[v(-0.4, 0, 1)] * 12)
        sp = b.finish("spoke", "wheel", WHEEL, subdivide=1, texels=2.0, sharp=50)
        sp.data.transform(m)
        parts.append(sp)
    # The hub: a dished shroud behind, and the horn pad on it.
    b = Builder()
    b.lathe([(0.0, 0.085), (0.035, 0.085), (0.048, 0.07), (0.055, 0.05), (0.058, 0.035), (0.0, 0.034)], v(0, 0, -0.012), v(1, 0, 0), count=32)
    hub = b.finish("hub", "black", WHEEL, subdivide=1, texels=1.5)
    hub.data.transform(m)
    parts.append(hub)
    b = Builder()
    rings = []
    for k in range(7):
        t = k / 6
        depth = lerp(0.050, 0.022, t)
        bulge = 0.010 * math.sin(math.pi * min(t * 1.4, 1.0))
        w, h = lerp(0.150, 0.158, t) * (1.0 - 0.08 * t * t), lerp(0.098, 0.104, t) * (1.0 - 0.08 * t * t)
        ring = [(u, wv) for (u, wv) in rounded_rect(w, h, 0.03, 8)]
        rings.append([v(depth - bulge * 0, u, wv - 0.012) for (u, wv) in ring])
    b.loft(rings, closed=True, caps=True)
    pad = b.finish("horn pad", "padded", WHEEL, subdivide=2, texels=2.5, sharp=60)
    pad.data.transform(m)
    parts.append(pad)
    return parts


def stalk(side, name="indicator stalk"):
    """A stalk out of the column's shroud just behind the wheel, on the left (side 1) or right (-1): it angles down
    and back towards the driver and flattens to a paddle with a thicker tip, in the wheel's frame."""
    m = wheel_frame()
    parts = []
    b = Builder()
    # Angled down far enough that its tip shows below a spoke at nine o'clock.
    path = spline([v(0.10, side * 0.035, 0.002), v(0.092, side * 0.088, -0.02), v(0.082, side * 0.125, -0.046), v(0.078, side * 0.148, -0.062)], 12)
    b.sweep(path, lambda i: rounded_rect(lerp(0.011, 0.016, i / 11), lerp(0.010, 0.008, i / 11), 0.0035), ups=[v(-0.3, 0, 1)] * 12)
    part = b.finish(name, "black", SHELL, subdivide=1, texels=2.0)
    part.data.transform(m)
    parts.append(part)
    b = Builder()
    b.lathe([(0.0, 0.0), (0.0095, 0.003), (0.0105, 0.02), (0.009, 0.032), (0.0, 0.035)], v(0.079, side * 0.145, -0.060), v(-0.12, side * 1.0, -0.6), count=16)
    tip = b.finish(name + " tip", "black", SHELL, subdivide=1, texels=2.0)
    tip.data.transform(m)
    parts.append(tip)
    return parts


def tilt_lever():
    """The short lever under the left stalk that frees the column to tilt."""
    m = wheel_frame()
    b = Builder()
    path = spline([v(0.13, 0.04, -0.035), v(0.12, 0.075, -0.05), v(0.11, 0.095, -0.06)], 8)
    b.sweep(path, lambda i: rounded_rect(lerp(0.009, 0.012, i / 7), 0.007, 0.003), ups=[v(0, 0, 1)] * 8)
    lever = b.finish("tilt lever", "black", SHELL, subdivide=1, texels=2.0)
    lever.data.transform(m)
    return [lever]


def column():
    """The steering column's shroud from behind the wheel into the dashboard, the indicator stalk on its left,
    the gear lever on its right and the key in its side."""
    m = wheel_frame()
    parts = []
    b = Builder()
    path = [v(t, 0, 0) for t in (0.075, 0.10, 0.14, 0.18, 0.22)]
    secs = [rounded_rect(0.075, 0.068, 0.028), rounded_rect(0.082, 0.072, 0.026), rounded_rect(0.092, 0.080, 0.022),
            rounded_rect(0.10, 0.090, 0.02), rounded_rect(0.11, 0.10, 0.02)]
    b.sweep(path, lambda i: secs[i], ups=[v(0, 0, 1)] * 5)
    shroud = b.finish("column shroud", "plastic", SHELL, subdivide=2, texels=1.5)
    shroud.data.transform(m)
    parts.append(shroud)
    parts += stalk(1)
    parts += tilt_lever()
    # The gear lever: out of a collar on the right of the column, out and down, with a round knob.
    b = Builder()
    b.lathe([(0.0, -0.01), (0.03, -0.01), (0.034, 0.0), (0.034, 0.035), (0.03, 0.045), (0.0, 0.045)], v(0.095, 0, 0), v(1, 0, 0), count=28)
    collar = b.finish("gear collar", "plastic", SHELL, subdivide=1, texels=1.5)
    collar.data.transform(m)
    parts.append(collar)
    b = Builder()
    path = spline([v(0.11, -0.03, 0.005), v(0.10, -0.09, 0.002), v(0.085, -0.17, -0.025), v(0.068, -0.228, -0.065)], 14)
    b.sweep(path, lambda i: circle(lerp(0.0072, 0.0055, i / 13), 14), ups=[v(0, 0, 1)] * 14)
    lever = b.finish("gear lever", "chrome", SHELL, texels=2.0)
    lever.data.transform(m)
    parts.append(lever)
    b = Builder()
    b.lathe([(0.0, 0.0), (0.008, 0.0), (0.013, 0.008), (0.0145, 0.022), (0.013, 0.034), (0.008, 0.041), (0.0, 0.043)], v(0.068, -0.228, -0.065), v(-0.25, -0.75, -0.60), count=24)
    knob = b.finish("gear knob", "black", SHELL, subdivide=1, texels=2.0)
    knob.data.transform(m)
    parts.append(knob)
    # The key's lock on the right of the shroud, with its key.
    b = Builder()
    b.lathe([(0.0, 0.0), (0.012, 0.0), (0.012, 0.006), (0.010, 0.008), (0.0, 0.008)], v(0.17, -0.052, 0.012), v(0, -1, 0), count=20)
    lock = b.finish("key lock", "chrome", SHELL, texels=2.0)
    lock.data.transform(m)
    parts.append(lock)
    b = Builder()
    b.slab(rounded_rect(0.022, 0.026, 0.008), v(0.17, -0.066, 0.012), (1, 0, 0), (0, 0, 1), 0.003, both=True)
    key = b.finish("key", "chrome", SHELL, bevel=0.001, texels=2.0)
    key.data.transform(m)
    parts.append(key)
    return parts


def bench(x_front, x_crease, y_out, y_in, z_top, back_top, back_lean, name, pleats=6, headrest=True, split=None, dishes=()):
    """A bench or one half of a split bench: a cushion channelled fore and aft behind a rolled front, a back
    channelled up it under a plain head band, and a head restraint on two posts."""
    parts = []
    width = abs(y_out - y_in)
    ym = (y_out + y_in) / 2
    base = z_top - 0.13
    # The cushion rises a little to its front, under the thighs.
    rise = math.radians(6)
    along = v(-math.cos(rise), 0.0, -math.sin(rise))
    length = x_front - x_crease
    parts.append(upholstery.cushion(name + " cushion", "cloth", v(x_front, ym, base + length * math.sin(rise)), (0, 1, 0), along, (0, 0, 1), width, length,
                                    0.13, channels=pleats, margin=0.065, roll=0.04, crown=0.014, seam_depth=0.010, along_seams=(0.16,), texels=1.1, dishes=dishes))
    # The base under it, in vinyl, down to the floor of the seat's frame.
    b = Builder()
    b.slab(rounded_rect(length - 0.04, width - 0.03, 0.02), v((x_front + x_crease) / 2, ym, base - 0.09), (1, 0, 0), (0, 1, 0), 0.10)
    parts.append(b.finish(name + " base", "vinyl", SHELL, bevel=0.006, texels=0.5))
    # The back, leaning back from the crease.
    up_back = v(-math.sin(back_lean), 0.0, math.cos(back_lean))
    facing = v(math.cos(back_lean), 0.0, math.sin(back_lean))
    height = back_top - z_top + 0.05
    # Its foot tucked down behind the cushion's back edge, so no light shows between them.
    back_origin = v(x_crease - 0.11 * math.cos(back_lean) + 0.01, ym, z_top - 0.07)
    parts.append(upholstery.cushion(name + " back", "cloth", back_origin, (0, 1, 0), up_back, facing, width, height / math.cos(back_lean), 0.11,
                                    channels=pleats, margin=0.07, roll=0.06, crown=0.016, along_seams=(0.82,), texels=1.0, top_taper=0.40, dishes=dishes))
    parts += upholstery.back_panel(name, "vinyl", back_origin, up_back, facing, width, height / math.cos(back_lean), pocket=headrest)
    for end in (y_out, y_in) if not headrest else ():
        side = 1 if end > ym else -1
        b = Builder()
        b.slab(rounded_rect(0.06, 0.04, 0.008), back_origin + up_back * 0.04 + v(0, end - ym + side * 0.004, 0), (1, 0, 0), (0, 0, 1), side * 0.008, both=True)
        parts.append(b.finish(name + " hinge cover", "plastic", SHELL, bevel=0.003, texels=1.2))
    if headrest:
        top = back_origin + up_back * (height / math.cos(back_lean)) + facing * 0.026
        centre = top + up_back * 0.13
        parts.append(upholstery.block(name + " head rest", "cloth", centre, (0, 1, 0), up_back, facing, 0.22, 0.14, 0.08, texels=0.9, taper=0.06))
        for dy in (-0.065, 0.065):
            b = Builder()
            b.lathe([(0.0055, 0.0), (0.0055, 0.10)], top + v(0, dy, 0) - up_back * 0.006, up_back, count=12)
            parts.append(b.finish(name + " head rest post", "chrome", SHELL, texels=1.0))
            b = Builder()
            # Sunk into the crown, so the post never shows below it.
            b.lathe([(0.0, 0.0), (0.011, 0.0), (0.011, 0.025), (0.0, 0.026)], top + v(0, dy, 0) - up_back * 0.016, up_back, count=16)
            parts.append(b.finish(name + " head rest collar", "black", SHELL, texels=1.0))
    return parts


def seats(house):
    s = house.side
    parts = []
    inner = s["sill_half"] - 0.06
    lean = math.radians(17)
    # The front bench, split in two at the middle, the driver's half on the left.
    parts += bench(0.27, -0.17, house.y(inner - 0.04), house.y(0.004), SEAT_Z, -0.17, lean, "driver seat", pleats=8)
    parts += bench(0.27, -0.17, house.y(-0.004), house.y(-(inner - 0.04)), SEAT_Z, -0.17, lean, "passenger seat", pleats=8)
    # The armrest folded up into the backs where the halves meet.
    up_back = v(-math.sin(lean), 0.0, math.cos(lean))
    facing = v(math.cos(lean), 0.0, math.sin(lean))
    foot = v(-0.17 - 0.11 * math.cos(lean) + 0.01, house.mid, SEAT_Z - 0.02) + facing * 0.105
    parts.append(upholstery.block("centre armrest", "cloth", foot + up_back * 0.19 - facing * 0.01, (0, 1, 0), up_back, facing, 0.20, 0.34, 0.05, texels=0.9))
    # The rear bench, from door to door, behind the front backs.
    parts += bench(-0.50, -0.96, house.y(s["sill_half"] - 0.066), house.y(-(s["sill_half"] - 0.066)), SEAT_Z + 0.02, -0.19, math.radians(22), "rear seat", pleats=14, headrest=False)
    return parts


def door_card(house, x_front, x_back, driver, rear=False, roll=True, power=False, pleats=3, welt=True):
    """The inside of a door: a padded roll along its top under the glass, a face of vinyl with a pleated insert,
    a long armrest with a pull in it, a chrome handle, the window's winder and the lock's knob, and carpet along
    its foot."""
    s = house.side
    parts = []
    side = 1 if driver else -1
    gl = s["sill_half"]
    face = gl - 0.055

    def y(out):
        return house.y(out, driver)
    # The rubber that wipes the glass where it goes down into the door.
    b = Builder()
    b.sweep([v(x, y(gl - 0.011), s["sill_z"] + 0.003) for x in (x_back + 0.005, x_front - 0.005)], rounded_rect(0.009, 0.012, 0.003),
            ups=[v(0, 0, 1)] * 2)
    parts.append(b.finish("window seal", "rubber", SHELL, texels=1.0))
    # The roll along the top: from the glass in and down to the face.
    # A front door's trim runs on under the dashboard's end, which hides its front edge as a shut door's is hidden.
    reach = x_front + (0.0 if rear else 0.10)
    if roll:
        b = Builder()
        sec = [(side * -0.004, 0.002), (side * -0.020, 0.008), (side * -0.040, 0.004), (side * -0.052, -0.012), (side * -0.056, -0.030), (side * -0.050, -0.040),
               (side * 0.0, -0.040)]
        b.bar(v(x_back + 0.01, y(gl - 0.004), s["sill_z"] - 0.003), v(min(reach, x_front + 0.03), y(gl - 0.004), s["sill_z"] - 0.003), sec, lambda p, u, w: p + v(0, u, w),
              inset=0.93, ends=0.008)
        parts.append(b.finish("door roll", "padded", SHELL, subdivide=2, texels=1.0))
    # The face: from under the roll to the floor, bowed in a little at the armrest.
    b = Builder()
    rows, cols = 14, 10
    grid = []
    for r in range(rows + 1):
        z = lerp(s["sill_z"] - 0.040, FLOOR_Z - 0.01, r / rows)
        ring = []
        # Under the dashboard the kick panel takes over from the door, the edge running smoothly between them.
        front = lerp(x_front, reach, smooth((z + 0.52) / 0.10))
        for c in range(cols + 1):
            x = lerp(front - 0.005, x_back + 0.005, c / cols)
            ring.append(v(x, y(face + 0.008 * math.sin(math.pi * r / rows)), z))
        grid.append(ring)
    b.loft(grid, closed=False, caps=False)
    parts.append(b.finish("door face", "vinyl", SHELL, solidify=0.01, texels=0.9))
    # The pleated insert across the upper face.
    b = Builder()
    # It stops behind the handle, which sits in its cup on the plain card ahead of it.
    ins_front, ins_back = x_front - 0.17, x_back + 0.05
    # Pleated in channels across its height, each sunk at its seams; finer pleats are lower, the panel nearly flat.
    rows, cols = 6 * pleats, 14
    crown = 0.007 if pleats <= 3 else 0.005
    top_z, foot_z = s["sill_z"] - 0.06, s["sill_z"] - 0.17
    grid = []
    for r in range(rows + 1):
        z = lerp(top_z, foot_z, r / rows)
        ring = []
        for c in range(cols + 1):
            x = lerp(ins_front, ins_back, c / cols)
            # Each pleat's crown drops flat into the welt over its last two centimetres.
            ends = min(1.0, abs(x - ins_front) / 0.02, abs(x - ins_back) / 0.02)
            pleat = crown * (1.0 - abs(math.cos(math.pi * r / rows * pleats)) ** 4) * ends
            ring.append(v(x, y(face - 0.006 - pleat), z))
        grid.append(ring)
    b.loft(grid, closed=False, caps=False)
    parts.append(b.finish("door insert", "cloth", SHELL, solidify=0.004, subdivide=1, texels=1.0))
    if welt:
        # A welt framing the insert, standing proud of it and of the card, closing its ends.
        cx, cz = (ins_front + ins_back) / 2, (top_z + foot_z) / 2
        loop = [v(cx + u, y(face - 0.006 - crown - 0.002), cz + w) for (u, w) in rounded_rect(abs(ins_front - ins_back), top_z - foot_z, 0.012, 4)]
        b = Builder()
        b.sweep(loop + loop[:1], rounded_rect(0.007, 0.008, 0.003), ups=[v(0, -side, 0)] * (len(loop) + 1))
        parts.append(b.finish("door insert welt", "cloth", SHELL, subdivide=0, texels=1.0))
    # The armrest: a long padded bar out of the face, with a pull cup let into it near the front.
    b = Builder()
    ax0, ax1 = (x_front - 0.12, x_back + 0.10) if not rear else (x_front - 0.06, x_back + 0.12)
    # A pad in the door's back half, its pull let into its front.
    ax0 = min(ax0, ax1 + 0.29)
    az = max(s["sill_z"] - 0.215, SEAT_Z + 0.17)
    # Flat on top for the elbow, rolled over at its edge and tucked back into the face under it.
    # Doubled points at its top corner keep the top flat and the edge tight under subdivision.
    sec = [(side * 0.0, 0.026), (side * -0.060, 0.026), (side * -0.067, 0.022), (side * -0.070, 0.014), (side * -0.070, -0.026), (side * -0.066, -0.034),
           (side * -0.040, -0.048), (side * 0.0, -0.060)]
    b.bar(v(ax1, y(face - 0.002), az), v(ax0, y(face - 0.002), az), sec, lambda p, u, w: p + v(0, u, w), inset=0.97, ends=0.004)
    parts.append(b.finish("armrest", "padded", SHELL, bevel=0.004, bevel_segments=2, texels=1.0))
    # A cup let into the armrest's top to pull the door shut by, behind the switches where it has them.
    b = Builder()
    b.slab(rounded_rect(0.10, 0.032, 0.012), v(ax0 - (0.17 if power else 0.06), y(face - 0.032), az + 0.0275), (1, 0, 0), (0, -side * 1.0, 0), side * 0.012)
    parts.append(b.finish("armrest pull", "black", SHELL, bevel=0.004, texels=1.6))
    # The handle: a chrome lever in a recessed bezel, ahead of the armrest.
    hx, hz = x_front - 0.085, s["sill_z"] - 0.11
    b = Builder()
    # The lever in a cup let into the card.
    b.slab(rounded_rect(0.12, 0.05, 0.018), v(hx - 0.003, y(face - 0.002), hz), (1, 0, 0), (0, 0, 1), side * 0.002)
    parts.append(b.finish("handle cup", "black", SHELL, bevel=0.001, texels=2.0))
    b = Builder()
    path = spline([v(hx + 0.03, y(face - 0.005), hz), v(hx - 0.01, y(face - 0.009), hz + 0.002), v(hx - 0.035, y(face - 0.008), hz + 0.001)], 10)
    b.sweep(path, rounded_rect(0.006, 0.011, 0.003), ups=[v(0, 0, 1)] * 10)
    parts.append(b.finish("handle", "chrome", SHELL, texels=2.0))
    if power:
        # The windows' switches in a pod at the armrest's front: the driver's has one for each window and the locks.
        count = 3 if driver and not rear else 1
        pod_at = v(ax0 - 0.06, y(face - 0.035), az + 0.024)
        b = Builder()
        b.slab(rounded_rect(0.10, 0.045, 0.012), pod_at, (1, 0, 0), (0, side * -1.0, 0.0), 0.008)
        parts.append(b.finish("switch pod", "black", SHELL, bevel=0.003, texels=1.6))
        for k in range(count):
            b = Builder()
            b.slab(rounded_rect(0.018, 0.026, 0.004), pod_at + v(lerp(0.03, -0.03, k / max(count - 1, 1)) if count > 1 else 0.0, 0, 0.009), (1, 0, 0), (0, 1, 0), 0.006)
            parts.append(b.finish("window switch", "black", SHELL, bevel=0.0015, texels=2.0))
        return parts + door_foot(house, x_front, x_back, driver, rear, y, face)
    # The window's winder: a crank on a boss, its knob turned out.
    # Just under the armrest's level, so the crank's whole sweep stays above the cushion.
    wx, wz = x_front - 0.14, az - 0.075
    b = Builder()
    b.lathe([(0.0, 0.0), (0.018, 0.0), (0.018, 0.006), (0.010, 0.014), (0.0, 0.016)], v(wx, y(face), wz), v(0, -side, 0), count=20)
    parts.append(b.finish("winder boss", "chrome", SHELL, texels=1.5))
    b = Builder()
    # Its arm parked pointing forward and up, clear of the armrest behind it.
    path = spline([v(wx, y(face - 0.016), wz), v(wx + 0.028, y(face - 0.024), wz + 0.05), v(wx + 0.05, y(face - 0.028), wz + 0.09)], 10)
    b.sweep(path, rounded_rect(0.016, 0.007, 0.003), ups=[v(0, -side, 0)] * 10)
    parts.append(b.finish("winder arm", "chrome", SHELL, texels=1.5))
    b = Builder()
    b.lathe([(0.0, 0.0), (0.010, 0.0), (0.012, 0.022), (0.009, 0.034), (0.0, 0.036)], v(wx + 0.05, y(face - 0.028), wz + 0.09), v(0, -side, 0), count=16)
    parts.append(b.finish("winder knob", "black", SHELL, subdivide=1, texels=1.5))
    return parts + door_foot(house, x_front, x_back, driver, rear, y, face)


def door_foot(house, x_front, x_back, driver, rear, y, face):
    """The lock's knob, the speaker in a front door's foot, and the carpet along the foot."""
    s = house.side
    gl = s["sill_half"]
    parts = []
    # The lock's knob, standing out of the roll at its back.
    b = Builder()
    b.lathe([(0.0, 0.0), (0.0055, 0.0), (0.0055, 0.022), (0.0045, 0.027), (0.0, 0.028)], v(x_back + 0.10, y(gl - 0.018), s["sill_z"] - 0.006), v(0, 0, 1), count=16)
    parts.append(b.finish("lock knob", "chrome", SHELL, subdivide=1, texels=1.5))
    # A map pocket along the front door's foot.
    if not rear:
        p0, p1 = x_front - 0.06, max(x_front - 0.50, x_back + 0.06)
        side = 1 if driver else -1
        b = Builder()
        b.slab(rounded_rect(p0 - p1, 0.11, 0.015), v((p0 + p1) / 2, y(face - 0.028), FLOOR_Z + 0.165), (1, 0, 0), (0, 0, 1), side * 0.008)
        parts.append(b.finish("map pocket", "vinyl", SHELL, bevel=0.003, texels=0.9))
        b = Builder()
        b.slab(rounded_rect(p0 - p1, 0.03, 0.008), v((p0 + p1) / 2, y(face - 0.013), FLOOR_Z + 0.115), (1, 0, 0), (0, side * 1.0, 0), 0.01, both=True)
        parts.append(b.finish("map pocket floor", "vinyl", SHELL, bevel=0.002, texels=0.9))
    # Carpet along the foot, and a moulded step between it and the card above.
    b = Builder()
    b.slab([(x_front - 0.005, FLOOR_Z), (x_back + 0.005, FLOOR_Z), (x_back + 0.005, FLOOR_Z + 0.12), (x_front - 0.005, FLOOR_Z + 0.12)], v(0, y(face - 0.002), 0),
           (1, 0, 0), (0, 0, 1), 0.006)
    parts.append(b.finish("door carpet", "carpet", SHELL, texels=0.6, smooth_shade=False))
    side = 1 if driver else -1
    b = Builder()
    b.sweep([v(x_front - 0.01, y(face - 0.008), FLOOR_Z + 0.125), v(x_back + 0.01, y(face - 0.008), FLOOR_Z + 0.125)], rounded_rect(0.02, 0.016, 0.006),
            ups=[v(0, -side, 0)] * 2)
    parts.append(b.finish("door step", "vinyl", SHELL, bevel=0.002, texels=0.8))
    b = Builder()
    b.slab(rounded_rect(0.06, 0.022, 0.008), v(x_back + 0.07, y(face - 0.004), FLOOR_Z + 0.16), (1, 0, 0), (0, 0, 1), side * 0.004)
    parts.append(b.finish("door courtesy lens", "lens", SHELL, bevel=0.0015, texels=1.6))
    return parts


def vent_bar(house, driver, latch=True):
    """The bar between a front door's glass and the vent window ahead of it, where the body has one. Where the
    measures put it on the windscreen's pillar it is the pillar, which is drawn already."""
    s = house.side
    if not vent_bar_at(house):
        return []
    b = Builder()
    f0, f1 = s["front"]
    path = [v(f0, house.y(house.glass_out(s["sill_z"] + 0.008) - 0.012, driver), s["sill_z"] + 0.008), v(f1, house.y(house.glass_out(s["top_z"]) - 0.01, driver), s["top_z"] - 0.012)]
    b.sweep(spline(path, 6), rounded_rect(0.022, 0.016, 0.005), ups=[v(0, -1 if driver else 1, 0)] * 6)
    parts = [b.finish("vent bar", "black", SHELL, texels=0.6)]
    # Where the pillar's trim leaves no pane in view, the bar is only the glass's run, with no latch.
    if not latch or s["a_front"] - f0 < 0.13:
        return parts
    # The vent window's latch on the pane's own frame just ahead of the bar: a pivot boss, and a flat paddle from it
    # pointing forward and a little down over the pane.
    side = 1 if driver else -1
    boss = path[0].lerp(path[1], 0.62) + v(0.013, -side * 0.006, 0)
    b = Builder()
    b.lathe([(0.0, 0.0), (0.011, 0.0), (0.011, 0.009), (0.008, 0.012), (0.0, 0.012)], boss, v(0, -side, 0), count=16)
    parts.append(b.finish("vent latch boss", "chrome", SHELL, subdivide=1, texels=1.6))
    b = Builder()
    b.slab(rounded_rect(0.05, 0.015, 0.006), boss + v(0.022, -side * 0.012, -0.006), (1, 0, -0.25), (0, 0, 1), -side * 0.006)
    parts.append(b.finish("vent latch", "chrome", SHELL, bevel=0.002, texels=1.6))
    return parts


def doors(house):
    s = house.side
    parts = []
    for driver in (True, False):
        parts += door_card(house, 0.48, s["b"][0] + 0.02, driver)
        if s.get("rear_foot") is not None:
            parts += door_card(house, s["b"][1] - 0.02, s["rear_foot"] + 0.05, driver, rear=True)
    return parts


def floor(house, x_back=-1.18, material="carpet", tunnel_high=0.10, inset=0.035, kick=True, speakers=True):
    """The floor in carpet, with the tunnel down its middle, the toe board up to the bulkhead, the sills and the
    kick panels at the dashboard's ends, and the panel behind the rear bench."""
    s = house.side
    parts = []
    b = Builder()
    rows, cols = 24, 24
    grid = []
    inner = s["sill_half"] - inset
    # The bulkhead stays under the windscreen's foot, where the bonnet begins outside.
    def x_front(out):
        return min(0.98, house.edge("foot", out)[0] + 0.25)
    for r in range(rows + 1):
        ring = []
        for c in range(cols + 1):
            out = lerp(inner, -inner, c / cols)
            x = lerp(x_back, x_front(abs(out)), r / rows)
            tunnel = tunnel_high * smooth((0.26 - abs(out)) / 0.20) * smooth((x + 0.9) / 0.4)
            toe = max(0.0, x - 0.66) * 1.25
            ring.append(v(x, house.y(out), min(FLOOR_Z + tunnel + toe, house.edge("foot", abs(out))[1] - 0.12)))
        grid.append(ring)
    b.loft(grid, closed=False, caps=False)
    parts.append(b.finish("floor", material, SHELL, solidify=-0.01, texels=0.5))
    for driver in (True, False) if kick else ():
        # Kick panels: the dashboard's ends down to the floor ahead of each door, out to the floor's edge.
        b = Builder()
        out = s["sill_half"] - 0.040
        foot_x, foot_z = house.edge("foot", out)
        b.slab([(0.38, -0.45), (foot_x - 0.02, foot_z - 0.05), (x_front(out), foot_z - 0.12), (x_front(out), FLOOR_Z + 0.36), (min(0.80, x_front(out) - 0.04), FLOOR_Z - 0.03),
                (0.38, FLOOR_Z - 0.03)], v(0, house.y(out + 0.004, driver), 0),
               (1, 0, 0), (0, 0, 1), 0.012)
        parts.append(b.finish("kick panel", "plastic", SHELL, bevel=0.004, texels=0.5))
        # A speaker in it under the dash's end, a grille in a ring.
        side = 1 if driver else -1
        if not speakers:
            continue
        # On the panel's inner face, which its thickness puts 8 mm in from its plane on the driver's side and 4 mm out on
        # the passenger's.
        at = v(0.62, house.y(out, driver) + (-0.009 if driver else -0.003), FLOOR_Z + 0.20)
        b = Builder()
        b.slab(circle(0.06, 36), at, (1, 0, 0), (0, 0, 1), side * 0.005)
        parts.append(b.finish("kick speaker", "grille", SHELL, bevel=0.002, texels=1.4))
        b = Builder()
        b.lathe([(0.058, 0.0), (0.068, 0.0), (0.072, 0.005), (0.068, 0.011), (0.060, 0.010), (0.058, 0.004)], at, v(0, -side, 0), count=36, caps=False)
        parts.append(b.finish("kick speaker ring", "plastic", SHELL, subdivide=1, texels=1.4))
        b = Builder()
        for k in range(-4, 5):
            dz = k * 0.012
            half = (0.059 ** 2 - dz ** 2) ** 0.5
            b.slab(rounded_rect(2 * half, 0.004, 0.0015), at + v(0, -side * 0.005, dz), (1, 0, 0), (0, 0, 1), side * 0.003)
        parts.append(b.finish("kick speaker grille bars", "plastic", SHELL, texels=1.4, smooth_shade=False))
        # The sill's scuff plate along each door's foot.
        b = Builder()
        b.slab(rounded_rect(0.50 - x_back, 0.035, 0.008), v((0.50 + x_back) / 2, house.y(out - 0.018, driver), FLOOR_Z + 0.015), (1, 0, 0), (0, 0, 1), 0.04,
               both=True)
        parts.append(b.finish("scuff plate", "chrome", SHELL, bevel=0.003, texels=0.4))
    return parts


def pedals():
    """An automatic's pedals hung from under the dashboard: a broad brake pad, the accelerator to its right, and the
    parking brake's pedal high at the left. Each pad stands clear of the toe board, under the dashboard's lip."""
    parts = []
    for name, at, size, arm_from in (("brake pedal", v(0.66, 0.10, -0.60), (0.12, 0.065), v(0.58, 0.10, -0.45)),
                                     ("parking pedal", v(0.62, 0.27, -0.55), (0.07, 0.045), v(0.56, 0.27, -0.44))):
        parts += hung_pedal(name, at, size, arm_from)
    parts += accelerator(v(0.70, -0.17, -0.63))
    # The parking brake's release, a T-handle under the dashboard's lower edge above its pedal.
    b = Builder()
    b.sweep([v(0.53, 0.25, -0.43), v(0.49, 0.25, -0.445)], circle(0.004, 8), ups=[v(0, 0, 1)] * 2)
    parts.append(b.finish("parking release rod", "chrome", SHELL, texels=1.6))
    b = Builder()
    b.bar(v(0.488, 0.28, -0.447), v(0.488, 0.22, -0.447), rounded_rect(0.016, 0.014, 0.006), lambda p, u, w: p + v(u, 0, w), ends=0.008)
    parts.append(b.finish("parking release", "black", SHELL, subdivide=1, texels=1.6))
    return parts


def hung_pedal(name, at, size, arm_from):
    parts = []
    b = Builder()
    b.slab(rounded_rect(size[0], size[1], 0.012), at, (0, 1, 0), (-0.45, 0, 0.89), 0.009, both=True)
    parts.append(b.finish(name, "rubber", SHELL, bevel=0.004, texels=1.0))
    b = Builder()
    path = spline([arm_from, (arm_from + at) / 2 + v(0.03, 0, 0), at + v(0.012, 0, 0.02)], 8)
    b.sweep(path, rounded_rect(0.012, 0.022, 0.004), ups=[v(0, 1, 0)] * 8)
    parts.append(b.finish(name + " arm", "black", SHELL, texels=0.6))
    return parts


def accelerator(at):
    """A long pad hung from its arm, its foot near the toe board."""
    parts = []
    b = Builder()
    b.slab(rounded_rect(0.05, 0.12, 0.01), at, (0, 1, 0), (-0.55, 0, 0.83), 0.01, both=True)
    parts.append(b.finish("accelerator", "rubber", SHELL, bevel=0.002, texels=1.0))
    b = Builder()
    b.sweep(spline([at + v(-0.09, 0, 0.17), at + v(-0.02, 0, 0.12), at + v(0.01, 0, 0.05)], 8), rounded_rect(0.008, 0.014, 0.003), ups=[v(0, 1, 0)] * 8)
    parts.append(b.finish("accelerator arm", "black", SHELL, texels=0.6))
    return parts


# The mirror on the screen: the angle its glass's width takes up seen from the eye, in degrees, and its height for its
# width (the picture of the road behind is 640 by 180), how far its middle hangs behind the windscreen at its own
# height, how far above its middle the stem meets the glass, and the furthest aside of straight ahead it is let hang
# from the eye, in degrees.
MIRROR_SEEN, MIRROR_TALL = 19.0, 0.045 / 0.160
MIRROR_HANG, MIRROR_PAD_UP, MIRROR_ASIDE = 0.035, 0.05, 42.0


def screen_up_at(house, z):
    """How far up the windscreen, at its middle, it is at the height z: 0 at its foot, 1 at its top."""
    foot, top = house.screen_at(0.0, 0.0), house.screen_at(0.0, 1.0)
    return (z - foot.z) / (top.z - foot.z)


def screen_mirror_at(house):
    """The middle of the mirror's glass: on the vehicle's middle line, at the height above the eye the mod's own drawn
    cabin hangs its mirror (Cabins.house), MIRROR_HANG behind the windscreen there; lower down the glass where that would
    put it more than MIRROR_ASIDE aside from the eye."""
    angle, rail = house.spec["glasshouse"]
    m = house.spec["measures"]
    scale, wheel_along, wheel_up = 2.0 / 3.0, 0.531, -0.447
    foot_along, foot_up = wheel_along + m[0] / scale, wheel_up + m[1] / scale
    run = (rail - foot_up) / math.tan(math.radians(angle))
    down = 0.16 / math.hypot(run, rail - foot_up)
    z = (rail - (rail - foot_up) * down - 0.01) * scale

    def hung(z):
        return v(house.screen_at(0.0, screen_up_at(house, z)).x - MIRROR_HANG, house.mid, z)

    least = abs(house.mid) / math.tan(math.radians(MIRROR_ASIDE))
    while hung(z).x < least:
        z -= 0.002
    if screen_up_at(house, z + MIRROR_PAD_UP) > 1.0 or screen_up_at(house, z) < 0.3:
        raise ValueError("the mirror at %.3f up does not hang from the windscreen" % z)
    return hung(z)


def visors_and_mirror(house, visors=True):
    parts = []
    s = house.side
    for driver in (True, False) if visors else ():
        cz = house.roof_z(0.30) - 0.022
        # Each visor's outer end, with its hinge rod, stays clear of the side glass leaning in towards the roof.
        outer = min(abs(house.spec["seats"][0][1] - house.mid) + 0.18, house.glass_out(min(cz, s["top_z"])) - 0.05)
        length = min(0.34, outer - 0.14)
        out = outer - length / 2 if driver else -(outer - length / 2)
        # Under the lining where its outer end reaches, which falls away towards the sides.
        cz = min(cz, house.roof_z(outer) - 0.022)
        # Its front edge a little behind the glass at its own height, lower on the glass than the glass's top.
        cx = house.screen_x(outer, min(cz - 0.01, house.edge("top", outer)[1])) - 0.12
        b = Builder()
        b.slab(rounded_rect(length, 0.15, 0.03), v(cx, house.y(abs(out) * 0.98, driver) - 0.0, cz), (0, -1 if driver else 1, 0), (1, 0, 0.12), 0.022, both=True)
        parts.append(b.finish("visor", "lining", SHELL, bevel=0.005, bevel_segments=2, texels=0.8))
        b = Builder()
        b.lathe([(0.005, 0.0), (0.005, 0.20)], v(cx + 0.05, house.y(outer, driver), cz + 0.008), v(0, -1 if driver else 1, 0), count=10)
        parts.append(b.finish("visor rod", "chrome", SHELL, texels=0.8))
        # The bracket on the lining the rod turns in.
        b = Builder()
        b.slab(rounded_rect(0.03, 0.024, 0.006), v(cx + 0.03, house.y(outer + 0.01, driver), house.roof_z(outer + 0.01) - 0.002), (1, 0, 0), (0, 1, 0), -0.012)
        parts.append(b.finish("visor bracket", "plastic", SHELL, bevel=0.003, texels=1.2))
        # The clip on the lining that the visor's free end snaps into, on its hinge line.
        inboard = outer - length - 0.03
        # The short rod at the visor's free end that snaps into the clip, on its hinge line.
        b = Builder()
        b.lathe([(0.003, 0.0), (0.003, 0.032)], v(cx + 0.05, house.y(outer - length + 0.004, driver), cz + 0.008), v(0, -1 if driver else 1, 0), count=8)
        parts.append(b.finish("visor clip rod", "chrome", SHELL, texels=0.8))
        b = Builder()
        b.slab(rounded_rect(0.030, 0.020, 0.005), v(cx + 0.05, house.y(inboard, driver), house.roof_z(inboard) - 0.006), (0, 1, 0), (1, 0, 0),
               max(0.018, house.roof_z(inboard) - cz))
        parts.append(b.finish("visor clip", "plastic", SHELL, bevel=0.002, texels=1.0))
    # The mirror: a stem from a pad on the windscreen to a slim housing, its glass a part the mod shows the road behind in.
    at = screen_mirror_at(house)
    # As wide from the seat in every body: one hung further off, as in a step van, is bigger.
    width = 2.0 * at.length * math.tan(math.radians(MIRROR_SEEN / 2))
    glass = (width, width * MIRROR_TALL)
    # Set back where the windscreen curves back towards its sides and leans over its top, so the housing's front
    # corners, 0.024 ahead of the glass, stay a little behind it.
    half_w, half_h = glass[0] / 2 + 0.007, glass[1] / 2 + 0.0065
    need = max(at.x + 0.030 - house.screen_x(half_w, at.z + dz) for dz in (-half_h, half_h))
    if need > 0.0:
        at = at - v(need, 0, 0)
    top = house.screen_at(0.0, screen_up_at(house, at.z + MIRROR_PAD_UP))
    glass_up = (house.screen_at(0.0, 1.0) - house.screen_at(0.0, 0.0)).normalized()
    # The stem leaves its pad square to the windscreen, so its end lies flat on the glass.
    inward = glass_up.cross(v(0, 1, 0))
    b = Builder()
    path = spline([top + inward * 0.004, top + inward * 0.02, top.lerp(at, 0.5) + v(0, 0, 0.01), at + v(0.012, 0, 0.012)], 10)
    b.sweep(path, circle(0.007, 12), ups=[v(0, 1, 0)] * 10)
    parts.append(b.finish("mirror stem", "black", SHELL, texels=1.0))
    b = Builder()
    b.slab(rounded_rect(0.028, 0.032, 0.008), top - glass_up * 0.004, (0, 1, 0), glass_up, -0.005)
    parts.append(b.finish("mirror button", "black", SHELL, bevel=0.0015, texels=1.0))
    # Square to the vehicle, its glass facing straight back, as the mod's own drawn cabin hangs it.
    facing = v(-1.0, 0.0, 0.0)
    left = v(0, 0, 1).cross(facing).normalized()
    up = facing.cross(left)
    b = Builder()
    rings = []
    for k, (d, scale) in enumerate(((-0.024, 0.92), (-0.016, 1.0), (0.0, 1.0), (0.003, 0.985))):
        rings.append([at + facing * d + left * u * scale + up * w * scale for (u, w) in rounded_rect(glass[0] + 0.014, glass[1] + 0.013, 0.02, 8)])
    b.loft(rings, closed=True, caps=True)
    parts.append(b.finish("mirror housing", "black", SHELL, subdivide=1, texels=1.2))
    b = Builder()
    b.slab(rounded_rect(glass[0], glass[1], 0.014, 8), at + facing * 0.0032, left, up, 0.0005)
    parts.append(b.finish("mirror glass", "mirror", MIRROR, smooth_shade=False))
    # The day and night tab under the housing's lower edge.
    b = Builder()
    b.slab(rounded_rect(0.026, 0.012, 0.004), at - up * (glass[1] / 2 + 0.012) - facing * 0.008, left, facing, 0.006, both=True)
    parts.append(b.finish("mirror tab", "black", SHELL, bevel=0.0015, texels=1.4))
    # The lamp in the roof, between the front and rear seats.
    b = Builder()
    lamp_x = house.edge("top", 0.0)[0] - 0.13
    # Its lens just below the lining in a bezel let into it.
    b.slab(rounded_rect(0.15, 0.065, 0.012), v(lamp_x, house.mid, house.roof_z(0.0) - 0.002), (0, -1, 0), (1, 0, 0), -0.006)
    parts.append(b.finish("dome lamp", "lamp", SHELL, bevel=0.002, texels=1.0))
    b = Builder()
    b.slab(rounded_rect(0.168, 0.083, 0.018), v(lamp_x, house.mid, house.roof_z(0.0) - 0.001), (0, -1, 0), (1, 0, 0), -0.004)
    parts.append(b.finish("dome lamp bezel", "plastic", SHELL, bevel=0.0015, texels=1.0))
    return parts


def door_mirrors(house, tall=False, aero=False, on_post=False, sail=True):
    """A mirror on each front door, its head where the game's own body has its mirror and its arm down to a foot on
    the door's skin at the foot of the glass, so it stands on the bodywork; a van's is tall, on two tube brackets."""
    s = house.side
    m = house.spec["mirror"]
    parts = []
    for driver in (True, False):
        side = 1 if driver else -1
        hx, hout, hz = m["head"]
        at = v(hx, house.y(hout, driver), hz)
        skin = m["skin"]
        to_eye = (v(0, 0, 0) - at).normalized()
        # Square to the vehicle, facing straight back, as the mod's own drawn cabin stands them.
        facing = v(-1.0, 0.0, 0.0)
        if tall:
            # Its head no taller than leaves the glass clear of the door's top from the driver's eye.
            size, depth = (0.17, 0.22, 0.02), ((0.04, 0.85), (0.03, 0.97), (0.015, 1.0), (0.0, 1.0), (-0.003, 0.97))
            at = at + v(0, 0, 0.095)
            # The brackets' arms end inside the housing, behind its glass.
            behind = at - facing * 0.045
            if on_post:
                # A walk-in van has no doors to bolt them to: both arms go forward to the windscreen's corner post.
                post = [(house.a_pillar_x(z) - 0.01, z) for z in (s["sill_z"] + 0.10, s["top_z"] - 0.15)]
                feet = [(behind + v(0, 0, -0.10), v(post[0][0], house.y(house.glass_out(post[0][1]) + 0.012, driver), post[0][1])),
                        (behind + v(0, 0, 0.10), v(post[1][0], house.y(house.glass_out(post[1][1]) + 0.012, driver), post[1][1]))]
            else:
                # The lower arm to the door's skin, the upper forward to the windscreen's post.
                post_z = min(s["sill_z"] + 0.25, s["top_z"] - 0.05)
                feet = [(behind + v(0, 0, -0.12), v(hx + 0.13, house.y(skin + 0.004, driver), s["sill_z"] - 0.06)),
                        (behind + v(0, 0, 0.10), v(house.a_pillar_x(post_z) - 0.03, house.y(house.glass_out(post_z) + 0.032, driver), post_z))]
            for k, (top, foot) in enumerate(feet):
                b = Builder()
                b.sweep(spline([foot, foot.lerp(top, 0.5) + v(0, side * 0.01, 0), top], 8), circle(0.008, 12), ups=[v(1, 0, 0)] * 8)
                parts.append(b.finish("door mirror arm", "chrome", SHELL, texels=0.8))
                parts += mirror_foot(foot, side, 0.035)
        elif aero:
            # A moulded housing deep behind its glass, on a short neck from a sail on the door's skin.
            size, depth = (0.17, 0.105, 0.035), ((0.085, 0.55), (0.065, 0.82), (0.04, 0.97), (0.015, 1.0), (0.0, 1.0), (-0.003, 0.97))
            foot = v(hx + 0.05, house.y(skin + 0.004, driver), s["sill_z"] - 0.035)
            # One faired fin, thin across and long fore and aft, flaring at its root into a low pad on the door's top.
            top = at + v(0.06, -side * (size[0] / 2 - 0.035), -size[1] * 0.2)
            path = spline([foot + v(0, 0, -0.006), foot + v(0, 0, 0.012), foot.lerp(top, 0.5), top], 8)
            sections = [(0.036, 0.13, 0.012), (0.030, 0.115, 0.011), (0.022, 0.095, 0.009), (0.019, 0.085, 0.008), (0.017, 0.077, 0.007), (0.016, 0.07, 0.007),
                        (0.015, 0.064, 0.007), (0.014, 0.06, 0.006)]
            b = Builder()
            b.sweep(path, lambda i: rounded_rect(*sections[i]), ups=[v(1, 0, 0)] * 8)
            parts.append(b.finish("door mirror arm", "black", SHELL, subdivide=1, texels=0.8))
        else:
            size, depth = (0.15, 0.095, 0.022), ((0.045, 0.75), (0.035, 0.95), (0.02, 1.0), (0.0, 1.0), (-0.003, 0.97))
            foot = v(hx + 0.05, house.y(skin + 0.004, driver), s["sill_z"] - 0.03)
            # The arm comes in from ahead of the head to a ball boss in the middle of its back, hidden behind it.
            elbow = v(hx + 0.075, house.y((skin + hout) / 2, driver), (s["sill_z"] - 0.03 + hz) / 2 - 0.01)
            pivot = at - facing * 0.05 - v(0, 0, 0.01)
            b = Builder()
            b.sweep(spline([foot, elbow, pivot], 10), rounded_rect(0.028, 0.016, 0.006), ups=[v(1, 0, 0)] * 10)
            parts.append(b.finish("door mirror arm", "chrome", SHELL, texels=0.8))
            b = Builder()
            b.lathe([(0.0, 0.0), (0.014, 0.0), (0.014, 0.008), (0.011, 0.011), (0.0, 0.012)], at - facing * 0.04 - v(0, 0, 0.01), -facing, count=20)
            parts.append(b.finish("door mirror boss", "chrome", SHELL, subdivide=1, texels=0.8))
            parts += mirror_foot(foot, side, 0.05)
        up = v(0, 0, 1)
        across = up.cross(facing)
        b = Builder()
        # The housing from its back round to a lip standing proud of the glass on all four sides, the glass set in
        # behind the lip.
        inner = 1.0 - 0.018 / min(size[0], size[1])
        shape = [(d, scale) for d, scale in depth if d >= 0.0] + [(-0.008, 1.0), (-0.009, 0.97), (-0.008, inner), (0.002, inner)]
        rings = [[at - facing * d + across * (u * scale) + up * (w * scale) for (u, w) in rounded_rect(size[0], size[1], size[2], 6)] for d, scale in shape]
        b.loft(rings, closed=True, caps=True, cap_ends=(True, False))
        parts.append(b.finish("door mirror housing", "black" if tall or aero else "chrome", SHELL, subdivide=1, texels=0.9))
        b = Builder()
        # The glass alone tipped up by half the eye's height over it, so it shows the road behind and not the ground.
        tipped = v(-1.0, 0.0, to_eye.z * 0.6).normalized()
        b.slab(rounded_rect(size[0] * inner - 0.002, size[1] * inner - 0.002, size[2] * 0.7, 6), at - facing * 0.0015, across, tipped.cross(across), 0.0005)
        parts.append(b.finish("door mirror glass", "mirror", MIRROR, smooth_shade=False))
        # Inside, where the door has no vent window, a moulded sail fills the glass's front lower corner, the mirror's
        # adjuster in it.
        # Its front corner behind the pillar trim's foot, so no point of it shows ahead of the pillar.
        corner = house.a_pillar_x(max(s["sill_z"], house.screen["foot"][2]) + 0.02) - 0.035
        # Only where the game's mirror stands at the window's front corner does a sail inside cover its mount.
        # Ahead of the door's glass the body has a fixed light. A narrow one is filled by the moulded sail the mirror
        # stands on; a broad one stays glass, the mirror on a pad on the door's top, and has no sail.
        light = s["a_front"] - s["front"][0] if vent_bar_at(house) else 0.0
        if sail and aero and light < 0.25:

            def at_corner(x, z):
                return v(x, house.y(house.glass_out(z) - 0.012, driver), z)
            # Its top point on the pillar's edge a hand above the sill, its rear foot a hand behind that, so a raked
            # pillar gives a long low sail and never a sliver.
            # Its top tucked in under the pillar's trim, so no point of it stands beside the pillar.
            apex_z = s["sill_z"] + 0.075
            apex_x = house.a_pillar_x(apex_z) - 0.10
            # Its rear foot runs back over the mirror's mount where the mirror stands back along the door.
            # Back along the door to just behind the mirror's head, so its foot stands on the sail.
            # Filling the fixed light back to its divider, or, where there is none, back past the mirror's foot.
            # The light itself is mostly behind the pillar's trim from the eye: the sail runs on back over the door
            # glass's front corner to just behind the mirror's head, so the mirror plainly stands on it.
            rear = min(corner - 0.16, hx - 0.04)
            rear_up = rear + 0.04
            # Its front foot carried under the pillar trim's foot and down onto the door's top, so no light shows there.
            tri = [(corner + 0.006, s["sill_z"] + 0.003), (rear, s["sill_z"] + 0.006), (max(rear_up, rear), apex_z - 0.01), (min(apex_x, corner - 0.05), apex_z)]
            b = Builder()
            rings = []
            for inward in (0.0, 0.02):
                rings.append([at_corner(x, z) + v(0, -side * inward, 0) for x, z in tri])
            b.loft(rings, closed=True, caps=True)
            parts.append(b.finish("mirror sail", "plastic", SHELL, bevel=0.005, bevel_segments=3, texels=1.0))
    return parts


def vent_bar_at(house):
    """Whether the front doors have a vent window ahead of their glass, divided off by a bar."""
    s = house.side
    # Judged at its foot: where the bar stands back from the windscreen's pillar at the sill there is a vent window
    # between them, whether or not the bar meets the pillar at the top.
    return s.get("front") is not None and s["front"][0] < s["a_front"] - 0.06


def mirror_foot(at, side, size):
    """The plate a mirror's arm is bolted to, flat on the door's skin."""
    b = Builder()
    b.slab(rounded_rect(size * 1.4, size, size * 0.3), at, (1, 0, 0), (0, 0, 1), side * 0.012, both=True)
    return [b.finish("door mirror foot", "black", SHELL, bevel=0.003, texels=0.8)]


def wheelhouse(house, wall_out, z_base, length, width, height, driver, material="carpet", name="wheelhouse", x_front=None, flat=False, arch=0.35, soft=False):
    """The housing over a rear wheel, standing in from a side wall at wall_out over the wheel's middle: an arch along
    the car following the wheel, square across with its top's inboard edge rounded, swept out into the floor at z_base
    and open against the wall. x_front cuts it off square ahead where something stands over the wheel's front half (a
    wagon's rear bench). flat makes it a stamped steel box, flat on top with rounded ends.
    """
    x_mid, half = house.spec["rear_axle"], length / 2
    x_lo, x_hi = x_mid - half, x_mid + half if x_front is None else min(x_mid + half, x_front)
    b = Builder()
    rings = []
    for k in range(21):
        x = lerp(x_lo, x_hi, k / 20)
        rise = max(0.0, 1.0 - ((x - x_mid) / half) ** 2) ** ((0.16 if soft else 0.08) if flat else arch)
        if k == 20 and x_hi < x_mid + half:
            rise = max(rise, 0.15)
        top = height * rise
        reach_top = width * (0.55 + 0.45 * rise)
        ring = []
        for j in range(11):
            a = math.pi / 2 * j / 10
            squareness = (0.14 if soft else 0.08) if flat else 0.25
            reach = reach_top * math.cos(a) ** squareness + (0.0 if flat else 0.03 * (1.0 - math.sin(a)) ** 3)
            ring.append(v(x, house.y(wall_out - reach, driver), z_base + top * math.sin(a) ** squareness))
        ring.append(v(x, house.y(wall_out + 0.01, driver), z_base + top))
        rings.append(ring)
    b.loft(rings, closed=False, caps=False)
    if flat:
        # Soft, its edges rounded a little more, still crisp enough not to read as a cushion.
        return b.finish(name, material, SHELL, solidify=0.01, bevel=0.02 if soft else 0.008, bevel_segments=3 if soft else 2, texels=0.5)
    return b.finish(name, material, SHELL, solidify=0.01, subdivide=1, texels=0.5)


def load_space(house):
    """A wagon's load space behind the rear bench: a carpeted floor over the rear axle, the trim of the sides from the
    floor to the quarter windows, and the trim of the tailgate under its glass."""
    s, q, b_ = house.side, house.spec["quarter"], house.back
    parts = []
    floor_z = FLOOR_Z + 0.20
    inner = q["sill_half"] - 0.05
    b = Builder()
    b.slab([(-1.02, house.y(inner)), (b_["foot"][0] + 0.02, house.y(inner)), (b_["foot"][0] + 0.02, house.y(-inner)), (-1.02, house.y(-inner))],
           v(0, 0, floor_z), (1, 0, 0), (0, 1, 0), 0.02)
    parts.append(b.finish("load floor", "carpet", SHELL, bevel=0.004, texels=0.5))
    lamp_x = house.back_edge_top(0.0) + 0.22
    b = Builder()
    b.slab(rounded_rect(0.15, 0.06, 0.012), v(lamp_x, house.mid, house.roof_z(0.0) - 0.002), (0, -1, 0), (1, 0, 0), -0.006)
    parts.append(b.finish("cargo lamp", "lamp", SHELL, bevel=0.002, texels=1.0))
    b = Builder()
    b.slab(rounded_rect(0.168, 0.078, 0.018), v(lamp_x, house.mid, house.roof_z(0.0) - 0.001), (0, -1, 0), (1, 0, 0), -0.004)
    parts.append(b.finish("cargo lamp bezel", "plastic", SHELL, bevel=0.0015, texels=1.0))
    for driver in (True, False):
        # The side of the load space under its window, from the load floor up to the sill.
        b = Builder()
        grid = []
        for r in range(5):
            z = lerp(floor_z, s["sill_z"] + 0.01, r / 4)
            out = q["sill_half"] - 0.035 - 0.02 * (1 - r / 4)
            # Its front laps inside the rear door's card, so nothing shows between them.
            xs = [lerp(b_["foot"][0] + 0.02, s["rear_foot"] + 0.13, c / 12) for c in range(13)]
            grid.append([v(x, house.y(lerp(out, s["sill_half"] - 0.068, smooth((x - s["rear_foot"] + 0.04) / 0.10)), driver), z) for x in xs])
        b.loft(grid, closed=False, caps=False)
        parts.append(b.finish("load side", "trim", SHELL, solidify=0.015, texels=0.5))
        # A rolled cap along its top at the window's foot, and on the driver's side the jack's cover on its two turn fasteners.
        side = 1 if driver else -1
        b = Builder()
        b.sweep([v(b_["foot"][0] + 0.04, house.y(q["sill_half"] - 0.05, driver), s["sill_z"] + 0.006), v(s["rear_foot"] + 0.10, house.y(q["sill_half"] - 0.05, driver), s["sill_z"] + 0.006)],
                rounded_rect(0.045, 0.026, 0.011), ups=[v(0, 0, 1)] * 2)
        parts.append(b.finish("load side roll", "trim", SHELL, subdivide=1, texels=0.5))
        if driver:
            jx, jz = (b_["foot"][0] + s["rear_foot"]) / 2 - 0.10, (floor_z + s["sill_z"]) / 2 + 0.02
            jy = house.y(q["sill_half"] - 0.052, driver)
            b = Builder()
            b.slab(rounded_rect(0.32, 0.17, 0.025), v(jx, jy, jz), (1, 0, 0), (0, 0, 1), side * 0.006)
            parts.append(b.finish("jack cover", "trim", SHELL, bevel=0.003, texels=0.6))
            for dx in (-0.12, 0.12):
                b = Builder()
                b.lathe([(0.0, 0.0), (0.012, 0.0), (0.012, 0.004), (0.004, 0.006), (0.004, 0.012), (0.0, 0.012)], v(jx + dx, jy - side * 0.006, jz), v(0, -side, 0), count=16)
                parts.append(b.finish("jack cover fastener", "black", SHELL, texels=1.4))
        parts.append(wheelhouse(house, q["sill_half"] - 0.055, floor_z + 0.02, 0.85, 0.20, 0.20, driver, x_front=-1.06))
    # The tailgate's trim under its glass.
    b = Builder()
    x = b_["foot"][0] + 0.015
    wide = q["sill_half"] - 0.037
    b.slab([(house.y(wide), floor_z), (house.y(-wide), floor_z), (house.y(-wide), b_["foot"][2] - 0.01), (house.y(wide), b_["foot"][2] - 0.01)], v(x, 0, 0),
           (0, 1, 0), (0, 0, 1), 0.02)
    parts.append(b.finish("tailgate trim", "trim", SHELL, bevel=0.004, texels=0.5))
    # Its inside release: a chrome pull in a recess at the middle of its top.
    hz = b_["foot"][2] - 0.07
    parts.append(surround("tailgate release bezel", "black", v(x + 0.02, house.mid, hz), (0, -1, 0), (0, 0, 1), 0.12, 0.035, 0.004, lip=0.006))
    b = Builder()
    b.slab(rounded_rect(0.10, 0.012, 0.004), v(x + 0.028, house.mid, hz + 0.006), (0, -1, 0), (0, 0, 1), 0.012)
    parts.append(b.finish("tailgate release", "chrome", SHELL, bevel=0.002, texels=1.6))
    return parts


def back_shelf(house, corners_cut=False):
    """The parcel shelf under the back window, with its speakers, and the trim of the rear quarters."""
    if house.back is None:
        return []
    if "quarter" in house.spec:
        return load_space(house)
    b_ = house.back
    s = house.side
    parts = []
    b = Builder()
    xf = b_["foot"][0] + 0.005
    # Out under the pillars' trim at its ends, so nothing shows between them.
    half = min(s["sill_half"] - 0.035, house.glass_out(b_["foot"][2]) - 0.002)
    # Its front edge just behind the top of the rear bench's back, which leans back 20 degrees from its crease.
    front = -1.02
    if len(house.spec["seats"]) > 2:
        crease = house.spec["seats"][2][0] - 0.20 - 0.11
        front = min(front, crease - math.tan(math.radians(21)) * (b_["foot"][2] - SEAT_Z) - 0.03)
    # On a hatch its front corners stand ahead of the pillars' trim, over the glass: they are cut back.
    cut = 0.05 if corners_cut else 0.0
    outline = [(xf, house.y(half)), (front - 2 * cut, house.y(half)), (front, house.y(half - cut)), (front, house.y(-(half - cut))), (front - 2 * cut, house.y(-half)),
               (xf, house.y(-half))]
    b.slab([(p[0], p[1]) for p in outline], v(0, 0, b_["foot"][2] - 0.012), (1, 0, 0), (0, 1, 0), 0.012)
    parts.append(b.finish("parcel shelf", "carpet", SHELL, bevel=0.004, texels=0.5))
    b = Builder()
    b.slab([(house.y(half - cut), SEAT_Z - 0.05), (house.y(-(half - cut)), SEAT_Z - 0.05), (house.y(-(half - cut)), b_["foot"][2] + 0.002),
            (house.y(half - cut), b_["foot"][2] + 0.002)],
           v(front - 0.006, 0, 0), (0, 1, 0), (0, 0, 1), 0.026)
    parts.append(b.finish("shelf front", "carpet", SHELL, bevel=0.008, bevel_segments=3, texels=0.5))
    for out in (0.42, -0.42):
        b = Builder()
        b.slab(rounded_rect(0.16, 0.11, 0.04), v((front + xf) / 2, house.y(out), b_["foot"][2] - 0.0055), (1, 0, 0), (0, 1, 0), 0.006)
        parts.append(b.finish("shelf speaker", "grille", SHELL, bevel=0.002, texels=1.0))
    # The rear quarters under the glass: a trimmed panel each side from the rear door back to the back window, up to
    # the sill. Above it the pillar behind the side glass is the shell's.
    for driver in (True, False):
        b = Builder()
        rows = 10
        grid = []
        for r in range(rows + 1):
            # Down to the floor behind the bench's end, so nothing shows past it.
            z = lerp(FLOOR_Z + 0.03, s["sill_z"] + 0.01, r / rows)
            x0 = b_["foot"][0] + (b_["top"][0] - b_["foot"][0]) * max(0.0, (z - b_["foot"][2]) / (b_["top"][2] - b_["foot"][2]))
            # Its front edge one straight line behind the rear door's card, top to bottom.
            x1 = s["rear_foot"] + 0.05
            out = house.glass_out(min(max(z, s["sill_z"]), s["top_z"])) - 0.03
            # Where the pillar's trim comes down over it, its top turns out behind that trim, so the trim laps it.
            if z > s["sill_z"] - 0.02:
                out += 0.022
            # Drawn in over its last hand's breadth, where the body's tail rounds in to the back.
            xs = [lerp(min(x0, -1.05), x1, c / 8) for c in range(9)]
            ring = [v(x, house.y(out - 0.035 * max(0.0, 1.0 - (x - xs[0]) / 0.15), driver), z) for x in xs]
            grid.append(ring)
        b.loft(grid, closed=False, caps=False)
        parts.append(b.finish("rear quarter", "trim", SHELL, solidify=0.012, subdivide=1, texels=0.6))
        # Where the shelf stands above the windows' foot, the quarter's trim rises under its ends as a ledge.
        if b_["foot"][2] - 0.012 > s["sill_z"] + 0.004:
            b = Builder()
            out = min(house.glass_out(s["sill_z"]), house.glass_out(b_["foot"][2])) - 0.075
            b.slab([(xf, s["sill_z"]), (front, s["sill_z"]), (front, b_["foot"][2] - 0.010), (xf, b_["foot"][2] - 0.010)], v(0, house.y(out, driver), 0),
                   (1, 0, 0), (0, 0, 1), (1 if driver else -1) * 0.02)
            parts.append(b.finish("shelf ledge", "trim", SHELL, bevel=0.004, texels=0.6))
    return parts


def belts(house):
    """The front belts: a D-ring bolted to each pillar behind the doors at shoulder height, the webbing stowed down the
    pillar's face to the retractor's cover low on it, and a buckle on a stalk at each front seat's inboard side."""
    s = house.side
    parts = []
    bx = (s["b"][0] + s["b"][1]) / 2
    for driver in (True, False):
        side = 1 if driver else -1
        ring_z = s["top_z"] - 0.17
        # Proud of the deeper trim a passenger van has over this pillar, round its windows.
        face = house.glass_out(min(ring_z, s["top_z"])) - (0.06 if "windows" in house.spec else 0.048)
        ring = v(bx + 0.01, house.y(face - 0.006, driver), ring_z)
        b = Builder()
        b.slab(rounded_rect(0.065, 0.034, 0.012), ring, (1, 0, 0), (0, 0, 1), -side * 0.008)
        parts.append(b.finish("belt loop", "chrome", SHELL, bevel=0.003, texels=1.2))
        # The slot the webbing runs through, near the loop's top bar.
        b = Builder()
        b.slab(rounded_rect(0.050, 0.006, 0.0025), ring + v(0, -side * 0.0085, 0.006), (1, 0, 0), (0, 0, 1), -side * 0.001)
        parts.append(b.finish("belt loop slot", "black", SHELL, texels=1.2, smooth_shade=False))
        low = s["sill_z"] - 0.24
        low_face = house.glass_out(s["sill_z"]) - 0.076
        b = Builder()
        # Out of the slot, folded over the loop's top bar and down against the trim.
        # The hanging ply held taut by the retractor: one straight run from just under the loop to the retractor's mouth.
        top = ring + v(0.0, -side * 0.012, -0.03)
        end = v(bx + 0.01, house.y(low_face - 0.02, driver), low + 0.03)
        path = spline([ring + v(0.0, -side * 0.009, 0.006), ring + v(0.0, -side * 0.012, 0.002), top, top.lerp(end, 0.5), end], 16)
        b.sweep(path, rounded_rect(0.048, 0.0025, 0.001), ups=[v(0, -side, 0)] * 16)
        parts.append(b.finish("belt", "webbing", SHELL, texels=0.8))
        b = Builder()
        b.slab(rounded_rect(0.085, 0.13, 0.02), v(bx + 0.01, house.y(low_face, driver), low), (1, 0, 0), (0, 0, 1), -side * 0.03)
        parts.append(b.finish("belt retractor", "plastic", SHELL, bevel=0.006, texels=1.0))
    # The buckles, on stalks at the inboard side of each front seat, by the crease.
    # Buckets: at each seat's inboard side, held clear of a hand brake on the middle line in a narrow car.
    buckets = (max(-0.27, house.mid + 0.06), min(2 * house.mid + 0.27, house.mid - 0.06))
    for y in (house.mid + 0.07, house.mid - 0.07) if house.spec.get("bench") else buckets:
        at = v(-0.15, y, SEAT_Z + 0.01)
        b = Builder()
        b.sweep(spline([at + v(0.02, 0, -0.10), at + v(0.0, 0, -0.02), at + v(-0.01, 0, 0.03)], 6), rounded_rect(0.024, 0.014, 0.005), ups=[v(0, 1, 0)] * 6)
        parts.append(b.finish("buckle stalk", "black", SHELL, texels=1.0))
        b = Builder()
        b.slab(rounded_rect(0.04, 0.075, 0.012), at + v(-0.012, 0, 0.065), (0, 1, 0), (-0.2, 0, 1), 0.02, both=True)
        parts.append(b.finish("buckle", "black", SHELL, bevel=0.003, texels=1.0))
        b = Builder()
        b.slab(rounded_rect(0.014, 0.01, 0.002), at + v(-0.03, 0, 0.09), (0, 1, 0), (0, 0, 1), -0.004)
        parts.append(b.finish("buckle button", "chrome", SHELL, texels=1.0, smooth_shade=False))
    if len(house.spec["seats"]) == 4:
        parts += rear_belts(house)
    return parts


def rear_belts(house):
    """The rear bench's belts: a loop high on each side behind the outer seats, the webbing down to the cushion's side,
    and four buckles on short stalks along the crease, two each side of the middle."""
    s = house.side
    parts = []
    crease = house.spec["seats"][2][0] - 0.21
    for driver in (True, False):
        side = 1 if driver else -1
        ring_z = s["top_z"] - 0.20
        out = house.glass_out(min(ring_z, s["top_z"])) - 0.045
        # On the pillar behind the rear side glass: over the bench where that pillar stands there, further back
        # where the glass runs on behind it (a hatch's quarter window).
        ring_x = min(crease - 0.06, house.c_pillar_x(ring_z) - 0.04) if s.get("rear_foot") is not None else crease - 0.06
        ring = v(ring_x, house.y(out, driver), ring_z)
        b = Builder()
        b.slab(rounded_rect(0.065, 0.034, 0.012), ring, (1, 0, 0), (0, 0, 1), -side * 0.008)
        parts.append(b.finish("rear belt loop", "chrome", SHELL, bevel=0.003, texels=1.2))
        b = Builder()
        b.slab(rounded_rect(0.050, 0.006, 0.0025), ring + v(0, -side * 0.0085, 0.006), (1, 0, 0), (0, 0, 1), -side * 0.001)
        parts.append(b.finish("rear belt loop slot", "black", SHELL, texels=1.2, smooth_shade=False))
        # Stowed: the webbing straight down the trim from the loop to its retractor's cover beside the bench's back.
        low_z = SEAT_Z + 0.12
        low_out = house.glass_out(min(max(low_z, s["sill_z"]), s["top_z"])) - 0.06
        if "quarter" in house.spec:
            # A wagon's load side stands in from the glass: the retractor sits on its face.
            low_out = min(low_out, house.spec["quarter"]["sill_half"] - 0.075)
        b = Builder()
        path = spline([ring + v(0.0, -side * 0.009, 0.006), ring + v(0.0, -side * 0.012, 0.002), ring + v(0.002, -side * 0.012, -0.05),
                       v(ring_x + 0.005, house.y((out + low_out) / 2 - 0.012, driver), (ring_z + low_z) / 2),
                       v(ring_x + 0.01, house.y(low_out - 0.012, driver), low_z + 0.06)], 16)
        b.sweep(path, rounded_rect(0.048, 0.0025, 0.001), ups=[v(0, -side, 0)] * 16)
        parts.append(b.finish("rear belt", "webbing", SHELL, texels=0.8))
        b = Builder()
        b.slab(rounded_rect(0.07, 0.11, 0.018), v(ring_x + 0.01, house.y(low_out, driver), low_z), (1, 0, 0), (0, 0, 1), -side * 0.03)
        parts.append(b.finish("rear belt retractor", "plastic", SHELL, bevel=0.006, texels=1.0))
    for out in (0.10, 0.17, -0.10, -0.17):
        at = v(crease + 0.01, house.y(out), SEAT_Z + 0.06)
        b = Builder()
        b.sweep(spline([at + v(0.0, 0, -0.06), at + v(-0.005, 0, 0.0), at + v(-0.01, 0, 0.025)], 6), rounded_rect(0.022, 0.012, 0.005), ups=[v(0, 1, 0)] * 6)
        parts.append(b.finish("rear buckle stalk", "black", SHELL, texels=1.0))
        b = Builder()
        b.slab(rounded_rect(0.032, 0.065, 0.01), at + v(-0.014, 0, 0.055), (0, 1, 0), (-0.2, 0, 1), 0.016, both=True)
        parts.append(b.finish("rear buckle", "black", SHELL, bevel=0.003, texels=1.0))
    return parts


def details(house):
    """The small things that make it a car people drove: a rubber mat under the driver's feet, the seats' side
    shields and their levers, the belts' buckles on the floor, the bonnet's release under the dash, a clip for
    each visor, and the ash trays in the rear doors."""
    s = house.side
    parts = []
    # The driver's mat, ribbed, laid on the carpet before the seat.
    b = Builder()
    b.slab(rounded_rect(0.42, 0.40, 0.03), v(0.56, 0.02, FLOOR_Z + 0.006), (1, 0, 0), (0, 1, 0), 0.005)
    parts.append(b.finish("floor mat", "mat", SHELL, bevel=0.002, texels=0.7))
    # The front seats' side shields, outboard of each half by the floor, with the lever that slides the seat.
    for driver in (True, False):
        side = 1 if driver else -1
        out = house.y(s["sill_half"] - 0.105, driver)
        b = Builder()
        b.slab(rounded_rect(0.38, 0.09, 0.025), v(0.05, out, SEAT_Z - 0.20), (1, 0, 0), (0, 0, 1), 0.012, both=True)
        parts.append(b.finish("seat shield", "plastic", SHELL, bevel=0.003, texels=0.6))
        b = Builder()
        path = spline([v(0.20, out - side * 0.004, SEAT_Z - 0.22), v(0.26, out - side * 0.012, SEAT_Z - 0.23), v(0.30, out - side * 0.02, SEAT_Z - 0.235)], 8)
        b.sweep(path, rounded_rect(0.008, 0.012, 0.003), ups=[v(0, 0, 1)] * 8)
        parts.append(b.finish("seat lever", "black", SHELL, texels=0.8))
    # The bonnet's release, a small black handle under the dash at the driver's left.
    b = Builder()
    b.slab(rounded_rect(0.06, 0.025, 0.008), v(0.53, house.y(s["sill_half"] - 0.09), -0.47), (1, 0, 0), (0, 0, 1), 0.012)
    parts.append(b.finish("bonnet release", "black", SHELL, bevel=0.003, texels=1.0))
    # The rear doors' ash trays, chrome lids let into the armrests' front.
    if s.get("rear_foot") is not None:
        for driver in (True, False):
            b = Builder()
            b.slab(rounded_rect(0.07, 0.03, 0.006), v(s["b"][1] - 0.10, house.y(s["sill_half"] - 0.07, driver), s["sill_z"] - 0.18), (1, 0, 0), (0, 0, 1), 0.006)
            parts.append(b.finish("rear ash tray", "chrome", SHELL, bevel=0.0015, texels=1.0))
    return parts


def build(spec):
    house = shell.House(spec)
    shell.panes(house)
    shell.lining(house)
    shell.pillars(house)
    dash(house)
    dash_details(house)
    steering_wheel()
    column()
    seats(house)
    doors(house)
    floor(house)
    pedals()
    visors_and_mirror(house)
    door_mirrors(house)
    back_shelf(house)
    belts(house)
    details(house)
    return house


# How far each needle turns from straight up, as the driver sees it, clockwise, at nought and at full scale,
# and what full scale is: km/h for the speed, the share of a tank for the fuel, of hot for the heat.
SPEED_SWEEP = (math.radians(-130), math.radians(130), 136.8)
FUEL_SWEEP = (math.radians(-120), math.radians(-60), 1.0)
HEAT_SWEEP = (math.radians(60), math.radians(120), 1.0)


def info(house, mirrors, needles=None, glow_at=(0.553, -0.03, -0.26)):
    """What the mod is told of the interior beside its triangles. needles is the style's needle table (see
    sedan_needles); the instruments' light comes from glow_at."""
    table = {group: (pivot, sweep) for group, pivot, _, _, sweep in (needles if needles is not None else sedan_needles())}
    floats = []
    # Speed, revs, fuel, heat: the pivot, the axis it turns about, and its sweep; nothing for a needle not there.
    for group in (NEEDLE_SPEED, NEEDLE_REVS, NEEDLE_FUEL, NEEDLE_HEAT):
        if group not in table:
            floats += [0.0] * 9
        else:
            c, sweep = table[group]
            floats += [c.x, c.y, c.z, -1.0, 0.0, 0.0, *sweep]
    mirror_floats = []
    for m in mirrors:
        centre, normal, across, half_w, half_h = m
        mirror_floats += [*centre, *normal, *across, half_w, half_h]
    return {
        "measures": house.spec["measures"],
        "wheel": (*WHEEL_AT, *COLUMN, RIM),
        "needles": floats,
        "mirrors": mirror_floats,
        "lamps": (house.edge("top", 0.0)[0] - 0.13, house.mid, house.roof_z(0.0) - 0.008, *glow_at),
        "glass": glass_layout(house),
    }


def glass_layout(house):
    foot, top = house.screen_at(0.0, 0.0), house.screen_at(0.0, 1.0)
    return {"mid": house.mid, "foot_z": foot.z, "top_z": top.z, "slant": (top - foot).length, "half": house.screen["foot"][3]}
