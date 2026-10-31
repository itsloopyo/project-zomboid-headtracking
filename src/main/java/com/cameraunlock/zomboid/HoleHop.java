package com.cameraunlock.zomboid;

import java.util.ArrayList;
import zombie.ai.states.ClimbOverFenceState;
import zombie.characters.IsoPlayer;
import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoDirections;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.objects.IsoWindow;
import zombie.util.list.PZArrayList;

/**
 * Climbing through a hole shot in a fence, a wall or a shut door (BlastHoles, Breakage) that a
 * body fits through: holding the Interact key with the reticle on the hole, offline, vaults the
 * character through it the way the game vaults a low fence. The game's vault (ClimbOverFenceState)
 * lets the body through the edge between the square it starts on and the next whatever stands
 * there, and crosses it where along the edge the character stands, so the character is first moved
 * along the edge to the middle of the opening. The game's own climbOverFence asks for a fence it
 * calls hoppable on that edge, so the state is set up here as it sets it up.
 */
final class HoleHop {
    private HoleHop() {}

    /**
     * The opening a body needs: half its width along the edge in tiles, and its foot and its head
     * in levels over the floor, a gap from knee height to over the shoulders 0.5 wide.
     */
    static final float HALF_WIDTH = 0.25f, FOOT = 0.45f / ViewCamera.LEVEL_HEIGHT, HEAD = 1.35f / ViewCamera.LEVEL_HEIGHT;
    /** How many points across and up the opening is looked at, edges included. */
    private static final int ACROSS = 6, UP = 9;
    /** How far along the edge from where the reticle meets it the middle of the opening may be, and the steps it is looked for in. */
    private static final float FURTHEST = 0.3f, STEP = 0.025f;

    /** The game's event that starts the vault, as IsoGameCharacter.climbOverFence reports it. */
    private static final String VAULT = "EventClimbFence";

    /** A climb found: the way out of the character's square, and where along that edge, from its west or north end, the body goes through. */
    record Hop(IsoDirections way, float along) {}

    /** Whether a point on the edge, in tiles along it and levels over its floor, is open. */
    interface Open {
        boolean at(float along, float up);
    }

    /**
     * The middle along the edge of the opening a body fits through nearest to where the reticle
     * meets it, no further off than FURTHEST and with the whole of it on the edge; NaN for none.
     */
    static float opening(Open open, float aimAlong) {
        for (int k = 0; k * STEP <= FURTHEST + 1e-6f; k++) {
            for (int sign = 1; sign >= (k == 0 ? 1 : -1); sign -= 2) {
                float middle = aimAlong + sign * k * STEP;
                if (middle - HALF_WIDTH >= 0f && middle + HALF_WIDTH <= 1f && clear(open, middle)) {
                    return middle;
                }
            }
        }
        return Float.NaN;
    }

    private static boolean clear(Open open, float middle) {
        for (int i = 0; i <= ACROSS; i++) {
            float along = middle - HALF_WIDTH + 2f * HALF_WIDTH * i / ACROSS;
            for (int j = 0; j <= UP; j++) {
                if (!open.at(along, FOOT + (HEAD - FOOT) * j / UP)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The climb the reticle's line asks for: through the edge of the character's square it leaves
     * the square by, where it meets that edge in a hole and a body fits through there, onto a square
     * the game would let a character climb through a window onto. Null for none.
     */
    static Hop find(ViewCamera.Pose line, Terrain terrain, IsoPlayer player) {
        IsoGridSquare from = player.getCurrentSquare();
        if (from == null) {
            return null;
        }
        float t = Float.POSITIVE_INFINITY;
        IsoDirections way = null;
        if (line.forwardY != 0f) {
            float y = line.forwardY < 0f ? from.getY() : from.getY() + 1f, at = (y - line.eyeY) / line.forwardY;
            float x = line.eyeX + line.forwardX * at;
            if (at >= 0f && x >= from.getX() && x <= from.getX() + 1f) {
                t = at;
                way = line.forwardY < 0f ? IsoDirections.N : IsoDirections.S;
            }
        }
        if (line.forwardX != 0f) {
            float x = line.forwardX < 0f ? from.getX() : from.getX() + 1f, at = (x - line.eyeX) / line.forwardX;
            float y = line.eyeY + line.forwardY * at;
            if (at >= 0f && at < t && y >= from.getY() && y <= from.getY() + 1f) {
                t = at;
                way = line.forwardX < 0f ? IsoDirections.W : IsoDirections.E;
            }
        }
        if (way == null) {
            return null;
        }
        boolean north = way == IsoDirections.N || way == IsoDirections.S;
        IsoGridSquare to = from.getAdjacentSquare(way);
        // An edge is the north or west edge of the square south or east of it, and what stands on it is that square's.
        IsoGridSquare owner = way == IsoDirections.N || way == IsoDirections.W ? from : to;
        if (owner == null) {
            return null;
        }
        float along = north ? line.eyeX + line.forwardX * t - owner.getX() : line.eyeY + line.forwardY * t - owner.getY();
        int level = owner.getZ();
        float up = (line.eyeUp + line.forwardUp * t - terrain.lift(owner.getX() + 0.5f, owner.getY() + 0.5f, level)) / ViewCamera.LEVEL_HEIGHT - level;
        ArrayList<IsoObject> blockers = blockers(owner, north);
        if (blockers.isEmpty()) {
            return null;
        }
        for (IsoObject blocker : blockers) {
            if (BlastHoles.of(blocker) == null || DoorGlass.isDoor(blocker) && DoorGlass.barricaded(blocker)) {
                return null;
            }
        }
        Open open = (a, u) -> {
            for (IsoObject blocker : blockers) {
                if (DoorGlass.isDoor(blocker) && !DoorGlass.onLeaf(blocker, north, a) || !BlastHoles.through(blocker, north, a, u)) {
                    return false;
                }
            }
            return true;
        };
        if (!open.at(along, up)) {
            return null;
        }
        float middle = opening(open, along);
        if (Float.isNaN(middle) || to == null || !IsoWindow.canClimbThroughHelper(player, from, to, north)) {
            return null;
        }
        return new Hop(way, middle);
    }

    /** What stops a character crossing a square's north or west edge: what collides there, and a shut door. */
    private static ArrayList<IsoObject> blockers(IsoGridSquare owner, boolean north) {
        ArrayList<IsoObject> found = new ArrayList<>();
        PZArrayList<IsoObject> objects = owner.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            PropertyContainer properties = object.getSprite() == null ? null : object.getSprite().getProperties();
            if (DoorGlass.shutOn(object, north) || properties != null && properties.has(north ? IsoFlagType.collideN : IsoFlagType.collideW)) {
                found.add(object);
            }
        }
        return found;
    }

    /** Starts the climb: the character moved along the edge to where the body goes through, and the game's vault set going. */
    static void go(IsoPlayer player, Hop hop) {
        IsoGridSquare from = player.getCurrentSquare();
        if (hop.way == IsoDirections.N || hop.way == IsoDirections.S) {
            float x = from.getX() + hop.along;
            player.setX(x);
            player.setNextX(x);
            player.setLastX(x);
        } else {
            float y = from.getY() + hop.along;
            player.setY(y);
            player.setNextY(y);
            player.setLastY(y);
        }
        ClimbOverFenceState.instance().setParams(player, hop.way);
        player.getActionContext().reportEvent(VAULT);
        Log.info("asked the game to vault " + hop.way + " through a hole at " + String.format(java.util.Locale.ROOT, "%.2f", hop.along) + " along the edge of ("
                + from.getX() + ", " + from.getY() + ", " + from.getZ() + ")");
    }
}
