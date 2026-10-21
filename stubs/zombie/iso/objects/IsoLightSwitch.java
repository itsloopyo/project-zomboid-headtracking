package zombie.iso.objects;

import zombie.iso.IsoObject;

public class IsoLightSwitch extends IsoObject {
    public boolean lightRoom;
    public boolean streetLight;

    public boolean isActivated() {
        return false;
    }

    public boolean hasLightBulb() { throw new UnsupportedOperationException("reference stub"); }
    public void setBulbItemRaw(String item) { throw new UnsupportedOperationException("reference stub"); }
    public boolean setActive(boolean active, boolean setActiveBoolOnly, boolean ignoreSwitchCheck) { throw new UnsupportedOperationException("reference stub"); }
    public void setActivated(boolean activated) { throw new UnsupportedOperationException("reference stub"); }
    public void switchLight(boolean active) { throw new UnsupportedOperationException("reference stub"); }
}
