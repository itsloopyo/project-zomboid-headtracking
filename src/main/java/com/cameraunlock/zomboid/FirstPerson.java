package com.cameraunlock.zomboid;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import zombie.GameTime;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.core.opengl.RenderThread;
import zombie.input.GameKeyboard;
import zombie.input.Mouse;
import zombie.iso.IsoCamera;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoUtils;
import zombie.iso.IsoWorld;
import zombie.iso.Vector2;
import zombie.ui.TextManager;
import zombie.ui.UIFont;
import zombie.vehicles.BaseVehicle;

/**
 * The first-person view: whether it is on, where it looks, and the places
 * it meets the game.
 *
 * Switching between the two views flies the camera from the one to the other:
 * see Flight. The view is played in once the camera has landed at the eye, and
 * until then the game is played as it is in its own view.
 *
 * The game keeps running exactly as it does in its own view. Its world render
 * still runs every frame, so everything that render keeps up to date (what has
 * been seen, what the cursor is over) stays as it was, and the first-person
 * picture is drawn over the result. The mouse turns the view; the game's cursor
 * is held on the ground ahead of the character along the view, so the game's
 * own aim, which runs from the character to the cursor, is the view direction.
 * The movement keys are turned so that forward is where the view faces. Head
 * tracking turns and moves the picture alone, and the game's reticle is drawn
 * where the aim meets the world in that picture.
 *
 * Everything here runs on the game's logic thread, but for coversTheGamesView,
 * which the render thread asks as well.
 */
public final class FirstPerson {
    /** How long the name of a graphics mode, picture style or aim mode just gone to stays on the screen. */
    private static final long SAID_FOR_NANOS = 2_500_000_000L;
    private static long saidAt;
    private static String said;
    /** How far off the overhead camera stands, in tiles: far enough that the picture has next to no perspective. */
    private static final float OVERHEAD_AWAY = 6000f;
    private static final float OVERHEAD_YAW = (float) (-0.75 * Math.PI), OVERHEAD_PITCH = (float) Math.toRadians(-30);
    private static boolean overhead;
    private static int overheadSaid;
    private static final ViewCamera.Pose overheadView = new ViewCamera.Pose(), standingView = new ViewCamera.Pose();
    /** What the overhead camera last took in above and below the place it looks at, in tiles. */
    private static float overheadHalfHigh;
    private static final ViewCamera.Pose flightView = new ViewCamera.Pose();
    /**
     * How near the eye the camera is when the viewer stops being one of the people in the
     * picture and becomes the one it is seen by: the distance their own head is cut away within.
     */
    private static final float OWN_FROM = 0.3f;
    /** The name the game stores its inventory key under. */
    private static final String INVENTORY_BINDING = "Toggle Inventory";
    private static final int ESCAPE_KEY = 1;
    /**
     * The middle mouse button, which brings the pointer out for as long as it is held. Nothing
     * in the game's default bindings uses it, and every letter key is taken.
     */
    private static final int POINT_BUTTON = 2;
    private static final int AIM_BUTTON = 1;
    /** How long after the aim button is let go the game may still be opening what it opens on a right click. */
    private static final long AFTER_AIM_NANOS = 400_000_000L;
    private static long aimedAt = Long.MIN_VALUE / 2;

    /** How far a pixel of mouse travel turns the view unzoomed. Zoomed it turns the view less, to move the picture as far. */
    private static final float RADIANS_PER_PIXEL = 0.0025f;
    /** A frame longer than this is a hitch, and taking it whole would snap the zoom to its target. */
    private static final float MAX_FRAME_SECONDS = 0.25f;
    private static final float PITCH_LIMIT = (float) Math.toRadians(89);
    /** How far ahead of the character the game's cursor is held, in tiles. */
    private static final float CURSOR_DISTANCE = 3f;
    /** The direction the forward key moves the character in the game's own view: up the screen. */
    private static final float STOCK_FORWARD_YAW = (float) Math.toRadians(-135);
    /** The movement keys ask for a way that is not ahead of the view: sideways or back. */
    private static boolean movingAside;

    /** Whether the character is being walked sideways or backwards from the way the view faces. */
    static boolean movingAside() {
        return movingAside && lookCaptured();
    }

    private static final float EYE_HEIGHT_STANDING = 1.6f;
    private static final float EYE_HEIGHT_SNEAKING = 1.1f;
    private static final float EYE_HEIGHT_SITTING = 0.9f;
    /** The eye goes from one of those heights to another over a few of these, where set at once the whole picture cut half a tile in a frame. */
    private static final float EYE_HEIGHT_SECONDS = 0.1f;
    private static float eyeHigh;
    private static final float[] eye = new float[3];
    static final boolean TRACE = Boolean.getBoolean("cameraunlock.zomboid.trace");
    private static final long TRACE_INTERVAL_NANOS = 1_000_000_000L;

    /** The view the player has asked for. The camera may still be on its way there. */
    private static volatile boolean on;
    /** The camera is at the eye, and the view is played in. */
    private static volatile boolean landed;
    /** The last frame was drawn from the eye or as near it as makes no difference: the viewer's own body was drawn as their own. */
    private static boolean fromTheEye;
    private static boolean wasRiding;
    /** The way the vehicle the player is in faced on the last frame. */
    private static float rideHeading;
    /** The view is played in, the game is running and the character is alive. */
    private static boolean active;
    private static boolean lookCaptured;
    /** Whether a frame of the picture has been queued since its targets were last given back. */
    private static boolean pictureUp;
    private static final PointerMode pointer = new PointerMode();
    /**
     * Leaving for the main menu stops the frames with the look still marked as captured, and
     * the game goes on reading its keys there.
     */
    private static final long CAPTURE_LASTS_NANOS = 500_000_000L;
    private static long lastFrameNanos;
    private static float yaw;
    private static float pitch;

    /**
     * How the picture is keeping up, said whoever is playing: a report of a slow game is of no
     * use without the frame time, the memory in use and the size of the scene. Once soon after
     * the view comes on and then once a minute, so that an evening's play leaves a log the
     * other lines can still be found in.
     */
    private static final long PACE_FIRST_NANOS = 5_000_000_000L;
    private static final long PACE_EVERY_NANOS = TRACE ? PACE_FIRST_NANOS : 60_000_000_000L;
    private static long paceWait = PACE_FIRST_NANOS;
    private static long paceSince, paceWorst;
    private static int paceFrames;
    /** With tracing on, the memory the logic thread took for the picture since the last pace line. */
    private static long paceBuiltBytes;
    private static WorldScene paceScene;
    /** A frame this long is a hitch the player feels: the log says what the frame before it built and drew. */
    private static final long LONG_FRAME_NANOS = 100_000_000L;
    private static boolean haveRawX, haveRawY;
    private static int lastRawX, lastRawY;
    private static int cursorX, cursorY;

    /** Where the reticle is drawn when the aim point is not in the picture: off the screen. */
    private static final int RETICLE_HIDDEN = -100_000;

    private static final ViewCamera.Pose cleanView = new ViewCamera.Pose();
    private static final ViewCamera.Pose trackedView = new ViewCamera.Pose();
    /**
     * The aim: the clean view from where the shot starts, which is the clean eye moved by
     * whatever of a lean the weapon has. The weapon is drawn in this view and the shot is
     * traced from it.
     */
    private static final ViewCamera.Pose aimView = new ViewCamera.Pose();
    private static final WorldQuery world = new WorldQuery();
    private static final float[] ndc = new float[2];
    private static int reticleX, reticleY;
    private static float shotDistance;

    private static final Vector2 forward = new Vector2();
    private static final Stride stride = new Stride();
    /** A read of the mouse this soon after the look takes it may be of a cursor the window has just moved, not of the hand. */
    private static final long LOOK_SETTLES_NANOS = 150_000_000L;
    private static long capturedNanos;
    private static boolean wasCaptured;
    /** For the line said a second after a landing: when it was, and what the mouse has turned the view by since, in all and the most in one read. */
    private static long landedNanos;
    private static boolean landingSaid = true;
    private static float mouseTurned;
    private static int mouseMost;
    private static final Vector2 drawn = new Vector2();
    private static final Vector2 traceAim = new Vector2();
    private static float traceMoveX, traceMoveY;
    private static long lastTrace;

    private FirstPerson() {}

    static boolean isOn() {
        return on;
    }

    /** Whether the viewer's own body is being drawn as it is seen from their own eye. */
    static boolean seenFromTheEye() {
        return fromTheEye;
    }

    /** Whether the picture is drawing the game's own view, with the camera at rest there. */
    static boolean overhead() {
        return overhead && !on && Flight.home();
    }

    /** The clean view direction: where the mouse has turned the view, with nothing else mixed in. */
    static float yaw() {
        return yaw;
    }

    static float pitch() {
        return pitch;
    }

    /** Whether the mouse is turning the view, for what the game asks between frames. */
    static boolean lookCaptured() {
        return lookCaptured && System.nanoTime() - lastFrameNanos < CAPTURE_LASTS_NANOS;
    }

    /** Whether the first person view is being played in, with the mouse turning the view or out as a pointer. */
    static boolean active() {
        return active && System.nanoTime() - lastFrameNanos < CAPTURE_LASTS_NANOS;
    }

    static void setLook(float newYaw, float newPitch) {
        // Turning one way all session would otherwise grow the yaw until a float no longer
        // holds a pixel's worth of turn.
        yaw = (float) Math.IEEEremainder(newYaw, 2 * Math.PI);
        pitch = Math.clamp(newPitch, -PITCH_LIMIT, PITCH_LIMIT);
    }

    /**
     * First person has been asked for and what it is drawn with is being made. Nothing of the
     * picture is made before the first time it is asked for: a player who never asks carries
     * none of it, and it is some hundreds of megabytes on the graphics card and in this process.
     * Made a piece a frame it takes a second or so, where made on the frame of the switch it
     * held that frame for seconds.
     */
    private static boolean gettingReady;
    private static long readySince;
    /** Whether the view holds anything on the graphics card that letting go would give back, and when it was last in use. */
    private static boolean held;
    private static long offSince;
    private static final long LET_GO_AFTER_NANOS = 60_000_000_000L;
    /** A PC on which the making and the rehearsal take longer than this goes on to first person without the rest of them. */
    private static final long READY_WITHIN_NANOS = 4_000_000_000L;

    static void toggle() {
        IsoPlayer player = IsoPlayer.getInstance();
        if (!on && !allowed(player)) {
            Log.info("first person stays off: " + whyNot(player));
            return;
        }
        if (gettingReady) {
            gettingReady = false;
            Log.info("first person no longer asked for");
            return;
        }
        // With the picture already drawing the game's own view there is nothing left to make and the world is built.
        if (!on && (Pipeline.unprepared() || Pipeline.contentWaited() || !rehearsed && !overhead())) {
            gettingReady = true;
            readySince = System.nanoTime();
            Log.info("first person asked for: what it is drawn with is being made");
            return;
        }
        switchView(player);
    }

    /** One frame of getting ready: the next piece of what the picture is drawn with, then the world built once unseen, then the switch. */
    private static void getReady(IsoPlayer player, long now) {
        boolean late = now - readySince > READY_WITHIN_NANOS;
        if (!late && (Pipeline.unprepared() || Pipeline.contentWaited())) {
            SpriteRenderer.instance.drawGeneric(Pipeline.PREPARE);
            return;
        }
        if (!late && !rehearsed && !Characters.wanted && !GameTime.isGamePaused() && !player.isDead()) {
            rehearse(player);
            return;
        }
        gettingReady = false;
        switchView(player);
    }

    /**
     * Gives back what the view holds that a view switched off has no use for: the world as it
     * was built, the graphics card's copy of it and the mod's own pictures. The next switch to
     * first person gets ready again, as the first of the session did. The caller hands
     * Pipeline.LET_GO to the render thread, which gives back the graphics card's share.
     */
    private static void letGo() {
        held = false;
        rehearsed = false;
        world.look(null, 0);
        WorldScene.letGo();
    }

    /**
     * Called as the game leaves a save for its menu. No frame of the save comes after this, so
     * nothing would come to give anything back, and the scenes would go on naming the map the
     * game has let go of. Nor would anything come to let go of the mouse: a save can be left with
     * the look still holding it, by a server that stops or drops the player.
     */
    public static void leftSave(Object state) {
        try {
            on = false;
            gettingReady = false;
            outOfSave = true;
            landed = false;
            active = false;
            lookCaptured = false;
            wasCaptured = false;
            fromTheEye = false;
            Characters.wanted = false;
            MouseGrab.wanted = false;
            MouseGrab.kept = false;
            // No frame is open here, and the menu's first frame clears whatever the sprite renderer
            // was handed since the last one: these go by the render thread's own queue.
            if (MouseGrab.applied || MouseGrab.keeping) {
                RenderThread.queueInvokeOnRenderContext(MouseGrab.DRAWER::render);
            }
            HeadTracking.rest();
            ViewModel.rest();
            Crosshair.hide();
            NameTags.lookAway();
            Flight.cut(false);
            if (pictureUp) {
                pictureUp = false;
                RenderThread.queueInvokeOnRenderContext(Pipeline.RELEASE::render);
            }
            if (held) {
                letGo();
                RenderThread.queueInvokeOnRenderContext(Pipeline.LET_GO::render);
                Log.info("the save was left: first person has given back what it held");
            }
        } catch (RuntimeException | LinkageError thrown) {
            // Thrown on, it would stop the game on its way out of the save.
            Log.error("first person could not give back what it held as the save was left", thrown);
        }
    }

    private static void switchView(IsoPlayer player) {
        on = !on;
        if (on && player.getVehicle() != null) {
            setLook(Ride.heading(player.getVehicle()), 0f);
        } else if (on) {
            Vector2 facing = player.getForwardDirection();
            setLook((float) Math.atan2(facing.y, facing.x), 0f);
            stride.begin(traceMoveX, traceMoveY, yaw);
        }
        Log.info("first person " + (on ? "on, " + facings(player) : "off"));
    }

    /** The ways the game has of saying which way a character faces, in degrees on the map: a landing that looks wrong is read off these. */
    private static String facings(IsoPlayer player) {
        Vector2 facing = player.getForwardDirection();
        player.getAnimForwardDirection(drawn);
        return String.format(Locale.ROOT, "character facing %.0f and drawn facing %.0f",
                Math.toDegrees(Math.atan2(facing.y, facing.x)), Math.toDegrees(Math.atan2(drawn.y, drawn.x)));
    }

    private static boolean allowed(IsoPlayer player) {
        return whyNot(player) == null;
    }

    private static String whyNot(IsoPlayer player) {
        if (player == null) {
            return "no player";
        }
        if (IsoPlayer.numPlayers != 1) {
            return "split screen";
        }
        String gap = Agent.firstPersonGap();
        if (gap == null) {
            gap = Panels.gap();
        }
        if (gap == null) {
            gap = CentreUse.gap();
        }
        if (gap == null) {
            gap = failure;
        }
        return gap != null ? gap : Pipeline.failure();
    }

    /** Called from IngameState.renderFrameInternal straight after the game has drawn its world. */
    public static void afterWorldRender() {
        long before = TRACE ? Allocation.byThisThread() : 0L;
        try {
            frame();
            // After the picture, which would cover it.
            String shown = gettingReady ? "First person: getting ready" : said != null && System.nanoTime() - saidAt < SAID_FOR_NANOS ? said : null;
            IsoPlayer player = IsoPlayer.getInstance();
            if (shown != null && player != null) {
                int index = player.getIndex();
                TextManager.instance.DrawStringCentre(UIFont.Medium, IsoCamera.getScreenWidth(index) / 2.0, IsoCamera.getScreenHeight(index) * 0.12,
                        shown, 1.0, 1.0, 1.0, 1.0);
            }
        } catch (RuntimeException | LinkageError thrown) {
            // Thrown on into the game's frame it comes again every frame, and the game's own
            // picture and panels pay for it. The view goes off for the session instead, the
            // next frame lets go of the mouse, and the log has why. The frame is still run
            // for its keys and for what it gives back, and what it throws from then on is
            // written the first time it comes from each place.
            StackTraceElement[] trace = thrown.getStackTrace();
            if (thrownAt.add(thrown.getClass().getName() + (trace.length == 0 ? "" : " at " + trace[0]))) {
                Log.error(failure == null ? "first person off: the frame could not be built"
                        : "first person is off, and its frame has thrown from another place", thrown);
            }
            if (failure == null) {
                failure = "building the picture threw " + thrown;
            }
            on = false;
            overhead = false;
            Flight.cut(false);
        }
        if (TRACE) {
            paceBuiltBytes += Allocation.byThisThread() - before;
        }
    }

    /** Why the picture can no longer be built this session, or null. */
    private static String failure;
    private static final Set<String> thrownAt = new HashSet<>();
    /**
     * No frame of a save has run since the game started or the last save was left. The hotkeys
     * are listened for all the while, in the menus too, and what was pressed there is not acted on.
     */
    private static boolean outOfSave = true;

    private static void frame() {
        int pressed = 0;
        // The hotkeys are heard whatever the game is doing with the keyboard, so a key that goes into one of its text boxes is not an action as well.
        if (outOfSave || Core.getInstance().isDoingTextEntry()) {
            outOfSave = false;
            HeadTracking.dropHotkeys();
        } else {
            pressed = HeadTracking.pollHotkeys();
        }
        if ((pressed & Config.FIRST_PERSON) != 0) {
            toggle();
        }
        if ((pressed & Config.AIM_MODE) != 0) {
            said = HeadTracking.aimModeLabel();
            saidAt = System.nanoTime();
        }
        DevKeys.poll();
        if ((pressed & Config.CYCLE_QUALITY) != 0) {
            HeadTracking.cycleGraphicsMode();
            said = "Graphics: " + Quality.name();
            saidAt = System.nanoTime();
        }
        if ((pressed & Config.CYCLE_STYLE) != 0) {
            HeadTracking.cycleStyle();
            said = "Style: " + Style.current().label;
            saidAt = System.nanoTime();
        }
        // The picture draws the game's own view once what it is drawn with is made: until then that view is the game's.
        boolean overheadAsked = !Quality.plain() && failure == null && allowed(IsoPlayer.getInstance());
        overhead = overheadAsked && !Pipeline.unprepared() && !Pipeline.contentWaited();
        if (Terrain.level != overhead()) {
            Terrain.level(overhead());
            WorldScene.forgetAll();
        }
        IsoPlayer player = IsoPlayer.getInstance();
        rideAlong(player);
        if ((on || !Flight.home()) && !allowed(player)) {
            if (on) {
                Log.info("first person off: " + whyNot(player));
            }
            on = false;
            Flight.cut(false);
        }
        long now = System.nanoTime();
        long frameNanos = now - lastFrameNanos;
        float sinceLastFrame = Math.min(frameNanos / 1e9f, MAX_FRAME_SECONDS);
        notePace(now, frameNanos);
        lastFrameNanos = now;
        Flight.advance(on, sinceLastFrame, !overhead);
        boolean wasLanded = landed;
        landed = on && Flight.landed();
        if (landed && !wasLanded) {
            landedNanos = now;
            landingSaid = false;
            mouseTurned = 0f;
            mouseMost = 0;
            Log.info(String.format(Locale.ROOT, "first person landed looking %.0f, %s, movement keys (%.2f, %.2f), %s",
                    Math.toDegrees(yaw), facings(player), traceMoveX, traceMoveY, HeadTracking.describe()));
        }
        if (landed && !wasLanded) {
            stride.landed(now);
        } else if (!on) {
            stride.reset();
        }
        active = landed && !GameTime.isGamePaused() && !player.isDead();
        if (!landed) {
            pointer.reset();
        } else if (active) {
            point();
        }
        lookCaptured = active && !pointer.on();
        if (lookCaptured && !wasCaptured) {
            capturedNanos = now;
        }
        wasCaptured = lookCaptured;
        if (!landed) {
            landingSaid = true;
        } else if (!landingSaid && now - landedNanos > 1_000_000_000L) {
            landingSaid = true;
            Log.info(String.format(Locale.ROOT, "a second after landing: looking %.0f, %s, the mouse turned the view %.1f degrees in all and %d pixels at most in one read",
                    Math.toDegrees(yaw), facings(player), Math.toDegrees(mouseTurned), mouseMost));
        }
        boolean flown = on || !Flight.home();
        Characters.wanted = flown || overhead && allowed(player) && !player.isDead();
        MouseGrab.wanted = lookCaptured;
        MouseGrab.kept = active && pointer.on();
        ModelHead.sayGap();
        NameTags.sayGap();
        fromTheEye = landed;
        if (!flown) {
            FieldOfView.rest();
            HeadTracking.follow(player != null && !GameTime.isGamePaused() && !player.isDead());
            ViewModel.rest();
            Crosshair.hide();
            NameTags.lookAway();
            if (MouseGrab.applied || MouseGrab.keeping) {
                SpriteRenderer.instance.drawGeneric(MouseGrab.DRAWER);
            }
            if (gettingReady && !allowed(player)) {
                gettingReady = false;
            } else if (gettingReady) {
                getReady(player, now);
            }
            if (Characters.wanted) {
                drawOverhead(player);
            } else if (pictureUp) {
                pictureUp = false;
                WorldScene.setDown();
                world.look(null, 0);
                SpriteRenderer.instance.drawGeneric(Pipeline.RELEASE);
            }
            // Switched off and on again within the minute, the view is there at once. Left off, it gives back
            // what it holds: the world on the graphics card and the mod's own pictures, which are most of it.
            // While the picture is drawing the game's own view it makes its own pieces, one a frame.
            boolean making = !gettingReady && !Characters.wanted && (overheadAsked && !overhead || Pipeline.contentHalfMade());
            if (making) {
                SpriteRenderer.instance.drawGeneric(Pipeline.PREPARE);
            }
            if (Characters.wanted || gettingReady || making) {
                held = true;
                offSince = now;
            } else if (held && now - offSince > LET_GO_AFTER_NANOS) {
                letGo();
                SpriteRenderer.instance.drawGeneric(Pipeline.LET_GO);
            }
            return;
        }

        float zoom = Core.getInstance().getZoom(player.getIndex());
        holdTheCursorAhead(player, zoom);
        float floorUp = player.getZ() * ViewCamera.LEVEL_HEIGHT + Terrain.lift(player.getX(), player.getY(), player.getZ());
        eye[0] = player.getX();
        eye[1] = player.getY();
        // Eased from where the last frame at the eye had it. The first of them has none before it.
        eyeHigh = landed && wasLanded ? ViewModelMath.ease(eyeHigh, eyeHeight(player), sinceLastFrame, EYE_HEIGHT_SECONDS) : eyeHeight(player);
        eye[2] = floorUp + eyeHigh;
        Headlights.gather(eye[0], eye[1]);
        BaseVehicle vehicle = player.getVehicle();
        Ride.note(player, vehicle, floorUp, eye);
        boolean sightsUp = Controls.sightsUp(player);
        FieldOfView.ease(sightsUp, sinceLastFrame);
        ViewModel.update(player, yaw, pitch, sinceLastFrame, sightsUp, shotDistance);
        ViewCamera.clean(cleanView, eye[0], eye[1], eye[2], yaw, pitch);
        world.look(IsoWorld.instance.currentCell, (int) Math.floor(player.getZ()));
        if (lookCaptured && vehicle == null) {
            CentreUse.mark(cleanView, floorUp, player, IsoWorld.instance.currentCell);
        } else {
            CentreUse.marked = null;
        }
        // The head is in the view the camera flies to and from, so it lands on what is drawn next.
        HeadTracking.view(!GameTime.isGamePaused() && !player.isDead(), cleanView, yaw, world, sightsUp, vehicle == null, trackedView);
        // The game's beam has the clean view's heading turned by the head's share, and no height: it is given the view's.
        double beamHeading = Math.atan2(cleanView.forwardY, cleanView.forwardX) + HeadFollowLight.turn();
        float beamFlat = (float) Math.sqrt(Math.max(1f - trackedView.forwardUp * trackedView.forwardUp, 0f));
        CarriedLight.gather(player, eye[0], eye[1], eye[2], (float) Math.cos(beamHeading) * beamFlat, (float) Math.sin(beamHeading) * beamFlat, trackedView.forwardUp);
        float[] carried = HeadTracking.carried();
        aimView.set(cleanView);
        aimView.eyeX += carried[0];
        aimView.eyeY += carried[1];
        aimView.eyeUp += carried[2];
        held = true;
        offSince = now;
        rehearsed = true;
        WorldScene scene = WorldScene.next();
        if (landed) {
            scene.build(player, trackedView, aimView, FieldOfView.tanHalfVertical(), FieldOfView.setTanHalfVertical(), null, false);
        } else {
            fly(player, scene, floorUp);
        }
        paceScene = scene;
        SpriteRenderer.instance.drawGeneric(scene);
        pictureUp = true;
        placeReticle(scene, floorUp);
        if (Online.joined()) {
            NameTags.look(cleanView, IsoWorld.instance.currentCell, player);
        } else {
            NameTags.lookAway();
        }
        // The game draws its own reticle while the aim is held, and nothing while it is not.
        if (reticleX != RETICLE_HIDDEN && lookCaptured && ViewModel.held != null && (!player.isAiming() || Controls.turnedOnly())) {
            Crosshair.show(ndc[0], ndc[1], ViewModel.spread());
        } else {
            Crosshair.hide();
        }

        if (TRACE) {
            trace(player, scene, zoom);
        }
    }

    /**
     * Builds the frame for a camera on its way between the game's own view and the eye. Until
     * it is as near as the viewer's own head it sees them as it sees anyone, in a scene built
     * round where they stand. From there in it is the first-person view from a little behind
     * the eye.
     */
    private static void fly(IsoPlayer player, WorldScene scene, float floorUp) {
        aimOverhead(player, floorUp);
        Flight.camera(overheadView, overheadHalfHigh / OVERHEAD_AWAY, OVERHEAD_AWAY, OVERHEAD_YAW, OVERHEAD_PITCH,
                cleanView, trackedView, yaw, pitch, FieldOfView.tanHalfVertical(), flightView);
        if (Flight.away() > OWN_FROM) {
            standingView.set(flightView);
            standingView.eyeX = eye[0];
            standingView.eyeY = eye[1];
            standingView.eyeUp = eye[2];
            Ride.seated = false;
            scene.build(player, standingView, standingView, FieldOfView.tanHalfVertical(), FieldOfView.setTanHalfVertical(), flightView, false);
            scene.seenFrom(flightView, Flight.tanHalfFieldOfView(), Flight.away(), Flight.share());
        } else {
            fromTheEye = true;
            scene.build(player, flightView, aimView, Flight.tanHalfFieldOfView(), FieldOfView.setTanHalfVertical(), null, false);
        }
        scene.mixed(Flight.shown());
    }

    /**
     * Getting into a vehicle in first person is sitting down facing the way the seat does: left
     * alone the view keeps the way the character faced on foot. From then on the view turns with
     * the vehicle, as a head does with the seat under it, and the mouse turns it from there. In
     * the game's own view nothing changes.
     */
    private static void rideAlong(IsoPlayer player) {
        BaseVehicle vehicle = player == null ? null : player.getVehicle();
        if (vehicle != null) {
            float heading = Ride.heading(vehicle);
            if (on) {
                setLook(wasRiding ? yaw + heading - rideHeading : heading, wasRiding ? pitch : 0f);
            }
            rideHeading = heading;
        }
        wasRiding = vehicle != null;
    }

    private static void notePace(long now, long frameNanos) {
        if (!on) {
            Quality.rest();
            paceWait = PACE_FIRST_NANOS;
            paceSince = now;
            paceFrames = 0;
            paceWorst = 0;
            return;
        }
        paceFrames++;
        paceWorst = Math.max(paceWorst, frameNanos);
        if (landed && !GameTime.isGamePaused()) {
            Quality.frame(now);
        } else {
            Quality.rest();
        }
        if (frameNanos > LONG_FRAME_NANOS && paceScene != null) {
            Log.info(String.format(Locale.ROOT, "first person long frame: %.0f ms, %s", frameNanos / 1e6, paceScene.timing()));
        }
        if (now - paceSince > paceWait) {
            Runtime memory = Runtime.getRuntime();
            Log.info(String.format(Locale.ROOT, "first person pace: %.1f ms a frame, worst %.1f, heap %d of %d MB, %s",
                    (now - paceSince) / 1e6 / paceFrames, paceWorst / 1e6, (memory.totalMemory() - memory.freeMemory()) >> 20,
                    memory.maxMemory() >> 20, paceScene == null ? "no scene yet" : paceScene.describe()));
            if (TRACE) {
                Log.info(String.format(Locale.ROOT, "first person memory taken a frame: %.1f KB building, %.1f KB drawing",
                        paceBuiltBytes / 1024.0 / paceFrames, WorldScene.takeDrawnBytes() / 1024.0 / paceFrames));
                paceBuiltBytes = 0;
            }
            paceWait = PACE_EVERY_NANOS;
            paceSince = now;
            paceFrames = 0;
            paceWorst = 0;
        }
    }

    /**
     * The game aims from its aim origin to the point under the cursor at the origin's own
     * height, so that is the height the cursor's point is taken at.
     */
    private static void holdTheCursorAhead(IsoPlayer player, float zoom) {
        float cursorAtX = player.getAimOriginPosX() + (float) Math.cos(yaw) * CURSOR_DISTANCE;
        float cursorAtY = player.getAimOriginPosY() + (float) Math.sin(yaw) * CURSOR_DISTANCE;
        float aimHeight = player.getAimOriginPosZ();
        cursorX = Math.round(IsoUtils.XToScreenExact(cursorAtX, cursorAtY, aimHeight, 0) / zoom);
        cursorY = Math.round(IsoUtils.YToScreenExact(cursorAtX, cursorAtY, aimHeight, 0) / zoom);
    }

    /** Whether the world round the player has been built, seen or unseen, since the view last gave back what it held. */
    private static boolean rehearsed;
    private static int rehearsals;

    /**
     * Builds the world as first person would see it from where the player stands, a few
     * milliseconds a frame, draws none of it and lets it go. The first build of a session reads
     * the mod's models out of its jar, reads every sprite's shape for the first time and runs
     * code Java has not yet made fast: left to the frame that switches to first person, the
     * chunks round the player alone held that frame for a fifth of a second.
     */
    private static void rehearse(IsoPlayer player) {
        float x = player.getX(), y = player.getY(), level = player.getZ();
        Vector2 facing = player.getForwardDirection();
        ViewCamera.clean(standingView, x, y, level * ViewCamera.LEVEL_HEIGHT + Terrain.lift(x, y, level) + eyeHeight(player),
                (float) Math.atan2(facing.y, facing.x), 0f);
        Headlights.gather(x, y);
        float facingLength = Math.max((float) Math.hypot(facing.x, facing.y), 1e-3f);
        CarriedLight.gather(player, standingView.eyeX, standingView.eyeY, standingView.eyeUp, facing.x / facingLength, facing.y / facingLength, 0f);
        WorldScene scene = WorldScene.next();
        WorldScene.rehearsing = true;
        try {
            scene.build(player, standingView, standingView, FieldOfView.tanHalfVertical(), FieldOfView.setTanHalfVertical(), null, false);
        } finally {
            WorldScene.rehearsing = false;
        }
        rehearsals++;
        if (scene.waiting() == 0) {
            rehearsed = true;
            Log.info("first person world rehearsed over " + rehearsals + " frames");
        }
    }

    /**
     * Draws the game's own view with the picture: the same world from the same place, at the
     * game's own angle and zoom, so that everything is where the game has it on the screen and
     * the pointer still points at what it is on. The game's view has no perspective. A camera
     * a long way off with a very narrow field of view has next to none, and everything the
     * picture does works with it as it stands.
     */
    private static void drawOverhead(IsoPlayer player) {
        float x = player.getX(), y = player.getY(), level = player.getZ();
        float floorUp = level * ViewCamera.LEVEL_HEIGHT;
        aimOverhead(player, floorUp);
        ViewCamera.clean(standingView, x, y, floorUp + Terrain.lift(x, y, level) + eyeHeight(player), OVERHEAD_YAW, OVERHEAD_PITCH);
        if (TRACE && (overheadSaid & 127) == 1) {
            int index = player.getIndex();
            ViewCamera.project(overheadView, overheadHalfHigh / OVERHEAD_AWAY, (float) IsoCamera.getScreenWidth(index) / IsoCamera.getScreenHeight(index),
                    x, y, floorUp + Terrain.lift(x, y, level), ndc);
            Log.info(String.format(Locale.ROOT, "overhead: the picture has the player's feet at (%.1f, %.1f), lift %.3f",
                    (ndc[0] * 0.5f + 0.5f) * IsoCamera.getScreenWidth(index), (0.5f - ndc[1] * 0.5f) * IsoCamera.getScreenHeight(index),
                    Terrain.lift(x, y, level)));
        }
        Headlights.gather(x, y);
        Vector2 facing = player.getForwardDirection();
        float facingLength = Math.max((float) Math.hypot(facing.x, facing.y), 1e-3f);
        CarriedLight.gather(player, standingView.eyeX, standingView.eyeY, standingView.eyeUp, facing.x / facingLength, facing.y / facingLength, 0f);
        Ride.seated = false;
        world.look(IsoWorld.instance.currentCell, (int) Math.floor(level));
        WorldScene scene = WorldScene.next();
        // The game's own view shows the ground out to twice the picture's half height along the way it looks, which is
        // 30 degrees down, and to its half width across, and each floor up a little further along.
        int screen = player.getIndex();
        float halfWide = overheadHalfHigh * IsoCamera.getScreenWidth(screen) / IsoCamera.getScreenHeight(screen);
        float flat = (float) Math.hypot(overheadView.forwardX, overheadView.forwardY);
        scene.seenOnly(overheadView.eyeX + overheadView.forwardX * OVERHEAD_AWAY, overheadView.eyeY + overheadView.forwardY * OVERHEAD_AWAY,
                overheadView.forwardX / flat, overheadView.forwardY / flat, halfWide, (float) (overheadHalfHigh / Math.sin(-OVERHEAD_PITCH)),
                (float) (ViewCamera.LEVEL_HEIGHT / Math.tan(-OVERHEAD_PITCH)));
        scene.build(player, standingView, standingView, FieldOfView.tanHalfVertical(), FieldOfView.setTanHalfVertical(), overheadView, true);
        scene.seenFrom(overheadView, overheadHalfHigh / OVERHEAD_AWAY, OVERHEAD_AWAY, 0f);
        paceScene = scene;
        SpriteRenderer.instance.drawGeneric(scene);
        pictureUp = true;
    }

    /**
     * Puts the overhead camera where the game's own view is drawn from: looking at the place
     * that is at the middle of the screen, at the height the player's floor is drawn at.
     */
    private static void aimOverhead(IsoPlayer player, float floorUp) {
        int index = player.getIndex();
        float zoom = Core.getInstance().getZoom(index);
        float x = player.getX(), y = player.getY(), level = player.getZ();
        float atX = IsoUtils.XToScreenExact(x, y, level, 0) / zoom, atY = IsoUtils.YToScreenExact(x, y, level, 0) / zoom;
        float eastX = IsoUtils.XToScreenExact(x + 1f, y, level, 0) / zoom - atX, eastY = IsoUtils.YToScreenExact(x + 1f, y, level, 0) / zoom - atY;
        float southX = IsoUtils.XToScreenExact(x, y + 1f, level, 0) / zoom - atX, southY = IsoUtils.YToScreenExact(x, y + 1f, level, 0) / zoom - atY;
        float wide = IsoCamera.getScreenWidth(index), high = IsoCamera.getScreenHeight(index);
        // The place on the player's floor that is at the middle of the screen.
        float toX = wide / 2f - atX, toY = high / 2f - atY;
        float det = eastX * southY - southX * eastY;
        float middleX = x + (toX * southY - southX * toY) / det, middleY = y + (eastX * toY - toX * eastY) / det;
        // A tile's diagonal lies along the screen: its length there over its length on the map.
        float pixelsToTile = (float) (Math.hypot(eastX - southX, eastY - southY) / Math.sqrt(2.0));
        overheadHalfHigh = high / 2f / pixelsToTile;
        if (TRACE && (overheadSaid++ & 127) == 0) {
            Log.info(String.format(Locale.ROOT, "overhead: player on screen at (%.1f, %.1f) of %.0fx%.0f, zoom %.2f, east (%.2f, %.2f) south (%.2f, %.2f), camera offset (%.1f, %.1f), middle of screen at tile (%.2f, %.2f), player (%.2f, %.2f, %.2f)",
                    atX, atY, wide, high, zoom, eastX, eastY, southX, southY, IsoCamera.getOffX(), IsoCamera.getOffY(), middleX, middleY, x, y, level));
        }
        ViewCamera.clean(overheadView, 0f, 0f, 0f, OVERHEAD_YAW, OVERHEAD_PITCH);
        overheadView.eyeX = middleX - overheadView.forwardX * OVERHEAD_AWAY;
        overheadView.eyeY = middleY - overheadView.forwardY * OVERHEAD_AWAY;
        overheadView.eyeUp = floorUp - overheadView.forwardUp * OVERHEAD_AWAY;
    }

    /**
     * The reticle marks a point, not a direction: where the clean aim, from where the shot
     * starts, first meets the world this frame. With the head leaned the picture is drawn from
     * another eye, and that point is no longer straight ahead of it. With nothing in reach the
     * aim is at infinity and only its direction is left to project.
     */
    private static void placeReticle(WorldScene scene, float floorUp) {
        pictureTanHalf = scene.tanHalfFieldOfView();
        shotDistance = world.shotDistance(aimView, floorUp, WorldScene.REACH,
                scene.characterX, scene.characterY, scene.characterBase, scene.characters);
        // The game looks for a shot's target along a line through this point. With nothing
        // in reach it is a point as far along the view as anything could have been. The
        // picture raises the land and the game's map is flat, so the point's height is
        // taken above the flat map, in floor levels.
        float aimed = shotDistance == Float.POSITIVE_INFINITY ? WorldScene.REACH : shotDistance;
        float aimedX = aimView.eyeX + aimView.forwardX * aimed, aimedY = aimView.eyeY + aimView.forwardY * aimed;
        float aimedUp = aimView.eyeUp + aimView.forwardUp * aimed;
        IsoPlayer player = IsoPlayer.getInstance();
        float level = player.getZ();
        float aimedLevel = (aimedUp - Terrain.lift(aimedX, aimedY, level)) / ViewCamera.LEVEL_HEIGHT;
        Controls.aimAt(aimedX, aimedY, aimedLevel);
        // The game takes the square an attack is made at from what its own view has under the
        // pointer, and under this picture that is nothing. A shot that hits nobody is told
        // to the server by that square, and one with no square is thrown away there: nobody
        // else would see or hear it. So the square is the one the aim is on, or failing one
        // there the shooter's own, which is what the game gives a player with a controller.
        if (lookCaptured) {
            IsoGridSquare under = IsoWorld.instance.currentCell.getGridSquare((int) Math.floor(aimedX), (int) Math.floor(aimedY),
                    (int) Math.floor(aimedLevel + 0.25f));
            player.setAttackTargetSquare(under != null ? under : player.getCurrentSquare());
        }

        float aspect = WorldScene.drawnAspect();
        if (aspect == 0f) {
            // No frame has been drawn yet, so the shape of the picture is not known.
            hideReticle();
            return;
        }
        float along = shotDistance;
        ViewCamera.Pose from = aimView;
        if (along == Float.POSITIVE_INFINITY) {
            along = 1f;
            from = trackedView;
        }
        boolean inFront = ViewCamera.project(trackedView, scene.tanHalfFieldOfView(), aspect,
                from.eyeX + aimView.forwardX * along, from.eyeY + aimView.forwardY * along,
                from.eyeUp + aimView.forwardUp * along, ndc);
        if (!inFront) {
            hideReticle();
            return;
        }
        int index = IsoPlayer.getInstance().getIndex();
        reticleX = Math.round((ndc[0] * 0.5f + 0.5f) * IsoCamera.getScreenWidth(index));
        reticleY = Math.round((0.5f - ndc[1] * 0.5f) * IsoCamera.getScreenHeight(index));
    }

    /** Half the height of the picture's field of view, as a tangent, as it was last drawn. */
    private static float pictureTanHalf;

    /**
     * Where a place in the world falls in the picture as it was last drawn, the head's turn
     * and lean included. False if it is behind the eye or no picture has been drawn.
     */
    static boolean inPicture(float x, float y, float up, float[] ndc) {
        float aspect = WorldScene.drawnAspect();
        return aspect != 0f && pictureTanHalf != 0f && ViewCamera.project(trackedView, pictureTanHalf, aspect, x, y, up, ndc);
    }

    private static void hideReticle() {
        reticleX = RETICLE_HIDDEN;
        reticleY = RETICLE_HIDDEN;
    }

    /**
     * Lets go of the mouse or takes it back. The inventory key is handled here and kept from
     * the game's own handler, which only flips the panels between shown and hidden: they are
     * shown folded from the start, so its first press would hide them.
     */
    private static void point() {
        Core.KeyBinding inventory = Core.getInstance().getKeyBinding(INVENTORY_BINDING);
        boolean inventoryKey = pressed(inventory.keyValue()) | pressed(inventory.altKey());
        boolean escape = GameKeyboard.isKeyPressed(ESCAPE_KEY);
        if (escape && pointer.byKey()) {
            // Or the same press would go on to open the game's menu.
            GameKeyboard.eatKeyPress(ESCAPE_KEY);
        }
        String was = TRACE ? pointer.describe() : null;
        int panels = Panels.open();
        long now = System.nanoTime();
        if (Mouse.isButtonDown(AIM_BUTTON)) {
            aimedAt = now;
        }
        if (now - aimedAt < AFTER_AIM_NANOS && !pointer.byKey()) {
            pointer.passOver(panels);
        }
        PointerMode.Inventory inventoryPanels = pointer.update(inventoryKey, escape, Mouse.isButtonDown(POINT_BUTTON), panels);
        if (inventoryPanels != PointerMode.Inventory.LEAVE) {
            Panels.showInventory(inventoryPanels == PointerMode.Inventory.OPEN);
        }
        if (TRACE && !was.equals(pointer.describe())) {
            Log.info("pointer " + pointer.describe());
        }
    }

    /**
     * Called as the Interact key goes down with the mouse turning the view: acts on what the
     * middle of the clean view is on, and says whether anything was there for it. In a
     * vehicle the key is the game's, which gets out with it.
     */
    static boolean useAtCentre() {
        IsoPlayer player = IsoPlayer.getInstance();
        if (player.getVehicle() != null) {
            return false;
        }
        float floorUp = player.getZ() * ViewCamera.LEVEL_HEIGHT + Terrain.lift(player.getX(), player.getY(), player.getZ());
        CentreUse.Outcome outcome = CentreUse.press(cleanView, floorUp, player, IsoWorld.instance.currentCell);
        if (outcome == CentreUse.Outcome.CONTAINER) {
            pointer.open();
        }
        if (TRACE) {
            Log.info("interact at the middle of the view: " + outcome + " on " + Thread.currentThread().getName());
        }
        return outcome != CentreUse.Outcome.NOTHING;
    }

    /** Whether a key of a binding went down this frame. The game eats the press, so its own handlers never see the key come up. */
    private static boolean pressed(int key) {
        if (key == 0 || !GameKeyboard.isKeyPressed(key)) {
            return false;
        }
        GameKeyboard.eatKeyPress(key);
        return true;
    }

    /** Called where the game's reticle and aim cursor read the screen column to draw at. */
    public static int reticleX(int stock) {
        return landed ? Scope.hidesReticle() || Controls.turnedOnly() ? RETICLE_HIDDEN : reticleX : stock;
    }

    /** Called where the game's reticle and aim cursor read the screen row to draw at, counted from the top. */
    public static int reticleY(int stock) {
        return landed ? Scope.hidesReticle() || Controls.turnedOnly() ? RETICLE_HIDDEN : reticleY : stock;
    }

    private static float eyeHeight(IsoPlayer player) {
        if (player.isSitOnGround() || player.isSittingOnFurniture()) {
            return EYE_HEIGHT_SITTING;
        }
        return player.isSneaking() ? EYE_HEIGHT_SNEAKING : EYE_HEIGHT_STANDING;
    }

    /**
     * Whether the first person picture is drawn over the whole of the game's own view, so that
     * what the game draws of the weather there is never seen: the eye has landed, and is not on
     * its way between the two views, when the game's own shows through. Called from the game's
     * code on its logic thread, and for Characters on its render thread, which draws the frame
     * the logic thread built a moment before: on the one frame where the two differ the picture
     * is as good as whole.
     */
    public static boolean coversTheGamesView() {
        return landed && !WITHOUT_COVER;
    }

    /** Dev only: the game draws its weather under the picture as it always did, to measure what leaving it out saves. */
    private static final boolean WITHOUT_COVER = Boolean.getBoolean("cameraunlock.zomboid.withoutCover");

    /**
     * Called as the game's window is about to show or hide its cursor to suit the game. The
     * game shows and hides its pointer as the player aims and fires, and each change puts the
     * window's cursor back where it was before: the look, which goes by how far the cursor
     * has moved, took that for a turn and jumped. While the look has the mouse the window's
     * cursor is the look's alone.
     */
    public static boolean holdsMouse() {
        return !MouseGrab.ISOLATED && (MouseGrab.applied || MouseGrab.keeping);
    }

    /** Called at the end of the game's Mouse.update with the cursor column it read. */
    public static int cursorX(int raw) {
        if (!lookCaptured) {
            haveRawX = false;
            return raw;
        }
        if (MouseGrab.moved.get() > 0 || System.nanoTime() - capturedNanos < LOOK_SETTLES_NANOS) {
            haveRawX = false;
        }
        if (haveRawX && !MouseGrab.ISOLATED) {
            float turn = (raw - lastRawX) * RADIANS_PER_PIXEL * FieldOfView.zoom() * Scope.turn();
            mouseTurned += turn;
            mouseMost = Math.max(mouseMost, Math.abs(raw - lastRawX));
            setLook(yaw + turn, pitch);
        }
        lastRawX = raw;
        haveRawX = true;
        return cursorX;
    }

    /** Called at the end of the game's Mouse.update with the cursor row it read, counted from the top. */
    public static int cursorY(int raw) {
        if (!lookCaptured) {
            haveRawY = false;
            return raw;
        }
        if (MouseGrab.moved.get() > 0) {
            haveRawY = false;
            MouseGrab.moved.decrementAndGet();
        }
        if (System.nanoTime() - capturedNanos < LOOK_SETTLES_NANOS) {
            haveRawY = false;
        }
        if (haveRawY && !MouseGrab.ISOLATED) {
            setLook(yaw, pitch - (raw - lastRawY) * RADIANS_PER_PIXEL * FieldOfView.zoom() * Scope.turn());
        }
        lastRawY = raw;
        haveRawY = true;
        return cursorY;
    }

    /**
     * Called with the way the game takes the player to be looking when it works out what
     * they can see. The game hides whoever is outside that cone, and its cone is the body's:
     * walking sideways the body faces the way it walks, and a zombie straight ahead of the
     * view, an arm's length off, was gone and back again as the body turned. In first person
     * what the player can see is what the view faces.
     */
    public static Vector2 sightVector(Vector2 look) {
        if (!landed) {
            return look;
        }
        return look.set((float) Math.cos(yaw), (float) Math.sin(yaw));
    }

    /**
     * Called with the direction the movement keys ask for, in world axes. In
     * the game's own view forward is up the screen; here it is where the view
     * faces. With no key down and no aim held the character is turned to face
     * the view, since nothing else in the game does that without the cursor.
     */
    public static Vector2 moveVector(Vector2 move) {
        traceMoveX = move.x;
        traceMoveY = move.y;
        // The keys are the view's from the moment first person is asked for, not from the landing: see Stride.
        if (!on) {
            return move;
        }
        IsoPlayer player = IsoPlayer.getInstance();
        if (player == null || player.getVehicle() != null) {
            return move;
        }
        if (move.x == 0f && move.y == 0f) {
            stride.reset();
            movingAside = false;
            if (!player.isAiming()) {
                player.setForwardDirection(forward.set((float) Math.cos(yaw), (float) Math.sin(yaw)));
            }
            return move;
        }
        movingAside = Online.aside(move.x, move.y);
        float turn = stride.turn(move.x, move.y, yaw, STOCK_FORWARD_YAW, System.nanoTime());
        float cos = (float) Math.cos(turn);
        float sin = (float) Math.sin(turn);
        return move.set(move.x * cos - move.y * sin, move.x * sin + move.y * cos);
    }

    private static void trace(IsoPlayer player, WorldScene scene, float zoom) {
        long now = System.nanoTime();
        if (now - lastTrace < TRACE_INTERVAL_NANOS) {
            return;
        }
        lastTrace = now;
        Vector2 facing = player.getForwardDirection();
        player.getAimVector(traceAim);
        Log.info(String.format(Locale.ROOT,
                "trace yaw %.1f pitch %.1f at (%.2f, %.2f, %.2f) facing (%.2f, %.2f) aim (%.2f, %.2f) aiming %b keys (%.2f, %.2f)"
                        + " cursor (%d, %d) zoom %.2f offset (%.0f, %.0f) captured %b pointer %s | %s | reticle (%d, %d) ndc (%.4f, %.4f)"
                        + " shot distance %.3f render eye (%.3f, %.3f, %.3f) | %s | %s | %s",
                Math.toDegrees(yaw), Math.toDegrees(pitch), player.getX(), player.getY(), player.getZ(),
                facing.x, facing.y, traceAim.x, traceAim.y, player.isAiming(), traceMoveX, traceMoveY,
                cursorX, cursorY, zoom, IsoCamera.getOffX(), IsoCamera.getOffY(), lookCaptured, pointer.describe(), HeadTracking.describe(),
                reticleX, reticleY, ndc[0], ndc[1], shotDistance, trackedView.eyeX, trackedView.eyeY, trackedView.eyeUp,
                scene.describe(), Controls.describe(), ViewModel.describe()));
    }
}
