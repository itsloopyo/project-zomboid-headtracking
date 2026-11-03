package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.iso.IsoWorld;

/**
 * Head tracking in the camera of Project Viewpoint, another mod, which draws the world from a
 * camera of its own behind or in the character. Each frame Viewpoint's ThirdPerson.capture fills
 * the frame's camera shot afresh, and among it the angles its own effects add to where the camera
 * looks: addYaw, addPitch and addRoll, in radians, which its CameraView adds to the look's angles
 * for the picture and for what is projected onto the screen. Viewpoint aims from the look's own
 * angles, so the head turns the picture and leaves the aim where the mouse put it. The head's turn
 * is added there as capture returns, and the next capture writes them afresh.
 *
 * Viewpoint's yaw turns right as it grows and its pitch up, as the tracker's do, and its roll
 * tilts the picture the other way.
 *
 * The lean goes where each of Viewpoint's two cameras takes it. Behind the character the shot's
 * addRight, addUp and addBack move the camera along its own right, straight up and back along the
 * look, and CameraView.place reads them again for the picture after capture, while the camera
 * Viewpoint aims and plays sounds from (View.cameraX, cameraY, cameraUp) was placed before. At the
 * eye the shot's camera is off and they are not read, and the eye drawn from is what
 * viewpoint.input.Camera.eye makes of the frame's eye: the picture, its hands and what it projects
 * onto the screen take that, while the aim reads the frame's eye itself. So there the lean is added
 * to what Camera.eye returns.
 *
 * Viewpoint's places are tiles across, levels of ViewCamera.LEVEL_HEIGHT tiles up, and its x and z
 * are the world's x and y measured back from the frame's camX and camY. A lean is moved a tile for
 * each metre the tracker sends, as the mod's own view moves.
 *
 * The fields are found by name, as public fields of the classes the frame holds. Where they are not
 * there as floats, Viewpoint is a version this was not made for: that is said once and nothing is
 * added. Logic thread, but for Camera.eye, which runs where Viewpoint draws.
 */
final class ViewpointCamera {
    /** Older than this, Viewpoint's camera was not drawn lately and nothing it said is used. */
    private static final long STALE_NANOS = 250_000_000L;

    private static Class<?> frameClass;
    private static Field shot, yaw, pitch, roll, back, up, right, on, cut;
    private static Field camX, camY, camZ, eyeX, eyeY, eyeZ, eyeLean, viewYaw;
    private static Field cameraX, cameraY, cameraUp;
    private static boolean refused;

    /** Where Viewpoint's camera was at the last capture, before the lean, in the world's tiles, and who it was for, when. */
    private static IsoPlayer capturedFor;
    private static long capturedAt;
    private static float lookYaw, atX, atY, atUp;
    private static boolean behind, cutUnasked;
    private static final WorldQuery world = new WorldQuery();

    /** The lean Camera.eye adds at the eye, in Viewpoint's axes: nought behind the character. */
    private static volatile float leanX, leanUp, leanZ;

    private ViewpointCamera() {}

    static void addHead(Object frame, IsoGameCharacter character) {
        if (refused || !(character instanceof IsoPlayer player)) {
            return;
        }
        View view = View.of(player);
        if (view == null) {
            return;
        }
        try {
            if (frame.getClass() != frameClass) {
                find(frame.getClass());
            }
            Object camera = shot.get(frame);
            capture(frame, camera, player);
            float[] head = view.headTracking.headTurn();
            if (head != null) {
                yaw.setFloat(camera, yaw.getFloat(camera) + head[0]);
                pitch.setFloat(camera, pitch.getFloat(camera) + head[1]);
                roll.setFloat(camera, roll.getFloat(camera) - head[2]);
            }
            // A cut drops the lean asked for before it, from the camera there was.
            lean(camera, cutUnasked ? NO_LEAN : view.headTracking.eyeAlone());
        } catch (ReflectiveOperationException | RuntimeException failure) {
            refused = true;
            leanX = leanUp = leanZ = 0f;
            Log.error("head tracking is left out of Project Viewpoint's camera: its frame is not as this was made for", failure);
        }
    }

    /** Where the camera is and looks this frame, for the next lean asked for. */
    private static void capture(Object frame, Object camera, IsoPlayer player) throws IllegalAccessException {
        float originX = camX.getFloat(frame), originY = camY.getFloat(frame);
        float look = viewYaw.getFloat(frame);
        boolean nowBehind = on.getBoolean(camera);
        float x = originX - eyeX.getFloat(frame);
        float y = originY - eyeZ.getFloat(frame);
        float height = camZ.getFloat(frame) * ViewCamera.LEVEL_HEIGHT + eyeY.getFloat(frame);
        if (nowBehind) {
            x += cameraX.getFloat(null);
            y += cameraY.getFloat(null);
            height += cameraUp.getFloat(null);
        } else {
            // As Camera.eye puts the eye ahead of the head.
            float ahead = 0.12f + eyeLean.getFloat(frame);
            x += (float) Math.cos(look) * ahead;
            y += (float) Math.sin(look) * ahead;
        }
        long now = System.nanoTime();
        cutUnasked |= capturedFor != player || now - capturedAt > STALE_NANOS || nowBehind != behind || nowBehind && cut.getBoolean(camera);
        capturedFor = player;
        capturedAt = now;
        behind = nowBehind;
        lookYaw = look;
        atX = x;
        atY = y;
        atUp = height;
    }

    /** The lean in the world's axes, put where the camera drawn this frame takes it. */
    private static void lean(Object camera, float[] offset) throws IllegalAccessException {
        if (behind) {
            leanX = leanUp = leanZ = 0f;
            alongTheBoom(lookYaw, offset, boom);
            right.setFloat(camera, right.getFloat(camera) + boom[0]);
            up.setFloat(camera, up.getFloat(camera) + boom[1]);
            back.setFloat(camera, back.getFloat(camera) + boom[2]);
        } else {
            atTheEye(offset, boom);
            leanX = boom[0];
            leanUp = boom[1];
            leanZ = boom[2];
        }
    }

    private static final float[] boom = new float[3], NO_LEAN = new float[3];

    /**
     * A lean in the world's axes as the camera behind the character takes it: right of the look,
     * straight up and back along the look. Of a lean that is level, the share along the look moves
     * the camera along the look itself, so up or down with it as it looks down at the character.
     *
     * @param out right, up and back
     */
    static void alongTheBoom(float lookYaw, float[] offset, float[] out) {
        float cos = (float) Math.cos(lookYaw), sin = (float) Math.sin(lookYaw);
        out[0] = -sin * offset[0] + cos * offset[1];
        out[1] = offset[2];
        out[2] = -cos * offset[0] - sin * offset[1];
    }

    /** A lean in the world's axes in Viewpoint's own, which run against the world's x and y. */
    static void atTheEye(float[] offset, float[] out) {
        out[0] = -offset[0];
        out[1] = offset[2];
        out[2] = -offset[1];
    }

    /**
     * The head tracking of the player Viewpoint's camera was drawn for lately, asked for with the
     * lean, which the next capture puts into that camera. False, with nothing asked, where
     * Viewpoint's camera is not drawn for this player: the caller asks as for the game's own view.
     */
    static boolean follow(IsoPlayer player, HeadTracking headTracking, boolean inGameplay) {
        if (refused || player != capturedFor || System.nanoTime() - capturedAt > STALE_NANOS) {
            return false;
        }
        world.look(IsoWorld.instance.currentCell, (int) Math.floor(atUp / ViewCamera.LEVEL_HEIGHT));
        headTracking.beside(inGameplay, lookYaw, atX, atY, cutUnasked, world);
        cutUnasked = false;
        return true;
    }

    /** A save was left: its player and its map are let go, not held through the menus and the next load. */
    static void forget() {
        capturedFor = null;
        world.look(null, 0);
    }

    /** As Viewpoint's Camera.eye returns the eye it draws from at the character's own eye: the lean added. */
    static void leanTheEye(float[] eye) {
        eye[0] += leanX;
        eye[1] += leanUp;
        eye[2] += leanZ;
    }

    private static void find(Class<?> frame) throws ReflectiveOperationException {
        Field found = frame.getField("cameraShot");
        Class<?> camera = found.getType();
        Field[] floats = {camera.getField("addYaw"), camera.getField("addPitch"), camera.getField("addRoll"),
                camera.getField("addBack"), camera.getField("addUp"), camera.getField("addRight")};
        for (Field each : floats) {
            settable(each, float.class);
        }
        Field[] flags = {camera.getField("on"), camera.getField("cut")};
        for (Field each : flags) {
            settable(each, boolean.class);
        }
        Field[] places = {frame.getField("camX"), frame.getField("camY"), frame.getField("camZ"), frame.getField("eyeX"),
                frame.getField("eyeY"), frame.getField("eyeZ"), frame.getField("eyeLean"), frame.getField("viewYaw")};
        for (Field each : places) {
            settable(each, float.class);
        }
        Class<?> view = Class.forName("viewpoint.core.View", false, frame.getClassLoader());
        Field[] placed = {view.getField("cameraX"), view.getField("cameraY"), view.getField("cameraUp")};
        for (Field each : placed) {
            if (each.getType() != float.class || !Modifier.isStatic(each.getModifiers())) {
                throw new IllegalStateException(view.getName() + "." + each.getName() + " is not a static float");
            }
        }
        shot = found;
        yaw = floats[0];
        pitch = floats[1];
        roll = floats[2];
        back = floats[3];
        up = floats[4];
        right = floats[5];
        on = flags[0];
        cut = flags[1];
        camX = places[0];
        camY = places[1];
        camZ = places[2];
        eyeX = places[3];
        eyeY = places[4];
        eyeZ = places[5];
        eyeLean = places[6];
        viewYaw = places[7];
        cameraX = placed[0];
        cameraY = placed[1];
        cameraUp = placed[2];
        frameClass = frame;
        Log.info("head tracking turns and leans Project Viewpoint's camera, through " + frame.getName() + ".cameraShot");
        if (DevBuild.ON) {
            Log.info("Project Viewpoint's camera is leaned a tile for each metre the tracker sends, as the mod's own view is:"
                    + " Viewpoint's places are read as tiles across and levels of " + ViewCamera.LEVEL_HEIGHT + " tiles up");
        }
    }

    private static void settable(Field field, Class<?> type) {
        if (field.getType() != type || Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
            throw new IllegalStateException(field.getDeclaringClass().getName() + "." + field.getName() + " is not a " + type + " the object holds and can be set");
        }
    }
}
