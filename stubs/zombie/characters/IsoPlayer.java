package zombie.characters;

import zombie.iso.Vector2;

public class IsoPlayer extends IsoLivingCharacter {
    public zombie.AttackType getAttackType() { throw new UnsupportedOperationException("reference stub"); }
    public static int numPlayers;
    public static final IsoPlayer[] players = null;
    public float dirtyRecalcGridStackTime;
    public fmod.fmod.BaseSoundListener soundListener;
    public boolean isSpeek;

    public static IsoPlayer getInstance() { throw new UnsupportedOperationException("reference stub"); }
    public final int getIndex() { throw new UnsupportedOperationException("reference stub"); }
    public final int getPlayerNum() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isLocalPlayer() { throw new UnsupportedOperationException("reference stub"); }
    public short getOnlineID() { throw new UnsupportedOperationException("reference stub"); }
    public Vector2 getAimVector(Vector2 out) { throw new UnsupportedOperationException("reference stub"); }
    public zombie.vehicles.BaseVehicle getNearVehicle() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isAiming() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isAnyAimKeyDown() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.core.skinnedmodel.visual.HumanVisual getHumanVisual() { throw new UnsupportedOperationException("reference stub"); }
}
