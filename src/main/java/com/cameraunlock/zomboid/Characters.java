package com.cameraunlock.zomboid;

import zombie.iso.IsoWorld;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.characters.animals.IsoAnimal;
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
import zombie.core.textures.Texture;
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
 * shadow. Vehicles, animals and held objects use the game's renderer.
 * CharacterDraw draws bodies, clothes and hair from the game's bones and
 * textures. The viewer's head is hidden, and arms holding a weapon are drawn
 * separately with it.
 *
 * Render thread only.
 */
public final class Characters {
    final View view;

    Characters(View view) {
        this.view = view;
    }

    /** The model's own units to tiles: the game draws its characters at one and a half times their size. */
    static final float CHARACTER_SCALE = 1.5f;
    /** The depth the game's model shader leaves a model at when it is not being fitted among sprites. */
    private static final float NO_DEPTH_SHIFT = 0.5f;
    /** Whether the part being drawn is being drawn by the picture, and not by the game for its own view. Render thread. */
    private boolean ours;
    /**
     * The skin the game is about to draw a part of an animal in, and the finer one the picture
     * has it wear in its place: see wear. The game draws an animal itself, by a program of its
     * own, so the finer skin is handed to that draw. Render thread.
     */
    private Texture wornFor, worn;

    /**
     * Whether a part the game is about to draw is one of its own view's, under a first person
     * picture that covers that view: it is then not drawn. Called from the game's code.
     */
    public boolean gamesOwnDrawIsCovered() {
        return !ours && view.firstPerson.coversTheGamesView();
    }

    /** Called by the game's draw of a part as it begins: a part of an animal near the eye wears its finer skin. */
    void wear(ModelInstanceRenderData part) {
        if (ours && wornFor != null && part.tex == wornFor) {
            part.tex = worn;
        }
    }

    private static final int GL_CW = 0x0900, GL_CCW = 0x0901;

    /**
     * Turns the winding round for the game's draws in a mirror's picture. The game culls by
     * glCullFace and leaves the winding counter-clockwise, and a mirror's view, of the other hand,
     * turns every triangle round: a hat or a car the game drew there showed only its inside.
     * The picture's own draws swap their cull instead (CharacterDraw.mirrored).
     */
    private static void turnGameWinding(boolean turned) {
        if (CharacterDraw.mirrored) {
            GL11.glFrontFace(turned ? GL_CW : GL_CCW);
        }
    }

    /** Has the game draw one part of a model for the picture. */
    private void drawPart(ModelInstanceRenderData model, ModelSlotRenderData data) {
        CharacterDraw.release();
        long began = TALLY ? System.nanoTime() : 0L;
        ours = true;
        turnGameWinding(true);
        try {
            model.RenderCharacter(data);
        } finally {
            turnGameWinding(false);
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
    static final float[] IDENTITY = {1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f};

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
    /** The pictures a vehicle is drawn into, its colour and its lamps, while the picture of lamps is bound; null otherwise. See VehicleLamps. */
    int[] lamps;
    private static final int[] COLOUR_ONLY = {0x8CE0};
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
            notedFrames++;
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
        notedFrames++;
        notedIn = null;
        holding = null;
        viewer = null;
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

    /**
     * Render thread: the player the frame being drawn was built for. The game empties the slot
     * of a player who has died while frames built before that are still to be drawn.
     */
    IsoPlayer viewer;

    /** Whether there is anything to draw this frame, asked as each picture begins. The noted models of an earlier frame are dropped. */
    boolean ready() {
        drawing |= 1 << view.index;
        if (noted.isEmpty() || notedIn != SpriteRenderer.instance.getRenderingState()) {
            noted.clear();
            notedFrames++;
            return false;
        }
        return true;
    }

    /** Whether the game drew any vehicle this frame: with none, a picture's pass for vehicles has nothing to draw. */
    boolean vehiclesNoted() {
        for (int i = 0; i < noted.size(); i++) {
            if (noted.get(i).character == null) {
                return true;
            }
        }
        return false;
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
        playersByRoom = Online.shownAsAlone(true);
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
        notingCars = vehicles;
        if (notingCars) {
            carsFound = 0;
            carsFromX = camera.eyeX;
            carsFromY = camera.eyeY;
        }
        // Seen from outside the viewer is one of the people in the picture.
        ViewCamera.view(camera, eyeView);
        CharacterDraw.nearCut = scope ? 0f : CharacterDraw.NEAR_CUT;
        CharacterDraw.looking(eyeView);
        int drawn = draw(eyeView, view.pipeline.outside, false, null, camera.eyeX, camera.eyeY, Float.MAX_VALUE);
        CharacterDraw.nearCut = 0f;
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
        notingCars = false;
        seenFrom = null;
        return drawn;
    }

    /** While the eye's picture or a scope's is drawn, who it leaves out by the room they stand in, or null: see FigureRooms. */
    private FigureRooms byRoom;
    /** Whether other players are left out by room too: not where players can fight, where the game shows them. See Online.shownAsAlone. */
    private boolean playersByRoom;
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
    /** The views of the pictures this frame that read the sun's maps, the eye's first, and the tangents of half of each picture's width and height. */
    private final float[][] seers = new float[SEERS][16];
    private final float[] seersWide = new float[SEERS], seersHigh = new float[SEERS];
    private int seersFound;
    /** The eye, a mirror and a scope. */
    private static final int SEERS = 3;
    private final float[] past = new float[5];

    /**
     * Said before the sun's maps are cast: what the frame's eye will see. A model whose shadow
     * cannot fall where any picture this frame sees is then left out of them.
     */
    void seenBy(ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect) {
        seersFound = 0;
        alsoSeenBy(camera, tanHalfFieldOfView, aspect);
    }

    /** Said after seenBy, of another picture this frame that reads the sun's maps: a mirror's or a scope's. */
    void alsoSeenBy(ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect) {
        ViewCamera.view(camera, seers[seersFound]);
        seersHigh[seersFound] = tanHalfFieldOfView;
        seersWide[seersFound] = tanHalfFieldOfView * aspect;
        seersFound++;
    }

    private boolean shadesNothingSeen(float[] map, float x, float y, float up, float round) {
        for (int i = 0; i < seersFound; i++) {
            if (!PictureEdges.shadesNothingSeen(seers[i], seersWide[i], seersHigh[i], map, x, y, up, round)) {
                return false;
            }
        }
        return true;
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
        CharacterDraw.looking(eyeView);
        notingCars = vehicles;
        if (notingCars) {
            carsFound = 0;
            carsFromX = camera.eyeX;
            carsFromY = camera.eyeY;
        }
        CharacterDraw.plainly = true;
        int drawn;
        try {
            drawn = draw(eyeView, false, false, null, camera.eyeX, camera.eyeY, vehicles ? MIRROR_VEHICLES : MIRROR_PEOPLE);
        } finally {
            CharacterDraw.plainly = false;
        }
        notingCars = false;
        mirrored = false;
        only = ALL;
        return drawn;
    }

    /**
     * While set, the plane of a mirror in a room whose picture is drawn, as LookingGlass
     * gives it: nothing behind the wall it hangs on is in it.
     */
    private float[] glassPlane;
    /** While set, the viewer is drawn whole, head and all, as anyone else is: the picture is of them in a mirror. */
    private boolean viewerWhole;
    /** How far behind a mirror's plane the middle of a person may be and they still be drawn in it, in tiles: they lean on its wall. */
    private static final float BEHIND_GLASS = 0.3f;

    /**
     * Draws the people, the viewer among them, or the vehicles, that a mirror in a room
     * shows, into whatever target is bound, whose depth has to be that of the world the
     * mirror shows. camera is the eye's image behind the glass (LookingGlass), and plane the
     * glass's. Returns how many were drawn.
     */
    int drawInGlass(ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float near, float far, boolean vehicles, float[] plane) {
        kind = BY_MIRROR;
        ViewCamera.projection(tanHalfFieldOfView, aspect, near, far, projection);
        aside(projection);
        only = vehicles ? ONLY_VEHICLES : ONLY_CHARACTERS;
        glassPlane = plane;
        viewerWhole = true;
        seenHigh = tanHalfFieldOfView;
        seenWide = tanHalfFieldOfView * aspect;
        ViewCamera.view(camera, eyeView);
        seenFrom = eyeView;
        CharacterDraw.looking(eyeView);
        notingCars = vehicles;
        if (notingCars) {
            carsFound = 0;
            carsFromX = camera.eyeX;
            carsFromY = camera.eyeY;
        }
        CharacterDraw.mirrored = true;
        try {
            return draw(eyeView, true, false, null, camera.eyeX, camera.eyeY, Float.MAX_VALUE);
        } finally {
            CharacterDraw.mirrored = false;
            notingCars = false;
            glassPlane = null;
            viewerWhole = false;
            seenFrom = null;
            only = ALL;
        }
    }

    /** A model this far to the side of what a mirror takes in may still reach into it: a vehicle is that long. */
    private static final float MIRROR_MARGIN = 4f;

    /** The frame has drawn the models it was given. */
    void done() {
        if (TALLY) {
            tally();
        }
        noted.clear();
        notedFrames++;
        holding = null;
        carrying = false;
    }

    /**
     * Draws the viewer's body without their head into the bound target, in the light the frame's
     * scene has for what the viewer holds (SkyState.held).
     * False if the viewer is not among the models.
     */
    boolean drawViewer(ViewCamera.Pose camera, float[] held, float tanHalfFieldOfView, float aspect, float nearFrom, float far) {
        holding = null;
        carrying = false;
        ownLight = held[HeldLight.LIGHT];
        ownOutside = held[HeldLight.OUTSIDE] > 0.5f;
        System.arraycopy(held, HeldLight.CABIN, ownAmbient, 0, 3);
        if (view.pipeline.seated) {
            // The body is not drawn from a seat, so a weapon raised out of a window is held by
            // the hands drawHeld draws on it and by nothing else.
            for (int i = 0; i < noted.size(); i++) {
                ModelSlotRenderData data = noted.get(i);
                if (!repainted && data.character != null && data.character == viewer) {
                    settleTheWeapon(data, camera);
                    break;
                }
            }
            return drawOwnVehicle(camera, tanHalfFieldOfView, aspect, nearFrom, far);
        }
        for (int i = 0; i < noted.size(); i++) {
            ModelSlotRenderData data = noted.get(i);
            if (data.character != null && data.character == viewer && !data.inVehicle) {
                settleTheWeapon(data, camera);
                settleTheTask(data, camera);
                drawOwnBody(data, camera, tanHalfFieldOfView, aspect, nearFrom, far);
                return true;
            }
        }
        return false;
    }

    /**
     * How many tiles a metre of the mod's arm model is for the viewer, as the arms on a held weapon
     * are drawn. The game draws every figure but an animal at a final scale of 1
     * (ModelSlotRenderData), so it needs no figure of the viewer among the frame's models, which
     * a seated viewer mostly is not.
     */
    float viewerArmScale() {
        return ARM_SCALE;
    }

    /** Draws the driver's arms on the wheel, posed into skins, into whatever target is bound. */
    void drawWheelArms(float[] projection, float[][] skins) {
        OwnArms.Worn worn = wornOnArms();
        float[] stains = view.ride.dress.stains;
        // Under the vehicle's roof no snow falls on them, and no square's light is said of them: the pass
        // that lights them gives them the sky's through the cabin's windows (uCabinAround).
        OwnArms.snowed(0f);
        // A hand that holds a weapon up is on the weapon, and not on the wheel as well.
        if (rightOnWheel()) {
            OwnArms.draw(projection, skins[0], false, viewer.isFemale(), view.ride.skin, -1f, false, worn, stains);
        }
        if (leftOnWheel()) {
            OwnArms.draw(projection, skins[1], true, viewer.isFemale(), view.ride.skin, -1f, false, worn, stains);
        }
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
                float groundUp = view.pipeline.seatedGroundUp;
                aboard(sat, at);
                String script = view.ride.seatedIn;
                ViewCamera.view(camera, eyeView);
                CharacterDraw.looking(eyeView);
                if (script != null && !parts(data).isEmpty()) {
                    multiply(projection, eyeView, combined);
                    VehicleMeshes.note(cabin, view.ride, script, parts(data).get(0), placed, view.pipeline.seatedAxes, WHEEL_SETTLES, view.pipeline.seatedX, view.pipeline.seatedY, groundUp,
                            data.centerOfMassY, combined);
                }
                float red = data.ambientR, green = data.ambientG, blue = data.ambientB;
                data.ambientR = 1f;
                data.ambientG = 1f;
                data.ambientB = 1f;
                fromSeat = true;
                CharacterDraw.release();
                vehicle(data, i * 4, eyeView);
                fromSeat = false;
                data.ambientR = red;
                data.ambientG = green;
                data.ambientB = blue;
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
        ViewModel.Bare bare = view.viewModel.bare;
        if (hands == null && bare != null && CharacterDraw.cuts() && Own.arms() && OwnArms.model() != null) {
            // A shove or a stamp with no weapon in hand: the mod's own arms, and the body's left out.
            // Whatever else the game has in a hand is carried in that hand through it.
            ViewCamera.between(aim, camera, eyeFromAim, fromAim);
            ArmModel model = OwnArms.model();
            float scale = ARM_SCALE * data.finalScale;
            ModelInstanceRenderData right = weaponOf(data), left = leftOf(data);
            for (int side = 0; side < 2; side++) {
                boolean full = side == 0 ? bare.rightFull() || right != null : bare.leftFull() || left != null;
                BareHands.hand(bare.way(), bare.reached(), bare.past(), bare.struck(), full, side == 1, bareHands[side]);
                barePoses[side] = model.pose(BareHands.poseOf(bare.way(), full));
                float[] hand = bareHands[side];
                TimedActs.Hand seen = seenHands[side];
                ViewModelMath.place(fromAim, hand[0] * scale, hand[1] * scale, hand[2] * scale, seen.at);
                System.arraycopy(hand, 3, bareWay, 0, 3);
                ViewModelMath.turned(fromAim, bareWay, seen.out);
                System.arraycopy(hand, 6, bareWay, 0, 3);
                ViewModelMath.turned(fromAim, bareWay, seen.fingers);
                ArmPose.unit(seen.out[0], seen.out[1], seen.out[2], seen.out);
                ArmPose.unit(seen.fingers[0], seen.fingers[1], seen.fingers[2], seen.fingers);
            }
            if (right != null && !inTheHand(data, 0, right, seenHands[0], scale, heldView) || left != null && !inTheHand(data, 1, left, seenHands[1], scale, leftView)) {
                if (!bareCarryRefused) {
                    bareCarryRefused = true;
                    Log.info("what is in the viewer's hand could not be put in the mod's own hand for a shove or a stamp (the game's hand, or the item's mesh or place, is not read yet): the game's own arms are drawn for it, as often as that happens");
                }
                return;
            }
            if (ArmHolds.bare(model, scale, bareHands, barePoses, fromAim, armSkins, armSaid)) {
                holding = data;
                carrying = true;
                armHands = null;
                armFemale = bare.female();
                armSkin = bare.skin();
                armCarries.what = ArmHolds.NOTHING;
                ownArms = true;
                heldBoth = true;
                // Nothing with a muzzle is held up: no flash is drawn.
                view.viewModel.hasMuzzle = false;
            } else if (!bareRefused) {
                bareRefused = true;
                Log.info("the mod's own arms could not be placed for a shove or a stamp with no weapon in hand: the game's own arms are drawn for it, as often as that happens");
            }
            return;
        }
        if (hands != null && CharacterDraw.cuts()) {
            ModelInstanceRenderData weapon = weaponOf(data);
            ViewCamera.between(aim, camera, eyeFromAim, fromAim);
            boolean own = Own.arms() && OwnArms.model() != null;
            float[] hand = hands.firearm() ? null : gameHand(data);
            swungLoading = false;
            MeleeHold.Shape swung = weapon == null || hands.firearm() ? null : swungShape(weapon, hands.item());
            // Until the mesh of what is swung is read, the game's own arms hold it where the game has it, as a firearm's do.
            own &= !swungLoading;
            if (weapon != null && view.viewModel.place(weapon.xfrm.get(weaponPlace), data.finalScale, weapon.modelInstance, hands,
                    CHARACTER_SCALE, own, hand, swung, fromAim, heldView)) {
                holding = data;
                armHands = hands;
                armFemale = hands.female();
                armSkin = hands.skin();
                ownArms = own && reachOwn(data, hands);
                if (ownArms) {
                    return;
                }
                if (own) {
                    // The weapon was placed for the mod's hands, which are another size and hold it another way.
                    if (!armsRefused && !gripLoading) {
                        armsRefused = true;
                        Log.info("the mod's own arms could not be placed on " + hands.item() + ": the game's own are drawn on it, as often as that happens");
                    }
                    if (!view.viewModel.place(weapon.xfrm.get(weaponPlace), data.finalScale, weapon.modelInstance, hands, CHARACTER_SCALE, false, hand, swung, fromAim, heldView)) {
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

    /**
     * Whether the hands can be brought up to the timed action under way is settled before the
     * body is drawn, as for a weapon. Only the mod's own arms are brought up: with the game's
     * own, its animation of the action is drawn as it is.
     */
    private void settleTheTask(ModelSlotRenderData data, ViewCamera.Pose camera) {
        ViewModel.Task task = view.viewModel.task;
        if (task == null || holding != null || !CharacterDraw.cuts() || !Own.arms() || OwnArms.model() == null) {
            return;
        }
        ViewCamera.between(aim, camera, eyeFromAim, fromAim);
        ModelInstanceRenderData right = weaponOf(data), left = leftOf(data);
        TimedActs.hands(task.kind(), task.low(), task.time(), right != null, left != null, task.up(), taskHands[0], taskHands[1]);
        float scale = ARM_SCALE * data.finalScale;
        for (int side = 0; side < 2; side++) {
            TimedActs.Hand from = taskHands[side], to = seenHands[side];
            ViewModelMath.place(fromAim, from.at[0] * scale, from.at[1] * scale, from.at[2] * scale, to.at);
            ViewModelMath.turned(fromAim, from.fingers, to.fingers);
            ViewModelMath.turned(fromAim, from.out, to.out);
            to.pose = from.pose;
        }
        if (right != null && !inTheHand(data, 0, right, seenHands[0], scale, heldView) || left != null && !inTheHand(data, 1, left, seenHands[1], scale, leftView)) {
            if (!taskCarryRefused) {
                taskCarryRefused = true;
                Log.info("what is in the viewer's hand could not be put in the mod's own hand at " + task.anim() + " (the game's hand, or the item's mesh or place, is not read yet): the game's own animation of it is drawn, as often as that happens");
            }
            return;
        }
        if (!ArmHolds.placeTask(OwnArms.model(), scale, seenHands, fromAim, armSkins, armSaid)) {
            if (!taskRefused) {
                taskRefused = true;
                Log.info("the mod's own arms could not be placed at " + task.anim() + ": the game's own animation of it is drawn, as often as that happens");
            }
            return;
        }
        holding = data;
        carrying = true;
        armHands = null;
        armFemale = task.female();
        armSkin = task.skin();
        ownArms = true;
        heldBoth = true;
        // Nothing with a muzzle is held up: no flash is drawn.
        view.viewModel.hasMuzzle = false;
    }

    /**
     * The matrix that carries what a hand of the game's holds, with that hand, to where the
     * mod's hand is before the eye: from the axes the person is stood from, at the scale the
     * game draws them, to the view's. False while the hand or what it holds cannot be read.
     *
     * @param side 0 the right hand, 1 the left
     * @param hand the mod's hand, in the view
     */
    private boolean inTheHand(ModelSlotRenderData data, int side, ModelInstanceRenderData item, TimedActs.Hand hand, float scale, float[] out) {
        Model model = item.model;
        if (model.mesh == null) {
            return false;
        }
        boolean found = false;
        for (int part = 0; !found && part < parts(data).size(); part++) {
            found = CharacterDraw.hand(parts(data).get(part), side == 1, data.finalScale, joined);
        }
        if (!found) {
            return false;
        }
        // The line across the wrist is the wrist's widest, either way along it: the palm is on
        // the side of the hand what it holds is on, and kept when that is not plain.
        float[] xfrm = item.xfrm.get(itemPlace);
        if (!(xfrm[1] * xfrm[1] + xfrm[5] * xfrm[5] + xfrm[9] * xfrm[9] > 0f)) {
            // The frame something is put in the hand the game has not placed it yet, and its matrix is all noughts:
            // the palm's side found from it would be kept.
            return false;
        }
        Vector3f low = model.mesh.minXyz, high = model.mesh.maxXyz;
        float cx = (low.x + high.x) / 2f, cy = (low.y + high.y) / 2f, cz = (low.z + high.z) / 2f;
        for (int axis = 0; axis < 3; axis++) {
            itemMiddle[axis] = (xfrm[axis * 4] * cx + xfrm[axis * 4 + 1] * cy + xfrm[axis * 4 + 2] * cz + xfrm[axis * 4 + 3]) * data.finalScale;
        }
        float facing = ViewModelMath.palmFacing(joined, itemMiddle) * CHARACTER_SCALE;
        if (Math.abs(facing) > PALM_PLAIN || palmSides[side] == 0f) {
            float sideFound = facing < 0f ? -1f : 1f;
            if (palmSides[side] == 0f) {
                if (DevBuild.ON) {
                    Log.info(String.format(Locale.ROOT, "the %s palm is first found facing %s the game's hand's own line, %.3f tiles from the middle of %s", side == 0 ? "right" : "left",
                            sideFound > 0f ? "along" : "against", facing, model.name));
                }
            }
            palmSides[side] = sideFound;
        }
        // The middle of the game's hand goes where the middle of the mod's is, behind what it holds.
        ViewModelMath.carry(joined, palmSides[side], hand.fingers, hand.out, hand.at, CHARACTER_SCALE, HELD_IN * scale, out);
        return true;
    }

    /** What is in the viewer's left hand, or null: the one part the game hangs from that hand's bone. */
    private static ModelInstanceRenderData leftOf(ModelSlotRenderData data) {
        for (int part = 0; part < parts(data).size(); part++) {
            ModelInstanceRenderData model = parts(data).get(part);
            if (model.model != null && model.model.isStatic && LEFT_HAND_BONE.equals(model.modelInstance.parentBoneName)) {
                return model;
            }
        }
        return null;
    }

    /** Whether a part is what the left hand holds, or is fixed to it. */
    private static boolean inLeftHand(ModelInstance instance) {
        for (ModelInstance of = instance; of != null; of = of.parent) {
            if (LEFT_HAND_BONE.equals(of.parentBoneName)) {
                return true;
            }
        }
        return false;
    }

    /** The bone the game hangs what is in the left hand from. */
    private static final String LEFT_HAND_BONE = "Bip01_Prop2";
    /**
     * Whether the hands are drawn with what the game has in them, as they are at a timed action
     * and in a shove or a stamp with no weapon in hand, from drawViewer until the frame is done.
     */
    private boolean carrying;
    private boolean taskRefused;
    private final TimedActs.Hand[] taskHands = {new TimedActs.Hand(), new TimedActs.Hand()}, seenHands = {new TimedActs.Hand(), new TimedActs.Hand()};
    /** The matrix that carries what the left hand holds to before the eye, as heldView does what the right holds. */
    private final float[] leftView = new float[16], itemPlace = new float[16];
    private final float[] itemMiddle = new float[3];
    /** For each hand, right then left, which way along the game's hand's own line (ViewModelMath.palmFacing) its palm faces: 0 until it is found. */
    private final float[] palmSides = new float[2];
    /** Further than this from the plane of the game's hand, in tiles, what it holds says which side its palm is on. */
    private static final float PALM_PLAIN = 0.01f;
    /** How far behind the middle of what the mod's hand holds the middle of the hand is, in metres. */
    private static final float HELD_IN = 0.03f;

    private void drawOwnBody(ModelSlotRenderData data, ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float nearFrom, float far) {
        ViewCamera.projection(tanHalfFieldOfView, aspect, nearFrom, far, projection);
        // In full light, as everyone else is: the picture does the lighting, once,
        // by the light what the viewer holds is in (HeldLight).
        float red = data.ambientR, green = data.ambientG, blue = data.ambientB;
        data.ambientR = 1f;
        data.ambientG = 1f;
        data.ambientB = 1f;
        ViewCamera.view(camera, eyeView);
        CharacterDraw.looking(eyeView);
        // Put back whatever the draw does: the other view's people are drawn by the same flag.
        CharacterDraw.fromTheEye = true;
        try {
            character(data, standsUp(data, System.nanoTime()), eyeView, true, false, holding != null, heldBoth, 0f);
        } finally {
            CharacterDraw.fromTheEye = false;
            CharacterDraw.release();
        }
        data.ambientR = red;
        data.ambientG = green;
        data.ambientB = blue;
    }

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
    /** Where the eye the frame is drawn from is from the aim's, in the world's axes: see ViewCamera.between. */
    final float[] eyeFromAim = new float[3];
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

    /** Whether the driver's right hand is on the wheel this frame: not while it holds a weapon up. Read after drawViewer. */
    boolean rightOnWheel() {
        return holding == null;
    }

    /** Whether the driver's left hand is on the wheel this frame: not while it is on the weapon too. Read after drawViewer. */
    boolean leftOnWheel() {
        return holding == null || !heldBoth;
    }
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

    /** The light what the viewer holds is in (HeldLight), and whether that is out of doors. */
    float ownLight = 1f;
    private boolean ownOutside = true;

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
        ViewModel.Held hands = armHands;
        boolean laid = side == 1 && view.viewModel.hasMuzzle && hands != null && foreEndHeld(hands);
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
        boolean fitted = view.viewModel.hasMuzzle && hands != null && foreEndHeld(hands);
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

    /** Whether the arms on the weapon held up are the mod's own, and for each, right then left, where its bones are. */
    private boolean ownArms, armsRefused, bareRefused;
    /** How the weapon was held when its arms were placed: they are drawn for that, whatever the logic thread has said since. */
    private ViewModel.Held armHands;

    /** Whether what is held is one of the mod's own models whose fore-end the left hand holds with the pump gun's hold. */
    private static boolean foreEndHeld(ViewModel.Held held) {
        OwnModel own = held.ownDrawn();
        return own != null && own.foreEndHeld;
    }

    /** Whose arms they are: a woman's or not, and the game's skin. */
    private boolean armFemale;
    private int armSkin;
    /** Where each hand is in a shove or a stamp with no weapon in hand, right then left, and its pose: see BareHands. */
    private final float[][] bareHands = new float[2][BareHands.SAYS];
    /** A way out of a palm or along the fingers being turned into the view: scratch. */
    private final float[] bareWay = new float[3];
    private boolean bareCarryRefused, taskCarryRefused;
    private final int[] barePoses = new int[2];
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
            // Something swung is held at the view model's grip: where its mesh says the hand goes on its handle, or where
            // the game's hand has it on a mesh that says nothing.
            grip = view.viewModel.gripSeen;
        }
        ArmHolds.Weapon weapon = new ArmHolds.Weapon(hands.firearm(), handgun, grip, view.viewModel.pointing, view.viewModel.over, placed, view.viewModel.secondFist, !hands.firearm() && view.viewModel.spearHeld);
        ArmHolds.Doing doing = new ArmHolds.Doing(hands.aim(), heldBoth, hands.action(), hands.cycled(), hands.doing(), hands.done(), hands.round(), hands.pace(), hands.seated(), hands.clip(), hands.out(), hands.fetching(), hands.fetchTakes(), view.viewModel.turned);
        long now = System.nanoTime();
        armSettled.seconds = Math.min((now - armsAsOf) / 1e9f, 0.1f);
        armsAsOf = now;
        int arms = ArmHolds.place(OwnArms.model(), ARM_SCALE * data.finalScale, weapon, doing, fromAim, armSkins, armSaid, armCarries, armSettled);
        // The left hand comes to a weapon held in one hand to load it or rack it.
        heldBoth = arms == 2;
        return arms > 0;
    }

    /**
     * The firearm held, where the view has its mesh: the mod's own model where one stands for
     * it, the game's otherwise. Null for a firearm whose mesh has no grip the hands can be
     * put on, which is then held by the game's own arms.
     */
    private ArmHolds.Placed placed(ModelSlotRenderData data, ViewModel.Held hands) {
        gripLoading = false;
        ModelInstanceRenderData held = weaponOf(data);
        if (held == null || !view.viewModel.hasMuzzle) {
            return null;
        }
        float pumped = view.viewModel.pumpedPlaced;
        OwnModel own = hands.ownDrawn();
        if (own != null) {
            float out = muzzleOut();
            own.hold(meshView, heldView, weaponPlace, data.finalScale, out);
            own.hold(meshStill, view.viewModel.still, weaponPlace, data.finalScale, out);
            // A pump gun's fore-end is where its maker put it and slides as far as the model's does; the rifle's bolt is drawn back as far as its model's.
            // A gun that opens to be loaded says where its rounds go in, wherever its moving parts have it.
            return heldMesh.set(own.shape, meshView, meshStill, own.pump ? own.travel * pumped : 0f,
                    own.pump ? own.foreEnd * own.muzzle[1] : Float.NaN, own.pump ? ArmHolds.BOLT_TRAVEL : own.travel)
                    .port(view.ownGun(own).loadingPort(own));
        }
        WeaponShape shape = shapeOf(held, hands.item(), view.viewModel.handgun);
        if (shape == null) {
            return null;
        }
        meshTo(heldView, data.finalScale, meshView);
        meshTo(view.viewModel.still, data.finalScale, meshStill);
        return heldMesh.set(shape, meshView, meshStill, ViewModel.PUMP_TRAVEL * shape.muzzle * pumped, Float.NaN,
                view.viewModel.handgun ? ArmHolds.SLIDE_TRAVEL : ArmHolds.BOLT_TRAVEL);
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
    /** What each of the game's models of something swung says of it, read once for the mesh and the buffer the model script has: null where it says nothing. */
    private record Swung(Model model, VertexBufferObject from, MeleeHold.Shape shape) {}
    private static final java.util.IdentityHashMap<ModelScript, Swung> SWUNG_SHAPES = new java.util.IdentityHashMap<>();
    /** Whether the mesh of what is swung was still loading when its shape was asked for: the game's own arms hold it this frame. */
    private boolean swungLoading;

    /**
     * What the mesh of something swung says of where it strikes and how far it reaches, or null while the mesh is
     * loading (`swungLoading`) and for a mesh that says nothing, which is said once: that is held where the game's hand
     * has it. Render thread.
     */
    private MeleeHold.Shape swungShape(ModelInstanceRenderData held, String item) {
        Model model = held.model;
        ModelScript script = held.modelInstance.modelScript;
        VertexBufferObject from = model.mesh == null ? null : model.mesh.vb;
        Swung known = SWUNG_SHAPES.get(script);
        if (known != null && known.model == model && known.from == from) {
            return known.shape;
        }
        StringBuilder why = new StringBuilder();
        CharacterDraw.Shape mesh = CharacterDraw.shapeOf(model, why);
        if (mesh == null && why.isEmpty()) {
            swungLoading = true;
            return null;
        }
        MeleeHold.Shape shape = mesh == null ? null : MeleeHold.of(mesh.places());
        if (shape == null) {
            Log.info("the mod's own hands cannot be put on " + item + " by its mesh (" + model.name + ": "
                    + (why.isEmpty() ? "its mesh has no length along y" : why) + "): it is held where the game's hand has it, and swung as it comes");
        } else {
            if (DevBuild.ON) {
                Log.info(String.format(Locale.ROOT, "%s (%s) is swung from %.3f to %.3f along its mesh, striking with its side (%.2f, %.2f) in x and z",
                        item, model.name, shape.butt(), shape.tip(), shape.strikeX(), shape.strikeZ()));
            }
        }
        SWUNG_SHAPES.put(script, new Swung(model, from, shape));
        return shape;
    }

    /** Whether the held firearm's mesh was still loading when its shape was asked for, so the game's own arms hold it this frame and nothing is said. */
    private boolean gripLoading;

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
                gripLoading = true;
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
        if (DevBuild.ON) {
            Log.info(String.format(Locale.ROOT, "the hands on %s (%s): grip %.4f along and %.4f up of its mesh, its line %.0f degrees from upright, %.3f thick; guard %.4f to %.4f; fore-end %.4f to %.4f, level %.4f to %.4f%s",
                    item, model.name, ArmPose.dot(shape.grip, shape.along), ArmPose.dot(shape.grip, shape.up), Math.toDegrees(Math.atan2(ArmPose.dot(shape.gripLine, shape.along), ArmPose.dot(shape.gripLine, shape.up))),
                    shape.gripThick / shape.perMetre, shape.guardBack, shape.guardFront, shape.foreFrom, shape.foreTo, shape.levelFrom, shape.levelTo,
                    shape.magazine ? String.format(Locale.ROOT, "; magazine %.4f to %.4f, its foot at %.4f", shape.magazineBack, shape.magazineFront, shape.magazineFoot) : ""));
        }
        return shape;
    }

    /** The game's mesh units in a metre of a long gun, which it makes the size long guns are: its pump gun is 0.4461 of them from grip to muzzle, 0.9015 m. */
    static final float PER_METRE = 0.4461f / 0.9015f;

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

    /** For the trace: the light the hands and what they hold are in, and how the wrists of the hands on the weapon are bent. */
    String describeArms() {
        StringBuilder said = new StringBuilder(String.format(Locale.ROOT, "held in light %.2f %s, ", ownLight, ownOutside ? "out of doors" : "indoors"));
        if (ownArms) {
            for (int side = 0; side < (heldBoth ? 2 : 1); side++) {
                float[] of = armSaid[side];
                said.append(String.format(Locale.ROOT, "%s own arm: wrist bent %.0f, forearm turned %.0f, upper arm drawn %.2f, shoulder gave %.3f, wrist (%.2f, %.2f, %.2f) elbow (%.2f, %.2f, %.2f)",
                        side == 0 ? "right" : "; left", Math.toDegrees(of[ArmPose.BENT]), Math.toDegrees(of[ArmPose.TWISTED]), of[ArmPose.DRAWN], of[ArmPose.GAVE],
                        of[ArmPose.WRIST], of[ArmPose.WRIST + 1], of[ArmPose.WRIST + 2], of[ArmPose.ELBOW], of[ArmPose.ELBOW + 1], of[ArmPose.ELBOW + 2]));
            }
            return said.toString();
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

    private final float[] partPlace = new float[16], partWas = new float[16], mountAt = new float[3];

    /**
     * Draws a part the game fixes to its weapon on the mod's own model of the weapon instead,
     * where the model says it goes (OwnModel.mount): the part moved for the one draw so that the
     * point the game fixes it by is there, and left out where the model has no place for it.
     *
     * @param unitsPerMetre the weapon's units to a metre of the model, as it is held (OwnModel.hold)
     */
    private void drawOnOwn(ModelInstanceRenderData part, ModelSlotRenderData data, OwnModel own, float unitsPerMetre) {
        float[] at = own.mount(part.modelInstance.attachmentNameParent, mountAt);
        if (at == null) {
            return;
        }
        unshifted(part.modelInstance);
        GL11.glDepthMask(true);
        part.xfrm.get(partWas);
        System.arraycopy(partWas, 0, partPlace, 0, 16);
        float[] weapon = weaponPlace;
        for (int row = 0; row < 3; row++) {
            partPlace[row * 4 + 3] = weapon[row * 4 + 3]
                    + (weapon[row * 4] * at[0] + weapon[row * 4 + 1] * at[1] + weapon[row * 4 + 2] * at[2]) * unitsPerMetre;
        }
        // The game's shader takes the part's place from its properties, which the game filled from xfrm before the frame was drawn.
        part.xfrm.set(partPlace);
        part.properties.SetMatrix4("transform", part.xfrm);
        try {
            drawPart(part, data);
        } finally {
            part.xfrm.set(partWas);
            part.properties.SetMatrix4("transform", part.xfrm);
        }
    }

    /** The game's model of the scope on what the right hand holds, or null. */
    private static ModelInstanceRenderData scopeOf(ModelSlotRenderData data) {
        for (int part = 0; part < parts(data).size(); part++) {
            ModelInstanceRenderData model = parts(data).get(part);
            if (model.model != null && model.model.isStatic && model.modelInstance.attachmentNameParent != null
                    && Scope.ATTACHMENTS.contains(model.modelInstance.attachmentNameParent) && inHand(model.modelInstance)) {
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
        if (carrying) {
            drawTask(tanHalfFieldOfView, aspect, nearFrom, far);
            return;
        }
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
        boolean outside = ownOutside;
        // Whatever is fixed to it but a scope, a red dot sight, a laser or a light is left out with the game's model of it.
        OwnModel own = held != null && view.viewModel.hasMuzzle ? hands.ownDrawn() : null;
        if (own != null) {
            view.ownGun(own).place(own, held.modelInstance.modelScript == null ? null : held.modelInstance.modelScript.name, heldView, weaponPlace, data.finalScale,
                    muzzleOut(), ownLight, outside, hands.aim(), hands.paused());
        }
        // A scope is drawn by the picture too, after the hands, in place of the game's model of it.
        ModelInstanceRenderData glass = held == null || view.scope.fitted() == null ? null : scopeOf(data);
        if (glass != null) {
            view.scope.place(heldView, glass.xfrm.get(scopePlace), weaponPlace, data.finalScale, ownLight, outside);
        }
        // The weapon first. What the picture draws itself says, beside its colour, which way
        // it faces, and the game's draw of the weapon does not: drawn after the hands, the
        // weapon would be lit where it covers them by the way the hand under it faces.
        for (int part = 0; part < parts(data).size(); part++) {
            ModelInstanceRenderData model = parts(data).get(part);
            // The game's own flash is a model on the muzzle, lit like the weapon it is on.
            // The picture draws the flash itself, as light.
            if (model.model == null || !model.model.isStatic || !inHand(model.modelInstance) || model == glass
                    || model.ignoreLighting && MUZZLE.equals(model.modelInstance.attachmentNameParent)
                    || own != null && model.modelInstance.attachmentNameParent == null) {
                continue;
            }
            // With the mod's own model in hand, of what is fixed to it only a red dot sight, a laser or a light is the game's, drawn where the
            // model has a place for it.
            if (own != null) {
                drawOnOwn(model, data, own, muzzleOut() / own.muzzleFar);
            } else {
                unshifted(model.modelInstance);
                GL11.glDepthMask(true);
                drawPart(model, data);
            }
        }
        if (ownArms) {
            OwnArms.snowed(SnowCarried.on(data.character));
            OwnArms.Worn worn = wornOnArms();
            float[] stains = view.ride.dress.stains;
            OwnArms.draw(projection, armSkins[0], false, armFemale, armSkin, ownLight, outside, worn, stains);
            if (heldBoth) {
                OwnArms.draw(projection, armSkins[1], true, armFemale, armSkin, ownLight, outside, worn, stains);
                OwnArms.carried(projection, armCarries.what, armCarries.at, ownLight, outside);
            }
            EYE.End();
            data.ambientR = red;
            data.ambientG = green;
            data.ambientB = blue;
            return;
        }
        CharacterDraw.release();
        CharacterDraw.begin(data, handViews[0], projection, false, 0f);
        CharacterDraw.stands(ownLight, ownOutside);
        CharacterDraw.snowed(SnowCarried.on(data.character));
        for (int part = 0; part < parts(data).size(); part++) {
            ModelInstanceRenderData model = parts(data).get(part);
            if (model.model == null || !model.model.isStatic) {
                CharacterDraw.piece(model, false, true);
            }
        }
        if (heldBoth) {
            CharacterDraw.begin(data, handViews[1], projection, false, 0f);
            CharacterDraw.stands(ownLight, ownOutside);
            CharacterDraw.snowed(SnowCarried.on(data.character));
            for (int part = 0; part < parts(data).size(); part++) {
                ModelInstanceRenderData model = parts(data).get(part);
                if (model.model == null || !model.model.isStatic) {
                    CharacterDraw.leftHand(model);
                }
            }
        }
        CharacterDraw.release();
        for (int side = 0; side < (heldBoth ? 2 : 1); side++) {
            for (int part = 0; CharacterDraw.OWN_ARMS && arms[side] && part < parts(data).size(); part++) {
                CharacterDraw.sleeve(parts(data).get(part), side == 1, joints[side], bends[side], cuffs[side], wides[side]);
            }
        }
        CharacterDraw.release();
        EYE.End();
        data.ambientR = red;
        data.ambientG = green;
        data.ambientB = blue;
    }

    /** Draws the hands at a timed action, or in a shove or a stamp, and what the game has in them, as drawHeld does the weapon. */
    private void drawTask(float tanHalfFieldOfView, float aspect, float nearFrom, float far) {
        ModelSlotRenderData data = holding;
        ViewCamera.projection(tanHalfFieldOfView, aspect, nearFrom, far, projection);
        float red = data.ambientR, green = data.ambientG, blue = data.ambientB;
        data.ambientR = 1f;
        data.ambientG = 1f;
        data.ambientB = 1f;
        boolean outside = ownOutside;
        for (int part = 0; part < parts(data).size(); part++) {
            ModelInstanceRenderData model = parts(data).get(part);
            if (model.model == null || !model.model.isStatic) {
                continue;
            }
            boolean right = inHand(model.modelInstance);
            if (!right && !inLeftHand(model.modelInstance)) {
                continue;
            }
            // The game's draw takes the matrix pushed when the camera is begun: each hand's is pushed for what that hand holds.
            System.arraycopy(right ? heldView : leftView, 0, combined, 0, 16);
            EYE.Begin();
            unshifted(model.modelInstance);
            GL11.glDepthMask(true);
            drawPart(model, data);
            EYE.End();
        }
        OwnArms.snowed(SnowCarried.on(data.character));
        OwnArms.Worn worn = wornOnArms();
        float[] stains = view.ride.dress.stains;
        OwnArms.draw(projection, armSkins[0], false, armFemale, armSkin, ownLight, outside, worn, stains);
        OwnArms.draw(projection, armSkins[1], true, armFemale, armSkin, ownLight, outside, worn, stains);
        data.ambientR = red;
        data.ambientG = green;
        data.ambientB = blue;
    }

    /** How far the muzzle of what the hand holds is from where the game hangs it, in the model's own measure: of the game's model the view model was placed by. */
    private float muzzleOut() {
        Vector3f muzzle = view.viewModel.muzzleScript.getAttachmentById(MUZZLE).getOffset();
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

    /**
     * And for the shadow map of the torch in the viewer's own hand: everyone and every vehicle
     * but the viewer, who would stand in their own beam, and nobody the torch's picture has no
     * room for. The parameters are castCharacters'.
     */
    void castOthers(float[] lightMatrix, float[] torchView, float tanHalf, float reaches) {
        System.arraycopy(IDENTITY, 0, projection, 0, 16);
        seenFrom = torchView;
        seenWide = tanHalf;
        seenHigh = tanHalf;
        seenWithin = reaches;
        draw(lightMatrix, false, true, null, view.pipeline.focusX, view.pipeline.focusY, SHADOW_REACH);
        seenFrom = null;
        seenWithin = 0f;
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
     * The vehicles nearest the eye drawn into the view last drawn, for the snow on them
     * (SnowShader.ON_VEHICLES), CAR_FLOATS each: the ground under the middle and half the length,
     * the way it faces and half the width, its up and its height, what it carries by FigureSnow's
     * ROOF, BONNET and SCREEN, and how far it is from the eye, squared.
     */
    static final int CAR_FLOATS = 16;
    final float[] cars = new float[SnowShader.MOST_CARS * CAR_FLOATS];
    int carsFound;
    private boolean notingCars;
    /** Where the eye stands that the vehicles noted are drawn for: the nearest are kept. */
    private float carsFromX, carsFromY;
    private final Vector3f carAhead = new Vector3f();

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

    /** The light the vehicle the viewer sits in stands in, the viewer's torch left out (HeldLight). */
    final float[] ownAmbient = new float[3];

    /** How far from where it stands a model or its shadow's caster reaches, in tiles: a vehicle's half length and some over. */
    private static final float MODEL_REACH = 4f;

    /**
     * @param casting whether the models are drawn as the light sees them, into a shadow map
     * @param map     the matrix of a shadow map that looks along the sun's rays, which a model standing outside is left out of, or null
     */
    private int draw(float[] viewMatrix, boolean withViewer, boolean casting, float[] map, float eyeX, float eyeY, float reach) {
        BaseVehicle sat = view.ride.sat;
        int drawn = 0;
        float ambientSum = 0f;
        long began = TALLY ? System.nanoTime() : 0L;
        long now = System.nanoTime();
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
            // Seated, the viewer's model is drawn by the game only for the weapon in its hand.
            if (data.character != null && data.character == viewer && (!withViewer || data.inVehicle)) {
                continue;
            }
            if (only != ALL && (data.character == null) != (only == ONLY_VEHICLES)) {
                continue;
            }
            float toX = data.x - eyeX, toY = data.y - eyeY;
            if (toX * toX + toY * toY > reach * reach || !view.pipeline.drawDistance.draws(data.x, data.y)) {
                continue;
            }
            // Stepped before anything is left out, so a step is not lost while its walker is out of the picture.
            float standsUp = data.character != null && !data.inVehicle ? standsUp(data, now) : 0f;
            if (map != null && outside(map, data)) {
                continue;
            }
            if (glassPlane != null && behindGlass(data, i * 4, standsUp)) {
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
            } else if (!WITHOUT_CULL && (seenFrom != null || map != null)) {
                // The place is where the draw stands the model, and the ball round it is what it cannot reach out of.
                boolean stands = data.character != null && !data.inVehicle;
                float x = stands ? data.x : stood[i * 4], y = stands ? data.y : stood[i * 4 + 1], z = stands ? data.z : stood[i * 4 + 2];
                float up = stands ? standsUp + PERSON_MIDDLE : z * ViewCamera.LEVEL_HEIGHT + this.view.worldScene.terrain.lift(x, y, z);
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
                } else if (shadesNothingSeen(map, x, y, up, round)) {
                    continue;
                }
            }
            if (!WITHOUT_CULL && (kind == BY_EYE || kind == BY_SCOPE) && byRoom != null && data.character != null && !data.inVehicle
                    && (playersByRoom || !(data.character instanceof IsoPlayer))) {
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
                roots[at + 2] = standsUp;
                roots[at + 3] = data.animPlayerAngle;
            }
            if (kind == BY_EYE && !data.inVehicle) {
                // Who rides is not noted: they go as the vehicle goes.
                boolean stands = data.character != null;
                float x = stands ? data.x : stood[i * 4], y = stands ? data.y : stood[i * 4 + 1], z = stands ? data.z : stood[i * 4 + 2];
                movers.note(stands ? data.character : data.object, x, y, stands ? standsUp : z * ViewCamera.LEVEL_HEIGHT + this.view.worldScene.terrain.lift(x, y, z), stands ? data.animPlayerAngle : 0f,
                        toX * toX + toY * toY);
            }
            if (data.character == null) {
                CharacterDraw.release();
                vehicle(data, i * 4, viewMatrix);
            } else if (data.inVehicle) {
                CharacterDraw.release();
                seated(data, i * 4, viewMatrix);
            } else {
                character(data, standsUp, viewMatrix, data.character == viewer && !viewerWhole, casting, false, false,
                        data.character == viewer ? 0f : fromViewer(toX, toY, data.z - viewer.getZ()));
            }
            data.ambientR = red;
            data.ambientG = green;
            data.ambientB = blue;
            drawn++;
        }
        CharacterDraw.release();
        if (!casting && map == null && only != ONLY_VEHICLES) {
            drawPieces(viewMatrix, eyeX, eyeY, reach);
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
     * Whether a model stands behind the plane of the mirror being drawn: a person whose middle
     * is further behind it than BEHIND_GLASS, a vehicle whose middle is behind it at all.
     */
    private boolean behindGlass(ModelSlotRenderData data, int at, float standsUp) {
        boolean stands = data.character != null && !data.inVehicle;
        float x = stands ? data.x : stood[at], y = stands ? data.y : stood[at + 1];
        float up = stands ? standsUp + PERSON_MIDDLE : stood[at + 2] * ViewCamera.LEVEL_HEIGHT;
        float before = glassPlane[0] * x + glassPlane[1] * y + glassPlane[2] * up - glassPlane[3];
        return before < (stands ? -BEHIND_GLASS : 0f);
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
     * The height a person standing in the picture stands at: see KerbSteps. The viewer's own is
     * stepped with the eye, on the logic thread, so the body seen from the eye goes up a kerb
     * with it.
     */
    private float standsUp(ModelSlotRenderData data, long now) {
        Terrain terrain = view.worldScene.terrain;
        return data.character == viewer ? view.kerbSteps.up(terrain, data.character, data.x, data.y, data.z)
                : view.kerbSteps.step(terrain, data.character, data.x, data.y, data.z, now);
    }

    /**
     * @param up      the height the person stands at: see standsUp
     * @param fromEye how far the person stands from the eye, in tiles: see fromViewer
     */
    private void character(ModelSlotRenderData data, float up, float[] viewMatrix, boolean own, boolean casting, boolean held, boolean bothHands, float fromEye) {
        place(data.x, data.y, up, data.animPlayerAngle, CHARACTER_SCALE);
        multiply(viewMatrix, placed, combined);
        EYE.Begin();
        CharacterDraw.begin(data, combined, projection, casting, fromEye);
        // The body seen from the eye is lit as the hands are, which at a doorway may be the next square's light.
        if (own && !casting) {
            CharacterDraw.stands(ownLight, ownOutside);
        } else if (!casting) {
            lightWhere(data);
        }
        if (!casting) {
            CharacterDraw.snowed(SnowCarried.on(data.character));
        }
        for (int part = 0; part < parts(data).size(); part++) {
            ModelInstanceRenderData model = parts(data).get(part);
            if (own && onHead(model.modelInstance)) {
                continue;
            }
            if (held) {
                // The weapon and the arms on it are drawn apart, held up before the eye, and so is what the left hand holds at a timed action.
                if (inHand(model.modelInstance) || carrying && inLeftHand(model.modelInstance)) {
                    continue;
                }
                CharacterDraw.release();
                if (CharacterDraw.piece(model, bothHands, false)) {
                    continue;
                }
            } else if (CharacterDraw.part(model)) {
                if (TALLY) {
                    talliedOurs[kind]++;
                }
                continue;
            }
            if (TALLY) {
                talliedGames[kind]++;
            }
            unshifted(model.modelInstance);
            // Making a finer skin runs the game's combiner, so it is asked for before the
            // game's draw sets any of its state.
            if (data.character instanceof IsoAnimal && CharacterDraw.finelySkinned() && model.modelInstance.tex != null) {
                wornFor = model.modelInstance.tex;
                worn = FineSkins.of(wornFor, fromEye);
            }
            // The game's draw ends by putting its own remembered state back, which has depth
            // writes off. Without them the next part, or the next model, is painted over
            // whatever is nearer.
            GL11.glDepthMask(true);
            try {
                drawPart(model, data);
            } finally {
                wornFor = worn = null;
            }
        }
        if (!own && !casting && !held && data.character instanceof IsoZombie) {
            keepPieces(data, up);
        }
        EYE.End();
    }

    /** The pieces cut off a zombie posed this frame are kept with its bones as drawn. See Pieces. */
    private void keepPieces(ModelSlotRenderData data, float up) {
        for (Pieces.Piece piece : Pieces.of(data.character)) {
            if (piece.state == Pieces.POSED) {
                Pieces.keep(piece, data, parts(data), data.x, data.y, up, data.animPlayerAngle, CHARACTER_SCALE * data.finalScale);
            }
        }
    }

    /** How far from the viewer's eye someone stands, in tiles: along the ground, and a storey's height for each floor between them. */
    private static float fromViewer(float toX, float toY, float levels) {
        float up = levels * ViewCamera.LEVEL_HEIGHT;
        return (float) Math.sqrt(toX * toX + toY * toY + up * up);
    }

    /** Every piece cut off a zombie, where it flies or lies, with what it kept to be drawn by. See Pieces. */
    private void drawPieces(float[] viewMatrix, float eyeX, float eyeY, float reach) {
        List<Pieces.Piece> pieces = Pieces.all();
        for (Pieces.Piece piece : pieces) {
            if (Pieces.toLay(piece)) {
                layPiece(piece);
            }
        }
        for (Pieces.Piece piece : pieces) {
            float toX = piece.x - eyeX, toY = piece.y - eyeY;
            if (toX * toX + toY * toY > reach * reach) {
                continue;
            }
            place(piece.x, piece.y, piece.standsUp, piece.angle, CHARACTER_SCALE);
            multiply(viewMatrix, placed, combined);
            ModelSlotRenderData person = piece.person;
            person.ambientR = 1f;
            person.ambientG = 1f;
            person.ambientB = 1f;
            CharacterDraw.begin(person, combined, projection, false, fromViewer(toX, toY, piece.z - viewer.getZ()));
            IsoGridSquare square = IsoWorld.instance.currentCell.getGridSquare((int) Math.floor(piece.x), (int) Math.floor(piece.y), (int) Math.floor(piece.z));
            if (square != null) {
                lightAt(square);
            }
            for (int part = 0; part < piece.parts.size(); part++) {
                ModelInstanceRenderData model = piece.parts.get(part);
                model.matrixPalette = Pieces.bones(piece, part);
                CharacterDraw.part(model);
            }
        }
        CharacterDraw.release();
    }

    /** The eye and the projection a piece at rest is pictured from, looking straight down on it: see LimbDecals. */
    private final float[] layView = new float[16], layProjection = new float[16], layCombined = new float[16];
    /** How far over the piece the picture's eye is, and how far down it sees, in tiles: a level is 2.4 high. */
    private static final float LAY_ABOVE = 1f, LAY_DEPTH = 2f;

    /**
     * Pictures a piece that has come to rest from straight above and lays the picture on the
     * ground, as the game's own picture of a body is laid: see LimbDecals. Drawn in full white
     * light, as the game draws that picture, and lit where it lies like anything on the ground.
     */
    private void layPiece(Pieces.Piece piece) {
        float[] lies = Pieces.lies(piece);
        float x = lies[0], y = lies[1], half = lies[2];
        // Rows right, up and towards the eye: x to the right, y up the picture against the map's y, which runs south.
        Arrays.fill(layView, 0f);
        layView[0] = 1f;
        layView[5] = -1f;
        layView[10] = 1f;
        layView[12] = -x;
        layView[13] = y;
        layView[14] = -(piece.standsUp + LAY_ABOVE);
        layView[15] = 1f;
        Arrays.fill(layProjection, 0f);
        layProjection[0] = 1f / half;
        layProjection[5] = 1f / half;
        layProjection[10] = -2f / LAY_DEPTH;
        layProjection[14] = -1f;
        layProjection[15] = 1f;
        LimbDecals.lay(x, y, (int) Math.floor(piece.z), half, () -> {
            boolean bare = CharacterDraw.bare, mirrored = CharacterDraw.mirrored;
            CharacterDraw.release();
            CharacterDraw.bare = false;
            CharacterDraw.mirrored = false;
            try {
                place(piece.x, piece.y, piece.standsUp, piece.angle, CHARACTER_SCALE);
                multiply(layView, placed, layCombined);
                CharacterDraw.begin(piece.person, layCombined, layProjection, false, 0f);
                for (int part = 0; part < piece.parts.size(); part++) {
                    ModelInstanceRenderData model = piece.parts.get(part);
                    model.matrixPalette = Pieces.bones(piece, part);
                    boolean unlit = model.ignoreLighting;
                    model.ignoreLighting = true;
                    try {
                        CharacterDraw.part(model);
                    } finally {
                        model.ignoreLighting = unlit;
                    }
                }
            } finally {
                CharacterDraw.release();
                CharacterDraw.bare = bare;
                CharacterDraw.mirrored = mirrored;
            }
        });
        Pieces.laid(piece);
        if (FirstPerson.TRACE) {
            Log.info(String.format(Locale.ROOT, "pieces: part %d laid on the ground at (%.2f, %.2f, %d), %.2f tiles across", piece.part, x, y, (int) Math.floor(piece.z), 2f * half));
        }
    }

    private static boolean onHead(ModelInstance model) {
        for (ModelInstance at = model; at != null; at = at.parent) {
            if ("Bip01_Head".equals(at.parentBoneName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Says to the picture's draw of a person what the game's light is on the square they stand
     * on, read as the world's own squares are, so that a person in a dark room is as dark as
     * the room. Nothing is said of a square the viewer has not seen: the game holds no light for it.
     * The viewer's torch is not in it: its beam lights a person as it lights the wall behind them.
     */
    private final ColorInfo stoodIn = new ColorInfo();

    private void lightWhere(ModelSlotRenderData data) {
        lightAt(data.character.getCurrentSquare());
    }

    private void lightAt(IsoGridSquare square) {
        if (square == null || !square.isSeen(view.index)) {
            return;
        }
        // As the room round them is lit: with the day it lets in.
        ColorInfo light = view.worldScene.daylitNow(square, view.torchLess.light(square, view.index, stoodIn), stoodIn);
        float kept = view.worldScene.daylightKeptNow * (square.isOutside() ? 1f : view.worldScene.roomKeptNow);
        CharacterDraw.stands(Evening.dimmed((light.r + light.g + light.b) / 3f, view.worldScene.openNow, kept), square.isOutside());
    }

    /**
     * A character in a vehicle. The game stands its camera where the vehicle is and then seats
     * the character from there itself, by the seat's place in the vehicle and the way the
     * vehicle is turned and tipped. Here the camera stands the same place as seen from the eye,
     * and the game does its seating on top of that.
     */
    private void seated(ModelSlotRenderData data, int at, float[] viewMatrix) {
        BaseVehicle in = data.character.getVehicle();
        if (in != null) {
            aboard(in, at);
        } else {
            place(stood[at], stood[at + 1], stood[at + 2] * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(stood[at], stood[at + 1], stood[at + 2]) + VEHICLE_RAISE,
                    stood[at + 3], VEHICLE_SCALE);
        }
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
        if (data.object instanceof BaseVehicle standing) {
            aboard(standing, at);
            if (notingCars) {
                noteCar(standing);
            }
        } else {
            place(stood[at], stood[at + 1], stood[at + 2] * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(stood[at], stood[at + 1], stood[at + 2]) + VEHICLE_RAISE,
                    stood[at + 3], VEHICLE_SCALE);
        }
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
        if (lamps != null) {
            GL20.glDrawBuffers(lamps);
        }
        turnGameWinding(true);
        try {
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
        } finally {
            turnGameWinding(false);
        }
        if (lamps != null) {
            GL20.glDrawBuffers(COLOUR_ONLY);
        }
        ModelCamera.instance = gameCamera;
        data.squareDepth = squareDepth;
    }

    /**
     * Notes the vehicle aboard just placed for the snow on it, in place of the furthest noted
     * when there is no room and it is nearer the eye. Its up is where placed takes the model's
     * up, and the way it faces the game's own forward laid on the plane it is tipped onto.
     */
    private void noteCar(BaseVehicle vehicle) {
        float toX = placed[12] - carsFromX, toY = placed[13] - carsFromY;
        float away = toX * toX + toY * toY;
        int slot = carsFound;
        if (slot == SnowShader.MOST_CARS) {
            slot = 0;
            for (int i = 1; i < SnowShader.MOST_CARS; i++) {
                if (cars[i * CAR_FLOATS + 15] > cars[slot * CAR_FLOATS + 15]) {
                    slot = i;
                }
            }
            if (cars[slot * CAR_FLOATS + 15] <= away) {
                return;
            }
        } else {
            carsFound++;
        }
        float upX = placed[4], upY = placed[5], up = placed[6];
        float upLength = (float) Math.sqrt(upX * upX + upY * upY + up * up);
        upX /= upLength;
        upY /= upLength;
        up /= upLength;
        // The game's forward along the map, laid in the plane the vehicle is tipped onto. The model's
        // own axes do not say it: the game's vehicle draw turns the model a quarter round from them.
        vehicle.getForwardVector(carAhead);
        float alongX = carAhead.x, alongY = carAhead.z;
        float lift = alongX * upX + alongY * upY;
        alongX -= upX * lift;
        alongY -= upY * lift;
        float alongUp = -up * lift;
        float alongLength = (float) Math.sqrt(alongX * alongX + alongY * alongY + alongUp * alongUp);
        Vector3f extents = vehicle.getScript().getExtents();
        float[] snow = SnowCarried.on(vehicle);
        int at = slot * CAR_FLOATS;
        cars[at] = placed[12] - upX * VEHICLE_RAISE;
        cars[at + 1] = placed[13] - upY * VEHICLE_RAISE;
        cars[at + 2] = placed[14] - up * VEHICLE_RAISE;
        cars[at + 3] = extents.z / 2f;
        cars[at + 4] = alongX / alongLength;
        cars[at + 5] = alongY / alongLength;
        cars[at + 6] = alongUp / alongLength;
        cars[at + 7] = extents.x / 2f;
        cars[at + 8] = upX;
        cars[at + 9] = upY;
        cars[at + 10] = up;
        cars[at + 11] = extents.y;
        cars[at + 12] = snow[FigureSnow.ROOF];
        cars[at + 13] = snow[FigureSnow.BONNET];
        cars[at + 14] = snow[FigureSnow.SCREEN];
        cars[at + 15] = away;
    }

    /** How each vehicle noted lies on the land this frame, by its slot. */
    private VehicleSlope[] lies = new VehicleSlope[16];
    /** The frame each of lies was worked out for: a vehicle lies one way a frame, in every pass. */
    private int[] liesFor = new int[16];
    private int notedFrames;
    private final Vector3f ahead = new Vector3f();

    /**
     * Places what is noted at slot at, a vehicle or someone in one, on the land as the vehicle
     * lies there: see VehicleSlope. A vehicle is one body. It and all in it are turned together
     * about the ground under its middle, by one slope a frame. The vehicle the viewer sits in
     * is where it was, and lies as it lay, when the eye was seated in it: on its springs.
     */
    private void aboard(BaseVehicle vehicle, int at) {
        int of = slotOf(vehicle);
        // Someone whose vehicle the game did not draw this frame rides on the land under them.
        float middleX = of < 0 ? stood[at] : stood[of * 4], middleY = of < 0 ? stood[at + 1] : stood[of * 4 + 1];
        float dx = stood[at] - middleX, dy = stood[at + 1] - middleY;
        float[] turn;
        float groundUp;
        if (view.pipeline.seated && vehicle == view.ride.sat) {
            turn = view.pipeline.seatedTurn;
            groundUp = view.pipeline.seatedGroundUp;
            if (of >= 0) {
                // The game's draw has the vehicle where its physics had it at another moment
                // than the one the eye was seated at: up to 2 cm off at speed.
                middleX = view.pipeline.seatedX;
                middleY = view.pipeline.seatedY;
            }
        } else {
            VehicleSlope lies = lying(vehicle, of < 0 ? at / 4 : of);
            turn = lies.turn;
            groundUp = lies.groundUp;
        }
        place(0f, 0f, 0f, stood[at + 3], VEHICLE_SCALE);
        tip(turn);
        placed[12] = middleX + turn[0] * dx + turn[3] * dy + turn[6] * VEHICLE_RAISE;
        placed[13] = middleY + turn[1] * dx + turn[4] * dy + turn[7] * VEHICLE_RAISE;
        placed[14] = groundUp + turn[2] * dx + turn[5] * dy + turn[8] * VEHICLE_RAISE;
    }

    /** The slot the vehicle was noted in this frame, or -1. */
    private int slotOf(BaseVehicle vehicle) {
        for (int i = 0; i < noted.size(); i++) {
            if (noted.get(i).object == vehicle && noted.get(i).character == null) {
                return i;
            }
        }
        return -1;
    }

    /** How the vehicle noted at slot lies on the land under its wheels this frame. */
    private VehicleSlope lying(BaseVehicle vehicle, int slot) {
        if (slot >= lies.length) {
            lies = Arrays.copyOf(lies, Math.max(slot + 1, lies.length * 2));
            liesFor = Arrays.copyOf(liesFor, lies.length);
        }
        VehicleSlope slope = lies[slot];
        if (slope == null) {
            slope = lies[slot] = new VehicleSlope();
        } else if (liesFor[slot] == notedFrames) {
            return slope;
        }
        liesFor[slot] = notedFrames;
        int at = slot * 4;
        vehicle.getForwardVector(ahead);
        Vector3f extents = vehicle.getScript().getExtents();
        slope.lie(view.worldScene.terrain, stood[at], stood[at + 1], stood[at + 2], ahead.x, ahead.z, extents.z, extents.x);
        return slope;
    }

    /** Tips what placed stands up by the turn of a VehicleSlope, about where it stands. */
    private void tip(float[] turn) {
        for (int column = 0; column < 12; column += 4) {
            float x = placed[column], y = placed[column + 1], up = placed[column + 2];
            placed[column] = turn[0] * x + turn[3] * y + turn[6] * up;
            placed[column + 1] = turn[1] * x + turn[4] * y + turn[7] * up;
            placed[column + 2] = turn[2] * x + turn[5] * y + turn[8] * up;
        }
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
