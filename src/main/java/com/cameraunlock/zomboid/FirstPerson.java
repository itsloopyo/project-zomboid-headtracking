package com.cameraunlock.zomboid;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.joml.Vector3f;
import zombie.GameTime;
import zombie.characters.CharacterJoypadAxis2dBinding;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.animals.IsoAnimal;
import zombie.core.Core;
import zombie.core.PerformanceSettings;
import zombie.core.SpriteRenderer;
import zombie.core.opengl.RenderThread;
import zombie.input.AimingReticle;
import zombie.input.GameKeyboard;
import zombie.input.Mouse;
import zombie.iso.IsoCamera;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoUtils;
import zombie.iso.IsoWorld;
import zombie.iso.PlayerCamera;
import zombie.iso.Vector2;
import zombie.iso.objects.IsoTree;
import zombie.scripting.objects.VehicleScript;
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
    final View view;

    FirstPerson(View view) {
        this.view = view;
    }

    /** How long the name of a graphics mode, picture style or aim mode just gone to stays on the screen. */
    private static final long SAID_FOR_NANOS = 2_500_000_000L;
    private long saidAt;
    private String said;
    /** How far off the overhead camera stands, in tiles: far enough that the picture has next to no perspective. */
    private static final float OVERHEAD_AWAY = 6000f;
    private static final float OVERHEAD_YAW = (float) (-0.75 * Math.PI), OVERHEAD_PITCH = (float) Math.toRadians(-30);
    private boolean overhead;
    private int overheadSaid;
    private final ViewCamera.Pose overheadView = new ViewCamera.Pose(), standingView = new ViewCamera.Pose();
    /** What the overhead camera last took in above and below the place it looks at, in tiles. */
    private float overheadHalfHigh;
    /** Where on the player's floor the game's window through trees is (CanopyWindow), and the pointer's while aiming. */
    private float canopyX, canopyY, canopyAimX, canopyAimY;
    private boolean canopyAimed;
    /** The trees the game draws see-through: each one's square and how much of it the game draws, three floats a tree. */
    private final float[] canopyFaded = new float[3 * CanopyWindow.TREES];
    private int canopyFadedCount;
    private final float[] onFloor = new float[2], maskAt = new float[2];
    /** How many faded trees were left out this frame for want of room, and whether that has been logged. */
    private int canopyOver;
    private boolean canopyOverSaid;
    private final ViewCamera.Pose flightView = new ViewCamera.Pose();
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
    private long aimedAt = Long.MIN_VALUE / 2;

    /** How far a pixel of mouse travel turns the view unzoomed. Zoomed it turns the view less, to move the picture as far. */
    private static final float RADIANS_PER_PIXEL = 0.0025f;
    /** A frame longer than this is a hitch, and taking it whole would snap the zoom to its target. */
    private static final float MAX_FRAME_SECONDS = 0.25f;
    private static final float PITCH_LIMIT = (float) Math.toRadians(89);
    /** How fast a controller's stick pushed right over turns the view unzoomed, in radians a second: across, and up or down. */
    private static final float STICK_YAW = (float) Math.toRadians(220), STICK_PITCH = (float) Math.toRadians(140);
    private final Vector2 stick = new Vector2();
    /** How far ahead of the character the game's cursor is held, in tiles. */
    private static final float CURSOR_DISTANCE = 3f;
    /** The direction the forward key moves the character in the game's own view: up the screen. */
    private static final float STOCK_FORWARD_YAW = (float) Math.toRadians(-135);
    /** The movement keys ask for a way that is not ahead of the view: sideways or back. */
    private boolean movingAside;

    /** Whether the character is being walked sideways or backwards from the way the view faces. */
    boolean movingAside() {
        return movingAside && lookCaptured();
    }

    private static final float EYE_HEIGHT_STANDING = 1.6f;
    private static final float EYE_HEIGHT_SNEAKING = 1.1f;
    private static final float EYE_HEIGHT_SITTING = 0.9f;
    /** The eye goes from one of those heights to another over a few of these, where set at once the whole picture cut half a tile in a frame. */
    private static final float EYE_HEIGHT_SECONDS = 0.1f;
    private float eyeHigh;
    private final float[] eye = new float[3];
    private final float[] headOffset = new float[3];
    static final boolean TRACE = Boolean.getBoolean("cameraunlock.zomboid.trace");
    private static final long TRACE_INTERVAL_NANOS = 1_000_000_000L;

    /** The view the player has asked for. The camera may still be on its way there. */
    private volatile boolean on;
    /** The camera is at the eye, and the view is played in. */
    private volatile boolean landed;
    /** The last frame was drawn from the eye or as near it as makes no difference: the viewer's own body was drawn as their own. */
    private boolean fromTheEye;
    private boolean wasRiding;
    /** The way the vehicle the player is in faced on the last frame. */
    private float rideHeading;
    /** The view is played in, the game is running and the character is alive. */
    private boolean active;
    private boolean lookCaptured;
    /** Whether a frame of the picture has been queued since its targets were last given back. */
    private boolean pictureUp;
    /** Logic frames in a row with no player at this view's number. */
    private int goneFor;
    private final PointerMode pointer = new PointerMode();
    /**
     * Leaving for the main menu stops the frames with the look still marked as captured, and
     * the game goes on reading its keys there.
     */
    private static final long CAPTURE_LASTS_NANOS = 500_000_000L;
    private long lastFrameNanos;
    private float yaw;
    private float pitch;

    /**
     * How the picture is keeping up, said whoever is playing: a report of a slow game is of no
     * use without the frame time, the memory in use and the size of the scene. Once soon after
     * the view comes on and then once a minute, so that an evening's play leaves a log the
     * other lines can still be found in.
     */
    private static final long PACE_FIRST_NANOS = 5_000_000_000L;
    private static final long PACE_EVERY_NANOS = TRACE ? PACE_FIRST_NANOS : 60_000_000_000L;
    private long paceWait = PACE_FIRST_NANOS;
    private long paceSince, paceWorst;
    private int paceFrames;
    /** With tracing on, the memory the logic thread took for the picture since the last pace line. */
    private long paceBuiltBytes;
    private WorldScene.Frame paceScene;
    /** A frame this long is a hitch the player feels: the log says what the frame before it built and drew. */
    private static final long LONG_FRAME_NANOS = 100_000_000L;
    /** A PC that draws every frame that slowly would write a line a frame, and each line is a write to disk: one a second, with how many went unsaid. */
    private static final long LONG_FRAME_LOG_INTERVAL_NANOS = 1_000_000_000L;
    private long longFrameLoggedNanos = System.nanoTime() - LONG_FRAME_LOG_INTERVAL_NANOS;
    private int longFramesUnsaid;
    private boolean haveRawX, haveRawY;
    private int lastRawX, lastRawY;
    private int cursorX, cursorY;

    /** Where the reticle is drawn when the aim point is not in the picture: off the screen. */
    private static final int RETICLE_HIDDEN = -100_000;

    private final ViewCamera.Pose cleanView = new ViewCamera.Pose();
    private final ViewCamera.Pose trackedView = new ViewCamera.Pose();
    /**
     * The aim: the clean view from where the shot starts, which is the clean eye moved by
     * whatever of a lean the weapon has. The weapon is drawn in this view and the shot is
     * traced from it.
     */
    private final ViewCamera.Pose aimView = new ViewCamera.Pose();
    private final HitPart onBone = new HitPart();
    private final WorldQuery world = new WorldQuery();
    private final float[] ndc = new float[2];
    private int reticleX, reticleY;
    private float shotDistance;

    private final Vector2 forward = new Vector2();
    private final Stride stride = new Stride();
    /** A read of the mouse this soon after the look takes it may be of a cursor the window has just moved, not of the hand. */
    private static final long LOOK_SETTLES_NANOS = 150_000_000L;
    private long capturedNanos;
    private boolean wasCaptured;
    /** For the line said a second after a landing: when it was, and what the mouse has turned the view by since, in all and the most in one read. */
    private long landedNanos;
    private boolean landingSaid = true;
    private float mouseTurned;
    private int mouseMost;
    private final Vector2 drawn = new Vector2();
    private final Vector2 traceAim = new Vector2();
    private float traceMoveX, traceMoveY;
    private long lastTrace;


    boolean isOn() {
        return on;
    }

    /** Whether the viewer's own body is being drawn as it is seen from their own eye. */
    boolean seenFromTheEye() {
        return fromTheEye;
    }

    /** Whether the picture is drawing the game's own view, with the camera at rest there. */
    boolean overhead() {
        return overhead && !on && view.flight.home();
    }

    /** The clean view direction: where the mouse has turned the view, with nothing else mixed in. */
    float yaw() {
        return yaw;
    }

    float pitch() {
        return pitch;
    }

    /** The clean aim as the last frame drawn had it: where a shot is aimed from and which way, in the picture's units. */
    ViewCamera.Pose aimView() {
        return aimView;
    }

    /** Whether the mouse is turning the view, for what the game asks between frames. */
    boolean lookCaptured() {
        return lookCaptured && System.nanoTime() - lastFrameNanos < CAPTURE_LASTS_NANOS;
    }

    /** Whether the first person view is being played in, with the mouse turning the view or out as a pointer. */
    boolean active() {
        return active && System.nanoTime() - lastFrameNanos < CAPTURE_LASTS_NANOS;
    }

    void setLook(float newYaw, float newPitch) {
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
    private boolean gettingReady;
    private long readySince;
    /** Whether the view holds anything on the graphics card that letting go would give back, and when it was last in use. */
    private boolean held;
    private long offSince;
    private static final long LET_GO_AFTER_NANOS = 60_000_000_000L;
    /** A PC on which the making and the rehearsal take longer than this goes on to first person without the rest of them. */
    private static final long READY_WITHIN_NANOS = 4_000_000_000L;

    void toggle() {
        IsoPlayer player = view.player();
        if (!on && !allowed(player)) {
            Log.info(who() + "first person stays off: " + whyNot(player));
            return;
        }
        if (gettingReady) {
            gettingReady = false;
            Log.info(who() + "first person no longer asked for");
            return;
        }
        if (!on) {
            // What is made ready is what first person's graphics mode draws.
            Quality.inFirstPerson(view.index, true);
        }
        // With the picture already drawing the game's own view there is nothing left to make and the world is built.
        if (!on && (view.pipeline.unprepared() || view.pipeline.contentWaited() || !rehearsed && !overhead())) {
            gettingReady = true;
            readySince = System.nanoTime();
            Log.info(who() + "first person asked for: what it is drawn with is being made");
            return;
        }
        switchView(player);
    }

    /** One frame of getting ready: the next piece of what the picture is drawn with, then the world built once unseen, then the switch. */
    private void getReady(IsoPlayer player, long now) {
        boolean late = now - readySince > READY_WITHIN_NANOS;
        if (!late && (view.pipeline.unprepared() || view.pipeline.contentWaited())) {
            SpriteRenderer.instance.drawGeneric(view.pipeline.PREPARE);
            return;
        }
        if (!late && !rehearsed && !view.characters.wanted && !GameTime.isGamePaused() && !player.isDead()) {
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
    private void letGo() {
        held = false;
        rehearsed = false;
        view.ride.sat = null;
        world.look(null, 0);
        view.worldScene.letGo();
    }

    /**
     * Called as the game leaves a save for its menu. No frame of the save comes after this, so
     * nothing would come to give anything back, and the scenes would go on naming the map the
     * game has let go of. Nor would anything come to let go of the mouse: a save can be left with
     * the look still holding it, by a server that stops or drops the player.
     */
    void leftSave() {
        SnowPrints.forget();
        giveBack("the save was left");
    }

    /**
     * Called once a frame while there is no player at this view's number: they left the game
     * with the view on, or with what it holds still held. No frame of theirs comes again to
     * give it back, and the other view would wait on it for what the two share.
     */
    void playerGone() {
        // The render thread may have the player's last frames still to draw, from the builds giveBack
        // lets go of: it waits as long as the scene ring does before it fills a frame again.
        if (goneFor < WorldScene.SCENES) {
            goneFor++;
        } else if (on || held || pictureUp || !view.flight.home()) {
            giveBack("player " + (view.index + 1) + " is gone");
        }
        // After giveBack, whose rest of the head tracking marks the view as one played in.
        Tracking.gone(view.index);
        Quality.rest(view.index);
    }

    /** What a line in the log begins with to say whose view it is of, for any player but the first. */
    private String who() {
        return view.index == 0 ? "" : "player " + (view.index + 1) + ": ";
    }

    /** Lets go of the look out of the eye, and of the mouse where the view has it. */
    private void stopLooking() {
        landed = false;
        active = false;
        lookCaptured = false;
        wasCaptured = false;
        fromTheEye = false;
        view.characters.wanted = false;
        if (view.hasMouse()) {
            MouseGrab.wanted = false;
            MouseGrab.kept = false;
        }
    }

    /** Switches the view off where it stands and gives back all it holds, from outside any frame of its own. */
    private void giveBack(String why) {
        try {
            on = false;
            gettingReady = false;
            stopLooking();
            // No frame is open here, and the menu's first frame clears whatever the sprite renderer
            // was handed since the last one: these go by the render thread's own queue.
            if (view.hasMouse() && (MouseGrab.applied || MouseGrab.keeping)) {
                RenderThread.queueInvokeOnRenderContext(MouseGrab.DRAWER::render);
            }
            view.headTracking.rest();
            view.viewModel.rest();
            view.crosshair.hide();
            view.nameTags.lookAway();
            view.hearing.rest();
            view.flight.cut(false);
            Quality.inFirstPerson(view.index, false);
            if (pictureUp) {
                pictureUp = false;
                RenderThread.queueInvokeOnRenderContext(view.pipeline.RELEASE::render);
            }
            if (held) {
                letGo();
                RenderThread.queueInvokeOnRenderContext(view.pipeline.LET_GO::render);
                Log.info(why + ": first person has let go of the map, and the render thread is asked for what the graphics card holds of it");
            }
        } catch (RuntimeException | LinkageError thrown) {
            // Thrown on, it would stop the game on its way out of the save.
            Log.error("first person could not give back what it held: " + why, thrown);
        }
    }

    private void switchView(IsoPlayer player) {
        on = !on;
        if (on && player.getVehicle() != null) {
            setLook(view.ride.heading(player.getVehicle()), 0f);
        } else if (on) {
            Vector2 facing = player.getForwardDirection();
            setLook((float) Math.atan2(facing.y, facing.x), 0f);
            stride.begin(traceMoveX, traceMoveY, yaw);
        }
        if (on) {
            said = Quality.said();
            saidAt = System.nanoTime();
        }
        Log.info(who() + "first person " + (on ? "on, " + facings(player) : "off"));
    }

    /** The ways the game has of saying which way a character faces, in degrees on the map: a landing that looks wrong is read off these. */
    private String facings(IsoPlayer player) {
        Vector2 facing = player.getForwardDirection();
        player.getAnimForwardDirection(drawn);
        return String.format(Locale.ROOT, "character facing %.0f and drawn facing %.0f",
                Math.toDegrees(Math.atan2(facing.y, facing.x)), Math.toDegrees(Math.atan2(drawn.y, drawn.x)));
    }

    private boolean allowed(IsoPlayer player) {
        return whyNot(player) == null;
    }

    private String whyNot(IsoPlayer player) {
        if (player == null) {
            return "no player";
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
        return gap != null ? gap : view.pipeline.failure();
    }

    /**
     * Called from IngameState.renderFrameInternal straight after the game has drawn this player's world.
     *
     * @param pressed the actions of Config's bits pressed this frame that are this view's to act on or to say
     */
    void afterWorldRender(int pressed) {
        goneFor = 0;
        long before = TRACE ? Allocation.byThisThread() : 0L;
        view.controls.frame();
        try {
            frame(pressed);
            IsoPlayer player = view.player();
            if (player != null && GameTime.isGamePaused()) {
                view.hearing.whilePaused(player);
            }
            // After the picture, which would cover it.
            String shown = gettingReady ? "First person: getting ready" : said != null && System.nanoTime() - saidAt < SAID_FOR_NANOS ? said : null;
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
            // A frame that throws again before it reaches them never clears these.
            stopLooking();
            if (view.hasMouse() && (MouseGrab.applied || MouseGrab.keeping)) {
                SpriteRenderer.instance.drawGeneric(MouseGrab.DRAWER);
            }
            view.flight.cut(false);
            view.hearing.rest();
        }
        if (TRACE) {
            paceBuiltBytes += Allocation.byThisThread() - before;
        }
    }

    /** Why the picture can no longer be built this session, or null. */
    private String failure;
    private final Set<String> thrownAt = new HashSet<>();

    /** @param pressed the actions of Config's bits pressed this frame that are this view's to act on or to say */
    private void frame(int pressed) {
        if ((pressed & Config.FIRST_PERSON) != 0) {
            toggle();
        }
        if ((pressed & Config.AIM_MODE) != 0) {
            said = Tracking.aimModeLabel();
            saidAt = System.nanoTime();
        }
        if (view.hasMouse()) {
            DevKeys.poll();
        }
        if ((pressed & Config.CYCLE_QUALITY) != 0) {
            said = Quality.said();
            saidAt = System.nanoTime();
        }
        if ((pressed & Config.CYCLE_STYLE) != 0) {
            said = "Style: " + Style.current().label;
            saidAt = System.nanoTime();
        }
        if ((pressed & Config.SPLIT_SCREEN) != 0) {
            said = "Split screen: " + SplitScreen.described();
            saidAt = System.nanoTime();
        }
        // The picture draws the game's own view once what it is drawn with is made: until then that view is the game's.
        IsoPlayer player = view.player();
        // Asked once: the answer is several looks into the game's Lua, and the frame goes by one answer throughout.
        boolean allowed = allowed(player);
        boolean overheadAsked = !Quality.overheadPlain() && allowed;
        overhead = overheadAsked && !view.pipeline.unprepared() && !view.pipeline.contentWaited();
        view.worldScene.level(overhead());
        rideAlong(player);
        tread(player);
        if ((on || !view.flight.home()) && !allowed) {
            if (on) {
                Log.info(who() + "first person off: " + whyNot(player));
            }
            on = false;
            view.flight.cut(false);
        }
        long now = System.nanoTime();
        long frameNanos = now - lastFrameNanos;
        float sinceLastFrame = Math.min(frameNanos / 1e9f, MAX_FRAME_SECONDS);
        notePace(now, frameNanos);
        lastFrameNanos = now;
        view.flight.advance(on, sinceLastFrame, !overhead);
        boolean wasFirstPerson = Quality.firstPersonInEffect();
        Quality.inFirstPerson(view.index, on || gettingReady || !view.flight.home());
        if (wasFirstPerson && !Quality.firstPersonInEffect()) {
            // Back in the isometric view, with its own graphics mode: said, so a mode not wanted there is not played on unawares.
            said = Quality.said();
            saidAt = now;
        }
        boolean wasLanded = landed;
        landed = on && view.flight.landed();
        if (landed && !wasLanded) {
            landedNanos = now;
            landingSaid = false;
            mouseTurned = 0f;
            mouseMost = 0;
            Log.info(String.format(Locale.ROOT, "first person landed looking %.0f, %s, movement keys (%.2f, %.2f), %s",
                    Math.toDegrees(yaw), facings(player), traceMoveX, traceMoveY, view.headTracking.describe()));
            stride.landed(now);
        } else if (!on) {
            stride.reset();
        }
        // The game stops drawing the world of a player who sleeps once their screen has faded, and with it this frame:
        // the look is let go of as the sleep begins, while frames still come to let go of the mouse.
        active = landed && !GameTime.isGamePaused() && !player.isDead() && !player.isAsleep();
        if (!landed || !view.onMouse()) {
            pointer.reset();
        } else if (active) {
            point();
        }
        lookCaptured = active && !pointer.on();
        if (lookCaptured && View.onController(player)) {
            stickLook(player, sinceLastFrame);
        }
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
        boolean flown = on || !view.flight.home();
        view.characters.wanted = flown || overhead && allowed && !player.isDead();
        if (view.hasMouse()) {
            MouseGrab.wanted = lookCaptured && view.onMouse();
            MouseGrab.kept = active && pointer.on();
        }
        ModelHead.sayGap();
        NameTags.sayGap();
        Hearing.sayGap();
        fromTheEye = landed;
        if (!flown) {
            view.fieldOfView.rest();
            view.headTracking.follow(player != null && !GameTime.isGamePaused() && !player.isDead());
            view.viewModel.rest();
            view.crosshair.hide();
            view.nameTags.lookAway();
            view.hearing.rest();
            if (view.hasMouse() && (MouseGrab.applied || MouseGrab.keeping)) {
                SpriteRenderer.instance.drawGeneric(MouseGrab.DRAWER);
            }
            if (gettingReady && !allowed) {
                gettingReady = false;
            } else if (gettingReady) {
                getReady(player, now);
            }
            if (view.characters.wanted) {
                drawOverhead(player);
            } else if (pictureUp) {
                pictureUp = false;
                view.worldScene.setDown();
                world.look(null, 0);
                SpriteRenderer.instance.drawGeneric(view.pipeline.RELEASE);
            }
            // Switched off and on again within the minute, the view is there at once. Left off, it gives back
            // what it holds: the world on the graphics card and the mod's own pictures, which are most of it.
            // While the picture is drawing the game's own view it makes its own pieces, one a frame.
            boolean making = !gettingReady && !view.characters.wanted && (overheadAsked && !overhead || view.pipeline.contentHalfMade());
            if (making) {
                SpriteRenderer.instance.drawGeneric(view.pipeline.PREPARE);
            }
            // A mode in which the picture draws the game's own view keeps what it is drawn with while that view is not
            // being drawn, a dead player's among them: let go, it would all be made again at once, and let go again a minute later.
            if (view.characters.wanted || gettingReady || making || overheadAsked) {
                held = true;
                offSince = now;
            } else if (held && now - offSince > LET_GO_AFTER_NANOS) {
                letGo();
                SpriteRenderer.instance.drawGeneric(view.pipeline.LET_GO);
            }
            return;
        }

        float zoom = Core.getInstance().getZoom(player.getIndex());
        holdTheCursorAhead(player, zoom);
        float floorUp = player.getZ() * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(player.getX(), player.getY(), player.getZ());
        float height = eyeHeight(player);
        world.look(IsoWorld.instance.currentCell, (int) Math.floor(player.getZ()));
        float reach = (float) Math.hypot(headOffset[0], headOffset[1]);
        float share = reach > 0f ? world.roomForTheEye(player.getX(), player.getY(), headOffset[0] / reach,
                headOffset[1] / reach, reach, Tracking.settings().collisionMargin) / reach : 1f;
        eye[0] = player.getX() + headOffset[0] * share;
        eye[1] = player.getY() + headOffset[1] * share;
        // Eased from where the last frame at the eye had it. The first of them has none before it.
        eyeHigh = landed && wasLanded ? ViewModelMath.ease(eyeHigh, height, sinceLastFrame, EYE_HEIGHT_SECONDS) : height;
        BaseVehicle vehicle = player.getVehicle();
        // Up and down a kerb as legs carry the eye, and the body seen from it with it: see KerbSteps.
        float standsUp = floorUp;
        if (landed && wasLanded && vehicle == null) {
            standsUp = view.kerbSteps.step(view.worldScene.terrain, player, player.getX(), player.getY(), player.getZ(), now);
        } else {
            view.kerbSteps.lose(player);
        }
        eye[2] = standsUp + eyeHigh;
        view.headlights.gather(view.worldScene.terrain, eye[0], eye[1]);
        view.ride.note(player, vehicle, floorUp, view.worldScene.terrain, eye);
        boolean sightsUp = view.controls.sightsUp(player);
        view.fieldOfView.ease(sightsUp, sinceLastFrame);
        view.viewModel.update(player, yaw, pitch, sinceLastFrame, sightsUp, shotDistance);
        ViewCamera.clean(cleanView, eye[0], eye[1], eye[2], yaw, pitch);
        // From a seat the view tips and rolls with the vehicle, as the eye does.
        if (view.ride.tilt != null) {
            ViewCamera.tilt(cleanView, view.ride.tilt);
        }
        if (lookCaptured && vehicle == null) {
            view.centreUse.mark(cleanView, floorUp, player, IsoWorld.instance.currentCell);
        } else {
            view.centreUse.marked = Outlines.NONE;
        }
        // The head is in the view the camera flies to and from, so it lands on what is drawn next.
        view.headTracking.view(!GameTime.isGamePaused() && !player.isDead(), cleanView, yaw, view.ride.tilt, world, sightsUp,
                vehicle == null && Online.leanCarriesTheShot(), view.viewModel.forwardStop(), vehicle != null, trackedView);
        // The game's beam has the clean view's heading turned by the head's share for a light, and no height: it is
        // given the height the view has with that same share, so the beam is one pose and not two.
        double beamHeading = Math.atan2(cleanView.forwardY, cleanView.forwardX) + view.headFollowLight.turn();
        float beamUp = view.headFollowLight.up(cleanView);
        float beamFlat = (float) Math.sqrt(Math.max(1f - beamUp * beamUp, 0f));
        view.carriedLight.gather(player, eye[0], eye[1], eye[2], (float) Math.cos(beamHeading) * beamFlat, (float) Math.sin(beamHeading) * beamFlat, beamUp);
        float[] carried = view.headTracking.carried();
        aimView.set(cleanView);
        aimView.eyeX += carried[0];
        aimView.eyeY += carried[1];
        aimView.eyeUp += carried[2];
        held = true;
        offSince = now;
        rehearsed = true;
        WorldScene.Frame scene = view.worldScene.next();
        if (landed) {
            scene.build(player, trackedView, aimView, view.fieldOfView.tanHalfVertical(), FieldOfView.setTanHalfVertical(), null, false);
            view.hearing.face(trackedView, 0f, 1f);
        } else {
            fly(player, scene, floorUp);
        }
        paceScene = scene;
        SpriteRenderer.instance.drawGeneric(scene);
        pictureUp = true;
        placeReticle(scene);
        if (Online.joined()) {
            view.nameTags.look(cleanView, IsoWorld.instance.currentCell, player);
        } else {
            view.nameTags.lookAway();
        }
        // The game draws its own reticle while the aim is held, and nothing while it is not.
        if (reticleX != RETICLE_HIDDEN && lookCaptured && view.viewModel.held != null && (!player.isAiming() || view.controls.turnedOnly())) {
            view.crosshair.show(ndc[0], ndc[1], view.viewModel.spread());
        } else {
            view.crosshair.hide();
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
    private void fly(IsoPlayer player, WorldScene.Frame scene, float floorUp) {
        aimOverhead(player, floorUp);
        view.flight.camera(overheadView, overheadHalfHigh / OVERHEAD_AWAY, OVERHEAD_AWAY, OVERHEAD_YAW, OVERHEAD_PITCH,
                cleanView, trackedView, yaw, pitch, view.fieldOfView.tanHalfVertical(), flightView);
        view.hearing.face(flightView, -OVERHEAD_PITCH * (1f - view.flight.share()), view.flight.share());
        if (view.flight.away() > OWN_FROM) {
            standingView.set(flightView);
            standingView.eyeX = eye[0];
            standingView.eyeY = eye[1];
            standingView.eyeUp = eye[2];
            view.ride.seated = false;
            scene.build(player, standingView, standingView, view.fieldOfView.tanHalfVertical(), FieldOfView.setTanHalfVertical(), flightView, false);
            scene.seenFrom(flightView, view.flight.tanHalfFieldOfView(), view.flight.away(), view.flight.share());
            scene.canopyWindow(canopyX, canopyY, floorUp, canopyAimed, canopyAimX, canopyAimY, canopyFaded, canopyFadedCount);
        } else {
            fromTheEye = true;
            scene.build(player, flightView, aimView, view.flight.tanHalfFieldOfView(), FieldOfView.setTanHalfVertical(), null, false);
        }
        scene.mixed(view.flight.shown());
    }

    private int treadSaid;
    private float treadUp;
    /** Where each wheel of the vehicle being driven touches the ground, x and y of each: see tread. */
    private final float[] wheelsAt = new float[SnowPrints.WHEELS * 2];
    private final Vector3f wheelAt = new Vector3f();

    /**
     * Says where the player walks or drives, for lying snow to show the marks of it: see
     * SnowPrints. Only while snow lies, or the next fall would come down already trodden, and
     * only out of doors, or pacing a room would use up the marks kept from the street.
     */
    private void tread(IsoPlayer player) {
        if (SkyState.snowDrawn() <= 0f) {
            SnowPrints.forget();
            return;
        }
        if (player == null) {
            SnowPrints.lifted(view.index);
            return;
        }
        // The square is asked of the map by where the walker or the vehicle is: someone seated in a vehicle is on no square of their own.
        BaseVehicle vehicle = player.getVehicle();
        float x = vehicle == null ? player.getX() : vehicle.getX(), y = vehicle == null ? player.getY() : vehicle.getY();
        IsoGridSquare square = IsoWorld.instance.currentCell.getGridSquare((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(player.getZ()));
        if (player.isDead() || square == null || !square.isOutside()) {
            SnowPrints.lifted(view.index);
        } else {
            treadUp = player.getZ() * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(x, y, player.getZ());
            float floorUp = player.getZ() * ViewCamera.LEVEL_HEIGHT;
            if (vehicle == null) {
                // In first person the body faces the way the mouse turned the view, the head's turn
                // not counted. The game turns its character round to walk backwards, which the
                // view does not show: a step back left a print pointing back.
                Vector2 facing = player.getForwardDirection();
                float way = on ? yaw : (float) Math.atan2(facing.y, facing.x);
                SnowPrints.walked(view.index, x, y, (float) Math.cos(way), (float) Math.sin(way), floorUp);
                if (TRACE && (treadSaid & 255) == 0) {
                    Log.info(String.format(Locale.ROOT, "snow marks: a foot points %.0f degrees, the character faces %.0f, the view %.0f",
                            Math.toDegrees(way), Math.toDegrees(Math.atan2(facing.y, facing.x)), Math.toDegrees(yaw)));
                }
            } else {
                // A wheel's offset is in the vehicle's own frame, as its physics body has it: getWorldPos puts it on the map.
                VehicleScript script = vehicle.getScript();
                int wheels = Math.min(script.getWheelCount(), SnowPrints.WHEELS);
                for (int wheel = 0; wheel < wheels; wheel++) {
                    vehicle.getWorldPos(script.getWheel(wheel).getOffset(), wheelAt);
                    wheelsAt[wheel * 2] = wheelAt.x;
                    wheelsAt[wheel * 2 + 1] = wheelAt.y;
                }
                SnowPrints.drove(view.index, wheelsAt, wheels, floorUp);
                if (TRACE && (treadSaid & 255) == 0) {
                    StringBuilder said = new StringBuilder("snow marks: wheels of ").append(vehicle.getScriptName()).append(" at (")
                            .append(String.format(Locale.ROOT, "%.2f, %.2f)", x, y));
                    for (int wheel = 0; wheel < wheels; wheel++) {
                        said.append(String.format(Locale.ROOT, ", %s%s (%.2f, %.2f)", script.getWheel(wheel).getId(), script.getWheel(wheel).front ? " front" : "",
                                wheelsAt[wheel * 2], wheelsAt[wheel * 2 + 1]));
                    }
                    Log.info(said.toString());
                }
            }
        }
        if (TRACE && (treadSaid++ & 255) == 0) {
            Log.info("snow marks: " + SnowPrints.said() + (square == null ? ", on no square" : square.isOutside() ? ", out of doors" : ", indoors")
                    + (vehicle == null ? ", on foot" : ", in a vehicle") + String.format(Locale.ROOT, " at (%.2f, %.2f) on ground %.3f up, level %.3f", x, y, treadUp, player.getZ()));
        }
    }

    /**
     * Getting into a vehicle in first person is sitting down facing the way the seat does: left
     * alone the view keeps the way the character faced on foot. From then on the view turns with
     * the vehicle, as a head does with the seat under it, and the mouse turns it from there. In
     * the game's own view nothing changes.
     */
    private void rideAlong(IsoPlayer player) {
        BaseVehicle vehicle = player == null ? null : player.getVehicle();
        if (vehicle != null) {
            float heading = view.ride.heading(vehicle);
            if (on) {
                setLook(wasRiding ? yaw + heading - rideHeading : heading, wasRiding ? pitch : 0f);
            }
            rideHeading = heading;
        }
        wasRiding = vehicle != null;
    }

    private void notePace(long now, long frameNanos) {
        if (!on) {
            Quality.rest(view.index);
            paceWait = PACE_FIRST_NANOS;
            paceSince = now;
            paceFrames = 0;
            paceWorst = 0;
            return;
        }
        paceFrames++;
        paceWorst = Math.max(paceWorst, frameNanos);
        // Frames that make the mod's pictures, as a level that needs them is first drawn, are slower for that and not for the level.
        if (landed && !GameTime.isGamePaused() && !view.pipeline.contentHalfMade()) {
            PerformanceSettings performance = PerformanceSettings.instance;
            Quality.frame(view.index, now, performance.isFramerateUncapped() ? 0f : performance.getFramerate());
        } else {
            Quality.rest(view.index);
        }
        if (frameNanos > LONG_FRAME_NANOS && paceScene != null) {
            if (now - longFrameLoggedNanos < LONG_FRAME_LOG_INTERVAL_NANOS) {
                longFramesUnsaid++;
            } else {
                Log.info(String.format(Locale.ROOT, "first person long frame: %.0f ms, %s%s", frameNanos / 1e6, paceScene.timing(),
                        longFramesUnsaid == 0 ? "" : ", and " + longFramesUnsaid + " more long frames since the last of these lines"));
                longFrameLoggedNanos = now;
                longFramesUnsaid = 0;
            }
        }
        if (now - paceSince > paceWait) {
            Runtime memory = Runtime.getRuntime();
            Log.info(String.format(Locale.ROOT, "first person pace: %.1f ms a frame, worst %.1f, heap %d of %d MB, %s",
                    (now - paceSince) / 1e6 / paceFrames, paceWorst / 1e6, (memory.totalMemory() - memory.freeMemory()) >> 20,
                    memory.maxMemory() >> 20, paceScene == null ? "no scene yet" : paceScene.describe()));
            if (TRACE) {
                Log.info(String.format(Locale.ROOT, "first person memory taken a frame: %.1f KB building, %.1f KB drawing",
                        paceBuiltBytes / 1024.0 / paceFrames, view.worldScene.takeDrawnBytes() / 1024.0 / paceFrames));
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
    private void holdTheCursorAhead(IsoPlayer player, float zoom) {
        float cursorAtX = player.getAimOriginPosX() + (float) Math.cos(yaw) * CURSOR_DISTANCE;
        float cursorAtY = player.getAimOriginPosY() + (float) Math.sin(yaw) * CURSOR_DISTANCE;
        float aimHeight = player.getAimOriginPosZ();
        cursorX = Math.round(IsoUtils.XToScreenExact(cursorAtX, cursorAtY, aimHeight, 0) / zoom);
        cursorY = Math.round(IsoUtils.YToScreenExact(cursorAtX, cursorAtY, aimHeight, 0) / zoom);
    }

    /** Whether the world round the player has been built, seen or unseen, since the view last gave back what it held. */
    private boolean rehearsed;
    private int rehearsals;

    /**
     * Builds the world as first person would see it from where the player stands, a few
     * milliseconds a frame, draws none of it and lets it go. The first build of a session reads
     * the mod's models out of its jar, reads every sprite's shape for the first time and runs
     * code Java has not yet made fast: left to the frame that switches to first person, the
     * chunks round the player alone held that frame for a fifth of a second.
     */
    private void rehearse(IsoPlayer player) {
        float x = player.getX(), y = player.getY(), level = player.getZ();
        Vector2 facing = player.getForwardDirection();
        ViewCamera.clean(standingView, x, y, level * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(x, y, level) + eyeHeight(player),
                (float) Math.atan2(facing.y, facing.x), 0f);
        view.headlights.gather(view.worldScene.terrain, x, y);
        float facingLength = Math.max((float) Math.hypot(facing.x, facing.y), 1e-3f);
        view.carriedLight.gather(player, standingView.eyeX, standingView.eyeY, standingView.eyeUp, facing.x / facingLength, facing.y / facingLength, 0f);
        WorldScene.Frame scene = view.worldScene.next();
        view.worldScene.rehearsing = true;
        try {
            scene.build(player, standingView, standingView, view.fieldOfView.tanHalfVertical(), FieldOfView.setTanHalfVertical(), null, false);
        } finally {
            view.worldScene.rehearsing = false;
        }
        rehearsals++;
        if (scene.waiting() == 0) {
            rehearsed = true;
            Log.info(who() + "first person world rehearsed over " + rehearsals + " frames");
        }
    }

    /**
     * Draws the game's own view with the picture: the same world from the same place, at the
     * game's own angle and zoom, so that everything is where the game has it on the screen and
     * the pointer still points at what it is on. The game's view has no perspective. A camera
     * a long way off with a very narrow field of view has next to none, and everything the
     * picture does works with it as it stands.
     */
    private void drawOverhead(IsoPlayer player) {
        float x = player.getX(), y = player.getY(), level = player.getZ();
        float floorUp = level * ViewCamera.LEVEL_HEIGHT;
        aimOverhead(player, floorUp);
        ViewCamera.clean(standingView, x, y, floorUp + view.worldScene.terrain.lift(x, y, level) + eyeHeight(player), OVERHEAD_YAW, OVERHEAD_PITCH);
        if (TRACE && (overheadSaid & 127) == 1) {
            int index = player.getIndex();
            ViewCamera.project(overheadView, overheadHalfHigh / OVERHEAD_AWAY, (float) IsoCamera.getScreenWidth(index) / IsoCamera.getScreenHeight(index),
                    x, y, floorUp + view.worldScene.terrain.lift(x, y, level), ndc);
            Log.info(String.format(Locale.ROOT, "overhead: the picture has the player's feet at (%.1f, %.1f), lift %.3f",
                    (ndc[0] * 0.5f + 0.5f) * IsoCamera.getScreenWidth(index), (0.5f - ndc[1] * 0.5f) * IsoCamera.getScreenHeight(index),
                    view.worldScene.terrain.lift(x, y, level)));
        }
        view.headlights.gather(view.worldScene.terrain, x, y);
        Vector2 facing = player.getForwardDirection();
        float facingLength = Math.max((float) Math.hypot(facing.x, facing.y), 1e-3f);
        view.carriedLight.gather(player, standingView.eyeX, standingView.eyeY, standingView.eyeUp, facing.x / facingLength, facing.y / facingLength, 0f);
        view.ride.seated = false;
        world.look(IsoWorld.instance.currentCell, (int) Math.floor(level));
        WorldScene.Frame scene = view.worldScene.next();
        // The game's own view shows the ground out to twice the picture's half height along the way it looks, which is
        // 30 degrees down, and to its half width across, and each floor up a little further along.
        int screen = player.getIndex();
        float halfWide = overheadHalfHigh * IsoCamera.getScreenWidth(screen) / IsoCamera.getScreenHeight(screen);
        float flat = (float) Math.hypot(overheadView.forwardX, overheadView.forwardY);
        scene.seenOnly(overheadView.eyeX + overheadView.forwardX * OVERHEAD_AWAY, overheadView.eyeY + overheadView.forwardY * OVERHEAD_AWAY,
                overheadView.forwardX / flat, overheadView.forwardY / flat, halfWide, (float) (overheadHalfHigh / Math.sin(-OVERHEAD_PITCH)),
                (float) (ViewCamera.LEVEL_HEIGHT / Math.tan(-OVERHEAD_PITCH)));
        scene.build(player, standingView, standingView, view.fieldOfView.tanHalfVertical(), FieldOfView.setTanHalfVertical(), overheadView, true);
        scene.seenFrom(overheadView, overheadHalfHigh / OVERHEAD_AWAY, OVERHEAD_AWAY, 0f);
        scene.canopyWindow(canopyX, canopyY, floorUp, canopyAimed, canopyAimX, canopyAimY, canopyFaded, canopyFadedCount);
        paceScene = scene;
        SpriteRenderer.instance.drawGeneric(scene);
        pictureUp = true;
    }

    /**
     * Puts the overhead camera where the game's own view is drawn from: looking at the place
     * that is at the middle of the screen, at the height the player's floor is drawn at.
     */
    private void aimOverhead(IsoPlayer player, float floorUp) {
        int index = player.getIndex();
        float zoom = Core.getInstance().getZoom(index);
        float x = player.getX(), y = player.getY(), level = player.getZ();
        float atX = IsoUtils.XToScreenExact(x, y, level, 0) / zoom, atY = IsoUtils.YToScreenExact(x, y, level, 0) / zoom;
        float eastX = IsoUtils.XToScreenExact(x + 1f, y, level, 0) / zoom - atX, eastY = IsoUtils.YToScreenExact(x + 1f, y, level, 0) / zoom - atY;
        float southX = IsoUtils.XToScreenExact(x, y + 1f, level, 0) / zoom - atX, southY = IsoUtils.YToScreenExact(x, y + 1f, level, 0) / zoom - atY;
        float wide = IsoCamera.getScreenWidth(index), high = IsoCamera.getScreenHeight(index);
        // The place on the player's floor that is at the middle of the screen.
        ViewCamera.underScreen(x, y, atX, atY, eastX, eastY, southX, southY, wide / 2f, high / 2f, onFloor);
        float middleX = onFloor[0], middleY = onFloor[1];
        // A tile's diagonal lies along the screen: its length there over its length on the map.
        float pixelsToTile = (float) (Math.hypot(eastX - southX, eastY - southY) / Math.sqrt(2.0));
        overheadHalfHigh = high / 2f / pixelsToTile;
        // The game opens its window through trees about the middle of its screen less the
        // camera's lead towards the pointer, so about the player, and while a firearm is aimed
        // about the pointer as well. Either is a place on the screen, put on the player's floor.
        PlayerCamera lead = IsoCamera.cameras[index];
        CanopyWindow.playerMaskAt(wide, high, lead.rightClickX, lead.rightClickY, zoom, maskAt);
        ViewCamera.underScreen(x, y, atX, atY, eastX, eastY, southX, southY, maskAt[0], maskAt[1], onFloor);
        canopyX = onFloor[0];
        canopyY = onFloor[1];
        canopyAimed = player.isAnyAimKeyDown();
        if (canopyAimed) {
            ViewCamera.underScreen(x, y, atX, atY, eastX, eastY, southX, southY, AimingReticle.getX(index) / zoom, AimingReticle.getY(index) / zoom, onFloor);
            canopyAimX = onFloor[0];
            canopyAimY = onFloor[1];
        }
        // The trees the game is fading or has faded: those it looks at for either mask, at any
        // distance from the player, nearest each mask's middle first.
        canopyFadedCount = 0;
        canopyOver = 0;
        gatherFaded(canopyX, canopyY, CanopyWindow.PLAYER_HALF);
        if (canopyAimed) {
            gatherFaded(canopyAimX, canopyAimY, CanopyWindow.POINTER_HALF);
        }
        if ((canopyOver > 0) != canopyOverSaid) {
            canopyOverSaid = canopyOver > 0;
            Log.info(canopyOverSaid ? "overhead: " + canopyOver + " more trees the game draws see-through than the " + CanopyWindow.TREES
                    + " the picture is told of: those further from the window are drawn whole" : "overhead: the picture is told of every tree the game draws see-through again");
        }
        if (TRACE && (overheadSaid++ & 127) == 0) {
            Log.info(String.format(Locale.ROOT, "overhead: player on screen at (%.1f, %.1f) of %.0fx%.0f, zoom %.2f, east (%.2f, %.2f) south (%.2f, %.2f), camera offset (%.1f, %.1f), middle of screen at tile (%.2f, %.2f), player (%.2f, %.2f, %.2f)",
                    atX, atY, wide, high, zoom, eastX, eastY, southX, southY, IsoCamera.getOffX(), IsoCamera.getOffY(), middleX, middleY, x, y, level));
        }
        ViewCamera.clean(overheadView, 0f, 0f, 0f, OVERHEAD_YAW, OVERHEAD_PITCH);
        overheadView.eyeX = middleX - overheadView.forwardX * OVERHEAD_AWAY;
        overheadView.eyeY = middleY - overheadView.forwardY * OVERHEAD_AWAY;
        overheadView.eyeUp = floorUp - overheadView.forwardUp * OVERHEAD_AWAY;
    }

    /** Adds the trees the game is fading under a mask about that place on the floor, ring by ring out from it, each tree once. */
    private void gatherFaded(float middleX, float middleY, int halfTexels) {
        IsoCell cell = IsoWorld.instance.currentCell;
        int fromX = (int) Math.floor(middleX), fromY = (int) Math.floor(middleY), reach = CanopyWindow.reach(halfTexels);
        for (int ring = 0; ring <= reach; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                int step = dy == -ring || dy == ring ? 1 : Math.max(2 * ring, 1);
                for (int dx = -ring; dx <= ring; dx += step) {
                    int squareX = fromX + dx, squareY = fromY + dy;
                    if (!CanopyWindow.underMask(squareX, squareY, middleX, middleY, halfTexels)) {
                        continue;
                    }
                    IsoGridSquare square = cell.getGridSquare(squareX, squareY, 0);
                    IsoTree tree = square == null ? null : square.getTree();
                    if (tree == null || tree.fadeAlpha >= 1f || gathered(squareX, squareY)) {
                        continue;
                    }
                    if (canopyFadedCount == CanopyWindow.TREES) {
                        canopyOver++;
                        continue;
                    }
                    canopyFaded[3 * canopyFadedCount] = squareX;
                    canopyFaded[3 * canopyFadedCount + 1] = squareY;
                    canopyFaded[3 * canopyFadedCount + 2] = tree.fadeAlpha;
                    canopyFadedCount++;
                }
            }
        }
    }

    private boolean gathered(int x, int y) {
        for (int i = 0; i < canopyFadedCount; i++) {
            if (canopyFaded[3 * i] == x && canopyFaded[3 * i + 1] == y) {
                return true;
            }
        }
        return false;
    }

    /**
     * The reticle marks a point, not a direction: where the clean aim, from where the shot
     * starts, first meets the world this frame. With the head leaned the picture is drawn from
     * another eye, and that point is no longer straight ahead of it. With nothing in reach the
     * aim is at infinity and only its direction is left to project.
     */
    private void placeReticle(WorldScene.Frame scene) {
        pictureTanHalf = scene.tanHalfFieldOfView();
        IsoPlayer player = view.player();
        float reach = Controls.aimReach(player);
        shotDistance = world.shotDistance(aimView, view.worldScene.terrain, player.getZ() * ViewCamera.LEVEL_HEIGHT, reach,
                scene.characterX, scene.characterY, scene.characterBase, scene.characters);
        // The game looks for a shot's target along a line through this point. With nothing
        // in reach it is a point as far along the view as anything could have been. The
        // picture raises the land and the game's map is flat, so the point's height is
        // taken above the flat map, in floor levels.
        float aimed = shotDistance == Float.POSITIVE_INFINITY ? reach : shotDistance;
        float aimedX = aimView.eyeX + aimView.forwardX * aimed, aimedY = aimView.eyeY + aimView.forwardY * aimed;
        float aimedUp = aimView.eyeUp + aimView.forwardUp * aimed;
        // The game finds the target down a ray from its overhead camera through this point. Where
        // the aim meets a character's outline the point is in front of the body, and at the height
        // of the head that ray passes over the shoulder: the game had no target for most shots
        // aimed at a head (measured: none of five at four tiles with the hunting rifle). On the bone
        // the aim is on, the ray goes through that part of the body.
        IsoGameCharacter aimedAt = characterAimedAt(scene);
        if (aimedAt != null && !(aimedAt instanceof IsoAnimal) && onBone.part(aimView, view.kerbSteps.up(view.worldScene.terrain, aimedAt, aimedAt.getX(), aimedAt.getY(), aimedAt.getZ()), aimedAt) != HitPart.NONE) {
            aimedX = onBone.boneX;
            aimedY = onBone.boneY;
            aimedUp = onBone.boneUp;
        }
        float level = player.getZ();
        float aimedLevel = (aimedUp - view.worldScene.terrain.lift(aimedX, aimedY, level)) / ViewCamera.LEVEL_HEIGHT;
        view.controls.aimAt(aimedX, aimedY, aimedLevel);
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

        float aspect = view.worldScene.drawnAspect();
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
        int index = view.player().getIndex();
        reticleX = Math.round((ndc[0] * 0.5f + 0.5f) * IsoCamera.getScreenWidth(index));
        reticleY = Math.round((0.5f - ndc[1] * 0.5f) * IsoCamera.getScreenHeight(index));
    }

    /** Half the height of the picture's field of view, as a tangent, as it was last drawn. */
    private float pictureTanHalf;

    /**
     * Where a place in the world falls in the picture as it was last drawn, the head's turn
     * and lean included. False if it is behind the eye or no picture has been drawn.
     */
    boolean inPicture(float x, float y, float up, float[] ndc) {
        float aspect = view.worldScene.drawnAspect();
        return aspect != 0f && pictureTanHalf != 0f && ViewCamera.project(trackedView, pictureTanHalf, aspect, x, y, up, ndc);
    }

    /** The character the clean aim meets before anything else this frame, or null. */
    private IsoGameCharacter characterAimedAt(WorldScene.Frame scene) {
        for (int i = 0; i < scene.characters; i++) {
            if (WorldQuery.cylinderDistance(aimView, scene.characterX[i], scene.characterY[i], scene.characterBase[i]) == shotDistance) {
                return scene.characterOf[i];
            }
        }
        return null;
    }

    private void hideReticle() {
        reticleX = RETICLE_HIDDEN;
        reticleY = RETICLE_HIDDEN;
    }

    /**
     * Lets go of the mouse or takes it back. The inventory key is handled here and kept from
     * the game's own handler, which only flips the panels between shown and hidden: they are
     * shown folded from the start, so its first press would hide them.
     */
    private void point() {
        Core.KeyBinding inventory = Core.getInstance().getKeyBinding(INVENTORY_BINDING);
        boolean inventoryKey = pressed(inventory.keyValue()) | pressed(inventory.altKey());
        boolean escape = GameKeyboard.isKeyPressed(ESCAPE_KEY);
        if (escape && pointer.byKey()) {
            // Or the same press would go on to open the game's menu.
            GameKeyboard.eatKeyPress(ESCAPE_KEY);
        }
        String was = TRACE ? pointer.describe() : null;
        int panels = Panels.open(view.index);
        long now = System.nanoTime();
        if (Mouse.isButtonDown(AIM_BUTTON)) {
            aimedAt = now;
        }
        if (now - aimedAt < AFTER_AIM_NANOS && !pointer.byKey()) {
            pointer.passOver(panels);
        }
        PointerMode.Inventory inventoryPanels = pointer.update(inventoryKey, escape, Mouse.isButtonDown(POINT_BUTTON), panels);
        if (inventoryPanels != PointerMode.Inventory.LEAVE) {
            Panels.showInventory(view.index, inventoryPanels == PointerMode.Inventory.OPEN);
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
    boolean useAtCentre() {
        IsoPlayer player = view.player();
        if (player.getVehicle() != null) {
            return false;
        }
        float floorUp = player.getZ() * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(player.getX(), player.getY(), player.getZ());
        CentreUse.Outcome outcome = view.centreUse.press(cleanView, floorUp, player, IsoWorld.instance.currentCell, view.onMouse());
        if (outcome == CentreUse.Outcome.CONTAINER) {
            pointer.open();
        }
        if (TRACE) {
            Log.info("interact at the middle of the view: " + outcome + " on " + Thread.currentThread().getName());
        }
        return outcome != CentreUse.Outcome.NOTHING;
    }

    /** Whether a key of a binding went down this frame. The game eats the press, so its own handlers never see the key come up. */
    private boolean pressed(int key) {
        if (key == 0 || !GameKeyboard.isKeyPressed(key)) {
            return false;
        }
        GameKeyboard.eatKeyPress(key);
        return true;
    }

    /**
     * A controller's aiming stick turns the view, as the mouse does for the player who has
     * one. The game hands the stick over with its own dead zone taken off, and says whether
     * it is the character's at all: while the controller is working one of the game's panels
     * it is not. The turn goes by the square of how far the stick is pushed, so a small push
     * is a fine aim and a full one a quick turn, and by the zoom, as the mouse's does.
     */
    private void stickLook(IsoPlayer player, float seconds) {
        if (!player.getCharacterInputComponent().isJoypadAimingAxisApplied()) {
            return;
        }
        CharacterJoypadAxis2dBinding.Aiming.getValue(player.getJoypadBind(), stick);
        float pushed = Math.min((float) Math.hypot(stick.x, stick.y), 1f);
        float turn = pushed * seconds * view.fieldOfView.zoom() * view.scope.turn();
        setLook(yaw + stick.x * turn * STICK_YAW, pitch - stick.y * turn * STICK_PITCH);
    }

    /**
     * Called with where the game has this player's aim across their part of the screen. For a
     * player with the mouse that is the mouse, which is held ahead of the character along the
     * view already (cursorX). A controller's is a place the game works out from the stick, and
     * the same point ahead is answered in its place, so the game's own aim, which runs from
     * the character to it, is the view direction for a controller too.
     */
    int heldAcross(int stock) {
        IsoPlayer player = view.player();
        return lookCaptured() && player != null && View.onController(player) ? cursorX : stock;
    }

    /** The same, down their part of the screen. */
    int heldDown(int stock) {
        IsoPlayer player = view.player();
        return lookCaptured() && player != null && View.onController(player) ? cursorY : stock;
    }

    /** Called where the game's reticle and aim cursor read the screen column to draw at. */
    public int reticleX(int stock) {
        return landed ? view.scope.hidesReticle() || view.controls.turnedOnly() ? RETICLE_HIDDEN : reticleX : stock;
    }

    /** Called where the game's reticle and aim cursor read the screen row to draw at, counted from the top. */
    public int reticleY(int stock) {
        return landed ? view.scope.hidesReticle() || view.controls.turnedOnly() ? RETICLE_HIDDEN : reticleY : stock;
    }

    private float eyeHeight(IsoPlayer player) {
        if (view.modelHead.eyeOffset(player, yaw, headOffset)) {
            return headOffset[2];
        }
        headOffset[0] = 0f;
        headOffset[1] = 0f;
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
    public boolean coversTheGamesView() {
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
    public boolean holdsMouse() {
        return !MouseGrab.ISOLATED && (MouseGrab.applied || MouseGrab.keeping);
    }

    /** Whether the mouse turns the view: a player on a controller turns it with the stick, and the mouse is the game's. */
    private boolean mouseLooks() {
        return lookCaptured && view.onMouse();
    }

    /** Called at the end of the game's Mouse.update with the cursor column it read. */
    public int cursorX(int raw) {
        if (!mouseLooks()) {
            haveRawX = false;
            return raw;
        }
        if (MouseGrab.moved.get() > 0 || System.nanoTime() - capturedNanos < LOOK_SETTLES_NANOS) {
            haveRawX = false;
        }
        if (haveRawX && !MouseGrab.ISOLATED) {
            float turn = (raw - lastRawX) * RADIANS_PER_PIXEL * view.fieldOfView.zoom() * view.scope.turn();
            mouseTurned += turn;
            mouseMost = Math.max(mouseMost, Math.abs(raw - lastRawX));
            setLook(yaw + turn, pitch);
        }
        lastRawX = raw;
        haveRawX = true;
        return cursorX;
    }

    /** Called at the end of the game's Mouse.update with the cursor row it read, counted from the top. */
    public int cursorY(int raw) {
        if (!mouseLooks()) {
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
            setLook(yaw, pitch - (raw - lastRawY) * RADIANS_PER_PIXEL * view.fieldOfView.zoom() * view.scope.turn());
        }
        lastRawY = raw;
        haveRawY = true;
        return cursorY;
    }

    /**
     * The way the view faces along the map, the head's turn in it, as it was last made: not of
     * length one, and next to nothing while the view is straight up or down. Logic thread.
     */
    void facing(float[] out) {
        out[0] = trackedView.forwardX;
        out[1] = trackedView.forwardY;
    }

    /**
     * Called with the way the game takes the player to be looking when it works out what
     * they can see. The game hides whoever is outside that cone, and its cone is the body's:
     * walking sideways the body faces the way it walks, and a zombie straight ahead of the
     * view, an arm's length off, was gone and back again as the body turned. In first person
     * what the player can see is what the view faces.
     */
    public Vector2 sightVector(Vector2 look) {
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
    public Vector2 moveVector(Vector2 move) {
        traceMoveX = move.x;
        traceMoveY = move.y;
        // The keys are the view's from the moment first person is asked for, not from the landing: see Stride.
        if (!on) {
            return move;
        }
        IsoPlayer player = view.player();
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

    private void trace(IsoPlayer player, WorldScene.Frame scene, float zoom) {
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
                cursorX, cursorY, zoom, IsoCamera.getOffX(), IsoCamera.getOffY(), lookCaptured, pointer.describe(), view.headTracking.describe(),
                reticleX, reticleY, ndc[0], ndc[1], shotDistance, trackedView.eyeX, trackedView.eyeY, trackedView.eyeUp,
                scene.describe(), view.controls.describe(), view.viewModel.describe()));
    }
}
