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
CLIP = ' A still from an early 1990s music video with coloured stage lights and haze,' + NOISE
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
     'Two news anchors, a man and a woman, sit a little apart at a long news desk seen wide, a newsroom of desks behind'
     ' them with boxy tube monitors showing plain pictures of fields and skies.',
     'A woman news anchor with short brown hair and an ordinary face sits at a desk looking off to one side, a large'
     ' framed photograph of a green river valley seen from the air hanging on the pale wall over her shoulder.',
     'A wide view of a large dim room with deep blue walls, a curved wooden desk lit warm in the middle and one anchor'
     ' of about fifty in a dark suit sitting small behind it, papers in his hands.')
show('news-field', FIELD,
     'A woman news reporter of about thirty with shoulder length brown hair, in a beige trench coat, holds a plain'
     ' black microphone in front of an army roadblock of green trucks and coiled barbed wire at dusk.',
     'Seen from far off down a rural highway, small figures of soldiers in gas masks and plain green fatigues stand at'
     ' a checkpoint, grey concrete barriers across the road under an overcast sky.',
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
     'A row of plain grey television cameras on tripods faces an empty outdoor podium with a bank of microphones, a'
     ' soft blur of reporters standing behind them.',
     'A field command post of green army tents in a summer field, a thin grey steel lattice radio mast with a few long'
     ' whip aerials standing beside them, green 1990s army trucks with canvas backs parked nearby and two soldiers'
     ' walking between the tents.')
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
     'A car burns in the middle of an empty city street at night, three police officers in dark riot helmets with plain'
     ' smooth clear shields standing in a line a little way off, one tall, one short and stocky, one thin.',
     'Five protesters of different ages stand in a loose uneven line on the wide stone steps of a building with'
     ' columns, each holding up one blank white square of card, an old man with a grey beard, a young woman with a'
     ' ponytail, a heavy man in a cap with a plain front, a woman of about fifty in glasses and a thin young man, all'
     ' in plain solid coloured clothes.',
     'A column of olive green canvas backed army cargo trucks of the 1980s drives down a city avenue between tall buildings, a'
     ' few people watching from the sidewalk.',
     'A smashed shop window seen close from the sidewalk at dawn, the big glass pane broken out of its frame leaving'
     ' jagged teeth of glass round the edges, shards across the pavement, bare empty shelves and an empty floor behind'
     ' it, the quiet street around it deserted.',
     'A city seen from high above at night, looking down, several fires burning between the dark buildings and smoke rising.',
     'Five shoppers, each with one empty shopping cart, wait in a line along the plain brick front of a small town'
     ' supermarket, and through its big front windows the white metal shelves inside stand bare and empty.')
show('emergency', VIDEO,
     'An empty wooden desk under warm light in a dim room with deep blue walls, the chair pushed back and loose papers'
     ' left on it.',
     'A tired middle aged official with thinning grey hair, a round face and glasses, in shirt sleeves and a loosened'
     ' tie, sits at a plain desk with his forearms resting on it, a single sheet of paper lying flat on the desk, an'
     ' American flag on a pole beside him.',
     'A plain flat blue panel filling the whole picture edge to edge, with a simple flat gold eagle emblem in the'
     ' middle.',
     'A dark empty room with one large grey 1990s television camera on a heavy wheeled pedestal pointing at an empty'
     ' wooden chair, the chair alone in a round patch of warm light on the floor.')
show('local-desk', VIDEO,
     'A male local news anchor in a brown suit sits at a wood panelled news desk, a photograph of a small city skyline behind'
     ' him.',
     'A woman local news anchor of about forty with big permed hair and a green blazer sits at a wood panelled desk,'
     ' seen close from the chest up with a plain flat blue wall filling the whole picture behind her, the light flat'
     ' and a little harsh, the picture soft with smeared colour.',
     'Two local news anchors, a man and a woman, sit a little apart at a wood panelled desk and turn to each other smiling,'
     ' plain blue panels behind them.',
     'A local weatherman of about fifty in a tan suit stands beside a big flat map of Kentucky, the state in pale green'
     ' surrounded on every side by plain beige land, a few simple yellow suns and white clouds drawn on it, one arm'
     ' raised toward it.')
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
     'A male reporter of about thirty five in a windbreaker stands with a hand microphone in front of a strip of plain'
     ' yellow tape stretched across a rural road between green summer fields, plain white sedans parked side on behind'
     ' him.',
     'A plain white van seen side on, parked on a grass verge beside a highway, a tall thin grey metal mast fixed to'
     ' the middle of its roof rising straight up into the sky with a small round dish at the very top, its smooth white'
     ' side bare.',
     'A dark country road at night seen through a car windshield, the headlights picking out ordinary 1980s family cars'
     ' left askew across the verge with their doors hanging open, as if their drivers ran.',
     'Small figures of grown men and women run across a field at dusk, seen from far off, the picture shaky and'
     ' blurred.',
     'A frightened male reporter of about forty with thinning sandy hair, a round soft face and wire glasses, in a'
     ' crumpled suit, crouches alone behind the back of a parked car at night, his hands empty, his head turned to look'
     ' back over his shoulder away from us, lit by a harsh white light.')
show('statement', VIDEO,
     'A man of about fifty five in a dark suit speaks at a plain wooden podium in front of a blue curtain, two American'
     ' flags on poles beside it.',
     'Rows of seated reporters in suits seen from the front across a wide room, their faces towards us, the reporters'
     ' small, a few with one hand raised to ask a question, a plain bare wall behind them.',
     'A white government building with columns lit up at night.',
     'A grave heavy man of about sixty five with a broad fleshy face, a double chin, thinning white hair combed straight'
     ' back and bushy dark eyebrows, in a dark suit and red tie, speaks into a cluster of plain black microphones, seen'
     ' from the chest up.')

# Home and hobbies.
show('cook', VIDEO,
     'A cheerful woman in an apron stands at the stove of a studio kitchen, stirring a pot with a wooden spoon.',
     'A flat overhead view like a map: a square wooden chopping board fills the whole picture edge to edge, small '
     'heaps of orange carrot rounds and diced white onion on it, flat even light.',
     'A big glossy brown pot roast of beef sitting in dark gravy in a black cast iron pan, surrounded by soft browned'
     ' roast potatoes and carrots glistening with gravy, seen from the side on a home kitchen counter.',
     'A cheerful woman in an apron stands behind a kitchen counter smiling straight out of the frame towards the'
     ' viewer, a mixing bowl and a whisk on the counter in front of her.',
     'A pan of vegetables steams on a gas stove with a wooden spoon resting in it, seen close up.',
     'A tall round layer cake covered all over in thick smooth white buttercream icing with piped rosettes round its top'
     ' edge sits on a cake stand on the counter of a studio kitchen.')
show('woodwork', VIDEO,
     'A bearded man of about forty five in a plaid shirt and safety glasses saws off the end of a pine plank laid'
     ' across two sawhorses, an old wooden handled hand saw cutting straight down near the end of the plank, the short'
     ' end piece about to drop, seen from a few paces back.',
     'A plain claw hammer lies flat on its side on pine boards on a workbench, a few small steel nails with flat round'
     ' heads scattered beside it and one nail half driven into the wood, seen close up.',
     'In a home workshop with a wooden workbench and sawdust on the floor, a bearded man of about forty five in a plaid'
     ' shirt stands proudly beside a finished wooden bookcase, a few old wooden handled hand tools hanging on the plain'
     ' wall behind him.',
     'A pegboard wall, a brown board full of small round holes, over a workbench, with a claw hammer, a screwdriver'
     ' and a wooden handled hand saw hung well apart on metal hooks.',
     'A smooth plain wooden hand plane rests on a pine board on a workbench, a long thin pale wood shaving curled up'
     ' into a loose spiral lying beside it.',
     'A bearded man of about forty five in a plaid shirt, a yellow pencil resting on top of his ear behind it, bends'
     ' low at the end of a workbench with one eye right at the end of a long pine board, sighting down its length to'
     ' check it is straight.')
show('survival', FIELD,
     'A rugged man in a khaki shirt and a bandana crouches by a small campfire in a forest, looking straight out of'
     ' the frame towards the viewer as he speaks.',
     'A rugged man of about forty in a plain khaki shirt kneels beside a river with a trout laid on a flat rock in'
     ' front of him.',
     'A lean to shelter of branches and leaves stands in a forest clearing.',
     'A small loop of thin cord hanging low over a narrow forest path, tied to a bent green sapling and held down by a'
     ' small wooden peg, seen close up.',
     'A rugged man in a khaki shirt walks through tall ferns and looks back over his shoulder towards the viewer.',
     'A blackened open pot of water boiling over a campfire at dusk, steam rising, a small low canvas tent behind it,'
     ' the picture soft and grainy.')
show('advert', VIDEO,
     'A man of about fifty in a red knitted sweater and a woman of about forty five with permed hair in a blue sweater'
     ' stand an arm\'s length apart behind a kitchen counter with a big white mixing bowl and a chrome toaster on it,'
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
     'A man of about fifty five in a cardigan laughs with his head thrown back at a desk with an old beige telephone on'
     ' it, seen close, a plain painted wall filling the picture behind him.')

# Entertainment.
show('hollywood', VIDEO,
     'A woman entertainment news presenter of about thirty five in a pink blazer with ordinary hair and make up stands'
     ' in front of a painted mural of palm trees and a pink sunset sky that fills the whole picture behind her.',
     'Film stars of about forty in evening dress, the women in long sleeved floor length gowns, walk a red carpet at'
     ' night, a dark blurred crowd beyond a rope with bright white flash bursts.',
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
     'A plain dark boxy sedan seen side on, parked in a wet city alley at night, a small round red lamp glowing inside'
     ' on top of its dashboard behind the windscreen, its smooth bare bonnet catching the light from one bulb over a'
     ' door.',
     'A detective of about fifty in a tan raincoat stands in a dry dim hallway with brown wallpaper, seen side on, a'
     ' step back from a closed wooden door with one brass knob, his head tilted and one ear turned towards the door,'
     ' listening.',
     'A thin unshaven man of about thirty five in a plain grey cotton work shirt sits at a metal table in a bare grey'
     ' room under one hanging lamp, and a heavy detective of about fifty in shirt sleeves and a loosened tie leans on'
     ' the table across from him.',
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
     'A woman of about forty with big teased blonde 1980s hair, in a loose long sleeved evening gown with a closed'
     ' collar up to her chin, points angrily at a man of about forty five in a tuxedo who stands a step away with his'
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
     'A shiny new red convertible seen exactly side on from a little way back, its front and back cut off by the edges'
     ' of the picture, standing on a round turntable in front of a plain dark curtain as a prize, confetti on the'
     ' floor.',
     'Rows of an audience in a dark room, seen from the front, a dim soft blur of faces beyond a haze of bright white'
     ' glare.')

# For children.
# The heroes are the mod's own: helmets with visors, green and orange, no capes, no masks with ears, no chest emblems.
OWN_HEROES = (' They are original characters in green and orange jumpsuits that are plain from collar to boots, with'
              ' round clear bubble helmets showing their faces and small backpacks.')
show('heroes', CEL,
     'A team of five space rangers stand in a row a little apart on a rooftop at night, each with a different human'
     ' face and natural skin, two men and three women.' + OWN_HEROES,
     'A single space ranger flies alone over a city skyline, two short jet nozzles fixed to the bottom of the backpack'
     ' on his back blowing orange flame, his arms stretched forward and both hands open and empty.' + OWN_HEROES,
     'Three space rangers stand far apart in a row across a city street, all three seen exactly side on facing right'
     ' towards a giant grey robot, a tall man with black hair, a short woman with red hair and a stocky man with a'
     ' blond beard.' + OWN_HEROES,
     'A villain with a ruddy face and a thick black beard, in a long grey coat and a tall dark metal helmet open at the'
     ' face, throws his head back laughing in a dark lair with a few big simple machines, everything in greys and dull'
     ' reds.',
     'A team of space rangers stand round a glowing table in a futuristic control room.' + OWN_HEROES)
show('puppets', VIDEO,
     'Two hand puppets of worn felt with a hand inside each, an orange fox on the left and a brown owl with big round'
     ' eyes and a beak on the right, seen from the front, no legs, rising a little apart from behind a low ledge in'
     ' front of a painted cardboard forest in a children\'s puppet show.',
     'A smiling presenter in dungarees kneels beside just two hand puppets of worn felt with a hand inside each, a'
     ' badger and a rabbit, seen from the front, no legs, rising from behind a low wooden ledge, a painted forest'
     ' behind them, in a children\'s puppet show.',
     'A felt hand puppet of a hedgehog with stitched eyes and soft fabric spines sits at a tiny table with a toy tea set in a'
     ' children\'s puppet show.',
     'A felt owl puppet looks out of a hole in a cardboard tree in a children\'s puppet show.',
     'Hand puppets made of felt, a fox, an owl and a rabbit, poke up from behind a painted wooden fence with their'
     ' mouths open in song, painted forest trees behind, on a cheap 1980s children\'s show set.')
# Asked for over a watercolour background, Qwen-Image paints a patch on a sheet of bare paper.
show('bunnies', ' A still image from a 1980s cartoon for very small children, painted by hand on celluloid over a'
                ' soft painted background, soft faded pastel colours, wobbly hand inked outlines and uneven paint. The'
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
     'Three round pastel cartoon bunnies sleep in a row, each in its own little bed, under a smiling moon, a painted'
     ' deep blue night sky full of stars running right out to every edge and corner.')
show('kids-advert', VIDEO,
     'In a cosy family den with wood panelled walls, a brother of about twelve and a sister of about ten sit side by'
     ' side on two separate chairs at a small desk in front of a beige home computer whose boxy tube monitor shows a'
     ' plain bright blue screen, in a commercial.',
     'A loud salesman in a jacket of big bold red and yellow checks spreads his arms wide in a store aisle between'
     ' tall shelves of plain cardboard boxes, seen from a little way back, in a commercial.',
     'A shiny silver plastic toy robot with a smooth plain silver chest, square head and round red eyes stands on a'
     ' kitchen table beside three loose plain silver batteries, in a toy commercial.',
     'A mother of about thirty five, a father of about forty and a girl of about eight in plain sweaters sit on a'
     ' living room carpet round a red and black checkerboard, flat round red and black draughts pieces on its squares,'
     ' in a commercial.')

# Public access.
show('tech', LOCAL,
     'A man of about twenty eight with glasses and a headset sits at a desk crowded with beige computers and circuit'
     ' boards, a plain brown curtain behind him.',
     'A beige early 1990s desktop computer case standing on a desk, the U shaped metal cover lifted off and lying'
     ' beside it, circuit boards and grey ribbon cables showing inside, seen close up.',
     'A boxy beige computer monitor on a cluttered desk, its screen glowing with a simple green wireframe grid of'
     ' lines.',
     'A man of about twenty eight with glasses and a short beard grins and holds up a green circuit board, a plain'
     ' brown curtain behind him.')
show('interview', LOCAL,
     'A woman interviewer of about forty in a navy trouser suit and a man of about seventy, a silver haired senator'
     ' with a lined face in a grey suit and tie, sit in two armchairs facing one another a little apart in a plain'
     ' office, a fern behind them.',
     'A silver haired senator of about seventy with a lined face in a grey suit and tie answers a question, seen close'
     ' from the chest up against a plain beige wall.',
     'A woman interviewer of about forty in a blazer sits in a cheap armchair in a small bare room with a wrinkled'
     ' curtain behind her, looking down at the notes in her lap, seen close from the front.')
show('clown', LOCAL,
     'A clown with an orange wig and a red nose waves in front of a paper backdrop with balloons in a children\'s show.',
     'One clown with an orange wig and a red nose stands alone on the bare floor of a small room, holding up a bunch '
     'of three balloons on strings above his head, one red, one yellow and one blue, the wall behind him painted with '
     'yellow stars on a deep blue sky.',
     'A clown with an orange wig sits on a stool facing a puppet made from a plain grey wool sock with two black button'
     ' eyes, which pokes up over the edge of a small puppet booth of plain red painted wood beside him in a'
     ' children\'s show.',
     'Small children sit cross legged on a carpet and laugh in a children\'s show, each child different: a girl of'
     ' about five with red pigtails, a chubby boy of about four with a crew cut, a thin girl of about six with dark'
     ' braids and a boy of about five with glasses, a plain painted yellow wall behind them.')
show('county', FIELD,
     'A woman presenter in a denim jacket stands by a white fence on a horse farm, rolling green hills behind her.',
     'A county fair on a summer evening, one ferris wheel lit up against the sky behind a row of striped stalls.',
     'An old man in a faded blue cotton shirt sits in a rocking chair on the porch of a wooden house, snapping green '
     'beans from a wicker basket on his lap into a tin pail at his feet, seen from the yard.',
     'A shallow creek running clear over flat grey rocks between steep wooded banks on a summer afternoon, sunlight '
     'coming through the leaves onto the water, seen from the water\'s edge.',
     'A tall old black painted wooden tobacco barn with vertical plank siding and open vent boards standing alone at the edge'
     ' of a cut field at sunset, seen from the road.',
     'Two horses, a chestnut and a grey, graze well apart in a green pasture, short green grass in the foreground and a'
     ' white wooden fence running along the field behind them.')
show('survivors', ' A still from old footage lit by one harsh light, dim and grainy with muted colours. The'
                  ' picture fills the whole image edge to edge.',
     'An exhausted man of about forty in a dirty flannel shirt sits at a desk in a dim room, a door behind him'
     ' barricaded with a heavy wooden wardrobe and a stacked table and chairs.',
     'A tired woman of about thirty five with a bandaged arm sits in a dim room, looking straight into the lens as she'
     ' speaks.',
     'A young man of about twenty five with short dark hair crouches beside an old metal barrel in a dim empty garage,'
     ' small flames rising out of the open top of the barrel.',
     'Stacks of plain silver food cans and plastic water jugs stand on the floor of a dim room against a bare wall.',
     'An empty desk in a dim room, and on the floor beside it a black office chair lies tipped over on its side with'
     ' its wheels in the air.')

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
     'Three women of about twenty five, each face different, in bright colour blocked baggy jackets zipped up and loose'
     ' trousers, dance a little apart in front of a plain white wall filling the picture, caught mid jump with their'
     ' arms up and knees bent.',
     'Four men in their mid twenties in denim jackets pose against a painted brick wall, one tall and black haired, one'
     ' with a blond mullet, one stocky with a goatee and one with curly red hair.',
     'A singer of about twenty five in a loose jacket with a thin wire microphone curving from his ear round to his'
     ' mouth dances with both arms swinging, four dancers of about twenty five in long sleeved baggy clothes jumping'
     ' behind him, coloured light and haze around them.',
     'A pop singer of about twenty five with big curly hair falling over her ears, in a loose white blouse buttoned up'
     ' to the collar, sings into a handheld microphone, seen from the waist up, coloured haze behind her.',
     'Seen from far off, a crowd of young people about nineteen, nearly adult, dance with their arms in the air as dark'
     ' shapes against coloured haze.')
show('rock', CLIP,
     'A long haired rock band plays on a huge arena stage, a towering wall of plain stacked black amplifier cabinets'
     ' behind them, jets of flame shooting up at the sides, white and amber light and haze, a packed crowd of raised'
     ' arms filling the foreground, seen from far back in the arena.',
     'Seen from far back in the dark, a long haired guitarist stands alone in a single round beam of white light on a'
     ' dark stage, a small figure, his guitar a dark shape in his hands, the beam coming down from far above out of'
     ' frame.',
     'A drummer of about thirty with long hair in a plain black sleeveless shirt sits behind a huge drum kit with two'
     ' bass drums with plain black heads and a wide ring of toms and cymbals, a drumstick in each hand, everything'
     ' bathed in deep red light and haze.',
     'A male rock singer of about thirty with long hair tucked behind his bare ears, in scarves and a leather jacket,'
     ' screams into a microphone on a stand, seen from the chest up in red haze.',
     'A concert crowd seen from the front of the stage looking out over their heads, rows of faces and raised fists out'
     ' of focus in the haze, the people facing towards us.')
show('soul', CLIP,
     'A singer of about thirty in a silk shirt stands singing softly in a loft lit only by many candles, warm dim light'
     ' on his face, the dark brick walls behind him.',
     'A woman of about thirty in a long satin dress sings beside a saxophone player of about forty, the two of them in'
     ' warm amber light and soft haze against dark.',
     'A man of about thirty five plays an electric piano in a dim room in warm amber light, seen from the front across'
     ' the top of the instrument, his hands on the keys and his face lit from above out of frame.',
     'A couple of about thirty slow dance holding each other close, her head on his shoulder, in a dim room in front of'
     ' a tall window onto the city lights at night.')
show('rap', CLIP,
     'Three rappers in their mid twenties stand bareheaded on a city basketball court beside a big portable stereo on a'
     ' bench, seen from the waist up: a tall thin one with a tall flat topped box of short black hair in a baggy red'
     ' hooded sweatshirt, a stocky one with a shaved head in an oversized denim jacket and one with short neat twists'
     ' of hair in a big plain yellow t-shirt.',
     'Two men in their mid twenties, one in a plain black satin jacket leaning on the side of the car and one in a'
     ' plain white t-shirt leaning on the door, with a long low 1960s convertible lowrider, deep red with chrome wire'
     ' wheels, seen side on under orange street lights at night, the street otherwise empty.',
     'A disc jockey of about twenty five in big headphones stands at two black record turntables with a small plain'
     ' grey mixer between them, in a dim club full of red haze, warm light falling on him from above out of frame.',
     'Three men in their mid twenties in plain solid coloured shirts and baggy jeans pose on the stone steps of a city'
     ' row house in daylight, one tall and thin, one heavy with a beard and one short with a shaved head.')
show('club', HAZE,
     'Seen from high up on a balcony at the back of a large dark dance hall, the crowd on the dance floor far below '
     'and small, a sea of heads and shoulders, green laser beams fanning through thick smoke over them.',
     'A disc jockey seen from the side at two black record turntables, one hand resting on a spinning black vinyl '
     'record and the other holding one side of his big headphones to his ear, his face lit red, smoke behind him '
     'glowing red and blue.',
     'Black silhouettes of the heads and shoulders of a crowd of dancers seen from behind, framed from the shoulders '
     'up, against thick smoke glowing red, yellow and blue.',
     'Glowing coloured shapes spin on a black background in early 1990s computer video art.')
show('eighties', CLIP,
     'A singer of about thirty with big teased hair in a plain black jacket with wide padded shoulders, hanging open,'
     ' stands in front of a wall of glowing pink and blue neon lines forming a grid, purple haze around him, the light'
     ' coming from somewhere above out of frame.',
     'A man of about thirty in a pale pink suit stands alone in purple haze playing a keytar, a small white plastic'
     ' keyboard with a short smooth plastic handle, slung across his body on a strap, his fingers on its black and'
     ' white keys, lit by warm coloured light from above out of frame.',
     'A low wedge shaped red sports car seen exactly side on and small in the distance drives along an empty highway at'
     ' night, its wheels a blur, long lines of pink and blue neon tubing glowing along the roof edges of the low'
     ' buildings beside the road.',
     'Three men of about thirty in pastel suits, pink, mint and pale blue, stand a little apart on a sandy beach at'
     ' sunset, one holding a plain white electric guitar with a small plain headstock, one a plain black bass, and one'
     ' a small white keyboard hanging from a strap over his shoulder.',
     'A 1980s dance class of grown women of about thirty with big permed hair and headbands, each face different, in'
     ' long baggy sweatshirts over black shorts and full length tights with leg warmers, dance in a wooden floored hall'
     ' with a ballet barre along a plain painted wall, seen from across the room.')

# Sport.
show('sports-desk', VIDEO,
     'Two sports presenters of about forty five in plain navy blazers sit at a desk with a plain gold cup standing on'
     ' it between them, a soft blurred picture of a stadium at night behind them.',
     'A sports presenter in a double breasted navy blazer with big lapels, a white shirt and a wide patterned tie,'
     ' with a moustache and a side parting, stands indoors beside a tall wooden desk with one hand on a pile of papers,'
     ' a plain dark blue wall behind him.',
     'A grinning sports presenter of about fifty with a moustache, in a plain navy blazer and a plain red tie, sits at'
     ' a desk with papers in front of him, a plain blue wall behind him.',
     'A tired athlete of about twenty five in a plain loose grey t-shirt with a white towel round his neck sits in'
     ' front of lockers in a locker room while a reporter beside him holds a microphone up to him.')
show('baseball', SPORT,
     'A close head and shoulders shot of a baseball pitcher in a plain white uniform and a plain white cap, only his '
     'face, cap and shoulders in the picture, his eyes narrowed as he stares in at the catcher, green outfield grass '
     'blurred behind him.',
     'Seen from far off through a long lens, a lone baseball catcher crouches in the brown dirt, a small figure in '
     'grey with dark padding and a dark mask, holding up his brown glove as a target, a plain dark green wall behind'
     ' him.',
     'The wide green outfield grass of a baseball park seen from high in the stands on a sunny afternoon, mown in broad'
     ' light and dark green stripes, the far stands a soft distant blur of colour.',
     'Seen from far off across the outfield, a baseball outfielder in plain grey leaps at the low padded outfield'
     ' fence, a small lone figure, the empty green grass in front.',
     'A baseball dugout seen from across the field, four players in plain grey uniforms and plain dark caps leaning on'
     ' its rail in the deep shade of its roof, one old and heavy, one tall and thin, one with a dark beard and one'
     ' young and fair, the green grass of the field in front.',
     'Seen from very high up in the top of the stands far away, a whole baseball infield, one runner a tiny figure in a'
     ' plain grey uniform sliding into a base in a cloud of dust on the brown dirt.')
show('golf', SPORT,
     'A golfer of about forty in a plain pale blue polo shirt and a plain white glove follows through after a drive'
     ' from the tee, a single ball rising away into the sky, a line of spectators behind a rope behind him.',
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
     'The view from low in the open cockpit of an open wheel racing car down a long straight, a small plain black'
     ' steering wheel in the bottom of the picture, the two front tyres either side and another open wheel car ahead.',
     'Seen from high above and far away, a starting grid of small low open wheel racing cars in plain solid colours'
     ' sits two by two on a grey paved oval track, the long grandstand along one side packed with people.',
     'Seen from far off, a racing driver in plain white overalls and plain white boots, a small figure, stands alone on'
     ' top of a podium with champagne spraying up in an arc above him, a plain blue sky filling the picture all round'
     ' him and the podium.',
     'Stock cars in plain colours race side by side round a dirt oval.')
show('skating', SPORT,
     'A skateboarder of about eighteen in a plain white t-shirt and a plain black helmet flies in mid air above a'
     ' wooden half pipe, a few spectators in plain solid coloured shirts watching.',
     'Seen from far off, a small figure of a skateboarder about seventeen, nearly adult, tall and lanky, in a baggy'
     ' long sleeved plain t-shirt and long shorts slides along a metal rail beside a concrete plaza, the picture soft,'
     ' smeared and faded.',
     'Seen from very far off against a pale sky, a small figure of a rider about seventeen, nearly adult, tall and'
     ' lanky, in a plain blue t-shirt and jeans on a small BMX bicycle flies through the air over a dirt jump, an old'
     ' round open face helmet on his head, the dirt track around him empty.',
     'Three teenagers about seventeen, nearly adult, in plain solid coloured t-shirts, a tall boy with long blond hair,'
     ' a girl with a dark ponytail and a stocky boy with a buzz cut, cheer with their arms up at the top of a wooden'
     ' skate ramp, seen from below at the foot of the ramp against a blue sky.')
show('boxing', ' A still image from 1970s boxing coverage shot on 16mm colour film, bright ring lights and a'
               ' faint haze, soft and grainy with faded colours. The picture fills the whole image edge to edge.',
     'Seen from high in the dark seats far away, two small figures in a small bright square of ring far below, two '
     'heavyweight boxers squaring off a step apart, one in white trunks and one in black trunks, their gloves dark '
     'red, the ring lit from above out of the picture.',
     'Seen from far back in the dark seats, a small bright ring: a dazed bare chested boxer in black trunks sits '
     'slumped on the pale canvas in the middle of the ring, while the other boxer, bare chested in white trunks, '
     'walks away to the far corner, both small figures with dark red gloves.',
     'Looking straight down from high above the middle of a boxing ring: a bare chested boxer lying flat on his back'
     ' on the pale canvas with his arms spread, and a referee in a white shirt and black trousers kneeling beside '
     'him with one bare hand raised, the boxer\'s gloves smooth dark red leather with plain dark red wrist cuffs.',
     'A boxing crowd seen from high up in a dark hall, many rows of men on their feet rising away up the tiers, every'
     ' man different: bald heads, grey hair, flat caps, moustaches, shirts and jackets of many colours, arms waving, '
     'cigar smoke hanging in a bright light above them, the far end of the hall lost in darkness.',
     'Looking steeply down from high in the dark seats onto a lit boxing ring, a tired boxer in plain black trunks and'
     ' red gloves sits slumped on a stool in the corner, and an older man in a white short sleeved shirt and grey'
     ' slacks stands beside him pressing a white towel to his forehead.')
show('old-game', ARCHIVE,
     'A whole 1950s baseball diamond seen from very high up in the top of the grandstand behind home plate, the batter'
     ' a tiny figure in a light uniform swinging at the plate, the pitcher and fielders small dots on the grass.',
     'Men in hats and white shirts cheer with their arms up in the crowded wooden stands of a 1950s ballpark, seen from'
     ' a distance, only the packed rows of people filling the picture.',
     'A 1950s baseball stadium seen from high up at the back of the top tier of the stands, looking down over rows of'
     ' seats to the small diamond far below.',
     'Two baseball players in baggy plain flannel uniforms walk side by side off the field toward the dugout, seen'
     ' from far off, the picture worn, flickering and scratched.')
show('soccer', SPORT,
     'Seen from far off across a small town field, a soccer player in an all plain red shirt and shorts dribbles past a'
     ' defender in all plain white, two small figures a few paces apart on the green grass, trees behind.',
     'Seen from far off at the side of the pitch, a grown goalkeeper of about thirty in plain green, a small figure,'
     ' dives sideways between his two goalposts as the ball flies past his hands into the net behind him.',
     'Two grown soccer players of about twenty five in plain red shirts run towards us with their arms raised after a'
     ' goal, seen from the front, their faces shouting with joy.',
     'A small crowd of grown men and women in coats stands along a low wooden fence, their faces towards us, seen from'
     ' across a grass field, trees behind them.')
show('horse-race', SPORT,
     'Three race horses with jockeys in plain solid coloured silks gallop round a turn strung out in single file, each'
     ' a full length behind the one in front, seen side on.',
     'A row of race horses lunge out of a long plain green metal starting gate as its front doors spring open on a flat'
     ' dirt track, seen side on from across the track so the horses and jockeys are small, each horse in its own open'
     ' stall.',
     'Seen from far off across a dirt track, three small race horses gallop past a white finish post, the leader a'
     ' length ahead of the other two, its jockey standing up high in the stirrups, open dirt between them and the'
     ' viewer.',
     'A long race course grandstand full of people in hats seen from far across an empty raked brown dirt track, the'
     ' white outer rail running along the foreground.')

# Films, by the kind of film.
show('film-comedy', FILM,
     'A small boy alone in a big house at Christmas stands at the top of the stairs beside a paint can hanging on a string in'
     ' a family comedy.',
     'Two clumsy burglars in woolly hats sprawl on their backs at the foot of a staircase in a family comedy.',
     'A panicking mother and father throw suitcases into the back of a wood panelled station wagon in the driveway of'
     ' their suburban house at dawn, seen from the side, in a family comedy.',
     'Two teenage boys, about seventeen and nearly adult, one tall and lanky with long dark hair and one stocky and'
     ' blond, in denim jackets stand in a cluttered garage playing plain red and plain black electric guitars with'
     ' small plain headstocks, in a teen comedy.',
     'A bride and groom run hand in hand down the steps of a church, a few guests behind them tossing small pinches of'
     ' rice from little paper bags, in a comedy.',
     'A man in a crumpled suit sits on a curb in the rain beside a cardboard box of his things in a comedy.')
show('film-action', FILM,
     'A spy of about forty in a tuxedo stands beside a long low silver sports car on a mountain road, the car seen side'
     ' on from the front door back, snowy peaks behind, in a spy thriller.',
     'A man in a leather jacket flies flat out through the air towards the viewer, his whole body and legs stretched'
     ' out behind him a little above the ground, a warehouse bursting into a ball of fire far behind him, in an action'
     ' movie.',
     'A helicopter chases a speeding car along a canyon road in an action movie.',
     'A man of about thirty in a leather jacket lies face down flat on the roof of a moving train, both hands clamped'
     ' over the roof edge, his hair blown back, a wide dry American prairie rushing past, in an action movie.',
     'A fighter pilot in a plain grey helmet and oxygen mask sits in the cockpit of a 1980s jet fighter seen from just'
     ' outside the canopy, his gloved right hand on a short stick between his knees, white clouds and blue sky behind,'
     ' in an action movie.',
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
     'A plain clothes detective of about forty in a brown leather jacket and jeans stands in a city alley at night, his'
     ' right arm hanging straight down by his side with a big revolver in his hand, its barrel pointing at the wet'
     ' ground by his foot, in a police thriller.',
     'A few small loose diamonds glint in the bottom of an open black briefcase that rests on a plain bare wooden box'
     ' in a dim warehouse, in a crime movie.',
     'Two plain dark 1970s sedans with bare doors race down a steep San Francisco style city hill, the first one'
     ' airborne over the crest, seen side on, in a police thriller.',
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
     ' sits at a window writing a letter in a romantic drama.',
     'A man and a woman of about thirty laugh over two white cups of coffee at a bare table by a diner window, snow'
     ' falling outside on a quiet street, in a romantic comedy.')
show('film-western', FILM,
     'A gunslinger with a big moustache stands with his empty hands loose at his sides in the dusty main street of a'
     ' wooden town, only the wooden grip of his revolver showing above the holster on his hip, plain bare wooden'
     ' storefronts behind him, in a western.',
     'Three riders on horseback cross a desert canyon in single file in a western.',
     'Cowboys play cards at a round wooden table in a dim wooden saloon with bare plank walls, oil lamps and a long bar'
     ' behind them, in a western.',
     'A sheriff of about fifty with a grey moustache, in a plain collarless cotton shirt and a dark waistcoat with a'
     ' small tin star pinned to it, sits on the porch of a wooden house watching the sun set over the prairie, in a'
     ' western.',
     'A plain dark red stagecoach with bare painted sides races across a dusty plain, pulled by four galloping brown'
     ' horses in two pairs one behind the other, seen side on from a distance, a cloud of dust trailing behind it, in a'
     ' western.')
show('film-war', FILM,
     'The cramped control room of a Second World War submarine in red light, two sailors in plain dark caps seated at'
     ' round brass dials and pipes, and an officer standing with both hands on the two side handles of a plain vertical'
     ' steel periscope tube, his eye pressed to it, in a war movie.',
     'A plain dark grey submarine with a smooth bare tower breaks the surface of a grey sea, white water pouring off'
     ' its deck, in a war movie.',
     'American soldiers in round steel helmets and green fatigues wade through a jungle river with their black M16'
     ' rifles held over their heads, in a Vietnam war movie.',
     'Inside the cramped grey steel control room of a submarine lit dim red, a naval officer of about forty in a plain'
     ' dark sweater wearing plain black headphones looks up at the pipes and rivets of the low ceiling as the hull'
     ' creaks, in a war movie.',
     'Tall white columns of sea water from depth charges burst up out of the grey sea behind the stern of a grey'
     ' warship, in a war movie.')
show('film-future', FILM,
     'A soldier with a riveted steel plate over half his face and a round glowing blue glass eye stands in a rainy'
     ' alley lit by plain pink and green neon tubes bent into simple lines and circles, in a science fiction action'
     ' movie.',
     'A motorcycle rider in black leather is chased down an empty desert highway by a huge rusty black truck, the truck'
     ' seen from the side so its front is hidden in dust, in a science fiction action movie.',
     'A figure walks through sparks and molten steel in a dark factory in a science fiction action movie.',
     'A woman of about thirty in a leather coat holding a shotgun stands in the doorway of a wrecked bar, the bare'
     ' cracked plaster walls behind her scorched and empty, broken stools on the floor, in a science fiction action'
     ' movie.',
     'A dull scorched metal robot hand reaches up out of a heap of grey concrete rubble under a smoky dark sky, in a'
     ' science fiction action movie.')
show('film-epic', FILM,
     'A herd of woolly mammoths crosses a snowy valley in an adventure movie.',
     'A prehistoric sabre toothed cat, heavy and stocky with a plain even sandy tawny coat like a lion, thick forelegs,'
     ' a short stubby bobbed tail and two very long curved fangs reaching down below its chin, snarls on a rock, in an'
     ' adventure movie.',
     'Two people in fur coats stand looking up at a woolly mammoth in an adventure movie.',
     'Three Stone Age people in animal furs sit round a fire in a cave, their hands resting on their knees, faint'
     ' simple reddish ochre stick figure deer and bison on the rough rock wall behind them, in an adventure movie.',
     'A wide frozen plain under a low sun with a line of tiny figures walking in an adventure movie.')
show('film-art', ' A still image from a 1970s European art film shot on 35mm film, muted colour, fog and soft film'
                 ' grain. The picture fills the whole image edge to edge.',
     'A man in a long coat walks across a flooded field in fog.',
     'A candle burns on the floor of an empty ruined room, a single drop of water falling from the ceiling into a small'
     ' still puddle beside it.',
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
     'A pair of small glowing green animal eyes low down between the dark trunks of a forest at night, the rest of the'
     ' creature hidden in shadow.',
     'Villagers of all ages in long coats, each holding a burning torch high in one hand, gather in the street of an'
     ' old German village at night, the bare timbered house walls lit orange by the flames.')
show('critics', VIDEO,
     'A balding man of about fifty with a grey fringe of hair in a sweater vest and a woman of about forty five in a'
     ' blazer sit side by side in red velvet seats in the front row of an old cinema balcony, the gilded rail in front'
     ' of them and the dim hall below, both with their hands resting in their laps as they argue.',
     'A balding film critic in a sweater vest shakes his head, seen from the chest up.',
     'A woman film critic in a blazer laughs, seen from the chest up.',
     'A beam of light from the small projection window high in the back wall shines out over rows of empty red cinema'
     ' seats in a dark old cinema.')
show('bad-movie', LOCAL,
     'A goofy man in his forties with a small neat dark moustache trimmed short above his lip and a bow tie, seen '
     'side on from the end of his row, sits alone in an empty old cinema with a big glass bowl of popcorn on his lap,'
     ' staring up to the left at the screen out of the picture, his face lit by its glow, the dark red velvet backs '
     'of the seats in front of him, gilded plaster walls in the dark beyond.',
     'A frame of a grainy black and white 1950s science fiction film, soft and scratched: a giant lizard monster with a'
     ' scaly head, a long tail and a gaping mouth towers over a small model city of cardboard buildings under a dark'
     ' sky.',
     'A frame of a soft grainy black and white 1950s science fiction film: a small toy flying saucer hangs on a thin'
     ' string over a flat desert under a pale sky.',
     'A goofy man in his forties with a thin moustache and a bow tie laughs and points up at a big cinema screen filled'
     ' by a giant grey lizard monster towering over a small model city, only the monster and the buildings on it.')

# A network's evening.
show('high-school', SITCOM,
     'Teenagers about seventeen, nearly adult, in colourful early 1990s clothes and plain solid coloured shirts talk at'
     ' a row of blue school lockers in a school hallway, the tiled ceiling along the top of the picture.',
     'A teacher of about fifty stands at a plain blackboard in front of a class of bored teenagers about seventeen,'
     ' nearly adult, slumped at their desks, in a classroom with cream walls, tall windows and its tiled ceiling in'
     ' view.',
     'Four teenagers about seventeen, nearly adult, in plain solid coloured shirts sit in a diner booth with'
     ' milkshakes, the chrome trim, red seats and low white ceiling of the diner around them.',
     'Seen through the windshield of a car, a teenage girl about seventeen, nearly adult, grips the steering wheel on'
     ' the left, and a terrified driving instructor of about fifty with a grey moustache sits in the passenger seat on'
     ' the right.',
     'Teenagers about seventeen, nearly adult, in plain solid coloured clothes dance under paper streamers in a school'
     ' gym, its high ceiling of steel beams and its brick walls in view.')
show('irish', SITCOM,
     'A priest of about sixty in a black suit and white collar talks to a farmer of about fifty in a flat cap outside a'
     ' whitewashed stone pub with a plain green door in a small Irish village, green hills behind under a soft grey'
     ' sky.',
     'A housekeeper of about sixty in an apron pours tea from a brown teapot into a cup held by a priest of about fifty'
     ' sitting at the table, in an old fashioned kitchen with whitewashed walls and dark wooden ceiling beams.',
     'Three men of about fifty in plain wool sweaters sit laughing at a small table in an Irish pub, plain pint glasses'
     ' of dark stout on the table, the low beamed ceiling and dark wood panelled walls close around them.',
     'Outdoors on a narrow country lane between grassy banks in Ireland, a crowd of white sheep fills the lane from'
     ' side to side in front of a small old car that has stopped, green fields and a grey sky beyond.',
     'Outdoors, a priest of about sixty in a black suit rides an old bicycle along a narrow country lane between low'
     ' dry stone walls, green fields and hills beyond under a soft grey Irish sky.')
show('network-news', VIDEO,
     'A distinguished grey haired news anchor in a dark suit sits at a large desk, a deep blue backdrop with a globe behind'
     ' him.',
     'A large newsroom seen wide from the back, a man and a woman small behind a long curved wooden desk lit warm in'
     ' the middle, rows of empty desks with boxy grey tube monitors showing plain blue pictures around them, deep blue'
     ' walls.',
     'A correspondent in an overcoat with a microphone stands in front of a domed capitol building.',
     'Green farmland seen from high above in the open air, a country road below blocked by a line of olive green army'
     ' trucks with canvas backs parked across it.',
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
     'Two men crouch low behind a tall stack of plain unpainted wooden crates on a dock at night, only their heads,'
     ' shoulders and pistols showing past the side of the stack, a heavy bearded man in a leather jacket and a thin'
     ' clean shaven young man in a denim jacket, in a gun fight in a 1980s action series.',
     'Seen from far across a dark parking lot, two policemen in plain light blue short sleeved shirts, one heavy set'
     ' and balding, one young and thin with dark hair, sit on the front fender of a plain brown sedan parked side on,'
     ' paper bags of takeout between them, a low roadside diner of lit windows behind them, in a 1980s action'
     ' series.')
show('space', DRAMA,
     'The bridge of a starship, a crew in plain coloured uniforms, smooth and bare from shoulder to waist, seated at'
     ' grey consoles, and a captain of about fifty in the middle chair facing a screen full of stars.',
     'A tall bald man of about fifty playing an alien ambassador, his own human face with deep set eyes and a heavy'
     ' jaw, thick bony ridges of makeup running across his forehead and over the top of his head, in a high collared'
     ' coat of dark red and silver quilted fabric, seen from the chest up, in a 1980s science fiction series.',
     'A model starship flies past a ringed planet in a 1980s science fiction series.',
     'Three crew members in coloured uniforms that are plain from collar to boots stand well apart on a rocky planet'
     ' under a purple sky, seen from a little way off, their empty hands down at their sides and a small ray gun'
     ' resting in a holster at each hip, in a 1980s science fiction series.',
     'An engine room with a glowing column and an engineer at the controls in a 1980s science fiction series.')
show('action-team', DRAMA,
     'Four tough men stand in front of a black van in a desert scrapyard in a 1980s action series.',
     'A man in plain dark coveralls and an old flip up welding hood of black leather with a small rectangle of dark'
     ' green glass over his eyes holds a thick grey steel plate flat against the side door of a black van in a barn and'
     ' welds along its edge, a bright white arc and a shower of orange sparks where the plate meets the door, in a'
     ' 1980s action series.',
     'A big orange fireball and black smoke burst up beside a dirt road, an empty open green army jeep overturned in'
     ' the dust in front of it, tipped over onto its side with its dark underside and all four wheels turned towards'
     ' the viewer and the top of its windshield frame resting on the ground, seen whole from a distance, in a 1980s'
     ' action series.',
     'A grinning heavy built man in his late fifties with a bald head, a fringe of grey hair and a full bushy grey'
     ' beard, a fat cigar clamped in his teeth, seen from the waist up a few paces off, in a 1980s action series.',
     'Seen from far off and side on, a van speeds along a desert road leaving a trail of dust, and a small dark'
     ' helicopter follows low behind it in the same direction, in a 1980s action series.')
show('strange', DRAMA,
     'A man of about fifty in a trench coat stands in a foggy graveyard at night, looking straight into the lens as he'
     ' speaks, a few plain worn grey headstones far behind him in the fog, in a documentary series about the'
     ' unexplained.',
     'A dim misty forest seen from far off, a dark smudge of an upright shape half hidden between distant tree trunks,'
     ' out of focus and hard to make out, in a documentary series about the unexplained.',
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
     ' and sticking straight up, and two children of about ten and twelve.',
     'A mother of about forty in an apron stands in a homely kitchen full of white smoke holding a plain red fire'
     ' extinguisher by its handle, its smooth back towards us, white foam covering a burnt pan on the stove beside her,'
     ' wooden cupboards around her.',
     'A family of four laughs round a dinner table in a cosy dining room with flowered wallpaper, a hanging lamp over'
     ' the table, a father of about forty five with a moustache, a mother of about forty with curly hair, a girl of'
     ' about twelve and a boy of about nine.',
     'Two children sit side by side on a carpeted staircase between papered walls, a girl of about twelve cupping one'
     ' hand to the ear of a younger boy of about nine, who listens with wide eyes, a little space between their faces.',
     'A cosy living room seen from beside the sofa, looking across the room to the open front door on the far wall,'
     ' where a grinning neighbour of about fifty in a loud Hawaiian shirt covered in big orange and green flowers leans'
     ' his head and shoulders in.')
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
     'A balding man in his fifties in a brown cardigan sits in an armchair holding a big opened newspaper up in front of'
     ' his face so that only the top of his head shows over it, its pages a soft grey blur, while his wife in her'
     ' fifties in a floral blouse talks at him from the sofa beside, in a British comedy series, seen close in a cosy'
     ' English sitting room with flowered wallpaper behind them and the white ceiling in view.')

# What comes on a tape and is not on the air.
show('fitness', VIDEO,
     'An aerobics instructor of about thirty, a woman with a high ponytail in a plain purple t-shirt, stands on a low'
     ' platform facing the viewer with her arms raised, a class of grown men and women in plain solid coloured baggy'
     ' t-shirts behind her copying her, seen close from the waist up, in a 1980s exercise class in a gym.',
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
     'A mechanic of about forty five in plain blue overalls stands beside a big old American sedan with its hood raised'
     ' in a garage, pointing down into the large engine, the car seen from the side so its front is hidden by the'
     ' raised hood.',
     'A mechanic of about forty in plain blue overalls kneels beside a big old American sedan seen side on in a garage,'
     ' the car raised on a plain red jack so its front wheel hangs clear of the concrete floor, his hands on the wheel.',
     'A plain red metal toolbox with its drawers pulled open, full of spanners and screwdrivers, stands on a concrete'
     ' garage floor beside an old car tyre, seen close up.',
     'A mechanic in plain blue overalls leans over the open hood of an old car with his arms down inside the engine'
     ' bay, seen from the side a few paces back.')
show('first-aid', TAUGHT,
     'A paramedic of about forty in a plain white short sleeved shirt and dark trousers wraps a white bandage round the'
     ' forearm of a fully dressed volunteer of about thirty who sits upright on the edge of a table, in a plain beige'
     ' room.',
     'A paramedic of about thirty five in a plain white shirt and dark trousers kneels on a grey carpet with both hands'
     ' pressed on the chest of a plain pale plastic training dummy lying on its back, in a plain beige room.',
     'Rolled white bandages, a roll of white tape, a pair of round ended scissors and a few folded gauze pads laid out'
     ' in a row on a white table, seen from above.',
     'A woman doctor in a white coat sits in a doctor\'s office, looking straight out of the frame towards the viewer'
     ' as she speaks.',
     'A bandage is wrapped round a lower leg resting on a stool.')
show('guns', TAUGHT,
     'A man of about fifty in a hunting vest stands at the wooden bench of an outdoor shooting range, a plain wooden'
     ' bolt action hunting rifle lying flat on the bench in front of him, seen side on.',
     'A plain dark blued revolver with smooth unmarked metal lies on a grey cloth on a table, seen from above, its'
     ' cylinder swung out to the side on its hinge and still attached, six brass cartridges standing upright in a neat'
     ' row beside it.',
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
     'A man of about fifty in a cap with a plain front sits in a small plain green aluminium boat on a calm lake and'
     ' lifts a landing net out of the water, a big largemouth bass curled up wholly inside the deep mesh of the net.')
show('farm', OUTDOOR,
     'A girl of about ten holds out a handful of feed through a wide gap between the wooden rails of a fence, and a'
     ' white goat on the other side reaches its head through the gap to eat, at a small farm petting zoo in summer.',
     'A rooster stands on a fence post at sunrise.',
     'Black and brown cows graze in a wide open rolling green pasture in Kentucky, a long white painted wooden board'
     ' fence running across the grass, a few big lone oak trees and low wooded hills behind, under a hazy summer sky.',
     'A farmer of about sixty in a straw hat drives an old plain red tractor with a smooth bare hood across a wide flat'
     ' field of young corn in Kentucky, a wooden tobacco barn and a line of trees far behind, under a hazy summer sky.',
     'Two muddy piglets lie asleep on their sides a little apart in deep golden straw in a dim barn, their eyes'
     ' closed, seen from above.')
show('trade', TAUGHT,
     'An old grey metal house fuse box on a basement wall with its door open, a row of round glass fuses screwed into'
     ' round sockets inside like small light bulbs, an electrician of about fifty in a tool belt standing beside it'
     ' seen from behind.',
     'A tangle of loose coloured electrical wires with bare copper ends, a few small orange twist on caps on the joined'
     ' ends, lying on a workbench beside a roll of black tape and a pair of red handled wire strippers, seen close up.',
     'An old wooden cabinet television turned round on a workbench with its back panel off, and a repairman of about'
     ' fifty sitting on a stool behind it reaches one hand in with a soldering iron, his head and body outside the'
     ' cabinet, a thin wisp of smoke rising.',
     'A welder of about forty in a plain dark welding helmet and leather gloves bends over a steel beam in a dim'
     ' workshop, a bright white point of light at his torch with a short fan of orange sparks spraying out from it.',
     'A man in a hard hat stretches wire along a line of fence posts.')
show('sewing', TAUGHT,
     'An old woman of about seventy smiles at a full size plain white electric sewing machine with smooth bare sides on'
     ' a wooden table in a cosy room.',
     'A spool of red thread and a round red pincushion stuck with pins lie beside a folded piece of denim on a table,'
     ' seen close up.',
     'A plain smooth cream fabric dressmaker\'s dummy dressed in a half finished cotton blouse stands in a sewing room,'
     ' a tape measure hanging round its neck.',
     'Long heavy steel dressmaking shears lie closed on a bare wooden table beside a length of blue cloth cut into two'
     ' halves along a straight white chalk line, the two halves pulled a little apart.')
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
     'A brown bear stands at the lip of a waterfall in the white water at the very top of the falls, its mouth open as'
     ' a dull silvery salmon leaps up the falls toward it.',
     'A spiral galaxy and stars in deep space in an astronomy documentary.')
show('travel', ' A still image from a 1980s travel documentary shot on 16mm film on a sunny day, soft with film grain'
               ' and warm muted colours. The picture fills the whole image edge to edge.',
     'The roofs of Paris with the Eiffel Tower beyond them.',
     'A busy street market in a West African city, seen from above.',
     'The painted gate of a Korean palace with a modern city behind it.',
     'A single slim white rocket stands on its launch pad beside the sea under a clear sky.',
     'A row of old tanks in front of a brick army building.',
     'A river barge passes under a steel bridge.')
show('birthday', HOME,
     'A child blows out the candles on a birthday cake with the family round the table.',
     'Three children in paper party hats sit at a table at a birthday party in a living room.',
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
     'An empty motel swimming pool under palm trees with sun loungers and a striped towel.',
     'A mother and father in shorts, t-shirts and sun visors stand side by side squinting in the sun in front of a tall'
     ' plain white space rocket with black bands on display on a green lawn outside a museum in Florida, palm trees and'
     ' blue sky.')
show('christmas', HOME,
     'Children in pajamas open presents under a Christmas tree.',
     'A family in paper crowns sits round a table with a roast turkey.',
     'A school nativity play on a small stage, two children dressed as shepherds in striped robes and two as angels in white'
     ' with tinsel, each child different, seen from the audience.',
     'A man in a Christmas sweater holds up a pair of socks and pulls a face.')
show('pets', HOME,
     'Two kittens, one ginger and one grey, sit apart in a cardboard box on a kitchen floor.',
     'A dog leaps to catch a flying disc in a back yard.',
     'A child hugs a big shaggy dog on a porch.',
     'A kitten bats at a ball of wool on a carpet.')
show('stage', HOME,
     'A boy in a white shirt plays a saxophone on a school stage.',
     'A row of children sing on risers in a school hall, seen from the back of the hall.',
     'Teenagers in cloaks act on a school stage with painted scenery.',
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
