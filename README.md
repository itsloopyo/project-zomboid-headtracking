# Project Zomboid Head Tracking

![Project Zomboid running with this mod](https://raw.githubusercontent.com/itsloopyo/project-zomboid-headtracking/main/assets/readme-clip.gif)

An unofficial head tracking mod for Project Zomboid that moves the view with your head while your mouse keeps aiming, driven by a webcam, phone, or any OpenTrack compatible tracker, with no VR headset required.

## Features

- **Decoupled look and aim** - your head moves the view, your mouse keeps the aim
- **6DOF tracking** - yaw, pitch and roll plus positional lean, peek and duck
- **Works with any OpenTrack compatible tracker** - free options available for PC, iOS and Android
- **First person view** - `F8` switches from the game's isometric view to a first person one, which is where head tracking applies
- **First person controls** - left mouse attacks at once, right mouse aims down the sights, `Left Shift` sprints and `Left Ctrl` crouches
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

1. Copy `plugins\ProjectZomboidHeadTracking.jar` from the installer ZIP into the game folder, next to `ProjectZomboid64.exe`.
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
- **Clicks** do not act on the doors, switches and containers of the game's own view, and a right click does not open its menu: there is no pointer to use them with while the mouse turns the view.
- **Crouch** keeps your own key if you moved it, and gains `Left Ctrl`.
- Reload, rack, shove, the weapon slots, the light and interact are on your own bindings, as in the game's own view.
- `Left Alt` is also the game's default for an attack aimed at the ground, so an attack made with the jog held is aimed down.

None of this is written to your key bindings. The game's own view, a paused game and the options screen all have your bindings as you saved them. If the game's **toggle to aim** option is on, the left mouse button works as it does in the game: set the aim first, then click.

### Head tracking

The two columns below do the same things. Use whichever your keyboard has. In first person `Left Ctrl` and `Left Shift` also crouch and sprint; the right hand `Ctrl` and `Shift` work for the chords and do neither.

| Action | Nav cluster | Chord |
|--------|-------------|-------|
| Toggle tracking | `End` | `Ctrl+Shift+Y` |
| Cycle tracking mode | `Page Up` | `Ctrl+Shift+G` |
| Toggle yaw mode | `Page Down` | `Ctrl+Shift+H` |

- **Tracking mode** cycles rotation and position, rotation only, then position only. Rotation only is how you turn the positional lean off.
- **Yaw mode** switches head yaw between turning about straight up and turning about the view's own up.

These last for the session. Each launch starts with tracking on, rotation and position, and yaw about straight up.

Tracking applies while the first person view is on, the game is not paused and your character is alive.

## Configuration

The mod has no settings file. Its values are built in:

| Value | Built in | What it does |
|-------|----------|--------------|
| UDP port | `4242` | Where the mod listens for the tracker |
| Local smoothing | `0.0` | Applied to a tracker sending from `127.0.0.1` |
| Remote smoothing | `0.15` | Applied to a tracker sending from any other address, a 23.5 ms time constant |
| Lean limit, sideways | `0.30` m | Either way |
| Lean limit, up and down | `0.20` m | Either way |
| Lean limit, forward | `0.40` m | Leaning towards the screen |
| Lean limit, back | `0.10` m | Leaning away from it |
| Field of view | `65` degrees | Measured from the bottom of the picture to the top |
| Field of view, firearm aimed | `50` degrees | 0.73 of the field of view's half tangent, a magnification of 1.37 |
| Carried light | `1.5` | How far a carried light turns relative to your head |

Both smoothing values cover rotation and position. A lean is also held back from walls, so the view does not end up inside one.

A flashlight you carry follows your head and not your aim, and turns a little further than the view does. When you turn your head your eyes end up past the center of the screen, so a beam matched to the view alone lands short of what you are looking at.

Sensitivity, axis inversion, deadzones and response curves are set in your tracker, so one profile behaves the same in every game.

## Troubleshooting

Read the log first. The mod writes `ProjectZomboidHeadTracking.log` next to `ProjectZomboid64.exe`, starting it again on each launch.

**Mod not loading**

- If there is no log after a launch, the Java agent was not loaded. Check that `ProjectZomboidHeadTracking.jar` and `ProjectZomboid64.site.json` are both next to the exe, and run `install.cmd` again if either is missing.
- If `install.cmd` stops because a `ProjectZomboid64.site.json` that does not load this mod is already there, that file belongs to you or to another mod and the installer will not overwrite it. Make the three changes to it by hand (see [Manual Installation](#manual-installation)).
- If `F8` does nothing, the log says why on a `first person stays off:` line.

**No tracking response**

- Head tracking only applies in the first person view. Press `F8` in a single player game.
- Look for `head tracking listening on UDP port 4242` in the log. A `head tracking cannot open UDP port 4242` line means another program holds the port: close it. The mod tries the port again every half second and logs the listening line when it has it.
- Look for `first tracking packet from` next. Without it no packets are arriving, so check the tracker's destination address and port, and that it is started.
- Press `End` in case tracking was toggled off. The log records `head tracking on` or `head tracking off` each time.

**Jittery or unstable tracking**

- Send a noisy phone feed through OpenTrack and use its filter (see [Phone App Setup](#phone-app-setup)).
- With a webcam, improve the lighting on your face and raise the filter strength in OpenTrack.

**Wrong rotation axis or direction**

- Fix it in the tracker, by swapping or inverting the axis in OpenTrack's output mapping. The mod applies the pose as it arrives.
- If the view sits off to one side, center it in the tracker.

## Updating

Download the new release and run `install.cmd` again.

## Uninstalling

Run `uninstall.cmd`. This removes the mod's jar, the `ProjectZomboid64.site.json` the installer wrote, and the log, and leaves the game's own files as they were. The mod uses no mod loader, so `uninstall.cmd /force`, which removes a loader the installer did not put there, has nothing further to remove.

## Building from Source

Prerequisites: Windows, Git and [pixi](https://pixi.sh). Pixi provides the JDK, and the build needs no game install.

```powershell
git clone --recurse-submodules https://github.com/itsloopyo/project-zomboid-headtracking.git
cd project-zomboid-headtracking
pixi run build      # compiles the jar into build\
pixi run test       # unit tests
pixi run package    # installer ZIP in release\
pixi run install    # deploys to every installed copy of the game
```

## Community & Support

- [Discord](https://discord.com/invite/dxyZdyFNT9) - setup help, bug reports, and new-release announcements
- [Lopari](https://lopari.app) - free Windows launcher with one-click install and launch of head-tracking mods
- [Headcam](https://headcam.app) - free app that turns your phone into a head tracker

## License

MIT License - see [LICENSE](LICENSE) for details.

## Credits

- [The Indie Stone](https://theindiestone.com) - developer and publisher of Project Zomboid. Thanks to The Indie Stone for creating [Project Zomboid](https://projectzomboid.com/), which made this possible. This is an unofficial fan production for non-commercial purposes made under the [Indie Stone Terms](https://projectzomboid.com/blog/support/terms-conditions/).
- [OpenTrack](https://github.com/opentrack/opentrack) - the head tracking software and UDP protocol the mod reads
- [LWJGL](https://www.lwjgl.org) - the OpenGL and window bindings the game ships, which the first person view draws through
- [cameraunlock-core](https://github.com/itsloopyo/cameraunlock-core) - shared installer scripts

## Disclaimer

This mod is not affiliated with, endorsed by, or supported by The Indie Stone. Use at your own risk.
