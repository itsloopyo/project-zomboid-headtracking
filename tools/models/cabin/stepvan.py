"""The interior of the Chevalier Step Van: a walk-in delivery van. A flat painted steel dash under a windscreen that
stands straight up, the instruments in a pod over the column, a pedestal seat for the driver and a folding one for a helper, an open
floor of ribbed rubber between them, and behind them, open to the cab, the tall box of the body in painted steel.
"""

import math

from . import compact, sedan, shell, truck, van
from .kit import SHELL, Builder, circle, lerp, rounded_rect, spline, v
from .sedan import FLOOR_Z, SEAT_Z


def build(spec):
    house = shell.House(spec)
    shell.panes(house)
    shell.lining(house, seams=False)
    shell.pillars(house)
    # A flat steel dash well ahead under the upright windscreen; the instruments in a pod of their own over the column.
    truck.dash(house, material="painted", face_x=0.64, flat=True)
    truck.dash_details(house, face_x=0.64, glove=False, stack_drop=0.01)
    truck.steering_wheel()
    sedan.column()
    lean = math.radians(10)
    compact.bucket(0.24, -0.16, 0.0, 0.46, SEAT_Z + 0.06, -0.20, lean, "driver seat", head="none", pedestal=True)
    compact.bucket(0.18, -0.14, 2 * house.mid, 0.40, SEAT_Z + 0.06, -0.26, lean, "passenger seat", head="none", pedestal=True)
    sedan.floor(house, x_back=spec["side"]["b"][0] - 0.02, material="floor rubber", tunnel_high=0.0, inset=0.30, kick=False)
    sedan.pedals()
    sedan.visors_and_mirror(house)
    sedan.door_mirrors(house, tall=True, on_post=True)
    van.engine_cover(house, nose=0.08)
    van.box(house)
    doorways(house)
    lap_belts(house)
    bulkhead(house)
    return house


def bulkhead(house):
    """The steel bulkhead under the dash, from the floor's front edge up behind the dash's lip, across the cab."""
    s = house.side
    out = s["sill_half"] - 0.01
    x = min(min(0.98, house.edge("foot", o)[0] + 0.25) for o in (0.0, out)) - 0.005
    foot = min(min(house.edge("foot", o)[1] for o in (0.0, out)) - 0.03, -0.36)
    b = Builder()
    b.slab([(house.y(out), FLOOR_Z - 0.20), (house.y(-out), FLOOR_Z - 0.20), (house.y(-out), foot), (house.y(out), foot)], v(x, 0, 0), (0, 1, 0), (0, 0, 1),
           0.015, both=True)
    return [b.finish("bulkhead", "painted", SHELL, texels=0.4)]


def lap_belts(house):
    """A lap belt at each pedestal seat: the buckle on a short strap at its inboard side, the webbing stowed along
    its outboard side."""
    parts = []
    for y, driver in ((0.0, True), (2 * house.mid, False)):
        side = 1 if driver else -1
        inboard = y - side * 0.25
        b = Builder()
        b.sweep([v(-0.14, inboard, SEAT_Z - 0.06), v(-0.10, inboard, SEAT_Z + 0.06)], rounded_rect(0.03, 0.006, 0.002), ups=[v(0, 1, 0)] * 2)
        parts.append(b.finish("lap belt strap", "webbing", SHELL, texels=1.0))
        b = Builder()
        b.slab(rounded_rect(0.035, 0.07, 0.008), v(-0.095, inboard, SEAT_Z + 0.09), (1, 0, 0.3), (0, 0, 1), 0.014, both=True)
        parts.append(b.finish("lap belt buckle", "black", SHELL, bevel=0.003, texels=1.4))
        outboard = y + side * 0.25
        b = Builder()
        b.sweep([v(-0.14, outboard, SEAT_Z - 0.06), v(-0.12, outboard, SEAT_Z + 0.03), v(0.0, outboard, SEAT_Z + 0.035)], rounded_rect(0.045, 0.004, 0.0015),
                ups=[v(0, 1, 0)] * 3)
        parts.append(b.finish("lap belt", "webbing", SHELL, texels=1.0))
    return parts


def doorways(house):
    """A grab handle up each doorway's post, and the bows across the bare roof of the walk-in cab."""
    s = house.side
    parts = []
    for driver in (True, False):
        x = s["b"][0] + 0.03
        out = s["sill_half"] - 0.04
        b = Builder()
        b.sweep(spline([v(x, house.y(out, driver), -0.30), v(x - 0.045, house.y(out - 0.01, driver), -0.27), v(x - 0.045, house.y(out - 0.01, driver), 0.05),
                        v(x, house.y(out, driver), 0.08)], 16), circle(0.013, 12), ups=[v(1, 0, 0)] * 16)
        parts.append(b.finish("grab handle", "chrome", SHELL, texels=1.0))
    # A step well in each doorway, a step down below the cab's floor with a ribbed tread, and the windscreen's post
    # carried on down to the floor as the doorway's front jamb.
    for driver in (True, False):
        side = 1 if driver else -1
        x0, x1 = s["b"][0], s["a_front"] - 0.05
        y_in, y_out = house.y(s["sill_half"] - 0.30, driver), house.y(s["sill_half"] - 0.01, driver)
        b = Builder()
        b.slab([(x0, y_in), (x1, y_in), (x1, y_out), (x0, y_out)], v(0, 0, FLOOR_Z - 0.20), (1, 0, 0), (0, 1, 0), 0.015)
        parts.append(b.finish("step tread", "floor rubber", SHELL, texels=0.6))
        # A ribbed aluminium nosing along the tread's doorway edge.
        y_edge = house.y(s["sill_half"] - 0.03, driver)
        b = Builder()
        b.slab([(x0 + 0.01, y_edge - 0.02), (x1 - 0.01, y_edge - 0.02), (x1 - 0.01, y_edge + 0.02), (x0 + 0.01, y_edge + 0.02)], v(0, 0, FLOOR_Z - 0.185), (1, 0, 0),
               (0, 1, 0), 0.008)
        for k in range(4):
            dy = lerp(-0.014, 0.014, k / 3)
            b.sweep([v(x0 + 0.015, y_edge + dy, FLOOR_Z - 0.177), v(x1 - 0.015, y_edge + dy, FLOOR_Z - 0.177)], rounded_rect(0.003, 0.003, 0.001), ups=[v(0, 0, 1)] * 2)
        parts.append(b.finish("step tread nosing", "chrome", SHELL, texels=0.8, smooth_shade=False))
        for x, top in ((x0, FLOOR_Z), (x1, -0.30)):
            b = Builder()
            b.slab([(y_in, FLOOR_Z - 0.20), (y_out, FLOOR_Z - 0.20), (y_out, top), (y_in, top)], v(x, 0, 0), (0, 1, 0), (0, 0, 1), 0.015, both=True)
            parts.append(b.finish("step wall", "painted", SHELL, texels=0.5))
        b = Builder()
        b.slab([(x0, FLOOR_Z - 0.20), (x1, FLOOR_Z - 0.20), (x1, FLOOR_Z), (x0, FLOOR_Z)], v(0, y_in, 0), (1, 0, 0), (0, 0, 1), 0.015, both=True)
        parts.append(b.finish("step riser", "painted", SHELL, texels=0.5))
        # Ahead of the step well the riser's line carries on as the toe board's side, up to the dash and on to the
        # bulkhead, so no seam opens between the floor and the well.
        b = Builder()
        b.slab([(x1, FLOOR_Z - 0.20), (0.975, FLOOR_Z - 0.20), (0.975, -0.38), (x1, -0.38)], v(0, y_in, 0), (1, 0, 0), (0, 0, 1), 0.015, both=True)
        parts.append(b.finish("toe board side", "painted", SHELL, texels=0.5))
        b = Builder()
        b.sweep([v(x0, y_in, FLOOR_Z + 0.004), v(x1, y_in, FLOOR_Z + 0.004)], rounded_rect(0.03, 0.012, 0.004), ups=[v(0, 0, 1)] * 2)
        parts.append(b.finish("step nosing", "chrome", SHELL, texels=0.8))
        # The dash's end closed down to the tread by a steel panel flush with it, and the doorway's front jamb, a boxed
        # post down from the windscreen's post.
        b = Builder()
        b.slab([(0.64, FLOOR_Z - 0.20), (x1 + 0.03, FLOOR_Z - 0.20), (x1 + 0.03, -0.36), (0.80, -0.36), (0.66, -0.47), (0.64, -0.46)],
               v(0, house.y(s["sill_half"] - 0.03, driver), 0), (1, 0, 0), (0, 0, 1), side * 0.03)
        parts.append(b.finish("dash end panel", "painted", SHELL, bevel=0.004, texels=0.5))
        b = Builder()
        top = v(x1 - 0.01, house.y(s["sill_half"] - 0.06, driver), s["sill_z"] - 0.02)
        b.sweep([top, v(top.x, top.y, FLOOR_Z - 0.20)], rounded_rect(0.10, 0.06, 0.008), ups=[v(1, 0, 0)] * 2)
        parts.append(b.finish("door jamb", "painted", SHELL, bevel=0.004, texels=0.5))
        # The sliding door shut across the doorway: its plain steel inner skin from the tread up to the glass's foot,
        # two lightening holes, an upright pull, the latch's paddle at its front edge, and its track along the header.
        dy = house.y(house.glass_out(s["sill_z"]) - 0.03, driver)
        b = Builder()
        b.slab([(x0 + 0.03, FLOOR_Z - 0.20), (x1 - 0.01, FLOOR_Z - 0.20), (x1 - 0.01, s["sill_z"] + 0.01), (x0 + 0.03, s["sill_z"] + 0.01)], v(0, dy, 0), (1, 0, 0),
               (0, 0, 1), side * 0.015)
        parts.append(b.finish("sliding door", "painted", SHELL, bevel=0.003, texels=0.5))
        # The glass's channel along its top, a rubber lip on it.
        b = Builder()
        b.sweep([v(x0 + 0.06, dy - side * 0.008, s["sill_z"] + 0.012), v(x1 - 0.05, dy - side * 0.008, s["sill_z"] + 0.012)], rounded_rect(0.016, 0.012, 0.004),
                ups=[v(0, 0, 1)] * 2)
        parts.append(b.finish("sliding door channel", "rubber", SHELL, texels=0.6))
        inner_y = dy - side * 0.0152
        # The holes clear of the dash's end panel ahead, each punched with a rolled flange round a dark well.
        for hole_x in (x0 + 0.20, 0.47):
            at = v(hole_x, inner_y, FLOOR_Z + 0.18)
            b = Builder()
            b.slab(rounded_rect(0.25, 0.12, 0.05), at - v(0, side * 0.0008, 0), (1, 0, 0), (0, 0, 1), -side * 0.001)
            parts.append(b.finish("sliding door hole", "black", SHELL, texels=1.0, smooth_shade=False))
            loop = [at + v(u, 0, w) for (u, w) in rounded_rect(0.25, 0.12, 0.05, 6)]
            b = Builder()
            b.sweep(loop + loop[:1], [(-0.004, 0.0), (0.004, 0.0), (0.003, -side * 0.009), (-0.003, -side * 0.009)], ups=[v(0, 1, 0)] * (len(loop) + 1), caps=False)
            parts.append(b.finish("sliding door hole flange", "painted", SHELL, texels=0.8))
        hx = 0.58
        b = Builder()
        b.sweep(spline([v(hx, inner_y, s["sill_z"] - 0.22), v(hx, inner_y - side * 0.03, s["sill_z"] - 0.20), v(hx, inner_y - side * 0.03, s["sill_z"] - 0.09),
                        v(hx, inner_y, s["sill_z"] - 0.07)], 10), circle(0.009, 10), ups=[v(1, 0, 0)] * 10)
        parts.append(b.finish("sliding door pull", "chrome", SHELL, texels=1.0))
        b = Builder()
        b.slab(rounded_rect(0.08, 0.03, 0.008), v(0.615, inner_y, s["sill_z"] - 0.14), (1, 0, 0), (0, 0, 1), side * 0.008)
        parts.append(b.finish("sliding door latch", "black", SHELL, bevel=0.002, texels=1.2))
        b = Builder()
        track_z = s["top_z"] + 0.02
        b.sweep([v(x0 - 0.05, house.y(house.glass_out(s["top_z"]) - 0.03, driver), track_z), v(min(x1, house.a_pillar_x(s["top_z"]) - 0.10), house.y(house.glass_out(s["top_z"]) - 0.03, driver), track_z)],
                rounded_rect(0.03, 0.04, 0.004), ups=[v(0, 0, 1)] * 2)
        parts.append(b.finish("sliding door track", "painted", SHELL, bevel=0.002, texels=0.5))
    for x in (0.25, -0.05):
        b = Builder()
        half = house.glass_out(s["top_z"]) - 0.03
        path = [v(x, house.y(half), house.roof_z(half) - 0.02), v(x, house.mid, house.roof_z(0.0) - 0.02), v(x, house.y(-half), house.roof_z(half) - 0.02)]
        b.sweep(spline(path, 16), rounded_rect(0.05, 0.03, 0.006), ups=[v(0, 0, -1)] * 16)
        parts.append(b.finish("roof bow", "painted", SHELL, texels=0.5))
    return parts


def info(house, mirrors):
    return truck.info(house, mirrors)
