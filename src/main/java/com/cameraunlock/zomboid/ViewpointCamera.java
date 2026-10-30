package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;

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
 * The fields are found by name, as public fields of the classes the frame holds. Where they are not
 * there as floats, Viewpoint is a version this was not made for: that is said once and nothing is
 * added. Logic thread.
 */
final class ViewpointCamera {
    private static Class<?> frameClass;
    private static Field shot, yaw, pitch, roll;
    private static boolean refused;

    private ViewpointCamera() {}

    static void addHead(Object frame, IsoGameCharacter character) {
        if (refused || !(character instanceof IsoPlayer player)) {
            return;
        }
        View view = View.of(player);
        float[] head = view == null ? null : view.headTracking.headTurn();
        if (head == null) {
            return;
        }
        try {
            if (frame.getClass() != frameClass) {
                find(frame.getClass());
            }
            Object camera = shot.get(frame);
            yaw.setFloat(camera, yaw.getFloat(camera) + head[0]);
            pitch.setFloat(camera, pitch.getFloat(camera) + head[1]);
            roll.setFloat(camera, roll.getFloat(camera) - head[2]);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            refused = true;
            Log.error("head tracking is left out of Project Viewpoint's camera: its frame is not as this was made for", failure);
        }
    }

    private static void find(Class<?> frame) throws NoSuchFieldException {
        Field found = frame.getField("cameraShot");
        Class<?> camera = found.getType();
        Field[] angles = {camera.getField("addYaw"), camera.getField("addPitch"), camera.getField("addRoll")};
        for (Field angle : angles) {
            if (angle.getType() != float.class || Modifier.isStatic(angle.getModifiers()) || Modifier.isFinal(angle.getModifiers())) {
                throw new IllegalStateException(camera.getName() + "." + angle.getName() + " is not a float the shot holds and can be set");
            }
        }
        shot = found;
        yaw = angles[0];
        pitch = angles[1];
        roll = angles[2];
        frameClass = frame;
        Log.info("head tracking turns Project Viewpoint's camera, through " + frame.getName() + ".cameraShot");
    }
}
