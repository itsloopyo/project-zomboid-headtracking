"""What every batch of props shares: builds a batch's models with furniture.py's parts and packs them, with where each
stands on the pieces the game lays its overlays over, for the jar.

A batch is one script here, tools/models/props/BATCH.py, for one family of pieces. Run it headless with Blender 4.4,
from the repository root:

    blender --background --factory-startup --python tools/models/props/BATCH.py

It writes two files into src/main/resources/props, the folder of the props' own art jar:

    BATCH.bin.gz   its models, in furniture.bin.gz's format (see furniture.py), each named BATCH_something
    BATCH.txt      what each overlay makes of each piece, which Props.java reads and documents line by line

and beside the script BATCH.built: the SHA-256 of the batch's script, of this one and of the two files it wrote, and
each host as the batch built it, by the bounds of its model and the SHA-256 of furniture.bin.gz's model of it. PropsTests
reads it to say the committed files are the scripts', and that each host is the jar's model of it, the model the props
stand on: a host whose door or handle furniture.py changes is found as surely as one built to another size. The batch refuses to
write where a host it builds is not the size furniture.bin.gz has it.

The build finds every batch by its pair of files, so a batch adds its own script and its own two files and edits
nothing shared: not furniture.py, not this file. A batch builds its props in the space of the model of the piece they stand on (its host), as
furniture.py builds that model: metres, Z up, the back to +Y. Where a prop stands is then the host's own geometry,
and the mod stands it however the piece is turned and scaled.
"""

import gzip
import hashlib
import os
import struct
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

import furniture  # noqa: E402  (furniture.py builds nothing when imported)
from mathutils import Vector  # noqa: E402

RESOURCES = os.path.normpath(os.path.join(HERE, "..", "..", "..", "src", "main", "resources"))
PROPS = os.path.join(RESOURCES, "props")
FURNITURE = os.path.join(RESOURCES, "models", "furniture.bin.gz")
# How far a host's bounds as a batch builds it may be from furniture.bin.gz's: what a float holds of a few metres.
SAME_SIZE = 1e-4
SURFACE_BITS = {name: 1 << i for i, name in enumerate(furniture.SURFACES)}


def origin(model):
    """Where finish will move a built model's origin to, in the space it was built in: the middle of its footprint on
    its lowest point, or for a model not centred the origin it was built round, on its lowest point."""
    low = Vector([min(v[0][axis] for v in model.vertices) for axis in range(3)])
    high = Vector([max(v[0][axis] for v in model.vertices) for axis in range(3)])
    if model.centred:
        return Vector(((low.x + high.x) / 2, (low.y + high.y) / 2, low.z))
    return Vector((0.0, 0.0, low.z))


def bounds(model):
    """A built model's bounds as finish will leave them, from its origin: low x, y, z and high x, y, z."""
    at = origin(model)
    return ([min(v[0][axis] for v in model.vertices) - at[axis] for axis in range(3)]
            + [max(v[0][axis] for v in model.vertices) - at[axis] for axis in range(3)])


def furniture_models():
    """Each model furniture.bin.gz has, by name: its bounds, and the SHA-256 of its bytes there, from its name's length
    to its plain shape's last triangle."""
    data = gzip.open(FURNITURE).read()
    if data[:4] != b"CUFM":
        raise SystemExit("%s is not a file of models" % FURNITURE)
    _, count = struct.unpack_from("<II", data, 4)
    at = 12
    out = {}
    for _ in range(count):
        start = at
        size = data[at]
        name = data[at + 1:at + 1 + size].decode("ascii")
        at += 1 + size
        bounds = list(struct.unpack_from("<6f", data, at))
        at += 28
        for _ in range(2):
            vertices, triangles = struct.unpack_from("<II", data, at)
            at += 8 + vertices * 26 + triangles * 6
        out[name] = (bounds, hashlib.sha256(data[start:at]).hexdigest())
    return out


def stands_on(prop, host):
    """Has a prop built in its host's space shaded by the host as it will stand there: the host's triangles shade it
    and its floor is the host's."""
    prop.around = [tuple(host.vertices[i + k][0].copy() for k in range(3)) for i in range(0, len(host.vertices), 3)]
    prop.floor = 0.0


class Swap:
    """The host drawn as another model while the overlay lies over it."""
    def __init__(self, overlay, host, model):
        self.overlay, self.host, self.model = overlay, host, model


class Prop:
    """A model built where it stands in the host's space. dye is "host", "plain" or ("over", surfaces, point): the
    surfaces named take the colour the overlay paints at that point of the host's space."""
    def __init__(self, overlay, host, model, dye="host"):
        self.overlay, self.host, self.model, self.dye = overlay, host, model, dye


class Marks:
    """Marks spread over the parallelogram corner, corner + across, corner + along of the host's space, across by along
    of them, each standing where the overlay paints its point, its surfaces named in dyed taken from the overlay there.
    models are the names of the marks, each built standing on its own origin."""
    def __init__(self, overlay, host, models, corner, across, along, counts, dyed):
        self.overlay, self.host, self.models, self.dyed = overlay, host, models, dyed
        self.corner, self.across, self.along, self.counts = Vector(corner), Vector(across), Vector(along), counts


def _numbers(vector):
    return " ".join("%.5f" % value for value in vector)


def _mask(surfaces):
    mask = 0
    for surface in surfaces:
        mask |= SURFACE_BITS[surface]
    return mask


def write(batch, script, build, hosts):
    """Builds and writes the batch. build() returns (models, entries) and is called three times, as furniture.py
    builds each of its models: whole, plain, and plain with its soft parts firmer. hosts maps the name of each host the
    entries name to a function building its model, whose origin the places are written from."""
    furniture.plain = furniture.soft_whole = False
    models, entries = build()
    for model in models:
        if not model.name.startswith(batch + "_"):
            raise SystemExit("%s: a model of the batch is called %s_something, so no two batches' models share a name" % (model.name, batch))
    furniture.plain = True
    plains, _ = build()
    furniture.soft_whole = True
    firmer, _ = build()
    furniture.plain = furniture.soft_whole = False
    built_hosts = {name: make() for name, make in hosts.items()}
    origins = {name: origin(model) for name, model in built_hosts.items()}
    sizes = {name: bounds(model) for name, model in built_hosts.items()}
    shipped = furniture_models()
    for name, size in sorted(sizes.items()):
        if name not in shipped:
            raise SystemExit("the host %s is not in furniture.bin.gz" % name)
        if max(abs(a - b) for a, b in zip(size, shipped[name][0])) > SAME_SIZE:
            raise SystemExit("the host %s as %s builds it is %s, and furniture.bin.gz has it %s: the batch is to build it as every() does"
                             % (name, batch, _numbers(size), _numbers(shipped[name][0])))

    data = [b"CUFM", struct.pack("<II", 3, len(models))]
    by_name = {}
    for model, less, firm in zip(models, plains, firmer):
        if model.shares is not None:
            raise SystemExit("%s: a prop is one model, not shares of one" % model.name)
        if less.apart(model) > furniture.PLAIN_DETAIL:
            less = firm
        apart = less.apart(model)
        model.finish()
        less.finish(model)
        data += [model.header(apart), model.packed(), less.packed()]
        by_name[model.name] = model
        print("%-28s %5d triangles, plain %5d, within %.4f  x %.3f to %.3f  y %.3f to %.3f  z %.3f to %.3f" % (
            model.name, len(model.vertices) // 3, len(less.vertices) // 3, apart, model.low.x, model.high.x, model.low.y, model.high.y,
            model.low.z, model.high.z))
        if len(model.vertices) // 3 > model.most:
            raise SystemExit("%s has more triangles than allowed (%d)" % (model.name, model.most))
        if less.longest_shade:
            raise SystemExit("the plain %s still has a sharp shade over %.3f" % (model.name, less.longest_shade))

    lines = []
    for entry in entries:
        if entry.host not in origins:
            raise SystemExit("%s is made on %s, which is not among the batch's hosts" % (entry.overlay, entry.host))
        for name in [entry.model] if isinstance(entry, Prop) else entry.models if isinstance(entry, Marks) else []:
            if name in by_name and (not by_name[name].around or by_name[name].floor != 0.0):
                raise SystemExit("%s stands on %s and was built without props.stands_on, which shades it by its host" % (name, entry.host))
        at = origins[entry.host]
        head = "%s\t%s" % (entry.overlay, entry.host)
        if isinstance(entry, Swap):
            if entry.model not in by_name:
                raise SystemExit("%s swaps %s for %s, which the batch does not make" % (entry.overlay, entry.host, entry.model))
            lines.append("swap\t%s\t%s\t%s" % (head, entry.model, _numbers(by_name[entry.model].move - at)))
        elif isinstance(entry, Prop):
            model = by_name[entry.model]
            if entry.dye in ("host", "plain"):
                dye = entry.dye
            else:
                _, surfaces, point = entry.dye
                dye = "over:%d:%s" % (_mask(surfaces), ",".join("%.5f" % value for value in Vector(point) - at))
            lines.append("prop\t%s\t%s\t%s\t%s" % (head, entry.model, _numbers(model.move - at), dye))
        else:
            for name in entry.models:
                if name not in by_name:
                    raise SystemExit("%s spreads %s, which the batch does not make" % (entry.overlay, name))
            lines.append("marks\t%s\t%s\t%s\t%s\t%s\t%d %d\t%d" % (head, ",".join(entry.models), _numbers(entry.corner - at), _numbers(entry.across),
                                                                _numbers(entry.along), entry.counts[0], entry.counts[1], _mask(entry.dyed)))

    packed = os.path.join(PROPS, "%s.bin.gz" % batch)
    os.makedirs(PROPS, exist_ok=True)
    with open(packed, "wb") as raw:
        # No name and no time in the gzip header, so the same models pack to the same bytes.
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as out:
            out.write(b"".join(data))
    places = os.path.join(PROPS, "%s.txt" % batch)
    with open(places, "w", encoding="ascii", newline="\n") as out:
        out.write("# Made by tools/models/props/%s.py: see Props.java for what each line says.\n" % batch)
        for line in lines:
            out.write(line + "\n")
    print("wrote %s, %d bytes, and %s, %d lines" % (packed, os.path.getsize(packed), places, len(lines)))
    built = os.path.join(HERE, "%s.built" % batch)
    with open(built, "w", newline="\n") as out:
        for named in (script, os.path.abspath(__file__), packed, places):
            with open(named, "rb") as read:
                out.write("%s %s\n" % (os.path.basename(named), hashlib.sha256(read.read()).hexdigest()))
        for name, size in sorted(sizes.items()):
            out.write("host %s %s %s\n" % (name, _numbers(size), shipped[name][1]))
    print("wrote %s" % built)
