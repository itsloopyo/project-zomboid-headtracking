package com.cameraunlock.zomboid;

/**
 * The mod's own models of the pump action shotgun and of the same gun sawn off, drawn in the
 * viewer's hands in place of the game's: a mesh, its colour and the shape and finish of its
 * surface, made by tools/models/shotgun.py and kept in the jar (OwnGun says how each is held,
 * lit and drawn).
 *
 * The fore-end slides back with the hand that works it, the bolt goes with it, and as the bolt
 * comes back the spent shell is thrown out of the port and falls away.
 *
 * All of it is the render thread's.
 */
final class OwnShotgun extends OwnGun {
    /** The parts the file's layout names, and how many it has. */
    private static final int FIXED = 0, SHELL = 3, STOCK = 4;
    static final int PARTS = 5, MOVES = 0;
    /** How far from the eye the stock is first drawn with the sights up, in the model's metres. */
    private static final float NOT_DRAWN_WITHIN = 0.13f;

    /** Spent shells: thrown out to the right, up and a little back, and tumbling fast. */
    private final Thrown shells = new Thrown(3, 0.9f, 1.5f, 1.1f, 0.35f, 7.5f, 17f);
    private float cut, pumpedBefore;

    OwnShotgun(View view) {
        // Varnish and rubber mirror the sky far less cleanly than the sums for a perfect coat say: taken whole, walnut under a blue sky was purple.
        super(view, 0.45f, 0f, 0f);
    }

    @Override
    void placed(float aim) {
        // With the sights up the butt of the stock is at the eye, where the shoulder it is held
        // against would be, and seen from there its end would fill the foot of the picture. As
        // it comes up it is left out for a hand's length from the eye, and the stock is seen
        // from there on: its comb, the wrist the right hand is on, the back of the receiver.
        cut = ViewModelMath.smooth(aim) * NOT_DRAWN_WITHIN * metre();
    }

    @Override
    void work(ViewCamera.Pose camera, float seconds) {
        float pumped = view.viewModel.pumpedPlaced;
        if (PumpStroke.throwsShell(pumpedBefore, pumped)) {
            shells.out(camera, model, drawn.port[0], drawn.port[1] - pumped * drawn.travel * 0.5f, drawn.port[2]);
        }
        pumpedBefore = pumped;
        shells.fly(camera, seconds);
    }

    @Override
    void parts(Program drawing) {
        for (int part = 0; part < SHELL; part++) {
            drawing.set("uSlide", part == FIXED ? 0f : view.viewModel.pumpedPlaced * drawn.travel);
            part(part);
        }
        drawing.set("uSlide", 0f);
        drawing.set("uCut", cut);
        part(STOCK);
        drawing.set("uCut", 0f);
        shells.draw(this, drawing, SHELL);
    }
}
