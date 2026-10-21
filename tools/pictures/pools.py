"""Pictures that hang in place of the family photographs where a family album does not belong.

The game hangs the same "Painting" sprites of family photographs in houses, restaurants, bars, offices and shops. A house
shows the family pictures of catalogue.py; a room of another kind shows one of its own kind's pictures here instead, chosen
by the room the sprite hangs in (Decor.inRoom). POOLS is room kind -> prompts; each becomes the picture pool-<kind>-NN.
"""

PRINT = (' Shot on 35mm colour negative film in the 1980s, a slightly faded drugstore print with soft contrast and fine grain,'
         ' ordinary amateur framing, natural colours, Kentucky, United States.')
OLD_PRINT = ' A 1950s black and white photograph, silver gelatin print with soft grey tones and slight fading, Kentucky, United States.'

POOLS = {
    'restaurant': [
        'The front of a small brick family restaurant on a small town main street on a sunny afternoon, a striped awning over the'
        ' door and a few parked 1980s cars at the kerb, seen from across the street.' + PRINT,
        'The same small town diner on the day it opened in the 1950s, a crowd of people in hats standing on the pavement outside'
        ' under a striped awning, seen from across the street.' + OLD_PRINT,
        'A boys Little League baseball team of about twelve kids in matching green caps and white uniforms standing in two rows on'
        ' a grass field, a chain link fence behind them, seen from a few metres away.' + PRINT,
        'A plate of fried catfish, hushpuppies and coleslaw with a glass of iced tea on a red and white checked tablecloth, seen'
        ' from above at an angle, warm window light.' + PRINT,
        'A whole pizza with pepperoni and green peppers on a metal tray on a wooden counter, steam rising, warm light.' + PRINT,
        'A cook in a white apron and paper hat standing at a flat griddle in a small restaurant kitchen, seen from the side and'
        ' a few metres away, his hands on the counter.' + PRINT,
        'The dining room of a small family restaurant, wooden booths with red vinyl seats and a long counter with stools,'
        ' empty in the morning light, seen from the doorway.' + PRINT,
        'A county fair in summer, a ferris wheel and white tents on a grass field under a blue sky, people walking between'
        ' the tents in the distance.' + PRINT,
    ],
    'bar': [
        'A men\'s softball team of about ten in matching blue t-shirts and caps standing in two rows on a dusty field at evening,'
        ' seen from a few metres away.' + PRINT,
        'A bowling league team of four men in matching red bowling shirts standing side by side in a 1980s bowling alley, lanes'
        ' and pins behind them.' + PRINT,
        'A wooden fishing dock on a Kentucky lake at sunset, a small aluminium boat tied up beside it, calm water.' + PRINT,
        'Racehorses with jockeys in coloured silks galloping down the home stretch of a dirt race track, grandstand behind,'
        ' seen from the rail.' + PRINT,
        'A small country band of three men playing guitar, fiddle and upright bass on a low wooden stage in a dim bar,'
        ' seen from across the room.' + PRINT,
        'The outside of a roadside bar at dusk, a low wooden building with a gravel car park and pickup trucks, a neon light in'
        ' the window glowing red.' + PRINT,
        'A pool table with racked balls under a hanging green lamp in an empty bar, seen from table height.' + PRINT,
        'A group of five men in hunting jackets and orange caps standing at the edge of autumn woods, seen from a distance.' + PRINT,
        'Old men sitting on a wooden bench in front of a country general store in the 1950s, seen from across a dirt road.' + OLD_PRINT,
        'A large largemouth bass lying on wet planks beside a tackle box and a fishing rod on a boat dock.' + PRINT,
        'A stock car race on a small dirt oval at night under floodlights, cars sliding sideways through the turn, seen from'
        ' the stands.' + PRINT,
        'A river in autumn with a covered wooden bridge, orange and red trees on both banks, calm water.' + PRINT,
    ],
    'office': [
        'A group photograph of about fifteen office workers in 1980s business clothes standing in three rows on the front steps'
        ' of a brick office building, seen from a distance.' + PRINT,
        'A formal portrait of a grey haired company founder in a dark suit sitting at a wooden desk.' + OLD_PRINT,
        'A ribbon cutting in front of a new single storey brick office building, a few people in suits standing by a red'
        ' ribbon, seen from a distance.' + PRINT,
        'An aerial photograph of a small Kentucky town, a main street, a water tower, a church steeple and green fields around'
        ' it, taken from a small plane.' + PRINT,
        'A brick factory building with a tall chimney and a car park full of 1970s cars, seen from across the road.' + PRINT,
        'A company softball team in matching yellow t-shirts kneeling and standing in two rows on a grass field.' + PRINT,
        'A misty Kentucky horse farm at dawn, white plank fences across rolling green fields and a red barn in the distance.' + PRINT,
        'A tobacco barn in autumn, an old black wooden barn beside a field of cut tobacco, seen from the road.' + PRINT,
    ],
    'shop': [
        'The front of a small town hardware store with a plain green awning and no sign, its windows full of tools and paint cans, seen'
        ' from across the street on a sunny day.' + PRINT,
        'A small town main street in the 1950s, shop fronts with awnings and a few old cars parked along the kerb.' + OLD_PRINT,
        'A white delivery truck parked in front of a shop loading bay on a sunny day, seen from the side.' + PRINT,
        'A small town Fourth of July parade, a marching band in red uniforms coming down main street, flags on the shop fronts,'
        ' seen from the pavement.' + PRINT,
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
        'A high school basketball team of twelve boys in white and maroon uniforms posed in two rows on a gym floor, their coach'
        ' beside them.' + PRINT,
        'A formal portrait of a school principal, a middle aged woman with short grey hair in a navy jacket, plain blue grey'
        ' backdrop.' + PRINT,
        'A high school graduating class in maroon caps and gowns standing in rows on a football field bleacher, seen from a'
        ' distance.' + PRINT,
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
        'A row of four white and blue 1980s police cruisers parked in front of a small town brick police station.' + PRINT,
        'A small town police department in the 1950s, a few officers in uniform standing beside an old patrol car.' + OLD_PRINT,
    ],
    'stage': [
        'A theatre stage during a play, actors in period costume under warm stage lights, a painted backdrop of a garden,'
        ' seen from the back of the audience.' + PRINT,
        'The cast of a community theatre play, about fifteen people in costume standing in a row on stage taking a bow.' + PRINT,
        'An old cinema front at night, its marquee lit with rows of bulbs, seen from across the street.' + PRINT,
        'A woman singer in a long sequinned gown with long sleeves that reaches to the floor, standing alone at a'
        ' microphone on a small stage under a single spotlight, seen from across the dark room. The photograph fills'
        ' the whole frame edge to edge.' + PRINT,
        'An empty theatre auditorium with red velvet seats and a closed red curtain, warm house lights.' + PRINT,
        'A small brass band of six musicians in white jackets playing in a bandstand in a park.' + OLD_PRINT,
        'Dancers in matching costumes in a line on stage in mid kick, seen from the audience.' + PRINT,
        'A row of costumes hanging on a rail backstage under bare bulbs, a mirror and a dressing table beside it.' + PRINT,
    ],
    'church': [
        'A formal portrait of a pastor, a grey haired man in a dark suit and tie, plain blue grey backdrop.' + PRINT,
        'A congregation of about forty people in their Sunday best standing on the steps of a white clapboard church, seen from'
        ' across the lawn.' + PRINT,
        'A church choir in maroon robes standing in three rows in front of a wooden altar, seen from the pews.' + PRINT,
        'A church picnic on a summer lawn, long tables with white cloths under oak trees, people sitting at them in the'
        ' distance.' + PRINT,
    ],
}
