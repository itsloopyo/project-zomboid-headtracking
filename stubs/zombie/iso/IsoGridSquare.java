package zombie.iso;

import zombie.core.textures.ColorInfo;
import zombie.util.list.PZArrayList;

public final class IsoGridSquare {
    public zombie.iso.objects.IsoTree getTree() { throw new UnsupportedOperationException("reference stub"); }
    public interface ILighting {
        ColorInfo lightInfo();
        boolean bCanSee();
        int resultLightCount();
        ResultLight getResultLight(int index);
    }

    public static final class ResultLight {
        public static final int RLF_TORCH = stub();
        public int id;
        public float r;
        public float g;
        public float b;
        public int flags;

        private static int stub() { throw new UnsupportedOperationException("reference stub"); }
    }

    public final ILighting[] lighting = null;

    public PZArrayList<IsoObject> getObjects() { throw new UnsupportedOperationException("reference stub"); }
    public int transmitRemoveItemFromSquare(IsoObject object) { throw new UnsupportedOperationException("reference stub"); }
    public zombie.iso.areas.IsoBuilding getBuilding() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.iso.areas.IsoRoom getRoom() { throw new UnsupportedOperationException("reference stub"); }
    public long getRoomID() { throw new UnsupportedOperationException("reference stub"); }
    public IsoObject getWall(boolean north) { throw new UnsupportedOperationException("reference stub"); }
    public IsoObject getFloor() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isBlockedTo(IsoGridSquare other) { throw new UnsupportedOperationException("reference stub"); }
    public boolean isWindowTo(IsoGridSquare other) { throw new UnsupportedOperationException("reference stub"); }
    public LosUtil.TestResults testVisionAdjacent(int x, int y, int z, boolean specialDiag, boolean ignoreDoors) { throw new UnsupportedOperationException("reference stub"); }
    public boolean isSolid() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isSolidTrans() { throw new UnsupportedOperationException("reference stub"); }
    public java.util.ArrayList<zombie.iso.objects.IsoWorldInventoryObject> getWorldObjects() { throw new UnsupportedOperationException("reference stub"); }
    public boolean haveElectricity() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isOutside() { throw new UnsupportedOperationException("reference stub"); }
    public boolean has(zombie.iso.SpriteDetails.IsoFlagType flag) { throw new UnsupportedOperationException("reference stub"); }
    public boolean isSeen(int playerIndex) { throw new UnsupportedOperationException("reference stub"); }
    public int getPlayerCutawayFlag(int playerIndex, long time) { throw new UnsupportedOperationException("reference stub"); }
    public boolean isCouldSee(int playerIndex) { throw new UnsupportedOperationException("reference stub"); }
    public boolean isCanSee(int playerIndex) { throw new UnsupportedOperationException("reference stub"); }
    public ColorInfo getLightInfo(int playerIndex) { throw new UnsupportedOperationException("reference stub"); }
    public java.util.ArrayList<IsoMovingObject> getMovingObjects() { throw new UnsupportedOperationException("reference stub"); }
    public java.util.ArrayList<IsoMovingObject> getStaticMovingObjects() { throw new UnsupportedOperationException("reference stub"); }
    public boolean HasTree() { throw new UnsupportedOperationException("reference stub"); }
    public int getX() { throw new UnsupportedOperationException("reference stub"); }
    public int getY() { throw new UnsupportedOperationException("reference stub"); }
    public int getZ() { throw new UnsupportedOperationException("reference stub"); }
    public IsoChunk getChunk() { throw new UnsupportedOperationException("reference stub"); }
    public void cacheLightInfo() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.core.properties.PropertyContainer getProperties() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.vehicles.BaseVehicle getVehicleContainer() { throw new UnsupportedOperationException("reference stub"); }
    public IsoChunk chunk;
    public zombie.iso.objects.IsoFire getFire() { throw new UnsupportedOperationException("reference stub"); }
}
