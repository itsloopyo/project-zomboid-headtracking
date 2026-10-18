# Project Zomboid Head Tracking

![Project Zomboid running with this mod](https://raw.githubusercontent.com/itsloopyo/project-zomboid-headtracking/main/assets/readme-clip.gif)

An unofficial head tracking mod for Project Zomboid that moves the view with your head while your mouse or controller keeps aiming, driven by a webcam, phone, or any OpenTrack compatible tracker, with no VR headset required.

It adds a first person view to the game, with a rebuilt world and new graphics.

## Features

- **Decoupled look and aim** - head tracking moves the view, your mouse or controller keeps the aim
- **6DOF tracking** - yaw, pitch and roll plus positional lean, peek and duck
- **Works with any OpenTrack compatible tracker** - free options available for PC, iOS and Android
- **Multiplayer and split screen** - join a server; only you need the mod. Players who have it see each other's heads turn as their trackers do, through the game's own player updates, so the server needs no mod. Players without the mod see your head as the game poses it. Up to four local players can each have their own first person view and head tracker.

## First person

- **On foot and in vehicles** - press `Delete` to switch views. Play with a mouse and keyboard or controller, with sound that follows where you look, muffled by the walls between you and it and ringing in the room you stand in.
- **A rebuilt world** - in Medium and High: 3D buildings and furniture, new textures, detailed plants, rolling hills and distant scenery. Low keeps the game's own look in first person, apart from the furniture, windows and doors, which are 3D in every mode.
- **New renderer graphics in both views** - lighting, shadows, models and textures in Medium and High. High adds reflections on roads, floors, glass and water, and light beams through fog. Mirrors show the room and you in every mode with Reflections on.
- **Combat** - custom arms and hands, melee swings, recoil and reload animations. Every shot kicks the weapon and lets it spring back, a shotgun hardest, without moving where the shot goes. Shots hit the body part you aim at. In single player a shot arm hangs limp, a shot leg slows a zombie to a lurching shamble, and a leg shot through drops it to crawl. Keep shooting a ruined limb and it comes off at the wrist, elbow or shoulder, or the ankle, knee or hip, flies, and lies flat on the ground where it lands, and a shotgun blast to the head now and then knocks the head off or bursts it. Medium and High add pump shotgun, sawn-off, hunting rifle, over and under double barrel, pistol and revolver models, a toy cap pistol among them, and magnified scopes. A revolver's hammer falls and its cylinder turns with each shot, and on a reload the cylinder swings out, the empties fall and the rounds go in one at a time. To reload a double barrel, full length or sawn off, the top lever goes over and the barrels drop open, the fired shells fly out over your shoulder, the new ones go in and the gun snaps shut. A sawn-off has no stock to brace, so with the sights up it kicks in your hands as hard as at the hip. A shot knocks a zombie along the line it flew: one that stays up is jolted where the round went in, and one that drops goes limp at once, folds at the knees and is thrown back, harder by a shotgun than a pistol. The fall needs the game's **Use Physics Hit Reactions** option, which the game turns off on a server. In Medium and High, shots strike what they hit: dust and chips from brick and concrete, splinters from wood, sparks from metal, blood from flesh, and a hole left in the walls, floors and ceilings.
- **Fire** - in Medium and High, burning buildings, Molotovs and campfires have flames from a fire simulation, solid from every side and from above, rising smoke and flickering firelight, and lit fireplaces and cooking pits burn inside their models, a wood stove glowing through its door.
- **Vehicle interiors** - dashboards, seats and working mirrors and instrumentation. In first person the game's own dashboard panel is hidden and the vehicle's dials show speed in mph, revs and fuel; the engine, lights and the rest are on the game's vehicle menu (`V`).
- **Drive-by shooting** - in first person, raise a firearm from any seat and shoot out of the vehicle with the window shut: the shot breaks the window, windscreen or rear screen it goes through. Driving with keyboard and mouse, the driver keeps the wheel with a rifle or shotgun raised, apart from while reloading.
- **Shoot out lights** - offline, a shot breaks the vehicle head or tail lamp it lands on, one at a time, and any lamp it passes through: table and wall lamps, street lamps. A broken lamp stays dark until it is repaired or a new bulb is fitted.
- **Shots through walls** - offline, a firearm's round goes on through plasterboard, wooden walls, shut doors, gates and window glass and hits what is behind it, with less damage for each one it passes. Shotgun pellets get through one plasterboard wall at most, a pistol round through a few walls or a wooden door, a rifle round through more. Brick, block, concrete, stone, tile, metal plate and barricades stop every round. In first person the reticle sits on a zombie behind a wall the round can pass. On a server a shot stops where the game stops it.
- **Smash through fences** - offline and in either view, a vehicle faster than a walk knocks down fences and street lamp posts and drives on, a little slower and a little dented.
- **Drive through bushes** - offline, bushes, plants, saplings and other growth no longer slow or cap a vehicle, and the engine keeps its full pull on grass and dirt. Grip off the road and the bumps are the game's own.
- **Plough through zombies** - offline, a zombie hit or a body run over pushes back on a vehicle a quarter as hard as the game has it, so a crowd barely slows or turns the car.
- **Hills that drive** - offline, a car you drive is pulled down the first person hills and leaves the ground over a crest at speed, with no drive or brakes until it lands.
- **Burning wrecks** - offline, a vehicle left on its roof catches fire after four seconds, throws out whoever is inside and goes up six seconds later, leaving the game's burnt wreck. What was in it burns with it.
- **Talking mouths** - off unless you turn it on: tick **Talking mouths** in the game's options and your character's mouth moves with what you say into your microphone, in first person as another player at your screen sees them and in the isometric view: the jaw drops for "ah", the lips spread for "ee" and "s" and round for "oo" and "oh", and the teeth and tongue show between them. Each player at the screen picks their own microphone there. Online, other players' mouths move while the game's voice chat plays them, and a player who has this mod with **Talking mouths** ticked shows how their own microphone shapes their mouth. The sound is measured as it comes in and is never recorded or sent: online, only how open your mouth is and the shape of your lips go to other players.
- **Adjustable graphics** - automatic or manual quality, three picture styles, field of view, hill height and optional replacement models and textures.

## Requirements

- [Project Zomboid on Steam](https://store.steampowered.com/app/108600/Project_Zomboid/), tested on build **42.21.0**. The first line of `version.txt` in your `Zomboid` user folder (`C:\Users\<you>\Zomboid`) shows your build. Other stores have not been tested.
- [OpenTrack](https://github.com/opentrack/opentrack) with a tracking source, or an app that sends the OpenTrack UDP protocol
- Windows 10 or 11, 64-bit

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
| `ProjectZomboidHeadTracking.jar`, `CameraUnlockCore.dll` | Copy from the ZIP, replacing existing copies | Remove |
| `ProjectZomboid64.site.json` | Write launch settings that load the mod whenever you start the game | Remove if it contains this mod's JAR filename |
| `.headtracking-state.json` | Record the installed mod version and date | Remove after successful cleanup |
| `ProjectZomboidHeadTracking.log`, `ProjectZomboidHeadTracking.prev.log` | Leave alone; the mod writes these when it runs | Remove |
| `CameraUnlock.ini` | Keep your settings; the mod creates this file on first launch if missing | Keep |

Original game files and saves stay untouched. Both scripts also remove the old `ProjectZomboidHeadTracking.dll` if present.

**Reinstalling replaces custom edits to the mod's `.site.json`.** An existing file without this mod's JAR filename is left alone: installation stops. Removing `ProjectZomboid64.site.json` restores normal game startup.

Lopari writes `ProjectZomboid64.site.json` the same way when it installs the mod, and writes it again before a launch when the game's `ProjectZomboid64.json` has changed.

### Manual installation

You can install the mod without running any script. There is no mod manager package, because the launch settings file has to be made from your copy of the game's own.

1. Download `ProjectZomboidHeadTracking-v<version>-installer.zip` from [Releases](https://github.com/itsloopyo/project-zomboid-headtracking/releases) and extract it.
2. Open the game folder: in Steam, right-click Project Zomboid and choose **Manage > Browse local files**. It holds `ProjectZomboid64.exe`. Turn on **View > Show > File name extensions** in File Explorer, so you can tell `ProjectZomboid64.json` from `ProjectZomboid64.exe` and do not end up with a name ending in `.json.json`.
3. Copy `plugins\ProjectZomboidHeadTracking.jar` and `plugins\CameraUnlockCore.dll` from the ZIP into the game folder.
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

To remove a manual install, delete these from the game folder: `ProjectZomboidHeadTracking.jar`, `CameraUnlockCore.dll`, `ProjectZomboid64.site.json`, `ProjectZomboidHeadTracking.log` and `ProjectZomboidHeadTracking.prev.log`, and `.headtracking-state.json` and `ProjectZomboidHeadTracking.dll` if they are there from the installer or an older version. `CameraUnlock.ini` holds your settings: delete it too if you do not want them kept.

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
| `F9` | Change graphics quality |
| `F8` | Change picture style |
| `F7` | Arrange two-player split screen side by side or top and bottom |

- `Page Up` goes through three modes in turn: rotation and position, rotation only, position only.
- `Page Down` switches head turns between turning about straight up and turning about the view's own up. It does nothing from a vehicle's seat, where head turns always go about the view's own up.
- `Insert` goes through three aiming modes in turn:
  - **Sights locked** keeps your eye lined up with the sights while you aim.
  - **Free look with a marker** leaves the weapon pointing where you aim while your head looks around it, and the game's reticle marks where the shot lands.
  - **Stock sights** eases head turns and leaning out while the sights are up, so you see the game's own sight picture, and keeps only head tilt.
- `F9` goes through Automatic, Low, Medium, High and Custom, and sets first person and the isometric view to the same mode. The isometric view has no Automatic: it stays in Low while first person is in Automatic, and `F9` pressed in the isometric view skips Automatic. To give the two views different modes, set them in the game's options.
- `F8` goes through the picture styles Vivid, Natural and Bleak.

Every key except `Delete` saves its new setting to `CameraUnlock.ini` as you press it. No key turns head tracking on or off: `EnableOnStartup` in `CameraUnlock.ini` decides that. There is no recenter key: centre the view in your tracker app.

### Mouse and keyboard

The mouse turns the view. Movement keys follow the direction you face.

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
| Right-click with the pointer out | Open the menu for what the pointer is on |

**Other controls** - reload, shove, weapon selection and flashlight use your existing keys.

**Toggle to aim** - if this game option is enabled, aim before attacking.

**Interaction** - put the reticle on a door, window, curtain, light switch, container or item on the ground and press `E`. For everything else, such as drinking from a sink, bring the pointer out (click middle mouse, or open the inventory) and right-click the thing in the view: the game's own menu for it opens, as a right click on it does in the isometric view. A left click with the pointer out does not act on the world.

### Controller and split screen

**Controller** - the controllers ticked in the game's Controller options play. While none of the gamepads plugged in is ticked, every one the game recognises is ticked and plays, and pressing **A** on the main menu gives that controller the menu. Player 1 plays on whatever last worked the main menu: the controller that pressed **A**, or the keyboard and mouse once you click or press a key there. Any other controller joins once the game is under way: press **A** on it and choose **Add new player**. A 3Dconnexion SpaceMouse is never taken as a controller. Double-tap **Back** to switch views. The aiming stick turns the view, right trigger attacks and left trigger raises firearm sights.

**More players** - double-tap `Delete` to switch player 2's view, or hold it to switch every player's. Players on a controller can double-tap **Back**. Send player 2's tracker to port **4243**, player 3's to **4244** and player 4's to **4245**.

**PvP** - on a server with PvP turned on, the mod holds back anything that could decide a fight between players:

- Other players are shown only as the game shows them, and a shot at a player hits the body part the game rolls.
- With firearm sights up, a sideways lean eases out, so leaning never moves where a shot starts.
- In first person, moving sideways or backwards puts your character in the game's aiming stance, at that stance's pace, so other players see it face where you look.
- Hills stand at height 1 whatever you set, and zombies and props are drawn at full distance.
- From a vehicle seat you shoot only with your window open or broken, and a driver who raises a rifle or shotgun lets go of the wheel, as in the game.

Zombies, rooms and vehicles with no other player in them look as they do offline. In split screen and on a server without PvP, other players are drawn as far off as zombies, where the game's own view from above would leave them out or fade them; an admin the game makes invisible stays invisible. On a server without PvP the mod plays as it does alone, apart from wounds and vehicles: on any server, your own co-op host included, a shot arm or leg leaves a zombie as the game does, and vehicles drive and roll as the game has them, on its level ground, and none burns out on its roof.

## Configuration

Use **Options > Mods > Head Tracking** for tracking and graphics. First person and the isometric view each keep their own graphics quality: first person starts on Automatic and the isometric view on Low, the game's own picture. Switching views shows the quality the new view uses. For keys, ports and smoothing, edit `CameraUnlock.ini` beside the game executable.

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
; and more again when it recovers. Custom uses the rows of [Graphics].
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
; Switches between first person and the game's own view. Tap for player 1, tap twice for player 2, hold for every player.
FirstPersonKey=Delete
; Goes to the next graphics mode and sets both views to it: Automatic, Low, Medium, High, Custom (Quality and
; IsometricQuality). The isometric view has no Automatic: it takes Low for it, and the key skips Automatic
; there. The game's options set the two views apart.
CycleQualityKey=F9
; Goes to the next picture style: Vivid, Natural, Bleak (Style).
CycleStyleKey=F8
; With two players at the screen, switches between side by side and one above the other.
SplitScreenKey=F7

[Light]
; How far the light turns for each degree your head turns.
; 1 matches the view, 0 keeps the light on your aim.
LightMultiplier=default

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
; true: shadow maps 4096 texels across. false: 2048, a quarter of the graphics memory.
SharpShadows=true
; How far the first person view is drawn, in tiles: 32 to 80, within the map the game has loaded.
ViewDistance=80
; How far zombies, people, vehicles and the mod's furniture are drawn, in tiles: 16 to 80, never past
; ViewDistance. Where players can fight, everything is drawn.
ZombiesAndPropsDrawDistance=80
; How far past the loaded map trees are drawn, in tiles: 0 (none) to 768.
FarTreeDistance=768
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
; true: what people and zombies wear and carry casts a shadow at any distance. false: past 4 tiles
; only their bodies do, which costs less in a crowd.
WholeFigureShadows=true
; true: each frame is blended with the one before, for smoother edges and leaves and a slightly softer picture.
FrameBlending=true

[Content]
; true: the mod's models of the pump shotgun and its sawn-off, the hunting rifle, the double barrelled shotgun and its
; sawn-off, the pistols, the revolvers, the toy cap pistol and rifle scopes. false: the game's own, with no picture
; through the scope.
OwnWeapons=true
; true: the mod's models of furniture, appliances, street lamps, clocks, picture frames, banners and flags. false: the
; game's sprites.
OwnFurniture=true
; true: the mod's photographs, paintings, posters, signs and television programmes. false: the game's pictures, and
; only colour bars on a television.
OwnPictures=true
; true: the mod's trees, bushes, boulders, grass, ferns, flowers, crops, fallen leaves and fallen sticks. false: the game's
; sprites.
OwnPlants=true
; true: the mod's textures of ground, wood, roofing, paint, carpet, cloth and wallpaper, and clean road lines. false:
; the colours of the game's sprites.
OwnTextures=true
; true: windows, doors, curtains, handrails, chain link fences, open stairs and wall finishes built by the mod. false:
; the game's sprites.
OwnBuildings=true
; true: the mod's skins, with lifelike faces, for zombies near you. false: the game's own skins.
OwnZombies=true
; true: the mod's skins, with lifelike faces, for your character and other living people. false: the game's own skins.
OwnPeople=true
; true: the mod's arms and hands on a held weapon, working its bolt, slide and magazine. false: the game's own, for a
; mod that changes the arms or the weapons.
OwnArms=true
; true: clothes and hair near you are sharpened, with the weave, knit, grain or strands of what they are made of.
; false: the game's own pictures.
OwnClothes=true
; true: the mod's skins for animals near you, with the hair of their coats. false: the game's own skins.
OwnAnimals=true
; true: flames, smoke and firelight for burning buildings, Molotovs, campfires, fireplaces, wood stoves, burn barrels
; and cooking pits. false: the game's own flat flames and smoke, stood up where they burn.
OwnFire=true
; true: the mod's models of the inside of the vehicle you sit in, its dashboard, instruments, wheel, seats and doors.
; false: the game's own, with its glass cut away.
OwnCabins=true

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
- **The game no longer starts** - delete `ProjectZomboid64.site.json` from the game folder to start the game without the mod. After a manual install, check that file for a missing or extra comma, then make it again.
- **Sights or scope problems** - press `Insert` to select **sights locked**.
For help, use [Discord](https://discord.com/invite/dxyZdyFNT9) or [report an issue](https://github.com/itsloopyo/project-zomboid-headtracking/issues). Include `ProjectZomboidHeadTracking.log` from the game folder.

## Updating

With the standalone installer, run the new release's `install.cmd`, and rerun it after a game update too. For a manual install, copy the new release's two files over the old ones, delete `ProjectZomboidHeadTracking.dll` if it is there, and redo steps 4 and 5. Your `CameraUnlock.ini` settings stay either way.

## Uninstalling

Run `uninstall.cmd`, or delete the files listed under [Manual installation](#manual-installation). Your `CameraUnlock.ini` settings stay unless you delete that file too.

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
- [Headcam](https://headcam.app) - free app that turns your iPhone or Android phone into the head tracker

## License

[MIT License](LICENSE). Third-party credits and licences are in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

## Credits

Thanks to [The Indie Stone](https://theindiestone.com) for Project Zomboid, and to [OpenTrack](https://github.com/opentrack/opentrack) for the UDP tracking protocol.

## Disclaimer

This is an unofficial, non-commercial fan production under the [Indie Stone Terms](https://projectzomboid.com/blog/support/terms-conditions/), not affiliated with or endorsed by The Indie Stone.
