package zombie.scripting.objects;

import org.joml.Vector3f;

public final class VehicleScript {
    public org.joml.Vector3f getExtents() { throw new UnsupportedOperationException("reference stub"); }
    public org.joml.Vector2f getExtentsOffset() { throw new UnsupportedOperationException("reference stub"); }
    public org.joml.Vector3f getCenterOfMassOffset() { throw new UnsupportedOperationException("reference stub"); }
    public int getPassengerCount() { throw new UnsupportedOperationException("reference stub"); }
    public Passenger getPassenger(int index) { throw new UnsupportedOperationException("reference stub"); }
    public int getWheelCount() { throw new UnsupportedOperationException("reference stub"); }
    public Wheel getWheel(int index) { throw new UnsupportedOperationException("reference stub"); }
    public float getRollInfluence() { throw new UnsupportedOperationException("reference stub"); }
    public org.joml.Vector3f getModelOffset() { throw new UnsupportedOperationException("reference stub"); }

    public static final class Passenger {
        public Position getPositionById(String id) { throw new UnsupportedOperationException("reference stub"); }
    }

    public static final class Wheel {
        public boolean front;
        public float radius;
        public String getId() { throw new UnsupportedOperationException("reference stub"); }
        public Vector3f getOffset() { throw new UnsupportedOperationException("reference stub"); }
    }

    public Model getModel() { throw new UnsupportedOperationException("reference stub"); }

    public static final class Model {
        public Vector3f getOffset() { throw new UnsupportedOperationException("reference stub"); }
    }

    public static final class Position {
        public Vector3f getOffset() { throw new UnsupportedOperationException("reference stub"); }
    }
    public String getFullName() { throw new UnsupportedOperationException("reference stub"); }
    public String getName() { throw new UnsupportedOperationException("reference stub"); }
    public String getCarModelName() { throw new UnsupportedOperationException("reference stub"); }
    public String getFileName() { throw new UnsupportedOperationException("reference stub"); }
    public Part getPartById(String id) { throw new UnsupportedOperationException("reference stub"); }

    public static final class Part {
    }
}
