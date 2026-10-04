package zombie.vehicles;

import org.joml.Vector3f;
import zombie.characters.IsoGameCharacter;

public final class BaseVehicle {
    public float getX() { throw new UnsupportedOperationException("reference stub"); }
    public float getY() { throw new UnsupportedOperationException("reference stub"); }
    public Vector3f getForwardVector(Vector3f out) { throw new UnsupportedOperationException("reference stub"); }
    public int getSeat(IsoGameCharacter character) { throw new UnsupportedOperationException("reference stub"); }
    public Vector3f getPassengerWorldPos(int seat, Vector3f out) { throw new UnsupportedOperationException("reference stub"); }
}
