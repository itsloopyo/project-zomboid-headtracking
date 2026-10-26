"""Builds the mod's own furniture models and packs them for the jar.

Run headless with Blender 4.4, from the repository root:

    blender --background --factory-startup --python tools/models/furniture.py

It writes src/main/resources/models/furniture.bin.gz and prints each model's
triangle count and bounds. Every model is made here from nothing but the
figures of the gallery's sculpture, which sculpture.py carves into
sculpture.bin.gz beside this script: no mesh, texture or measurement is read
from the game.

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


def lathe(bm, profile, cx=0.0, cy=0.0, segments=20, sx=1.0, sy=1.0, closed=False, caps=True, to=None, least=6):
    """Turns a profile of (radius, height) round the upright through (cx, cy). A radius of 0 is a point on the axis.

    closed joins the last ring to the first, for a ring such as a seat; caps closes an open end with a disc; to moves
    the whole afterwards, for something turned about another axis. least is the fewest sides the plain copy keeps.
    """
    if plain:
        # As few sides as keep the widest ring within half of PLAIN_DETAIL of a circle.
        widest = max(radius for radius, _ in profile) * max(sx, sy)
        enough = math.ceil(math.pi / math.acos(max(1 - PLAIN_DETAIL / 2 / widest, 0.0)))
        segments = min(segments, max(least, enough + enough % 2))
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
    def __init__(self, name, centred=True, hung=False):
        self.name = name
        # What hangs high on a wall has no floor under it to shade its underside.
        self.hung = hung
        # A piece made to meet its neighbours, the corner of a run of counters, is placed by
        # the square it fills and keeps the origin it was built round.
        self.centred = centred
        self.vertices = []
        self.most = MOST_TRIANGLES
        self.veined = False
        # A piece too long for one square, made whole and drawn as its shares, each (name, x0, x1) placed by its own square.
        self.shares = None

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
        if not self.hung:
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
        if self.veined:
            stone = SURFACES.index("ceramic")
            self.light = [light * (1 - VEIN_DEPTH * vein(position)) if surface == stone else light
                          for (position, _, surface), light in zip(self.vertices, self.light)]
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

    def cut(self):
        """The finished model's shares, the part of it between x0 and x1 for each, moved to stand over its own middle. A triangle
        across a cut is cut in two there, the light along the cut taken between its corners, so two shares meet with one light."""
        if self.shares is None:
            return [self]
        pieces = []
        for name, x0, x1 in self.shares:
            piece = Model(name, centred=False)
            piece.vertices, piece.light, piece.seen = [], [], []
            shift = Vector(((x0 + x1) / 2, 0.0, 0.0))
            for i in range(0, len(self.vertices), 3):
                polygon = [(position, normal, surface, light, seen) for (position, normal, surface), light, seen
                           in zip(self.vertices[i:i + 3], self.light[i:i + 3], self.seen[i:i + 3])]
                for edge, keep in ((x0, lambda x: x >= x0), (x1, lambda x: x <= x1)):
                    kept = []
                    for k, one in enumerate(polygon):
                        other = polygon[(k + 1) % len(polygon)]
                        if keep(one[0].x):
                            kept.append(one)
                        if keep(one[0].x) != keep(other[0].x):
                            t = (edge - one[0].x) / (other[0].x - one[0].x)
                            kept.append((one[0].lerp(other[0], t), one[1].lerp(other[1], t).normalized(), one[2],
                                         one[3] + (other[3] - one[3]) * t, one[4] and other[4]))
                    polygon = kept
                for k in range(1, len(polygon) - 1):
                    corners = (polygon[0], polygon[k], polygon[k + 1])
                    if (corners[1][0] - corners[0][0]).cross(corners[2][0] - corners[0][0]).length < 1e-10:
                        continue
                    for position, normal, surface, light, seen in corners:
                        piece.vertices.append((position - shift, normal, surface))
                        piece.light.append(light)
                        piece.seen.append(seen)
            piece.low = Vector([min(v[0][axis] for v in piece.vertices) for axis in range(3)])
            piece.high = Vector([max(v[0][axis] for v in piece.vertices) for axis in range(3)])
            pieces.append(piece)
        return pieces

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


def chest_of_drawers(model):
    """A dresser's chest: a plinth, a carcass, a top board and three drawers with pairs of pulls. Gives its half
    width, the y of its front and its back, and the height of its top."""
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
    return half, front, back, top


def dresser():
    model = Model("dresser")
    chest_of_drawers(model)
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


def laundry_case(model):
    """The white case of a washing machine or a dryer, with the console its dials are on along the back. Gives
    its half width, the y of its front and its back, and the height of its top."""
    half, front, back, top = 0.34, -0.33, 0.33, 0.92
    body = bmesh.new()
    cuboid(body, -half, half, front, back, 0.03, top)
    cuboid(body, -half, half, back - 0.14, back, top, top + 0.16)
    model.hard(body, "enamel", bevel=0.012, segments=3)
    return half, front, back, top


def laundry_feet(model, half, front, back, top):
    """The rubber feet under a laundry case, and the rubber face of its console where the dials are set."""
    feet = bmesh.new()
    for x in (-1, 1):
        for y in (front + 0.05, back - 0.05):
            lathe(feet, [(0.0, 0.0), (0.025, 0.0), (0.025, 0.03), (0.0, 0.03)], cx=x * (half - 0.06), cy=y, segments=10)
    cuboid(feet, -half + 0.03, half - 0.03, back - 0.146, back - 0.14, top + 0.03, top + 0.13)
    model.turned(feet, "rubber")


def washing_machine():
    model = Model("washing_machine")
    half, front, back, top = laundry_case(model)

    lid = bmesh.new()
    cuboid(lid, -half + 0.05, half - 0.05, front + 0.04, back - 0.16, top, top + 0.016)
    model.hard(lid, "enamel", bevel=0.006, segments=2)

    laundry_feet(model, half, front, back, top)

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
        cuboid(box, low + 0.007, low + wide - 0.007, -half + 0.02, half - 0.02, top - 0.03, top - 0.008)
        for side in (-1, 1):
            cuboid(box, low + 0.007, low + wide - 0.007, side * (half - 0.03), side * (half - 0.008), 0.01, top - 0.03)
            cuboid(box, side * (half - 0.03), side * (half - 0.008), low + 0.007, low + wide - 0.007, 0.01, top - 0.03)
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

    # A steel bracket over each corner of the lid and of the foot, folded round both sides.
    caps = bmesh.new()
    for x in (-1, 1):
        for y in (-1, 1):
            x0, x1 = sorted((x * (half - 0.07), x * (half + 0.014)))
            y0, y1 = sorted((y * (half - 0.07), y * (half + 0.014)))
            cuboid(caps, x0, x1, y0, y1, top - 0.012, top + 0.004)
            for z0, z1 in ((top - 0.09, top), (0.0, 0.09)):
                cuboid(caps, x0, x1, y * (half + 0.004) - 0.01, y * (half + 0.004) + 0.01, z0, z1)
                cuboid(caps, x * (half + 0.004) - 0.01, x * (half + 0.004) + 0.01, y0, y1, z0, z1)
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


def sheet():
    """A sheet of paper pinned flat to a wall, a metre each way and a millimetre thick, stretched to the sheet it is."""
    model = Model("sheet")
    paper = bmesh.new()
    cuboid(paper, -0.5, 0.5, -0.0005, 0.0005, 0.0, 1.0)
    model.plain(paper, "pane")
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


def shop_wall_shelves(name, lip):
    """A shop's shelves on a wall, the wall at +Y: two deep boards on brackets, each with a lip along its front."""
    model = Model(name)
    back, deep = 0.205, 0.41
    boards, lips, brackets = bmesh.new(), bmesh.new(), bmesh.new()
    for top in (0.89, 1.58):
        cuboid(boards, -0.46, 0.46, back - deep + 0.02, back, top - 0.04, top)
        cuboid(lips, -0.46, 0.46, back - deep, back - deep + 0.02, top - 0.05, top)
        for x in (-0.34, 0.34):
            prism_y(brackets, (x, top - 0.06, 0.012, 0.03, back - deep + 0.06), (x, top - 0.16, 0.012, 0.2, back))
    model.hard(boards, "paint", bevel=0.004, segments=1)
    model.hard(lips, lip, bevel=0.003, segments=1)
    model.hard(brackets, "metal", bevel=0.002, segments=1)
    return model


def hood_dryer():
    """A salon's hood hair dryer: a five legged foot on castors, a post, the motor at its top, and on an arm out to -Y the clear
    dome that goes over a sitting head, open below."""
    model = Model("hood_dryer", centred=False)
    steel = bmesh.new()
    for n in range(5):
        a = n * 2 * math.pi / 5
        rod(steel, (0.0, 0.0, 0.08), (0.28 * math.cos(a), 0.28 * math.sin(a), 0.05), 0.018, segments=6)
        lathe(steel, [(0.0, 0.0), (0.025, 0.0), (0.025, 0.05), (0.0, 0.05)], cx=0.28 * math.cos(a), cy=0.28 * math.sin(a), segments=8)
    lathe(steel, [(0.0, 0.06), (0.03, 0.06), (0.018, 0.1), (0.018, 1.18), (0.0, 1.18)], segments=10)
    rod(steel, (0.0, -0.05, 1.4), (0.0, -0.4, 1.46), 0.02, segments=8)
    model.turned(steel, "steel", sharp=50.0)
    motor = bmesh.new()
    lathe(motor, [(0.0, 1.16), (0.08, 1.16), (0.11, 1.22), (0.11, 1.48), (0.08, 1.53), (0.0, 1.54)], segments=16)
    model.turned(motor, "enamel", sharp=40.0)
    dome = bmesh.new()
    outer = [(0.22 * math.sin(math.radians(t)), 0.22 * math.cos(math.radians(t))) for t in range(0, 95, 10)]
    inner = [(0.205 * math.sin(math.radians(t)), 0.205 * math.cos(math.radians(t))) for t in range(90, -5, -10)]
    lathe(dome, outer + inner, cy=-0.55, segments=20, to=Matrix.Translation((0.0, 0.0, 1.36)))
    model.turned(dome, "pane", sharp=60.0)
    return model


def car_wash_brush():
    """A car wash's upright roller brush: a thick round brush of bristles on its shaft, the shaft standing in a rail along X on
    the floor. The origin is the shaft's axis."""
    model = Model("car_wash_brush", centred=False)
    steel = bmesh.new()
    cuboid(steel, -0.5, 0.5, -0.07, 0.07, 0.0, 0.04)
    lathe(steel, [(0.0, 0.04), (0.04, 0.04), (0.04, 2.55), (0.0, 2.55)], segments=10)
    model.turned(steel, "metal", sharp=50.0)
    brush = bmesh.new()
    profile = [(0.0, 0.36), (0.24, 0.36), (0.3, 0.42)] + [(0.3 + 0.012 * math.sin(n * 1.7), 0.42 + n * 0.12) for n in range(1, 15)]
    profile += [(0.3, 2.19), (0.24, 2.25), (0.0, 2.25)]
    lathe(brush, profile, segments=24)
    model.soft(brush, "cloth", levels=1)
    return model


def car_wash_curtain():
    """A car wash's curtain of cloth strips hung along X from a bar, on a post from the ceiling. The origin is under the post."""
    model = Model("car_wash_curtain", centred=False)
    steel = bmesh.new()
    cuboid(steel, -0.5, 0.5, -0.03, 0.03, 2.23, 2.3)
    lathe(steel, [(0.0, 2.3), (0.025, 2.3), (0.025, 2.5), (0.0, 2.5)], segments=8)
    model.hard(steel, "steel", bevel=0.004, segments=1)
    strips = bmesh.new()
    for n in range(5):
        x0 = -0.48 + n * 0.195
        lean = 0.03 * math.sin(n * 2.3)
        prism(strips, (x0 + 0.085, 0.0, 0.17, 0.012, 2.23), (x0 + 0.085 + lean, 0.0, 0.16, 0.012, 0.25))
    model.hard(strips, "cloth", bevel=0.002, segments=1)
    return model


def bar_back():
    """The shelving behind a bar, the wall at +Y: a panelled cabinet to counter height, a tall back board with dark panels set in
    it, an open shelf between, and a cap along its top."""
    model = Model("bar_back")
    back = 0.25
    wood = bmesh.new()
    cuboid(wood, -0.46, 0.46, back - 0.05, back, 0.0, 1.9)
    cuboid(wood, -0.46, 0.46, back - 0.43, back - 0.05, 0.04, 0.78)
    cuboid(wood, -0.46, 0.46, back - 0.46, back - 0.05, 0.78, 0.82)
    cuboid(wood, -0.46, 0.46, back - 0.42, back - 0.05, 1.47, 1.52)
    cuboid(wood, -0.47, 0.47, back - 0.1, back, 1.9, 1.95)
    for x0, x1 in ((-0.4, -0.02), (0.02, 0.4)):
        cuboid(wood, x0, x1, back - 0.445, back - 0.43, 0.12, 0.7)
    model.hard(wood, "wood", bevel=0.006, segments=2)
    dark = bmesh.new()
    for z0, z1 in ((0.9, 1.42), (1.58, 1.84)):
        cuboid(dark, -0.4, 0.4, back - 0.055, back - 0.05, z0, z1)
    model.plain(dark, "dark_wood")
    plinth = bmesh.new()
    cuboid(plinth, -0.45, 0.45, back - 0.4, back - 0.05, 0.0, 0.04)
    model.hard(plinth, "dark_wood", bevel=0.003, segments=1)
    return model


def sewer_pipes(name, apart, bend):
    """Two pipes standing from the floor of a sewer to its ceiling along Y, the big one at +Y, each on a flange with collars up it.
    With bend, the small one rises to a little over a metre and turns across into the big one."""
    model = Model(name, centred=False)
    pipes = bmesh.new()
    for y, radius, top in ((apart / 2, 0.066, 2.5), (-apart / 2, 0.05, 1.3 if bend else 2.5)):
        lathe(pipes, [(0.0, 0.0), (radius + 0.04, 0.0), (radius + 0.04, 0.03), (radius, 0.035), (radius, top), (0.0, top)], cy=y, segments=14)
        for z in (0.9, 1.6):
            if z < top:
                lathe(pipes, [(radius + 0.012, z), (radius + 0.012, z + 0.06)], cy=y, segments=14)
        if not bend or radius > 0.06:
            lathe(pipes, [(radius + 0.03, top - 0.04), (radius + 0.03, top)], cy=y, segments=14)
    if bend:
        rod(pipes, (0.0, -apart / 2, 1.3), (0.0, apart / 2 - 0.05, 1.3), 0.05, segments=12)
        lathe(pipes, [(0.0, 1.22), (0.06, 1.22), (0.06, 1.36), (0.0, 1.36)], cy=-apart / 2, segments=12)
    model.turned(pipes, "metal", sharp=50.0)
    return model


def clothes_wall_rails():
    """A clothes shop's rails on its wall, the wall at +Y: two rows, each a board on the wall and a rail out in front of it on
    one bracket from a plate in the board's middle, for clothes to hang from."""
    model = Model("clothes_wall_rails")
    back = 0.16
    boards, steel = bmesh.new(), bmesh.new()
    for top in (1.02, 1.89):
        cuboid(boards, -0.45, 0.45, back - 0.03, back, top - 0.2, top)
        rail = top - 0.11
        rod(steel, (-0.47, back - 0.3, rail), (0.45, back - 0.3, rail), 0.02, segments=8)
        cuboid(steel, -0.035, 0.035, back - 0.05, back - 0.03, rail - 0.05, rail + 0.05)
        cuboid(steel, -0.012, 0.012, back - 0.3, back - 0.05, rail - 0.012, rail + 0.012)
    model.hard(boards, "wood", bevel=0.005, segments=2)
    model.turned(steel, "plastic", sharp=50.0)
    return model


def generator():
    """A portable generator in its frame: the painted body with its fuel tank on top and the cap, its sockets and switches on a
    dark panel at -Y, and the frame of round tube in an arch at each end along X, joined along the foot."""
    model = Model("generator")
    body = bmesh.new()
    cuboid(body, -0.25, 0.25, -0.17, 0.17, 0.06, 0.29)
    cuboid(body, -0.22, 0.22, -0.15, 0.15, 0.29, 0.35)
    model.hard(body, "paint", bevel=0.02, segments=2)
    dark = bmesh.new()
    cuboid(dark, -0.18, 0.12, -0.18, -0.17, 0.09, 0.25)
    lathe(dark, [(0.0, 0.35), (0.035, 0.35), (0.035, 0.38), (0.0, 0.38)], cx=-0.08, cy=0.0, segments=12)
    model.hard(dark, "plastic", bevel=0.003, segments=1)
    frame = bmesh.new()
    for x in (-0.3, 0.3):
        arch = [(x, -0.2, 0.0), (x, -0.2, 0.3)] + [(x, -0.2 * math.cos(math.radians(a)), 0.3 + 0.09 * math.sin(math.radians(a))) for a in range(30, 151, 30)]
        arch += [(x, 0.2, 0.3), (x, 0.2, 0.0)]
        for p0, p1 in zip(arch, arch[1:]):
            rod(frame, p0, p1, 0.018, segments=8)
    for y in (-0.2, 0.2):
        rod(frame, (-0.3, y, 0.02), (0.3, y, 0.02), 0.018, segments=8)
    model.turned(frame, "rubber", sharp=60.0)
    return model


def marker_ball():
    """A marker ball clamped on a power line so that low flying aircraft see it: a red sphere split around the wire. The origin
    is under its middle, and the wire runs along X through its middle."""
    model = Model("marker_ball", centred=False)
    ball = bmesh.new()
    lathe(ball, [(0.13 * math.sin(math.radians(a)), 0.13 - 0.13 * math.cos(math.radians(a))) for a in range(0, 181, 15)], segments=20)
    model.turned(ball, "vinyl", sharp=60.0)
    seam = bmesh.new()
    # A band round the ball where its halves meet, upright across the wire: turned about its own middle to stand on edge.
    lathe(seam, [(0.133, 0.115), (0.133, 0.145)], segments=20, to=Matrix.Translation((0.0, 0.0, 0.13)) @ Matrix.Rotation(math.radians(90), 4, "Y")
          @ Matrix.Translation((0.0, 0.0, -0.13)))
    model.turned(seam, "linen", sharp=60.0)
    return model


# How high a hospital's curtain rail is, in metres, and how high its curtains' hems are.
CURTAIN_RAIL_UP, CURTAIN_HEM_UP = 2.28, 0.08


def hospital_curtain(name, x0, x1, folds):
    """A hospital's curtain rail along X, hung from the ceiling on a rod in its middle, and its curtain from x0 to x1 in that many
    pleats, none where there are none: drawn along the whole rail, or bunched at one end."""
    model = Model(name, centred=False)
    steel = bmesh.new()
    cuboid(steel, -0.5, 0.5, -0.02, 0.02, CURTAIN_RAIL_UP - 0.03, CURTAIN_RAIL_UP + 0.01)
    cuboid(steel, -0.012, 0.012, -0.012, 0.012, CURTAIN_RAIL_UP + 0.01, 2.45)
    model.hard(steel, "steel", bevel=0.004, segments=1)
    if folds:
        cloth = bmesh.new()
        width = (x1 - x0) / folds
        deep = 0.05 if folds > 6 else 0.07
        # One sheet pleated in a zigzag, each pleat a slab whose ends meet its neighbours', so no light shows between them.
        pleat = [(x0 + n * width, deep if n % 2 else -deep) for n in range(folds + 1)]
        for (a, ya), (b, yb) in zip(pleat, pleat[1:]):
            outline_up(cloth, [(a, ya - 0.004), (b, yb - 0.004), (b, yb + 0.004), (a, ya + 0.004)], CURTAIN_HEM_UP, CURTAIN_RAIL_UP - 0.04)
        model.hard(cloth, "cloth", bevel=0.002, segments=1)
    return model


# How high a fireplace surround's mantel shelf is at its top, in metres: its pillars' caps are as high.
MANTEL_TOP = 1.33


def mantel_pillar():
    """A pillar of a fireplace's surround, its back on the wall at Y 0 and its front out to -Y: a plinth, a shaft that swells
    out in a corbel under its capital, and a slab on top as the mantel shelf has."""
    model = Model("mantel_pillar", centred=False)
    stone = bmesh.new()
    cuboid(stone, -0.12, 0.12, -0.26, 0.0, 0.0, 0.08)
    cuboid(stone, -0.1, 0.1, -0.22, 0.0, 0.08, 0.8)
    outline_along_x(stone, [(-0.22, 0.8), (0.0, 0.8), (0.0, 1.17), (-0.3, 1.17), (-0.3, 1.1)], -0.1, 0.1)
    cuboid(stone, -0.12, 0.12, -0.32, 0.0, 1.17, MANTEL_TOP - 0.08)
    model.hard(stone, "paint", bevel=0.006, segments=2)
    slab = bmesh.new()
    cuboid(slab, -0.14, 0.14, -0.33, 0.0, MANTEL_TOP - 0.08, MANTEL_TOP)
    model.hard(slab, "worktop", bevel=0.006, segments=2)
    return model


def mantel_lintel():
    """The middle of a fireplace's surround over the fire, a square long along X, its back on the wall at Y 0: a frieze, a
    moulding over it, and the mantel shelf on top."""
    model = Model("mantel_lintel", centred=False)
    stone = bmesh.new()
    cuboid(stone, -0.5, 0.55, -0.07, 0.0, 0.62, 1.15)
    cuboid(stone, -0.5, 0.55, -0.13, 0.0, 1.15, MANTEL_TOP - 0.08)
    model.hard(stone, "paint", bevel=0.006, segments=2)
    slab = bmesh.new()
    cuboid(slab, -0.5, 0.55, -0.2, 0.0, MANTEL_TOP - 0.08, MANTEL_TOP)
    model.hard(slab, "worktop", bevel=0.006, segments=2)
    return model


def pipe_upright(name, run):
    """A pipe standing from the floor to the ceiling, flanged where its lengths meet. With run "long" it bends at its foot and
    runs out along -Y, low over the floor on a leg, to an open flange a square away; with "stub" it bends and ends there. The
    origin is its axis."""
    model = Model(name, centred=False)
    pipe = bmesh.new()
    radius, low, bend = 0.06, 0.1, 0.16
    if not run:
        lathe(pipe, [(0.0, 0.0), (radius + 0.03, 0.0), (radius + 0.03, 0.05), (radius, 0.05), (radius, 2.5), (0.0, 2.5)], segments=14)
    else:
        lathe(pipe, [(0.0, low + bend), (radius, low + bend), (radius, 2.5), (0.0, 2.5)], segments=14)
        steps = 6
        points = [(0.0, -bend + bend * math.cos(math.radians(90 * k / steps)), low + bend - bend * math.sin(math.radians(90 * k / steps)))
                  for k in range(steps + 1)]
        for p0, p1 in zip(points, points[1:]):
            rod(pipe, p0, p1, radius, segments=14)
        end = -0.85 if run == "long" else -bend - 0.06
        rod(pipe, (0.0, -bend, low), (0.0, end, low), radius, segments=14)
        # The flange at the open end, turned about Y.
        lathe(pipe, [(radius + 0.025, 0.0), (radius + 0.025, 0.04)], segments=14,
              to=Matrix.Translation((0.0, end + 0.04, low)) @ Matrix.Rotation(math.radians(90), 4, "X"))
        rod(pipe, (0.0, -bend, 0.0), (0.0, -bend, low - radius), 0.012, segments=6)
    for z in (0.9, 1.7):
        lathe(pipe, [(radius + 0.025, z), (radius + 0.025, z + 0.05)], segments=14)
    model.turned(pipe, "metal", sharp=50.0)
    return model


# How high a cash machine's ledge with its keys is, in metres: its face leans back from there.
LEDGE = 1.15


def atm():
    """A cash machine standing against a wall at +Y: a painted cabinet whose upper half leans back from the ledge with its
    keys, the screen in the leaning face and a sign over it, and the cash slot below the ledge."""
    model = Model("atm")
    case = bmesh.new()
    outline_along_x(case, [(-0.3, 0.0), (0.3, 0.0), (0.3, 1.78), (-0.02, 1.78), (-0.3, LEDGE)], -0.27, 0.27)
    model.hard(case, "paint", bevel=0.012, segments=2)
    lean = math.atan2(1.78 - LEDGE, 0.28)
    # A plate laid on the leaning face: across X, from up to up + tall along the face, standing off it by off.
    def on_face(bm, x0, x1, up, tall, off):
        y0, z0 = -0.3 + up * math.cos(lean), LEDGE + up * math.sin(lean)
        y1, z1 = -0.3 + (up + tall) * math.cos(lean), LEDGE + (up + tall) * math.sin(lean)
        ny, nz = -math.sin(lean) * off, math.cos(lean) * off
        outline_along_x(bm, [(y0, z0), (y1, z1), (y1 + ny, z1 + nz), (y0 + ny, z0 + nz)], x0, x1)
    dark, screen = bmesh.new(), bmesh.new()
    cuboid(dark, -0.27, 0.27, -0.32, -0.3, 0.0, 0.06)
    cuboid(dark, -0.2, 0.2, -0.38, -0.3, LEDGE - 0.04, LEDGE)
    on_face(dark, -0.2, 0.2, 0.48, 0.16, 0.012)
    on_face(screen, -0.13, 0.13, 0.14, 0.26, 0.008)
    model.hard(dark, "plastic", bevel=0.004, segments=1)
    model.plain(screen, "screen")
    keys = bmesh.new()
    cuboid(keys, -0.12, 0.12, -0.37, -0.33, LEDGE, LEDGE + 0.012)
    cuboid(keys, -0.1, 0.1, -0.312, -0.3, 0.92, 0.94)
    model.hard(keys, "steel", bevel=0.002, segments=1)
    return model


def coop():
    """A chicken coop on four squares: a boarded shed on blocks, its ridge along Y and toward its -X side, and its gable end at
    -Y with a doorway and a plank door in it, under a roof of corrugated sheet that overhangs all round."""
    model = Model("coop")
    half_x, half_y, foot, eaves, ridge, ridge_x = 0.82, 0.95, 0.12, 1.25, 1.85, -0.45
    blocks = bmesh.new()
    for x in (-half_x + 0.08, half_x - 0.08):
        for y in (-half_y + 0.08, 0.0, half_y - 0.08):
            cuboid(blocks, x - 0.07, x + 0.07, y - 0.07, y + 0.07, 0.0, foot)
    model.hard(blocks, "dark_wood", bevel=0.01, segments=1)
    boards = bmesh.new()
    cuboid(boards, -half_x, half_x, -half_y, half_y, foot, foot + 0.04)
    for side in (-1, 1):
        cuboid(boards, side * half_x - 0.03, side * half_x + 0.03, -half_y, half_y, foot, eaves)
    cuboid(boards, -half_x, half_x, half_y - 0.03, half_y + 0.03, foot, eaves)
    outline_along_y(boards, [(-half_x, eaves), (half_x, eaves), (ridge_x, ridge)], half_y - 0.03, half_y + 0.03)
    # The front: boards either side of the doorway and over it, and the gable over them.
    door_x0, door_x1, door_top = -0.15, 0.35, 0.95
    cuboid(boards, -half_x, door_x0, -half_y - 0.03, -half_y + 0.03, foot, eaves)
    cuboid(boards, door_x1, half_x, -half_y - 0.03, -half_y + 0.03, foot, eaves)
    cuboid(boards, door_x0, door_x1, -half_y - 0.03, -half_y + 0.03, door_top, eaves)
    outline_along_y(boards, [(-half_x, eaves), (half_x, eaves), (ridge_x, ridge)], -half_y - 0.03, -half_y + 0.03)
    model.hard(boards, "paint", bevel=0.006, segments=1)
    door = bmesh.new()
    cuboid(door, door_x0 + 0.01, door_x1 - 0.01, -half_y - 0.05, -half_y - 0.03, foot + 0.02, door_top - 0.02)
    model.hard(door, "dark_wood", bevel=0.006, segments=1)
    roof = bmesh.new()
    over = 0.12
    for side in (-1, 1):
        run = half_x - side * ridge_x
        slope = [(side * (half_x + over), eaves - over * (ridge - eaves) / run), (ridge_x, ridge + 0.03), (ridge_x, ridge + 0.07),
                 (side * (half_x + over), eaves - over * (ridge - eaves) / run + 0.04)]
        outline_along_y(roof, slope, -half_y - over, half_y + over)
    model.hard(roof, "metal", bevel=0.004, segments=1)
    return model


def name_blade(bm, frame, x0, x1, y, z):
    """A street's name blade along X from x0 to x1 on its centre line y, its middle z high: the green plate into bm and its
    white edge into frame."""
    cuboid(bm, x0, x1, y - 0.008, y + 0.008, z - 0.07, z + 0.07)
    cuboid(frame, x0 - 0.008, x1 + 0.008, y - 0.006, y + 0.006, z - 0.078, z + 0.078)


def street_name_sign():
    """A street name sign: a post with two name blades on its top, crossed, one along X and the one under it along Y."""
    model = Model("street_name_sign", centred=False)
    post = bmesh.new()
    lathe(post, [(0.0, 0.0), (0.03, 0.0), (0.03, 2.1), (0.0, 2.1)], segments=10)
    model.turned(post, "metal", sharp=50.0)
    plates, frames = bmesh.new(), bmesh.new()
    name_blade(plates, frames, -0.38, 0.38, 0.0, 2.02)
    turned = bmesh.new()
    name_blade(turned, frames, -0.38, 0.38, 0.0, 1.85)
    bmesh.ops.rotate(turned, verts=turned.verts, cent=(0.0, 0.0, 0.0), matrix=Matrix.Rotation(math.radians(90), 3, "Z"))
    model.hard(plates, "paint", bevel=0.003, segments=1)
    model.hard(turned, "paint", bevel=0.003, segments=1)
    model.hard(frames, "enamel", bevel=0.002, segments=1)
    return model


def street_blade():
    """A street's name blade on a traffic signal's post, out along +X from a clamp round the post on two short brackets."""
    model = Model("street_blade", centred=False)
    steel = bmesh.new()
    for z in (-0.045, 0.045):
        cuboid(steel, 0.05, 0.12, -0.01, 0.01, z - 0.01, z + 0.01)
        lathe(steel, [(0.085, z - 0.015), (0.085, z + 0.015)], segments=12, caps=False)
    model.hard(steel, "steel", bevel=0.002, segments=1)
    plates, frames = bmesh.new(), bmesh.new()
    name_blade(plates, frames, 0.12, 0.82, 0.0, 0.0)
    model.hard(plates, "paint", bevel=0.003, segments=1)
    model.hard(frames, "enamel", bevel=0.002, segments=1)
    return model


# Where bar_back's cabinet top and its shelf are, in metres over its floor, and how far its bottles stand from its middle
# across: bar_back is centred, its bounds 0.02 behind its middle, so its goods are built round the same middle.
BAR_TOP, BAR_SHELF, BAR_MIDDLE = 0.82, 1.52, 0.02


def bar_bottles(name, seed, fill):
    """The bottles a bar keeps on its back shelving: a row on the cabinet's top and one on the shelf over it, fill of each
    row's length taken. Its foot is the foot of the bottles on the cabinet. Glass, green and brown, with paper labels."""
    model = Model(name, centred=False)
    pick = random.Random(seed)
    glass, dark, red, labels = bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new()
    for level in (BAR_TOP, BAR_SHELF):
        x = -0.42
        while x < 0.42:
            if pick.random() < fill:
                tall, wide = pick.uniform(0.24, 0.32), pick.uniform(0.035, 0.045)
                y = pick.uniform(0.0, 0.08) - BAR_MIDDLE
                into = pick.choice((glass, glass, dark, red))
                lathe(into, [(0.0, level), (wide, level), (wide, level + tall * 0.62), (0.014, level + tall * 0.8), (0.014, level + tall),
                             (0.0, level + tall)], cx=x, cy=y, segments=10)
                lathe(labels, [(wide + 0.002, level + tall * 0.2), (wide + 0.002, level + tall * 0.45)], cx=x, cy=y, segments=10, caps=False)
            x += 0.1
    model.turned(glass, "pane", sharp=60.0)
    model.turned(dark, "rubber", sharp=60.0)
    model.turned(red, "vinyl", sharp=60.0)
    model.turned(labels, "linen", sharp=60.0)
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


def garden_chair():
    """A moulded plastic armchair in one piece: splayed legs, a dished seat, arms that run down into the front legs and a
    back of upright slats curved round the sitter under a rounded top rail."""
    model = Model("garden_chair")
    seat = 0.4

    def behind(x, z):
        # The back curves forward at its sides and leans away from the seat as it rises.
        return 0.19 - 0.05 * (x / 0.24) ** 2 + 0.1 * (z - seat) / 0.42

    frame = bmesh.new()
    for x in (-1, 1):
        prism(frame, (x * 0.25, -0.25, 0.07, 0.06, 0.0), (x * 0.225, -0.2, 0.055, 0.05, seat))
        prism(frame, (x * 0.225, -0.2, 0.055, 0.05, seat), (x * 0.235, -0.17, 0.05, 0.045, 0.6))
        prism(frame, (x * 0.25, 0.27, 0.07, 0.06, 0.0), (x * 0.225, 0.18, 0.055, 0.05, seat))
        prism(frame, (x * 0.22, behind(0.22, seat), 0.05, 0.035, seat - 0.02), (x * 0.23, behind(0.23, 0.79), 0.05, 0.035, 0.79))
        cuboid(frame, x * 0.235 - 0.008, x * 0.235 + 0.008, -0.2, 0.17, seat - 0.075, seat - 0.02)
    cuboid(frame, -0.235, 0.235, -0.225, -0.21, seat - 0.075, seat - 0.02)
    for x in (-0.13, -0.065, 0.0, 0.065, 0.13):
        prism(frame, (x, behind(x, seat), 0.04, 0.02, seat - 0.01), (x, behind(x, 0.78), 0.04, 0.02, 0.78))
    model.hard(frame, "plastic", bevel=0.008, segments=2)
    shell = bmesh.new()
    cage = grid_box(shell, spread(-0.24, 0.24, 2), spread(-0.24, 0.2, 2), [seat - 0.025, seat])
    for (i, j, k), vert in cage.items():
        if i == 1 and j == 1:
            vert.co.z -= 0.02
        if j == 0:
            vert.co.z -= 0.015
    crease(shell, cage, 0.6)
    model.soft(shell, "plastic", levels=2)
    for x in (-1, 1):
        arm = bmesh.new()
        cage = grid_box(arm, sorted([x * 0.2, x * 0.27]), spread(-0.21, 0.19, 3), [0.585, 0.62])
        for (i, j, k), vert in cage.items():
            vert.co.z += 0.04 * j / 3
            vert.co.y += behind(0.24, vert.co.z) - 0.19 if j == 3 else 0.0
        crease(arm, cage, 0.6)
        model.soft(arm, "plastic", levels=2)
    rail = bmesh.new()
    cage = grid_box(rail, spread(-0.245, 0.245, 4), [0.0, 0.032], [0.77, 0.83])
    for (i, j, k), vert in cage.items():
        if k == 1:
            vert.co.z += 0.02 * (1 - (vert.co.x / 0.245) ** 2)
        vert.co.y += behind(vert.co.x, vert.co.z)
    crease(rail, cage, 0.4)
    model.soft(rail, "plastic", levels=2)
    return model


def patio_table():
    """A moulded plastic garden table two squares long: a top sunk inside a raised rim, a hole for a sunshade in its
    middle, a skirt under the rim and four square legs."""
    model = Model("patio_table")
    hx, hy, top, rim = 0.9, 0.45, 0.74, 0.04
    legs = bmesh.new()
    for x in (-1, 1):
        for y in (-1, 1):
            prism(legs, (x * (hx - 0.08), y * (hy - 0.08), 0.055, 0.055, 0.0), (x * (hx - 0.075), y * (hy - 0.075), 0.065, 0.065, top - 0.06))
    model.hard(legs, "plastic", bevel=0.01, segments=2)
    body = bmesh.new()
    for side in (-1, 1):
        cuboid(body, -hx, hx, side * hy - rim if side > 0 else -hy, hy if side > 0 else -hy + rim, top - 0.075, top)
        cuboid(body, *mirrored(hx - rim, hx, side), -hy + rim, hy - rim, top - 0.075, top)
    cuboid(body, -hx + rim, hx - rim, -hy + rim, hy - rim, top - 0.03, top - 0.012)
    model.hard(body, "plastic", bevel=0.008, segments=2)
    hole = bmesh.new()
    lathe(hole, [(0.0, top - 0.012), (0.024, top - 0.012), (0.024, top - 0.009), (0.0, top - 0.009)], segments=16)
    model.turned(hole, "rubber")
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
    # The window and the sign over it show the sprite's own painting of them (Furniture.VENDING_GLASS_Y is their face).
    window = bmesh.new()
    cuboid(window, -half + 0.06, half - 0.28, front - 0.008, front, 0.5, 1.5)
    cuboid(window, -half + 0.04, half - 0.04, front - 0.008, front, 1.56, 1.76)
    model.hard(window, "pane", bevel=0.004, segments=1)
    trim = bmesh.new()
    cuboid(trim, half - 0.2, half - 0.08, front - 0.01, front, 0.78, 0.82)
    cuboid(trim, half - 0.2, half - 0.08, front - 0.01, front, 1.22, 1.4)
    model.hard(trim, "metal", bevel=0.003, segments=1)
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


def ice_chest():
    """One square of a shop's ice chest, two squares long: an enamel box whose front shows the sprite's own painting of it,
    lettering and all (Furniture.ICE_FRONT_Y is that face), under a glass lid in a frame, over the white of the frost
    inside. Each square is a model, half of a chest 1.3 long and 0.62 deep, stood against the other half (Furniture.ICE_SHIFT)."""
    model = Model("ice_chest")
    half, front, back, top = 0.325, -0.31, 0.31, 0.76
    body = bmesh.new()
    cuboid(body, -half, half, front, back, 0.0, top)
    model.hard(body, "enamel", bevel=0.006, segments=1)
    face = bmesh.new()
    cuboid(face, -half, half, front - 0.004, front, 0.02, top - 0.02)
    model.hard(face, "pane", bevel=0.002, segments=1)
    frost = bmesh.new()
    cuboid(frost, -half + 0.09, half - 0.09, front + 0.09, back - 0.09, top - 0.04, top - 0.03)
    model.hard(frost, "ceramic", bevel=0.004, segments=1)
    frame = bmesh.new()
    for x0, x1, y0, y1 in ((-half + 0.05, half - 0.05, front + 0.05, front + 0.09), (-half + 0.05, half - 0.05, back - 0.09, back - 0.05),
                           (-half + 0.05, -half + 0.09, front + 0.09, back - 0.09), (half - 0.09, half - 0.05, front + 0.09, back - 0.09)):
        cuboid(frame, x0, x1, y0, y1, top, top + 0.02)
    model.hard(frame, "metal", bevel=0.004, segments=1)
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
    """A gas grill on a cabinet: two doors under the firebox, a steel hood with a bar handle, a panel of knobs, a
    slatted shelf each side and two wheels at one end."""
    model = Model("gas_barbecue")
    half, front, back = 0.3, -0.25, 0.25
    body = bmesh.new()
    cuboid(body, -half, half, front, back, 0.1, 0.72)
    cuboid(body, -half - 0.01, half + 0.01, front + 0.01, back - 0.01, 0.72, 0.86)
    for x in (-half + 0.03, half - 0.03):
        for y in (front + 0.03, back - 0.03):
            if x < 0:
                cuboid(body, x - 0.02, x + 0.02, y - 0.02, y + 0.02, 0.0, 0.1)
    model.hard(body, "rubber", bevel=0.008, segments=1)
    hood = bmesh.new()
    arch = [(front, 0.86), (front, 0.93)] + [(0.25 * -math.cos(math.radians(a)), 0.93 + 0.15 * math.sin(math.radians(a))) for a in range(20, 180, 20)] + [(back, 0.93), (back, 0.86)]
    outline_along_x(hood, arch, -half, half)
    panel = [(front - 0.012, 0.73), (front - 0.012, 0.84), (front + 0.01, 0.855), (front + 0.01, 0.73)]
    outline_along_x(hood, panel, -half + 0.01, half - 0.01)
    model.turned(hood, "steel", sharp=40.0)
    trim = bmesh.new()
    bar_handle(trim, -half + 0.06, half - 0.06, front - 0.005, 0.96, 0.978, proud=0.045, thick=0.016)
    for x in (-0.18, -0.06, 0.06, 0.18):
        lathe(trim, [(0.0, 0.0), (0.02, 0.0), (0.017, 0.02), (0.0, 0.022)], segments=12, to=along_y(x, front - 0.012, 0.785))
    for x in (-0.06, 0.06):
        cuboid(trim, x - 0.008, x + 0.008, front - 0.03, front, 0.45, 0.62)
    cuboid(trim, -0.002, 0.002, front - 0.004, front, 0.14, 0.7)
    model.turned(trim, "metal", sharp=35.0)
    shelves = bmesh.new()
    for side in (-1, 1):
        low, high = sorted((side * half, side * (half + 0.27)))
        for n in range(4):
            y = front + 0.03 + n * 0.115
            cuboid(shelves, low, high, y, y + 0.09, 0.82, 0.84)
    model.hard(shelves, "wood", bevel=0.004, segments=1)
    brackets = bmesh.new()
    for side in (-1, 1):
        low, high = sorted((side * half, side * (half + 0.25)))
        for y in (front + 0.04, back - 0.04):
            cuboid(brackets, low, high, y - 0.008, y + 0.008, 0.8, 0.82)
    model.hard(brackets, "rubber", bevel=0.002, segments=1)
    wheels = bmesh.new()
    for y in (front + 0.02, back - 0.02):
        lathe(wheels, [(0.0, -0.022), (0.075, -0.022), (0.085, -0.01), (0.085, 0.01), (0.075, 0.022), (0.0, 0.022)], segments=14,
              to=Matrix.Translation((half - 0.04, y, 0.085)) @ Matrix.Rotation(math.radians(90), 4, "X"))
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


def traffic_light(name="traffic_light", side=-1):
    """The upper length of a traffic signal's post, the game's second sprite of it: the post's top, two arms out along X, to
    the side side says, and on them a dark housing with three lenses under hoods facing -Y. The origin is the post's axis."""
    model = Model(name, centred=False)
    post = bmesh.new()
    lathe(post, [(0.0, 0.0), (0.075, 0.0), (0.075, 2.56), (0.06, 2.6), (0.0, 2.61)], segments=14)
    for z in (1.45, 2.2):
        rod(post, (0.0, 0.0, z), (side * 0.68, 0.0, z), 0.022, segments=8)
    model.turned(post, "metal", sharp=50.0)
    housing, lenses, hoods = bmesh.new(), bmesh.new(), bmesh.new()
    cuboid(housing, min(side * 0.96, side * 0.66), max(side * 0.96, side * 0.66), -0.1, 0.12, 1.38, 2.26)
    model.hard(housing, "plastic", bevel=0.02, segments=2)
    # Turned about X a profile's height runs out of the housing's face along -Y.
    to = Matrix.Translation((0.0, -0.1, 0.0)) @ Matrix.Rotation(math.radians(90), 4, "X")
    for z in (2.1, 1.82, 1.54):
        lathe(lenses, [(0.0, 0.0), (0.1, 0.0), (0.1, 0.012), (0.0, 0.012)], cx=side * 0.81, cy=z, segments=16, to=to)
        lathe(hoods, [(0.115, 0.0), (0.125, 0.0), (0.125, 0.14), (0.115, 0.14)], cx=side * 0.81, cy=z, segments=16, to=to)
    model.turned(lenses, "screen", sharp=50.0)
    model.turned(hoods, "rubber", sharp=50.0)
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


def drinking_fountain():
    """A stainless drinking fountain hung on a wall: a plate behind it, a wide shallow bowl with a flat underside, a spout
    and a drain in it, and a push button on each side. Its back is at +Y. The rim is 0.2 above the bowl's underside, the
    model's foot."""
    model = Model("drinking_fountain")
    rim = 0.2
    steel = bmesh.new()
    cuboid(steel, -0.22, 0.22, 0.2, 0.215, 0.0, rim + 0.28)
    lathe(steel, [(0.0, 0.0), (0.18, 0.0), (0.21, 0.025), (0.225, rim - 0.03), (0.22, rim), (0.195, rim), (0.17, rim - 0.05),
                  (0.06, rim - 0.065), (0.0, rim - 0.065)], cy=0.02, segments=24, sy=0.85)
    cuboid(steel, -0.21, 0.21, 0.06, 0.2, 0.0, rim)
    model.turned(steel, "steel", sharp=50.0)
    fittings = bmesh.new()
    lathe(fittings, [(0.0, rim - 0.065), (0.014, rim - 0.065), (0.012, rim - 0.02), (0.007, rim - 0.01), (0.0, rim - 0.01)], cy=0.12, segments=10)
    for side in (-1, 1):
        tube(fittings, (side * 0.2, 0.02, 0.1), (side * 0.24, 0.02, 0.1), 0.026, segments=12)
    model.turned(fittings, "metal", sharp=40.0)
    drain = bmesh.new()
    lathe(drain, [(0.0, rim - 0.063), (0.03, rim - 0.063), (0.03, rim - 0.058), (0.0, rim - 0.058)], cy=0.0, segments=14)
    model.turned(drain, "rubber")
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


# A shop's awning, a square's length of it to a model, so that the squares of a run meet: Awnings.java stands one on
# each square the game lays an awning on. How thick its cloth is, how many stripes lie on a square, and how far its
# back stands off the edge of its square, clear of the skin of the wall it hangs on (WorldScene.WALL_HALF and a hair).
AWNING_CLOTH = 0.008
AWNING_STRIPES = 4
AWNING_BACK = 0.06
# How many cuts across a stripe the foot of its valance is drawn with, and how many steps a curve of the cloth takes.
AWNING_CUTS = 8
AWNING_STEPS = 6


def awning_foot(edge, drop, across):
    """How far above the lowest point of a stripe's valance its foot is, across being 0 at one side of the stripe and 1 at
    the other: a round tab, a point, a scallop that hangs between two tabs or a straight hem."""
    middle = 2.0 * across - 1.0
    if edge == "tabs":
        return drop * (1.0 - math.sqrt(max(1.0 - middle * middle, 0.0)))
    if edge == "points":
        return drop * abs(middle)
    if edge == "scallops":
        return drop * (1.0 - math.sin(math.pi * across))
    return 0.0


def awning(name, reach, wall, front, foot, edge, drop, dome=False):
    """A length of shop awning a metre long, hung on the wall at +Y: a sheet of cloth in stripes that runs out from the
    wall and turns down at the front into a valance, and the steel that holds it, a rail on the wall and an arm under
    the middle of the cloth to a bar along the front.

    reach is how far the front is from the edge of the square the wall is on. wall is how high the cloth is where it
    meets the wall, front how high it is where it turns down, foot how high the lowest point of the valance is, and
    drop how much higher its foot is at the side of a tab or a point. A dome runs out from the wall level and curves
    down until it is upright at the front, and the others slope straight and turn down round a short curve.

    The stripes go by turns to two surfaces, so that the mod can give the cloth one colour or two. Each stripe is a
    body of its own, as is each length of rail, and those of a square meet those of the next at its edge.
    """
    model = Model(name, hung=True)
    deep = reach - AWNING_BACK
    if dome:
        profile = [(deep * math.sin(math.pi / 2 * k / (2 * AWNING_STEPS)), front + (wall - front) * math.cos(math.pi / 2 * k / (2 * AWNING_STEPS)))
                   for k in range(2 * AWNING_STEPS + 1)]
    else:
        # The front's curve: a curve drawn between the points a nose's length back along the slope and as far down
        # from the corner the slope makes with the valance.
        nose = 0.06
        run = math.hypot(deep, wall - front)
        start = (deep * (1 - nose / run), front + (wall - front) * nose / run)
        corner, end = (deep, front), (deep, front - nose)
        profile = [(0.0, wall)]
        for k in range(AWNING_STEPS + 1):
            t = k / AWNING_STEPS
            profile.append(tuple((1 - t) ** 2 * s + 2 * (1 - t) * t * c + t * t * e for s, c, e in zip(start, corner, end)))
    hem = profile[-1][1]

    def inward(points):
        """Each point of the outline of the cloth moved by its thickness to the underside: along the outline turned a
        quarter, which is down under the slope and in towards the wall behind the valance."""
        out = []
        for i, (d, z) in enumerate(points):
            (d0, z0), (d1, z1) = points[max(i - 1, 0)], points[min(i + 1, len(points) - 1)]
            length = math.hypot(d1 - d0, z1 - z0)
            out.append((d + AWNING_CLOTH * (z1 - z0) / length, z - AWNING_CLOTH * (d1 - d0) / length))
        return out

    wide = 1.0 / AWNING_STRIPES
    cloths = [bmesh.new(), bmesh.new()]
    for stripe in range(AWNING_STRIPES):
        x0 = -0.5 + stripe * wide
        outer, inner = [], []
        for cut in range(AWNING_CUTS + 1):
            across = cut / AWNING_CUTS
            x = x0 + wide * across
            low = foot + awning_foot(edge, drop, across)
            outline = profile + [(deep, hem + (low - hem) * k / 3) for k in range(1, 4)]
            for row, points in ((outer, outline), (inner, inward(outline))):
                row.append([(x, deep / 2 - d, z) for d, z in points])
        # The grids are by cut and then along the outline; shell takes them along the outline and then across.
        shell(cloths[stripe % 2], [list(column) for column in zip(*outer)], [list(column) for column in zip(*inner)])
    model.plain(cloths[0], "cloth")
    model.plain(cloths[1], "linen")

    def under(d):
        """How high the underside of the cloth is that far out from the wall."""
        for (d0, z0), (d1, z1) in zip(profile, profile[1:]):
            if d0 <= d <= d1 and d1 > d0:
                return z0 + (z1 - z0) * (d - d0) / (d1 - d0) - AWNING_CLOTH * 1.5
        raise ValueError("%.3f is not under the cloth" % d)

    frame = bmesh.new()
    back = deep / 2
    tube = 0.025
    cuboid(frame, -0.5, 0.5, back - 0.03, back, wall - 0.11, wall - 0.05)
    bar = deep - 0.07 if not dome else deep - 0.05
    bar_z = under(bar) - tube / 2 if not dome else hem - 0.02
    cuboid(frame, -0.5, 0.5, back - bar - tube / 2, back - bar + tube / 2, bar_z - tube / 2, bar_z + tube / 2)
    arm_z = wall - 0.08
    if dome:
        # Straight across the inside of the curve, from the rail to the bar.
        prism_y(frame, (0.0, bar_z, tube, tube, back - bar), (0.0, arm_z, tube, tube, back - 0.03))
    else:
        prism_y(frame, (0.0, bar_z, tube, tube, back - bar), (0.0, under(0.04) - tube / 2, tube, tube, back - 0.03))
        # A strut from low on the wall to the middle of the arm.
        brace = bar * 0.55
        prism_y(frame, (0.0, under(brace) - tube * 1.5, tube * 0.8, tube * 0.8, back - brace), (0.0, wall - min(0.5, deep * 0.9), tube * 0.8, tube * 0.8, back - 0.03))
        cuboid(frame, -0.04, 0.04, back - 0.012, back, wall - min(0.5, deep * 0.9) - 0.05, wall - min(0.5, deep * 0.9) + 0.05)
    model.plain(frame, "steel")
    return model


def awnings():
    """The shop awnings, read off the game's sprites of them: a level is 2.45 m."""
    level = 2.45
    return [awning("awning", 1.0, 0.969 * level, 0.836 * level, 0.727 * level, "tabs", 0.07),
            awning("awning_hemmed", 1.0, 0.969 * level, 0.836 * level, 0.727 * level, "straight", 0.0),
            awning("awning_pointed", 1.0, 0.969 * level, 0.836 * level, 0.727 * level, "points", 0.08),
            awning("awning_short", 0.39, 0.969 * level, 0.935 * level, 0.80 * level, "tabs", 0.07),
            awning("awning_dome", 1.0, 0.985 * level, 0.70 * level, 0.625 * level, "scallops", 0.06, dome=True)]


# A hood of roofing over a wall that stands on its own, a kiosk's: the eave row of a roof with no roof behind it, which
# Hoods.java hangs one model of a metre on each square of. How far it runs out from the wall, how thick its deck is, and
# how deep the board along its front edge, in metres. Its top is the top of the wall, its front edge as high as the game's
# sprite of the eave draws it: 1.0 and 0.874 of a level.
HOOD_REACH = 0.4
HOOD_DECK = 0.04
# How far its back is from the edge of its square: just clear of the wall's skin, so that no gap shows between them.
HOOD_BACK = 0.045
HOOD_FASCIA = 0.1225


def hood(name, plus, minus):
    """A metre of a hood of roofing on the wall at +Y: a deck that slopes from the top of the wall down to the front, a
    board along its front edge, and courses of shingles on it. plus and minus say whether the hood ends at that end of the
    model, x at +0.5 or -0.5: there it is hipped, a slope as steep as the deck's coming down from the wall to a corner
    as far past the end of the wall as the hood runs out from it. A length with no end is open there, to meet the next.
    """
    model = Model(name, centred=False, hung=True)
    level = 2.45
    top, low = 1.0 * level, 0.874 * level
    # The model's origin is on the edge of the square the wall is on, in the middle of the square's length of it; it is
    # built with its foot at the floor of the level and Model.finish lowers it to the foot of the board.
    reach = HOOD_REACH - HOOD_BACK
    far, near = 0.5 + (reach if plus else 0.0), -0.5 - (reach if minus else 0.0)
    wall_y = -HOOD_BACK

    def deck(drop):
        """The deck's upper face, or a copy of it drop lower, as its corners: the wall's top, the hips' feet at the wall, the front edge."""
        corners = {"top_minus": (-0.5, wall_y, top - drop), "top_plus": (0.5, wall_y, top - drop),
                   "front_plus": (far, wall_y - reach, low - drop), "front_minus": (near, wall_y - reach, low - drop)}
        if plus:
            corners["foot_plus"] = (far, wall_y, low - drop)
        if minus:
            corners["foot_minus"] = (near, wall_y, low - drop)
        return corners

    body = bmesh.new()
    upper = {key: body.verts.new(point) for key, point in deck(0.0).items()}
    lower = {key: body.verts.new(point) for key, point in deck(HOOD_DECK).items()}
    ring = (["foot_minus"] if minus else []) + ["top_minus", "top_plus"] + (["foot_plus"] if plus else []) + ["front_plus", "front_minus"]
    faces = [["top_minus", "top_plus", "front_plus", "front_minus"]]
    if plus:
        faces.append(["top_plus", "foot_plus", "front_plus"])
    if minus:
        faces.append(["foot_minus", "top_minus", "front_minus"])
    for face in faces:
        body.faces.new([upper[key] for key in face])
        body.faces.new([lower[key] for key in reversed(face)])
    for a, b in zip(ring, ring[1:] + ring[:1]):
        body.faces.new((upper[a], upper[b], lower[b], lower[a]))
    model.plain(body, "paint")

    board = bmesh.new()
    cuboid(board, near, far, wall_y - reach, wall_y - reach + 0.022, low - HOOD_FASCIA, low - HOOD_DECK * 0.5)
    for end, at in ((plus, far), (minus, near)):
        if end:
            side = 1.0 if at > 0 else -1.0
            cuboid(board, min(at, at - side * 0.022), max(at, at - side * 0.022), wall_y - reach + 0.022, wall_y, low - HOOD_FASCIA, low - HOOD_DECK * 0.5)
    model.hard(board, "wood", bevel=0.004, segments=1)

    # Courses of shingles on the deck, each a strip whose lower edge stands proud of the one below it, as long as the
    # deck is at that height on the slope.
    courses = bmesh.new()
    rows = 4
    for row in range(rows):
        lo, hi = row / rows, (row + 1) / rows
        def at(share):
            return wall_y - reach * share, low + (top - low) * (1.0 - share)
        (y0, z0), (y1, z1) = at(1.0 - lo), at(1.0 - hi)
        # How far past the wall's length the deck reaches at each height: nothing at the top, the hood's reach at the front.
        x_plus = lambda share: 0.5 + (reach * share if plus else 0.0)
        x_minus = lambda share: -0.5 - (reach * share if minus else 0.0)
        s0, s1 = 1.0 - lo, 1.0 - hi
        proud = 0.01
        quad = [(x_minus(s0), y0, z0 + proud), (x_plus(s0), y0, z0 + proud), (x_plus(s1), y1, z1 + 0.002), (x_minus(s1), y1, z1 + 0.002)]
        upper_course = [courses.verts.new(p) for p in quad]
        lower_course = [courses.verts.new((x, y, z - 0.002 - (proud if i < 2 else 0.0))) for i, (x, y, z) in enumerate(quad)]
        courses.faces.new(upper_course)
        courses.faces.new(lower_course[::-1])
        for i in range(4):
            j = (i + 1) % 4
            courses.faces.new((upper_course[i], upper_course[j], lower_course[j], lower_course[i]))
    model.plain(courses, "paint")
    return model


def hoods():
    """The four lengths of a hood: open at both ends, hipped at one end or the other, and hipped at both."""
    return [hood("hood", False, False), hood("hood_end_plus", True, False), hood("hood_end_minus", False, True), hood("hood_alone", True, True)]


def range_hood():
    """A length of canopy hood over a cooker, a tile wide, so that the lengths of a long one meet: a steel lip, and over
    it a canopy whose front slopes back to a flat top. Its back, flat, is at +Y, and its foot is the lower edge of the
    lip: the mod hangs it over a hob."""
    model = Model("range_hood")
    half, front, back = 0.5, -0.3, 0.3
    canopy = bmesh.new()
    cuboid(canopy, -half, half, front, back, 0.0, 0.07)
    prism(canopy, (0.0, 0.0, 2 * half, back - front, 0.07), (0.0, back - 0.15, 2 * half, 0.3, 0.36))
    model.hard(canopy, "steel", bevel=0.006, segments=2)
    filters = bmesh.new()
    for side in (-1, 1):
        cuboid(filters, side * 0.24 - 0.21, side * 0.24 + 0.21, front + 0.06, back - 0.06, -0.006, 0.0)
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


def light_switch():
    """A light switch on a wall, the wall at +Y: a plate 7 cm wide and 11.5 high, the toggle tipped up out of a slot let into its middle, a screw over and under it."""
    model = Model("light_switch", hung=True)
    face, slot_back = -0.006, -0.003
    plate = bmesh.new()
    cells = grid_box(plate, [-0.035, -0.0065, 0.0065, 0.035], [face, 0.0], [0.0, 0.044, 0.071, 0.115])
    middle = {cells[(i, 0, k)] for i in (1, 2) for k in (1, 2)}
    opening = next(f for f in plate.faces if set(f.verts) == middle)
    sunk = bmesh.ops.extrude_face_region(plate, geom=[opening])
    bmesh.ops.delete(plate, geom=[opening], context="FACES")
    walls = [v for v in sunk["geom"] if isinstance(v, bmesh.types.BMVert)]
    bmesh.ops.translate(plate, vec=(0.0, slot_back - face, 0.0), verts=walls)
    model.hard(plate, "enamel", bevel=0.0025, segments=2)
    slot = bmesh.new()
    cuboid(slot, -0.0065, 0.0065, slot_back - 0.0005, slot_back, 0.044, 0.071)
    model.plain(slot, "plastic")
    toggle = bmesh.new()
    prism_y(toggle, (0.0, 0.066, 0.0075, 0.007, -0.019), (0.0, 0.0575, 0.009, 0.016, slot_back))
    model.hard(toggle, "enamel", bevel=0.0015, segments=1)
    screws = bmesh.new()
    for z in (0.0175, 0.0975):
        lathe(screws, [(0.0, 0.0), (0.0034, 0.0), (0.003, 0.0011), (0.0, 0.0016)], segments=10, to=along_y(0.0, face, z))
    model.turned(screws, "metal", sharp=50.0)
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
    model.plain(tape, "plastic")
    return model


def battered_carton(name, width, depth, high):
    """A cardboard box knocked about: its sides bulge, its top has sunk at one corner, the flap at +Y has come loose and
    stands open, and what is left of the tape runs over the other flap and down the -X end."""
    model = Model(name)
    hx, hy = width / 2, depth / 2

    def plate(bm, quad, thick):
        a, b, d = (Vector(p) for p in (quad[0], quad[1], quad[3]))
        up = (b - a).cross(d - a).normalized() * thick
        low = [bm.verts.new(p) for p in quad]
        high = [bm.verts.new(Vector(p) + up) for p in quad]
        for i in range(4):
            j = (i + 1) % 4
            bm.faces.new((low[i], low[j], high[j], high[i]))
        bm.faces.new(low[::-1])
        bm.faces.new(high)

    corners = [(-1, -1), (1, -1), (1, 1), (-1, 1)]
    sag = {(-1, -1): 0.012, (1, -1): 0.0, (1, 1): 0.045, (-1, 1): 0.02}
    rings = [
        [(sx * hx, sy * hy, 0.0) for sx, sy in corners],
        [(sx * (hx + 0.018), sy * (hy + 0.014), high * 0.45) for sx, sy in corners],
        [(sx * (hx - 0.01), sy * (hy - 0.006), high - 0.006 - sag[(sx, sy)]) for sx, sy in corners],
    ]
    box = bmesh.new()
    verts = [[box.verts.new(p) for p in ring] for ring in rings]
    box.faces.new(verts[0][::-1])
    for low, up in zip(verts, verts[1:]):
        for i in range(4):
            j = (i + 1) % 4
            box.faces.new((low[i], low[j], up[j], up[i]))
    box.faces.new(verts[2])
    model.hard(box, "paint", bevel=0.004, segments=1)
    top = rings[2]
    flaps = bmesh.new()
    seam = [(x, 0.0, (z0 + z1) / 2) for (x, _, z0), (_, _, z1) in ((top[0], top[3]), (top[1], top[2]))]
    plate(flaps, [top[0], top[1], seam[1], seam[0]], 0.006)
    lift, reach = math.radians(125.0), hy * 0.95
    out = Vector((0.0, -math.cos(lift) * reach, math.sin(lift) * reach))
    plate(flaps, [top[3], top[2], tuple(Vector(top[2]) + out), tuple(Vector(top[3]) + out)][::-1], 0.005)
    model.hard(flaps, "paint", bevel=0.002, segments=1)
    tape = bmesh.new()
    over = Vector((0.0, 0.0, 0.0065))
    near = Vector(top[0]).lerp(Vector(seam[0]), 0.7)
    plate(tape, [tuple(near + over), tuple(near + over + Vector((hx * 1.1, 0.0, 0.0))),
                 tuple(near + over + Vector((hx * 1.1, 0.03, 0.0))), tuple(near + over + Vector((0.0, 0.03, 0.0)))], 0.002)
    middle = Vector(rings[1][0]).lerp(Vector(rings[1][3]), 0.7)
    down = Vector((-0.0025, 0.0, 0.0))
    plate(tape, [tuple(near + down + Vector((0.0, 0.03, 0.006))), tuple(near + down + Vector((0.0, 0.0, 0.006))),
                 tuple(middle + down), tuple(middle + down + Vector((0.0, 0.03, 0.0)))], 0.002)
    model.plain(tape, "plastic")
    return model


def computer():
    """A desktop computer of the nineties: the case lying flat, the tube monitor on it, the keyboard before them."""
    model = Model("computer")
    case = bmesh.new()
    cuboid(case, -0.21, 0.21, -0.12, 0.28, 0.0, 0.105)
    model.hard(case, "paint", bevel=0.006, segments=2)
    slots = bmesh.new()
    cuboid(slots, 0.04, 0.17, -0.123, -0.12, 0.06, 0.07)
    cuboid(slots, 0.04, 0.17, -0.123, -0.12, 0.032, 0.042)
    model.plain(slots, "plastic")
    tube = bmesh.new()
    lathe(tube, [(0.0, 0.105), (0.11, 0.105), (0.12, 0.115), (0.07, 0.14), (0.0, 0.14)], cy=0.06, segments=14)
    cuboid(tube, -0.185, 0.185, -0.1, 0.02, 0.14, 0.46)
    prism_y(tube, (0.0, 0.3, 0.36, 0.31, 0.02), (0.0, 0.29, 0.22, 0.2, 0.27))
    model.hard(tube, "paint", bevel=0.012, segments=2)
    glass = bmesh.new()
    cuboid(glass, -0.15, 0.15, -0.104, -0.1, 0.185, 0.425)
    model.hard(glass, "screen", bevel=0.01, segments=2)
    keys = bmesh.new()
    prism(keys, (0.0, -0.3, 0.44, 0.16, 0.0), (0.0, -0.295, 0.44, 0.15, 0.022))
    model.hard(keys, "paint", bevel=0.004, segments=1)
    caps = bmesh.new()
    cuboid(caps, -0.2, 0.2, -0.36, -0.235, 0.022, 0.027)
    model.plain(caps, "linen")
    return model


def water_cooler():
    """A water dispenser: a cabinet with two taps over a drip tray, and the bottle upside down on top."""
    model = Model("water_cooler")
    body = bmesh.new()
    cuboid(body, -0.16, 0.16, -0.16, 0.16, 0.0, 0.98)
    model.hard(body, "paint", bevel=0.012, segments=2)
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
    half, front, back, top = chest_of_drawers(model)
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
    half, front, back, top = laundry_case(model)
    door = bmesh.new()
    lathe(door, [(0.15, 0.0), (0.215, 0.0), (0.215, 0.02), (0.2, 0.034), (0.15, 0.034)], segments=22, closed=True, to=along_y(0.0, front, 0.5))
    model.turned(door, "enamel", sharp=40.0)
    glass = bmesh.new()
    lathe(glass, [(0.0, 0.0), (0.15, 0.0), (0.15, 0.012), (0.09, 0.026), (0.0, 0.03)], segments=18, to=along_y(0.0, front, 0.5))
    model.turned(glass, "screen", sharp=50.0)
    laundry_feet(model, half, front, back, top)
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


# A cake shop's display case, two squares long: its numbers in metres from the middle of its footprint, its front to -Y.
# DisplayCases.java lays the glass of the case where these say it is (a model is solid, and glass is not), and stands
# the cakes on its deck and its shelf: the two must give the same numbers.
CASE_HALF, CASE_FRONT, CASE_BACK, CASE_TOP, CASE_UNDER = 0.98, -0.37, 0.37, 0.97, 0.94
CASE_ARC_FOOT, CASE_CURVE_Y, CASE_KICK, CASE_DECK, CASE_SHELF, CASE_SHELF_FRONT, CASE_END = 0.45, 0.0, 0.08, 0.30, 0.62, -0.22, 0.03
CASE_ARC_STEPS = 12


def bakery_case():
    """A glass-fronted counter for cakes: solid ends whose front edge rises straight from the floor and then curves back
    to the top, a top board over the back half, a deck over a low cupboard, a glass shelf on steel bars, and sliding
    doors at the back. The glass is not in the model: the mod lays it in the world, where it is drawn as glass."""
    model = Model("bakery_case")
    inner = CASE_HALF - CASE_END
    rise, deep = CASE_UNDER - CASE_ARC_FOOT, CASE_CURVE_Y - CASE_FRONT
    outline = [(CASE_BACK, 0.0), (CASE_FRONT, 0.0)]
    outline += [(CASE_CURVE_Y - deep * math.cos(math.pi / 2 * i / CASE_ARC_STEPS), CASE_ARC_FOOT + rise * math.sin(math.pi / 2 * i / CASE_ARC_STEPS))
                for i in range(CASE_ARC_STEPS + 1)]
    outline += [(CASE_BACK, CASE_UNDER)]
    ends = bmesh.new()
    for side in (-1, 1):
        outline_along_x(ends, outline, side * inner, side * CASE_HALF)
    model.hard(ends, "wood", bevel=0.006, segments=2)
    body = bmesh.new()
    cuboid(body, -inner, inner, CASE_FRONT, CASE_FRONT + 0.04, 0.0, CASE_KICK)
    cuboid(body, -inner, inner, CASE_FRONT + 0.04, CASE_BACK - 0.02, 0.0, CASE_DECK - 0.015)
    # The back's frame: a rail under the top, one over the deck and a stile where the two doors overlap.
    cuboid(body, -inner, inner, CASE_BACK - 0.04, CASE_BACK, CASE_UNDER - 0.05, CASE_UNDER)
    cuboid(body, -inner, inner, CASE_BACK - 0.04, CASE_BACK, CASE_DECK - 0.015, CASE_DECK + 0.04)
    cuboid(body, -0.02, 0.02, CASE_BACK - 0.04, CASE_BACK, CASE_DECK + 0.04, CASE_UNDER - 0.05)
    model.hard(body, "wood", bevel=0.004, segments=1)
    top = bmesh.new()
    cuboid(top, -CASE_HALF, CASE_HALF, CASE_CURVE_Y - 0.03, CASE_BACK, CASE_UNDER, CASE_TOP)
    model.hard(top, "worktop", bevel=0.006, segments=2)
    deck = bmesh.new()
    cuboid(deck, -inner, inner, CASE_FRONT + 0.04, CASE_BACK - 0.04, CASE_DECK - 0.015, CASE_DECK)
    model.plain(deck, "enamel")
    # The glass shelf's nosing and the bars it rests on, along its ends and its back.
    shelf = bmesh.new()
    cuboid(shelf, -inner, inner, CASE_SHELF_FRONT - 0.006, CASE_SHELF_FRONT + 0.006, CASE_SHELF - 0.014, CASE_SHELF + 0.004)
    cuboid(shelf, -inner, inner, CASE_BACK - 0.06, CASE_BACK - 0.04, CASE_SHELF - 0.02, CASE_SHELF)
    for side in (-1, 1):
        cuboid(shelf, min(side * inner, side * (inner - 0.02)), max(side * inner, side * (inner - 0.02)), CASE_SHELF_FRONT, CASE_BACK - 0.06,
               CASE_SHELF - 0.02, CASE_SHELF)
    model.hard(shelf, "steel", bevel=0.002, segments=1)
    lamp = bmesh.new()
    cuboid(lamp, -inner + 0.04, inner - 0.04, 0.04, 0.08, CASE_UNDER - 0.022, CASE_UNDER)
    model.plain(lamp, "plastic")
    return model


def sphere(bm, x, y, z, radius, segments=8, squash=1.0):
    """A ball round (x, y, z), squash times as high as it is wide."""
    rings = max(segments // 2, 3)
    lathe(bm, [(radius * math.sin(math.pi * i / rings), z - radius * squash * math.cos(math.pi * i / rings)) for i in range(rings + 1)],
          cx=x, cy=y, segments=segments)


def torus(bm, x, y, z, ring, tube, segments=16, start=0.0, end=360.0, steps=8):
    """A ring: a circle of radius tube swept round the upright at radius ring. From start to end of the circle only, for
    the icing on a doughnut."""
    whole = end - start >= 360.0
    profile = [(ring + tube * math.cos(math.radians(start + (end - start) * i / steps)), z + tube * math.sin(math.radians(start + (end - start) * i / steps)))
               for i in range(steps if whole else steps + 1)]
    lathe(bm, profile, cx=x, cy=y, segments=segments, closed=whole, caps=False)


class Bake:
    """The cakes of one model, a part for each thing they are made of: crust, jam, cream, sponge, chocolate, the tin
    they stand on and the paper of a cupcake."""
    def __init__(self):
        self.parts = {made: bmesh.new() for made in ("wood", "vinyl", "pillow", "linen", "dark_wood", "steel", "ceramic")}

    def pie(self, x, y, z, radius, lattice=True):
        lathe(self.parts["wood"], [(0.0, z), (radius * 0.86, z), (radius, z + 0.03), (radius * 1.02, z + 0.038), (radius * 0.9, z + 0.042),
                                   (radius * 0.86, z + 0.03), (0.0, z + 0.03)], cx=x, cy=y, segments=14)
        lathe(self.parts["vinyl"], [(radius * 0.88, z + 0.03), (radius * 0.6, z + 0.036), (0.0, z + 0.037)], cx=x, cy=y, segments=14, caps=False)
        if lattice:
            for i in (-1, 0, 1):
                along = radius * 0.5 * i
                reach = math.sqrt(max(radius * radius * 0.78 - along * along, 0.0))
                beam(self.parts["wood"], (x + along, y - reach, z + 0.041), (x + along, y + reach, z + 0.041), 0.016, 0.008)
                beam(self.parts["wood"], (x - reach, y + along, z + 0.046), (x + reach, y + along, z + 0.046), 0.016, 0.008)

    def cake(self, x, y, z, radius, high, chocolate=False):
        lathe(self.parts["dark_wood" if chocolate else "linen"], [(0.0, z), (radius, z), (radius, z + high), (0.0, z + high)], cx=x, cy=y, segments=16)
        lathe(self.parts["dark_wood" if chocolate else "pillow"], [(radius * 1.015, z + high - 0.012), (radius * 1.02, z + high + 0.004),
                                                                   (radius * 0.9, z + high + 0.012), (0.0, z + high + 0.014)], cx=x, cy=y, segments=16, caps=False)
        for i in range(6):
            turn = 2 * math.pi * i / 6
            if chocolate:
                sphere(self.parts["pillow"], x + radius * 0.78 * math.cos(turn), y + radius * 0.78 * math.sin(turn), z + high + 0.022, 0.012, segments=6, squash=0.8)
            else:
                sphere(self.parts["vinyl"], x + radius * 0.75 * math.cos(turn), y + radius * 0.75 * math.sin(turn), z + high + 0.024, 0.011, segments=6)
        sphere(self.parts["vinyl"], x, y, z + high + 0.026, 0.014, segments=6)

    def tray(self, x, y, z, wide, deep):
        cuboid(self.parts["steel"], x - wide / 2, x + wide / 2, y - deep / 2, y + deep / 2, z, z + 0.008)
        return z + 0.008

    def doughnuts(self, x, y, z, glaze):
        top = self.tray(x, y, z, 0.2, 0.19)
        for dx, dy in ((-0.05, -0.045), (0.05, -0.045), (0.0, 0.05)):
            torus(self.parts["wood"], x + dx, y + dy, top + 0.017, 0.028, 0.017, segments=10, steps=6)
            torus(self.parts[glaze], x + dx, y + dy, top + 0.019, 0.028, 0.0175, segments=10, start=-10.0, end=190.0, steps=4)

    def cupcakes(self, x, y, z, count):
        top = self.tray(x, y, z, 0.2, 0.17)
        across = (count + 1) // 2
        for i in range(count):
            cx, cy = x + 0.055 * (i % across - (across - 1) / 2), y - 0.04 + 0.08 * (i // across)
            lathe(self.parts["ceramic"], [(0.0, top), (0.026, top), (0.033, top + 0.038), (0.0, top + 0.038)], cx=cx, cy=cy, segments=8)
            lathe(self.parts["wood"], [(0.035, top + 0.036), (0.034, top + 0.05), (0.022, top + 0.062), (0.0, top + 0.066)], cx=cx, cy=cy, segments=8, caps=False)
            lathe(self.parts["pillow"], [(0.027, top + 0.058), (0.02, top + 0.075), (0.008, top + 0.088), (0.0, top + 0.09)], cx=cx, cy=cy, segments=8, caps=False)
            sphere(self.parts["vinyl"], cx, cy, top + 0.096, 0.008, segments=6)

    def cookies(self, x, y, z):
        top = self.tray(x, y, z, 0.2, 0.17)
        for i, (dx, dy) in enumerate(((-0.05, -0.035), (0.045, -0.04), (-0.04, 0.045), (0.05, 0.04))):
            for layer in range(1 + i % 2):
                lathe(self.parts["wood"], [(0.0, top + 0.012 * layer), (0.036, top + 0.012 * layer), (0.038, top + 0.012 * layer + 0.006),
                                           (0.034, top + 0.012 * layer + 0.011), (0.0, top + 0.012 * layer + 0.012)],
                      cx=x + dx + 0.004 * layer, cy=y + dy - 0.003 * layer, segments=10)

    def croissants(self, x, y, z):
        top = self.tray(x, y, z, 0.2, 0.17)
        for dy in (-0.04, 0.045):
            # A crescent of swellings, fattest in the middle, each overlapping the next.
            for k in range(7):
                bend = math.radians(-75 + 25 * k)
                thick = 0.025 - 0.005 * abs(k - 3)
                sphere(self.parts["wood"], x + 0.05 * math.sin(bend), y + dy - 0.03 * math.cos(bend) + 0.02, top + thick * 0.75, thick, segments=8, squash=0.75)

    def into(self, model):
        for made, part in self.parts.items():
            if part.verts:
                model.turned(part, made, sharp=50.0)
            else:
                part.free()


def pastries(name, seed, on_deck, on_shelf):
    """The cakes the game paints over one square of a case while it holds something, made: on_deck and on_shelf are how
    many of the places on each hold a cake. The model's middle is the middle of the square along the case and of the case
    across it, and its lowest point is the deck's top, where the mod stands it."""
    rng = random.Random(seed)
    model = Model(name, centred=False)
    bake = Bake()
    deck_places = [(x, y) for y in (-0.15, 0.16) for x in (-0.33, -0.11, 0.11, 0.33)]
    shelf_places = [(x, 0.06) for x in (-0.33, -0.11, 0.11, 0.33)]
    big = [lambda x, y, z: bake.pie(x, y, z, 0.1), lambda x, y, z: bake.cake(x, y, z, 0.095, 0.085),
           lambda x, y, z: bake.cake(x, y, z, 0.09, 0.09, chocolate=True), lambda x, y, z: bake.pie(x, y, z, 0.1, lattice=False),
           lambda x, y, z: bake.doughnuts(x, y, z, "pillow"), lambda x, y, z: bake.cupcakes(x, y, z, 4)]
    small = [lambda x, y, z: bake.doughnuts(x, y, z, "dark_wood"), lambda x, y, z: bake.doughnuts(x, y, z, "vinyl"),
             lambda x, y, z: bake.cupcakes(x, y, z, 3), lambda x, y, z: bake.cookies(x, y, z), lambda x, y, z: bake.croissants(x, y, z),
             lambda x, y, z: bake.pie(x, y, z, 0.075, lattice=False)]
    for places, count, kinds, z in ((deck_places, on_deck, big, CASE_DECK), (shelf_places, on_shelf, small, CASE_SHELF)):
        for x, y in sorted(rng.sample(places, count)):
            rng.choice(kinds)(x, y, z)
    bake.into(model)
    low = min(position.z for position, _, _ in model.vertices)
    if abs(low - CASE_DECK) > 1e-4:
        raise SystemExit("%s does not stand on the case's deck: its lowest point is at %f" % (name, low))
    return model


def display_cases():
    """A cake shop's case and the cakes the game shows on its shelves: the pieces DisplayCases.java places."""
    return [bakery_case(), pastries("pastries_many_a", 31, 8, 4), pastries("pastries_many_b", 32, 8, 4),
            pastries("pastries_few_a", 33, 2, 2), pastries("pastries_few_b", 34, 3, 1)]


def indoors():
    """What stands in rooms and hangs on their walls: the pieces Indoors.java places."""
    return [range_hood(), garbage_can(), waste_basket(), louvre("wall_vent", 0.56, 0.42, "plastic", 5), louvre("wall_grille", 0.64, 0.34, "enamel", 7, deep=0.03),
            round_vent(), exit_sign(), sconce(), pedestal_sink(), medicine_cabinet(), hand_dryer(), light_switch(), coat_rack(),
            filing_cabinet(), carton("carton", 0.58, 0.58, 0.56), carton("carton_small", 0.44, 0.34, 0.3), battered_carton("carton_battered", 0.44, 0.34, 0.3), computer(), water_cooler(),
            floor_lamp("torchiere", False), floor_lamp("standard_lamp", True), dresser_mirror(), side_table(), media_stand(), bar_stool(), clothes_dryer(),
            plank_bench(), pew(), bunk_bed("bunk_bed", "dark_wood"), bunk_bed("steel_bunk_bed", "metal"),
            cabinet("steel_counter", 0.62, 0.90, True, made="steel", top_of="steel"),
            cabinet("steel_counter_sink", 0.62, 0.90, True, sink=True, made="steel", top_of="steel"),
            cabinet("steel_counter_corner", 0.62, 0.90, True, corner=True, made="steel", top_of="steel"),
            wall_shelf("wire_wall_shelves", 2, made="plastic"), school_desk()] + restaurant_seating() + [pool_table()]


# A pool table's rails: the outer edge and the inner face the cushions stand against, half along x and half along y, the
# rail's foot and top, and where a pocket's leather lies on it. The cushion noses stand POOL_NOSE in from the rails.
POOL_RAIL_OUT, POOL_RAIL_IN, POOL_RAIL_LOW, POOL_RAIL_TOP, POOL_RIM = (0.96, 0.48), (0.86, 0.38), 0.77, 0.842, 0.845
POOL_NOSE = 0.036


class PoolPocket:
    """One pocket of a pool table, in the quarter where x and y are positive: the cushion noses either side of its mouth,
    the throat where the cushions meet the rails, and the round cut through the rail behind it, through both ends of
    the throat. The cloth reaches to the throat and drops away behind it."""
    def __init__(self, noses, throat, centre):
        self.noses, self.throat, self.centre = noses, throat, centre
        self.radius = math.hypot(throat[0][0] - centre[0], throat[0][1] - centre[1])

    def angles(self):
        """The angles of the throat's ends from the cut's middle, the first more than the second by the way round the back."""
        (x1, y1), (x2, y2) = self.throat
        start = math.degrees(math.atan2(y1 - self.centre[1], x1 - self.centre[0]))
        end = math.degrees(math.atan2(y2 - self.centre[1], x2 - self.centre[0]))
        while end > start:
            end -= 360.0
        return start, end

    def cut(self, radius, steps):
        """The round of the cut through the rail at radius, from the first end of the throat round the back to the other."""
        return arc(*self.centre, radius, *self.angles(), steps)


def pool_pockets():
    """The corner pocket's mouth is 0.112 between its noses (1.9 balls once the game stands the table), its throat 0.098 and
    its cut 0.06 round; a side pocket's mouth is 0.13 (2.2 balls), narrowing to a throat of 0.112, cut 0.057 round."""
    (ix, iy), nx, ny = POOL_RAIL_IN, POOL_RAIL_IN[0] - POOL_NOSE, POOL_RAIL_IN[1] - POOL_NOSE
    mouth, throat = 0.112 / math.sqrt(2), 0.098 / math.sqrt(2)
    back = 0.0245
    corner = PoolPocket(((nx - mouth, ny), (nx, ny - mouth)), ((ix - throat, iy), (ix, iy - throat)),
                        (ix - throat / 2 + back, iy - throat / 2 + back))
    side = PoolPocket(((-0.065, ny), (0.065, ny)), ((-0.056, iy), (0.056, iy)), (0.0, iy + 0.012))
    return corner, side


def jawed(bm, ends):
    """A cushion: its outline, given going round as pairs of points where each line of it starts and ends, joined
    along and closed at both ends. Each end is a flat cut, so a cushion can end on the slant of a pocket's jaw."""
    first = [bm.verts.new(start) for start, _ in ends]
    last = [bm.verts.new(end) for _, end in ends]
    count = len(ends)
    for i in range(count):
        j = (i + 1) % count
        bm.faces.new((first[i], first[j], last[j], last[i]))
    bm.faces.new(first[::-1])
    bm.faces.new(last)


def pool_liner(bm, pocket, sx, sy):
    """A pocket's leather: a band round the cut lying on the rail and down its face, along the throat under the cloth's
    edge, and a dark floor across the drop."""
    (x1, y1), (x2, y2) = pocket.throat
    length = math.hypot(x2 - x1, y2 - y1)
    # Across the throat towards the cut.
    nx, ny = -(y2 - y1) / length, (x2 - x1) / length
    if (pocket.centre[0] - x1) * nx + (pocket.centre[1] - y1) * ny < 0:
        nx, ny = -nx, -ny
    along = [(x2 + (x1 - x2) * k / 6, y2 + (y1 - y2) * k / 6) for k in range(1, 6)]
    inner = pocket.cut(pocket.radius - 0.002, 16) + [(x + nx * 0.003, y + ny * 0.003) for x, y in along]
    outer = pocket.cut(pocket.radius + 0.008, 16) + [(x - nx * 0.008, y - ny * 0.008) for x, y in along]
    tops = [POOL_RIM] * 17 + [0.797] * 5
    low = 0.768
    rings = [[bm.verts.new((sx * x, sy * y, z)) for (x, y), z in zip(points, heights)]
             for points, heights in ((inner, [low] * 22), (inner, tops), (outer, tops), (outer, [low] * 22))]
    count = len(inner)
    for i in range(count):
        j = (i + 1) % count
        for a, b in ((0, 1), (1, 2), (2, 3), (3, 0)):
            bm.faces.new((rings[a][i], rings[a][j], rings[b][j], rings[b][i]))
    outline_up(bm, [(sx * x, sy * y) for x, y in inner], low - 0.003, low + 0.001)


def pool_table():
    """A pool table two squares long: a cloth bed inside cushioned rails, six pockets each cut round through the rail
    behind the cushions' jaws and lined with leather, a deep apron and four square legs. Width along x. The cloth is
    0.80 high and the rails 0.842."""
    model = Model("pool_table")
    (ox, oy), (ix, iy) = POOL_RAIL_OUT, POOL_RAIL_IN
    corner, side = pool_pockets()

    # The bed of slate under its cloth, reaching under the cushions to the rails, each corner cut off across the
    # corner pocket's throat.
    bed = bmesh.new()
    (cx, _), (_, cy) = corner.throat
    xs = [-ix, -cx] + spread(-0.6, 0.6, 4) + [cx, ix]
    ys = [-iy, -cy] + spread(-0.2, 0.2, 2) + [cy, iy]
    cage = grid_box(bed, xs, ys, [0.74, 0.8])
    for (i, j, k), vert in cage.items():
        if i in (0, len(xs) - 1) and j in (0, len(ys) - 1):
            vert.co.x = math.copysign((ix + cx) / 2, vert.co.x)
            vert.co.y = math.copysign((iy + cy) / 2, vert.co.y)
    model.hard(bed, "cloth", bevel=0.003, segments=1)

    # The cushions along the rails, in the same cloth: a nose that overhangs the bed, each end cut on the slant from the
    # nose at the pocket's mouth back to its throat. The nose is 0.034 over the cloth, 37 mm once the game raises the
    # cloth to its sprite's top: a real cushion meets a 57 mm ball at 63.5% of its height.
    cushions = bmesh.new()
    nose = [(0.0, 0.795), (0.028, 0.795), (POOL_NOSE, 0.834), (0.031, 0.839), (0.0, 0.84)]

    def jaw(pocket, end, inward):
        """Where a cushion's line inward of the rail ends at a pocket: from the throat's end towards the nose's."""
        back, front = pocket.throat[end], pocket.noses[end]
        t = inward / POOL_NOSE
        return back[0] + (front[0] - back[0]) * t, back[1] + (front[1] - back[1]) * t

    for sx in (-1, 1):
        for sy in (-1, 1):
            jawed(cushions, [((sx * jaw(side, 1, w)[0], sy * (iy - w), z), (sx * jaw(corner, 0, w)[0], sy * (iy - w), z)) for w, z in nose])
        jawed(cushions, [((sx * (ix - w), -jaw(corner, 1, w)[1], z), (sx * (ix - w), jaw(corner, 1, w)[1], z)) for w, z in nose])
    model.hard(cushions, "cloth", bevel=0.004, segments=2)

    # The rails, cut round each pocket: a half for each end of the table, joined into one ring behind the side pockets
    # so that no joint is rounded off there.
    rails = bmesh.new()
    top = side.cut(side.radius, 12)
    top = top[len(top) // 2:] + corner.cut(corner.radius, 16) + [(ix, 0.0)]
    inside = top + [(x, -y) for x, y in reversed(top)][1:]
    half = inside + [(0.0, -oy), (ox, -oy), (ox, oy), (0.0, oy)]
    for sx in (-1, 1):
        outline_up(rails, [(sx * x + 0.0, y) for x, y in half], POOL_RAIL_LOW, POOL_RAIL_TOP)
    bmesh.ops.remove_doubles(rails, verts=rails.verts[:], dist=1e-6)
    joints = [face for face in rails.faces if all(abs(vert.co.x) < 1e-6 for vert in face.verts)]
    if len(joints) != 2:
        raise SystemExit("pool_table: the rail halves meet in %d faces, not 2" % len(joints))
    bmesh.ops.delete(rails, geom=joints, context="FACES_ONLY")
    model.hard(rails, "dark_wood", bevel=0.006, segments=2)

    # The apron under the rails, and the legs.
    body = bmesh.new()
    cuboid(body, -0.93, 0.93, -0.45, 0.45, 0.6, 0.765)
    cuboid(body, -0.94, 0.94, -0.46, 0.46, 0.595, 0.63)
    for sx in (-1, 1):
        for sy in (-1, 1):
            prism(body, (sx * 0.8, sy * 0.34, 0.11, 0.11, 0.045), (sx * 0.8, sy * 0.34, 0.14, 0.14, 0.62))
            cuboid(body, sx * 0.8 - 0.08, sx * 0.8 + 0.08, sy * 0.34 - 0.08, sy * 0.34 + 0.08, 0.0, 0.05)
    model.hard(body, "dark_wood", bevel=0.006, segments=2)

    liners = bmesh.new()
    for sy in (-1, 1):
        for sx in (-1, 1):
            pool_liner(liners, corner, sx, sy)
        pool_liner(liners, side, 1, sy)
    model.turned(liners, "rubber", sharp=50.0)
    return model


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
    """A galvanised dustbin: a can corrugated top to bottom between a band at its foot and a rolled rim, two bail handles,
    and a domed lid pressed with ribs from its middle out, with a loop handle of its own."""
    model = Model("dustbin")
    flutes, deep = 16, 0.014
    can = bmesh.new()
    heights = [0.055, 0.065, 0.075, 0.09] + [0.09 + 0.43 * k / 8 for k in range(1, 8)] + [0.52, 0.535, 0.545, 0.555, 0.565]
    wall = [(0.204 + 0.032 * (z - 0.055) / 0.51, z) for z in heights]
    lathe(can, [(0.0, 0.0), (0.2, 0.0), (0.21, 0.01), (0.21, 0.045)] + wall + [(0.243, 0.575), (0.243, 0.59), (0.252, 0.6), (0.252, 0.625),
                                                                              (0.0, 0.625)], segments=64)
    if not plain:
        for vert in can.verts:
            z = vert.co.z
            if 0.06 < z < 0.56:
                rise, fall = min((z - 0.055) / 0.035, 1.0), min((0.565 - z) / 0.035, 1.0)
                swell = rise * rise * (3 - 2 * rise) * fall * fall * (3 - 2 * fall)
                wave = math.cos(flutes * math.atan2(vert.co.y, vert.co.x))
                out = 1.0 + deep * swell * math.copysign(abs(wave) ** 0.5, wave) / math.hypot(vert.co.x, vert.co.y)
                vert.co.x *= out
                vert.co.y *= out
    model.turned(can, "steel", sharp=50.0)
    lid = bmesh.new()
    lathe(lid, [(0.0, 0.625), (0.262, 0.625), (0.264, 0.652), (0.255, 0.662), (0.22, 0.676), (0.16, 0.698), (0.09, 0.714), (0.0, 0.72)],
          segments=64)
    if not plain:
        for vert in lid.verts:
            r = math.hypot(vert.co.x, vert.co.y)
            if 0.06 < r < 0.25 and vert.co.z > 0.66:
                vert.co.z += 0.006 * math.sin(math.pi * (r - 0.06) / 0.19) * max(math.cos(12 * math.atan2(vert.co.y, vert.co.x)), 0.0)
    model.turned(lid, "steel", sharp=50.0)
    wire = bmesh.new()
    loop = [(-0.06, 0.712), (-0.055, 0.745), (-0.035, 0.76), (0.035, 0.76), (0.055, 0.745), (0.06, 0.712)]
    for (x0, z0), (x1, z1) in zip(loop, loop[1:]):
        rod(wire, (x0, 0.0, z0), (x1, 0.0, z1), 0.007, segments=8)
    for side in (-1, 1):
        for way in (-1, 1):
            rod(wire, (side * 0.232, way * 0.05, 0.55), (side * 0.28, way * 0.04, 0.5), 0.006, segments=8)
        rod(wire, (side * 0.28, -0.046, 0.5), (side * 0.28, 0.046, 0.5), 0.006, segments=8)
    model.turned(wire, "steel", sharp=40.0)
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


# How high one layer of a stack of logs is, in metres: the game paints a layer laid on another this far up, whether it is of five logs or six.
LOG_LAYER = 34 / 192 * 2.45


def log_stack(name, rows, shifts=None, radius=None, half=0.92):
    """Logs stacked along y, each row lying in the hollows of the row under it, moved along by shifts[row] radii: bark, and the pale
    wood of the sawn ends. Unless a radius is given the logs are as thick as makes the stack a layer high."""
    model = Model(name)
    if radius is None:
        radius = LOG_LAYER / (2 + (len(rows) - 1) * math.sqrt(3))
    bark, ends = bmesh.new(), bmesh.new()
    random.seed(len(name))
    for row, count in enumerate(rows):
        for n in range(count):
            x = (n - (count - 1) / 2 + (shifts[row] if shifts else 0) / 2) * 2 * radius
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


def log_bundle():
    """A load of long logs lying along y, three rows of three, each row in the hollows of the one under it, with two steel straps round
    the lot: three squares long and two across."""
    rows, shifts, radius, half = (3, 3, 3), (0, 1, 0), 0.26, 1.42
    model = log_stack("log_bundle", rows, shifts, radius, half)
    centres = [((n - (count - 1) / 2 + shifts[row] / 2) * 2 * radius, radius + row * radius * math.sqrt(3))
               for row, count in enumerate(rows) for n in range(count)]
    # The strap runs round the outermost logs: the hull of their outlines, a little out from the bark.
    points = [(x + (radius + 0.004) * math.cos(2 * math.pi * k / 24), z + (radius + 0.004) * math.sin(2 * math.pi * k / 24)) for x, z in centres for k in range(24)]
    points.sort()

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    lower, upper = [], []
    for point in points:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], point) <= 0:
            lower.pop()
        lower.append(point)
    for point in reversed(points):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], point) <= 0:
            upper.pop()
        upper.append(point)
    hull = lower[:-1] + upper[:-1]
    strap = bmesh.new()
    for y in (-0.5, 0.5):
        band(strap, hull, y - 0.025, y + 0.025, 0.006)
    model.plain(strap, "steel")
    return model


def band(bm, ring, y0, y1, thick):
    """A flat band of a thickness laid round a ring of (x, z) points that goes round counter-clockwise, from y0 to y1."""
    count = len(ring)
    out = []
    for i in range(count):
        before, after = ring[i - 1], ring[(i + 1) % count]
        dx, dz = after[0] - before[0], after[1] - before[1]
        length = math.hypot(dx, dz)
        out.append((ring[i][0] + dz / length * thick, ring[i][1] - dx / length * thick))
    layers = [[bm.verts.new((x, y, z)) for x, z in points] for points in (ring, out) for y in (y0, y1)]
    inner0, inner1, outer0, outer1 = layers
    for i in range(count):
        j = (i + 1) % count
        bm.faces.new((outer0[i], outer0[j], outer1[j], outer1[i]))
        bm.faces.new((inner0[j], inner0[i], inner1[i], inner1[j]))
        bm.faces.new((inner0[i], inner0[j], outer0[j], outer0[i]))
        bm.faces.new((inner1[j], inner1[i], outer1[i], outer1[j]))


# A small square bale of hay as it comes off the baler: how long, how wide and how high, and how many flakes of pressed hay it is of.
HAY_LONG, HAY_WIDE, HAY_HIGH, HAY_FLAKES = 0.95, 0.48, 0.41, 8


def hay_bales(name, count):
    """Bales of hay one on another, each lying along y: a block of pressed hay with soft edges, its sides rippled where one flake
    meets the next and no two flakes quite alike, and two strings of twine round each bale along its length."""
    model = Model(name)
    rng = random.Random(count)
    twine = bmesh.new()
    for bale in range(count):
        low = bale * HAY_HIGH
        shift = rng.uniform(-0.015, 0.015) if bale else 0.0
        hay = bmesh.new()
        xs, ys, zs = spread(-HAY_WIDE / 2, HAY_WIDE / 2, 2), spread(-HAY_LONG / 2, HAY_LONG / 2, HAY_FLAKES), spread(low, low + HAY_HIGH, 2)
        cage = grid_box(hay, xs, ys, zs)
        flake = [(rng.uniform(-0.024, -0.004), rng.uniform(-0.008, 0.008)) for _ in ys]
        for (i, j, k), vert in cage.items():
            pulled, moved = flake[j] if 0 < j < len(ys) - 1 else (0.0, 0.0)
            # A bale bulges at the middle of its sides, where the twine does not hold it.
            out = pulled + (0.014 if k == 1 else 0.0)
            vert.co.x += shift + moved + (out if i == len(xs) - 1 else -out if i == 0 else 0.0)
            if k == len(zs) - 1:
                vert.co.z += pulled + (0.012 if i == 1 else 0.0)
        crease(hay, cage, 0.5)
        model.soft(hay, "cloth", levels=1)
        for x in (-0.12, 0.12):
            top = low + HAY_HIGH + 0.001
            cuboid(twine, shift + x - 0.005, shift + x + 0.005, -HAY_LONG / 2 + 0.03, HAY_LONG / 2 - 0.03, top - 0.004, top)
            for side in (-1, 1):
                y0, y1 = sorted((side * (HAY_LONG / 2 + 0.001), side * (HAY_LONG / 2 - 0.004)))
                cuboid(twine, shift + x - 0.005, shift + x + 0.005, y0, y1, low + 0.03, top - 0.03)
    model.plain(twine, "rubber")
    return model


def feed_trough(name, long):
    """A wooden trough for feed on two splayed legs at each end: a floor, sides that lean out, ends, and a board along the top of each
    side. It is long along x."""
    model = Model(name)
    rim, floor, wide = 0.40, 0.2, 0.46
    wood = bmesh.new()
    inner = long / 2 - 0.025
    cuboid(wood, -inner, inner, -wide / 2 + 0.05, wide / 2 - 0.05, floor, floor + 0.03)
    for side in (-1, 1):
        prism(wood, (0.0, side * (wide / 2 - 0.06), 2 * inner, 0.025, floor), (0.0, side * (wide / 2 - 0.015), 2 * inner, 0.025, rim - 0.025))
        cuboid(wood, -long / 2, long / 2, side * (wide / 2 + 0.012) - 0.03, side * (wide / 2 + 0.012), rim - 0.025, rim)
        prism(wood, (side * (inner + 0.0125), 0.0, 0.025, wide - 0.12, floor), (side * (inner + 0.0125), 0.0, 0.025, wide - 0.03, rim - 0.025))
        x = side * (long / 2 - 0.1)
        for across in (-1, 1):
            beam(wood, (x, across * (wide / 2 + 0.06), 0.0), (x, across * (wide / 2 - 0.08), floor + 0.02), 0.06, 0.05)
        cuboid(wood, x - 0.03, x + 0.03, -wide / 2 + 0.02, wide / 2 - 0.02, floor - 0.06, floor)
    model.hard(wood, "wood", bevel=0.005, segments=1)
    return model


def stadium(long, wide, out, steps=8):
    """The outline of a stock tank, long along x: two half rounds joined by straight sides, out further than its own."""
    radius, straight = wide / 2 + out, (long - wide) / 2
    return arc(straight, 0.0, radius, -90, 90, steps) + arc(-straight, 0.0, radius, 90, 270, steps)


def wall_ring(bm, outer, inner, z0, z1):
    """A wall of a thickness between two outlines of the same number of points, from z0 to z1."""
    rings = [[bm.verts.new((x, y, z)) for x, y in outline] for outline in (outer, inner) for z in (z0, z1)]
    outer0, outer1, inner0, inner1 = rings
    count = len(outer)
    for i in range(count):
        j = (i + 1) % count
        bm.faces.new((outer0[i], outer0[j], outer1[j], outer1[i]))
        bm.faces.new((inner0[j], inner0[i], inner1[i], inner1[j]))
        bm.faces.new((outer1[i], outer1[j], inner1[j], inner1[i]))
        bm.faces.new((inner0[i], inner0[j], outer0[j], outer0[i]))


def stock_tank(name, long, wide=0.92, high=0.6):
    """A galvanised stock tank with round ends: a wall of sheet with two ribs pressed round it, a rolled rim, a floor, and seams
    riveted up its straight sides."""
    model = Model(name)
    sheet = bmesh.new()
    wall_ring(sheet, stadium(long, wide, 0.0), stadium(long, wide, -0.012), 0.0, high - 0.02)
    wall_ring(sheet, stadium(long, wide, 0.012), stadium(long, wide, -0.02), high - 0.03, high)
    for z in (high * 0.33, high * 0.66):
        wall_ring(sheet, stadium(long, wide, 0.008), stadium(long, wide, -0.004), z - 0.012, z + 0.012)
    outline_up(sheet, stadium(long, wide, -0.006), 0.0, 0.025)
    model.turned(sheet, "steel", sharp=50.0)
    seams = bmesh.new()
    straight = (long - wide) / 2
    for x in (-straight / 2, straight / 2) if straight > 0.6 else (0.0,):
        for side in (-1, 1):
            y = side * (wide / 2 + 0.004)
            cuboid(seams, x - 0.02, x + 0.02, y - 0.004, y + 0.004, 0.03, high - 0.03)
            for z in spread(0.06, high - 0.06, 4):
                y0, y1 = sorted((side * (wide / 2 + 0.006), side * (wide / 2 + 0.013)))
                cuboid(seams, x - 0.007, x + 0.007, y0, y1, z - 0.007, z + 0.007)
    model.plain(seams, "steel")
    return model


def dog_house():
    """A kennel of dark boards with a door at the front under its gable and a roof of three courses of shingle each side."""
    model = Model("dog_house")
    wide, deep, eaves, ridge, board = 0.78, 0.84, 0.5, 0.85, 0.03
    door_half, door_top = 0.17, 0.44
    walls = bmesh.new()
    cuboid(walls, -wide / 2, wide / 2, deep / 2 - board, deep / 2, 0.0, eaves)
    for side in (-1, 1):
        x0, x1 = sorted((side * wide / 2, side * (wide / 2 - board)))
        cuboid(walls, x0, x1, -deep / 2, deep / 2, 0.0, eaves)
        x0, x1 = sorted((side * door_half, side * (wide / 2 - board)))
        cuboid(walls, x0, x1, -deep / 2, -deep / 2 + board, 0.0, eaves)
    cuboid(walls, -door_half, door_half, -deep / 2, -deep / 2 + board, door_top, eaves)
    for y0, y1 in ((-deep / 2, -deep / 2 + board), (deep / 2 - board, deep / 2)):
        outline_along_y(walls, [(-wide / 2, eaves), (wide / 2, eaves), (0.0, ridge)], y0, y1)
    cuboid(walls, -wide / 2 + board, wide / 2 - board, -deep / 2 + board, deep / 2 - board, 0.0, 0.04)
    model.hard(walls, "dark_wood", bevel=0.004, segments=1)
    trim = bmesh.new()
    for side in (-1, 1):
        cuboid(trim, side * door_half - 0.025, side * door_half + 0.025, -deep / 2 - 0.012, -deep / 2 + 0.002, 0.0, door_top + 0.025)
        for y in (-deep / 2 - 0.012, deep / 2 - 0.002):
            cuboid(trim, side * wide / 2 - 0.03, side * wide / 2 + 0.03, y, y + 0.014, 0.0, eaves)
    cuboid(trim, -door_half - 0.025, door_half + 0.025, -deep / 2 - 0.012, -deep / 2 + 0.002, door_top, door_top + 0.04)
    model.hard(trim, "wood", bevel=0.003, segments=1)
    roof = bmesh.new()
    reach, over = wide / 2 + 0.06, deep / 2 + 0.05
    slope = Vector((reach, (eaves - ridge) * reach / (wide / 2)))
    slope_unit = slope.normalized()
    up = Vector((-slope_unit.y, slope_unit.x)) if slope_unit.x > 0 else Vector((slope_unit.y, -slope_unit.x))
    for side in (-1, 1):
        for course in range(3):
            t0, t1 = course / 3, (course + 1) / 3 + 0.04
            top, bottom = slope * t0, slope * min(t1, 1.0)
            outline = [(side * top.x, ridge + top.y), (side * (top.x + up.x * 0.012), ridge + top.y + up.y * 0.012),
                       (side * (bottom.x + up.x * 0.03), ridge + bottom.y + up.y * 0.03), (side * bottom.x, ridge + bottom.y)]
            if side < 0:
                outline.reverse()
            outline_along_y(roof, outline, -over, over)
    cuboid(roof, -0.035, 0.035, -over - 0.005, over + 0.005, ridge - 0.01, ridge + 0.035)
    model.hard(roof, "paint", bevel=0.004, segments=1)
    return model


def slatted_box(name, side, high, slats, gap, made="wood"):
    """An open box of boards nailed round a post in each corner, with gaps between the boards; returns the model and how far in from
    its middle the inner face of its boards is."""
    model = Model(name)
    half, post, board = side / 2, 0.05, 0.022
    wood = bmesh.new()
    for x in (-1, 1):
        for y in (-1, 1):
            x0, x1 = sorted((x * (half - board), x * (half - board - post)))
            y0, y1 = sorted((y * (half - board), y * (half - board - post)))
            cuboid(wood, x0, x1, y0, y1, 0.0, high)
    tall = (high - 0.01 - gap * (slats - 1)) / slats
    for n in range(slats):
        z = 0.01 + n * (tall + gap)
        for s in (-1, 1):
            y0, y1 = sorted((s * half, s * (half - board)))
            cuboid(wood, -half, half, y0, y1, z, z + tall)
            x0, x1 = sorted((s * half, s * (half - board)))
            cuboid(wood, x0, x1, -half + board, half - board, z, z + tall)
    model.hard(wood, made, bevel=0.004, segments=1)
    return model, half - board


def composter(name, full):
    """A compost bin of boards with gaps between them for the air, empty or full of dark compost heaped a little in the middle."""
    model, inside = slatted_box(name, 0.94, 0.58, 5, 0.03)
    if full:
        heap = bmesh.new()
        lathe(heap, [(0.0, 0.02), (inside * 1.2, 0.02), (inside * 1.2, 0.4), (inside * 0.8, 0.47), (0.0, 0.5)], segments=4)
        bmesh.ops.rotate(heap, verts=heap.verts[:], cent=(0.0, 0.0, 0.0), matrix=Matrix.Rotation(math.radians(45), 3, "Z"))
        bmesh.ops.scale(heap, verts=heap.verts[:], vec=(inside * 0.98 / (inside * 1.2 * math.cos(math.radians(45))),) * 2 + (1.0,))
        model.turned(heap, "rubber", sharp=60.0)
    return model


def rain_crate(name, water):
    """A crate of close boards lined with a plastic sheet whose edge is folded over the top of the boards, empty or with rain standing in it."""
    model, inside = slatted_box(name, 0.68, 0.6, 4, 0.004)
    sheet = bmesh.new()
    half = 0.68 / 2
    wall_ring(sheet, [(-half - 0.006, -half - 0.006), (half + 0.006, -half - 0.006), (half + 0.006, half + 0.006), (-half - 0.006, half + 0.006)],
              [(-half + 0.006, -half + 0.006), (half - 0.006, -half + 0.006), (half - 0.006, half - 0.006), (-half + 0.006, half - 0.006)], 0.5, 0.605)
    wall_ring(sheet, [(-inside, -inside), (inside, -inside), (inside, inside), (-inside, inside)],
              [(-inside + 0.006, -inside + 0.006), (inside - 0.006, -inside + 0.006), (inside - 0.006, inside - 0.006), (-inside + 0.006, inside - 0.006)],
              0.03, 0.6)
    cuboid(sheet, -inside, inside, -inside, inside, 0.03, 0.04)
    model.hard(sheet, "cloth", bevel=0.003, segments=1)
    if water:
        rain = bmesh.new()
        cuboid(rain, -inside + 0.006, inside - 0.006, -inside + 0.006, inside - 0.006, 0.04, 0.48)
        model.plain(rain, "enamel")
    return model


def leaf_strip(bm, points, widths, thick=0.004):
    """A leaf or a blade as a ribbon of a thickness along a line of points, as wide at each point as widths says, laid level across."""
    rows = []
    for i, point in enumerate(points):
        point = Vector(point)
        along = (Vector(points[min(i + 1, len(points) - 1)]) - Vector(points[max(i - 1, 0)])).normalized()
        side = along.cross(Vector((0.0, 0.0, 1.0)))
        side = Vector((1.0, 0.0, 0.0)) if side.length < 1e-6 else side.normalized()
        up = side.cross(along).normalized()
        half = max(widths[i] / 2, 0.0005)
        rows.append([bm.verts.new(point + side * dx * half + up * (dz * thick / 2 + 0.25 * half * (1 - abs(dx)))) for dx, dz in
                     ((-1, -1), (0, -1), (1, -1), (1, 1), (0, 1), (-1, 1))])
    for a, b in zip(rows, rows[1:]):
        for i in range(6):
            j = (i + 1) % 6
            bm.faces.new((a[i], a[j], b[j], b[i]))
    bm.faces.new(rows[0][::-1])
    bm.faces.new(rows[-1])


def arching(start, yaw, rise, length, droop, pieces=6):
    """Points along a stalk that leaves start at yaw, rising at rise degrees, and bends down by droop at its end."""
    way = Vector((math.cos(math.radians(yaw)), math.sin(math.radians(yaw)), 0.0))
    return [Vector(start) + way * length * t * math.cos(math.radians(rise)) + Vector((0.0, 0.0, length * t * math.sin(math.radians(rise)) - droop * t * t))
            for t in (i / pieces for i in range(pieces + 1))]


def raised_bed(name, plant, droop=0.0):
    """A raised bed of pale boards a little higher than a knee, full of earth, and a fern of arching fronds growing in it when plant."""
    model = Model(name)
    side, high, board = 0.84, 0.62, 0.035
    half = side / 2
    wood = bmesh.new()
    for n in range(3):
        z0, z1 = n * high / 3 + 0.002, (n + 1) * high / 3 - 0.002
        for s in (-1, 1):
            y0, y1 = sorted((s * half, s * (half - board)))
            cuboid(wood, -half, half, y0, y1, z0, z1)
            x0, x1 = sorted((s * half, s * (half - board)))
            cuboid(wood, x0, x1, -half + board, half - board, z0, z1)
    model.hard(wood, "wood", bevel=0.004, segments=1)
    earth = bmesh.new()
    cuboid(earth, -half + board, half - board, -half + board, half - board, 0.0, high - 0.06)
    model.plain(earth, "rubber")
    if plant:
        fronds = bmesh.new()
        rng = random.Random(len(name))
        for k in range(22):
            yaw = k * 360 / 22 * 7 % 360 + rng.uniform(-8, 8)
            rise = rng.uniform(40, 72) - droop * 40
            # Every frond stays over the earth, inside the boards round it.
            length = min(rng.uniform(0.5, 0.7), (half - board - 0.03) / math.cos(math.radians(rise)))
            points = arching((0.04 * math.cos(math.radians(yaw)), 0.04 * math.sin(math.radians(yaw)), high - 0.06), yaw, rise, length, 0.42 + droop * 0.2)
            leaf_strip(fronds, points, [0.02, 0.07, 0.085, 0.08, 0.065, 0.04, 0.008])
        model._take(fronds, "plastic", lambda obj: None, sharp=50.0)
    return model


# A mall's planter of stone blocks: how wide it is outside, how high, how thick its walls are, and how far under its top the
# earth in it lies, in metres.
PLANTER_SIDE, PLANTER_HIGH, PLANTER_WALL, PLANTER_EARTH = 0.95, 0.74, 0.09, 0.1


def mall_planter():
    """A square planter of stone blocks laid in two courses, each course's corners the other way about, so the joints of one
    fall between those of the other; filled with earth to a hand under its top and planted with five clumps of broad
    leaves, each a ring of leaves arching out round a heart of cupped ones, all inside the walls."""
    model = Model("mall_planter")
    half, wall, course = PLANTER_SIDE / 2, PLANTER_WALL, PLANTER_HIGH / 2
    blocks = bmesh.new()
    for k in range(2):
        z0, z1 = k * course, (k + 1) * course
        for s in (-1, 1):
            # The walls that own the corners in this course run the whole side, the others between them. A wall is two blocks
            # in the lower course and three in the upper, so no joint runs from the floor to the top.
            for along_x in (k == 0, k == 1):
                reach = half if along_x == (k == 0) else half - wall
                inner, outer = sorted((s * half, s * (half - wall)))
                cuts = (-reach, 0.0, reach) if k == 0 else (-reach, -reach / 2, reach / 2, reach)
                for a0, a1 in zip(cuts, cuts[1:]):
                    if along_x:
                        cuboid(blocks, a0, a1, inner, outer, z0, z1)
                    else:
                        cuboid(blocks, inner, outer, a0, a1, z0, z1)
    model.hard(blocks, "paint", bevel=0.008, segments=1)
    ground = PLANTER_HIGH - PLANTER_EARTH
    inside = half - wall
    earth = bmesh.new()
    cuboid(earth, -inside, inside, -inside, inside, 0.0, ground)
    # Brown and not black like a pot's earth: a span of black this wide reads as a hole.
    model.plain(earth, "dark_wood")
    leaves = bmesh.new()
    rng = random.Random(47)
    for cx, cy in ((-0.17, -0.17), (0.17, -0.16), (-0.16, 0.17), (0.17, 0.17), (0.0, 0.01)):
        turn = rng.uniform(0, 2 * math.pi)
        for n in range(8):
            yaw = turn + 2 * math.pi * n / 8 + rng.uniform(-0.15, 0.15)
            foot = (cx + 0.02 * math.cos(yaw), cy + 0.02 * math.sin(yaw), ground - 0.005)
            blade(leaves, foot, yaw, math.radians(rng.uniform(55, 65)), rng.uniform(1.3, 1.6), rng.uniform(0.36, 0.42), rng.uniform(0.17, 0.2), "oval",
                  fold=0.12)
        for n in range(4):
            yaw = turn + 2 * math.pi * (n + 0.5) / 4
            foot = (cx + 0.01 * math.cos(yaw), cy + 0.01 * math.sin(yaw), ground - 0.005)
            blade(leaves, foot, yaw, math.radians(rng.uniform(84, 88)), rng.uniform(0.6, 0.8), rng.uniform(0.34, 0.4), rng.uniform(0.14, 0.16), "oval",
                  fold=0.25)
    # Every leaf stays over the earth, clear of the blocks round it: what reaches past the knee is drawn in, the more the further out
    # it is, and nothing nearer the middle moves.
    room = inside - 0.015
    knee = room - 0.1
    for vert in leaves.verts:
        for axis in (0, 1):
            out = abs(vert.co[axis])
            if out > knee:
                vert.co[axis] = math.copysign(knee + (room - knee) * math.tanh((out - knee) / (room - knee)), vert.co[axis])
    model.turned(leaves, "plastic", sharp=60.0)
    return model


def washing_bin():
    """A laundry cart: a frame of steel tube on four castors with a canvas bag hung from its top rails."""
    model = Model("washing_bin")
    wide, deep, high, tube = 0.78, 0.6, 0.8, 0.022
    frame = bmesh.new()
    for x in (-1, 1):
        for y in (-1, 1):
            cuboid(frame, x * wide / 2 - tube / 2, x * wide / 2 + tube / 2, y * deep / 2 - tube / 2, y * deep / 2 + tube / 2, 0.09, high)
            lathe(frame, [(0.0, 0.0), (0.035, 0.0), (0.035, 0.03), (0.02, 0.07), (0.0, 0.09)], cx=x * wide / 2, cy=y * deep / 2, segments=8)
    for z in (0.12, high - tube / 2):
        for y in (-1, 1):
            cuboid(frame, -wide / 2, wide / 2, y * deep / 2 - tube / 2, y * deep / 2 + tube / 2, z - tube / 2, z + tube / 2)
        for x in (-1, 1):
            cuboid(frame, x * wide / 2 - tube / 2, x * wide / 2 + tube / 2, -deep / 2, deep / 2, z - tube / 2, z + tube / 2)
    model.hard(frame, "steel", bevel=0.005, segments=1)
    bag = bmesh.new()
    xs, ys, zs = spread(-wide / 2 + 0.02, wide / 2 - 0.02, 4), spread(-deep / 2 + 0.02, deep / 2 - 0.02, 3), spread(0.24, high + 0.01, 3)
    cage = grid_box(bag, xs, ys, zs)
    for (i, j, k), vert in cage.items():
        middle = (1 - abs(xs[i]) / (wide / 2)) * (1 - abs(ys[j]) / (deep / 2))
        if k == 0:
            vert.co.z -= 0.06 * middle
        elif k < len(zs) - 1:
            vert.co.x *= 1.03
            vert.co.y *= 1.04
    model.soft(bag, "cloth", levels=2)
    return model


def bird_bath(name, square_foot):
    """A stone bird bath: a wide shallow bowl on a turned column, on a square plinth or a round foot, with water in the bowl."""
    model = Model(name)
    stone = bmesh.new()
    if square_foot:
        cuboid(stone, -0.17, 0.17, -0.17, 0.17, 0.0, 0.07)
        foot = [(0.0, 0.07), (0.12, 0.07)]
    else:
        foot = [(0.0, 0.0), (0.18, 0.0), (0.18, 0.04), (0.12, 0.09)]
    lathe(stone, foot + [(0.08, 0.14), (0.06, 0.3), (0.075, 0.45), (0.055, 0.6), (0.07, 0.66), (0.24, 0.74), (0.28, 0.76), (0.285, 0.8),
                         (0.26, 0.8), (0.23, 0.77), (0.0, 0.76)], segments=24)
    model.turned(stone, "paint", sharp=40.0)
    water = bmesh.new()
    lathe(water, [(0.0, 0.76), (0.235, 0.76), (0.245, 0.785), (0.0, 0.785)], segments=24)
    model.turned(water, "enamel", sharp=40.0)
    return model


def salt_lick():
    """A block of salt for stock to lick, a hollow worn into its top."""
    model = Model("salt_lick")
    block = bmesh.new()
    prism(block, (0.0, 0.0, 0.25, 0.25, 0.0), (0.0, 0.0, 0.24, 0.24, 0.2))
    model.hard(block, "paint", bevel=0.02, segments=2)
    return model


def log_bench(name):
    """A bench of a log split down its middle, its flat face up for the seat, on two pairs of splayed legs. It is long along x."""
    model = Model(name)
    long, seat, radius = 1.7, 0.41, 0.17
    half_round = [(radius * math.cos(math.radians(a)), seat - 0.012 + radius * 0.55 * math.sin(math.radians(a))) for a in range(180, 361, 15)]
    bark = bmesh.new()
    outline_along_x(bark, [(y, z) for y, z in reversed(half_round)], -long / 2, long / 2)
    for x in (-long / 2 + 0.2, long / 2 - 0.2):
        for side in (-1, 1):
            beam(bark, (x, side * 0.26, 0.0), (x, side * 0.05, seat - 0.12), 0.07, 0.07)
    model.hard(bark, "dark_wood", bevel=0.012, segments=1)
    top = bmesh.new()
    cuboid(top, -long / 2 + 0.01, long / 2 - 0.01, -radius + 0.01, radius - 0.01, seat - 0.014, seat)
    model.hard(top, "wood", bevel=0.004, segments=1)
    return model


def stump(name, high, radius, roots):
    """A tree's stump sawn level, its foot spreading into roots, or a round of trunk sawn for a chopping block when roots is 0."""
    model = Model(name)
    bark = bmesh.new()
    if roots:
        profile = [(0.0, 0.0), (radius * 1.35, 0.0), (radius * 1.12, 0.05), (radius * 1.02, 0.13), (radius, high - 0.01)]
    else:
        profile = [(0.0, 0.0), (radius * 0.97, 0.0), (radius, 0.02), (radius, high - 0.01)]
    lathe(bark, profile + [(radius * 0.96, high - 0.004), (0.0, high - 0.004)], segments=16)
    for k in range(roots):
        angle = 2 * math.pi * (k + 0.3) / roots
        way = Vector((math.cos(angle), math.sin(angle), 0.0))
        beam(bark, way * radius * 0.7 + Vector((0.0, 0.0, 0.1)), way * radius * 1.7 + Vector((0.0, 0.0, 0.005)), 0.08, 0.06)
    model.turned(bark, "dark_wood", sharp=50.0)
    face = bmesh.new()
    lathe(face, [(0.0, high - 0.006), (radius * 0.95, high - 0.006), (radius * 0.95, high), (0.0, high)], segments=16)
    model.turned(face, "wood", sharp=50.0)
    return model


GNOME_SCALE = 2.0


def gnome():
    """A garden gnome of painted plaster, waving with his left hand and the right on his hip: round boots, red breeches, a
    blue tunic flared at its hem with a belt and buckle, a round face with cheeks, a big nose, eyes, brows and ears, a
    moustache over a full beard down to his chest, and a tall pointed red hat with a rolled brim. Made half size."""
    model = Model("gnome")
    boots = bmesh.new()
    for side in (-1, 1):
        cuboid(boots, side * 0.048 - 0.034, side * 0.048 + 0.034, -0.09, 0.028, 0.0, 0.05)
    model.soft(boots, "dark_wood")
    legs = bmesh.new()
    for side in (-1, 1):
        lathe(legs, [(0.0, 0.035), (0.034, 0.035), (0.036, 0.06), (0.04, 0.09), (0.0, 0.09)], cx=side * 0.046, cy=-0.01, segments=12)
    model.turned(legs, "vinyl", sharp=60.0)
    coat = bmesh.new()
    lathe(coat, [(0.0, 0.075), (0.106, 0.075), (0.114, 0.085), (0.104, 0.12), (0.1, 0.17), (0.096, 0.21), (0.086, 0.245), (0.062, 0.272), (0.0, 0.28)],
          sy=0.86, segments=20)
    for side in (-1, 1):
        sphere(coat, side * 0.084, 0.0, 0.245, 0.03, segments=10)
    # The left arm raised to wave beside his face, the right bent to his hip.
    for start, end in (((0.084, 0.0, 0.245), (0.14, -0.012, 0.245)), ((0.14, -0.012, 0.245), (0.152, -0.03, 0.32)),
                       ((-0.084, 0.0, 0.245), (-0.135, 0.004, 0.19)), ((-0.135, 0.004, 0.19), (-0.1, -0.02, 0.15))):
        rod(coat, start, end, 0.027, segments=10)
    for x, y, z in ((0.14, -0.012, 0.245), (-0.135, 0.004, 0.19)):
        sphere(coat, x, y, z, 0.027, segments=10)
    model.turned(coat, "paint", sharp=60.0)
    belt = bmesh.new()
    lathe(belt, [(0.099, 0.121), (0.106, 0.124), (0.106, 0.139), (0.099, 0.142)], sy=0.87, segments=20, closed=True, caps=False)
    model.turned(belt, "dark_wood", sharp=50.0)
    buckle = bmesh.new()
    cuboid(buckle, -0.019, 0.019, -0.1, -0.088, 0.119, 0.144)
    model.hard(buckle, "metal", bevel=0.003, segments=1)
    skin = bmesh.new()
    sphere(skin, 0.0, -0.004, 0.318, 0.056, segments=22)
    for side in (-1, 1):
        sphere(skin, side * 0.03, -0.045, 0.31, 0.022, segments=10)
        sphere(skin, side * 0.056, -0.002, 0.322, 0.017, segments=8, squash=1.3)
    sphere(skin, 0.0, -0.066, 0.318, 0.022, segments=14, squash=0.9)
    sphere(skin, 0.155, -0.034, 0.345, 0.024, segments=14, squash=1.25)
    rod(skin, (0.142, -0.036, 0.342), (0.13, -0.04, 0.36), 0.008, segments=6)
    sphere(skin, -0.096, -0.026, 0.148, 0.023, segments=14)
    model.turned(skin, "worktop", sharp=60.0)
    eyes = bmesh.new()
    for side in (-1, 1):
        sphere(eyes, side * 0.021, -0.054, 0.336, 0.008, segments=8)
    model.turned(eyes, "screen", sharp=60.0)
    beard = bmesh.new()
    lathe(beard, [(0.0, 0.148), (0.02, 0.155), (0.048, 0.182), (0.066, 0.222), (0.073, 0.262), (0.069, 0.296), (0.058, 0.314), (0.0, 0.32)], cy=-0.052, sy=0.62,
          segments=20)
    for side in (-1, 1):
        rod(beard, (0.0, -0.084, 0.304), (side * 0.042, -0.074, 0.288), 0.013, segments=10)
        sphere(beard, side * 0.042, -0.074, 0.288, 0.013, segments=10)
        rod(beard, (side * 0.008, -0.058, 0.349), (side * 0.036, -0.05, 0.352), 0.006, segments=6)
    model.turned(beard, "pillow", sharp=60.0)
    hat = bmesh.new()
    lathe(hat, [(0.0, 0.356), (0.066, 0.356), (0.063, 0.38), (0.05, 0.42), (0.034, 0.462), (0.018, 0.508), (0.0, 0.55)], cy=0.008, segments=22)
    torus(hat, 0.0, 0.008, 0.362, 0.064, 0.009, segments=22, steps=8)
    bmesh.ops.rotate(hat, verts=hat.verts[:], cent=(0.0, 0.0, 0.36), matrix=Matrix.Rotation(math.radians(-5), 3, "X"))
    model.turned(hat, "vinyl", sharp=60.0)
    # As big as the game paints it, 1.1 m to the tip of the hat.
    model.vertices = [(position * GNOME_SCALE, normal, surface) for position, normal, surface in model.vertices]
    return model


FLAMINGO_SCALE = 1.3


def flamingo(name, pecking):
    """A lawn flamingo of moulded plastic on two wire legs: a body with its tail raised, an S of a neck and a small head with
    a bent beak, its head up or, pecking, bowed to the grass in front of it."""
    model = Model(name)
    body = bmesh.new()
    lathe(body, [(0.0, 0.0), (0.04, 0.02), (0.085, 0.09), (0.105, 0.19), (0.1, 0.28), (0.075, 0.35), (0.0, 0.39)], segments=14, sx=0.75,
          to=along_y(0.0, 0.2, 0.58) @ Matrix.Rotation(math.radians(-12), 4, "X"))
    if pecking:
        neck = [(0.0, -0.15, 0.62), (0.0, -0.23, 0.64), (0.0, -0.3, 0.58), (0.0, -0.34, 0.49), (0.0, -0.35, 0.42)]
        head, beak = (0.0, -0.355, 0.4), ((0.0, -0.37, 0.37), (0.0, -0.36, 0.3))
    else:
        neck = [(0.0, -0.15, 0.64), (0.0, -0.17, 0.72), (0.0, -0.13, 0.81), (0.0, -0.11, 0.9), (0.0, -0.15, 0.97)]
        head, beak = (0.0, -0.18, 0.98), ((0.0, -0.21, 0.98), (0.0, -0.27, 0.93))
    for start, end in zip(neck, neck[1:]):
        rod(body, start, end, 0.022, segments=8)
    for x, y, z in neck[1:-1]:
        lathe(body, [(0.0, z - 0.022), (0.022, z), (0.0, z + 0.022)], cx=x, cy=y, segments=8)
    lathe(body, [(0.0, head[2] - 0.036), (0.03, head[2] - 0.02), (0.036, head[2]), (0.03, head[2] + 0.02), (0.0, head[2] + 0.036)], cx=head[0], cy=head[1],
          segments=10)
    model.turned(body, "paint", sharp=60.0)
    bill = bmesh.new()
    start, end = Vector(beak[0]), Vector(beak[1])
    turn = Vector((0.0, 0.0, 1.0)).rotation_difference((end - start).normalized()).to_matrix().to_4x4()
    lathe(bill, [(0.0, -0.01), (0.016, 0.0), (0.011, (end - start).length * 0.6), (0.0, (end - start).length)], segments=8,
          to=Matrix.Translation(start) @ turn)
    model.turned(bill, "linen", sharp=60.0)
    legs = bmesh.new()
    for side in (-1, 1):
        rod(legs, (side * 0.025, 0.02, 0.0), (side * 0.02, 0.0, 0.5), 0.005, segments=6)
    model.turned(legs, "steel", sharp=60.0)
    # As big as the game paints it, a head taller than a real one.
    model.vertices = [(position * FLAMINGO_SCALE, normal, surface) for position, normal, surface in model.vertices]
    return model


def potted_flowers(name, pot, rim, high, heads, spread_wide, stalk, clay_of="ceramic"):
    """A pot of earth with a mound of leaves and flower heads on it. pot is the pot's profile; heads how many flowers there are."""
    model = Model(name)
    clay = bmesh.new()
    lathe(clay, pot, segments=16)
    model.turned(clay, clay_of, sharp=40.0)
    earth = bmesh.new()
    lathe(earth, [(0.0, high - 0.012), (rim - 0.006, high - 0.012), (rim - 0.006, high - 0.008), (0.0, high - 0.008)], segments=16)
    model.plain(earth, "rubber")
    plant = bmesh.new()
    rng = random.Random(len(name) + heads)
    for k in range(heads + 4):
        yaw = k * 360 / (heads + 4) + rng.uniform(-15, 15)
        leaf_strip(plant, arching((0.0, 0.0, high - 0.01), yaw, rng.uniform(25, 55), spread_wide * rng.uniform(0.8, 1.1), 0.02, 4),
                   [0.004, 0.02, 0.028, 0.022, 0.002], thick=0.002)
    blooms = bmesh.new()
    for k in range(heads):
        angle, out = rng.uniform(0, 2 * math.pi), spread_wide * math.sqrt(rng.uniform(0.0, 0.7))
        x, y, z = out * math.cos(angle), out * math.sin(angle), high + stalk * rng.uniform(0.75, 1.0)
        lathe(blooms, [(0.0, z - 0.008), (0.012, z - 0.003), (0.016, z + 0.003), (0.008, z + 0.008), (0.0, z + 0.005)], cx=x, cy=y, segments=8)
        beam(plant, (x * 0.3, y * 0.3, high - 0.01), (x, y, z - 0.01), 0.004, 0.004)
    model._take(plant, "plastic", lambda obj: None, sharp=50.0)
    model.turned(blooms, "vinyl", sharp=60.0)
    return model


def cactus():
    """A column cactus with two arms, in a white pot."""
    model = Model("cactus_pot")
    clay = bmesh.new()
    lathe(clay, [(0.0, 0.0), (0.04, 0.0), (0.052, 0.07), (0.056, 0.075), (0.056, 0.085), (0.0, 0.085)], segments=16)
    model.turned(clay, "ceramic", sharp=40.0)
    green = bmesh.new()
    ribs = [(0.0, 0.08)] + [(0.022, 0.09)] + [(0.022, z) for z in (0.2, 0.32)] + [(0.016, 0.34), (0.0, 0.35)]
    lathe(green, ribs, segments=10)
    for side, low, top in ((1, 0.15, 0.27), (-1, 0.19, 0.3)):
        beam(green, (0.0, 0.0, low), (side * 0.045, 0.0, low + 0.01), 0.022, 0.022)
        lathe(green, [(0.0, low - 0.005), (0.013, low), (0.013, top - 0.01), (0.0, top)], cx=side * 0.05, segments=8)
    model.turned(green, "plastic", sharp=50.0)
    return model


def bonsai():
    """A bonsai in a dark shallow tray: a twisted trunk and three flat pads of leaves."""
    model = Model("bonsai")
    tray = bmesh.new()
    wall_ring(tray, [(-0.14, -0.08), (0.14, -0.08), (0.14, 0.08), (-0.14, 0.08)], [(-0.128, -0.068), (0.128, -0.068), (0.128, 0.068), (-0.128, 0.068)], 0.0, 0.055)
    cuboid(tray, -0.13, 0.13, -0.07, 0.07, 0.0, 0.012)
    model.hard(tray, "ceramic", bevel=0.004, segments=1)
    earth = bmesh.new()
    cuboid(earth, -0.128, 0.128, -0.068, 0.068, 0.012, 0.045)
    model.plain(earth, "rubber")
    trunk = bmesh.new()
    path = [(0.02, 0.0, 0.04), (0.0, 0.0, 0.09), (0.04, 0.01, 0.14), (0.0, 0.0, 0.2)]
    for a, b in zip(path, path[1:]):
        beam(trunk, a, b, 0.022, 0.022)
    beam(trunk, (0.03, 0.005, 0.12), (0.1, 0.0, 0.13), 0.012, 0.012)
    beam(trunk, (0.01, 0.0, 0.1), (-0.09, 0.01, 0.12), 0.012, 0.012)
    model.hard(trunk, "dark_wood", bevel=0.003, segments=1)
    pads = bmesh.new()
    for x, z, size in ((0.1, 0.135, 0.06), (-0.09, 0.125, 0.055), (0.0, 0.205, 0.07)):
        lathe(pads, [(0.0, z - 0.012), (size, z - 0.004), (size * 0.9, z + 0.012), (0.0, z + 0.025)], cx=x, segments=10, sy=0.75)
    model.turned(pads, "plastic", sharp=60.0)
    return model


def rose_bouquet():
    """A bunch of roses laid on its side: stems tied together, leaves, and the heads fanned out at one end. The heads are long along x."""
    model = Model("rose_bouquet")
    rng = random.Random(7)
    stems = bmesh.new()
    for k in range(9):
        y, z = rng.uniform(-0.05, 0.05), rng.uniform(0.012, 0.04)
        beam(stems, (0.12, y * 0.3, 0.02), (-0.12 + rng.uniform(-0.03, 0.03), y * 1.6, z), 0.006, 0.006)
        leaf_strip(stems, arching((rng.uniform(-0.06, 0.06), y, z), rng.choice((-90, 90)) + rng.uniform(-30, 30), 15, 0.05, 0.01, 3),
                   [0.003, 0.022, 0.018, 0.002], thick=0.002)
    model._take(stems, "plastic", lambda obj: None, sharp=50.0)
    heads = bmesh.new()
    for k in range(9):
        x, y, z = -0.15 + rng.uniform(-0.04, 0.03), rng.uniform(-0.09, 0.09), rng.uniform(0.03, 0.06)
        lathe(heads, [(0.0, z - 0.025), (0.022, z - 0.015), (0.03, z + 0.005), (0.022, z + 0.02), (0.0, z + 0.016)], cx=x, cy=y, segments=8)
    model.turned(heads, "vinyl", sharp=60.0)
    return model


def hay_feeder():
    """A feeder for stock: a steel trough with a hopper bottom, and over it a frame of green tube on four posts holding a rack of bars
    that lean in from the top rails on both sides to the trough, for the hay. It is long along x."""
    model = Model("hay_feeder")
    long, half_wide, rim, high, tube = 1.68, 0.42, 0.55, 1.85, 0.042
    trough = bmesh.new()
    outer = [(-half_wide, rim), (-half_wide, 0.42), (-0.18, 0.3), (0.18, 0.3), (half_wide, 0.42), (half_wide, rim)]
    inner = [(y * (half_wide - 0.02) / half_wide, z + (0.02 if z < rim else 0.0)) for y, z in outer]
    outline_along_x(trough, outer + inner[::-1], -long / 2, long / 2)
    for x in (-long / 2, long / 2 - 0.02):
        outline_along_x(trough, outer, x, x + 0.02)
    model.hard(trough, "plastic", bevel=0.004, segments=1)
    floor = bmesh.new()
    outline_along_x(floor, [(y * 0.98, z + 0.004) for y, z in inner[1:5]], -long / 2 + 0.02, long / 2 - 0.02)
    model.plain(floor, "steel")
    frame = bmesh.new()
    px, py = long / 2 + 0.04, half_wide + 0.05
    for x in (-px, px):
        for y in (-py, py):
            cuboid(frame, x - tube / 2, x + tube / 2, y - tube / 2, y + tube / 2, 0.0, high)
        for z in (0.75, 0.9, high - tube / 2):
            cuboid(frame, x - tube / 2, x + tube / 2, -py, py, z - tube / 2, z + tube / 2)
    for y in (-py, py):
        cuboid(frame, -px, px, y - tube / 2, y + tube / 2, high - tube, high)
    for side in (-1, 1):
        top, low = (side * (py - 0.06), high - 0.15), (side * 0.08, 0.72)
        beam(frame, (-px, top[0], top[1]), (px, top[0], top[1]), 0.032, 0.032)
        beam(frame, (-px, low[0], low[1]), (px, low[0], low[1]), 0.032, 0.032)
        for x in spread(-px + 0.16, px - 0.16, 7):
            beam(frame, (x, top[0], top[1]), (x, low[0], low[1]), 0.024, 0.024)
    model.hard(frame, "paint", bevel=0.01, segments=1)
    return model


def straw_post():
    """A post driven into the ground with straw bound round it in two thick sheaves, a hand of bare post between them and at the top."""
    model = Model("straw_post")
    post = bmesh.new()
    lathe(post, [(0.0, 0.0), (0.07, 0.0), (0.065, 1.66), (0.05, 1.7), (0.0, 1.7)], segments=10)
    model.turned(post, "dark_wood", sharp=50.0)
    straw = bmesh.new()
    for low, top in ((0.18, 0.86), (0.96, 1.62)):
        cage = grid_box(straw, spread(-0.17, 0.17, 2), spread(-0.17, 0.17, 2), spread(low, top, 3))
        for (i, j, k), vert in cage.items():
            if k in (0, 3):
                vert.co.x *= 0.7
                vert.co.y *= 0.7
        crease(straw, cage, 0.2)
    model.soft(straw, "cloth", levels=2)
    return model


def garage_leaf(name, track):
    """A tile's width of a sectional garage door raised and lying under the ceiling: four sections hinged one to the next,
    each stiffened across on its underside, running into the garage from the head of the opening. With track 1 or -1 the
    door's track runs along that side under it (garage_hanger hangs its far end from the ceiling). Its origin is the middle of the
    door's head in the wall's plane, under the stiffeners, 0.022 below the door's underside; its front faces into the garage."""
    model = Model(name, centred=False)
    near, far, thick, gap = -0.06, -2.0, 0.04, 0.005
    # A track stands inside the face of the garage's side wall, 0.46 from the middle, and the leaf ends at its flange.
    side = track * 0.445
    low = -0.5 if track >= 0 else side + 0.04
    high = 0.5 if track <= 0 else side - 0.04
    sections = 4
    leaf = bmesh.new()
    stiffeners = bmesh.new()
    for k in range(sections):
        y1 = near + (far - near) * k / sections - (gap / 2 if k else 0.0)
        y0 = near + (far - near) * (k + 1) / sections + (gap / 2 if k < sections - 1 else 0.0)
        cuboid(leaf, low, high, y0, y1, 0.022, 0.022 + thick)
        middle = (y0 + y1) / 2
        cuboid(stiffeners, low, high, middle - 0.02, middle + 0.02, 0.0, 0.022)
    model.hard(leaf, "paint", bevel=0.006)
    model.hard(stiffeners, "steel", bevel=0.003)
    if track:
        iron = bmesh.new()
        cuboid(iron, side - 0.0025, side + 0.0025, far - 0.15, near + 0.02, 0.0, thick + 0.06)
        inner = side - track * 0.04
        cuboid(iron, min(side, inner), max(side, inner), far - 0.15, near + 0.02, 0.0, 0.005)
        model.hard(iron, "steel", bevel=0.002)
    return model


def garage_hanger():
    """The strap a raised garage door's track hangs from at its far end, a metre long from its foot on the track up, to be
    stood as tall as the ceiling is over the track."""
    model = Model("garage_hanger", centred=False)
    strap = bmesh.new()
    cuboid(strap, -0.02, 0.02, -0.02, 0.02, 0.0, 1.0)
    model.hard(strap, "steel", bevel=0.002)
    return model


GLASS_PLINTH, GLASS_TOP, GLASS_RAIL = 0.32, 0.93, 0.99


def glass_rail_length(bm_plinth, bm_steel, x0, y0, x1, y1):
    """One straight length of a mall's glass balustrade from (x0, y0) to (x1, y1) on the floor: a painted plinth, the
    channel its pane stands in and a round handrail along its top. The pane is laid as glass by the mod (Outdoors.addGlass)."""
    beam(bm_plinth, (x0, y0, GLASS_PLINTH / 2), (x1, y1, GLASS_PLINTH / 2), 0.08, GLASS_PLINTH)
    beam(bm_steel, (x0, y0, GLASS_PLINTH + 0.015), (x1, y1, GLASS_PLINTH + 0.015), 0.03, 0.03)
    rod(bm_steel, (x0, y0, GLASS_RAIL), (x1, y1, GLASS_RAIL), 0.025, segments=12)
    beam(bm_steel, (x0, y0, GLASS_TOP + 0.005), (x1, y1, GLASS_TOP + 0.005), 0.025, 0.012)
    for x, y in ((x0, y0), (x1, y1)):
        rod(bm_steel, (x, y, GLASS_RAIL - 0.04), (x, y, GLASS_RAIL), 0.006, segments=8)


def glass_rail(name, legs):
    """The frame of a mall's glass balustrade along its square's edge, a metre to a length, or round a corner. Each leg runs
    from (x0, y0) to (x1, y1) on the floor; a stanchion of steel stands at the origin, the middle of a length or where the
    lengths meet, on a block of plinth that fills the corner the lengths leave. The legs
    are Outdoors.GLASS_RUN, GLASS_CORNER and GLASS_POST, which lay the glass between GLASS_PLINTH and GLASS_TOP."""
    model = Model(name, centred=False)
    plinth, steel = bmesh.new(), bmesh.new()
    for x0, y0, x1, y1 in legs:
        glass_rail_length(plinth, steel, x0, y0, x1, y1)
    rod(steel, (0.0, 0.0, GLASS_PLINTH), (0.0, 0.0, GLASS_RAIL + 0.02), 0.022, segments=12)
    if len(legs) > 1:
        cuboid(plinth, -0.04, 0.04, -0.04, 0.04, 0.0, GLASS_PLINTH)
    model.hard(plinth, "paint", bevel=0.006, segments=1)
    model.turned(steel, "steel", sharp=50.0)
    return model


def forked_post(name, scale):
    """A squared timber post a storey high with a brace out to each side along the porch's edge, under the beam it holds up,
    and with scale, a market's hanging scale on a steel bracket out over the front: a dial faced both ways, a spring and a
    pan."""
    model = Model(name, centred=False)
    top = 2.449
    post = bmesh.new()
    cuboid(post, -0.075, 0.075, -0.075, 0.075, 0.0, top)
    model.hard(post, "dark_wood", bevel=0.01)
    braces = bmesh.new()
    for side in (-1, 1):
        beam(braces, (side * 0.03, 0.0, 1.86), (side * 0.5, 0.0, top - 0.06), 0.095, 0.095)
    model.hard(braces, "dark_wood", bevel=0.008)
    if not scale:
        return model
    out, dial = -0.4, 2.14
    iron = bmesh.new()
    cuboid(iron, -0.012, 0.012, out - 0.02, -0.075, 2.29, 2.32)
    beam(iron, (0.0, -0.075, 2.05), (0.0, out * 0.6, 2.3), 0.02, 0.012)
    rod(iron, (0.0, out, 2.29), (0.0, out, dial + 0.15), 0.006, segments=8)
    model.hard(iron, "steel", bevel=0.003)
    face = bmesh.new()
    lathe(face, [(0.0, -0.035), (0.13, -0.035), (0.13, 0.035), (0.0, 0.035)], segments=32, to=along_x(0.0, out, dial))
    model.turned(face, "enamel", sharp=40.0)
    rim = bmesh.new()
    lathe(rim, [(0.125, -0.04), (0.145, -0.04), (0.15, -0.025), (0.15, 0.025), (0.145, 0.04), (0.125, 0.04)], segments=32, closed=False, caps=False,
          to=along_x(0.0, out, dial))
    model.turned(rim, "steel", sharp=40.0)
    marks = bmesh.new()
    for x in (-0.037, 0.037):
        for k in range(12):
            turn = 2 * math.pi * k / 12
            r0, r1 = (0.095, 0.12) if k % 3 else (0.08, 0.12)
            beam(marks, (x, out + r0 * math.sin(turn), dial + r0 * math.cos(turn)), (x, out + r1 * math.sin(turn), dial + r1 * math.cos(turn)), 0.004, 0.006)
        beam(marks, (x * 1.05, out, dial), (x * 1.05, out + 0.02, dial + 0.1), 0.004, 0.006)
    model.plain(marks, "rubber")
    hanger = bmesh.new()
    spring = [(0.0, out + 0.012 * math.sin(k * 1.3), dial - 0.15 - k * 0.012) for k in range(26)]
    for start, end in zip(spring, spring[1:]):
        rod(hanger, start, end, 0.004, segments=6)
    low = spring[-1][2]
    rim_z = 1.42
    for side in (-1, 1):
        rod(hanger, (0.0, out, low), (side * 0.14, out, rim_z + 0.02), 0.004, segments=6)
    model.hard(hanger, "steel", bevel=0.002)
    pan = bmesh.new()
    lathe(pan, [(0.0, 1.35), (0.09, 1.355), (0.14, 1.38), (0.16, 1.415), (0.165, rim_z), (0.155, rim_z), (0.15, 1.42), (0.13, 1.39), (0.085, 1.367), (0.0, 1.362)],
          segments=32, cy=out, caps=False)
    model.turned(pan, "steel", sharp=60.0)
    return model


def fallen_log():
    """A length of fallen trunk lying on the ground, sawn at one end and broken at the other, two snapped branches standing off it.
    It lies aslant as the game's sprite paints it, turned so that its sawn end is towards the south-west of the map."""
    model = Model("fallen_log")
    radius, half = 0.11, 0.42
    bark = bmesh.new()
    lathe(bark, [(0.0, -half), (radius, -half), (radius * 1.04, -half + 0.2), (radius * 0.95, half - 0.1), (radius * 0.7, half), (0.0, half + 0.02)],
          segments=12, to=along_y(0.0, 0.0, radius))
    for at, yaw, rise in ((-0.05, 1, 35), (0.2, -1, 50)):
        start = Vector((0.0, at, radius * 1.4))
        beam(bark, start, start + Vector((yaw * 0.16, 0.06, 0.16 * math.tan(math.radians(rise)))), 0.035, 0.035)
    model.turned(bark, "dark_wood", sharp=50.0)
    end = bmesh.new()
    lathe(end, [(0.0, -half - 0.004), (radius * 0.97, -half - 0.004), (radius * 0.97, -half + 0.001), (0.0, -half + 0.001)], segments=12,
          to=along_y(0.0, 0.0, radius))
    model.plain(end, "wood")
    turn = Matrix.Rotation(math.radians(159), 3, "Z")
    model.vertices = [(turn @ position, turn @ normal, surface) for position, normal, surface in model.vertices]
    return model


def farm_and_garden():
    """What stands in a farmyard or a garden: hay, troughs, a kennel, compost and rain, a mall's planter, a raised bed, a laundry cart, a bird bath,
    a salt lick, a log bench, stumps, a gnome, lawn flamingos, and the plants that stand on a table."""
    return [hay_bales("hay_bale", 1), hay_bales("hay_bales_2", 2), log_bundle(), feed_trough("feed_trough", 1.88), feed_trough("feed_trough_small", 0.92),
            stock_tank("stock_tank", 1.86), stock_tank("stock_tank_long", 2.8), dog_house(), composter("composter", False), composter("composter_full", True),
            rain_crate("rain_crate", False), rain_crate("rain_crate_water", True), mall_planter(), raised_bed("raised_bed", True), raised_bed("raised_bed_dry", True, 0.6),
            raised_bed("raised_bed_bare", False), washing_bin(), bird_bath("bird_bath", True), bird_bath("bird_bath_round", False), salt_lick(),
            log_bench("log_bench"), stump("stump", 0.33, 0.28, 5), stump("chopping_block", 0.33, 0.23, 0), gnome(), flamingo("flamingo", False), flamingo("flamingo_pecking", True),
            potted_flowers("pot_flowers", [(0.0, 0.0), (0.04, 0.0), (0.05, 0.075), (0.055, 0.08), (0.055, 0.09), (0.0, 0.09)], 0.05, 0.09, 12, 0.06, 0.05),
            potted_flowers("pot_violets", [(0.0, 0.0), (0.038, 0.0), (0.05, 0.08), (0.056, 0.085), (0.056, 0.095), (0.0, 0.095)], 0.05, 0.095, 12, 0.07, 0.06, "wood"),
            potted_flowers("vase_flowers", [(0.0, 0.0), (0.045, 0.0), (0.058, 0.06), (0.055, 0.12), (0.042, 0.15), (0.045, 0.16), (0.0, 0.16)], 0.04, 0.16, 11,
                           0.07, 0.08),
            cactus(), bonsai(), rose_bouquet(), hay_feeder(), straw_post(), fallen_log(), forked_post("forked_post", False),
            forked_post("forked_post_scale", True), garage_leaf("garage_leaf", 0), garage_leaf("garage_leaf_track", 1),
            garage_leaf("garage_leaf_track_left", -1), garage_hanger(),
            glass_rail("glass_rail", [(-0.5, 0.0, 0.5, 0.0)]),
            glass_rail("glass_rail_corner", [(0.0, 0.0, 0.96, 0.0), (0.0, 0.0, 0.0, -0.96)]),
            glass_rail("glass_rail_post", [(0.0, 0.0, -0.04, 0.0), (0.0, 0.0, 0.0, 0.04)])]


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


def felt_rolls(name, end):
    """A square's length of a bundle of roofing felt lying along y, two rolls and one on them. The game lays the bundle
    over three squares in a row: at an end, the rolls stop a quarter in from -y, where their cores show and a band ties
    them, and run on to the square's edge at +y; in the middle they cross the whole square."""
    model = Model(name)
    radius = 0.085
    start = -0.25 if end else -0.5
    rolls, cores = bmesh.new(), bmesh.new()
    top = 0.0
    for row, count in enumerate((2, 1)):
        for n in range(count):
            x = (n - (count - 1) / 2) * 2 * radius
            z = radius + row * radius * math.sqrt(3)
            top = z + radius
            profile = [(radius, start), (radius, 0.5)]
            lathe(rolls, [(0.03, start)] + profile if end else profile, segments=12, caps=False, to=along_y(x, 0.0, z))
            if end:
                lathe(cores, [(0.0, start), (0.03, start), (0.03, start + 0.004), (0.0, start + 0.004)], segments=8, to=along_y(x, 0.0, z))
    model.turned(rolls, "rubber", sharp=40.0)
    model.plain(cores, "dark_wood")
    if end:
        band = bmesh.new()
        wide = 2 * radius + 0.004
        outline_along_y(band, [(-wide, 0.0), (wide, 0.0), (wide, radius), (radius * 0.6, top + 0.004), (-radius * 0.6, top + 0.004), (-wide, radius)], -0.02, 0.08)
        model.plain(band, "steel")
    return model


def parking_meter():
    """A parking meter: a steel post and on it a dark head with a rounded top, its dial's window and coin slot at -Y.
    The origin is the post's axis."""
    model = Model("parking_meter", centred=False)
    post = bmesh.new()
    lathe(post, [(0.0, 0.0), (0.045, 0.0), (0.045, 0.03), (0.025, 0.04), (0.025, 1.04), (0.0, 1.04)], segments=12)
    model.turned(post, "metal", sharp=50.0)
    head = bmesh.new()
    cuboid(head, -0.07, 0.07, -0.05, 0.06, 1.02, 1.28)
    lathe(head, [(0.07, -0.05), (0.07, 0.06)], segments=16, sx=1.0, to=Matrix.Translation((0.0, 0.0, 1.28)) @ Matrix.Rotation(math.radians(-90), 4, "X"))
    model.hard(head, "plastic", bevel=0.008, segments=2)
    window = bmesh.new()
    prism_y(window, (0.0, 1.27, 0.09, 0.08, -0.056), (0.0, 1.27, 0.09, 0.08, -0.049))
    model.plain(window, "screen")
    slot = bmesh.new()
    cuboid(slot, -0.025, 0.025, -0.056, -0.049, 1.14, 1.17)
    model.plain(slot, "steel")
    return model


def security_monitors():
    """A security desk's wall of monitors, the wall at +Y: a steel cabinet, and over it a frame holding two rows of two
    screens on shelves with their buttons, under a cap."""
    model = Model("security_monitors")
    case, dark, glass, keys = bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new()
    cuboid(case, -0.46, 0.46, -0.03, 0.06, 0.0, 0.72)
    cuboid(case, -0.47, 0.47, -0.08, 0.06, 1.95, 2.03)
    cuboid(dark, -0.46, 0.46, 0.0, 0.06, 0.72, 1.95)
    for z in (0.72, 1.33):
        cuboid(dark, -0.46, 0.46, -0.12, 0.0, z, z + 0.06)
        for x in (-0.23, 0.23):
            cuboid(dark, x - 0.2, x + 0.2, -0.08, 0.0, z + 0.06, z + 0.6)
            prism_y(glass, (x, z + 0.34, 0.32, 0.42, -0.087), (x, z + 0.34, 0.32, 0.42, -0.08))
            for k in range(3):
                cuboid(keys, x + 0.06 + k * 0.04, x + 0.08 + k * 0.04, -0.135, -0.12, z + 0.02, z + 0.04)
    model.hard(case, "steel", bevel=0.008, segments=2)
    model.hard(dark, "plastic", bevel=0.01, segments=2)
    model.plain(glass, "screen")
    model.plain(keys, "metal")
    return model


def chimney_pots():
    """The top of a chimney stack: a stone cap and on it two clay pots side by side along X, tapering to a lipped rim."""
    model = Model("chimney_pots")
    cap = bmesh.new()
    cuboid(cap, -0.315, 0.315, -0.315, 0.315, 0.0, 0.06)
    model.hard(cap, "metal", bevel=0.01, segments=1)
    pots = bmesh.new()
    for x in (-0.15, 0.15):
        lathe(pots, [(0.0, 0.06), (0.105, 0.06), (0.08, 0.25), (0.088, 0.26), (0.088, 0.28), (0.068, 0.28), (0.068, 0.2), (0.0, 0.2)], cx=x,
              segments=16)
    model.turned(pots, "paint", sharp=50.0)
    return model


def theatre_seats():
    """Two theatre seats side by side across X, facing -Y and standing in the back of their square: upholstered seats and backs,
    the backs in a dark shell, three wooden arm rests on steel standards, each standard on a foot along Y."""
    model = Model("theatre_seats", centred=False)
    cushions, shells, arms, steel = bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new()
    for x in (-0.26, 0.26):
        prism_y(cushions, (x, 0.47, 0.47, 0.12, -0.12), (x, 0.47, 0.47, 0.14, 0.3))
        prism_y(cushions, (x, 0.7, 0.47, 0.46, 0.3), (x, 0.72, 0.47, 0.5, 0.4))
        prism_y(cushions, (x, 0.97, 0.45, 0.12, 0.34), (x, 0.99, 0.45, 0.12, 0.46))
        prism_y(shells, (x, 0.72, 0.49, 0.56, 0.4), (x, 0.74, 0.49, 0.6, 0.47))
    model.hard(cushions, "vinyl", bevel=0.04, segments=2)
    model.hard(shells, "rubber", bevel=0.015, segments=2)
    for x in (-0.52, 0.0, 0.52):
        cuboid(arms, x - 0.035, x + 0.035, -0.06, 0.38, 0.62, 0.66)
        cuboid(steel, x - 0.025, x + 0.025, 0.15, 0.21, 0.0, 0.62)
        cuboid(steel, x - 0.025, x + 0.025, -0.12, 0.46, 0.0, 0.04)
    model.hard(arms, "wood", bevel=0.01, segments=2)
    model.hard(steel, "plastic", bevel=0.004, segments=1)
    return model


def parking_barrier():
    """A car park's barrier over the four squares the game lays it on: a painted booth with a rounded top on a concrete foot at +Y,
    and its arm down -Y nearly three squares, white with black stripes across it, from a round mount on the booth's face. The
    origin is the middle of the four squares."""
    model = Model("parking_barrier", centred=False)
    x, y = 0.03, 1.52
    foot = bmesh.new()
    cuboid(foot, x - 0.18, x + 0.18, y - 0.18, y + 0.18, 0.0, 0.15)
    model.hard(foot, "metal", bevel=0.01, segments=1)
    booth = bmesh.new()
    cuboid(booth, x - 0.16, x + 0.16, y - 0.16, y + 0.16, 0.15, 1.12)
    lathe(booth, [(0.16, -0.16), (0.16, 0.16)], segments=16, to=Matrix.Translation((x, y, 1.12)) @ Matrix.Rotation(math.radians(90), 4, "Y"))
    model.hard(booth, "paint", bevel=0.01, segments=2)
    mount = bmesh.new()
    lathe(mount, [(0.0, 0.0), (0.09, 0.0), (0.09, 0.05), (0.0, 0.05)], segments=16,
          to=Matrix.Translation((x, y - 0.16, 0.76)) @ Matrix.Rotation(math.radians(90), 4, "X"))
    model.turned(mount, "rubber", sharp=50.0)
    white, black = bmesh.new(), bmesh.new()
    tip, root, step = -1.52, y - 0.21, 0.18
    n = 0
    at = root
    while at > tip:
        cuboid(white if n % 2 == 0 else black, x - 0.025, x + 0.025, max(at - step, tip), at, 0.71, 0.81)
        at -= step
        n += 1
    model.hard(white, "enamel", bevel=0.004, segments=1)
    model.hard(black, "rubber", bevel=0.004, segments=1)
    return model


# How tall a flag pole is from the ground to the floor of the storey over it, where the game paints its top and the flag.
FLAG_POLE_LOW = 2.45


def flag_pole():
    """A flag pole's lower length, from a collar on the ground to the floor of the storey over it. The origin is its axis."""
    model = Model("flag_pole", centred=False)
    pole = bmesh.new()
    lathe(pole, [(0.0, 0.0), (0.075, 0.0), (0.075, 0.12), (0.04, 0.14), (0.04, FLAG_POLE_LOW), (0.0, FLAG_POLE_LOW)], segments=14)
    model.turned(pole, "metal", sharp=50.0)
    return model


def flag_top():
    """A flag pole's top length with its ball, and the flag hung limp from it along -X in folds that run down it: red and white
    stripes across it and the blue of its corner at the top by the pole. The origin is the pole's axis at the foot of this length."""
    model = Model("flag_top", centred=False)
    pole = bmesh.new()
    lathe(pole, [(0.0, 0.0), (0.04, 0.0), (0.04, 1.12), (0.0, 1.12)], segments=14)
    lathe(pole, [(0.0, 1.1)] + [(0.065 * math.sin(math.radians(a)), 1.165 - 0.065 * math.cos(math.radians(a))) for a in range(30, 180, 30)]
          + [(0.0, 1.23)], segments=14)
    model.turned(pole, "metal", sharp=50.0)
    red, white, blue = bmesh.new(), bmesh.new(), bmesh.new()
    # Hoisted along the pole and hanging limp in folds that run down it: seven red stripes and six white across it, and the
    # blue corner over the top six by the pole.
    folds = [(-0.04, 0.0), (-0.075, 0.03), (-0.11, -0.025), (-0.145, 0.025), (-0.18, -0.02), (-0.21, 0.01)]
    top, bottom, stripes, canton_x = 1.08, 0.18, 13, -0.11
    stripe = (top - bottom) / stripes
    for n, ((x0, y0), (x1, y1)) in enumerate(zip(folds, folds[1:])):
        for k in range(stripes):
            z0, z1 = bottom + k * stripe, bottom + (k + 1) * stripe
            into = blue if k >= stripes - 7 and x0 > canton_x - 0.001 else red if k % 2 == 0 else white
            # A piece of one stripe on one fold of the cloth, a closed sheet 6 mm thick.
            vs = [into.verts.new(v) for v in ((x0, y0 - 0.003, z0), (x1, y1 - 0.003, z0), (x1, y1 + 0.003, z0), (x0, y0 + 0.003, z0),
                                              (x0, y0 - 0.003, z1), (x1, y1 - 0.003, z1), (x1, y1 + 0.003, z1), (x0, y0 + 0.003, z1))]
            for face in ((0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)):
                into.faces.new([vs[i] for i in face])
    model.plain(red, "vinyl")
    model.plain(white, "linen")
    model.plain(blue, "cloth")
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


def crossing_signal():
    """A railway crossing's signal: a white post on a concrete foot, banded yellow and black near the ground, a pair of lamps
    under hoods on an arm across it, and the crossed boards over them, yellow striped across in black on the side read from at -Y and
    bare metal behind. The origin is the post's axis."""
    model = Model("crossing_signal", centred=False)
    foot = bmesh.new()
    cuboid(foot, -0.17, 0.17, -0.17, 0.17, 0.0, 0.1)
    model.hard(foot, "metal", bevel=0.01, segments=1)
    post, yellow, black = bmesh.new(), bmesh.new(), bmesh.new()
    lathe(post, [(0.05, 0.1), (0.05, 2.71), (0.035, 2.73), (0.0, 2.73)], segments=14)
    for into, z0, z1 in ((yellow, 0.34, 0.43), (black, 0.43, 0.57), (yellow, 0.57, 0.64), (black, 0.64, 0.72)):
        lathe(into, [(0.053, z0), (0.053, z1)], segments=14)
    model.turned(post, "paint", sharp=50.0)
    model.turned(yellow, "plastic", sharp=50.0)
    lamps, lenses = bmesh.new(), bmesh.new()
    cuboid(lamps, -0.26, 0.26, -0.03, 0.03, 1.67, 1.73)
    for x in (-0.21, 0.21):
        lathe(lamps, [(0.0, -0.06), (0.1, -0.06), (0.1, 0.07), (0.0, 0.07)], cx=x, cy=-1.7, segments=16,
              to=Matrix.Rotation(math.radians(-90), 4, "X"))
        prism_y(lamps, (x, 1.81, 0.21, 0.015, -0.17), (x, 1.81, 0.21, 0.015, -0.06))
        lathe(lenses, [(0.0, -0.075), (0.075, -0.075), (0.075, -0.06), (0.0, -0.06)], cx=x, cy=-1.7, segments=16,
              to=Matrix.Rotation(math.radians(-90), 4, "X"))
    model.turned(lamps, "steel", sharp=50.0)
    model.turned(lenses, "vinyl", sharp=50.0)
    boards, stripes, backs = bmesh.new(), bmesh.new(), bmesh.new()
    for turn in (45, -45):
        spin = Matrix.Translation((0.0, 0.0, 2.12)) @ Matrix.Rotation(math.radians(turn), 4, "Y")
        for into, x0, x1, y0, y1, z0, z1 in ((boards, -0.42, 0.42, -0.09, -0.07, -0.07, 0.07), (backs, -0.42, 0.42, -0.07, -0.06, -0.07, 0.07),
                                             (stripes, -0.36, -0.28, -0.095, -0.09, -0.07, 0.07), (stripes, -0.2, -0.12, -0.095, -0.09, -0.07, 0.07),
                                             (stripes, 0.12, 0.2, -0.095, -0.09, -0.07, 0.07), (stripes, 0.28, 0.36, -0.095, -0.09, -0.07, 0.07)):
            first = len(into.verts)
            cuboid(into, x0, x1, y0, y1, z0, z1)
            into.verts.ensure_lookup_table()
            bmesh.ops.transform(into, matrix=spin, verts=into.verts[first:])
    model.hard(boards, "plastic", bevel=0.004, segments=1)
    model.hard(stripes, "rubber", bevel=0.002, segments=1)
    model.hard(backs, "metal", bevel=0.002, segments=1)
    model.turned(black, "rubber", sharp=50.0)
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


# A cooper's barrel set out to catch the rain: its height, the radius of its heads and of its belly, how many staves
# it is made of, how thick they are and how wide the joint between two of them is, all in metres.
BARREL_HIGH, BARREL_HEAD, BARREL_BELLY = 0.84, 0.30, 0.35
BARREL_STAVES, BARREL_WALL, BARREL_JOINT = 18, 0.022, 0.003


def barrel_radius(z):
    return BARREL_HEAD + (BARREL_BELLY - BARREL_HEAD) * math.sin(math.pi * min(max(z, 0.0), BARREL_HIGH) / BARREL_HIGH)


def rain_barrel(name, water=False, tarp=False, lid=False):
    """A wooden barrel of staves bellied out between four iron hoops, its top head knocked out to catch the rain.
    water puts the rain in it, a pale sheet a hand under the rim; tarp ties a sheet over its mouth that sags into
    it as a funnel and hangs over the rim; lid lays a wooden lid on that sheet, which is then drawn flat."""
    model = Model(name, centred=False)
    rows = 10
    staves = bmesh.new()
    for k in range(BARREL_STAVES):
        a0, a1 = 2 * math.pi * k / BARREL_STAVES, 2 * math.pi * (k + 1) / BARREL_STAVES
        outer, inner = [], []
        for i in range(rows + 1):
            z = BARREL_HIGH * i / rows
            radius = barrel_radius(z)
            half = BARREL_JOINT / 2 / radius
            angles = (a0 + half, (a0 + a1) / 2, a1 - half)
            outer.append([(radius * math.cos(a), radius * math.sin(a), z) for a in angles])
            inner.append([((radius - BARREL_WALL) * math.cos(a), (radius - BARREL_WALL) * math.sin(a), z) for a in angles])
        shell(staves, outer, inner)
    # The bottom head, let into the staves a little above their ends.
    bottom = barrel_radius(0.07) - BARREL_WALL + 0.004
    lathe(staves, [(0.0, 0.055), (bottom, 0.055), (bottom, 0.085), (0.0, 0.085)], segments=BARREL_STAVES)
    model.turned(staves, "dark_wood", sharp=50.0)

    hoops = bmesh.new()
    for middle in (0.05, 0.27, 0.6, BARREL_HIGH - 0.04):
        low, high = middle - 0.024, middle + 0.024
        lathe(hoops, [(barrel_radius(low) - 0.004, low), (barrel_radius(low) + 0.005, low), (barrel_radius(high) + 0.005, high),
                      (barrel_radius(high) - 0.004, high)], segments=36, closed=True)
    model.turned(hoops, "metal", sharp=40.0)

    if water:
        sheet = barrel_radius(BARREL_HIGH - 0.1) - BARREL_WALL + 0.004
        rain = bmesh.new()
        lathe(rain, [(0.0, BARREL_HIGH - 0.15), (sheet, BARREL_HIGH - 0.15), (sheet, BARREL_HIGH - 0.1), (0.0, BARREL_HIGH - 0.1)], segments=BARREL_STAVES)
        model.turned(rain, "enamel", sharp=40.0)

    if tarp:
        top, rim = BARREL_HIGH, BARREL_HEAD
        hem = BARREL_HIGH - 0.12
        skirt = barrel_radius(hem) + 0.014
        sag = 0.0 if lid else 0.13
        thick = 0.005
        profile = [(0.0, top - sag), (0.5 * rim, top - sag * 0.55), (0.85 * rim, top - sag * 0.15), (rim + 0.004, top + 0.012), (rim + 0.022, top + 0.004),
                   (skirt - 0.002, top - 0.05), (skirt + 0.004, hem), (skirt + 0.004 - thick, hem), (skirt - 0.002 - thick, top - 0.05),
                   (rim + 0.017, top + 0.004 - thick), (rim + 0.002, top + 0.012 - thick), (0.85 * rim, top - sag * 0.15 - thick),
                   (0.5 * rim, top - sag * 0.55 - thick), (0.0, top - sag - thick)]
        sheet = bmesh.new()
        lathe(sheet, profile, segments=32, closed=True)
        # The sheet hangs in folds below where it is tied, deeper the further down it hangs.
        for vert in sheet.verts:
            out = math.hypot(vert.co.x, vert.co.y)
            if out > rim + 0.03 and vert.co.z < top - 0.02:
                angle = math.atan2(vert.co.y, vert.co.x)
                deep = (top - 0.02 - vert.co.z) / (top - 0.02 - hem)
                vert.co.z += deep * 0.03 * math.sin(5 * angle + 1.3) + deep * 0.015 * math.sin(11 * angle)
                push = 1.0 + deep * 0.03 * (0.5 + 0.5 * math.sin(7 * angle + 0.4)) / out
                vert.co.x *= push
                vert.co.y *= push
        model.turned(sheet, "cloth", sharp=60.0)
        cord = bmesh.new()
        tied = top - 0.035
        lathe(cord, [(barrel_radius(tied) + 0.007, tied - 0.005), (barrel_radius(tied) + 0.016, tied - 0.005), (barrel_radius(tied) + 0.016, tied + 0.005),
                     (barrel_radius(tied) + 0.007, tied + 0.005)], segments=32, closed=True)
        model.turned(cord, "linen", sharp=60.0)

    if lid:
        board = bmesh.new()
        lathe(board, [(0.0, BARREL_HIGH + 0.013), (BARREL_HEAD - 0.02, BARREL_HIGH + 0.013), (BARREL_HEAD - 0.02, BARREL_HIGH + 0.036), (0.0, BARREL_HIGH + 0.036)],
              segments=28)
        for side in (-1, 1):
            cuboid(board, side * 0.12 - 0.025, side * 0.12 + 0.025, -0.22, 0.22, BARREL_HIGH + 0.036, BARREL_HIGH + 0.056)
        model.turned(board, "dark_wood", sharp=40.0)
    return model


def plant_pot(model, rim, base, high):
    """A round pot of glazed clay with a rolled rim, filled with earth to a little under it. Gives how high the earth is."""
    earth_top = high - 0.035
    pot = bmesh.new()
    lathe(pot, [(0.0, 0.0), (base - 0.01, 0.0), (base, 0.012), (rim - 0.008, high - 0.03), (rim + 0.006, high - 0.026), (rim + 0.008, high),
                (rim - 0.014, high), (rim - 0.02, earth_top - 0.02), (0.0, earth_top - 0.02)], segments=24)
    model.turned(pot, "ceramic", sharp=40.0)
    earth = bmesh.new()
    lathe(earth, [(0.0, earth_top - 0.025), (rim - 0.019, earth_top - 0.025), (rim - 0.019, earth_top - 0.004), (rim * 0.5, earth_top + 0.004),
                  (0.0, earth_top + 0.008)], segments=20)
    model.turned(earth, "rubber", sharp=40.0)
    return earth_top


def stalk(bm, points, radius, tip=1.0, sides=6):
    """A round stem or cane through the points, its radius narrowing to tip of what it is at its foot."""
    points = [Vector(point) for point in points]
    first = (points[1] - points[0]).normalized()
    across = first.cross(Vector((1.0, 0.0, 0.0)) if abs(first.x) < 0.9 else Vector((0.0, 1.0, 0.0))).normalized()
    rings = []
    for i, point in enumerate(points):
        way = (points[min(i + 1, len(points) - 1)] - points[max(i - 1, 0)]).normalized()
        side = (across - way * across.dot(way)).normalized()
        other = way.cross(side)
        size = radius * (1.0 - (1.0 - tip) * i / (len(points) - 1))
        rings.append([bm.verts.new(point + (side * math.cos(2 * math.pi * k / sides) + other * math.sin(2 * math.pi * k / sides)) * size) for k in range(sides)])
    for a, b in zip(rings, rings[1:]):
        for k in range(sides):
            j = (k + 1) % sides
            bm.faces.new((a[k], a[j], b[j], b[k]))
    bm.faces.new(rings[0][::-1])
    bm.faces.new(rings[-1])


def curve(start, yaw, rise, bend, length, pieces):
    """Points along a line that sets off from start towards yaw, rise radians over the level, and bends down by
    bend radians over its length: a stem, or the middle of a leaf. Gives the points and the way it runs at each."""
    points, ways = [], []
    here = Vector(start)
    for i in range(pieces + 1):
        lean = rise - bend * i / pieces
        way = Vector((math.cos(lean) * math.cos(yaw), math.cos(lean) * math.sin(yaw), math.sin(lean)))
        points.append(here.copy())
        ways.append(way)
        here = here + way * (length / pieces)
    return points, ways


def blade(bm, start, yaw, rise, bend, length, width, shape, fold=0.15, twist=0.0, thick=0.004):
    """A leaf as a thin body: its middle along a curve, as wide across as shape says at each place along it, folded
    a little along its rib so its two halves catch the light apart. shape is "lance" (wide in the middle, pointed
    at both ends), "oval" (wide for most of its length, its tip rounded), "sword" (wide from the foot and pointed at the tip) or "strap" (long and narrow)."""
    pieces = max(2, math.ceil(length / 0.09))
    points, ways = curve(start, yaw, rise, bend, length, pieces)
    level = Vector((-math.sin(yaw), math.cos(yaw), 0.0))
    upper, lower = [], []
    for i, (point, way) in enumerate(zip(points, ways)):
        t = i / pieces
        if shape == "lance":
            wide = width * math.sin(math.pi * min(0.05 + t, 1.0)) ** 0.7
        elif shape == "oval":
            wide = width * math.sin(math.pi * (0.04 + 0.92 * t)) ** 0.4
        elif shape == "sword":
            wide = width * (1.0 - t ** 3) * (0.75 + 0.25 * min(1.0, t * 4))
        else:
            wide = width * (1.0 - t ** 2) * min(1.0, 0.35 + t * 5)
        wide = max(wide, 0.004)
        side = Matrix.Rotation(twist * t, 3, way) @ level
        face = way.cross(side).normalized()
        if face.z < 0.0:
            face = -face
        row = [point - side * wide / 2, point - face * fold * wide, point + side * wide / 2]
        upper.append([tuple(corner) for corner in row])
        lower.append([tuple(corner - face * thick) for corner in row])
    shell(bm, upper, lower)
    return points[-1]


def small_leaf(bm, start, way, side, length, width, thick=0.003):
    """A leaf too small to bend: a flat diamond with a little thickness, its foot at start."""
    way, side = Vector(way).normalized(), Vector(side).normalized()
    face = way.cross(side).normalized()
    middle = Vector(start) + way * length * 0.45
    foot, tip = bm.verts.new(start), bm.verts.new(Vector(start) + way * length)
    left, right = bm.verts.new(middle - side * width / 2), bm.verts.new(middle + side * width / 2)
    above, below = bm.verts.new(middle + face * thick), bm.verts.new(middle - face * thick)
    for a, b in ((foot, right), (right, tip), (tip, left), (left, foot)):
        bm.faces.new((a, b, above))
        bm.faces.new((b, a, below))


# How far from the middle of its pot a potted plant reaches at most, in metres: the edge of its square less a wall's half.
PLANT_REACH = 0.45


def potted(name, plant, grown=True, droop=False):
    """A plant in a pot that stands on the floor, as the game has five kinds of, which plant names. Not grown is its
    pot with only earth in it. droop is the plant when it has gone without water, its leaves let down."""
    model = Model(name, centred=False)
    rng = random.Random(sum(ord(letter) for letter in name))
    rim, base, high = {"evergreen": (0.21, 0.15, 0.42), "dragon_tree": (0.2, 0.15, 0.44), "snake_plant": (0.19, 0.17, 0.42),
                       "cast_iron": (0.215, 0.14, 0.42), "ficus": (0.2, 0.15, 0.42)}[plant]
    ground = plant_pot(model, rim, base, high)
    if not grown:
        return model
    leaves, stems = bmesh.new(), bmesh.new()
    if plant == "snake_plant":
        # Stiff swords from the earth, a few of them falling outwards when the plant is dry.
        for k in range(10):
            yaw = 2 * math.pi * k / 10 + rng.uniform(-0.3, 0.3)
            out = rng.uniform(0.0, 0.09)
            foot = (out * math.cos(yaw), out * math.sin(yaw), ground - 0.01)
            long = rng.uniform(0.55, 1.25)
            rise, bend = (math.radians(rng.uniform(80, 88)), rng.uniform(0.05, 0.25)) if not droop else (math.radians(rng.uniform(65, 84)), rng.uniform(0.3, 1.0))
            blade(leaves, foot, yaw, rise, bend, long * (0.85 if droop else 1.0), rng.uniform(0.06, 0.08), "sword", fold=0.12, twist=rng.uniform(-0.8, 0.8))
    elif plant == "evergreen":
        # Broad leaves on stalks, the inner ones held higher.
        for k in range(11):
            yaw = k * 2.4 + rng.uniform(-0.2, 0.2)
            inner = k / 10
            stem_rise = math.radians((74 - 18 * inner) - (15 if droop else 0))
            points, _ = curve((0.02 * math.cos(yaw), 0.02 * math.sin(yaw), ground - 0.01), yaw, stem_rise, 0.25 if droop else 0.1, 0.58 - 0.3 * inner, 6)
            stalk(leaves, points, 0.008, tip=0.7)
            blade(leaves, points[-1], yaw, math.radians(45 - 30 * inner) - (0.7 if droop else 0.0), 0.7 if droop else 0.55, 0.3, 0.12, "lance", fold=0.2)
    elif plant == "cast_iron":
        # Long leaves on long stalks that rise almost straight and arch over at the top.
        for k in range(9):
            yaw = k * 2.4 + rng.uniform(-0.2, 0.2)
            long = rng.uniform(0.85, 1.45)
            points, _ = curve((0.02 * math.cos(yaw), 0.02 * math.sin(yaw), ground - 0.01), yaw, math.radians(rng.uniform(84, 88) - (8 if droop else 0)),
                              0.15 if droop else 0.03, long, max(2, math.ceil(long / 0.09)))
            stalk(leaves, points, 0.007, tip=0.7)
            blade(leaves, points[-1], yaw, math.radians(60 if not droop else 5), 1.5 if not droop else 1.2, 0.42, 0.14, "lance", fold=0.22)
    elif plant == "dragon_tree":
        # Three canes of three heights, each with a head of narrow leaves at its top.
        for cane, (tall, lean) in enumerate(((1.25, 0.6), (1.7, 2.6), (2.1, 4.5))):
            top = (0.08 * math.cos(lean), 0.08 * math.sin(lean), tall)
            foot = (0.04 * math.cos(lean + 3.14), 0.04 * math.sin(lean + 3.14), ground - 0.01)
            pieces = max(2, math.ceil((tall - ground) / 0.09))
            stalk(stems, [Vector(foot).lerp(Vector(top), i / pieces) for i in range(pieces + 1)], 0.022, tip=0.8)
            for k in range(18):
                yaw = k * 2.4 + cane
                below = 0.18 * (1 - k / 17)
                start = Vector(top) + Vector((0.0, 0.0, -below))
                rise = math.radians(-15 + 75 * k / 17) if not droop else math.radians(-40 + 45 * k / 17)
                blade(leaves, start, yaw, rise, 0.9 if not droop else 1.2, 0.42, 0.035, "strap", fold=0.1)
    elif plant == "ficus":
        # A little tree: a trunk, a few branches out of it, and a round head of small leaves.
        trunk_top = 0.9
        pieces = 9
        stalk(stems, [(0.012 * math.sin(i), 0.0, ground - 0.01 + (trunk_top - ground) * i / pieces) for i in range(pieces + 1)], 0.022, tip=0.6)
        middle = Vector((0.0, 0.0, 1.12))
        for k in range(6):
            yaw = k * 2.4
            points, _ = curve((0.0, 0.0, 0.62 + 0.05 * k), yaw, math.radians(50), 0.0, 0.32, 3)
            stalk(stems, points, 0.01, tip=0.5)
        count = 420 if not droop else 200
        for _ in range(count):
            while True:
                place = Vector((rng.uniform(-1, 1), rng.uniform(-1, 1), rng.uniform(-1, 1)))
                if 0.55 < place.length <= 1.0:
                    break
            at = middle + Vector((place.x * 0.36, place.y * 0.36, place.z * 0.32))
            if droop and rng.random() < 0.5:
                at.z -= 0.12
            out = (at - Vector((0.0, 0.0, middle.z - 0.1))).normalized()
            way = (out + Vector((rng.uniform(-0.6, 0.6), rng.uniform(-0.6, 0.6), (-1.6 if droop else rng.uniform(-0.4, 0.4))))).normalized()
            side = way.cross(Vector((0.0, 0.0, 1.0)))
            side = Vector((1.0, 0.0, 0.0)) if side.length < 1e-3 else side.normalized()
            side = Matrix.Rotation(rng.uniform(-0.5, 0.5), 3, way) @ side
            small_leaf(leaves, at, way, side, rng.uniform(0.075, 0.1), rng.uniform(0.038, 0.05))
    # Held inside its own square, so that a plant stood in a corner reaches no leaf through either wall.
    reach = max(math.hypot(vert.co.x, vert.co.y) for part in (leaves, stems) for vert in part.verts)
    if reach > PLANT_REACH:
        for part in (leaves, stems):
            for vert in part.verts:
                vert.co.x *= PLANT_REACH / reach
                vert.co.y *= PLANT_REACH / reach
    model.turned(leaves, "plastic", sharp=60.0)
    if stems.verts:
        model.turned(stems, "dark_wood", sharp=50.0)
    else:
        stems.free()
    return model


def barrels_and_pots():
    """The barrels that catch rain and the plants in pots that Outdoors.java and Potted.java place."""
    plants = []
    for plant in ("evergreen", "dragon_tree", "snake_plant", "cast_iron", "ficus"):
        plants += [potted(plant, plant), potted(plant + "_dry", plant, droop=True), potted("pot_" + plant, plant, grown=False)]
    return [rain_barrel("rain_barrel"), rain_barrel("rain_barrel_water", water=True), rain_barrel("rain_barrel_tarp", tarp=True),
            rain_barrel("rain_barrel_lid", tarp=True, lid=True)] + plants


def acorn(casing, glass, foot, cx=0.0):
    """An acorn lantern standing on foot over (cx, 0): a cup, then a round glass that swells to a band and narrows to a knob."""
    lathe(casing, [(0.0, foot), (0.035, foot), (0.05, foot + 0.05), (0.085, foot + 0.15), (0.11, foot + 0.21), (0.112, foot + 0.22), (0.0, foot + 0.22)],
          cx=cx, segments=16)
    rim = foot + 0.22
    lathe(glass, [(0.0, rim), (0.115, rim), (0.15, rim + 0.12), (0.166, rim + 0.22), (0.16, rim + 0.28), (0.13, rim + 0.36), (0.085, rim + 0.43),
                  (0.05, rim + 0.46), (0.045, rim + 0.5), (0.03, rim + 0.535), (0.0, rim + 0.545)], cx=cx, segments=20)
    lathe(casing, [(0.155, rim + 0.2), (0.172, rim + 0.215), (0.172, rim + 0.265), (0.155, rim + 0.28)], cx=cx, segments=20, closed=True, caps=False)


def acorn_head():
    """The upper length of a lamp post the game has in two sprites, one a level above the other: the top of the post with
    its two collars and the acorn lantern on it. It stands on the floor of the upper level, over the post below."""
    model = Model("acorn_head", centred=False)
    shaft = bmesh.new()
    lathe(shaft, [(0.0, 0.0), (0.066, 0.0), (0.066, 1.23), (0.088, 1.25), (0.088, 1.31), (0.066, 1.33), (0.066, 1.42), (0.088, 1.44),
                  (0.088, 1.52), (0.06, 1.55), (0.04, 1.57), (0.0, 1.57)], segments=14)
    model.turned(shaft, "paint", sharp=40.0)
    casing, glass = bmesh.new(), bmesh.new()
    acorn(casing, glass, 1.57)
    model.turned(casing, "paint", sharp=40.0)
    model.turned(glass, "shade", sharp=50.0)
    return model


def twin_acorn_head():
    """The upper length of a lamp post with two acorn lanterns, the game's second sprite of it: the top of the post with a
    collar and a finial, and an arm out to each side along x, held up by a scroll, with a lantern on its end. Each lantern's
    cup ends in a spike underneath. The origin is the post's axis."""
    model = Model("twin_acorn_head", centred=False)
    shaft = bmesh.new()
    lathe(shaft, [(0.0, 0.0), (0.066, 0.0), (0.066, 1.66), (0.088, 1.69), (0.088, 1.86), (0.07, 1.88), (0.05, 1.92), (0.045, 1.96), (0.06, 2.0),
                  (0.05, 2.05), (0.02, 2.1), (0.0, 2.14)], segments=14)
    model.turned(shaft, "paint", sharp=40.0)
    iron, casing, glass = bmesh.new(), bmesh.new(), bmesh.new()
    reach, foot = 0.55, 1.57
    for side in (-1, 1):
        beam(iron, (side * 0.06, 0.0, 1.74), (side * (reach - 0.07), 0.0, 1.7), 0.03, 0.03)
        # The scroll: from low on the post out and up to the arm, with a curl where it leaves the post.
        curve = [(0.066 + 0.035 + 0.035 * math.cos(math.radians(a)), 1.47 + 0.035 * math.sin(math.radians(a))) for a in range(180, 450, 45)]
        curve += [(0.14 + 0.25 * t, 1.5 + 0.21 * t ** 0.6 - 0.03 * math.sin(math.pi * t)) for t in (0.2, 0.4, 0.6, 0.8, 1.0)]
        for (x0, z0), (x1, z1) in zip(curve, curve[1:]):
            beam(iron, (side * x0, 0.0, z0), (side * x1, 0.0, z1), 0.016, 0.016)
        lathe(casing, [(0.0, foot - 0.26), (0.012, foot - 0.2), (0.035, foot), (0.0, foot)], cx=side * reach, segments=10)
        acorn(casing, glass, foot, cx=side * reach)
    model.hard(iron, "paint", bevel=0.004, segments=1)
    model.turned(casing, "paint", sharp=40.0)
    model.turned(glass, "shade", sharp=50.0)
    return model


def upright_bar(bm, angle, radius, wide, deep, low, high, out=0.0):
    """A flat bar standing on a circle round the upright at angle, wide along the circle and deep across it, from low to high;
    out moves its top that much further from the axis than its foot."""
    way = Vector((math.cos(angle), math.sin(angle), 0.0))
    along = Vector((-way.y, way.x, 0.0))
    rings = []
    for z, r in ((low, radius), (high, radius + out)):
        rings.append([bm.verts.new(way * (r + dr * deep / 2) + along * (da * wide / 2) + Vector((0.0, 0.0, z)))
                      for dr, da in ((-1, -1), (-1, 1), (1, 1), (1, -1))])
    bm.faces.new(rings[0][::-1])
    bm.faces.new(rings[1])
    for i in range(4):
        j = (i + 1) % 4
        bm.faces.new((rings[0][i], rings[0][j], rings[1][j], rings[1][i]))


def slatted_bin():
    """A street's litter bin of steel slats round a liner: a band at its foot and one at the top of the slats, which then flare
    out to a rolled rim, and inside the rim a dished lid with a round hole in the middle."""
    model = Model("slatted_bin")
    steel = bmesh.new()
    slats = 24
    for n in range(slats):
        angle = 2 * math.pi * (n + 0.5) / slats
        upright_bar(steel, angle, 0.3, 0.035, 0.012, 0.03, 0.72)
        upright_bar(steel, angle, 0.3, 0.035, 0.012, 0.72, 0.885, out=0.075)
    model.hard(steel, "paint", bevel=0.003, segments=1)
    bands = bmesh.new()
    lathe(bands, [(0.29, 0.0), (0.313, 0.0), (0.313, 0.06), (0.29, 0.06)], segments=32, closed=True, caps=False)
    lathe(bands, [(0.29, 0.68), (0.313, 0.68), (0.313, 0.74), (0.29, 0.74)], segments=32, closed=True, caps=False)
    lathe(bands, [(0.36 + 0.016 * math.cos(math.radians(a)), 0.893 + 0.016 * math.sin(math.radians(a))) for a in range(0, 360, 60)],
          segments=32, closed=True, caps=False)
    model.turned(bands, "paint", sharp=40.0)
    liner = bmesh.new()
    lathe(liner, [(0.0, 0.03), (0.27, 0.03), (0.27, 0.79), (0.0, 0.79)], segments=24)
    model.turned(liner, "plastic", sharp=40.0)
    lid = bmesh.new()
    lathe(lid, [(0.09, 0.8), (0.33, 0.84), (0.36, 0.865), (0.36, 0.875), (0.32, 0.855), (0.1, 0.82)], segments=28, closed=True, caps=False)
    model.turned(lid, "metal", sharp=40.0)
    return model


def picket_post():
    """The post at the end or corner of a picket fence: a square of painted timber with its top cut to a low point."""
    model = Model("picket_post", centred=False)
    timber = bmesh.new()
    prism(timber, (0.0, 0.0, 0.086, 0.086, 0.0), (0.0, 0.0, 0.086, 0.086, 0.9))
    prism(timber, (0.0, 0.0, 0.086, 0.086, 0.9), (0.0, 0.0, 0.03, 0.03, 0.925))
    model.hard(timber, "paint", bevel=0.004, segments=1)
    return model


def rail_post():
    """A sawn post of a fence of rails or wire: a square of rough timber, flat on top."""
    model = Model("rail_post", centred=False)
    timber = bmesh.new()
    cuboid(timber, -0.055, 0.055, -0.055, 0.055, 0.0, 0.995)
    model.hard(timber, "wood", bevel=0.006, segments=1)
    return model


def pipe_post(name, high):
    """A steel tube that holds up a fence of mesh: a flange at its foot and a round cap on its top."""
    model = Model(name, centred=False)
    tube = bmesh.new()
    lathe(tube, [(0.0, 0.0), (0.044, 0.0), (0.044, 0.012), (0.024, 0.03), (0.022, high - 0.035), (0.044, high - 0.025), (0.044, high - 0.012),
                 (0.03, high), (0.0, high)], segments=12)
    model.turned(tube, "paint", sharp=40.0)
    return model


def block_post():
    """A tall square steel post set in a block of concrete on the ground."""
    model = Model("block_post", centred=False)
    block = bmesh.new()
    cuboid(block, -0.145, 0.145, -0.145, 0.145, 0.0, 0.27)
    model.hard(block, "metal", bevel=0.01, segments=2)
    steel = bmesh.new()
    cuboid(steel, -0.031, 0.031, -0.031, 0.031, 0.27, 2.52)
    model.hard(steel, "paint", bevel=0.004, segments=1)
    return model


def street():
    """Lamp heads, a litter bin and fence posts along streets and parks."""
    return [acorn_head(), twin_acorn_head(), slatted_bin(), picket_post(), rail_post(), pipe_post("pipe_post", 1.26), pipe_post("pipe_post_tall", 2.16),
            block_post()]


def toward(start, end):
    """Moves something turned about the upright so that its axis runs from start towards end."""
    way = (Vector(end) - Vector(start)).normalized()
    return Matrix.Translation(start) @ Vector((0.0, 0.0, 1.0)).rotation_difference(way).to_matrix().to_4x4()


def pay_phone():
    """Two pay phones side by side on two posts against a wall, the wall at +Y: a back plate, a hood over each phone
    with a side panel between and either side, and a ledge under them."""
    model = Model("pay_phone")
    back = 0.17
    posts = bmesh.new()
    for x in (-0.24, 0.24):
        cuboid(posts, x - 0.04, x + 0.04, back - 0.12, back - 0.04, 0.0, 1.0)
    model.hard(posts, "plastic", bevel=0.006, segments=1)
    case = bmesh.new()
    cuboid(case, -0.44, 0.44, back - 0.03, back, 0.95, 1.85)
    cuboid(case, -0.46, 0.46, -back, back, 1.85, 1.89)
    cuboid(case, -0.44, 0.44, -0.1, back - 0.03, 0.95, 0.99)
    model.hard(case, "steel", bevel=0.005, segments=1)
    sides = bmesh.new()
    for x in (-0.445, 0.0, 0.445):
        cuboid(sides, x - 0.013, x + 0.013, -back + 0.01, back - 0.03, 0.95, 1.85)
    model.hard(sides, "paint", bevel=0.004, segments=1)
    phones = bmesh.new()
    for x in (-0.22, 0.22):
        cuboid(phones, x - 0.09, x + 0.09, back - 0.12, back - 0.03, 1.26, 1.66)
        cuboid(phones, x - 0.07, x + 0.07, back - 0.15, back - 0.03, 1.1, 1.16)
    model.hard(phones, "metal", bevel=0.008, segments=2)
    dark = bmesh.new()
    for x in (-0.22, 0.22):
        cuboid(dark, x - 0.055, x + 0.055, back - 0.125, back - 0.12, 1.4, 1.54)
        cuboid(dark, x - 0.06, x - 0.02, back - 0.16, back - 0.12, 1.5, 1.6)
        lathe(dark, [(0.0, 0.0), (0.022, 0.0), (0.022, 0.24), (0.0, 0.24)], segments=10, to=Matrix.Translation((x - 0.115, back - 0.15, 1.33)))
        cuboid(dark, x - 0.135, x - 0.095, back - 0.17, back - 0.13, 1.3, 1.34)
        cuboid(dark, x - 0.135, x - 0.095, back - 0.17, back - 0.13, 1.56, 1.6)
    model.hard(dark, "rubber", bevel=0.003, segments=1)
    return model


def deer_trophy():
    """A deer's head mounted on an oval plaque on a wall, the wall at +Y: the neck out of the plaque, the head held up
    looking ahead, ears, and the antlers branching up and forward. Its foot is the foot of the plaque."""
    model = Model("deer_trophy")
    back = 0.2
    plaque = bmesh.new()
    lathe(plaque, [(0.0, 0.0), (0.15, 0.0), (0.15, 0.026), (0.134, 0.04), (0.0, 0.04)], segments=28, sy=1.35, to=along_y(0.0, back, 0.2))
    model.turned(plaque, "dark_wood", sharp=35.0)
    head = bmesh.new()
    neck_from, neck_to = (0.0, back - 0.03, 0.22), (0.0, 0.02, 0.36)
    length = (Vector(neck_to) - Vector(neck_from)).length
    lathe(head, [(0.0, 0.0), (0.1, 0.0), (0.095, length * 0.4), (0.075, length * 0.85), (0.0, length * 1.15)], segments=18, sx=0.8,
          to=toward(neck_from, neck_to))
    skull_from, snout = (0.0, 0.07, 0.4), (0.0, -0.2, 0.33)
    length = (Vector(snout) - Vector(skull_from)).length
    lathe(head, [(0.0, 0.0), (0.06, 0.015), (0.08, 0.06), (0.075, 0.11), (0.055, 0.17), (0.04, 0.22), (0.028, length - 0.01), (0.0, length)],
          segments=18, sx=0.85, to=toward(skull_from, snout))
    for side in (-1, 1):
        lathe(head, [(0.0, 0.0), (0.028, 0.0), (0.034, 0.045), (0.022, 0.085), (0.0, 0.1)], segments=10, sx=0.35,
              to=toward((side * 0.04, 0.04, 0.44), (side * 0.15, 0.08, 0.5)))
    model.turned(head, "wood", sharp=50.0)
    dark = bmesh.new()
    lathe(dark, [(0.0, 0.0), (0.024, 0.004), (0.026, 0.02), (0.0, 0.032)], segments=10, sx=1.2, to=toward((0.0, -0.17, 0.335), (0.0, -0.21, 0.325)))
    for side in (-1, 1):
        lathe(dark, [(0.0, 0.0), (0.012, 0.003), (0.012, 0.012), (0.0, 0.016)], segments=8,
              to=toward((side * 0.05, -0.03, 0.4), (side * 0.07, -0.03, 0.4)))
    model.turned(dark, "rubber", sharp=60.0)
    antlers = bmesh.new()
    for side in (-1, 1):
        main = [(side * 0.04, 0.03, 0.44), (side * 0.12, 0.05, 0.54), (side * 0.18, 0.03, 0.64), (side * 0.2, -0.04, 0.74), (side * 0.16, -0.1, 0.82)]
        for n in range(len(main) - 1):
            wide = 0.03 - n * 0.004
            beam(antlers, main[n], main[n + 1], wide, wide)
        for n, up in ((1, 0.13), (2, 0.12), (3, 0.09)):
            x, y, z = main[n]
            beam(antlers, main[n], (x - side * 0.02, y - 0.04, z + up), 0.018, 0.018)
        x, y, z = main[0]
        beam(antlers, (x + side * 0.03, y - 0.01, z + 0.04), (x + side * 0.05, y - 0.1, z + 0.09), 0.018, 0.018)
    model.hard(antlers, "worktop", bevel=0.004, segments=1)
    return model


def cell_slab(name, long, deep, apron):
    """A slab to sit or lie on fixed to a wall in a cell, the wall at +Y, on steel brackets, with an apron of the same
    stuff down its front and ends where it has one. Its top is the model's top."""
    model = Model(name)
    half, top, thick = long / 2, 0.36, 0.06
    back, front = deep / 2, -deep / 2
    slab = bmesh.new()
    cuboid(slab, -half, half, front, back, top - thick, top)
    if apron:
        cuboid(slab, -half, half, front, front + 0.035, top - thick - 0.11, top - thick)
        for x0, x1 in ((-half, -half + 0.035), (half - 0.035, half)):
            cuboid(slab, x0, x1, front + 0.035, back, top - thick - 0.11, top - thick)
    model.hard(slab, "paint", bevel=0.01, segments=2)
    brackets = bmesh.new()
    count = 3 if long > 1.2 else 2
    for n in range(count):
        x = -half + 0.2 + n * (long - 0.4) / (count - 1)
        cuboid(brackets, x - 0.02, x + 0.02, back - 0.012, back, 0.0, top - thick)
        cuboid(brackets, x - 0.02, x + 0.02, front + 0.08, back - 0.012, top - thick - 0.012, top - thick)
        beam(brackets, (x, back - 0.02, 0.03), (x, front + 0.1, top - thick - 0.02), 0.02, 0.012)
    model.hard(brackets, "metal", bevel=0.003, segments=1)
    return model


def chalkboard(name, length):
    """A chalkboard on a wall, the wall at +Y: a dark board in a wooden frame, a tray for chalk along its foot."""
    model = Model(name)
    half, high, back = length / 2, 1.28, 0.02
    board = bmesh.new()
    cuboid(board, -half + 0.04, half - 0.04, back - 0.016, back, 0.1, high - 0.04)
    model.hard(board, "rubber", bevel=0.002, segments=1)
    frame = bmesh.new()
    cuboid(frame, -half, half, back - 0.03, back, high - 0.045, high)
    cuboid(frame, -half, half, back - 0.03, back, 0.06, 0.105)
    for side in (-1, 1):
        x0, x1 = (half - 0.045, half) if side > 0 else (-half, -half + 0.045)
        cuboid(frame, x0, x1, back - 0.03, back, 0.105, high - 0.045)
    cuboid(frame, -half + 0.03, half - 0.03, back - 0.1, back - 0.03, 0.06, 0.075)
    cuboid(frame, -half + 0.03, half - 0.03, back - 0.1, back - 0.088, 0.075, 0.092)
    model.hard(frame, "wood", bevel=0.004, segments=1)
    return model


def wall_objects():
    """What the game hangs on a wall by the way it faces that stands out from it: the pieces WallObjects.java places."""
    return [pay_phone(), deer_trophy(), cell_slab("cell_seat", 0.85, 0.6, False), cell_slab("cell_bunk", 1.9, 0.75, True),
            chalkboard("chalkboard_3", 2.5), chalkboard("chalkboard_4", 3.5)]


# A storey of the game's map, in metres, and how far the corner legs of a lattice mast or a pylon's waist stand from its axis.
STOREY = 2.44949
MAST_HALF = 0.74


def strut(bm, start, end, size):
    """A steel member of a lattice from start to end, size across both ways."""
    beam(bm, start, end, size, size)


def lattice_base():
    """The four legged steel base the game's pylons and water towers stand on: legs splayed from feet 4.4 m apart, on
    concrete pads, to a waist of MAST_HALF at four and a half storeys, then straight up to five. Belts round it at each
    panel, crossed braces on each face and across it in plan at two belts. The origin is the axis on the ground."""
    model = Model("lattice_base", centred=False)
    foot, waist, top, pad = 2.2, 4.5 * STOREY, 5.0 * STOREY, 0.22

    def half(z):
        return foot + (MAST_HALF - foot) * min(z, waist) / waist

    corners = ((-1, -1), (1, -1), (1, 1), (-1, 1))
    belts = [pad, 1.7 * STOREY, 2.9 * STOREY, 3.9 * STOREY, waist, top]
    legs, braces = bmesh.new(), bmesh.new()
    for sx, sy in corners:
        strut(legs, (sx * foot, sy * foot, pad), (sx * MAST_HALF, sy * MAST_HALF, waist), 0.13)
        strut(legs, (sx * MAST_HALF, sy * MAST_HALF, waist), (sx * MAST_HALF, sy * MAST_HALF, top), 0.13)
    for z in belts[1:]:
        r = half(z)
        for i, (sx, sy) in enumerate(corners):
            tx, ty = corners[(i + 1) % 4]
            strut(braces, (sx * r, sy * r, z), (tx * r, ty * r, z), 0.07)
    for low, high in zip(belts, belts[1:]):
        a, b = half(low), half(high)
        for i, (sx, sy) in enumerate(corners):
            tx, ty = corners[(i + 1) % 4]
            strut(braces, (sx * a, sy * a, low), (tx * b, ty * b, high), 0.05)
            strut(braces, (tx * a, ty * a, low), (sx * b, sy * b, high), 0.05)
    for z in (belts[1], waist):
        r = half(z)
        strut(braces, (-r, -r, z), (r, r, z), 0.05)
        strut(braces, (r, -r, z), (-r, r, z), 0.05)
    model.plain(legs, "steel")
    model.plain(braces, "steel")
    pads = bmesh.new()
    for sx, sy in corners:
        cuboid(pads, sx * foot - 0.32, sx * foot + 0.32, sy * foot - 0.32, sy * foot + 0.32, 0.0, pad)
    model.hard(pads, "worktop", bevel=0.02, segments=1)
    return model


def lattice_storey(name, surface):
    """One storey of a square lattice mast, MAST_HALF from its axis to each corner leg: a belt half way up and at the
    top, and a cross of braces on each face of each half. Its foot belt is the top of whatever it stands on."""
    model = Model(name, centred=False)
    corners = ((-1, -1), (1, -1), (1, 1), (-1, 1))
    steel = bmesh.new()
    for sx, sy in corners:
        strut(steel, (sx * MAST_HALF, sy * MAST_HALF, 0.0), (sx * MAST_HALF, sy * MAST_HALF, STOREY), 0.1)
    for low, high in ((0.0, STOREY / 2), (STOREY / 2, STOREY)):
        for i, (sx, sy) in enumerate(corners):
            tx, ty = corners[(i + 1) % 4]
            strut(steel, (sx * MAST_HALF, sy * MAST_HALF, high), (tx * MAST_HALF, ty * MAST_HALF, high), 0.06)
            strut(steel, (sx * MAST_HALF, sy * MAST_HALF, low + 0.02), (tx * MAST_HALF, ty * MAST_HALF, high), 0.045)
            strut(steel, (tx * MAST_HALF, ty * MAST_HALF, low + 0.02), (sx * MAST_HALF, sy * MAST_HALF, high), 0.045)
    model.plain(steel, surface)
    return model


def pylon_arm(name, reach, root_low, root_high, tip_high, square_tip):
    """A pair of a pylon's cross-arms reaching out along y either side of the mast, each a truss: two lower chords from
    the mast's two legs on that face at root_low, two upper chords from the same legs at root_high, all four meeting at
    the tip, reach from the axis at tip_high. A square tip keeps the arm as wide as the mast to its end, with a cross bar
    there; any other comes to a point. Verticals and crossed braces between the chords. Heights are over the floor of
    the level the arm is drawn on, and the origin is the mast's axis on that floor."""
    model = Model(name, centred=False)
    steel = bmesh.new()
    panels = 4
    for side in (-1, 1):
        root = side * MAST_HALF

        def at(x_side, t, upper):
            """A point of a chord: x_side picks the leg it starts at, t runs from the mast (0) to the tip (1)."""
            x = x_side * MAST_HALF * (1.0 if square_tip else 1.0 - t)
            y = root + (side * reach - root) * t
            z = (root_high if upper else root_low) + (tip_high - (root_high if upper else root_low)) * t
            return (x, y, z)

        for x_side in (-1, 1):
            strut(steel, at(x_side, 0.0, False), at(x_side, 1.0, False), 0.09)
            strut(steel, at(x_side, 0.0, True), at(x_side, 1.0, True), 0.08)
            for k in range(1, panels):
                t = k / panels
                strut(steel, at(x_side, t, False), at(x_side, t, True), 0.045)
            for k in range(panels):
                t0, t1 = k / panels, (k + 1) / panels
                strut(steel, at(x_side, t0, k % 2 == 0), at(x_side, t1, k % 2 == 1), 0.04)
        for k in range(panels):
            t0, t1 = k / panels, (k + 1) / panels
            strut(steel, at(-1, t0, False), at(1, t1, False), 0.04)
            strut(steel, at(-1, t0, True), at(1, t0, True), 0.04)
        if square_tip:
            strut(steel, at(-1, 1.0, False), at(1, 1.0, False), 0.09)
    model.plain(steel, "steel")
    return model


# A strain insulator: how far it reaches out from where it hangs, the radius of its discs, and how far apart the two strings of a pair are.
INSULATOR_REACH, INSULATOR_DISC, INSULATOR_APART = 0.8, 0.085, 0.26


def insulator(name, strings):
    """Strings of glass discs that hold a wire's end off a pylon's arm, reaching along +x from the arm at the origin: a
    yoke plate at each end, and between them one string, or two side by side."""
    model = Model(name, centred=False)
    discs, metal = bmesh.new(), bmesh.new()
    ys = [0.0] if strings == 1 else [-INSULATOR_APART / 2, INSULATOR_APART / 2]
    count = 8
    for y in ys:
        lathe(metal, [(0.0, 0.0), (0.012, 0.0), (0.012, INSULATOR_REACH), (0.0, INSULATOR_REACH)], segments=6, to=along_x(0.0, y, 0.0))
        for k in range(count):
            x = 0.1 + (INSULATOR_REACH - 0.2) * (k + 0.5) / count
            lathe(discs, [(0.0, -0.022), (INSULATOR_DISC * 0.55, -0.025), (INSULATOR_DISC, -0.008), (INSULATOR_DISC, 0.004), (0.0, 0.02)],
                  segments=12, to=along_x(x, y, 0.0))
    for x in (0.0, INSULATOR_REACH):
        cuboid(metal, x - 0.015, x + 0.015, min(ys) - 0.04, max(ys) + 0.04, -0.03, 0.03)
    model.turned(discs, "ceramic")
    model.plain(metal, "metal")
    return model


def lattice_post(name, footed, surface="steel"):
    """One storey of a narrow square lattice column, as a substation's gantry is built of: four corner angles 0.54 m
    apart, belts half way and at the top, a cross on each face of each half. The lowest storey stands on a concrete pad."""
    model = Model(name)
    half = 0.27
    corners = ((-1, -1), (1, -1), (1, 1), (-1, 1))
    low = 0.16 if footed else 0.0
    steel = bmesh.new()
    for sx, sy in corners:
        strut(steel, (sx * half, sy * half, low), (sx * half, sy * half, STOREY), 0.06)
    for z0, z1 in ((low, STOREY / 2), (STOREY / 2, STOREY)):
        for i, (sx, sy) in enumerate(corners):
            tx, ty = corners[(i + 1) % 4]
            strut(steel, (sx * half, sy * half, z1), (tx * half, ty * half, z1), 0.04)
            strut(steel, (sx * half, sy * half, z0 + 0.01), (tx * half, ty * half, z1), 0.03)
    model.plain(steel, surface)
    if footed:
        pad = bmesh.new()
        cuboid(pad, -0.4, 0.4, -0.4, 0.4, 0.0, low)
        model.hard(pad, "worktop", bevel=0.02, segments=1)
    return model


def railing_ring(bm, radius, z, high, posts):
    """A round guard rail on a deck at height z: posts and a top and a middle rail, each rail a ring of short bars."""
    for k in range(posts):
        a = 2 * math.pi * k / posts
        cuboid(bm, radius * math.cos(a) - 0.02, radius * math.cos(a) + 0.02, radius * math.sin(a) - 0.02, radius * math.sin(a) + 0.02, z, z + high)
    for rail in (high, high * 0.5):
        for k in range(posts * 2):
            a0, a1 = 2 * math.pi * k / (posts * 2), 2 * math.pi * (k + 1) / (posts * 2)
            strut(bm, (radius * math.cos(a0), radius * math.sin(a0), z + rail), (radius * math.cos(a1), radius * math.sin(a1), z + rail), 0.04)


def water_tank():
    """The wooden water tank of a roof or a water tower, a storey over its foot: four legs braced in crosses carry a
    steel bowl and a round deck with a guard rail, and on that a staved barrel under a shallow cone of a roof with a
    ladder up its side. The origin is the axis at the legs' feet."""
    model = Model("water_tank", centred=False)
    deck, barrel_r, barrel_top, eave, roof_r, apex = STOREY, 1.1, 1.95 * STOREY, 2.0 * STOREY, 1.38, 2.4 * STOREY
    legs = bmesh.new()
    leg = 0.7
    corners = ((-1, -1), (1, -1), (1, 1), (-1, 1))
    for sx, sy in corners:
        strut(legs, (sx * leg, sy * leg, 0.0), (sx * leg, sy * leg, deck - 0.3), 0.08)
    for i, (sx, sy) in enumerate(corners):
        tx, ty = corners[(i + 1) % 4]
        strut(legs, (sx * leg, sy * leg, 0.1), (tx * leg, ty * leg, deck - 0.4), 0.04)
        strut(legs, (tx * leg, ty * leg, 0.1), (sx * leg, sy * leg, deck - 0.4), 0.04)
    model.plain(legs, "steel")
    bowl = bmesh.new()
    lathe(bowl, [(0.0, deck - 0.45), (0.5, deck - 0.4), (0.95, deck - 0.2), (1.15, deck - 0.05), (1.6, deck - 0.05), (1.6, deck), (0.0, deck)], segments=28)
    model.turned(bowl, "metal")
    rail = bmesh.new()
    railing_ring(rail, 1.55, deck, 0.75, 16)
    model.plain(rail, "vinyl")
    staves = bmesh.new()
    lathe(staves, [(0.0, deck), (barrel_r, deck), (barrel_r * 1.02, (deck + barrel_top) / 2), (barrel_r, barrel_top), (0.0, barrel_top)], segments=32)
    model.turned(staves, "wood", sharp=50.0)
    hoops = bmesh.new()
    for k in range(4):
        z = deck + 0.2 + (barrel_top - deck - 0.4) * k / 3
        lathe(hoops, [(barrel_r + 0.012, z - 0.025), (barrel_r + 0.022, z), (barrel_r + 0.012, z + 0.025)], segments=32, caps=False)
    model.turned(hoops, "metal")
    roof = bmesh.new()
    lathe(roof, [(0.0, eave - 0.06), (roof_r, eave - 0.06), (roof_r, eave), (0.05, apex), (0.0, apex + 0.05)], segments=32)
    model.turned(roof, "metal", sharp=30.0)
    ladder = bmesh.new()
    lx = barrel_r + 0.12
    for y in (-0.2, 0.2):
        strut(ladder, (lx, y, deck), (lx, y, eave - 0.05), 0.035)
        strut(ladder, (lx, y, eave - 0.05), (0.25, y, apex - 0.1), 0.035)
    for k in range(8):
        z = deck + 0.25 + (eave - deck - 0.4) * k / 7
        strut(ladder, (lx, -0.2, z), (lx, 0.2, z), 0.025)
    model.plain(ladder, "vinyl")
    return model


def storage_tank():
    """A welded steel storage tank on the ground, 5.5 m across: its wall on a plinth, a shallow coned roof with a hatch,
    a guard rail round the roof's edge, and a ladder up the wall at its rail's gap. The origin is the axis on the ground."""
    model = Model("storage_tank", centred=False)
    r, wall, rise = 2.75, 2.05, 0.2
    shell_ = bmesh.new()
    lathe(shell_, [(0.0, 0.0), (r + 0.06, 0.0), (r + 0.06, 0.12), (r, 0.14), (r, wall), (r + 0.03, wall), (r + 0.03, wall + 0.06), (0.4, wall + rise),
                   (0.0, wall + rise)], segments=48)
    model.turned(shell_, "paint", sharp=30.0)
    seams = bmesh.new()
    for z in (0.95, 1.9):
        lathe(seams, [(r + 0.004, z - 0.012), (r + 0.01, z), (r + 0.004, z + 0.012)], segments=48, caps=False)
    model.turned(seams, "metal")
    hatch = bmesh.new()
    cuboid(hatch, -0.6, 0.6, -0.6, 0.6, wall + rise - 0.1, wall + rise + 0.12)
    model.hard(hatch, "metal", bevel=0.015, segments=1)
    rail = bmesh.new()
    railing_ring(rail, r - 0.12, wall + 0.04, 0.75, 28)
    model.plain(rail, "vinyl")
    ladder = bmesh.new()
    a = math.radians(-8.0)
    out = r + 0.2
    for side in (-0.22, 0.22):
        ox, oy = out * math.cos(a) - side * math.sin(a), out * math.sin(a) + side * math.cos(a)
        strut(ladder, (ox, oy, 0.0), (ox, oy, wall + 0.85), 0.04)
    for k in range(10):
        z = 0.3 + (wall - 0.1) * k / 9
        p = [(out * math.cos(a) - s * math.sin(a), out * math.sin(a) + s * math.cos(a), z) for s in (-0.22, 0.22)]
        strut(ladder, p[0], p[1], 0.025)
    model.plain(ladder, "vinyl")
    return model


def silo_storey():
    """One storey of a steel silo 1.8 m across, standing on the one under it: the shell, a seam at its foot, and a lid
    over its top that the storey above covers."""
    model = Model("silo_storey", centred=False)
    r = 0.91
    body = bmesh.new()
    lathe(body, [(0.0, 0.0), (r, 0.0), (r, STOREY - 0.04), (r - 0.04, STOREY), (0.0, STOREY)], segments=32)
    model.turned(body, "paint", sharp=30.0)
    seam = bmesh.new()
    lathe(seam, [(r + 0.004, 0.02), (r + 0.012, 0.05), (r + 0.004, 0.08)], segments=32, caps=False)
    model.turned(seam, "metal")
    return model


def vent_tank():
    """A small steel tank on four short legs with two lagged vent pipes over its lid, 1.7 m across: rust gathered in a band
    round its foot, a seam round it at each of its plates, a lip round its lid and a hatch on it. The origin is its axis on
    the ground."""
    model = Model("vent_tank", centred=False)
    r, foot, top = 0.86, 0.22, 1.85
    body = bmesh.new()
    lathe(body, [(0.0, foot), (r, foot), (r, top - 0.03), (r - 0.03, top), (0.0, top)], segments=32)
    model.turned(body, "paint", sharp=30.0)
    rust = bmesh.new()
    lathe(rust, [(r + 0.004, foot), (r + 0.006, foot + 0.1), (r + 0.003, foot + 0.16)], segments=32, caps=False)
    model.turned(rust, "dark_wood")
    seams = bmesh.new()
    for z in (foot + 0.55, foot + 1.1):
        lathe(seams, [(r + 0.003, z - 0.012), (r + 0.01, z), (r + 0.003, z + 0.012)], segments=32, caps=False)
    lathe(seams, [(r - 0.04, top + 0.002), (r + 0.012, top - 0.01), (r + 0.012, top - 0.05), (r + 0.002, top - 0.06)], segments=32, caps=False)
    model.turned(seams, "metal")
    legs = bmesh.new()
    for k in range(4):
        a = math.pi / 4 + k * math.pi / 2
        x, y = (r - 0.05) * math.cos(a), (r - 0.05) * math.sin(a)
        cuboid(legs, x - 0.06, x + 0.06, y - 0.06, y + 0.06, 0.0, foot + 0.3)
    model.hard(legs, "dark_wood", bevel=0.01, segments=1)
    hatch = bmesh.new()
    lathe(hatch, [(0.0, top), (0.24, top), (0.24, top + 0.05), (0.22, top + 0.07), (0.0, top + 0.07)], cx=-0.32, cy=0.32, segments=20)
    strut(hatch, (-0.42, 0.32, top + 0.09), (-0.22, 0.32, top + 0.09), 0.015)
    model.turned(hatch, "metal")
    pipes = bmesh.new()
    collars = bmesh.new()
    for x, y in ((0.13, -0.37), (0.37, -0.13)):
        lathe(collars, [(0.0, top), (0.14, top), (0.14, top + 0.08), (0.0, top + 0.08)], cx=x, cy=y, segments=12)
        lathe(pipes, [(0.0, top + 0.08), (0.11, top + 0.08), (0.11, top + 0.45), (0.0, top + 0.45)], cx=x, cy=y, segments=12)
        for k in range(4):
            z = top + 0.14 + 0.08 * k
            lathe(pipes, [(0.11, z - 0.012), (0.118, z), (0.11, z + 0.012)], cx=x, cy=y, segments=12, caps=False)
        lathe(collars, [(0.0, top + 0.45), (0.13, top + 0.45), (0.13, top + 0.55), (0.0, top + 0.6)], cx=x, cy=y, segments=12)
    model.turned(pipes, "steel")
    model.turned(collars, "rubber")
    return model


def towers():
    """Lattice pylons and masts, water towers, and steel tanks and silos: the pieces Towers.java stands whole where the
    game draws them a square at a time."""
    return [lattice_base(), lattice_storey("lattice_storey", "steel"), lattice_storey("lattice_storey_white", "enamel"),
            lattice_storey("lattice_storey_red", "vinyl"),
            pylon_arm("pylon_arm_short", 3.0, 0.6 * STOREY, 1.0 * STOREY, 0.6 * STOREY, True),
            pylon_arm("pylon_arm_long", 5.0, 0.0, 0.6 * STOREY, 0.03 * STOREY, False),
            insulator("insulator_pair", 2), insulator("insulator_string", 1), lattice_post("lattice_post", False),
            lattice_post("lattice_post_foot", True), lattice_post("lattice_post_white", False, "enamel"), water_tank(), storage_tank(), silo_storey(), vent_tank()]
# Shop fittings, as ShopFittings.java stands them: the runs of shelving shops sell from, the rails and stands clothes hang
# on, a shop's open chiller, the seats of malls, bars and salons, and what the game paints on a fitting while it holds
# something, made: goods on shelves, clothes on rails. What a fitting holds is a model of its own, stood on one square of
# the fitting while the game paints that square's goods, so it is built where the fitting has its shelves or its rail
# and keeps the origin it was built round: see Model's centred.

# A run of shop shelving four squares long, its front to -Y: half its length, its front and back, the tops of its deck
# and its two shelves, how high its back stands and the pegboard of its last square, and where that square begins.
GONDOLA_HALF, GONDOLA_FRONT, GONDOLA_BACK = 1.975, -0.265, 0.265
GONDOLA_SHELVES = (0.14, 0.48, 0.82)
GONDOLA_TOP, PEGBOARD_TOP, PEGBOARD_FROM = 1.02, 1.19, 1.0
# The middle of the last square, where the pegboard is; where on that square the pegboard's hooks are, from its middle,
# and how high; and how far under the top of a shelf's edge the tickets on it come down to.
LAST_SQUARE, HOOKS_X, HOOKS_Z, TICKETS = 1.5, (-0.2, 0.2), (0.55, 0.88), 0.04
# How far either way from the middle of its square the goods on a square of shelving reach: clear of the end of the run.
GOODS_REACH = 0.43
# A clothes shop's long wooden run: half its length; its two ends stand higher than its back.
LONG_HALF, LONG_ENDS = 1.64, 1.28
# How high the top of the rail of a small clothes rail is, of a large rack's two rails and of an open wardrobe's, and
# how far apart a large rack's rails are, front to back.
RAIL_TOP, RACK_TOP, WARDROBE_RAIL_TOP, RACK_RAILS = 1.3, 1.65, 1.27, 0.56
# A clothes stand's four arms: the two along x stand higher than the two along y.
STAND_HIGH, STAND_LOW, STAND_ARM = 1.42, 1.24, 0.42
# A shop's open chiller one square wide: half its width, its front and back, and the tops of its deck and its shelves
# with how far back from the front each one's front edge is.
CHILLER_HALF, CHILLER_FRONT, CHILLER_BACK, CHILLER_TOP = 0.49, -0.4, 0.4, 2.2
CHILLER_SHELVES = ((0.32, 0.0), (0.66, 0.12), (0.98, 0.19), (1.30, 0.26), (1.62, 0.33))
# The colours of what is sold: packets, tins, bottles and clothes. Every one is a surface the mod already has.
PACKETS = ("books", "books", "vinyl", "cloth", "linen", "worktop", "dark_wood", "pillow", "shade")
TINS = ("steel", "steel", "vinyl", "enamel", "cloth")
BOTTLES = ("plastic", "vinyl", "linen", "ceramic", "cloth")
CLOTHES = ("cloth", "vinyl", "dark_wood", "rubber", "linen", "books", "plastic", "pillow", "cloth", "vinyl")


def parts(model):
    """A bmesh for each surface, made as it is first asked for, and the whole taken into the model by finish."""
    made = {}

    def part(surface):
        if surface not in made:
            made[surface] = bmesh.new()
        return made[surface]

    def finish(bevel=0.0):
        for surface, bm in made.items():
            if bevel:
                model.hard(bm, surface, bevel=bevel, segments=1)
            else:
                model.plain(bm, surface)
    return part, finish


def tube(bm, start, end, radius, segments=10):
    """A round bar from start to end."""
    start, end = Vector(start), Vector(end)
    way = end - start
    turn = way.normalized().to_track_quat("Z", "Y").to_matrix().to_4x4()
    lathe(bm, [(radius, 0.0), (radius, way.length)], segments=segments, to=Matrix.Translation(start) @ turn)


def mirrored(x0, x1, side):
    """x0 to x1 on the side +1, or the same on the other side of the middle for -1, low end first."""
    return (x0, x1) if side > 0 else (-x1, -x0)


def gondola(name, side, trim):
    """A run of shop shelving four squares long that stands in the middle of its squares: a deck and two shelves along
    three of them before a back, and on the fourth, at +X for side 1 and -X for -1, a pegboard with hooks over a foot
    rounded at its front corners. Its boards take the colour the sprite gives them; trim is what its edges are."""
    model = Model(name)
    half, front, back = GONDOLA_HALF, GONDOLA_FRONT, GONDOLA_BACK
    deck = GONDOLA_SHELVES[0]
    boards, edges, slats, hooks = bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new()
    run = mirrored(-half, PEGBOARD_FROM, side)
    cuboid(boards, *run, back - 0.03, back, deck, GONDOLA_TOP)
    cuboid(edges, *run, front + 0.015, back - 0.03, 0.0, deck - 0.025)
    cuboid(boards, *run, front + 0.015, back - 0.03, deck - 0.025, deck)
    cuboid(edges, *run, front, front + 0.015, 0.02, deck + 0.01)
    for z in GONDOLA_SHELVES[1:]:
        cuboid(boards, *run, front + 0.015, back - 0.03, z - 0.022, z)
        cuboid(edges, *run, front, front + 0.015, z - 0.045, z + 0.008)
    # The plain end, its front and top edged.
    end = mirrored(-half, -half + 0.035, side)
    cuboid(boards, *end, front + 0.012, back, 0.0, GONDOLA_TOP - 0.012)
    edged = mirrored(-half - 0.003, -half + 0.038, side)
    cuboid(edges, *edged, front - 0.003, front + 0.012, 0.0, GONDOLA_TOP)
    cuboid(edges, *edged, front - 0.003, back + 0.003, GONDOLA_TOP - 0.012, GONDOLA_TOP)
    # The last square: a pegboard in a frame over a foot rounded at its front corners.
    near, far = PEGBOARD_FROM, half
    round_off = 0.09
    outline = [(near, back - 0.03), (near, front + round_off)] + arc(near + round_off, front + round_off, round_off, 180.0, 270.0, 4)[1:] \
        + arc(far - round_off, front + round_off, round_off, 270.0, 360.0, 4) + [(far, back - 0.03)]
    if side < 0:
        outline = [(-x, y) for x, y in reversed(outline)]
    outline_up(edges, outline, 0.0, deck - 0.02)
    outline_up(boards, outline, deck - 0.02, deck)
    cuboid(boards, *mirrored(near + 0.04, far - 0.04, side), back - 0.04, back - 0.012, deck, PEGBOARD_TOP - 0.04)
    for x0, x1 in ((near, near + 0.04), (far - 0.04, far)):
        cuboid(edges, *mirrored(x0, x1, side), back - 0.05, back, 0.0, PEGBOARD_TOP)
    cuboid(edges, *mirrored(near, far, side), back - 0.05, back, PEGBOARD_TOP - 0.04, PEGBOARD_TOP)
    z = deck + 0.1
    while z < PEGBOARD_TOP - 0.08:
        cuboid(slats, *mirrored(near + 0.04, far - 0.04, side), back - 0.0425, back - 0.039, z, z + 0.006)
        z += 0.12
    for x in HOOKS_X:
        for z in HOOKS_Z:
            hx = side * (LAST_SQUARE + x)
            beam(hooks, (hx, back - 0.04, z), (hx, back - 0.25, z + 0.025), 0.007, 0.007)
            beam(hooks, (hx, back - 0.25, z + 0.025), (hx, back - 0.26, z + 0.05), 0.007, 0.007)
    model.hard(boards, "paint", bevel=0.004, segments=1)
    model.hard(edges, trim, bevel=0.004, segments=1)
    model.plain(slats, "plastic")
    model.hard(hooks, "metal", bevel=0.002, segments=1)
    return model


def long_shelves():
    """A clothes shop's run of wooden shelves along three squares and a little of a fourth: a back, a deck and two
    shelves between two ends that stand higher than the back."""
    model = Model("long_shelves")
    half, front, back = LONG_HALF, GONDOLA_FRONT, GONDOLA_BACK
    wood = bmesh.new()
    inner = (-half + 0.04, half - 0.04)
    cuboid(wood, *inner, back - 0.025, back, 0.0, GONDOLA_TOP)
    cuboid(wood, *inner, front + 0.02, back - 0.025, 0.0, GONDOLA_SHELVES[0])
    for z in GONDOLA_SHELVES[1:]:
        cuboid(wood, *inner, front + 0.01, back - 0.025, z - 0.025, z)
    for x in (-1, 1):
        cuboid(wood, *mirrored(half - 0.04, half, x), front, back, 0.0, LONG_ENDS)
    model.hard(wood, "wood", bevel=0.004, segments=1)
    return model


def packet(part, pick, x0, x1, y0, y1, z, high):
    cuboid(part(pick.choice(PACKETS)), x0, x1, y0, y1, z, z + high)


def tin(part, pick, x, y, z, radius, high):
    lathe(part(pick.choice(TINS)), [(0.0, z), (radius, z), (radius, z + high), (radius * 0.9, z + high + 0.004), (0.0, z + high + 0.004)],
          cx=x, cy=y, segments=8)


def bottle(part, pick, x, y, z, radius, high):
    lathe(part(pick.choice(BOTTLES)), [(0.0, z), (radius, z), (radius, z + high * 0.62), (radius * 0.4, z + high * 0.82), (radius * 0.32, z + high),
                                       (0.0, z + high)], cx=x, cy=y, segments=8)


def stocked(part, pick, levels, x0, x1, fill, depth_rows):
    """Fills shelves with what a shop sells, in rows across from x0 to x1: each row one kind of thing, as many deep as
    fit up to depth_rows, and a row's place left empty for 1 - fill of them. levels are (top of the board, the room over
    it, its front, its back)."""
    for z, room, y0, y1 in levels:
        x = x0
        while True:
            kind = pick.random()
            if kind < 0.45:
                wide, deep, high = pick.uniform(0.07, 0.16), pick.uniform(0.06, 0.18), min(pick.uniform(0.12, 0.26), room)
            elif kind < 0.75:
                radius = pick.uniform(0.032, 0.045)
                wide = deep = 2 * radius + 0.006
                high = min(pick.uniform(0.09, 0.14), room)
            else:
                radius = pick.uniform(0.035, 0.045)
                wide = deep = 2 * radius + 0.008
                high = min(pick.uniform(0.2, 0.3), room)
            if x + wide > x1:
                break
            if pick.random() < fill:
                rows = min(depth_rows, max(1, int((y1 - y0) / (deep + 0.01))))
                rows = pick.randint(1, rows) if fill < 0.6 else rows
                for row in range(rows):
                    y = y0 + 0.005 + row * (deep + 0.01)
                    if kind < 0.45:
                        packet(part, pick, x + 0.003, x + wide - 0.003, y, y + deep, z, high)
                    elif kind < 0.75:
                        tin(part, pick, x + wide / 2, y + deep / 2, z, wide / 2 - 0.003, high)
                    else:
                        bottle(part, pick, x + wide / 2, y + deep / 2, z, wide / 2 - 0.004, high)
            x += wide


def tickets(part, pick, tops, front, reach):
    """The price tickets on the front edge of each board that holds goods, from -reach to reach along it. A ticket on
    every board, the lowest included, comes down TICKETS under the board's top, which is where the goods' model has its foot."""
    for z in tops:
        for x in (-reach * 0.6, 0.0, reach * 0.6):
            x += pick.uniform(-0.06, 0.06)
            cuboid(part("pillow"), x - 0.03, x + 0.03, front - 0.003, front - 0.0005, z - TICKETS, z - TICKETS + 0.03)


def shelf_goods(name, seed, fill, depth_rows):
    """The goods on one square's length of a run of shelving, a square's length less a hand at each end: on its deck and
    both its shelves, with the tickets on their edges. Its foot is the foot of the tickets on the deck's edge."""
    model = Model(name, centred=False)
    part, finish = parts(model)
    pick = random.Random(seed)
    tops = list(GONDOLA_SHELVES) + [GONDOLA_SHELVES[-1] + 0.3]
    levels = [(z, tops[i + 1] - z - 0.04, GONDOLA_FRONT + 0.03, GONDOLA_BACK - 0.04) for i, z in enumerate(GONDOLA_SHELVES)]
    stocked(part, pick, levels, -GOODS_REACH, GOODS_REACH, fill, depth_rows)
    tickets(part, pick, GONDOLA_SHELVES, GONDOLA_FRONT, GOODS_REACH)
    finish()
    return model


def hook_goods(name, seed, count):
    """Packets hung on the four hooks of a pegboard, count to a hook, as many as fit on it. Its top is the top of the
    packets on the higher hooks."""
    model = Model(name, centred=False)
    part, finish = parts(model)
    pick = random.Random(seed)
    for x in HOOKS_X:
        for z in HOOKS_Z:
            colour = pick.choice(PACKETS)
            wide, high = pick.uniform(0.1, 0.14), pick.uniform(0.15, 0.2)
            for n in range(count):
                y = GONDOLA_BACK - 0.07 - n * 0.04
                cuboid(part(colour), x - wide / 2, x + wide / 2, y - 0.018, y, z - high + 0.02 - 0.004 * n, z + 0.02 - 0.004 * n)
    finish()
    return model


def folded(name, seed, stacks):
    """Folded clothes in piles on one square's length of the long wooden shelves, stacks piles to a board."""
    model = Model(name, centred=False)
    part, finish = parts(model)
    pick = random.Random(seed)
    tops = list(GONDOLA_SHELVES) + [GONDOLA_SHELVES[-1] + 0.3]
    for i, z in enumerate(GONDOLA_SHELVES):
        room = tops[i + 1] - z - 0.05
        places = [-0.28, 0.0, 0.28]
        for x in pick.sample(places, stacks):
            colour = pick.choice(CLOTHES)
            pile = z
            for n in range(pick.randint(3, max(3, int(room / 0.04)))):
                thick = pick.uniform(0.028, 0.04)
                if pile + thick > z + room:
                    break
                lean = pick.uniform(-0.01, 0.01)
                cuboid(part(colour if pick.random() < 0.6 else pick.choice(CLOTHES)), x - 0.13 + lean, x + 0.13 + lean, GONDOLA_FRONT + 0.03,
                       GONDOLA_FRONT + 0.3, pile, pile + thick - 0.002)
                pile += thick
    finish(bevel=0.008)
    return model


def garment(part, pick, x, y, top, across_y=True, longest=0.95, widest=0.46):
    """A garment on a hanger: the hook over the rail at top, the hanger's bar, and the garment hung from it with its
    shoulders sloping down from the hanger's neck and its hem a little wider or narrower than its shoulders. Its width
    is across y, or across x where across_y is False."""
    colour = pick.choice(CLOTHES)
    long = pick.uniform(0.55, longest)
    wide, thick = pick.uniform(widest - 0.08, widest), pick.uniform(0.035, 0.06)
    shoulder = top - 0.075
    hem = wide / 2 * pick.uniform(0.85, 1.15)
    outline = [(-0.07, shoulder + 0.01), (0.07, shoulder + 0.01), (wide / 2, shoulder - 0.06), (hem, shoulder - long), (-hem, shoulder - long),
               (-wide / 2, shoulder - 0.06)]
    if across_y:
        outline_along_x(part(colour), [(y + across, z) for across, z in outline], x - thick / 2, x + thick / 2)
        beam(part("metal"), (x, y - wide / 2 + 0.03, shoulder + 0.005), (x, y + wide / 2 - 0.03, shoulder + 0.005), 0.006, 0.006)
    else:
        outline_along_y(part(colour), [(x + across, z) for across, z in outline], y - thick / 2, y + thick / 2)
        beam(part("metal"), (x - wide / 2 + 0.03, y, shoulder + 0.005), (x + wide / 2 - 0.03, y, shoulder + 0.005), 0.006, 0.006)
    beam(part("metal"), (x, y, shoulder + 0.005), (x, y, top), 0.005, 0.005)


def rail_garments(name, seed, rails, length, count, longest=0.95, widest=0.46):
    """Clothes hung along rails that run along x, one square's length of them: rails is where each rail is across y.
    Its top is the tops of the hooks, which hang over the rail's top."""
    model = Model(name, centred=False)
    part, finish = parts(model)
    pick = random.Random(seed)
    for y in rails:
        step = length / count
        for n in range(count):
            garment(part, pick, -length / 2 + step * (n + 0.5) + pick.uniform(-0.015, 0.015), y, 0.0, longest=longest, widest=widest)
    finish(bevel=0.006)
    return model


def stand_garments(name, seed, count):
    """Clothes on the four arms of a clothes stand: along each arm, count of them, from its high end out. Its top is the
    tops of the hooks on the two higher arms."""
    model = Model(name, centred=False)
    part, finish = parts(model)
    pick = random.Random(seed)
    for axis, top in ((0, 0.0), (1, STAND_LOW - STAND_HIGH)):
        for side in (-1, 1):
            for n in range(count):
                out = side * (0.12 + (STAND_ARM - 0.14) * (n + 0.5) / count)
                if axis == 0:
                    garment(part, pick, out, 0.0, top, across_y=True, longest=0.75)
                else:
                    garment(part, pick, 0.0, out, top, across_y=False, longest=0.65)
    finish(bevel=0.006)
    return model


def castor_foot(bm, wheels, x, reach):
    """The foot of a rail that can be wheeled: a bar across y under the upright at x, a wheel under each end."""
    beam(bm, (x, -reach, 0.06), (x, reach, 0.06), 0.03, 0.024)
    for y in (-reach + 0.02, reach - 0.02):
        lathe(wheels, [(0.0, -0.012), (0.026, -0.012), (0.03, 0.0), (0.026, 0.012), (0.0, 0.012)], segments=10, to=along_x(x - 0.012, y, 0.03))


def clothes_rail():
    """A shop's clothes rail on wheels, two squares long: two uprights on wheeled feet and the rail across their tops."""
    model = Model("clothes_rail")
    steel, wheels = bmesh.new(), bmesh.new()
    reach = 0.82
    for x in (-reach, reach):
        tube(steel, (x, 0.0, 0.06), (x, 0.0, RAIL_TOP - 0.013), 0.013)
        castor_foot(steel, wheels, x, 0.22)
    tube(steel, (-reach - 0.06, 0.0, RAIL_TOP - 0.013), (reach + 0.06, 0.0, RAIL_TOP - 0.013), 0.013)
    model.turned(steel, "steel", sharp=40.0)
    model.turned(wheels, "rubber", sharp=60.0)
    return model


def clothes_rack():
    """A shop's large clothes rack, two squares long: a frame of tube with a rail along each side at the top, joined at
    the ends, on a frame of tube on the floor."""
    model = Model("clothes_rack")
    steel = bmesh.new()
    half, across, r = 0.94, RACK_RAILS / 2, 0.013
    for y in (-across, across):
        tube(steel, (-half, y, RACK_TOP - r), (half, y, RACK_TOP - r), r)
        tube(steel, (-half + 0.08, y, 0.03), (half - 0.08, y, 0.03), r)
        for x in (-half + 0.08, half - 0.08):
            tube(steel, (x, y, 0.0), (x, y, RACK_TOP - r), r)
    for x in (-half + 0.08, half - 0.08):
        tube(steel, (x, -across, 0.03), (x, across, 0.03), r)
        tube(steel, (x, -across, RACK_TOP - 0.2), (x, across, RACK_TOP - 0.2), r)
    model.turned(steel, "steel", sharp=40.0)
    return model


def clothes_stand():
    """A shop's clothes stand: an upright on a cross of feet, and four arms out from it, the two along x higher than the
    two along y."""
    model = Model("clothes_stand")
    steel = bmesh.new()
    r = 0.012
    tube(steel, (0.0, 0.0, 0.02), (0.0, 0.0, STAND_HIGH + 0.08), 0.016)
    for x, y in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        tube(steel, (0.0, 0.0, 0.02), (x * 0.3, y * 0.3, 0.02), 0.014)
    for top, axis in ((STAND_HIGH, 0), (STAND_LOW, 1)):
        for side in (-1, 1):
            end = (side * STAND_ARM, 0.0) if axis == 0 else (0.0, side * STAND_ARM)
            tube(steel, (0.0, 0.0, top - 0.06), (end[0] * 0.3, end[1] * 0.3, top - r), r)
            tube(steel, (end[0] * 0.3, end[1] * 0.3, top - r), (end[0], end[1], top - r), r)
            tube(steel, (end[0], end[1], top - r), (end[0] * 1.03, end[1] * 1.03, top + 0.03), r)
    model.turned(steel, "steel", sharp=40.0)
    return model


def open_wardrobe():
    """A clothes shop's open cupboard three squares long that coats hang in: two ends, a back, a top and a plinth, and a
    rail along under the top."""
    model = Model("open_wardrobe")
    half, front, back, top = 1.45, -0.29, 0.29, 1.45
    wood, panels, steel = bmesh.new(), bmesh.new(), bmesh.new()
    cuboid(wood, -half + 0.03, half - 0.03, back - 0.02, back, 0.0, top - 0.03)
    cuboid(wood, -half, half, front - 0.02, back, top - 0.03, top)
    cuboid(wood, -half + 0.03, half - 0.03, front + 0.02, back - 0.02, 0.0, 0.08)
    for side in (-1, 1):
        cuboid(wood, *mirrored(half - 0.03, half, side), front, back, 0.0, top - 0.03)
        # A panelled face to each end.
        for z0, z1 in ((0.12, 0.62), (0.7, top - 0.12)):
            cuboid(panels, *mirrored(half - 0.001, half + 0.008, side), front + 0.08, back - 0.08, z0, z1)
    tube(steel, (-half + 0.03, 0.02, WARDROBE_RAIL_TOP - 0.012), (half - 0.03, 0.02, WARDROBE_RAIL_TOP - 0.012), 0.012)
    model.hard(wood, "wood", bevel=0.005, segments=1)
    model.hard(panels, "wood", bevel=0.006, segments=1)
    model.turned(steel, "steel", sharp=40.0)
    return model


def open_chiller():
    """One square of a shop's open chiller: a cabinet open at the front, its sides cut back as they rise, a deck and four
    shelves that each stop further back, a lit sign along the canopy and a grille along its foot."""
    model = Model("open_chiller")
    half, front, back, top = CHILLER_HALF, CHILLER_FRONT, CHILLER_BACK, CHILLER_TOP
    body, shelves, dark, lit = bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new()
    cuboid(body, -half, half, back - 0.05, back, 0.0, top)
    cuboid(body, -half, half, front + 0.15, back - 0.05, top - 0.06, top)
    cuboid(body, -half, half, front + 0.02, back - 0.05, 0.06, CHILLER_SHELVES[0][0])
    side = [(front, 0.0), (back, 0.0), (back, top), (front + 0.15, top), (front + 0.15, top - 0.28), (front + 0.32, 1.85), (front + 0.03, 0.9),
            (front, CHILLER_SHELVES[0][0])]
    for x in (-1, 1):
        outline_along_x(body, side, *mirrored(half - 0.03, half, x))
    # The canopy over the open front, with the sign along it.
    prism_y(body, (0.0, top - 0.14, 2 * half - 0.06, 0.16, front + 0.15), (0.0, top - 0.14, 2 * half - 0.06, 0.16, front + 0.4))
    cuboid(lit, -half + 0.03, half - 0.03, front + 0.135, front + 0.15, top - 0.21, top - 0.07)
    cuboid(dark, -half + 0.03, half - 0.03, front + 0.0, front + 0.02, 0.0, CHILLER_SHELVES[0][0] - 0.02)
    for z, setback in CHILLER_SHELVES[1:]:
        prism_y(shelves, (0.0, z - 0.03, 2 * half - 0.06, 0.02, front + setback), (0.0, z - 0.008, 2 * half - 0.06, 0.02, back - 0.05))
        cuboid(shelves, -half + 0.03, half - 0.03, front + setback - 0.01, front + setback, z - 0.05, z)
    model.hard(body, "enamel", bevel=0.006, segments=1)
    model.hard(shelves, "steel", bevel=0.003, segments=1)
    model.plain(dark, "rubber")
    model.plain(lit, "worktop")
    return model


def chiller_goods(name, seed, fill, depth_rows):
    """The drinks, cartons and packets on the deck and the shelves of one square of an open chiller, with the tickets on
    their edges. Its foot is the foot of the tickets on the deck's edge."""
    model = Model(name, centred=False)
    part, finish = parts(model)
    pick = random.Random(seed)
    tops = [z for z, _ in CHILLER_SHELVES] + [CHILLER_SHELVES[-1][0] + 0.33]
    levels = [(z, tops[i + 1] - z - 0.05, CHILLER_FRONT + setback + 0.03, CHILLER_BACK - 0.08) for i, (z, setback) in enumerate(CHILLER_SHELVES)]
    stocked(part, pick, levels, -CHILLER_HALF + 0.04, CHILLER_HALF - 0.04, fill, depth_rows)
    for z, setback in CHILLER_SHELVES:
        tickets(part, pick, (z,), CHILLER_FRONT + (setback - 0.01 if setback else 0.0), CHILLER_HALF - 0.08)
    finish()
    return model


def padded(model, surface, x0, x1, y0, y1, z0, z1, crown):
    """A pad of upholstery: a box with soft edges, fuller in the middle of its top."""
    part = bmesh.new()
    cage = grid_box(part, spread(x0, x1, 2), spread(y0, y1, 2), [z0, z1])
    cage[(1, 1, 1)].co.z += crown
    crease(part, cage, 0.3)
    model.soft(part, surface, levels=2)


def mall_chair():
    """A mall's swivel chair: a round padded seat on a column and a foot, in a low shell back that wraps round its rear
    half. Its seat is 0.47 high."""
    model = Model("mall_chair")
    seat = 0.47
    base = bmesh.new()
    lathe(base, [(0.0, 0.0), (0.24, 0.0), (0.245, 0.012), (0.2, 0.035), (0.05, 0.05), (0.035, 0.06), (0.035, seat - 0.09), (0.12, seat - 0.07),
                 (0.0, seat - 0.06)], segments=24)
    model.turned(base, "plastic", sharp=50.0)
    cushion = bmesh.new()
    lathe(cushion, [(0.0, seat - 0.07), (0.235, seat - 0.07), (0.255, seat - 0.05), (0.258, seat - 0.02), (0.24, seat), (0.0, seat + 0.012)], segments=24)
    model.turned(cushion, "vinyl", sharp=60.0)
    shell = bmesh.new()
    outer, inner, low, high = 0.27, 0.235, seat - 0.08, seat + 0.38
    steps = 12
    rings = []
    for radius in (outer, inner):
        ring = []
        for i in range(steps + 1):
            angle = math.radians(15 + 150 * i / steps)
            ring.append((radius * math.cos(angle), radius * math.sin(angle)))
        rings.append(ring)
    outline = rings[0] + rings[1][::-1]
    outline_up(shell, outline, low, high)
    model.turned(shell, "dark_wood", sharp=35.0)
    return model


def pedestal_stool():
    """A diner's or a bar's stool fixed on one column: a round foot, a ring for the feet and a round padded seat. Its seat
    is 0.75 high."""
    model = Model("pedestal_stool")
    seat = 0.75
    steel = bmesh.new()
    lathe(steel, [(0.0, 0.0), (0.2, 0.0), (0.2, 0.012), (0.12, 0.04), (0.03, 0.06), (0.028, seat - 0.08), (0.1, seat - 0.075), (0.11, seat - 0.065),
                  (0.0, seat - 0.065)], segments=24)
    lathe(steel, [(0.16, 0.26), (0.172, 0.27), (0.16, 0.28), (0.148, 0.27)], segments=20, closed=True)
    for n in range(3):
        angle = math.radians(90 + n * 120)
        tube(steel, (0.0, 0.0, 0.27), (0.152 * math.cos(angle), 0.152 * math.sin(angle), 0.27), 0.008, segments=8)
    model.turned(steel, "steel", sharp=50.0)
    cushion = bmesh.new()
    lathe(cushion, [(0.0, seat - 0.075), (0.18, seat - 0.075), (0.195, seat - 0.055), (0.198, seat - 0.02), (0.18, seat), (0.0, seat + 0.01)],
          segments=24)
    model.turned(cushion, "vinyl", sharp=60.0)
    return model


def salon_chair():
    """A hairdresser's chair: a padded seat with padded arms and a high back on a column over a round foot, and a bar
    before it for the feet. Its seat is 0.52 high, its back at +Y."""
    model = Model("salon_chair")
    seat = 0.52
    steel = bmesh.new()
    lathe(steel, [(0.0, 0.0), (0.28, 0.0), (0.28, 0.015), (0.2, 0.05), (0.05, 0.07), (0.045, 0.3), (0.035, 0.32), (0.035, seat - 0.12), (0.0, seat - 0.12)],
          segments=24)
    cuboid(steel, -0.2, 0.2, -0.22, 0.2, seat - 0.13, seat - 0.1)
    for x in (-0.25, 0.25):
        beam(steel, (x, 0.12, seat - 0.11), (x, -0.16, seat + 0.18), 0.03, 0.012)
    tube(steel, (-0.12, -0.42, 0.14), (0.12, -0.42, 0.14), 0.014)
    for x in (-0.12, 0.12):
        beam(steel, (x, -0.42, 0.14), (x * 1.2, -0.2, seat - 0.1), 0.02, 0.012)
    model.hard(steel, "steel", bevel=0.004, segments=1)
    padded(model, "plastic", -0.24, 0.24, -0.25, 0.22, seat - 0.1, seat, 0.02)
    back = bmesh.new()
    cage = grid_box(back, spread(-0.23, 0.23, 2), [0.17, 0.25], spread(seat, seat + 0.62, 2))
    for (i, j, k), vert in cage.items():
        vert.co.y += 0.09 * (vert.co.z - seat) / 0.62
    cage[(1, 0, 1)].co.y -= 0.02
    crease(back, cage, 0.3)
    model.soft(back, "plastic", levels=2)
    for x in (-1, 1):
        padded(model, "plastic", *mirrored(0.215, 0.29, x), -0.2, 0.16, seat + 0.16, seat + 0.22, 0.008)
    return model


# A shop's glass counter one square across, its back to +Y: half its width, the top of its black cabinet, the foot and
# the top of the glass round its case, the top of the case, how thick its corner posts are and where its back stands.
# ShopFittings.java lays the glass by these numbers.
COUNTER_HALF, COUNTER_BASE, COUNTER_GLASS_FOOT, COUNTER_GLASS_TOP, COUNTER_TOP, COUNTER_POST, COUNTER_BACK = 0.49, 0.42, 0.44, 0.85, 0.87, 0.025, 0.47


def glass_counter():
    """A shop's glass counter: a black cabinet with a case on it, glass on the top, the front and the two ends in a
    frame of steel, and at the back, where whoever serves stands, a black board with two sliding doors in it."""
    model = Model("glass_counter")
    half, post = COUNTER_HALF, COUNTER_POST
    black, steel, felt = bmesh.new(), bmesh.new(), bmesh.new()
    cuboid(black, -half + 0.02, half - 0.02, -half + 0.02, half - 0.02, 0.0, 0.06)
    cuboid(black, -half, half, -half, half, 0.06, COUNTER_BASE)
    cuboid(black, -half + post, half - post, COUNTER_BACK, half - 0.004, COUNTER_BASE, COUNTER_TOP - 0.02)
    cuboid(felt, -half + post, half - post, -half + post, COUNTER_BACK, COUNTER_BASE, COUNTER_GLASS_FOOT)
    for x in (-half, half - post):
        for y in (-half, half - post):
            cuboid(steel, x, x + post, y, y + post, COUNTER_BASE, COUNTER_TOP)
    for z0, z1 in ((COUNTER_BASE, COUNTER_GLASS_FOOT), (COUNTER_GLASS_TOP, COUNTER_TOP)):
        for y in (-half, half - post):
            cuboid(steel, -half, half, y, y + post, z0, z1)
        for x in (-half, half - post):
            cuboid(steel, x, x + post, -half, half, z0, z1)
    # The seams of the doors in the back, and their pulls.
    seams = bmesh.new()
    for x in (-0.45, 0.0, 0.45):
        cuboid(seams, x - 0.004, x + 0.004, half - 0.004, half - 0.001, COUNTER_BASE + 0.03, COUNTER_GLASS_TOP - 0.03)
    for x in (-0.06, 0.06):
        cuboid(seams, x - 0.012, x + 0.012, half - 0.004, half, 0.6, 0.7)
    model.hard(black, "rubber", bevel=0.004, segments=1)
    model.hard(steel, "steel", bevel=0.003, segments=1)
    model.plain(felt, "linen")
    model.plain(seams, "metal")
    return model


# A magazine stand, its back to +Y: its front and back, how high its back stands, and its four tiers, each the height
# of its front edge and how far back from the stand's front that edge is. A tier's board rises MAGAZINE_LEAN for every
# metre it goes back, over MAGAZINE_BOARD.
MAGAZINE_FRONT, MAGAZINE_BACK, MAGAZINE_TOP = -0.3, 0.3, 1.34
MAGAZINE_TIERS = ((0.22, 0.02), (0.5, 0.15), (0.78, 0.28), (1.06, 0.41))
MAGAZINE_LEAN, MAGAZINE_BOARD = 2.0, 0.13
# A mall's high table, made of a quarter on each of four squares: how high its top is.
MALL_TABLE_TOP = 0.93


def magazine_stand(name, half, trim):
    """A stand for magazines, half long either way: a back, two ends, and four tiers stepping back and up from the front,
    each a board leaning back with a rail along its front edge for the magazines to rest on."""
    model = Model(name)
    front, back, top = MAGAZINE_FRONT, MAGAZINE_BACK, MAGAZINE_TOP
    body, rails = bmesh.new(), bmesh.new()
    cuboid(body, -half + 0.02, half - 0.02, back - 0.03, back, 0.0, top)
    cuboid(body, -half + 0.02, half - 0.02, front + 0.03, back - 0.03, 0.0, 0.12)
    side = [(front, 0.0), (back, 0.0), (back, top)] + [(front + y + 0.0, z + 0.04) for z, y in reversed(MAGAZINE_TIERS)] + [(front, 0.12)]
    for x in (-1, 1):
        outline_along_x(body, side, *mirrored(half - 0.02, half, x))
    for z, y in MAGAZINE_TIERS:
        prism_y(body, (0.0, z, 2 * half - 0.04, 0.02, front + y), (0.0, z + MAGAZINE_BOARD * MAGAZINE_LEAN, 2 * half - 0.04, 0.02, front + y + MAGAZINE_BOARD))
        cuboid(rails, -half + 0.02, half - 0.02, front + y - 0.018, front + y, z - 0.02, z + 0.05)
    model.hard(body, "paint", bevel=0.004, segments=1)
    model.hard(rails, trim, bevel=0.004, segments=1)
    return model


def magazines(name, seed, fill):
    """The magazines on one square's length of a magazine stand: on each tier, a row of them standing on its board behind
    its rail and leaning back as the board does, with the tickets on the rails. Its foot is the foot of the tickets on
    the lowest rail."""
    model = Model(name, centred=False)
    part, finish = parts(model)
    pick = random.Random(seed)
    slope = math.hypot(1.0, MAGAZINE_LEAN)
    up_y, up_z = 1.0 / slope, MAGAZINE_LEAN / slope
    for z, y in MAGAZINE_TIERS:
        x = -0.44
        while True:
            wide = pick.uniform(0.2, 0.23)
            if x + wide > 0.44:
                break
            if pick.random() < fill:
                high, thick = pick.uniform(0.24, 0.28), 0.008
                foot_y, foot_z = MAGAZINE_FRONT + y + 0.004, z + 0.012
                outline = [(foot_y, foot_z), (foot_y - thick * up_z, foot_z + thick * up_y),
                           (foot_y - thick * up_z + high * up_y, foot_z + thick * up_y + high * up_z), (foot_y + high * up_y, foot_z + high * up_z)]
                outline_along_x(part(pick.choice(PACKETS)), outline, x + 0.003, x + wide - 0.003)
            x += wide
    for z, y in MAGAZINE_TIERS:
        tickets(part, pick, (z + 0.05,), MAGAZINE_FRONT + y - 0.018, 0.4)
    finish()
    return model


def mall_table_quarter():
    """A quarter of a mall's high table four squares big, on one square: a square of its top, rounded at its outer corner
    and edged with a dark lip, and the quarter of the column under the middle of the table. The table's middle is the
    square's corner at -X, +Y, and its origin is the middle of the square."""
    model = Model("mall_table_quarter", centred=False)
    top, lip, round_off = MALL_TABLE_TOP, 0.04, 0.12
    outline = [(-0.5, -0.5), (0.5 - round_off, -0.5)] + arc(0.5 - round_off, -0.5 + round_off, round_off, 270.0, 360.0, 4)[1:] + [(0.5, 0.5), (-0.5, 0.5)]
    board, edge, column = bmesh.new(), bmesh.new(), bmesh.new()
    outline_up(edge, outline, top - lip, top)
    outline_up(board, [(x * 0.96 - 0.02, y * 0.96 + 0.02) for x, y in outline], top - 0.002, top + 0.004)
    lathe(column, [(0.0, 0.0), (0.42, 0.0), (0.42, 0.02), (0.3, 0.05), (0.07, 0.07), (0.06, top - lip), (0.0, top - lip)], cx=-0.5, cy=0.5, segments=40)
    for normal in ((1.0, 0.0, 0.0), (0.0, -1.0, 0.0)):
        bmesh.ops.bisect_plane(column, geom=column.verts[:] + column.edges[:] + column.faces[:], plane_co=(-0.5, 0.5, 0.0), plane_no=normal,
                               clear_inner=True)
    model.hard(edge, "dark_wood", bevel=0.006, segments=1)
    model.plain(board, "worktop")
    model.turned(column, "plastic", sharp=50.0)
    return model


# A small rack of black wire that stands on a shop's counter: its length runs front to back, its back end stands higher
# than its front, and its two shelves are slats across it between rails along its sides. The game gives it no height of
# its own: its Surface is IsSurfaceOffset, the counter's top it is painted on, and only an IsTable object lifts what is
# set on its square. Its heights are read off its sprite, 78 pixels to a metre.
COUNTER_RACK_HALF, COUNTER_RACK_SIDE, COUNTER_RACK_SHELVES = 0.35, 0.16, (0.07, 0.3)
COUNTER_RACK_ENDS = (0.56, 0.68)


def counter_rack():
    """The wire rack the game stands on a counter: a hoop of tube at each end, its upper corners rounded, the back one
    the taller, rails along both sides at each shelf and slats across between them, on four rubber feet."""
    model = Model("counter_rack")
    wire, slats, feet = bmesh.new(), bmesh.new(), bmesh.new()
    r, bend, side = 0.0065, 0.06, COUNTER_RACK_SIDE
    for y, top in zip((-COUNTER_RACK_HALF, COUNTER_RACK_HALF), COUNTER_RACK_ENDS):
        corner_low = top - r - bend
        path = ([(-side, 0.012)]
                + [(-side + bend + bend * math.cos(math.radians(a)), corner_low + bend * math.sin(math.radians(a))) for a in range(180, 85, -15)]
                + [(side - bend + bend * math.cos(math.radians(a)), corner_low + bend * math.sin(math.radians(a))) for a in range(90, -5, -15)] + [(side, 0.012)])
        for (x0, z0), (x1, z1) in zip(path, path[1:]):
            rod(wire, (x0, y, z0), (x1, y, z1), r, segments=8)
        for x in (-side, side):
            lathe(feet, [(0.0, 0.0), (r * 1.6, 0.0), (r * 1.6, 0.014), (0.0, 0.014)], cx=x, cy=y, segments=8)
    for z in COUNTER_RACK_SHELVES:
        for x in (-side, side):
            rod(wire, (x, -COUNTER_RACK_HALF, z), (x, COUNTER_RACK_HALF, z), r, segments=8)
        count = 15
        for n in range(count):
            y = -COUNTER_RACK_HALF + 0.035 + (2 * COUNTER_RACK_HALF - 0.07) * n / (count - 1)
            rod(slats, (-side, y, z + r * 0.6), (side, y, z + r * 0.6), r * 0.55, segments=6)
    model.turned(wire, "plastic", sharp=40.0)
    model.turned(slats, "plastic", sharp=40.0)
    model.turned(feet, "rubber", sharp=60.0)
    return model


def open_counter():
    """One square of a shop's counter, a metre wide as cabinet's shop_counter is so that it meets the run, open at its
    front over two shelves: two sides, a back, a deck over the kick and a shelf half way up, under the same worktop."""
    model = Model("open_counter")
    deep, high, kick, lip, board = 0.62, 0.90, 0.10, 0.02, 0.02
    top, back = high - 0.04, deep / 2
    front = back - deep
    body = bmesh.new()
    cuboid(body, -0.5, 0.5, front + lip + 0.06, back, 0.0, kick)
    for side in (-1, 1):
        cuboid(body, *sorted((side * 0.5, side * (0.5 - board))), front + lip, back, kick, top)
    cuboid(body, -0.5 + board, 0.5 - board, back - board, back, kick, top)
    cuboid(body, -0.5 + board, 0.5 - board, front + lip, back - board, kick, kick + board)
    cuboid(body, -0.5 + board, 0.5 - board, front + lip + 0.01, back - board, 0.47 - board, 0.47)
    cuboid(body, -0.5 + board, 0.5 - board, front + lip, back - board, top - board, top)
    model.hard(body, "wood", bevel=0.004)
    tops = bmesh.new()
    cuboid(tops, -0.5, 0.5, front, back, top, high)
    model.hard(tops, "worktop", bevel=0.005, segments=2)
    return model


def deposit_boxes():
    """One square of a bank's standing safe deposit boxes, a metre wide so that each meets the next: a steel case on a
    plinth, its front at -Y three columns of six small doors, each with a keyhole plate and a pull."""
    model = Model("deposit_boxes")
    half, front, back, top, plinth = 0.5, -0.22, 0.22, 2.2, 0.08
    case, doors, fittings = bmesh.new(), bmesh.new(), bmesh.new()
    cuboid(case, -half, half, front + 0.04, back, 0.0, plinth)
    cuboid(case, -half, half, front, back, plinth, top)
    columns, rows, gap, edge = 3, 6, 0.008, 0.03
    wide = (2 * half - 2 * edge - (columns - 1) * gap) / columns
    high = (top - plinth - 2 * edge - (rows - 1) * gap) / rows
    for c in range(columns):
        x0 = -half + edge + c * (wide + gap)
        for r in range(rows):
            z0 = plinth + edge + r * (high + gap)
            cuboid(doors, x0, x0 + wide, front - 0.008, front, z0, z0 + high)
            cuboid(fittings, x0 + wide * 0.5 - 0.022, x0 + wide * 0.5 + 0.022, front - 0.012, front - 0.008, z0 + high * 0.62, z0 + high * 0.82)
            cuboid(fittings, x0 + wide * 0.5 - 0.03, x0 + wide * 0.5 + 0.03, front - 0.02, front - 0.008, z0 + high * 0.22, z0 + high * 0.3)
    model.hard(case, "steel", bevel=0.004, segments=1)
    model.hard(doors, "steel", bevel=0.002, segments=1)
    model.hard(fittings, "metal", bevel=0.002, segments=1)
    return model


def shop_fittings():
    """What stands in shops, malls, bars and salons: the pieces ShopFittings.java places, and what they hold."""
    return [gondola("shop_gondola", 1, "wood"), gondola("shop_gondola_m", -1, "wood"), gondola("shop_gondola_red", 1, "vinyl"),
            gondola("shop_gondola_red_m", -1, "vinyl"), long_shelves(),
            shelf_goods("shelf_goods_many", 41, 0.95, 3), shelf_goods("shelf_goods_few", 42, 0.45, 1),
            hook_goods("hook_goods_many", 43, 5), hook_goods("hook_goods_few", 44, 2),
            folded("folded_many", 45, 3), folded("folded_few", 46, 1),
            clothes_rail(), clothes_rack(), clothes_stand(), open_wardrobe(),
            rail_garments("rail_garments_many", 47, (0.0,), 0.86, 11), rail_garments("rail_garments_few", 48, (0.0,), 0.86, 4),
            rail_garments("rack_garments_many", 49, (-RACK_RAILS / 2, RACK_RAILS / 2), 0.86, 10, widest=0.38),
            rail_garments("rack_garments_few", 50, (-RACK_RAILS / 2, RACK_RAILS / 2), 0.86, 3, widest=0.38),
            stand_garments("stand_garments_many", 51, 4), stand_garments("stand_garments_few", 52, 1),
            rail_garments("coat_garments_many", 53, (0.03,), 0.9, 10, longest=0.95), rail_garments("coat_garments_few", 54, (0.03,), 0.9, 4, longest=0.95),
            open_chiller(), chiller_goods("chiller_goods_many", 55, 0.95, 3), chiller_goods("chiller_goods_few", 56, 0.45, 1),
            mall_chair(), pedestal_stool(), salon_chair(), glass_counter(),
            magazine_stand("magazine_stand", 0.49, "wood"), magazine_stand("magazine_stand_red", 0.98, "vinyl"),
            magazines("magazines_many", 57, 0.95), magazines("magazines_few", 58, 0.4), mall_table_quarter(), counter_rack(), open_counter(), deposit_boxes()]


def outdoors():
    """What stands out of doors: on roofs, in yards and along streets."""
    return [duct_run(), duct_down(), duct_elbow(), roof_dome(), roof_fan(), fan_unit(), roof_stacks(), pipe_run(), pipe_corner(), culvert_pipe(),
            deck_chair(), slab_bench(), dustbin(), road_cone(), traffic_drum(), hydrant(), pallet(), brick_pallet(),
            gravestone("gravestone_round", "round"), gravestone("gravestone_square", "square"), gravestone("gravestone_gabled", "gabled"),
            sack_barrier(), log_stack("log_stack_6", (3, 3), (0, 1)), log_stack("log_stack_5", (3, 2)),
            twin_head(), girder(), felt_rolls("felt_middle", False), felt_rolls("felt_end", True), stop_sign(), crossing_signal(), flag_pole(), flag_top(), parking_barrier(), theatre_seats(), parking_meter(), security_monitors(), chimney_pots(), hand_truck(),
            roof_case("roof_case_low", 92 / 192 * 2.45), roof_case("roof_case_tall", 190 / 192 * 2.45)]         + tyres()

def rod(bm, start, end, radius, segments=10):
    """A round bar from start to end."""
    start, end = Vector(start), Vector(end)
    way = end - start
    turn = Vector((0.0, 0.0, 1.0)).rotation_difference(way.normalized()).to_matrix().to_4x4()
    lathe(bm, [(radius, 0.0), (radius, way.length)], segments=segments, to=Matrix.Translation(start) @ turn)


def strip_path(bm, path, x0, x1, thick):
    """A closed strip from x0 to x1 that follows a path of (y, z) down its middle, thick deep under it."""
    rows = [[bm.verts.new((x, y, z - drop)) for drop in (0.0, thick) for x in (x0, x1)] for y, z in path]
    for a, b in zip(rows, rows[1:]):
        bm.faces.new((a[0], a[1], b[1], b[0]))
        bm.faces.new((a[3], a[2], b[2], b[3]))
        bm.faces.new((a[2], a[0], b[0], b[2]))
        bm.faces.new((a[1], a[3], b[3], b[1]))
    bm.faces.new((rows[0][1], rows[0][0], rows[0][2], rows[0][3]))
    bm.faces.new((rows[-1][0], rows[-1][1], rows[-1][3], rows[-1][2]))


# How high the beam of a park's swing is, in metres, and how far each leg of its frame stands out from under the beam.
SWING_TOP, SWING_SPREAD = 2.23, 0.72


def swing_end():
    """The end of a swing's frame on the square of its beam: the leg on that square, the brace across the frame and the cap over the
    apex. Its origin is on the ground under the end of the beam, and the leg stands out to +Y."""
    model = Model("swing_end", centred=False)
    frame = bmesh.new()
    rod(frame, (0.0, SWING_SPREAD, 0.0), (0.0, 0.03, SWING_TOP), 0.038)
    model.turned(frame, "paint")
    brace = bmesh.new()
    across = SWING_SPREAD * (1.0 - 0.95 / SWING_TOP)
    rod(brace, (0.0, across, 0.95), (0.0, -across, 0.95), 0.022, segments=8)
    model.turned(brace, "metal")
    cap = bmesh.new()
    cuboid(cap, -0.055, 0.055, -0.07, 0.07, SWING_TOP - 0.12, SWING_TOP + 0.05)
    model.hard(cap, "paint", bevel=0.008, segments=1)
    return model


def swing_leg():
    """The other leg of a swing's frame, on the square beside the beam's, from the same origin to -Y."""
    model = Model("swing_leg", centred=False)
    frame = bmesh.new()
    rod(frame, (0.0, -SWING_SPREAD, 0.0), (0.0, -0.03, SWING_TOP), 0.038)
    model.turned(frame, "paint")
    return model


def swing_beam(name, seat=None):
    """A square's length of a swing's beam, and the seat that hangs from it on two chains where seat says how far along the
    beam its middle is. Its origin is on the ground under the middle of the length."""
    model = Model(name, centred=False)
    beam_bm = bmesh.new()
    cuboid(beam_bm, -0.5, 0.5, -0.045, 0.045, SWING_TOP - 0.045, SWING_TOP + 0.045)
    model.hard(beam_bm, "paint", bevel=0.01, segments=2)
    if seat is None:
        return model
    chains = bmesh.new()
    for x in (seat - 0.27, seat + 0.27):
        rod(chains, (x, 0.0, 0.47), (x, 0.0, SWING_TOP - 0.045), 0.009, segments=6)
        cuboid(chains, x - 0.03, x + 0.03, -0.01, 0.01, SWING_TOP - 0.06, SWING_TOP - 0.04)
    model.turned(chains, "metal", sharp=50.0)
    sling = bmesh.new()
    bottom = [(seat + 0.27 * t / 6, 0.44 + 0.07 * (t / 6) ** 2) for t in range(-6, 7)]
    top = [(x, z + 0.025) for x, z in reversed(bottom)]
    outline_along_y(sling, bottom + top, -0.085, 0.085)
    model.turned(sling, "plastic", sharp=40.0)
    return model


def sport_tyres(name, count, wobble=0.0):
    """A stack of old tyres painted for a track's barrier, every other one set off its neighbours by wobble."""
    model = Model(name)
    stack = bmesh.new()
    for n in range(count):
        off = wobble * (1 if n % 2 else -1)
        z = n * 0.205
        lathe(stack, [(0.215, z + 0.02), (0.3, z), (0.355, z + 0.02), (0.372, z + 0.1), (0.355, z + 0.185), (0.3, z + 0.205), (0.215, z + 0.185),
                      (0.205, z + 0.1)], cx=off, cy=off * 0.5, segments=24, closed=True)
    model.turned(stack, "paint", sharp=50.0)
    return model


def play_castle():
    """A child's play house on two squares by two: four walls of moulded plastic on posts round a deck, a doorway in two of them, and a
    slide out of the one at +Y. It stands in the back of its squares, the slide in front of it. Each wall is its own colour, as the
    moulded ones are; the one at +X takes the sprite's."""
    model = Model("play_castle", centred=False)
    x0, x1, y0, y1, high, deck = -0.58, 0.58, -0.83, 0.26, 1.27, 0.5
    posts = bmesh.new()
    for x in (x0, x1):
        for y in (y0, y1):
            cuboid(posts, x - 0.05, x + 0.05, y - 0.05, y + 0.05, 0.0, high + 0.08)
    model.hard(posts, "plastic", bevel=0.02, segments=2)
    floor = bmesh.new()
    cuboid(floor, x0, x1, y0, y1, deck - 0.05, deck)
    model.hard(floor, "metal", bevel=0.01, segments=1)

    def wall(surface, along_x, at, door):
        part = bmesh.new()
        t = 0.03
        lo, hi = (x0, x1) if along_x else (y0, y1)
        lo, hi = lo + 0.05, hi - 0.05
        mid = (lo + hi) / 2 + (0.1 if along_x else 0.0)
        if door:
            pieces = [(lo, mid - 0.2, 0.06, high), (mid + 0.2, hi, 0.06, high), (mid - 0.2, mid + 0.2, 0.06, deck),
                      (mid - 0.2, mid + 0.2, deck + 0.6, high)]
        else:
            pieces = [(lo, hi, 0.06, high)]
        for a0, a1, z0, z1 in pieces:
            if along_x:
                cuboid(part, a0, a1, at - t, at + t, z0, z1)
            else:
                cuboid(part, at - t, at + t, a0, a1, z0, z1)
        model.hard(part, surface, bevel=0.012, segments=2)

    wall("paint", False, x1, True)
    wall("vinyl", True, y0, False)
    wall("cloth", False, x0, False)
    wall("shade", True, y1, True)
    chute = bmesh.new()
    path = [(y1 + 0.02 + 0.62 * t / 10, deck - (deck - 0.12) * (0.5 - 0.5 * math.cos(math.pi * t / 10))) for t in range(11)]
    path += [(path[-1][0] + 0.12, 0.12)]
    strip_path(chute, path, -0.08, 0.28, 0.03)
    for edge in (-0.12, 0.28):
        strip_path(chute, [(y, z + 0.09) for y, z in path], edge, edge + 0.04, 0.12)
    model.hard(chute, "vinyl", bevel=0.008, segments=1)
    return model


def treadmill():
    """A running machine two squares long: a deck with its belt, two posts up from the back and the console across their tops. Its
    back is +Y."""
    model = Model("treadmill", centred=False)
    deck = bmesh.new()
    cuboid(deck, -0.37, 0.37, -0.85, 0.62, 0.05, 0.2)
    model.hard(deck, "metal", bevel=0.02, segments=2)
    belt = bmesh.new()
    cuboid(belt, -0.25, 0.25, -0.8, 0.5, 0.2, 0.215)
    model.hard(belt, "rubber", bevel=0.004, segments=1)
    feet = bmesh.new()
    for x in (-0.33, 0.33):
        for y in (-0.8, 0.55):
            cuboid(feet, x - 0.03, x + 0.03, y - 0.03, y + 0.03, 0.0, 0.05)
    model.hard(feet, "rubber", bevel=0.004, segments=1)
    frame = bmesh.new()
    for x in (-0.34, 0.34):
        rod(frame, (x, 0.52, 0.18), (x, 0.42, 1.28), 0.028, segments=8)
        rod(frame, (x, 0.42, 1.08), (x, 0.05, 1.08), 0.02, segments=8)
    model.turned(frame, "metal")
    console = bmesh.new()
    prism_y(console, (0.0, 1.32, 0.78, 0.16, 0.33), (0.0, 1.4, 0.78, 0.12, 0.5))
    model.hard(console, "metal", bevel=0.015, segments=2)
    screen = bmesh.new()
    prism_y(screen, (0.0, 1.33, 0.24, 0.07, 0.325), (0.0, 1.34, 0.24, 0.07, 0.34))
    model.plain(screen, "screen")
    return model


def weight_bench():
    """A flat bench for lifting with a rack for the bar at its head, two squares long. Its head is +Y."""
    model = Model("weight_bench", centred=False)
    pads = bmesh.new()
    cuboid(pads, -0.15, 0.15, -0.9, -0.12, 0.38, 0.47)
    cuboid(pads, -0.15, 0.15, -0.1, 0.42, 0.38, 0.47)
    model.hard(pads, "plastic", bevel=0.02, segments=2)
    frame = bmesh.new()
    cuboid(frame, -0.03, 0.03, -0.85, 0.42, 0.3, 0.37)
    for y in (-0.8, 0.0):
        cuboid(frame, -0.03, 0.03, y - 0.03, y + 0.03, 0.05, 0.3)
        cuboid(frame, -0.2, 0.2, y - 0.03, y + 0.03, 0.0, 0.05)
    for x in (-0.35, 0.35):
        cuboid(frame, x - 0.035, x + 0.035, 0.47, 0.54, 0.0, 0.82)
        cuboid(frame, x - 0.035, x + 0.035, 0.36, 0.65, 0.0, 0.05)
    cuboid(frame, -0.35, 0.35, 0.47, 0.54, 0.25, 0.31)
    cuboid(frame, -0.03, 0.03, 0.42, 0.54, 0.25, 0.31)
    model.hard(frame, "paint", bevel=0.006, segments=1)
    hooks = bmesh.new()
    for x in (-0.35, 0.35):
        cuboid(hooks, x - 0.03, x + 0.03, 0.45, 0.56, 0.82, 0.86)
        cuboid(hooks, x - 0.03, x + 0.03, 0.45, 0.48, 0.86, 0.93)
        cuboid(hooks, x - 0.03, x + 0.03, 0.53, 0.56, 0.86, 0.93)
    model.hard(hooks, "plastic", bevel=0.004, segments=1)
    return model


def upright_piano():
    """An upright piano across two squares, its back to +Y: the case on two front legs, the keys on their bed and a lid over the top."""
    model = Model("upright_piano", centred=False)
    case = bmesh.new()
    cuboid(case, -0.74, 0.74, -0.02, 0.42, 0.02, 1.06)
    cuboid(case, -0.78, 0.78, -0.07, 0.45, 1.06, 1.1)
    cuboid(case, -0.74, 0.74, -0.33, -0.02, 0.6, 0.68)
    for x in (-0.74, 0.65):
        cuboid(case, x, x + 0.09, -0.36, -0.02, 0.6, 0.83)
    cuboid(case, -0.65, 0.65, -0.06, -0.02, 0.82, 1.04)
    cuboid(case, -0.65, 0.65, -0.05, -0.02, 0.1, 0.58)
    model.hard(case, "dark_wood", bevel=0.008, segments=2)
    legs = bmesh.new()
    for x in (-0.695, 0.695):
        lathe(legs, [(0.0, 0.0), (0.03, 0.0), (0.025, 0.08), (0.03, 0.45), (0.04, 0.55), (0.04, 0.6), (0.0, 0.6)], cx=x, cy=-0.3, segments=12)
    model.turned(legs, "dark_wood", sharp=50.0)
    keys = bmesh.new()
    cuboid(keys, -0.64, 0.64, -0.3, -0.12, 0.68, 0.71)
    model.hard(keys, "ceramic", bevel=0.003, segments=1)
    sharps = bmesh.new()
    cuboid(sharps, -0.64, 0.64, -0.12, -0.06, 0.68, 0.725)
    model.hard(sharps, "rubber", bevel=0.003, segments=1)
    pedals = bmesh.new()
    for x in (-0.06, 0.0, 0.06):
        cuboid(pedals, x - 0.018, x + 0.018, -0.17, -0.02, 0.05, 0.07)
    model.hard(pedals, "metal", bevel=0.003, segments=1)
    return model


def piano_bench():
    """A piano's bench across two squares: a padded top with an apron under it on four turned legs."""
    model = Model("piano_bench", centred=False)
    top = bmesh.new()
    cuboid(top, -0.4, 0.4, -0.18, 0.18, 0.43, 0.5)
    model.hard(top, "dark_wood", bevel=0.01, segments=2)
    legs = bmesh.new()
    for x in (-0.35, 0.35):
        for y in (-0.13, 0.13):
            lathe(legs, [(0.0, 0.0), (0.022, 0.0), (0.02, 0.1), (0.027, 0.3), (0.027, 0.43), (0.0, 0.43)], cx=x, cy=y, segments=10)
    model.turned(legs, "dark_wood", sharp=50.0)
    return model


def slide():
    """A park's slide two squares long: a ladder up to a little platform at +Y and the chute curving down from it to -Y."""
    model = Model("slide", centred=False)
    top, half = 1.3, 0.24
    chute = bmesh.new()
    path = []
    for t in range(16):
        f = t / 15
        y = 0.45 - 1.38 * f
        z = 0.24 + (top - 0.24) * (0.5 + 0.5 * math.cos(math.pi * min(f * 1.08, 1.0)))
        path.append((y, z))
    path.append((-0.98, 0.24))
    strip_path(chute, path, -half + 0.03, half - 0.03, 0.03)
    model.hard(chute, "steel", bevel=0.004, segments=1)
    rails = bmesh.new()
    for x0 in (-half, half - 0.045):
        strip_path(rails, [(y, z + 0.11) for y, z in path], x0, x0 + 0.045, 0.14)
    model.hard(rails, "vinyl", bevel=0.01, segments=1)
    steel = bmesh.new()
    for x in (-half + 0.02, half - 0.02):
        rod(steel, (x, -0.92, 0.0), (x, -0.92, 0.2), 0.02, segments=8)
        rod(steel, (x, 0.95, 0.0), (x, 0.55, top + 0.6), 0.022, segments=8)
    for n in range(1, 6):
        z = n * top / 6
        y = 0.95 - 0.4 * z / (top + 0.6)
        rod(steel, (-half + 0.02, y, z), (half - 0.02, y, z), 0.015, segments=6)
    for x in (-half + 0.02, half - 0.02):
        rod(steel, (x, 0.45, top), (x, 0.52, top + 0.5), 0.016, segments=6)
    cuboid(steel, -half, half, 0.42, 0.66, top - 0.03, top)
    model.turned(steel, "metal")
    return model


def merry_go_round():
    """A roundabout on two squares by two: a turning deck round a post, with six bars to hold that bend in to the post."""
    model = Model("merry_go_round")
    deck = bmesh.new()
    lathe(deck, [(0.0, 0.23), (0.95, 0.23), (0.97, 0.26), (0.97, 0.32), (0.95, 0.34), (0.0, 0.34)], segments=48)
    model.turned(deck, "paint", sharp=50.0)
    under = bmesh.new()
    lathe(under, [(0.0, 0.0), (0.22, 0.0), (0.16, 0.23), (0.0, 0.23)], segments=16)
    model.turned(under, "metal", sharp=50.0)
    bars = bmesh.new()
    lathe(bars, [(0.0, 0.34), (0.06, 0.34), (0.06, 1.0), (0.08, 1.02), (0.08, 1.08), (0.0, 1.1)], segments=14)
    for n in range(6):
        c, s = math.cos(n * math.pi / 3), math.sin(n * math.pi / 3)
        rod(bars, (0.8 * c, 0.8 * s, 0.34), (0.8 * c, 0.8 * s, 1.0), 0.018, segments=6)
        rod(bars, (0.8 * c, 0.8 * s, 1.0), (0.07 * c, 0.07 * s, 1.0), 0.018, segments=6)
    model.turned(bars, "metal")
    return model


def speaker_cabinet(name, wide, deep, high, made):
    """A speaker cabinet on the floor, its two cones in its front at -Y: each a dish deepest at its middle, where the dust
    cap rises, with the roll of its surround round its rim, all of it proud of the cabinet's face, which has no hole."""
    model = Model(name)
    box = bmesh.new()
    cuboid(box, -wide / 2, wide / 2, -deep / 2, deep / 2, 0.0, high)
    model.hard(box, made, bevel=0.008, segments=2)
    cones = bmesh.new()
    for z, r in ((high * 0.3, wide * 0.34), (high * 0.74, wide * 0.17)):
        lathe(cones, [(0.0, 0.007), (r * 0.2, 0.005), (r * 0.24, 0.003), (r, 0.014), (r * 1.06, 0.018), (r * 1.12, 0.014), (r * 1.14, 0.002)], segments=20,
              to=Matrix.Translation((0.0, -deep / 2 - 0.0005, z)) @ Matrix.Rotation(math.radians(90), 4, "X"))
    model.turned(cones, "rubber", sharp=45.0)
    return model


def jukebox():
    """A jukebox: a cabinet with a round top, the glass of its selector and the lit bands round its face at -Y."""
    model = Model("jukebox")
    wide, deep, high = 0.86, 0.62, 1.5
    side = [(-wide / 2, 0.0), (wide / 2, 0.0)] + [(wide / 2 * math.cos(math.radians(a)), high - wide / 2 + wide / 2 * math.sin(math.radians(a)))
                                                    for a in range(0, 181, 15)]
    body = bmesh.new()
    outline_along_y(body, side, -deep / 2, deep / 2)
    model.hard(body, "plastic", bevel=0.012, segments=2)
    bands = bmesh.new()
    inner = [(x * 0.82, z * 0.94 + 0.04) for x, z in side]
    outline_along_y(bands, inner, -deep / 2 - 0.02, -deep / 2 + 0.01)
    model.hard(bands, "shade", bevel=0.006, segments=1)
    glass = bmesh.new()
    cuboid(glass, -0.26, 0.26, -deep / 2 - 0.03, -deep / 2 - 0.01, 0.72, 1.12)
    model.hard(glass, "screen", bevel=0.004, segments=1)
    grille = bmesh.new()
    cuboid(grille, -0.26, 0.26, -deep / 2 - 0.03, -deep / 2 - 0.01, 0.12, 0.55)
    model.hard(grille, "linen", bevel=0.004, segments=1)
    return model


def grand_piano():
    """A grand piano two squares by three, its keys at -Y and its tail curving round to +Y, on three legs, the lid shut."""
    model = Model("grand_piano", centred=False)
    front, back, half = -1.25, 1.3, 0.92
    # The bass side is straight, at -X. The treble side runs straight back from the keys, curves in, and rounds into the tail.
    waist, neck = front + 0.6, back - 0.5
    plan = [(-half, front), (half, front), (half, waist)]
    plan += [(half - (half - 0.15) * (0.5 - 0.5 * math.cos(math.pi * t / 8)), waist + (neck - waist) * t / 8) for t in range(1, 9)]
    tail = (half + 0.15) / 2
    plan += [(0.15 - tail + tail * math.cos(math.radians(a)), neck + tail * math.sin(math.radians(a))) for a in range(15, 181, 15)]
    case = bmesh.new()
    outline_up(case, plan, 0.66, 1.02)
    model.hard(case, "screen", bevel=0.01, segments=2)
    lid = bmesh.new()
    outline_up(lid, [(x * 1.01, y) for x, y in plan], 1.02, 1.04)
    cuboid(lid, -0.36, 0.36, front + 0.12, front + 0.16, 1.04, 1.28)
    model.hard(lid, "screen", bevel=0.004, segments=1)
    legs = bmesh.new()
    for x, y in ((-half + 0.1, front + 0.15), (half - 0.1, front + 0.15), (-0.38, back - 0.3)):
        lathe(legs, [(0.0, 0.0), (0.035, 0.0), (0.04, 0.05), (0.035, 0.44), (0.06, 0.64), (0.0, 0.66)], cx=x, cy=y, segments=12)
    rod(legs, (0.0, front + 0.3, 0.0), (0.0, front + 0.3, 0.66), 0.02, segments=8)
    model.turned(legs, "screen", sharp=50.0)
    keys = bmesh.new()
    cuboid(keys, -0.82, 0.82, front - 0.2, front, 0.72, 0.75)
    model.hard(keys, "ceramic", bevel=0.003, segments=1)
    sharps = bmesh.new()
    cuboid(sharps, -0.82, 0.82, front - 0.05, front, 0.72, 0.765)
    cuboid(sharps, -0.88, 0.88, front - 0.24, front, 0.66, 0.72)
    for x in (-0.88, 0.82):
        cuboid(sharps, x, x + 0.06, front - 0.24, front, 0.72, 0.8)
    model.hard(sharps, "screen", bevel=0.003, segments=1)
    return model


# A church's lectern (location_community_church_small_01_44..47), read off its sprites: its base plate and the plinth on it, as
# half width, half depth and height in metres; the panelled post's half width and half depth; and the desk's half width, how far
# its foot and its head stand out from the post's middle, and how high each of them is.
LECTERN_BASE = (0.26, 0.125, 0.05)
LECTERN_PLINTH = (0.2, 0.09, 0.11)
LECTERN_POST = (0.125, 0.055)
LECTERN_DESK = (0.3, -0.2, 0.2, 0.97, 1.13)


def lectern():
    """A lectern: a base plate and a plinth, a panelled post with a collar at its head, and a sloped desk for the book with a
    ledge along its foot, falling to whoever reads from it at -Y."""
    model = Model("lectern")
    base_x, base_y, base_z = LECTERN_BASE
    plinth_x, plinth_y, plinth_z = LECTERN_PLINTH
    post_x, post_y = LECTERN_POST
    desk_x, foot_y, head_y, foot_z, head_z = LECTERN_DESK
    collar = foot_z - 0.07
    stand = bmesh.new()
    cuboid(stand, -base_x, base_x, -base_y, base_y, 0.0, base_z)
    prism(stand, (0.0, 0.0, 2 * plinth_x, 2 * plinth_y, base_z), (0.0, 0.0, 2 * plinth_x - 0.03, 2 * plinth_y - 0.03, plinth_z))
    model.hard(stand, "dark_wood", bevel=0.005, segments=1)
    # The post: a core with a sunk panel up each broad face, framed by a stile down each side and a rail at the foot and the head.
    sunk = 0.01
    post = bmesh.new()
    cuboid(post, -post_x, post_x, -post_y + sunk, post_y - sunk, plinth_z, collar)
    for y0, y1 in ((-post_y, -post_y + sunk), (post_y - sunk, post_y)):
        cuboid(post, -post_x, post_x, y0, y1, plinth_z, plinth_z + 0.06)
        cuboid(post, -post_x, post_x, y0, y1, collar - 0.06, collar)
        for x0, x1 in ((-post_x, -post_x + 0.035), (post_x - 0.035, post_x)):
            cuboid(post, x0, x1, y0, y1, plinth_z + 0.06, collar - 0.06)
    model.hard(post, "dark_wood", bevel=0.004, segments=1)
    head = bmesh.new()
    cuboid(head, -post_x - 0.012, post_x + 0.012, -post_y - 0.012, post_y + 0.012, collar, foot_z - 0.01)
    cuboid(head, -post_x - 0.012, post_x + 0.012, -post_y - 0.012, post_y + 0.012, plinth_z, plinth_z + 0.025)
    model.hard(head, "dark_wood", bevel=0.004, segments=1)
    # The desk: a board 3 cm thick on a wedge that carries it over the collar, and the ledge the book rests on along its foot.
    rise = head_z - foot_z
    slope = math.hypot(head_y - foot_y, rise)
    normal = (-rise / slope, (head_y - foot_y) / slope)
    desk = bmesh.new()
    corners = [(foot_y, foot_z), (head_y, head_z), (head_y + normal[0] * 0.03, head_z + normal[1] * 0.03),
               (foot_y + normal[0] * 0.03, foot_z + normal[1] * 0.03)]
    outline_along_x(desk, [(y, z - 0.03) for y, z in corners], -desk_x, desk_x)
    outline_along_x(desk, [(-post_y - 0.012, foot_z - 0.01), (post_y + 0.012, foot_z - 0.01),
                           (post_y + 0.012, foot_z + (post_y + 0.012 - foot_y) * rise / (head_y - foot_y) - 0.03),
                           (-post_y - 0.012, foot_z + (-post_y - 0.012 - foot_y) * rise / (head_y - foot_y) - 0.03)],
                    -post_x - 0.012, post_x + 0.012)
    model.hard(desk, "wood", bevel=0.006, segments=2)
    ledge = bmesh.new()
    cuboid(ledge, -desk_x, desk_x, foot_y - 0.005, foot_y + 0.02, foot_z - 0.03, foot_z + 0.035)
    model.hard(ledge, "wood", bevel=0.004, segments=1)
    return model


def pinball():
    """A pinball machine two squares long: its table sloping up to the box at its head, +Y, on four legs."""
    model = Model("pinball", centred=False)
    side = [(-0.6, 0.68), (0.45, 0.68), (0.45, 1.0), (-0.6, 0.9)]
    table = bmesh.new()
    outline_along_x(table, side, -0.36, 0.36)
    cuboid(table, -0.36, 0.36, 0.45, 0.62, 0.68, 1.8)
    model.hard(table, "paint", bevel=0.01, segments=2)
    glass = bmesh.new()
    prism_y(glass, (0.0, 0.905, 0.64, 0.01, -0.58), (0.0, 0.995, 0.64, 0.01, 0.43))
    model.plain(glass, "pane")
    face = bmesh.new()
    cuboid(face, -0.32, 0.32, 0.43, 0.45, 1.06, 1.74)
    model.hard(face, "shade", bevel=0.004, segments=1)
    legs = bmesh.new()
    for x in (-0.31, 0.31):
        for y in (-0.53, 0.52):
            cuboid(legs, x - 0.025, x + 0.025, y - 0.025, y + 0.025, 0.0, 0.68)
    model.hard(legs, "metal", bevel=0.004, segments=1)
    return model


def altar():
    """A church's altar on two squares, its length across them and shorter than they are: a panelled wooden chest on a plinth
    under a thick top, and a cloth over the top that falls down the front at -Y nearly to the floor, its hem cut in scallops,
    and a little way down the back."""
    model = Model("altar")
    body = bmesh.new()
    cuboid(body, -0.62, 0.62, -0.32, 0.40, 0.0, 0.06)
    cuboid(body, -0.6, 0.6, -0.3, 0.38, 0.06, 0.76)
    model.hard(body, "dark_wood", bevel=0.008, segments=1)
    top = bmesh.new()
    cuboid(top, -0.64, 0.64, -0.36, 0.44, 0.76, 0.87)
    model.hard(top, "dark_wood", bevel=0.014, segments=2)
    # The frames round the panels of its front, three of them, and of each end.
    frames = bmesh.new()
    for x0, x1 in ((-0.56, -0.52), (-0.21, -0.17), (0.17, 0.21), (0.52, 0.56)):
        cuboid(frames, x0, x1, -0.315, -0.3, 0.12, 0.70)
    for z0, z1 in ((0.12, 0.16), (0.66, 0.70)):
        cuboid(frames, -0.56, 0.56, -0.315, -0.3, z0, z1)
    for side in (-1, 1):
        x0, x1 = mirrored(0.6, 0.615, side)
        for y0, y1 in ((-0.26, -0.22), (0.30, 0.34)):
            cuboid(frames, x0, x1, y0, y1, 0.12, 0.70)
        for z0, z1 in ((0.12, 0.16), (0.66, 0.70)):
            cuboid(frames, x0, x1, -0.26, 0.34, z0, z1)
    model.hard(frames, "dark_wood", bevel=0.004, segments=1)
    cloth = bmesh.new()
    cuboid(cloth, -0.3, 0.3, -0.375, 0.455, 0.87, 0.885)
    cuboid(cloth, -0.3, 0.3, -0.39, -0.375, 0.2, 0.885)
    cuboid(cloth, -0.3, 0.3, 0.455, 0.47, 0.6, 0.885)
    for i in range(6):
        x = -0.3 + 0.6 * (i + 0.5) / 6
        cuboid(cloth, x - 0.04, x + 0.04, -0.39, -0.375, 0.17, 0.2)
    model.hard(cloth, "linen", bevel=0.004, segments=1)
    return model


# A church's pipe organ: its back at +Y, how far its case stands out from the wall and how high its parts are, in metres.
ORGAN_BACK = 0.45
ORGAN_HALF = 1.45
ORGAN_CASE_TOP = 1.2
ORGAN_FLAT_TOP = 3.1
ORGAN_TOWER_TOP = 3.9
ORGAN_PILLAR_TOP = 3.7


def organ_pipes(bm, mouths, x0, x1, count, foot, tops, radius, y):
    """A row of front pipes from x0 to x1, each a cone up from foot to its mouth and a tube on up to its top, with the dark
    slot of its mouth facing -Y."""
    for i in range(count):
        x = x0 + (x1 - x0) * (i + 0.5) / count
        top = tops[i]
        mouth = foot + 0.32
        lathe(bm, [(0.0, foot), (radius * 0.3, foot), (radius, mouth), (radius, top), (radius * 0.86, top), (0.0, top - 0.03)], cx=x, cy=y,
              segments=10)
        cuboid(mouths, x - radius * 0.55, x + radius * 0.55, y - radius - 0.004, y - radius * 0.6, mouth + 0.02, mouth + 0.02 + radius * 1.1)


def pipe_organ():
    """A church's pipe organ along three squares, two storeys high, its back to the wall at +Y: a case with the console at its
    front (two manuals, the stops either side, a music desk) over a pedalboard, and above it a middle tower of pipes between two
    lower flats of them and two pillars, crosses on the tower's cornice."""
    model = Model("pipe_organ", centred=False)
    back, half, top = ORGAN_BACK, ORGAN_HALF, ORGAN_CASE_TOP
    case = bmesh.new()
    cuboid(case, -half - 0.03, half + 0.03, -0.08, back + 0.02, 0.0, 0.1)
    cuboid(case, -half, half, -0.05, back, 0.1, top)
    cuboid(case, -half - 0.05, half + 0.05, -0.1, back + 0.02, top, top + 0.08)
    # The panels of the case's front either side of the console.
    for side in (-1, 1):
        x0, x1 = mirrored(0.82, 1.38, side)
        cuboid(case, x0, x1, -0.07, -0.05, 0.22, 1.08)
    model.hard(case, "wood", bevel=0.008, segments=1)
    # The pillars at the ends, a flat of pipes between each and the middle tower, the tower: tall plain boards, which with
    # every edge rounded and cut to find its shade came to more than the rest of the organ.
    case = bmesh.new()
    for side in (-1, 1):
        x0, x1 = mirrored(1.2, half, side)
        cuboid(case, x0, x1, 0.02, back, top + 0.08, ORGAN_PILLAR_TOP)
        cuboid(case, x0 - 0.03, x1 + 0.03, -0.01, back + 0.02, ORGAN_PILLAR_TOP, ORGAN_PILLAR_TOP + 0.1)
        cuboid(case, x0 + 0.04, x1 - 0.04, 0.0, back, ORGAN_PILLAR_TOP + 0.1, ORGAN_PILLAR_TOP + 0.16)
        x0, x1 = mirrored(0.55, 1.2, side)
        cuboid(case, x0, x1, back - 0.08, back, top + 0.08, ORGAN_FLAT_TOP)
        cuboid(case, x0, x1, 0.0, back, top + 0.08, top + 0.2)
        cuboid(case, x0, x1, -0.03, back, ORGAN_FLAT_TOP, ORGAN_FLAT_TOP + 0.12)
        cuboid(case, x0, x1, 0.02, back, ORGAN_FLAT_TOP - 0.18, ORGAN_FLAT_TOP)
        x0, x1 = mirrored(0.5, 0.6, side)
        cuboid(case, x0, x1, -0.02, back, top + 0.08, ORGAN_TOWER_TOP)
    cuboid(case, -0.5, 0.5, back - 0.08, back, top + 0.08, ORGAN_TOWER_TOP)
    cuboid(case, -0.5, 0.5, -0.0, back, top + 0.08, top + 0.3)
    cuboid(case, -0.5, 0.5, 0.0, back, ORGAN_TOWER_TOP - 0.22, ORGAN_TOWER_TOP)
    cuboid(case, -0.66, 0.66, -0.06, back + 0.02, ORGAN_TOWER_TOP, ORGAN_TOWER_TOP + 0.12)
    prism(case, (0.0, back / 2 - 0.02, 1.2, back + 0.04, ORGAN_TOWER_TOP + 0.12), (0.0, back / 2 - 0.02, 0.9, back - 0.06, ORGAN_TOWER_TOP + 0.3))
    model.plain(case, "wood")
    case = bmesh.new()
    # The console: a key desk standing out of the case between two cheeks, the upper manual's shelf, the music desk.
    for side in (-1, 1):
        x0, x1 = mirrored(0.62, 0.72, side)
        outline_along_x(case, [(-0.56, 0.62), (-0.05, 0.62), (-0.05, 1.12), (-0.2, 1.12), (-0.56, 0.78)], x0, x1)
    cuboid(case, -0.62, 0.62, -0.54, -0.05, 0.6, 0.7)
    cuboid(case, -0.62, 0.62, -0.34, -0.05, 0.7, 0.8)
    prism_y(case, (0.0, 0.98, 1.1, 0.28, -0.17), (0.0, 1.0, 1.1, 0.26, -0.14))
    cuboid(case, -0.62, 0.62, -0.08, -0.05, 0.1, 0.6)
    # The pedalboard's frame on the floor in front, short of the bench, which the game stands a square out from the wall.
    cuboid(case, -0.62, 0.62, -0.8, -0.05, 0.0, 0.05)
    model.hard(case, "wood", bevel=0.008, segments=1)
    keys = bmesh.new()
    sharps = bmesh.new()
    for low, front, deep in ((0.7, -0.53, 0.17), (0.8, -0.33, 0.15)):
        cuboid(keys, -0.6, 0.6, front, front + deep, low, low + 0.022)
        for octave in range(5):
            for k in (1, 2, 4, 5, 6):
                x = -0.6 + 0.24 * octave + 0.24 * k / 7.0
                cuboid(sharps, x - 0.007, x + 0.007, front + deep * 0.4, front + deep, low + 0.022, low + 0.036)
    for i in range(13):
        x = -0.54 + 1.08 * i / 12
        cuboid(keys, x - 0.03, x + 0.03, -0.78, -0.4, 0.05, 0.08)
    for i in (1, 2, 4, 5, 6, 8, 9, 11):
        x = -0.54 + 1.08 * (i - 0.5) / 12
        cuboid(sharps, x - 0.018, x + 0.018, -0.6, -0.4, 0.08, 0.12)
    model.plain(keys, "ceramic")
    model.plain(sharps, "rubber")
    knobs = bmesh.new()
    # The stops, in three rows on the case's front either side of the console.
    for side in (-1, 1):
        for row in range(3):
            for col in range(3):
                x, z = side * (0.86 + 0.09 * col), 0.82 + 0.1 * row
                tube(knobs, (x, -0.05, z), (x, -0.1, z), 0.016, segments=8)
    model.turned(knobs, "ceramic", sharp=50.0)
    pipes = bmesh.new()
    mouths = bmesh.new()
    organ_pipes(pipes, mouths, -0.48, 0.48, 7, top + 0.3, [3.15, 3.35, 3.55, 3.7, 3.55, 3.35, 3.15], 0.058, 0.2)
    for side in (-1, 1):
        x0, x1 = mirrored(0.6, 1.15, side)
        tops = [2.45, 2.62, 2.78, 2.92]
        organ_pipes(pipes, mouths, x0, x1, 4, top + 0.2, tops if side > 0 else tops[::-1], 0.05, 0.22)
    # The crosses on the tower's cornice.
    for x in (-0.42, 0.42):
        cuboid(pipes, x - 0.018, x + 0.018, 0.12, 0.16, ORGAN_TOWER_TOP + 0.12, ORGAN_TOWER_TOP + 0.5)
        cuboid(pipes, x - 0.1, x + 0.1, 0.12, 0.16, ORGAN_TOWER_TOP + 0.34, ORGAN_TOWER_TOP + 0.38)
    model.turned(pipes, "metal", sharp=50.0)
    model.plain(mouths, "rubber")
    return model


# How high a church steeple's belfry is to the top of its cap, where the spire on the storey above stands, in metres.
BELFRY_TOP = 2.21


def steeple_belfry():
    """The tower of a small church's steeple, where it comes up through the ridge: a boarded box, a cornice, a hipped skirt of
    sheet metal up to a narrower boarded lantern with two bands round it, and a low metal cap the spire stands on."""
    model = Model("steeple_belfry")
    boards = bmesh.new()
    cuboid(boards, -0.47, 0.47, -0.47, 0.47, 0.0, 0.99)
    cuboid(boards, -0.49, 0.49, -0.49, 0.49, 0.99, 1.1)
    cuboid(boards, -0.345, 0.345, -0.345, 0.345, 1.31, 1.35)
    cuboid(boards, -0.325, 0.325, -0.325, 0.325, 1.35, 1.86)
    for z in (1.5, 1.66):
        cuboid(boards, -0.333, 0.333, -0.333, 0.333, z, z + 0.02)
    cuboid(boards, -0.35, 0.35, -0.35, 0.35, 1.86, 1.92)
    model.hard(boards, "paint", bevel=0.008, segments=2)
    roofing = bmesh.new()
    prism(roofing, (0.0, 0.0, 0.98, 0.98, 1.1), (0.0, 0.0, 0.66, 0.66, 1.31))
    prism(roofing, (0.0, 0.0, 0.46, 0.46, 1.92), (0.0, 0.0, 0.4, 0.4, BELFRY_TOP))
    model.hard(roofing, "metal", bevel=0.006, segments=1)
    return model


def steeple_spire():
    """A small church's spire, the storey over its belfry: a tall four-sided point of sheet metal with an iron cross on it."""
    model = Model("steeple_spire")
    point = bmesh.new()
    prism(point, (0.0, 0.0, 0.38, 0.38, 0.0), (0.0, 0.0, 0.02, 0.02, 2.25))
    model.hard(point, "metal", bevel=0.004, segments=1)
    cross = bmesh.new()
    cuboid(cross, -0.016, 0.016, -0.016, 0.016, 2.22, 2.7)
    cuboid(cross, -0.11, 0.11, -0.016, 0.016, 2.54, 2.575)
    model.hard(cross, "rubber", bevel=0.004, segments=1)
    return model


# A church tower's spire over its two squares by two, as its sprites paint it: how high its face is, in metres, at each
# distance out from the apex towards the middle of a side, in squares. The flared one's faces sweep out to the foot.
TOWER_SPIRE = [(0.0, 2.6), (1.04, 0.0)]
TOWER_SPIRE_FLARED = [(0.0, 2.6), (0.125, 2.04), (0.25, 1.48), (0.375, 1.07), (0.5, 0.74), (0.625, 0.49), (0.75, 0.31), (0.875, 0.15),
                      (1.0, 0.0)]


def tower_spire(name, profile):
    """The spire on a church's tower: four faces of slate rising from the tower's top to a point over its middle."""
    model = Model(name)
    slate = bmesh.new()
    apex = slate.verts.new((0.0, 0.0, profile[0][1]))
    rings = [[slate.verts.new((dx * out, dy * out, high)) for dx, dy in ((-1, -1), (1, -1), (1, 1), (-1, 1))] for out, high in profile[1:]]
    for i in range(4):
        slate.faces.new((apex, rings[0][i], rings[0][(i + 1) % 4]))
    for upper, lower in zip(rings, rings[1:]):
        for i in range(4):
            j = (i + 1) % 4
            slate.faces.new((upper[i], lower[i], lower[j], upper[j]))
    slate.faces.new(rings[-1][::-1])
    model.turned(slate, "metal", sharp=40.0)
    return model


def tower_cross():
    """The iron cross on a church tower's spire, its arms along X."""
    model = Model("tower_cross")
    cross = bmesh.new()
    cuboid(cross, -0.04, 0.04, -0.04, 0.04, 0.0, 1.28)
    cuboid(cross, -0.35, 0.35, -0.04, 0.04, 0.78, 0.86)
    model.hard(cross, "rubber", bevel=0.006, segments=1)
    return model


def amplifier():
    """A guitar amplifier: a covered box with its grille at -Y and its knobs on top."""
    model = Model("amplifier")
    box = bmesh.new()
    cuboid(box, -0.24, 0.24, -0.13, 0.13, 0.0, 0.42)
    model.hard(box, "wood", bevel=0.012, segments=2)
    grille = bmesh.new()
    cuboid(grille, -0.2, 0.2, -0.14, -0.13, 0.04, 0.32)
    model.hard(grille, "cloth", bevel=0.003, segments=1)
    panel = bmesh.new()
    cuboid(panel, -0.2, 0.2, -0.14, -0.13, 0.34, 0.39)
    model.hard(panel, "plastic", bevel=0.003, segments=1)
    return model


def drum(bm, heads, x, y, z, radius, deep, lying=False):
    """A drum's shell into bm and its two heads into heads, its middle at (x, y, z), upright or lying along Y."""
    to = Matrix.Translation((x, y, z)) @ (Matrix.Rotation(math.radians(90), 4, "X") if lying else Matrix.Identity(4))
    lathe(bm, [(radius, -deep / 2 + 0.01), (radius, deep / 2 - 0.01)], segments=20, caps=False, to=to)
    for end in (-1, 1):
        lathe(heads, [(0.0, end * deep / 2), (radius * 1.02, end * deep / 2), (radius * 1.02, end * (deep / 2 - 0.012)), (0.0, end * (deep / 2 - 0.012))],
              segments=20, to=to)


def drum_kick():
    """A bass drum lying on its side, its face to -Y, with two drums on a mount over it."""
    model = Model("drum_kick")
    shells, heads = bmesh.new(), bmesh.new()
    drum(shells, heads, 0.0, 0.0, 0.34, 0.33, 0.45, lying=True)
    drum(shells, heads, -0.16, -0.1, 0.9, 0.16, 0.2)
    drum(shells, heads, 0.17, -0.1, 0.92, 0.17, 0.21)
    model.turned(shells, "vinyl", sharp=50.0)
    model.turned(heads, "linen", sharp=50.0)
    stand = bmesh.new()
    rod(stand, (0.0, -0.1, 0.66), (0.0, -0.1, 0.8), 0.014, segments=6)
    rod(stand, (-0.2, -0.05, 0.0), (-0.24, -0.1, 0.06), 0.01, segments=6)
    rod(stand, (0.2, -0.05, 0.0), (0.24, -0.1, 0.06), 0.01, segments=6)
    model.turned(stand, "metal")
    return model


def drum_stand():
    """A drum on a stand of three legs, a snare or a tom, with a cymbal on a stand of its own beside it."""
    model = Model("drum_stand")
    shells, heads = bmesh.new(), bmesh.new()
    drum(shells, heads, 0.0, 0.0, 0.58, 0.17, 0.15)
    model.turned(shells, "vinyl", sharp=50.0)
    model.turned(heads, "linen", sharp=50.0)
    steel = bmesh.new()
    for x, y, top in ((0.0, 0.0, 0.5), (0.22, 0.12, 1.02)):
        rod(steel, (x, y, 0.15), (x, y, top), 0.011, segments=6)
        for n in range(3):
            a = n * 2 * math.pi / 3
            rod(steel, (x + 0.2 * math.cos(a), y + 0.2 * math.sin(a), 0.0), (x, y, 0.16), 0.008, segments=6)
    model.turned(steel, "metal")
    cymbal = bmesh.new()
    lathe(cymbal, [(0.0, 1.06), (0.03, 1.05), (0.2, 1.02), (0.2, 1.015), (0.03, 1.045), (0.0, 1.055)], cx=0.22, cy=0.12, segments=24)
    model.turned(cymbal, "worktop", sharp=45.0)
    return model


def basket_pole():
    """The post a basketball hoop stands on: a steel pole a little more than a storey high."""
    model = Model("basket_pole")
    pole = bmesh.new()
    lathe(pole, [(0.0, 0.0), (0.055, 0.0), (0.055, 2.67), (0.045, 2.69), (0.0, 2.69)], segments=14)
    model.turned(pole, "paint", sharp=50.0)
    return model


def basket_board():
    """A basketball backboard a storey up, its face to -Y with the hoop and its net out in front, and the bracket at its back."""
    model = Model("basket_board", centred=False)
    board = bmesh.new()
    outline = [(-0.5, 0.0), (0.5, 0.0)] + [(0.5 * math.cos(math.radians(a)), 0.5 + 0.17 * math.sin(math.radians(a))) for a in range(0, 181, 15)]
    outline_along_y(board, outline, -0.02, 0.02)
    model.hard(board, "paint", bevel=0.008, segments=2)
    mark = bmesh.new()
    for x0, x1, z0, z1 in ((-0.2, 0.2, 0.08, 0.11), (-0.2, 0.2, 0.38, 0.41), (-0.2, -0.17, 0.08, 0.41), (0.17, 0.2, 0.08, 0.41)):
        cuboid(mark, x0, x1, -0.025, -0.02, z0, z1)
    rim = bmesh.new()
    lathe(rim, [(0.21, 0.12), (0.23, 0.12), (0.23, 0.14), (0.21, 0.14)], cy=-0.26, segments=24, closed=True)
    cuboid(rim, -0.05, 0.05, -0.06, -0.02, 0.1, 0.16)
    model.hard(mark, "vinyl", bevel=0.002, segments=1)
    model.turned(rim, "vinyl", sharp=50.0)
    net = bmesh.new()
    for n in range(12):
        a = n * math.pi / 6
        rod(net, (0.13 * math.cos(a), -0.26 + 0.13 * math.sin(a), -0.24), (0.22 * math.cos(a + 0.3), -0.26 + 0.22 * math.sin(a + 0.3), 0.12), 0.005, segments=4)
    model.turned(net, "linen", sharp=60.0)
    back = bmesh.new()
    cuboid(back, -0.18, 0.18, 0.02, 0.06, 0.05, 0.45)
    model.hard(back, "plastic", bevel=0.006, segments=1)
    return model


# A park's fountain: a round basin over three squares by three, how far its wall is from the middle and how high its rim, its water.
FOUNTAIN_RADIUS, FOUNTAIN_RIM, FOUNTAIN_WATER = 1.18, 0.5, 0.4


def fountain_part(name, sector):
    """The part of a fountain's basin on one of the squares round its middle, or the middle itself (sector None): the wall, rim and
    water of the angles the square takes, its sprite's square being the one at sector * 45 degrees from the middle, +X first and
    turning to +Y. The model is built on its own square's middle, the basin's middle a square away along that way. The corner square
    the game puts nothing on, at -X -Y, is shared between its two neighbours."""
    model = Model(name, centred=False)
    if sector is None:
        cx = cy = 0.0
        spans = []
    else:
        a = math.radians(sector * 45)
        cx, cy = -round(math.cos(a)), -round(math.sin(a))
        lo, hi = sector * 45 - 22.5, sector * 45 + 22.5
        if sector == 4:
            hi += 22.5
        if sector == 6:
            lo -= 22.5
        spans = [(lo, hi)]
    steps = 6
    wall, rim, water = bmesh.new(), bmesh.new(), bmesh.new()
    inner = FOUNTAIN_RADIUS - 0.16
    for lo, hi in spans:
        n = max(2, round((hi - lo) / 45 * steps))
        ring = [math.radians(lo + (hi - lo) * i / n) for i in range(n + 1)]
        for r0, r1, z0, z1, into in ((inner + 0.02, FOUNTAIN_RADIUS - 0.03, 0.0, FOUNTAIN_RIM - 0.07, wall),
                                     (inner, FOUNTAIN_RADIUS, FOUNTAIN_RIM - 0.07, FOUNTAIN_RIM, rim)):
            for t0, t1 in zip(ring, ring[1:]):
                corners = [(r * math.cos(t), r * math.sin(t)) for r, t in ((r0, t0), (r1, t0), (r1, t1), (r0, t1))]
                low = [into.verts.new((cx + x, cy + y, z0)) for x, y in corners]
                high = [into.verts.new((cx + x, cy + y, z1)) for x, y in corners]
                into.faces.new(low[::-1])
                into.faces.new(high)
                for i in range(4):
                    j = (i + 1) % 4
                    into.faces.new((low[i], low[j], high[j], high[i]))
        for t0, t1 in zip(ring, ring[1:]):
            corners = [(r * math.cos(t), r * math.sin(t)) for r, t in ((0.5, t0), (inner + 0.03, t0), (inner + 0.03, t1), (0.5, t1))]
            low = [water.verts.new((cx + x, cy + y, 0.02)) for x, y in corners]
            high = [water.verts.new((cx + x, cy + y, FOUNTAIN_WATER)) for x, y in corners]
            water.faces.new(low[::-1])
            water.faces.new(high)
            for i in range(4):
                j = (i + 1) % 4
                water.faces.new((low[i], low[j], high[j], high[i]))
    if sector is None:
        lathe(water, [(0.0, 0.02), (0.5, 0.02), (0.5, FOUNTAIN_WATER), (0.0, FOUNTAIN_WATER)], segments=24)
    else:
        # Built as the map lies, +X east: a model's +X is the map's west.
        for part in (wall, rim):
            bmesh.ops.scale(part, vec=(-1.0, 1.0, 1.0), verts=part.verts)
        model.hard(wall, "metal", bevel=0.01, segments=1)
        model.hard(rim, "worktop", bevel=0.012, segments=2)
    bmesh.ops.scale(water, vec=(-1.0, 1.0, 1.0), verts=water.verts)
    model.plain(water, "pane")
    return model


def fountain_tiers():
    """The fountain in the middle of the basin: three bowls one over the other on a turned stem."""
    model = Model("fountain_tiers")
    tiers = bmesh.new()
    lathe(tiers, [(0.0, 0.0), (0.16, 0.0), (0.16, 0.1), (0.1, 0.16), (0.08, 0.5), (0.12, 0.62), (0.5, 0.72), (0.53, 0.8), (0.5, 0.82),
                  (0.12, 0.76), (0.06, 0.8), (0.05, 1.05), (0.09, 1.1), (0.32, 1.17), (0.34, 1.23), (0.31, 1.24), (0.08, 1.2),
                  (0.04, 1.24), (0.035, 1.42), (0.16, 1.48), (0.17, 1.53), (0.06, 1.55), (0.05, 1.62), (0.0, 1.66)], segments=28)
    bmesh.ops.scale(tiers, vec=(1.15, 1.15, 1.15), verts=tiers.verts)
    model.turned(tiers, "plastic", sharp=40.0)
    return model


# A net across a court: how high its foot and its top are, where its posts stand along the first and the last of its squares.
NET_FOOT, NET_TOP, NET_FIRST_POST, NET_LAST_POST = 1.02, 1.9, 0.02, 0.85


def court_net(name, first, last):
    """A square's length of a net across a court, along the model's X, with its post where the net starts on the first square of its
    run or ends on the last, and the bar the posts are joined by along the ground. Its origin is at the middle of its square."""
    model = Model(name, centred=False)
    x0 = NET_FIRST_POST + 0.04 - 0.5 if first else -0.5
    x1 = NET_LAST_POST - 0.04 - 0.5 if last else 0.5
    mesh = bmesh.new()
    cells = max(1, round((x1 - x0) / 0.1))
    for i in range(cells + 1):
        x = x0 + (x1 - x0) * i / cells
        cuboid(mesh, x - 0.004, x + 0.004, -0.004, 0.004, NET_FOOT, NET_TOP)
    for k in range(10):
        z = NET_FOOT + (NET_TOP - NET_FOOT) * k / 9
        cuboid(mesh, x0, x1, -0.004, 0.004, z - 0.004, z + 0.004)
    model.plain(mesh, "rubber")
    band = bmesh.new()
    cuboid(band, x0, x1, -0.008, 0.008, NET_TOP, NET_TOP + 0.05)
    model.hard(band, "linen", bevel=0.003, segments=1)
    steel = bmesh.new()
    rod(steel, (NET_FIRST_POST - 0.5 if first else -0.5, 0.0, 0.03), (NET_LAST_POST - 0.5 if last else 0.5, 0.0, 0.03), 0.02, segments=8)
    model.turned(steel, "metal")
    if first or last:
        posts = bmesh.new()
        for at, here in ((NET_FIRST_POST - 0.5, first), (NET_LAST_POST - 0.5, last)):
            if here:
                cuboid(posts, at - 0.03, at + 0.03, -0.03, 0.03, 0.0, NET_TOP + 0.1)
                cuboid(posts, at - 0.04, at + 0.04, -0.28, 0.28, 0.0, 0.03)
        model.hard(posts, "plastic", bevel=0.006, segments=1)
    return model


# A football goal: how high its crossbar is, where its posts stand along the first and the last of its squares.
GOAL_TOP, GOAL_FIRST_POST, GOAL_LAST_POST = 2.44, 0.42, 0.55


def goal(name, first, last):
    """A square's length of a goal's frame along the model's X, with a post where it starts on the first square of its run or ends on
    the last, and a round joint where the post meets the bar. Its origin is at the middle of its square."""
    model = Model(name, centred=False)
    frame = bmesh.new()
    x0 = GOAL_FIRST_POST - 0.5 if first else -0.5
    x1 = GOAL_LAST_POST - 0.5 if last else 0.5
    rod(frame, (x0, 0.0, GOAL_TOP), (x1, 0.0, GOAL_TOP), 0.06, segments=12)
    for x, here in ((x0, first), (x1, last)):
        if here:
            rod(frame, (x, 0.0, 0.0), (x, 0.0, GOAL_TOP), 0.06, segments=12)
            lathe(frame, [(0.0, GOAL_TOP - 0.06)] + [(0.06 * math.sin(math.pi * k / 6), GOAL_TOP - 0.06 * math.cos(math.pi * k / 6)) for k in range(1, 6)]
                  + [(0.0, GOAL_TOP + 0.06)], cx=x, segments=12)
    model.turned(frame, "paint", sharp=50.0)
    return model


def leisure():
    """Play, sport and music: a park's swings, slides, play house, roundabout and fountain, a gym's machines, tyre walls of a track,
    a basketball hoop, pianos, speakers, a jukebox, drums, a pinball table, and a church's lectern and altar."""
    return [swing_end(), swing_leg(), swing_beam("swing_beam"), swing_beam("swing_near", -0.05), swing_beam("swing_far", -0.25),
            swing_beam("swing_near_e", 0.05), swing_beam("swing_far_e", 0.25),
            sport_tyres("tyres_1", 1), sport_tyres("tyres_2", 2), sport_tyres("tyres_3", 3), sport_tyres("tyres_4", 4), sport_tyres("tyres_2_askew", 2, 0.04),
            sport_tyres("tyres_3_askew", 3, 0.04), sport_tyres("tyres_4_askew", 4, 0.04),
            play_castle(), treadmill(), weight_bench(), upright_piano(), piano_bench(), slide(), merry_go_round(),
            speaker_cabinet("speaker_tall", 0.46, 0.36, 0.82, "dark_wood"), speaker_cabinet("speaker_low", 0.46, 0.34, 0.64, "plastic"),
            jukebox(), grand_piano(), lectern(), pinball(), altar(), pipe_organ(), steeple_belfry(), steeple_spire(), tower_spire("tower_spire", TOWER_SPIRE),
            tower_spire("tower_spire_flared", TOWER_SPIRE_FLARED), tower_cross(), amplifier(), drum_kick(), drum_stand(),
            basket_pole(), basket_board(), court_net("net_first", True, False), court_net("net_middle", False, False),
            court_net("net_last", False, True), goal("goal_first", True, False), goal("goal_middle", False, False), goal("goal_last", False, True)] + \
        [fountain_part("fountain_%d" % sector, sector) for sector in range(8) if sector != 5] + [fountain_part("fountain_middle", None), fountain_tiers()]


# What a works, a warehouse, a garage and a distillery have: belt conveyors and the machines a line of them runs
# through, a fuel tank lying on saddles, a pipe down from the ceiling, crates, a rack of casks and the casks, a
# workbench, a mechanic's tool chest, a security desk and gas bottles. Industry.java stands them.

# How high the game says a conveyor's belt is: 34 pixels of its sprite.
BELT_TOP = 34 / 96 * 2.44949


def belt(model, half, y0, y1, top):
    """A belt between two steel side rails, from y0 to y1 along the model, its top a hair under the rails' at top."""
    band = bmesh.new()
    cuboid(band, -half + 0.03, half - 0.03, y0, y1, top - 0.06, top - 0.012)
    model.plain(band, "rubber")
    rails = bmesh.new()
    for side in (-1, 1):
        x0, x1 = sorted((side * (half - 0.03), side * half))
        cuboid(rails, x0, x1, y0, y1, top - 0.11, top)
    model.hard(rails, "steel", bevel=0.004, segments=1)


def conveyor():
    """A square's length of belt conveyor, to meet the next: the belt on its rails and a pair of legs at its back end with a
    bar between them."""
    model = Model("conveyor")
    half = 0.4
    belt(model, half, -0.5, 0.5, BELT_TOP)
    legs, feet = bmesh.new(), bmesh.new()
    y = 0.42
    for side in (-1, 1):
        x = side * (half - 0.035)
        cuboid(legs, x - 0.022, x + 0.022, y - 0.022, y + 0.022, 0.03, BELT_TOP - 0.11)
        cuboid(feet, x - 0.03, x + 0.03, y - 0.03, y + 0.03, 0.0, 0.03)
    cuboid(legs, -half + 0.035, half - 0.035, y - 0.015, y + 0.015, 0.16, 0.2)
    model.hard(legs, "steel", bevel=0.004, segments=1)
    model.plain(feet, "rubber")
    return model


def conveyor_boxed():
    """A square's length of belt conveyor on a closed steel cabinet, with a louvred panel in each side."""
    model = Model("conveyor_boxed")
    half = 0.42
    belt(model, half, -0.5, 0.5, BELT_TOP)
    case = bmesh.new()
    cuboid(case, -half + 0.02, half - 0.02, -0.47, 0.47, 0.04, BELT_TOP - 0.11)
    model.hard(case, "steel", bevel=0.008, segments=2)
    dark = bmesh.new()
    cuboid(dark, -half + 0.05, half - 0.05, -0.44, 0.44, 0.0, 0.04)
    for side in (-1, 1):
        for slat in range(6):
            z = 0.22 + slat * 0.06
            x0, x1 = sorted((side * (half - 0.02), side * (half - 0.017)))
            cuboid(dark, x0, x1, -0.25, 0.25, z, z + 0.022)
    model.plain(dark, "rubber")
    return model


# A machine's case on a production line: half its width, its height, and the opening a belt runs through.
MACHINE_CASE_HALF, CASE_HIGH, HOLE_HALF, HOLE_LOW, HOLE_HIGH = 0.45, 1.55, 0.27, 0.7, 1.1


def case_top(model, x0, x1, y0, y1, high):
    """The steel lid on a machine's case, its ribs round the upper part and the dark plinth it stands on."""
    lid = bmesh.new()
    cuboid(lid, x0 + 0.01, x1 - 0.01, y0 + 0.01, y1 - 0.01, high - 0.04, high)
    model.hard(lid, "metal", bevel=0.008, segments=2)
    ribs = bmesh.new()
    for z in (high - 0.38, high - 0.28, high - 0.18):
        cuboid(ribs, x0 - 0.012, x1 + 0.012, y0 - 0.012, y1 + 0.012, z, z + 0.05)
    model.hard(ribs, "paint", bevel=0.01, segments=2)
    plinth = bmesh.new()
    cuboid(plinth, x0 + 0.03, x1 - 0.03, y0 + 0.03, y1 - 0.03, 0.0, 0.04)
    model.plain(plinth, "plastic")


def machine_case():
    """A machine's tall painted case with a square opening through it front to back, and the belt that runs through it."""
    model = Model("machine_case")
    w = MACHINE_CASE_HALF
    body = bmesh.new()
    cuboid(body, -w, w, -w, w, 0.04, HOLE_LOW)
    cuboid(body, -w, w, -w, w, HOLE_HIGH, CASE_HIGH - 0.04)
    cuboid(body, -w, -HOLE_HALF, -w, w, HOLE_LOW, HOLE_HIGH)
    cuboid(body, HOLE_HALF, w, -w, w, HOLE_LOW, HOLE_HIGH)
    model.hard(body, "paint", bevel=0.01, segments=2)
    case_top(model, -w, w, -w, w, CASE_HIGH)
    belt(model, HOLE_HALF - 0.02, -0.5, 0.5, BELT_TOP)
    return model


def machine_shell():
    """The closed back half of a machine two squares long, a tall painted case that reaches the front of its square, where
    the other half of the machine stands. Its origin is the middle of its square."""
    model = Model("machine_shell", centred=False)
    w = MACHINE_CASE_HALF
    body = bmesh.new()
    cuboid(body, -w, w, -0.5, w, 0.04, CASE_HIGH - 0.04)
    model.hard(body, "paint", bevel=0.01, segments=2)
    case_top(model, -w, w, -0.5, w, CASE_HIGH)
    return model


def machine_outlet():
    """The front half of that machine: the rest of its case at the back of the square, and the belt coming out of it to the
    front under a glass hood, on a pair of legs. Its origin is the middle of its square."""
    model = Model("machine_outlet", centred=False)
    w = MACHINE_CASE_HALF
    body = bmesh.new()
    cuboid(body, -w, w, 0.0, 0.5, 0.04, CASE_HIGH - 0.04)
    model.hard(body, "paint", bevel=0.01, segments=2)
    case_top(model, -w, w, 0.0, 0.5, CASE_HIGH)
    belt(model, 0.33, -0.5, 0.0, BELT_TOP)
    hood = bmesh.new()
    cuboid(hood, -0.32, 0.32, -0.36, 0.0, BELT_TOP, BELT_TOP + 0.34)
    model.hard(hood, "pane", bevel=0.004, segments=1)
    legs = bmesh.new()
    for side in (-1, 1):
        x = side * 0.3
        cuboid(legs, x - 0.02, x + 0.02, -0.44, -0.4, 0.0, BELT_TOP - 0.11)
    cuboid(legs, -0.3, 0.3, -0.435, -0.405, 0.16, 0.2)
    model.hard(legs, "steel", bevel=0.004, segments=1)
    return model


def machine_column():
    """A narrow painted cabinet, ribbed round its upper part, with a steel pipe standing up its front to a gauge."""
    model = Model("machine_column")
    x, y = 0.27, 0.33
    body = bmesh.new()
    cuboid(body, -x, x, -y, y, 0.04, 1.56)
    model.hard(body, "paint", bevel=0.01, segments=2)
    case_top(model, -x, x, -y, y, 1.6)
    pipe = bmesh.new()
    lathe(pipe, [(0.0, 0.0), (0.06, 0.0), (0.06, 1.2), (0.045, 1.24), (0.0, 1.24)], cx=0.1, cy=-y - 0.065, segments=14)
    cuboid(pipe, 0.1 - 0.012, 0.1 + 0.012, -y - 0.065, -y, 0.95, 0.98)
    model.turned(pipe, "steel", sharp=40.0)
    gauge = bmesh.new()
    cuboid(gauge, 0.03, 0.17, -y - 0.14, -y - 0.12, 0.98, 1.12)
    model.hard(gauge, "plastic", bevel=0.004, segments=1)
    return model


def machine_drum():
    """A mixer: a steel drum lying along x between two painted cheeks on a low painted base, a rusty pipe up from the base
    into its front."""
    model = Model("machine_drum")
    base = bmesh.new()
    cuboid(base, -0.47, 0.47, -0.47, 0.47, 0.0, 0.28)
    for side in (-1, 1):
        x0, x1 = sorted((side * 0.42, side * 0.47))
        outline_along_x(base, [(-0.42, 0.28), (0.42, 0.28), (0.24, 0.98), (-0.24, 0.98)], x0, x1)
    model.hard(base, "paint", bevel=0.008, segments=2)
    drum = bmesh.new()
    lathe(drum, [(0.0, -0.42), (0.25, -0.42), (0.29, -0.38), (0.29, 0.38), (0.25, 0.42), (0.0, 0.42)], segments=24, to=along_x(0.0, 0.0, 0.66))
    model.turned(drum, "steel", sharp=40.0)
    pipe = bmesh.new()
    lathe(pipe, [(0.0, 0.28), (0.035, 0.28), (0.035, 0.6), (0.0, 0.6)], cx=0.12, cy=-0.3, segments=12)
    model.turned(pipe, "vinyl", sharp=40.0)
    return model


# A fuel tank lying on its side on two saddles: its radius and how high its axis is.
TANK_RADIUS, TANK_AXIS = 0.95, 1.15


def half_tank(bm, stations):
    """The +Y half of a tank lying along x, as rings at (x, radius), closed at both ends: the half the game paints on one
    row of the squares it lies on. Its cut face is on the axis, where the other row's half meets it."""
    rings = []
    for x, radius in stations:
        rings.append([bm.verts.new((x, radius * math.cos(math.radians(a)), TANK_AXIS + radius * math.sin(math.radians(a))))
                      for a in range(-90, 91, 12)])
    for a, b in zip(rings, rings[1:]):
        count = len(a)
        for i in range(count):
            j = (i + 1) % count
            bm.faces.new((a[i], a[j], b[j], b[i]))
    bm.faces.new(rings[0][::-1])
    bm.faces.new(rings[-1])


def tank_length():
    """A square's length of the tank's half, open to the next: its origin is on the axis, at the middle of the square's
    length."""
    model = Model("tank_length", centred=False)
    shell = bmesh.new()
    half_tank(shell, [(-0.5, TANK_RADIUS), (0.5, TANK_RADIUS)])
    model.turned(shell, "paint", sharp=20.0)
    return model


def tank_end(name, way):
    """The end square of the tank's half: its length ends in a dished head, to +X for way 1 and to -X for way -1, and a
    concrete saddle stands under it."""
    model = Model(name, centred=False)
    shell = bmesh.new()
    stations = [(-0.5, TANK_RADIUS), (0.18, TANK_RADIUS), (0.24, TANK_RADIUS * 0.97), (0.29, TANK_RADIUS * 0.88), (0.32, TANK_RADIUS * 0.7),
                (0.335, TANK_RADIUS * 0.4), (0.34, 0.02)]
    half_tank(shell, [(x * way, radius) for x, radius in stations][::way])
    model.turned(shell, "paint", sharp=20.0)
    saddle = bmesh.new()
    x0, x1 = sorted((-0.2 * way, 0.02 * way))
    cuboid(saddle, x0, x1, 0.0, 0.72, 0.0, 0.62)
    model.hard(saddle, "metal", bevel=0.01, segments=2)
    return model


def downpipe():
    """A pipe down from the ceiling through the middle of its square, that turns at the floor and runs out to the front.
    Its origin is the foot of the upright."""
    model = Model("downpipe", centred=False)
    radius, low, bend, top = 0.065, 0.075, 0.12, 2.44949
    pipe = bmesh.new()
    lathe(pipe, [(radius, low + bend), (radius, top)], segments=14, caps=False)
    lathe(pipe, [(radius, bend), (radius, 0.42)], segments=14, caps=False, to=along_y(0.0, 0.0, low))
    rings = []
    for i in range(7):
        angle = math.radians(90 * i / 6)
        centre = Vector((0.0, -bend + bend * math.cos(angle), low + bend - bend * math.sin(angle)))
        out = Vector((0.0, math.cos(angle), -math.sin(angle)))
        rings.append([pipe.verts.new(centre + (Vector((1.0, 0.0, 0.0)) * math.cos(2 * math.pi * k / 14) + out * math.sin(2 * math.pi * k / 14)) * radius)
                      for k in range(14)])
    for a, b in zip(rings, rings[1:]):
        for k in range(14):
            pipe.faces.new((a[k], a[(k + 1) % 14], b[(k + 1) % 14], b[k]))
    model.turned(pipe, "plastic", sharp=40.0)
    collars = bmesh.new()
    for z in (0.45, top - 0.05):
        lathe(collars, [(0.0, z), (radius + 0.012, z), (radius + 0.012, z + 0.04), (0.0, z + 0.04)], segments=14)
    lathe(collars, [(0.0, 0.38), (radius + 0.012, 0.38), (radius + 0.012, 0.42), (0.0, 0.42)], segments=14, to=along_y(0.0, 0.0, low))
    model.turned(collars, "metal", sharp=40.0)
    return model


def cask_staves(bm, low, high, end, belly, to=None):
    """A wooden cask's body from low to high along its axis, end its radius at the heads and belly at the middle."""
    steps = 8
    profile = [(0.0, low), (end - 0.02, low)]
    for i in range(steps + 1):
        t = i / steps
        profile.append((end + (belly - end) * math.sin(math.pi * t), low + (high - low) * t))
    profile += [(end - 0.02, high), (0.0, high)]
    lathe(bm, profile, segments=24, to=to)


def cask_hoops(bm, low, high, end, belly, to=None):
    for t in (0.06, 0.28, 0.72, 0.94):
        z = low + (high - low) * t
        radius = end + (belly - end) * math.sin(math.pi * t) + 0.006
        lathe(bm, [(radius - 0.01, z - 0.02), (radius, z - 0.02), (radius, z + 0.02), (radius - 0.01, z + 0.02)], segments=24, closed=True, to=to)


# A cask: how long it is, and its radius at its heads and its belly.
CASK_LONG, CASK_END, CASK_BELLY = 0.88, 0.28, 0.33


def cask(name, lying):
    """A wooden cask bound with four iron hoops, standing on a head, or lying along x."""
    model = Model(name)
    to = along_x(-CASK_LONG / 2, 0.0, CASK_BELLY) if lying else None
    staves, hoops = bmesh.new(), bmesh.new()
    cask_staves(staves, 0.0, CASK_LONG, CASK_END, CASK_BELLY, to)
    cask_hoops(hoops, 0.0, CASK_LONG, CASK_END, CASK_BELLY, to)
    model.turned(staves, "dark_wood", sharp=50.0)
    model.turned(hoops, "plastic", sharp=40.0)
    return model


def barrel_rack(name, bays):
    """A rack of three plank shelves on four square posts, a cask lying along x in each of the bays, from the lowest up,
    that bays says."""
    model = Model(name)
    half, top = 0.47, 1.67
    shelves = (0.06, 0.84, top - 0.04)
    frame = bmesh.new()
    for x in (-1, 1):
        for y in (-1, 1):
            cuboid(frame, x * half - 0.03, x * half + 0.03, y * half - 0.03, y * half + 0.03, 0.0, top)
    for z in shelves:
        for side in (-1, 1):
            cuboid(frame, -half, half, side * half - 0.025, side * half + 0.025, z - 0.06, z)
    model.hard(frame, "dark_wood", bevel=0.005, segments=1)
    boards = bmesh.new()
    for z in shelves:
        for n in range(5):
            x0 = -half + 0.03 + n * (2 * half - 0.06) / 5
            cuboid(boards, x0 + 0.004, x0 + (2 * half - 0.06) / 5 - 0.004, -half + 0.03, half - 0.03, z, z + 0.04)
    model.hard(boards, "wood", bevel=0.004, segments=1)
    staves, hoops = bmesh.new(), bmesh.new()
    long = 2 * half - 0.12
    for bay in range(bays):
        to = along_x(-long / 2, 0.0, shelves[bay] + 0.04 + 0.33)
        cask_staves(staves, 0.0, long, 0.27, 0.33, to)
        cask_hoops(hoops, 0.0, long, 0.27, 0.33, to)
    if bays:
        model.turned(staves, "dark_wood", sharp=50.0)
        model.turned(hoops, "plastic", sharp=40.0)
    return model


def workbench():
    """A workbench two squares long: a thick wooden top on a frame of steel angle, legs at its ends joined low down."""
    model = Model("workbench")
    half, deep, top = 0.95, 0.42, 24 / 96 * 2.44949
    board = bmesh.new()
    cuboid(board, -half, half, -deep, deep, top - 0.05, top)
    model.hard(board, "wood", bevel=0.006, segments=2)
    frame = bmesh.new()
    for x in (-1, 1):
        for y in (-1, 1):
            cx, cy = x * (half - 0.07), y * (deep - 0.06)
            cuboid(frame, cx - 0.025, cx + 0.025, cy - 0.025, cy + 0.025, 0.0, top - 0.05)
        cuboid(frame, x * (half - 0.07) - 0.02, x * (half - 0.07) + 0.02, -deep + 0.06, deep - 0.06, 0.12, 0.16)
    for y in (-1, 1):
        cuboid(frame, -half + 0.07, half - 0.07, y * (deep - 0.06) - 0.02, y * (deep - 0.06) + 0.02, top - 0.12, top - 0.05)
    cuboid(frame, -half + 0.07, half - 0.07, (deep - 0.06) - 0.02, (deep - 0.06) + 0.02, 0.12, 0.16)
    model.hard(frame, "plastic", bevel=0.004, segments=1)
    return model


def tool_chest():
    """A mechanic's tool chest on castors: a painted steel cabinet with a row of drawers in its front, each with a bright
    handle, and a rubber mat on its top."""
    model = Model("tool_chest")
    half, front, back, low, top = 0.4, -0.3, 0.3, 0.11, BELT_TOP
    body = bmesh.new()
    cuboid(body, -half, half, front, back, low, top - 0.012)
    for n in range(4):
        z0 = low + 0.04 + n * (top - low - 0.08) / 4
        z1 = z0 + (top - low - 0.08) / 4 - 0.012
        cuboid(body, -half + 0.03, half - 0.03, front - 0.012, front, z0, z1)
    model.hard(body, "paint", bevel=0.006, segments=2)
    handles = bmesh.new()
    for n in range(4):
        z = low + 0.04 + (n + 1) * (top - low - 0.08) / 4 - 0.05
        cuboid(handles, -half + 0.1, half - 0.1, front - 0.03, front - 0.012, z, z + 0.018)
    model.hard(handles, "metal", bevel=0.003, segments=1)
    dark = bmesh.new()
    cuboid(dark, -half + 0.005, half - 0.005, front + 0.005, back - 0.005, top - 0.012, top)
    for x in (-1, 1):
        for y in (front + 0.05, back - 0.05):
            cuboid(dark, x * (half - 0.05) - 0.012, x * (half - 0.05) + 0.012, y - 0.012, y + 0.012, 0.07, low)
            lathe(dark, [(0.0, -0.016), (0.035, -0.016), (0.035, 0.016), (0.0, 0.016)], segments=10, to=along_x(x * (half - 0.05) - 0.016, y, 0.035))
    model.hard(dark, "rubber", bevel=0.003, segments=1)
    return model


def security_desk():
    """A security desk: a steel cabinet whose front slopes back under a panel of two screens and a row of buttons."""
    model = Model("security_desk")
    half, front, back, low_top, high_top = 0.44, -0.33, 0.33, 0.82, 1.22
    slope_back = 0.06
    body = bmesh.new()
    outline_along_x(body, [(front, 0.03), (back, 0.03), (back, high_top), (slope_back, high_top), (front, low_top)], -half, half)
    model.hard(body, "steel", bevel=0.008, segments=2)
    rise, run = high_top - low_top, slope_back - front
    length = math.hypot(rise, run)
    up = Vector((0.0, run / length, rise / length))
    out = Vector((0.0, -rise / length, run / length))
    face = Vector((0.0, front, low_top))
    screens, buttons = bmesh.new(), bmesh.new()
    for x0, x1 in ((-0.36, -0.03), (0.03, 0.36)):
        corners = [face + up * 0.12 + out * 0.002, face + up * (length - 0.04) + out * 0.002]
        a, b = corners
        verts = [screens.verts.new((x, p.y, p.z)) for x, p in ((x0, a), (x1, a), (x1, b), (x0, b))]
        screens.faces.new(verts)
        back_verts = [screens.verts.new((x, p.y, p.z)) for x, p in ((x0, a - out * 0.004), (x1, a - out * 0.004), (x1, b - out * 0.004), (x0, b - out * 0.004))]
        screens.faces.new(back_verts[::-1])
        for i in range(4):
            j = (i + 1) % 4
            screens.faces.new((verts[i], back_verts[i], back_verts[j], verts[j]))
    model.plain(screens, "screen")
    for n in range(8):
        x = -0.35 + n * 0.1
        centre = face + up * 0.06
        cuboid(buttons, x - 0.018, x + 0.018, centre.y - 0.018, centre.y + 0.018, centre.z - 0.01, centre.z + 0.012)
    model.plain(buttons, "rubber")
    vents = bmesh.new()
    for slot in range(4):
        z = 0.12 + slot * 0.04
        cuboid(vents, half, half + 0.003, 0.05, 0.25, z, z + 0.015)
    model.plain(vents, "rubber")
    return model


def gas_bottle(name, radius, high):
    """A gas bottle standing on its foot ring: a round shoulder, a neck and its valve."""
    model = Model(name)
    body = bmesh.new()
    lathe(body, [(0.0, 0.0), (radius - 0.01, 0.0), (radius, 0.03), (radius, high - radius * 0.6), (radius * 0.75, high - radius * 0.15),
                 (radius * 0.35, high), (0.0, high)], segments=20)
    model.turned(body, "paint", sharp=40.0)
    valve = bmesh.new()
    lathe(valve, [(0.0, high), (0.022, high), (0.022, high + 0.05), (0.035, high + 0.05), (0.035, high + 0.08), (0.0, high + 0.08)], segments=10)
    cuboid(valve, -0.012, 0.05, -0.008, 0.008, high + 0.055, high + 0.07)
    model.turned(valve, "metal", sharp=40.0)
    return model


def propane_tank():
    """A propane tank: a squat drum with rounded ends on a foot ring, a collar round its valve to carry it by."""
    model = Model("propane_tank")
    radius, high = 0.16, 0.5
    body = bmesh.new()
    lathe(body, [(0.0, 0.07), (radius * 0.6, 0.075), (radius * 0.92, 0.1), (radius, 0.15), (radius, high - 0.1), (radius * 0.92, high - 0.05),
                 (radius * 0.6, high - 0.02), (0.0, high - 0.015)], segments=22)
    model.turned(body, "paint", sharp=40.0)
    rings = bmesh.new()
    lathe(rings, [(radius * 0.88, 0.0), (radius * 0.92, 0.0), (radius * 0.92, 0.1), (radius * 0.88, 0.1)], segments=22, closed=True)
    lathe(rings, [(0.085, high - 0.03), (0.095, high - 0.03), (0.095, high + 0.08), (0.085, high + 0.08)], segments=16, closed=True)
    lathe(rings, [(0.0, high - 0.02), (0.02, high - 0.02), (0.02, high + 0.06), (0.0, high + 0.06)], segments=10)
    model.turned(rings, "metal", sharp=40.0)
    return model


# The machines of a workshop: a lathe three squares long, a press, a drill press, a band saw, a press over a line's belt
# and a bench grinder. Industry.java stands them. Each one's case is paint, which takes the colour of its sprite.


def axis_to(centre, way):
    """Moves something made round the upright to stand at centre with its axis along way."""
    turn = Vector((0.0, 0.0, 1.0)).rotation_difference(Vector(way).normalized()).to_matrix().to_4x4()
    return Matrix.Translation(centre) @ turn


def handwheel(rim, hub, centre, way, radius, spokes=3, grip=None):
    """A handwheel turned about way through centre: a round rim, its spokes and hub, and a grip standing out of the rim
    towards whoever turns it, made in grip when given."""
    to = axis_to(centre, way)
    tube_r = radius * 0.11
    ring = [(radius + tube_r * math.cos(2 * math.pi * i / 6), tube_r * math.sin(2 * math.pi * i / 6)) for i in range(6)]
    lathe(rim, ring, segments=16, closed=True, to=to)
    lathe(hub, [(0.0, -0.022), (radius * 0.24, -0.022), (radius * 0.24, 0.022), (0.0, 0.022)], segments=12, to=to)
    for n in range(spokes):
        angle = 2 * math.pi * n / spokes + 0.3
        tip = Vector((math.cos(angle) * radius, math.sin(angle) * radius, 0.0))
        rod(hub, to @ (tip * 0.2), to @ tip, tube_r * 0.7, segments=6)
    if grip is not None:
        angle = 0.3 + math.pi / spokes
        at = Vector((math.cos(angle) * radius, math.sin(angle) * radius, 0.0))
        lathe(grip, [(0.0, 0.0), (tube_r * 1.3, 0.0), (tube_r * 1.4, radius * 0.5), (tube_r * 1.1, radius * 0.6), (0.0, radius * 0.6)], segments=10,
              to=to @ Matrix.Translation(at))


def louvres(bm, x0, x1, y, z0, z1, count, out=-1):
    """Slats across a face at y, their fronts out of it the way out says."""
    pitch = (z1 - z0) / count
    for n in range(count):
        z = z0 + n * pitch
        a, b = sorted((y, y + out * 0.006))
        cuboid(bm, x0, x1, a, b, z + pitch * 0.25, z + pitch * 0.75)


def dial(bm, x, y, z, radius, deep):
    """A round knob on a face at y, standing out of it towards -Y."""
    lathe(bm, [(0.0, 0.0), (radius, 0.0), (radius, deep * 0.7), (radius * 0.75, deep), (0.0, deep)], segments=10, to=axis_to((x, y, z), (0.0, -1.0, 0.0)))


def lever(stick, ball, start, end, radius=0.007):
    rod(stick, start, end, radius, segments=6)
    lathe(ball, [(0.0, -radius * 3), (radius * 2.6, -radius * 2), (radius * 3, 0.0), (radius * 2.6, radius * 2), (0.0, radius * 3)], segments=8,
          to=Matrix.Translation(end))


# A lathe's bed, its centre height and the top of its two pedestals, in metres over the floor.
LATHE_PEDESTAL, LATHE_BED, LATHE_CENTRE = 0.66, 0.92, 1.12


def lathe_pedestal(paint, rubber, metal, dark, steel, x0, x1):
    """A pedestal of the lathe from x0 to x1: a cabinet with a recessed plinth, a door, a louvre and a steel cap."""
    front, back, top = -0.38, 0.38, LATHE_PEDESTAL
    cuboid(paint, x0, x1, front + 0.02, back - 0.02, 0.06, top - 0.03)
    cuboid(rubber, x0 + 0.03, x1 - 0.03, front + 0.05, back - 0.05, 0.0, 0.06)
    cuboid(metal, x0 - 0.01, x1 + 0.01, front, back, top - 0.03, top)
    cuboid(paint, x0 + 0.05, x1 - 0.05, front + 0.012, front + 0.02, 0.12, top - 0.09)
    louvres(dark, x0 + 0.12, x0 + 0.42, front + 0.012, 0.18, 0.42, 6)
    cuboid(steel, x1 - 0.14, x1 - 0.1, front - 0.002, front + 0.012, 0.34, 0.42)


def engine_lathe():
    """A lathe three squares long, its origin the middle of the middle square, the headstock at -X and its front at -Y:
    the bed on two pedestals capped in steel with a chip pan between them, the carriage with its apron and handwheel,
    cross slide and tool post, and the tailstock with its quill and wheel. The headstock is lathe_head, which the
    square at the lathe's -X end stands; the square at its +X end stands nothing of its own."""
    model = Model("lathe", centred=False)
    paint, steel, dark, red, rubber, metal = bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new()
    top, bed, centre = LATHE_PEDESTAL, LATHE_BED, LATHE_CENTRE
    front, back = -0.38, 0.38
    lathe_pedestal(paint, rubber, metal, dark, steel, 0.66, 1.46)
    # The chip pan between them, a tray with a rolled lip all round it.
    cuboid(metal, -0.56, 0.66, front + 0.02, back - 0.02, 0.5, 0.52)
    for y0, y1 in ((front + 0.02, front + 0.05), (back - 0.05, back - 0.02)):
        cuboid(metal, -0.56, 0.66, y0, y1, 0.52, top - 0.02)
    cuboid(paint, -0.56, 0.66, back - 0.06, back - 0.03, 0.06, 0.5)
    # The bed and its two ways, with the lead screw and the feed rod along its front.
    cuboid(paint, -1.42, 1.42, -0.19, 0.19, top, bed)
    for y0, y1 in ((-0.17, -0.1), (0.1, 0.17)):
        prism(steel, (0.22, (y0 + y1) / 2, 2.4, y1 - y0, bed), (0.22, (y0 + y1) / 2, 2.4, (y1 - y0) * 0.45, bed + 0.026))
    for z, r in ((0.865, 0.011), (0.8, 0.009)):
        rod(steel, (-0.78, -0.21, z), (1.4, -0.21, z), r, segments=6)
    cuboid(paint, 1.36, 1.42, -0.23, -0.19, 0.77, 0.9)
    stick, ball = bmesh.new(), bmesh.new()
    # The carriage: saddle on the ways, apron hanging in front with its handwheel, cross slide, compound and tool post.
    cuboid(paint, -0.2, 0.2, -0.23, 0.23, bed + 0.026, bed + 0.08)
    cuboid(paint, -0.21, 0.21, -0.33, -0.22, 0.74, bed + 0.08)
    cuboid(steel, -0.09, 0.09, -0.29, 0.19, bed + 0.08, bed + 0.12)
    cuboid(steel, -0.07, 0.07, -0.13, 0.07, bed + 0.12, bed + 0.155)
    cuboid(rubber, -0.04, 0.04, -0.1, -0.02, bed + 0.155, bed + 0.23)
    cuboid(steel, -0.01, 0.01, -0.02, 0.05, centre - 0.012, centre + 0.004)
    rim, hub = bmesh.new(), bmesh.new()
    handwheel(red, hub, (-0.09, -0.355, 0.86), (0.0, -1.0, 0.0), 0.08, grip=rubber)
    handwheel(rim, hub, (0.0, -0.32, bed + 0.1), (0.0, -1.0, 0.0), 0.04, grip=rubber)
    lever(stick, ball, (0.12, -0.33, 0.82), (0.17, -0.39, 0.88))
    # The tailstock: base, body, quill and centre, and its wheel at the far end.
    cuboid(paint, 0.88, 1.26, -0.14, 0.14, bed + 0.026, bed + 0.07)
    prism(paint, (1.07, 0.0, 0.36, 0.2, bed + 0.07), (1.08, 0.0, 0.3, 0.16, centre + 0.07))
    lathe(steel, [(0.0, 0.0), (0.03, 0.0), (0.03, 0.12), (0.018, 0.13), (0.0, 0.17)], segments=16,
          to=along_x(0.92, 0.0, centre) @ Matrix.Rotation(math.radians(180), 4, "Y"))
    lathe(steel, [(0.0, 0.0), (0.012, 0.0), (0.012, 0.06), (0.0, 0.06)], segments=8, to=along_x(1.23, 0.0, centre))
    handwheel(rim, hub, (1.3, 0.0, centre), (1.0, 0.0, 0.0), 0.07, grip=rubber)
    model.hard(paint, "paint", bevel=0.01, segments=2)
    model.hard(steel, "steel", bevel=0.003, segments=1)
    model.hard(metal, "metal", bevel=0.004, segments=1)
    model.plain(dark, "plastic")
    model.turned(red, "vinyl")
    model.turned(rubber, "rubber")
    model.turned(rim, "steel")
    model.turned(hub, "steel")
    model.turned(stick, "steel")
    model.turned(ball, "rubber")
    return model


def lathe_headstock():
    """The square at the lathe's -X end: the pedestal there, and over it the headstock and its chuck. Its origin is the
    middle of that square, the lathe's at +X, and its front is the lathe's. The pedestal is a cabinet with a recessed
    plinth, a door, a louvre and a steel cap, as the other is, and the headstock's case has a dark panel on its front
    with dials, a stop button and gear levers, and a lever on its top."""
    model = Model("lathe_head", centred=False)
    paint, steel, dark, red, rubber, metal = bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new()
    bed, centre = LATHE_BED, LATHE_CENTRE
    lathe_pedestal(paint, rubber, metal, dark, steel, -0.46, 0.44)
    cuboid(paint, -0.44, 0.22, -0.3, 0.3, bed, 1.42)
    cuboid(dark, -0.36, 0.14, -0.308, -0.3, 0.98, 1.34)
    for x, z in ((-0.2, 1.22), (-0.02, 1.22), (-0.2, 1.07)):
        dial(rubber, x, -0.308, z, 0.035, 0.025)
    dial(red, 0.0, -0.308, 1.07, 0.024, 0.02)
    stick, ball = bmesh.new(), bmesh.new()
    lever(stick, ball, (0.08, -0.31, 1.05), (0.08, -0.38, 1.13))
    lever(stick, ball, (-0.1, -0.31, 1.3), (-0.1, -0.38, 1.36))
    lever(stick, ball, (-0.2, 0.08, 1.42), (-0.05, 0.08, 1.5))
    lathe(steel, [(0.0, 0.0), (0.06, 0.0), (0.06, 0.03), (0.135, 0.035), (0.135, 0.13), (0.12, 0.14), (0.03, 0.14), (0.0, 0.14)], segments=20,
          to=along_x(0.22, 0.0, centre))
    for n in range(3):
        angle = 2 * math.pi * n / 3 + 0.5
        jaw = bmesh.new()
        cuboid(jaw, 0.36, 0.4, -0.02, 0.02, -0.04, 0.04)
        bmesh.ops.rotate(jaw, cent=(0, 0, 0), matrix=Matrix.Rotation(angle, 3, "X"), verts=jaw.verts[:])
        bmesh.ops.translate(jaw, vec=(0.0, math.cos(angle) * 0.075, centre + math.sin(angle) * 0.075), verts=jaw.verts[:])
        model.hard(jaw, "steel", bevel=0.003, segments=1)
    model.hard(paint, "paint", bevel=0.01, segments=2)
    model.turned(steel, "steel")
    model.hard(metal, "metal", bevel=0.004, segments=1)
    model.plain(dark, "plastic")
    model.turned(red, "vinyl")
    model.turned(rubber, "rubber")
    model.turned(stick, "steel")
    model.turned(ball, "rubber")
    return model


def hydraulic_press(name, loaded):
    """A press that stands on one square, its front at -Y: a cabinet with a recess under its table for the operator's knees
    between a switch tower at its left and a cupboard at its right, vents in its back, a wooden table, four columns up
    to the head with its ram and platen, glass guards round the table but for the front's right half, and a pendant on
    its cable at the front right. A loaded one has a die on its table."""
    model = Model(name)
    paint, steel, dark, rubber, glass, red = bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new()
    table, head, front, back = 0.9, 1.3, -0.4, 0.38
    cuboid(rubber, -0.39, 0.39, front + 0.03, back - 0.03, 0.0, 0.05)
    cuboid(paint, -0.42, -0.12, front, back, 0.05, table)
    cuboid(paint, 0.14, 0.42, front + 0.02, back, 0.05, table)
    cuboid(paint, -0.12, 0.14, 0.02, back, 0.05, table)
    cuboid(paint, -0.12, 0.14, front + 0.02, back, 0.05, 0.12)
    cuboid(dark, -0.36, -0.18, front - 0.008, front, 0.5, 0.8)
    for z, kind in ((0.74, red), (0.66, rubber), (0.58, rubber)):
        dial(kind, -0.27, front - 0.008, z, 0.018, 0.016)
    cuboid(paint, 0.18, 0.38, front + 0.012, front + 0.02, 0.12, table - 0.06)
    cuboid(steel, 0.33, 0.35, front + 0.002, front + 0.012, 0.44, 0.56)
    louvres(dark, -0.32, 0.32, back + 0.006, 0.14, 0.44, 7, out=1)
    for x in (-0.06, 0.02, 0.08):
        rod(rubber, (x, 0.02, 0.86), (x + 0.02, -0.02, 0.14), 0.01, segments=6)
    cuboid(paint, -0.44, 0.44, front - 0.02, back + 0.02, table, table + 0.035)
    wood = bmesh.new()
    cuboid(wood, -0.42, 0.42, front, back, table + 0.035, table + 0.075)
    model.hard(wood, "wood", bevel=0.004, segments=1)
    for x in (-0.32, 0.32):
        for y in (-0.28, 0.27):
            lathe(steel, [(0.0, table + 0.075), (0.034, table + 0.075), (0.034, head), (0.0, head)], segments=14, cx=x, cy=y)
    cuboid(paint, -0.38, 0.38, -0.36, 0.36, head, 1.48)
    cuboid(paint, -0.44, 0.44, -0.42, 0.42, 1.48, 1.62)
    cuboid(dark, -0.3, -0.06, -0.368, -0.36, 1.36, 1.44)
    for x, kind in ((-0.25, red), (-0.18, rubber), (-0.11, rubber)):
        dial(kind, x, -0.368, 1.4, 0.012, 0.01)
    lathe(steel, [(0.0, 1.06), (0.2, 1.06), (0.2, 1.1), (0.08, 1.1), (0.08, head), (0.0, head)], segments=24)
    for x0, x1, y0, y1 in ((-0.4, 0.0, -0.43, -0.424), (-0.444, -0.438, -0.4, 0.36), (0.438, 0.444, -0.4, 0.36), (-0.4, 0.4, 0.356, 0.362)):
        cuboid(glass, x0, x1, y0, y1, table + 0.08, head - 0.02)
    for x, y in ((-0.41, -0.43), (0.0, -0.43), (-0.44, 0.36), (0.44, 0.36)):
        cuboid(steel, x - 0.01, x + 0.01, y - 0.01, y + 0.01, table + 0.075, head)
    cuboid(dark, 0.3, 0.4, front - 0.1, front - 0.04, table + 0.12, table + 0.36)
    for z, kind in ((table + 0.31, red), (table + 0.25, rubber), (table + 0.19, rubber)):
        dial(kind, 0.35, front - 0.1, z, 0.016, 0.014)
    rod(rubber, (0.35, front - 0.07, table + 0.36), (0.35, front - 0.07, head + 0.06), 0.011, segments=6)
    rod(rubber, (0.35, front - 0.07, head + 0.06), (0.3, -0.36, head + 0.12), 0.011, segments=6)
    if loaded:
        lathe(steel, [(0.0, table + 0.075), (0.15, table + 0.075), (0.15, table + 0.17), (0.12, table + 0.19), (0.05, table + 0.19), (0.0, table + 0.19)], segments=24)
    model.hard(paint, "paint", bevel=0.01, segments=2)
    model.hard(steel, "steel", bevel=0.003, segments=1)
    model.plain(dark, "plastic")
    model.turned(rubber, "rubber")
    model.turned(red, "vinyl")
    model.plain(glass, "pane")
    return model


# How high the game says what is set on a drill press's table and a band saw's is: 32 and 30 pixels of their sprites.
DRILL_PRESS_TABLE, BAND_SAW_TABLE = 32 / 96 * 2.44949, 30 / 96 * 2.44949


def drill_press():
    """A drill press standing on the floor, its front at -Y: a base plate, a column, a table on an arm, the head with its
    belt guard, motor behind and switch in front, the quill, chuck and bit under it and three feed handles at its right."""
    model = Model("drill_press")
    paint, steel, dark, rubber, red = bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new()
    cy = 0.1
    cuboid(dark, -0.26, 0.26, -0.34, 0.26, 0.0, 0.05)
    lathe(dark, [(0.0, 0.05), (0.08, 0.05), (0.07, 0.09), (0.0, 0.09)], segments=16, cy=cy)
    lathe(steel, [(0.0, 0.09), (0.045, 0.09), (0.045, 1.42), (0.0, 1.42)], segments=16, cy=cy)
    top = DRILL_PRESS_TABLE
    for z in (top - 0.15, top - 0.07):
        lathe(dark, [(0.0, z), (0.065, z), (0.065, z + 0.05), (0.0, z + 0.05)], segments=16, cy=cy)
    cuboid(dark, -0.05, 0.05, -0.12, cy, top - 0.13, top - 0.09)
    cuboid(dark, -0.16, 0.16, -0.3, 0.02, top - 0.035, top)
    cuboid(paint, -0.14, 0.14, -0.22, 0.2, 1.36, 1.56)
    prism(paint, (0.0, -0.01, 0.26, 0.38, 1.56), (0.0, 0.0, 0.22, 0.32, 1.64))
    lathe(dark, [(0.0, 1.36), (0.085, 1.36), (0.085, 1.6), (0.0, 1.6)], segments=16, cy=0.29)
    cuboid(dark, -0.05, 0.05, -0.228, -0.22, 1.42, 1.5)
    dial(red, 0.0, -0.228, 1.465, 0.012, 0.016)
    lathe(steel, [(0.0, 1.2), (0.03, 1.2), (0.03, 1.36), (0.0, 1.36)], segments=14, cy=-0.12)
    lathe(steel, [(0.0, 1.12), (0.02, 1.13), (0.026, 1.2), (0.0, 1.2)], segments=12, cy=-0.12)
    lathe(steel, [(0.0, 1.03), (0.005, 1.04), (0.005, 1.12), (0.0, 1.12)], segments=6, cy=-0.12)
    lathe(steel, [(0.0, 0.0), (0.035, 0.0), (0.035, 0.05), (0.0, 0.05)], segments=12, to=along_x(0.14, -0.05, 1.42))
    for n in range(3):
        angle = 2 * math.pi * n / 3 - 0.4
        tip = (0.19, -0.05 + math.cos(angle) * 0.17, 1.42 + math.sin(angle) * 0.17)
        rod(steel, (0.19, -0.05, 1.42), tip, 0.007, segments=6)
        lathe(red, [(0.0, -0.02), (0.016, -0.012), (0.018, 0.0), (0.016, 0.012), (0.0, 0.02)], segments=10, to=Matrix.Translation(tip))
    model.hard(paint, "paint", bevel=0.012, segments=2)
    model.turned(steel, "steel")
    model.hard(dark, "plastic", bevel=0.004, segments=1)
    model.turned(rubber, "rubber")
    model.turned(red, "vinyl")
    return model


def band_saw():
    """A band saw standing on the floor, its front at -Y and its spine at the left: a base cabinet over the lower wheel, the
    spine up to the upper wheel's housing, the table through which the blade runs, its guide post, a fence, a switch
    panel on the spine and the tension wheel on top."""
    model = Model("band_saw")
    paint, steel, dark, rubber, red = bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new()
    top = BAND_SAW_TABLE
    base = top - 0.16
    cuboid(paint, -0.32, 0.32, -0.23, 0.23, 0.05, base)
    cuboid(rubber, -0.3, 0.3, -0.21, 0.21, 0.0, 0.05)
    cuboid(paint, -0.28, 0.28, -0.238, -0.23, 0.12, base - 0.06)
    louvres(dark, -0.2, 0.0, -0.238, 0.2, 0.42, 6)
    cuboid(paint, -0.32, -0.14, -0.14, 0.14, base, 1.36)
    outline = [(-0.32, 1.36), (0.28, 1.36), (0.28, 1.68), (0.2, 1.78), (-0.24, 1.78), (-0.32, 1.7)]
    housing = bmesh.new()
    outline_along_y(housing, outline, -0.14, 0.14)
    model.hard(housing, "paint", bevel=0.012, segments=2)
    cuboid(paint, -0.26, 0.24, -0.148, -0.14, 1.42, 1.72)
    cuboid(paint, -0.3, -0.16, -0.148, -0.14, 0.9, 1.3)
    cuboid(dark, -0.29, -0.17, -0.158, -0.148, 1.0, 1.2)
    dial(red, -0.23, -0.158, 1.15, 0.016, 0.018)
    dial(rubber, -0.23, -0.158, 1.06, 0.016, 0.014)
    lathe(steel, [(0.0, 0.0), (0.06, 0.0), (0.06, 0.015), (0.0, 0.015)], segments=16, to=Matrix.Translation((-0.05, 0.0, 1.78)))
    lathe(steel, [(0.0, 0.0), (0.008, 0.0), (0.008, 0.05), (0.0, 0.05)], segments=6, to=Matrix.Translation((-0.05, 0.04, 1.795)))
    cuboid(dark, 0.0, 0.2, -0.12, 0.12, base, top - 0.03)
    cuboid(steel, -0.12, 0.5, -0.3, 0.3, top - 0.03, top)
    for x in (0.12, 0.2):
        cuboid(dark, x - 0.004, x + 0.004, -0.3, 0.3, top - 0.003, top + 0.0001)
    cuboid(rubber, 0.42, 0.44, -0.29, 0.29, top, top + 0.04)
    cuboid(steel, 0.155, 0.185, -0.03, 0.0, top + 0.09, 1.36)
    cuboid(rubber, 0.14, 0.2, -0.06, 0.02, top + 0.05, top + 0.11)
    cuboid(steel, 0.166, 0.174, -0.001, 0.001, base, 1.36)
    model.hard(paint, "paint", bevel=0.012, segments=2)
    model.hard(steel, "steel", bevel=0.002, segments=1)
    model.plain(dark, "plastic")
    model.turned(rubber, "rubber")
    model.turned(red, "vinyl")
    return model


def line_press():
    """A press on a production line, a square of it: a grey steel cabinet with vents in its sides, the belt across it along
    the model's Y between painted rails, and over the belt four steel posts carrying a painted crosshead, the ribbed
    housing of the cylinder on it with grey guards at its sides, the ram and its die, and black hoses down its sides."""
    model = Model("line_press")
    paint, steel, dark, rubber, metal = bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new()
    belt(model, 0.36, -0.5, 0.5, BELT_TOP)
    cuboid(metal, -0.44, 0.44, -0.42, 0.42, 0.04, BELT_TOP - 0.11)
    cuboid(rubber, -0.41, 0.41, -0.39, 0.39, 0.0, 0.04)
    for side in (-1, 1):
        x0, x1 = sorted((side * 0.44, side * 0.446))
        for n in range(6):
            z = 0.2 + n * 0.06
            cuboid(dark, x0, x1, -0.22, 0.22, z, z + 0.025)
    for x in (-0.36, 0.36):
        for y in (-0.36, 0.36):
            cuboid(steel, x - 0.025, x + 0.025, y - 0.025, y + 0.025, BELT_TOP - 0.07, 1.12)
    for side in (-1, 1):
        x0, x1 = sorted((side * 0.46, side * 0.33))
        cuboid(paint, x0, x1, -0.46, 0.46, BELT_TOP - 0.13, BELT_TOP - 0.05)
    cuboid(paint, -0.42, 0.42, -0.42, 0.42, 1.06, 1.14)
    cuboid(paint, -0.22, 0.22, -0.2, 0.2, 1.14, 1.62)
    for z in (1.24, 1.36, 1.48):
        cuboid(paint, -0.235, 0.235, -0.215, 0.215, z, z + 0.04)
    for side in (-1, 1):
        x0, x1 = sorted((side * 0.22, side * 0.27))
        cuboid(metal, x0, x1, -0.16, 0.16, 1.2, 1.56)
    cuboid(metal, -0.2, 0.2, -0.18, 0.18, 1.62, 1.66)
    lathe(steel, [(0.0, 1.66), (0.04, 1.66), (0.04, 1.76), (0.0, 1.76)], segments=12)
    lathe(steel, [(0.0, 0.98), (0.2, 0.98), (0.2, 1.02), (0.06, 1.02), (0.06, 1.06), (0.0, 1.06)], segments=20)
    for x in (-0.27, 0.27):
        rod(steel, (x, 0.0, BELT_TOP + 0.02), (x, 0.0, 1.06), 0.016, segments=8)
    for side in (-1, 1):
        rod(rubber, (side * 0.27, 0.12, 1.5), (side * 0.44, 0.12, 1.4), 0.016, segments=8)
        rod(rubber, (side * 0.44, 0.12, 1.4), (side * 0.44, 0.12, BELT_TOP + 0.02), 0.016, segments=8)
    model.hard(paint, "paint", bevel=0.008, segments=2)
    model.hard(metal, "metal", bevel=0.008, segments=2)
    model.turned(steel, "steel")
    model.plain(dark, "plastic")
    model.turned(rubber, "rubber")
    return model


def bench_grinder():
    """A bench grinder, its front at -Y and its wheels on either end of the motor along X: the motor's round case on a
    foot, a grey wheel in a guard at each end, a tool rest in front of each wheel and a switch on the foot."""
    model = Model("bench_grinder")
    paint, steel, dark, metal, glass = bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new()
    axis = 0.11
    prism(paint, (0.0, 0.0, 0.2, 0.15, 0.0), (0.0, 0.0, 0.11, 0.09, 0.07))
    lathe(paint, [(0.0, -0.1), (0.06, -0.1), (0.072, -0.08), (0.075, 0.0), (0.072, 0.08), (0.06, 0.1), (0.0, 0.1)], segments=24, to=along_x(0.0, 0.0, axis))
    for side in (-1, 1):
        lathe(steel, [(0.0, 0.0), (0.012, 0.0), (0.012, 0.03), (0.0, 0.03)], segments=8,
              to=along_x(side * 0.1, 0.0, axis) @ Matrix.Rotation(math.radians(0 if side > 0 else 180), 4, "Y"))
        x0 = side * 0.13
        lathe(metal, [(0.0, -0.012), (0.068, -0.012), (0.068, 0.012), (0.0, 0.012)], segments=24, to=along_x(x0 + side * 0.012, 0.0, axis))
        guard = [(0.082 * math.cos(math.radians(a)), 0.082 * math.sin(math.radians(a))) for a in range(-40, 225, 15)]
        guard += [(0.074 * math.cos(math.radians(a)), 0.074 * math.sin(math.radians(a))) for a in range(210, -55, -15)]
        shell = bmesh.new()
        front = [shell.verts.new((x0 - side * 0.006, -yz[0], axis + yz[1])) for yz in guard]
        back = [shell.verts.new((x0 + side * 0.042, -yz[0], axis + yz[1])) for yz in guard]
        for i in range(len(guard)):
            j = (i + 1) % len(guard)
            shell.faces.new((front[i], front[j], back[j], back[i]))
        shell.faces.new(front[::-1])
        shell.faces.new(back)
        model.hard(shell, "plastic", bevel=0.003, segments=1)
        rest = (x0 + side * 0.012, -0.095, axis - 0.02)
        cuboid(steel, rest[0] - 0.025, rest[0] + 0.025, rest[1] - 0.02, rest[1] + 0.02, rest[2] - 0.006, rest[2])
        cuboid(steel, rest[0] - 0.006, rest[0] + 0.006, rest[1] + 0.02, -0.05, rest[2] - 0.03, rest[2] - 0.006)
        cuboid(glass, rest[0] - 0.04, rest[0] + 0.04, -0.085, -0.082, axis + 0.06, axis + 0.11)
    cuboid(dark, -0.03, 0.03, -0.076, -0.07, 0.02, 0.05)
    model.hard(paint, "paint", bevel=0.006, segments=2)
    model.hard(steel, "steel", bevel=0.002, segments=1)
    model.turned(metal, "metal")
    model.plain(dark, "rubber")
    model.plain(glass, "pane")
    return model


def industrial():
    """What a works, a warehouse, a garage and a distillery have: the pieces Industry.java places."""
    return [conveyor(), conveyor_boxed(), machine_case(), machine_shell(), machine_outlet(), machine_column(), machine_drum(),
            tank_length(), tank_end("tank_end", 1), tank_end("tank_end_mirrored", -1), downpipe(),
            cask("cask", False), cask("cask_lying", True), barrel_rack("barrel_rack", 0), barrel_rack("barrel_rack_low", 1),
            barrel_rack("barrel_rack_full", 2), workbench(), tool_chest(), security_desk(),
            gas_bottle("extinguisher_bottle", 0.09, 0.62), propane_tank(),
            engine_lathe(), lathe_headstock(), hydraulic_press("press", False), hydraulic_press("press_loaded", True), drill_press(), band_saw(), line_press(),
            bench_grinder()]


def jersey_barrier():
    """A concrete road barrier a square long, to meet the next: a broad foot that steps in, slopes up and rises to a narrow
    flat top, with a slot through its foot at each end for the forks that lift it."""
    model = Model("jersey_barrier")
    half_long, foot, high = 0.5, 0.19, 0.47
    profile = [(-foot, 0.0), (foot, 0.0), (foot, 0.06), (foot - 0.035, 0.1), (0.06, 0.29), (0.045, high), (-0.045, high), (-0.06, 0.29),
               (-foot + 0.035, 0.1), (-foot, 0.06)]
    concrete = bmesh.new()
    front = [concrete.verts.new((-half_long, y, z)) for y, z in profile]
    back = [concrete.verts.new((half_long, y, z)) for y, z in profile]
    for i in range(len(profile)):
        j = (i + 1) % len(profile)
        concrete.faces.new((front[i], back[i], back[j], front[j]))
    concrete.faces.new(front)
    concrete.faces.new(back[::-1])
    model.hard(concrete, "paint", bevel=0.008, segments=2)
    slots = bmesh.new()
    for end in (-1, 1):
        x = end * 0.3
        cuboid(slots, x - 0.07, x + 0.07, -foot - 0.002, foot + 0.002, 0.0, 0.035)
    model.plain(slots, "rubber")
    return model


def gravestone_battered():
    """An old headstone worn by the weather: a thick slab with a level head whose edges and corners are worn round and one
    corner broken off, on a plinth that has sunk a little."""
    model = Model("gravestone_battered")
    plinth = bmesh.new()
    cuboid(plinth, -0.285, 0.285, -0.15, 0.15, 0.0, 0.08)
    model.hard(plinth, "paint", bevel=0.02, segments=2)
    stone = bmesh.new()
    half, top = 0.22, 0.7
    outline = [(-half, 0.08), (half, 0.08), (half, top - 0.07), (half - 0.03, top - 0.02), (half - 0.09, top), (-half + 0.03, top),
               (-half, top - 0.03)]
    outline_along_y(stone, outline, -0.09, 0.09)
    model.hard(stone, "paint", bevel=0.018, segments=2)
    return model


def clipped(polygon, x0, x1):
    """The part of a polygon of (x, z) points between x = x0 and x = x1."""
    for edge, keep in ((x0, lambda x: x >= x0), (x1, lambda x: x <= x1)):
        kept = []
        for i, (ax, az) in enumerate(polygon):
            bx, bz = polygon[(i + 1) % len(polygon)]
            if keep(ax):
                kept.append((ax, az))
            if keep(ax) != keep(bx):
                t = (edge - ax) / (bx - ax)
                kept.append((edge, az + (bz - az) * t))
        polygon = kept
    return polygon


def road_barricade():
    """A road works barricade: two folding A frames of steel, two boards across the front frame striped on the slant in the
    bright paint and white, a plain board across the back, and a flashing lamp on top of each end. Its boards are to the front."""
    model = Model("road_barricade")
    half, top, spread = 0.46, 0.8, 0.27
    lean = spread / top
    frame = bmesh.new()
    for x in (-half, half):
        for way in (-1, 1):
            beam(frame, (x, way * spread, 0.0), (x, way * 0.02, top), 0.035, 0.035)
        cuboid(frame, x - 0.02, x + 0.02, -0.03, 0.03, top - 0.02, top + 0.03)
    model.hard(frame, "metal", bevel=0.004, segments=1)
    bright, white = bmesh.new(), bmesh.new()
    period, slant, length = 0.3, 0.5, 2 * half + 0.02
    for low, high in ((0.25, 0.39), (0.59, 0.74)):
        for n in range(-4, 6):
            for colour, shift in ((bright, 0.0), (white, period / 2)):
                x0 = -length / 2 + n * period + shift
                band = [(x0 - slant * (low - 0.25), low), (x0 + period / 2 - slant * (low - 0.25), low),
                        (x0 + period / 2 - slant * (high - 0.25), high), (x0 - slant * (high - 0.25), high)]
                part = clipped(band, -length / 2, length / 2)
                if len(part) < 3:
                    continue
                before = set(colour.verts)
                outline_along_y(colour, part, -0.012, 0.012)
                moved = [vert for vert in colour.verts if vert not in before]
                # Onto the slope of the front frame, just in front of its legs.
                for vert in moved:
                    vert.co.y += -spread + lean * vert.co.z - 0.03
    model.hard(bright, "paint", bevel=0.003, segments=1)
    model.hard(white, "enamel", bevel=0.003, segments=1)
    board = bmesh.new()
    cuboid(board, -length / 2, length / 2, -0.012, 0.012, 0.6, 0.74)
    bmesh.ops.transform(board, matrix=Matrix(((1, 0, 0, 0), (0, 1, -lean, spread + 0.03), (0, 0, 1, 0), (0, 0, 0, 1))), verts=board.verts)
    model.hard(board, "metal", bevel=0.003, segments=1)
    housings, lenses = bmesh.new(), bmesh.new()
    for x in (-half, half):
        cuboid(housings, x - 0.045, x + 0.045, -0.035, 0.035, top + 0.03, top + 0.05)
        lathe(lenses, [(0.0, -0.03), (0.06, -0.03), (0.06, 0.0), (0.05, 0.025), (0.0, 0.03)], segments=12, to=along_y(x, 0.0, top + 0.11))
    model.hard(housings, "rubber", bevel=0.004, segments=1)
    model.turned(lenses, "paint", sharp=50.0)
    return model


def flood_light():
    """A work light on a tripod: three splayed legs from a low hub, a telescopic yellow mast, and at its head a bar across with a
    caged floodlight at each end, their glass to the front. The origin is the mast's axis."""
    model = Model("flood_light", centred=False)
    hub, bar = 0.15, 2.05
    legs = bmesh.new()
    for k in range(3):
        angle = math.radians(90 + 120 * k)
        beam(legs, (0.0, 0.0, hub), (0.4 * math.cos(angle), 0.4 * math.sin(angle), 0.015), 0.025, 0.025)
    lathe(legs, [(0.0, hub - 0.06), (0.04, hub - 0.06), (0.04, hub + 0.1), (0.0, hub + 0.1)], segments=10)
    model.hard(legs, "paint", bevel=0.003, segments=1)
    mast = bmesh.new()
    lathe(mast, [(0.0, hub), (0.022, hub), (0.022, 1.02), (0.032, 1.02), (0.032, 1.08), (0.017, 1.08), (0.017, bar), (0.0, bar)], segments=10)
    cuboid(mast, -0.5, 0.5, -0.02, 0.02, bar - 0.02, bar + 0.02)
    model.turned(mast, "paint", sharp=40.0)
    cases, cages, glass = bmesh.new(), bmesh.new(), bmesh.new()
    yokes = bmesh.new()
    for x in (-0.4, 0.4):
        # A yoke holds each light by its sides: from across a street a thin stem under the case is not seen, and the light floats.
        cuboid(yokes, x - 0.02, x + 0.02, -0.02, 0.02, bar + 0.02, bar + 0.07)
        cuboid(yokes, x - 0.19, x + 0.19, -0.02, 0.02, bar + 0.05, bar + 0.08)
        for side in (-1, 1):
            cuboid(yokes, x + side * 0.165, x + side * 0.19, -0.02, 0.02, bar + 0.05, bar + 0.29)
        prism_y(cases, (x, bar + 0.27, 0.32, 0.32, -0.06), (x, bar + 0.27, 0.26, 0.26, 0.08))
        cuboid(glass, x - 0.14, x + 0.14, -0.064, -0.06, bar + 0.13, bar + 0.41)
        for n in range(4):
            gx = x - 0.12 + n * 0.08
            cuboid(cages, gx - 0.005, gx + 0.005, -0.09, -0.08, bar + 0.11, bar + 0.43)
        for z in (bar + 0.11, bar + 0.43):
            cuboid(cages, x - 0.16, x + 0.16, -0.09, -0.08, z - 0.005, z + 0.005)
    model.hard(cases, "plastic", bevel=0.006, segments=1)
    model.hard(yokes, "paint", bevel=0.003, segments=1)
    model.plain(cages, "metal")
    model.plain(glass, "shade")
    return model


def fuel_pump():
    """A petrol pump, a dispenser on each of its two faces: a white pillar at each end, between them a dark cabinet with its
    meter windows, its nozzles in their holsters and a coloured panel low down, and over the whole a coloured head. A hose
    hangs from the head at each end. The pump's long way is across its front."""
    model = Model("fuel_pump")
    half, deep, head = 0.36, 0.25, 1.98
    plinth = bmesh.new()
    cuboid(plinth, -half, half, -deep, deep, 0.0, 0.08)
    model.hard(plinth, "plastic", bevel=0.006, segments=1)
    pillars = bmesh.new()
    for side in (-1, 1):
        cuboid(pillars, side * half - side * 0.12, side * half, -deep + 0.01, deep - 0.01, 0.08, head)
    model.hard(pillars, "enamel", bevel=0.008, segments=1)
    cabinet = bmesh.new()
    cuboid(cabinet, -half + 0.12, half - 0.12, -deep + 0.05, deep - 0.05, 0.08, 1.55)
    prism(cabinet, (0.0, 0.0, 2 * half - 0.24, 2 * deep - 0.1, 1.55), (0.0, 0.0, 2 * half - 0.24, 2 * deep - 0.26, 1.68))
    model.hard(cabinet, "plastic", bevel=0.008, segments=1)
    panels, windows, holsters = bmesh.new(), bmesh.new(), bmesh.new()
    for face in (-1, 1):
        y = face * (deep - 0.05)
        cuboid(panels, -half + 0.13, half - 0.13, y - 0.006, y + 0.006, 0.1, 0.62)
        for x in (-0.1, 0.1):
            cuboid(windows, x - 0.07, x + 0.07, y - 0.006, y + 0.006, 1.3, 1.38)
            cuboid(holsters, x - 0.04, x + 0.04, y - face * 0.06, y, 0.95, 1.12)
    model.hard(panels, "paint", bevel=0.003, segments=1)
    model.plain(windows, "screen")
    model.hard(holsters, "metal", bevel=0.004, segments=1)
    cap = bmesh.new()
    cuboid(cap, -half - 0.01, half + 0.01, -deep - 0.01, deep + 0.01, head, head + 0.3)
    model.hard(cap, "vinyl", bevel=0.006, segments=1)
    lid = bmesh.new()
    cuboid(lid, -half - 0.015, half + 0.015, -deep - 0.015, deep + 0.015, head + 0.3, head + 0.33)
    model.hard(lid, "plastic", bevel=0.004, segments=1)
    hoses = bmesh.new()
    for side in (-1, 1):
        x = side * (half + 0.03)
        points = [(x, -0.12, head), (x, -0.14, 1.2), (x, -0.16, 0.45), (x - side * 0.03, -0.2, 0.2), (x - side * 0.12, -deep - 0.04, 0.22),
                  (x - side * 0.22, -deep - 0.03, 0.55), (side * 0.12, -deep - 0.06, 0.95)]
        for start, end in zip(points, points[1:]):
            beam(hoses, start, end, 0.025, 0.025)
    model.plain(hoses, "rubber")
    return model


def cement_mixer():
    """A cement mixer on a frame: the drum tilted with its mouth up and to the front, the frame on two wheels at one end and a
    leg at the other, the motor's box beside the drum at the wheel end and the tipping wheel at the other."""
    model = Model("cement_mixer")
    frame = bmesh.new()
    rail = 0.42
    for y in (-0.16, 0.16):
        beam(frame, (-0.4, y, rail), (0.36, y, rail), 0.035, 0.035)
        beam(frame, (0.36, y, rail), (0.42, y * 0.6, 0.0), 0.03, 0.03)
        beam(frame, (-0.4, y, rail), (-0.4, y, 0.12), 0.03, 0.03)
    beam(frame, (-0.4, -0.27, 0.12), (-0.4, 0.27, 0.12), 0.03, 0.03)
    beam(frame, (0.0, 0.0, rail), (0.0, 0.0, 0.62), 0.05, 0.05)
    beam(frame, (0.3, 0.0, rail), (0.3, 0.0, 0.9), 0.03, 0.03)
    model.hard(frame, "metal", bevel=0.004, segments=1)
    wheels = bmesh.new()
    for y in (-0.3, 0.3):
        lathe(wheels, [(0.0, -0.03), (0.12, -0.03), (0.12, 0.03), (0.0, 0.03)], segments=14, to=along_y(-0.4, y, 0.12))
    model.turned(wheels, "rubber", sharp=40.0)
    drum = bmesh.new()
    tilted = Matrix.Translation((0.0, -0.04, 0.82)) @ Matrix.Rotation(math.radians(55), 4, "X") @ Matrix.Rotation(math.radians(180), 4, "X")
    lathe(drum, [(0.15, -0.36), (0.17, -0.33), (0.26, -0.24), (0.32, -0.1), (0.33, 0.03), (0.29, 0.17), (0.18, 0.3), (0.0, 0.34)], segments=20,
          caps=False, to=tilted)
    model.turned(drum, "paint", sharp=50.0)
    # What is in the drum, just inside its mouth, so the inside of the shell is never seen.
    inside = bmesh.new()
    lathe(inside, [(0.0, -0.345), (0.155, -0.345), (0.155, -0.335), (0.0, -0.335)], segments=20, to=tilted)
    model.turned(inside, "dark_wood", sharp=50.0)
    motor = bmesh.new()
    cuboid(motor, -0.44, -0.3, -0.12, 0.12, 0.66, 0.92)
    model.hard(motor, "plastic", bevel=0.01, segments=1)
    tipper = bmesh.new()
    lathe(tipper, [(0.16, -0.012), (0.18, -0.012), (0.18, 0.012), (0.16, 0.012)], segments=16, closed=True, to=along_x(0.36, 0.0, 0.9))
    for k in range(3):
        angle = math.radians(120 * k)
        beam(tipper, (0.36, 0.0, 0.9), (0.36, 0.17 * math.cos(angle), 0.9 + 0.17 * math.sin(angle)), 0.015, 0.015)
    model.hard(tipper, "metal", bevel=0.003, segments=1)
    return model


def sand_pile(name, radius, peak, seed):
    """A heap of sand tipped on the ground: a cone whose sides sag towards a spread foot and whose point is rounded off, its
    surface uneven."""
    model = Model(name)
    random.seed(seed)
    rings, segments = 8, 28
    heap = bmesh.new()
    rows = []
    for ring in range(rings + 1):
        out = 1.0 - ring / rings
        z = peak * (1.0 - out ** 1.25) ** 1.2
        if ring == rings:
            rows.append([heap.verts.new((0.0, 0.0, peak))])
            continue
        rows.append([heap.verts.new(((radius * out * random.uniform(0.94, 1.06)) * math.cos(2 * math.pi * k / segments),
                                     (radius * out * random.uniform(0.94, 1.06)) * math.sin(2 * math.pi * k / segments),
                                     z * (random.uniform(0.95, 1.05) if 0 < ring else 0.0))) for k in range(segments)])
    for a, b in zip(rows, rows[1:]):
        for k in range(segments):
            j = (k + 1) % segments
            heap.faces.new((a[k], a[j], b[0]) if len(b) == 1 else (a[k], a[j], b[j], b[k]))
    heap.faces.new(rows[0][::-1])
    model.turned(heap, "paint", sharp=70.0)
    return model


def warning_sign():
    """A diamond warning sign on its post: a plate as wide as the sprite paints it and a little less high, that shows the
    sprite's own painting of it on both its faces, bolted to the front of a square steel post. The origin is the post's axis."""
    model = Model("warning_sign", centred=False)
    post_high, middle, half_wide, half_high = 2.1, 1.82, 0.49, 0.4
    shaft = bmesh.new()
    cuboid(shaft, -0.025, 0.025, -0.025, 0.025, 0.0, post_high)
    model.hard(shaft, "metal", bevel=0.004, segments=1)
    plate = bmesh.new()
    outline_along_y(plate, [(-half_wide, middle), (0.0, middle - half_high), (half_wide, middle), (0.0, middle + half_high)], WARNING_PLATE_Y - 0.004,
                    WARNING_PLATE_Y + 0.004)
    model.plain(plate, "pane")
    return model


# Where the face of a warning sign's plate is, along the model's y: in front of its post. Outdoors.WARNING_PLATE_Y is the same.
WARNING_PLATE_Y = -0.035


def burnt_post(name, half, high, broken, seed):
    """A fence's post the fire has been at: square timber, charred, its top split and broken off unevenly where broken is set.
    The origin is the post's axis."""
    model = Model(name, centred=False)
    random.seed(seed)
    wood = bmesh.new()
    cuboid(wood, -half, half, -half, half, 0.0, high)
    tops = [vert for vert in wood.verts if vert.co.z > high / 2]
    for vert in tops:
        vert.co.z -= random.uniform(0.0, 0.25 if broken else 0.03)
    model.hard(wood, "dark_wood", bevel=0.006, segments=1)
    return model


def pole(name, radius, high, cap):
    """A steel pole of a fence, round, on a small flange, with a ball on top where cap is set. The origin is its axis."""
    model = Model(name, centred=False)
    steel = bmesh.new()
    profile = [(0.0, 0.0), (radius * 1.8, 0.0), (radius * 1.8, 0.01), (radius, 0.02), (radius, high)]
    if cap:
        profile += [(radius * 1.6, high + 0.01), (radius * 1.7, high + 0.035), (radius * 1.2, high + 0.055)]
    lathe(steel, profile + [(0.0, profile[-1][1])], segments=10)
    model.turned(steel, "paint", sharp=40.0)
    return model


def street_clutter():
    """What stands along streets and lots: road barriers and barricades, work lights, petrol pumps, a cement mixer, sand
    tipped on the ground, and an old headstone."""
    return [jersey_barrier(), gravestone_battered(), road_barricade(), flood_light(), fuel_pump(), cement_mixer(),
            sand_pile("sand_pile", 1.0, 0.59, 3), sand_pile("sand_pile_large", 1.05, 0.68, 5), warning_sign(),
            burnt_post("burnt_picket_post", 0.04, 0.96, False, 1), burnt_post("burnt_rail_post", 0.055, 1.35, True, 2),
            burnt_post("burnt_tall_post", 0.08, 2.33, False, 3), pole("fence_pole", 0.022, 2.17, False), pole("fence_pole_capped", 0.022, 1.25, True),
            pole("fence_pole_wide", 0.05, 2.19, False)]


def china_cabinet():
    """A china cabinet two squares wide: a cupboard with drawers down one side, and over it a narrower case with two
    glazed doors and shelves, under a cornice with an arched crest at its front."""
    model = Model("china_cabinet")
    half, front, back = 0.52, -0.23, 0.23
    upper, upper_front = 0.47, -0.15

    body = bmesh.new()
    cuboid(body, -half, half, front + 0.03, back, 0.0, 0.08)
    cuboid(body, -half, half, front, back, 0.08, 0.83)
    cuboid(body, -half - 0.02, half + 0.02, front - 0.025, back, 0.83, 0.87)
    cuboid(body, -upper, upper, upper_front, back, 0.87, 1.76)
    cuboid(body, -upper - 0.02, upper + 0.02, upper_front - 0.025, back, 1.76, 1.80)
    cuboid(body, -upper - 0.045, upper + 0.045, upper_front - 0.05, back, 1.80, CHINA_TOP)
    model.hard(body, "dark_wood", bevel=0.006, segments=1)

    crest = bmesh.new()
    arch = [(x, CHINA_TOP + 0.10 * math.cos(x / upper * math.pi / 2) ** 2) for x in spread(-upper, upper, 12)]
    outline_along_y(crest, [(-upper, CHINA_TOP - 0.02)] + arch + [(upper, CHINA_TOP - 0.02)], upper_front - 0.05, upper_front + 0.01)
    model.hard(crest, "dark_wood", bevel=0.004, segments=1)

    fronts, pulls, frame, panel = bmesh.new(), bmesh.new(), bmesh.new(), bmesh.new()
    face = front - 0.018
    drawer_fronts(fronts, pulls, -half + 0.01, -0.12, 0.09, 0.82, 3, face, size=1.0)
    framed_door(frame, panel, -0.115, half - 0.01, 0.095, 0.815, face - 0.004, rail=0.07)
    knob(pulls, -0.07, face - 0.004, 0.62)
    glass = bmesh.new()
    shelves = bmesh.new()
    glazed = upper_front - 0.02
    for side in (-1, 1):
        low, high = sorted((side * 0.004, side * (upper - 0.012)))
        cuboid(frame, low, low + 0.045, glazed, glazed + 0.02, 0.89, 1.75)
        cuboid(frame, high - 0.045, high, glazed, glazed + 0.02, 0.89, 1.75)
        cuboid(frame, low + 0.045, high - 0.045, glazed, glazed + 0.02, 0.89, 0.95)
        cuboid(frame, low + 0.045, high - 0.045, glazed, glazed + 0.02, 1.69, 1.75)
        cuboid(glass, low + 0.04, high - 0.04, glazed + 0.008, glazed + 0.012, 0.945, 1.695)
        knob(pulls, side * 0.035, glazed, 1.30, 0.9)
    for z in (1.18, 1.44):
        cuboid(shelves, -upper + 0.02, upper - 0.02, glazed + 0.04, back - 0.02, z, z + 0.018)
    model.hard(fronts, "dark_wood", bevel=0.004, segments=1)
    model.hard(frame, "dark_wood", bevel=0.004, segments=1)
    model.hard(panel, "dark_wood", bevel=0.003, segments=1)
    model.hard(shelves, "dark_wood", bevel=0.003, segments=1)
    model.plain(glass, "screen")
    model.turned(pulls, "metal")
    return model


WORKTOP_HIGH = 0.90
WORKTOP_THICK = 0.035


def worktop(name, sink=False, corner=False):
    """The board of a counter top the game lays over a counter, which has a colour of its own: a slab over the counter
    model's own top, built where that top is in the counter's model, so that it stands where the counter stands."""
    model = Model(name, centred=False)
    counter = cabinet("counter_corner" if corner else "counter_sink" if sink else "counter", 0.62, WORKTOP_HIGH, True, corner=corner, sink=sink)
    xs = [position.x for position, _, _ in counter.vertices]
    ys = [position.y for position, _, _ in counter.vertices]
    # The straight counter's model is stood by the middle of its bounds; its corner keeps the origin it was built round.
    cx, cy = (0.0, 0.0) if corner else ((min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2)
    deep, clear = 0.62, 0.003
    back = 0.455 if corner else deep / 2
    front = back - deep - clear
    side = -0.455
    low, high = 0.0, WORKTOP_THICK
    tops = bmesh.new()
    if sink:
        x0, x1, y0, y1 = -0.27, 0.27, front + clear + 0.12, back - 0.14
        cuboid(tops, -0.5 - clear - cx, x0 - cx, front - cy, back + clear - cy, low, high)
        cuboid(tops, x1 - cx, 0.5 + clear - cx, front - cy, back + clear - cy, low, high)
        cuboid(tops, x0 - cx, x1 - cx, front - cy, y0 - 0.016 - cy, low, high)
        cuboid(tops, x0 - cx, x1 - cx, y1 + 0.016 - cy, back + clear - cy, low, high)
    else:
        cuboid(tops, (side - clear if corner else -0.5 - clear) - cx, 0.5 + clear - cx, front - cy, back + clear - cy, low, high)
        if corner:
            cuboid(tops, side - clear - cx, side + deep + clear - cx, -0.5 - cy, front - cy, low, high)
    model.hard(tops, "worktop", bevel=0.004, segments=2)
    return model


def low_glass_table():
    """A low table two squares long with round ends: a glass top in a wooden rim, a shelf under it and a post at each corner."""
    model = Model("low_glass_table")
    half, deep, top = 0.66, 0.36, 0.38
    straight = half - deep

    def stadium(inset, z0, z1):
        ring = [(straight + (deep - inset) * math.cos(math.radians(a)), (deep - inset) * math.sin(math.radians(a))) for a in range(-90, 91, 15)]
        ring += [(-straight + (deep - inset) * math.cos(math.radians(a)), (deep - inset) * math.sin(math.radians(a))) for a in range(90, 271, 15)]
        bm = bmesh.new()
        lows = [bm.verts.new((x, y, z0)) for x, y in ring]
        highs = [bm.verts.new((x, y, z1)) for x, y in ring]
        bm.faces.new(lows[::-1])
        bm.faces.new(highs)
        for i in range(len(ring)):
            j = (i + 1) % len(ring)
            bm.faces.new((lows[i], lows[j], highs[j], highs[i]))
        return bm

    model.hard(stadium(0.0, top - 0.035, top), "dark_wood", bevel=0.008, segments=2)
    model.hard(stadium(0.03, top - 0.12, top - 0.035), "dark_wood", bevel=0.004, segments=1)
    model.hard(stadium(0.04, 0.06, 0.09), "dark_wood", bevel=0.005, segments=1)
    glass = stadium(0.045, top, top + 0.004)
    model.plain(glass, "screen")
    posts = bmesh.new()
    for x in (-1, 1):
        for y in (-1, 1):
            lathe(posts, [(0.0, 0.0), (0.018, 0.0), (0.016, 0.02), (0.016, top - 0.12), (0.0, top - 0.12)],
                  cx=x * (straight + (deep - 0.05) * 0.7), cy=y * (deep - 0.05) * 0.7, segments=10)
    model.turned(posts, "dark_wood")
    return model


def sabre_table():
    """A low table two squares long whose four legs sweep out to the floor, under a deep apron."""
    model = Model("sabre_table")
    half, deep, top = 0.62, 0.32, 0.36
    board = bmesh.new()
    cuboid(board, -half, half, -deep, deep, top - 0.035, top)
    model.hard(board, "dark_wood", bevel=0.008, segments=2)
    apron = bmesh.new()
    cuboid(apron, -half + 0.05, half - 0.05, -deep + 0.05, deep - 0.05, top - 0.12, top - 0.035)
    model.hard(apron, "dark_wood", bevel=0.004, segments=1)
    legs = bmesh.new()
    for x in (-1, 1):
        for y in (-1, 1):
            cx, cy = x * (half - 0.09), y * (deep - 0.08)
            # Each leg is a square bar bent outwards to its foot, cut in short straight lengths.
            steps = 6
            ring = None
            for n in range(steps + 1):
                t = n / steps
                z = (top - 0.12) * (1 - t)
                out = 0.09 * t * t
                size = 0.034 - 0.012 * t
                px, py = cx + x * out * 0.7, cy + y * out * 0.7
                new = [legs.verts.new((px + dx * size / 2, py + dy * size / 2, z)) for dx, dy in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
                if ring is None:
                    legs.faces.new(new)
                else:
                    for i in range(4):
                        j = (i + 1) % 4
                        legs.faces.new((ring[i], ring[j], new[j], new[i]))
                ring = new
            legs.faces.new(ring[::-1])
    model.hard(legs, "dark_wood", bevel=0.003, segments=1)
    return model


def coffee_maker():
    """A filter coffee maker: a column at the back with the water in it, the head over the jug, and the jug on a plate."""
    model = Model("coffee_maker")
    body = bmesh.new()
    cuboid(body, -0.08, 0.08, 0.02, 0.12, 0.0, 0.34)
    cuboid(body, -0.08, 0.08, -0.12, 0.02, 0.0, 0.03)
    cuboid(body, -0.08, 0.08, -0.12, 0.02, 0.24, 0.34)
    model.hard(body, "plastic", bevel=0.01, segments=2)
    jug = bmesh.new()
    lathe(jug, [(0.0, 0.035), (0.055, 0.035), (0.068, 0.08), (0.06, 0.16), (0.05, 0.19), (0.052, 0.21), (0.0, 0.21)], cy=-0.055, segments=16)
    model.turned(jug, "screen")
    plate = bmesh.new()
    lathe(plate, [(0.0, 0.03), (0.065, 0.03), (0.065, 0.036), (0.0, 0.036)], cy=-0.055, segments=16)
    model.turned(plate, "metal")
    return model


def toaster():
    model = Model("toaster")
    body = bmesh.new()
    cuboid(body, -0.13, 0.13, -0.08, 0.08, 0.012, 0.19)
    body.faces.ensure_lookup_table()
    model.hard(body, "steel", bevel=0.035, segments=3)
    slots = bmesh.new()
    for y in (-0.03, 0.03):
        cuboid(slots, -0.095, 0.095, y - 0.012, y + 0.012, 0.188, 0.192)
    model.plain(slots, "rubber")
    foot = bmesh.new()
    cuboid(foot, -0.125, 0.125, -0.075, 0.075, 0.0, 0.014)
    cuboid(foot, 0.10, 0.118, -0.095, -0.079, 0.08, 0.15)
    model.hard(foot, "plastic", bevel=0.003, segments=1)
    return model


def foot_locker():
    """A small steel chest: a lid with a rim over a box with a handle at each end and a hasp at its front."""
    model = Model("foot_locker")
    half, front, back, top = 0.38, -0.25, 0.25, 0.46
    box = bmesh.new()
    cuboid(box, -half, half, front, back, 0.0, top - 0.11)
    model.hard(box, "paint", bevel=0.01, segments=2)
    lid = bmesh.new()
    cuboid(lid, -half - 0.01, half + 0.01, front - 0.01, back + 0.01, top - 0.11, top - 0.09)
    cuboid(lid, -half, half, front, back, top - 0.09, top)
    model.hard(lid, "paint", bevel=0.012, segments=2)
    fittings = bmesh.new()
    for x in (-1, 1):
        cuboid(fittings, x * (half + 0.006) - 0.006, x * (half + 0.006) + 0.006, -0.08, 0.08, 0.2, 0.22)
    for x in (-0.22, 0.22):
        cuboid(fittings, x - 0.03, x + 0.03, front - 0.012, front, top - 0.16, top - 0.07)
    model.hard(fittings, "metal", bevel=0.002, segments=1)
    return model


def steel_toilet(name, left):
    """A cell's toilet and basin in one: a steel column with a basin in its top at one end, and the bowl along the wall at the other."""
    model = Model(name)
    way = -1 if left else 1
    front, back = -0.34, 0.12
    column = bmesh.new()
    cuboid(column, -0.18, 0.18, front + 0.04, back, 0.0, 0.88)
    model.hard(column, "steel", bevel=0.05, segments=3)
    basin = bmesh.new()
    lathe(basin, [(0.0, 0.84), (0.10, 0.84), (0.135, 0.885), (0.14, 0.89), (0.0, 0.89)], cy=(front + back) / 2 - 0.02, segments=16)
    model.turned(basin, "rubber")
    bowl = bmesh.new()
    cx = way * 0.42
    cuboid(bowl, cx - 0.24, cx + 0.24, -0.05, back, 0.0, 0.40)
    model.hard(bowl, "steel", bevel=0.03, segments=2)
    seat = bmesh.new()
    lathe(seat, [(0.0, 0.40), (0.19, 0.40), (0.2, 0.41), (0.15, 0.42), (0.11, 0.405), (0.0, 0.40)], cx=cx, cy=-0.12, segments=20, sy=1.0, sx=1.15)
    lathe(seat, [(0.13, 0.0), (0.16, 0.30), (0.19, 0.40), (0.0, 0.40)], cx=cx, cy=-0.12, segments=20, sx=1.15)
    model.turned(seat, "steel", sharp=50.0)
    hole = bmesh.new()
    lathe(hole, [(0.0, 0.415), (0.12, 0.415), (0.12, 0.421), (0.0, 0.421)], cx=cx, cy=-0.12, segments=16, sx=1.15, sy=0.85)
    model.plain(hole, "rubber")
    tap = bmesh.new()
    lathe(tap, [(0.012, 0.88), (0.012, 0.93), (0.0, 0.935)], cy=back - 0.04, segments=8)
    for x in (-0.1, 0.1):
        cuboid(tap, x - 0.02, x + 0.02, back - 0.05, back - 0.01, 0.88, 0.905)
    model.hard(tap, "steel", bevel=0.003, segments=1)
    return model


def wall_shelving(name, corner):
    """A tall unit of open shelves with a panelled back, against a wall: or two such meeting in a corner."""
    model = Model(name, centred=not corner)
    high, deep = SHELVING_TOP, 0.42
    frame, back, boards = bmesh.new(), bmesh.new(), bmesh.new()
    if not corner:
        x0, x1, y1 = -0.48, 0.48, 0.21
        y0 = y1 - deep
        cuboid(back, x0, x1, y1 - 0.02, y1, 0.0, high)
        for x in (x0, x1 - 0.035):
            cuboid(frame, x, x + 0.035, y1 - 0.12, y1 - 0.02, 0.0, high)
        cuboid(frame, x0, x1, y1 - 0.06, y1 - 0.02, high - 0.08, high)
        for z in (0.0, 0.52, 1.02, 1.52):
            cuboid(boards, x0 + 0.035, x1 - 0.035, y0, y1 - 0.02, z + (0.06 if z == 0.0 else 0.0), z + 0.08 if z == 0.0 else z + 0.035)
        cuboid(boards, x0 + 0.035, x1 - 0.035, y0 + 0.02, y1 - 0.02, 0.0, 0.06)
    else:
        # Built on its square, its two backs along the square's far edges, off them as a straight one is.
        b = 0.455
        cuboid(back, -0.5, b, b - 0.02, b, 0.0, high)
        cuboid(back, b - 0.02, b, -0.5, b - 0.02, 0.0, high)
        cuboid(frame, -0.5, -0.465, b - 0.12, b - 0.02, 0.0, high)
        cuboid(frame, b - 0.12, b - 0.02, -0.5, -0.465, 0.0, high)
        cuboid(frame, b - 0.06, b - 0.02, -0.5, b - 0.02, high - 0.08, high)
        cuboid(frame, -0.5, b - 0.06, b - 0.06, b - 0.02, high - 0.08, high)
        for z in (0.0, 0.52, 1.02, 1.52):
            z0, z1 = (0.0, 0.08) if z == 0.0 else (z, z + 0.035)
            cuboid(boards, -0.465, b - 0.02, b - deep, b - 0.02, z0, z1)
            cuboid(boards, b - deep, b - 0.02, -0.465, b - deep, z0, z1)
    if corner:
        # Its second back is on the side to its left as it is looked at, as a corner counter's is.
        for bm in (frame, back, boards):
            bmesh.ops.scale(bm, vec=(-1.0, 1.0, 1.0), verts=bm.verts[:])
    model.hard(frame, "dark_wood", bevel=0.004, segments=1)
    model.hard(back, "wood", bevel=0.003, segments=1)
    model.hard(boards, "dark_wood", bevel=0.005, segments=1)
    return model


def double_oven():
    """A kitchen's stack of two steel ovens on legs, two squares wide: a door with a window and a bar on each oven, a
    panel of keys beside each door."""
    model = Model("double_oven")
    half, front, back, top = 0.92, -0.36, 0.36, DOUBLE_OVEN_TOP
    body = bmesh.new()
    cuboid(body, -half, half, front, back, 0.12, top - 0.04)
    cuboid(body, -half - 0.01, half + 0.01, front - 0.015, back, top - 0.04, top)
    cuboid(body, -half + 0.01, half - 0.01, front + 0.005, back - 0.01, 0.10, 0.12)
    model.hard(body, "steel", bevel=0.008, segments=2)
    legs = bmesh.new()
    for x in (-1, 1):
        for y in (front + 0.04, back - 0.04):
            cuboid(legs, x * (half - 0.04) - 0.025, x * (half - 0.04) + 0.025, y - 0.025, y + 0.025, 0.0, 0.11)
    model.hard(legs, "rubber", bevel=0.004, segments=1)
    doors, glass, keys = bmesh.new(), bmesh.new(), bmesh.new()
    deck = (top - 0.04 - 0.12) / 2
    for n in range(2):
        z0 = 0.12 + n * deck + 0.03
        z1 = z0 + deck - 0.06
        cuboid(doors, -half + 0.36, half - 0.04, front - 0.03, front, z0, z1)
        cuboid(glass, -half + 0.48, half - 0.16, front - 0.034, front - 0.03, z0 + 0.1, z1 - 0.08)
        bar_handle(doors, -half + 0.46, half - 0.14, front - 0.03, z1 - 0.06, z1 - 0.04, proud=0.04, thick=0.018)
        cuboid(keys, -half + 0.06, -half + 0.30, front - 0.008, front, z0 + 0.12, z1 - 0.06)
    model.hard(doors, "steel", bevel=0.006, segments=2)
    model.plain(glass, "screen")
    model.hard(keys, "plastic", bevel=0.003, segments=1)
    return model


def fireplace():
    """An open fire let into a chimney breast, as it stands out of its wall: a sooty firebox under a lintel, its iron
    grate, and the stone hearth on the floor before it."""
    model = Model("fireplace")
    half, front, back = 0.62, -0.15, 0.15
    hearth = bmesh.new()
    cuboid(hearth, -half - 0.08, half + 0.08, front - 0.25, back, 0.0, 0.05)
    model.hard(hearth, "worktop", bevel=0.008, segments=2)
    box = bmesh.new()
    for x0, x1 in ((-half, -half + 0.12), (half - 0.12, half)):
        cuboid(box, x0, x1, front, back, 0.05, 0.78)
    cuboid(box, -half, half, front, back, 0.78, 0.92)
    cuboid(box, -half + 0.12, half - 0.12, back - 0.06, back, 0.05, 0.78)
    model.hard(box, "rubber", bevel=0.006, segments=1)
    grate = bmesh.new()
    for x in spread(-0.28, 0.28, 7):
        cuboid(grate, x - 0.008, x + 0.008, front + 0.05, back - 0.08, 0.08, 0.2)
    for y in (front + 0.05, back - 0.09):
        cuboid(grate, -0.3, 0.3, y, y + 0.016, 0.08, 0.2)
    for x in (-0.26, 0.26):
        cuboid(grate, x - 0.015, x + 0.015, front + 0.05, front + 0.08, 0.05, 0.08)
    model.hard(grate, "metal", bevel=0.002, segments=1)
    return model


def utility_sink():
    """A deep steel sink on four legs with a slatted shelf under it, and a spray arm on a sprung hose over it."""
    model = Model("utility_sink")
    half, front, back, rim = 0.42, -0.30, 0.30, UTILITY_SINK_RIM
    tub = bmesh.new()
    cuboid(tub, -half, half, front, back, rim - 0.32, rim)
    tub.faces.ensure_lookup_table()
    bmesh.ops.delete(tub, geom=[face for face in tub.faces if face.normal.z > 0.5], context="FACES")
    model.hard(tub, "steel", bevel=0.01, segments=2)
    inside = bmesh.new()
    cuboid(inside, -half + 0.02, half - 0.02, front + 0.02, back - 0.02, rim - 0.30, rim + 0.002)
    inside.faces.ensure_lookup_table()
    bmesh.ops.delete(inside, geom=[face for face in inside.faces if face.normal.z > 0.5], context="FACES")
    model.hard(inside, "steel", bevel=0.01, segments=2, inward=True)
    lip = bmesh.new()
    for x0, x1, y0, y1 in ((-half, half, front, front + 0.02), (-half, half, back - 0.02, back), (-half, -half + 0.02, front, back), (half - 0.02, half, front, back)):
        cuboid(lip, x0, x1, y0, y1, rim - 0.002, rim + 0.01)
    model.hard(lip, "steel", bevel=0.003, segments=1)
    stand = bmesh.new()
    for x in (-1, 1):
        for y in (front + 0.02, back - 0.04):
            cuboid(stand, x * (half - 0.03) - 0.02, x * (half - 0.03) + 0.02, y, y + 0.02 + 0.0, 0.0, rim - 0.32)
    for x in spread(-half + 0.06, half - 0.06, 8):
        cuboid(stand, x - 0.008, x + 0.008, front + 0.04, back - 0.04, 0.16, 0.175)
    cuboid(stand, -half + 0.02, half - 0.02, front + 0.03, front + 0.05, 0.155, 0.18)
    cuboid(stand, -half + 0.02, half - 0.02, back - 0.06, back - 0.04, 0.155, 0.18)
    model.hard(stand, "steel", bevel=0.003, segments=1)
    arm = bmesh.new()
    lathe(arm, [(0.02, rim + 0.01), (0.02, rim + 0.05), (0.014, rim + 0.06), (0.014, rim + 0.55), (0.0, rim + 0.56)], cy=back - 0.06, segments=10)
    for n in range(10):
        z = rim + 0.6 + 0.012 * n
        lathe(arm, [(0.0, z), (0.045, z), (0.045, z + 0.008), (0.0, z + 0.008)], cy=back - 0.06, segments=10)
    lathe(arm, [(0.01, 0.0), (0.01, 0.22), (0.0, 0.225)], segments=8, to=along_y(0.0, back - 0.06, rim + 0.48) @ Matrix.Rotation(math.radians(180), 4, "X"))
    model.turned(arm, "metal")
    return model


def ottoman():
    model = Model("ottoman")
    half, deep, seat = 0.30, 0.30, OTTOMAN_SEAT
    pad = bmesh.new()
    cage = grid_box(pad, spread(-half, half, 3), spread(-deep, deep, 3), [0.08, seat - 0.12, seat])
    crease(pad, cage, 0.6)
    model.soft(pad, "cloth", levels=2)
    feet = bmesh.new()
    for x in (-1, 1):
        for y in (-1, 1):
            lathe(feet, [(0.0, 0.0), (0.016, 0.0), (0.022, 0.09), (0.0, 0.09)], cx=x * (half - 0.05), cy=y * (deep - 0.05), segments=8)
    model.turned(feet, "dark_wood")
    return model


def deep_fryer():
    """A double deep fryer on four legs: two wells in its top, a splash back behind them, a bank of switches in front and
    two drain taps under it."""
    model = Model("deep_fryer")
    half, front, back, top = 0.40, -0.34, 0.34, 0.90
    body = bmesh.new()
    cuboid(body, -half, half, front, back, 0.32, top - 0.31)
    for x0, x1, y0, y1 in ((-half, half, front, front + 0.17), (-half, half, back - 0.09, back), (-half, -0.34, front, back), (0.34, half, front, back),
                           (-0.04, 0.04, front, back)):
        cuboid(body, x0, x1, y0, y1, top - 0.31, top - 0.02)
    cuboid(body, -half, half, back - 0.05, back, top - 0.02, top + 0.25)
    model.hard(body, "steel", bevel=0.008, segments=2)
    # Two wells let into the top, each a steel box open at its top with the oil standing in it.
    wells, oil, rim = bmesh.new(), bmesh.new(), bmesh.new()
    w0, w1 = front + 0.17, back - 0.09
    for x in (-0.19, 0.19):
        cuboid(wells, x - 0.147, x + 0.147, w0 + 0.003, w1 - 0.003, top - 0.3, top + 0.002)
        cuboid(oil, x - 0.145, x + 0.145, w0 + 0.005, w1 - 0.005, top - 0.12, top - 0.11)
    wells.faces.ensure_lookup_table()
    bmesh.ops.delete(wells, geom=[face for face in wells.faces if face.normal.z > 0.5], context="FACES")
    model.hard(wells, "steel", bevel=0.006, segments=1, inward=True)
    model.plain(oil, "rubber")
    for x0, x1, y0, y1 in ((-half, half, front, w0), (-half, half, w1, back - 0.05), (-half, -0.34, w0, w1), (-0.04, 0.04, w0, w1), (0.34, half, w0, w1)):
        cuboid(rim, x0, x1, y0, y1, top - 0.02, top)
    cuboid(rim, -half, half, front - 0.012, front, top - 0.14, top)
    model.hard(rim, "steel", bevel=0.004, segments=1)
    panel = bmesh.new()
    for x in (-0.24, -0.16, 0.16, 0.24):
        lathe(panel, [(0.0, 0.0), (0.018, 0.0), (0.016, 0.014), (0.0, 0.016)], segments=10, to=along_y(x, front - 0.012, top - 0.07))
    model.turned(panel, "plastic")
    legs = bmesh.new()
    for x in (-1, 1):
        for y in (front + 0.03, back - 0.03):
            cuboid(legs, x * (half - 0.03) - 0.02, x * (half - 0.03) + 0.02, y - 0.02, y + 0.02, 0.0, 0.32)
    for x in (-0.19, 0.19):
        cuboid(legs, x - 0.012, x + 0.012, front + 0.04, front + 0.2, 0.26, 0.32)
        cuboid(legs, x - 0.012, x + 0.012, front + 0.02, front + 0.06, 0.2, 0.27)
    model.hard(legs, "steel", bevel=0.004, segments=1)
    return model


def freezer_chest():
    """A home's chest freezer: an enamel box with a lid on a seal, a handle at the lid's front edge."""
    model = Model("freezer_chest")
    half, front, back, top = 0.40, -0.33, 0.33, FREEZER_TOP
    body = bmesh.new()
    cuboid(body, -half, half, front, back, 0.02, top - 0.11)
    model.hard(body, "enamel", bevel=0.015, segments=2)
    seal = bmesh.new()
    cuboid(seal, -half + 0.005, half - 0.005, front + 0.005, back - 0.005, top - 0.11, top - 0.1)
    cuboid(seal, -half + 0.01, half - 0.01, front + 0.01, back - 0.01, 0.0, 0.02)
    model.hard(seal, "rubber", bevel=0.003, segments=1)
    lid = bmesh.new()
    cuboid(lid, -half - 0.008, half + 0.008, front - 0.012, back, top - 0.1, top)
    model.hard(lid, "enamel", bevel=0.02, segments=2)
    handle = bmesh.new()
    bar_handle(handle, -0.14, 0.14, front - 0.012, top - 0.085, top - 0.07, proud=0.03, thick=0.015)
    model.hard(handle, "plastic", bevel=0.004, segments=1)
    return model


def trailer_table():
    """A trailer's table two squares long: a round-cornered top on one steel column with a foot."""
    model = Model("trailer_table")
    half, deep, top = 0.72, 0.36, TRAILER_TABLE_TOP
    board = bmesh.new()
    cuboid(board, -half, half, -deep, deep, top - 0.035, top)
    model.hard(board, "worktop", bevel=0.03, segments=3)
    edge = bmesh.new()
    cuboid(edge, -half + 0.02, half - 0.02, -deep + 0.02, deep - 0.02, top - 0.05, top - 0.034)
    model.hard(edge, "metal", bevel=0.003, segments=1)
    column = bmesh.new()
    lathe(column, [(0.0, 0.0), (0.16, 0.0), (0.16, 0.012), (0.04, 0.03), (0.032, 0.05), (0.032, top - 0.07), (0.07, top - 0.05), (0.0, top - 0.05)], segments=16)
    model.turned(column, "metal")
    return model


def wood_stove():
    """A cast iron stove on cabriole feet: a fire door with a grille, two plates on its top, and its flue going up at the back."""
    model = Model("wood_stove")
    half, front, back, top = 0.36, -0.24, 0.24, WOOD_STOVE_TOP
    body = bmesh.new()
    cuboid(body, -half, half, front, back, 0.16, top - 0.04)
    cuboid(body, -half - 0.025, half + 0.025, front - 0.025, back + 0.025, top - 0.04, top)
    cuboid(body, -half - 0.015, half + 0.015, front - 0.015, back + 0.015, 0.14, 0.18)
    model.hard(body, "paint", bevel=0.01, segments=2)
    feet = bmesh.new()
    for x in (-1, 1):
        for y in (-1, 1):
            prism(feet, (x * (half - 0.02), y * (back - 0.02), 0.05, 0.05, 0.0), (x * (half - 0.05), y * (back - 0.05), 0.06, 0.06, 0.15))
    model.hard(feet, "paint", bevel=0.008, segments=1)
    door = bmesh.new()
    cuboid(door, -0.22, 0.2, front - 0.02, front, 0.24, top - 0.12)
    model.hard(door, "paint", bevel=0.006, segments=1)
    grille = bmesh.new()
    for x in spread(-0.15, 0.1, 6):
        cuboid(grille, x - 0.01, x + 0.01, front - 0.024, front - 0.019, 0.3, top - 0.18)
    knob(grille, 0.16, front - 0.02, (0.24 + top - 0.12) / 2, 1.0)
    model.hard(grille, "rubber", bevel=0.002, segments=1)
    plates = bmesh.new()
    for x in (-0.17, 0.17):
        lathe(plates, [(0.0, top), (0.12, top), (0.12, top + 0.008), (0.0, top + 0.008)], cx=x, cy=-0.04, segments=16)
    model.turned(plates, "metal")
    flue = bmesh.new()
    lathe(flue, [(0.0, top), (0.075, top), (0.075, top + 0.04), (0.06, top + 0.05), (0.06, FLUE_TOP), (0.0, FLUE_TOP)], cy=back - 0.06, segments=14)
    model.turned(flue, "paint")
    return model


def rocking_chair(name, made):
    """A bentwood rocking chair: a caned seat and back on curved frames that run down to two rockers."""
    model = Model(name)
    seat, half = ROCKER_SEAT, 0.25
    frame = bmesh.new()
    for x in (-half, half):
        # The rocker, an arc along the chair under each side.
        rocker = [(x, 0.4 * math.sin(math.radians(a)), 0.03 + 0.55 * (1 - math.cos(math.radians(a)))) for a in range(-50, 51, 10)]
        bar(frame, rocker, 0.022)
        bar(frame, [(x, -0.22, 0.12), (x, -0.22, seat), (x, -0.25, seat + 0.2), (x, -0.08, seat + 0.24), (x, 0.12, seat + 0.2)], 0.018)
        bar(frame, [(x, 0.24, 0.14), (x, 0.2, seat), (x, 0.28, seat + 0.35), (x * 0.9, 0.34, seat + 0.62)], 0.018)
    bar(frame, [(-half * 0.9, 0.34, seat + 0.62), (-0.1, 0.36, seat + 0.67), (0.1, 0.36, seat + 0.67), (half * 0.9, 0.34, seat + 0.62)], 0.018)
    for y, z in ((-0.22, seat), (0.2, seat), (-0.22, 0.12), (0.22, 0.12)):
        bar(frame, [(-half, y, z), (half, y, z)], 0.014)
    model.hard(frame, made, bevel=0.004, segments=1)
    cane = bmesh.new()
    cuboid(cane, -half + 0.02, half - 0.02, -0.21, 0.19, seat - 0.008, seat + 0.008)
    prism(cane, (0.0, 0.22, 2 * half - 0.07, 0.015, seat + 0.15), (0.0, 0.31, 2 * half - 0.09, 0.015, seat + 0.58))
    model.hard(cane, "linen", bevel=0.003, segments=1)
    return model


def bar(bm, points, thick):
    """A square bar of that thickness through the points, its lengths mitred at each point between."""
    rings = []
    for i, (x, y, z) in enumerate(points):
        a = Vector(points[max(i - 1, 0)])
        b = Vector(points[min(i + 1, len(points) - 1)])
        way = (b - a).normalized()
        side = way.cross(Vector((0.0, 0.0, 1.0)))
        if side.length < 1e-4:
            side = way.cross(Vector((1.0, 0.0, 0.0)))
        side.normalize()
        up = side.cross(way).normalized()
        centre = Vector((x, y, z))
        rings.append([bm.verts.new(centre + (side * dx + up * dy) * thick / 2) for dx, dy in ((-1, -1), (1, -1), (1, 1), (-1, 1))])
    bm.faces.new(rings[0][::-1])
    bm.faces.new(rings[-1])
    for r0, r1 in zip(rings, rings[1:]):
        for i in range(4):
            j = (i + 1) % 4
            bm.faces.new((r0[i], r0[j], r1[j], r1[i]))


def espresso_machine():
    """A cafe's espresso machine: a steel case with a cup tray on its top, two group heads with handles and a drip tray."""
    model = Model("espresso_machine")
    half, front, back, top = 0.36, -0.26, 0.26, 0.42
    body = bmesh.new()
    cuboid(body, -half, half, front + 0.08, back, 0.0, top)
    cuboid(body, -half, half, front, front + 0.08, 0.0, 0.05)
    cuboid(body, -half, half, front, front + 0.08, top - 0.1, top)
    model.hard(body, "steel", bevel=0.01, segments=2)
    side = bmesh.new()
    cuboid(side, -half - 0.01, half + 0.01, front + 0.08, back + 0.01, top - 0.004, top + 0.012)
    model.hard(side, "enamel", bevel=0.004, segments=1)
    tray = bmesh.new()
    cuboid(tray, -half + 0.03, half - 0.03, front + 0.005, front + 0.08, 0.05, 0.06)
    for x in spread(-half + 0.05, half - 0.05, 10):
        cuboid(tray, x - 0.004, x + 0.004, front + 0.01, front + 0.075, 0.06, 0.066)
    model.hard(tray, "metal", bevel=0.002, segments=1)
    heads = bmesh.new()
    for x in (-0.15, 0.15):
        lathe(heads, [(0.0, top - 0.1), (0.04, top - 0.1), (0.04, top - 0.16), (0.0, top - 0.16)], cx=x, cy=front + 0.06, segments=12)
        cuboid(heads, x - 0.012, x + 0.012, front - 0.1, front + 0.04, top - 0.16, top - 0.14)
    lathe(heads, [(0.0, 0.0), (0.09, 0.0), (0.085, 0.07), (0.06, 0.09), (0.0, 0.09)], cx=0.0, cy=0.06, segments=16,
          to=Matrix.Translation((0.0, 0.0, top + 0.012)))
    model.turned(heads, "metal")
    return model


def hood_dishwasher():
    """A kitchen's pass-through dishwasher: a steel tank on legs with a rack in it, and a hood over it on a handle bar."""
    model = Model("hood_dishwasher")
    half, front, back = 0.34, -0.34, 0.34
    tank = bmesh.new()
    cuboid(tank, -half, half, front, back, 0.12, 0.86)
    model.hard(tank, "steel", bevel=0.008, segments=2)
    rack = bmesh.new()
    for x in spread(-half + 0.05, half - 0.05, 9):
        cuboid(rack, x - 0.006, x + 0.006, front + 0.03, back - 0.03, 0.86, 0.875)
    model.hard(rack, "metal", bevel=0.002, segments=1)
    hood = bmesh.new()
    cuboid(hood, -half - 0.01, half + 0.01, front - 0.01, back + 0.01, 1.2, 1.62)
    cuboid(hood, -0.03, 0.03, back - 0.04, back, 0.86, 1.2)
    model.hard(hood, "steel", bevel=0.01, segments=2)
    handle = bmesh.new()
    bar(handle, [(-half - 0.03, 0.1, 1.28), (-half - 0.03, front - 0.04, 1.28), (half + 0.03, front - 0.04, 1.28), (half + 0.03, 0.1, 1.28)], 0.022)
    model.hard(handle, "metal", bevel=0.004, segments=1)
    legs = bmesh.new()
    for x in (-1, 1):
        for y in (front + 0.04, back - 0.04):
            cuboid(legs, x * (half - 0.04) - 0.02, x * (half - 0.04) + 0.02, y - 0.02, y + 0.02, 0.0, 0.12)
    model.hard(legs, "steel", bevel=0.004, segments=1)
    return model


def outhouse_seat():
    """The seat of an outhouse: a box of boards with a round hole in its lid."""
    model = Model("outhouse_seat")
    half, front, back, top = 0.48, -0.28, 0.28, OUTHOUSE_SEAT
    box = bmesh.new()
    cuboid(box, -half, half, front, back, 0.0, top - 0.03)
    model.hard(box, "dark_wood", bevel=0.004, segments=1)
    lid = bmesh.new()
    for x0, x1, y0, y1 in ((-half - 0.01, half + 0.01, front - 0.02, -0.15), (-half - 0.01, half + 0.01, 0.07, back),
                           (-half - 0.01, -0.13, -0.15, 0.07), (0.13, half + 0.01, -0.15, 0.07)):
        cuboid(lid, x0, x1, y0, y1, top - 0.03, top)
    model.hard(lid, "dark_wood", bevel=0.004, segments=1)
    hole = bmesh.new()
    lathe(hole, [(0.0, top - 0.035), (0.13, top - 0.035), (0.13, top - 0.03), (0.0, top - 0.03)], cy=-0.04, segments=16, sy=0.85)
    model.plain(hole, "rubber")
    return model


CHINA_TOP = 1.88
SHELVING_TOP = 64 / 96 * 2.45
DOUBLE_OVEN_TOP = 57 / 96 * 2.45
UTILITY_SINK_RIM = 35 / 96 * 2.45
OTTOMAN_SEAT = 17 / 96 * 2.45
FREEZER_TOP = 34 / 96 * 2.45
TRAILER_TABLE_TOP = 27 / 96 * 2.45
WOOD_STOVE_TOP = 34 / 96 * 2.45
FLUE_TOP = 1.75
ROCKER_SEAT = 0.40
OUTHOUSE_SEAT = 22 / 96 * 2.45


def home_fittings():
    """Fittings of homes, kitchens and bathrooms that HomeFittings.java places."""
    return [china_cabinet(), worktop("worktop"), worktop("worktop_sink", sink=True), worktop("worktop_corner", corner=True),
            low_glass_table(), sabre_table(), coffee_maker(), toaster(), foot_locker(), steel_toilet("steel_toilet", False),
            steel_toilet("steel_toilet_left", True), wall_shelving("wall_shelving", False), wall_shelving("wall_shelving_corner", True),
            double_oven(), fireplace(), utility_sink(), ottoman(), deep_fryer(), freezer_chest(), trailer_table(), wood_stove(),
            rocking_chair("rocking_chair", "dark_wood"), rocking_chair("rocking_chair_black", "rubber"), espresso_machine(), hood_dishwasher(), outhouse_seat()]


# A building's steel frame: a girder's flanges and its depth, and a column's.
STEEL_BEAM_HALF, STEEL_BEAM_HIGH, STEEL_FLANGE, STEEL_WEB = 0.1, 0.245, 0.02, 0.012
STEEL_COLUMN_HALF = 0.1175


def steel_beam():
    """A square's length of I girder of a building's frame, along y: a metre long, to meet the next. The mod hangs it under a ceiling or lays it on a floor."""
    model = Model("steel_beam")
    steel = bmesh.new()
    cuboid(steel, -STEEL_BEAM_HALF, STEEL_BEAM_HALF, -0.5, 0.5, 0.0, STEEL_FLANGE)
    cuboid(steel, -STEEL_BEAM_HALF, STEEL_BEAM_HALF, -0.5, 0.5, STEEL_BEAM_HIGH - STEEL_FLANGE, STEEL_BEAM_HIGH)
    cuboid(steel, -STEEL_WEB / 2, STEEL_WEB / 2, -0.5, 0.5, STEEL_FLANGE, STEEL_BEAM_HIGH - STEEL_FLANGE)
    model.plain(steel, "paint")
    return model


def steel_column():
    """An H column of a building's frame, a storey high, on a base plate."""
    model = Model("steel_column")
    steel = bmesh.new()
    half, flange, web, high = STEEL_COLUMN_HALF, 0.02, 0.014, 2.45
    cuboid(steel, -half, half, -half, -half + flange, 0.0, high)
    cuboid(steel, -half, half, half - flange, half, 0.0, high)
    cuboid(steel, -web / 2, web / 2, -half + flange, half - flange, 0.0, high)
    model.plain(steel, "paint")
    plate = bmesh.new()
    cuboid(plate, -half - 0.03, half + 0.03, -half - 0.03, half + 0.03, 0.0, 0.016)
    model.plain(plate, "metal")
    return model


def church_column(name, plinth):
    """A round plastered column a storey high, on a square plinth where plinth is (half its width, its height), or none where the column goes on from the storey under it."""
    model = Model(name)
    radius, high = 0.2875, 2.45
    if plinth is not None:
        half, up = plinth
        block = bmesh.new()
        cuboid(block, -half, half, -half, half, 0.0, up)
        model.hard(block, "paint", bevel=0.012, segments=2)
        foot = bmesh.new()
        lathe(foot, [(0.0, up), (radius + 0.04, up), (radius + 0.04, up + 0.04), (radius + 0.015, up + 0.08), (0.0, up + 0.08)], segments=28)
        model.turned(foot, "paint", sharp=50.0)
    shaft = bmesh.new()
    lathe(shaft, [(radius, 0.0), (radius, high)], segments=28)
    model.turned(shaft, "paint")
    return model


# A sheet steel duct that climbs a wall: half its width, and how far apart the seams between its lengths are.
DUCT_UP_HALF, DUCT_UP_SEAM_EVERY = 0.19, 0.6


def duct_up(name, high):
    """A square duct standing on the floor and climbing a wall, high metres, with a seam round it between each length and the next."""
    model = Model(name)
    body = bmesh.new()
    cuboid(body, -DUCT_UP_HALF, DUCT_UP_HALF, -DUCT_UP_HALF, DUCT_UP_HALF, 0.0, high)
    model.plain(body, "paint")
    seams = bmesh.new()
    out = DUCT_UP_HALF + DUCT_SEAM
    z = DUCT_UP_SEAM_EVERY
    while z < high - 0.1:
        cuboid(seams, -out, out, -out, out, z - DUCT_BAND / 2, z + DUCT_BAND / 2)
        z += DUCT_UP_SEAM_EVERY
    model.plain(seams, "paint")
    return model


# The wide pipe on a works' floor, as culvert_pipe has it: its outer and inner radius, and how high its middle is.
CULVERT_OUT, CULVERT_IN = 0.14, 0.118


def culvert_length(bm, start, end, along):
    """A length of the wide pipe from start to end, in metres from the square's middle, along "x" or "y", open at both ends."""
    profile = [(CULVERT_IN, start), (CULVERT_OUT, start), (CULVERT_OUT, end), (CULVERT_IN, end)]
    if along == "y":
        # along_y turns a height h to y = -h.
        profile = [(r, -h) for r, h in profile]
        lathe(bm, profile, segments=18, closed=True, to=along_y(0.0, 0.0, CULVERT_OUT))
    else:
        lathe(bm, profile, segments=18, closed=True, to=along_x(0.0, 0.0, CULVERT_OUT))


def culvert_fitting(name, lengths):
    """The wide pipe turning, branching or stopping in the middle of its square: lengths of it from there to the edges, given as
    (start, end, along), round a ball that closes the joint. The origin is the middle of the square, so each end is at its edge."""
    model = Model(name, centred=False)
    pipe = bmesh.new()
    for start, end, along in lengths:
        culvert_length(pipe, start, end, along)
    model.turned(pipe, "paint", sharp=50.0)
    ball = bmesh.new()
    lathe(ball, [(CULVERT_OUT * math.sin(math.pi * i / 10), CULVERT_OUT - CULVERT_OUT * math.cos(math.pi * i / 10)) for i in range(11)], segments=18)
    model.turned(ball, "paint", sharp=50.0)
    return model


# A timber of a frame built of sawn wood, as the sprites of one paint it: how wide it is and how deep.
TIMBER_WIDE, TIMBER_DEEP = 0.2, 0.12


def timber_corner():
    """Two timbers of a frame along two edges of a square, each its whole length, meeting at a corner: the north and west edges as the
    mod turns it first, which in the model are its front and its +x side. The origin is the middle of the square."""
    model = Model("timber_corner", centred=False)
    wood = bmesh.new()
    cuboid(wood, -0.5, 0.5, -0.5, -0.5 + TIMBER_WIDE, 0.0, TIMBER_DEEP)
    cuboid(wood, 0.5 - TIMBER_WIDE, 0.5, -0.5 + TIMBER_WIDE, 0.5, 0.0, TIMBER_DEEP)
    model.hard(wood, "wood", bevel=0.006, segments=1)
    return model


def timber_block():
    """A short block of the same timber standing on end."""
    model = Model("timber_block")
    wood = bmesh.new()
    cuboid(wood, -0.095, 0.095, -0.095, 0.095, 0.0, 0.15)
    model.hard(wood, "wood", bevel=0.006, segments=1)
    return model


# A frame of scaffolding as the sprites of one paint it: the tube's radius, the standards' distance from the middle of the frame along it,
# and how high its three ledgers are.
SCAFFOLD_TUBE, SCAFFOLD_STANDARD, SCAFFOLD_LEDGERS = 0.024, 0.455, (0.34, 2.11, 2.42)


def scaffold_tube(bm, start, end):
    """A length of scaffold tube from start to end."""
    start, end = Vector(start), Vector(end)
    way = end - start
    turned = Vector((0.0, 0.0, 1.0)).rotation_difference(way.normalized()).to_matrix().to_4x4()
    lathe(bm, [(SCAFFOLD_TUBE, 0.0), (SCAFFOLD_TUBE, way.length)], segments=10, to=Matrix.Translation(start) @ turned)


def scaffold(name, standards, braced):
    """A square's frame of scaffolding along y: three ledgers its whole length, a standard a storey high at each end in standards (-1
    the front, 1 the back), and where braced a brace that zigzags from the foot of the back standard to the middle of the front end and
    up to the back standard's head again. The frames of the squares along a run meet end to end."""
    model = Model(name)
    tubes = bmesh.new()
    for end in standards:
        scaffold_tube(tubes, (0.0, end * SCAFFOLD_STANDARD, 0.0), (0.0, end * SCAFFOLD_STANDARD, 2.45))
    for z in SCAFFOLD_LEDGERS:
        scaffold_tube(tubes, (0.0, -0.5, z), (0.0, 0.5, z))
    if braced:
        low, high = SCAFFOLD_LEDGERS[0], SCAFFOLD_LEDGERS[1]
        scaffold_tube(tubes, (0.0, SCAFFOLD_STANDARD, low), (0.0, -0.5, (low + high) / 2))
        scaffold_tube(tubes, (0.0, -0.5, (low + high) / 2), (0.0, SCAFFOLD_STANDARD, high))
    model.turned(tubes, "metal", sharp=40.0)
    return model


def overhead():
    """What runs along a building's frame or hangs under its ceiling: steel girders and columns, ducts, a works' wide pipe, a church's columns."""
    front, side, back, other = (-0.5, 0.0, "y"), (0.0, 0.5, "x"), (0.0, 0.5, "y"), (-0.5, 0.0, "x")
    return [steel_beam(), steel_column(), church_column("church_column", None), church_column("church_column_base", (0.274, 0.61)),
            duct_up("duct_up", 2.45), duct_up("duct_up_short", 0.93 * 2.45),
            culvert_fitting("culvert_elbow", [front, side]), culvert_fitting("culvert_tee", [front, side, other]),
            culvert_fitting("culvert_cross", [front, side, back, other]), culvert_fitting("culvert_end", [front]),
            timber_corner(), timber_block(), scaffold("scaffold_frame", (-1, 1), False), scaffold("scaffold_braced", (1,), True)]


def castor(wheels, forks, x, y, radius=0.04, wide=0.025):
    """A castor: a wheel turned about x under a fork from a stem at (x, y). The fork's top is at twice the radius and a little."""
    lathe(wheels, [(0.0, -wide / 2), (radius * 0.7, -wide / 2), (radius, -wide / 4), (radius, wide / 4), (radius * 0.7, wide / 2), (0.0, wide / 2)],
          segments=12, to=along_x(x, y, radius))
    cuboid(forks, x - wide / 2 - 0.008, x + wide / 2 + 0.008, y - 0.012, y + 0.012, radius * 1.6, radius * 2 + 0.02)


def hospital_bed():
    """A ward's bed two squares long, its head at +Y: a deck on a low frame on castors, a mattress under a sheet, a
    pillow, a board at the head and at the foot, and two rails along each side. Its mattress is 0.62 high."""
    model = Model("hospital_bed")
    hx, head, foot = 0.44, 1.0, -1.0
    wheels, frame = bmesh.new(), bmesh.new()
    for x in (-0.36, 0.36):
        for y in (-0.86, 0.86):
            castor(wheels, frame, x, y, radius=0.05)
        cuboid(frame, x - 0.03, x + 0.03, -0.9, 0.9, 0.12, 0.17)
    for y in (-0.86, 0.86):
        cuboid(frame, -0.39, 0.39, y - 0.03, y + 0.03, 0.12, 0.17)
    model.turned(wheels, "rubber")
    model.hard(frame, "plastic", bevel=0.006, segments=1)
    lift = bmesh.new()
    for y in (-0.55, 0.55):
        cuboid(lift, -0.08, 0.08, y - 0.06, y + 0.06, 0.17, 0.4)
    cuboid(lift, -0.41, 0.41, foot + 0.04, head - 0.04, 0.4, 0.44)
    model.hard(lift, "steel", bevel=0.006, segments=1)
    mattress = bmesh.new()
    cuboid(mattress, -hx + 0.02, hx - 0.02, foot + 0.05, head - 0.05, 0.44, 0.62)
    model.hard(mattress, "cloth", bevel=0.035, segments=2)
    pillow = bmesh.new()
    cage = grid_box(pillow, spread(-0.3, 0.3, 2), spread(-0.18, 0.18, 2), [-0.06, 0.0, 0.06])
    for (i, j, k), vert in cage.items():
        if k != 1:
            vert.co.x *= 0.75
            vert.co.y *= 0.7
        vert.co.y += head - 0.32
        vert.co.z += 0.67
    model.soft(pillow, "pillow", levels=2)
    boards = bmesh.new()
    for y0, y1, top in ((head - 0.05, head, 0.98), (foot, foot + 0.05, 0.84)):
        for x in (-hx, hx - 0.06):
            cuboid(boards, x, x + 0.06, y0, y1, 0.36, top)
        cuboid(boards, -hx + 0.06, hx - 0.06, y0, y1, top - 0.08, top)
        cuboid(boards, -hx + 0.06, hx - 0.06, y0 + 0.01, y1 - 0.01, 0.36, top - 0.2)
    model.hard(boards, "paint", bevel=0.012, segments=2)
    rails = bmesh.new()
    for side in (-1, 1):
        x = side * (hx + 0.02)
        for y0, y1 in ((0.2, 0.85), (-0.85, -0.3)):
            cuboid(rails, x - 0.012, x + 0.012, y0, y1, 0.84, 0.865)
            cuboid(rails, x - 0.012, x + 0.012, y0 + 0.04, y1 - 0.04, 0.66, 0.68)
            for n in range(4):
                y = y0 + 0.03 + n * (y1 - y0 - 0.06) / 3
                cuboid(rails, x - 0.01, x + 0.01, y - 0.01, y + 0.01, 0.6 if n in (0, 3) else 0.66, 0.84)
    model.hard(rails, "steel", bevel=0.004, segments=1)
    return model


def mayo_stand():
    """A tray table on castors: a steel tray, longer along x, on a column at its -X end, over a base of two runners along y
    and a bar between them. Its tray is 0.92 high."""
    model = Model("mayo_stand")
    wheels, steel = bmesh.new(), bmesh.new()
    for x in (-0.26, 0.24):
        cuboid(steel, x - 0.025, x + 0.025, -0.24, 0.24, 0.07, 0.1)
        for y in (-0.21, 0.21):
            castor(wheels, steel, x, y, radius=0.03, wide=0.018)
    cuboid(steel, -0.24, 0.22, -0.02, 0.02, 0.075, 0.095)
    cuboid(steel, -0.28, -0.24, -0.02, 0.02, 0.1, 0.89)
    cuboid(steel, -0.28, 0.0, -0.02, 0.02, 0.86, 0.89)
    model.turned(wheels, "rubber")
    model.hard(steel, "steel", bevel=0.005, segments=1)
    tray = bmesh.new()
    hx, hy, top = 0.32, 0.22, 0.92
    cuboid(tray, -hx, hx, -hy, hy, top - 0.03, top - 0.025)
    cuboid(tray, -hx, hx, -hy, -hy + 0.012, top - 0.03, top)
    cuboid(tray, -hx, hx, hy - 0.012, hy, top - 0.03, top)
    cuboid(tray, -hx, -hx + 0.012, -hy + 0.012, hy - 0.012, top - 0.03, top)
    cuboid(tray, hx - 0.012, hx, -hy + 0.012, hy - 0.012, top - 0.03, top)
    model.hard(tray, "steel", bevel=0.003, segments=1)
    return model


def medical_stool():
    """A round padded stool on a gas column over five legs on castors, with a ring for the feet. Its seat is 0.56 high."""
    model = Model("medical_stool")
    seat = bmesh.new()
    lathe(seat, [(0.0, 0.48), (0.17, 0.48), (0.19, 0.5), (0.195, 0.53), (0.185, 0.553), (0.16, 0.562), (0.0, 0.565)], segments=24)
    model.turned(seat, "vinyl", sharp=60.0)
    steel = bmesh.new()
    lathe(steel, [(0.0, 0.11), (0.04, 0.11), (0.04, 0.3), (0.024, 0.31), (0.024, 0.48), (0.0, 0.48)], segments=14)
    lathe(steel, [(0.19 + 0.011 * math.cos(2 * math.pi * i / 8), 0.3 + 0.011 * math.sin(2 * math.pi * i / 8)) for i in range(8)], segments=24, closed=True)
    for n in range(3):
        angle = math.radians(90 + n * 120)
        beam(steel, (0.0, 0.0, 0.3), (0.19 * math.cos(angle), 0.19 * math.sin(angle), 0.3), 0.012, 0.012)
    model.turned(steel, "steel", sharp=40.0)
    legs, wheels = bmesh.new(), bmesh.new()
    for n in range(5):
        angle = math.radians(90 + n * 72)
        x, y = math.cos(angle), math.sin(angle)
        beam(legs, (0.0, 0.0, 0.11), (x * 0.25, y * 0.25, 0.085), 0.03, 0.025)
        castor(wheels, legs, x * 0.25, y * 0.25, radius=0.025, wide=0.016)
    model.hard(legs, "steel", bevel=0.004, segments=1)
    model.turned(wheels, "rubber")
    return model


def rotary_phone():
    """A desk telephone with a dial: a body sloping down to the front, two forked cradle posts on its top, and the
    handset lying across them, its handle arched between the earpiece and mouthpiece cups, which rest face down on the
    top. The dial is on the slope. Its body is the colour of the sprite's."""
    model = Model("rotary_phone")
    body = bmesh.new()
    prism(body, (0.0, 0.0, 0.2, 0.23, 0.0), (0.0, 0.03, 0.16, 0.14, 0.085))
    middle, rest = 0.06, 0.135
    for side in (-1, 1):
        x0, x1 = sorted((side * 0.034, side * 0.05))
        cuboid(body, x0, x1, middle - 0.03, middle + 0.03, 0.08, rest)
        for y0, y1 in ((middle - 0.03, middle - 0.021), (middle + 0.021, middle + 0.03)):
            cuboid(body, x0, x1, y0, y1, rest - 0.005, rest + 0.014)
    model.hard(body, "plastic", bevel=0.012, segments=2)
    handle = bmesh.new()
    under = [(-0.092, rest - 0.022), (-0.068, rest - 0.004), (-0.046, rest), (0.046, rest), (0.068, rest - 0.004), (0.092, rest - 0.022)]
    outline_along_y(handle, under + [(x, z + 0.026) for x, z in reversed(under)], middle - 0.019, middle + 0.019)
    model.hard(handle, "plastic", bevel=0.007, segments=2)
    cups = bmesh.new()
    face = 0.09
    for x in (-0.09, 0.09):
        lathe(cups, [(0.0, face + 0.004), (0.028, face + 0.004), (0.033, face), (0.037, face + 0.002), (0.038, face + 0.011),
                     (0.036, face + 0.024), (0.03, face + 0.036), (0.02, face + 0.045), (0.009, face + 0.049), (0.0, face + 0.05)],
              cx=x, cy=middle, segments=24)
    model.turned(cups, "plastic", sharp=80.0)
    dial = bmesh.new()
    tilt = Matrix.Translation((0.0, -0.066, 0.058)) @ Matrix.Rotation(math.radians(48), 4, "X")
    lathe(dial, [(0.0, 0.0), (0.05, 0.0), (0.05, 0.006), (0.018, 0.008), (0.0, 0.008)], segments=16, to=tilt)
    model.turned(dial, "metal", sharp=50.0)
    return model


def desk_phone():
    """An office telephone of the eighties: a low wedge with the handset across its back and the keys in the slope before it."""
    model = Model("desk_phone")
    body = bmesh.new()
    prism(body, (0.0, 0.0, 0.24, 0.2, 0.0), (0.0, 0.02, 0.23, 0.16, 0.035))
    cuboid(body, -0.115, 0.115, 0.04, 0.1, 0.035, 0.055)
    model.hard(body, "plastic", bevel=0.006, segments=2)
    handset = bmesh.new()
    cuboid(handset, -0.11, 0.11, 0.045, 0.095, 0.055, 0.075)
    for side in (-1, 1):
        cuboid(handset, side * 0.1 - 0.025, side * 0.1 + 0.025, 0.04, 0.1, 0.05, 0.085)
    model.hard(handset, "plastic", bevel=0.009, segments=2)
    keys = bmesh.new()
    for row in range(4):
        for column in range(3):
            x, y = -0.035 + column * 0.03, -0.065 + row * 0.022
            cuboid(keys, x - 0.01, x + 0.01, y - 0.008, y + 0.008, 0.0, 0.045 - 0.004 * (3 - row))
    model.plain(keys, "linen")
    return model


def exam_table():
    """A doctor's couch two squares long, its head at +Y: a wooden cabinet of drawers under a padded top whose head
    end is raised, with a strip of paper down it. The top is 0.8 high."""
    model = Model("exam_table")
    cabinet = bmesh.new()
    cuboid(cabinet, -0.3, 0.3, -0.72, 0.66, 0.0, 0.72)
    model.hard(cabinet, "wood", bevel=0.006, segments=2)
    fronts, pulls = bmesh.new(), bmesh.new()
    drawer_fronts(fronts, pulls, -0.28, 0.28, 0.04, 0.7, 3, -0.738)
    for side in (-1, 1):
        x = side * 0.3
        for n in range(3):
            z = 0.06 + n * 0.22
            cuboid(fronts, min(x, x + side * 0.008), max(x, x + side * 0.008), -0.6, 0.55, z, z + 0.18)
    model.hard(fronts, "wood", bevel=0.004)
    model.turned(pulls, "metal")
    pad = bmesh.new()
    cuboid(pad, -0.36, 0.36, -0.92, 0.28, 0.72, 0.8)
    back = Matrix.Translation((0.0, 0.28, 0.72)) @ Matrix.Rotation(math.radians(24), 4, "X") @ Matrix.Translation((0.0, -0.28, -0.72))
    made = set(pad.verts)
    cuboid(pad, -0.36, 0.36, 0.28, 0.92, 0.72, 0.8)
    bmesh.ops.transform(pad, matrix=back, verts=[vert for vert in pad.verts if vert not in made])
    model.hard(pad, "vinyl", bevel=0.025, segments=2)
    paper = bmesh.new()
    cuboid(paper, -0.25, 0.25, -0.9, 0.28, 0.8, 0.803)
    made = set(paper.verts)
    cuboid(paper, -0.25, 0.25, 0.28, 0.9, 0.8, 0.803)
    bmesh.ops.transform(paper, matrix=back, verts=[vert for vert in paper.verts if vert not in made])
    model.plain(paper, "linen")
    return model


def treatment_couch():
    """A couch on one steel column, two squares long, its head at +Y: a black pad in two lengths, the head's raised a little,
    on a frame over a broad foot plate. The pad is 0.78 high."""
    model = Model("treatment_couch")
    steel = bmesh.new()
    cuboid(steel, -0.3, 0.3, -0.4, 0.4, 0.0, 0.04)
    cuboid(steel, -0.12, 0.12, -0.2, 0.2, 0.04, 0.62)
    cuboid(steel, -0.3, 0.3, -0.85, 0.85, 0.62, 0.68)
    model.hard(steel, "steel", bevel=0.008, segments=2)
    pad = bmesh.new()
    cuboid(pad, -0.33, 0.33, -0.92, 0.3, 0.68, 0.78)
    made = set(pad.verts)
    cuboid(pad, -0.33, 0.33, 0.31, 0.92, 0.68, 0.78)
    back = Matrix.Translation((0.0, 0.31, 0.68)) @ Matrix.Rotation(math.radians(10), 4, "X") @ Matrix.Translation((0.0, -0.31, -0.68))
    bmesh.ops.transform(pad, matrix=back, verts=[vert for vert in pad.verts if vert not in made])
    model.hard(pad, "plastic", bevel=0.03, segments=2)
    trim = bmesh.new()
    for side in (-1, 1):
        cuboid(trim, side * 0.335 - 0.006, side * 0.335 + 0.006, -0.9, 0.9, 0.655, 0.675)
    model.hard(trim, "metal", bevel=0.003, segments=1)
    return model


def iv_stand():
    """A drip stand: a pole on five legs on castors, a bar with two hooks at its top, and a pump on the pole."""
    model = Model("iv_stand")
    steel, wheels = bmesh.new(), bmesh.new()
    for n in range(5):
        angle = math.radians(90 + n * 72)
        x, y = math.cos(angle), math.sin(angle)
        beam(steel, (0.0, 0.0, 0.1), (x * 0.27, y * 0.27, 0.07), 0.025, 0.02)
        castor(wheels, steel, x * 0.27, y * 0.27, radius=0.025, wide=0.016)
    lathe(steel, [(0.0, 0.07), (0.035, 0.07), (0.035, 0.13), (0.013, 0.14), (0.013, 2.0), (0.0, 2.0)], segments=12)
    cuboid(steel, -0.2, 0.2, -0.01, 0.01, 1.98, 2.0)
    for side in (-1, 1):
        cuboid(steel, side * 0.2 - 0.006, side * 0.2 + 0.006, -0.006, 0.006, 1.9, 1.99)
        cuboid(steel, side * 0.2 - 0.006, side * 0.2 + 0.006, -0.04, 0.006, 1.9, 1.912)
    model.hard(steel, "steel", bevel=0.003, segments=1)
    model.turned(wheels, "rubber")
    pump = bmesh.new()
    cuboid(pump, -0.08, 0.08, -0.12, -0.03, 1.05, 1.3)
    cuboid(pump, -0.03, 0.03, -0.03, -0.012, 1.15, 1.22)
    model.hard(pump, "plastic", bevel=0.008, segments=2)
    face = bmesh.new()
    cuboid(face, -0.06, 0.06, -0.123, -0.12, 1.2, 1.27)
    model.plain(face, "screen")
    return model


def foot_bench():
    """A low padded step on a black frame of two runners, for getting up onto a couch. Its top is 0.38 high."""
    model = Model("foot_bench")
    hx, hy, top = 0.3, 0.2, 0.38
    frame = bmesh.new()
    for x in (-hx + 0.03, hx - 0.03):
        cuboid(frame, x - 0.015, x + 0.015, -hy + 0.02, hy - 0.02, 0.0, 0.025)
        for y in (-hy + 0.035, hy - 0.035):
            cuboid(frame, x - 0.015, x + 0.015, y - 0.015, y + 0.015, 0.025, top - 0.05)
    model.hard(frame, "plastic", bevel=0.004, segments=1)
    pad = bmesh.new()
    cuboid(pad, -hx, hx, -hy, hy, top - 0.05, top)
    model.hard(pad, "cloth", bevel=0.012, segments=2)
    return model


def gurney():
    """A stretcher on wheels two squares long: a padded mattress held by two straps on a frame of crossed legs on castors,
    with a rail at each end. The mattress is 0.74 high."""
    model = Model("gurney")
    steel, wheels = bmesh.new(), bmesh.new()
    for x in (-0.25, 0.25):
        for y0, y1 in ((-0.75, 0.6), (0.75, -0.6)):
            beam(steel, (x, y0, 0.1), (x, y1, 0.6), 0.025, 0.025)
        for y in (-0.75, 0.75):
            castor(wheels, steel, x, y, radius=0.045)
        cuboid(steel, x - 0.015, x + 0.015, -0.92, 0.92, 0.6, 0.63)
    for y in (-0.75, 0.75, -0.6, 0.6):
        cuboid(steel, -0.25, 0.25, y - 0.012, y + 0.012, 0.09 if abs(y) > 0.7 else 0.6, 0.11 if abs(y) > 0.7 else 0.62)
    for y in (-0.95, 0.95):
        cuboid(steel, -0.3, 0.3, y - 0.012, y + 0.012, 0.85, 0.87)
        for x in (-0.28, 0.0, 0.28):
            cuboid(steel, x - 0.01, x + 0.01, y - 0.01, y + 0.01, 0.62, 0.86)
    model.hard(steel, "steel", bevel=0.004, segments=1)
    model.turned(wheels, "rubber")
    pad = bmesh.new()
    cuboid(pad, -0.3, 0.3, -0.9, 0.9, 0.63, 0.74)
    model.hard(pad, "vinyl", bevel=0.03, segments=2)
    straps = bmesh.new()
    for y in (-0.25, 0.3):
        cuboid(straps, -0.31, 0.31, y - 0.035, y + 0.035, 0.62, 0.746)
    model.hard(straps, "rubber", bevel=0.003, segments=1)
    return model


def microscope():
    """A bench microscope: a heavy foot, an arm at the back, the stage before it and a head with two eyepieces leaning
    towards whoever sits at it, at -Y."""
    model = Model("microscope")
    body = bmesh.new()
    prism(body, (0.0, 0.0, 0.18, 0.24, 0.0), (0.0, 0.01, 0.16, 0.2, 0.06))
    cuboid(body, -0.04, 0.04, 0.03, 0.11, 0.06, 0.3)
    cuboid(body, -0.07, 0.07, -0.07, 0.07, 0.13, 0.145)
    prism_y(body, (0.0, 0.33, 0.07, 0.08, -0.06), (0.0, 0.33, 0.07, 0.08, 0.11))
    model.hard(body, "enamel", bevel=0.01, segments=2)
    dark = bmesh.new()
    lathe(dark, [(0.0, 0.16), (0.018, 0.16), (0.018, 0.29), (0.0, 0.29)], cy=-0.02, segments=10)
    for side in (-1, 1):
        lathe(dark, [(0.0, 0.0), (0.016, 0.0), (0.016, 0.1), (0.02, 0.105), (0.0, 0.11)], segments=10,
              to=Matrix.Translation((side * 0.03, -0.04, 0.36)) @ Matrix.Rotation(math.radians(40), 4, "X"))
    for side in (-1, 1):
        lathe(dark, [(0.0, -0.012), (0.03, -0.012), (0.03, 0.012), (0.0, 0.012)], segments=12, to=along_x(side * 0.045, 0.07, 0.2))
    model.turned(dark, "plastic", sharp=40.0)
    return model


def fume_hood(name, end):
    """Half of a cabinet two squares wide, at +Y its wall: a cupboard of two doors, a worktop, an open chamber under a
    canopy, and a panel at its end on the side end says, -1 or +1. Each half fills its square along x, so two meet."""
    model = Model(name)
    front, back = -0.37, 0.37
    body = bmesh.new()
    cuboid(body, -0.5, 0.5, front + 0.03, back, 0.0, 0.08)
    cuboid(body, -0.5, 0.5, front + 0.05, back, 0.08, 0.86)
    cuboid(body, -0.5, 0.5, back - 0.04, back, 0.9, 1.62)
    cuboid(body, -0.5, 0.5, front + 0.06, front + 0.1, 1.6, 1.66)
    prism_y(body, (0.0, 1.98, 1.0, 0.72, front + 0.12), (0.0, 1.95, 1.0, 0.7, back))
    side = end * 0.5
    cuboid(body, min(side, side - end * 0.04), max(side, side - end * 0.04), front, back, 0.0, 2.32)
    model.hard(body, "steel", bevel=0.006, segments=1)
    top = bmesh.new()
    cuboid(top, -0.5, 0.5, front - 0.02, back, 0.86, 0.9)
    model.hard(top, "worktop", bevel=0.006, segments=1)
    doors, pulls = bmesh.new(), bmesh.new()
    for x0, x1 in ((-0.48, -0.01), (0.01, 0.48)):
        framed_door(doors, doors, x0, x1, 0.1, 0.82, front + 0.032, rail=0.05, thick=0.018)
        x = x1 - 0.06 if x0 < 0 else x0 + 0.06
        bar_handle(pulls, x - 0.008, x + 0.008, front + 0.032, 0.55, 0.7, proud=0.025, thick=0.008)
    model.hard(doors, "steel", bevel=0.004)
    model.hard(pulls, "metal", bevel=0.002, segments=1)
    canopy = bmesh.new()
    prism_y(canopy, (0.0, 2.32, 1.0, 0.04, front + 0.02), (0.0, 2.32, 1.0, 0.04, back))
    model.hard(canopy, "plastic", bevel=0.005, segments=1)
    return model


def first_aid_case():
    """A first aid case on a wall, at +Y: a box with a handle on its top whose front is the sprite's own painting of it."""
    model = Model("first_aid_case")
    box = bmesh.new()
    cuboid(box, -0.21, 0.21, -0.06, 0.07, 0.0, 0.3)
    model.hard(box, "vinyl", bevel=0.012, segments=2)
    handle = bmesh.new()
    cuboid(handle, -0.08, 0.08, -0.02, 0.02, 0.32, 0.335)
    for x in (-0.08, 0.065):
        cuboid(handle, x, x + 0.015, -0.02, 0.02, 0.3, 0.32)
    for x in (-0.19, 0.15):
        cuboid(handle, x, x + 0.04, -0.064, -0.054, 0.22, 0.26)
    model.hard(handle, "linen", bevel=0.004, segments=1)
    face = bmesh.new()
    cuboid(face, -0.195, 0.195, -0.062, -0.058, 0.015, 0.285)
    model.plain(face, "pane")
    return model


def weighing_scale():
    """A doctor's scale: a platform, and at its back a column up to a head of sliding weights on a beam."""
    model = Model("weighing_scale")
    deck = bmesh.new()
    cuboid(deck, -0.17, 0.17, -0.2, 0.18, 0.0, 0.06)
    model.hard(deck, "paint", bevel=0.01, segments=2)
    mat = bmesh.new()
    cuboid(mat, -0.15, 0.15, -0.18, 0.12, 0.06, 0.068)
    model.plain(mat, "rubber")
    column = bmesh.new()
    cuboid(column, -0.018, 0.018, 0.14, 0.17, 0.06, 1.35)
    prism(column, (0.0, 0.155, 0.08, 0.04, 1.35), (0.0, 0.155, 0.3, 0.05, 1.42))
    cuboid(column, -0.15, 0.15, 0.13, 0.18, 1.42, 1.52)
    model.hard(column, "paint", bevel=0.006, segments=1)
    beams = bmesh.new()
    for z in (1.445, 1.49):
        cuboid(beams, -0.14, 0.14, 0.125, 0.13, z, z + 0.018)
    for x, z in ((-0.05, 1.445), (0.06, 1.49)):
        cuboid(beams, x - 0.012, x + 0.012, 0.115, 0.135, z - 0.004, z + 0.024)
    model.hard(beams, "steel", bevel=0.002, segments=1)
    return model


def morgue_table():
    """A post-mortem table two squares long: a steel tray with a raised rim on a pedestal under its foot half, a drain and
    a tap at its head, at +Y. The tray is 0.88 high."""
    model = Model("morgue_table")
    steel = bmesh.new()
    hx, hy, top = 0.42, 0.95, 0.88
    cuboid(steel, -hx, hx, -hy, hy, top - 0.04, top - 0.03)
    cuboid(steel, -hx, hx, -hy, -hy + 0.025, top - 0.06, top)
    cuboid(steel, -hx, hx, hy - 0.025, hy, top - 0.06, top)
    cuboid(steel, -hx, -hx + 0.025, -hy + 0.025, hy - 0.025, top - 0.06, top)
    cuboid(steel, hx - 0.025, hx, -hy + 0.025, hy - 0.025, top - 0.06, top)
    cuboid(steel, -0.18, 0.18, -0.65, -0.2, 0.06, top - 0.06)
    cuboid(steel, -0.24, 0.24, -0.72, -0.13, 0.0, 0.06)
    model.hard(steel, "steel", bevel=0.005, segments=1)
    pipes = bmesh.new()
    lathe(pipes, [(0.0, top - 0.04), (0.035, top - 0.04), (0.035, top - 0.15), (0.02, top - 0.17), (0.02, 0.0), (0.0, 0.0)], cy=0.6, segments=10)
    lathe(pipes, [(0.0, top), (0.014, top), (0.014, top + 0.22), (0.0, top + 0.23)], cy=hy - 0.06, segments=10)
    beam(pipes, (0.0, hy - 0.06, top + 0.2), (0.0, hy - 0.2, top + 0.2), 0.022, 0.022)
    model.turned(pipes, "metal", sharp=40.0)
    return model


def blood_bank():
    """A refrigerator for blood, at +Y its wall: a steel cabinet of two compartments one over the other, each behind a door
    of steel bars round a card holder."""
    model = Model("blood_bank")
    hx, front, back, top = 0.4, -0.33, 0.33, 1.92
    body = bmesh.new()
    cuboid(body, -hx, hx, front + 0.03, back, 0.0, 0.1)
    for x in (-hx, hx - 0.04):
        cuboid(body, x, x + 0.04, front, back, 0.1, top)
    cuboid(body, -hx + 0.04, hx - 0.04, front, back, top - 0.06, top)
    cuboid(body, -hx + 0.04, hx - 0.04, front, back, 0.98, 1.04)
    cuboid(body, -hx + 0.04, hx - 0.04, front, back, 0.1, 0.16)
    cuboid(body, -hx + 0.04, hx - 0.04, back - 0.03, back, 0.16, top - 0.06)
    model.hard(body, "steel", bevel=0.006, segments=1)
    inside = bmesh.new()
    for z0, z1 in ((0.16, 0.98), (1.04, top - 0.06)):
        for n in range(1, 3):
            z = z0 + (z1 - z0) * n / 3
            cuboid(inside, -hx + 0.04, hx - 0.04, front + 0.08, back - 0.03, z - 0.01, z)
    model.hard(inside, "plastic", bevel=0.002, segments=1)
    bars = bmesh.new()
    for z0, z1 in ((0.17, 0.97), (1.05, top - 0.07)):
        cuboid(bars, -hx + 0.05, hx - 0.05, front - 0.012, front + 0.008, z0, z0 + 0.03)
        cuboid(bars, -hx + 0.05, hx - 0.05, front - 0.012, front + 0.008, z1 - 0.03, z1)
        for n in range(13):
            x = -hx + 0.08 + n * (2 * hx - 0.16) / 12
            cuboid(bars, x - 0.005, x + 0.005, front - 0.008, front + 0.004, z0 + 0.03, z1 - 0.03)
    model.hard(bars, "metal", bevel=0.002, segments=1)
    cards = bmesh.new()
    for z in (0.83, top - 0.2):
        cuboid(cards, -hx + 0.06, -hx + 0.24, front - 0.016, front - 0.012, z, z + 0.08)
    model.plain(cards, "linen")
    return model


def bassinet():
    """A ward's crib for a baby: an open tub on a steel frame of four legs on castors, a mattress in it. The tub's rim is 0.9 high."""
    model = Model("bassinet")
    hx, hy, top = 0.42, 0.27, 0.9
    steel, wheels = bmesh.new(), bmesh.new()
    for x in (-hx + 0.03, hx - 0.03):
        for y in (-hy + 0.03, hy - 0.03):
            cuboid(steel, x - 0.012, x + 0.012, y - 0.012, y + 0.012, 0.08, top - 0.04)
            castor(wheels, steel, x, y, radius=0.03, wide=0.018)
        cuboid(steel, x - 0.01, x + 0.01, -hy + 0.03, hy - 0.03, 0.2, 0.22)
    cuboid(steel, -hx + 0.03, hx - 0.03, -0.01, 0.01, 0.2, 0.22)
    cuboid(steel, -hx, hx, -hy, hy, top - 0.27, top - 0.25)
    model.hard(steel, "metal", bevel=0.004, segments=1)
    model.turned(wheels, "rubber")
    tub = bmesh.new()
    cuboid(tub, -hx - 0.02, hx + 0.02, -hy - 0.02, -hy, top - 0.25, top)
    cuboid(tub, -hx - 0.02, hx + 0.02, hy, hy + 0.02, top - 0.25, top)
    for x0, x1 in ((-hx - 0.02, -hx), (hx, hx + 0.02)):
        cuboid(tub, x0, x1, -hy, hy, top - 0.25, top)
    cuboid(tub, -hx, hx, -hy, hy, top - 0.25, top - 0.23)
    model.hard(tub, "enamel", bevel=0.004, segments=1)
    mattress = bmesh.new()
    cuboid(mattress, -hx + 0.03, hx - 0.03, -hy + 0.03, hy - 0.03, top - 0.23, top - 0.17)
    model.hard(mattress, "cloth", bevel=0.015, segments=2)
    return model


def dentist_chair():
    """A dentist's chair two squares long, its head at +Y: a grey base and lift arm, a padded seat, a back laid well back,
    a headrest, and a rest for the legs sloping down to the foot. The seat is 0.5 high."""
    model = Model("dentist_chair")
    base = bmesh.new()
    cuboid(base, -0.3, 0.3, -0.55, 0.35, 0.0, 0.06)
    prism_y(base, (0.0, 0.2, 0.3, 0.28, -0.1), (0.0, 0.2, 0.3, 0.28, 0.3))
    beam(base, (0.0, 0.2, 0.2), (0.0, -0.15, 0.42), 0.26, 0.14)
    lathe(base, [(0.0, -0.17), (0.11, -0.17), (0.11, 0.17), (0.0, 0.17)], segments=16, to=along_x(0.0, 0.2, 0.18))
    model.hard(base, "metal", bevel=0.01, segments=2)
    pads = bmesh.new()
    cuboid(pads, -0.29, 0.29, -0.35, 0.25, 0.42, 0.52)
    made = set(pads.verts)
    cuboid(pads, -0.27, 0.27, 0.25, 0.9, 0.42, 0.5)
    tilt = Matrix.Translation((0.0, 0.25, 0.47)) @ Matrix.Rotation(math.radians(28), 4, "X") @ Matrix.Translation((0.0, -0.25, -0.47))
    bmesh.ops.transform(pads, matrix=tilt, verts=[vert for vert in pads.verts if vert not in made])
    made = set(pads.verts)
    cuboid(pads, -0.25, 0.25, -0.95, -0.35, 0.42, 0.5)
    legs = Matrix.Translation((0.0, -0.35, 0.47)) @ Matrix.Rotation(math.radians(22), 4, "X") @ Matrix.Translation((0.0, 0.35, -0.47))
    bmesh.ops.transform(pads, matrix=legs, verts=[vert for vert in pads.verts if vert not in made])
    model.hard(pads, "vinyl", bevel=0.03, segments=2)
    head = bmesh.new()
    cuboid(head, -0.13, 0.13, 0.0, 0.09, 0.0, 0.2)
    bmesh.ops.transform(head, matrix=Matrix.Translation((0.0, 0.93, 0.85)) @ Matrix.Rotation(math.radians(28), 4, "X"), verts=head.verts[:])
    model.hard(head, "vinyl", bevel=0.03, segments=2)
    arms = bmesh.new()
    for side in (-1, 1):
        cuboid(arms, side * 0.33 - 0.04, side * 0.33 + 0.04, -0.05, 0.35, 0.6, 0.65)
        cuboid(arms, side * 0.33 - 0.02, side * 0.33 + 0.02, 0.25, 0.32, 0.47, 0.6)
    model.hard(arms, "metal", bevel=0.01, segments=2)
    return model


def dentist_unit():
    """A dentist's cart and lamp two squares long: a cabinet on castors at the back with a tray of instruments on an arm at
    its side, and from it a column whose arm carries the lamp over the chair at -Y."""
    model = Model("dentist_unit")
    cart, wheels = bmesh.new(), bmesh.new()
    cuboid(cart, -0.2, 0.2, 0.3, 0.7, 0.08, 0.82)
    cuboid(cart, -0.16, 0.16, 0.24, 0.3, 0.55, 0.8)
    for x in (-0.16, 0.16):
        for y in (0.34, 0.66):
            castor(wheels, cart, x, y, radius=0.03, wide=0.018)
    model.hard(cart, "enamel", bevel=0.01, segments=2)
    model.turned(wheels, "rubber")
    steel = bmesh.new()
    lathe(steel, [(0.0, 0.82), (0.035, 0.82), (0.035, 1.92), (0.0, 1.92)], cy=0.55, segments=12)
    beam(steel, (0.0, 0.58, 1.9), (0.0, -0.55, 1.9), 0.05, 0.05)
    lathe(steel, [(0.0, 1.62), (0.022, 1.62), (0.022, 1.9), (0.0, 1.9)], cy=-0.55, segments=10)
    beam(steel, (0.18, 0.5, 0.86), (0.38, 0.2, 0.86), 0.04, 0.04)
    model.hard(steel, "metal", bevel=0.005, segments=1)
    lamp = bmesh.new()
    cuboid(lamp, -0.15, 0.15, -0.65, -0.45, 1.52, 1.64)
    cuboid(lamp, 0.24, 0.52, 0.02, 0.28, 0.86, 0.9)
    model.hard(lamp, "enamel", bevel=0.012, segments=2)
    light = bmesh.new()
    cuboid(light, -0.12, 0.12, -0.63, -0.47, 1.515, 1.52)
    for n in range(3):
        x = 0.3 + n * 0.08
        cuboid(light, x - 0.01, x + 0.01, 0.06, 0.08, 0.68, 0.86)
    model.plain(light, "plastic")
    return model


DESK_TOP = 0.75
DESK_INSET = 0.04


def desk_square(name, x0, x1, y1, support, side=0):
    """One square of a desk the game lays over several in an L or a run: its share of the top, from x0 to x1 and from the
    front of the square to y1, and what holds it up. support is 'panel' (a modesty board along the back), 'end' (that,
    and a panel leg at the side side says), 'column' (a post on a foot under the middle) or 'pedestal' (a stack of
    drawers, opening to the front, and the board at the back). The top is 0.75 high, its front at -0.5: each square of a
    run meets the next."""
    model = Model(name, centred=False)
    top = bmesh.new()
    cuboid(top, x0, x1, -0.5, y1, DESK_TOP - 0.035, DESK_TOP)
    model.hard(top, "wood", bevel=0.006, segments=2)
    body = bmesh.new()
    if support in ("panel", "end", "pedestal"):
        cuboid(body, x0, x1, y1 - 0.06, y1 - 0.04, 0.24, DESK_TOP - 0.035)
    if support == "end":
        edge = x0 if side < 0 else x1
        cuboid(body, min(edge, edge - side * 0.035), max(edge, edge - side * 0.035), -0.46, y1 - 0.02, 0.0, DESK_TOP - 0.035)
    if support == "column":
        lathe(body, [(0.0, 0.0), (0.2, 0.0), (0.2, 0.025), (0.05, 0.04), (0.04, 0.06), (0.04, DESK_TOP - 0.035), (0.0, DESK_TOP - 0.035)], segments=16)
    fronts, pulls = bmesh.new(), bmesh.new()
    if support == "pedestal":
        cuboid(body, -0.25, 0.25, -0.42, y1 - 0.06, 0.0, DESK_TOP - 0.035)
        drawer_fronts(fronts, pulls, -0.25, 0.25, 0.03, DESK_TOP - 0.045, 3, -0.438, size=1.1)
    model.hard(body, "metal", bevel=0.005)
    if support == "pedestal":
        model.hard(fronts, "metal", bevel=0.004)
        model.turned(pulls, "steel")
    return model


def office_medical():
    """What offices, wards, surgeries and laboratories have that Clinic.java stands: the medical furniture, telephones, and
    the squares of desks laid in an L."""
    inset = 0.5 - DESK_INSET
    return [hospital_bed(), mayo_stand(), medical_stool(), rotary_phone(), desk_phone(), exam_table(), treatment_couch(), iv_stand(),
            foot_bench(), gurney(), microscope(), fume_hood("fume_hood_left", -1), fume_hood("fume_hood_right", 1), first_aid_case(),
            weighing_scale(), morgue_table(), blood_bank(), bassinet(), dentist_chair(), dentist_unit(),
            desk_square("desk_run", -0.5, 0.5, inset, "panel"), desk_square("desk_end_left", -inset, 0.5, inset, "end", -1),
            desk_square("desk_end_right", -0.5, inset, inset, "end", 1), desk_square("desk_corner", -0.5, inset, inset, "column"),
            desk_square("desk_pedestal", -0.5, 0.5, inset, "pedestal"), desk_square("desk_pedestal_left", -inset, 0.5, inset, "pedestal"),
            desk_square("desk_pedestal_right", -0.5, inset, inset, "pedestal")]


def soft_slab(model, surface, x0, x1, y0, y1, z0, z1, crown, cells=(2, 4)):
    """A soft body the shape of a box, its top fuller in the middle: a mattress, a sleeping bag."""
    part = bmesh.new()
    cage = grid_box(part, spread(x0, x1, cells[0]), spread(y0, y1, cells[1]), [z0, z1])
    for (i, j, k), vert in cage.items():
        if k == 1 and 0 < i < cells[0] and 0 < j < cells[1]:
            vert.co.z += crown
    crease(part, cage, 0.3)
    model.soft(part, surface, levels=2)


def camp_rod(radius, low, high):
    """The profile of a rod, in rings no further apart than a vertex's light can be trusted to run evenly over."""
    steps = max(1, math.ceil((high - low) / SHORTEST_CUT))
    return [(0.0, low)] + [(radius, low + (high - low) * n / steps) for n in range(steps + 1)] + [(0.0, high)]


def camp_pole(bm, x, y, z0, z1, radius):
    lathe(bm, camp_rod(radius, z0, z1), cx=x, cy=y, segments=8)


def log_x(bm, x0, x1, y, z, radius):
    """A round length of wood lying along x."""
    lathe(bm, camp_rod(radius, x0, x1), segments=8, to=along_x(0.0, y, z))


def stone(bm, x, y, z, size, turn, rng):
    """A rounded field stone, its long side turned turn degrees from x."""
    high = size * rng.uniform(0.75, 1.05)
    lathe(bm, [(0.0, 0.0), (size * 0.75, 0.0), (size, high * 0.4), (size * 0.8, high * 0.85), (0.0, high)], segments=7,
          sx=rng.uniform(1.1, 1.4), sy=rng.uniform(0.8, 1.0), to=Matrix.Translation((x, y, z)) @ Matrix.Rotation(math.radians(turn), 4, "Z"))


def stone_ring(model, radius, count, courses, size, seed):
    """Stones laid round in a ring, a course on a course, each course turned half a stone on the one under it."""
    rng = random.Random(seed)
    stones = bmesh.new()
    for course in range(courses):
        for n in range(count):
            angle = 360.0 * (n + 0.5 * (course % 2)) / count + rng.uniform(-4, 4)
            at = radius + rng.uniform(-0.01, 0.01)
            stone(stones, at * math.cos(math.radians(angle)), at * math.sin(math.radians(angle)), course * size * 0.85, size, angle + 90, rng)
    model.plain(stones, "paint")


def tent(name, half, length, ridge, door, guys):
    """A ridge tent: a fly over a pole at each end, its door to the front as a frame round a dark net, closed at its back,
    on a groundsheet, its poles held by a line to a peg ahead of each end when guys is more than 0."""
    model = Model(name)
    end = length / 2
    fly = bmesh.new()
    thick = 0.012
    outline_along_y(fly, [(-half, 0.03), (0.0, ridge), (half, 0.03), (half - thick * 1.5, 0.03), (0.0, ridge - thick * 1.6), (-half + thick * 1.5, 0.03)],
                    -end - 0.05, end + 0.05)
    outline_along_y(fly, [(-half + 0.03, 0.03), (half - 0.03, 0.03), (0.0, ridge - 0.03)], end - 0.03, end - 0.02)
    if door:
        model.hard(fly, "cloth", bevel=0.003, segments=1)
        frame, net = bmesh.new(), bmesh.new()
        outline_along_y(frame, [(-half + 0.03, 0.03), (half - 0.03, 0.03), (0.0, ridge - 0.03)], -end + 0.02, -end + 0.03)
        model.hard(frame, "linen", bevel=0.002, segments=1)
        inset = 0.09
        outline_along_y(net, [(-half + 0.03 + inset * 1.6, 0.03 + inset), (half - 0.03 - inset * 1.6, 0.03 + inset), (0.0, ridge - 0.03 - inset * 1.4)],
                        -end + 0.014, -end + 0.02)
        model.hard(net, "rubber", bevel=0.002, segments=1)
    else:
        outline_along_y(fly, [(-half + 0.03, 0.03), (half - 0.03, 0.03), (0.0, ridge - 0.03)], -end + 0.02, -end + 0.03)
        model.hard(fly, "cloth", bevel=0.003, segments=1)
    poles = bmesh.new()
    for side in (-1, 1):
        camp_pole(poles, 0.0, side * end, 0.0, ridge + 0.1, 0.012)
    model.turned(poles, "metal")
    sheet = bmesh.new()
    cuboid(sheet, -half + 0.04, half - 0.04, -end - 0.08, end, 0.0, 0.008)
    model.hard(sheet, "rubber", bevel=0.002, segments=1)
    if guys > 0:
        lines = bmesh.new()
        for side in (-1, 1):
            beam(lines, (0.0, side * end, ridge + 0.08), (0.0, side * (end + guys), 0.03), 0.008, 0.008)
            cuboid(lines, -0.012, 0.012, side * (end + guys) - 0.012, side * (end + guys) + 0.012, 0.0, 0.05)
        model.hard(lines, "linen", bevel=0.002, segments=1)
    return model


def sleeping_bag():
    """A sleeping bag laid out flat, its head to the back, the top of its cover turned down there to show the lining."""
    model = Model("sleeping_bag")
    soft_slab(model, "cloth", -0.38, 0.38, -0.97, 0.97, 0.0, 0.08, 0.02, cells=(2, 6))
    soft_slab(model, "linen", -0.36, 0.36, 0.5, 0.94, 0.07, 0.1, 0.006, cells=(2, 2))
    return model


def camp_mattress():
    """A mattress laid on the floor with no bed under it."""
    model = Model("camp_mattress")
    soft_slab(model, "linen", -0.46, 0.46, -0.96, 0.96, 0.0, 0.125, 0.012)
    return model


def plank_bed():
    """A bed knocked together from planks: a box of boards on four corner posts, a mattress laid in its top."""
    model = Model("plank_bed")
    half, end, sides = 0.475, 0.975, 0.43
    frame = bmesh.new()
    for side in (-1, 1):
        cuboid(frame, side * half, side * (half - 0.025), -end + 0.04, end - 0.04, 0.06, sides)
        cuboid(frame, -half + 0.04, half - 0.04, side * end, side * (end - 0.025), 0.06, sides)
        for other in (-1, 1):
            cuboid(frame, side * half - 0.03, side * half + 0.03, other * end - 0.03, other * end + 0.03, 0.0, sides + 0.02)
    for n in range(7):
        y = -end + 0.12 + n * (2 * end - 0.24) / 6
        cuboid(frame, -half + 0.025, half - 0.025, y - 0.04, y + 0.04, sides - 0.06, sides - 0.03)
    for z in (0.2, 0.33):
        for side in (-1, 1):
            cuboid(frame, side * (half - 0.002), side * (half + 0.004), -end + 0.04, end - 0.04, z - 0.004, z)
    model.hard(frame, "wood", bevel=0.004, segments=1)
    soft_slab(model, "linen", -half + 0.03, half - 0.03, -end + 0.03, end - 0.03, sides - 0.03, 0.536, 0.01)
    return model


def tarp_shelter():
    """A tarp hung from a bar between two posts at its front and pegged to the ground at its back, over a floor of logs."""
    model = Model("tarp_shelter")
    half, front, back, bar = 0.94, -0.4, 0.46, 1.32
    wood, logs = bmesh.new(), bmesh.new()
    for side in (-1, 1):
        camp_pole(wood, side * (half - 0.06), front, 0.0, bar + 0.13, 0.04)
    log_x(wood, -half - 0.02, half + 0.02, front, bar, 0.034)
    for n in range(6):
        log_x(logs, -half + 0.08 + 0.03 * (n % 2), half - 0.08 - 0.03 * (n % 3), front + 0.1 + n * 0.12, 0.055, 0.055)
    model.turned(wood, "wood")
    model.turned(logs, "dark_wood")
    tarp = bmesh.new()
    outline_along_x(tarp, [(front + 0.02, bar - 0.02), (back, 0.02), (back + 0.008, 0.028), (front + 0.026, bar - 0.012)], -half + 0.02, half - 0.02)
    model.plain(tarp, "cloth")
    return model


def drying_rack():
    """A rack of poles to hang hides and washing on: a pole along the top lashed into an A of poles at each end, and a pole along the ground on each side."""
    model = Model("drying_rack")
    half, spread_y, top = 0.92, 0.66, 1.52
    rack = bmesh.new()
    beam(rack, (-half - 0.04, 0.0, top), (half + 0.04, 0.0, top), 0.055, 0.055)
    for x in (-half + 0.08, half - 0.08):
        for side in (-1, 1):
            beam(rack, (x, -side * 0.06, top + 0.12), (x, side * spread_y, 0.03), 0.055, 0.055)
    for side in (-1, 1):
        beam(rack, (-half, side * spread_y, 0.04), (half, side * spread_y, 0.04), 0.05, 0.05)
    model.hard(rack, "wood", bevel=0.012, segments=2)
    return model


def plank_table():
    """A square table of planks laid across, on four legs joined by rails under the top and stretchers low down."""
    model = Model("plank_table")
    half, top = 0.475, 0.87
    wood = bmesh.new()
    for n in range(5):
        x = -half + n * (2 * half) / 5
        cuboid(wood, x + 0.003, x + 2 * half / 5 - 0.003, -half, half, top - 0.04, top)
    for sx in (-1, 1):
        for sy in (-1, 1):
            cuboid(wood, sx * (half - 0.05) - 0.035, sx * (half - 0.05) + 0.035, sy * (half - 0.05) - 0.035, sy * (half - 0.05) + 0.035, 0.0, top - 0.04)
    for side in (-1, 1):
        cuboid(wood, -half + 0.08, half - 0.08, side * (half - 0.05) - 0.015, side * (half - 0.05) + 0.015, top - 0.15, top - 0.04)
        cuboid(wood, side * (half - 0.05) - 0.015, side * (half - 0.05) + 0.015, -half + 0.08, half - 0.08, top - 0.15, top - 0.04)
        cuboid(wood, side * (half - 0.05) - 0.012, side * (half - 0.05) + 0.012, -half + 0.08, half - 0.08, 0.14, 0.2)
    model.hard(wood, "wood", bevel=0.005, segments=1)
    return model


def plank_side_table():
    """A low table of boards with a drawer under its top at the front."""
    model = Model("plank_side_table")
    half, top = 0.3, 0.385
    wood = bmesh.new()
    for n in range(3):
        x = -half + n * 2 * half / 3
        cuboid(wood, x + 0.003, x + 2 * half / 3 - 0.003, -half, half, top - 0.03, top)
    for sx in (-1, 1):
        for sy in (-1, 1):
            cuboid(wood, sx * (half - 0.04) - 0.025, sx * (half - 0.04) + 0.025, sy * (half - 0.04) - 0.025, sy * (half - 0.04) + 0.025, 0.0, top - 0.03)
    cuboid(wood, -half + 0.06, half - 0.06, -half + 0.03, half - 0.06, top - 0.13, top - 0.03)
    cuboid(wood, -half + 0.07, half - 0.07, -half + 0.015, -half + 0.03, top - 0.125, top - 0.035)
    model.hard(wood, "wood", bevel=0.004, segments=1)
    pull = bmesh.new()
    cuboid(pull, -0.04, 0.04, -half - 0.005, -half + 0.015, top - 0.09, top - 0.07)
    model.hard(pull, "metal", bevel=0.002, segments=1)
    return model


def chair_frame(bm, seat, back_top, half, depth):
    """Four square legs, the back two going on up as the posts of the back, a seat of boards and rails under it."""
    for sx in (-1, 1):
        cuboid(bm, sx * half, sx * (half - 0.04), -depth, -depth + 0.04, 0.0, seat - 0.03)
        cuboid(bm, sx * half, sx * (half - 0.04), depth - 0.04, depth, 0.0, back_top)
        cuboid(bm, sx * (half - 0.005), sx * (half - 0.035), -depth + 0.04, depth - 0.04, 0.12, 0.15)
    for n in range(3):
        y = -depth + n * (2 * depth - 0.04) / 3
        cuboid(bm, -half, half, y + 0.003, y + (2 * depth - 0.04) / 3 - 0.003, seat - 0.03, seat)


def slat_chair():
    """A chair made with care: three slats in its back between a rail over the seat and a rail at the top."""
    model = Model("slat_chair")
    half, depth, seat, top = 0.22, 0.21, 0.43, 0.96
    wood = bmesh.new()
    chair_frame(wood, seat, top, half, depth)
    cuboid(wood, -half + 0.04, half - 0.04, depth - 0.035, depth - 0.01, top - 0.08, top - 0.01)
    cuboid(wood, -half + 0.04, half - 0.04, depth - 0.035, depth - 0.01, seat + 0.06, seat + 0.1)
    for x in (-0.09, 0.0, 0.09):
        cuboid(wood, x - 0.025, x + 0.025, depth - 0.03, depth - 0.015, seat + 0.1, top - 0.08)
    model.hard(wood, "wood", bevel=0.004, segments=1)
    return model


def plank_chair():
    """A chair of rough boards: its back is two planks standing side by side."""
    model = Model("plank_chair")
    half, depth, seat, top = 0.22, 0.21, 0.43, 0.98
    wood = bmesh.new()
    chair_frame(wood, seat, seat + 0.02, half, depth)
    for x in (-0.1, 0.1):
        cuboid(wood, x - 0.098, x + 0.098, depth - 0.025, depth, seat - 0.03, top - (0.03 if x > 0 else 0.0))
    model.hard(wood, "wood", bevel=0.004, segments=1)
    return model


def box_chair():
    """A seat made of a box: boards closed in at its sides, and a back of boards laid across between two posts."""
    model = Model("box_chair")
    half, depth, seat, top = 0.23, 0.22, 0.38, 0.86
    wood = bmesh.new()
    for side in (-1, 1):
        cuboid(wood, side * half, side * (half - 0.025), -depth, depth, 0.0, seat - 0.03)
        cuboid(wood, side * half, side * (half - 0.04), depth - 0.04, depth, seat - 0.03, top)
    cuboid(wood, -half + 0.025, half - 0.025, -depth, -depth + 0.02, seat - 0.12, seat - 0.03)
    for n in range(3):
        y = -depth + n * 2 * depth / 3
        cuboid(wood, -half, half, y + 0.003, y + 2 * depth / 3 - 0.003, seat - 0.03, seat)
    for z in (seat + 0.12, seat + 0.27, top - 0.1):
        cuboid(wood, -half + 0.04, half - 0.04, depth - 0.03, depth - 0.01, z, z + 0.09)
    model.hard(wood, "wood", bevel=0.004, segments=1)
    return model


def campfire():
    """A cold fire in a ring of stones: charred sticks fallen in across each other on a bed of ash."""
    model = Model("campfire")
    stone_ring(model, 0.3, 11, 1, 0.075, 3)
    ash, sticks = bmesh.new(), bmesh.new()
    lathe(ash, [(0.0, 0.0), (0.24, 0.0), (0.22, 0.012), (0.0, 0.02)], segments=12)
    model.turned(ash, "rubber")
    for n, turn in enumerate((10, 70, 125, 160)):
        lathe(sticks, [(0.0, -0.2), (0.03, -0.2), (0.03, 0.2), (0.0, 0.2)], segments=7,
              to=Matrix.Rotation(math.radians(turn), 4, "Z") @ Matrix.Translation((0.0, 0.0, 0.035 + 0.025 * n)) @ Matrix.Rotation(math.radians(90 + 8 * (n % 2 * 2 - 1)), 4, "Y"))
    model.turned(sticks, "dark_wood")
    return model


def cooking_pit():
    """A round hearth built up of stones, a grill of steel bars over its top and charcoal in the bottom of it."""
    model = Model("cooking_pit")
    stone_ring(model, 0.36, 13, 4, 0.12, 5)
    grill = bmesh.new()
    lathe(grill, [(0.32, 0.47), (0.335, 0.47), (0.335, 0.485), (0.32, 0.485)], segments=24, closed=True)
    for n in range(7):
        y = -0.3 + n * 0.1
        reach = math.sqrt(0.33 ** 2 - y ** 2)
        cuboid(grill, -reach, reach, y - 0.005, y + 0.005, 0.47, 0.48)
    model.hard(grill, "metal", bevel=0.002, segments=1)
    coal = bmesh.new()
    lathe(coal, [(0.0, 0.0), (0.24, 0.0), (0.24, 0.05), (0.0, 0.07)], segments=12)
    model.turned(coal, "rubber")
    return model


def cooking_pit_simple():
    """A fire in a ring of stones with a forked stick driven in each side of it and a bar across the forks to hang a pot from."""
    model = Model("cooking_pit_simple")
    stone_ring(model, 0.3, 11, 1, 0.075, 7)
    wood = bmesh.new()
    for side in (-1, 1):
        x = side * 0.42
        camp_pole(wood, x, 0.0, 0.0, 0.86, 0.025)
        beam(wood, (x, 0.0, 0.84), (x - side * 0.06, 0.0, 1.0), 0.03, 0.03)
        beam(wood, (x, 0.0, 0.84), (x + side * 0.05, 0.0, 0.98), 0.03, 0.03)
    log_x(wood, -0.48, 0.48, 0.0, 0.93, 0.02)
    model.turned(wood, "wood")
    ash = bmesh.new()
    lathe(ash, [(0.0, 0.0), (0.24, 0.0), (0.22, 0.012), (0.0, 0.02)], segments=12)
    model.turned(ash, "rubber")
    return model


def hoop(bm, radius, z0, z1):
    lathe(bm, [(radius, z0), (radius + 0.006, z0), (radius + 0.006, z1), (radius, z1)], segments=20, closed=True)


def wooden_bucket():
    """A bucket of staves in two iron hoops, a handle of wire over it and water inside."""
    model = Model("wooden_bucket")
    low, high, tall = 0.155, 0.19, 0.3
    staves = bmesh.new()
    lathe(staves, [(0.0, 0.0), (low, 0.0), (high, tall), (high - 0.012, tall), (low - 0.012, 0.025), (0.0, 0.025)], segments=18)
    model.turned(staves, "wood", sharp=35.0)
    iron = bmesh.new()
    for z in (0.04, 0.24):
        hoop(iron, low + (high - low) * (z + 0.015) / tall, z, z + 0.03)
    steps = 10
    for n in range(steps):
        a0, a1 = math.pi * n / steps, math.pi * (n + 1) / steps
        beam(iron, (high * math.cos(a0), 0.0, tall - 0.03 + 0.16 * math.sin(a0)), (high * math.cos(a1), 0.0, tall - 0.03 + 0.16 * math.sin(a1)), 0.008, 0.008)
    model.plain(iron, "metal")
    water = bmesh.new()
    lathe(water, [(0.0, 0.2), (0.172, 0.2), (0.172, 0.24), (0.0, 0.24)], segments=18)
    model.turned(water, "plastic")
    return model


def burn_barrel():
    """A steel drum with its top cut out, to burn rubbish in: ash and charred scraps lie in it."""
    model = Model("burn_barrel")
    wide = 0.285
    body = bmesh.new()
    profile = [(0.0, 0.0), (wide, 0.0), (wide + 0.008, 0.01), (wide + 0.008, 0.03), (wide, 0.04)]
    for z in (0.3, 0.58):
        profile += [(wide, z - 0.015), (wide + 0.011, z - 0.004), (wide + 0.011, z + 0.012), (wide, z + 0.023)]
    profile += [(wide, 0.88), (wide - 0.006, 0.88), (wide - 0.006, 0.03), (0.0, 0.03)]
    lathe(body, profile, segments=28)
    model.turned(body, "paint", sharp=35.0)
    ash = bmesh.new()
    lathe(ash, [(0.0, 0.55), (wide - 0.007, 0.55), (wide - 0.007, 0.58), (0.12, 0.62), (0.0, 0.6)], segments=16)
    model.turned(ash, "rubber", sharp=60.0)
    return model


def wooden_barrel():
    """A barrel of bent staves, wider at its middle, in four iron hoops, its head closed."""
    model = Model("wooden_barrel")
    staves = bmesh.new()
    profile = [(0.0, 0.0), (0.22, 0.0), (0.245, 0.2), (0.255, 0.4), (0.245, 0.6), (0.22, 0.8), (0.205, 0.8), (0.205, 0.785), (0.0, 0.785)]
    lathe(staves, profile, segments=20)
    model.turned(staves, "wood", sharp=35.0)
    iron = bmesh.new()
    for z, radius in ((0.04, 0.224), (0.2, 0.245), (0.57, 0.247), (0.73, 0.226)):
        hoop(iron, radius, z, z + 0.035)
    model.plain(iron, "metal")
    return model


def stone_well():
    """A well: a round wall of stones, a post each side of it and a windlass between them with the rope wound on it."""
    model = Model("stone_well")
    stone_ring(model, 0.36, 12, 5, 0.15, 11)
    dark = bmesh.new()
    lathe(dark, [(0.0, 0.4), (0.23, 0.4), (0.23, 0.42), (0.0, 0.42)], segments=14)
    model.turned(dark, "rubber")
    wood = bmesh.new()
    for side in (-1, 1):
        cuboid(wood, side * 0.38 - 0.045, side * 0.38 + 0.045, -0.045, 0.045, 0.0, 1.4)
    log_x(wood, -0.34, 0.34, 0.0, 1.2, 0.055)
    model.hard(wood, "wood", bevel=0.008, segments=1)
    rope = bmesh.new()
    log_x(rope, -0.09, 0.09, 0.0, 1.2, 0.07)
    beam(rope, (0.0, -0.065, 1.2), (0.0, -0.065, 0.55), 0.012, 0.012)
    model.turned(rope, "linen")
    crank = bmesh.new()
    beam(crank, (0.43, 0.0, 1.2), (0.47, 0.0, 1.2), 0.03, 0.03)
    beam(crank, (0.47, 0.0, 1.2), (0.47, 0.0, 1.02), 0.025, 0.025)
    beam(crank, (0.47, 0.0, 1.03), (0.56, 0.0, 1.03), 0.025, 0.025)
    model.plain(crank, "metal")
    return model


def steel_target():
    """A steel plate to shoot at, its face to the front, on a frame of timber whose legs reach back behind it.
    The origin is the foot of the plate's middle."""
    model = Model("steel_target", centred=False)
    plate = bmesh.new()
    cuboid(plate, -0.47, 0.47, -0.012, 0.012, 0.1, 2.05)
    model.hard(plate, "steel", bevel=0.004, segments=1)
    trim = bmesh.new()
    cuboid(trim, -0.48, 0.48, -0.02, 0.02, 2.04, 2.08)
    cuboid(trim, -0.48, 0.48, -0.02, -0.012, 1.04, 1.09)
    for side in (-1, 1):
        cuboid(trim, side * 0.47, side * 0.458, -0.02, -0.012, 0.1, 2.05)
    model.hard(trim, "metal", bevel=0.003, segments=1)
    wood = bmesh.new()
    for side in (-1, 1):
        x = side * 0.38
        cuboid(wood, x - 0.035, x + 0.035, -0.14, 0.52, 0.0, 0.07)
        cuboid(wood, x - 0.035, x + 0.035, 0.012, 0.08, 0.07, 1.2)
        beam(wood, (x, 0.05, 1.12), (x, 0.47, 0.06), 0.065, 0.065)
    cuboid(wood, -0.5, 0.5, -0.09, -0.012, 1.1, 1.18)
    cuboid(wood, -0.42, 0.42, 0.38, 0.45, 0.07, 0.14)
    model.hard(wood, "wood", bevel=0.006, segments=1)
    return model


def plank_shelves():
    """Open shelves of boards, a back of planks and five boards between two sides. Open to the front."""
    model = Model("plank_shelves")
    half, depth, top = 0.48, 0.19, 2.0
    wood = bmesh.new()
    for side in (-1, 1):
        cuboid(wood, side * half, side * (half - 0.025), -depth, depth, 0.0, top)
    for n in range(4):
        x = -half + 0.025 + n * (2 * half - 0.05) / 4
        cuboid(wood, x + 0.002, x + (2 * half - 0.05) / 4 - 0.002, depth - 0.02, depth, 0.0, top)
    for z in (0.06, 0.53, 1.02, 1.5, top - 0.025):
        cuboid(wood, -half + 0.025, half - 0.025, -depth, depth - 0.02, z, z + 0.025)
    model.hard(wood, "wood", bevel=0.004, segments=1)
    return model


def candle():
    """A candle in a dish."""
    model = Model("candle")
    dish = bmesh.new()
    lathe(dish, [(0.0, 0.0), (0.055, 0.0), (0.062, 0.012), (0.056, 0.014), (0.028, 0.016), (0.028, 0.03), (0.0, 0.03)], segments=14)
    model.turned(dish, "metal", sharp=50.0)
    wax = bmesh.new()
    lathe(wax, [(0.0, 0.03), (0.021, 0.03), (0.021, 0.15), (0.017, 0.156), (0.0, 0.156)], segments=12)
    model.turned(wax, "enamel", sharp=50.0)
    wick = bmesh.new()
    cuboid(wick, -0.002, 0.002, -0.002, 0.002, 0.156, 0.168)
    model.plain(wick, "rubber")
    return model


def frame_posts():
    """Two posts of a wall's frame a storey high, a square's length apart less a post."""
    model = Model("frame_posts")
    wood = bmesh.new()
    for side in (-1, 1):
        cuboid(wood, side * 0.39 - 0.045, side * 0.39 + 0.045, -0.045, 0.045, 0.0, 2.449)
    model.hard(wood, "wood", bevel=0.006, segments=1)
    return model


def cloth_table():
    """A long table of boards with a cloth over its top and a shelf of boards low down between its legs."""
    model = Model("cloth_table")
    half, end, top = 0.45, 0.95, 0.8
    wood = bmesh.new()
    for sx in (-1, 1):
        for sy in (-1, 1):
            cuboid(wood, sx * (half - 0.04) - 0.03, sx * (half - 0.04) + 0.03, sy * (end - 0.04) - 0.03, sy * (end - 0.04) + 0.03, 0.0, top)
    for n in range(5):
        x = -half + 0.07 + n * (2 * half - 0.14) / 5
        cuboid(wood, x + 0.003, x + (2 * half - 0.14) / 5 - 0.003, -end + 0.07, end - 0.07, 0.17, 0.2)
    model.hard(wood, "wood", bevel=0.005, segments=1)
    cloth = bmesh.new()
    cuboid(cloth, -half - 0.01, half + 0.01, -end - 0.01, end + 0.01, top, top + 0.018)
    for side in (-1, 1):
        cuboid(cloth, side * (half + 0.004), side * (half + 0.01), -end - 0.01, end + 0.01, top - 0.1, top)
        cuboid(cloth, -half - 0.01, half + 0.01, side * (end + 0.004), side * (end + 0.01), top - 0.1, top)
    model.hard(cloth, "linen", bevel=0.004, segments=1)
    return model


def live_trap(name, open_door):
    """A cage trap of steel bars, long to the front, its door at the front end: shut, or swung up where the trap is set. The origin is the middle of the cage."""
    model = Model(name, centred=False)
    half, end, high, bar = 0.21, 0.42, 0.36, 0.012
    cage = bmesh.new()
    for x in (-half, half):
        for y in (-end, end):
            cuboid(cage, x - bar, x + bar, y - bar, y + bar, 0.0, high)
    for z in (0.012, high - 0.012):
        for x in (-half, half):
            cuboid(cage, x - bar, x + bar, -end, end, z - bar, z + bar)
        cuboid(cage, -half, half, end - bar, end + bar, z - bar, z + bar)
        if z < 0.1:
            cuboid(cage, -half, half, -end - bar, -end + bar, z - bar, z + bar)
    for n in range(1, 8):
        y = -end + n * 2 * end / 8
        for x in (-half, half):
            cuboid(cage, x - 0.004, x + 0.004, y - 0.004, y + 0.004, 0.0, high)
        cuboid(cage, -half, half, y - 0.004, y + 0.004, high - 0.016, high - 0.008)
    for n in range(1, 5):
        x = -half + n * 2 * half / 5
        cuboid(cage, x - 0.004, x + 0.004, end - 0.004, end + 0.004, 0.0, high)
    cuboid(cage, -half, half, -end, end, 0.0, 0.006)
    model.hard(cage, "steel", bevel=0.002, segments=1)
    door = bmesh.new()
    hinge = Vector((0.0, -end, high))
    swing = Matrix.Translation(hinge) @ Matrix.Rotation(math.radians(-150 if open_door else 0), 4, "X") @ Matrix.Translation(-hinge)
    cuboid(door, -half, half, -end - bar, -end + bar, high - 0.02, high)
    for x in (-half + 0.01, half - 0.01):
        cuboid(door, x - bar, x + bar, -end - bar, -end + bar, 0.03, high)
    cuboid(door, -half, half, -end - bar, -end + bar, 0.03, 0.05)
    for n in range(1, 5):
        x = -half + n * 2 * half / 5
        cuboid(door, x - 0.004, x + 0.004, -end - 0.004, -end + 0.004, 0.05, high - 0.02)
    bmesh.ops.transform(door, matrix=swing, verts=door.verts[:])
    model.hard(door, "steel", bevel=0.002, segments=1)
    return model


def camp():
    """Tents, bedding and furniture of a camp, its fires, water and traps, crates and an army's target."""
    return [tent("ridge_tent", 0.88, 2.05, 1.15, True, 0.75), tent("pup_tent", 0.45, 1.8, 1.0, False, 0.0), sleeping_bag(), camp_mattress(), plank_bed(),
            tarp_shelter(), drying_rack(), plank_table(), plank_side_table(), slat_chair(), plank_chair(), box_chair(), campfire(), cooking_pit(),
            cooking_pit_simple(), wooden_bucket(), burn_barrel(), wooden_barrel(), stone_well(), steel_target(), plank_shelves(), candle(), frame_posts(),
            cloth_table(), live_trap("live_trap", False), live_trap("live_trap_set", True)]


# Dishes and aerials on roofs and walls, as Towers.java, WallObjects.java and Outdoors.java stand them.

def dish_shell(bm, radius, depth, thick, to, segments=32, rings=6):
    """A dish of a paraboloid, its rim radius round its axis and its middle depth behind the rim, thick through: turned
    about Z with its hollow to +Z and the middle of its rim at the origin, then moved by to."""
    front = [(radius * k / rings, -depth * (1 - (k / rings) ** 2)) for k in range(rings + 1)]
    back = [(r, z - thick) for r, z in reversed(front)]
    lathe(bm, front + back, segments=segments, caps=False, to=to)


def facing(way, at):
    """Moves something turned about Z so that its +Z points along way, and its origin to at."""
    way = Vector(way).normalized()
    return Matrix.Translation(at) @ Vector((0.0, 0.0, 1.0)).rotation_difference(way).to_matrix().to_4x4()


# The big dish on its pedestal as the game paints it over its four squares, read off its sprites drawn together: the
# middle of its rim from the pedestal's axis, the way it looks (16 degrees off the model's front, 32 up), and the bearing.
LARGE_DISH_AT = (-0.36, -1.06, 2.65)
LARGE_DISH_WAY = (-math.sin(math.radians(16)) * math.cos(math.radians(32)), -math.cos(math.radians(16)) * math.cos(math.radians(32)),
                  math.sin(math.radians(32)))
LARGE_DISH_HEAD = 1.82


def satellite_dish_large():
    """A dish 2.7 m across on a round pedestal: a foot, a steel column, a bearing head with its axle across the way the
    dish looks, struts from the head to the back of the dish, and three struts in from the rim holding the feed at the
    focus. The origin is the pedestal's axis on the ground."""
    model = Model("satellite_dish_large", centred=False)
    way, middle = Vector(LARGE_DISH_WAY).normalized(), Vector(LARGE_DISH_AT)
    radius, depth, focus = 1.35, 0.38, 1.2
    foot = bmesh.new()
    lathe(foot, [(0.0, 0.0), (0.3, 0.0), (0.3, 0.08), (0.24, 0.12), (0.24, 0.6), (0.0, 0.6)], segments=24)
    model.turned(foot, "metal", sharp=30.0)
    column = bmesh.new()
    lathe(column, [(0.0, 0.6), (0.165, 0.6), (0.165, LARGE_DISH_HEAD - 0.12), (0.0, LARGE_DISH_HEAD - 0.12)], segments=24)
    model.turned(column, "steel", sharp=30.0)
    head = bmesh.new()
    cuboid(head, -0.2, 0.2, -0.2, 0.2, LARGE_DISH_HEAD - 0.14, LARGE_DISH_HEAD + 0.1)
    model.hard(head, "steel", bevel=0.02, segments=1)
    across = Vector((-way.y, way.x, 0.0)).normalized()
    centre = Vector((0.0, 0.0, LARGE_DISH_HEAD))
    axle = bmesh.new()
    rod(axle, tuple(centre - across * 0.3), tuple(centre + across * 0.3), 0.11, segments=16)
    model.turned(axle, "metal")
    dish = bmesh.new()
    dish_shell(dish, radius, depth, 0.025, facing(way, middle), segments=40)
    model.turned(dish, "enamel", sharp=60.0)
    rim = bmesh.new()
    lathe(rim, [(radius - 0.01, -0.04), (radius + 0.03, -0.04), (radius + 0.03, 0.01), (radius - 0.01, 0.01)], segments=40,
          to=facing(way, middle))
    model.turned(rim, "enamel", sharp=30.0)
    vertex = middle - way * depth
    up = way.cross(across).normalized()
    frame = bmesh.new()
    for side in (-1, 1):
        for lift in (-1, 1):
            mount = vertex + across * side * 0.55 + up * lift * 0.55 + way * (depth * (0.55 * math.sqrt(2) / radius) ** 2 - 0.03)
            strut(frame, tuple(centre + across * side * 0.25), tuple(mount), 0.06)
    strut(frame, tuple(centre), tuple(vertex - way * 0.03), 0.09)
    model.plain(frame, "steel")
    feed = bmesh.new()
    point = vertex + way * focus
    for k in range(3):
        a = math.radians(90 + 120 * k)
        on_rim = middle + (across * math.cos(a) + up * math.sin(a)) * (radius - 0.04)
        rod(feed, tuple(on_rim), tuple(point - way * 0.05), 0.022, segments=8)
    rod(feed, tuple(point - way * 0.12), tuple(point + way * 0.1), 0.07, segments=12)
    model.turned(feed, "metal")
    return model


# A house's dish on its wall, the wall at +Y: how high the middle of its wall plate is, and of its rim, over the floor.
SMALL_DISH_PLATE, SMALL_DISH_MIDDLE = 1.0, 1.34


def satellite_dish(name="satellite_dish", post=False):
    """A house's satellite dish 0.8 m across on a wall bracket: a plate on the wall, an arm out from it to the back of
    the dish, the dish looking out and a little up, and an arm up from its lower rim holding the receiver at the focus.
    With post, the plate is on a post down to a foot on the roof, for a dish the map puts on a roof."""
    model = Model(name)
    radius, depth = 0.4, 0.07
    way = Vector((0.0, -math.cos(math.radians(14)), math.sin(math.radians(14))))
    middle = Vector((0.0, -0.3, SMALL_DISH_MIDDLE))
    plate = bmesh.new()
    cuboid(plate, -0.07, 0.07, -0.012, 0.0, SMALL_DISH_PLATE - 0.1, SMALL_DISH_PLATE + 0.1)
    model.hard(plate, "metal", bevel=0.004, segments=1)
    vertex = middle - way * depth
    arm = bmesh.new()
    strut(arm, (0.0, -0.012, SMALL_DISH_PLATE), (0.0, vertex.y + 0.08, SMALL_DISH_PLATE), 0.035)
    strut(arm, (0.0, vertex.y + 0.08, SMALL_DISH_PLATE), tuple(vertex - way * 0.02), 0.035)
    model.plain(arm, "metal")
    dish = bmesh.new()
    dish_shell(dish, radius, depth, 0.012, facing(way, middle), segments=32, rings=5)
    model.turned(dish, "enamel", sharp=60.0)
    feed = bmesh.new()
    point = vertex + way * 0.5
    low = middle - way.cross(Vector((1.0, 0.0, 0.0))).normalized() * (radius - 0.03)
    rod(feed, tuple(low), tuple(point), 0.012, segments=6)
    rod(feed, tuple(point - way * 0.03), tuple(point + way * 0.07), 0.03, segments=10)
    model.turned(feed, "plastic")
    if post:
        foot = bmesh.new()
        cuboid(foot, -0.1, 0.1, -0.2, 0.0, 0.0, 0.012)
        model.hard(foot, "metal", bevel=0.003, segments=1)
        stand = bmesh.new()
        rod(stand, (0.0, -0.04, 0.01), (0.0, -0.04, SMALL_DISH_PLATE + 0.08), 0.024, segments=10)
        model.turned(stand, "metal")
    return model


# A television aerial on a roof: how high its mast is, and the two booms across its top, along X and along Y.
AERIAL_MAST, AERIAL_BOOMS = 1.66, (1.6, 1.46)


def tv_aerial():
    """A rooftop television aerial: a mast on a foot plate with a raking stay to a second plate behind it, at +Y, and on
    the mast two yagis at right angles, each a boom with its elements across it, longest at one end. The origin is the
    foot of the mast."""
    model = Model("tv_aerial", centred=False)
    plates = bmesh.new()
    cuboid(plates, -0.08, 0.08, -0.08, 0.08, 0.0, 0.015)
    cuboid(plates, -0.05, 0.05, 0.2, 0.32, 0.0, 0.012)
    model.hard(plates, "metal", bevel=0.003, segments=1)
    tubes = bmesh.new()
    rod(tubes, (0.0, 0.0, 0.01), (0.0, 0.0, AERIAL_MAST), 0.022, segments=10)
    rod(tubes, (0.0, 0.26, 0.012), (0.0, 0.02, 0.62), 0.014, segments=8)
    model.turned(tubes, "metal")
    elements = bmesh.new()
    for along_x, z, start, end in ((True, AERIAL_BOOMS[0], -0.95, 0.75), (False, AERIAL_BOOMS[1], -0.8, 0.95)):
        a, b = (Vector((start, 0.0, z)), Vector((end, 0.0, z))) if along_x else (Vector((0.0, start, z)), Vector((0.0, end, z)))
        rod(elements, tuple(a), tuple(b), 0.011, segments=6)
        count = 11
        for k in range(count):
            at = a + (b - a) * k / (count - 1)
            half = 0.42 - 0.22 * k / (count - 1)
            side = Vector((0.0, half, 0.0)) if along_x else Vector((half, 0.0, 0.0))
            rod(elements, tuple(at - side), tuple(at + side), 0.005, segments=4)
    model.turned(elements, "metal")
    return model


def aerials():
    """Dishes and aerials: what the game paints on roofs and walls to take in a broadcast."""
    return [satellite_dish_large(), satellite_dish(), satellite_dish("satellite_dish_post", True), tv_aerial()]

def tapered_along_x(bm, outline, half, grow):
    """A body with the same outline, given as (y, z) going round, whose ends lean out as it rises: each end is
    half + grow * z from x = 0, which keeps both ends flat."""
    left = [bm.verts.new((-half - grow * z, y, z)) for y, z in outline]
    right = [bm.verts.new((half + grow * z, y, z)) for y, z in outline]
    count = len(outline)
    for i in range(count):
        j = (i + 1) % count
        bm.faces.new((left[i], left[j], right[j], right[i]))
    bm.faces.new(left[::-1])
    bm.faces.new(right)


def eight(bm, corners):
    """A block from its eight corners: the four of its bottom going round, then the four over them in the same order."""
    v = [bm.verts.new(c) for c in corners]
    bm.faces.new((v[3], v[2], v[1], v[0]))
    bm.faces.new((v[4], v[5], v[6], v[7]))
    for i in range(4):
        j = (i + 1) % 4
        bm.faces.new((v[i], v[j], v[j + 4], v[i + 4]))


def leaning(bm, x0, x1, y0, y1, z0, z1, grow):
    """A block on an end of a body made by tapered_along_x, x0 and x1 measured out from that end: it leans out with it."""
    side = 1 if x1 > 0 else -1
    at = lambda x, z: side * (abs(x) + grow * z)
    eight(bm, [(at(x0, z0), y0, z0), (at(x1, z0), y0, z0), (at(x1, z0), y1, z0), (at(x0, z0), y1, z0),
               (at(x0, z1), y0, z1), (at(x1, z1), y0, z1), (at(x1, z1), y1, z1), (at(x0, z1), y1, z1)])


# The game's blue dumpster: a steel bin 1.2 high at its front and 1.3 at its back, its front slanting in below the knee,
# under two lids that slope down to the front. 1.8 long over its fork pockets, 0.95 deep with its lids.
SLOPED_FOOT, SLOPED_HALF, SLOPED_GROW = 0.10, 0.80, 0.03
SLOPED_FRONT, SLOPED_BACK, SLOPED_KNEE, SLOPED_TOE = -0.44, 0.42, 0.55, -0.29
SLOPED_TOP_FRONT, SLOPED_TOP_BACK = 1.20, 1.30


def sloped_top(y):
    """How high the top of the blue dumpster's bin is at y."""
    return SLOPED_TOP_FRONT + (SLOPED_TOP_BACK - SLOPED_TOP_FRONT) * (y - SLOPED_FRONT) / (SLOPED_BACK - SLOPED_FRONT)


def dumpster_sloped():
    """The game's blue dumpster, one model over its two squares: a painted steel bin whose ends lean out a little as they
    rise and whose front slants in below the knee, ribbed across its front and back and up its ends, a pocket for a
    lorry's forks on each end, a lip round its top, two steel runners under it, and two plastic lids sloping to the front,
    each with three ridges, a handle at its front edge and two hinges at its back."""
    model = Model("dumpster_sloped")
    half = lambda z: SLOPED_HALF + SLOPED_GROW * z
    body = bmesh.new()
    tapered_along_x(body, [(SLOPED_TOE, SLOPED_FOOT), (SLOPED_BACK, SLOPED_FOOT), (SLOPED_BACK, SLOPED_TOP_BACK),
                           (SLOPED_FRONT, SLOPED_TOP_FRONT), (SLOPED_FRONT, SLOPED_KNEE)], SLOPED_HALF, SLOPED_GROW)
    lip_front, lip_back = SLOPED_FRONT - 0.025, SLOPED_BACK + 0.025
    tapered_along_x(body, [(lip_front, sloped_top(lip_front) - 0.08), (lip_back, sloped_top(lip_back) - 0.08),
                           (lip_back, sloped_top(lip_back) + 0.01), (lip_front, sloped_top(lip_front) + 0.01)], SLOPED_HALF + 0.02, SLOPED_GROW)
    # Ribs across the front above the knee and along the knee, and across the back.
    for z in (0.78, 0.99):
        tapered_along_x(body, [(SLOPED_FRONT - 0.022, z - 0.03), (SLOPED_FRONT + 0.01, z - 0.03), (SLOPED_FRONT + 0.01, z + 0.03),
                               (SLOPED_FRONT - 0.022, z + 0.03)], SLOPED_HALF - 0.025, SLOPED_GROW)
    tapered_along_x(body, [(SLOPED_FRONT - 0.022, SLOPED_KNEE - 0.04), (SLOPED_FRONT + 0.03, SLOPED_KNEE - 0.06),
                           (SLOPED_FRONT + 0.03, SLOPED_KNEE + 0.03), (SLOPED_FRONT - 0.022, SLOPED_KNEE + 0.03)], SLOPED_HALF - 0.025, SLOPED_GROW)
    for z in (0.42, 0.88):
        tapered_along_x(body, [(SLOPED_BACK - 0.01, z - 0.03), (SLOPED_BACK + 0.022, z - 0.03), (SLOPED_BACK + 0.022, z + 0.03),
                               (SLOPED_BACK - 0.01, z + 0.03)], SLOPED_HALF - 0.025, SLOPED_GROW)
    for side in (-1, 1):
        for y in (-0.3, 0.3):
            leaning(body, side * (SLOPED_HALF - 0.01), side * (SLOPED_HALF + 0.022), y - 0.03, y + 0.03, SLOPED_FOOT + 0.03, 1.15, SLOPED_GROW)
        # The fork pocket: a sleeve on the end, open at front and back. Its hollow is drawn dark inside it.
        leaning(body, side * (SLOPED_HALF - 0.01), side * (SLOPED_HALF + 0.075), -0.25, 0.25, 0.6, 0.62, SLOPED_GROW)
        leaning(body, side * (SLOPED_HALF - 0.01), side * (SLOPED_HALF + 0.075), -0.25, 0.25, 0.8, 0.82, SLOPED_GROW)
        leaning(body, side * (SLOPED_HALF + 0.063), side * (SLOPED_HALF + 0.075), -0.25, 0.25, 0.6, 0.82, SLOPED_GROW)
    model.hard(body, "paint", bevel=0.006, segments=1)
    hollow = bmesh.new()
    for side in (-1, 1):
        leaning(hollow, side * (SLOPED_HALF + 0.005), side * (SLOPED_HALF + 0.063), -0.245, 0.245, 0.62, 0.8, SLOPED_GROW)
    model.plain(hollow, "rubber")
    runners = bmesh.new()
    for y in (-0.2, 0.32):
        cuboid(runners, -SLOPED_HALF + 0.06, SLOPED_HALF - 0.06, y - 0.04, y + 0.04, 0.0, SLOPED_FOOT + 0.005)
    model.hard(runners, "metal", bevel=0.004, segments=1)
    lids = bmesh.new()
    front, back = lip_front - 0.02, lip_back
    under = lambda y: sloped_top(y) + 0.012
    for side in (-1, 1):
        a, b = sorted((side * 0.008, side * (half(1.25) + 0.02)))
        eight(lids, [(a, front, under(front)), (b, front, under(front)), (b, back, under(back)), (a, back, under(back)),
                     (a, front, under(front) + 0.04), (b, front, under(front) + 0.04), (b, back, under(back) + 0.04), (a, back, under(back) + 0.04)])
        # The lid's front edge turns down over the lip.
        eight(lids, [(a, front, under(front) - 0.07), (b, front, under(front) - 0.07), (b, front + 0.025, under(front) - 0.07), (a, front + 0.025, under(front) - 0.07),
                     (a, front, under(front) + 0.02), (b, front, under(front) + 0.02), (b, front + 0.025, under(front) + 0.02), (a, front + 0.025, under(front) + 0.02)])
        for k in (-1, 0, 1):
            x = (a + b) / 2 + k * (b - a) * 0.3
            f, r = front + 0.06, back - 0.06
            eight(lids, [(x - 0.035, f, under(f) + 0.035), (x + 0.035, f, under(f) + 0.035), (x + 0.035, r, under(r) + 0.035), (x - 0.035, r, under(r) + 0.035),
                         (x - 0.025, f, under(f) + 0.062), (x + 0.025, f, under(f) + 0.062), (x + 0.025, r, under(r) + 0.062), (x - 0.025, r, under(r) + 0.062)])
    model.hard(lids, "plastic", bevel=0.008, segments=2)
    fittings = bmesh.new()
    for side in (-1, 1):
        middle = side * (half(1.25) + 0.028) / 2
        z = under(front) - 0.025
        cuboid(fittings, middle - 0.1, middle + 0.1, front - 0.045, front - 0.03, z - 0.012, z + 0.012)
        for x in (middle - 0.09, middle + 0.09):
            cuboid(fittings, x - 0.012, x + 0.012, front - 0.045, front, z - 0.012, z + 0.012)
        for x in (middle - 0.3, middle + 0.3):
            lathe(fittings, [(0.0, -0.06), (0.022, -0.06), (0.022, 0.06), (0.0, 0.06)], segments=10, to=along_x(x, back + 0.012, under(back) + 0.012))
    model.turned(fittings, "metal", sharp=35.0)
    return model


# The game's orange dumpster: a straight steel box framed in ribs, 1.02 high, under two lids that each arch across the
# half of the top it closes, up to the 1.17 the game says things stand on it at.
DOMED_FOOT, DOMED_TOP, DOMED_CROWN = 0.08, 1.02, 1.17
DOMED_HALF, DOMED_GROW, DOMED_DEEP = 0.835, 0.03, 0.44


def arch(y0, y1, z0, rise, thick, steps=6):
    """The outline, as (y, z) going round, of a shell arched from (y0, z0) to (y1, z0), rising by rise at its middle and
    thick through."""
    middle, reach = (y0 + y1) / 2, (y1 - y0) / 2
    outside, inside = [], []
    for i in range(steps + 1):
        t = -1 + 2 * i / steps
        up = (1 - t * t) ** 0.5
        outside.append((middle + t * reach, z0 + thick + rise * up))
        inside.append((middle + t * reach, z0 + (rise - thick) * up))
    return outside + inside[::-1]


def dumpster_domed():
    """The game's orange dumpster, one model over its two squares: a painted steel box, its ends leaning out a touch as
    they rise, framed with posts and rails standing proud of its sides, on a steel base, under two black lids that meet
    along its middle and each arch across the half of the top it closes, a rib arched over each a third of a metre apart."""
    model = Model("dumpster_domed")
    half = lambda z: DOMED_HALF + DOMED_GROW * z
    proud = 0.022
    body = bmesh.new()
    tapered_along_x(body, [(-DOMED_DEEP + 0.01, DOMED_FOOT), (DOMED_DEEP - 0.01, DOMED_FOOT), (DOMED_DEEP + 0.01, DOMED_TOP),
                           (-DOMED_DEEP - 0.01, DOMED_TOP)], DOMED_HALF, DOMED_GROW)
    # The rails round it: at its foot, twice up its sides, and the lip at its top.
    for z0, z1 in ((DOMED_FOOT, DOMED_FOOT + 0.07), (0.36, 0.42), (0.68, 0.74), (DOMED_TOP - 0.06, DOMED_TOP + 0.005)):
        reach = DOMED_DEEP + 0.01 + proud
        tapered_along_x(body, [(-reach, z0), (reach, z0), (reach, z1), (-reach, z1)], DOMED_HALF + proud, DOMED_GROW)
    # The posts up its front and back: at the corners and a third of the way in.
    for side in (-1, 1):
        y0, y1 = sorted((side * DOMED_DEEP, side * (DOMED_DEEP + 0.012 + proud)))
        for x in (-0.3, 0.3):
            cuboid(body, x - 0.035, x + 0.035, y0, y1, DOMED_FOOT, DOMED_TOP)
        for end in (-1, 1):
            leaning(body, end * (DOMED_HALF - 0.07), end * (DOMED_HALF + proud), y0, y1, DOMED_FOOT, DOMED_TOP, DOMED_GROW)
        # And one up the middle of each end.
        leaning(body, side * (DOMED_HALF - 0.01), side * (DOMED_HALF + proud), -0.035, 0.035, DOMED_FOOT, DOMED_TOP, DOMED_GROW)
    model.hard(body, "paint", bevel=0.005, segments=1)
    base = bmesh.new()
    for y in (-DOMED_DEEP + 0.06, DOMED_DEEP - 0.06):
        cuboid(base, -DOMED_HALF + 0.02, DOMED_HALF - 0.02, y - 0.045, y + 0.045, 0.0, DOMED_FOOT + 0.005)
    for x in (-DOMED_HALF + 0.06, 0.0, DOMED_HALF - 0.06):
        cuboid(base, x - 0.04, x + 0.04, -DOMED_DEEP + 0.03, DOMED_DEEP - 0.03, 0.0, DOMED_FOOT)
    model.hard(base, "metal", bevel=0.004, segments=1)
    ends = half(DOMED_TOP) + proud + 0.01
    rise = DOMED_CROWN - DOMED_TOP - 0.045
    lids, ribs = bmesh.new(), bmesh.new()
    count = 6
    for side in (-1, 1):
        y0, y1 = sorted((side * 0.006, side * (DOMED_DEEP + 0.04)))
        outline_along_x(lids, arch(y0, y1, DOMED_TOP + 0.005, rise, 0.02), -ends, ends)
        for k in range(count):
            x = -ends + 0.13 + k * (2 * ends - 0.26) / (count - 1)
            outline_along_x(ribs, arch(y0 + 0.025, y1 - 0.025, DOMED_TOP + 0.005, rise + 0.02, 0.025), x - 0.03, x + 0.03)
    model.hard(lids, "plastic", bevel=0.006, segments=1)
    model.hard(ribs, "plastic", bevel=0.006, segments=1)
    return model


def dumpsters():
    """The game's two dumpsters, each one model over its two squares."""
    return [dumpster_sloped(), dumpster_domed()]

# A bar's panelling: how high the top of its rail is, low and tall, how thick the rail is and the radius of its rounded
# nose, how far the nose's straight top runs out from the back, and how far in front of the panels a tall one's foot rail runs.
WAINSCOT_TOP, BAR_PANEL_TOP, WAINSCOT_RAIL, WAINSCOT_NOSE, WAINSCOT_LEDGE, FOOT_RAIL_OUT = 0.88, 1.13, 0.075, 0.0375, 0.12, 0.17


def wainscot_run(model, x0, x1, top, tall, place=None, butted=False, pilaster=None):
    """One length of a bar's panelling from x0 to x1 along x, its back at y 0 and its face to -y: boards, or framed
    raised panels when tall, on a skirting, a pilaster at x pilaster (the middle if None) with a carved scroll under a
    rounded rail, and a brass foot rail before a tall one. place moves every part afterwards, for a length that runs
    another way. butted ends each layer at x1 less its own depth, where it meets the same layer of a length along x1's edge."""
    mid = (x0 + x1) / 2 if pilaster is None else pilaster
    under = top - WAINSCOT_RAIL

    def end(depth):
        return x1 - depth if butted else x1

    parts = {name: bmesh.new() for name in ("boards", "fields", "trim", "rail", "carving", "brass")}
    cuboid(parts["trim"], x0, end(0.05), -0.05, 0.0, 0.0, 0.09)
    cuboid(parts["trim"], x0, end(0.06), -0.06, 0.0, under - 0.025, under)
    if tall:
        cuboid(parts["boards"], x0, end(0.02), -0.02, 0.0, 0.09, under - 0.025)
        for z0, z1 in ((0.09, 0.15), (0.55, 0.6), (under - 0.105, under - 0.025)):
            cuboid(parts["trim"], x0, x1, -0.035, -0.02, z0, z1)
        for z0, z1 in ((0.15, 0.55), (0.6, under - 0.105)):
            cuboid(parts["trim"], x0, x0 + 0.03, -0.035, -0.02, z0, z1)
            cuboid(parts["trim"], x1 - 0.03, x1, -0.035, -0.02, z0, z1)
            for a, b in ((x0 + 0.03, mid - 0.035), (mid + 0.035, x1 - 0.03)):
                cx, cz, sx, sz = (a + b) / 2, (z0 + z1) / 2, b - a - 0.004, z1 - z0 - 0.004
                prism_y(parts["fields"], (cx, cz, sx - 0.07, sz - 0.07, -0.034), (cx, cz, sx, sz, -0.02))
        for x in ((x0 + x1) / 2 - 0.25, (x0 + x1) / 2 + 0.25):
            cuboid(parts["brass"], x - 0.01, x + 0.01, -FOOT_RAIL_OUT, -0.035, 0.105, 0.135)
        lathe(parts["brass"], [(0.022, x0), (0.022, x1)], segments=14, to=along_x(0.0, -FOOT_RAIL_OUT, 0.12))
    else:
        boards = 9
        width = (end(0.03) - x0) / boards
        for n in range(boards):
            cuboid(parts["boards"], x0 + n * width, x0 + (n + 1) * width, -0.03, 0.0, 0.09, under - 0.025)
    cuboid(parts["trim"], mid - 0.035, mid + 0.035, -0.055, -0.02, 0.09, under - 0.1)
    cuboid(parts["trim"], mid - 0.05, mid + 0.05, -0.085, -0.02, 0.0, 0.13)
    # The scroll under the rail: a bracket that bellies out from the pilaster's head to under the rail's nose, with a
    # curl near its top and one at its foot.
    belly = [(-0.055 - 0.05 * t * t * (3 - 2 * t), under - 0.125 + 0.1 * t) for t in (k / 8 for k in range(9))]
    outline_along_x(parts["carving"], [(-0.02, under - 0.125), (-0.02, under - 0.025)] + belly[::-1], mid - 0.025, mid + 0.025)
    for y, z, radius in ((-0.085, under - 0.05, 0.02), (-0.062, under - 0.11, 0.016)):
        lathe(parts["carving"], [(radius, mid - 0.028), (radius, mid + 0.028)], segments=12, to=along_x(0.0, y, z))
    nose = [(-WAINSCOT_LEDGE - WAINSCOT_NOSE * math.sin(math.pi * k / 8), top - WAINSCOT_NOSE - WAINSCOT_NOSE * math.cos(math.pi * k / 8))
            for k in range(9)]
    outline_along_x(parts["rail"], [(0.0, under)] + nose + [(0.0, top)], x0, end(WAINSCOT_LEDGE + WAINSCOT_NOSE))
    if place is not None:
        for bm in parts.values():
            bmesh.ops.transform(bm, matrix=place, verts=bm.verts[:])
    model.hard(parts["boards"], "wood", bevel=0.004, segments=1)
    model.hard(parts["trim"], "wood", bevel=0.004, segments=1)
    if tall:
        model.hard(parts["fields"], "wood", bevel=0.003, segments=1)
        model.turned(parts["brass"], "metal", sharp=50.0)
    else:
        parts["fields"].free()
        parts["brass"].free()
    model.turned(parts["rail"], "wood", sharp=35.0)
    model.turned(parts["carving"], "wood", sharp=50.0)


# Where the game's sprites paint each run's pilaster, along the model's x from the middle of its square: facing south, on
# a square's north edge, x is the map's east; facing east, on its west edge, x is the map's north. Read off the sprites'
# plinths and the pilaster's sides on their face: the low run at 0.45 of the north edge and 0.51 of the west, the tall
# one at 0.50 and 0.54.
PILASTER_NORTH, PILASTER_WEST, TALL_PILASTER_NORTH, TALL_PILASTER_WEST = -0.05, -0.01, 0.0, -0.04


def wainscot(name, top, tall, pilaster):
    """A square's length of a bar's panelling against a wall, a metre long so that each meets the next."""
    model = Model(name)
    wainscot_run(model, -0.5, 0.5, top, tall, pilaster=pilaster)
    return model


def wainscot_corner():
    """Where two runs of a bar's low panelling meet, on one square: along its back and the side on its left as it is
    looked at, each standing off its edge as far as a straight length is stood off it, its pilaster where a straight
    length along that edge has its own."""
    model = Model("wainscot_north_west", centred=False)
    back = 0.44
    wainscot_run(model, -back, 0.5, WAINSCOT_TOP, False, place=Matrix.Translation((0.0, back, 0.0)), pilaster=PILASTER_NORTH)
    wainscot_run(model, -0.5, back, WAINSCOT_TOP, False, butted=True,
                 place=Matrix.Translation((-back, 0.0, 0.0)) @ Matrix.Rotation(math.radians(90), 4, "Z"), pilaster=PILASTER_WEST)
    return model


def wainscot_post():
    """The square post that ends a run of a bar's low panelling."""
    model = Model("wainscot_post")
    post = bmesh.new()
    cuboid(post, -0.035, 0.035, -0.035, 0.035, 0.11, WAINSCOT_TOP)
    cuboid(post, -0.045, 0.045, -0.045, 0.045, 0.0, 0.11)
    cuboid(post, -0.048, 0.048, -0.048, 0.048, WAINSCOT_TOP, WAINSCOT_TOP + 0.03)
    model.hard(post, "wood", bevel=0.005, segments=2)
    return model


def bar_panelling():
    """A bar's wooden panelling: low runs with a rail along their top, their corner and the post at the end of a run, and
    tall runs with a foot rail before them."""
    return [wainscot("wainscot_north", WAINSCOT_TOP, False, PILASTER_NORTH), wainscot("wainscot_west", WAINSCOT_TOP, False, PILASTER_WEST),
            wainscot("bar_panelling", BAR_PANEL_TOP, True, TALL_PILASTER_NORTH), wainscot("bar_panelling_west", BAR_PANEL_TOP, True, TALL_PILASTER_WEST),
            wainscot_corner(), wainscot_post()]


# Sport: what a playing field, a boxing ring and a gym have, each measured off the game's sprites of it.


# A boxing ring: how high each of its four ropes is, top first (red, blue, red, blue), how high and thick its corner posts
# are, where along a square's length of rope the strap that ties the ropes together is, and its steps: how far out from
# the corner post the outer post stands, how high that is and the rungs between them.
RING_ROPES = ((1.67, "vinyl"), (1.37, "paint"), (1.06, "vinyl"), (0.75, "paint"))
RING_POST_TOP, RING_POST_RADIUS, RING_ROPE_RADIUS, RING_TIE = 1.86, 0.11, 0.022, -0.08
RING_STEPS_OUT, RING_STEPS_TOP, RING_RUNGS = 1.1, 1.43, (0.49, 0.79, 1.11, 1.41)


def ring_post_into(model):
    post = bmesh.new()
    lathe(post, [(0.0, 0.0), (RING_POST_RADIUS + 0.02, 0.0), (RING_POST_RADIUS + 0.02, 0.06), (RING_POST_RADIUS, 0.08),
                 (RING_POST_RADIUS, RING_POST_TOP - 0.05), (RING_POST_RADIUS - 0.03, RING_POST_TOP), (0.0, RING_POST_TOP)], segments=16)
    model.turned(post, "enamel", sharp=50.0)
    # The ropes are made fast to the post through a turnbuckle at each.
    buckles = bmesh.new()
    for z, _ in RING_ROPES:
        lathe(buckles, [(RING_POST_RADIUS + 0.012, z - 0.04), (RING_POST_RADIUS + 0.012, z + 0.04)], segments=16)
    model.turned(buckles, "steel", sharp=50.0)


def ring_ropes_into(model, x0, x1, y0, y1):
    """The four ropes from (x0, y0) to (x1, y1) at their heights, each of its colour."""
    by = {}
    for z, surface in RING_ROPES:
        rod(by.setdefault(surface, bmesh.new()), (x0, y0, z), (x1, y1, z), RING_ROPE_RADIUS, segments=8)
    for surface, part in by.items():
        model.turned(part, surface)


def ring_post():
    """A boxing ring's corner post with the turnbuckles its ropes are made fast to."""
    model = Model("ring_post", centred=False)
    ring_post_into(model)
    return model


def ring_ropes(name, tied):
    """A square's length of a boxing ring's ropes along the model's X, round its origin, and on some a strap down them
    that ties them together."""
    model = Model(name, centred=False)
    ring_ropes_into(model, -0.5, 0.5, 0.0, 0.0)
    if tied:
        strap = bmesh.new()
        top, bottom = RING_ROPES[0][0], RING_ROPES[-1][0]
        cuboid(strap, RING_TIE - 0.025, RING_TIE + 0.025, -RING_ROPE_RADIUS - 0.006, RING_ROPE_RADIUS + 0.006, bottom - RING_ROPE_RADIUS, top + 0.04)
        model.hard(strap, "enamel", bevel=0.004, segments=1)
    return model


def ring_corner():
    """The corner post at the back of a ring and the first square's length of rope from it along +X and along -Y, which
    the game paints with the post."""
    model = Model("ring_corner", centred=False)
    ring_post_into(model)
    ring_ropes_into(model, RING_POST_RADIUS, 1.0, 0.0, 0.0)
    ring_ropes_into(model, 0.0, 0.0, -RING_POST_RADIUS, -1.0)
    return model


def ring_steps():
    """A corner post of a ring with the steps up to it: a lower post out along -X and four rungs between the two."""
    model = Model("ring_steps", centred=False)
    ring_post_into(model)
    frame = bmesh.new()
    lathe(frame, [(0.0, 0.0), (0.06, 0.0), (0.06, RING_STEPS_TOP - 0.03), (0.04, RING_STEPS_TOP), (0.0, RING_STEPS_TOP)],
          cx=-RING_STEPS_OUT, segments=12)
    for z in RING_RUNGS:
        rod(frame, (-RING_STEPS_OUT, 0.0, z), (-RING_POST_RADIUS, 0.0, z), 0.026, segments=8)
    model.turned(frame, "enamel", sharp=50.0)
    return model


# An American football goalpost: its post on the ground, padded from the foot, up to the crossbar a level up. How high the
# crossbar is over the level the game paints it on, how high the uprights rise over that level, how thick each is, and how
# far up the pad on the post reaches.
GOALPOST_BAR, GOALPOST_TOP, GOALPOST_RADIUS, GOALPOST_PAD = 0.22, 2.33, 0.07, 1.65


def goalpost_post():
    """The post a goalpost stands on, from the ground to its crossbar a level up, padded up to the height of a man."""
    model = Model("goalpost_post")
    post = bmesh.new()
    lathe(post, [(0.0, 0.0), (GOALPOST_RADIUS, 0.0), (GOALPOST_RADIUS, 2.45 + GOALPOST_BAR)], segments=14)
    model.turned(post, "paint", sharp=50.0)
    pad = bmesh.new()
    lathe(pad, [(0.0, 0.0), (0.1, 0.0), (0.1, GOALPOST_PAD - 0.03), (0.085, GOALPOST_PAD), (GOALPOST_RADIUS, GOALPOST_PAD)], segments=16)
    model.turned(pad, "cloth", sharp=40.0)
    return model


def goalpost_bar():
    """A square's length of a goalpost's crossbar along the model's X, round its origin."""
    model = Model("goalpost_bar", centred=False)
    bar = bmesh.new()
    rod(bar, (-0.5, 0.0, GOALPOST_BAR), (0.5, 0.0, GOALPOST_BAR), GOALPOST_RADIUS, segments=14)
    model.turned(bar, "paint", sharp=50.0)
    return model


def goalpost_end(name, upright, first):
    """The end of a goalpost's crossbar and the upright that rises from it at X = upright, the bar running on from it to
    the square's edge at +X on the first square of the run and at -X on the last."""
    model = Model(name, centred=False)
    bend = 0.16
    frame = bmesh.new()
    away = 1.0 if first else -1.0
    edge = 0.5 * away
    rod(frame, (upright + away * bend, 0.0, GOALPOST_BAR), (edge, 0.0, GOALPOST_BAR), GOALPOST_RADIUS, segments=14)
    # The elbow, a quarter round from the bar up into the upright.
    steps = 6
    points = [(upright + away * bend * (1.0 - math.sin(math.pi / 2 * k / steps)), GOALPOST_BAR + bend * (1.0 - math.cos(math.pi / 2 * k / steps)))
              for k in range(steps + 1)]
    for (x0, z0), (x1, z1) in zip(points, points[1:]):
        rod(frame, (x0, 0.0, z0), (x1, 0.0, z1), GOALPOST_RADIUS, segments=14)
    rod(frame, (upright, 0.0, GOALPOST_BAR + bend), (upright, 0.0, GOALPOST_TOP), GOALPOST_RADIUS * 0.85, segments=14)
    model.turned(frame, "paint", sharp=50.0)
    return model


def golf_flag():
    """The flag in a golf hole: a thin pole banded black and white with a red pennant at its top, in the cup's white rim."""
    model = Model("golf_flag", centred=False)
    top, bands = 2.2, 9
    white, black = bmesh.new(), bmesh.new()
    for k in range(bands):
        z0, z1 = 0.02 + (top - 0.02) * k / bands, 0.02 + (top - 0.02) * (k + 1) / bands
        lathe(white if k % 2 == 0 else black, [(0.0, z0), (0.012, z0), (0.012, z1), (0.0, z1)], segments=8)
    model.turned(white, "enamel", sharp=50.0)
    model.turned(black, "rubber", sharp=50.0)
    cup = bmesh.new()
    lathe(cup, [(0.054, 0.0), (0.066, 0.0), (0.066, 0.012), (0.054, 0.012)], segments=20, closed=True)
    model.turned(cup, "enamel", sharp=50.0)
    hole = bmesh.new()
    lathe(hole, [(0.0, 0.001), (0.054, 0.001), (0.054, 0.004), (0.0, 0.004)], segments=20)
    model.plain(hole, "rubber")
    flag = bmesh.new()
    outline_along_y(flag, [(0.012, top - 0.48), (0.3, top - 0.34), (0.3, top - 0.2), (0.012, top - 0.02)], -0.004, 0.004)
    model.hard(flag, "vinyl", bevel=0.002, segments=1)
    return model


def base_bag():
    """A bag on a corner of a baseball diamond: a white square cushion."""
    model = Model("base_bag")
    bag = bmesh.new()
    cuboid(bag, -0.19, 0.19, -0.19, 0.19, 0.0, 0.09)
    model.hard(bag, "enamel", bevel=0.025, segments=2)
    return model


def slab(bm, outline, z0, z1):
    """A flat body with the same outline, given as (x, y) going round anticlockwise, from z0 up to z1."""
    low = [bm.verts.new((x, y, z0)) for x, y in outline]
    high = [bm.verts.new((x, y, z1)) for x, y in outline]
    count = len(outline)
    for i in range(count):
        j = (i + 1) % count
        bm.faces.new((low[i], low[j], high[j], high[i]))
    bm.faces.new(low[::-1])
    bm.faces.new(high)


def home_plate():
    """Home plate: a white rubber pentagon, its front edge 0.43 across, its sides 0.215 back from that and the point where its
    two back edges meet, which is to +X +Y: the plate lies square to the diagonal of its square, as the game paints it."""
    model = Model("home_plate")
    half = 0.215
    plate_y = [(-half, -half), (half, -half), (half, 0.0), (0.0, half), (-half, 0.0)]
    turn = math.radians(-45)
    outline = [(x * math.cos(turn) - y * math.sin(turn), x * math.sin(turn) + y * math.cos(turn)) for x, y in plate_y]
    plate = bmesh.new()
    slab(plate, outline, 0.0, 0.035)
    model.hard(plate, "enamel", bevel=0.008, segments=1)
    return model


def pitching_rubber():
    """The pitcher's plate in the middle of a diamond, long along the model's X."""
    model = Model("pitching_rubber")
    plate = bmesh.new()
    cuboid(plate, -0.305, 0.305, -0.076, 0.076, 0.0, 0.05)
    model.hard(plate, "enamel", bevel=0.01, segments=1)
    return model


# A show jump: how high its three poles are, how high the wing standards at its ends are, and where along the first and the
# last squares of a run the wings stand.
JUMP_POLES, JUMP_TOP, JUMP_FIRST, JUMP_LAST = (0.59, 0.88, 1.2), 1.62, -0.1, 0.3


def jump(name, first, last):
    """A square's length of a show jump along the model's X, round its origin: its three poles striped red and white, and the
    wing standard where the run starts on the first square or ends on the last."""
    model = Model(name, centred=False)
    x0 = JUMP_FIRST + 0.05 if first else -0.5
    x1 = JUMP_LAST - 0.05 if last else 0.5
    red, white = bmesh.new(), bmesh.new()
    stripe = 0.2
    for z in JUMP_POLES:
        k = math.floor(x0 / stripe)
        while k * stripe < x1:
            a, b = max(x0, k * stripe), min(x1, (k + 1) * stripe)
            if b - a > 0.005:
                rod(red if k % 2 == 0 else white, (a, 0.0, z), (b, 0.0, z), 0.045, segments=10)
            k += 1
    model.turned(red, "vinyl", sharp=50.0)
    model.turned(white, "enamel", sharp=50.0)
    wings = bmesh.new()
    for at, here in ((JUMP_FIRST, first), (JUMP_LAST, last)):
        if not here:
            continue
        cuboid(wings, at - 0.04, at + 0.04, -0.04, 0.04, 0.0, JUMP_TOP)
        cuboid(wings, at - 0.05, at + 0.05, -0.05, 0.05, JUMP_TOP - 0.02, JUMP_TOP + 0.02)
        # The foot across the line of the jump, and a brace from each end of it up the standard.
        cuboid(wings, at - 0.04, at + 0.04, -0.34, 0.34, 0.0, 0.05)
        for side in (-1, 1):
            prism(wings, (at, side * 0.3, 0.06, 0.04, 0.05), (at, side * 0.05, 0.06, 0.04, 0.55))
        # The cups the poles rest in.
        for z in JUMP_POLES:
            cuboid(wings, at - 0.06, at + 0.06, -0.035, 0.035, z - 0.06, z - 0.035)
    model.hard(wings, "enamel", bevel=0.008, segments=1)
    return model


# A tennis net: how high it is at its posts and in the middle, how far down from its band it is mesh, and where along its
# first and its last squares its posts stand.
TENNIS_NET_TOP, TENNIS_POST_TOP, TENNIS_FIRST, TENNIS_LAST = 0.89, 0.96, -0.47, 0.44


def tennis_net(name, first, last):
    """A square's length of a tennis net along the model's X, round its origin, with its post where the run starts on the
    first square or ends on the last."""
    model = Model(name, centred=False)
    x0 = TENNIS_FIRST + 0.03 if first else -0.5
    x1 = TENNIS_LAST - 0.03 if last else 0.5
    mesh = bmesh.new()
    foot, top = 0.04, TENNIS_NET_TOP - 0.05
    cells = max(1, round((x1 - x0) / 0.045))
    for i in range(cells + 1):
        x = x0 + (x1 - x0) * i / cells
        cuboid(mesh, x - 0.003, x + 0.003, -0.003, 0.003, foot, top)
    rows = max(1, round((top - foot) / 0.045))
    for k in range(rows + 1):
        z = foot + (top - foot) * k / rows
        cuboid(mesh, x0, x1, -0.003, 0.003, z - 0.003, z + 0.003)
    model.plain(mesh, "rubber")
    band = bmesh.new()
    cuboid(band, x0, x1, -0.012, 0.012, top, TENNIS_NET_TOP)
    model.hard(band, "enamel", bevel=0.004, segments=1)
    if first or last:
        posts = bmesh.new()
        for at, here in ((TENNIS_FIRST, first), (TENNIS_LAST, last)):
            if here:
                cuboid(posts, at - 0.035, at + 0.035, -0.035, 0.035, 0.0, TENNIS_POST_TOP)
                cuboid(posts, at - 0.045, at + 0.045, -0.045, 0.045, TENNIS_POST_TOP - 0.02, TENNIS_POST_TOP + 0.01)
        model.hard(posts, "paint", bevel=0.006, segments=1)
    return model


# A gym's climbing frame: two posts a level and more high, a beam across their tops and a rope net or climbing ropes from it.
# The game paints it in a sprite a square and a level, four squares long. Each model is built round the middle of its
# square, the frame along X. Where the posts stand on the end squares, how high the beam's top is over the floor, how thick
# post and beam are, how far the net's foot is pulled out to -Y, how far apart its ropes are, and how far above the floor
# the climbing ropes end.
CLIMB_NORTH_POST, CLIMB_SOUTH_POST, CLIMB_BEAM_TOP, CLIMB_POST, CLIMB_BEAM = -0.28, 0.31, 4.2, 0.07, 0.08
CLIMB_NET_PULL, CLIMB_NET_CELL, CLIMB_ROPE_END, CLIMB_LEVEL = 0.3, 0.33, 0.66, 2.45


def climb_net_y(z):
    """How far out the net is at a height above the floor, from its foot pulled out to its top under the beam."""
    top = CLIMB_BEAM_TOP - 2 * CLIMB_BEAM
    return -CLIMB_NET_PULL * (1.0 - min(max(z, 0.0), top) / top)


def climb_part(name, low, post=None, beam=(-0.5, 0.5), net=None, ropes=()):
    """The part of a climbing frame over one square, on the level of its foot (low) or the level over that. post is where
    a post stands on the square, beam the beam's reach over it, net the net's reach over it, ropes where climbing ropes
    hang from the beam."""
    model = Model(name, centred=False)
    base = 0.0 if low else CLIMB_LEVEL
    lo, hi = base, base + CLIMB_LEVEL

    def z(height):
        return height - base

    wood = bmesh.new()
    if post is not None:
        cuboid(wood, post - CLIMB_POST, post + CLIMB_POST, -CLIMB_POST, CLIMB_POST, z(max(lo, 0.0)), z(min(hi, CLIMB_BEAM_TOP)))
        inward = 1.0 if post < 0 else -1.0
        if low:
            # A short block at the post's foot and a brace from it out to +Y.
            cuboid(wood, post - 0.06, post + 0.06, CLIMB_POST, CLIMB_POST + 0.12, 0.0, 0.55)
            prism(wood, (post, 0.6, 0.1, 0.1, 0.0), (post, CLIMB_POST + 0.06, 0.1, 0.1, 0.9))
        else:
            # The knee brace from the post in under the beam.
            under = CLIMB_BEAM_TOP - 2 * CLIMB_BEAM
            bar = bmesh.new()
            start = Vector((post + inward * CLIMB_POST, 0.0, z(under - 0.65)))
            end = Vector((post + inward * 0.65, 0.0, z(under)))
            way = end - start
            turn = Vector((0.0, 0.0, 1.0)).rotation_difference(way.normalized()).to_matrix().to_4x4()
            cuboid(bar, -0.045, 0.045, -0.045, 0.045, 0.0, way.length)
            bmesh.ops.transform(bar, matrix=Matrix.Translation(start) @ turn, verts=bar.verts)
            model.hard(bar, "dark_wood", bevel=0.008, segments=1)
    if not low:
        x0, x1 = beam
        cuboid(wood, x0, x1, -CLIMB_BEAM, CLIMB_BEAM, z(CLIMB_BEAM_TOP - 2 * CLIMB_BEAM), z(CLIMB_BEAM_TOP))
    model.hard(wood, "dark_wood", bevel=0.01, segments=1)
    rope = bmesh.new()
    if net is not None:
        x0, x1 = net
        foot, top = 0.15, CLIMB_BEAM_TOP - 2 * CLIMB_BEAM
        # The ropes that hang meet the floor of the level over the frame's foot, and the first that runs across is a rope's
        # thickness above it, so nothing of the square's piece is under that floor.
        n0, n1 = max(foot, lo), min(top, hi)
        across = max(foot, lo + 0.012)
        # The ropes that hang are at fixed places along the whole net, so the squares' nets meet.
        k = math.ceil(x0 / CLIMB_NET_CELL - 1e-6)
        while k * CLIMB_NET_CELL <= x1 + 1e-6:
            x = k * CLIMB_NET_CELL
            rod(rope, (x, climb_net_y(n0), z(n0)), (x, climb_net_y(n1), z(n1)), 0.014, segments=6)
            k += 1
        k = math.ceil((across - foot) / CLIMB_NET_CELL - 1e-6)
        while foot + k * CLIMB_NET_CELL <= n1 + 1e-6:
            h = foot + k * CLIMB_NET_CELL
            rod(rope, (x0, climb_net_y(h), z(h)), (x1, climb_net_y(h), z(h)), 0.012, segments=6)
            k += 1
        if not low:
            # Where the net is lashed to the beam.
            k = math.ceil(x0 / CLIMB_NET_CELL - 1e-6)
            while k * CLIMB_NET_CELL <= x1 + 1e-6:
                x = k * CLIMB_NET_CELL
                cuboid(rope, x - 0.03, x + 0.03, -CLIMB_BEAM - 0.012, CLIMB_BEAM + 0.012, z(CLIMB_BEAM_TOP - 2 * CLIMB_BEAM - 0.01), z(CLIMB_BEAM_TOP + 0.01))
                k += 1
    for x in ropes:
        r0, r1 = max(CLIMB_ROPE_END, lo), min(CLIMB_BEAM_TOP - 2 * CLIMB_BEAM, hi)
        lathe(rope, [(0.0, z(r0)), (0.022, z(r0)), (0.022, z(r1)), (0.0, z(r1))], cx=x, segments=8)
        if low:
            lathe(rope, [(0.0, z(r0) - 0.06), (0.03, z(r0) - 0.05), (0.04, z(r0)), (0.035, z(r0) + 0.06), (0.0, z(r0) + 0.07)], cx=x, segments=10)
        else:
            lathe(rope, [(0.0, z(CLIMB_BEAM_TOP) - 0.01), (0.04, z(CLIMB_BEAM_TOP)), (0.045, z(CLIMB_BEAM_TOP) + 0.04), (0.0, z(CLIMB_BEAM_TOP) + 0.07)], cx=x, segments=10)
    model.turned(rope, "worktop", sharp=50.0)
    return model


def climbing_frames():
    north, south = CLIMB_NORTH_POST, CLIMB_SOUTH_POST
    beam_north, beam_south = (north - 0.14, 0.5), (-0.5, south + 0.14)
    net_north, net_south = (north + CLIMB_POST, 0.5), (-0.5, south - CLIMB_POST)
    return [climb_part("climb_net_north_low", True, post=north, net=net_north),
            climb_part("climb_net_low", True, net=(-0.5, 0.5)),
            climb_part("climb_net_south_low", True, post=south, net=net_south),
            climb_part("climb_net_north_high", False, post=north, beam=beam_north, net=net_north),
            climb_part("climb_net_high", False, net=(-0.5, 0.5)),
            climb_part("climb_net_south_high", False, post=south, beam=beam_south, net=net_south),
            climb_part("climb_post_north_low", True, post=north),
            climb_part("climb_post_south_low", True, post=south),
            climb_part("climb_rope_low", True, ropes=(0.47,)),
            climb_part("climb_beam_north_high", False, post=north, beam=beam_north),
            climb_part("climb_beam_high", False),
            climb_part("climb_beam_rope_high", False, ropes=(0.47,)),
            climb_part("climb_beam_south_rope_high", False, post=south, beam=beam_south, ropes=(-0.55,))]


def sports():
    """What a playing field, a boxing ring and a gym have: Leisure.field stands each."""
    return [ring_post(), ring_ropes("ring_ropes", False), ring_ropes("ring_ropes_tied", True), ring_corner(), ring_steps(),
            goalpost_post(), goalpost_bar(), goalpost_end("goalpost_end_first", -0.12, True), goalpost_end("goalpost_end_last", 0.05, False),
            golf_flag(),
            base_bag(), home_plate(), pitching_rubber(),
            jump("jump_first", True, False), jump("jump_middle", False, False), jump("jump_last", False, True),
            tennis_net("tennis_net_first", True, False), tennis_net("tennis_net_middle", False, False), tennis_net("tennis_net_last", False, True)] \
        + climbing_frames()


# A grocer's display crate, as ShopFittings.java stands it: half its width, the height of its rim at the front and at the
# back, and how far the felt deck is under the rim. Read off the sprites location_shop_greenes_01_24..27: a crate 0.85 m
# square whose top falls towards the customer.
PRODUCE_HALF, PRODUCE_FRONT_TOP, PRODUCE_BACK_TOP, PRODUCE_DECK_DROP = 0.42, 0.80, 0.92, 0.035


def sloped_box(bm, x0, x1, y0, y1, z0, top0, top1):
    """A box from x0 to x1 and y0 to y1 standing on z0, its top top0 high at y0 and top1 high at y1."""
    low = [bm.verts.new((x, y, z0)) for x, y in ((x0, y0), (x1, y0), (x1, y1), (x0, y1))]
    high = [bm.verts.new((x, y, z)) for x, y, z in ((x0, y0, top0), (x1, y0, top0), (x1, y1, top1), (x0, y1, top1))]
    bm.faces.new(low[::-1])
    bm.faces.new(high)
    for i in range(4):
        j = (i + 1) % 4
        bm.faces.new((low[i], low[j], high[j], high[i]))


def produce_top(y):
    """How high the crate's rim is over y along it."""
    return PRODUCE_FRONT_TOP + (y + PRODUCE_HALF) / (2 * PRODUCE_HALF) * (PRODUCE_BACK_TOP - PRODUCE_FRONT_TOP)


def produce_crate():
    """A grocer's display crate: upright boards round four sides, a rim of battens round its top, and a felt deck a little
    under the rim, the whole top falling towards the front. The boards take the sprite's wood, the deck its felt."""
    model = Model("produce_crate")
    half, rim, board, wide = PRODUCE_HALF, 0.03, 0.02, 0.105
    boards, deck = bmesh.new(), bmesh.new()
    count = round(2 * half / wide)
    step = 2 * half / count
    for n in range(count):
        a, b = -half + n * step + 0.003, -half + (n + 1) * step - 0.003
        for s in (-1, 1):
            y0, y1 = sorted((s * half, s * (half - board)))
            top = produce_top(s * half) - 0.05
            sloped_box(boards, a, b, y0, y1, 0.0, top, top)
            x0, x1 = sorted((s * half, s * (half - board)))
            sloped_box(boards, x0, x1, a, b, 0.0, produce_top(a) - 0.05, produce_top(b) - 0.05)
    # The rim: four battens a little proud of the boards, the two along the sides sloped.
    out = half + 0.008
    for s in (-1, 1):
        y0, y1 = sorted((s * out, s * (out - rim)))
        top = produce_top(s * half)
        sloped_box(boards, -out, out, y0, y1, top - 0.06, top, top)
        x0, x1 = sorted((s * out, s * (out - rim)))
        sloped_box(boards, x0, x1, -out + rim, out - rim, produce_top(-half) - 0.06, produce_top(-half + rim), produce_top(half - rim))
    inner = half - board
    sloped_box(deck, -inner, inner, -inner, inner, 0.08, produce_top(-inner) - PRODUCE_DECK_DROP, produce_top(inner) - PRODUCE_DECK_DROP)
    model.hard(boards, "wood", bevel=0.004, segments=1)
    model.hard(deck, "cloth", bevel=0.004, segments=1)
    return model


PLAIN_FRUIT = 16


def fruit(bm, x, y, z, radius, squash, wide, deep, segments=22):
    """A piece of fruit or a vegetable resting on z: round, squash times as high as it is wide, and wide and deep times
    as long along x and y, with its top and bottom a little flattened where a stalk and a base are. The plain copy,
    which is all Low draws, keeps PLAIN_FRUIT sides, as a piece is near and small enough that fewer read as a stone."""
    rings = (PLAIN_FRUIT if plain else segments) // 2
    profile = []
    for i in range(rings + 1):
        angle = math.pi * i / rings
        profile.append((radius * math.sin(angle), z + radius * squash * (1.0 - math.cos(angle))))
    profile[0] = (0.0, z + radius * squash * 0.08)
    profile[-1] = (0.0, z + radius * squash * 1.94)
    lathe(bm, profile, cx=x, cy=y, segments=segments, sx=wide, sy=deep, least=PLAIN_FRUIT)


def produce_pile(name, seed):
    """A heap of fruit or vegetables over a ninth of a display crate's deck: round things two deep, each its own size,
    squash and length, half of them with the sheen of a waxed apple and half with the duller skin of an onion or a
    potato. All of it takes the colour the game paints its produce there, so it is apples where it paints apples and
    onions where it paints onions. Its foot is on the deck."""
    model = Model(name)
    pick = random.Random(seed)
    waxed, dull = bmesh.new(), bmesh.new()
    reach = 0.111
    for layer, (across, z) in enumerate(((3, 0.0), (2, 0.052))):
        apart = 2 * reach / across
        for i in range(across):
            for j in range(across):
                radius = pick.uniform(0.031, 0.047) if layer == 0 else pick.uniform(0.029, 0.041)
                x = -reach + (i + 0.5) * apart + pick.uniform(-0.012, 0.012)
                y = -reach + (j + 0.5) * apart + pick.uniform(-0.012, 0.012)
                squash = pick.uniform(0.72, 1.02)
                long = pick.uniform(1.0, 1.18)
                wide, deep = (long, 1.0) if pick.random() < 0.5 else (1.0, long)
                fruit(waxed if pick.random() < 0.5 else dull, x, y, z, radius, squash, wide, deep)
    model.turned(waxed, "enamel", sharp=80.0)
    model.turned(dull, "plastic", sharp=80.0)
    return model


def picket_length():
    """A square's length of white picket fence, as Pickets.java stands it on the square's edge: six pickets with pointed
    tops on the edge's line, nailed to two rails behind them, from a picket_post at the start of the edge to a gap the
    width of the gaps between pickets short of the next one. Measured off fencing_01_5: pickets 0.87 m high and 85 mm
    wide, rails from 0.10 and 0.60 m up."""
    model = Model("picket_length", centred=False)
    pickets, rails = bmesh.new(), bmesh.new()
    half, high, point, post = 0.0425, 0.87, 0.04, 0.043
    gap = (1.0 - 2 * post - 6 * 2 * half) / 7
    for k in range(6):
        x = -0.5 + post + gap + half + k * (2 * half + gap)
        outline_along_y(pickets, [(x - half, 0.0), (x + half, 0.0), (x + half, high - point), (x, high), (x - half, high - point)], -0.01, 0.01)
    for z in (0.10, 0.60):
        cuboid(rails, -0.5, 0.5, 0.01, 0.035, z, z + 0.07)
    model.hard(pickets, "paint", bevel=0.003, segments=1)
    model.hard(rails, "paint", bevel=0.003, segments=1)
    return model


def fence_kerb():
    """A square's length of the concrete footing the security fence's mesh stands in, as WallBuilder lays it on the
    square's edge: two cast blocks end to end along x, 0.2 m thick and 0.28 m high, with a hairline joint between them.
    Measured off fencing_01_49."""
    model = Model("fence_kerb", centred=False)
    blocks = bmesh.new()
    for x0, x1 in ((-0.5, -0.003), (0.003, 0.5)):
        cuboid(blocks, x0, x1, -0.1, 0.1, 0.0, 0.28)
    model.hard(blocks, "paint", bevel=0.01, segments=1)
    return model


def fence_pier():
    """The pier the security fence's post stands on, over the footing: wider and a little higher. Measured off fencing_01_48."""
    model = Model("fence_pier", centred=False)
    pier = bmesh.new()
    cuboid(pier, -0.13, 0.13, -0.13, 0.13, 0.0, 0.31)
    model.hard(pier, "paint", bevel=0.012, segments=1)
    return model


# A log wall is ten courses to a level. LogWalls.java lays a wall along y a log's radius lower than one along x, so at a
# corner where the two cross, each log's end passes between two of the other wall's.
LOG_RADIUS = 2.44949 / 20
# How far a log runs on past the corner it crosses another wall at: past that wall's logs, and a hand's width more.
LOG_OVER = 0.24


def logs_along_x(bark, ends, z, x0, x1, cap0, cap1):
    """One log from x0 to x1 with its axis at height z, the cut end grain at x0 and x1 where asked."""
    lathe(bark, [(LOG_RADIUS, x0), (LOG_RADIUS, x1)], segments=12, caps=False, to=along_x(0.0, 0.0, z))
    for cap in (cap0, cap1):
        if cap is not None:
            # A disc each way: which side of it is seen depends on the end it closes.
            lathe(ends, [(0.0, cap), (LOG_RADIUS, cap)], segments=12, caps=False, to=along_x(0.0, 0.0, z))
            lathe(ends, [(LOG_RADIUS, cap), (0.0, cap)], segments=12, caps=False, to=along_x(0.0, 0.0, z))


def log_wall(name, x0, x1, cap1):
    """Ten courses of a log wall along x from x0 to x1, the lowest on the foot, the end grain at x1 where cap1."""
    model = Model(name, centred=False)
    bark, ends = bmesh.new(), bmesh.new()
    for course in range(10):
        z = LOG_RADIUS * (2 * course + 1)
        logs_along_x(bark, ends, z, x0, x1, None, x1 if cap1 else None)
    model.turned(bark, "wood", sharp=50.0)
    model.plain(ends, "worktop")
    return model


def log_single(name, half):
    """One log along x from -half to half, its axis a log's radius over the foot, the end grain at both ends: a course of
    a log wall, or a piece of what is left of one beside a door or a window."""
    model = Model(name, centred=False)
    bark, ends = bmesh.new(), bmesh.new()
    logs_along_x(bark, ends, LOG_RADIUS, -half, half, -half, half)
    model.turned(bark, "wood", sharp=50.0)
    model.plain(ends, "worktop")
    return model


def log_walls():
    """The log walls of a cabin: the ends that run on past a corner, and a course a square long and in a sixteenth of a
    square for the courses a door or a window cuts."""
    return [log_wall("log_end", 0.0, LOG_OVER, True), log_single("log_course", 0.5), log_single("log_piece", 1.0 / 32)]


def shops():
    """What a grocer, a florist or a shop stands out that ShopFittings.java gives a model for, the produce crate and the
    produce the game paints in it, the length of white picket fence Pickets.java stands, and the security fence's footing."""
    return [produce_crate(), produce_pile("produce_pile", 61), produce_pile("produce_pile_b", 62), picket_length(), fence_kerb(), fence_pier()]


# A gallery's sculpture: busts on their plinths and statues on their bases, carved from metaballs. Gallery stands them.


# A gallery's sculpture and a stage's microphone: busts on their plinths and statues on their bases, carved from
# metaballs. Gallery stands the sculpture and Leisure the microphone.


# A gallery's sculpture, a stage's microphone and the lamp over a bar's pool table: busts on their plinths and statues
# on their bases, the figures carved by sculpture.py out of a human body. Gallery stands the sculpture, Leisure the
# microphone and the lamp.

SCULPTURE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sculpture.bin.gz")
# Marble's grain: the light a figure's stone takes is lowered by up to this much along the veins through it.
VEIN_DEPTH = 0.08
# A figure is more triangles than anything else here: its face is drawn from close up.
SCULPTURE_TRIANGLES = 20000
carved = {}


def carved_figure(name):
    """The figure sculpture.py carved, its places and its triangles. The plain copy has it whole: Low draws the plain
    copy at every distance, and the pictures of the gallery's sculpture are taken close up at Low."""
    if not carved:
        with gzip.open(SCULPTURE, "rb") as packed:
            data = packed.read()
        version, count = struct.unpack_from("<II", data, 4)
        if data[:4] != b"SCLP" or version != 1:
            raise SystemExit("%s is not a version 1 sculpture file" % SCULPTURE)
        at = 12
        for _ in range(count):
            called = data[at + 1:at + 1 + data[at]].decode("ascii")
            at += 1 + data[at]
            vertices, triangles = struct.unpack_from("<II", data, at)
            at += 8
            places = struct.unpack_from("<%df" % (vertices * 3), data, at)
            at += vertices * 12
            carved[called] = (places, struct.unpack_from("<%dI" % (triangles * 3), data, at))
            at += triangles * 12
    return carved[name]


def figure(model, name, foot, top):
    """The carved figure stood with its foot at height foot, scaled so that its top is at top."""
    places, corners = carved_figure(name)
    scale = (top - foot) / max(places[2::3])
    bm = bmesh.new()
    verts = [bm.verts.new((places[i] * scale, places[i + 1] * scale, foot + places[i + 2] * scale)) for i in range(0, len(places), 3)]
    for i in range(0, len(corners), 3):
        bm.faces.new((verts[corners[i]], verts[corners[i + 1]], verts[corners[i + 2]]))
    model.turned(bm, "ceramic", sharp=75.0)
    model.most = SCULPTURE_TRIANGLES
    model.veined = True


def vein(position):
    """How far into a vein of the marble a point is, 0 to 1: thin bands through the stone, bent by turbulence."""
    bent = noise.turbulence(position * 3.0, 3, False)
    across = abs(math.sin((position.x * 0.7 + position.y * 0.35 + position.z * 0.9) * 14.0 + bent * 6.0))
    return max(0.0, 1.0 - across / 0.18)


def bust(name):
    """A bust on a stone plinth: the plinth 0.49 m square and 1.15 m high, a turned socle, and a head and shoulders
    0.75 m from the socle to the crown, about a half again life size, as the game's sprites of them are."""
    model = Model(name)
    plinth = bmesh.new()
    cuboid(plinth, -0.245, 0.245, -0.245, 0.245, 0.0, 1.09)
    cuboid(plinth, -0.26, 0.26, -0.26, 0.26, 1.09, 1.15)
    cuboid(plinth, -0.26, 0.26, -0.26, 0.26, 0.0, 0.05)
    model.hard(plinth, "paint", bevel=0.008)
    socle = bmesh.new()
    cuboid(socle, -0.1, 0.1, -0.1, 0.1, 1.15, 1.19)
    lathe(socle, [(0.0, 1.19), (0.075, 1.19), (0.06, 1.21), (0.045, 1.25), (0.05, 1.27), (0.08, 1.29), (0.0, 1.29)], segments=24)
    model.hard(socle, "ceramic", bevel=0.004)
    figure(model, name, 1.29, 1.29 + 0.532 * 1.42)
    return model


def statue_base(model, half, high, made):
    base = bmesh.new()
    cuboid(base, -half, half, -half, half, 0.0, high)
    model.hard(base, made, bevel=0.01)


def female_statue():
    """A draped woman a little over life size, her arms raised to her hair and her chiton falling to her feet, on a
    white stone base 0.9 m square and 0.46 m high: the game's Female Statue, 2.47 m to the top of her hands."""
    model = Model("statue_female")
    statue_base(model, 0.45, 0.46, "paint")
    figure(model, "statue_female", 0.46, 2.47)
    return model


def male_statue():
    """A standing youth, his weight on his right leg and the left knee bent, a cloth knotted about his hips and one hand
    at his shoulder: the game's Male Statue, 2.38 m to the top of his head on a white stone base 0.9 m square and 0.46 m high."""
    model = Model("statue_male")
    statue_base(model, 0.45, 0.46, "paint")
    figure(model, "statue_male", 0.46, 2.38)
    return model


def orator_statue():
    """A robed man on a tall pedestal, his right hand raised before him and a staff in his left hand: the game's Gallery
    Statue, painted over two storeys. The pedestal is 0.92 m square and 1.05 m high with a plinth 0.7 m square on it,
    and the figure's staff reaches 3.07 m."""
    model = Model("statue_orator")
    pedestal = bmesh.new()
    cuboid(pedestal, -0.46, 0.46, -0.46, 0.46, 0.0, 0.84)
    cuboid(pedestal, -0.47, 0.47, -0.47, 0.47, 0.84, 0.9)
    cuboid(pedestal, -0.46, 0.46, -0.46, 0.46, 0.9, 1.05)
    model.hard(pedestal, "paint", bevel=0.01)
    plinth = bmesh.new()
    cuboid(plinth, -0.35, 0.35, -0.35, 0.35, 1.05, 1.2)
    model.hard(plinth, "ceramic", bevel=0.01)
    figure(model, "statue_orator", 1.2, 3.07)
    return model


def stage_microphone():
    """A singer's microphone on a stand: three splayed legs, a black pole to 1.0 m, a thinner rod and a gooseneck that
    leans the microphone out towards the front, its grille 1.65 m up."""
    model = Model("stage_microphone")
    legs, pole, grille = bmesh.new(), bmesh.new(), bmesh.new()
    for n in range(3):
        angle = math.radians(90 + n * 120)
        x, y = math.cos(angle), math.sin(angle)
        rod(legs, (x * 0.03, y * 0.03, 0.12), (x * 0.33, y * 0.33, 0.012), 0.008, segments=6)
        rod(pole, (x * 0.33, y * 0.33, 0.012), (x * 0.345, y * 0.345, 0.0), 0.012, segments=6)
    lathe(pole, [(0.0, 0.08), (0.022, 0.08), (0.022, 0.16), (0.013, 0.17), (0.013, 1.0), (0.019, 1.0), (0.019, 1.06), (0.0, 1.06)], segments=10)
    rod(pole, (0.0, 0.0, 1.06), (0.0, 0.0, 1.45), 0.007, segments=8)
    neck = [(0.0, 0.0, 1.45), (0.0, 0.025, 1.5), (0.0, 0.02, 1.55), (0.0, -0.02, 1.59), (0.0, -0.06, 1.61)]
    for a, b in zip(neck, neck[1:]):
        rod(pole, a, b, 0.006, segments=8)
    body = Vector((0.0, -0.06, 1.61)), Vector((0.0, -0.21, 1.66))
    way = (body[1] - body[0]).normalized()
    turn = Matrix.Translation(body[0]) @ Vector((0.0, 0.0, 1.0)).rotation_difference(way).to_matrix().to_4x4()
    lathe(pole, [(0.0, 0.0), (0.014, 0.0), (0.02, 0.1), (0.024, 0.13), (0.0, 0.13)], segments=12, to=turn)
    lathe(grille, [(0.0, 0.12), (0.026, 0.13), (0.03, 0.15), (0.026, 0.175), (0.0, 0.185)], segments=12, to=turn)
    model.hard(legs, "steel", bevel=0.002, segments=1)
    model.turned(pole, "rubber")
    model.turned(grille, "steel")
    return model


def pool_lamp():
    """A billiard lamp over a pool table two squares long: three green shades with a brass fringe on a brass bar along x,
    held by two curved arms from a pole down from the ceiling. The shades' rims are 1.58 m up and 0.53 m apart."""
    model = Model("pool_lamp", hung=True)
    brass, shades, fringe = bmesh.new(), bmesh.new(), bmesh.new()
    top = 2.449
    rod(brass, (0.0, 0.0, top), (0.0, 0.0, 2.17), 0.01, segments=8)
    lathe(brass, [(0.0, top - 0.02), (0.05, top - 0.02), (0.05, top), (0.0, top)], segments=12)
    lathe(brass, [(0.0, 2.12), (0.022, 2.13), (0.022, 2.19), (0.0, 2.2)], segments=10)
    rod(brass, (-0.62, 0.0, 1.75), (0.62, 0.0, 1.75), 0.011, segments=8)
    for side in (-1, 1):
        arc = [(side * 0.31 * (1 - math.cos(math.radians(a))), 0.0, 2.15 - 0.4 * math.sin(math.radians(a))) for a in range(0, 91, 15)]
        for a, b in zip(arc, arc[1:]):
            rod(brass, a, b, 0.007, segments=6)
        lathe(brass, [(0.0, 1.73), (0.014, 1.73), (0.014, 1.81), (0.0, 1.81)], cx=side * 0.62, segments=8)
    for x in (-0.53, 0.0, 0.53):
        lathe(brass, [(0.0, 1.66), (0.012, 1.66), (0.012, 1.75), (0.0, 1.75)], cx=x, segments=8)
        lathe(brass, [(0.0, 1.64), (0.03, 1.645), (0.03, 1.665), (0.0, 1.67)], cx=x, segments=10)
        lathe(shades, [(0.03, 1.66), (0.08, 1.645), (0.13, 1.615), (0.155, 1.585), (0.16, 1.575), (0.15, 1.575), (0.12, 1.6),
                       (0.075, 1.625), (0.028, 1.64)], cx=x, segments=20, closed=True)
        lathe(fringe, [(0.162, 1.585), (0.165, 1.545), (0.155, 1.545), (0.152, 1.585)], cx=x, segments=20, closed=True)
    model.turned(brass, "metal")
    model.turned(shades, "shade")
    model.turned(fringe, "metal")
    return model


def gallery():
    return [bust("bust_male"), bust("bust_female"), female_statue(), male_statue(), orator_statue(), stage_microphone(), pool_lamp()]


def studio_camera():
    """A television studio's camera on a tripod, the Imekagi HK533p: a beige body on a black pan head and a grey centre
    column, a black hood open at the front round the lens, a viewfinder on top over its left side with its screen at
    the back under a hood of its own, a pan bar out to the right and a cable looped down the same side. Three silver
    legs splay from 0.8 m up to feet 0.44 m out. The origin is the column, which the sprites paint in the middle of the
    square; the lens is 1.45 m up and the viewfinder's hood reaches 1.79 m."""
    model = Model("studio_camera", centred=False)
    legs, black, feet, column, body, hood, glass, screen = (bmesh.new() for _ in range(8))
    hub = 0.8
    for n in range(3):
        angle = math.radians(90 + n * 120)
        x, y = math.cos(angle), math.sin(angle)
        rod(legs, (x * 0.045, y * 0.045, hub), (x * 0.44, y * 0.44, 0.035), 0.02, segments=8)
        lathe(feet, [(0.0, 0.0), (0.024, 0.0), (0.022, 0.05), (0.0, 0.05)], cx=x * 0.445, cy=y * 0.445, segments=8)
    lathe(black, [(0.0, hub - 0.08), (0.065, hub - 0.08), (0.065, hub + 0.06), (0.04, hub + 0.08), (0.0, hub + 0.08)], segments=12)
    lathe(column, [(0.0, hub + 0.07), (0.03, hub + 0.07), (0.03, 1.17), (0.0, 1.17)], segments=10)
    lathe(black, [(0.0, 1.15), (0.075, 1.15), (0.075, 1.24), (0.06, 1.26), (0.0, 1.26)], segments=12)
    cuboid(black, -0.1, 0.1, -0.12, 0.1, 1.24, 1.27)
    # The pan bar, out to the camera's right from the back of the head and a little down.
    rod(black, (0.05, 0.06, 1.21), (0.33, 0.2, 1.14), 0.015, segments=8)
    lathe(black, [(0.017, 0.0), (0.017, 0.12)], segments=8, to=Matrix.Translation((0.33, 0.2, 1.14))
          @ Vector((0.0, 0.0, 1.0)).rotation_difference(Vector((0.28, 0.14, -0.07)).normalized()).to_matrix().to_4x4())
    # The body, longer than it is wide, its front where the lens hood meets it.
    cuboid(body, -0.13, 0.13, -0.29, 0.19, 1.27, 1.55)
    cuboid(body, -0.1, 0.1, -0.29, -0.11, 1.55, 1.59)
    # The viewfinder over the left of the front half, its screen in the back face under a hood of three flaps.
    cuboid(body, -0.15, 0.03, -0.22, 0.03, 1.57, 1.75)
    cuboid(screen, -0.13, 0.01, 0.03, 0.035, 1.6, 1.72)
    cuboid(hood, -0.16, -0.148, 0.03, 0.14, 1.58, 1.78)
    cuboid(hood, 0.028, 0.04, 0.03, 0.14, 1.58, 1.78)
    cuboid(hood, -0.16, 0.04, 0.03, 0.16, 1.78, 1.793)
    # The panel on the right side and the cable that loops down from it.
    cuboid(hood, 0.13, 0.136, -0.13, 0.07, 1.33, 1.47)
    cable = [(0.14, 0.08, 1.41), (0.16, 0.12, 1.37), (0.165, 0.13, 1.29), (0.15, 0.1, 1.22), (0.12, 0.04, 1.2), (0.09, 0.0, 1.23)]
    for a, b in zip(cable, cable[1:]):
        rod(black, a, b, 0.008, segments=6)
    # The hood round the lens: open at the front, wider and taller than the body.
    front, back = -0.6, -0.29
    cuboid(hood, -0.19, 0.19, front, back, 1.55, 1.57)
    cuboid(hood, -0.19, 0.19, front, back, 1.33, 1.35)
    cuboid(hood, -0.19, -0.17, front, back, 1.35, 1.55)
    cuboid(hood, 0.17, 0.19, front, back, 1.35, 1.55)
    cuboid(hood, -0.17, 0.17, back - 0.02, back, 1.35, 1.55)
    lathe(black, [(0.0, 0.0), (0.075, 0.0), (0.075, 0.22), (0.065, 0.24), (0.0, 0.24)], segments=16, to=along_y(0.0, back - 0.02, 1.45) @ Matrix.Rotation(math.pi, 4, "X"))
    lathe(glass, [(0.06, 0.0), (0.0, 0.005)], segments=16, to=along_y(0.0, back - 0.263, 1.45) @ Matrix.Rotation(math.pi, 4, "X"))
    model.hard(legs, "steel", bevel=0.002, segments=1)
    model.turned(feet, "rubber")
    model.turned(column, "steel")
    model.hard(black, "rubber", bevel=0.004)
    model.hard(body, "paint", bevel=0.015)
    model.hard(hood, "plastic", bevel=0.004)
    model.plain(screen, "screen")
    model.turned(glass, "screen")
    return model


def cameras():
    return [studio_camera()]


# An arcade cabinet on one square, its front at -Y, as the game paints Dr. Oids and Kaboom (recreational_01_16..23), read
# off the sprites: half its width, where the front of its foot and its back are, how high its top is and where its back
# starts to lean in to it, where the face of its marquee is and how high its foot, the control panel's front and how
# high its top and its foot, and the screen's foot and top on the face that leans back over the panel.
ARCADE_HALF, ARCADE_FOOT, ARCADE_BACK, ARCADE_TOP, ARCADE_SHOULDER = 0.41, -0.10, 0.35, 1.74, 1.48
ARCADE_MARQUEE, ARCADE_MARQUEE_FOOT = -0.20, 1.46
ARCADE_PANEL, ARCADE_PANEL_TOP, ARCADE_PANEL_FOOT = -0.26, 0.92, 0.84
ARCADE_SCREEN_FOOT, ARCADE_SCREEN_TOP = (-0.13, 0.98), (-0.05, 1.41)


def arcade_machine():
    """An upright arcade cabinet: two side panels in the game's art, the marquee over the screen, the screen in a bezel
    of the art's colour leaning back over a control panel with two sticks and their buttons, and a coin door in the front under it. The
    marquee is a pane, for the sprite's own painting of it."""
    model = Model("arcade_machine")
    half = ARCADE_HALF
    side = [(ARCADE_FOOT, 0.0), (ARCADE_BACK, 0.0), (ARCADE_BACK, ARCADE_SHOULDER), (ARCADE_BACK - 0.17, ARCADE_TOP),
            (ARCADE_MARQUEE, ARCADE_TOP), (ARCADE_MARQUEE, ARCADE_MARQUEE_FOOT), ARCADE_SCREEN_TOP, ARCADE_SCREEN_FOOT,
            (ARCADE_PANEL, ARCADE_PANEL_TOP), (ARCADE_PANEL, ARCADE_PANEL_FOOT), (ARCADE_FOOT, ARCADE_PANEL_FOOT - 0.06)]
    body = bmesh.new()
    outline_along_x(body, side, -half, half)
    model.hard(body, "plastic", bevel=0.008, segments=1)
    # The art on each side, a skin a little inside the side's edges, over all of it but the kick at its foot.
    art = bmesh.new()
    inset = [(ARCADE_FOOT + 0.03, 0.1), (ARCADE_BACK - 0.03, 0.1), (ARCADE_BACK - 0.03, ARCADE_SHOULDER - 0.01),
             (ARCADE_BACK - 0.19, ARCADE_TOP - 0.04), (ARCADE_MARQUEE + 0.03, ARCADE_TOP - 0.04), (ARCADE_MARQUEE + 0.03, ARCADE_MARQUEE_FOOT + 0.02),
             (ARCADE_SCREEN_TOP[0] + 0.03, ARCADE_SCREEN_TOP[1] - 0.02), (ARCADE_SCREEN_FOOT[0] + 0.03, ARCADE_SCREEN_FOOT[1] - 0.01),
             (ARCADE_PANEL + 0.03, ARCADE_PANEL_TOP - 0.02), (ARCADE_PANEL + 0.03, ARCADE_PANEL_FOOT + 0.02), (ARCADE_FOOT + 0.03, ARCADE_PANEL_FOOT - 0.04)]
    for x in (-1, 1):
        outline_along_x(art, inset, x * half - 0.004, x * half + 0.004)
    model.hard(art, "paint", bevel=0.002, segments=1)
    marquee = bmesh.new()
    cuboid(marquee, -half + 0.03, half - 0.03, ARCADE_MARQUEE - 0.006, ARCADE_MARQUEE + 0.01, ARCADE_MARQUEE_FOOT + 0.05, ARCADE_TOP - 0.025)
    model.plain(marquee, "pane")
    # The bezel and the glass in it lie on the face that leans back from the panel to the marquee.
    (y0, z0), (y1, z1) = ARCADE_SCREEN_FOOT, ARCADE_SCREEN_TOP
    rise = math.hypot(y1 - y0, z1 - z0)
    up = ((y1 - y0) / rise, (z1 - z0) / rise)
    out = (-up[1], up[0])

    def on_face(along, proud):
        return y0 + up[0] * along + out[0] * proud, z0 + up[1] * along + out[1] * proud

    bezel, glass = bmesh.new(), bmesh.new()
    for into, x, a0, a1, proud in ((bezel, half - 0.04, 0.0, rise, 0.008), (glass, half - 0.13, 0.06, rise - 0.07, 0.018)):
        corners = [on_face(a, p) for a, p in ((a0, 0.0), (a1, 0.0), (a1, proud), (a0, proud))]
        outline_along_x(into, [(yy, zz) for yy, zz in corners], -x, x)
    model.hard(bezel, "paint", bevel=0.003, segments=1)
    model.hard(glass, "screen", bevel=0.01, segments=2)
    # The control panel: its top falls a little to the player, with a strip of the art's colour along its front edge.
    trim = bmesh.new()
    cuboid(trim, -half + 0.01, half - 0.01, ARCADE_PANEL - 0.008, ARCADE_PANEL + 0.002, ARCADE_PANEL_FOOT + 0.015, ARCADE_PANEL_TOP - 0.01)
    model.hard(trim, "paint", bevel=0.003, segments=1)
    slope = (ARCADE_SCREEN_FOOT[1] - ARCADE_PANEL_TOP) / (ARCADE_SCREEN_FOOT[0] - ARCADE_PANEL)

    def panel_z(y):
        return ARCADE_PANEL_TOP + (y - ARCADE_PANEL) * slope

    balls, sticks, buttons = bmesh.new(), bmesh.new(), bmesh.new()
    for x in (-0.2, 0.2):
        y = ARCADE_PANEL + 0.06
        z = panel_z(y)
        lathe(sticks, [(0.0, 0.0), (0.025, 0.0), (0.025, 0.008), (0.006, 0.01), (0.006, 0.075), (0.0, 0.075)], cx=x, cy=y, segments=10,
              to=Matrix.Translation((0.0, 0.0, z)))
        lathe(balls, [(0.0, -0.021)] + [(0.021 * math.sin(math.pi * k / 6), -0.021 * math.cos(math.pi * k / 6)) for k in range(1, 6)] + [(0.0, 0.021)],
              cx=x, cy=y, segments=12, to=Matrix.Translation((0.0, 0.0, z + 0.09)))
        for k, (dx, dy) in enumerate(((0.08, -0.01), (0.12, 0.025), (0.16, 0.0), (0.085, 0.05), (0.125, 0.07), (0.165, 0.045))):
            bx, by = x + dx - 0.1, ARCADE_PANEL + 0.06 + dy
            lathe(buttons, [(0.0, 0.0), (0.02, 0.0), (0.02, 0.01), (0.016, 0.015), (0.0, 0.016)], cx=bx, cy=by, segments=10,
                  to=Matrix.Translation((0.0, 0.0, panel_z(by) - 0.002)))
    model.turned(sticks, "steel", sharp=35.0)
    model.turned(balls, "vinyl", sharp=60.0)
    model.turned(buttons, "paint", sharp=35.0)
    # The coin door: a steel plate with two coin slots lit red over two return flaps.
    door = bmesh.new()
    cuboid(door, -0.11, 0.11, ARCADE_FOOT - 0.008, ARCADE_FOOT + 0.002, 0.32, 0.64)
    model.hard(door, "metal", bevel=0.004, segments=1)
    slots = bmesh.new()
    for x in (-0.045, 0.045):
        cuboid(slots, x - 0.028, x + 0.028, ARCADE_FOOT - 0.014, ARCADE_FOOT - 0.006, 0.52, 0.61)
    model.hard(slots, "vinyl", bevel=0.002, segments=1)
    flaps = bmesh.new()
    for x in (-0.045, 0.045):
        cuboid(flaps, x - 0.028, x + 0.028, ARCADE_FOOT - 0.014, ARCADE_FOOT - 0.006, 0.37, 0.46)
    model.hard(flaps, "rubber", bevel=0.002, segments=1)
    return model


def netarcade():
    return [arcade_machine()]


# A church's long panelled desk (location_community_church_small_01_93 at its west end, 94 for each square between, 95 at its
# east end), its front at -Y, read off the sprites laid in a row: where its middle stands along the row from the west end
# square's corner, and in metres the half length and half depth and height of its plinth, the panelled body's half length and
# half depth, the top's half length, half depth, height and thickness, and how wide the pilaster at each end of the body is.
PULPIT_MIDDLE = 1.44
PULPIT_PLINTH = (1.0, 0.23, 0.12)
PULPIT_BODY = (0.91, 0.155)
PULPIT_TOP = (0.96, 0.4225, 0.955, 0.09)
PULPIT_PILASTER = 0.075


def pulpit():
    """The long desk, made and lit as one piece and then cut at the edges of the three squares of its row: a share made and lit
    alone had no neighbour to shade it or close its open end, and the light of its faces stepped where it met the next."""
    model = Model("pulpit", centred=False)
    plinth_x, plinth_y, plinth_z = PULPIT_PLINTH
    body_x, body_y = PULPIT_BODY
    top_x, top_y, top_z, top_t = PULPIT_TOP
    under = top_z - top_t

    def taper(bm, x0, x1, y0, y1, z0, inset, z1):
        """A box narrowing by inset on every side from z0 up to z1: the cap of a plinth or the cove under a top."""
        prism(bm, ((x0 + x1) / 2, (y0 + y1) / 2, x1 - x0, y1 - y0, z0), ((x0 + x1) / 2, (y0 + y1) / 2, x1 - x0 - 2 * inset, y1 - y0 - 2 * inset, z1))

    plinth = bmesh.new()
    cuboid(plinth, -plinth_x, plinth_x, -plinth_y, plinth_y, 0.0, plinth_z - 0.03)
    taper(plinth, -plinth_x, plinth_x, -plinth_y, plinth_y, plinth_z - 0.03, 0.03, plinth_z)
    model.hard(plinth, "dark_wood", bevel=0.006, segments=1)
    # The body: a core with a long sunk field front and back, framed by a rail at its foot and its head and a pilaster at each end,
    # with a moulding round its foot over the plinth.
    sunk = 0.012
    body = bmesh.new()
    cuboid(body, -body_x, body_x, -body_y + sunk, body_y - sunk, plinth_z, under)
    for y0, y1 in ((-body_y, -body_y + sunk), (body_y - sunk, body_y)):
        cuboid(body, -body_x, body_x, y0, y1, plinth_z, plinth_z + 0.1)
        cuboid(body, -body_x, body_x, y0, y1, under - 0.09, under)
        for x0, x1 in ((-body_x, -body_x + PULPIT_PILASTER), (body_x - PULPIT_PILASTER, body_x)):
            cuboid(body, x0, x1, y0, y1, plinth_z + 0.1, under - 0.09)
    model.hard(body, "dark_wood", bevel=0.004, segments=1)
    trim = bmesh.new()
    cuboid(trim, -body_x - 0.012, body_x + 0.012, -body_y - 0.012, body_y + 0.012, plinth_z, plinth_z + 0.03)
    taper(trim, -body_x - 0.012, body_x + 0.012, -body_y - 0.012, body_y + 0.012, under - 0.045, -0.025, under)
    model.hard(trim, "dark_wood", bevel=0.004, segments=1)
    top = bmesh.new()
    cuboid(top, -top_x, top_x, -top_y, top_y, under, top_z)
    model.hard(top, "wood", bevel=0.012, segments=2)
    # A carved sprig in the field near each end of the front: a stem curving up with its leaves to either side.
    carving = bmesh.new()
    face = -body_y + sunk
    for side in (-1, 1):
        x = side * (body_x - PULPIT_PILASTER - 0.09)
        stem = [(x + side * 0.03 * math.sin(math.pi * t / 4), plinth_z + 0.2 + 0.36 * t / 4) for t in range(5)]
        for (ax, az), (bx, bz) in zip(stem, stem[1:]):
            rod(carving, (ax, face - 0.004, az), (bx, face - 0.004, bz), 0.006, segments=6)
        for k, (lx, lz) in enumerate(stem[1:]):
            lean = (-1) ** k * 50.0
            lathe(carving, [(0.0, -0.035), (0.012, -0.02), (0.016, 0.0), (0.01, 0.022), (0.0, 0.035)], segments=8, sy=0.35,
                  to=Matrix.Translation((lx + math.copysign(0.025, lean), face - 0.004, lz + 0.01)) @ Matrix.Rotation(math.radians(lean), 4, "Y"))
    model.turned(carving, "dark_wood", sharp=50.0)
    model.shares = [(name, square - PULPIT_MIDDLE, square + 1 - PULPIT_MIDDLE)
                    for square, name in enumerate(("pulpit_west", "pulpit_middle", "pulpit_east"))]
    return [model]


def fluted(bm, profile, segments, flute):
    """A lathe of (radius, height) whose every other side is drawn in by flute of its radius, so the surface is ribbed
    upright like strings of crystal hung side by side. The ends are closed, a radius of 0 a point on the axis."""
    rings = []
    for radius, z in profile:
        if radius <= 1e-6:
            rings.append([bm.verts.new((0.0, 0.0, z))])
            continue
        rings.append([bm.verts.new((radius * (1 - flute * (i % 2)) * math.cos(2 * math.pi * i / segments),
                                    radius * (1 - flute * (i % 2)) * math.sin(2 * math.pi * i / segments), z)) for i in range(segments)])
    for a, b in zip(rings, rings[1:]):
        for i in range(segments):
            j = (i + 1) % segments
            if len(a) == 1:
                bm.faces.new((a[0], b[j], b[i]))
            elif len(b) == 1:
                bm.faces.new((a[i], a[j], b[0]))
            else:
                bm.faces.new((a[i], a[j], b[j], b[i]))
    for ring, flip in ((rings[0], True), (rings[-1], False)):
        if len(ring) > 1:
            bm.faces.new(ring[::-1] if flip else ring)


def drop(bm, x, y, z, size):
    """A cut crystal hung by its top at (x, y, z): an eight-sided point, longer than it is wide."""
    top, waist, low = bm.verts.new((x, y, z)), [], bm.verts.new((x, y, z - size * 3))
    for i in range(4):
        a = math.pi / 2 * i + math.pi / 4
        waist.append(bm.verts.new((x + size * math.cos(a), y + size * math.sin(a), z - size)))
    for i in range(4):
        j = (i + 1) % 4
        bm.faces.new((top, waist[j], waist[i]))
        bm.faces.new((low, waist[i], waist[j]))


# A church's crystal chandelier, heights in metres over the floor of the storey its bowl is painted on, as its sprites paint it:
# the gold ring round the bowl's top, and each tier of crystal over it, (radius, foot, top, sides). The game paints the bowl on
# one storey and the tiers on the one over it, CHANDELIER_STOREY higher, and the rod goes up to that storey's ceiling.
CHANDELIER_RING, CHANDELIER_RING_UP = 0.84, 2.35
CHANDELIER_TIERS = [(0.64, 2.36, 2.88, 40), (0.48, 2.9, 3.39, 32), (0.33, 3.39, 3.85, 28), (0.19, 3.85, 4.13, 20)]
CHANDELIER_STOREY = 2.45


def chandelier_bowl():
    """The bowl of a crystal chandelier: crystal strings hung in a deep bowl under a gold ring 1.68 m across, held by six arms
    from a hub on the rod, which goes up to the ceiling of its storey, and drops under it. Its foot, the drop at the bottom,
    is 1.2 m over the floor. The origin is the rod."""
    model = Model("chandelier_bowl", hung=True)
    gold, crystal = bmesh.new(), bmesh.new()
    fluted(crystal, [(0.8, 2.33), (0.79, 2.16), (0.7, 1.93), (0.57, 1.74), (0.46, 1.56), (0.38, 1.43), (0.28, 1.335), (0.12, 1.295),
                     (0.0, 1.285)], 48, 0.07)
    for i in range(16):
        a = 2 * math.pi * (i + 0.5) / 16
        drop(crystal, 0.3 * math.cos(a), 0.3 * math.sin(a), 1.34, 0.02)
    drop(crystal, 0.0, 0.0, 1.29, 0.03)
    torus(gold, 0.0, 0.0, CHANDELIER_RING_UP, CHANDELIER_RING, 0.025, segments=40, steps=6)
    for i in range(6):
        a = math.pi / 3 * i
        rod(gold, (0.0, 0.0, CHANDELIER_RING_UP - 0.04), (CHANDELIER_RING * math.cos(a), CHANDELIER_RING * math.sin(a), CHANDELIER_RING_UP),
            0.01, segments=6)
    lathe(gold, [(0.0, 2.2), (0.05, 2.22), (0.06, 2.3), (0.03, 2.36), (0.016, 2.37), (0.016, CHANDELIER_STOREY)], segments=12)
    model.turned(gold, "metal", sharp=50.0)
    model.turned(crystal, "ceramic", sharp=20.0)
    return model


def chandelier_tiers():
    """The tiers of a crystal chandelier over its bowl, on the storey above the bowl's: four drums of crystal strings in gold
    rims, narrowing up the rod, drops round the foot of each, and the rod up to the ceiling with a rose there. Its foot, the
    drops under the widest tier, is 0.138 m under the floor of its storey. The origin is the rod."""
    model = Model("chandelier_tiers", hung=True)
    gold, crystal = bmesh.new(), bmesh.new()
    down = -CHANDELIER_STOREY
    for radius, foot, top, sides in CHANDELIER_TIERS:
        fluted(crystal, [(radius, foot + down), (radius, top + down)], sides, 0.08)
        torus(gold, 0.0, 0.0, top + down, radius + 0.01, 0.012, segments=sides, steps=4)
        torus(gold, 0.0, 0.0, foot + down + 0.01, radius + 0.01, 0.012, segments=sides, steps=4)
        for i in range(sides // 2):
            a = 2 * math.pi * (2 * i + 0.5) / sides
            drop(crystal, (radius + 0.004) * math.cos(a), (radius + 0.004) * math.sin(a), foot + down, 0.016)
    ceiling = CHANDELIER_STOREY
    lathe(gold, [(0.0, CHANDELIER_TIERS[0][1] + down), (0.016, CHANDELIER_TIERS[0][1] + down), (0.016, ceiling - 0.08), (0.05, ceiling - 0.06),
                 (0.11, ceiling - 0.02), (0.11, ceiling), (0.0, ceiling)], segments=12)
    model.turned(gold, "metal", sharp=50.0)
    model.turned(crystal, "ceramic", sharp=20.0)
    return model


def wall_cross():
    """A cross hung on a wall, the wall at +Y: an upright 1.2 m long and a crossbar 0.7 m long a third of the way down it,
    bars 0.094 wide standing 0.047 off the wall, each with a sunk groove down the middle of its face between two raised rims,
    the upright's groove running on through the crossing."""
    model = Model("wall_cross", hung=True)
    rims, core = bmesh.new(), bmesh.new()
    high, wide, bar, deep, sunk, groove = 1.2, 0.7, 0.094, 0.047, 0.008, 0.032
    arm = high - 0.33
    rim = (bar - groove) / 2
    cuboid(core, -bar / 2, bar / 2, -deep + sunk, 0.0, 0.0, high)
    cuboid(core, -wide / 2, wide / 2, -deep + sunk, 0.0, arm - bar / 2, arm + bar / 2)
    for x0 in (-bar / 2, bar / 2 - rim):
        cuboid(rims, x0, x0 + rim, -deep, -deep + sunk, 0.0, high)
    for x0, x1 in ((-wide / 2, -bar / 2), (bar / 2, wide / 2)):
        for z0 in (arm - bar / 2, arm + bar / 2 - rim):
            cuboid(rims, x0, x1, -deep, -deep + sunk, z0, z0 + rim)
        end = x0 if x0 < 0 else x1 - rim
        cuboid(rims, end, end + rim, -deep, -deep + sunk, arm - bar / 2 + rim, arm + bar / 2 - rim)
    model.hard(rims, "plastic", bevel=0.002, segments=1)
    model.plain(core, "rubber")
    return model


# The gold cross with rays behind it that the game paints over two squares of a wall and two storeys: the foot of its upright
# over the floor, its length, where the crossbar crosses it, and the rays, (angle from straight up in degrees, length).
GLORY_FOOT, GLORY_HIGH, GLORY_CROSSING = 1.0, 3.0, 2.26
GLORY_RAYS = [(8, 1.62), (-12, 1.45), (24, 1.2), (-30, 1.5), (40, 1.35), (-48, 1.05), (58, 1.25), (-66, 1.3), (78, 1.2),
              (-84, 1.05), (100, 1.3), (-104, 1.0), (122, 1.1), (-126, 1.38), (144, 1.55), (-150, 1.2), (164, 1.68), (-170, 1.4)]


def glory_cross():
    """A tall gold cross on a wall, the wall at +Y, with pale gold rays fanning out from where its arms cross, flat against the
    wall behind it: an upright 3 m long and 0.15 across standing 0.12 off the wall, a crossbar 1.81 m long, and rays up to
    1.68 m long. Its foot is the foot of the upright, 1 m over the floor where the game paints it."""
    model = Model("glory_cross", hung=True)
    gold, rays = bmesh.new(), bmesh.new()
    bar, deep, arm = 0.15, 0.12, 1.81
    cuboid(gold, -bar / 2, bar / 2, -deep, 0.0, 0.0, GLORY_HIGH)
    cuboid(gold, -arm / 2, arm / 2, -deep, 0.0, GLORY_CROSSING - bar / 2, GLORY_CROSSING + bar / 2)
    for angle, length in GLORY_RAYS:
        before = set(rays.verts)
        cuboid(rays, -0.018, 0.018, 0.0, 0.02, GLORY_CROSSING + 0.1, GLORY_CROSSING + length)
        bmesh.ops.rotate(rays, cent=(0.0, 0.0, GLORY_CROSSING), matrix=Matrix.Rotation(math.radians(angle), 3, "Y"),
                         verts=[v for v in rays.verts if v not in before])
    model.hard(gold, "metal", bevel=0.006, segments=2)
    model.hard(rays, "shade", bevel=0.003, segments=1)
    return model


def church_fittings():
    return [chandelier_bowl(), chandelier_tiers(), wall_cross(), glory_cross()]


def every():
    return [bed("double_bed", 1.80, 2.00, 2), bed("single_bed", 0.90, 1.90, 1), wardrobe(), dining_chair(), small_table(),
              settee("sofa", 1.90, 2), settee("armchair", 1.02, 1), bedside_cabinet(), dresser(), desk(), office_chair(),
              cabinet("counter", 0.62, 0.90, True), cabinet("counter_sink", 0.62, 0.90, True, sink=True),
              cabinet("counter_corner", 0.62, 0.90, True, corner=True), cabinet("wall_cupboard", 0.34, 0.70, False),
              cabinet("wall_cupboard_corner", 0.34, 0.70, False, corner=True), fridge(), oven(), bookcase(), locker(), tv(), tv_wide(), tv_console(), lit_screen(),
              table_lamp(), toilet(), bath(), washing_machine(), wheelie_bin(), litter_bin(), mailbox(), postbox(), park_bench(),
              picnic_table(), crate(), barrel(),
              framed("frame_tall", 0.6, 0.9), framed("frame_square", 0.7, 0.7), framed("frame_wide", 0.9, 0.6), canvas(), sheet(), wall_clock(),
              table("table_2", 1.9, 0.92), table("table_3", 2.9, 0.92), table("table_4", 1.9, 1.9, leg=0.08), table("narrow_table", 0.92, 0.5),
              table("low_table", 0.9, 0.5, top=0.42, leg=0.05), table("low_table_2", 1.5, 0.6, top=0.42, leg=0.05), round_table(), office_desk(),
              cabinet("shop_counter", 0.62, 0.90, True, panelled=True), low_shelves(), wall_shelf("wall_shelf", 1), wall_shelf("wall_shelves", 2), shop_wall_shelves("shop_wall_shelves", "wood"), shop_wall_shelves("zippee_wall_shelves", "vinyl"), hood_dryer(), car_wash_brush(), car_wash_curtain(), bar_back(), bar_bottles("bar_bottles_many", 51, 0.9), bar_bottles("bar_bottles_few", 52, 0.35), sewer_pipes("sewer_pipes_bend", 0.46, True), sewer_pipes("sewer_pipes", 0.25, False), clothes_wall_rails(), generator(), marker_ball(), hospital_curtain("curtain_rail", 0.0, 0.0, 0), hospital_curtain("curtain_drawn", -0.49, 0.49, 10), hospital_curtain("curtain_gathered", 0.12, 0.49, 6), mantel_pillar(), mantel_lintel(), pipe_upright("pipe_upright", None), pipe_upright("pipe_upright_run", "long"), pipe_upright("pipe_upright_stub", "stub"), atm(), coop(), street_name_sign(), street_blade(),
              metal_rack(), store_shelves(), plastic_chair(), garden_chair(), patio_table(), folding_chair(), booth(), stool(), vending_machine(),
              radio(), microwave(), dishwasher(), ice_chest(), register(), shower("shower", True), shower("shower_head", False),
              kettle_barbecue(), gas_barbecue(), cooler("mini_fridge", 0.5, 0.82, 1), cooler("large_fridge", 1.7, 1.95, 2),
              street_lamp("cone_lamp", "cone"), street_lamp("globe_lamp", "globe"), street_lamp("lantern_lamp", "lantern"),
              cobra_head(), traffic_light(), traffic_light("traffic_light_r", 1), wall_bowl(), wall_lantern(), bulkhead_light(), wall_sink(), drinking_fountain(), serving_counter(), air_conditioner(),
              parasol("parasol", [(0.045, 2.40), (0.35, 2.25), (0.68, 2.08), (1.0, 1.91)], 0.10, 0.06),
              parasol("parasol_dome", [(0.045, 2.43), (0.24, 2.39), (0.46, 2.28), (0.66, 2.11), (0.82, 1.88), (0.92, 1.62)], 0.08, 0.06)] + (indoors() + outdoors()
            + upholstered() + barrels_and_pots() + street() + display_cases() + awnings() + hoods() + industrial()
            + street_clutter() + home_fittings() + overhead() + office_medical() + wall_objects() + camp()
            + towers() + aerials() + farm_and_garden() + leisure() + shop_fittings() + dumpsters()
            + bar_panelling() + sports() + shops() + log_walls() + gallery() + cameras() + netarcade() + pulpit() + church_fittings())


def main():
    global plain, soft_whole
    models = every()
    plain = True
    plains = every()
    soft_whole = True
    firmer = every()
    plain = soft_whole = False
    data = [b"CUFM", struct.pack("<II", 3, sum(len(model.shares or [model]) for model in models))]
    totals = [0, 0, 0, 0]
    for model, less, firm in zip(models, plains, firmer):
        if less.apart(model) > PLAIN_DETAIL:
            less = firm
        apart = less.apart(model)
        model.finish()
        less.finish(model)
        corners = less_corners = 0
        for piece, plain_piece in zip(model.cut(), less.cut()):
            data += [piece.header(apart), piece.packed(), plain_piece.packed()]
            corners += piece.corners
            less_corners += plain_piece.corners
        print("%-20s %5d triangles %5d vertices  plain %5d triangles %5d vertices, within %.4f  x %.3f to %.3f  y %.3f to %.3f  z %.3f to %.3f  "
              "shaped %d  sharpest shade over %.3f" % (
                  model.name, len(model.vertices) // 3, corners, len(less.vertices) // 3, less_corners, apart,
                  model.low.x, model.high.x, model.low.y, model.high.y, model.low.z, model.high.z, model.shaped, model.longest_shade))
        if less.longest_shade:
            raise SystemExit("the plain %s still has a sharp shade over %.3f" % (model.name, less.longest_shade))
        for i, count in enumerate((len(model.vertices) // 3, corners, len(less.vertices) // 3, less_corners)):
            totals[i] += count
    print("in all %d triangles %d vertices, plain %d triangles %d vertices" % tuple(totals))
    heavy = ["%s (%d)" % (model.name, model.most) for model in models if len(model.vertices) // 3 > model.most]
    if heavy:
        raise SystemExit("more triangles than allowed in %s" % ", ".join(heavy))
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
