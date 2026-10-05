# Third-Party Notices

ProjectZomboidHeadTracking.jar holds only this project's own classes, two of
which carry the pieces of shader code listed under Shader code below, and
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

- **Files:** every `materials/*.jpg` and `materials/*.png` inside the mod's jar but `paint.jpg`: asphalt, gravel, soil, grass, shingles, wood, eight barks (`bark-*.jpg`), planks, carpet, leaves, vinyl, cloth, jersey, litter, rock, frond, ivy and sprigs
- **Version:** made with Stable Diffusion XL base 1.0, from text prompts alone, for this mod. No image from Project Zomboid or anywhere else was given to the model.
- **License:** the model is under the CreativeML Open RAIL++-M License, https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0/blob/main/LICENSE.md, Copyright (c) 2023 Stability AI. That licence says its licensor claims no rights in what the model generates, and that no use of the output may go against the licence's use restrictions. The textures are grain for surfaces in a game and are used for nothing else.
- **Upstream:** https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0
- **Usage:** The first person view lays them over open ground, roofs, walls and wooden floors to give each the grain of what it is made of, and draws tree trunks, each in the bark of its species, the fallen leaves under trees, boulders, ferns and the ivy on tree trunks with them. `sprigs.png` holds eight pictures of leaves, each cut out of the white ground it was generated on by `tools/materials/pack.py`: the crowns of trees and bushes are built of them. The prompts are in `tools/materials/generate.py`.
- **Bundled:** the textures, yes. The model is not.
- **Not among them:** `materials/paint.jpg` is filtered noise made by `tools/materials/paint.py` in this repository. No model was used for it.

---

## Shotgun model

- **Files:** `models/shotgun.mesh.gz`, `models/shotgun.colour.gz` and `models/shotgun.surface.gz` inside the mod's jar
- **License:** MIT, this project's own, for the model and for what `tools/models/shotgun.py` paints on it. The walnut of its stock and fore-end and the grain of its rubber pad are baked from two pictures made with Stable Diffusion XL base 1.0 from text prompts alone, under the same terms as the generated material textures above. The prompts are in `tools/models/shotgun_textures.py`. No image from Project Zomboid or anywhere else was given to the model.
- **Usage:** The first person view draws it in the viewer's hands in place of the game's model of `Base.Shotgun`.
- **Bundled:** yes. The image model is not.

The model is built from nothing by `tools/models/shotgun.py` in this repository,
a Blender script: a twelve bore pump action field gun of no maker's pattern. No
mesh or texture is read from Project Zomboid or from anywhere else. Three
measures of the game's own shotgun decide how the model is held, and nothing of
its shape: the game's model script says where that gun's muzzle is from its
grip, which the mod reads while the game runs and sizes the model to, and how
far along that gun its stock, receiver and fore-end lie was read once from the
outline of the game's mesh, so that the hands the game poses for its gun fall on
the grip and the fore-end of this one.

---

## Furniture models

- **Files:** `models/furniture.bin.gz` inside the mod's jar
- **License:** MIT, this project's own
- **Usage:** The first person view draws them where the game stands a piece of furniture.
- **Bundled:** yes.

Every model is built from nothing by `tools/models/furniture.py` in this
repository, a Blender script. No mesh, texture or measurement is read from
Project Zomboid or from anywhere else.

---

## Shader code

The jar's shaders are this project's own, with two exceptions. Both are in the
jar as shader source text.

### Hash without Sine

- **Where:** `chance` and `chanceAt` in the grain shader of `CharacterDraw.java`, which are that work's `hash13` and `hash11`
- **License:** MIT
- **Upstream:** https://www.shadertoy.com/view/4djSRW
- **Usage:** The pseudo-random numbers behind the fine grain of skin, hair and cloth.
- **Bundled:** yes.

Copyright (c) 2014 David Hoskins.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

### FXAA pass

- **Where:** the edge blend in the `SMOOTH` shader of `PipelineShaders.java`
- **License:** BSD-3-Clause
- **Upstream:** https://github.com/mattdesl/glsl-fxaa (`fxaa.glsl`), whose header says it is based on the code on geeks3d.com and comes from https://github.com/mitsuhiko/webgl-meincraft
- **Usage:** Smooths the stair-steps of edges in the finished first person picture.
- **Bundled:** yes, changed: the names are this project's, the constants are written out as numbers, it reads the picture at this project's own coordinates, and the sharpening that follows it is this project's own.

Copyright (c) 2011 by Armin Ronacher.

Some rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.

    * The names of the contributors may not be used to endorse or
      promote products derived from this software without specific
      prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

### Acknowledgement

The dither that spreads the picture's samples from pixel to pixel is the
interleaved gradient noise formula Jorge Jimenez presented in "Next Generation
Post Processing in Call of Duty: Advanced Warfare" (SIGGRAPH 2014,
https://www.iryoku.com/next-generation-post-processing-in-call-of-duty-advanced-warfare/).
It is a published formula of three constants. No code was taken.

---
