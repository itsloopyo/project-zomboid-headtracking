package com.cameraunlock.zomboid;

import java.util.Arrays;
import zombie.characters.IsoPlayer;
import zombie.core.textures.ColorInfo;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoLightSource;
import zombie.iso.LightingJNI;

/**
 * A square's light without the viewer's own torch in it, nor the lamp the game lights for a shot
 * of theirs.
 *
 * The game lights whole squares with a torch: every square of the level the character stands
 * on that lies in a wide fan before them, the same from the floor to the ceiling and from no
 * side, and nothing on the floor above until the character is on it (read on a staircase:
 * no square of the level above lists the torch until the character's height reaches it). The picture throws the
 * torch's beam itself (PipelineShaders.torchAt), so the game's share has to come out of each
 * square's light first, or a room is lit twice and the first of the two is a pattern of
 * squares.
 *
 * The lighting engine says what that share is. With a square's light it lists the lights that
 * reach the square, and for a torch the colour it lists is what the torch adds there. On a
 * square the character can see at this moment the light is the light without the torch plus
 * that share, each of red, green and blue stopped at 1. On one they cannot see the torch is
 * listed and not added. Both were read off every square round a character in five places,
 * 1374 squares, with a torch lit and with none, and held on all of them.
 *
 * So where the sum is under 1 the light without the torch is known exactly. Where it was
 * stopped at 1, which is most of the fan near the character, what is left is only that the
 * light was at least 1 less the share. Such a square keeps the light it last had when it
 * was known, which is the light it had before the torch came onto it: a room's own light does
 * not change because a torch is turned on it. Only a square never known since it came within
 * the torch's reach takes its light from the squares round it that are known, room by room,
 * the nearest first: a dark room stays as dark as its corners, and a lamp's light runs on
 * under the fan as it lies beside it. Taken from the squares round it every time, a square's
 * light was another at every step the torch's fan moved, and every chunk under the fan was
 * built again for it.
 *
 * The game lights a shot too (EffectsManager.startMuzzleFlash), with a lamp of reach 18 at the
 * shooter's square. Its light reaches the squares a tenth of a second after the shot and stays a
 * quarter of a second, so a shot was the picture's flash (MuzzleFlash) and then a second, longer
 * light. While that flash shows, the lamp is taken out too. The engine lists it by its number on
 * every square it reaches, seen or not, but with its own colour and not what it adds there (0.55
 * listed on the shooter's square, lit from 0.22 to 0.55, and on one 4 squares off, lit to 0.42).
 * So a square it is on keeps the light it had before the shot, read from the shot on.
 *
 * The engine keeps its torch. What the character can see by it, how well zombies see the
 * character and whether a page can be read are worked out from the same squares, and none of
 * that changes.
 */
final class TorchLess {

    /** The squares are read this far past the torch's reach, so that the fan has known squares round it. */
    private static final int MARGIN = 2;
    /** The engine hands light over in 255 steps: at the last of them a sum may have been stopped. */
    private static final float STOPPED = 1f - 0.5f / 255f;
    /** A player's torches are numbered from four times the player's index plus 1, four at most: LightingJNI.checkPlayerTorches. */
    private static final int TORCHES_A_PLAYER = 4;
    /** A square of no room and one that is not there or has not been seen. Rooms are numbered from 0, and -1 is out of doors. */
    static final long NOT_THERE = Long.MIN_VALUE;

    /**
     * What one gathering found, never written again once it is handed over: a block of squares
     * of the character's level, the light of each without the torch, which is the game's own
     * light for a square the torch is not on, and which of them the torch or the lamp of a shot is on.
     */
    private static final class Taken {
        final int fromX, fromY, level, span, counter, lampId;
        final float[] light;
        final boolean[] lit;
        /** Whether the square was there to be read and had been seen. */
        final boolean[] read;
        /** Whether a square's light here is one it was known to have, now or when it was last known, and not one taken from the squares round it. */
        final boolean[] met;
        /** Whether the engine still had any of the viewer's torches on a square. */
        boolean torched;

        Taken(int fromX, int fromY, int level, int span, int counter, int lampId) {
            this.lampId = lampId;
            this.fromX = fromX;
            this.fromY = fromY;
            this.level = level;
            this.span = span;
            this.counter = counter;
            light = new float[span * span * 3];
            lit = new boolean[span * span];
            met = new boolean[span * span];
            read = new boolean[span * span];
        }
    }

    private volatile Taken taken;
    /** How far the torch reached when it was last on, read on after it goes off while the engine still has it on squares. */
    private float torchReach;
    /** The lamp the game lit for the viewer's last shot, while the game may still have it. Logic thread. */
    private IsoLightSource lamp;
    /** The number of the view whose picture this is for. */
    private final int view;

    TorchLess(int view) {
        this.view = view;
    }
    /** What one reading works with, kept between readings and grown to the widest reach met: a reading writes every part of them that it reads. */
    private boolean[] known = new boolean[0];
    private float[] least = new float[0];
    private long[] room = new long[0];

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
     * Reads the squares round the character, on the logic thread, before the scene is built.
     *
     * @param reach how far the torch that throws the beam reaches, in tiles, or 0 when the viewer carries none that does
     * @param untilGone whether, with no torch, its light is still taken out for as long as the engine has it on squares:
     *     the engine takes a torch switched off out of its light a pass or more later, and the squares in its cone are
     *     meanwhile lit whole by it, wide and bright
     */
    void gather(IsoCell cell, IsoPlayer player, float reach, boolean untilGone) {
        if (reach > 0f) {
            torchReach = reach;
        } else if (untilGone && taken != null && taken.torched) {
            reach = torchReach;
        }
        int playerX = (int) Math.floor(player.getX()), playerY = (int) Math.floor(player.getY());
        IsoLightSource lamp = lampToTakeOut(playerX, playerY);
        int lampId = lamp == null ? 0 : lamp.id;
        if (lamp != null) {
            reach = Math.max(reach, lamp.radius + Math.max(Math.abs(lamp.x - playerX), Math.abs(lamp.y - playerY)));
        }
        if (reach <= 0f) {
            if (taken != null) {
                // Taken with no torch and no lamp on any square, the light was the game's own to the bit.
                if (taken.torched || taken.lampId != 0) {
                    retell(taken, null);
                }
                taken = null;
            }
            return;
        }
        int index = player.getIndex();
        int half = (int) Math.ceil(reach) + MARGIN, span = 2 * half + 1;
        int fromX = playerX - half, fromY = playerY - half, level = (int) Math.floor(player.getZ());
        int counter = LightingJNI.getUpdateCounter(index);
        Taken now = taken;
        if (now != null && now.counter == counter && now.fromX == fromX && now.fromY == fromY && now.level == level && now.span == span && now.lampId == lampId) {
            // The engine has lit nothing anew since these were read.
            return;
        }
        Taken next = new Taken(fromX, fromY, level, span, counter, lampId);
        if (room.length < span * span) {
            known = new boolean[span * span * 3];
            least = new float[span * span * 3];
            room = new long[span * span];
        }
        int firstTorch = index * TORCHES_A_PLAYER + 1;
        for (int j = 0; j < span; j++) {
            for (int i = 0; i < span; i++) {
                int at = j * span + i;
                IsoGridSquare square = cell.getGridSquare(fromX + i, fromY + j, level);
                if (square == null || !square.isSeen(index)) {
                    room[at] = NOT_THERE;
                    continue;
                }
                room[at] = square.getRoomID();
                next.read[at] = true;
                IsoGridSquare.ILighting lighting = square.lighting[index];
                ColorInfo with = lighting.lightInfo();
                float red = 0f, green = 0f, blue = 0f;
                boolean canSee = lighting.bCanSee(), lamplit = false;
                for (int light = 0; light < lighting.resultLightCount(); light++) {
                    IsoGridSquare.ResultLight result = lighting.getResultLight(light);
                    if ((result.flags & IsoGridSquare.ResultLight.RLF_TORCH) == 0) {
                        lamplit |= lampId != 0 && result.id == lampId;
                    } else if (canSee && result.id >= firstTorch && result.id < firstTorch + TORCHES_A_PLAYER) {
                        red += result.r;
                        green += result.g;
                        blue += result.b;
                    }
                }
                next.lit[at] = lamplit || red > 0f || green > 0f || blue > 0f;
                next.torched |= red > 0f || green > 0f || blue > 0f;
                without(with.r, red, next.light, known, least, at * 3);
                without(with.g, green, next.light, known, least, at * 3 + 1);
                without(with.b, blue, next.light, known, least, at * 3 + 2);
                if (lamplit) {
                    // Nothing of the light is known under the lamp: the square keeps what it had before the shot.
                    Arrays.fill(known, at * 3, at * 3 + 3, false);
                    Arrays.fill(least, at * 3, at * 3 + 3, 0f);
                }
                if (!next.lit[at]) {
                    // The game's own light, to the bit: it is what the picture is given for this square.
                    next.met[at] = true;
                    continue;
                }
                int before = now == null || now.level != level ? -1 : at(now, fromX + i, fromY + j);
                next.met[at] = keep(next.light, known, least, at * 3, before >= 0 && now.met[before] ? now.light : null, before * 3);
            }
        }
        settle(span, next.light, known, least, room);
        if (now != null) {
            retell(now, next);
        }
        retell(next, now);
        taken = next;
    }

    /**
     * Has the chunks built again whose squares the picture was given one light for and would
     * now be given another. The game marks a chunk when a square's own light changes, and a
     * square whose sum is stopped at 1 before and after has not changed for the game, while
     * what it takes from the squares round it has. A square the torch has come onto or gone
     * off with its light what it was is the same square to the picture, and nothing is told
     * for it: told for every such square, every chunk under the torch was built again at
     * every step and every turn of the head.
     */
    private void retell(Taken of, Taken against) {
        for (int j = 0; j < of.span; j++) {
            for (int i = 0; i < of.span; i++) {
                int at = j * of.span + i;
                if (!of.lit[at]) {
                    continue;
                }
                int x = of.fromX + i, y = of.fromY + j;
                boolean same = false;
                if (against != null && against.level == of.level) {
                    int oi = x - against.fromX, oj = y - against.fromY;
                    if (oi >= 0 && oj >= 0 && oi < against.span && oj < against.span && against.read[oj * against.span + oi]) {
                        int other = (oj * against.span + oi) * 3;
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
        return i < 0 || j < 0 || i >= of.span || j >= of.span ? -1 : j * of.span + i;
    }

    /**
     * The engine's light is in steps of 1/255, and a sum less the torch's share comes back a
     * step or so off what the square has with no torch on it. Within this of what the square
     * was last known to have, it has what it had.
     */
    private static final float SAME_WITHIN = 2f / 255f;

    /**
     * Gives a square the light it was last known to have, where it has one. A part that is
     * known now and within a step or two of that stays to the bit what it was, so that the
     * square is the same light for as long as nothing but the torch changes on it. A part the
     * torch has blotted out takes what was known, or the least it can now be if that is more,
     * and counts as known from here on. With no light known before, the square is left as it
     * was read.
     *
     * @param before the lights of the last taking, or null when the square had none known in it
     * @return whether the square's light is now all known
     */
    static boolean keep(float[] light, boolean[] known, float[] least, int at, float[] before, int beforeAt) {
        if (before == null) {
            return known[at] && known[at + 1] && known[at + 2];
        }
        for (int part = 0; part < 3; part++) {
            float was = before[beforeAt + part];
            if (!known[at + part]) {
                light[at + part] = Math.max(was, least[at + part]);
                known[at + part] = true;
            } else if (Math.abs(light[at + part] - was) <= SAME_WITHIN) {
                light[at + part] = was;
            }
        }
        return true;
    }

    /** One of a square's red, green or blue: exactly what it is without the torch where the sum was not stopped at 1, and otherwise the least it can be. */
    static void without(float with, float share, float[] light, boolean[] known, float[] least, int at) {
        known[at] = share <= 0f || with < STOPPED;
        least[at] = share <= 0f ? with : Math.max(with - share, 0f);
        // On the engine's own steps, as the square's light is with no torch on it: left as the
        // subtraction gives it, the same square was one light under the torch and another, in its last bits, beside it.
        light[at] = share <= 0f ? with : Math.round(least[at] * 255f) / 255f;
    }

    /** A square is not made more than this many times as bright as the squares round it to fit the part of its light that is known. */
    private static final float MOST_SCALED = 4f;

    /**
     * Gives each square whose light is not all known the light of the squares round it. Each
     * pass, a square with a settled one beside it in its own room takes the mean of those and
     * is settled for the next pass, so the light is carried inwards from the edge of what the
     * torch has blotted out, never through a wall into the next room.
     *
     * A torch's light is red before it is blue, so its sum is often stopped at 1 in red while
     * the blue is still exact. The parts that are known stay as they are, and the others take
     * the colour of the squares round the square at the brightness its known parts have: each
     * part taken from its own neighbours would leave a lamplit ceiling in squares of pink and
     * green. A square no settled one can be reached from keeps the least it can be. Nothing
     * is given less than that least, nor more than 1.
     *
     * @param light the squares, three numbers each
     * @param known for each number, whether it is exact
     * @param least for each number, the least it can be
     * @param room  for each square its room, or NOT_THERE for one that is to be left out
     */
    static void settle(int span, float[] light, boolean[] known, float[] least, long[] room) {
        boolean[] settled = new boolean[span * span], settledNow = new boolean[span * span];
        for (int at = 0; at < span * span; at++) {
            settled[at] = room[at] != NOT_THERE && known[at * 3] && known[at * 3 + 1] && known[at * 3 + 2];
        }
        float[] round = new float[3];
        boolean any = true;
        while (any) {
            any = false;
            Arrays.fill(settledNow, false);
            for (int j = 0; j < span; j++) {
                for (int i = 0; i < span; i++) {
                    int at = j * span + i;
                    if (room[at] == NOT_THERE || settled[at]) {
                        continue;
                    }
                    Arrays.fill(round, 0f);
                    int count = 0;
                    for (int side = 0; side < 4; side++) {
                        int ni = i + (side == 0 ? -1 : side == 1 ? 1 : 0), nj = j + (side == 2 ? -1 : side == 3 ? 1 : 0);
                        if (ni < 0 || nj < 0 || ni >= span || nj >= span) {
                            continue;
                        }
                        int beside = nj * span + ni;
                        // Settled before this pass began: one settled in this pass would hand its light on a square further in one direction than the others.
                        if (room[beside] == room[at] && settled[beside]) {
                            for (int part = 0; part < 3; part++) {
                                round[part] += light[beside * 3 + part];
                            }
                            count++;
                        }
                    }
                    if (count == 0) {
                        continue;
                    }
                    float has = 0f, would = 0f;
                    for (int part = 0; part < 3; part++) {
                        round[part] /= count;
                        if (known[at * 3 + part]) {
                            has += light[at * 3 + part];
                            would += round[part];
                        }
                    }
                    float scale = would > 1e-4f ? Math.min(has / would, MOST_SCALED) : 1f;
                    for (int part = 0; part < 3; part++) {
                        if (!known[at * 3 + part]) {
                            light[at * 3 + part] = Math.clamp(round[part] * scale, least[at * 3 + part], 1f);
                        }
                    }
                    settledNow[at] = true;
                    any = true;
                }
            }
            for (int at = 0; at < settledNow.length; at++) {
                settled[at] |= settledNow[at];
            }
        }
    }

    /**
     * The square's light for the picture: the game's own, or where the viewer's torch is on
     * the square, that light without the torch, written into the answer given and returned.
     * For either thread.
     */
    ColorInfo light(IsoGridSquare square, int playerIndex, ColorInfo answer) {
        return light(square, square.lighting[playerIndex].lightInfo(), answer);
    }

    /** The same, given the square's light as the game has it. */
    ColorInfo light(IsoGridSquare square, ColorInfo with, ColorInfo answer) {
        Taken now = taken;
        if (now == null) {
            return with;
        }
        int i = square.getX() - now.fromX, j = square.getY() - now.fromY;
        if (square.getZ() != now.level || i < 0 || j < 0 || i >= now.span || j >= now.span) {
            return with;
        }
        int at = j * now.span + i;
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
