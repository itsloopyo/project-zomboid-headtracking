package com.cameraunlock.zomboid;

import java.util.ArrayList;
import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteInstance;

/** The game's own flames and smoke as it animates them, read off its objects. Apart from Fires, which the tests load without the game. */
final class FireLooks {
    private FireLooks() {}

    /**
     * The game's own animation of flames ("Fire") or of smoke ("Smoke") an object carries, the
     * first it has: a burning square's and a lit hearth's, an IsoAnim, or null for none.
     */
    static Object look(Object object, String kind) {
        // Taken as any object: a caller that passes a burning square's IsoFire then needs no game class to load.
        ArrayList<IsoSpriteInstance> attached = ((IsoObject) object).getAttachedAnimSprite();
        if (attached == null) {
            return null;
        }
        for (int i = 0; i < attached.size(); i++) {
            IsoSprite sprite = attached.get(i).parentSprite;
            if (sprite != null && Fires.isLook(sprite.getName(), kind)) {
                return sprite.currentAnim;
            }
        }
        return null;
    }

    /** Whether a sprite is one of the game's animations of flames or of smoke, which the picture draws as fire and never as a card. */
    static boolean isLook(IsoSprite sprite) {
        String name = sprite.getName();
        return Fires.isLook(name, "Fire") || Fires.isLook(name, "Smoke");
    }
}
