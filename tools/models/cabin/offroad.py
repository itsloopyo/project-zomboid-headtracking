"""The interior of the Dash Rancher: a short off-roader of the eighties with a hard top. A flat dashboard of painted
steel with a padded rail along its top, the truck's instruments in their bezel, a grab bar before the passenger, the
gear lever and the transfer box's lever on the floor, two high-backed vinyl buckets, a padded bar over the seats, a
rubber floor and steel everywhere it shows.
"""

import math

from . import compact, sedan, shell, truck
from .kit import SHELL, Builder, circle, lerp, rounded_rect, spline, v
from .sedan import FLOOR_Z, SEAT_Z


def extras(house):
    parts = []
    s = house.side
    mid = house.mid
    # The padded rail along the dash's top edge.
    b = Builder()
    path = [v(truck.FACE_X - 0.01, house.y(out), -0.185) for out in (s["sill_half"] - 0.05, 0.0, -(s["sill_half"] - 0.05))]
    b.sweep(path, circle(0.025, 14), ups=[v(0, 0, 1)] * 3)
    parts.append(b.finish("crash rail", "padded", SHELL, subdivide=1, texels=1.0))
    # The grab bar before the passenger.
    # The grab bar before the passenger, over the glove box and clear of its lid, on a bracket at each end.
    gy0, gy1 = house.y(-0.20), house.y(-(s["sill_half"] - 0.12))
    gz = -0.212
    b = Builder()
    b.sweep(spline([v(truck.FACE_X, gy0, gz - 0.012), v(truck.FACE_X - 0.055, gy0 - 0.035, gz), v(truck.FACE_X - 0.055, gy1 + 0.035, gz), v(truck.FACE_X, gy1, gz - 0.012)], 16),
            circle(0.014, 12), ups=[v(0, 0, 1)] * 16)
    parts.append(b.finish("grab bar", "rubber", SHELL, texels=1.0))
    for gy in (gy0, gy1):
        b = Builder()
        b.slab(rounded_rect(0.04, 0.05, 0.01), v(truck.FACE_X - 0.002, gy, gz - 0.012), (0, -1, 0), (0, 0, 1), 0.012)
        parts.append(b.finish("grab bar bracket", "chrome", SHELL, bevel=0.003, texels=1.2))
    # The transfer box's lever beside the gear lever, in its own boot.
    parts += truck.transfer_lever(v(0.17, mid - 0.06, FLOOR_Z + 0.16))
    # The padded bar over the seats: one hoop from the tub's top rail beside each seat back up into a crossbar under the
    # roof, round bends between, and a brace from each upper bend back and down to the tub's rear corner.
    hoop_x = s["b"][0] - 0.04
    foot_out = s["sill_half"] - 0.07
    top_out = house.glass_out(s["top_z"]) - 0.10
    bar_z = s["top_z"] - 0.09
    radius = 0.11
    leg_top = bar_z - radius
    path = [v(hoop_x, house.y(lerp(foot_out, top_out, t)), lerp(s["sill_z"] - 0.01, leg_top, t)) for t in (0.0, 0.5, 1.0)]
    corner = v(hoop_x, house.y(top_out - radius), leg_top)
    side_way = (v(hoop_x, house.y(top_out), 0) - v(hoop_x, house.y(top_out - radius), 0)).normalized()
    for k in range(1, 9):
        a = math.pi / 2 * k / 8
        path.append(corner + side_way * (radius * math.cos(a)) + v(0, 0, radius * math.sin(a)))
    path += [v(p.x, 2 * house.mid - p.y, p.z) for p in reversed(path)]
    rings_n = len(path)
    b = Builder()
    b.sweep(path, circle(0.045, 24), ups=[v(1, 0, 0)] * rings_n)
    parts.append(b.finish("sport bar", "padded", SHELL, texels=0.6))
    for driver in (True, False):
        b = Builder()
        b.slab(rounded_rect(0.12, 0.10, 0.01), v(hoop_x, house.y(foot_out, driver), s["sill_z"] - 0.012), (1, 0, 0), (0, 1, 0), 0.008)
        parts.append(b.finish("sport bar foot", "painted", SHELL, bevel=0.002, texels=0.8))
    corner_x = house.back["foot"][0] + 0.10
    for driver in (True, False):
        b = Builder()
        joint = v(hoop_x, house.y(lerp(foot_out, top_out, 0.88), driver), lerp(s["sill_z"] - 0.01, leg_top, 0.88))
        foot = v(corner_x, house.y(s["sill_half"] - 0.08, driver), s["sill_z"] + 0.03)
        way = (foot - joint).normalized()
        b = Builder()
        b.sweep([joint + way * 0.01, foot], circle(0.032, 24), ups=[v(0, 0, 1)] * 2)
        parts.append(b.finish("sport bar brace", "padded", SHELL, texels=0.6))
        b = Builder()
        b.lathe([(0.040, 0.0), (0.042, 0.006), (0.042, 0.044), (0.040, 0.05)], joint + way * 0.035, way, count=24)
        parts.append(b.finish("sport bar collar", "padded", SHELL, subdivide=1, texels=0.6))
        b = Builder()
        # Under the tube's end, the end just inside the plate's rear edge.
        b.slab(rounded_rect(0.07, 0.06, 0.01), v(corner_x, house.y(s["sill_half"] - 0.08, driver), s["sill_z"] + 0.004) - v(way.x, way.y, 0).normalized() * 0.03,
               (1, 0, 0), (0, 1, 0), 0.008)
        parts.append(b.finish("sport bar brace foot", "painted", SHELL, bevel=0.002, texels=0.8))
    return parts


def levers(house):
    """On the bare tunnel, no console: the gear lever in its boot, nearly upright, and beside it on the right and a
    little behind, the transfer box's shorter lever in a boot of its own."""
    parts = []
    mid = house.mid
    base = v(0.30, mid + 0.01, FLOOR_Z + 0.165)
    b = Builder()
    b.lathe([(0.055, 0.0), (0.05, 0.015), (0.04, 0.035), (0.03, 0.05), (0.02, 0.07), (0.013, 0.085)], base, v(-0.1, 0, 1), count=24)
    parts.append(b.finish("gear boot", "rubber", SHELL, subdivide=1, texels=1.2))
    top = base + v(-0.05, 0, 0.30)
    b = Builder()
    b.sweep(spline([base + v(-0.008, 0, 0.08), base + v(-0.03, 0, 0.2), top], 10), lambda i: circle(0.0075, 12), ups=[v(1, 0, 0)] * 10)
    parts.append(b.finish("gear lever", "chrome", SHELL, texels=1.5))
    b = Builder()
    b.lathe([(0.0, -0.005), (0.016, 0.0), (0.021, 0.012), (0.021, 0.025), (0.016, 0.036), (0.0, 0.04)], top, v(-0.15, 0, 1), count=24)
    parts.append(b.finish("gear knob", "black", SHELL, subdivide=1, texels=2.0))
    return parts


def tailgate(house):
    """The tailgate's inner panel across the back of the tub, up to the hard top's back window, its top capped with a
    rail level with the tub's sides and its handle let into the middle."""
    b_, s = house.back, house.side
    parts = []
    inner = s["sill_half"] - 0.03
    xb = b_["foot"][0] + 0.02
    b = Builder()
    b.slab([(house.y(inner), FLOOR_Z), (house.y(-inner), FLOOR_Z), (house.y(-inner), b_["foot"][2] - 0.004), (house.y(inner), b_["foot"][2] - 0.004)], v(xb, 0, 0),
           (0, 1, 0), (0, 0, 1), 0.02)
    parts.append(b.finish("tailgate panel", "painted", SHELL, bevel=0.004, texels=0.5))
    b = Builder()
    b.sweep([v(xb + 0.01, house.y(inner), b_["foot"][2] - 0.02), v(xb + 0.01, house.y(-inner), b_["foot"][2] - 0.02)], rounded_rect(0.06, 0.03, 0.008), ups=[v(0, 0, 1)] * 2)
    parts.append(b.finish("tailgate rail", "painted", SHELL, bevel=0.003, texels=0.5))
    # Ribs stamped across the plain steel.
    for k in range(3):
        z = lerp(FLOOR_Z + 0.12, b_["foot"][2] - 0.10, k / 2)
        b = Builder()
        b.sweep([v(xb + 0.02, house.y(inner - 0.08), z), v(xb + 0.02, house.y(-(inner - 0.08)), z)], rounded_rect(0.05, 0.02, 0.008), ups=[v(1, 0, 0)] * 2)
        parts.append(b.finish("tailgate rib", "painted", SHELL, bevel=0.002, texels=0.5))
    # Its inside release: a chrome pull in a recess at the middle of its top.
    hz = b_["foot"][2] - 0.06
    parts.append(sedan.surround("tailgate release bezel", "black", v(xb + 0.02, house.mid, hz), (0, -1, 0), (0, 0, 1), 0.15, 0.04, 0.004, lip=0.006))
    b = Builder()
    b.slab(rounded_rect(0.12, 0.014, 0.004), v(xb + 0.028, house.mid, hz + 0.006), (0, -1, 0), (0, 0, 1), 0.012)
    parts.append(b.finish("tailgate release", "chrome", SHELL, bevel=0.002, texels=1.6))
    return parts


def tub(house):
    """The tub behind the seats: its painted inner sides from the floor up to the top rail, where the hard top's
    pillars stand, and a wheelhouse over each rear wheel."""
    s, b_ = house.side, house.back
    parts = []
    inner = s["sill_half"] - 0.03
    x_front, x_back = s["b"][0] + 0.06, b_["foot"][0] + 0.02
    for driver in (True, False):
        b = Builder()
        grid = []
        for r in range(6):
            z = lerp(FLOOR_Z, s["sill_z"] + 0.004, r / 5)
            grid.append([v(lerp(x_back, x_front, c / 12), house.y(inner - 0.015 * math.sin(math.pi * r / 5), driver), z) for c in range(13)])
        b.loft(grid, closed=False, caps=False)
        parts.append(b.finish("tub side", "painted", SHELL, solidify=0.008, texels=0.5))
        b = Builder()
        b.sweep([v(x_back, house.y(inner - 0.02, driver), s["sill_z"]), v(x_front, house.y(inner - 0.02, driver), s["sill_z"])], rounded_rect(0.05, 0.02, 0.006),
                ups=[v(0, 0, 1)] * 2)
        parts.append(b.finish("tub rail", "painted", SHELL, bevel=0.003, texels=0.5))
        parts.append(sedan.wheelhouse(house, inner - 0.01, FLOOR_Z, 0.70, 0.20, 0.24, driver, material="painted"))
    return parts


def build(spec):
    house = shell.House(spec)
    shell.panes(house)
    shell.lining(house)
    shell.pillars(house)
    truck.dash(house, material="painted")
    truck.dash_details(house, dials=truck.ROUND, demister_frac=0.15)
    truck.steering_wheel()
    compact.column()
    levers(house)
    tailgate(house)
    lean = math.radians(14)
    compact.bucket(0.26, -0.16, 0.0, 0.50, SEAT_Z + 0.04, -0.24, lean, "driver seat", head="integrated")
    compact.bucket(0.26, -0.16, 2 * house.mid, 0.50, SEAT_Z + 0.04, -0.24, lean, "passenger seat", head="integrated")
    for driver in (True, False):
        truck.door_card(house, 0.46, spec["side"]["b"][0] + 0.02, driver)
    sedan.floor(house, x_back=spec["back"]["foot"][0] + 0.02, material="floor rubber", tunnel_high=0.16)
    compact.pedals()
    sedan.visors_and_mirror(house)
    sedan.door_mirrors(house)
    extras(house)
    tub(house)
    sedan.belts(house)
    return house


def info(house, mirrors):
    return truck.info(house, mirrors, truck.ROUND)
