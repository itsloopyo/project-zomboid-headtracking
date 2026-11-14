package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Locale;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import zombie.core.skinnedmodel.model.ModelInstanceRenderData;
import zombie.core.skinnedmodel.model.ModelInstanceRenderDataList;
import zombie.core.skinnedmodel.model.ModelMesh;
import zombie.core.skinnedmodel.model.VehicleModelInstance;
import zombie.core.skinnedmodel.model.VehicleSubModelInstance;
import zombie.core.skinnedmodel.model.VertexBufferObject;
import zombie.core.textures.Texture;
import zombie.vehicles.VehiclePart;

/**
 * Where the steering wheel is in each vehicle, read from the vehicle's own model. Every body
 * has its wheel as a loose piece of 36 triangles, a six-sided ring, and its middle is found
 * once per model on the render thread. The driver's eye and the driver's place the picture
 * builds are both put from it, so a pickup's driver sits as high as a pickup's wheel is and a
 * van's as far forward.
 */
final class VehicleMeshes {
    private VehicleMeshes() {}

    private static final int WHEEL_TRIANGLES = 36;
    private static final int GL_ARRAY_BUFFER = 0x8892, GL_ARRAY_BUFFER_BINDING = 0x8894, GL_BUFFER_SIZE = 0x8764;
    private static final float[] NONE = new float[0];

    /**
     * What is read of a body is read from three things: its mesh, the mask that says which of
     * its triangles are glass, and the script whose seats tell its steering wheel and its seats.
     * Two vehicles can share a mesh and differ in either.
     */
    private record Body(ModelMesh mesh, Texture mask, String script) {}

    /** The wheel's middle in each body's own space, or NONE for a body with no such piece. */
    private static final HashMap<Body, float[]> middles = new HashMap<>();
    /** Which of each body's model axes is its up and which its length: see Cabins.upright. */
    private static final HashMap<Body, int[]> uprights = new HashMap<>();
    private static final String[] AXES = {"x", "y", "z"};
    /**
     * The panes of each body, as loose triangles in the model's own space: the vehicle's mask
     * texture says which of its triangles are glass. Empty for a body with none found.
     */
    private static final HashMap<Body, FloatBuffer> panes = new HashMap<>();
    private static final HashMap<Body, Integer> rearPanes = new HashMap<>();
    private static final float[] shifted = new float[16], stands = new float[16], fromEye = new float[16];

    /** The triangles of a body that are not glass, sorted by what part of the cabin each is, in the model's own space. */
    static final class Parts {
        final FloatBuffer triangles;
        /** For each kind CabinParts tells, where its vertices begin and how many they are. */
        final int[] first = new int[CabinParts.KINDS], count = new int[CabinParts.KINDS];
        /** Whether the seats were found as pieces of their own. Where they were not, they are not told from the shell. */
        final boolean seats;
        /**
         * From the wheel's middle, in tiles: the foot of the windscreen, along and up; the
         * sill of the driver's window, up; how far out from the wheel that window is; how far
         * across the vehicle's middle line is, by sign; the top of the dashboard, up; and
         * where the shelf of the dashboard begins, along.
         */
        final float[] measures;
        /** For each triangle of the body, whether it is a pane of the front of the glasshouse. */
        final boolean[] front;
        /** The texture place of the middle of each triangle of the bonnet, u then v, each brought into 0..1. */
        final float[] bonnetUv;

        Parts(FloatBuffer triangles, boolean seats, float[] measures, boolean[] front, float[] bonnetUv) {
            this.triangles = triangles;
            this.seats = seats;
            this.measures = measures;
            this.front = front;
            this.bonnetUv = bonnetUv;
        }
    }

    private static final HashMap<Body, Parts> parts = new HashMap<>();

    /**
     * The vehicle one viewer sits in, as its body was drawn for that viewer's picture this
     * frame: two viewers may sit in two vehicles, or in two seats of one. Render thread.
     */
    static final class Seated {
        /**
         * The panes of the vehicle, and the matrix that puts them on the screen. Null with
         * none. The first rearPanes vertices are the panes behind the front of the glasshouse;
         * the rest are the screen and the front windows, where the picture builds its own.
         */
        FloatBuffer panes;
        int rearPanes;
        final float[] paneMatrix = new float[16];
        /** The parts of the vehicle, drawn with paneMatrix as the panes are. Null with none. */
        Parts parts;
        /** Where the wheel of the vehicle is in the world this frame, or null: x, y and up. */
        float[] wheel;
        /**
         * The way the vehicle faces, the side to the left of that and its own up, in the
         * world, as its body is drawn this frame. On its springs a vehicle tips and rolls, a
         * crash throws it on its side or its roof, and what the picture builds in its cabin is
         * laid out along these.
         */
        final float[] axes = new float[9];
        /** The vehicle's paint. */
        final float[] paint = new float[3];
        private final float[] wheelNow = new float[3];
        /**
         * Whether the vehicle's inside is models of its own, a vehicle mod's dashboard and seats
         * hung on its parts, and whether its glass is parts of its own: with ModdedVehicleInteriors
         * on, for a body with no wheel of the game's kind. The picture's treatment of the game's
         * painted cabins is then left off, and so is telling its glass by colour.
         */
        boolean ownInterior, glassParted;
        /**
         * The steering wheel a vehicle mod models, with ModdedVehicleInteriors on, or null: where
         * the middle of its rim is in the world this frame, x, y and up; the way its column runs
         * away from the driver, along the vehicle, to its left and up; and how far out from its
         * middle a hand holds its rim.
         */
        float[] moddedWheel;
        private final float[] moddedWheelNow = new float[7];
        /** The windows a vehicle mod made parts of their own, as drawn this frame: the first paneCount of each. */
        int paneCount;
        PaneMesh[] paneMeshes = new PaneMesh[8];
        float[][] paneStands = new float[8][16];

        void clear() {
            panes = null;
            parts = null;
            wheel = null;
            ownInterior = false;
            glassParted = false;
            moddedWheel = null;
            for (int i = 0; i < paneCount; i++) {
                paneMeshes[i] = null;
            }
            paneCount = 0;
        }

        private void addPane(PaneMesh mesh, float[] stands) {
            if (paneCount == paneMeshes.length) {
                paneMeshes = java.util.Arrays.copyOf(paneMeshes, paneCount * 2);
                float[][] more = java.util.Arrays.copyOf(paneStands, paneCount * 2);
                for (int i = paneCount; i < more.length; i++) {
                    more[i] = new float[16];
                }
                paneStands = more;
            }
            paneMeshes[paneCount] = mesh;
            System.arraycopy(stands, 0, paneStands[paneCount], 0, 16);
            paneCount++;
        }
    }

    /** Saves left on the logic thread, for what the render thread keeps of each vehicle mod's script to be let go. */
    private static final LeftSaves leftSaves = new LeftSaves();

    /**
     * Logic thread, as a save is left: what was read of each script's part models is let go at
     * the next vehicle noted. Another save may have another vehicle mod give a script of the
     * same name other models.
     */
    static void forget() {
        leftSaves.left();
    }

    /**
     * Whether a model of a vehicle is one of its road wheels, or hangs on one as a hubcap or a
     * brake drum does, by the vehicle's own list of them.
     */
    static boolean roadWheel(ModelInstanceRenderData model) {
        if (!(model.modelInstance instanceof VehicleSubModelInstance sub) || sub.modelInfo == null) {
            return false;
        }
        for (VehiclePart part = sub.modelInfo.part; part != null; part = part.getParent()) {
            if (part.getWheelIndex() >= 0) {
                return true;
            }
        }
        return false;
    }

    /** Whether a model of a vehicle is a window a vehicle mod made a part of its own. */
    static boolean pane(ModelInstanceRenderData model) {
        return model.modelInstance instanceof VehicleSubModelInstance sub && sub.modelInfo != null && sub.modelInfo.part.getWindow() != null;
    }

    private static final HashSet<String> turnedSaid = new HashSet<>();
    private static final HashSet<String> skinlessSaid = new HashSet<>();
    private static int tipSaid;
    /** What turns a vertex of the body into tiles along the vehicle, across it and up from the ground: three rows of four. */
    private static final float[] frame = new float[12];
    private static final int GL_TEXTURE_2D = 0x0DE1, GL_TEXTURE_BINDING_2D = 0x8069, GL_TEXTURE_WIDTH = 0x1000, GL_TEXTURE_HEIGHT = 0x1001;
    private static final int GL_RGBA = 0x1908, GL_UNSIGNED_BYTE = 0x1401;

    private static final int PLACE_NORMAL_AND_TEXTURE_PLACE = 32;
    private static Field handleField, elementsField, bufferField, strideField;
    private static final float[] xfrm = new float[16];

    /**
     * Called on the render thread for the vehicle a viewer sits in, with the matrix that
     * stands it in the world, how far the steering wheel read from its model sits above the
     * one the game draws, along the body's up, and where the vehicle stands.
     *
     * @param seated what that viewer's picture is told of the vehicle
     * @param ride   that viewer's vehicle as the logic thread has it
     * @param models the vehicle's models as the game draws them, its body first
     * @param faces  the way the vehicle faces, its left and its up, as the logic thread had them when it seated the eye: see Ride.stands
     * @param eye    the projection times the turn of the view from the eye (ViewCamera.turned), which takes places counted from camera's eyeX
     */
    static void note(Seated seated, Ride ride, String script, ModelInstanceRenderDataList models, float[] placed, float[] faces, float settles, float standsX, float standsY,
            float groundUp, float centreOfMass, float[] eye, ViewCamera.Pose camera) {
        seated.clear();
        if (leftSaves.sinceAsked()) {
            int vehicles = interiorDone.size(), windows = paneMeshes.size();
            partMiddles.clear();
            interiorDone.clear();
            moddedWheels.clear();
            paneMeshes.clear();
            if (vehicles + windows > 0) {
                Log.info("a save was left: what was read of the part models of " + vehicles + " vehicles, and of " + windows + " windows, is let go");
            }
        }
        ModelInstanceRenderData body = models.get(0);
        float[] seatedAxes = seated.axes, paneMatrix = seated.paneMatrix;
        float aheadX = ride.aheadX, aheadY = ride.aheadY;
        Cabins.level(aheadX, aheadY, seatedAxes);
        if (!(body.modelInstance instanceof VehicleModelInstance instance) || body.modelInstance.model == null) {
            return;
        }
        ModelMesh mesh = body.modelInstance.model.mesh;
        if (mesh == null) {
            return;
        }
        body.xfrm.get(xfrm);
        // The game hangs the body from the vehicle's centre of mass, along the model's own up.
        System.arraycopy(placed, 0, shifted, 0, 16);
        for (int k = 0; k < 3; k++) {
            shifted[12 + k] += placed[4 + k] * centreOfMass;
        }
        ViewCamera.multiply(shifted, xfrm, stands);
        // The panes are half a pace from the eye: stood from the map, the eye's place taken off by the matrix, they were a millimetre off.
        System.arraycopy(stands, 0, fromEye, 0, 16);
        fromEye[12] -= camera.eyeX;
        fromEye[13] -= camera.eyeY;
        fromEye[14] -= camera.eyeUp;
        ViewCamera.multiply(eye, fromEye, paneMatrix);
        Body read = new Body(mesh, instance.textureMask, script);
        int[] upright = uprights.get(read);
        if (upright == null) {
            upright = Cabins.upright(stands, faces);
            if (upright != null) {
                uprights.put(read, upright);
                if (DevBuild.ON) {
                    Log.info(String.format(Locale.ROOT, "the body of %s stands with its model's %s axis up and its %s axis along it", script, AXES[upright[0] / 4], AXES[upright[2] / 4]));
                }
            }
        }
        if (upright == null || !Cabins.axes(stands, upright, faces, seatedAxes)) {
            System.arraycopy(faces, 0, seatedAxes, 0, 9);
            if (turnedSaid.add(script)) {
                Log.info("the body of " + script + " does not lie along the way its vehicle faces: its cabin is laid out by the vehicle as the physics has it, not by the body as it is drawn");
            }
        }
        float[] axes = seatedAxes;
        if (FirstPerson.TRACE && (tipSaid++ & 7) == 0) {
            Log.info(String.format(Locale.ROOT, "body of %s: nose up %.2f, left side up %.2f, its up %.2f off straight up; off the vehicle's own as the physics has it, the nose %.2f and the up %.2f, in degrees",
                    script, Math.toDegrees(Math.asin(axes[2])), Math.toDegrees(Math.asin(axes[5])), Math.toDegrees(Math.acos(Math.clamp(axes[8], -1f, 1f))),
                    Math.toDegrees(Math.acos(Math.clamp(axes[0] * faces[0] + axes[1] * faces[1] + axes[2] * faces[2], -1f, 1f))),
                    Math.toDegrees(Math.acos(Math.clamp(axes[6] * faces[6] + axes[7] * faces[7] + axes[8] * faces[8], -1f, 1f)))));
        }
        float[] middle = middles.get(read);
        if (middle == null) {
            // A mask still on its way from the disc reads as empty, and no glass would be the
            // answer kept for the body from then on.
            if (!mesh.isReady() || mesh.vb == null || instance.textureMask != null && !instance.textureMask.isReady()) {
                return;
            }
            // So does a body the game has not placed yet, whose matrix is all noughts.
            float turns = 0f;
            for (int column = 0; column < 12; column += 4) {
                turns += stands[column] * stands[column] + stands[column + 1] * stands[column + 1] + stands[column + 2] * stands[column + 2];
            }
            if (!(turns > 0f)) {
                return;
            }
            for (int column = 0; column < 4; column++) {
                float shift = column == 3 ? 1f : 0f;
                float x = stands[column * 4] - standsX * shift, y = stands[column * 4 + 1] - standsY * shift, up = stands[column * 4 + 2] - groundUp * shift;
                for (int axis = 0; axis < 3; axis++) {
                    frame[axis * 4 + column] = x * axes[axis * 3] + y * axes[axis * 3 + 1] + up * axes[axis * 3 + 2];
                }
            }
            middle = find(read, instance, body.modelInstance.model.name, ride.seats);
            middles.put(read, middle);
        }
        FloatBuffer glass = panes.get(read);
        if (glass != null && glass.capacity() > 0) {
            seated.panes = glass;
            seated.rearPanes = rearPanes.get(read);
        }
        seated.parts = parts.get(read);
        // A body drawn with the game's norandom shader is the colours of its skin, whatever paint the vehicle was given:
        // a police car's livery, not the hue the game rolled for it.
        // Either may be null, as the characters' draw takes them: a modded vehicle whose script's skin did not resolve has no texture of its own.
        Texture skinned = instance.tex != null ? instance.tex : body.modelInstance.model.tex;
        boolean skinShader = body.modelInstance.model.effect != null && body.modelInstance.model.effect.getName().startsWith(SKIN_COLOURED);
        if (skinShader && skinned == null && skinlessSaid.add(script)) {
            Log.error("the body of " + script + " is drawn with the game's " + SKIN_COLOURED + " shader and has no skin texture: its cabin is the vehicle's paint, not its livery");
        }
        boolean skinColoured = skinShader && skinned != null;
        float[] skin = skinColoured ? skinColour(read, skinned, seated.parts, script) : NONE;
        // A skin still loading is painted for now: the wheel is measured all the same, or the eye is not placed by it.
        if (skin == null || skin == NONE) {
            paint(instance.painColor.x, Math.clamp(instance.painColor.y, 0f, 1f), Math.clamp(instance.painColor.z, 0f, 1f), seated.paint);
        } else {
            System.arraycopy(skin, 0, seated.paint, 0, 3);
        }
        if (middle == NONE) {
            if (Part.MODDED_VEHICLE_INTERIORS.on()) {
                ownInterior(seated, ride, script, models, axes, settles, placed, standsX, standsY, groundUp);
            }
            return;
        }
        // On the body as it is drawn, tipped and rolled with it.
        float bodyX = stands[0] * middle[0] + stands[4] * middle[1] + stands[8] * middle[2] + stands[12];
        float bodyY = stands[1] * middle[0] + stands[5] * middle[1] + stands[9] * middle[2] + stands[13];
        float bodyUp = stands[2] * middle[0] + stands[6] * middle[1] + stands[10] * middle[2] + stands[14];
        seated.wheelNow[0] = bodyX - axes[6] * settles;
        seated.wheelNow[1] = bodyY - axes[7] * settles;
        seated.wheelNow[2] = bodyUp - axes[8] * settles;
        seated.wheel = seated.wheelNow;
        if (!ride.wheels.containsKey(script)) {
            float[] measured = measured(bodyX, bodyY, bodyUp, axes, settles, placed, standsX, standsY, groundUp);
            if (DevBuild.ON) {
                Log.info(String.format(Locale.ROOT, "wheel of %s (%s): %.3f along, %.3f across, %.3f up from the centre of mass %.3f above the ground, %.3f from the body's middle",
                        script, body.modelInstance.model.name, measured[0], measured[1], measured[2], measured[4], measured[3]));
            }
            // Repeated world-to-local measurements feed motion and rounding into the next frame's seat position.
            ride.wheels.put(script, measured);
        }
    }

    /**
     * A wheel's middle on the body as it is drawn, as Ride seats the eye from it: along, across
     * and up from the centre of mass, how far across from the body's middle, and how high the
     * centre of mass is above the ground. The game's physics tips and rolls the body about its
     * centre of mass, so the wheel is measured from there, and the eye is turned about the same
     * point. Read with stands and shifted as note left them for the body.
     */
    private static float[] measured(float bodyX, float bodyY, float bodyUp, float[] axes, float settles, float[] placed, float standsX, float standsY, float groundUp) {
        float x = bodyX - axes[6] * settles, y = bodyY - axes[7] * settles, up = bodyUp - axes[8] * settles;
        float toX = x - shifted[12], toY = y - shifted[13], toUp = up - shifted[14];
        float along = toX * axes[0] + toY * axes[1] + toUp * axes[2];
        float across = toX * axes[3] + toY * axes[4] + toUp * axes[5];
        float above = toX * axes[6] + toY * axes[7] + toUp * axes[8];
        float side = (bodyX - stands[12]) * axes[3] + (bodyY - stands[13]) * axes[4] + (bodyUp - stands[14]) * axes[5];
        float tall = (float) Math.sqrt(placed[4] * placed[4] + placed[5] * placed[5] + placed[6] * placed[6]);
        float centre = ((shifted[12] - standsX) * placed[4] + (shifted[13] - standsY) * placed[5] + (shifted[14] - groundUp) * placed[6]) / tall;
        return new float[] {along, across, above, side, centre};
    }

    private record PartMesh(ModelMesh mesh, String script, String part) {}

    /**
     * The steering wheel in each model a vehicle hangs on its parts, as roundIn reads it, or NONE
     * for a model with none. Keyed by the part as well, as one mesh can hang on several parts in
     * different places.
     */
    private static final HashMap<PartMesh, float[]> partMiddles = new HashMap<>();
    private static final float[] partXfrm = new float[16], partStands = new float[16], partFrame = new float[12];
    /** Vehicles whose part models have all been looked through for a steering wheel. */
    private static final HashSet<String> interiorDone = new HashSet<>();

    /** The steering wheel chosen among a vehicle's part models: the part it is on, what roundIn read of it, and where it puts the driver's eye (Ride.eyeWheels). */
    private record ModdedWheel(PartMesh on, float[] read, float[] eye) {}

    private static final HashMap<String, ModdedWheel> moddedWheels = new HashMap<>();

    /**
     * A body with no wheel of the game's kind, whose vehicle may bring its inside as models of
     * its own hung on its parts, as KI5's do: the dashboard on the glove box, a seat on each
     * seat, the glass on each window. Notes which it does. Once every part model has been read,
     * the widest steering wheel among them is noted for the driver's eye (Ride.eyeWheels), and
     * from then on is placed each frame for the driver's hands (Seated.moddedWheel).
     */
    private static void ownInterior(Seated seated, Ride ride, String script, ModelInstanceRenderDataList models, float[] axes, float settles, float[] placed,
            float standsX, float standsY, float groundUp) {
        float[] seats = ride.seats;
        boolean done = interiorDone.contains(script) || seats == null || seats.length < 3;
        ModdedWheel known = done ? moddedWheels.get(script) : null;
        // Each view seats its own eye: another view may have been the one to read the wheel.
        if (known != null && !ride.eyeWheels.containsKey(script)) {
            ride.eyeWheels.put(script, known.eye);
        }
        boolean all = true;
        float widest = 0f;
        float[] chosen = null;
        PartMesh chosenOn = null;
        String chosenName = null;
        for (int i = 1; i < models.size(); i++) {
            ModelInstanceRenderData model = models.get(i);
            if (roadWheel(model)) {
                continue;
            }
            if (pane(model)) {
                seated.glassParted = true;
                notePane(seated, model, script, seats, axes, standsX, standsY, groundUp);
                continue;
            }
            if (!(model.modelInstance instanceof VehicleSubModelInstance sub) || sub.modelInfo == null || model.modelInstance.model == null) {
                continue;
            }
            String partId = sub.modelInfo.part.getId();
            if (partId.startsWith("Seat")) {
                seated.ownInterior = true;
            }
            ModelMesh mesh = model.modelInstance.model.mesh;
            if (done) {
                if (known != null && known.on.mesh == mesh && known.on.part.equals(partId) && placedPart(model)) {
                    placeWheel(seated, known.read, axes, settles);
                }
                continue;
            }
            if (mesh == null || !mesh.isReady() || mesh.vb == null) {
                all = false;
                continue;
            }
            PartMesh key = new PartMesh(mesh, script, partId);
            float[] middle = partMiddles.get(key);
            if (middle == NONE) {
                continue;
            }
            if (!placedPart(model)) {
                all = false;
                continue;
            }
            if (middle == null) {
                framePart(axes, standsX, standsY, groundUp);
                // The seats are placed from the body's own middle, as the body's wheel is looked for.
                // The script's x runs the other way from across here: every KI5 car seated at a
                // positive x has its wheel at a negative across.
                float bodyAlong = (stands[12] - standsX) * axes[0] + (stands[13] - standsY) * axes[1] + (stands[14] - groundUp) * axes[2];
                float bodyAcross = (stands[12] - standsX) * axes[3] + (stands[13] - standsY) * axes[4] + (stands[14] - groundUp) * axes[5];
                float[] seat = {bodyAlong + seats[2], -seats[0]};
                middle = withVertices(mesh.vb, model.modelInstance.model.name, "no steering wheel is looked for in it", NONE,
                        (raw, stride, vertices, elements) -> roundIn(raw, stride, vertices, elements, seat, bodyAcross));
                partMiddles.put(key, middle);
                if (middle == NONE) {
                    continue;
                }
            }
            if (middle[3] > widest) {
                widest = middle[3];
                // The eye is put behind the middle of the rim, where the hands are put on it (placeWheel).
                float x = partStands[0] * middle[7] + partStands[4] * middle[8] + partStands[8] * middle[9] + partStands[12];
                float y = partStands[1] * middle[7] + partStands[5] * middle[8] + partStands[9] * middle[9] + partStands[13];
                float up = partStands[2] * middle[7] + partStands[6] * middle[8] + partStands[10] * middle[9] + partStands[14];
                chosen = measured(x, y, up, axes, settles, placed, standsX, standsY, groundUp);
                chosenOn = key;
                chosenName = model.modelInstance.model.name;
            }
        }
        if (ride.eyeWheels.containsKey(script)) {
            seated.ownInterior = true;
        }
        if (seated.ownInterior && !ride.ownInteriors.contains(script)) {
            ride.ownInteriors.add(script);
        }
        if (done || !all) {
            return;
        }
        interiorDone.add(script);
        if (chosen != null) {
            seated.ownInterior = true;
            float[] read = partMiddles.get(chosenOn);
            moddedWheels.put(script, new ModdedWheel(chosenOn, read, chosen));
            if (DevBuild.ON) {
                Log.info(String.format(Locale.ROOT, "wheel of %s for the eye: %.3f along, %.3f across, %.3f up from the centre of mass %.3f above the ground, %.3f from the body's middle",
                        script, chosen[0], chosen[1], chosen[2], chosen[4], chosen[3]));
            }
            ride.eyeWheels.put(script, chosen);
            Log.info(String.format(Locale.ROOT, "%s brings its own inside as models on its parts%s; the driver's eye is put behind its steering wheel, a round piece %.2f tiles across in %s,"
                    + " whose column leans %.0f degrees from level and whose middle is dished %.3f tiles from its rim: the driver's hands hold it %.3f tiles out from its middle",
                    script, seated.glassParted ? ", and its glass as parts of their own, left out from the seat" : "", widest, chosenName,
                    Math.toDegrees(read[4]), read[5], read[6]));
        } else if (seated.ownInterior || seated.glassParted) {
            Log.info(script + " brings its own inside as models on its parts" + (seated.glassParted ? ", and its glass as parts of their own, left out from the seat" : "")
                    + "; none of them holds a steering wheel, so the driver's eye is put from the game's seat");
        }
    }

    /**
     * A window a vehicle mod made a part of its own, as the cabin's glass is drawn on it
     * (CabinDraw.drawLooseGlass): its triangles one by one, in the model's own space and laid
     * out as CabinModel's vertices, each with its place on the pane in tiles; for the
     * windscreen, which the wipers sweep, half its width and how far the blades reach up it; and
     * whether it is a slab modelled with both its faces, of which only the one turned to the eye
     * is drawn, or the glass would be laid on twice.
     */
    record PaneMesh(ByteBuffer vertices, int count, boolean wiped, float screenHalf, float wiperReach, boolean slab) {}

    /** What each window part a vehicle mod models is drawn with, or UNREAD for one whose model could not be read. */
    private static final HashMap<PartMesh, PaneMesh> paneMeshes = new HashMap<>();
    private static final PaneMesh UNREAD = new PaneMesh(null, 0, false, 0f, 0f, false);

    /**
     * A window part's model, with partStands as placedPart left them for it, kept for the frame
     * for the glass to be drawn on it: read once, with the vehicle laid out by axes.
     */
    private static void notePane(Seated seated, ModelInstanceRenderData model, String script, float[] seats, float[] axes, float standsX, float standsY, float groundUp) {
        VehicleSubModelInstance sub = (VehicleSubModelInstance) model.modelInstance;
        ModelMesh mesh = model.modelInstance.model == null ? null : model.modelInstance.model.mesh;
        if (mesh == null || !mesh.isReady() || mesh.vb == null || !placedPart(model)) {
            return;
        }
        String partId = sub.modelInfo.part.getId();
        PartMesh key = new PartMesh(mesh, script, partId);
        PaneMesh glass = paneMeshes.get(key);
        if (glass == null) {
            framePart(axes, standsX, standsY, groundUp);
            // The game's own id for the windscreen, the pane its wipers sweep.
            boolean screen = "Windshield".equals(partId);
            float bodyAcross = (stands[12] - standsX) * axes[3] + (stands[13] - standsY) * axes[4] + (stands[14] - groundUp) * axes[5];
            float driverAcross = seats != null && seats.length >= 3 ? bodyAcross - seats[0] : bodyAcross;
            glass = withVertices(mesh.vb, model.modelInstance.model.name, "no glass is drawn on it", UNREAD,
                    (raw, stride, vertices, elements) -> paneIn(raw, stride, vertices, elements, screen, driverAcross));
            paneMeshes.put(key, glass);
            if (DevBuild.ON && glass != UNREAD) {
                Log.info(String.format(Locale.ROOT, "glass of %s on its part %s (%s): %d corners, %.2f tiles either way of its middle and %.2f up it%s%s",
                        script, partId, model.modelInstance.model.name, glass.count(), glass.screenHalf(), glass.wiperReach() / CabinModel.WIPER_SHARE,
                        screen ? ", swept by the wipers" : "", glass.slab() ? ", a slab drawn on the face turned to the eye" : ""));
            }
        }
        if (glass != UNREAD) {
            seated.addPane(glass, partStands);
        }
    }

    /** Sets partFrame, from the model's own space to along, across and up from the ground under the vehicle, from partStands. */
    private static void framePart(float[] axes, float standsX, float standsY, float groundUp) {
        for (int column = 0; column < 4; column++) {
            float shift = column == 3 ? 1f : 0f;
            float x = partStands[column * 4] - standsX * shift, y = partStands[column * 4 + 1] - standsY * shift, up = partStands[column * 4 + 2] - groundUp * shift;
            for (int axis = 0; axis < 3; axis++) {
                partFrame[axis * 4 + column] = x * axes[axis * 3] + y * axes[axis * 3 + 1] + up * axes[axis * 3 + 2];
            }
        }
    }

    /**
     * A window's model laid out for the cabin's glass, read with partFrame set for it. A place on
     * the pane is counted in tiles along the pane's own level and up its slant; on the
     * windscreen across from its middle, the driver's side below nought, and up from
     * CabinModel.WIPERS_BELOW under its foot, as the mod's own interiors have it.
     *
     * @param driverAcross how far across the vehicle the driver sits, as partFrame counts it
     */
    private static PaneMesh paneIn(ByteBuffer raw, int stride, int vertices, int[] elements, boolean screen, float driverAcross) {
        float[] at = new float[vertices * 3];
        for (int i = 0; i < vertices; i++) {
            for (int row = 0; row < 3; row++) {
                float place = partFrame[row * 4 + 3];
                for (int axis = 0; axis < 3; axis++) {
                    place += partFrame[row * 4 + axis] * raw.getFloat(i * stride + axis * 4);
                }
                at[i * 3 + row] = place;
            }
        }
        float[] on = CabinParts.paneFace(at, elements, screen, driverAcross);
        int corners = elements.length;
        ByteBuffer out = ByteBuffer.allocateDirect(corners * CabinModel.VERTEX_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int t = 0; t < corners; t += 3) {
            int a = elements[t], b = elements[t + 1], c = elements[t + 2];
            // The face's way in the model's own space, which the drawing turns with the part.
            float ux = raw.getFloat(b * stride) - raw.getFloat(a * stride), uy = raw.getFloat(b * stride + 4) - raw.getFloat(a * stride + 4), uz = raw.getFloat(b * stride + 8) - raw.getFloat(a * stride + 8);
            float vx = raw.getFloat(c * stride) - raw.getFloat(a * stride), vy = raw.getFloat(c * stride + 4) - raw.getFloat(a * stride + 4), vz = raw.getFloat(c * stride + 8) - raw.getFloat(a * stride + 8);
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (!(length > 0f)) {
                length = 1f;
            }
            for (int k = 0; k < 3; k++) {
                int i = elements[t + k], base = (t + k) * CabinModel.VERTEX_BYTES;
                out.putFloat(base, raw.getFloat(i * stride));
                out.putFloat(base + 4, raw.getFloat(i * stride + 4));
                out.putFloat(base + 8, raw.getFloat(i * stride + 8));
                out.putShort(base + CabinModel.NORMAL_AT, (short) Math.round(nx / length * Short.MAX_VALUE));
                out.putShort(base + CabinModel.NORMAL_AT + 2, (short) Math.round(ny / length * Short.MAX_VALUE));
                out.putShort(base + CabinModel.NORMAL_AT + 4, (short) Math.round(nz / length * Short.MAX_VALUE));
                out.putShort(base + CabinModel.WAY_AT, (short) 1);
                out.putFloat(base + CabinModel.UV_AT, on[4 + i * 2]);
                out.putFloat(base + CabinModel.UV_AT + 4, on[4 + i * 2 + 1]);
                out.put(base + CabinModel.SKY_AT, screen ? (byte) 255 : 0);
                out.put(base + CabinModel.NEAR_AT, (byte) 255);
            }
        }
        return new PaneMesh(out, corners, screen, on[0], CabinModel.WIPER_SHARE * on[1], on[2] > 0.5f);
    }

    /** Sets partStands for a part model as it is drawn this frame. False for one the game has not placed yet, whose matrix is all noughts. */
    private static boolean placedPart(ModelInstanceRenderData model) {
        model.xfrm.get(partXfrm);
        ViewCamera.multiply(shifted, partXfrm, partStands);
        float turns = 0f;
        for (int column = 0; column < 12; column += 4) {
            turns += partStands[column] * partStands[column] + partStands[column + 1] * partStands[column + 1] + partStands[column + 2] * partStands[column + 2];
        }
        return turns > 0f;
    }

    /**
     * The driver's hands' wheel for the frame, from what roundIn read of it and partStands as
     * placedPart left them for its part: the middle of its rim in the world, as the body's own
     * wheel is placed, and its column in the vehicle's own axes. The game turns no model hung on
     * a part as the vehicle is steered, so the wheel stands as modelled.
     */
    private static void placeWheel(Seated seated, float[] read, float[] axes, float settles) {
        float[] now = seated.moddedWheelNow;
        for (int row = 0; row < 3; row++) {
            now[row] = partStands[row] * read[7] + partStands[4 + row] * read[8] + partStands[8 + row] * read[9] + partStands[12 + row] - axes[6 + row] * settles;
        }
        now[3] = (float) Math.cos(read[4]);
        now[4] = 0f;
        now[5] = (float) Math.sin(read[4]);
        now[6] = read[6];
        seated.moddedWheel = now;
    }

    /**
     * The steering wheel in a model a vehicle hangs on a part, or NONE. Read with partFrame set
     * for the model, which takes the model's own space to along, across and up from the ground
     * under the vehicle: the middle of the box its ring fills, in the model's own space, and how
     * wide it is across the vehicle in tiles; then its face (CabinParts.faceOf): the lean of its
     * column from level in radians, how dished it is in tiles, how far out from its middle a
     * hand holds its rim; and where the middle of its rim is, in the model's own space.
     */
    private static float[] roundIn(ByteBuffer raw, int stride, int vertices, int[] elements, float[] seat, float middleLine) {
        float[] at = new float[vertices * 3];
        for (int i = 0; i < vertices; i++) {
            for (int row = 0; row < 3; row++) {
                float place = partFrame[row * 4 + 3];
                for (int axis = 0; axis < 3; axis++) {
                    place += partFrame[row * 4 + axis] * raw.getFloat(i * stride + axis * 4);
                }
                at[i * 3 + row] = place;
            }
        }
        int[] ring = CabinParts.roundWheelOf(at, elements, seat, middleLine);
        if (ring == null) {
            return NONE;
        }
        float[] low = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, high = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        float least = Float.MAX_VALUE, most = -Float.MAX_VALUE;
        for (int i : ring) {
            for (int axis = 0; axis < 3; axis++) {
                float v = raw.getFloat(i * stride + axis * 4);
                low[axis] = Math.min(low[axis], v);
                high[axis] = Math.max(high[axis], v);
            }
            least = Math.min(least, at[i * 3 + 1]);
            most = Math.max(most, at[i * 3 + 1]);
        }
        float wide = most - least;
        float[] face = CabinParts.faceOf(at, ring);
        float lean = face[0], deep = face[4];
        // The rim's middle along, across and up, taken back into the model's own space through partFrame.
        float[] rim = unframed(face[5] + (float) Math.cos(lean) * deep, face[6], face[7] + (float) Math.sin(lean) * deep);
        return new float[] {(low[0] + high[0]) / 2f, (low[1] + high[1]) / 2f, (low[2] + high[2]) / 2f, wide, lean, face[3], CabinParts.gripRadius(wide), rim[0], rim[1], rim[2]};
    }

    /** A place along, across and up from the ground under the vehicle, in the model's own space: partFrame undone. */
    private static float[] unframed(float along, float across, float up) {
        float[] f = partFrame;
        float bx = along - f[3], by = across - f[7], bz = up - f[11];
        float det = f[0] * (f[5] * f[10] - f[6] * f[9]) - f[1] * (f[4] * f[10] - f[6] * f[8]) + f[2] * (f[4] * f[9] - f[5] * f[8]);
        float x = (bx * (f[5] * f[10] - f[6] * f[9]) - f[1] * (by * f[10] - f[6] * bz) + f[2] * (by * f[9] - f[5] * bz)) / det;
        float y = (f[0] * (by * f[10] - f[6] * bz) - bx * (f[4] * f[10] - f[6] * f[8]) + f[2] * (f[4] * bz - by * f[8])) / det;
        float z = (f[0] * (f[5] * bz - by * f[9]) - f[1] * (f[4] * bz - by * f[8]) + bx * (f[4] * f[9] - f[5] * f[8])) / det;
        return new float[] {x, y, z};
    }

    /**
     * A vehicle's paint. The game gives a body's skin this hue and moves the skin's strength
     * and lightness by the other two. The skin is not read here: the two are taken as the
     * paint's own, which is right for a skin of middling strength and lightness.
     */
    private static void paint(float hue, float strength, float light, float[] out) {
        for (int channel = 0; channel < 3; channel++) {
            float turn = (hue * 6f + (channel == 0 ? 0f : channel == 1 ? 4f : 2f)) % 6f;
            float ramp = Math.clamp(Math.abs(turn - 3f) - 1f, 0f, 1f);
            out[channel] = light * (1f - strength + strength * ramp);
        }
    }

    /** The prefix of the game's vehicle shaders that draw a body in its skin's own colours and take no paint. */
    private static final String SKIN_COLOURED = "vehicle_norandom";

    private record Skinned(Body body, Texture skin) {}

    private static final HashMap<Skinned, float[]> skinColours = new HashMap<>();

    /**
     * The colour of a body that is its skin's own: the skin's mean over the bonnet, which is what
     * of the paint is seen from the seat, or over the whole skin for a body whose parts are not
     * told. Null while the skin is not loaded; NONE for a skin that cannot be read back, said once.
     */
    private static float[] skinColour(Body body, Texture skin, Parts parts, String script) {
        Skinned key = new Skinned(body, skin);
        float[] known = skinColours.get(key);
        if (known != null) {
            return known;
        }
        if (!skin.isReady() || skin.getID() <= 0) {
            return null;
        }
        float[] colour = parts == null || parts.bonnetUv.length == 0 ? meanOf(skin) : meanAt(skin, parts.bonnetUv);
        if (colour == null) {
            Log.info("the skin of " + script + " cannot be read back for its colour: its bonnet is drawn in the paint the game gave the vehicle");
            colour = NONE;
        } else if (DevBuild.ON) {
            Log.info(String.format(Locale.ROOT, "%s is drawn in its skin's own colours: its bonnet (%d triangles) is %.2f, %.2f, %.2f",
                    script, parts == null ? 0 : parts.bonnetUv.length / 2, colour[0], colour[1], colour[2]));
        }
        skinColours.put(key, colour);
        return colour;
    }

    /** The mean colour of a picture at the texture places given, u then v, read back from the graphics card. Null for one too big to read. */
    private static float[] meanAt(Texture picture, float[] uv) {
        int was = GL11.glGetInteger(GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL_TEXTURE_2D, picture.getID());
        int width = GL11.glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH), height = GL11.glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
        if (width <= 0 || height <= 0 || (long) width * height > MOST_TEXELS) {
            GL11.glBindTexture(GL_TEXTURE_2D, was);
            return null;
        }
        OffHeap.Block read = OffHeap.take(width * height * 4);
        try {
            GL11.glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, read.bytes);
            GL11.glBindTexture(GL_TEXTURE_2D, was);
            float[] mean = new float[3];
            int places = uv.length / 2;
            for (int i = 0; i < places; i++) {
                // Laid against the texture place the same way up as the mask is: see glassBy.
                int x = Math.min((int) (uv[i * 2] * width), width - 1), y = Math.min((int) (uv[i * 2 + 1] * height), height - 1);
                int texel = (y * width + x) * 4;
                for (int channel = 0; channel < 3; channel++) {
                    mean[channel] += (read.bytes.get(texel + channel) & 0xff) / (255f * places);
                }
            }
            return mean;
        } finally {
            read.free();
        }
    }

    /**
     * The middle of the wheel in a body's vertices, read back from the graphics card once a body.
     *
     * @param seats where the vehicle's script seats its passengers, as Ride has them
     */
    private static float[] find(Body body, VehicleModelInstance instance, String name, float[] seats) {
        return withVertices(body.mesh().vb, name, "its driver's place and its glass are the game's own", NONE, (raw, stride, vertices, elements) -> {
            boolean[] glass = glassIn(raw, stride, elements, instance, name);
            float[] wheel = wheelIn(raw, stride, vertices, elements, name, seats);
            boolean[] front = new boolean[glass.length];
            if (wheel != NONE) {
                Parts made = partsIn(raw, stride, vertices, elements, glass, wheel, name, seats);
                parts.put(body, made);
                front = made.front;
            }
            FloatBuffer loose = loose(raw, stride, elements, glass, front);
            panes.put(body, loose);
            int rear = 0;
            for (int t = 0; t < glass.length; t++) {
                rear += glass[t] && !front[t] ? 3 : 0;
            }
            rearPanes.put(body, rear);
            return wheel;
        });
    }

    private interface VertexRead<T> {
        T read(ByteBuffer raw, int stride, int vertices, int[] elements);
    }

    /**
     * Reads a model's vertices back from the graphics card and hands them to read, or says why
     * not and returns NONE for one not laid out as the reads take it.
     */
    private static <T> T withVertices(VertexBufferObject buffer, String name, String otherwise, T none, VertexRead<T> read) {
        try {
            if (handleField == null) {
                handleField = VertexBufferObject.class.getDeclaredField(Links.BUFFER_HANDLE.name());
                handleField.setAccessible(true);
                elementsField = VertexBufferObject.class.getDeclaredField(Links.BUFFER_ELEMENTS.name());
                elementsField.setAccessible(true);
            }
            Object handle = handleField.get(buffer);
            int[] elements = (int[]) elementsField.get(buffer);
            if (handle == null || elements == null) {
                throw new IllegalStateException("the vehicle model " + name + " has no buffer to read");
            }
            if (bufferField == null) {
                bufferField = handle.getClass().getDeclaredField(Links.HANDLE_BUFFER.name());
                bufferField.setAccessible(true);
                strideField = handle.getClass().getDeclaredField(Links.HANDLE_STRIDE.name());
                strideField.setAccessible(true);
            }
            int stride = strideField.getInt(handle);
            int bound = GL11.glGetInteger(GL_ARRAY_BUFFER_BINDING);
            GL15.glBindBuffer(GL_ARRAY_BUFFER, bufferField.getInt(handle));
            int bytes = GL15.glGetBufferParameteri(GL_ARRAY_BUFFER, GL_BUFFER_SIZE);
            String lacks = lacks(stride, bytes, elements);
            if (lacks != null) {
                GL15.glBindBuffer(GL_ARRAY_BUFFER, bound);
                Log.info("the vehicle model " + name + " is not laid out as the game's own bodies are (" + lacks + "): " + otherwise);
                return none;
            }
            // The vertices as the graphics card has them, read once and given back when what is kept of them is made.
            OffHeap.Block block = OffHeap.take(bytes);
            try {
                ByteBuffer raw = block.bytes;
                GL15.glGetBufferSubData(GL_ARRAY_BUFFER, 0L, raw);
                GL15.glBindBuffer(GL_ARRAY_BUFFER, bound);
                return read.read(raw, stride, bytes / stride, elements);
            } finally {
                block.free();
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the game's vertex buffer is not laid out as this build of the mod knows it: " + e, e);
        }
    }

    /**
     * What of a body's buffer is not as the reads below take it, or null: a place, a normal and a
     * texture place of floats at bytes 0, 12 and 24 of each vertex, as every body of the game's
     * has them, and triangles that name only vertices the buffer holds. A body a vehicle mod
     * supplies need not be.
     */
    static String lacks(int stride, int bytes, int[] elements) {
        if (stride < PLACE_NORMAL_AND_TEXTURE_PLACE) {
            return stride + " bytes a vertex";
        }
        if (elements.length % 3 != 0) {
            return elements.length + " corners, which is not a list of triangles";
        }
        int vertices = bytes / stride;
        for (int element : elements) {
            if (element < 0 || element >= vertices) {
                return "a corner at vertex " + element + " of " + vertices;
            }
        }
        return null;
    }

    private static float[] wheelIn(ByteBuffer raw, int stride, int vertices, int[] elements, String name, float[] seats) {
        // The body as it stands this frame, in tiles along the vehicle, across it and up:
        // which of its pieces is the wheel is told by where the driver sits.
        float[] at = new float[vertices * 3];
        for (int i = 0; i < vertices; i++) {
            for (int row = 0; row < 3; row++) {
                float place = frame[row * 4 + 3];
                for (int axis = 0; axis < 3; axis++) {
                    place += frame[row * 4 + axis] * raw.getFloat(i * stride + axis * 4);
                }
                at[i * 3 + row] = place;
            }
        }
        int[] ring = CabinParts.wheelOf(at, elements, seats == null || seats.length < 3 ? null : new float[] {frame[3] + seats[2], Math.abs(seats[0])}, frame[7]);
        if (ring == null) {
            Log.info("the vehicle model " + name + " has no steering wheel the picture can find (" + elements.length / 3 + " triangles, none a piece of " + WHEEL_TRIANGLES
                    + "): its driver's place is the game's own");
            return NONE;
        }
        float[] low = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, high = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i : ring) {
            for (int axis = 0; axis < 3; axis++) {
                float v = raw.getFloat(i * stride + axis * 4);
                low[axis] = Math.min(low[axis], v);
                high[axis] = Math.max(high[axis], v);
            }
        }
        return new float[] {(low[0] + high[0]) / 2f, (low[1] + high[1]) / 2f, (low[2] + high[2]) / 2f};
    }

    /**
     * Which triangles of a body are glass. The game's vehicle mask paints each window in a
     * colour of its own, every channel of it nothing or a half, and a triangle is a pane when
     * the mask at its middle is one of those. None are, for a body whose mask does not say.
     */
    private static boolean[] glassIn(ByteBuffer raw, int stride, int[] elements, VehicleModelInstance instance, String name) {
        boolean[] none = new boolean[elements.length / 3];
        if (instance.textureMask == null || stride < 32) {
            Log.info("the vehicle model " + name + " has no mask to tell its glass by: its windows are told by their colour");
            return none;
        }
        int was = GL11.glGetInteger(GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL_TEXTURE_2D, instance.textureMask.getID());
        int width = GL11.glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH), height = GL11.glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
        if (width <= 0 || height <= 0) {
            GL11.glBindTexture(GL_TEXTURE_2D, was);
            Log.info("the vehicle model " + name + " has an empty mask: its windows are told by their colour");
            return none;
        }
        if ((long) width * height > MOST_TEXELS) {
            // Four bytes a texel would pass what an int holds at 32768 a side, and the read would write past the block.
            GL11.glBindTexture(GL_TEXTURE_2D, was);
            Log.info("the vehicle model " + name + " has a mask of " + width + " by " + height + ", too large to read back: its windows are told by their colour");
            return none;
        }
        // The mask is as big as the vehicle's skin: read out of memory that goes back when the triangles are told apart.
        OffHeap.Block read = OffHeap.take(width * height * 4);
        try {
            GL11.glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, read.bytes);
            GL11.glBindTexture(GL_TEXTURE_2D, was);
            return glassBy(read.bytes, width, height, raw, stride, elements, name);
        } finally {
            read.free();
        }
    }

    private static boolean[] glassBy(ByteBuffer mask, int width, int height, ByteBuffer raw, int stride, int[] elements, String name) {
        int triangles = elements.length / 3;
        boolean[][] glass = new boolean[2][triangles];
        int[] found = new int[2];
        for (int flip = 0; flip < 2; flip++) {
            for (int t = 0; t < triangles; t++) {
                float u = 0f, v = 0f;
                for (int corner = 0; corner < 3; corner++) {
                    int at = elements[t * 3 + corner] * stride + 24;
                    u += raw.getFloat(at) / 3f;
                    v += raw.getFloat(at + 4) / 3f;
                }
                u -= (float) Math.floor(u);
                v -= (float) Math.floor(v);
                int x = Math.min((int) (u * width), width - 1), y = Math.min((int) ((flip == 0 ? v : 1f - v) * height), height - 1);
                int texel = (y * width + x) * 4;
                int halves = 0, nones = 0;
                for (int channel = 0; channel < 3; channel++) {
                    int value = mask.get(texel + channel) & 0xff;
                    if (value < 40) {
                        nones++;
                    } else if (value > 100 && value < 156) {
                        halves++;
                    }
                }
                glass[flip][t] = halves + nones == 3 && halves >= 1 && halves <= 2;
                if (glass[flip][t]) {
                    found[flip]++;
                }
            }
        }
        // The mask lies the same way up against every model: the first way.
        int way = 0;
        if (DevBuild.ON) {
            Log.info("glass of " + name + ": " + found[0] + " triangles one way up, " + found[1] + " the other, of " + triangles + "; mask " + width + "x" + height);
        }
        return found[way] < 6 || found[way] > triangles / 3 ? new boolean[triangles] : glass[way];
    }

    /**
     * The panes of a body as loose triangles in the model's own space, those of the front of
     * the glasshouse after the rest. Empty for a body with none found.
     */
    private static FloatBuffer loose(ByteBuffer raw, int stride, int[] elements, boolean[] glass, boolean[] front) {
        int found = 0;
        for (boolean pane : glass) {
            found += pane ? 1 : 0;
        }
        FloatBuffer out = ByteBuffer.allocateDirect(found * 9 * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (int pass = 0; pass < 2; pass++) {
            for (int t = 0; t < glass.length; t++) {
                if (glass[t] && front[t] == (pass == 1)) {
                    for (int corner = 0; corner < 3; corner++) {
                        int at = elements[t * 3 + corner] * stride;
                        out.put(raw.getFloat(at)).put(raw.getFloat(at + 4)).put(raw.getFloat(at + 8));
                    }
                }
            }
        }
        out.flip();
        return out;
    }

    /**
     * What part of the cabin each triangle of a body is. The body is measured as it stands
     * this frame, in tiles along the vehicle, across it and up, which is the frame the script
     * seats its passengers in.
     */
    private static Parts partsIn(ByteBuffer raw, int stride, int vertices, int[] elements, boolean[] glass, float[] wheel, String name, float[] scriptSeats) {
        float[] at = new float[vertices * 3], facing = new float[vertices * 3];
        for (int i = 0; i < vertices; i++) {
            for (int row = 0; row < 3; row++) {
                float place = frame[row * 4 + 3], faces = 0f;
                for (int axis = 0; axis < 3; axis++) {
                    place += frame[row * 4 + axis] * raw.getFloat(i * stride + axis * 4);
                    faces += frame[row * 4 + axis] * raw.getFloat(i * stride + 12 + axis * 4);
                }
                at[i * 3 + row] = place;
                facing[i * 3 + row] = faces;
            }
        }
        float[] middle = new float[3];
        for (int row = 0; row < 3; row++) {
            middle[row] = frame[row * 4] * wheel[0] + frame[row * 4 + 1] * wheel[1] + frame[row * 4 + 2] * wheel[2] + frame[row * 4 + 3];
        }
        float[] seats = CabinParts.seats(scriptSeats, frame[3], frame[7], middle[1]);
        CabinParts.Found found = CabinParts.classify(at, facing, elements, glass, middle, seats);
        int bonnets = 0;
        for (int kind : found.kinds) {
            bonnets += kind == CabinParts.BONNET ? 1 : 0;
        }
        float[] bonnetUv = new float[bonnets * 2];
        for (int t = 0, b = 0; t < found.kinds.length; t++) {
            if (found.kinds[t] == CabinParts.BONNET) {
                float u = 0f, v = 0f;
                for (int corner = 0; corner < 3; corner++) {
                    int uv = elements[t * 3 + corner] * stride + 24;
                    u += raw.getFloat(uv) / 3f;
                    v += raw.getFloat(uv + 4) / 3f;
                }
                bonnetUv[b++] = u - (float) Math.floor(u);
                bonnetUv[b++] = v - (float) Math.floor(v);
            }
        }
        Parts made = new Parts(ByteBuffer.allocateDirect(elements.length * 3 * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer(), found.seatPieces > 0,
                new float[] {found.footAlong - middle[0], found.footUp - middle[2], found.sillUp - middle[2], found.glassOut - Math.abs(middle[1] - found.middleAcross),
                    found.middleAcross - middle[1], found.dashTop - middle[2], found.dashBack - middle[0]}, found.front, bonnetUv);
        for (int kind = 0; kind < CabinParts.KINDS; kind++) {
            made.first[kind] = made.triangles.position() / 3;
            for (int t = 0; t < found.kinds.length; t++) {
                if (found.kinds[t] == kind) {
                    for (int corner = 0; corner < 3; corner++) {
                        int v = elements[t * 3 + corner] * stride;
                        made.triangles.put(raw.getFloat(v)).put(raw.getFloat(v + 4)).put(raw.getFloat(v + 8));
                    }
                }
            }
            made.count[kind] = made.triangles.position() / 3 - made.first[kind];
        }
        made.triangles.flip();
        if (DevBuild.ON) {
            Log.info(String.format(Locale.ROOT,
                    "cabin of %s: %d seat pieces, and %d seat, %d dashboard, %d door and %d bonnet vertices; the driver sits %.3f behind the wheel and %.3f beside it;"
                            + " windscreen foot %.3f ahead of the wheel and %.3f above, sill %.3f above, dashboard up to %.3f",
                    name, found.seatPieces, made.count[CabinParts.SEAT], made.count[CabinParts.DASH], made.count[CabinParts.DOOR], made.count[CabinParts.BONNET],
                    seats.length == 0 ? Float.NaN : middle[0] - seats[0], seats.length == 0 ? Float.NaN : seats[1] - middle[1],
                    made.measures[0], made.measures[1], made.measures[2], made.measures[5]));
        }
        if (!made.seats) {
            Log.info("the vehicle model " + name + " has no seat that is a piece of its own: its seats are told by their colour");
        }
        return made;
    }

    private static final IdentityHashMap<Texture, float[]> means = new IdentityHashMap<>();
    /** A picture bigger than this many texels is not read back for its colour. */
    private static final int MOST_TEXELS = 4096 * 4096;

    /**
     * The mean colour of a picture of the game's, each texel counted by how solid it is, read
     * back from the graphics card once. Null while the picture is not loaded, and for one
     * that is empty or too big to read. A picture may be one piece of a larger sheet.
     */
    static float[] meanOf(Texture picture) {
        float[] known = means.get(picture);
        if (known != null || means.containsKey(picture)) {
            return known;
        }
        if (!picture.isReady() || picture.getID() <= 0) {
            return null;
        }
        int was = GL11.glGetInteger(GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL_TEXTURE_2D, picture.getID());
        int width = GL11.glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH), height = GL11.glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
        float[] mean = null;
        if (width > 0 && height > 0 && (long) width * height <= MOST_TEXELS) {
            // A garment's picture is one piece of a sheet of up to 64 MB, read whole: out of memory that goes back at once.
            OffHeap.Block read = OffHeap.take(width * height * 4);
            try {
                GL11.glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, read.bytes);
                mean = meanOver(read.bytes, picture, width, height);
            } finally {
                read.free();
            }
        }
        GL11.glBindTexture(GL_TEXTURE_2D, was);
        means.put(picture, mean);
        return mean;
    }

    /**
     * Gives up what was read of the game's meshes and pictures, which are the game's to load
     * anew between saves: each is read again when it is next sat in or worn. They are read
     * once for every view and given up when none draws: see Characters.letGo. Render thread.
     */
    static void letGo() {
        middles.clear();
        uprights.clear();
        panes.clear();
        rearPanes.clear();
        parts.clear();
        means.clear();
        skinColours.clear();
    }

    private static float[] meanOver(ByteBuffer texels, Texture picture, int width, int height) {
        int fromX = Math.clamp((int) (Math.min(picture.getXStart(), picture.getXEnd()) * width), 0, width - 1);
        int toX = Math.clamp((int) Math.ceil(Math.max(picture.getXStart(), picture.getXEnd()) * width), fromX + 1, width);
        int fromY = Math.clamp((int) (Math.min(picture.getYStart(), picture.getYEnd()) * height), 0, height - 1);
        int toY = Math.clamp((int) Math.ceil(Math.max(picture.getYStart(), picture.getYEnd()) * height), fromY + 1, height);
        long red = 0, green = 0, blue = 0, solid = 0;
        for (int y = fromY; y < toY; y++) {
            for (int x = fromX; x < toX; x++) {
                int at = (y * width + x) * 4;
                int alpha = texels.get(at + 3) & 0xff;
                red += (texels.get(at) & 0xff) * alpha;
                green += (texels.get(at + 1) & 0xff) * alpha;
                blue += (texels.get(at + 2) & 0xff) * alpha;
                solid += alpha;
            }
        }
        return solid > 0 ? new float[] {red / (255f * solid), green / (255f * solid), blue / (255f * solid)} : null;
    }
}
