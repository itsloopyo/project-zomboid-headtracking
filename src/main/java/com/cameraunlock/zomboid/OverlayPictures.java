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

    /** What a container's square is painted holding. */
    enum Holding { MANY, FEW }

    private static final class Fields {
        static final Field MAP = open(ContainerOverlays.class, Links.CONTAINER_OVERLAY_MAP);
        static volatile Field entries, many, few;
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

    /**
     * What the picture painted over object's square shows it holding, or null where nothing is painted over it or what is
     * painted is none of the game's pictures of what containers hold: the game, a mod or an old save lays other things
     * over a square too, a burnt wall's soot, a painted sign, a crafted station's look.
     */
    static Holding holding(IsoObject object) {
        if (object.getOverlaySprite() == null) {
            return null;
        }
        String painted = object.getOverlaySprite().getName();
        if (ContainerOverlays.instance.getUnderlyingSpriteNames(painted) == null) {
            return null;
        }
        String sprite = object.getSprite().getName();
        try {
            Object overlay = ((Map<?, ?>) Fields.MAP.get(ContainerOverlays.instance)).get(sprite);
            if (overlay != null) {
                if (Fields.entries == null) {
                    Fields.entries = open(overlay.getClass(), Links.CONTAINER_OVERLAY_ENTRIES);
                }
                for (Object entry : (List<?>) Fields.entries.get(overlay)) {
                    if (Fields.few == null) {
                        Fields.many = open(entry.getClass(), Links.CONTAINER_OVERLAY_MANY);
                        Fields.few = open(entry.getClass(), Links.CONTAINER_OVERLAY_FEW);
                    }
                    if (painted.equals(Fields.few.get(entry))) {
                        return Holding.FEW;
                    }
                    if (painted.equals(Fields.many.get(entry))) {
                        return Holding.MANY;
                    }
                }
            }
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("the game's pictures of what " + sprite + " holds could not be read", failure);
        }
        throw new IllegalStateException(painted + ", a picture of what a container holds, is painted over " + sprite
                + " and is neither of the game's pictures of what " + sprite + " holds");
    }
}
