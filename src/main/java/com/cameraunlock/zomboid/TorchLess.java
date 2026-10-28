package com.cameraunlock.zomboid;

import zombie.characters.IsoPlayer;
import zombie.core.textures.ColorInfo;
import zombie.iso.IsoCell;
import zombie.iso.IsoChunkMap;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoLightSource;
import zombie.iso.LightingJNI;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehiclePart;

/**
 * A square's light without the viewer's own torch in it, nor any vehicle's lamps, nor the lamp
 * the game lights for a shot of the viewer's.
 *
 * The game lights whole squares with a torch: every square of the level the character stands
 * on that lies in a wide fan before them, the same from the floor to the ceiling and from no
 * side, and nothing on the floor above until the character is on it (read on a staircase:
 * no square of the level above lists the torch until the character's height reaches it). The picture throws the
 * torch's beam itself (PipelineShaders.torchAt), so the game's share has to come out of each
 * square's light first, or a room is lit twice and the first of the two is a pattern of
 * squares.
 *
 * A vehicle's lamps are torches to the engine too (LightingJNI.checkTorch), head lamps and rear
 * lamps alike, each reaching 36 tiles, and they light squares the same way: a fan of squares
 * that steps as the vehicle turns and as the viewer's sight moves over it, since a torch is only
 * added to a square the viewer can see. The picture throws the head lamps' beams itself
 * (Headlights), so their share comes out the same way.
 *
 * The lighting engine (Lighting64) builds a square's light from what it lists for the square.
 * Each of red, green and blue is the greatest of the square's daylight (IsoGridSquare.lightLevel)
 * and what each lamp or room light listed for it gives: the light's colour, as a byte, times
 * 1 less the length of the way from the light to the square over its radius, cut to a whole
 * byte. The way is walked square to square, 1 a step, the square root of 2 across a corner and
 * 3 a level, round walls, and coloured by a window it passes. The torches the square can be seen
 * by are added to that, the greatest of them part by part, the sum is stopped at 255, and it is
 * dimmed by the square's darkMulti. For a torch the colour listed is what it adds there. Read
 * off every square round street lamps and a building's lit rooms, at several hours of the
 * night, with the torch on and off, the lights listed gave the light without the torch to the
 * byte wherever the way runs straight.
 *
 * So where the sum is under 1 the light without the torch is known exactly. Where it was
 * stopped at 1, which is most of the fan near the character, the light is worked out the
 * engine's way from the lights the square lists, each made no brighter than the parts of the
 * square that are known allow, or with none known, than the squares beside it allow: that is
 * what a wall or a window in its way does. Taken from the
 * squares round it instead, red from those the torch leaves red on and green from nearer ones,
 * the ground between a street lamp and a white shop light was squares of red, pink and grey.
 *
 * The game lights a shot too (EffectsManager.startMuzzleFlash), with a lamp of reach 18 at the
 * shooter's square. Its light reaches the squares a tenth of a second after the shot and stays a
 * quarter of a second, so a shot was the picture's flash (MuzzleFlash) and then a second, longer
 * light. While that flash shows, the lamp is taken out too: a square it is on is worked out from
 * its other lights, as one the torch has stopped at 1.
 *
 * The engine keeps its torch. What the character can see by it, how well zombies see the
 * character and whether a page can be read are worked out from the same squares, and none of
 * that changes.
 */
final class TorchLess {

    /** The squares are read this far past the torch's reach. */
    private static final int MARGIN = 2;
    /** The engine hands light over in 255 steps: at the last of them a sum may have been stopped. */
    private static final float STOPPED = 1f - 0.5f / 255f;
    /**
     * A player's torches are numbered from four times a number of the player's plus 1, four at
     * most (LightingJNI.checkPlayerTorches): the player's index alone, and on a server their
     * online number plus 1, the one way the game numbers every player's there.
     */
    private static final int TORCHES_A_PLAYER = 4;
    /**
     * A vehicle's lamps are numbered 4096 plus ten times the vehicle's number plus the lamp's
     * place among its lights (LightingJNI.update). Players' torches are all numbered under it.
     */
    static final int FIRST_VEHICLE_TORCH = 4096;
    /** A square of no room and one that is not there or has not been seen. Rooms are numbered from 0, and -1 is out of doors. */
    static final long NOT_THERE = Long.MIN_VALUE;
    /** The engine lists at most 6 lights a square, the size of the list it keeps each square's in (LightingJNI). */
    private static final int MOST_LIGHTS = 6;
    private static final float CORNER = (float) Math.sqrt(2.0);
    /** What a level up or down adds to the way a light goes. */
    private static final float LEVEL_STEP = 3f;

    /**
     * What one gathering found, never written again once it is handed over: a block of squares
     * of the character's level, the light of each without the torch, which is the game's own
     * light for a square the torch is not on, and which of them the torch or the lamp of a shot is on.
     */
    private static final class Taken {
        final int fromX, fromY, level, spanX, spanY, counter, lampId;
        final float[] light;
        final boolean[] lit;
        /** Whether the square was there to be read and had been seen. */
        final boolean[] read;
        /** Whether the engine still had any torch that is taken out on a square. */
        boolean torched;

        Taken(int fromX, int fromY, int level, int spanX, int spanY, int counter, int lampId) {
            this.lampId = lampId;
            this.fromX = fromX;
            this.fromY = fromY;
            this.level = level;
            this.spanX = spanX;
            this.spanY = spanY;
            this.counter = counter;
            light = new float[spanX * spanY * 3];
            lit = new boolean[spanX * spanY];
            read = new boolean[spanX * spanY];
        }
    }

    private volatile Taken taken;
    /** The block of squares to read, west, north, east and south, inclusive. Logic thread, only while one reading is made. */
    private int west, north, east, south;
    /** The lamp the game lit for the viewer's last shot, while the game may still have it. Logic thread. */
    private IsoLightSource lamp;
    /** The number of the view whose picture this is for. */
    private final int view;

    TorchLess(int view) {
        this.view = view;
    }

    /**
     * What one reading works with for each square of its block, kept between readings and grown to
     * the widest block met. Logic thread. A reading writes every part of them that it reads.
     */
    private boolean[] known = new boolean[0];
    private float[] least = new float[0], day = new float[0], others = new float[0], dark = new float[0];
    private long[] room = new long[0];
    /** The lamps and room lights of each square: how many, their numbers, x, y, level and radius, colours, and how much of each the square's known parts allow. */
    private int[] lampCount = new int[0], lampIds = new int[0], lampPlaces = new int[0];
    private float[] lampColours = new float[0], lampAllowed = new float[0];
    /** How much of each of one square's lights is let through, as the square works it out. */
    private final float[] allowed = new float[MOST_LIGHTS];

    /** The game has lit a lamp for a shot of the viewer's. Logic thread. */
    void shot(IsoLightSource lit) {
        lamp = lit;
    }

    /**
     * The game's lamp of the viewer's last shot while the picture's flash stands in for it, or
     * null. The engine numbers a lamp when it takes it and sets the number back to 0 when it lets
     * it go, its life run out. Held from the shot on, before the engine has it, so the squares
     * are read as they are without it.
     */
    private IsoLightSource lampToTakeOut(int playerX, int playerY) {
        if (lamp != null && (lamp.id == 0 && lamp.life <= 0 || !reaches(lamp.x, lamp.y, lamp.radius, playerX, playerY))) {
            lamp = null;
        }
        return lamp == null || !MuzzleFlash.on() || Quality.plain(view) ? null : lamp;
    }

    /** Whether a torch the engine lists is one the picture throws itself: the viewer's own or a vehicle's lamp. */
    static boolean pictured(int id, int firstTorch) {
        return id >= firstTorch && id < firstTorch + TORCHES_A_PLAYER || id >= FIRST_VEHICLE_TORCH;
    }

    /** The number of a player's first torch, as the engine numbers torches alone or on a server. */
    static int firstTorch(int index, short onlineId, boolean joined) {
        return (joined ? onlineId + 1 : index) * TORCHES_A_PLAYER + 1;
    }

    /**
     * Whether a lamp's light reaches the viewer's square. The squares read are widened to take in
     * every square the lamp is on, so a lamp left behind must be let go: one still held after the
     * viewer was moved across the map widened them to the distance between the two, and the light
     * read for them to gigabytes.
     */
    static boolean reaches(int lampX, int lampY, int radius, int playerX, int playerY) {
        return Math.max(Math.abs(lampX - playerX), Math.abs(lampY - playerY)) <= radius;
    }

    /**
     * Reads the squares round the character and round every lit vehicle lamp, on the logic thread, before the scene is
     * built. With no torch on, the squares of the last reading are still read for as long as the engine has a torch on
     * them: the engine takes a torch switched off out of its light a pass or more later, and the squares in its cone are
     * meanwhile lit whole by it, wide and bright.
     *
     * @param reach how far the torch that throws the beam reaches, in tiles, or 0 when the viewer carries none that does
     */
    void gather(IsoCell cell, IsoPlayer player, float reach) {
        int playerX = (int) Math.floor(player.getX()), playerY = (int) Math.floor(player.getY());
        IsoLightSource lamp = lampToTakeOut(playerX, playerY);
        int lampId = lamp == null ? 0 : lamp.id;
        west = north = Integer.MAX_VALUE;
        east = south = Integer.MIN_VALUE;
        if (reach > 0f) {
            cover(playerX, playerY, reach);
        }
        boolean torchOn = coverVehicleLamps(cell) || reach > 0f;
        if (lamp != null) {
            cover(lamp.x, lamp.y, lamp.radius);
        }
        Taken now = taken;
        if (!torchOn && now != null && now.torched) {
            west = Math.min(west, now.fromX);
            north = Math.min(north, now.fromY);
            east = Math.max(east, now.fromX + now.spanX - 1);
            south = Math.max(south, now.fromY + now.spanY - 1);
        }
        int index = player.getIndex();
        IsoChunkMap map = cell.getChunkMap(index);
        int mapSide = IsoChunkMap.chunkGridWidth * WorldScene.CHUNK_TILES;
        west = Math.max(west, map.getWorldXMinTiles());
        north = Math.max(north, map.getWorldYMinTiles());
        east = Math.min(east, map.getWorldXMinTiles() + mapSide - 1);
        south = Math.min(south, map.getWorldYMinTiles() + mapSide - 1);
        if (west > east || north > south) {
            if (now != null) {
                // Taken with no torch and no lamp on any square, the light was the game's own to the bit.
                if (now.torched || now.lampId != 0) {
                    retell(now, null);
                }
                taken = null;
            }
            return;
        }
        int fromX = west, fromY = north, spanX = east - west + 1, spanY = south - north + 1, level = (int) Math.floor(player.getZ());
        int counter = LightingJNI.getUpdateCounter(index);
        if (now != null && now.counter == counter && now.fromX == fromX && now.fromY == fromY && now.level == level && now.spanX == spanX
                && now.spanY == spanY && now.lampId == lampId) {
            // The engine has lit nothing anew since these were read.
            return;
        }
        Taken next = new Taken(fromX, fromY, level, spanX, spanY, counter, lampId);
        int squares = spanX * spanY;
        if (room.length < squares) {
            known = new boolean[squares * 3];
            least = new float[squares * 3];
            day = new float[squares * 3];
            others = new float[squares * 3];
            dark = new float[squares];
            room = new long[squares];
            lampCount = new int[squares];
            lampIds = new int[squares * MOST_LIGHTS];
            lampPlaces = new int[squares * MOST_LIGHTS * 4];
            lampColours = new float[squares * MOST_LIGHTS * 3];
            lampAllowed = new float[squares * MOST_LIGHTS];
        }
        int firstTorch = firstTorch(index, player.getOnlineID(), Online.joined());
        for (int j = 0; j < spanY; j++) {
            for (int i = 0; i < spanX; i++) {
                int at = j * spanX + i;
                IsoGridSquare square = cell.getGridSquare(fromX + i, fromY + j, level);
                if (square == null || !square.isSeen(index)) {
                    room[at] = NOT_THERE;
                    continue;
                }
                room[at] = square.getRoomID();
                next.read[at] = true;
                IsoGridSquare.ILighting lighting = square.lighting[index];
                ColorInfo with = lighting.lightInfo();
                float ownRed = 0f, ownGreen = 0f, ownBlue = 0f;
                others[at * 3] = others[at * 3 + 1] = others[at * 3 + 2] = 0f;
                boolean canSee = lighting.bCanSee(), lamplit = false;
                int lamps = 0;
                for (int light = 0; light < lighting.resultLightCount(); light++) {
                    IsoGridSquare.ResultLight result = lighting.getResultLight(light);
                    if ((result.flags & IsoGridSquare.ResultLight.RLF_TORCH) != 0) {
                        // A torch is listed on a square the character cannot see, and not added.
                        if (!canSee) {
                            continue;
                        }
                        if (pictured(result.id, firstTorch)) {
                            ownRed = Math.max(ownRed, result.r);
                            ownGreen = Math.max(ownGreen, result.g);
                            ownBlue = Math.max(ownBlue, result.b);
                        } else {
                            others[at * 3] = Math.max(others[at * 3], result.r);
                            others[at * 3 + 1] = Math.max(others[at * 3 + 1], result.g);
                            others[at * 3 + 2] = Math.max(others[at * 3 + 2], result.b);
                        }
                    } else if (lampId != 0 && result.id == lampId) {
                        lamplit = true;
                    } else {
                        int listed = at * MOST_LIGHTS + lamps;
                        lampIds[listed] = result.id;
                        lampPlaces[listed * 4] = result.x;
                        lampPlaces[listed * 4 + 1] = result.y;
                        lampPlaces[listed * 4 + 2] = result.z;
                        lampPlaces[listed * 4 + 3] = result.radius;
                        lampColours[listed * 3] = result.r;
                        lampColours[listed * 3 + 1] = result.g;
                        lampColours[listed * 3 + 2] = result.b;
                        lamps++;
                    }
                }
                lampCount[at] = lamps;
                // The torches on a square are joined part by part by the greatest: those taken out add only what they have over the others.
                float red = Math.max(ownRed - others[at * 3], 0f), green = Math.max(ownGreen - others[at * 3 + 1], 0f), blue = Math.max(ownBlue - others[at * 3 + 2], 0f);
                next.lit[at] = lamplit || red > 0f || green > 0f || blue > 0f;
                next.torched |= ownRed > 0f || ownGreen > 0f || ownBlue > 0f;
                daylight(square, day, at * 3);
                dark[at] = lighting.darkMulti();
                without(with.r, next.lit[at] ? red : 0f, day[at * 3], next.light, known, least, at * 3);
                without(with.g, next.lit[at] ? green : 0f, day[at * 3 + 1], next.light, known, least, at * 3 + 1);
                without(with.b, next.lit[at] ? blue : 0f, day[at * 3 + 2], next.light, known, least, at * 3 + 2);
                if (lamplit) {
                    // Nothing of the light is known under the shot's lamp but that it has the daylight.
                    for (int part = 0; part < 3; part++) {
                        known[at * 3 + part] = false;
                        least[at * 3 + part] = day[at * 3 + part];
                    }
                }
                for (int listed = at * MOST_LIGHTS; listed < at * MOST_LIGHTS + lamps; listed++) {
                    lampAllowed[listed] = allows(next.light, known, at * 3, fromX + i, fromY + j, level, lampPlaces, lampColours, listed);
                }
            }
        }
        for (int j = 0; j < spanY; j++) {
            for (int i = 0; i < spanX; i++) {
                int at = j * spanX + i;
                if (!next.lit[at]) {
                    // The game's own light, to the bit: it is what the picture is given for this square.
                    continue;
                }
                if (!known[at * 3] || !known[at * 3 + 1] || !known[at * 3 + 2]) {
                    for (int listed = 0; listed < lampCount[at]; listed++) {
                        float own = lampAllowed[at * MOST_LIGHTS + listed];
                        allowed[listed] = Float.isNaN(own) ? allowedRound(spanX, spanY, i, j, lampIds[at * MOST_LIGHTS + listed]) : own;
                    }
                    fromLights(next.light, known, least, day, others, at * 3, dark[at], fromX + i, fromY + j, level,
                            lampCount[at], lampPlaces, lampColours, at * MOST_LIGHTS, allowed);
                }
                int before = now == null || now.level != level ? -1 : at(now, fromX + i, fromY + j);
                if (before >= 0 && now.lit[before]) {
                    steady(next.light, at * 3, now.light, before * 3);
                }
            }
        }
        if (now != null) {
            retell(now, next);
        }
        retell(next, now);
        taken = next;
    }

    /** Widens the block to read to take in every square a light at (x, y) reaches. */
    private void cover(int x, int y, float reach) {
        int half = (int) Math.ceil(reach) + MARGIN;
        west = Math.min(west, x - half);
        north = Math.min(north, y - half);
        east = Math.max(east, x + half);
        south = Math.max(south, y + half);
    }

    /**
     * Widens the block to read to take in what every lit lamp of a vehicle reaches. The engine lights none for a vehicle
     * of no number (LightingJNI.update). Returns whether any was lit.
     */
    private boolean coverVehicleLamps(IsoCell cell) {
        boolean any = false;
        for (BaseVehicle vehicle : cell.getVehicles()) {
            if (vehicle.vehicleId == -1) {
                continue;
            }
            for (int i = 0; i < vehicle.getLightCount(); i++) {
                VehiclePart part = vehicle.getLightByIndex(i);
                if (part.getLight() == null || !part.getLight().getActive()) {
                    continue;
                }
                // The lamp is on the vehicle's body, at most half its length from its middle.
                float body = Math.max(vehicle.getScript().getExtents().x, vehicle.getScript().getExtents().z) / 2f;
                cover((int) Math.floor(vehicle.getX()), (int) Math.floor(vehicle.getY()), part.getLightDistance() + body);
                any = true;
            }
        }
        return any;
    }

    /**
     * The square's daylight, red, green and blue, as the engine hands it over with its light
     * (getSquareLighting, kept as IsoGridSquare.lightLevel): what its light is with no lamp on it.
     */
    private static void daylight(IsoGridSquare square, float[] into, int at) {
        // Red is the lowest byte, as in the square's light. IsoGridSquare.GetRLightLevel reads it from the
        // third: taken through that, the evening's blue sky was red under the torch (read at 22:15, 1080 of
        // 1168 squares to the byte this way round, against 55 the other).
        int level = square.lightLevel;
        into[at] = (level & 0xff) / 255f;
        into[at + 1] = (level >> 8 & 0xff) / 255f;
        into[at + 2] = (level >> 16 & 0xff) / 255f;
    }

    /**
     * How much of a light whose own way to a square is not known the square has: as much as the
     * squares beside it of its own room that know it have, the most of them. A light that runs
     * round a wall into a room is dimmed by that wall for every square behind it alike, and taken
     * at its straight way, a lamp outside lit the squares of a filling station's shop the torch
     * stopped at 1 orange where they are grey. The most, not the least, of them, so that a square
     * beside a fence keeps the light on its own side.
     */
    private float allowedRound(int spanX, int spanY, int i, int j, int id) {
        long own = room[j * spanX + i];
        float most = Float.NaN;
        for (int side = 0; side < 4; side++) {
            int ni = i + (side == 0 ? -1 : side == 1 ? 1 : 0), nj = j + (side == 2 ? -1 : side == 3 ? 1 : 0);
            if (ni < 0 || nj < 0 || ni >= spanX || nj >= spanY) {
                continue;
            }
            int beside = nj * spanX + ni;
            if (room[beside] != own) {
                continue;
            }
            for (int lamp = beside * MOST_LIGHTS; lamp < beside * MOST_LIGHTS + lampCount[beside]; lamp++) {
                if (lampIds[lamp] == id && !Float.isNaN(lampAllowed[lamp])) {
                    most = Float.isNaN(most) ? lampAllowed[lamp] : Math.max(most, lampAllowed[lamp]);
                }
            }
        }
        return Float.isNaN(most) ? 1f : most;
    }

    /** How far a light listed for a square at (x, y, z) has fallen there by its straight way: 1 at the light, 0 at its radius and past it. */
    static float falls(int x, int y, int z, int[] places, int lamp) {
        int radius = places[lamp * 4 + 3];
        if (radius <= 0) {
            return 0f;
        }
        int dx = Math.abs(x - places[lamp * 4]), dy = Math.abs(y - places[lamp * 4 + 1]);
        float way = Math.max(dx, dy) - Math.min(dx, dy) + CORNER * Math.min(dx, dy) + LEVEL_STEP * Math.abs(z - places[lamp * 4 + 2]);
        return Math.max(1f - way / radius, 0f);
    }

    /** What a light gives one part of a square as a byte, by its straight way. */
    static float gives(float falls, float[] colours, int lamp, int part) {
        return (int) (Math.round(colours[lamp * 3 + part] * 255f) * falls);
    }

    /**
     * How much of what a light gives a square by its straight way the square's known parts let it
     * have, at most 1, or NaN when no known part says. The engine joins the lights by the greatest,
     * so no light gives a known part more than that part has: one that would by its straight way
     * comes round a wall or through a window, which take as much off it.
     */
    static float allows(float[] light, boolean[] known, int at, int x, int y, int z, int[] places, float[] colours, int lamp) {
        float falls = falls(x, y, z, places, lamp), allowed = Float.NaN;
        for (int part = 0; part < 3; part++) {
            float gives = gives(falls, colours, lamp, part);
            if (known[at + part] && gives > 0f) {
                float most = Math.min((light[at + part] * 255f + 0.5f) / gives, 1f);
                allowed = Float.isNaN(allowed) ? most : Math.min(allowed, most);
            }
        }
        return allowed;
    }

    /**
     * Has the chunks built again whose squares the picture was given one light for and would
     * now be given another. The game marks a chunk when a square's own light changes, and a
     * square whose sum is stopped at 1 before and after has not changed for the game, while
     * what is worked out for it may have. A square the torch has come onto or gone
     * off with its light what it was is the same square to the picture, and nothing is told
     * for it: told for every such square, every chunk under the torch was built again at
     * every step and every turn of the head.
     */
    private void retell(Taken of, Taken against) {
        for (int j = 0; j < of.spanY; j++) {
            for (int i = 0; i < of.spanX; i++) {
                int at = j * of.spanX + i;
                if (!of.lit[at]) {
                    continue;
                }
                int x = of.fromX + i, y = of.fromY + j;
                boolean same = false;
                if (against != null && against.level == of.level) {
                    int oi = x - against.fromX, oj = y - against.fromY;
                    if (oi >= 0 && oj >= 0 && oi < against.spanX && oj < against.spanY && against.read[oj * against.spanX + oi]) {
                        int other = (oj * against.spanX + oi) * 3;
                        same = of.light[at * 3] == against.light[other] && of.light[at * 3 + 1] == against.light[other + 1] && of.light[at * 3 + 2] == against.light[other + 2];
                    }
                }
                if (!same) {
                    ChunkMarks.tell(Math.floorDiv(x, WorldScene.CHUNK_TILES), Math.floorDiv(y, WorldScene.CHUNK_TILES));
                }
            }
        }
    }

    /** Where the square at (x, y) is in what was taken, or -1 when it is outside it. */
    private int at(Taken of, int x, int y) {
        int i = x - of.fromX, j = y - of.fromY;
        return i < 0 || j < 0 || i >= of.spanX || j >= of.spanY ? -1 : j * of.spanX + i;
    }

    /**
     * The engine's light is in steps of 1/255, and a sum less the torch's share comes back a
     * step or so off what the square has with no torch on it. Within this of what the square
     * had at the last reading, it has what it had.
     */
    private static final float SAME_WITHIN = 2f / 255f;

    /**
     * Keeps each part of a square's light to the bit what it was at the last reading where it
     * is within a step or two of that, so that the square is the same light for as long as
     * nothing but the torch changes on it: 75 of 300 changes of light in a walk were a step
     * either way, and each built a chunk again.
     */
    static void steady(float[] light, int at, float[] before, int beforeAt) {
        for (int part = 0; part < 3; part++) {
            if (Math.abs(light[at + part] - before[beforeAt + part]) <= SAME_WITHIN) {
                light[at + part] = before[beforeAt + part];
            }
        }
    }

    /**
     * One of a square's red, green or blue: exactly what it is without the torch where the sum was not stopped at 1, and otherwise the least it can be.
     * That is never less than the daylight, which the engine joins to the lamps by the greater.
     *
     * @param day the square's daylight in this part
     */
    static void without(float with, float share, float day, float[] light, boolean[] known, float[] least, int at) {
        known[at] = share <= 0f || with < STOPPED;
        least[at] = share <= 0f ? with : Math.max(with - share, Math.min(day, with));
        // On the engine's own steps, as the square's light is with no torch on it: left as the
        // subtraction gives it, the same square was one light under the torch and another, in its last bits, beside it.
        light[at] = share <= 0f ? with : Math.round(least[at] * 255f) / 255f;
    }

    /**
     * Works out the parts of a square's light that are not known as the engine makes them:
     * the greatest of the daylight and of what each listed light gives, with the other torches
     * added, stopped at 1 and dimmed by darkMulti. A light gives what its straight way gives it,
     * times as much of that as is let through to the square (allows). A part is never less than
     * the least it can be.
     *
     * @param at where the square's red is in light, known, least, day and others
     * @param first where the square's first light is in places (x, y, level, radius each) and colours (red, green, blue each)
     * @param allowed for each light, how much of its straight way's light reaches the square
     */
    static void fromLights(float[] light, boolean[] known, float[] least, float[] day, float[] others, int at, float dark,
            int x, int y, int z, int lamps, int[] places, float[] colours, int first, float[] allowed) {
        for (int part = 0; part < 3; part++) {
            if (known[at + part]) {
                continue;
            }
            float most = Math.round(day[at + part] * 255f);
            for (int lamp = 0; lamp < lamps; lamp++) {
                most = Math.max(most, (int) (gives(falls(x, y, z, places, first + lamp), colours, first + lamp, part) * allowed[lamp]));
            }
            float total = Math.min(255f, most + Math.round(others[at + part] * 255f));
            float made = (int) (Math.min(1f, total / 255f * dark) * 255f) / 255f;
            light[at + part] = Math.clamp(made, least[at + part], 1f);
        }
    }

    /**
     * The square's light for the picture: the game's own, or where the viewer's torch is on
     * the square, that light without the torch, written into the answer given and returned.
     * Logic thread, where the game's light is read (FigureLights).
     */
    ColorInfo light(IsoGridSquare square, int playerIndex, ColorInfo answer) {
        return light(square, square.lighting[playerIndex].lightInfo(), answer);
    }

    /** The same, given the square's light as the game has it. For either thread. */
    ColorInfo light(IsoGridSquare square, ColorInfo with, ColorInfo answer) {
        Taken now = taken;
        if (now == null) {
            return with;
        }
        int i = square.getX() - now.fromX, j = square.getY() - now.fromY;
        if (square.getZ() != now.level || i < 0 || j < 0 || i >= now.spanX || j >= now.spanY) {
            return with;
        }
        int at = j * now.spanX + i;
        if (!now.lit[at]) {
            return with;
        }
        answer.r = now.light[at * 3];
        answer.g = now.light[at * 3 + 1];
        answer.b = now.light[at * 3 + 2];
        answer.a = with.a;
        return answer;
    }
}
