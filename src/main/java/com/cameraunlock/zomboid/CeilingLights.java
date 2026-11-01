package com.cameraunlock.zomboid;

import java.util.ArrayList;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoRoomLight;

/**
 * The ceiling lights of the rooms round the eye that are on, for their shadows (LampShadows).
 * The game gives a room's light by its switch to every square of one rectangle of the room's
 * plan, an IsoRoomLight each, in the same warm white whatever bulb the switch lists, and the
 * picture has nothing of its own from it: a square's light is the game's. Here each is a light
 * under the ceiling over the middle of its rectangle, which is where a shadow map is drawn from,
 * and the rectangle is the floor it lights.
 */
final class CeilingLights {
    /** The most ceiling lights gathered, the nearest, and the floats of each. */
    static final int MOST = 8, FLOATS = 8;
    /** Where in a record: the least x and y of the rectangle and the most, then the light's place and its strength. */
    static final int RECT = 0, AT = 4, STRENGTH = 7;
    /** How far under its ceiling the light hangs, as a share of a level. */
    static final float UNDER = 0.08f;
    /**
     * How strong a ceiling light counts for, beside a lamp's record strength (Lamps, RoomLamps),
     * when the lamps that throw shadows are picked: about a table lamp's.
     */
    static final float STRENGTH_OF_ONE = 3f;
    /** Over the last part of the light distance a ceiling light comes in as the eye nears it, as Lamps.COMES_IN. */
    private static final float COMES_IN = 0.2f;

    /** How far each gathered light is from the eye, squared. Logic thread only. */
    private static final float[] far = new float[MOST];

    private CeilingLights() {}

    /**
     * Gathers the ceiling lights that are on, on the eye's level, within within tiles of the eye,
     * of rooms the viewer has seen into, up to MOST of the nearest, into sky. Logic thread, which
     * is the one that switches the game's lights.
     */
    static void gather(SkyState sky, IsoCell cell, Terrain terrain, int playerIndex, float eyeX, float eyeY, int eyeLevel, float within) {
        int count = 0;
        float[] to = sky.ceilings;
        ArrayList<IsoRoomLight> lights = within > 0f ? cell.roomLights : null;
        for (int i = 0; lights != null && i < lights.size(); i++) {
            IsoRoomLight light = lights.get(i);
            if (!light.active || light.z != eyeLevel || light.width <= 0 || light.height <= 0) {
                continue;
            }
            float x = light.x + light.width / 2f, y = light.y + light.height / 2f;
            float dx = x - eyeX, dy = y - eyeY;
            float distance = dx * dx + dy * dy;
            if (distance > within * within) {
                continue;
            }
            int slot = Lamps.slotFor(far, count, MOST, distance);
            if (slot < 0) {
                continue;
            }
            IsoGridSquare middle = cell.getGridSquare((int) Math.floor(x), (int) Math.floor(y), light.z);
            if (middle == null || !middle.isSeen(playerIndex) || middle.lighting[playerIndex].darkMulti() <= 0f) {
                continue;
            }
            if (count < MOST) {
                count++;
            }
            far[slot] = distance;
            int at = slot * FLOATS;
            to[at + RECT] = light.x;
            to[at + RECT + 1] = light.y;
            to[at + RECT + 2] = light.x + light.width;
            to[at + RECT + 3] = light.y + light.height;
            to[at + AT] = x;
            to[at + AT + 1] = y;
            to[at + AT + 2] = light.z * ViewCamera.LEVEL_HEIGHT + terrain.lift(x, y, light.z) + (1f - UNDER) * ViewCamera.LEVEL_HEIGHT;
            to[at + STRENGTH] = STRENGTH_OF_ONE * Math.min((1f - (float) Math.sqrt(distance) / within) / COMES_IN, 1f);
        }
        sky.ceilingCount = count;
    }
}
