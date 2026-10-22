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
    ('A head and shoulders photograph of a lively white grandmother of about sixty from rural Kentucky, young for a '
     'grandmother, with a smooth full rounded face, rosy cheeks and only faint smile lines at the corners of her eyes, '
     'short permed curly silver grey hair, and big square 1980s glasses with thick chunky light brown plastic frames, '
     'in a floral blouse, the mottled backdrop behind her running off all four edges of the picture.' + PORTRAIT, TALL),
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
    ('A photograph of a young white American soldier of about twenty in 1968, framed close from the top of his chest '
     'up, bareheaded with a short army haircut, clean shaven, in a US Army khaki shirt of the Vietnam era worn open at '
     'the neck, only its open collar in the picture, a mottled grey backdrop behind him running off all four edges of '
     'the picture.' + (' Taken in black and white in the 1960s, only shades of grey from black to white,'
     ' soft light and fine film grain, the sitter and the backdrop filling the whole frame.'), TALL),
    ('A white baby of about six months with soft smooth pink baby skin and wisps of fair hair, in a white christening '
     'gown, sitting on a white blanket and smiling straight out of the frame towards the viewer.' + BABY, TALL),
    ('A white ten year old boy from rural Kentucky with a 1980s bowl haircut, straight brown hair cut in a level fringe'
     ' across his forehead, in a red and white horizontally striped t-shirt, sitting on the lowered tailgate of an old '
     'pickup truck in a farmyard on a summer afternoon with a slight shy smile and his lips together, framed from the '
     'knees up, a red barn and green fields behind him.' + SNAP, TALL),
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
    ('A white high school senior of seventeen with a young man\'s face and a strong jaw, short brown hair, in a plain '
     'grey hooded sweatshirt, stands on the grass at the edge of a high school football field at dusk, facing the '
     'viewer and smiling, seen from the front and from the waist up, an old worn brown leather football tucked under '
     'one arm against his hip. Behind him the empty wooden bleachers.' + SNAP, TALL),
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
    ('A white Kentucky family of four standing in a row on a gravel driveway a little apart from each other, all facing'
     ' the viewer, seen from a few metres away, a wood panelled station wagon parked on the gravel behind them. On the '
     'left a father of about forty five with a moustache in a short sleeved plaid shirt, beside him a mother of about '
     'forty with permed brown hair in a blue blouse, then a son of about twelve, a head shorter than his father, with '
     'shaggy brown hair over his ears, in a white t-shirt, and on the right a daughter of about ten with loose blonde '
     'hair, in a pink sweatshirt. Green lawn and trees behind the car.' + SNAP, WIDE),
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
        'An outdoor basketball hoop on a steel pole at the edge of a cracked asphalt driveway beside a Kentucky '
        'farmhouse at sunset, a white painted plywood backboard and an orange rim with a chain net, an orange '
        'basketball dropping through the net, seen from the side at rim height, the orange evening sky and dark trees '
        'behind, nobody there.' + STILL,
        'A grown man dribbling a basketball in an empty high school gym, seen from the side and framed from the waist '
        'up, crouched forward with his eyes up, his right hand pressing down on the ball at the height of his hip, the '
        'bare wall of the gym out of focus behind. He wears a solid forest green cotton tank top, one colour all over '
        'with nothing on it, and the ball is a plain orange rubber basketball with only its thin black seams.' + ACTION,
        'A grown man at the free throw line of a small high school gym, seen from behind and framed from the waist up, '
        'the orange basketball raised above his forehead in both hands, ready to shoot, the hoop and white backboard '
        'ahead of him out of focus against the bare painted cinder block end wall. He wears a solid white cotton tank '
        'top, one colour all over.' + ACTION,
        'An empty old high school gym in the late afternoon, seen from the floor near one sideline, long shafts of '
        'light from high windows lying across the polished hardwood, and one worn orange basketball resting on the '
        'floor in the middle distance, small in the picture and half in shadow.' + STILL,
        'Looking straight up from the gym floor directly under a basketball hoop: the orange rim a circle in the middle'
        ' of the picture with the white cord net hanging down from it towards the viewer, an orange basketball just '
        'dropping into the rim from above, and behind the rim the underside of an old white painted wooden backboard, '
        'all against the dark steel roof beams and caged round lamps of an old high school gym.' + STILL,
        'A low steel ball cart of the 1980s holding two rows of worn orange basketballs, each rubbed smooth with only '
        'its thin black seams, standing on the varnished maple floor at the edge of an empty old high school gym, late '
        'afternoon light from high windows lying across the boards, folded wooden bleachers behind out of focus.' + STILL,
    ],
    'tennis': [
        'The net of an empty grass tennis court at dusk, seen low from the grass beside one net post, the white net '
        'stretching away across the picture, one fuzzy yellow ball resting in the grass against the bottom of the net, '
        'long shadows over the worn grass and a white chalk line, hedges and trees beyond.' + STILL,
        'A white American man tossing the ball up to serve, seen from a low angle against a clear pale sky and framed '
        'from the chest up, his left arm raised high and the yellow ball rising above his open hand, his face turned up'
        ' towards it, his right arm lowered and out of the picture. He wears a white cotton shirt with a collar and a '
        'white towelling headband.' + ACTION,
        'A red clay tennis court in afternoon sun seen from courtside some way off, a woman in a pink tennis dress '
        'small in the middle of the picture stretching wide for a backhand, red dust rising at her feet and her long '
        'shadow across the clay, the green canvas windscreen behind the court.' + ACTION,
        'Two fuzzy yellow tennis balls lying in long natural lawn grass beside a white chalked line, seen low and '
        'close, real blades of grass and clover growing up round them, dew on the grass, the rest of the grass court '
        'soft and blurred behind.' + STILL,
        'A tennis player in white sliding on red clay to reach a low ball, seen from the far end of a long clay court '
        'beyond the net so he is small in the middle distance, a low evening sun directly behind him so the cloud of '
        'red dust round his feet glows and he shows as a dark figure against it.' + ACTION,
        'A grass tennis court at a small country club on a sunny day, seen from high in a little wooden stand at one '
        'end, looking straight down the length of the court: one player in white near the bottom of the picture at the '
        'near baseline with his back to the viewer, the net across the middle, and the other player small at the far '
        'baseline beyond it, facing him, hedges and trees round the court.' + ACTION,
    ],
    'boxing': [
        'The corner of an empty boxing ring under one hanging lamp, a red padded corner post with three red ropes '
        'running from it, a plain wooden stool and a dented tin bucket with a white towel over its rim standing on the '
        'pale canvas in the corner, everything beyond the ropes pure black darkness.' + STILL,
        'Two boxers facing each other a metre apart in a ring with their gloves raised, one in red trunks on the left '
        'facing right and one in blue trunks on the right facing left, seen exactly side on from far back at ringside '
        'level so they are small figures in profile under the bright ring lights, the empty white canvas around them '
        'and the dark empty seats beyond.' + ACTION,
        'Inside an empty boxing ring, a boxer in white trunks stands in the far corner facing the viewer with his back '
        'to the corner post, his arms down and his gloves hanging, small in the picture, seen from the opposite corner '
        'at ring level so the bare pale canvas stretches between, a single cone of spotlight from directly above on '
        'him, everything beyond the ropes pure black darkness.' + ACTION,
        'A tall stretch of bare weathered plank wall in an old gym seen from four metres away, worn wooden floorboards '
        'along the bottom of the picture, and hanging from one nail in the upper middle of the wall a pair of worn old '
        'red leather boxing gloves on their long white laces, small in the picture. The gloves hang with their palm '
        'sides towards the viewer, so each shows its padded thumb and the white laces criss crossing up the inside of '
        'its wrist, and nothing else on them.' + STILL,
        'A boxer in a grey sweatshirt hitting a black heavy punch bag in an old brick gym, his bare fists bound in '
        'white cloth hand wraps, shafts of window light, seen from the side.' + ACTION,
        'An empty boxing ring under bright lights, seen from ringside at the middle of one side, the pale canvas floor '
        'and three red ropes running straight across the picture from edge to edge, everything beyond the ring pure '
        'black darkness.' + STILL,
    ],
    'golf': [
        'A golfer walking away down an evening fairway with his bag on his shoulder, seen from far behind so he is '
        'small, a low orange sun ahead of him so he is a pure black shape against the glowing sky, his long shadow '
        'stretching back towards the viewer over the grass, dark trees either side.' + ACTION,
        'A golf green on a summer afternoon seen from a long way off across the smooth grass, a woman golfer in a white'
        ' sweater, bareheaded with her hair tied back, crouched reading a putt beside the flag, she and the flag both '
        'small near the top third of the picture, the wide empty green filling the lower two thirds, trees behind.' + ACTION,
        'A golfer in plaid trousers and a dark navy sweater swinging out of a sand bunker, seen from the side, a low '
        'late afternoon sun behind him so the spray of sand glows gold and he shows as a dark shape, the flying sand '
        'hiding his hands and the club.' + ACTION,
        'A white golf ball sitting on a wooden tee in trimmed grass, close up, a green fairway blurred behind.' + STILL,
        'A golf green with a single flag at sunrise, mist over rolling fairways and trees.' + STILL,
        'A long tree lined fairway in early morning seen from behind the tee, a golfer standing small at the bottom of '
        'the picture in his follow through after a drive, his back to the viewer and the club wrapped round behind his '
        'neck, the fairway stretching far away ahead of him.' + ACTION,
    ],
    'baseball': [
        'A baseball batter in the on deck circle beside the dugout taking a hard practice swing, seen from the side, '
        'nobody else in the picture, the chain link fence and empty green seats blurred behind. He wears a blue '
        'pinstripe uniform with a blank front, a dark blue batting helmet one colour all over and all black leather '
        'cleats, and swings a bare wooden bat showing only its wood grain from knob to end.' + ACTION,
        'A baseball pitcher in a grey uniform and a red cap mid throw on the mound, seen from the side and framed from '
        'the waist up against the dark green outfield wall, his throwing arm high behind his head with the white ball '
        'in his fingers and his gloved hand tucked in against his chest, showing only the brown leather back of the '
        'glove.' + ACTION,
        'A baseball catcher crouched behind home plate seen exactly side on from the first base side at dusk, his mitt '
        'held out towards the pitcher, his mask, chest guard and shin guards all dark green and showing only as padded '
        'shapes from the side, the low evening sun behind him.' + ACTION,
        'A wooden baseball bat and a scuffed white baseball lying on red infield dirt.' + STILL,
        'A baseball player sliding feet first into a white canvas base, seen side on at ground level, lying back on the'
        ' dirt with both legs stretched out in front of him towards the base and his hands raised, a big cloud of red '
        'dust thrown up round him, a low late sun behind him so the dust glows gold and he shows mostly as a dark shape'
        ' in a dark navy batting helmet.' + ACTION,
        'A small white baseball with red stitching lying in the dirt of a base path a hand\'s width from the edge of a '
        'full size square white canvas base, the base about five times as wide as the ball, seen low and close, the '
        'green outfield soft and blurred behind.' + STILL,
    ],
    'football': [
        'An empty high school American football field at night in thick fog, seen from behind one end zone, the '
        'floodlights at the far end glowing through the fog, the near goal post a black shape against the bright haze, '
        'the grass and white yard lines fading away into the fog.' + ACTION,
        'An American football running back running with the ball tucked under one arm, alone in open field, wearing a '
        'white helmet with a grey face mask and a matte white cotton mesh jersey hanging loose over his shoulder pads, '
        'seen from the side.' + ACTION,
        'An American football player leaping for a pass, framed from the waist up, a blazing low sun directly behind '
        'him, the figure pure black against a deep orange sunset sky with no light falling on him at all, the ball '
        'above his reaching hands.' + ACTION,
        'An old worn American football of plain brown pebbled leather with only its white laces and its seams, lying on'
        ' short green grass, seen low and close, the field soft and blurred behind.' + STILL,
        'An old blue American football helmet lying on the grass of a field at dusk, seen from behind so the smooth '
        'unbroken rounded back of the shell faces the viewer and the face mask points away, stadium lights behind.' + STILL,
        'An American football tumbling end over end high against a deep blue dusk sky, seen from below, only the ball, '
        'the sky and a dark line of treetops along the bottom edge in the picture.' + ACTION,
    ],
    'soccer': [
        'A lone soccer player seen from far off on a misty muddy training field at dusk, small in the picture, swinging'
        ' his leg to kick a ball, a dark figure against the pale grey mist, bare trees behind.' + ACTION,
        'A goalkeeper in a green jersey, black shorts and black socks diving full length through the air, seen side on '
        'from beside the pitch, his arms stretched out and the ball just beyond his fingertips, the goal out of the '
        'picture, the green pitch and dark trees beyond.' + ACTION,
        'An American high school soccer game on a grass field at the edge of a small town, seen from the top of grey '
        'aluminium bleachers, the players small figures in blue and in red spread across the pitch, the field filling '
        'the picture, green Kentucky hills and woods beyond it.' + ACTION,
        'A black and white leather soccer ball on the penalty spot of a grass pitch, the goal behind.' + STILL,
        'A black and white soccer ball resting in the back of a goal net on a muddy pitch at dusk, nobody there.' + ACTION,
        'An empty grass soccer field at a small American high school at dusk, seen from one corner along the long white'
        ' touchline, the white lines thin and straight, one small white goal far off at the end, low grey aluminium '
        'bleachers along the far side, tall wooden floodlight poles lit, woods behind.' + STILL,
    ],
    'athletics': [
        'A sprinter pushing out of the starting blocks on a running track, seen from the side. He wears a solid red '
        'cotton vest and solid red shorts, each one colour all over from neck to hem, and plain black canvas running '
        'shoes with black rubber soles and no marks of any kind.' + ACTION,
        'A high jumper arching backwards over the bar, seen from below and to the side as a pure black shape against a '
        'bright pale evening sky, a blazing low sun directly behind him, the thin bar a dark line, only the sky and the'
        ' top of the bar in the picture.' + ACTION,
        'A hurdler clearing a hurdle, seen from track level, a pure black figure against a blazing low orange sun '
        'directly behind him, the hurdle a black frame under his outstretched leg, his long shadow across the track '
        'towards the viewer.' + ACTION,
        'An empty long jump sand pit at a running track early in the morning, freshly raked yellow sand with one set of'
        ' footprints across it, seen low from the side of the pit, the red runway running straight to it with a white '
        'take off board set in it, dew on the grass beside it.' + STILL,
        'A javelin thrower in plain white seen from far off at the side of an empty field, a small figure leaning back'
        ' with the long javelin just launched and flying high above him against a pale sky.' + ACTION,
        'An empty red running track curving round a stadium at sunrise, white lane lines.' + STILL,
    ],
    'weights': [
        'A weightlifter holding a barbell overhead seen as a black silhouette against a tall bright window in a dim old'
        ' gym.' + ACTION,
        'A bodybuilder on a stage against a dark curtain, seen from the front and framed from the waist up, flexing '
        'both arms in a double biceps pose, his skin oiled under a spotlight.' + ACTION,
        'An aerobics class of about a dozen people in grey sweatshirts and grey sweatpants exercising with small '
        'dumbbells in a 1980s gym with bare painted brick walls and tall windows, seen from the back of the room, the '
        'instructor at the front facing the class.' + ACTION,
        'A bare unloaded steel barbell bar lying on a rubber gym floor, running diagonally across the picture and out '
        'of both sides, its knurled grip catching a shaft of light, chalk dust in the air.' + STILL,
        'A man in a grey t-shirt lying on a bench pressing a barbell in an old gym, seen from the side and framed from '
        'the waist up, the steel bar running across the top of the picture with both ends out of the picture, his hands'
        ' gripping it at shoulder width.' + ACTION,
        'A barbell resting on a wooden lifting platform against a red painted wall, both ends loaded the same with two '
        'big black iron plates, low light from the side catching only the polished steel bar, the plates black shapes '
        'in deep shadow.' + STILL,
    ],
    'hockey': [
        'A frozen farm pond in winter seen from far up the bank, a few men in wool sweaters playing hockey on the grey '
        'ice, small figures with thin dark sticks chasing a puck, bare trees and snow on the banks around the pond.' + ACTION,
        'A close view of an ice hockey goalie\'s head and shoulders, a moulded white fibreglass mask of the 1970s with '
        'oval eye holes and small breathing holes, his shoulders in a white jersey, the white netting behind him '
        'blurred.' + ACTION,
        'A pair of worn black leather ice hockey skates hanging by their laces from a nail on a wooden wall, a wooden '
        'stick leaning below them.' + ACTION,
        'A black rubber puck lying on rough scratched white ice, seen from ice level so only its dark rounded side '
        'shows, ice shavings around it, a red line under the ice behind it blurred.' + STILL,
        'An empty small town ice rink seen from high up at one end, the white ice with a red line across its width at '
        'the middle, a blue line either side of it, the centre circle a thin red ring with a small red dot, white ice '
        'inside it, bare white boards round the ice and wooden bench seats.' + STILL,
        'An ice hockey player in a plain yellow and black jersey with no crest skating with his stick raised in one hand after'
        ' a goal.' + ACTION,
    ],
    'bowling': [
        'A dark blue marbled bowling ball rolling away from the viewer down the middle of a long polished wooden lane, '
        'seen from just above the boards behind it, the ten white pins small in their triangle at the far end under a '
        'warm light, only darkness above them.' + ACTION,
        'Ten white bowling pins flying apart at the end of a polished lane, a dark ball among them seen side on, the'
        ' pins a tumbling blur of white, seen from lane level a few metres back.' + ACTION,
        'One white bowling pin with two red stripes round its neck standing alone on a polished wooden lane, warm'
        ' light glowing on the varnish, the dark alley behind.' + STILL,
        'A 1980s bowling alley seen from behind and above at a distance, a woman in a pink bowling shirt standing small'
        ' on the approach of one lane, about to bowl, framed so only her lane and the lanes either side are in the '
        'picture, the long polished boards stretching away to the pins in soft light.' + ACTION,
        'A row of empty bowling lanes in a retro bowling alley with neon lights over the pins.' + STILL,
        'Ten white bowling pins with red neck stripes standing in their neat triangle of four rows at the end of a'
        ' polished lane, seen from lane level.' + STILL,
    ],
    'racing': [
        'A dirt track oval at a county fair at dusk seen from the infield grass, looking at the far turn with only '
        'trees and the evening sky behind it, old two door American sedans of the 1970s, each one bright solid colour, '
        'sliding sideways through the turn with their back ends swung out and their front wheels turned the other way, '
        'a thick curtain of dust rising behind them.' + ACTION,
        'Two 1990s American stock cars, heavy full bodied two door race cars shaped like sedans with no lettering, one blue and one yellow, racing side by side on a speedway, the grandstand blurred'
        ' behind.' + ACTION,
        'A racing driver in a plain red fire suit standing beside his full bodied American stock car in the pits, his helmet under one arm.' + ACTION,
        'A chequered flag waving high against a pale sky, filling the upper part of the picture, and below it far off a'
        ' green and white full bodied 1990s American stock car crossing the line, small and blurred with speed.' + ACTION,
        'A Saturday night race on a small paved oval seen from the top of the grandstand under floodlights, a pack of '
        'heavy old American sedans in bright solid colours small in the distance.' + ACTION,
        'A full bodied 1990s American stock car with no lettering parked alone in a pit lane at dusk, the empty track behind.' + STILL,
    ],
    'pool': [
        'A man in a waistcoat leaning over a green pool table lining up a shot on the white cue ball, seen side on, '
        'both hands on his cue: his front hand flat on the felt making a bridge and his back hand holding the butt by '
        'his hip, a hanging lamp above, the bar behind lost in darkness beyond the light of the lamp.' + ACTION,
        'A green pool table under a low hanging lamp seen from a few paces back, a loose scatter of solid coloured '
        'balls small and far apart on the felt, and one long wooden cue lying along the near cushion.' + STILL,
        'A black eight ball on the green felt of a pool table beside a corner pocket.' + STILL,
        'The moment of a break on a pool table: the triangle of fifteen coloured balls bursting apart in every '
        'direction across the green felt, blurred with motion, one white ball among them, seen from table level near '
        'the head of the table, only the felt, the cushions and the flying balls in the picture.' + ACTION,
        'A pool table under a hanging lamp in an empty dim bar, balls scattered on the felt, a cue leaning on the table.' + STILL,
        'An old pool hall with rows of green tables under hanging lamps, empty, seen from the doorway.' + STILL,
    ],
    'fieldhockey': [
        'A women\'s field hockey match on a school grass field, seen from high up and far off with the whole field in '
        'view, the players tiny figures in red and in blue all chasing one small white ball near the middle, a low wide'
        ' netted goal at each end no taller than a person, a line of trees behind.' + ACTION,
        'A woman field hockey player in a blank blue kit with no logos, plain white shoes and an unmarked wooden stick, striking the ball on a grass pitch, seen from the side.' + ACTION,
        'A low wide field hockey goal with a dark board along the bottom of its net, a white ball lying just inside it'
        ' on wet green grass at dusk, nobody there.' + ACTION,
        'The curved hooked foot of a varnished wooden field hockey stick lying on green grass, only the hook and a '
        'short length above it in the picture, beside a hard smooth white ball, perfectly round and glossy like a cue '
        'ball, the size of an orange.' + STILL,
    ],
    'hunting': [
        'A brown whitetail buck with a reddish brown coat and large antlers standing in an autumn meadow at dawn.' + WILD,
        'A wild turkey displaying its fanned tail in a woodland clearing.' + WILD,
        'A hunter in a plaid jacket and a blaze orange cap sits on a fallen log at the edge of autumn woods at dawn, '
        'seen from far off so he is small in the picture, holding a steaming tin cup in both hands, mist in the trees.' + WILD,
        'A mallard duck taking off from a misty marsh at sunrise.' + WILD,
        'A grown doe with a smooth plain tan brown coat and, a little apart from her, a small spotted fawn, standing'
        ' in tall green grass at the edge of a forest.' + WILD,
        'A white-tailed deer buck, the American deer, with forward curving antlers, standing on a ridge, a blazing low '
        'sun directly behind him, the deer and the ridge pure black against an orange sky.' + WILD,
        'A pointer hunting dog seen side on, frozen rigid on point in a field of dry grass, its nose stretched'
        ' forward, one front paw lifted and its tail held out straight behind it.' + WILD,
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
PAINTINGS = {
    "Lilly's Waterpond": ('impressionist painting of water lilies floating on a pond, reflections of willows, soft blues, greens and violets' + OIL, WIDE),
    "She's Got It": ('classical painting of a woman in a white gown reclining on a red velvet chaise longue, dark drapery behind' + OIL, BROAD),
    'Aaaaah!': ('expressionist painting of a pale lone figure in a long dark coat on a long bridge facing the viewer with both hands pressed to its cheeks and its mouth open in a scream, a swirling orange and red sky above a dark blue fjord' + OIL, TALL),
    'Abe Lincoln': ('formal 19th century portrait of Abraham Lincoln seated in a dark suit, sombre background' + OIL, TALL),
    'Apple Faced Man': ('surreal painting of a gentleman in a dark overcoat and bowler hat standing in front of a low wall, his face hidden behind a green apple floating in the air in front of it, his arms at his sides, a cloudy sky behind' + OIL, TALL),
    'Benjamin Franklin': ('formal 18th century portrait of Benjamin Franklin in a brown coat, long grey hair, dark background' + OIL, TALL),
    'Betty': ('1950s painted portrait of a smiling blonde woman with curled hair in a high necked blue cardigan, head and shoulders, warm brown background' + OIL, TALL),
    'Calvinist': ('17th century Dutch portrait of a stern old man in a black hat and a wide white ruff collar, dark background, chiaroscuro' + OIL, TALL),
    'Chromatic Accident': ('an abstract expressionist drip painting, dense tangled splatters and drips of black, rust orange, white and grey on a cream canvas' + '. The painted surface filling the whole image edge to edge.', LONGER),
    'Configuration II': ('a geometric abstract painting in the De Stijl manner: a few straight thick black lines dividing a white canvas into a handful of large clean flat areas, rectangles of pure red, blue and yellow, crisp hard edges, flat even paint' + '. The paint carried right out to all four edges and into every corner, the painted surface filling the whole image edge to edge.', SQUARE),
    'Configuration No. 10': ('a geometric abstract painting in the De Stijl manner: a few straight thick black lines running right across a white ground, dividing it into a grid of rectangles; one large rectangle between the lines painted solid blue and two small ones solid red and yellow, each colour filling its rectangle exactly up to the black lines, the rest white, flat even paint' + '. The painted surface filling the whole image edge to edge.', SQUARE),
    'Layout I': ('a geometric abstract painting in the De Stijl manner: a few straight thick black lines dividing a white canvas into a handful of large clean flat areas, a large red square and small blue and yellow rectangles, crisp hard edges, flat even paint' + '. The painted surface filling the whole image edge to edge.', SQUARE),
    'Desert Landscape': ('painting of a desert landscape, red sandstone mesas and sagebrush under a huge pale sky at dusk' + OIL, BROAD),
    'Flowers On Table': ('still life painting of a vase of mixed flowers on a table with a white cloth, dark background, Dutch golden age' + OIL, TALL),
    'George Washington': ('formal portrait of George Washington as an older man in his sixties, his long face, heavy jaw and firm closed mouth, grey powdered hair tied back, in a black velvet suit, a red curtain behind, in the manner of Gilbert Stuart' + OIL, TALL),
    'Helianthus': ('post-impressionist still life painting of sunflowers in a clay vase against a yellow wall, thick impasto' + OIL, TALL),
    'Incognito Library': ('painting of the interior of an old library, tall shelves of leather books, a fireplace, an armchair and a brass lamp, warm brown tones' + OIL, WIDE),
    'Island Beach': ('seascape painting of waves breaking on dark rocks off an island shore, turquoise sea, cloudy sky' + OIL, LONGER),
    'John Adams': ('formal 18th century portrait of John Adams in a dark coat and white cravat, grey hair, dark background' + OIL, TALL),
    'Lakeside': ('painting of a still woodland lake, green trees and their reflections, purple wildflowers on the bank, dappled light' + OIL, LONGER),
    'Lighthouse': ('painting of a white lighthouse with a red roof on a rocky headland, blue sea and sky, a wooden walkway leading to it' + OIL, TALL),
    'Man With One Ear': ('post-impressionist self portrait of a red bearded man in a fur cap and green coat, a thick white cloth bandage wrapped round his head over one ear and tied under his chin, swirling brushstrokes' + OIL, TALL),
    'Margarite': ('17th century Dutch portrait of a girl in a blue and yellow turban glancing over her shoulder, a pearl earring, her plain ochre jacket smooth and unmarked, black background' + OIL, TALL),
    'Monica Elisa': ('an ink and watercolour portrait of a woman with long black hair in a red and orange patterned robe, head and shoulders, white paper background', TALL),
    'Mount Never Rest': ('romantic landscape painting of a towering snow capped mountain above a lake and pine forest, golden light breaking through clouds' + OIL, LONGER),
    'Night With Stars': ('post-impressionist painting of a swirling starry night sky with a crescent moon over a small sleeping village of many little houses with lit windows clustered below hills, a church spire rising among them, a tall dark cypress in the foreground, thick impasto' + OIL, BROAD),
    'Nomad Above The Gloom': ('romantic painting of a lone man in a dark frock coat seen from behind, standing on a rocky peak above a sea of fog and distant mountains' + OIL, SQUARE),
    'Omega Department': ('a 1970s children\'s storybook illustration in gouache of a friendly brown bear and a badger standing side by side a little apart, pale yellow background, printed on paper', TALL),
    'Riverside': ('impressionist painting of a river bank in spring, poplars and their reflections, pink and lilac sky' + OIL, WIDE),
    'Sir Brakefire': ('17th century portrait of a gentleman with a pointed beard in a broad black hat and lace collar, dark background, chiaroscuro' + OIL, TALL),
    'Two Frame': ('small impressionist landscape painting of a country lane between flowering hedges, lilac hills beyond' + OIL, WIDE),
    'Violetta': ('impressionist painting of a woman with a parasol in a garden of pink roses, green lawn, sunlight' + OIL, WIDE),
    'Waterbeach Hills': ('landscape painting of rolling green hills and meadows under a blue sky with white clouds' + OIL, WIDE),
    'Waterfall': ('landscape painting of a waterfall dropping through autumn woods into a rocky pool' + OIL, WIDE),
    'Wide': ('classical landscape painting of a river valley with ancient trees, a shallow ford with a small wooden boat drawn up on the bank, and distant blue hills, golden evening light' + OIL, STOUT),
    'Dr. Oids': ('a framed 1980s arcade video game screen, chunky pixel art of a round yellow creature with an open mouth being chased along a corridor of a green neon maze by a pink pixel ghost, rows of small white dots in the corridors, black background, no lettering', TALL),
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
art('painting-field-purple', 'a colour field abstract painting, two large soft edged hazy rectangles, deep violet above midnight blue, on a dark plum ground, matte paint on canvas' + '. The painted surface filling the whole image edge to edge.', TALL)
HUNG.append(('Paintings|Pink and Yelllow', 'painting-field-pink'))
HUNG.append(('Paintings|Purple and Blue', 'painting-field-purple'))
art('painting-sitter', 'dark old master portrait of a pale woman in a blue silk dress, sombre background, cracked varnish' + OIL, TALL)
HUNG.append(('location_entertainment_gallery_01_72', 'painting-sitter'))
HUNG.append(('location_entertainment_gallery_01_73', 'painting-sitter'))

# Boards.
art('board-cork', 'A cork notice board seen straight on and filling the whole frame, untidily covered in pinned papers '
                  'of many sizes and colours overlapping at odd angles: torn sheets of lined paper with faint pencil '
                  'scribble, a yellowed typed letter, a folded pink flyer, a curled faded snapshot of a lake and '
                  'another of an old pickup truck, coloured push pins here and there, all seen from far enough back '
                  'that the writing is only faint grey marks. A plain photograph in even office light.', WIDE)
art('board-white', 'A white dry erase board seen straight on and filling the whole frame, faint grey smudges of old marker, a hand'
    ' drawn bar chart and a few arrows in blue and red marker and no words. A plain photograph in even office light.', BROAD)
art('board-darts', 'A closed dark wooden dartboard cabinet seen straight on, its two plain panelled doors shut, small'
                   ' brass hinges and a little brass catch. A plain photograph in even light.', BROAD)
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
OLD_GIG = (' They are placed low in the picture with their heads below the middle of the frame, and the plain backdrop'
           ' behind them carries on above their heads to the top edge. A 1960s black and white promotional photograph'
           ' filling the whole frame edge to edge, a slightly faded silver print.')
MUSIC = [
    'A four piece glam rock band in black leather jackets standing in a row a little apart in front of a dark red'
    ' curtain, four different looking people: a tall woman with a big teased blond mane, a short dark haired woman'
    ' with a fringe, a lanky man with a black shag cut and a stocky bearded man with a red mullet.' + GIG,
    'One rock and roll singer alone, a man with slick dark hair in a dark waistcoat and white shirt, sitting on a stool'
    ' against a plain dark grey backdrop, his arms folded.' + OLD_GIG,
    'Four young men in matching slim grey suits and narrow ties standing in a row a little apart, a 1960s beat group,'
    ' each plainly a different person: one tall and thin with a blond mop, one short and stocky with dark curls, one'
    ' with glasses and slicked back hair, one with a long face and a dark fringe.' + OLD_GIG,
    'A cheerful 1960s pop group of four in bright paisley shirts standing in a row a little apart against a pink'
    ' backdrop, four different looking young men: a tall one with a ginger mop, a short round faced one with dark'
    ' curls, one with round glasses and long sideburns, and a thin one with a blond fringe.' + GIG,
    'A 1970s hard rock band in denim standing in a row on a stage in front of a wall of amplifiers, four different'
    ' looking men: one with long straight black hair, one with a curly brown perm and a moustache, one bald and'
    ' bearded, and one with long blond hair and a headband.' + GIG,
    'A rock singer with shoulder length brown hair and a headband standing at a microphone stand on a dark stage, a black shirt,'
    ' a single spotlight.' + GIG,
    'A country rock guitarist with long hair, sunglasses and a fringed suede jacket standing with an electric guitar slung on his'
    ' shoulder against an orange desert sunset.' + GIG,
    'A pop singer with wavy shoulder length blond hair in a black leather jacket and white t-shirt, head and shoulders, against a'
    ' deep blue background.' + GIG,
    'Three young men in white suits and blue caps, a 1990s rhythm and blues vocal group, seen full length from a few'
    ' paces back, each man standing on his own with an arm\'s length of space between him and the next, small enough'
    ' in the frame that their caps stay below the top third, a plain dark navy studio backdrop behind and above'
    ' them.' + GIG,
    'One red haired woman singer alone, in a long white dress with long sleeves and a high neck, seen from the waist'
    ' up, a cream and rose backdrop, a 1970s folk album cover.' + GIG,
    'An elderly crooner in a black tuxedo and bow tie standing beside a vintage silver microphone on a stand, black background,'
    ' warm spotlight.' + GIG,
    'A pop singer with long curly red hair laughing, in a denim jacket over a white t-shirt, seen from the waist up, a'
    ' pale teal backdrop.' + GIG,
    'A country singer in a black hat and black shirt sitting on a stool with an acoustic guitar resting on his knee, an old barn'
    ' wall behind.' + OLD_GIG,
    'A young woman singer with short brown hair in a white t-shirt and jeans sitting on a wooden stool, seen from the'
    ' waist up, a pastel pink backdrop.' + GIG,
    'A disco singer with a big afro in a long sleeved high necked purple sequin gown under a mirror ball, purple light.' + GIG,
    'A smiling country singer in a white cowboy hat and an embroidered western shirt with an acoustic guitar slung on his'
    ' shoulder, golden sepia background.' + GIG,
    'A folk singer in a flat cap, white shirt and red tie with an acoustic guitar slung across his back, seen from the'
    ' waist up, a cream backdrop.' + GIG,
    'An iron bridge over a river in a city with one single purple balloon floating above it, nobody in sight, the top third'
    ' plain empty sky. A 1990s indie album cover photograph on colour film, a slightly faded print with fine grain.',
]
# The names the game's own posters carry, where they can be read off them. The rest hang with no lettering.
ACTS = {1: 'FRIDAY NIGHT', 2: 'THE 594s', 3: 'THE BOJANGLES', 4: 'STEAMSHIP HURRICANE', 6: 'WILL BILL GROOVY', 7: 'SOUNDS LIKE AUSTIN PARKER!',
        8: 'SOFT ONEZ', 9: 'MARIA', 10: 'TIME FOR LOVE', 11: 'TONI', 14: 'YVETTE', 15: 'HANK GILMAN'}
for _i, _prompt in enumerate(MUSIC):
    art('music-%02d' % _i, _prompt, SQUARE)
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
FILM = (' The subject stands high in the picture with every face in the upper half of the image, and the dark backdrop'
        ' carries on below it to the bottom edge. A 1993 studio photograph for a movie, the picture filling the whole'
        ' image edge to edge, a fine halftone texture and slightly muted colours.')
FILMS = {
    'Cyberkiller': ('Cyberkiller', 'Head and shoulders of a grim square jawed soldier in black body armour, a riveted'
                                   ' steel plate over the left half of his face with a round glowing blue glass eye'
                                   ' where the eye was, dark teal background.' + FILM,
                    'CYBERKILLER', 'IN THEATERS NOW'),
    'Cyberkiller 2': ('Cyberkiller 2', 'A muscular bearded man in a leather jacket and a soldier with a riveted steel plate over half'
                      ' his face standing side by side a little apart, dark teal background.' + FILM,
                      'CYBERKILLER 2', 'IN THEATERS NOW'),
    'Operation Fort Knox': ('Operation Fort Knox', 'A blond secret agent in a black tuxedo, head and shoulders, stacks of gold bars'
                            ' glowing behind him, golden light.' + FILM, 'OPERATION FORT KNOX', 'IN THEATERS NOW'),
    'PAWS': ('PAWS', 'A raccoon, a brown bear cub and a badger walking a little apart along a path in a sunny green'
                     ' forest, the animals in the upper half, a hand painted gouache illustration for a 1993 animated'
                     ' film with soft colours, the forest running off all four edges of the image.',
             'PAWS', 'IN THEATERS NOW'),
    'Dog Goblin 4': ('Dog Goblin 4', 'A huge cracked stone skull half buried in a misty moonlit wood, bare trees, black and steel'
                     ' blue.' + FILM, 'DOG GOBLIN 4', 'IN THEATERS NOW'),
    'Pleistocene Land': ('Pleistocene Land', 'A woolly mammoth with great curved tusks walking through a snowy pine forest in a'
                         ' blizzard, pale winter light.' + FILM, 'PLEISTOCENE LAND', 'IN THEATERS NOW'),
    'Dr Oids': ('Dr Oids', 'Chunky 1980s arcade pixel art of a round yellow creature with an open mouth and a pink'
                           ' pixel ghost in a green neon maze on black, the creature and the ghost in the upper half,'
                           ' the black maze running off all four edges of the image.', 'DR. OIDS', 'NOW SHOWING'),
    'The Forgotten Element': ('The Forgotten Element', 'A rugged man in a fedora and leather jacket standing beside a dark haired'
                              ' woman in a khaki shirt, a little apart, a propeller plane in a golden sunset sky behind them.' + FILM,
                              'THE FORGOTTEN ELEMENT', 'COMING SOON'),
    'Movie': ('Movie', 'A man and a woman in raincoats standing side by side a little apart under a street lamp on a'
                       ' rainy city street at night, seen full length from a little way'
                       ' back.' + FILM, 'ONE NIGHT IN LOUISVILLE', 'IN THEATERS NOW'),
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
    'A weather forecaster in a brown suit standing beside a large weather map of the United States, plain green land'
    ' and blue sea with a few big yellow suns and grey clouds drawn on it, the map clean and simple from edge to'
    ' edge.' + ON_AIR,
    'A cheerful woman in an apron standing at a stove in a studio kitchen set, a pot on the stove, a cooking show.' + ON_AIR,
    'A bearded man in a plaid shirt and safety glasses standing at a workbench in a woodwork shop beside a half finished wooden'
    ' chair, a woodworking show.' + ON_AIR,
    'A man in a fishing vest and cap sitting in a small aluminium boat on a calm lake, a fishing rod in his hands, a fishing'
    ' show.' + ON_AIR,
    'A baseball pitcher in a plain white uniform throwing from the mound, green outfield grass behind, a baseball'
    ' game seen from behind the plate.' + ON_AIR,
    'A woman with big blonde hair in a long sleeved, high necked blue evening gown and a man in a tuxedo facing each'
    ' other a little apart across an opulent living room, a daytime soap opera.' + ON_AIR,
    'A small blue robot and a green frog building a cardboard rocket in a garage, a 1980s Saturday morning cartoon in hand'
    ' painted cel animation with flat colours.',
    'A brown whitetail deer with a reddish brown summer coat standing in a misty forest clearing at dawn, a nature documentary.' + ON_AIR,
    'A dated 1980s game show stage with three wooden contestant podiums painted orange and brown, rows of round light'
    ' bulbs framing the set, a host in a sparkly blue jacket standing at one side.' + ON_AIR,
    'A cowboy on horseback riding through a desert canyon in a classic western film.' + ON_AIR,
    'An aerobics class of about a dozen people in loose t-shirts and track pants seen from the back of a bright studio, a 1980s'
    ' exercise show.' + ON_AIR,
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
