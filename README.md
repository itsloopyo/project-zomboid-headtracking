# Project Zomboid Head Tracking

![Project Zomboid running with this mod](https://raw.githubusercontent.com/itsloopyo/project-zomboid-headtracking/main/assets/readme-clip.gif)

Play Project Zomboid in first person with a rebuilt world, new graphics and head tracking. Works with any OpenTrack compatible tracker. No VR headset required.

## Features

- **First person on foot and in vehicles** - switch views with one key. Play with a mouse and keyboard or controller, with sound that follows where you look.
- **A rebuilt world** - 3D buildings and furniture, new textures, detailed plants, rolling hills and distant scenery.
- **New graphics in both views** - lighting, shadows, models and textures in Medium and High. High adds reflections on roads, floors, glass and water, and light beams through fog.
- **First person combat** - custom arms and hands, melee swings, recoil and reload animations. Medium and High add shotgun and hunting rifle models and magnified scopes.
- **Vehicle interiors** - dashboards, seats and working mirrors, with a view that tilts as the vehicle moves.
- **Full 6DOF head tracking** - turn left and right (yaw), look up and down (pitch), tilt your head (roll), and move left and right, up and down, forward and back.
- **Decoupled look and aim** - look around with your head while your mouse or controller controls where you aim.
- **Multiplayer and split screen** - join a server; only you need the mod. Two local players can each have their own first person view and head tracker.
- **Adjustable graphics** - automatic or manual quality, three picture styles, field of view, hill height and optional replacement models and textures.
- **Works with any OpenTrack compatible tracker** - free options available for PC, iOS and Android

## Requirements

- [Project Zomboid on Steam](https://store.steampowered.com/app/108600/Project_Zomboid/), tested on build **42.21.0**. Other stores have not been tested.
- [OpenTrack](https://github.com/opentrack/opentrack) with a tracking source, or an app that sends the OpenTrack UDP protocol
- Windows 10 or 11, 64-bit

## Installation

### Lopari

Once available in [Lopari](https://lopari.app), choose **Project Zomboid** and click **Play with head tracking**.

### Standalone installer

1. Download `ProjectZomboidHeadTracking-v<version>-installer.zip` from [Releases](https://github.com/itsloopyo/project-zomboid-headtracking/releases).
2. Extract it and double-click `install.cmd`.
3. Follow [Tracker setup](#tracker-setup) below.
4. Launch the game, load a save or join a server, and press `Delete` for first person.

If the installer cannot find the game, run it with the folder containing `ProjectZomboid64.exe`:

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

**Reinstalling replaces custom edits to the mod's `.site.json`.** An existing file without this mod's JAR filename is left alone: installation stops. Removing the mod's file restores normal game startup.

## Tracker setup

### OpenTrack

1. Install [OpenTrack](https://github.com/opentrack/opentrack/releases).
2. Choose your **Input**. For a webcam, select **neuralnet tracker** and your camera. For a VR headset, connect it, start SteamVR and select the SteamVR tracker.
3. Set **Output** to **UDP over network**, host `127.0.0.1`, port `4242`.
4. Press **Start**.

Use your tracker's centering control to reset your view. Adjust sensitivity and axis direction in the tracker too.

### Phone

Use an app that sends the **OpenTrack UDP protocol** to your PC's local network IP address on port `4242`. [Headcam](https://headcam.app), my free app, smooths tracking on the phone and can send directly. If your feed shakes, send it through OpenTrack's UDP input on another port and use its filter before forwarding to `127.0.0.1:4242`.

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
| Hold middle mouse | Show the pointer |
| Hold middle mouse and right-click | Open a context menu |

**Other controls** - reload, shove, weapon selection and flashlight use your existing keys.

**Toggle to aim** - if this game option is enabled, aim before attacking.

**Interaction** - use `E` for doors and windows. Mouse clicks target the underlying isometric view.

### Controller and split screen

**Controller** - double-tap **Back** to switch views. The aiming stick turns the view, right trigger attacks and left trigger raises firearm sights.

**Second player** - double-tap `Delete` to switch view, or hold it to switch both players. Send their tracker to port **4243**.

**PvP** - leaning does not move shots; zombies and room contents follow the game's visibility rules.

## Settings

Use **Options > Mods > Head Tracking** for tracking and graphics. For keys, ports and smoothing, edit `CameraUnlock.ini` beside the game executable. See the [configuration reference](config/CameraUnlock.ini).

## Help, updates and removal

- **No tracking** - start the tracker, check its address and port, then press `Delete` in a loaded game.
- **Sights or scope problems** - press `Insert` to select **sights locked**.
- **Update** - run the new release's `install.cmd`. Rerun it after a game update too.
- **Uninstall** - run `uninstall.cmd`. Your `CameraUnlock.ini` settings stay.

For help, use [Discord](https://discord.com/invite/dxyZdyFNT9) or [report an issue](https://github.com/itsloopyo/project-zomboid-headtracking/issues). Include `ProjectZomboidHeadTracking.log` from the game folder.

## License and credits

[MIT License](LICENSE). Third-party credits and licences are in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

Thanks to [The Indie Stone](https://theindiestone.com) for Project Zomboid. This is an unofficial, non-commercial fan production under the [Indie Stone Terms](https://projectzomboid.com/blog/support/terms-conditions/), not affiliated with or endorsed by The Indie Stone.
