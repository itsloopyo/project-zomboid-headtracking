"""Builds the mod's own furniture models and packs them for the jar.

Run headless with Blender 4.4, from the repository root:

    blender --background --factory-startup --python tools/models/furniture.py

It writes src/main/resources/models/furniture.bin.gz and prints each model's
triangle count and bounds. Every model is made here from nothing: no mesh,
texture or measurement is read from the game.

Every model is made twice. The whole one is what is seen from near. The plain
one leaves out what is narrower than PLAIN_DETAIL, and the file says how far
apart the two are at most: the mod casts shadows with the plain one, and draws
it where that distance is under a pixel of the picture.

A model is in metres, Z up, its origin on the floor at the centre of its
footprint, with +Y the head of a bed and the back of everything else, so its
front faces -Y. Each triangle belongs to one surface, which says what it is
made of; the mod gives a surface its colour and lays its grain on by where the
triangle is in the world, so there are no texture coordinates.

The file is gzip over this, all little-endian:

    4 bytes   the ASCII letters CUFM
    uint32    format version, 3
    uint32    number of models
    per model:
      uint8     length of the name, then that many ASCII bytes
      float32   x 6: the least x, y, z and the greatest x, y, z of the whole one
      float32   the furthest a vertex of either one is from the surface of the other
      the whole one, then the plain one, each:
        uint32    number of vertices, fewer than 65536
        uint32    number of triangles
        per vertex, in the order of their surfaces:
          float32 x 3   position
          float32 x 3   unit normal
          uint8         surface: an index into SURFACES below
          uint8         how much of the room's light reaches the vertex past the
                        rest of the model and the floor, 255 for all of it
        per triangle, in the order of their surfaces:
          uint16 x 3    its vertices, counted from the first of this one
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
# What the plain copy of a model leaves out: a bevel narrower than this, as much of the
# roundness of something turned as moves its outline by less than half of it, and a level of
# smoothing of its soft parts where that leaves it no further than this from the whole one.
PLAIN_DETAIL = 0.012
# Set while the plain copies are made, and the second while they are made with their soft parts whole.
plain = False
soft_whole = False


def bevel_steps(width, asked):
    """How many steps a bevel of that width is rounded in. Its faces are shaded as one curve
    however many there are, so more steps only move its outline: by a fifth of its width from
    one step to two, which under 8 mm is less than 2 mm, and by a twentieth from two to three."""
    return min(asked, 1 if width < 0.008 else 2 if width < 0.02 else asked)


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
    if plain:
        # As few sides as keep the widest ring within half of PLAIN_DETAIL of a circle.
        widest = max(radius for radius, _ in profile) * max(sx, sy)
        enough = math.ceil(math.pi / math.acos(max(1 - PLAIN_DETAIL / 2 / widest, 0.0)))
        segments = min(segments, max(6, enough + enough % 2))
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
        if plain and bevel < PLAIN_DETAIL:
            self._take(bm, surface, lambda obj: None, sharp=30.0, inward=inward)
            return
        # The plain copy carries no joint's shade (see finish), so it needs no vertex near each joint.
        while not plain:
            long = [edge for edge in bm.edges if edge.calc_length() > LONGEST_EDGE]
            if not long:
                break
            bmesh.ops.subdivide_edges(bm, edges=long, cuts=1, use_grid_fill=True)

        def modifiers(obj):
            rounded = obj.modifiers.new("bevel", "BEVEL")
            rounded.width = bevel
            rounded.segments = 1 if plain else bevel_steps(bevel, segments)
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
            smoothed.levels = max(levels - 1, 1) if plain and not soft_whole else levels
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

    def finish(self, whole=None):
        """Stands the model on the floor over the middle of its footprint and works out what each vertex can see of the room.

        whole is given for a plain copy: it stands where the whole one does, and is said to be as big.
        """
        low = Vector([min(v[0][axis] for v in self.vertices) for axis in range(3)])
        high = Vector([max(v[0][axis] for v in self.vertices) for axis in range(3)])
        if whole is None:
            self.move = Vector(((low.x + high.x) / 2, (low.y + high.y) / 2, low.z)) if self.centred else Vector((0.0, 0.0, low.z))
            self.low, self.high = low - self.move, high - self.move
        else:
            self.move, self.low, self.high = whole.move, whole.low, whole.high
        move = self.move
        self.vertices = [(position - move, normal, surface) for position, normal, surface in self.vertices]

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

        def alike(position, normal):
            return (round(position.x, 4), round(position.y, 4), round(position.z, 4), round(normal.x, 1), round(normal.y, 1), round(normal.z, 1))

        def reached(position, normal):
            """What share of the room's light reaches a vertex, and whether the vertex is out in the room to be seen."""
            key = alike(position, normal)
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
        for _ in range(REFINEMENTS if whole is None else 0):
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
        if whole is not None:
            # A plain copy's faces are not cut, so it cannot hold a joint's shade as short as the joint. The
            # light is raised at the darker end of every edge it would change sharply along, wherever that
            # vertex is, which leaves such a shade out and keeps the shade of a whole face.
            same = {}
            for i, (position, normal, _) in enumerate(self.vertices):
                same.setdefault(alike(position, normal), []).append(i)
            raised = True
            while raised:
                raised = False
                for i in range(0, len(self.vertices), 3):
                    for a, b in ((0, 1), (1, 2), (2, 0)):
                        dark, bright = sorted((i + a, i + b), key=lambda vertex: self.light[vertex])
                        if self.seen[dark] and self.seen[bright] and self.light[bright] - self.light[dark] > MOST_STEP:
                            for vertex in same[alike(*self.vertices[dark][:2])]:
                                self.light[vertex] = self.light[bright] - MOST_STEP * 0.999
                            raised = True
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

    def apart(self, other):
        """The furthest a vertex of either model is from the surface of the other, both as they were made or both finished."""
        def furthest(one, from_other):
            points = [position for position, _, _ in from_other.vertices]
            tree = BVHTree.FromPolygons(points, [(i, i + 1, i + 2) for i in range(0, len(points), 3)])
            return max(tree.find_nearest(position)[3] for position, _, _ in one.vertices)
        return max(furthest(self, other), furthest(other, self))

    def header(self, apart):
        name = self.name.encode("ascii")
        return struct.pack("<B", len(name)) + name + struct.pack("<7f", *self.low, *self.high, apart)

    def packed(self):
        """The model's vertices, each once however many triangles meet at it, and its triangles as numbers of them."""
        triangles = [[struct.pack("<6fBB", *position, *normal, surface, round(light * 255))
                      for (position, normal, surface), light in zip(self.vertices[i:i + 3], self.light[i:i + 3])]
                     for i in range(0, len(self.vertices), 3)]
        # Both in the order of their surfaces: the mod finds a surface's vertices as one run.
        triangles.sort(key=lambda triangle: triangle[0][-2])
        once = sorted(set(vertex for triangle in triangles for vertex in triangle), key=lambda vertex: (vertex[-2], vertex))
        self.corners = len(once)
        if self.corners >= 65536:
            raise SystemExit("%s has %d vertices, and a triangle names its own in sixteen bits" % (self.name, self.corners))
        number = {vertex: i for i, vertex in enumerate(once)}
        return b"".join([struct.pack("<II", len(once), len(triangles))] + once
                        + [struct.pack("<3H", *(number[vertex] for vertex in triangle)) for triangle in triangles])


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


def cabinet(name, deep, high, base, corner=False, sink=False, panelled=False, made="wood", top_of="worktop"):
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
    model.hard(body, made, bevel=0.004)

    # A corner piece's two faces are plain: the doors of a corner cupboard are on the pieces either side of it.
    if panelled and not corner:
        frame, panel = bmesh.new(), bmesh.new()
        framed_door(frame, panel, -0.498, 0.498, kick + 0.012, top - 0.012, front + lip - 0.02, rail=0.075, thick=0.02)
        model.hard(frame, made, bevel=0.004)
        model.hard(panel, made, bevel=0.003)
    elif not corner:
        frame, panel, pulls = bmesh.new(), bmesh.new(), bmesh.new()
        face = front + lip - 0.02
        for way in (-1, 1):
            low, far = sorted((way * 0.004, way * 0.492))
            framed_door(frame, panel, low, far, kick + 0.012, top - 0.012, face, rail=0.06, thick=0.02, field=False)
            grip = top - 0.14 if base else kick + 0.07
            bar_handle(pulls, way * 0.05 - 0.006, way * 0.05 + 0.006, face, grip, grip + 0.11, proud=0.028, thick=0.01)
        model.hard(frame, made, bevel=0.004)
        model.hard(panel, made, bevel=0.003)
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
        model.hard(tops, top_of, bevel=0.005, segments=2)
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


def television(model, low, width, high, deep, glass, made_of):
    """A television set with a picture tube, its foot at height low. glass is how wide and how high the tube's face
    is: a set is little bigger than its tube, so the glass is most of its front."""
    half, front, back = width / 2, -deep / 2, deep / 2
    top = low + high
    wide, tall = glass
    glass_low = top - 0.04 - tall
    case = bmesh.new()
    cuboid(case, -half, half, front, front + 0.14, low + 0.03, top)
    prism_y(case, (0.0, low + 0.03 + (high - 0.03) / 2, width - 0.03, high - 0.05, front + 0.14), (0.0, low + 0.03 + high * 0.45, width * 0.55, high * 0.6, back))
    cuboid(case, -half * 0.6, half * 0.6, front + 0.03, back - 0.1, low, low + 0.03)
    model.hard(case, made_of, bevel=0.012, segments=2)

    tube = bmesh.new()
    cuboid(tube, -wide / 2, wide / 2, front - 0.006, front + 0.002, glass_low, glass_low + tall)
    model.hard(tube, "screen", bevel=0.012, segments=2)

    dials = bmesh.new()
    middle = (low + 0.03 + glass_low) / 2
    for n in range(4):
        x = wide / 2 - 0.02 - n * 0.05
        cuboid(dials, x - 0.015, x + 0.015, front - 0.005, front, middle - 0.01, middle + 0.01)
    model.hard(dials, "rubber", bevel=0.002, segments=1)


def tv():
    """A set of twenty-odd inches in a black case, its tube three high for four wide."""
    model = Model("tv")
    television(model, 0.0, 0.56, 0.48, 0.46, (0.44, 0.33), "plastic")
    return model


def tv_wide():
    """A big set in a silver case, its tube nine high for sixteen wide."""
    model = Model("tv_wide")
    television(model, 0.0, 0.84, 0.56, 0.5, (0.72, 0.405), "steel")
    return model


def tv_console():
    """A set in a wooden cabinet that stands on the floor on its own legs."""
    model = Model("tv_console")
    half, front, back, low, top = 0.42, -0.22, 0.22, 0.2, 0.78
    # The tube's face, three high for four wide.
    left, right, foot, head = -half + 0.075, 0.135, low + 0.115, low + 0.475

    body = bmesh.new()
    cuboid(body, -half, half, front, back, low, top)
    for x in (-1, 1):
        for y in (front + 0.05, back - 0.05):
            prism(body, (x * (half - 0.06), y, 0.03, 0.03, 0.0), (x * (half - 0.05), y, 0.05, 0.05, low))
    model.hard(body, "wood", bevel=0.008)

    trim = bmesh.new()
    cuboid(trim, left - 0.035, right + 0.035, front - 0.012, front, foot - 0.035, head + 0.035)
    for n in range(7):
        cuboid(trim, 0.225, half - 0.05, front - 0.008, front, low + 0.07 + n * 0.028, low + 0.082 + n * 0.028)
    model.hard(trim, "rubber", bevel=0.004, segments=1)

    tube = bmesh.new()
    cuboid(tube, left, right, front - 0.018, front - 0.01, foot, head)
    model.hard(tube, "screen", bevel=0.02, segments=3)

    dials = bmesh.new()
    for z in (top - 0.1, top - 0.19):
        lathe(dials, [(0.0, 0.0), (0.026, 0.0), (0.022, 0.022), (0.0, 0.025)], segments=12, to=along_y(0.295, front, z))
    lathe(dials, [(0.0, top), (0.05, top), (0.04, top + 0.025), (0.0, top + 0.03)], segments=12)
    for side in (-1, 1):
        prism(dials, (side * 0.01, 0.0, 0.007, 0.007, top + 0.02), (side * 0.2, 0.02, 0.004, 0.004, top + 0.42))
    model.turned(dials, "metal", sharp=35.0)
    return model


def lit_screen():
    """The picture a set shows when it is on: a sheet a metre wide and a metre high with rounded corners, which the
    mod stands just proud of a set's glass, as wide and as high as the glass is."""
    model = Model("lit_screen")
    sheet = bmesh.new()
    round_, steps, outline = 0.06, 5, []
    for cx, cz, start in ((0.5 - round_, round_, -90), (0.5 - round_, 1.0 - round_, 0), (-0.5 + round_, 1.0 - round_, 90), (-0.5 + round_, round_, 180)):
        for i in range(steps + 1):
            turn = math.radians(start + 90 * i / steps)
            outline.append((cx + round_ * math.cos(turn), cz + round_ * math.sin(turn)))
    outline_along_y(sheet, outline, -0.002, 0.002)
    model.plain(sheet, "screen")
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


def wall_shelf(name, boards, made="wood"):
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
    model.hard(planks, made, bevel=0.005, segments=2)
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


def air_conditioner():
    """An air conditioner hung through an outside wall: the steel case that stands out of the wall on two brackets, a
    grille of slats across its face, slots down both sides and a lid that overhangs. Its back is at +Y."""
    model = Model("air_conditioner")
    half, front, back, low, top = 0.29, -0.2, 0.2, 0.05, 0.4

    case = bmesh.new()
    cuboid(case, -half, half, front, back, low, top)
    cuboid(case, -half - 0.012, half + 0.012, front - 0.014, back, top, top + 0.016)
    # The frame the grille sits in.
    for z0, z1 in ((low + 0.025, low + 0.05), (top - 0.05, top - 0.025)):
        cuboid(case, -half + 0.03, half - 0.03, front - 0.012, front, z0, z1)
    for x0, x1 in ((-half + 0.03, -half + 0.055), (half - 0.055, half - 0.03)):
        cuboid(case, x0, x1, front - 0.012, front, low + 0.05, top - 0.05)
    model.hard(case, "enamel", bevel=0.006, segments=2)

    dark = bmesh.new()
    cuboid(dark, -half + 0.055, half - 0.055, front - 0.002, front, low + 0.05, top - 0.05)
    for side in (-1, 1):
        for slot in range(6):
            z = low + 0.07 + slot * 0.04
            cuboid(dark, side * half - 0.002, side * half + 0.002, front + 0.07, back - 0.09, z, z + 0.016)
    model.plain(dark, "rubber")

    slats = bmesh.new()
    for slat in range(8):
        z = low + 0.062 + slat * 0.0305
        # Each slat leans, its lower edge out, to shed the rain.
        prism_y(slats, (0.0, z + 0.005, 2 * half - 0.11, 0.004, front - 0.012), (0.0, z + 0.019, 2 * half - 0.11, 0.004, front - 0.001))
    model.hard(slats, "metal", bevel=0.002, segments=1)

    brackets = bmesh.new()
    for x in (-half + 0.06, half - 0.09):
        cuboid(brackets, x, x + 0.03, front + 0.03, back, low - 0.012, low)
        cuboid(brackets, x, x + 0.03, back - 0.012, back, 0.0, low - 0.012)
        prism_y(brackets, (x + 0.015, low - 0.02, 0.012, 0.006, front + 0.06), (x + 0.015, 0.012, 0.012, 0.006, back - 0.012))
    model.hard(brackets, "steel", bevel=0.002, segments=1)
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


def shell(bm, outer, inner):
    """A sheet with a thickness: two grids of points of the same shape, one for each side of it, closed round their edges."""
    rows, cols = len(outer), len(outer[0])
    sides = [[[bm.verts.new(point) for point in row] for row in grid] for grid in (outer, inner)]
    for i in range(rows - 1):
        for j in range(cols - 1):
            for side in sides:
                bm.faces.new((side[i][j], side[i][j + 1], side[i + 1][j + 1], side[i + 1][j]))
    one, other = sides
    for i in range(rows - 1):
        for j in (0, cols - 1):
            bm.faces.new((one[i][j], one[i + 1][j], other[i + 1][j], other[i][j]))
    for j in range(cols - 1):
        for i in (0, rows - 1):
            bm.faces.new((one[i][j], one[i][j + 1], other[i][j + 1], other[i][j]))


# A parasol's cloth: how thick it is, how many ribs hold it, and how many stripes lie between two ribs.
PARASOL_CLOTH = 0.006
PARASOL_RIBS = 8
PARASOL_STRIPES = 4


def parasol(name, profile, drop, scallop):
    """A cafe parasol: a pole on a weighted foot, ribs and their stretchers, and a canopy of flat panels between the
    ribs with a scalloped valance hung from its edge.

    profile is the canopy's outline along a rib as (radius, height) from the hub out to the edge. The cloth is in
    stripes that go by turns to two surfaces, so that the mod can give it one colour or two. drop is how far the
    valance hangs at the ends of a scallop, and scallop how much further in the middle of one.
    """
    model = Model(name)
    top, edge = profile[0][1], profile[-1][1]
    reach = profile[-1][0]
    ways = [(math.cos(2 * math.pi * k / PARASOL_RIBS), math.sin(2 * math.pi * k / PARASOL_RIBS)) for k in range(PARASOL_RIBS)]

    def on(k, across, radius, z):
        """A point of the panel after rib k: across is 0 at that rib and 1 at the next."""
        (x0, y0), (x1, y1) = ways[k], ways[(k + 1) % PARASOL_RIBS]
        return (radius * (x0 + (x1 - x0) * across), radius * (y0 + (y1 - y0) * across), z)

    cloths = [bmesh.new(), bmesh.new()]
    cuts = 4
    for k in range(PARASOL_RIBS):
        for stripe in range(PARASOL_STRIPES):
            cloth = cloths[stripe % 2]
            a, b = stripe / PARASOL_STRIPES, (stripe + 1) / PARASOL_STRIPES
            shell(cloth, [[on(k, a, radius, z), on(k, b, radius, z)] for radius, z in profile],
                  [[on(k, a, radius, z - PARASOL_CLOTH), on(k, b, radius, z - PARASOL_CLOTH)] for radius, z in profile])
            hung, behind = [[], []], [[], []]
            for cut in range(cuts + 1):
                across = a + (b - a) * cut / cuts
                low = edge - drop - scallop * math.sin(math.pi * cut / cuts)
                hung[0].append(on(k, across, reach, edge))
                hung[1].append(on(k, across, reach, low))
                behind[0].append(on(k, across, reach - PARASOL_CLOTH, edge))
                behind[1].append(on(k, across, reach - PARASOL_CLOTH, low))
            shell(cloth, hung, behind)
    model.plain(cloths[0], "cloth")
    model.plain(cloths[1], "linen")

    frame = bmesh.new()
    lathe(frame, [(0.26, 0.0), (0.26, 0.045), (0.24, 0.06), (0.06, 0.075), (0.045, 0.2), (0.022, 0.2), (0.022, top + 0.03), (0.03, top + 0.04),
                  (0.03, top + 0.07), (0.0, top + 0.1)], segments=16)
    runner = edge - 0.12
    lathe(frame, [(0.04, runner - 0.04), (0.04, runner + 0.04)], segments=12)
    lathe(frame, [(0.045, top - 0.06), (0.045, profile[0][1] - PARASOL_CLOTH)], segments=12)
    model.turned(frame, "metal")
    ribs = bmesh.new()
    for k, (x, y) in enumerate(ways):
        for (r0, z0), (r1, z1) in zip(profile, profile[1:]):
            prism(ribs, (x * r0, y * r0, 0.014, 0.014, z0 - PARASOL_CLOTH - 0.016), (x * (r1 - 0.01), y * (r1 - 0.01), 0.014, 0.014, z1 - PARASOL_CLOTH - 0.016))
        # The stretcher from the runner on the pole to the middle of the rib.
        middle = len(profile) // 2
        prism(ribs, (x * 0.04, y * 0.04, 0.012, 0.012, runner), (x * profile[middle][0], y * profile[middle][0], 0.012, 0.012, profile[middle][1] - PARASOL_CLOTH - 0.03))
    model.plain(ribs, "steel")
    return model


def range_hood():
    """A canopy hood over a cooker: a steel lip, a canopy whose front slopes back to a duct, and the duct going up
    to the ceiling. Its back, flat, is at +Y, and its foot is the lower edge of the lip: the mod hangs it over a hob."""
    model = Model("range_hood")
    half, front, back = 0.44, -0.3, 0.3
    canopy = bmesh.new()
    cuboid(canopy, -half, half, front, back, 0.0, 0.07)
    prism(canopy, (0.0, 0.0, 2 * half, back - front, 0.07), (0.0, back - 0.15, 2 * half - 0.2, 0.3, 0.36))
    model.hard(canopy, "steel", bevel=0.006, segments=2)
    duct = bmesh.new()
    cuboid(duct, -0.16, 0.16, back - 0.27, back - 0.03, 0.36, 0.86)
    model.hard(duct, "steel", bevel=0.004, segments=1)
    filters = bmesh.new()
    for side in (-1, 1):
        cuboid(filters, side * 0.215 - 0.19, side * 0.215 + 0.19, front + 0.06, back - 0.06, -0.006, 0.0)
    model.plain(filters, "rubber")
    return model


def ruffled(bm, rng, about, by):
    """Moves every vertex of a part in or out from the upright through about by up to by, for something of no
    regular shape: a bag's folds, a heap."""
    for vert in bm.verts:
        away = Vector((vert.co.x - about[0], vert.co.y - about[1], 0.0))
        if away.length > 1e-6:
            vert.co += away.normalized() * rng.uniform(-by, by)


def garbage_can():
    """A round can with a bin bag in it: the bag's top turned down over the rim in folds, and what has been thrown
    away heaped to the brim."""
    model = Model("garbage_can")
    rng = random.Random(17)
    can = bmesh.new()
    profile = [(0.0, 0.0), (0.225, 0.0), (0.232, 0.012)]
    for z in (0.2, 0.42):
        profile += [(0.232 + 0.03 * z / 0.7, z - 0.012), (0.24 + 0.03 * z / 0.7, z), (0.232 + 0.03 * z / 0.7, z + 0.012)]
    profile += [(0.262, 0.7), (0.0, 0.7)]
    lathe(can, profile, segments=24)
    model.turned(can, "paint", sharp=35.0)
    bag = bmesh.new()
    lathe(bag, [(0.2, 0.655), (0.25, 0.722), (0.274, 0.71), (0.283, 0.62), (0.28, 0.53), (0.268, 0.53), (0.266, 0.69), (0.24, 0.7), (0.2, 0.64)],
          segments=24, closed=True)
    ruffled(bag, rng, (0.0, 0.0), 0.008)
    for vert in bag.verts:
        if vert.co.z < 0.56:
            vert.co.z += rng.uniform(-0.035, 0.03)
    model.turned(bag, "rubber", sharp=70.0)
    heap = bmesh.new()
    lathe(heap, [(0.0, 0.6), (0.205, 0.6), (0.2, 0.655), (0.13, 0.705), (0.06, 0.725), (0.0, 0.73)], segments=14)
    for vert in heap.verts:
        if vert.co.z > 0.66:
            vert.co.z += rng.uniform(-0.02, 0.03)
    model.turned(heap, "rubber", sharp=25.0)
    for made, lumps in (("shade", ((0.07, 0.03, 0.735, 0.06), (-0.09, -0.06, 0.715, 0.05))), ("linen", ((-0.03, 0.1, 0.725, 0.055),)),
                        ("metal", ((0.02, -0.1, 0.72, 0.045),))):
        lump = bmesh.new()
        for x, y, z, size in lumps:
            bmesh.ops.create_icosphere(lump, subdivisions=1, radius=size, matrix=Matrix.Translation((x, y, z)) @ Matrix.Diagonal((1.0, 0.8, 0.6, 1.0)))
        for vert in lump.verts:
            vert.co += Vector((rng.uniform(-0.012, 0.012), rng.uniform(-0.012, 0.012), rng.uniform(-0.008, 0.008)))
        model.turned(lump, made, sharp=20.0)
    return model


def waste_basket():
    """A small bin for under a desk: oval, wider at its rim, open."""
    model = Model("waste_basket")
    bin_ = bmesh.new()
    lathe(bin_, [(0.0, 0.0), (0.125, 0.0), (0.13, 0.01), (0.165, 0.35), (0.168, 0.36), (0.156, 0.36), (0.122, 0.022), (0.0, 0.022)], segments=20, sy=0.78)
    model.turned(bin_, "plastic", sharp=40.0)
    return model


def louvre(name, width, height, made, slats, deep=0.045):
    """A grille on a wall, its back at +Y: a frame, slats across it that lean with their lower edge out, and the dark behind them."""
    model = Model(name)
    half, front, back, rail = width / 2, -deep, 0.0, 0.035
    frame = bmesh.new()
    cuboid(frame, -half, half, front, back, 0.0, rail)
    cuboid(frame, -half, half, front, back, height - rail, height)
    cuboid(frame, -half, -half + rail, front, back, rail, height - rail)
    cuboid(frame, half - rail, half, front, back, rail, height - rail)
    model.hard(frame, made, bevel=0.005, segments=2)
    dark = bmesh.new()
    cuboid(dark, -half + rail, half - rail, back - 0.006, back, rail, height - rail)
    model.plain(dark, "rubber")
    blades = bmesh.new()
    step = (height - 2 * rail) / slats
    for n in range(slats):
        z = rail + n * step
        prism_y(blades, (0.0, z + 0.2 * step, width - 2 * rail, 0.005, front + 0.004), (0.0, z + 0.85 * step, width - 2 * rail, 0.005, back - 0.008))
    model.hard(blades, made, bevel=0.002, segments=1)
    return model


def round_vent():
    """A round vent on a wall, its back at +Y: a domed face with slots across it."""
    model = Model("round_vent")
    face = bmesh.new()
    lathe(face, [(0.0, 0.0), (0.17, 0.0), (0.17, 0.02), (0.14, 0.034), (0.0, 0.04)], segments=20, to=along_y(0.0, 0.0, 0.17))
    model.turned(face, "enamel", sharp=40.0)
    slots = bmesh.new()
    for n in range(5):
        z = 0.17 + (n - 2) * 0.052
        reach = math.sqrt(max(0.125 ** 2 - (z - 0.17) ** 2, 0.0))
        cuboid(slots, -reach, reach, -0.043, -0.03, z - 0.011, z + 0.011)
    model.plain(slots, "rubber")
    return model


def exit_sign():
    """The sign over a way out, its back at +Y: a white case with the word in red letters standing a little out of its face."""
    model = Model("exit_sign")
    case = bmesh.new()
    cuboid(case, -0.19, 0.19, -0.05, 0.0, 0.0, 0.2)
    model.hard(case, "enamel", bevel=0.006, segments=2)
    word = bmesh.new()
    face, low, high, bar = -0.05, 0.045, 0.155, 0.02

    def stroke(x0, x1, z0, z1):
        cuboid(word, x0, x1, face - 0.004, face, z0, z1)

    # Whoever reads the sign looks along +Y, and has the model's -X on their left.
    stroke(-0.15, -0.13, low, high)
    for z in (low, (low + high) / 2 - bar / 2, high - bar):
        stroke(-0.13, -0.085, z, z + bar)
    for lean in (-1, 1):
        prism(word, (-0.035 - lean * 0.026, face - 0.002, bar, 0.004, low), (-0.035 + lean * 0.026, face - 0.002, bar, 0.004, high))
    stroke(0.03, 0.05, low, high)
    stroke(0.08, 0.15, high - bar, high)
    stroke(0.105, 0.125, low, high - bar)
    model.plain(word, "vinyl")
    return model


def sconce():
    """A lamp on a wall, the wall at +Y: a brass plate, an arm that comes out and turns up, and a shade on it."""
    model = Model("sconce")
    brass = bmesh.new()
    cuboid(brass, -0.05, 0.05, 0.085, 0.1, 0.0, 0.16)
    prism_y(brass, (0.0, 0.05, 0.02, 0.02, -0.03), (0.0, 0.07, 0.02, 0.02, 0.085))
    lathe(brass, [(0.0, 0.04), (0.012, 0.04), (0.012, 0.14), (0.022, 0.15), (0.0, 0.155)], cy=-0.03, segments=10)
    model.hard(brass, "metal", bevel=0.003, segments=1)
    shade = bmesh.new()
    lathe(shade, [(0.1, 0.13), (0.104, 0.13), (0.064, 0.3), (0.06, 0.3)], cy=-0.03, segments=18, closed=True)
    bmesh.ops.create_uvsphere(shade, u_segments=8, v_segments=5, radius=0.028, matrix=Matrix.Translation((0.0, -0.03, 0.19)))
    model.turned(shade, "shade", sharp=60.0)
    return model


def pedestal_sink():
    """A basin on a column, the wall at +Y. Its rim is 0.87 above the floor."""
    model = Model("pedestal_sink")
    lift = 0.43
    bowl = bmesh.new()
    lathe(bowl, [(0.0, 0.27 + lift), (0.11, 0.28 + lift), (0.21, 0.34 + lift), (0.25, 0.42 + lift), (0.26, 0.44 + lift), (0.235, 0.44 + lift),
                 (0.2, 0.36 + lift), (0.1, 0.315 + lift), (0.0, 0.31 + lift)], segments=22, sy=0.8)
    cuboid(bowl, -0.25, 0.25, 0.14, 0.21, 0.36 + lift, 0.47 + lift)
    lathe(bowl, [(0.0, 0.0), (0.12, 0.0), (0.115, 0.03), (0.075, 0.09), (0.062, 0.45), (0.09, 0.66), (0.11, 0.72), (0.0, 0.72)], cy=0.04, segments=16, sy=0.85)
    model.turned(bowl, "ceramic", sharp=50.0)
    taps = bmesh.new()
    lathe(taps, [(0.0, 0.47 + lift), (0.018, 0.47 + lift), (0.018, 0.56 + lift), (0.0, 0.565 + lift)], cy=0.175, segments=10)
    prism_y(taps, (0.0, 0.535 + lift, 0.022, 0.02, 0.05), (0.0, 0.55 + lift, 0.022, 0.02, 0.175))
    for side in (-0.09, 0.09):
        lathe(taps, [(0.0, 0.47 + lift), (0.02, 0.47 + lift), (0.024, 0.495 + lift), (0.0, 0.5 + lift)], cx=side, cy=0.175, segments=8)
    model.turned(taps, "steel", sharp=40.0)
    return model


def medicine_cabinet():
    """A cupboard on a bathroom wall with a mirror for its door, the wall at +Y."""
    model = Model("medicine_cabinet")
    half, front, back, high = 0.23, -0.14, 0.0, 0.58
    body = bmesh.new()
    cuboid(body, -half, half, front + 0.02, back, 0.0, high)
    cuboid(body, -half - 0.012, half + 0.012, front, back, high, high + 0.02)
    model.hard(body, "enamel", bevel=0.005, segments=2)
    frame = bmesh.new()
    rail, face = 0.035, front
    cuboid(frame, -half + 0.004, half - 0.004, face, face + 0.02, 0.006, 0.006 + rail)
    cuboid(frame, -half + 0.004, half - 0.004, face, face + 0.02, high - 0.006 - rail, high - 0.006)
    cuboid(frame, -half + 0.004, -half + 0.004 + rail, face, face + 0.02, 0.006 + rail, high - 0.006 - rail)
    cuboid(frame, half - 0.004 - rail, half - 0.004, face, face + 0.02, 0.006 + rail, high - 0.006 - rail)
    model.hard(frame, "enamel", bevel=0.004, segments=1)
    glass = bmesh.new()
    cuboid(glass, -half + rail, half - rail, face + 0.008, face + 0.02, rail, high - rail)
    model.plain(glass, "pane")
    pull = bmesh.new()
    knob(pull, half - 0.022, face, 0.1, 0.8)
    model.turned(pull, "metal")
    return model


def hand_dryer():
    """A hot air hand dryer on a wall, the wall at +Y: a rounded steel case, the spout under it and its button."""
    model = Model("hand_dryer")
    case = bmesh.new()
    cuboid(case, -0.13, 0.13, -0.17, 0.0, 0.05, 0.3)
    model.hard(case, "steel", bevel=0.035, segments=3)
    spout = bmesh.new()
    lathe(spout, [(0.0, 0.0), (0.035, 0.0), (0.04, 0.012), (0.04, 0.06), (0.0, 0.06)], cy=-0.1, segments=12)
    lathe(spout, [(0.0, 0.0), (0.026, 0.0), (0.024, 0.012), (0.0, 0.014)], segments=12, to=along_y(0.0, -0.17, 0.2))
    model.turned(spout, "metal", sharp=40.0)
    return model


def coat_rack():
    """A shelf on a wall with a row of hooks under it, the wall at +Y."""
    model = Model("coat_rack")
    half = 0.46
    boards = bmesh.new()
    cuboid(boards, -half, half, -0.2, 0.0, 0.2, 0.225)
    cuboid(boards, -half, half, -0.02, 0.0, 0.07, 0.2)
    for side in (-1, 1):
        outline_along_x(boards, [(-0.18, 0.2), (0.0, 0.2), (0.0, 0.05), (-0.03, 0.05)], side * (half - 0.05) - 0.012, side * (half - 0.05) + 0.012)
    model.hard(boards, "wood", bevel=0.004, segments=1)
    hooks = bmesh.new()
    for n in range(4):
        x = (n - 1.5) * 0.22
        prism_y(hooks, (x, 0.13, 0.012, 0.012, -0.075), (x, 0.13, 0.012, 0.012, -0.02))
        cuboid(hooks, x - 0.006, x + 0.006, -0.087, -0.075, 0.124, 0.17)
        prism_y(hooks, (x, 0.04, 0.012, 0.012, -0.09), (x, 0.1, 0.012, 0.012, -0.02))
        cuboid(hooks, x - 0.006, x + 0.006, -0.102, -0.09, 0.034, 0.08)
    model.hard(hooks, "metal", bevel=0.003, segments=1)
    return model


def filing_cabinet():
    """A steel filing cabinet of three drawers, each with a pull and a holder for its label."""
    model = Model("filing_cabinet")
    half, front, back, top = 0.235, -0.3, 0.3, 1.02
    body = bmesh.new()
    cuboid(body, -half, half, front, back, 0.0, top)
    model.hard(body, "paint", bevel=0.006, segments=2)
    fronts, trim = bmesh.new(), bmesh.new()
    high = (top - 0.05) / 3
    for n in range(3):
        low = 0.03 + n * high
        cuboid(fronts, -half + 0.012, half - 0.012, front - 0.016, front, low, low + high - 0.012)
        bar_handle(trim, -0.07, 0.07, front - 0.016, low + high * 0.62, low + high * 0.62 + 0.016, proud=0.026, thick=0.01)
        cuboid(trim, -0.045, 0.045, front - 0.02, front - 0.016, low + high * 0.3, low + high * 0.3 + 0.035)
    model.hard(fronts, "paint", bevel=0.005, segments=2)
    model.hard(trim, "metal", bevel=0.003, segments=1)
    return model


def carton(name, width, depth, high):
    """A cardboard box, shut: its four flaps meet along the middle of its top under a strip of tape that runs a hand down each end."""
    model = Model(name)
    hx, hy = width / 2, depth / 2
    box = bmesh.new()
    cuboid(box, -hx, hx, -hy, hy, 0.0, high - 0.006)
    for side in (-1, 1):
        low, far = sorted((side * 0.003, side * hy))
        cuboid(box, -hx, hx, low, far, high - 0.006, high)
    model.hard(box, "paint", bevel=0.004, segments=1)
    tape = bmesh.new()
    cuboid(tape, -hx - 0.002, hx + 0.002, -0.03, 0.03, high - 0.0005, high + 0.002)
    for side in (-1, 1):
        low, far = sorted((side * hx, side * (hx + 0.002)))
        cuboid(tape, low, far, -0.03, 0.03, high - 0.11, high + 0.002)
    model.plain(tape, "shade")
    return model


def computer():
    """A desktop computer of the nineties: the case lying flat, the tube monitor on it, the keyboard before them."""
    model = Model("computer")
    case = bmesh.new()
    cuboid(case, -0.21, 0.21, -0.12, 0.28, 0.0, 0.105)
    model.hard(case, "shade", bevel=0.006, segments=2)
    slots = bmesh.new()
    cuboid(slots, 0.04, 0.17, -0.123, -0.12, 0.06, 0.07)
    cuboid(slots, 0.04, 0.17, -0.123, -0.12, 0.032, 0.042)
    model.plain(slots, "plastic")
    tube = bmesh.new()
    lathe(tube, [(0.0, 0.105), (0.11, 0.105), (0.12, 0.115), (0.07, 0.14), (0.0, 0.14)], cy=0.06, segments=14)
    cuboid(tube, -0.185, 0.185, -0.1, 0.02, 0.14, 0.46)
    prism_y(tube, (0.0, 0.3, 0.36, 0.31, 0.02), (0.0, 0.29, 0.22, 0.2, 0.27))
    model.hard(tube, "shade", bevel=0.012, segments=2)
    glass = bmesh.new()
    cuboid(glass, -0.15, 0.15, -0.104, -0.1, 0.185, 0.425)
    model.hard(glass, "screen", bevel=0.01, segments=2)
    keys = bmesh.new()
    prism(keys, (0.0, -0.3, 0.44, 0.16, 0.0), (0.0, -0.295, 0.44, 0.15, 0.022))
    model.hard(keys, "shade", bevel=0.004, segments=1)
    caps = bmesh.new()
    cuboid(caps, -0.2, 0.2, -0.36, -0.235, 0.022, 0.027)
    model.plain(caps, "linen")
    return model


def water_cooler():
    """A water dispenser: a cabinet with two taps over a drip tray, and the bottle upside down on top."""
    model = Model("water_cooler")
    body = bmesh.new()
    cuboid(body, -0.16, 0.16, -0.16, 0.16, 0.0, 0.98)
    model.hard(body, "shade", bevel=0.012, segments=2)
    trim = bmesh.new()
    cuboid(trim, -0.1, 0.1, -0.2, -0.16, 0.6, 0.615)
    for x in (-0.045, 0.045):
        cuboid(trim, x - 0.012, x + 0.012, -0.19, -0.16, 0.76, 0.8)
    model.hard(trim, "metal", bevel=0.003, segments=1)
    recess = bmesh.new()
    cuboid(recess, -0.11, 0.11, -0.162, -0.16, 0.62, 0.84)
    model.plain(recess, "plastic")
    bottle = bmesh.new()
    profile = [(0.0, 0.98), (0.04, 0.98), (0.045, 1.03), (0.12, 1.09)]
    for z in (1.16, 1.25):
        profile += [(0.135, z - 0.035), (0.125, z), (0.135, z + 0.035)]
    profile += [(0.135, 1.34), (0.11, 1.38), (0.0, 1.385)]
    lathe(bottle, profile, segments=18)
    model.turned(bottle, "paint", sharp=50.0)
    return model


def floor_lamp(name, shaded):
    """A lamp that stands on the floor: a weighted foot and a thin post, with a shade on top, or the dish of an
    uplighter. The post is paint, which the mod dyes."""
    model = Model(name)
    top = 1.5 if shaded else 1.72
    post_ = bmesh.new()
    lathe(post_, [(0.0, 0.0), (0.14, 0.0), (0.14, 0.018), (0.05, 0.035), (0.014, 0.05), (0.014, top), (0.0, top)], segments=16)
    model.turned(post_, "paint", sharp=40.0)
    light = bmesh.new()
    if shaded:
        lathe(light, [(0.17, 1.28), (0.174, 1.28), (0.114, 1.62), (0.11, 1.62)], segments=20, closed=True)
        bmesh.ops.create_uvsphere(light, u_segments=8, v_segments=5, radius=0.035, matrix=Matrix.Translation((0.0, 0.0, 1.45)))
    else:
        lathe(light, [(0.0, 1.7), (0.03, 1.7), (0.12, 1.75), (0.17, 1.8), (0.16, 1.805), (0.1, 1.77), (0.0, 1.75)], segments=20)
    model.turned(light, "shade", sharp=50.0)
    return model


def dresser_mirror():
    """A chest of drawers with a looking glass standing on its top between two posts, the wall at +Y. The chest's top is 0.88 high."""
    model = Model("dresser_mirror")
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
    frame = bmesh.new()
    wide, low, high, rail, face = 0.31, top + 0.05, top + 0.86, 0.05, back - 0.07
    for side in (-1, 1):
        cuboid(frame, side * (wide + 0.03) - 0.02, side * (wide + 0.03) + 0.02, face, face + 0.04, top, top + 0.5)
    cuboid(frame, -wide, wide, face, face + 0.032, low, low + rail)
    cuboid(frame, -wide, -wide + rail, face, face + 0.032, low + rail, high - rail)
    cuboid(frame, wide - rail, wide, face, face + 0.032, low + rail, high - rail)
    outline_along_y(frame, [(-wide, high - rail), (wide, high - rail), (wide, high), (wide * 0.6, high + 0.07), (0.0, high + 0.095), (-wide * 0.6, high + 0.07),
                            (-wide, high)], face, face + 0.032)
    model.hard(frame, "wood", bevel=0.006, segments=2)
    glass = bmesh.new()
    cuboid(glass, -wide + rail - 0.004, wide - rail + 0.004, face + 0.011, face + 0.032, low + rail - 0.004, high - rail + 0.004)
    model.plain(glass, "pane")
    return model


def side_table():
    """A small table for the end of a sofa, with one drawer under its top."""
    model = Model("side_table")
    half, top = 0.25, 0.5
    under = bmesh.new()
    for x in (-1, 1):
        for y in (-1, 1):
            prism(under, (x * (half - 0.035), y * (half - 0.035), 0.03, 0.03, 0.0), (x * (half - 0.035), y * (half - 0.035), 0.045, 0.045, top - 0.025))
    cuboid(under, -half + 0.04, half - 0.04, -half + 0.03, half - 0.03, top - 0.14, top - 0.025)
    model.hard(under, "wood", bevel=0.004, segments=1)
    board = bmesh.new()
    cuboid(board, -half, half, -half, half, top - 0.025, top)
    model.hard(board, "wood", bevel=0.009, segments=3)
    fronts, pulls = bmesh.new(), bmesh.new()
    drawer_fronts(fronts, pulls, -half + 0.06, half - 0.06, top - 0.135, top - 0.03, 1, -half + 0.03 - 0.014, thick=0.014)
    model.hard(fronts, "wood", bevel=0.004)
    model.turned(pulls, "metal")
    return model


def media_stand():
    """A low open cabinet a television stands on: a top, a foot, two sides and a shelf between them, with a back."""
    model = Model("media_stand")
    half, front, back, top = 0.45, -0.24, 0.24, 0.42
    boards = bmesh.new()
    cuboid(boards, -half, half, front, back, top - 0.03, top)
    cuboid(boards, -half + 0.01, half - 0.01, front + 0.01, back, 0.04, 0.065)
    cuboid(boards, -half + 0.01, half - 0.01, front + 0.02, back, 0.215, 0.235)
    for side in (-1, 1):
        low, far = sorted((side * (half - 0.035), side * (half - 0.01)))
        cuboid(boards, low, far, front + 0.01, back, 0.0, top - 0.03)
    cuboid(boards, -half + 0.035, half - 0.035, back - 0.012, back, 0.065, top - 0.03)
    model.hard(boards, "wood", bevel=0.005, segments=2)
    return model


def bar_stool():
    """A tall stool for a bar: a round padded seat on four splayed legs with a ring to rest the feet on. Its seat is 0.75 high."""
    model = Model("bar_stool")
    top = 0.75
    seat = bmesh.new()
    lathe(seat, [(0.0, top - 0.06), (0.165, top - 0.06), (0.18, top - 0.045), (0.18, top - 0.015), (0.16, top), (0.0, top + 0.005)], segments=20)
    model.turned(seat, "wood", sharp=50.0)
    legs = bmesh.new()
    for n in range(4):
        angle = math.radians(45 + n * 90)
        x, y = math.cos(angle), math.sin(angle)
        prism(legs, (x * 0.2, y * 0.2, 0.03, 0.03, 0.0), (x * 0.12, y * 0.12, 0.036, 0.036, top - 0.06))
    lathe(legs, [(0.165, 0.24), (0.178, 0.25), (0.165, 0.26), (0.152, 0.25)], segments=16, closed=True)
    model.hard(legs, "wood", bevel=0.004, segments=1)
    return model


def clothes_dryer():
    """A tumble dryer: a white case with a round door in its front and its dials on a console along the back. Its top is 0.92 high."""
    model = Model("clothes_dryer")
    half, front, back, top = 0.34, -0.33, 0.33, 0.92
    body = bmesh.new()
    cuboid(body, -half, half, front, back, 0.03, top)
    cuboid(body, -half, half, back - 0.14, back, top, top + 0.16)
    model.hard(body, "enamel", bevel=0.012, segments=3)
    door = bmesh.new()
    lathe(door, [(0.15, 0.0), (0.215, 0.0), (0.215, 0.02), (0.2, 0.034), (0.15, 0.034)], segments=22, closed=True, to=along_y(0.0, front, 0.5))
    model.turned(door, "enamel", sharp=40.0)
    glass = bmesh.new()
    lathe(glass, [(0.0, 0.0), (0.15, 0.0), (0.15, 0.012), (0.09, 0.026), (0.0, 0.03)], segments=18, to=along_y(0.0, front, 0.5))
    model.turned(glass, "screen", sharp=50.0)
    feet = bmesh.new()
    for x in (-1, 1):
        for y in (front + 0.05, back - 0.05):
            lathe(feet, [(0.0, 0.0), (0.025, 0.0), (0.025, 0.03), (0.0, 0.03)], cx=x * (half - 0.06), cy=y, segments=10)
    cuboid(feet, -half + 0.03, half - 0.03, back - 0.146, back - 0.14, top + 0.03, top + 0.13)
    model.turned(feet, "rubber")
    dials = bmesh.new()
    lathe(dials, [(0.0, 0.0), (0.04, 0.0), (0.036, 0.022), (0.0, 0.025)], segments=14, to=along_y(0.17, back - 0.146, top + 0.08))
    lathe(dials, [(0.0, 0.0), (0.02, 0.0), (0.018, 0.018), (0.0, 0.02)], segments=10, to=along_y(-0.12, back - 0.146, top + 0.08))
    model.turned(dials, "metal")
    return model


def plank_bench():
    """A bench with no back, two squares long: three planks on two steel legs. Its seat is 0.45 high."""
    model = Model("plank_bench")
    half, top = 0.9, 0.45
    planks = bmesh.new()
    for n in range(3):
        y = (n - 1) * 0.125
        cuboid(planks, -half, half, y - 0.057, y + 0.057, top - 0.04, top)
    model.hard(planks, "wood", bevel=0.006, segments=2)
    legs = bmesh.new()
    for side in (-1, 1):
        x = side * (half - 0.3)
        cuboid(legs, x - 0.03, x + 0.03, -0.17, 0.17, top - 0.055, top - 0.04)
        cuboid(legs, x - 0.025, x + 0.025, -0.03, 0.03, 0.02, top - 0.055)
        cuboid(legs, x - 0.04, x + 0.04, -0.17, 0.17, 0.0, 0.02)
    model.hard(legs, "steel", bevel=0.004, segments=1)
    return model


def pew():
    """A church pew three squares long: a seat and a leaning back between two shaped ends. Its seat is 0.44 high, its back at +Y."""
    model = Model("pew")
    half, seat = 1.45, 0.44
    boards = bmesh.new()
    cuboid(boards, -half + 0.05, half - 0.05, -0.2, 0.17, seat - 0.035, seat)
    outline_along_x(boards, [(0.13, seat - 0.035), (0.17, seat - 0.035), (0.262, 0.95), (0.222, 0.95)], -half + 0.05, half - 0.05)
    cuboid(boards, -half + 0.05, half - 0.05, 0.1, 0.13, 0.08, seat - 0.035)
    model.hard(boards, "wood", bevel=0.006, segments=2)
    ends = bmesh.new()
    for side in (-1, 1):
        low, far = sorted((side * (half - 0.05), side * half))
        outline_along_x(ends, [(-0.27, 0.0), (0.27, 0.0), (0.27, 0.6), (0.29, 0.98), (0.21, 1.0), (0.13, 0.66), (-0.2, 0.62), (-0.27, 0.56)], low, far)
    model.hard(ends, "wood", bevel=0.01, segments=2)
    return model


def bunk_bed(name, made):
    """Two beds, one over the other, in one frame of four posts, with a ladder at the foot. Its head is at +Y."""
    model = Model(name)
    hx, hy, top = 0.47, 0.98, 1.72
    frame = bmesh.new()
    for x in (-1, 1):
        for y in (-1, 1):
            cuboid(frame, x * hx - 0.03, x * hx + 0.03, y * hy - 0.03, y * hy + 0.03, 0.0, top)
    for low in (0.3, 1.2):
        for x in (-1, 1):
            cuboid(frame, x * hx - 0.015, x * hx + 0.015, -hy + 0.03, hy - 0.03, low, low + 0.14)
        for y in (-1, 1):
            cuboid(frame, -hx + 0.03, hx - 0.03, y * hy - 0.015, y * hy + 0.015, low, low + 0.3)
        cuboid(frame, -hx + 0.015, hx - 0.015, -hy + 0.015, hy - 0.015, low + 0.02, low + 0.05)
    # The rail that keeps whoever sleeps on top from rolling out, and the ladder up to them.
    cuboid(frame, -hx - 0.012, -hx + 0.012, -hy + 0.5, hy - 0.03, 1.5, 1.56)
    for x in (-0.28, 0.02):
        cuboid(frame, x - 0.015, x + 0.015, -hy - 0.035, -hy - 0.015, 0.0, 1.5)
    for z in (0.28, 0.58, 0.88, 1.18):
        cuboid(frame, -0.28, 0.02, -hy - 0.04, -hy - 0.012, z, z + 0.025)
    model.hard(frame, made, bevel=0.005, segments=1)
    for low in (0.35, 1.25):
        mattress = bmesh.new()
        cuboid(mattress, -hx + 0.035, hx - 0.035, -hy + 0.035, hy - 0.035, low, low + 0.15)
        model.hard(mattress, "linen", bevel=0.03, segments=2)
        pillow = bmesh.new()
        cage = grid_box(pillow, spread(-0.3, 0.3, 2), spread(hy - 0.47, hy - 0.09, 2), [low + 0.15, low + 0.24])
        cage[(1, 1, 1)].co.z += 0.03
        crease(pillow, cage, 0.3)
        model.soft(pillow, "pillow", levels=2)
        blanket = bmesh.new()
        cuboid(blanket, -hx + 0.03, hx - 0.03, -hy + 0.03, hy - 0.55, low + 0.1, low + 0.165)
        model.hard(blanket, "cloth", bevel=0.02, segments=2)
    return model


def school_desk():
    """A pupil's desk: a board on four steel legs with a tray for books under it, open towards whoever sits at it. Its top is 0.74 high."""
    model = Model("school_desk")
    hx, hy, top = 0.4, 0.3, 0.74
    board = bmesh.new()
    cuboid(board, -hx, hx, -hy, hy, top - 0.025, top)
    model.hard(board, "wood", bevel=0.007, segments=2)
    steel = bmesh.new()
    for x in (-1, 1):
        for y in (-1, 1):
            cuboid(steel, x * (hx - 0.04) - 0.014, x * (hx - 0.04) + 0.014, y * (hy - 0.04) - 0.014, y * (hy - 0.04) + 0.014, 0.0, top - 0.025)
        cuboid(steel, x * (hx - 0.04) - 0.01, x * (hx - 0.04) + 0.01, -hy + 0.054, hy - 0.054, 0.12, 0.14)
    cuboid(steel, -hx + 0.054, hx - 0.054, -hy + 0.05, hy - 0.04, top - 0.16, top - 0.15)
    cuboid(steel, -hx + 0.054, hx - 0.054, hy - 0.05, hy - 0.04, top - 0.15, top - 0.025)
    for x in (-1, 1):
        cuboid(steel, x * (hx - 0.06) - 0.006, x * (hx - 0.06) + 0.006, -hy + 0.05, hy - 0.04, top - 0.15, top - 0.025)
    model.hard(steel, "plastic", bevel=0.003, segments=1)
    return model


def rounded_rect(cx, cy, sx, sy, radius, steps=4):
    """The outline of a rectangle with rounded corners, going round anticlockwise seen from above."""
    hx, hy = sx / 2 - radius, sy / 2 - radius
    points = []
    for (x, y), start in (((hx, -hy), -90), ((hx, hy), 0), ((-hx, hy), 90), ((-hx, -hy), 180)):
        points += arc(cx + x, cy + y, radius, start, start + 90, steps)
    return points


# A restaurant's table on one column: how high its top is, how thick, and how wide the wooden band round its laminate is.
DINER_TOP, DINER_BOARD, DINER_BAND = 0.918, 0.038, 0.07


def pedestal_table(name, tops, columns, foot, banded=True, made="steel"):
    """Tops on columns, each column on a round foot of that radius. A top is (centre x, centre y, width, depth).

    banded: the top is laminate in a wooden band, a restaurant's; else laminate to its edge."""
    model = Model(name)
    under = DINER_TOP - DINER_BOARD
    for cx, cy, sx, sy in tops:
        board = bmesh.new()
        outline_up(board, rounded_rect(cx, cy, sx, sy, 0.07), under, DINER_TOP - (0.004 if banded else 0.0))
        model.hard(board, "wood" if banded else "worktop", bevel=0.008, segments=2)
        if banded:
            inlay = bmesh.new()
            outline_up(inlay, rounded_rect(cx, cy, sx - 2 * DINER_BAND, sy - 2 * DINER_BAND, 0.04), DINER_TOP - 0.01, DINER_TOP)
            model.hard(inlay, "worktop", bevel=0.002, segments=1)
    steel = bmesh.new()
    for cx, cy in columns:
        lathe(steel, [(0.0, 0.0), (foot, 0.0), (foot, 0.012), (foot * 0.82, 0.03), (0.07, 0.045), (0.042, 0.06), (0.035, 0.08),
                      (0.035, under - 0.06), (0.05, under - 0.04), (0.0, under - 0.04)], cx=cx, cy=cy, segments=24)
        # A spider of flat bars under the top, screwed to it.
        for angle in (45, 135):
            reach = 0.24
            dx, dy = reach * math.cos(math.radians(angle)), reach * math.sin(math.radians(angle))
            beam(steel, (cx - dx, cy - dy, under - 0.02), (cx + dx, cy + dy, under - 0.02), 0.04, 0.02)
        cuboid(steel, cx - 0.06, cx + 0.06, cy - 0.06, cy + 0.06, under - 0.04, under - 0.01)
    model.hard(steel, made, bevel=0.004, segments=1)
    return model


def swivel_chair():
    """A diner's chair: a round padded seat on a drum of wood, turning on a steel column, and a bentwood hoop back with two spindles. Its seat is 0.48 high."""
    model = Model("swivel_chair")
    seat = 0.48
    steel = bmesh.new()
    lathe(steel, [(0.0, 0.0), (0.15, 0.0), (0.15, 0.012), (0.12, 0.03), (0.05, 0.05), (0.032, 0.07), (0.032, seat - 0.11), (0.06, seat - 0.1),
                  (0.06, seat - 0.085), (0.0, seat - 0.085)], segments=24)
    model.hard(steel, "steel", bevel=0.004, segments=1)
    drum = bmesh.new()
    lathe(drum, [(0.0, seat - 0.09), (0.19, seat - 0.09), (0.2, seat - 0.08), (0.2, seat - 0.05), (0.0, seat - 0.05)], segments=32)
    model.hard(drum, "wood", bevel=0.006, segments=2)
    pad = bmesh.new()
    lathe(pad, [(0.0, seat - 0.05), (0.205, seat - 0.05), (0.215, seat - 0.04), (0.215, seat - 0.02), (0.2, seat - 0.004), (0.15, seat),
                (0.0, seat + 0.004)], segments=32)
    model.turned(pad, "vinyl", sharp=60.0)

    def rake(z):
        """How far back the hoop stands at height z."""
        return 0.17 + max(z - seat, 0.0) * 0.12

    back = bmesh.new()
    low, spring, half = seat - 0.07, 0.83, 0.16
    for side in (-1, 1):
        beam(back, (side * half, rake(low), low), (side * half, rake(spring), spring), 0.03, 0.026)
    hoop = arc(0.0, spring, half, 0, 180, 10)
    for (x0, z0), (x1, z1) in zip(hoop, hoop[1:]):
        beam(back, (x0, rake(z0), z0), (x1, rake(z1), z1), 0.03, 0.026)
    beam(back, (-half, rake(seat + 0.06), seat + 0.06), (half, rake(seat + 0.06), seat + 0.06), 0.03, 0.022)
    for x in (-0.055, 0.055):
        top = spring + math.sqrt(half * half - x * x)
        beam(back, (x, rake(seat + 0.06), seat + 0.06), (x, rake(top), top), 0.024, 0.018)
    model.hard(back, "wood", bevel=0.004, segments=1)
    return model


def cafe_table():
    """A square cafe table on a column with four feet reaching to its corners. Its top is 0.816 high, as the game gives it."""
    model = Model("cafe_table")
    top, half = 0.816, 0.43
    board = bmesh.new()
    cuboid(board, -half, half, -half, half, top - 0.034, top)
    model.hard(board, "wood", bevel=0.008, segments=2)
    iron = bmesh.new()
    lathe(iron, [(0.0, 0.04), (0.06, 0.04), (0.06, 0.09), (0.03, 0.12), (0.026, top - 0.08), (0.04, top - 0.06), (0.11, top - 0.045),
                 (0.11, top - 0.034), (0.0, top - 0.034)], segments=20)
    for n in range(4):
        angle = math.radians(45 + 90 * n)
        x, y = math.cos(angle), math.sin(angle)
        beam(iron, (x * 0.04, y * 0.04, 0.09), (x * 0.25, y * 0.25, 0.035), 0.035, 0.03)
        beam(iron, (x * 0.25, y * 0.25, 0.035), (x * 0.36, y * 0.36, 0.015), 0.035, 0.03)
        lathe(iron, [(0.0, 0.0), (0.025, 0.0), (0.025, 0.02), (0.0, 0.02)], cx=x * 0.36, cy=y * 0.36, segments=10)
    model.hard(iron, "steel", bevel=0.004, segments=1)
    return model


def cafe_chair():
    """A cafe's wooden chair: a dished seat on four legs, the back legs running up into posts with knobs, a deep top rail and a slat under it."""
    model = Model("cafe_chair")
    half, seat, front, back = 0.2, 0.45, -0.19, 0.18

    def rake(z):
        """How far back the posts stand at height z."""
        return back + max(z - seat, 0.0) * 0.12

    frame = bmesh.new()
    for side in (-1, 1):
        prism(frame, (side * (half + 0.01), front - 0.01, 0.03, 0.03, 0.0), (side * half, front, 0.036, 0.036, seat - 0.03))
        prism(frame, (side * (half + 0.01), back + 0.03, 0.03, 0.03, 0.0), (side * half, back, 0.036, 0.036, seat - 0.03))
        prism(frame, (side * half, back, 0.036, 0.036, seat - 0.03), (side * half, rake(0.9), 0.034, 0.03, 0.9))
        lathe(frame, [(0.0, 0.9), (0.02, 0.9), (0.024, 0.915), (0.016, 0.935), (0.0, 0.94)], cx=side * half, cy=rake(0.92), segments=10)
        cuboid(frame, side * half - 0.01, side * half + 0.01, front + 0.02, back - 0.02, 0.16, 0.185)
    cuboid(frame, -half + 0.02, half - 0.02, -0.012 + (front + back) / 2, 0.012 + (front + back) / 2, 0.16, 0.185)
    for y in (front, back):
        cuboid(frame, -half + 0.02, half - 0.02, y - 0.012, y + 0.012, seat - 0.09, seat - 0.03)
    for side in (-1, 1):
        cuboid(frame, side * half - 0.012, side * half + 0.012, front + 0.02, back - 0.02, seat - 0.09, seat - 0.03)
    prism(frame, (0.0, rake(0.76), 2 * half - 0.03, 0.022, 0.76), (0.0, rake(0.87), 2 * half - 0.03, 0.022, 0.87))
    prism(frame, (0.0, rake(0.6), 2 * half - 0.03, 0.018, 0.6), (0.0, rake(0.64), 2 * half - 0.03, 0.018, 0.64))
    model.hard(frame, "wood", bevel=0.004, segments=1)
    board = bmesh.new()
    cage = grid_box(board, spread(-half - 0.02, half + 0.02, 2), spread(front - 0.03, back + 0.01, 2), [seat - 0.03, seat])
    cage[(1, 1, 1)].co.z -= 0.012
    crease(board, cage, 0.7)
    model.soft(board, "wood", levels=2)
    return model


def restaurant_seating():
    """A diner's tables on one column and the chairs that turn at them, and a cafe's table and chairs."""
    side = 0.8
    return [pedestal_table("diner_tables", [(0.0, -0.5, side, side), (0.0, 0.5, side, side)], [(0.0, -0.5), (0.0, 0.5)], 0.19),
            pedestal_table("diner_table_long", [(0.0, 0.0, side, 1.8)], [(0.0, 0.0)], 0.22),
            pedestal_table("fifties_table", [(0.0, 0.0, 1.84, 0.88)], [(0.0, 0.0)], 0.38, banded=False, made="metal"),
            swivel_chair(), cafe_table(), cafe_chair()]


def lounge_booth():
    """A fast food restaurant's booth two squares long: a moulded shell with a hard seat, a high back and a high side at
    each end, and a padded back of upright pleats let into the shell."""
    model = Model("lounge_booth")
    half, front, back, seat, top, side, shell = 0.95, -0.45, 0.45, 0.45, 1.15, 0.06, 0.15
    body = bmesh.new()
    cuboid(body, -half + side, half - side, front, back - shell, 0.0, seat)
    cuboid(body, -half + side, half - side, back - shell, back, 0.0, top)
    cuboid(body, -half, -half + side, front, back, 0.0, top)
    cuboid(body, half - side, half, front, back, 0.0, top)
    model.hard(body, "paint", bevel=0.025, segments=3)
    pleats = 4
    each = (2 * (half - side) - 0.06) / (2 * pleats)
    for n in range(2 * pleats):
        x0 = -half + side + 0.03 + n * each
        part = bmesh.new()
        cage = grid_box(part, spread(x0 + 0.004, x0 + each - 0.004, 2), [back - shell - 0.075, back - shell + 0.01], [seat + 0.04, top - 0.09])
        for (i, j, k), vert in cage.items():
            if i == 1 and j == 0:
                vert.co.y -= 0.02
        crease(part, cage, 0.25)
        model.soft(part, "vinyl", levels=2)
    return model


def leather_booth():
    """A bar's booth two squares long, upholstered in leather all over: a deep seat rolled at its front and its ends and
    a high back with a roll along its top, on a wooden plinth."""
    model = Model("leather_booth")
    half, front, back, plinth, seat, top, rest = 0.95, -0.45, 0.45, 0.2, 0.47, 1.2, 0.22
    foot = bmesh.new()
    cuboid(foot, -half + 0.03, half - 0.03, front + 0.03, back, 0.0, plinth)
    model.hard(foot, "dark_wood", bevel=0.006, segments=1)
    pad = bmesh.new()
    cage = grid_box(pad, spread(-half, half, 4), spread(front, back - rest + 0.02, 2), [plinth, seat])
    for x in (1, 3):
        cage[(x, 1, 1)].co.z += 0.015
    crease(pad, cage, 0.15)
    model.soft(pad, "vinyl", levels=2)
    backrest = bmesh.new()
    cage = grid_box(backrest, spread(-half, half, 4), spread(back - rest, back, 2), spread(plinth, top, 3))
    for (i, j, k), vert in cage.items():
        if k == 3 and j == 0:
            vert.co.y += 0.03
        if 0 < i < 4 and j == 0 and k in (1, 2):
            vert.co.y -= 0.025
    crease(backrest, cage, 0.3)
    model.soft(backrest, "vinyl", levels=2)
    return model


def waiting_chairs():
    """Two waiting room chairs side by side, a square each: a padded seat and back, wooden arms, each on one post with a
    long foot along the floor."""
    model = Model("waiting_chairs")
    seat, back = 0.45, 0.24
    frame = bmesh.new()
    pads = bmesh.new()
    arms = bmesh.new()
    for middle in (-0.5, 0.5):
        cuboid(frame, middle - 0.03, middle + 0.03, -0.26, 0.26, 0.0, 0.035)
        cuboid(frame, middle - 0.025, middle + 0.025, -0.025, 0.025, 0.035, seat - 0.06)
        cuboid(frame, middle - 0.2, middle + 0.2, -0.17, 0.17, seat - 0.07, seat - 0.05)
        cuboid(frame, middle - 0.02, middle + 0.02, 0.18, back - 0.02, seat - 0.07, seat + 0.12)
        for x in (-1, 1):
            arm = middle + x * 0.265
            cuboid(frame, arm - 0.012, arm + 0.012, 0.0, 0.03, seat - 0.07, 0.62)
            cuboid(arms, arm - 0.025, arm + 0.025, -0.2, 0.17, 0.62, 0.65)
        cage = grid_box(pads, spread(middle - 0.24, middle + 0.24, 2), spread(-0.22, 0.2, 2), [seat - 0.05, seat + 0.02])
        cage[(1, 1, 1)].co.z += 0.012
        cage[(1, 0, 1)].co.z -= 0.008
        crease(pads, cage, 0.4)
        cage = grid_box(pads, spread(middle - 0.23, middle + 0.23, 2), [back - 0.06, back], spread(seat + 0.08, 0.95, 2))
        for (i, j, k), vert in cage.items():
            vert.co.y += 0.06 * (vert.co.z - seat - 0.08) / (0.95 - seat - 0.08)
        cage[(1, 0, 2)].co.z += 0.03
        cage[(1, 1, 2)].co.z += 0.03
        cage[(1, 0, 1)].co.y -= 0.012
        crease(pads, cage, 0.4)
    model.hard(frame, "rubber", bevel=0.004, segments=1)
    model.soft(pads, "cloth", levels=2)
    model.hard(arms, "wood", bevel=0.008, segments=2)
    return model


def upholstered():
    """Seats in restaurants, bars and waiting rooms that Indoors.java places."""
    return [lounge_booth(), leather_booth(), waiting_chairs()]


def indoors():
    """What stands in rooms and hangs on their walls: the pieces Indoors.java places."""
    return [range_hood(), garbage_can(), waste_basket(), louvre("wall_vent", 0.56, 0.42, "plastic", 5), louvre("wall_grille", 0.64, 0.34, "enamel", 7, deep=0.03),
            round_vent(), exit_sign(), sconce(), pedestal_sink(), medicine_cabinet(), hand_dryer(), coat_rack(),
            filing_cabinet(), carton("carton", 0.58, 0.58, 0.56), carton("carton_small", 0.44, 0.34, 0.3), computer(), water_cooler(),
            floor_lamp("torchiere", False), floor_lamp("standard_lamp", True), dresser_mirror(), side_table(), media_stand(), bar_stool(), clothes_dryer(),
            plank_bench(), pew(), bunk_bed("bunk_bed", "dark_wood"), bunk_bed("steel_bunk_bed", "metal"),
            cabinet("steel_counter", 0.62, 0.90, True, made="steel", top_of="steel"),
            cabinet("steel_counter_sink", 0.62, 0.90, True, sink=True, made="steel", top_of="steel"),
            cabinet("steel_counter_corner", 0.62, 0.90, True, corner=True, made="steel", top_of="steel"),
            wall_shelf("wire_wall_shelves", 2, made="plastic"), school_desk()] + restaurant_seating()


def beam(bm, start, end, wide, deep):
    """A bar from start to end, wide across the way it runs and deep the other way."""
    start, end = Vector(start), Vector(end)
    along = (end - start).normalized()
    side = along.cross(Vector((0.0, 0.0, 1.0)))
    side = Vector((1.0, 0.0, 0.0)) if side.length < 1e-6 else side.normalized()
    up = side.cross(along).normalized()
    rings = [[bm.verts.new(point + side * dx * wide / 2 + up * dz * deep / 2) for dx, dz in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
             for point in (start, end)]
    bm.faces.new(rings[0][::-1])
    bm.faces.new(rings[1])
    for i in range(4):
        j = (i + 1) % 4
        bm.faces.new((rings[0][i], rings[0][j], rings[1][j], rings[1][i]))


def outline_up(bm, outline, z0, z1):
    """A body with the same outline, given as (x, y) going round, from z0 to z1."""
    low = [bm.verts.new((x, y, z0)) for x, y in outline]
    high = [bm.verts.new((x, y, z1)) for x, y in outline]
    count = len(outline)
    for i in range(count):
        j = (i + 1) % count
        bm.faces.new((low[i], low[j], high[j], high[i]))
    bm.faces.new(low[::-1])
    bm.faces.new(high)


def arc(cx, cy, radius, start, end, steps):
    """Points round (cx, cy) from the angle start to end, in degrees."""
    return [(cx + radius * math.cos(math.radians(start + (end - start) * i / steps)),
             cy + radius * math.sin(math.radians(start + (end - start) * i / steps))) for i in range(steps + 1)]


# A length of sheet steel duct as it lies on a flat roof: half its width, its height, and how far its seams stand out and how wide they are.
DUCT_HALF, DUCT_HIGH, DUCT_SEAM, DUCT_BAND = 0.2, 0.34, 0.012, 0.03


def duct_mouth(bm, x, y, across_x):
    """The dark of a duct's open end, a hair proud of the end at (x, y): across_x for an end that faces along y."""
    inset, proud = 0.018, 0.0015
    if across_x:
        cuboid(bm, x - DUCT_HALF + inset, x + DUCT_HALF - inset, y - proud, y + proud, inset, DUCT_HIGH - inset)
    else:
        cuboid(bm, x - proud, x + proud, y - DUCT_HALF + inset, y + DUCT_HALF - inset, inset, DUCT_HIGH - inset)


def duct_run():
    """A square's length of duct, open at both ends so that a run of them is one pipe: a metre long, to meet the next."""
    model = Model("duct_run")
    shell = bmesh.new()
    wall = 0.012
    outer = [(-DUCT_HALF, 0.0), (DUCT_HALF, 0.0), (DUCT_HALF, DUCT_HIGH), (-DUCT_HALF, DUCT_HIGH)]
    inner = [(-DUCT_HALF + wall, wall), (DUCT_HALF - wall, wall), (DUCT_HALF - wall, DUCT_HIGH - wall), (-DUCT_HALF + wall, DUCT_HIGH - wall)]
    rings = [[shell.verts.new((x, y, z)) for x, z in outline] for outline in (outer, inner) for y in (-0.5, 0.5)]
    for i in range(4):
        j = (i + 1) % 4
        shell.faces.new((rings[0][i], rings[0][j], rings[1][j], rings[1][i]))
        for y in (0, 1):
            shell.faces.new((rings[y][i], rings[y][j], rings[2 + y][j], rings[2 + y][i]))
    model.plain(shell, "paint")
    # The seam where two sheets are joined, round the middle of the length.
    seam = bmesh.new()
    for x0, x1, z0, z1 in ((-DUCT_HALF - DUCT_SEAM, DUCT_HALF + DUCT_SEAM, DUCT_HIGH, DUCT_HIGH + DUCT_SEAM),
                           (-DUCT_HALF - DUCT_SEAM, -DUCT_HALF, 0.0, DUCT_HIGH), (DUCT_HALF, DUCT_HALF + DUCT_SEAM, 0.0, DUCT_HIGH)):
        cuboid(seam, x0, x1, -DUCT_BAND / 2, DUCT_BAND / 2, z0, z1)
    model.plain(seam, "paint")
    bore = bmesh.new()
    lining = [[bore.verts.new((x, y, z)) for x, z in inner] for y in (-0.5, 0.5)]
    for i in range(4):
        j = (i + 1) % 4
        bore.faces.new((lining[0][i], lining[0][j], lining[1][j], lining[1][i]))
    model._take(bore, "plastic", lambda obj: None, sharp=1.0, inward=True)
    return model


def duct_down():
    """A length of duct whose back end turns down through the roof. Its front end is open, to meet a run."""
    model = Model("duct_down")
    body = bmesh.new()
    bend = 0.5 - DUCT_HIGH
    outline_along_x(body, [(-0.5, 0.0), (-0.5, DUCT_HIGH)] + arc(bend, 0.0, DUCT_HIGH, 90.0, 0.0, 8), -DUCT_HALF, DUCT_HALF)
    model.turned(body, "paint", sharp=35.0)
    seam = bmesh.new()
    cuboid(seam, -DUCT_HALF - DUCT_SEAM, DUCT_HALF + DUCT_SEAM, -0.12 - DUCT_BAND / 2, -0.12 + DUCT_BAND / 2, 0.0, DUCT_HIGH + DUCT_SEAM)
    model.plain(seam, "paint")
    mouth = bmesh.new()
    duct_mouth(mouth, 0.0, -0.5, True)
    model.plain(mouth, "plastic")
    return model


def duct_elbow():
    """A duct turning a corner on the level: one end open to the front and the other to +X. The origin is the middle of
    its square, so each end is at the square's edge."""
    model = Model("duct_elbow", centred=False)
    body = bmesh.new()
    outline_up(body, [(-DUCT_HALF, -0.5), (DUCT_HALF, -0.5), (DUCT_HALF, -DUCT_HALF), (0.5, -DUCT_HALF), (0.5, DUCT_HALF)]
               + arc(DUCT_HALF, -DUCT_HALF, 2 * DUCT_HALF, 90.0, 180.0, 8)[1:], 0.0, DUCT_HIGH)
    model.turned(body, "paint", sharp=35.0)
    mouth = bmesh.new()
    duct_mouth(mouth, 0.0, -0.5, True)
    duct_mouth(mouth, 0.5, 0.0, False)
    model.plain(mouth, "plastic")
    return model


def roof_dome():
    """A ventilator on a flat roof: a square curb, a short collar and a low domed cowl that overhangs it."""
    model = Model("roof_dome")
    curb = bmesh.new()
    prism(curb, (0.0, 0.0, 0.86, 0.86, 0.0), (0.0, 0.0, 0.8, 0.8, 0.07))
    model.hard(curb, "paint", bevel=0.008, segments=2)
    collar = bmesh.new()
    lathe(collar, [(0.27, 0.07), (0.27, 0.17), (0.0, 0.17)], segments=24)
    model.turned(collar, "plastic")
    cowl = bmesh.new()
    lathe(cowl, [(0.0, 0.15), (0.35, 0.15), (0.36, 0.165), (0.345, 0.2), (0.3, 0.235), (0.2, 0.265), (0.1, 0.278), (0.0, 0.282)], segments=24)
    model.turned(cowl, "paint", sharp=50.0)
    return model


def fan_in(model, top, radius=0.3):
    """An extractor fan let into a level top: a rim, the dark of its well, and four blades on a hub under a guard of two bars."""
    rim = bmesh.new()
    lathe(rim, [(radius, top), (radius + 0.035, top), (radius + 0.035, top + 0.02), (radius, top + 0.02)], segments=24, closed=True)
    model.turned(rim, "paint", sharp=50.0)
    well = bmesh.new()
    lathe(well, [(0.0, top - 0.002), (radius, top - 0.002), (radius, top + 0.002), (0.0, top + 0.002)], segments=24)
    model.plain(well, "rubber")
    blades = bmesh.new()
    for turn in range(4):
        angle = math.radians(45 + 90 * turn)
        out = Vector((math.cos(angle), math.sin(angle), 0.0))
        side = Vector((-out.y, out.x, 0.0))
        corners = [out * 0.05 - side * 0.03, out * (radius - 0.03) - side * 0.09, out * (radius - 0.03) + side * 0.09, out * 0.05 + side * 0.03]
        outline_up(blades, [(point.x, point.y) for point in corners], top + 0.006, top + 0.01)
    lathe(blades, [(0.0, top + 0.004), (0.06, top + 0.004), (0.06, top + 0.024), (0.0, top + 0.024)], segments=12)
    model.plain(blades, "metal")
    guard = bmesh.new()
    cuboid(guard, -radius, radius, -0.012, 0.012, top + 0.02, top + 0.03)
    cuboid(guard, -0.012, 0.012, -radius, radius, top + 0.02, top + 0.03)
    model.plain(guard, "paint")


def roof_fan():
    """An extractor fan on a flat roof: a low square curb with the fan in its top."""
    model = Model("roof_fan")
    curb = bmesh.new()
    prism(curb, (0.0, 0.0, 0.86, 0.86, 0.0), (0.0, 0.0, 0.8, 0.8, 0.09))
    model.hard(curb, "paint", bevel=0.008, segments=2)
    fan_in(model, 0.09)
    return model


def fan_unit():
    """A condenser on a roof: a steel case a third of a metre high with a fan in its top."""
    model = Model("fan_unit")
    case = bmesh.new()
    cuboid(case, -0.46, 0.46, -0.46, 0.46, 0.02, 0.33)
    model.hard(case, "paint", bevel=0.03, segments=3)
    foot = bmesh.new()
    cuboid(foot, -0.42, 0.42, -0.42, 0.42, 0.0, 0.02)
    model.plain(foot, "plastic")
    fan_in(model, 0.33, radius=0.33)
    return model


def roof_stacks():
    """Two vent pipes through a flat roof, each in a cone of flashing, on one sheet of it."""
    model = Model("roof_stacks")
    sheet = bmesh.new()
    prism(sheet, (0.0, 0.0, 0.62, 0.62, 0.0), (0.0, 0.0, 0.56, 0.56, 0.035))
    model.hard(sheet, "paint", bevel=0.006, segments=1)
    stacks = bmesh.new()
    for x, y, high in ((-0.11, 0.08, 0.3), (0.13, -0.08, 0.24)):
        lathe(stacks, [(0.1, 0.035), (0.062, 0.12), (0.05, high), (0.058, high), (0.058, high + 0.015), (0.04, high + 0.015), (0.04, high - 0.05),
                       (0.0, high - 0.05)], cx=x, cy=y, segments=14)
    model.turned(stacks, "paint", sharp=40.0)
    return model


# A pipe run along the foot of a wall: how far its middle is from the middle of its square and above the floor, and how thick it is.
PIPE_OUT, PIPE_UP, PIPE_RADIUS = 0.38, 0.13, 0.055


def pipe_length(bm, fittings, from_y, to_y, x):
    """A pipe along y at x with a flange at each end, on two stands."""
    lathe(bm, [(PIPE_RADIUS, from_y), (PIPE_RADIUS, to_y)], segments=12, caps=False, to=along_y(x, 0.0, PIPE_UP) @ Matrix.Rotation(math.radians(180), 4, "X"))
    for y in (from_y, to_y - 0.03):
        lathe(fittings, [(0.0, y), (PIPE_RADIUS + 0.03, y), (PIPE_RADIUS + 0.03, y + 0.03), (0.0, y + 0.03)], segments=12,
              to=along_y(x, 0.0, PIPE_UP) @ Matrix.Rotation(math.radians(180), 4, "X"))
    for y in (from_y + (to_y - from_y) * 0.3, from_y + (to_y - from_y) * 0.7):
        cuboid(fittings, x - 0.008, x + 0.008, y - 0.008, y + 0.008, 0.0, PIPE_UP - PIPE_RADIUS + 0.01)
        cuboid(fittings, x - 0.04, x + 0.04, y - 0.012, y + 0.012, 0.0, 0.008)


def pipe_run():
    """A square's length of pipe on its stands. It lies along y through the model's middle: the mod stands it at the edge of its square."""
    model = Model("pipe_run", centred=False)
    pipe, fittings = bmesh.new(), bmesh.new()
    pipe_length(pipe, fittings, -0.5, 0.5, 0.0)
    model.turned(pipe, "paint")
    model.turned(fittings, "metal", sharp=40.0)
    return model


def pipe_corner():
    """Two lengths of pipe that meet in an elbow at a corner of their square: one along the square's +X side and one along its front."""
    model = Model("pipe_corner", centred=False)
    pipe, fittings = bmesh.new(), bmesh.new()
    bend = 0.1
    pipe_length(pipe, fittings, -PIPE_OUT + bend, 0.5, PIPE_OUT)
    lathe(pipe, [(PIPE_RADIUS, -0.5), (PIPE_RADIUS, PIPE_OUT - bend)], segments=12, caps=False, to=along_x(0.0, -PIPE_OUT, PIPE_UP))
    for x in (-0.5, PIPE_OUT - bend - 0.03):
        lathe(fittings, [(0.0, x), (PIPE_RADIUS + 0.03, x), (PIPE_RADIUS + 0.03, x + 0.03), (0.0, x + 0.03)], segments=12, to=along_x(0.0, -PIPE_OUT, PIPE_UP))
    for x in (-0.2, 0.1):
        cuboid(fittings, x - 0.008, x + 0.008, -PIPE_OUT - 0.008, -PIPE_OUT + 0.008, 0.0, PIPE_UP - PIPE_RADIUS + 0.01)
        cuboid(fittings, x - 0.012, x + 0.012, -PIPE_OUT - 0.04, -PIPE_OUT + 0.04, 0.0, 0.008)
    # The elbow: a quarter of a ring about the upright through the middle of the bend.
    steps = 6
    rings = []
    for i in range(steps + 1):
        angle = math.radians(-90 * i / steps)
        out = Vector((math.cos(angle), math.sin(angle), 0.0))
        centre = Vector((PIPE_OUT - bend, -PIPE_OUT + bend, PIPE_UP)) + out * bend
        rings.append([pipe.verts.new(centre + (out * math.cos(2 * math.pi * k / 12) + Vector((0.0, 0.0, 1.0)) * math.sin(2 * math.pi * k / 12)) * PIPE_RADIUS)
                      for k in range(12)])
    for a, b in zip(rings, rings[1:]):
        for k in range(12):
            pipe.faces.new((a[k], a[(k + 1) % 12], b[(k + 1) % 12], b[k]))
    model.turned(pipe, "paint")
    model.turned(fittings, "metal", sharp=40.0)
    return model


def culvert_pipe():
    """A length of wide pipe lying on the ground, open at both ends: a metre long, to meet the next."""
    model = Model("culvert_pipe")
    pipe = bmesh.new()
    lathe(pipe, [(0.118, -0.5), (0.14, -0.5), (0.14, 0.5), (0.118, 0.5)], segments=18, closed=True, to=along_y(0.0, 0.0, 0.14))
    model.turned(pipe, "paint", sharp=50.0)
    return model


def deck_chair():
    """A folding deck chair: two frames of wood that cross, and a sling of striped canvas hung from the top of one to the front of the other. Its foot is the front."""
    model = Model("deck_chair")
    half = 0.29
    frame = bmesh.new()
    # The long frame runs from the ground at the front up to the top of the back, and the short one from the ground at the back up to the front of the seat.
    long_low, long_high = (-0.56, 0.02), (0.5, 0.92)
    short_low, short_high = (0.52, 0.02), (-0.5, 0.36)
    for side in (-1, 1):
        beam(frame, (side * half, long_low[0], long_low[1]), (side * half, long_high[0], long_high[1]), 0.022, 0.045)
        beam(frame, (side * (half - 0.03), short_low[0], short_low[1]), (side * (half - 0.03), short_high[0], short_high[1]), 0.022, 0.045)
        # The prop that holds the two apart, from the long frame down to the back of the short one.
        beam(frame, (side * (half + 0.025), 0.3, 0.75), (side * (half + 0.025), 0.5, 0.06), 0.02, 0.035)
    for y, z, reach in ((long_low[0], long_low[1], half), (long_high[0], long_high[1], half), (short_low[0], short_low[1], half - 0.03),
                        (short_high[0], short_high[1], half - 0.03)):
        beam(frame, (-reach, y, z), (reach, y, z), 0.03, 0.03)
    model.hard(frame, "wood", bevel=0.005, segments=1)
    # The sling hangs in a curve from the top rail to the front rail, lowest just behind the front rail.
    steps, stripes = 10, 7

    def hang(t):
        return (short_high[0] + (long_high[0] - short_high[0]) * t,
                short_high[1] + (long_high[1] - short_high[1]) * t - 0.52 * t * (1.0 - t) * (1.6 - t))

    wide = 2 * (half - 0.045)
    for surface in ("linen", "cloth"):
        canvas = bmesh.new()
        for stripe in range(stripes):
            if (stripe % 2 == 0) != (surface == "cloth"):
                continue
            x0 = -wide / 2 + wide * stripe / stripes
            x1 = x0 + wide / stripes
            # A strip of cloth with a thickness, closed all round, so that each side of it faces its own way.
            rows = [[canvas.verts.new((x, hang(i / steps)[0], hang(i / steps)[1] + lift)) for lift in (0.0, 0.005) for x in (x0, x1)] for i in range(steps + 1)]
            for a, b in zip(rows, rows[1:]):
                canvas.faces.new((a[1], a[0], b[0], b[1]))
                canvas.faces.new((a[2], a[3], b[3], b[2]))
                canvas.faces.new((a[0], a[2], b[2], b[0]))
                canvas.faces.new((a[3], a[1], b[1], b[3]))
            canvas.faces.new((rows[0][0], rows[0][1], rows[0][3], rows[0][2]))
            canvas.faces.new((rows[-1][1], rows[-1][0], rows[-1][2], rows[-1][3]))
        model._take(canvas, surface, lambda obj: None, sharp=60.0)
    return model


def slab_bench():
    """A street bench: a slab of three boards on two steel legs bolted to the ground."""
    model = Model("slab_bench")
    half, top = 0.5, 0.36
    boards = bmesh.new()
    for n in range(3):
        y = -0.19 + n * 0.13
        cuboid(boards, -half, half, y, y + 0.12, top - 0.04, top)
    model.hard(boards, "paint", bevel=0.005, segments=1)
    legs = bmesh.new()
    for side in (-1, 1):
        x = side * (half - 0.1)
        cuboid(legs, x - 0.025, x + 0.025, -0.03, 0.03, 0.0, top - 0.04)
        cuboid(legs, x - 0.025, x + 0.025, -0.18, 0.18, top - 0.065, top - 0.04)
        cuboid(legs, x - 0.05, x + 0.05, -0.1, 0.1, 0.0, 0.012)
    model.hard(legs, "metal", bevel=0.004, segments=1)
    return model


def dustbin():
    """A galvanised dustbin: a ribbed can that widens to its rim, two handles, and a lid with a handle of its own."""
    model = Model("dustbin")
    can = bmesh.new()
    lathe(can, [(0.0, 0.0), (0.2, 0.0), (0.205, 0.02), (0.24, 0.6), (0.25, 0.61), (0.25, 0.63), (0.0, 0.63)], segments=20)
    model.turned(can, "steel", sharp=35.0)
    ribs = bmesh.new()
    for k in range(10):
        angle = 2 * math.pi * k / 10
        way = Vector((math.cos(angle), math.sin(angle), 0.0))
        beam(ribs, way * 0.213 + Vector((0.0, 0.0, 0.08)), way * 0.238 + Vector((0.0, 0.0, 0.56)), 0.022, 0.012)
    for side in (-1, 1):
        cuboid(ribs, side * 0.246 - 0.022, side * 0.246 + 0.022, -0.05, 0.05, 0.44, 0.46)
    model.hard(ribs, "steel", bevel=0.004, segments=1)
    lid = bmesh.new()
    lathe(lid, [(0.0, 0.63), (0.262, 0.63), (0.262, 0.66), (0.2, 0.685), (0.08, 0.705), (0.0, 0.708)], segments=20)
    model.turned(lid, "steel", sharp=35.0)
    grip = bmesh.new()
    cuboid(grip, -0.05, 0.05, -0.01, 0.01, 0.7, 0.74)
    model.hard(grip, "steel", bevel=0.004, segments=1)
    return model


def road_cone():
    model = Model("road_cone")
    foot = bmesh.new()
    cuboid(foot, -0.19, 0.19, -0.19, 0.19, 0.0, 0.03)
    model.hard(foot, "paint", bevel=0.006, segments=1)
    body = bmesh.new()
    lathe(body, [(0.14, 0.03), (0.035, 0.68), (0.035, 0.7), (0.02, 0.7), (0.02, 0.6), (0.0, 0.6)], segments=16)
    model.turned(body, "paint", sharp=40.0)
    return model


def traffic_drum():
    """A striped post that marks off road works: a tube on a heavy square foot of rubber, banded dark and bright."""
    model = Model("traffic_drum")
    foot = bmesh.new()
    prism(foot, (0.0, 0.0, 0.5, 0.5, 0.0), (0.0, 0.0, 0.42, 0.42, 0.06))
    model.hard(foot, "rubber", bevel=0.008, segments=1)
    bright, dark = bmesh.new(), bmesh.new()
    bands = [0.06, 0.24, 0.4, 0.56, 0.72, 0.9, 1.04]
    for n, (low, high) in enumerate(zip(bands, bands[1:])):
        lathe(dark if n % 2 == 0 else bright, [(0.115 - 0.02 * low, low), (0.115 - 0.02 * high, high)], segments=16, caps=False)
    lathe(bright, [(0.094, 1.04), (0.07, 1.04), (0.07, 0.9), (0.0, 0.9)], segments=16, caps=False)
    model.turned(bright, "paint", sharp=40.0)
    model.turned(dark, "plastic", sharp=40.0)
    return model


def hydrant():
    """A fire hydrant: a barrel on a flanged foot, a domed bonnet with its nut, and a capped outlet on each side and one at the front."""
    model = Model("hydrant")
    body = bmesh.new()
    lathe(body, [(0.0, 0.0), (0.13, 0.0), (0.13, 0.03), (0.085, 0.05), (0.085, 0.42), (0.11, 0.43), (0.11, 0.46), (0.1, 0.47), (0.095, 0.56), (0.07, 0.62),
                 (0.035, 0.645), (0.035, 0.7), (0.0, 0.7)], segments=16)
    model.turned(body, "paint", sharp=35.0)
    outlets = bmesh.new()
    for side in (0, 180):
        lathe(outlets, [(0.0, 0.07), (0.05, 0.07), (0.05, 0.13), (0.06, 0.13), (0.06, 0.16), (0.0, 0.16)], segments=10,
              to=Matrix.Rotation(math.radians(side), 4, "Z") @ along_x(0.0, 0.0, 0.33))
    lathe(outlets, [(0.0, 0.07), (0.065, 0.07), (0.065, 0.13), (0.075, 0.13), (0.075, 0.16), (0.0, 0.16)], segments=10, to=along_y(0.0, 0.0, 0.3))
    model.turned(outlets, "paint", sharp=35.0)
    return model


def pallet_boards(model):
    boards = bmesh.new()
    for n in range(5):
        y = -0.5 + n * 0.2125
        cuboid(boards, -0.5, 0.5, y, y + 0.15, 0.115, 0.14)
    for n in range(3):
        y = -0.5 + n * 0.425
        cuboid(boards, -0.5, 0.5, y, y + 0.15, 0.0, 0.022)
    for x in (-0.5, -0.05, 0.4):
        cuboid(boards, x, x + 0.1, -0.5, 0.5, 0.022, 0.115)
    model.hard(boards, "wood", bevel=0.004, segments=1)


def pallet():
    model = Model("pallet")
    pallet_boards(model)
    return model


def brick_pallet():
    """A pallet with a cube of bricks stacked on it."""
    model = Model("brick_pallet")
    pallet_boards(model)
    stack = bmesh.new()
    cuboid(stack, -0.4, 0.4, -0.4, 0.4, 0.14, 0.72)
    model.hard(stack, "paint", bevel=0.006, segments=1)
    return model


def gravestone(name, top):
    """A headstone on its plinth, its face to the front: top is round for a half-round head, square for a level one and gabled for a point."""
    model = Model(name)
    plinth = bmesh.new()
    cuboid(plinth, -0.3, 0.3, -0.13, 0.13, 0.0, 0.1)
    model.hard(plinth, "paint", bevel=0.01, segments=2)
    stone = bmesh.new()
    half, shoulder = 0.22, 0.62
    if top == "round":
        head = [(x, z + shoulder) for x, z in arc(0.0, 0.0, half, 0.0, 180.0, 10)]
    elif top == "square":
        head = [(half, shoulder + 0.08), (-half, shoulder + 0.08)]
    else:
        head = [(half, shoulder), (0.0, shoulder + 0.17), (-half, shoulder)]
    outline_along_y(stone, [(-half, 0.1), (half, 0.1)] + head, -0.055, 0.055)
    model.turned(stone, "paint", sharp=50.0)
    return model


def sack_barrier():
    """A barrier of the kind an army fills with earth: a cube of sacking in a cage of wire mesh, heaped at the top."""
    model = Model("sack_barrier")
    sack = bmesh.new()
    prism(sack, (0.0, 0.0, 0.94, 0.94, 0.0), (0.0, 0.0, 0.98, 0.98, 0.95))
    model.hard(sack, "paint", bevel=0.05, segments=3)
    heap = bmesh.new()
    lathe(heap, [(0.6, 0.93), (0.5, 1.0), (0.28, 1.06), (0.0, 1.08)], segments=4, to=Matrix.Rotation(math.radians(45), 4, "Z"))
    model.turned(heap, "linen", sharp=80.0)
    cage = bmesh.new()
    for side in (-1, 1):
        for n in range(5):
            at = -0.48 + n * 0.24
            cuboid(cage, at - 0.006, at + 0.006, side * 0.497 - 0.006, side * 0.497 + 0.006, 0.0, 0.96)
            cuboid(cage, side * 0.497 - 0.006, side * 0.497 + 0.006, at - 0.006, at + 0.006, 0.0, 0.96)
        for z in (0.02, 0.33, 0.64, 0.95):
            cuboid(cage, -0.5, 0.5, side * 0.497 - 0.006, side * 0.497 + 0.006, z - 0.006, z + 0.006)
            cuboid(cage, side * 0.497 - 0.006, side * 0.497 + 0.006, -0.5, 0.5, z - 0.006, z + 0.006)
    model.plain(cage, "metal")
    return model


def log_stack(name, rows):
    """Logs stacked along y, each row one fewer than the row under it and lying in its hollows: bark, and the pale wood of the sawn ends."""
    model = Model(name)
    radius, half = 0.13, 0.92
    bark, ends = bmesh.new(), bmesh.new()
    random.seed(len(name))
    for row, count in enumerate(rows):
        for n in range(count):
            x = (n - (count - 1) / 2) * 2 * radius
            z = radius + row * radius * math.sqrt(3)
            # No two logs are sawn to one length.
            front, back = -half + random.uniform(0.0, 0.07), half - random.uniform(0.0, 0.07)
            thick = radius * random.uniform(0.93, 1.0)
            lathe(bark, [(thick, -back), (thick, -front)], segments=10, caps=False, to=along_y(x, 0.0, z))
            # Each sawn end is a thin disc of its own past the end of the bark, so it faces out of the log.
            for end, out in ((-back, 0.003), (-front, -0.003)):
                lathe(ends, [(0.0, min(end, end - out)), (thick, min(end, end - out)), (thick, max(end, end - out)), (0.0, max(end, end - out))], segments=10,
                      to=along_y(x, 0.0, z))
    model.turned(bark, "dark_wood")
    model.plain(ends, "wood")
    return model


def twin_head():
    """The upper length of a car park's lamp the game has in two sprites: the top of the post and two flat heads on a
    short arm, one to the front and one to the back, each with its lens underneath. The origin is the post's axis."""
    model = Model("twin_head", centred=False)
    shaft = bmesh.new()
    lathe(shaft, [(0.0, 0.0), (0.045, 0.0), (0.045, 1.84), (0.0, 1.84)], segments=10)
    model.turned(shaft, "paint", sharp=40.0)
    heads, lenses = bmesh.new(), bmesh.new()
    cuboid(heads, -0.03, 0.03, -0.24, 0.24, 1.76, 1.82)
    for side in (-1, 1):
        y = side * 0.5
        prism(heads, (0.0, y, 0.3, 0.54, 1.72), (0.0, y, 0.34, 0.58, 1.78))
        prism(heads, (0.0, y, 0.34, 0.58, 1.78), (0.0, y, 0.3, 0.54, 1.87))
        cuboid(lenses, -0.11, 0.11, y - 0.2, y + 0.2, 1.714, 1.72)
    model.hard(heads, "paint", bevel=0.008, segments=2)
    model.plain(lenses, "shade")
    return model


def girder():
    """A square's length of steel girder lying on the ground on one flange: a metre long, to meet the next."""
    model = Model("girder")
    steel = bmesh.new()
    half, high, flange, web = 0.16, 0.3, 0.03, 0.025
    cuboid(steel, -half, half, -0.5, 0.5, 0.0, flange)
    cuboid(steel, -half, half, -0.5, 0.5, high - flange, high)
    cuboid(steel, -web / 2, web / 2, -0.5, 0.5, flange, high - flange)
    model.plain(steel, "paint")
    return model


def felt_rolls(name, length, rows):
    """Rolls of roofing felt lying along y, so many in each row: long ones side by side, or short ones stacked and held by a band."""
    model = Model(name)
    radius = 0.085
    rolls, cores = bmesh.new(), bmesh.new()
    top = 0.0
    for row, count in enumerate(rows):
        for n in range(count):
            x = (n - (count - 1) / 2) * 2 * radius
            z = radius + row * radius * math.sqrt(3)
            top = z + radius
            lathe(rolls, [(0.03, -length / 2), (radius, -length / 2), (radius, length / 2), (0.03, length / 2)], segments=12, caps=False, to=along_y(x, 0.0, z))
            for y in (-length / 2, length / 2 - 0.004):
                lathe(cores, [(0.0, y), (0.03, y), (0.03, y + 0.004), (0.0, y + 0.004)], segments=8, to=along_y(x, 0.0, z))
    model.turned(rolls, "plastic", sharp=40.0)
    model.plain(cores, "rubber")
    if len(rows) > 1:
        band = bmesh.new()
        wide = rows[0] * radius + 0.004
        outline_along_y(band, [(-wide, 0.0), (wide, 0.0), (wide, radius), (radius * 0.6, top + 0.004), (-radius * 0.6, top + 0.004), (-wide, radius)], -0.05, 0.05)
        model.plain(band, "steel")
    return model


def stop_sign():
    """A stop sign on its post: an eight sided plate, red inside a white edge on the side it is read from and bare metal behind.
    The origin is the post's axis."""
    model = Model("stop_sign", centred=False)
    post = bmesh.new()
    cuboid(post, -0.02, 0.02, -0.012, 0.012, 0.0, 1.9)
    model.hard(post, "metal", bevel=0.003, segments=1)
    middle = 1.6

    def plate(bm, radius, front, back):
        outline_along_y(bm, [(x, z + middle) for x, z in arc(0.0, 0.0, radius / math.cos(math.radians(22.5)), 22.5, 337.5, 7)], front, back)

    back, edge, face = bmesh.new(), bmesh.new(), bmesh.new()
    plate(back, 0.27, -0.018, -0.012)
    model.plain(back, "metal")
    plate(edge, 0.27, -0.021, -0.018)
    model.plain(edge, "enamel")
    plate(face, 0.245, -0.023, -0.021)
    model.plain(face, "vinyl")
    return model


def hand_truck():
    """A stock trolley: a flat bed of slats near the ground on four small wheels, and at its back a frame of two uprights with
    rungs between them, each upright ending in a grip to push it by. The bed is to the front."""
    model = Model("hand_truck")
    half, back, front, bed, top = 0.25, 0.23, -0.25, 0.09, 1.05
    frame = bmesh.new()
    for side in (-1, 1):
        cuboid(frame, side * half - 0.014, side * half + 0.014, back - 0.014, back + 0.014, bed - 0.02, top)
        cuboid(frame, side * half - 0.014, side * half + 0.014, front, back, bed - 0.02, bed + 0.008)
    for n in range(4):
        z = 0.34 + n * 0.2
        cuboid(frame, -half, half, back - 0.01, back + 0.01, z - 0.012, z + 0.012)
    for n in range(5):
        y = front + n * (back - front - 0.03) / 4
        cuboid(frame, -half, half, y, y + 0.03, bed - 0.006, bed + 0.008)
    model.hard(frame, "metal", bevel=0.004, segments=1)
    dark = bmesh.new()
    for side in (-1, 1):
        beam(dark, (side * half, back, top), (side * half, back + 0.1, top + 0.03), 0.034, 0.034)
        for y in (front + 0.05, back - 0.03):
            lathe(dark, [(0.0, -0.012), (0.035, -0.012), (0.035, 0.012), (0.0, 0.012)], segments=10, to=along_x(side * (half - 0.03), y, 0.035))
    model.turned(dark, "rubber", sharp=40.0)
    return model


def roof_case(name, high):
    """The steel case of a roof's machine, a square across: sheets bolted at their corners on a dark plinth, in sections a little over a
    metre high with a seam between one and the next. What the game stands on top of one is painted at the height of its top."""
    model = Model(name)
    half, plinth = 0.485, 0.06
    sections = max(1, round(high / 1.2))
    body = bmesh.new()
    cuboid(body, -half, half, -half, half, plinth, high)
    model.hard(body, "paint", bevel=0.012, segments=2)
    dark, bolts = bmesh.new(), bmesh.new()
    cuboid(dark, -half + 0.03, half - 0.03, -half + 0.03, half - 0.03, 0.0, plinth)
    for section in range(sections):
        low = plinth + (high - plinth) * section / sections
        top = plinth + (high - plinth) * (section + 1) / sections
        if section:
            for side in (-1, 1):
                cuboid(dark, -half, half, side * half - 0.002, side * half + 0.002, low - 0.006, low + 0.006)
                cuboid(dark, side * half - 0.002, side * half + 0.002, -half, half, low - 0.006, low + 0.006)
        for side in (-1, 1):
            for across in (-1, 1):
                for z in (low + 0.08, top - 0.08):
                    cuboid(bolts, across * (half - 0.08) - 0.012, across * (half - 0.08) + 0.012, side * half - 0.004, side * half + 0.004, z - 0.012, z + 0.012)
                    cuboid(bolts, side * half - 0.004, side * half + 0.004, across * (half - 0.08) - 0.012, across * (half - 0.08) + 0.012, z - 0.012, z + 0.012)
    model.plain(dark, "plastic")
    model.plain(bolts, "metal")
    return model


TYRE_HIGH = 0.2
TYRE_ACROSS = 0.64
# The section of a car's tyre lying flat, as (radius, height): from the lip of its upper bead out over the sidewall, down
# the floor of the tread between its blocks, back under the lower sidewall to the lower lip, and then the inside of the
# casing back up to the first. The inside is hollow, so the middle is open and the casing can be seen into.
TYRE_SECTION = [(0.205, 0.185), (0.235, 0.197), (0.275, 0.2), (0.3, 0.19), (0.31, 0.172),
                (0.31, 0.15), (0.31, 0.1), (0.31, 0.05),
                (0.31, 0.028), (0.3, 0.01), (0.275, 0.0), (0.235, 0.003), (0.205, 0.015),
                (0.214, 0.027), (0.296, 0.1), (0.214, 0.173)]
# The tread: two rows of blocks round the face, a groove round the middle between them, and those of one row half a block
# on from those of the other. Each block leans along the way round, so its grooves run aslant.
TREAD_BLOCKS, TREAD_ROWS = 24, ((0.034, 0.097), (0.103, 0.166))
TREAD_FLOOR, TREAD_FACE, TREAD_FILL, TREAD_LEAN = 0.306, 0.321, 0.66, 0.35


def tyre(model, x, y, z):
    """A tyre lying flat with the middle of its foot at (x, y, z): a hollow ring of rubber with its tread in blocks."""
    steps = 20 if plain else 24
    casing = bmesh.new()
    rings = []
    for radius, high in TYRE_SECTION:
        # Without the blocks the floor of the tread is the face.
        r = TYRE_ACROSS / 2 if plain and radius == 0.31 and 0.03 < high < 0.17 else radius
        rings.append([casing.verts.new((x + r * math.cos(2 * math.pi * i / steps), y + r * math.sin(2 * math.pi * i / steps), z + high))
                      for i in range(steps)])
    for a, b in zip(rings, rings[1:] + rings[:1]):
        for i in range(steps):
            j = (i + 1) % steps
            casing.faces.new((a[i], a[j], b[j], b[i]))
    model.turned(casing, "rubber", sharp=50.0)
    if plain:
        return
    blocks = bmesh.new()
    pitch = 2 * math.pi / TREAD_BLOCKS
    for row, (low, high) in enumerate(TREAD_ROWS):
        for n in range(TREAD_BLOCKS):
            middle = (n + row / 2) * pitch
            corners = []
            for r in (TREAD_FLOOR, TREAD_FACE):
                for up, lean in ((low, -1), (high, 1)):
                    for side in (-1, 1):
                        turn = middle + (side * TREAD_FILL / 2 + lean * TREAD_LEAN / 2) * pitch
                        corners.append(blocks.verts.new((x + r * math.cos(turn), y + r * math.sin(turn), z + up)))
            # corners: inner then outer; within each the low pair then the high pair, each pair one side then the other.
            i0, i1, i2, i3, o0, o1, o2, o3 = corners
            for face in ((i0, i1, i3, i2), (o0, o2, o3, o1), (i0, o0, o1, i1), (i2, i3, o3, o2), (i0, i2, o2, o0), (i1, o1, o3, i3)):
                blocks.faces.new(face)
    model.plain(blocks, "rubber")


def tyre_stack(name, shifts):
    """Tyres lying one on another, the lowest with its middle at the origin. shifts gives each tyre from the lowest up, as
    how far it lies to the right of the lowest on the game's sprite, in that sprite's pixels, which this model's x and y
    take in equal parts: the game draws a tyre 76 of them across. Each is a body of its own, so stands on the one under it."""
    model = Model(name, centred=False)
    for n, shift in enumerate(shifts):
        off = -TYRE_ACROSS * shift / 76 / math.sqrt(2)
        tyre(model, off, off, n * TYRE_HIGH)
    return model


def tyres():
    """Car tyres lying loose: one alone, straight stacks of two, three and four, and stacks of each height as the game
    paints them thrown on less squarely, each tyre a little off the one under it."""
    return [tyre_stack("tyre", (0,)), tyre_stack("tyre_stack_2", (0, 0)), tyre_stack("tyre_stack_3", (0, 0, 0)), tyre_stack("tyre_stack_4", (0, 0, 0, 0)),
            tyre_stack("tyre_pile_2n", (0, -8.5)), tyre_stack("tyre_pile_2w", (0, 9.5)),
            tyre_stack("tyre_pile_3n", (0, -2.5, 3)), tyre_stack("tyre_pile_3w", (0, 3.5, -1)),
            tyre_stack("tyre_pile_4n", (0, 2.5, 0.5, 7)), tyre_stack("tyre_pile_4w", (0, -4, 2.5, -1.5))]


def outdoors():
    """What stands out of doors: on roofs, in yards and along streets."""
    return [duct_run(), duct_down(), duct_elbow(), roof_dome(), roof_fan(), fan_unit(), roof_stacks(), pipe_run(), pipe_corner(), culvert_pipe(),
            deck_chair(), slab_bench(), dustbin(), road_cone(), traffic_drum(), hydrant(), pallet(), brick_pallet(),
            gravestone("gravestone_round", "round"), gravestone("gravestone_square", "square"), gravestone("gravestone_gabled", "gabled"),
            sack_barrier(), log_stack("log_stack_6", (3, 2, 1)), log_stack("log_stack_5", (3, 2)),
            twin_head(), girder(), felt_rolls("felt_roll", 1.0, (2,)), felt_rolls("felt_bundle", 0.55, (2, 1)), stop_sign(), hand_truck(),
            roof_case("roof_case_low", 92 / 192 * 2.45), roof_case("roof_case_tall", 190 / 192 * 2.45)]         + tyres()


def every():
    return [bed("double_bed", 1.80, 2.00, 2), bed("single_bed", 0.90, 1.90, 1), wardrobe(), dining_chair(), small_table(),
              settee("sofa", 1.90, 2), settee("armchair", 1.02, 1), bedside_cabinet(), dresser(), desk(), office_chair(),
              cabinet("counter", 0.62, 0.90, True), cabinet("counter_sink", 0.62, 0.90, True, sink=True),
              cabinet("counter_corner", 0.62, 0.90, True, corner=True), cabinet("wall_cupboard", 0.34, 0.70, False),
              cabinet("wall_cupboard_corner", 0.34, 0.70, False, corner=True), fridge(), oven(), bookcase(), locker(), tv(), tv_wide(), tv_console(), lit_screen(),
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
              cobra_head(), wall_bowl(), wall_lantern(), bulkhead_light(), wall_sink(), serving_counter(), air_conditioner(),
              parasol("parasol", [(0.045, 2.40), (0.35, 2.25), (0.68, 2.08), (1.0, 1.91)], 0.10, 0.06),
              parasol("parasol_dome", [(0.045, 2.43), (0.24, 2.39), (0.46, 2.28), (0.66, 2.11), (0.82, 1.88), (0.92, 1.62)], 0.08, 0.06)] + indoors() + outdoors() \
        + upholstered()


def main():
    global plain, soft_whole
    models = every()
    plain = True
    plains = every()
    soft_whole = True
    firmer = every()
    plain = soft_whole = False
    data = [b"CUFM", struct.pack("<II", 3, len(models))]
    totals = [0, 0, 0, 0]
    for model, less, firm in zip(models, plains, firmer):
        if less.apart(model) > PLAIN_DETAIL:
            less = firm
        apart = less.apart(model)
        model.finish()
        less.finish(model)
        data += [model.header(apart), model.packed(), less.packed()]
        print("%-20s %5d triangles %5d vertices  plain %5d triangles %5d vertices, within %.4f  x %.3f to %.3f  y %.3f to %.3f  z %.3f to %.3f  "
              "shaped %d  sharpest shade over %.3f" % (
                  model.name, len(model.vertices) // 3, model.corners, len(less.vertices) // 3, less.corners, apart,
                  model.low.x, model.high.x, model.low.y, model.high.y, model.low.z, model.high.z, model.shaped, model.longest_shade))
        if less.longest_shade:
            raise SystemExit("the plain %s still has a sharp shade over %.3f" % (model.name, less.longest_shade))
        for i, count in enumerate((len(model.vertices) // 3, model.corners, len(less.vertices) // 3, less.corners)):
            totals[i] += count
    print("in all %d triangles %d vertices, plain %d triangles %d vertices" % tuple(totals))
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
