package zombie.iso.areas;

/** The tests' stand-in for a building of the map, which a test puts squares in: see zombie.iso.IsoGridSquare.building. */
public final class IsoBuilding {
    private final zombie.iso.BuildingDef def;

    public IsoBuilding(zombie.iso.BuildingDef def) {
        this.def = def;
    }

    public zombie.iso.BuildingDef getDef() {
        return def;
    }
}
