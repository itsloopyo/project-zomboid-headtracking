# Reference stubs

Signature-only stand-ins for the game and LWJGL classes the agent calls, so the
build needs no game install. They go on the compile classpath only and are
never packed into the jar: at run time the names resolve to the game's own
classes. A stub lists only the members the agent uses, with the signature the
game declares.
