"""The interior of the Dash Elite and the Chevalier Primani: a family saloon of about 1990. A low, rounded dashboard
in soft-grained plastic that sweeps round to the doors, the instruments under a rounded hood, a four-spoke wheel with
a broad pad, a floor console with the automatic's selector and an armrest, cloth buckets, door cards with the
windows' switches in the armrest, and a rear bench.
"""

import math

from . import compact, luxury, materials, sedan, shell, upholstery
from .kit import NEEDLE_FUEL, NEEDLE_HEAT, NEEDLE_REVS, NEEDLE_SPEED, SHELL, Builder, lerp, rounded_rect, smooth, spline, v
from .sedan import FLOOR_Z, SEAT_Z

# One black face under the hood: the speedometer in the middle, the revs to its left, fuel and heat to its right.
SPEEDO = (v(0.562, 0.030, -0.243), 0.050)
REVS = (v(0.562, -0.093, -0.245), 0.042)
COMBI = (v(0.562, 0.150, -0.245), 0.040)


def needles():
    return [(NEEDLE_SPEED, SPEEDO[0], SPEEDO[1], 0.92, sedan.SPEED_SWEEP), (NEEDLE_REVS, REVS[0], REVS[1], 0.92, (math.radians(-130), math.radians(130), 1.0)),
            (NEEDLE_FUEL, COMBI[0] + v(0, 0.012, 0), COMBI[1], 0.62, sedan.FUEL_SWEEP), (NEEDLE_HEAT, COMBI[0] + v(0, -0.012, 0), COMBI[1], 0.62, sedan.HEAT_SWEEP)]


def dash(house):
    """A low rounded top that falls in one sweep to a face drawn back at its ends into the doors, and before the
    driver a rounded hood over a recess for the instruments."""
    s = house.side
    inner = s["sill_half"] - 0.030
    rings = []
    for k in range(65):
        out = lerp(inner, -inner, k / 64)
        y = house.y(out)
        xf, zf = house.edge("foot", abs(out))
        xf -= 0.01
        # Towards the doors the face sweeps back, wrapping round the people in front.
        wrap = 0.05 * smooth((abs(out) - 0.35) / 0.35)
        fx = 0.54 - wrap
        top = max(zf + 0.004, -0.215)
        normal = [(xf + 0.05, zf - 0.07), (xf + 0.004, zf + 0.001), (lerp(xf, fx, 0.45), top + 0.012), (fx + 0.04, top + 0.006), (fx + 0.008, top - 0.008),
                  (fx - 0.006, top - 0.03), (fx - 0.008, top - 0.06), (fx - 0.004, -0.31), (fx + 0.004, -0.37), (fx + 0.02, -0.41), (fx + 0.05, -0.44),
                  (0.68, -0.46), (0.82, -0.36)]
        hood = [(xf + 0.05, zf - 0.07), (xf + 0.004, zf + 0.001), (0.63, -0.160), (0.55, -0.147), (0.525, -0.154), (0.515, -0.168), (0.522, -0.182),
                (0.585, -0.188), (0.592, -0.29), (0.535, -0.31), (fx + 0.02, -0.41), (fx + 0.05, -0.44), (0.68, -0.46), (0.82, -0.36)]
        w = smooth((0.26 - abs(y)) / 0.06) if abs(y) < 0.26 else 0.0
        rings.append([v(x, y, z) for (x, z) in sedan.blend_sections(normal, hood, w)])
    b = Builder()
    b.loft(rings, closed=True, caps=True)
    return b.finish("dashboard", "padded", SHELL, subdivide=2, coarsest=1, texels=1.5, sharp=50)


def dash_details(house):
    parts = []
    s = house.side
    mid = house.mid
    b = Builder()
    b.slab(rounded_rect(0.42, 0.112, 0.02), v(0.570, 0.03, -0.244), (0, -1, 0), (0, 0, 1), 0.004)
    parts.append(b.finish("instrument panel", "black", SHELL, texels=1.5, smooth_shade=False))
    for name, (c, r), picture in (("speedometer", SPEEDO, "speedometer"), ("revs", REVS, "revs"), ("fuel and heat", COMBI, "fuel-heat")):
        parts += sedan.instrument(name, c, r, picture, ring=False)
    for group, pivot, radius, length, _ in needles():
        parts += sedan.needle(group, pivot, radius, length)
    # Slim slatted vents in the face's middle and at its ends.
    for dy in (-0.07, 0.07):
        parts += sedan.vent(v(0.534, mid + dy, -0.254), (0, 1, 0), 0.12, 0.04)
    for out in (s["sill_half"] - 0.10, -(s["sill_half"] - 0.10)):
        # Turned with the face where it wraps back towards the doors.
        parts += sedan.vent(v(0.497, house.y(out), -0.245), (-0.15 if out > 0 else 0.15, 1, 0), 0.08, 0.04)
    # The glove box, its lid moulded into the face.
    gy = house.y(-0.45)
    b = Builder()
    # Its door cut from the face: a lid barely proud inside a dark shut line, its pull let into its top corner.
    lid = b.panel(rounded_rect(0.34, 0.10, 0.014, 6), v(0.40, gy, -0.318), (0, -1, 0), (0, 0, 1))
    lid = b.finish("glove box", "padded", SHELL, solidify=0.001, texels=1.0)
    lid["lay"] = 0.0016
    parts.append(lid)
    b = Builder()
    b.panel(rounded_rect(0.348, 0.108, 0.018, 6), v(0.40, gy, -0.318), (0, -1, 0), (0, 0, 1))
    line = b.finish("glove box shut line", "black", SHELL, solidify=0.004, texels=1.0, smooth_shade=False)
    line["lay"] = 0.0005
    parts.append(line)
    b = Builder()
    # Its latch: a pull in a rectangular bezel at the lid's top edge, the lock's cylinder beside it.
    b.slab(rounded_rect(0.07, 0.025, 0.006), v(0.40, gy + 0.10, -0.283), (0, -1, 0), (0, 0, 1), 0.003)
    handle = b.finish("glove box handle", "black", SHELL, bevel=0.001, texels=1.6)
    handle["lay"] = 0.0046
    parts.append(handle)
    b = Builder()
    b.lathe([(0.0, 0.0), (0.011, 0.0), (0.011, 0.003), (0.009, 0.004), (0.0, 0.004)], v(0.40, gy + 0.045, -0.283), v(-1, 0, 0), count=16)
    lock = b.finish("glove box lock", "chrome", SHELL, texels=2.0)
    lock["lay"] = 0.0046
    parts.append(lock)
    parts += sedan.demisters(house, 0.54)
    # The headlamps' rotary switch in a bezel on the face left of the cluster's hood.
    b = Builder()
    b.lathe([(0.0, 0.0), (0.024, 0.0), (0.024, 0.003), (0.020, 0.005), (0.0, 0.005)], v(0.531, 0.30, -0.27), v(-1, 0, 0), count=24)
    parts.append(b.finish("lamp switch bezel", "black", SHELL, texels=2.0))
    b = Builder()
    b.lathe([(0.0, 0.0), (0.015, 0.0), (0.015, 0.012), (0.013, 0.016), (0.0, 0.017)], v(0.527, 0.30, -0.27), v(-1, 0, 0), count=24)
    parts.append(b.finish("lamp switch", "plastic", SHELL, subdivide=1, texels=2.0))
    return parts


def seats(house):
    s = house.side
    parts = []
    lean = math.radians(17)
    parts += compact.bucket(0.27, -0.16, 0.0, 0.52, SEAT_Z + 0.01, -0.16, lean, "driver seat")
    parts += compact.bucket(0.27, -0.16, 2 * house.mid, 0.52, SEAT_Z + 0.01, -0.16, lean, "passenger seat")
    inner = s["sill_half"] - 0.07
    rear = house.spec["seats"][2][0]
    parts += sedan.bench(rear + 0.25, rear - 0.20, house.y(inner), house.y(-inner), SEAT_Z + 0.03, -0.14, math.radians(21), "rear seat", pleats=10,
                         headrest=False, dishes=(0.24, 0.76))
    return parts


def doors(house):
    s = house.side
    parts = []
    for driver in (True, False):
        parts += sedan.door_card(house, 0.52, s["b"][0] + 0.02, driver, power=True)
        parts += sedan.door_card(house, s["b"][1] - 0.02, s["rear_foot"] + 0.05, driver, rear=True, power=True)
    return parts


def build(spec):
    house = shell.House(spec)
    shell.panes(house)
    shell.lining(house)
    shell.pillars(house)
    dash(house)
    dash_details(house)
    luxury.steering_wheel(rim="wheel", spokes="padded", boss_skin="padded", pad=(0.20, 0.14), angles=(60, -60, 120, -120))
    compact.column()
    # The stack let into the dash's face, standing only a bezel's depth proud of it.
    luxury.console(house, skin="plastic", trim="black", face_x=0.530, turn=8.0)
    seats(house)
    doors(house)
    sedan.floor(house, tunnel_high=0.14)
    sedan.pedals()
    sedan.visors_and_mirror(house)
    sedan.door_mirrors(house, aero=True)
    sedan.back_shelf(house)
    sedan.belts(house)
    return house


def info(house, mirrors):
    return sedan.info(house, mirrors, needles(), glow_at=(SPEEDO[0].x, 0.03, -0.235))
