"""What hangs on the walls of the first person view: every picture the mod makes, and which of the game's sprites shows it.

A picture is made from a text prompt alone (generate.py), then cut to the shapes it hangs in and given its lettering
(pack.py). Nothing of the game's own art goes into one.

ART is name -> (prompt, negative, width, height). HUNG is the list of (key, picture) that pack.py writes as the mod's
index: a key is a sprite's name, or the name and group the game gives a piece, "Painting|Lighthouse". POOLS is
(subject, room kind) -> pictures: a picture of that subject (subject()) hung in a room of that kind (rooms.py) shows one of
these instead, chosen by Decor.inRoom. LONG is the keys
that hold only for a piece lying along several squares: the game gives some single sprites, each a part of a hoarding,
the same name. SHAPED is how many times as wide as it is high a painting that hangs along several squares is on its
wall, inside its frame: it is cut to that. LETTERED says what is written on a poster. SEEDS is the candidate picked for each picture
(generate.py makes several).
"""

import pools
import rooms
import shows
import signs

NO_TEXT = 'text, letters, words, caption, watermark, signature, logo, frame, border'
PHOTO_NEGATIVE = NO_TEXT + ', illustration, painting, cartoon, 3d render, deformed, extra fingers, blurry, modern, smartphone'
PAINT_NEGATIVE = NO_TEXT + ', photograph, 3d render, wall, room, gallery, museum, easel'
POSTER_NEGATIVE = NO_TEXT + ', deformed, extra fingers, extra limbs, blurry, wall, room'

TALL, WIDE, SQUARE, BROAD, LONGER, STOUT = (832, 1216), (1216, 832), (1024, 1024), (1344, 768), (1536, 640), (1152, 896)

ART = {}
HUNG = []
POOLS = {}
LONG = set()
SHAPED = {}
LETTERED = {}
SEEDS = {}


def art(name, prompt, negative, size):
    if name in ART:
        raise ValueError('two pictures are called ' + name)
    ART[name] = (prompt, negative, size[0], size[1])


# Family photographs, as they hang in the houses of a small Kentucky town in 1993.
SNAP = (' Shot on 35mm colour negative film in the 1980s, a slightly faded drugstore print with soft contrast, fine grain and'
        ' natural colours, ordinary amateur framing.')
STUDIO = (' A 1980s department store studio portrait, mottled blue grey backdrop, soft flash, a slightly faded colour print with'
          ' fine grain.')
OLD = ' A 1940s black and white studio portrait, a silver gelatin print with soft grey tones and slight fading at the edges.'
FAMILY = [
    ('Head and shoulders portrait of a smiling grandmother of about seventy with curly grey hair and large glasses, in a floral'
     ' blouse.' + STUDIO, TALL),
    ('Three quarter length portrait of a young bride in a white lace wedding dress and veil, a small bouquet of white roses held'
     ' in front of her waist in both hands.' + STUDIO, TALL),
    ('Head and shoulders portrait of a young woman of about twenty with big permed brown hair, in a denim jacket over a white'
     ' blouse.' + STUDIO, TALL),
    ('Portrait of an old farmer in a plaid shirt and a plain cap, his forearms resting on the top rail of a wooden fence, an'
     ' old black tobacco barn behind him.' + SNAP, TALL),
    ('An elderly couple in their Sunday best standing side by side in front of a white clapboard church, seen from a few'
     ' metres away, her hand on his arm.' + SNAP, TALL),
    ('Head and shoulders portrait of a stern grandfather of about sixty in a dark suit and tie.' + OLD, TALL),
    ('Head and shoulders portrait of a young soldier in a 1940s United States army dress uniform and cap.' + OLD, TALL),
    ('A baby of about six months in a white christening gown sitting on a white blanket, smiling at the camera.' + STUDIO, TALL),
    ('School portrait of a ten year old boy with a bowl haircut and a striped shirt, a gap toothed smile.' + STUDIO, TALL),
    ('A family of four sitting a little apart on the wooden porch steps of a white farmhouse in summer, a father, a mother and'
     ' two children of about eight and ten, everyone smiling, hands resting on their knees, seen from a few metres away.' + SNAP, WIDE),
    ('Head and shoulders portrait of a teenage girl in a plain blue and white high school cheerleader uniform with no'
     ' lettering.' + STUDIO, TALL),
    ('Head and shoulders portrait of a middle aged man with a moustache in a brown suit and a wide tie.' + STUDIO, TALL),
    ('A young mother sitting on a chair with a toddler of about two sitting on her lap, both smiling, her arms around the'
     ' child.' + STUDIO, TALL),
    ('A little girl of about six in a yellow dress sitting on the grass in a garden beside a sleeping tabby cat.' + SNAP, TALL),
    ('A wedding couple standing side by side under an oak tree, the groom in a grey tuxedo and the bride in white, seen from'
     ' a few metres away.' + SNAP, TALL),
    ('Three generations of a family, grandparents, parents and two children, sitting together on a sofa in a wood panelled'
     ' living room at Christmas, a decorated tree beside them, seen from across the room.' + SNAP, WIDE),
    ('Head and shoulders portrait of a young woman in a green graduation cap and gown.' + STUDIO, TALL),
    ('Head and shoulders portrait of a grey haired man in a navy blazer and tie with a kindly smile.' + STUDIO, TALL),
    ('A golden retriever sitting on a lawn in front of a brick house on a summer afternoon.' + SNAP, TALL),
    ('A high school football player in a plain red jersey with no numbers kneeling on a grass field, his helmet on the grass'
     ' beside him.' + SNAP, TALL),
    ('Head and shoulders portrait of a young man in a dark blue graduation cap and gown with a proud smile.' + STUDIO, TALL),
    ('Two brothers of about ten and twelve in t-shirts and fishing vests standing side by side on a lake shore, a fishing rod'
     ' leaning on a tackle box on the grass in front of them, seen from a few metres away.' + SNAP, WIDE),
    ('Head and shoulders portrait of a woman of about forty with feathered blonde hair in a red sweater.' + STUDIO, TALL),
    ('A married couple and their teenage son and daughter standing a little apart in front of a wood panelled station wagon'
     ' on a driveway, seen from a few metres away.' + SNAP, WIDE),
]
for _i, (_prompt, _size) in enumerate(FAMILY):
    art('family-%02d' % _i, _prompt, PHOTO_NEGATIVE, _size)
for _block in range(3):
    for _i in range(8):
        # The sheet has eight seen from the south, then the same eight seen from the east.
        HUNG.append(('walls_decoration_02_%d' % (_block * 16 + _i), 'family-%02d' % (_block * 8 + _i)))
        HUNG.append(('walls_decoration_02_%d' % (_block * 16 + 8 + _i), 'family-%02d' % (_block * 8 + _i)))

# Sports posters of a teenager's bedroom, a bar and a gym. No lettering: a poster's kit is plain and unbranded.
ACTION = (' A 1990s sports poster made from a press photograph taken with a long lens, printed on glossy paper with a fine'
          ' halftone texture, slightly muted colours.')
STILL = ' A 1990s sports poster made from a plain studio photograph, printed on glossy paper with a fine halftone texture.'
WILD = ' A 1990s outdoor magazine wildlife photograph on slide film, natural colours, fine grain.'
SPORTS = {
    'basketball': [
        'A basketball player in a plain blue jersey and shorts with no numbers rising alone for a slam dunk, the ball above the rim,'
        ' the crowd behind far away and out of focus.' + ACTION,
        'A basketball player in a plain red jersey with no numbers dribbling low on a hardwood court, seen from the side, nobody'
        ' near him.' + ACTION,
        'A basketball player in a plain yellow jersey with no numbers in the air shooting a jump shot, the ball just leaving'
        ' his hands, the arena behind out of focus.' + ACTION,
        'A plain orange leather basketball with no markings resting on a polished hardwood court under a single spotlight.' + STILL,
        'A basketball dropping through the net of a single hoop, seen from below against the lights of an arena roof.' + STILL,
        'An empty indoor basketball court with a polished hardwood floor and one hoop, afternoon light from high windows.' + STILL,
    ],
    'tennis': [
        'A woman tennis player in a plain white dress hitting a forehand on a grass court, seen from the side, the ball on her'
        ' racket strings.' + ACTION,
        'A man tennis player in plain white clothes and a white headband tossing the ball up to serve on a blue hard court,'
        ' seen from the side.' + ACTION,
        'A woman tennis player in a plain pink dress stretching for a backhand on a red clay court, dust rising at her feet.' + ACTION,
        'A wooden tennis racket lying on a green grass court beside two yellow tennis balls.' + STILL,
        'A tennis player in plain white sliding on red clay to reach a low ball, seen from the far end of the court.' + ACTION,
        'A grass tennis court seen from high in the stands on a sunny day, two small players in white at either baseline.' + ACTION,
    ],
    'boxing': [
        'A heavyweight boxer in plain red gloves and black trunks throwing a straight punch alone in the ring under bright'
        ' lights, the dark arena behind.' + ACTION,
        'Two boxers in a ring under bright lights, one in red trunks and one in blue, squared up a metre apart, seen from'
        ' outside the ropes.' + ACTION,
        'A boxer in white trunks standing in his corner with his plain gloves raised, a single spotlight from above, the arena'
        ' dark.' + ACTION,
        'A pair of worn plain red leather boxing gloves with no markings hanging by their laces from a nail on a wooden wall.' + STILL,
        'A boxer in a grey sweatshirt hitting a heavy punch bag in an old brick gym, shafts of window light, seen from the'
        ' side.' + ACTION,
        'An empty boxing ring under bright lights in a dark arena, the canvas floor and red ropes, seen from ringside.' + STILL,
    ],
    'golf': [
        'A golfer in a white cap and plain polo shirt at the top of his swing on a green fairway, seen from the side, blue sky.' + ACTION,
        'A woman golfer in a plain pink visor crouched reading a putt on a green beside a flag, seen from a few metres away.' + ACTION,
        'A golfer in plaid trousers swinging out of a sand bunker, sand spraying, seen from the side.' + ACTION,
        'A white golf ball sitting on a wooden tee in trimmed grass, close up, a green fairway blurred behind.' + STILL,
        'A golf green with a single flag at sunrise, mist over rolling fairways and trees.' + STILL,
        'A golfer seen from behind in his follow through after a drive, a long fairway stretching ahead.' + ACTION,
    ],
    'baseball': [
        'A baseball batter in a plain blue pinstripe uniform with no lettering swinging at a pitch, seen from the side, the'
        ' catcher and stands blurred behind.' + ACTION,
        'A baseball pitcher in a plain grey uniform with no lettering and a red cap mid throw on the mound, seen from the'
        ' side.' + ACTION,
        'A baseball catcher in a mask and a plain green uniform crouching behind home plate, seen from the front.' + ACTION,
        'A wooden baseball bat and a scuffed white baseball lying on red infield dirt.' + STILL,
        'A baseball player in a plain white uniform sliding feet first into base in a cloud of dust, seen from the side.' + ACTION,
        'A worn brown leather baseball glove with a baseball in its pocket lying on a wooden dugout bench.' + STILL,
    ],
    'football': [
        'An American football quarterback in a plain blue and orange uniform with no numbers drawing back to throw, seen from'
        ' the side, the stadium blurred behind.' + ACTION,
        'An American football running back in a plain white uniform with no numbers running with the ball tucked under one arm,'
        ' alone in open field.' + ACTION,
        'An American football receiver in a plain red uniform with no numbers leaping alone for a pass, the ball just above his'
        ' hands.' + ACTION,
        'A brown leather American football lying on the white yard line of a grass field.' + STILL,
        'A plain blue American football helmet with no decal lying on the grass of a field at dusk, stadium lights behind.' + STILL,
        'An American football flying end over end between yellow goal posts in a stadium at dusk, seen from behind the posts.' + ACTION,
    ],
    'soccer': [
        'A soccer player in a plain red kit with no numbers striking the ball in a stadium, seen from the side, the crowd far'
        ' away and out of focus.' + ACTION,
        'A soccer goalkeeper in a plain green jersey diving at full stretch, the ball just past his gloves, seen from behind'
        ' the goal net.' + ACTION,
        'A soccer player in a plain blue and white striped kit running with the ball at his feet on a grass pitch, nobody near'
        ' him.' + ACTION,
        'A black and white leather soccer ball on the penalty spot of a grass pitch, the goal behind.' + STILL,
        'A soccer player in a plain yellow kit running with his arms wide after a goal, the crowd behind far away and out of'
        ' focus.' + ACTION,
        'An empty grass soccer pitch in a small stadium at evening, floodlights on, the goal in the foreground.' + STILL,
    ],
    'athletics': [
        'A sprinter in a plain red vest and shorts pushing out of the starting blocks on a running track, seen from the side.' + ACTION,
        'A high jumper in plain blue arching backwards over the bar in a stadium, seen from the side.' + ACTION,
        'A hurdler in plain blue clearing a hurdle on a red track, seen from the side.' + ACTION,
        'A woman long jumper in plain green in mid air over the sand pit, seen from the side.' + ACTION,
        'A javelin thrower in plain white at the moment of release, the javelin leaving his hand, seen from the side.' + ACTION,
        'An empty red running track curving round a stadium at sunrise, white lane lines.' + STILL,
    ],
    'weights': [
        'A weightlifter in a plain dark singlet holding a heavy barbell overhead on a competition platform, seen from the'
        ' front.' + ACTION,
        'A bodybuilder in plain black trunks flexing both arms on a stage against a dark curtain, seen from the front.' + ACTION,
        'An aerobics class of about a dozen people in loose t-shirts and track pants exercising with small dumbbells in a 1980s'
        ' gym, seen from the back of the room.' + ACTION,
        'A heavy iron dumbbell resting on a rubber gym floor, chalk dust in a shaft of light.' + STILL,
        'A man in a grey t-shirt lying on a bench pressing a barbell in an old gym, seen from the side.' + ACTION,
        'A loaded barbell with iron plates resting on a wooden lifting platform against a plain red background.' + STILL,
    ],
    'hockey': [
        'An ice hockey player in a plain blue jersey with no crest skating hard with the puck, ice spraying from his skates.' + ACTION,
        'An ice hockey goalie in white pads, a mask and a plain white jersey with no crest crouching in front of the net.' + ACTION,
        'An ice hockey player in a plain green jersey with no crest winding up for a slap shot, seen from the side.' + ACTION,
        'A wooden ice hockey stick and a black puck lying on scratched white ice.' + STILL,
        'An empty ice rink seen from the stands, the white ice and red and blue lines under arena lights.' + STILL,
        'An ice hockey player in a plain yellow and black jersey with no crest skating with his stick raised in one hand after'
        ' a goal.' + ACTION,
    ],
    'bowling': [
        'A bowler in a plain retro bowling shirt releasing a ball down a polished wooden lane, seen from behind.' + ACTION,
        'A bowling ball striking ten white pins at the end of a lane, pins flying.' + ACTION,
        'A glossy blue marbled bowling ball resting on a polished wooden lane beside one white bowling pin.' + STILL,
        'A woman in a plain pink bowling shirt standing on the approach of a lane in a 1980s bowling alley, about to bowl,'
        ' seen from behind.' + ACTION,
        'A row of empty bowling lanes in a retro bowling alley with neon lights over the pins.' + STILL,
        'Ten white bowling pins standing in their triangle at the end of a lane, seen from lane level.' + STILL,
    ],
    'racing': [
        'A single red 1990s American stock car, a heavy full bodied two door race car shaped like a sedan, with no lettering, racing round a banked oval track, motion blur in the background.' + ACTION,
        'Two 1990s American stock cars, heavy full bodied two door race cars shaped like sedans with no lettering, one blue and one yellow, racing side by side on a speedway, the grandstand blurred'
        ' behind.' + ACTION,
        'A racing driver in a plain red fire suit standing beside his full bodied American stock car in the pits, his helmet under one arm.' + ACTION,
        'A green and white full bodied 1990s American stock car with no lettering crossing the finish line, a chequered flag waving above.' + ACTION,
        'A line of colourful full bodied 1990s American stock cars with no lettering going through a turn at a speedway, seen from high in the stands.' + ACTION,
        'A full bodied 1990s American stock car with no lettering parked alone in a pit lane at dusk, the empty track behind.' + STILL,
    ],
    'pool': [
        'A man in a waistcoat leaning over a green pool table lining up a shot with his cue, a hanging lamp above, a dim bar'
        ' behind.' + ACTION,
        'A triangle of fifteen coloured pool balls racked on green felt, a cue lying beside them.' + STILL,
        'A black eight ball on the green felt of a pool table beside a corner pocket.' + STILL,
        'A cue ball breaking a rack of coloured pool balls on green felt, balls scattering, seen from table level.' + ACTION,
        'A pool table under a hanging lamp in an empty dim bar, balls scattered on the felt, a cue leaning on the table.' + STILL,
        'An old pool hall with rows of green tables under hanging lamps, empty, seen from the doorway.' + STILL,
    ],
    'fieldhockey': [
        'A woman field hockey player in a plain red kit running with the ball at the end of her stick on a grass pitch, seen'
        ' from the side.' + ACTION,
        'A woman field hockey player in a plain blue kit striking the ball on a grass pitch, seen from the side.' + ACTION,
        'A woman field hockey goalkeeper in padded kit crouching in front of her goal on a grass pitch.' + ACTION,
        'A wooden field hockey stick and a white ball lying on green grass.' + STILL,
    ],
    'hunting': [
        'A brown whitetail buck with a reddish brown coat and large antlers standing in an autumn meadow at dawn.' + WILD,
        'A wild turkey displaying its fanned tail in a woodland clearing.' + WILD,
        'A hunter in a plaid jacket and an orange cap walking away through autumn woods, a rifle slung on his shoulder, seen from'
        ' behind and a distance.' + WILD,
        'A mallard duck taking off from a misty marsh at sunrise.' + WILD,
        'A brown whitetail doe and her spotted fawn standing a little apart in tall green grass at the edge of a forest.' + WILD,
        'The black silhouette of a stag with antlers on a ridge against an orange sunset sky.' + WILD,
        'A pointer hunting dog standing rigid in a field of dry grass, nose forward.' + WILD,
        'A bull elk calling in a mountain valley in autumn.' + WILD,
    ],
}
for _sport, _prompts in SPORTS.items():
    for _i, _prompt in enumerate(_prompts):
        art('%s-%d' % (_sport, _i), _prompt, '', TALL)


def pooled(sheet, first, last, sport):
    """Each sprite of a run of one sport's posters shows one of that sport's pictures, spread so that neighbours differ."""
    count = len(SPORTS[sport])
    for index in range(first, last + 1):
        HUNG.append(('%s_%d' % (sheet, index), '%s-%d' % (sport, (index * 5 + index // 8) % count)))


pooled('walls_decoration_02', 48, 71, 'basketball')
pooled('walls_decoration_02', 72, 95, 'tennis')
pooled('walls_decoration_02', 96, 119, 'boxing')
pooled('walls_decoration_02', 120, 143, 'golf')
pooled('walls_decoration_02', 144, 167, 'baseball')
pooled('walls_decoration_02', 168, 191, 'football')
pooled('walls_decoration_05', 0, 23, 'soccer')
pooled('walls_decoration_05', 24, 47, 'athletics')
pooled('walls_decoration_05', 48, 71, 'weights')
pooled('walls_decoration_05', 72, 95, 'hockey')
pooled('walls_decoration_05', 96, 119, 'bowling')
pooled('walls_decoration_05', 120, 143, 'racing')
pooled('walls_decoration_05', 144, 159, 'pool')
pooled('walls_decoration_05', 160, 175, 'fieldhockey')
pooled('walls_decoration_05', 176, 199, 'hunting')

# Paintings, by the name the game gives each. Where the name points at a famous canvas the prompt describes that subject
# in words, and the model paints it afresh: no picture of any canvas is given to it.
OIL = ('. An oil painting on canvas with visible brushstrokes and slightly yellowed varnish, unsigned, the whole canvas'
       ' photographed straight on and filling the frame.')
PAINTINGS = {
    "Lilly's Waterpond": ('impressionist painting of water lilies floating on a pond, reflections of willows, soft blues, greens and violets' + OIL, WIDE),
    "She's Got It": ('classical painting of a woman in a white gown reclining on a red velvet chaise longue, dark drapery behind' + OIL, BROAD),
    'Aaaaah!': ('expressionist painting of a pale lone figure on a long bridge facing the viewer with both hands pressed to its cheeks and its mouth open in a scream, a swirling orange and red sky above a dark blue fjord' + OIL, TALL),
    'Abe Lincoln': ('formal 19th century portrait of Abraham Lincoln seated in a dark suit, sombre background' + OIL, TALL),
    'Apple Faced Man': ('surreal painting of a gentleman in a dark overcoat and bowler hat standing in front of a low wall, his face hidden behind a green apple floating in the air in front of it, his arms at his sides, a cloudy sky behind' + OIL, TALL),
    'Benjamin Franklin': ('formal 18th century portrait of Benjamin Franklin in a brown coat, long grey hair, dark background' + OIL, TALL),
    'Betty': ('1950s painted portrait of a smiling blonde woman with curled hair in a high necked blue cardigan, head and shoulders, warm brown background' + OIL, TALL),
    'Calvinist': ('17th century Dutch portrait of a stern old man in a black hat and a wide white ruff collar, dark background, chiaroscuro' + OIL, TALL),
    'Chromatic Accident': ('an abstract expressionist drip painting, dense tangled splatters and drips of black, rust orange, white and grey on a cream canvas' + '. The whole canvas photographed straight on and filling the frame, unsigned.', LONGER),
    'Configuration II': ('a geometric abstract painting in the De Stijl manner: a few straight thick black lines dividing a white canvas into a handful of large clean flat areas, rectangles of pure red, blue and yellow, crisp hard edges, flat even paint' + '. The whole canvas photographed straight on and filling the frame, unsigned.', SQUARE),
    'Configuration No. 10': ('a geometric abstract painting in the De Stijl manner: a few straight thick black lines dividing a white canvas into a handful of large clean flat areas, a large blue rectangle and small red and yellow rectangles, crisp hard edges, flat even paint' + '. The whole canvas photographed straight on and filling the frame, unsigned.', SQUARE),
    'Layout I': ('a geometric abstract painting in the De Stijl manner: a few straight thick black lines dividing a white canvas into a handful of large clean flat areas, a large red square and small blue and yellow rectangles, crisp hard edges, flat even paint' + '. The whole canvas photographed straight on and filling the frame, unsigned.', SQUARE),
    'Desert Landscape': ('painting of a desert landscape, red sandstone mesas and sagebrush under a huge pale sky at dusk' + OIL, BROAD),
    'Flowers On Table': ('still life painting of a vase of mixed flowers on a table with a white cloth, dark background, Dutch golden age' + OIL, TALL),
    'George Washington': ('formal 18th century portrait of George Washington in a black velvet suit, white powdered hair, red curtain behind' + OIL, TALL),
    'Helianthus': ('post-impressionist still life painting of sunflowers in a clay vase against a yellow wall, thick impasto' + OIL, TALL),
    'Incognito Library': ('painting of the interior of an old library, tall shelves of leather books, a fireplace, an armchair and a brass lamp, warm brown tones' + OIL, WIDE),
    'Island Beach': ('seascape painting of waves breaking on dark rocks off an island shore, turquoise sea, cloudy sky' + OIL, LONGER),
    'John Adams': ('formal 18th century portrait of John Adams in a dark coat and white cravat, grey hair, dark background' + OIL, TALL),
    'Lakeside': ('painting of a still woodland lake, green trees and their reflections, purple wildflowers on the bank, dappled light' + OIL, LONGER),
    'Lighthouse': ('painting of a white lighthouse with a red roof on a rocky headland, blue sea and sky, a wooden walkway leading to it' + OIL, TALL),
    'Man With One Ear': ('post-impressionist self portrait of a red bearded man in a fur cap and green coat with a white bandage over his ear, swirling brushstrokes' + OIL, TALL),
    'Margarite': ('17th century Dutch portrait of a girl in a blue and yellow turban glancing over her shoulder, a pearl earring, black background' + OIL, TALL),
    'Monica Elisa': ('an ink and watercolour portrait of a woman with long black hair in a red and orange patterned robe, head and shoulders, white paper background, unsigned', TALL),
    'Mount Never Rest': ('romantic landscape painting of a towering snow capped mountain above a lake and pine forest, golden light breaking through clouds' + OIL, LONGER),
    'Night With Stars': ('post-impressionist painting of a swirling starry night sky over a sleeping village with a church spire and a dark cypress, thick impasto' + OIL, BROAD),
    'Nomad Above The Gloom': ('romantic painting of a lone man in a dark frock coat seen from behind, standing on a rocky peak above a sea of fog and distant mountains' + OIL, SQUARE),
    'Omega Department': ('a 1970s children\'s storybook illustration in gouache of a friendly brown bear and a badger standing side by side a little apart, pale yellow background, printed on paper, unsigned', TALL),
    'Riverside': ('impressionist painting of a river bank in spring, poplars and their reflections, pink and lilac sky' + OIL, WIDE),
    'Sir Brakefire': ('17th century portrait of a gentleman with a pointed beard in a broad black hat and lace collar, dark background, chiaroscuro' + OIL, TALL),
    'Two Frame': ('small impressionist landscape painting of a country lane between flowering hedges, lilac hills beyond' + OIL, WIDE),
    'Violetta': ('impressionist painting of a woman with a parasol in a garden of pink roses, green lawn, sunlight' + OIL, WIDE),
    'Waterbeach Hills': ('landscape painting of rolling green hills and meadows under a blue sky with white clouds' + OIL, WIDE),
    'Waterfall': ('landscape painting of a waterfall dropping through autumn woods into a rocky pool' + OIL, WIDE),
    'Wide': ('classical landscape painting of a river valley with ancient trees, cattle at a ford and distant blue hills, golden evening light' + OIL, STOUT),
    'Dr. Oids': ('a framed 1980s arcade video game screen, chunky pixel art of a round yellow creature with an open mouth being chased along a corridor of a green neon maze by a pink pixel ghost, rows of small white dots in the corridors, black background, no lettering', TALL),
}
for _name, (_prompt, _size) in PAINTINGS.items():
    _key = 'painting-' + ''.join(c for c in _name.lower().replace(' ', '-') if c.isalnum() or c == '-').strip('-')
    art(_key, _prompt, NO_TEXT + ', wall, room' if 'pixel art' in _prompt or 'cartoon' in _prompt or 'geometric' in _prompt or 'drip' in _prompt
        or 'watercolour' in _prompt else PAINT_NEGATIVE, _size)
    HUNG.append(('Painting|' + _name, _key))
# Measured off the game's sprites of each with the mod's own sums, less the frame the mod paints round it.
for _name, _shape in (("Lilly's Waterpond", 1.5), ("She's Got It", 2.05), ('Chromatic Accident', 2.9), ('Desert Landscape', 2.0), ('Island Beach', 2.45),
                      ('Lakeside', 2.4), ('Mount Never Rest', 2.9), ('Night With Stars', 1.8), ('Nomad Above The Gloom', 1.0), ('Wide', 1.2)):
    SHAPED['painting-' + ''.join(c for c in _name.lower().replace(' ', '-') if c.isalnum() or c == '-').strip('-')] = _shape

art('painting-field-pink', 'a colour field abstract painting, two large soft edged hazy rectangles, magenta above warm yellow, on an orange ground, matte paint on canvas' + '. The whole canvas photographed straight on and filling the frame, unsigned.',
    NO_TEXT + ', wall, room, objects', TALL)
art('painting-field-purple', 'a colour field abstract painting, two large soft edged hazy rectangles, deep violet above midnight blue, on a dark plum ground, matte paint on canvas' + '. The whole canvas photographed straight on and filling the frame, unsigned.',
    NO_TEXT + ', wall, room, objects', TALL)
HUNG.append(('Paintings|Pink and Yelllow', 'painting-field-pink'))
HUNG.append(('Paintings|Purple and Blue', 'painting-field-purple'))
art('painting-sitter', 'dark old master portrait of a pale woman in a blue silk dress, sombre background, cracked varnish' + OIL, PAINT_NEGATIVE, TALL)
HUNG.append(('location_entertainment_gallery_01_72', 'painting-sitter'))
HUNG.append(('location_entertainment_gallery_01_73', 'painting-sitter'))

# Boards.
art('board-cork', 'A cork notice board seen straight on and filling the whole frame, covered in pinned paper notes, index cards, a'
    ' calendar page and two photographs, coloured push pins, the writing on the papers too small and faint to read. A plain'
    ' photograph in even office light.', '', WIDE)
art('board-white', 'A white dry erase board seen straight on and filling the whole frame, faint grey smudges of old marker, a hand'
    ' drawn bar chart and a few arrows in blue and red marker and no words. A plain photograph in even office light.', '', BROAD)
art('board-darts', 'An open dark wooden dartboard cabinet seen straight on, a round bristle dartboard in the middle with three darts in'
    ' it, two small blank black chalkboards inside the open doors. A plain photograph in even light.', '', BROAD)
for _group in ('Big Cork', 'Cork'):
    HUNG.append(('Noteboard|' + _group, 'board-cork'))
for _group in ('Office', 'Wide Office'):
    HUNG.append(('Whiteboard|' + _group, 'board-white'))
HUNG.append(('Dartboard|', 'board-darts'))
art('certificate', 'An empty unprinted sheet of cream parchment paper seen straight on and filling the whole frame, an engraved'
    ' guilloche border in dark green round its edge and a round embossed gold foil seal near the bottom, the middle of the sheet'
    ' plain and blank. A flat scan in even light.', '', WIDE)
HUNG.append(('Certificate|Doctor Degree', 'certificate-doctor'))
HUNG.append(('Certificate|Surgeon Degree', 'certificate-surgeon'))
LETTERED['certificate-doctor'] = ('certificate', 'diploma', 'Doctor of Medicine', 'Kentucky State School of Medicine')
LETTERED['certificate-surgeon'] = ('certificate', 'diploma', 'Fellow in Surgery', 'Kentucky College of Surgeons')
# What the same sprites show in a room of a business, lettered on the same blank sheet: name, title, who gives it, the
# line over the title and the line under it.
_HOLDS = 'certifies that this establishment holds a'
_VALID = 'valid through December 31, 1993'
_RIGHTS = 'with all the rights and privileges thereto pertaining'
CERTIFICATES = {
    'restaurant': [('food-permit', 'Food Service Permit', 'Kentucky Department for Public Health', _HOLDS, _VALID),
                   ('grade-a', 'Grade A', 'Knox County Health Department', 'on inspection this establishment has been awarded', 'inspected June 1993')],
    'shop': [('business-license', 'Business License', 'Commonwealth of Kentucky', 'certifies that this business holds a', _VALID),
             ('chamber', 'Member in Good Standing', 'Knox County Chamber of Commerce', 'proudly recognizes this business as a', 'for the year 1993')],
    'office': [('cpa', 'Certified Public Accountant', 'Kentucky State Board of Accountancy', 'has conferred upon the bearer the title of', _RIGHTS),
               ('employee', 'Employee of the Month', 'In Recognition of Outstanding Service', 'is proudly presented to the bearer as', 'with the thanks of all the staff')],
    'school': [('teaching', 'Teaching Certificate', 'Kentucky Department of Education', 'certifies that the bearer holds a', _RIGHTS),
               ('principal', 'Principal', 'Kentucky Department of Education', 'certifies that the bearer is qualified to serve as', _RIGHTS)],
    'police': [('peace-officer', 'Peace Officer Certification', 'Kentucky Law Enforcement Council', 'certifies that the bearer has earned', _RIGHTS)],
    'home': [('high-school', 'High School Diploma', 'Muldraugh High School', 'has conferred upon the bearer this',
              'with all the rights, honours and privileges thereto pertaining')],
}
for _kind, _sheets in CERTIFICATES.items():
    POOLS['certificate', _kind] = ['certificate-doctor', 'certificate-surgeon'] if _kind == 'home' else []
    for _name, _title, _issuer, _phrase, _foot in _sheets:
        LETTERED['certificate-' + _name] = ('certificate', 'diploma', _title, _issuer, _phrase, _foot)
        POOLS['certificate', _kind].append('certificate-' + _name)

# Posters of the bands and singers of the record shop and of bedroom walls: eighteen acts, each as a large sheet, a wide
# one and a small one. The art is square, and pack.py cuts each shape out of it.
GIG = (' The performers fill the lower two thirds of the frame and the top third is plain dark empty background. A 1980s'
       ' record company promotional photograph on colour film, a slightly faded print with fine grain.')
OLD_GIG = (' The performers fill the lower two thirds of the frame and the top third is plain empty background. A 1960s black'
           ' and white promotional photograph, a slightly faded silver print.')
MUSIC = [
    'A four piece glam rock band, two women and two men with big teased hair and black leather jackets, standing in a row a'
    ' little apart in front of a dark red curtain.' + GIG,
    'A rock and roll singer with slick dark hair in a red waistcoat and white shirt leaning against a brick wall, his arms'
    ' folded.' + OLD_GIG,
    'Four young men in matching slim grey suits and narrow ties standing in a row a little apart, a 1960s beat group.' + OLD_GIG,
    'A cheerful 1960s pop group of five in bright paisley shirts standing in a row a little apart against a pink background.' + GIG,
    'A 1970s hard rock band of four long haired men in denim standing in a row on a stage in front of a wall of amplifiers.' + GIG,
    'A rock singer with shoulder length brown hair and a headband standing at a microphone stand on a dark stage, a black shirt,'
    ' a single spotlight.' + GIG,
    'A country rock guitarist with long hair, sunglasses and a fringed suede jacket standing with an electric guitar slung on his'
    ' shoulder against an orange desert sunset.' + GIG,
    'A pop singer with wavy shoulder length blond hair in a black leather jacket and white t-shirt, head and shoulders, against a'
    ' deep blue background.' + GIG,
    'Three young men in white suits and blue caps standing side by side a little apart, a 1990s rhythm and blues vocal group,'
    ' cream background.' + GIG,
    'A red haired woman singer in a long white dress with long sleeves and a high neck, head and shoulders, cream and rose'
    ' background, a 1970s folk album cover.' + GIG,
    'An elderly crooner in a black tuxedo and bow tie standing beside a vintage silver microphone on a stand, black background,'
    ' warm spotlight.' + GIG,
    'A pop singer with long curly red hair laughing, in a denim jacket over a white t-shirt, head and shoulders, pale teal'
    ' background.' + GIG,
    'A country singer in a black hat and black shirt sitting on a stool with an acoustic guitar resting on his knee, an old barn'
    ' wall behind.' + OLD_GIG,
    'A young woman singer with short brown hair in a white t-shirt and jeans sitting cross legged on the floor, pastel pink'
    ' background.' + GIG,
    'A disco singer with a big afro in a long sleeved high necked purple sequin gown under a mirror ball, purple light.' + GIG,
    'A smiling country singer in a white cowboy hat and an embroidered western shirt with an acoustic guitar slung on his'
    ' shoulder, golden sepia background.' + GIG,
    'A folk singer in a flat cap, white shirt and red tie with an acoustic guitar slung on his shoulder, cream background.' + GIG,
    'An iron bridge over a river in a city with one single purple balloon floating above it, an indie album cover.' + OLD_GIG,
]
# The names the game's own posters carry, where they can be read off them. The rest hang with no lettering.
ACTS = {1: 'FRIDAY NIGHT', 2: 'THE 594s', 3: 'THE BOJANGLES', 4: 'STEAMSHIP HURRICANE', 6: 'WILL BILL GROOVY', 7: 'SOUNDS LIKE AUSTIN PARKER!',
        8: 'SOFT ONEZ', 9: 'MARIA', 10: 'TIME FOR LOVE', 11: 'TONI', 14: 'YVETTE', 15: 'HANK GILMAN'}
for _i, _prompt in enumerate(MUSIC):
    art('music-%02d' % _i, _prompt, '', SQUARE)
    if _i in ACTS:
        LETTERED['music-%02d-tall' % _i] = ('music-%02d' % _i, 'gig-tall', ACTS[_i], 'THE NEW ALBUM  .  OUT NOW')
        LETTERED['music-%02d-wide' % _i] = ('music-%02d' % _i, 'gig-wide', ACTS[_i], 'THE NEW ALBUM  .  OUT NOW')
# The sheet has them eight to a row, a row seen from the south and then a row seen from the east, and each act is three
# sprites running on through the rows of its side: the large sheet, the wide one, the small one.
_SHAPES = ('tall', 'wide', 'tall')
for _side in (0, 8):
    _run = [_row * 16 + _side + _i for _row in range(7) for _i in range(8)][:54]
    for _place, _index in enumerate(_run):
        HUNG.append(('walls_decoration_06_%d' % _index, 'music-%02d-%s' % (_place // 3, _SHAPES[_place % 3])))

# The posters of the films showing in 1993, as the game names them. Square art again, cut tall for a one sheet and broad
# for a hoarding, with the film's name lettered on.
FILM = (' The subject fills the upper half of the frame and the lower third is plain dark empty background. A 1993 movie'
        ' poster made from a studio photograph, printed on paper with a fine halftone texture and slightly muted colours.')
FILMS = {
    'Cyberkiller': ('Cyberkiller', 'Head and shoulders of a grim square jawed soldier in black body armour, a riveted steel plate over'
                    ' the left half of his face with a round blue camera lens where the eye was, dark teal background.' + FILM,
                    'CYBERKILLER', 'IN THEATERS NOW'),
    'Cyberkiller 2': ('Cyberkiller 2', 'A muscular bearded man in a leather jacket and a soldier with a riveted steel plate over half'
                      ' his face standing side by side a little apart, dark teal background.' + FILM,
                      'CYBERKILLER 2', 'IN THEATERS NOW'),
    'Operation Fort Knox': ('Operation Fort Knox', 'A blond secret agent in a black tuxedo, head and shoulders, stacks of gold bars'
                            ' glowing behind him, golden light.' + FILM, 'OPERATION FORT KNOX', 'IN THEATERS NOW'),
    'PAWS': ('PAWS', 'A raccoon, a brown bear cub and a badger walking a little apart along a path in a sunny green forest, a 1993'
             ' hand painted animated film poster in gouache with soft colours, printed on paper with a fine halftone texture.',
             'PAWS', 'IN THEATERS NOW'),
    'Dog Goblin 4': ('Dog Goblin 4', 'A huge cracked stone skull half buried in a misty moonlit wood, bare trees, black and steel'
                     ' blue.' + FILM, 'DOG GOBLIN 4', 'IN THEATERS NOW'),
    'Pleistocene Land': ('Pleistocene Land', 'A woolly mammoth with great curved tusks walking through a snowy pine forest in a'
                         ' blizzard, pale winter light.' + FILM, 'PLEISTOCENE LAND', 'IN THEATERS NOW'),
    'Dr Oids': ('Dr Oids', 'Chunky 1980s arcade pixel art of a round yellow creature with an open mouth and a pink pixel ghost in a'
                ' green neon maze on black, printed on a poster.', 'DR. OIDS', 'NOW SHOWING'),
    'The Forgotten Element': ('The Forgotten Element', 'A rugged man in a fedora and leather jacket standing beside a dark haired'
                              ' woman in a khaki shirt, a little apart, a propeller plane in a golden sunset sky behind them.' + FILM,
                              'THE FORGOTTEN ELEMENT', 'COMING SOON'),
    'Movie': ('Movie', 'A man and a woman in raincoats standing close together under a street lamp on a rainy city street at'
              ' night.' + FILM, 'ONE NIGHT IN LOUISVILLE', 'IN THEATERS NOW'),
}
for _group, (_name, _prompt, _title, _line) in FILMS.items():
    _key = 'film-' + _name.lower().replace(' ', '-')
    art(_key, _prompt, '', SQUARE)
    LETTERED[_key + '-tall'] = (_key, 'tall', _title, _line)
    LETTERED[_key + '-broad'] = (_key, 'broad', _title, _line)
    for _shape, _cut in (('', 'tall'), (' Small', 'tall'), (' Large', 'broad'), (' Wide', 'broad')):
        HUNG.append(('Poster|' + _group + _shape, _key + '-' + _cut))
        if _cut == 'broad':
            LONG.add('Poster|' + _group + _shape)
HUNG.append(('Banner|Movie', 'film-movie-broad'))
LONG.add('Banner|Movie')

# What a television that is on shows: stills of the programmes of 1993, four wide for three high as a tube is. They hang
# nowhere, so no sprite is their key: SCREENS is their names in the order the mod numbers them, and pack.py makes each
# again in black and white for a set with no colour, and draws the bars a set shows with nothing coming in.
ON_AIR = (' A still frame from a 1993 American television broadcast recorded off air onto VHS, soft analog video with slight'
          ' colour bleed and muted colours.')
PROGRAMMES = [
    'A male news anchor in a grey suit and red tie sitting at a news desk in front of a plain blue studio backdrop, looking into'
    ' the camera.' + ON_AIR,
    'A weather forecaster in a brown suit standing beside a weather map of the United States with simple sun and cloud symbols'
    ' and no words.' + ON_AIR,
    'A cheerful woman in an apron standing at a stove in a television studio kitchen set, a pot on the stove, a cooking show.' + ON_AIR,
    'A bearded man in a plaid shirt and safety glasses standing at a workbench in a woodwork shop beside a half finished wooden'
    ' chair, a woodworking show.' + ON_AIR,
    'A man in a fishing vest and cap sitting in a small aluminium boat on a calm lake, a fishing rod in his hands, a fishing'
    ' show.' + ON_AIR,
    'A baseball pitcher in a plain white uniform throwing from the mound, green outfield grass behind, a televised baseball'
    ' game seen from behind the plate.' + ON_AIR,
    'A woman with big blonde hair in a blue evening dress and a man in a tuxedo facing each other across an opulent living room,'
    ' a daytime soap opera.' + ON_AIR,
    'A small blue robot and a green frog building a cardboard rocket in a garage, a 1980s Saturday morning cartoon in hand'
    ' painted cel animation with flat colours, recorded off air onto VHS.',
    'A brown whitetail deer with a reddish brown summer coat standing in a misty forest clearing at dawn, a nature documentary.' + ON_AIR,
    'A game show host in a sparkly blue jacket standing on a brightly lit stage with coloured lights and three empty contestant'
    ' podiums, a game show.' + ON_AIR,
    'A cowboy on horseback riding through a desert canyon, a classic western film shown on television.' + ON_AIR,
    'An aerobics class of about a dozen people in loose t-shirts and track pants seen from the back of a bright studio, a 1980s'
    ' exercise show.' + ON_AIR,
]
SCREENS = ['screen-%02d' % _i for _i in range(len(PROGRAMMES))]
for _name, _prompt in zip(SCREENS, PROGRAMMES):
    art(_name, _prompt, '', STOUT)

# The shots of each show a set can tell it is showing (shows.py). SHOTS is each packed picture's name with the art it is
# cut from: screen-<show>-<n>, and the dozen above again as the show of no one programme, screen-any-<n>.
# A shot is packed at 640 by 480: it is made at three quarters of the pixels a picture for a wall is.
TUBE = (1024, 768)
SHOTS = {'screen-%s-%d' % (shows.ANY, _i): _name for _i, _name in enumerate(SCREENS)}
for _show, _shots in shows.SHOWS.items():
    for _i, _prompt in enumerate(_shots):
        _name = 'screen-%s-%d' % (_show, _i)
        art(_name, _prompt, '', TUBE)
        SHOTS[_name] = _name

# Shop signs, notices and flags (signs.py): their grounds and emblems are art like any other, and each shows on the
# name and group the game gives the sign.
for _name, (_prompt, _negative, _wide, _high) in signs.ART.items():
    art(_name, _prompt, _negative, (_wide, _high))
HUNG += signs.HUNG

# Where a family album does not belong: a business shows pictures of its own kind (pools.py) in place of the family
# photographs, a clinic or a motel shows landscapes and a garage or a gym sports posters. Homes, prison cells and a photo
# studio keep the family photographs.
for _kind, _prompts in pools.POOLS.items():
    POOLS['family', _kind] = []
    for _i, _prompt in enumerate(_prompts):
        art('pool-%s-%02d' % (_kind, _i), _prompt, '', TALL)
        POOLS['family', _kind].append('pool-%s-%02d' % (_kind, _i))
_LANDSCAPES = ['painting-' + _name for _name in ('waterbeach-hills', 'waterfall', 'riverside', 'two-frame', 'lighthouse', 'helianthus', 'flowers-on-table')]
POOLS['family', 'medical'] = POOLS['family', 'motel'] = _LANDSCAPES
POOLS['family', 'industrial'] = ['racing-%d' % _i for _i in range(len(SPORTS['racing']))]
POOLS['family', 'gym'] = ['%s-%d' % (_sport, _i) for _sport in ('weights', 'boxing') for _i in range(len(SPORTS[_sport]))]
for (_subject, _kind), _pool in POOLS.items():
    if _kind not in rooms.KINDS:
        raise ValueError('pictures are pooled for a kind of room rooms.py has no rule for: ' + _kind)
    _unknown = [_name for _name in _pool if _name not in ART and _name not in LETTERED]
    if _unknown:
        raise ValueError('the pool of %s in a %s names pictures the catalogue has not: %s' % (_subject, _kind, ' '.join(_unknown)))


def subject(name):
    """What a picture is of where the room it hangs in may change it for another (POOLS), or None."""
    if name.startswith('family-'):
        return 'family'
    if name.startswith('certificate-'):
        return 'certificate'
    return None
