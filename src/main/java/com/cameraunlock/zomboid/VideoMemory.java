package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.Locale;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;

/**
 * How much of the graphics card's memory the mod may fill, and what it gives up to stay inside
 * it. The card is asked once, on the render thread before anything of the mod's is made. Where
 * the driver says how much memory the card has (NVIDIA's, GL_NVX_gpu_memory_info), a card too
 * small for what a step was measured to take starts past it (stepFor). Every other card starts
 * at step 0.
 *
 * The answer sets a step: 0 draws every graphics mode as it is, and each step after caps what
 * every mode draws a little more (fit). The step goes down again, for the rest of the session,
 * when the driver has no room for something the mod makes (outOfMemory), and on nothing else.
 * Past the last step there is nothing more to give up.
 *
 * skinBytes, jarSkinBytes and tailoredSkinBytes are the caps FineSkins keeps the skins to at
 * each step.
 *
 * Dev builds only: cameraunlock.zomboid.videoMemoryMb makes the card look that small, and
 * cameraunlock.zomboid.ballastMb fills that much of it before the mod makes anything (DevVideoMemory).
 */
final class VideoMemory {
    private VideoMemory() {}

    /**
     * What the mod takes on the card at each step, in MB, on top of what the game holds when the
     * card is asked: the growth of the game's dedicated video memory from then to its peak, at
     * 2560x1440 (the most pixels a preset draws) in High with 100 zombies and a drive, each step
     * drawn from the start: 2997, 3015, 2155, 1866 and 1277 MB, rounded up by about 100 for what
     * one run takes more than another. Step 1 saves less than that, and is not started at.
     */
    static final int[] NEED_MB = {3100, 3100, 2300, 2000, 1400};
    static final int LAST_STEP = NEED_MB.length - 1;
    /** What the game held on the card when it was asked, measured at 1350 MB and rounded up. */
    static final int GAME_MB = 1400;
    /** What is left on the card for Windows and other programs: an allowance, not measured on players' PCs. */
    static final int OTHERS_MB = 1536;
    /**
     * After a step, a refusal of the driver's is put down to the same shortage, not stepped
     * again for, until what the step gave back has gone: the graphics card lets go of it once
     * the frames drawn with it are done.
     */
    static final long SETTLES_NANOS = 2_000_000_000L;

    /** What outOfMemory did: went down a step, waited on the last step's room, or had nothing left to give up. */
    enum Room { STEPPED, SETTLING, NONE_LEFT }

    /** Thrown by what the driver had no room for, after Pipeline.noted is told: the frame is given up, and the next drawn with less. */
    static final class Refused extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Refused(String what) {
            super(what);
        }
    }

    /**
     * The most a step lets each mode draw. Each is the one before with less: the shadow maps 2048
     * across where they were 4096 and no frame blending, then the mod's pictures at half size, then
     * no more than Medium draws, and without its frame blending (but for the far trees, which Low
     * draws further than Medium), then no more than Low, which draws none of the mod's own content.
     * Step 0 draws every mode as it is, a Custom dearer than High too.
     */
    private static final Quality.Picture[] CEILINGS;

    static {
        Quality.Picture high = Quality.HIGH_PICTURE;
        Quality.Picture coarse = new Quality.Picture(high.stockLook(), high.treeQuality(), high.grassDensity(), high.renderScale(), false,
                high.viewDistance(), high.drawDistance(), high.farTreeDistance(), high.rainOnGlass(), high.shadingAndLight(), high.reflections(),
                high.fullSizeTextures(), high.surfaceDepth(), high.wholeFigureShadows(), false, high.lightDistance(), high.reflectionScale(),
                high.corpseModels());
        Quality.Picture small = new Quality.Picture(coarse.stockLook(), coarse.treeQuality(), coarse.grassDensity(), coarse.renderScale(), false,
                coarse.viewDistance(), coarse.drawDistance(), coarse.farTreeDistance(), coarse.rainOnGlass(), coarse.shadingAndLight(), coarse.reflections(),
                false, coarse.surfaceDepth(), coarse.wholeFigureShadows(), false, coarse.lightDistance(), coarse.reflectionScale(), coarse.corpseModels());
        Quality.Picture medium = Quality.MEDIUM_PICTURE;
        Quality.Picture mediumUnblended = new Quality.Picture(medium.stockLook(), medium.treeQuality(), medium.grassDensity(), medium.renderScale(),
                medium.sharpShadows(), medium.viewDistance(), medium.drawDistance(),
                Math.max(medium.farTreeDistance(), Quality.LOW_PICTURE.farTreeDistance()), medium.rainOnGlass(), medium.shadingAndLight(),
                medium.reflections(), medium.fullSizeTextures(), medium.surfaceDepth(), medium.wholeFigureShadows(), false, medium.lightDistance(),
                medium.reflectionScale(), medium.corpseModels());
        CEILINGS = new Quality.Picture[] {high, coarse, small, mediumUnblended, Quality.LOW_PICTURE};
    }

    private static final String[] GIVEN_UP = {
        "nothing",
        "shadow maps 2048 across, no frame blending",
        "shadow maps 2048 across, no frame blending, the mod's pictures at half size",
        "no more than Medium draws, and no frame blending: shadow maps 2048 across, the mod's pictures at half size, 0.8 of the pixels, glass reflections at half that, the mod's trees near only, "
            + "less grass, sight 60 tiles, no surface depth",
        "no more than Low draws: the game's own look, none of the mod's own content, 0.62 of the pixels, sight 48 tiles",
    };

    /** The skins' caps by step, in MB: what FineSkins composes, what it reads from the jar, what it tailors. */
    private static final int[] SKIN_MB = {512, 512, 256, 192, 128};
    private static final int[] JAR_SKIN_MB = {512, 512, 256, 192, 128};
    private static final int[] TAILORED_SKIN_MB = {384, 384, 192, 128, 96};

    private static final int GL_RENDERER = 0x1F01;
    private static final int GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX = 0x9047;
    private static final int GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX = 0x9049;
    private static final int GL_GPU_MEMORY_INFO_EVICTION_COUNT_NVX = 0x904A;
    private static final int GL_TEXTURE_FREE_MEMORY_ATI = 0x87FC;

    private static final int NONE = 0, NVIDIA = 1, AMD = 2;
    private static int says = NONE;
    private static boolean read;
    private static int cardMb;
    private static volatile int step;
    /** When the step was last taken by a refusal, and whether one has been. */
    private static long steppedAt;
    private static boolean steppedDown;
    private static boolean settlingSaid;
    private static final int[] four = new int[4];

    /** The step a card with this many MB starts at: the first whose need fits beside the game and the allowance for other programs. */
    static int stepFor(int cardMb) {
        for (int at = 0; at < LAST_STEP; at++) {
            if (NEED_MB[at] + GAME_MB + OTHERS_MB <= cardMb) {
                return at;
            }
        }
        return LAST_STEP;
    }

    /**
     * What a mode that would draw picture draws at the step: nothing dearer than the step's ceiling.
     * Corpse models are left as the picture has them: no preset draws them, and a player who sets
     * them in Custom asked for the cost.
     */
    static Quality.Picture fit(Quality.Picture picture, int at) {
        if (at == 0) {
            return picture;
        }
        Quality.Picture most = CEILINGS[at];
        return new Quality.Picture(picture.stockLook() || most.stockLook(), Math.min(picture.treeQuality(), most.treeQuality()),
                Math.min(picture.grassDensity(), most.grassDensity()), Math.min(picture.renderScale(), most.renderScale()),
                picture.sharpShadows() && most.sharpShadows(), Math.min(picture.viewDistance(), most.viewDistance()),
                Math.min(picture.drawDistance(), most.drawDistance()), Math.min(picture.farTreeDistance(), most.farTreeDistance()),
                Math.min(picture.rainOnGlass(), most.rainOnGlass()), picture.shadingAndLight() && most.shadingAndLight(),
                picture.reflections() && most.reflections(), picture.fullSizeTextures() && most.fullSizeTextures(),
                picture.surfaceDepth() && most.surfaceDepth(), picture.wholeFigureShadows() && most.wholeFigureShadows(),
                picture.frameBlending() && most.frameBlending(), Math.min(picture.lightDistance(), most.lightDistance()),
                Math.min(picture.reflectionScale(), most.reflectionScale()), picture.corpseModels());
    }

    static Quality.Picture fit(Quality.Picture picture) {
        return fit(picture, step);
    }

    static int step() {
        return step;
    }

    static long skinBytes(int at) {
        return (long) SKIN_MB[at] << 20;
    }

    static long jarSkinBytes(int at) {
        return (long) JAR_SKIN_MB[at] << 20;
    }

    static long tailoredSkinBytes(int at) {
        return (long) TAILORED_SKIN_MB[at] << 20;
    }

    /** The most video memory the skins FineSkins composes are to take now. */
    static long skinBytes() {
        return skinBytes(step);
    }

    /** The most video memory the skins FineSkins reads from the jar are to take now. */
    static long jarSkinBytes() {
        return jarSkinBytes(step);
    }

    /** The most video memory the skins FineSkins tailors are to take now. */
    static long tailoredSkinBytes() {
        return tailoredSkinBytes(step);
    }

    static String given(int at) {
        return GIVEN_UP[at];
    }

    /** The most a mode draws at a step: see fit. */
    static Quality.Picture ceiling(int at) {
        return CEILINGS[at];
    }

    /** What the screen says once a step is taken: the log has what was given up. */
    static String shown(int at) {
        return "Graphics drawn with less: the graphics card is short of memory (step " + at + " of " + LAST_STEP + ")";
    }

    /** Render thread, once, before the mod makes anything on the card. First person never asks a card below GlFloor, so nor does this. */
    static void read() {
        if (read) {
            return;
        }
        read = true;
        GLCapabilities card = GL.getCapabilities();
        String renderer = GL11.glGetString(GL_RENDERER);
        String how;
        int at = 0;
        if (card.GL_NVX_gpu_memory_info) {
            says = NVIDIA;
            cardMb = GL11.glGetInteger(GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX) / 1024;
            how = String.format(Locale.ROOT, "%d MB on the card, %d MB of it free (GL_NVX_gpu_memory_info)", cardMb, free());
            int pretend = DevVideoMemory.pretendMb();
            if (pretend > 0) {
                cardMb = Math.min(cardMb, pretend);
                how += "; cameraunlock.zomboid.videoMemoryMb makes it look " + cardMb + " MB";
            }
            int ballastMb = DevVideoMemory.ballast();
            if (ballastMb > 0) {
                how += "; cameraunlock.zomboid.ballastMb filled " + ballastMb + " MB of it, " + free() + " MB is free after";
            }
            if (Part.GRAPHICS_MEMORY_STEPS.on()) {
                at = stepFor(cardMb);
                how += String.format(Locale.ROOT, ". A step starts on a card of at least its need, %d MB for the game and %d MB for other programs (%s MB)", GAME_MB,
                        OTHERS_MB, Arrays.toString(NEED_MB));
            } else {
                how += ". GraphicsMemorySteps is off: every mode starts as it is, and steps down only if the driver has no room for what the mod makes";
            }
        } else if (card.GL_ATI_meminfo) {
            says = AMD;
            how = free() + " MB free for textures (GL_ATI_meminfo, which says nothing of the card's size): every mode starts as it is, and steps down only if the"
                    + " driver has no room for what the mod makes";
        } else {
            how = "the driver says nothing of the card's memory (neither GL_NVX_gpu_memory_info nor GL_ATI_meminfo): every mode starts as it is, and"
                    + " steps down only if the driver has no room for what the mod makes";
        }
        int forced = DevVideoMemory.startStep();
        if (forced >= 0) {
            at = forced;
            how += "; cameraunlock.zomboid.videoMemoryStep starts it at step " + forced;
        }
        Log.info("video memory: " + renderer + ", " + how + ". Graphics modes are drawn with " + (at == 0 ? "nothing given up" : "step " + at + " of "
                + LAST_STEP + " given up: " + GIVEN_UP[at]));
        if (at > 0) {
            stepTo(at);
        }
    }

    private static int free() {
        if (says == NVIDIA) {
            return GL11.glGetInteger(GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX) / 1024;
        }
        GL11.glGetIntegerv(GL_TEXTURE_FREE_MEMORY_ATI, four);
        return four[0] / 1024;
    }

    /** What was read, for a log line. */
    static String described() {
        return (says == NVIDIA ? cardMb + " MB of video memory" : "the card's size unknown") + ", step " + step + " of " + LAST_STEP;
    }

    /**
     * Render thread: the driver had no room for what was being made. Steps down, and logs what
     * was given up; or, within SETTLES_NANOS of the last step, puts it down to the shortage that
     * step was for; or, at the last step, has nothing left to give up.
     */
    static Room outOfMemory(String what) {
        long now = System.nanoTime();
        long since = steppedDown ? now - steppedAt : Long.MAX_VALUE;
        Room room = room(step, since);
        if (room == Room.SETTLING && !settlingSaid) {
            settlingSaid = true;
            Log.info(String.format(Locale.ROOT, "video memory: the driver had no room for %s either, %.0f ms after the step: what the step gave back is"
                    + " given time to go, and nothing more is given up for this", what, since / 1e6));
        }
        if (room != Room.STEPPED) {
            return room;
        }
        stepTo(step + 1);
        steppedAt = now;
        steppedDown = true;
        settlingSaid = false;
        Log.info("video memory: the driver had no room for " + what
                + (says == NVIDIA ? " (" + free() + " MB free, " + GL11.glGetInteger(GL_GPU_MEMORY_INFO_EVICTION_COUNT_NVX) + " evictions so far)" : "")
                + ". For the rest of the session every graphics mode is drawn with step " + step + " of " + LAST_STEP + " given up: " + GIVEN_UP[step]);
        return room;
    }

    /** What a refusal does at the step, so many nanoseconds after the last refusal that took a step. */
    static Room room(int at, long sinceStepNanos) {
        return sinceStepNanos < SETTLES_NANOS ? Room.SETTLING : at == LAST_STEP ? Room.NONE_LEFT : Room.STEPPED;
    }

    private static void stepTo(int at) {
        step = at;
        Quality.memoryStepped();
    }
}
