"""The shapes an interior is made of, built straight into Blender meshes.

Every length is in tiles, which are the game's metres, in the cabin's own frame: from the driver's eye
as the mod seats it, x ahead along the vehicle, y to its left, z up. A part is a Blender object with a
material named for what it is made of and a group that says how the mod moves it.
"""

import math

import bmesh
import bpy
from mathutils import Matrix, Vector

# How the mod moves each part: SHELL stays put, WHEEL turns about the column, a NEEDLE about its own
# pivot, GLASS is the panes the mod draws as glass, MIRROR the faces that show the road behind.
SHELL, WHEEL, NEEDLE_SPEED, NEEDLE_REVS, NEEDLE_FUEL, NEEDLE_HEAT, GLASS, MIRROR = range(8)
GROUPS = 8

PARTS = []
# How many levels of subdivision every part is built with fewer than it asks for: none for the model the detail is
# baked from, more for the one the game draws.
COARSER = [0]


def levels(asked, coarsest=0):
    """The levels a part is built with; coarsest keeps a part whose shape folds without them from going below it."""
    return max(min(asked, coarsest), asked - COARSER[0])


# How far below the eye the floor of the cabin is, in metres.
FLOOR_Z = -0.82

def v(x, y, z):
    return Vector((x, y, z))


def smooth(t):
    t = min(max(t, 0.0), 1.0)
    return t * t * (3.0 - 2.0 * t)


def lerp(a, b, t):
    return a + (b - a) * t


def spline(points, count):
    """count places along a Catmull-Rom curve through points (Vectors), ends included."""
    points = [Vector(p) for p in points]
    ext = [points[0] * 2 - points[1]] + points + [points[-1] * 2 - points[-2]]
    lengths = [(ext[i + 1] - ext[i]).length for i in range(1, len(ext) - 2)]
    total = sum(lengths)
    out = []
    for k in range(count):
        target = total * k / (count - 1)
        seg = 0
        while seg < len(lengths) - 1 and target > lengths[seg]:
            target -= lengths[seg]
            seg += 1
        t = target / lengths[seg] if lengths[seg] > 0 else 0.0
        p0, p1, p2, p3 = ext[seg], ext[seg + 1], ext[seg + 2], ext[seg + 3]
        t2, t3 = t * t, t * t * t
        out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3))
    return out


def rounded_rect(width, height, radius, per_corner=None):
    """A rounded rectangle about the origin, counter-clockwise, as (u, v) pairs."""
    if per_corner is None:
        per_corner = 6 if COARSER[0] == 0 else 2
    radius = min(radius, width / 2 - 1e-5, height / 2 - 1e-5)
    out = []
    for cx, cy, start in ((width / 2 - radius, height / 2 - radius, 0.0), (-width / 2 + radius, height / 2 - radius, 90.0),
                          (-width / 2 + radius, -height / 2 + radius, 180.0), (width / 2 - radius, -height / 2 + radius, 270.0)):
        for i in range(per_corner + 1):
            a = math.radians(start + 90.0 * i / per_corner)
            out.append((cx + radius * math.cos(a), cy + radius * math.sin(a)))
    return out


def superellipse(width, height, power=4.0, count=32):
    out = []
    for i in range(count):
        a = 2 * math.pi * i / count
        c, s = math.cos(a), math.sin(a)
        out.append((width / 2 * math.copysign(abs(c) ** (2 / power), c), height / 2 * math.copysign(abs(s) ** (2 / power), s)))
    return out


def circle(radius, count=None, start=0.0):
    if count is None:
        count = 24 if COARSER[0] == 0 else 12
    return [(radius * math.cos(start + 2 * math.pi * i / count), radius * math.sin(start + 2 * math.pi * i / count)) for i in range(count)]


class Builder:
    """Gathers vertices and faces for one part."""

    def __init__(self):
        self.bm = bmesh.new()

    def vert(self, p):
        return self.bm.verts.new(Vector(p))

    def face(self, verts):
        try:
            return self.bm.faces.new(verts)
        except ValueError:
            return None

    def loft(self, rings, closed=True, caps=True, cap_ends=(True, True)):
        """Joins rings of equal length with quads. Rings run along the shape; each ring is a loop of places."""
        made = [[self.vert(p) for p in ring] for ring in rings]
        n = len(rings[0])
        for a, b in zip(made, made[1:]):
            for i in range(n if closed else n - 1):
                j = (i + 1) % n
                self.face([a[i], a[j], b[j], b[i]])
        if caps and closed:
            if cap_ends[0]:
                self.face(list(reversed(made[0])))
            if cap_ends[1]:
                self.face(made[-1])
        return made

    def bar(self, start, end, section, at, inset=0.85, ends=0.012):
        """A straight bar of one section from start to end, its ends rounded off square: under subdivision a loft of
        a few rings closes to a cone at each end, so each end has a ring a little in, and the cap ring is drawn in.
        section is (u, w) pairs, placed by at(point, u, w)."""
        start, end = Vector(start), Vector(end)
        length = (end - start).length
        share = min(ends / max(length, 1e-6), 0.25)
        rings = []
        for t, scale in ((0.0, inset), (share * 0.4, 0.97), (share, 1.0), (0.5, 1.0), (1.0 - share, 1.0), (1.0 - share * 0.4, 0.97), (1.0, inset)):
            p = start.lerp(end, t)
            cu = sum(u for u, _ in section) / len(section)
            cw = sum(w for _, w in section) / len(section)
            rings.append([at(p, cu + (u - cu) * scale, cw + (w - cw) * scale) for (u, w) in section])
        return self.loft(rings, closed=True, caps=True)

    def sweep(self, path, section, ups=None, closed=True, caps=True, scales=None, twist=None):
        """A section (u, v) carried along path, u along the frame's side and v along its up. ups gives the
        up at each place (else carried along without turning); scales a (su, sv) per place."""
        path = [Vector(p) for p in path]
        rings = []
        up_prev = None
        for i, p in enumerate(path):
            ahead = (path[min(i + 1, len(path) - 1)] - path[max(i - 1, 0)]).normalized()
            if ups is not None:
                up = Vector(ups[i] if not callable(ups) else ups(i, p))
            elif up_prev is None:
                up = Vector((0, 0, 1)) if abs(ahead.z) < 0.9 else Vector((1, 0, 0))
            else:
                up = up_prev
            up = (up - ahead * up.dot(ahead)).normalized()
            up_prev = up
            side = ahead.cross(up).normalized()
            su, sv = scales[i] if scales else (1.0, 1.0)
            sec = section(i) if callable(section) else section
            ring = []
            for (u, w) in sec:
                ring.append(p + side * (u * su) + up * (w * sv))
            rings.append(ring)
        return self.loft(rings, closed=closed, caps=caps)

    def box(self, lo, hi):
        lo, hi = Vector(lo), Vector(hi)
        c = [Vector((x, y, z)) for z in (lo.z, hi.z) for y in (lo.y, hi.y) for x in (lo.x, hi.x)]
        vs = [self.vert(p) for p in c]
        for f in ((0, 2, 3, 1), (4, 5, 7, 6), (0, 1, 5, 4), (2, 6, 7, 3), (0, 4, 6, 2), (1, 3, 7, 5)):
            self.face([vs[i] for i in f])

    def slab(self, outline, origin, u_axis, v_axis, thickness, both=False):
        """A flat panel: outline (u, v) on the plane through origin, pushed thickness along the plane's normal."""
        origin, u_axis, v_axis = Vector(origin), Vector(u_axis).normalized(), Vector(v_axis).normalized()
        n = u_axis.cross(v_axis).normalized()
        back = -thickness / 2 if both else 0.0
        front = thickness / 2 if both else thickness
        rings = [[origin + u_axis * u + v_axis * w + n * d for (u, w) in outline] for d in (back, front)]
        return self.loft(rings)

    def panel(self, outline, origin, u_axis, v_axis, rings=6):
        """A flat sheet filling outline, in rings drawn in towards its middle, so it can be bent onto a curved face
        without its middle cutting through it."""
        origin, u_axis, v_axis = Vector(origin), Vector(u_axis).normalized(), Vector(v_axis).normalized()
        loops = [[origin + (u_axis * u + v_axis * w) * (1.0 - k / rings * 0.96) for (u, w) in outline] for k in range(rings + 1)]
        return self.loft(loops, closed=True, caps=True, cap_ends=(False, True))

    def lathe(self, profile, origin, axis, count=32, caps=True):
        """A shape turned about axis through origin: profile is (radius, along) pairs from one end to the other. Without
        caps its ends stay open: a ring round something it must not cover."""
        axis = Vector(axis).normalized()
        side = axis.orthogonal().normalized()
        other = axis.cross(side)
        rings = []
        for (r, h) in profile:
            rings.append([Vector(origin) + axis * h + (side * math.cos(2 * math.pi * k / count) + other * math.sin(2 * math.pi * k / count)) * r
                          for k in range(count)])
        return self.loft(rings, caps=caps)

    def finish(self, name, material, group=SHELL, subdivide=0, crease_angle=None, bevel=0.0, bevel_segments=2, sharp=35.0, texels=1.0,
               solidify=0.0, smooth_shade=True, keep=None, coarsest=0):
        """Makes the part a Blender object with its modifiers applied."""
        bmesh.ops.remove_doubles(self.bm, verts=self.bm.verts, dist=1e-6)
        bmesh.ops.recalc_face_normals(self.bm, faces=self.bm.faces)
        subdivide = levels(subdivide, coarsest)
        if subdivide:
            # Subdivided, a face of many sides pinches to a star at its middle, which reads as crumpled foil on a cap
            # and draws a lofted end to a point. Each is given a ring of quads inside its edge and a fan in its middle.
            many = [f for f in self.bm.faces if len(f.verts) > 4]
            for f in many:
                middle = f.calc_center_median()
                reach = sum((vert.co - middle).length for vert in f.verts) / len(f.verts)
                bmesh.ops.inset_individual(self.bm, faces=[f], thickness=reach * 0.18, use_even_offset=True)
            bmesh.ops.poke(self.bm, faces=many)
        mesh = bpy.data.meshes.new(name)
        self.bm.to_mesh(mesh)
        self.bm.free()
        obj = bpy.data.objects.new(name, mesh)
        bpy.context.scene.collection.objects.link(obj)
        if crease_angle is not None and subdivide:
            crease = mesh.attributes.get("crease_edge") or mesh.attributes.new("crease_edge", "FLOAT", "EDGE")
            values = []
            for e in mesh.edges:
                faces = [p for p in mesh.polygons if e.key in p.edge_keys]
                values.append(1.0 if len(faces) == 2 and faces[0].normal.angle(faces[1].normal) > math.radians(crease_angle) else 0.0)
            crease.data.foreach_set("value", values)
        if solidify:
            m = obj.modifiers.new("solid", "SOLIDIFY")
            m.thickness = solidify
            m.offset = 0.0
        if bevel:
            m = obj.modifiers.new("bevel", "BEVEL")
            m.width = bevel
            m.segments = bevel_segments
            m.limit_method = "ANGLE"
            m.angle_limit = math.radians(30)
            m.harden_normals = False
        if subdivide:
            m = obj.modifiers.new("subdivide", "SUBSURF")
            m.levels = subdivide
            m.render_levels = subdivide
        apply_all(obj)
        mesh = obj.data
        for p in mesh.polygons:
            p.use_smooth = smooth_shade
        if smooth_shade:
            # A coarse part's facets meet at wider angles than the full one's: kept smooth across them, as the full
            # surface is, so what its normals are baked to carries only the full surface's finer turns.
            mesh.set_sharp_from_angle(angle=math.radians(sharp if COARSER[0] == 0 else max(sharp, 65.0)))
        obj["material"] = material
        obj["group"] = group
        obj["texels"] = texels
        PARTS.append(obj)
        return obj


def apply_all(obj):
    if not obj.modifiers:
        return
    depsgraph = bpy.context.evaluated_depsgraph_get()
    mesh = bpy.data.meshes.new_from_object(obj.evaluated_get(depsgraph), preserve_all_data_layers=True, depsgraph=depsgraph)
    obj.modifiers.clear()
    old = obj.data
    obj.data = mesh
    bpy.data.meshes.remove(old)


def frame_from(origin, ahead, up):
    """A matrix whose x is ahead, z up and y the left side, at origin."""
    ahead = Vector(ahead).normalized()
    up = (Vector(up) - ahead * Vector(up).dot(ahead)).normalized()
    left = up.cross(ahead)
    m = Matrix.Identity(4)
    for r in range(3):
        m[r][0], m[r][1], m[r][2], m[r][3] = ahead[r], left[r], up[r], origin[r]
    return m


def place(obj, matrix):
    obj.data.transform(matrix)


def mirror_y(obj, about):
    """Mirrors a part left for right about the plane y = about, keeping its faces outward."""
    obj.data.transform(Matrix.Translation((0, about, 0)) @ Matrix.Scale(-1, 4, (0, 1, 0)) @ Matrix.Translation((0, -about, 0)))
    obj.data.flip_normals()
