package zombie.iso;

public final class IsoChunk {
    public int wx;
    public int wy;
    public boolean loaded;
    public int lightingUpdateCounter;

    public int getMinLevel() { throw new UnsupportedOperationException("reference stub"); }
    public int getMaxLevel() { throw new UnsupportedOperationException("reference stub"); }
    public IsoGridSquare getGridSquare(int x, int y, int z) { throw new UnsupportedOperationException("reference stub"); }
    public zombie.iso.fboRenderChunk.FBORenderLevels getRenderLevels(int playerIndex) { throw new UnsupportedOperationException("reference stub"); }
}
