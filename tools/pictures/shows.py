"""What is on television: the shows a set can be showing and the shots the mod has of each.

A show is a handful of shots, each a still made from a text prompt alone (generate.py) and packed four wide for three
high as screen-<show>-<n> (pack.py). A set cuts from one shot of its show to the next and moves across each as it is
drawn (Screens.java). The shows and their names are the mod's own.

Which of the game's broadcasts and tapes is which show is told by schedule.py, from patterns kept outside the repository.
"""

VIDEO = ', still frame from a 1990s american television broadcast, analog video, NTSC colour, slightly soft, studio lighting'
FIELD = ', still frame from 1990s television news footage, shoulder mounted video camera, analog video, NTSC colour, slightly soft'
LOCAL = ', still frame from a low budget 1990s public access television show, cheap studio set, flat lighting, analog video, slightly soft'
SITCOM = ', still frame from a 1990s television sitcom, multi camera studio set, bright even lighting, analog video, slightly soft'
DRAMA = ', still frame from a 1980s television drama series, 16mm film transferred to video, slightly soft'
FILM = ', still frame from a 1980s hollywood movie, 35mm film, cinematic lighting, film grain'
CEL = ', 1980s saturday morning cartoon, cel animation, flat bright colours, thick outlines'
HOME = ', still frame from a 1990 home video, consumer camcorder footage, VHS, washed out colour, slightly blurry, amateur framing'
CLIP = ', still frame from a 1990s music video, analog video, coloured stage lighting, smoke'
SPORT = ', still frame from a 1990s televised sports broadcast, long lens, analog video, NTSC colour, slightly soft'
NATURE = ', still frame from a 1990s nature documentary, 16mm film, long lens'
ARCHIVE = ', 1950s black and white newsreel footage, film grain, soft focus'
TAUGHT = ', still frame from a 1990s instructional video, VHS, flat lighting, slightly soft'

# The shots of each show, in the order the mod numbers them.
SHOWS = {}


def show(key, look, *shots):
    if key in SHOWS:
        raise ValueError('two shows are called ' + key)
    SHOWS[key] = [shot + look for shot in shots]


# The news, and what the news shows as things get worse.
show('news-desk', VIDEO,
     'a woman news anchor in a red blazer sitting at a glass news desk, world map backdrop in blue',
     'close up of a serious male news anchor in a dark suit and striped tie, blue studio backdrop',
     'two news anchors at a long news desk seen from a wide camera, a busy newsroom with monitors behind them',
     'a news anchor holding papers and looking off to one side, a map of a river valley on a screen over her shoulder',
     'a television news studio seen from behind the cameras, an anchor small at the desk under the lights')
show('news-field', FIELD,
     'a woman reporter in a beige trench coat holding a microphone in front of an army roadblock with trucks and barbed wire at dusk',
     'soldiers in gas masks standing at a checkpoint on a rural highway, concrete barriers, overcast',
     'a long traffic jam of cars on a two lane highway through fields seen from a helicopter',
     'a crowd of worried people pressed against a chain link fence, soldiers on the other side',
     'a woman reporter at night lit by a camera light, flashing lights of emergency vehicles far behind her')
show('pundit', VIDEO,
     'a stern grey haired man in a dark suit pointing at the camera from behind a wooden desk, an american flag and red curtain behind him',
     'close up of an angry grey haired television pundit shouting, red curtain behind him',
     'a television opinion show set with a wooden desk, a leather chair, a flag and red curtains, the host small in the middle',
     'a grey haired man in a dark suit leaning back in a leather chair with his arms folded, smug')
show('general', FIELD,
     'an army general in a green dress uniform with medals speaking at a podium with microphones outdoors, soldiers standing behind him',
     'close up of a grim faced army general in a green uniform and cap',
     'a crowd of press photographers and reporters holding up cameras and microphones, flashes going off',
     'army tents and a radio mast in a field with jeeps and soldiers, a field command post')
show('expert', VIDEO,
     'a professor with a grey beard and a tweed jacket being interviewed in a book lined office',
     'a scientist in a white lab coat standing in a laboratory with glassware and a microscope',
     'close up of a gloved hand holding a glass slide under a microscope',
     'a woman doctor in a white coat talking to an interviewer in a hospital corridor')
show('unrest', FIELD,
     'a city street at night with a burning car, a crowd running and police in riot helmets with shields',
     'protesters holding up blank placards in front of a government building with columns',
     'army trucks driving down a city avenue between tall buildings, people watching from the pavement',
     'a looted shop front with broken glass on the pavement at dawn',
     'a city seen from a helicopter at night with several fires and smoke rising',
     'people queueing with shopping trolleys outside a supermarket with empty shelves')
show('emergency', VIDEO,
     'an empty television news desk with the chair pushed back and papers left on it, studio lights on',
     'a tired official in shirt sleeves reading from a sheet of paper at a plain desk with a flag beside him',
     'a plain blue television backdrop with a gold eagle crest in the middle',
     'an empty television studio with a camera left pointing at an empty chair')
show('local-desk', VIDEO,
     'a male local news anchor in a brown suit at a wood panelled news desk, a photograph of a city skyline behind him',
     'a woman local news anchor with big hair in a green blazer at a wood panelled news desk',
     'two local news anchors turning to one another at a wood panelled desk, a small city television studio',
     'a local weather forecaster pointing at a map with sun and cloud symbols')
show('talk', VIDEO,
     'a woman talk show host in an armchair facing a male guest in an armchair, a night time city window behind them, potted plant',
     'close up of a woman talk show host listening with her chin on her hand, dark blue studio',
     'a bearded man in a corduroy jacket gesturing as he talks in an armchair, dark blue studio',
     'a late night television talk show set with two armchairs and a low table seen from the back of the studio')
show('local-field', FIELD,
     'a male reporter in a windbreaker holding a microphone at a police line across a rural road, patrol cars behind',
     'a television news van with its mast up parked on a grass verge by a highway',
     'a dark country road with abandoned cars seen through a windscreen at night, headlights',
     'people running across a field at dusk seen from far off, shaky camera',
     'a frightened male reporter crouching behind a car and looking over his shoulder, night')
show('statement', VIDEO,
     'a man in a dark suit speaking at a podium in front of a blue curtain and two flags',
     'a room full of reporters in suits raising their hands, seen from the podium',
     'a white government building with columns lit up at night',
     'close up of a grave man in a dark suit and red tie speaking into microphones')

# Home and hobbies.
show('cook', VIDEO,
     'a cheerful woman in an apron stirring a pot in a television studio kitchen, cooking show',
     'hands chopping carrots and onions on a wooden board seen from above, cooking show',
     'a roast chicken on a platter with vegetables on a kitchen counter, cooking show',
     'a cheerful woman in an apron holding up a mixing bowl and a whisk and smiling, television studio kitchen',
     'a pan on a gas stove with steam rising, a wooden spoon stirring, close up',
     'a frosted layer cake on a stand on a kitchen counter, cooking show')
show('woodwork', VIDEO,
     'a bearded man in a plaid shirt and safety glasses sawing a plank on sawhorses in a workshop',
     'close up of hands hammering a nail into pine boards on a workbench',
     'a bearded man in a plaid shirt standing proudly beside a finished wooden bookcase in a workshop',
     'a wall of hand tools hanging on a pegboard over a workbench',
     'close up of a hand plane curling a shaving off a board',
     'a bearded man in a plaid shirt measuring a board with a folding rule, workshop')
show('survival', FIELD,
     'a rugged man in a khaki shirt and a bandana crouching by a small campfire in a forest and talking to the camera',
     'a rugged man in a khaki shirt holding up a trout beside a river',
     'a lean to shelter made of branches and leaves in a forest clearing',
     'close up of hands tying a snare of cord to a bent sapling',
     'a rugged man in a khaki shirt walking through tall ferns and looking back at the camera',
     'a pot of water boiling over a campfire at dusk, tent behind')
show('advert', VIDEO,
     'a man and a woman in bright sweaters behind a table of kitchen gadgets, a studio audience behind them, shopping show',
     'close up of a kitchen knife slicing a tomato on a white board',
     'a shiny electric cooking pot on a kitchen counter with steam coming out, product shot',
     'a studio audience clapping and smiling',
     'a smiling presenter holding a kitchen gadget up to the camera',
     'a cosy living room lit by a bright ceiling lamp, a family on the sofa')
show('doctor', VIDEO,
     'an elderly doctor in a white coat sitting on the edge of a desk in a consulting room and talking to the camera',
     'a smiling elderly couple walking arm in arm through a park',
     'an elderly doctor in a white coat holding up a small bottle of pills')
show('bear', VIDEO,
     'a plush teddy bear in a red bow tie sitting on a child\'s bed in a sunny bedroom, toy commercial',
     'two small children hugging a plush teddy bear in a playroom, toy commercial',
     'a plush teddy bear sitting at a toy picnic on the grass with a tea set, toy commercial')
show('phone-in', LOCAL,
     'a middle aged man in a cardigan at a desk with a telephone and a mug, wood panelled backdrop',
     'a middle aged man in a cardigan holding a telephone receiver to his ear and nodding',
     'close up of a desk telephone with a row of buttons, one lit',
     'a middle aged man in a cardigan laughing with his head thrown back at a desk')

# Entertainment.
show('hollywood', VIDEO,
     'a glamorous woman presenter in a pink blazer in front of a backdrop of spotlights and palm trees, entertainment news',
     'film stars in evening dress walking a red carpet past photographers at a premiere at night',
     'a film set with a camera on a crane, lights and a folding chair',
     'a handsome actor in a tuxedo and an actress in a sequin gown waving to a crowd',
     'a cinema front at night with a lit blank marquee and a crowd outside')
show('detectives', DRAMA,
     'two detectives in suits arguing with a police captain in a cluttered precinct office',
     'a plain sedan with a red light on its dashboard parked in a city alley at night',
     'a detective in a raincoat with a revolver drawn standing beside a door in a dim hallway',
     'a suspect sitting at a table in a bare interrogation room under one lamp, a detective leaning over him',
     'two detectives running across a rooftop with the city behind them')
show('barrister', DRAMA,
     'an english barrister in a white wig and black gown standing to speak in an oak panelled courtroom',
     'an english judge in red robes and a long wig looking down from the bench',
     'an elderly man in a waistcoat writing in a diary at a desk by lamplight',
     'a nervous man standing in the dock of an english courtroom',
     'a london street in the rain with a black taxi and a man under an umbrella')
show('soap', VIDEO,
     'a glamorous woman with big blonde hair and a man in a tuxedo arguing in an opulent living room, daytime soap opera',
     'close up of a tearful woman with heavy eye make up and big hair, daytime soap opera',
     'a man lying in a hospital bed with a bandaged head, a woman in a fur coat at the bedside, daytime soap opera',
     'a couple in evening dress on a terrace over a city at sunset, daytime soap opera',
     'a woman in a red dress slapping a man at the foot of a mansion staircase, daytime soap opera')
show('quiz', VIDEO,
     'a game show host in a sparkly blue jacket holding a microphone on a brightly lit stage with coloured lights',
     'three game show contestants standing behind podiums with buzzers',
     'a game show contestant jumping for joy under falling confetti',
     'a new car on a turning stage under coloured lights, game show prize',
     'a game show audience clapping and cheering')

# For children.
# The heroes are the mod's own: helmets with visors, green and orange, no capes, no masks with ears, no chest emblems.
OWN_HEROES = ', original characters, green and orange jumpsuits, round helmets with visors, backpacks, no capes'
show('heroes', CEL,
     'saturday morning cartoon, a team of five space rangers standing in a row on a rooftop at night' + OWN_HEROES,
     'saturday morning cartoon, a space ranger flying with a jet backpack over a city skyline' + OWN_HEROES,
     'saturday morning cartoon, three space rangers facing a giant grey robot in a city street' + OWN_HEROES,
     'saturday morning cartoon, a villain in a long grey coat and a tall metal helmet laughing in a lair full of machines',
     'saturday morning cartoon, a team of space rangers standing round a glowing table in a futuristic control room' + OWN_HEROES)
show('puppets', VIDEO,
     'felt hand puppets of a fox and an owl in a painted cardboard forest, children\'s puppet show',
     'a smiling presenter in dungarees kneeling beside puppets of a badger and a rabbit in a painted forest, children\'s puppet show',
     'a hedgehog puppet sitting at a tiny table with a tea set, children\'s puppet show',
     'an owl puppet looking out of a hole in a cardboard tree, children\'s puppet show',
     'felt puppets of woodland animals singing in a row in front of painted trees, children\'s puppet show')
show('bunnies', ', 1980s cartoon for very small children, cel animation, soft pastel colours, round simple shapes, thick outlines',
     'three round pastel cartoon bunnies hugging in a meadow of flowers under a rainbow',
     'two round pastel cartoon bunnies building a tower of toy blocks',
     'round pastel cartoon bunnies having a picnic on a checked blanket',
     'a round pastel cartoon bunny holding a red balloon on a green hill',
     'round pastel cartoon bunnies asleep in a row under a smiling moon')
show('kids-advert', VIDEO,
     'two excited children at a beige 1990s home computer with a boxy monitor on a desk, commercial',
     'a loud salesman in a checked jacket with his arms spread wide in a shop aisle stacked with boxes, commercial',
     'a plastic toy robot on a kitchen table beside loose batteries, toy commercial',
     'a family playing a board game on a living room carpet, commercial')

# Public access.
show('tech', LOCAL,
     'a young man with glasses and a headset at a desk crowded with beige 1990s computers and circuit boards, a curtain behind him',
     'close up of hands with a screwdriver inside an open beige computer case',
     'a boxy computer monitor glowing green on a cluttered desk',
     'a young man with glasses holding up a circuit board and grinning')
show('interview', LOCAL,
     'a woman interviewer in a blazer and a senator in a suit in two armchairs facing one another, a fern and a flag behind them',
     'close up of a silver haired senator in a suit answering a question',
     'a woman interviewer in a blazer looking down at her notes')
show('clown', LOCAL,
     'a clown with an orange wig and a red nose waving in front of a paper backdrop with balloons, children\'s show',
     'a clown with an orange wig juggling three coloured balls, children\'s show',
     'a clown with an orange wig holding a sock puppet, children\'s show',
     'small children sitting cross legged on a carpet and laughing, children\'s show')
show('county', FIELD,
     'a woman presenter in a denim jacket standing by a white fence on a horse farm, rolling green hills',
     'a county fair with a ferris wheel and stalls on a summer evening',
     'an old man whittling a stick on the porch of a wooden house',
     'a covered wooden bridge over a creek among autumn trees',
     'a tobacco barn in a field at sunset',
     'horses grazing behind a white fence')
show('survivors', ', still frame from a home video camera on a tripod, VHS, harsh single light, dim, grainy',
     'an exhausted man in a dirty flannel shirt sitting at a desk in a dim television studio, a barricaded door behind him',
     'a tired woman with a bandaged arm talking to the camera in a dim television studio',
     'a young man in a cap crouching beside a portable generator and fuel cans in a dim room',
     'tins of food and jugs of water stacked on the floor of a dim television studio',
     'an empty desk with an overturned chair in a dim television studio, a light flickering')

# Music videos.
show('ballad', CLIP,
     'a singer in a white suit at a grand piano among candles and fog',
     'a woman singing in the rain on a city street at night, neon reflected in the wet road',
     'a couple in silhouette on a beach at sunset',
     'close up of a singer with a microphone in soft blue light',
     'a single red rose lying on the keys of a piano')
show('pop', CLIP,
     'three young women in bright colour blocked outfits dancing on a white studio set',
     'four young men in denim jackets posing against a painted brick wall',
     'a singer with a headset microphone dancing in front of four dancers under coloured lights',
     'close up of a young woman pop singer with big hoop earrings singing',
     'a crowd of teenagers dancing with their arms in the air')
show('rock', CLIP,
     'a long haired rock band on a stage with jets of flame and a wall of amplifiers',
     'a long haired guitarist playing a solo in a single spotlight',
     'a drummer in a vest behind a huge drum kit under red lights',
     'a rock singer in scarves and leather screaming into a microphone stand',
     'a concert crowd with raised fists seen from the stage')
show('soul', CLIP,
     'a smooth singer in a silk shirt in a loft lit by candles',
     'a woman in a long satin dress singing beside a saxophone player',
     'close up of hands on the keys of an electric piano in warm light',
     'a couple slow dancing in a dim room with a window on the city lights')
show('rap', CLIP,
     'three rappers in baggy tracksuits and caps on a city basketball court beside a big portable stereo',
     'a long low car at night under street lights with young men leaning on it',
     'a disc jockey with headphones scratching a record on two turntables',
     'a group of young men posing on the stone steps of a city terrace house')
show('club', CLIP,
     'dancers in a dark night club under green lasers and smoke',
     'a disc jockey in a booth among smoke and flashing lights',
     'silhouettes of dancers against a wall of flashing coloured lights',
     'glowing coloured shapes spinning on a black background, 1990s video art')
show('eighties', CLIP,
     'a singer with big hair and shoulder pads against a glowing neon grid',
     'a man in a pastel suit playing a keyboard slung on a strap',
     'a red sports car on a highway at night under neon lights',
     'a band in pastel suits miming with their instruments on a beach',
     'a woman in a leotard and leg warmers dancing in a dance studio')

# Sport.
show('sports-desk', VIDEO,
     'two sports presenters in blazers at a desk with a trophy on it, a wall of monitors behind them',
     'a sports presenter in a blazer standing beside a wall of television screens',
     'close up of a grinning sports presenter with a moustache holding papers',
     'a locker room interview, a reporter holding a microphone up to a sweating athlete')
show('baseball', SPORT,
     'a baseball pitcher in a plain white uniform throwing from the mound, green outfield grass behind him',
     'a baseball batter in a plain grey uniform swinging, the catcher and the umpire crouched behind him',
     'a baseball field seen from high behind home plate on a sunny afternoon, full stands',
     'a baseball outfielder leaping to catch a ball at the wall',
     'baseball players in plain uniforms leaning on the rail of a dugout',
     'a baseball runner sliding into a base in a cloud of dust')
show('golf', SPORT,
     'a golfer in a polo shirt driving from the tee, a line of spectators behind him',
     'a golfer crouching to line up a putt on a green',
     'a golf ball rolling toward the hole and the flag on a green',
     'a golf fairway between tall pines with a lake beside it',
     'a golfer and his caddie walking down a fairway')
show('poker', VIDEO,
     'five players round a green poker table under a low lamp in a casino, stacks of chips',
     'close up of hands lifting the corners of two playing cards beside a stack of chips',
     'a poker player in sunglasses and a cowboy hat staring across the table',
     'a casino dealer in a waistcoat dealing cards across green felt',
     'a pile of poker chips being pushed into the middle of a green table')
show('racing', SPORT,
     'open wheel racing cars going through a banked corner of a race track',
     'a pit crew in overalls changing the tyres of a racing car',
     'the view from the cockpit of a racing car down a straight, another car ahead',
     'racing cars lined up on the starting grid seen from above',
     'a racing driver in overalls spraying champagne on a podium',
     'stock cars racing side by side round a dirt oval')
show('skating', SPORT,
     'a skateboarder in a helmet in mid air above a wooden half pipe, a crowd watching',
     'a skateboarder sliding along a metal rail',
     'a boy on a small bicycle in mid air over a dirt jump',
     'teenagers in baggy clothes cheering beside a skate ramp')
show('boxing', ', still frame from a 1970s televised boxing match, 16mm colour film, ring lights, smoke',
     'two heavyweight boxers trading punches in a ring',
     'a boxer landing a punch that snaps the other man\'s head back',
     'a referee in a bow tie counting over a boxer on the canvas',
     'a boxing crowd on its feet round a ring',
     'a boxer on his stool in the corner with a trainer holding a towel')
show('old-game', ARCHIVE,
     'a baseball batter swinging in a 1950s stadium',
     'a 1950s baseball crowd of men in hats cheering',
     'an old baseball stadium seen from the upper deck',
     'baseball players in baggy flannel uniforms shaking hands')
show('soccer', SPORT,
     'a soccer player in a red shirt dribbling past a defender in a white shirt on a small town pitch',
     'a goalkeeper diving to save a shot',
     'soccer players in red shirts hugging after a goal',
     'a small crowd in coats watching a soccer match from a low stand')
show('horse-race', SPORT,
     'race horses and jockeys in bright silks galloping round a turn',
     'race horses bursting out of the starting gate',
     'a jockey standing in the stirrups as his horse crosses the line ahead of two others',
     'a race course grandstand full of people in hats')

# Films, by the kind of film.
show('film-comedy', FILM,
     'a small boy alone in a big house at christmas tying a string to a paint can at the top of the stairs, family comedy',
     'two clumsy burglars in woolly hats slipping on a staircase, family comedy',
     'a panicking mother and father at an airport desk with suitcases, family comedy',
     'two teenage boys in denim with electric guitars in a garage, teen comedy',
     'a bride and groom running from a church in a shower of rice with the guests in a muddle, comedy',
     'a man in a crumpled suit sitting on a kerb in the rain with a cardboard box of his things, comedy')
show('film-action', FILM,
     'a spy in a tuxedo standing beside a silver sports car on a mountain road, spy thriller',
     'a man diving away from an exploding warehouse, action movie',
     'a helicopter chasing a speeding car along a canyon road, action movie',
     'a man clinging to the roof of a moving train, action movie',
     'a fighter pilot in a helmet and mask in a jet cockpit above the clouds, action movie',
     'a muscular man in a torn vest holding a machine gun in a jungle, action movie',
     'a man in a suit running through a crowded railway station looking over his shoulder, thriller')
show('film-horror', FILM,
     'a woman with a torch walking down the aisle of a dark church, horror movie',
     'the shadow of a figure with a raised knife on a motel wall, horror movie',
     'thin pale figures in a doorway full of white light at the end of a bedroom, horror movie',
     'a pale man in a black cloak in a candle lit crypt, horror movie',
     'a terrified woman\'s face lit from below in the dark, horror movie',
     'an old motel under a bare tree at night with one lit window, horror movie')
show('film-crime', FILM,
     'gangsters in pinstripe suits round a table in an italian restaurant, crime drama',
     'a policeman in a leather jacket with a big revolver in a city alley at night, police thriller',
     'diamonds spilling from a briefcase on a crate in a warehouse, crime movie',
     'a 1970s car chase over a steep city hill, police thriller',
     'a private detective in a hat sitting in an office striped with light through blinds, film noir',
     'young men in bandanas on a street corner beside a parked car at dusk, crime drama')
show('film-romance', FILM,
     'a man in a white dinner jacket and a woman in a hat at a table in a moroccan cafe under ceiling fans, romantic classic',
     'a governess and a gentleman in victorian dress in the library of a country house, period drama',
     'a couple dancing with umbrellas on a rainy paris street, classic musical',
     'a couple kissing in the rain under a street lamp, romantic movie',
     'a woman in a silk robe writing a letter at a window, romantic drama',
     'a man and a woman laughing over coffee in a diner on a snowy day, romantic comedy')
show('film-western', FILM,
     'a gunslinger with a big moustache standing in the dusty main street of a wooden town, western',
     'riders on horseback crossing a desert canyon, western',
     'cowboys playing cards at a saloon table, western',
     'a sheriff with a star on his waistcoat watching the sun set from a porch, western',
     'a stagecoach racing across a plain in a cloud of dust, western')
show('film-war', FILM,
     'sailors at the periscope and the dials of a submarine control room under red light, war movie',
     'a submarine surfacing in a grey sea, war movie',
     'soldiers wading through a jungle river with their rifles held up, war movie',
     'a naval officer with a headset looking up as the hull creaks, war movie',
     'tall plumes of water from depth charges behind a warship, war movie')
show('film-future', FILM,
     'a cyborg with half a metal face and a glowing red eye in a rainy alley lit by neon, science fiction action movie',
     'a motorcycle chased by a huge truck down a desert highway, science fiction action movie',
     'sparks and molten steel in a dark factory, a figure walking through them, science fiction action movie',
     'a woman with a shotgun standing in the doorway of a wrecked bar, science fiction action movie',
     'a metal robot hand reaching up out of rubble, science fiction action movie')
show('film-epic', FILM,
     'a herd of woolly mammoths crossing a snowy valley, adventure movie',
     'a sabre toothed cat snarling on a rock, adventure movie',
     'explorers in fur coats looking up at a mammoth, adventure movie',
     'people in furs round a fire in a painted cave, adventure movie',
     'a wide frozen plain under a low sun with a line of tiny figures walking, adventure movie')
show('film-art', ', still frame from a 1970s european art film, 35mm film, muted colour, fog, long take',
     'a man in a long coat walking across a flooded field in fog',
     'a candle burning on the floor of an empty ruined room with water dripping from the ceiling',
     'a woman\'s face in profile beside a rain streaked window',
     'a white horse standing in a misty meadow',
     'a long empty corridor with peeling walls and a door open at the end')
show('monster', ', still frame from a low budget 1980s monster movie, 35mm film, fog, blue night lighting',
     'the silhouette of a shaggy dog headed monster howling on a foggy hill under a full moon',
     'a hairy clawed hand reaching round the edge of a door',
     'screaming teenagers backing into the corner of a log cabin',
     'two glowing eyes among the trees of a dark forest',
     'villagers with burning torches in the street of an old german village at night')
show('critics', VIDEO,
     'a man in a sweater vest and a woman in a blazer arguing in the red seats of a cinema balcony, film review show',
     'close up of a balding film critic in a sweater vest shaking his head',
     'close up of a woman film critic in a blazer laughing',
     'the beam of a projector over rows of empty red cinema seats')
show('bad-movie', LOCAL,
     'a goofy man in a bow tie sitting in the front row of an empty old cinema with a tub of popcorn',
     '1950s black and white science fiction film still, a man in a rubber monster suit attacking a cardboard city',
     '1950s black and white science fiction film still, a toy flying saucer hanging over a desert',
     'a goofy man in a bow tie laughing and pointing at a cinema screen')

# A network's evening.
show('high-school', SITCOM,
     'teenagers in colourful 1990s clothes talking at a row of school lockers',
     'a teacher at a blackboard with a class of bored teenagers, one passing a note',
     'four teenagers in a diner booth with milkshakes',
     'a terrified driving instructor beside a teenage girl at the wheel of a car',
     'teenagers dancing under streamers in a school gym')
show('irish', SITCOM,
     'a priest in a black suit and white collar talking to a farmer in a flat cap outside a stone pub in a green village',
     'a housekeeper pouring tea for a priest in an old fashioned kitchen',
     'men in jumpers laughing over pints in a small irish pub',
     'sheep blocking a narrow lane in front of a small old car',
     'a priest on a bicycle on a lane between stone walls')
show('network-news', VIDEO,
     'a distinguished grey haired news anchor in a dark suit at a large desk, deep blue backdrop with a globe',
     'a network news studio seen wide with cameras and a lit desk',
     'a correspondent in an overcoat with a microphone in front of a domed capitol building',
     'farmland and a road block with army trucks seen from a helicopter',
     'close up of a distinguished grey haired news anchor looking grave')
show('cop-show', DRAMA,
     'a policeman in mirrored sunglasses and a leather jacket walking away from an explosion, 1980s action series',
     'an angry police captain shouting across a desk, 1980s action series',
     'a muscle car in mid air over the crest of a hill, 1980s action series',
     'a gun fight among crates on a dock, 1980s action series',
     'two policemen eating burgers on the bonnet of a car, 1980s action series')
show('space', DRAMA,
     'the bridge of a starship with a crew in coloured uniforms at consoles and a captain in the middle chair, a screen full of stars',
     'an alien ambassador with a ridged forehead in long robes, 1980s science fiction series',
     'a model starship flying past a ringed planet, 1980s science fiction series',
     'three crew in coloured uniforms with ray guns on a rocky planet under a purple sky, 1980s science fiction series',
     'an engine room with a glowing column and an engineer at the controls, 1980s science fiction series')
show('action-team', DRAMA,
     'four tough men standing in front of a black van in a desert scrapyard, 1980s action series',
     'a man welding armour plate onto a truck in a barn in a shower of sparks, 1980s action series',
     'a jeep flipping over in an explosion on a dirt road, 1980s action series',
     'a grinning grey haired man with a cigar, 1980s action series',
     'a helicopter flying low over a desert road after a van, 1980s action series')
show('strange', DRAMA,
     'a man in a trench coat standing in a foggy graveyard at night and talking to the camera, documentary series about the unexplained',
     'a blurred dark ape like figure walking between the trees of a forest, documentary series about the unexplained',
     'strange lights in the night sky over a farmhouse, documentary series about the unexplained',
     'an old farmer being interviewed on his porch at night, documentary series about the unexplained',
     'an old house at night with one lit window, documentary series about the unexplained')
show('chat', VIDEO,
     'a late night talk show host behind a desk with a city skyline backdrop, a film actor on the sofa beside it',
     'a talk show host in a suit telling a joke with a microphone in front of a curtain',
     'a talk show band in matching jackets playing horns',
     'a handsome film actor laughing on a talk show sofa',
     'a talk show audience laughing')
show('family', SITCOM,
     'a suburban family on a sofa in a living room, the father with scorched hair sticking up',
     'a mother in a smoky kitchen holding a fire extinguisher',
     'a family laughing round a dinner table',
     'two children whispering on a staircase',
     'a neighbour in a loud shirt leaning in at a front door')
show('britcom', SITCOM,
     'a woman in a floral dress and pearls pouring tea in a chintzy english sitting room, british sitcom',
     'two neighbours gossiping over a garden fence, british sitcom',
     'a vicar balancing a teacup on a sofa between two old ladies, british sitcom',
     'a man in a cardigan hiding behind a newspaper while his wife talks, british sitcom')

# What comes on a tape and is not on the air.
show('fitness', VIDEO,
     'an aerobics instructor in a pink leotard and leg warmers leading a workout class, 1980s exercise show',
     'a row of people in bright leotards doing squats, 1980s exercise show',
     'a muscular man in a headband and vest lifting dumbbells, 1980s exercise show',
     'people stepping up and down on plastic steps in a gym, 1980s exercise show')
show('garage', TAUGHT,
     'a mechanic in overalls pointing under the raised bonnet of a car in a clean garage',
     'a mechanic changing a car wheel beside a jack',
     'close up of hands with a spanner on a car engine',
     'a mechanic checking the oil with a dipstick and a rag')
show('first-aid', TAUGHT,
     'a paramedic in uniform bandaging the arm of a volunteer who sits on a table',
     'a paramedic pressing on the chest of a training dummy on the floor',
     'an open first aid kit with bandages and scissors on a table',
     'a woman doctor in a white coat talking to the camera in a surgery',
     'hands wrapping a bandage round a lower leg')
show('guns', TAUGHT,
     'a man in a hunting vest standing at the bench of an outdoor shooting range with a rifle lying on it',
     'close up of hands loading the cylinder of a revolver',
     'paper targets on stands at the far end of a shooting range',
     'a police instructor in uniform talking to a classroom of officers')
show('garden', TAUGHT,
     'a man in a straw hat and dungarees kneeling among tomato plants in a vegetable garden',
     'close up of hands planting a seedling in dark soil',
     'pots of herbs on a sunny window sill',
     'a basket of fresh vegetables on a garden path',
     'a man spraying the leaves of a row of bean plants')
show('fishing', FIELD,
     'a man in a fishing vest and cap holding up a largemouth bass in a small boat on a lake',
     'a man casting a fishing rod from a lake shore at dawn',
     'an open tackle box full of lures on a jetty',
     'a man lifting a fish out of the water in a landing net')
show('farm', FIELD,
     'a girl feeding goats through the fence of a petting zoo',
     'a rooster on a fence post at sunrise',
     'cows grazing in a green pasture',
     'a farmer on an old red tractor in a field',
     'piglets in the straw of a barn')
show('trade', TAUGHT,
     'an electrician in a tool belt working at an open fuse box',
     'hands holding the probes of a meter to wires on a workbench',
     'a man soldering inside the open back of a television set',
     'a welder in a mask in a shower of sparks',
     'a man in a hard hat stretching wire along a line of fence posts')
show('sewing', TAUGHT,
     'an old woman smiling at a sewing machine in a cosy room',
     'close up of hands pushing a needle through denim',
     'a tape measure hanging round the neck of a dressmaker\'s dummy',
     'hands cutting cloth with big scissors along a chalk line')
show('pottery', TAUGHT,
     'hands shaping a wet clay pot on a turning wheel',
     'a potter in an apron sitting at a wheel in a studio lined with shelves of pots',
     'a row of glazed pots on a wooden shelf')
show('lesson', TAUGHT,
     'a woman in a business suit talking to the camera in a beige office with a pot plant, staff training video',
     'a professor in a jacket chalking a diagram on a blackboard',
     'a dentist in a white coat holding a big model of teeth and a toothbrush',
     'a flight instructor pointing at the dials of a light aircraft cockpit',
     'two people in suits shaking hands across an office desk, staff training video',
     'a clergyman in a grey suit talking gently to the camera in front of a stained glass window')
show('nature', NATURE,
     'a white tailed deer standing in a misty forest clearing at dawn',
     'penguins crowded on an ice shelf',
     'a red fox pouncing in the snow',
     'an alligator lying in a green swamp',
     'a wide river winding between wooded hills seen from the air',
     'a brown bear catching a salmon at a waterfall',
     'a spiral galaxy and stars in deep space, astronomy documentary')
show('travel', ', still frame from a 1990s travel documentary, 16mm film, sunny',
     'the roofs of paris with the eiffel tower beyond them',
     'a crowded street market in a west african city',
     'the painted gate of a korean palace with a modern city behind it',
     'a white rocket standing on its launch pad beside the sea',
     'a row of old tanks in front of a brick army building',
     'a river barge passing under a steel bridge')
show('birthday', HOME,
     'a child blowing out the candles on a birthday cake with the family round the table',
     'children in paper hats at a birthday party in a living room',
     'a grandmother laughing in front of a birthday cake',
     'a teenage boy opening a present on a sofa',
     'a blindfolded child swinging a stick at a paper donkey in a garden')
show('wedding', HOME,
     'a bride and groom standing before a minister in a small church',
     'a bride and groom cutting a tiered cake',
     'wedding guests dancing in a hall hung with streamers',
     'a bride in a white dress laughing with her bridesmaids on a lawn')
show('baby', HOME,
     'a toddler taking wobbly steps across a living room carpet toward a kneeling mother',
     'a baby in a high chair with food on its face',
     'a father holding a baby up in the air in a garden',
     'a baby crawling across a carpet toward the camera')
show('vacation', HOME,
     'a mother and father in t-shirts and shorts waving from under a striped umbrella on a sandy beach',
     'a father and two children in front of a fairground wheel',
     'a donkey looking over a stone wall in a green field',
     'an empty motel swimming pool under palm trees with sun loungers and a striped towel',
     'a family posing in front of a big rocket in a museum garden')
show('christmas', HOME,
     'children in pyjamas tearing open presents under a christmas tree',
     'a family round a table with a roast turkey and paper crowns',
     'small children dressed as shepherds and angels on a school stage',
     'a man in a christmas jumper holding up a pair of socks and pulling a face')
show('pets', HOME,
     'a box of kittens on a kitchen floor',
     'a dog leaping to catch a flying disc in a back garden',
     'a child hugging a big shaggy dog on a porch',
     'a kitten batting at a ball of wool on a carpet')
show('stage', HOME,
     'a boy in a white shirt playing a saxophone on a school stage',
     'a row of children singing on risers in a school hall',
     'teenagers in cloaks acting on a school stage with painted scenery',
     'parents on folding chairs clapping in a school hall')

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
