package com.cameraunlock.zomboid;

import java.util.List;
import java.util.Locale;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoLightSource;

/**
 * The light a lamp hung on the outside of a building throws round it. The game lights whole
 * squares, and a lamp's light falls off slowly over its radius of eight or nine, so a porch
 * light lit the yard as evenly as the moon and nothing on the wall beside it showed that a lamp
 * burned there. In the evening it was not seen at all: the game holds every square at full
 * daylight until late dusk, after the picture's sky is the night's (see Evening), and a lamp has
 * nothing over that to show in. Here each lamp is also a light of the picture's, at the lamp,
 * thinning with the square of the distance, so the wall and the ground just round it are bright
 * and the light falls away from it.
 *
 * Only a lamp on a wall: a street lamp's head is up on its post, and the game's pool under it
 * reads as one. The lamp burns where the game has the square lit over the open ground's light,
 * so it stops at walls as the game's own does, and while the game holds its daylight, by as
 * much as the picture has taken it down, on whatever out of doors faces it, with nothing known
 * of what stands in its way.
 */
final class Lamps {
    /** The most lamps that light a picture, the nearest. */
    static final int LIGHTS = 8;
    /** How far from the eye a lamp is gathered, in tiles. */
    private static final float WITHIN = 40f;
    /** How far off the wall a lamp hangs, in tiles, and how high over the floor, as a share of a level. */
    private static final float OFF_WALL = 0.15f, UP = 0.72f;
    /** How strong a lamp's light is beside it, for one the game lists at full brightness. */
    private static final float STRENGTH = 5f;
    /**
     * The colour of a lamp's light, linear. The game lists a porch lamp as (1, 0.39, 0.31), and
     * its engine mixes that towards white: what it gives the ground under one is (1, 0.58, 0.36).
     */
    private static final float[] WARM = {1f, 0.58f, 0.36f};

    /** The light shaders take from the lamps: LIGHTS of them, each where it is and how strong, and its colour and reach. */
    static final String GLSL = """
            uniform vec4 uLampAt[8];
            uniform vec4 uLampHue[8];

            // What the lamps give a face out of doors at p that faces n, where the game has given its
            // square lit as much of their light as lit says.
            vec3 lampWorldLight(vec3 p, vec3 n, float lit) {
                vec3 sum = vec3(0.0);
                for (int i = 0; i < 8; i++) {
                    vec4 at = uLampAt[i];
                    if (at.w <= 0.0) {
                        break;
                    }
                    vec3 to = at.xyz - p;
                    float far2 = dot(to, to);
                    float reach = uLampHue[i].w;
                    if (far2 >= reach * reach) {
                        continue;
                    }
                    vec3 dir = to * inversesqrt(max(far2, 0.0001));
                    float far = sqrt(far2);
                    sum += uLampHue[i].rgb * (at.w * max(dot(n, dir), 0.0) * (1.0 - smoothstep(reach * 0.55, reach, far)) / (1.0 + 0.6 * far2));
                }
                return sum * lit;
            }
            """;

    /** How many lamps the log last said light the picture: dev only. */
    private static int said = -1;
    /** Where the lamp being gathered hangs: logic thread only. */
    private static final float[] place = new float[2];

    private Lamps() {}

    /**
     * Gathers the burning lamps hung on walls near the eye into sky.lamps, up to LIGHTS of the
     * nearest. Logic thread, which is the one that switches the game's lamps.
     */
    static void gather(SkyState sky, IsoCell cell, Terrain terrain, float eyeX, float eyeY) {
        float[] to = sky.lamps;
        float[] far = sky.lampFar;
        int count = 0;
        List<IsoLightSource> lamps = cell.getLamppostPositions();
        for (int i = 0; i < lamps.size(); i++) {
            IsoLightSource lamp = lamps.get(i);
            if (!lamp.active) {
                continue;
            }
            float dx = lamp.x + 0.5f - eyeX, dy = lamp.y + 0.5f - eyeY;
            float distance = dx * dx + dy * dy;
            if (distance > WITHIN * WITHIN) {
                continue;
            }
            int slot = count;
            if (count == LIGHTS) {
                slot = 0;
                for (int k = 1; k < LIGHTS; k++) {
                    if (far[k] > far[slot]) {
                        slot = k;
                    }
                }
                if (far[slot] <= distance) {
                    continue;
                }
            }
            if (!hung(cell, lamp, place) || listed(to, count, slot)) {
                continue;
            }
            if (count < LIGHTS) {
                count++;
            }
            far[slot] = distance;
            int at = slot * 8;
            to[at] = place[0];
            to[at + 1] = place[1];
            to[at + 2] = lamp.z * ViewCamera.LEVEL_HEIGHT + terrain.lift(to[at], to[at + 1], lamp.z) + UP * ViewCamera.LEVEL_HEIGHT;
            to[at + 3] = STRENGTH * Math.max(lamp.r, Math.max(lamp.g, lamp.b));
            System.arraycopy(WARM, 0, to, at + 4, 3);
            to[at + 7] = lamp.radius;
        }
        if (DevBuild.ON && count != said) {
            said = count;
            StringBuilder line = new StringBuilder("first person lamps on walls lighting the picture: ").append(count);
            for (int i = 0; i < count; i++) {
                line.append(String.format(Locale.ROOT, " (%.2f, %.2f, %.2f)", to[i * 8], to[i * 8 + 1], to[i * 8 + 2]));
            }
            Log.info(line.toString());
        }
        sky.lampCount = count;
    }

    /** Whether a lamp hung where place says is already one of the first count, other than the one in slot: a fixture with two bulbs is two lights of the game's. */
    private static boolean listed(float[] to, int count, int slot) {
        for (int i = 0; i < count; i++) {
            if (i != slot && to[i * 8] == place[0] && to[i * 8 + 1] == place[1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Where on the map a lamp hangs, into to: off a wall on one of its square's edges between
     * a room and out of doors, on the side out of doors. The game stands walls on the north and
     * west edges of squares only, so a lamp on the west face of a house stands on the square west
     * of it and its wall is the one on the west edge of the square east of it. False for a lamp
     * with no such wall, a street lamp.
     */
    private static boolean hung(IsoCell cell, IsoLightSource lamp, float[] to) {
        IsoGridSquare square = cell.getGridSquare(lamp.x, lamp.y, lamp.z);
        if (square == null) {
            return false;
        }
        boolean inRoom = square.getRoomID() != -1L;
        for (int edge = 0; edge < 4; edge++) {
            // West, north, east, south: the way across the edge, and the square whose wall it is.
            int acrossX = edge == 0 ? -1 : edge == 2 ? 1 : 0, acrossY = edge == 1 ? -1 : edge == 3 ? 1 : 0;
            IsoGridSquare beyond = cell.getGridSquare(lamp.x + acrossX, lamp.y + acrossY, lamp.z);
            if (beyond == null || (beyond.getRoomID() != -1L) == inRoom) {
                continue;
            }
            if (WorldScene.wallOn(edge < 2 ? square : beyond, edge % 2 == 1) == null) {
                continue;
            }
            // Off the wall's face out of doors: into the lamp's square, or out of it for a lamp in a room.
            float off = inRoom ? OFF_WALL : -OFF_WALL;
            to[0] = lamp.x + 0.5f + acrossX * (0.5f + off);
            to[1] = lamp.y + 0.5f + acrossY * (0.5f + off);
            return true;
        }
        return false;
    }
}
