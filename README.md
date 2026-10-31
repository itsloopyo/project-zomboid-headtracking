# Project Zomboid Head Tracking

![Project Zomboid running with this mod](https://raw.githubusercontent.com/itsloopyo/project-zomboid-headtracking/main/assets/readme-clip.gif)

An unofficial head tracking mod for Project Zomboid that moves the camera with your head while your mouse or controller keeps aiming, driven by a webcam, phone, or any OpenTrack compatible tracker, with no VR headset required.

It also adds a first person view to the game, with a rebuilt world and new graphics. You don't need a head tracker or a microphone, and you choose how much of the mod you see:

- **Isometric with new graphics** - the game starts in its usual isometric view, looking just like vanilla. Set the isometric view to Medium or High in **Options > Mods > Head Tracking** to get the mod's lighting, shadows, models and textures there too.
- **First person with the vanilla look** - press `Delete` for first person, and switch it to Low with `F9` to keep the game's own art. Furniture, windows and doors are 3D in every mode. Any of the mod's weapons, furniture, pictures, plants, textures, buildings, skins, arms, clothes, fire and vehicle interiors can be switched back to the game's own on the same options page.
- **No head tracker needed** - first person works with mouse and keyboard or a controller. Without a tracker, the view just doesn't follow your head.
- **No microphone needed** - Talking mouths is off until you tick it, and the mod doesn't open a microphone until you do.

When you play offline, the mod also changes some of the game's rules. Shots go through thin walls and doors, break lamps and windows, blow holes in wooden doors and board fences, and can cripple zombies or take their limbs off. Vehicles knock down fences, lamp posts and road barricades and drive through bushes. In the isometric view, zombies show up as soon as nothing blocks your character's view of them. Each of these is on by default and can be turned off in **Options > Mods > Head Tracking**. On a server they all stay vanilla, except that on servers without PvP, shots still go through thin walls and doors and past tree trunks, and zombies still show up straight away.

## Features

- **Decoupled look and aim** - head tracking moves the view, your mouse or controller keeps the aim
- **6DOF tracking** - yaw, pitch and roll plus positional lean, peek and duck
- **Works with any OpenTrack compatible tracker** - free options available for PC, iOS and Android
- **Multiplayer and split screen** - join a server without the server or anyone else needing the mod. Players who have it see your head turn as your tracker does, and players who don't see your character as the game normally poses it. Up to four players on one PC can each have their own first person view and head tracker.

## First person

Press `Delete` to switch between the isometric view and first person, on foot or in a vehicle. Saves load in whichever view you used last, and a hint on screen shows the key that switches views, then the key that changes graphics quality.

### World and graphics

- **A rebuilt world** - 3D buildings, new textures, detailed plants, rolling hills and distant scenery in Medium and High. Furniture, windows and doors are 3D in every mode.
- **New graphics in both views** - lighting, shadows, models, textures and reflections in Medium and High, plus light beams through fog in first person. High adds sharper shadows and depth on surfaces up close.
- **Fire** - the game's flames stand upright in the 3D world, and Medium and High add smoke, embers and firelight to burning buildings, Molotovs and campfires.
- **Sound** - turns with your view, and walls between you and a sound muffle it.
- **Adjustable** - automatic or manual quality, five picture styles, field of view and hill height, and any of the replacement models or textures can be switched back to the game's own.

### Combat

- **Weapons** - custom arms and hands, melee swings, recoil, and reloads with working bolts, levers, cylinders and break actions. Medium and High add new shotgun, rifle, pistol and revolver models and magnified scopes.
- **Hits where you aim** - shots hit the body part under the reticle and jolt zombies along the line of fire. Offline, shot limbs go limp or come off, and a killing head shot can take the head off, which a shotgun up close usually does.
- **Impacts** - dust, splinters, sparks, blood and bullet holes in Medium and High.
- **Shots through walls** - offline and on servers without PvP, shots go through plasterboard, wood, doors and glass, losing damage each time. Brick, concrete, metal and barricades stop them. A broad-leaved tree only stops a shot at its trunk, not across its whole tile.
- **Shoot out lights** - offline, shots break vehicle lights and table, wall and street lamps.
- **Shoot out windows** - offline, a shot breaks every closed window it passes through.
- **Blast holes** - offline, in first person, a shotgun blast that lands tightly grouped on a wooden door or a wooden fence, tall or low, blows a ragged hole through it, which you can see and shoot through. Pellets that miss the hole leave marks on a door. The holes stay in your save.
- **Bullet holes** - offline, in first person, a round that goes through a door, a wall or a wooden fence leaves a small hole where it went in and a wider, splintered one where it came out, which you can see through. The holes stay in your save.

### Vehicles

- **Interiors** - dashboards, seats, working gauges and mirrors in Medium and High.
- **Drive-by shooting** - raise a gun from any seat and shoot through the glass.
- **Offline driving** - smash through fences, lamp posts and road barricades, drive through bushes and get air over hill crests.

### Talking mouths

Off by default. Tick **Talking mouths** in the options and your character's mouth moves with what you say into your microphone. Your voice is never recorded or sent anywhere. Online, other players who have this mod only get how your head is turned and the shape of your mouth.

## Multiplayer

On a server without PvP, the mod works the same as offline, except for a few things that stay vanilla on every server, your own co-op host included: shots don't break lamps or windows or blow holes in doors and fences, limb shots work as in vanilla, and vehicles handle as in vanilla, on level ground.

In split screen and on servers without PvP, other players are drawn as far away as zombies, even where the isometric view would leave them out or fade them. An admin the game makes invisible stays invisible.

On a server with PvP on, the mod holds back anything that could decide a fight between players:

- Other players only show up where the game itself would show them, and a shot at a player hits whatever body part the game rolls.
- With a gun's sights up, sideways lean fades out, so leaning never changes where a shot starts from.
- Hill height is fixed at 1, all the grass is grown, and zombies and props are drawn at full distance, whatever your settings.
- Fog is as thick as in the game's own view.
- Walls, doors and trees stop shots as in vanilla.
- Walking sideways or backwards in first person, your character keeps facing where you look, in the game's aiming stance and at the slower speed the game gives it, whatever `FacesTheView` says. Other players see it facing the way you look rather than turning its back. Jog or sprint that way and you walk instead. Walking forwards or on a forward diagonal, it turns to face the way it's going, as in vanilla.
- In a vehicle you can only shoot with your window open or broken, and a driver who raises a rifle or shotgun lets go of the wheel, as in vanilla.

Zombies, and rooms and vehicles with no other player in them, look the same as they do offline.

## Requirements

- [Project Zomboid on Steam](https://store.steampowered.com/app/108600/Project_Zomboid/), tested on build **42.21.0**. Your build is on the first line of `version.txt` in your `Zomboid` user folder (`C:\Users\<you>\Zomboid`). Other stores haven't been tested.
- For head tracking: [OpenTrack](https://github.com/opentrack/opentrack) with a tracking source, or an app that sends the OpenTrack UDP protocol. First person doesn't need either.
- Windows 10 or 11, 64-bit
- For first person: a graphics card and driver that give the game OpenGL 3.3 (compatibility profile) with EXT_texture_array and ARB_shader_texture_lod, and at least 27 texture units and 2836 uniform components in a fragment shader. Without those, first person stays off, the game looks as it does without the mod, and `ProjectZomboidHeadTracking.log` says what's missing.
- For the Steam Workshop version: [ZombieBuddy](https://github.com/zed-0xff/ZombieBuddy) 2.x, set up once

## Installation

### Lopari

Download [Lopari](https://lopari.app), choose **Project Zomboid**, and click **Play with head tracking**.

### Standalone installer

1. Download `ProjectZomboidHeadTracking-v<version>-installer.zip` from [Releases](https://github.com/itsloopyo/project-zomboid-headtracking/releases).
2. Close the game if it's running, or the installer will stop.
3. Extract the ZIP and double-click `install.cmd`.
4. Set up your tracker with [Setting Up OpenTrack](#setting-up-opentrack) below.
5. Launch the game, load a save or join a server, and press `Delete` for first person.

If the installer can't find the game, it asks for the folder that has `ProjectZomboid64.exe` in it. You can also give it the folder from a command prompt in the extracted folder:

```powershell
.\install.cmd "D:\Games\ProjectZomboid"
```

To install into a second copy of the game, run it again with that copy's path. You don't need a separate mod loader.

### What the scripts change

Both scripts work in the game folder you pick:

| File | `install.cmd` | `uninstall.cmd` |
| --- | --- | --- |
| `ProjectZomboidHeadTracking.jar` and the art jars `ProjectZomboidHeadTracking-*.jar` | Copies them from the ZIP, replacing any already there | Removes them |
| `ProjectZomboid64.site.json` | Writes launch settings that load the mod every time you start the game | Removes it if it mentions this mod's jar |
| `.headtracking-state.json` | Records the installed version and date | Removes it once everything else is cleaned up |
| `ProjectZomboidHeadTracking.log`, `ProjectZomboidHeadTracking.prev.log` | Leaves them alone; the mod writes these when it runs | Removes them |
| `CameraUnlock.ini` | Keeps your settings; the mod creates this file on first launch if it's missing | Keeps it |

The game's own files and your saves aren't touched.

**Reinstalling overwrites any changes you've made to `ProjectZomboid64.site.json`.** If there's already a `ProjectZomboid64.site.json` that doesn't mention this mod's jar, the installer leaves it alone and stops. Deleting `ProjectZomboid64.site.json` puts the game back to starting normally.

Lopari writes `ProjectZomboid64.site.json` the same way when it installs the mod, and writes it again before a launch if the game's `ProjectZomboid64.json` has changed.

### Steam Workshop

The Workshop version is loaded by [ZombieBuddy](https://github.com/zed-0xff/ZombieBuddy) 2.x and has been tested with 2.3.4. ZombieBuddy 3.0 isn't supported yet.

1. Subscribe to [ZombieBuddy](https://steamcommunity.com/sharedfiles/filedetails/?id=3619862853) on the Steam Workshop.
2. Set ZombieBuddy up. You only do this once, and if you already play another mod that needs it, it's done. Otherwise use one of the two ways on ZombieBuddy's Workshop page:
   - **Installer:** download `ZombieBuddyInstaller.exe` from [ZombieBuddy's GitHub releases](https://github.com/zed-0xff/ZombieBuddy/releases/tag/windows_installer), run it and choose **Install or update ZombieBuddy**. Pick which launch mode to patch (ZombieBuddy recommends **Both**). It lists every change before it makes it, and asks you to close Steam if it needs to change the launch options.
   - **By hand:** copy `ZombieBuddy.jar` and `zbNative.dll` from `steamapps\workshop\content\108600\3619862853\mods\ZombieBuddy\libs\` into the game folder, then in Steam open Project Zomboid's **Properties** and set **Launch Options** to `-agentlib:zbNative --`. You need the `--` at the end.
3. Subscribe to PZHT - First Person + Graphics & Gameplay Overhaul + Head Tracking on the Steam Workshop.
4. Start the game, open **Mods** from the main menu, enable ZombieBuddy and PZHT - First Person + Graphics & Gameplay Overhaul + Head Tracking, then quit and start the game again. ZombieBuddy starts Java mods as the game starts, so they only load after that restart.
5. ZombieBuddy asks whether to load the mod's Java code. Click **Yes**. With ZombieBuddy's default settings it asks again after every update to the mod.
6. Set up your tracker with [Setting Up OpenTrack](#setting-up-opentrack) below, then load a save and press `Delete` for first person.

Use either the Workshop version or an installed copy, not both. If you've installed the mod in the game folder with `install.cmd` or Lopari, that copy runs and the Workshop version stays out of the way. To play the Workshop version, run `uninstall.cmd` first.

The Workshop version keeps its settings in `CameraUnlock.ini` and its log in `ProjectZomboidHeadTracking.log`, both in the game folder, just like an installed copy. ZombieBuddy's own messages go to `console.txt` in your `Zomboid` user folder.

### Manual installation

You can also install the mod by hand, without running any script. There's no mod manager package, because the launch settings file has to be built from the one in your own copy of the game.

1. Download `ProjectZomboidHeadTracking-v<version>-installer.zip` from [Releases](https://github.com/itsloopyo/project-zomboid-headtracking/releases) and extract it.
2. Open the game folder: in Steam, right-click Project Zomboid and choose **Manage > Browse local files**. It's the folder with `ProjectZomboid64.exe` in it. Turn on **View > Show > File name extensions** in File Explorer, so you can tell `ProjectZomboid64.json` from `ProjectZomboid64.exe` and don't end up with a file called `.json.json`.
3. Copy every `.jar` from the ZIP's `plugins` folder into the game folder: `ProjectZomboidHeadTracking.jar` and the art jars that start with `ProjectZomboidHeadTracking-`, such as `ProjectZomboidHeadTracking-models.jar`. The mod looks for the art jars next to its own jar, and if one is missing it stops and names it in its log.
4. In the game folder, make a copy of `ProjectZomboid64.json` called `ProjectZomboid64.site.json`, replacing any `ProjectZomboid64.site.json` this mod made before. Leave the original alone. If there's already a `ProjectZomboid64.site.json` that doesn't mention `ProjectZomboidHeadTracking.jar`, something else made it, and the installer would stop rather than replace it. To go ahead anyway, rename it to get it out of the way, knowing that whatever made it will stop working.
5. Open `ProjectZomboid64.site.json` in a text editor and make three changes:
   - Note down the value of `"mainClass"`, then replace it with `"com/cameraunlock/core/agent/Boot"`.
   - Add `"ProjectZomboidHeadTracking.jar"` as the last entry in `"classpath"`.
   - Add these three entries at the start of the `"vmArgs"` list that comes after `"classpath"` (not the one under `"windows"`), with the value you noted in place of `<mainClass>`. If any of the three is already further down that list, remove the old one. In the game's own file, that's `--enable-native-access=ALL-UNNAMED`.

     ```json
     "-Dcameraunlock.mainClass=<mainClass>",
     "-XX:+EnableDynamicAgentLoading",
     "--enable-native-access=ALL-UNNAMED",
     ```

   Leave everything else as the game set it, such as the memory and garbage collector settings. Every entry in a list except the last needs a comma after it. Save the file as UTF-8 without a BOM, which is what the installer writes.
6. Set up your tracker with [Setting Up OpenTrack](#setting-up-opentrack), launch the game and press `Delete` once a save has loaded. The mod writes `ProjectZomboidHeadTracking.log` in the game folder when it loads.

With the game's file from build 42.21.0, the result starts like this:

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

Start from your own `ProjectZomboid64.json` rather than copying this, because another build can have other settings. The game reads `ProjectZomboid64.site.json` instead of `ProjectZomboid64.json`, so after every game update, make the site file again from the game's new file (steps 4 and 5). Running the installer again does the same.

To remove a manual install, delete these from the game folder: `ProjectZomboidHeadTracking.jar`, the art jars `ProjectZomboidHeadTracking-*.jar`, `ProjectZomboid64.site.json`, `ProjectZomboidHeadTracking.log` and `ProjectZomboidHeadTracking.prev.log`, plus `.headtracking-state.json` if the installer left it there. `CameraUnlock.ini` holds your settings, so delete it too if you don't want to keep them.

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

Tracking data from another device comes in over your network, so Windows
Firewall has to let it through. If Windows asks whether to let the game through
the firewall, allow it on the type of network your PC is on (Private for most
home networks). If you said no, or nothing ever arrives from your phone, add the
rule yourself from a command prompt run as administrator:

```
netsh advfirewall firewall add rule name="Project Zomboid Head Tracking" dir=in action=allow protocol=UDP localport=4242-4245
```

Ports 4243 to 4245 are for players 2 to 4 in split screen.

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
| `Delete` | Switch between first person and isometric (`End` with Project Viewpoint) |
| `Page Up` | Track head rotation and movement, rotation only, or movement only |
| `Page Down` | Switch between horizon-relative and camera-relative head turns |
| `Insert` | Change aiming mode |
| `F9` | Change graphics quality of the view you're in |
| `F8` | Change picture style |
| `F7` | Arrange two-player split screen side by side or above and below |

- `Page Up` cycles through rotation and position, rotation only, and position only.
- `Page Down` switches head turns between horizon-relative, which turns the view around the vertical and keeps the horizon level when you look up or down, and camera-relative, which turns it around the view's own up axis. In a vehicle seat head turns are always camera-relative, so the key does nothing there, and the screen says so. Both keys show the new setting on screen.
- `Insert` cycles through three aiming modes:
  - **Sights locked** keeps your eye lined up with the sights while you aim.
  - **Free look with a marker** keeps the gun pointing where you aim while your head looks around it, and the game's reticle marks where the shot will land.
  - **Stock sights** fades out head turning and leaning while the sights are up, leaving only head tilt, so you see the game's normal sight picture.
- `F9` cycles the graphics mode of the view you're in: Automatic, Low, Medium, High and Custom in first person, or Low, Medium, High and Custom in the isometric view. The other view keeps its own mode. Going past Low with every Mod content box unticked ticks them all again, and in the isometric view with first person turned off it ticks First person view, since without them every mode looks like Low. When you switch views, the screen names the mode of the view you switched to, like `First person (High)` or `Isometric (Low)`.
- When a player's first person view is bigger than 2560x1440, every mode except Custom renders it with no more pixels than it would at 2560x1440, then scales it up to fit. In split screen that goes by each player's own part of the screen. Custom renders at its `RenderScale` at any size, and so does the isometric view.
- `F8` cycles the picture style: Vivid, Natural, Bleak, Warm and Cold. Each new style fades in over most of a second.
- In split screen each player has their own graphics mode and picture style. Tap `F9` or `F8` once for player 1, twice for player 2, three times for player 3 and four times for player 4, or hold it to change everyone. Settings for players 2, 3 and 4 are under `[Players]` in `CameraUnlock.ini` and on the options page.

Every key saves its new setting to `CameraUnlock.ini` as you press it, and `Delete` also saves which view your next load starts in (`FirstPerson`). There's no key to turn head tracking on or off: use the Head tracking on tick box at the top of the mod's options page, or `EnableOnStartup` in `CameraUnlock.ini`. There's no recenter key either, so centre the view in your tracker app.

### Mouse and keyboard

The mouse turns the view, and the movement keys move you relative to where you're looking. If you walk or sneak in any direction other than straight ahead, diagonals included, your character keeps facing where you look, in the game's aiming stance but at the speed you'd walk or sneak forwards. Jogging and sprinting go straight ahead or on a forward diagonal, at the game's own pace. Try them sideways or backwards and you walk. That's how it works offline and on servers without PvP. Set `FacesTheView=false` if you'd rather your character always turned to face the way it's going, as in vanilla. On a PvP server your character only keeps facing where you look when walking sideways or backwards, at the game's slower aiming speed: see [Multiplayer](#multiplayer).

| Input | Action |
| --- | --- |
| Left click | Attack |
| Right click | Aim |
| `Left Shift` | Sprint |
| `Left Alt` | Jog |
| `C` (or your crouch key), `Left Ctrl` | Crouch |
| `E` by default | Interact with the highlighted object |
| `I` by default | Open or close the inventory |
| Middle click | Show the mouse pointer, until you middle click again or press `Esc` |
| Left click with the pointer out | Use what the pointer is on, the same as `E` |
| Right click with the pointer out | Open the menu for what the pointer is on |

**Other controls** - reload, shove, weapon selection and flashlight use your usual keys.

**Toggle to aim** - if you have this game option turned on, aim before you attack.

**Interaction** - put the reticle on a door, window, curtain, light switch, container, item on the ground, radio, TV or crafting station and press `E`. For a window over a counter or a dresser, put the reticle on its glass: `E` opens or shuts it, and the game walks your character over to it first if it can find a way. On an open or broken window, `E` climbs through if there's a floor on the other side to land on. On a fridge, `E` opens the fridge or the freezer, whichever half the reticle is on. On anything else within reach, like a sink or a chair, `E` opens the game's own menu for it. You can also bring out the mouse pointer (middle click, or open the inventory) and right-click something in the view to open its menu, or left-click it to do what `E` would do, the same as clicking in the isometric view.

### Controller and split screen

**Controller** - the controllers ticked in the game's Controller options are the ones that play. If none of the gamepads plugged in is ticked, every one the game recognises gets ticked and plays, and pressing **A** on the main menu gives that controller the menu. Player 1 plays on whatever last used the main menu: the controller that pressed **A**, or the keyboard and mouse once you click or press a key there. Any other controller can join once you're in the game: press **A** on it and choose **Add new player**. A 3Dconnexion SpaceMouse is never treated as a controller. Double-tap **Back** to switch views. The aiming stick turns the view, the right trigger attacks and the left trigger raises a gun's sights.

**More players** - tap `Delete` twice to switch player 2's view, three times for player 3's and four times for player 4's. Hold it to change everyone at once: if anyone is in the isometric view, everyone goes to first person, and holding it again puts everyone in the isometric view. Players on a controller can double-tap **Back** instead. Send player 2's tracker to port **4243**, player 3's to **4244** and player 4's to **4245**.

## Configuration

Most settings are in the game under **Options > Mods > Head Tracking**: head tracking, first person, graphics and the gameplay tweaks (see [The options page](#the-options-page)). First person and the isometric view each have their own graphics quality. First person starts on Automatic, and the isometric view on Low, which is the game's own look. For the keys, the tracker's UDP ports and how far you can lean, edit `CameraUnlock.ini` in the game folder.

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
; true: head tracking is on. Your tracker moves the first person view and turns your character's head in the isometric
; view. false: it is off. No key turns it on or off. The options screen changes it when you apply, and an edit here
; takes effect at the next start.
EnableOnStartup=default
; true: turning your head left or right turns the view around the vertical, which keeps the horizon level while you look
; up or down. false: it turns around the view's own up, which tilts the horizon while you look up or down. In a
; vehicle seat it always turns around the view's own up.
WorldSpaceYaw=default
; true: turning your head turns the view.
; Tracking mode at startup, with PositionEnabled. The mode hotkey changes both.
RotationEnabled=default
; Milliseconds a tracker packet stays current. Once the tracker has sent nothing
; for this long, the mod stops following it until data arrives again.
DataFreshnessMs=default
; true: the mod's first person view is there to switch to. false: saves load in the isometric view whatever FirstPerson
; says, and with FirstPersonInVehicles on, first person comes on as you get into a vehicle. With both off there is no
; first person view, the isometric view is the game's own whatever IsometricQuality says, and nothing more is made
; ready for first person, though what is ready already stays until you leave the save. The first person key, or a
; double tap of Back on a controller, then turns this on and switches to first person, and the graphics key turns it
; on to go to an isometric graphics mode other than Low.
; Takes effect at once. From the next start, off also stops the mod changing the game's own code for this.
FirstPersonEnabled=true
; Vertical field of view of the first person view, in degrees: 40 to 110.
FieldOfView=65.0
; First person graphics mode: Automatic, Low, Medium, High or Custom. Automatic draws less when the frame rate drops
; and more again when it recovers. Custom draws the [Graphics] rows. Every mode but Custom draws at most 2560x1440
; pixels, however large the window.
Quality=Automatic
; Graphics mode of the game's isometric view: Low (the game's own picture), Medium, High or Custom, which draws the
; [Graphics] rows.
IsometricQuality=Low
; Picture style: Vivid (strong contrast and colour), Natural (colours as lit, less contrast), Bleak (washed out, close
; to the game's own look), Warm (golden light and amber shade) or Cold (steel blue shade and pale light). A new
; style fades in over most of a second.
Style=Bleak
; true: saves load in first person, while FirstPersonEnabled is on. Switching the view with the first person key, or a
; double tap of Back on a controller, changes this to match.
FirstPerson=false
; true: getting into a vehicle switches to first person, and getting out switches back to the isometric view, even
; with FirstPersonEnabled off. The first person key still switches the view at any time, and a view it chose is kept
; when you get out.
FirstPersonInVehicles=false
; How tall the first person hills are: 0 is as flat as the game's map, 1 is as the mod makes them, up to 2.0.
; Always 1 where players can fight.
HillHeight=1.5
; true: firing a gun flashes at the muzzle and briefly lights up what is nearby, in every graphics mode but Low.
MuzzleFlash=true
; true: in first person, while the game draws no reticle of its own, a small crosshair with a weapon in hand, or a dot
; without one, shows where the shot lands and what the interact key uses. false: neither is drawn.
Crosshair=true
; true: a two-handed weapon goes back into both hands when your left hand puts down what it held beside it, as the
; inventory's Equip Two Hands does. A light in your left hand is put away when you switch it off, so the weapon goes
; back into both hands, and taking up a light leaves the weapon in your right hand. false: as in the game, the weapon
; stays in your right hand alone, and taking up a light takes it out of your hands. Never on a server where players
; can fight.
RegripTwoHandedWeapons=true
; How two players share one screen: Vertical (side by side) or Horizontal (above and below). Takes effect at once.
; Vertical is the game's own split screen, and from the next start the mod leaves that part of the game alone.
SplitScreen=Vertical

[Smoothing]
; Smoothing for a tracker on this PC that sends to 127.0.0.1. 0 is the least, 1 the most.
LocalSmoothing=default
; Smoothing for every other tracker: one on another device such as a phone, or one on this PC that sends to this PC's
; network address instead of 127.0.0.1. 0 is the least, 1 the most.
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
; true: leaning your head stops at walls instead of moving the view through them. false: the lean is not held off
; walls, so leaning into one can put the view through it.
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
; Switches between first person and the isometric view. Tap once for player 1, twice for player 2, three times for
; player 3, four times for player 4, or hold for every player.
FirstPersonKey=Delete
; Switches between first person and the isometric view in place of FirstPersonKey while Project Viewpoint, another
; mod, runs, as Viewpoint opens its settings window on Delete. Tapped and held as FirstPersonKey is.
FirstPersonKeyBesideViewpoint=End
; Goes to the next graphics mode of the view a player is in: in first person Automatic, Low, Medium, High, Custom
; (Quality), in the isometric view Low, Medium, High, Custom (IsometricQuality), and for players 2, 3 and 4 their
; rows of [Players]. Tap once for player 1, twice for player 2, three times for player 3, four times for player 4,
; or hold for every player.
CycleQualityKey=F9
; Goes to a player's next picture style: Vivid, Natural, Bleak, Warm, Cold (Style, and for players 2, 3 and 4
; their rows of [Players]). Tap once for player 1, twice for player 2, three times for player 3, four times for
; player 4, or hold for every player.
CycleStyleKey=F8
; With two players at the screen, switches between side by side and above and below.
SplitScreenKey=F7

[Light]
; How far the flashlight turns for each degree your head turns, while TorchFollowsHead is on. 1 turns it as far as
; the view, more turns it further, and 0 leaves it where your character faces.
LightMultiplier=default

[FirstPerson]
; true: in first person, a flashlight you carry points where your head turns, as far as LightMultiplier says.
; false: it points the way your character faces, whatever your head does.
TorchFollowsHead=true
; true: in first person you hear from your character's eyes: sounds turn with the view, walls between you and a sound
; muffle it, and rooms echo. false: you hear as the isometric view does, facing north-west on the map.
; Takes effect when the game next starts. When off, the mod does not change the game's own code for this.
HearFromTheEye=true
; true: in first person, lightning strikes as a bolt from the clouds to the ground and lights up the world while it
; flashes. false: the game's own lightning, a flash that brightens the whole map for a moment.
; Takes effect when the game next starts. When off, the mod does not change the game's own code for this.
LightningBolts=true
; true: in first person, in Medium and High, a shot kicks up dust, chips, splinters, sparks or blood where it hits
; and leaves a hole. false: shots leave no mark, as in the game's own view. Shots still break lamps and glass
; and hole doors, walls and fences when ShotsBreakLamps, ShotsBreakGlass, BlastHoles and BulletHoles are on, and
; while any of them is on, the mod keeps changing the game's code for this.
; Takes effect at once. From the next start, off also stops the mod changing the game's own code for this.
ShotImpacts=true
; true: in first person, vehicles, zombies and animals in sight are drawn even past the edge of the game's own
; screen, and so are other players where players cannot fight. false: each is drawn only where the game's own
; view would show it.
FarVehiclesAndZombies=true
; true: while anyone plays in first person, a body that is hit switches to its ragdoll at once, and a first person
; shot pushes it along the shot from where it went in. false: bodies switch to their ragdolls the way the
; game blends them in.
; Takes effect when the game next starts. When off, the mod does not change the game's own code for this.
QuickRagdolls=true
; true: a zombie shot in first person that stays on its feet reacts where the bullet hit: its head snaps back or
; its shoulder spins round. false: it plays only the game's own stagger.
HitFlinch=true
; true: in first person, when you are knocked over, lie down, get up or die, the view follows your character's
; head. false: the mouse keeps the view while the game moves the body, and when you die the view stays where it was.
HeadLedViewWhenDown=true
; true: in first person, items on the ground are shown as the game's own 3D models of them, while the game's 3D
; ground items option is on. false: each lies flat as its picture.
; Takes effect at once. From the next start, off also stops the mod changing the game's own code for this.
ItemsAsModels=true
; true: in first person, people near you wear more detailed skins than the game's: the mod's own, and any finer
; ones in the folder the log names. false: they wear the game's own.
; Takes effect when the game next starts. When off, the mod does not change the game's own code for this.
FinerSkins=true
; true: the game's grey tint for the season, fog and weather is left off the first person picture, which shows those
; with its own lighting. false: the game tints the first person picture as it tints its own view.
; Takes effect when the game next starts. When off, the mod does not change the game's own code for this.
LeaveOutScreenGrey=true
; true: on a server, in first person, the names and speech the game shows over people sit over their heads in the
; picture, and your own are hidden. false: the game shows them where its own view puts them.
; Takes effect when the game next starts. When off, the mod does not change the game's own code for this.
NameTagsAtHeads=true

[Players]
; Player 2's first person graphics mode, as Quality.
SecondPlayerQuality=Automatic
; Player 2's isometric graphics mode, as IsometricQuality.
SecondPlayerIsometricQuality=Low
; Player 2's picture style, as Style.
SecondPlayerStyle=Bleak
; Player 2's view at load, as FirstPerson.
SecondPlayerFirstPerson=false
; Player 2's view in vehicles, as FirstPersonInVehicles.
SecondPlayerFirstPersonInVehicles=false
; Player 3's first person graphics mode, as Quality.
ThirdPlayerQuality=Automatic
; Player 3's isometric graphics mode, as IsometricQuality.
ThirdPlayerIsometricQuality=Low
; Player 3's picture style, as Style.
ThirdPlayerStyle=Bleak
; Player 3's view at load, as FirstPerson.
ThirdPlayerFirstPerson=false
; Player 3's view in vehicles, as FirstPersonInVehicles.
ThirdPlayerFirstPersonInVehicles=false
; Player 4's first person graphics mode, as Quality.
FourthPlayerQuality=Automatic
; Player 4's isometric graphics mode, as IsometricQuality.
FourthPlayerIsometricQuality=Low
; Player 4's picture style, as Style.
FourthPlayerStyle=Bleak
; Player 4's view at load, as FirstPerson.
FourthPlayerFirstPerson=false
; Player 4's view in vehicles, as FirstPersonInVehicles.
FourthPlayerFirstPersonInVehicles=false

[Graphics]
; Low: the game's plant sprites. Medium: the mod's trees, with the game's sprites past 40 tiles.
; High: the mod's trees as far as you can see.
TreeQuality=High
; How much of the grass the mod grows blade by blade is grown, as a percentage: 10 to 100.
; Lower, fewer and broader plants cover the same lawns and long grass, which costs less to build and draw. High
; grows all of it and Medium 40. On a server where players can hurt each other it grows all of it.
; With TreeQuality Low the mod grows no grass of its own.
GrassDensity=100
; How far from you lawns are grown blade by blade in first person, in tiles: 8 to 48. Past that, lawns are flat. Higher
; costs more to build and draw. Medium and High grow lawns to 16 tiles. Long grass is not affected.
; With TreeQuality Low the mod grows no grass of its own.
GrassDistance=16
; How much of the window's resolution the first person picture is drawn at, each way: 0.5 to 1.
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
; true: shaded corners, light from things nearby and lit air, and with Reflections, wet roads, floors, glass and water
; that mirror the world.
ShadingAndLight=true
; true: mirrors in rooms show the room and you, your vehicle's mirrors show the road behind, and with
; ShadingAndLight, wet roads, floors, glass and water mirror the world.
Reflections=true
; How much of the first person picture's resolution wall mirrors and window glass draw their reflections at, each
; way: 0.25 to 1. High draws 0.75, Medium 0.5. Lower costs less and looks softer.
ReflectionScale=0.75
; true: the mod's surface, wall and skin pictures at full size. false: half size, using a quarter of the memory.
FullSizeTextures=true
; true: near surfaces get the depth of what they show. false: they are drawn flat.
SurfaceDepth=true
; true: shadow maps 4096 pixels across. false: 2048, using a quarter of the graphics memory.
SharpShadows=true
; true: what people and zombies wear and carry casts a shadow at any distance. false: past 4 tiles only
; their bodies do, which costs less in a crowd.
WholeFigureShadows=true
; true: each frame is blended with the ones before it (temporal anti-aliasing), which smooths grass, leaves and fine
; edges that Antialiasing alone cannot, for a slightly softer picture. Medium and High use it.
FrameBlending=true
; How jagged edges are smoothed, from each frame alone: Off; Low, a quick blend across each edge; Medium, which also
; follows each edge to its ends so long shallow edges come out even; High, which follows them further and catches
; fainter ones too. The Low, Medium and High graphics modes use Low, whose slight softening also calms the shimmer
; of FrameBlending; Medium and High only touch the edges, for a sharper picture.
Antialiasing=Low
; How far away lamps light what is around them with light of their own, like the pool of light under a street lamp,
; in tiles: 0 (the game's light only) to 80. Past that, lamps light the ground as the game does.
LightDistance=60
; true: corpses are drawn as models lying where they fell, as zombies are. false: as the game's flat picture of each,
; laid on the ground, which costs far less where many lie. Low, Medium and High use the flat picture.
CorpseModels=false

[Optimizations]
; true: when the game relights many places at once, the first person view takes in their new light over the next few
; frames, nearest first, so driving past lights stutters less. false: all of it in the frame the game relights it,
; so a far light change can show about a tenth of a second sooner, in longer frames.
SpreadLightUpdates=true
; true: the game's lookups of items by name, of component classes and of character state machines are answered from
; what they found before, and two of its bone updates skip work that changes nothing, in both views. The
; results are the same. false: the game works each one out every time.
; Takes effect when the game next starts. When off, the mod does not change the game's own code for this.
CachedGameLookups=true
; true: while the first person picture covers the screen, the game skips drawing its own view underneath it: its
; weather, floors, objects, shadows, people and the rest. What you see is the same. false: the game draws its
; whole view under the picture every frame.
; Takes effect at once. From the next start, off also stops the mod changing the game's own code for this.
SkipCoveredGameDrawing=true
; true: while the first person picture covers the game's view, what characters wear is built into their skins while
; the render thread waits for the next frame. The skins look the same. false: the game builds them in the
; middle of drawing the frame.
; Takes effect at once. From the next start, off also stops the mod changing the game's own code for this.
SkinsComposedWhileWaiting=true
; true: the game's changes to each chunk are counted as it makes them, so the first person view rebuilds only the
; chunks that changed. What you see is the same. false: the view checks every chunk it draws for changes.
; Takes effect when the game next starts. When off, the mod does not change the game's own code for this.
ChunkChangeCounts=true
; true: in first person, a zombie in a room you could not see into, even with every nearby door and curtain open,
; has no model until you come close enough to see in. false: it keeps its model wherever it stands.
UnseenRoomZombiesUnmodelled=true
; true: the game's map of loaded chunks keeps its centre until you are a tile past a chunk edge, so turning on the
; spot right on an edge does not load a column of chunks again every few seconds. false: as in the game, the
; centre moves to your chunk at once.
; Takes effect at once. From the next start, off also stops the mod changing the game's own code for this.
HoldChunkMapCentre=true
; true: on an NVIDIA graphics card without enough memory for what a graphics mode draws, first person starts the
; session drawing less in every mode. false: every mode starts as it is. Checked when first person first
; starts in a session. Either way, when the card runs out of room for something the mod makes, every mode
; drops a step.
GraphicsMemorySteps=true
; true: a save starts only once the mod's shaders are compiled and the distant land and the skins around you are
; made, so first person does not stutter making them during play. false: the save starts as soon as the game
; is ready, and first person makes them as you play.
; Takes effect at once. From the next start, off also stops the mod changing the game's own code for this.
PrepareWhileLoading=true
; true: under the first person picture, the rooms the game fills with zombies and the squares it counts as seen
; follow what the view sees, a few times a second. false: they follow where the mod has the game work out
; light, which can fill a room you have not looked into. Needs SkipCoveredGameDrawing.
; Takes effect at once. From the next start, off also stops the mod changing the game's own code for this.
SpotBySight=true
; true: the first person view rebuilds chunks on threads of its own, all at once, and shows each one a frame
; later. false: each is rebuilt on the game's own thread as the view reaches it, and shows in that frame.
BuildOnWorkerThreads=true
; true: a chunk the first person view built before some of its sprites or pictures had loaded is rebuilt once
; those have. false: it is rebuilt whenever any sprite or picture loads, which for the first seconds after
; arriving somewhere is most frames.
RebuildForOwnSprites=true
; true: as chunks load, what HiddenWallDecor reads from a building for each room it decorates is kept, and only
; the chunks the game has changed since are read again. The rooms come out the same. Needs ChunkChangeCounts.
; false: the whole building is read again for each chunk that loads, which can hitch in a tall building.
HiddenWallReadsKept=true
; true: what a building's room names say, what kind of place each room is, whether the building is a home and what
; each picture shows are worked out once and kept. The rooms and their pictures come out the same.
; false: they are worked out again for each room, which can hitch in a tall building.
BuildingFactsKept=true

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
; true: the game's sprites. false: windows, doors, curtains, handrails, chain link fences, open stairs, railway
; track and wall finishes built by the mod.
VanillaBuildings=false
; true: the game's own skins for zombies. false: the mod's skins, with lifelike faces, for zombies near you.
VanillaZombies=false
; true: the game's own skins for people. false: the mod's skins, with lifelike faces, for your character and other
; living people.
VanillaPeople=false
; true: the game's own arms and hands on a held weapon, for a mod that changes the arms or the weapons. false: the
; mod's arms and hands, which work the bolt, slide and magazine.
VanillaArms=false
; true: the game's own pictures of clothes and hair. false: clothes and hair near you are sharpened, with the weave,
; knit, grain or strands of what they are made of.
VanillaClothes=false
; true: the game's own skins for animals. false: the mod's skins for animals near you, with the hair of their coats.
VanillaAnimals=false
; true: the game's own flames and smoke, standing upright where they burn. false: the game's flames with the mod's
; smoke, embers and firelight for burning buildings, Molotovs, campfires, fireplaces, wood stoves, burn barrels
; and cooking pits.
VanillaFire=false
; true: the game's own inside of the vehicle you sit in, with its glass cut away. false: the mod's model of the inside,
; with its dashboard, instruments, wheel, seats and doors.
VanillaCabins=false

[Rules]
; true: your shots go on through stud walls, plank walls, shut doors and windows, losing damage to each by what it
; is made of. false: they stop at them, as in the game. On a server where players can hurt each other the game's
; rule applies.
; Takes effect at once. With this and FirstPersonEnabled both off, the mod stops changing the game's own code for
; this from the next start.
ShotsThroughWalls=true
; true: offline, a shot breaks a lamp it passes through and a vehicle light it hits. false: lamps stay lit, as in the
; game. On a server the game's rule applies.
ShotsBreakLamps=true
; true: offline, a shot breaks the glass of every shut window it crosses, whether or not it hits anything behind it.
; false: only a shot that hits someone through a window breaks it, as in the game. On a server the game's rule
; applies.
ShotsBreakGlass=true
; true: offline, first person shots to a zombie's legs slow it and drop it to a crawl, shots to its arms leave them
; hanging, enough take the limb off, and a killing head shot can take the head off or burst it. false: hits are
; the game's. On a server the game's rule applies.
ShotsWoundLimbs=true
; true: offline, a vehicle going faster than walking pace knocks down fences, street lamp posts and road barricades,
; drives through bushes without slowing and keeps its engine's full power off the road. false: the game's rules
; for all of it. On a server the game's rules apply.
; Takes effect at once. From the next start, off also stops the mod changing the game's own code for this.
VehiclesSmashThrough=true
; true: walking or sneaking sideways or backwards in first person, your character keeps facing where you look, and
; you jog or sprint only straight ahead or on a forward diagonal. false: it always turns, as in the game. On a
; server where players can hurt each other it keeps facing where you look when going sideways and backwards, on
; or off, at the slower pace the game gives its aiming stance.
FacesTheView=true
; true: a tree stops your shots only where its trunk is drawn, and a shot past the trunk goes on. false: a tree stops
; every shot across its square, as in the game. On a server where players can hurt each other the game's rule
; applies.
ShotsPastTrees=true
; true: in the isometric view a zombie shows as soon as nothing stands between your character and it, within the
; reach of your character's sight by the game's rules. false: the game's own sight, which can show a zombie late
; or only once it is close. On a server where players can hurt each other the game's rule applies.
; Takes effect at once. From the next start, off also stops the mod changing the game's own code for this.
ZombiesSeenAtOnce=true
; true: in first person, the south and east walls of a room, which the isometric view never shows from inside, get
; pictures drawn by the mod and never saved. false: nothing is drawn on them, and nothing is added to them
; whatever HiddenWallDecorSaved says. On a server the pictures are drawn too.
; Takes effect at once. With this and FirstPersonEnabled both off, the mod stops changing the game's own code for
; this from the next start.
HiddenWallDecor=true
; true: offline, while HiddenWallDecor is on, those walls also get the game's own mirrors, clocks and coat racks where
; the room's other walls say they belong, the first time the room is loaded, and those stay in your save.
; false: nothing is added to the save, and what was added before stays. On a server nothing is added.
HiddenWallDecorSaved=true
; true: offline, a shotgun blast in first person whose pellets land close together on a wooden door or a wooden
; fence blows a ragged hole through it that you can see and shoot through, and the pellets that miss the hole
; leave marks on a door. The holes stay in your save. false: shots leave doors and fences whole. On a server
; doors and fences stay whole.
BlastHoles=true
; true: offline, while ShotsThroughWalls is on, a round you fire in first person that goes through a door, a wall
; or a fence leaves a small hole where it went in and a wider, splintered one where it came out, which you can
; see through. The holes stay in your save. false: rounds still go through, and leave only a mark. On a server
; nothing is holed.
BulletHoles=true
; true: in first person, sitting in a vehicle with a gun, you can fire through any part of your seat's window, and
; the shot breaks it. false: as in the game, the window has to be opened or broken first. On a server where
; players can hurt each other, the game's rule applies.
ShotsFromVehicleSeats=true
; true: in first person, driving with the keyboard, you can still steer, accelerate and brake while aiming a gun.
; false: as in the game, aiming a gun takes over the controls. With a controller, and on a server where
; players can hurt each other, the game's rule applies.
DriveWhileAiming=true
; true: offline, driving in first person, hills pull a vehicle down them and can throw it into the air over a crest
; at speed. false: vehicles move as on the game's level ground. On a server they always do.
; Takes effect at once. From the next start, off also stops the mod changing the game's own code for this.
HillsMoveVehicles=true
; true: in first person, a light in your hands is switched off when you take the driver's seat, since your hands
; are on the wheel. false: it stays as it was.
HandLightOutAtTheWheel=true

[Fixes]
; true: stops the game freezing for good while it sets up a burnt-down building whose door or window reaches into a
; chunk that has not loaded yet. false: the game's own code, which can loop on that door forever.
; Takes effect when the game next starts. When off, the mod does not change the game's own code for this.
BurntBuildingFreezeFix=true
; true: the game's window is centred in the part of the monitor the taskbar leaves free. false: the game centres it
; on the whole main monitor, which can leave part of it under the taskbar.
; Takes effect when the game next starts. When off, the mod does not change the game's own code for this.
CentreTheWindow=true
; true: the controllers you tick in the game's options stay ticked, a 3Dconnexion 6-axis mouse is never used as a
; controller, and the main menu goes to the controller whose A button was pressed. false: the game picks its
; controllers, which with none ticked is the first one, a SpaceMouse if one was plugged in first.
; Takes effect when the game next starts. When off, the mod does not change the game's own code for this.
ControllerChoiceFix=true

[Voice]
; true: your character's mouth moves with what you say into your microphone: the jaw drops for "ah", the lips spread
; for "ee" and round for "oo". Online, other players' mouths move while the game's voice chat plays them, and
; other players with this mod see your mouth move. The sound is never recorded or sent. false: no microphone is
; opened. Takes effect at once, but at the next start for the people the game draws itself, as in the isometric view
; at Low. With this and FirstPersonEnabled both off, the mod stops changing the game's own code for this from the
; next start.
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

### The options page

**Options > Mods > Head Tracking** has every setting from `CameraUnlock.ini` except the keys, the tracker's UDP ports, the lean limits (the `PositionLimit` settings), lean collision (the `Collision` settings) and `DataFreshnessMs`. Changes take effect, and are saved to `CameraUnlock.ini`, when you apply them with **APPLY** or **ACCEPT**. If you leave without applying, nothing changes.

The page is split into Quick settings, First person, Head tracking, Graphics, Custom graphics, Mod content, Gameplay tweaks, Game fixes, Optimizations, Split screen, a section for each of players 2, 3 and 4, Voice, and Keys, which lists the keys and where to change them.

Custom graphics shows the settings behind player 1's first person graphics mode, and choosing another mode shows that mode's settings there. Changing any of them switches player 1's first person graphics to Custom. Every player set to Custom uses these settings, in first person or the isometric view.

Each part of the mod, gameplay tweak, game fix and optimization has its own tick box. Where a setting's description says that off stops the mod changing the game's own code, turning it off means the mod leaves that code alone from the next start, so another Java mod that changes the same methods finds the game's original code there.

**Quick settings**, at the top of the page, has the head tracking switch and five lists that each change a whole group of settings at once:

- **Head tracking on** - on or off, saved as `EnableOnStartup`. No key turns it on or off.
- **Everything** - `On` ticks every box on the page except Talking mouths, Horizon-relative head turns, the Custom graphics settings, and each player's Load saves in first person and First person in vehicles. It also turns head tracking on, and switches any player whose first person graphics are on Low to Automatic. `Off` unticks the same boxes and First person in vehicles, which brings back the game's own content, sets every player's graphics to Low (the game's own look) in both views, and puts split screen back to side by side, as in vanilla. Head tracking and Talking mouths stay as they are, so you can turn back on just the parts you want.
- **First person** - `On` turns first person on and First person in vehicles off for every player, without changing which view their saves load in. `In vehicles only` unticks First person view and ticks First person in vehicles for every player: saves load in the isometric view, getting into a vehicle switches to first person and getting out switches back. `Off` unticks both, so there is no first person view until you press `Delete`, which ticks First person view again.
- **Enhanced graphics** - `On` ticks every box under Graphics and Mod content, and switches any player whose first person graphics are on Low to Automatic. `Off` unticks them, which brings back the game's own content, and sets every player's graphics to Low in both views.
- **Gameplay tweaks** - ticks or unticks every box under Gameplay tweaks.
- **Optimizations** - ticks or unticks every box under Optimizations.

Picking an option in one of these lists changes the settings it covers on the page, and you can still change any of them before you apply. Each list shows the option your settings match, or `Mixed` if they don't match any.

### Project Viewpoint

If you run [Project Viewpoint](https://steamcommunity.com/sharedfiles/filedetails/?id=3809306528) alongside this mod, head tracking turns Viewpoint's camera too (yaw, pitch and roll, but not lean). Head tracking has to be on for this: the Head tracking on tick box at the top of the mod's options page, or `EnableOnStartup`.

Viewpoint opens its settings window on `Delete`, so while Viewpoint runs this mod switches first person on `End` instead (`FirstPersonKeyBesideViewpoint`), and the hint on screen names that key.

## Troubleshooting

- **No tracking** - start your tracker, check its address and port, then press `Delete` once a save has loaded.
- **Nothing arrives from your phone** - let it through Windows Firewall, as described under [Phone](#phone).
- **The game won't start** - delete `ProjectZomboid64.site.json` from the game folder and the game starts without the mod. If you installed by hand, make that file again (steps 4 and 5) and check it for a missing or extra comma.
- **Problems with sights or scopes** - press `Insert` until you're on **Sights locked**.
- **The main menu says the mod is not running** - the game version is one the mod doesn't work with, or the mod hit an error as it started. `ProjectZomboidHeadTracking.log` says which. The game runs as it does without the mod.
- **`Delete` doesn't turn first person on** - the screen says why: another mod or a game update changed something it needs, or it stopped after an error, in which case it stays off until you restart the game. `ProjectZomboidHeadTracking.log` has the details.
- **The screen says a setting was not saved** - the mod couldn't write `CameraUnlock.ini`, or couldn't read it when the game started. The change still applies until you quit. `ProjectZomboidHeadTracking.log` says why.
- **First person won't turn on, and the screen says it needs OpenGL 3.3** - your graphics driver doesn't provide what's listed under [Requirements](#requirements). `ProjectZomboidHeadTracking.log` says what's missing.
- **High looks like Medium or Low** - on an NVIDIA graphics card with less than 6 GB, or when the card runs out of memory, the mod scales every mode down so the game keeps running, and tells you so on screen. The `video memory:` lines in the log show what the card reported and what was turned down.

For help, ask on [Discord](https://discord.com/invite/dxyZdyFNT9) or [report an issue](https://github.com/itsloopyo/project-zomboid-headtracking/issues), and include `ProjectZomboidHeadTracking.log` from the game folder.

## Updating

Steam keeps the Workshop version up to date for you. With the standalone installer, run the new release's `install.cmd`, and run it again after every game update too. For a manual install, copy every `.jar` from the new release's `plugins` folder over the old ones and redo steps 4 and 5. Your settings in `CameraUnlock.ini` are kept either way.

## Uninstalling

For the Workshop version, disable the mod or unsubscribe, then restart the game, since ZombieBuddy can't take Java code back out of a running game. Otherwise run `uninstall.cmd`, or delete the files listed under [Manual installation](#manual-installation). Your settings in `CameraUnlock.ini` stay unless you delete that file too.

## Building from source

You need [pixi](https://pixi.sh), which brings Java 25 and Node with it. The first build downloads cameraunlock-core's WebAssembly toolchain (about 620 MB), once per user.

```powershell
git clone --recurse-submodules https://github.com/itsloopyo/project-zomboid-headtracking.git
cd project-zomboid-headtracking
pixi run package
```

`pixi run package` builds the mod, runs the tests and writes the installer ZIP to `release\`.

## Community & Support

- Discord: [Loop's Head Tracking Hangout](https://discord.com/invite/dxyZdyFNT9) - setup help, bug reports, and new-release announcements
- [Lopari](https://lopari.app) - free Windows launcher with one-click install and launch for the released head-tracking mods
- [Headcam](https://headcam.app) - my free app that turns your iPhone or Android phone into the head tracker

## License

[MIT License](LICENSE). Third-party credits and licences are in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

## Credits

Thanks to [The Indie Stone](https://theindiestone.com) for Project Zomboid, and to [OpenTrack](https://github.com/opentrack/opentrack) for the UDP tracking protocol.

The pictures the mod hangs on walls and shows on TVs are screenshots taken in Project Zomboid, and its stained glass windows are redrawn from the game's own. Thanks to The Indie Stone for creating Project Zomboid (https://projectzomboid.com/), which made this possible. This is an unofficial fan production for non-commercial purposes made under the [Indie Stone Terms](https://projectzomboid.com/blog/support/terms-conditions/).

## Disclaimer

This is an unofficial, non-commercial fan production under the [Indie Stone Terms](https://projectzomboid.com/blog/support/terms-conditions/), not affiliated with or endorsed by The Indie Stone.
