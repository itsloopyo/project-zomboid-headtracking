package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import org.joml.Matrix4f;
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
import zombie.core.textures.ColorInfo;
import zombie.iso.IsoGridSquare;

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
    /** The model's own units to tiles: the game draws its characters at one and a half times their size. */
    private static final float CHARACTER_SCALE = 1.5f;
    /** The depth the game's model shader leaves a model at when it is not being fitted among sprites. */
    private static final float NO_DEPTH_SHIFT = 0.5f;
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

    private static final ArrayList<ModelSlotRenderData> noted = new ArrayList<>();
    /** Where the game's own camera stood each noted model: x, y, floor level and the angle it turned it by. */
    private static float[] stood = new float[64 * 4];
    /** The frame the noted models belong to: the game reuses their render data for the next. */
    private static Object notedIn;
    /** Set on the logic thread: whether the first-person view wants the models at all. */
    static volatile boolean wanted;
    private static final float[] placed = new float[16];
    private static final float[] combined = new float[16];
    private static final float[] projection = new float[16];
    private static int drawnLast;
    /**
     * While set, a vehicle is drawn in another colour of paint than its own. What of it comes
     * out the same as in its own paint is not painted bodywork: its glass, its trim, its seats.
     * The game's picture of a vehicle does not otherwise say which of it is glass.
     */
    static boolean repainted;
    /** Which of the models a draw takes: vehicles are painted metal and are lit apart from flesh and cloth. */
    private static final int ALL = 0, ONLY_CHARACTERS = 1, ONLY_VEHICLES = 2;
    private static int only = ALL;
    private static float asideX, asideY;

    static void aside(float x, float y) {
        asideX = x;
        asideY = y;
    }
    private static final boolean TRACE = Boolean.getBoolean("cameraunlock.zomboid.trace");
    /** With tracing on, what the models noted last frame were. */
    static volatile String described = "";

    /**
     * What the game's vehicle draw asks for its matrices: it calls Begin and End on whatever
     * camera is current, where its character draw takes them from the caller.
     */
    private static final ModelCamera EYE = new ModelCamera() {
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
    private static int eyeBegun;
    /** The game's own model camera, while EYE stands in for it. */
    private static ModelCamera gameCamera;

    private Characters() {}

    static int drawnLast() {
        return drawnLast;
    }

    /** Called at the end of the game's own draw of a model, while the game's camera still holds where it stood it. */
    public static void drawn(ModelSlotRenderData data) {
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
    static void unwind() {
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
    static void setDown() {
        noted.clear();
        notedIn = null;
        holding = null;
        CharacterDraw.setDown();
    }

    /** Whether there is anything to draw this frame. The noted models of an earlier frame are dropped. */
    static boolean ready() {
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
     */
    static void draw(ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float near, float far, boolean vehicles) {
        System.arraycopy(ViewCamera.projection(tanHalfFieldOfView, aspect, near, far), 0, projection, 0, 16);
        // Drawn the same fraction of a pixel aside as the world is this frame, so the models'
        // edges are smoothed over time with everything else's.
        projection[8] += asideX;
        projection[9] += asideY;
        only = vehicles ? ONLY_VEHICLES : ONLY_CHARACTERS;
        notingRoots = !vehicles;
        if (notingRoots) {
            rootsFound = 0;
        }
        // Seen from overhead the viewer is one of the people in the picture.
        int drawn = draw(ViewCamera.view(camera), Pipeline.overhead, false, null, camera.eyeX, camera.eyeY, Float.MAX_VALUE);
        drawnLast = vehicles ? drawnLast + drawn : drawn;
        only = ALL;
        notingRoots = false;
    }

    /** How far behind a mirror a person is still drawn into it, and a vehicle, in tiles. */
    private static final float MIRROR_PEOPLE = 30f, MIRROR_VEHICLES = 60f;
    /** While set, only what stands before this eye, looking this way and no wider than this, is drawn: a mirror's picture. */
    private static boolean mirrored;
    private static float mirrorWayX, mirrorWayY, mirrorWide;

    /**
     * Draws the people, or the vehicles, that a mirror looking back from a vehicle takes in,
     * into whatever target is bound, whose depth has to be that of the world the mirror
     * shows. The mirror is small: only what is near enough to be told in it is drawn, and
     * nothing of the vehicle it hangs in. Returns how many were drawn.
     */
    static int drawBehind(ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float near, float far, boolean vehicles) {
        System.arraycopy(ViewCamera.projection(tanHalfFieldOfView, aspect, near, far), 0, projection, 0, 16);
        only = vehicles ? ONLY_VEHICLES : ONLY_CHARACTERS;
        mirrored = true;
        mirrorWayX = camera.forwardX;
        mirrorWayY = camera.forwardY;
        mirrorWide = tanHalfFieldOfView * aspect;
        int drawn = draw(ViewCamera.view(camera), false, false, null, camera.eyeX, camera.eyeY, vehicles ? MIRROR_VEHICLES : MIRROR_PEOPLE);
        mirrored = false;
        only = ALL;
        return drawn;
    }

    /** A model this far to the side of what a mirror takes in may still reach into it: a vehicle is that long. */
    private static final float MIRROR_MARGIN = 4f;

    /** The frame has drawn the models it was given. */
    static void done() {
        noted.clear();
        holding = null;
    }

    /**
     * Draws the viewer's own model, into whatever target is bound, through a camera whose near
     * plane is nearFrom away. The eye is inside the model's head: everything nearer than that
     * is cut away, the head, the shoulders and the chest, and what is left is the body a
     * person sees looking down, arms and legs. False if the viewer is not among the models.
     */
    static boolean drawViewer(ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float nearFrom, float far) {
        IsoPlayer viewer = IsoPlayer.getInstance();
        holding = null;
        if (Pipeline.seated) {
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
    private static boolean drawOwnVehicle(ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float nearFrom, float far) {
        // Seated, what is the viewer's own is the vehicle round the eye. It is drawn here,
        // apart from the world, so that the picture can leave out what of it is in the
        // way without leaving a hole in the world behind it.
        for (int i = 0; i < noted.size(); i++) {
            ModelSlotRenderData data = noted.get(i);
            float toX = data.x - camera.eyeX, toY = data.y - camera.eyeY;
            if (data.character == null && toX * toX + toY * toY < OWN_VEHICLE * OWN_VEHICLE) {
                System.arraycopy(ViewCamera.projection(tanHalfFieldOfView, aspect, nearFrom, far), 0, projection, 0, 16);
                // Drawn in full light, whatever the hour: which of its pixels are glass is
                // told from their colour, and the picture lights the cabin itself, by the
                // light the game would have given it.
                int at = i * 4;
                float groundUp = stood[at + 2] * ViewCamera.LEVEL_HEIGHT + Terrain.lift(stood[at], stood[at + 1], stood[at + 2]);
                place(stood[at], stood[at + 1], groundUp + VEHICLE_RAISE, stood[at + 3], VEHICLE_SCALE);
                String script = Ride.seatedIn;
                if (script != null && !parts(data).isEmpty()) {
                    multiply(projection, ViewCamera.view(camera), combined);
                    VehicleMeshes.note(script, parts(data).get(0), placed, WHEEL_SETTLES - VEHICLE_SCALE * data.centerOfMassY, stood[at], stood[at + 1], groundUp,
                            data.centerOfMassY, combined);
                }
                ownAmbient[0] = data.ambientR;
                ownAmbient[1] = data.ambientG;
                ownAmbient[2] = data.ambientB;
                data.ambientR = 1f;
                data.ambientG = 1f;
                data.ambientB = 1f;
                fromSeat = true;
                vehicle(data, i * 4, ViewCamera.view(camera));
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
    private static void settleTheWeapon(ModelSlotRenderData data, ViewCamera.Pose camera) {
        ViewModel.Held hands = ViewModel.held;
        if (hands != null && CharacterDraw.cuts()) {
            ModelInstanceRenderData weapon = weaponOf(data);
            ViewCamera.between(aim, camera, fromAim);
            if (weapon != null && ViewModel.place(weapon.xfrm.get(weaponPlace), data.finalScale, weapon.modelInstance, hands,
                    CHARACTER_SCALE, fromAim, heldView)) {
                holding = data;
                arms[0] = reach(data, 0);
                // A bat or an axe is swung from the right hand here: the game's left
                // hand is placed for its own swing, and is nowhere near the handle in ours.
                boolean both = hands.bothHands() && hands.firearm();
                arms[1] = both && reach(data, 1);
                // A firearm with a muzzle has its left hand held on the fore-end.
                // Anything else carried in both hands is not in both at every moment: a
                // left hand that is off it stays with the body, where the game has it.
                heldBoth = both && (ViewModel.hasMuzzle || !(arms[0] && arms[1] && apart(joints[0], joints[1]) > ON_WEAPON));
                if (!arms[0] && !armless) {
                    armless = true;
                    Log.info("the hands on the weapon are drawn without arms: no part of the viewer's model has a right hand and a forearm");
                }
            }
        }
    }

    private static void drawOwnBody(ModelSlotRenderData data, ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float nearFrom, float far) {
        System.arraycopy(ViewCamera.projection(tanHalfFieldOfView, aspect, nearFrom, far), 0, projection, 0, 16);
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
        character(data, ViewCamera.view(camera), true, false, holding != null, heldBoth);
        data.ambientR = red;
        data.ambientG = green;
        data.ambientB = blue;
        data.x = x;
        data.y = y;
    }

    /** The bone the game hangs what is in the right hand from. */
    private static final String HAND_BONE = "Bip01_Prop1";
    /** The attachment of a weapon that the game hangs its own flash from. */
    private static final String MUZZLE = "muzzle";

    /** The viewer's model while its weapon is held up, from drawViewer until the frame is done. */
    private static ModelSlotRenderData holding;
    private static final float[] weaponPlace = new float[16], heldView = new float[16];
    /**
     * The aim of the frame being drawn. The weapon, the hands on it and the shoulders behind
     * them are placed in this view and drawn through the camera, so they stay on the aim
     * while the head turns and leans the picture.
     */
    static final ViewCamera.Pose aim = new ViewCamera.Pose();
    private static final float[] fromAim = new float[16], pole = new float[3];
    /**
     * The views the hands on the weapon are drawn through, right then left: the weapon's, with
     * each hand turned about its palm to the arm behind it, and the left first moved to hold
     * its palm on the fore-end.
     */
    private static final float[][] handViews = new float[2][16];
    private static final float[] turn = new float[16];
    /** Whether the left hand is on the weapon held up, with the right. */
    private static boolean heldBoth;
    /**
     * The picture's own arms behind the hands on the weapon, right then left: whether each is
     * drawn, its wrist, elbow and shoulder as the eye sees them, the line square to both its
     * bones, the line across its cuff, and half the cuff's width across and through with how
     * far its skin stands off the elbow and the shoulder.
     */
    private static final boolean[] arms = new boolean[2];
    private static final float[][] joints = new float[2][9], bends = new float[2][3], cuffs = new float[2][3], wides = new float[2][4];
    private static final float[] joined = new float[CharacterDraw.HAND_SAYS], other = new float[CharacterDraw.HAND_SAYS];
    private static final float[] shoulder = new float[3], elbow = new float[3], wrist = new float[3], rigid = new float[3], palm = new float[3], across = new float[3];
    /**
     * For the trace, right then left, in radians: how far each wrist would be bent with the
     * hand as the game posed it, and how far it is bent as drawn. Written by the render
     * thread and read by the logic thread's trace, which may read a frame's two halves.
     */
    private static final float[][] wristSaid = new float[2][2];
    private static boolean armless;
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
    static float ownLight = 1f;

    /**
     * Works out one of the picture's arms for the weapon held up, and the turn of the hand it
     * ends in: from the hand the game has on the weapon, moved before the eye with it, back to
     * where that shoulder is, with the elbow dropped down and outwards. The hand is turned
     * about its palm to meet the arm. False if no part of the model has that hand and its
     * forearm, which is where the arm's own measures are taken from; the hand is then drawn
     * as the game posed it.
     */
    private static boolean reach(ModelSlotRenderData data, int side) {
        float[] view = handViews[side];
        System.arraycopy(heldView, 0, view, 0, 16);
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
        ViewModelMath.place(view, joined[0], joined[1], joined[2], wrist);
        ViewModelMath.place(view, joined[3], joined[4], joined[5], rigid);
        ViewModelMath.place(view, joined[6], joined[7], joined[8], palm);
        ViewModelMath.place(view, joined[9], joined[10], joined[11], across);
        for (int axis = 0; axis < 3; axis++) {
            across[axis] -= wrist[axis];
        }
        if (side == 1 && ViewModel.hasMuzzle) {
            for (int axis = 0; axis < 3; axis++) {
                float move = ViewModel.foreEnd[axis] - palm[axis];
                wrist[axis] += move;
                rigid[axis] += move;
                palm[axis] += move;
                view[12 + axis] += move;
            }
        }
        float[] at = side == 1 ? ViewModel.LEFT_SHOULDER : ViewModel.RIGHT_SHOULDER;
        ViewModelMath.place(fromAim, at[0], at[1], at[2], shoulder);
        ViewModelMath.turned(fromAim, side == 1 ? ViewModel.LEFT_ELBOW : ViewModel.RIGHT_ELBOW, pole);
        // The game's people are small beside the weapons they hold, and an arm of the model's
        // own lengths does not reach from a shoulder under the eye to a hand out on the
        // weapon. The solver then brings the shoulder to the hand, up into the picture, and
        // the arm's far end is a dome in front of the eye. The upper arm is out of sight,
        // so it is as long as the reach needs and the shoulder stays put.
        float reaching = (apart(shoulder, wrist) * REACH_SPARE + WRIST_SWINGS) / apart(wrist, rigid) - 1f;
        if (!ViewModelMath.arm(shoulder, pole, Math.max(UPPER_ARM, reaching), wrist, rigid, palm, turn, elbow, wristSaid[side])) {
            return false;
        }
        ViewModelMath.onto(turn, view);
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

    /** For the trace: how the wrists of the hands on the weapon are bent. */
    static String describeArms() {
        StringBuilder said = new StringBuilder();
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

    private static float apart(float[] a, float[] b) {
        float x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2];
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    /** Whether drawViewer left the weapon and the arms that hold it for drawHeld. */
    static boolean holding() {
        return holding != null;
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
    static void drawHeld(float tanHalfFieldOfView, float aspect, float nearFrom, float far) {
        ModelSlotRenderData data = holding;
        System.arraycopy(ViewCamera.projection(tanHalfFieldOfView, aspect, nearFrom, far), 0, projection, 0, 16);
        System.arraycopy(heldView, 0, combined, 0, 16);
        float red = data.ambientR, green = data.ambientG, blue = data.ambientB;
        data.ambientR = 1f;
        data.ambientG = 1f;
        data.ambientB = 1f;
        EYE.Begin();
        // The weapon first. What the picture draws itself says, beside its colour, which way
        // it faces, and the game's draw of the weapon does not: drawn after the hands, the
        // weapon would be lit where it covers them by the way the hand under it faces.
        for (int part = 0; part < parts(data).size(); part++) {
            ModelInstanceRenderData model = parts(data).get(part);
            // The game's own flash is a model on the muzzle, lit like the weapon it is on.
            // The picture draws the flash itself, as light.
            if (model.model == null || !model.model.isStatic || !inHand(model.modelInstance)
                    || model.ignoreLighting && MUZZLE.equals(model.modelInstance.attachmentNameParent)) {
                continue;
            }
            model.modelInstance.targetDepth = NO_DEPTH_SHIFT;
            GL11.glDepthMask(true);
            model.RenderCharacter(data);
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
            for (int part = 0; arms[side] && part < parts(data).size(); part++) {
                CharacterDraw.sleeve(parts(data).get(part), side == 1, joints[side], bends[side], cuffs[side], wides[side]);
            }
        }
        EYE.End();
        data.ambientR = red;
        data.ambientG = green;
        data.ambientB = blue;
    }

    /**
     * Draws the models within reach of the eye as the light sees them, into a shadow map. The
     * viewer is among them: a first-person view has no body yet, but it has a shadow.
     */
    static void cast(float[] lightMatrix) {
        System.arraycopy(IDENTITY, 0, projection, 0, 16);
        draw(lightMatrix, true, true, lightMatrix, Pipeline.focusX, Pipeline.focusY, SHADOW_REACH);
    }

    /** The same for a lamp's shadow map, and people only: a vehicle would stand in its own headlights. */
    static void castCharacters(float[] lightMatrix) {
        System.arraycopy(IDENTITY, 0, projection, 0, 16);
        only = ONLY_CHARACTERS;
        draw(lightMatrix, true, true, null, Pipeline.focusX, Pipeline.focusY, SHADOW_REACH);
        only = ALL;
        notingRoots = false;
    }

    /**
     * Where each person drawn into the view stands and the way they are turned, so that the
     * picture can lay cloth and skin on them that moves as they do.
     */
    static final int ROOTS = 16;
    static final float[] roots = new float[ROOTS * 4];
    static int rootsFound;
    private static boolean notingRoots;

    /**
     * Read from a vehicle's model, its steering wheel comes out this much higher than the wheel
     * the game draws, once the centre of mass is allowed for: the same in nine vehicles
     * measured against the depth of their own cabins, to about 0.02.
     */
    private static final float WHEEL_SETTLES = -0.015f;

    private static boolean fromSeat;

    /** The light the game gave the models last drawn, averaged over them. */
    static float ambient = 1f;

    /** The light the game gives the vehicle the viewer sits in. */
    static final float[] ownAmbient = {1f, 1f, 1f};

    /** How far from where it stands a model or its shadow's caster reaches, in tiles: a vehicle's half length and some over. */
    private static final float MODEL_REACH = 4f;

    /**
     * @param casting whether the models are drawn as the light sees them, into a shadow map
     * @param map     the matrix of a shadow map that looks along the sun's rays, which a model standing outside is left out of, or null
     */
    private static int draw(float[] view, boolean withViewer, boolean casting, float[] map, float eyeX, float eyeY, float reach) {
        IsoPlayer viewer = IsoPlayer.getInstance();
        int drawn = 0;
        float ambientSum = 0f;
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
            if (toX * toX + toY * toY > reach * reach) {
                continue;
            }
            if (map != null && outside(map, data)) {
                continue;
            }
            // The vehicle the viewer sits in is drawn apart, with the viewer's own body.
            if (!withViewer && Pipeline.seated && data.character == null && toX * toX + toY * toY < OWN_VEHICLE * OWN_VEHICLE) {
                continue;
            }
            if (mirrored) {
                // Nothing of the vehicle the mirror hangs in, nor who rides in it, and
                // nothing the mirror does not take in.
                float behind = toX * mirrorWayX + toY * mirrorWayY, aside = Math.abs(toX * mirrorWayY - toY * mirrorWayX);
                if (toX * toX + toY * toY < OWN_VEHICLE * OWN_VEHICLE || behind < 0f || aside > behind * mirrorWide + MIRROR_MARGIN) {
                    continue;
                }
            }
            // Drawn in full light. The game dims a model by the light where it stands and the
            // picture then lit it again, which left people dull and dark at noon; the game's
            // light is kept as a figure and the picture does the lighting once.
            float red = data.ambientR, green = data.ambientG, blue = data.ambientB;
            ambientSum += (red + green + blue) / 3f;
            data.ambientR = 1f;
            data.ambientG = 1f;
            data.ambientB = 1f;
            if (data.character != null && !data.inVehicle && rootsFound < ROOTS && notingRoots) {
                int at = rootsFound++ * 4;
                roots[at] = data.x;
                roots[at + 1] = data.y;
                roots[at + 2] = data.z * ViewCamera.LEVEL_HEIGHT + Terrain.lift(data.x, data.y, data.z);
                roots[at + 3] = data.animPlayerAngle;
            }
            if (data.character == null) {
                vehicle(data, i * 4, view);
            } else if (data.inVehicle) {
                seated(data, i * 4, view);
            } else {
                character(data, view, data.character == viewer, casting, false, false);
            }
            data.ambientR = red;
            data.ambientG = green;
            data.ambientB = blue;
            drawn++;
        }
        if (drawn > 0) {
            ambient = ambientSum / drawn;
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
     * part; of anyone else the picture draws the parts it can itself and leaves the game the
     * rest, what they hold and what hangs from them.
     */
    private static void character(ModelSlotRenderData data, float[] view, boolean own, boolean casting, boolean held, boolean bothHands) {
        place(data.x, data.y, data.z * ViewCamera.LEVEL_HEIGHT + Terrain.lift(data.x, data.y, data.z), data.animPlayerAngle, CHARACTER_SCALE);
        multiply(view, placed, combined);
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
                continue;
            }
            model.modelInstance.targetDepth = NO_DEPTH_SHIFT;
            // The game's draw ends by putting its own remembered state back, which has depth
            // writes off. Without them the next part, or the next model, is painted over
            // whatever is nearer.
            GL11.glDepthMask(true);
            model.RenderCharacter(data);
        }
        EYE.End();
    }

    /**
     * Says to the picture's draw of a person what the game's light is on the square they stand
     * on, read as the world's own squares are, so that a person in a dark room is as dark as
     * the room. Nothing is said of a square the viewer has not seen: the game holds no light for it.
     */
    private static void lightWhere(ModelSlotRenderData data) {
        IsoPlayer viewer = IsoPlayer.getInstance();
        IsoGridSquare square = data.character.getCurrentSquare();
        if (viewer == null || square == null || !square.isSeen(viewer.getIndex())) {
            return;
        }
        ColorInfo light = square.lighting[viewer.getIndex()].lightInfo();
        CharacterDraw.stands((light.r + light.g + light.b) / 3f, square.isOutside());
    }

    /**
     * A character in a vehicle. The game stands its camera where the vehicle is and then seats
     * the character from there itself, by the seat's place in the vehicle and the way the
     * vehicle is turned and tipped. Here the camera stands the same place as seen from the eye,
     * and the game does its seating on top of that.
     */
    private static void seated(ModelSlotRenderData data, int at, float[] view) {
        place(stood[at], stood[at + 1], stood[at + 2] * ViewCamera.LEVEL_HEIGHT + Terrain.lift(stood[at], stood[at + 1], stood[at + 2]) + VEHICLE_RAISE,
                stood[at + 3], VEHICLE_SCALE);
        multiply(view, placed, combined);
        gameCamera = ModelCamera.instance;
        ModelCamera.instance = EYE;
        Model.CharacterModelCameraBegin(data);
        ModelCamera.instance = gameCamera;
        for (int part = 0; part < parts(data).size(); part++) {
            ModelInstanceRenderData model = parts(data).get(part);
            model.modelInstance.targetDepth = NO_DEPTH_SHIFT;
            GL11.glDepthMask(true);
            model.RenderCharacter(data);
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
    private static void vehicle(ModelSlotRenderData data, int at, float[] view) {
        place(stood[at], stood[at + 1], stood[at + 2] * ViewCamera.LEVEL_HEIGHT + Terrain.lift(stood[at], stood[at + 1], stood[at + 2]) + VEHICLE_RAISE,
                stood[at + 3], VEHICLE_SCALE);
        multiply(view, placed, combined);
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
    private static void place(float x, float y, float up, float angle, float scale) {
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
