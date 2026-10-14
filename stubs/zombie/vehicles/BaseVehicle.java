package zombie.vehicles;

import org.joml.Vector3f;
import zombie.characters.IsoGameCharacter;

public final class BaseVehicle extends zombie.iso.IsoMovingObject {
    public float getX() { throw new UnsupportedOperationException("reference stub"); }
    public float getZ() { throw new UnsupportedOperationException("reference stub"); }
    public boolean getHeadlightsOn() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isEngineRunning() { throw new UnsupportedOperationException("reference stub"); }
    public String getScriptName() { throw new UnsupportedOperationException("reference stub"); }
    public float getY() { throw new UnsupportedOperationException("reference stub"); }
    public Vector3f getForwardVector(Vector3f out) { throw new UnsupportedOperationException("reference stub"); }
    public Vector3f getUpVector(Vector3f out) { throw new UnsupportedOperationException("reference stub"); }
    public int getSeat(IsoGameCharacter character) { throw new UnsupportedOperationException("reference stub"); }
    public Vector3f getPassengerWorldPos(int seat, Vector3f out) { throw new UnsupportedOperationException("reference stub"); }
    public void repair() { throw new UnsupportedOperationException("reference stub"); }
    public void setHotwired(boolean hotwired) { throw new UnsupportedOperationException("reference stub"); }
    public boolean enter(int seat, IsoGameCharacter character) { throw new UnsupportedOperationException("reference stub"); }
    public boolean isSeatOccupied(int seat) { throw new UnsupportedOperationException("reference stub"); }
    public VehicleParts getParts() { throw new UnsupportedOperationException("reference stub"); }
    public float getCurrentSpeedKmHour() { throw new UnsupportedOperationException("reference stub"); }
    public float getSpeed2D() { throw new UnsupportedOperationException("reference stub"); }
    public boolean shouldCollideWithObjects() { throw new UnsupportedOperationException("reference stub"); }
    public Vector3f getLocalPos(float x, float y, float z, Vector3f out) { throw new UnsupportedOperationException("reference stub"); }
    public void crash(float amount, boolean front) { throw new UnsupportedOperationException("reference stub"); }
    public void applyImpulseFromHitPlant(zombie.iso.IsoObject object, float amount) { throw new UnsupportedOperationException("reference stub"); }
    public double getEngineSpeed() { throw new UnsupportedOperationException("reference stub"); }
    public VehiclePart getPassengerDoor(int seat) { throw new UnsupportedOperationException("reference stub"); }
    public VehiclePart getPartById(String id) { throw new UnsupportedOperationException("reference stub"); }
    public void transmitPartItem(VehiclePart part) { throw new UnsupportedOperationException("reference stub"); }
    public void playSound(String name) { throw new UnsupportedOperationException("reference stub"); }
    public zombie.scripting.objects.VehicleScript getScript() { throw new UnsupportedOperationException("reference stub"); }
    public org.joml.Vector3f getWorldPos(org.joml.Vector3f local, org.joml.Vector3f out) { throw new UnsupportedOperationException("reference stub"); }
    public org.joml.Vector3f getWorldPos(float x, float y, float z, org.joml.Vector3f out) { throw new UnsupportedOperationException("reference stub"); }
    public Vector3f getLinearVelocity(Vector3f out) { throw new UnsupportedOperationException("reference stub"); }
    public float getCurrentSteering() { throw new UnsupportedOperationException("reference stub"); }
    public int getMaxPassengers() { throw new UnsupportedOperationException("reference stub"); }
    public IsoGameCharacter getCharacter(int seat) { throw new UnsupportedOperationException("reference stub"); }
    public org.joml.Vector3f getIntersectPoint(org.joml.Vector3f start, org.joml.Vector3f end, org.joml.Vector3f result) { throw new UnsupportedOperationException("reference stub"); }
    public zombie.iso.IsoChunk chunk;
    public float savedPhysicsZ;
    public final org.joml.Quaternionf savedRot = null;
    public final zombie.core.physics.Transform jniTransform = null;
    public BaseVehicle(zombie.iso.IsoCell cell) { throw new UnsupportedOperationException("reference stub"); }
    public short getId() { throw new UnsupportedOperationException("reference stub"); }
    public float getMass() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isBurntOrSmashed() { throw new UnsupportedOperationException("reference stub"); }
    public boolean exit(IsoGameCharacter character) { throw new UnsupportedOperationException("reference stub"); }
    public void setCharacterPosition(IsoGameCharacter character, int seat, String position) { throw new UnsupportedOperationException("reference stub"); }
    public int getSkinIndex() { throw new UnsupportedOperationException("reference stub"); }
    public void setSkinIndex(int index) { throw new UnsupportedOperationException("reference stub"); }
    public void setScriptName(String name) { throw new UnsupportedOperationException("reference stub"); }
    public void setScript() { throw new UnsupportedOperationException("reference stub"); }
    public void setDir(zombie.iso.IsoDirections dir) { throw new UnsupportedOperationException("reference stub"); }
    public void setSquare(zombie.iso.IsoGridSquare square) { throw new UnsupportedOperationException("reference stub"); }
    public void addToWorld() { throw new UnsupportedOperationException("reference stub"); }
    public void permanentlyRemove() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isRemovedFromWorld() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.core.physics.Transform getWorldTransform(zombie.core.physics.Transform out) { throw new UnsupportedOperationException("reference stub"); }
    public void setWorldTransform(zombie.core.physics.Transform transform) { throw new UnsupportedOperationException("reference stub"); }
}
