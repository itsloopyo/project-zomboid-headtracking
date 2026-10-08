# Third-Party Notices

ProjectZomboidHeadTracking.jar holds this project's own classes, three of which
carry the pieces of shader code listed under Shader code below, and two classes
of cameraunlock-core's. CameraUnlockCore.dll is cameraunlock-core's code alone.
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

- **Version:** commit 8d0e04ec9a5cdcfb4dfca7c387c87eabe1ca4cfe
- **License:** MIT
- **Upstream:** https://github.com/itsloopyo/cameraunlock-core
- **Usage:** `CameraUnlockCore.dll` is built from it: the OpenTrack receiver, the pose pipeline, the settings file reader and writer, the hotkey poller and the log, which the jar loads from beside itself. Its two Java classes, the binding to that DLL and the class the game's launcher starts the mod through, are compiled into the jar. `install.cmd` and `uninstall.cmd` run the installer script bodies it provides.
- **Bundled:** yes. The DLL, the two classes in the jar, and the script bodies in the release ZIP's `shared` folder.

Copyright (c) 2026 itsloopyo

---

## OpenTrack

- **Version:** n/a
- **License:** ISC
- **Upstream:** https://github.com/opentrack/opentrack
- **Usage:** cameraunlock-core's receiver reads the OpenTrack UDP pose datagram; it is protocol interoperability only.
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

## FMOD

- **Version:** the copy Project Zomboid ships
- **License:** proprietary, Firelight Technologies
- **Upstream:** https://www.fmod.com
- **Usage:** The agent calls the game's own binding to it, `fmod.javafmod`, at the one place the game sets its listener, to hand over the way the first person view faces in place of the way the game's own view does.
- **Bundled:** no. The name resolves to the class inside the game's jar at run time, which calls the copy of FMOD in the game's folder.

---

## Generated material textures

- **Files:** every `materials/*.bc` inside the mod's jar but `paint.bc` and `sprigs-form.bc`, each packed by `tools/textures/compress.py` from the picture of its name in `src/textures/materials` (the eight barks into the one `bark.bc`): asphalt, gravel, soil, grass, shingles, wood, eight barks (`bark-*.jpg`), planks, carpet, leaves, vinyl, cloth, jersey, litter, rock, frond, ivy and sprigs
- **Version:** made with Stable Diffusion XL base 1.0, from text prompts alone, for this mod. No image from Project Zomboid or anywhere else was given to the model.
- **License:** the model is under the CreativeML Open RAIL++-M License, https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0/blob/main/LICENSE.md, Copyright (c) 2023 Stability AI. That licence says its licensor claims no rights in what the model generates, and that no use of the output may go against the licence's use restrictions. The textures are grain for surfaces in a game and are used for nothing else.
- **Upstream:** https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0
- **Usage:** The first person view lays them over open ground, roofs, walls and wooden floors to give each the grain of what it is made of, and draws tree trunks, each in the bark of its species, the fallen leaves under trees, boulders, ferns and the ivy on tree trunks with them. `sprigs.png`, which `sprigs.bc` is packed from, holds eight pictures of leaves, each cut out of the white ground it was generated on by `tools/materials/pack.py`: the crowns of trees and bushes are built of them. `sprigs-form.png` is worked out from `sprigs.png` by the same script, and holds which way each leaf is turned and how much light it lets through. The prompts are in `tools/materials/generate.py`.
- **Bundled:** the textures, yes. The model is not.
- **Not among them:** `paint.bc` is packed from `paint.jpg`, filtered noise made by `tools/materials/paint.py` in this repository. No model was used for it. Nor are the six `*.high.jpg` beside the sources, which the packing puts in the fourth channel of the texture of the same name and which say how far each point of a surface stands out of it: `tools/materials/heights.py` in this repository works each out from the texture of the same name, by arithmetic on its light and dark.

## Generated wall pictures

- **Files:** every `pictures/*.jpg` inside the mod's jar: the family photographs, paintings, sports posters, band and film posters, boards and certificates that hang on walls in the first person view
- **Version:** made with Stable Diffusion XL base 1.0, from text prompts alone, for this mod. No image from Project Zomboid or anywhere else was given to the model. The people in the photographs and on the posters are nobody: the model drew them from a description such as "a grandmother with curly grey hair and glasses". The five portraits of American statesmen are paintings of those men as the prompt names them, and no copy of any one canvas.
- **License:** the model is under the CreativeML Open RAIL++-M License, https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0/blob/main/LICENSE.md, Copyright (c) 2023 Stability AI. That licence says its licensor claims no rights in what the model generates, and that no use of the output may go against the licence's use restrictions. The pictures are decoration on walls in a game and are used for nothing else.
- **Upstream:** https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0
- **Usage:** The first person view shows each in the frame or on the sheet where the game has its own small sprite of a picture. Which sprite shows which picture is `pictures/index.txt`, written by `tools/pictures/pack.py` from `tools/pictures/catalogue.py`, where the prompts are. A picture is chosen for a sprite by what the game calls it ("Painting", "Lighthouse") or by what its sprite depicts (a boxer, a family), described in words.
- **Lettering:** `tools/pictures/pack.py` letters the film and band posters and the certificates, in Impact and Georgia as installed with Windows. Only the letters as drawn are in a picture. No font file is bundled. The names of the films are the ones the game gives those posters, and the names of the bands and singers are the ones that can be read on the game's own. The title of the one film the game calls only "Movie", the line under each name, the film company and the two medical schools are made up for the mod.
- **Bundled:** the pictures, yes. The model is not.

## Generated skins for zombies and the living

- **Files:** every `skins/body/*.jpg` inside the mod's jar: the skins zombies wear near the eye in the first person view, one for each sex, skin tone and state of decay, and the skins your character and the other living wear there, one for each sex and each of the game's five skin tones, with a second for a man with hair on his body
- **Version:** baked by `tools/skins/bake.py` in this repository from pictures made with Stable Diffusion XL base 1.0, from text prompts alone, for this mod: a face from straight ahead for each sex and tone in each state of decay, and close pictures of skin, rotted skin, raw flesh and white cotton. No image from Project Zomboid or anywhere else was given to the model. The faces are nobody's: the model drew them from a description such as "the face of a bald dead man, very pale skin, days dead". The states of decay of a face were made by giving the model its own first picture of that face again. The faces of the living were made the same way, one picture each, from a description such as "the face of a bald man thirty five years old, fair light beige skin".
- **License:** the model is under the CreativeML Open RAIL++-M License, https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0/blob/main/LICENSE.md, Copyright (c) 2023 Stability AI, on the same terms as the generated material textures above.
- **Upstream:** https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0
- **Layout:** a skin has to lie on the game's body model as the game's own does. `tools/skins/bake.py` reads `MaleBody.x` and `FemaleBody.x` from an installed copy of the game for where each point of the picture lies on the body and which way the body faces there, and lays the generated pictures on by that. No part of the body models is in the skins or in this repository. Of the game's textures it uses one colour for each of the five tones of the living, what that tone's skin mostly is, so that a character is as dark and as warm in first person as the tone the player chose: each face's picture is made that light and its colour brought part of the way to it. Nothing else of the game's textures is read.
- **Usage:** The game composes what a person wears from the body's skin, dirt, blood and clothes. For a zombie or one of the living near the eye the first person view has the game's own combiner compose it again with the mod's skin in place of the game's, and draws the result. The settled blood and veins on a zombie's skin and the hair on a man's body are arithmetic in `tools/skins/bake.py`, with no model. The prompts are in `tools/skins/generate.py`.
- **Bundled:** the skins, yes. The model is not.


---

## Generated fabrics, T-shirt prints, blood and dirt

- **Files:** inside the mod's jar, `fabric/*.jpg` (eight close photographs of cloth, leather and hair, one channel each), `skins/shirtdecals/*.jpg` (sixty prints for the chest of a T-shirt) and `skins/bloodtextures/*.jpg` (dried blood and dirt), each of the last two with a `.cut.png` beside it that says what of it shows.
- **Version:** made by `tools/skins/fabrics.py` and `tools/skins/prints.py` in this repository with Stable Diffusion XL base 1.0, from text prompts alone, for this mod. No image from Project Zomboid or anywhere else was given to the model. A print is a design of the mod's own on the theme the game's name for that print gives it (a slice of pizza for a pizza place, a wolf for a wolf): no brand, mascot or lettering of the game's is in it, and the game's prints were not read. Any lettering in one is the model's own scribble and spells nothing.
- **License:** the model is under the CreativeML Open RAIL++-M License, https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0/blob/main/LICENSE.md, Copyright (c) 2023 Stability AI, on the same terms as the generated material textures above.
- **Upstream:** https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0
- **Usage:** In the first person view the mod makes the game's own picture of each garment, head of hair and beard finer as the game runs, reading it from the player's installed copy, and multiplies the light and dark of one of the fabrics into it. Nothing of the game's pictures is in the jar. The prints, the blood and the dirt stand in for the game's own where its combiner composes what a person wears.
- **Bundled:** the pictures, yes. The model is not.
---

## Shotgun model

- **Files:** `models/shotgun.mesh.gz`, `models/shotgun.colour.bc` and `models/shotgun.surface.bc` inside the mod's jar
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

## Rifle model

- **Files:** `models/rifle.mesh.gz`, `models/rifle.colour.bc` and `models/rifle.surface.bc` inside the mod's jar
- **License:** MIT, this project's own, for the model and for what `tools/models/rifle.py` paints on it. The walnut of its stock and the grain of its rubber pad are baked from the two pictures the shotgun's are, made with Stable Diffusion XL base 1.0 from text prompts alone, under the same terms as the generated material textures above. No image from Project Zomboid or anywhere else was given to the model.
- **Usage:** The first person view draws it in the viewer's hands in place of the game's model of `Base.HuntingRifle`.
- **Bundled:** yes. The image model is not.

The model is built from nothing by `tools/models/rifle.py` in this repository, a
Blender script, with the tools of `tools/models/shotgun.py`: a bolt action
sporting rifle of no maker's pattern. No mesh or texture is read from Project
Zomboid or from anywhere else. Two measures of the game's own hunting rifle
decide how the model is held, and nothing of its shape: the game's model script
says where that rifle's muzzle is from its grip, which the mod reads while the
game runs and sizes the model to, and where a scope is fixed to it, which the
model's receiver is made to stand under.

---

## Arm model

- **Files:** `models/arms.mesh.gz`, `models/arms.colour.bc` and `models/arms.surface.bc` inside the mod's jar
- **Version:** made by `tools/models/arms.py` in this repository, a Blender script, from the MakeHuman base mesh as the MPFB 2 add-on for Blender, version 2.0.17, carries it in its `data` folder: `3dobjs/base.obj`, the mesh; `rigs/standard/rig.default.json` and `weights.default.json`, its bones and how the mesh hangs on them; three of its macro targets, `caucasian-male-young`, `caucasian-female-young` and `universal-male-young-maxmuscle-averageweight`, which shape the mesh into a man's arm and a woman's; and `textures/mpfb_fingernails.jpg`, which says where on the mesh the nails are.
- **License:** those files are assets of MPFB, which its makers give away under Creative Commons CC0 1.0 Universal: MPFB's `LICENSE.md` puts "the base mesh and proxies", "targets and modifiers", "textures", "rigs, poses and expressions" and "JSON data with mesh information" under CC0, and its source code under the GPL. None of MPFB's source code is used, run or bundled: `tools/models/arms.py` reads the data files itself. What that script makes of them is MIT, this project's own.
- **Upstream:** https://github.com/makehumancommunity/mpfb2 and https://static.makehumancommunity.org/mpfb.html
- **Usage:** The first person view draws these arms on a held weapon in place of the game's own arms.
- **Bundled:** what the script makes, yes. MPFB and its data files are not.

`tools/models/arms.py` cuts the right arm out of the base mesh from the shoulder
down with the bones that move it, smooths it once, and bends its fingers into
each way a hand holds something: round a grip, on a trigger, under a fore-end,
round a handle, on a bolt's knob, over a slide, round a magazine. Those poses
are numbers in the script, made for this mod. The sleeve and the glove are the
arm's own surface stood off the skin. The skin's colour, its grain, the creases
of its knuckles and its nails are worked out by the script and baked onto the
arm: no picture of skin from MPFB, from Project Zomboid or from anywhere else is
in them. No mesh or texture of Project Zomboid's is read. The left arm is the
right one mirrored.

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

## BC7 texture compression

- **Files:** every `*.bc` inside the mod's jar, and `Bc7.java`
- **Encoder:** the pictures are packed into BC7 blocks by Microsoft's `texconv`, of DirectXTex release `may2026` (MIT, Copyright (c) Microsoft Corporation), https://github.com/microsoft/DirectXTex. `tools/textures/compress.py` fetches it when someone remakes a picture. It is a tool: no part of it is in the jar or in this repository, and its licence asks nothing of what it writes.
- **Format:** BC7 is the block format of OpenGL's ARB_texture_compression_bptc, https://registry.khronos.org/OpenGL/extensions/ARB/ARB_texture_compression_bptc.txt. That specification is where the layout of a block and the tables in `Bc7.java` (which texel is in which set, and which texel's index is stored a bit short) come from. The code that unpacks a block, for a graphics card that cannot, is this project's own, MIT.
- **Bundled:** the packed pictures, yes. The encoder is not.

---

## Shader code

The jar's shaders are this project's own, with two exceptions. Both are in the
jar as shader source text.

### Hash without Sine

- **Where:** `chance` and `chanceAt` in the grain shader of `CharacterDraw.java`, which are that work's `hash13` and `hash11`; `chance` and `chance3` in the arm shader of `OwnArms.java`, its `hash12` and `hash13`; and `facHash` in the world shader of `PipelineShaders.java`, its `hash12`
- **License:** MIT
- **Upstream:** https://www.shadertoy.com/view/4djSRW
- **Usage:** The pseudo-random numbers behind the fine grain of skin, hair and cloth, the grain of leather and the shapes of blood and dirt on the arms in first person, the difference in shade from one brick or board of a wall to the next, and the lie of settled snow and the crystals that glint in it.
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
