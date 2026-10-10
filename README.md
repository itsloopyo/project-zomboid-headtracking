# Project Zomboid Head Tracking

![Project Zomboid running with this mod](https://raw.githubusercontent.com/itsloopyo/project-zomboid-headtracking/main/assets/readme-clip.gif)

An unofficial head tracking mod for Project Zomboid that moves the view with your head while your mouse or controller keeps aiming, driven by a webcam, phone, or any OpenTrack compatible tracker, with no VR headset required.

## Features

- **First person view** - `Delete` switches from the game's isometric view to a first person one, where head tracking moves the view. In the isometric view your character's head turns with yours
- **Decoupled look and aim** - your head moves the view while your mouse or controller keeps the aim
- **6DOF positional tracking** - lean and peek with head position
- **Works with any OpenTrack compatible tracker** - free options available for PC, iOS and Android
- **Redrawn graphics in both views** - in the `Medium` and `High` graphics modes the mod draws the isometric view as well as first person, with its own lighting, shadows, textures and models. `F9` cycles the modes, `Automatic`, which the mod starts in, steps between them as fast as your graphics card draws, `Low` leaves the isometric view as the game draws it, and `Custom` draws the mix of settings you choose
- **Multiplayer** - works when you join a server, and only you need the mod
- **Settings in the game's options** - the tracking, aim and yaw modes, the field of view, the graphics mode and the settings of its `Custom` mode, the picture style, the height of the hills and what the mod draws of its own are under `Mods` in the game's options
- **Two players at one screen** - each has a first person view and a head tracker of their own. Tap `Delete` for the first player, tap it twice for the second, hold it for both
- **On a controller** - the first player plays first person on a controller as well as the second. Tap Back twice to switch the view

## Requirements

- [Project Zomboid](https://store.steampowered.com/app/108600/Project_Zomboid/) on Steam, the store the installer detects. The mod was built and run against build 42.21.0. A copy from another store has not been run with it; the installer takes its folder as a path (see below).
- A tracking source: [OpenTrack](https://github.com/opentrack/opentrack) with a webcam or VR headset, or a phone app that sends the OpenTrack UDP protocol
- Windows 10 or 11, 64-bit

## Installation

### Lopari

Once this mod is available in Lopari, download [Lopari](https://lopari.app), choose **Project Zomboid**, and click
**Play with head tracking**.

### Standalone Installer

1. Download `ProjectZomboidHeadTracking-v<version>-installer.zip` from the [Releases page](https://github.com/itsloopyo/project-zomboid-headtracking/releases).
2. Extract it anywhere.
3. Double-click `install.cmd`.
4. Configure OpenTrack to output UDP to `127.0.0.1:4242` (see [Setting Up OpenTrack](#setting-up-opentrack)).
5. Launch the game, load a save or join a server, and press `Delete`.

If the installer cannot find your game, tell it where the folder holding `ProjectZomboid64.exe` is, in either of two ways:

```powershell
# Environment variable
$env:PROJECT_ZOMBOID_PATH = "D:\Games\ProjectZomboid"
.\install.cmd

# Or pass the path
.\install.cmd "D:\Games\ProjectZomboid"
```

The installer writes to one copy of the game. If you have more than one, run it again with the other copy's path.

Run `install.cmd` again after a game update. The installer builds its launch settings from the game's own, so a reinstall picks up whatever the update changed in them.

### Manual Installation

Mod managers do not deploy this mod: it needs a launch settings file built from the one in your own game folder, which an archive cannot carry. To place the files by hand:

1. Copy `plugins\ProjectZomboidHeadTracking.jar` and `plugins\CameraUnlockCore.dll` from the installer ZIP into the game folder, next to `ProjectZomboid64.exe`.
2. In the same folder, copy `ProjectZomboid64.json` to a new file named `ProjectZomboid64.site.json`. The game's launcher reads the site file in place of the stock one when it is there.
3. Open `ProjectZomboid64.site.json` and make three changes:
   - Note the value of `mainClass`, then change it to `"com/cameraunlock/core/agent/Boot"`.
   - Add `"ProjectZomboidHeadTracking.jar"` as the last entry of the `classpath` list.
   - Add `"-Dcameraunlock.mainClass=zombie/gameStates/MainScreenState"`, `"-XX:+EnableDynamicAgentLoading"` and `"--enable-native-access=ALL-UNNAMED"` as the first three entries of the `vmArgs` list. The part after the `=` of the first is the `mainClass` value you noted.

There is no mod loader to install. The mod is a Java agent that the game's own Java runtime loads. It does not use or need any other first person mod; those are alternatives to this one.

## Setting Up OpenTrack

The mod listens for the OpenTrack UDP protocol on port `4242`.

1. Install [OpenTrack](https://github.com/opentrack/opentrack/releases).
2. Pick a tracker under **Input**, using the subsections below.
3. Set **Output** to **UDP over network**, with host `127.0.0.1` and port `4242`.
4. Press **Start**, then launch the game.

Centering is done in your tracker: OpenTrack's **Center** bind, SteamVR's reset, or the CENTER button in a phone app.

### VR Headset Setup

1. Connect the headset to the PC over Air Link, Virtual Desktop or a link cable.
2. Start SteamVR.
3. Set OpenTrack's **Input** to the SteamVR tracker.
4. Leave **Output** on UDP over network, `127.0.0.1:4242`.

### Webcam Setup

Set OpenTrack's **Input** to **neuralnet tracker** and pick your camera in its settings. It needs no markers and no IR hardware.

### Phone App Setup

The mod accepts one thing: the OpenTrack UDP protocol on port `4242`. A phone app works here if it sends that protocol itself, or ships a PC companion that does. Check your app for an OpenTrack or UDP output option.

How you wire it up depends on how much filtering the app does before the packet leaves the phone:

- **An app that filters on-device can send direct.** Point it at this PC's LAN IP address (run `ipconfig` to find it) on port `4242`, with no OpenTrack on the PC.
- **A raw or lightly filtered feed needs to go through OpenTrack.** The mod's smoothing is sized to take the edge off a clean signal, not to rescue a noisy one. Send the app to OpenTrack's **UDP over network** input on a spare port, and let OpenTrack's filters and curves clean the feed up before its output forwards to `127.0.0.1:4242`. Use this route too if you want OpenTrack's curve mapping.

The test: send direct and hold your head still. If the view drifts or shakes, route it through OpenTrack.

I made [Headcam](https://headcam.app) so decent tracking was free for anybody with a phone already in their pocket. It filters on-device, so it can send direct, and any app that filters enough noise works the same way.

A phone on WiFi is a remote connection, and the mod smooths a remote connection more than a local one (see [Configuration](#configuration)). So is a tracker on this same PC that sends to the PC's LAN address instead of `127.0.0.1`: the mod reads the address the packet came from, not which machine sent it.

## Controls

`Delete` switches between the game's own view and the first person view, and the camera flies from one to the other in a little over half a second. In the `Low` graphics mode nothing of the first person view is loaded until you first ask for it, so the first press of a session shows `First person: getting ready` while the view is made, and then flies. In `Medium` and `High`, and in `Automatic`, which begins as `High`, the mod draws the game's own view as well, so what it draws with is loaded as play begins. `F9` goes to the next graphics mode, `Automatic`, `Low`, `Medium`, `High` or `Custom`, in either view, and `F8` to the next picture style, `Vivid`, `Natural` or `Bleak`: see [Configuration](#configuration). The view lands facing the way your character faced when you pressed it. In first person the mouse turns the view and the movement keys follow it, from the moment you press the key. Movement keys you were already holding keep the walk they were making, steered by the mouse, until you let go or press other keys, and other movement keys keep that walk too until half a second after the camera lands. On a multiplayer server it works: see [On a server](#on-a-server).

### In first person

While the mouse is turning the first person view, these work differently from the game's own view:

| Action | In first person | The game's default |
|--------|-----------------|--------------------|
| Attack | Left mouse, at once | Left mouse while the aim is held |
| Aim | Right mouse | Right mouse or `Left Ctrl` |
| Sprint | `Left Shift` | `Left Alt` |
| Jog | `Left Alt` | `Left Shift` |
| Crouch | `C` or `Left Ctrl` | `C` |

- **Attack**: a left click raises your weapon and attacks with it, a firearm or a melee weapon. What the attack does is the game's own: ammunition, recoil, aiming time and hit chance are unchanged. A click also does what holding the aim does in the game: it stops a sprint and whatever your character was in the middle of.
- **Aim**: with a firearm in hand, holding the aim narrows the view, to 50 degrees at the default field of view. A left click alone does not narrow it. Head tracking moves the picture as far through the sights as at the hip, and so does the mouse. The game is given the direction the view points as the aim, and the point the view is aimed at as the place to look for a target.
- **Clicks** do not act on the doors, switches and containers of the game's own view, and a right click does not open its menu: there is no pointer to use them with while the mouse turns the view. Bring the pointer out for those (see [The pointer](#the-pointer)).
- **Crouch** keeps your own key if you moved it, and gains `Left Ctrl`.
- Reload, rack, shove, the weapon slots, the light and interact are on your own bindings, as in the game's own view.
- **Sound** is heard the way the view faces: what is to your left in the picture is heard on the left, and turning the mouse or your head turns it with the view. The game's own view hears a sound by where it is on the screen, and as the camera flies between the two views what you hear turns with it. In first person the game's sounds are given a camera zoomed all the way in, whatever zoom the game's own view was left at. Where your character hears from is the game's own.
- `Left Alt` is also the game's default for an attack aimed at the ground, so an attack made with the jog held is aimed down.

None of this is written to your key bindings. The game's own view, a paused game and the options screen all have your bindings as you saved them. If the game's **toggle to aim** option is on, the left mouse button works as it does in the game: set the aim first, then click.

### The weapon in your hands

With a weapon in hand and on foot, first person draws it and the arms that hold it before the eye instead of where the game's animation carries them. The shotgun, the hunting rifle and the scope described below are the mod's own and are drawn in `Medium` and `High`. In `Low`, which the mod starts in, the game's own weapon is drawn there, and a scope shows no picture of its own. The arms and hands described below are the mod's own in every mode, `Low` included, so the weapon is held in hands that reach it from below the picture.

- **A firearm** sits low and to the right at the hip, pointing at the place the shot will land. Holding the aim brings it up onto your aim as the view narrows. It stays on your aim when you turn your head: look away and it is off to one side, still pointing at the reticle. Your left arm comes with it when the weapon is held in both hands.
- **A melee weapon** is held up at the lower right. An attack is drawn for the eye, in time with the game's own: the weapon is drawn back, comes round and lands at the moment the game lands the hit. What it landed on stops it and it springs back, and a miss goes on through. Which attack you see is the one the game chose: a swing across from the right shoulder, one brought down from overhead, one brought up from the hip, one onto something lying on the ground, a thrust with a knife or a spear, and a shove.
- **The pump action shotgun** has a model of the mod's own, made to be seen from a hand's breadth away: walnut and blued steel, lit by the sun, the sky and the room. After each shot the fore-end is worked back and forward, the bolt opens and the spent shell is thrown out to the right. The sawn-off shotgun and every other weapon are the game's own models, and a scope, recoil pad or choke fitted to the shotgun does not show on it.
- **The hunting rifle** has a model of the mod's own too: a bolt action sporting rifle with a walnut stock checkered at the grip and the fore-end, a dark fore-end tip, a blued barrel and receiver and open sights. After each shot the bolt is worked: its handle turns up, the bolt comes back, the fired case is thrown out to the right, and the bolt shuts again. A scope fitted to it is drawn on it. A recoil pad or a sling fitted to it does not show.
- **A rifle scope**: the game's x2, x4 and x8 scopes are drawn as a scope of the mod's own, and you look through it. Its eyepiece shows the world a second time, drawn from the scope's front lens along the scope's own axis, with the reticle in it. The power is counted from your unaided view: through the x8 a thing is eight times as large as it is with nothing aimed. While the sights are up the mouse turns your aim by one over the power, so the reticle crosses what it is on as fast as the crosshair does at the hip. See [Through a scope](#through-a-scope). A red dot and the tritium sights are the game's own models.
- **Your arms and hands** on the weapon are the mod's own, modelled with a wrist, knuckles, nails and fingers that close on what they hold, in your character's skin tone, sleeves and gloves, with the blood and dirt the game says is on them. Each firearm is held where its own model has its grip, the right hand round it along the grip's own line with the web of the hand at its back, and the left hand under the fore-end the model has, as high as its underside, so a firearm another mod adds is held by its own grip too. A handgun is held in both hands while your left hand is free, the left fist round the right, and in the right alone while the left holds something else. The first finger lies along the frame of a firearm at the hip and goes to the trigger as the sights come up. Each hand goes to the part of the weapon it works: the right hand to a bolt's knob after a shot, the left under the fore-end, to the magazine, to the port a shell goes in at, and over a pistol's slide. To be loaded the weapon is brought up before you and tipped over, and with the sights up a rifle comes down off the eye while its bolt is worked. The arms are the length arms are, from shoulders beside and under your eye, so a rifle or a shotgun is held nearer at the hip than the game's arms hold it, where the left hand reaches its fore-end. A handgun is drawn six tenths of the game's size, which is the size it is beside a hand. `OwnArms=false` under `[Content]`, `Own arms` in the game's options, draws the game's own arms and its own sizes instead, in every graphics mode: the game's arms as its animation poses them, moved with the weapon to before the eye. That is for another mod that changes your character's arms or weapons.
- The weapon trails a turn of the view by a few degrees and catches up, sways with each step, drops across the body while you sprint, and dips when you crouch or land.
- **A shot** kicks the weapon back and up for about a sixth of a second. A small tongue of fire opens out ahead of the muzzle for a frame or two, behind the weapon from where you look along it, and lights what is near it for that long. `MuzzleFlash=false` in `CameraUnlock.ini`, or `Muzzle flash` in the game's options, leaves the flash and its light out.
- **The crosshair**: while the aim is held the game draws its own reticle, which widens and tightens as the game's aim does, and first person puts it where the shot will land. With a weapon in hand and the aim not held the game draws none, so first person draws a small crosshair at that same point. It spreads a little while you move and after a shot. There is none without a weapon, in a vehicle, or while the pointer is out.

All of it is drawn only. Where the shot goes, how fast you can fire, recoil's effect on your aim and what a swing hits are the game's own and are not changed. The rest of your body stays as the game poses it.

### The pointer

The inventory, crafting, the health panel, the map and the game's menus all need a pointer. In first person the mouse becomes one when you ask for it or when one of them opens, and goes back to turning the view afterwards. While the pointer is out the first person picture stays where it was, head tracking carries on, the game is not paused, the movement keys still follow the view and keep their first person keys, and clicks do what they do in the game's own view.

| What | Pointer |
|------|---------|
| Inventory key (`I`, or your own binding) | Shows the inventory and loot panels and brings the pointer out. Press it again, or `Escape`, to hide them and go back to the look |
| A window, dialog, context menu or radial menu of the game's opens | Out for as long as it is open |
| Middle mouse button, held | Out while it is held |

- The inventory and loot panels are the game's own and behave as they do there: unless you have pinned them, each one unfolds when the pointer is over it.
- A window you leave open would keep the pointer out. Pressing the inventory key twice goes back to the look past whatever is open, and the pointer comes out again the next time something opens. A window that was already open when you switched to first person does not bring it out.
- Hold the middle mouse button and right click to get the game's menu for something in the world. The menu then keeps the pointer out until you pick from it or close it.
- A click on the world goes to what the game's own view has under the pointer, which is drawn beneath the first person picture and is not what the picture shows there. `E` acts on what the middle of the view is on, and is the surer way to use a door or a window. Whatever `E` would act on is drawn with a pale line round it: a door, a window, a curtain, a light switch, a piece of furniture that holds things, a body, or something lying on the ground.
- `Tab` and every other key are as the game has them.
- If you have bound something to the middle mouse button, it does both.

### On a controller

The first player can play with a controller, as the game has it: tick the controller in the game's controller options, press A on it in game and pick taking over player 1. The second player at one screen is on a controller too.

- **Back tapped twice** switches that player's first person view. The second tap has to come within 0.4 seconds of the first. A single press or a hold of Back still opens the game's wheel, and the second press of a double tap does not leave it open.
- **In first person** the aiming stick turns the view, and the movement stick walks the way the view faces. The right trigger attacks. With a firearm in hand the left trigger raises the sights, and does not shove while it does: without one it is the game's shove as before. The controller works the game's panels as it does in the game's own view.
- While the first player is on a controller, the mouse does not turn the view and is not held by the game window.

### Two players at one screen

A second player joins the screen with a controller, as the game has it, and has a first person view of their own. Each view is switched by itself: one player can be in first person while the other stays in the game's own view.

- **`Delete` tapped** switches the first player's view, **tapped twice** the second player's, and **held** for 0.4 seconds both players'. A tap acts 0.3 seconds after the key is let go, which is how long a second tap has to follow it.
- **The second player's tracker** sends the OpenTrack UDP protocol to port `4243`, where the first player's sends to `4242`. Run a second tracker, or a second output, pointed at `127.0.0.1:4243`. The port is `SecondPlayerUdpPort` in [Configuration](#configuration).
- **The second player's controller** works first person as in [On a controller](#on-a-controller), Back tapped twice included.
- **The modes are shared.** The tracking mode, the yaw mode, the aim mode, the graphics mode and the picture style are one setting for both players, on the keys above.
- **`F7`** turns the screen between side by side and one above the other. Side by side each picture is half the screen's width and all of its height, with the same field of view up and down, so less shows to either side. One above the other each has the whole width.

Two players can ride in one vehicle, each seeing it from their own seat.

A third and a fourth player are drawn in the game's own view, and have no first person view.

### What your character can see

In first person your character sees no more than the game lets them see in its own view.

- **Fog, rain and snow** hide what stands far off in them. In the game's full fog a zombie four tiles off is plain, one at six half hidden and one at ten gone, as in the game's own view, and thinner fog, rain and snow hide less. The air takes the colour of the sky it lies under, and at night of the light on the ground round you. In `High`, where the air is lit, fog glows where a torch or headlights shine through it.
- **Zombies** past the distance the game lets your character see in that weather and at that hour fade out, as the game fades them.
- **Drink** blurs and sways the view as the game's own does: the game draws that over the whole window, first person included.
- **Tiredness, panic, drink and some headwear** darken the edge of the view as far as they narrow the game's cone of sight. The dark does not: the night is as dark as the game's light makes it. What is behind your character is not dimmed either, since in first person you turn your head to see it.

### On a server

The first person view and head tracking work when you join a multiplayer server. Only you need the mod. The server does not, and neither do the other players, who see your character as they see anyone's.

- The names and the speech the game writes over other players are over their heads in the first person view. Your own name and what you say are not written across the view: what you say is in the chat panel. On a server that shows a name only under the pointer, a name shows while the middle of the view is on its player.
- `Esc` opens the game's menu without pausing, as it does for everyone on a server. The key page in it shows and saves your own keys, not the ones first person answers with.
- Your character's head turns with yours on your own screen only. The mod sends nothing to the server, so other players do not see it turn.
- Dying and coming back as a new character leaves you in first person.

Where you and another player can hurt each other, six things are held to what the game gives a player without the mod, or to what every player with it has, so that nobody loses a fight to it. That is any server with PvP on, whatever your own safety is set to: the game lets a player whose safety is off hurt one whose safety is on. On a server with PvP off none of them applies and the mod plays as it does alone.

- Leaning does not move a shot. With the sights up in sights locked, the lean across the aim eases out and the round leaves from your character, where the other players see your weapon. Alone, your arms and weapon move with your head and the round leaves from where your eye is.
- Walking sideways or backwards puts your character in the game's own aiming stance, at the pace the game gives that stance, with the sights down. The game shows a player what is in front of their character, and other players read which way a character faces to tell what its player can see. In first person you see where the view faces, so there your character faces it too. Walking forward, and forward with a sideways key, is as it is alone.
- A room is filled as the game shows it. What stands in a room, and a body lying in it, is drawn once the game has shown your character the room, as it is for every player. Alone, in `Medium` and `High`, a room you could soon see into is whole before you can see into it, and one you cannot see into is not drawn.
- A zombie is shown as the game shows it to every player. Alone, and on a server where nobody can be fought, a zombie before your eye is drawn whole up to the distance the game lets your character see in that weather and at that hour, hidden by what stands between and by fog, rain and snow.
- Nothing is left out for the draw distance. `ZombiesAndPropsDrawDistance` and `ForwardDrivingDrawDistance` draw everything as far as the view goes, whatever they are set to, so a vehicle or a piece of furniture another player stands behind is never missing from your picture. Alone, and on a server with PvP off, they leave out what stands further off than they say.
- The hills stand at `1` whatever `HillHeight` says, so a crest hides from you what it hides from every other player with the mod. Alone, and on a server where nobody can be fought, they stand as high as you set them.

`MuzzleFlash` is the same there as alone. The game draws no first person view and so no flash in one, so a player without the mod sees what you see with `MuzzleFlash=false`, and a flash you leave on is one only you see.

### Hotkeys

| Action | Key |
|--------|-----|
| Cycle tracking mode | `Page Up` |
| Toggle yaw mode | `Page Down` |
| Cycle aim mode | `Insert` |
| Toggle first person view | `Delete` tapped; tapped twice for a second player; held for both. On a controller, Back tapped twice |
| Cycle graphics mode | `F9` |
| Cycle picture style | `F8` |
| Turn split screen | `F7` |

- **Tracking mode** cycles rotation and position, rotation only, then position only. Rotation only is how you turn the positional lean off.
- **Yaw mode** switches head yaw between turning about straight up and turning about the view's own up. In a vehicle the view tips and rolls with it, and head yaw there is always about the view's own up; on foot it is as you set it.
- **Aim mode** is how head tracking behaves while a firearm's sights are up. See [Aiming down sights](#aiming-down-sights).
- **Graphics mode** and **picture style** are `Quality` and `Style` in [Configuration](#configuration).
- **Split screen** is for two players at one screen: the key goes between side by side, as the game has them, and player 1 above player 2, each with the whole width and half the height, says on the screen which it went to and saves it as `SplitScreen`. With one, three or four players the key does nothing and the screen is laid out as the game lays it out.

These keys do nothing in the game itself. There are no `Ctrl+Shift` chords, because Project Zomboid acts on a letter key whatever is held with it, and no key turns head tracking off: `EnableOnStartup` in `CameraUnlock.ini` does that. The tracking mode, the yaw mode and the aim mode are also in the game's own options (see [Configuration](#configuration)).

Every key here is a row of `[Hotkeys]` in `CameraUnlock.ini`: change a row to move an action to other keys, or leave it empty to take its keys away. The rows are read when the game starts. A key pressed while you are typing in one of the game's text boxes, or in its menus, does nothing.

From a seat in a vehicle head yaw always turns about the view's own up, whatever the yaw mode is, and `Page Down` does nothing until you get out. The yaw mode you chose applies again on foot.

The tracking mode, the yaw mode, the aim mode, the graphics mode and the picture style are saved when you change them. The first person view is not: each save starts in the game's own view.

### Head tracking

Tracking moves the view while the first person view is on, the game is not paused and your character is alive.

Your character's head turns with yours in both views, alone, on a server and with two players at one screen. In the game's own view that is all tracking does: the view stays where the game has it. In first person it shows in your character's shadow. The head turns as far as a neck does, 80 degrees to either side, and a lean does not move it. The tracking mode's rotation setting switches it with the rest of the rotation.

In `High`, a window in first person also shows your character in its glass, with their head turned as yours is and their eyes level with yours. It shows in a shut, unbroken window on the floor you are on that you stand square in front of, faintly by day and plainly at night. A mirror hung on a wall shows you the same way.

## Configuration

Project Zomboid has no field of view setting, so the first person view has one of its own: `FieldOfView` in `CameraUnlock.ini`, in degrees from the bottom of the picture to the top, `65` unless you change it and anything from `40` to `110`. A wider screen shows more to the sides at the same number. The mod reads it again while the game runs: save the file and the view changes within a second or so, with no restart. The reticle and head tracking follow the angle the picture is drawn with.

The mod's own picture costs a graphics card far more than the game's own view does. `F9` goes through five graphics modes, `Automatic`, `Low`, `Medium`, `High` and `Custom`, says on the screen which one it went to and saves it as `Quality` in `CameraUnlock.ini`, so the game starts in it next time. `Low` is as near the game's own look as there is: the game's own view is the game's own picture, and first person is drawn with the game's own sprites, models and textures in place of everything the mod made but the arms on a held weapon, which `OwnArms` switches, at a little over three fifths of the pixels each way, seeing 48 tiles, without the shading of corners, the lit air, the reflections or the depth in near surfaces. `Medium` and `High` draw both views, the game's own view too, with what the mod made. Drawn by the mod, the game's own view opens a building as the game does: the floors over yours are left out of the buildings the game leaves them out of, the walls the game cuts away are cut down to a low course, and a room has the game's light for it, so one your character has not seen into is dark. `High` is the whole picture: corners are shaded and take the light of what stands near them, the air is lit, wet roads, polished floors, glass and car paint mirror, and near surfaces have depth. `Medium` has none of those four, shows the game's own grass, trees, bushes and rocks in place of the mod's whatever `OwnPlants` says, draws four fifths of the pixels each way, sees 64 tiles instead of 80 and uses smaller shadow maps. In first person in `High`, water mirrors the sky, its banks and what is built and grows on them, though not people, zombies or vehicles, and a piece of water shows the same thing whichever way your head is turned. The wind roughens it. A vehicle's mirrors show the road behind as `Low` draws it in every mode: 48 tiles of it, with no depth in its surfaces. With `Reflections=false` in `Custom` they are plain glass, and the road behind is not drawn for them at all. The game starts in `Automatic` until you change it. `Automatic` goes by how fast the first person picture is drawn, and only while first person is on: it begins as `High` and draws less, as `Medium` and then `Low` do but still with everything the mod made, and last as `Low` itself, the game's own look, each time the picture has been drawn fewer than 45 times a second for three seconds. It draws more again, a level at a time, once the picture has been drawn 57 times a second or more for fifteen seconds, but not a level it left in the last two minutes, and that wait doubles, up to 32 minutes, each time a level it went back up to was too slow within a minute. So a place that is too slow at one level and fast enough at the one below settles at the one below. With a frame limit set in the game's options, 45 and 57 are at most four fifths and nineteen twentieths of the limit, so a game held to 30 frames a second is not taken for a slow one. Each step says `Graphics: Automatic (Medium)` on the screen, with the level it went to, `Stock` for `Low`'s own look, and the game's options name the mode the same way. Only `Automatic` steps: `Low`, `Medium`, `High` and `Custom` are drawn as you chose them however fast or slow the picture is. Beginning as `High`, `Automatic` puts the mod's own surfaces, plants and weapons on the graphics card as play begins, where `Low` puts nothing there until you first switch to first person. Measured on one machine at 1280 by 720, that took 133 ms over 17 frames, none longer than 34 ms, and first person in `High` held about 460 MB more of the graphics card's memory and 630 MB more of the PC's than the same session did at `Stock`. The row is read again while the game runs, like `FieldOfView`. The surfaces, plants and weapon pictures the mod made are held on the graphics card in BC7 texture compression, which a card with OpenGL 4.2 or the `ARB_texture_compression_bptc` extension reads. On a card with neither they are unpacked as they are read and take four times the video memory.

`Custom` draws the picture the `[Graphics]` section of `CameraUnlock.ini` describes, a row for each thing the other modes decide: `StockLook` (the game's own look, as `Low` has it), `SpritePlants` (the game's own grass, trees, bushes and rocks, as `Medium` has them), `RenderScale` (how many of the window's pixels the first person picture has each way, `0.5` to `1`), `SharpShadows` (shadow maps 4096 texels across where they are otherwise 2048), `ViewDistance` (how far first person is built and drawn, `32` to `80` tiles: the land, the buildings and the plants), `ZombiesAndPropsDrawDistance` (how far off zombies, people, vehicles and the furniture, appliances and decor the mod has models of are drawn, `16` to `80` tiles), `ForwardDrivingDrawDistance` (the same on the side a vehicle you ride in faces), `RainOnGlass` (`Low`, `Medium` or `High`), `ShadingAndLight` (the shaded corners and the lit air, and with `Reflections` the mirroring of wet roads, polished floors, glass and water), `Reflections` (the mirroring of the world, a vehicle's mirrors included: off, water shows the sky alone as in `Low` and `Medium`), `FullSizeTextures` (the mod's pictures of surfaces, walls and skin at full size where they are otherwise half), `FarTrees` (the mod's own trees and bushes to the end of sight, where past 40 tiles they are otherwise the game's sprites) and `SurfaceDepth` (the depth in near surfaces). In the game's options those rows show the picture of the mode you are in. Change any of them there and apply, and the graphics mode goes straight to `Custom` with the rows as you set them, says `Graphics: Custom` on the screen as `F9` does, and saves the mode and the rows. A row edited in the file while the game runs goes to `Custom` the same way. Until you change them the rows hold `High`'s picture, so the first `F9` from `High` to `Custom` draws the same picture. `Low`, `Medium` and `High` never change them, so `F9` coming round to `Custom` again draws them as you left them.

`F8` goes through three picture styles, says on the screen which one it went to and saves it as `Style` in `CameraUnlock.ini`. A style is how the mod's picture is developed from the light in it, and nothing else: the light, the weather, the hour and what the world is built of are the same in all three, and none costs more to draw than another. `Vivid`, which the game starts in, has strong contrast and full colours, cool in the shade and warm in the sun, with dark corners, glare off the sun and a glow round bright lights. `Natural` shows the colours as they are lit, with no tint, less contrast, and less of the dark corners, the glare and the glow. `Bleak` is washed out and low in contrast under a faint drab cast, with no glare off the sun, nearer the colours of the game's own view. The style applies wherever the mod draws the picture: first person in every graphics mode, and the game's own view in `Medium` and `High`. The row is read again while the game runs.

The game's map is flat. In first person the mod gives the land a lie of its own: the country rises and falls, hills stand on it, more of them and higher in farmland and woods than anywhere else, a town's streets go over gentle swells, and open ground is uneven underfoot. `HillHeight` in `CameraUnlock.ini`, `Hill height` in the game's options, is how high all of that stands: `1`, the default, as the mod makes it, `0` as level as the game's map, and anything up to `1.5`, where the land is half as high again and the steepest main road climbs about one in five and a half. A kerb, a bank down to water and how far water lies below its banks are the same at every height, and everything drawn on the land, buildings, vehicles, people and your own eye with it, stands on it at the height you set. It applies in first person in every graphics mode, `Low` included, and does not change what the picture costs, so it is not one of the rows of `[Graphics]` and changing it leaves the graphics mode as it is. The game's own view from overhead is always level. The row is read again while the game runs: the land, what stands on it and the far land are built again at the new height over the next second or so.

Each frame of the first person picture is drawn on its own. `FrameBlending=true` in `CameraUnlock.ini` mixes each frame with the one before it instead: fine edges, leaves and the grain of soft shadows are smoother and steadier, and the whole picture is a little softer, as much standing still as while you turn or walk. It is `false` unless you change it, and it is read again while the game runs.

A shot from a firearm in first person flashes at the muzzle: a small bloom of fire out ahead of the barrel for a frame or two, drawn behind the weapon, and a moment's light on the weapon, your arms and what stands near. The same flash looks smaller in the open by day than at night, as the eye lets in less light. `MuzzleFlash=false` in `CameraUnlock.ini` leaves out both the flash and its light. It is `true` unless you change it, and it is read again while the game runs.

The first person view shows a good deal that the mod made itself in place of the game's own: models of two firearms and of rifle scopes, furniture, pictures on walls, leaves, trunks and grass, textures for the ground and for buildings, windows, doors, curtains and handrails built as shapes, skins for zombies and for the living, the cloth, leather and hair of what they wear, and your character's arms and hands on a held weapon. Each of those is a row of the `[Content]` section of `CameraUnlock.ini`, `true` unless you change it: `OwnWeapons`, `OwnFurniture`, `OwnPictures`, `OwnPlants`, `OwnTextures`, `OwnBuildings`, `OwnZombies`, `OwnPeople`, `OwnArms` and `OwnClothes`. Set a row to `false` and the mod draws the game's own there, which is how to see a weapon, a piece of furniture or a texture that another mod has changed as that mod made it. These rows are read again while the game runs too, so the change shows a second or so after you save the file. In `Low` the game's own view is the game's own picture, and first person draws the game's own whatever the rows say, but for `OwnArms`, which applies in every mode. In `Medium` and `High` the mod draws that view too, and the rows switch what it shows there as they do in first person.

What the rows do not switch is how the first person view is put together from the game's flat sprites: walls with a thickness, fitted roofs, kerbs, the shape of the land, the sky, the light and the inside of a vehicle are the view itself, and `Delete` is the switch for that. How high the land's hills stand is `HillHeight`.

Aiming a firearm narrows the view to 0.73 of that angle's half tangent, a magnification of 1.37, which is 50 degrees at the default. That ratio is built in and is not a setting.

The tracking mode, the yaw mode, the aim mode, the graphics mode and the picture style are saved to `CameraUnlock.ini` when their keys change them. Head tracking is on or off as `EnableOnStartup` says. An uninstall leaves the file in place.

The same settings are in the game's own options, from the main menu and from the menu `Esc` brings up in a save: the `Mods` tab has a `Head Tracking` section with the tracking mode, the aim mode, `World space yaw`, `Field of view`, `Quality`, `Style`, `Hill height`, `Frame blending`, `Muzzle flash`, `Split screen`, the thirteen rows of `[Graphics]` and the ten rows of `[Content]`. `Apply` or `Accept` saves what you changed to `CameraUnlock.ini` and the game runs on it at once. Each time the options open, the section shows what the mod is running on then, so a mode a key changed or a row you edited in the file is there as it is. The field of view moves a whole degree at a time there, and the file takes any value from 40 to 110. `Hill height` moves a hundredth at a time. Each setting shows what it does with the pointer over it: a slider with the pointer over its name or its number. The key lists, the tracker's ports, the smoothing and the limits of a lean are in the file alone. A setting saved from the options is written as a value, like one a hotkey saves, so it no longer follows `Defaults.ini` in this game until you set it to `default` again.

`LocalSmoothing` applies to a tracker sending from `127.0.0.1` and `RemoteSmoothing` to one sending from any other address, this PC's own network address included. Both smoothing values cover rotation and position. A lean is also held back from walls, so the view does not end up inside one.

A flashlight you carry throws a beam in the mod's view, which stops at whatever stands in it. It follows your head and not your aim, and turns a little further than the view does. When you turn your head your eyes end up past the center of the screen, so a beam matched to the view alone lands short of what you are looking at.

| Setting | Default | What it does |
| --- | --- | --- |
| `LightMultiplier` | `1.5` | How far it turns relative to your head. `1.0` matches the view, `0` leaves the beam on the aim |

Sensitivity, axis inversion, deadzones and response curves are set in your tracker, so one profile behaves the same in every game.

<!-- cameraunlock:config -->
The mod reads its settings from `CameraUnlock.ini` in the game folder, and creates the file when it starts and finds none. Edit it with any text editor.

A setting set to `default` takes its value from `Defaults.ini`, which every head tracking mod that keeps its settings in `CameraUnlock.ini` reads. Head tracking mods that keep their settings in another file do not read it. Changing a setting in `Defaults.ini` changes it in every game that has it set to `default`. Writing a value in place of `default` changes that setting for this game only. When the mod saves a setting that a hotkey changed in game, it writes the new value in place of `default`, so that setting no longer follows `Defaults.ini` in this game until you set it to `default` again.

`Defaults.ini` is `%AppData%\CameraUnlock\Defaults.ini` on Windows; `$XDG_CONFIG_HOME/CameraUnlock/Defaults.ini` on Linux, or `~/.config/CameraUnlock/Defaults.ini` where `XDG_CONFIG_HOME` is not set, under Wine and Proton too; and `~/Library/Application Support/CameraUnlock/Defaults.ini` on macOS. The mod's log, where it writes one, names the file it read.

When the mod starts and finds no `Defaults.ini`, it creates one holding the built-in values, unless Windows runs the game as a packaged app. The mod never changes `Defaults.ini` after that. Edit it with any text editor.

The built-in value of each setting set to `default` below:

- `UdpPort=4242`
- `EnableOnStartup=true`
- `WorldSpaceYaw=true`
- `RotationEnabled=true`
- `DataFreshnessMs=500`
- `LocalSmoothing=0.0`
- `RemoteSmoothing=0.15`
- `PositionEnabled=true`
- `TrueFreeLook=false`
- `FreeLookMarker=false`
- `StockSights=false`
- `PositionLimitX=0.3`
- `PositionLimitY=0.2`
- `PositionLimitYDown=0.2`
- `PositionLimitZ=0.4`
- `PositionLimitZBack=0.1`
- `CollisionEnabled=true`
- `CollisionReleaseSmoothing=0.9`
- `LightMultiplier=1.5`

With every setting at its default, the file reads:

```ini
; Project Zomboid head tracking settings.
; Comments start with ; and go on their own line. Text after a value is part of the value.
; Hotkeys are key names such as End, PageUp or Ctrl+Shift+Y. Separate several with commas; leave empty for none.
; A setting set to default takes its value from Defaults.ini, which every head tracking mod
; that keeps its settings in CameraUnlock.ini reads: %AppData%\CameraUnlock\Defaults.ini on
; Windows, $XDG_CONFIG_HOME/CameraUnlock/Defaults.ini (normally ~/.config/CameraUnlock) on
; Linux, under Wine and Proton too, and ~/Library/Application Support/CameraUnlock/Defaults.ini
; on macOS. The log names the file it read. Change a setting in Defaults.ini to change it in
; every game that has it set to default, or write a value here instead of default to change it
; for this game only.

[CameraUnlock]
; Written by the mod. Leave this section in place.
ConfigFormat=1

[Network]
; UDP port the mod receives tracker data on (OpenTrack protocol).
UdpPort=default
; UDP port the mod receives a second player's tracker data on, for two players at
; one screen (OpenTrack protocol). The first player's tracker is on UdpPort, and the
; two have to differ.
SecondPlayerUdpPort=4243

[General]
; true: head tracking is on when the game starts. false: it is off. No key turns it on or off.
EnableOnStartup=default
; true: yaw turns around the world's up axis. false: around the camera's own up axis.
WorldSpaceYaw=default
; true: turning your head turns the view.
; Tracking mode at startup, with PositionEnabled. The mode hotkey changes both.
RotationEnabled=default
; Milliseconds a tracker packet stays current. Once the tracker has sent nothing
; for this long, the mod stops following it until data arrives again.
DataFreshnessMs=default
; Field of view of the first person view in degrees, from the bottom of the picture to
; the top: 40 to 110. A wider screen shows more to the sides. Project Zomboid has no
; field of view setting of its own. Aiming a firearm still narrows the view by the
; same ratio. Read again while the game runs, so a change shows as soon as the file
; is saved.
FieldOfView=65.0
; The graphics mode: how much of the mod's own picture is drawn. CycleQualityKey goes
; from one to the next, Automatic, Low, Medium, High, Custom, and saves it here.
; Automatic, which a first start is in: begins at High and draws less, as Medium and
; then Low do but still with everything the mod made, and last as Low itself, each
; time the picture is drawn fewer than 45 times a second for three seconds. It draws
; more again, a level at a time, once the picture has been drawn 57 times a second or
; more for fifteen seconds, though not a level it left in the last two minutes: a
; wait that doubles, up to 32 minutes, each time a level it went back up to was too
; slow within a minute. Under a frame limit set in the game's options, the two rates
; are at most four fifths and nineteen twentieths of the limit. Low is as near
; the game's own look as there is: the game's own view is the game's own picture, and
; first person is drawn with the game's own sprites, models and textures in place of
; everything the mod made but the arms on a held weapon, which OwnArms switches, at a
; little over three fifths of the pixels each way, sight to 48 tiles, smaller shadow
; maps, and no shading of corners, lit air, reflections or depth in surfaces. Medium
; and High draw both views with what the mod made. High: the
; whole picture. Medium: the game's own grass, trees, bushes and rocks whatever
; OwnPlants says, no shading of corners, lit air, reflections or depth in surfaces
; either, four fifths of the pixels each way, sight to 64 tiles and smaller shadow
; maps. Custom: the picture the rows of [Graphics] describe. A change to any of those
; rows on the game's options screen, or in this file while the game runs, goes to
; Custom. Read again while the game runs.
Quality=Automatic
; How the mod's picture is developed, in every graphics mode. CycleStyleKey goes
; from one to the next and saves it here. Vivid: strong contrast and full colours,
; cool in the shade and warm in the sun, with dark corners, glare off the sun and a
; glow round bright lights. Natural: the colours as they are lit, with no tint,
; less contrast, and less of the dark corners, the glare and the glow. Bleak:
; washed out colours and low contrast under a faint drab cast, with no glare off
; the sun, nearer the colours of the game's own view. The light, the weather and
; the hour are the same in all three. Read again while the game runs.
Style=Vivid
; How high the hills stand in first person. The game's map is flat, and in first
; person the mod gives the land a lie of its own: the country rising and falling,
; hills, higher and more of them in farmland and woods than in town, swells that
; streets go over, and ground uneven underfoot. 1: as the mod makes it. 0: as level
; as the game's map. Up to 1.5: that many times as high, where the
; steepest main road climbs about one in five and a half. A kerb, a bank down to water
; and how far water lies below its banks are the same at every height. The game's
; own view from overhead is always level, and on a
; server where players can fight one another the hills stay at 1. Read again
; while the game runs.
HillHeight=1.0
; Whether each frame of the first person picture is mixed with the one before it.
; false: every frame is drawn on its own. true: fine edges, leaves and the grain of
; soft shadows are smoother and steadier, and the whole picture is a little softer,
; as much standing still as while you turn or walk. Read again while the game runs.
FrameBlending=false
; true: a shot from a firearm in first person flashes at the muzzle: a small bloom
; of fire ahead of the barrel for a frame or two, and a moment's light on what is
; near. false: no flash and no light from it. Read again while the game runs.
MuzzleFlash=true
; How two players at one screen share it. SplitScreenKey goes from one to the other
; and saves it here. Vertical: side by side, each half the width and the whole
; height, as the game has it. Horizontal: player 1 above player 2, each the whole
; width and half the height. One player has the whole screen and three or four have
; a quarter each, as the game has it, whichever this says. Read again while the game
; runs.
SplitScreen=Vertical

[Smoothing]
; Smoothing when the tracker runs on this PC. 0 is the least, 1 the most.
LocalSmoothing=default
; Smoothing when the tracker is another device on the network, such as a phone.
; 0 is the least, 1 the most.
RemoteSmoothing=default

[Position]
; true: moving your head moves the view.
; Tracking mode at startup, with RotationEnabled. The mode hotkey changes both.
PositionEnabled=default
; false: while you aim down the sights, leaning keeps your eye on the sights.
; true: the weapon stays put and your head moves freely around it (free look), and
; the game's reticle shows where your shot will land.
TrueFreeLook=default
; The aim marker of free look. In Project Zomboid the game's own reticle is that
; marker and is drawn whenever you aim, so this row changes nothing: the mod
; writes it with TrueFreeLook.
FreeLookMarker=default
; true: while you aim down the sights your head stops moving the view, apart from tilting it,
; so the sights sit in the centre as they do without head tracking. At the hip nothing changes.
StockSights=default
; How far, in metres, leaning left or right can move the view.
PositionLimitX=default
; How far, in metres, raising your head can move the view.
PositionLimitY=default
; How far, in metres, lowering your head can move the view.
PositionLimitYDown=default
; How far, in metres, leaning forward can move the view.
PositionLimitZ=default
; How far, in metres, leaning back can move the view.
PositionLimitZBack=default
; true: leaning stops at walls instead of moving the view through them.
; Only games whose mod sweeps the level for walls read this; the rest ignore it.
CollisionEnabled=default
; How far the view is held off a wall when you lean into it, in map tiles.
CollisionMargin=0.12
; How gently the view eases back out after a wall stopped a lean.
; 0 is the quickest, 1 the slowest.
CollisionReleaseSmoothing=default

[Hotkeys]
; Changes the tracking mode: rotation and position, rotation only, position only.
CycleTrackingModeKey=PageUp
; Switches yaw between the world's up axis and the camera's own (WorldSpaceYaw).
YawModeKey=PageDown
; Cycles the aim mode: sights locked, free look with a marker, stock sights
; (TrueFreeLook, FreeLookMarker, StockSights).
TrueFreeLookKey=Insert
; Goes into the first person view and back to the game's own. Tapped, for the first
; player. Tapped twice, for a second player at the same screen. Held for 400 ms,
; for both.
FirstPersonKey=Delete
; Goes to the next graphics mode: Automatic, Low, Medium, High, Custom (Quality).
CycleQualityKey=F9
; Goes to the next picture style: Vivid, Natural, Bleak (Style).
CycleStyleKey=F8
; With two players at the screen, goes between side by side and one above the other
; (SplitScreen). Does nothing with any other number of players.
SplitScreenKey=F7

[Light]
; How far the light turns for each degree your head turns.
; 1 matches the view, 0 keeps the light on your aim.
LightMultiplier=default

[Graphics]
; The rows of this section are the Custom graphics mode: what is drawn while Quality is
; Custom. Until you change them they are High's. Low, Medium and High draw their own
; picture and leave these rows as they are. Changing any of them on the game's options
; screen, or here while the game runs, sets Quality to Custom. On the options screen
; they show the picture of the mode you are in, and what you apply there is kept here.
; Read again while the game runs.
; true: as near the game's own look as there is, as in Low: the game's own view is the
; game's own picture, and first person is drawn with the game's own sprites, models and
; textures in place of everything the mod made but the arms on a held weapon, which
; OwnArms switches. false: both views are drawn with what the mod made, as the rows of
; [Content] say.
StockLook=false
; true: grass, trees, bushes and rocks are the game's own sprites whatever OwnPlants
; says, as in Medium. The mod's are the dearest thing in the picture to draw. false:
; as OwnPlants says.
SpritePlants=false
; How many of the first person picture's pixels there are to each of the window's, each
; way: 0.5 to 1. Low draws 0.62, Medium 0.8 and High 1.
RenderScale=1.0
; true: shadow maps 4096 texels across, as in High. false: 2048, as in Low and Medium,
; which take a quarter of the memory on the graphics card.
SharpShadows=true
; How far the first person view is built and drawn, in tiles, where the loaded map
; reaches that far: 32 to 80. It is the land, the buildings and the plants, and as far
; as anything is drawn. Low draws 48, Medium 64 and High 80.
ViewDistance=80
; How far off zombies, people, vehicles and the furniture, appliances and decor the mod
; has models of are drawn in first person, in tiles: 16 to 80. It never reaches
; past ViewDistance, which is how far the land and the buildings go: at ViewDistance or
; over it they are drawn as far as the view goes. Lower, what stands further off is
; left out, which costs less to draw. Low draws 48, Medium 64 and High 80, each its own
; view distance. On a server where players can hurt each other it leaves nothing out.
ZombiesAndPropsDrawDistance=80
; ZombiesAndPropsDrawDistance ahead of a vehicle you ride in: how far off on the side
; it faces zombies, people, vehicles and props are drawn, in tiles: 16 to 80. Never
; past ViewDistance. Behind and beside the vehicle, and on foot,
; ZombiesAndPropsDrawDistance is what counts. Low draws 48, Medium 64 and High 80. On a
; server where players can hurt each other it leaves nothing out.
ForwardDrivingDrawDistance=80
; How much of the rain on the windows of your vehicle is drawn. Low: drops of one size.
; Medium: specks among them, and trickles down the panes no wiper reaches. High: three
; sizes of drop, and drops that run down and leave a line of wet behind them.
RainOnGlass=High
; true: corners are shaded and lent the light of what stands near and the air is lit, as
; in High, and with Reflections true wet roads, polished floors, glass and water mirror
; the world. false: none of these, as in Low and Medium.
ShadingAndLight=true
; true: the mirrors of a vehicle you sit in show the road behind, as in every mode, and
; with ShadingAndLight true wet roads, polished floors, glass and water mirror the
; world, as in High. false: none of that. A vehicle's mirrors are plain glass, and the
; road behind is not drawn for them, which costs nothing, and water shows the sky alone,
; as in Low and Medium.
Reflections=true
; true: the mod's pictures of surfaces, on walls and of skin are made at their full size,
; as in High. false: at half their width and height, a quarter of what they take on
; the graphics card and in the PC's memory.
FullSizeTextures=true
; true: every tree and bush is the mod's own as far as the view is built, as in High.
; false: one further off than 40 tiles is the game's sprite on a fan of cards,
; which costs less and changes to the mod's as you near it.
FarTrees=true
; true: near surfaces are given the depth of what they show, as in High. false: they are
; drawn flat.
SurfaceDepth=true

[Content]
; What the mod made itself and shows in place of the game's own. Set a row to false
; and the game's own is drawn there instead, as the game or another mod has it.
; These rows are read again while the game runs, so a change shows as soon as the
; file is saved. In Medium and High they apply to the game's own view too, which
; the mod draws there.
; true: the mod's models of the pump shotgun, the hunting rifle and rifle scopes.
; false: the game's own weapon and scope models, and no picture through the scope.
OwnWeapons=true
; true: the mod's models of furniture, appliances, street lamps, clocks and picture
; frames, banners hung as cloth, and bunting and flags laid on their wall. false:
; the game's sprites of them.
OwnFurniture=true
; true: the mod's photographs, paintings, posters and signs on walls, and programmes on a
; television that is on. false: the game's pictures, and only colour bars on a
; television.
OwnPictures=true
; true: the mod's leaves, tree trunks, boulders, grass, ferns, flowers and fallen
; leaves. false: the game's sprites of trees, bushes and grass.
OwnPlants=true
; true: the mod's textures of tarmac, gravel, earth, lawn, wood, boards, roofing,
; paint, carpet, cloth and wallpaper, and the lines painted on roads drawn as paint
; with clean edges. false: the colours of the game's sprites alone, and the lines
; as the game's sprites have them.
OwnTextures=true
; true: windows, doors, curtains and handrails built by the mod, the strings and posts
; of an open flight of stairs, and the outside of a wall finished as its building is.
; false: the game's sprites of them, on both sides of the wall.
OwnBuildings=true
; true: the mod's own skins for zombies near the eye, each with a lifelike face.
; false: the game's own painted skins.
OwnZombies=true
; true: the mod's own skins for your character and the other living, in the game's
; five skin tones, each with a lifelike face. false: the game's own painted skins.
OwnPeople=true
; true: arms and hands of the mod's own on a held weapon, with fingers that close on
; it and go to its bolt, slide, magazine and loading port as it is worked and loaded,
; in the skin, sleeves and gloves your character has, in every graphics mode, Low
; included. false: the game's own arms and hands as the game poses them, moved with
; the weapon to before the eye: for another mod that changes the arms or the weapons.
OwnArms=true
; true: what people and zombies wear is made finer near the eye: the game's picture
; of each garment and head of hair enlarged with its outline smoothed, and the weave,
; knit, grain or strands of what it is made of laid on from the mod's own photographs.
; false: the game's own pictures as they are.
OwnClothes=true
```
<!-- /cameraunlock:config -->

### Aiming down sights

Head tracking stays on while you aim, unless you pick stock sights below. The
weapon stays where your mouse points it, so with your head turned it sits off to
one side with its sights still lined up, and your rounds land where those sights
point. Head movement is scaled to the zoom, so the narrowed view does not
magnify it.

`Insert` cycles three ways of aiming, and the mod saves the one
you pick, so it holds the next time you start the game:

1. **Sights locked** (default) - leaning never takes your eye off the sights,
   and leaning in towards them brings them closer.
2. **Free look with a marker** - the weapon stays put and your head moves freely
   around it, so the sights only line up with your head behind them. The game's
   own reticle is the marker: it shows where your rounds will land while the
   sights are up.
3. **Stock sights** - while the sights are up your head stops turning and
   leaning the view, so the sights sit in the center as they do without head
   tracking. Tilting your head still tilts the view. When you lower the weapon
   the view goes back to where you are looking.

Leaning carries on through the aim. As the sights come up, your arms and weapon
move with your head, so the sights stay in front of your eye, and your rounds
leave from where your eye is and land where the sights point. Leaning in stops
just short of the rear sight.

On a server where players can hurt each other the lean across the aim eases out
while the sights are up and does not move the shot: see [On a server](#on-a-server).

The mode is shown on the screen and logged each time it changes, as `Aim mode: sights locked` and so on.

#### Through a scope

A scope shows its whole picture only to an eye on its axis, behind the
eyepiece and no further back than a fixed distance. What you see through it
depends on where your eye is:

- **Sights locked** keeps your eye there. Lean and turn your head as you like:
  the rifle comes with your eye and the picture stays whole. With your head
  turned the scope is off to one side, and its reticle is still on the point
  your rounds go to.
- **In free look** the rifle stays put and your head has to be
  behind the scope. To one side of the axis the picture dims and then goes
  dark. Too far back, a shadow closes in from its edge, from one side first
  if your head is off to that side as well. What is dark is the inside of the
  tube.
- **Stock sights** puts your eye on the axis whenever the sights are up.

A weaker scope forgives more. The picture is whole with your head within about
3 mm of the axis of the x2, 1 mm of the x4 and a quarter of a millimetre of the
x8, and gone at 9 mm, 7 mm and 6 mm.

Leaning in brings the eyepiece nearer, and the picture grows with it: it stays
whole, fills the glass as it did before, and shows what is in it larger. The
lean is stopped about 5 cm in, just short of the glass, where the picture is a
little over three times as large as it was. The mouse turns your aim that much
less, so the reticle still crosses what it is on as fast as it did. No shadow
closes in for being near.

The reticle is in the same plane as the picture, so moving your eye does not
move it across the target. In sights locked and in stock sights the game's own reticle
is left out behind a scope: the scope's is on the point your rounds go to. In
free look it stays, because there the scope's reticle is true only with
your eye behind the scope, and the game's is the mark of where the rounds go.

The picture in the scope is the same world in the same light, without rain and
without the glass of windows. It shows other people only as far off as the
game draws them.

## Troubleshooting

Read the log first. The mod writes `ProjectZomboidHeadTracking.log` next to `ProjectZomboid64.exe`, starting it again on each launch and keeping the launch before as `ProjectZomboidHeadTracking.prev.log`.

**Mod not loading**

- If there is no log after a launch, the Java agent was not loaded. Check that `ProjectZomboidHeadTracking.jar` and `ProjectZomboid64.site.json` are both next to the exe, and run `install.cmd` again if either is missing.
- If `install.cmd` stops because a `ProjectZomboid64.site.json` that does not load this mod is already there, that file belongs to you or to another mod and the installer will not overwrite it. Make the three changes to it by hand (see [Manual Installation](#manual-installation)).
- If `Delete` does nothing, the log says why on a `first person stays off:` line.
- If the log is one `could not start, and the game runs without it` entry, `ProjectZomboidHeadTracking.jar` and `CameraUnlockCore.dll` next to the exe are not from the same release, or the DLL is missing. The entry says which. Run `install.cmd` from one release again.
- If the log has a `the mod stays out of the game:` line, a game update changed something the mod uses and the game is running without the mod. The `not in this build of the game as the mod uses it:` lines above it name what changed. The mod needs an update for that build of the game.

**No tracking response**

- Head tracking only moves the view in first person. Press `Delete` once you are in a game. In the game's own view it turns your character's head and nothing else.
- Look for `Listening for OpenTrack datagrams on UDP port 4242` in the log. A `Failed to bind UDP port 4242` line in its place means the port could not be opened, and it carries the error number Windows gave. Error 10048 is another program holding the port, usually another game with a head tracking mod that is still running: close that program. The mod tries the port again every half second, with no restart needed, and logs `Bound UDP port 4242` when it has it. Error 10013 is a port Windows has reserved, which no program holds: if `netsh int ipv4 show excludedportrange protocol=udp` lists a range containing the port, set `UdpPort` in `CameraUnlock.ini` and your tracker to a port outside the listed ranges.
- Look for `First UDP packet received` next. Without it no packets are arriving, so check the tracker's destination address and port, and that it is started. The mod listens on IPv4, so a tracker set to send to `::1` or another IPv6 address reaches nothing: send to `127.0.0.1`.
- The mod follows one tracker at a time. If two programs send to the port, it stays with the first it heard until that one has been silent for two seconds, and the log has a `a SECOND tracker source is sending to this port` line naming both. Stop the one you do not mean.
- Check `EnableOnStartup` in `CameraUnlock.ini` is not `false`. The log's start line says `head tracking on` or `head tracking off`.

**Jittery or unstable tracking**

- Send a noisy phone feed through OpenTrack and use its filter (see [Phone App Setup](#phone-app-setup)).
- With a webcam, improve the lighting on your face and raise the filter strength in OpenTrack.

**Aiming**

- **The weapon is off to one side when I aim down sights.** Your head is turned: the weapon stays on your aim and you are looking past it. Turn back to it, or move your aim to where you are looking.
- **I can't see down the sights, they are misaligned.** You are in free look and your head is leaned off them. Move your head back behind them, or press `Insert` until the screen says sights locked.
- **The scope is black, or has a shadow down one side.** You are in free look and your eye is off the scope's axis, or too far behind it. Move your head until the picture is whole, or press `Insert` until the screen says sights locked. A kick or a quick turn takes the scope off your eye for a moment in any mode.
- **The view swings when I raise or lower the sights.** You are in stock sights with your head turned: the view goes to your aim while the sights are up and back to where you are looking when they come down. Press `Insert` for another mode if you want head tracking to carry on through the aim.
- **I lean round a corner and nobody is there.** The game shows you a zombie only when your character can see it from where it stands, and leaning moves your eye and your weapon, not your character. Step out until your character is past the corner.

**Wrong rotation axis or direction**

- Fix it in the tracker, by swapping or inverting the axis in OpenTrack's output mapping. The mod applies the pose as it arrives.
- If the view sits off to one side, center it in the tracker.

**Yaw feels wrong when looking up or down at extreme angles**

- Try toggling between world-locked and camera-local yaw with `Page Down`. World-locked (default) is horizon-stable; camera-local follows the camera's current up-axis.

## Updating

Download the new release and run `install.cmd` again. Your config is preserved.

## Uninstalling

Run `uninstall.cmd`. This removes the mod's jar and DLL, the `ProjectZomboid64.site.json` the installer wrote, and the logs, and leaves `CameraUnlock.ini` and the game's own files as they were. The mod uses no mod loader, so `uninstall.cmd /force`, which removes a loader the installer did not put there, has nothing further to remove.

## Building from Source

Prerequisites: Windows, Git, [pixi](https://pixi.sh), CMake, Visual Studio with the C++ workload, and Node.js for `pixi run package`. Pixi provides the JDK; CMake and Visual Studio build `CameraUnlockCore.dll` from the cameraunlock-core submodule, and the build needs no game install.

```powershell
git clone --recurse-submodules https://github.com/itsloopyo/project-zomboid-headtracking.git
cd project-zomboid-headtracking
pixi run build      # compiles the jar and the DLL into build\
pixi run test       # unit tests
pixi run package    # installer ZIP in release\, and a -dev ZIP with the development keys in
pixi run install    # deploys the development jar, with the development keys in, to every installed copy of the game
```

## Community & Support

- Discord: [Loop's Head Tracking Hangout](https://discord.com/invite/dxyZdyFNT9) - setup help, bug reports, and new-release announcements
- [Lopari](https://lopari.app) - free Windows launcher with one-click install and launch for the released head-tracking mods
- [Headcam](https://headcam.app) - free app that turns your iPhone or Android phone into the head tracker

## License

MIT License - see [LICENSE](LICENSE) for details.

## Credits

- [The Indie Stone](https://theindiestone.com) - developer and publisher of Project Zomboid. Thanks to The Indie Stone for creating [Project Zomboid](https://projectzomboid.com/), which made this possible. This is an unofficial fan production for non-commercial purposes made under the [Indie Stone Terms](https://projectzomboid.com/blog/support/terms-conditions/).
- [OpenTrack](https://github.com/opentrack/opentrack) - the head tracking software and UDP protocol the mod reads
- [LWJGL](https://www.lwjgl.org) - the OpenGL and window bindings the game ships, which the first person view draws through
- [cameraunlock-core](https://github.com/itsloopyo/cameraunlock-core) - the tracker receiver, the pose pipeline, the settings file and the hotkeys, as the `CameraUnlockCore.dll` the mod loads, and the shared installer scripts
- [Hash without Sine](https://www.shadertoy.com/view/4djSRW) by David Hoskins (MIT) - the hash functions behind the grain of skin, hair and cloth, the shade of each brick and the lie of settled snow
- [glsl-fxaa](https://github.com/mattdesl/glsl-fxaa) (BSD-3-Clause) - the FXAA pass that smooths edges in the first person picture
- [Stable Diffusion XL](https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0) - the model the surface textures and the pictures on walls were generated with, from text prompts alone

The licence notices for these are in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

## Disclaimer

This mod is not affiliated with, endorsed by, or supported by The Indie Stone. Use at your own risk.
