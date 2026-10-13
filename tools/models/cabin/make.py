"""Unwraps an interior's parts onto one texture, bakes what they are made of into it, bakes the light the sky gives
them through the glass and the shade of what stands near into their vertices, and packs the mesh and the pictures
for the jar.

Three kinds of part: those with a place in the texture, which carry the fine work (the dash, the seats, the
instruments); those drawn in one of the repeating close-up surfaces alone (the lining, the carpet, the trim of the
glasshouse), which differ from body to body; and the glass and mirrors, which the mod draws itself.
"""

import gzip
import math
import os
import struct

import bmesh
import bpy
import numpy
from bpy_extras import bmesh_utils

from . import detail, materials
from .kit import GLASS, GROUPS, MIRROR

HERE = os.path.dirname(os.path.abspath(__file__))
OUTPUT = os.path.join(HERE, "..", "..", "..", "src", "main", "resources", "cabins")
PICTURES = os.path.join(HERE, "..", "..", "..", "src", "textures", "cabins")
TEXTURE = 2048
# How far faces may turn from one another in one island. Wider, the projection draws faces out of shape by up to
# one over the cosine of it, and a 60 degree limit had 3% of faces too far out of shape to keep.
ANGLE = 35.0
MAGIC = b"CUCM"
VERSION = 2
NO_DETAIL = 127


def select(objs, active):
    for o in bpy.context.view_layer.objects:
        o.select_set(o in objs)
    bpy.context.view_layer.objects.active = active


def triangulated(obj):
    m = obj.modifiers.new("triangles", "TRIANGULATE")
    m.keep_custom_normals = True
    depsgraph = bpy.context.evaluated_depsgraph_get()
    mesh = bpy.data.meshes.new_from_object(obj.evaluated_get(depsgraph), preserve_all_data_layers=True, depsgraph=depsgraph)
    obj.modifiers.clear()
    old = obj.data
    obj.data = mesh
    bpy.data.meshes.remove(old)


def kinds(parts):
    """The parts with a place in the texture, those in a repeating surface alone, and the glass and mirrors."""
    drawn = [o for o in parts if o["group"] in (GLASS, MIRROR)]
    tiled = [o for o in parts if o not in drawn and o["material"] in materials.TILED]
    return [o for o in parts if o not in drawn and o not in tiled], tiled, drawn


def cut_long_islands(obj, longest):
    """Cuts each island of the part's texture places that is longer than longest into pieces along its length, each
    piece moved apart from the next so the packer takes it as an island of its own: a strip (a rail, a moulding, the
    dashboard's sweep) left whole has the packer fit everything round one island as long as the texture is wide."""
    bm = bmesh.new()
    bm.from_mesh(obj.data)
    layer = bm.loops.layers.uv.active
    for island in bmesh_utils.bmesh_linked_uv_islands(bm, layer):
        uvs = numpy.array([loop[layer].uv[:] for face in island for loop in face.loops])
        low, high = uvs.min(0), uvs.max(0)
        axis = int(numpy.argmax(high - low))
        if high[axis] - low[axis] <= longest:
            continue
        reach = max(numpy.ptp([loop[layer].uv[axis] for loop in face.loops]) for face in island)
        for face in island:
            middle = sum(loop[layer].uv[axis] for loop in face.loops) / len(face.loops)
            piece = int((middle - low[axis]) / longest)
            for loop in face.loops:
                loop[layer].uv[axis] += piece * 2.0 * reach + piece * 0.01
    bm.to_mesh(obj.data)
    bm.free()


def unwrap(solid):
    """Gives every part that is drawn with the texture a place in it, the parts near the eye more texels than the
    rest, and joins them into one object."""
    relaid = [0, 0]
    for obj in solid:
        triangulated(obj)
        mesh = obj.data
        a = mesh.attributes.new("group", "INT", "FACE")
        a.data.foreach_set("value", [obj["group"]] * len(mesh.polygons))
        mesh.uv_layers.new(name="UVMap")
        select([obj], obj)
        bpy.ops.object.mode_set(mode="EDIT")
        bpy.ops.mesh.select_all(action="SELECT")
        bpy.ops.uv.smart_project(angle_limit=math.radians(ANGLE), island_margin=0.0, area_weight=0.0, scale_to_bounds=False)
        bpy.ops.object.mode_set(mode="OBJECT")
        uv = numpy.empty(len(mesh.loops) * 2)
        mesh.uv_layers.active.data.foreach_get("uv", uv)
        tri = uv.reshape(-1, 3, 2)
        flat = 0.5 * numpy.abs(numpy.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0]))
        area = numpy.empty(len(mesh.polygons))
        mesh.polygons.foreach_get("area", area)
        # The middling face's stretch, where a sum would be thrown by faces the projection laid edge on.
        laid = flat > 1e-12
        scale = float(numpy.median(numpy.sqrt(area[laid] / flat[laid]))) * obj["texels"]
        # Faces the projection drew out of shape are laid again flat on their own plane, each an island of its own
        # off to the side, at the part's middling stretch. How far out of shape: the ratio of the most to the least a
        # face's texture place stretches it, one way to another, against its stretch beside the part's.
        middling = scale / obj["texels"]
        corners = numpy.empty(len(mesh.vertices) * 3)
        mesh.vertices.foreach_get("co", corners)
        corners = corners.reshape(-1, 3)
        index = numpy.empty(len(mesh.loops), dtype=numpy.int64)
        mesh.loops.foreach_get("vertex_index", index)
        places = corners[index].reshape(-1, 3, 3)
        a1, a2 = places[:, 1] - places[:, 0], places[:, 2] - places[:, 0]
        e1 = a1 / numpy.maximum(numpy.linalg.norm(a1, axis=1), 1e-12)[:, None]
        normal = numpy.cross(a1, a2)
        normal /= numpy.maximum(numpy.linalg.norm(normal, axis=1), 1e-12)[:, None]
        e2 = numpy.cross(normal, e1)
        P = numpy.stack([numpy.stack([numpy.einsum("ij,ij->i", a1, e1), numpy.zeros(len(a1))], 1),
                         numpy.stack([numpy.einsum("ij,ij->i", a2, e1), numpy.einsum("ij,ij->i", a2, e2)], 1)], 2)
        Q = numpy.stack([tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0]], 2)
        sound = numpy.abs(numpy.linalg.det(P)) > 1e-14
        stretches = numpy.ones((len(tri), 2))
        stretches[sound] = numpy.linalg.svd(Q[sound] @ numpy.linalg.inv(P[sound]), compute_uv=False)
        out_of_shape = sound & ((stretches[:, 0] > 1.4 * stretches[:, 1]) | (stretches[:, 0] * middling > 1.5) | (stretches[:, 1] * middling < 1 / 1.5))
        tri = tri.copy()
        cursor = 2.0
        relaid[0] += int(out_of_shape.sum())
        relaid[1] += len(tri)
        for k in numpy.nonzero(out_of_shape)[0]:
            p0 = places[k][0]
            laid_flat = numpy.array([((p - p0) @ e1[k] / middling, (p - p0) @ e2[k] / middling) for p in places[k]])
            laid_flat -= laid_flat.min(0)
            laid_flat[:, 0] += cursor
            cursor += laid_flat[:, 0].max() - cursor + 0.002
            tri[k] = laid_flat
        uv = tri.reshape(-1)
        mesh.uv_layers.active.data.foreach_set("uv", uv * scale)
    print("%d of %d faces laid again on their own planes" % tuple(relaid), flush=True)
    # The pieces' length is set by how much there is to lay out: a tenth of the side of a square holding it all.
    areas, reaches = {}, {}
    for obj in solid:
        uv = numpy.empty(len(obj.data.loops) * 2)
        obj.data.uv_layers.active.data.foreach_get("uv", uv)
        tri = uv.reshape(-1, 3, 2)
        areas[obj.name] = 0.5 * numpy.abs(numpy.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])).sum()
        reaches[obj.name] = numpy.linalg.norm(tri[:, [1, 2, 0]] - tri, axis=2).max()
    longest = 0.1 * math.sqrt(sum(areas.values()))
    for name in sorted(reaches, key=lambda n: -reaches[n])[:8]:
        print("a face of %s is %.2f of the pieces' longest" % (name, reaches[name] / longest), flush=True)
    for obj in solid:
        cut_long_islands(obj, longest)
    select(solid, solid[0])
    bpy.ops.object.join()
    cabin = bpy.context.view_layer.objects.active
    cabin.name = "cabin"
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.select_all(action="SELECT")
    bpy.ops.uv.pack_islands(rotate=True, rotate_method="AXIS_ALIGNED", margin_method="FRACTION", margin=0.002, shape_method="CONCAVE")
    bpy.ops.object.mode_set(mode="OBJECT")
    mesh = cabin.data
    uv = numpy.empty(len(mesh.loops) * 2)
    mesh.uv_layers.active.data.foreach_get("uv", uv)
    tri = uv.reshape(-1, 3, 2)
    flat = 0.5 * numpy.abs(numpy.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])).sum()
    print("the texture is %.0f%% used, at %.0f texels to the tile where a part is weighted one" % (
        flat * 100, TEXTURE * math.sqrt(flat / sum(p.area for p in mesh.polygons))), flush=True)
    return cabin


def lay_tiled(tiled):
    """Lays each part in a repeating surface out on it: every face by the axis it faces most along, in repeats."""
    for obj in tiled:
        triangulated(obj)
        mesh = obj.data
        repeat = detail.repeat(materials.TILED[obj["material"]][0])
        layer = mesh.uv_layers.new(name="UVMap")
        index = numpy.empty(len(mesh.loops), dtype=numpy.int32)
        mesh.loops.foreach_get("vertex_index", index)
        place = numpy.empty(len(mesh.vertices) * 3)
        mesh.vertices.foreach_get("co", place)
        place = place.reshape(-1, 3)[index]
        facing = numpy.empty(len(mesh.polygons) * 3)
        mesh.polygons.foreach_get("normal", facing)
        facing = numpy.repeat(numpy.abs(facing.reshape(-1, 3)), 3, axis=0)
        axis = facing.argmax(axis=1)
        u = numpy.where(axis == 0, place[:, 1], place[:, 0])
        v = numpy.where(axis == 2, place[:, 1], place[:, 2])
        layer.data.foreach_set("uv", (numpy.stack([u, v], 1) / repeat).reshape(-1))


def vertex_layer(obj, name):
    mesh = obj.data
    if name not in mesh.color_attributes:
        mesh.color_attributes.new(name, "BYTE_COLOR", "POINT")
    mesh.color_attributes.active_color = mesh.color_attributes[name]


def world_for_baking():
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.render.bake.margin = 12
    scene.render.bake.use_clear = True
    world = bpy.data.worlds.new("bake")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (1, 1, 1, 1)
    scene.world = world
    return scene, world


def bake_texture(cabin, drawn, samples_ao=96):
    """What the parts with a place in the texture are made of, and the shade of what stands near them, into it."""
    scene, world = world_for_baking()
    for o in drawn:
        o.hide_render = True
    target = bpy.data.images.new("bake", TEXTURE, TEXTURE, float_buffer=True, is_data=True)
    for graph in materials.GRAPHS.values():
        graph.baked.image = target
        graph.nodes.active = graph.baked
    select([cabin], cabin)
    out = {}

    def taken():
        pixels = numpy.empty(TEXTURE * TEXTURE * 4, dtype=numpy.float32)
        target.pixels.foreach_get(pixels)
        return pixels.reshape(TEXTURE, TEXTURE, 4)[..., :3].copy()

    scene.cycles.samples = 1
    for which in ("colour", "rough", "glow"):
        for graph in materials.GRAPHS.values():
            graph.show(which)
        bpy.ops.object.bake(type="EMIT")
        out[which] = taken()
        print("baked", which, flush=True)
    for graph in materials.GRAPHS.values():
        graph.show(None)
    scene.cycles.samples = 8
    bpy.ops.object.bake(type="NORMAL", normal_space="TANGENT")
    out["normal"] = taken()
    print("baked normal", flush=True)
    # The near shade from the coarse surface smoothed again, laid on the same texture places: from the flat facets a
    # ray leaving along the smoothed normal runs into the facet beside it and darkens every other triangle.
    smooth = cabin.modifiers.new("smoothed for shade", "SUBSURF")
    smooth.levels = smooth.render_levels = 2
    smooth.uv_smooth = "NONE"
    scene.cycles.samples = samples_ao
    world.light_settings.distance = 0.06
    bpy.ops.object.bake(type="AO")
    cabin.modifiers.remove(smooth)
    out["near"] = taken()
    print("baked near shade", flush=True)
    for o in drawn:
        o.hide_render = False
    return out


def bake_vertices(cabin, tiled, drawn, samples_ao=96):
    scene, world = world_for_baking()
    for o in drawn:
        o.hide_render = True
    # Into the vertices of every part: what of the sky each place sees with the glass open, and the shade of what
    # stands within a hand's breadth of it.
    vertexed = [cabin] + tiled
    select(vertexed, cabin)
    scene.render.bake.target = "VERTEX_COLORS"
    # The sky's light is baked as the light it gives, bounced round the cabin as well as straight in, from a sky of
    # one all round: a place open to the whole sky has one, one under the dash next to none.
    for o in vertexed:
        vertex_layer(o, "sky")
    scene.render.bake.use_pass_direct = True
    scene.render.bake.use_pass_indirect = True
    scene.render.bake.use_pass_color = False
    scene.cycles.samples = samples_ao * 4
    bpy.ops.object.bake(type="DIFFUSE")
    print("baked sky into the vertices", flush=True)
    for o in vertexed:
        vertex_layer(o, "near")
    world.light_settings.distance = 0.08
    scene.cycles.samples = samples_ao * 3
    bpy.ops.object.bake(type="AO")
    print("baked near into the vertices", flush=True)
    scene.render.bake.target = "IMAGE_TEXTURES"
    for o in drawn:
        o.hide_render = False


def encoded(linear):
    linear = numpy.clip(linear, 0.0, 1.0)
    return numpy.where(linear <= 0.0031308, linear * 12.92, 1.055 * numpy.power(linear, 1 / 2.4) - 0.055)


def bytes_of(array):
    return (numpy.clip(array, 0.0, 1.0) * 255.0 + 0.5).astype(numpy.uint8)


def pictures_of(baked):
    """The two pictures: colour with the near shade beside it; and the surface's slope, its roughness and what of
    it glows with the instruments. What is metal and what takes the trim's tint are each the same over a part, and
    go with its vertices."""
    colour = numpy.concatenate([encoded(baked["colour"]), baked["near"][..., :1]], axis=2)
    surface = numpy.concatenate([baked["normal"][..., :2], baked["rough"][..., :1], baked["glow"][..., :1]], axis=2)
    return bytes_of(colour), bytes_of(surface)


def per_face(obj, values_of_material):
    """For each corner, a byte its part's material gives."""
    mesh = obj.data
    index = numpy.empty(len(mesh.polygons), dtype=numpy.int32)
    mesh.polygons.foreach_get("material_index", index)
    table = numpy.array([values_of_material(slot) for slot in mesh.materials], dtype=numpy.uint8)
    return numpy.repeat(table[index], 3)


def finish_of(slot):
    """How much a material takes the trim's tint, in the low four bits, and how metal it is, in the high four."""
    graph = next(g for g in materials.GRAPHS.values() if g.material == slot)
    tint, metal = graph.sockets["tint"], graph.sockets["metal"]
    if not isinstance(tint, (int, float)) or not isinstance(metal, (int, float)):
        raise SystemExit("the material %s has a tint or metal that changes over it: the vertices cannot carry it" % slot.name)
    return int(round(tint * 15)) | int(round(metal * 15)) << 4


def kind_of(slot, tiled_names):
    """For a part with a place in the texture, 128 and the repeating surface it takes its fine relief from, or 127
    for none; for one drawn in a repeating surface alone, which of the interior's such materials it is."""
    name = slot.name.split(".")[0]
    if name in tiled_names:
        return tiled_names.index(name)
    layer = materials.DETAIL.get(name)
    return 128 | (detail.index(layer) if layer else NO_DETAIL)


def corners_of(obj):
    """Every corner of an object's triangles: place, normal, tangent, handedness, texture place, sky, near shade."""
    mesh = obj.data
    has_uv = mesh.uv_layers.active is not None
    if has_uv:
        mesh.calc_tangents(uvmap=mesh.uv_layers.active.name)
    n = len(mesh.loops)
    index = numpy.empty(n, dtype=numpy.int32)
    mesh.loops.foreach_get("vertex_index", index)
    place = numpy.empty(len(mesh.vertices) * 3, dtype=numpy.float32)
    mesh.vertices.foreach_get("co", place)
    place = place.reshape(-1, 3)[index]
    normal = numpy.empty(n * 3, dtype=numpy.float32)
    mesh.loops.foreach_get("normal", normal)
    tangent = numpy.zeros(n * 3, dtype=numpy.float32)
    way = numpy.ones(n, dtype=numpy.float32)
    uv = numpy.zeros(n * 2, dtype=numpy.float32)
    if has_uv:
        mesh.loops.foreach_get("tangent", tangent)
        mesh.loops.foreach_get("bitangent_sign", way)
        mesh.uv_layers.active.data.foreach_get("uv", uv)
    shades = {}
    for name in ("sky", "near"):
        shades[name] = numpy.ones(n, dtype=numpy.float32)
        if name in mesh.color_attributes:
            # The layer is a vertex's (vertex_layer), and foreach_get fills a longer array without a word, leaving
            # the rest as it was: each corner takes its vertex's value.
            layer = mesh.color_attributes[name]
            if layer.domain != "POINT":
                raise ValueError("%s's %s layer is on %s, not on its vertices" % (obj.name, name, layer.domain))
            c = numpy.empty(len(mesh.vertices) * 4, dtype=numpy.float32)
            layer.data.foreach_get("color", c)
            shades[name] = c.reshape(-1, 4)[index, 0]
    return place, normal.reshape(-1, 3), tangent.reshape(-1, 3), way, uv.reshape(-1, 2), shades["sky"], shades["near"]


def mirror_uv(obj, which):
    """A mirror's glass laid out 0 to 1 across its own face, its whole part telling which mirror it is."""
    mesh = obj.data
    pts = numpy.array([v.co[:] for v in mesh.vertices])
    centre = pts.mean(0)
    _, _, vt = numpy.linalg.svd(pts - centre)
    # Across is whichever way along the glass is nearer level: a van's door mirror is taller than it is wide.
    across, up = (vt[0], vt[1]) if abs(vt[0][2]) <= abs(vt[1][2]) else (vt[1], vt[0])
    if up[2] < 0:
        up = -up
    # Across runs to the driver's right as seen from the seat, which is the mirror's left for right.
    if across[1] > 0:
        across = -across
    u = (pts - centre) @ across
    w = (pts - centre) @ up
    uv = numpy.stack([(u - u.min()) / (u.max() - u.min()) * 0.999 + which, (w - w.min()) / (w.max() - w.min())], 1)
    layer = mesh.uv_layers.new(name="UVMap")
    index = numpy.empty(len(mesh.loops), dtype=numpy.int32)
    mesh.loops.foreach_get("vertex_index", index)
    layer.data.foreach_set("uv", uv[index].reshape(-1))
    return centre, numpy.cross(across, up), across, (u.max() - u.min()) / 2, (w.max() - w.min()) / 2


ROW = numpy.dtype([("place", "<f4", 3), ("normal", "<i2", 3), ("tangent", "<i2", 3), ("way", "<i2"), ("uv", "<f4", 2), ("sky", "u1"), ("near", "u1"),
                   ("finish", "u1"), ("kind", "u1"), ("pad", "<u2")])


def pack(name, cabin, tiled, drawn, info, texture_name, colour, surface):
    """Writes the mesh, and the pictures where they were baked here. texture_name is the interior whose pictures
    the mesh is drawn with."""
    tiled_names = []
    for o in tiled:
        if o["material"] not in tiled_names:
            tiled_names.append(o["material"])
    rows, groups = [], []
    for obj in [cabin] + tiled + drawn:
        if obj not in [cabin] + tiled:
            triangulated(obj)
        place, normal, tangent, way, uv, sky, near = corners_of(obj)
        n = len(place)
        r = numpy.zeros(n, dtype=ROW)
        r["place"] = place
        r["normal"] = numpy.round(numpy.clip(normal, -1, 1) * 32767)
        r["tangent"] = numpy.round(numpy.clip(tangent, -1, 1) * 32767)
        r["way"] = numpy.where(way < 0, -1, 1)
        r["sky"] = numpy.round(numpy.clip(sky, 0, 1) * 255)
        r["near"] = numpy.round(numpy.clip(near, 0, 1) * 255)
        group = obj.get("group")
        if obj is cabin:
            g = numpy.empty(len(obj.data.polygons), dtype=numpy.int32)
            obj.data.attributes["group"].data.foreach_get("value", g)
            g = numpy.repeat(g, 3)
        else:
            g = numpy.full(n, group, dtype=numpy.int32)
        if group == GLASS and obj is not cabin:
            # A pane's texture place is the place on it the glass's dust and rain are laid out by, in tiles.
            uv, swept = glass_places(obj.name, place, info["glass"])
            r["sky"] = 255 if swept else 0
            r["near"] = 255
        elif group == MIRROR and obj is not cabin:
            r["near"] = 255
        else:
            r["finish"] = per_face(obj, finish_of)
            r["kind"] = per_face(obj, lambda slot: kind_of(slot, tiled_names))
        r["uv"] = uv
        rows.append(r)
        groups.append(g)
    rows = numpy.concatenate(rows)
    groups = numpy.concatenate(groups)
    vertices, which = numpy.unique(rows.view([("all", "V%d" % rows.dtype.itemsize)]), return_inverse=True)
    which = which.reshape(-1)
    tri_group = groups[0::3]
    order = numpy.argsort(tri_group, kind="stable")
    indices = which.reshape(-1, 3)[order].reshape(-1).astype("<u4")
    counts = [int((tri_group == k).sum()) * 3 for k in range(GROUPS)]
    firsts = [sum(counts[:k]) for k in range(GROUPS)]
    head = MAGIC + struct.pack("<4I", VERSION, len(vertices), len(indices), GROUPS)
    for f, c in zip(firsts, counts):
        head += struct.pack("<2I", f, c)
    words = texture_name.encode("ascii")
    head += struct.pack("<B", len(words)) + words
    floats = []
    for key in ("measures", "wheel", "needles", "lamps"):
        floats += list(info[key])
    floats.append(info["glass"]["half"])
    # Each repeating surface's tiles to a repeat, then each material drawn in one alone: its surface, its colour,
    # its roughness; then the mirrors, each its middle, the way it faces, the way across it and its half sizes.
    floats += [r for _, r in detail.LAYERS]
    floats.append(len(tiled_names))
    for t in tiled_names:
        layer, colour_, rough = materials.TILED[t]
        floats += [detail.index(layer), *colour_, rough]
    floats.append(len(info["mirrors"]) // 11)
    floats += list(info["mirrors"])
    head += struct.pack("<I", len(floats)) + struct.pack("<%df" % len(floats), *floats)
    os.makedirs(OUTPUT, exist_ok=True)
    path = os.path.join(OUTPUT, name + ".mesh.gz")
    with gzip.open(path, "wb", compresslevel=9) as f:
        f.write(head + vertices.tobytes() + indices.tobytes())
    print("wrote %s: %d vertices, %d triangles (%s)" % (os.path.normpath(path), len(vertices), len(indices) // 3,
                                                      ", ".join("%d" % (c // 3) for c in counts)), flush=True)
    if colour is not None:
        os.makedirs(PICTURES, exist_ok=True)
        for kind, picture in (("colour", colour), ("surface", surface)):
            p = os.path.join(PICTURES, "%s.%s.gz" % (texture_name, kind))
            with gzip.open(p, "wb", compresslevel=9) as f:
                f.write(picture.tobytes())
            print("wrote %s" % os.path.normpath(p), flush=True)


def glass_places(name, place, glass):
    """Where each corner of a pane is on it, as the glass's shader lays out its dust, rain and wipers: across the
    windscreen from its middle, the driver's side below nought, and up it from 0.3 under its foot; along the side
    windows from 7 tiles behind the eye, and up; across the back window and up. And whether the wipers sweep it."""
    if name.startswith("windscreen"):
        t = (place[:, 2] - glass["foot_z"]) / (glass["top_z"] - glass["foot_z"])
        return numpy.stack([-(place[:, 1] - glass["mid"]), t * glass["slant"] - 0.3], 1), True
    if name.startswith("back window"):
        return numpy.stack([place[:, 1] - glass["mid"], place[:, 2]], 1), False
    return numpy.stack([place[:, 0] + 7.0, place[:, 2]], 1), False


def dress(cabin, colour, surface, folder):
    """Puts the baked pictures on the cabin in place of what they were baked from, as the mod will draw it."""
    material = bpy.data.materials.new("baked")
    material.use_nodes = True
    tree = material.node_tree
    shader = tree.nodes["Principled BSDF"]

    def picture(name, array, is_colour):
        image = bpy.data.images.new(name, TEXTURE, TEXTURE, alpha=True, is_data=not is_colour)
        image.pixels.foreach_set((array.astype(numpy.float32) / 255.0).reshape(-1))
        image.filepath_raw = os.path.join(folder, name + ".png")
        image.file_format = "PNG"
        image.save()
        node = tree.nodes.new("ShaderNodeTexImage")
        node.image = image
        return node

    tint, shape = picture("cabin-colour", colour, True), picture("cabin-surface", surface, False)
    # The colour darkened by the near shade, in the picture's fourth number and in the vertices, as the mod does.
    near = tree.nodes.new("ShaderNodeVertexColor")
    near.layer_name = "near"
    first, second = tree.nodes.new("ShaderNodeMix"), tree.nodes.new("ShaderNodeMix")
    for m in (first, second):
        m.data_type = "RGBA"
        m.blend_type = "MULTIPLY"
        m.inputs["Factor"].default_value = 1.0
    tree.links.new(tint.outputs["Color"], first.inputs["A"])
    tree.links.new(tint.outputs["Alpha"], first.inputs["B"])
    tree.links.new(first.outputs["Result"], second.inputs["A"])
    tree.links.new(near.outputs["Color"], second.inputs["B"])
    tree.links.new(second.outputs["Result"], shader.inputs["Base Color"])
    parts = tree.nodes.new("ShaderNodeSeparateColor")
    tree.links.new(shape.outputs["Color"], parts.inputs[0])
    tree.links.new(parts.outputs[2], shader.inputs["Roughness"])
    # The way the surface faces, from the picture's first two numbers in its tangent frame, the third made up as the
    # mod makes it.
    z = tree.nodes.new("ShaderNodeMath")
    z.operation = "SQRT"
    xy = tree.nodes.new("ShaderNodeVectorMath")
    xy.operation = "MULTIPLY_ADD"
    xy.inputs[1].default_value = (2.0, 2.0, 0.0)
    xy.inputs[2].default_value = (-1.0, -1.0, 0.0)
    tree.links.new(shape.outputs["Color"], xy.inputs[0])
    length = tree.nodes.new("ShaderNodeVectorMath")
    length.operation = "DOT_PRODUCT"
    tree.links.new(xy.outputs[0], length.inputs[0])
    tree.links.new(xy.outputs[0], length.inputs[1])
    rest = tree.nodes.new("ShaderNodeMath")
    rest.operation = "SUBTRACT"
    rest.inputs[0].default_value = 1.0
    rest.use_clamp = True
    tree.links.new(length.outputs["Value"], rest.inputs[1])
    tree.links.new(rest.outputs[0], z.inputs[0])
    split = tree.nodes.new("ShaderNodeSeparateXYZ")
    tree.links.new(xy.outputs[0], split.inputs[0])
    join = tree.nodes.new("ShaderNodeCombineXYZ")
    tree.links.new(split.outputs[0], join.inputs[0])
    tree.links.new(split.outputs[1], join.inputs[1])
    tree.links.new(z.outputs[0], join.inputs[2])
    back = tree.nodes.new("ShaderNodeVectorMath")
    back.operation = "MULTIPLY_ADD"
    back.inputs[1].default_value = (0.5, 0.5, 0.5)
    back.inputs[2].default_value = (0.5, 0.5, 0.5)
    tree.links.new(join.outputs[0], back.inputs[0])
    facing = tree.nodes.new("ShaderNodeNormalMap")
    tree.links.new(back.outputs[0], facing.inputs["Color"])
    tree.links.new(facing.outputs["Normal"], shader.inputs["Normal"])
    cabin.data.materials.clear()
    cabin.data.materials.append(material)
    for p in cabin.data.polygons:
        p.material_index = 0
