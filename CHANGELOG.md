# Changelog

## [0.0.0] - 2026-10-07

### Added

#### First person and head tracking

- First person view. `Delete` switches between the isometric view and first person, with the camera flying between them. Each save starts in the isometric view.
- Head tracking from any OpenTrack compatible tracker on UDP port `4242`: turning your head turns the view and leaning moves it. The mouse or controller keeps the aim.
- `Page Up` cycles rotation and position, rotation only, and position only.
- `Page Down` switches head turns between the horizon and the view's own up. In a vehicle the view always turns about its own up.
- Leaning stops at walls.
- Your character's head turns with yours, up to 80 degrees each way, in both views. Mirrors show it, and in `High` windows do too.
- Walking and running don't bob the view. Crouching and sitting lower it smoothly.
- Knocked down, lying, getting up or dying, the view follows your character's head.
- `FieldOfView` sets the field of view: 65 by default, 40 to 110.
- A carried flashlight points where your head points (`LightMultiplier`). Its beam lights everything in front of it and casts shadows.
- Head tracking runs while the game is unpaused and your character is alive. It follows one tracker at a time.

#### Controls

- Left mouse attacks, right mouse aims, `Left Shift` sprints, `Left Alt` jogs, `Left Ctrl` crouches. Your own key bindings are not changed.
- The pointer comes out with the inventory open, with any game window or menu open, or with a click of the middle mouse button until you click it again or press `Esc`. Head tracking carries on.
- `E` uses the door, window, curtain, light switch, container or item in the middle of the view. What it will use is outlined.
- Right click with the pointer out opens the game's own menu for what the pointer is on, the same as in the isometric view.
- A single quick click is enough for a melee attack.
- Controller: the aim stick turns the view, right trigger attacks, left trigger raises the sights, double-tap Back switches view.
- If no plugged-in gamepad is ticked in the game's Controller options, every gamepad the game recognises plays. A 3Dconnexion SpaceMouse is never taken as a controller.
- Every mod key can be rebound in `[Hotkeys]`. Mod keys do nothing in the main menu or while typing.

#### Weapons and combat

- The reticle sits where the shot will land. With a weapon held and not aiming, the mod draws a small crosshair there.
- Three aim modes on `Insert`: sights locked (default, the weapon follows your lean), free look (your head moves around the weapon) and stock sights (head turn and lean ease out while aiming, tilt stays).
- Aiming a firearm zooms the view. Head movement is scaled so the zoom doesn't magnify it.
- Held weapons are drawn like a modern shooter: low right at the hip, raised to the sights, swaying, with recoil based on each weapon's recoil stat and a muzzle flash (`MuzzleFlash`).
- The mod's own arms and hands hold each gun by its real grip, read from the gun's model, so guns from other mods are held right too. Handguns are held two-handed when your left hand is free. `OwnArms=false` uses the game's arms.
- Reload and action animations: pump shotguns rack, pistols cycle their slide, lock back when empty and swap magazines, revolvers turn the cylinder and load one round at a time, double barrels break open and throw out the spent shells.
- A sawn-off has no stock, so it kicks in your hands when aiming too.
- Red dot sights, lasers and gun lights are drawn on the mod's gun models.
- `Medium` and `High`: x2, x4 and x8 scopes are real scopes with magnified view. Moving your eye off the scope's axis darkens the picture.
- Melee swings, overhead blows, stabs and shoves follow the game's timing, with a separate motion for each weapon type. A hit pauses the weapon at contact for a moment.
- Bare-handed shoves and stomps use your own hands.
- Eating, drinking, reading, bandaging, hammering, sawing, searching and other timed actions move your hands in view.
- From a vehicle seat you can raise a firearm and shoot out of the vehicle, window shut or not. The shot breaks the window, windscreen or rear screen it goes through. Where players can fight, the window has to be open or broken first, as in the game.
- The driver keeps steering, accelerating and braking with a rifle or shotgun raised, driving with keyboard and mouse. Reloading still takes the controls, and on a controller, and where players can fight, raising one does too, as in the game.
- Shots hit the body part you aim at.
- Single player: shot legs make zombies shamble, then crawl. Shot arms sag, then hang.
- Single player: limbs come off at the hand, foot, elbow, knee, shoulder or hip. Most burst in blood and gore, some fly off and land on the ground. Shotgun headshots sometimes take the head off or burst it. Zombies missing limbs leave blood trails.
- Shot zombies ragdoll as they're hit and fall along the line of the shot. Ones that stay standing flinch where they were hit. Needs the game's Use Physics Hit Reactions option, which servers turn off.
- `Medium` and `High`: bullet impacts throw dust, chips, splinters, sparks or blood depending on the surface, and leave holes. The last 400 holes stay.
- Single player: rounds go through plasterboard, wooden walls, doors, gates and glass, losing damage on each. Rifles go furthest, shotgun pellets least. Brick, concrete, stone, metal and barricades stop them.
- Single player: shooting a vehicle's head or tail lamp, a lamp or a street lamp breaks it.
- `Medium` and `High`: blood on floors and walls has smooth edges, looks wet when fresh and dries brown.

#### Vehicles

- First person from any seat. The view tilts and rolls with the vehicle on its springs and in crashes.
- Full interiors for every vehicle in the game, with working speedometer (mph), rev counter and fuel gauge, and hands on the wheel (`OwnCabins`). The game's dashboard panel is hidden in first person.
- Working rear-view and side mirrors.
- Rain and snow on the windows: drops land, run down and are cleared by the wipers. Snow melts on the windscreen and piles up where the wipers park.
- Snow settles on vehicle roofs, bonnets and windscreens. Engine heat and wipers clear the windscreen.
- Headlights light the road and cast shadows. Wet roads reflect them.
- Taking the driver's seat switches off a light you're holding.
- Vehicles tilt with the hills under them.
- Single player: hills slow vehicles uphill and speed them up downhill, and vehicles take off over crests at speed.
- Single player: a vehicle left on its roof catches fire after 4 seconds, throws out anyone inside, and burns out 6 seconds later, leaving the game's burnt wreck.
- Single player: vehicles smash through fences and street lamp posts, losing a little speed and taking some damage.
- Single player: bushes and plants don't slow vehicles, and grass and dirt don't cut engine power.
- Single player: zombies and bodies push back on a vehicle a quarter as hard as in the base game.

#### World

- `Medium` and `High` rebuild the world in 3D: buildings with thick walls, roofs, eaves, gutters, downpipes and chimneys; doors, windows and curtains; furniture, appliances, shop fittings, signs, street lamps, traffic lights, power lines, pylons, playgrounds, sports equipment, instruments and more.
- New textures for floors, tiles, carpets, wallpaper, brick, wood, roofs and roads. Road markings are drawn as clean paint.
- Pavements and car parks lie level, with kerbs.
- A TV that is on shows a moving picture: test bars with no signal, otherwise the show on its channel or tape.
- Pictures, banners and bunting on walls.
- Sharper skins for zombies, survivors and clothing. Animals get new detailed skins (`OwnAnimals`).
- Hills in the countryside, flat in towns. `HillHeight` sets their height from 0 to 2 (default 1.5).
- Land and buildings are drawn up to 768 tiles away, past the area the game has loaded.
- Distant trees are drawn where the game's map places them (`FarTreeDistance`, up to 768 tiles).
- Zombies are visible as far as your character can see in the current weather and light, and hidden only by what's between them and you.
- Rooms are built before you see into them, so doors open onto furnished rooms. Rooms you can't see into aren't drawn.
- Each `[Content]` row switches one kind of mod content back to the game's own.

#### Plants and seasons

- Trees have real branches and leafy crowns. Pines, hemlocks and hollies have needles. Dead trees have bare branches.
- Bushes, ferns and grass are 3D. Grass moves in the wind and parts around your feet.
- Field crops are drawn stalk by stalk within 30 tiles.
- Ivy grows up tree trunks and across the ground around them.
- Fallen sticks lie on the ground.
- Broad-leaved trees change colour in autumn, thin out and go bare in winter. Distant trees follow the season too.
- In autumn, leaves fall from the trees and blow in the wind. Fallen leaves pile up on the ground, fresh in autumn and matted in winter.
- Plants sway with the game's wind.

#### Weather and light

- Rain and snow fall as streaks and splash on the ground. None falls under a roof.
- Fog, rain and snow limit how far you see, matching the game. Fog glows in torch and headlight beams in `High`.
- Lightning bolts strike during storms, lighting up the sky, clouds and ground.
- Settled snow has drifts, banks against kerbs and sparkles in the sun. It keeps your footprints and vehicle tyre tracks.
- Snow settles on trees, bushes, grass and ivy, and dusts people and zombies out in it.
- Clouds, cloud shadows, the moon, low evening sun and dark nights.
- Shadows from the sun, flashlights, headlights and fires. Lit lamps glow.
- Sunbeams through windows, with dust floating in the air.
- Water flows and reflects.
- Mirrors on walls, dressers and bathroom cabinets show the room, the people in it and you, in every graphics mode with `Reflections` on. The glass has a faint tint, smudges and a worn silver edge.
- `Medium` and `High`: burning houses, Molotovs and campfires have flames, smoke and flickering firelight. Fireplaces and stoves burn inside. `OwnFire` turns the mod's fire off.
- The picture adjusts to the brightness of what you're looking at, like an eye.
- `Medium` and `High`: every surface reflects the sky, the room or the ground by how rough it is, so metal looks like metal. Skin and clothes take the light the same way.
- `Medium` and `High`: brick, block and stone walls have depth up close. The mortar lies back between them and the sun's shade falls into the joints.

#### Graphics settings

- `F9` cycles `Low`, `Medium`, `High`, `Custom` and `Automatic`, fading between them. First person and the isometric view can be set separately in the game's options.
- `Automatic` lowers the quality when the frame rate drops below 45 and raises it when it holds above 57.
- `Low` is the game's own look. `Medium` and `High` also redraw the isometric view.
- `F8` cycles the picture styles `Vivid`, `Natural` and `Bleak` (default).
- `ZombiesAndPropsDrawDistance` (16 to 80 tiles) and `Reflections` settings.
- `GrassDensity` setting (10 to 100 percent): fewer grass plants for a faster picture. Every mode grows all of it.

#### Sound

- In first person, sound comes from where things are relative to the way you face.
- Walls, doors and floors muffle sound. Rooms echo by their size and what they're made of.

#### Talking mouths

- Optional, off by default: your character's mouth moves with what you say into your microphone, in first person and in the isometric view, opening, spreading and rounding with the sounds you make, with teeth and a tongue behind the lips. Each player at the screen picks their own microphone. Online, other players' mouths move with the game's voice chat, and with their own microphone where they have this mod with talking mouths on. Nothing is recorded; online, only how open your mouth is and the shape of your lips are sent.

#### Multiplayer and split screen

- Works on servers. Only you need the mod.
- Online, players who have the mod see each other's heads turn with their trackers. It goes through the game's own player updates, so the server needs no mod. Players without the mod see your character's head as the game poses it.
- Names and speech appear over other players' heads in first person.
- On PvP servers: leaning doesn't move your shot, moving sideways or backwards uses the game's aiming stance, hills are fixed at height 1, other players are shown only as the game shows them, a shot at a player hits the part the game rolls, and nothing is hidden by draw distance.
- Wounds, limb loss, bullet penetration, shooting out lamps and vehicle physics are single player only.
- Up to four players at one screen, each with first person and their own head tracker on ports `4243`, `4244` and `4245`. Double-tap `Delete` for player 2, hold it for everyone.
- `F7` switches two-player split screen between side by side and top and bottom.

#### Setup

- `install.cmd` installs the mod with no mod loader. Lopari can install it too.
- Settings are in `CameraUnlock.ini` in the game folder. Most changes apply without a restart.
- The main settings are also in the game's options under `Mods` > `Head Tracking`.
- The game window opens centred on its monitor.
- If a game update breaks something the mod uses, the mod stays off and the log says what changed.
