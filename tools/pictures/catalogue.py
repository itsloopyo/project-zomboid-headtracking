"""What hangs on the walls of the first person view: every picture the mod makes, and which of the game's sprites shows it.

A picture is made from a text prompt alone (generate.py), then cut to the shapes it hangs in and given its lettering
(pack.py). Nothing of the game's own art goes into one.

ART is name -> (prompt, width, height). HUNG is the list of (key, picture) that pack.py writes as the mod's
index: a key is a sprite's name, or the name and group the game gives a piece, "Painting|Lighthouse". POOLS is
(subject, room kind) -> pictures: a picture of that subject (subject()) hung in a room of that kind (rooms.py) shows one of
these instead, chosen by Decor.inRoom. LONG is the keys
that hold only for a piece lying along several squares: the game gives some single sprites, each a part of a hoarding,
the same name. SHAPED is how many times as wide as it is high a painting that hangs along several squares is on its
wall, inside its frame: it is cut to that. LETTERED says what is written on a poster. SEEDS is the candidate picked for each picture, as
(model, seed): generate.py makes several with each model, and pack.py reads them from <folder>/<model>/<name>/<seed>.png.
"""

import pools
import rooms
import shows
import signs

TALL, WIDE, SQUARE, BROAD, LONGER, STOUT = (832, 1216), (1216, 832), (1024, 1024), (1344, 768), (1536, 640), (1152, 896)

ART = {}
HUNG = []
POOLS = {}
LONG = set()
SHAPED = {}
LETTERED = {}
SEEDS = {}


def art(name, prompt, size):
    if name in ART:
        raise ValueError('two pictures are called ' + name)
    ART[name] = (prompt, size[0], size[1])


# Family photographs, as they hang in the houses of a small Kentucky town in 1993.
SNAP = (' A snapshot on 35mm colour negative film from the 1980s, faded colours with soft contrast, fine grain and'
        ' ordinary amateur composition. The picture fills the whole image edge to edge.')
STUDIO = (' A 1980s department store studio portrait on colour film, mottled blue grey backdrop, soft flash, slightly '
          'faded colours, fine grain and natural skin with fine pores. The picture fills the whole image edge to edge.')
BABY = (' A 1980s department store studio portrait on colour film, mottled blue grey backdrop, soft flash, slightly'
        ' faded colours and fine grain. The picture fills the whole image edge to edge.')
OLD = (' Taken in black and white in the 1940s, only shades of grey from black to white, soft light and fine film grain,'
       ' the sitter and the backdrop filling the whole frame.')
PORTRAIT = (' Taken on colour film in the 1980s against a mottled blue grey backdrop, soft flash, slightly faded colours,'
            ' fine grain and natural skin, the sitter and the backdrop filling the whole frame.')
FAMILY = [
    ('A close head and shoulders picture of a cheerful plump white farm wife of about forty eight from rural Kentucky '
     'on her front porch, her face and shoulders filling most of the picture, a full round face with plump smooth '
     'cheeks and a double chin, rosy cheeks, short permed brown hair with a little grey at the temples and big square '
     'glasses with chunky light brown plastic frames, the collar of a floral blouse along the bottom edge, smiling at '
     'the viewer, the white porch posts and potted geraniums soft behind her.' + SNAP, TALL),
    ('A head and shoulders photograph of a smiling white bride of twenty five from rural Kentucky, a grown woman with '
     'defined cheekbones and full make up, lipstick and mascara, her brown hair in big 1980s curls under a lace veil, '
     'in a white lace wedding dress with a high lace neck up to her chin and puffed sleeves.' + PORTRAIT, TALL),
    ('Head and shoulders portrait of a young woman of about twenty with big permed brown hair, in a denim jacket over a white'
     ' blouse.' + STUDIO, TALL),
    ('Portrait of an old farmer in a plaid shirt and a plain cap, his forearms resting on the top rail of a wooden fence, an'
     ' old black tobacco barn behind him.' + SNAP, TALL),
    ('An elderly couple in their Sunday best standing side by side in front of a white clapboard church, seen from a few'
     ' metres away, her hand on his arm.' + SNAP, TALL),
    ('A head and shoulders photograph of a serious white man of about fifty eight with thick dark brown hair combed '
     'back, just a little grey above his ears, a smooth full face and a firm jaw, in a dark suit and tie, the mottled '
     'grey backdrop behind him running off all four edges of the picture.' + OLD, TALL),
    ('A snapshot of a young white American soldier of about twenty, bareheaded with a short army haircut, '
     'clean shaven, a clear sunburnt face with fine pores and a sheen of sweat, in a plain pale grey '
     't-shirt and army fatigue trousers, standing '
     'in front of a chest high wall of smooth tightly packed sandbags under a hot sky and smiling at the '
     'viewer, framed from the waist up, his arms folded across his chest, palm trees and a tin roofed hut '
     'soft behind him.' + (' Taken in black and white on 35mm film in the 1960s, a true monochrome picture in which every tone, his '
               'shirt and trousers included, is a neutral grey between black and white, fine film grain and ordinary '
               'amateur composition, the picture filling the whole image edge to edge.'), TALL),
    ('A white baby of about six months with soft smooth pink baby skin and wisps of fair hair, in a white christening '
     'gown, sitting on a white blanket and smiling straight out of the frame towards the viewer.' + BABY, TALL),
    ('A white ten year old boy from rural Kentucky with a bowl haircut, straight brown hair cut in a level fringe '
     'across his forehead, in a red and white horizontally striped t-shirt and blue jeans, sitting on a stack of hay '
     'bales in a farmyard on a summer afternoon with a slight shy closed mouth smile, framed from the knees up, a red '
     'barn and green fields behind him.' + SNAP, TALL),
    ('A white rural Kentucky family of four on the wooden porch steps of a white farmhouse in summer, all smiling '
     'towards the viewer, seen from about six metres away with the whole of the steps in view down to the grass so '
     'every foot is in the picture. The father of about forty, with short brown hair, in a plain blue work shirt and '
     'jeans, and the mother of about thirty eight, with permed brown hair, in a yellow blouse, sit side by side on the '
     'top step, each with a hand resting on the shoulder of the child below. On the step below them sit a girl of ten '
     'with long blonde hair in a pink t-shirt and a boy of eight with a brown bowl haircut in a green t-shirt, both '
     'with their hands in their laps.' + SNAP, WIDE),
    ('Head and shoulders portrait of an American teenage girl with feathered 1980s hair in a plain blue high school'
     ' cheerleader sweater with a white stripe round its V neck over a white turtleneck.' + STUDIO, TALL),
    ('Head and shoulders portrait of a middle aged man with a moustache in a brown suit and a wide tie.' + STUDIO, TALL),
    ('A white young mother of about twenty five from rural Kentucky with permed brown hair, in a pale blue blouse and '
     'jeans, sitting on a wooden porch swing on the front porch of a white farmhouse with her toddler daughter of about'
     ' two in a red summer dress on her lap, both laughing, her arms around the child, seen from a few metres away on a'
     ' summer evening, potted geraniums on the porch boards.' + SNAP, TALL),
    ('A little white girl of six with a round child\'s face and soft round cheeks, her eyes open and looking at the '
     'viewer, her blonde hair in two plaits, sitting cross legged on the lawn in front of a white wooden farmhouse in '
     'autumn, in blue jeans and a thick red sweater, a sleeping grey striped tabby cat with a white chin curled up in '
     'her lap with its head resting on her knee, one of her hands stroking its back, an ordinary closed mouth smile.' + SNAP, TALL),
    ('A white American couple on their wedding day in 1985 standing side by side under an oak tree beside a small '
     'country church, both smiling towards the viewer, seen from a few metres away: the groom of about twenty six with a '
     'moustache in a grey tuxedo with a ruffled shirt, and the bride of about twenty four with big permed brown hair in'
     ' a 1980s white church wedding dress with a high lace neck, long puffed sleeves and a full skirt.' + SNAP, TALL),
    ('A family at Christmas sitting together on a sofa in a wood panelled living room, a grandmother with grey hair, a'
     ' father with a moustache, a mother with permed hair and a small boy, a decorated tree beside them, seen from'
     ' across the room.' + SNAP, WIDE),
    ('A head and shoulders photograph of a white woman of eighteen with a grown woman\'s face, defined cheekbones and a'
     ' slim jaw, light lipstick and mascara, her permed brown hair styled to her shoulders, in a dark green graduation '
     'gown of dull wool crepe and a dark green felt mortarboard cap, framed close so only the collar of the gown shows '
     'at the bottom.' + PORTRAIT, TALL),
    ('Head and shoulders portrait of a grey haired man in a navy blazer and tie with a kindly smile.' + STUDIO, TALL),
    ('A golden retriever sitting on a lawn in front of a brick house on a summer afternoon.' + SNAP, TALL),
    ('A big heavy set white man of about twenty four, built like a college football lineman, a barrel chest, wide '
     'heavy shoulders, a short thick neck as wide as his head, a full fleshy face with a broad square jaw and a '
     'thick even layer of dark stubble over his cheeks, jaw, chin and upper lip alike, short brown hair, in a plain '
     'grey hooded sweatshirt stretched tight over his chest, standing in a back yard in front of a tall row of dark '
     'green trees on a summer evening, facing the viewer and grinning, framed from the waist up so his wide '
     'shoulders fill the picture from side to side.' + SNAP, TALL),
    ('A head and shoulders photograph of a white college graduate of twenty two with a man\'s face: a strong grown up '
     'jaw, a faint shadow of stubble and a short neat haircut, broad shoulders, a proud smile, in a dark blue '
     'graduation gown of dull wool crepe and a dark blue felt mortarboard cap.' + PORTRAIT, TALL),
    ('Two white brothers of about ten and twelve with brown hair, in t-shirts and fishing vests, standing side by side '
     'on a grassy lake shore facing the viewer and smiling, seen from the front from a few metres away, the lake behind'
     ' them. On the grass in front of their feet sits a closed plain green plastic tackle box, and one fishing rod lies'
     ' almost flat beside it, its cork handle resting across the lid of the box and its long thin tip lying out along '
     'the grass.' + SNAP, WIDE),
    ('A head and shoulders photograph of a white woman of about thirty with a young, smooth full face, bright eyes and '
     'light natural make up, thick glossy feathered honey blonde hair with lots of body, in a red sweater, the mottled '
     'backdrop behind her running off all four edges of the picture.' + PORTRAIT, TALL),
    ('A white Kentucky family of four standing side by side in one straight line on the mown green grass of their '
     'front lawn, all four the same distance from the camera with a small gap between each of them, all facing the '
     'viewer, seen from a few metres away, the smooth green lawn running out of the bottom of the picture. Behind them'
     ' a long wood panelled station wagon is parked side on along the edge of the lawn, its front pointing to the '
     'left. From left to right: a father of about forty five with a moustache in a plain short sleeved plaid shirt; '
     'a mother of about forty with permed brown hair in a blue blouse; a son of fifteen, nearly as tall as his '
     'mother, lanky, with shaggy brown hair over his ears, in a white t-shirt; and a daughter of nine, a head shorter'
     ' than her brother, with loose blonde hair, in a smooth plain pink cotton sweatshirt, the same soft pink all '
     'over. Leafy trees behind the car.' + SNAP, WIDE),
]
for _i, (_prompt, _size) in enumerate(FAMILY):
    art('family-%02d' % _i, _prompt, _size)
for _block in range(3):
    for _i in range(8):
        # The sheet has eight seen from the south, then the same eight seen from the east.
        HUNG.append(('walls_decoration_02_%d' % (_block * 16 + _i), 'family-%02d' % (_block * 8 + _i)))
        HUNG.append(('walls_decoration_02_%d' % (_block * 16 + 8 + _i), 'family-%02d' % (_block * 8 + _i)))

# Sports posters of a teenager's bedroom, a bar and a gym. No lettering: a poster's kit is plain and unbranded.
ACTION = (' An early 1990s press photograph taken with a long lens, the picture filling the whole image edge to edge,'
          ' a fine halftone texture and slightly muted colours.')
STILL = (' An early 1990s plain still life photograph, the picture filling the whole image edge to edge, a fine'
         ' halftone texture and slightly muted colours.')
WILD = (' A 1990s outdoor magazine wildlife photograph on colour film, natural colours, fine grain. The picture fills'
        ' the whole image edge to edge.')
SPORTS = {
    'basketball': [
        'An old outdoor basketball hoop on a single steel pole at the edge of a gravel farmyard beside a weathered red '
        'wooden barn at sunset, seen straight on from in front a long way off so the whole pole and board are in the '
        'picture: a plain white painted plywood backboard of one colour, the orange rim fixed to the middle of its '
        'bottom edge with a short net of hanging steel chain links, the orange evening sky and dark trees behind, '
        'everything sharp from near to far, the colours pale, washed out and faded like an old print.' + STILL,
        'A grown man dribbling a basketball in an empty high school gym, seen from the side and framed from the waist '
        'up, crouched forward with his eyes up, his right hand pressing down on the ball at the height of his hip, the '
        'bare wall of the gym out of focus behind. He wears a solid forest green cotton tank top, one colour all over '
        'with nothing on it, and the ball is a plain orange rubber basketball with only its thin black seams.' + ACTION,
        'Seen from far back at the other end of a small old high school gym, one grown man of about thirty in a plain '
        'white cotton t-shirt and grey sweatpants standing alone at the free throw line, a small figure, a basketball '
        'held at his chest as he looks up at the hoop on the bare painted cinder block end wall, late afternoon light '
        'from high windows across the boards, everything sharp from near to far, the colours pale, washed out and '
        'faded like an old print.' + ACTION,
        'An empty old high school gym in the late afternoon, seen from the floor near one sideline, long shafts of '
        'light from high windows lying across the polished hardwood, and one worn orange basketball resting on the '
        'floor in the middle distance, small in the picture and half in shadow.' + STILL,
        'A basketball hoop on an old white painted wooden backboard fixed high on the end wall of an empty old high '
        'school gym, seen straight on from the middle of the court at standing height, the orange rim with a white '
        'cord net fixed to the middle of the bottom edge of the board, caged round lamps hanging from the dark steel '
        'roof beams above it and the polished wooden floor below, everything sharp, the colours pale, washed out '
        'and faded like an old print.' + STILL,
        'A low steel ball cart of the 1980s holding two rows of worn orange basketballs, each rubbed smooth with only '
        'its thin black seams, standing on the varnished maple floor at the edge of an empty old high school gym, late '
        'afternoon light from high windows lying across the boards, folded wooden bleachers behind out of focus.' + STILL,
    ],
    'tennis': [
        'The net of an empty grass tennis court at dusk, seen low from the grass beside one net post, the white net '
        'stretching away across the picture, the whole court living lawn grass, worn thin and brown in patches and '
        'uneven under the net, one fuzzy yellow ball resting in the grass against the bottom of the net a little way '
        'along from the post, long shadows over the grass, hedges and trees beyond.' + STILL,
        'A white American man tossing the ball up to serve, seen from a low angle against a clear pale sky and framed '
        'from the chest up, his left arm raised high and the yellow ball rising above his open hand, his face turned up'
        ' towards it, his right arm lowered and out of the picture. He wears a white cotton shirt with a collar and a '
        'white towelling headband.' + ACTION,
        'One green net post of a red clay tennis court at dusk, seen low and close from the clay beside it, the dark '
        'net hanging from the top of the post and running away across the picture to the right, its white top band '
        'sagging gently, the smooth red clay under it catching the last warm light, the rest of the court and the '
        'dark trees beyond soft and blurred under a pale orange sky.' + STILL,
        'A forgotten fuzzy yellow tennis ball lying half buried in a drift of dry brown autumn leaves at the foot of a '
        'chain link fence, seen from standing height a couple of metres away, leaves curled over the top of the ball '
        'so only a yellow crescent of it shows, plain grey overcast light, a faded green court beyond the '
        'fence.' + STILL,
        'An empty old green asphalt tennis court in a small town park on an overcast summer evening, seen '
        'from standing height beyond one corner of the court, the faded white lines and the low sagging net '
        'running across it, a chain link fence and leafy trees beyond, everything sharp from near to far, '
        'the colours pale, washed out and faded like an old print.' + STILL,
        'Seen from far off through a tall chain link fence, one grey haired man of about fifty in a pale '
        'blue shirt and white shorts stands alone at the far end of an empty green asphalt court in a small '
        'town park on a hot summer afternoon, a small figure about to serve, a plain wooden racket in his '
        'hand, leafy green summer trees behind him.' + ACTION,
    ],
    'boxing': [
        'The corner of an empty boxing ring under one hanging lamp, a red padded corner post with three red ropes '
        'running from it, a plain wooden stool and a dented tin bucket with a white towel over its rim standing on the '
        'pale canvas in the corner, everything beyond the ropes pure black darkness.' + STILL,
        'A boxing ring under bright hanging lights in a dark empty hall, seen from a few rows back among empty folding '
        'chairs, the square of pale bare canvas lit bright, three plain white ropes along each side, padded corner posts'
        ' in plain red and plain blue, a thin haze of smoke in the light above the ring, the rows of empty chairs round '
        'it fading into darkness.' + STILL,
        'A row of four black leather heavy punch bags hanging on chains from a ceiling beam in an old brick gym, each '
        'one plain scuffed black leather patched with strips of grey tape, shafts of window light falling across them '
        'and onto the worn wooden floorboards, the room quiet and empty.' + STILL,
        'A tall stretch of bare weathered plank wall in an old gym seen from four metres away, worn wooden floorboards '
        'along the bottom of the picture, and hanging from one nail in the upper middle of the wall a pair of worn old '
        'red leather boxing gloves on their long white laces, small in the picture. The gloves hang with their palm '
        'sides towards the viewer, so each shows its padded thumb and the white laces criss crossing up the inside of '
        'its wrist, and nothing else on them.' + STILL,
        'A boxer in a grey sweatshirt hitting a black heavy punch bag in an old brick gym, his bare fists bound in '
        'white cloth hand wraps, shafts of window light, seen from the side.' + ACTION,
        'An old boxing gym in the afternoon, a raised boxing ring standing square in the middle of the picture, seen '
        'straight on from across the room at head height, one padded post standing at each of its four corners, three '
        'plain white ropes along its front and pale canvas, a long wooden bench against the bare brick wall behind it,'
        ' tall dusty windows letting long shafts of light across the floorboards, the room quiet and still.' + STILL,
    ],
    'golf': [
        'A putting green in long evening light seen from its edge a few paces off at standing height, a thin white '
        'flagstick standing upright in the hole with a small plain yellow cloth flag of one colour hanging from its '
        'top, the whole flagstick and flag in the picture, one white golf ball resting on the close mown grass a short way '
        'from the hole, dark trees behind, everything sharp from near to far, the colours pale, washed out and faded '
        'like an old print.' + STILL,
        'A wide sweep of rolling fairway on a summer afternoon seen from a hill, curving down to a green with a plain '
        'red flag on a thin pole, white sand bunkers beside the green and a small still pond in front of it, tall trees '
        'all round, the whole course quiet and empty.' + STILL,
        'A sand bunker beside a putting green on a sunny afternoon, seen low from the edge of it, the pale sand raked '
        'smooth in neat lines, one white golf ball sitting in the sand, a wooden bunker rake lying across the grass at '
        'its edge, the green and a plain red flag soft behind.' + STILL,
        'A white golf ball sitting on a wooden tee in trimmed grass, close up, a green fairway blurred behind.' + STILL,
        'A golf green with a single flag at sunrise, mist over rolling fairways and trees.' + STILL,
        'One white golf ball resting in close mown fairway grass on a summer morning, seen low and close, '
        'the green grass round it beaded with tiny drops of morning dew catching the light, the tree lined '
        'fairway behind it fading into soft early morning mist.' + STILL,
    ],
    'baseball': [
        'A baseball batter taking a hard practice swing alone on the grass in front of the dugout steps, seen from the '
        'side from a few paces away, nothing in the picture but him, the grass and the dugout and the empty green seats'
        ' blurred behind. He wears a blue pinstripe uniform with a blank front, a dark blue batting helmet one colour '
        'all over and all black leather cleats, and swings a bare wooden bat showing only its wood grain from knob to '
        'end.' + ACTION,
        'The empty dugout of an old small town ball field on a summer afternoon, seen from one end of it, '
        'a long worn wooden bench along the back wall of grey painted cinder block, one worn brown leather '
        'baseball glove lying on the bench palm up with its four long padded fingers and its thumb spread '
        'open, and a dented galvanised bucket heaped full to the brim with scuffed white baseballs '
        'standing on the floor at the foot of the bench, the shade of the dugout roof, and beyond the open '
        'front the bright green grass and brown dirt of the diamond in the sun.' + STILL,
        'Seen low and close at the batting end of a baseball diamond: set flush into the red dirt, a flat five sided '
        'slab of white rubber, level with the ground and thin as a doormat, its point towards the viewer and its '
        'white top dusted with red dirt, a white chalk line running off either side of it, and beyond it the empty '
        'brown infield, a low hump of brown dirt in the middle and the green outfield under a soft evening '
        'sky.' + STILL,
        'A wooden baseball bat and a scuffed white baseball lying on red infield dirt.' + STILL,
        'The empty wooden bleachers of a small town ball field on a summer evening, seen from the end of one row so '
        'the long grey weathered plank seats run away across the picture, and beyond them the green outfield grass '
        'and a plain dark green board fence soft and blurred, tall lights on wooden poles just coming on against a '
        'pink sky.' + STILL,
        'An old wooden baseball bat leaning against the chain link backstop fence of a small town ball field on a '
        'summer evening, seen from standing height a few paces off, the bat plain pale ash showing only its wood '
        'grain from knob to end, the red dirt of the infield and the green outfield beyond the fence, everything '
        'sharp from near to far, the colours pale, washed out and faded like an old print.' + STILL,
    ],
    'football': [
        'An empty high school American football field at night in thick fog, seen from behind one end zone, the '
        'floodlights at the far end glowing through the fog, the near goal post a black shape against the bright haze, '
        'the grass and white yard lines fading away into the fog.' + ACTION,
        'A tall yellow American football goal post standing against a glowing orange dusk sky, seen from below on the '
        'grass behind it, the thick padded base, the crossbar and the two tall uprights reaching up out of the picture,'
        ' a strip of short green grass along the bottom edge, dark treetops low on the horizon.' + STILL,
        'A pair of old black leather high top American football cleats laced up high over the ankle, each '
        'upper one smooth unbroken piece of plain black leather on both sides, short muddy studs, standing '
        'side by side on a weathered grey wooden bench at dusk, seen low and close from the end of the '
        'bench, creased with wear, their long black laces hanging loose over the edge of the bench, an '
        'open grass field and dark trees soft and blurred behind.' + STILL,
        'An old worn American football of plain brown pebbled leather with only its white laces and its seams, lying on'
        ' short green grass, seen low and close, the field soft and blurred behind.' + STILL,
        'A yellow penalty flag, a plain square of yellow cloth knotted round a small weight, lying on short green grass'
        ' across a white chalk yard line at dusk, seen low and close, the floodlights of a small high school field '
        'blurred to soft glowing discs behind.' + STILL,
        'A close view looking straight down from standing height at wet churned dark mud after a game in the rain, '
        'the flat mud filling the whole picture out to all four edges, deep stud prints and torn '
        'clods of green grass pressed into it, small puddles shining grey.' + STILL,
    ],
    'soccer': [
        'A lone soccer player seen from far off on a misty muddy training field at dusk, small in the picture, swinging'
        ' his leg to kick a ball, a dark figure against the pale grey mist, bare trees behind.' + ACTION,
        'A close view of the white cord net of a soccer goal on a misty morning, the square mesh filling the whole '
        'picture, every knot beaded with dew, the grey mist and dark green grass beyond it soft and blurred.' + STILL,
        'A short thin white soccer corner flag pole standing in the grass at the edge of an empty soccer '
        'field, a small plain red cloth flag on its top lifting in the breeze, seen low and close against a '
        'golden summer evening sky, the grass round its foot and the field behind soft and blurred.' + STILL,
        'A black and white leather soccer ball on the penalty spot of a grass pitch, the goal behind.' + STILL,
        'A black and white soccer ball resting in the back of a goal net on a muddy pitch at dusk, nobody there.' + ACTION,
        'A small white soccer goal standing alone on a grass field at a small high school at dusk, seen from in front a'
        ' little way off, its net hanging still, tall wooden floodlight poles lit, low grey aluminium bleachers along '
        'one side, woods behind.' + STILL,
    ],
    'athletics': [
        'A grown man of about twenty five sprinting flat out down a red running track, seen exactly side on from a '
        'long way off so his whole body is in the picture with track above and below him, in a solid red cotton vest '
        'and solid red shorts, each one colour all over, and plain dark running shoes, the white lane lines running '
        'across the picture under him and a green infield behind, the colours pale, washed out and faded like an '
        'old print.' + ACTION,
        'A high jump set up on the grass of an empty school field on a summer evening, seen from the side, a thick deep'
        ' blue foam landing mat, plain one colour, and above the front of it a thin plain white bar resting across two '
        'tall upright steel stands, a pale evening sky behind and a line of trees along the bottom.' + STILL,
        'A plain silver aluminium relay baton, a short smooth hollow tube, lying across a white lane line on an empty '
        'red running track in the evening, seen from standing height looking down at a slant, its long shadow on the '
        'grainy red surface, the track running on behind it, flat natural light, everything sharp, the colours pale, '
        'washed out and faded like an old print.' + STILL,
        'A heavy dark grey iron shot for the shot put, a round metal ball the size of a grapefruit, smooth '
        'and matte all over like an old cannonball, sitting in a small dent in short mown grass on a summer '
        'evening, seen low and close, warm golden light on one side of it and its long shadow stretching '
        'across the grass, beyond it only soft green grass and a dark band of trees far behind.' + STILL,
        'Three long thin javelins stuck point first into the short grass of an empty throwing field on a summer '
        'evening, standing a little apart and leaning the same way, seen side on from a few paces off, a pale evening '
        'sky and a line of trees behind, everything sharp, the colours pale, washed out and faded like an old '
        'print.' + STILL,
        'An empty red running track curving round a stadium at sunrise, white lane lines.' + STILL,
    ],
    'weights': [
        'A weightlifter holding a barbell overhead seen as a black silhouette against a tall bright window in a dim old'
        ' gym.' + ACTION,
        'A pair of old fashioned cast iron globe dumbbells lying side by side on the scuffed wooden floor of '
        'an old gym, each one a short iron handle with a round ball of dull matte black iron at each end, '
        'worn smooth and grey where hands have held them, seen low and close, a bare painted brick wall soft '
        'behind in dim window light.' + STILL,
        'Two ordinary grown adults in plain grey sweatshirts and grey sweatpants stand a little apart in an '
        'old gym with bare painted brick walls and tall windows, facing the viewer, each lifting a small '
        'round chrome dumbbell in each hand up to the shoulders mid curl: a heavy set bald man of about '
        'fifty five, clean shaven, and a grey haired woman of about sixty with short permed hair, both '
        'puffing with effort, a candid snapshot of a town gym.' + ACTION,
        'A thick old brown leather lifting belt lying loosely coiled on a scarred wooden bench in an old American town'
        ' gym, its plain steel buckle catching the light, soft window light from one side, a bare painted cinder '
        'block wall behind and the rest of the gym dim.' + STILL,
        'An old flat wooden weight bench padded with cracked brown leather standing alone on a worn plank '
        'floor in an old gym, seen side on from a few paces off, a bare painted brick wall behind it and '
        'soft window light falling across the leather.' + STILL,
        'A barbell resting on a wooden lifting platform in front of a red painted wall, seen from in front with the bar'
        ' running across the picture, both ends loaded the same with big black iron plates, a bright light on the red '
        'wall behind it so the plates in front are flat black discs with no detail, only the steel bar catching a thin '
        'line of light.' + STILL,
    ],
    'hockey': [
        'A farm pond in winter frozen solid from bank to bank and covered in snow, seen from the bank, a '
        'rough rectangle in the middle swept down to pale milky white ice scored with white skate '
        'scratches, low banks of swept snow along its sides, at its far end a pond hockey goal of two short '
        'split firewood logs standing on end a few feet apart on the ice, a black puck lying on the ice '
        'between them, small in the picture, bare trees round the pond under a pale grey sky, quiet and '
        'still.' + STILL,
        'A close view of an ice hockey goalie\'s head and shoulders, a moulded white fibreglass mask of the 1970s with '
        'oval eye holes and small breathing holes, his shoulders in a white jersey, the white netting behind him '
        'blurred.' + ACTION,
        'A pair of worn black leather ice hockey skates hanging by their laces from a nail on a wooden wall, a wooden '
        'stick leaning below them.' + ACTION,
        'A black rubber puck standing on rough scratched white ice, seen with the camera lying flat on the ice right '
        'beside it, so the puck shows only as a low dark band from the side, ice shavings round it, the rest of the '
        'rink a soft pale blur behind.' + STILL,
        'A small outdoor ice rink in a snowy town park at dusk, seen from a little way off and slightly above, smooth '
        'plain white ice ringed by low wooden boards painted white, old lamp posts glowing round it, bare trees and '
        'snow beyond, nobody there.' + STILL,
        'An empty small old indoor ice rink under bright lights, seen from high in the stands behind one '
        'end, looking straight down the length of the rink: plain white ice with rounded corners, one '
        'small red goal net standing near the far end in front of the far boards, one straight thick red '
        'line running across the ice from side to side halfway down the rink, plain white boards all round '
        'the ice, empty wooden seats rising along both sides.' + STILL,
    ],
    'bowling': [
        'A dark blue marbled bowling ball rolling away from the viewer down the middle of a long polished wooden lane, '
        'seen from just above the boards behind it, the ten white pins small in their triangle at the far end under a '
        'warm light, only darkness above them.' + ACTION,
        'Three old bowling balls of dull plain colours, one dark red, one navy blue and one black, sitting side by side '
        'in the round hollows of a low wooden floor rack against a dark wood panelled wall, their finger holes turned '
        'away so only their smooth round backs show, a soft satin sheen on them, seen from standing height a few paces '
        'off, everything sharp, the colours pale, washed out and faded like an old print.' + STILL,
        'One white bowling pin with two red stripes round its neck standing on the polished maple boards of a lane, '
        'seen low and close, and behind it a wide dim bowling alley with many lanes side by side, their long shiny '
        'boards running away into the distance soft and out of focus, warm light glowing on the varnish.' + STILL,
        'One plain dark red bowling ball sitting still on the worn maple boards at the start of a lane, seen from '
        'beside it at standing height, its finger holes turned away so only its smooth round side shows, the long lane '
        'running away beside it to the small white pins at the far end, plain dark walls, the room dim, everything '
        'sharp from near to far, the colours pale, washed out and faded like an old print.' + STILL,
        'A row of empty bowling lanes in an old bowling alley at closing time, seen low from the approach, the long '
        'polished boards gleaming as they run away to the small white pins far off, the room dark and the area above '
        'the pins in deep shadow.' + STILL,
        'The ten white bowling pins standing close together in their neat triangle on the pin deck at the end of a '
        'lane, seen straight down the lane from a few metres in front at the height of the pins, the front pin in the '
        'middle and the rest stepping back behind it in rows of two, three and four, each pin with one red stripe '
        'round its neck, warm light on them, the dark pit behind in shadow, everything sharp, the colours pale, '
        'washed out and faded like an old print.' + STILL,
    ],
    'racing': [
        'An empty red clay dirt track oval at a county fairground at dusk, seen from the top of the bank on the outside '
        'of a turn, the smooth banked clay curving away round the turn, fresh wheel grooves swept through it, a plain '
        'white wooden rail round the outside, floodlights on tall wooden poles just coming on, trees and a pink evening '
        'sky beyond.' + STILL,
        'Damp red clay in the middle of a turn of a dirt race track in the evening, seen low and close, the whole '
        'ground from the bottom edge to the far distance one sweep of red clay: deep curved wheel ruts cut through it,'
        ' clods thrown up along the rims of the grooves, the clay curving away soft and blurred, a haze of red dust '
        'hanging in the low golden light.' + STILL,
        'An old round white helmet of the 1960s shaped like a smooth deep bowl, open at the front with the face '
        'uncovered, a brown leather chin strap hanging below it, sitting on a weathered wooden workbench in a dim tin '
        'roofed garage beside a pair of brown leather goggles, soft light from an open door, the bare plank wall '
        'behind.' + STILL,
        'A black and white chequered flag on a short wooden pole waving high against a pale evening sky, the cloth '
        'rippling and filling the upper half of the picture, below it only the soft blurred grey edge of a track and '
        'the far floodlight poles of a small speedway.' + ACTION,
        'A small paved oval race track at night under floodlights, empty, seen from the top of the grandstand, the grey '
        'asphalt banked turns shining under the lights, a low concrete wall painted plain white round the outside, the '
        'green infield grass in the middle, the dark sky above.' + STILL,
        'Looking down from standing height at smooth fine grey asphalt filling the whole picture, and two wide smooth '
        'black rubber skid streaks sweeping across it in long parallel curves, each one a soft solid dark smear '
        'fading at its edges, warm low evening light raking across the surface.' + STILL,
    ],
    'pool': [
        'A white cue ball sitting alone on the green felt of an old pool table near the dark wooden rail, seen from '
        'standing height at a slant, warm light falling on the felt from a lamp above out of the picture, the rest of '
        'the bare table running away into the dark room, everything sharp, the colours pale, washed out and faded '
        'like an old print.' + STILL,
        'Two pool balls resting a hand\'s width apart on the bare green felt of an old pool table, one plain solid red '
        'of one colour all over and one plain solid yellow of one colour all over, seen from standing height at a '
        'slant, warm light from a lamp above out of the picture, the dark wooden rail beyond them and the room dark, '
        'everything sharp, the colours pale, washed out and faded like an old print.' + STILL,
        'A black eight ball on the green felt of a pool table beside a corner pocket.' + STILL,
        'The corner pocket of an old pool table seen close from a little above, a worn brown leather pocket '
        'where two dark polished wooden rails meet, the green felt cushions curving into its dark mouth, '
        'warm lamp light on the felt, the rest of the bare green table soft and blurred beyond.' + STILL,
        'A pool table in a dim back room of a bar, a low lamp with a deep green glass shade hanging over it, the green '
        'felt bare and empty in its pool of light, the walls behind plain dark wood panelling from floor to ceiling, '
        'the corners of the room in darkness.' + STILL,
        'Two green pool tables side by side in a dim old pool hall at night, seen from a few metres away at standing '
        'height, each table with its own long low lamp with a green shade hanging right over the middle of its felt, '
        'pools of warm light on the two bare tables, plain dark wood panelling behind, the room quiet and '
        'still.' + STILL,
    ],
    'fieldhockey': [
        'One small hard white field hockey ball, the size of an orange, its plastic surface lightly dimpled, '
        'scuffed and dulled with use, a few blades of cut grass stuck to it, resting in short mown grass, seen '
        'low and close, late afternoon sun and its long shadow across the grass, beyond it only a soft green '
        'blur of grass and a dark band of trees.' + STILL,
        'Two varnished wooden field hockey sticks lying crossed on mown green grass, each with a short flat curved '
        'hook at its foot, a small hard ball of smooth seamless glossy white plastic, plain white all over, resting '
        'beside the hooks, seen from standing height looking down at a slant, long evening shadows across the '
        'grass.' + STILL,
        'A low wide field hockey goal standing on wet green grass at dusk, seen squarely from straight in front a '
        'little way off with its open mouth facing the viewer: two short white posts and a white crossbar making one '
        'plain rectangle, a white net hanging straight down behind it, and a white chalk line curving round in front '
        'of the goal across the grass.' + STILL,
        'The curved hooked foot of a varnished wooden field hockey stick lying on green grass, only the hook and a '
        'short length above it in the picture, beside a hard smooth white ball, perfectly round and glossy like a cue '
        'ball, the size of an orange.' + STILL,
    ],
    'hunting': [
        'A whitetail buck with a small neat rack of short antlers grazing in a mown hayfield on an '
        'overcast autumn morning, standing still side on with his head lowered to the stubble, a compact '
        'deer body on four slim legs, his coat a rough shaggy grey brown, the whole deer in view and small '
        'in the picture, seen from a long way off through a long lens, slightly soft, a line of bare woods '
        'far behind across the field.' + WILD,
        'A wild turkey gobbler walking side on through dry brown leaves in a woodland clearing in spring, '
        'seen from a little way off with a long lens, his one tail of folded feathers trailing low behind '
        'him close to the ground in a single narrow bundle, his body feathers dull dark brown, his small '
        'bare head a dull bluish grey with a faded red wattle stretched forward, grey tree trunks behind '
        'in flat overcast light, dull muted colours and slightly soft focus.' + WILD,
        'A plain blaze orange wool hunting cap hanging on the top of a weathered grey wooden fence post at the edge of'
        ' a misty autumn meadow at dawn, seen close, dew on the grass and the fence wire, the misty trees of the wood '
        'soft and grey behind.' + WILD,
        'A pair of mallards swimming side by side on a still misty marsh at sunrise, a drake with a glossy green head '
        'and a mottled brown female, both seen side on from a little way off with their wings folded, their '
        'reflections in the calm water below them, tall reeds around and soft golden mist behind.' + WILD,
        'A grown doe with a smooth plain tan brown coat and, a little apart from her, a small spotted fawn, standing'
        ' in tall green grass at the edge of a forest.' + WILD,
        'A wide flat mown hayfield of short dull brown stubble at dusk filling the lower two thirds of the '
        'picture, and very far off across it at the edge of a bare autumn wood a lone whitetail buck '
        'standing side on, a tiny dark grey brown shape with a small rack of antlers against the grey tree '
        'trunks, a pale overcast sky, muted cool colours, grainy and a little soft.' + WILD,
        'A liver and white pointer hunting dog sitting upright in short dry autumn grass at the edge of a '
        'field, seen side on from a little way off, her head up and turned to look out across the field, '
        'her ears hanging soft, the bare brown woods behind her soft and blurred, flat overcast light and '
        'muted autumn colours.' + WILD,
        'A bull elk with one large pair of branching antlers standing side on in a mountain meadow in autumn, its head'
        ' held high.' + WILD,
    ],
}
for _sport, _prompts in SPORTS.items():
    for _i, _prompt in enumerate(_prompts):
        art('%s-%d' % (_sport, _i), _prompt, TALL)


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
# Qwen-Image paints whatever is named, so asking for no signature gave every painting one.
OIL = ('. An oil painting with visible brushstrokes and slightly yellowed varnish, the background brushwork carried'
       ' right out to all four edges and into every corner, the painted surface filling the whole image edge to edge.')
DETAIL = (', the brushwork running off every edge of the picture. Visible brushstrokes and slightly yellowed'
          ' varnish.')
PAINTINGS = {
    "Lilly's Waterpond": ('a close cropped detail from the middle of a large impressionist oil painting of water lilies'
                          ' floating on a pond among the reflections of willows, soft blues, greens and violets' + DETAIL, WIDE),
    "She's Got It": ('a close cropped detail from the middle of a large classical oil painting: a woman in a white gown'
                     ' reclining on a red velvet chaise longue against dark drapery, the chaise and the drapery running'
                     ' on past the sides' + DETAIL, BROAD),
    'Aaaaah!': ('a close cropped detail from the middle of a large expressionist oil painting: a pale lone figure in a '
                'long dark coat on a long bridge facing the viewer, both hands pressed to its cheeks and its mouth open'
                ' in a scream, a swirling orange and red sky above a dark blue fjord, the bridge and its rail running '
                'out of the bottom of the picture' + DETAIL, TALL),
    'Abe Lincoln': ('a close cropped detail of the head and shoulders from a large formal 19th century oil portrait of '
                    'Abraham Lincoln in a dark suit, his dark coat running off the bottom and the sombre brown '
                    'background off the top and sides' + DETAIL, TALL),
    'Apple Faced Man': ('a close cropped detail of the head and shoulders from a large naive American folk '
                        'art oil painting of a laughing old orchard farmer with a big round face and round '
                        'shiny red cheeks like two apples, a white bristly beard and twinkling eyes, '
                        'bareheaded with wild white hair, in a faded blue work shirt with one big red '
                        'apple sitting in its breast pocket, leafy green apple boughs hung with red apples '
                        'behind him, painted flatly in bright simple colours, his shoulders running off the '
                        'bottom and the sides and the boughs off the top, the brushwork running off every '
                        'edge of the picture. Visible brushstrokes of matte oil paint.', TALL),
    'Benjamin Franklin': ('a close cropped detail of the head and shoulders from a large formal 18th century oil '
                          'portrait of Benjamin Franklin in a brown coat, long grey hair, his coat running off the '
                          'bottom and the dark background off the top and sides' + DETAIL, TALL),
    'Betty': ('1950s painted portrait of a smiling blonde woman with curled hair in a high necked blue cardigan, head and shoulders, warm brown background' + OIL, TALL),
    'Calvinist': ('a close cropped detail of the head and shoulders from a large 17th century Dutch oil portrait of a '
                  'stern old man in a black hat and a wide white ruff collar, chiaroscuro, his black coat running off '
                  'the bottom and the dark background off the top and sides' + DETAIL, TALL),
    'Chromatic Accident': ('a close cropped detail from the middle of a large abstract expressionist drip painting, '
                           'dense tangled splatters and drips of black, rust orange, white and grey on cream' + ', the splatters running off every edge of the picture. Matte paint, the painted surface filling the whole image.', LONGER),
    'Configuration II': ('an unsigned square painted wooden panel, a geometric abstract painting in the De Stijl manner '
                         'filling the whole panel: a few straight thick black lines dividing a white ground into a handful'
                         ' of large clean flat areas, rectangles of pure red, blue and yellow gathered round the middle, '
                         'crisp hard edges, flat even paint, the black lines meeting the edges of the panel well away from'
                         ' its corners, all four corners wide plain areas of the same flat white paint. The paint carried '
                         'right out to all four edges, the painted surface filling the whole image edge to edge.', SQUARE),
    'Configuration No. 10': ('a close cropped detail from the middle of a large geometric abstract painting in the De '
                             'Stijl manner: a few straight thick black lines running right across a white ground and '
                             'off every edge of the picture, dividing it into rectangles; one large rectangle painted '
                             'solid blue and two small ones solid red and yellow, each colour filling its rectangle '
                             'exactly up to the black lines, the rest white, flat even paint' + '.', SQUARE),
    'Layout I': ('a geometric abstract painting in the De Stijl manner: a few straight thick black lines dividing a white canvas into a handful of large clean flat areas, a large red square and small blue and yellow rectangles, crisp hard edges, flat even paint' + '. The painted surface filling the whole image edge to edge.', SQUARE),
    'Desert Landscape': ('a close cropped detail from the middle of a large oil painting of a desert landscape: red '
                         'sandstone mesas and sagebrush under a huge pale sky at dusk, the sagebrush running out of the'
                         ' bottom of the picture' + DETAIL, BROAD),
    'Flowers On Table': ('a close cropped detail from the middle of a large Dutch golden age still life oil painting: a'
                         ' vase of mixed flowers on a table with a white cloth against a dark background, the cloth and'
                         ' the table running out of the bottom of the picture' + DETAIL, TALL),
    'George Washington': ('a close cropped detail of the head and shoulders from a large formal oil '
                          'portrait of George Washington at sixty four in the manner of Gilbert Stuart, '
                          'turned three quarters to the left, an old man with a stern grave unsmiling '
                          'look: a big broad heavy face lined with age, a long straight nose, calm grey '
                          'eyes under heavy lids, ruddy cheeks, a wide firmly closed mouth with the lips '
                          'pressed tightly together and drawn in, the lower face full and square over a '
                          'heavy jaw, white powdered hair brushed back from a high forehead and rolled in '
                          'curls over the ears, a white lace frill at the throat, his black velvet coat '
                          'running off the bottom and a red curtain behind him running off the top and '
                          'sides' + DETAIL, TALL),
    'Helianthus': ('a close cropped detail from the middle of a large American oil painting: three tall sunflowers in a'
                   ' white enamel milk jug standing on a farmhouse windowsill, a pale blue painted wall either side and '
                   'an open window behind them onto green summer fields and a deep blue sky, cool blues and greens round '
                   'the yellow flowers, smooth thin paint and soft even brushwork, the jug running out of the bottom of '
                   'the picture, the brushwork running off every edge of the picture.', TALL),
    'Incognito Library': ('a close cropped detail from the middle of a large oil painting of the interior of an old '
                          'library: tall shelves of leather books, a fireplace, an armchair and a brass lamp, warm '
                          'brown tones, the shelves running off both sides and the floor off the bottom' + DETAIL, WIDE),
    'Island Beach': ('a close cropped detail from the middle of a large seascape oil painting: waves breaking white on '
                     'dark rocks off an island shore, a deep turquoise sea laid in with clean smooth strokes of blue and '
                     'green, a soft sky of grey and white clouds, the surf and the rocks running out of the bottom of the'
                     ' picture, the brushwork running off every edge of the picture. Visible brushstrokes, clean cool '
                     'colours.', LONGER),
    'John Adams': ('a close cropped detail of the head and shoulders from a large formal 18th century oil portrait of '
                   'John Adams in a dark coat and white cravat, grey hair, his coat running off the bottom and the dark'
                   ' background off the top and sides' + DETAIL, TALL),
    'Lakeside': ('a close cropped detail from the middle of a large oil painting of a still woodland lake: green trees'
                 ' and their reflections in the water, dappled light, purple wildflowers along the near bank running '
                 'out of the bottom of the picture' + DETAIL, LONGER),
    'Lighthouse': ('a detail from a large oil painting of a rocky coast: a small round white brick '
                   'lighthouse with a black iron lantern room on top standing low in the picture, the top '
                   'of its lantern a little above the middle of the picture\'s height, the whole upper half '
                   'of the picture open sky painted in matte broken dabs of dry blue and grey white paint, '
                   'the clouds loose scrubbed brushstrokes with the canvas weave showing through them like a '
                   'Winslow Homer coast, a white clapboard '
                   'keeper\'s house and dark pine trees beside it on a low point of plain cool grey granite '
                   'ledges, a blue sea, the ledges and the white surf running out of the bottom of the '
                   'picture, painted in cool blues, whites, slate greys and dark greens, the brushwork running '
                   'off every edge of the picture. Visible brushstrokes of matte oil paint.', TALL),
    'Man With One Ear': ('post-impressionist self portrait of a red bearded man in a fur cap and green coat, a thick white cloth bandage wrapped round his head over one ear and tied under his chin, swirling brushstrokes' + OIL, TALL),
    'Margarite': ('a close cropped detail of the head and shoulders from a large 17th century Dutch oil '
                  'painting of a plump laughing young woman of about twenty five, bareheaded, her thick '
                  'red brown hair parted in the middle and pinned up loosely with curls falling by her '
                  'cheeks, facing the viewer full front with her head tilted to one side and her mouth '
                  'open in a wide laugh, rosy cheeks, in a plain white linen chemise gathered high up to '
                  'her collarbone under a deep red wool bodice laced at the front, a plain flat evenly lit '
                  'wall of warm ochre plaster behind her, painted in loose quick bold brushstrokes, her '
                  'bodice running off the bottom and the wall off the top and sides' + DETAIL, TALL),
    'Monica Elisa': ('an ink and watercolour portrait of a woman with long black hair in a red and orange patterned robe, head and shoulders, white paper background', TALL),
    'Mount Never Rest': ('a close cropped detail from the middle of a large romantic landscape oil painting: a towering'
                         ' snow capped mountain above a lake and pine forest, golden light breaking through clouds, the'
                         ' lake running out of the bottom of the picture' + DETAIL, LONGER),
    'Night With Stars': ('post-impressionist painting of a swirling starry night sky with a crescent moon over a small sleeping village of many little houses with lit windows clustered below hills, a church spire rising among them, a tall dark cypress in the foreground, thick impasto' + OIL, BROAD),
    'Nomad Above The Gloom': ('a close cropped detail from the middle of a large romantic oil painting: a small far off '
                              'traveller in a long red cloak with a walking staff, seen from the side, leading a laden '
                              'grey mule along a narrow path high on a sunlit grassy ridge that runs across the picture,'
                              ' deep dark valleys full of storm clouds and gloom below the path, a pale golden evening '
                              'sky above, warm gold and red against deep blue grey, the valleys running out of the '
                              'bottom of the picture and the ridge off both sides' + DETAIL, SQUARE),
    'Omega Department': ('a gouache illustration of a friendly brown bear and a badger with a grey coat, a black and '
                         'white striped face and a short grey tail, standing side by side a little apart on a flat pale'
                         ' yellow ground that runs off every edge of the picture, soft matte gouache colours', TALL),
    'Riverside': ('a close cropped detail from the middle of a large impressionist oil painting in the manner of Claude '
                  'Monet of a river bank on a hazy early summer day: a wide calm river winding away between low '
                  'green meadows, two big willow trees leaning over the water on the near bank, a small wooden rowing '
                  'boat tied up under them, a soft hazy pale grey blue sky with soft white clouds, the colours soft and '
                  'muted, sage and olive greens, dusty grey blues, ochre and creamy whites, the brushwork varied: short '
                  'quick flicks in the grass, feathery dabs in the willows, long broken horizontal strokes on the water '
                  'carrying the reflections of the trees and the sky, the lower corners of the picture full of meadow '
                  'grass on one side and river water on the other, the water running out of the bottom of the '
                  'picture' + DETAIL, WIDE),
    'Sir Brakefire': ('17th century portrait of a gentleman with a pointed beard in a broad black hat and lace collar, dark background, chiaroscuro' + OIL, TALL),
    'Two Frame': ('a close cropped detail from the middle of a large impressionist oil painting of a country lane '
                  'between flowering hedges, lilac hills beyond, the lane running out of the bottom of the picture' + DETAIL, WIDE),
    'Violetta': ('a close cropped detail from the middle of a large impressionist oil painting: a woman in a white '
                 'dress holding a parasol in a sunny garden of pink roses, the green lawn running out of the bottom of'
                 ' the picture and the roses off both sides' + DETAIL, WIDE),
    'Waterbeach Hills': ('a close cropped detail from the middle of a large landscape oil painting: rolling green '
                         'hills and smooth meadows under a blue sky with white clouds, the meadow running out of the '
                         'bottom of the picture' + DETAIL, WIDE),
    'Waterfall': ('a close cropped detail from the middle of a large oil painting of a waterfall dropping through '
                  'autumn woods into a rocky pool, the pool and its rocks running out of the bottom of the picture' + DETAIL, WIDE),
    'Wide': ('a close cropped detail from the middle of a large pastoral landscape oil painting: a green river valley '
             'with old spreading oak trees, a shallow ford with a small wooden rowing boat drawn up on the grassy bank, '
             'distant blue hills and golden evening light, the river running out of the bottom of the picture' + DETAIL, STOUT),
    'Dr. Oids': ('chunky 8-bit pixel art made of large flat squares of colour like a cross stitch pattern: a little '
                 'red robot with a square head, two square eyes and a short antenna walking along a row of brown '
                 'square brick blocks, a black night sky above him dotted with single white square pixels for stars,'
                 ' the bricks running off both sides of the image, every square one flat colour with crisp hard '
                 'edges', TALL),
}
for _name, (_prompt, _size) in PAINTINGS.items():
    _key = 'painting-' + ''.join(c for c in _name.lower().replace(' ', '-') if c.isalnum() or c == '-').strip('-')
    art(_key, _prompt, _size)
    HUNG.append(('Painting|' + _name, _key))
# Measured off the game's sprites of each with the mod's own sums, less the frame the mod paints round it.
for _name, _shape in (("Lilly's Waterpond", 1.5), ("She's Got It", 2.05), ('Chromatic Accident', 2.9), ('Desert Landscape', 2.0), ('Island Beach', 2.45),
                      ('Lakeside', 2.4), ('Mount Never Rest', 2.9), ('Night With Stars', 1.8), ('Nomad Above The Gloom', 1.0), ('Wide', 1.2)):
    SHAPED['painting-' + ''.join(c for c in _name.lower().replace(' ', '-') if c.isalnum() or c == '-').strip('-')] = _shape

art('painting-field-pink', 'a colour field abstract painting, two large soft edged hazy rectangles, magenta above warm yellow, on an orange ground, matte paint on canvas' + '. The painted surface filling the whole image edge to edge.', TALL)
art('painting-field-purple', 'a very dark and sombre colour field abstract painting, two large soft edged '
                             'hazy rectangles of flat plain colour stacked on a deep wine dark ground: the '
                             'upper one a black aubergine, the lower one a near black navy, each one an '
                             'even stain of a single colour, the whole canvas dark and quiet, thin smooth '
                             'translucent washes of matte paint stained flat into the canvas, the edges of '
                             'the rectangles soft and cloudy' + '. The painted surface filling the whole image edge'
    ' to edge.', TALL)
HUNG.append(('Paintings|Pink and Yelllow', 'painting-field-pink'))
HUNG.append(('Paintings|Purple and Blue', 'painting-field-purple'))
art('painting-sitter', 'a close cropped detail of the head and shoulders from a large dark old master oil portrait of a '
    'pale woman with dark hair parted in the middle, in a blue silk dress, against a sombre brown background, the old '
    'paint softly aged and mellow under warm amber varnish, her dress running off the bottom and the background off the '
    'top and sides' + DETAIL, TALL)
HUNG.append(('location_entertainment_gallery_01_72', 'painting-sitter'))
HUNG.append(('location_entertainment_gallery_01_73', 'painting-sitter'))

# Boards.
art('board-cork', 'A flat scan of brown granular cork, the cork surface filling the whole image to all four edges and '
                  'corners, untidily covered in papers of many sizes and colours overlapping at odd angles: torn '
                  'sheets of blank lined paper, a yellowed sheet, a folded pink sheet and one curled faded snapshot of'
                  ' a lake, each paper held by a single round red push pin. A flat scan seen straight on in even '
                  'light.', WIDE)
art('board-white', 'A seamless field of smooth white enamel seen straight on, the white running on past every edge and'
    ' corner of the image, faint grey ghost smears of wiped marker ink spread across it, a large looping scribble, a few'
    ' curved arrows and some wavy lines in faded blue and red marker ink, half wiped away, the whole surface lit by flat'
    ' even light with a soft even sheen.', BROAD)
art('board-darts', 'A close view of the two shut doors of a dark stained wooden wall cabinet seen straight on, the two plain'
                   ' panelled doors filling the whole picture edge to edge, a thin seam down the middle where they meet,'
                   ' small brass hinges at the outer sides and a little brass catch at the seam. A plain photograph in'
                   ' even light.', BROAD)
for _group in ('Big Cork', 'Cork'):
    HUNG.append(('Noteboard|' + _group, 'board-cork'))
for _group in ('Office', 'Wide Office'):
    HUNG.append(('Whiteboard|' + _group, 'board-white'))
HUNG.append(('Dartboard|', 'board-darts'))
art('certificate', 'An empty unprinted sheet of cream parchment paper seen straight on and filling the whole frame, an engraved'
    ' guilloche border in dark green round its edge and a round embossed gold foil seal near the bottom, the middle of the sheet'
    ' plain and blank. A flat scan in even light.', WIDE)
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
GIG = (' They are placed low in the picture with their heads below the middle of the frame, and the backdrop behind'
       ' them darkens gently upward and carries on above their heads to the top edge. A 1980s record company'
       ' promotional photograph on colour film filling the whole frame edge to edge, a slightly faded print with fine'
       ' grain.')
OLD_GIG = (' Seen from far back, so they take up only the lower half of the picture with their heads a little below the'
           ' middle, and the same plain wall carries on across the whole upper half above them. Taken in black and white'
           ' in the 1960s, only shades of grey, fine film grain, the photograph running right out to all four edges.')
LOW = (' Seen from far back, so they take up only the lower half of the picture with their heads a little below the middle,'
       ' looking at the viewer, and the same plain wall carries on, darkening gently upward, across the whole upper half'
       ' above them. Taken on colour film in the 1980s, natural faded colours and fine grain, the photograph running right'
       ' out to all four edges.')
BAND = (' Facing the camera and framed from the waist up, lit with soft even studio light. Taken on colour film in the'
        ' 1980s, slightly faded colours and fine grain, the plain coloured backdrop running right out to all four edges'
        ' of the image.')
OLD_BAND = (' Facing the camera and framed from the waist up, lit with soft even studio light. Taken in black and white'
            ' in the 1960s, only shades of grey, fine film grain, the plain backdrop running right out to all four edges'
            ' of the image.')
MUSIC = [
    'A four piece glam rock band in black leather jackets standing side by side in a row in front of a plain dark red '
    'wall: a tall woman with a big teased blond mane, a short dark haired woman with a fringe, a lanky man with a black'
    ' shag cut and a stocky bearded man with short red hair.' + BAND,
    'One rock and roll singer alone, a man of about twenty five with slick dark hair in a dark waistcoat and white '
    'shirt, sitting upright on a low stool seen full length, his arms folded, a plain dark grey wall behind him.' + OLD_GIG,
    'Four young men in matching slim grey suits and narrow ties standing in a row a little apart, a 1960s beat group,'
    ' each plainly a different person: one tall and thin with a blond mop, one short and stocky with dark curls, one'
    ' with glasses and slicked back hair, one with a long face and a dark fringe, seen full length in front of a plain'
    ' pale grey wall.' + OLD_GIG,
    'A cheerful pop group of four young men in bright paisley shirts standing shoulder to shoulder in a row, all four '
    'turned towards the camera and smiling, in front of a plain pink wall: a tall one with short ginger hair, a short '
    'round faced one with dark curls, one with round glasses and long sideburns, and a thin one with a blond '
    'fringe.' + BAND,
    'A 1970s hard rock trio of three men in denim jackets over plain black cotton t-shirts, standing in a '
    'row a little apart, their arms hanging at their sides, in front of a plain charcoal grey wall, three '
    'different looking men of about thirty with clear detailed faces: one clean shaven with long straight '
    'black hair, one with a curly brown perm and a short dark beard, and one clean shaven with long blond '
    'hair falling loose over his shoulders.' + BAND,
    'A rock singer with shoulder length brown hair and a headband standing full length at a microphone stand on a dark'
    ' stage, a black shirt, a single spotlight on him.' + LOW,
    'A country rock singer of about thirty with long brown hair, sunglasses and a fringed suede jacket '
    'over a plain white shirt, standing low in the picture with a wide empty band of the plain burnt '
    'orange wall above the top of his head, his thumbs hooked in the pockets of his jeans.' + BAND,
    'A pop singer of about twenty five with wavy shoulder length blond hair in a black leather jacket, white t-shirt and'
    ' jeans, sitting upright on a low wooden crate seen full length, a plain deep blue wall behind him.' + LOW,
    'Three young men in white suits with short dark hair, a 1990s rhythm and blues vocal group, standing in a row '
    'with their faces and the fronts of their jackets towards the camera, smiling at the viewer, seen full length, '
    'an arm\'s length of space between each man and the next, a plain dark navy wall behind them reaching to the top '
    'edge.' + LOW,
    'One red haired woman singer of about thirty alone, with long wavy red hair, in a white dress with long sleeves and'
    ' a high neck, her arms hanging relaxed at her sides, smiling at the viewer, in front of a plain dusty rose '
    'wall.' + BAND,
    'A crooner of about seventy with silver hair in a black tuxedo and bow tie standing full length beside a vintage '
    'silver microphone on a stand, a plain black wall behind him, a warm spotlight.' + LOW,
    'A woman pop singer of about twenty five with long curly red hair laughing, in a buttoned denim jacket over a white'
    ' t-shirt tucked into jeans, sitting upright on a low stool seen full length, behind her one smooth pale teal wall '
    'of a single even colour from the floor all the way up to the top edge.' + (
        ' Seen from far back, so she takes up only the lower half of the picture with her head a little below the'
        ' middle, looking at the viewer. Taken on colour film in the 1980s, natural faded colours and fine grain, the'
        ' photograph running right out to all four edges.'),
    'A country singer of about thirty five in a black shirt sitting on a low stool, holding his black hat in both '
    'hands against his chest, his dark hair combed back, a plain weathered plank wall behind him.' + OLD_BAND,
    'A woman singer of about twenty five with short brown hair in a loose white t-shirt and jeans, her arms '
    'hanging at her sides, her lips together in a calm gentle smile, a plain pastel pink wall '
    'behind her.' + BAND,
    'A disco singer with a big afro in a long sleeved high necked purple sequin gown under a mirror ball, purple light.' + GIG,
    'A smiling country singer of about thirty in a white cowboy hat and an embroidered western shirt, his '
    'arms folded across his chest, standing low in the picture with a wide empty band of the plain golden '
    'sepia wall above the crown of his hat.' + BAND,
    'A folk singer of about thirty in a flat cap, white shirt and red tie, his hands in his trouser pockets and his '
    'head tilted a little, smiling, in front of a plain cream wall.' + BAND,
    'A plain iron arch bridge over a river in a city on a still overcast day, one single purple balloon floating at '
    'mid height in the picture just above the bridge, open pale sky above it, nobody in sight. A 1990s indie album '
    'cover picture on colour film, slightly faded with fine grain.',
]
# The names the game's own posters carry, where they can be read off them. The rest hang with no lettering.
ACTS = {1: 'FRIDAY NIGHT', 2: 'THE 594s', 3: 'THE BOJANGLES', 4: 'STEAMSHIP HURRICANE', 6: 'WILL BILL GROOVY', 7: 'SOUNDS LIKE AUSTIN PARKER!',
        8: 'SOFT ONEZ', 9: 'MARIA', 10: 'TIME FOR LOVE', 11: 'TONI', 14: 'YVETTE', 15: 'HANK GILMAN'}
for _i, _prompt in enumerate(MUSIC):
    art('music-%02d' % _i, _prompt, SQUARE)
    _words = (ACTS[_i], 'THE NEW ALBUM  .  OUT NOW') if _i in ACTS else ()
    LETTERED['music-%02d-tall' % _i] = ('music-%02d' % _i, 'gig-tall', *_words)
    LETTERED['music-%02d-wide' % _i] = ('music-%02d' % _i, 'gig-wide', *_words)
# The sheet has them eight to a row, a row seen from the south and then a row seen from the east, and each act is three
# sprites running on through the rows of its side: the large sheet, the wide one, the small one.
_SHAPES = ('tall', 'wide', 'tall')
for _side in (0, 8):
    _run = [_row * 16 + _side + _i for _row in range(7) for _i in range(8)][:54]
    for _place, _index in enumerate(_run):
        HUNG.append(('walls_decoration_06_%d' % _index, 'music-%02d-%s' % (_place // 3, _SHAPES[_place % 3])))

# The posters of the films showing in 1993, as the game names them. Square art again, cut tall for a one sheet and broad
# for a hoarding, with the film's name lettered on.
FILM = (' The subject stands high in the picture with every face in the upper half of the image, and the dark backdrop'
        ' carries on below it to the bottom edge. A 1993 studio photograph for a movie, the picture filling the whole'
        ' image edge to edge, a fine halftone texture and slightly muted colours.')
FILMS = {
    'Cyberkiller': ('Cyberkiller', 'Head and shoulders of a grim square jawed cyborg soldier in smooth black modern '
                                   'tactical body armour of moulded matte plates, a smooth steel plate over the left '
                                   'half of his face with a round glowing blue glass eye where the eye was, dark teal '
                                   'background.' + FILM,
                    'CYBERKILLER', 'IN THEATERS NOW'),
    'Cyberkiller 2': ('Cyberkiller 2', 'Two grim faces side by side seen close from the shoulders up, their heads large '
                      'in the picture: on the left a bearded man with the collar of a brown leather jacket turned up, '
                      'and on the right a man in a plain dark zipped nylon jacket whose left half of the face from brow'
                      ' to jaw is a smooth riveted steel mask, the right half his own bare face, the dark teal '
                      'backdrop reaching every edge of the picture.' + FILM,
                      'CYBERKILLER 2', 'IN THEATERS NOW'),
    'Operation Fort Knox': ('Operation Fort Knox', 'Head and shoulders of a secret agent of about forty five with thinning '
                            'sandy hair, a broad weathered face with deep lines and a crooked nose, natural pale skin, in a '
                            'dark grey suit and a loosened dark tie, standing in a dim steel vault, behind him rows of '
                            'smooth bare gold bars with clean blank faces stacked on steel shelves, cool grey light on his '
                            'face and a warm gleam on the gold.' + FILM, 'OPERATION FORT KNOX', 'IN THEATERS NOW'),
    'PAWS': ('PAWS', 'A raccoon, a brown bear cub and a badger walking a little apart along the top of a grassy ridge'
                     ' against a big pale summer sky, seen from below at the foot of the slope, so the animals stand '
                     'high in the picture with their faces in the upper third, and the green slope dotted with '
                     'wildflowers fills the whole lower half below them. A hand painted gouache illustration with soft'
                     ' colours, the scene running off all four edges of the image.',
             'PAWS', 'IN THEATERS NOW'),
    'Dog Goblin 4': ('Dog Goblin 4', 'Head and shoulders of a snarling goblin monster with the head of a '
                                     'shaggy black dog, pointed bat ears, small yellow eyes and long '
                                     'crooked teeth, an actor in a dull matte rubber mask and a shaggy '
                                     'matted fur costume, his shaggy shoulders along the bottom of the '
                                     'figure and his arms below the picture, from a low budget 1980s '
                                     'horror film shot on old 35mm film, among bare trees in a dark misty '
                                     'wood at night, lit dimly from one side by a single weak orange '
                                     'lamp, the rest of the wood in black shadow, soft focus, faded dull '
                                     'brown and grey colours and coarse heavy film grain.' + FILM, 'DOG GOBLIN 4',
                     'IN THEATERS NOW'),
    'Pleistocene Land': ('Pleistocene Land', 'A great shaggy woolly mammoth, a real living animal covered in long matted '
                         'reddish brown hair, with a high domed head, small furry ears, a hairy trunk and two long '
                         'yellowed ivory tusks curving out and up, photographed outdoors standing among snowy pine '
                         'trees under a flat grey winter sky, snow falling, natural dull colours and coarse film '
                         'grain.' + FILM, 'PLEISTOCENE LAND', 'IN THEATERS NOW'),
    'Dr Oids': ('Dr Oids', 'Chunky 8-bit pixel art made of large flat squares of colour like a cross stitch pattern, '
                           'seen flat and straight on: a little silver robot with a square head, two square red eyes '
                           'and a short antenna standing in the upper half of the picture on a floor of square green '
                           'tiles that runs off both sides, a plain flat black sky above of one even colour, every '
                           'square one flat colour with crisp hard edges.',
                'DR. OIDS', 'NOW SHOWING'),
    'The Forgotten Element': ('The Forgotten Element', 'A clean shaven lean man of about thirty five with a narrow face and '
                              'short fair hair under a brown felt fedora with a pinched crown and a short brim, in a worn '
                              'brown leather flight jacket, and a few steps to his right a dark haired woman of about '
                              'thirty in a khaki shirt with her hair pinned up, the two standing well apart with open sky '
                              'between them, framed from the chest up with both their heads near the top of the '
                              'picture, a small bare silver propeller plane of smooth unpainted metal flying in the '
                              'golden sunset sky between them.' + FILM,
                              'THE FORGOTTEN ELEMENT', 'COMING SOON'),
    'Movie': ('Movie', 'A man of about forty with short dark hair in a grey trench coat and a woman of about thirty with '
                       'shoulder length auburn hair and natural matte skin in a tan belted raincoat, standing side by side'
                       ' a little apart under an old iron street lamp on an empty wet cobbled street at night, the blank '
                       'brick walls of old warehouses behind them, rain falling through the lamp light, seen full length'
                       ' from a little way back.' + FILM, 'ONE NIGHT IN LOUISVILLE', 'IN THEATERS NOW'),
}
for _group, (_name, _prompt, _title, _line) in FILMS.items():
    _key = 'film-' + _name.lower().replace(' ', '-')
    art(_key, _prompt, SQUARE)
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
ON_AIR = shows.VIDEO
PROGRAMMES = [
    'A male news anchor in a grey suit and red tie sitting at a news desk in front of a plain blue studio backdrop,'
    ' looking straight out of the frame towards the viewer.' + ON_AIR,
    'A weather forecaster in a brown suit seen from the waist up, smiling and holding out one open hand to the side '
    'as he talks, a smooth plain blue wall behind him filling the rest of the picture.' + ON_AIR,
    'A cheerful young woman of about thirty five with a smooth fresh face and glossy shoulder length brown hair, in a '
    'plain red apron, stirring a pot with a wooden spoon on a white stove at the bottom of the picture, plain wooden '
    'cupboards on the wall behind her, a cooking show.' + ON_AIR,
    'A bearded man in a plaid shirt and safety glasses standing at a workbench in a woodwork shop, pressing a wooden '
    'handled chisel into the edge of a pine board lying flat on the bench, curls of pale wood shavings round it, '
    'a woodworking show.' + ON_AIR,
    'A man in a plain tan fishing vest and a plain olive green cap of one colour sitting in a small aluminium boat on a'
    ' calm lake, a fishing rod in his hands, a fishing show.' + ON_AIR,
    'A baseball pitcher in a plain white uniform and a plain navy blue cap of one colour, seen from far behind home '
    'plate through a long lens, a small whole figure standing on the brown dirt mound in the middle of the picture, '
    'leaning forward to look in with his brown glove held at his chest, the green outfield grass behind him, a '
    'baseball game.' + ON_AIR,
    'A woman with big blonde hair in a long sleeved, high necked blue evening gown and a man in a tuxedo facing each'
    ' other a little apart across an opulent living room, a daytime soap opera.' + ON_AIR,
    'A small blue robot and a green frog building a cardboard rocket in a garage, a 1980s Saturday morning cartoon in hand'
    ' painted cel animation with flat colours.',
    'A grown female whitetail deer standing side on in a misty forest clearing at dawn, an even plain tan brown coat, '
    'a smooth rounded head with a slim tapering muzzle and two large oval ears standing up, a small white spotted '
    'fawn beside her, a nature documentary.' + ON_AIR,
    'A dated 1980s game show stage with three wooden contestant podiums painted orange and brown, rows of round light'
    ' bulbs framing the set, a host in a sparkly blue jacket standing at one side.' + ON_AIR,
    'A cowboy on horseback riding through a desert canyon in a classic western film.' + ON_AIR,
    'An aerobics class of about a dozen men and women of different ages doing an aerobics routine, every '
    'one of them in mid move with both arms raised high over the head and one knee lifted, in plain '
    't-shirts of different colours, red, yellow, white, green and pink, and loose grey sweatpants, seen '
    'from the back of a bright room with bare white walls, a 1980s exercise show.' + ON_AIR,
]
SCREENS = ['screen-%02d' % _i for _i in range(len(PROGRAMMES))]
for _name, _prompt in zip(SCREENS, PROGRAMMES):
    art(_name, _prompt, STOUT)

# The shots of each show a set can tell it is showing (shows.py). SHOTS is each packed picture's name with the art it is
# cut from: screen-<show>-<n>, and the dozen above again as the show of no one programme, screen-any-<n>.
# A shot is packed at 640 by 480: it is made at three quarters of the pixels a picture for a wall is.
TUBE = (1024, 768)
SHOTS = {'screen-%s-%d' % (shows.ANY, _i): _name for _i, _name in enumerate(SCREENS)}
for _show, _shots in shows.SHOWS.items():
    for _i, _prompt in enumerate(_shots):
        _name = 'screen-%s-%d' % (_show, _i)
        art(_name, _prompt, TUBE)
        SHOTS[_name] = _name

# Shop signs, notices and flags (signs.py): their grounds and emblems are art like any other, and each shows on the
# name and group the game gives the sign.
for _name, (_prompt, _wide, _high) in signs.ART.items():
    art(_name, _prompt, (_wide, _high))
HUNG += signs.HUNG

# Where a family album does not belong: a business shows pictures of its own kind (pools.py) in place of the family
# photographs, a clinic or a motel shows landscapes and a garage or a gym sports posters. Homes, prison cells and a photo
# studio keep the family photographs.
for _kind, _prompts in pools.POOLS.items():
    POOLS['family', _kind] = []
    for _i, _prompt in enumerate(_prompts):
        art('pool-%s-%02d' % (_kind, _i), _prompt, TALL)
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
