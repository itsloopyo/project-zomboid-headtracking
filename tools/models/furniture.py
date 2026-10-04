"""Builds the mod's own furniture models and packs them for the jar.

Run headless with Blender 4.4, from the repository root:

    blender --background --factory-startup --python tools/models/furniture.py

It writes src/main/resources/models/furniture.bin.gz and prints each model's
triangle count and bounds. Every model is made here from nothing: no mesh,
texture or measurement is read from the game.

A model is in metres, Z up, its origin on the floor at the centre of its
footprint, with +Y the head of a bed and the back of everything else, so its
front faces -Y. Each triangle belongs to one surface, which says what it is
made of; the mod gives a surface its colour and lays its grain on by where the
triangle is in the world, so there are no texture coordinates.

The file is gzip over this, all little-endian:

    4 bytes   the ASCII letters CUFM
    uint32    format version, 2
    uint32    number of models
    per model:
      uint8     length of the name, then that many ASCII bytes
      float32   x 6: the least x, y, z and the greatest x, y, z
      uint32    number of triangles, in the order of their surfaces
      per vertex, three to a triangle:
        float32 x 3   position
        float32 x 3   unit normal
        uint8         surface: an index into SURFACES below
        uint8         how much of the room's light reaches the vertex past the
                      rest of the model and the floor, 255 for all of it
"""

import gzip
import math
import os
import random
import struct

import bmesh
import bpy
from mathutils import Matrix, Vector, noise
from mathutils.bvhtree import BVHTree

SURFACES = ["wood", "dark_wood", "cloth", "linen", "pillow", "metal", "paint", "enamel", "steel", "plastic", "screen", "ceramic",
            "books", "shade", "rubber", "worktop", "pane", "vinyl"]

OUTPUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "models", "furniture.bin.gz")

# How far a vertex looks for whatever stands between it and the room's light, in metres.
OCCLUSION_REACH = 0.45
OCCLUSION_RAYS = 24
# A vertex nothing at all can see keeps this much light, so a deep joint is dark and not a hole.
OCCLUSION_FLOOR = 0.3
# A flat face is cut until no edge of it is longer than this, so that there is a vertex near
# enough to every joint for the joint's shade to be found at all.
LONGEST_EDGE = 0.45
# An edge along which the light changes by more than MOST_STEP is cut in two, down to SHORTEST_CUT long.
MOST_STEP = 0.25
SHORTEST_CUT = 0.12
REFINEMENTS = 12
MOST_TRIANGLES = 6000


def cuboid(bm, x0, x1, y0, y1, z0, z1):
    prism(bm, ((x0 + x1) / 2, (y0 + y1) / 2, x1 - x0, y1 - y0, z0), ((x0 + x1) / 2, (y0 + y1) / 2, x1 - x0, y1 - y0, z1))


def prism(bm, low, high):
    """A box whose bottom and top are rectangles of their own, each given as centre x, centre y, width, depth, height above the floor."""
    rings = []
    for cx, cy, sx, sy, z in (low, high):
        rings.append([bm.verts.new((cx + dx * sx / 2, cy + dy * sy / 2, z)) for dx, dy in ((-1, -1), (1, -1), (1, 1), (-1, 1))])
    bm.faces.new(rings[0][::-1])
    bm.faces.new(rings[1])
    for i in range(4):
        j = (i + 1) % 4
        bm.faces.new((rings[0][i], rings[0][j], rings[1][j], rings[1][i]))


def prism_y(bm, front, back):
    """A box whose front and back are rectangles of their own, each given as centre x, centre z, width, height, y."""
    rings = []
    for cx, cz, sx, sz, y in (front, back):
        rings.append([bm.verts.new((cx + dx * sx / 2, y, cz + dz * sz / 2)) for dx, dz in ((-1, -1), (1, -1), (1, 1), (-1, 1))])
    bm.faces.new(rings[0])
    bm.faces.new(rings[1][::-1])
    for i in range(4):
        j = (i + 1) % 4
        bm.faces.new((rings[0][i], rings[1][i], rings[1][j], rings[0][j]))


def lathe(bm, profile, cx=0.0, cy=0.0, segments=20, sx=1.0, sy=1.0, closed=False, caps=True, to=None):
    """Turns a profile of (radius, height) round the upright through (cx, cy). A radius of 0 is a point on the axis.

    closed joins the last ring to the first, for a ring such as a seat; caps closes an open end with a disc; to moves
    the whole afterwards, for something turned about another axis.
    """
    rings = []
    for radius, z in profile:
        if radius <= 1e-6:
            rings.append([bm.verts.new((cx, cy, z))])
        else:
            rings.append([bm.verts.new((cx + radius * sx * math.cos(2 * math.pi * i / segments), cy + radius * sy * math.sin(2 * math.pi * i / segments), z))
                          for i in range(segments)])
    pairs = list(zip(rings, rings[1:])) + ([(rings[-1], rings[0])] if closed else [])
    for a, b in pairs:
        for i in range(segments):
            j = (i + 1) % segments
            if len(a) == 1 and len(b) == 1:
                continue
            if len(a) == 1:
                bm.faces.new((a[0], b[j], b[i]))
            elif len(b) == 1:
                bm.faces.new((a[i], a[j], b[0]))
            else:
                bm.faces.new((a[i], a[j], b[j], b[i]))
    if caps and not closed:
        if len(rings[0]) > 1:
            bm.faces.new(rings[0][::-1])
        if len(rings[-1]) > 1:
            bm.faces.new(rings[-1])
    verts = [vert for ring in rings for vert in ring]
    if to is not None:
        bmesh.ops.transform(bm, matrix=to, verts=verts)
    return verts


def along_y(x, y, z):
    """Moves something turned about the upright so that its axis runs towards the front from (x, y, z)."""
    return Matrix.Translation((x, y, z)) @ Matrix.Rotation(math.radians(90), 4, "X")


def along_x(x, y, z):
    return Matrix.Translation((x, y, z)) @ Matrix.Rotation(math.radians(90), 4, "Y")


def outline_along_y(bm, outline, y0, y1):
    """A body with the same outline, given as (x, z) going round, from y0 to y1."""
    front = [bm.verts.new((x, y0, z)) for x, z in outline]
    back = [bm.verts.new((x, y1, z)) for x, z in outline]
    count = len(outline)
    for i in range(count):
        j = (i + 1) % count
        bm.faces.new((front[i], front[j], back[j], back[i]))
    bm.faces.new(front[::-1])
    bm.faces.new(back)


def grid_box(bm, xs, ys, zs, bottom=True):
    """A box cut into cells along each axis, as a cage for something soft. Returns its vertices by their place in the grid."""
    verts = {}
    last = (len(xs) - 1, len(ys) - 1, len(zs) - 1)

    def at(i, j, k):
        if (i, j, k) not in verts:
            verts[(i, j, k)] = bm.verts.new((xs[i], ys[j], zs[k]))
        return verts[(i, j, k)]

    for i in range(last[0]):
        for j in range(last[1]):
            if bottom:
                bm.faces.new((at(i, j, 0), at(i, j + 1, 0), at(i + 1, j + 1, 0), at(i + 1, j, 0)))
            bm.faces.new((at(i, j, last[2]), at(i + 1, j, last[2]), at(i + 1, j + 1, last[2]), at(i, j + 1, last[2])))
    for i in range(last[0]):
        for k in range(last[2]):
            bm.faces.new((at(i, 0, k), at(i + 1, 0, k), at(i + 1, 0, k + 1), at(i, 0, k + 1)))
            bm.faces.new((at(i, last[1], k), at(i, last[1], k + 1), at(i + 1, last[1], k + 1), at(i + 1, last[1], k)))
    for j in range(last[1]):
        for k in range(last[2]):
            bm.faces.new((at(0, j, k), at(0, j, k + 1), at(0, j + 1, k + 1), at(0, j + 1, k)))
            bm.faces.new((at(last[0], j, k), at(last[0], j + 1, k), at(last[0], j + 1, k + 1), at(last[0], j, k + 1)))
    return verts


def spread(low, high, cells):
    return [low + (high - low) * i / cells for i in range(cells + 1)]


def crease(bm, verts, amount):
    """Holds the cage's outer edges against the smoothing, so a cushion keeps the shape of a box with soft edges."""
    layer = bm.edges.layers.float.get("crease_edge") or bm.edges.layers.float.new("crease_edge")
    last = [max(key[axis] for key in verts) for axis in range(3)]
    place = {vert: key for key, vert in verts.items()}
    for edge in bm.edges:
        if edge.verts[0] not in place or edge.verts[1] not in place:
            continue
        a, b = place[edge.verts[0]], place[edge.verts[1]]
        on_limit = sum(1 for axis in range(3) if a[axis] == b[axis] and a[axis] in (0, last[axis]))
        if on_limit >= 2:
            edge[layer] = amount


class Model:
    def __init__(self, name, centred=True):
        self.name = name
        # A piece made to meet its neighbours, the corner of a run of counters, is placed by
        # the square it fills and keeps the origin it was built round.
        self.centred = centred
        self.vertices = []

    def hard(self, bm, surface, bevel=0.004, segments=2, inward=False):
        """A part of wood or metal: every edge rounded off, its flat faces kept flat."""
        while True:
            long = [edge for edge in bm.edges if edge.calc_length() > LONGEST_EDGE]
            if not long:
                break
            bmesh.ops.subdivide_edges(bm, edges=long, cuts=1, use_grid_fill=True)

        def modifiers(obj):
            rounded = obj.modifiers.new("bevel", "BEVEL")
            rounded.width = bevel
            rounded.segments = segments
            rounded.limit_method = "ANGLE"
            rounded.angle_limit = math.radians(30)
            flat = obj.modifiers.new("normals", "WEIGHTED_NORMAL")
            flat.mode = "FACE_AREA"
            flat.weight = 100
            flat.keep_sharp = True
        self._take(bm, surface, modifiers, inward=inward)

    def soft(self, bm, surface, levels=2):
        """A part of cloth: the cage smoothed into a rounded body."""
        def modifiers(obj):
            smoothed = obj.modifiers.new("subdivision", "SUBSURF")
            smoothed.levels = levels
            smoothed.subdivision_type = "CATMULL_CLARK"
        self._take(bm, surface, modifiers)

    def turned(self, bm, surface, sharp=40.0):
        """A part turned on a lathe or drawn out along a line: smooth round its curve, sharp where its outline turns a corner."""
        self._take(bm, surface, lambda obj: None, sharp=sharp)

    def plain(self, bm, surface):
        """A part too small to round: flat faces and square edges."""
        self._take(bm, surface, lambda obj: None, sharp=1.0)

    def _take(self, bm, surface, modifiers, sharp=None, inward=False):
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
        if inward:
            bmesh.ops.reverse_faces(bm, faces=bm.faces[:])
        mesh = bpy.data.meshes.new("part")
        bm.to_mesh(mesh)
        bm.free()
        mesh.shade_smooth()
        if sharp is not None:
            mesh.set_sharp_from_angle(angle=math.radians(sharp))
        obj = bpy.data.objects.new("part", mesh)
        bpy.context.scene.collection.objects.link(obj)
        modifiers(obj)
        shaped = obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
        done = shaped.to_mesh()
        done.calc_loop_triangles()
        normals = done.corner_normals
        index = SURFACES.index(surface)
        for triangle in done.loop_triangles:
            for corner, vertex in zip(triangle.loops, triangle.vertices):
                self.vertices.append((Vector(done.vertices[vertex].co), Vector(normals[corner].vector).normalized(), index))
        shaped.to_mesh_clear()
        bpy.data.objects.remove(obj)
        bpy.data.meshes.remove(mesh)

    def finish(self):
        """Stands the model on the floor over the middle of its footprint and works out what each vertex can see of the room."""
        low = Vector([min(v[0][axis] for v in self.vertices) for axis in range(3)])
        high = Vector([max(v[0][axis] for v in self.vertices) for axis in range(3)])
        move = Vector(((low.x + high.x) / 2, (low.y + high.y) / 2, low.z)) if self.centred else Vector((0.0, 0.0, low.z))
        self.vertices = [(position - move, normal, surface) for position, normal, surface in self.vertices]
        self.low, self.high = low - move, high - move

        points = [position for position, _, _ in self.vertices]
        faces = [(i, i + 1, i + 2) for i in range(0, len(points), 3)]
        # The floor the piece stands on shades its underside as the rest of it does.
        ground = len(points)
        points += [Vector((-9, -9, 0)), Vector((9, -9, 0)), Vector((9, 9, 0)), Vector((-9, 9, 0))]
        faces.append((ground, ground + 1, ground + 2, ground + 3))
        tree = BVHTree.FromPolygons(points, faces)
        rays = []
        for i in range(OCCLUSION_RAYS):
            # Spread so that each ray stands for the same share of the light a surface takes in.
            radius = math.sqrt((i + 0.5) / OCCLUSION_RAYS)
            turn = i * math.pi * (3 - math.sqrt(5))
            rays.append((radius * math.cos(turn), radius * math.sin(turn), math.sqrt(1 - radius * radius)))

        def blocked(start, way, inside):
            """How far along a ray the light is stopped, or None. inside is how many parts the ray starts within:
            a vertex that lies under another part, the corner of a panel under its rail, starts inside that
            part. The ray comes out through the back of a face, which stops no light, and only what it meets
            once it is out in the room stops it. Counted as a stop, such a vertex was black, and the black
            ran out across the part of its face that shows."""
            gone = 0.0
            for _ in range(16):
                location, normal, _, distance = tree.ray_cast(start, way, OCCLUSION_REACH - gone)
                if location is None:
                    return None
                gone += distance
                if normal.dot(way) >= 0.0:
                    inside = max(inside - 1, 0)
                elif inside == 0:
                    return gone
                else:
                    inside += 1
                start = location + way * 1e-4
            return None

        def within(start, way):
            """How many parts a point is inside: along any ray out of it, the faces it leaves by less the faces it enters by."""
            inside = 0
            for _ in range(64):
                location, normal, _, _ = tree.ray_cast(start, way, 40.0)
                if location is None:
                    break
                inside += 1 if normal.dot(way) >= 0.0 else -1
                start = location + way * 1e-4
            return max(inside, 0)

        known = {}
        self.shaped = len(self.vertices) // 3

        def reached(position, normal):
            """What share of the room's light reaches a vertex, and whether the vertex is out in the room to be seen."""
            key = (round(position.x, 4), round(position.y, 4), round(position.z, 4), round(normal.x, 1), round(normal.y, 1), round(normal.z, 1))
            if key not in known:
                side = normal.orthogonal().normalized()
                other = normal.cross(side)
                start = position + normal * 0.0031
                # Three rays, none of them square to the model: a ray along one of its axes runs down the
                # shared edge of two triangles as often as not, and is counted through both or neither.
                inside = sorted(within(start, (normal + side * a + other * b).normalized()) for a, b in ((0.137, 0.061), (-0.089, 0.173), (0.053, -0.191)))[1]
                hidden = 0.0
                for x, y, z in rays:
                    stopped = blocked(start, side * x + other * y + normal * z, inside)
                    if stopped is not None:
                        hidden += 1 - stopped / OCCLUSION_REACH
                known[key] = (OCCLUSION_FLOOR + (1 - OCCLUSION_FLOOR) * (1 - hidden / OCCLUSION_RAYS), inside == 0)
            return known[key]

        def edge(a, b):
            return frozenset(((round(a.x, 4), round(a.y, 4), round(a.z, 4)), (round(b.x, 4), round(b.y, 4), round(b.z, 4))))

        def between(a, b):
            return ((a[0] + b[0]) / 2, (a[1] + b[1]).normalized(), a[2])

        # Light is worked out at the vertices and runs evenly between them, so where it differs
        # much between the two ends of an edge the shade of a joint at one end would lie right
        # along the edge, and across a big face as a dark triangle. Such an edge is cut in two,
        # in every triangle that has it, until the shade is as short as the joint's really is.
        for _ in range(REFINEMENTS):
            cut = set()
            for i in range(0, len(self.vertices), 3):
                corners = self.vertices[i:i + 3]
                for a, b in ((0, 1), (1, 2), (2, 0)):
                    one, other = reached(*corners[a][:2]), reached(*corners[b][:2])
                    # Only between two vertices that are out in the room. One that lies inside another part
                    # has the light of the room just outside that part, which is a guess at what the
                    # shown end of its face should run towards, and no joint's shade.
                    if (corners[a][0] - corners[b][0]).length > SHORTEST_CUT and abs(one[0] - other[0]) > MOST_STEP and one[1] and other[1]:
                        cut.add(edge(corners[a][0], corners[b][0]))
            if not cut:
                break
            cut_up = []
            for i in range(0, len(self.vertices), 3):
                a, b, c = self.vertices[i:i + 3]
                for _ in range(3):
                    if edge(a[0], b[0]) in cut:
                        break
                    a, b, c = b, c, a
                ab, bc, ca = edge(a[0], b[0]) in cut, edge(b[0], c[0]) in cut, edge(c[0], a[0]) in cut
                if not ab:
                    cut_up += [a, b, c]
                elif bc and ca:
                    cut_up += [a, between(a, b), between(c, a), between(a, b), b, between(b, c), between(c, a), between(b, c), c,
                               between(a, b), between(b, c), between(c, a)]
                elif bc:
                    cut_up += [between(a, b), b, between(b, c), a, between(a, b), between(b, c), a, between(b, c), c]
                elif ca:
                    cut_up += [a, between(a, b), between(c, a), between(a, b), b, between(c, a), b, c, between(c, a)]
                else:
                    cut_up += [a, between(a, b), c, between(a, b), b, c]
            self.vertices = cut_up
        self.light = [reached(position, normal)[0] for position, normal, _ in self.vertices]
        self.seen = [reached(position, normal)[1] for position, normal, _ in self.vertices]
        self.check()

    def check(self):
        """Refuses a model with a normal that is not a unit vector, and finds the longest edge along which the light still changes sharply."""
        self.longest_shade = 0.0
        for i in range(0, len(self.vertices), 3):
            corners = self.vertices[i:i + 3]
            for position, normal, _ in corners:
                if abs(normal.length - 1.0) > 1e-3:
                    raise SystemExit("%s has a normal of length %f" % (self.name, normal.length))
            for a, b in ((0, 1), (1, 2), (2, 0)):
                if abs(self.light[i + a] - self.light[i + b]) > MOST_STEP and self.seen[i + a] and self.seen[i + b]:
                    self.longest_shade = max(self.longest_shade, (corners[a][0] - corners[b][0]).length)

    def darkest(self, inside):
        """The least light on any vertex out in the room for which inside(position, normal) holds."""
        found = [light for (position, normal, _), light, seen in zip(self.vertices, self.light, self.seen) if seen and inside(position, normal)]
        if not found:
            raise SystemExit("%s has no vertex where its light was to be looked at" % self.name)
        return min(found)

    def packed(self):
        name = self.name.encode("ascii")
        out = [struct.pack("<B", len(name)), name, struct.pack("<6f", *self.low, *self.high), struct.pack("<I", len(self.vertices) // 3)]
        triangles = [(self.vertices[i:i + 3], self.light[i:i + 3]) for i in range(0, len(self.vertices), 3)]
        # In the order of their surfaces, so that the mod adds each surface's triangles as one run.
        triangles.sort(key=lambda triangle: triangle[0][0][2])
        for corners, lights in triangles:
            for (position, normal, surface), light in zip(corners, lights):
                out.append(struct.pack("<6fBB", *position, *normal, surface, round(light * 255)))
        return b"".join(out)


def bed(name, width, length, pillows):
    model = Model(name)
    half, head, foot = width / 2, length / 2, -length / 2
    top = 0.54

    frame = bmesh.new()
    for side in (-1, 1):
        cuboid(frame, side * (half + 0.015) - 0.02, side * (half + 0.015) + 0.02, foot - 0.02, head + 0.02, 0.17, 0.37)
        prism(frame, (side * (half + 0.01), head + 0.04, 0.05, 0.05, 0.0), (side * (half + 0.01), head + 0.04, 0.07, 0.07, 1.08))
        prism(frame, (side * (half + 0.01), foot - 0.04, 0.05, 0.05, 0.0), (side * (half + 0.01), foot - 0.04, 0.07, 0.07, 0.56))
    cuboid(frame, -half, half, foot - 0.055, foot - 0.025, 0.17, 0.50)
    cuboid(frame, -half, half, head + 0.03, head + 0.055, 0.17, 0.98)
    cuboid(frame, -half - 0.03, half + 0.03, head + 0.01, head + 0.07, 0.98, 1.04)
    model.hard(frame, "wood", bevel=0.006, segments=1)

    mattress = bmesh.new()
    cuboid(mattress, -half, half, foot, head, 0.30, top)
    model.hard(mattress, "linen", bevel=0.035, segments=2)

    # The duvet lies from the foot to where it is turned down short of the pillows, and
    # hangs over both sides and the foot.
    turned = head - 0.64
    over = half + 0.035
    duvet = bmesh.new()
    cage = grid_box(duvet, spread(-over, over, max(6, round(width * 5))), spread(foot - 0.035, turned, 11), [0.37, top + 0.05], bottom=False)
    for (i, j, k), vert in cage.items():
        at = vert.co
        if k == 1:
            at.z += 0.014 * noise.noise(Vector((at.x * 2.6, at.y * 2.6, 0.3))) + 0.007 * noise.noise(Vector((at.x * 7.0, at.y * 7.0, 4.1)))
        else:
            hang = 0.012 * noise.noise(Vector((at.x * 5.0, at.y * 5.0, 9.7)))
            at.x += math.copysign(hang, at.x)
            at.z += 0.02 * noise.noise(Vector((at.x * 6.0, at.y * 6.0, 2.2)))
    model.soft(duvet, "cloth", levels=1)

    # Turned down, the duvet shows its pale underside as a band across the bed.
    band = bmesh.new()
    cage = grid_box(band, spread(-over - 0.008, over + 0.008, max(6, round(width * 5))), [turned - 0.30, turned - 0.15, turned + 0.015], [0.41, top + 0.085], bottom=False)
    for (i, j, k), vert in cage.items():
        if k == 1:
            vert.co.z += 0.008 * noise.noise(Vector((vert.co.x * 4.0, vert.co.y * 4.0, 6.5)))
    model.soft(band, "linen", levels=1)

    for n in range(pillows):
        centre = 0.0 if pillows == 1 else (n - 0.5) * (half + 0.02)
        lean = math.radians((-5, 4)[n % 2])
        wide, deep, thick = min(0.36, half - 0.09), 0.23, 0.075
        pillow = bmesh.new()
        cage = grid_box(pillow, spread(-wide, wide, 2), spread(-deep, deep, 2), [-thick, 0.0, thick])
        for (i, j, k), vert in cage.items():
            at = vert.co
            if k != 1:
                # The two faces are drawn in from the seam, which leaves the pillow thin at its
                # edge and full in the middle.
                at.x *= 0.72
                at.y *= 0.66
            turned_x = at.x * math.cos(lean) - at.y * math.sin(lean)
            turned_y = at.x * math.sin(lean) + at.y * math.cos(lean)
            at.x, at.y, at.z = centre + turned_x, head - 0.31 + turned_y, top + thick - 0.012 + at.z
        model.soft(pillow, "pillow", levels=2)
    return model


def dining_chair():
    model = Model("dining_chair")
    half, seat = 0.20, 0.44

    def rake(z):
        """How far back the chair's back stands at height z."""
        return 0.19 + max(z - seat, 0.0) * 0.14

    frame = bmesh.new()
    for side in (-1, 1):
        prism(frame, (side * half, -0.19, 0.026, 0.026, 0.0), (side * half, -0.19, 0.04, 0.04, seat))
        prism(frame, (side * (half - 0.005), 0.21, 0.026, 0.026, 0.0), (side * half, rake(seat), 0.04, 0.04, seat))
        prism(frame, (side * half, rake(seat), 0.04, 0.04, seat), (side * half, rake(0.96), 0.034, 0.028, 0.96))
        cuboid(frame, side * half - 0.011, side * half + 0.011, -0.17, 0.17, 0.37, seat)
        cuboid(frame, side * half - 0.009, side * half + 0.009, -0.17, 0.18, 0.17, 0.195)
    cuboid(frame, -half + 0.02, half - 0.02, -0.201, -0.179, 0.37, seat)
    cuboid(frame, -half + 0.02, half - 0.02, 0.179, 0.201, 0.37, seat)
    cuboid(frame, -half + 0.009, half - 0.009, -0.01, 0.01, 0.17, 0.195)
    model.hard(frame, "wood", bevel=0.004, segments=1)

    back = bmesh.new()
    prism(back, (0.0, rake(0.85), 2 * half - 0.03, 0.022, 0.85), (0.0, rake(0.945), 2 * half - 0.03, 0.022, 0.945))
    prism(back, (0.0, rake(0.56), 2 * half - 0.03, 0.02, 0.56), (0.0, rake(0.60), 2 * half - 0.03, 0.02, 0.60))
    for x in (-0.10, 0.0, 0.10):
        prism(back, (x, rake(0.60), 0.045, 0.012, 0.595), (x, rake(0.85), 0.045, 0.012, 0.855))
    model.hard(back, "wood", bevel=0.004, segments=1)

    board = bmesh.new()
    cuboid(board, -half - 0.025, half + 0.025, -0.225, 0.20, seat, seat + 0.022)
    model.hard(board, "wood", bevel=0.007)

    pad = bmesh.new()
    cage = grid_box(pad, spread(-half, half, 2), spread(-0.20, 0.17, 2), [seat + 0.018, seat + 0.05])
    cage[(1, 1, 1)].co.z -= 0.014
    crease(pad, cage, 0.45)
    model.soft(pad, "cloth", levels=2)
    return model


def small_table():
    model = Model("small_table")
    half, top = 0.45, 0.75

    under = bmesh.new()
    for x in (-1, 1):
        for y in (-1, 1):
            prism(under, (x * (half - 0.065), y * (half - 0.065), 0.036, 0.036, 0.0), (x * (half - 0.065), y * (half - 0.065), 0.058, 0.058, top - 0.03))
    for side in (-1, 1):
        cuboid(under, side * (half - 0.065) - 0.011, side * (half - 0.065) + 0.011, -half + 0.09, half - 0.09, top - 0.115, top - 0.03)
        cuboid(under, -half + 0.09, half - 0.09, side * (half - 0.065) - 0.011, side * (half - 0.065) + 0.011, top - 0.115, top - 0.03)
    model.hard(under, "wood", bevel=0.004, segments=1)

    board = bmesh.new()
    cuboid(board, -half, half, -half, half, top - 0.03, top)
    model.hard(board, "wood", bevel=0.011, segments=3)
    return model


def knob(bm, x, face, z, size=1.0):
    """A turned knob standing out of a front at y = face."""
    lathe(bm, [(0.0, 0.0), (0.008 * size, 0.0), (0.007 * size, 0.012 * size), (0.016 * size, 0.02 * size), (0.014 * size, 0.03 * size), (0.0, 0.033 * size)],
          segments=10, to=along_y(x, face, z))


def bar_handle(bm, x0, x1, face, z0, z1, proud=0.03, thick=0.012):
    """A bar held off a front at y = face by a post at each end. It runs along whichever of x and z it is longer in."""
    cuboid(bm, x0, x1, face - proud, face - proud + thick, z0, z1)
    if x1 - x0 > z1 - z0:
        for x in (x0 + thick, x1 - 2 * thick):
            cuboid(bm, x, x + thick, face - proud + thick, face, z0, z1)
    else:
        for z in (z0 + thick, z1 - 2 * thick):
            cuboid(bm, x0, x1, face - proud + thick, face, z, z + thick)


def drawer_fronts(fronts, pulls, x0, x1, z0, z1, count, face, at=(0.5,), gap=0.008, thick=0.018, size=1.0):
    """A stack of drawer fronts filling x0 to x1 and z0 to z1, their faces at y = face. Every front is the same
    height, the gaps between them and round them are all the same, and each pull is at the middle of its front's height."""
    high = (z1 - z0 - gap * (count + 1)) / count
    for n in range(count):
        low = z0 + gap + n * (high + gap)
        cuboid(fronts, x0 + gap, x1 - gap, face, face + thick, low, low + high)
        for share in at:
            knob(pulls, x0 + (x1 - x0) * share, face, low + high / 2, size)


def framed_door(frame, panel, x0, x1, z0, z1, face, rail=0.07, thick=0.022, field=True):
    """A door of two stiles and two rails round a panel set back from them, its face at y = face. The panel runs a
    little way in under the frame and stops: nothing of it lies hidden behind a rail."""
    cuboid(frame, x0, x0 + rail, face, face + thick, z0, z1)
    cuboid(frame, x1 - rail, x1, face, face + thick, z0, z1)
    cuboid(frame, x0 + rail, x1 - rail, face, face + thick, z0, z0 + rail)
    cuboid(frame, x0 + rail, x1 - rail, face, face + thick, z1 - rail, z1)
    cuboid(panel, x0 + rail - 0.004, x1 - rail + 0.004, face + 0.011, face + thick, z0 + rail - 0.004, z1 - rail + 0.004)
    if field:
        cuboid(panel, x0 + rail + 0.045, x1 - rail - 0.045, face + 0.005, face + 0.013, z0 + rail + 0.045, z1 - rail - 0.045)


def wardrobe():
    model = Model("wardrobe")
    half, front, back = 0.90, -0.28, 0.28

    body = bmesh.new()
    cuboid(body, -half + 0.02, half - 0.02, front + 0.04, back, 0.0, 0.10)
    cuboid(body, -half, half, front, back, 0.10, 1.90)
    cuboid(body, -half - 0.02, half + 0.02, front - 0.035, back, 1.90, 1.95)
    cuboid(body, -half - 0.045, half + 0.045, front - 0.06, back, 1.95, 2.0)
    model.hard(body, "wood", bevel=0.008, segments=1)

    fronts, pulls = bmesh.new(), bmesh.new()
    drawer_fronts(fronts, pulls, -half + 0.012, half - 0.012, 0.105, 0.60, 2, front - 0.018, at=(0.28, 0.72), size=1.2)
    model.hard(fronts, "wood", bevel=0.005, segments=1)

    frame, panel = bmesh.new(), bmesh.new()
    face = front - 0.022
    for side in (-1, 1):
        low, high = sorted((side * 0.004, side * (half - 0.020)))
        framed_door(frame, panel, low, high, 0.608, 1.892, face, rail=0.085)
        knob(pulls, side * 0.047, face, 1.20, 1.3)
    model.hard(frame, "wood", bevel=0.005, segments=1)
    model.hard(panel, "wood", bevel=0.004, segments=1)
    model.turned(pulls, "metal")
    return model


def bedside_cabinet():
    model = Model("bedside_cabinet")
    half, front, back = 0.25, -0.20, 0.21

    body = bmesh.new()
    for x in (-1, 1):
        for y in (front + 0.04, back - 0.04):
            prism(body, (x * (half - 0.04), y, 0.028, 0.028, 0.0), (x * (half - 0.04), y, 0.042, 0.042, 0.1))
    cuboid(body, -half, half, front, back, 0.09, 0.63)
    model.hard(body, "wood", bevel=0.006)

    board = bmesh.new()
    cuboid(board, -half - 0.02, half + 0.02, front - 0.03, back, 0.63, 0.66)
    model.hard(board, "wood", bevel=0.009, segments=3)

    fronts, pulls = bmesh.new(), bmesh.new()
    drawer_fronts(fronts, pulls, -half + 0.01, half - 0.01, 0.10, 0.62, 2, front - 0.018, size=1.1)
    model.hard(fronts, "wood", bevel=0.005)
    model.turned(pulls, "metal")
    return model


def dresser():
    model = Model("dresser")
    half, front, back, top = 0.46, -0.23, 0.24, 0.88

    body = bmesh.new()
    cuboid(body, -half + 0.03, half - 0.03, front + 0.04, back, 0.0, 0.07)
    cuboid(body, -half, half, front, back, 0.07, top - 0.035)
    model.hard(body, "wood", bevel=0.006)

    board = bmesh.new()
    cuboid(board, -half - 0.025, half + 0.025, front - 0.035, back, top - 0.035, top)
    model.hard(board, "wood", bevel=0.01, segments=3)

    fronts, pulls = bmesh.new(), bmesh.new()
    drawer_fronts(fronts, pulls, -half + 0.012, half - 0.012, 0.08, top - 0.045, 3, front - 0.018, at=(0.25, 0.75), size=1.15)
    model.hard(fronts, "wood", bevel=0.005)
    model.turned(pulls, "metal")
    return model


def desk():
    model = Model("desk")
    half, front, back, top = 0.75, -0.36, 0.36, 0.75

    board = bmesh.new()
    cuboid(board, -half, half, front, back, top - 0.035, top)
    model.hard(board, "wood", bevel=0.008, segments=3)

    body = bmesh.new()
    cuboid(body, 0.30, half - 0.03, front + 0.05, back - 0.03, 0.0, top - 0.035)
    cuboid(body, -half + 0.03, -half + 0.065, front + 0.05, back - 0.03, 0.0, top - 0.035)
    cuboid(body, -half + 0.065, 0.30, back - 0.07, back - 0.05, 0.28, top - 0.035)
    model.hard(body, "wood", bevel=0.005)

    fronts, pulls = bmesh.new(), bmesh.new()
    drawer_fronts(fronts, pulls, 0.30, half - 0.03, 0.03, top - 0.04, 3, front + 0.05 - 0.018, size=1.1)
    model.hard(fronts, "wood", bevel=0.004)
    model.turned(pulls, "metal")
    return model


def cabinet(name, deep, high, base, corner=False, sink=False, panelled=False):
    """One square of a run of cupboards, a metre wide so that each meets the next: the counter under a worktop when
    base, the cupboard on the wall when not. Its origin is the middle of its square, which a corner piece fills along
    two of its edges: its back, and the side on its left as it is looked at. A shop's counter is panelled: it has no
    doors on the side its customers stand at."""
    model = Model(name, centred=not corner)
    kick = 0.10 if base else 0.0
    top = high - 0.04 if base else high
    # A straight piece is built round the middle of its own depth. A corner piece is built on
    # its square, standing off the square's edge as far as a straight piece is stood off it.
    back = 0.455 if corner else deep / 2
    front = back - deep
    lip = 0.02 if base else 0.0
    side = -0.455

    body = bmesh.new()
    if base:
        cuboid(body, side if corner else -0.5, 0.5, front + lip + 0.06, back, 0.0, kick)
    cuboid(body, side if corner else -0.5, 0.5, front + lip, back, kick, top)
    if corner:
        if base:
            cuboid(body, side, side + deep - lip - 0.06, -0.5, front + lip + 0.06, 0.0, kick)
        cuboid(body, side, side + deep - lip, -0.5, front + lip, kick, top)
    model.hard(body, "wood", bevel=0.004)

    # A corner piece's two faces are plain: the doors of a corner cupboard are on the pieces either side of it.
    if panelled and not corner:
        frame, panel = bmesh.new(), bmesh.new()
        framed_door(frame, panel, -0.498, 0.498, kick + 0.012, top - 0.012, front + lip - 0.02, rail=0.075, thick=0.02)
        model.hard(frame, "wood", bevel=0.004)
        model.hard(panel, "wood", bevel=0.003)
    elif not corner:
        frame, panel, pulls = bmesh.new(), bmesh.new(), bmesh.new()
        face = front + lip - 0.02
        for way in (-1, 1):
            low, far = sorted((way * 0.004, way * 0.492))
            framed_door(frame, panel, low, far, kick + 0.012, top - 0.012, face, rail=0.06, thick=0.02, field=False)
            grip = top - 0.14 if base else kick + 0.07
            bar_handle(pulls, way * 0.05 - 0.006, way * 0.05 + 0.006, face, grip, grip + 0.11, proud=0.028, thick=0.01)
        model.hard(frame, "wood", bevel=0.004)
        model.hard(panel, "wood", bevel=0.003)
        model.hard(pulls, "metal", bevel=0.003, segments=1)

    if base:
        tops = bmesh.new()
        if sink:
            x0, x1, y0, y1 = -0.27, 0.27, front + 0.12, back - 0.14
            cuboid(tops, -0.5, x0, front, back, top, high)
            cuboid(tops, x1, 0.5, front, back, top, high)
            cuboid(tops, x0, x1, front, y0, top, high)
            cuboid(tops, x0, x1, y1, back, top, high)
            bowl = bmesh.new()
            cuboid(bowl, x0, x1, y0, y1, high - 0.19, high + 0.003)
            bowl.faces.ensure_lookup_table()
            bmesh.ops.delete(bowl, geom=[face for face in bowl.faces if face.normal.z > 0.5], context="FACES")
            model.hard(bowl, "steel", bevel=0.012, segments=2, inward=True)
            rim = bmesh.new()
            for a0, a1, b0, b1 in ((x0 - 0.014, x1 + 0.014, y0 - 0.014, y0), (x0 - 0.014, x1 + 0.014, y1, y1 + 0.014),
                                   (x0 - 0.014, x0, y0, y1), (x1, x1 + 0.014, y0, y1)):
                cuboid(rim, a0, a1, b0, b1, high - 0.002, high + 0.004)
            model.hard(rim, "steel", bevel=0.002, segments=1)
            tap = bmesh.new()
            lathe(tap, [(0.016, high), (0.016, high + 0.02), (0.011, high + 0.03), (0.011, high + 0.2), (0.0, high + 0.205)], cy=y1 + 0.07, segments=12)
            lathe(tap, [(0.009, 0.0), (0.009, 0.15), (0.0, 0.153)], segments=10, to=along_y(0.0, y1 + 0.07, high + 0.185))
            for x in (-0.07, 0.07):
                lathe(tap, [(0.013, high), (0.013, high + 0.03), (0.019, high + 0.035), (0.019, high + 0.05), (0.0, high + 0.054)], cx=x, cy=y1 + 0.07, segments=10)
            model.turned(tap, "metal")
        else:
            cuboid(tops, side if corner else -0.5, 0.5, front, back, top, high)
            if corner:
                cuboid(tops, side, side + deep, -0.5, front, top, high)
        model.hard(tops, "worktop", bevel=0.005, segments=2)
    return model


def fridge():
    model = Model("fridge")
    half, front, back, top = 0.35, -0.30, 0.34, 1.70

    body = bmesh.new()
    cuboid(body, -half, half, front, back, 0.05, top)
    model.hard(body, "enamel", bevel=0.012, segments=3)

    foot = bmesh.new()
    cuboid(foot, -half + 0.02, half - 0.02, front + 0.01, back - 0.03, 0.0, 0.05)
    for n in range(5):
        cuboid(foot, -half + 0.05, half - 0.05, front + 0.004, front + 0.012, 0.008 + n * 0.008, 0.012 + n * 0.008)
    model.hard(foot, "rubber", bevel=0.002, segments=1)

    doors = bmesh.new()
    cuboid(doors, -half + 0.002, half - 0.002, front - 0.045, front - 0.004, 0.06, 1.215)
    cuboid(doors, -half + 0.002, half - 0.002, front - 0.045, front - 0.004, 1.225, top - 0.003)
    model.hard(doors, "enamel", bevel=0.016, segments=3)

    pulls = bmesh.new()
    bar_handle(pulls, -half + 0.045, -half + 0.065, front - 0.045, 0.80, 1.17, proud=0.04, thick=0.016)
    bar_handle(pulls, -half + 0.045, -half + 0.065, front - 0.045, 1.27, 1.50, proud=0.04, thick=0.016)
    model.hard(pulls, "steel", bevel=0.005)
    return model


def oven():
    model = Model("oven")
    half, front, back, hob = 0.38, -0.31, 0.33, 0.91

    body = bmesh.new()
    cuboid(body, -half, half, front, back, 0.03, hob)
    cuboid(body, -half, half, back - 0.07, back, hob, hob + 0.18)
    cuboid(body, -half + 0.012, half - 0.012, front - 0.022, front, 0.045, 0.15)
    cuboid(body, -half + 0.012, half - 0.012, front - 0.022, front, 0.165, 0.76)
    model.hard(body, "enamel", bevel=0.01, segments=2)

    feet = bmesh.new()
    for x in (-1, 1):
        for y in (front + 0.05, back - 0.05):
            cuboid(feet, x * (half - 0.05) - 0.02, x * (half - 0.05) + 0.02, y - 0.02, y + 0.02, 0.0, 0.03)
    for x in (-0.19, 0.19):
        for y in (-0.14, 0.11):
            lathe(feet, [(0.0, hob), (0.095, hob), (0.095, hob + 0.006), (0.08, hob + 0.016), (0.03, hob + 0.016), (0.0, hob + 0.01)], cx=x, cy=y, segments=16)
    model.turned(feet, "rubber")

    glass = bmesh.new()
    cuboid(glass, -half + 0.11, half - 0.11, front - 0.028, front - 0.02, 0.30, 0.61)
    model.hard(glass, "screen", bevel=0.006)

    trim = bmesh.new()
    bar_handle(trim, -half + 0.07, half - 0.07, front - 0.022, 0.685, 0.705, proud=0.04, thick=0.016)
    model.hard(trim, "steel", bevel=0.005)

    dials = bmesh.new()
    for x in (-0.27, -0.135, 0.0, 0.135, 0.27):
        lathe(dials, [(0.0, 0.0), (0.024, 0.0), (0.021, 0.02), (0.0, 0.022)], segments=12, to=along_y(x, back - 0.07, hob + 0.10))
    model.turned(dials, "plastic")
    return model


def bookcase():
    model = Model("bookcase")
    half, front, back, top = 0.45, -0.16, 0.16, 1.95
    shelves = [0.09, 0.47, 0.84, 1.21, 1.58]

    body = bmesh.new()
    for side in (-1, 1):
        cuboid(body, side * half - 0.014, side * half + 0.014, front, back, 0.0, top - 0.03)
    cuboid(body, -half - 0.03, half + 0.03, front - 0.02, back, top - 0.03, top)
    cuboid(body, -half + 0.014, half - 0.014, front + 0.02, back, 0.0, 0.065)
    cuboid(body, -half + 0.014, half - 0.014, back - 0.012, back, 0.065, top - 0.03)
    for z in shelves:
        cuboid(body, -half + 0.014, half - 0.014, front + 0.006, back - 0.012, z - 0.025, z)
    model.hard(body, "wood", bevel=0.004, segments=1)

    books = bmesh.new()
    pick = random.Random(7)
    for row, z in enumerate(shelves):
        x = -half + 0.022
        full = half - 0.03 if row % 2 else half - 0.03 - pick.uniform(0.12, 0.3)
        while True:
            wide, tall, deep = pick.uniform(0.022, 0.05), pick.uniform(0.19, 0.31), pick.uniform(0.15, 0.22)
            if x + wide > full:
                break
            set_back = pick.uniform(0.02, 0.045)
            cuboid(books, x, x + wide - 0.002, front + set_back, front + set_back + deep, z, z + tall)
            x += wide
    model.plain(books, "books")
    return model


def locker():
    model = Model("locker")
    half, front, back, top = 0.38, -0.23, 0.23, 1.83

    body = bmesh.new()
    cuboid(body, -half, half, front, back, 0.08, top)
    for x in (-1, 1):
        for y in (front + 0.04, back - 0.04):
            cuboid(body, x * (half - 0.04) - 0.02, x * (half - 0.04) + 0.02, y - 0.02, y + 0.02, 0.0, 0.08)
    model.hard(body, "paint", bevel=0.005)

    doors, trim = bmesh.new(), bmesh.new()
    for side in (-1, 1):
        low, high = sorted((side * 0.004, side * (half - 0.012)))
        cuboid(doors, low, high, front - 0.014, front, 0.095, top - 0.015)
        for group in (0.30, 1.48):
            for n in range(4):
                z = group + n * 0.035
                prism_y(doors, ((low + high) / 2, z + 0.004, high - low - 0.12, 0.012, front - 0.022), ((low + high) / 2, z + 0.012, high - low - 0.12, 0.018, front - 0.014))
        cuboid(trim, side * 0.06 - 0.02, side * 0.06 + 0.02, front - 0.018, front - 0.014, 0.93, 1.05)
        bar_handle(trim, side * 0.06 - 0.006, side * 0.06 + 0.006, front - 0.018, 0.95, 1.03, proud=0.02, thick=0.008)
    model.hard(doors, "paint", bevel=0.003, segments=1)
    model.hard(trim, "metal", bevel=0.002, segments=1)
    return model


def cushion(model, x0, x1, y0, y1, z0, z1, crown, lean=0.0, piped=False):
    """A cushion: a box with soft edges, fuller in the middle of its face, its top leant back by lean."""
    part = bmesh.new()
    cage = grid_box(part, spread(x0, x1, 2), spread(y0, y1, 2), spread(z0, z1, 2) if lean else [z0, z1])
    for (i, j, k), vert in cage.items():
        vert.co.y += lean * (vert.co.z - z0) / (z1 - z0)
    if lean:
        cage[(1, 0, 1)].co.y -= crown
    else:
        cage[(1, 1, 1)].co.z += crown
    crease(part, cage, 0.3)
    model.soft(part, "cloth", levels=2)
    if piped:
        # The cord sewn into the seam round the top of a seat cushion.
        cord = bmesh.new()
        inset, corner, z, thick = 0.016, 0.05, z1 - 0.022, 0.0055
        path = []
        for cx, cy, turn in ((x1 - inset - corner, y0 + inset + corner, -90), (x1 - inset - corner, y1 - inset - corner, 0),
                             (x0 + inset + corner, y1 - inset - corner, 90), (x0 + inset + corner, y0 + inset + corner, 180)):
            for step in range(4):
                angle = math.radians(turn + step * 30)
                path.append((cx + corner * math.cos(angle), cy + corner * math.sin(angle), math.cos(angle), math.sin(angle)))
        rings = []
        for x, y, out_x, out_y in path:
            rings.append([cord.verts.new((x + out_x * thick * math.cos(a), y + out_y * thick * math.cos(a), z + thick * math.sin(a)))
                          for a in (0.0, math.pi / 2, math.pi, 3 * math.pi / 2)])
        for n, ring in enumerate(rings):
            after = rings[(n + 1) % len(rings)]
            for i in range(4):
                cord.faces.new((ring[i], ring[(i + 1) % 4], after[(i + 1) % 4], after[i]))
        model.turned(cord, "cloth", sharp=180.0)


def rolled_arm(model, inner, outer, front, back, low, shoulder, roll):
    """The arm of a settee: an upright side with a roll along its top, drawn out from front to back."""
    middle = (inner + outer) / 2
    outline = [(inner, low), (outer, low), (outer, shoulder)]
    for degrees in (-35, 5, 45, 90, 135, 175, 215):
        outline.append((middle + roll * math.cos(math.radians(degrees)) * (1 if outer > inner else -1), shoulder + roll * 0.75 + roll * math.sin(math.radians(degrees))))
    outline.append((inner, shoulder))
    part = bmesh.new()
    rings = [[part.verts.new((x, y, z)) for x, z in outline] for y in spread(front, back, 3)]
    count = len(outline)
    for a, b in zip(rings, rings[1:]):
        for i in range(count):
            j = (i + 1) % count
            part.faces.new((a[i], a[j], b[j], b[i]))
    centre_x = sum(x for x, _ in outline) / count
    centre_z = sum(z for _, z in outline) / count
    for ring, y, out in ((rings[0], front, -1), (rings[-1], back, 1)):
        inside = [part.verts.new((centre_x + (x - centre_x) * 0.55, y + out * 0.02, centre_z + (z - centre_z) * 0.55)) for x, z in outline]
        for i in range(count):
            j = (i + 1) % count
            part.faces.new((ring[i], ring[j], inside[j], inside[i]))
        part.faces.new(inside)
    model.soft(part, "cloth", levels=1)


def settee(name, width, seats):
    """A sofa or an armchair: seats cushions wide between two rolled arms."""
    model = Model(name)
    half, front, back, arm = width / 2, -0.44, 0.44, 0.16
    inner = half - arm

    feet = bmesh.new()
    for x in (-1, 1):
        for y in (front + 0.08, back - 0.07):
            prism(feet, (x * (half - 0.08), y, 0.04, 0.04, 0.0), (x * (half - 0.08), y, 0.065, 0.065, 0.08))
    model.hard(feet, "dark_wood", bevel=0.004, segments=1)

    base = bmesh.new()
    cage = grid_box(base, spread(-inner - 0.02, inner + 0.02, 2), [front + 0.03, back - 0.02], [0.075, 0.31])
    crease(base, cage, 0.7)
    model.soft(base, "cloth", levels=2)

    for side in (-1, 1):
        rolled_arm(model, side * (inner - 0.005), side * half, front, back, 0.075, 0.43, 0.105)

    rest = bmesh.new()
    cage = grid_box(rest, spread(-inner - 0.02, inner + 0.02, 2), [0.25, back], [0.075, 0.5, 0.84])
    for (i, j, k), vert in cage.items():
        vert.co.y += 0.06 * (vert.co.z - 0.075) / 0.765
    crease(rest, cage, 0.35)
    model.soft(rest, "cloth", levels=2)

    each = 2 * inner / seats
    for n in range(seats):
        low, high = -inner + n * each + 0.006, -inner + (n + 1) * each - 0.006
        cushion(model, low, high, front - 0.02, 0.26, 0.30, 0.455, 0.03, piped=True)
        cushion(model, low + 0.012, high - 0.012, 0.10, 0.285, 0.43, 0.80, 0.04, lean=0.085)
    return model


def office_chair():
    model = Model("office_chair")

    base = bmesh.new()
    for n in range(5):
        angle = math.radians(90 + n * 72)
        before = set(base.verts)
        prism_y(base, (0.0, 0.07, 0.05, 0.035, -0.03), (0.0, 0.052, 0.036, 0.026, -0.29))
        made = [vert for vert in base.verts if vert not in before]
        bmesh.ops.rotate(base, cent=(0, 0, 0), matrix=Matrix.Rotation(angle, 3, "Z"), verts=made)
        lathe(base, [(0.0, -0.024), (0.022, -0.024), (0.03, -0.012), (0.03, 0.012), (0.022, 0.024), (0.0, 0.024)], segments=10,
              to=Matrix.Rotation(angle, 4, "Z") @ along_x(0.0, -0.285, 0.03))
    lathe(base, [(0.0, 0.05), (0.05, 0.05), (0.045, 0.1), (0.0, 0.1)], segments=12)
    cuboid(base, -0.12, 0.12, -0.12, 0.14, 0.415, 0.445)
    cuboid(base, -0.035, 0.035, 0.2, 0.235, 0.40, 0.72)
    cuboid(base, -0.035, 0.035, 0.12, 0.235, 0.40, 0.43)
    model.turned(base, "rubber", sharp=35.0)

    column = bmesh.new()
    lathe(column, [(0.03, 0.1), (0.03, 0.24), (0.02, 0.25), (0.02, 0.415)], segments=12, caps=False)
    model.turned(column, "metal")

    seat = bmesh.new()
    cage = grid_box(seat, spread(-0.24, 0.24, 2), spread(-0.25, 0.22, 2), [0.445, 0.53])
    cage[(1, 1, 1)].co.z += 0.02
    cage[(1, 0, 1)].co.z -= 0.02
    crease(seat, cage, 0.3)
    model.soft(seat, "cloth", levels=2)

    rest = bmesh.new()
    cage = grid_box(rest, spread(-0.21, 0.21, 2), [0.215, 0.285], spread(0.62, 1.02, 2))
    for (i, j, k), vert in cage.items():
        vert.co.y += 0.05 * (vert.co.z - 0.62) / 0.4
    cage[(1, 0, 1)].co.y -= 0.03
    crease(rest, cage, 0.3)
    model.soft(rest, "cloth", levels=2)
    return model


def television(model, low):
    """A television set with a picture tube, its foot at height low."""
    half, front, back, high = 0.33, -0.22, 0.24, 0.5
    case = bmesh.new()
    cuboid(case, -half, half, front, front + 0.16, low + 0.03, low + high)
    prism_y(case, (0.0, low + 0.03 + (high - 0.03) / 2, 2 * half - 0.03, high - 0.05, front + 0.16), (0.0, low + 0.26, 0.34, 0.28, back))
    cuboid(case, -0.2, 0.2, front + 0.03, back - 0.1, low, low + 0.03)
    model.hard(case, "plastic", bevel=0.012, segments=2)

    tube = bmesh.new()
    cuboid(tube, -half + 0.045, half - 0.045, front - 0.006, front + 0.002, low + 0.12, low + high - 0.04)
    model.hard(tube, "screen", bevel=0.012, segments=2)

    dials = bmesh.new()
    for x in (0.12, 0.17, 0.22, 0.27):
        cuboid(dials, x - 0.015, x + 0.015, front - 0.005, front, low + 0.055, low + 0.075)
    model.hard(dials, "rubber", bevel=0.002, segments=1)


def tv():
    model = Model("tv")
    television(model, 0.0)
    return model


def tv_console():
    """A set in a wooden cabinet that stands on the floor on its own legs."""
    model = Model("tv_console")
    half, front, back, low, top = 0.42, -0.22, 0.22, 0.2, 0.78

    body = bmesh.new()
    cuboid(body, -half, half, front, back, low, top)
    for x in (-1, 1):
        for y in (front + 0.05, back - 0.05):
            prism(body, (x * (half - 0.06), y, 0.03, 0.03, 0.0), (x * (half - 0.05), y, 0.05, 0.05, low))
    model.hard(body, "wood", bevel=0.008)

    trim = bmesh.new()
    cuboid(trim, -half + 0.04, 0.15, front - 0.012, front, low + 0.06, top - 0.05)
    for n in range(7):
        cuboid(trim, 0.21, half - 0.05, front - 0.008, front, low + 0.07 + n * 0.028, low + 0.082 + n * 0.028)
    model.hard(trim, "rubber", bevel=0.004, segments=1)

    tube = bmesh.new()
    cuboid(tube, -half + 0.075, 0.115, front - 0.018, front - 0.01, low + 0.095, top - 0.085)
    model.hard(tube, "screen", bevel=0.02, segments=3)

    dials = bmesh.new()
    for z in (top - 0.1, top - 0.19):
        lathe(dials, [(0.0, 0.0), (0.026, 0.0), (0.022, 0.022), (0.0, 0.025)], segments=12, to=along_y(0.285, front, z))
    lathe(dials, [(0.0, top), (0.05, top), (0.04, top + 0.025), (0.0, top + 0.03)], segments=12)
    for side in (-1, 1):
        prism(dials, (side * 0.01, 0.0, 0.007, 0.007, top + 0.02), (side * 0.2, 0.02, 0.004, 0.004, top + 0.42))
    model.turned(dials, "metal", sharp=35.0)
    return model


def table_lamp():
    model = Model("table_lamp")

    foot = bmesh.new()
    lathe(foot, [(0.0, 0.0), (0.07, 0.0), (0.075, 0.012), (0.05, 0.028), (0.034, 0.07), (0.058, 0.15), (0.062, 0.19), (0.03, 0.255), (0.014, 0.275), (0.0, 0.275)], segments=18)
    model.turned(foot, "ceramic", sharp=50.0)

    stem = bmesh.new()
    lathe(stem, [(0.008, 0.275), (0.008, 0.36), (0.014, 0.365), (0.014, 0.39), (0.0, 0.39)], segments=10, caps=False)
    model.turned(stem, "metal")

    shade = bmesh.new()
    lathe(shade, [(0.15, 0.27), (0.1, 0.47)], segments=20, caps=False)
    bmesh.ops.create_uvsphere(shade, u_segments=10, v_segments=6, radius=0.03, matrix=Matrix.Translation((0.0, 0.0, 0.41)))
    model.turned(shade, "shade", sharp=60.0)
    return model


def toilet():
    model = Model("toilet")

    tank = bmesh.new()
    cuboid(tank, -0.2, 0.2, 0.16, 0.34, 0.42, 0.78)
    model.hard(tank, "ceramic", bevel=0.025, segments=3)
    lid = bmesh.new()
    cuboid(lid, -0.21, 0.21, 0.15, 0.345, 0.78, 0.81)
    model.hard(lid, "ceramic", bevel=0.01, segments=2)

    bowl = bmesh.new()
    lathe(bowl, [(0.0, 0.0), (0.105, 0.0), (0.11, 0.03), (0.085, 0.12), (0.095, 0.2), (0.15, 0.31), (0.178, 0.38), (0.182, 0.4),
                 (0.165, 0.405), (0.145, 0.35), (0.1, 0.27), (0.04, 0.24), (0.0, 0.235)], cy=-0.05, segments=20, sy=1.3)
    cuboid(bowl, -0.1, 0.1, 0.05, 0.2, 0.0, 0.4)
    model.turned(bowl, "ceramic", sharp=50.0)

    seat = bmesh.new()
    lathe(seat, [(0.125, 0.405), (0.19, 0.405), (0.192, 0.418), (0.185, 0.428), (0.13, 0.428)], cy=-0.05, segments=20, sy=1.3, closed=True)
    cuboid(seat, -0.18, 0.18, 0.125, 0.16, 0.41, 0.83)
    model.turned(seat, "enamel", sharp=50.0)

    handle = bmesh.new()
    cuboid(handle, -0.17, -0.11, 0.145, 0.16, 0.7, 0.715)
    model.hard(handle, "metal", bevel=0.003, segments=1)
    return model


def bath():
    model = Model("bath")
    half, front, back, top = 0.85, -0.38, 0.38, 0.56
    rim, slope = 0.06, 0.1

    tub = bmesh.new()
    xs = [-half, -half + rim, -half + rim + slope, 0.0, half - rim - slope, half - rim, half]
    ys = [front, front + rim, front + rim + slope, back - rim - slope, back - rim, back]
    cage = grid_box(tub, xs, ys, [0.0, top])
    for (i, j, k), vert in cage.items():
        if k == 1 and 2 <= i <= 4 and 2 <= j <= 3:
            vert.co.z = 0.13
    crease(tub, cage, 0.75)
    model.soft(tub, "ceramic", levels=2)

    taps = bmesh.new()
    lathe(taps, [(0.018, top), (0.018, top + 0.03), (0.012, top + 0.04), (0.012, top + 0.13), (0.0, top + 0.135)], cx=half - 0.03, segments=12)
    lathe(taps, [(0.01, 0.0), (0.01, 0.13), (0.0, 0.133)], segments=10, to=along_x(half - 0.03, 0.0, top + 0.115) @ Matrix.Rotation(math.radians(180), 4, "X"))
    for y in (-0.09, 0.09):
        lathe(taps, [(0.014, top), (0.014, top + 0.04), (0.022, top + 0.045), (0.022, top + 0.062), (0.0, top + 0.066)], cx=half - 0.03, cy=y, segments=10)
    model.turned(taps, "metal")
    return model


def washing_machine():
    model = Model("washing_machine")
    half, front, back, top = 0.34, -0.33, 0.33, 0.92

    body = bmesh.new()
    cuboid(body, -half, half, front, back, 0.03, top)
    cuboid(body, -half, half, back - 0.14, back, top, top + 0.16)
    model.hard(body, "enamel", bevel=0.012, segments=3)

    lid = bmesh.new()
    cuboid(lid, -half + 0.05, half - 0.05, front + 0.04, back - 0.16, top, top + 0.016)
    model.hard(lid, "enamel", bevel=0.006, segments=2)

    feet = bmesh.new()
    for x in (-1, 1):
        for y in (front + 0.05, back - 0.05):
            lathe(feet, [(0.0, 0.0), (0.025, 0.0), (0.025, 0.03), (0.0, 0.03)], cx=x * (half - 0.06), cy=y, segments=10)
    cuboid(feet, -half + 0.03, half - 0.03, back - 0.146, back - 0.14, top + 0.03, top + 0.13)
    model.turned(feet, "rubber")

    dials = bmesh.new()
    lathe(dials, [(0.0, 0.0), (0.04, 0.0), (0.036, 0.022), (0.0, 0.025)], segments=14, to=along_y(-0.17, back - 0.146, top + 0.08))
    for x in (0.03, 0.12, 0.21):
        lathe(dials, [(0.0, 0.0), (0.02, 0.0), (0.018, 0.018), (0.0, 0.02)], segments=10, to=along_y(x, back - 0.146, top + 0.08))
    model.turned(dials, "metal")
    return model


def wheelie_bin():
    model = Model("wheelie_bin")

    body = bmesh.new()
    prism(body, (0.0, -0.02, 0.44, 0.5, 0.05), (0.0, 0.0, 0.56, 0.7, 0.96))
    cuboid(body, -0.3, 0.3, -0.37, 0.37, 0.94, 0.985)
    cuboid(body, -0.23, 0.23, 0.37, 0.41, 0.93, 0.965)
    model.hard(body, "plastic", bevel=0.012, segments=2)

    lid = bmesh.new()
    prism(lid, (0.0, 0.0, 0.62, 0.76, 0.985), (0.0, 0.01, 0.55, 0.68, 1.045))
    cuboid(lid, -0.12, 0.12, -0.4, -0.37, 0.985, 1.01)
    model.hard(lid, "plastic", bevel=0.014, segments=2)

    wheels = bmesh.new()
    for side in (-1, 1):
        lathe(wheels, [(0.0, -0.025), (0.085, -0.025), (0.1, -0.015), (0.1, 0.015), (0.085, 0.025), (0.0, 0.025)], segments=16, to=along_x(side * 0.255, 0.25, 0.1))
    lathe(wheels, [(0.012, -0.25), (0.012, 0.25)], segments=8, caps=False, to=along_x(0.0, 0.25, 0.1))
    model.turned(wheels, "rubber")
    return model


def litter_bin():
    """A bin for litter with a hooded top and a flap to push it through."""
    model = Model("litter_bin")

    body = bmesh.new()
    prism(body, (0.0, 0.0, 0.4, 0.4, 0.0), (0.0, 0.0, 0.46, 0.46, 0.78))
    model.hard(body, "plastic", bevel=0.012, segments=2)

    hood = bmesh.new()
    cuboid(hood, -0.245, 0.245, -0.245, 0.245, 0.78, 1.0)
    model.hard(hood, "plastic", bevel=0.035, segments=3)

    flap = bmesh.new()
    cuboid(flap, -0.16, 0.16, -0.25, -0.238, 0.83, 0.95)
    model.hard(flap, "rubber", bevel=0.004, segments=1)
    return model


def mailbox():
    """A letter box on a post at the kerb."""
    model = Model("mailbox")

    post = bmesh.new()
    cuboid(post, -0.045, 0.045, -0.045, 0.045, 0.0, 1.02)
    cuboid(post, -0.07, 0.07, -0.2, 0.2, 1.0, 1.03)
    model.hard(post, "dark_wood", bevel=0.005)

    box = bmesh.new()
    arch = [(-0.085, 1.03), (0.085, 1.03), (0.085, 1.14)]
    arch += [(0.085 * math.cos(math.radians(a)), 1.14 + 0.085 * math.sin(math.radians(a))) for a in range(15, 180, 15)]
    arch.append((-0.085, 1.14))
    outline_along_y(box, arch, -0.24, 0.24)
    outline_along_y(box, [(x * 1.04, 1.027 + (z - 1.03) * 1.03) for x, z in arch], -0.25, -0.24)
    model.turned(box, "steel", sharp=40.0)

    latch = bmesh.new()
    cuboid(latch, -0.012, 0.012, -0.262, -0.25, 1.19, 1.215)
    model.hard(latch, "metal", bevel=0.003, segments=1)
    return model


def postbox():
    """A box in the street that letters are posted into."""
    model = Model("postbox")

    body = bmesh.new()
    arch = [(-0.28, 0.14), (0.28, 0.14), (0.28, 0.95)]
    arch += [(0.28 * math.cos(math.radians(a)), 0.95 + 0.24 * math.sin(math.radians(a))) for a in range(15, 180, 15)]
    arch.append((-0.28, 0.95))
    outline_along_y(body, arch, -0.25, 0.25)
    model.turned(body, "paint", sharp=40.0)

    parts = bmesh.new()
    for x in (-1, 1):
        for y in (-1, 1):
            cuboid(parts, x * 0.24 - 0.025, x * 0.24 + 0.025, y * 0.21 - 0.025, y * 0.21 + 0.025, 0.0, 0.15)
    cuboid(parts, -0.22, 0.22, -0.268, -0.25, 0.9, 1.06)
    cuboid(parts, -0.2, 0.2, -0.262, -0.25, 0.25, 0.72)
    model.hard(parts, "paint", bevel=0.006)

    pull = bmesh.new()
    bar_handle(pull, -0.12, 0.12, -0.268, 1.02, 1.035, proud=0.03, thick=0.012)
    model.hard(pull, "metal", bevel=0.003, segments=1)
    return model


def park_bench():
    model = Model("park_bench")
    half, seat = 0.9, 0.455

    def lean(z):
        """How far back the bench's back stands at height z."""
        return 0.18 + (z - 0.43) * 0.28

    frame = bmesh.new()
    for side in (-1, 1):
        x = side * (half - 0.12)
        prism(frame, (x, -0.26, 0.05, 0.05, 0.0), (x, -0.2, 0.05, 0.05, 0.43))
        prism(frame, (x, 0.3, 0.05, 0.05, 0.0), (x, lean(0.43), 0.05, 0.05, 0.43))
        prism(frame, (x, lean(0.43), 0.05, 0.045, 0.43), (x, lean(0.86), 0.05, 0.04, 0.86))
        cuboid(frame, x - 0.025, x + 0.025, -0.23, lean(0.43) + 0.02, 0.39, 0.43)
    model.hard(frame, "dark_wood", bevel=0.006)

    slats = bmesh.new()
    for n in range(4):
        y = -0.26 + n * 0.108
        cuboid(slats, -half, half, y, y + 0.09, 0.43, seat)
    for z in (0.53, 0.645, 0.76):
        prism(slats, (0.0, lean(z) - 0.035, 2 * half, 0.024, z), (0.0, lean(z + 0.09) - 0.035, 2 * half, 0.024, z + 0.09))
    model.hard(slats, "wood", bevel=0.005)
    return model


def picnic_table():
    model = Model("picnic_table")
    half, top, seat = 0.9, 0.76, 0.46

    boards = bmesh.new()
    for n in range(5):
        y = -0.37 + n * 0.15
        cuboid(boards, -half, half, y, y + 0.14, top - 0.04, top)
    for side in (-1, 1):
        for n in range(2):
            y = side * 0.62 - 0.14 + n * 0.145
            cuboid(boards, -half, half, y, y + 0.135, seat - 0.04, seat)
    model.hard(boards, "wood", bevel=0.006)

    frame = bmesh.new()
    for end in (-1, 1):
        x = end * 0.62
        for side in (-1, 1):
            prism(frame, (x, side * 0.72, 0.05, 0.09, 0.0), (x, side * 0.2, 0.05, 0.09, top - 0.04))
        cuboid(frame, x - 0.025, x + 0.025, -0.76, 0.76, seat - 0.1, seat - 0.04)
        cuboid(frame, x - 0.025, x + 0.025, -0.36, 0.36, top - 0.1, top - 0.04)
    model.hard(frame, "steel", bevel=0.006)
    return model


def crate():
    model = Model("crate")
    half, top = 0.42, 0.84

    box = bmesh.new()
    cuboid(box, -half + 0.022, half - 0.022, -half + 0.022, half - 0.022, 0.0, top - 0.022)
    wide = (2 * half - 0.044) / 4
    for n in range(4):
        low = -half + 0.022 + n * wide
        cuboid(box, low + 0.003, low + wide - 0.003, -half + 0.02, half - 0.02, top - 0.03, top - 0.008)
        for side in (-1, 1):
            cuboid(box, low + 0.003, low + wide - 0.003, side * (half - 0.03), side * (half - 0.008), 0.01, top - 0.03)
            cuboid(box, side * (half - 0.03), side * (half - 0.008), low + 0.003, low + wide - 0.003, 0.01, top - 0.03)
    model.hard(box, "wood", bevel=0.003, segments=1)

    battens = bmesh.new()
    for side in (-1, 1):
        for z in (0.0, top - 0.075):
            cuboid(battens, -half, half, side * half - 0.011, side * half + 0.011, z, z + 0.075)
            cuboid(battens, side * half - 0.011, side * half + 0.011, -half, half, z, z + 0.075)
        for other in (-1, 1):
            cuboid(battens, side * half - 0.011, side * half + 0.011, other * (half - 0.0375) - 0.0375, other * (half - 0.0375) + 0.0375, 0.075, top - 0.075)
            cuboid(battens, other * (half - 0.0375) - 0.0375, other * (half - 0.0375) + 0.0375, side * half - 0.011, side * half + 0.011, 0.075, top - 0.075)
    model.hard(battens, "wood", bevel=0.004, segments=1)

    # A steel plate over each corner of the lid.
    caps = bmesh.new()
    for x in (-1, 1):
        for y in (-1, 1):
            x0, x1 = sorted((x * (half - 0.06), x * (half + 0.012)))
            y0, y1 = sorted((y * (half - 0.06), y * (half + 0.012)))
            cuboid(caps, x0, x1, y0, y1, top - 0.012, top + 0.003)
    model.hard(caps, "metal", bevel=0.002, segments=1)
    return model


def barrel():
    """A steel drum."""
    model = Model("barrel")
    body = bmesh.new()
    wide = 0.285
    profile = [(0.0, 0.0), (wide, 0.0), (wide + 0.008, 0.01), (wide + 0.008, 0.03), (wide, 0.04)]
    for z in (0.3, 0.58):
        profile += [(wide, z - 0.015), (wide + 0.011, z - 0.004), (wide + 0.011, z + 0.012), (wide, z + 0.023)]
    profile += [(wide, 0.84), (wide + 0.008, 0.85), (wide + 0.008, 0.87), (wide, 0.88), (wide - 0.012, 0.88), (wide - 0.012, 0.862), (0.0, 0.862)]
    lathe(body, profile, segments=28)
    model.turned(body, "paint", sharp=35.0)

    bung = bmesh.new()
    lathe(bung, [(0.0, 0.862), (0.028, 0.862), (0.028, 0.872), (0.012, 0.876), (0.0, 0.876)], cx=0.16, segments=10)
    model.turned(bung, "metal")
    return model


def outline_along_x(bm, outline, x0, x1):
    """A body with the same outline, given as (y, z) going round, from x0 to x1."""
    left = [bm.verts.new((x0, y, z)) for y, z in outline]
    right = [bm.verts.new((x1, y, z)) for y, z in outline]
    count = len(outline)
    for i in range(count):
        j = (i + 1) % count
        bm.faces.new((left[i], left[j], right[j], right[i]))
    bm.faces.new(left[::-1])
    bm.faces.new(right)


def framed(name, width, height):
    """A frame on a wall, its back at +Y. What it holds fills the whole of it behind the moulding, so the mod can lay
    a picture over the frame's outline and the moulding hides the picture's own edge."""
    model = Model(name)
    half, rail, front, back = width / 2, 0.045, -0.016, 0.016
    moulding = bmesh.new()
    cuboid(moulding, -half, half, front, back, 0.0, rail)
    cuboid(moulding, -half, half, front, back, height - rail, height)
    cuboid(moulding, -half, -half + rail, front, back, rail, height - rail)
    cuboid(moulding, half - rail, half, front, back, rail, height - rail)
    model.hard(moulding, "dark_wood", bevel=0.007, segments=2)
    held = bmesh.new()
    cuboid(held, -half + rail - 0.004, half - rail + 0.004, front + 0.011, back, rail - 0.004, height - rail + 0.004)
    model.plain(held, "pane")
    return model


def canvas():
    """One square's length of a picture too long for one square: a metre each way, with no frame, so that the
    lengths of it meet."""
    model = Model("canvas")
    board = bmesh.new()
    cuboid(board, -0.5, 0.5, -0.0125, 0.0125, 0.0, 1.0)
    model.plain(board, "pane")
    return model


def wall_clock():
    model = Model("wall_clock")
    middle = 0.16
    rim = bmesh.new()
    lathe(rim, [(0.0, 0.0), (0.16, 0.0), (0.16, 0.03), (0.152, 0.042), (0.14, 0.042), (0.14, 0.012), (0.0, 0.012)], segments=28,
          to=along_y(0.0, 0.02, middle))
    model.turned(rim, "rubber", sharp=35.0)
    face = bmesh.new()
    lathe(face, [(0.0, 0.012), (0.14, 0.012), (0.14, 0.014), (0.0, 0.014)], segments=28, to=along_y(0.0, 0.02, middle))
    model.turned(face, "enamel", sharp=35.0)
    marks = bmesh.new()
    for hour in range(12):
        angle = math.radians(hour * 30)
        long = 0.022 if hour % 3 == 0 else 0.012
        before = set(marks.verts)
        cuboid(marks, -0.004, 0.004, 0.004, 0.0065, middle + 0.128 - long, middle + 0.128)
        bmesh.ops.rotate(marks, cent=(0, 0, middle), matrix=Matrix.Rotation(angle, 3, "Y"), verts=[v for v in marks.verts if v not in before])
    # Ten past ten.
    for angle, long, wide, proud in ((-55, 0.075, 0.006, 0.003), (60, 0.11, 0.0045, 0.0015)):
        before = set(marks.verts)
        cuboid(marks, -wide, wide, proud, proud + 0.002, middle - 0.012, middle + long)
        bmesh.ops.rotate(marks, cent=(0, 0, middle), matrix=Matrix.Rotation(math.radians(angle), 3, "Y"), verts=[v for v in marks.verts if v not in before])
    model.plain(marks, "rubber")
    return model


def table(name, width, depth, top=0.75, leg=0.06, wood="wood"):
    """A table on four legs with a rail under its top, width along x."""
    model = Model(name)
    hx, hy = width / 2, depth / 2
    inset = leg / 2 + 0.035
    under = bmesh.new()
    for x in (-1, 1):
        for y in (-1, 1):
            prism(under, (x * (hx - inset), y * (hy - inset), leg * 0.65, leg * 0.65, 0.0), (x * (hx - inset), y * (hy - inset), leg, leg, top - 0.035))
    rail = min(0.09, top * 0.2)
    for side in (-1, 1):
        cuboid(under, side * (hx - inset) - 0.011, side * (hx - inset) + 0.011, -hy + inset + leg / 2, hy - inset - leg / 2, top - 0.035 - rail, top - 0.035)
        cuboid(under, -hx + inset + leg / 2, hx - inset - leg / 2, side * (hy - inset) - 0.011, side * (hy - inset) + 0.011, top - 0.035 - rail, top - 0.035)
    model.hard(under, wood, bevel=0.004, segments=1)
    board = bmesh.new()
    cuboid(board, -hx, hx, -hy, hy, top - 0.035, top)
    model.hard(board, wood, bevel=0.011, segments=3)
    return model


def round_table():
    model = Model("round_table")
    top = 0.72
    board = bmesh.new()
    lathe(board, [(0.0, top - 0.035), (0.49, top - 0.035), (0.5, top - 0.027), (0.5, top - 0.008), (0.492, top), (0.0, top)], segments=32)
    model.turned(board, "wood", sharp=50.0)
    foot = bmesh.new()
    lathe(foot, [(0.07, top - 0.035), (0.05, top - 0.08), (0.035, top - 0.3), (0.05, 0.3), (0.06, 0.2), (0.045, 0.15), (0.0, 0.15)], segments=16, caps=False)
    for n in range(3):
        before = set(foot.verts)
        prism_y(foot, (0.0, 0.17, 0.055, 0.07, -0.03), (0.0, 0.03, 0.045, 0.045, -0.38))
        bmesh.ops.rotate(foot, cent=(0, 0, 0), matrix=Matrix.Rotation(math.radians(n * 120), 3, "Z"), verts=[v for v in foot.verts if v not in before])
    model.turned(foot, "wood", sharp=40.0)
    return model


def office_desk():
    """A desk three squares long: a top on two panel ends, a pedestal of drawers at one end and a board across its back."""
    model = Model("office_desk")
    half, front, back, top = 1.45, -0.44, 0.44, 0.76

    board = bmesh.new()
    cuboid(board, -half, half, front, back, top - 0.04, top)
    model.hard(board, "wood", bevel=0.008, segments=3)

    body = bmesh.new()
    cuboid(body, half - 0.5, half - 0.03, front + 0.06, back - 0.04, 0.0, top - 0.04)
    cuboid(body, -half + 0.03, -half + 0.07, front + 0.06, back - 0.04, 0.0, top - 0.04)
    cuboid(body, -half + 0.07, half - 0.5, back - 0.08, back - 0.055, 0.22, top - 0.04)
    model.hard(body, "wood", bevel=0.005)

    fronts, pulls = bmesh.new(), bmesh.new()
    drawer_fronts(fronts, pulls, half - 0.5, half - 0.03, 0.03, top - 0.045, 3, front + 0.06 - 0.018, size=1.1)
    model.hard(fronts, "wood", bevel=0.004)
    model.turned(pulls, "metal")
    return model


def low_shelves():
    """One square of a run of low open shelves, a metre wide so that each meets the next."""
    model = Model("low_shelves")
    half, front, back, top = 0.5, -0.19, 0.19, 0.84
    body = bmesh.new()
    for side in (-1, 1):
        cuboid(body, side * (half - 0.011) - 0.011, side * (half - 0.011) + 0.011, front, back, 0.0, top - 0.025)
    cuboid(body, -half, half, front - 0.012, back, top - 0.025, top)
    cuboid(body, -half + 0.022, half - 0.022, back - 0.012, back, 0.0, top - 0.025)
    for z in (0.07, 0.44):
        cuboid(body, -half + 0.022, half - 0.022, front + 0.006, back - 0.012, z - 0.022, z)
    model.hard(body, "wood", bevel=0.004, segments=1)
    return model


def wall_shelf(name, boards):
    """Boards on brackets on a wall, the wall at +Y. Its top is the top of the highest board."""
    model = Model(name)
    half, front, back = 0.45, -0.13, 0.13
    planks, brackets = bmesh.new(), bmesh.new()
    for n in range(boards):
        z = n * 0.4
        cuboid(planks, -half, half, front, back, z + 0.13, z + 0.16)
        for side in (-1, 1):
            x = side * (half - 0.12)
            cuboid(brackets, x - 0.009, x + 0.009, back - 0.012, back, z, z + 0.13)
            cuboid(brackets, x - 0.009, x + 0.009, front + 0.04, back - 0.012, z + 0.118, z + 0.13)
            prism_y(brackets, (x, z + 0.105, 0.008, 0.012, front + 0.07), (x, z + 0.02, 0.008, 0.012, back - 0.012))
    model.hard(planks, "wood", bevel=0.005, segments=2)
    model.hard(brackets, "metal", bevel=0.002, segments=1)
    return model


def metal_rack():
    """Steel racking two squares long: four slotted uprights and three decks."""
    model = Model("metal_rack")
    half, front, back, top = 0.93, -0.3, 0.3, 1.95
    posts = bmesh.new()
    for x in (-1, 1):
        for y in (front + 0.025, back - 0.025):
            cuboid(posts, x * (half - 0.025) - 0.025, x * (half - 0.025) + 0.025, y - 0.025, y + 0.025, 0.0, top)
        prism_y(posts, (x * (half - 0.025), 0.25, 0.012, 0.03, front + 0.05), (x * (half - 0.025), 0.9, 0.012, 0.03, back - 0.05))
        prism_y(posts, (x * (half - 0.025), 1.75, 0.012, 0.03, front + 0.05), (x * (half - 0.025), 1.05, 0.012, 0.03, back - 0.05))
    model.hard(posts, "paint", bevel=0.004, segments=1)
    decks = bmesh.new()
    for z in (0.12, 0.95, 1.72):
        cuboid(decks, -half + 0.05, half - 0.05, front, back, z - 0.045, z)
    model.hard(decks, "paint", bevel=0.005, segments=1)
    return model


def store_shelves():
    """Shop shelving two squares long that stands with its back to a wall or to another: a back, a deep foot and four shelves."""
    model = Model("store_shelves")
    half, front, back, top = 0.94, -0.24, 0.24, 1.9
    body = bmesh.new()
    cuboid(body, -half, half, back - 0.03, back, 0.0, top)
    cuboid(body, -half, half, front - 0.04, back - 0.03, 0.0, 0.16)
    for side in (-1, 1):
        cuboid(body, side * (half - 0.012) - 0.012, side * (half - 0.012) + 0.012, front + 0.06, back - 0.03, 0.16, top)
    model.hard(body, "paint", bevel=0.005, segments=1)
    shelves = bmesh.new()
    for z in (0.55, 0.92, 1.29, 1.66):
        prism_y(shelves, (0.0, z - 0.008, 2 * half - 0.05, 0.03, front + 0.02), (0.0, z + 0.012, 2 * half - 0.05, 0.03, back - 0.03))
        cuboid(shelves, -half + 0.025, half - 0.025, front + 0.012, front + 0.02, z - 0.03, z + 0.022)
    model.hard(shelves, "paint", bevel=0.003, segments=1)
    return model


def plastic_chair():
    """A stacking chair: a moulded seat and back on four steel legs."""
    model = Model("plastic_chair")
    seat = 0.44
    legs = bmesh.new()
    for x in (-1, 1):
        prism(legs, (x * 0.2, -0.2, 0.02, 0.02, 0.0), (x * 0.17, -0.15, 0.02, 0.02, seat))
        prism(legs, (x * 0.2, 0.22, 0.02, 0.02, 0.0), (x * 0.17, 0.16, 0.02, 0.02, seat))
        prism(legs, (x * 0.17, 0.16, 0.02, 0.02, seat), (x * 0.16, 0.24, 0.02, 0.02, 0.74))
    model.hard(legs, "metal", bevel=0.004, segments=1)
    shell = bmesh.new()
    cage = grid_box(shell, spread(-0.22, 0.22, 2), spread(-0.22, 0.2, 2), [seat, seat + 0.022])
    for (i, j, k), vert in cage.items():
        if i == 1 and j == 1:
            vert.co.z -= 0.018
        if j == 0:
            vert.co.z -= 0.02
    crease(shell, cage, 0.5)
    model.soft(shell, "plastic", levels=2)
    rest = bmesh.new()
    cage = grid_box(rest, spread(-0.21, 0.21, 2), [0.215, 0.235], spread(0.58, 0.86, 2))
    for (i, j, k), vert in cage.items():
        vert.co.y += 0.07 * (vert.co.z - 0.58) / 0.28 + (0.02 if i == 1 else 0.0)
    crease(rest, cage, 0.5)
    model.soft(rest, "plastic", levels=2)
    return model


def folding_chair():
    """A steel folding chair: two frames crossed at the seat, a padded seat and back."""
    model = Model("folding_chair")
    seat = 0.44
    frame = bmesh.new()
    for x in (-1, 1):
        prism(frame, (x * 0.2, -0.22, 0.02, 0.02, 0.0), (x * 0.2, 0.24, 0.02, 0.02, 0.86))
        prism(frame, (x * 0.18, 0.25, 0.02, 0.02, 0.0), (x * 0.18, -0.18, 0.02, 0.02, seat - 0.01))
    cuboid(frame, -0.2, 0.2, -0.22, -0.2, 0.08, 0.1)
    cuboid(frame, -0.18, 0.18, 0.24, 0.26, 0.06, 0.08)
    model.hard(frame, "metal", bevel=0.004, segments=1)
    pads = bmesh.new()
    cage = grid_box(pads, spread(-0.19, 0.19, 2), spread(-0.2, 0.17, 2), [seat - 0.01, seat + 0.025])
    crease(pads, cage, 0.5)
    model.soft(pads, "vinyl", levels=2)
    back = bmesh.new()
    cage = grid_box(back, spread(-0.19, 0.19, 2), [0.195, 0.22], spread(0.66, 0.84, 2))
    for (i, j, k), vert in cage.items():
        vert.co.y += 0.5 * (vert.co.z - 0.66) * 0.46 / 0.86
    crease(back, cage, 0.5)
    model.soft(back, "vinyl", levels=2)
    return model


def booth():
    """A diner's bench seat two squares long: a padded seat and a high padded back on a boxed base."""
    model = Model("booth")
    half, front, back = 0.94, -0.4, 0.4
    base = bmesh.new()
    cuboid(base, -half, half, front + 0.06, back, 0.0, 0.3)
    cuboid(base, -half, half, back - 0.09, back, 0.3, 1.08)
    model.hard(base, "wood", bevel=0.008, segments=1)
    for side in (-1, 1):
        low, high = sorted((side * 0.006, side * (half - 0.01)))
        pad = bmesh.new()
        cage = grid_box(pad, spread(low, high, 2), spread(front, back - 0.14, 2), [0.3, 0.46])
        cage[(1, 1, 1)].co.z += 0.02
        crease(pad, cage, 0.4)
        model.soft(pad, "vinyl", levels=2)
        rest = bmesh.new()
        cage = grid_box(rest, spread(low, high, 2), [back - 0.2, back - 0.09], spread(0.44, 1.1, 2))
        for (i, j, k), vert in cage.items():
            vert.co.y += 0.05 * (vert.co.z - 0.44) / 0.66
        cage[(1, 0, 1)].co.y -= 0.025
        crease(rest, cage, 0.4)
        model.soft(rest, "vinyl", levels=2)
    return model


def stool():
    model = Model("stool")
    top = 0.45
    seat = bmesh.new()
    lathe(seat, [(0.0, top - 0.04), (0.15, top - 0.04), (0.16, top - 0.03), (0.16, top - 0.008), (0.15, top), (0.0, top)], segments=20)
    model.turned(seat, "wood", sharp=50.0)
    legs = bmesh.new()
    for n in range(3):
        angle = math.radians(90 + n * 120)
        x, y = math.cos(angle), math.sin(angle)
        prism(legs, (x * 0.17, y * 0.17, 0.028, 0.028, 0.0), (x * 0.1, y * 0.1, 0.036, 0.036, top - 0.04))
    lathe(legs, [(0.118, 0.17), (0.118, 0.19)], segments=12, caps=False)
    model.hard(legs, "wood", bevel=0.004, segments=1)
    return model


def vending_machine():
    model = Model("vending_machine")
    half, front, back, top = 0.46, -0.38, 0.38, 1.83
    body = bmesh.new()
    cuboid(body, -half, half, front, back, 0.03, top)
    model.hard(body, "paint", bevel=0.01, segments=2)
    feet = bmesh.new()
    for x in (-1, 1):
        for y in (front + 0.06, back - 0.06):
            cuboid(feet, x * (half - 0.07) - 0.03, x * (half - 0.07) + 0.03, y - 0.03, y + 0.03, 0.0, 0.03)
    cuboid(feet, -half + 0.06, half - 0.3, front - 0.012, front, 0.2, 0.36)
    for n in range(4):
        for m in range(3):
            cuboid(feet, half - 0.2 + m * 0.045, half - 0.165 + m * 0.045, front - 0.008, front, 0.98 + n * 0.045, 1.015 + n * 0.045)
    model.hard(feet, "rubber", bevel=0.004, segments=1)
    glass = bmesh.new()
    cuboid(glass, -half + 0.06, half - 0.28, front - 0.008, front, 0.5, 1.5)
    model.hard(glass, "screen", bevel=0.004, segments=1)
    sign = bmesh.new()
    cuboid(sign, -half + 0.04, half - 0.04, front - 0.01, front, 1.56, 1.76)
    model.hard(sign, "shade", bevel=0.004, segments=1)
    trim = bmesh.new()
    cuboid(trim, half - 0.2, half - 0.08, front - 0.01, front, 0.78, 0.82)
    cuboid(trim, half - 0.2, half - 0.08, front - 0.01, front, 1.22, 1.4)
    model.hard(trim, "metal", bevel=0.003, segments=1)
    return model


def arcade_machine():
    model = Model("arcade_machine")
    half = 0.36
    side = [(-0.34, 0.0), (0.36, 0.0), (0.36, 1.78), (-0.22, 1.78), (-0.3, 1.56), (-0.14, 1.5), (-0.06, 1.08), (-0.4, 0.98), (-0.4, 0.86), (-0.34, 0.82)]
    body = bmesh.new()
    outline_along_x(body, side, -half, half)
    model.hard(body, "plastic", bevel=0.006, segments=1)
    tube = bmesh.new()
    prism_y(tube, (0.0, 1.11, 2 * half - 0.1, 0.03, -0.075), (0.0, 1.47, 2 * half - 0.1, 0.03, -0.145))
    model.plain(tube, "screen")
    sign = bmesh.new()
    prism_y(sign, (0.0, 1.585, 2 * half - 0.06, 0.02, -0.3), (0.0, 1.76, 2 * half - 0.06, 0.02, -0.235))
    model.plain(sign, "shade")
    sticks = bmesh.new()
    for x in (-0.16, 0.16):
        lathe(sticks, [(0.0, 0.0), (0.009, 0.0), (0.009, 0.05), (0.02, 0.055), (0.02, 0.085), (0.0, 0.09)], cx=x, cy=-0.27, segments=10,
              to=Matrix.Translation((0.0, 0.0, 1.0)))
    for x in (-0.05, 0.02, 0.09):
        lathe(sticks, [(0.0, 0.0), (0.016, 0.0), (0.014, 0.012), (0.0, 0.014)], cx=x, cy=-0.25, segments=10, to=Matrix.Translation((0.0, 0.0, 1.01)))
    cuboid(sticks, -0.1, 0.1, -0.345, -0.335, 0.4, 0.62)
    model.turned(sticks, "rubber", sharp=35.0)
    return model


def dumpster():
    """A steel skip for rubbish, two squares long, under two lids."""
    model = Model("dumpster")
    half, top = 0.93, 1.12
    body = bmesh.new()
    prism(body, (0.0, 0.0, 2 * half - 0.12, 0.74, 0.1), (0.0, 0.0, 2 * half, 0.9, top))
    for x in (-0.6, 0.0, 0.6):
        for y in (-1, 1):
            prism(body, (x, y * 0.38, 0.06, 0.03, 0.14), (x, y * 0.455, 0.06, 0.03, top - 0.03))
    cuboid(body, -half - 0.01, half + 0.01, -0.46, 0.46, top - 0.06, top)
    for side in (-1, 1):
        cuboid(body, side * (half + 0.03) - 0.04, side * (half + 0.03) + 0.04, -0.22, 0.22, 0.72, 0.84)
    model.hard(body, "paint", bevel=0.008, segments=1)
    feet = bmesh.new()
    for x in (-1, 1):
        for y in (-1, 1):
            lathe(feet, [(0.0, -0.02), (0.05, -0.02), (0.055, 0.0), (0.05, 0.02), (0.0, 0.02)], segments=12, to=along_x(x * 0.72, y * 0.3, 0.055))
    model.turned(feet, "rubber")
    lids = bmesh.new()
    for side in (-1, 1):
        low, high = sorted((side * 0.01, side * (half - 0.01)))
        prism(lids, ((low + high) / 2, 0.0, high - low, 0.92, top), ((low + high) / 2, 0.01, high - low - 0.03, 0.88, top + 0.045))
        cuboid(lids, (low + high) / 2 - 0.1, (low + high) / 2 + 0.1, -0.48, -0.455, top + 0.005, top + 0.03)
    model.hard(lids, "plastic", bevel=0.01, segments=2)
    return model


def radio():
    model = Model("radio")
    case = bmesh.new()
    cuboid(case, -0.16, 0.16, -0.055, 0.055, 0.0, 0.17)
    model.hard(case, "plastic", bevel=0.01, segments=2)
    trim = bmesh.new()
    cuboid(trim, -0.14, 0.02, -0.06, -0.055, 0.025, 0.145)
    cuboid(trim, -0.1, 0.1, -0.012, 0.012, 0.17, 0.185)
    model.hard(trim, "rubber", bevel=0.003, segments=1)
    dials = bmesh.new()
    for z in (0.05, 0.12):
        lathe(dials, [(0.0, 0.0), (0.02, 0.0), (0.017, 0.014), (0.0, 0.016)], segments=12, to=along_y(0.09, -0.055, z))
    prism(dials, (0.13, 0.03, 0.006, 0.006, 0.17), (0.2, 0.03, 0.003, 0.003, 0.52))
    model.turned(dials, "metal", sharp=35.0)
    return model


def microwave():
    model = Model("microwave")
    half, front, back, top = 0.25, -0.18, 0.18, 0.29
    case = bmesh.new()
    cuboid(case, -half, half, front, back, 0.012, top)
    model.hard(case, "enamel", bevel=0.008, segments=2)
    feet = bmesh.new()
    for x in (-1, 1):
        for y in (front + 0.04, back - 0.04):
            cuboid(feet, x * (half - 0.05) - 0.015, x * (half - 0.05) + 0.015, y - 0.015, y + 0.015, 0.0, 0.012)
    cuboid(feet, half - 0.12, half - 0.015, front - 0.004, front, 0.03, top - 0.02)
    model.hard(feet, "rubber", bevel=0.002, segments=1)
    glass = bmesh.new()
    cuboid(glass, -half + 0.035, half - 0.15, front - 0.005, front, 0.05, top - 0.04)
    model.hard(glass, "screen", bevel=0.006, segments=1)
    keys = bmesh.new()
    for n in range(4):
        for m in range(2):
            cuboid(keys, half - 0.105 + m * 0.045, half - 0.07 + m * 0.045, front - 0.007, front - 0.004, 0.05 + n * 0.035, 0.075 + n * 0.035)
    cuboid(keys, half - 0.105, half - 0.025, front - 0.007, front - 0.004, 0.2, 0.235)
    model.hard(keys, "metal", bevel=0.002, segments=1)
    return model


def dishwasher():
    model = Model("dishwasher")
    half, front, back, top = 0.3, -0.3, 0.3, 0.85
    body = bmesh.new()
    cuboid(body, -half, half, front + 0.02, back, 0.1, top)
    cuboid(body, -half + 0.01, half - 0.01, front + 0.07, back, 0.0, 0.1)
    model.hard(body, "enamel", bevel=0.006, segments=2)
    door = bmesh.new()
    cuboid(door, -half + 0.004, half - 0.004, front, front + 0.02, 0.105, top - 0.125)
    cuboid(door, -half + 0.004, half - 0.004, front, front + 0.02, top - 0.115, top - 0.004)
    model.hard(door, "enamel", bevel=0.008, segments=2)
    trim = bmesh.new()
    bar_handle(trim, -half + 0.07, half - 0.07, front, top - 0.2, top - 0.18, proud=0.04, thick=0.016)
    model.hard(trim, "steel", bevel=0.005)
    dials = bmesh.new()
    lathe(dials, [(0.0, 0.0), (0.024, 0.0), (0.021, 0.018), (0.0, 0.02)], segments=12, to=along_y(half - 0.08, front, top - 0.06))
    for x in (-0.18, -0.12, -0.06):
        cuboid(dials, x - 0.02, x + 0.02, front - 0.005, front, top - 0.07, top - 0.05)
    model.turned(dials, "plastic", sharp=35.0)
    return model


def chest_freezer():
    """A shop's freezer chest, two squares long, under sliding glass."""
    model = Model("chest_freezer")
    half, front, back, top = 0.9, -0.36, 0.36, 0.86
    body = bmesh.new()
    cuboid(body, -half + 0.02, half - 0.02, front + 0.03, back - 0.03, 0.0, 0.08)
    walls = ((-half, half, front, front + 0.06), (-half, half, back - 0.06, back), (-half, -half + 0.06, front + 0.06, back - 0.06),
             (half - 0.06, half, front + 0.06, back - 0.06))
    for x0, x1, y0, y1 in walls:
        cuboid(body, x0, x1, y0, y1, 0.08, top)
    cuboid(body, -half + 0.06, half - 0.06, front + 0.06, back - 0.06, 0.08, 0.3)
    model.hard(body, "enamel", bevel=0.012, segments=2)
    rim = bmesh.new()
    for x0, x1, y0, y1 in walls:
        cuboid(rim, x0 - 0.008, x1 + 0.008, y0 - 0.008, y1 + 0.008, top, top + 0.025)
    cuboid(rim, -0.02, 0.02, front + 0.06, back - 0.06, top + 0.004, top + 0.025)
    model.hard(rim, "metal", bevel=0.004, segments=1)
    glass = bmesh.new()
    cuboid(glass, -half + 0.05, -0.01, front + 0.05, back - 0.05, top + 0.006, top + 0.012)
    cuboid(glass, 0.01, half - 0.05, front + 0.05, back - 0.05, top + 0.014, top + 0.02)
    model.plain(glass, "screen")
    return model


def register():
    """A till: a sloped bank of keys, a drawer under it and the sum shown on a post."""
    model = Model("register")
    case = bmesh.new()
    cuboid(case, -0.2, 0.2, -0.21, 0.21, 0.0, 0.1)
    prism_y(case, (0.0, 0.125, 0.38, 0.05, -0.2), (0.0, 0.17, 0.38, 0.14, 0.2))
    cuboid(case, 0.03, 0.17, 0.1, 0.2, 0.24, 0.33)
    model.hard(case, "plastic", bevel=0.008, segments=2)
    keys = bmesh.new()
    for n in range(4):
        for m in range(5):
            x, y = -0.15 + m * 0.045, -0.16 + n * 0.055
            z = 0.15 + (y + 0.2) * 0.1125
            cuboid(keys, x, x + 0.032, y, y + 0.036, z, z + 0.012)
    cuboid(keys, -0.19, 0.19, -0.216, -0.21, 0.015, 0.085)
    model.hard(keys, "rubber", bevel=0.003, segments=1)
    shown = bmesh.new()
    cuboid(shown, 0.04, 0.16, 0.094, 0.1, 0.255, 0.315)
    model.plain(shown, "screen")
    return model


def shower(name, tray):
    """A shower on a wall, the wall at +Y: a riser to a rose and two taps, over a tray when it has one."""
    model = Model(name, centred=False)
    back = 0.45
    if tray:
        base = bmesh.new()
        cage = grid_box(base, [-0.45, -0.38, -0.3, 0.3, 0.38, 0.45], [-0.45, -0.38, -0.3, 0.3, 0.38, 0.45], [0.0, 0.12])
        for (i, j, k), vert in cage.items():
            if k == 1 and 2 <= i <= 3 and 2 <= j <= 3:
                vert.co.z = 0.04
        crease(base, cage, 0.8)
        model.soft(base, "ceramic", levels=2)
    pipes = bmesh.new()
    lathe(pipes, [(0.011, 1.0), (0.011, 2.0)], cy=back - 0.03, segments=10, caps=False)
    lathe(pipes, [(0.011, 0.0), (0.011, 0.22), (0.0, 0.223)], segments=10, to=along_y(0.0, back - 0.03, 2.0))
    lathe(pipes, [(0.0, 0.0), (0.012, 0.0), (0.06, 0.05), (0.06, 0.06), (0.0, 0.06)], segments=16,
          to=Matrix.Translation((0.0, back - 0.25, 2.0)) @ Matrix.Rotation(math.radians(205), 4, "X"))
    for x in (-0.09, 0.09):
        lathe(pipes, [(0.03, 0.0), (0.03, 0.012), (0.014, 0.016), (0.014, 0.045), (0.024, 0.05), (0.024, 0.075), (0.0, 0.08)], segments=12,
              to=along_y(x, back, 1.1))
    cuboid(pipes, -0.1, 0.1, back - 0.012, back, 0.99, 1.01)
    model.turned(pipes, "metal", sharp=40.0)
    return model


def kettle_barbecue():
    """A charcoal grill: a bowl and a domed lid on three legs."""
    model = Model("kettle_barbecue")
    legs = bmesh.new()
    for n in range(3):
        angle = math.radians(90 + n * 120)
        x, y = math.cos(angle), math.sin(angle)
        prism(legs, (x * 0.27, y * 0.27, 0.02, 0.02, 0.0), (x * 0.14, y * 0.14, 0.02, 0.02, 0.56))
    lathe(legs, [(0.21, 0.2), (0.21, 0.212)], segments=14, caps=False)
    cuboid(legs, -0.07, 0.07, -0.012, 0.012, 0.99, 1.0)
    for x in (-0.06, 0.06):
        cuboid(legs, x - 0.006, x + 0.006, -0.006, 0.006, 0.93, 0.99)
    model.hard(legs, "metal", bevel=0.003, segments=1)
    bowl = bmesh.new()
    lathe(bowl, [(0.0, 0.5), (0.12, 0.52), (0.22, 0.6), (0.275, 0.7), (0.285, 0.76)], segments=24, caps=False)
    model.turned(bowl, "rubber", sharp=60.0)
    lid = bmesh.new()
    lathe(lid, [(0.292, 0.755), (0.292, 0.775), (0.27, 0.85), (0.2, 0.915), (0.1, 0.945), (0.0, 0.95)], segments=24, caps=False)
    model.turned(lid, "paint", sharp=60.0)
    return model


def gas_barbecue():
    """A gas grill on a cart: a firebox under a hood, a shelf each side and two wheels."""
    model = Model("gas_barbecue")
    half, front, back = 0.34, -0.26, 0.26
    cart = bmesh.new()
    for x in (-1, 1):
        for y in (front + 0.02, back - 0.02):
            cuboid(cart, x * (half - 0.02) - 0.018, x * (half - 0.02) + 0.018, y - 0.018, y + 0.018, 0.05, 0.78)
    cuboid(cart, -half, half, front, back, 0.2, 0.225)
    cuboid(cart, -half, half, front, back, 0.66, 0.86)
    cuboid(cart, -half - 0.02, half + 0.02, front - 0.02, front, 0.7, 0.8)
    model.hard(cart, "rubber", bevel=0.005, segments=1)
    hood = bmesh.new()
    arch = [(front, 0.86), (front, 0.95)] + [(0.26 * -math.cos(math.radians(a)), 0.95 + 0.17 * math.sin(math.radians(a))) for a in range(20, 180, 20)] + [(back, 0.95), (back, 0.86)]
    outline_along_x(hood, arch, -half - 0.01, half + 0.01)
    model.turned(hood, "steel", sharp=40.0)
    trim = bmesh.new()
    bar_handle(trim, -half + 0.08, half - 0.08, front - 0.005, 0.99, 1.008, proud=0.045, thick=0.016)
    for x in (-0.2, 0.0, 0.2):
        lathe(trim, [(0.0, 0.0), (0.022, 0.0), (0.019, 0.02), (0.0, 0.022)], segments=12, to=along_y(x, front - 0.02, 0.75))
    model.turned(trim, "metal", sharp=35.0)
    shelves = bmesh.new()
    for side in (-1, 1):
        for n in range(3):
            y = front + 0.04 + n * 0.16
            low, high = sorted((side * (half + 0.03), side * (half + 0.31)))
            cuboid(shelves, low, high, y, y + 0.13, 0.82, 0.842)
    model.hard(shelves, "wood", bevel=0.004, segments=1)
    wheels = bmesh.new()
    for y in (front + 0.02, back - 0.02):
        lathe(wheels, [(0.0, -0.018), (0.065, -0.018), (0.075, -0.008), (0.075, 0.008), (0.065, 0.018), (0.0, 0.018)], segments=14,
              to=Matrix.Translation((-half + 0.02, y, 0.075)) @ Matrix.Rotation(math.radians(90), 4, "X"))
    model.turned(wheels, "rubber")
    return model


def cooler(name, width, top, doors):
    """A fridge of another size than the kitchen's: one under a counter, or a shop's with two doors."""
    model = Model(name)
    half, front, back = width / 2, -0.3, 0.34
    body = bmesh.new()
    cuboid(body, -half, half, front, back, 0.04, top)
    model.hard(body, "enamel", bevel=0.01, segments=2)
    foot = bmesh.new()
    cuboid(foot, -half + 0.02, half - 0.02, front + 0.01, back - 0.03, 0.0, 0.04)
    model.hard(foot, "rubber", bevel=0.002, segments=1)
    fronts, pulls = bmesh.new(), bmesh.new()
    each = width / doors
    for n in range(doors):
        low, high = -half + n * each + 0.003, -half + (n + 1) * each - 0.003
        cuboid(fronts, low, high, front - 0.04, front - 0.004, 0.05, top - 0.004)
        grip = high - 0.06 if n < doors / 2 else low + 0.04
        bar_handle(pulls, grip, grip + 0.02, front - 0.04, top * 0.55, min(top * 0.55 + 0.34, top - 0.06), proud=0.04, thick=0.016)
    model.hard(fronts, "enamel", bevel=0.014, segments=2)
    model.hard(pulls, "steel", bevel=0.005)
    return model


def post(bm, top, radius=0.05, foot=0.11):
    """A lamp's post from the ground to top: a stepped foot, then a shaft that narrows a little."""
    lathe(bm, [(0.0, 0.0), (foot, 0.0), (foot, 0.06), (foot * 0.72, 0.1), (foot * 0.72, 0.34), (radius * 1.15, 0.4), (radius, 0.46),
               (radius * 0.86, top), (0.0, top)], segments=12)


def street_lamp(name, head):
    """A street lamp that is one sprite: a post about as high as a storey with its light on top.

    The post is paint, which the mod dyes the black or the grey the game's sprite has; what gives the light is shade,
    which the mod has glow after dark.
    """
    model = Model(name)
    shaft = bmesh.new()
    post(shaft, 2.32)
    model.turned(shaft, "paint", sharp=40.0)
    light, casing = bmesh.new(), bmesh.new()
    if head == "cone":
        # A reflector that opens upward from the post to a lid, the light in a band under the lid.
        lathe(casing, [(0.0, 2.3), (0.06, 2.3), (0.075, 2.36), (0.2, 2.56), (0.205, 2.575), (0.0, 2.575)], segments=20)
        lathe(light, [(0.0, 2.575), (0.215, 2.575), (0.235, 2.6), (0.235, 2.66), (0.0, 2.66)], segments=20)
        lathe(casing, [(0.0, 2.66), (0.255, 2.66), (0.26, 2.685), (0.2, 2.73), (0.07, 2.76), (0.0, 2.765)], segments=20)
    elif head == "globe":
        lathe(casing, [(0.0, 2.3), (0.075, 2.3), (0.09, 2.33), (0.085, 2.37), (0.0, 2.37)], segments=16)
        lathe(light, [(0.0, 2.36), (0.07, 2.37), (0.13, 2.42), (0.17, 2.5), (0.18, 2.57), (0.165, 2.65), (0.12, 2.72), (0.06, 2.755), (0.0, 2.765)],
              segments=20)
    else:
        lantern(casing, light, 2.3)
    model.turned(casing, "paint", sharp=40.0)
    model.turned(light, "shade", sharp=50.0)
    return model


def lantern(casing, light, foot, size=1.0):
    """A four-sided lantern standing on foot: glass that widens upward, under a cap with a finial."""
    s = size
    prism(casing, (0.0, 0.0, 0.16 * s, 0.16 * s, foot), (0.0, 0.0, 0.13 * s, 0.13 * s, foot + 0.04 * s))
    prism(light, (0.0, 0.0, 0.13 * s, 0.13 * s, foot + 0.04 * s), (0.0, 0.0, 0.25 * s, 0.25 * s, foot + 0.34 * s))
    prism(casing, (0.0, 0.0, 0.31 * s, 0.31 * s, foot + 0.34 * s), (0.0, 0.0, 0.3 * s, 0.3 * s, foot + 0.365 * s))
    prism(casing, (0.0, 0.0, 0.3 * s, 0.3 * s, foot + 0.365 * s), (0.0, 0.0, 0.07 * s, 0.07 * s, foot + 0.5 * s))
    lathe(casing, [(0.0, foot + 0.5 * s), (0.025 * s, foot + 0.5 * s), (0.035 * s, foot + 0.54 * s), (0.012 * s, foot + 0.58 * s), (0.0, foot + 0.6 * s)],
          segments=8)


def lantern_head():
    """The upper length of a lamp post the game has in two sprites, one a level above the other: the top of the
    post and the lantern on it. It stands on the floor of the upper level, over the post below."""
    model = Model("lantern_head", centred=False)
    shaft = bmesh.new()
    lathe(shaft, [(0.0, 0.0), (0.043, 0.0), (0.04, 0.72), (0.06, 0.76), (0.06, 0.8), (0.0, 0.8)], segments=12)
    model.turned(shaft, "paint", sharp=40.0)
    casing, light = bmesh.new(), bmesh.new()
    lantern(casing, light, 0.8, size=1.25)
    model.turned(casing, "paint", sharp=40.0)
    model.turned(light, "shade", sharp=50.0)
    return model


def cobra_head():
    """The upper length of a road lamp the game has in two sprites: the top of the post, the arm out over the
    road towards the front, and the head at its end with its lens underneath. The origin is the post's axis."""
    model = Model("cobra_head", centred=False)
    shaft = bmesh.new()
    lathe(shaft, [(0.0, 0.0), (0.05, 0.0), (0.043, 2.2), (0.058, 2.22), (0.058, 2.38), (0.0, 2.4)], segments=12)
    model.turned(shaft, "paint", sharp=40.0)
    arm = bmesh.new()
    # A stay from the post up to the arm, as these have, and the arm itself rising a little to the head.
    prism_y(arm, (0.0, 2.33, 0.05, 0.05, -0.5), (0.0, 2.27, 0.06, 0.06, 0.0))
    prism_y(arm, (0.0, 2.31, 0.03, 0.03, -0.3), (0.0, 2.02, 0.03, 0.03, -0.03))
    model.hard(arm, "paint", bevel=0.008, segments=2)
    head = bmesh.new()
    lathe(head, [(0.0, 2.3), (0.13, 2.305), (0.2, 2.33), (0.2, 2.36), (0.15, 2.4), (0.06, 2.42), (0.0, 2.425)], cy=-0.74, segments=18, sx=0.72, sy=1.45)
    model.turned(head, "paint", sharp=40.0)
    lens = bmesh.new()
    lathe(lens, [(0.0, 2.262), (0.08, 2.27), (0.13, 2.295), (0.14, 2.31), (0.0, 2.31)], cy=-0.8, segments=16, sx=0.75, sy=1.3)
    model.turned(lens, "shade", sharp=50.0)
    return model


def wall_bowl():
    """A lamp on a wall that throws its light at the ceiling: a shallow bowl on a short arm, its back at +Y."""
    model = Model("wall_bowl")
    bowl = bmesh.new()
    lathe(bowl, [(0.0, 0.0), (0.07, 0.012), (0.12, 0.05), (0.15, 0.115), (0.14, 0.115), (0.11, 0.06), (0.06, 0.03), (0.0, 0.022)], segments=20)
    model.turned(bowl, "shade", sharp=50.0)
    fixing = bmesh.new()
    cuboid(fixing, -0.035, 0.035, 0.15, 0.165, 0.0, 0.11)
    prism_y(fixing, (0.0, 0.035, 0.03, 0.03, 0.1), (0.0, 0.055, 0.03, 0.03, 0.152))
    model.hard(fixing, "metal", bevel=0.004, segments=1)
    return model


def wall_lantern():
    """A lantern outside a door: a plate on the wall, an arm, and the lantern hung out from it. Its back is at +Y."""
    model = Model("wall_lantern")
    iron = bmesh.new()
    cuboid(iron, -0.04, 0.04, 0.15, 0.165, 0.02, 0.2)
    prism_y(iron, (0.0, 0.03, 0.022, 0.022, 0.0), (0.0, 0.1, 0.022, 0.022, 0.152))
    model.hard(iron, "rubber", bevel=0.004, segments=1)
    casing, light = bmesh.new(), bmesh.new()
    lantern(casing, light, 0.02, size=0.62)
    model.turned(casing, "rubber", sharp=40.0)
    model.turned(light, "shade", sharp=50.0)
    return model


def bulkhead_light():
    """A plain outdoor light flat on a wall: a domed glass in a rim. Its back is at +Y."""
    model = Model("bulkhead_light")
    rim = bmesh.new()
    lathe(rim, [(0.0, 0.0), (0.105, 0.0), (0.105, 0.03), (0.09, 0.036), (0.0, 0.036)], segments=18, sx=0.72, to=along_y(0.0, 0.05, 0.14))
    model.turned(rim, "metal", sharp=40.0)
    glass = bmesh.new()
    lathe(glass, [(0.0, 0.03), (0.088, 0.034), (0.075, 0.07), (0.04, 0.092), (0.0, 0.1)], segments=18, sx=0.72, to=along_y(0.0, 0.05, 0.14))
    model.turned(glass, "shade", sharp=50.0)
    return model


def wall_sink():
    """A basin hung on a wall with no cupboard under it: the bowl, the taps on its back edge and the trap below. Its
    back is at +Y. The rim is 0.44 above the foot of the trap, which is the model's foot."""
    model = Model("wall_sink")
    bowl = bmesh.new()
    lathe(bowl, [(0.0, 0.27), (0.11, 0.28), (0.21, 0.34), (0.25, 0.42), (0.26, 0.44), (0.235, 0.44), (0.2, 0.36), (0.1, 0.315), (0.0, 0.31)],
          segments=22, sy=0.8)
    cuboid(bowl, -0.25, 0.25, 0.14, 0.21, 0.36, 0.47)
    model.turned(bowl, "ceramic", sharp=50.0)
    plumbing = bmesh.new()
    lathe(plumbing, [(0.0, 0.47), (0.018, 0.47), (0.018, 0.56), (0.0, 0.565)], cy=0.175, segments=10)
    prism_y(plumbing, (0.0, 0.535, 0.022, 0.02, 0.05), (0.0, 0.55, 0.022, 0.02, 0.175))
    for side in (-0.09, 0.09):
        lathe(plumbing, [(0.0, 0.47), (0.02, 0.47), (0.024, 0.495), (0.0, 0.5)], cx=side, cy=0.175, segments=8)
    lathe(plumbing, [(0.0, 0.0), (0.022, 0.0), (0.022, 0.275), (0.0, 0.275)], segments=10)
    prism_y(plumbing, (0.0, 0.03, 0.04, 0.04, 0.0), (0.0, 0.03, 0.04, 0.04, 0.21))
    model.turned(plumbing, "steel", sharp=40.0)
    return model


def serving_counter():
    """A length of a canteen's serving counter: a steel body with a pan let into its top, and over it a sheet of
    glass sloping down to the side the food is served to, the front, from a rail on two posts at the back. A metre
    wide, so that the lengths of a run meet."""
    model = Model("serving_counter")
    half, front, back, top = 0.5, -0.31, 0.31, 0.9
    body = bmesh.new()
    cuboid(body, -half, half, front, back, 0.1, top - 0.1)
    # The rim round the pan: four bars, so the pan lies below the counter's top.
    cuboid(body, -half, half, front, front + 0.07, top - 0.1, top)
    cuboid(body, -half, half, back - 0.07, back, top - 0.1, top)
    cuboid(body, -half, -half + 0.07, front + 0.07, back - 0.07, top - 0.1, top)
    cuboid(body, half - 0.07, half, front + 0.07, back - 0.07, top - 0.1, top)
    model.hard(body, "steel", bevel=0.006, segments=2)
    plinth = bmesh.new()
    cuboid(plinth, -half, half, front + 0.04, back, 0.0, 0.1)
    model.hard(plinth, "metal", bevel=0.003, segments=1)
    pan = bmesh.new()
    cuboid(pan, -half + 0.07, half - 0.07, front + 0.07, back - 0.07, top - 0.1, top - 0.085)
    model.plain(pan, "metal")
    rail = bmesh.new()
    for side in (-1, 1):
        cuboid(rail, side * (half - 0.05) - 0.018, side * (half - 0.05) + 0.018, back - 0.06, back - 0.024, top, top + 0.46)
    cuboid(rail, -half, half, back - 0.075, back - 0.01, top + 0.46, top + 0.495)
    model.hard(rail, "steel", bevel=0.004, segments=1)
    glass = bmesh.new()
    prism_y(glass, (0.0, top + 0.1, 2 * half - 0.02, 0.012, front), (0.0, top + 0.47, 2 * half - 0.02, 0.012, back - 0.07))
    model.plain(glass, "pane")
    return model


def main():
    models = [bed("double_bed", 1.80, 2.00, 2), bed("single_bed", 0.90, 1.90, 1), wardrobe(), dining_chair(), small_table(),
              settee("sofa", 1.90, 2), settee("armchair", 1.02, 1), bedside_cabinet(), dresser(), desk(), office_chair(),
              cabinet("counter", 0.62, 0.90, True), cabinet("counter_sink", 0.62, 0.90, True, sink=True),
              cabinet("counter_corner", 0.62, 0.90, True, corner=True), cabinet("wall_cupboard", 0.34, 0.70, False),
              cabinet("wall_cupboard_corner", 0.34, 0.70, False, corner=True), fridge(), oven(), bookcase(), locker(), tv(), tv_console(),
              table_lamp(), toilet(), bath(), washing_machine(), wheelie_bin(), litter_bin(), mailbox(), postbox(), park_bench(),
              picnic_table(), crate(), barrel(),
              framed("frame_tall", 0.6, 0.9), framed("frame_square", 0.7, 0.7), framed("frame_wide", 0.9, 0.6), canvas(), wall_clock(),
              table("table_2", 1.9, 0.92), table("table_3", 2.9, 0.92), table("table_4", 1.9, 1.9, leg=0.08), table("narrow_table", 0.92, 0.5),
              table("low_table", 0.9, 0.5, top=0.42, leg=0.05), table("low_table_2", 1.5, 0.6, top=0.42, leg=0.05), round_table(), office_desk(),
              cabinet("shop_counter", 0.62, 0.90, True, panelled=True), low_shelves(), wall_shelf("wall_shelf", 1), wall_shelf("wall_shelves", 2),
              metal_rack(), store_shelves(), plastic_chair(), folding_chair(), booth(), stool(), vending_machine(), arcade_machine(), dumpster(),
              radio(), microwave(), dishwasher(), chest_freezer(), register(), shower("shower", True), shower("shower_head", False),
              kettle_barbecue(), gas_barbecue(), cooler("mini_fridge", 0.5, 0.82, 1), cooler("large_fridge", 1.7, 1.95, 2),
              street_lamp("cone_lamp", "cone"), street_lamp("globe_lamp", "globe"), street_lamp("lantern_lamp", "lantern"), lantern_head(),
              cobra_head(), wall_bowl(), wall_lantern(), bulkhead_light(), wall_sink(), serving_counter()]
    data = [b"CUFM", struct.pack("<II", 2, len(models))]
    for model in models:
        model.finish()
        data.append(model.packed())
        print("%-20s %5d triangles  x %.3f to %.3f  y %.3f to %.3f  z %.3f to %.3f  shaped %d  sharpest shade over %.3f" % (
            model.name, len(model.vertices) // 3, model.low.x, model.high.x, model.low.y, model.high.y, model.low.z, model.high.z,
            model.shaped, model.longest_shade))
    heavy = [model.name for model in models if len(model.vertices) // 3 > MOST_TRIANGLES]
    if heavy:
        raise SystemExit("more than %d triangles in %s" % (MOST_TRIANGLES, ", ".join(heavy)))
    # The field of a wardrobe's door is a flat board in the open: only its edge is in the shade of the frame round it.
    wardrobe_model = models[2]
    field = wardrobe_model.darkest(lambda position, normal: normal.y < -0.9 and 0.2 < abs(position.x) < 0.7 and 0.85 < position.z < 1.65
                                   and position.y < wardrobe_model.low.y + 0.08)
    print("the field of the wardrobe's door is nowhere darker than %.2f" % field)
    if field < 0.75:
        raise SystemExit("the wardrobe's door is in shade where it lies open to the room")
    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "wb") as raw:
        # No name and no time in the gzip header, so the same models pack to the same bytes.
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as packed:
            packed.write(b"".join(data))
    print("wrote %s, %d bytes" % (os.path.normpath(OUTPUT), os.path.getsize(OUTPUT)))


main()
