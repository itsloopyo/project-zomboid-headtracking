package com.cameraunlock.zomboid;

import java.util.List;
import java.util.Locale;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoLightSource;
import zombie.iso.IsoObject;
import zombie.iso.objects.IsoLightSwitch;
import zombie.util.list.PZArrayList;

/**
 * The light a lamp hung on the outside of a building, or up on a street lamp's post, throws round
 * it. The game lights whole
 * squares, and a lamp's light falls off slowly over its radius of eight or nine, so a porch
 * light lit the yard as evenly as the moon and nothing on the wall beside it showed that a lamp
 * burned there. In the evening it was not seen at all: the game holds every square at full
 * daylight until late dusk, after the picture's sky is the night's (see Evening), and a lamp has
 * nothing over that to show in. Here each lamp is also a light of the picture's, at the lamp,
 * thinning with the square of the distance, so the wall and the ground just round it are bright
 * and the light falls away from it.
 *
 * A street lamp's light has a radius of 60, so the game lights the street under one as evenly
 * as a porch light lights a yard, and the snow at its foot was no brighter than the road ten
 * squares off. Its light is put where the picture draws the lamp's lens (ShotLights.lightsOf).
 * The game's own light from it, even over the whole of that radius, is taken off open ground
 * (streetLampsCover), or the pool stands on a wash as bright as itself.
 * A lamp burns where the game has the square lit over the open ground's light,
 * so it stops at walls as the game's own does, and while the game holds its daylight, by as
 * much as the picture has taken it down, on whatever out of doors faces it, with nothing known
 * of what stands in its way.
 */
final class Lamps {
    /** The most lamps that light a picture, the nearest, and how many numbers each is told in. */
    static final int LIGHTS = 16, RECORD = 9;
    /**
     * Over the last part of the light distance a lamp comes in as the eye nears it, its own light
     * and the game's taken off together, so that walking towards a street lamp its pool does not
     * appear at once.
     */
    private static final float COMES_IN = 0.2f;
    /** How far off the wall a lamp hangs, in tiles, and how high over the floor, as a share of a level. */
    private static final float OFF_WALL = 0.15f, UP = 0.72f;
    /** How strong a lamp's light is beside it, for one the game lists at full brightness. */
    private static final float STRENGTH = 5f;
    /** The same for a street lamp's, whose lens is two levels or more over the ground it lights. */
    private static final float STREET_STRENGTH = 40f;
    /**
     * The colour of a lamp's light, linear. The game lists a porch lamp as (1, 0.39, 0.31), and
     * its engine mixes that towards white: what it gives the ground under one is (1, 0.58, 0.36).
     */
    private static final float[] WARM = {1f, 0.58f, 0.36f};

    /**
     * The light shaders take from the lamps: LIGHTS of them, each where it is and how strong, and
     * its colour and reach, the reach negative for a street lamp's lens, which looks down: what is
     * under it takes all of its light, what is 40 degrees off a third, and what is level with it a
     * twentieth: a lens five tiles up lit by distance alone gave the ground at the foot of its
     * post one and a half times what it gave six squares off, and no pool. Then how much of the
     * game's own light from a street lamp is taken off, by how far in it is (see COMES_IN).
     */
    static final String GLSL = """
            uniform vec4 uLampAt[16];
            uniform vec4 uLampHue[16];
            uniform float uLampCover[16];
            // How many of them are listed: the rest are dark.
            uniform float uLampCount;

            // What the lamps give a face out of doors at p that faces n, where the game has given its
            // square lit as much of their light as lit says.
            vec3 lampWorldLight(vec3 p, vec3 n, float lit) {
                // By day the game gives no square any, while the lamps are still listed and burn.
                if (lit <= 0.0) {
                    return vec3(0.0);
                }
                vec3 sum = vec3(0.0);
                for (int i = 0; i < 16; i++) {
                    if (float(i) >= uLampCount) {
                        break;
                    }
                    vec4 at = uLampAt[i];
                    if (at.w <= 0.0) {
                        continue;
                    }
                    vec3 to = at.xyz - p;
                    float far2 = dot(to, to);
                    float reach = abs(uLampHue[i].w);
                    if (far2 >= reach * reach) {
                        continue;
                    }
                    vec3 dir = to * inversesqrt(max(far2, 0.0001));
                    float far = sqrt(far2);
                    float down = max(dir.z, 0.0) * max(dir.z, 0.0);
                    float lens = uLampHue[i].w < 0.0 ? 0.05 + 0.95 * down * down : 1.0;
                    sum += uLampHue[i].rgb * (at.w * lens * max(dot(n, dir), 0.0) * (1.0 - smoothstep(reach * 0.55, reach, far)) / (1.0 + 0.6 * far2));
                }
                return sum * lit;
            }

            // How much of the game's lamp light out of doors at p the street lamps above light themselves.
            float streetLampsCover(vec3 p) {
                float cover = 0.0;
                for (int i = 0; i < 16; i++) {
                    if (float(i) >= uLampCount) {
                        break;
                    }
                    float reach = -uLampHue[i].w;
                    if (uLampCover[i] > 0.0 && reach > 0.0) {
                        cover = max(cover, uLampCover[i] * (1.0 - smoothstep(reach * 0.55, reach, length(uLampAt[i].xy - p.xy))));
                    }
                }
                return cover;
            }
            """;

    /** Which lamps the log last said light the picture, as a hash of where they are, whatever order they were gathered in: dev only. */
    private static int said = -1;
    /** Where the lamp being gathered hangs: logic thread only. */
    private static final float[] place = new float[3];

    private Lamps() {}

    /**
     * Gathers the burning lamps hung on walls and street lamps within within tiles of the eye into
     * sky.lamps, up to LIGHTS of the nearest. Logic thread, which is the one that switches the
     * game's lamps.
     */
    static void gather(SkyState sky, IsoCell cell, Terrain terrain, float eyeX, float eyeY, float within) {
        float[] to = sky.lamps;
        float[] far = sky.lampFar;
        int count = 0;
        if (within <= 0f) {
            sky.lampCount = 0;
            return;
        }
        List<IsoLightSource> lamps = cell.getLamppostPositions();
        for (int i = 0; i < lamps.size(); i++) {
            IsoLightSource lamp = lamps.get(i);
            if (!lamp.active) {
                continue;
            }
            float dx = lamp.x + 0.5f - eyeX, dy = lamp.y + 0.5f - eyeY;
            float distance = dx * dx + dy * dy;
            if (distance > within * within) {
                continue;
            }
            int slot = slotFor(far, count, LIGHTS, distance);
            if (slot < 0) {
                continue;
            }
            float up;
            float strength = STRENGTH;
            float reach = lamp.radius;
            if (hungOutside(lamp) && hung(cell, lamp, place)) {
                up = lamp.z * ViewCamera.LEVEL_HEIGHT + terrain.lift(place[0], place[1], lamp.z) + UP * ViewCamera.LEVEL_HEIGHT;
            } else if (onPost(cell, lamp, place)) {
                up = lamp.z * ViewCamera.LEVEL_HEIGHT + terrain.height(lamp.x + 0.5f, lamp.y + 0.5f) + place[2];
                strength = STREET_STRENGTH;
                reach = -reach;
            } else {
                continue;
            }
            if (listed(to, count, slot)) {
                continue;
            }
            if (count < LIGHTS) {
                count++;
            }
            far[slot] = distance;
            int at = slot * RECORD;
            to[at] = place[0];
            to[at + 1] = place[1];
            to[at + 2] = up;
            float comesIn = Math.min((1f - (float) Math.sqrt(distance) / within) / COMES_IN, 1f);
            to[at + 3] = comesIn * strength * Math.max(lamp.r, Math.max(lamp.g, lamp.b));
            System.arraycopy(WARM, 0, to, at + 4, 3);
            to[at + 7] = reach;
            to[at + 8] = reach < 0f ? comesIn : 0f;
        }
        int lit = Flora.mix(count, 0, 0);
        for (int i = 0; FirstPerson.TRACE && i < count * RECORD; i += RECORD) {
            lit += Flora.mix(Float.floatToIntBits(to[i]), Float.floatToIntBits(to[i + 1]), 0);
        }
        if (FirstPerson.TRACE && lit != said) {
            said = lit;
            StringBuilder line = new StringBuilder("first person lamps on walls and posts lighting the picture: ").append(count);
            for (int i = 0; i < count; i++) {
                line.append(String.format(Locale.ROOT, " (%.2f, %.2f, %.2f)", to[i * RECORD], to[i * RECORD + 1], to[i * RECORD + 2]));
            }
            Log.info(line.toString());
        }
        sky.lampCount = count;
    }

    /**
     * The slot of most lights a light at distance (squared) from the eye goes in: the next free one while
     * fewer than most are held, else the farthest held one if this is nearer, else -1.
     */
    static int slotFor(float[] far, int count, int most, float distance) {
        if (count < most) {
            return count;
        }
        int slot = 0;
        for (int k = 1; k < most; k++) {
            if (far[k] > far[slot]) {
                slot = k;
            }
        }
        return far[slot] <= distance ? -1 : slot;
    }

    /** Whether a lamp hung where place says is already one of the first count, other than the one in slot: a fixture with two bulbs is two lights of the game's. */
    private static boolean listed(float[] to, int count, int slot) {
        for (int i = 0; i < count; i++) {
            if (i != slot && to[i * RECORD] == place[0] && to[i * RECORD + 1] == place[1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Where the lens of a street lamp standing on the lamp's square is, into to: east and south on
     * the map, and how high over the floor of its level. A lamp with two lenses is lit from between
     * them. False where no street lamp the picture models stands there.
     */
    private static boolean onPost(IsoCell cell, IsoLightSource lamp, float[] to) {
        IsoGridSquare square = cell.getGridSquare(lamp.x, lamp.y, lamp.z);
        if (square == null) {
            return false;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            if (!(objects.get(i) instanceof IsoLightSwitch post)) {
                continue;
            }
            float[] lights = ShotLights.lightsOf(ShotLights.streetLampOf(post));
            if (lights == null) {
                continue;
            }
            int lenses = lights.length / 3;
            to[0] = lamp.x + 0.5f;
            to[1] = lamp.y + 0.5f;
            to[2] = 0f;
            for (int k = 0; k < lights.length; k += 3) {
                to[0] += lights[k] / lenses;
                to[1] += lights[k + 1] / lenses;
                to[2] += lights[k + 2] / lenses;
            }
            return true;
        }
        return false;
    }

    /**
     * Whether a light is one of the game's lamps for the outside of a building, by its switch's
     * sprite. A lamp of an indoor sheet on a square beside an outside wall, and a set, are lights
     * of a room's (RoomLamps).
     */
    private static boolean hungOutside(IsoLightSource lamp) {
        if (lamp.switches.isEmpty()) {
            return false;
        }
        IsoLightSwitch light = lamp.switches.get(0);
        return outdoorSprite(light.getSprite() == null ? null : light.getSprite().getName());
    }

    /**
     * The name of the sprite the game lays over a lamp's sprite while the lamp burns: its tileset's
     * name, _on_ and its place on the sheet (IsoObject.getOnOverlay), lighting_outdoor_01_on_42 for
     * lighting_outdoor_01_42. Null for a name with no place on a sheet.
     */
    static String litTwin(String sprite) {
        int under = sprite == null ? -1 : sprite.lastIndexOf('_');
        if (under <= 0 || under == sprite.length() - 1 || !sprite.substring(under + 1).chars().allMatch(Character::isDigit)) {
            return null;
        }
        return sprite.substring(0, under) + "_on_" + sprite.substring(under + 1);
    }

    /** Whether a lamp's sprite is of the game's sheets of lamps for the outside of a building. */
    static boolean outdoorSprite(String sprite) {
        return sprite != null && sprite.startsWith("lighting_outdoor");
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
