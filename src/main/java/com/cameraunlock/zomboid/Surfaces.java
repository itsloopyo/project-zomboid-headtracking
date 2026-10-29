package com.cameraunlock.zomboid;

import zombie.core.properties.PropertyContainer;

/**
 * The surface a sprite's properties give, asked of the game one build at a time. The game works
 * a container's surface out the first time it is asked (PropertyContainer.initSurface), and marks
 * it worked out before it has: a build thread asking while another works it out would read no
 * surface. Any thread.
 */
final class Surfaces {
    private static final Object ASKING = new Object();

    private Surfaces() {}

    /** PropertyContainer.getSurface. */
    static int surface(PropertyContainer properties) {
        synchronized (ASKING) {
            return properties.getSurface();
        }
    }

    /** PropertyContainer.isTable. */
    static boolean table(PropertyContainer properties) {
        synchronized (ASKING) {
            return properties.isTable();
        }
    }

    /** PropertyContainer.getItemHeight. */
    static int itemHeight(PropertyContainer properties) {
        synchronized (ASKING) {
            return properties.getItemHeight();
        }
    }
}
