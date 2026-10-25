package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.LosUtil;

/**
 * Looking down on the map, the game shows a zombie only once its square is marked seen in its
 * grid of what the character sees, which its lighting thread works out a square at a time
 * (IsoPlayer.updateLOS, IsoGameCharacter.TestIfSeen). In the game's own view the mark came
 * 100 to 400 ms after the line to a zombie opened, and the fade in took 140 to 260 ms more. Some
 * squares stayed unmarked with a clear line to them for seconds, until the character was a few
 * tiles off: zombies came out of nothing beside the character. Measured in town by day and
 * night, rig runs iso1, night1 and night2.
 *
 * With the ZombiesSeenAtOnce row on, offline, the game's test reads the grid's marks for a
 * zombie's square with the line from the character to it read off the map as it is now
 * (LosUtil.lineClear, where a shut door hides as a wall does) beside them: a clear line marks
 * the square one the character could see, and one in the cone the character takes in as the
 * game last worked it out, which tiredness, panic, drink and the dark narrow, one it sees. The
 * rest of the test runs as the game has it: its sight for the weather and the hour, the
 * detection range in which the character notices what is beside and behind it, and the light the
 * zombie stands in. A zombie the test passes is given the game's target of being seen, and fades
 * in at the game's pace. Both of the game's askings go by it: the zombie's own update, which
 * hides a zombie the test fails, and updateLOS, which hides one off the grid before it asks and
 * is followed here by showing it again. What the character spots, its panic and the game's own
 * slowing for a zombie in sight go by the game's grid as they did.
 *
 * The isometric view only: under the first person picture zombies before the eye are drawn by
 * where they are (FarModels).
 */
final class LiveSight {
    private LiveSight() {}

    /** The cone each local player takes in as the game last worked it out, in degrees across. NaN until it has. */
    private static final float[] coneDegrees = new float[View.COUNT];

    static {
        Arrays.fill(coneDegrees, Float.NaN);
    }

    /**
     * Notes the cone the game worked out for a character, which it hands its lighting thread as
     * LightingJNI.degreesToCone gives it, and hands it back. Logic thread.
     */
    static float coneWorkedOut(float cone, IsoGameCharacter character) {
        if (character instanceof IsoPlayer player && player.isLocalPlayer()) {
            int index = player.getPlayerNum();
            if (index >= 0 && index < coneDegrees.length) {
                coneDegrees[index] = SightRule.coneToDegrees(cone);
            }
        }
        return cone;
    }

    /** The zombie couldSee last read the line to, and whether it is in the cone with the line clear. */
    private static IsoGameCharacter asked;
    private static boolean askedSees;

    /**
     * After IsoGameCharacter.TestIfSeen reads the grid's mark of whether a player of this machine
     * could see a character's square: that mark, or the line to the square clear now. Logic thread.
     */
    static boolean couldSee(boolean marked, IsoGameCharacter character, int index, IsoPlayer player) {
        asked = null;
        if (!(character instanceof IsoZombie) || !Rules.seenAtOnce() || index < 0 || index >= coneDegrees.length || Float.isNaN(coneDegrees[index])) {
            return marked;
        }
        View view = View.of(index);
        if (view != null && view.firstPerson.coversTheGamesView()) {
            return marked;
        }
        IsoGridSquare from = player.getCurrentSquare(), at = character.getCurrentSquare();
        IsoCell cell = IsoWorld.instance.currentCell;
        if (from == null || at == null || cell == null) {
            return marked;
        }
        float coneCos = (float) Math.cos(Math.toRadians(coneDegrees[index] / 2f));
        boolean inCone = player.getDotWithForwardDirection(character.getX(), character.getY()) >= coneCos;
        if (marked && !inCone) {
            return true;
        }
        LosUtil.TestResults line = LosUtil.lineClear(cell, from.getX(), from.getY(), from.getZ(), at.getX(), at.getY(), at.getZ(), false);
        boolean clear = line != LosUtil.TestResults.Blocked && line != LosUtil.TestResults.ClearThroughClosedDoor;
        asked = character;
        askedSees = clear && inCone;
        return marked || clear;
    }

    /**
     * After the same test reads the grid's mark of whether the player sees the square: that mark,
     * or what couldSee found just before it for the same character. Logic thread.
     */
    static boolean canSee(boolean marked, IsoGameCharacter character) {
        boolean sees = asked == character && askedSees;
        asked = null;
        return marked || sees;
    }

    /**
     * After the game's IsoPlayer.updateLOS for a player of this machine, which hides every zombie
     * its grid does not mark seen before it asks whether the character sees it. Logic thread.
     */
    static void afterLineOfSight(IsoPlayer player) {
        if (!Rules.seenAtOnce()) {
            return;
        }
        int index = player.getPlayerNum();
        IsoCell cell = IsoWorld.instance.currentCell;
        if (index < 0 || index >= coneDegrees.length || cell == null) {
            return;
        }
        ArrayList<IsoZombie> zombies = cell.getZombieList();
        for (int i = 0; i < zombies.size(); i++) {
            IsoZombie zombie = zombies.get(i);
            if (zombie.isVisibleToPlayer[index] && zombie.getTargetAlpha(index) == 0f) {
                zombie.setTargetAlpha(index, 1f);
            }
        }
    }
}
