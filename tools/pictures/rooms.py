"""What kind of room each room of the game's map is, for the pictures that hang in it (Decor.inRoom).

    python tools/pictures/rooms.py MAP

MAP is the folder of the game's map, ...\\ProjectZomboid\\media\\maps\\Muldraugh, KY. Every room name in its lotheaders is
put in a kind by own(), and written with it, lower case, to src/main/resources/pictures/rooms.txt as name, a tab and the
kind. A name that does not say what its building is (office, hall, kitchen) is written with UNSAID, or OFFICE_LIKE for the
rooms an office has, and takes its kind from its building's other rooms at runtime by kind(), which Decor ports line for
line. src/test/data/rooms.txt is the test of that port: a room of each name on the map in each sort of building it is in,
as the kind this file gives it, a tab, its name, a tab and its building's room names.
"""
import os
import pathlib
import re
import struct
import sys

HERE = pathlib.Path(__file__).resolve().parent
TABLE = HERE.parents[1] / 'src' / 'main' / 'resources' / 'pictures' / 'rooms.txt'
CHECKS = HERE.parents[1] / 'src' / 'test' / 'data' / 'rooms.txt'

# A tie between two kinds of a building's rooms goes to the one first here: a station, a school or a gym with a canteen
# or a counter in it is still the station, the school or the gym.
KINDS = ('home', 'police', 'prison', 'medical', 'school', 'church', 'motel', 'gym', 'stage', 'photostudio', 'industrial', 'office',
         'shop', 'bar', 'restaurant')
UNSAID, OFFICE_LIKE = '-', '-office'

NAMES_UNSAID = {
    'hall', 'hallway', 'corridor', 'kitchen', 'lobby', 'bathroom', 'empty', 'closet', 'storage', 'garage', 'janitor', 'room1', 'room2',
    'room3', 'room4', 'stairwell', 'elevator', 'derelict', 'vacated', 'emptycommercial', 'shed', 'maintenance', 'foyer', 'laundry',
    'emptyoutside', 'dining', 'diningr', 'attic', 'basement', 'changeroom', 'garbage', 'balcony', 'roofstairs', 'roofaccess', 'hallway2',
    'storagehall', 'hallstorage', 'miscstorage', 'sttorage', 'technical', '', 'all', 'archive', 'catwalk', 'emptykitchen', 'main', 'mainhall',
    'nogo', 'outside', 'power', 'electricroom', 'trash', 'tunnel', 'cave', 'bunker', 'basementaccess', 'showers', 'lostandfound', 'vault',
    'industry', 'machinery', 'coldroom', 'coldstorage', 'pantry', 'patio', 'porch', 'survivorcache2', 'killbox', 'location_sewer_01_11', 'sas',
    'scou', 'traincar', 'greenhouse', 'garage_storage', 'contraband', 'baggageroom', 'baggagesearch', 'darkroom', 'horsebox',
    'horsewashingstation', 'picnic', 'reverendpeterwattsbathroom', 'revpeterwattsbathroom', 'waterstorage'}
NAMES_OFFICE_LIKE = {'office', 'officestorage', 'meeting', 'meetingroom', 'breakroom', 'breakoom', 'nolansoffice'}
NAMES_HOME = {
    'livingroom', 'bedroom', 'bedroom1', 'bedroom4', 'kidsbedroom', 'kidsbedroom4', 'diningroom', 'garagestorage', 'guestbedroom', 'nursery',
    'sewingroom', 'homecinema', 'gameroom', 'bandlivingroom', 'ww_bedroom', 'ww_livingroom', 'ww_kitchen', 'ww_hall', 'cultistbedroom', 'sunroom',
    'agriworkerdorm', 'barn', 'stable', 'farmstorage', 'woodcraftset', 'haystorage', 'liviingroom', 'chickencoop', 'pigsty', 'woodshed',
    'ww_shed', 'boathouse'}
# The first that matches a name gives its kind.
RULES = [
    ('bar', r'^(bar|barstorage|barkitchen|ww_bar|stripclub|stripclubvip|stripcluboffice|viplounge|smokingroom|beergarden|barcountertwiggy'
            r'|barg_bathroom|lapdancecabin)$'),
    ('restaurant', r'dining|restaurant|pizza|^diner|dinerkitchen|dinerbackroom|^cafe|cafeteria|icecream|bakery$|bakerykitchen|donut|spiffo'
                   r'|jayschicken|burger|sushi|seafood|mexican|chinese|chili|deepfry|catfish|western|theatrekitchen|arenakitchen|cybercafe|italian'
                   r'|fishchips|foodcourt|hotdogstand|juicestand|kitchen_crepe|tacokitchen'),
    ('prison', r'^prisoncells$'),
    ('police', r'police|prison|^security|detective|interrogation|evidence|swat|^fire|armory|armystorage|oldarmy|sherrif|ranger|captainoffice|coroner|armytent'),
    ('medical', r'medical|medclinic|^clinic|dentist|hospital|morgue|^lab$|laboratory|^vet$|decontamination|pharmacystorage|optometrist|waitingroom'),
    ('school', r'classroom|elementaryhall|secondaryhall|schoollab|schoolstorage|schoolgym|library|daycare|musicschool|musicroom|universityoffice'
               r'|^school$|universitystorage'),
    ('church', r'church'),
    ('gym', r'^gym|fitness|boxing|bowling|^pool$|locker|rollerrink|recreation|baseballstorage|sportstorage|batstorage'
            r'|^sport$|sportroom|^golf$|baseballchangeroom|changeroomjockey'),
    ('photostudio', r'^photoroom$'),
    ('stage', r'theatre|backstage|^studio|gallery|arcade|dressingrooms|joanstudio|broadcasting|newsroom|_herald|communications|controlroom|serverroom'
              r'|game$|lasertag|ticketoffice|^band|^music$|musicstage|musicstorage|^stage$|ballroom|partyhallstorage|museum|jackiejaye|judgematthas'),
    ('motel', r'motel'),
    ('office', r'^bank|depositboxes|cardealershipoffice|mayorwestpointoffice|office_ranger|^post|poststorage|^officechurch'),
    ('shop', r'bookclassic|showroom|auction|laundryshirts|gardeningstorage|^sports$|gunstorage|store|shop|grocery|gigamart|pawn|pharmacy|florist'
             r'|movierental|antique|butcher|gas2go|fossoil|zippee|jewelry|petstore|plazastore|kitchenwares|camping|hunting|armysurplus|carsupply'
             r'|aesthetic|^spa$|clockrepair|outdoorsupply|kennels|fishing|liquor|giftstorage|clothingstorage|clothesstorage|tailorstorage|wedding'
             r'|sewingstore|shoestorage|departmentstorage|bookstorage|candystorage|comicstorage|clothesestorage|housewarestorage|toolstorage'
             r'|toystorage|sewingstorage|lightingstorage|tobaccostorage|hairdresser|laundrysuits'),
    ('industrial', r'druglab|factory|shipping|workshop|warehouse|mechanic|storageunit|fabrication|bottling|brewery|distillery|slaughter|railroad'
                   r'|aircraft|plumber|blacksmith|construction|metalshop|metal|weld|potato|produce|tofu|carpenter|gasstorage|batterystorage'
                   r'|electronicsstorage|dogfoodstorage|furniturestorage|whiskey|print|logging|bottlestorage|cannedstorage|eggstorage|hingestorage|jerky'
                   r'|mannequinpainting|radiostorage|sodastorage|sodatruck|whiskystorage|racetrackgarage|drugshack'),
]


def own(name):
    """The kind a room's name says it is, UNSAID or OFFICE_LIKE where it says nothing of its building, or None where no rule knows it."""
    name = name.lower()
    if name in NAMES_HOME:
        return 'home'
    if name in NAMES_OFFICE_LIKE:
        return OFFICE_LIKE
    if name in NAMES_UNSAID:
        return UNSAID
    for kind, rule in RULES:
        if re.search(rule, name):
            return kind
    return None


def kind(said, building):
    """The kind of a room, given what its own name says (a kind, UNSAID or OFFICE_LIKE) and what each of its building's room
    names says, one entry a name. A room whose name says its kind is that. Otherwise, in a building with no business it is a
    home; in one that is a home over a business, a room an office has is the business and any other is the home; in a
    business, a room an office has is an office where the building has as many of those as of its most common business,
    and any other is that business."""
    if said not in (UNSAID, OFFICE_LIKE):
        return said
    business = {}
    for each in building:
        if each not in (UNSAID, OFFICE_LIKE, 'home'):
            business[each] = business.get(each, 0) + 1
    if not business:
        return 'home'
    top = min(business, key=lambda k: (-business[k], KINDS.index(k)))
    if 'home' in building:
        return top if said == OFFICE_LIKE else 'home'
    if said == OFFICE_LIKE and building.count(OFFICE_LIKE) >= business[top]:
        return 'office'
    return top


def read_map(folder):
    """Every building of the map as the list of its rooms' names, and the names of the rooms in no building."""
    buildings, alone = [], []
    for header in sorted(pathlib.Path(folder).glob('*.lotheader')):
        b = header.read_bytes()
        if b[:4] != b'LOTH':
            raise ValueError(f'{header} is not a lotheader')
        _, tiles = struct.unpack_from('<ii', b, 4)
        p = 12
        for _ in range(tiles):
            p = b.index(b'\n', p) + 1
        p += 16
        count = struct.unpack_from('<i', b, p)[0]
        p += 4
        names = []
        for _ in range(count):
            end = b.index(b'\n', p)
            names.append(b[p:end].decode('latin1').strip())
            p = end + 1
            _, rects = struct.unpack_from('<ii', b, p)
            p += 8 + 16 * rects
            p += 4 + 12 * struct.unpack_from('<i', b, p)[0]
        housed = set()
        count = struct.unpack_from('<i', b, p)[0]
        p += 4
        for _ in range(count):
            held = struct.unpack_from('<i', b, p)[0]
            p += 4
            rooms = struct.unpack_from(f'<{held}i', b, p)
            p += 4 * held
            buildings.append([names[r] for r in rooms])
            housed.update(rooms)
        alone += [n for i, n in enumerate(names) if i not in housed]
    return buildings, alone


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    buildings, alone = read_map(sys.argv[1])
    names = sorted({n.lower() for b in buildings for n in b} | {n.lower() for n in alone})
    unknown = [n for n in names if own(n) is None]
    if unknown:
        raise SystemExit('no rule says what kind of room these are: ' + ' '.join(unknown))
    with open(TABLE, 'w', encoding='utf-8', newline='\n') as table:
        for name in names:
            table.write(f'{name}\t{own(name)}\n')
    # Each name once, and a room of each sort of building that kind() tells apart: what its name says, the counts of its
    # building's businesses, whether the building is also a home, and how many of its names are rooms an office has.
    seen = {}
    for building in buildings:
        distinct = sorted({n.lower() for n in building})
        says = [own(n) for n in distinct]
        business = tuple(sorted((k, says.count(k)) for k in set(says) if k not in (UNSAID, OFFICE_LIKE, 'home')))
        for name in distinct:
            seen.setdefault(name, (name, distinct))
            if own(name) in (UNSAID, OFFICE_LIKE):
                seen.setdefault((own(name), business, 'home' in says, says.count(OFFICE_LIKE)), (name, distinct))
    with open(CHECKS, 'w', encoding='utf-8', newline='\n') as checks:
        for name, distinct in sorted(set((n, tuple(d)) for n, d in seen.values())):
            checks.write(f"{kind(own(name), [own(n) for n in distinct])}\t{name}\t{' '.join(distinct)}\n")
    print(f'{len(names)} room names, {len(buildings)} buildings, {len(seen)} rooms to check')


if __name__ == '__main__':
    main()
