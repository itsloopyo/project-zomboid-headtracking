package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import zombie.characters.IsoPlayer;
import zombie.core.textures.ColorInfo;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;

/**
 * Where the game counts a square too dark to read, build or craft by, round the viewer, for the
 * develop shader: under a roof the picture opens the eye no further there than the game's own
 * light shows the square, though never to black. Without it a stairwell the game would not let the
 * player build in came out bright, the eye having opened up to it as it does to any dim room. Out
 * of doors the eye does not open up to a dark square, and the night is left as the picture has it.
 *
 * The game's test is IsoPlayer.tooDarkToRead: the player's own square is too dark where the
 * mean of its light and its light over the worn items' vision modifier is under 0.43, its light
 * being the strongest of its colours, and nothing is too dark while the player holds or wears
 * something that gives light. Here each square is put to the same test by its own light, and
 * the darkening comes in over a RAMP under the line, so neither the line across a floor nor a
 * lamp's light moving over it shows as a step. Only the player's own square is spared by a
 * light carried: the squares a torch does not reach stay as dark as the game has them.
 *
 * The game's light is only true of a square the player can see now: one seen before is dimmed,
 * one never seen has none. Such a square takes its light from the squares beside it that are in
 * sight (Sight.fill), so a room half seen is all of the light of the half in sight. One with
 * none in sight round it is not known, and is left as the picture has it.
 *
 * Three levels of a block of SIDE by SIDE squares round the viewer, the viewer's level in the
 * middle, read on the logic thread with the frame every REREAD frames, and read whole by the
 * render thread when that frame is drawn, as Sight is.
 */
final class TooDark {
    static final int SIDE = 64, LEVELS = 3;
    /** The game's line: tooDarkToRead's. */
    static final float LINE = 0.43f;
    /** How far under the line the square is wholly as the game's light shows it. */
    static final float RAMP = 0.1f;
    /** What the picture's light is multiplied by at most, over a square the picture lights darker than the game. */
    static final float MOST_RATIO = 4f;
    private static final int REREAD = 3;

    /** The block's first square and the viewer's level. */
    int x, y, level;
    /** Per square, level by level: how far into the dark it is, 0 to 1, and what the picture's light is multiplied by to be the game's. */
    final FloatBuffer map = ByteBuffer.allocateDirect(SIDE * SIDE * LEVELS * 2 * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
    /** Per square, how far the ground of the viewer's level is lifted over its level's height, in world height. */
    final FloatBuffer lift = ByteBuffer.allocateDirect(SIDE * SIDE * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
    /** Whether the block has been read once. */
    boolean read;
    private int frames;
    private final int[] strongest = new int[SIDE * SIDE], mean = new int[SIDE * SIDE];
    private final int[] scratch = new int[SIDE * SIDE], taken = new int[SIDE * SIDE];
    private final float[] drawn = new float[SIDE * SIDE];
    private final boolean[] outside = new boolean[SIDE * SIDE];

    /**
     * How far into the dark a square is, 0 at the game's line or over it to 1 a RAMP under it, by
     * its light (the strongest of its colours) and the player's worn items' vision modifier.
     */
    static float darkness(float light, float visionModifier) {
        float seen = (light / visionModifier + light) / 2f;
        return Math.clamp((LINE - seen) / RAMP, 0f, 1f);
    }

    /**
     * What the picture's light on a square is multiplied by to be as the game's own view shows
     * it, by the mean of the square's colours as the game has it and as the picture lights it.
     * The picture is lit in linear light, the game's figures are as they show on the screen.
     */
    static float ratio(float gameMean, float drawnMean) {
        if (drawnMean <= 0f) {
            return 1f;
        }
        return Math.min((float) Math.pow(gameMean / drawnMean, 2.2), MOST_RATIO);
    }

    /** Reads the block round the viewer again when it is due. Logic thread. */
    void take(View view) {
        if (read && ++frames < REREAD) {
            return;
        }
        frames = 0;
        IsoPlayer player = view.player();
        IsoCell cell = IsoWorld.instance == null ? null : IsoWorld.instance.currentCell;
        if (player == null || cell == null) {
            read = false;
            return;
        }
        int playerIndex = player.getPlayerNum();
        level = (int) Math.floor(player.getZ());
        x = (int) Math.floor(player.getX()) - SIDE / 2;
        y = (int) Math.floor(player.getY()) - SIDE / 2;
        float visionModifier = player.getWornItemsVisionModifier();
        WorldScene scene = view.worldScene;
        float open = scene.openNow * scene.daylightKeptNow;
        for (int row = 0; row < SIDE; row++) {
            for (int column = 0; column < SIDE; column++) {
                lift.put(row * SIDE + column, scene.terrain.lift(x + column + 0.5f, y + row + 0.5f, level));
            }
        }
        for (int slot = 0; slot < LEVELS; slot++) {
            int z = level - 1 + slot;
            for (int row = 0; row < SIDE; row++) {
                for (int column = 0; column < SIDE; column++) {
                    int at = row * SIDE + column;
                    IsoGridSquare square = cell.getGridSquare(x + column, y + row, z);
                    outside[at] = square == null || square.isOutside();
                    if (square == null) {
                        strongest[at] = mean[at] = Sight.UNREAD;
                        drawn[at] = open;
                        continue;
                    }
                    float picture = view.characters.drawnLight(square);
                    drawn[at] = Float.isNaN(picture) ? open : picture;
                    // isCanSee brings the square's light up to date from the lighting engine.
                    if (!square.isCanSee(playerIndex)) {
                        strongest[at] = mean[at] = Sight.CLOSED;
                        continue;
                    }
                    ColorInfo light = square.lighting[playerIndex].lightInfo();
                    strongest[at] = Math.round(Math.clamp(Math.max(light.r, Math.max(light.g, light.b)), 0f, 1f) * 255f);
                    mean[at] = Math.round(Math.clamp((light.r + light.g + light.b) / 3f, 0f, 1f) * 255f);
                }
            }
            Sight.fill(strongest, scratch, taken, SIDE, SIDE, Sight.UNREAD);
            Sight.fill(mean, scratch, taken, SIDE, SIDE, Sight.UNREAD);
            for (int at = 0; at < SIDE * SIDE; at++) {
                boolean known = strongest[at] >= 0 && !outside[at];
                float dark = known ? darkness(strongest[at] / 255f, visionModifier) : 0f;
                int put = (slot * SIDE * SIDE + at) * 2;
                map.put(put, dark);
                map.put(put + 1, known ? ratio(mean[at] / 255f, drawn[at]) : 1f);
            }
        }
        // The game spares the player's own square while they carry a light.
        if (!player.tooDarkToRead()) {
            int at = (SIDE * SIDE + (SIDE / 2) * SIDE + SIDE / 2) * 2;
            map.put(at, 0f);
        }
        read = true;
    }
}
