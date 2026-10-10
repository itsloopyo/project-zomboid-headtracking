package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.core.skinnedmodel.ModelCamera;
import zombie.core.skinnedmodel.model.Model;
import zombie.core.skinnedmodel.model.ModelInstance;
import zombie.core.skinnedmodel.model.ModelInstanceRenderData;
import zombie.core.skinnedmodel.model.ModelInstanceRenderDataList;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;
import zombie.core.skinnedmodel.model.VehicleModelInstance;
import zombie.core.skinnedmodel.model.VertexBufferObject;
import zombie.core.textures.ColorInfo;
import zombie.scripting.objects.ModelAttachment;
import zombie.scripting.objects.ModelScript;
import zombie.iso.IsoGridSquare;
import zombie.vehicles.BaseVehicle;

/**
 * The game's models in the first-person picture: zombies, survivors and
 * animals with their clothes, wounds and whatever they carry, and vehicles.
 *
 * Each frame the game poses every model in view and draws it into its own
 * picture through a camera that looks down from the south-east. This class
 * notes each model the game draws, and has it drawn again through a camera
 * that stands where the first-person eye is, or where the sun is for its
 * shadow. Vehicles, animals, the viewer's own body and whatever a person
 * holds are drawn again by the game, with its own shaders, textures, bones
 * and lights. The bodies, clothes and hair of everyone else standing in the
 * world are drawn by CharacterDraw from the same bones and textures, and so
 * is the viewer's body while a weapon is in hand: the arms on the weapon are
 * then drawn apart from the rest, held up before the eye with it.
 *
 * Render thread only.
 */
public final class Characters {
    final View view;

    Characters(View view) {
        this.view = view;
    }

    /** The model's own units to tiles: the game draws its characters at one and a half times their size. */
    private static final float CHARACTER_SCALE = 1.5f;
    /** The depth the game's model shader leaves a model at when it is not being fitted among sprites. */
    private static final float NO_DEPTH_SHIFT = 0.5f;
    /** Whether the part being drawn is being drawn by the picture, and not by the game for its own view. Render thread. */
    private boolean ours;

    /**
     * Whether a part the game is about to draw is one of its own view's, under a first person
     * picture that covers that view: it is then not drawn. Called from the game's code.
     */
    public boolean gamesOwnDrawIsCovered() {
        return !ours && view.firstPerson.coversTheGamesView();
    }

    /** Has the game draw one part of a model for the picture. */
    private void drawPart(ModelInstanceRenderData model, ModelSlotRenderData data) {
        long began = TALLY ? System.nanoTime() : 0L;
        ours = true;
        try {
            model.RenderCharacter(data);
        } finally {
            ours = false;
        }
        if (TALLY) {
            talliedGameNanos[kind] += System.nanoTime() - began;
        }
    }

    /**
     * Has the game's shader leave a part at the depth it is drawn at. For a part that hangs on
     * another, a pair of glasses or a hat on a head, the shader takes the shift from the model
     * it hangs on, which still had the one the game fits a whole person among its sprites with:
     * such a part was drawn nearer the eye than the head it was on, and showed through it.
     */
    private static void unshifted(ModelInstance instance) {
        instance.targetDepth = NO_DEPTH_SHIFT;
        if (instance.parent != null) {
            instance.parent.targetDepth = NO_DEPTH_SHIFT;
        }
    }

    /** How far from the eye a model still casts a shadow, in tiles. */
    private static final float SHADOW_REACH = 45f;
    /** A vehicle whose middle is this near the eye of a seated viewer is the one the viewer is in: tiles. */
    private static final float OWN_VEHICLE = 2.5f;
    /** How far ahead of the middle of the head the eyes are, in tiles. */
    private static final float EYES_AHEAD = 0.2f;
    /**
     * How far above where the game's camera stands a vehicle its wheels reach the ground, in
     * tiles: without it the car stands in the road up to its sills.
     */
    private static final float VEHICLE_RAISE = 0.6f;
    /**
     * Vehicles are drawn at their models' own size, which the game's vehicle camera does too:
     * the standard car is then 4.7 tiles long, as its script says and as it is in the game's
     * own view. Characters are drawn at one and a half times theirs.
     */
    private static final float VEHICLE_SCALE = 1f;
    private static final float[] IDENTITY = {1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f};

    private final ArrayList<ModelSlotRenderData> noted = new ArrayList<>();
    /** Where the game's own camera stood each noted model: x, y, floor level and the angle it turned it by. */
    private float[] stood = new float[64 * 4];
    /** The frame the noted models belong to: the game reuses their render data for the next. */
    private Object notedIn;
    /** Set on the logic thread: whether the first-person view wants the models at all. */
    volatile boolean wanted;
    private final float[] placed = new float[16];
    private final float[] combined = new float[16];
    private final float[] projection = new float[16];
    private final float[] eyeView = new float[16];
    private int drawnLast;
    /**
     * While set, a vehicle is drawn in another colour of paint than its own. What of it comes
     * out the same as in its own paint is not painted bodywork: its glass, its trim, its seats.
     * The game's picture of a vehicle does not otherwise say which of it is glass.
     */
    boolean repainted;
    /** Which of the models a draw takes: vehicles are painted metal and are lit apart from flesh and cloth. */
    private static final int ALL = 0, ONLY_CHARACTERS = 1, ONLY_VEHICLES = 2;
    private int only = ALL;
    private float asideX, asideY;

    void aside(float x, float y) {
        asideX = x;
        asideY = y;
    }

    /** Puts a projection the same fraction of a pixel aside as this frame's models are drawn. */
    void aside(float[] projection) {
        projection[8] += asideX;
        projection[9] += asideY;
    }
    private static final boolean TRACE = Boolean.getBoolean("cameraunlock.zomboid.trace");
    /** With tracing on, what the models noted last frame were. */
    volatile String described = "";

    /**
     * What the game's vehicle draw asks for its matrices: it calls Begin and End on whatever
     * camera is current, where its character draw takes them from the caller.
     */
    private final ModelCamera EYE = new ModelCamera() {
        @Override
        public void Begin() {
            Core core = Core.getInstance();
            Matrix4f p = core.projectionMatrixStack.alloc();
            p.set(projection);
            core.projectionMatrixStack.push(p);
            Matrix4f m = core.modelViewMatrixStack.alloc();
            m.set(combined);
            core.modelViewMatrixStack.push(m);
            eyeBegun++;
        }

        @Override
        public void End() {
            Core core = Core.getInstance();
            core.modelViewMatrixStack.pop();
            core.projectionMatrixStack.pop();
            eyeBegun--;
        }
    };
    /** How many of EYE's pairs of matrices are on the game's stacks. */
    private int eyeBegun;
    /** The game's own model camera, while EYE stands in for it. */
    private ModelCamera gameCamera;


    int drawnLast() {
        return drawnLast;
    }

    /** The kinds of pass the models are drawn in: the eye's, the sun's two maps, a lamp's, a mirror's, a scope's. */
    private static final int BY_EYE = 0, BY_NEAR = 1, BY_FAR = 2, BY_LAMP = 3, BY_MIRROR = 4, BY_SCOPE = 5;
    private static final String[] KIND_NAMES = {"eye", "near map", "far map", "lamp map", "mirror", "scope"};
    private static final int TALLY_FRAMES = 240;
    private static final boolean TALLY = Boolean.getBoolean("cameraunlock.zomboid.modelTimes");
    /**
     * With -Dcameraunlock.zomboid.modelTimes=true, for each kind of pass since the last line: the models drawn, the parts the picture
     * drew, the parts the game did, the render thread's time in the pass and how much of that was the game's draw of its parts; and
     * the render thread's time in the whole picture.
     */
    private final int[] talliedModels = new int[KIND_NAMES.length], talliedOurs = new int[KIND_NAMES.length], talliedGames = new int[KIND_NAMES.length];
    private final long[] talliedNanos = new long[KIND_NAMES.length], talliedGameNanos = new long[KIND_NAMES.length];
    private double talliedPicture;
    private int kind, talliedFrames, talliedNoted;

    private void tally() {
        talliedNoted += noted.size();
        talliedPicture += view.pipeline.lastMillis();
        if (++talliedFrames < TALLY_FRAMES) {
            return;
        }
        StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "first person models a frame: the picture %.2f ms, noted %.1f", talliedPicture / talliedFrames,
                (float) talliedNoted / talliedFrames));
        for (int each = 0; each < KIND_NAMES.length; each++) {
            if (talliedModels[each] > 0 || talliedNanos[each] > 0) {
                line.append(String.format(Locale.ROOT, " | %s %.1f, parts %.1f ours %.1f the game's, %.2f ms of which the game's %.2f", KIND_NAMES[each],
                        (float) talliedModels[each] / talliedFrames, (float) talliedOurs[each] / talliedFrames, (float) talliedGames[each] / talliedFrames,
                        talliedNanos[each] / 1e6 / talliedFrames, talliedGameNanos[each] / 1e6 / talliedFrames));
            }
            talliedModels[each] = 0;
            talliedOurs[each] = 0;
            talliedGames[each] = 0;
            talliedNanos[each] = 0;
            talliedGameNanos[each] = 0;
        }
        talliedFrames = 0;
        talliedNoted = 0;
        talliedPicture = 0;
        Log.info(line.toString());
    }

    /**
     * Called at the end of the game's own draw of a model into this view's player's part of the
     * screen, while the game's camera still holds where it stood it. The game draws one
     * player's world and that player's picture before it begins the next player's, so the
     * models noted here are the ones this view's picture is then drawn from.
     */
    public void drawn(ModelSlotRenderData data) {
        // A person whose textures are still being made is not drawn by the game, though its
        // draw returns all the same, and has nothing whole to draw from yet.
        if (!wanted || data.character != null && !data.canRender()) {
            return;
        }
        Object frame = SpriteRenderer.instance.getRenderingState();
        if (frame != notedIn) {
            noted.clear();
            notedIn = frame;
        }
        int at = noted.size() * 4;
        if (at == stood.length) {
            stood = Arrays.copyOf(stood, stood.length * 2);
        }
        ModelCamera camera = ModelCamera.instance;
        stood[at] = camera.x;
        stood[at + 1] = camera.y;
        stood[at + 2] = camera.z;
        stood[at + 3] = camera.useAngle;
        noted.add(data);
    }

    /**
     * The parts the game draws a model from this frame. While a person's clothes are being
     * remade the game goes on drawing the last whole set, and the new one is not fit to draw.
     */
    private static ModelInstanceRenderDataList parts(ModelSlotRenderData data) {
        return data.character != null ? data.getModelData() : data.modelData;
    }

    /**
     * Puts back what a draw that threw part way left changed in the game: its matrix stacks,
     * which a later draw of the game's would read this picture's camera from, and its model
     * camera.
     */
    void unwind() {
        Core core = Core.getInstance();
        for (; eyeBegun > 0; eyeBegun--) {
            core.modelViewMatrixStack.pop();
            core.projectionMatrixStack.pop();
        }
        if (ModelCamera.instance == EYE) {
            ModelCamera.instance = gameCamera;
        }
    }

    /** Render thread, as the picture stops being drawn: the models last noted are the game's to let go of. */
    void setDown() {
        noted.clear();
        notedIn = null;
        holding = null;
        CharacterDraw.setDown();
    }

    /**
     * The views whose pictures draw people, a bit for each. What is made once for every view's
     * people, the finer skins, the copies of the game's meshes and what was read of its
     * vehicles, is given back when the last of them lets go. Render thread.
     */
    private static int drawing;

    /**
     * Render thread, once this view's first person has been off a while: gives back what the
     * picture's people and vehicles are drawn from, unless another view still draws them.
     * Returns how many copies of the game's meshes were given back.
     */
    int letGo() {
        cabin.clear();
        drawing &= ~(1 << view.index);
        if (drawing != 0) {
            return 0;
        }
        FineSkins.letGo();
        VehicleMeshes.letGo();
        return CharacterDraw.letGo();
    }

    /** Whether there is anything to draw this frame, asked as each picture begins. The noted models of an earlier frame are dropped. */
    boolean ready() {
        drawing |= 1 << view.index;
        if (noted.isEmpty() || notedIn != SpriteRenderer.instance.getRenderingState()) {
            noted.clear();
            return false;
        }
        return true;
    }

    /**
     * Draws the models the game drew this frame through the first-person camera, into
     * whatever target is bound. The target's depth has to be the world's, so a zombie behind a
     * wall stays behind it. The viewer's own model is left out: the eye is inside its head.
     * Returns how many were drawn.
     */
    int draw(ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float near, float far, boolean vehicles, boolean scope) {
        kind = scope ? BY_SCOPE : BY_EYE;
        byRoom = view.pipeline.figureRooms;
        leftOutByRoom = 0;
        long now = FIGURE_ROOMS && !vehicles && !scope ? System.nanoTime() : 0L;
        figureRoomsLine = now - figureRoomsSaid >= 1_000_000_000L ? new StringBuilder() : null;
        seenHigh = tanHalfFieldOfView;
        seenWide = tanHalfFieldOfView * aspect;
        seenFrom = eyeView;
        ViewCamera.projection(tanHalfFieldOfView, aspect, near, far, projection);
        // Drawn the same fraction of a pixel aside as the world is this frame, so the models'
        // edges are smoothed over time with everything else's.
        projection[8] += asideX;
        projection[9] += asideY;
        only = vehicles ? ONLY_VEHICLES : ONLY_CHARACTERS;
        notingRoots = !vehicles;
        if (notingRoots) {
            rootsFound = 0;
        }
        // Seen from outside the viewer is one of the people in the picture.
        ViewCamera.view(camera, eyeView);
        int drawn = draw(eyeView, view.pipeline.outside, false, null, camera.eyeX, camera.eyeY, Float.MAX_VALUE);
        drawnLast = vehicles ? drawnLast + drawn : drawn;
        if (figureRoomsLine != null) {
            figureRoomsSaid = now;
            Log.info(String.format(Locale.ROOT, "figures by room: the eye's picture drew %d people, left out %d standing where no line of sight reaches (%s, %d rooms built)%s",
                    drawn, leftOutByRoom, byRoom == null ? "no room test" : "room test on", byRoom == null ? 0 : byRoom.rooms(), figureRoomsLine));
            figureRoomsLine = null;
        }
        byRoom = null;
        only = ALL;
        notingRoots = false;
        seenFrom = null;
        return drawn;
    }

    /** While the eye's picture or a scope's is drawn, who it leaves out by the room they stand in, or null: see FigureRooms. */
    private FigureRooms byRoom;
    private int leftOutByRoom;
    /**
     * With -Dcameraunlock.zomboid.figureRooms=true, once a second, how many people the eye's
     * picture drew and how many it left out by the room they stand in, and where those stood.
     */
    private static final boolean FIGURE_ROOMS = Boolean.getBoolean("cameraunlock.zomboid.figureRooms");
    private StringBuilder figureRoomsLine;
    private long figureRoomsSaid;

    /**
     * With -Dcameraunlock.zomboid.withoutModelCull=true every model within reach is drawn in
     * every pass, whether or not the pass can show it: for telling what the culls leave out.
     */
    private static final boolean WITHOUT_CULL = Boolean.getBoolean("cameraunlock.zomboid.withoutModelCull");
    /** While the pass is a picture seen through a view of its own, the eye's or a lamp's: that view, with the tangents of half the picture's width and half its height. */
    private float[] seenFrom;
    private float seenWide, seenHigh;
    /** A lamp's map shows nothing further from the lamp than this, or everything while it is nought. */
    private float seenWithin;
    /** The eye's view of the frame and the tangents of half its picture, as the sun's maps are cast, and whether nothing else looks at what those maps shade. */
    private final float[] eyeSees = new float[16];
    private float eyeWide, eyeHigh;
    private boolean eyeAlone;
    private final float[] past = new float[5];

    /**
     * Said before the sun's maps are cast: what the frame's eye will see. A model whose shadow
     * cannot fall where the eye sees is then left out of them.
     *
     * @param alone whether the eye's picture is the only one this frame that reads those maps: a mirror and a scope look elsewhere
     */
    void seenBy(ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, boolean alone) {
        ViewCamera.view(camera, eyeSees);
        eyeHigh = tanHalfFieldOfView;
        eyeWide = tanHalfFieldOfView * aspect;
        eyeAlone = alone;
    }

    /**
     * How far from the middle of their height a person is taken to reach, in tiles: lying at
     * full length or with a spear held out is about two, and the rest is over, in case an
     * animation carries a model off the spot the game gives for it. PERSON_MIDDLE is how far
     * above the feet that middle is.
     */
    private static final float PERSON_ROUND = 3f;
    private static final float PERSON_MIDDLE = 0.9f;

    /** How far behind a mirror a person is still drawn into it, and a vehicle, in tiles. */
    private static final float MIRROR_PEOPLE = 30f, MIRROR_VEHICLES = 60f;
    /** While set, only what stands before this eye, looking this way and no wider than this, is drawn: a mirror's picture. */
    private boolean mirrored;
    private float mirrorWayX, mirrorWayY, mirrorWide;

    /**
     * Draws the people, or the vehicles, that a mirror looking back from a vehicle takes in,
     * into whatever target is bound, whose depth has to be that of the world the mirror
     * shows. The mirror is small: only what is near enough to be told in it is drawn, and
     * nothing of the vehicle it hangs in. Returns how many were drawn.
     */
    int drawBehind(ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float near, float far, boolean vehicles) {
        kind = BY_MIRROR;
        ViewCamera.projection(tanHalfFieldOfView, aspect, near, far, projection);
        only = vehicles ? ONLY_VEHICLES : ONLY_CHARACTERS;
        mirrored = true;
        mirrorWayX = camera.forwardX;
        mirrorWayY = camera.forwardY;
        mirrorWide = tanHalfFieldOfView * aspect;
        ViewCamera.view(camera, eyeView);
        int drawn = draw(eyeView, false, false, null, camera.eyeX, camera.eyeY, vehicles ? MIRROR_VEHICLES : MIRROR_PEOPLE);
        mirrored = false;
        only = ALL;
        return drawn;
    }

    /** A model this far to the side of what a mirror takes in may still reach into it: a vehicle is that long. */
    private static final float MIRROR_MARGIN = 4f;

    /** The frame has drawn the models it was given. */
    void done() {
        if (TALLY) {
            tally();
        }
        noted.clear();
        holding = null;
    }

    /**
     * Draws the viewer's own model, into whatever target is bound, through a camera whose near
     * plane is nearFrom away. The eye is inside the model's head: everything nearer than that
     * is cut away, the head, the shoulders and the chest, and what is left is the body a
     * person sees looking down, arms and legs. False if the viewer is not among the models.
     */
    boolean drawViewer(ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float nearFrom, float far) {
        IsoPlayer viewer = view.player();
        holding = null;
        if (view.pipeline.seated) {
            return drawOwnVehicle(camera, tanHalfFieldOfView, aspect, nearFrom, far);
        }
        for (int i = 0; i < noted.size(); i++) {
            ModelSlotRenderData data = noted.get(i);
            if (data.character != null && data.character == viewer && !data.inVehicle) {
                settleTheWeapon(data, camera);
                drawOwnBody(data, camera, tanHalfFieldOfView, aspect, nearFrom, far);
                return true;
            }
        }
        return false;
    }

    /** False if the vehicle sat in is not among the models. */
    private boolean drawOwnVehicle(ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float nearFrom, float far) {
        // Seated, what is the viewer's own is the vehicle round the eye. It is drawn here,
        // apart from the world, so that the picture can leave out what of it is in the
        // way without leaving a hole in the world behind it.
        BaseVehicle sat = view.ride.sat;
        for (int i = 0; i < noted.size(); i++) {
            ModelSlotRenderData data = noted.get(i);
            if (sat != null && data.character == null && data.object == sat) {
                ViewCamera.projection(tanHalfFieldOfView, aspect, nearFrom, far, projection);
                // Drawn in full light, whatever the hour: which of its pixels are glass is
                // told from their colour, and the picture lights the cabin itself, by the
                // light the game would have given it.
                int at = i * 4;
                float groundUp = stood[at + 2] * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(stood[at], stood[at + 1], stood[at + 2]);
                place(stood[at], stood[at + 1], groundUp + VEHICLE_RAISE, stood[at + 3], VEHICLE_SCALE);
                String script = view.ride.seatedIn;
                ViewCamera.view(camera, eyeView);
                if (script != null && !parts(data).isEmpty()) {
                    multiply(projection, eyeView, combined);
                    VehicleMeshes.note(cabin, view.ride, script, parts(data).get(0), placed, WHEEL_SETTLES - VEHICLE_SCALE * data.centerOfMassY, stood[at], stood[at + 1], groundUp,
                            data.centerOfMassY, combined);
                }
                ownAmbient[0] = data.ambientR;
                ownAmbient[1] = data.ambientG;
                ownAmbient[2] = data.ambientB;
                data.ambientR = 1f;
                data.ambientG = 1f;
                data.ambientB = 1f;
                fromSeat = true;
                vehicle(data, i * 4, eyeView);
                fromSeat = false;
                data.ambientR = ownAmbient[0];
                data.ambientG = ownAmbient[1];
                data.ambientB = ownAmbient[2];
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the weapon can be held up is settled before the body is drawn: the
     * body is drawn without the arms that hold it only if they are then drawn
     * with the weapon.
     */
    private void settleTheWeapon(ModelSlotRenderData data, ViewCamera.Pose camera) {
        ViewModel.Held hands = view.viewModel.held;
        if (hands != null && CharacterDraw.cuts()) {
            ModelInstanceRenderData weapon = weaponOf(data);
            ViewCamera.between(aim, camera, fromAim);
            boolean own = Own.arms() && OwnArms.model() != null;
            float[] hand = hands.firearm() ? null : gameHand(data);
            if (weapon != null && view.viewModel.place(weapon.xfrm.get(weaponPlace), data.finalScale, weapon.modelInstance, hands,
                    CHARACTER_SCALE, own, hand, fromAim, heldView)) {
                holding = data;
                armHands = hands;
                ownArms = own && reachOwn(data, hands);
                if (ownArms) {
                    return;
                }
                if (own) {
                    // The weapon was placed for the mod's hands, which are another size and hold it another way.
                    if (!armsRefused) {
                        armsRefused = true;
                        Log.info("the mod's own arms could not be placed on " + hands.item() + ": the game's own are drawn on it, as often as that happens");
                    }
                    if (!view.viewModel.place(weapon.xfrm.get(weaponPlace), data.finalScale, weapon.modelInstance, hands, CHARACTER_SCALE, false, hand, fromAim, heldView)) {
                        holding = null;
                        return;
                    }
                }
                arms[0] = reach(data, 0);
                // A bat or an axe is swung from the right hand here: the game's left
                // hand is placed for its own swing, and is nowhere near the handle in ours.
                boolean both = hands.bothHands() && hands.firearm();
                arms[1] = both && reach(data, 1);
                // A firearm with a muzzle has its left hand held on the fore-end.
                // Anything else carried in both hands is not in both at every moment: a
                // left hand that is off it stays with the body, where the game has it.
                heldBoth = both && (view.viewModel.hasMuzzle || !(arms[0] && arms[1] && ArmPose.apart(joints[0], joints[1]) > ON_WEAPON));
                if (!arms[0] && !armless) {
                    armless = true;
                    Log.info("the hands on the weapon are drawn without arms: no part of the viewer's model has a right hand and a forearm");
                }
            }
        }
    }

    private void drawOwnBody(ModelSlotRenderData data, ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float nearFrom, float far) {
        ViewCamera.projection(tanHalfFieldOfView, aspect, nearFrom, far, projection);
        // A person's eyes are at the front of the head, ahead of the neck. The view's
        // eye is on the model's axis, so the model is stood a little behind it: looking
        // down, the chest is below the eye and not the hole of the collar.
        float flat = (float) Math.hypot(camera.forwardX, camera.forwardY);
        float x = data.x, y = data.y;
        if (flat > 1e-3f) {
            data.x -= camera.forwardX / flat * EYES_AHEAD;
            data.y -= camera.forwardY / flat * EYES_AHEAD;
        }
        // In full light, as everyone else is: the picture does the lighting, once,
        // by the light the game gives where the viewer stands.
        float red = data.ambientR, green = data.ambientG, blue = data.ambientB;
        ownLight = (red + green + blue) / 3f;
        data.ambientR = 1f;
        data.ambientG = 1f;
        data.ambientB = 1f;
        ViewCamera.view(camera, eyeView);
        // Put back whatever the draw does: the other view's people are drawn by the same flag.
        CharacterDraw.fromTheEye = true;
        try {
            character(data, eyeView, true, false, holding != null, heldBoth);
        } finally {
            CharacterDraw.fromTheEye = false;
        }
        data.ambientR = red;
        data.ambientG = green;
        data.ambientB = blue;
        data.x = x;
        data.y = y;
    }

    /** The game's light where the viewer stands, as their reflection was last drawn. */
    float reflectedLight = 1f;
    /** How far above the joint of the head the eyes are, in tiles. */
    private static final float EYES_ABOVE_JOINT = 0.1f;

    /**
     * Draws the viewer as the glass of a window shows them, whole, into whatever target is
     * bound: the same model through a view that first puts every point where it is seen in
     * the glass. False if the viewer is not among the models.
     */
    boolean drawReflected(ViewCamera.Pose camera, OwnReflection.Glass glass, float tanHalfFieldOfView, float aspect, float near, float far) {
        IsoPlayer viewer = view.player();
        for (int i = 0; i < noted.size(); i++) {
            ModelSlotRenderData data = noted.get(i);
            if (data.character == null || data.character != viewer || data.inVehicle) {
                continue;
            }
            ViewCamera.projection(tanHalfFieldOfView, aspect, near, far, projection);
            aside(projection);
            ViewCamera.view(camera, eyeView);
            OwnReflection.mirror(eyeView, glass);
            // Stood a little behind the eye, as the body the viewer looks down at is.
            float flat = (float) Math.hypot(camera.forwardX, camera.forwardY);
            float x = data.x, y = data.y, z = data.z;
            if (flat > 1e-3f) {
                data.x -= camera.forwardX / flat * EYES_AHEAD;
                data.y -= camera.forwardY / flat * EYES_AHEAD;
            }
            // A mirror shows the eyes that look into it on their own line of sight. The view's
            // eye is at a height of its own and the model's head is wherever the animation
            // has it, so the model is stood as much higher or lower as brings its eyes to the
            // view's.
            float jointUp = view.modelHead.jointUp;
            if (!Float.isNaN(jointUp)) {
                float eyesUp = z * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(x, y, z) + jointUp * data.finalScale * CHARACTER_SCALE + EYES_ABOVE_JOINT;
                data.z += (camera.eyeUp - eyesUp) / ViewCamera.LEVEL_HEIGHT;
                if (TRACE && (liftSaid++ & 127) == 0) {
                    Log.info(String.format(Locale.ROOT, "glass: the viewer is stood %.3f higher, head joint %.3f in the model, scale %.3f", camera.eyeUp - eyesUp, jointUp, data.finalScale));
                }
            }
            float red = data.ambientR, green = data.ambientG, blue = data.ambientB;
            reflectedLight = (red + green + blue) / 3f;
            data.ambientR = 1f;
            data.ambientG = 1f;
            data.ambientB = 1f;
            CharacterDraw.mirrored = true;
            try {
                character(data, eyeView, false, false, false, false);
            } finally {
                CharacterDraw.mirrored = false;
            }
            data.ambientR = red;
            data.ambientG = green;
            data.ambientB = blue;
            data.x = x;
            data.y = y;
            data.z = z;
            return true;
        }
        return false;
    }

    private int liftSaid;

    /** The bone the game hangs what is in the right hand from. */
    private static final String HAND_BONE = "Bip01_Prop1";
    /** The attachment of a weapon that the game hangs its own flash from. */
    private static final String MUZZLE = "muzzle";

    /** The viewer's model while its weapon is held up, from drawViewer until the frame is done. */
    private ModelSlotRenderData holding;
    private final float[] weaponPlace = new float[16], heldView = new float[16];
    /**
     * The aim of the frame being drawn. The weapon, the hands on it and the shoulders behind
     * them are placed in this view and drawn through the camera, so they stay on the aim
     * while the head turns and leans the picture.
     */
    final ViewCamera.Pose aim = new ViewCamera.Pose();
    private final float[] fromAim = new float[16], pole = new float[3];
    /**
     * The views the hands on the weapon are drawn through, right then left: the weapon's, with
     * each hand turned about its palm to the arm behind it, and the left first moved to hold
     * its palm on the fore-end.
     */
    private final float[][] handViews = new float[2][16];
    private final float[] turn = new float[16];
    /** Whether the left hand is on the weapon held up, with the right. */
    private boolean heldBoth;
    /**
     * The picture's own arms behind the hands on the weapon, right then left: whether each is
     * drawn, its wrist, elbow and shoulder as the eye sees them, the line square to both its
     * bones, the line across its cuff, and half the cuff's width across and through with how
     * far its skin stands off the elbow and the shoulder.
     */
    private final boolean[] arms = new boolean[2];
    private final float[][] joints = new float[2][9], bends = new float[2][3], cuffs = new float[2][3], wides = new float[2][4];
    private final float[] joined = new float[CharacterDraw.HAND_SAYS], other = new float[CharacterDraw.HAND_SAYS];
    private final float[] shoulder = new float[3], elbow = new float[3], wrist = new float[3], rigid = new float[3], palm = new float[3], across = new float[3];
    /**
     * For the trace, right then left, in radians: how far each wrist would be bent with the
     * hand as the game posed it, and how far it is bent as drawn. Written by the render
     * thread and read by the logic thread's trace, which may read a frame's two halves.
     */
    private final float[][] wristSaid = new float[2][2];
    private boolean armless;
    /** The upper arm against the forearm, which is the bone the model is measured by. */
    private static final float UPPER_ARM = 1.15f;
    /** An arm is never drawn quite straight: this much more than the reach, and room for the wrist to move as the hand is turned, in tiles. */
    private static final float REACH_SPARE = 1.06f, WRIST_SWINGS = 0.08f;
    /** In tiles: a loose coat measures far wider at the elbow than the arm in it, and that near the eye it is a wall. */
    private static final float WIDEST_ELBOW = 0.027f;
    /** How much of the cuff's thickness the arm keeps by the elbow. */
    private static final float SLIMS = 0.62f;
    /** How the arm is drawn against the model's own measure at the elbow, and the upper arm against the elbow. */
    private static final float AT_ELBOW = 0.52f;
    /** A left wrist further than this from the right, in tiles, is not on what the right hand holds. */
    private static final float ON_WEAPON = 0.5f;

    /** The light the game gives where the viewer stands, as it gave the viewer's model last drawn. */
    float ownLight = 1f;

    /**
     * Works out one of the picture's arms for the weapon held up, and the turn of the hand it
     * ends in: from the hand the game has on the weapon, moved before the eye with it, back to
     * where that shoulder is, with the elbow dropped down and outwards. The hand is turned
     * about its palm to meet the arm. False if no part of the model has that hand and its
     * forearm, which is where the arm's own measures are taken from; the hand is then drawn
     * as the game posed it.
     */
    private boolean reach(ModelSlotRenderData data, int side) {
        float[] handView = handViews[side];
        System.arraycopy(heldView, 0, handView, 0, 16);
        boolean found = false;
        float wristAcross = 0f, wristThrough = 0f;
        // The joints are the first such part's, which is the body's own. The cuff has to go
        // over every part's cut end, so it is as wide as the widest of them.
        for (int part = 0; part < parts(data).size(); part++) {
            if (CharacterDraw.hand(parts(data).get(part), side == 1, data.finalScale, found ? other : joined)) {
                float[] said = found ? other : joined;
                wristAcross = Math.max(wristAcross, said[CharacterDraw.WRIST_ACROSS]);
                wristThrough = Math.max(wristThrough, said[CharacterDraw.WRIST_THROUGH]);
                found = true;
            }
        }
        if (!found) {
            return false;
        }
        ViewModelMath.place(handView, joined[0], joined[1], joined[2], wrist);
        ViewModelMath.place(handView, joined[3], joined[4], joined[5], rigid);
        ViewModelMath.place(handView, joined[6], joined[7], joined[8], palm);
        ViewModelMath.place(handView, joined[9], joined[10], joined[11], across);
        for (int axis = 0; axis < 3; axis++) {
            across[axis] -= wrist[axis];
        }
        ViewModel.Held hands = view.viewModel.held;
        boolean laid = side == 1 && view.viewModel.hasMuzzle && hands != null && OwnWeapon.standsFor(hands.item());
        if (side == 0 && view.viewModel.hasMuzzle) {
            // The right hand's palm is on the grip, which says which way round a palm faces
            // from a hand's length and its thumb in these axes.
            palmSide = 1f;
            handAxes(false);
            float to = 0f;
            for (int axis = 0; axis < 3; axis++) {
                to += facing[axis] * (view.viewModel.gripSeen[axis] - palm[axis]);
            }
            palmSide = to < 0f ? -1f : 1f;
        }
        if (laid) {
            layOnForeEnd(handView);
        }
        if (side == 1 && view.viewModel.hasMuzzle) {
            for (int axis = 0; axis < 3; axis++) {
                float move = (laid ? held[axis] : view.viewModel.foreEnd[axis]) - palm[axis];
                wrist[axis] += move;
                rigid[axis] += move;
                palm[axis] += move;
                handView[12 + axis] += move;
            }
        }
        // The game's hands are made broad, to be read from a rooftop, and its guns are made to
        // match them. The mod's own gun is the size a gun is, and hands that size on it are a
        // giant's: they are drawn smaller about their middles, which stay where they hold,
        // and the arms behind them are as much thinner. The forearm keeps its length.
        boolean fitted = view.viewModel.hasMuzzle && hands != null && OwnWeapon.standsFor(hands.item());
        if (fitted) {
            for (int i = 0; i < 16; i++) {
                laying[i] = i % 5 == 0 ? HAND_FITS : 0f;
            }
            laying[15] = 1f;
            for (int axis = 0; axis < 3; axis++) {
                laying[12 + axis] = palm[axis] * (1f - HAND_FITS);
                float fore = rigid[axis] - wrist[axis];
                wrist[axis] = palm[axis] + (wrist[axis] - palm[axis]) * HAND_FITS;
                rigid[axis] = wrist[axis] + fore;
            }
            ViewModelMath.onto(laying, handView);
            wristAcross *= HAND_FITS;
            wristThrough *= HAND_FITS;
        }
        float[] at = side == 1 ? ViewModel.LEFT_SHOULDER : ViewModel.RIGHT_SHOULDER;
        ViewModelMath.place(fromAim, at[0], at[1], at[2], shoulder);
        if (side == 1 && fitted) {
            // At the hip the left elbow is out to the side, as for any gun. With the sights up
            // it drops under the fore-end, where an arm the size an arm is has room to hang.
            float up = ViewModelMath.smooth(hands.aim());
            for (int axis = 0; axis < 3; axis++) {
                across2[axis] = ViewModel.LEFT_ELBOW[axis] + (FITTED_LEFT_ELBOW[axis] - ViewModel.LEFT_ELBOW[axis]) * up;
            }
            ViewModelMath.turned(fromAim, across2, pole);
        } else {
            ViewModelMath.turned(fromAim, side == 1 ? ViewModel.LEFT_ELBOW : ViewModel.RIGHT_ELBOW, pole);
        }
        // The game's people are small beside the weapons they hold, and an arm of the model's
        // own lengths does not reach from a shoulder under the eye to a hand out on the
        // weapon. The solver then brings the shoulder to the hand, up into the picture, and
        // the arm's far end is a dome in front of the eye. The upper arm is out of sight,
        // so it is as long as the reach needs and the shoulder stays put.
        float reaching = (ArmPose.apart(shoulder, wrist) * REACH_SPARE + WRIST_SWINGS) / ArmPose.apart(wrist, rigid) - 1f;
        if (!ViewModelMath.arm(shoulder, pole, Math.max(UPPER_ARM, reaching), wrist, rigid, palm, turn, elbow, wristSaid[side])) {
            return false;
        }
        ViewModelMath.onto(turn, handView);
        ViewModelMath.turned(turn, across, across);
        ViewModelMath.cuff(across, wrist, elbow, cuffs[side]);
        float[] arm = joints[side], bend = bends[side], wide = wides[side];
        System.arraycopy(wrist, 0, arm, 0, 3);
        System.arraycopy(elbow, 0, arm, 3, 3);
        System.arraycopy(shoulder, 0, arm, 6, 3);
        float ax = elbow[0] - wrist[0], ay = elbow[1] - wrist[1], az = elbow[2] - wrist[2];
        float bx = shoulder[0] - elbow[0], by = shoulder[1] - elbow[1], bz = shoulder[2] - elbow[2];
        // An arm is never quite straight, so its two bones always have a line square to both.
        float x = ay * bz - az * by, y = az * bx - ax * bz, z = ax * by - ay * bx;
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        bend[0] = x / length;
        bend[1] = y / length;
        bend[2] = z / length;
        wide[0] = wristAcross * CHARACTER_SCALE * ViewModelMath.CUFF;
        wide[1] = wristThrough * CHARACTER_SCALE * ViewModelMath.CUFF;
        // The game's hands are broad for their arms, and an arm as thick all the way up as
        // the cuff that covers the hand's cut end is a log across the foot of the picture.
        wide[2] = Math.max(Math.min(joined[CharacterDraw.ELBOW_WIDE] * CHARACTER_SCALE * AT_ELBOW, WIDEST_ELBOW), wide[1] * SLIMS);
        wide[3] = wide[2];
        return true;
    }

    /** For the trace: how far beside the weapon's middle the middle of the game's hand was, in tiles. */
    private float handAside;
    /** Whether the arms on the weapon held up are the mod's own, and for each, right then left, where its bones are. */
    private boolean ownArms, armsRefused;
    /** How the weapon was held when its arms were placed: they are drawn for that, whatever the logic thread has said since. */
    private ViewModel.Held armHands;
    private final float[][] armSkins = new float[2][24 * 16], armSaid = new float[2][ArmPose.SAYS];
    /**
     * The model's metres to tiles as the game draws a person of scale one: its pump gun's grip
     * is 0.4461 of its units from its muzzle, which on the gun the mod made to the same
     * measure is 0.9015 m, and a unit is CHARACTER_SCALE tiles.
     */
    private static final float ARM_SCALE = 0.4461f / 0.9015f * CHARACTER_SCALE;

    /** Places the mod's own arms on the weapon held up. False if either cannot be placed, and the game's own arms are then drawn. */
    private boolean reachOwn(ModelSlotRenderData data, ViewModel.Held hands) {
        float[] grip = view.viewModel.gripSeen;
        ArmHolds.Placed placed = null;
        boolean handgun = view.viewModel.handgun;
        if (hands.firearm()) {
            // A firearm is held where its own mesh has its grip, and a handgun in both hands when the left is free.
            placed = placed(data, hands);
            if (placed == null) {
                return false;
            }
            grip = placed.at(placed.shape().grip);
            heldBoth = hands.bothHands() || handgun && hands.leftFree();
        } else {
            heldBoth = hands.bothHands();
            // Something swung is held where the game's own hand has it along its handle, on the handle's line.
            for (int part = 0; part < parts(data).size(); part++) {
                if (CharacterDraw.hand(parts(data).get(part), false, data.finalScale, joined)) {
                    ViewModelMath.place(heldView, joined[6], joined[7], joined[8], palm);
                    float[] ahead = view.viewModel.pointing;
                    float along = 0f;
                    for (int axis = 0; axis < 3; axis++) {
                        along += (palm[axis] - view.viewModel.gripSeen[axis]) * ahead[axis];
                    }
                    for (int axis = 0; axis < 3; axis++) {
                        gripAlong[axis] = palm[axis] - view.viewModel.gripSeen[axis] - ahead[axis] * along;
                        palm[axis] = view.viewModel.gripSeen[axis] + ahead[axis] * along;
                    }
                    handAside = ArmPose.apart(gripAlong, NOWHERE);
                    grip = palm;
                    break;
                }
            }
        }
        ArmHolds.Weapon weapon = new ArmHolds.Weapon(hands.firearm(), handgun, grip, view.viewModel.pointing, view.viewModel.over, placed);
        ArmHolds.Doing doing = new ArmHolds.Doing(hands.aim(), heldBoth, hands.action(), hands.cycled(), hands.doing(), hands.done(), hands.round(), hands.pace(), hands.seated(), hands.clip(), hands.out(), hands.fetching(), hands.fetchTakes(), view.viewModel.turned);
        long now = System.nanoTime();
        armSettled.seconds = Math.min((now - armsAsOf) / 1e9f, 0.1f);
        armsAsOf = now;
        int arms = ArmHolds.place(OwnArms.model(), ARM_SCALE * data.finalScale, weapon, doing, fromAim, armSkins, armSaid, armCarries, armSettled);
        // The left hand comes to a weapon held in one hand to load it or rack it.
        heldBoth = arms == 2;
        return arms > 0;
    }

    private static final float[] NOWHERE = new float[3];

    /**
     * The firearm held, where the view has its mesh: the mod's own model where one stands for
     * it, the game's otherwise. Null for a firearm whose mesh has no grip the hands can be
     * put on, which is then held by the game's own arms.
     */
    private ArmHolds.Placed placed(ModelSlotRenderData data, ViewModel.Held hands) {
        ModelInstanceRenderData held = weaponOf(data);
        if (held == null || !view.viewModel.hasMuzzle) {
            return null;
        }
        float pumped = view.viewModel.pumpedPlaced;
        String item = hands.item();
        OwnModel own = OwnWeapon.standsFor(item) ? OwnWeapon.MODEL : OwnRifle.standsFor(item) ? OwnRifle.MODEL : null;
        if (own != null) {
            float out = muzzleOut(held);
            own.hold(meshView, heldView, weaponPlace, data.finalScale, out);
            own.hold(meshStill, view.viewModel.still, weaponPlace, data.finalScale, out);
            // The shotgun's fore-end is where its maker put it and slides as far as the model's does; the rifle's bolt is drawn back as far as its model's.
            boolean shotgun = own == OwnWeapon.MODEL;
            return heldMesh.set(own.shape, meshView, meshStill, shotgun ? own.travel * pumped : 0f,
                    shotgun ? own.foreEnd * own.muzzle[1] : Float.NaN, shotgun ? ArmHolds.BOLT_TRAVEL : own.travel);
        }
        WeaponShape shape = shapeOf(held, hands.item(), view.viewModel.handgun);
        if (shape == null) {
            return null;
        }
        meshTo(heldView, data.finalScale, meshView);
        meshTo(view.viewModel.still, data.finalScale, meshStill);
        return heldMesh.set(shape, meshView, meshStill, ViewModel.PUMP_TRAVEL * shape.muzzle * pumped, Float.NaN, ArmHolds.BOLT_TRAVEL);
    }

    /** The matrix from the held weapon's mesh to a view, from the matrix from the viewer's model to that view. */
    private void meshTo(float[] modelView, float finalScale, float[] out) {
        for (int column = 0; column < 4; column++) {
            float x = weaponPlace[column] * finalScale, y = weaponPlace[4 + column] * finalScale, z = weaponPlace[8 + column] * finalScale;
            float w = weaponPlace[12 + column];
            for (int row = 0; row < 4; row++) {
                out[column * 4 + row] = modelView[row] * x + modelView[4 + row] * y + modelView[8 + row] * z + modelView[12 + row] * w;
            }
        }
    }

    private final float[] meshView = new float[16], meshStill = new float[16];
    private final ArmHolds.Placed heldMesh = new ArmHolds.Placed();
    /**
     * What each of the game's firearm models says of where hands go on its mesh, as it is held by
     * hands of the size hands are, or null where it says there is nowhere: read once for the
     * mesh and the buffer the model script has, and again when the game loads it anew. The
     * script says where the muzzle is, and so which way the barrel and the top run.
     */
    private record Read(Model model, VertexBufferObject from, WeaponShape shape) {}
    private static final java.util.IdentityHashMap<ModelScript, Read> SHAPES = new java.util.IdentityHashMap<>();

    /**
     * The shape of the game's mesh of a firearm held, or null while the mesh is not loaded or if
     * the hands cannot be put on it, which is said once. The muzzle attachment is there: the view
     * model found it. Render thread.
     */
    private WeaponShape shapeOf(ModelInstanceRenderData held, String item, boolean handgun) {
        Model model = held.model;
        ModelScript script = held.modelInstance.modelScript;
        Read known = SHAPES.get(script);
        if (known != null && known.model == model && known.from == (model.mesh == null ? null : model.mesh.vb)) {
            return known.shape;
        }
        ModelAttachment muzzle = script.getAttachmentById(MUZZLE);
        Vector3f offset = muzzle.getOffset(), rotate = muzzle.getRotate();
        float[] along = new float[3], up = new float[3];
        StringBuilder why = new StringBuilder();
        CharacterDraw.Shape mesh = null;
        if (!WeaponShape.axes(rotate.x, rotate.y, offset.z, along, up)) {
            why.append("its muzzle attachment has its barrel run along its top");
        } else {
            mesh = CharacterDraw.shapeOf(model, why);
            if (mesh == null && why.isEmpty()) {
                return null;
            }
        }
        WeaponShape shape = mesh == null ? null
                : WeaponShape.read(mesh.places(), mesh.corners(), along, up, new float[] {offset.x, offset.y, offset.z}, PER_METRE / (handgun ? ViewModel.HANDGUN_FITS : 1f), why);
        SHAPES.put(script, new Read(model, model.mesh == null ? null : model.mesh.vb, shape));
        if (shape == null) {
            Log.info("the mod's own hands cannot be put on " + item + " (" + model.name + ": " + why + "): the game's own arms hold it");
            return null;
        }
        Log.info(String.format(Locale.ROOT, "the hands on %s (%s): grip %.4f along and %.4f up of its mesh, its line %.0f degrees from upright, %.3f thick; guard %.4f to %.4f; fore-end %.4f to %.4f, level %.4f to %.4f%s",
                item, model.name, ArmPose.dot(shape.grip, shape.along), ArmPose.dot(shape.grip, shape.up), Math.toDegrees(Math.atan2(ArmPose.dot(shape.gripLine, shape.along), ArmPose.dot(shape.gripLine, shape.up))),
                shape.gripThick / shape.perMetre, shape.guardBack, shape.guardFront, shape.foreFrom, shape.foreTo, shape.levelFrom, shape.levelTo,
                shape.magazine ? String.format(Locale.ROOT, "; magazine %.4f to %.4f, its foot at %.4f", shape.magazineBack, shape.magazineFront, shape.magazineFoot) : ""));
        return shape;
    }

    /** The game's mesh units in a metre of a long gun, which it makes the size long guns are: its pump gun is 0.4461 of them from grip to muzzle, 0.9015 m. */
    private static final float PER_METRE = 0.4461f / 0.9015f;

    /** Room for the sums that find where on a handle the mod's hand goes. Placing the arms there makes arrays of its own each frame. */
    private final float[] gripAlong = new float[3];
    private final ArmHolds.Carried armCarries = new ArmHolds.Carried();
    private final ArmHolds.Settled armSettled = new ArmHolds.Settled();
    private long armsAsOf;
    private Cabins.Wear wearSeen;
    private OwnArms.Worn wornSeen = OwnArms.BARE;

    /** What the mod's own arms wear, from what the game says the viewer has on: worked out again only when that changes. */
    private OwnArms.Worn wornOnArms() {
        Cabins.Wear wear = view.ride.dress.worn;
        if (wear != wearSeen) {
            wearSeen = wear;
            Cabins.Garment sleeve = wear.sleeve(), glove = wear.glove();
            float[] sleeveColour = new float[3], gloveColour = new float[3];
            if (sleeve != null) {
                Cabins.colour(sleeve, sleeveColour);
            }
            if (glove != null) {
                Cabins.colour(glove, gloveColour);
            }
            wornSeen = new OwnArms.Worn(sleeve != null, wear.toTheWrist() ? OwnArms.CUFF_AT : OwnArms.SHORT_TO, madeOf(sleeve), sleeveColour,
                    glove != null, madeOf(glove), gloveColour);
        }
        return wornSeen;
    }

    private static int madeOf(Cabins.Garment garment) {
        return garment == null || garment.picture() == null ? CharacterDraw.WEAVE : Math.max(CharacterDraw.wearOf("", garment.picture().getName(), false), CharacterDraw.WEAVE);
    }

    /** 1 or -1: which way round a hand's palm faces from its length and its thumb, as the right hand on the grip shows it. */
    private float palmSide = 1f;
    private final float[] along = new float[3], thumb = new float[3], facing = new float[3], held = new float[3];
    private final float[] barrel = new float[3], upright = new float[3], aside = new float[3];
    private final float[] wantAlong = new float[3], wantThumb = new float[3], wantFacing = new float[3], laying = new float[16];
    /**
     * How the hand that works a fore-end lies on it, in parts of the weapon's own axes, which
     * are its upright, the way to its right and the way to its muzzle: the palm under it and
     * to the left, facing up into it, and the fingers across under it and round its far side.
     * Then how far the middle of the hand is from the middle of the fore-end, in tiles.
     */
    private static final float PALM_UP = 0.82f, PALM_RIGHT = 0.57f, FINGERS_RIGHT = 0.90f, FINGERS_AHEAD = 0.42f, PALM_OFF = 0.03f;
    /** How large the hands on the mod's own gun are drawn against the game's own. */
    private static final float HAND_FITS = 0.64f;
    /**
     * The way the left elbow drops under the mod's own gun with the sights up. An arm the size
     * an arm is can hang under the fore-end as an arm does, and comes up to it from below: out
     * to the side, it lies across the view beside the sights.
     */
    private static final float[] FITTED_LEFT_ELBOW = {-0.55f, -1f, 0f};
    private final float[] across2 = new float[3];

    /**
     * A hand's own axes in the view, from its wrist, the elbow rigid with it, its middle and
     * the line across its wrist: along it to the fingers, towards its thumb, which is the side
     * its middle lies to, and out of its palm.
     */
    private void handAxes(boolean left) {
        unit(wrist[0] - rigid[0], wrist[1] - rigid[1], wrist[2] - rigid[2], along);
        float lengthwise = across[0] * along[0] + across[1] * along[1] + across[2] * along[2];
        unit(across[0] - along[0] * lengthwise, across[1] - along[1] * lengthwise, across[2] - along[2] * lengthwise, thumb);
        float side = (palm[0] - wrist[0]) * thumb[0] + (palm[1] - wrist[1]) * thumb[1] + (palm[2] - wrist[2]) * thumb[2];
        float way = (side < 0f ? -1f : 1f);
        for (int axis = 0; axis < 3; axis++) {
            thumb[axis] *= way;
        }
        float turn = left ? palmSide : -palmSide;
        ArmPose.cross(along, thumb, facing);
        for (int axis = 0; axis < 3; axis++) {
            facing[axis] *= turn;
        }
    }

    /**
     * Lays the left hand on the fore-end of the mod's own model as a hand holds one, whatever
     * the game's animation has it doing: turned about its middle, which is then held under
     * the fore-end. The game poses the hand for its own gun and its own racking of it, and a
     * hand left as posed twists on the fore-end as the action is worked.
     */
    private void layOnForeEnd(float[] handView) {
        handAxes(true);
        unit(view.viewModel.muzzle[0] - view.viewModel.gripSeen[0], view.viewModel.muzzle[1] - view.viewModel.gripSeen[1], view.viewModel.muzzle[2] - view.viewModel.gripSeen[2], barrel);
        float tipped = view.viewModel.over[0] * barrel[0] + view.viewModel.over[1] * barrel[1] + view.viewModel.over[2] * barrel[2];
        unit(view.viewModel.over[0] - barrel[0] * tipped, view.viewModel.over[1] - barrel[1] * tipped, view.viewModel.over[2] - barrel[2] * tipped, upright);
        ArmPose.cross(barrel, upright, aside);
        unit(upright[0] * PALM_UP + aside[0] * PALM_RIGHT, upright[1] * PALM_UP + aside[1] * PALM_RIGHT, upright[2] * PALM_UP + aside[2] * PALM_RIGHT, wantFacing);
        float into = 0f;
        for (int axis = 0; axis < 3; axis++) {
            wantAlong[axis] = aside[axis] * FINGERS_RIGHT + barrel[axis] * FINGERS_AHEAD;
            into += wantAlong[axis] * wantFacing[axis];
        }
        unit(wantAlong[0] - wantFacing[0] * into, wantAlong[1] - wantFacing[1] * into, wantAlong[2] - wantFacing[2] * into, wantAlong);
        ArmPose.cross(wantFacing, wantAlong, wantThumb);
        for (int axis = 0; axis < 3; axis++) {
            wantThumb[axis] *= palmSide;
        }
        // The turn that takes the hand's axes to those: each of its own axes read off, and laid along the one wanted.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                laying[column * 4 + row] = wantAlong[row] * along[column] + wantThumb[row] * thumb[column] + wantFacing[row] * facing[column];
            }
            laying[row * 4 + 3] = 0f;
        }
        laying[15] = 1f;
        for (int row = 0; row < 3; row++) {
            laying[12 + row] = palm[row] - (laying[row] * palm[0] + laying[4 + row] * palm[1] + laying[8 + row] * palm[2]);
        }
        ViewModelMath.onto(laying, handView);
        ViewModelMath.place(laying, wrist[0], wrist[1], wrist[2], wrist);
        ViewModelMath.place(laying, rigid[0], rigid[1], rigid[2], rigid);
        ViewModelMath.turned(laying, across, across);
        for (int axis = 0; axis < 3; axis++) {
            held[axis] = view.viewModel.foreEnd[axis] - wantFacing[axis] * PALM_OFF;
        }
    }

    private static void unit(float x, float y, float z, float[] out) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        out[0] = x / length;
        out[1] = y / length;
        out[2] = z / length;
    }

    /** For the trace: how the wrists of the hands on the weapon are bent. */
    String describeArms() {
        StringBuilder said = new StringBuilder();
        if (ownArms) {
            for (int side = 0; side < (heldBoth ? 2 : 1); side++) {
                float[] of = armSaid[side];
                said.append(String.format(Locale.ROOT, "%s own arm: wrist bent %.0f, forearm turned %.0f, upper arm drawn %.2f, shoulder gave %.3f, wrist (%.2f, %.2f, %.2f) elbow (%.2f, %.2f, %.2f)",
                        side == 0 ? "right" : "; left", Math.toDegrees(of[ArmPose.BENT]), Math.toDegrees(of[ArmPose.TWISTED]), of[ArmPose.DRAWN], of[ArmPose.GAVE],
                        of[ArmPose.WRIST], of[ArmPose.WRIST + 1], of[ArmPose.WRIST + 2], of[ArmPose.ELBOW], of[ArmPose.ELBOW + 1], of[ArmPose.ELBOW + 2]));
            }
            return armHands != null && !armHands.firearm() ? said.append(String.format(Locale.ROOT, "; the game's hand was %.3f beside the handle's line", handAside)).toString()
                    : said.toString();
        }
        for (int side = 0; side < (heldBoth ? 2 : 1); side++) {
            said.append(side == 0 ? "right" : "; left");
            if (arms[side]) {
                said.append(String.format(Locale.ROOT, " wrist bent %.0f, as posed %.0f, cuff %.3f by %.3f",
                        Math.toDegrees(wristSaid[side][1]), Math.toDegrees(wristSaid[side][0]), wides[side][0], wides[side][1]));
                said.append(String.format(Locale.ROOT, " joints wrist (%.2f, %.2f, %.2f) elbow (%.2f, %.2f, %.2f) shoulder (%.2f, %.2f, %.2f)", joints[side][0], joints[side][1], joints[side][2], joints[side][3], joints[side][4], joints[side][5], joints[side][6], joints[side][7], joints[side][8]));
            } else {
                said.append(" hand has no arm");
            }
        }
        return said.toString();
    }

    /** Whether drawViewer left the weapon and the arms that hold it for drawHeld. */
    boolean holding() {
        return holding != null;
    }

    private final float[] gameHand = new float[3];

    /** The middle of the game's right hand, in the axes the person is stood from at the scale it is drawn, or null if no part of the viewer's model has one. */
    private float[] gameHand(ModelSlotRenderData data) {
        for (int part = 0; part < parts(data).size(); part++) {
            if (CharacterDraw.hand(parts(data).get(part), false, data.finalScale, joined)) {
                System.arraycopy(joined, 6, gameHand, 0, 3);
                return gameHand;
            }
        }
        return null;
    }

    /** What is in the viewer's right hand, or null: the one part the game hangs from the hand's bone. */
    private static ModelInstanceRenderData weaponOf(ModelSlotRenderData data) {
        for (int part = 0; part < parts(data).size(); part++) {
            ModelInstanceRenderData model = parts(data).get(part);
            if (model.model != null && model.model.isStatic && HAND_BONE.equals(model.modelInstance.parentBoneName)) {
                return model;
            }
        }
        return null;
    }

    private final float[] scopePlace = new float[16];

    /** The game's model of the scope on what the right hand holds, or null. */
    private static ModelInstanceRenderData scopeOf(ModelSlotRenderData data) {
        for (int part = 0; part < parts(data).size(); part++) {
            ModelInstanceRenderData model = parts(data).get(part);
            if (model.model != null && model.model.isStatic && Scope.ATTACHMENT.equals(model.modelInstance.attachmentNameParent) && inHand(model.modelInstance)) {
                return model;
            }
        }
        return null;
    }

    /** Whether a part is what the right hand holds, or is fixed to it: a sight, a magazine. */
    private static boolean inHand(ModelInstance instance) {
        for (ModelInstance of = instance; of != null; of = of.parent) {
            if (HAND_BONE.equals(of.parentBoneName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Draws the weapon the viewer holds, the hands on it and the picture's own arms behind
     * them, into whatever target is bound, where a first person view has them: the weapon
     * moved from where the game's animation holds it to a place before the eye, and the hands
     * moved with it and turned to their arms. Only after drawViewer, in a frame it left them
     * out of.
     */
    void drawHeld(float tanHalfFieldOfView, float aspect, float nearFrom, float far) {
        ModelSlotRenderData data = holding;
        ViewCamera.projection(tanHalfFieldOfView, aspect, nearFrom, far, projection);
        System.arraycopy(heldView, 0, combined, 0, 16);
        float red = data.ambientR, green = data.ambientG, blue = data.ambientB;
        data.ambientR = 1f;
        data.ambientG = 1f;
        data.ambientB = 1f;
        EYE.Begin();
        // A weapon the mod has a model of its own for is drawn by the picture, after the hands,
        // and the game's model of it and of what is fixed to it is left out.
        ViewModel.Held hands = armHands;
        ModelInstanceRenderData held = weaponOf(data);
        IsoGridSquare square = data.character.getCurrentSquare();
        boolean outside = square != null && square.isOutside();
        boolean own = held != null && view.viewModel.hasMuzzle && OwnWeapon.standsFor(hands.item());
        if (own) {
            view.ownWeapon.place(heldView, weaponPlace, data.finalScale, muzzleOut(held), ownLight, outside, hands.aim());
        }
        // The hunting rifle is the mod's own too, and whatever is fixed to it but a scope is left out with the game's model of it.
        boolean rifle = !own && held != null && view.viewModel.hasMuzzle && OwnRifle.standsFor(hands.item());
        if (rifle) {
            view.ownRifle.place(heldView, weaponPlace, data.finalScale, muzzleOut(held), ownLight, outside);
        }
        // A scope is drawn by the picture too, after the hands, in place of the game's model of it.
        ModelInstanceRenderData glass = own || held == null || view.scope.fitted() == null ? null : scopeOf(data);
        if (glass != null) {
            view.scope.place(heldView, glass.xfrm.get(scopePlace), weaponPlace, data.finalScale, ownLight, outside);
        }
        // The weapon first. What the picture draws itself says, beside its colour, which way
        // it faces, and the game's draw of the weapon does not: drawn after the hands, the
        // weapon would be lit where it covers them by the way the hand under it faces.
        for (int part = 0; !own && !rifle && part < parts(data).size(); part++) {
            ModelInstanceRenderData model = parts(data).get(part);
            // The game's own flash is a model on the muzzle, lit like the weapon it is on.
            // The picture draws the flash itself, as light.
            if (model.model == null || !model.model.isStatic || !inHand(model.modelInstance) || model == glass
                    || model.ignoreLighting && MUZZLE.equals(model.modelInstance.attachmentNameParent)) {
                continue;
            }
            unshifted(model.modelInstance);
            GL11.glDepthMask(true);
            drawPart(model, data);
        }
        if (ownArms) {
            OwnArms.Worn worn = wornOnArms();
            float[] stains = view.ride.dress.stains;
            OwnArms.draw(projection, armSkins[0], false, armHands.female(), armHands.skin(), outside, worn, stains);
            if (heldBoth) {
                OwnArms.draw(projection, armSkins[1], true, armHands.female(), armHands.skin(), outside, worn, stains);
                OwnArms.carried(projection, armCarries.what, armCarries.at, outside);
            }
            EYE.End();
            data.ambientR = red;
            data.ambientG = green;
            data.ambientB = blue;
            return;
        }
        CharacterDraw.begin(data, handViews[0], projection, false);
        for (int part = 0; part < parts(data).size(); part++) {
            ModelInstanceRenderData model = parts(data).get(part);
            if (model.model == null || !model.model.isStatic) {
                CharacterDraw.piece(model, false, true);
            }
        }
        if (heldBoth) {
            CharacterDraw.begin(data, handViews[1], projection, false);
            for (int part = 0; part < parts(data).size(); part++) {
                ModelInstanceRenderData model = parts(data).get(part);
                if (model.model == null || !model.model.isStatic) {
                    CharacterDraw.leftHand(model);
                }
            }
        }
        for (int side = 0; side < (heldBoth ? 2 : 1); side++) {
            for (int part = 0; CharacterDraw.OWN_ARMS && arms[side] && part < parts(data).size(); part++) {
                CharacterDraw.sleeve(parts(data).get(part), side == 1, joints[side], bends[side], cuffs[side], wides[side]);
            }
        }
        EYE.End();
        data.ambientR = red;
        data.ambientG = green;
        data.ambientB = blue;
    }

    /** How far the muzzle of what the hand holds is from where the game hangs it, in the model's own measure. */
    private static float muzzleOut(ModelInstanceRenderData held) {
        Vector3f muzzle = held.modelInstance.modelScript.getAttachmentById(MUZZLE).getOffset();
        return (float) Math.sqrt(muzzle.x * muzzle.x + muzzle.y * muzzle.y + muzzle.z * muzzle.z);
    }

    /**
     * Draws the models within reach of the eye as the light sees them, into a shadow map. The
     * viewer is among them, for their shadow.
     */
    void cast(float[] lightMatrix) {
        kind = within == null ? BY_NEAR : BY_FAR;
        System.arraycopy(IDENTITY, 0, projection, 0, 16);
        draw(lightMatrix, true, true, lightMatrix, view.pipeline.focusX, view.pipeline.focusY, SHADOW_REACH);
    }

    /**
     * The same into the far map, leaving out every person who stands wholly within the near
     * map. Both maps look along the same rays, so a caster and its shadow lie on the same spot
     * of each: whatever a person inside the near map's square shades is inside it too, and is
     * shaded from the near map. Cast into both, a crowd round the viewer was drawn a third time
     * for nothing.
     */
    void castBeyond(float[] farMatrix, float[] nearMatrix) {
        within = nearMatrix;
        cast(farMatrix);
        within = null;
    }

    private float[] within;
    /** How far from where they stand a person and what they hold reach, in tiles. */
    private static final float PERSON_REACH = 1.5f;
    /** The world's program reads the near map up to this far from its middle, of 1: see inside in PipelineShaders. */
    private static final float NEAR_MAP_READ = 0.98f;

    private static boolean wholeWithin(float[] map, ModelSlotRenderData data) {
        float up = data.z * ViewCamera.LEVEL_HEIGHT;
        float across = map[0] * data.x + map[4] * data.y + map[8] * up + map[12];
        float along = map[1] * data.x + map[5] * data.y + map[9] * up + map[13];
        float acrossReach = PERSON_REACH * (float) Math.sqrt(map[0] * map[0] + map[4] * map[4] + map[8] * map[8]);
        float alongReach = PERSON_REACH * (float) Math.sqrt(map[1] * map[1] + map[5] * map[5] + map[9] * map[9]);
        return Math.abs(across) + acrossReach < NEAR_MAP_READ && Math.abs(along) + alongReach < NEAR_MAP_READ;
    }

    /**
     * The same for a lamp's shadow map, and people only: a vehicle would stand in its own
     * headlights. Nobody the lamp's own picture has no room for is drawn into it.
     *
     * @param lampView    the view from the lamp, which the map's matrix is a projection of
     * @param tanHalf     the tangent of half that picture's width, which is its height too
     * @param reaches     how far from the lamp the map goes, in tiles
     */
    void castCharacters(float[] lightMatrix, float[] lampView, float tanHalf, float reaches) {
        System.arraycopy(IDENTITY, 0, projection, 0, 16);
        only = ONLY_CHARACTERS;
        kind = BY_LAMP;
        seenFrom = lampView;
        seenWide = tanHalf;
        seenHigh = tanHalf;
        seenWithin = reaches;
        draw(lightMatrix, true, true, null, view.pipeline.focusX, view.pipeline.focusY, SHADOW_REACH);
        seenFrom = null;
        seenWithin = 0f;
        only = ALL;
        notingRoots = false;
    }

    /** And for the shadow map of the torch in the viewer's own hand: everyone and every vehicle but the viewer, who would stand in their own beam. */
    void castOthers(float[] lightMatrix) {
        System.arraycopy(IDENTITY, 0, projection, 0, 16);
        draw(lightMatrix, false, true, null, view.pipeline.focusX, view.pipeline.focusY, SHADOW_REACH);
        notingRoots = false;
    }

    /** Where the people and vehicles the eye's picture draws stand, and stood a frame ago. */
    final Movers movers = new Movers();

    /**
     * Where each person drawn into the view stands and the way they are turned, so that the
     * picture can lay cloth and skin on them that moves as they do.
     */
    static final int ROOTS = 16;
    final float[] roots = new float[ROOTS * 4];
    int rootsFound;
    private boolean notingRoots;

    /**
     * Read from a vehicle's model, its steering wheel comes out this much higher than the wheel
     * the game draws, once the centre of mass is allowed for: the same in nine vehicles
     * measured against the depth of their own cabins, to about 0.02.
     */
    private static final float WHEEL_SETTLES = -0.015f;

    private boolean fromSeat;
    /** The vehicle the viewer sits in, as its body was last drawn from the seat. */
    final VehicleMeshes.Seated cabin = new VehicleMeshes.Seated();

    /** The light the game gave the models last drawn, averaged over them. */
    float ambient = 1f;

    /** The light the game gives the vehicle the viewer sits in. */
    final float[] ownAmbient = {1f, 1f, 1f};

    /** How far from where it stands a model or its shadow's caster reaches, in tiles: a vehicle's half length and some over. */
    private static final float MODEL_REACH = 4f;

    /**
     * @param casting whether the models are drawn as the light sees them, into a shadow map
     * @param map     the matrix of a shadow map that looks along the sun's rays, which a model standing outside is left out of, or null
     */
    private int draw(float[] viewMatrix, boolean withViewer, boolean casting, float[] map, float eyeX, float eyeY, float reach) {
        IsoPlayer viewer = view.player();
        BaseVehicle sat = view.ride.sat;
        int drawn = 0;
        float ambientSum = 0f;
        long began = TALLY ? System.nanoTime() : 0L;
        if (!withViewer && TRACE) {
            StringBuilder said = new StringBuilder();
            for (int i = 0; i < noted.size(); i++) {
                ModelSlotRenderData data = noted.get(i);
                if (data.character == null) {
                    for (int part = 0; part < parts(data).size(); part++) {
                        Model model = parts(data).get(part).modelInstance.model;
                        said.append('[').append(model == null ? "?" : model.name).append(']');
                    }
                }
                said.append(data.character == null ? "none" : data.character.getClass().getSimpleName()).append(data.character == viewer ? "(viewer)" : "")
                        .append(" parts ").append(parts(data).size()).append(" away ")
                        .append(String.format(Locale.ROOT, "%.2f", Math.hypot(data.x - eyeX, data.y - eyeY))).append("; ");
            }
            described = said.append("ambient ").append(String.format(Locale.ROOT, "%.2f", ambient)).toString();
        }
        for (int i = 0; i < noted.size(); i++) {
            ModelSlotRenderData data = noted.get(i);
            if (!withViewer && data.character != null && data.character == viewer) {
                continue;
            }
            if (only != ALL && (data.character == null) != (only == ONLY_VEHICLES)) {
                continue;
            }
            float toX = data.x - eyeX, toY = data.y - eyeY;
            if (toX * toX + toY * toY > reach * reach || !view.pipeline.drawDistance.draws(data.x, data.y)) {
                continue;
            }
            if (map != null && outside(map, data)) {
                continue;
            }
            if (within != null && data.character != null && !data.inVehicle && wholeWithin(within, data)) {
                continue;
            }
            // The vehicle the viewer sits in is drawn apart, with the viewer's own body.
            if (!withViewer && view.pipeline.seated && data.character == null && data.object == sat) {
                continue;
            }
            if (mirrored) {
                // Nothing of the vehicle the mirror hangs in, nor who rides in it, and
                // nothing the mirror does not take in.
                float behind = toX * mirrorWayX + toY * mirrorWayY, aside = Math.abs(toX * mirrorWayY - toY * mirrorWayX);
                if (data.object == sat || data.inVehicle && toX * toX + toY * toY < OWN_VEHICLE * OWN_VEHICLE || behind < 0f || aside > behind * mirrorWide + MIRROR_MARGIN) {
                    continue;
                }
            } else if (!WITHOUT_CULL && (seenFrom != null || map != null && eyeAlone)) {
                // The place is where the draw stands the model, and the ball round it is what it cannot reach out of.
                boolean stands = data.character != null && !data.inVehicle;
                float x = stands ? data.x : stood[i * 4], y = stands ? data.y : stood[i * 4 + 1], z = stands ? data.z : stood[i * 4 + 2];
                float up = z * ViewCamera.LEVEL_HEIGHT + this.view.worldScene.terrain.lift(x, y, z) + (stands ? PERSON_MIDDLE : 0f);
                float round = stands ? PERSON_ROUND : MODEL_REACH;
                if (seenFrom != null) {
                    // Left out when the ball lies wholly behind the pass's eye, wholly past one edge of
                    // its picture, or wholly further than a lamp's map goes. A scope's narrow picture
                    // is the same test with its own tangents.
                    PictureEdges.outsideOf(seenFrom, seenWide, seenHigh, x, y, up, past);
                    if (past[0] > round || past[1] > round || past[2] > round || past[3] > round || past[4] > round
                            || seenWithin > 0f && -past[0] > seenWithin + round) {
                        continue;
                    }
                } else if (PictureEdges.shadesNothingSeen(eyeSees, eyeWide, eyeHigh, map, x, y, up, round)) {
                    continue;
                }
            }
            if (!WITHOUT_CULL && (kind == BY_EYE || kind == BY_SCOPE) && byRoom != null && data.character != null && !data.inVehicle) {
                IsoGridSquare square = data.character.getCurrentSquare();
                boolean leftOut = square != null && byRoom.leftOut(square.getRoomID(), square.getX(), square.getY(), square.getZ());
                if (FIGURE_ROOMS && figureRoomsLine != null && square != null) {
                    figureRoomsLine.append(String.format(Locale.ROOT, " (%d,%d,%d room %d %s)", square.getX(), square.getY(), square.getZ(), square.getRoomID(),
                            leftOut ? "LEFT OUT" : square.getRoomID() == RoomSight.OUTSIDE ? "outdoors" : byRoom.built(square.getRoomID()) ? "drawn, room built" : "drawn, beside an opening"));
                }
                if (leftOut) {
                    leftOutByRoom++;
                    continue;
                }
            }
            // Drawn in full light. The game dims a model by the light where it stands, and the
            // picture lights it too: the game's light is kept as a figure and the picture does
            // the lighting once.
            float red = data.ambientR, green = data.ambientG, blue = data.ambientB;
            ambientSum += (red + green + blue) / 3f;
            data.ambientR = 1f;
            data.ambientG = 1f;
            data.ambientB = 1f;
            if (data.character != null && !data.inVehicle && rootsFound < ROOTS && notingRoots) {
                int at = rootsFound++ * 4;
                roots[at] = data.x;
                roots[at + 1] = data.y;
                roots[at + 2] = data.z * ViewCamera.LEVEL_HEIGHT + this.view.worldScene.terrain.lift(data.x, data.y, data.z);
                roots[at + 3] = data.animPlayerAngle;
            }
            if (kind == BY_EYE && !data.inVehicle) {
                // Who rides is not noted: they go as the vehicle goes.
                boolean stands = data.character != null;
                float x = stands ? data.x : stood[i * 4], y = stands ? data.y : stood[i * 4 + 1], z = stands ? data.z : stood[i * 4 + 2];
                movers.note(stands ? data.character : data.object, x, y, z * ViewCamera.LEVEL_HEIGHT + this.view.worldScene.terrain.lift(x, y, z), stands ? data.animPlayerAngle : 0f,
                        toX * toX + toY * toY);
            }
            if (data.character == null) {
                vehicle(data, i * 4, viewMatrix);
            } else if (data.inVehicle) {
                seated(data, i * 4, viewMatrix);
            } else {
                character(data, viewMatrix, data.character == viewer, casting, false, false);
            }
            data.ambientR = red;
            data.ambientG = green;
            data.ambientB = blue;
            drawn++;
        }
        if (drawn > 0) {
            ambient = ambientSum / drawn;
        }
        if (TALLY) {
            talliedModels[kind] += drawn;
            talliedNanos[kind] += System.nanoTime() - began;
        }
        return drawn;
    }

    /**
     * Whether a model stands clear of a shadow map's square of ground. The near map covers
     * about a third of the ground models are cast from, and each one left out is a whole
     * model's draw saved. The map looks along parallel rays, so its matrix has no divide.
     */
    private static boolean outside(float[] map, ModelSlotRenderData data) {
        float up = data.z * ViewCamera.LEVEL_HEIGHT;
        float across = map[0] * data.x + map[4] * data.y + map[8] * up + map[12];
        float along = map[1] * data.x + map[5] * data.y + map[9] * up + map[13];
        float acrossReach = MODEL_REACH * (float) Math.sqrt(map[0] * map[0] + map[4] * map[4] + map[8] * map[8]);
        float alongReach = MODEL_REACH * (float) Math.sqrt(map[1] * map[1] + map[5] * map[5] + map[9] * map[9]);
        return Math.abs(across) > 1f + acrossReach || Math.abs(along) > 1f + alongReach;
    }

    /**
     * A person standing in the world. The viewer's own body is the game's to draw, part by
     * part, unless a weapon is held up: the picture then draws it without the arms on the
     * weapon. Of anyone else the picture draws the parts it can itself and leaves the game the
     * rest, what they hold and what hangs from them.
     */
    private void character(ModelSlotRenderData data, float[] viewMatrix, boolean own, boolean casting, boolean held, boolean bothHands) {
        place(data.x, data.y, data.z * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(data.x, data.y, data.z), data.animPlayerAngle, CHARACTER_SCALE);
        multiply(viewMatrix, placed, combined);
        EYE.Begin();
        if (!own || held) {
            CharacterDraw.begin(data, combined, projection, casting);
        }
        if (!own && !casting) {
            lightWhere(data);
        }
        for (int part = 0; part < parts(data).size(); part++) {
            ModelInstanceRenderData model = parts(data).get(part);
            if (held) {
                // The weapon and the arms on it are drawn apart, held up before the eye.
                if (inHand(model.modelInstance) || CharacterDraw.piece(model, bothHands, false)) {
                    continue;
                }
            } else if (!own && CharacterDraw.part(model)) {
                if (TALLY) {
                    talliedOurs[kind]++;
                }
                continue;
            }
            if (TALLY) {
                talliedGames[kind]++;
            }
            unshifted(model.modelInstance);
            // The game's draw ends by putting its own remembered state back, which has depth
            // writes off. Without them the next part, or the next model, is painted over
            // whatever is nearer.
            GL11.glDepthMask(true);
            drawPart(model, data);
        }
        EYE.End();
    }

    /**
     * Says to the picture's draw of a person what the game's light is on the square they stand
     * on, read as the world's own squares are, so that a person in a dark room is as dark as
     * the room. Nothing is said of a square the viewer has not seen: the game holds no light for it.
     * The viewer's torch is not in it: its beam lights a person as it lights the wall behind them.
     */
    private final ColorInfo stoodIn = new ColorInfo();

    private void lightWhere(ModelSlotRenderData data) {
        IsoGridSquare square = data.character.getCurrentSquare();
        if (view.player() == null || square == null || !square.isSeen(view.index)) {
            return;
        }
        ColorInfo light = view.torchLess.light(square, view.index, stoodIn);
        CharacterDraw.stands(Evening.dimmed((light.r + light.g + light.b) / 3f, view.worldScene.openNow, view.worldScene.daylightKeptNow), square.isOutside());
    }

    /**
     * A character in a vehicle. The game stands its camera where the vehicle is and then seats
     * the character from there itself, by the seat's place in the vehicle and the way the
     * vehicle is turned and tipped. Here the camera stands the same place as seen from the eye,
     * and the game does its seating on top of that.
     */
    private void seated(ModelSlotRenderData data, int at, float[] viewMatrix) {
        place(stood[at], stood[at + 1], stood[at + 2] * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(stood[at], stood[at + 1], stood[at + 2]) + VEHICLE_RAISE,
                stood[at + 3], VEHICLE_SCALE);
        multiply(viewMatrix, placed, combined);
        gameCamera = ModelCamera.instance;
        ModelCamera.instance = EYE;
        Model.CharacterModelCameraBegin(data);
        ModelCamera.instance = gameCamera;
        for (int part = 0; part < parts(data).size(); part++) {
            ModelInstanceRenderData model = parts(data).get(part);
            unshifted(model.modelInstance);
            GL11.glDepthMask(true);
            drawPart(model, data);
        }
        EYE.End();
    }

    /**
     * The game's vehicle draw sets its matrices up itself, from the current camera, and then
     * moves the vehicle's depth so that its middle lands at the depth of the square it stands
     * on in the game's own picture. Here the middle is to stay where the camera puts it, so the
     * draw is told that the square's depth is exactly the depth the middle already has. The
     * game takes that depth as the middle's z after the matrices and before any divide by w,
     * its own camera having none, so that is the figure given here. With the divide the two
     * differ, and the vehicle is drawn nearer than it is: through the wall of a house.
     */
    private void vehicle(ModelSlotRenderData data, int at, float[] viewMatrix) {
        place(stood[at], stood[at + 1], stood[at + 2] * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(stood[at], stood[at + 1], stood[at + 2]) + VEHICLE_RAISE,
                stood[at + 3], VEHICLE_SCALE);
        multiply(viewMatrix, placed, combined);
        float eyeX = combined[4] * data.centerOfMassY + combined[12];
        float eyeY = combined[5] * data.centerOfMassY + combined[13];
        float eyeZ = combined[6] * data.centerOfMassY + combined[14];
        float eyeW = combined[7] * data.centerOfMassY + combined[15];
        float clipZ = projection[2] * eyeX + projection[6] * eyeY + projection[10] * eyeZ + projection[14] * eyeW;
        float squareDepth = data.squareDepth;
        data.squareDepth = (clipZ + 1f) / 2f;
        gameCamera = ModelCamera.instance;
        ModelCamera.instance = EYE;
        for (int part = 0; part < parts(data).size(); part++) {
            GL11.glDepthMask(true);
            ModelInstanceRenderData model = parts(data).get(part);
            // The game's bodies have no bulkhead and no arches inside: from the seat the road
            // wheels would stand in the cabin.
            if (fromSeat && model.modelInstance.model != null && model.modelInstance.model.name.contains("wheel")) {
                continue;
            }
            if (repainted && model.modelInstance instanceof VehicleModelInstance body) {
                float x = body.painColor.x, y = body.painColor.y, z = body.painColor.z;
                body.painColor.x = (x + 0.5f) % 1f;
                body.painColor.y = y < 0.5f ? 1f : 0f;
                body.painColor.z = z < 0.5f ? 1f : 0.15f;
                // The paint is the vehicle's own, kept from frame to frame: it is put back whatever the draw does.
                try {
                    model.RenderVehicle(data);
                } finally {
                    body.painColor.x = x;
                    body.painColor.y = y;
                    body.painColor.z = z;
                }
            } else {
                model.RenderVehicle(data);
            }
        }
        ModelCamera.instance = gameCamera;
        data.squareDepth = squareDepth;
    }

    /**
     * The matrix that stands a model in the world. A model's own axes are x to its side, y up
     * and z along its back; the game turns it by its angle plus half a turn, mirrors it across
     * x and scales it. In world axes (x, y, up) that comes to this.
     */
    private void place(float x, float y, float up, float angle, float scale) {
        float cos = (float) Math.cos(angle + Math.PI) * scale;
        float sin = (float) Math.sin(angle + Math.PI) * scale;
        placed[0] = cos; placed[1] = sin; placed[2] = 0f; placed[3] = 0f;
        placed[4] = 0f; placed[5] = 0f; placed[6] = scale; placed[7] = 0f;
        placed[8] = sin; placed[9] = -cos; placed[10] = 0f; placed[11] = 0f;
        placed[12] = x; placed[13] = y; placed[14] = up; placed[15] = 1f;
    }

    /** out = a times b, all three column-major. */
    private static void multiply(float[] a, float[] b, float[] out) {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                out[column * 4 + row] = a[row] * b[column * 4] + a[4 + row] * b[column * 4 + 1]
                        + a[8 + row] * b[column * 4 + 2] + a[12 + row] * b[column * 4 + 3];
            }
        }
    }
}
