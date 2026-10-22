"""What is on television: the shows a set can be showing and the shots the mod has of each.

A show is a handful of shots, each a still made from a text prompt alone (generate.py) and packed four wide for three
high as screen-<show>-<n> (pack.py). A set cuts from one shot of its show to the next and moves across each as it is
drawn (Screens.java). The shows and their names are the mod's own.

Which of the game's broadcasts and tapes is which show is told by schedule.py, from patterns kept outside the repository.
"""

# The looks never name video, tape or broadcasting: Qwen-Image draws those as the set or the cassette around the picture.
NOISE = ' soft focus with muted colours, slight colour bleed and fine noise. The picture fills the whole image edge to edge.'
VIDEO = ' A still from early 1990s American footage,' + NOISE
FIELD = ' A still from early 1990s American news footage, the view a little tilted and loose,' + NOISE
OUTDOOR = ' A still from early 1990s regional footage of country life, natural light,' + NOISE
LOCAL = ' A still from a low budget early 1990s local public access show with flat cheap lighting,' + NOISE
SITCOM = ' A still from an early 1990s American comedy series, bright and evenly lit with warm colours,' + NOISE
DRAMA = (' A still from a 1980s American drama series shot on 16mm film, soft with film grain and muted colours. The'
         ' picture fills the whole image edge to edge.')
FILM = (' A still from a 1980s movie shot on 35mm film, soft with film grain and muted colours. The picture fills the'
        ' whole image edge to edge.')
CEL = (' A still from a 1980s Saturday morning cartoon, cel animation painted by hand with flat colours, thick black'
       ' outlines and visible paint texture. The picture fills the whole image edge to edge.')
HOME = (' A still from family home footage shot around 1990, low resolution, washed out faded colours, smeared'
        ' highlights, soft focus, slight blur and tilted amateur composition. The picture fills the whole image edge'
        ' to edge.')
CLIP = ' A still from an early 1990s music video lit by old round tungsten spotlights and haze,' + NOISE
# Stage lights in a prompt come out as LED rigs: these shows ask for the coloured light alone.
HAZE = ' A still from an early 1990s music video, in coloured light and haze,' + NOISE
SPORT = ' A still from early 1990s American sports footage shot through a long lens,' + NOISE
NATURE = (' A still from a 1980s nature documentary shot on 16mm film through a long lens, soft with film grain and'
          ' natural muted colours. The picture fills the whole image edge to edge.')
ARCHIVE = (' A still from 1950s black and white newsreel footage, soft focus with heavy film grain and scratches. The'
           ' picture fills the whole image edge to edge.')
TAUGHT = ' A still from an early 1990s instructional programme with flat lighting,' + NOISE

# The shots of each show, in the order the mod numbers them.
SHOWS = {}


def show(key, look, *shots):
    if key in SHOWS:
        raise ValueError('two shows are called ' + key)
    SHOWS[key] = [shot + look for shot in shots]


# The news, and what the news shows as things get worse.
show('news-desk', VIDEO,
     'A woman news anchor in a red blazer sits at a glass news desk with her hands resting on a few sheets of paper, a blue'
     ' world map backdrop behind her.',
     'A heavy set balding male news anchor of about fifty five with a round fleshy face, jowls and thick glasses, in a'
     ' dark suit and striped tie, seen from the chest up against a plain blue wall.',
     'Two news anchors, a man and a woman, sit a little apart at a long news desk seen wide, their hands '
     'resting on a few sheets of paper, a newsroom of desks behind them with small boxy grey tube monitors '
     'on the desks showing plain pictures of fields and skies, the walls behind plain painted blue.',
     'A woman news anchor with short brown hair and an ordinary face sits at a desk looking off to one side, a large'
     ' framed photograph of a green river valley seen from the air hanging on the pale wall over her shoulder.',
     'A wide view of a large dim room with deep blue walls, a long curved wooden desk shaped like a wide '
     'arc lit warm in the middle, and one news presenter of about fifty in a dark suit sitting small '
     'behind it, papers in his hands, the walls bare.')
show('news-field', FIELD,
     'A woman news reporter of about thirty with shoulder length brown hair, in a beige trench coat, holds a plain'
     ' black microphone in front of an army roadblock of green trucks and coiled barbed wire at dusk.',
     'Seen from very far off down an empty straight rural highway between green fields, the grey road running '
     'away from the bottom of the picture to a checkpoint tiny in the distance: a row of grey concrete '
     'barriers set across the road from one verge to the other and one tiny lone soldier in plain green '
     'standing beside them, under an overcast sky.',
     'A long traffic jam of 1980s cars on a two lane highway through green farm fields, seen straight down from high in'
     ' the sky, the cars tiny.',
     'A handful of worried civilians in summer t-shirts and jeans stand behind a tall chain link fence, seen past the backs of'
     ' two soldiers in green fatigues on the near side.',
     'A woman reporter stands on a roadside at night lit from the front by one bright white lamp, the red and blue'
     ' lights of police cars blurred far behind her.')
show('pundit', VIDEO,
     'A stern heavy man of about sixty with a broad red face, a thick grey moustache and slicked grey hair, in a dark'
     ' suit, sits behind a wooden desk pointing one finger straight out of the frame towards the viewer, an American'
     ' flag and a red curtain behind him.',
     'An angry grey haired pundit of about sixty in a dark suit leans straight towards the viewer mid shout, both hands'
     ' on the desk in front of him, seen from the chest up in front of a dark red curtain, the colours muted.',
     'A wood panelled room seen wide, one grey haired man of about sixty sitting in a leather chair at a wooden desk in'
     ' the middle, a flag on a pole and red curtains behind him.',
     'A grey haired man of about sixty in a dark suit leans back in a leather chair with his two hands resting one on'
     ' the other on his stomach and a smug look, seen from the waist up, a plain red curtain filling the whole wall'
     ' behind him.')
show('general', FIELD,
     'An army general of about sixty in a green dress uniform with rows of ribbons speaks at an outdoor wooden podium'
     ' crowded with plain black microphones on short stalks, a few soldiers standing at ease in a line behind him.',
     'A calm stern army general of about sixty with a broad weathered face and short grey hair, in a green uniform and'
     ' peaked cap, seen from the chest up outdoors under a grey sky, his mouth set in a straight line.',
     'Seen from just behind a row of plain grey television cameras on tripods, every camera pointing straight ahead at'
     ' an outdoor wooden podium standing alone and unattended in the middle distance, a bank of microphones on its top,'
     ' a soft blur of reporters standing off to either side.',
     'A field command post of a few plain olive green canvas army tents pitched in a row in a summer field, '
     'their flaps tied open, two soldiers in plain green fatigues walking between the tents, a line of '
     'trees behind under a hazy sky.')
show('expert', VIDEO,
     'A professor with a grey beard and a tweed jacket sits in an armchair being interviewed in an office lined with books, his'
     ' hands resting in his lap.',
     'A scientist in a white lab coat stands at a laboratory bench with glass flasks and a microscope on it, looking'
     ' toward someone just out of the frame.',
     'A grey laboratory microscope stands on a white bench with a glass slide clipped under its lens and a rack of test tubes'
     ' beside it, seen close up.',
     'A woman doctor in a white coat with a stethoscope round her neck talks to someone just out of the frame in a'
     ' pale green hospital corridor.')
show('unrest', FIELD,
     'A heap of broken wooden pallets and crates burns in the middle of an empty city street at night, '
     'tall orange flames rising from it, dark buildings either side and smoke drifting up into the black '
     'sky, grainy dark news footage.',
     'Three grown adult protesters stand a little apart on the wide stone steps of a building with '
     'columns, each holding up one blank white square of card: an old man with a grey beard, a woman of '
     'about forty five with short permed brown hair and glasses, and a heavy man of about forty five with '
     'a moustache, all in plain solid coloured shirts and long trousers.',
     'A column of olive green canvas backed army cargo trucks of the 1980s drives down a city avenue between tall buildings, a'
     ' few people watching from the sidewalk.',
     'A smashed shop window seen close from the sidewalk at dawn, the big glass pane almost all gone from '
     'its plain painted frame, a few large jagged shards of clear glass still stuck in the corners of the '
     'frame, broken glass scattered across the pavement, bare empty shelves and an empty floor behind it, '
     'the plain brick wall round it bare, the quiet street deserted.',
     'A city seen from high above at night, looking down, several fires burning between the dark buildings and smoke rising.',
     'A woman of about forty with permed brown hair, in a plain blue cardigan, stands in an aisle inside a '
     'small town supermarket, seen from the waist up, looking at long white metal shelves beside her that '
     'stand bare and empty under flat fluorescent light.')
show('emergency', VIDEO,
     'An empty wooden desk under warm light in a dim room with deep blue walls, the chair pushed back and loose papers'
     ' left on it.',
     'A tired middle aged official with thinning grey hair, a round face and glasses, in shirt sleeves and a loosened'
     ' tie, sits at a plain desk with his forearms resting on it, a single sheet of paper lying flat on the desk, an'
     ' American flag on a pole beside him.',
     'A flat solid dark navy blue background, the same deep dark navy right out into all four corners and along '
     'every edge, and in the middle of it one simple flat solid dull gold silhouette of an eagle with its wings '
     'spread.',
     'A dark empty room with one large television camera with smooth bare grey sides on a heavy wheeled pedestal'
     ' pointing at an empty wooden chair, the chair alone in a round patch of warm light on the floor.')
show('local-desk', VIDEO,
     'A male local news anchor in a brown suit sits at a wood panelled news desk, a photograph of a small city skyline behind'
     ' him.',
     'A woman local news anchor of about forty with big permed hair, in a green blazer over a white blouse with a high'
     ' collar buttoned right up to her neck, sits at a wood panelled desk,'
     ' seen close from the chest up with a plain flat blue wall filling the whole picture behind her, the light flat'
     ' and a little harsh, the picture soft with smeared colour.',
     'Two local news anchors, a man and a woman, sit a little apart at a wood panelled desk and turn to each other smiling,'
     ' plain blue panels behind them.',
     'A cheerful local weatherman of about fifty in a tan suit, seen from the waist up, stands in front of '
     'a big painted backdrop of a blue summer sky with soft white clouds that fills the whole picture '
     'behind him, one arm raised pointing up at the clouds.')
show('talk', VIDEO,
     'A woman host of about forty in a trouser suit and a male guest of about fifty sit in two armchairs facing one'
     ' another a little apart, a potted plant and a window onto a city at night behind them.',
     'A woman interviewer of about forty with short hair, in a dark blazer, listens with her chin resting on her hand,'
     ' seen from the shoulders up against a plain dark blue wall.',
     'A thin clean shaven man of about fifty with short grey hair and a long narrow face, in a corduroy jacket, sits in'
     ' an armchair talking with one open hand raised, his eyes on someone just out of the frame to the left, a plain'
     ' dark blue wall behind him.',
     'A cosy room seen wide, two people small in armchairs beside a low table, a city skyline at night in the window'
     ' behind them.')
show('local-field', FIELD,
     'A male reporter of about thirty five in a windbreaker stands on a rural road holding a bare plain black hand'
     ' microphone, a strip of plain yellow tape stretched across the road behind his back between green summer fields,'
     ' plain white sedans parked side on further behind.',
     'A quiet rural crossroads in green Kentucky farmland on a summer afternoon seen from a little way off, an '
     'empty two lane road, a small white farmhouse under shade trees, and a handful of grown men and women in '
     'summer clothes standing far off at the edge of the road, small figures looking at something out of the '
     'picture.',
     'A dark country road at night seen through a car windshield, the headlights picking out ordinary 1980s family cars'
     ' left askew across the verge with their doors hanging open, as if their drivers ran.',
     'Seen from far off across a wide open field at dusk, a scattered group of six grown men and women, '
     'tiny dark figures spread out across the width of the field, run away towards a line of dark trees, '
     'the long grass in the foreground blurred, the picture shaky and blurred.',
     'A frightened male reporter of about forty with thinning sandy hair and a crumpled suit, seen close from the'
     ' chest up at night, holding a plain black microphone, looking back over his shoulder with wide eyes, his face'
     ' lit from one side by a harsh white light, deep darkness behind him.')
show('statement', VIDEO,
     'A man of about fifty five in a dark suit speaks at a plain wooden podium in front of a blue curtain, two American'
     ' flags on poles beside it.',
     'Seen from the speaker\'s podium at the front of a wide press room, rows of seated reporters in suits all face'
     ' towards us, the reporters small, a few with one hand raised to ask a question, the plain bare back wall of the'
     ' room behind the last row.',
     'A white government building with columns lit up at night.',
     'A serious woman of about fifty with short brown hair going grey, glasses and a plain round ordinary '
     'face, in a navy blazer over a white blouse, speaks into a cluster of plain black microphones, seen '
     'from the chest up against a plain blue wall.')

# Home and hobbies.
show('cook', VIDEO,
     'A cheerful woman in an apron stands at the stove of a studio kitchen, stirring a pot with a wooden spoon.',
     'A glass baking dish of golden brown peach cobbler just out of the oven, sitting on a folded red and white'
     ' checked dish towel on a worn yellow kitchen counter, seen from a little above, the light flat and a little'
     ' harsh, the colours dull.',
     'A big glossy brown pot roast of beef sitting in dark gravy in a round black cast iron pan, surrounded by soft'
     ' browned roast potatoes and carrots glistening with gravy, the pan standing on a wooden board on a tiled kitchen'
     ' counter, seen close from the side so the black rim of the pan runs off both sides of the picture.',
     'A cheerful woman in an apron stands behind a kitchen counter smiling straight out of the frame towards the'
     ' viewer, a mixing bowl and a whisk on the counter in front of her.',
     'A pan of vegetables steams on a gas stove with a wooden spoon resting in it, seen close up.',
     'A tall round layer cake covered all over in thick smooth white buttercream icing with piped rosettes round its top'
     ' edge sits on a cake stand on the counter of a studio kitchen.')
show('woodwork', VIDEO,
     'A long pine board lying flat along the top of a sturdy wooden workbench in a home workshop, its '
     'freshly sawn end pale and clean, a little heap of pale sawdust on the floor below it, a plain painted '
     'wall behind, seen from a few paces back in warm light.',
     'A small heap of fresh pale curled wood shavings lying on a pine workbench beside a smooth block of '
     'newly planed pine, seen close up in warm light.',
     'In a home workshop with a wooden workbench and sawdust on the floor, a bearded man of about forty '
     'five in a plaid shirt stands proudly beside a finished wooden bookcase, a plain painted wall behind '
     'him and a few pine boards lying on the workbench.',
     'A pegboard wall, a brown board full of small round holes, over a workbench, with a claw hammer, a screwdriver'
     ' and a wooden handled hand saw hung well apart on metal hooks.',
     'A newly made small step stool of honey coloured pine with four splayed legs stands on the '
     'sawdusty floor of a home workshop beside a wooden workbench, seen from a few paces back in warm '
     'light.',
     'A bearded man of about forty five in a plaid shirt bends low at the end of a workbench with one eye '
     'right at the end of a long pine board, sighting down its length to check it is straight.')
show('survival', FIELD,
     'A rugged man in a khaki shirt and a bandana crouches by a small campfire in a forest, looking straight out of'
     ' the frame towards the viewer as he speaks.',
     'A rugged man of about forty in a plain khaki shirt kneels beside a river with a trout laid on a flat rock in'
     ' front of him.',
     'A lean to shelter of branches and leaves stands in a forest clearing.',
     'A small ring of round grey stones on the bare forest floor with a neat tepee of dry sticks and '
     'twigs built inside it ready to light, a heap of dry pine needles and pine cones beside it, seen close '
     'at ground level, ferns and tree trunks soft behind.',
     'A rugged man in a khaki shirt walks through tall ferns and looks back over his shoulder towards the viewer.',
     'A small campfire burning low in a ring of grey stones on the floor of a dark pine forest at night, its orange'
     ' glow lighting the nearest tree trunks, everything beyond them deep black, the picture dark, soft and grainy.')
show('advert', VIDEO,
     'A man of about fifty with thick dark brown hair just greying at the temples and a firm face, in a red knitted'
     ' sweater, and a woman of about forty five with permed hair in a blue sweater stand an arm\'s length apart behind'
     ' a kitchen counter with a big white mixing bowl and a plate of buttered toast on it,'
     ' with mild polite expressions, a plain painted kitchen wall behind them, the light flat and a little harsh and'
     ' the colours smeared.',
     'A ripe tomato cut into neat slices lies on a white cutting board beside a kitchen knife on a studio counter, seen close'
     ' up.',
     'A brown ceramic pot of beef stew steaming on a kitchen counter, a wooden spoon resting beside it, in a food'
     ' commercial.',
     'A studio audience in rows, men and women of all ages, some grey haired, some in glasses, some balding,'
     ' applauding, seen from the stage across the dim studio, all of them soft and out of focus.',
     'A smiling presenter in a plain navy sweater stands behind a kitchen counter with a big glass bowl of fruit salad'
     ' on it, leaning toward it.',
     'A cosy living room lit by a bright ceiling lamp, a mother and a father sitting a little apart on a sofa.')
show('doctor', VIDEO,
     'An elderly doctor in a white coat sits on the edge of a desk in a consulting room, looking straight out of the'
     ' frame towards the viewer as he speaks.',
     'A smiling elderly couple walk arm in arm along a path through a sunny park.',
     'An elderly doctor in a white coat stands beside a wooden desk in an old fashioned consulting room with'
     ' bookshelves behind him, the desk bare but for a green lamp and a small brown pill bottle.')
show('bear', VIDEO,
     'A plush teddy bear in a red bow tie sits on a child\'s bed with a patchwork quilt in a sunny bedroom, seen from'
     ' across the room, the picture soft, flat and slightly smeared, in a toy commercial.',
     'Two small children sit on a playroom carpet with a big plush teddy bear between them in a toy commercial, a girl'
     ' of about five with pigtails in a plain yellow t-shirt that is one solid colour all over and a boy of about four'
     ' with a bowl haircut in a plain red t-shirt that is one solid colour all over.',
     'A plush teddy bear sits on a picnic blanket on the grass beside one small white toy teapot and two toy cups in a toy'
     ' commercial.')
show('phone-in', LOCAL,
     'A man of about fifty five in a cardigan sits alone at a desk with a telephone and a plain white mug, seen close'
     ' from the front, a wood panelled wall behind him.',
     'A man of about fifty five in a cardigan sits alone at a desk holding a telephone receiver to his ear and nodding,'
     ' seen close from the chest up, a wood panelled wall behind him.',
     'A desk in a small wood panelled room seen from a few paces back, a beige desk telephone on it with one small red'
     ' light lit, a plain white mug beside it and an empty chair behind.',
     'A man of about fifty five in a cardigan laughs with his head thrown back at a desk, beside him on '
     'the desk an old beige rotary dial telephone with a round dial on its front and its handset resting '
     'on top in its cradle, seen close, a plain painted wall filling the picture behind him.')

# Entertainment.
show('hollywood', VIDEO,
     'A woman entertainment news presenter of about thirty five in a pink blazer with ordinary hair and make up stands'
     ' in front of a painted mural of palm trees and a pink sunset sky that fills the whole picture behind her.',
     'A film star, a man of about fifty with dark hair just greying at the temples and a firm face, in a '
     'black tuxedo and bow tie, walks alone along a red carpet at night waving, seen from the waist up, a '
     'dark blurred crowd behind him with bright white flash bursts.',
     'An empty false front street of wooden shop facades with plain painted windows, black cables lying across the'
     ' dusty ground and a canvas director\'s chair with a wooden frame standing empty in front.',
     'A handsome actor in a tuxedo and an actress in a long high necked sequin gown stand a little apart on a red carpet,'
     ' waving.',
     'The front of an old cinema at night, its marquee lit by rows of bulbs round a plain blank white panel with no letters,'
     ' a few people outside.')
show('detectives', DRAMA,
     'Three men in plain grey and brown suits and ties, two detectives of about forty and their heavy grey haired'
     ' captain of about sixty, argue across a wooden desk piled with brown paper folders and a green shaded desk lamp,'
     ' the plain cream painted wall and a tall wooden filing cabinet behind them.',
     'A tired detective of about fifty with a grey moustache, in a brown felt hat and a tan raincoat, sits behind the'
     ' wheel of a parked car at night on a stakeout, seen close through the open driver\'s window from outside, his'
     ' face lit orange by a street lamp, a plain white paper cup of coffee in his hand.',
     'A detective of about fifty in a matte tan cotton raincoat stands close beside a closed wooden door '
     'in a dry dim hallway with brown wallpaper, seen side on from the chest up, the side of his head held '
     'near the door with his ear turned to it and his eyes lowered, listening, the door and its brass knob '
     'filling the right of the picture.',
     'A suspect sits alone at a metal table in a bare grey interrogation room under one hanging lamp, seen '
     'from a few paces back, a thin man of about thirty five with short dark hair and a clean shaven face, '
     'in a plain grey long sleeved sweatshirt, his hands folded on the table, looking down.',
     'Two detectives run across a rooftop a few paces apart, the city behind them.')
show('barrister', DRAMA,
     'An English barrister in a white wig and black gown stands to speak in an oak panelled courtroom.',
     'An elderly English judge in red robes and a short white curled wig sits on the raised bench of an oak panelled'
     ' courtroom, seen from the front looking down, plain carved oak panels on the front of the bench below him and'
     ' plain dark oak panelling on the wall behind him.',
     'An elderly man in a waistcoat sits at a desk by lamplight, writing in a diary.',
     'A nervous man in his thirties in a grey suit stands indoors in an old English courtroom, inside a raised wooden'
     ' box with waist high oak panelled sides and a brass rail along its top, dark oak panelled walls and tall windows'
     ' behind him, seen from across the courtroom floor.',
     'A wet London street at night in the rain seen from across the road, one man in a dark overcoat under a black'
     ' umbrella walking past red brick terraced houses with black iron railings, puddles shining under the street'
     ' lamps.')
show('soap', VIDEO,
     'A woman of about forty with big teased blonde 1980s hair, in a dark red wool dress suit with big shoulder pads,'
     ' long sleeves and a high necked cream blouse buttoned up to her chin, points angrily at a man of about forty five in a tuxedo who stands a step away with his'
     ' arms spread, in an opulent living room in a daytime soap opera.',
     'A tearful woman with heavy eye make up and big hair, in a long sleeved high necked blouse buttoned up to her chin,'
     ' seen from the shoulders up in a daytime soap opera.',
     'A man lies in a hospital bed with a bandaged head, a woman in a fur coat sitting at the bedside, in a daytime soap'
     ' opera.',
     'A couple stand on a terrace over a city at sunset in a daytime soap opera, the woman in a long sleeved evening'
     ' gown with a high collar up to her chin and the man in a tuxedo.',
     'Inside a mansion entrance hall, a man of about forty five in a tuxedo stands at the foot of a wide carpeted'
     ' staircase with his head turned aside and one hand on his cheek, as a woman in a red dress walks up the stairs'
     ' away from him with her back to us, seen from a little way back, in a daytime soap opera.')
show('quiz', VIDEO,
     'A middle aged American game show host with a moustache and a grey side parting, in a sparkly blue jacket, holds a'
     ' long thin microphone on a dated 1980s set of orange and brown panels ringed with rows of round light bulbs.',
     'Three contestants, a bald man of about fifty, a woman of about thirty with red curls and a man of about forty'
     ' with a beard, stand a little apart behind plain blue podiums with big round red buttons, a plain dark blue'
     ' curtain filling the wall behind them.',
     'A woman contestant of about thirty five in a bright pink blouse jumps for joy with her arms in the air under'
     ' falling coloured confetti, a plain shimmering silver curtain filling the wall behind her.',
     'A new living room suite set out as a prize on a round turntable, a plump brown plaid sofa, a '
     'matching armchair and a brass floor lamp with a cream shade, in front of a plain dark curtain, '
     'coloured confetti on the floor.',
     'A television game show audience of grown men and women sitting on rows of tiered benches rising up '
     'away from the camera, all of them facing the camera, clapping and smiling, seen from the front, warm '
     'light on their faces, the top rows dim.')

# For children.
# The heroes are the mod's own: helmets with visors, green and orange, no capes, no masks with ears, no chest emblems.
OWN_HEROES = (' They are original characters in green and orange jumpsuits that are plain from collar to boots, with'
              ' round clear bubble helmets showing their faces and small backpacks.')
show('heroes', CEL,
     'A team of five space rangers stand in a row a little apart on a rooftop at night, each with a different human'
     ' face and natural skin, two men and three women.' + OWN_HEROES,
     'Only one space ranger, a man with black hair, flies alone high over the low rooftops of a small city of plain'
     ' square buildings at night, a thin trail of orange flame coming from the bottom of the small backpack on his'
     ' back, his arms stretched forward and both hands open and empty, the open night sky all round him.' + OWN_HEROES,
     'Three space rangers stand far apart in a row across a city street, all three seen exactly side on facing right'
     ' towards a giant grey robot, a tall man with black hair, a short woman with red hair and a stocky man with a'
     ' blond beard.' + OWN_HEROES,
     'A villain with a ruddy face and a thick black beard, in a long grey coat and a tall dark metal helmet open at the'
     ' face, throws his head back laughing in a dark lair with a few big simple machines, everything in greys and dull'
     ' reds.',
     'A team of space rangers stand round a glowing table in a futuristic control room.' + OWN_HEROES)
show('puppets', VIDEO,
     'A felt puppet of an orange fox with a black button nose looks out of the round window of a painted '
     'cardboard cottage, only its head and two front paws showing, its paws resting on the window sill, '
     'painted flowers round the window, in a children\'s puppet show.',
     'A smiling presenter of about thirty in dungarees over a plain yellow long sleeved shirt sits cross '
     'legged on the floor in front of a painted cardboard forest, a soft brown plush toy rabbit sitting in '
     'his lap, in a children\'s puppet show.',
     'A felt hand puppet of a hedgehog with stitched eyes and soft fabric spines sits at a tiny table with a toy tea set in a'
     ' children\'s puppet show.',
     'A felt owl puppet looks out of a hole in a cardboard tree in a children\'s puppet show.',
     'Hand puppets made of felt, a fox, an owl and a rabbit, poke up from behind a painted wooden fence with their'
     ' mouths open in song, painted forest trees behind, on a cheap 1980s children\'s show set.')
# Asked for over a watercolour background, Qwen-Image paints a patch on a sheet of bare paper.
show('bunnies', ' A still image from a 1980s television cartoon for very small children, cel animation painted by'
                ' hand: smooth flat pastel paint inside clean even dark outlines, over a soft painted background. The'
                ' picture fills the whole image edge to edge.',
     'Three round pastel cartoon bunnies of different sizes, a big pink one, a middle sized pale yellow one and a small'
     ' pale blue one, sit side by side in a meadow of flowers under a rainbow, the painted green meadow and blue sky'
     ' running right out to every edge and corner.',
     'Two round pastel cartoon bunnies build a tower of wooden toy blocks on a green lawn under a pale blue painted sky'
     ' with soft white clouds, the painted lawn and sky filling the frame right out to all four edges and corners.',
     'Three round pastel cartoon bunnies, one pink, one pale yellow and one pale blue, sit apart round a checked'
     ' picnic blanket on soft green grass.',
     'A fat round pale yellow cartoon bunny with long floppy ears hanging down and a round tummy holds a red balloon '
     'on a string on top of a green hill, a deep blue evening sky with a big round orange setting sun behind it, the '
     'deep blue colour reaching every edge of the picture.',
     'Three round pastel cartoon bunnies of different sizes, a big pink one, a middle sized pale yellow one and a'
     ' small pale blue one, sleep side by side under one patchwork quilt in a single wide bed seen straight from the'
     ' front, under a smiling moon, a painted deep blue night sky full of stars running right out to every edge and'
     ' corner.')
show('kids-advert', VIDEO,
     'In a cosy family den with wood panelled walls, a brother of about twelve and a sister of about ten, '
     'both in long sleeved t-shirts and blue jeans, sit side by side on two chairs pulled up to a small '
     'desk, seen from behind and a little to one side, both facing a beige home computer and looking at '
     'its boxy tube monitor, which shows a plain bright blue screen, in a commercial.',
     'A loud salesman in a jacket of big bold red and yellow checks spreads his arms wide in a store aisle between'
     ' tall shelves of plain cardboard boxes, seen from a little way back, in a commercial.',
     'A shiny silver plastic toy robot with a smooth plain silver chest, square head and round red eyes stands on a'
     ' kitchen table beside three loose plain silver batteries, in a toy commercial.',
     'A mother of about thirty eight with short permed hair, fine lines round her eyes and a wedding ring, a father of'
     ' about forty and a girl of about eight in plain sweaters sit on a living room carpet round a'
     ' tall tower of plain unpainted wooden toy blocks they are building together, all three laughing as it wobbles,'
     ' in a commercial.')

# Public access.
show('tech', LOCAL,
     'A man of about twenty eight with glasses and short brown hair, in a plain short sleeved shirt open at the neck,'
     ' sits at a desk beside one beige computer with one boxy monitor, turned towards the viewer and talking, a few'
     ' loose green circuit boards lying on the desk, a plain brown curtain behind him.',
     'An old plain beige computer mouse with a long grey cord lying on a plain blue mouse mat on a wooden '
     'desk, seen close up from a little above.',
     'A dim bedroom at night seen from the doorway, a soft green glow from a computer screen out of the '
     'picture to one side falling across an empty wooden desk chair and a plain white mug on the desk, the '
     'rest of the room in shadow.',
     'A man of about twenty eight with glasses and a short beard grins and holds up a green circuit board, a plain'
     ' brown curtain behind him.')
show('interview', LOCAL,
     'A woman interviewer of about forty in a navy trouser suit and a man of about seventy, a silver haired senator'
     ' with a lined face in a grey suit and tie, sit in two armchairs facing one another a little apart in a plain'
     ' office, a fern behind them.',
     'A silver haired senator of about seventy with a lined face in a grey suit and tie answers a question, seen close'
     ' from the chest up against a plain beige wall.',
     'A woman of about forty in a blazer, seen close from the chest up, sits in a cheap armchair looking down at the'
     ' notes in her lap, a wrinkled brown curtain filling the whole picture behind her.')
show('clown', LOCAL,
     'A clown with an orange wig and a red nose waves in front of a paper backdrop with balloons in a children\'s show.',
     'One clown with an orange wig and a red nose stands alone on the bare floor of a small room, holding up a bunch '
     'of three balloons on strings above his head, one red, one yellow and one blue, the wall behind him painted with '
     'yellow stars on a deep blue sky.',
     'A grown man clown of about forty with a broad adult face, crow\'s feet round his eyes, white face paint, an '
     'orange wig and a red nose, in a baggy polka dot suit, sits on a stool in a children\'s show with his hands '
     'resting on his knees, smiling up at a cloud of soap bubbles drifting down round him, a plain sunny yellow '
     'painted wall filling the picture behind him to every edge and corner.',
     'Small children sit cross legged on a carpet and laugh in a children\'s show, each child different: a girl of'
     ' about five with red pigtails, a chubby boy of about four with a crew cut, a thin girl of about six with dark'
     ' braids and a boy of about five with glasses, a plain painted yellow wall behind them.')
show('county', FIELD,
     'A woman presenter in a denim jacket stands by a white fence on a horse farm, rolling green hills behind her.',
     'A county fair on a summer evening, one ferris wheel lit up against the sky behind a row of striped stalls.',
     'An old man in a faded blue cotton shirt sits in a rocking chair on the porch of a wooden house, snapping green '
     'beans from a wicker basket on his lap into a tin pail at his feet, seen from the yard.',
     'A shallow country creek in low rolling wooded hills, clear water running over wide flat slabs of grey limestone'
     ' between gentle low grassy banks under leafy trees on a summer afternoon, sunlight coming through the leaves'
     ' onto the water, seen from the water\'s edge.',
     'A tall old black painted wooden tobacco barn with vertical plank siding and open vent boards standing alone at the edge'
     ' of a cut field at sunset, seen from the road.',
     'Two horses, a chestnut and a grey, graze well apart in a green pasture, short green grass in the foreground and a'
     ' white wooden fence running along the field behind them.')
show('survivors', ' A still from old footage lit by one harsh light, dim and grainy with muted colours. The'
                  ' picture fills the whole image edge to edge.',
     'An exhausted man of about forty in a dirty flannel shirt sits slumped at a bare wooden table in a dim '
     'room staring at nothing, behind him a wall of rough horizontal wooden planks with thin strips of '
     'daylight showing between them, the colours dull browns and greys like old 16mm film.',
     'A tired woman of about thirty five with a bandaged arm sits in a dim room, looking straight into the lens as she'
     ' speaks.',
     'A grown man of about thirty with short dark hair, dark stubble and a lined tired face sits hunched on an'
     ' upturned wooden crate with his arms folded, staring into a small fire of broken boards burning on the bare'
     ' concrete floor of a dim empty garage, the colours dull browns and greys like old 16mm film.',
     'Stacks of plain silver food cans and plastic water jugs stand on the floor of a dim room against a bare wall.',
     'A dim empty room in an old farmhouse with bare plank floors and peeling flowered wallpaper, an old wooden desk'
     ' against the wall with a plain wooden chair pushed back from it, grey daylight falling in from one side, the'
     ' colours dull browns and greys like old 16mm film.')

# Music videos.
show('ballad', HAZE,
     'A singer in a white suit sits at a grand piano among candles and fog.',
     'A woman in a long coat sings in the rain on an empty stone bridge at night, coloured light reflected in the wet'
     ' pavement and the dark river behind her.',
     'A couple stand in silhouette at the edge of the sea on a wide empty beach at sunset, only flat wet sand and '
     'water round them, lit by the low orange sun over the sea, a soft pink and amber haze in the air.',
     'A woman singer in a long dark velvet cloak with its deep hood up over her head, the hood falling round both '
     'sides of her face, sings into a handheld microphone in soft blue light, seen from the shoulders up, a warm '
     'glow behind her head.',
     'A single red rose lies on a dark polished wooden table top beside a burning white candle, seen from straight '
     'above, the dark polished wood filling the picture, blue light and haze.')
show('pop', CLIP,
     'Three women of about twenty five, each face different, one blonde, one black haired and one red '
     'haired, in bright colour blocked baggy jackets zipped up and long enough to hang down over their '
     'hips, and loose trousers, dance a little apart in front of a plain white wall filling the picture, '
     'caught mid jump with their arms up and knees bent.',
     'Four men in their mid twenties in denim jackets pose against a painted brick wall, one tall and black haired, one'
     ' with a blond mullet, one stocky with a goatee and one with curly red hair.',
     'A male pop singer of about twenty five with a short flat top haircut and a grown man\'s face, in a '
     'loose bright teal jacket over a plain white t-shirt and baggy black trousers, dances alone with his '
     'arms flung out and both hands empty, seen full length a little way off, coloured haze all round '
     'him.',
     'Seen from far back, a pop singer of about twenty five with big curly hair, in a loose white long '
     'sleeved blouse and high waisted trousers, stands alone in the middle of a bare floor singing into a '
     'handheld microphone, a small figure seen full length, coloured haze all round her.',
     'Seen from very far off across a big dark hall, a crowd of grown men and women in their twenties in '
     'loose long sleeved clothes dance with their arms in the air, tiny dark silhouettes against coloured '
     'haze.')
show('rock', CLIP,
     'Tall jets of orange flame shooting up into dark haze at a rock concert, seen from below, only the flames, a'
     ' few white spotlight beams slanting through the haze and the black dark above them in the picture.',
     'Seen from far back in the dark, a long haired guitarist stands alone in a single round beam of white light on a'
     ' dark stage, a small figure, his guitar a dark shape in his hands, the beam coming down from far above out of'
     ' frame.',
     'A long haired rock singer seen from behind, standing at the front edge of a stage with both arms '
     'spread wide, one flat black silhouette against a blaze of deep red light and haze, a huge dark crowd '
     'with raised arms beyond him.',
     'A male rock singer of about thirty with long hair tucked behind his bare ears, in scarves and a leather jacket,'
     ' screams into a microphone on a stand, seen from the chest up in red haze.',
     'A concert crowd seen from the front of the stage looking out over their heads, rows of faces and raised fists out'
     ' of focus in the haze, the people facing towards us.')
show('soul', CLIP,
     'A singer of about thirty in a silk shirt stands singing softly in a dim loft among many white '
     'candles standing on the floor and on low tables round him, warm dim light on his face, the dark '
     'brick walls behind him.',
     'A woman of about thirty in a long satin dress sings beside a saxophone player of about forty, the two of them in'
     ' warm amber light and soft haze against dark.',
     'A man of about thirty five sings softly in a dim room, seen close from the shoulders up, his eyes '
     'closed and his head tilted with the music, his face lit warm amber by one old round spotlight above '
     'out of frame, dark haze behind him.',
     'A couple of about thirty slow dance holding each other close, her head on his shoulder, in a dim room in front of'
     ' a tall window onto the city lights at night.')
show('rap', CLIP,
     'Two young Black men of about twenty five sit side by side on a low concrete wall beside a city basketball court'
     ' on a hazy summer afternoon, seen from a little way off, one big and heavy set with a shaved head in an oversized'
     ' denim jacket, one thin with short cropped hair in a baggy red hooded sweatshirt, both in baggy jeans, a chain'
     ' link fence behind them, the colours dull, matte and faded like old 16mm film, the picture soft and grainy.',
     'Two young men of about twenty four with young faces and short beards lean side by side against a '
     'plain brick wall under one orange street lamp at night, one in a baggy black hooded sweatshirt and '
     'one in a big plain white t-shirt, both in baggy jeans with their arms folded, the colours dull, '
     'matte and muted like old 16mm film.',
     'A young Black man of about twenty five with a short beard and a grown man\'s face, in a plain navy hooded'
     ' sweatshirt with the hood down, leans with one shoulder against an old red brick wall on a hazy summer'
     ' afternoon, looking off to one side, seen close from the chest up, the worn red bricks filling the whole'
     ' picture behind him, the colours dull, matte and muted like old 16mm film, the picture soft and grainy.',
     'Three young Black rappers of about twenty two sit and stand on the front stoop of a city row house '
     'in daylight, scowling towards the viewer with their arms crossed, all clean shaven, in oversized plain '
     'hooded sweatshirts hanging loose over baggy jeans, one red, one navy and one yellow, one with a high '
     'flat top haircut, one with a shaved head and one with short twists, thick gold chains round their '
     'necks.')
show('club', HAZE,
     'Looking steeply down from high up under the dark roof of a large dance hall, the crowd on the dance floor far '
     'below and small, a sea of heads and shoulders filling the picture from side to side, green laser beams fanning '
     'through thick smoke over them.',
     'Seen from across a dark club floor, a grown woman with long curly hair dances alone with both arms raised, a '
     'dark silhouette against thick glowing red smoke behind her, the picture very dark, blurry and grainy like a '
     'smeared frame of old video tape.',
     'A close view of a big mirror ball of hundreds of small square mirror tiles filling the middle of the '
     'picture, its tiles glinting red and white, dull red smoke all round it running out to every edge and '
     'corner, the picture dark and grainy.',
     'Glowing coloured shapes spin on a black background in early 1990s computer video art.')
show('eighties', HAZE,
     'A male singer of about thirty with a big teased blond mullet, his hair hanging down over his ears and neck, in'
     ' a wide white suit jacket with big square padded shoulders and the sleeves pushed up to the elbows over a plain'
     ' white t-shirt, sings into an old microphone on a stand in front of him, his hands at his sides, seen from the'
     ' waist up, pink and blue haze behind him, the picture soft, smeared and grainy like old video tape.',
     'A man of about thirty with short neat dark hair, in a pale pink suit jacket over a plain white t-shirt, stands'
     ' alone singing with his eyes closed and his hands at his sides, seen from the chest up, lit from above out of'
     ' frame, a dim soft greyish mauve haze behind him, the colours dull and faded.',
     'A long empty highway at dusk seen from the middle of the road, the dashed white centre line running away to'
     ' the horizon, a row of palm trees along one side as flat black silhouettes against a deep purple and pink'
     ' sky.',
     'A man of about forty with a lined face and a greying brown mullet, in a loose pale mint suit over a plain white'
     ' t-shirt, walks alone along a sandy beach at sunset with his hands in his pockets, seen side on from a little'
     ' way off, the low sun behind him and the sea beside him.',
     'A 1980s dance class of grown women of about thirty with big permed hair and headbands, each face different, in'
     ' long baggy sweatshirts over full length black tights that cover their legs from waist to ankle, with leg'
     ' warmers, dance in a wooden floored hall with a ballet barre along a plain painted wall, under the plain bright'
     ' ceiling lights of the hall, seen from across the room.')

# Sport.
show('sports-desk', VIDEO,
     'Two sports presenters of about forty five in plain navy blazers sit at a desk with a small shiny '
     'gold trophy cup with two curved handles on a short black base standing on it between them, a soft '
     'blurred picture of a stadium at night behind them.',
     'A sports presenter in a double breasted navy blazer with big lapels, a white shirt and a wide patterned tie,'
     ' with a moustache and a side parting, stands indoors beside a tall wooden desk with one hand on a pile of papers,'
     ' a plain dark blue wall behind him.',
     'A grinning sports presenter of about fifty with a moustache, in a plain navy blazer and a plain red tie, sits at'
     ' a desk with papers in front of him, a plain blue wall behind him.',
     'A stocky ballplayer of about thirty with a thick dark moustache and short dark hair, a sheen of sweat on his'
     ' forehead, smiles broadly as he talks with his eyes open, in a plain loose grey t-shirt with a white towel round'
     ' his neck, seen from the chest up in front of plain grey lockers, a plain black microphone held towards him'
     ' from the edge of the picture.')
show('baseball', SPORT,
     'A close head and shoulders shot of a baseball pitcher facing straight towards the camera, his chest and both '
     'shoulders square to us, in a plain white uniform shirt and a plain white cap, only his face, cap and shoulders '
     'in the picture, his eyes narrowed as he stares straight ahead past the camera, green outfield grass blurred '
     'behind him.',
     'Seen from far off through a long lens, a lone baseball catcher crouches in the brown dirt seen exactly side on, '
     'a small dark figure in plain grey trousers and a plain dark navy long sleeved shirt, a dark mask on his face, '
     'holding up his brown glove in front of him as a target, only him in the picture, a plain dark green wall behind'
     ' him.',
     'Looking steeply down from high above onto the outfield grass of a baseball park on a sunny afternoon, the grass '
     'mown in broad light and dark green diagonal stripes that fill the whole picture, one outfielder in plain grey '
     'standing small near the middle with his long shadow beside him.',
     'Seen from far off across the outfield, a baseball outfielder in plain grey leaps at the low padded outfield'
     ' fence, a small lone figure, the empty green grass in front.',
     'A baseball dugout seen from across the field, four players in plain grey uniforms, all bareheaded with their hair'
     ' showing, leaning on'
     ' its rail in the deep shade of its roof, one old and heavy, one tall and thin, one with a dark beard and one'
     ' young and fair, the green grass of the field in front.',
     'Seen exactly side on from far off, a lone baseball runner in a plain grey uniform and a plain dark grey cap '
     'sprints along the brown dirt base path, a small figure, his plain black shoes kicking up a little dust, only '
     'him on the empty dirt, green outfield grass blurred behind him.')
show('golf', SPORT,
     'Seen close from behind and from the waist up, a golfer of about forty in a pale blue shirt shades his eyes with'
     ' one hand as he watches his drive, a long green fairway running away ahead of him between tall trees.',
     'A golfer crouches to line up a putt on a green.',
     'A golf ball rolls toward the hole and the flag on a green.',
     'A golf fairway runs between tall pines with a lake beside it.',
     'Seen from very far away across a long empty fairway lined with tall trees, two tiny figures walk side by side, a'
     ' golfer and his caddie, the caddie carrying a plain white golf bag on his shoulder, only the two of them on the'
     ' wide green grass.')
show('poker', VIDEO,
     'Four men sit well apart round a green poker table under a low lamp, each in his own chair with space between'
     ' them: a heavy bald man of about fifty in a white shirt, a thin man of about thirty with slicked black hair in a'
     ' grey jacket, an old man of about seventy with a white beard in a cowboy hat and a red haired man of about forty'
     ' with glasses in a brown sweater, stacks of chips in front of each, seen from a little way back.',
     'Two men face each other across a green felt card table under a hanging lamp in a dim card room, short stacks of'
     ' red and white poker chips and two playing cards lying face down with plain red backs in front of each, seen'
     ' from across the room.',
     'A poker player of about fifty in sunglasses and a cowboy hat stares across a plain green table, his cards face'
     ' down in front of him.',
     'A casino dealer of about thirty five in a waistcoat stands behind a table covered in plain smooth green cloth'
     ' with playing cards laid out in front of her.',
     'A pile of red, white and blue poker chips sits in the middle of a green felt poker table under a low lamp.')
show('racing', SPORT,
     'Seen from high up in a distant grandstand with a clear open view, a bunched pack of small open wheel racing cars'
     ' in plain solid colours, several running side by side, sweeps round a steeply banked oval corner, the grey'
     ' concrete track tilted up at a steep angle like the side of a bowl toward the high outer wall.',
     'Seen from very high up and far away, a pit crew in plain white overalls swarms round a racing car painted plain'
     ' solid blue in the pit lane of a big oval track, all of them small figures.',
     'Seen from far off beside a long straight of a paved race track, one small open wheel racing car '
     'painted one solid yellow from nose to tail speeds past in a motion blur, its wheels a dark blur, a '
     'plain white concrete wall and green grass behind it.',
     'Seen from high above and far away, a starting grid of small low open wheel racing cars in plain solid colours'
     ' sits two by two on a grey paved oval track, the long grandstand along one side packed with people.',
     'Seen from far off, a racing driver in plain white overalls and plain white boots, a small figure, stands alone on'
     ' top of a podium with champagne spraying up in an arc above him, a plain blue sky filling the picture all round'
     ' him and the podium.',
     'Stock cars in plain colours race side by side round a dirt oval.')
show('skating', SPORT,
     'A skateboarder of about twenty, a grown young man with stubble, in a plain white t-shirt, jeans and a plain'
     ' black helmet, flies in mid air just above the curved top edge of a wooden skate ramp, seen side on against a'
     ' clear pale blue summer sky, both feet on his board in a matching pair of plain black canvas shoes, only him,'
     ' his board and the top of the ramp in the picture.',
     'Seen side on from a little way off, a grown young man of about twenty two with stubble and a man\'s build, in a'
     ' baggy plain long sleeved t-shirt and long shorts, rolls fast across an empty concrete plaza on a skateboard,'
     ' crouched low with his arms out for balance, the picture soft and a little faded.',
     'Seen from very far off against a pale sky, a small figure of a rider about seventeen, nearly adult, tall and'
     ' lanky, in a plain blue t-shirt and jeans on a small BMX bicycle flies through the air over a dirt jump, an old'
     ' round open face helmet on his head, the dirt track around him empty.',
     'Two grown men of about twenty five, one tall with long blond hair and a stubbly beard, one stocky with a buzz '
     'cut and a short dark beard, in plain solid coloured long sleeved t-shirts and jeans, sit side by side resting on '
     'the top edge of a wooden skate ramp with their skateboards across their knees, seen from a little way off under '
     'a pale hazy summer sky, the colours faded.')
show('boxing', ' A still image from 1970s boxing coverage shot on 16mm colour film, bright ring lights and a'
               ' faint haze, soft and grainy with faded colours. The picture fills the whole image edge to edge.',
     'Two white heavyweight boxers squaring off side on, seen close from the waist up so only their upper bodies, '
     'heads and raised gloves are in the picture, two plainly different men: one of about thirty with short dark '
     'hair and a dark moustache, one of about twenty eight with thinning fair hair and a broad red face, their '
     'gloves smooth plain dark red leather all over, the dark hazy hall blurred behind them.',
     'A close view of the face of a tired heavyweight boxer, a grown man of about thirty with a flattened '
     'nose, short dark hair and stubble, his eyebrows swollen and his face wet with sweat, his eyes looking '
     'down, only his face, neck and bare shoulders in the picture, the dark hall blurred behind him.',
     'Seen from the edge of the ring at canvas level, a bare chested heavyweight boxer, a grown man of about thirty, '
     'in black trunks lies knocked out flat on his back alone on the pale canvas with his arms spread, his whole body '
     'from head to boots side on across the picture, his gloves smooth dark red leather all over, the ropes and the '
     'dark hall behind him.',
     'A grey haired man of about sixty with a lined face, in a brown jacket over an open collared shirt, sits in a '
     'ringside seat watching the fight intently with his arms folded across his chest, seen from the chest up, the '
     'dark hall blurred behind him in a haze of smoke.',
     'Seen close from behind, a tired heavyweight boxer, a broad grown man of about thirty, sits slumped between'
     ' rounds, only his thick bare back, his shoulders and the back of his head with short dark hair in the picture, a white towel round his neck and sweat shining on his'
     ' back, the bright hazy ring lights and the dark hall blurred beyond him.')
show('old-game', ARCHIVE,
     'A baseball pitcher in a baggy pale flannel uniform and a plain cap winds up on a low dirt mound, seen side on '
     'from far off, a small lone figure with one leg raised, the wide empty outfield grass and a plain dark board '
     'fence behind him.',
     'Men in hats and white shirts cheer with their arms up in the crowded wooden stands of a 1950s ballpark, seen from'
     ' a distance, only the packed rows of people filling the picture.',
     'A 1950s baseball stadium seen from high up at the back of the top tier of the stands, looking down over rows of'
     ' seats to the small diamond far below.',
     'Two baseball players in baggy plain flannel uniforms walk side by side off the field toward the dugout, seen'
     ' from far off, the picture worn, flickering and scratched.')
show('soccer', SPORT,
     'Seen from high up and far off across a wide small town field, two grown men of about thirty play '
     'soccer, one in red and one in white, tiny figures in the middle of the wide empty green grass with '
     'a black and white ball between them, a line of trees behind.',
     'Seen from far off in front of a single white goal, a goalkeeper, a broad grown man of about thirty with a dark'
     ' moustache, in a plain green long sleeved shirt, a small figure, stands in the middle of the goal mouth hugging'
     ' one black and white ball to his chest with both arms, the empty net behind him.',
     'Two grown soccer players of about twenty five run towards us with their arms raised after a goal, seen from the'
     ' front and framed from the waist up, their faces shouting with joy, each in a plain cotton shirt of one solid'
     ' red all over, its sleeves and collar the same red.',
     'A small crowd of grown men and women in coats stands along a low wooden fence, their faces towards us, seen from'
     ' across a grass field, trees behind them.')
show('horse-race', SPORT,
     'Three race horses with jockeys in plain solid coloured silks gallop round a turn strung out in single file, each'
     ' a full length behind the one in front, seen side on.',
     'One brown race horse walks calmly along a raked dirt track, seen side on from across the track, its jockey in'
     ' plain solid red silks and a red cap sitting upright on its back, a plain smooth white saddle cloth under him, a'
     ' plain green metal starting gate small and far behind them.',
     'A close view of the head and neck of a dark brown race horse at full gallop, seen side on, its black mane'
     ' flying, its nostrils flared and its ears pricked, a plain brown leather bridle on its head, the dirt track and'
     ' the white rail blurred behind it.',
     'A long race course grandstand full of people in hats seen from far across an empty raked brown dirt track, the'
     ' white outer rail running along the foreground.')

# Films, by the kind of film.
show('film-comedy', FILM,
     'A small boy alone in a big house at Christmas stands at the top of the stairs beside a paint can hanging on a string in'
     ' a family comedy.',
     'Two clumsy burglars in woolly hats sprawl on their backs at the foot of a staircase in a family comedy.',
     'A panicking mother and father throw suitcases into the back of a wood panelled station wagon in the driveway of'
     ' their suburban house at dawn, seen from the side, in a family comedy.',
     'Two grown men of about twenty five with stubble, one tall and lanky with long dark hair and one stocky with'
     ' short dark hair and a thick dark moustache, in denim jackets, sit side by side on an old plaid couch in a'
     ' cluttered garage laughing with their heads thrown back, plain brown cardboard boxes stacked behind them, in a'
     ' buddy comedy.',
     'A bride and groom run hand in hand down the steps of a church, a few guests behind them tossing small pinches of'
     ' rice from little paper bags, in a comedy.',
     'A man in a crumpled suit sits on a curb in the rain beside a cardboard box of his things in a comedy.')
show('film-action', FILM,
     'A spy of about forty with neat dark hair, in a black tuxedo, stands at a low stone wall beside a mountain road at'
     ' dusk, seen from the waist up, looking out over a deep valley, snowy peaks behind him, in a spy thriller.',
     'A man in a leather jacket hurled through the air straight towards the camera by a blast, close in '
     'the foreground, his arms and legs flung out and his body tumbling and tilted, and far behind him in '
     'the distance a warehouse bursting into a huge ball of fire, in an action movie.',
     'A helicopter chases a speeding car along a canyon road in an action movie.',
     'A man of about thirty in a leather jacket lies face down flat on the roof of a moving train, both hands clamped'
     ' over the roof edge, his hair blown back, a wide dry American prairie rushing past, in an action movie.',
     'Seen from far below, a small jet fighter climbs steeply away into a bright blue sky as a dark swept wing '
     'silhouette, a long white vapour trail curving up behind it across the picture, white clouds far below it, in '
     'an action movie.',
     'A soldier in a sweat stained olive t-shirt and camouflage trousers walks through a jungle with a rifle slung on his'
     ' shoulder in an action movie.',
     'A man in a suit runs through a crowded railway station looking over his shoulder in a thriller.')
show('film-horror', FILM,
     'A lone woman with a flashlight walks down the aisle of a dark empty church at night, the pews in shadow, in a horror'
     ' movie.',
     'The shadow of a figure with a raised knife falls on a motel wall in a horror movie.',
     'Three tall figures in long dark hooded coats stand as solid black silhouettes in a bedroom doorway that blazes '
     'with white light behind them, their shapes flat black with no faces showing, in a horror movie.',
     'A pale man in a black cloak stands in a candle lit crypt in a horror movie.',
     'A terrified woman\'s face lit from below in the dark in a horror movie.',
     'An old one storey roadside motel under a bare tree at night with one lit window and a plain blank white board on'
     ' a post out front, in a horror movie.')
show('film-crime', FILM,
     'Four gangsters in pinstripe suits sit round a table in an Italian restaurant: a fat bald man of about sixty, a'
     ' thin grey haired man of about seventy in glasses, a broad man of about forty with a black beard and a young lean'
     ' man of about twenty five with curly hair, in a crime drama.',
     'A plain clothes detective of about forty with a lined face and dark stubble, in a brown leather jacket zipped '
     'up and jeans, stands in a city alley at night with his hands in his jacket pockets, seen from the waist up, '
     'looking off to one side under one street lamp, steam rising from a grate behind him, in a police thriller.',
     'A close view of a small heap of cut diamonds, each the size of a pea, spilling out of a little black velvet'
     ' pouch onto a dark wooden table top, lit by one hard lamp from above, the stones glinting dimly, the picture'
     ' dark, soft and heavily grainy, in a crime movie.',
     'Seen from high up and far off, a steep empty city hill street at dusk running down across the picture between'
     ' rows of pale painted wooden houses, the street lamps just lit, and one lone man in a dark coat running down the'
     ' sidewalk, a tiny figure, fog rolling in over the rooftops beyond, in a crime thriller.',
     'A private detective in a hat sits in an office striped with light through blinds in a film noir.',
     'Men in their mid twenties in bandanas and white vests over plain t-shirts stand on a street corner at dusk, one'
     ' leaning on the side of a parked car, plain brick walls behind them, in a crime drama.')
show('film-romance', FILM,
     'A man in a white dinner jacket and a woman in a hat sit at a table in a Moroccan cafe under ceiling fans in a romantic'
     ' classic.',
     'A governess of about thirty in a plain dark high necked dress with long sleeves and her hair pinned up, and a'
     ' gentleman of about forty in a dark Victorian frock coat, stand in the library of a country house, in a period'
     ' drama.',
     'A couple in 1950s clothes, a man in a grey suit and a woman in a long full skirted dress, dance side by side '
     'under black umbrellas on a rain soaked cobbled square at night, lamplight shining on the wet stones, dark stone '
     'buildings with shuttered windows around the square, in a classic musical.',
     'A couple kiss in the rain under a street lamp in a romantic movie.',
     'A woman of about thirty in a long high necked dressing gown that covers her to her ankles, with long sleeves,'
     ' sits at a small wooden writing desk against a papered wall, writing a letter by lamplight, heavy velvet curtains'
     ' drawn shut beside her, in a romantic drama.',
     'A man and a woman of about thirty laugh over two white cups of coffee at a bare table by a diner window, snow'
     ' falling outside on a quiet street, in a romantic comedy.')
show('film-western', FILM,
     'A gunslinger of about fifty with a lined weathered face, grey stubble and a thin grey moustache, in a dusty'
     ' black hat, seen close from the shoulders up, squinting into the sun in the dusty main street of a wooden town,'
     ' storefronts of bare grey weathered boards far behind him, soft and out of focus, in a western.',
     'Three riders on horseback cross a desert canyon in single file in a western.',
     'Cowboys play cards at a round wooden table in a dim wooden saloon with bare plank walls, oil lamps and a long bar'
     ' behind them, in a western.',
     'A sheriff of about fifty with a grey moustache, in a plain collarless cotton shirt and a dark waistcoat with a'
     ' small tin star pinned to it, sits on the porch of a wooden house watching the sun set over the prairie, in a'
     ' western.',
     'A plain dark red stagecoach with bare painted sides races across a dusty plain, pulled by a team of two galloping'
     ' brown horses side by side, a driver in a dark hat and long coat sitting up high on the bench at the front of'
     ' the coach holding the reins, seen side on from a distance, a cloud of dust trailing behind it, in a western.')
show('film-war', FILM,
     'A bearded sailor of about forty asleep in a narrow steel bunk in a Second World War submarine, lying on his side'
     ' with his face turned to the curved grey steel wall, a grey wool blanket over him, bare pipes just above him,'
     ' everything lit only by dim red light, in a war movie.',
     'A plain dark grey submarine with a smooth bare tower breaks the surface of a grey sea, white water pouring off'
     ' its deck, in a war movie.',
     'Seen from far off, a line of American soldiers in green fatigues and helmets, small figures, walk in single file'
     ' along a raised earth dike between flooded green rice paddies, tall jungle trees beyond under a hazy white sky,'
     ' in a Vietnam war movie.',
     'Inside the cramped grey steel control room of a submarine lit dim red, a naval officer of about forty in a plain'
     ' dark sweater wearing an old pair of headphones with plain smooth round black earcups looks up at the low'
     ' ceiling as the hull creaks, only bare pipes, valve wheels and rivets around him, in a war movie.',
     'A grey Second World War warship seen side on from far off, sailing across a choppy grey sea under a low grey'
     ' sky, white foam breaking at its bow, a thin trail of dark smoke drifting back from its funnel, in a war movie.')
show('film-future', FILM,
     'A bareheaded soldier, the right half of his face his own bare human face with skin, stubble and a normal eye,'
     ' and over the left half a riveted steel plate with a round glowing blue glass eye, stands in a dark rainy alley'
     ' lit by one dim yellow street lamp, steam rising behind him, the colours dull browns and greys with heavy film'
     ' grain, in a science fiction action movie.',
     'Seen exactly side on from far off, a small lone rider in black leather and a dark helmet speeds from left to'
     ' right along an empty desert road on a dark motorcycle at sunset, a long low plume of brown dust trailing'
     ' behind him along the road, flat scrub and distant hills, in a science fiction action movie.',
     'A figure walks through sparks and molten steel in a dark factory in a science fiction action movie.',
     'A woman of about thirty in a leather coat holding a shotgun stands in the doorway of a wrecked bar, the bare'
     ' cracked plaster walls behind her scorched and empty, broken stools on the floor, in a science fiction action'
     ' movie.',
     'A dull scorched metal robot hand and wrist rising out of a heap of grey concrete rubble, the arm '
     'buried in the broken concrete below the wrist and the fingers grasping up at a smoky dark sky, a '
     'practical model effect with gritty film grain, in a science fiction action movie.')
show('film-epic', FILM,
     'A herd of woolly mammoths crosses a snowy valley in an adventure movie.',
     'A huge shaggy dark brown cave bear rears up on its hind legs at the mouth of a rocky cave, roaring, seen from'
     ' below against a grey sky, in an adventure movie.',
     'Two people in fur coats stand looking up at a woolly mammoth in an adventure movie.',
     'An old Stone Age man of about sixty with a long grey beard and a lined weathered face, wrapped in a shaggy brown'
     ' fur cloak, sits by a small fire in a dark cave, seen from the chest up, his face lit orange by the flames, the'
     ' rough dark rock wall behind him, in an adventure movie.',
     'A wide frozen plain under a low sun with a line of tiny figures walking in an adventure movie.')
show('film-art', ' A still image from a 1970s European art film shot on 35mm film, muted colour, fog and soft film'
                 ' grain. The picture fills the whole image edge to edge.',
     'A man in a long coat walks across a flooded field in fog.',
     'A candle burns on the bare floor of an empty ruined room, a small still puddle of water on the floor '
     'beside it, and one single drop of water falling from the dark ceiling straight down into the puddle, '
     'small ripples spreading.',
     'A woman\'s face in profile beside a rain streaked window.',
     'A white horse stands in a misty meadow.',
     'A long empty corridor with peeling walls and a door open at the end.')
show('monster', ' A still image from a low budget 1980s monster movie shot on 35mm film, fog and blue night lighting,'
                ' soft with film grain. The picture fills the whole image edge to edge.',
     'The black silhouette of a werewolf, a tall man shaped figure standing upright on two legs with a shaggy wolf\'s'
     ' head, howls at a full moon on a foggy hill.',
     'A hairy grey clawed monster hand with long curved black nails reaches round the edge of a wooden door.',
     'A screaming young man of about twenty five and a screaming young woman of about twenty five stand apart with'
     ' their backs pressed to the corner of a log cabin, mouths wide open in terror.',
     'A gap torn in an old grey wooden rail fence at the edge of dark woods at night in fog, the broken '
     'ends of two planks hanging down at a slant from the posts either side of the gap, blue mist hanging '
     'between the trees beyond.',
     'A small crowd of grown men and women villagers, all adults from thirty to seventy, in long coats, '
     'each holding one burning torch high in one hand, gather in the street of an old German village at '
     'night, the bare timbered house walls lit orange by the flames.')
show('critics', VIDEO,
     'A balding man of about fifty with a grey fringe of hair in a sweater vest and a woman of about forty five in a'
     ' blazer sit side by side in red velvet seats in the front row of an old cinema balcony, the gilded rail in front'
     ' of them and the dim hall below, both with their hands resting in their laps as they argue.',
     'A balding film critic in a sweater vest shakes his head, seen from the chest up.',
     'A woman film critic in a blazer laughs, seen from the chest up.',
     'A beam of light from the small projection window high in the back wall shines out over rows of empty red cinema'
     ' seats in a dark old cinema.')
show('bad-movie', LOCAL,
     'A heaped plain white paper tub of popcorn spilling over its rim stands on the red velvet cushion of an empty'
     ' cinema seat, seen close, rows of empty red seats in front of it fading into the dark of an old cinema, a'
     ' flickering blue white glow from the screen out of the picture falling across the seat backs.',
     'A frame of a grainy black and white 1950s science fiction film, soft and scratched: a giant lizard monster with a'
     ' scaly head, a long tail and a gaping mouth towers over a small model city of cardboard buildings under a dark'
     ' sky.',
     'A frame of a soft grainy black and white 1950s science fiction film: a small toy flying saucer hangs on a thin'
     ' string over a flat desert under a pale sky.',
     'A goofy man in his forties with a thin moustache and a bow tie laughs and points up at a big cinema screen filled'
     ' by a giant grey lizard monster towering over a small model city, only the monster and the buildings on it.')

# A network's evening.
show('high-school', SITCOM,
     'High school seniors of eighteen who look like college students, young men with stubble and young '
     'women with grown up faces, in colourful early 1990s clothes and plain solid coloured shirts, talk at '
     'a row of blue school lockers in a school hallway, the tiled ceiling along the top of the picture.',
     'A weary high school teacher of about fifty with a grey moustache and glasses, in a short sleeved white shirt '
     'and a plain brown tie, stands at a tall classroom window looking out with a calm, faint, tired smile, seen '
     'side on, only his head and shoulders in the picture, soft daylight on his face, a cream classroom wall behind '
     'him.',
     'Four teenagers about seventeen, nearly adult, in plain solid coloured shirts sit in a diner booth with'
     ' milkshakes, the chrome trim, red seats and low white ceiling of the diner around them.',
     'A terrified driving instructor of about fifty with a grey moustache, in a short sleeved shirt and tie, sits'
     ' rigid in the passenger seat of a car with his eyes wide and his thin hair standing on end, gripping the'
     ' handle above the door with one hand, seen close through the open side window.',
     'A school gym decorated for a prom at night seen wide from high up in the bleachers, a dozen couples in '
     'suits and long dresses with sleeves slow dancing as small figures on the wooden floor under paper streamers '
     'and a turning mirror ball, its high ceiling of steel beams and its brick walls in view.')
show('irish', SITCOM,
     'A priest of about sixty in a black suit and white collar talks to a farmer of about fifty in a flat cap outside a'
     ' whitewashed stone cottage with bare white walls and a plain green door in a small Irish village, green hills behind under a soft grey'
     ' sky.',
     'An old grey haired housekeeper of about sixty five with a lined face, in a plain dark dress and a '
     'white apron, pours tea from a brown teapot into a cup held by a priest of about fifty in a black '
     'shirt and white collar, his hands bare, sitting at a plain wooden table, in an old fashioned '
     'farmhouse kitchen with whitewashed walls, dark wooden ceiling beams and an old black iron cooking '
     'range.',
     'Three men of about fifty in plain wool sweaters sit laughing at a small table in an Irish pub, plain pint glasses'
     ' of dark stout on the table, the low beamed ceiling and dark wood panelled walls close around them.',
     'Outdoors on a narrow country lane between grassy banks in Ireland, a crowd of white sheep fills the lane from'
     ' side to side in front of a small old car that has stopped, green fields and a grey sky beyond.',
     'Outdoors, a priest of about sixty in a black suit rides an old bicycle along a narrow country lane between low'
     ' dry stone walls, green fields and hills beyond under a soft grey Irish sky.')
show('network-news', VIDEO,
     'A distinguished grey haired news anchor in a dark suit sits at a large desk, a deep blue backdrop with a globe behind'
     ' him.',
     'Two news anchors of about fifty, a grey haired man in a dark suit and a woman with short brown hair in a navy '
     'blazer, sit side by side behind a long curved wooden news desk lit warm, seen from straight in front a little '
     'way back, both facing the viewer, a few sheets of paper on the desk, a plain deep blue wall behind them.',
     'A correspondent in an overcoat with a microphone stands in front of a domed capitol building.',
     'Seen from far off down a straight country road between green fields, two soldiers in green fatigues '
     'and steel helmets stand guard beside a red and white striped wooden pole lowered across the road, a '
     'small green canvas army tent beside it, under a grey sky.',
     'A grey haired news anchor of about sixty with a plain round face and heavy eyebrows looks grave, seen from the'
     ' chest up against a plain dark blue wall.')
show('cop-show', DRAMA,
     'One policeman alone, in mirrored sunglasses over his eyes and a brown leather jacket, walks towards the viewer'
     ' down an empty street, a car burning in a fireball far behind him, in a 1980s action series.',
     'A heavy set man in his fifties with a grey moustache, in shirt sleeves, a rumpled white business shirt and a'
     ' loosened brown tie, leans over a wooden desk piled with paper files, a typewriter and an old black telephone and'
     ' shouts, jabbing a finger at a younger man in a tweed jacket seated across the desk from him, in a dingy office'
     ' with venetian blinds and bare walls, in a 1980s action series.',
     'A dark blue 1970s muscle car flies in mid air over the crest of a hill, seen side on from far off, one wheel at'
     ' the front and one at the back of its near side, all four wheels clear of the road, in a 1980s action series.',
     'A lean detective of about thirty five with dark stubble and a grown man\'s face, in a denim jacket, presses his'
     ' back flat against a brick wall beside the corner of a warehouse on a dock at night, breathing hard, his head'
     ' turned to look round the corner, seen from the chest up, lit by one harsh lamp, in a gun fight in a 1980s'
     ' action series.',
     'Seen from far across a dark parking lot, two policemen in plain light blue short sleeved shirts, one heavy set'
     ' and balding, one young and thin with dark hair, sit on the front fender of a plain brown sedan parked side on,'
     ' paper bags of takeout between them, a low roadside diner of lit windows behind them, in a 1980s action'
     ' series.')
show('space', DRAMA,
     'Seen from inside a dim starship, a long wide window of thick glass set in a dark grey metal wall looks out on a'
     ' big blue and white planet glowing below against black space full of stars, the dark cabin in front of the'
     ' window empty, in a 1980s science fiction series.',
     'A tall thin man of about sixty with a long narrow face, playing an alien ambassador, in pale blue skin makeup'
     ' with a smooth bald domed head and long white eyebrows, in a high collared dark red robe, seen from the'
     ' shoulders up alone against a plain dark grey wall, in a 1980s science fiction series.',
     'A model starship flies past a ringed planet in a 1980s science fiction series.',
     'Three crew members in plain one colour jumpsuits, one red, one gold and one blue, each smooth and bare from'
     ' collar to boots, stand well apart on a rocky planet under a purple sky, seen from a little way off, their empty'
     ' hands hanging down at their sides, in a 1980s science fiction series.',
     'An engine room with a glowing column and an engineer at the controls in a 1980s science fiction series.')
show('action-team', DRAMA,
     'Four tough men stand in front of a black van in a desert scrapyard in a 1980s action series.',
     'A man in plain dark coveralls and an old flip up welding hood of black leather with a small rectangle of dark'
     ' green glass over his eyes holds a thick grey steel plate flat against the side door of a black van in a barn and'
     ' welds along its edge, a bright white arc and a shower of orange sparks where the plate meets the door, in a'
     ' 1980s action series.',
     'A big orange fireball and black smoke burst up from the ground beside a dirt road in the desert, an empty open'
     ' green army jeep standing on its four wheels on the road in front of it, dust flying round it, seen whole and'
     ' side on from a distance, in a 1980s action series.',
     'A grinning heavy built man in his late fifties with a bald head, a fringe of grey hair and a full '
     'bushy grey beard, in a plain open necked checked work shirt, leans against the side of a black van in '
     'a dusty desert scrapyard with his arms folded, seen from the waist up a few paces off, in a 1980s '
     'action series.',
     'Seen side on from very far away, a small dark helicopter flies low over flat desert scrub, a tiny dark shape '
     'against a pale hazy sky, distant blue mountains along the horizon, the colours dull, faded and grainy like old '
     '16mm film, in a 1980s action series.')
show('strange', DRAMA,
     'A man in a long trench coat, seen from behind, walks away alone along a narrow dirt path into thick grey fog at'
     ' night, a lone bare tree beside the path, the fog swallowing everything beyond him, in a documentary series about'
     ' the unexplained.',
     'Seen from high above in the grey light of early morning, a large perfect ring of flattened, swirled grass'
     ' pressed into the middle of a tall summer hay field in Kentucky, a dark line of trees along the far edge of the'
     ' field, in a documentary series about the unexplained.',
     'Three points of white light float high in the open black night sky in a triangle far above a dark farmhouse and '
     'its field, small and blurred like distant stars only brighter, in a documentary series about the unexplained.',
     'An old farmer of about seventy in overalls sits on his porch at night talking, a plain black microphone held up'
     ' towards him by a hand from the edge of the picture, in a documentary series about the unexplained.',
     'An old house at night with one lit window in a documentary series about the unexplained.')
show('chat', VIDEO,
     'A late night talk show host, a thin man with grey hair and glasses in a dark suit, sits behind a desk with a'
     ' city skyline backdrop, and on the sofa beside it his guest, a heavy set younger man with long dark curly hair'
     ' and a beard in a leather jacket.',
     'A talk show host in a suit stands in front of a curtain telling a joke into a microphone, smiling and looking out at'
     ' the audience.',
     'A talk show band of five men in matching plain navy blazers plays trumpets and saxophones, each man looking'
     ' different in age and build, seen close from the side so the horns fill the picture, a dark curtain behind'
     ' them.',
     'A man of about forty with a broad face, dark wavy hair and a dimpled chin laughs with his head back on a sofa,'
     ' one arm along its back, seen from the chest up, the colours soft and smeared.',
     'A talk show audience laughs in rows beyond the stage lights.')
show('family', SITCOM,
     'A suburban family sits close together on a flowered sofa in a cosy living room with patterned wallpaper and'
     ' framed pictures, a mother of about forty with a brown bob, a father of about forty five whose hair is scorched'
     ' and sticking straight up, and between them their two children both sitting on the sofa, a girl of about twelve'
     ' with long brown hair and a boy of about ten with short hair.',
     'A mother of about forty in an apron stands in a homely kitchen full of white smoke holding a plain red fire'
     ' extinguisher by its handle, its smooth back towards us, white foam covering a burnt pan on the stove beside her,'
     ' wooden cupboards around her.',
     'A family of four laughs round a dinner table in a cosy dining room with flowered wallpaper, a hanging lamp over'
     ' the table, a father of about forty five with a moustache, a mother of about forty with curly hair, a girl of'
     ' about twelve and a boy of about nine.',
     'Two children sit side by side on a carpeted staircase between papered walls, a girl of about twelve leaning over'
     ' with her mouth right at the ear of a younger boy of about nine, whispering a secret to him, and the boy'
     ' listening with wide eyes.',
     'A cosy living room seen from beside the sofa, looking across the room to the open front door on the '
     'far wall, bright daylight and a green front yard outside through the doorway, and a grinning '
     'neighbour of about fifty in a loud Hawaiian shirt covered in big orange and green flowers leaning '
     'only his head and shoulders in round the edge of the door from outside, the rest of him behind the '
     'door.')
show('britcom', SITCOM,
     'A woman in her sixties in a floral dress and pearls pours tea from a china teapot in a chintzy English sitting'
     ' room in a British comedy series, seen close from across a low table, flowered wallpaper behind her and the white'
     ' ceiling of the room with a fringed lampshade along the top of the picture.',
     'Two neighbours gossip over a low wooden garden fence in a British comedy series, seen close from the side: a woman in her'
     ' fifties in a headscarf on one side of the fence and a bald man in his sixties in a cardigan on the other side,'
     ' facing one another across it, the brick wall of a house and a hedge filling the whole picture behind them.',
     'A vicar in a black shirt and white dog collar sits squeezed on a sofa between two old ladies, a teacup on a saucer'
     ' balanced on his knee, in a chintzy English sitting room with flowered wallpaper and a white ceiling, in a'
     ' British comedy series.',
     'A man in his fifties in a brown cardigan sits in an armchair holding a big opened newspaper up high in front of'
     ' him so that it hides his face and head completely, only his hands at its edges and his cardigan sleeves and'
     ' legs showing, the back of the paper towards us a broad pale grey sheet out of focus, while his wife in her'
     ' fifties in a floral blouse talks at him from the sofa beside, in a British comedy series, seen close in a cosy'
     ' English sitting room with flowered wallpaper behind them and the white ceiling in view.')

# What comes on a tape and is not on the air.
show('fitness', VIDEO,
     'An aerobics instructor, a grown woman of about thirty with a high brown ponytail, in a loose long'
     ' sleeved plain purple sweatshirt, stands alone facing the viewer with her hands on her hips, smiling straight'
     ' at the viewer, seen from the waist up, a plain pale painted gym wall behind her, in a 1980s exercise class.',
     'Seen from the waist up across a gym, a row of four grown people a little apart doing squats, all in plain solid'
     ' coloured shirts: a heavy set bald man of about fifty in grey, a woman of about thirty five with a curly perm in'
     ' pink, a grey haired man of about sixty five in navy and a tall woman of about forty with a brown bob in yellow,'
     ' in a 1980s exercise class.',
     'A fit man of about thirty in a headband and a plain clean grey t-shirt stands in a gym with plain painted walls,'
     ' his arms hanging straight down by his sides, a dumbbell in each hand, in a 1980s exercise class.',
     'Four grown women in their thirties, each face different, in plain solid coloured t-shirts and plain dark loose'
     ' track pants, step up and down on low plastic steps in a row across a gym with bare painted walls, each on her'
     ' own step, seen from across the room, in a 1980s exercise class.')
show('garage', TAUGHT,
     'A mechanic of about forty five in plain blue overalls leans over the open engine bay of a big old '
     'car in a garage, seen close from the side of the car and framed from the chest up, the raised hood '
     'above him, his hands on the engine, only the engine and the edge of the dark painted fender in the '
     'picture.',
     'A mechanic of about forty five with grey at his temples and a moustache, in plain blue overalls, kneels beside'
     ' the front wheel of a big old American sedan in a garage with bare concrete walls, framed close on him and the'
     ' wheel so only the smooth bare wheel arch and the bottom of the door show, the car raised on a plain red trolley'
     ' jack standing on the floor so its front wheel hangs clear of the concrete, his hands on the wheel.',
     'A plain red metal toolbox with its drawers pulled open, full of spanners and screwdrivers, stands on a concrete'
     ' garage floor beside an old car tyre, seen close up.',
     'A mechanic in plain blue overalls leans over the open hood of an old car with his arms down inside the engine'
     ' bay, seen from the side a few paces back.')
show('first-aid', TAUGHT,
     'A grown woman of about thirty five with short permed hair, in a plain red sweater, sits upright on the edge of a'
     ' table in a plain beige room with her left forearm wrapped in a neat white bandage and resting in a white cloth'
     ' sling tied at her shoulder, smiling, seen from the waist up.',
     'A paramedic of about thirty five in a plain light blue cotton shirt with smooth bare sleeves and a '
     'smooth bare chest, worn untucked over plain dark trousers, kneels on a grey carpet with both hands '
     'pressed on the chest of a plain pale plastic training dummy lying on its back, in a plain beige room.',
     'Rolled white bandages, a roll of white tape, a pair of round ended scissors and a few folded gauze pads laid out'
     ' in a row on a white table, seen from above.',
     'A woman doctor in a white coat sits in a doctor\'s office, looking straight out of the frame towards the viewer'
     ' as she speaks.',
     'A bandage is wrapped round a lower leg resting on a stool.')
show('guns', TAUGHT,
     'A shooting instructor of about fifty with short grey hair and a grey moustache, bareheaded, in a plain tan'
     ' canvas vest over a long sleeved plaid shirt, stands outdoors talking towards the viewer, seen from the chest'
     ' up, green grass and trees soft and blurred behind him on a summer day.',
     'A pair of yellow tinted shooting glasses and a pair of plain smooth grey ear muffs lying side by side on the'
     ' weathered wooden bench of an outdoor shooting range, seen close from a little above, green grass soft and'
     ' blurred beyond the bench.',
     'Round paper targets on wooden stands at the far end of an outdoor shooting range, a grass bank behind them.',
     'A grey haired Kentucky sheriff\'s firearms instructor in a tan uniform shirt stands at the front of a plain'
     ' American classroom with wood panelled walls and a green chalkboard, a few adult deputies in tan shirts at'
     ' school desks seen from behind.')
show('garden', TAUGHT,
     'A man in a straw hat and dungarees kneels among tomato plants in a vegetable garden.',
     'A small green seedling newly planted in dark soil in a garden bed, a trowel lying flat on the soil beside it,'
     ' seen from a little above in soft daylight.',
     'Pots of herbs on a sunny window sill.',
     'A basket of fresh vegetables on a garden path.',
     'A man sprays the leaves of a row of bean plants.')
show('fishing', OUTDOOR,
     'A man in a fishing vest and a plain cap sits alone in a small aluminium boat on a calm lake in the morning,'
     ' holding up a largemouth bass, seen from a little way off across the water.',
     'A man casts a fishing rod from a lake shore at dawn.',
     'An open green plastic tackle box on a sun warmed wooden dock, its little trays holding bright fishing lures, red'
     ' and white spoons, yellow feathered jigs and soft purple worms with hooks, the lake water beyond.',
     'A small old wooden rowboat tied to the end of a grey wooden dock on a calm lake at dawn, a long fishing rod'
     ' laid along its seats, thin mist lying on the still water, seen from a little way back along the dock.')
show('farm', OUTDOOR,
     'A girl of about ten holds out a handful of feed through a wide gap between the wooden rails of a fence, and a'
     ' white goat on the other side reaches its head through the gap to eat, at a small farm petting zoo in summer.',
     'A rooster stands on a fence post at sunrise.',
     'One chestnut horse grazes alone side on in a wide open rolling green pasture in Kentucky, seen from a little '
     'way off, a long white painted wooden board fence running across the grass, a big lone oak tree and low wooded '
     'hills behind, under a hazy summer sky.',
     'A farmer of about sixty in a straw hat and a long sleeved work shirt, seen from behind, walks between rows of '
     'young green tobacco plants in a wide field in Kentucky, a wooden tobacco barn and a line of trees far behind, '
     'under a hazy summer sky.',
     'Two muddy piglets lie asleep on their sides a little apart in deep golden straw in a dim barn, their eyes'
     ' closed, seen from above.')
show('trade', TAUGHT,
     'An old grey metal house fuse box on a basement wall with its door open, a row of round glass fuses screwed into'
     ' round sockets inside like small light bulbs, an electrician of about fifty in a tool belt standing beside it'
     ' seen from behind.',
     'A neatly wound coil of red insulated electrical wire and a coil of white wire lying on a wooden workbench beside'
     ' a pair of plain red handled pliers, seen close up.',
     'A close view of a row of five old glass vacuum tubes standing upright in their sockets on the grey metal'
     ' chassis of an old radio, their filaments glowing warm orange in a dim workshop, seen from a little above.',
     'A welder of about forty in a plain dark welding helmet and leather gloves bends over a steel beam in a dim'
     ' workshop, a bright white point of light at his torch with a short fan of orange sparks spraying out from it.',
     'A man in a hard hat stretches wire along a line of fence posts.')
show('sewing', TAUGHT,
     'An old woman of about seventy with grey hair in a bun smiles down at her sewing in a cosy room, seen '
     'from the side a few paces back, sitting at a wooden table with a white sewing machine on it, a big '
     'heap of flowered cotton cloth piled over the machine and across the table so only the top of the '
     'machine shows above the cloth.',
     'A spool of red thread and a round red pincushion stuck with pins lie beside a folded piece of denim on a table,'
     ' seen close up.',
     'A half finished pale blue gingham cotton blouse hanging on a wooden coat hanger from a hook on a plain cream wall'
     ' of a sewing room, a few pins stuck along its open side seam, soft window light across it.',
     'Long heavy steel dressmaking shears lying closed on a neatly folded stack of plain blue cotton cloth on a bare'
     ' wooden table, seen close from a little above, soft window light.')
show('pottery', TAUGHT,
     'A potter of about fifty in an apron sits at a turning potter\'s wheel, seen from a little way back, a tall wet'
     ' clay pot rising on the wheel between her two hands.',
     'A potter in an apron sits at a wheel in a studio lined with shelves of pots.',
     'A row of glazed pots on a wooden shelf.')
show('lesson', TAUGHT,
     'A woman of about forty five in a grey business suit stands alone in a beige office beside a pot plant, seen close'
     ' from the waist up, looking straight towards the viewer as she speaks.',
     'A professor in a jacket stands at a blackboard beside a chalk diagram of circles and arrows with no writing.',
     'A dentist in a plain white coat sits at a desk with a big plastic model of teeth on it in front of him, a plain '
     'beige wall and a potted plant behind him, the picture dull and slightly smeared.',
     'A flight instructor in a plain white shirt stands beside a small single engine propeller plane on a grass'
     ' airfield, pointing at its wing, seen from a little way off.',
     'Two people in suits shake hands across an office desk, nobody else in the room, in a staff training video.',
     'A clergyman in a grey suit stands in front of a stained glass window, looking straight out of the frame towards'
     ' the viewer as he speaks gently.')
show('nature', NATURE,
     'A red brown doe of the Kentucky woods, her short summer coat a warm rusty cinnamon colour all over, with large '
     'ears and slender legs, stands in a misty forest clearing at dawn.',
     'Penguins crowd on an ice shelf, seen far off, the picture soft and grainy, the colours dull blue grey and'
     ' slightly faded.',
     'A red fox pounces in the snow.',
     'An American alligator with a broad rounded snout lies half submerged in a green swamp among lily pads.',
     'A wide river winds between wooded hills, seen from the air.',
     'A big brown bear stands in the rushing white water at the very top of a waterfall, its head lowered '
     'watching the water at its feet, white spray all round it.',
     'A spiral galaxy and stars in deep space in an astronomy documentary.')
show('travel', ' A still image from a 1980s travel documentary shot on 16mm film on a sunny day, soft with film grain'
               ' and warm muted colours. The picture fills the whole image edge to edge.',
     'The roofs of Paris with the Eiffel Tower beyond them.',
     'A busy street market in a West African city, seen from above.',
     'The curved grey tiled roof and the brightly painted green and red wooden eaves of an old Korean palace gate, '
     'seen from a little below, the upturned corners of the roof against a clear blue sky, low plain grey concrete '
     'office blocks with flat roofs beyond.',
     'A single slim rocket painted plain white all over, smooth and bare from nose to tail, stands on its '
     'launch pad beside the sea under a clear sky.',
     'Three old green tanks parked well apart in a row in front of a brick army building, each with one '
     'long gun barrel.',
     'A low river barge with a plain bare rusty steel hull, dark and unpainted, piled with coal, passes '
     'under a steel bridge.')
show('birthday', HOME,
     'A child blows out the candles on a birthday cake with the family round the table.',
     'Three children in plain cone shaped paper party hats covered in coloured polka dots sit at a table with plain'
     ' white paper plates at a birthday party in a living room.',
     'A grandmother laughs in front of a birthday cake.',
     'A teenage boy opens a present on a sofa.',
     'A colourful paper donkey pinata on a string from a tree branch in a sunny back yard, children in party hats standing on'
     ' the grass looking up at it.')
show('wedding', HOME,
     'A bride and groom stand before a minister in a small church.',
     'A bride and groom stand behind a tiered wedding cake on a table, a cake knife beside it.',
     'Wedding guests dance in a hall hung with streamers.',
     'A bride in a white 1980s wedding dress with big puffed sleeves laughs on a lawn, two bridesmaids in matching'
     ' pale pink dresses standing a little apart beside her, permed hair, a small white church behind.')
show('baby', HOME,
     'A toddler in a striped t-shirt and denim dungarees takes wobbly steps across a brown shag carpet toward his '
     'mother, who kneels with her arms out in a long sleeved blouse and jeans, in a wood panelled 1980s living room '
     'with a tube television in the corner, the colours faded and the picture smeared like an old worn tape.',
     'A baby in a high chair with food on its face.',
     'A father sits on a garden lawn with a smiling baby in a romper sitting on his lap.',
     'A baby crawls across a carpet towards the viewer.')
show('vacation', HOME,
     'A mother and father in t-shirts and shorts wave from under a striped umbrella on a sandy beach.',
     'A father and two children stand in front of a fairground wheel.',
     'A donkey looks over a stone wall in a green field.',
     'A motel swimming pool seen from just above the water, the clear turquoise water filling the whole '
     'picture with wobbling lines of sunlight on the pale blue bottom, one red and white beach ball '
     'floating on it and the shadow of a palm tree across the water.',
     'A mother and father in shorts, t-shirts and sun visors stand side by side squinting in the sun in front of a tall'
     ' plain white space rocket with black bands on display on a green lawn outside a museum in Florida, palm trees and'
     ' blue sky.')
show('christmas', HOME,
     'Children in pajamas open presents under a Christmas tree.',
     'A family in paper crowns sits round a table with a roast turkey.',
     'A school nativity play on a small stage, two children dressed as shepherds in striped robes and tea towel'
     ' headdresses kneeling beside a small wooden manger full of straw, one child a head taller than the other,'
     ' a plain dark blue curtain filling the back of the stage and the bare plain painted walls of the'
     ' hall either side, seen from the audience.',
     'A man in a Christmas sweater holds up a pair of socks and pulls a face.')
show('pets', HOME,
     'Two kittens, one ginger and one grey, sit apart in a cardboard box on a kitchen floor.',
     'A dog leaps to catch a flying disc in a back yard.',
     'A child hugs a big shaggy dog on a porch.',
     'A kitten bats at a ball of wool on a carpet.')
show('stage', HOME,
     'A boy of about twelve in a white shirt stands up on the raised wooden stage of an American school gym playing a'
     ' saxophone, a plain dark red curtain behind him, seen from the audience below the stage.',
     'A row of children sing on risers in a school hall, seen from the back of the hall.',
     'Two grown actors of about thirty five in a community play, a bearded man and a woman with her hair pinned up,'
     ' both in plain hooded brown medieval cloaks of rough wool, act on a school stage in front of painted scenery of'
     ' plain green trees and a grey stone wall.',
     'Grown mothers and fathers in their thirties and forties, in work clothes and summer dresses, some grey haired or '
     'balding, sit on folding chairs clapping in a school hall, seen from behind at a distance.')

# 'any' is the dozen stills of before the mod knew what was on, of no one show (catalogue.PROGRAMMES): what a set
# shows when nothing says what is on. 'snow' has no shots: a broadcast that is all interference, which a set draws
# as snow.
ANY = 'any'
ANY_SHOTS = 12
SNOW = 'snow'

# Which show each of the game's television channels is when nothing says what is on it, by its frequency.
CHANNELS = {200: 'news-desk', 201: 'local-desk', 203: 'cook', 204: 'hollywood', 205: 'puppets', 206: 'county', 207: 'pop',
            208: 'sports-desk', 209: 'critics', 210: 'network-news'}
# The channels whose broadcasts with none of the lines below are the channel's own show: the news is the news.
NEWS = {200, 201}

# The show of a broadcast that is nothing but a station's notice that it has stopped making programmes.
EMERGENCY = 'emergency'
