"""Pictures that hang in place of the family photographs where a family album does not belong.

The game hangs the same "Painting" sprites of family photographs in houses, restaurants, bars, offices and shops. A house
shows the family pictures of catalogue.py; a room of another kind shows one of its own kind's pictures here instead, chosen
by the room the sprite hangs in (Decor.inRoom). POOLS is room kind -> prompts; each becomes the picture pool-<kind>-NN.
"""

PRINT = (' A snapshot on 35mm colour negative film from the 1980s, faded colours with soft contrast and fine grain,'
         ' ordinary amateur composition, natural colours, Kentucky, United States. The picture fills the whole image'
         ' edge to edge.')
OLD_PRINT = (' A 1950s black and white photograph, soft grey tones, fine grain and slight fading, Kentucky, United'
             ' States. The picture fills the whole image edge to edge.')

POOLS = {
    'restaurant': [
        'The front of a small brick family restaurant on a small town main street on a sunny afternoon, a striped awning over the'
        ' door and a few parked 1980s cars at the kerb, seen from across the street.' + PRINT,
        'The same small town diner on the day it opened in the 1950s, a crowd of people in hats standing on the pavement outside'
        ' under a striped awning, seen from across the street.' + OLD_PRINT,
        'A boys Little League baseball team, bareheaded, in plain white uniforms with green trim, standing in two rows'
        ' on a grass field in front of a chain link fence, seen from across the field so they are small.' + PRINT,
        'A plate of long thin flat golden cornmeal fried catfish fillets with round hushpuppies and smooth creamy pale'
        ' coleslaw, a glass of iced tea beside it on a red and white checked tablecloth, seen from above at an angle,'
        ' warm window light.' + PRINT,
        'A whole pizza with pepperoni and green peppers on a metal tray on a wooden counter, steam rising, warm light.' + PRINT,
        'A cook in a white apron and paper hat standing at a flat griddle in a small restaurant kitchen, seen from the side and'
        ' a few metres away, his hands on the counter.' + PRINT,
        'A long diner counter with a row of chrome stools with red vinyl seats along it and wooden booths along the'
        ' window wall beyond, in a small family restaurant empty in the morning light, seen from one end of the'
        ' counter.' + PRINT,
        'A county fair in summer, a ferris wheel and white tents on a grass field under a blue sky, people walking between'
        ' the tents in the distance.' + PRINT,
    ],
    'bar': [
        'A men\'s softball team of men of different ages and builds, some bearded, in matching plain blue t-shirts and'
        ' bareheaded, in two rows on a dusty field at evening, the front row kneeling and the back row standing, seen'
        ' from a few metres away.' + PRINT,
        'Four men of different builds and ages, one bald, one with a beard, one tall and thin, one stout, in matching'
        ' plain red bowling shirts standing side by side, seen from the waist up, in a 1980s bowling alley with wood'
        ' panelled walls, the lanes behind them.' + PRINT,
        'A wooden fishing dock on a Kentucky lake at sunset, a small aluminium boat tied up beside it, calm water.' + PRINT,
        'Racehorses with jockeys in coloured silks galloping down the home stretch of a dirt race track, grandstand behind,'
        ' seen from the rail.' + PRINT,
        'A small country band of three men playing two acoustic guitars and an upright bass on a low wooden stage in a'
        ' dim bar, seen from across the room so they are small.' + PRINT,
        'The outside of a roadside bar at dusk, a low wooden building with a gravel car park and pickup trucks, a neon light in'
        ' the window glowing red.' + PRINT,
        'A pool table with a triangle of racked balls under a single hanging green lamp in an empty bar, seen from'
        ' table height.' + PRINT,
        'Hunters in plaid wool jackets and blaze orange caps standing a little apart at the edge of autumn woods, seen'
        ' from far off across a field so they are small figures.' + PRINT,
        'Old men sitting on a wooden bench on the porch of a plain clapboard country store in the 1950s, seen from'
        ' across a dirt road, the porch roof shading them.' + OLD_PRINT,
        'A large largemouth bass lying on the weathered grey planks of a wooden lake dock beside a fishing rod, the'
        ' lake water behind.' + PRINT,
        'A stock car race on a small dirt oval at night under floodlights, cars sliding sideways through the turn, seen from'
        ' the stands.' + PRINT,
        'A river in autumn with a covered wooden bridge, orange and red trees on both banks, calm water.' + PRINT,
    ],
    'office': [
        'A group photograph of office workers in 1980s business clothes, men and women of different ages, some grey'
        ' haired, some heavy set, standing in rows on the front steps of a brick office building, seen from across the'
        ' street so they are small.' + PRINT,
        'A formal portrait of a grey haired company founder in a dark suit sitting at a wooden desk.' + OLD_PRINT,
        'A ribbon cutting in front of a new single storey brick office building, a few people in suits standing by a red'
        ' ribbon, seen from a distance.' + PRINT,
        'An aerial photograph of a small Kentucky town, a main street, a water tower, a church steeple and green fields around'
        ' it, taken from a small plane.' + PRINT,
        'A brick factory building with a tall chimney and a car park full of 1970s cars, seen from across the road.' + PRINT,
        'A company softball team of men and women of different ages and builds in matching plain yellow t-shirts,'
        ' kneeling and standing in two rows on a grass field, seen from across the field so they are small.' + PRINT,
        'A misty Kentucky horse farm at dawn, white plank fences across rolling green fields and a red barn in the distance.' + PRINT,
        'An old black wooden tobacco barn in autumn with its side boards propped open, rows of cut tobacco plants'
        ' hanging upside down inside drying brown, a cut field of short stubble beside it, seen from the'
        ' road.' + PRINT,
    ],
    'shop': [
        'The front of a small town hardware store with a plain green awning and no sign, its windows full of tools and paint cans, seen'
        ' from across the street on a sunny day.' + PRINT,
        'A small town main street in the 1950s, shop fronts with awnings and a few old cars parked along the kerb.' + OLD_PRINT,
        'A white delivery truck parked in front of a shop loading bay on a sunny day, seen from the side.' + PRINT,
        'A small town Fourth of July parade seen from far down main street, a marching band in red uniforms small in'
        ' the distance with brass horns glinting, red brick shop fronts with striped awnings and flags hanging from'
        ' the porches.' + PRINT,
        'A small 1950s country general store, a plain white clapboard building with a wooden porch and an empty bench'
        ' out front, seen from across a gravel road, its front windows dark.' + OLD_PRINT,
        'A man standing proudly in the doorway of his small shop on opening day, arms folded, seen from across the pavement.' + PRINT,
        'A country road in summer lined with fences and trees, rolling green hills beyond, a blue sky with white clouds.' + PRINT,
        'The shop\'s storefront at night, its lit windows glowing onto an empty wet street.' + PRINT,
    ],
    'school': [
        'A class photograph of about twenty children of nine or ten years old in three rows in a school gymnasium,'
        ' seen from a few metres away, their teacher, a grown woman in a cardigan, standing apart at the far right end'
        ' of the back row, the children a little blurred, some squinting, fidgeting or looking away.' + PRINT,
        'A high school basketball team in plain maroon t-shirts posed in two rows on a gym floor, their coach beside'
        ' them, seen from the far end of the gym so they are small.' + PRINT,
        'A formal portrait of a school principal, a middle aged woman with short grey hair in a navy jacket, plain blue grey'
        ' backdrop.' + PRINT,
        'A high school graduating class in maroon caps and gowns seated in rows up the steps of the wooden bleachers'
        ' beside a football field, seen from far across the field.' + PRINT,
        'A single storey red brick elementary school with a flagpole and a yellow school bus out front, seen from across the'
        ' road.' + PRINT,
        'A high school marching band in blue and white uniforms playing on a football field, seen from the stands.' + PRINT,
        'The same school building when it was new, children standing in rows on the front lawn.' + OLD_PRINT,
        'A school library with wooden shelves of books and long tables, empty, afternoon light from tall windows.' + PRINT,
    ],
    'police': [
        'A police officer in a plain dark blue uniform and peaked cap standing beside his white patrol car on a small'
        ' town street, seen from across the street so he is small and his badge and cap are only dark shapes. The'
        ' photograph fills the whole frame edge to edge.' + PRINT,
        'A police academy graduating class of about twenty officers in dark uniforms standing in rows outside a brick building,'
        ' seen from a distance.' + PRINT,
        'A row of white and blue 1980s police cruisers parked side by side in front of a small town brick building,'
        ' seen from far across the street, the cars small.' + PRINT,
        'A small town police department in the 1950s, a few officers in uniform standing beside an old patrol car.' + OLD_PRINT,
    ],
    'stage': [
        'A theatre stage during a play, actors in period costume under warm stage lights, a painted backdrop of a garden,'
        ' seen from the back of the audience.' + PRINT,
        'The cast of a community theatre play in costume, a long row of people of all ages on stage, all bending'
        ' forward at the waist in a deep bow with their heads down, seen from the back of the hall.' + PRINT,
        'An old cinema front at night, its marquee lit with rows of bulbs, seen from across the street.' + PRINT,
        'A woman singer in a long sequinned gown with long sleeves that reaches to the floor, standing alone at a'
        ' microphone on a small stage under a single spotlight, seen from across the dark room. The photograph fills'
        ' the whole frame edge to edge.' + PRINT,
        'An empty theatre auditorium with red velvet seats and a closed red curtain, warm house lights.' + PRINT,
        'A small brass band in white jackets playing in an old bandstand in a park, seen from across the'
        ' lawn.' + OLD_PRINT,
        'Dancers in matching costumes in a line on stage in mid kick, seen from the audience.' + PRINT,
        'A row of costumes hanging on a rail backstage under bare bulbs, a mirror and a dressing table beside it.' + PRINT,
    ],
    'church': [
        'A formal portrait of a pastor, a grey haired man in a dark suit and tie, plain blue grey backdrop.' + PRINT,
        'A congregation of about forty people in their Sunday best standing on the steps of a white clapboard church, seen from'
        ' across the lawn.' + PRINT,
        'A church choir in maroon robes standing in three rows in front of a wooden altar, seen from the back of the'
        ' church over empty wooden pews.' + PRINT,
        'A church picnic on a summer lawn, long tables with white cloths under oak trees, people sitting at them in the'
        ' distance.' + PRINT,
    ],
}
