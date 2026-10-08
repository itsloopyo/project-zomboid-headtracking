package com.cameraunlock.zomboid;

import java.util.Arrays;
import zombie.characters.IsoPlayer;
import zombie.core.textures.ColorInfo;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.LightingJNI;

/**
 * A square's light without the viewer's own torch in it.
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
 * light was at least 1 less the share. Those squares take theirs from the squares round them
 * that are known, room by room, the nearest first: a dark room stays as dark as its corners,
 * and a lamp's light runs on under the fan as it lies beside it.
 *
 * The engine keeps its torch. What the character can see by it, how well zombies see the
 * character and whether a page can be read are worked out from the same squares, and none of
 * that changes.
 */
final class TorchLess {
    private TorchLess() {}

    /** The squares are read this far past the torch's reach, so that the fan has known squares round it. */
    private static final int MARGIN = 2;
    /** The engine hands light over in 255 steps: at the last of them a sum may have been stopped. */
    private static final float STOPPED = 1f - 0.5f / 255f;
    /** A player's torches are numbered from four times the player's index plus 1, four at most: LightingJNI.checkPlayerTorches. */
    private static final int TORCHES_A_PLAYER = 4;
    /** A square of no room and one that is not there or has not been seen. Rooms are numbered from 0, and -1 is out of doors. */
    static final long NOT_THERE = Long.MIN_VALUE;

    /** What one gathering found, never written again once it is handed over: a block of squares of the character's level, the light of each without the torch, and which of them the torch is on. */
    private static final class Taken {
        final int fromX, fromY, level, span, counter;
        final float[] light;
        final boolean[] lit;

        Taken(int fromX, int fromY, int level, int span, int counter) {
            this.fromX = fromX;
            this.fromY = fromY;
            this.level = level;
            this.span = span;
            this.counter = counter;
            light = new float[span * span * 3];
            lit = new boolean[span * span];
        }
    }

    private static volatile Taken taken;

    /**
     * Reads the squares round the character, on the logic thread, before the scene is built.
     *
     * @param reach how far the torch that throws the beam reaches, in tiles, or 0 when the viewer carries none that does
     */
    static void gather(IsoCell cell, IsoPlayer player, float reach) {
        if (reach <= 0f) {
            if (taken != null) {
                retell(taken, null);
                taken = null;
            }
            return;
        }
        int index = player.getIndex();
        int half = (int) Math.ceil(reach) + MARGIN, span = 2 * half + 1;
        int fromX = (int) Math.floor(player.getX()) - half, fromY = (int) Math.floor(player.getY()) - half, level = (int) Math.floor(player.getZ());
        int counter = LightingJNI.getUpdateCounter(index);
        Taken now = taken;
        if (now != null && now.counter == counter && now.fromX == fromX && now.fromY == fromY && now.level == level && now.span == span) {
            // The engine has lit nothing anew since these were read.
            return;
        }
        Taken next = new Taken(fromX, fromY, level, span, counter);
        boolean[] known = new boolean[span * span * 3];
        float[] least = new float[span * span * 3];
        long[] room = new long[span * span];
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
                IsoGridSquare.ILighting lighting = square.lighting[index];
                ColorInfo with = lighting.lightInfo();
                float red = 0f, green = 0f, blue = 0f;
                if (lighting.bCanSee()) {
                    for (int light = 0; light < lighting.resultLightCount(); light++) {
                        IsoGridSquare.ResultLight result = lighting.getResultLight(light);
                        if ((result.flags & IsoGridSquare.ResultLight.RLF_TORCH) != 0 && result.id >= firstTorch && result.id < firstTorch + TORCHES_A_PLAYER) {
                            red += result.r;
                            green += result.g;
                            blue += result.b;
                        }
                    }
                }
                next.lit[at] = red > 0f || green > 0f || blue > 0f;
                without(with.r, red, next.light, known, least, at * 3);
                without(with.g, green, next.light, known, least, at * 3 + 1);
                without(with.b, blue, next.light, known, least, at * 3 + 2);
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
     * what it takes from the squares round it has; nor has one the torch has just left at a
     * light it already had.
     */
    private static void retell(Taken of, Taken against) {
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
                    if (oi >= 0 && oj >= 0 && oi < against.span && oj < against.span && against.lit[oj * against.span + oi]) {
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

    /** One of a square's red, green or blue: exactly what it is without the torch where the sum was not stopped at 1, and otherwise the least it can be. */
    static void without(float with, float share, float[] light, boolean[] known, float[] least, int at) {
        known[at] = share <= 0f || with < STOPPED;
        least[at] = Math.max(with - share, 0f);
        light[at] = least[at];
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
    static ColorInfo light(IsoGridSquare square, int playerIndex, ColorInfo answer) {
        ColorInfo with = square.lighting[playerIndex].lightInfo();
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
