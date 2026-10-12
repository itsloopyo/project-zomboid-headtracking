"""Builds the mod's own vehicle interiors and packs them for the jar.

Run headless with Blender 4.4, from the repository root:

    blender --background --factory-startup --python tools/models/cabins.py -- [--family Base.CarNormal] [--renders DIR] [--look]

--look renders the shapes from the driver's seat with plain materials, in a minute, for checking them; --body OBJ adds a
measured game body, faintly, to its outside view.
"""

import math
import os
import sys

import bpy
from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from cabin import bodies, compact, detail, kit, luxury, make, materials, modern, offroad, sedan, sports, stepvan, truck, van  # noqa: E402


PLAIN = {
    "glass": ((0.6, 0.7, 0.75), 0.05, 0.0),
    "lining": ((0.62, 0.60, 0.56), 0.9, 0.0),
    "trim": ((0.20, 0.22, 0.27), 0.6, 0.0),
    "vinyl": ((0.16, 0.18, 0.24), 0.45, 0.0),
    "padded": ((0.16, 0.18, 0.24), 0.5, 0.0),
    "plastic": ((0.12, 0.13, 0.16), 0.55, 0.0),
    "cloth": ((0.17, 0.20, 0.30), 0.95, 0.0),
    "carpet": ((0.10, 0.11, 0.15), 1.0, 0.0),
    "chrome": ((0.9, 0.9, 0.9), 0.12, 1.0),
    "rubber": ((0.03, 0.03, 0.03), 0.8, 0.0),
    "black": ((0.025, 0.025, 0.028), 0.5, 0.0),
    "gauge": ((0.02, 0.02, 0.022), 0.3, 0.0),
    "lens": ((0.02, 0.02, 0.02), 0.03, 0.0),
    "mirror": ((0.95, 0.95, 0.95), 0.02, 1.0),
    "paint": ((0.45, 0.06, 0.05), 0.3, 0.0),
    "wood": ((0.25, 0.12, 0.05), 0.35, 0.0),
    "grille": ((0.04, 0.04, 0.045), 0.7, 0.0),
    "needle": ((0.9, 0.25, 0.05), 0.4, 0.0),
    "wheel": ((0.14, 0.16, 0.22), 0.45, 0.0),
    "webbing": ((0.12, 0.13, 0.16), 0.8, 0.0),
    "lamp": ((0.8, 0.8, 0.75), 0.3, 0.0),
    "mat": ((0.04, 0.04, 0.04), 0.8, 0.0),
    "leather": ((0.30, 0.18, 0.10), 0.45, 0.0),
    "race cloth": ((0.035, 0.035, 0.04), 0.9, 0.0),
    "cage": ((0.7, 0.7, 0.72), 0.3, 0.0),
    "floor rubber": ((0.045, 0.045, 0.047), 0.85, 0.0),
    "painted": ((0.18, 0.22, 0.32), 0.3, 0.0),
}


def clear():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    kit.PARTS.clear()
    materials.GRAPHS.clear()


def plain_materials():
    made = {}
    for obj in kit.PARTS:
        name = obj["material"]
        if name not in made and name.startswith("dial "):
            # A printed face shows its picture, so a cluster is judged as it reads.
            made[name] = materials.make(name, None).material
        if name not in made:
            colour, rough, metal = PLAIN.get(name, ((1, 0, 1), 0.5, 0.0))
            m = bpy.data.materials.new(name)
            m.use_nodes = True
            p = m.node_tree.nodes["Principled BSDF"]
            p.inputs["Base Color"].default_value = (*colour, 1.0)
            p.inputs["Roughness"].default_value = rough
            p.inputs["Metallic"].default_value = metal
            made[name] = m
        obj.data.materials.clear()
        obj.data.materials.append(made[name])
        if name == "glass":
            obj.hide_render = True


VIEWS = {
    # name: where from, the way looked (yaw degrees to the left, pitch degrees up), vertical field of view
    "ahead": ((0.0, 0.0, 0.0), (0.0, -8.0), 65.0),
    "down": ((0.0, 0.0, 0.0), (0.0, -35.0), 65.0),
    "left": ((0.0, 0.0, 0.0), (70.0, -12.0), 65.0),
    "right": ((0.0, 0.0, 0.0), (-55.0, -14.0), 65.0),
    "up": ((0.0, 0.0, 0.0), (-20.0, 40.0), 65.0),
    "back": ((0.0, 0.0, 0.0), (-160.0, -10.0), 65.0),
    "passenger": ((0.0, -0.64, 0.0), (35.0, -16.0), 65.0),
    "rear": ((-0.75, -0.30, 0.0), (8.0, -12.0), 65.0),
    "outside": ((0.25, 1.4, 0.05), (-90.0, -14.0), 50.0),
    # From between the front seats, looking down whatever lies behind them: a van's box, a wagon's load space.
    "box": ((-0.30, -0.32, -0.05), (180.0, -12.0), 75.0),
}


def render(folder, look_only, samples=32, size=(1280, 720), only=None, body=None):
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = samples
    scene.cycles.use_denoising = True
    scene.render.resolution_x, scene.render.resolution_y = size
    scene.view_settings.view_transform = "AgX"
    world = bpy.data.worlds.new("sky")
    world.use_nodes = True
    sky = world.node_tree.nodes.new("ShaderNodeTexSky")
    sky.sky_type = "NISHITA"
    sky.sun_elevation = math.radians(42)
    sky.sun_rotation = math.radians(200)
    sky.sun_intensity = 0.4
    world.node_tree.links.new(sky.outputs["Color"], world.node_tree.nodes["Background"].inputs["Color"])
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.12
    scene.view_settings.exposure = -0.5
    scene.world = world
    ground = bpy.data.meshes.new("ground")
    ground.from_pydata([(-40, -40, -1.48), (40, -40, -1.48), (40, 40, -1.48), (-40, 40, -1.48)], [], [(0, 1, 2, 3)])
    g = bpy.data.objects.new("ground", ground)
    gm = bpy.data.materials.new("road")
    gm.use_nodes = True
    gm.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.18, 0.18, 0.18, 1.0)
    ground.materials.append(gm)
    scene.collection.objects.link(g)
    camera = bpy.data.objects.new("eye", bpy.data.cameras.new("eye"))
    scene.collection.objects.link(camera)
    scene.camera = camera
    os.makedirs(folder, exist_ok=True)
    shell_obj = None
    if body:
        # The game's own body, measured into the same frame, drawn faintly in the outside view only: there it shows
        # whether what stands outside the glass (the door mirrors) meets the bodywork.
        verts, faces = [], []
        for line in open(body):
            if line.startswith("v "):
                verts.append(tuple(map(float, line.split()[1:4])))
            elif line.startswith("f "):
                faces.append(tuple(int(t.split("/")[0]) - 1 for t in line.split()[1:4]))
        mesh = bpy.data.meshes.new("game body")
        mesh.from_pydata(verts, [], faces)
        shell_obj = bpy.data.objects.new("game body", mesh)
        mat = bpy.data.materials.new("game body")
        mat.use_nodes = True
        bsdf = mat.node_tree.nodes["Principled BSDF"]
        bsdf.inputs["Base Color"].default_value = (0.55, 0.12, 0.10, 1.0)
        bsdf.inputs["Alpha"].default_value = 0.35
        mesh.materials.append(mat)
        scene.collection.objects.link(shell_obj)
    for name, (at, (yaw, pitch), fov) in VIEWS.items():
        if only and name not in only:
            continue
        if shell_obj is not None:
            shell_obj.hide_render = name != "outside"
        camera.location = at
        camera.data.sensor_fit = "VERTICAL"
        camera.data.angle = math.radians(fov)
        camera.data.clip_start = 0.01
        y, p = math.radians(yaw), math.radians(pitch)
        way = Vector((math.cos(p) * math.cos(y), math.cos(p) * math.sin(y), math.sin(p)))
        camera.rotation_euler = way.to_track_quat("-Z", "Y").to_euler()
        scene.render.filepath = os.path.join(folder, name + ".png")
        bpy.ops.render.render(write_still=True)


# Each style: its module, and every body built in it with the interior's name. Each body has a texture of its own,
# baked from it: the bodies of a style differ in parts as well as in measures.
STYLES = {
    "sedan": (sedan, {"Base.CarNormal": "carnormal", "Base.CarStationWagon": "carstationwagon"}),
    "compact": (compact, {"Base.SmallCar02": "smallcar02", "Base.SmallCar": "smallcar"}),
    "truck": (truck, {"Base.SUV": "suv", "Base.PickUpTruck": "pickuptruck"}),
    "van": (van, {"Base.VanSeats": "vanseats", "Base.Van": "van"}),
    "luxury": (luxury, {"Base.CarLuxury": "carluxury"}),
    "modern": (modern, {"Base.ModernCar": "moderncar", "Base.ModernCar02": "moderncar02"}),
    "sports": (sports, {"Base.SportsCar": "sportscar", "Base.RaceCar": "racecar"}),
    "offroad": (offroad, {"Base.OffRoad": "offroad"}),
    "stepvan": (stepvan, {"Base.StepVan": "stepvan"}),
}


def style_of(family):
    for style, (module, families) in STYLES.items():
        if family in families:
            return style, module, families[family]
    raise SystemExit("no style builds " + family)


def seat_on_dashboard():
    """Lays each demister's grille onto the dashboard's top where it stands, keeping its own thickness: each style
    places it roughly by the windscreen's foot, and where the dash's top curves away it floated above it."""
    from mathutils.bvhtree import BVHTree
    dashes = [o for o in kit.PARTS if o.name.startswith("dashboard")]
    if not dashes:
        return
    graph = bpy.context.evaluated_depsgraph_get()
    trees = [BVHTree.FromObject(o, graph) for o in dashes]
    for o in kit.PARTS:
        if not o.name.startswith("demister"):
            continue
        lowest = min(v.co.z for v in o.data.vertices)
        for vert in o.data.vertices:
            hits = [t.ray_cast(Vector((vert.co.x, vert.co.y, vert.co.z + 0.3)), Vector((0.0, 0.0, -1.0))) for t in trees]
            zs = [h[0].z for h in hits if h[0] is not None]
            if zs:
                vert.co.z = max(zs) + 0.0015 + (vert.co.z - lowest)


def lay_on_face():
    """Lays each part marked lay onto the dashboard's face straight ahead of it, its front that far proud of the face
    and its depth kept, so a lid cut from a curved face follows it as a flat panel cannot."""
    from mathutils.bvhtree import BVHTree
    laid = [o for o in kit.PARTS if "lay" in o.keys()]
    if not laid:
        return
    graph = bpy.context.evaluated_depsgraph_get()
    trees = [BVHTree.FromObject(o, graph) for o in kit.PARTS if o.name.startswith("dashboard")]
    for o in laid:
        front = min(v.co.x for v in o.data.vertices)
        for vert in o.data.vertices:
            hits = [t.ray_cast(Vector((vert.co.x - 0.3, vert.co.y, vert.co.z)), Vector((1.0, 0.0, 0.0))) for t in trees]
            xs = [h[0].x for h in hits if h[0] is not None]
            if not xs:
                raise RuntimeError(f"{o.name} has no face behind it at {tuple(vert.co)}")
            vert.co.x = min(xs) - o["lay"] + (vert.co.x - front)


def through_glass(house):
    """Each part that stands out through the windscreen, the side glass, the roof or the bulkhead, with how far it
    reaches past them at most, in metres. The door mirrors stand outside and are left out, and the pillars and rails
    that cover the glass's edges may reach past it as far as they are thick."""
    s = house.side
    found = {}
    graph = bpy.context.evaluated_depsgraph_get()
    for o in kit.PARTS:
        if o["group"] == kit.GLASS or o.name.startswith("door mirror"):
            continue
        mesh = o.evaluated_get(graph).to_mesh()
        worst, where = 0.0, ""
        for vert in mesh.vertices:
            p = o.matrix_world @ vert.co
            out = abs(p.y - house.mid)
            fx, fz = house.edge("foot", out)
            tx, tz = house.edge("top", out)
            pasts = {"roof": p.z - house.roof_z(out) - 0.004}
            if fz < p.z < tz and out < house.screen["foot"][3]:
                pasts["windscreen"] = p.x - kit.lerp(fx, tx, (p.z - fz) / (tz - fz))
            # Under the windscreen's foot the bonnet begins: nothing of the cabin reaches far past the bulkhead.
            if p.z <= fz:
                pasts["bulkhead"] = p.x - fx - 0.30
            if s["sill_z"] < p.z < s["top_z"] and house.back["foot"][0] < p.x < fx:
                pasts["side glass"] = out - house.glass_out(p.z)
            for surface, past in pasts.items():
                if past > worst:
                    worst, where = past, "%s at (%.2f, %.2f, %.2f)" % (surface, p.x, p.y, p.z)
        o.evaluated_get(graph).to_mesh_clear()
        edging = o.name.startswith(("a pillar", "b pillar", "roof rail", "header"))
        if worst > (0.035 if edging else 0.005):
            found[o.name] = (worst, where)
    return found


def outside_body(house, path):
    """Each part with places outside the game's own body below the foot of the side glass, measured in the same frame: a
    place is outside when, looking from it away from the vehicle's middle line, across and up, there is no skin of the
    body to meet. Only there can the driver see it, through the glass, standing out past the wings or the doors: the
    interior is drawn for the driver alone, who never sees the body's roof or pillars from outside. The door mirrors
    stand outside and are left out."""
    from mathutils.bvhtree import BVHTree
    verts, faces = [], []
    for line in open(path):
        if line.startswith("v "):
            verts.append(Vector(tuple(map(float, line.split()[1:4]))))
        elif line.startswith("f "):
            faces.append(tuple(int(t.split("/")[0]) - 1 for t in line.split()[1:4]))
    tree = BVHTree.FromPolygons(verts, faces)
    graph = bpy.context.evaluated_depsgraph_get()
    found = {}
    for o in kit.PARTS:
        if o["group"] == kit.GLASS or o.name.startswith("door mirror"):
            continue
        mesh = o.evaluated_get(graph).to_mesh()
        count, where = 0, None
        for k in range(0, len(mesh.vertices), max(1, len(mesh.vertices) // 400)):
            p = o.matrix_world @ mesh.vertices[k].co
            if p.z > house.side["sill_z"]:
                continue
            away = Vector((0.0, 1.0 if p.y > house.mid else -1.0, 0.0))
            if tree.ray_cast(p, away)[0] is None and tree.ray_cast(p, Vector((0.0, 0.0, 1.0)))[0] is None:
                count += 1
                where = tuple(p)
        o.evaluated_get(graph).to_mesh_clear()
        if count:
            found[o.name] = (count, where)
    return found


# The game draws each interior built this many levels of subdivision coarser than the parts ask for.
DRAWN_COARSER = 2


def assemble(family, module):
    """Builds a body's interior and dresses its parts: what has a place in the texture, what is drawn in a repeating
    surface alone, the glass and the mirrors, and what the mod is told of it."""
    clear()
    house = module.build(bodies.FAMILIES[family])
    seat_on_dashboard()
    lay_on_face()
    materials.dress_all(kit.PARTS)
    solid, tiled, drawn = make.kinds(kit.PARTS)
    mirror_parts = [o for o in drawn if o["group"] == kit.MIRROR]
    order = sorted(mirror_parts, key=lambda o: (0 if o.name.startswith("mirror glass") else 1 if o.name.endswith("glass") else 2, o.name))
    mirrors = [make.mirror_uv(o, k) for k, o in enumerate(order)]
    return house, solid, tiled, drawn, mirrors


def main():
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    materials.PICTURES_DIR[0] = os.path.abspath(args[args.index("--pictures") + 1]) if "--pictures" in args else os.path.abspath(".lab/carcabin/pictures")
    samples = int(os.environ.get("AO_SAMPLES", "96"))
    if "--coarser" in args:
        kit.COARSER[0] = int(args[args.index("--coarser") + 1])
    if "--detail" in args:
        clear()
        detail.bake_layers(make.PICTURES)
        return
    if "--stats" in args or "--look" in args or "--check" in args or "--pick" in args:
        family = args[args.index("--family") + 1] if "--family" in args else "Base.CarNormal"
        clear()
        house = style_of(family)[1].build(bodies.FAMILIES[family])
        seat_on_dashboard()
        lay_on_face()
        if "--pick" in args:
            # --pick view,x,y[;view,x,y...]: the part seen at each pixel of a 1280 by 720 render of that view.
            for o in [o for o in kit.PARTS if o["group"] == kit.GLASS]:
                bpy.data.objects.remove(o)
            graph = bpy.context.evaluated_depsgraph_get()
            for spec in args[args.index("--pick") + 1].split(";"):
                view, px, py = spec.split(",")
                at, (yaw, pitch), fov = VIEWS[view]
                y, p_ = math.radians(yaw), math.radians(pitch)
                ahead = Vector((math.cos(p_) * math.cos(y), math.cos(p_) * math.sin(y), math.sin(p_)))
                right = ahead.cross(Vector((0, 0, 1))).normalized()
                up = right.cross(ahead)
                half = math.tan(math.radians(fov) / 2)
                nx, ny = (float(px) / 1280 - 0.5) * 2 * half * 1280 / 720, (0.5 - float(py) / 720) * 2 * half
                hit, where, _, _, obj, _ = bpy.context.scene.ray_cast(graph, Vector(at), (ahead + right * nx + up * ny).normalized())
                print("PICK %s %s,%s: %s at (%.3f, %.3f, %.3f)" % (view, px, py, obj.name if hit else "nothing", *(where if hit else (0, 0, 0))))
            return
        if "--check" in args:
            for name, (past, where) in sorted(through_glass(house).items(), key=lambda item: -item[1][0]):
                print("THROUGH %s %s %.3f %s" % (family, name, past, where))
            if "--body" in args:
                for name, (count, where) in sorted(outside_body(house, os.path.abspath(args[args.index("--body") + 1])).items(), key=lambda item: -item[1][0]):
                    print("OUTSIDE %s %s %d places, e.g. (%.3f, %.3f, %.3f)" % (family, name, count, *where))
            return
        if "--stats" in args:
            rows = sorted(((sum(len(p.vertices) - 2 for p in o.data.polygons), o.name) for o in kit.PARTS), reverse=True)
            print("triangles in all: %d" % sum(r[0] for r in rows))
            for count, name in rows[:40]:
                print("%7d  %s" % (count, name))
            return
        plain_materials()
        folder = os.path.abspath(args[args.index("--renders") + 1]) if "--renders" in args else os.path.abspath(".lab/carcabin/look")
        only = args[args.index("--views") + 1].split(",") if "--views" in args else None
        game_body = os.path.abspath(args[args.index("--body") + 1]) if "--body" in args else None
        render(folder, True, samples=int(os.environ.get("SAMPLES", "24")), only=only, body=game_body)
        return
    styles = [args[args.index("--style") + 1]] if "--style" in args else list(STYLES)
    only = args[args.index("--only") + 1] if "--only" in args else None
    for style in styles:
        module, families = STYLES[style]
        for family, name in families.items():
            if only not in (None, family):
                continue
            # Each body built coarse, the model the game draws, and its texture baked from it.
            kit.COARSER[0] = DRAWN_COARSER
            house, solid, tiled, drawn, mirrors = assemble(family, module)
            cabin = make.unwrap(solid)
            make.lay_tiled(tiled)
            baked = make.bake_texture(cabin, drawn, samples)
            colour, surface = make.pictures_of(baked)
            make.bake_vertices(cabin, tiled, drawn, samples)
            make.pack(name, cabin, tiled, drawn, module.info(house, mirrors), name, colour, surface)
            if "--renders" in args:
                folder = os.path.join(os.path.abspath(args[args.index("--renders") + 1]), name)
                os.makedirs(folder, exist_ok=True)
                make.dress(cabin, colour, surface, folder)
                for o in drawn:
                    o.hide_render = o["group"] == kit.GLASS
                render(folder, False, samples=int(os.environ.get("SAMPLES", "32")), only=args[args.index("--views") + 1].split(",") if "--views" in args else None)

main()
