package com.cameraunlock.zomboid;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import zombie.core.properties.IsoPropertyType;
import zombie.core.textures.ColorInfo;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoLightSource;
import zombie.iso.IsoObject;
import zombie.iso.objects.IsoLightSwitch;
import zombie.iso.objects.IsoTelevision;
import zombie.util.list.PZArrayList;

/**
 * The light a lamp in a room, and a television that is on, throws round it. The game lights
 * whole squares, each to the greatest of the lights it lists, and a lamp's light falls off over
 * its radius of six as a straight line, so the room round a table lamp was lit as evenly as by
 * the room's own light, and the walls beside the lamp no brighter than the far corner. A set
 * that is on lists a light of radius 1 to 4 that the game changes every few seconds, lighting
 * the squares round it as flatly as a lamp's.
 *
 * Here each is also a light of the picture's, at the bulb or the glass, thinning with the square
 * of the distance, so the wall, the table and the floor next to it are bright and the light falls
 * away. At night what the game gave the room's squares from it is taken out first, down to the
 * least light of the room's squares the lamp reaches: the dark the room would be in without it.
 * Where that takes out more than the picture's light gives back, as on a face beside a set and
 * turned from its glass, the light given there is raised to what was taken, so a lamp or a set
 * that is on never leaves anything darker than it was.
 * A set's light comes off the front of its glass, of the colour and as bright as what the glass
 * shows in the picture (Screens.glow), and changes as that does.
 *
 * Only the squares of the lamp's own room are lit, so the light stops at the room's walls. The
 * game's line of sight (LosUtil.lineClear) is not asked as well: it left one square of the room
 * two from the lamp at 10817, 9759 unreached, a dark patch among lit ones. The game decides what burns: a lamp is
 * one the game has switched on and powered (its IsoLightSource is active), a set is one showing
 * a picture (Screens.showing).
 */
final class RoomLamps {
    /** The most lamps and sets that light a picture, the nearest. */
    static final int LIGHTS = 8;
    /** How far from its own square a light's squares are known, every way, in squares. */
    static final int REACH = 8;
    /** How many squares across the block of squares each light knows. */
    static final int SIDE = 2 * REACH + 1;
    /** How many vec4 of the shader's uRoomLampRows each light has, a row of its block in each float. */
    static final int ROW_VECTORS = (SIDE + 3) / 4;
    /** How many floats one light is: where it is, its colour, its room's dark, its square and way, and the game's light of it. */
    static final int FLOATS = 20;
    static final int AT = 0, HUE = 4, BASE = 8, WAY = 12, GAME = 16;
    /** What a lamp's way says: it lights all round. */
    static final float ALL_ROUND = 100f;
    /**
     * Over the last part of the light distance a light comes in as the eye nears it, its own
     * light and the game's taken out together, as Lamps.COMES_IN.
     */
    private static final float COMES_IN = 0.2f;
    /** How high a lamp's bulb is over its floor and a set's glass, as a share of a level. */
    private static final float BULB_UP = 0.42f, GLASS_UP = 0.28f;
    /** How far in front of the middle of its square a set's glass is, in tiles. */
    private static final float GLASS_OUT = 0.25f;
    /**
     * How strong a lamp's light is at its bulb, for one the game lists at full brightness, and a
     * set's at its glass for each part of the mean linear light of what the glass shows.
     */
    private static final float LAMP_GIVES = 4f, SET_GIVES = 8f;
    /** How far a set's light reaches, in tiles. */
    private static final float SET_REACH = 6f;
    /**
     * The colour a lamp's bulb is mixed towards, and by how much. The game lists a lamp with a
     * shade of colour in that colour, and a warm white one in white once it has doubled it
     * (LightingJNI): a bulb is warm, and a coloured shade colours only part of what it gives.
     */
    private static final float[] WARM = {1f, 0.86f, 0.62f};
    private static final float WARMED = 0.5f;
    /** How fast a light thins with distance: it is 1 / (1 + FALL d^2) of its strength d tiles off. */
    private static final float FALL = 1.4f;
    /** A light's block is worked out again after this many gatherings, one light a gathering at most. */
    private static final int STALE = 8;

    /** What is known of one light's squares: which it reaches, row by row, and its room's dark. */
    private static final class Reached {
        final int[] rows = new int[SIDE];
        final float[] dark = new float[3];
        int gathered = Integer.MIN_VALUE;
        boolean wanted;
        /** For a set, the mean linear colour its glass last showed, once glowing. */
        final float[] glow = new float[3];
        boolean glowing;
    }

    private final Map<IsoLightSource, Reached> known = new IdentityHashMap<>();
    /** Made on the first gathering: the game's classes are not there for the tests, which build scenes without gathering. */
    private IsoLightSource[] chosen;
    private IsoTelevision[] sets;
    private final float[] far = new float[LIGHTS];
    private final ColorInfo scratch = new ColorInfo();
    private final float[] glowing = new float[3];
    private int gathering;
    /** Which lights the log last said light the picture, as a hash of where they are: dev only. */
    private int said;

    /**
     * Gathers the lamps burning in rooms and the sets showing a picture near the eye into sky,
     * up to LIGHTS of the nearest. Logic thread, which is the one that switches the game's lamps.
     */
    void gather(SkyState sky, IsoCell cell, Terrain terrain, TorchLess torchLess, int playerIndex, float eyeX, float eyeY, int eyeLevel, float within) {
        gathering++;
        if (chosen == null) {
            chosen = new IsoLightSource[LIGHTS];
            sets = new IsoTelevision[LIGHTS];
        }
        int count = 0;
        List<IsoLightSource> lights = within > 0f ? cell.getLamppostPositions() : List.of();
        for (int i = 0; i < lights.size(); i++) {
            IsoLightSource light = lights.get(i);
            if (!light.active || Math.abs(light.z - eyeLevel) > 1) {
                continue;
            }
            float dx = light.x + 0.5f - eyeX, dy = light.y + 0.5f - eyeY;
            float distance = dx * dx + dy * dy;
            if (distance > within * within) {
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
            IsoGridSquare square = cell.getGridSquare(light.x, light.y, light.z);
            // A room the game shows dark, one the viewer has not seen into, shows no lamp either.
            if (square == null || square.getRoomID() == -1L || !square.isSeen(playerIndex) || square.lighting[playerIndex].darkMulti() <= 0f) {
                continue;
            }
            IsoTelevision set = null;
            if (light.switches.isEmpty()) {
                set = showing(square);
                if (set == null) {
                    continue;
                }
                Reached reached = known.computeIfAbsent(light, unknown -> new Reached());
                // Until what its glass shows has been read the set holds the colour it showed last, and lights nothing before it has shown one.
                if (Screens.glow(set, playerIndex, glowing)) {
                    System.arraycopy(glowing, 0, reached.glow, 0, 3);
                    reached.glowing = true;
                }
                if (!reached.glowing) {
                    continue;
                }
            } else if (!lamp(light.switches.get(0))) {
                continue;
            }
            chosen[slot] = light;
            sets[slot] = set;
            far[slot] = distance;
            if (count < LIGHTS) {
                count++;
            }
        }
        for (Reached reached : known.values()) {
            reached.wanted = false;
        }
        float[] to = sky.roomLamps;
        float[] rows = sky.roomLampRows;
        boolean refreshed = false;
        int hash = count;
        for (int slot = 0; slot < count; slot++) {
            IsoLightSource light = chosen[slot];
            Reached reached = known.computeIfAbsent(light, unknown -> new Reached());
            reached.wanted = true;
            if (reached.gathered == Integer.MIN_VALUE || !refreshed && gathering - reached.gathered >= STALE) {
                reach(cell, light, torchLess, playerIndex, reached);
                reached.gathered = gathering;
                refreshed = true;
            }
            place(light, sets[slot], terrain, reached, to, slot * FLOATS);
            float comesIn = Math.min((1f - (float) Math.sqrt(far[slot]) / within) / COMES_IN, 1f);
            to[slot * FLOATS + 3] *= comesIn;
            to[slot * FLOATS + GAME + 2] = comesIn;
            // And one it shows dimmer, out of sight now, shows the lamp's light as much dimmer.
            to[slot * FLOATS + 3] *= Math.min(cell.getGridSquare(light.x, light.y, light.z).lighting[playerIndex].darkMulti(), 1f);
            for (int row = 0; row < ROW_VECTORS * 4; row++) {
                rows[slot * ROW_VECTORS * 4 + row] = row < SIDE ? reached.rows[row] : 0f;
            }
            hash = 31 * (31 * (31 * hash + light.x) + light.y) + light.z;
            if (sets[slot] != null) {
                // A set's colour, to a twentieth, so the log says when what its glass shows changes.
                hash = 31 * hash + Math.round(to[slot * FLOATS + HUE] * 20f) * 441 + Math.round(to[slot * FLOATS + HUE + 1] * 20f) * 21 + Math.round(to[slot * FLOATS + HUE + 2] * 20f);
            }
        }
        for (Iterator<Reached> it = known.values().iterator(); it.hasNext(); ) {
            if (!it.next().wanted) {
                it.remove();
            }
        }
        for (int slot = count; slot < LIGHTS; slot++) {
            chosen[slot] = null;
            sets[slot] = null;
        }
        if (DevBuild.ON && hash != said) {
            said = hash;
            StringBuilder line = new StringBuilder("lamps and sets in rooms lighting the picture: ").append(count);
            for (int i = 0; i < count; i++) {
                int at = i * FLOATS;
                line.append(String.format(Locale.ROOT, " (%s %.2f, %.2f, %.2f strength %.2f hue %.2f %.2f %.2f dark %.3f %.3f %.3f)", to[at + WAY + 3] > 0f ? "set" : "lamp",
                        to[at], to[at + 1], to[at + 2], to[at + 3], to[at + HUE], to[at + HUE + 1], to[at + HUE + 2], to[at + BASE], to[at + BASE + 1], to[at + BASE + 2]));
            }
            Log.info(line.toString());
        }
        sky.roomLampCount = count;
    }

    /** A switch's light is a lamp in a room's: not the room's own light, nor a street lamp's, nor one hung on the outside of a house (see Lamps). */
    static boolean lamp(IsoLightSwitch light) {
        return !light.lightRoom && !light.streetLight && !Lamps.outdoorSprite(light.getSprite() == null ? null : light.getSprite().getName());
    }

    /** The set on a square that shows a picture, or null. A set that is on lists a light of no switch on its own square (IsoTelevision.updateLightSource). */
    private static IsoTelevision showing(IsoGridSquare square) {
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            if (objects.get(i) instanceof IsoTelevision set && Screens.showing(set) != Screens.DARK) {
                return set;
            }
        }
        return null;
    }

    /**
     * Which squares of its room a light reaches, and the least light the picture has for those
     * the viewer has seen: lit by nothing else, that is what the game gives the room in the dark.
     */
    private void reach(IsoCell cell, IsoLightSource light, TorchLess torchLess, int playerIndex, Reached reached) {
        IsoGridSquare home = cell.getGridSquare(light.x, light.y, light.z);
        long room = home == null ? TorchLess.NOT_THERE : home.getRoomID();
        float darkRed = Float.MAX_VALUE, darkGreen = Float.MAX_VALUE, darkBlue = Float.MAX_VALUE;
        for (int row = 0; row < SIDE; row++) {
            int bits = 0;
            for (int column = 0; column < SIDE; column++) {
                int x = light.x + column - REACH, y = light.y + row - REACH;
                IsoGridSquare square = cell.getGridSquare(x, y, light.z);
                if (square == null || square.getRoomID() != room) {
                    continue;
                }
                bits |= 1 << column;
                if (square.isSeen(playerIndex)) {
                    ColorInfo lit = torchLess.light(square, playerIndex, scratch);
                    darkRed = Math.min(darkRed, lit.r);
                    darkGreen = Math.min(darkGreen, lit.g);
                    darkBlue = Math.min(darkBlue, lit.b);
                }
            }
            reached.rows[row] = bits;
        }
        // With none of its squares seen nothing is known of the room's dark, and nothing is taken out.
        reached.dark[0] = darkRed == Float.MAX_VALUE ? 1f : (float) Math.pow(Math.clamp(darkRed, 0f, 1f), 2.2);
        reached.dark[1] = darkGreen == Float.MAX_VALUE ? 1f : (float) Math.pow(Math.clamp(darkGreen, 0f, 1f), 2.2);
        reached.dark[2] = darkBlue == Float.MAX_VALUE ? 1f : (float) Math.pow(Math.clamp(darkBlue, 0f, 1f), 2.2);
    }

    /** One light's record for the shader, at to[at]: see GLSL. */
    private static void place(IsoLightSource light, IsoTelevision set, Terrain terrain, Reached reached, float[] to, int at) {
        float x = light.x + 0.5f, y = light.y + 0.5f;
        float floor = light.z * ViewCamera.LEVEL_HEIGHT + terrain.lift(x, y, light.z);
        to[at + BASE] = reached.dark[0];
        to[at + BASE + 1] = reached.dark[1];
        to[at + BASE + 2] = reached.dark[2];
        to[at + BASE + 3] = floor;
        to[at + WAY] = light.x;
        to[at + WAY + 1] = light.y;
        to[at + GAME] = Math.min(2f * Math.max(light.r, Math.max(light.g, light.b)), 1f);
        to[at + GAME + 1] = Math.max(light.radius, 1);
        to[at + GAME + 3] = 0f;
        if (set == null) {
            to[at] = x;
            to[at + 1] = y;
            to[at + 2] = floor + BULB_UP * ViewCamera.LEVEL_HEIGHT;
            to[at + 3] = LAMP_GIVES * hue(light.r, light.g, light.b, to, at + HUE);
            to[at + HUE + 3] = Math.min(light.radius, REACH);
            to[at + WAY + 2] = ALL_ROUND;
            to[at + WAY + 3] = 0f;
            return;
        }
        float facing = facing(set);
        boolean faces = facing != ALL_ROUND;
        to[at] = x + (faces ? (float) Math.cos(facing) * GLASS_OUT : 0f);
        to[at + 1] = y + (faces ? (float) Math.sin(facing) * GLASS_OUT : 0f);
        to[at + 2] = floor + GLASS_UP * ViewCamera.LEVEL_HEIGHT;
        to[at + 3] = SET_GIVES * screenHue(reached.glow, to, at + HUE);
        to[at + HUE + 3] = SET_REACH;
        to[at + WAY + 2] = facing;
        to[at + WAY + 3] = 1f;
    }

    /** The colour of the light of a glass showing a picture of the mean linear colour glow, into to[at], its greatest part 1, and how bright it is. */
    static float screenHue(float[] glow, float[] to, int at) {
        float most = Math.max(glow[0], Math.max(glow[1], glow[2]));
        for (int k = 0; k < 3; k++) {
            to[at + k] = most > 0f ? glow[k] / most : 1f;
        }
        return most;
    }

    /**
     * The colour of a lamp the game lists in (r, g, b), into to[at], its greatest part 1, and how
     * bright it is: the game's colour doubled and stopped at 1 as the engine has it, then warmed.
     */
    static float hue(float r, float g, float b, float[] to, int at) {
        float red = Math.min(2f * r, 1f), green = Math.min(2f * g, 1f), blue = Math.min(2f * b, 1f);
        float most = Math.max(Math.max(red, Math.max(green, blue)), 1e-4f);
        to[at] = (1f - WARMED) * red / most + WARMED * WARM[0];
        to[at + 1] = (1f - WARMED) * green / most + WARMED * WARM[1];
        to[at + 2] = (1f - WARMED) * blue / most + WARMED * WARM[2];
        float mixed = Math.max(to[at], Math.max(to[at + 1], to[at + 2]));
        for (int k = 0; k < 3; k++) {
            to[at + k] /= mixed;
        }
        return most;
    }

    /** Which way a set's glass faces, as an angle on the map (x east, y south), or ALL_ROUND where its sprite does not say. */
    static float facing(IsoTelevision set) {
        String way = set.getSprite() == null ? null : set.getSprite().getProperties().get(IsoPropertyType.FACING);
        return facingOf(way);
    }

    static float facingOf(String way) {
        if (way == null) {
            return ALL_ROUND;
        }
        return switch (way) {
            case "E" -> 0f;
            case "S" -> (float) (Math.PI / 2);
            case "W" -> (float) Math.PI;
            case "N" -> (float) (-Math.PI / 2);
            default -> ALL_ROUND;
        };
    }

    /**
     * The light shaders take from lamps in rooms and sets: LIGHTS of them. uRoomLampAt is the
     * bulb or the glass and how strong it is there, 0 past the last; uRoomLampHue its colour and
     * reach; uRoomLampBase the least light of its room's squares it reaches, linear, and the
     * height of its level's floor; uRoomLampWay its square, the way its light leaves (ALL_ROUND
     * for a lamp) and 1 for a set; uRoomLampGame
     * how bright the game lights squares with it, its colour's greatest part doubled and stopped
     * at 1, its radius, and how far it has come in (COMES_IN).
     * uRoomLampRows has ROW_VECTORS vec4 a light, one row of the squares round its own in each
     * float, bit k for the square k - REACH east of it.
     */
    static final String GLSL = String.format(Locale.ROOT, """
            uniform vec4 uRoomLampAt[%1$d];
            uniform vec4 uRoomLampHue[%1$d];
            uniform vec4 uRoomLampBase[%1$d];
            uniform vec4 uRoomLampWay[%1$d];
            uniform vec4 uRoomLampGame[%1$d];
            uniform vec4 uRoomLampRows[%2$d];

            // Whether light i reaches what is at p, seen from the side face is turned to: the square
            // just off the face is the one whose light it is, so a wall is lit on its room's side only.
            float roomLampReaches(int i, vec3 p, vec3 face) {
                vec3 q = p + face * 0.05;
                vec4 base = uRoomLampBase[i];
                if (q.z < base.w - 0.05 || q.z >= base.w + %3$f) {
                    return 0.0;
                }
                vec2 cell = floor(q.xy) - uRoomLampWay[i].xy + %4$d.0;
                if (cell.x < 0.0 || cell.y < 0.0 || cell.x > %5$d.0 || cell.y > %5$d.0) {
                    return 0.0;
                }
                int row = int(cell.y);
                int four = row / 4;
                float bits = dot(uRoomLampRows[i * %6$d + four], vec4(equal(vec4(float(row - 4 * four)), vec4(0.0, 1.0, 2.0, 3.0))));
                // Half a unit over the row: GLSL does not promise an exact exp2 or division, and a quotient a hair under an odd whole number floors to the even one below.
                return mod(floor((bits + 0.5) / exp2(cell.x)), 2.0);
            }

            // What the lamps in rooms and the sets that are on give a face at p, with relief n, turned
            // to the eye as face. given, the game's light for it, loses what the lamps gave it there at
            // night, down to their room's dark: as much of it as the game's light of the lamp there,
            // falling off straight over its radius, makes of it, so light from elsewhere stays.
            vec3 roomLampsLight(vec3 p, vec3 n, vec3 face, inout vec3 given) {
                vec3 sum = vec3(0.0);
                vec3 least = given;
                float taken = 0.0;
                float most = max(max(given.r, max(given.g, given.b)), 0.0001);
                for (int i = 0; i < %1$d; i++) {
                    vec4 at = uRoomLampAt[i];
                    if (at.w <= 0.0) {
                        break;
                    }
                    if (roomLampReaches(i, p, face) < 0.5) {
                        continue;
                    }
                    vec4 hue = uRoomLampHue[i];
                    vec4 way = uRoomLampWay[i];
                    vec4 game = uRoomLampGame[i];
                    float gave = pow(game.x * clamp(1.0 - length(p.xy - way.xy - 0.5) / game.y, 0.0, 1.0), 2.2);
                    least = min(least, uRoomLampBase[i].rgb);
                    taken = max(taken, game.z * min(gave / most, 1.0));
                    vec3 to = at.xyz - p;
                    float far2 = dot(to, to);
                    float far = sqrt(far2);
                    if (far >= hue.w) {
                        continue;
                    }
                    vec3 dir = to / max(far, 0.001);
                    // Some of it comes back off the room's other walls, onto what is turned away too.
                    float turned = 0.12 + 0.88 * max(dot(n, dir), 0.0);
                    float strength = at.w * turned * (1.0 - smoothstep(hue.w * 0.6, hue.w, far)) / (1.0 + %7$f * far2);
                    if (way.z < 50.0) {
                        // A set's light leaves the front of its glass.
                        vec2 front = vec2(cos(way.z), sin(way.z));
                        strength *= 0.12 + 0.88 * smoothstep(-0.3, 0.5, dot(front, -dir.xy) / max(length(dir.xy), 0.001));
                    }
                    sum += hue.rgb * strength;
                }
                vec3 takenOut = given - mix(given, least, taken * uDark);
                given -= takenOut;
                vec3 lit = sum * mix(0.25, 1.0, uDark);
                float wanted = dot(takenOut, vec3(0.2126, 0.7152, 0.0722));
                float had = dot(lit, vec3(0.2126, 0.7152, 0.0722));
                if (had < wanted) {
                    lit = had > 0.00001 ? lit * (wanted / had) : takenOut;
                }
                return lit;
            }
            """, LIGHTS, LIGHTS * ROW_VECTORS, ViewCamera.LEVEL_HEIGHT, REACH, SIDE - 1, ROW_VECTORS, FALL);
}
