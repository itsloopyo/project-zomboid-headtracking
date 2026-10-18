package com.cameraunlock.zomboid;

import zombie.characters.IsoPlayer;
import zombie.core.textures.ColorInfo;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;

/**
 * The light what the viewer holds is in, as the world round it is lit by it: the game's light
 * for a square, the viewer's torch left out (TorchLess) and the daylight taken down as the hour
 * has it (Evening), which is what a person standing there is given (Characters.lightAt). The hands,
 * what they hold and the body seen from the eye are lit by it, and a vehicle's cabin from the seat
 * by the light of the square the viewer sits on.
 *
 * The game's own light for the viewer's model is not that light: it has the viewer's torch in it
 * and keeps the full daylight in a room at dusk.
 *
 * The logic thread gathers the square and the game's light for it, where the map may be read, and
 * the scene works the light out from that with the hour it has for the picture (light).
 */
final class HeldLight {
    private final View view;

    HeldLight(View view) {
        this.view = view;
    }

    /** How far before the eye, along the ground the aim faces, what the viewer holds is lit as standing: about the middle of a long gun at the hip, in tiles. */
    static final float AHEAD = 0.3f;
    /** In a gathering: 1 if a square was found, its light as the game has it less the torch, red, green and blue, and 1 if it is out of doors. */
    static final int FOUND = 0, RED = 1, OUTDOORS = 4;
    /** In the light: the light, 0 to 1, 1 if it is out of doors, and the cabin's light, red, green and blue. */
    static final int LIGHT = 0, OUTSIDE = 1, CABIN = 2;

    volatile float[] now = new float[5];
    private final ColorInfo answer = new ColorInfo();
    private final int[] chosen = new int[2];
    private IsoCell cell;
    private int level;
    private WorldQuery world;
    private final Squares squares = new Squares() {
        @Override
        public boolean seen(int x, int y) {
            IsoGridSquare square = cell.getGridSquare(x, y, level);
            return square != null && square.isSeen(view.index);
        }

        @Override
        public boolean between(int fromX, int fromY, int toX, int toY) {
            return world.solidToTheEye(fromX, fromY, toX, toY);
        }
    };

    /** The map as choose asks it, on one level. */
    interface Squares {
        /** Whether there is a square there that the viewer has seen: the game holds no light for one they have not. */
        boolean seen(int x, int y);

        /** Whether the eye could not pass the edge between two squares side by side. */
        boolean between(int fromX, int fromY, int toX, int toY);
    }

    /**
     * Which square what is held is lit as standing in, into square. On foot, the one AHEAD before the
     * eye along the aim, where it is beside the eye's own and nothing stands between the two that the
     * eye itself could not pass, either way round a corner diagonally; else the eye's own; and where the
     * eye leans out over air the game has no square for, or one not seen, the one the viewer stands on.
     * Aimed straight up or down, what is held is under the eye. Seated, the one the viewer sits on.
     * False where none of those has light said.
     *
     * @param standX where the viewer stands or sits
     * @param eyeX   the eye, as far as it leans
     * @param wayX   the ground part of the way the aim faces, not of the way the head looks: turning the head does not move what is held
     */
    static boolean choose(Squares squares, float standX, float standY, float eyeX, float eyeY, float wayX, float wayY, boolean seated, int[] square) {
        int x = (int) Math.floor(eyeX), y = (int) Math.floor(eyeY);
        if (seated || !squares.seen(x, y)) {
            square[0] = (int) Math.floor(standX);
            square[1] = (int) Math.floor(standY);
            return squares.seen(square[0], square[1]);
        }
        square[0] = x;
        square[1] = y;
        float flat = (float) Math.hypot(wayX, wayY);
        if (flat < 1e-3f) {
            return true;
        }
        int toX = (int) Math.floor(eyeX + wayX / flat * AHEAD), toY = (int) Math.floor(eyeY + wayY / flat * AHEAD);
        if (toX == x && toY == y || !squares.seen(toX, toY)) {
            return true;
        }
        boolean open = toX == x || toY == y ? !squares.between(x, y, toX, toY)
                : !squares.between(x, y, toX, y) && !squares.between(toX, y, toX, toY) || !squares.between(x, y, x, toY) && !squares.between(x, toY, toX, toY);
        if (open) {
            square[0] = toX;
            square[1] = toY;
        }
        return true;
    }

    /**
     * Gathers the square for this frame and the game's light on it. world has been told the cell and
     * the viewer's level.
     *
     * @param seated whether the viewer sits in a vehicle: what is held is in it
     */
    void gather(IsoPlayer player, WorldQuery world, float eyeX, float eyeY, float wayX, float wayY, boolean seated) {
        this.cell = IsoWorld.instance.currentCell;
        this.level = (int) Math.floor(player.getZ());
        this.world = world;
        float[] found = new float[5];
        if (choose(squares, player.getX(), player.getY(), eyeX, eyeY, wayX, wayY, seated, chosen)) {
            IsoGridSquare square = cell.getGridSquare(chosen[0], chosen[1], level);
            // As the room round it is lit: with the day it lets in.
            ColorInfo light = view.worldScene.daylitNow(square, view.torchLess.light(square, view.index, answer), answer);
            found[FOUND] = 1f;
            found[RED] = light.r;
            found[RED + 1] = light.g;
            found[RED + 2] = light.b;
            found[OUTDOORS] = square.isOutside() ? 1f : 0f;
        }
        now = found;
        // Held past the gathering, they would keep a save that is left alive.
        this.cell = null;
        this.world = null;
    }

    /** Called as the game leaves a save: the first frame of the next is not lit by this one's light. */
    void forget() {
        now = new float[5];
    }

    /**
     * The light from a gathering, with the hour the scene has for the picture: the open ground's light as
     * the game has it, red, green and blue, and how much of the daylight is kept out of doors and in a room
     * (Evening). With no square found, what is held is lit as the open ground round it is. The cabin's own
     * lighting takes the evening from the sky, so its light is the game's whole daylight. Into light.
     */
    static void light(float[] gathered, float openRed, float openGreen, float openBlue, float daylightKept, float roomKept, float[] light) {
        float open = (openRed + openGreen + openBlue) / 3f;
        boolean found = gathered[FOUND] > 0.5f;
        boolean outside = !found || gathered[OUTDOORS] > 0.5f;
        float red = found ? gathered[RED] : openRed, green = found ? gathered[RED + 1] : openGreen, blue = found ? gathered[RED + 2] : openBlue;
        float kept = daylightKept * (outside ? 1f : roomKept);
        light[LIGHT] = Math.clamp(Evening.dimmed((red + green + blue) / 3f, open, kept), 0f, 1f);
        light[OUTSIDE] = outside ? 1f : 0f;
        light[CABIN] = red;
        light[CABIN + 1] = green;
        light[CABIN + 2] = blue;
    }
}
