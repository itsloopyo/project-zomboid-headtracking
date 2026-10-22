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
    ('A snapshot of a young white American soldier of about twenty, bareheaded with a short army haircut, clean shaven,'
     ' in a plain pale grey t-shirt with his dog tags on a chain round his neck, sitting on a low wall of sandbags '
     'under a hot sky and smiling at the viewer, framed from the waist up, palm trees and a tin roofed hut soft behind '
     'him.' + (' Taken in black and white on 35mm film in the 1960s, a true monochrome picture in which every tone, his '
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
    ('A white young man of about twenty, a grown man with broad heavy shoulders, a thick neck, a wide square jaw and a '
     'dark shadow of stubble, short brown hair, in a plain grey hooded sweatshirt, standing in a back yard in front of '
     'a tall row of dark green trees on a summer evening, facing the viewer and smiling, seen from the front and from '
     'the waist up.' + SNAP, TALL),
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
    ('A white Kentucky family of four standing side by side in one straight line across a gravel driveway, all four '
     'the same distance from the camera with their feet level on the gravel and a small gap between each of them, all'
     ' facing the viewer, seen from a few metres away. Behind them a long wood panelled station wagon is parked along '
     'the driveway with its whole length side on to the camera, its front pointing to the left. From left to right: a'
     ' father of about forty five with a moustache in a plain short sleeved plaid shirt; a mother of about forty with '
     'permed brown hair in a blue blouse; a son of fifteen, nearly as tall as his mother, lanky, with shaggy brown hair'
     ' over his ears, in a white t-shirt; and a daughter of nine, a head shorter than her brother, with loose blonde '
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
        'An outdoor basketball hoop on a steel pole at the edge of a gravel farmyard beside a weathered red wooden barn'
        ' at sunset, a white painted plywood backboard and an orange rim with a net of hanging steel chain links, an '
        'orange basketball dropping through the chain net, seen from the side at rim height, the orange evening sky and'
        ' dark trees behind, nobody there.' + STILL,
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
        'stretching away across the picture, the whole court living lawn grass, worn thin and brown in patches and '
        'uneven under the net, one fuzzy yellow ball resting in the grass against the bottom of the net a little way '
        'along from the post, long shadows over the grass, hedges and trees beyond.' + STILL,
        'A white American man tossing the ball up to serve, seen from a low angle against a clear pale sky and framed '
        'from the chest up, his left arm raised high and the yellow ball rising above his open hand, his face turned up'
        ' towards it, his right arm lowered and out of the picture. He wears a white cotton shirt with a collar and a '
        'white towelling headband.' + ACTION,
        'An empty red clay tennis court at dusk, seen from behind one baseline at head height, the flat red clay with '
        'its white painted lines stretching away, the dark net strung across the middle between two green posts, long '
        'evening shadows across the clay, a tall chain link fence round the court and dark trees beyond it under a '
        'pale orange sky.' + STILL,
        'One fuzzy yellow tennis ball lost in an overgrown verge beside a tennis court, seen low and close through the '
        'tall wild grass: thick green stems and feathery seed heads stand in front of the ball and all round it and rise'
        ' far above it, the ball half hidden among them, the chain link fence of the court soft and blurred '
        'behind.' + STILL,
        'A close low view along a white painted baseline on a red clay tennis court in evening light, a long scuffed '
        'slide mark gouged through the smooth clay across the line, a little heap of red clay pushed up at its end and '
        'one fuzzy yellow ball resting beside it, the rest of the court soft and blurred behind.' + STILL,
        'Two men playing tennis on a green asphalt court in a small town park on a hot summer afternoon, seen from just '
        'behind the near baseline at head height: the near player big in the foreground, seen from behind from the waist '
        'up as he swings his racket, a man of about thirty with short brown hair in a white t-shirt, and across the net '
        'the far player small, an older man of about fifty with grey hair in a pale blue shirt, a tall chain link fence '
        'round the court, leafy maple trees and a wooden telephone pole beyond it.' + ACTION,
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
        'An old boxing gym in the afternoon seen from the doorway, a raised ring standing in the middle of the room with '
        'plain white ropes and pale canvas, a long wooden bench along the bare brick wall, a skipping rope coiled on the '
        'bench, tall dusty windows letting long shafts of light across the floorboards, the room quiet and '
        'empty.' + STILL,
    ],
    'golf': [
        'A white golf ball resting at the very lip of the hole on a smooth putting green, seen low and close from the '
        'grass, the bottom of a plain white flagstick rising out of the cup beside it, long evening light across the '
        'close mown green, dark trees soft and blurred behind.' + STILL,
        'A wide sweep of rolling fairway on a summer afternoon seen from a hill, curving down to a green with a plain '
        'red flag on a thin pole, white sand bunkers beside the green and a small still pond in front of it, tall trees '
        'all round, the whole course quiet and empty.' + STILL,
        'A sand bunker beside a putting green on a sunny afternoon, seen low from the edge of it, the pale sand raked '
        'smooth in neat lines, one white golf ball sitting in the sand, a wooden bunker rake lying across the grass at '
        'its edge, the green and a plain red flag soft behind.' + STILL,
        'A white golf ball sitting on a wooden tee in trimmed grass, close up, a green fairway blurred behind.' + STILL,
        'A golf green with a single flag at sunrise, mist over rolling fairways and trees.' + STILL,
        'A white golf ball sitting on a close mown fairway grey with morning dew, close and sharp in the foreground, a '
        'dark green trail through the dew behind it where it rolled, and beyond it the long tree lined fairway in early '
        'morning mist stretching far away, empty.' + STILL,
    ],
    'baseball': [
        'A baseball batter taking a hard practice swing alone on the grass in front of the dugout steps, seen from the '
        'side from a few paces away, nothing in the picture but him, the grass and the dugout and the empty green seats'
        ' blurred behind. He wears a blue pinstripe uniform with a blank front, a dark blue batting helmet one colour '
        'all over and all black leather cleats, and swings a bare wooden bat showing only its wood grain from knob to '
        'end.' + ACTION,
        'The empty dugout of an old small town ball field on a summer afternoon, seen from one end of it, a long worn '
        'wooden bench along the back wall of grey painted cinder block, a row of bare wooden bats leaning against the '
        'wall, the shade of the dugout roof, and beyond the open front the bright green grass and brown dirt of the '
        'diamond in the sun.' + STILL,
        'Home plate on the dirt of a baseball diamond, seen low and close from just behind it, the white plate dusted '
        'with red dirt, the chalk lines of the batter\'s boxes either side, and beyond it the empty brown infield, the '
        'pitcher\'s mound and the green outfield under a soft evening sky.' + STILL,
        'A wooden baseball bat and a scuffed white baseball lying on red infield dirt.' + STILL,
        'A small town ball field seen from high in the empty wooden stands on a summer evening, the brown dirt infield '
        'and its three white bases, the pitcher\'s mound, the wide green outfield and a plain dark green board fence '
        'round it, the tall lights on wooden poles just coming on, trees and a pink sky beyond, the field empty and '
        'freshly raked.' + STILL,
        'A scuffed white baseball lying on its own in the red dirt of an infield beside a white chalk line, seen low and '
        'close, its two white leather panels joined by one single continuous seam of red stitches that winds round the '
        'ball in a figure eight, the way the seam runs on a real baseball, footprints in the dirt round it, the green outfield soft and '
        'blurred behind.' + STILL,
    ],
    'football': [
        'An empty high school American football field at night in thick fog, seen from behind one end zone, the '
        'floodlights at the far end glowing through the fog, the near goal post a black shape against the bright haze, '
        'the grass and white yard lines fading away into the fog.' + ACTION,
        'A tall yellow American football goal post standing against a glowing orange dusk sky, seen from below on the '
        'grass behind it, the thick padded base, the crossbar and the two tall uprights reaching up out of the picture,'
        ' a strip of short green grass along the bottom edge, dark treetops low on the horizon.' + STILL,
        'An old worn brown leather American football lying on a weathered grey wooden bench on the sideline of an empty'
        ' small town field at dusk, seen low and close from the end of the bench, the ball plain brown pebbled leather '
        'with its white laces, the green grass and the far goal post soft and blurred behind.' + STILL,
        'An old worn American football of plain brown pebbled leather with only its white laces and its seams, lying on'
        ' short green grass, seen low and close, the field soft and blurred behind.' + STILL,
        'A yellow penalty flag, a plain square of yellow cloth knotted round a small weight, lying on short green grass'
        ' across a white chalk yard line at dusk, seen low and close, the floodlights of a small high school field '
        'blurred to soft glowing discs behind.' + STILL,
        'The churned mud of a high school football field after a game in the rain, seen low and close, deep stud marks '
        'and torn clods of grass pressed into the dark mud, a smeared white chalk line running across it, puddles '
        'shining, the floodlights blurred to soft glowing discs in the misty dark behind.' + STILL,
    ],
    'soccer': [
        'A lone soccer player seen from far off on a misty muddy training field at dusk, small in the picture, swinging'
        ' his leg to kick a ball, a dark figure against the pale grey mist, bare trees behind.' + ACTION,
        'Looking out of a soccer goal from inside it on a misty morning, the white net close in front of the viewer, '
        'and through it the empty goalmouth, the white chalk lines of the penalty area and the wide green grass of the '
        'field fading into the mist, trees faint beyond.' + STILL,
        'A small plain red corner flag on a thin white pole standing at the corner of a grass soccer field on a summer'
        ' evening, the white chalk arc curving round its foot, seen low and close, the empty field stretching away '
        'behind it in long golden light, green hills and woods beyond.' + STILL,
        'A black and white leather soccer ball on the penalty spot of a grass pitch, the goal behind.' + STILL,
        'A black and white soccer ball resting in the back of a goal net on a muddy pitch at dusk, nobody there.' + ACTION,
        'A small white soccer goal standing alone on a grass field at a small high school at dusk, seen from in front a'
        ' little way off, its net hanging still, tall wooden floodlight poles lit, low grey aluminium bleachers along '
        'one side, woods behind.' + STILL,
    ],
    'athletics': [
        'A sprinter pushing out of the starting blocks on a running track, seen from the side. He wears a solid red '
        'cotton vest and solid red shorts, each one colour all over from neck to hem, and plain black canvas running '
        'shoes with black rubber soles and no marks of any kind.' + ACTION,
        'A high jump set up on the grass of an empty school field on a summer evening, seen from the side, a thick deep'
        ' blue foam landing mat, plain one colour, and above the front of it a thin plain white bar resting across two '
        'tall upright steel stands, a pale evening sky behind and a line of trees along the bottom.' + STILL,
        'A row of white wooden hurdles standing across the lanes of an empty red running track in the evening, seen low'
        ' from the edge of the track so they line up one behind another into the distance, the white lane lines '
        'running away beside them, long shadows across the red track, trees beyond.' + STILL,
        'An empty long jump pit at dawn seen low from its far end, a long narrow rectangle of yellow sand, perfectly '
        'flat and level, combed all over in fine straight parallel grooves running from side to side like a ploughed '
        'field, a neat low concrete kerb round its edge, dew on the grass beside it, the field misty behind.' + STILL,
        'A javelin thrower in plain white seen from far off at the side of an empty field, a small figure leaning back'
        ' with the long javelin just launched and flying high above him against a pale sky.' + ACTION,
        'An empty red running track curving round a stadium at sunrise, white lane lines.' + STILL,
    ],
    'weights': [
        'A weightlifter holding a barbell overhead seen as a black silhouette against a tall bright window in a dim old'
        ' gym.' + ACTION,
        'A row of round black iron dumbbells resting in pairs on a long wooden rack against a bare painted brick wall '
        'in an old gym, each with plain round black iron heads and a knurled steel handle, the pairs getting bigger '
        'along the rack, soft window light across them and the scuffed wooden floor below.' + STILL,
        'An aerobics class of eight men and women in plain grey sweatshirts and grey sweatpants exercising with small '
        'dumbbells in two rows in an old gym with bare painted brick walls and tall windows, seen from the front of the '
        'room, the class facing the viewer, each of them a different person: in front a heavy set bald man of about '
        'fifty, a thin young woman of twenty with a blonde ponytail, a black woman of about forty with short curly hair '
        'and a tall man of thirty with a dark moustache, and behind them a grey haired woman of sixty, a stocky young man'
        ' of twenty five with a crew cut, a red haired woman of thirty five and a lean man of forty five with '
        'glasses.' + ACTION,
        'A round dented steel bowl full of soft lumps of white lifting chalk standing on a scarred wooden bench in an '
        'old gym, the bench top and the floor below it dusted white, soft window light from one side, the rest of the '
        'gym dim behind.' + STILL,
        'An empty flat padded weight bench in an old gym, seen from the side, a loaded barbell resting on the steel '
        'uprights of its rack above one end, plain round black iron plates on both ends of the bar, a bare painted '
        'brick wall behind and soft window light across the floor.' + STILL,
        'A barbell resting on a wooden lifting platform in front of a red painted wall, seen from in front with the bar'
        ' running across the picture, both ends loaded the same with big black iron plates, a bright light on the red '
        'wall behind it so the plates in front are flat black discs with no detail, only the steel bar catching a thin '
        'line of light.' + STILL,
    ],
    'hockey': [
        'A frozen farm pond in winter seen from the bank, the grey ice swept clear in a rough rectangle, two old wooden '
        'hockey sticks lying crossed on the ice with a black puck beside them, snow banked along the edges, bare trees '
        'round the pond under a pale grey sky, quiet and still.' + STILL,
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
        'An empty small old indoor ice rink under bright lights, seen from the top row of the stands, the smooth white '
        'ice with its red centre line, two blue lines and red face off circles, plain white boards round it, the goals '
        'at each end, rows of empty plain wooden seats along the sides, the whole rink quiet and still.' + STILL,
    ],
    'bowling': [
        'A dark blue marbled bowling ball rolling away from the viewer down the middle of a long polished wooden lane, '
        'seen from just above the boards behind it, the ten white pins small in their triangle at the far end under a '
        'warm light, only darkness above them.' + ACTION,
        'A row of bowling balls in deep marbled colours, red, blue, green and purple swirls, resting side by side on a '
        'curved wooden rack against a dark wood panelled wall, their three finger holes showing, warm light gleaming on '
        'their polished surfaces, seen close from a little to one side.' + STILL,
        'One white bowling pin with two red stripes round its neck standing on the polished maple boards of a lane, '
        'seen low and close, and behind it a wide dim bowling alley with many lanes side by side, their long shiny '
        'boards running away into the distance soft and out of focus, warm light glowing on the varnish.' + STILL,
        'A marbled red bowling ball sitting still on the polished maple boards of a lane\'s approach, seen low and close'
        ' from just behind it, its finger holes towards the viewer, the long lane gleaming away ahead of it into a soft '
        'warm blur, the room dim.' + STILL,
        'A row of empty bowling lanes in an old bowling alley at closing time, seen low from the approach, the long '
        'polished boards gleaming as they run away to the small white pins far off, the room dark and the area above '
        'the pins in deep shadow.' + STILL,
        'Ten white bowling pins with red neck stripes set up on the pin deck at the end of a polished lane, seen from '
        'above and in front, looking down on them at a steep angle so the whole triangle of pins shows plainly: one head '
        'pin at the point nearest the viewer, two pins in the row behind it, three in the next row and four in the back '
        'row, the triangle pointing at the viewer, the dark pit behind them in deep shadow.' + STILL,
    ],
    'racing': [
        'An empty red clay dirt track oval at a county fairground at dusk, seen from the top of the bank on the outside '
        'of a turn, the smooth banked clay curving away round the turn, fresh wheel grooves swept through it, a plain '
        'white wooden rail round the outside, floodlights on tall wooden poles just coming on, trees and a pink evening '
        'sky beyond.' + STILL,
        'The surface of a dirt race track seen low and close at the edge of a turn in the evening, deep curved wheel '
        'ruts cut through damp red clay, clods thrown up along the rim of the groove, the rest of the turn curving away '
        'soft and blurred, a haze of dust hanging in the low golden light.' + STILL,
        'A plain glossy white open face racing helmet of the 1970s, one colour all over, with a clear visor, resting on '
        'a folded plain red fire suit on a weathered wooden bench in a dim tin roofed garage, soft light from an open '
        'door, the bare plank wall behind.' + STILL,
        'A black and white chequered flag on a short wooden pole waving high against a pale evening sky, the cloth '
        'rippling and filling the upper half of the picture, below it only the soft blurred grey edge of a track and '
        'the far floodlight poles of a small speedway.' + ACTION,
        'A small paved oval race track at night under floodlights, empty, seen from the top of the grandstand, the grey '
        'asphalt banked turns shining under the lights, a low concrete wall painted plain white round the outside, the '
        'green infield grass in the middle, the dark sky above.' + STILL,
        'Black rubber skid marks curving across the grey asphalt of a turn on an empty paved oval race track, seen low '
        'from the edge of the track, a low concrete wall painted plain white along the outside of the turn, warm '
        'evening light and long shadows.' + STILL,
    ],
    'pool': [
        'A long wooden pool cue lying across the green felt of a pool table under a hanging lamp, its tip resting just '
        'behind a plain white cue ball, seen low and close from the end of the table, the warm lamp light on the felt '
        'and the dark wooden rail, the room beyond in darkness.' + STILL,
        'An empty wooden triangle rack lying on the green felt of a pool table under one hanging lamp with a single '
        'round green shade, a long wooden cue lying along the near cushion, the lamp light falling on the bare felt, the'
        ' room dark around it.' + STILL,
        'A black eight ball on the green felt of a pool table beside a corner pocket.' + STILL,
        'A wooden wall rack of plain wooden pool cues standing upright in a row against a dark wood panelled wall, warm '
        'lamp light falling across the varnished cues from one side, the corner of a green pool table soft in the '
        'foreground.' + STILL,
        'A pool table under a single hanging lamp in a dim back room of a bar with bare wood panelled walls, the green '
        'felt bare and empty, one long cue lying across the middle of it, the corners of the room in darkness.' + STILL,
        'An old pool hall at closing time seen from just inside the room, standing in the wide aisle between two rows of'
        ' green pool tables with plenty of floor between them, each table lit by one long rectangular lamp with a green'
        ' shade hanging low over its middle, the room still, quiet and dim, the far wall plain dark wood'
        ' panelling.' + STILL,
    ],
    'fieldhockey': [
        'Three wooden field hockey sticks with curved hooked feet leaning side by side against a white painted wooden '
        'fence beside a grass field, each bare varnished wood with a wrap of plain black tape round the handle, late '
        'afternoon sun, the empty green field soft behind.' + STILL,
        'An empty school field hockey pitch on a summer evening, seen from beside the halfway line, white chalk lines '
        'on mown green grass, a small low goal at each far end, long shadows across the grass and a line of trees '
        'beyond.' + STILL,
        'A low wide field hockey goal with a dark board along the bottom of its net, on wet green grass at dusk, and '
        'lying alone on the grass just inside it a plain small white hockey ball.' + STILL,
        'The curved hooked foot of a varnished wooden field hockey stick lying on green grass, only the hook and a '
        'short length above it in the picture, beside a hard smooth white ball, perfectly round and glossy like a cue '
        'ball, the size of an orange.' + STILL,
    ],
    'hunting': [
        'A whitetail buck with a small rack of antlers standing side on in a mown hayfield on an overcast autumn '
        'morning, the whole deer in view from its antlers to its hooves, short pale stubble round its hooves, seen from '
        'a little way off in plain soft daylight, a line of bare woods far behind across the field.' + WILD,
        'A wild turkey gobbler strutting in a woodland clearing in spring, seen side on from a little way off with a '
        'long lens, his tail spread in one single half circle fan behind him, his body feathers dark bronze and loose '
        'with a soft dull sheen, his bare blue and red head held high, dry brown leaves on the ground and grey tree '
        'trunks behind in soft overcast light, slightly soft focus.' + WILD,
        'A tall wooden hunting stand on four long legs at the edge of a misty autumn meadow at dawn, a ladder up one '
        'side to a small plank platform with a wooden rail round it, empty, the tall misty trees of the wood behind it '
        'and dew on the grass.' + WILD,
        'A pair of mallards swimming side by side on a still misty marsh at sunrise, a drake with a glossy green head '
        'and a mottled brown female, both seen side on from a little way off with their wings folded, their '
        'reflections in the calm water below them, tall reeds around and soft golden mist behind.' + WILD,
        'A grown doe with a smooth plain tan brown coat and, a little apart from her, a small spotted fawn, standing'
        ' in tall green grass at the edge of a forest.' + WILD,
        'A whitetail buck standing side on on a grassy ridge at sunset, the low sun in the sky well to the left of him, '
        'warm side light on his brown coat, his whole body and all four legs standing against the grass of the ridge, '
        'an orange evening sky above.' + WILD,
        'A liver and white female pointer hunting dog crouched rigid on point in thick tall golden sedge grass that rises'
        ' up to her shoulders, seen side on from a little way off, only her head, neck, level back and tail above the '
        'dense grass: her nose stretched forward towards a hidden bird and her tail held out straight behind her in one '
        'line with her back, the tall grass thick in front of her and all round her.' + WILD,
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
    'Apple Faced Man': ('a close cropped detail from the middle of a large surrealist oil painting: an orchard farmer '
                        'in faded blue overalls and a straw hat sitting on an upturned wooden crate under a laden apple '
                        'tree, his whole head one big shiny red apple with a short brown stem and a single green leaf '
                        'on top under the hat brim, a wicker basket of red apples at his feet and a wooden ladder '
                        'against the tree, warm golden afternoon light, red, green and gold, the grass running off the '
                        'bottom and the branches off the top and sides' + DETAIL, TALL),
    'Benjamin Franklin': ('a close cropped detail of the head and shoulders from a large formal 18th century oil '
                          'portrait of Benjamin Franklin in a brown coat, long grey hair, his coat running off the '
                          'bottom and the dark background off the top and sides' + DETAIL, TALL),
    'Betty': ('1950s painted portrait of a smiling blonde woman with curled hair in a high necked blue cardigan, head and shoulders, warm brown background' + OIL, TALL),
    'Calvinist': ('a close cropped detail of the head and shoulders from a large 17th century Dutch oil portrait of a '
                  'stern old man in a black hat and a wide white ruff collar, chiaroscuro, his black coat running off '
                  'the bottom and the dark background off the top and sides' + DETAIL, TALL),
    'Chromatic Accident': ('a close cropped detail from the middle of a large abstract expressionist drip painting, '
                           'dense tangled splatters and drips of black, rust orange, white and grey on cream' + ', the splatters running off every edge of the picture. Matte paint, the painted surface filling the whole image.', LONGER),
    'Configuration II': ('a geometric abstract painting in the De Stijl manner: a few straight thick black lines dividing a white canvas into a handful of large clean flat areas, rectangles of pure red, blue and yellow, crisp hard edges, flat even paint' + '. The paint carried right out to all four edges and into every corner, the painted surface filling the whole image edge to edge.', SQUARE),
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
    'George Washington': ('a close cropped detail of the head and shoulders from a large formal oil portrait of George '
                          'Washington at sixty four in the manner of Gilbert Stuart, turned three quarters to the left: '
                          'a big broad face, a long straight nose, pale grey blue eyes under heavy lids, ruddy cheeks, a '
                          'wide firmly shut mouth with the lips pressed thin and the lower face full and square over a '
                          'heavy jaw, white powdered hair brushed back from a high forehead and rolled in curls over the '
                          'ears, a white lace frill at the throat, his black velvet coat running off the bottom and a '
                          'red curtain behind him running off the top and sides' + DETAIL, TALL),
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
    'Lighthouse': ('a close cropped detail from the middle of a large oil painting of a rocky coast: a tall round white '
                   'brick lighthouse tower with a black iron lantern room on top, a white clapboard keeper\'s house and'
                   ' dark pine trees beside it on a low point of grey granite ledges, a blue sea and sky, the ledges '
                   'and the surf running out of the bottom of the picture' + DETAIL, TALL),
    'Man With One Ear': ('post-impressionist self portrait of a red bearded man in a fur cap and green coat, a thick white cloth bandage wrapped round his head over one ear and tied under his chin, swirling brushstrokes' + OIL, TALL),
    'Margarite': ('a close cropped detail of the head and shoulders from a large 17th century Dutch oil painting of a '
                  'young woman of about twenty with a round freckled face and red brown hair under a plain white linen '
                  'cap, facing straight out at the viewer with a small closed smile, a dark green bodice with a white '
                  'linen collar, a warm grey brown plaster wall behind her lit by a window on the right, her face '
                  'painted in soft blended oil brushstrokes, her bodice running off the bottom and the wall off the top '
                  'and sides' + DETAIL, TALL),
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
    'Riverside': ('a close cropped detail from the middle of a large impressionist oil painting of a river bank in '
                  'spring: a row of tall slender poplars, each a narrow upright spire of leaves, and their long '
                  'reflections in the river, a pink and lilac sky, the water running out of the bottom of the '
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
    'Dr. Oids': ('chunky 8-bit pixel art made of flat solid square pixels: a round yellow creature with an open wedge '
                 'mouth being chased along a black corridor by a pink ghost, a close crop from the middle of a maze of '
                 'thick green walls that run straight off every edge of the image, rows of small white dots along the '
                 'corridors, every pixel one flat colour with crisp hard edges', TALL),
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
art('painting-field-purple', 'a colour field abstract painting, two large soft edged hazy rectangles stacked on a dark plum'
    ' ground: the upper one a deep dark violet, nearly as dark as an aubergine, and the lower one a very dark midnight blue,'
    ' nearly black, each a smooth even veil of softly brushed matte paint' + '. The painted surface filling the whole image edge'
    ' to edge.', TALL)
HUNG.append(('Paintings|Pink and Yelllow', 'painting-field-pink'))
HUNG.append(('Paintings|Purple and Blue', 'painting-field-purple'))
art('painting-sitter', 'a close cropped detail of the head and shoulders from a large dark old master oil portrait of a pale '
    'woman in a blue silk dress against a sombre brown background, the whole surface crossed by a fine web of hair thin dark '
    'cracks in the old paint, her dress running off the bottom and the background off the top and sides' + DETAIL, TALL)
HUNG.append(('location_entertainment_gallery_01_72', 'painting-sitter'))
HUNG.append(('location_entertainment_gallery_01_73', 'painting-sitter'))

# Boards.
art('board-cork', 'A close view of the middle of a large cork notice board, the brown cork and the pinned papers '
                  'running off every edge of the picture, untidily covered in papers of many sizes and colours '
                  'overlapping at odd angles: torn sheets of blank lined paper, a yellowed sheet, a folded pink sheet '
                  'and one curled faded snapshot of a lake, each paper held by a single round red push pin. A plain '
                  'picture in even office light.', WIDE)
art('board-white', 'A close view of the middle of a white dry erase board, the glossy white surface filling the whole picture and'
    ' running off every edge, faint grey smudges of old marker, a large hand drawn circle, a few curved arrows and some wavy'
    ' lines in blue and red marker, half wiped away. A plain photograph in even office light.', BROAD)
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
    'A 1970s hard rock band in denim standing in a row a little apart on a bare wooden stage in front of a plain black'
    ' curtain, seen full length, four different looking men: one with long straight black hair, one with a curly brown'
    ' perm and a moustache, one bald and bearded, and one with long blond hair and a headband.' + LOW,
    'A rock singer with shoulder length brown hair and a headband standing full length at a microphone stand on a dark'
    ' stage, a black shirt, a single spotlight on him.' + LOW,
    'A country rock guitarist of about thirty with long brown hair, sunglasses and a fringed suede jacket, standing in'
    ' front of a plain burnt orange wall, holding one electric guitar in front of him across his body, both hands on '
    'it, its neck running out of the left edge of the picture so its end is out of view.' + BAND,
    'A pop singer of about twenty five with wavy shoulder length blond hair in a black leather jacket, white t-shirt and'
    ' jeans, sitting upright on a low wooden crate seen full length, a plain deep blue wall behind him.' + LOW,
    'Three young men in white suits with short dark hair, a 1990s rhythm and blues vocal group, all three facing the '
    'viewer, seen full length, each man standing on his own with an arm\'s length of space between him and the next, a'
    ' plain dark navy wall behind them.' + LOW,
    'One red haired woman singer of about thirty alone, with long wavy red hair, in a white dress with long sleeves and'
    ' a high neck, her hands clasped in front of her, smiling at the viewer, in front of a plain dusty rose '
    'wall.' + BAND,
    'A crooner of about seventy with silver hair in a black tuxedo and bow tie standing full length beside a vintage '
    'silver microphone on a stand, a plain black wall behind him, a warm spotlight.' + LOW,
    'A woman pop singer of about twenty five with long curly red hair laughing, in a buttoned denim jacket over a white'
    ' t-shirt tucked into jeans, sitting upright on a low stool seen full length, a plain pale teal wall behind'
    ' her.' + LOW,
    'A country singer of about thirty five in a black hat and black shirt sitting on a low stool, holding one acoustic'
    ' guitar on his knee in front of him, its neck running out of the right edge of the picture so its end is out of '
    'view, a plain weathered plank wall behind him.' + OLD_BAND,
    'A woman singer of about twenty five with short brown hair in a loose white t-shirt and jeans, sitting upright on a'
    ' low wooden stool seen full length, her knees together and her hands resting on her knees, a calm closed mouth '
    'smile, a plain pastel pink wall behind her.' + LOW,
    'A disco singer with a big afro in a long sleeved high necked purple sequin gown under a mirror ball, purple light.' + GIG,
    'A smiling country singer of about thirty in a white cowboy hat and an embroidered western shirt, holding one '
    'acoustic guitar in front of him across his chest with both hands, its neck running out of the right edge of the '
    'picture so its end is out of view, in front of a plain golden sepia wall.' + BAND,
    'A folk singer of about thirty in a flat cap, white shirt and red tie, holding one acoustic guitar in front of him'
    ' across his chest, its neck running out of the left edge of the picture so its end is out of view, in front of a '
    'plain cream wall.' + BAND,
    'A plain iron arch bridge over a river in a city on a still overcast day, one single purple balloon floating at '
    'mid height in the picture just above the bridge, open pale sky above it, nobody in sight. A 1990s indie album '
    'cover picture on colour film, slightly faded with fine grain.',
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
    'Cyberkiller': ('Cyberkiller', 'Head and shoulders of a grim square jawed cyborg soldier in smooth black modern '
                                   'tactical body armour of moulded matte plates, a smooth steel plate over the left '
                                   'half of his face with a round glowing blue glass eye where the eye was, dark teal '
                                   'background.' + FILM,
                    'CYBERKILLER', 'IN THEATERS NOW'),
    'Cyberkiller 2': ('Cyberkiller 2', 'Two grim men standing side by side a little apart, seen from the chest up: on the left a '
                      'bearded man in a brown leather jacket zipped up to the collar over a dark shirt, both arms in its '
                      'sleeves, and on the right a soldier in a dark zipped combat jacket with a riveted steel plate '
                      'covering only the left half of his face from brow to jaw, the right half his own bare face, dark '
                      'teal background.' + FILM,
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
    'Dog Goblin 4': ('Dog Goblin 4', 'A snarling goblin monster with the head of a shaggy black dog, pointed bat ears, small '
                     'yellow eyes and long crooked teeth, a latex rubber mask and fur costume from a low budget 1980s '
                     'horror film, crouching among bare trees in a dark misty wood at night, lit hard from one side by '
                     'a single warm orange light, the rest of the wood in black shadow.' + FILM, 'DOG GOBLIN 4', 'IN THEATERS NOW'),
    'Pleistocene Land': ('Pleistocene Land', 'A woolly mammoth with great curved tusks walking through a snowy pine forest in a'
                         ' blizzard, pale winter light.' + FILM, 'PLEISTOCENE LAND', 'IN THEATERS NOW'),
    'Dr Oids': ('Dr Oids', 'Chunky 8-bit pixel art made of flat solid square pixels: a close crop from the middle of a'
                           ' maze of thick green walls and wide black corridors, the walls running straight off every'
                           ' edge of the image, a round yellow creature with an open wedge mouth and a pink ghost in '
                           'the upper half, rows of small white dots along the corridors, every pixel one flat colour '
                           'with crisp hard edges.', 'DR. OIDS', 'NOW SHOWING'),
    'The Forgotten Element': ('The Forgotten Element', 'A clean shaven lean man of about thirty five with a narrow face and '
                              'short fair hair under a brown felt fedora with a pinched crown and a short brim, in a worn '
                              'brown leather flight jacket, and a few steps to his right a dark haired woman of about '
                              'thirty in a khaki shirt with her hair pinned up, the two standing well apart with open sky '
                              'between them, a propeller plane in a golden sunset sky behind them.' + FILM,
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
    'A weather forecaster in a brown suit seen from the waist up, turned half towards a big coloured weather map '
    'behind him and pointing at it, only a part of the map in the picture behind his shoulder: plain green land and '
    'blue sea with one big yellow sun and one grey cloud drawn on it.' + ON_AIR,
    'A cheerful woman of about forty in a plain red apron stirring a pot with a wooden spoon on a white stove top in a'
    ' studio kitchen set, plain wooden cupboards behind her, a cooking show.' + ON_AIR,
    'A bearded man in a plaid shirt and safety glasses standing at a workbench in a woodwork shop, holding a hand '
    'plane, beside him the bare frame of a small chair he is building: only four straight wooden legs joined near the'
    ' top by four plain straight rails, open on top like a small table frame, a woodworking show.' + ON_AIR,
    'A man in a plain tan fishing vest and a plain olive green cap of one colour sitting in a small aluminium boat on a'
    ' calm lake, a fishing rod in his hands, a fishing show.' + ON_AIR,
    'A baseball pitcher in a plain white jersey and a plain navy blue cap of one colour throwing from the mound, '
    'facing the camera in the middle of his throw, green outfield grass behind, a baseball game seen from behind the '
    'plate.' + ON_AIR,
    'A woman with big blonde hair in a long sleeved, high necked blue evening gown and a man in a tuxedo facing each'
    ' other a little apart across an opulent living room, a daytime soap opera.' + ON_AIR,
    'A small blue robot and a green frog building a cardboard rocket in a garage, a 1980s Saturday morning cartoon in hand'
    ' painted cel animation with flat colours.',
    'A grown whitetail doe with an even plain tan brown coat standing side on in a misty forest clearing at dawn, a '
    'nature documentary.' + ON_AIR,
    'A dated 1980s game show stage with three wooden contestant podiums painted orange and brown, rows of round light'
    ' bulbs framing the set, a host in a sparkly blue jacket standing at one side.' + ON_AIR,
    'A cowboy on horseback riding through a desert canyon in a classic western film.' + ON_AIR,
    'An aerobics class of about a dozen people in plain one colour t-shirts and plain solid colour track pants seen '
    'from the back of a bright studio with bare white walls, a 1980s exercise show.' + ON_AIR,
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
