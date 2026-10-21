package zombie.iso;

import java.util.ArrayList;
import zombie.iso.objects.IsoLightSwitch;

public class IsoLightSource {
    public int id;
    public int x;
    public int y;
    public int z;
    public int radius;
    public float r;
    public float g;
    public float b;
    public boolean active;
    public int life;
    public ArrayList<IsoLightSwitch> switches;
}
