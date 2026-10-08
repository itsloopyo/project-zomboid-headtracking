package com.cameraunlock.zomboid;

/**
 * Work the game does over and over for an answer that never changes, done once. Each fix gives
 * the game's code the answer it would have worked out itself, so nothing the game does comes
 * out differently, in its own view or in first person, alone or in multiplayer.
 */
public final class GameFixes {
    private GameFixes() {}

    /**
     * The game keeps what a character is made of, its state machine among them, in a map by
     * kind, and the kind of a component is the class it descends from directly under the
     * game's root class of components. It finds that by climbing from the class asked for to
     * the root, on every asking, and a zombie's state machine alone is asked for dozens of
     * times a frame: with 300 zombies on the screen the climb and the map were a ninth of
     * everything the game's logic thread did. A class's ancestors do not change, so the game
     * makes the climb once a class: what its own code answered for a class is kept here, and
     * handed back the next time that class is asked about. Where it answered nothing, nothing
     * is kept and it works the answer out each time, as it did.
     */
    private static final ClassValue<Class<?>[]> KIND = new ClassValue<>() {
        @Override
        protected Class<?>[] computeValue(Class<?> asked) {
            return new Class<?>[1];
        }
    };

    /** What ECSComponent.getECSClass(Class) answered for this class before, or null where it has not yet. Called from the game's code. */
    public static Class<?> knownKind(Class<?> asked) {
        return asked == null ? null : KIND.get(asked)[0];
    }

    /** Keeps what ECSComponent.getECSClass(Class) is about to answer for a class. Called from the game's code. */
    public static void learnKind(Class<?> asked, Class<?> kind) {
        if (asked != null) {
            KIND.get(asked)[0] = kind;
        }
    }
}
