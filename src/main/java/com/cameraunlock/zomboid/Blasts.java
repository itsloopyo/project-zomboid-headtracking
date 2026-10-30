package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Locale;
import zombie.characters.IsoGameCharacter;
import zombie.iso.IsoObject;

/**
 * What a shotgun's blast does to a wooden door or a board fence it strikes. The game fires a
 * blast's pellets one after another in a tick, and the mod is told where each struck
 * (ImpactFinder). Once the tick is over the pellets that struck one thing are looked at together:
 * where enough of them landed close together they blow a hole through it, sized to how close they
 * were, and the rest leave a mark each, as a single pellet does. Far off the pellets have spread
 * apart and only mark it. Logic thread.
 */
final class Blasts {
    private Blasts() {}

    /** The fewest pellets that blow a hole, and how far from their middle each may have struck, in tiles. */
    static final int FEWEST = 3;
    static final float CLOSE = 0.12f;
    /** How much past the furthest pellet of a hole's the hole reaches, and the least and most its radius is, in tiles. */
    static final float BITE = 0.03f, SMALLEST = 0.05f, LARGEST = 0.16f;

    /** A pellet that struck a thing: who fired it, where on the thing (as BlastHoles measures it) and the side it came from. */
    record Struck(IsoGameCharacter shooter, IsoObject object, boolean north, float along, float up, int side) {}

    private static final ArrayList<Struck> struck = new ArrayList<>();
    /** Where each hole's and mark's ragged outline is drawn from: below 2^24, so a float in the save holds it whole. */
    private static final java.util.Random seeds = new java.util.Random();

    /** A pellet struck a thing BlastHoles.blastable says a blast can blow a hole in. */
    static void pellet(IsoGameCharacter shooter, IsoObject object, boolean north, float along, float up, int side) {
        float[] holes = BlastHoles.of(object);
        if (holes != null && BlastHoles.inAHole(holes, north, along, up)) {
            return;
        }
        struck.add(new Struck(shooter, object, north, along, up, side));
    }

    /** Called as a tick of the world starts: the pellets of the last tick's blasts are settled. */
    static void tick() {
        if (struck.isEmpty()) {
            return;
        }
        ArrayList<Struck> group = new ArrayList<>();
        while (!struck.isEmpty()) {
            Struck first = struck.getFirst();
            group.clear();
            for (int i = struck.size() - 1; i >= 0; i--) {
                Struck each = struck.get(i);
                if (each.shooter == first.shooter && each.object == first.object && each.north == first.north) {
                    group.add(each);
                    struck.remove(i);
                }
            }
            settle(group);
        }
    }

    /** One blast's pellets on one thing: a hole where enough struck close together, a mark for each other on a door. */
    private static void settle(ArrayList<Struck> group) {
        Cluster cluster = cluster(group);
        if (cluster != null) {
            BlastHoles.add(cluster.object, cluster.north, cluster.along, cluster.up, cluster.radius, seeds.nextInt(1 << 20), 0);
        }
        if (!DoorGlass.isDoor(group.getFirst().object)) {
            return;
        }
        for (Struck each : group) {
            if (cluster == null || !cluster.members.contains(each)) {
                BlastHoles.add(each.object, each.north, each.along, each.up, BlastHoles.MARK_RADIUS, seeds.nextInt(1 << 20), each.side);
            }
        }
    }

    /** Where a blast's pellets on a thing blew a hole: its middle, its radius, and the pellets it took. */
    record Cluster(IsoObject object, boolean north, float along, float up, float radius, ArrayList<Struck> members) {}

    /**
     * The hole a blast's pellets on one thing blow, or null: the pellets furthest from the middle of
     * those left are let go one at a time until every one left is within CLOSE of their middle, and
     * a hole is blown where FEWEST or more are left. Distances are in tiles, up as well as along.
     */
    static Cluster cluster(ArrayList<Struck> group) {
        ArrayList<Struck> left = new ArrayList<>(group);
        while (left.size() >= FEWEST) {
            float along = 0f, up = 0f;
            for (Struck each : left) {
                along += each.along;
                up += each.up;
            }
            along /= left.size();
            up /= left.size();
            Struck furthest = null;
            float most = -1f;
            for (Struck each : left) {
                float d = distance(each, along, up);
                if (d > most) {
                    most = d;
                    furthest = each;
                }
            }
            if (most <= CLOSE) {
                float radius = Math.clamp(most + BITE, SMALLEST, LARGEST);
                if (DevBuild.ON) {
                    Log.info(String.format(Locale.ROOT, "a blast: %d of its %d pellets on the %s struck within %.3f tiles of their middle", left.size(), group.size(),
                            DoorGlass.isDoor(group.getFirst().object) ? "door" : "fence", most));
                }
                return new Cluster(group.getFirst().object, group.getFirst().north, along, up, radius, left);
            }
            left.remove(furthest);
        }
        if (DevBuild.ON && group.size() > 1) {
            Log.info(String.format(Locale.ROOT, "a blast: its %d pellets on the %s struck too far apart to blow a hole", group.size(),
                    DoorGlass.isDoor(group.getFirst().object) ? "door" : "fence"));
        }
        return null;
    }

    private static float distance(Struck each, float along, float up) {
        float da = each.along - along, du = (each.up - up) * ViewCamera.LEVEL_HEIGHT;
        return (float) Math.sqrt(da * da + du * du);
    }

    /** Forgets the pellets not settled yet: the save is left. */
    static void forget() {
        struck.clear();
    }
}
