"""What is on television: the shows a set can be showing and the shots the mod has of each.

A show is a handful of shots, each a still made from a text prompt alone (generate.py) and packed four wide for three
high as screen-<show>-<n> (pack.py). A set cuts from one shot of its show to the next and moves across each as it is
drawn (Screens.java). The shows and their names are the mod's own.

Which of the game's broadcasts and tapes is which show is told by schedule.py, from patterns kept outside the repository.
"""

VIDEO = (' A still frame from a 1993 American television broadcast recorded off air onto VHS, soft analog video with slight'
         ' colour bleed and muted colours.')
FIELD = (' A still frame from 1993 American television news footage recorded off air onto VHS, handheld and slightly'
         ' unsteady, soft analog video with muted colours and slight colour bleed.')
LOCAL = (' A still frame from a low budget 1993 local public access television show on a cheap studio set with flat lighting,'
         ' soft analog video recorded onto VHS with muted colours.')
SITCOM = (' A still frame from an early 1990s American sitcom taped on a multi camera studio set with bright even lighting,'
          ' recorded off air onto VHS, soft analog video with muted colours.')
DRAMA = (' A still frame from a 1980s American television drama shot on 16mm film and transferred to video, recorded off air'
         ' onto VHS, soft with film grain and muted colours.')
FILM = (' A full frame still from a 1980s movie shot on 35mm film, the picture filling the whole frame edge to edge, soft'
        ' with film grain and muted colours as if taped off air onto VHS.')
CEL = (' A frame from a 1980s Saturday morning cartoon, cel animation painted by hand with flat colours, thick black outlines'
       ' and visible paint texture, recorded off air onto VHS.')
HOME = (' A still frame from home camcorder footage taped around 1990, VHS with washed out colours, soft focus, slight blur'
        ' and amateur framing.')
CLIP = (' A still frame from an early 1990s music video, soft analog video with coloured stage lights and haze, recorded off'
        ' air onto VHS with muted colours.')
SPORT = (' A still frame from a 1993 American televised sports broadcast shot through a long lens, soft analog video recorded'
         ' off air onto VHS with muted colours.')
NATURE = (' A still frame from a 1980s nature documentary shot on 16mm film through a long lens and recorded off air onto VHS,'
          ' soft with film grain and natural muted colours.')
ARCHIVE = ' A still frame from 1950s black and white newsreel footage, soft focus with heavy film grain and scratches.'
TAUGHT = ' A still frame from an early 1990s instructional video on VHS, flat lighting, soft analog video with muted colours.'

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
     'A serious middle aged male news anchor in a dark suit and striped tie, with an ordinary lined face, seen from the chest'
     ' up against a plain blue studio backdrop.',
     'Two news anchors, a man and a woman, sit a little apart at a long news desk seen from a wide camera, a newsroom of desks'
     ' and glowing monitors behind them.',
     'A woman news anchor with short brown hair and an ordinary face sits at a desk looking off to one side, a monitor over her'
     ' shoulder showing a simple green map of a river valley with no words.',
     'A television news studio seen from behind two big studio cameras on pedestals, one anchor small at a lit desk under rows'
     ' of lights.')
show('news-field', FIELD,
     'A bareheaded woman television reporter in a beige trench coat holds a microphone in front of an army roadblock of green'
     ' trucks and coiled barbed wire at dusk.',
     'Soldiers in gas masks and green fatigues stand at a checkpoint on a rural highway, concrete barriers across the road under'
     ' an overcast sky.',
     'A long traffic jam of 1980s cars on a two lane highway through green farm fields, seen from a helicopter high above.',
     'A handful of worried civilians in summer t-shirts and jeans stand behind a tall chain link fence, seen past the backs of'
     ' two soldiers in green fatigues on the near side.',
     'A woman reporter stands on a roadside at night lit by a single camera light, the red and blue lights of police cars'
     ' blurred far behind her.')
show('pundit', VIDEO,
     'A stern grey haired man in a dark suit sits behind a wooden desk pointing one finger at the camera, an American flag and'
     ' a red curtain behind him.',
     'An angry grey haired television pundit in a dark suit leans toward the camera mid shout, seen from the chest up in front'
     ' of a red curtain.',
     'A television opinion show set seen wide, one grey haired host sitting in a leather chair at a wooden desk in the middle'
     ' with a flag on a pole and red curtains behind him.',
     'A grey haired man in a dark suit leans back in a leather chair with his arms folded and a smug look, a red curtain behind'
     ' him.')
show('general', FIELD,
     'An army general in a green dress uniform with rows of ribbons speaks at an outdoor podium crowded with microphones, a few'
     ' soldiers standing at ease in a line behind him.',
     'A grim faced army general in a green uniform and peaked cap, seen from the chest up outdoors under a grey sky.',
     'A row of television cameras on tripods faces an empty outdoor podium with a bank of microphones, reporters standing in a'
     ' loose line far behind it out of focus.',
     'A field command post of green army tents and a tall radio mast in a summer field, jeeps parked beside it and two soldiers'
     ' walking between the tents.')
show('expert', VIDEO,
     'A professor with a grey beard and a tweed jacket sits in an armchair being interviewed in an office lined with books, his'
     ' hands resting in his lap.',
     'A scientist in a white lab coat stands at a laboratory bench with glass flasks and a microscope on it, looking toward'
     ' someone off camera.',
     'A grey laboratory microscope stands on a white bench with a glass slide clipped under its lens and a rack of test tubes'
     ' beside it, seen close up.',
     'A woman doctor in a white coat with a stethoscope round her neck talks to an interviewer off camera in a pale green'
     ' hospital corridor.')
show('unrest', FIELD,
     'A car burns in the middle of an empty city street at night, three police officers in riot helmets with plain clear'
     ' shields standing in a line a little way off.',
     'Five protesters stand in a loose line on the steps of a government building with columns, holding up blank white'
     ' placards, plain unlettered card with nothing on it.',
     'A column of olive green canvas backed army cargo trucks of the 1980s drives down a city avenue between tall buildings, a'
     ' few people watching from the sidewalk.',
     'A smashed shop window seen close from the sidewalk at dawn, jagged broken glass in its frame and shards across '
     'the pavement, an emptied display behind it, the street around it empty of people.',
     'A city seen from high above at night, looking down, several fires burning between the dark buildings and smoke rising.',
     'A short line of shoppers with empty shopping carts waits outside a small town supermarket, the shelves seen through its'
     ' windows bare.')
show('emergency', VIDEO,
     'An empty television news desk with the chair pushed back and loose papers left on it, the studio lights still on.',
     'A tired official in shirt sleeves and a loosened tie sits at a plain desk reading from a sheet of paper laid flat in'
     ' front of him, an American flag beside him.',
     'A plain blue television backdrop with a simple flat gold eagle emblem printed in the middle and no words, lit unevenly by'
     ' studio lights.',
     'An empty television studio with one studio camera left pointing at an empty chair in a pool of light.')
show('local-desk', VIDEO,
     'A male local news anchor in a brown suit sits at a wood panelled news desk, a photograph of a small city skyline behind'
     ' him.',
     'A woman local news anchor with big permed hair and a green blazer sits at a wood panelled news desk.',
     'Two local news anchors, a man and a woman, sit a little apart at a wood panelled desk and turn to each other smiling,'
     ' plain blue panels behind them.',
     'A local weatherman in a tan suit stands beside a weather map of Kentucky with simple sun and cloud symbols and no words,'
     ' one arm raised toward it.')
show('talk', VIDEO,
     'A woman talk show host and a male guest sit in two armchairs facing one another a little apart, a potted plant and a'
     ' window onto a city at night behind them.',
     'A woman talk show host with short hair listens with her chin resting on her hand, seen from the shoulders up against a'
     ' dark blue studio.',
     'A bearded man in a corduroy jacket sits in an armchair talking with one open hand raised, his eyes on the host just off'
     ' camera, against a dark blue studio.',
     'A late night talk show set seen from the back of the studio, two people small in armchairs beside a low table under the'
     ' lights.')
show('local-field', FIELD,
     'A male reporter in a windbreaker stands with a hand microphone at a line of plain yellow police tape with no printing'
     ' across a rural road, plain white patrol cars with no markings parked side on behind him.',
     'A white television news van is parked on a grass verge beside a highway, a tall thin straight pole rising from '
     'its roof straight up into the sky like a flagpole with a small round dish at the very top, its side plain and '
     'unlettered.',
     'A dark country road at night seen through a car windshield, the headlights picking out abandoned cars left at angles'
     ' along the verge.',
     'Small figures run across a field at dusk, seen from far off through a shaky camera.',
     'A frightened male reporter in a suit, his hands empty, crouches alone behind a parked car at night, looking back '
     'over his shoulder, lit by a harsh white light.')
show('statement', VIDEO,
     'A man in a dark suit speaks at a podium in front of a blue curtain with two flags on poles beside it.',
     'Rows of seated reporters in suits seen from the podium, several of them raising one hand to ask a question, a plain bare'
     ' wall behind them.',
     'A white government building with columns lit up at night.',
     'A grave man in a dark suit and red tie speaks into a cluster of microphones, seen from the chest up.')

# Home and hobbies.
show('cook', VIDEO,
     'A cheerful woman in an apron stands at the stove of a television studio kitchen, stirring a pot with a wooden spoon.',
     'Chopped carrots and onions lie on a wooden board beside a kitchen knife on a studio kitchen counter, seen from above.',
     'A roast chicken on a platter surrounded by vegetables sits on the counter of a television studio kitchen.',
     'A cheerful woman in an apron stands behind a kitchen counter smiling at the camera, a mixing bowl and a whisk on the'
     ' counter in front of her.',
     'A pan of vegetables steams on a gas stove with a wooden spoon resting in it, seen close up.',
     'A tall round layer cake covered all over in thick smooth white buttercream icing with piped rosettes round its top'
     ' edge sits on a cake stand on the counter of a television studio kitchen.')
show('woodwork', VIDEO,
     'A bearded man in a plaid shirt and safety glasses saws a pine plank laid across two sawhorses in a home workshop, a hand'
     ' saw in his right hand.',
     'A claw hammer and a few nails lie on pine boards on a workbench, one nail half driven into the wood, seen close up.',
     'In a home workshop with a wooden workbench, hand tools hanging on a pegboard wall and sawdust on the floor, a '
     'bearded man in a plaid shirt stands proudly beside a finished wooden bookcase.',
     'A pegboard over a workbench with a few ordinary hand tools hung well apart on it: a claw hammer, a hand saw, a pair of'
     ' pliers and a screwdriver.',
     'A wooden hand plane sits on a pine board on a workbench, a long curl of pale wood shaving lying beside it.',
     'A bearded man in a plaid shirt with a pencil behind his ear leans over a long pine board on a workbench, checking it by'
     ' eye along its length.')
show('survival', FIELD,
     'A rugged man in a khaki shirt and a bandana crouches by a small campfire in a forest and talks to the camera.',
     'A rugged man in a khaki shirt kneels beside a river with a trout laid on a flat rock in front of him.',
     'A lean to shelter of branches and leaves stands in a forest clearing.',
     'A simple snare of cord tied to a bent sapling at the edge of a forest path, seen close up.',
     'A rugged man in a khaki shirt walks through tall ferns and looks back at the camera.',
     'A pot of water boils over a campfire at dusk, a small tent behind it.')
show('advert', VIDEO,
     'A man and a woman in bright sweaters stand a little apart behind a table of kitchen gadgets with pleasant closed mouth'
     ' smiles, a studio audience out of focus behind them, on a shopping show.',
     'A ripe tomato cut into neat slices lies on a white cutting board beside a kitchen knife on a studio counter, seen close'
     ' up.',
     'A shiny electric slow cooker with steam rising from its lid sits on a kitchen counter in a product shot.',
     'A studio audience in rows claps and smiles, seen from the stage with the back rows out of focus.',
     'A smiling presenter in a bright sweater stands behind a counter pointing at a white kitchen blender standing on it.',
     'A cosy living room lit by a bright ceiling lamp, a mother and a father sitting a little apart on a sofa.')
show('doctor', VIDEO,
     'An elderly doctor in a white coat sits on the edge of a desk in a consulting room and talks to the camera.',
     'A smiling elderly couple walk arm in arm along a path through a sunny park.',
     'An elderly doctor in a white coat stands beside his desk, a small brown bottle of pills with a plain unlettered label'
     ' on the desk in front of him.')
show('bear', VIDEO,
     'A plush teddy bear in a red bow tie sits on a child\'s bed in a sunny bedroom in a toy commercial.',
     'Two small children in t-shirts sit on a playroom carpet with a big plush teddy bear between them in a toy commercial.',
     'A plush teddy bear sits on a picnic blanket on the grass beside one small white toy teapot and two toy cups in a toy'
     ' commercial.')
show('phone-in', LOCAL,
     'A middle aged man in a cardigan sits at a desk with a telephone and a mug, a wood panelled wall behind him.',
     'A middle aged man in a cardigan sits at a desk holding a telephone receiver to his ear and nodding.',
     'A beige push button desk telephone with plain unmarked buttons and one small red light lit sits on a wooden desk, seen'
     ' close up.',
     'A middle aged man in a cardigan laughs with his head thrown back at a desk with an old beige telephone on'
     ' it, a plain painted backdrop behind him.')

# Entertainment.
show('hollywood', VIDEO,
     'A woman entertainment news presenter in a pink blazer with ordinary hair and make up stands in front of a backdrop of'
     ' spotlights and painted palm trees.',
     'Film stars in evening dress walk a red carpet at a night premiere, a row of photographers out of focus behind a rope.',
     'A film set with a big film camera on a crane, lamps on stands and an empty folding chair.',
     'A handsome actor in a tuxedo and an actress in a long high necked sequin gown stand a little apart on a red carpet,'
     ' waving.',
     'The front of an old cinema at night, its marquee lit by rows of bulbs round a plain blank white panel with no letters,'
     ' a few people outside.')
show('detectives', DRAMA,
     'Three men in plain grey and brown business suits and ties, two detectives and their grey haired captain, argue '
     'across a wooden desk covered in paper files and a desk lamp in a precinct office, no computers, nobody in '
     'uniform.',
     'A plain dark sedan with a red light on its dashboard is parked in a city alley at night.',
     'A detective in a tan raincoat waits in a dim hallway beside a closed door, seen from the side, his head bowed and'
     ' his ear turned to the door, listening.',
     'A suspect sits at a table in a bare interrogation room under a single lamp, a detective leaning on the table across from'
     ' him.',
     'Two detectives run across a rooftop a few paces apart, the city behind them.')
show('barrister', DRAMA,
     'An English barrister in a white wig and black gown stands to speak in an oak panelled courtroom.',
     'An elderly English judge in red robes and a short white curled wig sits on the raised bench of an oak panelled'
     ' courtroom, seen from the front looking down.',
     'An elderly man in a waistcoat sits at a desk by lamplight, writing in a diary.',
     'A nervous man in a grey suit stands in the dock of an English courtroom.',
     'A wet London street at night in the rain seen from across the road, one man in a dark overcoat under a black'
     ' umbrella walking past red brick terraced houses with black iron railings, puddles shining under the street'
     ' lamps.')
show('soap', VIDEO,
     'A woman with big blonde hair in a high necked evening dress and a man in a tuxedo argue in an opulent living room in a'
     ' daytime soap opera.',
     'A tearful woman with heavy eye make up and big hair, seen from the shoulders up in a daytime soap opera.',
     'A man lies in a hospital bed with a bandaged head, a woman in a fur coat sitting at the bedside, in a daytime soap'
     ' opera.',
     'A couple in evening dress stand on a terrace over a city at sunset in a daytime soap opera.',
     'A woman in a red dress slaps a man across the face at the foot of a mansion staircase, his head turned by the blow, in a'
     ' daytime soap opera.')
show('quiz', VIDEO,
     'A middle aged American game show host with a moustache and a grey side parting, in a sparkly blue jacket, holds a'
     ' long thin microphone on a dated 1980s set of orange and brown panels ringed with rows of round light bulbs.',
     'Three game show contestants stand a little apart behind podiums with buzzers, the podium fronts plain and unlettered.',
     'A game show contestant jumps for joy under falling confetti.',
     'A new car on a turning stage under coloured lights as a game show prize.',
     'A game show audience claps and cheers in rows beyond the stage lights, out of focus.')

# For children.
# The heroes are the mod's own: helmets with visors, green and orange, no capes, no masks with ears, no chest emblems.
OWN_HEROES = (' They are original characters in plain green and orange jumpsuits with round helmets with visors and small'
              ' backpacks, with no capes and no emblems.')
show('heroes', CEL,
     'A team of five space rangers stand in a row a little apart on a rooftop at night.' + OWN_HEROES,
     'A single space ranger flies alone with a jet backpack over a city skyline.' + OWN_HEROES,
     'Three space rangers stand well apart in a city street facing a giant grey robot.' + OWN_HEROES,
     'A villain in a long grey coat and a tall metal helmet open at the face throws his head back laughing in a dark lair with'
     ' a few big simple machines.',
     'A team of space rangers stand round a glowing table in a futuristic control room.' + OWN_HEROES)
show('puppets', VIDEO,
     'Two felt hand puppets, an orange fox on the left and a brown owl with big round eyes and a beak on the right, stand apart'
     ' in a painted cardboard forest in a children\'s puppet show.',
     'A smiling presenter in dungarees kneels beside felt puppets of a badger and a rabbit in a painted forest in a children\'s'
     ' puppet show.',
     'A felt hand puppet of a hedgehog with stitched eyes and soft fabric spines sits at a tiny table with a toy tea set in a'
     ' children\'s puppet show.',
     'A felt owl puppet looks out of a hole in a cardboard tree in a children\'s puppet show.',
     'Felt puppets of woodland animals sing in a row in front of painted trees in a children\'s puppet show.')
show('bunnies', ' A frame from a 1980s cartoon for very small children, cel animation painted by hand in soft pastel colours with'
                ' round simple shapes, thick outlines and visible paint texture.',
     'Three round pastel cartoon bunnies sit side by side in a meadow of flowers under a rainbow.',
     'Two round pastel cartoon bunnies build a tower of wooden toy blocks on a soft watercolour background, the paint '
     'uneven and slightly faded like a frame from an old worn film print.',
     'Three round pastel cartoon bunnies sit apart round a checked picnic blanket on flat green grass.',
     'A round pastel cartoon bunny stands on a green hill with a red balloon on a string.',
     'Three round pastel cartoon bunnies sleep in a row, each in its own little bed, under a smiling moon.')
show('kids-advert', VIDEO,
     'In a cosy family den with wood panelled walls, a brother and sister, the only two people in the room, sit at a '
     'small desk in front of a beige home computer whose monitor is a deep boxy tube screen with a bulging glass face, '
     'in a commercial.',
     'A loud salesman in a checked jacket spreads his arms wide in a store aisle stacked with plain unlettered boxes in a'
     ' commercial.',
     'A plastic toy robot stands on a kitchen table beside a few loose batteries in a toy commercial.',
     'A mother, a father and a child sit round a board game on a living room carpet in a commercial.')

# Public access.
show('tech', LOCAL,
     'A young man with glasses and a headset sits at a desk crowded with beige computers and circuit boards, a curtain behind'
     ' him.',
     'An open beige early 1990s desktop computer, a wide flat box with its lid off, lies on a desk with its circuit boards and cables showing, a screwdriver resting beside it,'
     ' seen close up.',
     'A boxy computer monitor glows with green lines and no readable words on a cluttered desk.',
     'A young man with glasses grins and holds up a green circuit board.')
show('interview', LOCAL,
     'A woman interviewer in a navy trouser suit and a silver haired senator in a suit sit in two armchairs facing one another,'
     ' a fern and a flag behind them.',
     'A silver haired senator in a suit answers a question, seen from the chest up.',
     'A woman interviewer in a blazer looks down at the notes in her lap.')
show('clown', LOCAL,
     'A clown with an orange wig and a red nose waves in front of a paper backdrop with balloons in a children\'s show.',
     'One clown with an orange wig and a red nose stands alone on a small bare studio stage, juggling three balls, one '
     'red, one yellow and one blue, all three up in the air above his hands, a painted backdrop of balloons and stars '
     'behind him.',
     'A clown with an orange wig talks to a sock puppet on one hand in a children\'s show.',
     'Small children sit cross legged on a carpet and laugh in a children\'s show.')
show('county', FIELD,
     'A woman presenter in a denim jacket stands by a white fence on a horse farm, rolling green hills behind her.',
     'A county fair on a summer evening, one ferris wheel lit up against the sky behind a row of striped stalls.',
     'An old man in overalls sits whittling a stick on the porch of a wooden house, seen from the yard.',
     'A covered wooden bridge with a roof and red painted board sides crosses a creek among green summer trees.',
     'A tall old black painted wooden tobacco barn with vertical plank siding and open vent boards standing alone at the edge'
     ' of a cut field at sunset, seen from the road.',
     'Two horses graze well apart behind a white fence.')
show('survivors', ' A still frame of amateur home video on VHS, lit by one harsh light, dim and grainy with muted'
                  ' colours.',
     'An exhausted man in a dirty flannel shirt sits at a desk in a dim television studio, a door barricaded with furniture'
     ' behind him.',
     'A tired woman with a bandaged arm talks to the camera in a dim television studio.',
     'A bareheaded young man crouches by a small fire burning in an old metal barrel in a dim empty garage.',
     'Stacks of food cans and plastic water jugs stand on the floor of a dim television studio, their labels plain and'
     ' unlettered.',
     'An empty news desk in a dim television studio with an office chair knocked over on its side on the floor beside it.')

# Music videos.
show('ballad', CLIP,
     'A singer in a white suit sits at a grand piano among candles and fog.',
     'A woman in a long coat sings in the rain on a city street at night, coloured light reflected in the wet road and the'
     ' store fronts dark and unlettered.',
     'A couple stand in silhouette on a beach at sunset.',
     'A singer with a microphone in soft blue light, seen from the shoulders up.',
     'A single red rose lies on the closed wooden lid of an old upright piano beside a burning candle in a dim room '
     'hazy with smoke and blue light.')
show('pop', CLIP,
     'Three young women in bright colour blocked baggy jackets and loose trousers dance a little apart on a white studio set.',
     'Four young men in denim jackets pose against a painted brick wall.',
     'A singer in a loose jacket with a headset microphone dances in front of four dancers in baggy clothes under coloured'
     ' lights.',
     'A young woman pop singer with big curly hair falling over her ears, in a loose white blouse buttoned to the '
     'collar, sings into a handheld microphone on a stage, seen from the waist up.',
     'Teenagers in t-shirts and baggy jeans dance with their arms in the air, seen from a distance under coloured lights.')
show('rock', CLIP,
     'A long haired rock band plays on a stage with jets of flame and a wall of amplifiers.',
     'A long haired guitarist plays a solo in a single spotlight.',
     'A drummer with long hair in a plain black sleeveless shirt sits behind a huge drum kit with two bass drums with '
     'plain black heads and a wide ring of toms and cymbals around him, under red stage lights.',
     'A male rock singer in scarves and a leather jacket screams into a microphone stand.',
     'A concert crowd with raised fists seen from the stage, out of focus in the haze.')
show('soul', CLIP,
     'A singer in a silk shirt stands singing softly in a loft lit by candles.',
     'A woman in a long satin dress sings beside a saxophone player.',
     'A man plays an electric piano in a dim studio in warm light, seen from the side.',
     'A couple slow dance in a dim room in front of a window onto the city lights.')
show('rap', CLIP,
     'Three rappers in baggy tracksuits and plain caps with no logos stand on a city basketball court beside a big portable'
     ' stereo.',
     'Two young men, one in a baseball jacket leaning on the front fender and one in a white t-shirt leaning on the '
     'door, with a long low 1960s convertible lowrider, deep red with chrome wire wheels, under orange street lights at'
     ' night, the street otherwise empty.',
     'A disc jockey in headphones stands at two turntables.',
     'Three young men pose on the stone steps of a city row house.')
show('club', CLIP,
     'Dancers seen far off in a dark night club under green lasers and smoke.',
     'A disc jockey stands in a booth among smoke and flashing lights.',
     'Silhouettes of dancers against a wall of flashing coloured lights.',
     'Glowing coloured shapes spin on a black background in early 1990s computer video art.')
show('eighties', CLIP,
     'A singer with big hair in a buttoned jacket with shoulder pads stands against a glowing neon grid.',
     'A man in a pastel suit stands alone at the front of a stage playing a keytar, a small white keyboard shaped like '
     'a guitar that hangs across his body from a strap over his shoulder.',
     'A red sports car drives along a highway at night under neon lights.',
     'Three men in pastel suits stand a little apart on a beach, one with an electric guitar, one with a bass guitar and one'
     ' with a keyboard on a strap.',
     'A dance class in loose t-shirts, sweatpants and leg warmers dances in a mirrored studio, seen from across the room.')

# Sport.
show('sports-desk', VIDEO,
     'Two sports presenters in blazers sit at a desk with a trophy standing on it between them, a plain blue backdrop '
     'behind them with a soft blurred photograph of a stadium at night.',
     'A sports presenter in a blazer stands beside a wall of television screens.',
     'A grinning sports presenter with a moustache sits at a desk with papers in front of him.',
     'A sweating athlete in a plain unlettered jersey sits in front of lockers in a locker room while a reporter beside him'
     ' holds a microphone up to him.')
show('baseball', SPORT,
     'A baseball pitcher in a plain white unlettered uniform throws from the mound, green outfield grass behind him.',
     'A baseball batter in a plain grey uniform swings at home plate, the catcher crouched behind him and the umpire behind'
     ' the catcher, seen from the pitcher\'s side.',
     'A baseball field seen from high behind home plate on a sunny afternoon, the stands full and far away.',
     'A baseball outfielder in a plain uniform leaps with his glove up at the low padded outfield fence of a ballpark.',
     'A baseball dugout seen from across the field, a row of small players in plain grey uniforms and dark caps leaning'
     ' on its rail in the deep shade of its roof, the green grass of the field in front.',
     'A baseball runner in a plain uniform with no number slides into a base in a cloud of dust.')
show('golf', SPORT,
     'A golfer in a polo shirt drives from the tee, a line of spectators behind him.',
     'A golfer crouches to line up a putt on a green.',
     'A golf ball rolls toward the hole and the flag on a green.',
     'A golf fairway runs between tall pines with a lake beside it.',
     'Seen from far away across a long empty fairway lined with tall trees, two small figures walk side by side, a '
     'golfer and his caddie, the caddie carrying a big golf bag on his shoulder, only the two of them on the green '
     'grass.')
show('poker', VIDEO,
     'Four players sit well apart round a green poker table under a low lamp in a casino, stacks of chips in front of them.',
     'Two men face each other across a green felt card table under a hanging lamp in a dim card room, short stacks of '
     'red and white poker chips in front of each, seen from across the room.',
     'A poker player in sunglasses and a cowboy hat stares across the table.',
     'A casino dealer in a waistcoat stands behind a green felt table with cards laid out in front of her.',
     'A pile of red, white and blue poker chips sits in the middle of a green felt poker table under a low lamp.')
show('racing', SPORT,
     'Seen from high up in a distant grandstand, a line of small open wheel racing cars in plain solid colours sweeps '
     'round a steeply banked oval corner, the grey concrete track tilted high up toward the outer wall.',
     'A pit crew in plain overalls changes the tires of a racing car.',
     'The view from the cockpit of a racing car down a straight, another car ahead.',
     'Racing cars sit two by two in a starting grid on a paved oval track, seen from above with the stands beside it.',
     'A racing driver in plain white overalls with no patches stands on a podium spraying champagne from a bottle.',
     'Stock cars in plain colours race side by side round a dirt oval.')
show('skating', SPORT,
     'A skateboarder in a helmet flies in mid air above a wooden half pipe, a few spectators watching.',
     'A skateboarder slides along a metal rail.',
     'A boy on a small bicycle flies in mid air over a dirt jump.',
     'Teenagers in plain t-shirts and baggy shorts cheer beside a skate ramp.')
show('boxing', ' A still frame from a 1970s televised boxing match shot on 16mm colour film, bright ring lights and cigarette'
               ' smoke, soft and grainy with faded colours.',
     'Two heavyweight boxers, one in white trunks and one in black, square off a step apart in a ring.',
     'A boxer in black trunks staggers back against the ropes after a punch, his opponent in white trunks stepping away.',
     'A referee in a bow tie counts over a boxer lying on the canvas.',
     'A boxing crowd on its feet round a ring, seen through the smoke beyond the ropes.',
     'A tired boxer in plain black trunks sits slumped on a stool in the corner of the ring, and beside him stands one '
     'older man, his trainer, in a white short sleeved shirt and grey slacks, pressing a white towel to the boxer\'s '
     'forehead, seen from high in the dark seats.')
show('old-game', ARCHIVE,
     'A baseball batter swings in a 1950s stadium.',
     'Men in hats cheer in the stands of a 1950s ballpark, seen from a distance.',
     'An old baseball stadium seen from the upper deck.',
     'Two baseball players in baggy plain flannel uniforms shake hands on the field.')
show('soccer', SPORT,
     'A soccer player in a plain red shirt dribbles past a defender in a plain white shirt on a small town field.',
     'A goalkeeper in a green jersey dives across the goal mouth with his arms out toward a ball flying past him.',
     'Two soccer players in plain red shirts with no numbers run with their arms raised after a goal.',
     'A small crowd in coats watches a soccer match from a low stand.')
show('horse-race', SPORT,
     'Three race horses with jockeys in bright silks gallop round a turn, spaced out a length apart.',
     'A row of race horses lunge out of a long green metal starting gate as its front doors spring open on a flat dirt '
     'track, seen side on from across the track so the horses and jockeys are small.',
     'A jockey stands in the stirrups as his horse crosses the line ahead of two others.',
     'A race course grandstand full of people in hats, seen from far across the track.')

# Films, by the kind of film.
show('film-comedy', FILM,
     'A small boy alone in a big house at Christmas stands at the top of the stairs beside a paint can hanging on a string in'
     ' a family comedy.',
     'Two clumsy burglars in woolly hats sprawl on their backs at the foot of a staircase in a family comedy.',
     'A panicking mother and father stand at an airport check in desk with their suitcases in a family comedy.',
     'Two teenage boys in denim with electric guitars stand in a garage in a teen comedy.',
     'A bride and groom run down the steps of a church, a few guests throwing rice behind them, in a comedy.',
     'A man in a crumpled suit sits on a curb in the rain beside a cardboard box of his things in a comedy.')
show('film-action', FILM,
     'A spy in a tuxedo stands beside a silver sports car on a mountain road in a spy thriller.',
     'A man in a leather jacket dives forward onto the ground toward the viewer, away from a warehouse that bursts into'
     ' a ball of fire far behind him, in an action movie.',
     'A helicopter chases a speeding car along a canyon road in an action movie.',
     'A man in a leather jacket lies flat on the roof of a moving train gripping its edge, the countryside rushing past, in an'
     ' action movie.',
     'A fighter pilot in a helmet and mask sits in a jet cockpit above the clouds in an action movie.',
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
     'An old motel under a bare tree at night with one lit window and a plain unlettered sign in a horror movie.')
show('film-crime', FILM,
     'Gangsters in pinstripe suits sit round a table in an Italian restaurant in a crime drama.',
     'A policeman in a leather jacket stands in a city alley at night, a big revolver in his right hand pointed at the ground,'
     ' in a police thriller.',
     'A few loose diamonds glint in an open briefcase on a wooden crate in a dim warehouse in a crime movie.',
     'A 1970s car chase over a steep city hill in a police thriller.',
     'A private detective in a hat sits in an office striped with light through blinds in a film noir.',
     'Young men in bandanas stand on a street corner beside a parked car at dusk, the store fronts behind them plain and'
     ' unlettered, in a crime drama.')
show('film-romance', FILM,
     'A man in a white dinner jacket and a woman in a hat sit at a table in a Moroccan cafe under ceiling fans in a romantic'
     ' classic.',
     'A governess and a gentleman in Victorian dress stand in the library of a country house in a period drama.',
     'A couple in 1950s clothes, a man in a grey suit and a woman in a long full skirted dress, dance side by side '
     'under black umbrellas on a rain soaked cobbled square at night, lamplight shining on the wet stones, dark stone '
     'buildings with shuttered windows around the square, in a classic musical.',
     'A couple kiss in the rain under a street lamp in a romantic movie.',
     'A woman in a long silk robe sits at a window writing a letter in a romantic drama.',
     'A man and a woman laugh over coffee in a diner on a snowy day in a romantic comedy.')
show('film-western', FILM,
     'A gunslinger with a big moustache stands in the dusty main street of a wooden town in a western.',
     'Three riders on horseback cross a desert canyon in single file in a western.',
     'Cowboys play cards at a saloon table in a western.',
     'A sheriff with a star on his waistcoat watches the sun set from a porch in a western.',
     'A stagecoach pulled by a team of four galloping horses races across a dusty plain in a western, seen side on from'
     ' a distance, a cloud of dust trailing behind it.')
show('film-war', FILM,
     'The cramped control room of a Second World War submarine in red light, two sailors at brass dials and pipes and an'
     ' officer at the periscope, in a war movie.',
     'A submarine surfaces in a grey sea in a war movie.',
     'Soldiers wade through a jungle river with their rifles held over their heads in a war movie.',
     'Inside the cramped grey steel control room of a submarine lit dim red, a naval officer wearing headphones looks '
     'up at the pipes and rivets of the low ceiling as the hull creaks, in a war movie.',
     'Tall plumes of water from depth charges rise behind a warship in a war movie.')
show('film-future', FILM,
     'A soldier with a riveted steel plate over half his face and a round blue camera lens for an eye stands in a rainy alley'
     ' lit by unlettered neon in a science fiction action movie.',
     'A motorcycle is chased by a huge truck down a desert highway in a science fiction action movie.',
     'A figure walks through sparks and molten steel in a dark factory in a science fiction action movie.',
     'A woman with a shotgun stands in the doorway of a wrecked bar in a science fiction action movie.',
     'A metal robot hand reaches up out of rubble in a science fiction action movie.')
show('film-epic', FILM,
     'A herd of woolly mammoths crosses a snowy valley in an adventure movie.',
     'A prehistoric sabre toothed tiger, a heavy stocky cat with thick forelegs, a short bobbed tail and two very long '
     'curved fangs reaching down below its chin, snarls on a rock in an adventure movie.',
     'Two people in fur coats stand looking up at a woolly mammoth in an adventure movie.',
     'Three Stone Age people in animal furs sit round a fire in a cave with painted walls, their hands resting on their knees,'
     ' in an adventure movie.',
     'A wide frozen plain under a low sun with a line of tiny figures walking in an adventure movie.')
show('film-art', ' A still frame from a 1970s European art film shot on 35mm film, muted colour, fog and soft film grain,'
                 ' recorded off air onto VHS.',
     'A man in a long coat walks across a flooded field in fog.',
     'A candle burns on the floor of an empty ruined room, water dripping from the ceiling.',
     'A woman\'s face in profile beside a rain streaked window.',
     'A white horse stands in a misty meadow.',
     'A long empty corridor with peeling walls and a door open at the end.')
show('monster', ' A still frame from a low budget 1980s monster movie shot on 35mm film, fog and blue night lighting, soft with'
                ' film grain, recorded off air onto VHS.',
     'The silhouette of a shaggy dog headed monster howls on a foggy hill under a full moon.',
     'A hairy grey clawed monster hand with long curved black nails reaches round the edge of a wooden door.',
     'Two screaming teenagers stand apart with their backs to the corner of a log cabin.',
     'Two glowing eyes among the trees of a dark forest.',
     'Villagers with burning torches gather in the street of an old German village at night.')
show('critics', VIDEO,
     'A balding man in a sweater vest and a woman in a blazer sit side by side in red velvet seats in an old cinema balcony,'
     ' arguing on a film review show.',
     'A balding film critic in a sweater vest shakes his head, seen from the chest up.',
     'A woman film critic in a blazer laughs, seen from the chest up.',
     'The beam of a projector shines over rows of empty red cinema seats in a dark old cinema.')
show('bad-movie', LOCAL,
     'A goofy man in a bow tie sits in the front row of an empty old cinema with a tub of popcorn.',
     'A black and white 1950s science fiction film plays, a man in a rubber monster suit trampling a cardboard model city.',
     'A black and white 1950s science fiction film plays, a toy flying saucer hanging on a string over a desert.',
     'A goofy man in a bow tie laughs and points at a cinema screen.')

# A network's evening.
show('high-school', SITCOM,
     'Teenagers in colourful early 1990s clothes talk at a row of school lockers.',
     'A teacher stands at a plain blackboard in front of a class of bored teenagers at their desks.',
     'Four teenagers sit in a diner booth with milkshakes.',
     'A teenage girl sits at the steering wheel of a car and a terrified middle aged driving instructor sits in the passenger'
     ' seat beside her, seen through the windshield.',
     'Teenagers dance under streamers in a school gym.')
show('irish', SITCOM,
     'A priest in a black suit and white collar talks to a farmer in a flat cap outside a stone pub with a plain unlettered'
     ' sign in a green Irish village.',
     'A housekeeper pours tea for a priest in an old fashioned kitchen.',
     'Three men in sweaters sit laughing at a small table in an Irish pub, pints of stout standing on the table.',
     'Sheep block a narrow lane in front of a small old car.',
     'A priest rides a bicycle along a lane between stone walls.')
show('network-news', VIDEO,
     'A distinguished grey haired news anchor in a dark suit sits at a large desk, a deep blue backdrop with a globe behind'
     ' him.',
     'A network news studio seen wide with cameras and a lit desk.',
     'A correspondent in an overcoat with a microphone stands in front of a domed capitol building.',
     'Farmland and a roadblock of army trucks seen from a helicopter.',
     'A distinguished grey haired news anchor looks grave, seen from the chest up.')
show('cop-show', DRAMA,
     'A policeman in mirrored sunglasses and a leather jacket walks away from an explosion in a 1980s action series.',
     'A plain clothes police captain, a heavy set man in his fifties in a rumpled white shirt and loosened brown tie, '
     'shouts and points across a wooden desk piled with paper files, a typewriter and an old black telephone, in a '
     'dingy precinct office with venetian blinds, in a 1980s action series.',
     'A muscle car flies in mid air over the crest of a hill in a 1980s action series.',
     'Two men crouch behind wooden crates on a dock at night in a gun fight in a 1980s action series.',
     'Two policemen eat burgers sitting on the hood of a car in a 1980s action series.')
show('space', DRAMA,
     'The bridge of a starship, a crew in coloured uniforms at consoles and a captain in the middle chair facing a screen full'
     ' of stars.',
     'An alien ambassador with a ridged forehead in long robes in a 1980s science fiction series.',
     'A model starship flies past a ringed planet in a 1980s science fiction series.',
     'Three crew members in coloured uniforms stand well apart on a rocky planet under a purple sky, each with a small ray gun'
     ' in a holster at the hip, in a 1980s science fiction series.',
     'An engine room with a glowing column and an engineer at the controls in a 1980s science fiction series.')
show('action-team', DRAMA,
     'Four tough men stand in front of a black van in a desert scrapyard in a 1980s action series.',
     'A man in a welding mask welds armour plate onto a truck in a barn in a shower of sparks in a 1980s action series.',
     'A jeep flips over in an explosion on a dirt road in a 1980s action series.',
     'A grinning grey haired man with a cigar in a 1980s action series.',
     'A helicopter flies low over a desert road after a van in a 1980s action series.')
show('strange', DRAMA,
     'A man in a trench coat stands facing the camera in a foggy graveyard at night and talks to it, in a documentary series'
     ' about the unexplained.',
     'A blurred dark hairy upright figure is half hidden between distant trees, out of focus and hard to make out, in a'
     ' documentary series about the unexplained.',
     'Three points of white light float high in the open black night sky in a triangle far above a dark farmhouse and '
     'its field, small and blurred like distant stars only brighter, in a documentary series about the unexplained.',
     'An old farmer is interviewed on his porch at night in a documentary series about the unexplained.',
     'An old house at night with one lit window in a documentary series about the unexplained.')
show('chat', VIDEO,
     'A late night talk show host sits behind a desk with a city skyline backdrop, a film actor on the sofa beside it.',
     'A talk show host in a suit stands in front of a curtain telling a joke into a microphone, smiling and looking out at'
     ' the audience.',
     'A talk show band in matching jackets plays horns.',
     'A handsome film actor laughs on a talk show sofa.',
     'A talk show audience laughs in rows beyond the stage lights.')
show('family', SITCOM,
     'A suburban family sits on a sofa in a living room, the father\'s hair scorched and sticking up.',
     'A mother stands in a kitchen full of white smoke with a red fire extinguisher, white foam covering a burnt pan on the'
     ' stove.',
     'A family laughs round a dinner table.',
     'Two children whisper on a staircase.',
     'A neighbour in a loud Hawaiian shirt covered in big orange and green flowers leans his head and shoulders in '
     'through a wide open front door, grinning, seen from inside the living room.')
show('britcom', SITCOM,
     'A woman in a floral dress and pearls pours tea in a chintzy English sitting room in a British sitcom.',
     'Two neighbours gossip over a garden fence in a British sitcom.',
     'A vicar in a black shirt and white dog collar sits squeezed on a sofa between two old ladies, a teacup on a saucer'
     ' balanced on his knee, in a British sitcom.',
     'A man in a cardigan hides behind a newspaper while his wife talks in a British sitcom.')

# What comes on a tape and is not on the air.
show('fitness', VIDEO,
     'An aerobics class of men and women in loose t-shirts, track pants and leg warmers follows an instructor at the front,'
     ' seen from across the room in a 1980s exercise show.',
     'A row of men and women in baggy t-shirts and sweatpants do squats, seen from the front across a gym in a 1980s exercise'
     ' show.',
     'A fit man in a headband and a grey t-shirt lifts a pair of dumbbells at his sides in a 1980s exercise show.',
     'Four grown women in their thirties in plain solid coloured t-shirts and long loose track pants step up and down '
     'on low plastic steps in a row across a gym, each on her own step, seen from across the room in a 1980s exercise '
     'show.')
show('garage', TAUGHT,
     'A mechanic in overalls points under the raised hood of a car in a clean garage.',
     'A mechanic kneels beside a car changing a wheel, a jack under the car.',
     'A car engine seen close up under a raised hood, a wrench resting on the engine.',
     'A mechanic leans over the open hood of a car pulling out the oil dipstick.')
show('first-aid', TAUGHT,
     'A paramedic in uniform bandages the forearm of a fully dressed volunteer who sits upright on a table.',
     'A paramedic kneels on the floor pressing on the chest of a training dummy.',
     'An open first aid kit with rolled bandages and scissors lies on a table, its box plain and unlettered.',
     'A woman doctor in a white coat talks to the camera in a doctor\'s office.',
     'A bandage is wrapped round a lower leg resting on a stool.')
show('guns', TAUGHT,
     'A man in a hunting vest stands at the bench of an outdoor shooting range, a single rifle lying on the bench in front of'
     ' him.',
     'A revolver lies on a cloth on a table with its cylinder swung open, six cartridges standing upright beside it.',
     'Round paper targets on wooden stands at the far end of an outdoor shooting range, a grass bank behind them.',
     'A police instructor in uniform stands in front of a classroom of adult police officers in uniform, the blackboard'
     ' behind him plain.')
show('garden', TAUGHT,
     'A man in a straw hat and dungarees kneels among tomato plants in a vegetable garden.',
     'A seedling stands newly planted in dark soil, a trowel lying beside it.',
     'Pots of herbs on a sunny window sill.',
     'A basket of fresh vegetables on a garden path.',
     'A man sprays the leaves of a row of bean plants.')
show('fishing', FIELD,
     'A man in a fishing vest and a plain cap with no logo holds up a largemouth bass in a small boat on a lake.',
     'A man casts a fishing rod from a lake shore at dawn.',
     'An open tackle box full of lures on a wooden dock.',
     'A man in a small boat lifts a landing net out of the water with a big bass inside it.')
show('farm', FIELD,
     'A girl feeds goats through the fence of a petting zoo.',
     'A rooster stands on a fence post at sunrise.',
     'Cows graze in a green pasture.',
     'A farmer drives an old red tractor across a field.',
     'Two muddy piglets lie in the straw of a dim barn.')
show('trade', TAUGHT,
     'An electrician in a tool belt works at an open fuse box.',
     'A yellow electrical meter with a plain unmarked face lies on a workbench, its two probe wires running to a bundle of'
     ' loose wires.',
     'A man solders inside the open back of a television set.',
     'A welder in a mask works in a shower of sparks.',
     'A man in a hard hat stretches wire along a line of fence posts.')
show('sewing', TAUGHT,
     'An old woman smiles at a full size white electric sewing machine on a wooden table in a cosy room.',
     'A piece of denim on a table with a needle and thread pushed through it, seen close up.',
     'A dressmaker\'s dummy dressed in a half finished cotton blouse stands in a sewing room, a tape measure hanging round its'
     ' neck.',
     'Long heavy steel dressmaking shears with black handles, as long as a forearm, lie on a cutting table beside a '
     'length of blue cloth already cut in two along a straight white chalk line, the two pieces pulled a little apart.')
show('pottery', TAUGHT,
     'A wet clay pot rises on a turning potter\'s wheel, the potter\'s hands resting round it.',
     'A potter in an apron sits at a wheel in a studio lined with shelves of pots.',
     'A row of glazed pots on a wooden shelf.')
show('lesson', TAUGHT,
     'A woman in a business suit talks to the camera in a beige office with a pot plant in a staff training video.',
     'A professor in a jacket stands at a blackboard beside a chalk diagram of circles and arrows with no writing.',
     'A dentist in a plain white coat sits at a desk with a big plastic model of teeth on it in front of him, a plain '
     'beige wall and a potted plant behind him, the picture dull and slightly smeared.',
     'A flight instructor points at the dials of a light aircraft cockpit.',
     'Two people in suits shake hands across an office desk, nobody else in the room, in a staff training video.',
     'A clergyman in a grey suit talks gently to the camera in front of a stained glass window.')
show('nature', NATURE,
     'A red brown doe of the Kentucky woods, her short summer coat a warm rusty cinnamon colour all over, with large '
     'ears and slender legs, stands in a misty forest clearing at dawn.',
     'Penguins crowd on an ice shelf.',
     'A red fox pounces in the snow.',
     'An American alligator with a broad rounded snout lies half submerged in a green swamp among lily pads.',
     'A wide river winds between wooded hills, seen from the air.',
     'A brown bear stands in the white water at the top of a waterfall, a salmon leaping in the air in front of it.',
     'A spiral galaxy and stars in deep space in an astronomy documentary.')
show('travel', ' A still frame from a 1980s travel documentary shot on 16mm film on a sunny day, soft with film grain and warm'
               ' muted colours, recorded off air onto VHS.',
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
     'A bride in a white dress laughs on a lawn with two bridesmaids standing a little apart beside her.')
show('baby', HOME,
     'A toddler in a striped t-shirt and denim dungarees takes wobbly steps across a brown shag carpet toward his '
     'mother, who kneels with her arms out in a long sleeved blouse and jeans, in a wood panelled 1980s living room '
     'with a tube television in the corner, the colours faded and the picture smeared like an old worn tape.',
     'A baby in a high chair with food on its face.',
     'A father sits on a garden lawn with a smiling baby in a romper sitting on his lap.',
     'A baby crawls across a carpet toward the camera.')
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
