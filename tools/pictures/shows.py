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
     'A serious middle aged male news anchor in a dark suit and striped tie, with an ordinary lined face, seen from the chest'
     ' up against a plain blue studio backdrop.',
     'Two news anchors, a man and a woman, sit a little apart at a long news desk seen wide, a newsroom of desks and'
     ' glowing monitors behind them.',
     'A woman news anchor with short brown hair and an ordinary face sits at a desk looking off to one side, a boxy'
     ' tube monitor in a grey plastic case on a shelf over her shoulder showing a plain green map of a river valley.',
     'A news studio seen from behind two big studio cameras on pedestals, one anchor small at a lit desk under rows'
     ' of lights.')
show('news-field', FIELD,
     'A bareheaded woman news reporter in a beige trench coat holds a microphone in front of an army roadblock of green'
     ' trucks and coiled barbed wire at dusk.',
     'Soldiers in gas masks and green fatigues stand at a checkpoint on a rural highway, concrete barriers across the road under'
     ' an overcast sky.',
     'A long traffic jam of 1980s cars on a two lane highway through green farm fields, seen from a helicopter high above.',
     'A handful of worried civilians in summer t-shirts and jeans stand behind a tall chain link fence, seen past the backs of'
     ' two soldiers in green fatigues on the near side.',
     'A woman reporter stands on a roadside at night lit from the front by one bright white lamp, the red and blue'
     ' lights of police cars blurred far behind her.')
show('pundit', VIDEO,
     'A stern grey haired man in a dark suit sits behind a wooden desk pointing one finger straight out of the frame'
     ' towards the viewer, an American flag and a red curtain behind him.',
     'An angry grey haired pundit in a dark suit leans towards the viewer mid shout, seen from the chest up'
     ' in front of a red curtain.',
     'An opinion show studio seen wide, one grey haired host sitting in a leather chair at a wooden desk in the middle'
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
     'A scientist in a white lab coat stands at a laboratory bench with glass flasks and a microscope on it, looking'
     ' toward someone just out of the frame.',
     'A grey laboratory microscope stands on a white bench with a glass slide clipped under its lens and a rack of test tubes'
     ' beside it, seen close up.',
     'A woman doctor in a white coat with a stethoscope round her neck talks to someone just out of the frame in a'
     ' pale green hospital corridor.')
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
     'Five shoppers with empty shopping carts wait in a line outside a small town supermarket, and through its big'
     ' front windows the white metal shelves inside stand bare and empty.')
show('emergency', VIDEO,
     'An empty news desk with the chair pushed back and loose papers left on it, the studio lights still on.',
     'A tired middle aged official with thinning grey hair, a round face and glasses, in shirt sleeves and a loosened'
     ' tie, sits at a plain desk with his forearms resting on it, a single sheet of paper lying flat on the desk, an'
     ' American flag on a pole beside him.',
     'A plain blue studio backdrop with a simple flat gold eagle emblem printed in the middle and no words, lit unevenly by'
     ' studio lights.',
     'An empty studio with one studio camera left pointing at an empty chair in a pool of light.')
show('local-desk', VIDEO,
     'A male local news anchor in a brown suit sits at a wood panelled news desk, a photograph of a small city skyline behind'
     ' him.',
     'A woman local news anchor with big permed hair and a green blazer sits at a wood panelled news desk in front of'
     ' a plain blue backdrop, the studio light flat and a little harsh, the picture soft with smeared colour.',
     'Two local news anchors, a man and a woman, sit a little apart at a wood panelled desk and turn to each other smiling,'
     ' plain blue panels behind them.',
     'A local weatherman in a tan suit stands beside a weather map of Kentucky with simple sun and cloud symbols and no words,'
     ' one arm raised toward it.')
show('talk', VIDEO,
     'A woman talk show host and a male guest sit in two armchairs facing one another a little apart, a potted plant and a'
     ' window onto a city at night behind them.',
     'A woman talk show host with short hair listens with her chin resting on her hand, seen from the shoulders up against a'
     ' dark blue studio.',
     'A bearded man in a corduroy jacket sits in an armchair talking with one open hand raised, his eyes on the host'
     ' just out of the frame, against a dark blue studio.',
     'A late night talk show set seen from the back of the studio, two people small in armchairs beside a low table under the'
     ' lights.')
show('local-field', FIELD,
     'A male reporter in a windbreaker stands with a hand microphone at a line of plain yellow police tape with no printing'
     ' across a rural road, plain white patrol cars with no markings parked side on behind him.',
     'A white news van is parked on a grass verge beside a highway, a tall thin straight pole rising from '
     'its roof straight up into the sky like a flagpole with a small round dish at the very top, its side plain and '
     'unlettered.',
     'A dark country road at night seen through a car windshield, the headlights picking out abandoned cars left at angles'
     ' along the verge.',
     'Small figures run across a field at dusk, seen from far off, the picture shaky and blurred.',
     'A frightened male reporter in a suit, his hands empty, crouches alone behind a parked car at night, looking back '
     'over his shoulder, lit by a harsh white light.')
show('statement', VIDEO,
     'A man in a dark suit speaks at a podium in front of a blue curtain with two flags on poles beside it.',
     'Rows of seated reporters in suits seen from the podium across a wide room, the reporters small, a few with one'
     ' hand raised to ask a question, a plain bare wall behind them.',
     'A white government building with columns lit up at night.',
     'A grave man in a dark suit and red tie speaks into a cluster of microphones, seen from the chest up.')

# Home and hobbies.
show('cook', VIDEO,
     'A cheerful woman in an apron stands at the stove of a studio kitchen, stirring a pot with a wooden spoon.',
     'Seen straight down from above, chopped carrots and onions lie on a wooden chopping board beside a small kitchen'
     ' knife with a plain bare steel blade and a wooden handle, the board filling the picture.',
     'A plump golden brown roast chicken with its legs tied and both its wings tucked neatly under it sits on a white'
     ' platter surrounded by roast potatoes and browned roast carrots, seen from the side on the counter of a studio'
     ' kitchen.',
     'A cheerful woman in an apron stands behind a kitchen counter smiling straight out of the frame towards the'
     ' viewer, a mixing bowl and a whisk on the counter in front of her.',
     'A pan of vegetables steams on a gas stove with a wooden spoon resting in it, seen close up.',
     'A tall round layer cake covered all over in thick smooth white buttercream icing with piped rosettes round its top'
     ' edge sits on a cake stand on the counter of a studio kitchen.')
show('woodwork', VIDEO,
     'A bearded man in a plaid shirt and safety glasses saws a pine plank laid across two sawhorses, the steel blade'
     ' of an old wooden handled hand saw cutting down through the end of the plank, seen from a few paces back.',
     'A claw hammer and a few nails lie on pine boards on a workbench, one nail half driven into the wood, seen close up.',
     'In a home workshop with a wooden workbench, hand tools hanging on a pegboard wall and sawdust on the floor, a '
     'bearded man in a plaid shirt stands proudly beside a finished wooden bookcase.',
     'A pegboard wall, a brown board full of small round holes, over a workbench, with a claw hammer, a screwdriver'
     ' and a wooden handled hand saw hung well apart on metal hooks.',
     'A wooden hand plane sits on a pine board on a workbench, a long curl of pale wood shaving lying beside it.',
     'A bearded man in a plaid shirt with a pencil tucked behind one ear bends low over a workbench to look along the'
     ' edge of a long pine board lying on it, his eye close to its end, checking it is straight.')
show('survival', FIELD,
     'A rugged man in a khaki shirt and a bandana crouches by a small campfire in a forest, looking straight out of'
     ' the frame towards the viewer as he speaks.',
     'A rugged man in a khaki shirt kneels beside a river with a trout laid on a flat rock in front of him.',
     'A lean to shelter of branches and leaves stands in a forest clearing.',
     'A simple snare of cord tied to a bent sapling at the edge of a forest path, seen close up.',
     'A rugged man in a khaki shirt walks through tall ferns and looks back over his shoulder towards the viewer.',
     'A pot of water boils over a campfire at dusk, a small tent behind it.')
show('advert', VIDEO,
     'A home shopping show: a middle aged man in a red knitted sweater and a woman with permed hair in a blue sweater'
     ' stand an arm\'s length apart behind a table of kitchen gadgets with mild polite expressions, a plain painted'
     ' kitchen backdrop behind them, the flat studio light a little harsh and the colours smeared.',
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
     'A middle aged man in a cardigan sits at a desk with a telephone and a mug, a wood panelled wall behind him.',
     'A middle aged man in a cardigan sits at a desk holding a telephone receiver to his ear and nodding.',
     'A host\'s desk in a small wood panelled room seen from a few paces back, a beige desk telephone on it with one'
     ' small red light lit, a coffee mug beside it and an empty chair behind.',
     'A middle aged man in a cardigan laughs with his head thrown back at a desk with an old beige telephone on'
     ' it, a plain painted wall behind him.')

# Entertainment.
show('hollywood', VIDEO,
     'A woman entertainment news presenter in a pink blazer with ordinary hair and make up stands in front of a backdrop of'
     ' spotlights and painted palm trees.',
     'Film stars in evening dress walk a red carpet at a night premiere, a dark blur of photographers and bright white'
     ' flash bursts behind a rope.',
     'An empty film set on a sound stage: a painted backdrop of a city street, tall lamps on stands, black cables'
     ' across the floor and a plain canvas folding chair standing empty in front.',
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
     'A woman with big teased blonde 1980s hair in a loose, long sleeved, high necked evening gown points angrily at a'
     ' man in a tuxedo who stands a step away with his arms spread, in an opulent living room in a daytime soap'
     ' opera.',
     'A tearful woman with heavy eye make up and big hair, seen from the shoulders up in a daytime soap opera.',
     'A man lies in a hospital bed with a bandaged head, a woman in a fur coat sitting at the bedside, in a daytime soap'
     ' opera.',
     'A couple in evening dress stand on a terrace over a city at sunset in a daytime soap opera.',
     'A man in a tuxedo stands at the foot of a mansion staircase with his head turned aside and a red mark on his'
     ' cheek, as a woman in a red dress storms up the stairs away from him, seen from a little way back, in a daytime'
     ' soap opera.')
show('quiz', VIDEO,
     'A middle aged American game show host with a moustache and a grey side parting, in a sparkly blue jacket, holds a'
     ' long thin microphone on a dated 1980s set of orange and brown panels ringed with rows of round light bulbs.',
     'Three game show contestants stand a little apart behind podiums with buzzers, the podium fronts plain and unlettered.',
     'A game show contestant jumps for joy under falling confetti.',
     'A shiny new red convertible seen side on from a little way back, standing on a turning stage under coloured'
     ' lights as a game show prize.',
     'Looking out past the bright stage lights into a dark studio, the rows of a game show audience a dim soft blur'
     ' beyond the glare.')

# For children.
# The heroes are the mod's own: helmets with visors, green and orange, no capes, no masks with ears, no chest emblems.
OWN_HEROES = (' They are original characters in green and orange jumpsuits that are plain from collar to boots, with'
              ' round clear bubble helmets showing their faces and small backpacks.')
show('heroes', CEL,
     'A team of five space rangers stand in a row a little apart on a rooftop at night.' + OWN_HEROES,
     'A single space ranger flies alone with a jet backpack over a city skyline.' + OWN_HEROES,
     'Three space rangers seen from the side stand far apart in a row across a city street, facing a giant grey'
     ' robot.' + OWN_HEROES,
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
     'Hand puppets made of felt, a fox, an owl and a rabbit, poke up from behind a painted wooden fence with their'
     ' mouths open in song, painted forest trees behind, on a cheap 1980s children\'s show set.')
show('bunnies', ' A still image from a 1980s cartoon for very small children, painted by hand on celluloid over a'
                ' watercolour background, soft faded pastel colours, wobbly hand inked outlines and uneven paint. The'
                ' picture fills the whole image edge to edge.',
     'Three round pastel cartoon bunnies of different sizes, a big pink one, a middle sized pale yellow one and a'
     ' small pale blue one, sit side by side in a meadow of flowers under a rainbow.',
     'Two round pastel cartoon bunnies build a tower of wooden toy blocks on a green lawn under a pale blue painted sky'
     ' with soft white clouds, the painted lawn and sky filling the frame right out to all four edges and corners.',
     'Three round pastel cartoon bunnies, one pink, one pale yellow and one pale blue, sit apart round a checked'
     ' picnic blanket on soft green grass.',
     'A round pastel cartoon bunny stands on a green hill with a red balloon on a string, a pale blue painted sky'
     ' filling everything behind it, the grass and the sky painted right out to all four edges and corners of the'
     ' picture.',
     'Three round pastel cartoon bunnies sleep in a row, each in its own little bed, under a smiling moon.')
show('kids-advert', VIDEO,
     'In a cosy family den with wood panelled walls, a brother and sister, the only two people in the room, sit at a '
     'small desk in front of a beige home computer whose monitor is a deep boxy tube screen with a bulging glass face, '
     'in a commercial.',
     'A loud salesman in a jacket of big bold red and yellow checks spreads his arms wide in a store aisle between'
     ' tall shelves of plain cardboard boxes, seen from a little way back, in a commercial.',
     'A plastic toy robot stands on a kitchen table beside a few loose batteries in a toy commercial.',
     'A mother, a father and a child in plain sweaters sit round a game of checkers on a living room carpet in a'
     ' commercial.')

# Public access.
show('tech', LOCAL,
     'A young man with glasses and a headset sits at a desk crowded with beige computers and circuit boards, a curtain behind'
     ' him.',
     'A beige early 1990s desktop computer case standing on a desk with its metal cover slid off and lying beside it,'
     ' circuit boards and grey ribbon cables showing inside, seen close up.',
     'A boxy computer monitor glows with green lines and no readable words on a cluttered desk.',
     'A young man with glasses grins and holds up a green circuit board.')
show('interview', LOCAL,
     'A woman interviewer in a navy trouser suit and an old man, a silver haired male senator with a lined face in a'
     ' grey suit and tie, sit in two armchairs facing one another a little apart, a fern behind them.',
     'A silver haired senator in a suit answers a question, seen from the chest up.',
     'A woman interviewer in a blazer sits in a cheap armchair in a small bare room with a wrinkled curtain behind her,'
     ' looking down at the notes in her lap, the light flat and harsh.')
show('clown', LOCAL,
     'A clown with an orange wig and a red nose waves in front of a paper backdrop with balloons in a children\'s show.',
     'One clown with an orange wig and a red nose stands alone on the bare floor of a small room, juggling three solid'
     ' rubber balls, one red, one yellow and one blue, all three up in the air above his hands, the wall behind him'
     ' painted with yellow stars on a deep blue sky.',
     'A clown with an orange wig sits on a stool facing a puppet made from a plain grey wool sock with two black button'
     ' eyes, which pokes up over the edge of a small puppet booth of plain red painted wood beside him in a'
     ' children\'s show.',
     'Small children sit cross legged on a carpet and laugh in a children\'s show, each child different: a girl of'
     ' about five with red pigtails, a chubby boy of about four with a crew cut, a thin girl of about six with dark'
     ' braids and a boy of about five with glasses, a plain painted yellow wall behind them.')
show('county', FIELD,
     'A woman presenter in a denim jacket stands by a white fence on a horse farm, rolling green hills behind her.',
     'A county fair on a summer evening, one ferris wheel lit up against the sky behind a row of striped stalls.',
     'An old man in overalls sits on the porch of a wooden house holding a long stick across his lap in one hand and a'
     ' small pocket knife in the other, shaving a curl of pale wood off the stick, seen from the yard.',
     'A covered wooden bridge with a pitched tin roof and red painted board sides crosses a creek among green summer'
     ' trees, seen side on from down the creek, its red sides running straight and level from bank to bank and ending'
     ' at the floor of the bridge, a stone abutment at each end.',
     'A tall old black painted wooden tobacco barn with vertical plank siding and open vent boards standing alone at the edge'
     ' of a cut field at sunset, seen from the road.',
     'Two horses, a chestnut and a grey, graze well apart in a green pasture, short green grass in the foreground and a'
     ' white wooden fence running along the field behind them.')
show('survivors', ' A still from old footage lit by one harsh light, dim and grainy with muted colours. The'
                  ' picture fills the whole image edge to edge.',
     'An exhausted man in a dirty flannel shirt sits at a desk in a dim studio, a door barricaded with furniture'
     ' behind him.',
     'A tired woman with a bandaged arm sits in a dim studio, looking straight out of the frame towards the'
     ' viewer as she speaks.',
     'A bareheaded young man crouches by a small fire burning in an old metal barrel in a dim empty garage.',
     'Stacks of food cans and plastic water jugs stand on the floor of a dim studio, their labels plain and'
     ' unlettered.',
     'An empty news desk in a dim studio, and on the floor beside it a black office chair lies tipped over'
     ' on its side with its wheels in the air.')

# Music videos.
show('ballad', CLIP,
     'A singer in a white suit sits at a grand piano among candles and fog.',
     'A woman in a long coat sings in the rain on an empty stone bridge at night, coloured light reflected in the wet'
     ' pavement and the dark river behind her.',
     'A couple stand in silhouette on a beach at sunset, lit only by the low orange sun over the sea, a soft pink and'
     ' amber haze drifting across the sand.',
     'A woman singer with long wavy dark hair falling down over both her ears, in a high necked long sleeved black'
     ' blouse, sings into a handheld microphone in soft blue light, seen from the shoulders up, one soft old round'
     ' spotlight glowing behind her head.',
     'A single red rose lies on the closed wooden top of an old upright piano beside a burning candle, seen close from'
     ' above, the plain dark polished wood of the top filling the picture, in a dim room hazy with smoke and blue'
     ' light.')
show('pop', CLIP,
     'Three young women in bright colour blocked baggy jackets and loose trousers dance a little apart on a white'
     ' studio set, caught mid jump with their arms up and knees bent.',
     'Four young men in denim jackets pose against a painted brick wall.',
     'A singer in a loose jacket with a thin headset microphone curving to his mouth dances with both arms swinging,'
     ' four dancers in baggy clothes jumping mid move behind him, under coloured lights, seen from across the stage.',
     'A young woman pop singer with big curly hair falling over her ears, in a loose white blouse buttoned to the '
     'collar, sings into a handheld microphone on a stage, seen from the waist up.',
     'Teenagers in plain solid coloured t-shirts and baggy jeans dance with their arms in the air, seen far off as'
     ' dark shapes against coloured lights.')
show('rock', CLIP,
     'A long haired rock band plays on a huge arena stage, a towering wall of stacked black amplifier cabinets behind'
     ' them, jets of flame shooting up at the sides, lit by hot white and amber spotlights hung from metal trusses,'
     ' seen from far back in the arena.',
     'Seen from far back in the dark, a long haired guitarist stands alone in a single white spotlight on a dark'
     ' stage, a small figure, his guitar a dark shape.',
     'A drummer with long hair in a plain black sleeveless shirt sits behind a huge drum kit with two bass drums with '
     'plain black heads and a wide ring of toms and cymbals around him, under red stage lights.',
     'A male rock singer in scarves and a leather jacket screams into a microphone stand.',
     'A concert crowd with raised fists seen from the stage, out of focus in the haze.')
show('soul', CLIP,
     'A singer in a silk shirt stands singing softly in a loft lit by candles.',
     'A woman in a long satin dress sings beside a saxophone player.',
     'A man plays an electric piano in a dim studio in warm light, seen from the front across the instrument, its'
     ' plain black back toward the viewer.',
     'A couple slow dance in a dim room in front of a window onto the city lights.')
show('rap', CLIP,
     'Three rappers stand bareheaded on a city basketball court beside a big portable stereo on a bench, seen from the'
     ' waist up: a tall thin one with a high top fade in a baggy red hooded sweatshirt, a stocky one with a shaved'
     ' head in an oversized denim jacket and one with short twists in a big plain yellow t-shirt.',
     'Two young men, one in a baseball jacket leaning on the front fender and one in a white t-shirt leaning on the '
     'door, with a long low 1960s convertible lowrider, deep red with chrome wire wheels, under orange street lights at'
     ' night, the street otherwise empty.',
     'A disc jockey in headphones stands at two turntables.',
     'Three young men pose on the stone steps of a city row house.')
show('club', CLIP,
     'Seen from far back in a dark night club, a mixed crowd of dancers, men and women of different heights and builds'
     ' in loose t-shirts that hang down to their hips and baggy shirts, small dark shapes under green laser beams and'
     ' smoke.',
     'A disc jockey in a baggy shirt faces the viewer from behind a table with two black vinyl record turntables on it,'
     ' his face lit as he looks down at the records with his hands on them, in a dark club booth, smoke behind him lit'
     ' by old round metal spotlights glowing red and blue, seen from a little way back.',
     'Silhouettes of dancers in loose t-shirts that hang down to their hips and baggy jeans, against smoke lit by old'
     ' round metal spotlights glowing red, yellow and blue.',
     'Glowing coloured shapes spin on a black background in early 1990s computer video art.')
show('eighties', CLIP,
     'A singer with big hair in a buttoned jacket with shoulder pads stands against a glowing neon grid.',
     'A man in a pastel suit stands alone at the front of a stage playing a keytar, a small white keyboard shaped like '
     'a guitar that hangs across his body from a strap over his shoulder.',
     'A low red 1980s sports car seen side on from far off drives along a highway at night, long lines of pink and'
     ' blue neon tubing glowing along the roof edges of the buildings beside the road.',
     'Three men in pastel suits stand a little apart on a beach, one with an electric guitar, one with a bass guitar and one'
     ' with a keyboard on a strap.',
     'A 1980s dance class of women with big permed hair and headbands, in long baggy sweatshirts and leg warmers over'
     ' plain tights, dances in a studio with a wall of mirrors and a ballet barre, seen from across the room.')

# Sport.
show('sports-desk', VIDEO,
     'Two sports presenters in blazers sit at a desk with a trophy standing on it between them, a plain blue backdrop '
     'behind them with a soft blurred photograph of a stadium at night.',
     'A sports presenter in a double breasted navy blazer with big lapels, a white shirt and a wide patterned tie,'
     ' with a moustache and a side parting, stands beside a stack of boxy tube television sets showing blurred green'
     ' sports fields.',
     'A grinning sports presenter with a moustache sits at a desk with papers in front of him.',
     'A sweating athlete in a plain unlettered jersey sits in front of lockers in a locker room while a reporter beside him'
     ' holds a microphone up to him.')
show('baseball', SPORT,
     'A baseball pitcher in a plain white uniform and a plain white cap throws from the mound, seen from the chest up as'
     ' his throwing arm comes over high, his other hand down out of the picture, green outfield grass blurred behind'
     ' him.',
     'Seen from high and far behind the pitcher\'s mound, a baseball batter in a plain grey uniform swings at home'
     ' plate, the catcher crouched behind him facing this way in dark padding and a mask, and the umpire in dark blue'
     ' behind the catcher, all three small figures on the brown dirt, a plain dark green wall behind them.',
     'The wide green outfield grass of a baseball park seen from high in the stands on a sunny afternoon, mown in broad'
     ' light and dark green stripes, the far stands a soft distant blur of colour.',
     'Seen from far off across the outfield, a baseball outfielder in plain grey leaps at the low padded outfield'
     ' fence, a small lone figure, the empty green grass in front.',
     'A baseball dugout seen from across the field, a row of small players in plain grey uniforms and dark caps leaning'
     ' on its rail in the deep shade of its roof, the green grass of the field in front.',
     'Seen from high up in the stands far away, one baseball runner, a small figure in a plain grey uniform, slides'
     ' into a base in a cloud of dust on the brown infield.')
show('golf', SPORT,
     'A golfer in a polo shirt drives from the tee, a line of spectators behind him.',
     'A golfer crouches to line up a putt on a green.',
     'A golf ball rolls toward the hole and the flag on a green.',
     'A golf fairway runs between tall pines with a lake beside it.',
     'Seen from far away across a long empty fairway lined with tall trees, two small figures walk side by side, a '
     'golfer and his caddie, the caddie carrying a big golf bag on his shoulder, only the two of them on the green '
     'grass.')
show('poker', VIDEO,
     'Four men sit well apart round a green poker table under a low lamp: a heavy bald man in a white shirt, a thin'
     ' young man with slicked black hair in a grey jacket, an old man with a white beard in a cowboy hat and a red'
     ' haired man with glasses in a brown sweater, stacks of chips in front of each, seen from a little way back.',
     'Two men face each other across a green felt card table under a hanging lamp in a dim card room, short stacks of '
     'red and white poker chips in front of each, seen from across the room.',
     'A poker player in sunglasses and a cowboy hat stares across the table.',
     'A casino dealer in a waistcoat stands behind a green felt table with cards laid out in front of her.',
     'A pile of red, white and blue poker chips sits in the middle of a green felt poker table under a low lamp.')
show('racing', SPORT,
     'Seen from high up in a distant grandstand, a line of small open wheel racing cars in plain solid colours sweeps '
     'round a steeply banked oval corner, the grey concrete track tilted high up toward the outer wall.',
     'Seen from high up in the stands far away, a pit crew in plain white overalls swarms round a racing car painted'
     ' plain solid blue, small figures in the pit lane.',
     'The view from the cockpit of a racing car down a straight, another car ahead.',
     'Seen from high above and far away, a starting grid of small racing cars in plain solid colours sits two by two'
     ' on a grey paved oval track, the stands along one side.',
     'Seen from far off, a racing driver in plain white overalls, a small figure, stands alone on top of a podium'
     ' against a plain blue sky with champagne spraying up in an arc above him.',
     'Stock cars in plain colours race side by side round a dirt oval.')
show('skating', SPORT,
     'A skateboarder in a helmet flies in mid air above a wooden half pipe, a few spectators watching.',
     'A teenage skateboarder in a baggy long sleeved plain t-shirt and long shorts slides along a metal rail beside a'
     ' concrete plaza, seen from far off, the picture soft, smeared and faded.',
     'Seen from far off against a pale sky, a teenager in a plain blue t-shirt and jeans on a small BMX bicycle flies'
     ' through the air over a dirt jump, a small dark figure in an old round open face helmet, the dirt track around'
     ' him empty.',
     'Teenagers in plain t-shirts cheer at the top of a wooden skate ramp, seen from below at the foot of the ramp,'
     ' from the waist up, against a blue sky.')
show('boxing', ' A still image from 1970s boxing coverage shot on 16mm colour film, bright ring lights and a'
               ' faint haze, soft and grainy with faded colours. The picture fills the whole image edge to edge.',
     'Two heavyweight boxers square off a step apart in a ring, seen from far back in the dark seats: one with a shaved'
     ' head and a thick black beard in plain white trunks, one with short red hair and a flattened nose in plain black'
     ' trunks, their gloves plain dark red leather all over.',
     'Seen from far back in the dark seats, a small bright ring: a dazed bare chested boxer in plain black trunks sags'
     ' back against the ropes with his arms hanging low, while the other boxer, bare chested in plain white trunks,'
     ' walks away to the far side, both small figures with plain dark red gloves.',
     'A referee in a plain white short sleeved shirt and a black bow tie counts over a bare chested boxer lying on his'
     ' back on the canvas, seen from just outside the ropes, the referee and the fallen boxer filling the picture'
     ' against the dark crowd, the boxer\'s gloves plain dark red all over.',
     'A boxing crowd on its feet in a dark hall, rows of men in shirt sleeves cheering, seen past the ropes of the ring,'
     ' smoke hanging in the bright light above their heads and the hall behind them lost in darkness.',
     'A tired boxer in plain black trunks sits slumped on a stool in the corner of the ring, and beside him stands one '
     'older man, his trainer, in a white short sleeved shirt and grey slacks, pressing a white towel to the boxer\'s '
     'forehead, seen from high in the dark seats.')
show('old-game', ARCHIVE,
     'Seen from far up in the stands, a baseball batter swings at home plate in a 1950s stadium, a small figure in a'
     ' plain light uniform.',
     'Men in hats cheer in the stands of a 1950s ballpark, seen from a distance.',
     'A 1950s baseball stadium seen from high up at the back of the top tier of the stands, looking down over rows of'
     ' seats to the small diamond far below.',
     'Two baseball players in baggy plain flannel uniforms walk side by side off the field toward the dugout, seen'
     ' from far off, the picture worn, flickering and scratched.')
show('soccer', SPORT,
     'Seen from far off across a small town field, a soccer player in plain red dribbles past a defender in plain'
     ' white, two small figures a few paces apart on the green grass, trees behind.',
     'Seen from far off at the side of the pitch, a goalkeeper in plain green, a small figure, dives across the goal'
     ' mouth as the ball flies past him into the net, nobody else in view.',
     'Two soccer players in plain red shirts with no numbers run with their arms raised after a goal.',
     'A small crowd in coats stands along a low wooden fence at the edge of a small town soccer field, trees behind,'
     ' seen from across the field.')
show('horse-race', SPORT,
     'Three race horses with jockeys in bright silks gallop round a turn, spaced out a length apart.',
     'A row of race horses lunge out of a long green metal starting gate as its front doors spring open on a flat dirt '
     'track, seen side on from across the track so the horses and jockeys are small.',
     'Seen from far off across the track, a race horse passes the white finish post ahead of two others, its jockey'
     ' standing up high in the stirrups, all of them small figures on the dirt.',
     'A race course grandstand full of people in hats, seen from far across the track.')

# Films, by the kind of film.
show('film-comedy', FILM,
     'A small boy alone in a big house at Christmas stands at the top of the stairs beside a paint can hanging on a string in'
     ' a family comedy.',
     'Two clumsy burglars in woolly hats sprawl on their backs at the foot of a staircase in a family comedy.',
     'A panicking mother and father throw suitcases into the back of a wood panelled station wagon in the driveway of'
     ' their suburban house at dawn, seen from the side, in a family comedy.',
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
     'A soldier with a riveted steel plate over half his face and a round glowing blue glass eye stands in a rainy'
     ' alley lit by unlettered neon in a science fiction action movie.',
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
show('film-art', ' A still image from a 1970s European art film shot on 35mm film, muted colour, fog and soft film'
                 ' grain. The picture fills the whole image edge to edge.',
     'A man in a long coat walks across a flooded field in fog.',
     'A candle burns on the floor of an empty ruined room, water dripping from the ceiling.',
     'A woman\'s face in profile beside a rain streaked window.',
     'A white horse stands in a misty meadow.',
     'A long empty corridor with peeling walls and a door open at the end.')
show('monster', ' A still image from a low budget 1980s monster movie shot on 35mm film, fog and blue night lighting,'
                ' soft with film grain. The picture fills the whole image edge to edge.',
     'The black silhouette of a werewolf, a tall man shaped figure standing upright on two legs with a shaggy wolf\'s'
     ' head, howls at a full moon on a foggy hill.',
     'A hairy grey clawed monster hand with long curved black nails reaches round the edge of a wooden door.',
     'Two screaming teenagers stand apart with their backs to the corner of a log cabin.',
     'Two glowing eyes among the trees of a dark forest.',
     'Villagers with burning torches gather in the street of an old German village at night.')
show('critics', VIDEO,
     'A balding man in a sweater vest and a woman in a blazer sit side by side in red velvet seats in an old cinema balcony,'
     ' arguing on a film review show.',
     'A balding film critic in a sweater vest shakes his head, seen from the chest up.',
     'A woman film critic in a blazer laughs, seen from the chest up.',
     'A beam of light from the small projection window high in the back wall shines out over rows of empty red cinema'
     ' seats in a dark old cinema.')
show('bad-movie', LOCAL,
     'A goofy man in his forties with a thin moustache and a bow tie sits in the front row of an empty old cinema with a'
     ' big glass bowl of popcorn on his lap, his face lit by the glow off the big screen, rows of smooth bare red velvet'
     ' seats stretching back into the dark behind him and gilded plaster walls at the sides.',
     'A grainy black and white 1950s science fiction film: a giant lizard monster with a scaly head, a long tail and a'
     ' gaping mouth towers over a small model city of cardboard buildings.',
     'A black and white 1950s science fiction film plays, a toy flying saucer hanging on a string over a desert.',
     'A goofy man in his forties with a thin moustache and a bow tie laughs and points up at a big cinema screen filled'
     ' by a giant grey lizard monster towering over a small model city, only the monster and the buildings on it.')

# A network's evening.
show('high-school', SITCOM,
     'Teenagers in colourful early 1990s clothes talk at a row of school lockers in a school hallway, the tiled ceiling'
     ' and its long lights along the top of the picture.',
     'A teacher stands at a plain blackboard in front of a class of bored teenagers at their desks, in a classroom with'
     ' cream walls, tall windows and its tiled ceiling in view.',
     'Four teenagers sit in a diner booth with milkshakes, the diner\'s chrome trim, red seats and low ceiling around'
     ' them.',
     'A teenage girl sits at the steering wheel of a car and a terrified middle aged driving instructor sits in the passenger'
     ' seat beside her, seen through the windshield.',
     'Teenagers dance under streamers in a school gym, its high ceiling of steel beams and its brick walls in view.')
show('irish', SITCOM,
     'A priest in a black suit and white collar talks to a farmer in a flat cap outside a stone pub with a plain unlettered'
     ' sign in a green Irish village.',
     'A housekeeper pours tea for a priest in an old fashioned kitchen with whitewashed walls and dark wooden ceiling'
     ' beams.',
     'Three men in sweaters sit laughing at a small table in an Irish pub, pints of stout standing on the table, the'
     ' low beamed ceiling and the dark wood panelled walls close around them.',
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
     'The bridge of a starship, a crew in coloured uniforms at consoles and a captain in the middle chair facing a screen full'
     ' of stars.',
     'An alien ambassador in long robes, a tall man in heavy rubber makeup with thick bony ridges running across his'
     ' forehead and over his bald head, in a 1980s science fiction series.',
     'A model starship flies past a ringed planet in a 1980s science fiction series.',
     'Three crew members in coloured uniforms that are plain from collar to boots stand well apart on a rocky planet'
     ' under a purple sky, seen from a little way off, their arms down at their sides and a small ray gun hanging in a'
     ' holster at each hip, in a 1980s science fiction series.',
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
     'A helicopter flies low over a desert road after a van in a 1980s action series.')
show('strange', DRAMA,
     'A man in a trench coat stands in a foggy graveyard at night, looking straight out of the frame towards the'
     ' viewer as he speaks, in a documentary series about the unexplained.',
     'A blurred dark hairy upright figure is half hidden between distant trees, out of focus and hard to make out, in a'
     ' documentary series about the unexplained.',
     'Three points of white light float high in the open black night sky in a triangle far above a dark farmhouse and '
     'its field, small and blurred like distant stars only brighter, in a documentary series about the unexplained.',
     'An old farmer is interviewed on his porch at night in a documentary series about the unexplained.',
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
     'A handsome film actor laughs on a talk show sofa.',
     'A talk show audience laughs in rows beyond the stage lights.')
show('family', SITCOM,
     'A suburban family sits on a sofa in a living room with papered walls and a white ceiling in view, the father\'s'
     ' hair scorched and sticking up.',
     'A mother stands in a kitchen full of white smoke with a red fire extinguisher, white foam covering a burnt pan on the'
     ' stove, the kitchen cupboards and the white ceiling around her.',
     'A family laughs round a dinner table in a dining room with papered walls and a white ceiling with a hanging lamp.',
     'Two children whisper on a carpeted staircase between papered walls, the white ceiling of the landing above them.',
     'A neighbour in a loud Hawaiian shirt covered in big orange and green flowers leans his head and shoulders in '
     'through a wide open front door, grinning, seen from inside the living room.')
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
     'An aerobics instructor, a woman with a high ponytail in a plain purple t-shirt, stands on a low platform facing'
     ' the viewer with her arms raised, a class of men and women in plain baggy t-shirts behind her copying her, all'
     ' seen from the waist up across the room, in a 1980s exercise show.',
     'Seen from the waist up across a gym, a row of four people a little apart doing squats: a heavy set bald man in a'
     ' grey sweatshirt, a young woman with a curly perm in a pink t-shirt, a grey haired older man in a navy t-shirt'
     ' and a tall woman with a brown bob in a yellow t-shirt, in a 1980s exercise show.',
     'A fit man in a headband and a grey t-shirt lifts a pair of dumbbells at his sides in a 1980s exercise show.',
     'Four grown women in their thirties in plain solid coloured t-shirts and long loose track pants step up and down '
     'on low plastic steps in a row across a gym, each on her own step, seen from across the room in a 1980s exercise '
     'show.')
show('garage', TAUGHT,
     'A mechanic in plain blue overalls stands beside a 1970s American sedan with its hood raised in a garage,'
     ' pointing down into the big V8 engine.',
     'A mechanic kneels beside a car changing a wheel, a jack under the car.',
     'A red metal toolbox with its drawers pulled open stands on a concrete garage floor beside an old car tyre, seen'
     ' close up.',
     'A mechanic in plain blue overalls leans over the open hood of an old car with his arms down inside the engine'
     ' bay, seen from the side a few paces back.')
show('first-aid', TAUGHT,
     'A paramedic in uniform bandages the forearm of a fully dressed volunteer who sits upright on a table.',
     'A paramedic kneels on the floor pressing on the chest of a training dummy.',
     'Rolled white bandages, a roll of white tape, a pair of round ended scissors and a few folded gauze pads laid out'
     ' in a row on a white table, seen from above.',
     'A woman doctor in a white coat sits in a doctor\'s office, looking straight out of the frame towards the viewer'
     ' as she speaks.',
     'A bandage is wrapped round a lower leg resting on a stool.')
show('guns', TAUGHT,
     'A man in a hunting vest stands at the bench of an outdoor shooting range, a single rifle lying on the bench in front of'
     ' him.',
     'A revolver lies on a cloth on a table with its cylinder swung open, six cartridges standing upright beside it.',
     'Round paper targets on wooden stands at the far end of an outdoor shooting range, a grass bank behind them.',
     'A grey haired Kentucky sheriff\'s firearms instructor in a tan uniform shirt stands at the front of a plain'
     ' American classroom with wood panelled walls and a green chalkboard, a few adult deputies in tan shirts at'
     ' school desks seen from behind.')
show('garden', TAUGHT,
     'A man in a straw hat and dungarees kneels among tomato plants in a vegetable garden.',
     'A seedling stands newly planted in dark soil, a trowel lying beside it.',
     'Pots of herbs on a sunny window sill.',
     'A basket of fresh vegetables on a garden path.',
     'A man sprays the leaves of a row of bean plants.')
show('fishing', OUTDOOR,
     'A man in a fishing vest and a plain cap sits alone in a small aluminium boat on a calm lake in the morning,'
     ' holding up a largemouth bass, seen from a little way off across the water.',
     'A man casts a fishing rod from a lake shore at dawn.',
     'An open tackle box full of lures on a wooden dock.',
     'A man in a small boat lifts a landing net out of the water with a big bass inside it.')
show('farm', OUTDOOR,
     'A girl feeds goats through the fence of a petting zoo.',
     'A rooster stands on a fence post at sunrise.',
     'Cows graze in a green pasture.',
     'A farmer drives an old red tractor across a field.',
     'Two muddy piglets lie asleep on their sides a little apart in deep golden straw in a dim barn, their eyes'
     ' closed, seen from above.')
show('trade', TAUGHT,
     'An old grey metal house fuse box on a basement wall with its door open, a row of round screw in glass fuses'
     ' inside, an electrician in a tool belt standing beside it seen from behind.',
     'A tangle of loose coloured electrical wires and a few wire nuts lie on a workbench beside a roll of black tape'
     ' and a pair of wire strippers, seen close up.',
     'A man solders inside the open back of a television set.',
     'A welder in a mask works in a shower of sparks.',
     'A man in a hard hat stretches wire along a line of fence posts.')
show('sewing', TAUGHT,
     'An old woman smiles at a full size white electric sewing machine on a wooden table in a cosy room.',
     'A spool of red thread and a round red pincushion stuck with pins lie beside a folded piece of denim on a table,'
     ' seen close up.',
     'A dressmaker\'s dummy dressed in a half finished cotton blouse stands in a sewing room, a tape measure hanging round its'
     ' neck.',
     'Long heavy steel dressmaking shears with black handles, as long as a forearm, lie on a cutting table beside a '
     'length of blue cloth already cut in two along a straight white chalk line, the two pieces pulled a little apart.')
show('pottery', TAUGHT,
     'A wet clay pot rises on a turning potter\'s wheel, the potter\'s hands resting round it.',
     'A potter in an apron sits at a wheel in a studio lined with shelves of pots.',
     'A row of glazed pots on a wooden shelf.')
show('lesson', TAUGHT,
     'A woman in a business suit stands in a beige office with a pot plant, looking straight out of the frame towards'
     ' the viewer as she speaks, in a staff training video.',
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
