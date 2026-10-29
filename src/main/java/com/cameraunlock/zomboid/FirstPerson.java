package com.cameraunlock.zomboid;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.joml.Vector3f;
import se.krka.kahlua.vm.KahluaTable;
import zombie.inventory.types.HandWeapon;
import zombie.GameTime;
import zombie.ai.State;
import zombie.ai.states.ClimbOverFenceState;
import zombie.ai.states.ClimbOverWallState;
import zombie.ai.states.ClimbThroughWindowState;
import zombie.ai.states.PlayerFallDownState;
import zombie.ai.states.PlayerGetUpState;
import zombie.ai.states.PlayerHitReactionPVPState;
import zombie.ai.states.PlayerHitReactionState;
import zombie.ai.states.PlayerKnockedDown;
import zombie.ai.states.PlayerOnGroundState;
import zombie.characters.CharacterJoypadAxis2dBinding;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoZombie;
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
import zombie.iso.IsoChunk;
import zombie.iso.IsoChunkMap;
import zombie.iso.IsoMetaGrid;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
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
    /** How long a save that loads in the game's own view says which key turns first person on. */
    private static final long HINT_FOR_NANOS = 10_000_000_000L;
    private String hint;
    private long hintAt;
    /** The hint said after the first, and when: HINT_GAP_NANOS after the first goes. */
    private String laterHint;
    private long laterHintAt;
    private static final long HINT_GAP_NANOS = 3_000_000_000L;
    private long saidAt;
    private String said;
    /** The step of VideoMemory's the screen has said. */
    private int memoryStepSaid;
    /** What this frame's world pass left for the text pass to write, or null. */
    private String shown;
    /** How far off the overhead camera stands, in tiles: far enough that the picture has next to no perspective. */
    private static final float OVERHEAD_AWAY = 6000f;
    private static final float OVERHEAD_YAW = (float) (-0.75 * Math.PI), OVERHEAD_PITCH = (float) Math.toRadians(-30);
    /** How far ahead on the ground the torch's beam meets it in the overhead view, in tiles: held level, as the eye looks, it lit walls at chest height and next to none of the ground. */
    private static final float OVERHEAD_BEAM_LANDS = 4f;
    private boolean overhead;
    /** The overhead view is asked to be the mod's and is the game's until what it is drawn with is made. */
    private boolean overheadWaited;
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
     * The middle mouse button, a click of which brings the pointer out and the next puts it
     * back. Nothing in the game's default bindings uses it, and every letter key is taken.
     */
    private static final int POINT_BUTTON = 2;
    private boolean pointButtonWasDown, aimButtonWasDown;
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

    /** The cosine of the angle between the way the movement keys ask for and the view: 1 ahead, -1 straight back. */
    private float keysAhead = 1f;
    /** Whether the movement keys ask for a way the view faces: ahead, or ahead and to one side. */
    private boolean walkingAhead;

    float keysAhead() {
        return keysAhead;
    }

    /** Whether the character is being walked the way the view faces, or ahead and to one side of it. */
    boolean walkingAhead() {
        return walkingAhead && lookCaptured();
    }

    private static final float EYE_HEIGHT_STANDING = 1.6f;
    private static final float EYE_HEIGHT_SNEAKING = 1.1f;
    private static final float EYE_HEIGHT_SITTING = 0.9f;
    /** The eye goes from one of those heights to another over a few of these, where set at once the whole picture cut half a tile in a frame. */
    private static final float EYE_HEIGHT_SECONDS = 0.1f;
    private float eyeHigh;
    private final double[] eye = new double[3];
    private final double[] ledEye = new double[2];
    private final float[] headOffset = new float[3];
    private final float[] headTurn = new float[9], headJoint = new float[3], ledTilt = new float[9];
    /** The clean view as the mouse has it this frame, before the head takes any of it: the aim is made from it. */
    private final ViewCamera.Pose mouseView = new ViewCamera.Pose();
    /** Where the tracked view's eye is from the aim's, and the flying camera's: see ViewCamera.between. */
    private final float[] eyeFromAim = new float[3], flightFromAim = new float[3];
    /** Whether the game's own animation has the body this frame: see BodyLed. */
    private boolean bodyLeads;
    /** Whether the whole view is the head's while the game's animation has the body, and the mouse turns nothing. */
    private boolean bodyWhole;
    /** The character the body led last frame was, so a new one after a death starts with the mouse's view. */
    private IsoPlayer ledPlayer;
    /** The zombie the dead player's corpse rose as, found again each frame in lead: see risenOf. */
    private IsoZombie risen;
    /** That zombie's id on the last frame, or -1 while there was none, for the log. */
    private int risenId = -1;
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
    /** How long a frame takes, smoothed over a few dozen. */
    private float frameSeconds = 1f / 60f;
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
    /**
     * The line from the eye the picture is drawn from through the reticle, set with it: the
     * Interact key acts on, and the picture outlines, what is drawn under the reticle. Only its
     * eye and its forward are set, and only while reticleOnScreen.
     */
    private final ViewCamera.Pose reticleLine = new ViewCamera.Pose();
    private boolean reticleOnScreen;
    /**
     * Whether the reticle falls inside the picture. An aim near the eye with the head turned far
     * from it projects in front of the eye but far off the edge, and is still reticleOnScreen.
     */
    private boolean reticleInPicture;

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

    /** Whether first person is on or asked for and being made ready. */
    boolean wanted() {
        return on || gettingReady;
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
     * First person has been asked for and what it is drawn with is being made. It is made while
     * the save loads (whileLoading) and in the first frames of play, a piece a frame, and kept
     * until the save is left, so the key finds it made: this is for a key pressed before that
     * is done, or after the graphics mode has asked for more. Made on the frame of the switch
     * it held that frame for seconds.
     */
    private boolean gettingReady;
    private long readySince;
    /** Whether the view holds anything on the graphics card that letting go would give back. */
    private boolean held;
    /** A PC on which the making and the rehearsal take longer than this goes on to first person without the rest of them. */
    private static final long READY_WITHIN_NANOS = 4_000_000_000L;
    /** How long from being asked for first person waits for the far land round the eye, in all: without it the picture ends at the loaded map in fog. */
    private static final long FAR_WITHIN_NANOS = FarLand.waitNanos(20_000L);

    /**
     * Whether this player's view at load (Tracking.firstPersonRemembered) is still to be put back:
     * from a save's start, or a player's joining, to the first frame first person is allowed or the
     * key is pressed.
     */
    private boolean viewAtLoadOwed = true;

    void toggle() {
        IsoPlayer player = view.player();
        if (!on && !allowed(player)) {
            Log.info(who() + "first person stays off: " + whyNot(player));
            if (view.pipeline.failure() != null) {
                said = cannotBeDrawn();
                saidAt = System.nanoTime();
            }
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
        if (!on && (view.pipeline.unprepared() || view.pipeline.contentWaited() || !rehearsed && !overhead() || !view.worldScene.farLand.ready())) {
            gettingReady = true;
            readySince = System.nanoTime();
            Log.info(who() + "first person asked for: what it is drawn with is being made");
            return;
        }
        switchView(player);
    }

    /**
     * Called once a frame while the save's world is loaded and the game has still to begin it:
     * makes the next piece of what first person is drawn with and the land beyond the loaded
     * map round the player, so that neither waits for the first switch to first person. The
     * game's loading screen is no frame of the sprite renderer's: both go by the render
     * thread's own queue.
     *
     * @return whether shader programs are still to be made, which the save is not begun without
     */
    boolean whileLoading(IsoPlayer player) {
        if (view.pipeline.failure() != null) {
            return false;
        }
        held = true;
        view.worldScene.tendFar(player.getX(), player.getY());
        RenderThread.queueInvokeOnRenderContext(view.pipeline.HAND_FAR::render);
        if (view.pipeline.unprepared() || view.pipeline.contentWaited()) {
            RenderThread.queueInvokeOnRenderContext(view.pipeline.PREPARE::render);
        }
        return view.pipeline.shadersWaited();
    }

    /**
     * One frame of getting ready: the next piece of what the picture is drawn with, then the
     * world built once unseen, then the land beyond the loaded map round the eye, then the
     * switch. The camera sets off with the far land and its trees already there to come into
     * sight on its way down, not arriving after it has landed.
     */
    private void getReady(IsoPlayer player, long now) {
        boolean late = now - readySince > READY_WITHIN_NANOS;
        // Made on a thread of its own, the far land comes while the rest is made.
        view.worldScene.tendFar(player.getX(), player.getY());
        // Drawing the game's own view, the picture hands it to the graphics card itself.
        if (!view.characters.wanted) {
            SpriteRenderer.instance.drawGeneric(view.pipeline.HAND_FAR);
        }
        if (!late && (view.pipeline.unprepared() || view.pipeline.contentWaited())) {
            SpriteRenderer.instance.drawGeneric(view.pipeline.PREPARE);
            return;
        }
        if (!late && !rehearsed && !view.characters.wanted && !GameTime.isGamePaused() && !player.isDead()) {
            rehearse(player);
            return;
        }
        if (!late && !view.worldScene.farLand.ready()) {
            return;
        }
        int round = view.worldScene.farLand.roundTheEye();
        if (round < FarLand.ROUND && now - readySince <= FAR_WITHIN_NANOS) {
            return;
        }
        if (late) {
            Log.info(String.format(Locale.ROOT, "%sfirst person switched on after %.0f ms of getting ready, with the far land %s",
                    who(), (now - readySince) / 1e6, view.worldScene.farLand.ready() ? "all round the eye"
                            : round == FarLand.ROUND ? "round the eye and the rest of it still coming"
                            : "not yet round the eye, so the picture ends at the loaded map until it is"));
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
        viewAtLoadOwed = true;
        SnowPrints.forget();
        WallTiles.leftSave();
        view.heldLight.forget();
        view.characters.leftSave();
        view.modeFade.giveBack("the save was left");
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
        viewAtLoadOwed = true;
        risen = null;
        view.modeFade.giveBack("player " + (view.index + 1) + " is gone");
        // After giveBack, whose rest of the head tracking marks the view as one played in.
        Tracking.gone(view.index);
        Quality.gone(view.index);
    }

    /** What the screen says when first person cannot be drawn on this PC; the log has the whole of why. */
    private static String cannotBeDrawn() {
        return GlFloor.missing() != null ? "First person needs OpenGL 3.3 with EXT_texture_array and ARB_shader_texture_lod"
                : "First person cannot be drawn on this PC: the log says why";
    }

    /** What a line in the log begins with to say whose view it is of, for any player but the first. */
    private String who() {
        return view.index == 0 ? "" : "player " + (view.index + 1) + ": ";
    }

    /** Lets go of the look out of the eye, and of the mouse where the view has it. */
    private void stopLooking() {
        risen = null;
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
            view.crosshair.hideSquare();
            view.crosshair.point(null);
            view.dragAim.aimed.forget();
            view.dragAim.ghost.clear();
            reticleOnScreen = false;
            view.nameTags.lookAway();
            view.hearing.rest();
            view.flight.cut(false);
            Quality.inFirstPerson(view.index, false);
            if (pictureUp) {
                pictureUp = false;
                RenderThread.queueInvokeOnRenderContext(view.pipeline.RELEASE::render);
            }
            if (held) {
                // A fault thrown by work out on another thread is thrown once the world is let go of: the graphics card's copy goes too.
                try {
                    letGo();
                } finally {
                    RenderThread.queueInvokeOnRenderContext(view.pipeline.LET_GO::render);
                }
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
            said = Quality.said(view.index);
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
            gap = WorldMenu.gap();
        }
        if (gap == null) {
            gap = failure;
        }
        return gap != null ? gap : view.pipeline.failure();
    }

    /**
     * Called from IngameState.renderFrameTextInternal straight after the game has written what people said
     * over this player's finished picture. The world pass is no place for it: its screen is the zoomed one, the screen's
     * width times the game's zoom, so a middle worked out there is off to one side at any zoom but 1.
     */
    void afterWorldText(int index) {
        if (shown != null) {
            TextManager.instance.DrawStringCentre(UIFont.Medium, IsoCamera.getScreenWidth(index) / 2.0, IsoCamera.getScreenHeight(index) * 0.12,
                    shown, 1.0, 1.0, 1.0, 1.0);
            shown = null;
        }
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
            // Over the world and under what the game draws over it, as the picture before was taken.
            view.modeFade.frame(gettingReady || overheadWaited || on && view.pipeline.contentWaited());
            if (Tracking.graphicsModeOwed(view.index)) {
                view.modeFade.take();
            }
            IsoPlayer player = view.player();
            SharedHeads.send(view, player);
            if (player != null && GameTime.isGamePaused()) {
                view.hearing.whilePaused(player);
            }
            if (player != null) {
                view.hearing.listen(player, view.worldScene, System.nanoTime());
            }
            long now = System.nanoTime();
            if (laterHint != null && now >= laterHintAt) {
                hint = laterHint;
                hintAt = now;
                laterHint = null;
                Log.info(who() + "the screen says: " + hint);
            }
            shown = gettingReady ? "First person: getting ready" : said != null && now - saidAt < SAID_FOR_NANOS ? said
                    : hint != null && now - hintAt < HINT_FOR_NANOS ? hint : null;
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
            viewAtLoadOwed = false;
            if (hint != null && laterHint != null) {
                laterHintAt = Math.min(laterHintAt, System.nanoTime() + HINT_GAP_NANOS);
            }
            hint = null;
            boolean asked = on || gettingReady;
            toggle();
            if ((on || gettingReady) != asked) {
                Tracking.rememberFirstPerson(view.index, !asked);
            }
        }
        int memoryStep = VideoMemory.step();
        if (memoryStep > memoryStepSaid) {
            memoryStepSaid = memoryStep;
            said = VideoMemory.shown(memoryStep);
            saidAt = System.nanoTime();
        }
        if ((pressed & Config.AIM_MODE) != 0) {
            said = Tracking.aimModeLabel();
            saidAt = System.nanoTime();
        }
        if (view.hasMouse()) {
            String told = DevKeys.poll();
            if (told != null) {
                said = told;
                saidAt = System.nanoTime();
            }
        }
        if ((pressed & Config.CYCLE_QUALITY) != 0) {
            said = view.pipeline.failure() != null ? cannotBeDrawn() : Quality.said(view.index);
            saidAt = System.nanoTime();
        }
        if ((pressed & Config.CYCLE_STYLE) != 0) {
            said = "Style: " + Style.of(view.index).label;
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
        if (viewAtLoadOwed && allowed) {
            viewAtLoadOwed = false;
            if (!on && !gettingReady && Tracking.firstPersonRemembered(view.index)) {
                Log.info(who() + "first person was on when the view was last switched: asked for again");
                toggle();
            } else if (!on && !gettingReady) {
                hint = View.onController(player) ? "Double-tap Back for first person" : "Press " + Tracking.firstPersonKeys() + " for first person";
                hintAt = System.nanoTime();
                Log.info(who() + "the save loads in the isometric view, and the screen says: " + hint);
                if (!View.onController(player)) {
                    laterHint = "Press " + Tracking.cycleQualityKeys() + " to change graphics quality. First person and isometric each have their own settings";
                    laterHintAt = hintAt + HINT_FOR_NANOS + HINT_GAP_NANOS;
                }
            }
        }
        boolean overheadAsked = !Quality.overheadPlain(view.index) && allowed;
        overhead = overheadAsked && !view.pipeline.unprepared() && !view.pipeline.contentWaited();
        overheadWaited = overheadAsked && !overhead;
        view.worldScene.level(overhead());
        rideAlong(player);
        Panels.dashboard(view.index, active() && player != null && player.getVehicle() != null);
        tread(player);
        if (player != null) {
            view.regrip.update(player, view.index);
        }
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
        frameSeconds += (Math.min(sinceLastFrame, 0.1f) - frameSeconds) * 0.05f;
        view.flight.advance(on, sinceLastFrame, !overhead);
        boolean wasFirstPerson = Quality.firstPersonInEffect(view.index);
        if (view.pipeline.failure() != null) {
            Quality.undrawable();
        }
        Quality.inFirstPerson(view.index, on || gettingReady || !view.flight.home());
        if (wasFirstPerson && !Quality.firstPersonInEffect(view.index)) {
            // Back in the isometric view, with its own graphics mode: said, so a mode not wanted there is not played on unawares.
            said = Quality.said(view.index);
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
        lead(player, sinceLastFrame);
        // The game stops drawing the world of a player who sleeps once their screen has faded, and with it this frame:
        // the look is let go of as the sleep begins, while frames still come to let go of the mouse.
        active = landed && !GameTime.isGamePaused() && !player.isDead() && !player.isAsleep();
        if (!landed || !view.onMouse()) {
            pointer.reset();
        } else if (active) {
            point();
        }
        lookCaptured = active && !pointer.on();
        if (lookCaptured && View.onController(player) && !bodyWhole) {
            stickLook(player, sinceLastFrame);
        }
        if (lookCaptured && !wasCaptured) {
            capturedNanos = now;
        }
        wasCaptured = lookCaptured;
        if (!landed) {
            landingSaid = true;
        } else if (DevBuild.ON && !landingSaid && now - landedNanos > 1_000_000_000L) {
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
        Impacts.sayGap();
        fromTheEye = landed;
        if (!flown) {
            lastX = Float.NaN;
            jumpedAt = 0;
            view.fieldOfView.rest();
            view.headTracking.follow(player != null && !GameTime.isGamePaused() && !player.isDead());
            view.viewModel.rest();
            view.crosshair.hide();
            view.crosshair.point(null);
            reticleOnScreen = false;
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
                drawOverhead(player, sinceLastFrame);
            } else if (pictureUp) {
                pictureUp = false;
                view.worldScene.setDown();
                world.look(null, 0);
                SpriteRenderer.instance.drawGeneric(view.pipeline.RELEASE);
            }
            // Whatever loading left unmade is made in play, one piece a frame, then the world is rehearsed, the far land handed over,
            // and all of it is kept until the save is left. Off, the view is there at once when asked for. A view that cannot be
            // drawn here holds nothing, so a player who leaves has nothing to give back.
            if (view.pipeline.failure() != null) {
                return;
            }
            boolean making = !gettingReady && !view.characters.wanted
                    && (view.pipeline.unprepared() || view.pipeline.contentWaited() || view.pipeline.contentHalfMade());
            if (making) {
                SpriteRenderer.instance.drawGeneric(view.pipeline.PREPARE);
            } else if (!gettingReady && !rehearsed && !view.characters.wanted && allowed && !GameTime.isGamePaused() && !player.isDead()) {
                rehearse(player);
            }
            if (!gettingReady && allowed && !view.worldScene.farLand.ready()) {
                view.worldScene.tendFar(player.getX(), player.getY());
                if (!view.characters.wanted) {
                    SpriteRenderer.instance.drawGeneric(view.pipeline.HAND_FAR);
                }
            }
            held = true;
            return;
        }

        float zoom = Core.getInstance().getZoom(player.getIndex());
        holdTheCursorAhead(player, zoom);
        float floorUp = player.getZ() * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(player.getX(), player.getY(), player.getZ());
        float height = eyeHeight(player, sinceLastFrame);
        world.look(IsoWorld.instance.currentCell, (int) Math.floor((risen != null ? risen : player).getZ()));
        float reach = (float) Math.hypot(headOffset[0], headOffset[1]);
        State state = player.getCurrentState();
        boolean climbing = state == ClimbOverFenceState.instance() || state == ClimbOverWallState.instance()
                || state == ClimbThroughWindowState.instance();
        float share = reach > 0f ? world.roomForTheEye(player.getX(), player.getY(), headOffset[0] / reach,
                headOffset[1] / reach, reach, Tracking.settings().collisionMargin, climbing) / reach : 1f;
        eye[0] = (double) player.getX() + headOffset[0] * share;
        eye[1] = (double) player.getY() + headOffset[1] * share;
        // A climb carries the head over the fence or through the window ahead of the feet: see roomForTheEye.
        if (!climbing) {
            world.holdOffWalls(eye, player.getX(), player.getY(), Tracking.settings().collisionMargin);
        }
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
        eye[2] = (double) standsUp + eyeHigh;
        view.ride.note(player, vehicle, floorUp, view.worldScene.terrain, eye);
        // After the ride is noted: the beam of the vehicle sat in comes from where it lies this frame.
        view.headlights.gather(view.worldScene.terrain, (float) eye[0], (float) eye[1], view.ride);
        // Nothing is aimed while the game's animation has the body: a zoom would close in on the head's view.
        boolean sightsUp = view.controls.sightsUp(player) && !bodyLeads;
        view.fieldOfView.ease(sightsUp, sinceLastFrame);
        // The arms are the game's own while its animation has the body: they are what fights the zombie off.
        if (bodyLeads) {
            view.viewModel.rest();
        } else {
            view.viewModel.update(player, yaw, pitch, sinceLastFrame, GameTime.isGamePaused(), sightsUp, shotDistance);
        }
        ViewCamera.clean(cleanView, eye[0], eye[1], eye[2], yaw, pitch);
        // From a seat the view tips and rolls with the vehicle, as the eye does.
        if (view.ride.tilt != null) {
            ViewCamera.tilt(cleanView, view.ride.tilt);
        }
        float[] tilt = led(player, standsUp);
        // The head is in the view the camera flies to and from, so it lands on what is drawn next.
        if (view.bodyLed.holding()) {
            view.headTracking.rest();
            trackedView.set(view.bodyLed.heldTracked);
        } else {
            view.headTracking.view(!GameTime.isGamePaused() && (!player.isDead() || bodyLeads), cleanView, yaw, tilt, world, sightsUp,
                    vehicle == null && Online.leanCarriesTheShot() && !bodyLeads, view.viewModel.forwardStop(), vehicle != null,
                    view.bodyLed.share() > 0f, trackedView);
        }
        // The game's beam has the clean view's heading turned by the head's share for a light, and no height: it is
        // given the height the view has with that same share, so the beam is one pose and not two.
        double beamHeading = Math.atan2(cleanView.forwardY, cleanView.forwardX) + view.headFollowLight.turn();
        float beamUp = view.headFollowLight.up(cleanView);
        float beamFlat = (float) Math.sqrt(Math.max(1f - beamUp * beamUp, 0f));
        view.carriedLight.gather(player, (float) eye[0], (float) eye[1], (float) eye[2], (float) Math.cos(beamHeading) * beamFlat, (float) Math.sin(beamHeading) * beamFlat, beamUp);
        view.heldLight.gather(player, world, (float) eye[0], (float) eye[1], cleanView.forwardX, cleanView.forwardY, vehicle != null);
        float[] carried = view.headTracking.carried();
        aimView.set(mouseView);
        aimView.moveEye(carried[0], carried[1], carried[2]);
        if (view.bodyLed.holding()) {
            ViewCamera.eyeFrom(aimView, trackedView, eyeFromAim);
        } else {
            // From the lean itself, not from the two places: see ViewCamera.between. Apart from
            // while the head leads, the clean view and the mouse's are the same.
            float[] alone = view.headTracking.eyeAlone();
            eyeFromAim[0] = cleanView.eyeX - mouseView.eyeX + alone[0];
            eyeFromAim[1] = cleanView.eyeY - mouseView.eyeY + alone[1];
            eyeFromAim[2] = cleanView.eyeUp - mouseView.eyeUp + alone[2];
        }
        held = true;
        rehearsed = true;
        WorldScene.Frame scene = view.worldScene.next();
        if (landed) {
            scene.build(player, trackedView, aimView, eyeFromAim, view.fieldOfView.tanHalfVertical(), FieldOfView.setTanHalfVertical(), null, false);
            view.hearing.face(trackedView, 0f, 1f);
        } else {
            fly(player, scene, floorUp);
        }
        scene.round = worldRound(player, scene, now);
        paceScene = scene;
        SpriteRenderer.instance.drawGeneric(scene);
        pictureUp = true;
        placeReticle(scene);
        aimDrag(player, scene);
        // Outlined in the next picture: this one was gathered before the reticle was placed.
        if (lookCaptured && vehicle == null && !bodyLeads && reticleOnScreen) {
            view.centreUse.mark(reticleLine, floorUp, player, IsoWorld.instance.currentCell);
        } else {
            view.centreUse.marked = Outlines.NONE;
        }
        if (Online.joined()) {
            view.nameTags.look(cleanView, IsoWorld.instance.currentCell, player);
        } else {
            view.nameTags.lookAway();
        }
        // The game draws its own reticle and aim cursor while the aim is held, and nothing while
        // it is not. Seated, a weapon is in view only with its sights up, so there is no hip to mark.
        boolean gameMarks = player.isAiming() && !view.controls.turnedOnly();
        if (reticleX != RETICLE_HIDDEN && lookCaptured && view.viewModel.held != null && player.getVehicle() == null && !gameMarks) {
            view.crosshair.show(ndc[0], ndc[1], view.viewModel.spread());
        } else if (reticleOnScreen && lookCaptured && !gameMarks) {
            view.crosshair.showDot(ndc[0], ndc[1]);
        } else {
            view.crosshair.hide();
        }
        // The shape of the picture is not known until a frame has been drawn.
        float drawnAspect = view.worldScene.drawnAspect();
        int level = (int) Math.floor(player.getZ());
        view.crosshair.point(drawnAspect == 0f ? null : Homing.pointers(player.getIndex(), trackedView, pictureTanHalf, drawnAspect,
                (x, y) -> level * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(x, y, level)));

        if (TRACE) {
            trace(player, scene, zoom);
        }
    }

    /** Tiles the player can move in one frame before it is a jump across the map, after which the game loads its map round them afresh: more than a car at 250 km/h covers in a second-long frame. */
    private static final float JUMP_TILES = 100f;
    /** Where the player was on the last frame built at the eye or on the way to it, x NaN when there was none. */
    private float lastX = Float.NaN, lastY;
    /** When the player last jumped, 0 once the world round them has come since or been waited for long enough. */
    private long jumpedAt;
    /** The longest the picture stays cut to black after a jump (Pipeline.cut). */
    private static final long JUMP_WAIT_NANOS = FarLand.waitNanos(3_000L);

    /**
     * Whether the world round the eye is there to be drawn, once the frame is built: after a
     * jump, not until every chunk of the game's map within sight is loaded, the build has left
     * none in view waiting, and the far land round the eye is on the graphics card. Until then
     * the picture has holes the fog shows through and ends at the loaded map.
     */
    private boolean worldRound(IsoPlayer player, WorldScene.Frame scene, long now) {
        float x = player.getX(), y = player.getY();
        if (!Float.isNaN(lastX) && Math.hypot(x - lastX, y - lastY) > JUMP_TILES) {
            jumpedAt = now;
        }
        lastX = x;
        lastY = y;
        if (jumpedAt == 0) {
            return true;
        }
        boolean loaded = loadedInSight(player, x, y), built = scene.waiting() == 0, far = view.worldScene.farLand.roundTheEye() == FarLand.ROUND;
        if (loaded && built && far) {
            jumpedAt = 0;
            return true;
        }
        if (now - jumpedAt > JUMP_WAIT_NANOS) {
            String missing = (loaded ? "" : ", the map within sight still loading") + (built ? "" : ", chunks in view still to build")
                    + (far ? "" : ", the far land round the eye still coming");
            Log.info(String.format(Locale.ROOT, "%sthe picture comes back from black %.0f ms after a jump with %s", who(), (now - jumpedAt) / 1e6, missing.substring(2)));
            jumpedAt = 0;
            return true;
        }
        return false;
    }

    /**
     * Whether the game has loaded the chunk the player stands in and every chunk of its map round
     * them within sight, short of the map's last two rings. A chunk off the world's map is never
     * loaded (IsoChunkMap.LoadChunkForLater), and is not waited for.
     */
    private boolean loadedInSight(IsoPlayer player, float x, float y) {
        IsoChunkMap map = IsoWorld.instance.currentCell.getChunkMap(player.getIndex());
        IsoMetaGrid world = IsoWorld.instance.getMetaGrid();
        int side = IsoChunkMap.chunkGridWidth, minX = map.getWorldXMinTiles(), minY = map.getWorldYMinTiles();
        int ownX = Math.floorDiv((int) Math.floor(x) - minX, WorldScene.CHUNK_TILES), ownY = Math.floorDiv((int) Math.floor(y) - minY, WorldScene.CHUNK_TILES);
        if (ownX < 0 || ownY < 0 || ownX >= side || ownY >= side) {
            return false;
        }
        IsoChunk own = map.getChunk(ownX, ownY);
        if (own == null || !own.loaded) {
            return false;
        }
        float reach = Math.min(Quality.sight(view.index), (side / 2 - 2) * WorldScene.CHUNK_TILES);
        for (int gridY = 0; gridY < side; gridY++) {
            for (int gridX = 0; gridX < side; gridX++) {
                float awayX = minX + (gridX + 0.5f) * WorldScene.CHUNK_TILES - x, awayY = minY + (gridY + 0.5f) * WorldScene.CHUNK_TILES - y;
                if (awayX * awayX + awayY * awayY > reach * reach) {
                    continue;
                }
                IsoChunk chunk = map.getChunk(gridX, gridY);
                if ((chunk == null || !chunk.loaded) && world.isValidChunk(minX / WorldScene.CHUNK_TILES + gridX, minY / WorldScene.CHUNK_TILES + gridY)) {
                    return false;
                }
            }
        }
        return true;
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
            standingView.eyeAt(eye[0], eye[1], eye[2]);
            view.ride.seated = false;
            scene.build(player, standingView, standingView, ViewCamera.SAME_EYE, view.fieldOfView.tanHalfVertical(), FieldOfView.setTanHalfVertical(), flightView, false);
            scene.seenFrom(flightView, view.flight.tanHalfFieldOfView(), view.flight.away(), view.flight.share());
            scene.canopyWindow(canopyX, canopyY, floorUp, canopyAimed, canopyAimX, canopyAimY, canopyFaded, canopyFadedCount);
        } else {
            fromTheEye = true;
            ViewCamera.eyeFrom(aimView, flightView, flightFromAim);
            scene.build(player, flightView, aimView, flightFromAim, view.flight.tanHalfFieldOfView(), FieldOfView.setTanHalfVertical(), null, false);
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
        if (TRACE && frameNanos > LONG_FRAME_NANOS && paceScene != null) {
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
            Log.info(String.format(Locale.ROOT, "first person pace: %.1f ms a frame, worst %.1f, heap %d of %d MB%s",
                    (now - paceSince) / 1e6 / paceFrames, paceWorst / 1e6, (memory.totalMemory() - memory.freeMemory()) >> 20,
                    memory.maxMemory() >> 20, !DevBuild.ON ? "" : paceScene == null ? ", no scene yet" : ", " + paceScene.describe()));
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
        ViewCamera.clean(standingView, x, y, level * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(x, y, level) + eyeHeight(player, 0f),
                (float) Math.atan2(facing.y, facing.x), 0f);
        view.headlights.gather(view.worldScene.terrain, x, y, view.ride);
        float facingLength = Math.max((float) Math.hypot(facing.x, facing.y), 1e-3f);
        view.carriedLight.gather(player, standingView.eyeX, standingView.eyeY, standingView.eyeUp, facing.x / facingLength, facing.y / facingLength, 0f);
        WorldScene.Frame scene = view.worldScene.next();
        view.worldScene.rehearsing = true;
        try {
            scene.build(player, standingView, standingView, ViewCamera.SAME_EYE, view.fieldOfView.tanHalfVertical(), FieldOfView.setTanHalfVertical(), null, false);
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
    private void drawOverhead(IsoPlayer player, float seconds) {
        float x = player.getX(), y = player.getY(), level = player.getZ();
        float floorUp = level * ViewCamera.LEVEL_HEIGHT;
        // Asked once a frame with the frame's time: the joint is eased (ModelHead.eyeOffset), and eased by no time it keeps whatever it
        // first read, which before the skeleton is posed is the feet, with the torch held under the ground.
        float eyeHeight = eyeHeight(player, seconds);
        aimOverhead(player, floorUp);
        ViewCamera.clean(standingView, x, y, floorUp + view.worldScene.terrain.lift(x, y, level) + eyeHeight, OVERHEAD_YAW, OVERHEAD_PITCH);
        if (TRACE && (overheadSaid & 127) == 1) {
            int index = player.getIndex();
            ViewCamera.project(overheadView, overheadHalfHigh / OVERHEAD_AWAY, (float) IsoCamera.getScreenWidth(index) / IsoCamera.getScreenHeight(index),
                    x, y, floorUp + view.worldScene.terrain.lift(x, y, level), ndc);
            Log.info(String.format(Locale.ROOT, "overhead: the picture has the player's feet at (%.1f, %.1f), lift %.3f",
                    (ndc[0] * 0.5f + 0.5f) * IsoCamera.getScreenWidth(index), (0.5f - ndc[1] * 0.5f) * IsoCamera.getScreenHeight(index),
                    view.worldScene.terrain.lift(x, y, level)));
        }
        view.headlights.gather(view.worldScene.terrain, x, y, view.ride);
        Vector2 facing = player.getForwardDirection();
        float facingLength = Math.max((float) Math.hypot(facing.x, facing.y), 1e-3f);
        float beamDown = (float) Math.atan2(CarriedLight.BELOW_EYE - eyeHeight, OVERHEAD_BEAM_LANDS);
        float beamFlat = (float) Math.cos(beamDown);
        view.carriedLight.gather(player, standingView.eyeX, standingView.eyeY, standingView.eyeUp, facing.x / facingLength * beamFlat,
                facing.y / facingLength * beamFlat, (float) Math.sin(beamDown));
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
        scene.build(player, standingView, standingView, ViewCamera.SAME_EYE, view.fieldOfView.tanHalfVertical(), FieldOfView.setTanHalfVertical(), overheadView, true);
        scene.seenFrom(overheadView, overheadHalfHigh / OVERHEAD_AWAY, OVERHEAD_AWAY, 0f);
        scene.canopyWindow(canopyX, canopyY, floorUp, canopyAimed, canopyAimX, canopyAimY, canopyFaded, canopyFadedCount);
        scene.round = true;
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
        if (DevBuild.ON && (canopyOver > 0) != canopyOverSaid) {
            canopyOverSaid = canopyOver > 0;
            Log.info(canopyOverSaid ? "overhead: " + canopyOver + " more trees the game draws see-through than the " + CanopyWindow.TREES
                    + " the picture is told of: those further from the window are drawn whole" : "overhead: the picture is told of every tree the game draws see-through again");
        }
        if (TRACE && (overheadSaid++ & 127) == 0) {
            Log.info(String.format(Locale.ROOT, "overhead: player on screen at (%.1f, %.1f) of %.0fx%.0f, zoom %.2f, east (%.2f, %.2f) south (%.2f, %.2f), camera offset (%.1f, %.1f), middle of screen at tile (%.2f, %.2f), player (%.2f, %.2f, %.2f)",
                    atX, atY, wide, high, zoom, eastX, eastY, southX, southY, IsoCamera.getOffX(), IsoCamera.getOffY(), middleX, middleY, x, y, level));
        }
        ViewCamera.clean(overheadView, 0f, 0f, 0f, OVERHEAD_YAW, OVERHEAD_PITCH);
        overheadView.eyeAt((double) middleX - overheadView.forwardX * OVERHEAD_AWAY, (double) middleY - overheadView.forwardY * OVERHEAD_AWAY,
                (double) floorUp - overheadView.forwardUp * OVERHEAD_AWAY);
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
                    canopyFaded[3 * canopyFadedCount + 2] = CanopyWindow.shown(tree.fadeAlpha);
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
        HandWeapon weapon = player.getPrimaryHandItem() instanceof HandWeapon held ? held : null;
        Penetration.Cartridge through = Penetration.applies(player, weapon) ? Penetration.cartridgeOf(weapon) : null;
        world.boles = Penetration.pastTrees(player, weapon);
        shotDistance = world.shotDistance(aimView, view.worldScene.terrain, player.getZ() * ViewCamera.LEVEL_HEIGHT, reach,
                scene.characterX, scene.characterY, scene.characterBase, scene.characters, through, player.getX(), player.getY());
        IsoGameCharacter aimedAt = characterAimedAt(scene);
        if (aimedAt == null && through != null && shotDistance != Float.POSITIVE_INFINITY && world.met == WorldQuery.WALL) {
            aimedAt = behindTheWall(player, through, reach);
        }
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
        float shownX = from.eyeX + aimView.forwardX * along, shownY = from.eyeY + aimView.forwardY * along;
        float shownUp = from.eyeUp + aimView.forwardUp * along;
        boolean inFront = ViewCamera.project(trackedView, scene.tanHalfFieldOfView(), aspect, shownX, shownY, shownUp, ndc);
        if (!inFront) {
            hideReticle();
            return;
        }
        float lineX = shownX - trackedView.eyeX, lineY = shownY - trackedView.eyeY, lineUp = shownUp - trackedView.eyeUp;
        float lineLength = (float) Math.sqrt(lineX * lineX + lineY * lineY + lineUp * lineUp);
        reticleLine.eyeAt(trackedView.exactX(), trackedView.exactY(), trackedView.exactUp());
        reticleLine.forwardX = lineX / lineLength;
        reticleLine.forwardY = lineY / lineLength;
        reticleLine.forwardUp = lineUp / lineLength;
        reticleOnScreen = true;
        int index = player.getIndex();
        reticleX = Math.round((ndc[0] * 0.5f + 0.5f) * IsoCamera.getScreenWidth(index));
        reticleY = Math.round((0.5f - ndc[1] * 0.5f) * IsoCamera.getScreenHeight(index));
        reticleInPicture = Math.abs(ndc[0]) <= 1f && Math.abs(ndc[1]) <= 1f;
    }

    /** Where a drag cursor's line meets the world: kept apart from the aim's, whose line of fire the game's hit list reads. */
    private final WorldQuery dragWorld = new WorldQuery();
    /** How many pieces each side of a drag cursor's square is drawn in, so a side partly behind the eye is drawn where it is in front. */
    static final int SQUARE_PIECES = 8;
    /** How far over its floor a drag cursor's square is drawn, in tiles, so the floor does not cover it. */
    private static final float SQUARE_OVER_FLOOR = 0.02f;
    private final float[] squareFrom = new float[2], squareTo = new float[2];
    private final float[] facingLines = new float[6 * DragAim.MOST_FACING_LINES];
    private int dragSaidX = Integer.MIN_VALUE, dragSaidY;
    private DragAim.Facing dragSaidFacing;
    private boolean dragButtonDown;
    private final WorldQuery.Ground dragGround = this::groundHeight;

    private float groundHeight(float x, float y) {
        return view.worldScene.terrain.height(x, y);
    }

    /**
     * Puts the drag cursor the game has up where the line through the reticle meets the world,
     * or with the pointer out the line through the pointer, and outlines in the picture the
     * square the game has it on, green where what is dragged can be put there and red where it
     * cannot, with the frame of what stands on an edge of it or an arrow the way what is put on
     * it faces (DragAim.facingLines), and stands the piece it would put down there, outlined
     * the same (DragAim.Ghost): the game draws its ghost into its own view, which is under the
     * picture.
     */
    private void aimDrag(IsoPlayer player, WorldScene.Frame scene) {
        KahluaTable drag = View.onController(player) ? null : DragAim.of(view.index);
        if (drag == null) {
            view.dragAim.aimed.forget();
            view.dragAim.ghost.clear();
            view.crosshair.hideSquare();
            dragSaidX = Integer.MIN_VALUE;
            dragSaidFacing = null;
            dragButtonDown = false;
            return;
        }
        IsoGridSquare square = DragAim.squareOf(drag);
        boolean fits = DragAim.fitsOf(drag);
        // The game puts down what is dragged as the build button is let go, where canBeBuild says it fits.
        boolean buttonDown = player.getCharacterInputComponent().isBuildButtonDown();
        if (dragButtonDown && !buttonDown) {
            boolean nowhere = view.dragAim.aimed.put(view.dragsInFirstPerson()) == DragAim.Put.NOWHERE;
            Log.info(String.format(Locale.ROOT, "drag %s let go on square %s, fits %b%s", drag.rawget("Type"),
                    square == null ? "none" : "(" + square.getX() + ", " + square.getY() + ", " + square.getZ() + ")", fits,
                    nowhere ? ", with no line for it: the game is handed no square" : ""));
        }
        dragButtonDown = buttonDown;
        ViewCamera.Pose line = pointer.on() ? lineThrough(Mouse.getXA(), Mouse.getYA()) : reticleOnScreen && reticleInPicture ? reticleLine : null;
        if (line == null) {
            view.dragAim.aimed.lose();
            view.dragAim.ghost.clear();
            view.crosshair.hideSquare();
            dragSaidX = Integer.MIN_VALUE;
            return;
        }
        dragWorld.look(IsoWorld.instance.currentCell, (int) Math.floor(player.getZ()));
        dragWorld.boles = DragAim.onBoles(drag);
        dragWorld.windows = DragAim.onWindows(drag);
        float met = dragWorld.toSurface(line, dragGround, player.getZ() * ViewCamera.LEVEL_HEIGHT, DragAim.REACH);
        float along = DragAim.along(met);
        float x = line.eyeX + line.forwardX * along, y = line.eyeY + line.forwardY * along;
        if (met <= DragAim.REACH && dragWorld.met == WorldQuery.WALL && !dragWorld.metBole) {
            if (dragWorld.metFacesX) {
                x = DragAim.offWall(x, line.forwardX);
            } else {
                y = DragAim.offWall(y, line.forwardY);
            }
        }
        view.dragAim.aim(x, y);
        view.dragAim.ghost.show(drag, square, fits);
        if (TRACE && square != null && (square.getX() != dragSaidX || square.getY() != dragSaidY)) {
            dragSaidX = square.getX();
            dragSaidY = square.getY();
            Log.info(String.format(Locale.ROOT, "drag %s on square (%d, %d, %d) fits %b, the %s's floor square (%d, %d)", drag.rawget("Type"),
                    square.getX(), square.getY(), square.getZ(), fits, pointer.on() ? "pointer" : "reticle", (int) Math.floor(x), (int) Math.floor(y)));
        }
        float aspect = view.worldScene.drawnAspect();
        if (square == null || aspect == 0f) {
            view.crosshair.hideSquare();
            return;
        }
        Crosshair.Square outline = view.crosshair.squareToFill();
        int pieces = 0;
        float tanHalf = scene.tanHalfFieldOfView();
        int squareX = square.getX(), squareY = square.getY(), level = square.getZ();
        // A piece over several squares is outlined round all of them on the drag's level: each side of each that has none of them beyond it.
        DragAim.Ghost ghost = view.dragAim.ghost;
        int parts = Math.max(ghost.count(), 1);
        for (int part = 0; part < parts; part++) {
            int partX = ghost.count() == 0 ? squareX : ghost.x(part), partY = ghost.count() == 0 ? squareY : ghost.y(part);
            if (ghost.count() > 0 && ghost.z(part) != level) {
                continue;
            }
            for (int side = 0; side < 4; side++) {
                // The sides in turn: north, east, south and west.
                if (ghost.count() > 0 && ghost.covers(partX + (side == 1 ? 1 : side == 3 ? -1 : 0), partY + (side == 2 ? 1 : side == 0 ? -1 : 0), level)) {
                    continue;
                }
                // The corners in turn: north-west, north-east, south-east, south-west.
                float fromX = partX + (side == 1 || side == 2 ? 1f : 0f), fromY = partY + (side >= 2 ? 1f : 0f);
                float toX = partX + (side == 0 || side == 1 ? 1f : 0f), toY = partY + (side == 1 || side == 2 ? 1f : 0f);
                pieces = outlineLine(outline.pieces, pieces, fromX, fromY, 0f, toX, toY, 0f, level, tanHalf, aspect);
            }
        }
        DragAim.Facing facing = DragAim.facingOf(drag);
        if (TRACE && facing != dragSaidFacing) {
            dragSaidFacing = facing;
            Log.info(String.format(Locale.ROOT, "drag %s on square (%d, %d, %d) is put %s", drag.rawget("Type"), squareX, squareY, level, facing));
        }
        int lines = DragAim.facingLines(facing, squareX, squareY, facingLines);
        for (int i = 0; i < lines * 6; i += 6) {
            pieces = outlineLine(outline.pieces, pieces, facingLines[i], facingLines[i + 1], facingLines[i + 2],
                    facingLines[i + 3], facingLines[i + 4], facingLines[i + 5], level, tanHalf, aspect);
        }
        outline.floats = pieces * 4;
        outline.fits = fits;
        view.crosshair.showSquare(outline);
    }

    /**
     * Adds a line over a drag cursor's floor to its outline, as pieces from where it is in the
     * picture to where it is, each one where both its ends are in front of the eye, and gives how
     * many pieces the outline has.
     */
    private int outlineLine(float[] outline, int pieces, float fromX, float fromY, float fromHigh, float toX, float toY, float toHigh,
            int level, float tanHalf, float aspect) {
        // Each piece's end is the next one's start, so each point is projected once.
        boolean fromShown = onSquareFloor(fromX, fromY, fromHigh, level, tanHalf, aspect, squareFrom);
        for (int piece = 0; piece < SQUARE_PIECES; piece++) {
            float b = (float) (piece + 1) / SQUARE_PIECES;
            boolean toShown = onSquareFloor(fromX + (toX - fromX) * b, fromY + (toY - fromY) * b, fromHigh + (toHigh - fromHigh) * b,
                    level, tanHalf, aspect, squareTo);
            if (fromShown && toShown) {
                outline[pieces * 4] = squareFrom[0];
                outline[pieces * 4 + 1] = squareFrom[1];
                outline[pieces * 4 + 2] = squareTo[0];
                outline[pieces * 4 + 3] = squareTo[1];
                pieces++;
            }
            fromShown = toShown;
            squareFrom[0] = squareTo[0];
            squareFrom[1] = squareTo[1];
        }
        return pieces;
    }

    /** Where a place this high over a floor falls in the picture as this frame draws it, into ndc, or false where it is not in front of the eye. */
    private boolean onSquareFloor(float x, float y, float high, int level, float tanHalf, float aspect, float[] ndc) {
        float up = level * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(x, y, level) + SQUARE_OVER_FLOOR + high;
        return ViewCamera.project(trackedView, tanHalf, aspect, x, y, up, ndc);
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

    /**
     * The zombie nearest along the aim past the wall it stops on that a round of the cartridge reaches
     * through that wall (see Penetration), or null; the aim is then on it. A zombie out of the player's
     * sight is not among the characters the picture keeps, so the cell's own list is looked through.
     */
    private IsoZombie behindTheWall(IsoPlayer player, Penetration.Cartridge cartridge, float reach) {
        IsoCell cell = IsoWorld.instance.currentCell;
        java.util.ArrayList<IsoZombie> zombies = cell.getZombieList();
        int floor = (int) Math.floor(player.getZ());
        IsoZombie nearest = null;
        float best = reach;
        for (int i = 0; i < zombies.size(); i++) {
            IsoZombie zombie = zombies.get(i);
            if (zombie.isDead() || zombie.isKnockedDown() || zombie.isCrawling() || (int) Math.floor(zombie.getZ()) != floor) {
                continue;
            }
            float x = zombie.getX(), y = zombie.getY();
            float distance = WorldQuery.cylinderDistance(aimView, x, y, view.kerbSteps.up(view.worldScene.terrain, zombie, x, y, zombie.getZ()));
            if (distance <= shotDistance || distance >= best
                    || Penetration.share(cell, floor, cartridge, world.boles, player.getX(), player.getY(), x, y, aimView, view.worldScene.terrain) <= 0f) {
                continue;
            }
            best = distance;
            nearest = zombie;
        }
        if (nearest != null) {
            shotDistance = best;
        }
        return nearest;
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
        reticleOnScreen = false;
    }

    /**
     * Lets go of the mouse or takes it back. The inventory key is handled here and kept from
     * the game's own handler, which only flips the panels between shown and hidden: they are
     * shown folded from the start, so its first press would hide them.
     */
    private void point() {
        Core.KeyBinding inventory = Core.getInstance().getKeyBinding(INVENTORY_BINDING);
        boolean inventoryKey = pressed(inventory.keyValue()) | pressed(inventory.altKey());
        boolean escapeKey = GameKeyboard.isKeyPressed(ESCAPE_KEY);
        boolean aimButton = Mouse.isButtonDown(AIM_BUTTON), aimClick = aimButton && !aimButtonWasDown;
        aimButtonWasDown = aimButton;
        boolean pointButton = Mouse.isButtonDown(POINT_BUTTON);
        boolean pointClick = pointButton && !pointButtonWasDown;
        pointButtonWasDown = pointButton;
        // Escape, a right click or a middle click takes a context menu down and is for nothing else: not the game's menu, the aim or the pointer.
        boolean menuShut = (escapeKey || aimClick || pointClick) && view.worldMenu.shut();
        if (menuShut) {
            if (escapeKey) {
                GameKeyboard.eatKeyPress(ESCAPE_KEY);
            }
            if (aimClick) {
                view.controls.spendRightPress();
            }
            pointer.menuShut();
            pointClick = false;
        }
        // With a drag cursor up the game's own Esc puts it away, and the pointer stays as it is.
        boolean escape = escapeKey && !menuShut && DragAim.of(view.index) == null;
        if (escape && pointer.escapeTakes()) {
            // Or the same press would go on to open the game's menu.
            GameKeyboard.eatKeyPress(ESCAPE_KEY);
        }
        String was = TRACE ? pointer.describe() : null;
        int panels = Panels.open(view.index);
        long now = System.nanoTime();
        if (aimButton) {
            aimedAt = now;
        }
        if (now - aimedAt < AFTER_AIM_NANOS && !pointer.byKey()) {
            pointer.passOver(panels);
        }
        PointerMode.Inventory inventoryPanels = pointer.update(inventoryKey, escape, pointClick, panels);
        if (inventoryPanels != PointerMode.Inventory.LEAVE) {
            Panels.showInventory(view.index, inventoryPanels == PointerMode.Inventory.OPEN);
        }
        if (TRACE && !was.equals(pointer.describe())) {
            Log.info("pointer " + pointer.describe());
        }
    }

    /** Whether the reticle is on a window the character can climb through, for which the key waits to see if it is held. */
    boolean climbableAtCentre() {
        IsoPlayer player = view.player();
        return player.getVehicle() == null && reticleOnScreen && view.centreUse.climbableAt(reticleLine, floorUp(player), player, IsoWorld.instance.currentCell);
    }

    /**
     * Called as the Interact key goes down with the mouse turning the view: acts on what the
     * last picture drew under the reticle, and says whether anything was there for it. In a
     * vehicle the key is the game's, which gets out with it.
     */
    boolean useAtCentre(boolean climb) {
        IsoPlayer player = view.player();
        if (player.getVehicle() != null || !reticleOnScreen) {
            return false;
        }
        CentreUse.Outcome outcome = view.centreUse.press(reticleLine, floorUp(player), player, IsoWorld.instance.currentCell, view.onMouse(), climb);
        if (outcome == CentreUse.Outcome.CONTAINER) {
            pointer.open();
        }
        if (outcome == CentreUse.Outcome.NOTHING && view.onMouse()) {
            // A sink or a table: the game's own key does nothing for it in this view, and its menu is what a player wants of it.
            IsoObject standing = view.centreUse.standingFor(reticleLine, floorUp(player), player, IsoWorld.instance.currentCell);
            if (standing != null && view.worldMenu.openFor(standing, IsoCamera.getScreenLeft(view.index) + reticleX, IsoCamera.getScreenTop(view.index) + reticleY)) {
                if (TRACE) {
                    Log.info("interact at the reticle: the world menu for " + standing);
                }
                return true;
            }
        }
        if (TRACE) {
            Log.info("interact at the reticle: " + outcome + " on " + Thread.currentThread().getName());
        }
        return outcome != CentreUse.Outcome.NOTHING;
    }

    /** The height of the floor a player stands on, in tiles above level 0. */
    private float floorUp(IsoPlayer player) {
        return player.getZ() * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(player.getX(), player.getY(), player.getZ());
    }

    /** Whether the mouse is out as a pointer over the first person picture. */
    boolean pointerOut() {
        return active() && pointer.on();
    }

    /**
     * What the game's world menu is for at a place on the screen: what the line from the eye
     * through it meets, in the picture as it was last drawn, the head's turn and lean included.
     * Null where it meets nothing, or no picture has been drawn.
     *
     * @param x across the game's window, in pixels
     * @param y down it
     */
    IsoObject underPointer(float x, float y) {
        ViewCamera.Pose line = lineThrough(x, y);
        if (line == null) {
            return null;
        }
        IsoPlayer player = view.player();
        return view.centreUse.forMenu(line, floorUp(player), player, IsoWorld.instance.currentCell);
    }

    /**
     * Acts on what is under the pointer at a place on the screen, as the Interact key does on
     * what is under the reticle, and as the game's own left click does in its own view.
     *
     * @param x across the game's window, in pixels
     * @param y down it
     */
    void useAtPointer(float x, float y) {
        IsoPlayer player = view.player();
        ViewCamera.Pose line = lineThrough(x, y);
        if (player.getVehicle() != null || line == null) {
            return;
        }
        CentreUse.Outcome outcome = view.centreUse.press(line, floorUp(player), player, IsoWorld.instance.currentCell, true, false);
        if (outcome == CentreUse.Outcome.CONTAINER) {
            // Opened as the Interact key opens one, it is put away as that one is: the pointer button or Escape shuts it.
            pointer.open();
        }
        if (TRACE) {
            Log.info("use at the pointer (" + x + ", " + y + "): " + outcome);
        }
    }

    /** The line from the eye through a place on the screen in the picture as it was last drawn, or null before one is drawn. */
    private ViewCamera.Pose lineThrough(float x, float y) {
        float aspect = view.worldScene.drawnAspect();
        if (aspect == 0f || pictureTanHalf == 0f) {
            return null;
        }
        float across = ((x - IsoCamera.getScreenLeft(view.index)) / IsoCamera.getScreenWidth(view.index) * 2f - 1f) * pictureTanHalf * aspect;
        float up = (1f - (y - IsoCamera.getScreenTop(view.index)) / IsoCamera.getScreenHeight(view.index) * 2f) * pictureTanHalf;
        pointerLine.set(trackedView);
        float forwardX = trackedView.forwardX + trackedView.rightX * across + trackedView.upX * up;
        float forwardY = trackedView.forwardY + trackedView.rightY * across + trackedView.upY * up;
        float forwardUp = trackedView.forwardUp + trackedView.rightUp * across + trackedView.upUp * up;
        float length = (float) Math.sqrt(forwardX * forwardX + forwardY * forwardY + forwardUp * forwardUp);
        pointerLine.forwardX = forwardX / length;
        pointerLine.forwardY = forwardY / length;
        pointerLine.forwardUp = forwardUp / length;
        return pointerLine;
    }

    /** The line from the eye through the pointer. Only its eye and its forward are set. */
    private final ViewCamera.Pose pointerLine = new ViewCamera.Pose();

    /**
     * Keeps the pointer out for a menu just opened over the picture until the menu closes. The
     * right click that opened it is not the aim, whose panels the pointer passes over.
     */
    void holdPointerForMenu() {
        aimedAt = System.nanoTime() - AFTER_AIM_NANOS;
        pointer.forNewest(Panels.open(view.index));
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

    /**
     * Whether the game's own animation has the body, and not the player: a hit or a bite and the
     * struggle with it, being dragged down, knocked or falling over, lying on the ground, getting
     * up from any of those, dying.
     */
    private boolean bodyLeads(IsoPlayer player) {
        // In a seat the eye goes with the vehicle; the game takes a dead player out of it.
        if (player.getVehicle() != null) {
            return false;
        }
        if (player.isDead()) {
            return true;
        }
        State state = player.getCurrentState();
        // Getting up from sitting is the player's own doing; from the ground after a fall it is the end of the fall.
        if (state == PlayerGetUpState.instance()) {
            return bodyLeads;
        }
        return hitReaction(state) || state == PlayerFallDownState.instance() || state == PlayerKnockedDown.instance()
                || state == PlayerOnGroundState.instance();
    }

    /**
     * Whether the game's animation has the view as well as the body: not for a hit or a bite and the struggle with
     * it, which the character stays on its feet through and the view is the mouse's through.
     */
    private boolean bodyWhole(IsoPlayer player) {
        State state = player.getCurrentState();
        if (player.isDead() || state != PlayerGetUpState.instance()) {
            return player.isDead() || !hitReaction(state);
        }
        return bodyWhole;
    }

    private static boolean hitReaction(State state) {
        return state == PlayerHitReactionState.instance() || state == PlayerHitReactionPVPState.instance();
    }

    /** Hands the eye to the head and back as the game's animation takes the body and lets it go: see BodyLed. */
    private void lead(IsoPlayer player, float seconds) {
        if (player != ledPlayer) {
            ledPlayer = player;
            view.bodyLed.reset();
            bodyLeads = false;
            bodyWhole = false;
            risenId = -1;
        }
        boolean wasLed = bodyLeads, wasWhole = bodyWhole, wasHolding = view.bodyLed.holding();
        risen = risenOf(player);
        if (landed) {
            // The game makes a dead character a body on the ground and takes it off the map: it is posed no more, until it rises.
            // Once the zombie it rose as has been posed the player's head is not kept (ModelHead.pose), so the eye stays put
            // while that zombie is not yet on the map, and where its head last was once the game makes it a body in turn.
            boolean posedNoMore = player.isDead() && (player.getCurrentSquare() == null || !view.modelHead.posed(player));
            view.bodyLed.frame(bodyLeads(player), bodyWhole(player), posedNoMore && risen == null, seconds);
        } else {
            view.bodyLed.reset();
        }
        int risenNow = risen == null ? -1 : risen.getID();
        if (risenNow != risenId) {
            Log.info(risen != null
                    ? String.format(Locale.ROOT, "%sthe corpse has risen: the eye is the head of zombie %d, at (%.2f, %.2f, %.2f)", who(), risenNow, risen.getX(), risen.getY(), risen.getZ())
                    : String.format(Locale.ROOT, "%sthe zombie the corpse rose as, %d, is gone from the world, dead again or not posed this frame: the eye is no longer its head", who(), risenId));
            risenId = risenNow;
        }
        bodyLeads = view.bodyLed.leads();
        bodyWhole = view.bodyLed.whole();
        if (DevBuild.ON && (bodyLeads != wasLed || bodyWhole != wasWhole)) {
            State state = player.getCurrentState();
            Log.info(String.format(Locale.ROOT, "%sthe eye is %s (%s, hit reaction '%s'%s)", who(),
                    bodyWhole ? "the head's: the game's animation has the body"
                            : bodyLeads ? "the mouse's: the game's animation has the body, the view stays" : "the mouse's again",
                    state == null ? "no state" : state.getClass().getSimpleName(), player.getHitReaction(), player.isDead() ? ", dead" : ""));
        }
        if (wasWhole && !bodyWhole && !player.isDead()) {
            // The mouse takes the view back facing where the head last did, so nothing turns as it does.
            ViewCamera.Pose head = view.bodyLed.head;
            setLook((float) Math.atan2(head.forwardY, head.forwardX), (float) Math.asin(Math.clamp(head.forwardUp, -1f, 1f)));
        }
        if (view.bodyLed.holding() && !wasHolding) {
            view.bodyLed.heldClean.set(cleanView);
            view.bodyLed.heldTracked.set(trackedView);
            Log.info(String.format(Locale.ROOT, "%sthe game has made the body: the eye stays where the head last was, at (%.2f, %.2f, %.2f) looking (%.2f, %.2f, %.2f)",
                    who(), cleanView.eyeX, cleanView.eyeY, cleanView.eyeUp, cleanView.forwardX, cleanView.forwardY, cleanView.forwardUp));
        }
    }

    /**
     * The zombie a dead player's corpse rose as, once the game has posed its head and while it is
     * in the world: the eye is that head's, as it was the dying body's, through standing up and
     * walking off, and through dying again until the game makes it a body. The game takes the
     * zombie off the map as it does, or as its chunk is let go, so it is found again every frame
     * and never kept. A client does not clear the link when the server takes the zombie away, and
     * the game hands that zombie out again as another: one handed out again is no longer a
     * reanimated player.
     */
    private IsoZombie risenOf(IsoPlayer player) {
        return player.isDead() && player.getReanimatedCorpse() instanceof IsoZombie zombie && zombie.isReanimatedPlayer() && zombie.getCurrentSquare() != null
                && view.modelHead.posed(zombie) ? zombie : null;
    }

    /** The character whose head the eye is in this frame: the player, or the zombie their corpse rose as (risenOf). Logic thread. */
    IsoGameCharacter own(IsoPlayer player) {
        return risen != null ? risen : player;
    }

    /**
     * Makes the clean view the head's, or the share of the way to it the hand-over has reached,
     * while the game's animation has the body.
     *
     * @return how the clean view is turned from the level one at the mouse's yaw, for the lean to go with it: the vehicle's tilt while the head does not lead
     */
    private float[] led(IsoPlayer player, float standsUp) {
        // The aim is the mouse's whatever the eye is: aimView is made from this.
        mouseView.set(cleanView);
        if (view.bodyLed.holding()) {
            cleanView.set(view.bodyLed.heldClean);
            BodyLed.tipped(yaw, cleanView, ledTilt);
            return ledTilt;
        }
        float share = view.bodyLed.share();
        if (share <= 0f) {
            return view.ride.tilt;
        }
        IsoGameCharacter body = risen != null ? risen : player;
        float bodyAngle = view.modelHead.head(body, headTurn, headJoint);
        if (Float.isNaN(bodyAngle)) {
            throw new IllegalStateException("the head leads the eye but the character's skeleton is not posed");
        }
        ViewCamera.Pose head = view.bodyLed.head;
        float x = body.getX(), y = body.getY();
        // The risen zombie's feet are where the picture stands its model: it is stepped as it is drawn (KerbSteps).
        float footUp = risen != null ? view.kerbSteps.up(view.worldScene.terrain, risen, x, y, risen.getZ()) : standsUp;
        BodyLed.look(headTurn, bodyAngle, x, y, footUp, headJoint[0], headJoint[1], headJoint[2], head);
        view.bodyLed.upright(head);
        // Held off walls along the map as the standing eye is, a head thrown back against one stopping short of it, and
        // off the floor as far: face down, the eye ahead of the head's joint is under it.
        float margin = Tracking.settings().collisionMargin;
        float awayX = head.eyeX - x, awayY = head.eyeY - y, reach = (float) Math.hypot(awayX, awayY);
        if (reach > 0f) {
            float room = world.roomForTheEye(x, y, awayX / reach, awayY / reach, reach, margin) / reach;
            head.eyeAt((double) x + awayX * room, (double) y + awayY * room, head.exactUp());
        }
        ledEye[0] = head.exactX();
        ledEye[1] = head.exactY();
        world.holdOffWalls(ledEye, x, y, margin);
        head.eyeAt(ledEye[0], ledEye[1], Math.max(head.exactUp(), (double) footUp + margin));
        if (share < 1f) {
            view.bodyLed.between(mouseView, head, share, cleanView);
        } else {
            cleanView.set(head);
        }
        BodyLed.tipped(yaw, cleanView, ledTilt);
        return ledTilt;
    }

    private float eyeHeight(IsoPlayer player, float seconds) {
        if (view.modelHead.eyeOffset(player, yaw, seconds, headOffset)) {
            return headOffset[2];
        }
        headOffset[0] = 0f;
        headOffset[1] = 0f;
        if (player.isSitOnGround() || player.isSittingOnFurniture()) {
            return EYE_HEIGHT_SITTING;
        }
        return player.isSneaking() ? EYE_HEIGHT_SNEAKING : EYE_HEIGHT_STANDING;
    }

    float frameSeconds() {
        return frameSeconds;
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
        if (haveRawX && !MouseGrab.ISOLATED && !bodyWhole) {
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
        if (haveRawY && !MouseGrab.ISOLATED && !bodyWhole) {
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
            walkingAhead = false;
            // While the game's animation has the body it faces where that left it: turned, the struggle would be played to one side.
            if (!player.isAiming() && !bodyLeads) {
                player.setForwardDirection(forward.set((float) Math.cos(yaw), (float) Math.sin(yaw)));
            }
            return move;
        }
        movingAside = Stride.aside(move.x, move.y);
        keysAhead = -move.y / (float) Math.hypot(move.x, move.y);
        walkingAhead = !movingAside;
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
                        + " shot distance %.3f render eye (%.3f, %.3f, %.3f) looking (%.2f, %.2f, %.2f)%s | %s | %s | %s",
                Math.toDegrees(yaw), Math.toDegrees(pitch), player.getX(), player.getY(), player.getZ(),
                facing.x, facing.y, traceAim.x, traceAim.y, player.isAiming(), traceMoveX, traceMoveY,
                cursorX, cursorY, zoom, IsoCamera.getOffX(), IsoCamera.getOffY(), lookCaptured, pointer.describe(), view.headTracking.describe(),
                reticleX, reticleY, ndc[0], ndc[1], shotDistance, trackedView.eyeX, trackedView.eyeY, trackedView.eyeUp,
                trackedView.forwardX, trackedView.forwardY, trackedView.forwardUp,
                risen == null ? "" : String.format(Locale.ROOT, " the head of risen zombie %d at (%.2f, %.2f, %.2f)", risen.getID(), risen.getX(), risen.getY(), risen.getZ()),
                scene.describe(), view.controls.describe(), view.viewModel.describe()));
    }
}
