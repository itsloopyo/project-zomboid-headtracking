# Project Zomboid Head Tracking

![Project Zomboid running with this mod](https://raw.githubusercontent.com/itsloopyo/project-zomboid-headtracking/main/assets/readme-clip.gif)

An unofficial head tracking mod for Project Zomboid that moves the view with your head while your mouse or controller keeps aiming, driven by a webcam, phone, or any OpenTrack compatible tracker, with no VR headset required.

It adds a first person view to the game, with a rebuilt world and new graphics. You do not need a head tracker or a microphone, and you choose how much of the mod you see:

- **Isometric with new graphics** - the game starts in its own isometric view, with the game's own picture. Set the isometric view to Medium or High in **Options > Mods > Head Tracking** for the mod's lighting, shadows, models and textures there too.
- **First person with the game's own look** - press `Delete` for first person. Low keeps the game's own look, apart from furniture, windows and doors, which are 3D in every mode. Each of the mod's own weapons, furniture, pictures, plants, textures, buildings, skins, arms, clothes, fire and vehicle interiors can be put back to the game's own on the same options page.
- **No head tracker needed** - first person plays with a mouse and keyboard or a controller. Without a tracker, nothing follows your head.
- **No microphone needed** - Talking mouths is off until you tick it, and no microphone is opened until then.

Offline the mod also changes some of the game's rules: shots go through thin walls and doors, break lamps, cripple zombies' limbs and can take them off; vehicles knock down fences, street lamp posts and road barricades and drive through bushes; in the isometric view zombies show as soon as your character has a clear line to them. Each is on until you untick it in **Options > Mods > Head Tracking**. On a server these stay as the game has them.

## Features

- **Decoupled look and aim** - head tracking moves the view, your mouse or controller keeps the aim
- **6DOF tracking** - yaw, pitch and roll plus positional lean, peek and duck
- **Works with any OpenTrack compatible tracker** - free options available for PC, iOS and Android
- **Multiplayer and split screen** - join a server; only you need the mod. Players who have it see each other's heads turn as their trackers do, through the game's own player updates, so the server needs no mod. Players without the mod see your head as the game poses it. Up to four local players can each have their own first person view and head tracker.

## First person

Press `Delete` to switch between the isometric and first person views, on foot or in a vehicle.

### World and graphics

- **A rebuilt world** - 3D buildings, new textures, detailed plants, rolling hills and distant scenery in Medium and High. Furniture, windows and doors are 3D in every mode.
- **New graphics in both views** - lighting, shadows, models and textures in Medium and High, with reflections, and light beams through fog in first person. High adds sharper shadows and depth in near surfaces.
- **Fire** - the game's flames stood up in the world, with smoke, embers and firelight from burning buildings, Molotovs and campfires in Medium and High.
- **Sound** - follows where you look, muffled by the walls in between.
- **Adjustable** - automatic or manual quality, three picture styles, field of view, hill height, and each replacement model or texture can be put back to the game's own.

### Combat

- **Weapons** - custom arms and hands, melee swings, recoil, and reloads with working bolts, levers, cylinders and break actions. Medium and High add new shotgun, rifle, pistol and revolver models and magnified scopes.
- **Hits where you aim** - shots hit the body part under the reticle and jolt zombies along the line of fire. Offline, shot limbs go limp or come off, and a shotgun can take the head off.
- **Impacts** - dust, splinters, sparks, blood and bullet holes in Medium and High.
- **Shots through walls** - offline, rounds pass through plasterboard, wood, doors and glass, losing damage on each. Brick, concrete, metal and barricades stop them. A broad-leaved tree stops them at its trunk, not across its whole tile.
- **Shoot out lights** - offline, a shot breaks vehicle lamps and table, wall and street lamps.

### Vehicles

- **Interiors** - dashboards, seats, working dials and mirrors in Medium and High.
- **Drive-by shooting** - raise a firearm from any seat and shoot through the glass.
- **Offline driving** - smash through fences, lamp posts and road barricades, drive through bushes, take off over hill crests.

### Talking mouths

Off by default. Tick **Talking mouths** in the game's options and your character's mouth follows what you say into your microphone. Nothing is recorded or sent: online, other players who have this mod see only how your head is turned and the shape of your mouth.

## Requirements

- [Project Zomboid on Steam](https://store.steampowered.com/app/108600/Project_Zomboid/), tested on build **42.21.0**. The first line of `version.txt` in your `Zomboid` user folder (`C:\Users\<you>\Zomboid`) shows your build. Other stores have not been tested.
- For head tracking only: [OpenTrack](https://github.com/opentrack/opentrack) with a tracking source, or an app that sends the OpenTrack UDP protocol
- Windows 10 or 11, 64-bit
- For the Steam Workshop copy: [ZombieBuddy](https://github.com/zed-0xff/ZombieBuddy) 2.x, set up once

## Installation

### Lopari

Once this mod is available in Lopari, download [Lopari](https://lopari.app), choose **Project Zomboid**, and click **Play with head tracking**.

### Standalone installer

1. Download `ProjectZomboidHeadTracking-v<version>-installer.zip` from [Releases](https://github.com/itsloopyo/project-zomboid-headtracking/releases).
2. Close the game if it is running: the installer stops while it is.
3. Extract the ZIP and double-click `install.cmd`.
4. Follow [Setting Up OpenTrack](#setting-up-opentrack) below.
5. Launch the game, load a save or join a server, and press `Delete` for first person.

If the installer cannot find the game, it asks for the folder containing `ProjectZomboid64.exe`. You can also give the folder when you run it from a command prompt in the extracted folder:

```powershell
.\install.cmd "D:\Games\ProjectZomboid"
```

Run it again with another path to install into a second copy. No separate mod loader is required.

### What the scripts change

Both scripts work in the selected game folder:

| File | `install.cmd` | `uninstall.cmd` |
| --- | --- | --- |
| `ProjectZomboidHeadTracking.jar` and the art jars `ProjectZomboidHeadTracking-*.jar` | Copy from the ZIP, replacing existing copies | Remove |
| `ProjectZomboid64.site.json` | Write launch settings that load the mod whenever you start the game | Remove if it contains this mod's JAR filename |
| `.headtracking-state.json` | Record the installed mod version and date | Remove after successful cleanup |
| `ProjectZomboidHeadTracking.log`, `ProjectZomboidHeadTracking.prev.log` | Leave alone; the mod writes these when it runs | Remove |
| `CameraUnlock.ini` | Keep your settings; the mod creates this file on first launch if missing | Keep |

Original game files and saves stay untouched. Both scripts also remove `ProjectZomboidHeadTracking.dll` and `CameraUnlockCore.dll` if present.

**Reinstalling replaces custom edits to the mod's `.site.json`.** An existing file without this mod's JAR filename is left alone: installation stops. Removing `ProjectZomboid64.site.json` restores normal game startup.

Lopari writes `ProjectZomboid64.site.json` the same way when it installs the mod, and writes it again before a launch when the game's `ProjectZomboid64.json` has changed.

### Steam Workshop

The Workshop copy is loaded by [ZombieBuddy](https://github.com/zed-0xff/ZombieBuddy) 2.x, tested with 2.3.4. ZombieBuddy 3.0 is not supported yet.

1. Subscribe to [ZombieBuddy](https://steamcommunity.com/sharedfiles/filedetails/?id=3619862853) on the Steam Workshop.
2. Set ZombieBuddy up once. If you already play another mod that needs it, this is done. Otherwise use one of the two routes on ZombieBuddy's Workshop page:
   - **Installer:** download `ZombieBuddyInstaller.exe` from [ZombieBuddy's GitHub releases](https://github.com/zed-0xff/ZombieBuddy/releases/tag/windows_installer), run it and choose **Install or update ZombieBuddy**. Pick the launch mode to patch; ZombieBuddy recommends **Both**. It lists every change before making it, and asks you to close Steam if it has to change the launch options.
   - **By hand:** copy `ZombieBuddy.jar` and `zbNative.dll` from `steamapps\workshop\content\108600\3619862853\mods\ZombieBuddy\libs\` into the game folder, then in Steam open Project Zomboid's **Properties** and set **Launch Options** to `-agentlib:zbNative --`. The `--` at the end is needed.
3. Subscribe to PZHT - First Person + Graphics & Gameplay Overhaul + Head Tracking on the Steam Workshop.
4. Start the game, open **Mods** on the main menu, enable ZombieBuddy and PZHT - First Person + Graphics & Gameplay Overhaul + Head Tracking, then quit and start the game again. ZombieBuddy starts Java mods while the game starts, so they only load after that restart.
5. ZombieBuddy shows a dialog asking whether to load the mod's Java code. Click **Yes**. With ZombieBuddy's default settings it asks again after every update to the mod.
6. Follow [Setting Up OpenTrack](#setting-up-opentrack) below, load a save and press `Delete` for first person.

Use the Workshop copy or an installed copy, not both. If `install.cmd` or Lopari has installed the mod in the game folder, that copy runs and the Workshop copy stays out. Run `uninstall.cmd` first to play the Workshop copy.

The Workshop copy keeps its settings in `CameraUnlock.ini` and its log in `ProjectZomboidHeadTracking.log`, both in the game folder, the same as an installed copy. ZombieBuddy's own messages are in `console.txt` in your `Zomboid` user folder.

### Manual installation

You can install the mod without running any script. There is no mod manager package, because the launch settings file has to be made from your copy of the game's own.

1. Download `ProjectZomboidHeadTracking-v<version>-installer.zip` from [Releases](https://github.com/itsloopyo/project-zomboid-headtracking/releases) and extract it.
2. Open the game folder: in Steam, right-click Project Zomboid and choose **Manage > Browse local files**. It holds `ProjectZomboid64.exe`. Turn on **View > Show > File name extensions** in File Explorer, so you can tell `ProjectZomboid64.json` from `ProjectZomboid64.exe` and do not end up with a name ending in `.json.json`.
3. Copy every `.jar` in the ZIP's `plugins` folder into the game folder: `ProjectZomboidHeadTracking.jar` and the art jars named `ProjectZomboidHeadTracking-` and a folder, such as `ProjectZomboidHeadTracking-models.jar`. The mod finds the art jars beside its own jar, and stops with their names in its log if one is missing.
4. In the game folder, copy `ProjectZomboid64.json` and name the copy `ProjectZomboid64.site.json`, replacing a `ProjectZomboid64.site.json` this mod made before. Leave the original as it is. If a `ProjectZomboid64.site.json` is already there and does not mention `ProjectZomboidHeadTracking.jar`, something else made it. The installer stops rather than replace it. Rename it to set it aside, knowing that whatever made it stops working, and continue.
5. Open `ProjectZomboid64.site.json` in a text editor and make three changes:
   - Note the value of `"mainClass"`, then replace it with `"com/cameraunlock/core/agent/Boot"`.
   - Add `"ProjectZomboidHeadTracking.jar"` as the last entry of `"classpath"`.
   - Put these three entries first in the `"vmArgs"` list that follows `"classpath"`, not the ones under `"windows"`, with the value you noted in place of `<mainClass>`. If any of the three is already further down that list, take the old copy out. In the game's own file that is `--enable-native-access=ALL-UNNAMED`.

     ```json
     "-Dcameraunlock.mainClass=<mainClass>",
     "-XX:+EnableDynamicAgentLoading",
     "--enable-native-access=ALL-UNNAMED",
     ```

   Leave everything else, such as the memory and garbage collector settings, as the game set it. Every entry of a list but the last ends with a comma. Save the file as UTF-8 without a BOM, which is what the installer writes.
6. Follow [Setting Up OpenTrack](#setting-up-opentrack), launch the game and press `Delete` in a loaded game. The mod writes `ProjectZomboidHeadTracking.log` in the game folder when it loads.

For the game's file of build 42.21.0, the start of the result reads:

```json
{
    "mainClass": "com/cameraunlock/core/agent/Boot",
    "classpath": [
        ".",
        "projectzomboid.jar",
        "ProjectZomboidHeadTracking.jar"
    ],
    "vmArgs": [
        "-Dcameraunlock.mainClass=zombie/gameStates/MainScreenState",
        "-XX:+EnableDynamicAgentLoading",
        "--enable-native-access=ALL-UNNAMED",
        "-Djava.awt.headless=true",
        "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
        "-Xmx3072m",
        ...
```

Start from your own `ProjectZomboid64.json` rather than copying this, since another build can have other settings. The game reads `ProjectZomboid64.site.json` in place of `ProjectZomboid64.json`, so after every game update, make the site file again from the game's file (steps 4 and 5), as the installer does when you rerun it.

To remove a manual install, delete these from the game folder: `ProjectZomboidHeadTracking.jar`, the art jars `ProjectZomboidHeadTracking-*.jar`, `ProjectZomboid64.site.json`, `ProjectZomboidHeadTracking.log` and `ProjectZomboidHeadTracking.prev.log`, and `.headtracking-state.json`, `ProjectZomboidHeadTracking.dll` and `CameraUnlockCore.dll` if they are there from the installer or an older version. `CameraUnlock.ini` holds your settings: delete it too if you do not want them kept.

## Setting Up OpenTrack

The mod listens for OpenTrack pose data on UDP port `4242`, on every network
interface. One datagram is six little-endian 64-bit floats in the order
`x, y, z, yaw, pitch, roll`: position in centimetres, rotation in degrees, 48
bytes in total. Anything that sends that to that port drives the view.
OpenTrack's **UDP over network** output sends exactly this, and the steps below
set it up.

1. Install [OpenTrack](https://github.com/opentrack/opentrack/releases).
2. Pick a tracker under **Input**, using the notes below.
3. Set **Output** to **UDP over network**, host `127.0.0.1`, port `4242`.
4. Press **Start**. Tracking and the game can start in either order.

### Webcam

OpenTrack ships a `neuralnet tracker` input that reads a plain webcam. Select it
under **Input**, pick your camera in its settings, and use the output settings
above. How well it tracks depends on your camera and your lighting, so try it
before buying anything.

### Phone

A phone app can reach the mod directly, with no OpenTrack on the PC, if it sends
the datagram described above. Point it at this PC's IP address (run `ipconfig`
to find it) on port `4242`. Not every phone tracker speaks this protocol, so
check yours for an OpenTrack or UDP output option first. [Headcam](https://headcam.app)
sends it, and I wrote it so decent tracking is free for anyone who already owns
a phone.

Data from another device comes in over your network, so Windows Firewall has to
let it through. If Windows asks whether to let the game through the firewall,
allow it on the kind of network your PC is on (Private for most home networks).
If you said no, or the phone's tracking never arrives, add the rule from a
command prompt run as administrator:

```
netsh advfirewall firewall add rule name="Project Zomboid Head Tracking" dir=in action=allow protocol=UDP localport=4242-4245
```

Ports 4243 to 4245 are for players 2 to 4 at the same screen.

Sending direct works when the app filters its own signal on the device. The
mod's smoothing is sized to take the edge off a clean signal rather than to
rescue a noisy one, so a raw feed sent direct will jitter. If it does, point the
app at OpenTrack's **UDP over network** *input* on some other port, say 5252,
and let OpenTrack's filters and curves clean it up before its output forwards to
`127.0.0.1:4242`.

Anything arriving from outside `127.0.0.0/8` counts as a remote connection and
is smoothed with `RemoteSmoothing` rather than `LocalSmoothing`. That includes a
tracker on this very PC that sends to the machine's own LAN address, because the
mod reads the source address and not the machine.

### Headset or other hardware

If your device has an OpenTrack input driver, select it under **Input** and use
the same output settings. OpenTrack's own **Input** list is the authority on
what it can read; the mod only ever sees what OpenTrack sends.

### Centring

Centring belongs to your tracker, recenter there.

## Controls

### Mod shortcuts

| Key | Action |
| --- | --- |
| `Delete` | Switch between first person and isometric |
| `Page Up` | Track head rotation and movement, rotation only, or movement only |
| `Page Down` | Switch between horizon-relative and camera-relative head turns |
| `Insert` | Change aiming mode |
| `F9` | Change graphics quality of the view you are in |
| `F8` | Change picture style |
| `F7` | Arrange two-player split screen side by side or top and bottom |

- `Page Up` goes through three modes in turn: rotation and position, rotation only, position only.
- `Page Down` switches head turns between turning about straight up and turning about the view's own up. It does nothing from a vehicle's seat, where head turns always go about the view's own up.
- `Insert` goes through three aiming modes in turn:
  - **Sights locked** keeps your eye lined up with the sights while you aim.
  - **Free look with a marker** leaves the weapon pointing where you aim while your head looks around it, and the game's reticle marks where the shot lands.
  - **Stock sights** eases head turns and leaning out while the sights are up, so you see the game's own sight picture, and keeps only head tilt.
- `F9` goes through Automatic, Low, Medium, High and Custom in first person, and Low, Medium, High and Custom in the isometric view, and leaves the other view's mode as it is. Switching views names the mode of the view you switch to, as `First Person (High)` or `Isometric (Low)`.
- Where a player's first person view is larger than 2560x1440, every mode but Custom draws it with the pixels it would use at 2560x1440, and the picture is scaled up to fit. In split screen that goes by each player's own part of the screen. Custom draws at its `RenderScale` at any size, and so does the isometric view.
- `F8` goes through the picture styles Vivid, Natural and Bleak.
- In split screen each player has their own graphics mode and picture style. Tap `F9` or `F8` once for player 1, twice for player 2, three times for player 3 and four times for player 4, or hold it for every player. Players 2, 3 and 4 keep theirs in `[Players]` in `CameraUnlock.ini` and in the game's options.

Every key except `Delete` saves its new setting to `CameraUnlock.ini` as you press it. No key turns head tracking on or off: `EnableOnStartup` in `CameraUnlock.ini` decides that. There is no recenter key: centre the view in your tracker app.

### Mouse and keyboard

The mouse turns the view. Movement keys follow the direction you face. Walking or sneaking sideways or backwards, your character keeps facing where you look, in the game's aiming stance at the pace of its walk ahead. Jogging or sprinting, it turns to the way it goes, as in the game. Offline only, and `FacesTheView=false` gives the game's own turn every time.

| Input | Action |
| --- | --- |
| Left click | Attack |
| Right click | Aim |
| `Left Shift` | Sprint |
| `Left Alt` | Jog |
| `C` (or your crouch key), `Left Ctrl` | Crouch |
| `E` by default | Interact with the highlighted object |
| `I` by default | Open or close inventory |
| Click middle mouse | Show the pointer, until you click it again or press `Esc` |
| Left-click with the pointer out | Use what the pointer is on, as `E` does |
| Right-click with the pointer out | Open the menu for what the pointer is on |

**Other controls** - reload, shove, weapon selection and flashlight use your existing keys.

**Toggle to aim** - if this game option is enabled, aim before attacking.

**Interaction** - put the reticle on a door, window, curtain, light switch, container, item on the ground, radio, TV or crafting station and press `E`. On a fridge, `E` opens the freezer or the fridge, whichever half the reticle is on. On anything else within reach, such as a sink or a chair, `E` opens the game's own menu for it. You can also bring the pointer out (click middle mouse, or open the inventory) and right-click a thing in the view to open its menu, as a right click does in the isometric view. A left click with the pointer out does what `E` does with the reticle on the same thing, as a left click does in the isometric view.

### Controller and split screen

**Controller** - the controllers ticked in the game's Controller options play. While none of the gamepads plugged in is ticked, every one the game recognises is ticked and plays, and pressing **A** on the main menu gives that controller the menu. Player 1 plays on whatever last worked the main menu: the controller that pressed **A**, or the keyboard and mouse once you click or press a key there. Any other controller joins once the game is under way: press **A** on it and choose **Add new player**. A 3Dconnexion SpaceMouse is never taken as a controller. Double-tap **Back** to switch views. The aiming stick turns the view, right trigger attacks and left trigger raises firearm sights.

**More players** - tap `Delete` twice to switch player 2's view, three times for player 3's and four times for player 4's, or hold it to change everyone at once: if anyone is in the isometric view every player goes to first person, and holding it again puts every player in the isometric view. Players on a controller can double-tap **Back**. Send player 2's tracker to port **4243**, player 3's to **4244** and player 4's to **4245**.

**PvP** - on a server with PvP turned on, the mod holds back anything that could decide a fight between players:

- Other players are shown only as the game shows them, and a shot at a player hits the body part the game rolls.
- With firearm sights up, a sideways lean eases out, so leaning never moves where a shot starts.
- Hills stand at height 1 whatever you set, and zombies and props are drawn at full distance.
- Fog is as thick as in the game's own view.
- From a vehicle seat you shoot only with your window open or broken, and a driver who raises a rifle or shotgun lets go of the wheel, as in the game.

Zombies, rooms and vehicles with no other player in them look as they do offline. In split screen and on a server without PvP, other players are drawn as far off as zombies, where the game's own view from above would leave them out or fade them; an admin the game makes invisible stays invisible. On a server without PvP the mod plays as it does alone, apart from wounds and vehicles: on any server, your own co-op host included, a shot arm or leg leaves a zombie as the game does, and vehicles drive and roll as the game has them, on level ground.

## Configuration

Use **Options > Mods > Head Tracking** for tracking, graphics and the offline rules. First person and the isometric view each keep their own graphics quality: first person starts on Automatic and the isometric view on Low, the game's own picture. Switching views shows the quality the new view uses. For keys, ports and smoothing, edit `CameraUnlock.ini` beside the game executable.

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
; UDP port for player 2's tracker (OpenTrack protocol). Each player needs their own.
SecondPlayerUdpPort=4243
; UDP port for player 3's tracker (OpenTrack protocol). Each player needs their own.
ThirdPlayerUdpPort=4244
; UDP port for player 4's tracker (OpenTrack protocol). Each player needs their own.
FourthPlayerUdpPort=4245

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
; Vertical field of view of the first person view, in degrees: 40 to 110.
FieldOfView=65.0
; First person graphics mode: Automatic, Low, Medium, High or Custom. Automatic draws less when the frame rate drops
; and more again when it recovers. Custom uses the rows of [Graphics]. A view larger than 2560x1440 is drawn in the
; modes but Custom with no more pixels than 2560x1440.
Quality=Automatic
; Graphics mode of the game's isometric view: Low (the game's own picture), Medium, High or Custom.
IsometricQuality=Low
; Picture style: Vivid (strong contrast and colour), Natural (colours as lit, less contrast) or Bleak (washed out, near
; the game's own look).
Style=Bleak
; How high the first person hills stand: 0 is as flat as the game's map, 1 as the mod makes them, up to 2.0.
; Held at 1 where players can fight.
HillHeight=1.5
; true: a shot from a firearm flashes at the muzzle and briefly lights what is near. Never in Low.
MuzzleFlash=true
; true: in first person, while the game draws no reticle of its own, a small crosshair with a weapon in hand and a
; dot without one mark where the shot lands and what the interact key uses. false: neither is drawn.
Crosshair=true
; true: a weapon made for both hands goes back into both when the left hand puts down what it held beside it,
; a torch say, as the inventory's Equip Two Hands does. A torch switched off beside it is put away for that, and one
; taken up again leaves the weapon in the right hand. false: the game's way, where the weapon stays in the right hand
; alone, and taking up a torch takes it out of both. Never on a server where players can fight.
RegripTwoHandedWeapons=true
; How two players at one screen share it: Vertical (side by side) or Horizontal (one above the other).
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
; false: aiming down the sights keeps your eye on the sights when you lean. true: free look, the weapon stays put and your
; head moves around it.
TrueFreeLook=default
; Changes nothing in Project Zomboid: the game's own reticle is the free look marker. Written with TrueFreeLook.
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
; Cycles the aim mode: sights locked, free look with a marker, stock sights.
TrueFreeLookKey=Insert
; Switches between first person and the game's own view. Tap once for player 1, twice for player 2, three times for
; player 3, four times for player 4, hold for every player.
FirstPersonKey=Delete
; Goes to the next graphics mode of the view a player is in: in first person Automatic, Low, Medium, High, Custom
; (Quality), in the isometric view Low, Medium, High, Custom (IsometricQuality), and for players 2, 3 and 4 their
; rows of [Players]. Tap once for player 1, twice for player 2, three times for player 3, four times for player 4,
; hold for every player.
CycleQualityKey=F9
; Goes to a player's next picture style: Vivid, Natural, Bleak (Style, and for players 2, 3 and 4 their rows of
; [Players]). Tap once for player 1, twice for player 2, three times for player 3, four times for player 4, hold
; for every player.
CycleStyleKey=F8
; With two players at the screen, switches between side by side and one above the other.
SplitScreenKey=F7

[Light]
; How far the light turns for each degree your head turns.
; 1 matches the view, 0 keeps the light on your aim.
LightMultiplier=default

[Players]
; Player 2's first person graphics mode, as Quality.
SecondPlayerQuality=Automatic
; Player 2's isometric graphics mode, as IsometricQuality.
SecondPlayerIsometricQuality=Low
; Player 2's picture style, as Style.
SecondPlayerStyle=Bleak
; Player 3's first person graphics mode, as Quality.
ThirdPlayerQuality=Automatic
; Player 3's isometric graphics mode, as IsometricQuality.
ThirdPlayerIsometricQuality=Low
; Player 3's picture style, as Style.
ThirdPlayerStyle=Bleak
; Player 4's first person graphics mode, as Quality.
FourthPlayerQuality=Automatic
; Player 4's isometric graphics mode, as IsometricQuality.
FourthPlayerIsometricQuality=Low
; Player 4's picture style, as Style.
FourthPlayerStyle=Bleak

[Graphics]
; Low: the game's plant sprites. Medium: the mod's trees, the game's sprites past 40 tiles.
; High: the mod's trees as far as you see.
TreeQuality=High
; How much of the grass the mod grows blade by blade is grown, as a percentage: 10 to 100.
; Lower, fewer and broader plants cover the same lawns and long grass, which costs less to
; build and draw. High grows all of it, Medium and Low 40. On a server where
; players can hurt each other it grows all of it.
GrassDensity=100
; Share of the window's resolution the first person picture is drawn at, each way: 0.5 to 1.
RenderScale=1.0
; How far the first person view is drawn, in tiles: 32 to 80, within the map the game has loaded.
ViewDistance=80
; How far zombies, people, vehicles and the mod's furniture are drawn, in tiles: 16 to 80, never past
; ViewDistance. Where players can fight, everything is drawn.
ZombiesAndPropsDrawDistance=80
; How far past the loaded map trees are drawn, in tiles: 0 (none) to 768.
FarTreeDistance=400
; Rain on your vehicle's windows: Low (one size of drop), Medium (adds specks and trickles) or High (three sizes and
; running drops).
RainOnGlass=High
; true: shaded corners, light from what stands near and lit air, and with Reflections, mirroring wet roads, floors,
; glass and water.
ShadingAndLight=true
; true: mirrors in rooms show the room and you, your vehicle's mirrors show the road behind, and with
; ShadingAndLight, wet roads, floors, glass and water mirror the world.
Reflections=true
; true: the mod's surface, wall and skin pictures at full size. false: half size, a quarter of the memory.
FullSizeTextures=true
; true: near surfaces are given the depth of what they show. false: they are drawn flat.
SurfaceDepth=true
; true: shadow maps 4096 texels across. false: 2048, a quarter of the graphics memory.
SharpShadows=true
; true: what people and zombies wear and carry casts a shadow at any distance. false: past 4 tiles
; only their bodies do, which costs less in a crowd.
WholeFigureShadows=true
; true: each frame is blended with the one before, for smoother edges and leaves and a slightly softer picture.
FrameBlending=true
; How far off lamps light what is round them with light of their own, a pool under a street lamp, in tiles:
; 0 (the game's light only) to 80. Past it, lamps light the ground as the game does.
LightDistance=60

[Content]
; true: the game's own weapons, with no picture through the scope. false: the mod's models of the pump shotgun and
; its sawn-off, the tactical pump shotgun, the hunting, varmint and tactical rifles, the lever action rifle and
; carbine, the self-loading rifle and carbine, the double barrelled shotgun and its sawn-off, the pistols, the
; revolvers, the toy cap pistol and rifle, the assault rifle, the battle rifle and rifle scopes.
VanillaWeapons=false
; true: the game's sprites. false: the mod's models of furniture, appliances, street lamps, clocks, picture frames,
; banners and flags.
VanillaFurniture=false
; true: the game's pictures on walls, and only colour bars on a television. false: the mod's own pictures on walls
; and televisions, where it has one.
VanillaPictures=false
; true: the game's sprites. false: the mod's trees, bushes, boulders, grass, ferns, flowers, crops, fallen leaves and
; fallen sticks.
VanillaPlants=false
; true: the colours of the game's sprites, and its own stained glass. false: the mod's textures of ground, wood,
; roofing, paint, carpet, cloth, wallpaper and stained glass, and clean road lines.
VanillaTextures=false
; true: the game's sprites. false: windows, doors, curtains, handrails, chain link fences, open stairs and wall
; finishes built by the mod.
VanillaBuildings=false
; true: the game's own skins for zombies. false: the mod's skins, with lifelike faces, for zombies near you.
VanillaZombies=false
; true: the game's own skins for people. false: the mod's skins, with lifelike faces, for your character and other
; living people.
VanillaPeople=false
; true: the game's own arms and hands on a held weapon, for a mod that changes the arms or the weapons. false: the
; mod's, working its bolt, slide and magazine.
VanillaArms=false
; true: the game's own pictures of clothes and hair. false: clothes and hair near you are sharpened, with the weave,
; knit, grain or strands of what they are made of.
VanillaClothes=false
; true: the game's own skins for animals. false: the mod's skins for animals near you, with the hair of their coats.
VanillaAnimals=false
; true: the game's own flames and smoke, stood up where they burn. false: the game's flames with the mod's smoke,
; embers and firelight for burning buildings, Molotovs, campfires, fireplaces, wood stoves, burn barrels and
; cooking pits.
VanillaFire=false
; true: the game's own inside of the vehicle you sit in, with its glass cut away. false: the mod's models of it, its
; dashboard, instruments, wheel, seats and doors.
VanillaCabins=false

[Rules]
; true: offline, your shots go on through stud walls, walls of boards, shut doors and windows, losing damage to
; each by what it is made of. false: they stop it, as the game has it. On a server the game's rule holds.
ShotsThroughWalls=true
; true: offline, a shot breaks the lamp it passes through and the vehicle lamp it lands on. false: lamps
; are left lit, as the game has it. On a server the game's rule holds.
ShotsBreakLamps=true
; true: offline, first person shots to a zombie's legs slow it and drop it to crawl, shots to its arms leave them
; hanging, enough take the limb off, and a head shot that kills can take the head off or burst it. false: hits
; are the game's. On a server the game's rule holds.
ShotsWoundLimbs=true
; true: offline, a vehicle faster than a walk knocks down fences, street lamp posts and road barricades, drives through bushes
; unslowed and keeps its engine's full power off the road. false: the game's rules for all of it. On a server the
; game's rules hold.
VehiclesSmashThrough=true
; true: offline, walking or sneaking sideways or backwards in first person, your character keeps facing where you
; look, at the pace of its walk ahead. Jogging or sprinting it turns to the way it goes. false: it always turns,
; as the game has it. On a server the game's rule holds.
FacesTheView=true
; true: offline, a tree stops your shots only where its trunk is drawn, and a shot past the trunk goes on.
; false: a tree stops every shot across its square, as the game has it. On a server the game's rule holds.
ShotsPastTrees=true
; true: offline, in the isometric view a zombie shows as soon as nothing stands between your character and it,
; where your character's sight reaches by the game's rules. false: the game's own sight, which can show a
; zombie late or only once it is close. On a server the game's rule holds.
ZombiesSeenAtOnce=true

[Voice]
; true: your character's mouth moves with what you say into your microphone: the jaw drops for "ah", the lips
; spread for "ee" and round for "oo". Online, other players' mouths move while the game's voice chat plays them.
; The sound is measured as it comes in, and nothing is recorded or sent.
; false: no microphone is opened.
TalkingMouths=false
; Your microphone, by the name Windows gives it, Default for Windows' own default, or None.
Microphone=Default
; The microphone of player 2 at the screen, by the name Windows gives it, Default for Windows' own default, or None.
SecondPlayerMicrophone=None
; The microphone of player 3 at the screen, by the name Windows gives it, Default for Windows' own default, or None.
ThirdPlayerMicrophone=None
; The microphone of player 4 at the screen, by the name Windows gives it, Default for Windows' own default, or None.
FourthPlayerMicrophone=None
```
<!-- /cameraunlock:config -->

## Troubleshooting

- **No tracking** - start the tracker, check its address and port, then press `Delete` in a loaded game.
- **Phone tracking never arrives** - let it through Windows Firewall, as [Phone](#phone) describes.
- **The game no longer starts** - delete `ProjectZomboid64.site.json` from the game folder to start the game without the mod. After a manual install, check that file for a missing or extra comma, then make it again.
- **Sights or scope problems** - press `Insert` to select **sights locked**.

For help, use [Discord](https://discord.com/invite/dxyZdyFNT9) or [report an issue](https://github.com/itsloopyo/project-zomboid-headtracking/issues). Include `ProjectZomboidHeadTracking.log` from the game folder.

## Updating

Steam updates the Workshop copy itself. With the standalone installer, run the new release's `install.cmd`, and rerun it after a game update too. For a manual install, copy every `.jar` in the new release's `plugins` folder over the old ones, delete `ProjectZomboidHeadTracking.dll` and `CameraUnlockCore.dll` if they are there, and redo steps 4 and 5. Your `CameraUnlock.ini` settings stay either way.

## Uninstalling

For the Workshop copy, disable or unsubscribe from the mod, then restart the game: ZombieBuddy cannot take Java code out of a running game. Otherwise run `uninstall.cmd`, or delete the files listed under [Manual installation](#manual-installation). Your `CameraUnlock.ini` settings stay unless you delete that file too.

## Building from source

Prerequisites: [pixi](https://pixi.sh), CMake, and Visual Studio with its C++ tools. pixi brings the Java 25 compiler.

```powershell
git clone --recurse-submodules https://github.com/itsloopyo/project-zomboid-headtracking.git
cd project-zomboid-headtracking
pixi run package
```

`pixi run package` builds, runs the tests and writes the installer ZIP to `release\`.

## Community & Support

- Discord: [Loop's Head Tracking Hangout](https://discord.com/invite/dxyZdyFNT9) - setup help, bug reports, and new-release announcements
- [Lopari](https://lopari.app) - free Windows launcher with one-click install and launch for the released head-tracking mods
- [Headcam](https://headcam.app) - my free app that turns your iPhone or Android phone into the head tracker

## License

[MIT License](LICENSE). Third-party credits and licences are in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

## Credits

Thanks to [The Indie Stone](https://theindiestone.com) for Project Zomboid, and to [OpenTrack](https://github.com/opentrack/opentrack) for the UDP tracking protocol.

The pictures the mod hangs on walls and shows on televisions are screenshots taken inside Project Zomboid. Thanks to The Indie Stone for creating Project Zomboid (https://projectzomboid.com/), which made this possible. This is an unofficial fan production for non-commercial purposes made under the [Indie Stone Terms](https://projectzomboid.com/blog/support/terms-conditions/).

## Disclaimer

This is an unofficial, non-commercial fan production under the [Indie Stone Terms](https://projectzomboid.com/blog/support/terms-conditions/), not affiliated with or endorsed by The Indie Stone.
