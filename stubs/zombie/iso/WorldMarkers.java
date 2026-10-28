package zombie.iso;

public final class WorldMarkers {
    public static final WorldMarkers instance = null;
    private final java.util.List<GridSquareMarker> gridSquareMarkers = null;
    private final PlayerHomingPointList[] homingPoints = null;

    @SuppressWarnings("serial")
    static class PlayerHomingPointList extends java.util.ArrayList<PlayerHomingPoint> {
    }

    public static final class GridSquareMarker {
        public boolean isRemoved() { throw new UnsupportedOperationException("reference stub"); }
        public boolean isActive() { throw new UnsupportedOperationException("reference stub"); }
        public float getSize() { throw new UnsupportedOperationException("reference stub"); }
        public float getR() { throw new UnsupportedOperationException("reference stub"); }
        public float getG() { throw new UnsupportedOperationException("reference stub"); }
        public float getB() { throw new UnsupportedOperationException("reference stub"); }
        public float getOriginalX() { throw new UnsupportedOperationException("reference stub"); }
        public float getOriginalY() { throw new UnsupportedOperationException("reference stub"); }
        public float getOriginalZ() { throw new UnsupportedOperationException("reference stub"); }
    }

    public static class PlayerHomingPoint {
        public boolean isRemoved() { throw new UnsupportedOperationException("reference stub"); }
        public boolean isActive() { throw new UnsupportedOperationException("reference stub"); }
        public int getX() { throw new UnsupportedOperationException("reference stub"); }
        public int getY() { throw new UnsupportedOperationException("reference stub"); }
        public float getR() { throw new UnsupportedOperationException("reference stub"); }
        public float getG() { throw new UnsupportedOperationException("reference stub"); }
        public float getB() { throw new UnsupportedOperationException("reference stub"); }
        public float getA() { throw new UnsupportedOperationException("reference stub"); }
    }
}
