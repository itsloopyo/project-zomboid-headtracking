"""The close-up surfaces every interior shares, each a seamless square that repeats: the felt of a roof lining,
loop-pile carpet, stippled hard plastic, grained vinyl, velour, painted steel, rubber, brushed metal.

The parts of a glasshouse are drawn in them outright, and the parts with a place in an interior's own texture
take their fine relief from them, so that what is at arm's length is as sharp as the screen is.

Each is baked from a procedural material laid on a torus in four dimensions, which repeats exactly.
"""

import math
import os

import bpy
import numpy

# Name, its tiles to a repeat, and the material: (base colour, how much the colour wanders, roughness, metal).
LAYERS = [
    ("felt", 0.08),
    ("carpet", 0.06),
    ("plastic", 0.05),
    ("vinyl", 0.06),
    ("velour", 0.04),
    ("paint", 0.20),
    ("rubber", 0.05),
    ("brushed", 0.10),
]
SIZE = 1024


def index(name):
    return [n for n, _ in LAYERS].index(name)


def repeat(name):
    return dict(LAYERS)[name]


class Torus:
    """Node sums whose patterns repeat once across the square, by walking a torus in four dimensions."""

    def __init__(self, material):
        self.material = material
        self.tree = material.node_tree
        self.nodes = self.tree.nodes
        uv = self.nodes.new("ShaderNodeTexCoord").outputs["UV"]
        sep = self.nodes.new("ShaderNodeSeparateXYZ")
        self.tree.links.new(uv, sep.inputs[0])
        self.u, self.v = sep.outputs[0], sep.outputs[1]

    def math(self, op, *values):
        n = self.nodes.new("ShaderNodeMath")
        n.operation = op
        for socket, value in zip(n.inputs, values):
            if isinstance(value, (int, float)):
                socket.default_value = value
            else:
                self.tree.links.new(value, socket)
        return n.outputs[0]

    def ring(self, t, radius):
        a = self.math("MULTIPLY", t, 2 * math.pi)
        return self.math("MULTIPLY", self.math("COSINE", a), radius), self.math("MULTIPLY", self.math("SINE", a), radius)

    def place(self, features, stretch=(1.0, 1.0)):
        """The four coordinates that walk the torus, with features patterns across the square (and stretch more one way)."""
        r = 1.0 / (2 * math.pi)
        x, y = self.ring(self.u, r * features * stretch[0])
        z, w = self.ring(self.v, r * features * stretch[1])
        comb = self.nodes.new("ShaderNodeCombineXYZ")
        for socket, value in zip(comb.inputs, (x, y, z)):
            self.tree.links.new(value, socket)
        return comb.outputs[0], w

    def noise(self, features, detail=3.0, rough=0.55, stretch=(1.0, 1.0)):
        vec, w = self.place(features, stretch)
        n = self.nodes.new("ShaderNodeTexNoise")
        n.noise_dimensions = "4D"
        self.tree.links.new(vec, n.inputs["Vector"])
        self.tree.links.new(w, n.inputs["W"])
        n.inputs["Scale"].default_value = 1.0
        n.inputs["Detail"].default_value = detail
        n.inputs["Roughness"].default_value = rough
        return n.outputs[0]

    def cells(self, features, feature="F1", randomness=1.0):
        vec, w = self.place(features)
        n = self.nodes.new("ShaderNodeTexVoronoi")
        n.voronoi_dimensions = "4D"
        n.feature = feature
        self.tree.links.new(vec, n.inputs["Vector"])
        self.tree.links.new(w, n.inputs["W"])
        n.inputs["Scale"].default_value = 1.0
        n.inputs["Randomness"].default_value = randomness
        return n.outputs["Distance"]

    def ribs(self, count, along_u=True):
        """count ridges across the square, 0 in each groove and 1 on each crest."""
        t = self.u if along_u else self.v
        s = self.math("SINE", self.math("MULTIPLY", t, 2 * math.pi * count))
        return self.math("MULTIPLY_ADD", s, 0.5, 0.5)

    def step(self, value, low, high):
        n = self.nodes.new("ShaderNodeMapRange")
        n.interpolation_type = "SMOOTHSTEP"
        self.tree.links.new(value, n.inputs["Value"])
        n.inputs["From Min"].default_value = low
        n.inputs["From Max"].default_value = high
        return n.outputs[0]


def recipe(name, t):
    """Height (in tiles), the colour's wander about one, and roughness, for a layer."""
    if name == "felt":
        fine = t.noise(260, 6, 0.75)
        return t.math("MULTIPLY", fine, 0.00006), t.math("MULTIPLY_ADD", fine, 0.16, 0.92), t.math("MULTIPLY_ADD", fine, 0.06, 0.9)
    if name == "carpet":
        loops = t.math("SUBTRACT", 1.0, t.step(t.cells(55), 0.1, 0.6))
        fibre = t.noise(420, 5, 0.8)
        h = t.math("ADD", t.math("MULTIPLY", loops, 0.0005), t.math("MULTIPLY", fibre, 0.0004))
        return h, t.math("MULTIPLY_ADD", t.math("ADD", loops, fibre), 0.22, 0.78), 0.97
    if name == "plastic":
        pebble = t.math("SUBTRACT", 1.0, t.step(t.cells(140), 0.05, 0.55))
        return t.math("MULTIPLY", pebble, 0.00005), t.math("MULTIPLY_ADD", pebble, 0.05, 0.98), t.math("MULTIPLY_ADD", pebble, -0.08, 0.6)
    if name == "vinyl":
        grain = t.math("SUBTRACT", 1.0, t.step(t.cells(70, randomness=0.9), 0.08, 0.7))
        wrinkle = t.noise(9, 3, 0.5)
        h = t.math("ADD", t.math("MULTIPLY", grain, 0.00012), t.math("MULTIPLY", wrinkle, 0.00006))
        return h, t.math("MULTIPLY_ADD", grain, 0.08, 0.95), t.math("MULTIPLY_ADD", grain, -0.12, 0.6)
    if name == "velour":
        rib = t.ribs(24, along_u=False)
        nap = t.noise(380, 4, 0.7)
        h = t.math("ADD", t.math("MULTIPLY", rib, 0.00008), t.math("MULTIPLY", nap, 0.00005))
        return h, t.math("MULTIPLY_ADD", t.math("ADD", rib, nap), 0.10, 0.88), 0.93
    if name == "paint":
        peel = t.noise(60, 2, 0.4)
        return t.math("MULTIPLY", peel, 0.00002), 1.0, t.math("MULTIPLY_ADD", peel, 0.05, 0.28)
    if name == "rubber":
        speck = t.noise(300, 4, 0.6)
        return t.math("MULTIPLY", speck, 0.00004), t.math("MULTIPLY_ADD", speck, 0.1, 0.95), 0.85
    if name == "brushed":
        lines = t.noise(14, 2, 0.5, stretch=(60.0, 1.0))
        return t.math("MULTIPLY", lines, 0.00001), t.math("MULTIPLY_ADD", lines, 0.08, 0.96), t.math("MULTIPLY_ADD", lines, 0.12, 0.18)
    raise SystemExit("no layer " + name)


def bake_layers(folder):
    """Bakes every layer to folder as detail.surface.N.gz, four bytes a texel: the slope, the roughness, and the
    colour's wander about a half."""
    os.makedirs(folder, exist_ok=True)
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.render.bake.margin = 0
    mesh = bpy.data.meshes.new("swatch")
    mesh.from_pydata([(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)], [], [(0, 1, 2, 3)])
    mesh.uv_layers.new(name="UVMap")
    mesh.uv_layers.active.data.foreach_set("uv", [0, 0, 1, 0, 1, 1, 0, 1])
    swatch = bpy.data.objects.new("swatch", mesh)
    scene.collection.objects.link(swatch)
    bpy.context.view_layer.update()
    for o in bpy.context.view_layer.objects:
        o.select_set(o == swatch)
    bpy.context.view_layer.objects.active = swatch
    target = bpy.data.images.new("detail", SIZE, SIZE, float_buffer=True, is_data=True)
    import gzip
    for k, (name, tiles) in enumerate(LAYERS):
        material = bpy.data.materials.new(name)
        material.use_nodes = True
        mesh.materials.clear()
        mesh.materials.append(material)
        t = Torus(material)
        height, wander, rough = recipe(name, t)
        shader = t.nodes["Principled BSDF"]
        bump = t.nodes.new("ShaderNodeBump")
        # The square is a tile across; its patterns are tiles across: the height is in tiles too.
        bump.inputs["Distance"].default_value = 1.0 / tiles
        t.tree.links.new(height, bump.inputs["Height"])
        t.tree.links.new(bump.outputs[0], shader.inputs["Normal"])
        glow = t.nodes.new("ShaderNodeEmission")
        image = t.nodes.new("ShaderNodeTexImage")
        image.image = target
        t.nodes.active = image
        out = t.nodes["Material Output"].inputs["Surface"]

        def emit(value):
            for link in list(glow.inputs["Color"].links):
                t.tree.links.remove(link)
            if isinstance(value, (int, float)):
                glow.inputs["Color"].default_value = (value, value, value, 1)
            else:
                t.tree.links.new(value, glow.inputs["Color"])
            t.tree.links.new(glow.outputs[0], out)
            scene.cycles.samples = 1
            bpy.ops.object.bake(type="EMIT")
            px = numpy.empty(SIZE * SIZE * 4, dtype=numpy.float32)
            target.pixels.foreach_get(px)
            return px.reshape(SIZE, SIZE, 4)[..., 0].copy()

        w = emit(wander)
        r = emit(rough)
        t.tree.links.new(shader.outputs[0], out)
        scene.cycles.samples = 4
        bpy.ops.object.bake(type="NORMAL", normal_space="TANGENT")
        px = numpy.empty(SIZE * SIZE * 4, dtype=numpy.float32)
        target.pixels.foreach_get(px)
        nrm = px.reshape(SIZE, SIZE, 4)
        # The wander is kept about a half, so that it can make a surface lighter as well as darker.
        surface = numpy.stack([nrm[..., 0], nrm[..., 1], r, w * 0.5], axis=2)
        for kind, array in (("surface", surface),):
            data = (numpy.clip(array, 0, 1) * 255 + 0.5).astype(numpy.uint8)
            with gzip.open(os.path.join(folder, "detail.%s.%d.gz" % (kind, k)), "wb", compresslevel=9) as f:
                f.write(data.tobytes())
        print("baked detail layer", k, name, flush=True)
