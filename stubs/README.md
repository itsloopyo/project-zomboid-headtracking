# Reference stubs

Signature-only stand-ins for the game and LWJGL classes the agent calls, so the
build needs no game install. They go on the compile classpath only and are
never packed into the jar: at run time the names resolve to the game's own
classes. A stub lists only the members the agent uses, with the signature the
game declares.

The stubs are this project's own files, under the repository's MIT licence.
None of them is copied from the game or from LWJGL: there is no method body,
constant value or resource in them, only the names and signatures the agent
compiles against. The classes they stand in for all ship inside the game's
jar, `zombie`, `org.lwjglx`, `org.joml` and `se.krka.kahlua` beside LWJGL's
`org.lwjgl`, and remain their owners' property.
