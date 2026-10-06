package com.cameraunlock.zomboid;

/**
 * Work the game does over and over for an answer that never changes, done once. Each fix gives
 * the game's code the answer it would have worked out itself, so nothing the game does comes
 * out differently, in its own view or in first person, alone or in multiplayer.
 */
public final class GameFixes {
    private GameFixes() {}

    private static final String COMPONENT = "zombie.characters.ecs.ECSComponent";

    /**
     * The game keeps what a character is made of, its state machine among them, in a map by
     * kind, and the kind of a component is the class it descends from directly under the
     * game's root class of components. It finds that by climbing from the class asked for to
     * the root, on every asking, and a zombie's state machine alone is asked for dozens of
     * times a frame: with 300 zombies on the screen the climb and the map were a ninth of
     * everything the game's logic thread did. A class's ancestors do not change, so the climb
     * is made once a class.
     */
    private static final ClassValue<Class<?>> KIND = new ClassValue<>() {
        @Override
        protected Class<?> computeValue(Class<?> asked) {
            Class<?> kind = null;
            for (Class<?> at = asked; at != null && !at.getName().equals(COMPONENT); at = at.getSuperclass()) {
                kind = at;
            }
            // A ClassValue holds no null: the root itself, whose kind the game has as none, is told by this.
            return kind == null ? Void.class : kind;
        }
    };

    /** What ECSComponent.getECSClass(Class) answers for a class that is not null. Called from the game's code. */
    public static Class<?> componentKind(Class<?> asked) {
        Class<?> kind = KIND.get(asked);
        return kind == Void.class ? null : kind;
    }
}
