# Third-Party Notices

ProjectZomboidHeadTracking.jar holds only this project's own classes. The
components below are what it ships beside, links against at run time, or
interoperates with. Each remains the property of its authors and is used under
its own licence.

Nothing in this repository is derived from, or redistributes any part of,
Project Zomboid, which is developed and published by The Indie Stone. The files
under `stubs/` are signature-only stand-ins written for this project: each
lists the class and member names, with the signatures the game and LWJGL
declare, that the agent calls, and has no body beyond a throw. They are on the
compile classpath only and are never packed into the jar.

## cameraunlock-core

- **Version:** commit d43b109c9e5d5f4cebdb05b26e3a8c81a4085d6e
- **License:** MIT
- **Upstream:** https://github.com/itsloopyo/cameraunlock-core
- **Usage:** `install.cmd` and `uninstall.cmd` run the installer script bodies it provides; none of its code is compiled into the jar.
- **Bundled:** yes. The script bodies go in the release ZIP's `shared` folder.

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
- **Usage:** The agent calls its OpenGL, GLFW and Win32 bindings to draw the first person view and place the window.
- **Bundled:** no. The names resolve to the game's own copy at run time.

---
