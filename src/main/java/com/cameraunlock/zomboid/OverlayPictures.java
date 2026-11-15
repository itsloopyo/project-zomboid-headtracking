package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import zombie.iso.ContainerOverlays;

/**
 * The pictures ContainerOverlays paints over a container's square, told apart: the one of it holding many things and the
 * one of it holding few. The picture says which, where the count of what the container holds does not: a client of a
 * server has a container's things only once a player there opens it, and the picture is the server's.
 *
 * Read once, on the first ask, from the game's map of the pictures, which it fills as it loads its Lua, before any world
 * is built. Any thread.
 */
final class OverlayPictures {
    private OverlayPictures() {}

    private static final class Read {
        static final Set<String> MANY = new HashSet<>(), FEW = new HashSet<>();

        static {
            try {
                Field map = ContainerOverlays.class.getDeclaredField(Links.CONTAINER_OVERLAY_MAP.name());
                map.setAccessible(true);
                Field entries = null, many = null, few = null;
                for (Object overlay : ((Map<?, ?>) map.get(ContainerOverlays.instance)).values()) {
                    if (entries == null) {
                        entries = overlay.getClass().getDeclaredField(Links.CONTAINER_OVERLAY_ENTRIES.name());
                        entries.setAccessible(true);
                    }
                    for (Object entry : (List<?>) entries.get(overlay)) {
                        if (many == null) {
                            many = entry.getClass().getDeclaredField(Links.CONTAINER_OVERLAY_MANY.name());
                            many.setAccessible(true);
                            few = entry.getClass().getDeclaredField(Links.CONTAINER_OVERLAY_FEW.name());
                            few.setAccessible(true);
                        }
                        // An entry with no picture of few things is painted with its picture of many whatever the count.
                        if (many.get(entry) instanceof String picture) {
                            MANY.add(picture);
                        }
                        if (few.get(entry) instanceof String picture) {
                            FEW.add(picture);
                        }
                    }
                }
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("the game's pictures of what containers hold could not be read", failure);
            }
            Log.info("container pictures: " + MANY.size() + " of many things, " + FEW.size() + " of few");
        }
    }

    /** Whether picture is one the game paints a container holding few things with, and never one holding many. */
    static boolean few(String picture) {
        return Read.FEW.contains(picture) && !Read.MANY.contains(picture);
    }
}
