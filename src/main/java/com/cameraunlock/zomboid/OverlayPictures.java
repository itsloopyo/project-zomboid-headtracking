package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import zombie.iso.ContainerOverlays;
import zombie.iso.IsoObject;

/**
 * Which of ContainerOverlays' pictures the game paints over a container's square: the one of it holding many things or
 * the one of it holding few. The picture says which, where the count of what the container holds does not: a client of
 * a server has a container's things only once a player there opens it, and the picture is the server's.
 *
 * Read from the game's own map of the pictures each time, by the sprite they are painted over, so it is the map of the
 * mods loaded now: the game empties it as a game is left and fills it again as the next loads. Any thread.
 */
final class OverlayPictures {
    private OverlayPictures() {}

    private static final class Fields {
        static final Field MAP = open(ContainerOverlays.class, Links.CONTAINER_OVERLAY_MAP);
        static volatile Field entries, few;
    }

    private static Field open(Class<?> owner, Links.Field field) {
        try {
            Field opened = owner.getDeclaredField(field.name());
            opened.setAccessible(true);
            return opened;
        } catch (NoSuchFieldException failure) {
            throw new IllegalStateException(owner.getName() + " has no " + field.name(), failure);
        }
    }

    /** Whether painted, painted over object's square, is the game's picture of it holding few things. */
    static boolean few(IsoObject object, String painted) {
        String sprite = object.getSprite().getName();
        try {
            Object overlay = ((Map<?, ?>) Fields.MAP.get(ContainerOverlays.instance)).get(sprite);
            if (overlay == null) {
                throw new IllegalStateException(painted + " is painted over " + sprite + ", which the game has no pictures of what it holds for");
            }
            if (Fields.entries == null) {
                Fields.entries = open(overlay.getClass(), Links.CONTAINER_OVERLAY_ENTRIES);
            }
            for (Object entry : (List<?>) Fields.entries.get(overlay)) {
                if (Fields.few == null) {
                    Fields.few = open(entry.getClass(), Links.CONTAINER_OVERLAY_FEW);
                }
                if (painted.equals(Fields.few.get(entry))) {
                    return true;
                }
            }
            return false;
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("the game's pictures of what " + sprite + " holds could not be read", failure);
        }
    }
}
