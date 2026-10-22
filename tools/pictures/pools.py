"""Pictures that hang in place of the family photographs where a family album does not belong.

The game hangs the same "Painting" sprites of family photographs in houses, restaurants, bars, offices and shops. A house
shows the family pictures of catalogue.py; a room of another kind shows one of its own kind's pictures here instead, chosen
by the room the sprite hangs in (Decor.inRoom). POOLS is room kind -> prompts; each becomes the picture pool-<kind>-NN.
"""

# No place is named: Qwen-Image letters a named place onto a sign or a caption in the picture.
PRINT = (' A snapshot on 35mm colour negative film from the 1980s, faded colours with soft contrast and fine grain,'
         ' ordinary amateur composition, natural colours. The picture fills the whole image edge to edge.')
# "Photograph" and "print" give the picture a white paper border.
OLD_PRINT = (' Taken in black and white in the 1950s, only shades of grey, soft tones, fine film grain and slight fading,'
             ' the scene running right out to all four edges of the image.')
STUDIO = (' Taken on colour film in the 1980s against a mottled blue grey backdrop, soft flash, slightly faded colours,'
          ' fine grain and natural skin, the sitter and the backdrop filling the whole frame.')

POOLS = {
    'restaurant': [
        'The front of a plain two storey red brick building on a small town main street on a sunny afternoon, a family '
        'restaurant on the ground floor with a big plate glass front window and a glass door under one long red and white '
        'striped canvas awning, the flat brick wall above the awning plain with two white sash windows upstairs and a '
        'flat roof line against a blue sky, a few parked 1980s cars at the kerb, seen from across the street.' + PRINT,
        'A small brick town diner on the day it opened in the 1950s, a crowd of people in hats standing on the pavement '
        'outside under a striped awning, big plate glass windows behind them and plain bare brick above the awning, seen '
        'from across the street.' + OLD_PRINT,
        'A boys Little League baseball team of about twelve boys of ten to twelve, each a different height and build '
        'with ordinary tousled brown or blond hair, in plain white uniforms with green trim, in two rows on a grass '
        'field in front of a chain link fence, the back row standing and the front row kneeling, seen from across the'
        ' field so they are small.' + PRINT,
        'A plate of long thin flat golden cornmeal fried catfish fillets with round hushpuppies and smooth creamy pale'
        ' coleslaw, a glass of iced tea beside it on a red and white checked tablecloth, seen from above at an angle,'
        ' warm window light.' + PRINT,
        'A whole fresh baked pizza with pepperoni and green peppers on a round metal tray on a wooden counter, seen from '
        'a little above, the cheese fully melted, bubbling and spotted golden brown, the crust puffed and browned, warm '
        'light.' + PRINT,
        'A cook of about forty in a white apron and a flat low white paper garrison cap, a narrow folded '
        'paper strip worn flat along the top of his head like a soda jerk\'s cap, standing at a long flat '
        'rectangular steel griddle plate built into the counter of a small restaurant kitchen with bare '
        'white tiled walls, seen from the side and a few metres away, both his hands resting on the steel '
        'counter.' + PRINT,
        'A long diner counter with a row of chrome stools with red vinyl seats along it and wooden booths along the'
        ' window wall beyond, in a small family restaurant empty in the morning light, seen from one end of the'
        ' counter.' + PRINT,
        'A county fair in summer, a ferris wheel and white tents on a grass field under a blue sky, people walking between'
        ' the tents in the distance.' + PRINT,
    ],
    'bar': [
        'A men\'s softball team of five men standing side by side in one loose row on a dusty field at '
        'evening, seen from a few metres away from the knees up, in matching plain blue t-shirts, each man '
        'plainly different in age, face and build: a heavy grey haired man of about fifty five with a grey '
        'moustache, a lean man of forty with a dark beard, a tall bald man of about fifty, a stocky man of '
        'thirty five with a red brown moustache and a thin young man of twenty two with shaggy brown hair, '
        'some with arms folded, some with hands on hips.' + PRINT,
        'Four men in matching plain red bowling shirts standing side by side, seen close from the waist up so they fill '
        'the picture: a bald man of about fifty, a bearded man of forty, a tall thin man of thirty and a stout man of '
        'sixty with grey hair, the room behind them out of focus in a soft warm wood coloured blur.' + PRINT,
        'A wooden fishing dock on a Kentucky lake at sunset, a small aluminium boat tied up beside it, calm water.' + PRINT,
        'Three racehorses with jockeys in coloured silks galloping down the home stretch of a dirt race '
        'track, well spread out one behind another, seen side on from the white rail, each jockey crouched '
        'low and tucked up over his horse\'s neck with very short stirrups and his knees high, green trees '
        'and a pale summer sky beyond the far side of the track.' + PRINT,
        'A small country band of three men playing two acoustic guitars and an upright bass on a low wooden stage in a'
        ' dim bar, seen from across the room so they are small.' + PRINT,
        'The outside of a roadside bar at dusk, a low wooden building with a gravel car park and two plain '
        'old boxy pickup trucks parked side on to the viewer, and in its window a glowing red neon tube '
        'bent into the shape of a frothy beer mug.' + PRINT,
        'An empty bar at night, a pool table under one single hanging lamp with a green shade, seen from a few paces '
        'back, the lamp light on the green felt, a small cluster of balls far off at the end of the table, soft and '
        'out of focus, a cue lying along the near cushion, plain dark wood panelled walls in shadow.' + PRINT,
        'Hunters in plaid wool jackets and blaze orange caps standing a little apart at the edge of autumn woods, seen'
        ' from far off across a field so they are small figures.' + PRINT,
        'Two old men in overalls sitting at either end of one long wooden bench that runs the whole width of the porch '
        'of a plain clapboard country store in the 1950s, the bench resting on the porch boards under both of them, '
        'seen from the far side of the dirt road in front, the porch roof shading them.' + OLD_PRINT,
        'A largemouth bass lying on its side on the weathered grey planks of a wooden lake dock, seen side '
        'on from a little way back so the whole fish lies across the middle of the picture with its small '
        'head at one end and its jaw closed in a neat line, its green and bronze scales and dark side '
        'stripe, a coil of old rope beside it, the lake water behind.' + PRINT,
        'An empty red clay dirt track at a county fairground at night, seen from the wooden stands, the banked oval lit '
        'by floodlights on tall wooden poles, a haze of dust still hanging in the light over the turns, dark trees '
        'beyond.' + PRINT,
        'A river in autumn with a covered wooden bridge, orange and red trees on both banks, calm water.' + PRINT,
    ],
    'office': [
        'A group photograph of office workers in 1980s business clothes, men and women of different ages, some grey'
        ' haired, some heavy set, standing in rows on the front steps of a brick office building, seen from across the'
        ' street so they are small.' + PRINT,
        'A grey haired man of about sixty in a dark suit and tie sitting behind a plain rectangular wooden desk with his '
        'hands folded on its top, seen from in front from the waist up, a bookcase behind him.' + OLD_PRINT,
        'A ribbon cutting in front of a new single storey brick office building under a clear even blue sky, seen from a '
        'little way off: one wide red ribbon stretched straight and tight across the front of the entrance between two '
        'short white posts, a grey haired man of about sixty in a grey suit standing in the middle behind it and holding '
        'a big pair of scissors open on the ribbon in his right hand, his left hand at his side, and four other people a '
        'little apart either side of him, a stout bald man of fifty in a navy suit, a young woman of thirty with short '
        'dark hair in a red jacket, a tall thin man of forty with glasses and a woman of about sixty with grey curls, all '
        'clapping.' + PRINT,
        'A small town in summer seen from high on a wooded hill above it, a main street of red brick buildings, a white'
        ' church steeple, tree lined streets of houses, and green fields and woods all round.' + PRINT,
        'A brick factory building with a tall chimney and a car park full of 1970s cars, seen from across the road.' + PRINT,
        'A company softball team of eight men and women posed on a grass field on a summer evening, all in '
        'matching plain yellow t-shirts, their hands empty, a chain link backstop behind them, seen from a '
        'few metres away so the whole team fits in the picture: four standing in the back row, a tall man '
        'of about fifty with grey hair, a heavy set man of forty with a moustache, a young man of twenty '
        'five with a mullet and a woman of thirty with dark hair, and four kneeling on one knee in the '
        'front row, a thin man of sixty with glasses, a woman of about forty five with short permed brown '
        'hair, a plump woman of fifty with short grey curls and a young woman of twenty two with feathered '
        'blonde hair.' + PRINT,
        'A misty Kentucky horse farm at dawn, white plank fences across rolling green fields and a red barn in the distance.' + PRINT,
        'An old black wooden tobacco barn in autumn with its side boards propped open, rows of cut tobacco plants'
        ' hanging upside down inside drying brown, a cut field of short stubble beside it, seen from the'
        ' road.' + PRINT,
    ],
    'shop': [
        'A row of long handled garden rakes and shovels with straight wooden handles leaning against the '
        'brick wall beside the door of a small town American hardware store, a plain red wheelbarrow and a '
        'stack of galvanised buckets set out on the wide concrete sidewalk in front, the store a flat '
        'roofed red brick building with a plain green canvas awning and big plate glass windows dark and '
        'reflecting the street, seen from across the street on a sunny day.' + PRINT,
        'A small town main street in the 1950s seen at a slant down the length of the street, two storey shop fronts '
        'of plain painted brick running away along one side, their plain striped canvas awnings pulled down low over '
        'the windows, the brick above them bare, a few rounded old cars parked along the kerb, tall shade '
        'trees.' + OLD_PRINT,
        'A white delivery truck parked in front of a shop loading bay on a sunny day, seen from the side.' + PRINT,
        'A small town summer parade seen from high above the street, looking far up main street, the street running '
        'away from the '
        'viewer, a marching band in red uniforms a long way off down the middle of it, people lining both pavements, red'
        ' brick shop fronts with striped awnings and American stars and stripes flags hanging from the porches.' + PRINT,
        'A small 1950s country shop, a plain white clapboard building with a wooden porch and an empty bench out front, '
        'its two front windows dark and bare, seen from across a gravel road.' + OLD_PRINT,
        'An American man of about fifty with a full head of short dark hair just greying at the temples and a firm '
        'face, bareheaded, in a white shirt and a plain dark apron, standing proudly in the doorway of his small town '
        'American red brick storefront on opening day with his arms folded, the big plate glass windows either side of '
        'the door dark and bare, a wide concrete sidewalk in front, seen from across the sidewalk.' + PRINT,
        'A country road in summer lined with fences and trees, rolling green hills beyond, a blue sky with white clouds.' + PRINT,
        'A small town American red brick storefront at night, a single storey building with a flat roof and big plate '
        'glass windows glowing warm yellow with plain shelves of goods inside, the light spilling across a wide '
        'concrete sidewalk and an empty wet street, the brick wall above the windows bare.' + PRINT,
    ],
    'school': [
        'A class picture of about twenty fourth graders, girls and boys in equal numbers, all nine and ten '
        'years old, in three rows in front of a plain beige painted cinder block wall, seen from a few '
        'metres away, every child different, some with glasses, ponytails, bowl cuts and curls, all '
        'looking at the camera with their eyes open and smiling, their teacher, a grown woman of about '
        'forty in a cardigan, standing at the right end of the back row.' + PRINT,
        'Three high school basketball seniors, grown young men of eighteen with men\'s faces, square jaws and a shadow '
        'of stubble, one tall and thin with short dark hair, one broad with a blond crew cut, one with a short curly '
        'afro, in plain maroon t-shirts and grey sweatpants, standing side by side on a polished gym floor, the one in '
        'the middle holding a worn orange basketball, folded wooden bleachers filling the whole background, seen from '
        'a few metres away from the knees up.' + PRINT,
        'Head and shoulders of a school principal, a woman of about forty eight with short brown hair just going grey at '
        'the temples, a smooth face and a kind smile, in a navy jacket.' + STUDIO,
        'A high school graduating class of young men and women of eighteen in maroon caps and gowns seated in rows on a'
        ' lawn, every one of them seated, the rows packed close so only the gowned graduates show, the knees of the '
        'front row along the bottom of the picture, all facing the camera, seen from a little way off, tall trees '
        'behind.' + PRINT,
        'A single storey red brick elementary school with a flagpole and a yellow school bus out front, seen from across the'
        ' road.' + PRINT,
        'A high school marching band in blue and white uniforms marching in neat rows across plain green grass, seen '
        'from high above, each player small with a shining brass horn raised to the lips and pointing forward, only the'
        ' green grass, a few thin straight white chalk lines running across it and the band in view.' + PRINT,
        'A new single storey brick school building in the 1950s, about fifteen children standing in two '
        'rows on the front lawn facing the camera, two grown women teachers standing one at each end of '
        'the rows, seen from a few metres away so every child shows clearly from head to foot.' + OLD_PRINT,
        'A school library with wooden shelves of books and long tables, empty, afternoon light from tall windows.' + PRINT,
    ],
    'police': [
        'A police officer of about forty with a moustache, bareheaded, in a plain dark blue short sleeved '
        'uniform shirt, standing beside his plain white patrol car on a small town street, the car seen '
        'side on with its front end out of the picture, seen from a few metres away from the waist up, '
        'plain brick walls soft and blurred behind him.' + PRINT,
        'A police academy graduating class of about twenty officers in dark uniforms standing in rows outside a brick building,'
        ' seen from a distance.' + PRINT,
        'Seen from high above, looking straight down on the roofs of four big long plain white 1980s '
        'American sedans used as patrol cars, parked side by side in four separate parking spaces with a '
        'wide gap of grey tarmac and a white painted line between each car and the next, each roof with a '
        'red and blue light bar across it, summer afternoon shadows.' + PRINT,
        'Four police officers in 1950s uniforms with plain sleeves and peaked caps standing in a row '
        'beside an old rounded dark patrol car with white doors and one single round light on its roof, on '
        'a small town street, each a different man: a heavy set sergeant of about fifty five with a grey '
        'moustache, a tall thin man of thirty, a stocky man of forty with a broad face and a short young '
        'man of twenty five.' + OLD_PRINT,
    ],
    'stage': [
        'A theatre stage during a play, actors in period costume under warm stage lights, a painted backdrop of a garden,'
        ' seen from the back of the audience.' + PRINT,
        'The curtain call of a community theatre play, five grown men and women in old fashioned costume '
        'standing upright in one row across the stage an arm\'s length apart, holding hands, each with a '
        'gentle closed mouth smile, every face different: a grey haired man of sixty in a plain waistcoat, '
        'a woman of about forty with dark hair pinned up in a long plain dress, a young man of twenty five '
        'in an open collared shirt, a plump woman of fifty in a long plain dress and a tall thin man of '
        'forty in a plain jacket, seen straight on from the back of the hall over the heads of the '
        'audience.' + PRINT,
        'An old cinema front at night seen from across the street, a wide marquee lit with rows of bulbs round its plain '
        'blank white boards, the doors and the pavement below in warm light.' + PRINT,
        'A woman singer in a long sequinned gown with long sleeves that reaches to the floor, standing alone at a'
        ' microphone on a small stage under a single spotlight, seen from across the dark room, the dark room and the stage'
        ' running right out to all four edges of the image.' + PRINT,
        'An empty theatre auditorium with red velvet seats and a closed red curtain, warm house lights.' + PRINT,
        'A small brass band of four men in white jackets sitting on chairs inside an old round wooden '
        'bandstand in a park, playing under its roof, seen from across the lawn, each man different: two '
        'with trumpets raised to their lips, one with a big round tuba on his lap and one with a trombone '
        'held out in front of him.' + OLD_PRINT,
        'Three grown women dancers of about thirty, each face different, one blonde, one dark haired and one red '
        'haired, in matching long sleeved red costumes with long red trousers, standing a little apart across a stage '
        'with their arms raised high in a bow at the end of a number, seen straight on from the audience.' + PRINT,
        'A row of costumes hanging on a rail backstage under bare bulbs, a mirror and a dressing table beside it.' + PRINT,
    ],
    'church': [
        'Head and shoulders of a pastor, a man of about sixty with grey hair and a kind face, in a dark suit and '
        'tie.' + STUDIO,
        'About a dozen people of a small country church standing together in two rows on the wide front '
        'steps of a white clapboard church after the Sunday service, all facing the camera and smiling, '
        'seen from a few metres away so every face is clear: old women in flowered dresses, a grey haired '
        'man in a short sleeved shirt and tie, a young mother holding a baby, a father in a plain sport '
        'coat, and two children of about eight standing in front of the grown ups facing the camera.' + PRINT,
        'A church choir of men and women of mixed ages from twenty five to seventy, some grey haired, some '
        'balding, some with glasses, every face different, in maroon robes, standing in three rows behind '
        'a wooden altar rail, facing the camera and the empty wooden pews in front of them, singing from '
        'open books, a plain wooden cross on the bare wall behind them, seen from halfway down the aisle.' + PRINT,
        'A church picnic on a summer lawn, long tables with white cloths under oak trees, people sitting at them in the'
        ' distance.' + PRINT,
    ],
}
