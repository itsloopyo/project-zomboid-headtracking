"""The interior of the Franklin Valuline and the vans built on it: a full-size American van of the early eighties.
The truck's upright dashboard, an engine cover standing between the two front seats, high-backed buckets, and
behind them a box: in the cargo van bare painted steel with its ribs and a plywood floor, in the passenger van a
lining, carpet, windows down the sides and benches in rows. Two doors at the back, each with its window.
"""

import math

from mathutils import Vector

from . import compact, materials, sedan, shell, truck, upholstery
from .kit import GLASS, SHELL, Builder, circle, lerp, rounded_rect, smooth, spline, v
from .sedan import FLOOR_Z, SEAT_Z


def windows_panes(house):
    """The side windows of the box, where it has any: each between its front and rear edges, at its own sill and top."""
    out = []
    for k, (f_foot, f_top, r_foot, r_top) in enumerate(house.spec.get("windows", [])):
        w = house.spec["window"]
        for driver in (True, False):
            b = Builder()
            rings = []
            for r in range(7):
                t = r / 6
                z = lerp(w["sill_z"], w["top_z"], t)
                out_r = lerp(w["sill_half"], w["top_half"], t)
                x0, x1 = lerp(r_foot, r_top, t), lerp(f_foot, f_top, t)
                rings.append([v(lerp(x0, x1, c / 10), house.y(out_r, driver), z) for c in range(11)])
            b.loft(rings, closed=False, caps=False)
            out.append(b.finish("box pane %d" % k + (" driver" if driver else " passenger"), "glass", GLASS, smooth_shade=False))
    return out


def engine_cover(house, nose=0.0):
    """The cover over the engine between the front seats: a long rounded box from under the dash to behind the seats.
    nose lowers its front, to leave a shadow line under a stack that stands low on a flat dash."""
    parts = []
    mid = house.mid
    b = Builder()
    rings = []
    # Its sides flare out to the floor and its top crowns a little; at its front it runs up under the dashboard.
    profile = ((0.68, 0.42 - nose, 0.42), (0.52, 0.34 - nose / 2, 0.44), (0.25, 0.30, 0.44), (0.00, 0.27, 0.42), (-0.16, 0.24, 0.40), (-0.22, 0.10, 0.38))
    for x, h, w in profile:
        ring = []
        for (u, wv) in rounded_rect(w, h, 0.12, 8):
            low = 0.5 - wv / h
            ring.append(v(x, mid + u * (1.0 + 0.36 * low), FLOOR_Z + h * 0.5 + wv + 0.055 * (1 - (2 * u / w) ** 2) * (wv > 0)))
        rings.append(ring)
    b.loft(rings, closed=True, caps=True)
    parts.append(b.finish("engine cover", "vinyl", SHELL, subdivide=1, texels=0.9))
    # A tray let into its top behind a raised lip; the gear lever is on the column.
    def skin(x, u):
        for (x0, h0, w0), (x1, h1, w1) in zip(profile, profile[1:]):
            if x1 <= x <= x0:
                t = (x - x1) / (x0 - x1)
                h, w = lerp(h1, h0, t), lerp(w1, w0, t)
                return FLOOR_Z + h + 0.055 * (1 - (2 * u / w) ** 2)
        raise ValueError(x)
    # A shallow rubber tray lying on the cover's top with its lean and crown, an even rim round it.
    b = Builder()
    outline = rounded_rect(0.22, 0.20, 0.03, 5)
    b.face([b.vert(v(0.10 + u, mid + w, skin(0.10 + u, w) + 0.003)) for (u, w) in outline])
    parts.append(b.finish("cover tray", "rubber", SHELL, solidify=0.002, texels=0.8, smooth_shade=False))
    b = Builder()
    rim = [v(0.10 + u, mid + w, skin(0.10 + u, w)) for (u, w) in rounded_rect(0.235, 0.215, 0.035, 5)]
    b.sweep(rim + rim[:1], [(-0.0075, 0.014), (0.0075, 0.014), (0.0075, -0.01), (-0.0075, -0.01)], ups=[v(0, 0, 1)] * (len(rim) + 1))
    parts.append(b.finish("cover tray rim", "vinyl", SHELL, bevel=0.003, texels=0.8))
    return parts


def seats(house):
    """High-backed buckets either side of the engine cover, and in the passenger van benches down the box."""
    s = house.side
    parts = []
    lean = math.radians(14)
    parts += compact.bucket(0.25, -0.17, 0.0, 0.52, SEAT_Z + 0.06, -0.24, lean, "driver seat", head="integrated")
    parts += compact.bucket(0.25, -0.17, 2 * house.mid, 0.52, SEAT_Z + 0.06, -0.24, lean, "passenger seat", head="integrated")
    inner = s["sill_half"] - 0.08
    for k, (x, _) in enumerate(house.spec["seats"][2::2]):
        # A row over the rear wheels stops short of their housing.
        over_wheels = abs(x - house.spec["rear_axle"]) < 0.70
        y0, y1 = house.y(s["sill_half"] - 0.37 if over_wheels else inner), house.y(-inner + 0.32)
        parts += sedan.bench(x + 0.24, x - 0.22, y0, y1, SEAT_Z + 0.06, -0.19, math.radians(16), "row %d" % k, pleats=10, headrest=False)
        # Its frame: a tube along the cushion's front, a leg at each end of it and behind, on tracks in the floor; and
        # the lap belts' buckles along its crease.
        front, back = x + 0.20, x - 0.18
        frame_z = SEAT_Z + 0.06 - 0.22
        b = Builder()
        b.sweep([v(front, y0 - 0.03, frame_z), v(front, y1 + 0.03, frame_z)], circle(0.012, 10), ups=[v(0, 0, 1)] * 2)
        parts.append(b.finish("row %d frame" % k, "black", SHELL, texels=0.6))
        for yy in (y0 - 0.06, y1 + 0.06):
            for xx in (front, back):
                b = Builder()
                b.sweep([v(xx, yy, frame_z), v(xx - 0.02, yy, FLOOR_Z + 0.02)], rounded_rect(0.03, 0.02, 0.006), ups=[v(1, 0, 0)] * 2)
                parts.append(b.finish("row %d leg" % k, "black", SHELL, texels=0.6))
            b = Builder()
            b.slab(rounded_rect(0.50, 0.035, 0.006), v(x + 0.01, yy, FLOOR_Z + 0.004), (1, 0, 0), (0, 1, 0), 0.008)
            parts.append(b.finish("row %d track" % k, "chrome", SHELL, bevel=0.002, texels=0.6))
        for t in (0.22, 0.30, 0.70, 0.78):
            at = v(x - 0.20, lerp(y0, y1, t), SEAT_Z + 0.06)
            b = Builder()
            b.sweep(spline([at + v(-0.02, 0, -0.03), at + v(0.0, 0, 0.01), at + v(0.02, 0, 0.03)], 6), rounded_rect(0.022, 0.003, 0.001), ups=[v(0, 1, 0)] * 6)
            parts.append(b.finish("row %d buckle strap" % k, "webbing", SHELL, texels=1.0))
            b = Builder()
            b.slab(rounded_rect(0.032, 0.065, 0.01), at + v(0.045, 0, 0.04), (0, 1, 0), (0.8, 0, 0.6), 0.016, both=True)
            parts.append(b.finish("row %d buckle" % k, "black", SHELL, bevel=0.003, texels=1.0))
    return parts


def box(house):
    """The van's box behind the cab: its floor, its walls and wheel arches, its roof, and its two back doors' trim."""
    s, b_ = house.side, house.back
    seated = len(house.spec["seats"]) > 2
    parts = []
    x_front, x_back = s["b"][0] - 0.03, b_["foot"][0] + 0.02
    inner = s["sill_half"] - 0.04
    # The floor: plywood in a cargo van, carpet where people ride.
    b = Builder()
    b.slab([(x_front, house.y(inner)), (x_back, house.y(inner)), (x_back, house.y(-inner)), (x_front, house.y(-inner))], v(0, 0, FLOOR_Z - 0.005), (1, 0, 0),
           (0, 1, 0), 0.02)
    parts.append(b.finish("box floor", "carpet" if seated else "painted", SHELL, texels=0.5))
    # The walls, from the floor to the roof: a passenger van's stop round its side windows, each with a reveal and a
    # pillar between the panes; a cargo van's are bare steel with ribs pressed in, roof bows over and ribs in the floor.
    roof = house.roof_z(inner)
    window = house.spec.get("window")
    spans = sorted((min(w[2], w[3]), max(w[0], w[1])) for w in house.spec.get("windows", []))

    # Flat and upright to the windows' foot, then drawn in with the glass to the roof.
    def wall_out(z):
        return min(inner, house.glass_out(max(z, house.side["sill_z"])) - 0.04)

    # Drawn in over the last hand's breadth, where the body's tail rounds in to the back doors.
    def tail(x):
        return 0.03 * max(0.0, 1.0 - (x - x_back) / 0.12)

    def patch(x0, x1, z0, z1, driver, name):
        b = Builder()
        grid = []
        rows = max(2, int((z1 - z0) / 0.1) + 1)
        for r in range(rows + 1):
            z = lerp(z0, z1, r / rows)
            grid.append([v(lerp(x0, x1, c / 12), house.y(wall_out(z) - tail(lerp(x0, x1, c / 12)), driver), z) for c in range(13)])
        b.loft(grid, closed=False, caps=False)
        return b.finish(name, "trim" if seated else "painted", SHELL, solidify=0.012, texels=0.5)
    for driver in (True, False):
        if window and spans:
            parts.append(patch(x_back - 0.014, x_front, FLOOR_Z, window["sill_z"] + 0.01, driver, "box wall"))
            parts.append(patch(x_back - 0.014, x_front, window["top_z"] - 0.01, roof, driver, "box wall top"))
            edges = [x_back] + [x for span in spans for x in span] + [x_front]
            for k in range(0, len(edges), 2):
                x0, x1 = edges[k], edges[k + 1]
                lo, hi = (x0 - 0.06, x1 + 0.06) if x1 - x0 < 0.12 else (x0, x1)
                parts.append(patch(lo, hi, window["sill_z"], window["top_z"], driver, "box pillar"))
            # A moulded garnish round each window, lapping the pillars and the walls above and below it.
            for x_lo, x_hi in spans:
                cx, cz = (x_lo + x_hi) / 2, (window["sill_z"] + window["top_z"]) / 2
                outline = rounded_rect(x_hi - x_lo - 0.07, window["top_z"] - window["sill_z"] - 0.04, 0.05, 5)
                loop = [v(cx + u, house.y(wall_out(cz + w) - 0.01, driver), cz + w) for (u, w) in outline]
                b = Builder()
                b.sweep(loop + loop[:1], rounded_rect(0.045, 0.014, 0.006), ups=[v(0, -1 if driver else 1, 0)] * (len(loop) + 1))
                parts.append(b.finish("window garnish", "trim", SHELL, subdivide=0, texels=0.6))
            # The lower wall's trim broken by a moulding along it, and a pad for the arm at each bench's end.
            # The side door round the first window, as the game gives it on both walls: its card the wall's own trim, divided
            # off by dark shut lines, its handle in a cup near its front edge and a step plate along its foot.
            door_lo, door_hi = spans[-1][0] - 0.05, spans[-1][1] + 0.04
            ins = -1 if driver else 1
            top_z = min(window["top_z"] + 0.035, roof - 0.02)
            b = Builder()
            for x in (door_lo, door_hi):
                path = [v(x, house.y(wall_out(z) - 0.0125, driver), z) for z in (lerp(FLOOR_Z + 0.01, top_z, t / 8) for t in range(9))]
                b.sweep(path, rounded_rect(0.010, 0.004, 0.0015), ups=[v(0, ins, 0)] * 9)
            for z in (top_z, FLOOR_Z + 0.05):
                b.sweep([v(door_lo, house.y(wall_out(z) - 0.0125, driver), z), v(door_hi, house.y(wall_out(z) - 0.0125, driver), z)],
                        rounded_rect(0.010, 0.004, 0.0015), ups=[v(0, ins, 0)] * 2)
            parts.append(b.finish("side door shut line", "black", SHELL, texels=0.5, smooth_shade=False))
            hz = window["sill_z"] - 0.11
            hx = door_hi - 0.09
            wall_y = house.y(wall_out(hz) - 0.012, driver)
            b = Builder()
            b.slab(rounded_rect(0.13, 0.05, 0.018), v(hx, wall_y, hz), (1, 0, 0), (0, 0, 1), ins * 0.003)
            parts.append(b.finish("side door handle cup", "black", SHELL, bevel=0.001, texels=2.0))
            b = Builder()
            b.sweep(spline([v(hx + 0.045, wall_y + ins * 0.006, hz), v(hx, wall_y + ins * 0.011, hz + 0.002), v(hx - 0.045, wall_y + ins * 0.009, hz + 0.001)], 10),
                    rounded_rect(0.007, 0.013, 0.003), ups=[v(0, 0, 1)] * 10)
            parts.append(b.finish("side door handle", "chrome", SHELL, texels=2.0))
            b = Builder()
            b.lathe([(0.0, 0.0), (0.006, 0.0), (0.006, 0.03), (0.0085, 0.034), (0.0085, 0.046), (0.0, 0.05)],
                    v(door_hi - 0.16, house.y(wall_out(window["sill_z"]) - 0.02, driver), window["sill_z"] - 0.012), v(0, 0, 1), count=12)
            parts.append(b.finish("side door lock knob", "black", SHELL, subdivide=1, texels=2.0))
            b = Builder()
            b.slab(rounded_rect(door_hi - door_lo - 0.02, 0.07, 0.006), v((door_lo + door_hi) / 2, house.y(wall_out(FLOOR_Z) - 0.045, driver), FLOOR_Z + 0.017), (1, 0, 0),
                   (0, 1, 0), 0.006)
            parts.append(b.finish("side door step plate", "chrome", SHELL, bevel=0.002, texels=0.5))
            # The lower wall's trim broken by a moulding along it, cut at the door's shut lines.
            z = window["sill_z"] - 0.25
            for x0, x1 in ((x_back + 0.12, door_lo - 0.012), (door_lo + 0.012, door_hi - 0.012), (door_hi + 0.012, x_front - 0.03)):
                if x1 - x0 < 0.05:
                    continue
                b = Builder()
                b.sweep([v(x0, house.y(wall_out(z) - 0.012, driver), z), v(x1, house.y(wall_out(z) - 0.012, driver), z)],
                        rounded_rect(0.035, 0.016, 0.006), ups=[v(0, ins, 0)] * 2)
                parts.append(b.finish("wall moulding", "trim", SHELL, bevel=0.003, texels=0.5))
            for x, _ in house.spec["seats"][2::2]:
                if driver:
                    b = Builder()
                    b.slab(rounded_rect(0.26, 0.06, 0.02), v(x - 0.02, house.y(wall_out(SEAT_Z) - 0.03, driver), SEAT_Z + 0.20), (1, 0, 0), (0, 1, 0), 0.05)
                    parts.append(b.finish("bench arm pad", "vinyl", SHELL, bevel=0.012, texels=0.8))
        else:
            parts.append(patch(x_back - 0.014, x_front, FLOOR_Z, roof, driver, "box wall"))
        parts.append(sedan.wheelhouse(house, wall_out(FLOOR_Z), FLOOR_Z, 0.95, 0.27, 0.28, driver, material="carpet" if seated else "painted",
                                      name="wheel arch", flat=True, soft=True))
        # Ribs pressed into a cargo van's steel every half metre, each a hat section following the wall, and two load
        # rails along it.
        if not seated:
            for z in (FLOOR_Z + 0.45, FLOOR_Z + 0.95):
                if z > roof - 0.15:
                    continue
                b = Builder()
                b.sweep([v(x_back + 0.12, house.y(wall_out(z) - 0.014, driver), z), v(x_front - 0.03, house.y(wall_out(z) - 0.014, driver), z)],
                        rounded_rect(0.07, 0.028, 0.008), ups=[v(0, -1 if driver else 1, 0)] * 2)
                parts.append(b.finish("load rail", "painted", SHELL, bevel=0.003, texels=0.5))
            count = max(2, int((x_front - x_back) / 0.5))
            for k in range(count):
                x = lerp(x_back + 0.3, x_front - 0.3, k / (count - 1))
                b = Builder()
                path = [v(x, house.y(wall_out(z) - 0.012, driver), z) for z in (lerp(FLOOR_Z + 0.04, roof - 0.02, t / 10) for t in range(11))]
                b.sweep(path, rounded_rect(0.065, 0.03, 0.01), ups=[v(0, -1 if driver else 1, 0)] * 11)
                parts.append(b.finish("rib", "painted", SHELL, texels=0.5))
    if seated:
        # The lining's moulded ribs across the roof, and a lamp over each row.
        for k in range(5):
            x = lerp(x_back + 0.25, x_front - 0.25, k / 4)
            b = Builder()
            half = wall_out(roof - 0.02) - 0.004
            path = [v(x, house.y(half), roof - 0.012), v(x, house.mid, house.roof_z(0.0) - 0.012), v(x, house.y(-half), roof - 0.012)]
            b.sweep(spline(path, 16), rounded_rect(0.05, 0.016, 0.007), ups=[v(0, 0, -1)] * 16)
            parts.append(b.finish("lining rib", "lining", SHELL, texels=0.5))
        for x, _ in house.spec["seats"][2::2]:
            b = Builder()
            b.slab(rounded_rect(0.25, 0.10, 0.02), v(x + 0.05, house.mid, house.roof_z(0.0) - 0.004), (1, 0, 0), (0, 1, 0), -0.02)
            parts.append(b.finish("dome lamp", "lamp", SHELL, bevel=0.006, texels=1.0))
    if not seated:
        count = max(2, int((x_front - x_back) / 0.5))
        for k in range(count):
            x = lerp(x_back + 0.3, x_front - 0.3, k / (count - 1))
            b = Builder()
            half = wall_out(roof - 0.02) - 0.02
            path = [v(x, house.y(half), roof - 0.02), v(x, house.mid, house.roof_z(0.0) - 0.02), v(x, house.y(-half), roof - 0.02)]
            b.sweep(spline(path, 16), rounded_rect(0.06, 0.045, 0.01), ups=[v(0, 0, -1)] * 16)
            parts.append(b.finish("roof bow", "painted", SHELL, texels=0.5))
        # The cargo lamp between the first two bows, and tie-down rings along each wall's foot.
        b = Builder()
        b.slab(rounded_rect(0.24, 0.10, 0.02), v(lerp(x_back + 0.3, x_front - 0.3, 0.5 / (count - 1)), house.mid, house.roof_z(0.0) - 0.004), (1, 0, 0), (0, 1, 0), -0.022)
        parts.append(b.finish("cargo lamp", "lens", SHELL, bevel=0.006, texels=1.0))
        for driver in (True, False):
            for k in range(3):
                x = lerp(x_back + 0.35, x_front - 0.35, k / 2)
                b = Builder()
                b.slab(rounded_rect(0.07, 0.05, 0.01), v(x, house.y(wall_out(FLOOR_Z) - 0.005, driver), FLOOR_Z + 0.06), (1, 0, 0), (0, 0, 1), 0.006, both=True)
                parts.append(b.finish("tie down plate", "painted", SHELL, bevel=0.002, texels=1.0))
                b = Builder()
                b.sweep(spline([v(x - 0.025, house.y(wall_out(FLOOR_Z) - 0.01, driver), FLOOR_Z + 0.07), v(x, house.y(wall_out(FLOOR_Z) - 0.045, driver), FLOOR_Z + 0.05),
                                v(x + 0.025, house.y(wall_out(FLOOR_Z) - 0.01, driver), FLOOR_Z + 0.07)], 10), circle(0.005, 8), ups=[v(0, 0, 1)] * 10)
                parts.append(b.finish("tie down ring", "chrome", SHELL, texels=1.0))
        for k in range(7):
            y = lerp(house.y(inner - 0.32), house.y(-(inner - 0.32)), k / 6)
            b = Builder()
            b.sweep([v(x_back + 0.02, y, FLOOR_Z + 0.015), v(x_front - 0.02, y, FLOOR_Z + 0.015)], rounded_rect(0.04, 0.012, 0.004), ups=[v(0, 0, 1)] * 2)
            parts.append(b.finish("floor rib", "painted", SHELL, texels=0.5))
    # The back doors' inner skins under their windows, one each side of the middle: a frame round each, two lightening
    # holes in a cargo van's bare steel, and the latch's handle on the right-hand door.
    skin = "trim" if seated else "painted"
    top_z = b_["foot"][2] - 0.005
    for driver in (True, False):
        y0, y1 = house.y(0.0, driver), house.y(wall_out(FLOOR_Z) - tail(x_back) + 0.004, driver)
        b = Builder()
        b.slab([(y0, FLOOR_Z), (y1, FLOOR_Z), (y1, top_z), (y0, top_z)], v(x_back - 0.005, 0, 0), (0, 1, 0), (0, 0, 1), 0.02)
        parts.append(b.finish("back door", skin, SHELL, bevel=0.004, texels=0.5))
        yc, half = (y0 + y1) / 2, abs(y1 - y0) / 2
        b = Builder()
        frame = [v(x_back + 0.018, yc + u, (FLOOR_Z + top_z) / 2 + w) for (u, w) in rounded_rect(2 * half - 0.05, top_z - FLOOR_Z - 0.06, 0.03, 4)]
        b.sweep(frame + frame[:1], rounded_rect(0.035, 0.016, 0.005), ups=[v(1, 0, 0)] * (len(frame) + 1))
        parts.append(b.finish("back door frame", skin, SHELL, texels=0.5))
        if not seated:
            for k in (0, 1):
                b = Builder()
                b.slab(rounded_rect(0.16, 0.08, 0.04), v(x_back + 0.016, yc, lerp(FLOOR_Z + 0.22, top_z - 0.16, k)), (0, -1, 0), (0, 0, 1), 0.002)
                parts.append(b.finish("lightening hole", "black", SHELL, texels=1.0, smooth_shade=False))
    # The doors' meeting stile up the middle, across the back window, and a post in each back corner where the doors
    # hang, closing the shut lines.
    b = Builder()
    b.slab(rounded_rect(0.05, roof - FLOOR_Z, 0.008), v(x_back + 0.018, house.mid, (FLOOR_Z + roof) / 2), (0, -1, 0), (0, 0, 1), 0.02, both=True)
    parts.append(b.finish("back door stile", skin, SHELL, bevel=0.004, texels=0.5))
    for driver in (True, False):
        b = Builder()
        # Following the wall as it leans in with the body above the waist, so no light shows between them.
        path = [v(x_back + 0.02, house.y(wall_out(z) - tail(x_back) - 0.045, driver), z) for z in (lerp(FLOOR_Z, roof - 0.01, t / 12) for t in range(13))]
        b.sweep(path, rounded_rect(0.11, 0.07, 0.01), ups=[v(1, 0, 0)] * 13)
        parts.append(b.finish("back corner post", skin, SHELL, bevel=0.004, texels=0.5))
    b = Builder()
    hy, hz = house.y(-0.12), (FLOOR_Z + top_z) / 2 + 0.05
    b.slab(rounded_rect(0.05, 0.12, 0.012), v(x_back + 0.016, hy, hz), (0, -1, 0), (0, 0, 1), 0.004)
    parts.append(b.finish("back door handle plate", "black", SHELL, bevel=0.0015, texels=1.6))
    b = Builder()
    b.sweep(spline([v(x_back + 0.02, hy, hz + 0.04), v(x_back + 0.05, hy, hz + 0.02), v(x_back + 0.05, hy, hz - 0.04)], 8), rounded_rect(0.014, 0.012, 0.004),
            ups=[v(0, 1, 0)] * 8)
    parts.append(b.finish("back door handle", "chrome", SHELL, texels=1.6))
    # The doors' panels beside and over the back window, out to the walls and up to the roof.
    for sign in (1, -1):
        def glass_edge(t, sign=sign):
            return house.back_at(sign * 1.0, t) + v(0.008, 0, 0)

        def wall_edge(t, sign=sign):
            p = house.back_at(sign * 1.0, t)
            return v(p.x + 0.008, house.y(wall_out(p.z) - tail(x_back) + 0.006, sign > 0), p.z)
        panel = shell.trimmed_between(glass_edge, wall_edge, "back door panel", bulge=0.0, subdivide=0)
        panel["material"] = "trim" if seated else "painted"
        parts.append(panel)
    top = b_["top"][2]
    b = Builder()
    b.slab([(house.y(inner), top), (house.y(-inner), top), (house.y(-inner), roof), (house.y(inner), roof)], v(b_["top"][0] + 0.01, 0, 0), (0, 1, 0), (0, 0, 1), 0.02)
    parts.append(b.finish("back door header", "trim" if seated else "painted", SHELL, bevel=0.004, texels=0.5))
    return parts


def build(spec):
    house = shell.House(spec)
    shell.panes(house)
    windows_panes(house)
    shell.lining(house, seams=False)
    shell.pillars(house)
    truck.dash(house)
    truck.dash_details(house)
    truck.steering_wheel()
    sedan.column()
    engine_cover(house)
    seats(house)
    for driver in (True, False):
        truck.door_card(house, 0.46, spec["side"]["b"][0] + 0.02, driver)
    sedan.floor(house, x_back=spec["side"]["b"][0] - 0.02, material="floor rubber", tunnel_high=0.0)
    sedan.pedals()
    sedan.visors_and_mirror(house)
    sedan.door_mirrors(house, tall=True)
    box(house)
    sedan.belts(house)
    return house


def info(house, mirrors):
    return truck.info(house, mirrors)
