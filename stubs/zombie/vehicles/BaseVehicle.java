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
    public VehicleParts getParts() { throw new UnsupportedOperationException("reference stub"); }
    public float getCurrentSpeedKmHour() { throw new UnsupportedOperationException("reference stub"); }
    public double getEngineSpeed() { throw new UnsupportedOperationException("reference stub"); }
    public VehiclePart getPassengerDoor(int seat) { throw new UnsupportedOperationException("reference stub"); }
    public VehiclePart getPartById(String id) { throw new UnsupportedOperationException("reference stub"); }
    public zombie.scripting.objects.VehicleScript getScript() { throw new UnsupportedOperationException("reference stub"); }
    public org.joml.Vector3f getWorldPos(org.joml.Vector3f local, org.joml.Vector3f out) { throw new UnsupportedOperationException("reference stub"); }
    public Vector3f getLinearVelocity(Vector3f out) { throw new UnsupportedOperationException("reference stub"); }
    public float getCurrentSteering() { throw new UnsupportedOperationException("reference stub"); }
    public float getMass() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isLocalPhysicSim() { throw new UnsupportedOperationException("reference stub"); }
    public short getId() { throw new UnsupportedOperationException("reference stub"); }
}
