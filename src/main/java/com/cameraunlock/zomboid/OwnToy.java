package com.cameraunlock.zomboid;

/**
 * The mod's own model of the toy cap rifle, drawn in the viewer's hands in place of the game's:
 * made by tools/models/toy.py and kept in the jar (OwnGun says how it is held, lit and drawn).
 *
 * The shot lets the hammer fall on a cap. A moment later the thumb cocks it again, and the
 * cylinder turns the next chamber up under it, an eighth of a turn.
 *
 * All of it is the render thread's.
 */
final class OwnToy extends OwnGun {
    /** The parts the file has, in its order. */
    private static final int FIXED = 0, CYLINDER = 1, HAMMER = 2;
    static final int PARTS = 3, MOVES = 4;
    /** No part slides back toward the eye as the action is worked. */
    static final int SLIDES = OwnModel.NO_PART;
    /** Where the numbers after the parts are: the cylinder's axis under the bore, the hammer's pin along and over the bore, and how far it falls. */
    private static final int AXIS_UP = 0, HAMMER_ALONG = 1, HAMMER_UP = 2, FALLS = 3;

    /** When the hammer is cocked again after the shot, in seconds, which turns the cylinder on. */
    private static final float COCKED_FROM = 0.18f, COCKED_TO = 0.42f;
    private static final float EIGHTH = (float) (Math.PI / 4);

    private final Caps caps = new Caps();
    private final float[] turn = new float[16];
    private float hammer, cylinder;
    /** The game's frame the hammer was last moved on in. */
    private int workedIn = Integer.MIN_VALUE;

    /** With the sights up the hammer falls, and the cylinder turns an eighth on: at the ends of their turns, and between. */
    static float[][] poses(float[] moves) {
        float[][] turns = new float[1 + 8][];
        turns[0] = new float[] {HAMMER, 0f, moves[HAMMER_ALONG], moves[HAMMER_UP], 1f, 0f, 0f, -moves[FALLS]};
        for (int step = 0; step < 8; step++) {
            turns[1 + step] = new float[] {CYLINDER, 0f, 0f, moves[AXIS_UP], 0f, 1f, 0f, EIGHTH * (step + 1) / 8f};
        }
        return turns;
    }

    OwnToy(View view) {
        // Plastic, sprayed and glossy, mirrors what is round it as varnish does.
        super(view, 0.45f, 0f, 0f);
    }

    /** How far the hammer is cocked a time after the shot, 0 fallen to 1 cocked: cocked before any. */
    static float cocked(float since) {
        return since < 0f ? 1f : ViewModelMath.smooth((since - COCKED_FROM) / (COCKED_TO - COCKED_FROM));
    }

    /**
     * The shots taken with the gun one after another, from what the game says of it frame by
     * frame: how long ago the last was, and how many eighths the cylinder has turned on for them.
     */
    static final class Caps {
        private int item = Integer.MIN_VALUE, shotsSeen;
        /** Seconds since the last shot, or less than nought for none since the gun was taken in hand. */
        float since = -1f;
        /** How many eighths the cylinder has turned since the gun was taken in hand. */
        int turned;

        /**
         * @param item      the game's id of the item held: another is another gun
         * @param carriedOn whether it was moved on in the frame before this: shots taken while it was not, with another
         *                  weapon or with this one not drawn, are not its own
         * @param shots     ViewModel.Held's count of shots, which counts every firearm's
         */
        void step(int item, boolean carriedOn, int shots, float seconds) {
            if (item != this.item || !carriedOn) {
                this.item = item;
                shotsSeen = shots;
                since = -1f;
            }
            if (shots != shotsSeen) {
                shotsSeen = shots;
                since = 0f;
                turned++;
            } else if (since >= 0f) {
                since += seconds;
            }
        }
    }

    @Override
    void work(ViewCamera.Pose camera, float seconds) {
        ViewModel.Held held = view.viewModel.held;
        if (held == null) {
            return;
        }
        int frame = Pipeline.frame();
        caps.step(held.itemId(), workedIn == frame - 1, held.shots(), seconds);
        workedIn = frame;
        float cocked = cocked(caps.since);
        hammer = -drawn.moves[FALLS] * (1f - cocked);
        // The chamber fired turns away as the hammer comes back, the next one up into line.
        cylinder = EIGHTH * (caps.turned - 1f + cocked);
    }

    @Override
    void parts(Program drawing) {
        part(FIXED);
        drawing.set("uBore", drawn.moves[AXIS_UP]);
        drawing.set("uTurn", (float) Math.cos(cylinder), (float) Math.sin(cylinder));
        part(CYLINDER);
        drawing.set("uTurn", 1f, 0f);
        drawing.set("uBore", drawn.muzzle[2]);
        turnAbout(turn, 0f, drawn.moves[HAMMER_ALONG], drawn.moves[HAMMER_UP], 1f, 0f, 0f, hammer);
        drawing.set("uPart", turn);
        part(HAMMER);
        drawing.set("uPart", Characters.IDENTITY);
    }
}
