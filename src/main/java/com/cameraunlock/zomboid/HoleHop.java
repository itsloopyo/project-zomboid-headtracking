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
     * The opening a body needs, 0.5 wide and 0.9 m tall: half its width along the edge in tiles,
     * and in levels its height, from its foot to over the shoulders of a body ducking through,
     * and the lowest and the highest its foot is looked for at: knee height, which a hole down to
     * the floor is climbed through from, and waist height, the highest sill a body vaults over.
     */
    static final float HALF_WIDTH = 0.25f, TALL = 0.9f / ViewCamera.LEVEL_HEIGHT, LOWEST = 0.45f / ViewCamera.LEVEL_HEIGHT,
            HIGHEST = 1f / ViewCamera.LEVEL_HEIGHT;
    /** How many points across the opening it is looked at, edges included, and how far apart its rows are, in levels. */
    private static final int ACROSS = 6;
    private static final float ROW = 0.05f / ViewCamera.LEVEL_HEIGHT;
    /** How many rows over the lowest the foot may be on, and how many over its foot the top is on. */
    private static final int SILLS = Math.round((HIGHEST - LOWEST) / ROW), BODY = Math.round(TALL / ROW);
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

    /** Whether the opening TALL high is clear across at middle from a foot between LOWEST and HIGHEST. */
    private static boolean clear(Open open, float middle) {
        int run = 0;
        for (int row = 0; row <= SILLS + BODY; row++) {
            if (across(open, middle, LOWEST + row * ROW)) {
                if (++run > BODY) {
                    return true;
                }
            } else if (row >= SILLS) {
                return false;
            } else {
                run = 0;
            }
        }
        return false;
    }

    private static boolean across(Open open, float middle, float up) {
        for (int i = 0; i <= ACROSS; i++) {
            if (!open.at(middle - HALF_WIDTH + 2f * HALF_WIDTH * i / ACROSS, up)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The climb the reticle's line asks for: through the edge of the character's square it leaves
     * the square by, where it meets that edge in a hole and a body fits through there, onto a square
     * the game would let a character climb through a window onto. Null for none. Said (say) logs
     * why a hole the reticle is in does not take a body: for a press of the key, not for the label
     * under the reticle, which asks every frame.
     */
    static Hop find(ViewCamera.Pose line, Terrain terrain, IsoPlayer player, boolean say) {
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
        if (!holed(owner, north)) {
            return null;
        }
        ArrayList<IsoObject> blockers = blockers(owner, north);
        Open[] cuts = new Open[blockers.size()];
        for (int i = 0; i < cuts.length; i++) {
            IsoObject blocker = blockers.get(i);
            Open cut = cut(BlastHoles.outlinesOn(BlastHoles.of(blocker), north), BlastHoles.fallen(blocker, north));
            cuts[i] = DoorGlass.isDoor(blocker) ? (a, u) -> DoorGlass.onLeaf(blocker, north, a, u) && cut.at(a, u) : cut;
        }
        Open open = (a, u) -> {
            for (Open cut : cuts) {
                if (!cut.at(a, u)) {
                    return false;
                }
            }
            return true;
        };
        if (!open.at(along, up)) {
            return null;
        }
        float middle = opening(open, along);
        String no = Float.isNaN(middle) ? "it has no gap a body fits through, 0.5 wide and 0.9 m tall with its foot at the waist or lower"
                : to == null ? "there is no square beyond it"
                : !IsoWindow.canClimbThroughHelper(player, from, to, north) ? "the game's window climb would land nobody on the square beyond it, where something stands or there is water"
                : null;
        if (no == null) {
            return new Hop(way, middle);
        }
        if (say) {
            Log.info("no climb through " + String.format(java.util.Locale.ROOT, "the hole at %.2f along the %s edge of (%d, %d, %d)", along, north ? "north" : "west",
                    owner.getX(), owner.getY(), owner.getZ()) + ": " + no);
        }
        return null;
    }

    /**
     * Whether something stops a character crossing the edge and everything that does has holes
     * in it, none of it a barricaded door: the only edges a climb can go through. Asks without
     * building anything, as the label under the reticle asks every frame and nearly every edge
     * has no holes.
     */
    private static boolean holed(IsoGridSquare owner, boolean north) {
        PZArrayList<IsoObject> objects = owner.getObjects();
        boolean any = false;
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            if (blocks(object, north)) {
                if (BlastHoles.of(object) == null || DoorGlass.isDoor(object) && DoorGlass.barricaded(object)) {
                    return false;
                }
                any = true;
            }
        }
        return any;
    }

    /**
     * What is cut out of a thing as the picture cuts it: its holes' outlines and what fell from between them. Not
     * BlastHoles.through, which keeps a shot to well inside each hole, so that the ragged rim of every hole in a
     * gap blown open by several reads as wall standing in the middle of the gap.
     */
    static Open cut(ArrayList<float[]> outlines, Breakage.Pieces fallen) {
        return (a, u) -> {
            if (fallen.inside(a, u)) {
                return true;
            }
            float tiles = u * ViewCamera.LEVEL_HEIGHT;
            for (float[] outline : outlines) {
                if (MeshCut.inside(outline, a, tiles)) {
                    return true;
                }
            }
            return false;
        };
    }

    /** What stops a character crossing a square's north or west edge: what collides there, and a shut door. */
    private static ArrayList<IsoObject> blockers(IsoGridSquare owner, boolean north) {
        ArrayList<IsoObject> found = new ArrayList<>();
        PZArrayList<IsoObject> objects = owner.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            if (blocks(object, north)) {
                found.add(object);
            }
        }
        return found;
    }

    private static boolean blocks(IsoObject object, boolean north) {
        PropertyContainer properties = object.getSprite() == null ? null : object.getSprite().getProperties();
        return DoorGlass.shutOn(object, north) || properties != null && properties.has(north ? IsoFlagType.collideN : IsoFlagType.collideW);
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
