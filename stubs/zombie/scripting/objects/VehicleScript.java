package zombie.scripting.objects;

import org.joml.Vector3f;

public final class VehicleScript {
    public int getPassengerCount() { throw new UnsupportedOperationException("reference stub"); }
    public Passenger getPassenger(int index) { throw new UnsupportedOperationException("reference stub"); }

    public static final class Passenger {
        public Position getPositionById(String id) { throw new UnsupportedOperationException("reference stub"); }
    }

    public static final class Position {
        public Vector3f getOffset() { throw new UnsupportedOperationException("reference stub"); }
    }
}
