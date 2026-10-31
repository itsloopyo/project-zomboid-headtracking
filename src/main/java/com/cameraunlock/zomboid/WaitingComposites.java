package com.cameraunlock.zomboid;

import zombie.core.SpriteRenderer;
import zombie.core.skinnedmodel.model.ModelInstanceTextureCreator;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;
import zombie.core.textures.TextureDraw;

/**
 * What a figure wears, composed into its skin while the render thread waits for the frame, and
 * not in the middle of drawing it.
 *
 * The game composes the skin, the dirt, the blood and the clothes of each character it draws
 * into one texture (ModelInstanceTextureCreator), about a millisecond of the render thread's
 * each. It sets one up on its logic thread (IsoGameCharacter.checkUpdateModelTextures), hands it
 * to the character's model as the model goes into the frame being drawn up
 * (ModelSlotRenderData.initModel), and makes it on the render thread as the frame's draw comes
 * to that model (ModelSlotRenderData.checkReady). The picture gives models to the
 * zombies down the road as they come within its sight (FarModels), a crowd at a time, and a
 * frame that asked for forty composites spent some forty milliseconds of its draw on them.
 *
 * Meanwhile the render thread, done with the frame before, waits for the logic thread to hand
 * the frame over, most of each frame on the drive, and the composites asked for so far are
 * made in that wait. What is left when the frame is handed over is made in its draw, where the
 * game makes it, so a model's composite is made by the time it is drawn, as before. The
 * composites are the game's own, made by its own code from the same steps: only where in the
 * frame the render thread runs them moves. That needs every one of the patches, so with one
 * missing none of it is done. In the game's own view, with no first person picture over it,
 * the game makes them as it always has.
 */
public final class WaitingComposites {
    private WaitingComposites() {}

    private static final CompositeQueue<ModelInstanceTextureCreator> QUEUE = new CompositeQueue<>(ModelInstanceTextureCreator::isRendered,
            ModelInstanceTextureCreator::render, () -> SpriteRenderer.instance.notifyRenderStateQueue());

    /** Whether the patches are in: told once, on the logic thread, as the first composite is asked for. */
    private static volatile boolean started;
    private static boolean patched;

    /** Has on ask again whether the patches are in: Agent.takeIn has put sets in. */
    static void askAgain() {
        started = false;
    }

    /**
     * Called as TextureDraw.drawModel returns, on the logic thread, once a model has gone into the
     * frame being drawn up and its data (the draw's drawer) has taken its character's composite
     * (ModelSlotRenderData.initModel). The rest of the data is filled there or, as the game has it
     * unless a debug option says otherwise (Threading.ModelSlotInit), on a pool of threads of its
     * own, which may still be at it while the render thread makes the composite. What they fill
     * reads nothing a composite writes, and in the game the same threads fill the next frame's
     * models while the draw makes this frame's composites.
     */
    public static void readied(TextureDraw draw) {
        // Set only for a composite the model's character wants and that is not made.
        ModelInstanceTextureCreator creator = ((ModelSlotRenderData) draw.drawer).textureCreator;
        if (creator != null && on()) {
            QUEUE.ask(creator);
        }
    }

    private static boolean on() {
        if (!started) {
            started = true;
            String gap = Agent.waitingCompositesGap();
            patched = gap == null;
            Log.info(patched ? "what characters wear is composed into their skins while the render thread waits for the frame, and what is left as the game does, in the draw"
                    : "what characters wear is composed into their skins as the game does, in the draw: " + gap);
        }
        if (!patched || !Part.SKINS_COMPOSED_WHILE_WAITING.on()) {
            return false;
        }
        for (int i = 0; i < View.COUNT; i++) {
            View view = View.of(i);
            if (view != null && view.firstPerson.coversTheGamesView()) {
                return true;
            }
        }
        return false;
    }

    /** Called as SpriteRenderer.pushFrameDown begins, on the logic thread, to hand the frame over. */
    public static void handing() {
        QUEUE.handing();
    }

    /** Called in SpriteRenderer.pushFrameDown once the frame is handed over (SpriteRendererStates.movePopulatingToReady), under the lock on the frames. */
    public static void handed() {
        QUEUE.handed();
    }

    /** Called as SpriteRenderer.notifyRenderStateQueue begins, on whichever thread wakes the render thread, before it takes the lock. */
    public static void notifying() {
        QUEUE.notifying();
    }

    /** Called in SpriteRenderer.notifyRenderStateQueue once it has woken the render thread, under the lock. */
    public static void woken() {
        QUEUE.woken();
    }

    /**
     * Called as SpriteRenderState.clear begins, on the logic thread, which lets go of a frame's
     * draws: each model's data in it, and the composites only those held go back to the game's pool.
     */
    public static void clearing() {
        QUEUE.lettingGo();
    }

    /** Called as RenderThread.waitForRenderStateCallback begins, which the render thread runs waiting for the frame, under the lock. */
    public static void calling() {
        QUEUE.calling();
    }

    /** Called as RenderThread.waitForRenderStateCallback returns whether the render thread goes on waiting. */
    public static boolean waiting(boolean keepWaiting) throws Exception {
        while (QUEUE.toMake(keepWaiting)) {
            // So the next frame's draw starts from the GL state the last one left, as it does with nothing made between them.
            FineSkins.withCombinerStateKept(() -> {
                QUEUE.make();
                return null;
            });
        }
        return keepWaiting;
    }

    /** Dev only, for the rig: what the waits have come to since this was last asked. Logic thread. */
    static String tolls() {
        return QUEUE.tolls();
    }
}
