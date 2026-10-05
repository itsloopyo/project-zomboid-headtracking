# Project Zomboid Head Tracking

![Project Zomboid running with this mod](https://raw.githubusercontent.com/itsloopyo/project-zomboid-headtracking/main/assets/readme-clip.gif)

An unofficial head tracking mod for Project Zomboid that moves the view with your head while your mouse keeps aiming, driven by a webcam, phone, or any OpenTrack compatible tracker, with no VR headset required.

## Features

- **Decoupled look and aim** - your head moves the view, your mouse keeps the aim
- **6DOF tracking** - yaw, pitch and roll plus positional lean, peek and duck
- **Works with any OpenTrack compatible tracker** - free options available for PC, iOS and Android
- **First person view** - `F8` switches from the game's isometric view to a first person one, which is where head tracking applies
- **First person controls** - left mouse attacks at once, right mouse aims down the sights, `Left Shift` sprints and `Left Ctrl` crouches
- **A weapon in your hands** - in first person a firearm sits at the hip and comes up to the eye when you aim, sways and kicks, and flashes at the muzzle when it fires
- **A pointer when you need one** - the inventory key, any window or menu of the game's, or the middle mouse button held, hands the mouse back as a pointer without leaving first person
- **A first person picture of its own** - sun, moon, shadows, clouds, rain, mirrored water, hills, standing grass and trees in leaf, drawn from the game's own map and sprites. The isometric view is left exactly as the game draws it, and no game file is changed

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
5. Launch the game, load a single player save and press `F8`.

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

1. Copy `plugins\ProjectZomboidHeadTracking.jar` and `plugins\ProjectZomboidHeadTracking.dll` from the installer ZIP into the game folder, next to `ProjectZomboid64.exe`.
2. In the same folder, copy `ProjectZomboid64.json` to a new file named `ProjectZomboid64.site.json`. The game's launcher reads the site file in place of the stock one when it is there.
3. Open `ProjectZomboid64.site.json` and make three changes:
   - Note the value of `mainClass`, then change it to `"com/cameraunlock/zomboid/Boot"`.
   - Add `"ProjectZomboidHeadTracking.jar"` as the last entry of the `classpath` list.
   - Add `"-Dcameraunlock.mainClass=zombie/gameStates/MainScreenState"` and `"-XX:+EnableDynamicAgentLoading"` as the first two entries of the `vmArgs` list. The part after `=` is the `mainClass` value you noted.

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

`F8` switches between the game's own view and the first person view. In first person the mouse turns the view and the movement keys follow it. The first person view is for single player: it stays off in multiplayer and split screen.

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
- `Left Alt` is also the game's default for an attack aimed at the ground, so an attack made with the jog held is aimed down.

None of this is written to your key bindings. The game's own view, a paused game and the options screen all have your bindings as you saved them. If the game's **toggle to aim** option is on, the left mouse button works as it does in the game: set the aim first, then click.

### The weapon in your hands

With a weapon in hand and on foot, first person draws it and the arms that hold it before the eye instead of where the game's animation carries them:

- **A firearm** sits low and to the right at the hip, pointing at the place the shot will land. Holding the aim brings it up onto your aim as the view narrows. It stays on your aim when you turn your head: look away and it is off to one side, still pointing at the reticle. Your left arm comes with it when the weapon is held in both hands.
- **A melee weapon** is held up at the lower right. A swing is the game's own attack animation, seen from the eye.
- The weapon trails a turn of the view by a few degrees and catches up, sways with each step, drops across the body while you sprint, and dips when you crouch or land.
- **A shot** kicks the weapon back and up for about a sixth of a second, throws a flash from the muzzle and lights the people and zombies round it for the length of the flash.
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
- A click on the world goes to what the game's own view has under the pointer, which is drawn beneath the first person picture and is not what the picture shows there. `E` acts on what your character faces and is the surer way to use a door or a window.
- `Tab` and every other key are as the game has them.
- If you have bound something to the middle mouse button, it does both.

### Head tracking

The two columns below do the same things. Use whichever your keyboard has. In first person `Left Ctrl` and `Left Shift` also crouch and sprint; the right hand `Ctrl` and `Shift` work for the chords and do neither.

| Action | Nav cluster | Chord |
|--------|-------------|-------|
| Toggle tracking | `End` | `Ctrl+Shift+Y` |
| Cycle tracking mode | `Page Up` | `Ctrl+Shift+G` |
| Toggle yaw mode | `Page Down` | `Ctrl+Shift+H` |
| Cycle aim mode | `Insert`    | `Ctrl+Shift+U` |

- **Tracking mode** cycles rotation and position, rotation only, then position only. Rotation only is how you turn the positional lean off.
- **Yaw mode** switches head yaw between turning about straight up and turning about the view's own up.

- **Aim mode** is how head tracking behaves while a firearm's sights are up. See [Aiming down sights](#aiming-down-sights).

The tracking mode, the yaw mode and the aim mode are saved when you change them. Turning tracking off with `End` lasts until the game closes.

Tracking applies while the first person view is on, the game is not paused and your character is alive.

## Configuration

Two values are built in and are not settings:

| Value | Built in | What it does |
|-------|----------|--------------|
| Field of view | `65` degrees | Measured from the bottom of the picture to the top |
| Field of view, firearm aimed | `50` degrees | 0.73 of the field of view's half tangent, a magnification of 1.37 |

The tracking mode, the yaw mode and the aim mode are saved to `CameraUnlock.ini` when their keys change them. `End` is not: head tracking starts as `EnableOnStartup` says every time. An uninstall leaves the file in place.

`LocalSmoothing` applies to a tracker sending from `127.0.0.1` and `RemoteSmoothing` to one sending from any other address, this PC's own network address included. Both smoothing values cover rotation and position. A lean is also held back from walls, so the view does not end up inside one.

A flashlight you carry follows your head and not your aim, and turns a little further than the view does. When you turn your head your eyes end up past the center of the screen, so a beam matched to the view alone lands short of what you are looking at.

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
- `ToggleKey=End, Ctrl+Shift+Y`
- `CycleTrackingModeKey=PageUp, Ctrl+Shift+G`
- `YawModeKey=PageDown, Ctrl+Shift+H`
- `TrueFreeLookKey=Insert, Ctrl+Shift+U`
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

[General]
; true: head tracking is on when the game starts. ToggleKey turns it on and off.
EnableOnStartup=default
; true: yaw turns around the world's up axis. false: around the camera's own up axis.
WorldSpaceYaw=default
; true: turning your head turns the view.
; Tracking mode at startup, with PositionEnabled. The mode hotkey changes both.
RotationEnabled=default
; Milliseconds a tracker packet stays current. Once the tracker has sent nothing
; for this long, the mod stops following it until data arrives again.
DataFreshnessMs=default

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
; true: the weapon stays put and your head moves freely around it (true free look).
TrueFreeLook=default
; true, with TrueFreeLook=true: an aim marker shows where your shot will land while you aim down the sights.
; It does nothing while TrueFreeLook is false.
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
; Turns head tracking on and off.
ToggleKey=default
; Changes the tracking mode: rotation and position, rotation only, position only.
CycleTrackingModeKey=default
; Switches yaw between the world's up axis and the camera's own (WorldSpaceYaw).
YawModeKey=default
; Cycles the aim mode: sights locked, free look with a marker, true free look, stock sights
; (TrueFreeLook, FreeLookMarker, StockSights).
TrueFreeLookKey=default

[Light]
; How far the light turns for each degree your head turns.
; 1 matches the view, 0 keeps the light on your aim.
LightMultiplier=default
```
<!-- /cameraunlock:config -->

### Aiming down sights

Head tracking stays on while you aim, unless you pick stock sights below. The
weapon stays where your mouse points it, so with your head turned it sits off to
one side with its sights still lined up, and your rounds land where those sights
point. Head movement is scaled to the zoom, so the narrowed view does not
magnify it.

`Insert` / `Ctrl+Shift+U` cycles four ways of aiming, and the mod saves the one
you pick, so it holds the next time you start the game:

1. **Sights locked** (default) - leaning never takes your eye off the sights,
   and leaning in towards them brings them closer.
2. **Free look with a marker** - the weapon stays put and your head moves freely
   around it, so the sights only line up with your head behind them. The game's
   own reticle shows where your rounds will land while the sights are up.
3. **True free look** - the same. The game draws its reticle whenever the aim is
   held, so in this game the two free look modes look alike.
4. **Stock sights** - while the sights are up your head stops turning and
   leaning the view, so the sights sit in the centre as they do without head
   tracking. Tilting your head still tilts the view. When you lower the weapon
   the view goes back to where you are looking.

Leaning carries on through the aim. As the sights come up, your arms and weapon
move with your head, so the sights stay in front of your eye, and your rounds
leave from where your eye is and land where the sights point. Leaning in stops
just short of the rear sight.

The mode is logged each time it changes, as `Aim mode: sights locked` and so on.

## Troubleshooting

Read the log first. The mod writes `ProjectZomboidHeadTracking.log` next to `ProjectZomboid64.exe`, starting it again on each launch.

**Mod not loading**

- If there is no log after a launch, the Java agent was not loaded. Check that `ProjectZomboidHeadTracking.jar` and `ProjectZomboid64.site.json` are both next to the exe, and run `install.cmd` again if either is missing.
- If `install.cmd` stops because a `ProjectZomboid64.site.json` that does not load this mod is already there, that file belongs to you or to another mod and the installer will not overwrite it. Make the three changes to it by hand (see [Manual Installation](#manual-installation)).
- If `F8` does nothing, the log says why on a `first person stays off:` line.
- If the log has a `the mod stays out of the game:` line, a game update changed something the mod uses and the game is running without the mod. The `not in this build of the game as the mod uses it:` lines above it name what changed. The mod needs an update for that build of the game.

**No tracking response**

- Head tracking only applies in the first person view. Press `F8` in a single player game.
- Look for `head tracking listening on UDP port 4242` in the log. A `head tracking cannot open UDP port 4242` line means another program holds the port: close it. The mod tries the port again every half second and logs the listening line when it has it.
- Look for `first tracking packet from` next. Without it no packets are arriving, so check the tracker's destination address and port, and that it is started.
- Press `End` in case tracking was toggled off. The log records `head tracking on` or `head tracking off` each time.

**Jittery or unstable tracking**

- Send a noisy phone feed through OpenTrack and use its filter (see [Phone App Setup](#phone-app-setup)).
- With a webcam, improve the lighting on your face and raise the filter strength in OpenTrack.

**Aiming**

- **The weapon is off to one side when I aim down sights.** Your head is turned: the weapon stays on your aim and you are looking past it. Turn back to it, or move your aim to where you are looking.
- **I can't see down the sights, they are misaligned.** You are in one of the free look modes and your head is leaned off them. Move your head back behind them, or press `Insert` / `Ctrl+Shift+U` until the log says sights locked.
- **The view swings when I raise or lower the sights.** You are in stock sights with your head turned: the view goes to your aim while the sights are up and back to where you are looking when they come down. Press `Insert` / `Ctrl+Shift+U` for another mode if you want head tracking to carry on through the aim.

**Wrong rotation axis or direction**

- Fix it in the tracker, by swapping or inverting the axis in OpenTrack's output mapping. The mod applies the pose as it arrives.
- If the view sits off to one side, center it in the tracker.

**Yaw feels wrong when looking up or down at extreme angles**

- Try toggling between world-locked and camera-local yaw with `Page Down`. World-locked (default) is horizon-stable; camera-local follows the camera's current up-axis.

## Updating

Download the new release and run `install.cmd` again.

## Uninstalling

Run `uninstall.cmd`. This removes the mod's jar, the `ProjectZomboid64.site.json` the installer wrote, and the log, and leaves the game's own files as they were. The mod uses no mod loader, so `uninstall.cmd /force`, which removes a loader the installer did not put there, has nothing further to remove.

## Building from Source

Prerequisites: Windows, Git, [pixi](https://pixi.sh), CMake and Visual Studio with the C++ workload. Pixi provides the JDK, CMake and Visual Studio build the DLL that reads the settings file, and the build needs no game install.

```powershell
git clone --recurse-submodules https://github.com/itsloopyo/project-zomboid-headtracking.git
cd project-zomboid-headtracking
pixi run build      # compiles the jar and the DLL into build\
pixi run test       # unit tests
pixi run package    # installer ZIP in release\, and a -dev ZIP with the development keys on
pixi run install    # deploys to every installed copy of the game
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
- [cameraunlock-core](https://github.com/itsloopyo/cameraunlock-core) - shared installer scripts

## Disclaimer

This mod is not affiliated with, endorsed by, or supported by The Indie Stone. Use at your own risk.
