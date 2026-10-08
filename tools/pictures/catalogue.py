"""What hangs on the walls of the first person view: every picture the mod makes, and which of the game's sprites shows it.

A picture is made from a text prompt alone (generate.py), then cut to the shapes it hangs in and given its lettering
(pack.py). Nothing of the game's own art goes into one.

ART is name -> (prompt, negative, width, height). HUNG is the list of (key, picture) that pack.py writes as the mod's
index: a key is a sprite's name, or the name and group the game gives a piece, "Painting|Lighthouse". LONG is the keys
that hold only for a piece lying along several squares: the game gives some single sprites, each a part of a hoarding,
the same name. SHAPED is how many times as wide as it is high a painting that hangs along several squares is on its
wall, inside its frame: it is cut to that. LETTERED says what is written on a poster. SEEDS is the seed of each picture not made with 1993.
"""

import shows

NO_TEXT = 'text, letters, words, caption, watermark, signature, logo, frame, border'
PHOTO_NEGATIVE = NO_TEXT + ', illustration, painting, cartoon, 3d render, deformed, extra fingers, blurry, modern, smartphone'
PAINT_NEGATIVE = NO_TEXT + ', photograph, 3d render, wall, room, gallery, museum, easel'
POSTER_NEGATIVE = NO_TEXT + ', deformed, extra fingers, extra limbs, blurry, wall, room'

TALL, WIDE, SQUARE, BROAD, LONGER, STOUT = (832, 1216), (1216, 832), (1024, 1024), (1344, 768), (1536, 640), (1152, 896)

ART = {}
HUNG = []
LONG = set()
SHAPED = {}
LETTERED = {}
SEEDS = {'basketball-3': 7, 'boxing-3': 7, 'hockey-1': 7, 'hockey-2': 11, 'hockey-5': 7, 'football-4': 21, 'certificate': 7,
         'music-05': 21, 'music-07': 7, 'music-08': 7, 'screen-05': 7, 'screen-07': 11, 'film-cyberkiller': 21, 'film-cyberkiller-2': 21,
         'screen-film-future-0': 21}


def art(name, prompt, negative, size):
    if name in ART:
        raise ValueError('two pictures are called ' + name)
    ART[name] = (prompt, negative, size[0], size[1])


# Family photographs, as they hang in the houses of a small Kentucky town in 1993.
SNAP = ', 1980s colour photograph, 35mm film, kodak print, soft natural light, slight film grain, sharp focus'
STUDIO = ', 1980s studio portrait photograph, mottled blue grey backdrop, soft studio lighting, 35mm film, sharp focus'
OLD = ', 1940s black and white studio portrait photograph, silver gelatin print, soft light, sharp focus'
FAMILY = [
    ('portrait of a smiling grandmother with curly grey hair and glasses in a floral blouse' + STUDIO, TALL),
    ('portrait of a young bride in a white lace wedding dress and veil holding a bouquet' + STUDIO, TALL),
    ('portrait of a young woman with big permed brown hair and a denim jacket' + STUDIO, TALL),
    ('portrait of an old farmer in a plaid shirt and cap leaning on a wooden fence, tobacco barn behind' + SNAP, TALL),
    ('portrait of an elderly couple in their sunday best standing in front of a white clapboard church' + SNAP, TALL),
    ('portrait of a stern grandfather in a dark suit and tie' + OLD, TALL),
    ('portrait of a young soldier in a 1940s army dress uniform and cap' + OLD, TALL),
    ('portrait of a baby in a white christening gown sitting on a blanket' + STUDIO, TALL),
    ('school portrait of a ten year old boy with a bowl haircut and a striped shirt, gap toothed smile' + STUDIO, TALL),
    ('a family of four sitting on the porch steps of a wooden house in summer, parents and two children' + SNAP, WIDE),
    ('portrait of a teenage girl in a blue high school cheerleader uniform' + STUDIO, TALL),
    ('portrait of a middle aged man with a moustache in a brown suit and wide tie' + STUDIO, TALL),
    ('portrait of a young mother holding a toddler on her lap, both smiling' + STUDIO, TALL),
    ('portrait of a little girl in a yellow dress holding a kitten in a garden' + SNAP, TALL),
    ('portrait of a wedding couple, groom in a grey tuxedo and bride in white, standing under an oak tree' + SNAP, TALL),
    ('three generations of a family posed together on a sofa in a wood panelled living room at christmas' + SNAP, WIDE),
    ('portrait of a young woman in a green graduation cap and gown holding a diploma' + STUDIO, TALL),
    ('portrait of a grey haired man in a navy blazer and tie, kindly smile' + STUDIO, TALL),
    ('portrait of a golden retriever dog sitting on a lawn in front of a house' + SNAP, TALL),
    ('portrait of a high school football player in a red jersey kneeling with his helmet beside him' + SNAP, TALL),
    ('portrait of a young man in a dark blue graduation cap and gown, proud smile' + STUDIO, TALL),
    ('two young brothers in fishing vests holding up a catfish beside a lake' + SNAP, WIDE),
    ('portrait of a woman with feathered blonde hair in a red sweater' + STUDIO, TALL),
    ('a married couple and their teenage son and daughter in front of a station wagon on a driveway' + SNAP, WIDE),
]
for _i, (_prompt, _size) in enumerate(FAMILY):
    art('family-%02d' % _i, _prompt, PHOTO_NEGATIVE, _size)
for _block in range(3):
    for _i in range(8):
        # The sheet has eight seen from the south, then the same eight seen from the east.
        HUNG.append(('walls_decoration_02_%d' % (_block * 16 + _i), 'family-%02d' % (_block * 8 + _i)))
        HUNG.append(('walls_decoration_02_%d' % (_block * 16 + 8 + _i), 'family-%02d' % (_block * 8 + _i)))

# Sports posters of a teenager's bedroom, a bar and a gym. No lettering: a model spells nothing right.
ACTION = ', 1990s sports poster, dramatic action sports photography, vivid colours, sharp focus, highly detailed'
STILL = ', 1990s sports poster, studio photograph, dramatic lighting, vivid colours, sharp focus, highly detailed'
SPORTS = {
    'basketball': [
        'a basketball player in a blue jersey leaping for a slam dunk in a packed arena' + ACTION,
        'a basketball player in a red jersey dribbling low past a defender on a hardwood court' + ACTION,
        'a basketball player in a yellow jersey shooting a jump shot, crowd blurred behind' + ACTION,
        'a plain unbranded orange leather basketball resting on a polished hardwood court under a spotlight' + STILL,
        'a basketball dropping through a hoop and net seen from below against arena lights' + STILL,
        'two basketball players jumping for a rebound under the basket' + ACTION,
    ],
    'tennis': [
        'a woman tennis player in white hitting a forehand on a grass court' + ACTION,
        'a man tennis player in a headband serving on a blue hard court, ball in the air' + ACTION,
        'a woman tennis player in a pink dress stretching for a backhand on a clay court' + ACTION,
        'a wooden tennis racket and two yellow tennis balls lying on a green grass court' + STILL,
        'a tennis player sliding on red clay to reach a low ball, dust flying' + ACTION,
        'a tennis court seen from the stands on a sunny day, two players in a rally' + ACTION,
    ],
    'boxing': [
        'a heavyweight boxer in red gloves throwing a punch in the ring, sweat flying, dark arena' + ACTION,
        'two boxers trading blows in a ring under bright lights, ropes in the foreground' + ACTION,
        'a boxer in white trunks standing with gloves raised in a dark ring, spotlight from above' + ACTION,
        'a pair of worn plain unbranded red leather boxing gloves hanging from a nail on a wooden wall' + STILL,
        'a boxer hitting a heavy punch bag in an old brick gym, shafts of light' + ACTION,
        'a champion boxer with his arm raised by the referee in the ring, crowd cheering' + ACTION,
    ],
    'golf': [
        'a golfer in a white cap at the top of his swing on a green fairway, blue sky' + ACTION,
        'a woman golfer in a pink visor putting on a green beside a red flag' + ACTION,
        'a golfer in plaid trousers blasting a ball out of a sand bunker, sand spraying' + ACTION,
        'a golf ball on a tee beside the head of an iron club on trimmed grass, shallow depth of field' + STILL,
        'a golf green with a flag at sunrise, mist over rolling fairways and trees' + STILL,
        'a golfer following through after a drive, seen from behind, long fairway ahead' + ACTION,
    ],
    'baseball': [
        'a baseball batter in a blue pinstripe uniform swinging at a pitch in a stadium' + ACTION,
        'a baseball pitcher in a red cap in mid throw on the mound' + ACTION,
        'a baseball catcher in a mask and green uniform crouching behind home plate' + ACTION,
        'a wooden baseball bat and a scuffed baseball lying on red infield dirt' + STILL,
        'a baseball player sliding into base in a cloud of dust as the fielder tags' + ACTION,
        'a worn leather baseball glove holding a baseball on a wooden bench' + STILL,
    ],
    'football': [
        'an american football quarterback in a blue and orange uniform drawing back to throw' + ACTION,
        'an american football running back in a white uniform carrying the ball through tacklers' + ACTION,
        'an american football receiver in a red uniform leaping to catch a pass' + ACTION,
        'a brown leather american football lying on the white yard line of a grass field' + STILL,
        'close up of the face of an american football player behind the face mask of a plain blue helmet with no decal, the helmet filling the frame, stadium lights' + ACTION,
        'an american football kicked through yellow goal posts in a stadium at dusk' + ACTION,
    ],
    'soccer': [
        'a soccer player in a red kit striking the ball in a full stadium' + ACTION,
        'a soccer goalkeeper in green diving at full stretch to save a shot' + ACTION,
        'a soccer player in a blue and white striped kit dribbling past a sliding defender' + ACTION,
        'a black and white leather soccer ball on the penalty spot of a grass pitch, goal behind' + STILL,
        'a soccer player in a yellow kit celebrating a goal with arms wide, crowd behind' + ACTION,
        'a soccer team in green kits posed in two rows for a team photograph on the pitch' + ACTION,
    ],
    'athletics': [
        'a sprinter in a red vest bursting out of the starting blocks on a running track' + ACTION,
        'a high jumper arching backwards over the bar in a stadium' + ACTION,
        'a hurdler in blue clearing a hurdle on a red track' + ACTION,
        'a woman long jumper in mid air over the sand pit' + ACTION,
        'a javelin thrower at the moment of release in a stadium' + ACTION,
        'runners rounding the bend of a running track in a pack' + ACTION,
    ],
    'weights': [
        'a muscular weightlifter straining to lift a heavy barbell overhead on a competition platform' + ACTION,
        'a bodybuilder flexing his biceps on a stage against a dark curtain' + ACTION,
        'a woman in a leotard and leg warmers doing aerobics with small dumbbells, 1980s gym, pink and purple lights' + ACTION,
        'a heavy iron dumbbell on a rubber gym floor, chalk dust in the light' + STILL,
        'a strongman doing a bench press in an old gym, spotter behind' + ACTION,
        'a loaded barbell with iron plates on a lifting platform, dramatic red background' + STILL,
    ],
    'hockey': [
        'an ice hockey player in a blue jersey skating hard with the puck, ice spraying' + ACTION,
        'an ice hockey goalie in white pads, a mask and a plain white jersey with no crest making a save' + ACTION,
        'an ice hockey player in a plain green jersey with no crest taking a slap shot' + ACTION,
        'two crossed wooden ice hockey sticks and a black puck on scratched ice' + STILL,
        'two ice hockey players fighting for the puck against the boards' + ACTION,
        'an ice hockey player in a plain yellow and black striped jersey with no crest celebrating with his stick raised' + ACTION,
    ],
    'bowling': [
        'a bowler in a retro bowling shirt releasing a ball down a polished wooden lane' + ACTION,
        'a bowling ball smashing into ten white pins, pins flying' + ACTION,
        'a glossy blue marbled bowling ball beside white bowling pins on a lane' + STILL,
        'a smiling woman in a pink bowling shirt holding a bowling ball in a 1980s bowling alley' + ACTION,
        'a row of bowling lanes in a retro bowling alley with neon lights' + STILL,
        'a bowling team of four in matching shirts posing with their trophy in a bowling alley' + ACTION,
    ],
    'racing': [
        'a red stock car racing at speed round a banked oval track, motion blur' + ACTION,
        'two stock cars side by side, blue and yellow, on a speedway, grandstand behind' + ACTION,
        'a racing driver in a red fire suit and open face helmet standing beside his stock car in the pits' + ACTION,
        'a green and white stock car crossing the finish line under a chequered flag' + ACTION,
        'a pack of colourful stock cars bunched through a turn at a speedway' + ACTION,
        'a pit crew changing the tyres of a stock car during a pit stop' + ACTION,
    ],
    'pool': [
        'a man in a waistcoat leaning over a green pool table lining up a shot, smoky bar, lamp overhead' + ACTION,
        'a triangle of coloured pool balls racked on green felt, cue beside them' + STILL,
        'a black eight ball on the green felt of a pool table beside a corner pocket' + STILL,
        'a cue ball breaking a rack of pool balls, balls scattering, seen from table level' + ACTION,
        'a woman in a leather jacket chalking her cue beside a pool table in a dim bar' + ACTION,
        'two men playing pool in an old pool hall, hanging lamps over rows of tables' + ACTION,
    ],
    'fieldhockey': [
        'a woman field hockey player in a red kit running with the ball on her stick on a grass pitch' + ACTION,
        'two women field hockey players in blue and white challenging for the ball' + ACTION,
        'a field hockey player in a green kit striking the ball' + ACTION,
        'two crossed field hockey sticks and a white ball on green grass' + STILL,
    ],
    'hunting': [
        'a white tailed buck deer with large antlers standing in an autumn meadow at dawn, wildlife photograph, sharp focus, highly detailed',
        'a wild turkey displaying its fanned tail in a woodland clearing, wildlife photograph, sharp focus, highly detailed',
        'a hunter in a plaid jacket and orange cap with a rifle on his shoulder walking through autumn woods, 1980s outdoor magazine photograph, sharp focus',
        'a mallard duck taking off from a misty marsh at sunrise, wildlife photograph, sharp focus, highly detailed',
        'a white tailed doe and fawn in tall green grass at the edge of a forest, wildlife photograph, sharp focus, highly detailed',
        'the black silhouette of a stag with antlers against an orange sunset sky over hills',
        'a hunting dog, a pointer, standing rigid in a field of dry grass, wildlife photograph, sharp focus, highly detailed',
        'a bull elk bugling in a mountain valley in autumn, wildlife photograph, sharp focus, highly detailed',
    ],
}
for _sport, _prompts in SPORTS.items():
    for _i, _prompt in enumerate(_prompts):
        art('%s-%d' % (_sport, _i), _prompt, POSTER_NEGATIVE if _sport != 'hunting' else NO_TEXT + ', blurry, deformed', TALL)


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
OIL = ', oil painting on canvas, visible brushstrokes, rich colour, museum quality, highly detailed'
PAINTINGS = {
    "Lilly's Waterpond": ('impressionist painting of water lilies floating on a pond, reflections of willows, soft blues, greens and violets' + OIL, WIDE),
    "She's Got It": ('classical painting of a woman in a white gown reclining on a red velvet chaise longue, dark drapery behind' + OIL, BROAD),
    'Aaaaah!': ('expressionist painting of a lone figure clutching its face on a long bridge under a swirling orange and red sky, dark blue fjord' + OIL, TALL),
    'Abe Lincoln': ('formal 19th century portrait of Abraham Lincoln seated in a dark suit, sombre background' + OIL, TALL),
    'Apple Faced Man': ('surreal painting of a gentleman in a dark suit and bowler hat whose head is a large green apple, cloudy sky behind' + OIL, TALL),
    'Benjamin Franklin': ('formal 18th century portrait of Benjamin Franklin in a brown coat, long grey hair, dark background' + OIL, TALL),
    'Betty': ('1950s pin-up style painted portrait of a smiling blonde woman with curled hair looking over her bare shoulder, warm brown background' + OIL, TALL),
    'Calvinist': ('17th century Dutch portrait of a stern old man in a black hat and a wide white ruff collar, dark background, chiaroscuro' + OIL, TALL),
    'Chromatic Accident': ('abstract expressionist drip painting, dense tangled splatters and drips of black, rust orange, white and grey on a cream canvas', LONGER),
    'Configuration II': ('geometric abstract painting, thick black grid lines dividing a white canvas, rectangles of pure red, blue and yellow, flat paint', SQUARE),
    'Configuration No. 10': ('geometric abstract painting, thick black grid lines on white, a large blue rectangle, small red and yellow rectangles, flat paint', SQUARE),
    'Layout I': ('geometric abstract painting, thick black grid lines on white, a large red square, small blue and yellow rectangles, flat paint', SQUARE),
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
    'Monica Elisa': ('ink and watercolour portrait of a woman with long black hair in a red and orange patterned robe, hands folded, white paper background', TALL),
    'Mount Never Rest': ('romantic landscape painting of a towering snow capped mountain above a lake and pine forest, golden light breaking through clouds' + OIL, LONGER),
    'Night With Stars': ('post-impressionist painting of a swirling starry night sky over a sleeping village with a church spire and a dark cypress, thick impasto' + OIL, BROAD),
    'Nomad Above The Gloom': ('romantic painting of a lone man in a dark frock coat seen from behind, standing on a rocky peak above a sea of fog and distant mountains' + OIL, SQUARE),
    'Omega Department': ('painted illustration of a friendly cartoon brown bear and a badger side by side, storybook style, pale yellow background', TALL),
    'Riverside': ('impressionist painting of a river bank in spring, poplars and their reflections, pink and lilac sky' + OIL, WIDE),
    'Sir Brakefire': ('17th century portrait of a gentleman with a pointed beard in a broad black hat and lace collar, dark background, chiaroscuro' + OIL, TALL),
    'Two Frame': ('small impressionist landscape painting of a country lane between flowering hedges, lilac hills beyond' + OIL, WIDE),
    'Violetta': ('impressionist painting of a woman with a parasol in a garden of pink roses, green lawn, sunlight' + OIL, WIDE),
    'Waterbeach Hills': ('landscape painting of rolling green hills and meadows under a blue sky with white clouds' + OIL, WIDE),
    'Waterfall': ('landscape painting of a waterfall dropping through autumn woods into a rocky pool' + OIL, WIDE),
    'Wide': ('classical landscape painting of a river valley with ancient trees, cattle at a ford and distant blue hills, golden evening light' + OIL, STOUT),
    'Dr. Oids': ('1980s arcade video game cover art, pixel art of a yellow blocky smiling creature chased by a pink blocky ghost through a neon green maze, dark background', TALL),
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

art('painting-field-pink', 'colour field abstract painting, two large soft edged hazy rectangles, magenta above warm yellow, on an orange ground, matte paint on canvas',
    NO_TEXT + ', wall, room, objects', TALL)
art('painting-field-purple', 'colour field abstract painting, two large soft edged hazy rectangles, deep violet above midnight blue, on a dark plum ground, matte paint on canvas',
    NO_TEXT + ', wall, room, objects', TALL)
HUNG.append(('Paintings|Pink and Yelllow', 'painting-field-pink'))
HUNG.append(('Paintings|Purple and Blue', 'painting-field-purple'))
art('painting-sitter', 'dark old master portrait of a pale woman in a blue silk dress, sombre background, cracked varnish' + OIL, PAINT_NEGATIVE, TALL)
HUNG.append(('location_entertainment_gallery_01_72', 'painting-sitter'))
HUNG.append(('location_entertainment_gallery_01_73', 'painting-sitter'))

# Boards.
art('board-cork', 'a cork notice board seen straight on filling the whole frame, covered in pinned paper notes, memos, index cards, a calendar page and photographs, coloured push pins, office, photograph, sharp focus, even light',
    'watermark, signature, wall, room, perspective, blurry', WIDE)
art('board-white', 'a white dry erase board seen straight on filling the whole frame, faint smudges, a hand drawn bar chart and arrows in blue and red marker, office, photograph, even light, sharp focus',
    'watermark, signature, wall, room, perspective, blurry, people', BROAD)
art('board-darts', 'an open wooden pub dartboard cabinet seen straight on, a bristle dartboard in the middle with three darts in it, small chalk scoreboards inside both open doors, dark wood, photograph, even light, sharp focus',
    'watermark, signature, perspective, blurry, people', BROAD)
for _group in ('Big Cork', 'Cork'):
    HUNG.append(('Noteboard|' + _group, 'board-cork'))
for _group in ('Office', 'Wide Office'):
    HUNG.append(('Whiteboard|' + _group, 'board-white'))
HUNG.append(('Dartboard|', 'board-darts'))
art('certificate', 'an empty unprinted sheet of cream parchment paper seen straight on filling the whole frame, an ornate engraved guilloche border round its edge, a gold foil seal at the bottom, the middle of the sheet empty and plain, flat scan, even light',
    'text, letters, words, handwriting, watermark, perspective, wall, frame, hands, blurry', WIDE)
HUNG.append(('Certificate|Doctor Degree', 'certificate-doctor'))
HUNG.append(('Certificate|Surgeon Degree', 'certificate-surgeon'))
LETTERED['certificate-doctor'] = ('certificate', 'diploma', 'Doctor of Medicine', 'Kentucky State School of Medicine')
LETTERED['certificate-surgeon'] = ('certificate', 'diploma', 'Fellow in Surgery', 'Kentucky College of Surgeons')

# Posters of the bands and singers of the record shop and of bedroom walls: eighteen acts, each as a large sheet, a wide
# one and a small one. The art is square, and pack.py cuts each shape out of it.
GIG = ', 1980s concert poster photograph, dramatic stage lighting, sharp focus, highly detailed'
MUSIC = [
    'a four piece glam rock band, two women and two men with big teased hair and leather jackets, posed against a dark red curtain' + GIG,
    'a rock and roll singer with slick dark hair in a red waistcoat and white shirt leaning against a brick wall, black and white background' + GIG,
    'four young men in matching slim grey suits and narrow ties standing in a row, 1960s beat group, black and white photograph, sharp focus',
    'a cheerful 1960s pop group of five in bright paisley shirts on a pink and rainbow striped background' + GIG,
    'a 1970s hard rock band of four long haired men on stage in front of red and yellow flames and amplifiers' + GIG,
    'a rock singer with shoulder length brown hair and a headband singing into a microphone on a dark stage, a black shirt, moody light' + GIG,
    'a country rock guitarist with long hair, sunglasses and a fringed jacket playing an electric guitar against an orange desert sunset' + GIG,
    'a pop singer with wavy shoulder length blond hair in a black leather jacket and white t-shirt against a deep blue background' + GIG,
    'three young men in white suits and blue caps posing back to back, 1990s rhythm and blues vocal group, cream background' + GIG,
    'a red haired woman singer in a flowing white dress, soft focus, cream and rose background, 1970s folk album cover photograph, sharp focus',
    'an elderly crooner in a black tuxedo and bow tie holding a vintage silver microphone, black background, warm spotlight' + GIG,
    'a pop singer with long curly red hair laughing in a denim jacket, pale teal background, 1980s pop album cover photograph, sharp focus',
    'a country singer in a black hat and black shirt sitting with an acoustic guitar, black and white photograph, old barn wall behind, sharp focus',
    'a young woman singer with short brown hair in a white t-shirt and jeans sitting cross legged, pastel pink background, 1980s pop album cover photograph, sharp focus',
    'a disco singer with a big afro in a sparkling purple sequin dress under a mirror ball, purple light and glitter' + GIG,
    'a smiling country singer in a white cowboy hat and embroidered western shirt holding an acoustic guitar, golden sepia background' + GIG,
    'a folk singer in a flat cap, white shirt and red tie holding an acoustic guitar, cream background, 1970s album cover photograph, sharp focus',
    'black and white photograph of an iron bridge over a river in a city, with one single purple balloon floating above it, indie album cover, sharp focus',
]
# The names the game's own posters carry, where they can be read off them. The rest hang with no lettering.
ACTS = {1: 'FRIDAY NIGHT', 2: 'THE 594s', 3: 'THE BOJANGLES', 4: 'STEAMSHIP HURRICANE', 6: 'WILL BILL GROOVY', 7: 'SOUNDS LIKE AUSTIN PARKER!',
        8: 'SOFT ONEZ', 9: 'MARIA', 10: 'TIME FOR LOVE', 11: 'TONI', 14: 'YVETTE', 15: 'HANK GILMAN'}
for _i, _prompt in enumerate(MUSIC):
    art('music-%02d' % _i, _prompt, POSTER_NEGATIVE, SQUARE)
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
FILM = ', movie poster key art, cinematic lighting, dramatic composition, highly detailed'
FILMS = {
    'Cyberkiller': ('Cyberkiller', 'close up of a grim square jawed soldier, a riveted steel plate over the left half of his face with a round blue camera lens where the eye was, dark teal circuit board background' + FILM,
                    'CYBERKILLER', 'IN THEATERS NOW'),
    'Cyberkiller 2': ('Cyberkiller 2', 'a muscular bearded action hero back to back with a soldier who has a riveted steel plate over half his face and a round blue camera lens for an eye, teal digital grid background' + FILM,
                      'CYBERKILLER 2', 'IN THEATERS NOW'),
    'Operation Fort Knox': ('Operation Fort Knox', 'a suave blond secret agent in a black tuxedo holding a pistol, stacks of gold bars glowing behind him, golden light' + FILM,
                            'OPERATION FORT KNOX', 'IN THEATERS NOW'),
    'PAWS': ('PAWS', 'animated family film illustration of a raccoon, a brown bear cub, a squirrel and a badger adventuring together through a sunny green forest, bright colours, storybook cartoon style',
             'PAWS', 'IN THEATERS NOW'),
    'Dog Goblin 4': ('Dog Goblin 4', 'horror film art, a huge cracked human skull looming out of darkness, pale moonlight, mist, bare trees, black and steel blue' + FILM,
                     'DOG GOBLIN 4', 'IN THEATERS NOW'),
    'Pleistocene Land': ('Pleistocene Land', 'a woolly mammoth with great curved tusks walking through a snowy pine forest in a blizzard, pale winter light' + FILM,
                         'PLEISTOCENE LAND', 'IN THEATERS NOW'),
    'Dr Oids': ('Dr Oids', '1980s arcade video game art, pixel art of a yellow blocky smiling creature and a pink blocky ghost on a dark blue neon grid, glowing green outlines',
                'DR. OIDS', 'NOW SHOWING'),
    'The Forgotten Element': ('The Forgotten Element', 'adventure film art, a rugged man in a fedora and leather jacket beside a dark haired woman, a vintage propeller plane crossing a huge golden sunset behind them' + FILM,
                              'THE FORGOTTEN ELEMENT', 'COMING SOON'),
    'Movie': ('Movie', 'romantic drama film art, a man and a woman embracing on a rainy city street at night under a street lamp, warm and blue tones' + FILM,
              'ONE NIGHT IN LOUISVILLE', 'IN THEATERS NOW'),
}
for _group, (_name, _prompt, _title, _line) in FILMS.items():
    _key = 'film-' + _name.lower().replace(' ', '-')
    art(_key, _prompt, POSTER_NEGATIVE, SQUARE)
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
ON_AIR = ', still frame from a 1990s american television broadcast, analog video, NTSC colour, slightly soft, studio lighting'
SCREEN_NEGATIVE = NO_TEXT + ', deformed, extra fingers, extra limbs, modern, smartphone, television set, screen bezel, monitor'
PROGRAMMES = [
    'a male news anchor in a grey suit and red tie sitting at a news desk, blue studio backdrop' + ON_AIR,
    'a weather forecaster in a brown suit standing in front of a weather map with sun and cloud symbols' + ON_AIR,
    'a cheerful woman in an apron stirring a pot in a television studio kitchen, cooking show' + ON_AIR,
    'a bearded man in a plaid shirt and safety glasses sanding a wooden chair in a workshop, woodworking show' + ON_AIR,
    'a man in a fishing vest and cap holding up a largemouth bass in a small boat on a lake, fishing show' + ON_AIR,
    'a baseball pitcher in a plain white uniform throwing from the mound, green outfield grass behind him, televised baseball game' + ON_AIR,
    'a glamorous woman with big blonde hair and a man in a tuxedo arguing in an opulent living room, daytime soap opera' + ON_AIR,
    'saturday morning cartoon, a small blue cartoon robot and a green cartoon frog building a rocket in a garage, 1980s cel animation, flat bright colours, thick outlines',
    'a white tailed deer standing in a misty forest clearing at dawn, nature documentary' + ON_AIR,
    'a game show host in a sparkly blue jacket holding a microphone on a brightly lit stage with coloured lights, game show' + ON_AIR,
    'a cowboy on horseback riding through a desert canyon, classic western film, technicolor' + ON_AIR,
    'an aerobics instructor in a pink leotard and leg warmers leading a workout class, 1980s exercise show' + ON_AIR,
]
SCREENS = ['screen-%02d' % _i for _i in range(len(PROGRAMMES))]
for _name, _prompt in zip(SCREENS, PROGRAMMES):
    art(_name, _prompt, SCREEN_NEGATIVE, STOUT)

# The shots of each show a set can tell it is showing (shows.py). SHOTS is each packed picture's name with the art it is
# cut from: screen-<show>-<n>, and the dozen above again as the show of no one programme, screen-any-<n>.
# A shot is packed at 640 by 480: it is made at three quarters of the pixels a picture for a wall is.
TUBE = (1024, 768)
SHOTS = {'screen-%s-%d' % (shows.ANY, _i): _name for _i, _name in enumerate(SCREENS)}
for _show, _shots in shows.SHOWS.items():
    for _i, _prompt in enumerate(_shots):
        _name = 'screen-%s-%d' % (_show, _i)
        art(_name, _prompt, SCREEN_NEGATIVE, TUBE)
        SHOTS[_name] = _name
