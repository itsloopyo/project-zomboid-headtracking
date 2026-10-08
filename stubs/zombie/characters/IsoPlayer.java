package zombie.characters;

import zombie.iso.Vector2;

public class IsoPlayer extends IsoLivingCharacter {
    public zombie.AttackType getAttackType() { throw new UnsupportedOperationException("reference stub"); }
    public static int numPlayers;
    public static final IsoPlayer[] players = new IsoPlayer[4];
    public float dirtyRecalcGridStackTime;
    public fmod.fmod.BaseSoundListener soundListener;

    public static IsoPlayer getInstance() { throw new UnsupportedOperationException("reference stub"); }
    public final int getIndex() { throw new UnsupportedOperationException("reference stub"); }
    public Vector2 getAimVector(Vector2 out) { throw new UnsupportedOperationException("reference stub"); }
    public boolean isAiming() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.core.skinnedmodel.visual.HumanVisual getHumanVisual() { throw new UnsupportedOperationException("reference stub"); }
}
