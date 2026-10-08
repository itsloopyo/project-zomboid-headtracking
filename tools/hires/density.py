"""
What the first person view still draws from a coarse picture of the game's, ranked.

    python tools/hires/density.py OUT_DIR [--game DIR] [--index src/main/resources/pictures/index.txt] [--tag NAME]

Reads the installed game, never the repo: the tile properties (media/*.tiles), the map as
shipped (media/maps/Muldraugh, KY/*.lotpack, every cell) and the sprite sheets
(media/texturepacks/*2x*.pack). Writes OUT_DIR/table[-TAG].tsv, a row a sprite the map places,
and OUT_DIR/sheets[-TAG].tsv, the same summed by sheet, both worst first. Counting the map is
slow the first time (a few minutes) and is kept in OUT_DIR/placed.pkl.

Columns of table.tsv:
  sprite     the game's name for it
  placed     how many squares of the shipped map hold it
  indoors    what share of those are squares of a room
  level      the commonest level it stands on
  drawn      how WorldScene draws it (see DRAWN below): the rules here follow
             WorldScene.addSprite, Furniture.recognise, materialOf and Masonry.suits by the
             sprite's name and tile properties. They are a reading of that code, not the code:
             what the mod decides from the sprite's dots (Tiles, Papers, Masonry, SpriteShape)
             is taken as the usual outcome for the sheet.
  own        1 where the mod shows a picture or surface of its own in place of the sprite's
  texels_m   texels of the picture across a metre of the surface it is drawn on
  metres2    how much surface a placing of it covers
  away_m     the distance it is usually looked at from
  enlarged   dots of a 1080 line high screen to a texel of it at that distance (field of view 65)
  detail     how much the picture has to lose: the mean step in brightness between
             neighbouring dots of it, 0 to 1. A plain wall has none and a lettered sign much.
  score      placed x metres2 x (enlarged - 1) x detail, 0 where own is 1 or enlarged <= 1
  whose      "surface" for a floor, a wall or a roof that repeats from square to square, "thing" for all else
  called     the game's CustomName|GroupName for it, where it has one
  where      a few places it stands, x,y,z

A tile is a metre (Furniture.TILES_PER_METRE). The game's 2x sheets paint a tile 128 dots
across and 64 down, and a level, 2.449 tiles high, 192 dots high. So a wall or anything on
the grid's lines has 64 texels a metre along and 78 up; a floor 90 one way and 45 the
other; an upright card turned to the eye 90 across and 78 up.
"""
import argparse
import collections
import glob
import io
import math
import os
import pickle
import struct
import sys

import numpy as np
from PIL import Image

STEAM = r"C:\Program Files (x86)\Steam\steamapps\common\ProjectZomboid"
TILE_FILES = ["newtiledefinitions.tiles", "tiledefinitions_erosion.tiles", "tiledefinitions_overlays.tiles",
              "tiledefinitions_b42chunkcaching.tiles", "tiledefinitions_noiseworks.patch.tiles"]
PACKS = ["Tiles2x.pack", "Tiles2x.floor.pack", "Overlays2x.pack"]
SCREEN_LINES = 1080
FIELD_OF_VIEW = 65.0
LEVEL_TILES = 2.44949
ALONG, UP, CARD_ACROSS = 64.0, 192.0 / LEVEL_TILES, 128.0 / math.sqrt(2.0)
FLOOR = 64.0


def tiles(media):
    out = {}
    for at, name in enumerate(TILE_FILES):
        data = open(os.path.join(media, name), 'rb').read()
        if data[:4] != b'tdef':
            raise SystemExit(name + " is no tile definition file")
        place = 4

        def line():
            nonlocal place
            end = data.index(b'\n', place)
            text = data[place:end].decode('latin1').strip()
            place = end + 1
            return text

        def number():
            nonlocal place
            value = struct.unpack_from('<i', data, place)[0]
            place += 4
            return value

        number()
        for _ in range(number()):
            sheet = line()
            line()
            number(), number(), number()
            for tile in range(number()):
                properties = {}
                for _ in range(number()):
                    key = line()
                    properties[key] = line()
                if properties or at == 0:
                    key = "%s_%d" % (sheet, tile)
                    if at and key in out:
                        out[key].update(properties)
                    else:
                        out[key] = properties
    return out


def placed(maps, keep):
    """For every sprite name: squares holding it, how many of those in a room, by level, and a few places."""
    if os.path.exists(keep):
        return pickle.load(open(keep, 'rb'))
    count = collections.Counter()
    inside = collections.Counter()
    levels = collections.defaultdict(collections.Counter)
    where = collections.defaultdict(list)
    headers = sorted(glob.glob(os.path.join(maps, '*.lotheader')))
    for done, header in enumerate(headers):
        data = open(header, 'rb').read()
        if data[:4] != b'LOTH':
            raise SystemExit(header + " is no lot header")
        _, names_count = struct.unpack_from('<ii', data, 4)
        place = 12
        names = []
        for _ in range(names_count):
            end = data.index(b'\n', place)
            names.append(data[place:end].decode('latin1').strip())
            place = end + 1
        wide, high, lowest, highest = struct.unpack_from('<iiii', data, place)
        cx, cy = map(int, os.path.basename(header).split('.')[0].split('_'))
        pack_path = os.path.join(maps, "world_%d_%d.lotpack" % (cx, cy))
        if not os.path.exists(pack_path):
            continue
        pack = open(pack_path, 'rb').read()
        if pack[:4] != b'LOTP':
            raise SystemExit(pack_path + " is no lot pack")
        ints = np.frombuffer(pack, dtype='<i4', count=(len(pack) - len(pack) % 4) // 4).tolist()
        per_level = wide * high
        for chunk in range(1024):
            at = struct.unpack_from('<q', pack, 12 + 8 * chunk)[0] // 4
            square = 0
            squares = per_level * (highest - lowest + 1)
            base_x, base_y = cx * 256 + (chunk // 32) * 8, cy * 256 + (chunk % 32) * 8
            while square < squares:
                held = ints[at]
                at += 1
                if held == -1:
                    square += ints[at]
                    at += 1
                    continue
                if held > 1:
                    room = ints[at]
                    z = lowest + square // per_level
                    rest = square % per_level
                    for index in ints[at + 1:at + held]:
                        name = names[index]
                        count[name] += 1
                        if room != -1:
                            inside[name] += 1
                        levels[name][z] += 1
                        spots = where[name]
                        if len(spots) < 4:
                            spots.append((base_x + rest // high, base_y + rest % high, z))
                    at += held
                square += 1
        if done % 200 == 0:
            print("map: %d of %d cells" % (done, len(headers)), file=sys.stderr)
    out = (count, inside, {name: dict(by) for name, by in levels.items()}, dict(where))
    pickle.dump(out, open(keep, 'wb'))
    return out


def sheets_index(packs):
    """Every sprite of the game's 2x sheets: the file, where its page's PNG is, and its rectangle on the page."""
    out = {}
    for path in packs:
        data = open(path, 'rb').read()
        if data[:4] != b'PZPK':
            raise SystemExit(path + " is no texture pack of the kind read here")
        place = 8
        pages = struct.unpack_from('<i', data, place)[0]
        place += 4
        for _ in range(pages):
            length = struct.unpack_from('<i', data, place)[0]
            place += 4 + length
            entries, _ = struct.unpack_from('<ii', data, place)
            place += 8
            subs = []
            for _ in range(entries):
                length = struct.unpack_from('<i', data, place)[0]
                place += 4
                name = data[place:place + length].decode('latin1')
                place += length
                subs.append((name, struct.unpack_from('<8i', data, place)))
                place += 32
            size = struct.unpack_from('<i', data, place)[0]
            place += 4
            for name, rectangle in subs:
                out[name] = (path, place, size, rectangle)
            place += size
    return out


class Sheets:
    def __init__(self, packs):
        self.index = sheets_index(packs)
        self.pages = {}
        self.order = collections.deque()

    def measure(self, name):
        """Dots painted, and the mean step in brightness between neighbouring painted dots, 0 to 1."""
        if name not in self.index:
            return None
        path, start, size, (x, y, w, h, _, _, _, _) = self.index[name]
        key = (path, start)
        if key not in self.pages:
            with open(path, 'rb') as file:
                file.seek(start)
                self.pages[key] = np.asarray(Image.open(io.BytesIO(file.read(size))).convert('RGBA'), dtype=np.float32) / 255.0
            self.order.append(key)
            if len(self.order) > 6:
                del self.pages[self.order.popleft()]
        cut = self.pages[key][y:y + h, x:x + w]
        if cut.size == 0:
            return None
        cover = cut[..., 3] > 0.5
        bright = cut[..., 0] * 0.3 + cut[..., 1] * 0.59 + cut[..., 2] * 0.11
        steps, pairs = 0.0, 0
        if w > 1:
            both = cover[:, 1:] & cover[:, :-1]
            steps += float(np.abs(bright[:, 1:] - bright[:, :-1])[both].sum())
            pairs += int(both.sum())
        if h > 1:
            both = cover[1:] & cover[:-1]
            steps += float(np.abs(bright[1:] - bright[:-1])[both].sum())
            pairs += int(both.sum())
        return int(cover.sum()), steps / pairs if pairs else 0.0


MASONRY = ("walls_exterior_house", "walls_exterior_roofs", "walls_house_", "walls_commercial", "walls_garage")
FRAMED = {"Painting", "Paintings", "Certificate", "Whiteboard", "Noteboard", "Dartboard"}
MODELLED = {"Clock", "Wall Lamp", "Outdoor Lamp", "Metal Drum", "Barrel", "Barbecue", "Stool", "Crate", "Bed", "Wardrobe", "Locker", "Couch",
            "Shelves", "Chair", "Seat", "Drawers", "Desk", "Table", "Low Table", "Counter", "Sink", "Fridge", "Freezer", "Machine", "Dumpster",
            "Shower", "Oven", "Television", "Lamp", "Toilet", "Bath", "Bin", "Box", "Bench", "Picknic Table", "Radio"}

# What "drawn" says, and whether the picture is the mod's own (1) or the game's sprite (0).
DRAWN = {
    "ground of the mod's": 1, "floor: tiles or carpet of the mod's": 1, "floor: sprite under the mod's grain": 0, "floor: sprite": 0,
    "laid flat: sprite": 0, "wire or pole of the mod's": 1, "picture of the mod's": 1, "frame of the mod's round the sprite": 0,
    "model of the mod's": 1, "model of the mod's (most of the name)": 1, "curtain of the mod's": 1, "door of the mod's": 1, "window of the mod's": 1,
    "wall: courses or paper of the mod's": 1, "wall: sprite under the mod's grain": 0, "on a wall: sprite": 0, "roof: sprite under the mod's grain": 0,
    "plant of the mod's": 1, "box, panel or card: sprite under the mod's grain": 0, "box, panel or card: sprite": 0, "not drawn": 1,
}


def drawn(name, properties, has_picture):
    """How WorldScene.addSprite draws a sprite, the density of its picture each way, and whether it lies on the ground."""
    has = properties.__contains__
    called = properties.get("CustomName")
    group = properties.get("GroupName", "")
    facing = properties.get("Facing")
    wall = (ALONG, UP)
    card = (CARD_ACROSS, UP)
    floor = (FLOOR, FLOOR)
    grows = has("vegitation") or has("canBeRemoved") or has("MoveWithWind")
    if name.startswith("blends_") or grows and name.startswith(("vegetation_", "f_", "e_", "d_")):
        # The ground and what grows on it: spreadGround, growGrass, addGrown and Flora stand in for these.
        return "ground of the mod's" if name.startswith("blends_") else "plant of the mod's", floor
    if name.startswith("floors_") or has("solidfloor") or has("FloorOverlay") or has("attachedFloor"):
        if name.startswith(("blends_natural", "blends_street", "floors_exterior_street")) or properties.get("FloorMaterial") == "Road_04":
            return "ground of the mod's", floor
        if name.startswith("floors_interior_carpet") or name.startswith("floors_") and "tile" in name and not has("FloorOverlay"):
            return "floor: tiles or carpet of the mod's", floor
        if "wood" in name or name.startswith("floors_rugs"):
            return "floor: sprite under the mod's grain", floor
        return "floor: sprite", floor
    if properties.get("RenderLayer") == "Floor" or properties.get("MoveType") == "FloorRug":
        return "laid flat: sprite", floor
    if name.startswith("appliances_com"):
        return "wire or pole of the mod's", wall
    if called and facing in ("S", "E") and not has("solid") and not has("solidtrans") and not has("container") and not has("IsTableTop"):
        if has_picture(name, called, group, "SpriteGridPos" in properties):
            return "picture of the mod's", wall
        if called in FRAMED:
            return "frame of the mod's round the sprite", wall
    if called in MODELLED or name.startswith("lighting_outdoor_01_") and not called:
        if called in ("Clock", "Bed", "Wardrobe", "Couch", "Fridge", "Oven", "Toilet", "Bath", "Dumpster", "Television", "Desk", "Barrel", "Metal Drum"):
            return "model of the mod's", wall
        return "model of the mod's (most of the name)", wall
    if name.startswith("fixtures_windows_curtains"):
        return "curtain of the mod's", wall
    if name.startswith("fixtures_doors") and not name.startswith("fixtures_doors_fences"):
        return "door of the mod's", wall
    if name.startswith("fixtures_windows") or has("WindowN") or has("WindowW"):
        return "window of the mod's", wall
    edge = has("WallN") or has("WallW") or has("WallNW") or has("WallSE") or has("DoorWallN") or has("DoorWallW") or has("WallNTrans") or has("WallWTrans")
    attached = has("attachedN") or has("attachedW") or has("attachedE") or has("attachedS") or has("WallOverlay")
    if edge:
        if name.startswith(MASONRY) or name.startswith("walls_interior_house"):
            return "wall: courses or paper of the mod's", wall
        return "wall: sprite under the mod's grain", wall
    if attached:
        return "on a wall: sprite", wall
    if name.startswith("roofs_"):
        return "roof: sprite under the mod's grain", (ALONG, ALONG)
    if has("vegitation") or has("canBeRemoved") or has("MoveWithWind") or name.startswith(("e_", "vegetation_", "f_", "d_plants", "boulders_")):
        return "plant of the mod's", card
    grained = name.startswith(("fencing_", "carpentry_", "furniture_tables", "furniture_storage", "furniture_shelving", "fixtures_counters",
                               "furniture_seating_indoor", "furniture_bedding", "lighting_", "walls_"))
    return ("box, panel or card: sprite under the mod's grain" if grained else "box, panel or card: sprite"), card


def away(what, share_inside, level):
    """The distance a thing is usually looked at from, in metres: across a room, from the street, or up from it."""
    if share_inside >= 0.5:
        return 2.0
    if level >= 1:
        return math.hypot(4.0, LEVEL_TILES * level + 0.4)
    return 3.0 if what.startswith(("ground", "floor", "laid")) else 4.0


def pictures(index_path):
    shows, shows_long = set(), set()
    if index_path and os.path.exists(index_path):
        for line in open(index_path, encoding='utf-8'):
            if line.startswith('='):
                shows.add(line[1:].split('\t')[0])
            elif line.startswith('+'):
                shows_long.add(line[1:].split('\t')[0])

    def has_picture(name, called, group, on_several):
        key = "%s|%s" % (called, group)
        return name in shows or key in shows or on_several and key in shows_long
    return has_picture


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("out")
    parser.add_argument("--game", default=STEAM)
    parser.add_argument("--index", default=os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "pictures", "index.txt"))
    parser.add_argument("--tag", default="")
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)
    media = os.path.join(args.game, "media")
    properties = tiles(media)
    count, inside, levels, where = placed(os.path.join(media, "maps", "Muldraugh, KY"), os.path.join(args.out, "placed.pkl"))
    sheets = Sheets([os.path.join(media, "texturepacks", name) for name in PACKS])
    has_picture = pictures(args.index)
    per_metre = SCREEN_LINES / (2.0 * math.tan(math.radians(FIELD_OF_VIEW / 2.0)))
    rows = []
    # In the order of the sheets' pages, so that each page is unpacked once.
    for name in sorted(count, key=lambda n: sheets.index.get(n, ("", 0))[:2]):
        measured = sheets.measure(name)
        if measured is None:
            continue
        dots, detail = measured
        props = properties.get(name, {})
        what, (across, up) = drawn(name, props, has_picture)
        share = inside[name] / count[name]
        level = max(levels[name].items(), key=lambda item: item[1])[0]
        far = away(what, share, level)
        texels = math.sqrt(across * up)
        enlarged = per_metre / far / texels
        metres2 = dots / (across * up)
        own = DRAWN[what]
        score = 0.0 if own or enlarged <= 1.0 else count[name] * metres2 * (enlarged - 1.0) * detail
        whose = "surface" if what.startswith(("ground", "floor", "wall:", "roof")) else "thing"
        called = "%s|%s" % (props["CustomName"], props.get("GroupName", "")) if "CustomName" in props else ""
        rows.append((score, name, count[name], share, level, what, own, texels, metres2, far, enlarged, detail, whose, called,
                     " ".join("%d,%d,%d" % spot for spot in where[name][:3])))
    rows.sort(key=lambda row: -row[0])
    tag = "-" + args.tag if args.tag else ""
    with open(os.path.join(args.out, "table%s.tsv" % tag), "w", encoding="utf-8") as out:
        out.write("sprite\tplaced\tindoors\tlevel\tdrawn\town\ttexels_m\tmetres2\taway_m\tenlarged\tdetail\tscore\twhose\tcalled\twhere\n")
        for score, name, n, share, level, what, own, texels, metres2, far, enlarged, detail, whose, called, spots in rows:
            out.write("%s\t%d\t%.2f\t%d\t%s\t%d\t%.0f\t%.2f\t%.1f\t%.1f\t%.3f\t%.1f\t%s\t%s\t%s\n"
                      % (name, n, share, level, what, own, texels, metres2, far, enlarged, detail, score, whose, called, spots))
    by_sheet = collections.defaultdict(lambda: [0.0, 0, 0, 0, collections.Counter(), 0.0, collections.Counter()])
    for score, name, n, share, level, what, own, texels, metres2, far, enlarged, detail, whose, called, spots in rows:
        sheet = by_sheet[name.rsplit('_', 1)[0]]
        sheet[0] += score
        sheet[1] += n
        sheet[2] += 1
        sheet[3] += own
        sheet[4][what] += n
        sheet[5] = max(sheet[5], enlarged)
        sheet[6][whose] += n
    with open(os.path.join(args.out, "sheets%s.tsv" % tag), "w", encoding="utf-8") as out:
        out.write("sheet\tscore\tplaced\tsprites\tsprites_own\tmost_enlarged\twhose\tmostly_drawn\n")
        for sheet, (score, n, sprites, own, whats, enlarged, whoses) in sorted(by_sheet.items(), key=lambda item: -item[1][0]):
            out.write("%s\t%.1f\t%d\t%d\t%d\t%.1f\t%s\t%s\n"
                      % (sheet, score, n, sprites, own, enlarged, whoses.most_common(1)[0][0], whats.most_common(1)[0][0]))
    print("%d sprites placed and measured, %d of them the mod's own; total score %.0f" % (len(rows), sum(row[6] for row in rows), sum(row[0] for row in rows)))


if __name__ == "__main__":
    main()
