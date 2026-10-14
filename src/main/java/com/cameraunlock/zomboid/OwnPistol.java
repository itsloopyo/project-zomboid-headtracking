package com.cameraunlock.zomboid;

/**
 * The mod's own models of the game's three pistols, drawn in the viewer's hands in place of the
 * game's: each a mesh, its colour and the shape and finish of its surface, made by
 * tools/models/pistol.py and kept in the jar, in the layout the long guns' files have (OwnGun
 * says how they are held, lit and drawn).
 *
 * Each shot drives the slide back and lets it run forward again, and as it goes back the fired
 * case is thrown out of the port. A shot that leaves the pistol with nothing in its chamber and
 * nothing in its magazine leaves the slide held back, as the empty magazine holds it, until a
 * round is chambered or the slide is racked. The magazine is drawn in its well while the game
 * has one in the pistol and the hand is not carrying it in or out.
 *
 * All of it is the render thread's.
 */
final class OwnPistol extends OwnGun {
    /** The parts the file's layout names. A pistol has nothing in the second, which is a pump's. */
    static final int PARTS = 5, MOVES = 0;
    private static final int FIXED = 0, SLIDE = 2, CASE = 3, MAGAZINE = 4;

    /** Fired cases: thrown out to the right and up, a little back, and tumbling fast. */
    private final Thrown cases = new Thrown(4, 0.8f, 1.6f, 1.3f, 0.2f, 7.5f, 26f);
    private final SelfLoading.Slide action = new SelfLoading.Slide();
    private boolean magazineIn;

    OwnPistol(View view) {
        // Plastic, rubber and oiled walnut grips seen along their length do not turn into mirrors of the sky either.
        super(view, 0.45f, 0.30f, 0f);
    }

    @Override
    void work(ViewCamera.Pose camera, float seconds) {
        ViewModel.Held held = view.viewModel.held;
        if (held != null) {
            action.step(held, seconds);
            if (action.thrown) {
                cases.out(camera, model, drawn.port[0], drawn.port[1] - action.back * drawn.travel * 0.5f, drawn.port[2]);
            }
            magazineIn = SelfLoading.magazineIn(held.clip(), held.doing(), held.seated());
        }
        cases.fly(camera, seconds);
    }

    @Override
    void parts(Program drawing) {
        part(FIXED);
        drawing.set("uSlide", action.back * drawn.travel);
        part(SLIDE);
        drawing.set("uSlide", 0f);
        if (magazineIn) {
            part(MAGAZINE);
        }
        cases.draw(this, drawing, CASE);
    }
}
