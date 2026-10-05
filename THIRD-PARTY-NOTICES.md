# Third-Party Notices

ProjectZomboidHeadTracking.jar holds only this project's own classes, and
ProjectZomboidHeadTracking.dll this project's own code and cameraunlock-core's.
The components below are what they ship beside, link against at run time, or
interoperate with. Each remains the property of its authors and is used under
its own licence.

This repository and its release ZIP contain no Project Zomboid code, no
extracted assets and no data files. The files under `stubs/` are signature-only
stand-ins written for this project: each lists the class and member names the
agent calls, with the signatures the game's classes and LWJGL's declare, and
has no body beyond a throw. They are on the compile classpath only and are
never packed into the jar.

## Project Zomboid

- **Version:** the copy the player owns
- **License:** proprietary, The Indie Stone. Mods are permitted under the [Project Zomboid Terms and Conditions](https://projectzomboid.com/blog/support/terms-conditions/) and the [Modding Policy](https://projectzomboid.com/blog/modding-policy/).
- **Upstream:** https://projectzomboid.com
- **Usage:** The agent is loaded by the game's own Java runtime and calls the game's classes as they run, including the `org.lwjglx` window and mouse classes inside the game's jar. The rewrites it makes to game classes happen in memory as they load.
- **Bundled:** no. The mod needs a purchased copy of the game and carries no part of it.

Project Zomboid is developed and published by The Indie Stone, who own the game
and its name. They are named here and in the README to say what the mod is for.
This is an unofficial mod, given away free, and is not affiliated with or
endorsed by The Indie Stone.

Thanks to The Indie Stone for creating Project Zomboid
(https://projectzomboid.com/), which made this possible. This is an unofficial
fan production for non-commercial purposes made under the Indie Stone Terms
(https://projectzomboid.com/blog/support/terms-conditions/).

---

## cameraunlock-core

- **Version:** commit 4a253c33fe06814547c680a3b633029b3319d2c5
- **License:** MIT
- **Upstream:** https://github.com/itsloopyo/cameraunlock-core
- **Usage:** Its C++ settings file reader and writer and its hotkey poller are compiled into `ProjectZomboidHeadTracking.dll`, which the jar loads. `install.cmd` and `uninstall.cmd` run the installer script bodies it provides. None of its code is compiled into the jar.
- **Bundled:** yes. Compiled into the DLL, and the script bodies go in the release ZIP's `shared` folder.

Copyright (c) 2026 itsloopyo

---

## OpenTrack

- **Version:** n/a
- **License:** ISC
- **Upstream:** https://github.com/opentrack/opentrack
- **Usage:** The mod's own receiver reads the OpenTrack UDP pose datagram; it is protocol interoperability only.
- **Bundled:** no. No OpenTrack code is included or linked.

---

## LWJGL

- **Version:** the copy Project Zomboid ships
- **License:** BSD-3-Clause
- **Upstream:** https://github.com/LWJGL/lwjgl3
- **Usage:** The agent calls its OpenGL, GLFW and Win32 bindings (`org.lwjgl.opengl`, `org.lwjgl.glfw`, `org.lwjgl.system.windows`) to draw the first person view and place the window.
- **Bundled:** no. The names resolve to the copy inside the game's jar at run time.

---

## JOML

- **Version:** the copy Project Zomboid ships
- **License:** MIT
- **Upstream:** https://github.com/JOML-CI/JOML
- **Usage:** The agent reads and fills the `org.joml.Matrix4f` and `org.joml.Vector3f` values that the game's own fields and methods hold, take and return.
- **Bundled:** no. The names resolve to the copy inside the game's jar at run time.

---

## Kahlua

- **Version:** the copy Project Zomboid ships
- **License:** MIT
- **Upstream:** https://github.com/krka/kahlua2
- **Usage:** The agent reads the game's Lua tables as `se.krka.kahlua.vm.KahluaTable` and calls the game's Lua functions through its `LuaCaller`, to tell the game's panels apart and to show and hide them.
- **Bundled:** no. The names resolve to the copy inside the game's jar at run time.

---

## Generated material textures

- **Files:** every `materials/*.rgb.gz` and `materials/*.rgba.gz` inside the mod's jar: asphalt, gravel, soil, grass, shingles, wood, bark, planks, carpet, leaves, vinyl, cloth, jersey, litter, rock, frond and ivy
- **Version:** made with Stable Diffusion XL base 1.0, from text prompts alone, for this mod. No image from Project Zomboid or anywhere else was given to the model.
- **License:** the model's, CreativeML Open RAIL++-M, https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0/blob/main/LICENSE.md
- **Upstream:** https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0
- **Usage:** The first person view lays them over open ground, roofs, walls, wooden floors and the crowns of trees to give each the grain of what it is made of, and draws the fallen leaves under trees, boulders, ferns and the ivy on tree trunks with them.
- **Bundled:** the textures, yes. The model is not.
- **Not among them:** `materials/paint.rgb.gz` is filtered noise made by `tools/materials/paint.py` in this repository. No model was used for it.

---
