package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Locale;
import zombie.characters.IsoPlayer;
import zombie.inventory.InventoryItem;
import zombie.iso.IsoWorld;

/**
 * The light the character carries, as the picture needs it: where it is held, the way it
 * shines and how far it reaches. The game lights whole squares with it, by where they lie on
 * the ground and nothing else, and a square lit like that is lit from top to bottom from no
 * side: a wood in a torch's cone looks as it does at noon. A torch that throws a beam is
 * thrown by the picture itself, from here, past whatever stands in it, and the game's light
 * for the squares is read without it (TorchLess). Of a light that throws none, a lantern,
 * the picture takes what a square has over the open ground's own light as having come from
 * here.
 *
 * The game's logic thread gathers it before it builds a picture's scene, and the scene keeps
 * that gathering whole for the render thread.
 */
final class CarriedLight {
    final View view;

    CarriedLight(View view) {
        this.view = view;
    }


    /** Where it is held (x, y, up), the way it shines (x, y, up), how far it reaches in tiles, 0 when none is lit, and 1 if it shines as a cone. */
    static final int HELD = 0, WAY = 3, REACH = 6, CONE = 7;
    /** A torch is held at the chest, this far under the eye. */
    private static final float BELOW_EYE = 0.25f;

    volatile float[] light = new float[8];
    private final ArrayList<InventoryItem> items = new ArrayList<>();

    /**
     * The eye is the character's own, and the way is a unit vector: the way the game's beam is turned on the ground, and
     * as far up or down as the view with the head's share for a light (HeadFollowLight.up).
     *
     * @param beam whether a torch throws its beam from here: in first person. In the game's own view a torch lights the
     *     squares as the game lights them, as it does in Low.
     */
    void gather(IsoPlayer player, float eyeX, float eyeY, float eyeUp, float wayX, float wayY, float wayUp, boolean beam) {
        player.getActiveLightItems(items);
        float[] found = new float[8];
        for (int i = 0; i < items.size(); i++) {
            InventoryItem item = items.get(i);
            if ((beam || !item.isTorchCone()) && item.getLightDistance() > found[REACH]) {
                found[REACH] = item.getLightDistance();
                found[CONE] = item.isTorchCone() ? 1f : 0f;
            }
        }
        // The items are the game's: none is kept past the asking.
        items.clear();
        found[HELD] = eyeX;
        found[HELD + 1] = eyeY;
        found[HELD + 2] = eyeUp - BELOW_EYE;
        found[WAY] = wayX;
        found[WAY + 1] = wayY;
        found[WAY + 2] = wayUp;
        light = found;
        view.torchLess.gather(IsoWorld.instance.currentCell, player, found[CONE] > 0f ? found[REACH] : 0f, beam);
    }

    String describe() {
        float[] now = light;
        return String.format(Locale.ROOT, "carried light reach %.0f cone %.0f held (%.2f, %.2f, %.2f) way (%.2f, %.2f, %.2f)",
                now[REACH], now[CONE], now[HELD], now[HELD + 1], now[HELD + 2], now[WAY], now[WAY + 1], now[WAY + 2]);
    }
}
