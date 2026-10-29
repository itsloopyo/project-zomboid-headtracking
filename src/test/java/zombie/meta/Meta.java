package zombie.meta;

import java.util.ArrayList;
import zombie.iso.IsoGridSquare;

/** The tests' stand-in for the game's record of where the player has been and what they have seen: the squares it is told were seen, in order. */
public final class Meta {
    public static final Meta instance = new Meta();
    public final ArrayList<IsoGridSquare> told = new ArrayList<>();

    public void dealWithSquareSeen(IsoGridSquare square) {
        told.add(square);
    }
}
