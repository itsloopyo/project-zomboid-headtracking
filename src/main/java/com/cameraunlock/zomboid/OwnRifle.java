package com.cameraunlock.zomboid;

/**
 * The mod's own model of the hunting rifle, drawn in the viewer's hands in place of the
 * game's: a mesh, its colour and the shape and finish of its surface, made by
 * tools/models/rifle.py and kept in the jar, in the layout the shotgun's files have (OwnGun
 * says how it is held, lit and drawn).
 *
 * After a shot its bolt is worked: the handle turned up, the bolt drawn back, the fired case
 * thrown out of the port, and the bolt shut again.
 *
 * All of it is the render thread's.
 */
final class OwnRifle extends OwnGun {
    /** The parts the file's layout names. The rifle has nothing in the second, which is a pump's, or the last, which is a stock left out. */
    private static final int FIXED = 0, BOLT = 2, CASE = 3;
    /** The part that slides back toward the eye by the file's travel as the action is worked: its bolt. */
    static final int SLIDES = BOLT;
    static final int PARTS = 5, MOVES = 0;

    /**
     * How far the handle is turned up to open the bolt, as tools/models/rifle.py makes it, and
     * how long each part of working it takes, in seconds: the handle up, the bolt back, a
     * moment there, the bolt forward, the handle down.
     */
    private static final float LIFT = (float) Math.toRadians(55);
    private static final float UP = 0.13f, BACK = 0.17f, HELD_BACK = 0.05f, FORWARD = 0.17f, DOWN = 0.13f;
    /** How long working the bolt takes from the handle's first turn to its last. */
    static final float WORKED_FOR = UP + BACK + HELD_BACK + FORWARD + DOWN;
    /** The bolt throws the case out once it is this far back, on its way back. */
    private static final float THROWN_AT = 0.85f;

    private static final float NOT_WORKING = -1f;
    /** Fired cases: thrown out to the right, up and a little back, and tumbling fast. */
    private final Thrown cases = new Thrown(2, 0.9f, 1.3f, 0.9f, 0.3f, 7.5f, 21f);
    private final float[] bolt = new float[2];
    private boolean caseThrown;

    OwnRifle(View view) {
        // Oiled wood seen along its length does not turn into a mirror of the sky, and with the
        // sights up the head over the comb shades what faces the eye from close by.
        super(view, 1f, 0.30f, 1f);
    }

    /** How far through working the bolt a time after it began is: the handle's turn, 0 to 1, and the bolt's draw. */
    static void worked(float seconds, float[] out) {
        out[0] = ViewModelMath.smooth(seconds / UP) - ViewModelMath.smooth((seconds - UP - BACK - HELD_BACK - FORWARD) / DOWN);
        out[1] = ViewModelMath.smooth((seconds - UP) / BACK) - ViewModelMath.smooth((seconds - UP - BACK - HELD_BACK) / FORWARD);
    }

    @Override
    void work(ViewCamera.Pose camera, float seconds) {
        // The bolt is worked a moment after the shot, by the clock the hand that works it goes by:
        // the clock of the frames counts no more than a twentieth of a second a frame, so on long
        // frames it runs slow, and by it the hand would leave an open bolt.
        ViewModel.Held held = view.viewModel.held;
        float working = held == null || held.cycled() < 0f ? NOT_WORKING : held.cycled() - ArmActs.BOLT_AFTER;
        if (working < 0f || working > WORKED_FOR) {
            bolt[0] = 0f;
            bolt[1] = 0f;
        } else {
            worked(working, bolt);
        }
        if (working < 0f) {
            caseThrown = false;
        }
        // Once each time it is worked, as the bolt comes right back, however few frames show it there.
        boolean throwing = !caseThrown && working >= 0f && (bolt[1] >= THROWN_AT || working > UP + BACK);
        caseThrown |= throwing;
        if (throwing) {
            cases.out(camera, model, drawn.port[0], drawn.port[1], drawn.port[2]);
        }
        cases.fly(camera, seconds);
    }

    @Override
    void parts(Program drawing) {
        part(FIXED);
        // The handle is on the model's left, which held up is the viewer's right, and rises as it is turned this way.
        float turn = -LIFT * bolt[0];
        drawing.set("uTurn", (float) Math.cos(turn), (float) Math.sin(turn));
        drawing.set("uSlide", bolt[1] * drawn.travel);
        part(BOLT);
        drawing.set("uTurn", 1f, 0f);
        drawing.set("uSlide", 0f);
        cases.draw(this, drawing, CASE);
    }
}
