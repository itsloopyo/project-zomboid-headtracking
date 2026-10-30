# Changelog

## [Unreleased]

### Added

- Every part of the mod can be switched off, on the mod's page of the options screen or in `CameraUnlock.ini`, and each is on until you untick it: first person as a whole (`FirstPersonEnabled`), the torch following your head, hearing from the eye, lightning bolts, shot impacts, far vehicles and zombies, quick ragdolls, hit flinches, the head-led view when down, items as models, finer skins, leaving out the game's screen grey and name tags at heads.
- `[Rules]` has `ShotsFromVehicleSeats`, `DriveWhileAiming`, `HillsMoveVehicles` and `HandLightOutAtTheWheel`, and `HiddenWallDecorSaved`, which decides apart from `HiddenWallDecor` whether the game's mirrors, clocks and coat racks are added to the save.
- A `[Fixes]` section, under Game fixes on the options page: `BurntBuildingFreezeFix`, `CentreTheWindow` and `ControllerChoiceFix`.
- The `[Optimizations]` section has `CachedGameLookups`, `SkipCoveredGameDrawing`, `SkinsComposedWhileWaiting`, `ChunkChangeCounts`, `UnseenRoomZombiesUnmodelled`, `HoldChunkMapCentre`, `GraphicsMemorySteps`, `PrepareWhileLoading` and `SpotBySight`, beside `SpreadLightUpdates`.
- Turning a setting off stops the mod patching that part of the game from the next start, so another Java mod that changes the same code finds the game's own. `ZombiesSeenAtOnce` and `VehiclesSmashThrough` do the same. A setting changed while the game runs that is switched back on puts its patches in at once, with no restart.
- Quick settings at the top of the mod's options page: a Head tracking tick box, which turns head tracking on and off while you play, and five lists, Everything, First person (`On`, `In vehicles only` or `Off`), Enhanced graphics, Gameplay tweaks and Optimizations. Each list switches a whole class of settings at once and shows which line your settings match, or `Mixed`.
- Local smoothing, remote smoothing and how far the flashlight follows your head (`LightMultiplier`) are on the options page.
- A Grass distance slider under Custom graphics (`[Graphics] GrassDistance`): how far from you lawns are grown blade by blade in first person, from 8 to 48 tiles. Medium and High grow them to 16 tiles. Long grass is not affected.
- A shotgun blast in first person whose pellets land close together on a wooden door or a wooden fence, tall or low, the map's or one you built, blows a ragged hole through it, which you can see and shoot through, and stray pellets leave marks on a door. The holes stay in your save. Offline only, and `[Rules] BlastHoles=false` turns it off.
- A round you fire in first person that goes through a door, a wall or a wooden fence leaves a small hole where it went in and a wider, splintered one where it came out, which you can see through, and a wall you have shot through lets you see into the room behind it. The holes stay in your save. Offline only, and `[Rules] BulletHoles=false` turns it off.
- Head tracking turns Project Viewpoint's camera when that mod runs alongside this one: yaw, pitch and roll, not the lean.
- `[Graphics] Antialiasing` (`Off`, `Low`, `Medium` or `High`) smooths jagged edges in first person from each frame alone, with no blending of frames. `Low` is the quick edge blend the mod has always used. `Medium` and `High` also follow each edge to its ends, so long shallow edges such as roof lines and wall tops come out as an even slope instead of steps, and `High` finds fainter edges too. Every graphics mode uses `Low`, which with the frame blending of Medium and High gives a slightly soft, steady picture; `Medium` and `High` are for a sharper picture, mostly with `FrameBlending=false`.
- Two more picture styles, `Warm` (golden light and amber shade) and `Cold` (steel blue shade and pale light), after `Bleak` on `F8` and in `Style`. Changing style fades from one to the next over most of a second instead of cutting.

### Changed

- Frame blending shimmers much less while your head moves: grass, leaves and fine edges hold still instead of crawling, for a slightly softer picture.
- The mod's options page is sorted into Quick settings, Head tracking, First person, Graphics, Custom graphics, Mod content, Gameplay tweaks, Game fixes, Optimizations, Split screen, each extra player, Voice and Keys, and every setting has a plain name and a description of what it does on and off. Mod content ticks a row for the mod's own and unticks it for the game's own. Keys lists every key the mod listens for, and where to change it.
- Nothing on the options page takes effect until you apply the options. Then everything you changed takes effect together and is saved to `CameraUnlock.ini`. Leaving without applying leaves every setting as it was.
- Choosing a first person graphics mode on the options page shows that mode's settings under Custom graphics straight away, and changing one of those switches the mode to Custom straight away, before you apply.
- The comments in `CameraUnlock.ini` say what each setting does, on and off, in plain words.
- A save that loads in first person shows the same hints as one that loads in the isometric view, once first person is ready: which key switches views, then which key changes the graphics quality.
- In the isometric view the mod's lawn grass reaches the edge of the picture, thinner and broader-bladed past about ten tiles, instead of ending in a ring 15 tiles round your character.
- In first person, the chunks a frame has to build again, and the grass and plants it grows again as you move, are built on up to four threads of their own, each starting as the view comes to it while the view goes on with the rest of the frame, instead of one after another on the game's own thread, and each shows a frame later. `[Optimizations] BuildOnWorkerThreads=false` builds them on the game's thread as before.
- Arriving somewhere in first person, a chunk built before some of its sprites or pictures had been read is built again once those have been, instead of every time any sprite anywhere is read: about a third fewer chunk builds over four arrivals in Louisville, half in the busiest, and the picture once everything is read is the same. `[Optimizations] RebuildForOwnSprites=false` builds it again as before.
- Shorter hitches as buildings load round you: dressing a room's hidden walls no longer reads the whole building again for every chunk of it that loads, only the chunks the game has changed since, and a mirror or clock hung in a room no longer has every floor of that chunk read again. Arriving at three spots in Louisville, that work fell from 0.3-1.1 s to 0.08-0.31 s a visit and its longest frame from 95-222 ms to 15-40 ms. What each room is given is the same. `[Optimizations] HiddenWallReadsKept=false` reads the whole building each time as before.
- Dressing the hidden walls of a tall building's rooms works out once what the building's room names say, what kind of place each room is and whether it is a home, and what each picture is of, instead of again for every room. Arriving at three spots in Louisville, all hidden-wall dressing took a fifth to a half less time than before this and the change above it. What each room is given is the same. `[Optimizations] BuildingFactsKept=false` works them out each time as before.
- Driving through open country runs faster in first person, with about a fifth more frames a second on the road out of Muldraugh: working out the ground's heights as the map loads takes far less time.
- Containers in places the save has not seen yet are filled with less work: an item the game looks up by name is found once and handed back until its scripts change. What each container gets is unchanged.
- Fewer stutters when new people come into view: their clothes are put together while the frame waits for the game, and no longer hold up the frame they are drawn in. They look the same as before.
- On a server where players cannot hurt each other, your character keeps facing where you look when walking sideways or backwards (`FacesTheView`), and zombies show at once in the isometric view (`ZombiesSeenAtOnce`), as they do offline.
- On a server where players can hurt each other, walking sideways or backwards in first person keeps your character facing where you look, in the game's aiming stance at that stance's own pace, whatever `FacesTheView` says. Other players see it face the way you are looking, not its back. Jogging or sprinting that way, it walks.
- On a server where players cannot hurt each other, first person shots go on through thin walls and doors (`ShotsThroughWalls`) and past a tree's trunk (`ShotsPastTrees`), as they do offline.
- On any server, the south and east walls of rooms carry the mod's pictures in first person, as they do offline.
- In first person, light follows the game's sooner when it changes, such as a lamp switched on or headlights going by: what you can see is lit again as soon as the game marks its light changed, nearest first, instead of waiting for that part of the world to be built again.
- Driving past lights stutters less: when the game relights many chunks at once, the mod reads their new light over the frames of the game's lighting pass, nearest first, instead of all in one frame. `SpreadLightUpdates` turns this off.
- Streets and floors with a lot of spilt blood on them take less work to draw in first person. Blood still darkens and fades over three days in small hourly steps, but each part of the town now takes its step at its own minute of the game hour, where before the ground under the blood was made again every few seconds.

### Fixed

- In first person, a shot through a light of a glazed door breaks that light, with the sound of breaking glass that zombies hear, as a shot breaks a window. The light is drawn as the shards left round its edge, from both sides, and stays broken in the save. The round goes on through it, and the next shot through the gap lands on what is behind the door instead of on the door. A shotgun blast with three or more pellets through a door's lights smashes the sash: every light breaks, and the wooden bars between them are blown out round where the blast struck, leaving splintered ends of uneven length nearer the frame.
- With `Reflections` on, a broken light of a glazed door no longer shows a reflection over the gap as if its glass were still there, whether a shot broke it or the map paints it broken.
- Letting go of forward while sprinting forward and to one side in first person no longer sends you on ahead at about twice the sprint's speed for most of a second. The sprint now slows to a stop as it does straight ahead, and then you step sideways at the stance's pace.
- Standing or turning on the spot where two parts of the map meet no longer loads a strip of the map again every few seconds, each time with a small hitch. The far edge of the loaded map now follows you up to a tile later.
- The slight wobble on edges in first person, worst round door and window frames, is gone: the eye's place is kept finer than before, so near edges no longer step as the head moves.
- Tarmac and gravel near your feet no longer sparkle as your head moves.
- Chain-link fences, window glass, film grain and fire smoke no longer fizz from frame to frame.
- First person no longer turns off for the session near a noise trap, or on a map with floor tiles from a mod that is missing.
- A pole on a roof is no longer drawn as the top of a pole set back below it.
- A porch canopy's depth follows when its band or its squares are built or taken down.
- Leaving a save gives the graphics card's memory back even when unloading the world fails.
- Walking pressed against a building's wall no longer shows the building through it with its inner walls missing.
- The dust in the air is a faint haze of fine specks, and no longer shows as large blobs close to the eye.
- A shop window's display stands, such as the mannequin stands behind the glass, no longer flicker against the glass along the window's foot.
- In first person, a fireplace or barbecue lit or put out, a compost bin filling or emptying, a plank knocked off a barricade, a body the game's corpse removal clears away, and vines and cracks spreading on walls and streets now show as they change, and a part of the map you come back to shows what changed there while you were away. Each could wait half a second or more close by, and six seconds or more further off.
- In first person, a building fills with zombies when you first see into one of its rooms on your floor from close enough, by the game's own rule, and no longer by where the mod happened to work out the light round you. What you have seen counts as seen for the game's random scenes, such as car crashes, the same way.
- In first person, the sides of a doorway or window with no frame of its own, such as the doorways and serving hatches in Spiffo's, are painted the colour of the wall. They were bare white, and showed as a pale strip down the edge of the opening and beside a shut door.

## [0.1.1] - 2026-10-28

### Changed

- In first person, jogging and sprinting go straight ahead or on a forward diagonal, at the game's own pace. Held sideways or backwards, your character walks. On a forward diagonal your character is drawn facing where you look.
- In first person, your character faces where you look whichever way it walks, diagonals included, so a mirror shows it looking where you do.

## [0.1.0] - 2026-10-27

### Added

#### First person and head tracking

- First person view. `Delete` switches between the isometric view and first person, with the camera flying between them. A save loads in the view you last switched to (`FirstPerson`), each local player in their own, and one that loads in the isometric view says which key turns first person on.
- Head tracking from any OpenTrack compatible tracker on UDP port `4242`: turning your head turns the view and leaning moves it. The mouse or controller keeps the aim.
- `Page Up` cycles rotation and position, rotation only, and position only.
- `Page Down` switches head turns between the horizon and the view's own up. In a vehicle the view always turns about its own up.
- Leaning stops at walls.
- Your character's head turns with yours, up to 80 degrees each way, in both views. Mirrors and window glass show it.
- Walking and running don't bob the view. Crouching and sitting lower it smoothly.
- Knocked down, lying, getting up or dying, the view follows your character's head.
- `FieldOfView` sets the field of view: 65 by default, 40 to 110.
- A carried flashlight points where your head points (`LightMultiplier`). Its beam lights everything in front of it and casts shadows.
- Head tracking runs while the game is unpaused and your character is alive. It follows one tracker at a time.
- When the tracker stops sending, the view eases back to the game's own, and your character's head with it. A save loaded with the tracker closed starts with the head straight.

#### Controls

- Left mouse attacks, right mouse aims, `Left Shift` sprints, `Left Alt` jogs, `Left Ctrl` crouches. Your own key bindings are not changed.
- The pointer comes out with the inventory open, with any game window or menu open, or with a click of the middle mouse button until you click it again or press `Esc`. Head tracking carries on.
- `E` uses the door, window, curtain, light switch, container, item, radio, TV or crafting station under the reticle, leaning and head turns included. What it will use is outlined. On an open or broken window it climbs through, where there is a floor on the other side to land on. On a fridge it opens the freezer or the fridge, whichever half the reticle is on. On anything else within reach, such as a sink or a chair, it opens the game's menu for it.
- Right click with the pointer out opens the game's own menu for what the pointer is on, the same as in the isometric view.
- Left click with the pointer out uses what the pointer is on, as `E` does with the reticle, the same as in the isometric view.
- The game's placing cursors (building, Walk To, Place Item, moving furniture, Cut down Tree, Dig Furrow) go where the reticle points, or the pointer when it is out. Their square is outlined, and a piece to build shows which way it will face.
- A single quick click is enough for a melee attack.
- Walking or sneaking sideways or backwards, and jogging or sprinting any way but straight ahead, in first person, your character keeps facing where you look, in the game's aiming stance at the pace of the same gait ahead, and a jog or sprint tires it as one ahead does. Offline only; `FacesTheView` switches it off.
- Controller: the aim stick turns the view, right trigger attacks, left trigger raises the sights, double-tap Back switches view.
- If no plugged-in gamepad is ticked in the game's Controller options, every gamepad the game recognises plays. A 3Dconnexion SpaceMouse is never taken as a controller.
- Every mod key can be rebound in `[Hotkeys]`. Mod keys do nothing in the main menu or while typing.

#### Weapons and combat

- The reticle sits where the shot will land. Whenever the game draws none, the mod marks the same point: a small crosshair with a weapon held, a see-through dot without one. `Crosshair` switches the mod's mark off.
- A two-handed weapon goes back into both hands when you put down a torch or anything else you held beside it, as Equip Two Hands does from the inventory. Switching a torch off beside one puts the torch away for that, and taking a torch up again keeps the weapon in the right hand. `RegripTwoHandedWeapons` turns this off. It never happens on a PvP server.
- Three aim modes on `Insert`: sights locked (default, the weapon follows your lean), free look (your head moves around the weapon) and stock sights (head turn and lean ease out while aiming, tilt stays).
- Aiming a firearm zooms the view. Head movement is scaled so the zoom doesn't magnify it.
- Held weapons are drawn like a modern shooter: low right at the hip, raised to the sights, swaying, with recoil based on each weapon's recoil stat and a muzzle flash (`MuzzleFlash`).
- The mod's own arms and hands hold each gun by its real grip, read from the gun's model, so guns from other mods are held right too. Handguns are held two-handed when your left hand is free. `VanillaArms=true` uses the game's arms.
- Reload and action animations: pump shotguns rack, pistols cycle their slide, lock back when empty and swap magazines, revolvers turn the cylinder and load one round at a time, double barrels break open and throw out the spent shells, the assault rifle, the battle rifle and the self-loading rifle and carbine cycle their bolt and throw out a case with every shot, hold the bolt back on the last round and let an empty magazine fall from the hand, lever guns swing the lever down and back and throw the fired case out of the top, and the toy cap rifle's hammer falls and its cylinder turns with each shot.
- With a one-handed weapon, the mod's left hand carries a torch beside it, pointing it where your head points and lowering it while it is switched off.
- A sawn-off has no stock, so it kicks in your hands when aiming too.
- Red dot sights, lasers and gun lights are drawn on the mod's gun models. With a red dot sight on a long gun the eye is raised to look through it.
- The varmint rifle has low open sights for when no scope is fitted.
- Leaning in with the sights up stops short of a long gun's rear sight.
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
- Single player: in first person a tree stops a shot at its trunk, and a shot past the trunk goes on. Pines, hemlocks and hollies, whose needles come down to the ground, still stop it anywhere on their square (`ShotsPastTrees`).
- Single player: shooting a vehicle's head or tail lamp, a lamp or a street lamp breaks it.
- Single player: a shot breaks the glass of every shut window it goes through, whether or not it hits anything behind it.
- `Medium` and `High`: blood on floors and walls has smooth edges, looks wet when fresh and dries brown.

#### Vehicles

- First person from any seat. The view tilts and rolls with the vehicle on its springs and in crashes.
- Full interiors for every vehicle in the game, with working speedometer (mph), rev counter and fuel gauge, and hands on the wheel (`VanillaCabins`). The game's dashboard panel is hidden in first person.
- Working rear-view and side mirrors. Zombies behind the vehicle show in them up to 30 tiles back.
- Rain and snow on the windows: drops land, run down and are cleared by the wipers. Snow melts on the windscreen and piles up where the wipers park.
- Snow settles on vehicle roofs, bonnets and windscreens. Engine heat and wipers clear the windscreen.
- Headlights light the road and cast shadows. Wet roads reflect them.
- Taking the driver's seat switches off a light you're holding.
- Vehicles tilt with the hills under them.
- Single player: hills slow vehicles uphill and speed them up downhill, and vehicles take off over crests at speed.
- Single player: vehicles smash through fences, street lamp posts and road barricades, losing a little speed and taking some damage.
- Single player: bushes and plants don't slow vehicles, and grass and dirt don't cut engine power.

#### World

- `Medium` and `High` rebuild the world in 3D: buildings with thick walls, roofs, eaves, gutters, downpipes and chimneys; doors, windows and curtains; furniture, appliances, shop fittings, fireplace surrounds and mantels, signs, street lamps, traffic lights, power lines, pylons, playgrounds, sports equipment, instruments and more.
- Rooms have skirting, cornices and dado rails mitred at the corners, and doors have bevelled casings, beading round each pane and lever handles where the game's picture shows one. Chain link fences are built from real wire, posts and rail.
- Railway track is built as rails on sleepers, with points where lines divide.
- Churches have their altar, crosses, chandeliers, lectern, long desk and pipe organ. Galleries have busts and statues. Sports grounds have goals with nets, boxing rings, tennis nets, show jumps, baseball bases and golf flags, and lie level over the hills. Pool tables, arcade cabinets, grocers' produce crates, picket fences and lawn flamingos are modelled too.
- Items lying loose in first person are drawn as the game's own 3D models when the game's 3D ground items option is on.
- New textures for floors, tiles, carpets, wallpaper, brick, wood, roofs and roads. Diagonal, chequered and parquet floors, mosaic splashbacks and cut stone walls keep their patterns. Road markings are drawn as clean paint.
- `Medium` and `High`: church stained glass windows are redrawn with richer glass and leading, in the same designs. The dove window has a priest at its foot (`VanillaTextures=true` puts the game's glass back).
- Pavements and car parks lie level, with kerbs.
- A TV that is on shows a moving picture: test bars with no signal, otherwise the show on its channel or tape.
- Pictures on walls and the shows on TV are stills staged and shot in the game. Family photos and certificates suit the room they hang in.
- Pictures, banners and bunting on walls.
- In first person the south and east walls of a room, which the isometric view never shows from inside, carry pictures that suit the house. They are drawn by the mod and never added to the save. Offline, `HiddenWallDecor` also hangs the game's own mirrors, clocks, coat racks and deer's heads there, as objects that stay in your save.
- Walls gather grime along floors and ceilings, in inside corners and under sills. Tarmac is worn in long stretches and patches, and along the lanes its road lines mark.
- Lifelike faces and sharper skins for people and zombies near you, and clothes and hair that show their weave, knit, grain or strands. Animals get new detailed skins (`VanillaAnimals`).
- Hills in the countryside, flat in towns. `HillHeight` sets their height from 0 to 2 (default 1.5).
- Land and buildings are drawn up to 768 tiles away, past the area the game has loaded.
- Distant trees are drawn where the game's map places them (`FarTreeDistance`, up to 768 tiles).
- Zombies are visible as far as your character can see in the current weather and light, and hidden only by what's between them and you.
- Single player: in the isometric view a zombie shows as soon as your character has a clear line to it and could see it by the game's rules, instead of late or only once it is close (`ZombiesSeenAtOnce`).
- Rooms are built before you see into them, so doors open onto furnished rooms. Rooms you can't see into aren't drawn.
- Each `Vanilla` row of `[Content]` (`VanillaWeapons`, `VanillaPlants` and the rest) puts one kind of mod content back to the game's own. They are all off by default.

#### Plants and seasons

- Trees have real branches and leafy crowns. Pines, hemlocks and hollies have needles. Dead trees have bare branches.
- Bushes, ferns and grass are 3D. Grass moves in the wind and parts around your feet.
- Field crops are drawn stalk by stalk within 30 tiles.
- Ivy grows up tree trunks and across the ground around them.
- Rose bushes grow out of the game's flower beds. They flower in season and are cut back bare in winter.
- Fallen sticks lie on the ground.
- Broad-leaved trees change colour in autumn, thin out and go bare in winter. Distant trees follow the season too.
- In autumn, leaves fall from the trees and blow in the wind. Fallen leaves pile up on the ground, fresh in autumn and matted in winter.
- Plants sway with the game's wind.

#### Weather and light

- Rain and snow fall as streaks and splash on the ground. None falls under a roof.
- Fog, rain and snow limit how far you see, matching the game. Fog glows in torch and headlight beams in `Medium` and `High`.
- Lightning bolts strike during storms, lighting up the sky, clouds and ground.
- Settled snow has drifts, banks against kerbs and sparkles in the sun. It keeps your footprints and vehicle tyre tracks.
- Snow settles on trees, bushes, grass and ivy, and dusts people and zombies out in it.
- Clouds, cloud shadows, the moon, low evening sun and dark nights.
- A low sun warms what it lights outdoors and turns rooms, people and what they hold gold.
- Moonlight brightens with the moon's phase and a clear sky, and follows the game's Night Darkness setting.
- Shadows from the sun, flashlights, headlights and fires. Lit lamps glow.
- Street lamps and lamps on buildings throw pools of light round them out to `LightDistance`: 30 tiles in `Medium`, 60 in `High`.
- A lamp or a TV that is on lights its room brightest close to it, falling away towards the walls. A TV's light is the colour of what it shows.
- Sunbeams through windows, with dust floating in the air.
- Water flows and reflects.
- Mirrors on walls, dressers and bathroom cabinets show the room, the people in it and you, in Medium and High with `Reflections` on. Low has no reflections. The glass has a faint tint, smudges and a worn silver edge.
- In `Medium` and `High` with `Reflections` on, shut windows and glazed doors within about 20 feet reflect your side of the glass, you included: the room from inside, plainest at night, and the street from outside. Glass on up to three walls shows it at once.
- `Medium` and `High`: burning houses, Molotovs and campfires burn with the game's flames stood up in the world, warm coloured and leaning back when seen from above, with smoke, embers and flickering firelight. Fireplaces and stoves burn inside. `VanillaFire` puts the game's smoke back.
- The picture adjusts to the brightness of what you're looking at, like an eye.
- `Medium` and `High`: every surface reflects the sky, the room or the ground by how rough it is, so metal looks like metal. Skin and clothes take the light the same way.
- `High`: brick, block and stone walls have depth up close. The mortar lies back between them and the sun's shade falls into the joints.

#### Graphics settings

- `F9` cycles the graphics mode of the view you are in, fading between them: `Automatic`, `Low`, `Medium`, `High` and `Custom` in first person, the same without `Automatic` in the isometric view. Switching views names the new view's mode, as `First Person (High)` or `Isometric (Low)`.
- `Automatic` lowers the quality when the frame rate drops below 45 and raises it when it holds above 57.
- `Low` is the game's own look, apart from first person's furniture, windows and doors, which are the mod's models in every mode (`VanillaFurniture`, `VanillaBuildings`). `Medium` and `High` also redraw the isometric view, in the game's colours and dark where your character can't see, as the game has it.
- `F8` cycles the picture styles `Vivid`, `Natural` and `Bleak` (default).
- Every shader the mod draws with is compiled while a save loads, with a count on the loading screen, and the save starts once they are done, so none is compiled in play.
- `ZombiesAndPropsDrawDistance` (16 to 80 tiles) and `Reflections` settings.
- `Custom` draws as the rows of `[Graphics]` say, which also include `TreeQuality`, `ViewDistance`, `RainOnGlass`, `ShadingAndLight`, `FullSizeTextures`, `SurfaceDepth`, `SharpShadows`, `WholeFigureShadows` and `FrameBlending`.
- `ReflectionScale` setting (0.25 to 1): the resolution wall mirrors and window glass draw their reflection at, as a share of the first person picture's. `High` draws 0.75, `Medium` 0.5.
- `GrassDensity` setting (10 to 100 percent): fewer grass plants for a faster picture. `High` grows all of it, `Medium` and `Low` 40 percent.
- `CorpseModels` setting: corpses are drawn as models lying where they fell, posed once and never animated, in place of the game's flat picture of each. Off in `Low`, `Medium` and `High`; turn it on in `Custom`. Animal carcasses keep the picture.
- A first person view larger than 2560x1440 is drawn in `Low`, `Medium`, `High` and `Automatic` with no more pixels than 2560x1440, and scaled up. `Custom` draws at its `RenderScale` at any size.
- `Medium` draws first person at 0.8 of the window's size each way, with shaded corners, lit air and, with `Reflections`, mirroring wet roads, floors, glass and water.
- Every mode can be drawn with less in four steps: no sharp shadows or frame blending; then also the mod's pictures at half size; then no more than `Medium`, without its frame blending; then no more than `Low`. On an NVIDIA graphics card the mod reads the card's memory when first person starts: 6 GB and more gives up nothing, 5 GB starts at no more than `Medium`, 4 GB or less at no more than `Low`. Other cards start with nothing given up.
- When the graphics card has no room for something the mod makes, every mode is drawn one step lower for the rest of the session, and the screen says so once. First person turns off only if the card has no room even at the lowest step. The log's `video memory:` lines say what was read and each step.

#### Sound

- In first person, sound comes from where things are relative to the way you face.
- Walls, doors and floors muffle sound. Rooms echo by their size and what they're made of.

#### Talking mouths

- Optional, off by default: your character's mouth moves with what you say into your microphone, in first person and in the isometric view, opening, spreading and rounding with the sounds you make, with teeth and a tongue behind the lips. Each player at the screen picks their own microphone. Online, other players' mouths move with the game's voice chat, and with their own microphone where they have this mod with talking mouths on. Nothing is recorded; online, only how open your mouth is and the shape of your lips are sent.

#### Multiplayer and split screen

- Works on servers. Only you need the mod.
- Online, players who have the mod see each other's heads turn with their trackers. It goes through the game's own player updates, so the server needs no mod. Players without the mod see your character's head as the game poses it.
- Names and speech appear over other players' heads in first person.
- In split screen and on servers without PvP, other players are drawn as far off as zombies.
- On PvP servers: leaning doesn't move your shot, hills are fixed at height 1, fog is as thick as in the game's own view, other players are shown only as the game shows them, a shot at a player hits the part the game rolls, and nothing is hidden by draw distance.
- Wounds, limb loss, bullet penetration, shooting out lamps and windows and vehicle physics are single player only. Each can be switched off in `[Rules]`: `ShotsWoundLimbs`, `ShotsThroughWalls`, `ShotsBreakLamps`, `ShotsBreakGlass` and `VehiclesSmashThrough`.
- Up to four players at one screen, each with first person and their own head tracker on ports `4243`, `4244` and `4245`. Tap `Delete` twice for player 2, three times for player 3 and four times for player 4, or hold it for everyone.
- Each player at the screen has their own graphics mode and picture style. Tap `F9` or `F8` once to four times for players 1 to 4, or hold it for everyone. Players 2, 3 and 4 keep theirs in `[Players]`. Starting split screen lowers `High` to `Medium`.
- `F7` switches two-player split screen between side by side and top and bottom.

#### Setup

- `install.cmd` installs the mod with no mod loader. Lopari can install it too.
- Lopari downloads the mod's art apart from its code, and on an update fetches only the art it does not already have.
- A Steam Workshop package, loaded by ZombieBuddy 2.x (tested with 2.3.4).
- The mod is one jar, cameraunlock-core included, with no native file.
- Settings are in `CameraUnlock.ini` in the game folder. Most changes apply without a restart.
- The main settings are also in the game's options under `Mods` > `Head Tracking`, grouped under titles with head tracking first.
- The game window opens centred on its monitor.
- If a game update breaks something the mod uses, the mod stays off and the log says what changed.
