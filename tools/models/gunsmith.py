"""What the scripts that make the mod's own firearms share: the tools that turn, loft, bevel and
cut a part, the materials of steel, alloy, brass and rubber, and the unwrapping, baking,
packing and rendering of the finished gun.

It is imported by shotgun.py, rifle.py, pistol.py, revolver.py and dbarrel.py, which run headless
in Blender 4.4, on one thread where they pack, and say how each is made and run. A model is in
metres, its barrel along +y and its top +z, as the game's own weapons are made; what a script
makes is packed about the hand that holds its grip. A part is an object made by `take`, which
every script's parts go through, in the order they are made; `PARTS` lists them for the
unwrapping and the bake.

The mesh file each script writes is gzip over this, all little-endian:

    4 bytes   the ASCII letters CUWM
    uint32    format version, 4 or 5
    float32   x 3: where the muzzle is
    float32   x 3: the middle of what the hand that works the action holds, if anything
    float32   x 3: the middle of the port a spent shell or case leaves by
    float32   how far back what slides to work the action travels
    float32   how far over the grip, the file's origin, the eye is with the sights lined up: not the bore, which is higher
    float32   how far behind the grip the eye is then
    uint32    number of vertices
    version 4:
      uint32 x 5: how many indices each of five parts has
    version 5:
      uint32    how many parts there are, then that many uint32: how many indices each has
      uint32    how many numbers follow that say how the parts move, then the float32s
    per vertex:
      float32 x 3   position
      int16 x 3     unit normal, 32767 for 1
      int16 x 3     unit tangent along the texture's u
      int16         1 or -1: which way the texture's v runs round the normal from the tangent
      uint16 x 2    place in the texture, 65535 for 1, v from the bottom
    uint32 per index, each part's after the last's

and two pictures beside it, TEXTURE texels wide and texture_high() high, four bytes a texel from
the bottom row up:

    NAME.colour.gz   the colour of the surface, and in alpha how much of the light all round
                     reaches it past the rest of the gun
    NAME.surface.gz  the x and y of the surface's normal in the triangle's own frame, how rough
                     it is, and how much of a metal
"""

import gzip
import math
import os
import struct
import sys

import bmesh
import bpy
import numpy
from mathutils import Matrix, Vector

HERE = os.path.dirname(os.path.abspath(__file__))
OUTPUT = os.path.join(HERE, "..", "..", "src", "main", "resources", "models")
TEXTURES = os.path.join(HERE, "..", "..", "src", "textures", "models")
# The side of the pictures baked, in texels: a script sets its own before it unwraps.
TEXTURE = 2048
# Whether the pictures are twice as wide as high, TEXTURE by TEXTURE / 2, which halves what a long thin gun's cost the
# jar: a long gun's script sets it before it unwraps, and tools/textures/compress.py lists the gun in WIDE. Else square.
WIDE = False


def texture_high():
    """How many rows the pictures have."""
    return TEXTURE // 2 if WIDE else TEXTURE


def smooth(t):
    t = min(max(t, 0.0), 1.0)
    return t * t * (3.0 - 2.0 * t)


def along(keys, count):
    """Stations through a list of keys, each a tuple of numbers, run smoothly from one to the next."""
    out = []
    last = len(keys) - 1
    for n in range(count):
        at = n / (count - 1) * last
        i = min(int(at), last - 1)
        t = at - i
        p0, p1, p2, p3 = keys[max(i - 1, 0)], keys[i], keys[i + 1], keys[min(i + 2, last)]
        out.append(tuple(0.5 * (2 * b + (c - a) * t + (2 * a - 5 * b + 4 * c - d) * t * t + (3 * b - a - 3 * c + d) * t * t * t)
                         for a, b, c, d in zip(p0, p1, p2, p3)))
    return out


def cuboid(bm, x0, x1, y0, y1, z0, z1):
    verts = [bm.verts.new((x, y, z)) for z in (z0, z1) for x, y in ((x0, y0), (x1, y0), (x1, y1), (x0, y1))]
    for a, b, c, d in ((3, 2, 1, 0), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)):
        bm.faces.new((verts[a], verts[b], verts[c], verts[d]))
    return verts


def lathe(bm, profile, to, segments=32):
    """Turns a profile of (radius, height) round its own upright, then moves the whole by a matrix. A radius of 0 is a point on the axis."""
    rings = []
    for radius, height in profile:
        if radius <= 1e-7:
            rings.append([bm.verts.new(to @ Vector((0.0, 0.0, height)))])
        else:
            rings.append([bm.verts.new(to @ Vector((radius * math.cos(2 * math.pi * i / segments), radius * math.sin(2 * math.pi * i / segments), height)))
                          for i in range(segments)])
    for a, b in zip(rings, rings[1:]):
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
    for ring, turn in ((rings[0], -1), (rings[-1], 1)):
        if len(ring) > 1:
            bm.faces.new(ring[::turn])


def along_y(x=0.0, y=0.0, z=0.0):
    """Stands a turned part's upright along the barrel."""
    return Matrix.Translation((x, y, z)) @ Matrix.Rotation(math.radians(-90), 4, "X")


def along_x(x=0.0, y=0.0, z=0.0):
    return Matrix.Translation((x, y, z)) @ Matrix.Rotation(math.radians(90), 4, "Y")


def loft(bm, rings, caps=True):
    """Skins a run of closed rings, each a list of points, all of one length."""
    made = [[bm.verts.new(point) for point in ring] for ring in rings]
    count = len(made[0])
    for a, b in zip(made, made[1:]):
        for i in range(count):
            j = (i + 1) % count
            bm.faces.new((a[i], a[j], b[j], b[i]))
    if caps:
        ends = [bm.faces.new(made[0][::-1]), bm.faces.new(made[-1])]
        bmesh.ops.triangulate(bm, faces=ends, ngon_method="EAR_CLIP")
    return made


def sweep(bm, path, half_wide, thick):
    """Draws a flat bar along a path of (y, z) in the plane the gun is symmetrical about. thick is a number or one for each point."""
    rings = []
    for n, (y, z) in enumerate(path):
        before, after = path[max(n - 1, 0)], path[min(n + 1, len(path) - 1)]
        way = Vector((after[0] - before[0], after[1] - before[1])).normalized()
        out = Vector((-way.y, way.x))
        half = (thick[n] if isinstance(thick, (list, tuple)) else thick) / 2
        rings.append([(sx * half_wide, y + sy * out.x * half, z + sy * out.y * half) for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1))])
    loft(bm, rings)


def rod(bm, path, radii, segments=16):
    """A round bar along a path of points, as thick at each as radii says."""
    rings = []
    for n, point in enumerate(path):
        before, after = Vector(path[max(n - 1, 0)]), Vector(path[min(n + 1, len(path) - 1)])
        turn = (after - before).normalized().to_track_quat("Z", "Y").to_matrix()
        rings.append([Vector(point) + turn @ Vector((radii[n] * math.cos(2 * math.pi * i / segments), radii[n] * math.sin(2 * math.pi * i / segments), 0.0))
                      for i in range(segments)])
    loft(bm, rings)


def screw(bm, x, y, z, radius, to):
    """A slotted screw's head, a little proud of what it is in, its top the way `to` turns +z."""
    lathe(bm, [(0.0, -0.0006), (radius, -0.0006), (radius, 0.0004), (radius - 0.0004, 0.0008), (0.0, 0.0008)], Matrix.Translation((x, y, z)) @ to, segments=20)


def stock_ring(y, top, bottom, half, square, comb, count=56, grow=0.0):
    """A cross section of a stock across the gun at y: rounded square, its comb narrower than its belly."""
    middle, tall = (top + bottom) / 2, (top - bottom) / 2
    ring = []
    for i in range(count):
        angle = 2 * math.pi * (i + 0.5) / count
        c, s = math.cos(angle), math.sin(angle)
        x = (half + grow) * math.copysign(abs(c) ** (2 / square), c)
        z = (tall + grow) * math.copysign(abs(s) ** (2 / square), s)
        if z > 0:
            x *= 1 - (1 - comb) * (z / (tall + grow)) ** 1.5
        ring.append((x, y, middle + z))
    return ring


def grip_point(front, back, half, square, comb, angle, grow=0.0, out=(0.0, 0.0)):
    """A point of a grip's cross section, in the plane through its front and back edge points (y, z) and across the gun,
    `angle` radians round it: from its front at minus a quarter turn through its right side at nought to its back."""
    middle = ((front[0] + back[0]) / 2 + out[0], (front[1] + back[1]) / 2 + out[1])
    deep = (back[0] - front[0], back[1] - front[1])
    tall = math.hypot(*deep) / 2
    way = (deep[0] / (2 * tall), deep[1] / (2 * tall))
    c, s = math.cos(angle), math.sin(angle)
    x = (half + grow) * math.copysign(abs(c) ** (2 / square), c)
    z = (tall + grow) * math.copysign(abs(s) ** (2 / square), s)
    if z > 0:
        x *= 1 - (1 - comb) * (z / (tall + grow)) ** 1.5
    return (x, middle[0] + way[0] * z, middle[1] + way[1] * z)


def grip_ring(front, back, half, square, comb, count=56, grow=0.0, out=(0.0, 0.0)):
    """A cross section of a grip, in the plane through its front and back edge points (y, z) and across the gun."""
    return [grip_point(front, back, half, square, comb, 2 * math.pi * (i + 0.5) / count, grow, out) for i in range(count)]


def prism(bm, outline, across, low, high):
    """A flat piece: an outline of points in the plane square to `across` ("x", "y" or "z"), given in the order of the
    other two axes (counter-clockwise seen from + across), stood between the planes at low and high along it."""
    def point(a, b, c):
        return {"x": (c, a, b), "y": (a, c, b), "z": (a, b, c)}[across]
    lower = [bm.verts.new(point(a, b, low)) for a, b in outline]
    upper = [bm.verts.new(point(a, b, high)) for a, b in outline]
    bm.faces.new(upper)
    bm.faces.new(lower[::-1])
    count = len(outline)
    for i in range(count):
        j = (i + 1) % count
        bm.faces.new((lower[i], lower[j], upper[j], upper[i]))


PARTS = []
PART_NAMES = []


def take(bm, name, material, group=0, bevel=0.0, segments=2, sharp=30.0, subdivide=0, cut=(), texels=1.0):
    """Makes an object of a part: its hard edges rounded off, its faces smooth between them."""
    bmesh.ops.remove_doubles(bm, verts=bm.verts[:], dist=1e-6)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    bm.free()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    for cutter_of in cut:
        hole = obj.modifiers.new("cut", "BOOLEAN")
        hole.operation = "DIFFERENCE"
        hole.solver = "EXACT"
        hole.object = cutter_of
    if subdivide:
        smoothed = obj.modifiers.new("subdivision", "SUBSURF")
        smoothed.levels = subdivide
    if bevel:
        rounded = obj.modifiers.new("bevel", "BEVEL")
        rounded.width = bevel
        rounded.segments = segments
        rounded.limit_method = "ANGLE"
        rounded.angle_limit = math.radians(sharp)
        rounded.harden_normals = False
    done = bpy.data.meshes.new_from_object(obj.evaluated_get(bpy.context.evaluated_depsgraph_get()))
    obj.modifiers.clear()
    obj.data = done
    bpy.data.meshes.remove(mesh)
    done.shade_smooth()
    done.set_sharp_from_angle(angle=math.radians(sharp))
    flat = obj.modifiers.new("normals", "WEIGHTED_NORMAL")
    flat.keep_sharp = True
    flat.weight = 80
    done.materials.append(material_of(material))
    obj["group"] = group
    obj["texels"] = texels
    PARTS.append(obj)
    return obj


def cutter(bm, bevel=0.0):
    """Something to cut out of a part: an object that is not itself part of the gun."""
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    mesh = bpy.data.meshes.new("cutter")
    bm.to_mesh(mesh)
    bm.free()
    obj = bpy.data.objects.new("cutter", mesh)
    bpy.context.scene.collection.objects.link(obj)
    if bevel:
        rounded = obj.modifiers.new("bevel", "BEVEL")
        rounded.width = bevel
        rounded.segments = 4
        rounded.limit_method = "NONE"
        rounded.affect = "EDGES"
        mesh.attributes.new("bevel_weight_edge", "FLOAT", "EDGE")
        rounded.limit_method = "WEIGHT"
        for edge in mesh.edges:
            a, b = mesh.vertices[edge.vertices[0]].co, mesh.vertices[edge.vertices[1]].co
            # Only the edges that run across the gun: the corners of a port as it is seen from the side.
            mesh.attributes["bevel_weight_edge"].data[edge.index].value = 1.0 if abs(a.x - b.x) > 1e-5 else 0.0
    obj.hide_render = True
    obj.display_type = "WIRE"
    return obj


def count_triangles():
    depsgraph = bpy.context.evaluated_depsgraph_get()
    total = 0
    for obj in PARTS:
        triangles = sum(len(polygon.vertices) - 2 for polygon in obj.evaluated_get(depsgraph).data.polygons)
        total += triangles
        print("%-18s %6d triangles" % (obj.name, triangles))
    print("%-18s %6d triangles" % ("in all", total))


def clear():
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj)


PICTURES = {}
GRAIN = {}


def load_pictures(folder, names=("walnut", "rubber")):
    """Reads the generated pictures and finds which way the lines in each run, so that they can be laid along the gun."""
    for name in names:
        image = bpy.data.images.load(os.path.join(os.path.abspath(folder), name + ".png"))
        PICTURES[name] = image
        pixels = numpy.empty(image.size[0] * image.size[1] * 4, dtype=numpy.float32)
        image.pixels.foreach_get(pixels)
        grey = pixels.reshape(image.size[1], image.size[0], 4)[..., :3].mean(axis=2)
        across = numpy.roll(grey, -1, axis=1) - numpy.roll(grey, 1, axis=1)
        up = numpy.roll(grey, -1, axis=0) - numpy.roll(grey, 1, axis=0)
        # The way the picture changes fastest is across its lines.
        steepest = 0.5 * math.atan2(2 * float((across * up).mean()), float((across * across).mean() - (up * up).mean()))
        GRAIN[name] = steepest + math.pi / 2
        print("%-8s lines run at %.1f degrees" % (name, math.degrees(GRAIN[name])))


class Graph:
    """The nodes of one material, written as sums. A value is a socket or a number."""

    def __init__(self, name):
        self.material = bpy.data.materials.new(name)
        self.material.use_nodes = True
        self.tree = self.material.node_tree
        self.shader = self.tree.nodes["Principled BSDF"]
        place = self.tree.nodes.new("ShaderNodeTexCoord")
        self.where = place.outputs["Object"]
        self.x, self.y, self.z = self.split(self.where)
        self.nx, self.ny, self.nz = self.split(place.outputs["Normal"])
        self.pointiness = self.tree.nodes.new("ShaderNodeNewGeometry").outputs["Pointiness"]

    def put(self, socket, value):
        if isinstance(value, (int, float)):
            socket.default_value = value
        else:
            self.tree.links.new(value, socket)

    def split(self, vector):
        node = self.tree.nodes.new("ShaderNodeSeparateXYZ")
        self.put(node.inputs[0], vector)
        return node.outputs[0], node.outputs[1], node.outputs[2]

    def math(self, operation, *values):
        node = self.tree.nodes.new("ShaderNodeMath")
        node.operation = operation
        for socket, value in zip(node.inputs, values):
            self.put(socket, value)
        return node.outputs[0]

    def add(self, *values):
        total = values[0]
        for value in values[1:]:
            total = self.math("ADD", total, value)
        return total

    def times(self, *values):
        total = values[0]
        for value in values[1:]:
            total = self.math("MULTIPLY", total, value)
        return total

    def step(self, value, low, high):
        """Nought at low and below, one at high and above, smooth between."""
        node = self.tree.nodes.new("ShaderNodeMapRange")
        node.interpolation_type = "SMOOTHSTEP"
        self.put(node.inputs["Value"], value)
        self.put(node.inputs["From Min"], low)
        self.put(node.inputs["From Max"], high)
        return node.outputs[0]

    def teeth(self, value):
        """A row of ridges one apart: nought in each groove, one on each crest."""
        return self.times(self.math("ABSOLUTE", self.math("SUBTRACT", self.math("FRACT", value), 0.5)), 2.0)

    def diamonds(self, pitch):
        """Diamonds `pitch` metres apart across the sides of the gun: nought in each cut, one on each crest."""
        one = self.teeth(self.add(self.times(self.y, 0.866 / pitch), self.times(self.z, 0.5 / pitch)))
        other = self.teeth(self.math("SUBTRACT", self.times(self.y, 0.866 / pitch), self.times(self.z, 0.5 / pitch)))
        return self.math("MINIMUM", one, other)

    def checkered(self, nearest, pitch, side):
        """Chequering within a panel that `nearest` measures, on what `side` says is the side of the gun: how deep each
        place is cut, where the line round the panel is, and the diamonds."""
        cut = self.times(self.math("SUBTRACT", 1.0, self.step(nearest, -0.0003, 0.0003)), side)
        line = self.times(self.math("SUBTRACT", 1.0, self.step(self.math("ABSOLUTE", self.add(nearest, 0.0016)), 0.0003, 0.0007)), side)
        return cut, line, self.diamonds(pitch)

    def colour(self, red, green, blue):
        node = self.tree.nodes.new("ShaderNodeRGB")
        node.outputs[0].default_value = (red, green, blue, 1.0)
        return node.outputs[0]

    def scaled(self, colour, by):
        node = self.tree.nodes.new("ShaderNodeVectorMath")
        node.operation = "SCALE"
        self.put(node.inputs[0], colour)
        self.put(node.inputs["Scale"], by)
        return node.outputs[0]

    def tinted(self, colour, tint):
        node = self.tree.nodes.new("ShaderNodeVectorMath")
        node.operation = "MULTIPLY"
        self.put(node.inputs[0], colour)
        self.put(node.inputs[1], tint)
        return node.outputs[0]

    def mix(self, share, first, second):
        node = self.tree.nodes.new("ShaderNodeMix")
        node.data_type = "RGBA"
        named = {socket.identifier: socket for socket in node.inputs}
        self.put(named["Factor_Float"], share)
        self.put(named["A_Color"], first)
        self.put(named["B_Color"], second)
        return next(socket for socket in node.outputs if socket.identifier == "Result_Color")

    def grey(self, colour):
        node = self.tree.nodes.new("ShaderNodeRGBToBW")
        self.put(node.inputs[0], colour)
        return node.outputs[0]

    def laid(self, name, span, turn=0.0):
        """A picture laid with its lines along the barrel, or turned from it by `turn` radians in the plane the gun is
        symmetrical about, span metres to a repeat. It is drawn out through the gun on a slant, so that it is not
        smeared on any face but one that slants the same way."""
        angle = GRAIN[name]
        lengthwise, up = self.y, self.z
        if turn:
            lengthwise = self.add(self.times(self.y, math.cos(turn)), self.times(self.z, math.sin(turn)))
            up = self.math("SUBTRACT", self.times(self.z, math.cos(turn)), self.times(self.y, math.sin(turn)))
        over = self.add(self.times(up, 0.82), self.times(self.x, 0.57))
        u = self.math("SUBTRACT", self.times(lengthwise, math.cos(angle) / span), self.times(over, math.sin(angle) / span))
        v = self.add(self.times(lengthwise, math.sin(angle) / span), self.times(over, math.cos(angle) / span))
        place = self.tree.nodes.new("ShaderNodeCombineXYZ")
        self.put(place.inputs[0], u)
        self.put(place.inputs[1], v)
        node = self.tree.nodes.new("ShaderNodeTexImage")
        node.image = PICTURES[name]
        self.put(node.inputs[0], place.outputs[0])
        return node.outputs["Color"]

    def boxed(self, name, span):
        node = self.tree.nodes.new("ShaderNodeTexImage")
        node.image = PICTURES[name]
        node.projection = "BOX"
        node.projection_blend = 0.3
        self.put(node.inputs[0], self.scaled(self.where, 1.0 / span))
        return node.outputs["Color"]

    def noise(self, scale, detail=3.0):
        node = self.tree.nodes.new("ShaderNodeTexNoise")
        self.put(node.inputs["Vector"], self.where)
        self.put(node.inputs["Scale"], scale)
        self.put(node.inputs["Detail"], detail)
        return node.outputs[0]

    def streaks(self):
        """The fine lines a part is left with by being polished along its length: about nought, a quarter either way."""
        drawn = self.tree.nodes.new("ShaderNodeVectorMath")
        drawn.operation = "MULTIPLY"
        self.put(drawn.inputs[0], self.where)
        drawn.inputs[1].default_value = (1100.0, 22.0, 1100.0)
        node = self.tree.nodes.new("ShaderNodeTexNoise")
        self.put(node.inputs["Vector"], drawn.outputs[0])
        node.inputs["Scale"].default_value = 1.0
        node.inputs["Detail"].default_value = 2.0
        return self.math("SUBTRACT", node.outputs[0], 0.5)

    def worn(self, most=1.0):
        """Where the finish is rubbed through: along the edges that stand out, brokenly, and thinly here and there on the flats."""
        # An edge is where the surface, rounded off over a couple of millimetres, faces another way than it does itself.
        rounded = self.tree.nodes.new("ShaderNodeBevel")
        rounded.samples = 8
        rounded.inputs["Radius"].default_value = 0.0022
        facing = self.tree.nodes.new("ShaderNodeNewGeometry")
        turn = self.tree.nodes.new("ShaderNodeVectorMath")
        turn.operation = "DOT_PRODUCT"
        self.put(turn.inputs[0], rounded.outputs[0])
        self.put(turn.inputs[1], facing.outputs["True Normal"])
        edge = self.times(self.step(self.math("SUBTRACT", 1.0, turn.outputs["Value"]), 0.012, 0.11), self.step(self.pointiness, 0.498, 0.512))
        broken = self.step(self.noise(240.0, 4.0), 0.36, 0.62)
        thin = self.times(self.step(self.noise(30.0, 5.0), 0.62, 0.86), self.step(self.noise(500.0, 4.0), 0.40, 0.75), 0.16)
        return self.math("MINIMUM", self.times(self.add(self.times(edge, broken, 0.85), thin), most), 1.0)

    def panel(self, y, z, half_long, half_high, corner, turn):
        """How far a place is outside a rounded panel on the side of the gun, in metres: less than nought within it."""
        dy, dz = self.math("SUBTRACT", self.y, y), self.math("SUBTRACT", self.z, z)
        along_it = self.add(self.times(dy, math.cos(turn)), self.times(dz, math.sin(turn)))
        up_it = self.math("SUBTRACT", self.times(dz, math.cos(turn)), self.times(dy, math.sin(turn)))
        qa = self.math("SUBTRACT", self.math("ABSOLUTE", along_it), half_long - corner)
        qb = self.math("SUBTRACT", self.math("ABSOLUTE", up_it), half_high - corner)
        outside = self.math("SQRT", self.add(self.math("POWER", self.math("MAXIMUM", qa, 0.0), 2.0), self.math("POWER", self.math("MAXIMUM", qb, 0.0), 2.0)))
        inside = self.math("MINIMUM", self.math("MAXIMUM", qa, qb), 0.0)
        return self.math("SUBTRACT", self.add(outside, inside), corner)

    def finish(self, colour, rough, metal, height):
        self.sockets = {"colour": colour, "rough": rough, "metal": metal}
        self.put(self.shader.inputs["Base Color"], colour)
        self.put(self.shader.inputs["Roughness"], rough)
        self.put(self.shader.inputs["Metallic"], metal)
        bump = self.tree.nodes.new("ShaderNodeBump")
        bump.inputs["Distance"].default_value = 1.0
        self.put(bump.inputs["Height"], height)
        self.tree.links.new(bump.outputs[0], self.shader.inputs["Normal"])
        self.glow = self.tree.nodes.new("ShaderNodeEmission")
        self.baked = self.tree.nodes.new("ShaderNodeTexImage")
        return self

    def show(self, which):
        """Has the material give out one of its own properties as light, to be baked, or with None be itself again."""
        out = self.tree.nodes["Material Output"].inputs["Surface"]
        if which is None:
            self.tree.links.new(self.shader.outputs[0], out)
            return
        for link in list(self.glow.inputs["Color"].links):
            self.tree.links.remove(link)
        value = self.sockets[which]
        if isinstance(value, (int, float)):
            self.glow.inputs["Color"].default_value = (value, value, value, 1.0)
        else:
            self.tree.links.new(value, self.glow.inputs["Color"])
        self.tree.links.new(self.glow.outputs[0], out)


def steel(g, colour, rough, wear=1.0, bare=(0.42, 0.42, 0.43)):
    """Polished steel under a finish: the lines of its polishing, and bare metal where the finish is rubbed through."""
    lines = g.streaks()
    rubbed = g.worn(wear)
    finish = g.scaled(g.colour(*colour), g.add(1.0, g.times(lines, 0.5)))
    return g.mix(rubbed, finish, g.colour(*bare)), g.add(rough, g.times(lines, 0.30), g.times(rubbed, 0.06)), lines


def blued(g, rough=0.30):
    colour, roughness, lines = steel(g, (0.030, 0.033, 0.044), rough)
    return g.finish(colour, roughness, 1.0, g.times(lines, 0.00002))


def bright(g):
    lines = g.streaks()
    return g.finish(g.scaled(g.colour(0.52, 0.52, 0.535), g.add(1.0, g.times(lines, 0.4))), g.add(0.24, g.times(lines, 0.4)), 1.0, g.times(lines, 0.00003))


def black_coat(g, colour=(0.016, 0.016, 0.018), rough=0.40, wear=0.3, bare=(0.50, 0.50, 0.52)):
    """A hard black coat, which wears to the pale metal at its edges: its colour, roughness, metal and height, for finish."""
    rubbed = g.worn(wear)
    grain = g.noise(2600.0, 1.0)
    return g.mix(rubbed, g.colour(*colour), g.colour(*bare)), g.add(rough, g.times(grain, 0.10)), rubbed, g.times(grain, 0.00002)


def black(g):
    """An alloy part in a hard black coat, which wears to the pale metal at its edges."""
    return g.finish(*black_coat(g))


def brass(g):
    return g.finish(g.colour(0.83, 0.61, 0.27), 0.27, 1.0, 0.0)


def ivory(g):
    return g.finish(g.colour(0.72, 0.69, 0.60), 0.45, 0.0, 0.0)


def rubber(g):
    grain = g.grey(g.boxed("rubber", 0.09))
    return g.finish(g.scaled(g.colour(0.020, 0.019, 0.018), g.add(0.6, g.times(grain, 1.5))), 0.80, 0.0, g.times(grain, 0.0006))


def walnut(g, panels, pitch, grain=0.0, span=0.30, tint=(1.45, 1.16, 0.98), smooth=()):
    """Oiled walnut, with a panel of fine diamonds `pitch` metres apart cut into each side of the wood within each of
    panels, given as Graph.panel takes them: where the hand holds it. Its grain runs `grain` radians off the barrel, the
    picture `span` metres to a repeat, coloured by `tint`. Each of smooth, (y, z, half along y, half along z), is a diamond
    left uncut within the panels, round a screw."""
    figure = g.laid("walnut", span, grain)
    light = g.grey(figure)
    colour = g.tinted(g.mix(0.6, figure, g.tinted(figure, figure)), g.colour(*tint))
    side = g.step(g.math("ABSOLUTE", g.nx), 0.30, 0.55)
    nearest = g.panel(*panels[0])
    for panel in panels[1:]:
        nearest = g.math("MINIMUM", nearest, g.panel(*panel))
    for y, z, half_y, half_z in smooth:
        # Outside the diamond, more than nought.
        outside = g.math("SUBTRACT", g.add(g.times(g.math("ABSOLUTE", g.math("SUBTRACT", g.y, y)), 1.0 / half_y),
                                           g.times(g.math("ABSOLUTE", g.math("SUBTRACT", g.z, z)), 1.0 / half_z)), 1.0)
        nearest = g.math("MAXIMUM", nearest, g.times(outside, -0.010))
    cut, line, diamond = g.checkered(nearest, pitch, side)
    height = g.add(g.times(light, 0.00006), g.times(cut, g.math("SUBTRACT", g.times(diamond, 0.00050), 0.00030)), g.times(line, -0.00030))
    colour = g.scaled(colour, g.math("SUBTRACT", 1.0, g.times(cut, g.math("SUBTRACT", 0.50, g.times(diamond, 0.38)))))
    rough = g.add(0.27, g.times(g.math("SUBTRACT", 1.0, light), 0.16), g.times(cut, 0.25))
    return g.finish(colour, rough, 0.0, height)


def rib(g):
    """The ventilated rib over a shotgun's barrel: steel, its top cut across with fine lines so that it does not shine in the eye."""
    colour, roughness, lines = steel(g, (0.024, 0.026, 0.032), 0.55, wear=0.5)
    top = g.step(g.nz, 0.6, 0.9)
    return g.finish(colour, roughness, 1.0, g.times(top, g.teeth(g.times(g.y, 1.0 / 0.0011)), 0.00013))


def hull_at(z):
    """The plastic body of a shotgun shell, ribbed along its length, for a shell lying along y at height z."""
    def make(g):
        round_it = g.math("ARCTAN2", g.x, g.math("SUBTRACT", g.z, z))
        ribs = g.teeth(g.times(round_it, 30 / (2 * math.pi)))
        return g.finish(g.scaled(g.colour(0.42, 0.025, 0.02), g.add(0.85, g.times(ribs, 0.2))), 0.42, 0.0, g.times(ribs, 0.00012))
    return make


# How far apart the diamonds a shotgun's walnut is checkered with are, in metres.
CHECKER_PITCH = 0.0017
# A twelve bore shell's brass head with its rim, as (radius, how far from the back of the rim) along the shell.
SHELL_HEAD = [(0.0, 0.0), (0.0040, 0.0), (0.0040, 0.0008), (0.0108, 0.0008), (0.0112, 0.0012), (0.0112, 0.0022), (0.0103, 0.0028), (0.0103, 0.0160),
              (0.0, 0.0160)]


def shell_head(bm, to, rim):
    """A shell's head turned along the shell's length, the back of its rim at rim along it, moved by to."""
    lathe(bm, [(radius, rim + along_it) for radius, along_it in SHELL_HEAD], to, segments=28)


# Behind a stock's wood: a white line, a hard black plate, and a pad of rubber rounded at the back. Each layer is
# how far behind the butt and how much the cross section has grown, as stock_ring takes them.
PAD = (("spacer", "ivory", ((0.0, 0.0002), (-0.0016, 0.0002))),
       ("pad plate", "black", ((-0.0016, 0.0), (-0.0050, 0.0))),
       ("pad", "rubber", ((-0.0050, 0.0003), (-0.0200, 0.0003), (-0.0235, -0.0008), (-0.0258, -0.0030), (-0.0268, -0.0065))))


def butt_pad(butt, last, group=0, layers=PAD):
    """The layers behind a stock whose last cross section, as stock_ring takes it without its y, is last, at butt."""
    for name, material, pad in layers:
        part = bmesh.new()
        loft(part, [stock_ring(butt + back, *last, grow=grow) for back, grow in pad])
        take(part, name, material, group=group, sharp=40.0)


# Each material by name: a script adds its own, and may put its own in place of one of these.
MAKERS = {"blued": blued, "receiver": lambda g: blued(g, 0.27), "bright": bright, "black": black, "brass": brass, "ivory": ivory, "rubber": rubber, "rib": rib}
GRAPHS = {}


def material_of(name):
    if name not in GRAPHS:
        GRAPHS[name] = MAKERS[name](Graph(name))
    return GRAPHS[name].material


# Each tube's axis, where along it its two lengths part, and the radius it is unrolled at, by the name of its part: a script's own.
TUBES = {}


def select(obj):
    for other in bpy.context.view_layer.objects:
        other.select_set(other == obj)
    bpy.context.view_layer.objects.active = obj


def unwrap_tube(mesh, axis_z, halfway, radius):
    """A tube is unrolled whole: its length along u and the way round it along v, in two lengths that part at a ring of
    its vertices, with its ends apart."""
    bm = bmesh.new()
    bm.from_mesh(mesh)
    layer = bm.loops.layers.uv.verify()
    for face in bm.faces:
        middle = face.calc_center_median()
        out = Vector((middle.x, 0.0, middle.z - axis_z))
        if abs(face.normal.y) > 0.7:
            for loop in face.loops:
                loop[layer].uv = (loop.vert.co.x + (10.0 if middle.y > halfway else 5.0), loop.vert.co.z)
            continue
        round_middle = math.atan2(middle.x, middle.z - axis_z)
        inward = face.normal.dot(out) < 0.0
        for loop in face.loops:
            at = loop.vert.co
            turn = math.atan2(at.x, at.z - axis_z)
            while turn - round_middle > math.pi:
                turn -= 2 * math.pi
            while turn - round_middle < -math.pi:
                turn += 2 * math.pi
            # One radius for the whole of it: a triangle unrolled at a radius of its own shares no corner with the next.
            loop[layer].uv = (at.y * (0.5 if inward else 1.0), turn * (0.5 if inward else 1.0) * radius + (0.2 if middle.y > halfway else 0.0) + (0.5 if inward else 0.0))
    bm.to_mesh(mesh)
    bm.free()


# Each island's margin, as a share of the width of the texture: on every side of it, along each axis, as many texels.
ISLAND_MARGIN = 0.0028


def pack_wide(gun):
    """Packs the islands of a gun in edit mode into a texture wider than high, so that a texel is as long as it is
    broad on the gun and the margin round each island is as many texels each way: into a box as much wider than high,
    then drawn out up the texture to fill it. Each is first turned to lie along the texture, the way there is room."""
    bpy.ops.uv.pack_islands(rotate=True, rotate_method="AXIS_ALIGNED_X", margin_method="FRACTION", margin=ISLAND_MARGIN, shape_method="CONCAVE")
    high = texture_high() / TEXTURE
    # Packing to the islands' own bounds packs into a box of their shape: a speck of a triangle at each of two corners
    # makes it the texture's, and they are taken away again after.
    edit = bmesh.from_edit_mesh(gun.data)
    layer = edit.loops.layers.uv.active
    for loop_face in edit.faces:
        for loop in loop_face.loops:
            loop[layer].uv = loop[layer].uv * high
    real = len(edit.verts)
    for corner, way in (((0.0, 0.0), 1.0), ((1.0, high), -1.0)):
        verts = [edit.verts.new((0.0, 0.0, 0.0)) for _ in range(3)]
        face = edit.faces.new(verts)
        for loop, (du, dv) in zip(face.loops, ((0.0, 0.0), (1e-5, 0.0), (0.0, 1e-5))):
            loop[layer].uv = (corner[0] + way * du, corner[1] + way * dv)
    bmesh.update_edit_mesh(gun.data)
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.select_all(action="SELECT")
    bpy.ops.uv.pack_islands(rotate=False, margin_method="FRACTION", margin=ISLAND_MARGIN, shape_method="CONCAVE", udim_source="ORIGINAL_AABB")
    edit = bmesh.from_edit_mesh(gun.data)
    edit.verts.ensure_lookup_table()
    bmesh.ops.delete(edit, geom=edit.verts[real:], context="VERTS")
    layer = edit.loops.layers.uv.active
    for loop_face in edit.faces:
        for loop in loop_face.loops:
            loop[layer].uv = (loop[layer].uv[0], loop[layer].uv[1] / high)
    bmesh.update_edit_mesh(gun.data)


def unwrap(name):
    """Gives every part a place in the texture, at one size to the metre but for what a part is given over the rest,
    and joins them into one object called name."""
    # On more than one thread Blender's tangents, and the normals of corners of triangles of no size, come out a little
    # different from one run to the next, and a revolver's cuts, and so its whole layout, are not the same on one thread
    # as on three.
    if bpy.context.scene.render.threads != 1 and "--no-pack" not in sys.argv:
        raise SystemExit("a gun is laid out and packed on one thread: run Blender with --threads 1, or pass --no-pack to only render")
    depsgraph = bpy.context.evaluated_depsgraph_get()
    for obj in PARTS:
        cut_up = obj.modifiers.new("triangles", "TRIANGULATE")
        cut_up.keep_custom_normals = True
        depsgraph.update()
        mesh = bpy.data.meshes.new_from_object(obj.evaluated_get(depsgraph), preserve_all_data_layers=True, depsgraph=depsgraph)
        obj.modifiers.clear()
        old = obj.data
        obj.data = mesh
        bpy.data.meshes.remove(old)
        group = mesh.attributes.new("group", "INT", "FACE")
        group.data.foreach_set("value", [obj["group"]] * len(mesh.polygons))
        part = mesh.attributes.new("part", "INT", "FACE")
        part.data.foreach_set("value", [PARTS.index(obj)] * len(mesh.polygons))
        # Under one name in every part, or joining them keeps each part's in a layer of its own.
        mesh.uv_layers.new(name="UVMap")
        if obj.name in TUBES:
            unwrap_tube(mesh, *TUBES[obj.name])
            scale = obj["texels"]
        else:
            select(obj)
            bpy.ops.object.mode_set(mode="EDIT")
            bpy.ops.mesh.select_all(action="SELECT")
            bpy.ops.uv.smart_project(angle_limit=math.radians(66), island_margin=0.0, area_weight=0.0, scale_to_bounds=False)
            bpy.ops.object.mode_set(mode="OBJECT")
            mesh = obj.data
            uv = numpy.empty(len(mesh.loops) * 2)
            mesh.uv_layers.active.data.foreach_get("uv", uv)
            uv = uv.reshape(-1, 3, 2)
            flat = 0.5 * numpy.abs(numpy.cross(uv[:, 1] - uv[:, 0], uv[:, 2] - uv[:, 0])).sum()
            scale = math.sqrt(sum(polygon.area for polygon in mesh.polygons) / flat) * obj["texels"]
        mesh = obj.data
        uv = numpy.empty(len(mesh.loops) * 2)
        mesh.uv_layers.active.data.foreach_get("uv", uv)
        mesh.uv_layers.active.data.foreach_set("uv", uv * scale)
        if "--stats" in sys.argv:
            placed = (uv * scale).reshape(-1, 2)
            print("%-18s scale %10.4f spans %.3f by %.3f" % (obj.name, scale, *(placed.max(axis=0) - placed.min(axis=0))), flush=True)

    PART_NAMES.extend(obj.name for obj in PARTS)
    for obj in bpy.context.view_layer.objects:
        obj.select_set(obj in PARTS)
    bpy.context.view_layer.objects.active = PARTS[0]
    bpy.ops.object.join()
    gun = bpy.context.view_layer.objects.active
    gun.name = name
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.select_all(action="SELECT")
    if WIDE:
        pack_wide(gun)
    else:
        bpy.ops.uv.pack_islands(rotate=True, margin_method="FRACTION", margin=ISLAND_MARGIN, shape_method="CONCAVE")
    bpy.ops.object.mode_set(mode="OBJECT")
    mesh = gun.data
    uv = numpy.empty(len(mesh.loops) * 2)
    mesh.uv_layers.active.data.foreach_get("uv", uv)
    uv = uv.reshape(-1, 3, 2)
    flat = 0.5 * numpy.abs(numpy.cross(uv[:, 1] - uv[:, 0], uv[:, 2] - uv[:, 0])).sum()
    print("the texture is %.0f%% used, at %.0f dots to the metre" % (flat * 100, math.sqrt(TEXTURE * texture_high() * flat / sum(polygon.area for polygon in mesh.polygons))))
    return gun


def stats(gun, layout):
    """Draws where each part lies in the texture into the picture `layout`, and says how much of it each part takes."""
    mesh = gun.data
    uv = numpy.empty(len(mesh.loops) * 2)
    mesh.uv_layers.active.data.foreach_get("uv", uv)
    uv = uv.reshape(-1, 3, 2)
    which = numpy.array([polygon.material_index for polygon in mesh.polygons])
    part = numpy.empty(len(mesh.polygons), dtype=numpy.int32)
    mesh.attributes["part"].data.foreach_get("value", part)
    only = os.environ.get("ONLY_PART")
    if only:
        uv = numpy.where((part == PART_NAMES.index(only))[:, None, None], uv, 0.0)
    picture = numpy.zeros((1024, 1024, 4), dtype=numpy.float32)
    picture[..., 3] = 1.0
    for share in numpy.linspace(0.0, 1.0, 200):
        for a, b in ((0, 1), (1, 2), (2, 0)):
            at = numpy.clip(((uv[:, a] * (1 - share) + uv[:, b] * share) * 1023).astype(int), 0, 1023)
            picture[at[:, 1], at[:, 0], 0] = 0.3 + 0.7 * ((which * 37) % 10) / 9.0
            picture[at[:, 1], at[:, 0], 1] = 0.3 + 0.7 * ((which * 53) % 7) / 6.0
            picture[at[:, 1], at[:, 0], 2] = 0.3 + 0.7 * ((which * 71) % 5) / 4.0
    image = bpy.data.images.new("layout", 1024, 1024)
    image.pixels.foreach_set(picture.reshape(-1))
    image.filepath_raw = os.path.abspath(layout)
    image.file_format = "PNG"
    image.save()
    areas = 0.5 * numpy.abs(numpy.cross(uv[:, 1] - uv[:, 0], uv[:, 2] - uv[:, 0]))
    for n, name in enumerate(PART_NAMES):
        mine = uv[part == n].reshape(-1, 2)
        print("%-18s %5.2f%% of the texture, box %.3f %.3f to %.3f %.3f" % (name, areas[part == n].sum() * 100, *mine.min(axis=0), *mine.max(axis=0)))


def bake(gun):
    """Bakes what the materials work out into pictures laid out as the texture is: each a float array, rows from the bottom."""
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    use_card(scene)
    scene.cycles.samples = 16
    scene.render.bake.margin = 12 * TEXTURE // 2048
    scene.render.bake.use_clear = True
    world = bpy.data.worlds.new("bake")
    world.light_settings.distance = 0.05
    scene.world = world
    target = bpy.data.images.new("bake", TEXTURE, texture_high(), float_buffer=True, is_data=True)
    for graph in GRAPHS.values():
        graph.baked.image = target
        graph.tree.nodes.active = graph.baked
    select(gun)
    out = {}

    def taken():
        pixels = numpy.empty(TEXTURE * texture_high() * 4, dtype=numpy.float32)
        target.pixels.foreach_get(pixels)
        return pixels.reshape(texture_high(), TEXTURE, 4)[..., :3].copy()

    for which in ("colour", "rough", "metal"):
        for graph in GRAPHS.values():
            graph.show(which)
        bpy.ops.object.bake(type="EMIT")
        out[which] = taken()
        print("baked", which, flush=True)
    for graph in GRAPHS.values():
        graph.show(None)
    bpy.ops.object.bake(type="NORMAL", normal_space="TANGENT")
    out["normal"] = taken()
    print("baked normal", flush=True)
    scene.cycles.samples = 192
    bpy.ops.object.bake(type="AO")
    out["light"] = taken()
    print("baked light", flush=True)
    return out


def use_card(scene):
    if "--cpu" in sys.argv:
        scene.cycles.device = "CPU"
        return
    preferences = bpy.context.preferences.addons["cycles"].preferences
    preferences.compute_device_type = "OPTIX"
    preferences.get_devices()
    for device in preferences.devices:
        device.use = device.type == "OPTIX"
    scene.cycles.device = "GPU"


def encoded(linear):
    """The sRGB coding of a linear colour, which keeps the steps between dark shades small."""
    linear = numpy.clip(linear, 0.0, 1.0)
    return numpy.where(linear <= 0.0031308, linear * 12.92, 1.055 * numpy.power(linear, 1 / 2.4) - 0.055)


def bytes_of(array):
    return (numpy.clip(array, 0.0, 1.0) * 255.0 + 0.5).astype(numpy.uint8)


def pictures_of(baked):
    colour = numpy.concatenate([encoded(baked["colour"]), baked["light"][..., :1]], axis=2)
    surface = numpy.concatenate([baked["normal"][..., :2], baked["rough"][..., :1], baked["metal"][..., :1]], axis=2)
    return bytes_of(colour), bytes_of(surface)


def pack(gun, name, grip, colour, surface, groups, head, moved=(), mirror=(-1.0, 1.0, 1.0)):
    """Writes the gun's files: name.mesh.gz, and with pictures name.colour.gz and name.surface.gz.

    grip is where the hand that holds it is, which the file has as its origin. groups are the parts' groups in the
    order the file has them. head(vertices, counts) makes the bytes before the vertices. moved is (group, place):
    a part made well clear of the gun, packed about that place of its own. mirror turns the model as the mod holds
    it: the long guns are made to be the mirror image in x of how they are laid out here, the handguns in z."""
    mesh = gun.data
    mesh.calc_tangents(uvmap=mesh.uv_layers.active.name)
    corners = len(mesh.loops)
    index = numpy.empty(corners, dtype=numpy.int32)
    mesh.loops.foreach_get("vertex_index", index)
    place = numpy.empty(len(mesh.vertices) * 3, dtype=numpy.float32)
    mesh.vertices.foreach_get("co", place)
    place = place.reshape(-1, 3)[index] - numpy.array(grip, dtype=numpy.float32)
    normal = numpy.empty(corners * 3, dtype=numpy.float32)
    mesh.loops.foreach_get("normal", normal)
    tangent = numpy.empty(corners * 3, dtype=numpy.float32)
    mesh.loops.foreach_get("tangent", tangent)
    way = numpy.empty(corners, dtype=numpy.float32)
    mesh.loops.foreach_get("bitangent_sign", way)
    uv = numpy.empty(corners * 2, dtype=numpy.float32)
    mesh.uv_layers.active.data.foreach_get("uv", uv)
    group = numpy.empty(len(mesh.polygons), dtype=numpy.int32)
    mesh.attributes["group"].data.foreach_get("value", group)
    starts = numpy.empty(len(mesh.polygons), dtype=numpy.int32)
    mesh.polygons.foreach_get("loop_start", starts)

    rows = numpy.zeros(corners, dtype=[("place", "<f4", 3), ("normal", "<i2", 3), ("tangent", "<i2", 3), ("way", "<i2"), ("uv", "<u2", 2)])
    mirror = numpy.array(mirror, dtype=numpy.float32)
    corner_group = numpy.repeat(group, 3)
    for which, at in moved:
        place[corner_group == which] -= numpy.array(at - grip, dtype=numpy.float32)
    rows["place"] = place * mirror
    rows["normal"] = numpy.round(normal.reshape(-1, 3) * mirror * 32767)
    rows["tangent"] = numpy.round(tangent.reshape(-1, 3) * mirror * 32767)
    # A mirror turns the way v runs round the normal about.
    flipped = numpy.prod(mirror) < 0
    rows["way"] = numpy.where(way < 0, 1 if flipped else -1, -1 if flipped else 1)
    rows["uv"] = numpy.round(numpy.clip(uv.reshape(-1, 2), 0.0, 1.0) * 65535)
    # A corner that is the same in every respect as another is one vertex.
    vertices, which = numpy.unique(rows.view([("all", "V%d" % rows.dtype.itemsize)]), return_inverse=True)
    which = which.reshape(-1)
    order = numpy.argsort(group, kind="stable")
    indices = numpy.concatenate([which[start:start + 3] for start in starts[order]]).astype("<u4")
    counts = [int((group == part).sum()) * 3 for part in groups]
    os.makedirs(OUTPUT, exist_ok=True)
    os.makedirs(TEXTURES, exist_ok=True)
    files = [(name + ".mesh.gz", head(len(vertices), counts) + vertices.tobytes() + indices.tobytes())]
    if colour is not None:
        files += [(name + ".colour.gz", colour.tobytes()), (name + ".surface.gz", surface.tobytes())]
    for file, data in files:
        path = os.path.join(OUTPUT if file.endswith(".mesh.gz") else TEXTURES, file)
        with open(path, "wb") as raw:
            # No name and no time in the gzip header, so the same model packs to the same bytes.
            with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0, compresslevel=9) as packed:
                packed.write(data)
        print("wrote %s, %d bytes" % (os.path.normpath(path), os.path.getsize(path)))
    print("%d vertices, %d triangles, in parts of %s" % (len(vertices), len(indices) // 3, ", ".join(str(count // 3) for count in counts)))


def head_v4(muzzle, held, port, travel, sight_line, eye_behind):
    """The head of a version 4 file, which has five parts, for pack: each place from the grip, as the file has it."""
    return lambda vertices, counts: b"CUWM" + struct.pack("<I3f3f3f3fI5I", 4, *muzzle, *held, *port, travel, sight_line, eye_behind, vertices, *counts)


def head_v5(muzzle, held, port, travel, sight_line, eye_behind, moves):
    """The head of a version 5 file, for pack: any number of parts, and the numbers that say how they move."""
    return lambda vertices, counts: (b"CUWM" + struct.pack("<I3f3f3f3fI", 5, *muzzle, *held, *port, travel, sight_line, eye_behind, vertices)
                                     + struct.pack("<I%dI" % len(counts), len(counts), *counts) + struct.pack("<I%df" % len(moves), len(moves), *moves))


def dress(gun, colour, surface, folder):
    """Puts the baked pictures on the gun in place of the materials they were baked from, as the mod will draw it."""
    material = bpy.data.materials.new("baked")
    material.use_nodes = True
    tree = material.node_tree
    shader = tree.nodes["Principled BSDF"]

    def picture(name, array, is_colour):
        image = bpy.data.images.new(name, TEXTURE, texture_high(), alpha=True, is_data=not is_colour)
        image.pixels.foreach_set((array.astype(numpy.float32) / 255.0).reshape(-1))
        image.filepath_raw = os.path.join(folder, name + ".png")
        image.file_format = "PNG"
        image.save()
        node = tree.nodes.new("ShaderNodeTexImage")
        node.image = image
        return node

    tint, shape = picture("texture-colour", colour, True), picture("texture-surface", surface, False)
    tree.links.new(tint.outputs["Color"], shader.inputs["Base Color"])
    parts = tree.nodes.new("ShaderNodeSeparateColor")
    tree.links.new(shape.outputs["Color"], parts.inputs[0])
    tree.links.new(parts.outputs[2], shader.inputs["Roughness"])
    tree.links.new(shape.outputs["Alpha"], shader.inputs["Metallic"])
    # The third part of the normal is not kept: it is what is left of a length of one.
    under = tree.nodes.new("ShaderNodeVectorMath")
    under.operation = "DOT_PRODUCT"
    centred = tree.nodes.new("ShaderNodeVectorMath")
    centred.operation = "MULTIPLY_ADD"
    centred.inputs[1].default_value = (2.0, 2.0, 0.0)
    centred.inputs[2].default_value = (-1.0, -1.0, 0.0)
    tree.links.new(shape.outputs["Color"], centred.inputs[0])
    tree.links.new(centred.outputs[0], under.inputs[0])
    tree.links.new(centred.outputs[0], under.inputs[1])
    rest = tree.nodes.new("ShaderNodeMath")
    rest.operation = "SUBTRACT"
    rest.inputs[0].default_value = 1.0
    tree.links.new(under.outputs["Value"], rest.inputs[1])
    root = tree.nodes.new("ShaderNodeMath")
    root.operation = "SQRT"
    tree.links.new(rest.outputs[0], root.inputs[0])
    halved = tree.nodes.new("ShaderNodeMath")
    halved.operation = "MULTIPLY_ADD"
    halved.inputs[1].default_value = 0.5
    halved.inputs[2].default_value = 0.5
    tree.links.new(root.outputs[0], halved.inputs[0])
    whole = tree.nodes.new("ShaderNodeCombineColor")
    tree.links.new(parts.outputs[0], whole.inputs[0])
    tree.links.new(parts.outputs[1], whole.inputs[1])
    tree.links.new(halved.outputs[0], whole.inputs[2])
    bent = tree.nodes.new("ShaderNodeNormalMap")
    tree.links.new(whole.outputs[0], bent.inputs["Color"])
    tree.links.new(bent.outputs[0], shader.inputs["Normal"])
    gun.data.materials.clear()
    gun.data.materials.append(material)
    for polygon in gun.data.polygons:
        polygon.material_index = 0


def render(folder, views, only=(), size=(1600, 900), samples=64, moved=None):
    """Renders the gun in a studio from each of views, {name: (where the camera is, what it looks at, focal length)},
    or only those named. moved, if given, is where the gun or its parts are put first."""
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    use_card(scene)
    scene.cycles.samples = samples
    scene.cycles.use_denoising = True
    scene.render.resolution_x, scene.render.resolution_y = size
    scene.view_settings.view_transform = "Filmic"
    world = bpy.data.worlds.new("studio")
    world.use_nodes = True
    sky = world.node_tree.nodes.new("ShaderNodeTexSky")
    sky.sky_type = "NISHITA"
    sky.sun_elevation = math.radians(38)
    sky.sun_rotation = math.radians(130)
    sky.sun_intensity = 0.25
    world.node_tree.links.new(sky.outputs["Color"], world.node_tree.nodes["Background"].inputs["Color"])
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.35
    scene.world = world
    floor = bpy.data.meshes.new("floor")
    floor.from_pydata([(-4, -4, -0.2), (4, -4, -0.2), (4, 4, -0.2), (-4, 4, -0.2)], [], [(0, 1, 2, 3)])
    ground = bpy.data.objects.new("floor", floor)
    paper = bpy.data.materials.new("paper")
    paper.use_nodes = True
    paper.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.11, 0.11, 0.115, 1.0)
    floor.materials.append(paper)
    scene.collection.objects.link(ground)
    for place, energy, size_of in (((1.2, -0.6, 1.4), 60, 1.5), ((-1.5, 1.0, 0.8), 30, 2.0), ((0.2, 1.6, 0.4), 16, 1.0)):
        lamp = bpy.data.lights.new("lamp", "AREA")
        lamp.energy = energy
        lamp.size = size_of
        holder = bpy.data.objects.new("lamp", lamp)
        holder.location = place
        holder.rotation_euler = (Vector((0.0, 0.25, 0.0)) - Vector(place)).to_track_quat("-Z", "Y").to_euler()
        scene.collection.objects.link(holder)
    if moved is not None:
        moved()
    camera = bpy.data.objects.new("camera", bpy.data.cameras.new("camera"))
    scene.collection.objects.link(camera)
    scene.camera = camera
    folder = os.path.abspath(folder)
    os.makedirs(folder, exist_ok=True)
    for name, (place, target, lens) in views.items():
        if only and name not in only:
            continue
        camera.location = place
        camera.data.lens = lens
        camera.data.clip_start = 0.01
        camera.rotation_euler = (Vector(target) - Vector(place)).to_track_quat("-Z", "Y").to_euler()
        scene.render.filepath = os.path.join(folder, name + ".png")
        bpy.ops.render.render(write_still=True)
