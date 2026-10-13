package zombie;

public abstract class BaseSoundManager {
    public abstract fmod.fmod.Audio PlayWorldSound(String name, zombie.iso.IsoGridSquare square, float delay, float radius, float maxGain, boolean ignoreOutside);
}
